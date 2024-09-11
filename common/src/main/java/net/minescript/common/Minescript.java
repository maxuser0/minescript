// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import static net.minescript.common.CommandSyntax.Token;
import static net.minescript.common.CommandSyntax.parseCommand;
import static net.minescript.common.CommandSyntax.quoteCommand;
import static net.minescript.common.CommandSyntax.quoteString;

import com.google.common.base.Suppliers;
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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
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
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.world.entity.Entity;
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

public class Minescript {
  private static final Logger LOGGER = LogManager.getLogger();

  public static Config config;

  // MINESCRIPT_DIR is relative to the minecraft directory which is the working directory.
  private static final String MINESCRIPT_DIR = "minescript";

  private enum FileOverwritePolicy {
    DO_NOT_OVERWRITE,
    OVERWRITTE
  }

  private static Platform platform;
  private static String version;
  private static Thread worldListenerThread;

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

    String lastRunVersion = getLastRunVersion();
    if (lastRunVersion.equals(LEGACY_VERSION)) {
      deleteLegacyFiles();
    }
    Minescript.version = getCurrentVersion();
    if (!version.equals(lastRunVersion)) {
      LOGGER.info(
          "Current version ({}) does not match last run version ({})",
          Minescript.version,
          lastRunVersion);

      loadMinescriptResources();
    }

    worldListenerThread =
        new Thread(Minescript::runWorldListenerThread, "minescript-world-listener");
    worldListenerThread.start();

    Path minescriptDir = Paths.get(System.getProperty("user.dir"), MINESCRIPT_DIR);
    if (System.getProperty("os.name").startsWith("Windows")) {
      copyJarResourceToFile(
          "windows_config.txt", minescriptDir, "config.txt", FileOverwritePolicy.DO_NOT_OVERWRITE);
    } else {
      copyJarResourceToFile(
          "posix_config.txt", minescriptDir, "config.txt", FileOverwritePolicy.DO_NOT_OVERWRITE);
    }

