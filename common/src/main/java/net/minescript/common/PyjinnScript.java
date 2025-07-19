// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pyjinn.interpreter.Script;
import org.pyjinn.parser.PyjinnParser;

public class PyjinnScript {
  private static final Logger LOGGER = LogManager.getLogger();

  static {
    Script.setDebugLogger((str, args) -> LOGGER.info(str, args));
  }

  private PyjinnScript() {}

  public interface NameMappings {
    String getRuntimeClassName(String prettyClassName);

    String getRuntimeFieldName(Class<?> clazz, String prettyFieldName);

    Set<String> getRuntimeMethodNames(Class<?> clazz, String prettyMethodName);
  }

  public static class NoNameMappings implements NameMappings {
    @Override
    public String getRuntimeClassName(String prettyClassName) {
      return prettyClassName;
    }

    @Override
    public String getRuntimeFieldName(Class<?> clazz, String prettyFieldName) {
      return prettyFieldName;
    }

    @Override
    public Set<String> getRuntimeMethodNames(Class<?> clazz, String prettyMethodName) {
      return Set.of(prettyMethodName);
    }
  }

  public static class ObfuscatedNameMappings implements NameMappings {
    // Map: officialClassName -> obfuscatedClassName
    private final BiMap<String, String> officialToObfuscatedClassMap = HashBiMap.create();

    // Map: [obfuscatedClassName, officialFieldName] -> obfuscateFieldName
    private final Map<ClassMemberKey, String> officialFieldMap = new HashMap<>();

    // Map: [obfuscatedClassName, officialMethodName] -> Set(obfuscatedMethodNames)]
    private final HashMultimap<ClassMemberKey, String> officialMethodMap = HashMultimap.create();

    // Map: obfuscatedClassName -> fabricClassName
    private final BiMap<String, String> obfuscatedToFabricClassMap = HashBiMap.create();

    // Map: fabricClassName -> obfuscatedClassName
    private final BiMap<String, String> fabricToObfuscatedClassMap =
        obfuscatedToFabricClassMap.inverse();

    // Map: obfuscatedClassName -> officialClassName
    private final BiMap<String, String> obfuscatedToOfficialClassMap =
        officialToObfuscatedClassMap.inverse();

    // Map: [obfuscatedClassName, obfuscatedFieldName] -> fabricFieldName
    private final Map<ClassMemberKey, String> fabricFieldMap = new HashMap<>();

    // Map: [obfuscatedClassName, obfuscatedMethodName] -> Set(fabricMethodName)]
    private final HashMultimap<ClassMemberKey, String> fabricMethodMap = HashMultimap.create();

    // Map: officialClassName -> fabricClassName
    private final Map<String, String> runtimeClassMap = new HashMap<>();

    // Map: [fabricClassName, officialFieldName] -> fabricFieldName
    private final Map<ClassMemberKey, String> runtimeFieldMap = new HashMap<>();

    // Map: [fabricClassName, officialMethodName] -> Set(fabricMethodNames)]
    private final HashMultimap<ClassMemberKey, String> runtimeMethodMap = HashMultimap.create();

    private final String scriptFilename;
    private final Optional<Long> dumpActiveMappingsTimestamp;

    private record ClassMemberKey(String runtimeClassName, String prettyMemberName) {}

    private ObfuscatedNameMappings(
        String modLoaderName, String mcVersion, ScriptNameMappings.ScriptMetadata scriptMetadata) {
      this.dumpActiveMappingsTimestamp =
          Optional.ofNullable(
              scriptMetadata.dumpActiveMappings() ? System.currentTimeMillis() : null);
      this.scriptFilename = scriptMetadata.scriptName();

      dumpActiveMappingsTimestamp.ifPresent(
          timestamp ->
              LOGGER.info(
                  "Dumping script mapping for {}:\n# <mappings {} {}>  # timestamp={}",
                  scriptFilename,
                  modLoaderName,
                  mcVersion,
                  timestamp));
    }

