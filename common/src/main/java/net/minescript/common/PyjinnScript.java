// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minescript.common.mappings.NameMappings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pyjinn.interpreter.Script;
import org.pyjinn.parser.PyjinnParser;

public class PyjinnScript {
  private static final Logger LOGGER = LogManager.getLogger();

  private static final Script.PyDict gameGlobalDict = new Script.PyDict(new ConcurrentHashMap<>());

  private PyjinnScript() {}

  // TODO(maxuser): Merge PyjinnTask into PyjinnJob.
  private static class PyjinnTask implements Task {
    private final Map<Long, Callback> callbackMap = new HashMap<>();
    private final SystemMessageQueue systemMessageQueue;

    public record Callback(Script.Environment env, Script.Function function) {}

    public PyjinnTask(SystemMessageQueue systemMessageQueue) {
      this.systemMessageQueue = systemMessageQueue;
    }

    // TODO(maxuser): Does Task::run make sense for Pyjinn script jobs?
    @Override
    public int run(ScriptConfig.BoundCommand command, JobControl jobControl) {
      return 0;
    }

    /**
     * Sends a return value to the given script function call. Returns true if response succeeds.
     */
    @Override
    public boolean sendResponse(long functionCallId, ScriptValue scriptValue, boolean finalReply) {
      var callback = callbackMap.get(functionCallId);
      if (callback == null) {
        LOGGER.error("No callback found in Pyjinn task for function call {}", functionCallId);
        return false;
      }
      try {
        callback.function.call(callback.env, scriptValue.get());
      } catch (Exception e) {
        ScriptExceptionHandler.reportException(systemMessageQueue, e);
      }
      return true;
    }

    /** Sends an exception to the given script function call. Returns true if response succeeds. */
    @Override
    public boolean sendException(long functionCallId, Exception exception) {
      if (exception instanceof RuntimeException runtimeException) {
        throw runtimeException;
      } else {
        throw new RuntimeException(exception);
      }
    }
  }

  static class PyjinnJob extends Job {
    private static final long ASYNC_FCALL_START_ID = 1000L;

    private final Script script;
    private final PyjinnTask task;
    private final boolean autoExit;
    private long nextFcallId = ASYNC_FCALL_START_ID;
    private boolean isRunningScriptGlobals = false;
    private boolean hasPendingCallbacksAfterExec = false;
    private boolean handlingExit = false;

    public PyjinnJob(
        int jobId,
        ScriptConfig.BoundCommand command,
        PyjinnTask task,
        Script script,
        Config config,
        SystemMessageQueue systemMessageQueue,
        boolean autoExit,
        Runnable doneCallback) {
      super(
          jobId,
          command,
          task,
          config,
          systemMessageQueue,
          Minescript::processMessage,
          doneCallback);
      this.task = task;
      this.script = script;
      this.autoExit = autoExit;
    }

    Script script() {
      return script;
    }

    @Override
    protected void start() {
      setState(JobState.RUNNING);

      try {
        isRunningScriptGlobals = true;
        script.exec();
        isRunningScriptGlobals = false;
        hasPendingCallbacksAfterExec = !task.callbackMap.isEmpty();
      } catch (Exception e) {
        isRunningScriptGlobals = false;
        ScriptExceptionHandler.reportException(systemMessageQueue, e);
        script.exit(1);
        return;
      }

      if (autoExit && !hasPendingCallbacksAfterExec) {
        script.exit(0);
      }
    }

    private void atExit(Integer exitCode) {
      // Ensure that atExit is called at most once.
      if (handlingExit) {
        return;
      }
      try {
        handlingExit = true;
        if (exitCode != null && exitCode.intValue() != 0) {
          systemMessageQueue.logUserError(
              jobSummaryWithStatus("Exited with error code " + exitCode));
        } else if (hasPendingCallbacksAfterExec) {
          // Log an info message about the script exits successfully only if the task had pending
          // callbacks immediately after running all global statements.
          if (state() != JobState.KILLED) {
            setState(JobState.DONE);
          }
          systemMessageQueue.logUserInfo(toString());
        }
      } finally {
        close();
      }
    }

