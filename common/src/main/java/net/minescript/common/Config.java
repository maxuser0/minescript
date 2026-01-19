// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import static java.util.stream.Collectors.joining;

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

/** Manages Minescript configuration settings, loaded from `config.txt`. */
public class Config {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final Pattern CONFIG_LINE_RE = Pattern.compile("([^=]+)=(.*)");
  private static final Pattern DOUBLE_QUOTED_STRING_RE = Pattern.compile("\"(.*)\"");
  private static final Pattern CONFIG_AUTORUN_RE = Pattern.compile("autorun\\[(.*)\\]");
  private static Gson GSON = new GsonBuilder().serializeNulls().create();

  private static final ImmutableList<String> VARIABLE_LIST =
      ImmutableList.of(
          "python",
          "command",
          "escape_command_double_quotes",
          "command_path",
          "pyjinn_import_path",
          "compile_pyjinn",
          "max_commands_per_cycle",
          "command_cycle_deadline_usecs",
          "ticks_per_cycle",
          "incremental_command_suggestions",
          "debug_output",
          "stderr_chat_ignore_pattern",
          "minescript_on_chat_received_event",
          "secondary_enter_key_code",
          "report_job_success_threshold_millis",
          "autorun[");

  private static final ImmutableList<String> CONFIG_VARIABLE_LIST =
      ImmutableList.copyOf(
          VARIABLE_LIST.stream().map(s -> "config " + s).collect(Collectors.toList()));

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

  private boolean minescriptOnChatReceivedEvent = false;

  // Default secondary `enter` key code to value of KEY_KP_ENTER from GLFW.
  private int secondaryEnterKeyCode = 335;

  // Map from world name (or "*" for all) to a list of Minescript/Minecraft commands.
  private Map<String, List<Message>> autorunCommands = new ConcurrentHashMap<>();

  private boolean debugOutput = false;
  private boolean incrementalCommandSuggestions = false;
  private int ticksPerCycle = 1;
  private boolean compilePyjinn = true;
  private int maxCommandsPerCycle = 15;
  private int commandCycleDeadlineUsecs = 10_000; // 10 milliseconds
  private int reportJobSuccessThresholdMillis = 3000;

  /**
   * Constructs a new Config object.
   *
   * @param minescriptDirName The name of the Minescript directory.
   * @param configFileName The name of the configuration file within the Minescript directory.
   * @param builtinCommands A list of commands that are built-in and always available.
   * @param ignoreDirsForCompletions A set of directory names to ignore when providing command
   *     completions.
   */
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

  /**
   * Returns the {@link File} object representing the configuration file.
   *
   * @return The configuration file.
   */
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

  /**
   * Returns the script configuration, which includes settings for running external scripts.
   *
   * @return The current {@link ScriptConfig}.
   */
  public ScriptConfig scriptConfig() {
    return scriptConfig;
  }

  /**
   * Gets the list of commands to be executed automatically when a specific world is loaded.
   *
   * @param worldName The name of the world.
   * @return A list of {@link Message} objects representing the autorun commands, or null if none
   *     are defined.
   */
  public List<Message> getAutorunCommands(String worldName) {
    return autorunCommands.get(worldName);
  }

  /**
   * Returns the regex pattern used to filter stderr messages from being displayed in chat.
   *
   * @return The compiled {@link Pattern} for ignoring stderr lines.
   */
  public Pattern stderrChatIgnorePattern() {
    return stderrChatIgnorePattern;
  }

  /**
   * Checks if the `on_chat_received` event is enabled.
   *
   * @return True if the event is enabled, false otherwise.
   */
  public boolean minescriptOnChatReceivedEvent() {
    return minescriptOnChatReceivedEvent;
  }

  /**
   * Gets the key code configured to act as a secondary "Enter" key in the chat input.
   *
   * @return The integer key code.
   */
  public int secondaryEnterKeyCode() {
    return secondaryEnterKeyCode;
  }

  /**
   * Gets the time threshold in milliseconds, above which successful jobs will report their
   * completion time.
   *
   * @return The threshold in milliseconds.
   */
  public int reportJobSuccessThresholdMillis() {
    return reportJobSuccessThresholdMillis;
  }

