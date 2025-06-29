// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.gson.JsonElement;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
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

    // TODO(maxuser): Define addEventListener in a module that needs to be imported explicitly?
    script.globals().setVariable("addEventListener", new AddEventListener());
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

    private record ClassMemberKey(String runtimeClassName, String prettyMemberName) {}

    private ObfuscatedNameMappings() {}

    @Override
    public String getRuntimeClassName(String prettyClassName) {
      return runtimeClassMap.computeIfAbsent(
          prettyClassName,
          prettyName -> {
            String obfuscatedName = officialToObfuscatedClassMap.get(prettyName);
            if (obfuscatedName == null) {
              return prettyName;
            }
            String fabricName = obfuscatedToFabricClassMap.get(obfuscatedName);
            if (fabricName == null) {
              return prettyName;
            }
            LOGGER.info(
                "Mapped official class name `{}` to Fabric name `{}`", prettyName, fabricName);
            return fabricName;
          });
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

      return fabricFieldName;
    }

    @Override
    public Set<String> getRuntimeMethodNames(Class<?> clazz, String prettyMethodName) {
      var cacheKey = new ClassMemberKey(clazz.getName(), prettyMethodName);
      if (runtimeMethodMap.containsKey(cacheKey)) {
        return runtimeMethodMap.get(cacheKey);
      }

      Set<String> fabricMemberNames = getFabricMemberNamesUncached(clazz, prettyMethodName, "");

      if (fabricMemberNames.isEmpty()) {
        LOGGER.warn(
            "Failed to map Fabric.official member name `{}/{}.{}`, falling back to `{}`",
            obfuscatedToOfficialClassMap.getOrDefault(
                fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
            clazz.getName(),
            prettyMethodName,
            fabricMemberNames);
        fabricMemberNames = Set.of(prettyMethodName);
      } else {
        LOGGER.info(
            "Mapped Fabric.official member name `{}/{}.{}` to `{}`",
            obfuscatedToOfficialClassMap.getOrDefault(
                fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
            clazz.getName(),
            prettyMethodName,
            fabricMemberNames);
      }

      runtimeMethodMap.putAll(cacheKey, fabricMemberNames);

      return fabricMemberNames;
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

    private Set<String> getFabricMemberNamesUncached(
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

          var names = getFabricMemberNamesUncached(superclass, officialMemberName, indent + "  ");
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
          var names = getFabricMemberNamesUncached(iface, officialMemberName, indent + "  ");
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
        String modLoaderName, String mcVersion) throws IOException {
      Path mappingsVersionPath = Paths.get("minescript", "mappings", mcVersion);
      Files.createDirectories(mappingsVersionPath);

      Path officialMappingsPath = mappingsVersionPath.resolve("client.txt");
      if (!Files.exists(officialMappingsPath)) {
        LOGGER.warn("Unable to find official mappings at {}", officialMappingsPath);
        return Optional.empty();
      }

      var mappings = new ObfuscatedNameMappings();
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
     *   <li>class mapping lines are formatted as "class com.foo.PrettyName runtimeName"
     *   <li>field mapping lines are associated with the most recent earlier class line and are
     *       <p>formatted as "field prettyName runtimeName"
     *   <li>method mapping lines are associated with the most recent earlier class line and are
     *       <p>formatted as "method prettyName runtimeName"
     * </ul>
     */
    public static Optional<ScriptNameMappings> loadFromScript(
        String sourceFilename, String modLoaderName, String mcVersion) throws IOException {
      Path scriptPath = Paths.get(sourceFilename);

      if (!Files.exists(scriptPath)) {
        return Optional.empty();
      }

      Map<String, String> classMappings = new HashMap<>();
      Map<ClassMemberKey, String> fieldMappings = new HashMap<>();
      HashMultimap<ClassMemberKey, String> methodMappings = HashMultimap.create();

      final String mappingsStartLine = "# <mappings %s %s>".formatted(modLoaderName, mcVersion);
      final String mappingsEndLine = "# </mappings>";

      boolean foundMappings = false;
      String runtimeClassName = null;
      try (BufferedReader reader = new BufferedReader(new FileReader(scriptPath.toFile()))) {
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
          lineNumber++;

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
          line = line.split("#")[0].stripTrailing(); // Remove comments.
          if (line.trim().isEmpty()) {
            continue;
          }

          String[] parts = line.trim().split("\\s+");
          if (parts.length != 3) {
            LOGGER.warn(
                "Malformed line in {}:{}: expected 3 words on line but got:\n{}",
                sourceFilename,
                lineNumber,
                line);
            continue;
          }
          if (parts[0].equals("class")) {
            // Parse class mapping: "class package.PrettyName pkg.RuntimeName"
            String prettyClassName = parts[1];
            runtimeClassName = parts[2];
            classMappings.put(prettyClassName, runtimeClassName);
          } else if (runtimeClassName != null && parts[0].equals("field")) {
            // Parse field mapping: "field prettyName runtimeName"
            String prettyMemberName = parts[1];
            String runtimeMemberName = parts[2];
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
          } else if (runtimeClassName != null && parts[0].equals("method")) {
            // Parse method mapping: "method prettyName runtimeName"
            String prettyMemberName = parts[1];
            String runtimeMemberName = parts[2];
            methodMappings.put(
                new ClassMemberKey(runtimeClassName, prettyMemberName), runtimeMemberName);
          } else {
            LOGGER.warn(
                "Malformed line in {}:{}: unexpected indented line outside of a class section:\n{}",
                sourceFilename,
                lineNumber,
                line);
          }
        }
      }

      if (!foundMappings) {
        return Optional.empty();
      }

      return Optional.of(new ScriptNameMappings(classMappings, fieldMappings, methodMappings));
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
        var scriptMappings =
            ScriptNameMappings.loadFromScript(sourceFilename, modLoaderName, mcVersion);
        if (scriptMappings.isPresent()) {
          nameMappings = scriptMappings.get();
        } else {
          var obfuscatedMappings = ObfuscatedNameMappings.loadFromFiles(modLoaderName, mcVersion);
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
    public Object call(Object... params) {
      expectNumParams(params, 1);
      var value = params[0];
      if (value instanceof Script.Function eventListener) {
        // TODO(maxuser): Call the event listener only when an event fires.
        LOGGER.info(
            "(maxuser-debug) Adding fake event listener... triggering it once for testing...");
        eventListener.call(new Event("fake"));
        LOGGER.info("(maxuser-debug) Done testing event listener");
      } else {
        throw new IllegalArgumentException("Expected addEventListener param to be callable");
      }
      return null; // TODO(maxuser): Return an listener registration object that can be cancelled.
    }
  }
}
