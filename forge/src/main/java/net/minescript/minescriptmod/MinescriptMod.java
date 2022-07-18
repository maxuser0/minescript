package net.minescript.minescriptmod;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.InterModEnqueueEvent;
import net.minecraftforge.fml.event.lifecycle.InterModProcessEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("minescript")
public class MinescriptMod {
  private static final Logger LOGGER = LogManager.getLogger();

  public MinescriptMod() {
    // Register the setup method for modloading
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
    // Register the enqueueIMC method for modloading
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::enqueueIMC);
    // Register the processIMC method for modloading
    FMLJavaModLoadingContext.get().getModEventBus().addListener(this::processIMC);

    // Register ourselves for server and other game events we are interested in
    MinecraftForge.EVENT_BUS.register(this);
  }

  private void setup(final FMLCommonSetupEvent event) {
    // some preinit code
  }

  private void enqueueIMC(final InterModEnqueueEvent event) {
    // some example code to dispatch IMC to another mod
  }

  private void processIMC(final InterModProcessEvent event) {
    // some example code to receive and process InterModComms from other mods
  }

  // You can use SubscribeEvent and let the Event Bus discover methods to call
  @SubscribeEvent
  public void onServerStarting(ServerStartingEvent event) {
    // do something when the server starts
  }

  @SubscribeEvent
  public void onCommandEvent(CommandEvent event) {}

  private static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
    return LiteralArgumentBuilder.<CommandSourceStack>literal(name);
  }

  private static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(
      String name, ArgumentType<T> type) {
    return RequiredArgumentBuilder.<CommandSourceStack, T>argument(name, type);
  }

  private static final String[] BUILTIN_COMMANDS =
      new String[] {"ls", "minescript_commands_per_cycle", "minescript_ticks_per_cycle"};

  private static List<String> getScriptCommandNamesWithBuiltins() {
    var names = getScriptCommandNames();
    for (String builtin : BUILTIN_COMMANDS) {
      names.add(builtin);
    }
    return names;
  }

  private static void logStackTrace(Exception e) {
    var sw = new StringWriter();
    var pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    LOGGER.error("(minescript) Exception thrown: {} {}", e.getMessage(), sw.toString());
  }

  private static final String MINESCRIPT_DIR = "mods/minescript";

  private static List<String> getScriptCommandNames() {
    List<String> scriptNames = new ArrayList<>();
    String minescriptDir = System.getProperty("user.dir") + "/" + MINESCRIPT_DIR;
    // LOGGER.info("### MODS DIR: " + minescriptDir);
    try {
      Files.list(new File(minescriptDir).toPath())
          .filter(path -> path.toString().endsWith(".py"))
          .forEach(
              path -> {
                String commandName =
                    path.toString().replace(minescriptDir + "/", "").replaceFirst("\\.py$", "");
                // LOGGER.info("### SCRIPT: " + path);
                // LOGGER.info("### COMMAND: " + commandName);
                scriptNames.add(commandName);
              });
    } catch (IOException e) {
      logStackTrace(e);
    }
    return scriptNames;
  }

  private static final Pattern TILDE_RE = Pattern.compile("^~([-\\+]?)([0-9]*)$");

  private static String tildeParamToNumber(String param, double playerPosition) {
    var match = TILDE_RE.matcher(param);
    if (match.find()) {
      return String.valueOf(
          (int) playerPosition
              + (match.group(1).equals("-") ? -1 : 1)
                  * (match.group(2).isEmpty() ? 0 : Integer.valueOf(match.group(2))));
    } else {
      LOGGER.error("(minescript) Canont parse tilde-param: \"{}\"", param);
      return String.valueOf((int) playerPosition);
    }
  }

  private static String[] substituteMinecraftVars(String[] command) {
    var player = Minecraft.getInstance().player;
    List<Integer> tildeParamPositions = new ArrayList<>();
    int consecutiveTildes = 0;
    for (int i = 0; i < command.length; ++i) {
      if (TILDE_RE.matcher(command[i]).find()) {
        consecutiveTildes++;
        tildeParamPositions.add(i);
      } else {
        if (consecutiveTildes % 3 != 0) {
          // TODO(maxuser): Report error to user in chat, or color chat input red.
          break;
        }
        consecutiveTildes = 0;
      }

      if (command[i].matches(".*\\$x\\b.*")) {
        LOGGER.info("$x matched command arg[" + i + "]: \"" + command[i] + "\"");
        command[i] = command[i].replaceAll("\\$x\\b", String.valueOf(player.getX()));
        LOGGER.info("command arg[" + i + "] substituted: \"" + command[i] + "\"");
      }
      if (command[i].matches(".*\\$y\\b.*")) {
        LOGGER.info("$y matched command arg[" + i + "]: \"" + command[i] + "\"");
        command[i] = command[i].replaceAll("\\$y\\b", String.valueOf(player.getY()));
        LOGGER.info("command arg[" + i + "] substituted: \"" + command[i] + "\"");
      }
      if (command[i].matches(".*\\$z\\b.*")) {
        LOGGER.info("$z matched command arg[" + i + "]: \"" + command[i] + "\"");
        command[i] = command[i].replaceAll("\\$z\\b", String.valueOf(player.getZ()));
        LOGGER.info("command arg[" + i + "] substituted: \"" + command[i] + "\"");
      }
    }

    if (consecutiveTildes % 3 != 0) {
      // TODO(maxuser): log error?
      return command;
    }

    // Substitute x, y, z into tildeParamPositions.
    int tildeCount = 0;
    for (int tildePos : tildeParamPositions) {
      switch (tildeCount++ % 3) {
        case 0:
          command[tildePos] = tildeParamToNumber(command[tildePos], player.getX());
          break;
        case 1:
          command[tildePos] = tildeParamToNumber(command[tildePos], player.getY());
          break;
        case 2:
          command[tildePos] = tildeParamToNumber(command[tildePos], player.getZ());
          break;
      }
    }

    return command;
  }

  // TODO(maxuser): Spawn a new thread for each command.
  private static int runExternalCommand(
      String[] command,
      Consumer<String> stdoutLineProcessor,
      Consumer<String> stderrLineProcessor) {
    // TODO(maxuser): Support non-Python executables in general.
    String scriptExtension = ".py";
    String scriptName = MINESCRIPT_DIR + "/" + command[0] + scriptExtension;

    String pythonInterpreterPath = findFirstFile("/usr/bin/python3", "/usr/local/bin/python3");
    if (pythonInterpreterPath == null) {
      LOGGER.error("Cannot find Python3 interpreter");
      commandQueue.add(
          wrapLineWithTellrawColor(
              "Cannot find Python3 interpreter at any of these locations:", "red"));
      commandQueue.add(wrapLineWithTellrawColor("  /usr/bin/python3", "red"));
      commandQueue.add(wrapLineWithTellrawColor("  /usr/local/bin/python3", "red"));
      commandQueue.add(wrapLineWithTellrawColor("See: https://www.python.org/downloads/", "red"));
      return -1;
    }

    String[] executableCommand = new String[command.length + 1];
    executableCommand[0] = pythonInterpreterPath;
    executableCommand[1] = scriptName;
    for (int i = 1; i < command.length; i++) {
      executableCommand[i + 1] = command[i];
    }

    final Process process;
    try {
      process = Runtime.getRuntime().exec(substituteMinecraftVars(executableCommand));
    } catch (IOException e) {
      logStackTrace(e);
      stderrLineProcessor.accept(e.getMessage());
      return -2;
    }

    try (var stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        var stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
      String line;
      while ((line = stdoutReader.readLine()) != null) {
        stdoutLineProcessor.accept(line);
      }
      while ((line = stderrReader.readLine()) != null) {
        stderrLineProcessor.accept(line);
      }
    } catch (IOException e) {
      logStackTrace(e);
      stderrLineProcessor.accept(e.getMessage());
      return -3;
    }
    if (process == null) {
      return -4;
    }
    try {
      return process.waitFor();
    } catch (InterruptedException e) {
      logStackTrace(e);
      return -5;
    }
  }

  static String wrapLineWithTellrawColor(String text, String color) {
    // Treat as plain text to write to the chat.
    return "/tellraw @s {\"text\":\""
        + text.replace("\\", "\\\\").replace("\"", "\\\"")
        + "\",\"color\":\""
        + color
        + "\"}";
  }

  static class Subprocess {
    private static int nextCommandId = 1;
    private final int commandId;
    private final String[] command;
    private final Queue<String> stdoutQueue = new ConcurrentLinkedQueue<String>();
    private final Queue<String> stderrQueue = new ConcurrentLinkedQueue<String>();

    public Subprocess(String[] command) {
      this.commandId = nextCommandId++;
      this.command = command;
    }

    public int commandId() {
      return commandId;
    }

    @Override
    public String toString() {
      String displayCommand = String.join(" ", command);
      if (displayCommand.length() > 24) {
        displayCommand = displayCommand.substring(0, 24);
      }
      return String.format("[%d] %s", commandId, displayCommand);
    }

    public Queue<String> stdoutQueue() {
      return stdoutQueue;
    }

    public Queue<String> stderrQueue() {
      return stderrQueue;
    }
  }

  static class SubprocessMap {
    private final Map<Integer, Subprocess> map = new ConcurrentHashMap<Integer, Subprocess>();

    public Subprocess addSubprocess(String[] command) {
      var subprocess = new Subprocess(command);
      map.put(subprocess.commandId(), subprocess);
      return subprocess;
    }

    public Map<Integer, Subprocess> map() {
      return map;
    }
  }

  private static SubprocessMap subprocessMap = new SubprocessMap();

  // TODO(maxuser): remove commandQueue in favor of subprocessMap.
  private static Queue<String> commandQueue = new ConcurrentLinkedQueue<String>();

  static void enqueueInterpretedScriptOutputLine(String text) {
    if (text.matches("^/[a-zA-Z].*")) {
      commandQueue.add(text);
    } else if (text.startsWith("#")) {
      commandQueue.add(text.substring(1).stripLeading());
    } else {
      // Treat as plain text to write to the chat.
      commandQueue.add(wrapLineWithTellrawColor(text, "white"));
    }
  }

  private static boolean checkMinescriptDir() {
    String minescriptDir = System.getProperty("user.dir") + "/" + MINESCRIPT_DIR;
    if (!Files.isDirectory(Paths.get(minescriptDir))) {
      commandQueue.add(
          wrapLineWithTellrawColor("Minescript folder is missing. Please create it at:", "red"));
      commandQueue.add(wrapLineWithTellrawColor(minescriptDir, "red"));
      return false;
    }
    return true;
  }

  private static void runCommand(String[] command) {
    if (!checkMinescriptDir()) {
      return;
    }

    // TODO(maxuser): Add commands for:
    // `jobs`: list currently running jobs from external commands, with an int id for each
    // `suspendjob ID`: suspend job with ID
    // `resumejob ID`: resume job with ID
    // `killjob ID`: kill job with ID

    if (command[0].equals("minescript_commands_per_cycle")) {
      int numCommands = Integer.valueOf(command[1]);
      if (numCommands < 1) numCommands = 1;
      minescriptCommandsPerCycle = numCommands;
      commandQueue.add(
          wrapLineWithTellrawColor(
              "Minescript execution set to " + numCommands + " command(s) per cycle.", "green"));
      return;
    }

    if (command[0].equals("minescript_ticks_per_cycle")) {
      int ticks = Integer.valueOf(command[1]);
      if (ticks < 1) ticks = 1;
      minescriptTicksPerCycle = ticks;
      commandQueue.add(
          wrapLineWithTellrawColor(
              "Minescript execution set to " + ticks + " tick(s) per cycle.", "green"));
      return;
    }

    if (!getScriptCommandNames().contains(command[0])) {
      commandQueue.add(wrapLineWithTellrawColor("Minescript commands:", "yellow"));
      for (String builtin : BUILTIN_COMMANDS) {
        commandQueue.add(wrapLineWithTellrawColor("  " + builtin + " [builtin]", "yellow"));
      }
      for (String script : getScriptCommandNames()) {
        commandQueue.add(wrapLineWithTellrawColor("  " + script, "yellow"));
      }
      if (!command[0].equals("ls")) {
        commandQueue.add(
            wrapLineWithTellrawColor("No Minescript command named \"" + command[0] + "\"", "red"));
      }
      return;
    }

    var subprocess = subprocessMap.addSubprocess(command);
    int exitCode =
        runExternalCommand(
            command,
            MinescriptMod::enqueueInterpretedScriptOutputLine,
            line -> commandQueue.add(wrapLineWithTellrawColor(line, "red")));
    if (exitCode != 0) {
      commandQueue.add(wrapLineWithTellrawColor("command exit code: " + exitCode, "red"));
    }
  }

  private static String findFirstFile(String... filenames) {
    for (String filename : filenames) {
      Path path = Paths.get(filename);
      if (Files.exists(path)) {
        return filename;
      }
    }
    return null;
  }

  private static void registerCommand(
      CommandDispatcher<CommandSourceStack> dispatcher, String commandName) {
    LOGGER.info("Registering script command: " + commandName);

    dispatcher.register(
        literal(commandName)
            .then(
                argument("param1", string())
                    .then(
                        argument("param2", string())
                            .then(
                                argument("param3", string())
                                    .then(
                                        argument("param4", string())
                                            .then(
                                                argument("param5", string())
                                                    .then(
                                                        argument("param6", string())
                                                            .then(
                                                                argument("param7", string())
                                                                    .then(
                                                                        argument("param8", string())
                                                                            .executes(
                                                                                context -> {
                                                                                  String param1 =
                                                                                      getString(
                                                                                          context,
                                                                                          "param1");
                                                                                  String param2 =
                                                                                      getString(
                                                                                          context,
                                                                                          "param2");
                                                                                  String param3 =
                                                                                      getString(
                                                                                          context,
                                                                                          "param3");
                                                                                  String param4 =
                                                                                      getString(
                                                                                          context,
                                                                                          "param4");
                                                                                  String param5 =
                                                                                      getString(
                                                                                          context,
                                                                                          "param5");
                                                                                  String param6 =
                                                                                      getString(
                                                                                          context,
                                                                                          "param6");
                                                                                  String param7 =
                                                                                      getString(
                                                                                          context,
                                                                                          "param7");
                                                                                  String param8 =
                                                                                      getString(
                                                                                          context,
                                                                                          "param8");
                                                                                  runCommand(
                                                                                      new String[] {
                                                                                        commandName,
                                                                                        param1,
                                                                                        param2,
                                                                                        param3,
                                                                                        param4,
                                                                                        param5,
                                                                                        param6,
                                                                                        param7,
                                                                                        param8
                                                                                      });
                                                                                  return 1;
                                                                                }))
                                                                    .executes(
                                                                        context -> {
                                                                          String param1 =
                                                                              getString(
                                                                                  context,
                                                                                  "param1");
                                                                          String param2 =
                                                                              getString(
                                                                                  context,
                                                                                  "param2");
                                                                          String param3 =
                                                                              getString(
                                                                                  context,
                                                                                  "param3");
                                                                          String param4 =
                                                                              getString(
                                                                                  context,
                                                                                  "param4");
                                                                          String param5 =
                                                                              getString(
                                                                                  context,
                                                                                  "param5");
                                                                          String param6 =
                                                                              getString(
                                                                                  context,
                                                                                  "param6");
                                                                          String param7 =
                                                                              getString(
                                                                                  context,
                                                                                  "param7");
                                                                          runCommand(
                                                                              new String[] {
                                                                                commandName,
                                                                                param1,
                                                                                param2,
                                                                                param3,
                                                                                param4,
                                                                                param5,
                                                                                param6,
                                                                                param7
                                                                              });
                                                                          return 1;
                                                                        }))
                                                            .executes(
                                                                context -> {
                                                                  String param1 =
                                                                      getString(context, "param1");
                                                                  String param2 =
                                                                      getString(context, "param2");
                                                                  String param3 =
                                                                      getString(context, "param3");
                                                                  String param4 =
                                                                      getString(context, "param4");
                                                                  String param5 =
                                                                      getString(context, "param5");
                                                                  String param6 =
                                                                      getString(context, "param6");
                                                                  runCommand(
                                                                      new String[] {
                                                                        commandName,
                                                                        param1,
                                                                        param2,
                                                                        param3,
                                                                        param4,
                                                                        param5,
                                                                        param6
                                                                      });
                                                                  return 1;
                                                                }))
                                                    .executes(
                                                        context -> {
                                                          String param1 =
                                                              getString(context, "param1");
                                                          String param2 =
                                                              getString(context, "param2");
                                                          String param3 =
                                                              getString(context, "param3");
                                                          String param4 =
                                                              getString(context, "param4");
                                                          String param5 =
                                                              getString(context, "param5");
                                                          runCommand(
                                                              new String[] {
                                                                commandName,
                                                                param1,
                                                                param2,
                                                                param3,
                                                                param4,
                                                                param5
                                                              });
                                                          return 1;
                                                        }))
                                            .executes(
                                                context -> {
                                                  String param1 = getString(context, "param1");
                                                  String param2 = getString(context, "param2");
                                                  String param3 = getString(context, "param3");
                                                  String param4 = getString(context, "param4");
                                                  runCommand(
                                                      new String[] {
                                                        commandName, param1, param2, param3, param4
                                                      });
                                                  return 1;
                                                }))
                                    .executes(
                                        context -> {
                                          String param1 = getString(context, "param1");
                                          String param2 = getString(context, "param2");
                                          String param3 = getString(context, "param3");
                                          runCommand(
                                              new String[] {commandName, param1, param2, param3});
                                          return 1;
                                        }))
                            .executes(
                                context -> {
                                  String param1 = getString(context, "param1");
                                  String param2 = getString(context, "param2");
                                  runCommand(new String[] {commandName, param1, param2});
                                  return 1;
                                }))
                    .executes(
                        context -> {
                          String param1 = getString(context, "param1");
                          runCommand(new String[] {commandName, param1});
                          return 1;
                        }))
            .executes(
                context -> {
                  runCommand(new String[] {commandName});
                  return 1;
                }));
  }

  private static int minescriptTicksPerCycle = 1;
  private static int minescriptCommandsPerCycle = 5;

  @SubscribeEvent
  public void onRegisterCommandEvent(RegisterCommandsEvent event) {
    LOGGER.info("host OS: \"" + System.getProperty("os.name") + "\"");
    CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
    dispatcher.register(
        literal("run")
            .then(
                argument("command", string())
                    .executes(
                        context -> {
                          String command = getString(context, "command");
                          LOGGER.info("Command is " + command);
                          runCommand(command.split("\\s+"));
                          return 1;
                        })));
    dispatcher.register(
        literal("minescript_ticks_per_cycle")
            .then(
                argument("ticks", integer())
                    .executes(
                        context -> {
                          int ticks = getInteger(context, "ticks");
                          if (ticks < 1) ticks = 1;
                          minescriptTicksPerCycle = ticks;
                          commandQueue.add(
                              wrapLineWithTellrawColor(
                                  "Minescript execution set to " + ticks + " tick(s) per cycle.",
                                  "green"));
                          return 1;
                        })));
    dispatcher.register(
        literal("minescript_commands_per_cycle")
            .then(
                argument("commands", integer())
                    .executes(
                        context -> {
                          int commands = getInteger(context, "commands");
                          if (commands < 1) commands = 1;
                          minescriptCommandsPerCycle = commands;
                          commandQueue.add(
                              wrapLineWithTellrawColor(
                                  "Minescript execution set to "
                                      + commands
                                      + " command(s) per cycle.",
                                  "green"));
                          return 1;
                        })));
    for (String scriptCommand : getScriptCommandNames()) {
      registerCommand(dispatcher, scriptCommand);
    }
  }

  private static int renderTickEventCounter = 0;
  private static int playerTickEventCounter = 0;

  private static int BACKSLASH_KEY = 92;
  private static int ESCAPE_KEY = 256;
  private static int TAB_KEY = 258;
  private static int BACKSPACE_KEY = 259;

  private static String insertSubstring(String original, int position, String insertion) {
    return original.substring(0, position) + insertion + original.substring(position);
  }

  private static String eraseChar(String original, int position) {
    if (original.isEmpty() || position == 0) {
      return original;
    }
    String modified = original.substring(0, position - 1);
    if (position < original.length()) {
      modified += original.substring(position);
    }
    return modified;
  }

  private static String longestCommonPrefix(List<String> strings) {
    if (strings.isEmpty()) {
      return "";
    }
    String longest = strings.get(0);
    for (int i = 1; i < strings.size(); i++) {
      String string = strings.get(i);
      int end = Math.min(string.length(), longest.length());
      if (end < longest.length()) {
        longest = longest.substring(0, end);
      }
      for (int j = 1; j < end; j++) {
        if (string.charAt(j) != longest.charAt(j)) {
          longest = longest.substring(0, j);
          break;
        }
      }
    }
    return longest;
  }

  private static List<String> commandSuggestions = new ArrayList<>();

  private static Object getField(Object object, String unobfuscatedName, String obfuscatedName)
      throws IllegalAccessException, NoSuchFieldException, SecurityException {
    Field field;
    try {
      field = object.getClass().getDeclaredField(obfuscatedName);
    } catch (NoSuchFieldException e) {
      LOGGER.info(
          "(minescript) Cannot find field with obfuscated name \"{}\", falling back to unobfuscated"
              + " name \"{}\"",
          obfuscatedName,
          unobfuscatedName);
      try {
        field = object.getClass().getDeclaredField(unobfuscatedName);
      } catch (NoSuchFieldException e2) {
        LOGGER.info("(minescript) Declared fields of {}:", object.getClass().getName());
        for (Field f : object.getClass().getDeclaredFields()) {
          LOGGER.info("(minescript)   {}", f);
        }
        throw e2;
      }
    }
    field.setAccessible(true);
    return field.get(object);
  }

  @SubscribeEvent
  public void onKeyboardKeyPressedEvent(ScreenEvent.KeyboardKeyPressedEvent event) {
    var screen = event.getScreen();
    if (screen != null && screen instanceof ChatScreen) {
      var scriptCommandNames = getScriptCommandNamesWithBuiltins();
      try {
        var input = (EditBox) getField(screen, "input", "f_95573_");
        String value = input.getValue();
        if (value.startsWith("\\") && value.length() > 1) {
          var key = event.getKeyCode();
          int cursorPos = input.getCursorPosition();
          if (key >= 32 && key < 127) {
            // TODO(maxuser): use input.setSuggestion(String) to set suggestion?
            // TODO(maxuser): detect upper vs lower case properly
            String extraChar = Character.toString((char) key).toLowerCase();
            value = insertSubstring(value, cursorPos, extraChar);
          } else if (key == BACKSPACE_KEY) {
            value = eraseChar(value, cursorPos);
          }
          String command = value.substring(1).split("\\s+")[0];
          if (key == TAB_KEY
              && !commandSuggestions.isEmpty()
              && cursorPos == command.length() + 1) {
            // Insert the remainder of the completed command.
            String maybeTrailingSpace =
                ((cursorPos < value.length() && value.charAt(cursorPos) == ' ')
                        || commandSuggestions.size() > 1)
                    ? ""
                    : " ";
            input.insertText(
                longestCommonPrefix(commandSuggestions).substring(command.length())
                    + maybeTrailingSpace);
            if (commandSuggestions.size() > 1) {
              input.setTextColor(0x5ee8e8); // cyan for partial completion
            } else {
              input.setTextColor(0x5ee85e); // green for full completion
            }
            commandSuggestions = new ArrayList<>();
            return;
          }
          if (scriptCommandNames.contains(command)) {
            input.setTextColor(0x5ee85e); // green
            commandSuggestions = new ArrayList<>();
          } else {
            List<String> newCommandSuggestions = new ArrayList<>();
            if (!command.isEmpty()) {
              for (String scriptName : scriptCommandNames) {
                if (scriptName.startsWith(command)) {
                  newCommandSuggestions.add(scriptName);
                }
              }
            }
            if (!newCommandSuggestions.isEmpty()) {
              if (!newCommandSuggestions.equals(commandSuggestions)) {
                commandQueue.add(wrapLineWithTellrawColor("completions:", "aqua"));
                for (String suggestion : newCommandSuggestions) {
                  commandQueue.add(wrapLineWithTellrawColor("  " + suggestion, "aqua"));
                }
                commandSuggestions = newCommandSuggestions;
              }
              input.setTextColor(0x5ee8e8); // cyan
            } else {
              input.setTextColor(0xe85e5e); // red
              commandSuggestions = new ArrayList<>();
            }
          }
        }
      } catch (IllegalAccessException | NoSuchFieldException | SecurityException e) {
        logStackTrace(e);
      }
    }
  }

  private static Method getMethod(
      Object object, String unobfuscatedName, String obfuscatedName, Class<?>... paramTypes)
      throws IllegalAccessException, NoSuchMethodException {
    Method method;
    try {
      method = object.getClass().getDeclaredMethod(obfuscatedName, paramTypes);
    } catch (NoSuchMethodException e) {
      LOGGER.info(
          "(minescript) Cannot find method with obfuscated name \"{}\", falling back to"
              + " unobfuscated name \"{}\"",
          obfuscatedName,
          unobfuscatedName);
      try {
        method = object.getClass().getDeclaredMethod(unobfuscatedName, paramTypes);
      } catch (NoSuchMethodException e2) {
        LOGGER.info("(minescript) Declared methods of {}:", object.getClass().getName());
        for (Method m : object.getClass().getDeclaredMethods()) {
          LOGGER.info("(minescript)   {}", m);
        }
        throw e2;
      }
    }
    method.setAccessible(true);
    return method;
  }

  @SubscribeEvent
  public void onKeyInputEvent(InputEvent.KeyInputEvent event) {
    var minecraft = Minecraft.getInstance();
    var screen = minecraft.screen;
    if (screen == null && event.getKey() == BACKSLASH_KEY) {
      try {
        var method = getMethod(minecraft, "openChatScreen", "m_91326_", String.class);
        method.invoke(minecraft, "");
      } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
        logStackTrace(e);
      }
    }
  }

  @SubscribeEvent
  public void onClientChatEvent(ClientChatEvent event) {
    if (event.getMessage().startsWith("\\")) {
      // TODO(maxuser): need to do single/double quote parsing etc.
      String[] command = event.getMessage().substring(1).split("\\s+");
      LOGGER.info(
          "(minescript) Processing command from chat event: {}", String.join(", ", command));
      runCommand(command);
      event.setCanceled(true);
    }
  }

  @SubscribeEvent
  public void onPlayerTick(TickEvent.PlayerTickEvent event) {
    if (++playerTickEventCounter % minescriptTicksPerCycle == 0) {
      var minecraft = Minecraft.getInstance();
      var player = minecraft.player;
      if (player != null) {
        for (int i = 0; i < minescriptCommandsPerCycle; ++i) {
          String command = commandQueue.poll();
          if (command == null) {
            break;
          }
          LOGGER.info("(minescript) Polled command from queue: " + command);
          player.chat(command);
        }
      }
    }
  }

  // You can use EventBusSubscriber to automatically subscribe events on the contained class (this
  // is subscribing to the MOD
  // Event bus for receiving Registry Events)
  @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
  public static class RegistryEvents {
    @SubscribeEvent
    public static void onBlocksRegistry(final RegistryEvent.Register<Block> blockRegistryEvent) {
      // register a new block here
    }
  }
}
