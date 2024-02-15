// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import static net.minescript.common.CommandSyntax.Token;
import static net.minescript.common.CommandSyntax.parseCommand;
import static net.minescript.common.CommandSyntax.quoteCommand;
import static net.minescript.common.CommandSyntax.quoteString;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import io.netty.buffer.Unpooled;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

public class Minescript {
  private static final Logger LOGGER = LogManager.getLogger();

  // MINESCRIPT_DIR is relative to the minecraft directory which is the working directory.
  private static final String MINESCRIPT_DIR = "minescript";

  private enum FileOverwritePolicy {
    DO_NOT_OVERWRITE,
    OVERWRITTE
  }

  private static Platform platform;
  private static Thread worldListenerThread;
  private static ScriptConfig scriptConfig;

  public static void init(Platform platform) {
    Minescript.platform = platform;
    LOGGER.info("Starting Minescript on OS: {}", System.getProperty("os.name"));
    if (new File(MINESCRIPT_DIR).mkdir()) {
      LOGGER.info("Created minescript dir");
    }

    final String blockpacksDir = Paths.get(MINESCRIPT_DIR, "blockpacks").toString();
    if (new File(blockpacksDir).mkdir()) {
      LOGGER.info("Created minescript blockpacks dir");
    }

    var undoDir = new File(Paths.get(MINESCRIPT_DIR, "undo").toString());
    if (undoDir.exists()) {
      int numDeletedFiles = 0;
      LOGGER.info("Deleting undo files from previous run...");
      for (var undoFile : undoDir.listFiles()) {
        if (undoFile.getName().endsWith(".txt") || undoFile.getName().endsWith(".zip")) {
          if (undoFile.delete()) {
            ++numDeletedFiles;
          }
        }
      }
      LOGGER.info("{} undo file(s) deleted.", numDeletedFiles);
    }

    String currentVersion = getCurrentVersion();
    String lastRunVersion = getLastRunVersion();
    if (!currentVersion.equals(lastRunVersion)) {
      LOGGER.info(
          "Current version ({}) does not match last run version ({})",
          currentVersion,
          lastRunVersion);

      loadMinescriptResources();
    }

    worldListenerThread =
        new Thread(Minescript::runWorldListenerThread, "minescript-world-listener");
    worldListenerThread.start();

    loadConfig();
  }

