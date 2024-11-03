// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.interpreter;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.StreamSupport;
import net.minescript.common.Numbers;

public class Interpreter {

  private Context globals = Context.createGlobals();

  public Interpreter parse(JsonElement element) {
    var parser = new JsonAstParser();
    parser.parseGlobals(element, globals);
    return this;
  }

  public Interpreter exec() {
    globals.execGlobalStatements();
    return this;
  }

  public FunctionDef getFunction(String name) {
    return globals.getBoundFunction(name).function();
  }

  public Object invoke(FunctionDef function, Object... args) {
    return function.invoke(globals, globals, args);
  }

  public static class JsonAstParser {

    private final Deque<LexicalScope> lexicalScopes = new ArrayDeque<>();

    public JsonAstParser() {
      // Lexical scope for global scope, reused across calls to parseGlobals(). To get a new, empty
      // stack of lexical scopes, instantiate a new JsonAstParser.
      lexicalScopes.push(new LexicalScope());
    }

    private static class LexicalScope {
      public final Map<String, Class<?>> classAliases = new HashMap<>();
    }

    public void parseGlobals(JsonElement element, Context globals) {
      String type = getType(element);
      switch (type) {
        case "Module":
          {
            for (var global : getBody(element)) {
              parseGlobals(global, globals);
            }
            return;
          }

        default:
          globals.addGlobalStatement(parseStatements(element));
          return;
      }
    }

    public Statement parseStatementBlock(JsonArray block) {
      if (block.size() == 1) {
        return parseStatements(block.get(0));
      } else {
        return new StatementBlock(
            StreamSupport.stream(block.spliterator(), false)
                .map(elem -> parseStatements(elem))
                .collect(toList()));
      }
    }

    private FunctionDef parseFunctionDef(JsonElement element) {
      lexicalScopes.push(new LexicalScope());
      final FunctionDef func;
      try {
        var identifier = new Identifier(getAttr(element, "name").getAsString());
        List<FunctionArg> args =
            parseFunctionArgs(
                getAttr(getAttr(element, "args").getAsJsonObject(), "args").getAsJsonArray());
        Statement body = parseStatementBlock(getBody(element));
        func = new FunctionDef(identifier, args, body);
      } finally {
        lexicalScopes.pop();
      }
      return func;
    }

