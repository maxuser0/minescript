// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.List;

public enum ScriptRedirect {
  DEFAULT,
  CHAT,
  ECHO,
  LOG,
  NULL;

  public record Pair(ScriptRedirect stdout, ScriptRedirect stderr) {
    public static final Pair DEFAULTS = new Pair(DEFAULT, DEFAULT);
  }

  public static Pair parseAndRemoveRedirects(List<String> commandTokens) {
    ScriptRedirect stdout = DEFAULT;
    ScriptRedirect stderr = DEFAULT;

    // Iterate the last 2 command tokens in reverse order, removing ones that correspond to
    // redirects.
    for (int i = 0; i < 2; ++i) {
      if (commandTokens.size() < 3) {
        break;
      }

      String secondLastToken = commandTokens.get(commandTokens.size() - 2);
      String lastToken = commandTokens.get(commandTokens.size() - 1);
      if (secondLastToken.equals(">")) {
        stdout = parseValue(lastToken);
        if (stdout != DEFAULT) {
          commandTokens.remove(commandTokens.size() - 1);
          commandTokens.remove(commandTokens.size() - 1);
        }
      } else if (secondLastToken.equals("2>")) {
        stderr = parseValue(lastToken);
        if (stderr != DEFAULT) {
          commandTokens.remove(commandTokens.size() - 1);
          commandTokens.remove(commandTokens.size() - 1);
        }
      } else {
        break;
      }
    }

    if (stdout == DEFAULT && stderr == DEFAULT) {
      return Pair.DEFAULTS;
    } else {
      return new Pair(stdout, stderr);
    }
  }

  private static ScriptRedirect parseValue(String value) {
    switch (value) {
      case "chat":
        return CHAT;
      case "echo":
        return ECHO;
      case "log":
        return LOG;
      case "null":
        return NULL;
      default:
        return DEFAULT;
    }
  }
}
