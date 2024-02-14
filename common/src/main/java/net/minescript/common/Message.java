// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

public record Message(Message.Type type, String value) {
  public enum Type {
    FUNCTION_CALL,
    MINECRAFT_COMMAND,
    MINESCRIPT_COMMAND,
    CHAT_MESSAGE,
    PLAIN_TEXT,
    JSON_FORMATTED_TEXT
  }

  public static Message createFunctionCall(String value) {
    return new Message(Type.FUNCTION_CALL, value);
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
}
