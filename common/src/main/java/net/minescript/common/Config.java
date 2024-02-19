// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

public class Config {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final Pattern CONFIG_LINE_RE = Pattern.compile("([^=]+)=(.*)");
  private static final Pattern DOUBLE_QUOTED_STRING_RE = Pattern.compile("\"(.*)\"");
  private static final Pattern CONFIG_AUTORUN_RE = Pattern.compile("autorun\\[(.*)\\]");
  private static Gson GSON = new GsonBuilder().serializeNulls().create();

  private final String minescriptDirName;
  private final Path absMinescriptDir;
  private final File file;
  private long lastConfigLoadTime = 0;

  private String pythonLocation;
  private final ImmutableList<String> builtinCommands;
  private final ImmutableSet<String> ignoreDirsForCompletions;
  private ScriptConfig scriptConfig;
  private final List<String> commands = new ArrayList<>();

  // Regex pattern for ignoring lines of output from stderr of Python scripts.
  private Pattern stderrChatIgnorePattern = Pattern.compile("^$");

  // Map from world name (or "*" for all) to a list of Minescript/Minecraft commands.
  private Map<String, List<Message>> autorunCommands = new ConcurrentHashMap<>();

  private boolean logChunkLoadEvents = false;
  private boolean scriptFunctionDebugOutptut = false;
  private boolean incrementalCommandSuggestions = false;
  private int minescriptTicksPerCycle = 1;
  private int minescriptCommandsPerCycle = 15;

  public Config(
      String minescriptDirName,
      String configFileName,
      ImmutableList<String> builtinCommands,
      ImmutableSet<String> ignoreDirsForCompletions) {
    this.minescriptDirName = minescriptDirName;
    this.absMinescriptDir = Paths.get(System.getProperty("user.dir"), minescriptDirName);
    this.file = new File(Paths.get(minescriptDirName, configFileName).toString());
    this.builtinCommands = builtinCommands;
    this.ignoreDirsForCompletions = ignoreDirsForCompletions;
  }

  public File file() {
    return file;
  }

