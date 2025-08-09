// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ScriptConfig {
  private static final Logger LOGGER = LogManager.getLogger();

  private final Path minescriptDir;
  private final ImmutableList<String> builtinCommands;
  private final ImmutableSet<String> ignoreDirs;

  private boolean escapeCommandDoubleQuotes = System.getProperty("os.name").startsWith("Windows");

  private ImmutableList<Path> commandPath =
      ImmutableList.of(Paths.get("system", "exec"), Paths.get(""));

  private String minescriptCommandPathEnvVar;

  private Map<String, FileTypeConfig> fileTypeMap = new ConcurrentHashMap<>();
  private List<String> fileExtensions = new ArrayList<>();

  public ScriptConfig(
      String minescriptDirName,
      ImmutableList<String> builtinCommands,
      ImmutableSet<String> ignoreDirs) {
    this.minescriptDir = Paths.get(System.getProperty("user.dir"), minescriptDirName);
    this.builtinCommands = builtinCommands;
    this.ignoreDirs = ignoreDirs;

    minescriptCommandPathEnvVar =
        "MINESCRIPT_COMMAND_PATH="
            + String.join(
                File.pathSeparator,
                commandPath.stream()
                    .map(p -> minescriptDir.resolve(p).toString())
                    .collect(Collectors.toList()));

    // Configure ".pyj" as the built-in file type for Pyjinn scripts.
    // TODO(maxuser): Prevent ".pyj" from being overwritten by user configuration.
    var commandPattern = ImmutableList.of("{command}", "{args}");
    var environmentVars = ImmutableList.<String>of();
    configureFileType(
        new CommandConfig(".pyj", commandPattern, environmentVars), /* createsSubprocess= */ false);
  }

  public void setCommandPath(List<Path> commandPath) {
    this.commandPath = ImmutableList.copyOf(commandPath);

    minescriptCommandPathEnvVar =
        "MINESCRIPT_COMMAND_PATH="
            + String.join(
                File.pathSeparator,
                this.commandPath.stream()
                    .map(p -> minescriptDir.resolve(p).toString())
                    .collect(Collectors.toList()));
  }

  public void setEscapeCommandDoubleQuotes(boolean enable) {
    this.escapeCommandDoubleQuotes = enable;
  }

  public boolean escapeCommandDoubleQuotes() {
    return escapeCommandDoubleQuotes;
  }

  public ImmutableList<Path> commandPath() {
    return commandPath;
  }

  // Add to `matches` all the files in `commandDir` that match `prefix`.
  private void addMatchingFilesInDir(
      String prefix, Path prefixPath, Path commandDir, List<String> matches) {
    Path resolvedCommandDir = minescriptDir.resolve(commandDir);
    Path resolvedDir = resolvedCommandDir;

    // Iterate all but the last component of the prefix path to walk the path within commandDir.
    for (int i = 0; i < prefixPath.getNameCount() - 1; ++i) {
      resolvedDir = resolvedDir.resolve(prefixPath.getName(i));
      if (resolvedDir == null || !Files.isDirectory(resolvedDir)) {
        return;
      }
    }

    if (resolvedDir == null) {
      return;
    }
    // When prefix ends with File.separator ("\" on Windows, "/" otherwise), the last name
    // component of the path is the component before the final separator. Treat the last name
    // component as empty instead.
    final String prefixFileName;
    if (prefix.length() > 1 && prefix.endsWith(File.separator)) {
      resolvedDir = resolvedDir.resolve(prefixPath.getFileName());
      if (resolvedDir == null || !Files.isDirectory(resolvedDir)) {
        return;
      }
      prefixFileName = "";
    } else {
      Path fileName = prefixPath.getFileName();
      if (fileName == null) {
        return;
      }
      prefixFileName = fileName.toString();
    }

    if (resolvedDir == null || !Files.isDirectory(resolvedDir)) {
      return;
    }
    try (var paths = Files.list(resolvedDir)) {
      paths.forEach(
          path -> {
            String fileName = path.getFileName().toString();
            if (Files.isDirectory(path)) {
              if (fileName.startsWith(prefixFileName)) {
                try {
                  String relativeName = resolvedCommandDir.relativize(path).toString();
                  if (!(commandDir.toString().isEmpty() && ignoreDirs.contains(relativeName))) {
                    matches.add(relativeName + File.separator);
                  }
                } catch (IllegalArgumentException e) {
                  LOGGER.info(
                      "Dir completion: resolvedCommandDir: {}  path: {}", resolvedCommandDir, path);
                  throw e;
                }
              }
            } else {
              if (fileExtensions.contains(getFileExtension(fileName))) {
                try {
                  String scriptName =
                      removeFileExtension(resolvedCommandDir.relativize(path).toString());
                  if (scriptName.startsWith(prefix) && !matches.contains(scriptName)) {
                    matches.add(scriptName);
                  }
                } catch (IllegalArgumentException e) {
                  LOGGER.info(
                      "File completion: resolvedCommandDir: {}  path: {}",
                      resolvedCommandDir,
                      path);
                  throw e;
                }
              }
            }
          });
    } catch (IOException e) {
      LOGGER.error("Error listing files inside dir `{}`: {}", resolvedDir, e);
    }
  }

  /**
   * Returns names of commands and directories accessible from command path that match the prefix.
   */
  public List<String> findCommandPrefixMatches(String prefix) {
    var matches = new ArrayList<String>();

    Path prefixPath = Paths.get(prefix);
    if (prefixPath.isAbsolute()) {
      return new ArrayList<>();
    }

    for (String builtin : builtinCommands) {
      if (builtin.startsWith(prefix)) {
        matches.add(builtin);
      }
    }

    for (Path commandDir : commandPath) {
      addMatchingFilesInDir(prefix, prefixPath, commandDir, matches);
    }

    return matches;
  }

  public record CommandConfig(String extension, List<String> command, List<String> environment) {}

  public void configureFileType(CommandConfig commandConfig, boolean createsSubprocess) {
    Preconditions.checkNotNull(commandConfig.extension);
    Preconditions.checkArgument(
        commandConfig.extension.startsWith("."),
        "File extension does not start with dot: \"%s\"",
        commandConfig.extension);

    var fileTypeConfig =
        new FileTypeConfig(
            CommandBuilder.create(
                commandConfig.command,
                // Escape double quotes only if this command creates a subprocess (which requires
                // the OS shell) and this system is configured to escape double quotes (by default,
                // only Windows).
                () -> createsSubprocess && escapeCommandDoubleQuotes),
            commandConfig.environment == null
                ? new String[0]
                : commandConfig.environment.stream()
                    .map(s -> s.replace("{minescript_dir}", minescriptDir.toString()))
                    .toArray(String[]::new));
    if (fileTypeMap.put(commandConfig.extension, fileTypeConfig) == null) {
      fileExtensions.add(commandConfig.extension);
    } else {
      LOGGER.warn(
          "Existing file extension configuration is being replaced: \"{}\"",
          commandConfig.extension);
    }

    LOGGER.info(
        "Configured file extension `{}` for commands: {}", commandConfig.extension, commandConfig);
  }

  public List<String> supportedFileExtensions() {
    return fileExtensions;
  }

  public Path resolveCommandPath(String command) {
    for (Path commandDir : commandPath) {
      for (String fileExtension : fileExtensions) {
        Path path = minescriptDir.resolve(commandDir).resolve(command + fileExtension);
        if (Files.exists(path)) {
          return path;
        }
      }
    }
    return null;
  }

  public ExecutableCommand getExecutableCommand(BoundCommand boundCommand) {
    var fileTypeConfig = fileTypeMap.get(boundCommand.fileExtension());
    if (fileTypeConfig == null) {
      return null;
    }

    var env = new ArrayList<String>();
    Collections.addAll(env, fileTypeConfig.environment());
    if (!minescriptCommandPathEnvVar.isEmpty()) {
      env.add(minescriptCommandPathEnvVar);
    }

    return new ExecutableCommand(
        fileTypeConfig.commandBuilder.buildExecutableCommand(boundCommand),
        env.toArray(String[]::new));
  }

  /**
   * Returns file extension including the ".", e.g. ".py" for "foo.py", or "" if no extension found.
   */
  private static String getFileExtension(String fileName) {
    int lastIndex = fileName.lastIndexOf('.');
    if (lastIndex == -1) {
      return ""; // No file extension found
    }
    return fileName.substring(lastIndex);
  }

  private static String removeFileExtension(String fileName) {
    int lastIndex = fileName.lastIndexOf('.');
    if (lastIndex == -1) {
      return fileName;
    }
    return fileName.substring(0, lastIndex);
  }

  public record BoundCommand(Path scriptPath, String[] command, ScriptRedirect.Pair redirects) {
    String fileExtension() {
      if (scriptPath == null) {
        return null;
      }
      return getFileExtension(scriptPath.getFileName().toString());
    }
  }

  public record ExecutableCommand(String[] command, String[] environment) {}

  public record FileTypeConfig(CommandBuilder commandBuilder, String[] environment) {}

  /**
   * Command builder for translating Minescript commands into executable commands.
   *
   * @param pattern command pattern containing "{command}" and "{args}" placeholders
   * @param commandIndex index of "{command}" in pattern
   * @param argsIndex index of "{args}" in pattern
   */
  public record CommandBuilder(
      String[] pattern,
      int commandIndex,
      int argsIndex,
      Supplier<Boolean> escapeCommandDoubleQuotesSupplier) {

    public static CommandBuilder create(
        List<String> commandPattern, Supplier<Boolean> escapeCommandDoubleQuotesSupplier) {
      int commandIndex = commandPattern.indexOf("{command}");
      Preconditions.checkArgument(
          commandIndex >= 0, "{command} not found in pattern: %s", commandPattern);

      int argsIndex = commandPattern.indexOf("{args}");
      Preconditions.checkArgument(
          argsIndex >= 0, "{args} not found in pattern: %s", commandPattern);

      return new CommandBuilder(
          commandPattern.toArray(String[]::new),
          commandIndex,
          argsIndex,
          escapeCommandDoubleQuotesSupplier);
    }

    public String[] buildExecutableCommand(BoundCommand boundCommand) {
      var executableCommand = new ArrayList<String>();
      boolean escapeCommandDoubleQuotes = escapeCommandDoubleQuotesSupplier.get();
      String[] command = boundCommand.command();
      for (int i = 0; i < pattern.length; ++i) {
        if (i == commandIndex) {
          executableCommand.add(boundCommand.scriptPath().toString());
        } else if (i == argsIndex) {
          for (int j = 1; j < command.length; ++j) {
            String arg = command[j];
            if (escapeCommandDoubleQuotes) {
              arg = arg.replace("\"", "\\\"");
            }
            executableCommand.add(arg);
          }
        } else {
          executableCommand.add(pattern[i]);
        }
      }
      return executableCommand.toArray(String[]::new);
    }
  }
}
