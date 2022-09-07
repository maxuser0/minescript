package net.minescript.common;

import java.util.ArrayList;
import java.util.List;

public class CommandSyntax {

  private static final String[] EMPTY_STRING_ARRAY = {};

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

  public static String[] parseCommand(String command) {
    command += " "; // add space for simpler termination of parsing
    List<String> argv = new ArrayList<>();
    var state = ParseState.START;
    var buffer = new StringBuilder();
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
              buffer.append(ch);
          }
          break;
        case WORD_OUTSIDE_QUOTES:
          switch (ch) {
            case '\'':
              if (prevCh == '\\') {
                buffer.setLength(buffer.length() - 1);
                buffer.append(ch);
              } else {
                state = ParseState.INSIDE_SINGLE_QUOTES;
              }
              break;
            case '"':
              if (prevCh == '\\') {
                buffer.setLength(buffer.length() - 1);
                buffer.append(ch);
              } else {
                state = ParseState.INSIDE_DOUBLE_QUOTES;
              }
              break;
            case ' ':
              argv.add(buffer.toString());
              buffer.setLength(0);
              state = ParseState.SPACES_OUTSIDE_QUOTES;
              break;
            default:
              buffer.append(ch);
          }
          break;
        case SPACES_OUTSIDE_QUOTES:
          switch (ch) {
            case '\'':
              state = ParseState.INSIDE_SINGLE_QUOTES;
              break;
            case '"':
              state = ParseState.INSIDE_DOUBLE_QUOTES;
              break;
            case ' ':
              break;
            default:
              buffer.append(ch);
              state = ParseState.WORD_OUTSIDE_QUOTES;
          }
          break;
        case INSIDE_SINGLE_QUOTES:
          switch (ch) {
            case '\'':
              if (prevCh == '\\') {
                buffer.setLength(buffer.length() - 1);
                buffer.append(ch);
              } else {
                state = ParseState.WORD_OUTSIDE_QUOTES;
              }
              break;
            case 'n':
              if (prevCh == '\\') {
                buffer.setLength(buffer.length() - 1);
                buffer.append('\n');
                break;
              }
              // intentional fallthru
            default:
              buffer.append(ch);
          }
          break;
        case INSIDE_DOUBLE_QUOTES:
          switch (ch) {
            case '"':
              if (prevCh == '\\') {
                buffer.setLength(buffer.length() - 1);
                buffer.append(ch);
              } else {
                state = ParseState.WORD_OUTSIDE_QUOTES;
              }
              break;
            case 'n':
              if (prevCh == '\\') {
                buffer.setLength(buffer.length() - 1);
                buffer.append('\n');
                break;
              }
              // intentional fallthru
            default:
              buffer.append(ch);
          }
      }
      prevCh = ch;
    }
    if (buffer.length() > 0) {
      argv.add(buffer.toString());
    }
    return argv.toArray(EMPTY_STRING_ARRAY);
  }
}
