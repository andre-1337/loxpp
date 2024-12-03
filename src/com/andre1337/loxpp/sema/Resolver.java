package com.andre1337.loxpp.sema;

import com.andre1337.loxpp.Lox;
import com.andre1337.loxpp.ast.Expr;
import com.andre1337.loxpp.ast.Stmt;
import com.andre1337.loxpp.interpreter.Interpreter;
import com.andre1337.loxpp.lexer.Token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
  private final Interpreter interpreter;
  private final Stack<Map<String, Boolean>> scopes = new Stack<>();
  private FunctionType currentFunction = FunctionType.NONE;

  public Resolver(Interpreter interpreter) {
    this.interpreter = interpreter;
  }

  private enum FunctionType {
    NONE,
    FUNCTION,
    LAMBDA,
    INITIALIZER,
    METHOD,
  }

  private enum ClassType {
    NONE,
    CLASS,
    TRAIT,
    SUBCLASS,
  }

  private ClassType currentClass = ClassType.NONE;

  public void resolve(List<Stmt> statements) {
    for (Stmt statement : statements) {
      resolve(statement);
    }
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    beginScope();
    resolve(stmt.statements);
    endScope();
    return null;
  }

  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
    ClassType enclosingClass = currentClass;
    currentClass = ClassType.CLASS;

    declare(stmt.name);
    define(stmt.name);

    if (stmt.fields != null) {
      for (Expr.Variable field : stmt.fields) {
        resolve(field);
      }
    }

    if (stmt.superclass != null &&
        stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
      Lox.error(stmt.superclass.name, "A class can't inherit from itself.");
    }

    if (stmt.superclass != null) {
      currentClass = ClassType.SUBCLASS;

      resolve(stmt.superclass);
    }

    if (stmt.superclass != null) {
      beginScope();
      scopes.peek().put("super", true);
    }

    for (Expr trait : stmt.traits) {
      resolve(trait);
    }

    beginScope();
    scopes.peek().put("self", true);

    for (Stmt.Function method : stmt.methods) {
      FunctionType declaration = FunctionType.METHOD;

      if (method.name.lexeme.equals("init")) {
        declaration = FunctionType.INITIALIZER;
      }

      resolveFunction(method, declaration);
    }

    endScope();

    for (Stmt.Function staticMethod : stmt.staticMethods) {
      beginScope();
      scopes.peek().put("Self", true);
      resolveFunction(staticMethod, FunctionType.METHOD);
      endScope();
    }

    if (stmt.superclass != null)
      endScope();

    currentClass = enclosingClass;

    return null;
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    resolve(stmt.expression);
    return null;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    declare(stmt.name);
    define(stmt.name);

    resolveFunction(stmt, FunctionType.FUNCTION);

    return null;
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    resolve(stmt.condition);
    resolve(stmt.thenBranch);
    if (stmt.elseBranch != null)
      resolve(stmt.elseBranch);
    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    if (currentFunction == FunctionType.NONE) {
      Lox.error(stmt.keyword, "Can't return from top-level code.");
    }

    if (stmt.value != null) {
      if (currentFunction == FunctionType.INITIALIZER) {
        Lox.error(stmt.keyword, "Can't return a value from an initializer.");
      }

      resolve(stmt.value);
    }

    return null;
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    declare(stmt.name);

    if (stmt.initializer != null) {
      resolve(stmt.initializer);
    }

    define(stmt.name);

    return null;
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    resolve(stmt.condition);
    resolve(stmt.body);
    return null;
  }

  @Override
  public Void visitForInStmt(Stmt.ForIn stmt) {
    declare(stmt.key);

    if (stmt.value != null) {
      declare(stmt.value);
    }

    resolve(stmt.iterable);
    define(stmt.key);

    if (stmt.value != null) {
      define(stmt.value);
    }

    resolve(stmt.body);
    return null;
  }

  @Override
  public Void visitMatchStmt(Stmt.Match stmt) {
    resolve(stmt.expression);
    for (Stmt.MatchCase matchCase : stmt.cases) {
      resolve(matchCase);
    }
    return null;
  }

  @Override
  public Void visitMatchCaseStmt(Stmt.MatchCase stmt) {
    resolve(stmt.pattern);
    resolve(stmt.statement);
    return null;
  }

  @Override
  public Void visitTraitStmt(Stmt.Trait stmt) {
    declare(stmt.name);
    define(stmt.name);
    ClassType enclosingClass = currentClass;
    currentClass = ClassType.TRAIT;

    if (stmt.traits != null) {
      for (Expr trait : stmt.traits) {
        resolve(trait);
      }
    }

    beginScope();
    scopes.peek().put("this", true);
    for (Stmt.Function method : stmt.methods) {
      FunctionType declaration = FunctionType.METHOD;
      resolveFunction(method, declaration);
    }
    endScope();

    currentClass = enclosingClass;
    return null;
  }

  @Override
  public Void visitThrowStmt(Stmt.Throw stmt) {
    resolve(stmt.thrown);
    return null;
  }

  @Override
  public Void visitEnumStmt(Stmt.Enum stmt) {
    declare(stmt.name);
    define(stmt.name);

    beginScope();
    scopes.peek().put("self", true);

    for (Stmt.Enum.Variant variant : stmt.variants) {
      declare(variant.name());
      define(variant.name());
    }

    endScope();
    return null;
  }

  @Override
  public Void visitTryCatchStmt(Stmt.TryCatch stmt) {
    beginScope();
    resolve(stmt.tryBody);
    endScope();

    beginScope();
    declare(stmt.exception);
    define(stmt.exception);
    resolve(stmt.catchBody);
    endScope();

    return null;
  }

  @Override
  public Void visitNamespaceStmt(Stmt.Namespace stmt) {
    declare(stmt.name);
    define(stmt.name);
    beginScope();
    resolve(stmt.body);
    endScope();

    return null;
  }

  @Override
  public Void visitObjectDestructuringStmt(Stmt.ObjectDestructuring stmt) {
    for (Token binding : stmt.bindings) {
      declare(binding);
      define(binding);
    }

    resolve(stmt.initializer);

    return null;
  }

  @Override
  public Void visitArrayDestructuringStmt(Stmt.ArrayDestructuring stmt) {
    for (Token binding : stmt.bindings) {
      declare(binding);
      define(binding);
    }

    resolve(stmt.initializer);

    return null;
  }

  @Override
  public Void visitUsingStmt(Stmt.Using stmt) {
    resolve(stmt.source);

    for (Expr.Variable name : stmt.names) {
      declare(name.name);
      define(name.name);
    }

    return null;
  }

  @Override
  public Void visitForStmt(Stmt.For stmt) {
    beginScope();

    if (stmt.initializer != null) {
      resolve(stmt.initializer);
    }

    if (stmt.condition != null) {
      resolve(stmt.condition);
    }

    resolve(stmt.body);

    if (stmt.increment != null) {
      resolve(stmt.increment);
    }

    endScope();
    return null;
  }

  @Override
  public Void visitAwaitExpr(Expr.Await expr) {
    resolve(expr.value);
    return null;
  }

  @Override
  public Void visitTernaryExpr(Expr.Ternary expr) {
    resolve(expr.condition);
    resolve(expr.thenBranch);
    resolve(expr.elseBranch);

    return null;
  }

  @Override
  public Void visitSpreadExpr(Expr.Spread expr) {
    resolve(expr.right);
    return null;
  }

  @Override
  public Void visitLazyExpr(Expr.Lazy expr) {
    resolve(expr.expr);
    return null;
  }

  @Override
  public Void visitTupleLiteralExpr(Expr.TupleLiteral expr) {
    for (Expr element : expr.elements) {
      resolve(element);
    }
    return null;
  }

  @Override
  public Void visitTypeofExpr(Expr.Typeof expr) {
    resolve(expr.var);
    return null;
  }

  @Override
  public Void visitDictionaryExpr(Expr.Dictionary expr) {
    beginScope();
    for (Map.Entry<Token, Expr> entry : expr.keyValues.entrySet()) {
      declare(entry.getKey());
      define(entry.getKey());
      resolve(entry.getValue());
    }

    endScope();
    return null;
  }

  @Override
  public Void visitLambdaExpr(Expr.Lambda expr) {
    FunctionType enclosing = currentFunction;
    currentFunction = FunctionType.LAMBDA;
    beginScope();

    for (Token param : expr.params) {
      declare(param);
      define(param);
    }

    resolve(expr.body);
    endScope();

    currentFunction = enclosing;
    return null;
  }

  @Override
  public Void visitArrayExpr(Expr.Array expr) {
    expr.elements.forEach(this::resolve);
    return null;
  }

  @Override
  public Void visitArraySubscriptGetExpr(Expr.SubscriptGet expr) {
    resolve(expr.indexee);
    resolve(expr.index);

    return null;
  }

  @Override
  public Void visitArraySubscriptSetExpr(Expr.SubscriptSet expr) {
    resolve(expr.value);
    resolve(expr.indexee);
    resolve(expr.index);

    return null;
  }

  @Override
  public Void visitAssignExpr(Expr.Assign expr) {
    resolve(expr.value);
    resolveLocal(expr, expr.name);

    return null;
  }

  @Override
  public Void visitBinaryExpr(Expr.Binary expr) {
    resolve(expr.left);
    resolve(expr.right);

    return null;
  }

  @Override
  public Void visitCallExpr(Expr.Call expr) {
    resolve(expr.callee);

    for (Expr argument : expr.arguments) {
      resolve(argument);
    }

    return null;
  }

  @Override
  public Void visitGetExpr(Expr.Get expr) {
    resolve(expr.object);

    return null;
  }

  @Override
  public Void visitGroupingExpr(Expr.Grouping expr) {
    resolve(expr.expression);

    return null;
  }

  @Override
  public Void visitLiteralExpr(Expr.Literal expr) {
    return null;
  }

  @Override
  public Void visitLogicalExpr(Expr.Logical expr) {
    resolve(expr.left);
    resolve(expr.right);

    return null;
  }

  @Override
  public Void visitSetExpr(Expr.Set expr) {
    resolve(expr.value);
    resolve(expr.object);

    return null;
  }

  @Override
  public Void visitSuperExpr(Expr.Super expr) {
    if (currentClass == ClassType.NONE) {
      Lox.error(expr.keyword, "Cannot use 'super' outside of a class.");
    } else if (currentClass == ClassType.TRAIT) {
      Lox.error(expr.keyword, "Cannot use 'super' in a trait.");
    } else if (currentClass != ClassType.SUBCLASS) {
      Lox.error(expr.keyword, "Cannot use 'super' in a class that has no superclass.");
    }

    resolveLocal(expr, expr.keyword);

    return null;
  }

  @Override
  public Void visitThisExpr(Expr.This expr) {
    if (currentClass == ClassType.NONE) {
      Lox.error(expr.keyword, "Can't use 'self' outside of a class.");
      return null;
    }

    resolveLocal(expr, expr.keyword);

    return null;
  }

  @Override
  public Void visitUnaryExpr(Expr.Unary expr) {
    resolve(expr.right);

    return null;
  }

  @Override
  public Void visitVariableExpr(Expr.Variable expr) {
    if (!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
      Lox.error(expr.name, "Can't read local variable in its own initializer.");
    }

    resolveLocal(expr, expr.name);

    return null;
  }

  private void resolve(Stmt stmt) {
    stmt.accept(this);
  }

  private void resolve(Expr expr) {
    expr.accept(this);
  }

  private void resolveFunction(Stmt.Function function, FunctionType type) {
    FunctionType enclosingFunction = currentFunction;
    currentFunction = type;

    beginScope();
    if (function.params != null) {
      for (Token param : function.params) {
        declare(param);
        define(param);
      }
    }

    if (function.body != null) {
      resolve(function.body);
    }

    endScope();
    currentFunction = enclosingFunction;
  }

  private void beginScope() {
    scopes.push(new HashMap<>());
  }

  private void endScope() {
    scopes.pop();
  }

  private void declare(Token name) {
    if (scopes.isEmpty())
      return;

    Map<String, Boolean> scope = scopes.peek();

    if (scope.containsKey(name.lexeme)) {
      Lox.error(name, "Already a variable with this name in this scope.");
    }

    scope.put(name.lexeme, false);
  }

  private void define(Token name) {
    if (scopes.isEmpty())
      return;
    scopes.peek().put(name.lexeme, true);
  }

  private void resolveLocal(Expr expr, Token name) {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      if (scopes.get(i).containsKey(name.lexeme)) {
        interpreter.resolve(expr, scopes.size() - 1 - i);
        return;
      }
    }
  }
}
