// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SubprocessTask implements Task {
  private static final Logger LOGGER = LogManager.getLogger();
  private static Gson GSON = new GsonBuilder().serializeNulls().create();

  private final ScriptConfig scriptConfig;
  private Process process;
  private BufferedWriter stdinWriter;

  public SubprocessTask(ScriptConfig scriptConfig) {
    this.scriptConfig = scriptConfig;
  }

  @Override
  public int run(ScriptConfig.BoundCommand command, JobControl jobControl) {
    var exec = scriptConfig.getExecutableCommand(command);
    if (exec == null) {
      jobControl.log(
          "Cannot run \"{}\" because execution is not configured for \"{}\" files.",
          command.scriptPath(),
          command.fileExtension());
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