    @Override
    public void requestKill() {
      super.requestKill();
      script.exit(128);
    }

    @Override
    protected void onClose() {
      // Nothing special to do when closing the Pyjinn job.
    }
  }

  public static PyjinnJob createJob(
      int jobId,
      ScriptConfig.BoundCommand boundCommand,
      String[] command,
      String scriptCode,
      Config config,
      SystemMessageQueue systemMessageQueue,
      NameMappings nameMappings,
      boolean autoExit,
      Runnable doneCallback)
      throws Exception {
    var script = loadScript(command, scriptCode, nameMappings);
    var job =
        new PyjinnJob(
            jobId,
            boundCommand,
            new PyjinnTask(systemMessageQueue),
            script,
            config,
            systemMessageQueue,
            autoExit,
            doneCallback);

    script.vars.__setitem__("job", job);
    script.vars.__setitem__("game", gameGlobalDict);
    script.redirectStdout(job::processStdout);
    script.redirectStderr(job::processStderr);
    script.atExit(job::atExit);

    return job;
  }

  private static class MinescriptModuleHandler implements Script.ModuleHandler {
    public MinescriptModuleHandler() {}

    @Override
    public void onParseImport(Script.Module module, Script.Import importModules) {
      for (var importedModule : importModules.modules()) {
        switch (importedModule.name()) {
          case "minescript":
          case "system.pyj.minescript":
            module.globals().setVariable("__has_explicit_minescript_import__", true);
            return;
        }
      }
    }

    @Override
    public void onParseImport(Script.Module module, Script.ImportFrom fromModule) {
      switch (fromModule.module()) {
        case "minescript":
        case "system.pyj.minescript":
          module.globals().setVariable("__has_explicit_minescript_import__", true);
          return;
      }
    }

    private static ImmutableList<Path> importDirs =
        ImmutableList.of(Paths.get("minescript"), Paths.get("minescript", "system", "pyj"));

    @Override
    public Path getModulePath(String name) {
      Path relativeImportPath = Script.ModuleHandler.super.getModulePath(name);
      for (Path dir : importDirs) {
        Path path = dir.resolve(relativeImportPath);
        if (Files.exists(path)) {
          LOGGER.info("Resolved import of {} to {}", name, path);
          return path;
        }
      }
      throw new IllegalArgumentException(
          "No module named '%s' (%s) found in import dirs: %s"
              .formatted(name, relativeImportPath, importDirs));
    }

    @Override
    public void onExecModule(Script.Module module) {
      LOGGER.info("Running Minescript module handler for Pyjinn module: {}", module.name());
      // The canonical module name is the filename relative to the Minecraft dir without the ".py"
      // extension and dots dir separators replaced with dots.
      if (module.name().equals("minescript.system.pyj.minescript")) {
        LOGGER.info("Adding built-in functions to Minescript Pyjinn module");
        module.globals().setVariable("add_event_listener", new AddEventListener());
        module.globals().setVariable("remove_event_listener", new RemoveEventListener());
      } else if (module.name().equals("__main__")
          && !(Boolean) module.globals().vars().get("__has_explicit_minescript_import__", false)) {
        LOGGER.info("Adding implicit import of Minescript Pyjinn module");
        module
            .globals()
            .globalStatements()
            .add(
                0,
                new Script.ImportFrom(
                    -1,
                    "system.pyj.minescript",
                    List.of(new Script.ImportName("*", Optional.empty()))));
        module.globals().setVariable("add_event_listener", new AddEventListener());
        module.globals().setVariable("remove_event_listener", new RemoveEventListener());
      }
    }
  }

