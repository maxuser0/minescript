// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pyjinn.interpreter.Script;
import org.pyjinn.parser.PyjinnParser;

public class PyjinnScript {
  private static final Logger LOGGER = LogManager.getLogger();

  private final Script script;
  private final Runnable onFinish;

  private PyjinnScript(Script script, Runnable onFinish) {
    this.script = script;
    this.onFinish = onFinish;

    // TODO(maxuser): Define add_event_listener in a module that needs to be imported explicitly?
    script.globals().setVariable("add_event_listener", new AddEventListener());
    initBuiltinFunctions(script);
  }

  private static void initBuiltinFunctions(Script script) {
    if (builtinFunctions == null) {
      var mapBuilder = new ImmutableMap.Builder<String, BuiltinScriptFunction>();
      for (String name : BUILTIN_FUNCTIONS) {
        mapBuilder.put(name, new BuiltinScriptFunction(name));
      }
      builtinFunctions = mapBuilder.build();
    }
    var globals = script.globals();
    for (var entry : builtinFunctions.entrySet()) {
      globals.setVariable(entry.getKey(), entry.getValue());
    }
  }

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
     * <p>Loads mappings from a script file looking for a comment section that starts with a line
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
        String sourceFilename, String modLoaderName, String mcVersion) throws IOException {
      Path scriptPath = Paths.get(sourceFilename);

      if (!Files.exists(scriptPath)) {
        return new ScriptMetadata(sourceFilename);
      }

      Map<String, String> classMappings = new HashMap<>();
      Map<ClassMemberKey, String> fieldMappings = new HashMap<>();
      HashMultimap<ClassMemberKey, String> methodMappings = HashMultimap.create();
      boolean dumpActiveMappings = false;

      final String mappingsStartLine = "# <mappings %s %s>".formatted(modLoaderName, mcVersion);
      final String mappingsEndLine = "# </mappings>";

      boolean foundMappings = false;
      try (BufferedReader reader = new BufferedReader(new FileReader(scriptPath.toFile()))) {
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
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

  public static PyjinnScript create(
      ScriptConfig.ExecutableCommand execCommand,
      Consumer<String> stdoutConsumer,
      String modLoaderName,
      Runnable onFinish)
      throws Exception {
    try {
      String sourceFilename = execCommand.command()[0];
      boolean isMinecraftClassObfuscated =
          !Minecraft.class.getName().equals("net.minecraft.client.Minecraft");

      final NameMappings nameMappings;
      // Check for obfuscated names, and if needed, load a mappings file.
      if (isMinecraftClassObfuscated) {
        String mcVersion = SharedConstants.getCurrentVersion().name();
        var scriptMetadata =
            ScriptNameMappings.loadScriptMetadata(sourceFilename, modLoaderName, mcVersion);
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
                    + execCommand.command()[0]
                    + "\nFor more information see: minescript.net/mappings");
          }
        }
      } else {
        nameMappings = new NoNameMappings();
      }

      String sourceCode = Files.readString(Paths.get(sourceFilename));
      JsonElement ast = PyjinnParser.parse(sourceCode);
      var script =
          new Script(
              PyjinnScript.class.getClassLoader(),
              nameMappings::getRuntimeClassName,
              nameMappings::getRuntimeFieldName,
              nameMappings::getRuntimeMethodNames);

      // TODO(maxuser): Support stderr redirection, too.
      script.redirectStdout(stdoutConsumer);

      script.parse(ast, sourceFilename);
      return new PyjinnScript(script, onFinish);
    } catch (Exception e) {
      LOGGER.error("Error creating PyjinnScript", e);
      onFinish.run();
      throw e;
    }
  }

  public void start() {
    try {
      script.exec();
    } finally {
      // TODO(maxuser): Run onFinish only when there are no remaining event listeners, i.e. when all
      // event listeners added by the script have been cancelled or none were added.
      onFinish.run();
    }
  }

  public record Event(String type) {}

  public class AddEventListener implements Script.Function {
    @Override
    public Object call(Script.Environment env, Object... params) {
      expectNumParams(params, 2);
      String eventType = params[0].toString();
      var value = params[1];
      if (value instanceof Script.Function eventListener) {
        // TODO(maxuser): Call the event listener only when an event fires.
        LOGGER.info(
            "(maxuser-debug) Adding fake event listener... triggering it once for testing...");
        eventListener.call(env, new Event(eventType));
        LOGGER.info("(maxuser-debug) Done testing event listener");
      } else {
        throw new IllegalArgumentException("Expected addEventListener param to be callable");
      }
      return null; // TODO(maxuser): Return an listener registration object that can be cancelled.
    }
  }

  private static Map<String, BuiltinScriptFunction> builtinFunctions = null;

  private static final String[] BUILTIN_FUNCTIONS = {
    "player_position",
    "player_name",
    "getblock",
    "getblocklist",
    "register_key_listener",
    "register_mouse_listener",
    "register_chat_message_listener",
    "register_chat_message_interceptor",
    "register_add_entity_listener",
    "register_block_update_listener",
    "register_explosion_listener",
    "register_take_item_listener",
    "register_damage_listener",
    "register_chunk_listener",
    "unregister_event_handler",
    "set_nickname",
    "player_hand_items",
    "player_inventory",
    // Removed in Minescript 5.0: "player_inventory_slot_to_hotbar",
    "player_inventory_select_slot",
    "press_key_bind",
    "player_press_forward",
    "player_press_backward",
    "player_press_left",
    "player_press_right",
    "player_press_jump",
    "player_press_sprint",
    "player_press_sneak",
    "player_press_pick_item",
    "player_press_use",
    "player_press_attack",
    "player_press_swap_hands",
    "player_press_drop",
    "player_orientation",
    "player_set_orientation",
    "player_get_targeted_block",
    "player_get_targeted_entity",
    "player_health",
    "player",
    "players",
    "entities",
    "version_info",
    "world_info",
    "screenshot",
    "screen_name",
    "container_get_items",
    "player_look_at",
    "show_chat_screen",
    "job_info",
    "append_chat_history",
    "chat_input",
    "set_chat_input",
    "execute",
    "echo_json",
    "echo",
    "chat",
    "log"
  };

  public record BuiltinScriptFunction(String name) implements Script.Function {
    @Override
    public Object call(Script.Environment env, Object... params) {
      try {
        // TODO(maxuser): Pass real values for job and funcCallId to be gotten from env vars.
        JobControl nullJob = null;
        long nullFuncCallId = 0;
        return Minescript.call(nullJob, nullFuncCallId, name, List.of(params));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
