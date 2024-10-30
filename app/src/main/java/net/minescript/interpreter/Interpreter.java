// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.interpreter;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.StreamSupport;
import net.minescript.common.Numbers;

public class Interpreter {

  private Context globals = Context.createGlobals();

  public Interpreter parse(JsonElement element) {
    JsonAstParser.parseGlobals(element, globals);
    return this;
  }

  public Interpreter exec() {
    globals.execGlobalStatements();
    return this;
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
            Statement body = parseStatementBlock(getBody(element));
            globals.functions().put(identifier.name, new FunctionDef(identifier, args, body));
            return;
          }

        default:
          globals.addGlobalStatement(parseStatements(element));
          return;
      }
    }

    public static Statement parseStatementBlock(JsonArray block) {
      if (block.size() == 1) {
        return parseStatements(block.get(0));
      } else {
        return new StatementBlock(
            StreamSupport.stream(block.spliterator(), false)
                .map(elem -> parseStatements(elem))
                .collect(toList()));
      }
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

        case "Global":
          {
            return new GlobalVarDecl(
                StreamSupport.stream(
                        getAttr(element, "names").getAsJsonArray().spliterator(), false)
                    .map(name -> new Identifier(name.getAsString()))
                    .collect(toList()));
          }

        case "Expr":
          return parseExpression(getAttr(element, "value"));

        case "If":
          return new IfBlock(
              parseExpression(getAttr(element, "test")),
              parseStatementBlock(getBody(element)),
              parseStatementBlock(getAttr(element, "orelse").getAsJsonArray()));

        case "Return":
          {
            var returnValue = getAttr(element, "value");
            if (returnValue.isJsonNull()) {
              // No return value. This differs from `return None` in terms of the AST, but the two
              // evaluate the same.
              return new ReturnStatement(null);
            } else {
              return new ReturnStatement(parseExpression(returnValue));
            }
          }
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

    private enum ParseContext {
      // Default context. Attributes parsed within NONE context are field accesses: foo.member
      NONE,

      // Attributes parsed within CALLER context are method bindings: foo.method(...)
      CALLER,
    }

    public static Expression parseExpression(JsonElement element) {
      return parseExpressionWithContext(element, ParseContext.NONE);
    }

    private static Expression parseExpressionWithContext(
        JsonElement element, ParseContext parseContext) {
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
              parseExpressionWithContext(getAttr(element, "func"), ParseContext.CALLER),
              StreamSupport.stream(getAttr(element, "args").getAsJsonArray().spliterator(), false)
                  .map(elem -> parseExpression(elem))
                  .collect(toList()));
        case "Attribute":
          {
            var object = parseExpression(getAttr(element, "value"));
            var attr = new Identifier(getAttr(element, "attr").getAsString());
            if (parseContext == ParseContext.CALLER) {
              return new BoundMethod(object, attr);
            } else {
              return new FieldAccess(object, attr);
            }
          }
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
      if (element.isJsonPrimitive()) {
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
      } else if (element.isJsonNull()) {
        return new ConstantExpression(null);
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
      throw new UnsupportedOperationException(
          "Execution of statement type not implemented: " + getClass().getSimpleName());
    }
  }

  public static boolean convertToBool(Object value) {
    if (value == null) {
      return false;
    } else if (value instanceof Boolean bool) {
      return bool;
    } else if (value instanceof String string) {
      return Boolean.parseBoolean(string);
    } else if (value instanceof Collection collection) {
      return !collection.isEmpty();
    } else if (value instanceof Number number) {
      return number.doubleValue() != 0.;
    } else {
      return true;
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
      return callContext.returnValue();
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

  public record IfBlock(Expression condition, Statement thenBody, Statement elseBody)
      implements Statement {
    @Override
    public void exec(Context context) {
      if (context.returned()) {
        return;
      }
      if (convertToBool(condition.eval(context))) {
        thenBody.exec(context);
      } else {
        elseBody.exec(context);
      }
    }
  }

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
      if (context.returned()) {
        return;
      }
      eval(context);
    }

    default Object eval(Context context) {
      throw new UnsupportedOperationException(
          String.format(
              "Eval for expression %s not implemented: %s", getClass().getSimpleName(), this));
    }
  }

  public record StatementBlock(List<Statement> statements) implements Statement {
    @Override
    public void exec(Context context) {
      for (var statement : statements) {
        if (context.returned()) {
          break;
        }
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

  public record GlobalVarDecl(List<Identifier> globalVars) implements Statement {
    @Override
    public void exec(Context context) {
      for (var identifier : globalVars) {
        context.declareGlobalVar(identifier.name());
      }
    }

    @Override
    public String toString() {
      return String.format(
          "global %s;", globalVars.stream().map(Object::toString).collect(joining(", ")));
    }
  }

  public record ReturnStatement(Expression returnValue) implements Statement {
    @Override
    public void exec(Context context) {
      context.returnWithValue(returnValue == null ? null : returnValue.eval(context));
    }

    @Override
    public String toString() {
      if (returnValue == null) {
        return "return;";
      } else {
        return String.format("return %s;", returnValue);
      }
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
      throw new UnsupportedOperationException("Unary op not implemented");
    }
  }

  public record ConstantExpression(Object value) implements Expression {
    @Override
    public Object eval(Context context) {
      return value;
    }

    @Override
    public String toString() {
      if (value == null) {
        return "null";
      } else if (value instanceof String) {
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
      throw new UnsupportedOperationException(
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
              "Eval for ArrayIndex expression not supported for types: %s[%s] (evaluated as:"
                  + " %s[%s])",
              array, index, arrayValue, indexValue));
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
        return callFunction(methodId, context);
      } else if (method instanceof BoundMethod boundMethod
          && boundMethod.object() instanceof Identifier objId) {
        return callBoundMethod(objId.name(), boundMethod.method().name(), context);
      }
      throw new IllegalArgumentException(
          String.format("Function `%s` not defined: %s", method, this));
    }

    private Object callFunction(Identifier methodId, Context context) {
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
            return convertToBool(params.get(0).eval(context));
          }
        case "print":
          System.out.println(
              params.stream().map(p -> Objects.toString(p.eval(context))).collect(joining(" ")));
          return null;
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
      throw new IllegalArgumentException(
          String.format("Function `%s` not defined: %s", methodId, this));
    }

    private Object callBoundMethod(String objectName, String methodName, Context context) {
      switch (objectName) {
        case "math":
          if (methodName.equals("sqrt")) {
            expectNumParams(1);
            var num = (Number) params.get(0).eval(context);
            return Math.sqrt(num.doubleValue());
          }
          break;
        case "Math":
          {
            try {
              // TODO(maxuser): cache class lookups
              Class<?> clss = Class.forName("java.lang.Math");
              // TODO(maxuser): cache method lookups
              for (Method m : clss.getMethods()) {
                // TODO(maxuser): also check param number and types
                if (m.getName().equals(methodName)) {
                  return m.invoke(
                      null, // static method ignores obj param
                      params.stream().map(p -> p.eval(context)).toArray(Object[]::new));
                }
              }
              throw new IllegalArgumentException(
                  String.format("Method not found: %s %s\n", clss.getName(), methodName));
            } catch (ClassNotFoundException
                | IllegalAccessException
                | InvocationTargetException e) {
              throw new RuntimeException(e);
            }
          }
      }
      throw new IllegalArgumentException(
          String.format("Function not defined: %s.%s", objectName, methodName));
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

  public record BoundMethod(Expression object, Identifier method) implements Expression {
    @Override
    public Object eval(Context context) {
      throw new UnsupportedOperationException(
          String.format("Bound methods can be called but not evaluated: %s.%s", object, method));
    }

    @Override
    public String toString() {
      return String.format("%s.%s", object, method);
    }
  }

  public record FieldAccess(Expression object, Identifier field) implements Expression {
    @Override
    public String toString() {
      return String.format("%s.%s", object, field);
    }
  }

  public static class Context {
    private static final Object NOT_FOUND = new Object();

    private final Context globals;
    private final List<Statement> globalStatements;
    private Set<String> globalVarNames = null;
    private Map<String, FunctionDef> functions = null;
    private final Map<String, Object> vars = new HashMap<>();
    private Object returnValue;
    private boolean returned = false;

    private Context() {
      globals = this;
      globalStatements = new ArrayList<>();
    }

    private Context(Context globals) {
      this.globals = globals;
      this.globalStatements = null; // Defined only for global context.
    }

    public static Context createGlobals() {
      return new Context();
    }

    public Context createLocalContext() {
      return new Context(globals);
    }

    public void declareGlobalVar(String name) {
      if (globalVarNames == null) {
        globalVarNames = new HashSet<>();
      }
      globalVarNames.add(name);
    }

    public void addGlobalStatement(Statement statement) {
      if (this != globals) {
        throw new IllegalStateException("Cannot add global statements in local context");
      }
      globalStatements.add(statement);
    }

    public void execGlobalStatements() {
      if (this != globals) {
        throw new IllegalStateException("Cannot execute global statements in local context");
      }
      for (var statement : globalStatements) {
        statement.exec(globals);
      }
    }

    public Map<String, FunctionDef> functions() {
      if (functions == null) {
        functions = new HashMap<>();
      }
      return functions;
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
      if (this != globals && globalVarNames != null && globalVarNames.contains(name)) {
        globals.vars.put(name, value);
      } else {
        vars.put(name, value);
      }
    }

    public void setVariable(Identifier id, Object value) {
      setVariable(id.name(), value);
    }

    public Object getVariable(Identifier id) {
      String name = id.name();
      if (this != globals && globalVarNames != null && globalVarNames.contains(name)) {
        return globals.getVariable(id);
      }
      var value = vars.getOrDefault(id.name(), NOT_FOUND);
      if (value != NOT_FOUND) {
        return value;
      } else if (this != globals) {
        return globals.getVariable(id);
      } else {
        throw new IllegalArgumentException("Variable not found: " + id.name());
      }
    }

    public void returnWithValue(Object returnValue) {
      if (this == globals) {
        throw new IllegalStateException("'return' outside function");
      }
      this.returnValue = returnValue;
      this.returned = true;
    }

    public boolean returned() {
      return returned;
    }

    public Object returnValue() {
      return returnValue;
    }
  }
}