  /**
   * Enables or disables debug output.
   *
   * @param enable True to enable debug output, false to disable.
   */
  public void setDebugOutptut(boolean enable) {
    debugOutput = enable;
  }

  /**
   * Checks if debug output is enabled.
   *
   * @return True if debug output is enabled, false otherwise.
   */
  public boolean debugOutput() {
    return debugOutput;
  }

  /**
   * Enables or disables incremental command suggestions.
   *
   * @param enable True to enable incremental suggestions, false to disable.
   */
  public void setIncrementalCommandSuggestions(boolean enable) {
    incrementalCommandSuggestions = enable;
  }

  /**
   * Checks if incremental command suggestions are enabled.
   *
   * @return True if incremental suggestions are enabled, false otherwise.
   */
  public boolean incrementalCommandSuggestions() {
    return incrementalCommandSuggestions;
  }

  /**
   * Gets whether compilation is enabled for Pyjinn scripts.
   *
   * @return True if compilation is enabled for Pyjinn scripts, false otherwise.
   */
  public boolean compilePyjinn() {
    return compilePyjinn;
  }

  /**
   * Gets the maximum number of commands that can be executed in a single game cycle.
   *
   * @return The maximum number of commands per cycle.
   */
  public int maxCommandsPerCycle() {
    return maxCommandsPerCycle;
  }

  /**
   * Gets the deadline in microseconds for processing commands within a single cycle.
   *
   * @return The command cycle deadline in microseconds.
   */
  public int commandCycleDeadlineUsecs() {
    return commandCycleDeadlineUsecs;
  }

  /**
   * Gets the number of game ticks that constitute a single processing cycle for Minescript
   * commands.
   *
   * @return The number of ticks per cycle.
   */
  public int ticksPerCycle() {
    return ticksPerCycle;
  }

  /**
   * Gets the list of all available configuration variable names for use in commands.
   *
   * @return An immutable list of configuration variable names.
   */
  public ImmutableList<String> getConfigVariables() {
    return CONFIG_VARIABLE_LIST;
  }

  /**
   * Iterates over all configuration key-value pairs.
   *
   * @param consumer A {@link BiConsumer} that accepts the name and string value of each
   *     configuration setting.
   */
  public void forEachValue(BiConsumer<String, String> consumer) {
    consumer.accept("python", getValue("python"));
    consumer.accept("command", getValue("command"));
    consumer.accept("escape_command_double_quotes", getValue("escape_command_double_quotes"));
    consumer.accept("command_path", getValue("command_path"));
    consumer.accept("pyjinn_import_path", getValue("pyjinn_import_path"));
    consumer.accept("compile_pyjinn", getValue("compile_pyjinn"));
    consumer.accept("max_commands_per_cycle", getValue("max_commands_per_cycle"));
    consumer.accept("command_cycle_deadline_usecs", getValue("command_cycle_deadline_usecs"));
    consumer.accept("ticks_per_cycle", getValue("ticks_per_cycle"));
    consumer.accept("incremental_command_suggestions", getValue("incremental_command_suggestions"));
    consumer.accept("debug_output", getValue("debug_output"));
    consumer.accept("stderr_chat_ignore_pattern", getValue("stderr_chat_ignore_pattern"));
    consumer.accept(
        "minescript_on_chat_received_event", getValue("minescript_on_chat_received_event"));
    consumer.accept("secondary_enter_key_code", getValue("secondary_enter_key_code"));
    consumer.accept(
        "report_job_success_threshold_millis", getValue("report_job_success_threshold_millis"));

    for (var entry : autorunCommands.entrySet()) {
      var worldName = entry.getKey();
      var commands = entry.getValue();
      consumer.accept(
          String.format("autorun[%s]", worldName),
          String.join("\n", commands.stream().map(Message::value).collect(Collectors.toList())));
    }
  }

