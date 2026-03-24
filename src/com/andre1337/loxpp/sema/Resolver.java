package com.andre1337.loxpp.sema;

import com.andre1337.loxpp.Lox;
import com.andre1337.loxpp.ast.Expr;
import com.andre1337.loxpp.ast.Stmt;
import com.andre1337.loxpp.interpreter.Interpreter;
import com.andre1337.loxpp.lexer.Token;

import java.util.*;

public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {
  private final Interpreter interpreter;

  // 🚨 Tracks the variable's index in the block array
  private static class LocalVar {
    final int index;
    boolean initialized;
    LocalVar(int index, boolean initialized) {
      this.index = index;
      this.initialized = initialized;
    }
  }

  private final Stack<Map<String, LocalVar>> scopes = new Stack<>();
  private FunctionType currentFunction = FunctionType.NONE;

  public Resolver(Interpreter interpreter) {
    this.interpreter = interpreter;
  }

  private enum FunctionType {
    NONE, FUNCTION, LAMBDA, INITIALIZER, METHOD, LAZY_BLOCK,
  }

  private enum ClassType {
    NONE, CLASS, TRAIT, SUBCLASS,
  }

  private ClassType currentClass = ClassType.NONE;
  private final Map<String, Set<String>> privateMethods = new HashMap<>();

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

    if (stmt.superclass != null && stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
      Lox.error(stmt.superclass.name, "A class can't inherit from itself.");
    }

    if (stmt.superclass != null) {
      currentClass = ClassType.SUBCLASS;
      resolve(stmt.superclass);
    }

    if (stmt.superclass != null) {
      beginScope();
      Map<String, LocalVar> scope = scopes.peek();
      scope.put("super", new LocalVar(scope.size(), true));
    }

    for (Expr trait : stmt.traits) {
      resolve(trait);
    }

    privateMethods.put(stmt.name.lexeme, new HashSet<>());

    beginScope();
    Map<String, LocalVar> scopeSelf = scopes.peek();
    scopeSelf.put("self", new LocalVar(scopeSelf.size(), true));

    for (Stmt.Function method : stmt.methods) {
      FunctionType declaration = FunctionType.METHOD;

      if (method.isPrivate) {
        privateMethods.get(stmt.name.lexeme).add(method.name.lexeme);
      }

      if (method.name.lexeme.equals("init")) {
        declaration = FunctionType.INITIALIZER;
      }

      resolveFunction(method, declaration);
    }

    endScope();

    for (Stmt.Function staticMethod : stmt.staticMethods) {
      beginScope();
      Map<String, LocalVar> scopeStatic = scopes.peek();
      scopeStatic.put("Self", new LocalVar(scopeStatic.size(), true));
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
    if (stmt.value != null) declare(stmt.value);

    resolve(stmt.iterable);

    define(stmt.key);
    if (stmt.value != null) define(stmt.value);

    resolve(stmt.body);
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
    Map<String, LocalVar> scopeTrait = scopes.peek();
    scopeTrait.put("this", new LocalVar(scopeTrait.size(), true));
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
    for (Stmt.EnumCase kase : stmt.cases) {
      declare(kase.name());
      define(kase.name());
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
      if (binding.lexeme.equals("_")) continue;
      declare(binding);
      define(binding);
    }
    resolve(stmt.initializer);
    return null;
  }

  @Override
  public Void visitArrayDestructuringStmt(Stmt.ArrayDestructuring stmt) {
    for (Token binding : stmt.bindings) {
      if (binding.lexeme.equals("_")) continue;
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
  public Void visitExportStmt(Stmt.Export stmt) {
    for (Token name : stmt.names) {
      boolean found = false;
      if (!scopes.isEmpty()) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
          if (scopes.get(i).containsKey(name.lexeme)) {
            found = true;
            break;
          }
        }
      }
      if (!found) {
        Lox.error(name, "Cannot export undeclared member '" + name.lexeme + "'.");
      }
    }
    return null;
  }

  @Override
  public Void visitForStmt(Stmt.For stmt) {
    beginScope();
    if (stmt.initializer != null) resolve(stmt.initializer);
    if (stmt.condition != null) resolve(stmt.condition);
    resolve(stmt.body);
    if (stmt.increment != null) resolve(stmt.increment);
    endScope();
    return null;
  }

  @Override
  public Void visitImplStmt(Stmt.Impl stmt) {
    ClassType enclosingClass = currentClass;
    currentClass = ClassType.CLASS;

    resolve(stmt.name);

    beginScope();
    Map<String, LocalVar> scopeImpl = scopes.peek();
    scopeImpl.put("self", new LocalVar(scopeImpl.size(), true));

    for (Stmt.Function method : stmt.methods) {
      resolveFunction(method, FunctionType.METHOD);
    }

    endScope();
    currentClass = enclosingClass;
    return null;
  }

  @Override
  public Void visitAwaitExpr(Expr.Await expr) {
    resolve(expr.value);
    return null;
  }

  @Override
  public Void visitNewExpr(Expr.New expr) {
    resolve(expr.constructor.callee);
    for (Expr argument : expr.constructor.arguments) {
      resolve(argument);
    }
    return null;
  }

  @Override
  public Void visitMatchExpr(Expr.Match expr) {
    resolve(expr.value);

    for (Expr.MatchCase kase : expr.cases) {
      beginScope();
      if (kase.pattern() instanceof Expr.Variable var) {
        declare(var.name);
        define(var.name);
      }
      resolve(kase.pattern());
      if (kase.guard() != null) resolve(kase.guard());
      resolve(kase.body());
      endScope();
    }
    return null;
  }

  @Override
  public Void visitUnionPatternExpr(Expr.UnionPattern expr) {
    for (Token binding : expr.bindings) {
      declare(binding);
      define(binding);
    }
    return null;
  }

  @Override
  public Void visitWildcardPatternExpr(Expr.WildcardPattern expr) {
    return null;
  }

  @Override
  public Void visitListPatternExpr(Expr.ListPattern expr) {
    for (Expr element : expr.elements) {
      if (element instanceof Expr.Variable var) {
        declare(var.name);
        define(var.name);
      }
      resolve(element);
    }

    if (expr.rest != null) {
      if (expr.rest instanceof Expr.Variable var) {
        declare(var.name);
        define(var.name);
      }
      resolve(expr.rest);
    }
    return null;
  }

  @Override
  public Void visitObjectPatternExpr(Expr.ObjectPattern expr) {
    for (Expr.ObjectPattern.Property property : expr.properties) {
      if (property.pattern() instanceof Expr.Variable var) {
        declare(var.name);
        define(var.name);
      }
      resolve(property.pattern());
    }

    if (expr.rest != null) {
      if (expr.rest instanceof Expr.Variable var) {
        declare(var.name);
        define(var.name);
      }
      resolve(expr.rest);
    }
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
    if (expr.expr != null) {
      resolve(expr.expr);
    } else {
      FunctionType enclosingFunction = currentFunction;
      currentFunction = FunctionType.LAZY_BLOCK;

      beginScope();
      resolve(expr.statements);
      endScope();

      currentFunction = enclosingFunction;
    }
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

    for (Stmt.Function.Param param : expr.params) {
      declare(param.name());
      define(param.name());

      if (param.defaultValue() != null) {
        resolve(param.defaultValue());
      }
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

    String methodName = expr.name.lexeme;
    for (Map.Entry<String, Set<String>> entry: privateMethods.entrySet()) {
      Set<String> privates = entry.getValue();

      if (privates.contains(methodName)) {
        if (currentClass == ClassType.NONE) {
          Lox.error(expr.name, "Cannot access private method '" + methodName + "' from outside a class.");
        }
      }
    }
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
    if (!scopes.isEmpty()) {
      LocalVar var = scopes.peek().get(expr.name.lexeme);
      if (var != null && !var.initialized) {
        Lox.error(expr.name, "Can't read local variable in its own initializer.");
      }
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
      for (Stmt.Function.Param param : function.params) {
        declare(param.name());
        define(param.name());
        if (param.defaultValue() != null) {
          resolve(param.defaultValue());
        }
      }
    }

    if (function.body != null) {
      resolve(function.body);
    }

    endScope();
    currentFunction = enclosingFunction;
  }

  private void beginScope() {
    scopes.push(new LinkedHashMap<>());
  }

  private void endScope() {
    scopes.pop();
  }

  private void declare(Token name) {
    if (scopes.isEmpty()) return;

    Map<String, LocalVar> scope = scopes.peek();
    if (scope.containsKey(name.lexeme)) {
      Lox.error(name, "Already a variable with this name in this scope.");
    }

    scope.put(name.lexeme, new LocalVar(scope.size(), false));
  }

  private void define(Token name) {
    if (scopes.isEmpty()) return;
    scopes.peek().get(name.lexeme).initialized = true;
  }

  private void resolveLocal(Expr expr, Token name) {
    for (int i = scopes.size() - 1; i >= 0; i--) {
      LocalVar var = scopes.get(i).get(name.lexeme);
      if (var != null) {
        interpreter.resolve(expr, scopes.size() - 1 - i, var.index);
        return;
      }
    }
  }
}