package com.andre1337.loxpp.ast;

import com.andre1337.loxpp.lexer.Token;

import java.util.*;

public abstract class Stmt {
  public interface Visitor<R> {
    R visitBlockStmt(Block stmt);

    R visitClassStmt(Class stmt);

    R visitExpressionStmt(Expression stmt);

    R visitFunctionStmt(Function stmt);

    R visitIfStmt(If stmt);

    R visitReturnStmt(Return stmt);

    R visitVarStmt(Var stmt);

    R visitWhileStmt(While stmt);

    R visitForInStmt(ForIn stmt);

    R visitMatchStmt(Match stmt);

    R visitMatchCaseStmt(MatchCase stmt);

    R visitTraitStmt(Trait stmt);

    R visitThrowStmt(Throw stmt);

    R visitEnumStmt(Enum stmt);

    R visitTryCatchStmt(TryCatch stmt);

    R visitNamespaceStmt(Namespace stmt);

    R visitObjectDestructuringStmt(ObjectDestructuring stmt);

    R visitArrayDestructuringStmt(ArrayDestructuring stmt);

    R visitUsingStmt(Using stmt);

    R visitForStmt(For stmt);
  }

  public static class Block extends Stmt {
    public Block(List<Stmt> statements) {
      this.statements = statements;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitBlockStmt(this);
    }

    public final List<Stmt> statements;
  }

  public static class Class extends Stmt {
    public Class(
        Token name,
        List<Expr.Variable> fields,
        Expr.Variable superclass,
        List<Expr> traits,
        List<Stmt.Function> methods,
        List<Stmt.Function> staticMethods
    ) {
      this.name = name;
      this.fields = fields;
      this.superclass = superclass;
      this.traits = traits;
      this.methods = methods;
      this.staticMethods = staticMethods;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitClassStmt(this);
    }

    public final Token name;
    public final List<Expr.Variable> fields;
    public final Expr.Variable superclass;
    public final List<Expr> traits;
    public final List<Stmt.Function> methods;
    public final List<Stmt.Function> staticMethods;
  }

  public static class Expression extends Stmt {
    public Expression(Expr expression) {
      this.expression = expression;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitExpressionStmt(this);
    }

    public final Expr expression;
  }

  public static class Function extends Stmt {
    public Function(Token name, List<Token> params, List<Stmt> body, boolean isAbstract, boolean isAsync) {
      this.name = name;
      this.params = params;
      this.body = body;
      this.isAbstract = isAbstract;
      this.isAsync = isAsync;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitFunctionStmt(this);
    }

    public final Token name;
    public final List<Token> params;
    public final List<Stmt> body;
    public final boolean isAbstract;
    public final boolean isAsync;
  }

  public static class If extends Stmt {
    public If(Expr condition, Stmt thenBranch, Stmt elseBranch) {
      this.condition = condition;
      this.thenBranch = thenBranch;
      this.elseBranch = elseBranch;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitIfStmt(this);
    }

    public final Expr condition;
    public final Stmt thenBranch;
    public final Stmt elseBranch;
  }

  public static class Return extends Stmt {
    public Return(Token keyword, Expr value) {
      this.keyword = keyword;
      this.value = value;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitReturnStmt(this);
    }

    public final Token keyword;
    public final Expr value;
  }

  public static class Var extends Stmt {
    public Var(Token name, Expr initializer) {
      this.name = name;
      this.initializer = initializer;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitVarStmt(this);
    }

    public final Token name;
    public final Expr initializer;
  }

  public static class While extends Stmt {
    public While(Expr condition, Stmt body) {
      this.condition = condition;
      this.body = body;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitWhileStmt(this);
    }

    public final Expr condition;
    public final Stmt body;
  }

  public static class ForIn extends Stmt {
    public ForIn(Token keyword, Token key, Token value, Expr iterable, List<Stmt> body) {
      this.keyword = keyword;
      this.key = key;
      this.value = value;
      this.iterable = iterable;
      this.body = body;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitForInStmt(this);
    }