    @Override
    public String getRuntimeClassName(String prettyClassName) {
      return runtimeClassMap.computeIfAbsent(
          prettyClassName,
          pcn -> {
            String rcn = computeRuntimeClassName(pcn);
            dumpActiveMappingsTimestamp.ifPresent(
                timestamp ->
                    LOGGER.info(
                        "Dumping script mapping for {}:\n#   class {} {}  # timestamp={}",
                        scriptFilename,
                        pcn,
                        rcn,
                        timestamp));
            return rcn;
          });
    }

    private String computeRuntimeClassName(String prettyClassName) {
      String obfuscatedName = officialToObfuscatedClassMap.get(prettyClassName);
      if (obfuscatedName == null) {
        return prettyClassName;
      }
      String fabricName = obfuscatedToFabricClassMap.get(obfuscatedName);
      if (fabricName == null) {
        return prettyClassName;
      }
      LOGGER.info(
          "Mapped official class name `{}` to Fabric name `{}`", prettyClassName, fabricName);
      return fabricName;
    }

    @Override
    public String getRuntimeFieldName(Class<?> clazz, String prettyFieldName) {
      var cacheKey = new ClassMemberKey(clazz.getName(), prettyFieldName);
      if (runtimeFieldMap.containsKey(cacheKey)) {
        return runtimeFieldMap.get(cacheKey);
      }

      Optional<String> optFabricFieldName = getFabricFieldNameUncached(clazz, prettyFieldName, "");
      final String fabricFieldName;

      if (optFabricFieldName.isEmpty()) {
        fabricFieldName = prettyFieldName;
        LOGGER.warn(
            "Failed to map Fabric.official field name `{}/{}.{}`, falling back to `{}`",
            obfuscatedToOfficialClassMap.getOrDefault(
                fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
            clazz.getName(),
            prettyFieldName,
            fabricFieldName);
      } else {
        fabricFieldName = optFabricFieldName.get();
        LOGGER.info(
            "Mapped Fabric.official field name `{}/{}.{}` to `{}`",
            obfuscatedToOfficialClassMap.getOrDefault(
                fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
            clazz.getName(),
            prettyFieldName,
            fabricFieldName);
      }

      runtimeFieldMap.put(cacheKey, fabricFieldName);

      dumpActiveMappingsTimestamp.ifPresent(
          timestamp ->
              LOGGER.info(
                  "Dumping script mapping for {}:\n#   field {} {} {}  # timestamp={}",
                  scriptFilename,
                  cacheKey.runtimeClassName(),
                  cacheKey.prettyMemberName(),
                  fabricFieldName,
                  timestamp));

      return fabricFieldName;
    }

    @Override
    public Set<String> getRuntimeMethodNames(Class<?> clazz, String prettyMethodName) {
      var cacheKey = new ClassMemberKey(clazz.getName(), prettyMethodName);
      if (runtimeMethodMap.containsKey(cacheKey)) {
        return runtimeMethodMap.get(cacheKey);
      }

      Set<String> fabricMethodNames = getFabricMethodNamesUncached(clazz, prettyMethodName, "");

      if (fabricMethodNames.isEmpty()) {
        LOGGER.warn(
            "Failed to map Fabric.official method name `{}/{}.{}`, falling back to {}",
            obfuscatedToOfficialClassMap.getOrDefault(
                fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
            clazz.getName(),
            prettyMethodName,
            fabricMethodNames);
        fabricMethodNames = Set.of(prettyMethodName);
      } else {
        LOGGER.info(
            "Mapped Fabric.official method name `{}/{}.{}` to {}",
            obfuscatedToOfficialClassMap.getOrDefault(
                fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
            clazz.getName(),
            prettyMethodName,
            fabricMethodNames);
      }

      runtimeMethodMap.putAll(cacheKey, fabricMethodNames);

      final var names = fabricMethodNames; // capture for lambda
      dumpActiveMappingsTimestamp.ifPresent(
          timestamp -> {
            for (String name : names) {
              LOGGER.info(
                  "Dumping script mapping for {}:\n#   method {} {} {}  # timestamp={}",
                  scriptFilename,
                  cacheKey.runtimeClassName(),
                  cacheKey.prettyMemberName(),
                  name,
                  timestamp);
            }
          });

      return fabricMethodNames;
    }

    private Optional<String> getFabricFieldNameUncached(
        Class<?> clazz, String officialFieldName, String indent) {
      String obfuscatedClassName = fabricToObfuscatedClassMap.get(clazz.getName());
      if (obfuscatedClassName == null) {
        return Optional.empty();
      }

      var officialFieldKey = new ClassMemberKey(obfuscatedClassName, officialFieldName);
      String obfuscatedFieldName = officialFieldMap.get(officialFieldKey);

      var fabricFieldKey = new ClassMemberKey(obfuscatedClassName, obfuscatedFieldName);
      String fabricFieldName = fabricFieldMap.get(fabricFieldKey);

      if (fabricFieldName == null) {
        LOGGER.info(
            "{}Could not find mapping in {}/{} for field {}; trying superclass and interfaces...",
            indent,
            obfuscatedToOfficialClassMap.getOrDefault(
                fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
            clazz.getName(),
            officialFieldName);

        var superclass = clazz.getSuperclass();
        if (superclass != null) {
          LOGGER.info(
              "{}Looking for field {} in superclass {}/{}",
              indent,
              officialFieldName,
              obfuscatedToOfficialClassMap.getOrDefault(
                  fabricToObfuscatedClassMap.get(superclass.getName()), "?"),
              superclass.getName());

          var name = getFabricFieldNameUncached(superclass, officialFieldName, indent + "  ");
          if (!name.isEmpty()) {
            return name;
          }
        }

        var interfaces = clazz.getInterfaces();
        LOGGER.info(
            "{}Checking {}/{} for {} interfaces...",
            indent,
            obfuscatedToOfficialClassMap.getOrDefault(
                fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
            clazz.getName(),
            interfaces.length);

        for (var iface : interfaces) {
          LOGGER.info(
              "{}Looking for field {} in interface {}/{}",
              indent,
              officialFieldName,
              obfuscatedToOfficialClassMap.getOrDefault(
                  fabricToObfuscatedClassMap.get(iface.getName()), "?"),
              iface.getName());
          var name = getFabricFieldNameUncached(iface, officialFieldName, indent + "  ");
          if (!name.isEmpty()) {
            return name;
          }
        }

        return Optional.empty();
      }

      LOGGER.info(
          "{}Found mappings in {}/{} for field {}: {}",
          indent,
          obfuscatedToOfficialClassMap.getOrDefault(
              fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
          clazz.getName(),
          officialFieldName,
          fabricFieldName);
      return Optional.of(fabricFieldName);
    }

    private Set<String> getFabricMethodNamesUncached(
        Class<?> clazz, String officialMemberName, String indent) {
      String obfuscatedClassName = fabricToObfuscatedClassMap.get(clazz.getName());
      if (obfuscatedClassName == null) {
        return Set.of();
      }

      var officialMemberKey = new ClassMemberKey(obfuscatedClassName, officialMemberName);
      Set<String> obfuscatedMemberNames = officialMethodMap.get(officialMemberKey);

      var fabricMemberNames = new HashSet<String>();
      for (var obfuscatedMemberName : obfuscatedMemberNames) {
        var fabricMemberKey = new ClassMemberKey(obfuscatedClassName, obfuscatedMemberName);
        fabricMemberNames.addAll(fabricMethodMap.get(fabricMemberKey));
      }

      if (fabricMemberNames.isEmpty()) {
        LOGGER.info(
            "{}Could not find mapping in {}/{} for member {}; trying superclass and interfaces...",
            indent,
            obfuscatedToOfficialClassMap.getOrDefault(
                fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
            clazz.getName(),
            officialMemberName);

        var superclass = clazz.getSuperclass();
        if (superclass != null) {
          LOGGER.info(
              "{}Looking for member {} in superclass {}/{}",
              indent,
              officialMemberName,
              obfuscatedToOfficialClassMap.getOrDefault(
                  fabricToObfuscatedClassMap.get(superclass.getName()), "?"),
              superclass.getName());

          var names = getFabricMethodNamesUncached(superclass, officialMemberName, indent + "  ");
          if (!names.isEmpty()) {
            return names;
          }
        }

        var interfaces = clazz.getInterfaces();
        LOGGER.info(
            "{}Checking {}/{} for {} interfaces...",
            indent,
            obfuscatedToOfficialClassMap.getOrDefault(
                fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
            clazz.getName(),
            interfaces.length);

        for (var iface : interfaces) {
          LOGGER.info(
              "{}Looking for member {} in interface {}/{}",
              indent,
              officialMemberName,
              obfuscatedToOfficialClassMap.getOrDefault(
                  fabricToObfuscatedClassMap.get(iface.getName()), "?"),
              iface.getName());
          var names = getFabricMethodNamesUncached(iface, officialMemberName, indent + "  ");
          if (!names.isEmpty()) {
            return names;
          }
        }

        return Set.of();
      }

      LOGGER.info(
          "{}Found mappings in {}/{} for member {}: {}",
          indent,
          obfuscatedToOfficialClassMap.getOrDefault(
              fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
          clazz.getName(),
          officialMemberName,
          fabricMemberNames);
      return fabricMemberNames;
    }

    public static Optional<ObfuscatedNameMappings> loadFromFiles(
        String modLoaderName, String mcVersion, ScriptNameMappings.ScriptMetadata scriptMetadata)
        throws IOException {
      Path mappingsVersionPath = Paths.get("minescript", "mappings", mcVersion);
      Files.createDirectories(mappingsVersionPath);

      Path officialMappingsPath = mappingsVersionPath.resolve("client.txt");
      if (!Files.exists(officialMappingsPath)) {
        LOGGER.warn("Unable to find official mappings at {}", officialMappingsPath);
        return Optional.empty();
      }

      var mappings = new ObfuscatedNameMappings(modLoaderName, mcVersion, scriptMetadata);
      mappings.loadOfficalMappings(officialMappingsPath);

      Path fabricMappingsPath = mappingsVersionPath.resolve(mcVersion + ".tiny");
      if (modLoaderName.equals("Fabric")) {
        if (!Files.exists(fabricMappingsPath)) {
          LOGGER.warn("Unable to find Fabric mappings at {}", fabricMappingsPath);
          return Optional.empty();
        }
        mappings.loadFabricMappings(fabricMappingsPath);
      }

      return Optional.of(mappings);
    }

    private static final Pattern OFFICIAL_MAPPINGS_CLASS_RE =
        Pattern.compile("^([^ ]+) -> ([a-z0-9A-Z_]+):$");

    private static final Pattern OFFICIAL_MAPPINGS_MEMBER_RE =
        Pattern.compile(" ([a-z0-9A-Z_]+)(\\([^\\)]*\\))? -> ([a-z0-9A-Z_]+)$");

    private void loadOfficalMappings(Path officialMappingsPath) throws IOException {
      long startTimeMillis = System.currentTimeMillis();
      try (BufferedReader reader =
          new BufferedReader(new FileReader(officialMappingsPath.toFile()))) {
        String line;
        String obfuscatedClassName = null;
        while ((line = reader.readLine()) != null) {
          // E.g. "net.minecraft.client.Minecraft -> fud:"
          var match = OFFICIAL_MAPPINGS_CLASS_RE.matcher(line);
          if (match.find()) {
            String officialClassName = match.group(1);
            obfuscatedClassName = match.group(2);
            officialToObfuscatedClassMap.put(officialClassName, obfuscatedClassName);
          }

          // E.g. "2502:2502:net.minecraft.client.Minecraft getInstance() -> R"
          match = OFFICIAL_MAPPINGS_MEMBER_RE.matcher(line);
          if (obfuscatedClassName != null && match.find()) {
            String officialMemberName = match.group(1);
            String methodParams = match.group(2);
            String obfuscatedMemberName = match.group(3);
            if (methodParams == null || methodParams.isEmpty()) {
              officialFieldMap.put(
                  new ClassMemberKey(obfuscatedClassName, officialMemberName),
                  obfuscatedMemberName);
            } else {
              officialMethodMap.put(
                  new ClassMemberKey(obfuscatedClassName, officialMemberName),
                  obfuscatedMemberName);
            }
          }
        }
      }
      long endTimeMillis = System.currentTimeMillis();
      LOGGER.info(
          "Loaded {} classes, {} fields, and {} methods from official mappings file in {}ms",
          officialToObfuscatedClassMap.size(),
          officialFieldMap.size(),
          officialMethodMap.size(),
          endTimeMillis - startTimeMillis);
    }

    private static final Pattern FABRIC_MAPPINGS_CLASS_RE =
        Pattern.compile("^CLASS\t([a-z0-9A-Z_.]+)\t([a-z0-9A-Z_/]+)$");

    private static final Pattern FABRIC_MAPPINGS_MEMBER_RE =
        Pattern.compile("^(FIELD|METHOD)\t([a-z0-9A-Z_.]+)\t[^\t]+\t([^\t]+)\t([^\t]+)$");

    private void loadFabricMappings(Path fabricMappingsPath) throws IOException {
      long startTimeMillis = System.currentTimeMillis();
      try (BufferedReader reader =
          new BufferedReader(new FileReader(fabricMappingsPath.toFile()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          // E.g. "CLASS\tfud\tnet/minecraft/class_310"
          var match = FABRIC_MAPPINGS_CLASS_RE.matcher(line);
          if (match.find()) {
            String obfuscatedClassName = match.group(1);
            String officialClassName = match.group(2).replace('/', '.');
            obfuscatedToFabricClassMap.put(obfuscatedClassName, officialClassName);
          }

          // E.g. "METHOD\tfud\t()Lfud;\tR\tmethod_1551"
          match = FABRIC_MAPPINGS_MEMBER_RE.matcher(line);
          if (match.find()) {
            String memberType = match.group(1);
            String obfuscatedClassName = match.group(2);
            String obfuscatedMemberName = match.group(3);
            String fabricMemberName = match.group(4);
            if (memberType.equals("FIELD")) {
              fabricFieldMap.put(
                  new ClassMemberKey(obfuscatedClassName, obfuscatedMemberName), fabricMemberName);
            } else {
              fabricMethodMap.put(
                  new ClassMemberKey(obfuscatedClassName, obfuscatedMemberName), fabricMemberName);
            }
          }
        }
      }
      long endTimeMillis = System.currentTimeMillis();
      LOGGER.info(
          "Loaded {} classes, {} fields, and {} methods from Fabric mappings file in {}ms",
          obfuscatedToFabricClassMap.size(),
          fabricFieldMap.size(),
          fabricMethodMap.size(),
          endTimeMillis - startTimeMillis);
    }
  }

  public static class ScriptNameMappings implements NameMappings {
    // Map: prettyClassName -> runtimeClassName
    private final Map<String, String> classMappings;

    // Map: [runtimeClassName, prettyFieldName] -> runtimeFieldName
    private final Map<ClassMemberKey, String> fieldMappings;

    // Map: [runtimeClassName, prettyMemberName] -> Set(runtimeMemberNames)]
    private final HashMultimap<ClassMemberKey, String> methodMappings;

    private record ClassMemberKey(String runtimeClassName, String prettyMemberName) {}

    private ScriptNameMappings(
        Map<String, String> classMappings,
        Map<ClassMemberKey, String> fieldMappings,
        HashMultimap<ClassMemberKey, String> methodMappings) {
      this.classMappings = classMappings;
      this.fieldMappings = fieldMappings;
      this.methodMappings = methodMappings;
    }

    @Override
    public String getRuntimeClassName(String prettyClassName) {
      return classMappings.getOrDefault(prettyClassName, prettyClassName);
    }

    @Override
    public String getRuntimeFieldName(Class<?> clazz, String prettyFieldName) {
      String runtimeClassName = clazz.getName();
      var fieldKey = new ClassMemberKey(runtimeClassName, prettyFieldName);
      return fieldMappings.getOrDefault(fieldKey, prettyFieldName);
    }

    @Override
    public Set<String> getRuntimeMethodNames(Class<?> clazz, String prettyMethodName) {
      String runtimeClassName = clazz.getName();
      var methodKey = new ClassMemberKey(runtimeClassName, prettyMethodName);
      Set<String> runtimeMethodNames = methodMappings.get(methodKey);
      if (!runtimeMethodNames.isEmpty()) {
        return runtimeMethodNames;
      }

      // No mapping found for the method, so fall back to the pretty name.
      return Set.of(prettyMethodName);
    }

    public record ScriptMetadata(
        String scriptName, Optional<ScriptNameMappings> mappings, boolean dumpActiveMappings) {

      public ScriptMetadata(String scriptName) {
        this(scriptName, Optional.empty(), false);
      }
    }

    /**
     * Returns inline mappings loaded from a script based on the mod loader and MC version.
     *
     * <p>Loads mappings from script source looking for a comment section that starts with a line
     * like "# &lt;mappings Fabric 1.21.6&gt;" and parses lines until "# &lt;/mappings&gt;".
     *
     * <p>The file format is:
     *
     * <ul>
     *   <li>blank lines are ignored
     *   <li>class mapping lines formatted as "class package.PrettyClassName pkg.RuntimeClassName"
     *   <li>field mapping lines associated with the most recent earlier class line and are
     *       <p>formatted as "field pkg.RuntimeClassName prettyName runtimeName"
     *   <li>method mapping lines are associated with the most recent earlier class line and are
     *       <p>formatted as "method pkg.RuntimeClassName prettyName runtimeName"
     * </ul>
     */
    public static ScriptMetadata loadScriptMetadata(
        String sourceFilename, String scriptCode, String modLoaderName, String mcVersion)
        throws IOException {
      Map<String, String> classMappings = new HashMap<>();
      Map<ClassMemberKey, String> fieldMappings = new HashMap<>();
      HashMultimap<ClassMemberKey, String> methodMappings = HashMultimap.create();
      boolean dumpActiveMappings = false;

      final String mappingsStartLine = "# <mappings %s %s>".formatted(modLoaderName, mcVersion);
      final String mappingsEndLine = "# </mappings>";

      boolean foundMappings = false;
      int lineNumber = 0;
      Iterable<String> lines = scriptCode.lines()::iterator;
      for (String line : lines) {
        lineNumber++;

        if (line.trim().equals("# @dump_active_mappings")) {
          dumpActiveMappings = true;
          continue;
        }

        if (!foundMappings) {
          if (line.startsWith(mappingsStartLine)) {
            foundMappings = true;
            LOGGER.info("Found matching `{}` in {}", line.trim().substring(2), sourceFilename);
          }
          continue;
        }

        if (line.startsWith(mappingsEndLine)) {
          break;
        }

        line = line.startsWith("# ") ? line.substring(2) : line; // Remove leading "# "
        line = line.split("#")[0].trim(); // Remove comments.
        if (line.isEmpty()) {
          continue;
        }

        String[] parts = line.split("\\s+");
        if (parts[0].equals("class")) {
          // Parse class mapping: "class package.PrettyClassName pkg.RuntimeClassName"
          if (parts.length != 3) {
            LOGGER.warn(
                "Malformed line at {}:{}: expected 3 words on `class` line but got:\n{}",
                sourceFilename,
                lineNumber,
                line);
            continue;
          }
          String prettyClassName = parts[1];
          String runtimeClassName = parts[2];
          classMappings.put(prettyClassName, runtimeClassName);
        } else if (parts[0].equals("field")) {
          // Field mapping: "field pkg.RuntimeClassName prettyFieldName runtimeFieldName"
          if (parts.length != 4) {
            LOGGER.warn(
                "Malformed line at {}:{}: expected 4 words on line but got:\n{}",
                sourceFilename,
                lineNumber,
                line);
            continue;
          }
          String runtimeClassName = parts[1];
          String prettyMemberName = parts[2];
          String runtimeMemberName = parts[3];
          String oldFieldName =
              fieldMappings.put(
                  new ClassMemberKey(runtimeClassName, prettyMemberName), runtimeMemberName);
          if (oldFieldName != null) {
            LOGGER.warn(
                "{}:{}: Field `{}` of class `{}` previously mapped to `{}` now mapped to `{}`:\n"
                    + "{}",
                sourceFilename,
                lineNumber,
                prettyMemberName,
                runtimeClassName,
                oldFieldName,
                runtimeMemberName,
                line);
          }
        } else if (parts[0].equals("method")) {
          // Method mapping: "method pkg.RuntimeClassName prettyMethodName runtimeMethodName"
          if (parts.length != 4) {
            LOGGER.warn(
                "Malformed line at {}:{}: expected 4 words on `method` line but got:\n{}",
                sourceFilename,
                lineNumber,
                line);
            continue;
          }
          String runtimeClassName = parts[1];
          String prettyMemberName = parts[2];
          String runtimeMemberName = parts[3];
          methodMappings.put(
              new ClassMemberKey(runtimeClassName, prettyMemberName), runtimeMemberName);
        } else {
          LOGGER.warn(
              "Unexpected format at {}:{}: unrecognized mapping metadata:\n{}",
              sourceFilename,
              lineNumber,
              line);
        }
      }

      if (!foundMappings) {
        return new ScriptMetadata(sourceFilename, Optional.empty(), dumpActiveMappings);
      }

      return new ScriptMetadata(
          sourceFilename,
          Optional.of(new ScriptNameMappings(classMappings, fieldMappings, methodMappings)),
          dumpActiveMappings);
    }
  }

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
    public boolean sendResponse(long functionCallId, JsonElement returnValue, boolean finalReply) {
      var callback = callbackMap.get(functionCallId);
      if (callback == null) {
        LOGGER.error("No callback found in Pyjinn task for function call {}", functionCallId);
        return false;
      }
      try {
        callback.function.call(callback.env, returnValue);
      } catch (Exception e) {
        systemMessageQueue.logException(e);
      }
      return true;
    }

    /** Sends an exception to the given script function call. Returns true if response succeeds. */
    @Override
    public boolean sendException(long functionCallId, ExceptionInfo exception) {
      return false; // TODO(maxuser): implement...
    }
  }

  private static class PyjinnJob extends Job {
    private static final long ASYNC_FCALL_START_ID = 1000L;

    private final Script script;
    private final PyjinnTask task;
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
        Runnable doneCallback) {
      super(
          jobId,
          command,
          task,
          config,
          systemMessageQueue,
          Minescript::processMessage,
          doneCallback);
      this.script = script;
      this.task = task;
    }

    @Override
    protected void start() {
      setState(JobState.RUNNING);

      try {
        script.vars.__setitem__("job", this);
        script.redirectStdout(this::processStdout);
        script.redirectStderr(this::processStderr);
        script.atExit(this::atExit);
        isRunningScriptGlobals = true;
        script.exec();
        isRunningScriptGlobals = false;
        hasPendingCallbacksAfterExec = !task.callbackMap.isEmpty();
      } catch (Exception e) {
        isRunningScriptGlobals = false;
        systemMessageQueue.logException(e);
        script.exit(1);
        return;
      }

      if (!hasPendingCallbacksAfterExec) {
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
      // Note that atExit() is called directly (not through script.exit()) when the sciript job is
      // killed by the user.
      super.requestKill();
      atExit(128);
    }

    @Override
    protected void onClose() {
      // Nothing special to do when closing the Pyjinn job.
    }
  }

  public static Job createJob(
      int jobId,
      ScriptConfig.BoundCommand boundCommand,
      Config config,
      SystemMessageQueue systemMessageQueue,
      String modLoaderName,
      Runnable doneCallback)
      throws Exception {
    var execCommand = config.scriptConfig().getExecutableCommand(boundCommand);

    var script =
        loadScript(
            execCommand.command(),
            Files.readString(Paths.get(execCommand.command()[0])),
            modLoaderName);

    var job =
        new PyjinnJob(
            jobId,
            boundCommand,
            new PyjinnTask(systemMessageQueue),
            script,
            config,
            systemMessageQueue,
            doneCallback);

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
          LOGGER.info("Resovled import of {} to {}", name, path);
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

  public static Script loadScript(String[] scriptCommand, String scriptCode, String modLoaderName)
      throws Exception {
    String scriptFilename = scriptCommand[0];
    boolean isMinecraftClassObfuscated =
        !Minecraft.class.getName().equals("net.minecraft.client.Minecraft");

    final NameMappings nameMappings;
    // Check for obfuscated names, and if needed, load a mappings file.
    if (isMinecraftClassObfuscated) {
      String mcVersion = SharedConstants.getCurrentVersion().name();
      var scriptMetadata =
          ScriptNameMappings.loadScriptMetadata(
              scriptFilename, scriptCode, modLoaderName, mcVersion);
      if (scriptMetadata.mappings().isPresent()) {
        nameMappings = scriptMetadata.mappings().get();
      } else {
        var obfuscatedMappings =
            ObfuscatedNameMappings.loadFromFiles(modLoaderName, mcVersion, scriptMetadata);
        if (obfuscatedMappings.isPresent()) {
          nameMappings = obfuscatedMappings.get();
        } else {
          throw new IllegalArgumentException(
              "Runtime is using obfuscated names for Java code and no mappings found for script: "
                  + scriptCommand[0]
                  + "\nFor more information see: minescript.net/mappings");
        }
      }
    } else {
      nameMappings = new NoNameMappings();
    }

    var moduleHandler = new MinescriptModuleHandler();

    var script =
        new Script(
            PyjinnScript.class.getClassLoader(),
            moduleHandler,
            nameMappings::getRuntimeClassName,
            nameMappings::getRuntimeFieldName,
            nameMappings::getRuntimeMethodNames);

    script.vars.__setitem__("sys_version", Script.versionInfo().toString());
    script.vars.__setitem__("sys_argv", scriptCommand);

    JsonElement scriptAst = PyjinnParser.parse(scriptCode);
    script.parse(scriptAst, scriptFilename);
    return script;
  }

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
          "chunk");

  public static class AddEventListener implements Script.Function {
    @Override
    public Object call(Script.Environment env, Object... params) {
      expectMinParams(params, 2);
      if (!(params[0] instanceof String)) {
        throw new IllegalArgumentException(
            "Expected first param to add_event_listener to be string (event type) but got "
                + params[0].toString());
      }

      String eventName = (String) params[0];
      if (!EVENT_NAMES.contains(eventName)) {
        throw new IllegalArgumentException(
            "Unsupported event type: %s. Must be one of: %s".formatted(eventName, EVENT_NAMES));
      }

      // TODO(maxuser): Instead of special-casing the chat_intercept registration, process the
      // keyword args in Pyjinn library code (e.g. system/pyj/minescript.py) so that the resulting
      // flattened arg list can be passed blindly to Minescript::call.
      final List<Object> args;
      if (eventName.equals("outgoing_chat_intercept")) {
        expectMaxParams(params, 3);
        if (params[2] instanceof Script.KeywordArgs kwargs) {
          // Can't use List.of(...) because at least one of the args must be null.
          args = new ArrayList<>();
          args.add(kwargs.get("prefix"));
          args.add(kwargs.get("pattern"));
        } else {
          throw new IllegalArgumentException(
              "Expected third param to add_event_listener() to be keyword args but got: "
                  + params[2]);
        }
      } else {
        expectMaxParams(params, 2);
        args = List.of();
      }

      try {
        var script = (Script) env.getVariable("__script__");
        var job = (PyjinnJob) script.vars.__getitem__("job");
        long listenerId = job.nextFcallId++;

        // TODO(maxuser): This is a hack, kicking off the start_foo_listener call immediately after
        // the corresponding register_foo_listener. Refactor and simplfy how async ops work from
        // Pyjinn scripts. "register..." and "start..." calls got split in the first place to
        // accomodate the semantics of externally executed (Python) scripts, because the
        // "register..." call was to return a handle to the calling script to refer to the listener
        // while the "start..." has no return value (Minescript::runExternalScriptFunction returns
        // Optional.empty()) to indicate that it's an async operation that will return value(s) at a
        // later time.

        if (params[1] instanceof Script.Function callback) {
          job.task.callbackMap.put(listenerId, new PyjinnTask.Callback(env, callback));

          Minescript.call(job, listenerId, "register_%s_listener".formatted(eventName), args);

          var functionCall =
              new ScriptFunctionCall("start_%s_listener".formatted(eventName), List.of(listenerId));
          if (!Minescript.startEventListener(job, listenerId, functionCall)) {
            throw new IllegalArgumentException(
                "Unable to start event listener using %s".formatted(functionCall.name()));
          }
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
            if (job.task.callbackMap.isEmpty() && !job.isRunningScriptGlobals) {
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
