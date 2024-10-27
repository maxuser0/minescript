// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.interpreter;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import net.minescript.common.Numbers;

public class Interpreter {

  public static class JsonAstParser {

    public static Statement parseStatements(JsonElement element) {
      String type = getType(element);
      switch (type) {
        case "Module":
          {
            return parseStatements(getBody(element).get(0));
          }
        case "FunctionDef":
          {
            var body = getBody(element);
            if (body.size() == 1) {
              return parseStatements(body.get(0));
            } else {
              return new StatementBlock(
                  StreamSupport.stream(body.spliterator(), false)
                      .map(elem -> parseStatements(elem))
                      .collect(toList()));
            }
          }
        case "Assign":
          {
            Expression lhs = parseExpression(getTargets(element).get(0));
            if (lhs instanceof Identifier || lhs instanceof ArrayIndex) {
              return new Assignment(lhs, parseExpression(getAttr(element, "value")));
            } else {
              throw new IllegalArgumentException(
                  String.format(
                      "Unsupported expression type for lhs of assignment: `%s` (%s)",
                      lhs, lhs.getClass().getSimpleName()));
            }
          }
        case "Return":
          return new ReturnStatement(parseExpression(getAttr(element, "value")));
      }
      throw new IllegalArgumentException("Unknown statement type: " + element.toString());
    }

    public static Expression parseExpression(JsonElement element) {
      String type = getType(element);
      switch (type) {
        case "BinOp":
          return new BinaryOp(
              parseExpression(getAttr(element, "left")),
              parseBinaryOp(getType(getAttr(element, "op"))),
              parseExpression(getAttr(element, "right")));
        case "Name":
          return getId(element);
        case "Constant":
          return parseConstant(getAttr(element, "value"));
        case "Call":
          return new MethodCall(
              parseExpression(getAttr(element, "func")),
              StreamSupport.stream(getAttr(element, "args").getAsJsonArray().spliterator(), false)
                  .map(elem -> parseExpression(elem))
                  .collect(toList()));
        case "Attribute":
          // TODO(maxuser): Don't assume `value.attr` can be concatenated into a single identifier.
          return new Identifier(
              String.format(
                  "%s.%s",
                  getAttr(getAttr(element, "value"), "id").getAsString(),
                  getAttr(element, "attr").getAsString()));
        case "Subscript":
          return new ArrayIndex(
              parseExpression(getAttr(element, "value")),
              parseExpression(getAttr(element, "slice")));
      }
      throw new IllegalArgumentException("Unknown expression type: " + element.toString());
    }

    private static BinaryOp.Op parseBinaryOp(String opName) {
      switch (opName) {
        case "Add":
          return BinaryOp.Op.ADD;
        case "Sub":
          return BinaryOp.Op.SUB;
        case "Mult":
          return BinaryOp.Op.MUL;
        default:
          throw new IllegalArgumentException("Unknown binary op: " + opName);
      }
    }

    private static Expression parseConstant(JsonElement element) {
      var primitive = element.getAsJsonPrimitive();
      if (primitive.isNumber()) {
        var number = primitive.getAsNumber();
        int n = number.intValue();
        double d = number.doubleValue();
        if (n == d) {
          return new ConstantExpression(number.intValue());
        } else {
          return new ConstantExpression(number.doubleValue());
        }
      } else if (primitive.isBoolean()) {
        return new ConstantExpression(primitive.getAsBoolean());
      } else if (primitive.isString()) {
        return new ConstantExpression(primitive.getAsString());
      }
      throw new IllegalArgumentException(String.format("Unsupported primitive type: %s", element));
    }

    private static String getType(JsonElement element) {
      return element.getAsJsonObject().get("type").getAsString();
    }

    private static JsonArray getTargets(JsonElement element) {
      return element.getAsJsonObject().get("targets").getAsJsonArray();
    }

    private static JsonElement getAttr(JsonElement element, String attr) {
      return element.getAsJsonObject().get(attr);
    }

    private static Identifier getId(JsonElement element) {
      return new Identifier(element.getAsJsonObject().get("id").getAsString());
    }

    private static JsonArray getBody(JsonElement element) {
      return element.getAsJsonObject().get("body").getAsJsonArray();
    }
  }

  public interface Statement {
    default void exec(Context context) {
      throw new IllegalArgumentException(
          "Execution of statement type not implemented: " + getClass().getSimpleName());
    }
  }

  public record IfBlock(Expression ifCondition, Statement ifBody, Statement elseBody)
      implements Statement {}

  public record WhileBlock(Expression condition, Statement body) implements Statement {}

  public record ForBlock(Expression vars, Expression iterator, Statement body)
      implements Statement {}

