// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.interpreter;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minescript.common.Numbers;

public class Script {

  private final ConcurrentHashMap<ExecutableCacheKey, Optional<Executable>> executableCache =
      new ConcurrentHashMap<>();

  private Context globals = Context.createGlobals(executableCache);

  public Context globals() {
    return globals;
  }

  public Script parse(JsonElement element) {
    return parse(element, "<stdin>");
  }

  public Script parse(JsonElement element, String scriptFilename) {
    globals.setScriptFilename(scriptFilename);
    var parser = new JsonAstParser(executableCache);
    parser.parseGlobals(element, globals);
    return this;
  }

  public Script exec() {
    globals.execGlobalStatements();
    return this;
  }

  public Script redirectStdout(Consumer<String> out) {
    globals.setVariable("__stdout__", out);
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
            getLineno(block),
            StreamSupport.stream(block.spliterator(), false)
                .map(elem -> parseStatements(elem))
                .toList());
      }
    }

    private ClassDef parseClassDef(JsonElement element) {
      var identifier = new Identifier(getAttr(element, "name").getAsString());
      var fields = new ArrayList<ClassFieldDef>();
      var methodDefs = new ArrayList<FunctionDef>();
      StreamSupport.stream(getBody(element).spliterator(), false)
          .forEach(
              elem -> {
                var type = getType(elem);
                switch (type) {
                  case "FunctionDef":
                    methodDefs.add(parseFunctionDef(identifier.name(), elem));
                    break;
                  case "Assign":
                    fields.add(parseClassFieldDef(elem, true));
                    break;
                  case "AnnAssign":
                    fields.add(parseClassFieldDef(elem, false));
                    break;
                }
              });
      return new ClassDef(
          getLineno(element), identifier, getDecorators(element), fields, methodDefs);
    }

    private ClassFieldDef parseClassFieldDef(JsonElement element, boolean multipleTargets) {
      var target =
          multipleTargets
              ? getAttr(element, "targets").getAsJsonArray().get(0).getAsJsonObject()
              : getAttr(element, "target").getAsJsonObject();
      var value = getAttr(element, "value");
      return new ClassFieldDef(
          new Identifier(target.get("id").getAsString()),
          value == null || value.isJsonNull()
              ? Optional.empty()
              : Optional.of(parseExpression(value)));
    }

    private List<Decorator> getDecorators(JsonElement element) {
      return StreamSupport.stream(
              getAttr(element, "decorator_list").getAsJsonArray().spliterator(), false)
          .map(
              e -> {
                String type = getType(e);
                if ("Call".equals(type)) {
                  return Optional.of(
                      new Decorator(
                          getAttr(getAttr(e, "func"), "id").getAsString(),
                          getAttr(e, "keywords").getAsJsonArray().asList()));
                } else if ("Name".equals(type)) {
                  return Optional.of(new Decorator(getAttr(e, "id").getAsString(), List.of()));
                } else {
                  // TODO(maxuser): What other kinds of decorator expressions are there?
                  return Optional.<Decorator>empty();
                }
              })
          .filter(Optional::isPresent)
          .map(Optional::get)
          .toList();
    }

    private FunctionDef parseFunctionDef(String enclosingClassName, JsonElement element) {
      var identifier = new Identifier(getAttr(element, "name").getAsString());
      var decorators = getDecorators(element);
      List<FunctionArg> args =
          parseFunctionArgs(
              getAttr(getAttr(element, "args").getAsJsonObject(), "args").getAsJsonArray());
      Statement body = parseStatementBlock(getBody(element));
      var func =
          new FunctionDef(
              getLineno(element), enclosingClassName, identifier, decorators, args, body);
      return func;
    }

    public Statement parseStatements(JsonElement element) {
      try {
        String type = getType(element);
        switch (type) {
          case "ClassDef":
            return parseClassDef(element);

          case "FunctionDef":
            return parseFunctionDef("<>", element);

          case "AnnAssign":
            {
              Expression lhs = parseExpression(element.getAsJsonObject().get("target"));
              Expression rhs = parseExpression(getAttr(element, "value"));
              return new Assignment(getLineno(element), lhs, rhs);
            }

          case "Assign":
            {
              Expression lhs = parseExpression(getTargets(element).get(0));
              Expression rhs = parseExpression(getAttr(element, "value"));
              return new Assignment(getLineno(element), lhs, rhs);
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
              if (lhs instanceof Identifier) {
                return new AugmentedAssignment(getLineno(element), lhs, op, rhs);
              } else if (lhs instanceof ArrayIndex) {
                return new AugmentedAssignment(getLineno(element), lhs, op, rhs);
              } else {
                throw new IllegalArgumentException(
                    String.format(
                        "Unsupported expression type for lhs of assignment: `%s` (%s)",
                        lhs, lhs.getClass().getSimpleName()));
              }
            }

          case "Delete":
            return new Deletion(
                getLineno(element),
                StreamSupport.stream(getTargets(element).spliterator(), false)
                    .map(this::parseExpression)
                    .toList());

          case "Global":
            {
              return new GlobalVarDecl(
                  getLineno(element),
                  StreamSupport.stream(
                          getAttr(element, "names").getAsJsonArray().spliterator(), false)
                      .map(name -> new Identifier(name.getAsString()))
                      .toList());
            }

          case "Expr":
            return parseExpression(getAttr(element, "value"));

          case "If":
            {
              var elseElement = getAttr(element, "orelse").getAsJsonArray();
              return new IfBlock(
                  getLineno(element),
                  parseExpression(getAttr(element, "test")),
                  parseStatementBlock(getBody(element)),
                  elseElement.isEmpty()
                      ? Optional.empty()
                      : Optional.of(parseStatementBlock(elseElement)));
            }

          case "For":
            return new ForBlock(
                getLineno(element),
                parseExpression(getAttr(element, "target")),
                parseExpression(getAttr(element, "iter")),
                parseStatementBlock(getBody(element)));

          case "While":
            return new WhileBlock(
                getLineno(element),
                parseExpression(getAttr(element, "test")),
                parseStatementBlock(getBody(element)));

          case "Break":
            return new Break();

          case "Try":
            {
              JsonArray finalBody = getAttr(element, "finalbody").getAsJsonArray();
              return new TryBlock(
                  getLineno(element),
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
            return new RaiseStatement(getLineno(element), parseExpression(getAttr(element, "exc")));

          case "Return":
            {
              var returnValue = getAttr(element, "value");
              if (returnValue.isJsonNull()) {
                // No return value. This differs from `return None` in terms of the AST, but the two
                // evaluate the same.
                return new ReturnStatement(getLineno(element), null);
              } else {
                return new ReturnStatement(getLineno(element), parseExpression(returnValue));
              }
            }
        }
      } catch (ParseException e) {
        throw e;
      } catch (Exception e) {
        throw new ParseException("Exception while parsing statement: %s".formatted(element), e);
      }
      throw new IllegalArgumentException("Unknown statement type: " + element.toString());
    }

    private int getLineno(JsonElement element) {
      if (element.isJsonObject()) {
        var obj = element.getAsJsonObject();
        final String LINENO = "lineno";
        if (obj.has(LINENO)) {
          return obj.get(LINENO).getAsNumber().intValue();
        }
      }
      return -1;
    }

    class ParseException extends RuntimeException {
      public ParseException(String message, Throwable cause) {
        super(message, cause);
      }
    }

    private ExceptionHandler parseExceptionHandler(JsonElement element) {
      var type = getAttr(element, "type");
      var name = getAttr(element, "name");
      return new ExceptionHandler(
          type.isJsonNull()
              ? Optional.empty()
              : Optional.of(new Identifier(getAttr(getAttr(element, "type"), "id").getAsString())),
          name.isJsonNull()
              ? Optional.empty()
              : Optional.of(new Identifier(getAttr(element, "name").getAsString())),
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
                  getLineno(element),
                  func,
                  StreamSupport.stream(args.spliterator(), false)
                      .map(this::parseExpression)
                      .toList());
            }
          }

        case "Attribute":
          {
            var object = parseExpression(getAttr(element, "value"));
            var attr = new Identifier(getAttr(element, "attr").getAsString());
            if (parseContext == ParseContext.CALLER) {
              return new BoundMethodExpression(object, attr, executableCache);
            } else {
              return new FieldAccess(object, attr);
            }
          }

        case "Subscript":
          return new ArrayIndex(
              parseExpression(getAttr(element, "value")),
              parseSliceExpression(getAttr(element, "slice")));

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

    private Expression parseSliceExpression(JsonElement element) {
      if ("Slice".equals(getType(element))) {
        var lower = getAttr(element, "lower");
        var upper = getAttr(element, "upper");
        var step = getAttr(element, "step");
        return new SliceExpression(
            lower.isJsonNull() ? Optional.empty() : Optional.of(parseExpression(lower)),
            upper.isJsonNull() ? Optional.empty() : Optional.of(parseExpression(upper)),
            step.isJsonNull() ? Optional.empty() : Optional.of(parseExpression(step)));
      } else {
        return parseExpression(element);
      }
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

    default int lineno() {
      return -1;
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

  public record Decorator(String name, List<JsonElement> keywords) {}

  public record BoundFunction(FunctionDef function, Context enclosingContext) implements Function {
    @Override
    public Object call(Object... params) {
      if (params.length != function.args().size()) {
        throw new IllegalArgumentException(
            String.format(
                "Expected %d params but got %d for function: %s",
                function.args().size(), params.length, function));
      }
      return function.invoke(enclosingContext, params);
    }
  }

  // `type` is an array of length 1 because CtorFunction needs to be instantiated before the
  // surrounding class is fully defined. (Alternatively, PyClass could be mutable so that it's
  // instantiated before CtorFunction.)
  public record CtorFunction(PyClass[] type, FunctionDef function, Context enclosingContext)
      implements Function {
    @Override
    public Object call(Object... params) {
      Object[] ctorParams = new Object[params.length + 1];
      var self = new PyObject(type[0]);
      ctorParams[0] = self;
      System.arraycopy(params, 0, ctorParams, 1, params.length);
      if (ctorParams.length != function.args().size()) {
        throw new IllegalArgumentException(
            "Expected %d params but got %d for %s constructor: %s"
                .formatted(function.args().size() - 1, params.length, type[0].name, function));
      }
      function.invoke(enclosingContext, ctorParams);
      return self;
    }
  }

  public record ClassFieldDef(Identifier identifier, Optional<Expression> value) {}

  public record ClassDef(
      int lineno,
      Identifier identifier,
      List<Decorator> decorators,
      List<ClassFieldDef> fields,
      List<FunctionDef> methodDefs)
      implements Statement {
    /** Adds this class definition to the specified {@code context}. */
    @Override
    public void exec(Context context) {
      var type = new PyClass[1]; // Using array to circumvent immutability constraint for record.
      Function ctor;
      Optional<Decorator> dataclass =
          decorators.stream().filter(d -> d.name().equals("dataclass")).findFirst();
      if (dataclass.isPresent()) {
        var initializedFields = fields.stream().filter(f -> f.value().isPresent()).toList();
        var uninitializedFieldNames =
            fields.stream()
                .filter(f -> f.value().isEmpty())
                .map(ClassFieldDef::identifier)
                .map(Identifier::name)
                .toList();
        ctor =
            params -> {
              Function.expectNumParams(
                  params, uninitializedFieldNames.size(), identifier.name() + ".__init__");
              var object = new PyObject(type[0]);
              for (var field : initializedFields) {
                object.__dict__.__setitem__(
                    field.identifier().name(), field.value().get().eval(context));
              }
              for (int i = 0; i < params.length; ++i) {
                object.__dict__.__setitem__(uninitializedFieldNames.get(i), params[i]);
              }
              return object;
            };
      } else {
        ctor =
            params -> {
              Function.expectNumParams(params, 0, identifier.name() + ".__init__");
              return new PyObject(type[0]);
            };
      }

      var instanceMethods = new HashMap<String, Function>();
      var classLevelMethods = new HashMap<String, ClassLevelMethod>();
      for (var methodDef : methodDefs) {
        String methodName = methodDef.identifier().name();
        // TODO(maxuser): Support __str__/__rep__ methods for custom string output.
        if ("__init__".equals(methodName)) {
          ctor = new CtorFunction(type, methodDef, context);
          instanceMethods.put(methodName, ctor);
        } else if (methodDef.decorators().stream().anyMatch(d -> d.name().equals("classmethod"))) {
          classLevelMethods.put(
              methodName, new ClassLevelMethod(true, new BoundFunction(methodDef, context)));
        } else if (methodDef.decorators().stream().anyMatch(d -> d.name().equals("staticmethod"))) {
          classLevelMethods.put(
              methodName, new ClassLevelMethod(false, new BoundFunction(methodDef, context)));
        } else {
          instanceMethods.put(methodName, new BoundFunction(methodDef, context));
        }
      }
      // Example of "@dataclass(frozen=True)":
      // [{"type":"keyword","arg":"frozen","value":{"value":true}}]
      boolean isFrozen =
          dataclass
              .map(
                  d ->
                      d.keywords().stream()
                          .anyMatch(
                              k ->
                                  JsonAstParser.getType(k).equals("keyword")
                                      && JsonAstParser.getAttr(k, "arg")
                                          .getAsString()
                                          .equals("frozen")
                                      && JsonAstParser.getAttr(
                                              JsonAstParser.getAttr(k, "value"), "value")
                                          .getAsBoolean()))
              .orElse(false);
      type[0] =
          new PyClass(
              identifier.name(),
              ctor,
              isFrozen,
              instanceMethods,
              classLevelMethods,
              dataclass.map(d -> dataclassHashCode(fields)),
              dataclass.map(d -> dataclassToString(fields)));
      context.setVariable(identifier.name(), type[0]);
      if (!dataclass.isPresent()) {
        for (var field : fields) {
          field
              .value()
              .ifPresent(
                  v -> type[0].__dict__.__setitem__(field.identifier().name(), v.eval(context)));
        }
      }
    }

    private static java.util.function.Function<PyObject, Integer> dataclassHashCode(
        List<ClassFieldDef> fields) {
      return dataObject ->
          Objects.hash(
              fields.stream()
                  .map(f -> dataObject.__dict__.__getitem__(f.identifier().name()))
                  .toArray());
    }

    private static java.util.function.Function<PyObject, String> dataclassToString(
        List<ClassFieldDef> fields) {
      return dataObject ->
          fields.stream()
              .map(
                  field -> {
                    var fieldName = field.identifier().name();
                    return "%s=%s".formatted(fieldName, dataObject.__dict__.__getitem__(fieldName));
                  })
              .collect(joining(", ", dataObject.type.name + "(", ")"));
    }

    @Override
    public String toString() {
      // TODO(maxuser): Parse Decorator.keywords from JsonElement to list of record (name, value).
      String decoratorString =
          decorators.stream()
              .map(
                  d ->
                      "@"
                          + d.name()
                          + d.keywords().stream()
                              .map(
                                  k ->
                                      JsonAstParser.getType(k).equals("keyword")
                                              && JsonAstParser.getAttr(k, "arg")
                                                  .getAsString()
                                                  .equals("frozen")
                                              && JsonAstParser.getAttr(
                                                      JsonAstParser.getAttr(k, "value"), "value")
                                                  .getAsBoolean()
                                          ? "frozen=True"
                                          : "")
                              .collect(joining(", ", "(", ")"))
                          + "\n")
              .collect(joining("\n"));

      var fieldsString =
          fields.stream()
              .map(
                  f ->
                      "\n  %s: any%s"
                          .formatted(
                              f.identifier(), f.value().map(v -> " = " + v.toString()).orElse("")))
              .collect(joining());

      var methodsString =
          methodDefs.stream()
              .map(m -> "\n  " + m.toString().replaceAll("\n", "\n  "))
              .collect(joining());

      return "%sclass %s:%s%s"
          .formatted(decoratorString, identifier.name(), fieldsString, methodsString);
    }
  }

  public static class PyObject {
    public final PyClass type;
    public PyDict __dict__ = new PyDict();

    public PyObject(PyClass type) {
      this.type = type;
    }

    /**
     * Calls PyObject method named {@code methodName} with {@code params}.
     *
     * <p>Return type is an array rather than Optional because Optional cannot store null.
     *
     * @param methodName name of PyObject method to call
     * @param params arguments passed to PyObject method
     * @return return value wrapped in an array of 1 element, or empty array if no matching method
     */
    public Object[] callMethod(String methodName, Object... params) {
      Object[] methodParams = new Object[params.length + 1];
      methodParams[0] = this;
      System.arraycopy(params, 0, methodParams, 1, params.length);
      var method = type.instanceMethods.get(methodName);
      if (method == null) {
        return new Object[] {};
      }
      return new Object[] {method.call(methodParams)};
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof PyObject pyOther
          && type == pyOther.type
          && type.isFrozen
          && type.hashMethod.isPresent()) {
        return hashCode() == other.hashCode();
      } else {
        return super.equals(other);
      }
    }

    @Override
    public int hashCode() {
      if (type.hashMethod.isPresent()) {
        return type.hashMethod.get().apply(this);
      } else {
        return System.identityHashCode(this);
      }
    }

    @Override
    public String toString() {
      if (type.strMethod.isPresent()) {
        return type.strMethod.get().apply(this);
      } else {
        return "<%s object at 0x%x>".formatted(type.name, System.identityHashCode(this));
      }
    }
  }

  public static class PyObjects {
    public static String toString(Object value) {
      if (value == null) {
        return "None";
      } else if (value.getClass().isArray()) {
        var out = new StringBuilder("[");
        int length = Array.getLength(value);
        for (int i = 0; i < length; i++) {
          if (i != 0) {
            out.append(", ");
          }
          out.append(toRepr(Array.get(value, i)));
        }
        out.append("]");
        return out.toString();
      } else if (value instanceof PyList pyList) {
        return pyList.getJavaList().stream()
            .map(PyObjects::toRepr)
            .collect(joining(", ", "[", "]"));
      } else if (value instanceof List<?> list) {
        return list.stream().map(PyObjects::toRepr).collect(joining(", ", "[", "]"));
      } else if (value instanceof Boolean bool) {
        return bool ? "True" : "False";
      } else {
        return value.toString();
      }
    }

    public static String toRepr(Object value) {
      if (value instanceof String string) {
        Gson gson =
            new GsonBuilder()
                .setPrettyPrinting() // Optional: for pretty printing
                .disableHtmlEscaping() // Important: to prevent double escaping
                .create();
        return gson.toJson(string);
      } else {
        return PyObjects.toString(value);
      }
    }
  }

  public record ClassLevelMethod(boolean isClassmethod, Function function) {}

  public static class PyClass extends PyObject implements Function {
    public final String name;
    public final Function ctor;
    public final boolean isFrozen;
    public final Map<String, Function> instanceMethods;
    public final Map<String, ClassLevelMethod> classLevelMethods;
    public final Optional<java.util.function.Function<PyObject, Integer>> hashMethod;
    public final Optional<java.util.function.Function<PyObject, String>> strMethod;

    private static PyClass CLASS_TYPE =
        new PyClass(
            "type", params -> null, false, Map.of(), Map.of(), Optional.empty(), Optional.empty());

    public PyClass(
        String name,
        Function ctor,
        boolean isFrozen,
        Map<String, Function> instanceMethods,
        Map<String, ClassLevelMethod> classLevelMethods,
        Optional<java.util.function.Function<PyObject, Integer>> hashMethod,
        Optional<java.util.function.Function<PyObject, String>> strMethod) {
      super(CLASS_TYPE);
      this.name = name;
      this.ctor = ctor;
      this.isFrozen = isFrozen;
      this.instanceMethods = instanceMethods;
      this.classLevelMethods = classLevelMethods;
      this.hashMethod = hashMethod;
      this.strMethod = strMethod;
    }

    @Override
    public Object call(Object... params) {
      return ctor.call(params);
    }

    @Override
    public Object[] callMethod(String methodName, Object... params) {
      var method = classLevelMethods.get(methodName);
      if (method == null) {
        return new Object[] {};
      }
      final Object[] methodParams;
      if (method.isClassmethod()) {
        methodParams = new Object[params.length + 1];
        methodParams[0] = this;
        System.arraycopy(params, 0, methodParams, 1, params.length);
      } else {
        methodParams = params;
      }
      return new Object[] {method.function().call(methodParams)};
    }

    @Override
    public String toString() {
      return "<class '%s'>".formatted(name);
    }
  }

  public record FunctionDef(
      int lineno,
      String enclosingClassName,
      Identifier identifier,
      List<Decorator> decorators,
      List<FunctionArg> args,
      Statement body)
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
    public Object invoke(Context enclosingContext, Object... argValues) {
      if (args.size() != argValues.length) {
        throw new IllegalArgumentException(
            String.format(
                "Invoking function `%s` with %d args but %d required",
                identifier, argValues.length, args.size()));
      }

      var localContext = enclosingContext.createLocalContext(enclosingClassName, identifier.name());
      for (int i = 0; i < args.size(); ++i) {
        var arg = args.get(i);
        var argValue = argValues[i];
        localContext.setVariable(arg.identifier().name(), argValue);
      }
      localContext.exec(body);
      return localContext.returnValue();
    }

    @Override
    public String toString() {
      String decoratorString =
          decorators.stream().map(d -> "@%s\n".formatted(d.name())).collect(joining());
      String bodyString = "  " + body.toString().replaceAll("\n", "\n  ");
      return "%sdef %s(%s):\n%s"
          .formatted(
              decoratorString,
              identifier.name(),
              args.stream().map(a -> a.identifier().name()).collect(joining(", ")),
              bodyString);
    }
  }

  public record FunctionArg(Identifier identifier) {}

  public record IfBlock(
      int lineno, Expression condition, Statement thenBody, Optional<Statement> elseBody)
      implements Statement {
    @Override
    public void exec(Context context) {
      if (context.skipStatement()) {
        return;
      }
      if (convertToBool(condition.eval(context))) {
        context.exec(thenBody);
      } else {
        elseBody.ifPresent(e -> context.exec(e));
      }
    }

    @Override
    public String toString() {
      var out = new StringBuilder("if ");
      out.append(condition.toString());
      out.append(":\n  ");
      out.append(thenBody.toString().replaceAll("\n", "\n  "));
      elseBody.ifPresent(
          e -> {
            out.append("\nelse:\n  ");
            out.append(e.toString().replaceAll("\n", "\n  "));
          });
      return out.toString();
    }
  }

  public record ForBlock(int lineno, Expression vars, Expression iter, Statement body)
      implements Statement {
    @Override
    public void exec(Context context) {
      if (context.skipStatement()) {
        return;
      }
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
        for (var value : getIterable(iter.eval(context))) {
          if (loopVar != null) {
            context.setVariable(loopVar.name(), value);
          } else {
            Assignment.assignTuple(context, loopVars, value);
          }
          context.exec(body);
          if (context.shouldBreak()) {
            break;
          }
        }
      } finally {
        context.exitLoop();
      }
    }

    @Override
    public String toString() {
      return "for %s in %s:\n%s"
          .formatted(vars, iter, body.toString().replaceAll("^", "  ").replaceAll("\n", "\n  "));
    }
  }

  public record WhileBlock(int lineno, Expression condition, Statement body) implements Statement {
    @Override
    public void exec(Context context) {
      if (context.skipStatement()) {
        return;
      }
      try {
        context.enterLoop();
        while (convertToBool(condition.eval(context))) {
          context.exec(body);
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
      Optional<Identifier> exceptionType, Optional<Identifier> exceptionVariable, Statement body) {}

  public record TryBlock(
      int lineno,
      Statement tryBody,
      List<ExceptionHandler> exceptionHandlers,
      Optional<Statement> finallyBlock)
      implements Statement {
    @Override
    public void exec(Context context) {
      if (context.skipStatement()) {
        return;
      }
      try {
        context.exec(tryBody);
      } catch (Exception e) {
        // PyException exists only to prevent all eval/exec/invoke methods from declaring that they
        // throw Exception.  Unwrap the underlying exception here.
        final Object exception;
        if (e instanceof PyException pyException) {
          exception = pyException.thrown;
        } else {
          exception = e;
        }
        boolean handled = false;
        for (var handler : exceptionHandlers) {
          var exceptionType = handler.exceptionType().map(t -> context.getVariable(t.name()));
          if (exceptionType.isEmpty()
              || (exceptionType.get() instanceof PyClass declaredType
                  && exception instanceof PyObject thrownObject
                  && thrownObject.type == declaredType)
              || (exceptionType.get() instanceof JavaClassId javaClassId
                  && javaClassId.clss().isAssignableFrom(exception.getClass()))) {
            handler
                .exceptionVariable()
                .ifPresent(
                    name -> {
                      context.setVariable(name, exception);
                    });
            context.exec(handler.body);
            handled = true;
            break;
          }
        }
        if (!handled) {
          throw new PyException(e);
        }
      } finally {
        finallyBlock.ifPresent(fb -> context.exec(fb));
      }
    }

    @Override
    public String toString() {
      var out = new StringBuilder("try:\n");
      out.append("  " + tryBody.toString().replaceAll("\n", "\n  ") + "\n");
      for (var handler : exceptionHandlers) {
        out.append("except");
        boolean hasExceptionType = handler.exceptionType().isPresent();
        boolean hasExceptionVariable = handler.exceptionVariable().isPresent();
        if (hasExceptionType && hasExceptionVariable) {
          out.append(
              " %s as %s".formatted(handler.exceptionType.get(), handler.exceptionVariable.get()));
        } else if (!hasExceptionType && hasExceptionVariable) {
          out.append(" " + handler.exceptionVariable.get());
        } else if (hasExceptionType && !hasExceptionVariable) {
          out.append(" " + handler.exceptionType.get());
        }
        out.append(":\n");
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
    public final Object thrown;

    public PyException(Object thrown) {
      super(PyObjects.toString(thrown));
      this.thrown = thrown;
    }
  }

  public record RaiseStatement(int lineno, Expression exception) implements Statement {
    @Override
    public void exec(Context context) {
      if (context.skipStatement()) {
        return;
      }
      throw new PyException(exception.eval(context));
    }

    @Override
    public String toString() {
      return String.format("throw %s", exception);
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

    default JsonElement astNode() {
      return JsonNull.INSTANCE;
    }

    default Object eval(Context context) {
      throw new UnsupportedOperationException(
          String.format(
              "Eval for expression %s not implemented: %s", getClass().getSimpleName(), this));
    }
  }

  public record StatementBlock(int lineno, List<Statement> statements) implements Statement {
    @Override
    public void exec(Context context) {
      for (var statement : statements) {
        if (context.skipStatement()) {
          break;
        }
        context.exec(statement);
      }
    }

    @Override
    public String toString() {
      return statements.stream().map(Object::toString).collect(joining("\n"));
    }
  }

  public record ClassAliasAssignment(int lineno, Identifier identifier, Class<?> clss)
      implements Statement {
    @Override
    public void exec(Context context) {}

    @Override
    public String toString() {
      // TODO(maxuser): Format as Python assignment.
      return String.format("import %s as %s", clss.getName(), identifier);
    }
  }

  public static class FrozenInstanceError extends RuntimeException {
    public FrozenInstanceError(String message) {
      super(message);
    }
  }

  public record Assignment(int lineno, Expression lhs, Expression rhs) implements Statement {
    public Assignment {
      // TODO(maxuser): Support destructuring assignment more than one level of identifiers deep.
      if (!(lhs instanceof Identifier
          || lhs instanceof FieldAccess
          || lhs instanceof ArrayIndex
          || (lhs instanceof TupleLiteral tuple
              && tuple.elements().stream().allMatch(Identifier.class::isInstance)))) {
        throw new IllegalArgumentException(
            "Unsupported expression type for lhs of assignment: `%s` (%s)"
                .formatted(lhs, lhs.getClass().getSimpleName()));
      }
    }

    public static void assignTuple(Context context, TupleLiteral lhsTuple, Object rhsValue) {
      List<Identifier> lhsVars = lhsTuple.elements().stream().map(Identifier.class::cast).toList();
      rhsValue = promoteArrayToTuple(rhsValue);
      if (rhsValue instanceof ItemGetter getter && rhsValue instanceof Lengthable lengthable) {
        int lengthToUnpack = lengthable.__len__();
        if (lengthToUnpack != lhsVars.size()) {
          throw new IllegalArgumentException(
              String.format(
                  "Cannot unpack %d values into %d variables: %s",
                  lengthToUnpack, lhsVars.size(), rhsValue));
        }
        for (int i = 0; i < lengthToUnpack; ++i) {
          context.setVariable(lhsVars.get(i).name(), getter.__getitem__(i));
        }
      } else {
        throw new IllegalArgumentException(
            "Cannot unpack value to tuple: " + PyObjects.toString(rhsValue));
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void exec(Context context) {
      if (context.skipStatement()) {
        return;
      }
      Object rhsValue = rhs.eval(context);
      if (lhs instanceof Identifier lhsId) {
        context.setVariable(lhsId, rhsValue);
        return;
      } else if (lhs instanceof FieldAccess lhsFieldAccess) {
        var lhsObject = lhsFieldAccess.object().eval(context);
        if (lhsObject instanceof PyObject pyObject) {
          String fieldName = lhsFieldAccess.field().name();
          if (pyObject.type.isFrozen) {
            throw new FrozenInstanceError(
                "cannot assign to field '%s' of type '%s'"
                    .formatted(fieldName, pyObject.type.name));
          }
          pyObject.__dict__.__setitem__(fieldName, rhsValue);
          return;
        }
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
        throw new IllegalArgumentException(
            "Unsupported subscript assignment to %s (%s)"
                .formatted(array, array.getClass().getSimpleName()));
      }
      throw new IllegalArgumentException(
          "Unsupported expression type for lhs of assignment: `%s` (%s)"
              .formatted(lhs, lhs.getClass().getSimpleName()));
    }

    @Override
    public String toString() {
      if (lhs instanceof Identifier lhsId) {
        return String.format("%s = %s", lhsId.name(), rhs);
      } else if (lhs instanceof ArrayIndex arrayIndex) {
        return String.format("%s[%s] = %s", arrayIndex.array(), arrayIndex.index(), rhs);
      } else {
        return String.format("%s = %s", lhs, rhs);
      }
    }
  }

  public record AugmentedAssignment(int lineno, Expression lhs, Op op, Expression rhs)
      implements Statement {
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
              return Numbers.subtract(lhsNum, rhsNum);
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

    @SuppressWarnings("unchecked")
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
      return String.format("%s %s %s", lhs, op.symbol(), rhs);
    }
  }

  public record Deletion(int lineno, List<Expression> targets) implements Statement {
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
      return String.format("del %s", targets.stream().map(Object::toString).collect(joining(", ")));
    }
  }

  public record GlobalVarDecl(int lineno, List<Identifier> globalVars) implements Statement {
    @Override
    public void exec(Context context) {
      for (var identifier : globalVars) {
        context.declareGlobalVar(identifier.name());
      }
    }

    @Override
    public String toString() {
      return String.format(
          "global %s", globalVars.stream().map(Object::toString).collect(joining(", ")));
    }
  }

  public record ReturnStatement(int lineno, Expression returnValue) implements Statement {
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
        return "return";
      } else {
        return String.format("return %s", returnValue);
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
      return PyObjects.toRepr(value);
    }
  }

  public record Comparison(Expression lhs, Op op, Expression rhs) implements Expression {
    public enum Op {
      IS("is"),
      IS_NOT("is not"),
      EQ("=="),
      LT("<"),
      LT_EQ("<="),
      GT(">"),
      GT_EQ(">="),
      NOT_EQ("!="),
      IN("in"),
      NOT_IN("not in");

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
        case "Is":
          return Op.IS;
        case "IsNot":
          return Op.IS_NOT;
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
        case "NotIn":
          return Op.NOT_IN;
        default:
          throw new UnsupportedOperationException("Unsupported comparison op: " + opName);
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object eval(Context context) {
      var lhsValue = lhs.eval(context);
      var rhsValue = rhs.eval(context);
      switch (op) {
        case IS:
          return lhsValue == rhsValue;
        case IS_NOT:
          return lhsValue != rhsValue;
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
          {
            var result = isIn(lhsValue, rhsValue);
            if (result.isPresent()) {
              return result.get();
            }
          }
        case NOT_IN:
          {
            var result = isIn(lhsValue, rhsValue);
            if (result.isPresent()) {
              return !result.get();
            }
          }
      }
      throw new UnsupportedOperationException(
          String.format("Comparison op not supported: %s %s %s", lhs, op.symbol(), rhs));
    }

    private static final Optional<Boolean> isIn(Object lhsValue, Object rhsValue) {
      if (rhsValue instanceof Collection<?> collection) {
        return Optional.of(collection.contains(lhsValue));
      } else if (rhsValue instanceof ItemContainer container) {
        return Optional.of(container.__contains__(lhsValue));
      } else if (rhsValue instanceof List list) {
        return Optional.of(list.contains(lhsValue));
      } else if (rhsValue instanceof Map map) {
        return Optional.of(map.containsKey(lhsValue));
      } else if (lhsValue instanceof String lhsStr && rhsValue instanceof String rhsStr) {
        return Optional.of(rhsStr.contains(lhsStr));
      }
      return Optional.empty();
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
          return !convertToBool(value);
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
          } else if (lhsValue instanceof String lhsString && rhsValue instanceof String rhsString) {
            return lhsString + rhsString;
          }
          if (lhsValue instanceof PyList pyList) {
            lhsValue = pyList.getJavaList();
          }
          if (rhsValue instanceof PyList pyList) {
            rhsValue = pyList.getJavaList();
          }
          if (lhsValue instanceof List lhsList && rhsValue instanceof List rhsList) {
            @SuppressWarnings("unchecked")
            var newList = new PyList(new ArrayList<Object>(lhsList));
            newList.__iadd__(rhsList);
            return newList;
          }
          break;
        case SUB:
          return Numbers.subtract((Number) lhsValue, (Number) rhsValue);
        case MUL:
          if (lhsValue instanceof String lhsString && rhsValue instanceof Integer rhsInt) {
            return lhsString.repeat(rhsInt);
          } else if (lhsValue instanceof Integer lhsInt && rhsValue instanceof String rhsString) {
            return rhsString.repeat(lhsInt);
          } else {
            return Numbers.multiply((Number) lhsValue, (Number) rhsValue);
          }
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
      return String.format("(%s %s %s)", lhs, op.symbol(), rhs);
    }
  }

  public record SliceExpression(
      Optional<Expression> lower, Optional<Expression> upper, Optional<Expression> step)
      implements Expression {
    @Override
    public Object eval(Context context) {
      try {
        return new SliceValue(
            lower.map(s -> (Integer) s.eval(context)),
            upper.map(s -> (Integer) s.eval(context)),
            step.map(s -> (Integer) s.eval(context)));
      } catch (ClassCastException e) {
        var string =
            Stream.of(lower, upper, step)
                .map(x -> x.map(Object::toString).orElse(""))
                .collect(joining(":", "[", "]"));
        throw new RuntimeException("Slice indices must be integers but got: %s".formatted(string));
      }
    }
  }

  public record SliceValue(
      Optional<Integer> lower, Optional<Integer> upper, Optional<Integer> step) {
    public ResolvedSliceIndices resolveIndices(int sequenceLength) {
      int normLower = lower.map(n -> n < 0 ? sequenceLength + n : n).orElse(0);
      int normUpper = upper.map(n -> n < 0 ? sequenceLength + n : n).orElse(sequenceLength);
      return new ResolvedSliceIndices(normLower, normUpper, step.orElse(1));
    }

    public static int resolveIndex(int i, int length) {
      return i < 0 ? length + i : i;
    }
  }

  /** Slice indices resolved for a particular length sequence to avoid negative or empty values. */
  public record ResolvedSliceIndices(int lower, int upper, int step) {
    public int length() {
      return upper - lower;
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
        if (indexValue instanceof SliceValue sliceValue) {
          var slice = sliceValue.resolveIndices(Array.getLength(arrayValue));
          Object copiedArray =
              Array.newInstance(arrayValue.getClass().getComponentType(), slice.length());
          System.arraycopy(arrayValue, slice.lower(), copiedArray, 0, slice.length());
          return copiedArray;
        } else {
          int intKey =
              SliceValue.resolveIndex(
                  ((Number) indexValue).intValue(), Array.getLength(arrayValue));
          return Array.get(arrayValue, intKey);
        }
      } else if (arrayValue instanceof List list) {
        if (indexValue instanceof SliceValue sliceValue) {
          var slice = sliceValue.resolveIndices(list.size());
          return list.subList(slice.lower(), slice.upper());
        } else {
          int intKey = SliceValue.resolveIndex(((Number) indexValue).intValue(), list.size());
          return list.get(intKey);
        }
      } else if (arrayValue instanceof Map map) {
        return map.get(indexValue);
      } else if (arrayValue instanceof String string) {
        if (indexValue instanceof SliceValue sliceValue) {
          var slice = sliceValue.resolveIndices(string.length());
          return string.substring(slice.lower(), slice.upper());
        } else {
          return String.valueOf(
              string.charAt(SliceValue.resolveIndex((Integer) indexValue, string.length())));
        }
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
        Class<?> clss,
        java.util.function.Function<Class<?>, T[]> executableGetter,
        Predicate<T> filter,
        Object[] paramValues,
        boolean traverseSuperclasses) {
      if (traverseSuperclasses && !isPublic(clss)) {
        for (var iface : clss.getInterfaces()) {
          var viableExecutable =
              findBestMatchingExecutable(iface, executableGetter, filter, paramValues, true);
          if (viableExecutable.isPresent()) {
            return viableExecutable;
          }
        }
        return findBestMatchingExecutable(
            clss.getSuperclass(), executableGetter, filter, paramValues, true);
      }

      Optional<T> bestExecutable = Optional.empty();
      int bestScore = 0; // Zero means that no viable executable has been found.
      for (T executable : executableGetter.apply(clss)) {
        if (filter.test(executable)) {
          int score = getTypeCheckScore(executable.getParameterTypes(), paramValues);
          if (score > bestScore) {
            bestScore = score;
            bestExecutable = Optional.of(executable);
          }
        }
      }
      if (bestScore == 0) {}
      return bestExecutable;
    }

    private static final int PUBLIC_MODIFIER = 0x1;

    private static boolean isPublic(Class<?> clss) {
      // See defintion of modifiers in the JVM spec:
      // https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.1-200-E.1
      return (clss.getModifiers() & PUBLIC_MODIFIER) != 0;
    }

    /**
     * Computes a score for how well {@code paramValues} matches the {@code formalParamTypes}.
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
        // Allow implementations of Function to be passed to params expecting an interface, but
        // don't boost the score for this iffy conversion.
        if (Function.class.isAssignableFrom(valueType) && type.isInterface()) {
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

  public record ListComprehension(
      Expression transform, Expression target, Expression iter, List<Expression> ifs)
      implements Expression {
    @Override
    public Object eval(Context context) {
      var localContext = context.createLocalContext();
      var list = new PyList();
      // TODO(maxuser): Share portions of impl with ForBlock::exec.
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
      for (var value : getIterable(iter.eval(localContext))) {
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

  public interface ItemGetter extends Lengthable {
    Object __getitem__(Object key);

    default int resolveIndex(int i) {
      return SliceValue.resolveIndex(i, __len__());
    }
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
      implements Iterable<Object>, ItemGetter, ItemSetter, ItemContainer, ItemDeleter {
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
      return list.stream().map(PyObjects::toString).collect(joining(", ", "[", "]"));
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

    @Override
    public Object __getitem__(Object key) {
      if (key instanceof Integer i) {
        return list.get(i);
      } else if (key instanceof SliceValue sliceValue) {
        var slice = sliceValue.resolveIndices(list.size());
        // TODO(maxuser): SliceValue.step not supported.
        return new PyList(list.subList(slice.lower(), slice.upper()));
      }
      throw new IllegalArgumentException(
          String.format(
              "list indices must be integers or slices of integers, not %s (%s)",
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

  // TODO(maxuser): Enforce immutability of tuples so that `t[0] = 0` is illegal.
  public static class PyTuple implements Iterable<Object>, ItemGetter, ItemContainer {
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
    public int hashCode() {
      return Arrays.hashCode(array);
    }

    @Override
    public Iterator<Object> iterator() {
      return Arrays.stream(array).iterator();
    }

    @Override
    public String toString() {
      return array.length == 1
          ? String.format("(%s,)", PyObjects.toString(array[0]))
          : Arrays.stream(array).map(PyObjects::toString).collect(joining(", ", "(", ")"));
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

    @Override
    public Object __getitem__(Object key) {
      if (key instanceof Integer i) {
        return array[i];
      } else if (key instanceof SliceValue sliceValue) {
        var slice = sliceValue.resolveIndices(array.length);
        // TODO(maxuser): SliceValue.step not supported.
        return new PyTuple(Arrays.copyOfRange(array, slice.lower(), slice.upper()));
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
      return params -> {
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
      return values.stream().map(v -> PyObjects.toString(v.eval(context))).collect(joining());
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
      implements Iterable<Object>, ItemGetter, ItemSetter, ItemContainer, ItemDeleter {
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

    public Iterable<Object> keys() {
      return map.keySet();
    }

    public Iterable<Object> values() {
      return map.values();
    }

    @Override
    public int __len__() {
      return map.size();
    }

    public Object get(Object key) {
      return map.get(key);
    }

    public Object setdefault(Object key) {
      return setdefault(key, null);
    }

    public Object setdefault(Object key, Object defaultValue) {
      if (map.containsKey(key)) {
        return map.get(key);
      } else {
        map.put(key, defaultValue);
        return defaultValue;
      }
    }

    @Override
    public Object __getitem__(Object key) {
      var value = map.getOrDefault(key, NOT_FOUND);
      if (value == NOT_FOUND) {
        throw new NoSuchElementException("Key not found: " + PyObjects.toString(key));
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
        out.append(PyObjects.toString(entry.getKey()));
        out.append(": ");
        out.append(PyObjects.toString(entry.getValue()));
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
    public Object call(Object... params) {
      var cacheKey = ExecutableCacheKey.forConstructor(clss, params);
      Optional<Constructor<?>> matchedCtor =
          executableCache
              .computeIfAbsent(
                  cacheKey,
                  ignoreKey ->
                      TypeChecker.findBestMatchingExecutable(
                          clss,
                          Class<?>::getConstructors,
                          c -> true,
                          params,
                          /* traverseSuperclasses= */ false))
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
                                : String.format(
                                    "(%s) %s", v.getClass().getName(), PyObjects.toRepr(v)))
                    .collect(joining(", "))));
      }
    }
  }

  public interface Function {
    Object call(Object... params);

    static void expectNumParams(Object[] params, int n, Object message) {
      if (params.length != n) {
        throw new IllegalArgumentException(
            String.format(
                "Expected %d params but got %d for function: %s", n, params.length, message));
      }
    }

    default void expectMinParams(Object[] params, int n) {
      if (params.length < n) {
        throw new IllegalArgumentException(
            String.format(
                "Expected at least %d param%s but got %d for function: %s",
                n, n == 1 ? "" : "s", params.length, this));
      }
    }

    default void expectMaxParams(Object[] params, int n) {
      if (params.length > n) {
        throw new IllegalArgumentException(
            String.format(
                "Expected at most %d param%s but got %d for function: %s",
                n, n == 1 ? "" : "s", params.length, this));
      }
    }

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
    public Object call(Object... params) {
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
    public Object call(Object... params) {
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
    public Object call(Object... params) {
      expectNumParams(params, 1);
      return PyObjects.toString(params[0]);
    }
  }

  public static class BoolFunction implements Function {
    @Override
    public Object call(Object... params) {
      expectNumParams(params, 1);
      return convertToBool(params[0]);
    }
  }

  public static class LenFunction implements Function {
    @Override
    public Object call(Object... params) {
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

  public static class TupleFunction implements Function {
    @Override
    public Object call(Object... params) {
      expectMaxParams(params, 1);
      if (params.length == 0) {
        return new PyTuple(new Object[] {});
      } else {
        Iterable<?> iterable = getIterable(params[0]);
        return new PyTuple(StreamSupport.stream(iterable.spliterator(), false).toArray());
      }
    }
  }

  public static class ListFunction implements Function {
    @Override
    public Object call(Object... params) {
      expectMaxParams(params, 1);
      if (params.length == 0) {
        return new PyList();
      } else {
        @SuppressWarnings("unchecked")
        Iterable<Object> iterable = (Iterable<Object>) getIterable(params[0]);
        // Stream.toList() returns immutable list, so using Stream.collect(toList()) for mutable
        // List.
        return new PyList(StreamSupport.stream(iterable.spliterator(), false).collect(toList()));
      }
    }
  }

  public record PrintFunction(Context context) implements Function {
    @Override
    public Object call(Object... params) {
      @SuppressWarnings("unchecked")
      var out = (Consumer<String>) context.getVariable("__stdout__");
      out.accept(Arrays.stream(params).map(PyObjects::toString).collect(joining(" ")));
      return null;
    }
  }

  public record TypeFunction(Map<ExecutableCacheKey, Optional<Executable>> executableCache)
      implements Function {
    @Override
    public Object call(Object... params) {
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
    public Object call(Object... params) {
      return new RangeIterable(params);
    }
  }

  public static class EnumerateFunction implements Function {
    @Override
    public Object call(Object... params) {
      if (params.length == 0 || params.length > 2) {
        throw new IllegalArgumentException(
            "Expected 1 or 2 params but got %d for function: enumerate".formatted(params.length));
      }
      int start = params.length > 1 ? (Integer) params[1] : 0;
      return new EnumerateIterable(getIterable(params[0]), start);
    }
  }

  public static Iterable<?> getIterable(Object object) {
    object = promoteArrayToTuple(object);
    if (object instanceof String string) {
      return new IterableString(string);
    } else {
      return (Iterable<?>) object;
    }
  }

  /**
   * Promotes {@code object} to {@code PyTuple} if it's an array, or else returns {@code object}.
   */
  public static Object promoteArrayToTuple(Object object) {
    if (object.getClass().isArray()) {
      if (object instanceof Object[] objectArray) {
        return new PyTuple(objectArray);
      } else {
        int length = Array.getLength(object);
        Object[] array = new Object[length];
        for (int i = 0; i < length; i++) {
          array[i] = Array.get(object, i);
        }
        return new PyTuple(array);
      }
    } else {
      return object;
    }
  }

  public record IterableString(String string) implements Iterable<String> {
    @Override
    public Iterator<String> iterator() {
      var list = new ArrayList<String>();
      for (int i = 0; i < string.length(); ++i) {
        list.add(String.valueOf(string.charAt(i)));
      }
      return list.iterator();
    }
  }

  public static class AbsFunction implements Function {
    @Override
    public Object call(Object... params) {
      expectNumParams(params, 1);
      var num = (Number) params[0];
      return num.doubleValue() > 0. ? num : Numbers.negate(num);
    }
  }

  public static class RoundFunction implements Function {
    @Override
    public Object call(Object... params) {
      expectNumParams(params, 1);
      var num = (Number) params[0];
      return Math.round(num.floatValue());
    }
  }

  public static class MinFunction implements Function {
    @Override
    public Object call(Object... params) {
      if (params.length == 0) {
        throw new IllegalArgumentException("min expected at least 1 argument, got 0");
      }
      var currentMin = (Number) params[0];
      for (var value : params) {
        var num = (Number) value;
        if (Numbers.lessThan(num, currentMin)) {
          currentMin = num;
        }
      }
      return currentMin;
    }
  }

  public static class MaxFunction implements Function {
    @Override
    public Object call(Object... params) {
      if (params.length == 0) {
        throw new IllegalArgumentException("max expected at least 1 argument, got 0");
      }
      var currentMax = (Number) params[0];
      for (var value : params) {
        var num = (Number) value;
        if (Numbers.greaterThan(num, currentMax)) {
          currentMax = num;
        }
      }
      return currentMax;
    }
  }

  public static class OrdFunction implements Function {
    @Override
    public Object call(Object... params) {
      expectNumParams(params, 1);
      if (params[0] instanceof String string && string.length() == 1) {
        return (int) string.charAt(0);
      } else {
        throw new IllegalArgumentException(
            "ord() expected string of length 1, but got %s".formatted(params[0]));
      }
    }
  }

  public static class ChrFunction implements Function {
    @Override
    public Object call(Object... params) {
      expectNumParams(params, 1);
      if (params[0] instanceof Integer codePointInteger) {
        int codePoint = codePointInteger;
        if (codePoint < 0 || codePoint > Character.MAX_CODE_POINT) {
          throw new IllegalArgumentException("chr(): Invalid code point: " + codePoint);
        }

        if (Character.isBmpCodePoint(codePoint)) {
          return String.valueOf((char) codePoint);
        } else {
          return String.valueOf(Character.toChars(codePoint));
        }
      } else {
        throw new IllegalArgumentException(
            "chr() requires an integer but got %s".formatted(params[0]));
      }
    }
  }

  public record FunctionCall(int lineno, Expression method, List<Expression> params)
      implements Expression {
    @Override
    public Object eval(Context context) {
      var caller = method.eval(context);
      Object[] paramValues = params.stream().map(p -> p.eval(context)).toArray(Object[]::new);
      if (caller instanceof Function function) {
        try {
          context.enterFunction(lineno);
          return function.call(paramValues);
        } finally {
          context.leaveFunction();
        }
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

  public static class EnumerateIterable implements Iterable<PyTuple> {
    private final Iterable<?> iterable;
    private final int start;

    public EnumerateIterable(Iterable<?> iterable, int start) {
      this.iterable = iterable;
      this.start = start;
    }

    public Iterator<PyTuple> iterator() {
      var iter = iterable.iterator();
      return new Iterator<PyTuple>() {
        private int pos = start;

        @Override
        public boolean hasNext() {
          return iter.hasNext();
        }

        @Override
        public PyTuple next() {
          var next = iter.next();
          return new PyTuple(new Object[] {pos++, next});
        }
      };
    }
  }

  public record BoundMethodExpression(
      Expression object,
      Identifier methodId,
      Map<ExecutableCacheKey, Optional<Executable>> executableCache)
      implements Expression {
    @Override
    public Object eval(Context context) {
      return new BoundMethod(object.eval(context), methodId.name(), executableCache, object);
    }

    @Override
    public String toString() {
      return String.format("%s.%s", object, methodId);
    }
  }

  public record BoundMethod(
      Object object,
      String methodName,
      Map<ExecutableCacheKey, Optional<Executable>> executableCache,
      Expression objectExpression)
      implements Function {
    @Override
    public Object call(Object... params) {
      Object[] pyObjectMethodResult;
      if (object instanceof PyObject pyObject
          && (pyObjectMethodResult = pyObject.callMethod(methodName, params)).length == 1) {
        return pyObjectMethodResult[0];
      }

      final boolean isStaticMethod;
      final Class<?> clss;
      if (object instanceof JavaClassId classId) {
        isStaticMethod = true;
        clss = classId.clss();
      } else {
        if (object == null) {
          throw new NullPointerException(
              "Cannot invoke method \"%s.%s()\" because \"%s\" is null"
                  .formatted(objectExpression, methodName, objectExpression));
        }
        isStaticMethod = false;
        clss = object.getClass();
      }

      Object[] mappedParams = mapMethodParams(clss, isStaticMethod, methodName, params);
      String mappedMethodName = mapMethodName(clss, isStaticMethod, methodName);
      var cacheKey = ExecutableCacheKey.forMethod(clss, isStaticMethod, methodName, mappedParams);
      Optional<Method> matchedMethod =
          executableCache
              .computeIfAbsent(
                  cacheKey,
                  ignoreKey ->
                      TypeChecker.findBestMatchingExecutable(
                          clss,
                          Class<?>::getMethods,
                          m ->
                              Modifier.isStatic(m.getModifiers()) == isStaticMethod
                                  && m.getName().equals(mappedMethodName),
                          mappedParams,
                          /* traverseSuperclasses= */ true))
              .map(Method.class::cast);
      if (matchedMethod.isPresent()) {
        FunctionalParamProxy.promoteFunctionalParams(matchedMethod.get(), mappedParams);
        try {
          return matchedMethod.get().invoke(isStaticMethod ? null : object, mappedParams);
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
                                : "(%s) %s".formatted(v.getClass().getName(), PyObjects.toRepr(v)))
                    .collect(joining(", "))));
      }
    }
  }

  private static Object[] mapMethodParams(
      Class<?> clss, boolean isStaticMethod, String methodName, Object[] params) {
    if (clss == String.class && !isStaticMethod) {
      if (methodName.equals("split") && params.length == 0) {
        return new Object[] {"\\s+"};
      }
    }
    return params;
  }

  private static String mapMethodName(Class<?> clss, boolean isStaticMethod, String methodName) {
    if (clss == String.class && !isStaticMethod) {
      if (methodName.equals("startswith")) {
        return "startsWith";
      } else if (methodName.equals("endswith")) {
        return "endsWith";
      }
    }
    return methodName;
  }

  public static class FunctionalParamProxy implements InvocationHandler {

    private final Function function;

    public FunctionalParamProxy(Function function) {
      this.function = function;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      return function.call(args);
    }

    public static void promoteFunctionalParams(Executable executable, Object[] params) {
      for (int i = 0; i < params.length; ++i) {
        var param = params[i];
        Class<?> functionalParamType;
        if (param instanceof Function function
            && isFunctionalInterface(functionalParamType = executable.getParameterTypes()[i])) {
          params[i] =
              Proxy.newProxyInstance(
                  functionalParamType.getClassLoader(),
                  new Class<?>[] {functionalParamType},
                  new FunctionalParamProxy(function));
        }
      }
    }
  }

  private static boolean isFunctionalInterface(Class<?> clss) {
    if (!clss.isInterface()) {
      return false;
    }
    long abstractMethodCount =
        Arrays.stream(clss.getMethods())
            .filter(m -> java.lang.reflect.Modifier.isAbstract(m.getModifiers()))
            .filter(m -> !m.isDefault())
            .count();

    return abstractMethodCount == 1;
  }

  public record FieldAccess(Expression object, Identifier field) implements Expression {
    @Override
    public Object eval(Context context) {
      // TODO(maxuser): Support references to static inner classes and enum values.
      var objectValue = object.eval(context);
      if (objectValue instanceof PyObject pyObject) {
        return pyObject.__dict__.__contains__(field.name())
            ? pyObject.__dict__.__getitem__(field.name())
            : pyObject.type.__dict__.__getitem__(field.name());
      }

      final boolean isClass;
      final Class<?> objectClass;
      if (objectValue instanceof JavaClassId javaClassId) {
        isClass = true;
        objectClass = javaClassId.clss();
      } else {
        if (objectValue == null) {
          throw new NullPointerException(
              "Cannot get field \"%s.%s\" because \"%s\" is null".formatted(object, field, object));
        }
        isClass = false;
        objectClass = objectValue.getClass();
      }

      try {
        var fieldAccess = objectClass.getField(field.name());
        return fieldAccess.get(isClass ? null : objectValue);
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

    // TODO(maxuser): Organize the global contextual state into a separate object shared by all the
    // contexts.
    private final Context globals;
    private final Context enclosingContext;
    private final ClassMethodName classMethodName;
    private final List<Statement> globalStatements; // set only for global context
    // NOTE: globalCallStack does not support multithreaded scripts.
    private final Deque<CallSite> globalCallStack; // set only for global context
    private String globalScriptFilename; // set only for global context
    private Set<String> globalVarNames = null;
    private final Map<String, Object> vars = new HashMap<>();
    private Object returnValue;
    private boolean returned = false;
    private int loopDepth = 0;
    private boolean breakingLoop = false;

    private record ClassMethodName(String type, String method) {}

    private record CallSite(ClassMethodName classMethodName, int lineno) {}

    private Context() {
      globals = this;
      enclosingContext = null;
      classMethodName = new ClassMethodName("<>", "<>");
      globalStatements = new ArrayList<>();
      globalScriptFilename = "<stdin>";
      globalCallStack = new ArrayDeque<>();
    }

    private Context(Context globals, Context enclosingContext) {
      this(globals, enclosingContext, enclosingContext.classMethodName);
    }

    private Context(Context globals, Context enclosingContext, ClassMethodName classMethodName) {
      this.globals = globals;
      this.enclosingContext = enclosingContext == globals ? null : enclosingContext;
      this.classMethodName = classMethodName;
      this.globalStatements = null; // Defined only for global context.
      this.globalScriptFilename = null; // Defined only for global context.
      this.globalCallStack = null; // Defined only for global context.
    }

    public void setScriptFilename(String filename) {
      if (this != globals) {
        throw new IllegalArgumentException("Cannot set script filename for non-global context");
      }
      globalScriptFilename = filename;
    }

    public static Context createGlobals(
        Map<ExecutableCacheKey, Optional<Executable>> executableCache) {
      var context = new Context();
      context.setVariable("__stdout__", (Consumer<String>) System.out::println);
      context.setVariable("math", new JavaClassId(math.class, executableCache));
      context.setVariable("int", new IntFunction());
      context.setVariable("float", new FloatFunction());
      context.setVariable("str", new StrFunction());
      context.setVariable("bool", new BoolFunction());
      context.setVariable("len", new LenFunction());
      context.setVariable("tuple", new TupleFunction());
      context.setVariable("list", new ListFunction());
      context.setVariable("print", new PrintFunction(context));
      context.setVariable("type", new TypeFunction(executableCache));
      context.setVariable("range", new RangeFunction());
      context.setVariable("enumerate", new EnumerateFunction());
      context.setVariable("abs", new AbsFunction());
      context.setVariable("round", new RoundFunction());
      context.setVariable("min", new MinFunction());
      context.setVariable("max", new MaxFunction());
      context.setVariable("ord", new OrdFunction());
      context.setVariable("chr", new ChrFunction());
      return context;
    }

    public void enterFunction(int lineno) {
      globals.globalCallStack.push(new CallSite(classMethodName, lineno));
    }

    public void leaveFunction() {
      globals.globalCallStack.pop();
    }

    public Context createLocalContext(String enclosingClassName, String enclosingMethodName) {
      return new Context(
          globals, this, new ClassMethodName(enclosingClassName, enclosingMethodName));
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
        globals.exec(statement);
      }
      globalStatements.clear();
    }

    /** Call this instead of Statement.exec directly for proper attribution with exceptions. */
    public void exec(Statement statement) {
      try {
        statement.exec(this);
      } catch (Exception e) {
        var stackTrace = e.getStackTrace();
        if (stackTrace.length > 0
            && !stackTrace[0].getFileName().equals(globals.globalScriptFilename)) {
          var scriptStack = new ArrayList<CallSite>();
          scriptStack.add(new CallSite(classMethodName, statement.lineno()));
          scriptStack.addAll(globals.globalCallStack);

          var newStackTrace = new StackTraceElement[stackTrace.length + scriptStack.size()];
          for (int i = 0; i < scriptStack.size(); ++i) {
            newStackTrace[i] =
                new StackTraceElement(
                    scriptStack.get(i).classMethodName().type,
                    scriptStack.get(i).classMethodName().method,
                    globals.globalScriptFilename,
                    scriptStack.get(i).lineno());
          }
          System.arraycopy(stackTrace, 0, newStackTrace, scriptStack.size(), stackTrace.length);
          e.setStackTrace(newStackTrace);
        }
        throw e;
      }
    }

    public void setBoundFunction(BoundFunction boundFunction) {
      setVariable(boundFunction.function().identifier().name(), boundFunction);
    }

    public BoundFunction getBoundFunction(String name) {
      return (BoundFunction) getVariable(name);
    }

    public Context setVariable(String name, Object value) {
      if (this != globals && globalVarNames != null && globalVarNames.contains(name)) {
        globals.vars.put(name, value);
      } else {
        vars.put(name, value);
      }
      return this;
    }

    public Context setVariable(Identifier id, Object value) {
      return setVariable(id.name(), value);
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