  public static Script loadScript(
      String[] scriptCommand, String scriptCode, NameMappings nameMappings) throws Exception {
    String scriptFilename = scriptCommand[0];

    var moduleHandler = new MinescriptModuleHandler();
    var script =
        new Script(
            scriptFilename,
            PyjinnScript.class.getClassLoader(),
            moduleHandler,
            nameMappings::getRuntimeClassName,
            nameMappings::getPrettyClassName,
            nameMappings::getRuntimeFieldName,
            nameMappings::getRuntimeMethodNames);

    script.vars.__setitem__("sys_version", Script.versionInfo().toString());
    script.vars.__setitem__("sys_argv", scriptCommand);

    // When a script is created for a PyjinnJob, the script's stdout and stderr will be redirected
    // to the chat. But by default, log the output in case this script isn't running within a
    // PyjinnJob.
    String scriptShortName = Paths.get(scriptFilename).getFileName().toString();
    script.redirectStdout(s -> LOGGER.info("[{} stdout] {}", scriptShortName, s));
    script.redirectStderr(s -> LOGGER.info("[{} stderr] {}", scriptShortName, s));
    script.setZombieCallbackHandler(
        (String scriptName, String callable, int count) -> {
          if (count == 1 || count % 10000 == 0) {
            LOGGER.warn(
                "[{} zombie] Invocation of {} (count: {}) defined in script that already"
                    + " exited: {}",
                scriptShortName,
                callable,
                count,
                scriptName);
          }
        });

    JsonElement scriptAst = PyjinnParser.parse(scriptFilename, scriptCode);
    script.parse(scriptAst, scriptFilename);
    return script;
  }

  // TODO(maxuser): Share these event names with Minescript.getDispatcherForEventName().
  private static final Set<String> EVENT_NAMES =
      Set.of(
          "tick",
          "render",
          "key",
          "mouse",
          "chat",
          "outgoing_chat_intercept",
          "add_entity",
          "block_update",
          "explosion",
          "take_item",
          "damage",
          "chunk",
          "world");

  public static class AddEventListener implements Script.Function {
    @Override
    public Object call(Script.Environment env, Object... params) {
      expectMinParams(params, 2);
      expectMaxParams(params, 3);
      if (!(params[0] instanceof String eventName)) {
        throw new IllegalArgumentException(
            "Expected first param to add_event_listener to be string (event type) but got "
                + params[0].toString());
      }

      if (!EVENT_NAMES.contains(eventName)) {
        throw new IllegalArgumentException(
            "Unsupported event type: %s. Must be one of: %s".formatted(eventName, EVENT_NAMES));
      }

      var kwargs =
          (params.length > 2 && params[2] instanceof Script.KeywordArgs ka)
              ? ka
              : new Script.KeywordArgs();

      try {
        var script = (Script) env.getVariable("__script__");
        var job = (PyjinnJob) script.vars.__getitem__("job");
        long listenerId = job.nextFcallId++;

        // registerEventListener() and startEventListener() were split to accomodate the semantics
        // of externally executed (Python) scripts, because the "register" call was to return a
        // handle to the calling script to refer to the listener while the "start" has no return
        // value (Minescript::runExternalScriptFunction returns Optional.empty()) to indicate that
        // it's an async operation that will return value(s) at a later time.

        if (params[1] instanceof Script.Function callback) {
          job.task.callbackMap.put(listenerId, new PyjinnTask.Callback(env, callback));
          Minescript.registerEventListener(job, listenerId, eventName, kwargs);
          Minescript.startEventListener(job, listenerId, eventName, listenerId);
        } else {
          throw new IllegalArgumentException(
              "Expected second param to `add_event_listener` to be callable but got %s"
                  .formatted(params[1]));
        }
        return listenerId;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static class RemoveEventListener implements Script.Function {
    @Override
    public Object call(Script.Environment env, Object... params) {
      expectNumParams(params, 1);
      if (params[0] instanceof Number listenerNum) {
        try {
          var script = (Script) env.getVariable("__script__");
          var job = (PyjinnJob) script.vars.__getitem__("job");
          Long listenerId = listenerNum.longValue();
          job.cancelOperation(listenerId);
          var removedListener = job.task.callbackMap.remove(listenerId);
          if (removedListener != null) {
            // If this is the last listener being removed and the job is no longer running global
            // statements, then kill the job.
            if (job.autoExit && job.task.callbackMap.isEmpty() && !job.isRunningScriptGlobals) {
              script.exit(0);
            }
            return true;
          } else {
            return false;
          }
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      } else {
        throw new IllegalArgumentException(
            "Expected param to `remove_event_listener` to be java.lang.Long but got %s"
                .formatted(params[0]));
      }
    }
  }
}