    public final Token keyword;
    public final Token key;
    public final Token value;
    public final Expr iterable;
    public final List<Stmt> body;
  }

  public static class Match extends Stmt {
    public Match(Expr expression, List<MatchCase> cases) {
      this.expression = expression;
      this.cases = cases;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitMatchStmt(this);
    }

    public final Expr expression;
    public final List<MatchCase> cases;
  }

  public static class MatchCase extends Stmt {
    public MatchCase(Expr pattern, Stmt statement) {
      this.pattern = pattern;
      this.statement = statement;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitMatchCaseStmt(this);
    }

    public final Expr pattern;
    public final Stmt statement;
  }

  public static class Trait extends Stmt {
    public Trait(Token name, List<Expr> traits, List<Stmt.Function> methods) {
      this.name = name;
      this.traits = traits;
      this.methods = methods;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitTraitStmt(this);
    }

    public final Token name;
    public final List<Expr> traits;
    public final List<Stmt.Function> methods;
  }

  public static class Throw extends Stmt {
    public Throw(Token keyword, Expr thrown) {
      this.keyword = keyword;
      this.thrown = thrown;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitThrowStmt(this);
    }

    public final Token keyword;
    public final Expr thrown;
  }

  public static class Enum extends Stmt {
    public record Variant(Token name, List<Token> parameters) { }

    public Enum(Token name, List<Variant> variants) {
      this.name = name;
      this.variants = variants;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitEnumStmt(this);
    }

    public final Token name;
    public final List<Variant> variants;
  }

  public static class TryCatch extends Stmt {
    public TryCatch(List<Stmt> tryBody, List<Stmt> catchBody, Token exception) {
      this.tryBody = tryBody;
      this.catchBody = catchBody;
      this.exception = exception;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) { return visitor.visitTryCatchStmt(this); }

    public final List<Stmt> tryBody;
    public final List<Stmt> catchBody;
    public final Token exception;
  }

  public static class Namespace extends Stmt {
    public Namespace(Token name, List<Stmt> body) {
      this.name = name;
      this.body = body;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) { return visitor.visitNamespaceStmt(this); }

    public final Token name;
    public final List<Stmt> body;
  }

  public static class ObjectDestructuring extends Stmt {
    public ObjectDestructuring(Token keyword, List<Token> bindings, Expr initializer) {
      this.keyword = keyword;
      this.bindings = bindings;
      this.initializer = initializer;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitObjectDestructuringStmt(this);
    }

    public final Token keyword;
    public final List<Token> bindings;
    public final Expr initializer;
  }

  public static class ArrayDestructuring extends Stmt {
    public ArrayDestructuring(Token keyword, List<Token> bindings, Expr initializer) {
      this.keyword = keyword;
      this.bindings = bindings;
      this.initializer = initializer;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitArrayDestructuringStmt(this);
    }

    public final Token keyword;
    public final List<Token> bindings;
    public final Expr initializer;
  }

  public static class Using extends Stmt {
    public Using(Token keyword, List<Expr.Variable> names, Expr source) {
      this.keyword = keyword;
      this.names = names;
      this.source = source;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) { return visitor.visitUsingStmt(this); }

    public final Token keyword;
    public final List<Expr.Variable> names;
    public final Expr source;
  }

  public static class For extends Stmt {
    public For(Token keyword, Stmt initializer, Expr condition, Expr increment, List<Stmt> body) {
      this.keyword = keyword;
      this.initializer = initializer;
      this.condition = condition;
      this.increment = increment;
      this.body = body;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) { return visitor.visitForStmt(this); }

    public final Token keyword;
    public final Stmt initializer;
    public final Expr condition;
    public final Expr increment;
    public final List<Stmt> body;
  }

  @SuppressWarnings("UnusedReturnValue")
  public abstract <R> R accept(Visitor<R> visitor);
}
