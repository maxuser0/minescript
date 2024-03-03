// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.List;

public record Message(Message.Type type, String value, Record data) {
  public enum Type {
    FUNCTION_CALL,
    MINECRAFT_COMMAND,
    MINESCRIPT_COMMAND,
    CHAT_MESSAGE,
    PLAIN_TEXT,
    JSON_FORMATTED_TEXT
  }

  public record FunctionCallData(long funcCallId, String argsString, List<?> args) {}

  public Message(Message.Type type, String value) {
    this(type, value, null);
  }

  public static Message createFunctionCall(
      long funcCallId, String functionName, String argsString, List<?> args) {
    return new Message(
        Type.FUNCTION_CALL, functionName, new FunctionCallData(funcCallId, argsString, args));
  }

  public static Message createMinecraftCommand(String value) {
    return new Message(Type.MINECRAFT_COMMAND, value);
  }

  public static Message createMinescriptCommand(String value) {
    return new Message(Type.MINESCRIPT_COMMAND, value);
  }

  public static Message createChatMessage(String value) {
    return new Message(Type.CHAT_MESSAGE, value);
  }

  public static Message fromPlainText(String value) {
    return new Message(Type.PLAIN_TEXT, value);
  }

  public static Message fromJsonFormattedText(String value) {
    return new Message(Type.JSON_FORMATTED_TEXT, value);
  }

  public static Message formatAsJsonColoredText(String text, String color) {
    return Message.fromJsonFormattedText(
        "{\"text\":\""
            + text.replace("\\", "\\\\").replace("\"", "\\\"")
            + "\",\"color\":\""
            + color
            + "\"}");
  }
}
