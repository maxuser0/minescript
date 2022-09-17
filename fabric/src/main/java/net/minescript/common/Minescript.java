// SPDX-FileCopyrightText: © 2022 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import static net.minescript.common.CommandSyntax.parseCommand;
import static net.minescript.common.CommandSyntax.quoteCommand;
import static net.minescript.common.CommandSyntax.quoteString;

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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
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

  public static void init() {
    LOGGER.info("Starting Minescript on OS: {}", System.getProperty("os.name"));
    if (new File(MINESCRIPT_DIR).mkdir()) {
      LOGGER.info("Created minescript dir");
    }

    var undoDir = new File(Paths.get(MINESCRIPT_DIR, "undo").toString());
    if (undoDir.exists()) {
      int numDeletedFiles = 0;
      LOGGER.info("Deleting undo files from previous run...");
      for (var undoFile : undoDir.listFiles()) {
        if (undoFile.getName().endsWith(".txt")) {
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
      copyJarResourceToMinescriptDir("paste.py", FileOverwritePolicy.OVERWRITTE);
    }

    loadConfig();
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

  private static final Pattern CONFIG_LINE_RE =
      Pattern.compile("^([a-zA-Z0-9_]+) *= *\"?([^\"]*)\"?");

  private static final File configFile =
      new File(Paths.get(MINESCRIPT_DIR, "config.txt").toString());
  private static long lastConfigLoadTime = 0;

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

    try (var reader = new BufferedReader(new FileReader(configFile.getPath()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.strip();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }
        var match = CONFIG_LINE_RE.matcher(line);
        if (match.find()) {
          String name = match.group(1);
          String value = match.group(2);
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
            default:
              LOGGER.warn(
                  "Unrecognized config var: {} = \"{}\" (\"{}\")", name, value, pythonLocation);
          }
        } else {
          LOGGER.warn("config.txt: unable parse config line: {}", line);
        }
      }
    } catch (IOException e) {
      LOGGER.error("Exception loading config file: {}", e);
    }
  }

  // TODO(maxuser): replace with ImmutableList
  private static final String[] BUILTIN_COMMANDS =
      new String[] {
        "ls",
        "copy",
        "jobs",
        "suspend",
        "z", // alias for suspend
        "resume",
        "killjob",
        "undo",
        "minescript_copy_size_limit",
        "minescript_commands_per_cycle",
        "minescript_ticks_per_cycle",
        "minescript_incremental_command_suggestions",
        "minescript_script_function_debug_outptut",
        "minescript_log_chunk_load_events",
        "enable_minescript_on_chat_received_event"
      };

  private static List<String> getScriptCommandNamesWithBuiltins() {
    var names = getScriptCommandNames();
    for (String builtin : BUILTIN_COMMANDS) {
      names.add(builtin);
    }
    return names;
  }

  private static void logException(Exception e) {
    var sw = new StringWriter();
    var pw = new PrintWriter(sw);
    e.printStackTrace(pw);
    logUserError("{}: {}", e.getClass().getSimpleName(), e.getMessage());
    LOGGER.error("exception stack trace: {}", sw.toString());
  }

  private static List<String> getScriptCommandNames() {
    List<String> scriptNames = new ArrayList<>();
    String minescriptDir = Paths.get(System.getProperty("user.dir"), MINESCRIPT_DIR).toString();
    try {
      Files.list(new File(minescriptDir).toPath())
          .filter(
              path ->
                  !path.getFileName().toString().startsWith("minescript")
                      && path.toString().endsWith(".py"))
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

    void enqueueStdout(String text);

    void enqueueStderr(String messagePattern, Object... arguments);

    void logJobException(Exception e);
  }

  static class UndoableAction {
    private static String UNDO_DIR = Paths.get(MINESCRIPT_DIR, "undo").toString();

    private volatile int originalJobId; // ID of the job that this undoes.
    private String[] originalCommand;
    private final long startTimeMillis;
    private final Deque<String> commands = new ArrayDeque<>();
    private final Set<Position> blocks = new HashSet<>();
    private String commandsFilename;
    private int[] coords = new int[6]; // Reuse array to avoid lots of small object instantiations.
    private boolean undone = false;

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

    public UndoableAction(int originalJobId, String[] originalCommand) {
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
            blockStateToString(readBlockState(level, coords[0], coords[1], coords[2]));
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
              Optional<String> block = blockStateToString(readBlockState(level, x, y, z));
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

  interface Task {
    int run(String[] command, JobControl jobControl);

    default boolean handleResponse(long functionCallId, String returnValue, boolean finalReply) {
      return false;
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

    public Job(int jobId, String[] command, Task task, Consumer<Integer> doneCallback) {
      this.jobId = jobId;
      this.command = Arrays.copyOf(command, command.length);
      this.task = task;
      this.doneCallback = doneCallback;
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
      boolean result = task.handleResponse(functionCallId, returnValue, finalReply);
      if (functionCallId == 0 && "\"exit!\"".equals(returnValue)) {
        state = JobState.DONE;
      }
      return result;
    }

    @Override
    public void enqueueStdout(String text) {
      jobCommandQueue.add(text);
    }

    @Override
    public void enqueueStderr(String messagePattern, Object... arguments) {
      String logMessage = ParameterizedMessage.format(messagePattern, arguments);
      LOGGER.error("{}", logMessage);
      jobCommandQueue.add(formatAsJsonText(logMessage, "yellow"));
    }

    @Override
    public void logJobException(Exception e) {
      var sw = new StringWriter();
      var pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      logUserError("Exception in job \"{}\": {}", jobSummary(), e.getMessage());
      LOGGER.error("exception stack trace in job \"{}\": {}", jobSummary(), sw.toString());
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
      if (process == null) {
        return -4;
      }
      if (jobControl.state() == JobState.KILLED) {
        process.destroy();
        return -5;
      }
      try {
        return process.waitFor();
      } catch (InterruptedException e) {
        jobControl.logJobException(e);
        return -6;
      }
    }

    @Override
    public boolean handleResponse(long functionCallId, String returnValue, boolean finalReply) {
      if (process == null || !process.isAlive() || stdinWriter == null) {
        return false;
      }

      try {
        // TODO(maxuser): Escape strings in returnValue (or use JSON library).
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
        // TODO(maxuser): Log the exception.
        return false;
      }
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

    public void createSubprocess(String[] command) {
      var job = new Job(allocateJobId(), command, new SubprocessTask(), this::removeJob);
      var undo = new UndoableAction(job.jobId(), command);
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
          new Job(allocateJobId(), undo.derivativeCommand(), new UndoTask(undo), this::removeJob);
      jobMap.put(undoJob.jobId(), undoJob);
      undoJob.start();
    }

    private synchronized int allocateJobId() {
      if (jobMap.isEmpty()) {
        nextJobId = 1;
      }
      return nextJobId++;
    }

    private void removeJob(int jobId) {
      var undo = jobUndoMap.remove(jobId);
      if (undo != null) {
        undo.onOriginalJobDone();
      }
      jobMap.remove(jobId);
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
    STRING
  }

  private static boolean checkParamTypes(String[] command, ParamType... types) {
    if (command.length != 1 + types.length) {
      return false;
    }
    for (int i = 0; i < types.length; i++) {
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

  // TODO(maxuser): Do proper quoting of params with spaces.
  private static String getParamsAsString(String[] command) {
    return String.join(" ", Arrays.copyOfRange(command, 1, command.length));
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

  private static BlockState readBlockState(World level, int x, int y, int z) {
    int chunkX = worldCoordToChunkCoord(x);
    int chunkZ = worldCoordToChunkCoord(z);
    var chunk = level.getChunk(chunkX, chunkZ);
    var chunkPos = chunk.getPos();
    return chunk.getBlockState(chunkPos.getBlockPos(x, y, z));
  }

  // BlockState#toString() returns a string formatted as:
  // "Block{minecraft:acacia_button}[face=floor,facing=west,powered=false]"
  //
  // BLOCK_STATE_RE helps transform this to:
  // "minecraft:acacia_button[face=floor,facing=west,powered=false]"
  private static Pattern BLOCK_STATE_RE = Pattern.compile("^Block\\{([^}]*)\\}(\\[.*\\])?$");

  private static int copySizeLimit = 200;

  private static Optional<String> blockStateToString(BlockState blockState) {
    var match = BLOCK_STATE_RE.matcher(blockState.toString());
    if (!match.find()) {
      return Optional.empty();
    }
    String blockType = match.group(1);
    String blockAttrs = match.group(2) == null ? "" : match.group(2);
    return Optional.of(blockType + blockAttrs);
  }

  private static void copyBlocks(
      int x0, int y0, int z0, int x1, int y1, int z1, Optional<String> label) {
    var minecraft = MinecraftClient.getInstance();
    var player = minecraft.player;
    if (player == null) {
      logUserError("Unable to copy blocks because player is null.");
      return;
    }

    int playerX = (int) player.getX();
    int playerY = (int) player.getY();
    int playerZ = (int) player.getZ();

    if (Math.abs(x0 - playerX) > copySizeLimit
        || Math.abs(y0 - playerY) > copySizeLimit
        || Math.abs(z0 - playerZ) > copySizeLimit
        || Math.abs(x1 - playerX) > copySizeLimit
        || Math.abs(y1 - playerY) > copySizeLimit
        || Math.abs(z1 - playerZ) > copySizeLimit) {
      logUserError("Player is more than {} blocks from `copy` coordinate.", copySizeLimit);
      return;
    }

    int xMin = Math.min(x0, x1);
    int yMin = Math.max(Math.min(y0, y1), -64); // TODO(maxuser): Use an API for min build height.
    int zMin = Math.min(z0, z1);

    int xMax = Math.max(x0, x1);
    int yMax = Math.min(Math.max(y0, y1), 320); // TODO(maxuser): Use an API for max build height.
    int zMax = Math.max(z0, z1);

    World level = player.getEntityWorld();

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
            // TODO(maxuser): Need to check chunkPos.get{Min,Max}Block{X,Z}()?
            // TODO(maxuser): Listen to ChunkEvent.Load and .Unload events to determine if the chunk
            // we're trying to read here is loaded. If it's not, load it and try again later.
            BlockState blockState = readBlockState(level, x, y, z);
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

  private static void runMinescriptCommand(String commandLine) {
    if (!checkMinescriptDir()) {
      return;
    }

    // Check if config needs to be reloaded.
    loadConfig();

    String[] command = parseCommand(commandLine);
    command = substituteMinecraftVars(command);

    if (command[0].equals("jobs")) {
      if (checkParamTypes(command)) {
        listJobs();
      } else {
        logUserError("Expected no params, instead got `{}`", getParamsAsString(command));
      }
      return;
    }

    if (command[0].equals("suspend") || command[0].equals("z")) {
      if (checkParamTypes(command)) {
        suspendJob(OptionalInt.empty());
      } else if (checkParamTypes(command, ParamType.INT)) {
        suspendJob(OptionalInt.of(Integer.valueOf(command[1])));
      } else {
        logUserError(
            "Expected no params or 1 param of type integer, instead got `{}`",
            getParamsAsString(command));
      }
      return;
    }

    if (command[0].equals("resume")) {
      if (checkParamTypes(command)) {
        resumeJob(OptionalInt.empty());
      } else if (checkParamTypes(command, ParamType.INT)) {
        resumeJob(OptionalInt.of(Integer.valueOf(command[1])));
      } else {
        logUserError(
            "Expected no params or 1 param of type integer, instead got `{}`",
            getParamsAsString(command));
      }
      return;
    }

    if (command[0].equals("killjob")) {
      if (checkParamTypes(command, ParamType.INT)) {
        killJob(Integer.valueOf(command[1]));
      } else {
        logUserError(
            "Expected 1 param of type integer, instead got `{}`", getParamsAsString(command));
      }
      return;
    }

    if (command[0].equals("undo")) {
      if (checkParamTypes(command)) {
        jobs.startUndo();
      } else {
        logUserError(
            "Expected no params or 1 param of type integer, instead got `{}`",
            getParamsAsString(command));
      }
      return;
    }

    if (command[0].equals("copy")) {
      if (checkParamTypes(
          command,
          ParamType.INT,
          ParamType.INT,
          ParamType.INT,
          ParamType.INT,
          ParamType.INT,
          ParamType.INT)) {
        int x0 = Integer.valueOf(command[1]);
        int y0 = Integer.valueOf(command[2]);
        int z0 = Integer.valueOf(command[3]);
        int x1 = Integer.valueOf(command[4]);
        int y1 = Integer.valueOf(command[5]);
        int z1 = Integer.valueOf(command[6]);
        copyBlocks(x0, y0, z0, x1, y1, z1, Optional.empty());
      } else if (checkParamTypes(
          command,
          ParamType.INT,
          ParamType.INT,
          ParamType.INT,
          ParamType.INT,
          ParamType.INT,
          ParamType.INT,
          ParamType.STRING)) {
        int x0 = Integer.valueOf(command[1]);
        int y0 = Integer.valueOf(command[2]);
        int z0 = Integer.valueOf(command[3]);
        int x1 = Integer.valueOf(command[4]);
        int y1 = Integer.valueOf(command[5]);
        int z1 = Integer.valueOf(command[6]);
        copyBlocks(x0, y0, z0, x1, y1, z1, Optional.of(command[7]));
      } else {
        logUserError(
            "Expected 6 params of type integer (optional 7th for label), instead got `{}`",
            getParamsAsString(command));
      }
      return;
    }

    if (command[0].equals("minescript_commands_per_cycle")) {
      if (checkParamTypes(command)) {
        logUserInfo("Minescript executing {} command(s) per cycle.", minescriptCommandsPerCycle);
      } else if (checkParamTypes(command, ParamType.INT)) {
        int numCommands = Integer.valueOf(command[1]);
        if (numCommands < 1) numCommands = 1;
        minescriptCommandsPerCycle = numCommands;
        logUserInfo("Minescript execution set to {} command(s) per cycle.", numCommands);
      } else {
        logUserError(
            "Expected 1 param of type integer, instead got `{}`", getParamsAsString(command));
      }
      return;
    }

    if (command[0].equals("minescript_ticks_per_cycle")) {
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
      return;
    }

    if (command[0].equals("minescript_copy_size_limit")) {
      if (checkParamTypes(command)) {
        logUserInfo("Minescript copy size limit is {} blocks.", copySizeLimit);
      } else if (checkParamTypes(command, ParamType.INT)) {
        int limit = Integer.valueOf(command[1]);
        if (limit < 1) limit = 1;
        copySizeLimit = limit;
        logUserInfo("Minescript copy size limit set to {} blocks.", limit);
      } else {
        logUserError(
            "Expected 1 param of type integer, instead got `{}`", getParamsAsString(command));
      }
      return;
    }

    if (command[0].equals("minescript_incremental_command_suggestions")) {
      if (checkParamTypes(command, ParamType.BOOL)) {
        boolean value = Boolean.valueOf(command[1]);
        incrementalCommandSuggestions = value;
        logUserInfo("Minescript incremental command suggestions set to {}", value);
      } else {
        logUserError(
            "Expected 1 param of type boolean, instead got `{}`", getParamsAsString(command));
      }
      return;
    }

    if (command[0].equals("minescript_script_function_debug_outptut")) {
      if (checkParamTypes(command, ParamType.BOOL)) {
        boolean value = Boolean.valueOf(command[1]);
        scriptFunctionDebugOutptut = value;
        logUserInfo("Minescript script function debug output set to {}", value);
      } else {
        logUserError(
            "Expected 1 param of type boolean, instead got `{}`", getParamsAsString(command));
      }
      return;
    }

    if (command[0].equals("minescript_log_chunk_load_events")) {
      if (checkParamTypes(command, ParamType.BOOL)) {
        boolean value = Boolean.valueOf(command[1]);
        logChunkLoadEvents = value;
        logUserInfo("Minescript logging of chunk load events set to {}", value);
      } else {
        logUserError(
            "Expected 1 param of type boolean, instead got `{}`", getParamsAsString(command));
      }
      return;
    }

    if (command[0].equals("enable_minescript_on_chat_received_event")) {
      if (checkParamTypes(command, ParamType.BOOL)) {
        boolean enable = command[1].equals("true");
        enableMinescriptOnChatReceivedEvent = enable;
        logUserInfo(
            "Minescript execution on client chat events {}.{}",
            (enable ? "enabled" : "disabled"),
            (enable
                ? " e.g. add command to command block: [execute as Player run tell Player \\help]"
                : ""));
      } else {
        logUserError(
            "Expected 1 param of type boolean, instead got `{}`", getParamsAsString(command));
      }
      return;
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
      return;
    }

    jobs.createSubprocess(command);
  }

  private static int minescriptTicksPerCycle = 3;
  private static int minescriptCommandsPerCycle = 15;

  private static int renderTickEventCounter = 0;
  private static int playerTickEventCounter = 0;

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
          if (key == ENTER_KEY && customNickname != null && !value.startsWith("/")) {
            // This branch is unnecessary on Forge because it supports ClientChatEvent.
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
            chatEditBox.setCursor(value.length());
          }
          cancel = true;
        } else if (key == DOWN_ARROW_KEY) {
          Optional<String> nextCommand = minescriptCommandHistory.moveForwardAndGet();
          if (nextCommand.isPresent()) {
            value = nextCommand.get();
            chatEditBox.setText(value);
            chatEditBox.setCursor(value.length());
          }
          cancel = true;
        } else if (key == ENTER_KEY) {
          // This branch is unnecessary on Forge because it supports ClientChatEvent.
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
        if (value.length() > 1) {
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
      LOGGER.info(
          "Forwarding chat message to listener {}: {}", listener.getKey(), quoteString(text));
      if (!listener.getValue().respond(quoteString(text), false)) {
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
    } else if (customNickname != null && !message.startsWith("/")) {
      String tellrawCommand = "/tellraw @a " + String.format(customNickname, message);
      systemCommandQueue.add(tellrawCommand);
      var minecraft = MinecraftClient.getInstance();
      var chatHud = minecraft.inGameHud.getChatHud();
      // TODO(maxuser): There appears to be a bug truncating the chat HUD command history. It might
      // be that onClientChat(...) can get called on a different thread from what other callers are
      // expecting, thereby corrupting the history. Verify whether this gets called on the same
      // thread as onPlayerTick() or other events.
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

  private static Map<Integer, ScriptFunctionCall> clientChatReceivedEventListeners =
      new ConcurrentHashMap<>();

  public static class ChunkLoadEventListener {
    // Map packed chunk (x, z) to boolean: true if chunk is loaded, false otherwise.
    private final Map<Long, Boolean> chunksToLoad = new ConcurrentHashMap<>();

    // World with chunks to listen for. Store hash rather than reference to avoid memory leak.
    private final int levelHashCode;

    private final ScriptFunctionCall scriptFunction;
    private int numUnloadedChunks = 0;
    private boolean suspended = false;

    public ChunkLoadEventListener(
        int x1, int z1, int x2, int z2, ScriptFunctionCall scriptFunction) {
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
      this.scriptFunction = scriptFunction;
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
      scriptFunction.respond("true", true);
    }
  }

  // Integer value represents the ID of the job that spawned this listener.
  private static Map<ChunkLoadEventListener, Integer> chunkLoadEventListeners =
      new ConcurrentHashMap<ChunkLoadEventListener, Integer>();

  private static String customNickname = null;

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

  private static String itemStackToJsonString(ItemStack itemStack) {
    if (itemStack.getCount() == 0) {
      return "null";
    } else {
      var nbt = itemStack.getNbt();
      return String.format(
          "{\"item\": \"%s\", \"count\": %d, \"nbt\": %s}",
          itemStack.getItem(),
          itemStack.getCount(),
          nbt == null
              ? "null"
              : '"'
                  + nbt.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
                  + '"');
    }
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

  private static String doPlayerAction(
      String functionName,
      long funcCallId,
      KeyBinding keyBinding,
      List<?> args,
      String argsString,
      Job job) {
    final String response;
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
      response = "true";
    } else {
      logUserError(
          "Error: `{}` expected 1 boolean param (true or false) but got: {}",
          functionName,
          argsString);
      response = "false";
    }
    job.respond(funcCallId, response, true);
    return response;
  }

  public static void onPlayerTick() {
    if (++playerTickEventCounter % minescriptTicksPerCycle == 0) {
      var minecraft = MinecraftClient.getInstance();
      var player = minecraft.player;
      if (player != null && (!systemCommandQueue.isEmpty() || !jobs.getMap().isEmpty())) {
        World level = player.getEntityWorld();
        for (int commandCount = 0; commandCount < minescriptCommandsPerCycle; ++commandCount) {
          String command = systemCommandQueue.poll();
          if (command != null) {
            processMessage(command);
          }
          for (var job : jobs.getMap().values()) {
            if (job.state() == JobState.RUNNING) {
              String jobCommand = job.commandQueue().poll();
              if (jobCommand != null) {
                jobs.getUndoForJob(job).ifPresent(u -> u.processCommandToUndo(level, jobCommand));
                if (jobCommand.startsWith("?") && jobCommand.length() > 1) {
                  String[] functionCall = jobCommand.substring(1).split("\\s+", 3);
                  long funcCallId = Long.valueOf(functionCall[0]);
                  String functionName = functionCall[1];
                  String argsString = functionCall.length == 3 ? functionCall[2] : "";
                  var gson = new Gson();
                  List<?> args = gson.fromJson(argsString, ArrayList.class);

                  // TODO(maxuser): This is poor form for a number of reasons. First, some branches
                  // (namely async-only functions) have no immediate response, but we have to assign
                  // a value to `response` anyway because it's `final`.  This is arguably better
                  // than removing `final` from the variable because that would allow multiple
                  // assignments.  Second, the long if-else-if chain is cumbersome.
                  //
                  // Refactor this so that each script function becomes an instance of a
                  // ScriptFunction class with a call(...) method.

                  final String response;

                  // TODO(maxuser): Support raising exceptions from script functions, e.g.
                  // {"fcid": ..., "exception": "error message...", "conn": "close"}

                  if (functionName.equals("player_position")) {
                    if (args.isEmpty()) {
                      response =
                          String.format(
                              "[%f, %f, %f]", player.getX(), player.getY(), player.getZ());
                      job.respond(funcCallId, response, true);
                    } else {
                      logUserError(
                          "Error: `{}` expected no params but got: {}", functionName, argsString);
                      response = "null";
                      job.respond(funcCallId, response, true);
                    }
                  } else if (functionName.equals("getblock")) {
                    if (args.size() == 3
                        && args.get(0) instanceof Number
                        && args.get(1) instanceof Number
                        && args.get(2) instanceof Number) {
                      Number arg0 = (Number) args.get(0);
                      Number arg1 = (Number) args.get(1);
                      Number arg2 = (Number) args.get(2);
                      Optional<String> block =
                          blockStateToString(
                              readBlockState(
                                  level, arg0.intValue(), arg1.intValue(), arg2.intValue()));
                      response = block.map(str -> quoteString(str, true)).orElse("null");
                      job.respond(funcCallId, response, true);
                    } else {
                      logUserError(
                          "Error: `{}` expected 3 params (x, y, z) but got: {}",
                          functionName,
                          argsString);
                      response = "null";
                      job.respond(funcCallId, response, true);
                    }
                  } else if (functionName.equals("register_chat_message_listener")) {
                    if (!args.isEmpty()) {
                      logUserError(
                          "Error: `{}` expected no params but got: {}", functionName, argsString);
                    } else if (clientChatReceivedEventListeners.containsKey(job.jobId())) {
                      logUserError(
                          "Error: `{}` failed: listener already registered for job: {}",
                          functionName,
                          job.jobSummary());
                    } else {
                      clientChatReceivedEventListeners.put(
                          job.jobId(), new ScriptFunctionCall(job, funcCallId));
                    }
                    response = "<unused>";
                  } else if (functionName.equals("unregister_chat_message_listener")) {
                    if (!args.isEmpty()) {
                      logUserError(
                          "Error: `{}` expected no params but got: {}", functionName, argsString);
                      response = "false";
                    } else if (!clientChatReceivedEventListeners.containsKey(job.jobId())) {
                      logUserError(
                          "Error: `{}` has no listeners to unregister for job: {}",
                          functionName,
                          job.jobSummary());
                      response = "false";
                    } else {
                      clientChatReceivedEventListeners.remove(job.jobId());
                      response = "true";
                    }
                    job.respond(funcCallId, response, true);
                  } else if (functionName.equals("await_loaded_region")) {
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
                              new ScriptFunctionCall(job, funcCallId));
                      listener.updateChunkStatuses();
                      if (listener.isFinished()) {
                        listener.onFinished();
                      } else {
                        chunkLoadEventListeners.put(listener, job.jobId());
                      }
                      response = "<unused>";
                    } else {
                      // TODO(maxuser): Support raising exceptions through script functions, e.g.
                      // {"fcid": ..., "exception": "error message...", "conn": "close"}
                      logUserError(
                          "Error: `{}` expected 4 number params (x1, z1, x2, z2) but got: {}",
                          functionName,
                          argsString);
                      response = "false";
                      job.respond(funcCallId, response, true);
                    }
                  } else if (functionName.equals("set_nickname")) {
                    if (args.isEmpty()) {
                      logUserInfo(
                          "Chat nickname reset to default; was {}",
                          customNickname == null ? "default already" : quoteString(customNickname));
                      customNickname = null;
                      response = "true";
                    } else if (args.size() == 1) {
                      String arg = args.get(0).toString();
                      if (arg.contains("%s")) {
                        logUserInfo("Chat nickname set to {}.", quoteString(arg, true));
                        customNickname = arg;
                        response = "true";
                      } else {
                        logUserError(
                            "Error: `{}` expects nickname to contain %s as a placeholder for"
                                + " message text.",
                            functionName);
                        response = "false";
                      }
                    } else {
                      logUserError(
                          "Error: `{}` expected 0 or 1 param but got: {}",
                          functionName,
                          argsString);
                      response = "false";
                    }
                    job.respond(funcCallId, response, true);
                  } else if (functionName.equals("player_hand_items")) {
                    if (args.isEmpty()) {
                      var result = new StringBuilder("[");
                      for (var itemStack : player.getHandItems()) {
                        if (result.length() > 1) {
                          result.append(",");
                        }
                        result.append(itemStackToJsonString(itemStack));
                      }
                      result.append("]");
                      response = result.toString();
                    } else {
                      logUserError(
                          "Error: `{}` expected no params but got: {}", functionName, argsString);
                      response = "null";
                    }
                    job.respond(funcCallId, response, true);
                  } else if (functionName.equals("player_inventory")) {
                    if (args.isEmpty()) {
                      var inventory = player.getInventory();
                      var result = new StringBuilder("[");
                      for (int i = 0; i < inventory.size(); i++) {
                        var itemStack = inventory.getStack(i);
                        if (itemStack.getCount() > 0) {
                          if (result.length() > 1) {
                            result.append(",");
                          }
                          result.append(itemStackToJsonString(itemStack));
                        }
                      }
                      result.append("]");
                      response = result.toString();
                    } else {
                      logUserError(
                          "Error: `{}` expected no params but got: {}", functionName, argsString);
                      response = "null";
                    }
                    job.respond(funcCallId, response, true);
                  } else if (functionName.equals("player_press_forward")) {
                    var key = minecraft.options.forwardKey;
                    response = doPlayerAction(functionName, funcCallId, key, args, argsString, job);
                  } else if (functionName.equals("player_press_backward")) {
                    var key = minecraft.options.backKey;
                    response = doPlayerAction(functionName, funcCallId, key, args, argsString, job);
                  } else if (functionName.equals("player_press_left")) {
                    var key = minecraft.options.leftKey;
                    response = doPlayerAction(functionName, funcCallId, key, args, argsString, job);
                  } else if (functionName.equals("player_press_right")) {
                    var key = minecraft.options.rightKey;
                    response = doPlayerAction(functionName, funcCallId, key, args, argsString, job);
                  } else if (functionName.equals("player_press_jump")) {
                    var key = minecraft.options.jumpKey;
                    response = doPlayerAction(functionName, funcCallId, key, args, argsString, job);
                  } else if (functionName.equals("player_press_sprint")) {
                    var key = minecraft.options.sprintKey;
                    response = doPlayerAction(functionName, funcCallId, key, args, argsString, job);
                  } else if (functionName.equals("player_press_sneak")) {
                    var key = minecraft.options.sneakKey;
                    response = doPlayerAction(functionName, funcCallId, key, args, argsString, job);
                  } else if (functionName.equals("player_press_pick_item")) {
                    var key = minecraft.options.pickItemKey;
                    response = doPlayerAction(functionName, funcCallId, key, args, argsString, job);
                  } else if (functionName.equals("player_press_use")) {
                    var key = minecraft.options.useKey;
                    response = doPlayerAction(functionName, funcCallId, key, args, argsString, job);
                  } else if (functionName.equals("player_press_attack")) {
                    var key = minecraft.options.attackKey;
                    response = doPlayerAction(functionName, funcCallId, key, args, argsString, job);
                  } else if (functionName.equals("player_press_swap_hands")) {
                    var key = minecraft.options.swapHandsKey;
                    response = doPlayerAction(functionName, funcCallId, key, args, argsString, job);
                  } else if (functionName.equals("player_press_drop")) {
                    var key = minecraft.options.dropKey;
                    response = doPlayerAction(functionName, funcCallId, key, args, argsString, job);
                  } else if (functionName.equals("player_orientation")) {
                    if (args.isEmpty()) {
                      response = String.format("[%f, %f]", player.getYaw(), player.getPitch());
                    } else {
                      logUserError(
                          "Error: `{}` expected no params but got: {}", functionName, argsString);
                      response = "null";
                    }
                    job.respond(funcCallId, response, true);
                  } else if (functionName.equals("player_set_orientation")) {
                    if (args.size() == 2
                        && args.get(0) instanceof Number
                        && args.get(1) instanceof Number) {
                      Number yaw = (Number) args.get(0);
                      Number pitch = (Number) args.get(1);
                      player.setYaw(yaw.floatValue() % 360.0f);
                      player.setPitch(pitch.floatValue() % 360.0f);
                      response = "true";
                    } else {
                      logUserError(
                          "Error: `{}` expected 2 number params but got: {}",
                          functionName,
                          argsString);
                      response = "false";
                    }
                    job.respond(funcCallId, response, true);
                  } else if (functionName.equals("screenshot")) {
                    final Optional<String> filename;
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
                    job.respond(funcCallId, response, true);
                  } else if (functionName.equals("flush")) {
                    if (args.isEmpty()) {
                      response = "true";
                      job.respond(funcCallId, response, true);
                    } else {
                      logUserError(
                          "Error: `{}` expected no params but got: {}", functionName, argsString);
                      response = "false";
                    }
                  } else if (funcCallId == 0 && functionName.equals("exit!")) {
                    response = "\"exit!\"";
                    job.respond(0, response, true);
                  } else {
                    logUserError(
                        "Error: unknown function `{}` called from job: {}",
                        functionName,
                        job.jobSummary());
                    response = "false";
                    job.respond(funcCallId, response, true);
                  }
                  if (scriptFunctionDebugOutptut) {
                    LOGGER.info(
                        "(debug) Script function `{}`: {} / {}  ->  {}",
                        functionName,
                        quoteString(argsString, true),
                        args,
                        response);
                  }
                } else {
                  processMessage(jobCommand);
                }
              }
            }
          }
        }
      }
    }
  }
}