  /**
   * Retrieves the string value of a configuration setting by its name.
   *
   * @param name The name of the configuration variable.
   * @return The string representation of the variable's value.
   * @throws IllegalArgumentException if the name is not a recognized configuration variable.
   */
  public String getValue(String name) {
    switch (name) {
      case "python":
        return pythonLocation;

      case "command":
        return String.join("\n", commands);

      case "escape_command_double_quotes":
        return String.valueOf(scriptConfig.escapeCommandDoubleQuotes());

      case "command_path":
        return scriptConfig.commandPath().stream()
            .map(FilePattern::toString)
            .collect(joining(File.pathSeparator));

      case "pyjinn_import_path":
        return scriptConfig.pyjinnImportPath().stream()
            .map(FilePattern::toString)
            .collect(joining(File.pathSeparator));

      case "compile_pyjinn":
        return String.valueOf(compilePyjinn);

      case "max_commands_per_cycle":
        return String.valueOf(maxCommandsPerCycle);

      case "command_cycle_deadline_usecs":
        return String.valueOf(commandCycleDeadlineUsecs);

      case "ticks_per_cycle":
        return String.valueOf(ticksPerCycle);

      case "incremental_command_suggestions":
        return String.valueOf(incrementalCommandSuggestions);

      case "debug_output":
        return String.valueOf(debugOutput);

      case "stderr_chat_ignore_pattern":
        return stderrChatIgnorePattern.toString();

      case "minescript_on_chat_received_event":
        return String.valueOf(minescriptOnChatReceivedEvent);

      case "secondary_enter_key_code":
        return String.valueOf(secondaryEnterKeyCode);

      case "report_job_success_threshold_millis":
        return String.valueOf(reportJobSuccessThresholdMillis);

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

  /**
   * Represents the status of a configuration-setting operation.
   *
   * @param success True if the operation was successful, false otherwise.
   * @param message A descriptive message about the outcome.
   */
  public record Status(boolean success, String message) {}

  /**
   * Sets the value of a configuration variable by its name.
   *
   * @param name The name of the configuration variable to set.
   * @param value The new string value for the variable.
   * @param out A {@link Consumer} that receives a {@link Status} object indicating the outcome of
   *     the operation.
   */
  public void setValue(String name, String value, Consumer<Status> out) {
    switch (name) {
      case "python":
        final String python;
        if (System.getProperty("os.name").startsWith("Windows")) {
          python =
              value.contains("%userprofile%")
                  ? value.replace("%userprofile%", System.getProperty("user.home"))
                  : value;
        } else {
          // This does not support "~otheruser/..." syntax. But that would be odd anyway.
          python =
              value.startsWith("~/")
                  ? value.replaceFirst(
                      "~", Matcher.quoteReplacement(System.getProperty("user.home")))
                  : value;
        }

        // `python3 -u` for unbuffered stdout and stderr.
        var commandPattern = ImmutableList.of(python, "-u", "{command}", "{args}");

        var environmentVars =
            ImmutableList.of(
                "PYTHONPATH="
                    + String.join(
                        File.pathSeparator,
                        ImmutableList.of(
                            absMinescriptDir.resolve(Paths.get("system", "lib")).toString(),
                            absMinescriptDir.toString())));

        try {
          var commandConfig =
              new ScriptConfig.CommandConfig(".py", commandPattern, environmentVars);
          scriptConfig.configureSubprocessFileType(commandConfig);
          reportInfo(out, "Setting config var: {} = \"{}\" ({})", name, value, commandConfig);
          pythonLocation = python;
        } catch (Exception e) {
          reportError(out, "Failed to configure .py script execution: {}", e.toString());
        }

        break;

      case "command":
        commands.add(value);
        try {
          var commandConfig = GSON.fromJson(value, ScriptConfig.CommandConfig.class);
          scriptConfig.configureSubprocessFileType(commandConfig);
          reportInfo(out, "Configured script execution for \"{}\"", commandConfig);
        } catch (Exception e) {
          reportError(out, "Failed to configure script execution: {}", e.toString());
        }
        break;

      case "escape_command_double_quotes":
        boolean isEnabled = Boolean.valueOf(value);
        scriptConfig.setEscapeCommandDoubleQuotes(isEnabled);
        reportInfo(out, "Setting escape_command_double_quotes to {}", isEnabled);
        break;

      case "command_path":
        var commandPath =
            Arrays.stream(value.split(Pattern.quote(File.pathSeparator), -1))
                .map(FilePattern::of)
                .toList();
        scriptConfig.setCommandPath(commandPath);
        reportInfo(out, "Setting command_path to {}", commandPath);
        break;

      case "pyjinn_import_path":
        var pyjinnImportPath =
            Arrays.stream(value.split(Pattern.quote(File.pathSeparator), -1))
                .map(FilePattern::of)
                .toList();
        scriptConfig.setPyjinnImportPath(pyjinnImportPath);
        reportInfo(out, "Setting pyjinn_import_path to {}", pyjinnImportPath);
        break;

      case "compile_pyjinn":
        boolean compile = Boolean.valueOf(value);
        compilePyjinn = compile;
        reportInfo(out, "Setting compile_pyjinn to {}", compile);
        break;

      case "max_commands_per_cycle":
        try {
          int numCommands = Integer.valueOf(value);
          if (numCommands < 1) numCommands = 1;
          maxCommandsPerCycle = numCommands;
          reportInfo(out, "Setting max_commands_per_cycle to {}", maxCommandsPerCycle);
        } catch (NumberFormatException e) {
          reportError(out, "Unable to parse max_commands_per_cycle as integer: {}", value);
        }
        break;

      case "command_cycle_deadline_usecs":
        try {
          int deadline = Integer.valueOf(value);
          if (deadline < 1) deadline = 1;
          commandCycleDeadlineUsecs = deadline;
          reportInfo(out, "Setting command_cycle_deadline_usecs to {}", commandCycleDeadlineUsecs);
        } catch (NumberFormatException e) {
          reportError(out, "Unable to parse command_cycle_deadline_usecs as integer: {}", value);
        }
        break;

      case "ticks_per_cycle":
        try {
          int ticks = Integer.valueOf(value);
          if (ticks < 1) ticks = 1;
          ticksPerCycle = ticks;
          reportInfo(out, "Setting ticks_per_cycle to {}", ticksPerCycle);
        } catch (NumberFormatException e) {
          reportError(out, "Unable to parse ticks_per_cycle as integer: {}", value);
        }
        break;

      case "incremental_command_suggestions":
        incrementalCommandSuggestions = Boolean.valueOf(value);
        reportInfo(
            out,
            "Setting minescript_incremental_command_suggestions to {}",
            incrementalCommandSuggestions);
        break;

      case "debug_output":
        debugOutput = Boolean.valueOf(value);
        reportInfo(out, "Setting debug_output to {}", debugOutput);
        break;

      case "stderr_chat_ignore_pattern":
        stderrChatIgnorePattern = Pattern.compile(value);
        reportInfo(out, "Setting stderr_chat_ignore_pattern to {}", value);
        break;

      case "minescript_on_chat_received_event":
        {
          boolean enable = Boolean.valueOf(value);
          minescriptOnChatReceivedEvent = enable;
          reportInfo(
              out,
              "Minescript execution on client chat events {}.",
              enable ? "enabled" : "disabled");
          if (enable) {
            reportInfo(
                out,
                "e.g. add command to command block: [execute as Player run tell Player \\help]");
          }
        }
        break;

      case "secondary_enter_key_code":
        try {
          secondaryEnterKeyCode = Integer.valueOf(value);
          reportInfo(out, "Setting secondary_enter_key_code to {}", secondaryEnterKeyCode);
        } catch (NumberFormatException e) {
          reportError(out, "Unable to parse secondary_enter_key_code as integer: {}", value);
        }
        break;

      case "report_job_success_threshold_millis":
        try {
          reportJobSuccessThresholdMillis = Integer.valueOf(value);
          reportInfo(
              out,
              "Setting report_job_success_threshold_millis to {}",
              reportJobSuccessThresholdMillis);
        } catch (NumberFormatException e) {
          reportError(
              out, "Unable to parse report_job_success_threshold_millis as integer: {}", value);
        }
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

  /**
   * Reports an error message through the status consumer.
   *
   * @param out The consumer to report the status to.
   * @param messagePattern The message format string.
   * @param arguments The arguments for the message format string.
   */
  public void reportError(Consumer<Status> out, String messagePattern, Object... arguments) {
    out.accept(new Status(false, ParameterizedMessage.format(messagePattern, arguments)));
  }
}
