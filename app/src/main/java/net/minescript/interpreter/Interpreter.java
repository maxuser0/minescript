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

  private Context globals = Context.createGlobals();

  public Interpreter parse(JsonElement element) {
    JsonAstParser.parseGlobals(element, globals);
    return this;
  }

  public FunctionDef getFunction() {
    return globals.getFunction();
  }

  public FunctionDef getFunction(String name) {
    return globals.getFunction(name);
  }

  public Object invoke(FunctionDef function, Object... args) {
    return function.invoke(globals, args);
  }

  public static class JsonAstParser {

    public static void parseGlobals(JsonElement element, Context globals) {
      String type = getType(element);
      switch (type) {
        case "Module":
          {
            for (var global : getBody(element)) {
              parseGlobals(global, globals);
            }
            return;
          }

        case "FunctionDef":
          {
            var identifier = new Identifier(getAttr(element, "name").getAsString());
            List<FunctionArg> args =
                parseFunctionArgs(
                    getAttr(getAttr(element, "args").getAsJsonObject(), "args").getAsJsonArray());
            final Statement body;
            var bodyArray = getBody(element);
            if (bodyArray.size() == 1) {
              body = parseStatements(bodyArray.get(0));
            } else {
              body =
                  new StatementBlock(
                      StreamSupport.stream(bodyArray.spliterator(), false)
                          .map(elem -> parseStatements(elem))
                          .collect(toList()));
            }
            globals.functions().put(identifier.name, new FunctionDef(identifier, args, body));
            return;
          }
      }
      throw new IllegalArgumentException("Unknown global element: " + element.toString());
    }

    public static Statement parseStatements(JsonElement element) {
      String type = getType(element);
      switch (type) {
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

    private static List<FunctionArg> parseFunctionArgs(JsonArray args) {
      return StreamSupport.stream(args.spliterator(), false)
          .map(
              arg ->
                  new FunctionArg(
                      new Identifier(getAttr(arg.getAsJsonObject(), "arg").getAsString())))
          .collect(toList());
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

  public record FunctionDef(Identifier identifier, List<FunctionArg> args, Statement body)
      implements Statement {
    /**
     * Executes function body.
     *
     * <p>The caller is responsible for binding function args in {@code Context}.
     */
    @Override
    public void exec(Context context) {
      body.exec(context);
    }

    public Object invoke(Context context, Object... argValues) {
      if (args.size() != argValues.length) {
        throw new IllegalArgumentException(
            String.format(
                "Invoking function `%s` with %d args but %d required",
                identifier, argValues.length, args.size()));
      }

      var callContext = context.createLocalContext();
      for (int i = 0; i < args.size(); ++i) {
        var arg = args.get(i);
        var argValue = argValues[i];
        callContext.setVariable(arg.identifier().name(), argValue);
      }
      exec(callContext);
      return callContext.output();
    }

    @Override
    public String toString() {
      String bodyString = body.toString();
      if (!bodyString.startsWith("{")) {
        bodyString = "{\n  " + bodyString.replaceAll("\n", "\n  ") + "\n}";
      }
      return String.format(
          "function %s(%s)\n%s",
          identifier.name(),
          args.stream().map(a -> a.identifier().name()).collect(joining(", ")),
          bodyString);
    }
  }

  public record FunctionArg(Identifier identifier) {}

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
              var value = params.get(0).eval(context);
              if (value instanceof String string) {
                return Integer.parseInt(string);
              } else {
                return ((Number) value).intValue();
              }
            }
          case "float":
            {
              expectNumParams(1);
              var value = params.get(0).eval(context);
              if (value instanceof String string) {
                return Double.parseDouble(string);
              } else {
                return ((Number) value).doubleValue();
              }
            }
          case "str":
            {
              expectNumParams(1);
              return params.get(0).eval(context).toString();
            }
          case "bool":
            {
              expectNumParams(1);
              var value = params.get(0).eval(context);
              if (value instanceof String string) {
                return Boolean.parseBoolean(string);
              } else {
                return ((Number) value).doubleValue() != 0.;
              }
            }
          case "math.sqrt":
            {
              expectNumParams(1);
              var num = (Number) params.get(0).eval(context);
              return Math.sqrt(num.doubleValue());
            }
          default:
            {
              FunctionDef func = context.getFunction(methodId.name());
              if (func != null) {
                expectNumParams(func.args.size());
                return func.invoke(
                    context, params.stream().map(p -> p.eval(context)).toArray(Object[]::new));
              }
            }
        }
      }
      throw new IllegalArgumentException(
          String.format("Function `%s` not defined: %s", method, this));
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
    private static final Object NOT_FOUND = new Object();

    private final Context globals;
    private Map<String, FunctionDef> functions = null;
    private final Map<String, Object> vars = new HashMap<>();
    private Object output;

    private Context() {
      globals = this;
    }

    private Context(Context globals) {
      this.globals = globals;
    }

    public static Context createGlobals() {
      return new Context();
    }

    public Context createLocalContext() {
      return new Context(globals);
    }

    public Map<String, FunctionDef> functions() {
      if (functions == null) {
        functions = new HashMap<>();
      }
      return functions;
    }

    public FunctionDef getFunction() {
      var funcs = functions == null ? globals.functions : functions;

      if (funcs.size() != 1) {
        throw new IllegalArgumentException(
            String.format(
                "getFunction() requires exactly one function defined but there are %d%s",
                funcs.size(),
                funcs.isEmpty() ? "" : funcs.keySet().stream().collect(joining(", ", " [", "]"))));
      }
      return funcs.entrySet().iterator().next().getValue();
    }

    public FunctionDef getFunction(String name) {
      var funcs = functions == null ? globals.functions : functions;

      var func = funcs.get(name);
      if (func == null && this != globals) {
        return globals.getFunction(name);
      } else {
        return func;
      }
    }

    public void setVariable(String name, Object value) {
      vars.put(name, value);
    }

    public void setVariable(Identifier id, Object value) {
      vars.put(id.name(), value);
    }

    public Object getVariable(Identifier id) {
      String name = id.name();
      var value = vars.getOrDefault(id.name(), NOT_FOUND);
      if (value != NOT_FOUND) {
        return value;
      } else if (this != globals) {
        return globals.getVariable(id);
      } else {
        throw new IllegalArgumentException("Variable not found: " + id.name());
      }
    }

    public void setOutput(Object output) {
      this.output = output;
    }

    public Object output() {
      return output;
    }
  }
}