  /** Loads config from {@code minescript/config.txt} if the file has changed since last loaded. */
  public void load() {
    if (file.lastModified() < lastConfigLoadTime) {
      return;
    }

    lastConfigLoadTime = System.currentTimeMillis();
    commands.clear();
    autorunCommands.clear();
    scriptConfig = new ScriptConfig(minescriptDirName, builtinCommands, ignoreDirsForCompletions);

    try (var reader = new BufferedReader(new FileReader(file.getPath()))) {
      String line;
      String continuedLine = null;
      int lineNum = 0;
      int[] lineNumWrapper = new int[1]; // array wrapper shields mutable lineNum in lambda

      while ((line = reader.readLine()) != null) {
        lineNumWrapper[0] = ++lineNum;
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

          setValue(
              name,
              value,
              status -> {
                if (status.success()) {
                  LOGGER.info(status.message());
                } else {
                  LOGGER.error("config.txt:{}: {}", lineNumWrapper[0], status.message());
                }
              });
        } else {
          LOGGER.warn("config.txt:{}: unable parse config line: {}", lineNum, line);
        }
      }
    } catch (IOException e) {
      LOGGER.error("Exception loading config file: {}", e);
    }
  }

  public ScriptConfig scriptConfig() {
    return scriptConfig;
  }

  public List<Message> getAutorunCommands(String worldName) {
    return autorunCommands.get(worldName);
  }

  public Pattern stderrChatIgnorePattern() {
    return stderrChatIgnorePattern;
  }

  public void setLogChunkLoadEvents(boolean enable) {
    logChunkLoadEvents = enable;
  }

  public boolean logChunkLoadEvents() {
    return logChunkLoadEvents;
  }

  public void setScriptFunctionDebugOutptut(boolean enable) {
    scriptFunctionDebugOutptut = enable;
  }

  public boolean scriptFunctionDebugOutptut() {
    return scriptFunctionDebugOutptut;
  }

  public void setIncrementalCommandSuggestions(boolean enable) {
    incrementalCommandSuggestions = enable;
  }

  public boolean incrementalCommandSuggestions() {
    return incrementalCommandSuggestions;
  }

  public void setMinescriptCommandsPerCycle(int num) {
    minescriptCommandsPerCycle = num;
  }

  public int minescriptCommandsPerCycle() {
    return minescriptCommandsPerCycle;
  }

  public void setMinescriptTicksPerCycle(int num) {
    minescriptTicksPerCycle = num;
  }

  public int minescriptTicksPerCycle() {
    return minescriptTicksPerCycle;
  }

  public void forEachValue(BiConsumer<String, String> consumer) {
    consumer.accept("python", getValue("python"));
    consumer.accept("command", getValue("command"));
    consumer.accept("escape_command_double_quotes", getValue("escape_command_double_quotes"));
    consumer.accept("path", getValue("path"));
    consumer.accept("minescript_commands_per_cycle", getValue("minescript_commands_per_cycle"));
    consumer.accept("minescript_ticks_per_cycle", getValue("minescript_ticks_per_cycle"));
    consumer.accept(
        "minescript_incremental_command_suggestions",
        getValue("minescript_incremental_command_suggestions"));
    consumer.accept(
        "minescript_script_function_debug_outptut",
        getValue("minescript_script_function_debug_outptut"));
    consumer.accept(
        "minescript_log_chunk_load_events", getValue("minescript_log_chunk_load_events"));
    consumer.accept("stderr_chat_ignore_pattern", getValue("stderr_chat_ignore_pattern"));

    for (var entry : autorunCommands.entrySet()) {
      var worldName = entry.getKey();
      var commands = entry.getValue();
      consumer.accept(
          String.format("autorun[%s]", worldName),
          String.join("\n", commands.stream().map(Message::value).collect(Collectors.toList())));
    }
  }

  public String getValue(String name) {
    switch (name) {
      case "python":
        return pythonLocation;

      case "command":
        return String.join("\n", commands);

      case "escape_command_double_quotes":
        return String.valueOf(scriptConfig.escapeCommandDoubleQuotes());

      case "path":
        return String.join(
            File.pathSeparator,
            scriptConfig.commandPath().stream().map(Path::toString).collect(Collectors.toList()));

      case "minescript_commands_per_cycle":
        return String.valueOf(minescriptCommandsPerCycle);

      case "minescript_ticks_per_cycle":
        return String.valueOf(minescriptTicksPerCycle);

      case "minescript_incremental_command_suggestions":
        return String.valueOf(incrementalCommandSuggestions);

      case "minescript_script_function_debug_outptut":
        return String.valueOf(scriptFunctionDebugOutptut);

      case "minescript_log_chunk_load_events":
        return String.valueOf(logChunkLoadEvents);

      case "stderr_chat_ignore_pattern":
        return stderrChatIgnorePattern.toString();

      default:
        {
          var match = CONFIG_AUTORUN_RE.matcher(name);
          if (match.matches()) {
            String worldName = match.group(1);
            var commands = autorunCommands.get(worldName);
            if (commands == null) {
              return "<null>";
            } else {
              return String.join(
                  "\n", commands.stream().map(Message::value).collect(Collectors.toList()));
            }
          } else {
            throw new IllegalArgumentException(
                String.format("Unrecognized config var: \"%s\"", name));
          }
        }
    }
  }

  public record Status(boolean success, String message) {}

  public void setValue(String name, String value, Consumer<Status> out) {
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
        reportInfo(out, "Setting config var: {} = \"{}\" (\"{}\")", name, value, pythonLocation);

        // `python3 -u` for unbuffered stdout and stderr.
        var commandPattern = ImmutableList.of(pythonLocation, "-u", "{command}", "{args}");

        var environmentVars =
            ImmutableList.of(
                "PYTHONPATH="
                    + String.join(
                        File.pathSeparator,
                        ImmutableList.of(
                            absMinescriptDir.resolve(Paths.get("system", "lib")).toString(),
                            absMinescriptDir.toString())));

        try {
          scriptConfig.configureFileType(
              new ScriptConfig.CommandConfig(".py", commandPattern, environmentVars));
        } catch (Exception e) {
          reportError(out, "Failed to configure .py script execution: {}", e.toString());
        }

        break;

      case "command":
        commands.add(value);
        try {
          var commandConfig = GSON.fromJson(value, ScriptConfig.CommandConfig.class);
          scriptConfig.configureFileType(commandConfig);
          reportInfo(out, "Configured script execution for \"{}\"", commandConfig.extension());
        } catch (Exception e) {
          reportError(out, "Failed to configure script execution: {}", e.toString());
        }
        break;

      case "escape_command_double_quotes":
        boolean isEnabled = Boolean.valueOf(value);
        scriptConfig.setEscapeCommandDoubleQuotes(isEnabled);
        reportInfo(out, "Setting escape_command_double_quotes to {}", isEnabled);
        break;

      case "path":
        var commandPath =
            Arrays.stream(value.split(Pattern.quote(File.pathSeparator), -1))
                .map(Paths::get)
                .collect(Collectors.toList());
        scriptConfig.setCommandPath(commandPath);
        reportInfo(out, "Setting path to {}", commandPath);
        break;

      case "minescript_commands_per_cycle":
        try {
          minescriptCommandsPerCycle = Integer.valueOf(value);
          reportInfo(
              out, "Setting minescript_commands_per_cycle to {}", minescriptCommandsPerCycle);
        } catch (NumberFormatException e) {
          reportError(out, "Unable to parse minescript_commands_per_cycle as integer: {}", value);
        }
        break;

      case "minescript_ticks_per_cycle":
        try {
          minescriptTicksPerCycle = Integer.valueOf(value);
          reportInfo(out, "Setting minescript_ticks_per_cycle to {}", minescriptTicksPerCycle);
        } catch (NumberFormatException e) {
          reportError(out, "Unable to parse minescript_ticks_per_cycle as integer: {}", value);
        }
        break;

      case "minescript_incremental_command_suggestions":
        incrementalCommandSuggestions = Boolean.valueOf(value);
        reportInfo(
            out,
            "Setting minescript_incremental_command_suggestions to {}",
            incrementalCommandSuggestions);
        break;

      case "minescript_script_function_debug_outptut":
        scriptFunctionDebugOutptut = Boolean.valueOf(value);
        reportInfo(
            out,
            "Setting minescript_script_function_debug_outptut to {}",
            scriptFunctionDebugOutptut);
        break;

      case "minescript_log_chunk_load_events":
        logChunkLoadEvents = Boolean.valueOf(value);
        reportInfo(out, "Setting minescript_log_chunk_load_events to {}", logChunkLoadEvents);
        break;

      case "stderr_chat_ignore_pattern":
        stderrChatIgnorePattern = Pattern.compile(value);
        reportInfo(out, "Setting stderr_chat_ignore_pattern to {}", value);
        break;

      default:
        {
          var match = CONFIG_AUTORUN_RE.matcher(name);
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
            reportInfo(out, "Added autorun command `{}` for `{}`", command, worldName);
          } else {
            reportError(out, "Unrecognized config var: {}", name);
          }
        }
    }
  }

  private void reportInfo(Consumer<Status> out, String messagePattern, Object... arguments) {
    out.accept(new Status(true, ParameterizedMessage.format(messagePattern, arguments)));
  }

  public void reportError(Consumer<Status> out, String messagePattern, Object... arguments) {
    out.accept(new Status(false, ParameterizedMessage.format(messagePattern, arguments)));
  }
}