  public record Identifier(String name) implements Expression {
    @Override
    public Object eval(Context context) {
      return context.getVariable(this);
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public record ExceptionHandler(Identifier exceptionType, Identifier exceptionVariable) {}

  public record TryBlock(
      Statement tryBody, List<ExceptionHandler> exceptionHandlers, Statement finallyBlock)
      implements Statement {}

  public record ResourceBlock(Expression resource, Statement body) implements Statement {}

  public interface Expression extends Statement {
    @Override
    default void exec(Context context) {
      eval(context);
    }

    default Object eval(Context context) {
      throw new IllegalArgumentException(
          String.format(
              "Eval for expression %s not implemented: %s", getClass().getSimpleName(), this));
    }
  }

  public record StatementBlock(List<Statement> statements) implements Statement {
    @Override
    public void exec(Context context) {
      for (var statement : statements) {
        statement.exec(context);
      }
    }

    @Override
    public String toString() {
      return statements.stream()
          .map(Object::toString)
          .map(s -> "  " + s)
          .collect(joining("\n", "{\n", "\n}"));
    }
  }

  public record Assignment(Expression lhs, Expression rhs) implements Statement {
    @Override
    public void exec(Context context) {
      Object rhsValue = rhs.eval(context);
      if (lhs instanceof Identifier lhsId) {
        context.setVariable(lhsId, rhsValue);
        return;
      } else if (lhs instanceof ArrayIndex lhsArrayIndex) {
        var array = lhsArrayIndex.array().eval(context);
        var index = lhsArrayIndex.index().eval(context);
        if (array instanceof Object[] objectArray) {
          objectArray[((Number) index).intValue()] = rhsValue;
          return;
        } else if (array instanceof int[] intArray) {
          intArray[((Number) index).intValue()] = (Integer) rhsValue;
          return;
        } else if (array instanceof long[] longArray) {
          longArray[((Number) index).intValue()] = (Long) rhsValue;
          return;
        } else if (array instanceof float[] floatArray) {
          floatArray[((Number) index).intValue()] = (Float) rhsValue;
          return;
        } else if (array instanceof double[] doubleArray) {
          doubleArray[((Number) index).intValue()] = (Double) rhsValue;
          return;
        } else if (array instanceof List list) {
          list.set(((Number) index).intValue(), rhsValue);
          return;
        } else if (array instanceof Map map) {
          map.put(index, rhsValue);
          return;
        }
      } else {
        throw new IllegalArgumentException(
            String.format(
                "Unsupported expression type for lhs of assignment: `%s` (%s)",
                lhs, lhs.getClass().getSimpleName()));
      }
    }

    @Override
    public String toString() {
      if (lhs instanceof Identifier lhsId) {
        return String.format("%s = %s;", lhsId.name(), rhs);
      } else if (lhs instanceof ArrayIndex arrayIndex) {
        return String.format("%s[%s] = %s;", arrayIndex.array(), arrayIndex.index(), rhs);
      } else {
        return String.format("%s = %s;", lhs, rhs);
      }
    }
  }

  public record AugmentedAssignment(Identifier lhs, Op op, Expression rhs) implements Statement {
    public enum Op {
      ADD_EQ("+="),
      SUB_EQ("-="),
      MUL_EQ("*="),
      DIV_EQ("/="),
      MOD_EQ("%=");

      private final String symbol;

      Op(String symbol) {
        this.symbol = symbol;
      }

      public String symbol() {
        return symbol;
      }
    }
  }

  public record ReturnStatement(Expression returnValue) implements Statement {
    @Override
    public void exec(Context context) {
      context.setOutput(returnValue.eval(context));
      return;
    }

    @Override
    public String toString() {
      return String.format("return %s;", returnValue);
    }
  }

  public record UnaryOp(Op op, Expression operand) implements Expression {
    public enum Op {
      NEGATIVE("-"),
      NOT("!");

      private final String symbol;

      Op(String symbol) {
        this.symbol = symbol;
      }

      public String symbol() {
        return symbol;
      }
    }

    @Override
    public Object eval(Context context) {
      switch (op) {
        case NEGATIVE:
          return Numbers.negate((Number) operand.eval(context));
        case NOT:
          return !(Boolean) operand.eval(context);
      }
      throw new IllegalArgumentException("Unary op not implemented");
    }
  }

  public record ConstantExpression(Object value) implements Expression {
    @Override
    public Object eval(Context context) {
      return value;
    }

    @Override
    public String toString() {
      if (value instanceof String) {
        return String.format("\"%s\"", value);
      } else {
        return value.toString();
      }
    }
  }

  public record BinaryOp(Expression lhs, Op op, Expression rhs) implements Expression {
    public enum Op {
      ADD("+"),
      SUB("-"),
      MUL("*"),
      DIV("/"),
      MOD("%"),
      EQ("=="),
      LESS("<"),
      LT_EQ("<="),
      GT(">"),
      GT_EQ(">="),
      NOT_EQ("!="),
      AND("&&"),
      OR("||");

      private final String symbol;

      Op(String symbol) {
        this.symbol = symbol;
      }

      public String symbol() {
        return symbol;
      }
    }

    @Override
    public Object eval(Context context) {
      var lhsValue = lhs.eval(context);
      var rhsValue = (op == Op.OR || op == Op.AND) ? null : rhs.eval(context);
      switch (op) {
        case EQ:
          return lhsValue.equals(rhsValue);
        case ADD:
          if (lhsValue instanceof Number lhsNum && rhsValue instanceof Number rhsNum) {
            return Numbers.add(lhsNum, rhsNum);
          } else if (lhsValue instanceof String && rhsValue instanceof String) {
            return lhsValue.toString() + rhsValue.toString();
          }
          break;
        case SUB:
          return Numbers.subtract((Number) lhsValue, (Number) rhsValue);
        case MUL:
          return Numbers.multiply((Number) lhsValue, (Number) rhsValue);
          // TODO(maxuser): impl ops...
      }
      throw new IllegalArgumentException(
          String.format(
              "Binary op not implemented for types `%s %s %s`: %s",
              lhsValue.getClass().getSimpleName(),
              op.symbol(),
              rhsValue.getClass().getSimpleName(),
              this));
    }

    @Override
    public String toString() {
      // TODO(maxuser): Fix output for proper order of operations that may need parentheses.
      // E.g. `(1 + 2) * 3` evaluates correctly but gets output to String as `1 + 2 * 3`.
      return String.format("%s %s %s", lhs, op.symbol(), rhs);
    }
  }

  public record ArrayIndex(Expression array, Expression index) implements Expression {
    @Override
    public Object eval(Context context) {
      var arrayValue = array.eval(context);
      var indexValue = index.eval(context);
      if (arrayValue == null || indexValue == null) {
        throw new NullPointerException(
            String.format("%s=%s, %s=%s in %s", array, arrayValue, index, indexValue, this));
      }

      if (arrayValue instanceof Object[] objectArray) {
        return objectArray[((Number) indexValue).intValue()];
      } else if (arrayValue instanceof int[] intArray) {
        return intArray[((Number) indexValue).intValue()];
      } else if (arrayValue instanceof long[] longArray) {
        return longArray[((Number) indexValue).intValue()];
      } else if (arrayValue instanceof float[] floatArray) {
        return floatArray[((Number) indexValue).intValue()];
      } else if (arrayValue instanceof double[] doubleArray) {
        return doubleArray[((Number) indexValue).intValue()];
      } else if (arrayValue instanceof List list) {
        return list.get(((Number) indexValue).intValue());
      } else if (arrayValue instanceof Map map) {
        return map.get(indexValue);
      }

      throw new IllegalArgumentException(
          String.format(
              "Eval for ArrayIndex expression not implemented for types: %s[%s]",
              array.getClass().getSimpleName(), index.getClass().getSimpleName()));
    }

    @Override
    public String toString() {
      return String.format("%s[%s]", array, index);
    }
  }

  public record CtorCall(Identifier classId, List<Expression> params) {}

  public record MethodCall(Expression method, List<Expression> params) implements Expression {
    @Override
    public Object eval(Context context) {
      if (method instanceof Identifier methodId) {
        switch (methodId.name()) {
          case "int":
            {
              expectNumParams(1);
              var num = (Number) params.get(0).eval(context);
              return num.intValue();
            }
          case "float":
            {
              expectNumParams(1);
              var num = (Number) params.get(0).eval(context);
              return num.doubleValue();
            }
          case "str":
            {
              expectNumParams(1);
              return params.get(0).eval(context).toString();
            }
          case "bool":
            {
              expectNumParams(1);
              var num = (Number) params.get(0).eval(context);
              return num.doubleValue() != 0.;
            }
          case "math.sqrt":
            {
              expectNumParams(1);
              var num = (Number) params.get(0).eval(context);
              return Math.sqrt(num.doubleValue());
            }
        }
      }
      throw new IllegalArgumentException(
          String.format("Function `%s` not implemented: %s", method, this));
    }

    private void expectNumParams(int n) {
      if (params.size() != n) {
        throw new IllegalArgumentException(
            String.format(
                "Expected %d params but got %d for function: %s", n, params.size(), this));
      }
    }

    @Override
    public String toString() {
      return String.format(
          "%s(%s)", method, params.stream().map(Object::toString).collect(joining(", ")));
    }
  }

  public record FieldAccess(Expression target, Identifier fieldId) {}

  public static class Context {
    private Map<String, Object> vars = new HashMap<>();
    private Object output;

    public void setVariable(String name, Object value) {
      vars.put(name, value);
    }

    public void setVariable(Identifier id, Object value) {
      vars.put(id.name(), value);
    }

    public Object getVariable(Identifier id) {
      return vars.get(id.name());
    }

    public void setOutput(Object output) {
      this.output = output;
    }

    public Object output() {
      return output;
    }
  }
}
