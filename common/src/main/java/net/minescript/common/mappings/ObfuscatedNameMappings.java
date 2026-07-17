// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.mappings;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ObfuscatedNameMappings implements NameMappings {
  private static final Logger LOGGER = LogManager.getLogger();

  // Map: officialClassName -> obfuscatedClassName
  // e.g. "net.minecraft.client.Minecraft" -> "fud"
  private final BiMap<String, String> officialToObfuscatedClassMap = HashBiMap.create();

  // Map: [obfuscatedClassName, officialFieldName] -> obfuscatedFieldName
  private final Map<ClassMemberKey, String> officialFieldMap = new HashMap<>();

  private record MethodSignature(String methodName, String signature) {}

  // Map: [obfuscatedClassName, officialMethodName] -> Set(obfuscated MethodSignature)]
  // e.g. for "net.minecraft.world.phys.shapes.VoxelShape::add":
  //   ["fjm", "max"] -> Set(["b", "Ljh$a;DD"], ["c", "Ljh$a;"])
  // "Obficial" refers to the key containing the obfuscated class name and the official method name.
  private final HashMultimap<ClassMemberKey, MethodSignature>
      obficialMethodNameToObfuscatedSigMethodMap = HashMultimap.create();

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

  private record ObfuscatedMethodKey(
      String obfuscatedClassName, MethodSignature obfuscatedMethodSig) {}

  // Map: [obfuscatedClassName, obfuscated MethodSignature] -> Set(fabricMethodName)]
  private final HashMultimap<ObfuscatedMethodKey, String> fabricMethodMap = HashMultimap.create();

  // Map: officialClassName -> fabricClassName
  private final Map<String, String> officialToFabricClassCache = new ConcurrentHashMap<>();

  // Map: fabricClassName -> officialClassName
  // (officialToFabricClassMap and fabricToOfficialClassMap could be BiMap inversions of each
  // other, but if a caller passes in a pretty name when a runtime name is expected, or vice versa,
  // the maps could get into inconsistent states and throw an exception. So, keep the maps
  // independent.)
  private final Map<String, String> fabricToOfficialClassCache = new ConcurrentHashMap<>();

  // Map: [fabricClassName, officialFieldName] -> fabricFieldName
  private final Map<ClassMemberKey, String> runtimeFieldCache = new ConcurrentHashMap<>();

  // Map: [fabricClassName, officialMethodName] -> Set(fabricMethodNames)]
  private final Map<ClassMemberKey, ImmutableSet<String>> runtimeMethodCache =
      new ConcurrentHashMap<>();

  private record ClassMemberKey(String className, String memberName) {}

  private ObfuscatedNameMappings() {}

  @Override
  public String getRuntimeClassName(String prettyClassName) {
    return officialToFabricClassCache.computeIfAbsent(
        prettyClassName, this::computeRuntimeClassName);
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
    LOGGER.info("Mapped official class name '{}' to Fabric name '{}'", prettyClassName, fabricName);
    return fabricName;
  }

  @Override
  public String getPrettyClassName(String runtimeClassName) {
    return fabricToOfficialClassCache.computeIfAbsent(
        runtimeClassName, this::computePrettyClassName);
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
        "Mapped Fabric class name '{}' to official name '{}'", runtimeClassName, officialName);
    return officialName;
  }

  @Override
  public String getRuntimeFieldName(Class<?> clazz, String prettyFieldName) {
    var cacheKey = new ClassMemberKey(clazz.getName(), prettyFieldName);
    return runtimeFieldCache.computeIfAbsent(
        cacheKey,
        ignoreKey -> {
          Optional<String> optFabricFieldName =
              getFabricFieldNameUncached(clazz, prettyFieldName, "");
          final String fabricFieldName;

          if (optFabricFieldName.isEmpty()) {
            fabricFieldName = prettyFieldName;
            LOGGER.info(
                "No mapping for field name '{}/{}.{}', falling back to '{}'",
                obfuscatedToOfficialClassMap.getOrDefault(
                    fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
                clazz.getName(),
                prettyFieldName,
                fabricFieldName);
          } else {
            fabricFieldName = optFabricFieldName.get();
            LOGGER.info(
                "Mapped Fabric.official field name '{}/{}.{}' to '{}'",
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
  public Set<String> getPrettyFieldNames(Class<?> clazz) {
    var fieldNames = new HashSet<String>();
    getPrettyFieldNamesRecurse(clazz, fieldNames);
    return fieldNames;
  }

  private void getPrettyFieldNamesRecurse(Class<?> clazz, HashSet<String> fieldNames) {
    String obfuscatedClassName = fabricToObfuscatedClassMap.get(clazz.getName());
    if (obfuscatedClassName == null) {
      Arrays.stream(clazz.getFields()).map(Field::getName).forEach(fieldNames::add);
      return; // No need to recurse since getFields() captures superclasses and interfaces.
    }

    officialFieldMap.keySet().stream()
        .filter(key -> key.className().equals(obfuscatedClassName))
        .map(key -> key.memberName())
        .forEach(fieldNames::add);

    if (clazz.getSuperclass() != null) {
      getPrettyFieldNamesRecurse(clazz.getSuperclass(), fieldNames);
    }
    Arrays.stream(clazz.getInterfaces()).forEach(i -> getPrettyFieldNamesRecurse(i, fieldNames));
  }

  @Override
  public Set<String> getRuntimeMethodNames(Class<?> clazz, String prettyMethodName) {
    var cacheKey = new ClassMemberKey(clazz.getName(), prettyMethodName);
    return runtimeMethodCache.computeIfAbsent(
        cacheKey,
        ignoreKey -> {
          Set<String> fabricMethodNames = getFabricMethodNamesUncached(clazz, prettyMethodName, "");
          if (fabricMethodNames.isEmpty()) {
            fabricMethodNames = Set.of(prettyMethodName);
            LOGGER.info(
                "No mapping for method name '{}/{}.{}', falling back to {}",
                obfuscatedToOfficialClassMap.getOrDefault(
                    fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
                clazz.getName(),
                prettyMethodName,
                fabricMethodNames);
          } else {
            LOGGER.info(
                "Mapped method name '{}/{}.{}' to {}",
                obfuscatedToOfficialClassMap.getOrDefault(
                    fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
                clazz.getName(),
                prettyMethodName,
                fabricMethodNames);
          }
          return ImmutableSet.copyOf(fabricMethodNames);
        });
  }

  @Override
  public Set<String> getPrettyMethodNames(Class<?> clazz) {
    var methodNames = new HashSet<String>();
    getPrettyMethodNamesRecurse(clazz, methodNames);
    return methodNames;
  }

  private void getPrettyMethodNamesRecurse(Class<?> clazz, HashSet<String> methodNames) {
    String obfuscatedClassName = fabricToObfuscatedClassMap.get(clazz.getName());
    if (obfuscatedClassName == null) {
      Arrays.stream(clazz.getMethods()).map(Method::getName).forEach(methodNames::add);
      return; // No need to recurse since getMethods() captures superclasses and interfaces.
    }

    obficialMethodNameToObfuscatedSigMethodMap.keySet().stream()
        .filter(key -> key.className().equals(obfuscatedClassName))
        .map(key -> key.memberName())
        .forEach(methodNames::add);

    if (clazz.getSuperclass() != null) {
      getPrettyMethodNamesRecurse(clazz.getSuperclass(), methodNames);
    }
    Arrays.stream(clazz.getInterfaces()).forEach(i -> getPrettyMethodNamesRecurse(i, methodNames));
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
      Class<?> clazz, String officialMethodName, String indent) {
    String obfuscatedClassName = fabricToObfuscatedClassMap.get(clazz.getName());
    if (obfuscatedClassName == null) {
      return Set.of();
    }

    var obficialMethodKey = new ClassMemberKey(obfuscatedClassName, officialMethodName);
    Set<MethodSignature> obfuscatedMethodSignatures =
        obficialMethodNameToObfuscatedSigMethodMap.get(obficialMethodKey);

    var fabricMethodNames = new HashSet<String>();
    for (var obfuscatedMethodSignature : obfuscatedMethodSignatures) {
      var fabricMemberKey = new ObfuscatedMethodKey(obfuscatedClassName, obfuscatedMethodSignature);
      fabricMethodNames.addAll(fabricMethodMap.get(fabricMemberKey));
    }

    if (fabricMethodNames.isEmpty()) {
      LOGGER.info(
          "{}Could not find mapping in {}/{} for member {}; trying superclass and interfaces...",
          indent,
          obfuscatedToOfficialClassMap.getOrDefault(
              fabricToObfuscatedClassMap.get(clazz.getName()), "?"),
          clazz.getName(),
          officialMethodName);

      var superclass = clazz.getSuperclass();
      if (superclass != null) {
        LOGGER.info(
            "{}Looking for member {} in superclass {}/{}",
            indent,
            officialMethodName,
            obfuscatedToOfficialClassMap.getOrDefault(
                fabricToObfuscatedClassMap.get(superclass.getName()), "?"),
            superclass.getName());

        var names = getFabricMethodNamesUncached(superclass, officialMethodName, indent + "  ");
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
            officialMethodName,
            obfuscatedToOfficialClassMap.getOrDefault(
                fabricToObfuscatedClassMap.get(iface.getName()), "?"),
            iface.getName());
        var names = getFabricMethodNamesUncached(iface, officialMethodName, indent + "  ");
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
        officialMethodName,
        fabricMethodNames);
    return fabricMethodNames;
  }

  private static boolean debugLogging = false;

  public static void enableDebugLogging(boolean enable) {
    debugLogging = enable;
  }

  public static Optional<ObfuscatedNameMappings> loadFromFiles(
      String modLoaderName, String mcVersion) throws IOException {
    Path mappingsVersionPath = Paths.get("minescript", "system", "mappings", mcVersion);
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

    // Map: [obfuscatedClassName, officialMethodName] -> Set("obficial" MethodSignature)]
    // e.g. for "net.minecraft.world.phys.shapes.VoxelShape::add":
    //   ["fjm", "max"] -> Set(["b", "net.minecraft.core.Direction$Axis,double,double",
    //                         ["c", "net.minecraft.core.Direction$Axis"])
    //
    // This map is used only temporarily during loading of official mappings. After all the
    // official-to-obfuscated class name mappings are known, populate the
    // obficialMethodNameToObfuscatedSigMethodMap from this.
    final HashMultimap<ClassMemberKey, MethodSignature> obficialMethodNameToObficialSigMethodMap =
        HashMultimap.create();

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
                "(debug) Mapped official class name '{}' to obfuscated name '{}'",
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
                  "(debug) Mapped class '{}' official field name '{}' to obfuscated name '{}'",
                  obfuscatedClassName,
                  officialMemberName,
                  obfuscatedMemberName);
            }
          } else {
            var methodParamsWithoutParens = methodParams.substring(1, methodParams.length() - 1);
            obficialMethodNameToObficialSigMethodMap.put(
                new ClassMemberKey(obfuscatedClassName, officialMemberName),
                new MethodSignature(obfuscatedMemberName, methodParamsWithoutParens));
            if (debugLogging) {
              LOGGER.info(
                  "(debug) Mapped class '{}' official method '{}' to obfuscated name '{}' with"
                      + " signature '{}'",
                  obfuscatedClassName,
                  officialMemberName,
                  obfuscatedMemberName,
                  methodParamsWithoutParens);
            }
          }
        }
      }
    }

    long mapSignaturesStartTimeMillis = System.currentTimeMillis();
    for (var entry : obficialMethodNameToObficialSigMethodMap.entries()) {
      // key: [obfuscatedClassName, officialMethodName]
      ClassMemberKey obficialMethodKey = entry.getKey();
      MethodSignature obficialMethodSig = entry.getValue();
      var obfuscatedMethodSig =
          new MethodSignature(
              obficialMethodSig.methodName,
              obfuscateOfficialMethodSignature(
                  officialToObfuscatedClassMap::get, obficialMethodSig.signature));
      if (debugLogging) {
        LOGGER.info(
            "(debug) Obfuscated class '{}' method '{}' sig '{}' as '{}'",
            obficialMethodKey.className,
            obficialMethodKey.memberName,
            obficialMethodSig.signature,
            obfuscatedMethodSig.signature);
      }
      obficialMethodNameToObfuscatedSigMethodMap.put(obficialMethodKey, obfuscatedMethodSig);
    }
    long mapSignaturesEndTimeMillis = System.currentTimeMillis();
    LOGGER.info(
        "Mapped {} method signatures from official names to obfuscated JNI method format in {}ms",
        obficialMethodNameToObficialSigMethodMap.size(),
        mapSignaturesEndTimeMillis - mapSignaturesStartTimeMillis);

    long endTimeMillis = System.currentTimeMillis();
    LOGGER.info(
        "Loaded {} classes, {} fields, and {} methods from official mappings file in {}ms",
        officialToObfuscatedClassMap.size(),
        officialFieldMap.size(),
        obficialMethodNameToObfuscatedSigMethodMap.size(),
        endTimeMillis - startTimeMillis);
  }

  /**
   * Converts a method signature with official names to a signature with obfuscated names.
   *
   * <p>E.g. convert "net.minecraft.core.Direction$Axis,double,double" to "Ljh$a;DD".
   */
  private static String obfuscateOfficialMethodSignature(
      Function<String, String> toObfuscatedClassName, String officialSignature) {
    // officialSignature.split(",") below given "" returns [""]. So short-circuit that.
    if (officialSignature.isEmpty()) {
      return "";
    }
    String[] officialArgs = officialSignature.split(",");
    var obfuscatedArgs = new StringBuilder();
    for (String officialArg : officialArgs) {
      while (officialArg.endsWith("[]")) {
        officialArg = officialArg.substring(0, officialArg.length() - 2);
        obfuscatedArgs.append("[");
      }
      switch (officialArg) {
        case "boolean":
          obfuscatedArgs.append("Z");
          break;
        case "byte":
          obfuscatedArgs.append("B");
          break;
        case "char":
          obfuscatedArgs.append("C");
          break;
        case "double":
          obfuscatedArgs.append("D");
          break;
        case "float":
          obfuscatedArgs.append("F");
          break;
        case "int":
          obfuscatedArgs.append("I");
          break;
        case "long":
          obfuscatedArgs.append("J");
          break;
        case "short":
          obfuscatedArgs.append("S");
          break;
        default:
          String obfuscatedName = toObfuscatedClassName.apply(officialArg);
          if (obfuscatedName == null) {
            obfuscatedName = officialArg;
          }
          obfuscatedArgs.append("L").append(obfuscatedName.replace('.', '/')).append(";");
          break;
      }
    }
    return obfuscatedArgs.toString();
  }

  private static final Pattern FABRIC_MAPPINGS_CLASS_RE =
      Pattern.compile("^CLASS\t([a-z0-9A-Z_/$]+)\t([a-z0-9A-Z_/$]+)$");

  private static final Pattern FABRIC_MAPPINGS_FIELD_RE =
      Pattern.compile("^FIELD\t([a-z0-9A-Z_/$]+)\t[^\t]+\t([^\t]+)\t([^\t]+)$");

  private static final Pattern FABRIC_MAPPINGS_METHOD_RE =
      Pattern.compile("^METHOD\t([a-z0-9A-Z_/$]+)\t\\(([^)]*)\\)[^\t]+\t([^\t]+)\t([^\t]+)$");

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
                "(debug) Mapped obfuscated class name '{}' to Fabric name '{}'",
                obfuscatedClassName,
                fabricClassName);
          }
          continue;
        }

        // E.g. "FIELD\tfjm\t[Lfjm;\tb\tfield_19318" matches ["fjm", "b", "field_19318"]
        match = FABRIC_MAPPINGS_FIELD_RE.matcher(line);
        if (match.find()) {
          String obfuscatedClassName = match.group(1).replace('/', '.');
          String obfuscatedMemberName = match.group(2).replace('/', '.');
          String fabricFieldName = match.group(3);
          fabricFieldMap.put(
              new ClassMemberKey(obfuscatedClassName, obfuscatedMemberName), fabricFieldName);
          if (debugLogging) {
            LOGGER.info(
                "(debug) Mapped obfuscated field name '{}' of class '{}' to Fabric name" + " '{}'",
                obfuscatedMemberName,
                obfuscatedClassName,
                fabricFieldName);
          }
          continue;
        }

        // E.g. "METHOD\tfjm\t(Lfis;[Lfis;DDDDDD)V\ta\tmethod_33662" matches
        // ["fjm", "Lfis;[Lfis;DDDDDD", "a", "method_33662"]
        match = FABRIC_MAPPINGS_METHOD_RE.matcher(line);
        if (match.find()) {
          String obfuscatedClassName = match.group(1).replace('/', '.');
          String obfuscatedMethodSignature = match.group(2);
          String obfuscatedMethodName = match.group(3).replace('/', '.');
          String fabricMethodName = match.group(4);
          fabricMethodMap.put(
              new ObfuscatedMethodKey(
                  obfuscatedClassName,
                  new MethodSignature(obfuscatedMethodName, obfuscatedMethodSignature)),
              fabricMethodName);
          if (debugLogging) {
            LOGGER.info(
                "(debug) Mapped class '{}' method '{}' with signature '{}' to Fabric name '{}'",
                obfuscatedClassName,
                obfuscatedMethodName,
                obfuscatedMethodSignature,
                fabricMethodName);
          }
          continue;
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
