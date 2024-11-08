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
import java.lang.reflect.Constructor;
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

        case "For":
          return new ForBlock(
              parseExpression(getAttr(element, "target")),
              parseExpression(getAttr(element, "iter")),
              parseStatementBlock(getBody(element)));

        case "While":
          return new WhileBlock(
              parseExpression(getAttr(element, "test")), parseStatementBlock(getBody(element)));

        case "Break":
          return new Break();

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
        case "UnaryOp":
          return new UnaryOp(
              UnaryOp.parse(getType(getAttr(element, "op"))),
              parseExpression(getAttr(element, "operand")));

        case "BinOp":
          return new BinaryOp(
              parseExpression(getAttr(element, "left")),
              BinaryOp.parse(getType(getAttr(element, "op"))),
              parseExpression(getAttr(element, "right")));

        case "Compare":
          return new Comparison(
              parseExpression(getAttr(element, "left")),
              Comparison.parse(getType(getAttr(element, "ops").getAsJsonArray().get(0))),
              parseExpression(getAttr(element, "comparators").getAsJsonArray().get(0)));

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
          return ConstantExpression.parse(
              getAttr(element, "typename").getAsString(), getAttr(element, "value"));

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
            } else if (func instanceof JavaClassId javaClassId) {
              return new CtorCall(
                  javaClassId,
                  StreamSupport.stream(args.spliterator(), false)
                      .map(elem -> parseExpression(elem))
                      .collect(toList()));
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
    } else if (value instanceof Lengthable lengthable) {
      return lengthable.__len__() != 0;
    } else if (value instanceof Collection<?> collection) {
      return !collection.isEmpty();
    } else if (value instanceof Map<?, ?> map) {
      return !map.isEmpty();
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
      if (context.skipStatement()) {
        return;
      }
      if (convertToBool(condition.eval(context))) {
        thenBody.exec(context);
      } else {
        elseBody.exec(context);
      }
    }
  }

  public record ForBlock(Expression vars, Expression iter, Statement body) implements Statement {
    @Override
    public void exec(Context context) {
      if (context.skipStatement()) {
        return;
      }
      // TODO(maxuser): Support arrays and other iterable values that don't implement Iterable<>.
      var iterValue = iter.eval(context);
      var iterableValue = (Iterable<?>) iterValue;
      // TODO(maxuser): Support unpacked values like `for k, v in map: ...`
      var varId = (Identifier) vars;
      try {
        context.enterLoop();
        for (var value : iterableValue) {
          context.setVariable(varId.name(), value);
          body.exec(context);
          if (context.shouldBreak()) {
            break;
          }
        }
      } finally {
        context.exitLoop();
      }
    }
  }

  public record WhileBlock(Expression condition, Statement body) implements Statement {
    @Override
    public void exec(Context context) {
      if (context.skipStatement()) {
        return;
      }
      try {
        context.enterLoop();
        while (convertToBool(condition.eval(context))) {
          body.exec(context);
          if (context.shouldBreak()) {
            break;
          }
        }
      } finally {
        context.exitLoop();
      }
    }
  }

  public record Break() implements Statement {
    @Override
    public void exec(Context context) {
      context.breakLoop();
    }
  }

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
      if (context.skipStatement()) {
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
        if (context.skipStatement()) {
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
      if (context.skipStatement()) {
        return;
      }
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
              pyList.__iadd__(rhs);
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
      if (context.skipStatement()) {
        return;
      }
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
      if (context.skipStatement()) {
        return;
      }
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

  public static Number parseIntegralValue(Number value) {
    long l = value.longValue();
    int i = (int) l;
    if (l == i) {
      return i;
    } else {
      return l;
    }
  }

  public static Number parseFloatingPointValue(Number value) {
    double d = value.doubleValue();
    float f = (float) d;
    if (d == f) {
      return f;
    } else {
      return d;
    }
  }

  public record ConstantExpression(Object value) implements Expression {
    public static ConstantExpression parse(String typename, JsonElement value) {
      switch (typename) {
        case "bool":
          return new ConstantExpression(value.getAsBoolean());
        case "int":
          return new ConstantExpression(parseIntegralValue(value.getAsNumber()));
        case "float":
          return new ConstantExpression(parseFloatingPointValue(value.getAsNumber()));
        case "str":
          return new ConstantExpression(value.getAsString());
        case "NoneType":
          return new ConstantExpression(null);
      }
      throw new IllegalArgumentException(
          String.format("Unsupported primitive type: %s (%s)", value, typename));
    }

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

  public record Comparison(Expression lhs, Op op, Expression rhs) implements Expression {
    public enum Op {
      EQ("=="),
      LT("<"),
      LT_EQ("<="),
      GT(">"),
      GT_EQ(">="),
      NOT_EQ("!=");

      private final String symbol;

      Op(String symbol) {
        this.symbol = symbol;
      }

      public String symbol() {
        return symbol;
      }
    }

    public static Op parse(String opName) {
      switch (opName) {
        case "Eq":
          return Op.EQ;
        case "Lt":
          return Op.LT;
        case "LtE":
          return Op.LT_EQ;
        case "Gt":
          return Op.GT;
        case "GtE":
          return Op.GT_EQ;
        case "NotEq":
          return Op.NOT_EQ;
        default:
          throw new UnsupportedOperationException("Unsupported binary op: " + opName);
      }
    }

    @Override
    public Object eval(Context context) {
      var lhsValue = lhs.eval(context);
      var rhsValue = rhs.eval(context);
      switch (op) {
        case EQ:
          if (lhsValue instanceof Number lhsNumber && rhsValue instanceof Number rhsNumber) {
            return Numbers.equals(lhsNumber, rhsNumber);
          } else {
            return lhsValue.equals(rhsValue);
          }
        case LT:
          if (lhsValue instanceof Number lhsNumber && rhsValue instanceof Number rhsNumber) {
            return Numbers.lessThan(lhsNumber, rhsNumber);
          } else if (lhsValue instanceof Comparable lhsComp
              && rhsValue instanceof Comparable rhsComp
              && lhsValue.getClass() == rhsValue.getClass()) {
            return lhsComp.compareTo(rhsComp) < 0;
          }
        case LT_EQ:
          if (lhsValue instanceof Number lhsNumber && rhsValue instanceof Number rhsNumber) {
            return Numbers.lessThanOrEquals(lhsNumber, rhsNumber);
          } else if (lhsValue instanceof Comparable lhsComp
              && rhsValue instanceof Comparable rhsComp
              && lhsValue.getClass() == rhsValue.getClass()) {
            return lhsComp.compareTo(rhsComp) <= 0;
          }
        case GT:
          if (lhsValue instanceof Number lhsNumber && rhsValue instanceof Number rhsNumber) {
            return Numbers.greaterThan(lhsNumber, rhsNumber);
          } else if (lhsValue instanceof Comparable lhsComp
              && rhsValue instanceof Comparable rhsComp
              && lhsValue.getClass() == rhsValue.getClass()) {
            return lhsComp.compareTo(rhsComp) > 0;
          }
        case GT_EQ:
          if (lhsValue instanceof Number lhsNumber && rhsValue instanceof Number rhsNumber) {
            return Numbers.greaterThanOrEquals(lhsNumber, rhsNumber);
          } else if (lhsValue instanceof Comparable lhsComp
              && rhsValue instanceof Comparable rhsComp
              && lhsValue.getClass() == rhsValue.getClass()) {
            return lhsComp.compareTo(rhsComp) >= 0;
          }
        case NOT_EQ:
          if (lhsValue instanceof Number lhsNumber && rhsValue instanceof Number rhsNumber) {
            return !Numbers.equals(lhsNumber, rhsNumber);
          } else {
            return !lhsValue.equals(rhsValue);
          }
      }
      throw new UnsupportedOperationException(
          String.format("Comparison op not supported: %s %s %s", lhs, op, rhs));
    }

    @Override
    public String toString() {
      return String.format("%s %s %s", lhs, op.symbol(), rhs);
    }
  }

  public record BoolOp(Expression lhs, Op op, Expression rhs) implements Expression {
    public enum Op {
      AND("and"),
      OR("or");

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
      boolean lhsBool = convertToBool(lhs.eval(context));
      switch (op) {
        case AND:
          return lhsBool && convertToBool(rhs.eval(context));
        case OR:
          return lhsBool || convertToBool(rhs.eval(context));
      }
      throw new UnsupportedOperationException(
          String.format("Boolean op not supported: %s %s %s", lhs, op, rhs));
    }

    @Override
    public String toString() {
      // TODO(maxuser): Fix output for proper order of operations that may need parentheses.
      // E.g. `(1 + 2) * 3` evaluates correctly but gets output to String as `1 + 2 * 3`.
      return String.format("%s %s %s", lhs, op.symbol(), rhs);
    }
  }

  public record UnaryOp(Op op, Expression operand) implements Expression {
    public enum Op {
      SUB("-"),
      NOT("not");

      private final String symbol;

      Op(String symbol) {
        this.symbol = symbol;
      }

      public String symbol() {
        return symbol;
      }
    }

    public static Op parse(String opName) {
      switch (opName) {
        case "USub":
          return Op.SUB;
        case "Not":
          return Op.NOT;
        default:
          throw new UnsupportedOperationException("Unsupported unary op: " + opName);
      }
    }

    @Override
    public Object eval(Context context) {
      var value = operand.eval(context);
      switch (op) {
        case SUB:
          if (value instanceof Number number) {
            return Numbers.negate(number);
          }
          break;
        case NOT:
          return !convertToBool(operand.eval(context));
      }
      throw new IllegalArgumentException(
          String.format(
              "bad operand type for unary %s: '%s' (%s)",
              op.symbol(), value.getClass().getName(), operand));
    }

    @Override
    public String toString() {
      return String.format("%s%s%s", op, op == Op.NOT ? " " : "", op.symbol(), operand);
    }
  }

  public record BinaryOp(Expression lhs, Op op, Expression rhs) implements Expression {
    public enum Op {
      ADD("+"),
      SUB("-"),
      MUL("*"),
      DIV("/"),
      MOD("%"),
      OR("||"),
      AND("&&");

      private final String symbol;

      Op(String symbol) {
        this.symbol = symbol;
      }

      public String symbol() {
        return symbol;
      }
    }

    public static Op parse(String opName) {
      switch (opName) {
        case "Add":
          return Op.ADD;
        case "Sub":
          return Op.SUB;
        case "Mult":
          return Op.MUL;
        default:
          throw new UnsupportedOperationException("Unsupported binary op: " + opName);
      }
    }

    @Override
    public Object eval(Context context) {
      var lhsValue = lhs.eval(context);
      var rhsValue = (op == Op.OR || op == Op.AND) ? null : rhs.eval(context);
      switch (op) {
        case ADD:
          if (lhsValue instanceof Number lhsNum && rhsValue instanceof Number rhsNum) {
            return Numbers.add(lhsNum, rhsNum);
          } else if (lhsValue instanceof String && rhsValue instanceof String) {
            return lhsValue.toString() + rhsValue.toString();
          }
          if (lhsValue instanceof PyList pyList) {
            lhsValue = pyList.getJavaList();
          }
          if (rhsValue instanceof PyList pyList) {
            rhsValue = pyList.getJavaList();
          }
          if (lhsValue instanceof List lhsList && rhsValue instanceof List rhsList) {
            var newList = new PyList(new ArrayList<Object>(lhsList));
            newList.__iadd__(rhsList);
            return newList;
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

      if (arrayValue instanceof PyList list) {
        return list.__getitem__(indexValue);
      } else if (arrayValue.getClass().isArray()) {
        int intKey = ((Number) indexValue).intValue();
        return Array.get(arrayValue, intKey);
      } else if (arrayValue instanceof List list) {
        int intKey = ((Number) indexValue).intValue();
        return list.get(intKey);
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

  public record CtorCall(JavaClassId classId, List<Expression> params) implements Expression {
    @Override
    public Object eval(Context context) {
      // TODO(maxuser): Check param types at parse time to select the appropriate ctor.
      Constructor<?>[] ctors = classId.clss().getConstructors();
      Object[] paramValues = params.stream().map(p -> p.eval(context)).toArray(Object[]::new);
      for (var ctor : ctors) {
        // TODO(maxuser): Find the best method overload rather than the first compatible one.
        if (paramsAreCompatible(ctor.getParameterTypes(), paramValues)) {
          try {
            return ctor.newInstance(paramValues);
          } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
          }
        }
      }
      throw new IllegalArgumentException(
          String.format(
              "No matching construtor for %s with %d params",
              classId.clss().getName(), params.size()));
    }

    @Override
    public String toString() {
      return String.format(
          "%s(%s)", classId, params.stream().map(Object::toString).collect(joining(", ")));
    }
  }

  private static boolean paramsAreCompatible(Class<?>[] formalParamTypes, Object[] paramValues) {
    if (formalParamTypes.length != paramValues.length) {
      return false;
    }
    for (int i = 0; i < formalParamTypes.length; ++i) {
      Class<?> type = promotePrimitiveType(formalParamTypes[i]);
      Object value = paramValues[i];
      if (value == null) {
        continue; // null is convertible to everything.
      }
      Class<?> valueType = value.getClass();
      if (Number.class.isAssignableFrom(type)
          && Number.class.isAssignableFrom(valueType)
          && numericTypeIsConvertible(valueType, type)) {
        continue;
      }
      if (!type.isAssignableFrom(value.getClass())) {
        return false;
      }
    }
    return true;
  }

  private static Class<?> promotePrimitiveType(Class<?> type) {
    if (type == boolean.class) {
      return Boolean.class;
    } else if (type == int.class) {
      return Integer.class;
    } else if (type == float.class) {
      return Float.class;
    } else if (type == double.class) {
      return Double.class;
    } else if (type == char.class) {
      return Character.class;
    } else {
      return type;
    }
  }

  private static boolean numericTypeIsConvertible(Class<?> from, Class<?> to) {
    if (to == Double.class) {
      return true;
    }
    if (to == Float.class) {
      return from != Double.class;
    }
    if (to == Integer.class) {
      return from == Integer.class;
    }
    return false;
  }

  public static String pyToString(Object value) {
    if (value == null) {
      return "None";
    } else if (value instanceof Object[] array) {
      // TODO(maxuser): Support for primitive array types too.
      return Arrays.stream(array).map(Interpreter::pyRepr).collect(joining(", ", "[", "]"));
    } else if (value instanceof PyList pyList) {
      return pyList.getJavaList().stream()
          .map(Interpreter::pyRepr)
          .collect(joining(", ", "[", "]"));
    } else if (value instanceof List<?> list) {
      return list.stream().map(Interpreter::pyRepr).collect(joining(", ", "[", "]"));
    } else if (value instanceof Boolean bool) {
      return bool ? "True" : "False";
    } else {
      return value.toString();
    }
  }

  public static String pyRepr(Object value) {
    if (value instanceof String string) {
      Gson gson =
          new GsonBuilder()
              .setPrettyPrinting() // Optional: for pretty printing
              .disableHtmlEscaping() // Important: to prevent double escaping
              .create();
      return gson.toJson(string);
    } else {
      return pyToString(value);
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

    public List<Object> getJavaList() {
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
      if (value instanceof PyList pyList) {
        this.list.addAll(pyList.list);
      } else if (value instanceof List<?> list) {
        this.list.addAll(list);
      } else {
        throw new IllegalArgumentException(
            String.format(
                "can only concatenate list (not \"%s\") to list",
                value == null ? "None" : value.getClass().getName()));
      }
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
              return parseIntegralValue(Long.parseLong(string));
            } else {
              return parseIntegralValue((Number) value);
            }
          }
        case "float":
          {
            expectNumParams(1);
            var value = params.get(0).eval(context);
            if (value instanceof String string) {
              return parseFloatingPointValue(Double.parseDouble(string));
            } else {
              return parseFloatingPointValue((Number) value);
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
        case "type":
          expectNumParams(1);
          return params.get(0).eval(context).getClass();
        case "range":
          return new RangeIterable(params.stream().map(p -> p.eval(context)).collect(toList()));
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
      var clss = objectValue.getClass();
      Object[] paramValues = params.stream().map(p -> p.eval(context)).toArray(Object[]::new);
      // TODO(maxuser): cache and filter method lookups by name
      try {
        for (Method m : clss.getMethods()) {
          // TODO(maxuser): Find the best method overload rather than the first compatible one.
          if (m.getName().equals(methodName)
              && paramsAreCompatible(m.getParameterTypes(), paramValues)) {
            return m.invoke(objectValue, paramValues);
          }
        }
      } catch (IllegalAccessException | InvocationTargetException e) {
        throw new RuntimeException(e);
      }

      throw new IllegalArgumentException(
          String.format(
              "Method not defined on %s: %s.%s(%s)",
              clss.getName(),
              object,
              methodName,
              Arrays.stream(paramValues)
                  .map(v -> String.format("(%s) %s", v.getClass().getName(), v))
                  .collect(joining(", "))));
    }

    private Object callStaticMethod(Class<?> clss, String methodName, Context context) {
      // TODO(maxuser): cache and filter method lookups by name
      Object[] paramValues = params.stream().map(p -> p.eval(context)).toArray(Object[]::new);
      try {
        for (Method m : clss.getMethods()) {
          // TODO(maxuser): Find the best method overload rather than the first compatible one.
          if (m.getName().equals(methodName)
              && paramsAreCompatible(m.getParameterTypes(), paramValues)) {
            return m.invoke(
                null, // static method ignores obj param
                paramValues);
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

  public static class RangeIterable implements Iterable<Integer> {
    private final int start;
    private final int stop;
    private final int step;

    public RangeIterable(List<Object> params) {
      switch (params.size()) {
        case 1:
          start = 0;
          stop = ((Number) params.get(0)).intValue();
          step = 1;
          break;
        case 2:
          start = ((Number) params.get(0)).intValue();
          stop = ((Number) params.get(1)).intValue();
          step = 1;
          break;
        case 3:
          start = ((Number) params.get(0)).intValue();
          stop = ((Number) params.get(1)).intValue();
          step = ((Number) params.get(2)).intValue();
          break;
        default:
          throw new IllegalArgumentException(
              "range expected 1 to 3 arguments, got " + params.size());
      }
    }

    public Iterator<Integer> iterator() {
      return new Iterator<Integer>() {
        private int pos = start;

        @Override
        public boolean hasNext() {
          return pos < stop;
        }

        @Override
        public Integer next() {
          int n = pos;
          pos += step;
          return n;
        }
      };
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
    private int loopDepth = 0;
    private boolean breakingLoop = false;

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

    public void enterLoop() {
      ++loopDepth;
    }

    public void exitLoop() {
      --loopDepth;
      if (loopDepth < 0) {
        throw new IllegalStateException("Exited more loops than were entered");
      }
      breakingLoop = false;
    }

    public void breakLoop() {
      if (loopDepth <= 0) {
        throw new IllegalStateException("'break' outside loop");
      }
      breakingLoop = true;
    }

    public void returnWithValue(Object returnValue) {
      if (this == globals) {
        throw new IllegalStateException("'return' outside function");
      }
      this.returnValue = returnValue;
      this.returned = true;
    }

    public boolean skipStatement() {
      return returned || breakingLoop;
    }

    public boolean shouldBreak() {
      return breakingLoop;
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
