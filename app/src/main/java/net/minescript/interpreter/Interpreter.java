package net.minescript.interpreter;

import java.util.List;

public class Interpreter {

  public static class Statement {
    public enum Type {
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
      CONST_FLOAT,
      CONST_INT,
      CONST_BOOL,
      CONST_STRING,
      VARIABLE,
      UNARY_OP,
      BINARY_OP,
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

    public static Expression createDouble(Double constDouble) {
      return new Expression(Type.CONST_DOUBLE, constDouble);
    }

    public static Expression createFloat(Float constFloat) {
      return new Expression(Type.CONST_FLOAT, constFloat);
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

    public static Expression createVariable(Identifier variable) {
      return new Expression(Type.VARIABLE, variable);
    }

    public static Expression createUnaryOp(UnaryOp unaryOp) {
      return new Expression(Type.UNARY_OP, unaryOp);
    }

    public static Expression createBinaryOp(BinaryOp binaryOp) {
      return new Expression(Type.BINARY_OP, binaryOp);
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
  }

  // TODO(maxuser): What about destructuring assignment to a tuple?
  public record Assignment(Identifier lhs, Expression rhs) {}

  public record AugmentedAssignment(Identifier lhs, Op op, Expression rhs) {
    public enum Op {
      ADD_EQ,
      SUB_EQ,
      MUL_EQ,
      DIV_EQ,
      MOD_EQ
    }
  }

  public record UnaryOp(Op op, Expression operand) {
    public enum Op {
      NEGATIVE,
      NOT
    }
  }

  public record BinaryOp(Expression lhs, Op op, Expression rhs) {
    public enum Op {
      ADD,
      SUB,
      MUL,
      DIV,
      MOD,
      LESS,
      LT_EQ,
      EQ,
      GT,
      GT_EQ,
      AND,
      OR
    }
  }

  public record Cast(Identifier castType, Expression rhs) {}

  public record CtorCall(Identifier classId, List<Expression> params) {}

  public record MethodCall(Expression target, Identifier methodId, List<Expression> params) {}

  public record FieldAccess(Expression target, Identifier fieldId) {}

  public interface Context {
    Object getVariable(Identifier id);
  }
}
