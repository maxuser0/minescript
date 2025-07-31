// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mappings;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ObfuscatedNameMappings implements NameMappings {
  private static final Logger LOGGER = LogManager.getLogger();

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
  private final Map<ClassMemberKey, String> fabricFieldMap = new ConcurrentHashMap<>();

  // Map: [obfuscatedClassName, obfuscatedMethodName] -> Set(fabricMethodName)]
  private final HashMultimap<ClassMemberKey, String> fabricMethodMap = HashMultimap.create();

  // Map: officialClassName -> fabricClassName
  private final Map<String, String> officialToFabricClassMap = new ConcurrentHashMap<>();

  // Map: fabricClassName -> officialClassName
  // (officialToFabricClassMap and fabricToOfficialClassMap could be BiMap inversions of each
  // other, but if a caller passes in a pretty name when a runtime name is expected, or vice versa,
  // the maps could get into inconsistent states and throw an exception. So, keep the maps
  // independent.)
  private final Map<String, String> fabricToOfficialClassMap = new ConcurrentHashMap<>();

  // Map: [fabricClassName, officialFieldName] -> fabricFieldName
  private final Map<ClassMemberKey, String> runtimeFieldMap = new ConcurrentHashMap<>();

  // Map: [fabricClassName, officialMethodName] -> Set(fabricMethodNames)]
  private final Map<ClassMemberKey, ImmutableSet<String>> runtimeMethodMap =
      new ConcurrentHashMap<>();

  private record ClassMemberKey(String runtimeClassName, String prettyMemberName) {}

  private ObfuscatedNameMappings() {}

  @Override
  public String getRuntimeClassName(String prettyClassName) {
    return officialToFabricClassMap.computeIfAbsent(prettyClassName, this::computeRuntimeClassName);
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
    LOGGER.info("Mapped official class name `{}` to Fabric name `{}`", prettyClassName, fabricName);
    return fabricName;
  }

  @Override
  public String getPrettyClassName(String runtimeClassName) {
    return fabricToOfficialClassMap.computeIfAbsent(runtimeClassName, this::computePrettyClassName);
  }

  private String computePrettyClassName(String runtimeClassName) {
    String obfuscatedName = fabricToObfuscatedClassMap.get(runtimeClassName);
    if (obfuscatedName == null) {
      return runtimeClassName;
    }
    String officialName = obfuscatedToOfficialClassMap.get(obfuscatedName);
    if (officialName == null) {
      return runtimeClassName;
    }
    LOGGER.info(
        "Mapped Fabric class name `{}` to official name `{}`", runtimeClassName, officialName);
    return officialName;
  }

  @Override
  public String getRuntimeFieldName(Class<?> clazz, String prettyFieldName) {
    var cacheKey = new ClassMemberKey(clazz.getName(), prettyFieldName);
    return runtimeFieldMap.computeIfAbsent(
        cacheKey,
        ignoreKey -> {
          Optional<String> optFabricFieldName =
              getFabricFieldNameUncached(clazz, prettyFieldName, "");
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
          return fabricFieldName;
        });
  }

  @Override
  public Set<String> getRuntimeMethodNames(Class<?> clazz, String prettyMethodName) {
    var cacheKey = new ClassMemberKey(clazz.getName(), prettyMethodName);
    return runtimeMethodMap.computeIfAbsent(
        cacheKey,
        ignoreKey -> {
          Set<String> fabricMethodNames = getFabricMethodNamesUncached(clazz, prettyMethodName, "");
          if (fabricMethodNames.isEmpty()) {
            fabricMethodNames = Set.of(prettyMethodName);
            LOGGER.warn(
                "No mapping for Fabric.official method name `{}/{}.{}`, falling back to {}",
                obfuscatedToOfficialClassMap.getOrDefault(
                    fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
                clazz.getName(),
                prettyMethodName,
                fabricMethodNames);
          } else {
            LOGGER.info(
                "Mapped Fabric.official method name `{}/{}.{}` to {}",
                obfuscatedToOfficialClassMap.getOrDefault(
                    fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
                clazz.getName(),
                prettyMethodName,
                fabricMethodNames);
          }
          return ImmutableSet.copyOf(fabricMethodNames);
        });
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

  public static boolean debugLogging = false;

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
      Pattern.compile("^([^ ]+) -> ([a-z0-9A-Z_.$]+):$");

  private static final Pattern OFFICIAL_MAPPINGS_MEMBER_RE =
      Pattern.compile(" ([a-z0-9A-Z_$]+)(\\([^\\)]*\\))? -> ([a-z0-9A-Z_$]+)$");

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
          if (debugLogging) {
            LOGGER.info(
                "(debug) Mapped official class name `{}` to obfuscated name `{}`",
                officialClassName,
                obfuscatedClassName);
          }
        }

        // E.g. "2502:2502:net.minecraft.client.Minecraft getInstance() -> R"
        match = OFFICIAL_MAPPINGS_MEMBER_RE.matcher(line);
        if (obfuscatedClassName != null && match.find()) {
          String officialMemberName = match.group(1);
          String methodParams = match.group(2);
          String obfuscatedMemberName = match.group(3);
          if (methodParams == null || methodParams.isEmpty()) {
            officialFieldMap.put(
                new ClassMemberKey(obfuscatedClassName, officialMemberName), obfuscatedMemberName);
            if (debugLogging) {
              LOGGER.info(
                  "(debug) Mapped official field name `{}` of class `{}` to obfuscated name `{}`",
                  officialMemberName,
                  obfuscatedClassName,
                  obfuscatedMemberName);
            }
          } else {
            officialMethodMap.put(
                new ClassMemberKey(obfuscatedClassName, officialMemberName), obfuscatedMemberName);
            if (debugLogging) {
              LOGGER.info(
                  "(debug) Mapped official method name`{}` of class `{}` to obfuscated name `{}`",
                  officialMemberName,
                  obfuscatedClassName,
                  obfuscatedMemberName);
            }
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
      Pattern.compile("^CLASS\t([a-z0-9A-Z_/$]+)\t([a-z0-9A-Z_/$]+)$");

  private static final Pattern FABRIC_MAPPINGS_MEMBER_RE =
      Pattern.compile("^(FIELD|METHOD)\t([a-z0-9A-Z_/$]+)\t[^\t]+\t([^\t]+)\t([^\t]+)$");

  private void loadFabricMappings(Path fabricMappingsPath) throws IOException {
    long startTimeMillis = System.currentTimeMillis();
    try (BufferedReader reader = new BufferedReader(new FileReader(fabricMappingsPath.toFile()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        // E.g. "CLASS\tfud\tnet/minecraft/class_310"
        var match = FABRIC_MAPPINGS_CLASS_RE.matcher(line);
        if (match.find()) {
          String obfuscatedClassName = match.group(1).replace('/', '.');
          String fabricClassName = match.group(2).replace('/', '.');
          obfuscatedToFabricClassMap.put(obfuscatedClassName, fabricClassName);
          if (debugLogging) {
            LOGGER.info(
                "(debug) Mapped obfuscated class name `{}` to Fabric name `{}`",
                obfuscatedClassName,
                fabricClassName);
          }
        }

        // E.g. "METHOD\tfud\t()Lfud;\tR\tmethod_1551"
        match = FABRIC_MAPPINGS_MEMBER_RE.matcher(line);
        if (match.find()) {
          String memberType = match.group(1);
          String obfuscatedClassName = match.group(2).replace('/', '.');
          String obfuscatedMemberName = match.group(3).replace('/', '.');
          String fabricMemberName = match.group(4);
          if (memberType.equals("FIELD")) {
            fabricFieldMap.put(
                new ClassMemberKey(obfuscatedClassName, obfuscatedMemberName), fabricMemberName);
            if (debugLogging) {
              LOGGER.info(
                  "(debug) Mapped obfuscated field name`{}` of class `{}` to Fabric name" + " `{}`",
                  obfuscatedMemberName,
                  obfuscatedClassName,
                  fabricMemberName);
            }
          } else {
            fabricMethodMap.put(
                new ClassMemberKey(obfuscatedClassName, obfuscatedMemberName), fabricMemberName);
            if (debugLogging) {
              LOGGER.info(
                  "(debug) Mapped obfuscated method name`{}` of class `{}` to Fabric name"
                      + " `{}`",
                  obfuscatedMemberName,
                  obfuscatedClassName,
                  fabricMemberName);
            }
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