    public Statement parseStatements(JsonElement element) {
      String type = getType(element);
      switch (type) {
        case "FunctionDef":
          return parseFunctionDef(element);

        case "Assign":
          {
            Expression lhs = parseExpression(getTargets(element).get(0));
            Expression rhs = parseExpression(getAttr(element, "value"));
            if (lhs instanceof Identifier lhsId) {
              if (rhs instanceof JavaClassId classId) {
                var alias = new ClassAliasAssignment(lhsId, classId.clss());
                var oldClass =
                    lexicalScopes.peek().classAliases.put(alias.identifier().name(), alias.clss());
                if (oldClass != null) {
                  throw new IllegalArgumentException(
                      String.format(
                          "Class alias `%s` multiply defined; first defined as %s and then as %s",
                          oldClass.getName(), alias.clss()));
                }
                return alias;
              } else {
                var oldClass = lexicalScopes.peek().classAliases.get(lhsId.name());
                if (oldClass != null) {
                  throw new IllegalArgumentException(
                      String.format(
                          "Class alias `%s` multiply defined; first defined as class %s and then as"
                              + " non-class %s",
                          oldClass.getName(), rhs));
                }
                return new Assignment(lhs, rhs);
              }
            } else if (lhs instanceof JavaClassId lhsClassId) {
              throw new IllegalArgumentException(
                  "JavaClass identifier cannot be reassigned: " + lhsClassId.toString());
            } else if (lhs instanceof ArrayIndex) {
              return new Assignment(lhs, rhs);
            } else {
              throw new IllegalArgumentException(
                  String.format(
                      "Unsupported expression type for lhs of assignment: `%s` (%s)",
                      lhs, lhs.getClass().getSimpleName()));
            }
          }

        case "AugAssign":
          {
            Expression lhs = parseExpression(getTarget(element));
            Expression rhs = parseExpression(getAttr(element, "value"));
            String opName = getType(getAttr(element, "op"));
            final AugmentedAssignment.Op op;
            switch (opName) {
              case "Add":
                op = AugmentedAssignment.Op.ADD_EQ;
                break;
              case "Sub":
                op = AugmentedAssignment.Op.SUB_EQ;
                break;
              case "Mult":
                op = AugmentedAssignment.Op.MULT_EQ;
                break;
              case "Div":
                op = AugmentedAssignment.Op.DIV_EQ;
                break;
              default:
                throw new IllegalArgumentException(
                    "Unsupported type of augmented assignment: " + opName);
            }
            if (lhs instanceof Identifier lhsId) {
              return new AugmentedAssignment(lhs, op, rhs);
            } else if (lhs instanceof ArrayIndex) {
              return new AugmentedAssignment(lhs, op, rhs);
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

    private List<FunctionArg> parseFunctionArgs(JsonArray args) {
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

    public Expression parseExpression(JsonElement element) {
      return parseExpressionWithContext(element, ParseContext.NONE);
    }

    private Class<?> findClassNameInEnclosingScopes(String className) {
      for (var scope : lexicalScopes) {
        Class<?> clss;
        if ((clss = scope.classAliases.get(className)) != null) {
          return clss;
        }
      }
      return null;
    }

    private Expression parseExpressionWithContext(JsonElement element, ParseContext parseContext) {
      String type = getType(element);
      switch (type) {
        case "BinOp":
          return new BinaryOp(
              parseExpression(getAttr(element, "left")),
              parseBinaryOp(getType(getAttr(element, "op"))),
              parseExpression(getAttr(element, "right")));

        case "Name":
          {
            Class<?> clss;
            Identifier id = getId(element);
            if (id.name().equals("JavaClass")) {
              return new JavaClass();
            } else if ((clss = findClassNameInEnclosingScopes(id.name())) != null) {
              return new JavaClassId(id, clss);
            } else {
              return id;
            }
          }

        case "Constant":
          return parseConstant(getAttr(element, "value"));

        case "Call":
          {
            var func = parseExpressionWithContext(getAttr(element, "func"), ParseContext.CALLER);
            var args = getAttr(element, "args").getAsJsonArray();
            if (func instanceof JavaClass) {
              if (args.size() != 1) {
                throw new IllegalArgumentException(
                    "Expected exactly one argument to JavaClass but got " + args.size());
              }
              var arg = parseExpression(args.get(0));
              if (arg instanceof ConstantExpression constExpr
                  && constExpr.value() instanceof String constString) {
                try {
                  return new JavaClassId(null, Class.forName(constString));
                } catch (ClassNotFoundException e) {
                  throw new IllegalArgumentException(e);
                }
              } else {
                throw new IllegalArgumentException(
                    String.format(
                        "Expected JavaClass argument to be a string literal but got %s (%s)",
                        arg, args.get(0)));
              }
            } else {
              return new MethodCall(
                  func,
                  StreamSupport.stream(args.spliterator(), false)
                      .map(elem -> parseExpression(elem))
                      .collect(toList()));
            }
          }

        case "Attribute":
          {
            var object = parseExpression(getAttr(element, "value"));
            var attr = new Identifier(getAttr(element, "attr").getAsString());
            if (parseContext == ParseContext.CALLER) {
              if (object instanceof JavaClassId classId) {
                return new StaticMethod(classId, attr);
              } else {
                return new BoundMethod(object, attr);
              }
            } else {
              return new FieldAccess(object, attr);
            }
          }

        case "Subscript":
          return new ArrayIndex(
              parseExpression(getAttr(element, "value")),
              parseExpression(getAttr(element, "slice")));

        case "List":
          return new NewList(
              StreamSupport.stream(getAttr(element, "elts").getAsJsonArray().spliterator(), false)
                  .map(elem -> parseExpression(elem))
                  .collect(toList()));
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

    private static JsonObject getTarget(JsonElement element) {
      return element.getAsJsonObject().get("target").getAsJsonObject();
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
    } else if (value instanceof Collection<?> collection) {
      return !collection.isEmpty();
    } else if (value instanceof Number number) {
      return number.doubleValue() != 0.;
    } else {
      return true;
    }
  }

  public record BoundFunction(FunctionDef function, Context enclosingContext) {}

  public record FunctionDef(Identifier identifier, List<FunctionArg> args, Statement body)
      implements Statement {
    /** Adds this function to the specified {@code context}. */
    @Override
    public void exec(Context context) {
      context.setBoundFunction(new BoundFunction(this, context));
    }

    /**
     * Invoke this function.
     *
     * @param callerContext Context from which this function was invoked.
     * @param enclosingContext Context enclosing the definition of this function.
     * @param argValues Values to pass to this function's body.
     * @return Return value from invoking this function.
     */
    public Object invoke(Context callerContext, Context enclosingContext, Object... argValues) {
      if (args.size() != argValues.length) {
        throw new IllegalArgumentException(
            String.format(
                "Invoking function `%s` with %d args but %d required",
                identifier, argValues.length, args.size()));
      }

      var localContext = callerContext.createLocalContext(enclosingContext);
      for (int i = 0; i < args.size(); ++i) {
        var arg = args.get(i);
        var argValue = argValues[i];
        localContext.setVariable(arg.identifier().name(), argValue);
      }
      body.exec(localContext);
      return localContext.returnValue();
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

  public record ClassAliasAssignment(Identifier identifier, Class<?> clss) implements Statement {
    @Override
    public void exec(Context context) {}

    @Override
    public String toString() {
      return String.format("import %s as %s;", clss.getName(), identifier);
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
        if (array.getClass().isArray()) {
          Array.set(array, ((Number) index).intValue(), rhsValue);
          return;
        } else if (array instanceof PyList pyList) {
          pyList.__setitem__(index, rhsValue);
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

  public record AugmentedAssignment(Expression lhs, Op op, Expression rhs) implements Statement {
    public enum Op {
      ADD_EQ("+="),
      SUB_EQ("-="),
      MULT_EQ("*="),
      DIV_EQ("/=");

      private final String symbol;

      Op(String symbol) {
        this.symbol = symbol;
      }

      public String symbol() {
        return symbol;
      }

      public Object apply(Object lhs, Object rhs) {
        switch (this) {
          case ADD_EQ:
            if (lhs instanceof Number lhsNum && rhs instanceof Number rhsNum) {
              return Numbers.add(lhsNum, rhsNum);
            } else if (lhs instanceof String lhsStr && rhs instanceof String rhsStr) {
              return lhsStr + rhsStr;
            }
            if (lhs instanceof PyList pyList) {
              lhs = pyList.getJavaList();
            }
            if (rhs instanceof PyList pyList) {
              rhs = pyList.getJavaList();
            }
            if (lhs instanceof List lhsList && rhs instanceof List rhsList) {
              lhsList.addAll(rhsList);
              return null; // Return value unused because op has already been applied to the list.
            }
            break;

          case SUB_EQ:
            if (lhs instanceof Number lhsNum && rhs instanceof Number rhsNum) {
              return Numbers.add(lhsNum, rhsNum);
            }
            break;

          case MULT_EQ:
            if (lhs instanceof Number lhsNum && rhs instanceof Number rhsNum) {
              return Numbers.multiply(lhsNum, rhsNum);
            }
            break;

          case DIV_EQ:
            if (lhs instanceof Number lhsNum && rhs instanceof Number rhsNum) {
              return Numbers.divide(lhsNum, rhsNum);
            }
            break;
        }
        String lhsType = lhs == null ? "None" : lhs.getClass().getName();
        String rhsType = rhs == null ? "None" : rhs.getClass().getName();
        throw new IllegalArgumentException(
            String.format(
                "unsupported operand type(s) for %s: '%s' and '%s' ('%s %s %s')",
                symbol(), lhsType, rhsType, lhs, symbol(), rhs));
      }
    }

    @Override
    public void exec(Context context) {
      Object rhsValue = rhs.eval(context);
      if (lhs instanceof Identifier lhsId) {
        var oldValue = context.getVariable(lhsId);
        var newValue = op.apply(oldValue, rhsValue);
        if (newValue != null) {
          context.setVariable(lhsId, newValue);
        }
        return;
      } else if (lhs instanceof ArrayIndex lhsArrayIndex) {
        var array = lhsArrayIndex.array().eval(context);
        var index = lhsArrayIndex.index().eval(context);
        if (array.getClass().isArray()) {
          int intKey = ((Number) index).intValue();
          var oldValue = Array.get(array, intKey);
          Array.set(array, intKey, op.apply(oldValue, rhsValue));
          return;
        } else if (array instanceof PyList pyList) {
          var oldValue = pyList.__getitem__(index);
          pyList.__setitem__(index, op.apply(oldValue, rhsValue));
          return;
        } else if (array instanceof List list) {
          int intKey = ((Number) index).intValue();
          var oldValue = list.get(intKey);
          list.set(intKey, op.apply(oldValue, rhsValue));
          return;
        } else if (array instanceof Map map) {
          var oldValue = map.get(index);
          map.put(index, op.apply(oldValue, rhsValue));
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
      return String.format("%s %s %s;", lhs, op.symbol(), rhs);
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

  public static String pyToString(Object value) {
    if (value == null) {
      return "None";
    } else if (value instanceof Object[] array) {
      // TODO(maxuser): Support for primitive array types too.
      return Arrays.stream(array).map(Interpreter::pyToString).collect(joining(", ", "[", "]"));
    } else if (value instanceof List<?> list) {
      return list.stream().map(Interpreter::pyToString).collect(joining(", ", "[", "]"));
    } else if (value instanceof Boolean bool) {
      return bool ? "True" : "False";
    } else if (value instanceof String string) {
      Gson gson =
          new GsonBuilder()
              .setPrettyPrinting() // Optional: for pretty printing
              .disableHtmlEscaping() // Important: to prevent double escaping
              .create();
      return gson.toJson(string);
    } else {
      return value.toString();
    }
  }

  public record NewList(List<Expression> elements) implements Expression {
    @Override
    public Object eval(Context context) {
      return new PyList(elements.stream().map(e -> e.eval(context)).collect(toList()));
    }

    @Override
    public String toString() {
      return elements.stream().map(Interpreter::pyToString).collect(joining(", ", "[", "]"));
    }
  }

  public interface Lengthable {
    int __len__();
  }

  public static class PyList implements Iterable<Object>, Lengthable {
    private final List<Object> list;

    public PyList() {
      list = new ArrayList<>();
    }

    public PyList(List<Object> list) {
      this.list = list;
    }

    protected List<Object> getJavaList() {
      return list;
    }

    @Override
    public boolean equals(Object value) {
      return value instanceof PyList pyList && this.list.equals(pyList.list);
    }

    @Override
    public Iterator<Object> iterator() {
      return list.iterator();
    }

    @Override
    public String toString() {
      return list.stream().map(Interpreter::pyToString).collect(joining(", ", "[", "]"));
    }

    public PyList __add__(Object value) {
      PyList newList = copy();
      if (value instanceof PyList pyList) {
        newList.list.addAll(pyList.list);
      } else if (value instanceof List<?> list) {
        newList.list.addAll(list);
      } else {
        throw new IllegalArgumentException(
            String.format(
                "can only concatenate list (not \"%s\") to list",
                value == null ? "None" : value.getClass().getName()));
      }
      return newList;
    }

    public boolean __contains__(Object key) {
      return list.contains(key);
    }

    public void __delitem__(Object key) {
      remove(key);
    }

    public boolean __eq__(Object value) {
      return this.equals(value);
    }

    public boolean __ne__(Object value) {
      return !this.equals(value);
    }

    public void __iadd__(Object value) {
      list.add(value);
    }

    @Override
    public int __len__() {
      return list.size();
    }

    // TODO(maxuser): Support slice notation.
    public Object __getitem__(Object key) {
      if (key instanceof Integer i) {
        return list.get(i);
      }
      throw new IllegalArgumentException(
          String.format(
              "list indices must be integers or slices, not %s (%s)",
              key.getClass().getName(), key));
    }

    // TODO(maxuser): Support slice notation.
    public Object __setitem__(Object key, Object value) {
      if (key instanceof Integer i) {
        return list.set(i, value);
      }
      throw new IllegalArgumentException(
          String.format(
              "list indices must be integers or slices, not %s (%s)",
              key.getClass().getName(), key));
    }

    public void append(Object object) {
      list.add(object);
    }

    public void clear() {
      list.clear();
    }

    public PyList copy() {
      return new PyList(new ArrayList<>(list));
    }

    public long count(Object value) {
      return list.stream().filter(o -> o.equals(value)).count();
    }

    public void extend(Iterable<?> iterable) {
      for (var value : iterable) {
        list.add(value);
      }
    }

    public int index(Object value) {
      int index = list.indexOf(value);
      if (index == -1) {
        throw new NoSuchElementException(String.format("%s is not in list", value));
      }
      return index;
    }

    public void insert(int index, Object object) {
      list.add(index, object);
    }

    public Object pop() {
      return list.remove(list.size() - 1);
    }

    public Object pop(int index) {
      return list.remove(index);
    }

    public void remove(Object value) {
      pop(index(value));
    }

    public void reverse() {
      Collections.reverse(list);
    }

    public void sort() {
      list.sort(null);
    }
  }

  public record JavaClass() implements Expression {
    @Override
    public Object eval(Context context) {
      throw new UnsupportedOperationException("JavaClass can be called but not evaluated");
    }

    @Override
    public String toString() {
      return "JavaClass";
    }
  }

  public record JavaClassId(Identifier alias, Class<?> clss) implements Expression {
    @Override
    public Object eval(Context context) {
      return clss;
    }

    @Override
    public String toString() {
      if (alias != null) {
        return alias.name();
      } else {
        return String.format("JavaClass(\"%s\")", clss.getName());
      }
    }
  }

  public record MethodCall(Expression method, List<Expression> params) implements Expression {
    @Override
    public Object eval(Context context) {
      if (method instanceof Identifier methodId) {
        return callFunction(methodId, context);
      } else if (method instanceof BoundMethod boundMethod) {
        return callBoundMethod(boundMethod.object(), boundMethod.method().name(), context);
      } else if (method instanceof StaticMethod staticMethod) {
        return callStaticMethod(
            staticMethod.classId().clss(), staticMethod.method().name(), context);
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
            return pyToString(params.get(0).eval(context));
          }
        case "bool":
          {
            expectNumParams(1);
            return convertToBool(params.get(0).eval(context));
          }
        case "len":
          {
            expectNumParams(1);
            var value = params.get(0).eval(context);
            if (value.getClass().isArray()) {
              return Array.getLength(value);
            } else if (value instanceof Lengthable lengthable) {
              return lengthable.__len__();
            } else if (value instanceof Collection<?> collection) {
              return collection.size();
            } else if (value instanceof Map map) {
              return map.size();
            } else if (value instanceof String str) {
              return str.length();
            }
            throw new IllegalArgumentException(
                String.format(
                    "Object of type '%s' has no len(): %s", value.getClass().getName(), this));
          }
        case "print":
          System.out.println(
              params.stream().map(p -> pyToString(p.eval(context))).collect(joining(" ")));
          return null;
        default:
          {
            BoundFunction boundFunction = context.getBoundFunction(methodId.name());
            if (boundFunction != null) {
              var func = boundFunction.function();
              expectNumParams(func.args.size());
              return func.invoke(
                  context,
                  boundFunction.enclosingContext,
                  params.stream().map(p -> p.eval(context)).toArray(Object[]::new));
            }
          }
      }
      throw new IllegalArgumentException(
          String.format("Function `%s` not defined: %s", methodId, this));
    }

    private Object callBoundMethod(Expression object, String methodName, Context context) {
      var objectValue = object.eval(context);
      // TODO(maxuser): cache and filter method lookups by name
      try {
        var clss = objectValue.getClass();
        for (Method m : clss.getMethods()) {
          // TODO(maxuser): also check convertibility of param types
          if (m.getName().equals(methodName) && m.getParameterTypes().length == params.size()) {
            return m.invoke(
                objectValue, params.stream().map(p -> p.eval(context)).toArray(Object[]::new));
          }
        }
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }

      throw new IllegalArgumentException(
          String.format("Function not defined: %s.%s", object, methodName));
    }

    private Object callStaticMethod(Class<?> clss, String methodName, Context context) {
      // TODO(maxuser): cache and filter method lookups by name
      try {
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
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }
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

  public record StaticMethod(JavaClassId classId, Identifier method) implements Expression {
    @Override
    public Object eval(Context context) {
      throw new UnsupportedOperationException(
          String.format("Static methods can be called but not evaluated: %s.%s", classId, method));
    }

    @Override
    public String toString() {
      return String.format("%s.%s", classId, method);
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
    public Object eval(Context context) {
      var objectValue = object.eval(context);
      var objectClass = objectValue instanceof Class ? (Class) objectValue : objectValue.getClass();
      try {
        var fieldAccess = objectClass.getField(field.name());
        return fieldAccess.get(objectValue);
      } catch (NoSuchFieldException | IllegalAccessException e) {
        throw new IllegalArgumentException(e);
      }
    }

    @Override
    public String toString() {
      return String.format("%s.%s", object, field);
    }
  }

  public static class Context {
    private static final Object NOT_FOUND = new Object();

    private final Context globals;
    private final Context enclosingContext;
    private final List<Statement> globalStatements;
    private Set<String> globalVarNames = null;
    private Map<String, BoundFunction> boundFunctions = null;
    private final Map<String, Object> vars = new HashMap<>();
    private Object returnValue;
    private boolean returned = false;

    private Context() {
      globals = this;
      enclosingContext = null;
      globalStatements = new ArrayList<>();
    }

    private Context(Context globals, Context enclosingContext) {
      this.globals = globals;
      this.enclosingContext = enclosingContext == globals ? null : enclosingContext;
      this.globalStatements = null; // Defined only for global context.
    }

    public static Context createGlobals() {
      var context = new Context();
      context.setVariable(new Identifier("math"), new math());
      return context;
    }

    public Context createLocalContext(Context enclosingContext) {
      return new Context(globals, enclosingContext);
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

    /**
     * Executes statements added via {@code addGlobalStatement} since last call to {@code
     * execGlobalStatements}.
     */
    public void execGlobalStatements() {
      if (this != globals) {
        throw new IllegalStateException("Cannot execute global statements in local context");
      }
      for (var statement : globalStatements) {
        statement.exec(globals);
      }
      globalStatements.clear();
    }

    public void setBoundFunction(BoundFunction boundFunction) {
      if (boundFunctions == null) {
        boundFunctions = new HashMap<>();
      }
      boundFunctions.put(boundFunction.function().identifier().name(), boundFunction);
    }

    public BoundFunction getBoundFunction(String name) {
      var funcs = boundFunctions == null ? globals.boundFunctions : boundFunctions;

      var func = funcs.get(name);
      if (func != null) {
        return func;
      }
      if (enclosingContext != null) {
        if ((func = enclosingContext.getBoundFunction(name)) != null) {
          return func;
        }
      }
      if (this != globals) {
        return globals.getBoundFunction(name);
      }
      return null;
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
      } else if (enclosingContext != null) {
        return enclosingContext.getVariable(id);
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

  /** Emulation of Python math module. */
  public static class math {
    public final double pi = Math.PI;
    public final double e = Math.E;
    public final double tau = Math.TAU;

    public double sqrt(double x) {
      return Math.sqrt(x);
    }
  }
}
