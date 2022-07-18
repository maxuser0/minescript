package net.minescript.minescriptmod;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
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
import org.apache.logging.log4j.message.ParameterizedMessage;

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
    // dispatch IMC to another mod
  }

  private void processIMC(final InterModProcessEvent event) {
    // receive and process InterModComms from other mods
  }

  // You can use SubscribeEvent and let the Event Bus discover methods to call
  @SubscribeEvent
  public void onServerStarting(ServerStartingEvent event) {
    // do something when the server starts
  }

  @SubscribeEvent
  public void onCommandEvent(CommandEvent event) {}

  // TODO(maxuser): replace with ImmutableList
  private static final String[] BUILTIN_COMMANDS =
      new String[] {
        "ls",
        "copy",
        "record",
        "jobs",
        "suspend",
        "resume",
        "killjob",
        "minescript_commands_per_cycle",
        "minescript_ticks_per_cycle",
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
    logUserError("Exception: {}", e.getMessage());
    LOGGER.error("(minescript) exception stack trace: {}", sw.toString());
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

  static String tellrawFormat(String text, String color) {
    // Treat as plain text to write to the chat.
    return "/tellraw @s {\"text\":\""
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

    void enqueueStdout(String text);

    void enqueueStderr(String messagePattern, Object... arguments);

    void logJobException(Exception e);
  }

  interface Task {
    int run(String[] command, JobControl jobControl);
  }

  static class Job implements JobControl {
    private static AtomicInteger nextJobId = new AtomicInteger(1);
    private final int jobId;
    private final String[] command;
    private final Task task;
    private Thread thread;
    private volatile JobState state = JobState.NOT_STARTED;
    private Consumer<Integer> doneCallback;
    private Queue<String> jobCommandQueue = new ConcurrentLinkedQueue<String>();
    private Lock lock = new ReentrantLock(true); // true indicates a fair lock to avoid starvation

    public Job(String[] command, Task task, Consumer<Integer> doneCallback) {
      this.jobId = nextJobId.getAndIncrement();
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
    public void enqueueStdout(String text) {
      if (text.matches("^/[a-zA-Z].*")) {
        jobCommandQueue.add(text);
      } else if (text.startsWith("#")) {
        jobCommandQueue.add(text.substring(1).stripLeading());
      } else {
        // Treat as plain text to write to the chat.
        jobCommandQueue.add(tellrawFormat(text, "white"));
      }
    }

    @Override
    public void enqueueStderr(String messagePattern, Object... arguments) {
      String logMessage = ParameterizedMessage.format(messagePattern, arguments);
      LOGGER.error("(minescript) {}", logMessage);
      jobCommandQueue.add(tellrawFormat(logMessage, "red"));
    }

    @Override
    public void logJobException(Exception e) {
      var sw = new StringWriter();
      var pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      logUserError("Exception in job \"{}\": {}", jobSummary(), e.getMessage());
      LOGGER.error(
          "(minescript) exception stack trace in job \"{}\": {}", jobSummary(), sw.toString());
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

    public Queue<String> commandQueue() {
      return jobCommandQueue;
    }

    private void runOnJobThread() {
      if (state == JobState.NOT_STARTED) {
        state = JobState.RUNNING;
      }
      try {
        int exitCode = task.run(command, this);
        final int millisToSleep = 1000;
        while (state != JobState.KILLED && !jobCommandQueue.isEmpty()) {
          try {
            Thread.sleep(millisToSleep);
          } catch (InterruptedException e) {
            logJobException(e);
          }
        }
        if (exitCode != 0) {
          logUserError("Command exit code: {}", exitCode);
        }
      } finally {
        doneCallback.accept(jobId);
      }
    }

    public int jobId() {
      return jobId;
    }

    String jobSummary() {
      // Same as toString(), but omits state.
      String displayCommand = String.join(" ", command);
      if (displayCommand.length() > 61) {
        displayCommand = displayCommand.substring(0, 61) + "...";
      }
      return String.format("[%d] %s", jobId, displayCommand);
    }

    @Override
    public String toString() {
      String displayCommand = String.join(" ", command);
      if (displayCommand.length() > 61) {
        displayCommand = displayCommand.substring(0, 61) + "...";
      }
      return String.format("[%d] %s:  %s", jobId, state, displayCommand);
    }
  }

  static class SubprocessTask implements Task {
    @Override
    public int run(String[] command, JobControl jobControl) {
      // TODO(maxuser): Support non-Python executables in general.
      String scriptExtension = ".py";
      String scriptName = MINESCRIPT_DIR + "/" + command[0] + scriptExtension;

      String pythonInterpreterPath = findFirstFile("/usr/bin/python3", "/usr/local/bin/python3");
      if (pythonInterpreterPath == null) {
        jobControl.enqueueStderr("Cannot find Python3 interpreter at any of these locations:");
        jobControl.enqueueStderr("  /usr/bin/python3");
        jobControl.enqueueStderr("  /usr/local/bin/python3");
        jobControl.enqueueStderr("See: https://www.python.org/downloads/");
        return -1;
      }

      String[] executableCommand = new String[command.length + 2];
      executableCommand[0] = pythonInterpreterPath;
      executableCommand[1] = "-u"; // `python3 -u` for unbuffered stdout and stderr.
      executableCommand[2] = scriptName;
      for (int i = 1; i < command.length; i++) {
        executableCommand[i + 2] = command[i];
      }

      final Process process;
      try {
        process = Runtime.getRuntime().exec(executableCommand);
      } catch (IOException e) {
        jobControl.logJobException(e);
        return -2;
      }

      // TODO(maxuser): Process stdout and stderr on different threads so they can be processed
      // concurrently.
      try (var stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
          var stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
        String line;
        while ((line = stdoutReader.readLine()) != null) {
          if (jobControl.state() == JobState.KILLED) {
            process.destroy();
            break;
          }
          jobControl.yield();
          jobControl.enqueueStdout(line);
        }
        while ((line = stderrReader.readLine()) != null) {
          if (jobControl.state() == JobState.KILLED) {
            process.destroy();
            break;
          }
          jobControl.yield();
          jobControl.enqueueStderr(line);
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
  }

  static class RecordingTask implements Task {
    @Override
    public int run(String[] command, JobControl jobControl) {
      // TODO(maxuser): implement; reading world blocks needs to happen on the main thread, but this
      // method is run on a job thread. Maybe distinguish between single-threaded tasks like this
      // one and multi-threaded tasks like SubprocessTask.
      return 1;
    }
  }

  static class JobManager {
    private final Map<Integer, Job> map = new ConcurrentHashMap<Integer, Job>();
    private RecordingTask recordingTask;

    public void createSubprocess(String[] command) {
      var job = new Job(command, new SubprocessTask(), jobId -> map.remove(jobId));
      map.put(job.jobId(), job);
      job.start();
    }

    public void startRecording(String[] command) {
      if (recordingTask != null) {
        logUserError("Recording is already in progress.");
        return;
      }
      recordingTask = new RecordingTask();
      var job =
          new Job(
              command,
              recordingTask,
              jobId -> {
                map.remove(jobId);
                recordingTask = null;
              });
      map.put(job.jobId(), job);
      job.start();
    }

    public Map<Integer, Job> getMap() {
      return map;
    }
  }

  private static JobManager jobs = new JobManager();

  private static Queue<String> systemCommandQueue = new ConcurrentLinkedQueue<String>();

  private static boolean checkMinescriptDir() {
    String minescriptDir = System.getProperty("user.dir") + "/" + MINESCRIPT_DIR;
    if (!Files.isDirectory(Paths.get(minescriptDir))) {
      logUserError("Minescript folder is missing. Please create it at: {}", minescriptDir);
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
    LOGGER.info("(minescript) {}", logMessage);
    systemCommandQueue.add(tellrawFormat(logMessage, "yellow"));
  }

  public static void logUserError(String messagePattern, Object... arguments) {
    String logMessage = ParameterizedMessage.format(messagePattern, arguments);
    LOGGER.error("(minescript) {}", logMessage);
    systemCommandQueue.add(tellrawFormat(logMessage, "red"));
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
      var job = jobs.getMap().get(jobId);
      if (job == null) {
        logUserError("No job with ID {}. Use \\jobs to list jobs.", jobId);
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
      var job = jobs.getMap().get(jobId);
      if (job == null) {
        logUserError("No job with ID {}. Use \\jobs to list jobs.", jobId);
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
    var job = jobs.getMap().get(jobId);
    if (job == null) {
      logUserError("No job with ID {}. Use \\jobs to list jobs.", jobId);
      return;
    }
    job.kill();
    logUserInfo("Removed job: {}", job.jobSummary());
  }

  // BlockState#toString() returns a string formatted as:
  // "Block{minecraft:acacia_button}[face=floor,facing=west,powered=false]"
  //
  // BLOCK_STATE_RE helps transform this to:
  // "minecraft:acacia_button[face=floor,facing=west,powered=false]"
  private static Pattern BLOCK_STATE_RE = Pattern.compile("^Block\\{([^}]*)\\}(\\[.*\\])?$");

  private static void copyBlocks(int x0, int y0, int z0, int x1, int y1, int z1) {
    var minecraft = Minecraft.getInstance();
    var player = minecraft.player;
    if (player == null) {
      logUserError("Unable to copy blocks because player is null.");
      return;
    }

    int playerX = (int) player.getX();
    int playerY = (int) player.getY();
    int playerZ = (int) player.getZ();

    if (Math.abs(x0 - playerX) > 200
        || Math.abs(y0 - playerY) > 200
        || Math.abs(z0 - playerZ) > 200
        || Math.abs(x1 - playerX) > 200
        || Math.abs(y1 - playerY) > 200
        || Math.abs(z1 - playerZ) > 200) {
      logUserError("Player is more than 200 blocks from `copy` coordinate.");
      return;
    }

    int xMin = Math.min(x0, x1);
    int yMin = Math.max(Math.min(y0, y1), -64);
    int zMin = Math.min(z0, z1);

    int xMax = Math.max(x0, x1);
    int yMax = Math.min(Math.max(y0, y1), 320);
    int zMax = Math.max(z0, z1);

    var level = player.getCommandSenderWorld();

    try (var writer = new PrintWriter(new FileWriter(MINESCRIPT_DIR + "/paste.py"))) {
      writer.print("# Generated by Minescript from this `copy` command:\n");
      writer.printf("# \\copy %d %d %d %d %d %d\n", x0, y0, z0, x1, y1, z1);
      writer.print("\n");
      writer.print("import math\n");
      writer.print("import sys\n");
      writer.print("\n");
      writer.print("x_start = int(sys.argv[1])\n");
      writer.print("y_start = int(sys.argv[2])\n");
      writer.print("z_start = int(sys.argv[3])\n");
      writer.print("fill_block = sys.argv[4] if len(sys.argv) > 4 else None\n");
      writer.print("\n");
      writer.printf("x_end = x_start + %d\n", x1 - x0);
      writer.printf("y_end = y_start + %d\n", y1 - y0);
      writer.printf("z_end = z_start + %d\n", z1 - z0);
      writer.print("\n");
      writer.print("x_min = min(x_start, x_end)\n");
      writer.print("y_min = min(y_start, y_end)\n");
      writer.print("z_min = min(z_start, z_end)\n");
      writer.print("\n");
      writer.print("x_max = max(x_start, x_end)\n");
      writer.print("y_max = max(y_start, y_end)\n");
      writer.print("z_max = max(z_start, z_end)\n");
      writer.print("\n");
      writer.print("delta_x = x_max - x_min\n");
      writer.print("delta_y = y_max - y_min\n");
      writer.print("delta_z = z_max - z_min\n");
      writer.print("\n");
      writer.print("if fill_block:\n");
      writer.print("  for chunk_x in range(math.ceil(delta_x / 32)):\n");
      writer.print("    fill_x_min = x_min + chunk_x * 32\n");
      writer.print("    fill_x_max = x_min + min(chunk_x * 32 + 31, delta_x)\n");
      writer.print("    for chunk_y in range(math.ceil(delta_y / 32)):\n");
      writer.print("      fill_y_min = y_min + chunk_y * 32\n");
      writer.print("      fill_y_max = y_min + min(chunk_y * 32 + 31, delta_y)\n");
      writer.print("      for chunk_z in range(math.ceil(delta_z / 32)):\n");
      writer.print("        fill_z_min = z_min + chunk_z * 32\n");
      writer.print("        fill_z_max = z_min + min(chunk_z * 32 + 31, delta_z)\n");
      writer.print(
          "        print(f'/fill {fill_x_min} {fill_y_min} {fill_z_min} {fill_x_max} {fill_y_max}"
              + " {fill_z_max} {fill_block}')\n");
      writer.print("\n");

      int numBlocks = 0;

      for (int x = xMin; x <= xMax; ++x) {
        for (int y = yMin; y <= yMax; ++y) {
          for (int z = zMin; z <= zMax; ++z) {
            // TODO(maxuser): Need to check chunkPos.get{Min,Max}Block{X,Z}()?
            // TODO(maxuser): Listen to ChunkEvent.Load and .Unload events to determine if the chunk
            // we're trying to read here is loaded. If it's not, load it and try again later.
            int chunkX = (x >= 0) ? (x / 16) : (((x + 1) / 16) - 1);
            int chunkZ = (z >= 0) ? (z / 16) : (((z + 1) / 16) - 1);
            var chunk = level.getChunk(chunkX, chunkZ);
            var chunkPos = chunk.getPos();
            var chunkWorldPos = chunkPos.getWorldPosition();
            BlockState blockState = chunk.getBlockState(chunkPos.getBlockAt(x, y, z));
            if (!blockState.isAir()) {
              var match = BLOCK_STATE_RE.matcher(blockState.toString());
              if (match.find()) {
                int xOffset = x - x0;
                int yOffset = y - y0;
                int zOffset = z - z0;
                String blockType = match.group(1);
                String blockAttrs = match.group(2) == null ? "" : match.group(2);
                writer.printf(
                    "print(f'/setblock {x_start + %d} {y_start + %d} {z_start + %d} %s%s')\n",
                    xOffset, yOffset, zOffset, blockType, blockAttrs);
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

  private static void runCommand(String[] command) {
    if (!checkMinescriptDir()) {
      return;
    }

    command = substituteMinecraftVars(command);

    if (command[0].equals("jobs")) {
      if (checkParamTypes(command)) {
        listJobs();
      } else {
        logUserError("Expected no params, instead got `{}`", getParamsAsString(command));
      }
      return;
    }

    if (command[0].equals("suspend")) {
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
        copyBlocks(x0, y0, z0, x1, y1, z1);
      } else {
        logUserError(
            "Expected 6 params of type integer, instead got `{}`", getParamsAsString(command));
      }
      return;
    }

    if (command[0].equals("record")) {
      if (checkParamTypes(command)) {
        jobs.startRecording(command);
      } else {
        logUserError("Expected no params, instead got `{}`", getParamsAsString(command));
      }
      return;
    }

    if (command[0].equals("minescript_commands_per_cycle")) {
      if (checkParamTypes(command, ParamType.INT)) {
        int numCommands = Integer.valueOf(command[1]);
        if (numCommands < 1) numCommands = 1;
        minescriptCommandsPerCycle = numCommands;
        logUserError("Minescript execution set to {} command(s) per cycle.", numCommands);
      } else {
        logUserError(
            "Expected 1 param of type integer, instead got `{}`", getParamsAsString(command));
      }
      return;
    }

    if (command[0].equals("minescript_ticks_per_cycle")) {
      if (checkParamTypes(command, ParamType.INT)) {
        int ticks = Integer.valueOf(command[1]);
        if (ticks < 1) ticks = 1;
        minescriptTicksPerCycle = ticks;
        logUserError("Minescript execution set to {} tick(s) per cycle.", ticks);
      } else {
        logUserError(
            "Expected 1 param of type integer, instead got `{}`", getParamsAsString(command));
      }
      return;
    }

    if (command[0].equals("enable_minescript_on_chat_received_event")) {
      if (checkParamTypes(command, ParamType.BOOL)) {
        boolean enable = command[1].equals("true");
        enableMinescriptOnChatReceivedEvent = enable;
        logUserInfo(
            "Minescript execution on ClientChatReceivedEvent {}.{}",
            (enable ? "enabled" : "disabled"),
            (enable
                ? " e.g. add command to command block: [execute as Dev run tell Dev \\hello"
                    + " ~ ~ ~]"
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

  private static String findFirstFile(String... filenames) {
    for (String filename : filenames) {
      if (Files.exists(Paths.get(filename))) {
        return filename;
      }
    }
    return null;
  }

  private static int minescriptTicksPerCycle = 1;
  private static int minescriptCommandsPerCycle = 15;

  /* This is the old way of registering commands. This works only with single-person worlds where
     * server is local:

  import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
  import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
  import static com.mojang.brigadier.arguments.StringArgumentType.getString;
  import static com.mojang.brigadier.arguments.StringArgumentType.string;

  import com.mojang.brigadier.CommandDispatcher;
  import net.minecraft.commands.CommandSourceStack;
  import com.mojang.brigadier.builder.LiteralArgumentBuilder;
  import com.mojang.brigadier.builder.RequiredArgumentBuilder;

    private static LiteralArgumentBuilder<CommandSourceStack> literal(String name) {
      return LiteralArgumentBuilder.<CommandSourceStack>literal(name);
    }

    private static <T> RequiredArgumentBuilder<CommandSourceStack, T> argument(
        String name, ArgumentType<T> type) {
      return RequiredArgumentBuilder.<CommandSourceStack, T>argument(name, type);
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

    @SubscribeEvent
    public void onRegisterCommandEvent(RegisterCommandsEvent event) {
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
                            systemCommandQueue.add(
                                tellrawFormat(
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
                            systemCommandQueue.add(
                                tellrawFormat(
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
    */

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
            "(minescript) Cannot find field with obfuscated name \"{}\", falling back to"
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
        LOGGER.info("(minescript) Declared fields of {}:", klass.getName());
        for (Field f : klass.getDeclaredFields()) {
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
        var input = (EditBox) getField(screen, ChatScreen.class, "input", "f_95573_");
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
                systemCommandQueue.add(tellrawFormat("completions:", "aqua"));
                for (String suggestion : newCommandSuggestions) {
                  systemCommandQueue.add(tellrawFormat("  " + suggestion, "aqua"));
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
        logException(e);
      }
    }
  }

  private static boolean loggedMethodNameFallback = false;

  private static Method getMethod(
      Object object, String unobfuscatedName, String obfuscatedName, Class<?>... paramTypes)
      throws IllegalAccessException, NoSuchMethodException {
    Method method;
    try {
      method = object.getClass().getDeclaredMethod(obfuscatedName, paramTypes);
    } catch (NoSuchMethodException e) {
      if (!loggedMethodNameFallback) {
        LOGGER.info(
            "(minescript) Cannot find method with obfuscated name \"{}\", falling back to"
                + " unobfuscated name \"{}\"",
            obfuscatedName,
            unobfuscatedName);
        loggedMethodNameFallback = true;
      }
      try {
        method = object.getClass().getDeclaredMethod(unobfuscatedName, paramTypes);
      } catch (NoSuchMethodException e2) {
        logUserError(
            "Internal Minescript error: cannot find method {}/{} in class {}. See log file for"
                + " details.",
            unobfuscatedName,
            obfuscatedName,
            object.getClass().getSimpleName());
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
        logException(e);
      }
    }
  }

  /*
  private static long lastChunkEvent = 0; // TODO(maxuser):
  @SubscribeEvent
  public void onChunkEvent(ChunkEvent chunkEvent) {
    // TODO(maxuser): to prevent log spam, rate-limit the logging of chunk events
    long currentTime = System.currentTimeMillis();
    if (currentTime < lastChunkEvent + 1000) {
      return;
    }
    lastChunkEvent = currentTime;

    var chunk = chunkEvent.getChunk();
    var chunkPos = chunk.getPos();
    Map<String, Integer> blockCounts = new HashMap<>();
    for (int x = chunkPos.getMinBlockX(); x < chunkPos.getMaxBlockX(); x++) {
      for (int z = chunkPos.getMinBlockZ(); z < chunkPos.getMaxBlockZ(); z++) {
        for (int y = -64; y < 320; y++) {
          BlockState blockState = chunk.getBlockState(chunkPos.getBlockAt(x, y, z));
          String blockString = blockState.toString();
          blockCounts.put(blockString, blockCounts.getOrDefault(blockString, 0) + 1);
        }
      }
    }
    var chunkWorldPos = chunkPos.getWorldPosition();
    LOGGER.info("(minescript) Chunk load at ({}, y,{})", chunkWorldPos.getX(), chunkWorldPos.getZ());
    for (var entry : blockCounts.entrySet()) {
      LOGGER.info("  [{}x] {}", entry.getValue(), entry.getKey());
    }
  }
  */

  private static String lastReceivedBackslashedChatMessage = "";
  private static long lastReceivedBackslashedChatMessageTime = 0;
  private static boolean enableMinescriptOnChatReceivedEvent = false;

  @SubscribeEvent
  public void onClientChatEvent(ClientChatReceivedEvent event) {
    if (!enableMinescriptOnChatReceivedEvent) {
      return;
    }

    // Respond to messages like this one sent from a command block:
    //
    // [execute as Dev run tell Dev \hello ~ ~ ~]
    //
    // TranslatableComponent.args[1]:TextComponent.text:String

    if (event.getMessage() instanceof TranslatableComponent) {
      var component = (TranslatableComponent) event.getMessage();
      for (var arg : component.getArgs()) {
        if (arg instanceof TextComponent) {
          var textComponent = (TextComponent) arg;
          String text = textComponent.getText();
          long currentTime = System.currentTimeMillis();
          // Ignore duplicate consecutive backslashed messages less than 500 milliseconds apart.
          if (text.startsWith("\\")
              && (!text.equals(lastReceivedBackslashedChatMessage)
                  || currentTime > lastReceivedBackslashedChatMessageTime + 500)) {
            lastReceivedBackslashedChatMessage = text;
            lastReceivedBackslashedChatMessageTime = currentTime;

            // TODO(maxuser): need to do single/double quote parsing etc.
            String[] command = text.substring(1).split("\\s+");
            LOGGER.info(
                "(minescript) Processing command from received chat event: {}",
                String.join(" ", command));
            runCommand(command);
            event.setCanceled(true);
          }
        }
      }
    }
  }

  @SubscribeEvent
  public void onClientChatEvent(ClientChatEvent event) {
    if (event.getMessage().startsWith("\\")) {
      // TODO(maxuser): need to do single/double quote parsing etc.
      String[] command = event.getMessage().substring(1).split("\\s+");
      LOGGER.info("(minescript) Processing command from chat event: {}", String.join(" ", command));
      runCommand(command);
      event.setCanceled(true);
    }
  }

  private static class ServerBlockList {
    private final Path serverBlockListPath;
    private boolean lastCheckedValue = true;
    private String lastCheckedServerName = "";
    private String lastCheckedServerIp = "";
    private long lastCheckedTime = 0;

    public ServerBlockList() {
      serverBlockListPath = Paths.get(MINESCRIPT_DIR + "/server_block_list.txt");
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

      LOGGER.info(
          "(minescript) {} modified since last checked; refreshing...",
          serverBlockListPath.toString());
      try (var reader = new BufferedReader(new FileReader(serverBlockListPath.toString()))) {
        var line = reader.readLine();
        while (line != null) {
          line = line.replaceAll("#.*$", "").strip();
          if (line.equals(serverName) || line.equals(serverIp)) {
            LOGGER.info(
                "(minescript) Found server match in {}, commands disabled: {}",
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
          "(minescript) No server match in {}, commands enabled: {} / {}",
          serverBlockListPath.toString(),
          serverName,
          serverIp);
      lastCheckedValue = true;
      return lastCheckedValue;
    }
  }

  private static ServerBlockList serverBlockList = new ServerBlockList();

  @SubscribeEvent
  public void onPlayerTick(TickEvent.PlayerTickEvent event) {
    if (++playerTickEventCounter % minescriptTicksPerCycle == 0) {
      var minecraft = Minecraft.getInstance();
      var serverData = minecraft.getCurrentServer();

      if (!systemCommandQueue.isEmpty()
          && serverData != null
          && !serverBlockList.areCommandsAllowedForServer(serverData.name, serverData.ip)) {
        systemCommandQueue.clear();
        LOGGER.info("(minescript) Commands disabled, clearing command queue");
        return;
      }

      var player = minecraft.player;
      if (player != null && (!systemCommandQueue.isEmpty() || !jobs.getMap().isEmpty())) {
        for (int i = 0; i < minescriptCommandsPerCycle; ++i) {
          String command = systemCommandQueue.poll();
          if (command != null) {
            player.chat(command);
          }
          for (var job : jobs.getMap().values()) {
            if (job.state() == JobState.RUNNING) {
              String jobCommand = job.commandQueue().poll();
              if (jobCommand != null) {
                player.chat(jobCommand);
              }
            }
          }
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