  private static void loadMinescriptResources() {
    // copy.py was renamed to copy_blocks.py in Minescript 4.0 to avoid conflict with the built-in
    // copy module. Delete the obsolete script if it exists.
    deleteMinescriptFile("copy.py");

    // Delete files that used to be stored directly within the `minescript` dir in prior versions.
    deleteMinescriptFile("minescript.py");
    deleteMinescriptFile("minescript_runtime.py");
    deleteMinescriptFile("help.py");
    deleteMinescriptFile("copy_blocks.py");
    deleteMinescriptFile("paste.py");
    deleteMinescriptFile("eval.py");

    Path minescriptDir = Paths.get(MINESCRIPT_DIR);
    Path libDir = Paths.get(MINESCRIPT_DIR, "system", "lib");
    Path execDir = Paths.get(MINESCRIPT_DIR, "system", "exec");

    new File(libDir.toString()).mkdirs();
    new File(execDir.toString()).mkdirs();

    copyJarResourceToFile("version.txt", minescriptDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("minescript.py", libDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("minescript_runtime.py", libDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("help.py", execDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("copy_blocks.py", execDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("paste.py", execDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("eval.py", execDir, FileOverwritePolicy.OVERWRITTE);
  }

  private static void deleteMinescriptFile(String fileName) {
    var fileToDelete = new File(Paths.get(MINESCRIPT_DIR, fileName).toString());
    if (fileToDelete.exists()) {
      if (fileToDelete.delete()) {
        LOGGER.info("Deleted obsolete file: `{}`", fileToDelete.getPath());
      }
    }
  }

  private static AtomicBoolean autorunHandled = new AtomicBoolean(false);

  private static void runWorldListenerThread() {
    final int millisToSleep = 1000;
    var minecraft = Minecraft.getInstance();
    boolean noWorld = (minecraft.level == null);
    while (true) {
      boolean noWorldNow = (minecraft.level == null);
      if (noWorld != noWorldNow) {
        if (noWorldNow) {
          autorunHandled.set(false);
          LOGGER.info("Exited world");
          for (var job : jobs.getMap().values()) {
            job.kill();
          }
          systemMessageQueue.clear();
        } else {
          LOGGER.info("Entered world");
        }
        noWorld = noWorldNow;
      }
      try {
        Thread.sleep(millisToSleep);
      } catch (InterruptedException e) {
        logException(e);
      }
    }
  }

  private static Gson GSON = new GsonBuilder().serializeNulls().create();

  private static String getCurrentVersion() {
    try (var in = Minescript.class.getResourceAsStream("/version.txt");
        var reader = new BufferedReader(new InputStreamReader(in))) {
      return reader.readLine().strip();
    } catch (IOException e) {
      LOGGER.error("Exception loading version resource: {}", e);
      return "";
    }
  }

  private static String getLastRunVersion() {
    Path versionPath = Paths.get(MINESCRIPT_DIR, "version.txt");
    if (!Files.exists(versionPath)) {
      return "";
    }
    try {
      return Files.readString(versionPath).strip();
    } catch (IOException e) {
      LOGGER.error("Exception loading version file: {}", e);
      return "";
    }
  }

  /** Copies resource from jar to a same-named file in the minescript dir.. */
  private static void copyJarResourceToFile(
      String resourceName, Path dir, FileOverwritePolicy overwritePolicy) {
    copyJarResourceToFile(resourceName, dir, resourceName, overwritePolicy);
  }

  /** Copies resource from jar to a file in the minescript dir. */
  private static void copyJarResourceToFile(
      String resourceName, Path dir, String fileName, FileOverwritePolicy overwritePolicy) {
    Path filePath = dir.resolve(fileName);
    if (Files.exists(filePath)) {
      switch (overwritePolicy) {
        case OVERWRITTE:
          try {
            Files.delete(filePath);
          } catch (IOException e) {
            LOGGER.error("Failed to delete file to be overwritten: {}", filePath);
            return;
          }
          LOGGER.info("Deleted outdated file: {}", filePath);
          break;
        case DO_NOT_OVERWRITE:
          return;
      }
    }
    try (var in = Minescript.class.getResourceAsStream("/" + resourceName);
        var reader = new BufferedReader(new InputStreamReader(in));
        var writer = new FileWriter(filePath.toString())) {
      reader.transferTo(writer);
      LOGGER.info("Copied jar resource \"{}\" to \"{}\"", resourceName, filePath);
    } catch (IOException e) {
      LOGGER.error("Failed to copy jar resource \"{}\" to \"{}\"", resourceName, filePath);
    }
  }

  private static String pythonLocation = null;

  private static final Pattern CONFIG_LINE_RE = Pattern.compile("([^=]+)=(.*)");
  private static final Pattern DOUBLE_QUOTED_STRING_RE = Pattern.compile("\"(.*)\"");
  private static final Pattern CONFIG_AUTORUN_RE = Pattern.compile("autorun\\[(.*)\\]");

  // Map from world name (or "*" for all) to a list of Minescript/Minecraft commands.
  private static Map<String, List<Message>> autorunCommands = new ConcurrentHashMap<>();

  private static final File configFile =
      new File(Paths.get(MINESCRIPT_DIR, "config.txt").toString());
  private static long lastConfigLoadTime = 0;

  // Regex pattern for ignoring lines of output from stderr of Python scripts.
  private static Pattern stderrChatIgnorePattern = Pattern.compile("^$");

  /** Loads config from {@code minescript/config.txt} if the file has changed since last loaded. */
  private static void loadConfig() {
    Path minescriptDir = Paths.get(System.getProperty("user.dir"), MINESCRIPT_DIR);

    if (System.getProperty("os.name").startsWith("Windows")) {
      copyJarResourceToFile(
          "windows_config.txt", minescriptDir, "config.txt", FileOverwritePolicy.DO_NOT_OVERWRITE);
    } else {
      copyJarResourceToFile(
          "posix_config.txt", minescriptDir, "config.txt", FileOverwritePolicy.DO_NOT_OVERWRITE);
    }
    if (configFile.lastModified() < lastConfigLoadTime) {
      return;
    }
    lastConfigLoadTime = System.currentTimeMillis();
    autorunCommands.clear();

    scriptConfig = new ScriptConfig(MINESCRIPT_DIR, BUILTIN_COMMANDS, IGNORE_DIRS_FOR_COMPLETIONS);

    try (var reader = new BufferedReader(new FileReader(configFile.getPath()))) {
      String line;
      String continuedLine = null;
      int lineNum = 0;
      while ((line = reader.readLine()) != null) {
        ++lineNum;
        line = line.stripLeading();

        // Concatenate across lines ending with backslash. Interpret line ending with double
        // backslash as escaped and treat it as a literal backslash.
        if (line.endsWith("\\\\")) {
          line = line.substring(0, line.length() - 1);
        } else if (line.endsWith("\\")) {
          line = line.substring(0, line.length() - 1);
          if (continuedLine == null) {
            continuedLine = line;
          } else {
            continuedLine += " " + line;
          }
          continue;
        }

        if (continuedLine != null) {
          line = continuedLine + " " + line;
          continuedLine = null;
        }

        if (line.matches("[^=]*=\\s*\\{.*") && !line.strip().endsWith("}")) {
          continuedLine = line;
          continue;
        }

        line = line.strip();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        var match = CONFIG_LINE_RE.matcher(line);
        if (match.matches()) {
          String name = match.group(1).strip();
          String value = match.group(2).strip();

          // Strip double quotes surrounding value if present.
          match = DOUBLE_QUOTED_STRING_RE.matcher(value);
          if (match.matches()) {
            value = match.group(1);
          }

          switch (name) {
            case "python":
              if (System.getProperty("os.name").startsWith("Windows")) {
                pythonLocation =
                    value.startsWith("%userprofile%\\")
                        ? value.replace("%userprofile%", System.getProperty("user.home"))
                        : value;
              } else {
                // This does not support "~otheruser/..." syntax. But that would be odd anyway.
                pythonLocation =
                    value.startsWith("~/")
                        ? value.replaceFirst(
                            "~", Matcher.quoteReplacement(System.getProperty("user.home")))
                        : value;
              }
              LOGGER.info("Setting config var: {} = \"{}\" (\"{}\")", name, value, pythonLocation);

              // `python3 -u` for unbuffered stdout and stderr.
              var commandPattern = ImmutableList.of(pythonLocation, "-u", "{command}", "{args}");

              var environmentVars =
                  ImmutableList.of(
                      "PYTHONPATH="
                          + String.join(
                              File.pathSeparator,
                              ImmutableList.of(
                                  minescriptDir.resolve(Paths.get("system", "lib")).toString(),
                                  minescriptDir.toString())));

              try {
                scriptConfig.configureFileType(
                    new ScriptConfig.CommandConfig(".py", commandPattern, environmentVars));
              } catch (Exception e) {
                LOGGER.error(
                    "config.txt:{}: Failed to configure .py script execution: {}",
                    lineNum,
                    e.toString());
              }

              break;

            case "command":
              try {
                var commandConfig = GSON.fromJson(value, ScriptConfig.CommandConfig.class);
                scriptConfig.configureFileType(commandConfig);
              } catch (Exception e) {
                LOGGER.error(
                    "config.txt:{}: Failed to configure script execution: {}",
                    lineNum,
                    e.toString());
              }
              break;

            case "path":
              var commandPath =
                  Arrays.stream(value.split(File.pathSeparator))
                      .map(Paths::get)
                      .collect(Collectors.toList());
              scriptConfig.setCommandPath(commandPath);
              LOGGER.info("Setting path to {}", commandPath);
              break;

            case "minescript_commands_per_cycle":
              try {
                minescriptCommandsPerCycle = Integer.valueOf(value);
                LOGGER.info(
                    "Setting minescript_commands_per_cycle to {}", minescriptCommandsPerCycle);
              } catch (NumberFormatException e) {
                LOGGER.error("Unable to parse minescript_commands_per_cycle as integer: {}", value);
              }
              break;

            case "minescript_ticks_per_cycle":
              try {
                minescriptTicksPerCycle = Integer.valueOf(value);
                LOGGER.info("Setting minescript_ticks_per_cycle to {}", minescriptTicksPerCycle);
              } catch (NumberFormatException e) {
                LOGGER.error("Unable to parse minescript_ticks_per_cycle as integer: {}", value);
              }
              break;

            case "minescript_incremental_command_suggestions":
              incrementalCommandSuggestions = Boolean.valueOf(value);
              LOGGER.info(
                  "Setting minescript_incremental_command_suggestions to {}",
                  incrementalCommandSuggestions);
              break;

            case "minescript_script_function_debug_outptut":
              scriptFunctionDebugOutptut = Boolean.valueOf(value);
              LOGGER.info(
                  "Setting minescript_script_function_debug_outptut to {}",
                  scriptFunctionDebugOutptut);
              break;

            case "minescript_log_chunk_load_events":
              logChunkLoadEvents = Boolean.valueOf(value);
              LOGGER.info("Setting minescript_log_chunk_load_events to {}", logChunkLoadEvents);
              break;

            case "stderr_chat_ignore_pattern":
              stderrChatIgnorePattern = Pattern.compile(value);
              LOGGER.info("Setting stderr_chat_ignore_pattern to {}", value);
              break;

            default:
              {
                match = CONFIG_AUTORUN_RE.matcher(name);
                if (match.matches()) {
                  value = value.strip();
                  // Interpret commands lacking a slash or backslash prefix as Minescript commands.
                  final Message command;
                  if (value.startsWith("/")) {
                    command = Message.createMinecraftCommand(value.substring(1));
                  } else if (value.startsWith("\\")) {
                    command = Message.createMinescriptCommand(value.substring(1));
                  } else {
                    command = Message.createMinescriptCommand(value);
                  }
                  String worldName = match.group(1);
                  synchronized (autorunCommands) {
                    var commandList =
                        autorunCommands.computeIfAbsent(worldName, k -> new ArrayList<Message>());
                    commandList.add(command);
                  }
                  LOGGER.info("Added autorun command `{}` for `{}`", command, worldName);
                } else {
                  LOGGER.warn("Unrecognized config var: {} = \"{}\"", name, value);
                }
              }
          }
        } else {
          LOGGER.warn("config.txt:{}: unable parse config line: {}", lineNum, line);
        }
      }
    } catch (IOException e) {
      LOGGER.error("Exception loading config file: {}", e);
    }
  }

  private static final ImmutableList<String> BUILTIN_COMMANDS =
      ImmutableList.of(
          "ls",
          "copy",
          "jobs",
          "suspend",
          "z", // alias for suspend
          "resume",
          "killjob",
          "undo",
          "which",
          "reload_minescript_resources",
          "minescript_commands_per_cycle",
          "minescript_ticks_per_cycle",
          "minescript_incremental_command_suggestions",
          "minescript_script_function_debug_outptut",
          "minescript_log_chunk_load_events",
          "enable_minescript_on_chat_received_event");

  private static final ImmutableSet<String> IGNORE_DIRS_FOR_COMPLETIONS =
      ImmutableSet.of("blockpacks", "copies", "undo");

  private static void logException(Exception e) {
    var sw = new StringWriter();
    var pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    logUserError(
        "Minescript internal error: {} (see logs/latest.log for details; to browse or report issues"
            + " see https://minescript.net/issues)",
        e.toString());
    LOGGER.error(sw.toString());
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
      logUserError("Cannot parse tilde-param: \"{}\"", param);
      return String.valueOf((int) playerPosition);
    }
  }

  private static String[] substituteMinecraftVars(String[] originalCommand) {
    String[] command = Arrays.copyOf(originalCommand, originalCommand.length);
    var player = Minecraft.getInstance().player;
    List<Integer> tildeParamPositions = new ArrayList<>();
    int consecutiveTildes = 0;
    for (int i = 0; i < command.length; ++i) {
      if (TILDE_RE.matcher(command[i]).find()) {
        consecutiveTildes++;
        tildeParamPositions.add(i);
      } else {
        if (consecutiveTildes % 3 != 0) {
          logUserError(
              "Expected number of consecutive tildes to be a multple of 3, but got {}.",
              consecutiveTildes);
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
      logUserError(
          "Expected number of consecutive tildes to be a multple of 3, but got {}.",
          consecutiveTildes);
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

  static Message formatAsJsonText(String text, String color) {
    return Message.fromJsonFormattedText(
        "{\"text\":\""
            + text.replace("\\", "\\\\").replace("\"", "\\\"")
            + "\",\"color\":\""
            + color
            + "\"}");
  }

  public enum JobState {
    NOT_STARTED("Not started"),
    RUNNING("Running"),
    SUSPENDED("Suspended"),
    KILLED("Killed"),
    DONE("Done");

    private final String displayName;

    private JobState(String displayName) {
      this.displayName = displayName;
    }

    @Override
    public String toString() {
      return displayName;
    }
  };

  record ExceptionInfo(String type, String message, String desc, List<StackElement> stack) {

    record StackElement(String file, String method, int line) {}

    public static ExceptionInfo fromException(Exception e) {
      var type = e.getClass().getName();
      var desc = e.toString();
      var stackBuilder = new ImmutableList.Builder<StackElement>();
      boolean hitMinescriptJava = false;
      for (var element : e.getStackTrace()) {
        var filename = element.getFileName();
        // Capture stacktrace up through Minescript.java, but no further.
        if (!hitMinescriptJava && filename.equals("Minescript.java")) {
          hitMinescriptJava = true;
        } else if (hitMinescriptJava && !filename.equals("Minescript.java")) {
          break;
        }
        stackBuilder.add(
            new StackElement(filename, element.getMethodName(), element.getLineNumber()));
      }
      return new ExceptionInfo(type, e.getMessage(), desc, stackBuilder.build());
    }
  }

  interface JobControl {
    JobState state();

    void yield();

    Queue<Message> messageQueue();

    boolean respond(long functionCallId, JsonElement returnValue, boolean finalReply);

    void raiseException(long functionCallId, ExceptionInfo exception);

    void processStdout(String text);

    void processStderr(String text);

    default void log(String messagePattern, Object... arguments) {
      String logMessage = ParameterizedMessage.format(messagePattern, arguments);
      processStderr(logMessage);
    }

    void logJobException(Exception e);
  }

  interface UndoableAction {
    int originalJobId();

    void onOriginalJobDone();

    String[] originalCommand();

    String[] derivativeCommand();

    void processCommandToUndo(Level level, Message output);

    void enqueueCommands(Queue<Message> messageQueue);
  }

  static class UndoableActionBlockPack implements UndoableAction {
    private static String UNDO_DIR = Paths.get(MINESCRIPT_DIR, "undo").toString();

    private volatile int originalJobId; // ID of the job that this undoes.
    private String[] originalCommand;
    private final long startTimeMillis;
    private BlockPacker blockpacker = new BlockPacker();
    private final Set<Position> blocks = new HashSet<>();
    private String blockpackFilename;
    private boolean undone = false;

    // coords and pos are reused to avoid lots of small object instantiations.
    private int[] coords = new int[6];
    private BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private static class Position {
      public final int x;
      public final int y;
      public final int z;

      public Position(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
      }

      @Override
      public int hashCode() {
        return Objects.hash(x, y, z);
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        }
        if (!(o instanceof Position)) {
          return false;
        }
        Position other = (Position) o;
        return x == other.x && y == other.y && z == other.z;
      }
    }

    public UndoableActionBlockPack(int originalJobId, String[] originalCommand) {
      this.originalJobId = originalJobId;
      this.originalCommand = originalCommand;
      this.startTimeMillis = System.currentTimeMillis();
    }

    public int originalJobId() {
      return originalJobId;
    }

    public synchronized void onOriginalJobDone() {
      originalJobId = -1;

      if (!blocks.isEmpty()) {
        // Write undo BlockPack to a file and clear in-memory commands queue.
        new File(UNDO_DIR).mkdirs();
        blockpackFilename = Paths.get(UNDO_DIR, startTimeMillis + ".zip").toString();
        blockpacker.comments().put("source command", "undo");
        blockpacker.comments().put("command to undo", quoteCommand(originalCommand));
        var blockpack = blockpacker.pack();
        try {
          blockpack.writeZipFile(blockpackFilename);
        } catch (Exception e) {
          logException(e);
        }
        blockpacker = null;
        blocks.clear();
      }
    }

    public String[] originalCommand() {
      return originalCommand;
    }

    public String[] derivativeCommand() {
      String[] derivative = new String[2];
      derivative[0] = "\\undo";
      derivative[1] = "(" + String.join(" ", originalCommand) + ")";
      return derivative;
    }

    public synchronized void processCommandToUndo(Level level, Message output) {
      if (output.type() != Message.Type.MINECRAFT_COMMAND) {
        return;
      }
      String command = output.value();
      if (command.startsWith("setblock ") && getSetblockCoords(command, coords)) {
        Optional<String> block =
            blockStateToString(level.getBlockState(pos.set(coords[0], coords[1], coords[2])));
        if (block.isPresent()) {
          if (!addBlockToUndoQueue(coords[0], coords[1], coords[2], block.get())) {
            return;
          }
        }
      } else if (command.startsWith("fill ") && getFillCoords(command, coords)) {
        int x0 = coords[0];
        int y0 = coords[1];
        int z0 = coords[2];
        int x1 = coords[3];
        int y1 = coords[4];
        int z1 = coords[5];
        for (int x = x0; x <= x1; x++) {
          for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
              Optional<String> block = blockStateToString(level.getBlockState(pos.set(x, y, z)));
              if (block.isPresent()) {
                if (!addBlockToUndoQueue(x, y, z, block.get())) {
                  return;
                }
              }
            }
          }
        }
      }
    }

    private boolean addBlockToUndoQueue(int x, int y, int z, String block) {
      if (undone) {
        LOGGER.error(
            "Cannot add command to undoable action after already undone: {}",
            String.join(" ", originalCommand));
        return false;
      }
      // For a given position, add only the first block, because that's the
      // block that needs to be restored at that position during an undo operation.
      if (blocks.add(new Position(x, y, z))) {
        blockpacker.setblock(x, y, z, block);
      }
      return true;
    }

    public synchronized void enqueueCommands(Queue<Message> messageQueue) {
      undone = true;
      int[] nullRotation = null;
      int[] nullOffset = null;
      if (blockpackFilename == null) {
        blockpacker
            .pack()
            .getBlockCommands(
                nullRotation, nullOffset, s -> messageQueue.add(Message.createMinecraftCommand(s)));
        blockpacker = null;
        blocks.clear();
      } else {
        try {
          BlockPack.readZipFile(blockpackFilename)
              .getBlockCommands(
                  nullRotation,
                  nullOffset,
                  s -> messageQueue.add(Message.createMinecraftCommand(s)));
        } catch (Exception e) {
          logException(e);
        }
      }
    }
  }

  interface Task {
    int run(ScriptConfig.BoundCommand command, JobControl jobControl);

    default boolean sendResponse(long functionCallId, JsonElement returnValue, boolean finalReply) {
      return false;
    }

    default boolean sendException(long functionCallId, ExceptionInfo exception) {
      return false;
    }
  }

  /** Tracker for managing resources accessed by a script job. */
  static class ResourceTracker<T> {
    private final String resourceTypeName;
    private final int jobId;
    private final AtomicInteger idAllocator = new AtomicInteger(0);
    private final Map<Integer, T> resources = new ConcurrentHashMap<>();

    public ResourceTracker(Class<T> resourceType, int jobId) {
      resourceTypeName = resourceType.getSimpleName();
      this.jobId = jobId;
    }

    public int retain(T resource) {
      int id = idAllocator.incrementAndGet();
      resources.put(id, resource);
      LOGGER.info("Mapped Job[{}] {}[{}]", jobId, resourceTypeName, id);
      return id;
    }

    public T getById(int id) {
      return resources.get(id);
    }

    public T releaseById(int id) {
      var resource = resources.remove(id);
      if (resource != null) {
        LOGGER.info("Unmapped Job[{}] {}[{}]", jobId, resourceTypeName, id);
      }
      return resource;
    }

    public void releaseAll() {
      for (int id : resources.keySet()) {
        releaseById(id);
      }
    }
  }

  static class Job implements JobControl {
    private final int jobId;
    private final ScriptConfig.BoundCommand command;
    private final Task task;
    private Thread thread;
    private volatile JobState state = JobState.NOT_STARTED;
    private Consumer<Integer> doneCallback;
    private Queue<Message> jobMessageQueue = new ConcurrentLinkedQueue<Message>();
    private Lock lock = new ReentrantLock(true); // true indicates a fair lock to avoid starvation
    private List<Runnable> atExitHandlers = new ArrayList<>();
    private final ResourceTracker<BlockPack> blockpacks;
    private final ResourceTracker<BlockPacker> blockpackers;

    // Special prefix for commands and function calls emitted from stdout of scripts, for example:
    // - script function call: "?mnsc:123 my_func [4, 5, 6]"
    // - script system call: "?mnsc:0 exit! []"
    private static final String FUNCTION_PREFIX = "?mnsc:";

    public Job(
        int jobId, ScriptConfig.BoundCommand command, Task task, Consumer<Integer> doneCallback) {
      this.jobId = jobId;
      this.command = command;
      this.task = task;
      this.doneCallback = doneCallback;
      blockpacks = new ResourceTracker<>(BlockPack.class, jobId);
      blockpackers = new ResourceTracker<>(BlockPacker.class, jobId);
    }

    public void addAtExitHandler(Runnable handler) {
      atExitHandlers.add(handler);
    }

    @Override
    public JobState state() {
      return state;
    }

    @Override
    public void yield() {
      // Lock and immediately unlock to respect job suspension which holds this lock.
      lock.lock();
      lock.unlock();
    }

    @Override
    public Queue<Message> messageQueue() {
      return jobMessageQueue;
    }

    @Override
    public boolean respond(long functionCallId, JsonElement returnValue, boolean finalReply) {
      boolean result = task.sendResponse(functionCallId, returnValue, finalReply);
      if (functionCallId == 0
          && returnValue.isJsonPrimitive()
          && returnValue instanceof JsonPrimitive primitive
          && primitive.isString()
          && primitive.getAsString().equals("exit!")) {
        state = JobState.DONE;
      }
      return result;
    }

    @Override
    public void raiseException(long functionCallId, ExceptionInfo exception) {
      task.sendException(functionCallId, exception);
    }

    @Override
    public void processStdout(String text) {
      // Stdout lines with FUNCTION_PREFIX are handled as function calls regardless of redirection.
      if (text.startsWith(FUNCTION_PREFIX)) {
        jobMessageQueue.add(Message.createFunctionCall(text.substring(FUNCTION_PREFIX.length())));
        return;
      }

      switch (command.redirects().stdout()) {
        case CHAT:
          if (text.startsWith("/")) {
            jobMessageQueue.add(Message.createMinecraftCommand(text.substring(1)));
          } else if (text.startsWith("\\")) {
            jobMessageQueue.add(Message.createMinescriptCommand(text.substring(1)));
          } else {
            jobMessageQueue.add(Message.createChatMessage(text));
          }
          break;
        case DEFAULT:
        case ECHO:
          jobMessageQueue.add(Message.fromPlainText(text));
          break;
        case LOG:
          LOGGER.info(text);
          break;
        case NULL:
          break;
      }
    }

    @Override
    public void processStderr(String text) {
      var match = stderrChatIgnorePattern.matcher(text);
      if (match.find()) {
        return;
      }

      switch (command.redirects().stderr()) {
        case CHAT:
          if (text.startsWith("/")) {
            jobMessageQueue.add(Message.createMinecraftCommand(text.substring(1)));
          } else if (text.startsWith("\\")) {
            jobMessageQueue.add(Message.createMinescriptCommand(text.substring(1)));
          } else {
            jobMessageQueue.add(Message.createChatMessage(text));
          }
          break;
        case DEFAULT:
        case ECHO:
          jobMessageQueue.add(formatAsJsonText(text, "yellow"));
          break;
        case LOG:
          LOGGER.info(text);
          break;
        case NULL:
          break;
      }
    }

    @Override
    public void logJobException(Exception e) {
      var sw = new StringWriter();
      var pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      logUserError(
          "Exception in job `{}`: {} (see logs/latest.log for details)",
          jobSummary(),
          e.toString());
      LOGGER.error("exception stack trace in job `{}`: {}", jobSummary(), sw.toString());
    }

    public void start() {
      thread =
          new Thread(this::runOnJobThread, String.format("job-%d-%s", jobId, command.command()[0]));
      thread.start();
    }

    public boolean suspend() {
      if (state == JobState.KILLED) {
        logUserError("Job already killed: {}", jobSummary());
        return false;
      }
      if (state == JobState.SUSPENDED) {
        logUserError("Job already suspended: {}", jobSummary());
        return false;
      }
      try {
        int timeoutSeconds = 2;
        if (lock.tryLock(timeoutSeconds, TimeUnit.SECONDS)) {
          state = JobState.SUSPENDED;
          for (var entry : chunkLoadEventListeners.entrySet()) {
            if (entry.getKey().jobId == jobId) {
              var listener = entry.getValue();
              listener.suspend();
            }
          }
          return true;
        } else {
          logUserError(
              "Timed out trying to suspend job after {} seconds: {}", timeoutSeconds, jobSummary());
          return false;
        }
      } catch (InterruptedException e) {
        logException(e);
        return false;
      }
    }

    public boolean resume() {
      if (state != JobState.SUSPENDED && state != JobState.KILLED) {
        logUserError("Job not suspended: {}", jobSummary());
        return false;
      }
      if (state == JobState.SUSPENDED) {
        state = JobState.RUNNING;

        var iter = chunkLoadEventListeners.entrySet().iterator();
        while (iter.hasNext()) {
          var entry = iter.next();
          if (entry.getKey().jobId == jobId) {
            var listener = entry.getValue();
            listener.resume();
            listener.updateChunkStatuses();
            if (listener.isFullyLoaded()) {
              listener.onFinished(true);
              iter.remove();
            }
          }
        }
      }
      try {
        lock.unlock();
      } catch (IllegalMonitorStateException e) {
        logException(e);
        return false;
      }
      return true;
    }

    public void kill() {
      JobState prevState = state;
      state = JobState.KILLED;
      if (prevState == JobState.SUSPENDED) {
        resume();
      }
    }

    private void runOnJobThread() {
      if (state == JobState.NOT_STARTED) {
        state = JobState.RUNNING;
      }
      try {
        final long startTimeMillis = System.currentTimeMillis();
        final long longRunningJobThreshold = 3000L;
        int exitCode = task.run(command, this);

        final int millisToSleep = 1000;
        while (state != JobState.KILLED && state != JobState.DONE && !jobMessageQueue.isEmpty()) {
          try {
            Thread.sleep(millisToSleep);
          } catch (InterruptedException e) {
            logJobException(e);
          }
        }
        final long endTimeMillis = System.currentTimeMillis();
        if (exitCode != 0) {
          logUserError(jobSummaryWithStatus("Exited with error code " + exitCode));
        } else if (endTimeMillis - startTimeMillis > longRunningJobThreshold) {
          if (state != JobState.KILLED) {
            state = JobState.DONE;
          }
          logUserInfo(toString());
        }
      } finally {
        doneCallback.accept(jobId);
        for (Runnable handler : atExitHandlers) {
          handler.run();
        }
        blockpacks.releaseAll();
        blockpackers.releaseAll();
      }
    }

    public int jobId() {
      return jobId;
    }

    public String jobSummary() {
      return jobSummaryWithStatus("");
    }

    private String jobSummaryWithStatus(String status) {
      String displayCommand = quoteCommand(command.command());
      if (displayCommand.length() > 61) {
        displayCommand = displayCommand.substring(0, 61) + "...";
      }
      return String.format(
          "[%d] %s%s%s", jobId, status, status.isEmpty() ? "" : ": ", displayCommand);
    }

    @Override
    public String toString() {
      return jobSummaryWithStatus(state.toString());
    }
  }

  static class ScriptFunctionCall {
    private final JobControl job;
    private final long funcCallId;

    public ScriptFunctionCall(JobControl job, long funcCallId) {
      this.job = job;
      this.funcCallId = funcCallId;
    }

    public boolean respond(JsonElement returnValue, boolean finalReply) {
      return job.respond(funcCallId, returnValue, finalReply);
    }

    public void raiseException(ExceptionInfo exception) {
      job.raiseException(funcCallId, exception);
    }
  }

  static class SubprocessTask implements Task {
    private Process process;
    private BufferedWriter stdinWriter;

    @Override
    public int run(ScriptConfig.BoundCommand command, JobControl jobControl) {
      var exec = scriptConfig.getExecutableCommand(command);
      if (exec == null) {
        jobControl.log(
            "Command execution not configured for \"{}\": {}",
            command.fileExtension(),
            configFile.getAbsolutePath());
        return -1;
      }

      try {
        process = Runtime.getRuntime().exec(exec.command(), exec.environment());
      } catch (IOException e) {
        jobControl.logJobException(e);
        return -2;
      }

      stdinWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));

      try (var stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
          var stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
        final int millisToSleep = 1;
        final long trailingReadTimeoutMillis = 5000;
        long lastReadTime = System.currentTimeMillis();
        String line;
        while (jobControl.state() != JobState.KILLED
            && jobControl.state() != JobState.DONE
            && (process.isAlive()
                || System.currentTimeMillis() - lastReadTime < trailingReadTimeoutMillis)) {
          if (stdoutReader.ready()) {
            if ((line = stdoutReader.readLine()) == null) {
              break;
            }
            lastReadTime = System.currentTimeMillis();
            jobControl.processStdout(line);
          }
          if (stderrReader.ready()) {
            if ((line = stderrReader.readLine()) == null) {
              break;
            }
            lastReadTime = System.currentTimeMillis();
            jobControl.log(line);
          }
          try {
            Thread.sleep(millisToSleep);
          } catch (InterruptedException e) {
            jobControl.logJobException(e);
          }
          jobControl.yield();
        }
      } catch (IOException e) {
        jobControl.logJobException(e);
        jobControl.log(e.getMessage());
        return -3;
      }

      LOGGER.info("Exited script event loop for job `{}`", jobControl.toString());

      if (process == null) {
        return -4;
      }
      if (jobControl.state() == JobState.KILLED) {
        LOGGER.info("Killing script process for job `{}`", jobControl.toString());
        process.destroy();
        return -5;
      }
      try {
        LOGGER.info("Waiting for script process to complete for job `{}`", jobControl.toString());
        int result = process.waitFor();
        LOGGER.info("Script process exited with {} for job `{}`", result, jobControl.toString());
        return result;
      } catch (InterruptedException e) {
        jobControl.logJobException(e);
        return -6;
      }
    }

    @Override
    public boolean sendResponse(long functionCallId, JsonElement returnValue, boolean finalReply) {
      if (!isReadyToRespond()) {
        return false;
      }
      try {
        var response = new JsonObject();
        response.addProperty("fcid", functionCallId);
        response.add("retval", returnValue);
        if (finalReply) {
          response.addProperty("conn", "close");
        }
        stdinWriter.write(GSON.toJson(response));
        stdinWriter.newLine();
        stdinWriter.flush();
        return true;
      } catch (IOException e) {
        LOGGER.error("IOException in SubprocessTask sendResponse: {}", e.getMessage());
        return false;
      }
    }

    @Override
    public boolean sendException(long functionCallId, ExceptionInfo exception) {
      if (!isReadyToRespond()) {
        return false;
      }
      try {
        var response = new JsonObject();
        response.addProperty("fcid", functionCallId);
        response.addProperty("conn", "close");
        var json = GSON.toJsonTree(exception);
        LOGGER.warn("Translating Java exception as JSON: {}", json);
        response.add("except", json);

        stdinWriter.write(GSON.toJson(response));
        stdinWriter.newLine();
        stdinWriter.flush();
        return true;
      } catch (IOException e) {
        LOGGER.error("IOException in SubprocessTask sendResponse: {}", e.getMessage());
        return false;
      }
    }

    private boolean isReadyToRespond() {
      return process != null && process.isAlive() && stdinWriter != null;
    }
  }

  static class UndoTask implements Task {
    private final UndoableAction undo;

    public UndoTask(UndoableAction undo) {
      this.undo = undo;
    }

    @Override
    public int run(ScriptConfig.BoundCommand command, JobControl jobControl) {
      undo.enqueueCommands(jobControl.messageQueue());
      return 0;
    }
  }

  static class JobManager {
    private final Map<Integer, Job> jobMap = new ConcurrentHashMap<Integer, Job>();
    private int nextJobId = 1;

    // Map from ID of original job (not an undo) to its corresponding undo, if applicable.
    private final Map<Integer, UndoableAction> jobUndoMap =
        new ConcurrentHashMap<Integer, UndoableAction>();

    private final Deque<UndoableAction> undoStack = new ArrayDeque<>();

    public void createSubprocess(ScriptConfig.BoundCommand command, List<Token> nextCommand) {
      var job =
          new Job(allocateJobId(), command, new SubprocessTask(), i -> finishJob(i, nextCommand));
      var undo = new UndoableActionBlockPack(job.jobId(), command.command());
      jobUndoMap.put(job.jobId(), undo);
      undoStack.addFirst(undo);
      jobMap.put(job.jobId(), job);
      job.start();
    }

    public Optional<UndoableAction> getUndoForJob(Job job) {
      var undo = jobUndoMap.get(job.jobId());
      if (undo == null) {
        return Optional.empty();
      }
      return Optional.of(undo);
    }

    public void startUndo() {
      var undo = undoStack.pollFirst();
      if (undo == null) {
        logUserError("The undo stack is empty.");
        return;
      }

      // If the job being undone is still alive, kill it.
      int originalJobId = undo.originalJobId();
      if (originalJobId != -1) {
        var job = jobMap.get(undo.originalJobId());
        if (job != null) {
          job.kill();
        }
      }

      var undoJob =
          new Job(
              allocateJobId(),
              new ScriptConfig.BoundCommand(
                  null, undo.derivativeCommand(), ScriptRedirect.Pair.DEFAULTS),
              new UndoTask(undo),
              i -> finishJob(i, Collections.emptyList()));
      jobMap.put(undoJob.jobId(), undoJob);
      undoJob.start();
    }

    private synchronized int allocateJobId() {
      if (jobMap.isEmpty()) {
        nextJobId = 1;
      }
      return nextJobId++;
    }

    private void finishJob(int jobId, List<Token> nextCommand) {
      var undo = jobUndoMap.remove(jobId);
      if (undo != null) {
        undo.onOriginalJobDone();
      }
      jobMap.remove(jobId);
      runParsedMinescriptCommand(nextCommand);
    }

    public Map<Integer, Job> getMap() {
      return jobMap;
    }
  }

  private static JobManager jobs = new JobManager();

  private static Queue<Message> systemMessageQueue = new ConcurrentLinkedQueue<Message>();

  private static boolean checkMinescriptDir() {
    Path minescriptDir = Paths.get(System.getProperty("user.dir"), MINESCRIPT_DIR);
    if (!Files.isDirectory(minescriptDir)) {
      logUserError(
          "Minescript folder is missing. It should have been created at: {}", minescriptDir);
      return false;
    }
    return true;
  }

  public enum ParamType {
    INT,
    BOOL,
    STRING,
    VAR_ARGS // matches any number of optional args at the end of the arg list
  }

  private static boolean checkParamTypes(String[] command, ParamType... types) {
    if (types.length == 0 || types[types.length - 1] != ParamType.VAR_ARGS) {
      // No terminating varargs param.
      if (command.length - 1 != types.length) {
        return false;
      }
    } else {
      // Formal params have a terminating varargs param. The command name (which isn't a param) at
      // command[0] and varargs at types[types.length - 1] don't count toward the number of params
      // to compare. (Technically there's no need to subtract one from each side, but being more
      // explicit about what's being compared is arguably more clear.)
      if (command.length - 1 < types.length - 1) {
        return false;
      }
    }

    for (int i = 0; i < types.length; i++) {
      if (types[i] == ParamType.VAR_ARGS) {
        break;
      }
      String param = command[i + 1];
      switch (types[i]) {
        case INT:
          try {
            Integer.valueOf(param);
          } catch (NumberFormatException e) {
            return false;
          }
          break;
        case BOOL:
          if (!param.equals("true") && !param.equals("false")) {
            return false;
          }
          break;
        case STRING:
          // Do nothing. String params are always valid.
          break;
      }
    }
    return true;
  }

  private static String getParamsAsString(String[] command) {
    var result = new StringBuilder();
    for (int i = 1; i < command.length; i++) {
      if (result.length() > 0) {
        result.append(' ');
      }
      result.append(quoteString(command[i]));
    }
    return result.toString();
  }

  public static void logUserInfo(String messagePattern, Object... arguments) {
    String logMessage = ParameterizedMessage.format(messagePattern, arguments);
    LOGGER.info("{}", logMessage);
    systemMessageQueue.add(formatAsJsonText(logMessage, "yellow"));
  }

  public static void logUserError(String messagePattern, Object... arguments) {
    String logMessage = ParameterizedMessage.format(messagePattern, arguments);
    LOGGER.error("{}", logMessage);
    systemMessageQueue.add(formatAsJsonText(logMessage, "red"));
  }

  private static void listJobs() {
    if (jobs.getMap().isEmpty()) {
      logUserInfo("There are no jobs running.");
      return;
    }
    for (var job : jobs.getMap().values()) {
      logUserInfo(job.toString());
    }
  }

  private static void suspendJob(OptionalInt jobId) {
    if (jobId.isPresent()) {
      // Suspend specified job.
      var job = jobs.getMap().get(jobId.getAsInt());
      if (job == null) {
        logUserError("No job with ID {}. Use \\jobs to list jobs.", jobId.getAsInt());
        return;
      }
      if (job.suspend()) {
        logUserInfo("Job suspended: {}", job.jobSummary());
      }
    } else {
      // Suspend all jobs.
      for (var job : jobs.getMap().values()) {
        if (job.suspend()) {
          logUserInfo("Job suspended: {}", job.jobSummary());
        }
      }
    }
  }

  private static void resumeJob(OptionalInt jobId) {
    if (jobId.isPresent()) {
      // Resume specified job.
      var job = jobs.getMap().get(jobId.getAsInt());
      if (job == null) {
        logUserError("No job with ID {}. Use \\jobs to list jobs.", jobId.getAsInt());
        return;
      }
      if (job.resume()) {
        logUserInfo("Job resumed: {}", job.jobSummary());
      }
    } else {
      // Resume all jobs.
      for (var job : jobs.getMap().values()) {
        if (job.resume()) {
          logUserInfo("Job resumed: {}", job.jobSummary());
        }
      }
    }
  }

  private static void killJob(int jobId) {
    if (jobId == -1) {
      // Special pseudo job ID -1 kills all jobs.
      for (var job : jobs.getMap().values()) {
        job.kill();
      }
      return;
    }
    var job = jobs.getMap().get(jobId);
    if (job == null) {
      logUserError("No job with ID {}. Use \\jobs to list jobs.", jobId);
      return;
    }
    job.kill();
    logUserInfo("Removed job: {}", job.jobSummary());
  }

  private static Pattern SETBLOCK_COMMAND_RE =
      Pattern.compile("setblock ([^ ]+) ([^ ]+) ([^ ]+).*");

  private static boolean getSetblockCoords(String setblockCommand, int[] coords) {
    var match = SETBLOCK_COMMAND_RE.matcher(setblockCommand);
    if (!match.find()) {
      return false;
    }
    if (setblockCommand.contains("~")) {
      logUserInfo("Warning: /setblock commands with ~ syntax cannot be undone.");
      logUserInfo("           Use minescript.player_position() instead.");
      return false;
    }
    try {
      coords[0] = Integer.valueOf(match.group(1));
      coords[1] = Integer.valueOf(match.group(2));
      coords[2] = Integer.valueOf(match.group(3));
    } catch (NumberFormatException e) {
      logUserError(
          "Error: invalid number format for /setblock coordinates: {} {} {}",
          match.group(1),
          match.group(2),
          match.group(3));
      return false;
    }
    return true;
  }

  private static Pattern FILL_COMMAND_RE =
      Pattern.compile("fill ([^ ]+) ([^ ]+) ([^ ]+) ([^ ]+) ([^ ]+) ([^ ]+).*");

  private static boolean getFillCoords(String fillCommand, int[] coords) {
    var match = FILL_COMMAND_RE.matcher(fillCommand);
    if (!match.find()) {
      return false;
    }
    if (fillCommand.contains("~")) {
      logUserError("Warning: /fill commands with ~ syntax cannot be undone.");
      logUserError("           Use minescript.player_position() instead.");
      return false;
    }
    coords[0] = Integer.valueOf(match.group(1));
    coords[1] = Integer.valueOf(match.group(2));
    coords[2] = Integer.valueOf(match.group(3));
    coords[3] = Integer.valueOf(match.group(4));
    coords[4] = Integer.valueOf(match.group(5));
    coords[5] = Integer.valueOf(match.group(6));
    return true;
  }

  private static int worldCoordToChunkCoord(int x) {
    return (x >= 0) ? (x / 16) : (((x + 1) / 16) - 1);
  }

  // BlockState#toString() returns a string formatted as:
  // "Block{minecraft:acacia_button}[face=floor,facing=west,powered=false]"
  //
  // BLOCK_STATE_RE helps transform this to:
  // "minecraft:acacia_button[face=floor,facing=west,powered=false]"
  private static Pattern BLOCK_STATE_RE = Pattern.compile("^Block\\{([^}]*)\\}(\\[.*\\])?$");

  private static Optional<String> blockStateToString(BlockState blockState) {
    var match = BLOCK_STATE_RE.matcher(blockState.toString());
    if (!match.find()) {
      return Optional.empty();
    }
    String blockType = match.group(1);
    String blockAttrs = match.group(2) == null ? "" : match.group(2);
    return Optional.of(blockType + blockAttrs);
  }

  private static void readBlocks(
      int x0,
      int y0,
      int z0,
      int x1,
      int y1,
      int z1,
      boolean safetyLimit,
      BlockPack.BlockConsumer blockConsumer) {
    var minecraft = Minecraft.getInstance();
    var player = minecraft.player;
    if (player == null) {
      throw new IllegalStateException("Unable to read blocks because player is null.");
    }

    int playerX = (int) player.getX();
    int playerY = (int) player.getY();
    int playerZ = (int) player.getZ();

    int xMin = Math.min(x0, x1);
    int yMin = Math.max(Math.min(y0, y1), -64); // TODO(maxuser): Use an API for min build height.
    int zMin = Math.min(z0, z1);

    int xMax = Math.max(x0, x1);
    int yMax = Math.min(Math.max(y0, y1), 320); // TODO(maxuser): Use an API for max build height.
    int zMax = Math.max(z0, z1);

    if (safetyLimit) {
      // Estimate the number of chunks to check against a soft limit.
      int numChunks = ((xMax - xMin) / 16 + 1) * ((zMax - zMin) / 16 + 1);
      if (numChunks > 1600) {
        throw new IllegalArgumentException(
            "`blockpack_read_world` exceeded soft limit of 1600 chunks (region covers "
                + numChunks
                + " chunks; "
                + "override this safety check by passing `no_limit` to `copy` command or "
                + "`safety_limit=False` to `blockpack_read_world` function).");
      }
    }

    Level level = player.getCommandSenderWorld();

    var pos = new BlockPos.MutableBlockPos();
    for (int x = xMin; x <= xMax; x += 16) {
      for (int z = zMin; z <= zMax; z += 16) {
        Optional<String> block = blockStateToString(level.getBlockState(pos.set(x, 0, z)));
        if (block.isEmpty() || block.get().equals("minecraft:void_air")) {
          throw new IllegalStateException(
              "Not all chunks are loaded within the requested `copy` volume.");
        }
      }
    }

    int numBlocks = 0;

    for (int x = xMin; x <= xMax; ++x) {
      for (int y = yMin; y <= yMax; ++y) {
        for (int z = zMin; z <= zMax; ++z) {
          BlockState blockState = level.getBlockState(pos.set(x, y, z));
          if (!blockState.isAir()) {
            Optional<String> block = blockStateToString(blockState);
            if (block.isPresent()) {
              blockConsumer.setblock(x, y, z, block.get());
              numBlocks++;
            } else {
              logUserError("Unexpected BlockState format: {}", blockState.toString());
            }
          }
        }
      }
    }
  }

  public static final String[] EMPTY_STRING_ARRAY = {};

  private static void runMinescriptCommand(String commandLine) {
    try {
      if (!checkMinescriptDir()) {
        return;
      }

      // Check if config needs to be reloaded.
      loadConfig();

      List<Token> tokens = parseCommand(commandLine);

      if (tokens.isEmpty()) {
        systemMessageQueue.add(
            Message.fromJsonFormattedText(
                "{\"text\":\"Technoblade never dies.\",\"color\":\"dark_red\",\"bold\":true}"));
        return;
      }

      runParsedMinescriptCommand(tokens);

    } catch (RuntimeException e) {
      logException(e);
    }
  }

  private static void runParsedMinescriptCommand(List<Token> tokens) {
    if (tokens.isEmpty()) {
      return;
    }

    try {
      // Split the token list on the first semicolon token, if there is one.
      int semicolonPos = tokens.indexOf(Token.semicolon());
      List<Token> nextCommand = Collections.emptyList();
      if (semicolonPos != -1) {
        nextCommand = tokens.subList(semicolonPos + 1, tokens.size());
        tokens = tokens.subList(0, semicolonPos);
      }
      if (tokens.isEmpty()) {
        runParsedMinescriptCommand(nextCommand);
        return;
      }

      List<String> tokenStrings = tokens.stream().map(Token::toString).collect(Collectors.toList());
      String[] command = substituteMinecraftVars(tokenStrings.toArray(EMPTY_STRING_ARRAY));

      switch (command[0]) {
        case "jobs":
          if (checkParamTypes(command)) {
            listJobs();
          } else {
            logUserError("Expected no params, instead got `{}`", getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "suspend":
        case "z":
          if (checkParamTypes(command)) {
            suspendJob(OptionalInt.empty());
          } else if (checkParamTypes(command, ParamType.INT)) {
            suspendJob(OptionalInt.of(Integer.valueOf(command[1])));
          } else {
            logUserError(
                "Expected no params or 1 param of type integer, instead got `{}`",
                getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "resume":
          if (checkParamTypes(command)) {
            resumeJob(OptionalInt.empty());
          } else if (checkParamTypes(command, ParamType.INT)) {
            resumeJob(OptionalInt.of(Integer.valueOf(command[1])));
          } else {
            logUserError(
                "Expected no params or 1 param of type integer, instead got `{}`",
                getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "killjob":
          if (checkParamTypes(command, ParamType.INT)) {
            killJob(Integer.valueOf(command[1]));
          } else {
            logUserError(
                "Expected 1 param of type integer, instead got `{}`", getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "undo":
          if (checkParamTypes(command)) {
            jobs.startUndo();
          } else {
            logUserError(
                "Expected no params or 1 param of type integer, instead got `{}`",
                getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "which":
          if (checkParamTypes(command, ParamType.STRING)) {
            String arg = command[1];
            if (BUILTIN_COMMANDS.contains(arg)) {
              logUserInfo("Built-in command: `{}`", arg);
            } else {
              Path commandPath = scriptConfig.resolveCommandPath(arg);
              if (commandPath == null) {
                logUserInfo("Command `{}` not found.", arg);
              } else {
                logUserInfo(commandPath.toString());
              }
            }
          } else {
            logUserError(
                "Expected 1 param of type string, instead got `{}`", getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "reload_minescript_resources":
          loadMinescriptResources();
          logUserInfo("Reloaded resources from Minescript jar.");
          runParsedMinescriptCommand(nextCommand);
          return;

        case "minescript_commands_per_cycle":
          if (checkParamTypes(command)) {
            logUserInfo(
                "Minescript executing {} command(s) per cycle.", minescriptCommandsPerCycle);
          } else if (checkParamTypes(command, ParamType.INT)) {
            int numCommands = Integer.valueOf(command[1]);
            if (numCommands < 1) numCommands = 1;
            minescriptCommandsPerCycle = numCommands;
            logUserInfo("Minescript execution set to {} command(s) per cycle.", numCommands);
          } else {
            logUserError(
                "Expected 1 param of type integer, instead got `{}`", getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "minescript_ticks_per_cycle":
          if (checkParamTypes(command)) {
            logUserInfo("Minescript executing {} tick(s) per cycle.", minescriptTicksPerCycle);
          } else if (checkParamTypes(command, ParamType.INT)) {
            int ticks = Integer.valueOf(command[1]);
            if (ticks < 1) ticks = 1;
            minescriptTicksPerCycle = ticks;
            logUserInfo("Minescript execution set to {} tick(s) per cycle.", ticks);
          } else {
            logUserError(
                "Expected 1 param of type integer, instead got `{}`", getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "minescript_incremental_command_suggestions":
          if (checkParamTypes(command, ParamType.BOOL)) {
            boolean value = Boolean.valueOf(command[1]);
            incrementalCommandSuggestions = value;
            logUserInfo("Minescript incremental command suggestions set to {}", value);
          } else {
            logUserError(
                "Expected 1 param of type boolean, instead got `{}`", getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "minescript_script_function_debug_outptut":
          if (checkParamTypes(command, ParamType.BOOL)) {
            boolean value = Boolean.valueOf(command[1]);
            scriptFunctionDebugOutptut = value;
            logUserInfo("Minescript script function debug output set to {}", value);
          } else {
            logUserError(
                "Expected 1 param of type boolean, instead got `{}`", getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "minescript_log_chunk_load_events":
          if (checkParamTypes(command, ParamType.BOOL)) {
            boolean value = Boolean.valueOf(command[1]);
            logChunkLoadEvents = value;
            logUserInfo("Minescript logging of chunk load events set to {}", value);
          } else {
            logUserError(
                "Expected 1 param of type boolean, instead got `{}`", getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "enable_minescript_on_chat_received_event":
          if (checkParamTypes(command, ParamType.BOOL)) {
            boolean enable = command[1].equals("true");
            enableMinescriptOnChatReceivedEvent = enable;
            logUserInfo(
                "Minescript execution on client chat events {}.{}",
                (enable ? "enabled" : "disabled"),
                (enable
                    ? " e.g. add command to command block: [execute as Player run tell Player"
                        + " \\help]"
                    : ""));
          } else {
            logUserError(
                "Expected 1 param of type boolean, instead got `{}`", getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "NullPointerException":
          // This is for testing purposes only. Throw NPE only if we're in debug mode.
          if (scriptFunctionDebugOutptut) {
            String s = null;
            logUserError("Length of a null string is {}", s.length());
          }
      }

      // Rename "copy" to "copy_blocks" for backward compatibility since copy.py was renamed to
      // copy_blocks.py.
      if ("copy".equals(command[0])) {
        command[0] = "copy_blocks";
      }

      Path commandPath = scriptConfig.resolveCommandPath(command[0]);
      if (commandPath == null) {
        logUserInfo("Minescript built-in commands:");
        for (String builtin : BUILTIN_COMMANDS) {
          logUserInfo("  {}", builtin);
        }
        logUserInfo("");
        logUserInfo("Minescript command directories:");
        Path minescriptDir = Paths.get(System.getProperty("user.dir"), MINESCRIPT_DIR);
        for (Path commandDir : scriptConfig.commandPath()) {
          Path path = minescriptDir.resolve(commandDir);
          logUserInfo("  {}", path);
        }
        if (!command[0].equals("ls")) {
          logUserError("No Minescript command named \"{}\"", command[0]);
        }
        runParsedMinescriptCommand(nextCommand);
        return;
      }

      var redirects = ScriptRedirect.parseAndRemoveRedirects(tokenStrings);

      // Reassign command based on potentially updated tokenStrings.
      command = substituteMinecraftVars(tokenStrings.toArray(EMPTY_STRING_ARRAY));

      jobs.createSubprocess(
          new ScriptConfig.BoundCommand(commandPath, command, redirects), nextCommand);

    } catch (RuntimeException e) {
      logException(e);
    }
  }

  private static int minescriptTicksPerCycle = 1;
  private static int minescriptCommandsPerCycle = 15;

  private static int renderTickEventCounter = 0;
  private static int clientTickEventCounter = 0;

  private static int BACKSLASH_KEY = 92;
  private static int ESCAPE_KEY = 256;
  public static int ENTER_KEY = 257;
  private static int TAB_KEY = 258;
  private static int BACKSPACE_KEY = 259;
  private static int UP_ARROW_KEY = 265;
  private static int DOWN_ARROW_KEY = 264;

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
      for (int j = 0; j < end; j++) {
        if (string.charAt(j) != longest.charAt(j)) {
          longest = longest.substring(0, j);
          break;
        }
      }
    }
    return longest;
  }

  private static List<String> commandSuggestions = new ArrayList<>();

  private static class MinescriptCommandHistory {
    private final List<String> commandList = new ArrayList<>();
    private int commandPosition;

    public MinescriptCommandHistory() {
      commandList.add("");
      commandPosition = 0;
    }

    public Optional<String> moveBackwardAndGet(String currentCommand) {
      // Temporarily add currentCommand as the final command if current position is at the final
      // command.
      if (commandPosition == 0) {
        return Optional.empty();
      }
      if (commandPosition == lastCommandPosition()) {
        commandList.set(commandPosition, currentCommand);
      }
      commandPosition--;
      return Optional.of(commandList.get(commandPosition));
    }

    public Optional<String> moveForwardAndGet() {
      if (commandPosition == lastCommandPosition()) {
        return Optional.empty();
      }
      commandPosition++;
      return Optional.of(commandList.get(commandPosition));
    }

    public void addCommand(String command) {
      // Command list of size 1 contains only the empty placeholder command. Ignore duplicate
      // consecutive user commands.
      if (commandList.size() == 1 || !command.equals(commandList.get(lastCommandPosition() - 1))) {
        commandList.add(lastCommandPosition(), command);
        moveToEnd();
      }
    }

    public void moveToEnd() {
      commandPosition = lastCommandPosition();
      commandList.set(commandPosition, "");
    }

    private int lastCommandPosition() {
      return commandList.size() - 1;
    }
  }

  public static void onKeyboardEvent(int key, int scanCode, int action, int modifiers) {
    var iter = keyEventListeners.entrySet().iterator();
    JsonObject json = null;
    while (iter.hasNext()) {
      var listener = iter.next();
      if (json == null) {
        String screenName = getScreenName().orElse(null);
        long timeMillis = System.currentTimeMillis();
        json = new JsonObject();
        json.addProperty("key", key);
        json.addProperty("scanCode", scanCode);
        json.addProperty("action", action);
        json.addProperty("modifiers", modifiers);
        json.addProperty("timeMillis", timeMillis);
        json.addProperty("screen", screenName);
      }
      LOGGER.info("Forwarding key event to listener {}: {}", listener.getKey(), json);
      if (!listener.getValue().respond(json, false)) {
        iter.remove();
      }
    }
  }

  public static void onMouseClick(int button, int action, int modifiers, double x, double y) {
    var minecraft = Minecraft.getInstance();
    var iter = mouseEventListeners.entrySet().iterator();
    JsonObject json = null;
    while (iter.hasNext()) {
      var listener = iter.next();
      if (json == null) {
        String screenName = getScreenName().orElse(null);
        long timeMillis = System.currentTimeMillis();
        json = new JsonObject();
        json.addProperty("button", button);
        json.addProperty("action", action);
        json.addProperty("modifiers", modifiers);
        json.addProperty("timeMillis", timeMillis);
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("screen", screenName);
      }
      LOGGER.info("Forwarding mouse event to listener {}: {}", listener.getKey(), json);
      if (!listener.getValue().respond(json, false)) {
        iter.remove();
      }
    }
  }

  private static EditBox chatEditBox = null;
  private static boolean reportedChatEditBoxError = false;

  public static void setChatScreenInput(EditBox input) {
    chatEditBox = input;
  }

  private static MinescriptCommandHistory minescriptCommandHistory = new MinescriptCommandHistory();
  private static boolean incrementalCommandSuggestions = false;

  public static boolean onKeyboardKeyPressed(Screen screen, int key) {
    boolean cancel = false;
    if (screen != null && screen instanceof ChatScreen) {
      if (chatEditBox == null) {
        if (!reportedChatEditBoxError) {
          reportedChatEditBoxError = true;
          logUserError(
              "Minescript internal error: Expected ChatScreen.input to be initialized by"
                  + " ChatScreen.init(), but it's null instead. Minescript commands sent through"
                  + " chat will not be interpreted as commands, and sent as normal chats instead.");
        }
        return cancel;
      }
      String value = chatEditBox.getValue();
      if (!value.startsWith("\\")) {
        minescriptCommandHistory.moveToEnd();
        if (key == ENTER_KEY
            && (customNickname != null || chatInterceptor != null)
            && !value.startsWith("/")) {
          cancel = true;
          chatEditBox.setValue("");
          onClientChat(value);
          screen.onClose();
        }
        return cancel;
      }
      if (key == UP_ARROW_KEY) {
        Optional<String> previousCommand = minescriptCommandHistory.moveBackwardAndGet(value);
        if (previousCommand.isPresent()) {
          value = previousCommand.get();
          chatEditBox.setValue(value);
          chatEditBox.setCursorPosition(value.length());
        }
        cancel = true;
      } else if (key == DOWN_ARROW_KEY) {
        Optional<String> nextCommand = minescriptCommandHistory.moveForwardAndGet();
        if (nextCommand.isPresent()) {
          value = nextCommand.get();
          chatEditBox.setValue(value);
          chatEditBox.setCursorPosition(value.length());
        }
        cancel = true;
      } else if (key == ENTER_KEY) {
        cancel = true;
        String text = chatEditBox.getValue();
        chatEditBox.setValue("");
        onClientChat(text);
        screen.onClose();
        return cancel;
      } else {
        minescriptCommandHistory.moveToEnd();
      }
      int cursorPos = chatEditBox.getCursorPosition();
      if (key >= 32 && key < 127) {
        // TODO(maxuser): use chatEditBox.setSuggestion(String) to set suggestion?
        // TODO(maxuser): detect upper vs lower case properly
        String extraChar = Character.toString((char) key).toLowerCase();
        value = insertSubstring(value, cursorPos, extraChar);
      } else if (key == BACKSPACE_KEY) {
        value = eraseChar(value, cursorPos);
      }
      if (value.stripTrailing().length() > 0) {
        String command = value.substring(1).split("\\s+")[0];
        if (key == TAB_KEY && !commandSuggestions.isEmpty()) {
          if (cursorPos == command.length() + 1) {
            // Insert the remainder of the completed command.
            String maybeTrailingSpace =
                ((cursorPos < value.length() && value.charAt(cursorPos) == ' ')
                        || commandSuggestions.size() > 1
                        || commandSuggestions.get(0).endsWith(File.separator))
                    ? ""
                    : " ";
            chatEditBox.insertText(
                longestCommonPrefix(commandSuggestions).substring(command.length())
                    + maybeTrailingSpace);
            if (commandSuggestions.size() > 1) {
              chatEditBox.setTextColor(0x5ee8e8); // cyan for partial completion
            } else {
              chatEditBox.setTextColor(0x5ee85e); // green for full completion
            }
            commandSuggestions = new ArrayList<>();
            return cancel;
          }
        }
        try {
          var scriptCommandNames = scriptConfig.findCommandPrefixMatches(command);
          scriptCommandNames.sort(null);
          if (scriptCommandNames.contains(command)) {
            chatEditBox.setTextColor(0x5ee85e); // green
            commandSuggestions = new ArrayList<>();
          } else {
            List<String> newCommandSuggestions = new ArrayList<>();
            newCommandSuggestions.addAll(scriptCommandNames);
            if (!newCommandSuggestions.isEmpty()) {
              if (!newCommandSuggestions.equals(commandSuggestions)) {
                if (key == TAB_KEY || incrementalCommandSuggestions) {
                  systemMessageQueue.add(formatAsJsonText("completions:", "aqua"));
                  for (String suggestion : newCommandSuggestions) {
                    systemMessageQueue.add(formatAsJsonText("  " + suggestion, "aqua"));
                  }
                }
                commandSuggestions = newCommandSuggestions;
              }
              chatEditBox.setTextColor(0x5ee8e8); // cyan
            } else {
              chatEditBox.setTextColor(0xe85e5e); // red
              commandSuggestions = new ArrayList<>();
            }
          }
        } catch (IOException e) {
          logException(e);
        }
      }
    }
    return cancel;
  }

  private static boolean loggedMethodNameFallback = false;

  public static void onKeyInput(int key) {
    var minecraft = Minecraft.getInstance();
    var screen = minecraft.screen;
    if (screen == null && key == BACKSLASH_KEY) {
      minecraft.setScreen(new ChatScreen(""));
    }
  }

  private static boolean enableMinescriptOnChatReceivedEvent = false;
  private static Pattern CHAT_WHISPER_MESSAGE_RE = Pattern.compile("You whisper to [^ :]+: (.*)");

  public static boolean onClientChatReceived(Component message) {
    boolean cancel = false;
    String text = message.getString();

    var iter = clientChatReceivedEventListeners.entrySet().iterator();
    while (iter.hasNext()) {
      var listener = iter.next();
      var jsonText = new JsonPrimitive(text);
      LOGGER.info("Forwarding chat message to listener {}: {}", listener.getKey(), jsonText);
      if (!listener.getValue().respond(jsonText, false)) {
        iter.remove();
      }
    }

    if (enableMinescriptOnChatReceivedEvent) {
      // Respond to messages like this one sent from a command block:
      // [execute as Dev run tell Dev \eval 1+2]
      var matcher = CHAT_WHISPER_MESSAGE_RE.matcher(text);
      if (matcher.find() && matcher.group(1).startsWith("\\")) {
        var command = matcher.group(1);
        LOGGER.info("Processing command from received chat event: {}", command);
        runMinescriptCommand(command.substring(1));
        cancel = true;
      }
    }

    return cancel;
  }

  private static long packInts(int x, int z) {
    return (((long) x) << 32) | (z & 0xffffffffL);
  }

  /** Unpack 64-bit long into two 32-bit ints written to returned 2-element int array. */
  private static int[] unpackLong(long x) {
    return new int[] {(int) (x >> 32), (int) x};
  }

  private static boolean logChunkLoadEvents = false;

  public static void onChunkLoad(LevelAccessor chunkLevel, ChunkAccess chunk) {
    int chunkX = chunk.getPos().x;
    int chunkZ = chunk.getPos().z;
    if (logChunkLoadEvents) {
      LOGGER.info("world {} chunk loaded: {} {}", chunkLevel.hashCode(), chunkX, chunkZ);
    }
    var iter = chunkLoadEventListeners.entrySet().iterator();
    while (iter.hasNext()) {
      var listener = iter.next().getValue();
      if (listener.onChunkLoaded(chunkLevel, chunkX, chunkZ)) {
        iter.remove();
      }
    }
  }

  public static void onChunkUnload(LevelAccessor chunkLevel, ChunkAccess chunk) {
    int chunkX = chunk.getPos().x;
    int chunkZ = chunk.getPos().z;
    if (logChunkLoadEvents) {
      LOGGER.info("world {} chunk unloaded: {} {}", chunkLevel.hashCode(), chunkX, chunkZ);
    }
    for (var entry : chunkLoadEventListeners.entrySet()) {
      var listener = entry.getValue();
      listener.onChunkUnloaded(chunkLevel, chunkX, chunkZ);
    }
  }

  public static boolean onClientChat(String message) {
    boolean cancel = false;
    if (message.startsWith("\\")) {
      minescriptCommandHistory.addCommand(message);

      LOGGER.info("Processing command from chat event: {}", message);
      runMinescriptCommand(message.substring(1));
      cancel = true;
    } else if (chatInterceptor != null && !message.startsWith("/")) {
      chatInterceptor.accept(message);
      cancel = true;
    } else if (customNickname != null && !message.startsWith("/")) {
      String tellrawCommand = "tellraw @a " + String.format(customNickname, message);
      systemMessageQueue.add(Message.createMinecraftCommand(tellrawCommand));
      var minecraft = Minecraft.getInstance();
      var chat = minecraft.gui.getChat();
      // TODO(maxuser): There appears to be a bug truncating the chat HUD command history. It might
      // be that onClientChat(...) can get called on a different thread from what other callers are
      // expecting, thereby corrupting the history. Verify whether this gets called on the same
      // thread as onClientWorldTick() or other events.
      chat.addRecentChat(message);
      cancel = true;
    }
    return cancel;
  }

  private static class ServerBlockList {
    private final Path serverBlockListPath;
    private boolean lastCheckedValue = true;
    private String lastCheckedServerName = "";
    private String lastCheckedServerIp = "";
    private long lastCheckedTime = 0;

    public ServerBlockList() {
      serverBlockListPath = Paths.get(MINESCRIPT_DIR, "server_block_list.txt");
    }

    public boolean areCommandsAllowedForServer(String serverName, String serverIp) {
      if (!Files.exists(serverBlockListPath)) {
        return true;
      }

      if (serverName.equals(lastCheckedServerName)
          && serverIp.equals(lastCheckedServerIp)
          && new File(serverBlockListPath.toString()).lastModified() < lastCheckedTime) {
        return lastCheckedValue;
      }

      lastCheckedServerName = serverName;
      lastCheckedServerIp = serverIp;
      lastCheckedTime = System.currentTimeMillis();

      LOGGER.info("{} modified since last checked; refreshing...", serverBlockListPath.toString());
      try (var reader = new BufferedReader(new FileReader(serverBlockListPath.toString()))) {
        var line = reader.readLine();
        while (line != null) {
          line = line.replaceAll("#.*$", "").strip();
          if (line.equals(serverName) || line.equals(serverIp)) {
            LOGGER.info(
                "Found server match in {}, commands disabled: {}",
                serverBlockListPath.toString(),
                line);
            lastCheckedValue = false;
            return lastCheckedValue;
          }
          line = reader.readLine();
        }
      } catch (IOException e) {
        logException(e);
      }
      LOGGER.info(
          "No server match in {}, commands enabled: {} / {}",
          serverBlockListPath.toString(),
          serverName,
          serverIp);
      lastCheckedValue = true;
      return lastCheckedValue;
    }
  }

  private static ServerBlockList serverBlockList = new ServerBlockList();

  private static Map<Integer, ScriptFunctionCall> keyEventListeners = new ConcurrentHashMap<>();
  private static Map<Integer, ScriptFunctionCall> mouseEventListeners = new ConcurrentHashMap<>();
  private static Map<Integer, ScriptFunctionCall> clientChatReceivedEventListeners =
      new ConcurrentHashMap<>();

  public static class ChunkLoadEventListener {

    interface DoneCallback {
      void done(boolean success);
    }

    // Map packed chunk (x, z) to boolean: true if chunk is loaded, false otherwise.
    private final Map<Long, Boolean> chunksToLoad = new ConcurrentHashMap<>();

    // Level with chunks to listen for. Store hash rather than reference to avoid memory leak.
    private final int levelHashCode;

    private final DoneCallback doneCallback;
    private int numUnloadedChunks = 0;
    private boolean suspended = false;
    private boolean finished = false;

    public ChunkLoadEventListener(int x1, int z1, int x2, int z2, DoneCallback doneCallback) {
      var minecraft = Minecraft.getInstance();
      this.levelHashCode = minecraft.level.hashCode();
      LOGGER.info("listener chunk region in level {}: {} {} {} {}", levelHashCode, x1, z1, x2, z2);
      int chunkX1 = worldCoordToChunkCoord(x1);
      int chunkZ1 = worldCoordToChunkCoord(z1);
      int chunkX2 = worldCoordToChunkCoord(x2);
      int chunkZ2 = worldCoordToChunkCoord(z2);

      int chunkXMin = Math.min(chunkX1, chunkX2);
      int chunkXMax = Math.max(chunkX1, chunkX2);
      int chunkZMin = Math.min(chunkZ1, chunkZ2);
      int chunkZMax = Math.max(chunkZ1, chunkZ2);

      for (int chunkX = chunkXMin; chunkX <= chunkXMax; chunkX++) {
        for (int chunkZ = chunkZMin; chunkZ <= chunkZMax; chunkZ++) {
          LOGGER.info("listener chunk registered: {} {}", chunkX, chunkZ);
          long packedChunkXZ = packInts(chunkX, chunkZ);
          chunksToLoad.put(packedChunkXZ, false);
        }
      }
      this.doneCallback = doneCallback;
    }

    public synchronized void suspend() {
      suspended = true;
    }

    public synchronized void resume() {
      suspended = false;
    }

    public synchronized void updateChunkStatuses() {
      var minecraft = Minecraft.getInstance();
      var level = minecraft.level;
      if (level.hashCode() != this.levelHashCode) {
        LOGGER.info("chunk listener's world doesn't match current world; clearing listener");
        chunksToLoad.clear();
        numUnloadedChunks = 0;
        return;
      }
      numUnloadedChunks = 0;
      var chunkManager = level.getChunkSource();
      for (var entry : chunksToLoad.entrySet()) {
        long packedChunkXZ = entry.getKey();
        int[] chunkCoords = unpackLong(packedChunkXZ);
        boolean isLoaded = chunkManager.getChunkNow(chunkCoords[0], chunkCoords[1]) != null;
        entry.setValue(isLoaded);
        if (!isLoaded) {
          numUnloadedChunks++;
        }
      }
      LOGGER.info("Unloaded chunks after updateChunkStatuses: {}", numUnloadedChunks);
    }

    /** Returns true if the final outstanding chunk is loaded. */
    public synchronized boolean onChunkLoaded(LevelAccessor chunkLevel, int chunkX, int chunkZ) {
      if (suspended) {
        return false;
      }
      if (chunkLevel.hashCode() != levelHashCode) {
        return false;
      }
      long packedChunkXZ = packInts(chunkX, chunkZ);
      if (!chunksToLoad.containsKey(packedChunkXZ)) {
        return false;
      }
      boolean wasLoaded = chunksToLoad.put(packedChunkXZ, true);
      if (!wasLoaded) {
        LOGGER.info("listener chunk loaded for level {}: {} {}", levelHashCode, chunkX, chunkZ);
        numUnloadedChunks--;
        if (numUnloadedChunks == 0) {
          onFinished(true);
          return true;
        }
      }
      return false;
    }

    public synchronized void onChunkUnloaded(LevelAccessor chunkLevel, int chunkX, int chunkZ) {
      if (suspended) {
        return;
      }
      if (chunkLevel.hashCode() != levelHashCode) {
        return;
      }
      long packedChunkXZ = packInts(chunkX, chunkZ);
      if (!chunksToLoad.containsKey(packedChunkXZ)) {
        return;
      }
      boolean wasLoaded = chunksToLoad.put(packedChunkXZ, false);
      if (wasLoaded) {
        numUnloadedChunks++;
      }
    }

    public synchronized boolean isFullyLoaded() {
      return numUnloadedChunks == 0;
    }

    /** To be called when either all requested chunks are loaded or operation is cancelled. */
    public synchronized void onFinished(boolean success) {
      if (finished) {
        LOGGER.warn(
            "ChunkLoadEventListener already finished; finished again with {}",
            success ? "success" : "failure");
        return;
      }
      finished = true;
      doneCallback.done(success);
    }
  }

  private record JobFunctionCallId(int jobId, long funcCallId) {}

  private static Map<JobFunctionCallId, ChunkLoadEventListener> chunkLoadEventListeners =
      new ConcurrentHashMap<JobFunctionCallId, ChunkLoadEventListener>();

  private static String customNickname = null;
  private static Consumer<String> chatInterceptor = null;

  private static boolean areCommandsAllowed() {
    var minecraft = Minecraft.getInstance();
    var serverData = minecraft.getCurrentServer();
    return serverData == null
        || serverBlockList.areCommandsAllowedForServer(serverData.name, serverData.ip);
  }

  private static void processMinecraftCommand(String command) {
    var minecraft = Minecraft.getInstance();
    var connection = minecraft.player.connection;
    if (!areCommandsAllowed()) {
      LOGGER.info("Minecraft command blocked for server: /{}", command);
      return;
    }
    connection.sendUnsignedCommand(command);
  }

  private static void processChatMessage(String message) {
    var minecraft = Minecraft.getInstance();
    var connection = minecraft.player.connection;
    connection.sendChat(message);
  }

  private static void processPlainText(String text) {
    var minecraft = Minecraft.getInstance();
    var chat = minecraft.gui.getChat();
    chat.addMessage(Component.nullToEmpty(text));
  }

  private static void processJsonFormattedText(String text) {
    var minecraft = Minecraft.getInstance();
    var chat = minecraft.gui.getChat();
    chat.addMessage(Component.Serializer.fromJson(text));
  }

  private static void processMessage(Message message) {
    var minecraft = Minecraft.getInstance();
    switch (message.type()) {
      case MINECRAFT_COMMAND:
        processMinecraftCommand(message.value());
        return;

      case MINESCRIPT_COMMAND:
        LOGGER.info("Processing command: {}", message.value());
        // TODO(maxuser): If there's a parent job that spawned this command, pass along the parent
        // job so that suspending or killing the parent job also suspends or kills the child job.
        // Also, child jobs are listed from `jobs` command and increment the job counter, but should
        // they?  This speaks to the conceptual distinction between "shell job" and "process" which
        // currently isn't established.
        runMinescriptCommand(message.value());
        return;

      case CHAT_MESSAGE:
        processChatMessage(message.value());
        return;

      case PLAIN_TEXT:
        processPlainText(message.value());
        return;

      case JSON_FORMATTED_TEXT:
        processJsonFormattedText(message.value());
        return;
    }
  }

  private static JsonElement itemStackToJsonElement(
      ItemStack itemStack, OptionalInt slot, boolean markSelected) {
    if (itemStack.getCount() == 0) {
      return JsonNull.INSTANCE;
    } else {
      var nbt = itemStack.getTag();
      var out = new JsonObject();
      out.addProperty("item", itemStack.getItem().toString());
      out.addProperty("count", itemStack.getCount());
      if (nbt != null) {
        out.addProperty("nbt", nbt.toString());
      }
      if (slot.isPresent()) {
        out.addProperty("slot", slot.getAsInt());
      }
      if (markSelected) {
        out.addProperty("selected", true);
      }
      return out;
    }
  }

  private static JsonObject entityToJsonObject(Entity entity, boolean includeNbt) {
    var minecraft = Minecraft.getInstance();
    var jsonEntity = new JsonObject();
    jsonEntity.addProperty("name", entity.getName().getString());
    jsonEntity.addProperty("type", entity.getType().toString());
    jsonEntity.addProperty("uuid", entity.getUUID().toString());
    if (entity instanceof LivingEntity livingEntity) {
      jsonEntity.addProperty("health", livingEntity.getHealth());
    }
    if (entity == minecraft.player) {
      jsonEntity.addProperty("local", "true");
    }
    var position = new JsonArray();
    position.add(entity.getX());
    position.add(entity.getY());
    position.add(entity.getZ());
    jsonEntity.add("position", position);

    jsonEntity.addProperty("yaw", entity.getYRot());
    jsonEntity.addProperty("pitch", entity.getXRot());

    var v = entity.getDeltaMovement();
    var velocity = new JsonArray();
    velocity.add(v.x);
    velocity.add(v.y);
    velocity.add(v.z);
    jsonEntity.add("velocity", velocity);

    if (includeNbt) {
      var nbt = new CompoundTag();
      jsonEntity.addProperty("nbt", entity.saveWithoutId(nbt).toString());
    }
    return jsonEntity;
  }

  private static JsonArray entitiesToJsonArray(
      Iterable<? extends Entity> entities, boolean includeNbt) {
    var jsonEntities = new JsonArray();
    for (var entity : entities) {
      jsonEntities.add(entityToJsonObject(entity, includeNbt));
    }
    return jsonEntities;
  }

  private static boolean scriptFunctionDebugOutptut = false;

  // String key: KeyMapping.getName()
  private static final Map<String, InputConstants.Key> keyBinds = new ConcurrentHashMap<>();

  public static void setKeyBind(String keyMappingName, InputConstants.Key key) {
    LOGGER.info("Set key binding: {} -> {}", keyMappingName, key);
    keyBinds.put(keyMappingName, key);
  }

  private static Optional<JsonElement> doPlayerAction(
      String functionName, KeyMapping keyMapping, ScriptFunctionArgList args, String argsString) {
    args.expectSize(1);
    boolean pressed = args.getBoolean(0);

    var key = keyBinds.get(keyMapping.getName());
    if (pressed) {
      KeyMapping.set(key, true);
      KeyMapping.click(key);
    } else {
      KeyMapping.set(key, false);
    }
    return OPTIONAL_JSON_TRUE;
  }

  public static String getWorldName() {
    var minecraft = Minecraft.getInstance();
    var serverData = minecraft.getCurrentServer();
    var serverName = serverData == null ? null : serverData.name;
    var server = minecraft.getSingleplayerServer();
    var saveProperties = server == null ? null : server.getWorldData();
    String saveName = saveProperties == null ? null : saveProperties.getLevelName();
    return serverName == null ? saveName : serverName;
  }

  public static Optional<String> getScreenName() {
    var minecraft = Minecraft.getInstance();
    var screen = minecraft.screen;
    if (screen == null) {
      return Optional.empty();
    }
    String name = screen.getTitle().getString();
    if (name.isEmpty()) {
      if (screen instanceof CreativeModeInventoryScreen) {
        name = "Creative Inventory";
      } else if (screen instanceof LevelLoadingScreen) {
        name = "L" + "evel Loading"; // Split literal to prevent symbol renaming.
      } else if (screen instanceof ReceivingLevelScreen) {
        name = "Progress";
      } else {
        // The class name is not descriptive in production builds where symbols
        // are obfuscated, but using the class name allows callers to
        // differentiate untitled screen types from each other.
        name = screen.getClass().getName();
      }
    }
    return Optional.of(name);
  }

  private static final Optional<JsonElement> OPTIONAL_JSON_NULL = Optional.of(JsonNull.INSTANCE);

  private static final Optional<JsonElement> OPTIONAL_JSON_TRUE =
      Optional.of(new JsonPrimitive(true));

  private static final Optional<JsonElement> OPTIONAL_JSON_FALSE =
      Optional.of(new JsonPrimitive(false));

  private static JsonElement jsonPrimitiveOrNull(Optional<String> s) {
    return s.map(str -> (JsonElement) new JsonPrimitive(str)).orElse(JsonNull.INSTANCE);
  }

  /** Returns a JSON response if a script function is called. */
  private static Optional<JsonElement> handleScriptFunction(
      Job job, long funcCallId, String functionName, ScriptFunctionArgList args, String argsString)
      throws Exception {
    var minecraft = Minecraft.getInstance();
    var world = minecraft.level;
    var player = minecraft.player;
    var options = minecraft.options;

    switch (functionName) {
      case "player_position":
        {
          args.expectSize(0);
          var result = new JsonArray();
          result.add(player.getX());
          result.add(player.getY());
          result.add(player.getZ());
          return Optional.of(result);
        }

      case "player_set_position":
        {
          args.expectSize(5);
          double x = args.getDouble(0);
          double y = args.getDouble(1);
          double z = args.getDouble(2);
          float yaw = player.getYRot();
          float pitch = player.getXRot();
          if (args.get(3) != null) {
            yaw = (float) args.getDouble(3);
          }
          if (args.get(4) != null) {
            pitch = (float) args.getDouble(4);
          }
          player.moveTo(x, y, z, yaw, pitch);
          return OPTIONAL_JSON_TRUE;
        }

      case "player_name":
        args.expectSize(0);
        return Optional.of(new JsonPrimitive(player.getName().getString()));

      case "getblock":
        {
          args.expectSize(3);
          Level level = player.getCommandSenderWorld();
          int arg0 = args.getConvertibleInt(0);
          int arg1 = args.getConvertibleInt(1);
          int arg2 = args.getConvertibleInt(2);
          Optional<String> block =
              blockStateToString(level.getBlockState(new BlockPos(arg0, arg1, arg2)));
          return Optional.of(jsonPrimitiveOrNull(block));
        }

      case "getblocklist":
        {
          Runnable badArgsResponse =
              () -> {
                throw new IllegalArgumentException(
                    String.format(
                        "`%s` expected a list of (x, y, z) positions but got: %s",
                        functionName, argsString));
              };

          args.expectSize(1);
          if (!(args.get(0) instanceof List)) {
            badArgsResponse.run();
          }
          List<?> positions = (List<?>) args.get(0);
          Level level = player.getCommandSenderWorld();
          var blocks = new JsonArray();
          var pos = new BlockPos.MutableBlockPos();
          for (var position : positions) {
            if (!(position instanceof List)) {
              badArgsResponse.run();
            }
            List<?> coords = (List<?>) position;
            if (coords.size() != 3
                || !(coords.get(0) instanceof Number)
                || !(coords.get(1) instanceof Number)
                || !(coords.get(2) instanceof Number)) {
              badArgsResponse.run();
            }
            int x = ((Number) coords.get(0)).intValue();
            int y = ((Number) coords.get(1)).intValue();
            int z = ((Number) coords.get(2)).intValue();
            Optional<String> block = blockStateToString(level.getBlockState(pos.set(x, y, z)));
            blocks.add(jsonPrimitiveOrNull(block));
          }
          return Optional.of(blocks);
        }

      case "register_key_event_listener":
        {
          args.expectSize(0);
          if (keyEventListeners.containsKey(job.jobId())) {
            throw new IllegalStateException(
                "Failed to create listener because a listener is already registered for job: "
                    + job.jobSummary());
          }
          keyEventListeners.put(job.jobId(), new ScriptFunctionCall(job, funcCallId));
          job.addAtExitHandler(() -> keyEventListeners.remove(job.jobId()));
          return Optional.empty();
        }

      case "unregister_key_event_listener":
        {
          args.expectSize(0);
          if (!keyEventListeners.containsKey(job.jobId())) {
            throw new IllegalStateException(
                String.format(
                    "`%s` has no listeners to unregister for job: %s",
                    functionName, job.jobSummary()));
          }
          keyEventListeners.remove(job.jobId());
          return OPTIONAL_JSON_TRUE;
        }

      case "register_mouse_event_listener":
        {
          args.expectSize(0);
          if (mouseEventListeners.containsKey(job.jobId())) {
            throw new IllegalStateException(
                "Failed to create listener because a listener is already registered for job: "
                    + job.jobSummary());
          }
          mouseEventListeners.put(job.jobId(), new ScriptFunctionCall(job, funcCallId));
          job.addAtExitHandler(() -> mouseEventListeners.remove(job.jobId()));
          return Optional.empty();
        }

      case "unregister_mouse_event_listener":
        {
          args.expectSize(0);
          if (!mouseEventListeners.containsKey(job.jobId())) {
            throw new IllegalStateException(
                String.format(
                    "`%s` has no listeners to unregister for job: %s",
                    functionName, job.jobSummary()));
          }
          mouseEventListeners.remove(job.jobId());
          return OPTIONAL_JSON_TRUE;
        }

      case "register_chat_message_listener":
        {
          args.expectSize(0);
          if (clientChatReceivedEventListeners.containsKey(job.jobId())) {
            throw new IllegalStateException(
                "Failed to create listener because a listener is already registered for job: "
                    + job.jobSummary());
          }
          clientChatReceivedEventListeners.put(
              job.jobId(), new ScriptFunctionCall(job, funcCallId));
          job.addAtExitHandler(() -> clientChatReceivedEventListeners.remove(job.jobId()));
          return Optional.empty();
        }

      case "unregister_chat_message_listener":
        {
          args.expectSize(0);
          if (!clientChatReceivedEventListeners.containsKey(job.jobId())) {
            throw new IllegalStateException(
                String.format(
                    "`%s` has no listeners to unregister for job: %s",
                    functionName, job.jobSummary()));
          }
          clientChatReceivedEventListeners.remove(job.jobId());
          return OPTIONAL_JSON_TRUE;
        }

      case "register_chat_message_interceptor":
        {
          args.expectSize(0);
          if (chatInterceptor != null) {
            throw new IllegalStateException("Chat interceptor already enabled for another job.");
          }
          chatInterceptor =
              s -> {
                job.respond(funcCallId, new JsonPrimitive(s), false);
              };
          job.addAtExitHandler(() -> chatInterceptor = null);
          logUserInfo("Chat interceptor enabled for job: {}", job.jobSummary());
          return Optional.empty();
        }

      case "unregister_chat_message_interceptor":
        {
          args.expectSize(0);
          if (chatInterceptor == null) {
            throw new IllegalStateException(
                "Chat interceptor already disabled: " + job.jobSummary());
          }
          chatInterceptor = null;
          logUserInfo("Chat interceptor disabled for job: {}", job.jobSummary());
          return OPTIONAL_JSON_TRUE;
        }

      case "await_loaded_region":
        {
          args.expectSize(4);
          int arg0 = args.getStrictInt(0);
          int arg1 = args.getStrictInt(1);
          int arg2 = args.getStrictInt(2);
          int arg3 = args.getStrictInt(3);
          var listener =
              new ChunkLoadEventListener(
                  arg0,
                  arg1,
                  arg2,
                  arg3,
                  (boolean success) -> job.respond(funcCallId, new JsonPrimitive(success), true));
          listener.updateChunkStatuses();
          if (listener.isFullyLoaded()) {
            listener.onFinished(true);
          } else {
            chunkLoadEventListeners.put(new JobFunctionCallId(job.jobId, funcCallId), listener);

            // TODO(maxuser): Generalize cancellation of long-running functions at job exit. When
            // such functions complete naturally, they should be cleared from at-exit handlers.
            job.addAtExitHandler(
                () -> {
                  var chunkListener =
                      chunkLoadEventListeners.remove(
                          new JobFunctionCallId(job.jobId(), funcCallId));
                  if (chunkListener != null) {
                    chunkListener.onFinished(false);
                    LOGGER.info(
                        "Cancelled function call {} for \"{}\" at job exit: {}",
                        funcCallId,
                        functionName,
                        job.jobSummary());
                  }
                });
          }
          return Optional.empty();
        }

      case "set_nickname":
        {
          args.expectSize(1);
          if (args.get(0) == null) {
            logUserInfo(
                "Chat nickname reset to default; was {}",
                customNickname == null ? "default already" : quoteString(customNickname));
            customNickname = null;
          } else {
            String arg = args.get(0).toString();
            if (!arg.contains("%s")) {
              throw new IllegalArgumentException(
                  "Expected nickname to contain %s as a placeholder for message text but got: "
                      + arg);
            }
            logUserInfo("Chat nickname set to {}.", quoteString(arg));
            customNickname = arg;
          }
          return OPTIONAL_JSON_TRUE;
        }

      case "player_hand_items":
        {
          args.expectSize(0);
          var result = new JsonArray();
          for (var itemStack : player.getHandSlots()) {
            result.add(itemStackToJsonElement(itemStack, OptionalInt.empty(), false));
          }
          return Optional.of(result);
        }

      case "player_inventory":
        {
          args.expectSize(0);
          var inventory = player.getInventory();
          var result = new JsonArray();
          int selectedSlot = inventory.selected;
          for (int i = 0; i < inventory.getContainerSize(); i++) {
            var itemStack = inventory.getItem(i);
            if (itemStack.getCount() > 0) {
              result.add(itemStackToJsonElement(itemStack, OptionalInt.of(i), i == selectedSlot));
            }
          }
          return Optional.of(result);
        }

      case "player_inventory_slot_to_hotbar":
        {
          args.expectSize(1);
          int slot = args.getStrictInt(0);
          var inventory = player.getInventory();
          var connection = minecraft.getConnection();
          connection.send(new ServerboundPickItemPacket(slot));
          return Optional.of(new JsonPrimitive(inventory.selected));
        }

      case "player_inventory_select_slot":
        {
          args.expectSize(1);
          int slot = args.getStrictInt(0);
          var inventory = player.getInventory();
          var previouslySelectedSlot = inventory.selected;
          inventory.selected = slot;
          return Optional.of(new JsonPrimitive(previouslySelectedSlot));
        }

      case "player_press_forward":
        return doPlayerAction(functionName, options.keyUp, args, argsString);

      case "player_press_backward":
        return doPlayerAction(functionName, options.keyDown, args, argsString);

      case "player_press_left":
        return doPlayerAction(functionName, options.keyLeft, args, argsString);

      case "player_press_right":
        return doPlayerAction(functionName, options.keyRight, args, argsString);

      case "player_press_jump":
        return doPlayerAction(functionName, options.keyJump, args, argsString);

      case "player_press_sprint":
        return doPlayerAction(functionName, options.keySprint, args, argsString);

      case "player_press_sneak":
        return doPlayerAction(functionName, options.keyShift, args, argsString);

      case "player_press_pick_item":
        return doPlayerAction(functionName, options.keyPickItem, args, argsString);

      case "player_press_use":
        return doPlayerAction(functionName, options.keyUse, args, argsString);

      case "player_press_attack":
        return doPlayerAction(functionName, options.keyAttack, args, argsString);

      case "player_press_swap_hands":
        return doPlayerAction(functionName, options.keySwapOffhand, args, argsString);

      case "player_press_drop":
        return doPlayerAction(functionName, options.keyDrop, args, argsString);

      case "player_orientation":
        {
          args.expectSize(0);
          var result = new JsonArray();
          result.add(player.getYRot());
          result.add(player.getXRot());
          return Optional.of(result);
        }

      case "player_set_orientation":
        {
          args.expectSize(2);
          Double yaw = args.getDouble(0);
          Double pitch = args.getDouble(1);
          player.setYRot(yaw.floatValue() % 360.0f);
          player.setXRot(pitch.floatValue() % 360.0f);
          return OPTIONAL_JSON_TRUE;
        }

      case "player_get_targeted_block":
        {
          // See minecraft.client.gui.hud.DebugHud for implementation of F3 debug screen.
          args.expectSize(1);
          double maxDistance = args.getDouble(0);
          var entity = minecraft.getCameraEntity();
          var blockHit = entity.pick(maxDistance, 0.0f, false);
          if (blockHit.getType() == HitResult.Type.BLOCK) {
            var hitResult = (BlockHitResult) blockHit;
            var blockPos = hitResult.getBlockPos();
            double playerDistance =
                Math3d.computeDistance(
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    blockPos.getX(),
                    blockPos.getY(),
                    blockPos.getZ());
            Level level = player.getCommandSenderWorld();
            Optional<String> block = blockStateToString(level.getBlockState(blockPos));
            var result = new JsonArray();
            var pos = new JsonArray();
            pos.add(blockPos.getX());
            pos.add(blockPos.getY());
            pos.add(blockPos.getZ());
            result.add(pos);
            result.add(playerDistance);
            result.add(hitResult.getDirection().toString());
            result.add(jsonPrimitiveOrNull(block));
            return Optional.of(result);
          } else {
            return OPTIONAL_JSON_NULL;
          }
        }

      case "player_health":
        return Optional.of(new JsonPrimitive(player.getHealth()));

      case "player":
        {
          args.expectArgs("nbt");
          boolean nbt = args.getBoolean(0);
          return Optional.of(entityToJsonObject(player, nbt));
        }

      case "players":
        {
          args.expectArgs(
              "nbt",
              "uuid",
              "name",
              "position",
              "offset",
              "min_distance",
              "max_distance",
              "sort",
              "limit");
          boolean nbt = args.getBoolean(0);
          Optional<String> uuid = args.getOptionalString(1);
          Optional<String> name = args.getOptionalString(2);
          Optional<String> type = Optional.empty();
          Optional<List<Double>> position = args.getOptionalDoubleListWithSize(3, 3);
          Optional<List<Double>> offset = args.getOptionalDoubleListWithSize(4, 3);
          OptionalDouble minDistance = args.getOptionalDouble(5);
          OptionalDouble maxDistance = args.getOptionalDouble(6);
          Optional<EntitySelection.SortType> sort =
              args.getOptionalString(7)
                  .map(String::toUpperCase)
                  .map(EntitySelection.SortType::valueOf);
          OptionalInt limit = args.getOptionalStrictInt(8);
          return Optional.of(
              entitiesToJsonArray(
                  new EntitySelection(
                          uuid, name, type, position, offset, minDistance, maxDistance, sort, limit)
                      .selectFrom(world.players()),
                  nbt));
        }

      case "entities":
        {
          args.expectArgs(
              "nbt",
              "uuid",
              "name",
              "type",
              "position",
              "offset",
              "min_distance",
              "max_distance",
              "sort",
              "limit");
          boolean nbt = args.getBoolean(0);
          Optional<String> uuid = args.getOptionalString(1);
          Optional<String> name = args.getOptionalString(2);
          Optional<String> type = args.getOptionalString(3);
          Optional<List<Double>> position = args.getOptionalDoubleListWithSize(4, 3);
          Optional<List<Double>> offset = args.getOptionalDoubleListWithSize(5, 3);
          OptionalDouble minDistance = args.getOptionalDouble(6);
          OptionalDouble maxDistance = args.getOptionalDouble(7);
          Optional<EntitySelection.SortType> sort =
              args.getOptionalString(8)
                  .map(String::toUpperCase)
                  .map(EntitySelection.SortType::valueOf);
          OptionalInt limit = args.getOptionalStrictInt(9);
          return Optional.of(
              entitiesToJsonArray(
                  new EntitySelection(
                          uuid, name, type, position, offset, minDistance, maxDistance, sort, limit)
                      .selectFrom(world.entitiesForRendering()),
                  nbt));
        }

      case "world_properties":
        {
          args.expectSize(0);
          var levelProperties = world.getLevelData();
          var difficulty = levelProperties.getDifficulty();
          var serverData = minecraft.getCurrentServer();
          var serverAddress = serverData == null ? "localhost" : serverData.ip;

          var spawn = new JsonArray();
          spawn.add(levelProperties.getXSpawn());
          spawn.add(levelProperties.getYSpawn());
          spawn.add(levelProperties.getZSpawn());

          var result = new JsonObject();
          result.addProperty("game_ticks", levelProperties.getGameTime());
          result.addProperty("day_ticks", levelProperties.getDayTime());
          result.addProperty("raining", levelProperties.isRaining());
          result.addProperty("thundering", levelProperties.isThundering());
          result.add("spawn", spawn);
          result.addProperty("hardcore", levelProperties.isHardcore());
          result.addProperty("difficulty", difficulty.getSerializedName());
          result.addProperty("name", getWorldName());
          result.addProperty("address", serverAddress);
          return Optional.of(result);
        }

      case "execute":
        {
          args.expectArgs("command");
          String command = args.getString(0);
          if (command.startsWith("\\")) {
            runMinescriptCommand(command.substring(1));
          } else {
            processMinecraftCommand(command.startsWith("/") ? command.substring(1) : command);
          }
          return Optional.empty();
        }

      case "echo_json_text":
        {
          args.expectArgs("message");
          var message = args.getString(0);
          processJsonFormattedText(message);
          return OPTIONAL_JSON_TRUE;
        }

      case "echo_plain_text":
        {
          args.expectArgs("message");
          var message = args.getString(0);
          processPlainText(message);
          return OPTIONAL_JSON_TRUE;
        }

      case "chat":
        {
          args.expectArgs("message");
          String message = args.getString(0);
          processChatMessage(message);
          return Optional.empty();
        }

      case "log":
        {
          args.expectArgs("message");
          String message = args.getString(0);
          LOGGER.info(message);
          return Optional.empty();
        }

      case "screenshot":
        {
          args.expectSize(1);
          String filename = args.get(0) == null ? null : args.getString(0);
          Screenshot.grab(
              minecraft.gameDirectory,
              filename,
              minecraft.getMainRenderTarget(),
              message -> job.log(message.getString()));
          return OPTIONAL_JSON_TRUE;
        }

      case "blockpack_read_world":
        {
          // Python function signature:
          //    (pos1: BlockPos, pos2: BlockPos,
          //     rotation: Rotation = None, offset: BlockPos = None,
          //     comments: Dict[str, str] = {}, safety_limit: bool = True) -> int
          args.expectSize(6);

          var pos1 = args.getIntListWithSize(0, 3);
          var pos2 = args.getIntListWithSize(1, 3);

          int[] rotation = null;
          if (args.get(2) != null) {
            rotation = args.getIntListWithSize(2, 9).stream().mapToInt(Integer::intValue).toArray();
          }

          int[] offset = null;
          if (args.get(3) != null) {
            offset = args.getIntListWithSize(3, 3).stream().mapToInt(Integer::intValue).toArray();
          }

          var comments = args.getConvertibleStringMap(4);
          boolean safetyLimit = args.getBoolean(5);

          var blockpacker = new BlockPacker();
          readBlocks(
              pos1.get(0),
              pos1.get(1),
              pos1.get(2),
              pos2.get(0),
              pos2.get(1),
              pos2.get(2),
              safetyLimit,
              new BlockPack.TransformedBlockConsumer(rotation, offset, blockpacker));
          blockpacker.comments().putAll(comments);
          var blockpack = blockpacker.pack();
          int key = job.blockpacks.retain(blockpack);
          return Optional.of(new JsonPrimitive(key));
        }

      case "blockpack_read_file":
        {
          // Python function signature:
          //    (filename: str) -> int
          args.expectSize(1);
          String blockpackFilename = args.getString(0);
          var blockpack = BlockPack.readZipFile(blockpackFilename);
          int key = job.blockpacks.retain(blockpack);
          return Optional.of(new JsonPrimitive(key));
        }

      case "blockpack_import_data":
        {
          // Python function signature:
          //    (base64_data: str) -> int
          args.expectSize(1);
          String base64Data = args.getString(0);
          var blockpack = BlockPack.fromBase64EncodedString(base64Data);
          int key = job.blockpacks.retain(blockpack);
          return Optional.of(new JsonPrimitive(key));
        }

      case "blockpack_block_bounds":
        {
          // Python function signature:
          //    (blockpack_id) -> Tuple[Tuple[int, int, int], Tuple[int, int, int]]
          args.expectSize(1);
          int blockpackId = args.getStrictInt(0);
          var blockpack = job.blockpacks.getById(blockpackId);
          if (blockpack == null) {
            throw new IllegalStateException(
                String.format("`%s` failed to find BlockPack[%d]", functionName, blockpackId));
          }

          int[] bounds = blockpack.blockBounds();

          var minBound = new JsonArray();
          minBound.add(bounds[0]);
          minBound.add(bounds[1]);
          minBound.add(bounds[2]);

          var maxBound = new JsonArray();
          maxBound.add(bounds[3]);
          maxBound.add(bounds[4]);
          maxBound.add(bounds[5]);

          var result = new JsonArray();
          result.add(minBound);
          result.add(maxBound);
          return Optional.of(result);
        }

      case "blockpack_comments":
        {
          // Python function signature:
          //    (blockpack_id) -> Dict[str, str]
          args.expectSize(1);
          int blockpackId = args.getStrictInt(0);
          var blockpack = job.blockpacks.getById(blockpackId);
          if (blockpack == null) {
            throw new IllegalStateException(
                String.format("`%s` Failed to find BlockPack[%d]", functionName, blockpackId));
          }
          return Optional.of(GSON.toJsonTree(blockpack.comments()));
        }

      case "blockpack_write_world":
        {
          // Python function signature:
          //    (blockpack_id: int, rotation: Rotation = None, offset: BlockPos = None) -> bool
          args.expectSize(3);

          int blockpackId = args.getStrictInt(0);

          int[] rotation = null;
          if (args.get(1) != null) {
            rotation = args.getIntListWithSize(1, 9).stream().mapToInt(Integer::intValue).toArray();
          }

          int[] offset = null;
          if (args.get(2) != null) {
            offset = args.getIntListWithSize(2, 3).stream().mapToInt(Integer::intValue).toArray();
          }

          var blockpack = job.blockpacks.getById(blockpackId);
          if (blockpack == null) {
            throw new IllegalStateException(
                String.format(
                    "`%s` failed to find BlockPack[%d] to write to world",
                    functionName, blockpackId));
          }

          blockpack.getBlockCommands(
              rotation, offset, s -> job.messageQueue().add(Message.createMinecraftCommand(s)));
          return OPTIONAL_JSON_TRUE;
        }

      case "blockpack_write_file":
        {
          // Python function signature:
          //    (blockpack_id: int, filename: str) -> bool
          args.expectSize(2);
          int blockpackId = args.getStrictInt(0);
          String blockpackFilename = args.getString(1);

          var blockpack = job.blockpacks.getById(blockpackId);
          if (blockpack == null) {
            throw new IllegalStateException(
                String.format(
                    "`%s` failed to find BlockPack[%d] to write to file `%s`",
                    functionName, blockpackId, blockpackFilename));
          }

          blockpack.writeZipFile(blockpackFilename);
          return OPTIONAL_JSON_TRUE;
        }

      case "blockpack_export_data":
        {
          // Python function signature:
          //    (blockpack_id: int) -> str
          args.expectSize(1);
          int blockpackId = args.getStrictInt(0);
          var blockpack = job.blockpacks.getById(blockpackId);
          if (blockpack == null) {
            throw new IllegalStateException(
                String.format(
                    "`%s` failed to find BlockPack[%d] from which to export data",
                    functionName, blockpackId));
          }
          return Optional.of(new JsonPrimitive(blockpack.toBase64EncodedString()));
        }

      case "blockpack_delete":
        {
          // Python function signature:
          //    (blockpack_id: int) -> bool
          args.expectSize(1);
          int blockpackId = args.getStrictInt(0);
          var blockpack = job.blockpacks.releaseById(blockpackId);
          if (blockpack == null) {
            throw new IllegalStateException(
                String.format(
                    "`%s` failed to find BlockPack[%d] to delete", functionName, blockpackId));
          }
          return OPTIONAL_JSON_TRUE;
        }

      case "blockpacker_create":
        // Python function signature:
        //    () -> int
        return Optional.of(new JsonPrimitive(job.blockpackers.retain(new BlockPacker())));

      case "blockpacker_add_blocks":
        {
          // Python function signature:
          //    (blockpacker_id: int, base_pos: BlockPos,
          //     base64_setblocks: str, base64_fills: str, blocks: List[str]) -> bool
          args.expectSize(5);
          int blockpackerId = args.getStrictInt(0);
          List<Integer> basePos = args.getIntListWithSize(1, 3);
          String setblocksBase64 = args.getString(2);
          String fillsBase64 = args.getString(3);
          List<String> blocks = args.getConvertibleStringList(4);

          var blockpacker = job.blockpackers.getById(blockpackerId);
          if (blockpacker == null) {
            throw new IllegalStateException(
                String.format("`%s` failed to find BlockPacker[%d]", functionName, blockpackerId));
          }

          blockpacker.addBlocks(
              basePos.get(0), basePos.get(1), basePos.get(2), setblocksBase64, fillsBase64, blocks);
          return OPTIONAL_JSON_TRUE;
        }

      case "blockpacker_add_blockpack":
        {
          // Python function signature:
          //    (blockpacker_id: int, blockpack_id: int,
          //     rotation: Rotation = None, offset: BlockPos = None) -> bool
          args.expectSize(4);

          int blockpackerId = args.getStrictInt(0);
          int blockpackId = args.getStrictInt(1);

          int[] rotation = null;
          if (args.get(2) != null) {
            rotation = args.getIntListWithSize(2, 9).stream().mapToInt(Integer::intValue).toArray();
          }

          int[] offset = null;
          if (args.get(3) != null) {
            offset = args.getIntListWithSize(3, 3).stream().mapToInt(Integer::intValue).toArray();
          }

          var blockpacker = job.blockpackers.getById(blockpackerId);
          if (blockpacker == null) {
            throw new IllegalStateException(
                String.format("`%s` failed to find BlockPacker[%d]", functionName, blockpackerId));
          }

          var blockpack = job.blockpacks.getById(blockpackId);
          if (blockpack == null) {
            throw new IllegalStateException(
                String.format("`%s` failed to find BlockPack[%d]", functionName, blockpackId));
          }

          blockpack.getBlocks(
              new BlockPack.TransformedBlockConsumer(rotation, offset, blockpacker));

          return OPTIONAL_JSON_TRUE;
        }

      case "blockpacker_pack":
        {
          // Python function signature:
          //    (blockpacker_id: int, comments: Dict[str, str]) -> int
          args.expectSize(2);
          int blockpackerId = args.getStrictInt(0);
          var comments = args.getConvertibleStringMap(1);

          var blockpacker = job.blockpackers.getById(blockpackerId);
          if (blockpacker == null) {
            throw new IllegalStateException(
                String.format("`%s` failed to find BlockPacker[%d]", functionName, blockpackerId));
          }

          blockpacker.comments().putAll(comments);
          return Optional.of(new JsonPrimitive(job.blockpacks.retain(blockpacker.pack())));
        }

      case "blockpacker_delete":
        {
          // Python function signature:
          //    (blockpacker_id: int) -> bool
          args.expectSize(1);
          int blockpackerId = args.getStrictInt(0);

          var blockpacker = job.blockpackers.releaseById(blockpackerId);
          if (blockpacker == null) {
            throw new IllegalStateException(
                String.format("`%s` failed to find BlockPacker[%d]", functionName, blockpackerId));
          }
          return OPTIONAL_JSON_TRUE;
        }

      case "screen_name":
        if (!args.isEmpty()) {
          throw new IllegalArgumentException("Expected no params but got: " + argsString);
        }
        return Optional.of(jsonPrimitiveOrNull(getScreenName()));

      case "container_get_items": // List of Items in Chest
        {
          if (!args.isEmpty()) {
            throw new IllegalArgumentException("Expected no params but got: " + argsString);
          }
          Screen screen = minecraft.screen;
          if (screen instanceof AbstractContainerScreen<?> handledScreen) {
            AbstractContainerMenu screenHandler = handledScreen.getMenu();
            Slot[] slots = screenHandler.slots.toArray(new Slot[0]);
            var result = new JsonArray();
            for (Slot slot : slots) {
              ItemStack itemStack = slot.getItem();
              if (itemStack.isEmpty()) {
                continue;
              }
              result.add(itemStackToJsonElement(itemStack, OptionalInt.of(slot.index), false));
            }
            return Optional.of(result);
          } else {
            return OPTIONAL_JSON_NULL;
          }
        }

      case "container_click_slot": // Click on slot in container
        {
          args.expectSize(1);
          Screen screen = minecraft.screen;
          int slotId = args.getStrictInt(0);
          if (screen == null || !(screen instanceof AbstractContainerScreen<?> handledScreen)) {
            return OPTIONAL_JSON_FALSE;
          }
          AbstractContainerMenu screenHandler = handledScreen.getMenu();
          if (slotId < 0 || slotId >= screenHandler.slots.size()) {
            throw new IndexOutOfBoundsException(
                String.format(
                    "Slot %d outside expected range: [0, %d]",
                    slotId, (screenHandler.slots.size() - 1)));
          }
          var slot = screenHandler.getSlot(slotId);
          FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
          buf.writeByte(screenHandler.containerId);
          buf.writeShort(slotId);
          buf.writeByte(0); // Button (0 for left click)
          buf.writeShort(0); // Action type (0 for click)
          buf.writeShort(0); // Mode (0 for normal)
          buf.writeItem(slot.getItem()); // Item stack
          var connection = minecraft.getConnection();
          connection.send(new ServerboundContainerClickPacket(buf));
          screenHandler.broadcastChanges();
          return OPTIONAL_JSON_TRUE;
        }

      case "player_look_at": // Look at x, y, z
        {
          args.expectSize(3);
          double x = args.getDouble(0);
          double y = args.getDouble(1);
          double z = args.getDouble(2);
          player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(x, y, z));
          return OPTIONAL_JSON_TRUE;
        }

      case "flush":
        args.expectSize(0);
        return OPTIONAL_JSON_TRUE;

      case "cancelfn!":
        var cancelfnRetval = Optional.of((JsonElement) new JsonPrimitive("cancelfn!"));
        if (funcCallId != 0) {
          LOGGER.error(
              "Internal error while cancelling function: funcCallId = 0 but got {} in job: {}",
              funcCallId,
              job.jobSummary());
          return cancelfnRetval;
        }
        if (args.size() != 2
            || !(args.get(0) instanceof Number)
            || !(args.get(1) instanceof String)) {
          LOGGER.error(
              "Internal error while cancelling function: expected [int, str] but got {} in job: {}",
              argsString,
              job.jobSummary());
          return cancelfnRetval;
        }
        long funcIdToCancel = ((Number) args.get(0)).longValue();
        String funcName = (String) args.get(1);
        // TODO(maxuser): Generalize script function call cancellation beyond chunk load listeners.
        var listener =
            chunkLoadEventListeners.remove(new JobFunctionCallId(job.jobId(), funcIdToCancel));
        if (listener == null) {
          LOGGER.warn(
              "Failed to find operation to cancel: funcCallId {} for \"{}\" in job: {}",
              funcIdToCancel,
              funcName,
              job.jobSummary());
        } else {
          listener.onFinished(false);
          LOGGER.info(
              "Cancelled function call {} for \"{}\" in job: {}",
              funcIdToCancel,
              funcName,
              job.jobSummary());
        }
        return cancelfnRetval;

      case "exit!":
        if (funcCallId == 0) {
          return Optional.of(new JsonPrimitive("exit!"));
        } else {
          return OPTIONAL_JSON_NULL;
        }

      case "IndexOutOfBoundsException":
        {
          var array = new int[1];
          int i = array[1];
          return OPTIONAL_JSON_NULL;
        }

      case "IllegalArgumentException":
        throw new IllegalArgumentException("This is a test.");

      case "NoSuchElementException":
        {
          var list = new ArrayList<String>();
          list.iterator().next();
          return OPTIONAL_JSON_NULL;
        }

      case "IllegalStateException":
        throw new IllegalStateException("This is a test.");

      case "NullPointerException":
        {
          String string = null;
          string.length();
          return OPTIONAL_JSON_NULL;
        }

      default:
        throw new IllegalArgumentException(
            String.format(
                "Unknown function `%s` called from job: %s", functionName, job.jobSummary()));
    }
  }

  public static void handleAutorun(String worldName) {
    LOGGER.info("Handling autorun for world `{}`", worldName);
    var commands = new ArrayList<Message>();

    var wildcardCommands = autorunCommands.get("*");
    if (wildcardCommands != null) {
      LOGGER.info(
          "Matched {} command(s) with autorun[*] for world `{}`",
          wildcardCommands.size(),
          worldName);
      commands.addAll(wildcardCommands);
    }

    var worldCommands = autorunCommands.get(worldName);
    if (worldCommands != null) {
      LOGGER.info("Matched {} command(s) with autorun[{}]", wildcardCommands.size(), worldName);
      commands.addAll(worldCommands);
    }

    for (var command : commands) {
      LOGGER.info("Running autorun command for world `{}`: {}", worldName, command);
      processMessage(command);
    }
  }

  public static void onClientWorldTick() {
    if (++clientTickEventCounter % minescriptTicksPerCycle == 0) {
      var minecraft = Minecraft.getInstance();
      var player = minecraft.player;

      String worldName = getWorldName();
      if (!autorunHandled.getAndSet(true) && worldName != null) {
        systemMessageQueue.clear();
        loadConfig();
        handleAutorun(worldName);
      }

      if (player != null && (!systemMessageQueue.isEmpty() || !jobs.getMap().isEmpty())) {
        Level level = player.getCommandSenderWorld();
        boolean hasMessage;
        int iterations = 0;
        do {
          hasMessage = false;
          ++iterations;
          Message sysMessage = systemMessageQueue.poll();
          if (sysMessage != null) {
            hasMessage = true;
            processMessage(sysMessage);
          }
          for (var job : jobs.getMap().values()) {
            if (job.state() == JobState.RUNNING) {
              try {
                Message message = job.messageQueue().poll();
                if (message != null) {
                  hasMessage = true;
                  if (message.type() == Message.Type.FUNCTION_CALL) {
                    // Function call messages have values formatted as:
                    // "{funcCallId} {functionName} {argsString}"
                    //
                    // argsString may have spaces, e.g. "123 my_func [4, 5, 6]"
                    String[] functionCall = message.value().split("\\s+", 3);
                    long funcCallId = Long.valueOf(functionCall[0]);
                    String functionName = functionCall[1];
                    String argsString = functionCall[2];
                    List<?> rawArgs = GSON.fromJson(argsString, ArrayList.class);
                    var args = new ScriptFunctionArgList(functionName, rawArgs, argsString);

                    try {
                      Optional<JsonElement> response =
                          handleScriptFunction(job, funcCallId, functionName, args, argsString);
                      if (response.isPresent()) {
                        job.respond(funcCallId, response.get(), true);
                      }
                      if (scriptFunctionDebugOutptut) {
                        LOGGER.info(
                            "(debug) Script function `{}`: {} / {}  ->  {}",
                            functionName,
                            quoteString(argsString),
                            rawArgs,
                            response.map(JsonElement::toString).orElse("<no response>"));
                      }
                    } catch (Exception e) {
                      job.raiseException(funcCallId, ExceptionInfo.fromException(e));
                    }
                  } else {
                    jobs.getUndoForJob(job).ifPresent(u -> u.processCommandToUndo(level, message));
                    processMessage(message);
                  }
                }
              } catch (RuntimeException e) {
                job.logJobException(e);
              }
            }
          }
        } while (hasMessage && iterations < minescriptCommandsPerCycle);
      }
    }
  }
}
