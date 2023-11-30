// SPDX-FileCopyrightText: © 2022-2023 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import static net.minescript.common.CommandSyntax.Token;
import static net.minescript.common.CommandSyntax.parseCommand;
import static net.minescript.common.CommandSyntax.quoteCommand;
import static net.minescript.common.CommandSyntax.quoteString;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
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
import java.lang.reflect.Field;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.LevelLoadingScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.c2s.play.PickFromInventoryC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.chunk.Chunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.lwjgl.glfw.GLFW;

public class Minescript {
  private static final Logger LOGGER = LogManager.getLogger();

  // MINESCRIPT_DIR is relative to the minecraft directory which is the working directory.
  private static final String MINESCRIPT_DIR = "minescript";

  private enum FileOverwritePolicy {
    DO_NOT_OVERWRITE,
    OVERWRITTE
  }

  private static Thread worldListenerThread;

  public static void init() {
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
      copyJarResourceToMinescriptDir("version.txt", FileOverwritePolicy.OVERWRITTE);
      copyJarResourceToMinescriptDir("minescript.py", FileOverwritePolicy.OVERWRITTE);
      copyJarResourceToMinescriptDir("minescript_runtime.py", FileOverwritePolicy.OVERWRITTE);
      copyJarResourceToMinescriptDir("help.py", FileOverwritePolicy.OVERWRITTE);
      copyJarResourceToMinescriptDir("copy.py", FileOverwritePolicy.OVERWRITTE);
      copyJarResourceToMinescriptDir("paste.py", FileOverwritePolicy.OVERWRITTE);
      copyJarResourceToMinescriptDir("eval.py", FileOverwritePolicy.OVERWRITTE);
    }

    worldListenerThread =
        new Thread(Minescript::runWorldListenerThread, "minescript-world-listener");
    worldListenerThread.start();

