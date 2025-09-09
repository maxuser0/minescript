// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

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
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.JsonOps;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
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
import java.util.OptionalLong;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
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
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
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
import net.minescript.common.CommandSyntax.Token;
import net.minescript.common.blocks.BlockPositionReader;
import net.minescript.common.blocks.BlockSequenceReader;
import net.minescript.common.dataclasses.*;
import net.minescript.common.events.*;
import net.minescript.common.mappings.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pyjinn.interpreter.Script;

public class Minescript {
  static final Logger LOGGER = LogManager.getLogger();

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
  public static MappingsLoader mappingsLoader;

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
    Minescript.version = getCurrentVersion();
    if (!version.equals(lastRunVersion)) {
      LOGGER.info(
          "Current version ({}) does not match last run version ({})",
          Minescript.version,
          lastRunVersion);

      LOGGER.info("Deleting files from version `{}` of Minescript...", lastRunVersion);
      deleteObsoleteFiles();

      LOGGER.info(
          "Loading files for current version `{}` of Minescript from jar resources...", version);
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

    boolean isMinecraftClassObfuscated =
        !Minecraft.class.getName().equals("net.minecraft.client.Minecraft");
    mappingsLoader =
        new MappingsLoader(
            SharedConstants.getCurrentVersion().name(),
            platform.modLoaderName(),
            isMinecraftClassObfuscated);
    try {
      mappingsLoader.load();
    } catch (Exception e) {
      LOGGER.error("Error loading mappings: {}", e.toString());
    }
  }

  public static void reloadMappings() throws Exception {
    mappingsLoader.load();
  }

  // TODO(maxuser): Allow this to be controlled via config.
  public static void enableDebugPyjinnLogging(boolean enable) {
    if (enable) {
      Script.setDebugLogger(
          (message, args) -> LOGGER.info("Pyjinn debug output: " + message.formatted(args)));
    } else {
      Script.setDebugLogger((message, args) -> {});
    }
  }

  private static void deleteObsoleteFiles() {
    Path minescriptDir = Paths.get(System.getProperty("user.dir"), MINESCRIPT_DIR);
    Path systemDir = minescriptDir.resolve("system");
    Path libDir = systemDir.resolve("lib");
    Path execDir = systemDir.resolve("exec");

    // Delete files that used to be stored directly within the `minescript` dir in legacy versions.
    deleteMinescriptFile(minescriptDir, "version.txt");
    deleteMinescriptFile(minescriptDir, "minescript.py");
    deleteMinescriptFile(libDir, "minescript.pyj"); // File appeared here before 5.0a4
    deleteMinescriptFile(minescriptDir, "minescript_runtime.py");
    deleteMinescriptFile(minescriptDir, "help.py");
    deleteMinescriptFile(minescriptDir, "copy.py");
    deleteMinescriptFile(minescriptDir, "paste.py");
    deleteMinescriptFile(minescriptDir, "eval.py");

    // Delete Python files replaced by Pyjinn equivalents in v5.0.
    deleteMinescriptFile(execDir, "eval.py");
  }

