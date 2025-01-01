// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.server;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minescript.common.ExceptionInfo;
import net.minescript.common.JobControl;
import net.minescript.common.JobState;
import net.minescript.common.Message;
import net.minescript.common.ScriptConfig;
import net.minescript.common.ScriptRedirect;
import net.minescript.common.SubprocessTask;
import net.minescript.interpreter.Script;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MinescriptServer {
  private static final Logger LOGGER = LogManager.getLogger();
  private static MinecraftServer minecraftServer = null;
  private static Path minescriptDir = null;

  // TODO(maxuser): Share this constant with Minescript client.
  // MINESCRIPT_DIR is relative to the minecraft directory which is the working directory.
  private static final String MINESCRIPT_DIR = "minescript";

  public static void setMinecraftServer(MinecraftServer server) {
    minecraftServer = server;
  }

  public static MinecraftServer getMinecraftServer() {
    return minecraftServer;
  }

  private static class ServerJob implements JobControl {
    private final int id;
    private final Consumer<String> stdoutConsumer;

    public ServerJob(int id, Consumer<String> stdoutConsumer) {
      this.id = id;
      this.stdoutConsumer = stdoutConsumer;
    }

    public int jobId() {
      return id;
    }

    public JobState state() {
      return JobState.RUNNING;
    }

    public void yield() {}

    public Queue<Message> renderQueue() {
      return null;
    }

    public Queue<Message> tickQueue() {
      return null;
    }

    public boolean respond(long functionCallId, JsonElement returnValue, boolean finalReply) {
      return true;
    }

    public boolean raiseException(long functionCallId, ExceptionInfo exception) {
      return false;
    }

    public void processStdout(String text) {
      stdoutConsumer.accept(text);
    }

    public void processStderr(String text) {
      LOGGER.info("[minescript server err] " + text);
    }

    public void logJobException(Exception e) {
      LOGGER.info("[minescript server exception] {}", e);
    }
  }

  private static ScriptConfig scriptConfig;
  private static Map<Path, Long> scriptLoadTimes = new HashMap<>();

  public static void init() {
    minescriptDir = Paths.get(System.getProperty("user.dir"), MINESCRIPT_DIR);
    LOGGER.info("Starting Minescript on OS: {}", System.getProperty("os.name"));
    if (new File(MINESCRIPT_DIR).mkdir()) {
      LOGGER.info("Created minescript dir");
    }

    scriptConfig = new ScriptConfig(MINESCRIPT_DIR, ImmutableList.of(), ImmutableSet.of());
    var python = "/usr/bin/python3"; // TODO(maxuser)! get from config
    var commandPattern = ImmutableList.of(python, "-u", "{command}", "{args}");
    var commandConfig = new ScriptConfig.CommandConfig(".py", commandPattern, ImmutableList.of());
    scriptConfig.configureFileType(commandConfig);
  }

  public static void registerCommands(
      CommandDispatcher<CommandSourceStack> dispatcher,
      CommandBuildContext context,
      Commands.CommandSelection selection) {
    Path commandsPath = minescriptDir.resolve("commands.pjn");
    try {
      var script = importScript(commandsPath);
      script
          .globals()
          .setVariable(
              "registerCommand",
              (Script.Function)
                  (Object... params) -> {
                    Script.Function.expectNumParams(params, 1, "registerCommand");
                    if (params[0] instanceof Script.Function function) {
                      function.call(dispatcher, context, selection);
                    } else {
                      throw new IllegalArgumentException(
                          "registerCommand expected a Function argument but got "
                              + params[0].getClass().getName());
                    }
                    return null;
                  });
      script.exec();
    } catch (Exception e) {
      LOGGER.error("Error reading {}: {}", commandsPath, e);
    }
  }

  private static int tickCount = 0;

  public static void onWorldTick() {
    if (tickCount++ % 20 == 0) {
      boolean ignore = checkScriptForReload("server.pjn") || checkScriptForReload("server.json");
    }
  }

  /**
   * Reloads script if it's changed since it was last loaded.
   *
   * @param scriptFilename name of script file to load
   * @return false if file does not exist, true otherwise
   */
  private static boolean checkScriptForReload(String scriptFilename) {
    Path scriptPath = minescriptDir.resolve(scriptFilename);
    if (!Files.exists(scriptPath)) {
      return false;
    }

    Long loadTime = scriptLoadTimes.get(scriptPath);
    long modifiedTime = 0;
    try {
      modifiedTime = Files.getLastModifiedTime(scriptPath).to(TimeUnit.MILLISECONDS);
      if (loadTime != null && loadTime >= modifiedTime) {
        return true;
      }
    } catch (IOException e) {
      LOGGER.error("Error reading file {}: {}", scriptFilename, e);
      return true;
    }

    scriptLoadTimes.put(scriptPath, modifiedTime);

    try {
      var fileExtension = getFileExtension(scriptFilename);
      Script script = null;
      if (fileExtension.equals("pjn")) {
        script = importScript(scriptPath);
      } else if (fileExtension.equals("json")) {
        try {
          script =
              parseScriptFromAst(
                  scriptPath,
                  Files.readAllLines(scriptPath).stream().collect(Collectors.joining("\n")));
        } catch (Exception e) {
          LOGGER.error("Error reading {}: {}", scriptFilename, e);
        }
      } else {
        LOGGER.error("Unsupported script file type: {}", scriptFilename);
        return true;
      }
      script
          .globals()
          .setVariable(
              "addAnimateHandler",
              (Script.Function)
                  (Object... params) -> {
                    Script.Function.expectNumParams(params, 1, "addAnimateHandler");
                    EventHandlers.addAnimateHandler(scriptPath, (Script.Function) params[0]);
                    return null;
                  });
      script.exec();
    } catch (Exception e) {
      LOGGER.error("Error reading {}: {}", scriptPath, e);
    }
    return true;
  }

  private static Script importScript(Path scriptPath) throws Exception {
    // TODO(maxuser)! install from jar resources
    Path commandPath = minescriptDir.resolve("dump_json_ast.py");

    String[] command = new String[] {commandPath.toString(), scriptPath.toString()};
    var boundCommand =
        new ScriptConfig.BoundCommand(commandPath, command, ScriptRedirect.Pair.DEFAULTS);
    var task = new SubprocessTask(scriptConfig);
    var stdoutConsumer = new StringBuilder();

    int jobId = 0; // TODO(maxuser)! allocate real job id
    int result = task.run(boundCommand, new ServerJob(jobId, stdoutConsumer::append));

    if (result == 0) {
      LOGGER.info("Script command succeeded: {}", boundCommand);
    } else {
      LOGGER.warn("Script command failed with error {}: {}", result, boundCommand);
    }

    return parseScriptFromAst(scriptPath, stdoutConsumer.toString());
  }

  private static String getFileExtension(String filename) {
    if (filename == null || filename.isEmpty()) {
      return "";
    }

    int dotIndex = filename.lastIndexOf('.');
    if (dotIndex == -1 || dotIndex == filename.length() - 1) {
      return "";
    }

    return filename.substring(dotIndex + 1);
  }

  private static Script parseScriptFromAst(Path scriptPath, String jsonAstString) throws Exception {
    EventHandlers.clearJobHandlers(scriptPath);
    JsonElement jsonAst = JsonParser.parseString(jsonAstString);
    var script = new Script();
    script.redirectStdout(s -> LOGGER.info("[{}] {}", scriptPath.getFileName(), s));
    script.parse(jsonAst, scriptPath.getFileName().toString());
    return script;
  }
}
