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
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.naming.Context;
import net.minescript.common.Numbers;

public class Interpreter {

  private final ConcurrentHashMap<ExecutableCacheKey, Optional<Executable>> executableCache =
      new ConcurrentHashMap<>();

  private Context globals = Context.createGlobals(executableCache);

  public Interpreter parse(JsonElement element) {
    var parser = new JsonAstParser(executableCache);
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
    return function.invoke(globals, args);
  }

  public static class JsonAstParser {

    private final Map<ExecutableCacheKey, Optional<Executable>> executableCache;

    public JsonAstParser(Map<ExecutableCacheKey, Optional<Executable>> executableCache) {
      this.executableCache = executableCache;
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
                .toList());
      }
    }

    private FunctionDef parseFunctionDef(JsonElement element) {
      var identifier = new Identifier(getAttr(element, "name").getAsString());
      List<FunctionArg> args =
          parseFunctionArgs(
              getAttr(getAttr(element, "args").getAsJsonObject(), "args").getAsJsonArray());
      Statement body = parseStatementBlock(getBody(element));
      var func = new FunctionDef(identifier, args, body);
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
            return new Assignment(lhs, rhs);
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

        case "Delete":
          return new Deletion(
              StreamSupport.stream(getTargets(element).spliterator(), false)
                  .map(this::parseExpression)
                  .toList());

        case "Global":
          {
            return new GlobalVarDecl(
                StreamSupport.stream(
                        getAttr(element, "names").getAsJsonArray().spliterator(), false)
                    .map(name -> new Identifier(name.getAsString()))
                    .toList());
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

        case "Try":
          {
            JsonArray finalBody = getAttr(element, "finalbody").getAsJsonArray();
            return new TryBlock(
                parseStatementBlock(getBody(element)),
                StreamSupport.stream(
                        getAttr(element, "handlers").getAsJsonArray().spliterator(), false)
                    .map(this::parseExceptionHandler)
                    .toList(),
                finalBody.isEmpty()
                    ? Optional.empty()
                    : Optional.of(parseStatementBlock(finalBody)));
          }

        case "Raise":
          return new RaiseStatement(parseExpression(getAttr(element, "exc")));

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

    private ExceptionHandler parseExceptionHandler(JsonElement element) {
      return new ExceptionHandler(
          new Identifier(getAttr(getAttr(element, "type"), "id").getAsString()),
          new Identifier(getAttr(element, "name").getAsString()),
          parseStatementBlock(getBody(element)));
    }

    private List<FunctionArg> parseFunctionArgs(JsonArray args) {
      return StreamSupport.stream(args.spliterator(), false)
          .map(
              arg ->
                  new FunctionArg(
                      new Identifier(getAttr(arg.getAsJsonObject(), "arg").getAsString())))
          .toList();
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

        case "BoolOp":
          return new BoolOp(
              BoolOp.parse(getType(getAttr(element, "op"))),
              StreamSupport.stream(getAttr(element, "values").getAsJsonArray().spliterator(), false)
                  .map(this::parseExpression)
                  .toList());

        case "Name":
          {
            Identifier id = getId(element);
            if (id.name().equals("JavaClass")) {
              return new JavaClass();
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
                  return new JavaClassId(Class.forName(constString), executableCache);
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
              return new FunctionCall(
                  func,
                  StreamSupport.stream(args.spliterator(), false)
                      .map(this::parseExpression)
                      .toList(),
                  executableCache);
            }
          }

        case "Attribute":
          {
            var object = parseExpression(getAttr(element, "value"));
            var attr = new Identifier(getAttr(element, "attr").getAsString());
            if (parseContext == ParseContext.CALLER) {
              return new BoundMethod(object, attr, executableCache);
            } else {
              return new FieldAccess(object, attr);
            }
          }

        case "Subscript":
          return new ArrayIndex(
              parseExpression(getAttr(element, "value")),
              parseExpression(getAttr(element, "slice")));

        case "IfExp":
          return new IfExpression(
              parseExpression(getAttr(element, "test")),
              parseExpression(getAttr(element, "body")),
              parseExpression(getAttr(element, "orelse")));

        case "ListComp":
          {
            var generator = getAttr(element, "generators").getAsJsonArray().get(0);
            var generatorType = getType(generator);
            if (!generatorType.equals("comprehension")) {
              throw new UnsupportedOperationException(
                  "Unsupported expression type in list comprehension: " + generatorType);
            }
            return new ListComprehension(
                parseExpression(getAttr(element, "elt")),
                parseExpression(getAttr(generator, "target")),
                parseExpression(getAttr(generator, "iter")),
                StreamSupport.stream(
                        getAttr(generator, "ifs").getAsJsonArray().spliterator(), false)
                    .map(this::parseExpression)
                    .toList());
          }

        case "Tuple":
          return new TupleLiteral(
              StreamSupport.stream(getAttr(element, "elts").getAsJsonArray().spliterator(), false)
                  .map(this::parseExpression)
                  .toList());

        case "List":
          return new ListLiteral(
              StreamSupport.stream(getAttr(element, "elts").getAsJsonArray().spliterator(), false)
                  .map(this::parseExpression)
                  .toList());

        case "Dict":
          return new DictLiteral(
              StreamSupport.stream(getAttr(element, "keys").getAsJsonArray().spliterator(), false)
                  .map(this::parseExpression)
                  .toList(),
              StreamSupport.stream(getAttr(element, "values").getAsJsonArray().spliterator(), false)
                  .map(this::parseExpression)
                  .toList());

        case "Lambda":
          {
            return new Lambda(
                parseFunctionArgs(
                    getAttr(getAttr(element, "args").getAsJsonObject(), "args").getAsJsonArray()),
                parseExpression(getAttr(element, "body")));
          }

        case "JoinedStr":
          return new FormattedString(
              StreamSupport.stream(getAttr(element, "values").getAsJsonArray().spliterator(), false)
                  .map(
                      v ->
                          getType(v).equals("FormattedValue")
                              ? parseExpression(getAttr(v, "value"))
                              : parseExpression(v))
                  .toList());
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

  private static class ExecutableCacheKey {
    private final Object[] callSignature;

    private enum ExecutableType {
      INSTANCE_METHOD,
      STATIC_METHOD,
      CONSTRUCTOR
    }

    public static ExecutableCacheKey forMethod(
        Class<?> methodClass, boolean isStaticMethod, String methodName, Object[] paramValues) {
      Object[] callSignature = new Object[paramValues.length + 3];
      callSignature[0] =
          isStaticMethod
              ? ExecutableType.STATIC_METHOD.ordinal()
              : ExecutableType.INSTANCE_METHOD.ordinal();
      callSignature[1] = methodClass;
      callSignature[2] = methodName;
      for (int i = 0; i < paramValues.length; ++i) {
        Object paramValue = paramValues[i];
        callSignature[i + 3] = paramValue == null ? null : paramValue.getClass();
      }
      return new ExecutableCacheKey(callSignature);
    }

    public static ExecutableCacheKey forConstructor(Class<?> ctorClass, Object[] paramValues) {
      Object[] callSignature = new Object[paramValues.length + 2];
      callSignature[0] = ExecutableType.CONSTRUCTOR.ordinal();
      callSignature[1] = ctorClass;
      for (int i = 0; i < paramValues.length; ++i) {
        Object paramValue = paramValues[i];
        callSignature[i + 2] = paramValue == null ? null : paramValue.getClass();
      }
      return new ExecutableCacheKey(callSignature);
    }

    private ExecutableCacheKey(Object[] callSignature) {
      this.callSignature = callSignature;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ExecutableCacheKey other = (ExecutableCacheKey) o;
      return Arrays.equals(callSignature, other.callSignature);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(callSignature);
    }

    @Override
    public String toString() {
      return Arrays.deepToString(callSignature);
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
    } else if (value instanceof String str) {
      return !str.isEmpty() && !str.equals("False");
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

  public record BoundFunction(FunctionDef function, Context enclosingContext) implements Function {
    @Override
    public Object invoke(Context context, Object[] params) {
      if (params.length != function.args().size()) {
        throw new IllegalArgumentException(
            String.format(
                "Expected %d params but got %d for function: %s",
                function.args().size(), params.length, function));
      }
      return function.invoke(enclosingContext, params);
    }
  }

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
     * @param enclosingContext Context enclosing the definition of this function.
     * @param argValues Values to pass to this function's body.
     * @return Return value from invoking this function.
     */
    public Object invoke(Context enclosingContext, Object[] argValues) {
      if (args.size() != argValues.length) {
        throw new IllegalArgumentException(
            String.format(
                "Invoking function `%s` with %d args but %d required",
                identifier, argValues.length, args.size()));
      }

      var localContext = enclosingContext.createLocalContext();
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
      final Identifier loopVar;
      final TupleLiteral loopVars;
      if (vars instanceof Identifier id) {
        loopVar = id;
        loopVars = null;
      } else if (vars instanceof TupleLiteral tuple) {
        loopVar = null;
        loopVars = tuple;
      } else {
        throw new IllegalArgumentException("Unexpected loop variable type: " + vars.toString());
      }
      try {
        context.enterLoop();
        for (var value : iterableValue) {
          if (loopVar != null) {
            context.setVariable(loopVar.name(), value);
          } else {
            Assignment.assignTuple(context, loopVars, value);
          }
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
      return context.getVariable(name);
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public record ExceptionHandler(
      Identifier exceptionType, Identifier exceptionVariable, Statement body) {}

  public record TryBlock(
      Statement tryBody, List<ExceptionHandler> exceptionHandlers, Optional<Statement> finallyBlock)
      implements Statement {
    @Override
    public void exec(Context context) {
      if (context.skipStatement()) {
        return;
      }
      try {
        tryBody.exec(context);
      } catch (Exception e) {
        // PyException exists only to prevent all eval/exec/invoke methods from declaring that they
        // throw Exception.  Unwrap the underlying exception here.
        if (e instanceof PyException pyException) {
          e = (Exception) pyException.getCause();
        }
        boolean handled = false;
        for (var handler : exceptionHandlers) {
          if (context.getVariable(handler.exceptionType().name()) instanceof JavaClassId javaClassId
              && javaClassId.clss().isAssignableFrom(e.getClass())) {
            context.setVariable(handler.exceptionVariable().name(), e);
            handler.body.exec(context);
            handled = true;
            break;
          }
        }
        if (!handled) {
          throw new PyException(e);
        }
      } finally {
        finallyBlock.ifPresent(fb -> fb.exec(context));
      }
    }

    @Override
    public String toString() {
      var out = new StringBuilder("try\n");
      out.append("  " + tryBody.toString().replaceAll("\n", "\n  ") + "\n");
      for (var handler : exceptionHandlers) {
        out.append(
            String.format(
                " catch (%s %s)\n", handler.exceptionType(), handler.exceptionVariable()));
        out.append("  " + handler.body().toString().replaceAll("\n", "\n  ") + "\n");
      }
      finallyBlock.ifPresent(
          fb -> {
            out.append(" finally\n");
            out.append("  " + fb.toString().replaceAll("\n", "\n  ") + "\n");
          });
      return out.toString();
    }
  }

  /**
   * RuntimeException subclass that allows arbitrary Exception types to be thrown without requiring
   * all eval/exec/invoke methods to declare that they throw Exception.
   */
  public static class PyException extends RuntimeException {
    public PyException(Exception e) {
      super(e);
    }
  }

  public record RaiseStatement(Expression exception) implements Statement {
    @Override
    public void exec(Context context) {
      if (context.skipStatement()) {
        return;
      }
      throw new PyException((Exception) exception.eval(context));
    }

    @Override
    public String toString() {
      return String.format("throw %s;", exception);
    }
  }

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
    public Assignment {
      // TODO(maxuser): Support destructuring assignment more than one level of identifiers deep.
      if (!(lhs instanceof Identifier
          || lhs instanceof ArrayIndex
          || (lhs instanceof TupleLiteral tuple
              && tuple.elements().stream().allMatch(Identifier.class::isInstance)))) {
        throw new IllegalArgumentException(
            String.format(
                "Unsupported expression type for lhs of assignment: `%s` (%s)",
                lhs, lhs.getClass().getSimpleName()));
      }
    }

    public static void assignTuple(Context context, TupleLiteral lhsTuple, Object rhsValue) {
      List<Identifier> lhsVars = lhsTuple.elements().stream().map(Identifier.class::cast).toList();
      if (rhsValue instanceof ItemGetter getter && rhsValue instanceof Lengthable lengthable) {
        int lengthToUnpack = lengthable.__len__();
        if (lengthToUnpack != lhsVars.size()) {
          throw new IllegalArgumentException(
              String.format(
                  "Cannot unpack %d values into %d loop variables: %s",
                  lengthToUnpack, lhsVars.size(), rhsValue));
        }
        for (int i = 0; i < lengthToUnpack; ++i) {
          context.setVariable(lhsVars.get(i).name(), getter.__getitem__(i));
        }
      } else {
        throw new IllegalArgumentException("Cannot unpack value to tuple: " + rhsValue.toString());
      }
    }

    @Override
    public void exec(Context context) {
      if (context.skipStatement()) {
        return;
      }
      Object rhsValue = rhs.eval(context);
      if (lhs instanceof Identifier lhsId) {
        context.setVariable(lhsId, rhsValue);
        return;
      } else if (lhs instanceof TupleLiteral lhsTuple) {
        assignTuple(context, lhsTuple, rhsValue);
        return;
      } else if (lhs instanceof ArrayIndex lhsArrayIndex) {
        var array = lhsArrayIndex.array().eval(context);
        var index = lhsArrayIndex.index().eval(context);
        if (array.getClass().isArray()) {
          Array.set(array, ((Number) index).intValue(), rhsValue);
          return;
        } else if (array instanceof ItemSetter itemSetter) {
          itemSetter.__setitem__(index, rhsValue);
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
        var oldValue = context.getVariable(lhsId.name());
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
        } else if (array instanceof ItemGetter itemGetter
            && array instanceof ItemSetter itemSetter) {
          var oldValue = itemGetter.__getitem__(index);
          itemSetter.__setitem__(index, op.apply(oldValue, rhsValue));
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

  public record Deletion(List<Expression> targets) implements Statement {
    @Override
    public void exec(Context context) {
      for (var target : targets) {
        if (target instanceof Identifier id) {
          context.deleteVariable(id.name());
        } else if (target instanceof ArrayIndex arrayIndex) {
          var array = arrayIndex.array().eval(context);
          var index = arrayIndex.index().eval(context);
          if (array instanceof ItemDeleter deleter) {
            deleter.__delitem__(index);
          } else if (array instanceof List list) {
            list.remove((int) (Integer) index);
          } else if (array instanceof Map map) {
            map.remove(index);
          } else {
            throw new IllegalArgumentException(
                "Object does not support subscript deletion: " + array.getClass().getName());
          }
        } else {
          throw new IllegalArgumentException("Cannot delete value: " + target.toString());
        }
      }
    }

    @Override
    public String toString() {
      return String.format(
          "del %s;", targets.stream().map(Object::toString).collect(joining(", ")));
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
      NOT_EQ("!="),
      IN("in");

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
        case "In":
          return Op.IN;
        default:
          throw new UnsupportedOperationException("Unsupported comparison op: " + opName);
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
        case IN:
          if (rhsValue instanceof Collection<?> collection) {
            return collection.contains(lhsValue);
          } else if (rhsValue instanceof ItemContainer container) {
            return container.__contains__(lhsValue);
          } else if (rhsValue instanceof List list) {
            return list.contains(lhsValue);
          } else if (rhsValue instanceof Map map) {
            return map.containsKey(lhsValue);
          } else if (lhsValue instanceof String lhsStr && rhsValue instanceof String rhsStr) {
            return rhsStr.contains(lhsStr);
          }
      }
      throw new UnsupportedOperationException(
          String.format("Comparison op not supported: %s %s %s", lhs, op.symbol(), rhs));
    }

    @Override
    public String toString() {
      return String.format("%s %s %s", lhs, op.symbol(), rhs);
    }
  }

  public record BoolOp(Op op, List<Expression> values) implements Expression {
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

    public static Op parse(String opName) {
      switch (opName) {
        case "And":
          return Op.AND;
        case "Or":
          return Op.OR;
        default:
          throw new UnsupportedOperationException("Unsupported bool op: " + opName);
      }
    }

    @Override
    public Object eval(Context context) {
      switch (op) {
        case AND:
          {
            Object result = null;
            for (var expr : values) {
              result = expr.eval(context);
              if (!convertToBool(result)) {
                break;
              }
            }
            return result;
          }

        case OR:
          {
            Object result = null;
            for (var expr : values) {
              result = expr.eval(context);
              if (convertToBool(result)) {
                break;
              }
            }
            return result;
          }
      }
      throw new UnsupportedOperationException("Boolean op not supported: " + toString());
    }

    @Override
    public String toString() {
      return values.stream().map(Object::toString).collect(joining(" " + op.symbol() + " "));
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
      POW("**"),
      MOD("%");

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
        case "Div":
          return Op.DIV;
        case "Pow":
          return Op.POW;
        case "Mod":
          return Op.MOD;
        default:
          throw new UnsupportedOperationException("Unsupported binary op: " + opName);
      }
    }

    @Override
    public Object eval(Context context) {
      var lhsValue = lhs.eval(context);
      var rhsValue = rhs.eval(context);
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
        case DIV:
          if (lhsValue instanceof Number lhsNum && rhsValue instanceof Number rhsNum) {
            double d = lhsNum.doubleValue() / rhsNum.doubleValue();
            int i = (int) d;
            return i == d ? (Number) i : (Number) d;
          }
          break;
        case POW:
          if (lhsValue instanceof Number lhsNum && rhsValue instanceof Number rhsNum) {
            double d = Math.pow(lhsNum.doubleValue(), rhsNum.doubleValue());
            int i = (int) d;
            return i == d ? (Number) i : (Number) d;
          }
          break;
        case MOD:
          {
            if (lhsValue instanceof String lhsString) {
              if (rhsValue instanceof PyTuple tuple) {
                return String.format(
                    lhsString, StreamSupport.stream(tuple.spliterator(), false).toArray());
              } else {
                return String.format(lhsString, rhsValue);
              }
            } else {
              var lhsNum = (Number) lhsValue;
              var rhsNum = (Number) rhsValue;
              var div = Numbers.divide(lhsNum, rhsNum);
              var mult = Numbers.multiply(div, rhsNum);
              return Numbers.subtract(lhsNum, mult);
            }
          }
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

      if (arrayValue instanceof ItemGetter itemGetter) {
        return itemGetter.__getitem__(indexValue);
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

  public record IfExpression(Expression test, Expression body, Expression orElse)
      implements Expression {
    @Override
    public Object eval(Context context) {
      return convertToBool(test.eval(context)) ? body.eval(context) : orElse.eval(context);
    }

    @Override
    public String toString() {
      return String.format("%s if %s else %s", body, test, orElse);
    }
  }

  public static class TypeChecker {
    public static <T extends Executable> Optional<T> findBestMatchingExecutable(
        T[] executables, Predicate<T> filter, Object[] paramValues) {
      int bestScore = 0;
      Optional<T> bestExecutable = Optional.empty();

      for (T executable : executables) {
        if (filter.test(executable)) {
          int score = getTypeCheckScore(executable.getParameterTypes(), paramValues);
          if (score > bestScore) {
            bestScore = score;
            bestExecutable = Optional.of(executable);
          }
        }
      }
      return bestExecutable;
    }

    /**
     * Computes a score reflecting how well {@code paramValues} matches the {@code
     * formalParamTypes}.
     *
     * <p>Add 1 point for a param requiring conversion, 2 points for a param that's an exact match.
     * Return value of 0 indicates that {@code paramValues} are incompatible with {@code
     * formalParamTypes}.
     */
    private static int getTypeCheckScore(Class<?>[] formalParamTypes, Object[] paramValues) {
      if (formalParamTypes.length != paramValues.length) {
        return 0;
      }

      // Start score at 1 so it's non-zero if there's an exact match of no params.
      int score = 1;

      for (int i = 0; i < formalParamTypes.length; ++i) {
        Class<?> type = promotePrimitiveType(formalParamTypes[i]);
        Object value = paramValues[i];
        if (value == null) {
          // null is convertible to everything except primitive types.
          if (type != formalParamTypes[i]) {
            return 0;
          }
          if (type.isArray()) {
            score += 1;
          } else {
            score += 2;
          }
          continue;
        }
        Class<?> valueType = value.getClass();
        if (valueType == type) {
          score += 2;
          continue;
        }
        if (Number.class.isAssignableFrom(type)
            && Number.class.isAssignableFrom(valueType)
            && numericTypeIsConvertible(valueType, type)) {
          score += 1;
          continue;
        }
        if (!type.isAssignableFrom(value.getClass())) {
          return 0;
        }
      }
      return score;
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

  public record ListComprehension(
      Expression transform, Expression target, Expression iter, List<Expression> ifs)
      implements Expression {
    @Override
    public Object eval(Context context) {
      var localContext = context.createLocalContext();
      var list = new PyList();
      // TODO(maxuser): Share portions of impl with ForBlock::exec.
      // TODO(maxuser): Support arrays and other iterable values that don't implement Iterable<>.
      var iterValue = iter.eval(localContext);
      var iterableValue = (Iterable<?>) iterValue;
      final Identifier loopVar;
      final TupleLiteral loopVars;
      if (target instanceof Identifier id) {
        loopVar = id;
        loopVars = null;
      } else if (target instanceof TupleLiteral tuple) {
        loopVar = null;
        loopVars = tuple;
      } else {
        throw new IllegalArgumentException("Unexpected loop variable type: " + target.toString());
      }
      outerLoop:
      for (var value : iterableValue) {
        if (loopVar != null) {
          localContext.setVariable(loopVar.name(), value);
        } else {
          Assignment.assignTuple(localContext, loopVars, value);
        }
        for (var ifExpr : ifs) {
          if (!convertToBool(ifExpr.eval(localContext))) {
            continue outerLoop;
          }
        }
        list.append(transform.eval(localContext));
      }
      return list;
    }

    @Override
    public String toString() {
      var out = new StringBuilder("[");
      out.append(transform.toString());
      out.append(" for ");
      out.append(target.toString());
      out.append(" in ");
      out.append(iter.toString());
      out.append(ifs.stream().map(i -> " if " + i.toString()).collect(joining()));
      out.append("]");
      return out.toString();
    }
  }

  public record TupleLiteral(List<Expression> elements) implements Expression {
    @Override
    public Object eval(Context context) {
      return new PyTuple(elements.stream().map(e -> e.eval(context)).toArray());
    }

    @Override
    public String toString() {
      return elements.size() == 1
          ? String.format("(%s,)", elements.get(0))
          : elements.stream().map(Object::toString).collect(joining(", ", "(", ")"));
    }
  }

  public record ListLiteral(List<Expression> elements) implements Expression {
    @Override
    public Object eval(Context context) {
      // Stream.toList() returns immutable list, so using Stream.collect(toList()) for mutable List.
      return new PyList(elements.stream().map(e -> e.eval(context)).collect(toList()));
    }

    @Override
    public String toString() {
      return elements.stream().map(Object::toString).collect(joining(", ", "[", "]"));
    }
  }

  public interface Lengthable {
    int __len__();
  }

  public interface ItemGetter {
    Object __getitem__(Object key);
  }

  public interface ItemSetter {
    void __setitem__(Object key, Object value);
  }

  public interface ItemContainer {
    boolean __contains__(Object item);
  }

  public interface ItemDeleter {
    void __delitem__(Object key);
  }

  public static class PyList
      implements Iterable<Object>, Lengthable, ItemGetter, ItemSetter, ItemContainer, ItemDeleter {
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

    @Override
    public boolean __contains__(Object key) {
      return list.contains(key);
    }

    @Override
    public void __delitem__(Object key) {
      list.remove((int) (Integer) key);
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
    @Override
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
    @Override
    public void __setitem__(Object key, Object value) {
      if (key instanceof Integer i) {
        list.set(i, value);
        return;
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

  public static class PyTuple implements Iterable<Object>, Lengthable, ItemGetter, ItemContainer {
    private final Object[] array;

    public PyTuple(Object[] array) {
      this.array = array;
    }

    public Object[] getJavaArray() {
      return array;
    }

    @Override
    public boolean equals(Object value) {
      return value instanceof PyTuple pyTuple && Arrays.equals(this.array, pyTuple.array);
    }

    @Override
    public Iterator<Object> iterator() {
      return Arrays.stream(array).iterator();
    }

    @Override
    public String toString() {
      return array.length == 1
          ? String.format("(%s,)", pyToString(array[0]))
          : Arrays.stream(array).map(Interpreter::pyToString).collect(joining(", ", "(", ")"));
    }

    public PyTuple __add__(Object value) {
      if (value instanceof PyTuple tuple) {
        return new PyTuple(
            Stream.concat(Arrays.stream(array), Arrays.stream(tuple.array)).toArray());
      }
      throw new IllegalArgumentException(
          String.format(
              "Can only concatenate tuple (not \"%s\") to tuple", value.getClass().getName()));
    }

    @Override
    public boolean __contains__(Object key) {
      return Arrays.asList(array).contains(key);
    }

    public boolean __eq__(Object value) {
      return this.equals(value);
    }

    public boolean __ne__(Object value) {
      return !this.equals(value);
    }

    @Override
    public int __len__() {
      return array.length;
    }

    // TODO(maxuser): Support slice notation.
    @Override
    public Object __getitem__(Object key) {
      if (key instanceof Integer i) {
        return array[i];
      }
      throw new IllegalArgumentException(
          String.format(
              "Tuple indices must be integers or slices, not %s (%s)",
              key.getClass().getName(), key));
    }

    public long count(Object value) {
      return Arrays.stream(array).filter(o -> o.equals(value)).count();
    }

    public int index(Object value) {
      for (int i = 0; i < array.length; ++i) {
        if (array[i].equals(value)) {
          return i;
        }
      }
      throw new IllegalArgumentException(
          String.format("tuple.index(%s): value not in tuple", value));
    }
  }

  public record DictLiteral(List<Expression> keys, List<Expression> values) implements Expression {
    public DictLiteral {
      if (keys.size() != values.size()) {
        throw new IllegalArgumentException(
            String.format(
                "Size mismatch between keys and values: %d and %d", keys.size(), values.size()));
      }
    }

    @Override
    public Object eval(Context context) {
      var map = new HashMap<Object, Object>();
      for (int i = 0; i < keys.size(); ++i) {
        map.put(keys.get(i).eval(context), values.get(i).eval(context));
      }
      return new PyDict(map);
    }

    @Override
    public String toString() {
      var out = new StringBuilder("{");
      for (int i = 0; i < keys.size(); ++i) {
        if (i > 0) {
          out.append(", ");
        }
        out.append(keys.get(i));
        out.append(": ");
        out.append(values.get(i));
      }
      out.append("}");
      return out.toString();
    }
  }

  public record Lambda(List<FunctionArg> args, Expression body) implements Expression {
    @Override
    public Object eval(Context context) {
      return createFunction(context);
    }

    private Function createFunction(Context enclosingContext) {
      return (context, params) -> {
        if (args.size() != params.length) {
          throw new IllegalArgumentException(
              String.format(
                  "Invoking lambda with %d args but %d required", params.length, args.size()));
        }

        var localContext = enclosingContext.createLocalContext();
        for (int i = 0; i < args.size(); ++i) {
          var arg = args.get(i);
          var argValue = params[i];
          localContext.setVariable(arg.identifier().name(), argValue);
        }
        return body.eval(localContext);
      };
    }

    @Override
    public String toString() {
      return String.format(
          "lambda(%s): %s",
          args.stream().map(a -> a.identifier().name()).collect(joining(", ")), body);
    }
  }

  public record FormattedString(List<Expression> values) implements Expression {
    @Override
    public Object eval(Context context) {
      return values.stream().map(v -> v.eval(context).toString()).collect(joining());
    }

    @Override
    public String toString() {
      return String.format(
          "f\"%s\"",
          values.stream()
              .map(
                  v ->
                      v instanceof ConstantExpression constExpr
                              && constExpr.value() instanceof String strValue
                          ? strValue
                          : String.format("{%s}", v))
              .collect(joining()));
    }
  }

  public static class PyDict
      implements Iterable<Object>, Lengthable, ItemGetter, ItemSetter, ItemContainer, ItemDeleter {
    private static final Object NOT_FOUND = new Object();
    private final Map<Object, Object> map;

    public PyDict() {
      map = new HashMap<>();
    }

    public PyDict(Map<Object, Object> map) {
      this.map = map;
    }

    public Map<Object, Object> getJavaMap() {
      return map;
    }

    @Override
    public boolean equals(Object value) {
      return value instanceof PyDict pyDict && this.map.equals(pyDict.map);
    }

    @Override
    public Iterator<Object> iterator() {
      return map.keySet().iterator();
    }

    public Iterable<PyTuple> items() {
      return map.entrySet().stream().map(e -> new PyTuple(new Object[] {e.getKey(), e.getValue()}))
          ::iterator;
    }

    @Override
    public int __len__() {
      return map.size();
    }

    @Override
    public Object __getitem__(Object key) {
      var value = map.getOrDefault(key, NOT_FOUND);
      if (value == NOT_FOUND) {
        throw new NoSuchElementException("Key not found: " + key.toString());
      }
      return value;
    }

    @Override
    public void __setitem__(Object key, Object value) {
      map.put(key, value);
    }

    @Override
    public boolean __contains__(Object key) {
      return map.containsKey(key);
    }

    @Override
    public void __delitem__(Object key) {
      map.remove(key);
    }

    @Override
    public String toString() {
      var out = new StringBuilder("{");
      boolean firstEntry = true;
      for (var entry : map.entrySet()) {
        if (!firstEntry) {
          out.append(", ");
        }
        out.append(pyToString(entry.getKey()));
        out.append(": ");
        out.append(pyToString(entry.getValue()));
        firstEntry = false;
      }
      out.append("}");
      return out.toString();
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

  public record JavaClassId(
      Class<?> clss, Map<ExecutableCacheKey, Optional<Executable>> executableCache)
      implements Expression, Function {
    @Override
    public Object eval(Context context) {
      return this;
    }

    @Override
    public String toString() {
      return String.format("JavaClass(\"%s\")", clss.getName());
    }

    @Override
    public Object invoke(Context context, Object[] params) {
      var cacheKey = ExecutableCacheKey.forConstructor(clss, params);
      Optional<Constructor<?>> matchedCtor =
          executableCache
              .computeIfAbsent(
                  cacheKey,
                  ignoreKey ->
                      TypeChecker.findBestMatchingExecutable(
                          clss.getConstructors(), c -> true, params))
              .map(Constructor.class::cast);
      if (matchedCtor.isPresent()) {
        try {
          return matchedCtor.get().newInstance(params);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
          throw new RuntimeException(e);
        }
      } else {
        throw new IllegalArgumentException(
            String.format(
                "No matching constructor: %s(%s)",
                clss.getName(),
                Arrays.stream(params)
                    .map(
                        v ->
                            v == null
                                ? "null"
                                : String.format("(%s) %s", v.getClass().getName(), pyRepr(v)))
                    .collect(joining(", "))));
      }
    }
  }

  public interface Function {
    Object invoke(Context context, Object[] params);

    default void expectNumParams(Object[] params, int n) {
      if (params.length != n) {
        throw new IllegalArgumentException(
            String.format(
                "Expected %d params but got %d for function: %s", n, params.length, this));
      }
    }
  }

  public static class IntFunction implements Function {
    @Override
    public Object invoke(Context context, Object[] params) {
      expectNumParams(params, 1);
      var value = params[0];
      if (value instanceof String string) {
        return parseIntegralValue(Long.parseLong(string));
      } else {
        return parseIntegralValue((Number) value);
      }
    }
  }

  public static class FloatFunction implements Function {
    @Override
    public Object invoke(Context context, Object[] params) {
      expectNumParams(params, 1);
      var value = params[0];
      if (value instanceof String string) {
        return parseFloatingPointValue(Double.parseDouble(string));
      } else {
        return parseFloatingPointValue((Number) value);
      }
    }
  }

  public static class StrFunction implements Function {
    @Override
    public Object invoke(Context context, Object[] params) {
      expectNumParams(params, 1);
      return pyToString(params[0]);
    }
  }

  public static class BoolFunction implements Function {
    @Override
    public Object invoke(Context context, Object[] params) {
      expectNumParams(params, 1);
      return convertToBool(params[0]);
    }
  }

  public static class LenFunction implements Function {
    @Override
    public Object invoke(Context context, Object[] params) {
      expectNumParams(params, 1);
      var value = params[0];
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
          String.format("Object of type '%s' has no len(): %s", value.getClass().getName(), this));
    }
  }

  public static class PrintFunction implements Function {
    @Override
    public Object invoke(Context context, Object[] params) {
      System.out.println(Arrays.stream(params).map(Interpreter::pyToString).collect(joining(" ")));
      return null;
    }
  }

  public record TypeFunction(Map<ExecutableCacheKey, Optional<Executable>> executableCache)
      implements Function {
    @Override
    public Object invoke(Context context, Object[] params) {
      expectNumParams(params, 1);
      var value = params[0];
      if (value instanceof JavaClassId classId) {
        return classId.clss();
      } else {
        return new JavaClassId(value.getClass(), executableCache);
      }
    }
  }

  public static class RangeFunction implements Function {
    @Override
    public Object invoke(Context context, Object[] params) {
      return new RangeIterable(params);
    }
  }

  public record FunctionCall(
      Expression method,
      List<Expression> params,
      Map<ExecutableCacheKey, Optional<Executable>> executableCache)
      implements Expression {
    @Override
    public Object eval(Context context) {
      var caller = method.eval(context);
      Object[] paramValues = params.stream().map(p -> p.eval(context)).toArray(Object[]::new);
      if (caller instanceof Function function) {
        return function.invoke(context, paramValues);
      }

      throw new IllegalArgumentException(
          String.format(
              "'%s' is not callable: %s",
              caller == null ? "NoneType" : caller.getClass().getName(), method));
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

    public RangeIterable(Object[] params) {
      switch (params.length) {
        case 1:
          start = 0;
          stop = ((Number) params[0]).intValue();
          step = 1;
          break;
        case 2:
          start = ((Number) params[0]).intValue();
          stop = ((Number) params[1]).intValue();
          step = 1;
          break;
        case 3:
          start = ((Number) params[0]).intValue();
          stop = ((Number) params[1]).intValue();
          step = ((Number) params[2]).intValue();
          break;
        default:
          throw new IllegalArgumentException(
              "range expected 1 to 3 arguments, got " + params.length);
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

  public record BoundMethod(
      Expression object,
      Identifier method,
      Map<ExecutableCacheKey, Optional<Executable>> executableCache)
      implements Expression, Function {
    @Override
    public Object eval(Context context) {
      return this;
    }

    @Override
    public String toString() {
      return String.format("%s.%s", object, method);
    }

    @Override
    public Object invoke(Context context, Object[] params) {
      String methodName = method.name();
      var objectValue = object.eval(context);

      final boolean isStaticMethod;
      final Class<?> clss;
      if (objectValue instanceof JavaClassId classId) {
        isStaticMethod = true;
        clss = classId.clss();
      } else {
        isStaticMethod = false;
        clss = objectValue.getClass();
      }

      var cacheKey = ExecutableCacheKey.forMethod(clss, isStaticMethod, methodName, params);
      Optional<Method> matchedMethod =
          executableCache
              .computeIfAbsent(
                  cacheKey,
                  ignoreKey ->
                      TypeChecker.findBestMatchingExecutable(
                          clss.getMethods(),
                          m ->
                              Modifier.isStatic(m.getModifiers()) == isStaticMethod
                                  && m.getName().equals(methodName),
                          params))
              .map(Method.class::cast);
      if (matchedMethod.isPresent()) {
        try {
          return matchedMethod.get().invoke(isStaticMethod ? null : objectValue, params);
        } catch (IllegalAccessException | InvocationTargetException e) {
          throw new RuntimeException(e);
        }
      } else {
        throw new IllegalArgumentException(
            String.format(
                "No matching method on %s: %s.%s(%s)",
                object,
                clss.getName(),
                methodName,
                Arrays.stream(params)
                    .map(
                        v ->
                            v == null
                                ? "null"
                                : String.format("(%s) %s", v.getClass().getName(), pyRepr(v)))
                    .collect(joining(", "))));
      }
    }
  }

  public record FieldAccess(Expression object, Identifier field) implements Expression {
    @Override
    public Object eval(Context context) {
      var objectValue = object.eval(context);
      var objectClass =
          objectValue instanceof Class<?> ? (Class<?>) objectValue : objectValue.getClass();
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

    public static Context createGlobals(
        Map<ExecutableCacheKey, Optional<Executable>> executableCache) {
      var context = new Context();
      context.setVariable("math", new JavaClassId(math.class, executableCache));
      context.setVariable("int", new IntFunction());
      context.setVariable("float", new FloatFunction());
      context.setVariable("str", new StrFunction());
      context.setVariable("bool", new BoolFunction());
      context.setVariable("len", new LenFunction());
      context.setVariable("print", new PrintFunction());
      context.setVariable("type", new TypeFunction(executableCache));
      context.setVariable("range", new RangeFunction());
      return context;
    }

    public Context createLocalContext() {
      return new Context(globals, this);
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
      setVariable(boundFunction.function().identifier().name(), boundFunction);
    }

    public BoundFunction getBoundFunction(String name) {
      return (BoundFunction) getVariable(name);
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

    public Object getVariable(String name) {
      if (this != globals && globalVarNames != null && globalVarNames.contains(name)) {
        return globals.getVariable(name);
      }
      var value = vars.getOrDefault(name, NOT_FOUND);
      if (value != NOT_FOUND) {
        return value;
      } else if (enclosingContext != null) {
        return enclosingContext.getVariable(name);
      } else if (this != globals) {
        return globals.getVariable(name);
      } else {
        throw new IllegalArgumentException("Variable not found: " + name);
      }
    }

    public void deleteVariable(String name) {
      if (this != globals && globalVarNames != null && globalVarNames.contains(name)) {
        globals.deleteVariable(name);
        return;
      }
      if (!vars.containsKey(name)) {
        throw new IllegalArgumentException(String.format("Name '%s' is not defined", name));
      }
      vars.remove(name);
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
    public static final double pi = Math.PI;
    public static final double e = Math.E;
    public static final double tau = Math.TAU;

    public static double sqrt(double x) {
      return Math.sqrt(x);
    }
  }
}