    config =
        new Config(MINESCRIPT_DIR, "config.txt", BUILTIN_COMMANDS, IGNORE_DIRS_FOR_COMPLETIONS);
    config.load();
  }

  private static void deleteLegacyFiles() {
    LOGGER.info("Deleting files from legacy version of Minescript...");

    // Delete files that used to be stored directly within the `minescript` dir in legacy versions.
    deleteMinescriptFile("version.txt");
    deleteMinescriptFile("minescript.py");
    deleteMinescriptFile("minescript_runtime.py");
    deleteMinescriptFile("help.py");
    deleteMinescriptFile("copy.py");
    deleteMinescriptFile("paste.py");
    deleteMinescriptFile("eval.py");
  }

  private static void loadMinescriptResources() {
    Path minescriptDir = Paths.get(MINESCRIPT_DIR);
    Path systemDir = Paths.get(MINESCRIPT_DIR, "system");
    Path libDir = systemDir.resolve("lib");
    Path execDir = systemDir.resolve("exec");

    new File(libDir.toString()).mkdirs();
    new File(execDir.toString()).mkdirs();

    copyJarResourceToFile("version.txt", systemDir, FileOverwritePolicy.OVERWRITTE);
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

  private static void cancelOrphanedOperations(
      Map<JobOperationId, ? extends Job.Operation> operations) {
    if (!operations.isEmpty()) {
      LOGGER.info(
          "Cancelling orphaned operations when exiting world: {}",
          operations.entrySet().stream()
              .map(e -> String.format("%s %s", e.getValue().name(), e.getKey()))
              .collect(Collectors.toList()));

      // Stash values in a new list so that the operations map isn't modified while being
      // iterated/streamed, because Job.Operation::cancel removes a listener from the map.
      new ArrayList<>(operations.values()).stream().forEach(Job.Operation::cancel);
    }
  }

  private static void killAllJobs() {
    for (var job : jobs.getMap().values()) {
      LOGGER.info("Killing job: {}", job.jobSummary());
      job.kill();
    }

    // Job operations should have been cleaned up when killing jobs, but the jobs killed above might
    // still be in the process of shutting down, or operations may have been leaked accidentally.
    // Clear any leaked operations here.
    for (var handlerMap : eventHandlerMaps) {
      cancelOrphanedOperations(handlerMap);
    }

    customNickname = null;
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
          killAllJobs();
          systemMessageQueue.clear();
        } else {
          LOGGER.info("Entered world");
        }
        noWorld = noWorldNow;
      }
      try {
        Thread.sleep(millisToSleep);
      } catch (InterruptedException e) {
        systemMessageQueue.logException(e);
      }
    }
  }

  private static Gson GSON = new GsonBuilder().serializeNulls().create();

  private static String getCurrentVersion() {
    try (var in = Minescript.class.getResourceAsStream("/version.txt");
        var reader = new BufferedReader(new InputStreamReader(in))) {
      return reader.readLine().strip();
    } catch (IOException e) {
      LOGGER.error("Exception loading version resource: {}", e.toString());
      return "";
    }
  }

  private static final String LEGACY_VERSION = "legacy";

  private static String getLastRunVersion() {
    Path legacyVersionPath = Paths.get(MINESCRIPT_DIR, "version.txt");
    if (Files.exists(legacyVersionPath)) {
      return LEGACY_VERSION; // Doesn't matter what's in the legacy version file. It's out of date.
    }

    Path versionPath = Paths.get(MINESCRIPT_DIR, "system", "version.txt");
    if (!Files.exists(versionPath)) {
      return "";
    }
    try {
      return Files.readString(versionPath).strip();
    } catch (IOException e) {
      LOGGER.error("Exception loading version file: {}", e.toString());
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

  private static final ImmutableList<String> BUILTIN_COMMANDS =
      ImmutableList.of(
          "help",
          "ls",
          "copy",
          "jobs",
          "suspend",
          "z", // alias for suspend
          "resume",
          "killjob",
          "undo",
          "which",
          "config",
          "reload_minescript_resources");

  private static final ImmutableSet<String> IGNORE_DIRS_FOR_COMPLETIONS =
      ImmutableSet.of("blockpacks", "undo");

  private static final Pattern TILDE_RE = Pattern.compile("^~([-\\+]?)([0-9]*)$");

  private static String tildeParamToNumber(String param, double playerPosition) {
    var match = TILDE_RE.matcher(param);
    if (match.find()) {
      return String.valueOf(
          (int) playerPosition
              + (match.group(1).equals("-") ? -1 : 1)
                  * (match.group(2).isEmpty() ? 0 : Integer.valueOf(match.group(2))));
    } else {
      systemMessageQueue.logUserError("Cannot parse tilde-param: \"{}\"", param);
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
          systemMessageQueue.logUserError(
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
      systemMessageQueue.logUserError(
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
          systemMessageQueue.logException(e);
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
          systemMessageQueue.logException(e);
        }
      }
    }
  }

  static class EventHandler implements Job.Operation {
    private final JobControl job;
    private final String funcName;
    private OptionalLong funcCallId = OptionalLong.empty();
    private State state = State.IDLE;
    private boolean suspended = false;
    private Runnable doneCallback;
    private Optional<Predicate<Object>> filter;

    public enum State {
      IDLE,
      ACTIVE,
      CANCELLED
    }

    public EventHandler(JobControl job, String funcName, Runnable doneCallback) {
      this.job = job;
      this.funcName = funcName;
      this.doneCallback = doneCallback;
    }

    public void setFilter(Predicate<Object> filter) {
      this.filter = Optional.of(filter);
    }

    public boolean applies(Object event) {
      if (filter.isPresent()) {
        return filter.get().test(event);
      }
      return true;
    }

    int jobId() {
      return job.jobId();
    }

    @Override
    public String name() {
      return funcName;
    }

    public synchronized void start(long funcCallId) {
      if (state != State.CANCELLED) {
        this.funcCallId = OptionalLong.of(funcCallId);
        state = State.ACTIVE;
      }
    }

    public synchronized boolean isActive() {
      return !suspended && state == State.ACTIVE;
    }

    @Override
    public synchronized void suspend() {
      suspended = true;
    }

    @Override
    public boolean resumeAndCheckDone() {
      if (state == State.CANCELLED) {
        return true;
      }
      suspended = false;
      return false;
    }

    @Override
    public synchronized void cancel() {
      LOGGER.info("Cancelling EventHandler for job {} func {}", jobId(), funcCallId);
      state = State.CANCELLED;
      funcCallId = OptionalLong.empty();
      doneCallback.run();
    }

    public synchronized boolean respond(JsonElement returnValue, boolean finalReply) {
      if (funcCallId.isPresent()) {
        return job.respond(funcCallId.getAsLong(), returnValue, finalReply);
      } else {
        return false;
      }
    }
  }

  static class ScheduledTaskList implements Job.Operation {
    private final Job job;
    private final String name;
    private final List<?> tasks;
    private final Runnable doneCallback;
    private long lastReportedErrorTime; // Throttle error reporting so as to not spam logs or chat.
    private boolean suspended = false;

    public ScheduledTaskList(Job job, String name, List<?> tasks, Runnable doneCallback) {
      this.job = job;
      this.name = name;
      this.tasks = tasks;
      this.doneCallback = doneCallback;
      this.lastReportedErrorTime = System.currentTimeMillis();
    }

    public void run() {
      if (suspended) {
        return;
      }

      try {
        runTasks(job, tasks);
      } catch (Exception e) {
        long time = System.currentTimeMillis();
        if (time - lastReportedErrorTime > 5000) {
          lastReportedErrorTime = time;
          systemMessageQueue.logException(
              e, String.format("Error from scheduled %s: %s", name, e.toString()));
        }
      }
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public void suspend() {
      suspended = true;
    }

    @Override
    public boolean resumeAndCheckDone() {
      suspended = false;
      return false; // Never done until cancelled.
    }

    @Override
    public void cancel() {
      LOGGER.info("Cancelling ScheduledTaskList: `{}`", name);
      doneCallback.run();
    }
  }

  static class UndoTask implements Task {
    private final UndoableAction undo;

    public UndoTask(UndoableAction undo) {
      this.undo = undo;
    }

    @Override
    public int run(ScriptConfig.BoundCommand command, JobControl jobControl) {
      undo.enqueueCommands(jobControl.tickQueue());
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
          new Job(
              allocateJobId(),
              command,
              new SubprocessTask(config),
              config,
              systemMessageQueue,
              Minescript::processScriptFunction,
              i -> finishJob(i, nextCommand));
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
        systemMessageQueue.logUserError("The undo stack is empty.");
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
              config,
              systemMessageQueue,
              Minescript::processScriptFunction,
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

  private static SystemMessageQueue systemMessageQueue = new SystemMessageQueue();

  private static boolean checkMinescriptDir() {
    Path minescriptDir = Paths.get(System.getProperty("user.dir"), MINESCRIPT_DIR);
    if (!Files.isDirectory(minescriptDir)) {
      systemMessageQueue.logUserError(
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

  private static void listJobs() {
    if (jobs.getMap().isEmpty()) {
      systemMessageQueue.logUserInfo("There are no jobs running.");
      return;
    }
    for (var job : jobs.getMap().values()) {
      systemMessageQueue.logUserInfo(job.toString());
    }
  }

  private static void suspendJob(OptionalInt jobId) {
    if (jobId.isPresent()) {
      // Suspend specified job.
      var job = jobs.getMap().get(jobId.getAsInt());
      if (job == null) {
        systemMessageQueue.logUserError(
            "No job with ID {}. Use \\jobs to list jobs.", jobId.getAsInt());
        return;
      }
      if (job.suspend()) {
        systemMessageQueue.logUserInfo("Job suspended: {}", job.jobSummary());
      }
    } else {
      // Suspend all jobs.
      for (var job : jobs.getMap().values()) {
        if (job.suspend()) {
          systemMessageQueue.logUserInfo("Job suspended: {}", job.jobSummary());
        }
      }
    }
  }

  private static void resumeJob(OptionalInt jobId) {
    if (jobId.isPresent()) {
      // Resume specified job.
      var job = jobs.getMap().get(jobId.getAsInt());
      if (job == null) {
        systemMessageQueue.logUserError(
            "No job with ID {}. Use \\jobs to list jobs.", jobId.getAsInt());
        return;
      }
      if (job.resume()) {
        systemMessageQueue.logUserInfo("Job resumed: {}", job.jobSummary());
      }
    } else {
      // Resume all jobs.
      for (var job : jobs.getMap().values()) {
        if (job.resume()) {
          systemMessageQueue.logUserInfo("Job resumed: {}", job.jobSummary());
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
      systemMessageQueue.logUserError("No job with ID {}. Use \\jobs to list jobs.", jobId);
      return;
    }
    job.kill();
    systemMessageQueue.logUserInfo("Removed job: {}", job.jobSummary());
  }

  private static Pattern SETBLOCK_COMMAND_RE =
      Pattern.compile("setblock ([^ ]+) ([^ ]+) ([^ ]+).*");

  private static boolean getSetblockCoords(String setblockCommand, int[] coords) {
    var match = SETBLOCK_COMMAND_RE.matcher(setblockCommand);
    if (!match.find()) {
      return false;
    }
    if (setblockCommand.contains("~")) {
      systemMessageQueue.logUserInfo("Warning: /setblock commands with ~ syntax cannot be undone.");
      systemMessageQueue.logUserInfo("           Use minescript.player_position() instead.");
      return false;
    }
    try {
      coords[0] = Integer.valueOf(match.group(1));
      coords[1] = Integer.valueOf(match.group(2));
      coords[2] = Integer.valueOf(match.group(3));
    } catch (NumberFormatException e) {
      systemMessageQueue.logUserError(
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
      systemMessageQueue.logUserError("Warning: /fill commands with ~ syntax cannot be undone.");
      systemMessageQueue.logUserError("           Use minescript.player_position() instead.");
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

    Level level = minecraft.level;

    int playerX = (int) player.getX();
    int playerY = (int) player.getY();
    int playerZ = (int) player.getZ();

    int xMin = Math.min(x0, x1);
    int yMin = Math.max(Math.min(y0, y1), level.getMinBuildHeight());
    int zMin = Math.min(z0, z1);

    int xMax = Math.max(x0, x1);
    int yMax = Math.min(Math.max(y0, y1), level.getMaxBuildHeight());
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
              systemMessageQueue.logUserError(
                  "Unexpected BlockState format: {}", blockState.toString());
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
      config.load();

      List<Token> tokens = parseCommand(commandLine);

      if (tokens.isEmpty()) {
        systemMessageQueue.add(
            Message.fromJsonFormattedText(
                "{\"text\":\"Technoblade never dies.\",\"color\":\"dark_red\",\"bold\":true}"));
        return;
      }

      runParsedMinescriptCommand(tokens);

    } catch (RuntimeException e) {
      systemMessageQueue.logException(e);
    }
  }

  private static boolean printBuiltinHelp(String command) {
    switch (command) {
      case "help":
        systemMessageQueue.logUserInfo("{} (built-in command)", command);
        systemMessageQueue.logUserInfo("Usage: \\help [command]");
        systemMessageQueue.logUserInfo("Prints documentation for the given command,");
        systemMessageQueue.logUserInfo("or this documentation if no command is given.");
        return true;
      case "ls":
        systemMessageQueue.logUserInfo("{} (built-in command)", command);
        systemMessageQueue.logUserInfo("Usage: \\ls");
        systemMessageQueue.logUserInfo("Lists available commands.");
        return true;
      case "copy":
        systemMessageQueue.logUserInfo("{} (built-in command)", command);
        systemMessageQueue.logUserInfo("Usage: \\copy ...");
        systemMessageQueue.logUserInfo("Alias for the built-in `copy_blocks` script.");
        return true;
      case "jobs":
        systemMessageQueue.logUserInfo("{} (built-in command)", command);
        systemMessageQueue.logUserInfo("Usage: \\jobs");
        systemMessageQueue.logUserInfo("Lists currently running (or suspended) script jobs.");
        return true;
      case "suspend":
        systemMessageQueue.logUserInfo("{} (built-in command) (alias: `z`)", command);
        systemMessageQueue.logUserInfo("Usage: \\suspend [jobID]");
        systemMessageQueue.logUserInfo(
            "Suspends the job with the given ID, or all jobs if none given.");
        return true;
      case "z":
        systemMessageQueue.logUserInfo("{} (built-in command)", command);
        systemMessageQueue.logUserInfo("Usage: \\z [jobID]");
        systemMessageQueue.logUserInfo("Alias for `\\suspend [jobID]`.");
        return true;
      case "resume":
        systemMessageQueue.logUserInfo("{} (built-in command)", command);
        systemMessageQueue.logUserInfo("Usage: \\resume [jobID]");
        systemMessageQueue.logUserInfo("Resumes the suspended job with the given ID.");
        return true;
      case "killjob":
        systemMessageQueue.logUserInfo("{} (built-in command)", command);
        systemMessageQueue.logUserInfo("Usage: \\killjob [jobID]");
        systemMessageQueue.logUserInfo("Kills the job with the given ID.");
        return true;
      case "undo":
        systemMessageQueue.logUserInfo("{} (built-in command)", command);
        systemMessageQueue.logUserInfo("Usage: \\undo");
        systemMessageQueue.logUserInfo(
            "Undoes the setblock and fill commands of the most recent job.");
        systemMessageQueue.logUserInfo("`undo` can be run multiple times to undo multiple jobs.");
        return true;
      case "which":
        systemMessageQueue.logUserInfo("{} (built-in command)", command);
        systemMessageQueue.logUserInfo("Usage: \\which [command]");
        systemMessageQueue.logUserInfo("Prints the location of the given command.");
        return true;
      case "config":
        systemMessageQueue.logUserInfo("{} (built-in command)", command);
        systemMessageQueue.logUserInfo("Usage: \\config [name [value]]");
        systemMessageQueue.logUserInfo("`\\config`: lists all config values");
        systemMessageQueue.logUserInfo("`\\config name`: echoes the value of the named variable");
        systemMessageQueue.logUserInfo(
            "`\\config name value`: sets the value of the named variable");
        return true;
      case "reload_minescript_resources":
        systemMessageQueue.logUserInfo("{} (built-in command)", command);
        systemMessageQueue.logUserInfo("Usage: \\reload_minescript_resources");
        systemMessageQueue.logUserInfo(
            "Reloads resources from the Minescript jar to the `minescript` dir.");
        return true;
      default:
        return false;
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
        case "help":
          if (checkParamTypes(command)) {
            printBuiltinHelp("help");
            runParsedMinescriptCommand(nextCommand);
            return;
          } else if (!checkParamTypes(command, ParamType.STRING)) {
            systemMessageQueue.logUserError(
                "Expected one param (command name), instead got `{}`", getParamsAsString(command));
            runParsedMinescriptCommand(nextCommand);
            return;
          } else if (printBuiltinHelp(command[1])) {
            runParsedMinescriptCommand(nextCommand);
            return;
          }
          // Handle below as an external script command via `help.py`.
          break;

        case "jobs":
          if (checkParamTypes(command)) {
            listJobs();
          } else {
            systemMessageQueue.logUserError(
                "Expected no params, instead got `{}`", getParamsAsString(command));
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
            systemMessageQueue.logUserError(
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
            systemMessageQueue.logUserError(
                "Expected no params or 1 param of type integer, instead got `{}`",
                getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "killjob":
          if (checkParamTypes(command, ParamType.INT)) {
            killJob(Integer.valueOf(command[1]));
          } else {
            systemMessageQueue.logUserError(
                "Expected 1 param of type integer, instead got `{}`", getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "undo":
          if (checkParamTypes(command)) {
            jobs.startUndo();
          } else {
            systemMessageQueue.logUserError(
                "Expected no params or 1 param of type integer, instead got `{}`",
                getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "which":
          if (checkParamTypes(command, ParamType.STRING)) {
            String arg = command[1];
            if (BUILTIN_COMMANDS.contains(arg)) {
              systemMessageQueue.logUserInfo("Built-in command: `{}`", arg);
            } else {
              Path commandPath = config.scriptConfig().resolveCommandPath(arg);
              if (commandPath == null) {
                systemMessageQueue.logUserInfo("Command `{}` not found.", arg);
              } else {
                systemMessageQueue.logUserInfo(commandPath.toString());
              }
            }
          } else {
            systemMessageQueue.logUserError(
                "Expected 1 param of type string, instead got `{}`", getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "config":
          if (command.length == 1) {
            systemMessageQueue.logUserInfo("Minescript config:");
            config.forEachValue(
                (name, value) -> systemMessageQueue.logUserInfo("  {} = \"{}\"", name, value));
          } else if (command.length == 2) {
            String name = command[1];
            try {
              systemMessageQueue.logUserInfo("{} = \"{}\"", name, config.getValue(name));
            } catch (IllegalArgumentException e) {
              systemMessageQueue.logUserError("{}", e.getMessage());
            }
          } else if (command.length == 3) {
            String name = command[1];
            String value = command[2];
            int[] infoMessages = new int[1]; // wrap in an array to allow mutable int in lambda
            config.setValue(
                name,
                value,
                status -> {
                  if (status.success()) {
                    systemMessageQueue.logUserInfo(status.message());
                    ++infoMessages[0];
                  } else {
                    systemMessageQueue.logUserError(status.message());
                  }
                });
            if (infoMessages[0] > 0) {
              systemMessageQueue.logUserInfo(
                  "(Note: Config value will revert when config.txt is reloaded.)");
            }
          } else {
            systemMessageQueue.logUserError(
                "Expected 2 or fewer params, instead got `{}`", getParamsAsString(command));
          }
          runParsedMinescriptCommand(nextCommand);
          return;

        case "reload_minescript_resources":
          loadMinescriptResources();
          systemMessageQueue.logUserInfo("Reloaded resources from Minescript jar.");
          runParsedMinescriptCommand(nextCommand);
          return;

        case "NullPointerException":
          // This is for testing purposes only. Throw NPE only if we're in debug mode.
          if (config.debugOutput()) {
            String s = null;
            systemMessageQueue.logUserError("Length of a null string is {}", s.length());
          }
      }

      // Rename "copy" to "copy_blocks" for backward compatibility since copy.py was renamed to
      // copy_blocks.py.
      if ("copy".equals(command[0])) {
        command[0] = "copy_blocks";
      }

      Path commandPath = config.scriptConfig().resolveCommandPath(command[0]);
      if (commandPath == null) {
        systemMessageQueue.logUserInfo("Minescript built-in commands:");
        for (String builtin : BUILTIN_COMMANDS) {
          systemMessageQueue.logUserInfo("  {}", builtin);
        }
        systemMessageQueue.logUserInfo("");
        systemMessageQueue.logUserInfo("Minescript command directories:");
        Path minescriptDir = Paths.get(System.getProperty("user.dir"), MINESCRIPT_DIR);
        for (Path commandDir : config.scriptConfig().commandPath()) {
          Path path = minescriptDir.resolve(commandDir);
          systemMessageQueue.logUserInfo("  {}", path);
        }
        if (!command[0].equals("ls")) {
          systemMessageQueue.logUserError("No Minescript command named \"{}\"", command[0]);
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
      systemMessageQueue.logException(e);
    }
  }

  private static long worldRenderEventCounter = 0;
  private static long clientTickEventCounter = 0;

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

  public static void onKeyboardEvent(int key, int scanCode, int action, int modifiers) {
    JsonObject json = null;
    for (var entry : keyEventListeners.entrySet()) {
      var listener = entry.getValue();
      if (listener.isActive()) {
        if (json == null) {
          String screenName = getScreenName().orElse(null);
          long timeMillis = System.currentTimeMillis();
          json = new JsonObject();
          json.addProperty("key", key);
          json.addProperty("scan_code", scanCode);
          json.addProperty("action", action);
          json.addProperty("modifiers", modifiers);
          json.addProperty("time", timeMillis / 1000.);
          json.addProperty("screen", screenName);
        }
        if (config.debugOutput()) {
          LOGGER.info("Forwarding key event to listener {}: {}", entry.getKey(), json);
        }
        listener.respond(json, false);
      }
    }
  }

  public static void onMouseClick(int button, int action, int modifiers, double x, double y) {
    var minecraft = Minecraft.getInstance();
    JsonObject json = null;
    for (var entry : mouseEventListeners.entrySet()) {
      var listener = entry.getValue();
      if (listener.isActive()) {
        if (json == null) {
          String screenName = getScreenName().orElse(null);
          long timeMillis = System.currentTimeMillis();
          json = new JsonObject();
          json.addProperty("button", button);
          json.addProperty("action", action);
          json.addProperty("modifiers", modifiers);
          json.addProperty("time", timeMillis / 1000.);
          json.addProperty("x", x);
          json.addProperty("y", y);
          json.addProperty("screen", screenName);
        }
        if (config.debugOutput()) {
          LOGGER.info("Forwarding mouse event to listener {}: {}", entry.getKey(), json);
        }
        listener.respond(json, false);
      }
    }
  }

  public static void onRenderWorld() {
    for (var taskList : renderTaskLists.values()) {
      taskList.run();
    }

    if (++worldRenderEventCounter % config.ticksPerCycle() == 0) {
      var minecraft = Minecraft.getInstance();
      var player = minecraft.player;
      if (player != null && !jobs.getMap().isEmpty()) {
        processMessageQueue(false /* processSystemMessages */, job -> job.renderQueue().poll());
      }
    }
  }

  private static EditBox chatEditBox = null;
  private static boolean reportedChatEditBoxError = false;

  public static void setChatScreenInput(EditBox input) {
    chatEditBox = input;
  }

  private static boolean checkChatScreenInput() {
    if (chatEditBox == null) {
      if (!reportedChatEditBoxError) {
        reportedChatEditBoxError = true;
        systemMessageQueue.logUserError(
            "Minescript internal error: Expected ChatScreen.input to be initialized by"
                + " ChatScreen.init(), but it's null instead. Minescript commands sent through"
                + " chat will not be interpreted as commands, and sent as normal chats instead.");
      }
      return false;
    }
    return true;
  }

  private static final ImmutableSet<String> COMMANDS_WITH_FIRST_PARAM_COMPLETIONS =
      ImmutableSet.of("config", "help", "which");

  private static String getCompletableCommand(String input) {
    String[] words = input.split("\\s+", -1);
    if (words.length > 1 && COMMANDS_WITH_FIRST_PARAM_COMPLETIONS.contains(words[0])) {
      return words[0] + " " + words[1];
    }
    return words.length > 0 ? words[0] : "";
  }

  private static List<String> getCommandCompletions(String command) {
    try {
      if (command.equals("config") || command.startsWith("config ")) {
        return config.getConfigVariables().stream()
            .filter(s -> s.startsWith(command))
            .sorted()
            .collect(Collectors.toList());
      } else if (command.equals("help") || command.startsWith("help ")) {
        return config.scriptConfig().findCommandPrefixMatches("").stream()
            .sorted()
            .map(s -> "help " + s)
            .filter(s -> s.startsWith(command))
            .collect(Collectors.toList());
      } else if (command.equals("which") || command.startsWith("which ")) {
        return config.scriptConfig().findCommandPrefixMatches("").stream()
            .sorted()
            .map(s -> "which " + s)
            .filter(s -> s.startsWith(command))
            .collect(Collectors.toList());
      } else {
        var completions = config.scriptConfig().findCommandPrefixMatches(command);
        completions.sort(null);
        return completions;
      }
    } catch (InvalidPathException e) {
      LOGGER.warn("Exception while finding command completions: {}", e.toString());
      return new ArrayList<>();
    }
  }

  public static boolean onKeyboardKeyPressed(Screen screen, int key) {
    boolean cancel = false;
    if (screen != null && screen instanceof ChatScreen) {
      if (!checkChatScreenInput()) {
        return cancel;
      }
      if (key == UP_ARROW_KEY || key == DOWN_ARROW_KEY) {
        return cancel;
      }
      String value = chatEditBox.getValue();
      if (!value.startsWith("\\")) {
        if ((key == ENTER_KEY || key == config.secondaryEnterKeyCode())
            && !value.startsWith("/")
            && (customNickname != null || matchesChatInterceptor(value))) {
          cancel = true;
          chatEditBox.setValue("");
          onClientChat(value);
          screen.onClose();
        }
        return cancel;
      }
      if (key == ENTER_KEY || key == config.secondaryEnterKeyCode()) {
        cancel = true;
        String text = chatEditBox.getValue();
        chatEditBox.setValue("");
        onClientChat(text);
        screen.onClose();
        return cancel;
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
        String command = getCompletableCommand(value.substring(1));
        if (key == TAB_KEY && !commandSuggestions.isEmpty()) {
          cancel = true;
          if (cursorPos == command.length() + 1) {
            // Insert the remainder of the completed command.
            String maybeTrailingSpace =
                ((cursorPos < value.length() && value.charAt(cursorPos) == ' ')
                        || commandSuggestions.size() > 1
                        || commandSuggestions.get(0).endsWith(File.separator))
                    ? ""
                    : " ";
            try {
              chatEditBox.insertText(
                  longestCommonPrefix(commandSuggestions).substring(command.length())
                      + maybeTrailingSpace);
            } catch (StringIndexOutOfBoundsException e) {
              systemMessageQueue.logUserError(
                  "Minescript internal error: "
                      + "StringIndexOutOfBoundsException from "
                      + "(longestCommonPrefix([\"{}\"]) = \"{}\").substring(\"{}\".length() = {})",
                  String.join("\", \"", commandSuggestions),
                  longestCommonPrefix(commandSuggestions),
                  command,
                  command.length());
              return cancel;
            }
            if (commandSuggestions.size() > 1) {
              chatEditBox.setTextColor(0x5ee8e8); // cyan for partial completion
            } else {
              chatEditBox.setTextColor(0x5ee85e); // green for full completion
            }
            commandSuggestions = new ArrayList<>();
            return cancel;
          }
        }
        var completions = getCommandCompletions(command);
        if (completions.contains(command)) {
          chatEditBox.setTextColor(0x5ee85e); // green
          commandSuggestions = new ArrayList<>();
        } else {
          List<String> newCommandSuggestions = new ArrayList<>();
          newCommandSuggestions.addAll(completions);
          if (!newCommandSuggestions.isEmpty()) {
            if (!newCommandSuggestions.equals(commandSuggestions)) {
              if (key == TAB_KEY || config.incrementalCommandSuggestions()) {
                if (key == TAB_KEY) {
                  cancel = true;
                }
                systemMessageQueue.add(Message.formatAsJsonColoredText("completions:", "aqua"));
                for (String suggestion : newCommandSuggestions) {
                  systemMessageQueue.add(
                      Message.formatAsJsonColoredText("  " + suggestion, "aqua"));
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

  private static Pattern CHAT_WHISPER_MESSAGE_RE = Pattern.compile("You whisper to [^ :]+: (.*)");

  public static boolean onClientChatReceived(Component message) {
    boolean cancel = false;
    String text = message.getString();

    var iter = chatEventListeners.entrySet().iterator();
    while (iter.hasNext()) {
      var entry = iter.next();
      var listener = entry.getValue();
      if (listener.isActive()) {
        var json = new JsonObject();
        json.addProperty("message", text);
        json.addProperty("time", System.currentTimeMillis() / 1000.);
        if (config.debugOutput()) {
          LOGGER.info("Forwarding chat message to listener {}: {}", entry.getKey(), json);
        }
        listener.respond(json, false);
      }
    }

    if (config.minescriptOnChatReceivedEvent()) {
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

  private static int chunkCoordToWorldCoord(int x) {
    return (x >= 0) ? (16 * x) : (16 * (x + 1) - 1);
  }

  private static void handleChunkEvent(int chunkX, int chunkZ, boolean loaded) {
    var minecraft = Minecraft.getInstance();
    var level = minecraft.level;
    JsonObject json = null;
    for (var handler : chunkEventListeners.values()) {
      if (handler.isActive()) {
        if (json == null) {
          json = new JsonObject();
          int worldX = chunkCoordToWorldCoord(chunkX);
          int worldZ = chunkCoordToWorldCoord(chunkZ);
          json.addProperty("loaded", loaded);
          json.addProperty("x_min", worldX);
          json.addProperty("z_min", worldZ);
          json.addProperty("x_max", worldX + 15);
          json.addProperty("z_max", worldZ + 15);
          json.addProperty("time", System.currentTimeMillis() / 1000.);
        }
        handler.respond(json, false);
      }
    }
  }

  public static void onChunkLoad(LevelAccessor chunkLevel, ChunkAccess chunk) {
    int chunkX = chunk.getPos().x;
    int chunkZ = chunk.getPos().z;
    if (config.debugOutput()) {
      LOGGER.info("world {} chunk loaded: {} {}", chunkLevel.hashCode(), chunkX, chunkZ);
    }
    var iter = chunkLoadEventListeners.entrySet().iterator();
    while (iter.hasNext()) {
      var entry = iter.next();
      var listener = entry.getValue();
      if (listener.onChunkLoaded(chunkLevel, chunkX, chunkZ)) {
        iter.remove();
      }
    }

    handleChunkEvent(chunkX, chunkZ, true);
  }

  public static void onChunkUnload(LevelAccessor chunkLevel, ChunkAccess chunk) {
    int chunkX = chunk.getPos().x;
    int chunkZ = chunk.getPos().z;
    if (config.debugOutput()) {
      LOGGER.info("world {} chunk unloaded: {} {}", chunkLevel.hashCode(), chunkX, chunkZ);
    }
    for (var entry : chunkLoadEventListeners.entrySet()) {
      var listener = entry.getValue();
      listener.onChunkUnloaded(chunkLevel, chunkX, chunkZ);
    }

    handleChunkEvent(chunkX, chunkZ, false);
  }

  private static boolean matchesChatInterceptor(String message) {
    for (var interceptor : chatInterceptors.values()) {
      if (interceptor.applies(message)) {
        return true;
      }
    }
    return false;
  }

  private static boolean applyChatInterceptors(String message) {
    for (var interceptor : chatInterceptors.values()) {
      if (interceptor.applies(message)) {
        var json = new JsonObject();
        json.addProperty("message", message);
        json.addProperty("time", System.currentTimeMillis() / 1000.);
        interceptor.respond(json, false);
        return true;
      }
    }
    return false;
  }

  public static boolean onClientChat(String message) {
    boolean cancel = false;
    var minecraft = Minecraft.getInstance();
    if (message.startsWith("\\")) {
      minecraft.gui.getChat().addRecentChat(message);

      LOGGER.info("Processing command from chat event: {}", message);
      runMinescriptCommand(message.substring(1));
      cancel = true;
    } else if (!message.startsWith("/") && applyChatInterceptors(message)) {
      cancel = true;
    } else if (customNickname != null && !message.startsWith("/")) {
      String tellrawCommand = "tellraw @a " + String.format(customNickname, message);
      systemMessageQueue.add(Message.createMinecraftCommand(tellrawCommand));
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
        systemMessageQueue.logException(e);
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

  public record JobOperationId(int jobId, long opId) {}

  // The keys in the event listener maps are based on the funcCallId of the register method (e.g.
  // "register_key_listener") which is considered the listener ID. But the funcCallId
  // associated with the actual listening within the EventHandler corresponds to the start method
  // (e.g. "start_key_listener").
  private static Map<JobOperationId, EventHandler> keyEventListeners = new ConcurrentHashMap<>();
  private static Map<JobOperationId, EventHandler> mouseEventListeners = new ConcurrentHashMap<>();
  private static Map<JobOperationId, EventHandler> chatEventListeners = new ConcurrentHashMap<>();
  private static Map<JobOperationId, EventHandler> chatInterceptors = new ConcurrentHashMap<>();
  private static Map<JobOperationId, EventHandler> addEntityEventListeners =
      new ConcurrentHashMap<>();
  private static Map<JobOperationId, EventHandler> blockUpdateEventListeners =
      new ConcurrentHashMap<>();
  private static Map<JobOperationId, EventHandler> takeItemEventListeners =
      new ConcurrentHashMap<>();
  private static Map<JobOperationId, EventHandler> damageEventListeners = new ConcurrentHashMap<>();
  private static Map<JobOperationId, EventHandler> explosionEventListeners =
      new ConcurrentHashMap<>();
  private static Map<JobOperationId, EventHandler> chunkEventListeners = new ConcurrentHashMap<>();

  private static ImmutableList<Map<JobOperationId, EventHandler>> eventHandlerMaps =
      ImmutableList.of(
          keyEventListeners,
          mouseEventListeners,
          chatEventListeners,
          chatInterceptors,
          addEntityEventListeners,
          blockUpdateEventListeners,
          takeItemEventListeners,
          damageEventListeners,
          explosionEventListeners,
          chunkEventListeners);

  private static Map<JobOperationId, ChunkLoadEventListener> chunkLoadEventListeners =
      new ConcurrentHashMap<JobOperationId, ChunkLoadEventListener>();

  private static Map<JobOperationId, ScheduledTaskList> tickTaskLists = new ConcurrentHashMap<>();
  private static Map<JobOperationId, ScheduledTaskList> renderTaskLists = new ConcurrentHashMap<>();

  public static void onAddEntityEvent(Entity entity) {
    JsonObject json = null;
    for (var handler : addEntityEventListeners.values()) {
      if (handler.isActive()) {
        if (json == null) {
          json = new JsonObject();
          boolean includeNbt = false;
          json.add(
              "entity",
              new EntityExporter(entityPositionInterpolation(), includeNbt).export(entity));
          json.addProperty("time", System.currentTimeMillis() / 1000.);
        }
        handler.respond(json, false);
      }
    }
  }

  public static void onBlockUpdateEvent(BlockPos pos, BlockState newState) {
    var minecraft = Minecraft.getInstance();
    var level = minecraft.level;
    JsonObject json = null;
    for (var handler : blockUpdateEventListeners.values()) {
      if (handler.isActive()) {
        if (json == null) {
          json = new JsonObject();
          var position = new JsonArray();
          position.add(pos.getX());
          position.add(pos.getY());
          position.add(pos.getZ());
          json.add("position", position);

          // TODO(maxuser): If a block changes due to local player's mouse click or key press,
          // the block can change locally before the BlockUpdate packet is received by the client.
          // Maybe track the current block during mouse and key events via
          // minecraft.getCameraEntity().pick(...). (see player_get_targeted_block())
          json.addProperty("old_state", blockStateToString(level.getBlockState(pos)).orElse(null));
          json.addProperty("new_state", blockStateToString(newState).orElse(null));
          json.addProperty("time", System.currentTimeMillis() / 1000.);
        }
        handler.respond(json, false);
      }
    }
  }

  public static void onTakeItemEvent(Entity player, Entity item, int amount) {
    JsonObject json = null;
    for (var handler : takeItemEventListeners.values()) {
      if (handler.isActive()) {
        if (json == null) {
          json = new JsonObject();
          boolean includeNbt = false;
          json.addProperty("player_uuid", player.getUUID().toString());
          json.add(
              "item", new EntityExporter(entityPositionInterpolation(), includeNbt).export(item));
          json.addProperty("amount", amount);
          json.addProperty("time", System.currentTimeMillis() / 1000.);
        }
        handler.respond(json, false);
      }
    }
  }

  public static void onDamageEvent(Entity entity, Entity cause, String source) {
    JsonObject json = null;
    for (var handler : damageEventListeners.values()) {
      if (handler.isActive()) {
        if (json == null) {
          json = new JsonObject();
          json.addProperty("entity_uuid", entity.getUUID().toString());
          json.addProperty("cause_uuid", cause == null ? null : cause.getUUID().toString());
          json.addProperty("source", source);
          json.addProperty("time", System.currentTimeMillis() / 1000.);
        }
        handler.respond(json, false);
      }
    }
  }

  public static void onExplosionEvent(double x, double y, double z, List<BlockPos> toExplode) {
    var minecraft = Minecraft.getInstance();
    var level = minecraft.level;
    JsonObject json = null;
    for (var handler : explosionEventListeners.values()) {
      if (handler.isActive()) {
        if (json == null) {
          json = new JsonObject();

          var blockpacker = new BlockPacker();
          for (var pos : toExplode) {
            blockStateToString(level.getBlockState(pos))
                .ifPresent(
                    block -> blockpacker.setblock(pos.getX(), pos.getY(), pos.getZ(), block));
          }
          String encodedBlockpack = blockpacker.pack().toBase64EncodedString();

          var position = new JsonArray();
          position.add(x);
          position.add(y);
          position.add(z);
          json.add("position", position);

          json.addProperty("blockpack_base64", encodedBlockpack);
          json.addProperty("time", System.currentTimeMillis() / 1000.);
        }
        handler.respond(json, false);
      }
    }
  }

  private static String customNickname = null;

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
    chat.addMessage(Component.Serializer.fromJson(text, minecraft.level.registryAccess()));
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
      var minecraft = Minecraft.getInstance();
      var nbt = itemStack.save(minecraft.level.registryAccess());
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

  // String key: KeyMapping.getName()
  private static final Map<String, InputConstants.Key> keyBinds = new ConcurrentHashMap<>();

  public static void setKeyBind(String keyMappingName, InputConstants.Key key) {
    LOGGER.info("Set key binding: {} -> {}", keyMappingName, key);
    keyBinds.put(keyMappingName, key);
  }

  private static void pressKeyBind(String keyMappingName, boolean pressed) {
    var key = keyBinds.get(keyMappingName);
    if (key == null) {
      throw new IllegalArgumentException(
          String.format(
              "No key mapping with name `%s`; assigned values are: %s",
              keyMappingName, String.join(", ", keyBinds.keySet().toArray(String[]::new))));
    }
    if (pressed) {
      KeyMapping.set(key, true);
      KeyMapping.click(key);
    } else {
      KeyMapping.set(key, false);
    }
  }

  private static Optional<JsonElement> doPlayerAction(
      String functionName, KeyMapping keyMapping, ScriptFunctionArgList args) {
    args.expectSize(1);
    boolean pressed = args.getBoolean(0);
    pressKeyBind(keyMapping.getName(), pressed);
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

  private static final Optional<JsonElement> OPTIONAL_JSON_ZERO =
      Optional.of(new JsonPrimitive(0L));

  private static JsonElement jsonPrimitiveOrNull(Optional<String> s) {
    return s.map(str -> (JsonElement) new JsonPrimitive(str)).orElse(JsonNull.INSTANCE);
  }

  private record JobInfo(
      int job_id, String[] command, String source, String status, Boolean self) {}

  public static void processScriptFunction(
      Job job, String functionName, long funcCallId, List<?> parsedArgs) {
    var args = new ScriptFunctionArgList(functionName, parsedArgs);
    try {
      Optional<JsonElement> response = runScriptFunction(job, funcCallId, functionName, args);
      if (response.isPresent()) {
        job.respond(funcCallId, response.get(), true);
      }
      if (config.debugOutput()) {
        LOGGER.info(
            "(debug) Script function {} `{}`: {}  ->  {}",
            funcCallId,
            functionName,
            parsedArgs,
            response.map(JsonElement::toString).orElse("<no response>"));
      }
    } catch (Exception e) {
      if (funcCallId == 0) {
        // Functions with call ID 0 do not wait for return values and cannot catch
        // exceptions. So, log the exception to the chat and log file, attributed to
        // the job and function name.
        job.logJobException(
            new RuntimeException(
                String.format(
                    "Exception while calling script function `%s` with args %s: %s",
                    functionName, parsedArgs, e.toString())));
      } else {
        job.raiseException(funcCallId, ExceptionInfo.fromException(e));
      }
    }
  }

  private static Optional<JsonElement> registerEventHandler(
      Job job,
      String functionName,
      long funcCallId,
      Map<JobOperationId, EventHandler> handlerMap,
      Optional<Predicate<Object>> filter) {
    var jobOpId = new JobOperationId(job.jobId(), funcCallId);
    if (handlerMap.containsKey(jobOpId)) {
      throw new IllegalStateException(
          "Failed to create event handler because handler ID is already registered for job: "
              + job.jobSummary());
    }
    var handler = new EventHandler(job, functionName, () -> handlerMap.remove(jobOpId));
    filter.ifPresent(handler::setFilter);
    handlerMap.put(jobOpId, handler);
    return Optional.of(new JsonPrimitive(funcCallId));
  }

  private static Optional<JsonElement> startEventHandler(
      Job job, long funcCallId, Map<JobOperationId, EventHandler> handlerMap, long handlerId) {
    var jobOpId = new JobOperationId(job.jobId(), handlerId);
    var handler = handlerMap.get(jobOpId);
    if (handler == null) {
      throw new IllegalStateException(
          "Event handler not found with requested ID: " + jobOpId.toString());
    }
    job.addOperation(handlerId, handler);
    handler.start(funcCallId);
    return Optional.empty();
  }

  /** Returns a JSON response if a script function is called. */
  private static Optional<JsonElement> runScriptFunction(
      Job job, long funcCallId, String functionName, ScriptFunctionArgList args) throws Exception {
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

      case "player_name":
        args.expectSize(0);
        return Optional.of(new JsonPrimitive(player.getName().getString()));

      case "getblock":
        {
          args.expectSize(3);
          Level level = minecraft.level;
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
                        functionName, args));
              };

          args.expectSize(1);
          if (!(args.get(0) instanceof List)) {
            badArgsResponse.run();
          }
          List<?> positions = (List<?>) args.get(0);
          Level level = minecraft.level;
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

      case "register_key_listener":
        args.expectSize(0);
        return registerEventHandler(
            job, functionName, funcCallId, keyEventListeners, Optional.empty());

      case "start_key_listener":
        args.expectArgs("handler_id");
        return startEventHandler(job, funcCallId, keyEventListeners, args.getStrictLong(0));

      case "register_mouse_listener":
        args.expectSize(0);
        return registerEventHandler(
            job, functionName, funcCallId, mouseEventListeners, Optional.empty());

      case "start_mouse_listener":
        args.expectArgs("handler_id");
        return startEventHandler(job, funcCallId, mouseEventListeners, args.getStrictLong(0));

      case "register_chat_message_listener":
        args.expectSize(0);
        return registerEventHandler(
            job, functionName, funcCallId, chatEventListeners, Optional.empty());

      case "start_chat_message_listener":
        args.expectArgs("handler_id");
        return startEventHandler(job, funcCallId, chatEventListeners, args.getStrictLong(0));

      case "register_chat_message_interceptor":
        {
          args.expectArgs("prefix", "pattern");
          Optional<String> prefixArg = args.getOptionalString(0);
          Optional<String> patternArg = args.getOptionalString(1);
          if (prefixArg.isPresent() && patternArg.isPresent()) {
            throw new IllegalArgumentException(
                "Only one of `prefix` and `pattern` can be specified");
          }
          Optional<Predicate<Object>> filter = Optional.empty();
          if (prefixArg.isPresent()) {
            String prefix = prefixArg.get();
            filter = Optional.of(o -> o instanceof String s && s.startsWith(prefix));
          } else if (patternArg.isPresent()) {
            Pattern pattern = Pattern.compile(patternArg.get());
            filter = Optional.of(o -> o instanceof String s && pattern.matcher(s).matches());
          }
          return registerEventHandler(job, functionName, funcCallId, chatInterceptors, filter);
        }

      case "start_chat_message_interceptor":
        args.expectArgs("handler_id");
        return startEventHandler(job, funcCallId, chatInterceptors, args.getStrictLong(0));

      case "register_add_entity_listener":
        args.expectSize(0);
        return registerEventHandler(
            job, functionName, funcCallId, addEntityEventListeners, Optional.empty());

      case "start_add_entity_listener":
        args.expectArgs("handler_id");
        return startEventHandler(job, funcCallId, addEntityEventListeners, args.getStrictLong(0));

      case "register_block_update_listener":
        args.expectSize(0);
        return registerEventHandler(
            job, functionName, funcCallId, blockUpdateEventListeners, Optional.empty());

      case "start_block_update_listener":
        args.expectArgs("handler_id");
        return startEventHandler(job, funcCallId, blockUpdateEventListeners, args.getStrictLong(0));

      case "register_take_item_listener":
        args.expectSize(0);
        return registerEventHandler(
            job, functionName, funcCallId, takeItemEventListeners, Optional.empty());

      case "start_take_item_listener":
        args.expectArgs("handler_id");
        return startEventHandler(job, funcCallId, takeItemEventListeners, args.getStrictLong(0));

      case "register_damage_listener":
        args.expectSize(0);
        return registerEventHandler(
            job, functionName, funcCallId, damageEventListeners, Optional.empty());

      case "start_damage_listener":
        args.expectArgs("handler_id");
        return startEventHandler(job, funcCallId, damageEventListeners, args.getStrictLong(0));

      case "register_explosion_listener":
        args.expectSize(0);
        return registerEventHandler(
            job, functionName, funcCallId, explosionEventListeners, Optional.empty());

      case "start_explosion_listener":
        args.expectArgs("handler_id");
        return startEventHandler(job, funcCallId, explosionEventListeners, args.getStrictLong(0));

      case "register_chunk_listener":
        args.expectSize(0);
        return registerEventHandler(
            job, functionName, funcCallId, chunkEventListeners, Optional.empty());

      case "start_chunk_listener":
        args.expectArgs("handler_id");
        return startEventHandler(job, funcCallId, chunkEventListeners, args.getStrictLong(0));

      case "unregister_event_handler":
        {
          args.expectArgs("handler_id");
          long handlerId = args.getStrictLong(0);
          return Optional.of(new JsonPrimitive(job.cancelOperation(handlerId)));
        }

      case "await_loaded_region":
        {
          args.expectSize(4);
          int arg0 = args.getStrictInt(0);
          int arg1 = args.getStrictInt(1);
          int arg2 = args.getStrictInt(2);
          int arg3 = args.getStrictInt(3);
          var jobOpId = new JobOperationId(job.jobId(), funcCallId);
          var listener =
              new ChunkLoadEventListener(
                  arg0,
                  arg1,
                  arg2,
                  arg3,
                  (boolean success, boolean removeFromListeners) -> {
                    job.respond(funcCallId, new JsonPrimitive(success), true);
                    job.removeOperation(funcCallId);
                    if (removeFromListeners) {
                      chunkLoadEventListeners.remove(jobOpId);
                    }
                  });
          listener.updateChunkStatuses();
          if (!listener.checkFullyLoaded()) {
            job.addOperation(funcCallId, listener);
            chunkLoadEventListeners.put(jobOpId, listener);
          }
          return Optional.empty();
        }

      case "set_nickname":
        {
          args.expectSize(1);
          if (args.get(0) == null) {
            systemMessageQueue.logUserInfo(
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
            systemMessageQueue.logUserInfo("Chat nickname set to {}.", quoteString(arg));
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

      case "press_key_bind":
        args.expectSize(2);
        pressKeyBind(args.getString(0), args.getBoolean(1));
        return OPTIONAL_JSON_TRUE;

      case "player_press_forward":
        return doPlayerAction(functionName, options.keyUp, args);

      case "player_press_backward":
        return doPlayerAction(functionName, options.keyDown, args);

      case "player_press_left":
        return doPlayerAction(functionName, options.keyLeft, args);

      case "player_press_right":
        return doPlayerAction(functionName, options.keyRight, args);

      case "player_press_jump":
        return doPlayerAction(functionName, options.keyJump, args);

      case "player_press_sprint":
        return doPlayerAction(functionName, options.keySprint, args);

      case "player_press_sneak":
        return doPlayerAction(functionName, options.keyShift, args);

      case "player_press_pick_item":
        return doPlayerAction(functionName, options.keyPickItem, args);

      case "player_press_use":
        return doPlayerAction(functionName, options.keyUse, args);

      case "player_press_attack":
        return doPlayerAction(functionName, options.keyAttack, args);

      case "player_press_swap_hands":
        return doPlayerAction(functionName, options.keySwapOffhand, args);

      case "player_press_drop":
        return doPlayerAction(functionName, options.keyDrop, args);

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
            Level level = minecraft.level;
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

      case "player_get_targeted_entity":
        {
          args.expectArgs("max_distance", "nbt");
          double maxDistance = args.getDouble(0);
          boolean includeNbt = args.getBoolean(1);
          return Optional.of(
              DebugRenderer.getTargetedEntity(player, (int) maxDistance)
                  .map(e -> new EntityExporter(entityPositionInterpolation(), includeNbt).export(e))
                  .map(
                      obj -> {
                        JsonElement element = obj; // implicit cast
                        return element;
                      })
                  .orElse(JsonNull.INSTANCE));
        }

      case "player_health":
        return Optional.of(new JsonPrimitive(player.getHealth()));

      case "player":
        {
          args.expectArgs("nbt");
          boolean includeNbt = args.getBoolean(0);
          return Optional.of(
              new EntityExporter(entityPositionInterpolation(), includeNbt).export(player));
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
          boolean includeNbt = args.getBoolean(0);
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
              new EntityExporter(entityPositionInterpolation(), includeNbt)
                  .export(
                      new EntitySelection(
                              uuid,
                              name,
                              type,
                              position,
                              offset,
                              minDistance,
                              maxDistance,
                              sort,
                              limit)
                          .selectFrom(world.players())));
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
          boolean includeNbt = args.getBoolean(0);
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
              new EntityExporter(entityPositionInterpolation(), includeNbt)
                  .export(
                      new EntitySelection(
                              uuid,
                              name,
                              type,
                              position,
                              offset,
                              minDistance,
                              maxDistance,
                              sort,
                              limit)
                          .selectFrom(world.entitiesForRendering())));
        }

      case "version_info":
        {
          args.expectSize(0);

          var result = new JsonObject();
          result.addProperty("minecraft", SharedConstants.getCurrentVersion().getName());
          result.addProperty("minescript", Minescript.version);
          result.addProperty("mod_loader", platform.modLoaderName());
          result.addProperty("launcher", minecraft.getLaunchedVersion());
          result.addProperty("os_name", System.getProperty("os.name"));
          result.addProperty("os_version", System.getProperty("os.version"));
          result.addProperty("minecraft_class_name", Minecraft.class.getName());
          return Optional.of(result);
        }

      case "world_info":
        {
          args.expectSize(0);
          var levelProperties = world.getLevelData();
          var difficulty = levelProperties.getDifficulty();
          var serverData = minecraft.getCurrentServer();
          var serverAddress = serverData == null ? "localhost" : serverData.ip;

          var spawn = new JsonArray();
          var spawnPos = levelProperties.getSpawnPos();
          spawn.add(spawnPos.getX());
          spawn.add(spawnPos.getY());
          spawn.add(spawnPos.getZ());

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

      case "echo_json":
        {
          args.expectArgs("json_text");
          var message = args.getString(0);
          processJsonFormattedText(message);
          return Optional.empty();
        }

      case "echo":
        {
          // Try to parse as a single string arg, and if that fails, fall back to string list.
          String message;
          try {
            args.expectSize(1);
            message = args.getString(0);
          } catch (IllegalArgumentException e) {
            message =
                String.join(
                    " ",
                    args.args().stream()
                        .map(Object::toString)
                        .collect(Collectors.toList())
                        .toArray(String[]::new));
          }

          processPlainText(message);
          return Optional.empty();
        }

      case "chat":
        {
          // Try to parse as a single string arg, and if that fails, fall back to string list.
          String message;
          try {
            args.expectSize(1);
            message = args.getString(0);
          } catch (IllegalArgumentException e) {
            message =
                String.join(
                    " ",
                    args.args().stream()
                        .map(Object::toString)
                        .collect(Collectors.toList())
                        .toArray(String[]::new));
          }

          // If the message starts with a slash or backslash, prepend a space so that it's printed
          // and not executed as a command.
          if (message.length() > 0) {
            char firstLetter = message.charAt(0);
            if (firstLetter == '\\' || firstLetter == '/') {
              message = " " + message;
            }
          }

          processChatMessage(message);
          return Optional.empty();
        }

      case "log":
        {
          // Try to parse as a single string arg, and if that fails, fall back to string list.
          String message;
          try {
            args.expectSize(1);
            message = args.getString(0);
          } catch (IllegalArgumentException e) {
            message =
                String.join(
                    " ",
                    args.args().stream()
                        .map(Object::toString)
                        .collect(Collectors.toList())
                        .toArray(String[]::new));
          }

          LOGGER.info(message);
          return Optional.empty();
        }

      case "screenshot":
        {
          args.expectArgs("filename");
          String filename = args.get(0) == null ? null : args.getString(0);

          if (filename != null) {
            if (filename.contains(File.separator)) {
              throw new IllegalArgumentException(
                  String.format(
                      "`screenshot` does not support filenames with `%s` character.",
                      File.separator));
            }
            int length = filename.length();
            if (length > 4 && !filename.substring(length - 4).toLowerCase().equals(".png")) {
              filename += ".png";
            }
          }

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
          long key = job.blockpacks.retain(blockpack);
          return Optional.of(new JsonPrimitive(key));
        }

      case "blockpack_read_file":
        {
          // Python function signature:
          //    (filename: str) -> int
          args.expectSize(1);
          String blockpackFilename = args.getString(0);
          var blockpack = BlockPack.readZipFile(blockpackFilename);
          long key = job.blockpacks.retain(blockpack);
          return Optional.of(new JsonPrimitive(key));
        }

      case "blockpack_import_data":
        {
          // Python function signature:
          //    (base64_data: str) -> int
          args.expectSize(1);
          String base64Data = args.getString(0);
          var blockpack = BlockPack.fromBase64EncodedString(base64Data);
          long key = job.blockpacks.retain(blockpack);
          return Optional.of(new JsonPrimitive(key));
        }

      case "blockpack_block_bounds":
        {
          // Python function signature:
          //    (blockpack_id) -> Tuple[Tuple[int, int, int], Tuple[int, int, int]]
          args.expectSize(1);
          long blockpackId = args.getStrictLong(0);
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
          long blockpackId = args.getStrictLong(0);
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

          long blockpackId = args.getStrictLong(0);

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
              rotation, offset, s -> job.tickQueue().add(Message.createMinecraftCommand(s)));
          return OPTIONAL_JSON_TRUE;
        }

      case "blockpack_write_file":
        {
          // Python function signature:
          //    (blockpack_id: int, filename: str) -> bool
          args.expectSize(2);
          long blockpackId = args.getStrictLong(0);
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
          long blockpackId = args.getStrictLong(0);
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
          long blockpackId = args.getStrictLong(0);
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
          long blockpackerId = args.getStrictLong(0);
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

          long blockpackerId = args.getStrictLong(0);
          long blockpackId = args.getStrictLong(1);

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
          long blockpackerId = args.getStrictLong(0);
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
          long blockpackerId = args.getStrictLong(0);

          var blockpacker = job.blockpackers.releaseById(blockpackerId);
          if (blockpacker == null) {
            throw new IllegalStateException(
                String.format("`%s` failed to find BlockPacker[%d]", functionName, blockpackerId));
          }
          return OPTIONAL_JSON_TRUE;
        }

      case "screen_name":
        if (!args.isEmpty()) {
          throw new IllegalArgumentException("Expected no params but got: " + args.toString());
        }
        return Optional.of(jsonPrimitiveOrNull(getScreenName()));

      case "container_get_items": // List of Items in Chest
        {
          if (!args.isEmpty()) {
            throw new IllegalArgumentException("Expected no params but got: " + args.toString());
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

      case "player_look_at": // Look at x, y, z
        {
          args.expectSize(3);
          double x = args.getDouble(0);
          double y = args.getDouble(1);
          double z = args.getDouble(2);
          player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(x, y, z));
          return OPTIONAL_JSON_TRUE;
        }

      case "show_chat_screen":
        {
          args.expectSize(2);
          boolean show = args.getBoolean(0);
          var screen = minecraft.screen;
          final Optional<JsonElement> result;
          if (show) {
            if (screen == null) {
              minecraft.setScreen(new ChatScreen(""));
            }
            var prompt = args.get(1);
            if (prompt != null && checkChatScreenInput()) {
              chatEditBox.setValue(prompt.toString());
            }
            result = OPTIONAL_JSON_TRUE;
          } else {
            if (screen != null && screen instanceof ChatScreen) {
              screen.onClose();
              result = OPTIONAL_JSON_TRUE;
            } else {
              result = OPTIONAL_JSON_FALSE;
            }
          }
          return result;
        }

      case "job_info":
        {
          args.expectSize(0);
          var result = new ArrayList<JobInfo>();
          jobs.getMap().entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .map(Map.Entry::getValue)
              .forEach(
                  j -> {
                    Path path = j.boundCommand().scriptPath();
                    result.add(
                        new JobInfo(
                            j.jobId(),
                            j.boundCommand().command(),
                            path == null ? null : path.toString(),
                            j.state().name(),
                            j == job ? Boolean.valueOf(true) : null));
                  });
          // Use default-constructed Gson (instead of GSON) so that nulls are not serialized.
          return Optional.of(new Gson().toJsonTree(result));
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
              args,
              job.jobSummary());
          return cancelfnRetval;
        }
        long funcIdToCancel = ((Number) args.get(0)).longValue();
        String funcName = (String) args.get(1);
        if (job.cancelOperation(funcIdToCancel)) {
          LOGGER.info(
              "Cancelled function call {} for \"{}\" in job: {}",
              funcIdToCancel,
              funcName,
              job.jobSummary());
        } else {
          LOGGER.warn(
              "Failed to find operation to cancel: funcCallId {} for \"{}\" in job: {}",
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

      case "append_chat_history":
        args.expectSize(1);
        minecraft.gui.getChat().addRecentChat(args.getString(0));
        return OPTIONAL_JSON_NULL;

      case "chat_input":
        {
          args.expectSize(0);
          var result = new JsonArray();
          result.add(chatEditBox.getValue());
          result.add(chatEditBox.getCursorPosition());
          return Optional.of(result);
        }

      case "set_chat_input":
        {
          args.expectSize(3);
          args.getOptionalString(0).ifPresent(chatEditBox::setValue);
          args.getOptionalStrictInt(1).ifPresent(chatEditBox::setCursorPosition);
          args.getOptionalStrictInt(2).ifPresent(chatEditBox::setTextColor);
          return OPTIONAL_JSON_NULL;
        }

      case "java_string":
        args.expectSize(1);
        return Optional.of(new JsonPrimitive(job.objects.retain(args.getString(0))));

      case "java_float":
        args.expectSize(1);
        return Optional.of(
            new JsonPrimitive(job.objects.retain(Float.valueOf((float) args.getDouble(0)))));

      case "java_double":
        args.expectSize(1);
        return Optional.of(new JsonPrimitive(job.objects.retain(args.getDouble(0))));

      case "java_long":
        args.expectSize(1);
        return Optional.of(new JsonPrimitive(job.objects.retain(args.getStrictLong(0))));

      case "java_int":
        args.expectSize(1);
        return Optional.of(new JsonPrimitive(job.objects.retain(args.getStrictInt(0))));

      case "java_bool":
        args.expectSize(1);
        return Optional.of(new JsonPrimitive(job.objects.retain(args.getBoolean(0))));

      case "java_class":
        {
          args.expectSize(1);
          var object = Class.forName(args.getString(0));
          return Optional.of(new JsonPrimitive(job.objects.retain(object)));
        }

      case "java_ctor":
        {
          args.expectSize(1);
          var target = (Class) job.objects.getById(args.getStrictLong(0));
          var ctors = new ImmutableList.Builder<Constructor>();
          var argCounts = new ImmutableSet.Builder<Integer>();
          for (var ctor : target.getConstructors()) {
            ctors.add(ctor);
            argCounts.add(ctor.getParameterCount());
          }
          var ctorSet = new ConstructorSet(target.getName(), ctors.build(), argCounts.build());
          return Optional.of(new JsonPrimitive(job.objects.retain(ctorSet)));
        }

      case "java_new_instance":
        {
          var ctorSet = (ConstructorSet) job.objects.getById(args.getStrictLong(0));
          Object[] params = new Object[args.size() - 1];
          for (int i = 0; i < params.length; ++i) {
            params[i] = job.objects.getById(args.getStrictLong(i + 1));
          }
          if (!ctorSet.argCounts().contains(params.length)) {
            throw new IllegalArgumentException(
                String.format(
                    "Constructor for `%s` got %d args but expected %s",
                    ctorSet.name(), params.length, ctorSet.argCounts()));
          }
          // Catch IllegalArgumentException until all the ctors in the set with the right number
          // of args have been exhausted.
          IllegalArgumentException exception = null;
          for (Constructor ctor : ctorSet.ctors()) {
            if (ctor.getParameterCount() == params.length) {
              try {
                var result = ctor.newInstance(params);
                return Optional.of(new JsonPrimitive(job.objects.retain(result)));
              } catch (IllegalArgumentException e) {
                exception = e;
              }
            }
          }
          throw exception;
        }

      case "java_member":
        {
          args.expectSize(2);
          String memberName = args.getString(1);
          var target = (Class) job.objects.getById(args.getStrictLong(0));
          Optional<Field> field;
          try {
            field = Optional.of(target.getField(memberName));
          } catch (NoSuchFieldException e) {
            field = Optional.empty();
          }
          var methods = new ImmutableList.Builder<Method>();
          var argCounts = new ImmutableSet.Builder<Integer>();
          for (var method : target.getMethods()) {
            if (method.getName().equals(memberName)) {
              methods.add(method);
              argCounts.add(method.getParameterCount());
            }
          }
          var memberSet = new MemberSet(memberName, field, methods.build(), argCounts.build());
          if (!memberSet.methods().isEmpty() || !memberSet.field().isEmpty()) {
            return Optional.of(new JsonPrimitive(job.objects.retain(memberSet)));
          }
          throw new IllegalArgumentException("Method not found: " + memberName);
        }

      case "java_call_method":
        {
          var target = (Object) job.objects.getById(args.getStrictLong(0));
          var memberSet = (MemberSet) job.objects.getById(args.getStrictLong(1));
          Object[] params = new Object[args.size() - 2];
          for (int i = 0; i < params.length; ++i) {
            params[i] = job.objects.getById(args.getStrictLong(i + 2));
          }
          // Catch IllegalArgumentException until all the methods in the set with the right number
          // of args have been exhausted.
          for (Method method : memberSet.methods()) {
            if (method.getParameterCount() == params.length) {
              try {
                var result = method.invoke(target, params);
                return Optional.of(new JsonPrimitive(job.objects.retain(result)));
              } catch (IllegalArgumentException e) {
              } catch (InvocationTargetException e) {
                throw new InvocationTargetException(
                    e,
                    String.format(
                        "Error invoking `%s` on `%s` from `java_call_method`: %s",
                        method.getName(), target, e.getCause()));
              }
            }
          }
          // Fell through without successfully invoking a matching method. Throw an exception that
          // reports the signatures of all methods in the overload set.
          var paramTypes = new ArrayList<String>();
          for (int i = 0; i < params.length; ++i) {
            var param = params[i];
            paramTypes.add(param == null ? "null" : param.getClass().getName());
          }
          var signatures = new ArrayList<String>();
          for (Method method : memberSet.methods()) {
            signatures.add(method.toString());
          }
          throw new IllegalArgumentException(
              String.format(
                  "No matching methods for %s(%s):\n%s",
                  memberSet.name(),
                  String.join(", ", paramTypes.toArray(String[]::new)),
                  String.join("\n", signatures.toArray(String[]::new))));
        }

      case "java_access_field":
        {
          var target = (Object) job.objects.getById(args.getStrictLong(0));
          var memberSet = (MemberSet) job.objects.getById(args.getStrictLong(1));
          var field = memberSet.field();
          if (field.isEmpty()) {
            throw new NoSuchFieldException(String.format("No field named `%s`", memberSet.name()));
          }
          var result = field.get().get(target);
          return Optional.of(new JsonPrimitive(job.objects.retain(result)));
        }

      case "java_array_length":
        {
          args.expectSize(1);
          var array = (Object[]) job.objects.getById(args.getStrictLong(0));
          return Optional.of(new JsonPrimitive(array.length));
        }

      case "java_array_index":
        {
          args.expectSize(2);
          var array = (Object[]) job.objects.getById(args.getStrictLong(0));
          int index = args.getStrictInt(1);
          var result = array[index];
          return Optional.of(new JsonPrimitive(job.objects.retain(result)));
        }

      case "java_to_string":
        {
          args.expectSize(1);
          var object = job.objects.getById(args.getStrictLong(0));
          return Optional.of(new JsonPrimitive(object == null ? "null" : object.toString()));
        }

      case "java_assign":
        {
          args.expectArgs("dest", "source");
          long dest = args.getStrictLong(0);
          long source = args.getStrictLong(1);
          job.objects.reassignId(dest, job.objects.getById(source));
          return OPTIONAL_JSON_NULL;
        }

      case "java_release":
        for (var arg : args.args()) {
          // For convenience, don't complain if a script attempts to release ID 0 which represents
          // null. This allows scripts to call Java methods and access Java fields with a null value
          // without requiring scripts to handle 0/null conditionally.
          long id = ScriptFunctionArgList.getStrictLongValue(arg).getAsLong();
          if (id != 0) {
            job.objects.releaseById(id);
          }
        }
        return OPTIONAL_JSON_NULL;

      case "run_tasks":
        return Optional.of(runTasks(job, args.args()));

      case "schedule_tick_tasks":
        return Optional.of(scheduleTasks(job, funcCallId, tickTaskLists, args.args()));

      case "schedule_render_tasks":
        return Optional.of(scheduleTasks(job, funcCallId, renderTaskLists, args.args()));

      case "cancel_scheduled_tasks":
        {
          args.expectArgs("task_list_id");
          long opId = args.getStrictLong(0);
          return Optional.of(new JsonPrimitive(job.cancelOperation(opId)));
        }

      default:
        throw new IllegalArgumentException(
            String.format(
                "Unknown function `%s` called from job: %s", functionName, job.jobSummary()));
    }
  }

  private static JsonElement scheduleTasks(
      Job job, long funcCallId, Map<JobOperationId, ScheduledTaskList> taskLists, List<?> tasks)
      throws Exception {
    var jobOpId = new JobOperationId(job.jobId(), funcCallId);
    if (taskLists.containsKey(jobOpId)) {
      throw new IllegalStateException(
          String.format("Task ID %d already scheduled for job %d", funcCallId, job.jobId()));
    }

    var scheduledTaskList =
        new ScheduledTaskList(
            job,
            String.format(
                "TaskList command=%s job=%d id=%d len=%d",
                job.boundCommand().command()[0], job.jobId(), funcCallId, tasks.size()),
            tasks,
            () -> taskLists.remove(jobOpId));
    taskLists.put(jobOpId, scheduledTaskList);
    job.addOperation(funcCallId, scheduledTaskList);
    return new JsonPrimitive(funcCallId);
  }

  enum TaskFlowControl {
    NORMAL_FLOW, // Control flows sequentially from one task to the next in a list.
    SKIP_TASKS // The remaining tasks in the list are skipped.
  }

  private static JsonElement runTasks(Job job, List<?> tasks) throws Exception {
    var taskValues = new HashMap<Long, Supplier<Object>>();
    JsonElement tasksResult = JsonNull.INSTANCE;
    TaskFlowControl[] flowControl = {TaskFlowControl.NORMAL_FLOW};
    for (var task : tasks) {
      var args = (List<?>) task;
      var result = runTask(job, args, taskValues, flowControl);
      taskValues.put(
          ScriptFunctionArgList.getStrictLongValue(args.get(0)).getAsLong(),
          Suppliers.memoize(
              () -> {
                // Gson doesn't appear to offer a way to parse JsonElement directly to a POJO. The
                // workaround used here is to put the JsonElement into a JsonArray, then parse that
                // as generic ArrayList and pull out the first element as Object.
                var array = new JsonArray();
                array.add(result);
                return GSON.fromJson(array, ArrayList.class).get(0);
              }));
      tasksResult = result;
      if (flowControl[0] == TaskFlowControl.SKIP_TASKS) {
        break;
      }
    }
    // Return the result of the last executed task.
    return tasksResult;
  }

  // flowControl is an array of length 1 to allow an output value without altering the return type.
  private static JsonElement runTask(
      Job job,
      List<?> argList,
      Map<Long, Supplier<Object>> taskValues,
      TaskFlowControl[] flowControl)
      throws Exception {
    var args = new ScriptFunctionArgList("runTask", argList);
    long funcCallId = args.getStrictLong(0);
    String funcName = args.getString(1);

    List resolvedArgs = (List) args.get(2);
    List<?> deferredArgs = (List<?>) args.get(3);

    for (int i = 0; i < deferredArgs.size(); ++i) {
      Object arg = deferredArgs.get(i);
      if (arg != null) {
        long taskId = ScriptFunctionArgList.getStrictLongValue(arg).getAsLong();
        if (!taskValues.containsKey(taskId)) {
          throw new IllegalArgumentException(
              String.format(
                  "Task `%s` accessed uninitialized result of Task ID %d", funcName, taskId));
        }
        resolvedArgs.set(i, taskValues.get(taskId).get());
      }
    }

    switch (funcName) {
      case "as_list":
        return GSON.toJsonTree(resolvedArgs);

      case "get_attr":
        {
          var map = (Map) resolvedArgs.get(0);
          var attr = resolvedArgs.get(1);
          return GSON.toJsonTree(map.get(attr));
        }

      case "get_index":
        {
          var list = (List) resolvedArgs.get(0);
          var index = ScriptFunctionArgList.getStrictIntValue(resolvedArgs.get(1));
          if (index.isEmpty()) {
            throw new IllegalArgumentException(
                "Expected second arg to `get_index` to be int but got: " + resolvedArgs.get(1));
          }
          return GSON.toJsonTree(list.get(index.getAsInt()));
        }

      case "contains":
        {
          var container = resolvedArgs.get(0);
          var element = resolvedArgs.get(1);
          final boolean found;
          if (container instanceof List list) {
            found = list.contains(element);
          } else if (container instanceof Map map) {
            found = map.containsKey(element);
          } else if (container instanceof String string) {
            found = string.contains(element.toString());
          } else {
            throw new IllegalArgumentException(
                String.format(
                    "Expected first arg (container) to be List, Map, or String but got: %s (%s)",
                    container, container.getClass().getName()));
          }
          return new JsonPrimitive(found);
        }

      case "as_int":
        {
          var arg = resolvedArgs.get(0);
          if (arg instanceof List list) {
            var ints = new JsonArray();
            for (int i = 0; i < list.size(); ++i) {
              var item = list.get(i);
              OptionalDouble number = ScriptFunctionArgList.getDoubleValue(item);
              if (number.isEmpty()) {
                throw new IllegalArgumentException(
                    String.format(
                        "Expected arg to `as_int` to be a number or list of numbers but got `%s` at"
                            + " index `%d`",
                        item, i));
              }
              ints.add((int) number.getAsDouble());
            }
            return ints;
          } else {
            OptionalDouble number = ScriptFunctionArgList.getDoubleValue(arg);
            if (number.isEmpty()) {
              throw new IllegalArgumentException(
                  "Expected arg to `as_int` to be a number or list of numbers but got: " + arg);
            }
            return new JsonPrimitive((int) number.getAsDouble());
          }
        }

      case "negate":
        {
          var arg = resolvedArgs.get(0);
          if (arg instanceof Boolean condition) {
            return new JsonPrimitive(!condition);
          } else {
            throw new IllegalArgumentException(
                "Expected arg to `negate` to be a boolean but got: " + arg);
          }
        }

      case "is_null":
        {
          var arg = resolvedArgs.get(0);
          return new JsonPrimitive(arg == null);
        }

      case "skip_if":
        {
          var arg = resolvedArgs.get(0);
          if (arg instanceof Boolean condition) {
            if (condition) {
              flowControl[0] = TaskFlowControl.SKIP_TASKS;
            }
            return new JsonPrimitive(condition);
          } else {
            throw new IllegalArgumentException(
                "Expected arg to `negate` to be a boolean but got: " + arg);
          }
        }

      default:
        var embeddedArgs = new ScriptFunctionArgList(funcName, resolvedArgs);
        return runScriptFunction(job, funcCallId, funcName, embeddedArgs).orElse(JsonNull.INSTANCE);
    }
  }

  private record ConstructorSet(
      String name, ImmutableList<Constructor> ctors, ImmutableSet<Integer> argCounts) {}

  private record MemberSet(
      String name,
      Optional<Field> field,
      ImmutableList<Method> methods,
      ImmutableSet<Integer> argCounts) {}

  public static void handleAutorun(String worldName) {
    LOGGER.info("Handling autorun for world `{}`", worldName);
    var commands = new ArrayList<Message>();

    var wildcardCommands = config.getAutorunCommands("*");
    if (wildcardCommands != null) {
      LOGGER.info(
          "Matched {} command(s) with autorun[*] for world `{}`",
          wildcardCommands.size(),
          worldName);
      commands.addAll(wildcardCommands);
    }

    var worldCommands = config.getAutorunCommands(worldName);
    if (worldCommands != null) {
      LOGGER.info("Matched {} command(s) with autorun[{}]", wildcardCommands.size(), worldName);
      commands.addAll(worldCommands);
    }

    for (var command : commands) {
      LOGGER.info("Running autorun command for world `{}`: {}", worldName, command);
      processMessage(command);
    }
  }

  private static volatile long lastTickStartTime = 0;

  private static double entityPositionInterpolation() {
    var minecraft = Minecraft.getInstance();
    double millisPerTick = minecraft.level.tickRateManager().millisecondsPerTick();
    long now = System.currentTimeMillis();
    return Math.min(1., (now - lastTickStartTime) / millisPerTick);
  }

  public static void onClientWorldTick() {
    for (var taskList : tickTaskLists.values()) {
      taskList.run();
    }

    lastTickStartTime = System.currentTimeMillis();
    if (++clientTickEventCounter % config.ticksPerCycle() == 0) {
      var minecraft = Minecraft.getInstance();
      var player = minecraft.player;

      String worldName = getWorldName();
      if (!autorunHandled.getAndSet(true) && worldName != null) {
        systemMessageQueue.clear();
        config.load();
        handleAutorun(worldName);
      }

      if (player != null && (!systemMessageQueue.isEmpty() || !jobs.getMap().isEmpty())) {
        processMessageQueue(true /* processSystemMessages */, job -> job.tickQueue().poll());
      }
    }
  }

  public static void processMessageQueue(
      boolean processSystemMessages, Function<Job, Message> jobMessageQueue) {
    var minecraft = Minecraft.getInstance();
    boolean hasMessage;
    int iterations = 0;
    long loopStartTimeUsecs = System.nanoTime() / 1000;
    do {
      hasMessage = false;
      ++iterations;
      if (processSystemMessages) {
        Message sysMessage = systemMessageQueue.poll();
        if (sysMessage != null) {
          hasMessage = true;
          processMessage(sysMessage);
        }
      }
      for (var job : jobs.getMap().values()) {
        if (job.state() == JobState.RUNNING || job.state() == JobState.DONE) {
          try {
            Message message = jobMessageQueue.apply(job);
            if (message != null) {
              hasMessage = true;
              if (message.type() == Message.Type.FUNCTION_CALL) {
                String functionName = message.value();
                var funcCallData = (Message.FunctionCallData) message.data();
                processScriptFunction(
                    job, functionName, funcCallData.funcCallId(), funcCallData.args());

              } else {
                // TODO(maxuser): Stash level in UndoableAction as a WeakReference<Level> so
                // that an undo operation gets applied only to the world that at existed at the
                // time the UndoableAction was created.
                Level level = minecraft.level;
                jobs.getUndoForJob(job).ifPresent(u -> u.processCommandToUndo(level, message));
                processMessage(message);
              }
            }
          } catch (RuntimeException e) {
            job.logJobException(e);
          }
        }
      }
    } while (hasMessage
        && iterations < config.maxCommandsPerCycle()
        && System.nanoTime() / 1000 - loopStartTimeUsecs < config.commandCycleDeadlineUsecs());

    /*
    if (config.debugOutput()) {
      long elapsedUsecs = System.nanoTime() / 1000 - loopStartTimeUsecs;
      if (elapsedUsecs >= config.commandCycleDeadlineUsecs()) {
        LOGGER.info(
            "Tick loop processed {} iterations in {} usecs (exceeded deadline of {} usecs)",
            iterations,
            elapsedUsecs,
            config.commandCycleDeadlineUsecs());
      } else {
        LOGGER.info("Tick loop processed {} iterations in {} usecs", iterations, elapsedUsecs);
      }
    }
    */
  }
}