    loadConfig();
  }

  private static AtomicBoolean autorunHandled = new AtomicBoolean(false);

  private static void runWorldListenerThread() {
    final int millisToSleep = 1000;
    var minecraft = MinecraftClient.getInstance();
    boolean noWorld = (minecraft.world == null);
    while (true) {
      boolean noWorldNow = (minecraft.world == null);
      if (noWorld != noWorldNow) {
        if (noWorldNow) {
          autorunHandled.set(false);
          LOGGER.info("Exited world");
          for (var job : jobs.getMap().values()) {
            job.kill();
          }
          systemCommandQueue.clear();
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

  private static Gson GSON = new Gson();

  private static String toJsonString(String s) {
    return GSON.toJson(s);
  }

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
  private static void copyJarResourceToMinescriptDir(
      String resourceName, FileOverwritePolicy overwritePolicy) {
    copyJarResourceToMinescriptDir(resourceName, resourceName, overwritePolicy);
  }

  /** Copies resource from jar to a file in the minescript dir. */
  private static void copyJarResourceToMinescriptDir(
      String resourceName, String fileName, FileOverwritePolicy overwritePolicy) {
    Path filePath = Paths.get(MINESCRIPT_DIR, fileName);
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
      LOGGER.info("Copied jar resource \"{}\" to minescript dir as \"{}\"", resourceName, fileName);
    } catch (IOException e) {
      LOGGER.error(
          "Failed to copy jar resource \"{}\" to minescript dir as \"{}\"", resourceName, fileName);
    }
  }

  private static String pythonLocation = null;

  private static final Pattern CONFIG_LINE_RE = Pattern.compile("([^=]+)=(.*)");
  private static final Pattern DOUBLE_QUOTED_STRING_RE = Pattern.compile("\"(.*)\"");
  private static final Pattern CONFIG_AUTORUN_RE = Pattern.compile("autorun\\[(.*)\\]");

  // Map from world name (or "*" for all) to a list of Minescript commands.
  private static Map<String, List<String>> autorunCommands = new ConcurrentHashMap<>();

  private static final File configFile =
      new File(Paths.get(MINESCRIPT_DIR, "config.txt").toString());
  private static long lastConfigLoadTime = 0;

  private static boolean useBlockPackForUndo = true;
  private static boolean useBlockPackForCopy = true;

  // Regex pattern for ignoring lines of output from stderr of Python scripts.
  private static Pattern stderrChatIgnorePattern = Pattern.compile("^$");

  /** Loads config from {@code minescript/config.txt} if the file has changed since last loaded. */
  private static void loadConfig() {
    if (System.getProperty("os.name").startsWith("Windows")) {
      copyJarResourceToMinescriptDir(
          "windows_config.txt", "config.txt", FileOverwritePolicy.DO_NOT_OVERWRITE);
    } else {
      copyJarResourceToMinescriptDir(
          "posix_config.txt", "config.txt", FileOverwritePolicy.DO_NOT_OVERWRITE);
    }
    if (configFile.lastModified() < lastConfigLoadTime) {
      return;
    }
    lastConfigLoadTime = System.currentTimeMillis();
    autorunCommands.clear();

    try (var reader = new BufferedReader(new FileReader(configFile.getPath()))) {
      String line;
      String continuedLine = null;
      while ((line = reader.readLine()) != null) {
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

        line = line.strip();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        var match = CONFIG_LINE_RE.matcher(line);
        if (match.matches()) {
          String name = match.group(1);
          String value = match.group(2);

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
            case "minescript_use_blockpack_for_copy":
              useBlockPackForCopy = Boolean.valueOf(value);
              LOGGER.info("Setting minescript_use_blockpack_for_copy to {}", useBlockPackForCopy);
              break;
            case "minescript_use_blockpack_for_undo":
              useBlockPackForUndo = Boolean.valueOf(value);
              LOGGER.info("Setting minescript_use_blockpack_for_undo to {}", useBlockPackForUndo);
              break;
            case "stderr_chat_ignore_pattern":
              stderrChatIgnorePattern = Pattern.compile(value);
              LOGGER.info("Setting stderr_chat_ignore_pattern to {}", value);
              break;
            default:
              {
                match = CONFIG_AUTORUN_RE.matcher(name);
                value = value.strip();
                // Interpret commands that lack a slash or backslash prefix as Minescript commands
                // by prepending a backslash.
                String command =
                    (value.startsWith("\\") || value.startsWith("/")) ? value : ("\\" + value);
                if (match.matches()) {
                  String worldName = match.group(1);
                  synchronized (autorunCommands) {
                    var commandList =
                        autorunCommands.computeIfAbsent(worldName, k -> new ArrayList<String>());
                    commandList.add(command);
                  }
                  LOGGER.info("Added autorun command `{}` for `{}`", command, worldName);
                } else {
                  LOGGER.warn(
                      "Unrecognized config var: {} = \"{}\" (\"{}\")",
                      name,
                      command,
                      pythonLocation);
                }
              }
          }
        } else {
          LOGGER.warn("config.txt: unable parse config line: {}", line);
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
          "minescript_commands_per_cycle",
          "minescript_ticks_per_cycle",
          "minescript_incremental_command_suggestions",
          "minescript_script_function_debug_outptut",
          "minescript_log_chunk_load_events",
          "minescript_use_blockpack_for_undo",
          "enable_minescript_on_chat_received_event");

  private static List<String> getScriptCommandNamesWithBuiltins() {
    var names = getScriptCommandNames();
    names.addAll(BUILTIN_COMMANDS);
    return names;
  }

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

  private static List<String> getScriptCommandNames() {
    List<String> scriptNames = new ArrayList<>();
    String minescriptDir = Paths.get(System.getProperty("user.dir"), MINESCRIPT_DIR).toString();
    try {
      Files.list(new File(minescriptDir).toPath())
          .filter(
              path -> {
                String filename = path.getFileName().toString();
                return (!filename.startsWith("minescript") || filename.endsWith("_test.py"))
                    && path.toString().endsWith(".py");
              })
          .forEach(
              path -> {
                String commandName =
                    path.toString()
                        .replace(minescriptDir + File.separator, "")
                        .replaceFirst("\\.py$", "");
                scriptNames.add(commandName);
              });
    } catch (IOException e) {
      logException(e);
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
      logUserError("Canont parse tilde-param: \"{}\"", param);
      return String.valueOf((int) playerPosition);
    }
  }

  private static String[] substituteMinecraftVars(String[] originalCommand) {
    String[] command = Arrays.copyOf(originalCommand, originalCommand.length);
    var player = MinecraftClient.getInstance().player;
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

  static String formatAsJsonText(String text, String color) {
    // Treat as plain text to write to the chat. The leading "|" signals to
    // processMessage to echo the text directly to the chat HUD without going
    // through the server.
    return "|{\"text\":\""
        + text.replace("\\", "\\\\").replace("\"", "\\\"")
        + "\",\"color\":\""
        + color
        + "\"}";
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

  interface JobControl {
    JobState state();

    void yield();

    Queue<String> commandQueue();

    boolean respond(long functionCallId, String returnValue, boolean finalReply);

    void raiseException(long functionCallId, String exceptionType, String message);

    void enqueueStdout(String text);

    void enqueueStderr(String messagePattern, Object... arguments);

    void logJobException(Exception e);
  }

  interface UndoableAction {
    int originalJobId();

    void onOriginalJobDone();

    String[] originalCommand();

    String[] derivativeCommand();

    void processCommandToUndo(World level, String command);

    void enqueueCommands(Queue<String> commandQueue);

    static UndoableAction create(int originalJobId, String[] originalCommand) {
      if (useBlockPackForUndo) {
        return new UndoableActionBlockPack(originalJobId, originalCommand);
      } else {
        return new UndoableActionSetblocks(originalJobId, originalCommand);
      }
    }
  }

  static class UndoableActionSetblocks implements UndoableAction {
    private static String UNDO_DIR = Paths.get(MINESCRIPT_DIR, "undo").toString();

    private volatile int originalJobId; // ID of the job that this undoes.
    private String[] originalCommand;
    private final long startTimeMillis;
    private final Deque<String> commands = new ArrayDeque<>();
    private final Set<Position> blocks = new HashSet<>();
    private String commandsFilename;
    private boolean undone = false;

    // coords and pos are reused to avoid lots of small object instantiations.
    private int[] coords = new int[6];
    private BlockPos.Mutable pos = new BlockPos.Mutable();

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

    public UndoableActionSetblocks(int originalJobId, String[] originalCommand) {
      this.originalJobId = originalJobId;
      this.originalCommand = originalCommand;
      this.startTimeMillis = System.currentTimeMillis();
    }

    public int originalJobId() {
      return originalJobId;
    }

    public synchronized void onOriginalJobDone() {
      originalJobId = -1;

      if (!commands.isEmpty()) {
        // Write undo commands to a file and clear in-memory commands queue.
        new File(UNDO_DIR).mkdirs();
        commandsFilename = Paths.get(UNDO_DIR, startTimeMillis + ".txt").toString();
        try (var writer = new PrintWriter(new FileWriter(commandsFilename))) {
          writer.printf("# Generated from Minescript command: %s\n", quoteCommand(originalCommand));
          for (String command : commands) {
            writer.println(command);
          }
        } catch (IOException e) {
          logException(e);
        }
        commands.clear();
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

    public synchronized void processCommandToUndo(World level, String command) {
      if (command.startsWith("/setblock ") && getSetblockCoords(command, coords)) {
        Optional<String> block =
            blockStateToString(level.getBlockState(pos.set(coords[0], coords[1], coords[2])));
        if (block.isPresent()) {
          if (!addBlockToUndoQueue(coords[0], coords[1], coords[2], block.get())) {
            return;
          }
        }
      } else if (command.startsWith("/fill ") && getFillCoords(command, coords)) {
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
        commands.addFirst(String.format("/setblock %d %d %d %s", x, y, z, block));
      }
      return true;
    }

    public synchronized void enqueueCommands(Queue<String> commandQueue) {
      undone = true;
      if (commandsFilename == null) {
        commandQueue.addAll(commands);
        commands.clear();
        blocks.clear();
      } else {
        try (var reader = new BufferedReader(new FileReader(commandsFilename))) {
          String line;
          while ((line = reader.readLine()) != null) {
            line = line.strip();
            if (line.startsWith("#")) {
              continue;
            }
            commandQueue.add(line);
          }
        } catch (IOException e) {
          logException(e);
        }
      }
    }
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
    private BlockPos.Mutable pos = new BlockPos.Mutable();

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

    public synchronized void processCommandToUndo(World level, String command) {
      if (command.startsWith("/setblock ") && getSetblockCoords(command, coords)) {
        Optional<String> block =
            blockStateToString(level.getBlockState(pos.set(coords[0], coords[1], coords[2])));
        if (block.isPresent()) {
          if (!addBlockToUndoQueue(coords[0], coords[1], coords[2], block.get())) {
            return;
          }
        }
      } else if (command.startsWith("/fill ") && getFillCoords(command, coords)) {
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

    public synchronized void enqueueCommands(Queue<String> commandQueue) {
      undone = true;
      int[] nullRotation = null;
      int[] nullOffset = null;
      if (blockpackFilename == null) {
        blockpacker.pack().getBlockCommands(nullRotation, nullOffset, commandQueue::add);
        blockpacker = null;
        blocks.clear();
      } else {
        try {
          BlockPack.readZipFile(blockpackFilename)
              .getBlockCommands(nullRotation, nullOffset, commandQueue::add);
        } catch (Exception e) {
          logException(e);
        }
      }
    }
  }

  interface Task {
    int run(String[] command, JobControl jobControl);

    default boolean sendResponse(long functionCallId, String returnValue, boolean finalReply) {
      return false;
    }

    default boolean sendException(long functionCallId, String exceptionType, String message) {
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
    private final String[] command;
    private final Task task;
    private Thread thread;
    private volatile JobState state = JobState.NOT_STARTED;
    private Consumer<Integer> doneCallback;
    private Queue<String> jobCommandQueue = new ConcurrentLinkedQueue<String>();
    private Lock lock = new ReentrantLock(true); // true indicates a fair lock to avoid starvation
    private List<Runnable> atExitHandlers = new ArrayList<>();
    private final ResourceTracker<BlockPack> blockpacks;
    private final ResourceTracker<BlockPacker> blockpackers;

    public Job(int jobId, String[] command, Task task, Consumer<Integer> doneCallback) {
      this.jobId = jobId;
      this.command = Arrays.copyOf(command, command.length);
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
    public Queue<String> commandQueue() {
      return jobCommandQueue;
    }

    @Override
    public boolean respond(long functionCallId, String returnValue, boolean finalReply) {
      boolean result = task.sendResponse(functionCallId, returnValue, finalReply);
      if (functionCallId == 0 && "\"exit!\"".equals(returnValue)) {
        state = JobState.DONE;
      }
      return result;
    }

    @Override
    public void raiseException(long functionCallId, String exceptionType, String message) {
      task.sendException(functionCallId, exceptionType, message);
    }

    @Override
    public void enqueueStdout(String text) {
      jobCommandQueue.add(text);
    }

    @Override
    public void enqueueStderr(String messagePattern, Object... arguments) {
      String logMessage = ParameterizedMessage.format(messagePattern, arguments);
      LOGGER.error("{}", logMessage);
      var match = stderrChatIgnorePattern.matcher(logMessage);
      if (!match.find()) {
        jobCommandQueue.add(formatAsJsonText(logMessage, "yellow"));
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
      thread = new Thread(this::runOnJobThread, String.format("job-%d-%s", jobId, command[0]));
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
            if (entry.getValue() == jobId) {
              var listener = entry.getKey();
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
          if (entry.getValue() == jobId) {
            var listener = entry.getKey();
            listener.resume();
            listener.updateChunkStatuses();
            if (listener.isFinished()) {
              listener.onFinished();
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
        while (state != JobState.KILLED && state != JobState.DONE && !jobCommandQueue.isEmpty()) {
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
      String displayCommand = quoteCommand(command);
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

    public boolean respond(String returnValue, boolean finalReply) {
      return job.respond(funcCallId, returnValue, finalReply);
    }

    public void raiseException(String exceptionType, String message) {
      job.raiseException(funcCallId, exceptionType, message);
    }
  }

  static class SubprocessTask implements Task {
    private Process process;
    private BufferedWriter stdinWriter;

    @Override
    public int run(String[] command, JobControl jobControl) {
      if (pythonLocation == null) {
        jobControl.enqueueStderr(
            "Python location not specified. Set `python` variable at: {}",
            configFile.getAbsolutePath());
        return -1;
      }

      String scriptName = Paths.get(MINESCRIPT_DIR, command[0] + ".py").toString();
      String[] executableCommand = new String[command.length + 2];
      executableCommand[0] = pythonLocation;
      executableCommand[1] = "-u"; // `python3 -u` for unbuffered stdout and stderr.
      executableCommand[2] = scriptName;
      for (int i = 1; i < command.length; i++) {
        executableCommand[i + 2] = command[i];
      }

      try {
        process = Runtime.getRuntime().exec(executableCommand);
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
            jobControl.enqueueStdout(line);
          }
          if (stderrReader.ready()) {
            if ((line = stderrReader.readLine()) == null) {
              break;
            }
            lastReadTime = System.currentTimeMillis();
            jobControl.enqueueStderr(line);
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
        jobControl.enqueueStderr(e.getMessage());
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
    public boolean sendResponse(long functionCallId, String returnValue, boolean finalReply) {
      if (!isReadyToRespond()) {
        return false;
      }
      try {
        if (finalReply) {
          stdinWriter.write(
              String.format(
                  "{\"fcid\": %d, \"retval\": %s, \"conn\": \"close\"}",
                  functionCallId, returnValue));
        } else {
          stdinWriter.write(
              String.format("{\"fcid\": %d, \"retval\": %s}", functionCallId, returnValue));
        }
        stdinWriter.newLine();
        stdinWriter.flush();
        return true;
      } catch (IOException e) {
        LOGGER.error("IOException in SubprocessTask sendResponse: {}", e.getMessage());
        return false;
      }
    }

    @Override
    public boolean sendException(long functionCallId, String exceptionType, String message) {
      if (!isReadyToRespond()) {
        return false;
      }
      try {
        stdinWriter.write(
            String.format(
                "{\"fcid\": %d, \"except\": {\"type\": %s, \"message\": %s}, \"conn\": \"close\"}",
                functionCallId, toJsonString(exceptionType), toJsonString(message)));
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

  /*
  static String jsonToString(JsonObjectBuilder builder) {
    var stringWriter = new StringWriter();
    try (var jsonWriter = Json.createWriter(stringWriter)) {
      jsonWriter.write(builder.build());
    }
    return stringWriter.toString();
  }
  */

  static class UndoTask implements Task {
    private final UndoableAction undo;

    public UndoTask(UndoableAction undo) {
      this.undo = undo;
    }

    @Override
    public int run(String[] command, JobControl jobControl) {
      undo.enqueueCommands(jobControl.commandQueue());
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

    public void createSubprocess(String[] command, List<Token> nextCommand) {
      var job =
          new Job(allocateJobId(), command, new SubprocessTask(), i -> finishJob(i, nextCommand));
      var undo = UndoableAction.create(job.jobId(), command);
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
              undo.derivativeCommand(),
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

  private static Queue<String> systemCommandQueue = new ConcurrentLinkedQueue<String>();

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
    systemCommandQueue.add(formatAsJsonText(logMessage, "yellow"));
  }

  public static void logUserError(String messagePattern, Object... arguments) {
    String logMessage = ParameterizedMessage.format(messagePattern, arguments);
    LOGGER.error("{}", logMessage);
    systemCommandQueue.add(formatAsJsonText(logMessage, "red"));
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
      Pattern.compile("/setblock ([^ ]+) ([^ ]+) ([^ ]+).*");

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
      Pattern.compile("/fill ([^ ]+) ([^ ]+) ([^ ]+) ([^ ]+) ([^ ]+) ([^ ]+).*");

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

  private static boolean readBlocks(
      int x0,
      int y0,
      int z0,
      int x1,
      int y1,
      int z1,
      boolean safetyLimit,
      BlockPack.BlockConsumer blockConsumer) {
    var minecraft = MinecraftClient.getInstance();
    var player = minecraft.player;
    if (player == null) {
      logUserError("Unable to read blocks because player is null.");
      return false;
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
        logUserError(
            "`blockpack_read_world` exceeded soft limit of 1600 chunks (region covers {} chunks; "
                + "override this safety check by passing `no_limit` to `copy` command or "
                + "`safety_limit=False` to `blockpack_read_world` function).",
            numChunks);
        return false;
      }
    }

    World level = player.getEntityWorld();

    var pos = new BlockPos.Mutable();
    for (int x = xMin; x <= xMax; x += 16) {
      for (int z = zMin; z <= zMax; z += 16) {
        Optional<String> block = blockStateToString(level.getBlockState(pos.set(x, 0, z)));
        if (block.isEmpty() || block.get().equals("minecraft:void_air")) {
          logUserError("Not all chunks are loaded within the requested `copy` volume.");
          return false;
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

    return true;
  }

  private static void copyBlocks(
      int x0, int y0, int z0, int x1, int y1, int z1, Optional<String> label, boolean safetyLimit) {
    var minecraft = MinecraftClient.getInstance();
    var player = minecraft.player;
    if (player == null) {
      logUserError("Unable to copy blocks because player is null.");
      return;
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
        logUserError(
            "`copy` command exceeded soft limit of 1600 chunks (region covers {} chunks; override"
                + " this safety check with `no_limit`).",
            numChunks);
        return;
      }
    }

    World level = player.getEntityWorld();

    var pos = new BlockPos.Mutable();
    for (int x = xMin; x <= xMax; x += 16) {
      for (int z = zMin; z <= zMax; z += 16) {
        Optional<String> block = blockStateToString(level.getBlockState(pos.set(x, 0, z)));
        if (block.isEmpty() || block.get().equals("minecraft:void_air")) {
          logUserError("Not all chunks are loaded within the requested `copy` volume.");
          return;
        }
      }
    }

    final String copiesDir = Paths.get(MINESCRIPT_DIR, "copies").toString();
    if (new File(copiesDir).mkdir()) {
      LOGGER.info("Created minescript copies dir");
    }

    try (var writer =
        new PrintWriter(
            new FileWriter(
                Paths.get(copiesDir, label.orElse("__default__") + ".txt").toString()))) {
      writer.print("# Generated from Minescript `copy` command:\n");
      writer.printf("# copy %d %d %d %d %d %d\n", x0, y0, z0, x1, y1, z1);

      int numBlocks = 0;

      for (int x = xMin; x <= xMax; ++x) {
        for (int y = yMin; y <= yMax; ++y) {
          for (int z = zMin; z <= zMax; ++z) {
            BlockState blockState = level.getBlockState(pos.set(x, y, z));
            if (!blockState.isAir()) {
              int xOffset = x - x0;
              int yOffset = y - y0;
              int zOffset = z - z0;
              Optional<String> block = blockStateToString(blockState);
              if (block.isPresent()) {
                writer.printf("/setblock %d %d %d %s\n", xOffset, yOffset, zOffset, block.get());
                numBlocks++;
              } else {
                logUserError("Unexpected BlockState format: {}", blockState.toString());
              }
            }
          }
        }
      }
      logUserInfo("Copied {} blocks.", numBlocks);
    } catch (IOException e) {
      logException(e);
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
        systemCommandQueue.add(
            "|{\"text\":\"Technoblade never dies.\",\"color\":\"dark_red\",\"bold\":true}");
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
      String[] command = tokenStrings.toArray(EMPTY_STRING_ARRAY);
      command = substituteMinecraftVars(command);

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

        case "copy":
          if (useBlockPackForCopy) {
            // In this case, `copy` is implemented in a script: copy.py
            break;
          } else {
            final var cmd = command;
            Runnable badArgsMessage =
                () ->
                    logUserError(
                        "Expected 6 params of type integer (plus optional params for label and"
                            + " `no_limit`), instead got `{}`",
                        getParamsAsString(cmd));

            if (checkParamTypes(
                    command,
                    ParamType.INT,
                    ParamType.INT,
                    ParamType.INT,
                    ParamType.INT,
                    ParamType.INT,
                    ParamType.INT,
                    ParamType.VAR_ARGS)
                && command.length <= 9) {
              int x0 = Integer.valueOf(command[1]);
              int y0 = Integer.valueOf(command[2]);
              int z0 = Integer.valueOf(command[3]);
              int x1 = Integer.valueOf(command[4]);
              int y1 = Integer.valueOf(command[5]);
              int z1 = Integer.valueOf(command[6]);

              boolean safetyLimit = true;
              Optional<String> label = Optional.empty();
              for (int i = 7; i < command.length; i++) {
                // Don't allow safetyLimit to be set to false multiple times.
                if (command[i].equals("no_limit") && safetyLimit) {
                  safetyLimit = false;
                } else if (label.isEmpty()) {
                  label = Optional.of(command[i]);
                } else {
                  badArgsMessage.run();
                  runParsedMinescriptCommand(nextCommand);
                  return;
                }
              }

              copyBlocks(x0, y0, z0, x1, y1, z1, label, safetyLimit);
            } else {
              badArgsMessage.run();
            }
            runParsedMinescriptCommand(nextCommand);
            return;
          }

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

        case "minescript_use_blockpack_for_copy":
          if (checkParamTypes(command, ParamType.BOOL)) {
            boolean value = Boolean.valueOf(command[1]);
            useBlockPackForCopy = value;
            logUserInfo("Minescript use of BlockPack for copy set to {}", value);
          } else {
            logUserError(
                "Expected 1 param of type boolean, instead got `{}`", getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "minescript_use_blockpack_for_undo":
          if (checkParamTypes(command, ParamType.BOOL)) {
            boolean value = Boolean.valueOf(command[1]);
            useBlockPackForUndo = value;
            logUserInfo("Minescript use of BlockPack for undo set to {}", value);
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

      if (!getScriptCommandNames().contains(command[0])) {
        logUserInfo("Minescript commands:");
        for (String builtin : BUILTIN_COMMANDS) {
          logUserInfo("  {} [builtin]", builtin);
        }
        for (String script : getScriptCommandNames()) {
          logUserInfo("  {}", script);
        }
        if (!command[0].equals("ls")) {
          logUserError("No Minescript command named \"{}\"", command[0]);
        }
        runParsedMinescriptCommand(nextCommand);
        return;
      }

      jobs.createSubprocess(command, nextCommand);

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

  private static boolean loggedFieldNameFallback = false;

  private static Object getField(
      Object object, Class<?> klass, String unobfuscatedName, String obfuscatedName)
      throws IllegalAccessException, NoSuchFieldException, SecurityException {
    Field field;
    try {
      field = klass.getDeclaredField(obfuscatedName);
    } catch (NoSuchFieldException e) {
      if (!loggedFieldNameFallback) {
        LOGGER.info(
            "Cannot find field with obfuscated name \"{}\", falling back to"
                + " unobfuscated name \"{}\"",
            obfuscatedName,
            unobfuscatedName);
        loggedFieldNameFallback = true;
      }
      try {
        field = klass.getDeclaredField(unobfuscatedName);
      } catch (NoSuchFieldException e2) {
        logUserError(
            "Internal Minescript error: cannot find field {}/{} in class {}. See log file for"
                + " details.",
            unobfuscatedName,
            obfuscatedName,
            klass.getSimpleName());
        LOGGER.info("Declared fields of {}:", klass.getName());
        for (Field f : klass.getDeclaredFields()) {
          LOGGER.info("  {}", f);
        }
        throw e2;
      }
    }
    field.setAccessible(true);
    return field.get(object);
  }

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
    var minecraft = MinecraftClient.getInstance();
    var iter = keyEventListeners.entrySet().iterator();
    String jsonText = null;
    while (iter.hasNext()) {
      var listener = iter.next();
      if (jsonText == null) {
        String screenName = getScreenName().orElse(null);
        long timeMillis = System.currentTimeMillis();
        jsonText =
            String.format(
                "{\"key\": %d, \"scanCode\": %d, \"action\": %d, \"modifiers\": %d, "
                    + "\"timeMillis\": %d, \"screen\": %s}",
                key, scanCode, action, modifiers, timeMillis, toJsonString(screenName));
      }
      LOGGER.info("Forwarding key event to listener {}: {}", listener.getKey(), jsonText);
      if (!listener.getValue().respond(jsonText, false)) {
        iter.remove();
      }
    }
  }

  private static MinescriptCommandHistory minescriptCommandHistory = new MinescriptCommandHistory();
  private static boolean incrementalCommandSuggestions = false;

  public static boolean onKeyboardKeyPressed(Screen screen, int key) {
    boolean cancel = false;
    if (screen != null && screen instanceof ChatScreen) {
      var scriptCommandNames = getScriptCommandNamesWithBuiltins();
      try {
        var chatEditBox =
            (TextFieldWidget) getField(screen, ChatScreen.class, "chatField", "field_2382");
        String value = chatEditBox.getText();
        if (!value.startsWith("\\")) {
          minescriptCommandHistory.moveToEnd();
          if (key == ENTER_KEY
              && (customNickname != null || chatInterceptor != null)
              && !value.startsWith("/")) {
            cancel = true;
            chatEditBox.setText("");
            onClientChat(value);
            screen.close();
          }
          return cancel;
        }
        if (key == UP_ARROW_KEY) {
          Optional<String> previousCommand = minescriptCommandHistory.moveBackwardAndGet(value);
          if (previousCommand.isPresent()) {
            value = previousCommand.get();
            chatEditBox.setText(value);
            chatEditBox.setSelectionStart(value.length());
          }
          cancel = true;
        } else if (key == DOWN_ARROW_KEY) {
          Optional<String> nextCommand = minescriptCommandHistory.moveForwardAndGet();
          if (nextCommand.isPresent()) {
            value = nextCommand.get();
            chatEditBox.setText(value);
            chatEditBox.setSelectionStart(value.length());
          }
          cancel = true;
        } else if (key == ENTER_KEY) {
          cancel = true;
          String text = chatEditBox.getText();
          chatEditBox.setText("");
          onClientChat(text);
          screen.close();
          return cancel;
        } else {
          minescriptCommandHistory.moveToEnd();
        }
        int cursorPos = chatEditBox.getCursor();
        if (key >= 32 && key < 127) {
          // TODO(maxuser): use chatEditBox.setSuggestion(String) to set suggestion?
          // TODO(maxuser): detect upper vs lower case properly
          String extraChar = Character.toString((char) key).toLowerCase();
          value = insertSubstring(value, cursorPos, extraChar);
        } else if (key == BACKSPACE_KEY) {
          value = eraseChar(value, cursorPos);
        }
        if (value.stripTrailing().length() > 1) {
          String command = value.substring(1).split("\\s+")[0];
          if (key == TAB_KEY && !commandSuggestions.isEmpty()) {
            if (cursorPos == command.length() + 1) {
              // Insert the remainder of the completed command.
              String maybeTrailingSpace =
                  ((cursorPos < value.length() && value.charAt(cursorPos) == ' ')
                          || commandSuggestions.size() > 1)
                      ? ""
                      : " ";
              chatEditBox.write(
                  longestCommonPrefix(commandSuggestions).substring(command.length())
                      + maybeTrailingSpace);
              if (commandSuggestions.size() > 1) {
                chatEditBox.setEditableColor(0x5ee8e8); // cyan for partial completion
              } else {
                chatEditBox.setEditableColor(0x5ee85e); // green for full completion
              }
              commandSuggestions = new ArrayList<>();
              return cancel;
            }
          }
          if (scriptCommandNames.contains(command)) {
            chatEditBox.setEditableColor(0x5ee85e); // green
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
                if (key == TAB_KEY || incrementalCommandSuggestions) {
                  systemCommandQueue.add(formatAsJsonText("completions:", "aqua"));
                  for (String suggestion : newCommandSuggestions) {
                    systemCommandQueue.add(formatAsJsonText("  " + suggestion, "aqua"));
                  }
                }
                commandSuggestions = newCommandSuggestions;
              }
              chatEditBox.setEditableColor(0x5ee8e8); // cyan
            } else {
              chatEditBox.setEditableColor(0xe85e5e); // red
              commandSuggestions = new ArrayList<>();
            }
          }
        }
      } catch (IllegalAccessException | NoSuchFieldException | SecurityException e) {
        logException(e);
        return cancel;
      }
    }
    return cancel;
  }

  private static boolean loggedMethodNameFallback = false;

  public static void onKeyInput(int key) {
    var minecraft = MinecraftClient.getInstance();
    var screen = minecraft.currentScreen;
    if (screen == null && key == BACKSLASH_KEY) {
      minecraft.setScreen(new ChatScreen(""));
    }
  }

  private static boolean enableMinescriptOnChatReceivedEvent = false;
  private static Pattern CHAT_WHISPER_MESSAGE_RE = Pattern.compile("You whisper to [^ :]+: (.*)");

  public static boolean onClientChatReceived(Text message) {
    boolean cancel = false;
    String text = message.getString();

    var iter = clientChatReceivedEventListeners.entrySet().iterator();
    while (iter.hasNext()) {
      var listener = iter.next();
      String quotedText = toJsonString(text);
      LOGGER.info("Forwarding chat message to listener {}: {}", listener.getKey(), quotedText);
      if (!listener.getValue().respond(quotedText, false)) {
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

  public static void onChunkLoad(WorldAccess chunkLevel, Chunk chunk) {
    int chunkX = chunk.getPos().x;
    int chunkZ = chunk.getPos().z;
    if (logChunkLoadEvents) {
      LOGGER.info("world {} chunk loaded: {} {}", chunkLevel.hashCode(), chunkX, chunkZ);
    }
    var iter = chunkLoadEventListeners.keySet().iterator();
    while (iter.hasNext()) {
      var listener = iter.next();
      if (listener.onChunkLoaded(chunkLevel, chunkX, chunkZ)) {
        iter.remove();
      }
    }
  }

  public static void onChunkUnload(WorldAccess chunkLevel, Chunk chunk) {
    int chunkX = chunk.getPos().x;
    int chunkZ = chunk.getPos().z;
    if (logChunkLoadEvents) {
      LOGGER.info("world {} chunk unloaded: {} {}", chunkLevel.hashCode(), chunkX, chunkZ);
    }
    for (var listener : chunkLoadEventListeners.keySet()) {
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
      String tellrawCommand = "/tellraw @a " + String.format(customNickname, message);
      systemCommandQueue.add(tellrawCommand);
      var minecraft = MinecraftClient.getInstance();
      var chatHud = minecraft.inGameHud.getChatHud();
      // TODO(maxuser): There appears to be a bug truncating the chat HUD command history. It might
      // be that onClientChat(...) can get called on a different thread from what other callers are
      // expecting, thereby corrupting the history. Verify whether this gets called on the same
      // thread as onClientWorldTick() or other events.
      chatHud.addToMessageHistory(message);
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

  private static Map<Integer, ScriptFunctionCall> clientChatReceivedEventListeners =
      new ConcurrentHashMap<>();

  public static class ChunkLoadEventListener {
    // Map packed chunk (x, z) to boolean: true if chunk is loaded, false otherwise.
    private final Map<Long, Boolean> chunksToLoad = new ConcurrentHashMap<>();

    // World with chunks to listen for. Store hash rather than reference to avoid memory leak.
    private final int levelHashCode;

    private final Runnable doneCallback;
    private int numUnloadedChunks = 0;
    private boolean suspended = false;

    public ChunkLoadEventListener(int x1, int z1, int x2, int z2, Runnable doneCallback) {
      var minecraft = MinecraftClient.getInstance();
      this.levelHashCode = minecraft.world.hashCode();
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
      var minecraft = MinecraftClient.getInstance();
      var level = minecraft.world;
      if (level.hashCode() != this.levelHashCode) {
        LOGGER.info("chunk listener's world doesn't match current world; clearing listener");
        chunksToLoad.clear();
        numUnloadedChunks = 0;
        return;
      }
      numUnloadedChunks = 0;
      var chunkManager = level.getChunkManager();
      for (var entry : chunksToLoad.entrySet()) {
        long packedChunkXZ = entry.getKey();
        int[] chunkCoords = unpackLong(packedChunkXZ);
        boolean isLoaded = chunkManager.getWorldChunk(chunkCoords[0], chunkCoords[1]) != null;
        entry.setValue(isLoaded);
        if (!isLoaded) {
          numUnloadedChunks++;
        }
      }
      LOGGER.info("Unloaded chunks after updateChunkStatuses: {}", numUnloadedChunks);
    }

    /** Returns true if the final outstanding chunk is loaded. */
    public synchronized boolean onChunkLoaded(WorldAccess chunkLevel, int chunkX, int chunkZ) {
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
          onFinished();
          return true;
        }
      }
      return false;
    }

    public synchronized void onChunkUnloaded(WorldAccess chunkLevel, int chunkX, int chunkZ) {
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

    public synchronized boolean isFinished() {
      return numUnloadedChunks == 0;
    }

    /** To be called when all requested chunks are loaded. */
    public synchronized void onFinished() {
      doneCallback.run();
    }
  }

  // Integer value represents the ID of the job that spawned this listener.
  private static Map<ChunkLoadEventListener, Integer> chunkLoadEventListeners =
      new ConcurrentHashMap<ChunkLoadEventListener, Integer>();

  private static String customNickname = null;
  private static Consumer<String> chatInterceptor = null;

  private static boolean areCommandsAllowed() {
    var minecraft = MinecraftClient.getInstance();
    var serverData = minecraft.getCurrentServerEntry();
    return serverData == null
        || serverBlockList.areCommandsAllowedForServer(serverData.name, serverData.address);
  }

  private static void processMessage(String message) {
    if (message.startsWith("\\")) {
      LOGGER.info("Processing command from message queue: {}", message);
      // TODO(maxuser): If there's a parent job that spawned this command, pass along the parent job
      // so that suspending or killing the parent job also suspends or kills the child job. Also,
      // child jobs are listed from `jobs` command and increment the job counter, but should they?
      // This speaks to the conceptual distinction between "shell job" and "process" which currently
      // isn't established.
      runMinescriptCommand(message.substring(1));
      return;
    }

    if (message.startsWith("|")) {
      var minecraft = MinecraftClient.getInstance();
      var chatHud = minecraft.inGameHud.getChatHud();
      if (message.startsWith("|{\"")) {
        chatHud.addMessage(Text.Serializer.fromJson(message.substring(1)));
      } else {
        chatHud.addMessage(Text.of(message.substring(1)));
      }
      return;
    }

    var minecraft = MinecraftClient.getInstance();
    var player = minecraft.player;
    if (message.startsWith("/")) {
      if (!areCommandsAllowed()) {
        LOGGER.info("Minecraft command blocked for server: {}", message); // [norewrite]
        return;
      }
      player.sendCommand(message.substring(1));
    } else {
      player.sendChatMessage(message, null /* preview */);
    }
  }

  private static String itemStackToJsonString(
      ItemStack itemStack, OptionalInt slot, boolean markSelected) {
    if (itemStack.getCount() == 0) {
      return "null";
    } else {
      var nbt = itemStack.getNbt();
      var out = new StringBuilder("{");
      out.append(
          String.format(
              "\"item\": \"%s\", \"count\": %d", itemStack.getItem(), itemStack.getCount()));
      if (nbt != null) {
        out.append(String.format(", \"nbt\": %s", GSON.toJson(nbt.toString())));
      }
      if (slot.isPresent()) {
        out.append(String.format(", \"slot\": %d", slot.getAsInt()));
      }
      if (markSelected) {
        out.append(", \"selected\":true");
      }
      out.append("}");
      return out.toString();
    }
  }

  private static String entitiesToJsonString(
      Iterable<? extends Entity> entities, boolean includeNbt) {
    var minecraft = MinecraftClient.getInstance();
    var player = minecraft.player;
    var result = new StringBuilder("[");
    for (var entity : entities) {
      if (result.length() > 1) {
        result.append(",");
      }
      result.append("{");
      result.append(String.format("\"name\":%s,", toJsonString(entity.getName().getString())));
      result.append(String.format("\"type\":%s,", toJsonString(entity.getType().toString())));
      if (entity instanceof LivingEntity) {
        var livingEntity = (LivingEntity) entity;
        result.append(String.format("\"health\":%s,", livingEntity.getHealth()));
      }
      if (entity == player) {
        result.append("\"local\":true,");
      }
      result.append(
          String.format("\"position\":[%s,%s,%s],", entity.getX(), entity.getY(), entity.getZ()));
      result.append(String.format("\"yaw\":%s,", entity.getYaw()));
      result.append(String.format("\"pitch\":%s,", entity.getPitch()));
      var v = entity.getVelocity();
      result.append(String.format("\"velocity\":[%s,%s,%s]", v.x, v.y, v.z));
      if (includeNbt) {
        var nbt = new NbtCompound();
        result.append(String.format(",\"nbt\":%s", toJsonString(entity.writeNbt(nbt).toString())));
      }
      result.append("}");
    }
    result.append("]");
    return result.toString();
  }

  private static boolean scriptFunctionDebugOutptut = false;

  private static final Map<KeyBinding, InputUtil.Key> boundKeys = new ConcurrentHashMap<>();

  private static void lazyInitBoundKeys() {
    // The map of bound keys must be initialized lazily because
    // minecraft.options is still null at the time the mod is initialized.
    if (!boundKeys.isEmpty()) {
      return;
    }

    // TODO(maxuser): These default bindings do not track custom bindings. Use
    // mixins to intercept KeyBinding constructor and setBoundKey method.
    var minecraft = MinecraftClient.getInstance();
    boundKeys.put(
        minecraft.options.forwardKey, InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_W));
    boundKeys.put(minecraft.options.backKey, InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_S));
    boundKeys.put(minecraft.options.leftKey, InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_A));
    boundKeys.put(
        minecraft.options.rightKey, InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_D));
    boundKeys.put(
        minecraft.options.jumpKey, InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_SPACE));
    boundKeys.put(
        minecraft.options.sprintKey,
        InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_LEFT_CONTROL));
    boundKeys.put(
        minecraft.options.sneakKey, InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_LEFT_SHIFT));
    boundKeys.put(
        minecraft.options.pickItemKey,
        InputUtil.Type.MOUSE.createFromCode(GLFW.GLFW_MOUSE_BUTTON_MIDDLE));
    boundKeys.put(
        minecraft.options.useKey,
        InputUtil.Type.MOUSE.createFromCode(GLFW.GLFW_MOUSE_BUTTON_RIGHT));
    boundKeys.put(
        minecraft.options.attackKey,
        InputUtil.Type.MOUSE.createFromCode(GLFW.GLFW_MOUSE_BUTTON_LEFT));
    boundKeys.put(
        minecraft.options.swapHandsKey, InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_F));
    boundKeys.put(minecraft.options.dropKey, InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_Q));
  }

  private static Optional<String> doPlayerAction(
      String functionName, KeyBinding keyBinding, List<?> args, String argsString) {
    if (args.size() == 1 && args.get(0) instanceof Boolean) {
      lazyInitBoundKeys();
      boolean pressed = (Boolean) args.get(0);
      var key = boundKeys.get(keyBinding);
      if (pressed) {
        KeyBinding.setKeyPressed(key, true);
        KeyBinding.onKeyPressed(key);
      } else {
        KeyBinding.setKeyPressed(key, false);
      }
      return Optional.of("true");
    } else {
      logUserError(
          "Error: `{}` expected 1 boolean param (true or false) but got: {}",
          functionName,
          argsString);
      return Optional.of("false");
    }
  }

  /** Returns int if object is a Number representing an int without truncation or rounding. */
  private static OptionalInt getStrictIntValue(Object object) {
    if (!(object instanceof Number)) {
      return OptionalInt.empty();
    }
    Number number = (Number) object;
    if (number instanceof Integer) {
      return OptionalInt.of(number.intValue());
    }
    if (number instanceof Long) {
      long lng = number.longValue();
      if (lng >= Integer.MIN_VALUE && lng <= Integer.MAX_VALUE) {
        return OptionalInt.of(number.intValue());
      } else {
        return OptionalInt.empty();
      }
    }
    if (number instanceof Double) {
      double dbl = number.doubleValue();
      if (!Double.isInfinite(dbl) && dbl == Math.floor(dbl)) {
        return OptionalInt.of(number.intValue());
      }
    }
    return OptionalInt.empty();
  }

  private static Optional<List<Integer>> getStrictIntList(Object object) {
    if (!(object instanceof List)) {
      return Optional.empty();
    }
    List<?> list = (List<?>) object;
    List<Integer> intList = new ArrayList<>();
    for (var element : list) {
      var asInt = getStrictIntValue(element);
      if (asInt.isEmpty()) {
        return Optional.empty();
      }
      intList.add(asInt.getAsInt());
    }
    return Optional.of(intList);
  }

  private static Optional<List<String>> getStringList(Object object) {
    if (!(object instanceof List)) {
      return Optional.empty();
    }
    List<?> list = (List<?>) object;
    List<String> stringList = new ArrayList<>();
    for (var element : list) {
      stringList.add(element.toString());
    }
    return Optional.of(stringList);
  }

  private static double computeDistance(
      double x1, double y1, double z1, double x2, double y2, double z2) {
    double dx = x1 - x2;
    double dy = y1 - y2;
    double dz = z1 - z2;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  public static String getWorldName() {
    var minecraft = MinecraftClient.getInstance();
    var serverData = minecraft.getCurrentServerEntry();
    var serverName = serverData == null ? null : serverData.name;
    var server = minecraft.getServer();
    var saveProperties = server == null ? null : server.getSaveProperties();
    String saveName = saveProperties == null ? null : saveProperties.getLevelName();
    return serverName == null ? saveName : serverName;
  }

  public static Optional<String> getScreenName() {
    var minecraft = MinecraftClient.getInstance();
    var screen = minecraft.currentScreen;
    if (screen == null) {
      return Optional.empty();
    }
    String name = screen.getTitle().getString();
    if (name.isEmpty()) {
      if (screen instanceof CreativeInventoryScreen) {
        name = "Creative Inventory";
      } else if (screen instanceof LevelLoadingScreen) {
        name = "L" + "evel Loading"; // Split literal to prevent symbol renaming.
      } else if (screen instanceof ProgressScreen) {
        name = "Progess";
      } else {
        // The class name is not descriptive in production builds where symbols
        // are obfuscated, but using the class name allows callers to
        // differentiate untitled screen types from each other.
        name = screen.getClass().getName();
      }
    }
    return Optional.of(name);
  }

  /** Returns a JSON response string if a script function is called. */
  private static Optional<String> handleScriptFunction(
      Job job, long funcCallId, String functionName, List<?> args, String argsString) {
    var minecraft = MinecraftClient.getInstance();
    var world = minecraft.world;
    var player = minecraft.player;
    var options = minecraft.options;

    Consumer<Integer> numParamsErrorLogger =
        (numParams) -> {
          logUserError(
              "Error: `{}` expected {} params but got: {}", functionName, numParams, argsString);
        };

    BiConsumer<String, String> paramTypeErrorLogger =
        (param, expectedType) -> {
          logUserError(
              "Error: `{}` expected param `{}` to be {} but got: {}",
              functionName,
              param,
              expectedType,
              argsString);
        };

    switch (functionName) {
      case "player_position":
        if (args.isEmpty()) {
          return Optional.of(
              String.format("[%s, %s, %s]", player.getX(), player.getY(), player.getZ()));
        } else {
          logUserError("Error: `{}` expected no params but got: {}", functionName, argsString);
          return Optional.of("null");
        }

      case "player_set_position":
        {
          if (args.size() < 3
              || !(args.get(0) instanceof Number)
              || !(args.get(1) instanceof Number)
              || !(args.get(2) instanceof Number)) {
            logUserError(
                "Error: `{}` expected 3 to 5 number params but got: {}", functionName, argsString);
            return Optional.of("false");
          }
          double x = ((Number) args.get(0)).doubleValue();
          double y = ((Number) args.get(1)).doubleValue();
          double z = ((Number) args.get(2)).doubleValue();
          float yaw = player.getYaw();
          float pitch = player.getPitch();
          if (args.size() >= 4 && args.get(3) != null) {
            if (!(args.get(3) instanceof Number)) {
              paramTypeErrorLogger.accept("yaw", "float");
              return Optional.of("false");
            }
            yaw = ((Number) args.get(3)).floatValue();
          }
          if (args.size() >= 5 && args.get(4) != null) {
            if (!(args.get(4) instanceof Number)) {
              paramTypeErrorLogger.accept("pitch", "float");
              return Optional.of("false");
            }
            pitch = ((Number) args.get(4)).floatValue();
          }
          player.refreshPositionAndAngles(x, y, z, yaw, pitch);
          return Optional.of("true");
        }

      case "player_name":
        if (args.isEmpty()) {
          return Optional.of(toJsonString(player.getName().getString()));
        } else {
          logUserError("Error: `{}` expected no params but got: {}", functionName, argsString);
          return Optional.of("null");
        }

      case "getblock":
        if (args.size() == 3
            && args.get(0) instanceof Number
            && args.get(1) instanceof Number
            && args.get(2) instanceof Number) {
          World level = player.getEntityWorld();
          int arg0 = ((Number) args.get(0)).intValue();
          int arg1 = ((Number) args.get(1)).intValue();
          int arg2 = ((Number) args.get(2)).intValue();
          Optional<String> block =
              blockStateToString(level.getBlockState(new BlockPos(arg0, arg1, arg2)));
          return Optional.of(block.map(str -> toJsonString(str)).orElse("null"));
        } else {
          logUserError(
              "Error: `{}` expected 3 params (x, y, z) but got: {}", functionName, argsString);
          return Optional.of("null");
        }

      case "getblocklist":
        {
          Supplier<Optional<String>> badArgsResponse =
              () -> {
                logUserError(
                    "Error: `{}` expected a list of (x, y, z) positions but got: {}",
                    functionName,
                    argsString);
                return Optional.of("null");
              };

          if (args.size() != 1 || !(args.get(0) instanceof List)) {
            return badArgsResponse.get();
          }
          List<?> positions = (List<?>) args.get(0);
          World level = player.getEntityWorld();
          List<String> blocks = new ArrayList<>();
          var pos = new BlockPos.Mutable();
          for (var position : positions) {
            if (!(position instanceof List)) {
              return badArgsResponse.get();
            }
            List<?> coords = (List<?>) position;
            if (coords.size() != 3
                || !(coords.get(0) instanceof Number)
                || !(coords.get(1) instanceof Number)
                || !(coords.get(2) instanceof Number)) {
              return badArgsResponse.get();
            }
            int x = ((Number) coords.get(0)).intValue();
            int y = ((Number) coords.get(1)).intValue();
            int z = ((Number) coords.get(2)).intValue();
            Optional<String> block = blockStateToString(level.getBlockState(pos.set(x, y, z)));
            blocks.add(block.orElse(null));
          }
          return Optional.of(GSON.toJson(blocks));
        }

      case "register_key_event_listener":
        if (!args.isEmpty()) {
          throw new IllegalArgumentException("Expected no params but got: " + argsString);
        }
        if (keyEventListeners.containsKey(job.jobId())) {
          throw new IllegalStateException(
              "Failed to create listener because a listener is already registered for job: "
                  + job.jobSummary());
        }
        keyEventListeners.put(job.jobId(), new ScriptFunctionCall(job, funcCallId));
        return Optional.empty();

      case "unregister_key_event_listener":
        if (!args.isEmpty()) {
          logUserError("Error: `{}` expected no params but got: {}", functionName, argsString);
          return Optional.of("false");
        } else if (!keyEventListeners.containsKey(job.jobId())) {
          logUserError(
              "Error: `{}` has no listeners to unregister for job: {}",
              functionName,
              job.jobSummary());
          return Optional.of("false");
        } else {
          keyEventListeners.remove(job.jobId());
          return Optional.of("true");
        }

      case "register_chat_message_listener":
        if (!args.isEmpty()) {
          throw new IllegalArgumentException("Expected no params but got: " + argsString);
        }
        if (clientChatReceivedEventListeners.containsKey(job.jobId())) {
          throw new IllegalStateException(
              "Failed to create listener because a listener is already registered for job: "
                  + job.jobSummary());
        }
        clientChatReceivedEventListeners.put(job.jobId(), new ScriptFunctionCall(job, funcCallId));
        return Optional.empty();

      case "unregister_chat_message_listener":
        if (!args.isEmpty()) {
          logUserError("Error: `{}` expected no params but got: {}", functionName, argsString);
          return Optional.of("false");
        } else if (!clientChatReceivedEventListeners.containsKey(job.jobId())) {
          logUserError(
              "Error: `{}` has no listeners to unregister for job: {}",
              functionName,
              job.jobSummary());
          return Optional.of("false");
        } else {
          clientChatReceivedEventListeners.remove(job.jobId());
          return Optional.of("true");
        }

      case "register_chat_message_interceptor":
        if (args.isEmpty()) {
          if (chatInterceptor == null) {
            chatInterceptor =
                s -> {
                  job.respond(funcCallId, toJsonString(s), false);
                };
            job.addAtExitHandler(() -> chatInterceptor = null);
            logUserInfo("Chat interceptor enabled for job: {}", job.jobSummary());
          } else {
            logUserError("Error: Chat interceptor already enabled for another job.");
          }
          return Optional.empty();
        } else {
          logUserError("Error: `{}` expected no params but got: {}", functionName, argsString);
          return Optional.empty();
        }

      case "unregister_chat_message_interceptor":
        if (args.isEmpty()) {
          if (chatInterceptor != null) {
            chatInterceptor = null;
            logUserInfo("Chat interceptor disabled for job: {}", job.jobSummary());
          } else {
            logUserError("Error: Chat interceptor already disabled: {}", job.jobSummary());
          }
          return Optional.empty();
        } else {
          logUserError("Error: `{}` expected no params but got: {}", functionName, argsString);
          return Optional.empty();
        }

      case "await_loaded_region":
        if (args.size() == 4
            && args.get(0) instanceof Number
            && args.get(1) instanceof Number
            && args.get(2) instanceof Number
            && args.get(3) instanceof Number) {
          Number arg0 = (Number) args.get(0);
          Number arg1 = (Number) args.get(1);
          Number arg2 = (Number) args.get(2);
          Number arg3 = (Number) args.get(3);
          var listener =
              new ChunkLoadEventListener(
                  arg0.intValue(),
                  arg1.intValue(),
                  arg2.intValue(),
                  arg3.intValue(),
                  () -> job.respond(funcCallId, "true", true));
          listener.updateChunkStatuses();
          if (listener.isFinished()) {
            listener.onFinished();
          } else {
            chunkLoadEventListeners.put(listener, job.jobId());
          }
          return Optional.empty();
        } else {
          throw new IllegalArgumentException(
              "Expected 4 number params (x1, z1, x2, z2) but got: " + argsString);
        }

      case "set_nickname":
        if (args.isEmpty()) {
          logUserInfo(
              "Chat nickname reset to default; was {}",
              customNickname == null ? "default already" : toJsonString(customNickname));
          customNickname = null;
          return Optional.of("true");
        } else if (args.size() == 1) {
          String arg = args.get(0).toString();
          if (arg.contains("%s")) {
            logUserInfo("Chat nickname set to {}.", toJsonString(arg));
            customNickname = arg;
            return Optional.of("true");
          } else {
            logUserError(
                "Error: `{}` expects nickname to contain %s as a placeholder for"
                    + " message text.",
                functionName);
            return Optional.of("false");
          }
        } else {
          logUserError("Error: `{}` expected 0 or 1 param but got: {}", functionName, argsString);
          return Optional.of("false");
        }

      case "player_hand_items":
        if (args.isEmpty()) {
          var result = new StringBuilder("[");
          for (var itemStack : player.getHandItems()) {
            if (result.length() > 1) {
              result.append(",");
            }
            result.append(itemStackToJsonString(itemStack, OptionalInt.empty(), false));
          }
          result.append("]");
          return Optional.of(result.toString());
        } else {
          logUserError("Error: `{}` expected no params but got: {}", functionName, argsString);
          return Optional.of("null");
        }

      case "player_inventory":
        if (args.isEmpty()) {
          var inventory = player.getInventory();
          var result = new StringBuilder("[");
          int selectedSlot = inventory.selectedSlot;
          for (int i = 0; i < inventory.size(); i++) {
            var itemStack = inventory.getStack(i);
            if (itemStack.getCount() > 0) {
              if (result.length() > 1) {
                result.append(",");
              }
              result.append(itemStackToJsonString(itemStack, OptionalInt.of(i), i == selectedSlot));
            }
          }
          result.append("]");
          return Optional.of(result.toString());
        } else {
          logUserError("Error: `{}` expected no params but got: {}", functionName, argsString);
          return Optional.of("null");
        }

      case "player_inventory_slot_to_hotbar":
        {
          OptionalInt value =
              (args.size() == 1) ? getStrictIntValue(args.get(0)) : OptionalInt.empty();
          if (value.isPresent()) {
            int slot = value.getAsInt();
            var inventory = player.getInventory();
            var connection = minecraft.getNetworkHandler();
            connection.sendPacket(new PickFromInventoryC2SPacket(slot));
            return Optional.of(Integer.toString(inventory.selectedSlot));
          } else {
            logUserError("Error: `{}` expected 1 int param but got: {}", functionName, argsString);
            return Optional.of("null");
          }
        }

      case "player_inventory_select_slot":
        {
          OptionalInt value =
              (args.size() == 1) ? getStrictIntValue(args.get(0)) : OptionalInt.empty();
          if (value.isPresent()) {
            int slot = value.getAsInt();
            var inventory = player.getInventory();
            var previouslySelectedSlot = inventory.selectedSlot;
            inventory.selectedSlot = slot;
            return Optional.of(Integer.toString(previouslySelectedSlot));
          } else {
            logUserError("Error: `{}` expected 1 int param but got: {}", functionName, argsString);
            return Optional.of("null");
          }
        }

      case "player_press_forward":
        return doPlayerAction(functionName, options.forwardKey, args, argsString);

      case "player_press_backward":
        return doPlayerAction(functionName, options.backKey, args, argsString);

      case "player_press_left":
        return doPlayerAction(functionName, options.leftKey, args, argsString);

      case "player_press_right":
        return doPlayerAction(functionName, options.rightKey, args, argsString);

      case "player_press_jump":
        return doPlayerAction(functionName, options.jumpKey, args, argsString);

      case "player_press_sprint":
        return doPlayerAction(functionName, options.sprintKey, args, argsString);

      case "player_press_sneak":
        return doPlayerAction(functionName, options.sneakKey, args, argsString);

      case "player_press_pick_item":
        return doPlayerAction(functionName, options.pickItemKey, args, argsString);

      case "player_press_use":
        return doPlayerAction(functionName, options.useKey, args, argsString);

      case "player_press_attack":
        return doPlayerAction(functionName, options.attackKey, args, argsString);

      case "player_press_swap_hands":
        return doPlayerAction(functionName, options.swapHandsKey, args, argsString);

      case "player_press_drop":
        return doPlayerAction(functionName, options.dropKey, args, argsString);

      case "player_orientation":
        if (args.isEmpty()) {
          return Optional.of(String.format("[%s, %s]", player.getYaw(), player.getPitch()));
        } else {
          logUserError("Error: `{}` expected no params but got: {}", functionName, argsString);
          return Optional.of("null");
        }

      case "player_set_orientation":
        if (args.size() == 2 && args.get(0) instanceof Number && args.get(1) instanceof Number) {
          Number yaw = (Number) args.get(0);
          Number pitch = (Number) args.get(1);
          player.setYaw(yaw.floatValue() % 360.0f);
          player.setPitch(pitch.floatValue() % 360.0f);
          return Optional.of("true");
        } else {
          logUserError(
              "Error: `{}` expected 2 number params but got: {}", functionName, argsString);
          return Optional.of("false");
        }

      case "player_get_targeted_block":
        {
          // See minecraft.client.gui.hud.DebugHud for implementation of F3 debug screen.
          if (args.size() != 1) {
            numParamsErrorLogger.accept(1);
            return Optional.of("null");
          }
          if (!(args.get(0) instanceof Number)) {
            paramTypeErrorLogger.accept("max_distance", "float");
            return Optional.of("null");
          }
          double maxDistance = ((Number) args.get(0)).doubleValue();
          var entity = minecraft.getCameraEntity();
          var blockHit = entity.raycast(maxDistance, 0.0f, false);
          if (blockHit.getType() == HitResult.Type.BLOCK) {
            var hitResult = (BlockHitResult) blockHit;
            var blockPos = hitResult.getBlockPos();
            double playerDistance =
                computeDistance(
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    blockPos.getX(),
                    blockPos.getY(),
                    blockPos.getZ());
            World level = player.getEntityWorld();
            Optional<String> block = blockStateToString(level.getBlockState(blockPos));
            return Optional.of(
                String.format(
                    "[[%d,%d,%d],%s,\"%s\",%s]",
                    blockPos.getX(),
                    blockPos.getY(),
                    blockPos.getZ(),
                    playerDistance,
                    hitResult.getSide(),
                    block.map(str -> toJsonString(str)).orElse("null")));
          } else {
            return Optional.of("null");
          }
        }

      case "player_health":
        return Optional.of(String.format("%s", player.getHealth()));

      case "players":
        {
          if (args.size() != 1) {
            numParamsErrorLogger.accept(1);
            return Optional.of("null");
          }
          if (!(args.get(0) instanceof Boolean)) {
            paramTypeErrorLogger.accept("nbt", "bool");
            return Optional.of("null");
          }
          boolean nbt = (Boolean) args.get(0);
          return Optional.of(entitiesToJsonString(world.getPlayers(), nbt));
        }

      case "entities":
        {
          if (args.size() != 1) {
            numParamsErrorLogger.accept(1);
            return Optional.of("null");
          }
          if (!(args.get(0) instanceof Boolean)) {
            paramTypeErrorLogger.accept("nbt", "bool");
            return Optional.of("null");
          }
          boolean nbt = (Boolean) args.get(0);
          return Optional.of(entitiesToJsonString(world.getEntities(), nbt));
        }

      case "world_properties":
        {
          var levelProperties = world.getLevelProperties();
          var difficulty = levelProperties.getDifficulty();
          var serverData = minecraft.getCurrentServerEntry();
          var serverAddress = serverData == null ? "localhost" : serverData.address;
          return Optional.of(
              String.format(
                  "{\"game_ticks\":%s,\"day_ticks\":%s,\"raining\":%s,\"thundering\":%s,"
                      + "\"spawn\":[%s,%s,%s],\"hardcore\":%s,\"difficulty\":%s,"
                      + "\"name\": %s,\"address\":%s}",
                  levelProperties.getTime(),
                  levelProperties.getTimeOfDay(),
                  levelProperties.isRaining(),
                  levelProperties.isThundering(),
                  levelProperties.getSpawnX(),
                  levelProperties.getSpawnY(),
                  levelProperties.getSpawnZ(),
                  levelProperties.isHardcore(),
                  toJsonString(difficulty.getName()),
                  toJsonString(getWorldName()),
                  toJsonString(serverAddress)));
        }

      case "log":
        if (args.size() == 1 && args.get(0) instanceof String) {
          String message = (String) args.get(0);
          LOGGER.info(message);
          return Optional.of("true");
        } else {
          logUserError("Error: `{}` expected 1 string param but got: {}", functionName, argsString);
          return Optional.of("false");
        }

      case "screenshot":
        final Optional<String> filename;
        final String response;
        if (args.isEmpty()) {
          filename = Optional.empty();
          response = "true";
        } else if (args.size() == 1 && args.get(0) instanceof String) {
          filename = Optional.of((String) args.get(0));
          response = "true";
        } else {
          filename = null;
          logUserError(
              "Error: `{}` expected no params or 1 string param but got: {}",
              functionName,
              argsString);
          response = "false";
        }
        if (filename != null) {
          ScreenshotRecorder.saveScreenshot(
              minecraft.runDirectory,
              filename.orElse(null),
              minecraft.getFramebuffer(),
              message -> job.enqueueStderr(message.getString()));
        }
        return Optional.of(response);

      case "blockpack_read_world":
        {
          // Python function signature:
          //    (pos1: BlockPos, pos2: BlockPos,
          //     rotation: Rotation = None, offset: BlockPos = None,
          //     comments: Dict[str, str] = {}, safety_limit: bool = True) -> int
          if (args.size() != 6) {
            numParamsErrorLogger.accept(6);
            return Optional.of("null");
          }

          Optional<List<Integer>> list1 = getStrictIntList(args.get(0));
          if (list1.isEmpty() || list1.get().size() != 3) {
            paramTypeErrorLogger.accept("pos1", "a sequence of 3 ints");
            return Optional.of("null");
          }

          Optional<List<Integer>> list2 = getStrictIntList(args.get(1));
          if (list2.isEmpty() || list2.get().size() != 3) {
            paramTypeErrorLogger.accept("pos2", "a sequence of 3 ints");
            return Optional.of("null");
          }

          int[] rotation = null;
          if (args.get(2) != null) {
            Optional<List<Integer>> list = getStrictIntList(args.get(2));
            if (list.isEmpty() || list.get().size() != 9) {
              paramTypeErrorLogger.accept("rotation", "a sequence of 9 ints");
              return Optional.of("null");
            }
            rotation = list.get().stream().mapToInt(Integer::intValue).toArray();
          }

          int[] offset = null;
          if (args.get(3) != null) {
            Optional<List<Integer>> list = getStrictIntList(args.get(3));
            if (list.isEmpty() || list.get().size() != 3) {
              paramTypeErrorLogger.accept("offset", "a sequence of 3 ints");
              return Optional.of("null");
            }
            offset = list.get().stream().mapToInt(Integer::intValue).toArray();
          }

          if (!(args.get(4) instanceof Map)) {
            paramTypeErrorLogger.accept("comment", "a dictionary");
            return Optional.of("null");
          }
          var comments = (Map<String, String>) args.get(4);

          if (!(args.get(5) instanceof Boolean)) {
            paramTypeErrorLogger.accept("safety_limit", "bool");
            return Optional.of("null");
          }
          boolean safetyLimit = (Boolean) args.get(5);

          var pos1 = list1.get();
          var pos2 = list2.get();
          var blockpacker = new BlockPacker();
          if (!readBlocks(
              pos1.get(0),
              pos1.get(1),
              pos1.get(2),
              pos2.get(0),
              pos2.get(1),
              pos2.get(2),
              safetyLimit,
              new BlockPack.TransformedBlockConsumer(rotation, offset, blockpacker))) {
            return Optional.of("null");
          }
          blockpacker.comments().putAll(comments);
          var blockpack = blockpacker.pack();
          int key = job.blockpacks.retain(blockpack);
          return Optional.of(Integer.toString(key));
        }

      case "blockpack_read_file":
        {
          // Python function signature:
          //    (filename: str) -> int
          if (args.size() != 1 || !(args.get(0) instanceof String)) {
            logUserError(
                "Error: `{}` expected one param of type string but got: {}",
                functionName,
                argsString);
            return Optional.of("null");
          }
          String blockpackFilename = (String) args.get(0);
          try {
            var blockpack = BlockPack.readZipFile(blockpackFilename);
            int key = job.blockpacks.retain(blockpack);
            return Optional.of(Integer.toString(key));
          } catch (Exception e) {
            logUserError("Error while reading blockpack {}: {}", blockpackFilename, e.getMessage());
            return Optional.of("null");
          }
        }

      case "blockpack_import_data":
        {
          // Python function signature:
          //    (base64_data: str) -> int
          if (args.size() != 1 || !(args.get(0) instanceof String)) {
            logUserError(
                "Error: `{}` expected one param of type string but got: {}",
                functionName,
                argsString);
            return Optional.of("null");
          }
          String base64Data = (String) args.get(0);
          try {
            var blockpack = BlockPack.fromBase64EncodedString(base64Data);
            int key = job.blockpacks.retain(blockpack);
            return Optional.of(Integer.toString(key));
          } catch (Exception e) {
            logException(e);
            return Optional.of("null");
          }
        }

      case "blockpack_block_bounds":
        {
          // Python function signature:
          //    (blockpack_id) -> Tuple[Tuple[int, int, int], Tuple[int, int, int]]
          OptionalInt value = args.isEmpty() ? OptionalInt.empty() : getStrictIntValue(args.get(0));
          if (!value.isPresent()) {
            logUserError(
                "Error: `{}` expected one int param but got: {}", functionName, argsString);
            return Optional.of("null");
          }

          int blockpackId = value.getAsInt();
          var blockpack = job.blockpacks.getById(blockpackId);
          if (blockpack == null) {
            logUserError(
                "Error: `{}` Failed to find BlockPack[{}]: {}",
                functionName,
                blockpackId,
                argsString);
            return Optional.of("null");
          }

          int[] bounds = blockpack.blockBounds();
          return Optional.of(
              String.format(
                  "[[%d,%d,%d],[%d,%d,%d]]",
                  bounds[0], bounds[1], bounds[2], bounds[3], bounds[4], bounds[5]));
        }

      case "blockpack_comments":
        {
          // Python function signature:
          //    (blockpack_id) -> Dict[str, str]
          OptionalInt value = args.isEmpty() ? OptionalInt.empty() : getStrictIntValue(args.get(0));
          if (!value.isPresent()) {
            logUserError(
                "Error: `{}` expected one int param but got: {}", functionName, argsString);
            return Optional.of("null");
          }

          int blockpackId = value.getAsInt();
          var blockpack = job.blockpacks.getById(blockpackId);
          if (blockpack == null) {
            logUserError(
                "Error: `{}` Failed to find BlockPack[{}]: {}",
                functionName,
                blockpackId,
                argsString);
            return Optional.of("null");
          }

          return Optional.of(GSON.toJson(blockpack.comments()));
        }

      case "blockpack_write_world":
        {
          // Python function signature:
          //    (blockpack_id: int, rotation: Rotation = None, offset: BlockPos = None) -> bool
          if (args.size() != 3) {
            numParamsErrorLogger.accept(3);
            return Optional.of("false");
          }

          OptionalInt value = getStrictIntValue(args.get(0));
          if (!value.isPresent()) {
            paramTypeErrorLogger.accept("blockpack_id", "int");
            return Optional.of("false");
          }
          int blockpackId = value.getAsInt();

          int[] rotation = null;
          if (args.get(1) != null) {
            Optional<List<Integer>> list = getStrictIntList(args.get(1));
            if (list.isEmpty() || list.get().size() != 9) {
              paramTypeErrorLogger.accept("rotation", "a sequence of 9 ints");
              return Optional.of("false");
            }
            rotation = list.get().stream().mapToInt(Integer::intValue).toArray();
          }

          int[] offset = null;
          if (args.get(2) != null) {
            Optional<List<Integer>> list = getStrictIntList(args.get(2));
            if (list.isEmpty() || list.get().size() != 3) {
              paramTypeErrorLogger.accept("offset", "a sequence of 3 ints");
              return Optional.of("false");
            }
            offset = list.get().stream().mapToInt(Integer::intValue).toArray();
          }

          var blockpack = job.blockpacks.getById(blockpackId);
          if (blockpack == null) {
            logUserError(
                "Error: `{}` Failed to find BlockPack[{}] to write to world: {}",
                functionName,
                blockpackId,
                argsString);
            return Optional.of("false");
          }

          blockpack.getBlockCommands(rotation, offset, job::enqueueStdout);
          return Optional.of("true");
        }

      case "blockpack_write_file":
        {
          // Python function signature:
          //    (blockpack_id: int, filename: str) -> bool
          OptionalInt value = args.isEmpty() ? OptionalInt.empty() : getStrictIntValue(args.get(0));
          if (!value.isPresent()) {
            logUserError(
                "Error: `{}` expected first param to be int but got: {}", functionName, argsString);
            return Optional.of("false");
          }
          if (args.size() < 2 || !(args.get(1) instanceof String)) {
            logUserError(
                "Error: `{}` expected second param to be string but got: {}",
                functionName,
                argsString);
            return Optional.of("false");
          }

          int blockpackId = value.getAsInt();
          String blockpackFilename = (String) args.get(1);

          var blockpack = job.blockpacks.getById(blockpackId);
          if (blockpack == null) {
            logUserError(
                "Error: `{}` Failed to find BlockPack[{}] to write to file: {}",
                functionName,
                blockpackId,
                argsString);
            return Optional.of("false");
          }

          try {
            blockpack.writeZipFile(blockpackFilename);
            return Optional.of("true");
          } catch (Exception e) {
            logUserError("Error while writing blockpack {}: {}", blockpackFilename, e.getMessage());
            return Optional.of("false");
          }
        }

      case "blockpack_export_data":
        {
          // Python function signature:
          //    (blockpack_id: int) -> str
          OptionalInt value =
              (args.size() == 1) ? getStrictIntValue(args.get(0)) : OptionalInt.empty();
          if (!value.isPresent()) {
            logUserError("Error: `{}` expected 1 int param but got: {}", functionName, argsString);
            return Optional.of("null");
          }
          var blockpack = job.blockpacks.getById(value.getAsInt());
          if (blockpack == null) {
            logUserError(
                "Error: `{}` Failed to find BlockPack[{}] from which to export data: {}",
                functionName,
                value.getAsInt(),
                argsString);
            return Optional.of("null");
          }
          return Optional.of(String.format("\"%s\"", blockpack.toBase64EncodedString()));
        }

      case "blockpack_delete":
        {
          // Python function signature:
          //    (blockpack_id: int) -> bool
          OptionalInt value =
              (args.size() == 1) ? getStrictIntValue(args.get(0)) : OptionalInt.empty();
          if (!value.isPresent()) {
            logUserError("Error: `{}` expected 1 int param but got: {}", functionName, argsString);
            return Optional.of("false");
          }
          var blockpack = job.blockpacks.releaseById(value.getAsInt());
          if (blockpack == null) {
            logUserError(
                "Error: `{}` Failed to find BlockPack[{}] to delete: {}",
                functionName,
                value.getAsInt(),
                argsString);
            return Optional.of("false");
          }
          return Optional.of("true");
        }

      case "blockpacker_create":
        // Python function signature:
        //    () -> int
        return Optional.of(Integer.toString(job.blockpackers.retain(new BlockPacker())));

      case "blockpacker_add_blocks":
        {
          // Python function signature:
          //    (blockpacker_id: int, base_pos: BlockPos,
          //     base64_setblocks: str, base64_fills: str, blocks: List[str]) -> bool
          if (args.size() != 5) {
            logUserError("Error: `{}` expected 5 params but got: {}", functionName, argsString);
            return Optional.of("false");
          }

          OptionalInt value = getStrictIntValue(args.get(0));
          if (!value.isPresent()) {
            logUserError(
                "Error: `{}` expected first param to be int but got: {}", functionName, argsString);
            return Optional.of("false");
          }

          Optional<List<Integer>> list = getStrictIntList(args.get(1));
          if (list.isEmpty() || list.get().size() != 3) {
            logUserError(
                "Error: `{}` expected second param to be list of 3 ints but got: {}",
                functionName,
                argsString);
            return Optional.of("false");
          }
          var basePos = list.get();

          String setblocksBase64 = args.get(2).toString();
          String fillsBase64 = args.get(3).toString();
          Optional<List<String>> blocks = getStringList(args.get(4));
          if (blocks.isEmpty()) {
            paramTypeErrorLogger.accept("blocks", "list of string");
            return Optional.of("false");
          }

          var blockpacker = job.blockpackers.getById(value.getAsInt());
          if (blockpacker == null) {
            logUserError(
                "Error: `{}` Failed to find BlockPacker[{}]: {}",
                functionName,
                value.getAsInt(),
                argsString);
            return Optional.of("false");
          }

          blockpacker.addBlocks(
              basePos.get(0),
              basePos.get(1),
              basePos.get(2),
              setblocksBase64,
              fillsBase64,
              blocks.get());
          return Optional.of("true");
        }

      case "blockpacker_add_blockpack":
        {
          // Python function signature:
          //    (blockpacker_id: int, blockpack_id: int,
          //     rotation: Rotation = None, offset: BlockPos = None) -> bool
          if (args.size() != 4) {
            numParamsErrorLogger.accept(4);
            return Optional.of("false");
          }

          OptionalInt value1 = getStrictIntValue(args.get(0));
          if (!value1.isPresent()) {
            paramTypeErrorLogger.accept("blockpacker_id", "int");
            return Optional.of("false");
          }

          OptionalInt value2 = getStrictIntValue(args.get(1));
          if (!value2.isPresent()) {
            paramTypeErrorLogger.accept("blockpack_id", "int");
            return Optional.of("false");
          }

          int[] rotation = null;
          if (args.get(2) != null) {
            Optional<List<Integer>> list = getStrictIntList(args.get(2));
            if (list.isEmpty() || list.get().size() != 9) {
              paramTypeErrorLogger.accept("rotation", "a sequence of 9 ints");
              return Optional.of("false");
            }
            rotation = list.get().stream().mapToInt(Integer::intValue).toArray();
          }

          int[] offset = null;
          if (args.get(3) != null) {
            Optional<List<Integer>> list = getStrictIntList(args.get(3));
            if (list.isEmpty() || list.get().size() != 3) {
              paramTypeErrorLogger.accept("offset", "a sequence of 3 ints");
              return Optional.of("false");
            }
            offset = list.get().stream().mapToInt(Integer::intValue).toArray();
          }

          var blockpacker = job.blockpackers.getById(value1.getAsInt());
          if (blockpacker == null) {
            logUserError(
                "Error: `{}` Failed to find BlockPacker[{}]: {}",
                functionName,
                value1.getAsInt(),
                argsString);
            return Optional.of("false");
          }

          var blockpack = job.blockpacks.getById(value2.getAsInt());
          if (blockpack == null) {
            logUserError(
                "Error: `{}` Failed to find BlockPack[{}]: {}",
                functionName,
                value2.getAsInt(),
                argsString);
            return Optional.of("false");
          }

          blockpack.getBlocks(
              new BlockPack.TransformedBlockConsumer(rotation, offset, blockpacker));

          return Optional.of("true");
        }

      case "blockpacker_pack":
        {
          // Python function signature:
          //    (blockpacker_id: int, comments: Dict[str, str]) -> int
          if (args.size() != 2) {
            logUserError("Error: `{}` expected 2 params but got: {}", functionName, argsString);
            return Optional.of("null");
          }

          OptionalInt value = getStrictIntValue(args.get(0));
          if (!value.isPresent()) {
            logUserError(
                "Error: `{}` expected first param to be int but got: {}", functionName, argsString);
            return Optional.of("null");
          }

          if (!(args.get(1) instanceof Map)) {
            logUserError(
                "Error: `{}` expected `comment` param to be a dictionary but got: {}",
                functionName,
                argsString);
            return Optional.of("null");
          }
          var comments = (Map<String, String>) args.get(1);

          var blockpacker = job.blockpackers.getById(value.getAsInt());
          if (blockpacker == null) {
            logUserError(
                "Error: `{}` Failed to find BlockPacker[{}]: {}",
                functionName,
                value.getAsInt(),
                argsString);
            return Optional.of("null");
          }

          blockpacker.comments().putAll(comments);
          return Optional.of(Integer.toString(job.blockpacks.retain(blockpacker.pack())));
        }

      case "blockpacker_delete":
        {
          // Python function signature:
          //    (blockpacker_id: int) -> bool
          OptionalInt value =
              (args.size() == 1) ? getStrictIntValue(args.get(0)) : OptionalInt.empty();
          if (!value.isPresent()) {
            logUserError("Error: `{}` expected 1 int param but got: {}", functionName, argsString);
            return Optional.of("false");
          }
          var blockpacker = job.blockpackers.releaseById(value.getAsInt());
          if (blockpacker == null) {
            logUserError(
                "Error: `{}` Failed to find BlockPacker[{}] to delete: {}",
                functionName,
                value.getAsInt(),
                argsString);
            return Optional.of("false");
          }
          return Optional.of("true");
        }

      case "screen_name":
        if (!args.isEmpty()) {
          throw new IllegalArgumentException("Expected no params but got: " + argsString);
        }
        return Optional.of(toJsonString(getScreenName().orElse(null)));

      case "flush":
        if (args.isEmpty()) {
          return Optional.of("true");
        } else {
          logUserError("Error: `{}` expected no params but got: {}", functionName, argsString);
          return Optional.of("false");
        }

      case "exit!":
        if (funcCallId == 0) {
          return Optional.of("\"exit!\"");
        } else {
          return Optional.of("null");
        }

      default:
        logUserError(
            "Error: unknown function `{}` called from job: {}", functionName, job.jobSummary());
        return Optional.of("false");
    }
  }

  public static void handleAutorun(String worldName) {
    LOGGER.info("Handling autorun for world `{}`", worldName);
    var commands = new ArrayList<String>();

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
      var minecraft = MinecraftClient.getInstance();
      var player = minecraft.player;

      String worldName = getWorldName();
      if (!autorunHandled.getAndSet(true) && worldName != null) {
        systemCommandQueue.clear();
        loadConfig();
        handleAutorun(worldName);
      }

      if (player != null && (!systemCommandQueue.isEmpty() || !jobs.getMap().isEmpty())) {
        World level = player.getEntityWorld();
        boolean hasCommand;
        int iterations = 0;
        do {
          hasCommand = false;
          ++iterations;
          String command = systemCommandQueue.poll();
          if (command != null) {
            hasCommand = true;
            processMessage(command);
          }
          for (var job : jobs.getMap().values()) {
            if (job.state() == JobState.RUNNING) {
              try {
                String jobCommand = job.commandQueue().poll();
                if (jobCommand != null) {
                  hasCommand = true;
                  jobs.getUndoForJob(job).ifPresent(u -> u.processCommandToUndo(level, jobCommand));
                  if (jobCommand.startsWith("?") && jobCommand.length() > 1) {
                    String[] functionCall = jobCommand.substring(1).split("\\s+", 3);
                    long funcCallId = Long.valueOf(functionCall[0]);
                    String functionName = functionCall[1];
                    String argsString = functionCall.length == 3 ? functionCall[2] : "";
                    var gson = new Gson();
                    List<?> args = gson.fromJson(argsString, ArrayList.class);

                    try {
                      Optional<String> response =
                          handleScriptFunction(job, funcCallId, functionName, args, argsString);
                      if (response.isPresent()) {
                        job.respond(funcCallId, response.get(), true);
                      }
                      if (scriptFunctionDebugOutptut) {
                        LOGGER.info(
                            "(debug) Script function `{}`: {} / {}  ->  {}",
                            functionName,
                            toJsonString(argsString),
                            args,
                            response.orElse("<no response>"));
                      }
                    } catch (IllegalArgumentException e) {
                      job.raiseException(funcCallId, "ValueError", e.getMessage());
                    } catch (IllegalStateException e) {
                      job.raiseException(funcCallId, "RuntimeError", e.getMessage());
                    } catch (Exception e) {
                      job.raiseException(funcCallId, "Exception", e.getMessage());
                    }
                  } else {
                    processMessage(jobCommand);
                  }
                }
              } catch (RuntimeException e) {
                job.logJobException(e);
              }
            }
          }
        } while (hasCommand && iterations < minescriptCommandsPerCycle);
      }
    }
  }
}