  private static void loadMinescriptResources() {
    Path systemDir = Paths.get(MINESCRIPT_DIR, "system");
    Path libDir = systemDir.resolve("lib");
    Path pyjDir = systemDir.resolve("pyj");
    Path execDir = systemDir.resolve("exec");

    new File(libDir.toString()).mkdirs();
    new File(pyjDir.toString()).mkdirs();
    new File(execDir.toString()).mkdirs();

    copyJarResourceToFile("version.txt", systemDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("system/lib/minescript.py", libDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("system/lib/java.py", libDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile(
        "system/lib/minescript_runtime.py", libDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("system/pyj/minescript.py", pyjDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("system/pyj/sys.py", pyjDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("system/pyj/json.py", pyjDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("system/pyj/pathlib.py", pyjDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("system/pyj/atexit.py", pyjDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("system/exec/help.py", execDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("system/exec/copy_blocks.py", execDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("system/exec/paste.py", execDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile(
        "system/exec/install_mappings.pyj", execDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("system/exec/eval.pyj", execDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("system/exec/pyeval.py", execDir, FileOverwritePolicy.OVERWRITTE);
    copyJarResourceToFile("system/exec/pyinterpreter.py", execDir, FileOverwritePolicy.OVERWRITTE);
  }

  private static void deleteMinescriptFile(Path dir, String fileName) {
    var fileToDelete = new File(dir.resolve(fileName).toString());
    if (fileToDelete.exists()) {
      if (fileToDelete.delete()) {
        LOGGER.info("Deleted obsolete file: `{}`", fileToDelete.getPath());
      }
    }
  }

  private static void cancelOrphanedOperations(
      Set<Integer> jobIdsToKeep, EventDispatcher eventDispatcher) {
    if (!eventDispatcher.isEmpty()) {
      LOGGER.info(
          "Cancelling orphaned operations when exiting world (excluding jobs with 'world' event"
              + " listeners): {}",
          eventDispatcher.entrySet().stream()
              .filter(e -> !jobIdsToKeep.contains(e.getValue().jobId()))
              .map(e -> String.format("%s %s", e.getValue().name(), e.getKey()))
              .collect(Collectors.toList()));

      // Stash values in a new list so that the operations map isn't modified while being
      // iterated/streamed, because Job.Operation::cancel removes a listener from the map.
      new ArrayList<>(eventDispatcher.values())
          .stream()
              .forEach(
                  op -> {
                    if (!jobIdsToKeep.contains(op.jobId())) {
                      op.cancel();
                    }
                  });
    }
  }

  private static void killAllJobs() {
    // Find all the jobs that have "world" event listeners and keep them alive.
    var jobIdsToKeep =
        new HashSet<>(worldListeners.keySet().stream().map(key -> key.jobId()).toList());

    for (var job : jobs.getMap().values()) {
      if (jobIdsToKeep.contains(job.jobId())) {
        LOGGER.info(
            "Keeping job alive because it has a world event listener: {}", job.jobSummary());
      } else {
        LOGGER.info("Killing job that has no world event listener: {}", job.jobSummary());
        job.requestKill();
      }
    }

    // Job operations should have been cleaned up when killing jobs, but the jobs killed above might
    // still be in the process of shutting down, or operations may have been leaked accidentally.
    // Clear any leaked operations here.
    for (var dispatcher : eventDispatchers) {
      cancelOrphanedOperations(jobIdsToKeep, dispatcher);
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
          // TODO(maxuser): It's not safe to call these listeners outside of the render thread. Can
          // this be processed on the render thread even when there's no current world? Where would
          // job stdout and stderr show up? Redirected to the log file?
          onWorldConnectionChange(false);
          autorunHandled.set(false);
          LOGGER.info("Exited world");
          killAllJobs();
          systemMessageQueue.clear();
        } else {
          LOGGER.info("Entered world");
          onWorldConnectionChange(true);
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

  private static void onWorldConnectionChange(boolean connected) {
    ScriptValue eventValue = null;
    for (var listener : worldListeners.values()) {
      if (listener.isActive()) {
        if (eventValue == null) {
          var event = new WorldEvent();
          event.connected = connected;
          event.time = System.currentTimeMillis() / 1000.;
          eventValue = ScriptValue.of(event);
        }
        listener.respond(eventValue);
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
      String resourcePath, Path dir, FileOverwritePolicy overwritePolicy) {
    // The rhs expression works even when '/' isn't found, because -1 + 1 = 0.
    String filename = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
    copyJarResourceToFile(resourcePath, dir, filename, overwritePolicy);
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
          "reload_mappings",
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
        String block =
            BlockPositionReader.getBlockStateString(
                level, pos.set(coords[0], coords[1], coords[2]));
        if (block != null) {
          if (!addBlockToUndoQueue(coords[0], coords[1], coords[2], block)) {
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
              String block = BlockPositionReader.getBlockStateString(level, pos.set(x, y, z));
              if (block != null) {
                if (!addBlockToUndoQueue(x, y, z, block)) {
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

  static class UndoTask implements Task {
    private final UndoableAction undo;

    public UndoTask(UndoableAction undo) {
      this.undo = undo;
    }

    @Override
    public int run(ScriptConfig.BoundCommand command, JobControl job) {
      undo.enqueueCommands(job.tickQueue());
      return 0;
    }
  }

  static class JobManager {
    private final Map<Integer, JobControl> jobMap = new ConcurrentHashMap<>();
    private int nextJobId = 1;

    // Map from ID of original job (not an undo) to its corresponding undo, if applicable.
    private final Map<Integer, UndoableAction> jobUndoMap =
        new ConcurrentHashMap<Integer, UndoableAction>();

    private final Deque<UndoableAction> undoStack = new ArrayDeque<>();

    public void createPyjinnJob(ScriptConfig.BoundCommand command, List<Token> nextCommand) {
      try {
        String scriptCode = Files.readString(command.scriptPath());
        int jobId = allocateJobId();
        var job =
            PyjinnScript.createJob(
                jobId,
                /* parentJobId= */ Optional.empty(),
                command,
                scriptCode,
                config,
                systemMessageQueue,
                mappingsLoader.get(),
                /* autoExit= */ true,
                () -> finishJob(jobId, nextCommand));
        jobMap.put(job.jobId(), job);
        job.start();
      } catch (Exception e) {
        systemMessageQueue.logException(e);
      }
    }

    public Script createPyjinnSubjob(
        Job.SubprocessJob parentJob, long opId, String scriptName, String scriptCode)
        throws Exception {
      var parentCommand = parentJob.boundCommand();
      var childCommand =
          new ScriptConfig.BoundCommand(
              parentCommand.scriptPath(), new String[] {scriptName}, parentCommand.redirects());
      int jobId = allocateJobId();
      var scriptJob =
          PyjinnScript.createJob(
              jobId,
              Optional.of(parentJob.jobId()),
              childCommand,
              scriptCode,
              config,
              systemMessageQueue,
              mappingsLoader.get(),
              /* autoExit= */ false,
              () -> finishJob(jobId, /* nextCommand= */ List.of()));
      jobMap.put(scriptJob.jobId(), scriptJob);
      parentJob.addOperation(opId, new PyjinnScriptOperation(scriptName, scriptJob));
      scriptJob.start();
      return scriptJob.script();
    }

    private class PyjinnScriptOperation implements Job.Operation {
      private final String name;
      private final PyjinnScript.PyjinnJob job;
      private final AtomicBoolean done = new AtomicBoolean(false);

      PyjinnScriptOperation(String name, PyjinnScript.PyjinnJob job) {
        this.name = name;
        this.job = job;
        job.script().atExit(status -> done.set(true));
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public void suspend() {
        job.suspend();
      }

      @Override
      public boolean resumeAndCheckDone() {
        job.resume();
        return done.get();
      }

      @Override
      public void cancel() {
        job.script().exit(0);
      }
    }

    public void createSubprocessJob(ScriptConfig.BoundCommand command, List<Token> nextCommand) {
      int jobId = allocateJobId();
      var job =
          new Job.SubprocessJob(
              jobId,
              /* parentJobId= */ Optional.empty(),
              command,
              new SubprocessTask(config),
              config,
              systemMessageQueue,
              Minescript::processScriptFunction,
              () -> finishJob(jobId, nextCommand));
      var undo = new UndoableActionBlockPack(job.jobId(), command.command());
      jobUndoMap.put(job.jobId(), undo);
      undoStack.addFirst(undo);
      jobMap.put(job.jobId(), job);
      job.start();
    }

    public Optional<UndoableAction> getUndoForJob(JobControl job) {
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
          job.requestKill();
        }
      }

      // TODO(maxuser): Migrate undo to Job.InprocessJob.
      int jobId = allocateJobId();
      var undoJob =
          new Job.SubprocessJob(
              jobId,
              /* parentJobId= */ Optional.empty(),
              new ScriptConfig.BoundCommand(
                  null, undo.derivativeCommand(), ScriptRedirect.Pair.DEFAULTS),
              new UndoTask(undo),
              config,
              systemMessageQueue,
              Minescript::processScriptFunction,
              () -> finishJob(jobId, Collections.emptyList()));
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

    public Map<Integer, JobControl> getMap() {
      return jobMap;
    }
  }

  private static JobManager jobs = new JobManager();

  public static final SystemMessageQueue systemMessageQueue = new SystemMessageQueue();

  public static Script loadPyjinnScript(List<String> scriptCommand, String scriptCode)
      throws Exception {
    return PyjinnScript.loadScript(
        scriptCommand.toArray(String[]::new), scriptCode, mappingsLoader.get());
  }

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

  private static void listJobs(boolean allJobs) {
    if (jobs.getMap().isEmpty()) {
      systemMessageQueue.logUserInfo("There are no jobs running.");
      return;
    }
    for (var job : jobs.getMap().values()) {
      if (allJobs || job.parentJobId().isEmpty()) {
        systemMessageQueue.logUserInfo(job.toString());
      }
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
        job.requestKill();
      }
      return;
    }
    var job = jobs.getMap().get(jobId);
    if (job == null) {
      systemMessageQueue.logUserError("No job with ID {}. Use \\jobs to list jobs.", jobId);
      return;
    }
    job.requestKill();
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

  public static void readBlocks(
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

    int xMin = Math.min(x0, x1);
    int yMin = Math.max(Math.min(y0, y1), level.getMinY());
    int zMin = Math.min(z0, z1);

    int xMax = Math.max(x0, x1);
    int yMax = Math.min(Math.max(y0, y1), level.getMaxY());
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
        String block = BlockPositionReader.getBlockStateString(level, pos.set(x, 0, z));
        if (block == null || block.equals("minecraft:void_air")) {
          throw new IllegalStateException(
              "Not all chunks are loaded within the requested `copy` volume.");
        }
      }
    }

    for (int x = xMin; x <= xMax; ++x) {
      for (int y = yMin; y <= yMax; ++y) {
        for (int z = zMin; z <= zMax; ++z) {
          BlockState blockState = level.getBlockState(pos.set(x, y, z));
          if (!blockState.isAir()) {
            String block = BlockPositionReader.blockStateToString(blockState);
            if (block != null) {
              blockConsumer.setblock(x, y, z, block);
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
        systemMessageQueue.logUserInfo("Usage: \\jobs [all]");
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
      case "reload_mappings":
        systemMessageQueue.logUserInfo("{} (built-in command)", command);
        systemMessageQueue.logUserInfo("Usage: \\reload_mappings");
        systemMessageQueue.logUserInfo("Reloads mappings files from `minescript/mappings` dir.");
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
          boolean noParams = checkParamTypes(command);
          boolean allParam = checkParamTypes(command, ParamType.STRING) && command[1].equals("all");
          if (noParams || allParam) {
            listJobs(allParam);
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

        case "reload_mappings":
          try {
            reloadMappings();
            systemMessageQueue.logUserInfo("Reloaded mappings from `minescript/mappings`.");
          } catch (Exception e) {
            systemMessageQueue.logException(e);
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

      var boundCommand = new ScriptConfig.BoundCommand(commandPath, command, redirects);

      if (commandPath.getFileName().toString().toLowerCase().endsWith(".pyj")) {
        try {
          jobs.createPyjinnJob(boundCommand, nextCommand);
        } catch (Exception e) {
          systemMessageQueue.logException(e);
        }
        return;
      }

      jobs.createSubprocessJob(boundCommand, nextCommand);

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
    ScriptValue eventValue = null;
    for (var entry : keyEventListeners.entrySet()) {
      var listener = entry.getValue();
      if (listener.isActive()) {
        if (eventValue == null) {
          String screenName = getScreenName().orElse(null);
          long timeMillis = System.currentTimeMillis();
          var event = new KeyEvent();
          event.key = key;
          event.scan_code = scanCode;
          event.action = action;
          event.modifiers = modifiers;
          event.time = timeMillis / 1000.;
          event.screen = screenName;
          eventValue = ScriptValue.of(event);
        }
        if (config.debugOutput()) {
          LOGGER.info("Forwarding key event to listener {}: {}", entry.getKey(), eventValue);
        }
        listener.respond(eventValue);
      }
    }
  }

  public static void onMouseClick(int button, int action, int modifiers, double x, double y) {
    ScriptValue eventValue = null;
    for (var entry : mouseEventListeners.entrySet()) {
      var listener = entry.getValue();
      if (listener.isActive()) {
        if (eventValue == null) {
          String screenName = getScreenName().orElse(null);
          long timeMillis = System.currentTimeMillis();
          var event = new MouseEvent();
          event.button = button;
          event.action = action;
          event.modifiers = modifiers;
          event.time = timeMillis / 1000.;
          event.x = x;
          event.y = y;
          event.screen = screenName;
          eventValue = ScriptValue.of(event);
        }
        if (config.debugOutput()) {
          LOGGER.info("Forwarding mouse event to listener {}: {}", entry.getKey(), eventValue);
        }
        listener.respond(eventValue);
      }
    }
  }

  public static void onRenderWorld(Object context) {
    // Process render event listeners from Pyjinn scripts.
    ScriptValue eventValue = null;
    for (var entry : renderEventListeners.entrySet()) {
      var listener = entry.getValue();
      if (listener.isActive()) {
        if (eventValue == null) {
          var event = new RenderEvent();
          event.context = context;
          event.time = System.currentTimeMillis() / 1000.;
          eventValue = ScriptValue.of(event);
        }
        listener.respond(eventValue);
      }
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
              chatEditBox.setTextColor(0xff5ee8e8); // cyan for partial completion
            } else {
              chatEditBox.setTextColor(0xff5ee85e); // green for full completion
            }
            commandSuggestions = new ArrayList<>();
            return cancel;
          }
        }
        var completions = getCommandCompletions(command);
        if (completions.contains(command)) {
          chatEditBox.setTextColor(0xff5ee85e); // green
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
            chatEditBox.setTextColor(0xff5ee8e8); // cyan
          } else {
            chatEditBox.setTextColor(0xffe85e5e); // red
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
    ScriptValue eventValue = null;

    var iter = chatEventListeners.entrySet().iterator();
    while (iter.hasNext()) {
      var entry = iter.next();
      var listener = entry.getValue();
      if (listener.isActive()) {
        if (eventValue == null) {
          var event = new ChatEvent();
          event.type = "chat";
          event.message = text;
          event.time = System.currentTimeMillis() / 1000.;
          eventValue = ScriptValue.of(event);
        }
        if (config.debugOutput()) {
          LOGGER.info("Forwarding chat message to listener {}: {}", entry.getKey(), eventValue);
        }
        listener.respond(eventValue);
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
    ScriptValue eventValue = null;
    for (var handler : chunkEventListeners.values()) {
      if (handler.isActive()) {
        if (eventValue == null) {
          int worldX = chunkCoordToWorldCoord(chunkX);
          int worldZ = chunkCoordToWorldCoord(chunkZ);
          var event = new ChunkEvent();
          event.loaded = loaded;
          event.x_min = worldX;
          event.z_min = worldZ;
          event.x_max = worldX + 15;
          event.z_max = worldZ + 15;
          event.time = System.currentTimeMillis() / 1000.;
          eventValue = ScriptValue.of(event);
        }
        handler.respond(eventValue);
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
        var event = new ChatEvent();
        event.type = "outgoing_chat_intercept";
        event.message = message;
        event.time = System.currentTimeMillis() / 1000.;
        interceptor.respond(ScriptValue.of(event));
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

  private static EventDispatcher tickEventListeners = new EventDispatcher();
  private static EventDispatcher renderEventListeners = new EventDispatcher();
  private static EventDispatcher keyEventListeners = new EventDispatcher();
  private static EventDispatcher mouseEventListeners = new EventDispatcher();
  private static EventDispatcher chatEventListeners = new EventDispatcher();
  private static EventDispatcher chatInterceptors =
      new EventDispatcher(Minescript::getOutgoingChatInterceptFilter);
  private static EventDispatcher addEntityEventListeners = new EventDispatcher();
  private static EventDispatcher blockUpdateEventListeners = new EventDispatcher();
  private static EventDispatcher takeItemEventListeners = new EventDispatcher();
  private static EventDispatcher damageEventListeners = new EventDispatcher();
  private static EventDispatcher explosionEventListeners = new EventDispatcher();
  private static EventDispatcher chunkEventListeners = new EventDispatcher();
  private static EventDispatcher worldListeners = new EventDispatcher();

  private static ImmutableList<EventDispatcher> eventDispatchers =
      ImmutableList.of(
          tickEventListeners,
          renderEventListeners,
          keyEventListeners,
          mouseEventListeners,
          chatEventListeners,
          chatInterceptors,
          addEntityEventListeners,
          blockUpdateEventListeners,
          takeItemEventListeners,
          damageEventListeners,
          explosionEventListeners,
          chunkEventListeners,
          worldListeners);

  /** Returns the event dispatcher for the given event name, or {@code null}. */
  static EventDispatcher getDispatcherForEventName(String eventName) throws Exception {
    return switch (eventName) {
      case "tick" -> tickEventListeners;
      case "render" -> renderEventListeners;
      case "key" -> keyEventListeners;
      case "mouse" -> mouseEventListeners;
      case "chat" -> chatEventListeners;
      case "outgoing_chat_intercept" -> chatInterceptors;
      case "add_entity" -> addEntityEventListeners;
      case "block_update" -> blockUpdateEventListeners;
      case "take_item" -> takeItemEventListeners;
      case "damage" -> damageEventListeners;
      case "explosion" -> explosionEventListeners;
      case "chunk" -> chunkEventListeners;
      case "world" -> worldListeners;
      default -> null;
    };
  }

  private static Map<JobOperationId, ChunkLoadEventListener> chunkLoadEventListeners =
      new ConcurrentHashMap<JobOperationId, ChunkLoadEventListener>();

  private static Optional<Predicate<Object>> getOutgoingChatInterceptFilter(
      Map<String, Object> listenerArgs) {
    String eventName = "outgoing_chat_intercept";
    Optional<Predicate<Object>> filter = Optional.empty();
    Object prefixArg = listenerArgs.get("prefix");
    Object patternArg = listenerArgs.get("pattern");
    if (prefixArg != null && patternArg != null) {
      throw new IllegalArgumentException("Only one of `prefix` and `pattern` can be specified");
    }
    if (prefixArg != null) {
      if (prefixArg instanceof String prefix) {
        filter = Optional.of(o -> o instanceof String s && s.startsWith(prefix));
      } else {
        throw new IllegalArgumentException(
            "Expected keyword arg `prefix` to event listener of `%s` to be string but got `%s`"
                .formatted(eventName, prefixArg));
      }
    } else if (patternArg != null) {
      if (patternArg instanceof String patternString) {
        Pattern pattern = Pattern.compile(patternString);
        filter = Optional.of(o -> o instanceof String s && pattern.matcher(s).matches());
      } else {
        throw new IllegalArgumentException(
            "Expected keyword arg `pattern` to event listener of `%s` to be string but got `%s`"
                .formatted(eventName, patternArg));
      }
    }
    return filter;
  }

  public static void onAddEntityEvent(Entity entity) {
    ScriptValue eventValue = null;
    for (var handler : addEntityEventListeners.values()) {
      if (handler.isActive()) {
        if (eventValue == null) {
          boolean includeNbt = false;
          var event = new AddEntityEvent();
          event.entity =
              new EntityExporter(entityPositionInterpolation(), includeNbt).export(entity);
          event.time = System.currentTimeMillis() / 1000.;
          eventValue = ScriptValue.of(event);
        }
        handler.respond(eventValue);
      }
    }
  }

  public static void onBlockUpdateEvent(BlockPos pos, BlockState newState) {
    var minecraft = Minecraft.getInstance();
    var level = minecraft.level;
    ScriptValue eventValue = null;
    for (var handler : blockUpdateEventListeners.values()) {
      if (handler.isActive()) {
        if (eventValue == null) {
          var event = new BlockUpdateEvent();
          event.position[0] = pos.getX();
          event.position[1] = pos.getY();
          event.position[2] = pos.getZ();

          // TODO(maxuser): If a block changes due to local player's mouse click or key press,
          // the block can change locally before the BlockUpdate packet is received by the client.
          // Maybe track the current block during mouse and key events via
          // minecraft.getCameraEntity().pick(...). (see player_get_targeted_block())
          event.old_state = BlockPositionReader.getBlockStateString(level, pos);
          event.new_state = BlockPositionReader.blockStateToString(newState);
          event.time = System.currentTimeMillis() / 1000.;
          eventValue = ScriptValue.of(event);
        }
        handler.respond(eventValue);
      }
    }
  }

  public static void onTakeItemEvent(Entity player, Entity item, int amount) {
    ScriptValue eventValue = null;
    for (var handler : takeItemEventListeners.values()) {
      if (handler.isActive()) {
        if (eventValue == null) {
          boolean includeNbt = false;
          var event = new TakeItemEvent();
          event.player_uuid = player.getUUID().toString();
          event.item = new EntityExporter(entityPositionInterpolation(), includeNbt).export(item);
          event.amount = amount;
          event.time = System.currentTimeMillis() / 1000.;
          eventValue = ScriptValue.of(event);
        }
        handler.respond(eventValue);
      }
    }
  }

  public static void onDamageEvent(Entity entity, Entity cause, String source) {
    ScriptValue eventValue = null;
    for (var handler : damageEventListeners.values()) {
      if (handler.isActive()) {
        if (eventValue == null) {
          var event = new DamageEvent();
          event.entity_uuid = entity.getUUID().toString();
          event.cause_uuid = cause == null ? null : cause.getUUID().toString();
          event.source = source;
          event.time = System.currentTimeMillis() / 1000.;
          eventValue = ScriptValue.of(event);
        }
        handler.respond(eventValue);
      }
    }
  }

  public static void onExplosionEvent(double x, double y, double z, List<BlockPos> toExplode) {
    var minecraft = Minecraft.getInstance();
    var level = minecraft.level;
    ScriptValue eventValue = null;
    for (var handler : explosionEventListeners.values()) {
      if (handler.isActive()) {
        if (eventValue == null) {
          var event = new ExplosionEvent();

          var blockpacker = new BlockPacker();
          for (var pos : toExplode) {
            String block = BlockPositionReader.getBlockStateString(level, pos);
            if (block != null) {
              blockpacker.setblock(pos.getX(), pos.getY(), pos.getZ(), block);
            }
          }
          String encodedBlockpack = blockpacker.pack().toBase64EncodedString();

          event.position[0] = x;
          event.position[1] = y;
          event.position[2] = z;

          event.blockpack_base64 = encodedBlockpack;
          event.time = System.currentTimeMillis() / 1000.;
          eventValue = ScriptValue.of(event);
        }
        handler.respond(eventValue);
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
    connection.sendCommand(command);
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

    JsonElement jsonElement = JsonParser.parseString(text);
    var component =
        (Component)
            ComponentSerialization.CODEC
                .parse(
                    RegistryAccess.EMPTY.createSerializationContext(JsonOps.INSTANCE), jsonElement)
                .resultOrPartial(
                    string ->
                        LOGGER.warn("Failed to parse JSON-formatted text '{}': {}", text, string))
                .orElse(null);
    chat.addMessage(component);
  }

  static void processMessage(Message message) {
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

  private static ScriptValue doPlayerAction(
      String functionName, KeyMapping keyMapping, ScriptFunctionCall.ArgList args) {
    args.expectSize(1);
    boolean pressed = args.getBoolean(0);
    pressKeyBind(keyMapping.getName(), pressed);
    return ScriptValue.TRUE;
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
        name = "Level Loading";
      } else if (screen instanceof ReceivingLevelScreen) {
        name = "Progress";
      } else {
        name = mappingsLoader.get().getPrettyClassName(screen.getClass().getName());
      }
    }
    return Optional.of(name);
  }

  private static final Optional<JsonElement> OPTIONAL_JSON_NULL = Optional.of(JsonNull.INSTANCE);

  private static final JsonElement JSON_TRUE = new JsonPrimitive(true);

  private static final Optional<JsonElement> OPTIONAL_JSON_TRUE = Optional.of(JSON_TRUE);

  public static void processScriptFunction(
      Job.SubprocessJob job, String functionName, long funcCallId, List<?> parsedArgs) {
    var funcCall = new ScriptFunctionCall(functionName, parsedArgs);
    try {
      Optional<JsonElement> response = runExternalScriptFunction(job, funcCallId, funcCall);
      if (response.isPresent()) {
        job.respond(funcCallId, response.map(json -> ScriptValue.fromJson(json)).get(), true);
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
                    functionName, funcCall.args(), e.toString())));
      } else {
        job.raiseException(funcCallId, e);
      }
    }
  }

  static void registerEventListener(
      JobControl job, long funcCallId, String eventName, Map<String, Object> listenerArgs)
      throws Exception {
    var jobOpId = new JobOperationId(job.jobId(), funcCallId);
    var eventDispatcher = getDispatcherForEventName(eventName);
    if (eventDispatcher.containsKey(jobOpId)) {
      throw new IllegalStateException(
          "Failed to create event listener because function call ID '%s' is already registered for job '%s': %s"
              .formatted(funcCallId, job.jobSummary()));
    }
    LOGGER.info(
        "Creating `{}` listener {} for `{}` with args {}",
        eventName,
        funcCallId,
        job,
        listenerArgs);
    var listener = new EventListener(job, eventName, () -> eventDispatcher.remove(jobOpId));
    eventDispatcher.addListener(jobOpId, listenerArgs, listener);
  }

  /** Call script function. Intended as a helper for Pyjinn scripts. */
  public static Object call(JobControl job, long funcCallId, String function, List<?> args)
      throws Exception {
    var functionCall = new ScriptFunctionCall(function, args);
    if (runNoReturnScriptFunction(functionCall)) {
      return null;
    }

    return runScriptFunction(job, funcCallId, functionCall).get();
  }

  private static ScriptValue runScriptFunction(
      JobControl job, long funcCallId, ScriptFunctionCall functionCall) throws Exception {
    var minecraft = Minecraft.getInstance();
    var world = minecraft.level;
    var player = minecraft.player;
    var options = minecraft.options;
    final String functionName = functionCall.name();
    final ScriptFunctionCall.ArgList args = functionCall.args();

    switch (functionName) {
      case "player_position":
        {
          args.expectSize(0);
          return ScriptValue.of(new Double[] {player.getX(), player.getY(), player.getZ()});
        }

      case "player_name":
        args.expectSize(0);
        return ScriptValue.of(player.getName().getString());

      case "getblock":
        {
          args.expectSize(3);
          Level level = minecraft.level;
          int arg0 = args.getConvertibleInt(0);
          int arg1 = args.getConvertibleInt(1);
          int arg2 = args.getConvertibleInt(2);
          String block =
              BlockPositionReader.getBlockStateString(level, new BlockPos(arg0, arg1, arg2));
          if (block != null) {
            return ScriptValue.of(block);
          } else {
            return ScriptValue.NULL;
          }
        }

      case "getblocklist":
        {
          args.expectSize(1);
          return ScriptValue.of(BlockSequenceReader.toStringArray(minecraft.level, args.get(0)));
        }

      case "unregister_event_handler":
        {
          args.expectArgs("handler_id");
          long handlerId = args.getStrictLong(0);
          return ScriptValue.of(job.cancelOperation(handlerId));
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
          return ScriptValue.TRUE;
        }

      case "player_hand_items":
        {
          args.expectSize(0);
          var handItems = new HandItems();
          handItems.main_hand =
              ItemStackData.of(player.getMainHandItem(), OptionalInt.empty(), false);
          handItems.off_hand =
              ItemStackData.of(player.getOffhandItem(), OptionalInt.empty(), false);
          return ScriptValue.of(handItems);
        }

      case "player_inventory":
        {
          args.expectSize(0);
          var inventory = player.getInventory();
          var result = new ArrayList<ItemStackData>();
          int selectedSlot = inventory.getSelectedSlot();
          for (int i = 0; i < inventory.getContainerSize(); i++) {
            var itemStack = inventory.getItem(i);
            if (itemStack.getCount() > 0) {
              result.add(ItemStackData.of(itemStack, OptionalInt.of(i), i == selectedSlot));
            }
          }
          return ScriptValue.of(result.toArray(ItemStackData[]::new));
        }

      case "player_inventory_slot_to_hotbar":
        {
          throw new UnsupportedOperationException(
              "player_inventory_slot_to_hotbar: support for ServerboundPickItemPacket removed in"
                  + " Minecraft 1.21.4");
        }

      case "player_inventory_select_slot":
        {
          args.expectSize(1);
          int slot = args.getStrictInt(0);
          var inventory = player.getInventory();
          int previouslySelectedSlot = inventory.getSelectedSlot();
          inventory.setSelectedSlot(slot);
          return ScriptValue.of(previouslySelectedSlot);
        }

      case "press_key_bind":
        args.expectSize(2);
        pressKeyBind(args.getString(0), args.getBoolean(1));
        return ScriptValue.TRUE;

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
          return ScriptValue.of(new Float[] {player.getYRot(), player.getXRot()});
        }

      case "player_set_orientation":
        {
          args.expectSize(2);
          Double yaw = args.getDouble(0);
          Double pitch = args.getDouble(1);
          player.setYRot(yaw.floatValue() % 360.0f);
          player.setXRot(pitch.floatValue() % 360.0f);
          return ScriptValue.TRUE;
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
            String block = BlockPositionReader.getBlockStateString(level, blockPos);
            var targetedBlock = new TargetedBlock();
            targetedBlock.position[0] = blockPos.getX();
            targetedBlock.position[1] = blockPos.getY();
            targetedBlock.position[2] = blockPos.getZ();
            targetedBlock.distance = playerDistance;
            targetedBlock.side = hitResult.getDirection().toString();
            targetedBlock.type = block;
            return ScriptValue.of(targetedBlock);
          } else {
            return ScriptValue.NULL;
          }
        }

      case "player_get_targeted_entity":
        {
          args.expectArgs("max_distance", "nbt");
          double maxDistance = args.getDouble(0);
          boolean includeNbt = args.getBoolean(1);
          return DebugRenderer.getTargetedEntity(player, (int) maxDistance)
              .map(e -> new EntityExporter(entityPositionInterpolation(), includeNbt).export(e))
              .map(e -> ScriptValue.of(e))
              .orElse(ScriptValue.NULL);
        }

      case "player_health":
        return ScriptValue.of(player.getHealth());

      case "player":
        {
          args.expectArgs("nbt");
          boolean includeNbt = args.getBoolean(0);
          return ScriptValue.of(
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
          return ScriptValue.of(
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
          return ScriptValue.of(
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

          var result = new VersionInfo();
          result.minecraft = SharedConstants.getCurrentVersion().name();
          result.minescript = Minescript.version;
          result.mod_loader = platform.modLoaderName();
          result.launcher = minecraft.getLaunchedVersion();
          result.os_name = System.getProperty("os.name");
          result.os_version = System.getProperty("os.version");
          result.minecraft_class_name = Minecraft.class.getName();
          result.pyjinn = Script.versionInfo().toString();
          return ScriptValue.of(result);
        }

      case "world_info":
        {
          args.expectSize(0);
          var levelProperties = world.getLevelData();
          var difficulty = levelProperties.getDifficulty();
          var serverData = minecraft.getCurrentServer();
          var serverAddress = serverData == null ? "localhost" : serverData.ip;

          var result = new WorldInfo();
          result.game_ticks = levelProperties.getGameTime();
          result.day_ticks = levelProperties.getDayTime();
          result.raining = levelProperties.isRaining();
          result.thundering = levelProperties.isThundering();

          var spawnPos = levelProperties.getSpawnPos();
          result.spawn[0] = spawnPos.getX();
          result.spawn[1] = spawnPos.getY();
          result.spawn[2] = spawnPos.getZ();

          result.hardcore = levelProperties.isHardcore();
          result.difficulty = difficulty.getSerializedName();
          result.name = getWorldName();
          result.address = serverAddress;
          return ScriptValue.of(result);
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
              /* downScale= */ 1,
              message -> job.log(message.getString()));
          return ScriptValue.TRUE;
        }

      case "screen_name":
        if (!args.isEmpty()) {
          throw new IllegalArgumentException("Expected no params but got: " + args.toString());
        }
        return getScreenName().map(ScriptValue::of).orElse(ScriptValue.NULL);

      case "container_get_items": // List of Items in Chest
        {
          if (!args.isEmpty()) {
            throw new IllegalArgumentException("Expected no params but got: " + args.toString());
          }
          Screen screen = minecraft.screen;
          if (screen instanceof AbstractContainerScreen<?> handledScreen) {
            AbstractContainerMenu screenHandler = handledScreen.getMenu();
            Slot[] slots = screenHandler.slots.toArray(new Slot[0]);
            var result = new ArrayList<ItemStackData>();
            for (Slot slot : slots) {
              ItemStack itemStack = slot.getItem();
              if (itemStack.isEmpty()) {
                continue;
              }
              result.add(ItemStackData.of(itemStack, OptionalInt.of(slot.index), false));
            }
            return ScriptValue.of(result.toArray(ItemStackData[]::new));
          } else {
            return ScriptValue.NULL;
          }
        }

      case "player_look_at": // Look at x, y, z
        {
          args.expectSize(3);
          double x = args.getDouble(0);
          double y = args.getDouble(1);
          double z = args.getDouble(2);
          player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(x, y, z));
          return ScriptValue.TRUE;
        }

      case "show_chat_screen":
        {
          args.expectSize(2);
          boolean show = args.getBoolean(0);
          var screen = minecraft.screen;
          final ScriptValue result;
          if (show) {
            if (screen == null) {
              minecraft.setScreen(new ChatScreen(""));
            }
            var prompt = args.get(1);
            if (prompt != null && checkChatScreenInput()) {
              chatEditBox.setValue(prompt.toString());
            }
            result = ScriptValue.TRUE;
          } else {
            if (screen != null && screen instanceof ChatScreen) {
              screen.onClose();
              result = ScriptValue.TRUE;
            } else {
              result = ScriptValue.FALSE;
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
                            j.parentJobId().orElse(null),
                            j == job));
                  });
          return ScriptValue.ofNullables(result.toArray(JobInfo[]::new));
        }

      case "append_chat_history":
        args.expectSize(1);
        minecraft.gui.getChat().addRecentChat(args.getString(0));
        return ScriptValue.NULL;

      case "chat_input":
        {
          args.expectSize(0);
          return ScriptValue.of(
              new Object[] {chatEditBox.getValue(), chatEditBox.getCursorPosition()});
        }

      case "set_chat_input":
        {
          args.expectSize(3);
          args.getOptionalString(0).ifPresent(chatEditBox::setValue);
          args.getOptionalStrictInt(1).ifPresent(chatEditBox::setCursorPosition);
          args.getOptionalStrictInt(2)
              .ifPresent(
                  i -> {
                    // If the high byte (alpha) is zero, promote to 0xFF so it's fully opaque
                    // instead of fully invsible.
                    if ((i & 0xFF000000) == 0) {
                      i |= 0xFF000000;
                    }
                    chatEditBox.setTextColor(i);
                  });
          return ScriptValue.NULL;
        }
    }

    throw new IllegalArgumentException(
        String.format("Unknown function `%s` called from job: %s", functionName, job.jobSummary()));
  }

  /**
   * Run no-return script function that has no return value when called from an external script.
   *
   * <p>No-return script functions are fire-and-forget when requested from an external script.
   *
   * @return true if a no-return script function was executed, false otherwise.
   */
  private static boolean runNoReturnScriptFunction(ScriptFunctionCall functionCall) {
    final String functionName = functionCall.name();
    final ScriptFunctionCall.ArgList args = functionCall.args();

    switch (functionName) {
      case "execute":
        {
          args.expectArgs("command");
          String command = args.getString(0);
          if (command.startsWith("\\")) {
            runMinescriptCommand(command.substring(1));
          } else {
            processMinecraftCommand(command.startsWith("/") ? command.substring(1) : command);
          }
          return true;
        }

      case "echo_json":
        {
          args.expectArgs("json_text");
          var message = args.getString(0);
          processJsonFormattedText(message);
          return true;
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
                    args.rawArgs().stream()
                        .map(Object::toString)
                        .collect(Collectors.toList())
                        .toArray(String[]::new));
          }

          processPlainText(message);
          return true;
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
                    args.rawArgs().stream()
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
          return true;
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
                    args.rawArgs().stream()
                        .map(Object::toString)
                        .collect(Collectors.toList())
                        .toArray(String[]::new));
          }

          LOGGER.info(message);
          return true;
        }
    }
    return false;
  }

  static void startEventListener(JobControl job, long funcCallId, String eventName, long listenerId)
      throws Exception {
    var eventDispatcher = getDispatcherForEventName(eventName);
    if (eventDispatcher == null) {
      throw new IllegalArgumentException("No event named '%s'".formatted(eventName));
    }
    var jobOpId = new JobOperationId(job.jobId(), listenerId);
    var listener = eventDispatcher.get(jobOpId);
    if (listener == null) {
      throw new IllegalStateException(
          "No %s listener found with requested ID: %s".formatted(eventName, jobOpId.toString()));
    }
    job.addOperation(listenerId, listener);
    listener.start(funcCallId);
  }

  /** Returns a JSON response if a script function is called. */
  private static Optional<JsonElement> runExternalScriptFunction(
      Job.SubprocessJob job, long funcCallId, ScriptFunctionCall functionCall) throws Exception {
    final String functionName = functionCall.name();
    final ScriptFunctionCall.ArgList args = functionCall.args();

    switch (functionName) {
      case "register_event_listener":
        {
          args.expectArgs("event_name", "kwargs");
          String eventName = args.getString(0);
          var kwargs = args.getStringKeyMap(1);
          registerEventListener(job, funcCallId, eventName, kwargs);
          return Optional.of(new JsonPrimitive(funcCallId));
        }

      case "start_event_listener":
        {
          args.expectArgs("event_name", "listener_id");
          String eventName = args.getString(0);
          long listenerId = args.getStrictLong(1);
          startEventListener(job, funcCallId, eventName, listenerId);
          return Optional.empty();
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
                    // TODO(maxuser): The calling convention should be respected within Job::respond
                    // so that it's applied universally for all async script functions, not just for
                    // this one.
                    job.respond(
                        funcCallId,
                        ScriptValue.fromJson(
                            functionCall.callingConvention()
                                    == ScriptFunctionCall.CallingConvention.JAVA
                                ? new JsonPrimitive(job.objects.retain(success))
                                : new JsonPrimitive(success)),
                        true);
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
          var object = Class.forName(mappingsLoader.get().getRuntimeClassName(args.getString(0)));
          return Optional.of(new JsonPrimitive(job.objects.retain(object)));
        }

      case "java_ctor":
        {
          args.expectSize(1);
          var target = (Class<?>) job.objects.getById(args.getStrictLong(0));
          var ctor = new ClassConstructor(target);
          return Optional.of(new JsonPrimitive(job.objects.retain(ctor)));
        }

      case "java_new_instance":
        {
          var klass = (ClassConstructor) job.objects.getById(args.getStrictLong(0));
          Object[] params = new Object[args.size() - 1];
          for (int i = 0; i < params.length; ++i) {
            params[i] = job.objects.getById(args.getStrictLong(i + 1));
          }
          Class<?>[] paramTypes = Script.TypeChecker.getTypes(params);
          var ctor =
              Script.TypeChecker.findBestMatchingConstructor(
                  klass.type(), paramTypes, /* diagnostics= */ null);
          if (ctor.isEmpty()) {
            // Re-run type checker with same args but with error diagnostics for creating exception.
            var diagnostics =
                new Script.TypeChecker.Diagnostics(mappingsLoader.get()::getPrettyClassName);
            Script.TypeChecker.findBestMatchingConstructor(klass.type(), paramTypes, diagnostics);
            throw diagnostics.createTruncatedException();
          }
          var result = ctor.get().newInstance(/* env= */ null, params);
          return Optional.of(new JsonPrimitive(job.objects.retain(result)));
        }

      case "java_member":
        {
          args.expectSize(2);
          var type = (Class<?>) job.objects.getById(args.getStrictLong(0));
          String memberName = args.getString(1);
          var classMember = new ClassMember(type, memberName);
          return Optional.of(new JsonPrimitive(job.objects.retain(classMember)));
        }

      case "java_call_method":
        {
          var target = (Object) job.objects.getById(args.getStrictLong(0));
          var member = (ClassMember) job.objects.getById(args.getStrictLong(1));
          Object[] params = new Object[args.size() - 2];
          for (int i = 0; i < params.length; ++i) {
            params[i] = job.objects.getById(args.getStrictLong(i + 2));
          }
          Class<?>[] paramTypes = Script.TypeChecker.getTypes(params);

          boolean isStaticMethod = target == null;
          var classForLookup = target == null ? member.type() : target.getClass();
          var method =
              Script.TypeChecker.findBestMatchingMethod(
                  classForLookup,
                  isStaticMethod,
                  mappingsLoader.get()::getRuntimeMethodNames,
                  member.name(),
                  paramTypes,
                  /* diagnostics= */ null);
          if (method.isEmpty()) {
            // Re-run type checker with same args but with error diagnostics for creating exception.
            var diagnostics =
                new Script.TypeChecker.Diagnostics(mappingsLoader.get()::getPrettyClassName);
            Script.TypeChecker.findBestMatchingMethod(
                classForLookup,
                isStaticMethod,
                mappingsLoader.get()::getRuntimeMethodNames,
                member.name(),
                paramTypes,
                diagnostics);
            throw diagnostics.createTruncatedException();
          }
          var result = method.get().invoke(/* env= */ null, target, params);
          return Optional.of(new JsonPrimitive(job.objects.retain(result)));
        }

      case "java_call_script_function":
        {
          // Try to parse first arg as either a long (and interpret it as a Java object handle
          // referencing a String) or directly as a JSON string.
          OptionalLong funcNameObjectHandle =
              ScriptFunctionCall.ArgList.getStrictLongValue(args.get(0));
          final String funcName;
          if (funcNameObjectHandle.isPresent()) {
            var functionNameObject = (Object) job.objects.getById(funcNameObjectHandle.getAsLong());
            if (functionNameObject instanceof String string) {
              funcName = string;
            } else {
              throw new IllegalArgumentException(
                  String.format(
                      "Expected first arg to java_call_script_function to be a handle to a Java"
                          + " String but got `%s` instead: %s",
                      functionNameObject.getClass().getName(), functionNameObject));
            }
          } else {
            funcName = args.getString(0);
          }
          List<Object> params = new ArrayList<>();
          for (int i = 1; i < args.size(); ++i) {
            params.add(job.objects.getById(args.getStrictLong(i)));
          }
          var funcCall = new ScriptFunctionCall(funcName, params);
          funcCall.setCallingConvention(ScriptFunctionCall.CallingConvention.JAVA);
          Optional<JsonElement> result = runExternalScriptFunction(job, funcCallId, funcCall);
          return result.isEmpty()
              ? result
              : Optional.of(new JsonPrimitive(job.objects.retain(result)));
        }

      case "java_access_field":
        {
          args.expectSize(2);
          var target = (Object) job.objects.getById(args.getStrictLong(0));
          var member = (ClassMember) job.objects.getById(args.getStrictLong(1));
          var field =
              member
                  .type()
                  .getField(mappingsLoader.get().getRuntimeFieldName(member.type(), member.name()));
          boolean isClass = target instanceof Class;
          var result = field.get(isClass ? null : target);
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

      case "java_new_array":
        {
          if (args.size() == 0) {
            throw new IllegalArgumentException("java_new_array has 1 required arg but got no args");
          }
          Object argId0 = job.objects.getById(args.getStrictLong(0));
          if (argId0 instanceof Class<?> clazz) {
            int arraySize = args.size() - 1;
            Object specificArray = Array.newInstance(clazz, arraySize);
            if (clazz.isPrimitive()) {
              if (clazz == byte.class) {
                for (int i = 0; i < arraySize; ++i) {
                  Array.setByte(
                      specificArray, i, (Byte) job.objects.getById(args.getStrictLong(i + 1)));
                }
              } else if (clazz == int.class) {
                for (int i = 0; i < arraySize; ++i) {
                  Array.setInt(
                      specificArray, i, (Integer) job.objects.getById(args.getStrictLong(i + 1)));
                }
              } else if (clazz == long.class) {
                for (int i = 0; i < arraySize; ++i) {
                  Array.setLong(
                      specificArray, i, (Long) job.objects.getById(args.getStrictLong(i + 1)));
                }
              } else if (clazz == float.class) {
                for (int i = 0; i < arraySize; ++i) {
                  Array.setFloat(
                      specificArray, i, (Float) job.objects.getById(args.getStrictLong(i + 1)));
                }
              } else if (clazz == double.class) {
                for (int i = 0; i < arraySize; ++i) {
                  Array.setDouble(
                      specificArray, i, (Double) job.objects.getById(args.getStrictLong(i + 1)));
                }
              } else if (clazz == char.class) {
                for (int i = 0; i < arraySize; ++i) {
                  Array.setChar(
                      specificArray, i, (Character) job.objects.getById(args.getStrictLong(i + 1)));
                }
              } else if (clazz == short.class) {
                for (int i = 0; i < arraySize; ++i) {
                  Array.setShort(
                      specificArray, i, (Short) job.objects.getById(args.getStrictLong(i + 1)));
                }
              } else {
                throw new IllegalArgumentException(
                    "Unexpected primitive type '%s' passed as first param to java_new_array"
                        .formatted(clazz.getName()));
              }
            } else {
              for (int i = 0; i < arraySize; ++i) {
                Array.set(specificArray, i, job.objects.getById(args.getStrictLong(i + 1)));
              }
            }
            return Optional.of(new JsonPrimitive(job.objects.retain(specificArray)));
          } else {
            throw new IllegalArgumentException(
                "Expected first arg to java_new_array to be a Java class (Class<?>) but got %s"
                    .formatted(argId0 == null ? "null" : argId0.getClass().getName()));
          }
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

      case "java_field_names":
        {
          args.expectSize(1);
          var object = job.objects.getById(args.getStrictLong(0));
          if (object instanceof Class<?> klass) {
            var array = new JsonArray();
            mappingsLoader.get().getPrettyFieldNames(klass).stream().forEach(array::add);
            return Optional.of(array);
          } else {
            throw new IllegalArgumentException(
                String.format(
                    "Expected arg to java_field_names to be a handle to a Java"
                        + " Class but got `%s` instead: %s",
                    object.getClass().getName(), object));
          }
        }

      case "java_method_names":
        {
          args.expectSize(1);
          var object = job.objects.getById(args.getStrictLong(0));
          if (object instanceof Class<?> klass) {
            var array = new JsonArray();
            mappingsLoader.get().getPrettyMethodNames(klass).stream().forEach(array::add);
            return Optional.of(array);
          } else {
            throw new IllegalArgumentException(
                String.format(
                    "Expected arg to java_method_names to be a handle to a Java"
                        + " Class but got `%s` instead: %s",
                    object.getClass().getName(), object));
          }
        }

      case "java_release":
        for (var arg : args.rawArgs()) {
          // For convenience, don't complain if a script attempts to release ID 0 which represents
          // null. This allows scripts to call Java methods and access Java fields with a null value
          // without requiring scripts to handle 0/null conditionally.
          long id = ScriptFunctionCall.ArgList.getStrictLongValue(arg).getAsLong();
          if (id != 0) {
            job.objects.releaseById(id);
          }
        }
        return OPTIONAL_JSON_NULL;

      case "eval_pyjinn_script":
        {
          args.expectSize(2);
          String scriptName = args.getString(0);
          String scriptCode = args.getString(1);
          var script = jobs.createPyjinnSubjob(job, funcCallId, scriptName, scriptCode);
          return Optional.of(new JsonPrimitive(job.objects.retain(script)));
        }

      case "flush":
        args.expectSize(0);
        return OPTIONAL_JSON_TRUE;

      case "cancelfn!":
        {
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
                "Internal error while cancelling function: expected [int, str] but got {} in job:"
                    + " {}",
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
        }

      case "exit!":
        if (funcCallId == 0) {
          return Optional.of(new JsonPrimitive("exit!"));
        } else {
          return OPTIONAL_JSON_NULL;
        }
    }

    if (runNoReturnScriptFunction(functionCall)) {
      return Optional.empty();
    }

    return Optional.of(runScriptFunction(job, funcCallId, functionCall).toJson());
  }

  private record ClassConstructor(Class<?> type) {}

  private record ClassMember(Class<?> type, String name) {}

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
      LOGGER.info("Matched {} command(s) with autorun[{}]", worldCommands.size(), worldName);
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
    // Process tick event listeners from Pyjinn scripts.
    ScriptValue eventValue = null;
    for (var entry : tickEventListeners.entrySet()) {
      var listener = entry.getValue();
      if (listener.isActive()) {
        if (eventValue == null) {
          var event = new TickEvent();
          event.time = System.currentTimeMillis() / 1000.;
          eventValue = ScriptValue.of(event);
        }
        listener.respond(eventValue);
      }
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

  public static void reportException(Throwable e) {
    ScriptExceptionHandler.reportException(systemMessageQueue, e);
  }

  public static void processMessageQueue(
      boolean processSystemMessages, Function<JobControl, Message> jobMessageQueue) {
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
          if (job instanceof Job.SubprocessJob externalJob) {
            try {
              Message message = jobMessageQueue.apply(externalJob);
              if (message != null) {
                hasMessage = true;
                if (message.type() == Message.Type.FUNCTION_CALL) {
                  String functionName = message.value();
                  var funcCallData = (Message.FunctionCallData) message.data();
                  processScriptFunction(
                      externalJob, functionName, funcCallData.funcCallId(), funcCallData.args());

                } else {
                  // TODO(maxuser): Stash level in UndoableAction as a WeakReference<Level> so
                  // that an undo operation gets applied only to the world that at existed at the
                  // time the UndoableAction was created.
                  Level level = minecraft.level;
                  jobs.getUndoForJob(externalJob)
                      .ifPresent(u -> u.processCommandToUndo(level, message));
                  processMessage(message);
                }
              }
            } catch (RuntimeException e) {
              externalJob.logJobException(e);
            }
          } else {
            Message message = jobMessageQueue.apply(job);
            if (message != null) {
              hasMessage = true;
              processMessage(message);
            }
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
