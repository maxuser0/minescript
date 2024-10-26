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
              return Statement.createBlock(
                  StreamSupport.stream(body.spliterator(), false)
                      .map(elem -> parseStatements(elem))
                      .collect(toList()));
            }
          }
        case "Assign":
          return Statement.createAssignment(
              new Assignment(
                  getId(getTargets(element).get(0)), parseExpression(getAttr(element, "value"))));
        case "Return":
          return Statement.createReturn(parseExpression(getAttr(element, "value")));
      }
      throw new IllegalArgumentException("Unknown statement type: " + element.toString());
    }

    public static Expression parseExpression(JsonElement element) {
      String type = getType(element);
      switch (type) {
        case "BinOp":
          return Expression.createBinaryOp(
              new BinaryOp(
                  parseExpression(getAttr(element, "left")),
                  parseBinaryOp(getType(getAttr(element, "op"))),
                  parseExpression(getAttr(element, "right"))));
        case "Name":
          return Expression.createIdentifier(getId(element));
        case "Constant":
          return parseConstant(getAttr(element, "value"));
        case "Call":
          return Expression.createMethodCall(
              new MethodCall(
                  parseExpression(getAttr(element, "func")),
                  StreamSupport.stream(
                          getAttr(element, "args").getAsJsonArray().spliterator(), false)
                      .map(elem -> parseExpression(elem))
                      .collect(toList())));
        case "Attribute":
          // TODO(maxuser): Don't assume `value.attr` can be concatenated into a single identifier.
          return Expression.createIdentifier(
              new Identifier(
                  String.format(
                      "%s.%s",
                      getAttr(getAttr(element, "value"), "id").getAsString(),
                      getAttr(element, "attr").getAsString())));
        case "Subscript":
          return Expression.createArrayIndex(
              new ArrayIndex(
                  parseExpression(getAttr(element, "value")),
                  parseExpression(getAttr(element, "slice"))));
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
          return Expression.createInteger(number.intValue());
        } else {
          return Expression.createDouble(number.doubleValue());
        }
      } else if (primitive.isBoolean()) {
        return Expression.createBool(primitive.getAsBoolean());
      } else if (primitive.isString()) {
        return Expression.createString(primitive.getAsString());
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

  public static class Statement {
    public enum Type {
      STATEMENT_BLOCK,
      EXPRESSION,
      ASSIGNMENT,
      AUGMENTED_ASSIGNMENT,
      IF_BLOCK,
      WHILE_BLOCK,
      FOR_BLOCK,
      BREAK,
      CONTINUE,
      RETURN,
      THROW,
      TRY_BLOCK,
      RESOURCE_BLOCK
    }

    private final Type type;
    private final Object data;

    private Statement(Type type, Object data) {
      this.type = type;
      this.data = data;
    }

    public Type type() {
      return type;
    }

    public static Statement createBlock(List<Statement> statements) {
      return new Statement(Type.STATEMENT_BLOCK, statements);
    }

    public static Statement createExpression(Expression expression) {
      return new Statement(Type.EXPRESSION, expression);
    }

    public static Statement createAssignment(Assignment assignment) {
      return new Statement(Type.ASSIGNMENT, assignment);
    }

    public static Statement createAugmentedAssignment(AugmentedAssignment augmentedAssignment) {
      return new Statement(Type.AUGMENTED_ASSIGNMENT, augmentedAssignment);
    }

    public static Statement createIfBlock(IfBlock ifBlock) {
      return new Statement(Type.IF_BLOCK, ifBlock);
    }

    public static Statement createWhileBlock(WhileBlock whileBlock) {
      return new Statement(Type.WHILE_BLOCK, whileBlock);
    }

    public static Statement createForBlock(ForBlock forBlock) {
      return new Statement(Type.FOR_BLOCK, forBlock);
    }

    public static Statement createBreak() {
      return new Statement(Type.BREAK, null);
    }

    public static Statement createContinue() {
      return new Statement(Type.CONTINUE, null);
    }

    public static Statement createReturn(Expression returnValue) {
      return new Statement(Type.RETURN, returnValue);
    }

    public static Statement createThrow(Expression exception) {
      return new Statement(Type.THROW, exception);
    }

    public static Statement createTryBlock(TryBlock tryBlock) {
      return new Statement(Type.TRY_BLOCK, tryBlock);
    }

    public static Statement createResourceBlock(ResourceBlock resourceBlock) {
      return new Statement(Type.RESOURCE_BLOCK, resourceBlock);
    }

    public void eval(Context context) {
      switch (type) {
        case STATEMENT_BLOCK:
          {
            var statements = (List<Statement>) data;
            for (var statement : statements) {
              statement.eval(context);
            }
            return;
          }

        case ASSIGNMENT:
          {
            var assign = (Assignment) data;
            context.setVariable(assign.lhs(), assign.rhs().eval(context));
            return;
          }

        case RETURN:
          {
            var returnExpression = (Expression) data;
            context.setOutput(returnExpression.eval(context));
            return;
          }

        case EXPRESSION:
        case AUGMENTED_ASSIGNMENT:
        case IF_BLOCK:
        case WHILE_BLOCK:
        case FOR_BLOCK:
        case BREAK:
        case CONTINUE:
        case THROW:
        case TRY_BLOCK:
        case RESOURCE_BLOCK:
      }
      throw new IllegalArgumentException("Statement type not implemented: " + type.toString());
    }

    @Override
    public String toString() {
      switch (type) {
        case STATEMENT_BLOCK:
          return ((List<Statement>) data)
              .stream()
                  .map(Object::toString)
                  .map(s -> "  " + s)
                  .collect(joining("\n", "{\n", "\n}"));

        case RETURN:
          return String.format("return %s;", data.toString());

        case ASSIGNMENT:
          {
            var assign = (Assignment) data;
            return String.format("%s = %s;", assign.lhs().name(), assign.rhs());
          }

        case EXPRESSION:
        case AUGMENTED_ASSIGNMENT:
        case BREAK:
        case CONTINUE:
        case THROW:
          return data.toString() + ";";

        case IF_BLOCK:
        case WHILE_BLOCK:
        case FOR_BLOCK:
        case TRY_BLOCK:
        case RESOURCE_BLOCK:
        default:
          return data.toString();
      }
    }
  }

  public record IfBlock(Expression ifCondition, Statement ifBody, Statement elseBody) {}

  public record WhileBlock(Expression condition, Statement body) {}

  public record ForBlock(Expression vars, Expression iterator, Statement body) {}

  public record Identifier(String name) {}

  public record ExceptionHandler(Identifier exceptionType, Identifier exceptionVariable) {}

  public record TryBlock(
      Statement tryBody, List<ExceptionHandler> exceptionHandlers, Statement finallyBlock) {}

  public record ResourceBlock(Expression resource, Statement body) {}

  public static class Expression {
    public enum Type {
      NULL,
      CONST_DOUBLE,
      CONST_INT,
      CONST_BOOL,
      CONST_STRING,
      IDENTIFIER,
      UNARY_OP,
      BINARY_OP,
      ARRAY_INDEX,
      CAST,
      CTOR_CALL,
      FIELD_ACCESS,
      METHOD_CALL
    }

    public static final Expression NULL = new Expression(Type.NULL, null);

    private final Type type;
    private final Object data;

    private Expression(Type type, Object data) {
      this.type = type;
      this.data = data;
    }

    public Type type() {
      return type;
    }

    public Object data() {
      return data;
    }

    public Object eval(Context context) {
      switch (type) {
        case NULL:
          return null;
        case CONST_DOUBLE:
          return data;
        case CONST_INT:
          return data;
        case CONST_BOOL:
          return data;
        case CONST_STRING:
          return data;
        case IDENTIFIER:
          return context.getVariable((Identifier) data);
        case UNARY_OP:
          {
            var op = (UnaryOp) data;
            return op.eval(context);
          }
        case BINARY_OP:
          {
            var op = (BinaryOp) data;
            return op.eval(context);
          }
        case ARRAY_INDEX:
          {
            // TODO(maxuser): Distinguish between array[index] being on lhs vs rhs of assignment.
            var arrayIndex = (ArrayIndex) data;
            var array = arrayIndex.array().eval(context);
            var index = arrayIndex.index().eval(context);
            if (array == null || index == null) {
              throw new NullPointerException(
                  String.format(
                      "%s=%s, %s=%s in %s",
                      arrayIndex.array(), array, arrayIndex.index(), index, this));
            }
            if (array instanceof Object[] objectArray) {
              return objectArray[((Number) index).intValue()];
            } else if (array instanceof int[] intArray) {
              return intArray[((Number) index).intValue()];
            } else if (array instanceof long[] longArray) {
              return longArray[((Number) index).intValue()];
            } else if (array instanceof float[] floatArray) {
              return floatArray[((Number) index).intValue()];
            } else if (array instanceof double[] doubleArray) {
              return doubleArray[((Number) index).intValue()];
            } else if (array instanceof List list) {
              return list.get(((Number) index).intValue());
            } else if (array instanceof Map map) {
              return map.get(index);
            }
            break;
          }
        case METHOD_CALL:
          {
            var call = (MethodCall) data;
            if (call.method().type() == Expression.Type.IDENTIFIER) {
              var methodId = (Identifier) call.method().data();
              switch (methodId.name()) {
                case "math.sqrt":
                  // TODO(maxuser): Check that there's exactly 1 param.
                  var num = (Number) call.params.get(0).eval(context);
                  return Math.sqrt(num.doubleValue());
              }
            }
            return null;
          }
        case CAST:
        case CTOR_CALL:
        case FIELD_ACCESS:
      }
      throw new IllegalArgumentException(
          String.format("Eval for expression %s not implemented: %s", type, this));
    }

    public static Expression createDouble(Double constDouble) {
      return new Expression(Type.CONST_DOUBLE, constDouble);
    }

    public static Expression createInteger(Integer constInt) {
      return new Expression(Type.CONST_INT, constInt);
    }

    public static Expression createBool(Boolean constBool) {
      return new Expression(Type.CONST_BOOL, constBool);
    }

    public static Expression createString(String constString) {
      return new Expression(Type.CONST_STRING, constString);
    }

    public static Expression createIdentifier(Identifier variable) {
      return new Expression(Type.IDENTIFIER, variable);
    }

    public static Expression createUnaryOp(UnaryOp unaryOp) {
      return new Expression(Type.UNARY_OP, unaryOp);
    }

    public static Expression createBinaryOp(BinaryOp binaryOp) {
      return new Expression(Type.BINARY_OP, binaryOp);
    }

    public static Expression createArrayIndex(ArrayIndex arrayIndex) {
      return new Expression(Type.ARRAY_INDEX, arrayIndex);
    }

    public static Expression createCast(Cast cast) {
      return new Expression(Type.CAST, cast);
    }

    public static Expression createCtorCall(CtorCall ctorCall) {
      return new Expression(Type.CTOR_CALL, ctorCall);
    }

    public static Expression createFieldAccess(FieldAccess fieldAccess) {
      return new Expression(Type.FIELD_ACCESS, fieldAccess);
    }

    public static Expression createMethodCall(MethodCall methodCall) {
      return new Expression(Type.METHOD_CALL, methodCall);
    }

    @Override
    public String toString() {
      switch (type) {
        case IDENTIFIER:
          return ((Identifier) data).name();
        case BINARY_OP:
          {
            var op = (BinaryOp) data;
            return String.format("%s %s %s", op.lhs(), op.op().symbol(), op.rhs());
          }
        case ARRAY_INDEX:
          {
            var arrayIndex = (ArrayIndex) data;
            return String.format("%s[%s]", arrayIndex.array(), arrayIndex.index());
          }
        case METHOD_CALL:
          {
            var call = (MethodCall) data;
            return String.format(
                "%s(%s)",
                call.method(), call.params().stream().map(Object::toString).collect(joining(", ")));
          }
        case NULL:
        case CONST_DOUBLE:
        case CONST_INT:
        case CONST_BOOL:
        case CONST_STRING:
        case UNARY_OP:
        case CAST:
        case CTOR_CALL:
        case FIELD_ACCESS:
        default:
          return data.toString();
      }
    }
  }

  // TODO(maxuser): What about destructuring assignment to a tuple?
  public record Assignment(Identifier lhs, Expression rhs) {}

  public record AugmentedAssignment(Identifier lhs, Op op, Expression rhs) {
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

  public record UnaryOp(Op op, Expression operand) {
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

    Object eval(Context context) {
      switch (op) {
        case NEGATIVE:
          return Numbers.negate((Number) operand.eval(context));
        case NOT:
          return !(Boolean) operand.eval(context);
      }
      throw new IllegalArgumentException("Unary op not implemented");
    }
  }

  public record BinaryOp(Expression lhs, Op op, Expression rhs) {
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

    Object eval(Context context) {
      switch (op) {
        case EQ:
          return lhs.eval(context).equals(rhs.eval(context));
        case ADD:
          return Numbers.add((Number) lhs.eval(context), (Number) rhs.eval(context));
        case SUB:
          return Numbers.subtract((Number) lhs.eval(context), (Number) rhs.eval(context));
        case MUL:
          return Numbers.multiply((Number) lhs.eval(context), (Number) rhs.eval(context));
          // TODO(maxuser): impl ops...
      }
      throw new IllegalArgumentException("Binary op not implemented");
    }
  }

  public record ArrayIndex(Expression array, Expression index) {}

  public record Cast(Identifier castType, Expression rhs) {}

  public record CtorCall(Identifier classId, List<Expression> params) {}

  public record MethodCall(Expression method, List<Expression> params) {}

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
