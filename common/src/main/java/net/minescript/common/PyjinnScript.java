// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.common.collect.HashMultimap;
import com.google.gson.JsonElement;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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

  // TODO(maxuser): Move all this work to a factory method and make the c'tor private.
  private PyjinnScript(Script script, Runnable onFinish) {
    this.script = script;
    this.onFinish = onFinish;

    // TODO(maxuser): Define addEventListener in a module that needs to be imported explicitly?
    script.globals().setVariable("addEventListener", new AddEventListener());
  }

  public static class NameMappings {
    String getRuntimeClassName(String prettyClassName) {
      return prettyClassName;
    }

    Set<String> getRuntimeMemberNames(Class<?> clazz, String prettyMemberName) {
      return Set.of(prettyMemberName);
    }
  }

  private record ClassMemberKey(String runtimeClassName, String prettyMemberName) {}

  public static class ObfuscatedNameMappings extends NameMappings {
    private final Map<String, String> classMappings;
    private final HashMultimap<ClassMemberKey, String>
        memberMappings; // Map: runtimeClassName -> (prettyMemberName -> runtimeMemberNames)

    public ObfuscatedNameMappings(
        Map<String, String> classMappings, HashMultimap<ClassMemberKey, String> memberMappings) {
      this.classMappings = classMappings;
      this.memberMappings = memberMappings;
    }

    @Override
    public String getRuntimeClassName(String prettyClassName) {
      return classMappings.getOrDefault(prettyClassName, prettyClassName);
    }

    @Override
    public Set<String> getRuntimeMemberNames(Class<?> clazz, String prettyMemberName) {
      String runtimeClassName = clazz.getName();
      var memberKey = new ClassMemberKey(runtimeClassName, prettyMemberName);
      Set<String> runtimeMemberNames = memberMappings.get(memberKey);
      if (!runtimeMemberNames.isEmpty()) {
        return runtimeMemberNames;
      }

      // No mapping found for the class member, so fall back to the pretty name.
      return Set.of(prettyMemberName);
    }
  }

  /**
   * Returns mappings loaded from a file based on the mod loader and MC version.
   *
   * <p>Loads mappings from a file named like "fabric-1.21.6-mappings.txt" in the minescript dir.
   * Reads the mappings file only if the Minecraft class name is obfuscated.
   *
   * <p>The file format is:
   *
   * <ul>
   *   <li>comments start with "#" and run until the end of the line
   *   <li>blank lines are ignored
   *   <li>non-indented lines indicate a class entry mapping pretty name to runtime name:
   *       <p>"com.foo.PrettyName runtimeName"
   *   <li>indented lines indicate a member within the most recent earlier class line, e.g.
   *       <p>" prettyName runtimeName"
   * </ul>
   */
  public static ObfuscatedNameMappings loadMappingsFile(String modLoaderName, String mcVersion)
      throws IOException {
    String mappingsFileName = String.format("%s-%s-mappings.txt", modLoaderName, mcVersion);

    Path mappingsPath = Paths.get("minescript", mappingsFileName);

    Map<String, String> classMappings = new HashMap<>();
    HashMultimap<ClassMemberKey, String> memberMappings = HashMultimap.create();

    String runtimeClassName = null;
    try (BufferedReader reader = new BufferedReader(new FileReader(mappingsPath.toFile()))) {
      String line;
      int lineNumber = 0;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        line = line.split("#")[0].stripTrailing(); // Remove comments.
        if (line.trim().isEmpty()) {
          continue;
        }
        if (!line.startsWith(" ")) {
          // Parse class line which has no leading whitespace.
          String[] parts = line.split("\\s+");
          if (parts.length == 2) {
            String prettyClassName = parts[0];
            runtimeClassName = parts[1];
            classMappings.put(prettyClassName, runtimeClassName);
          } else {
            LOGGER.warn(
                "Malformed line in {}:{}: expected 2 words on class line but got {}:\n{}",
                mappingsFileName,
                lineNumber,
                parts.length,
                line);
          }
        } else if (runtimeClassName != null) {
          // Parse member line which is indented within the current class.
          String[] parts = line.stripLeading().split("\\s+");
          if (parts.length == 2) {
            String prettyMemberName = parts[0];
            String runtimeMemberName = parts[1];
            memberMappings.put(
                new ClassMemberKey(runtimeClassName, prettyMemberName), runtimeMemberName);
          } else {
            LOGGER.warn(
                "Malformed line in {}:{}: expected 2 words on member line but got {}:\n{}",
                mappingsFileName,
                lineNumber,
                parts.length,
                line);
          }
        } else {
          LOGGER.warn(
              "Malformed line in {}:{}: unexpected indented line outside of a class section:\n{}",
              mappingsFileName,
              lineNumber,
              line);
        }
      }
    }

    return new ObfuscatedNameMappings(classMappings, memberMappings);
  }

  public static PyjinnScript create(
      ScriptConfig.ExecutableCommand execCommand,
      Consumer<String> stdoutConsumer,
      String modLoaderName,
      Runnable onFinish)
      throws Exception {
    try {
      final NameMappings nameMappings;
      // Check for obfuscated names, and if needed, load a mappings file.
      if (!Minecraft.class.getName().equals("net.minecraft.client.Minecraft")) {
        String mcVersion = SharedConstants.getCurrentVersion().name();
        // TODO(maxuser): Cache the name mappings if they haven't changed on disk.
        nameMappings = loadMappingsFile(modLoaderName.toLowerCase(), mcVersion);
      } else {
        nameMappings = new NameMappings();
      }

      String sourceFilename = execCommand.command()[0];
      String sourceCode = Files.readString(Paths.get(sourceFilename));
      JsonElement ast = PyjinnParser.parse(sourceCode);
      var script =
          new Script(
              PyjinnScript.class.getClassLoader(),
              nameMappings::getRuntimeClassName,
              nameMappings::getRuntimeMemberNames);

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
