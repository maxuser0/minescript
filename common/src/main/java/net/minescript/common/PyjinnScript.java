// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.google.gson.JsonElement;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
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

    String getRuntimeMemberName(Class<?> clazz, String prettyMemberName) {
      return prettyMemberName;
    }
  }

  public static class ObfuscatedNameMappings extends NameMappings {
    private final Map<String, String> classMappings;
    private final Map<String, Map<String, String>>
        memberMappings; // Map: runtimeClassName -> (prettyMemberName -> runtimeMemberName)

    public ObfuscatedNameMappings(
        Map<String, String> classMappings, Map<String, Map<String, String>> memberMappings) {
      this.classMappings = classMappings;
      this.memberMappings = memberMappings;
    }

    @Override
    public String getRuntimeClassName(String prettyClassName) {
      return classMappings.getOrDefault(prettyClassName, prettyClassName);
    }

    @Override
    public String getRuntimeMemberName(Class<?> clazz, String prettyMemberName) {
      String runtimeClassName = clazz.getName();
      Map<String, String> classMemberMappings = memberMappings.get(runtimeClassName);
      if (classMemberMappings != null) {
        return classMemberMappings.getOrDefault(prettyMemberName, prettyMemberName);
      }
      return prettyMemberName; // No mapping found for the class or member.
    }
  }

  public static ObfuscatedNameMappings loadMappingFiles(String modLoaderName, String mcVersion)
      throws IOException {
    String classesFileName = String.format("%s-%s-classes.txt", modLoaderName, mcVersion);
    String membersFileName = String.format("%s-%s-members.txt", modLoaderName, mcVersion);

    Path classesPath = Paths.get("minescript", classesFileName);
    Path membersPath = Paths.get("minescript", membersFileName);

    Map<String, String> classMappings = new HashMap<>();
    Map<String, Map<String, String>> memberMappings = new HashMap<>();

    // Load class mappings
    try (BufferedReader reader = new BufferedReader(new FileReader(classesPath.toFile()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.trim().split(" ");
        if (parts.length == 2) {
          classMappings.put(parts[0], parts[1]);
        } else {
          LOGGER.warn("Malformed line in " + classesFileName + ": " + line);
        }
      }
    }

    // Load member mappings
    try (BufferedReader reader = new BufferedReader(new FileReader(membersPath.toFile()))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] parts = line.trim().split(" ");
        if (parts.length == 3) {
          String runtimeClassName = parts[0];
          String prettyMemberName = parts[1];
          String runtimeMemberName = parts[2];

          memberMappings
              .computeIfAbsent(runtimeClassName, k -> new HashMap<>())
              .put(prettyMemberName, runtimeMemberName);
        } else {
          LOGGER.warn("Malformed line in " + membersFileName + ": " + line);
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
        nameMappings = loadMappingFiles(modLoaderName.toLowerCase(), mcVersion);
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
              nameMappings::getRuntimeMemberName);

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
