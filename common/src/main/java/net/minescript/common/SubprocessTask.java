// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SubprocessTask implements Task {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final Gson GSON = new GsonBuilder().serializeNulls().create();

  private final Config config;
  private JobControl jobControl;
  private Process process;
  private BufferedWriter stdinWriter;

  public SubprocessTask(Config config) {
    this.config = config;
  }

  @Override
  public int run(ScriptConfig.BoundCommand command, JobControl jobControl) {
    if (this.jobControl != null) {
      throw new IllegalStateException("SubprocessTask can be run only once: " + jobControl);
    }

    this.jobControl = jobControl;

    var exec = config.scriptConfig().getExecutableCommand(command);
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

    var stdoutThread =
        new Thread(this::processStdout, Thread.currentThread().getName() + "-stdout");
    stdoutThread.start();

    var stderrThread =
        new Thread(this::processStderr, Thread.currentThread().getName() + "-stderr");
    stderrThread.start();

    try {
      while (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
        if (jobControl.state() == JobState.KILLED) {
          LOGGER.info("Killing script process for job `{}`", jobControl);
          process.destroy();
          stdoutThread.interrupt();
          stderrThread.interrupt();
          return -5;
        }
      }
    } catch (InterruptedException e) {
      LOGGER.warn("Task thread interrupted while awaiting subprocess for job `{}`", jobControl);
    }

    int result = process.exitValue();
    LOGGER.info("Script process exited with {} for job `{}`", result, jobControl);
    return result;
  }

  private void processStdout() {
    try (var stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      while ((line = stdoutReader.readLine()) != null) {
        jobControl.yield();
        jobControl.processStdout(line);
      }
    } catch (IOException e) {
      LOGGER.error(
          "IOException while reading subprocess stdout for job `{}`: {}",
          jobControl,
          e.getMessage());
    } finally {
      if (Thread.interrupted()) {
        LOGGER.warn("Thread interrupted while reading subprocess stdout for job `{}`", jobControl);
      }
    }
  }

  private void processStderr() {
    try (var stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
      String line;
      while ((line = stderrReader.readLine()) != null) {
        jobControl.yield();
        jobControl.log(line);
      }
    } catch (IOException e) {
      LOGGER.error(
          "IOException while reading subprocess stderr for job `{}`: {}",
          jobControl,
          e.getMessage());
    } finally {
      if (Thread.interrupted()) {
        LOGGER.warn("Thread interrupted while reading subprocess stderr for job `{}`", jobControl);
      }
    }
  }

  @Override
  public boolean sendResponse(long functionCallId, ScriptValue returnValue, boolean finalReply) {
    if (!canRespond()) {
      LOGGER.warn(
          "Subprocess unresponsive to response from funcCallId {} for job {}: {}",
          functionCallId,
          jobControl,
          returnValue.get());
      return false;
    }
    try {
      var response = new JsonObject();
      response.addProperty("fcid", functionCallId);
      response.add("retval", returnValue.toJson());
      if (finalReply) {
        response.addProperty("conn", "close");
      }
      String responseString = GSON.toJson(response);
      synchronized (this) {
        stdinWriter.write(responseString);
        stdinWriter.newLine();
        stdinWriter.flush();
      }
      return true;
    } catch (IOException e) {
      LOGGER.error(
          "IOException in SubprocessTask sendResponse for job {}: {}", jobControl, e.getMessage());
      return false;
    }
  }

  @Override
  public boolean sendException(long functionCallId, Exception exception) {
    if (!canRespond()) {
      LOGGER.warn(
          "Subprocess unresponsive to exception from funcCallId {} for job {}: {}",
          functionCallId,
          jobControl,
          exception);
      return false;
    }
    try {
      var response = new JsonObject();
      response.addProperty("fcid", functionCallId);
      response.addProperty("conn", "close");
      var json = GSON.toJsonTree(ExceptionInfo.fromException(exception));
      LOGGER.warn("Translating Java exception as JSON: {}", json);
      response.add("except", json);

      String responseString = GSON.toJson(response);
      synchronized (this) {
        stdinWriter.write(responseString);
        stdinWriter.newLine();
        stdinWriter.flush();
      }
      return true;
    } catch (IOException e) {
      LOGGER.error(
          "IOException in SubprocessTask sendException for job {}: {}", jobControl, e.getMessage());
      return false;
    }
  }

  private boolean canRespond() {
    return process != null && process.isAlive() && stdinWriter != null;
  }
}
