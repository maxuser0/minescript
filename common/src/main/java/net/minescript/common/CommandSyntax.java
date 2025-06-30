// SPDX-FileCopyrightText: © 2022-2025 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CommandSyntax {

  private enum ParseState {
    START,
    WORD_OUTSIDE_QUOTES,
    SPACES_OUTSIDE_QUOTES,
    INSIDE_SINGLE_QUOTES,
    INSIDE_DOUBLE_QUOTES
  }

  public static String quoteString(String value) {
    return quoteString(value, false);
  }

  public static String quoteString(String value, boolean alwaysQuote) {
    if (value.isEmpty()) {
      return "\"\"";
    }

    long numWhitespace = value.chars().filter(ch -> ch == ' ' || ch == '\n').count();
    long numBackslashes = value.chars().filter(ch -> ch == '\\').count();
    long numSingleQuotes = value.chars().filter(ch -> ch == '\'').count();
    long numDoubleQuotes = value.chars().filter(ch -> ch == '"').count();

    if (numWhitespace == 0 && numBackslashes == 0 && numSingleQuotes == 0 && numDoubleQuotes == 0) {
      if (alwaysQuote) {
        return '"' + value + '"';
      } else {
        return value;
      }
    }

    var buffer = new StringBuilder();
    if (numDoubleQuotes > numSingleQuotes) {
      buffer.append('\'');
      buffer.append(value.replace("\\", "\\\\").replace("\n", "\\n").replace("'", "\\'"));
      buffer.append('\'');
    } else {
      buffer.append('"');
      buffer.append(value.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\""));
      buffer.append('"');
    }
    return buffer.toString();
  }

  public static String quoteCommand(String[] command) {
    var buffer = new StringBuilder();
    for (String value : command) {
      if (buffer.length() == 0) {
        // Special-case the first element since it's the command and we don't want to escape the
        // leading backslash.
        buffer.append(value);
      } else {
        buffer.append(' ');
        buffer.append(quoteString(value));
      }
    }
    return buffer.toString();
  }

  public static class Token {

    public static enum Type {
      STRING,
      AND,
      OR,
      SEMICOLON,
      REDIRECT_STDOUT,
      REDIRECT_STDERR
    }

    private static final Token AND_TOKEN = new Token(Type.AND);
    private static final Token OR_TOKEN = new Token(Type.OR);
    private static final Token SEMICOLON_TOKEN = new Token(Type.SEMICOLON);
    private static final Token REDIRECT_STDOUT_TOKEN = new Token(Type.REDIRECT_STDOUT);
    private static final Token REDIRECT_STDERR_TOKEN = new Token(Type.REDIRECT_STDERR);

    private final Optional<String> string;
    private final Type type;

    public static Token string(String string) {
      return new Token(string);
    }

    public static Token and() {
      return AND_TOKEN;
    }

    public static Token or() {
      return OR_TOKEN;
    }

    public static Token semicolon() {
      return SEMICOLON_TOKEN;
    }

    public static Token redirectStdout() {
      return REDIRECT_STDOUT_TOKEN;
    }

    public static Token redirectStderr() {
      return REDIRECT_STDERR_TOKEN;
    }

    public Type type() {
      return type;
    }

    @Override
    public String toString() {
      switch (type) {
        case STRING:
          return string.get();
        case AND:
          return "&&";
        case OR:
          return "||";
        case SEMICOLON:
          return ";";
        case REDIRECT_STDOUT:
          return ">";
        case REDIRECT_STDERR:
          return "2>";
        default:
          throw new IllegalStateException("Unsupported Token type: `" + type.toString() + "`");
      }
    }

    private Token(String string) {
      this.type = Type.STRING;
      this.string = Optional.of(string);
    }

    private Token(Type type) {
      this.type = type;
      this.string = Optional.empty();
    }
  }

  private static String consumeStringBuilder(StringBuilder builder) {
    String s = builder.toString();
    builder.setLength(0);
    return s;
  }

  public static List<Token> parseCommand(String command) {
    command += " "; // add space for simpler termination of parsing
    List<Token> args = new ArrayList<>();
    var state = ParseState.START;
    var argBuilder = new StringBuilder();
    var literalArgBuilder = new StringBuilder(); // buffer for arg preserving literals like quotes
    char prevCh = '\0';
    for (int i = 0; i < command.length(); ++i) {
      char ch = command.charAt(i);
      switch (state) {
        case START:
          switch (ch) {
            case '\'':
              state = ParseState.INSIDE_SINGLE_QUOTES;
              break;
            case '"':
              state = ParseState.INSIDE_DOUBLE_QUOTES;
              break;
            case ' ':
              state = ParseState.SPACES_OUTSIDE_QUOTES;
              break;
            default:
              state = ParseState.WORD_OUTSIDE_QUOTES;
              argBuilder.append(ch);
          }
          literalArgBuilder.append(ch);
          break;
        case WORD_OUTSIDE_QUOTES:
          switch (ch) {
            case '\'':
              if (prevCh == '\\') {
                argBuilder.setLength(argBuilder.length() - 1);
                argBuilder.append(ch);
              } else {
                state = ParseState.INSIDE_SINGLE_QUOTES;
              }
              literalArgBuilder.append(ch);
              break;
            case '"':
              if (prevCh == '\\') {
                argBuilder.setLength(argBuilder.length() - 1);
                argBuilder.append(ch);
              } else {
                state = ParseState.INSIDE_DOUBLE_QUOTES;
              }
              literalArgBuilder.append(ch);
              break;
            case ' ':
              {
                String arg = consumeStringBuilder(argBuilder);
                String literalArg = consumeStringBuilder(literalArgBuilder);
                if (literalArg.equals("&&")) {
                  args.add(Token.and());
                } else if (literalArg.equals("||")) {
                  args.add(Token.or());
                } else if (literalArg.endsWith(";")) {
                  String argPrefix = arg.substring(0, arg.length() - 1);
                  if (!argPrefix.isEmpty()) {
                    args.add(Token.string(argPrefix));
                  }
                  args.add(Token.semicolon());
                } else if (literalArg.startsWith(">")) {
                  args.add(Token.redirectStdout());
                  String argSuffix = arg.substring(1);
                  if (!argSuffix.isEmpty()) {
                    args.add(Token.string(argSuffix));
                  }
                } else if (literalArg.startsWith("2>")) {
                  args.add(Token.redirectStderr());
                  String argSuffix = arg.substring(2);
                  if (!argSuffix.isEmpty()) {
                    args.add(Token.string(argSuffix));
                  }
                } else {
                  args.add(Token.string(arg));
                }
                state = ParseState.SPACES_OUTSIDE_QUOTES;
                break;
              }
            default:
              argBuilder.append(ch);
              literalArgBuilder.append(ch);
          }
          break;
        case SPACES_OUTSIDE_QUOTES:
          switch (ch) {
            case '\'':
              state = ParseState.INSIDE_SINGLE_QUOTES;
              literalArgBuilder.append(ch);
              break;
            case '"':
              state = ParseState.INSIDE_DOUBLE_QUOTES;
              literalArgBuilder.append(ch);
              break;
            case ' ':
              break;
            default:
              argBuilder.append(ch);
              literalArgBuilder.append(ch);
              state = ParseState.WORD_OUTSIDE_QUOTES;
          }
          break;
        case INSIDE_SINGLE_QUOTES:
          switch (ch) {
            case '\'':
              if (prevCh == '\\') {
                argBuilder.setLength(argBuilder.length() - 1);
                argBuilder.append(ch);
              } else {
                state = ParseState.WORD_OUTSIDE_QUOTES;
              }
              break;
            case 'n':
              if (prevCh == '\\') {
                argBuilder.setLength(argBuilder.length() - 1);
                argBuilder.append('\n');
                break;
              }
              // intentional fallthru
            default:
              argBuilder.append(ch);
          }
          literalArgBuilder.append(ch);
          break;
        case INSIDE_DOUBLE_QUOTES:
          switch (ch) {
            case '"':
              if (prevCh == '\\') {
                argBuilder.setLength(argBuilder.length() - 1);
                argBuilder.append(ch);
              } else {
                state = ParseState.WORD_OUTSIDE_QUOTES;
              }
              break;
            case 'n':
              if (prevCh == '\\') {
                argBuilder.setLength(argBuilder.length() - 1);
                argBuilder.append('\n');
                break;
              }
              // intentional fallthru
            default:
              argBuilder.append(ch);
          }
          literalArgBuilder.append(ch);
          break;
      }
      prevCh = ch;
    }
    if (argBuilder.length() > 0) {
      throw new IllegalStateException(
          "Unexpected trailing characters when parsing command `"
              + command.substring(0, command.length() - 1) // trailing space added above
              + "`: `"
              + argBuilder.toString()
              + "`");
    }
    return args;
  }
}
