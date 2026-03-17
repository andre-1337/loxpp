package com.andre1337.loxpp.ast;

import com.andre1337.loxpp.lexer.Token;

import java.util.*;

public abstract class Expr {
  public interface Visitor<R> {
    R visitAssignExpr(Assign expr);

    R visitBinaryExpr(Binary expr);

    R visitCallExpr(Call expr);

    R visitGetExpr(Get expr);

    R visitGroupingExpr(Grouping expr);

    R visitLiteralExpr(Literal expr);

    R visitLogicalExpr(Logical expr);

    R visitSetExpr(Set expr);

    R visitSuperExpr(Super expr);

    R visitThisExpr(This expr);

    R visitUnaryExpr(Unary expr);

    R visitVariableExpr(Variable expr);

    R visitArrayExpr(Array expr);

    R visitArraySubscriptGetExpr(SubscriptGet expr);

    R visitArraySubscriptSetExpr(SubscriptSet expr);

    R visitLambdaExpr(Lambda expr);

    R visitDictionaryExpr(Dictionary expr);

    R visitTypeofExpr(Typeof expr);

    R visitTupleLiteralExpr(TupleLiteral expr);

    R visitLazyExpr(Lazy expr);

    R visitSpreadExpr(Spread expr);

    R visitTernaryExpr(Ternary expr);
  }

  public static class Assign extends Expr {
    public Assign(Token name, Expr value) {
      this.name = name;
      this.value = value;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitAssignExpr(this);
    }

    public final Token name;
    public final Expr value;
  }

  public static class Binary extends Expr {
    public Binary(Expr left, Token operator, Expr right) {
      this.left = left;
      this.operator = operator;
      this.right = right;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitBinaryExpr(this);
    }

    public final Expr left;
    public final Token operator;
    public final Expr right;
  }

  public static class Call extends Expr {
    public Call(Expr callee, Token paren, List<Expr> arguments, Expr instance) {
      this.callee = callee;
      this.paren = paren;
      this.arguments = arguments;
      this.instance = instance;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitCallExpr(this);
    }

    public final Expr callee;
    public final Token paren;
    public final List<Expr> arguments;
    public final Expr instance;
  }

  public static class Get extends Expr {
    public Get(Expr object, Token name) {
      this.object = object;
      this.name = name;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitGetExpr(this);
    }

    public final Expr object;
    public final Token name;
  }

  public static class Grouping extends Expr {
    public Grouping(Expr expression) {
      this.expression = expression;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitGroupingExpr(this);
    }

    public final Expr expression;
  }

  public static class Literal extends Expr {
    public Literal(Object value) {
      this.value = value;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitLiteralExpr(this);
    }

    public final Object value;
  }

  public static class Logical extends Expr {
    public Logical(Expr left, Token operator, Expr right) {
      this.left = left;
      this.operator = operator;
      this.right = right;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitLogicalExpr(this);
    }

    public final Expr left;
    public final Token operator;
    public final Expr right;
  }

  public static class Set extends Expr {
    public Set(Expr object, Token name, Expr value) {
      this.object = object;
      this.name = name;
      this.value = value;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitSetExpr(this);
    }

    public final Expr object;
    public final Token name;
    public final Expr value;
  }

  public static class Super extends Expr {
    public Super(Token keyword, Token method) {
      this.keyword = keyword;
      this.method = method;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitSuperExpr(this);
    }

    public final Token keyword;
    public final Token method;
  }

  public static class This extends Expr {
    public This(Token keyword) {
      this.keyword = keyword;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitThisExpr(this);
    }

    public final Token keyword;
  }

  public static class Unary extends Expr {
    public Unary(Token operator, Expr right) {
      this.operator = operator;
      this.right = right;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitUnaryExpr(this);
    }

    public final Token operator;
    public final Expr right;
  }

  public static class Variable extends Expr {
    public Variable(Token name) {
      this.name = name;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitVariableExpr(this);
    }

    public final Token name;
  }

  public static class Array extends Expr {
    public Array(Token bracket, List<Expr> elements) {
      this.bracket = bracket;
      this.elements = elements;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitArrayExpr(this);
    }

    public final Token bracket;
    public final List<Expr> elements;
  }

  public static class SubscriptGet extends Expr {
    public SubscriptGet(Expr indexee, Token bracket, Expr index) {
      this.indexee = indexee;
      this.bracket = bracket;
      this.index = index;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitArraySubscriptGetExpr(this);
    }

    public final Expr indexee;
    public final Token bracket;
    public final Expr index;
  }

  public static class SubscriptSet extends Expr {
    public SubscriptSet(Expr indexee, Token bracket, Expr index, Expr value) {
      this.indexee = indexee;
      this.bracket = bracket;
      this.index = index;
      this.value = value;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitArraySubscriptSetExpr(this);
    }

    public final Expr indexee;
    public final Token bracket;
    public final Expr index;
    public final Expr value;
  }

  public static class Lambda extends Expr {
    public Lambda(List<Token> params, List<Stmt> body) {
      this.params = params;
      this.body = body;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitLambdaExpr(this);
    }

    public final List<Token> params;
    public final List<Stmt> body;
  }

  public static class Dictionary extends Expr {
    public Dictionary(Token brace, Map<Token, Expr> keyValues) {
      this.brace = brace;
      this.keyValues = keyValues;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitDictionaryExpr(this);
    }

    public final Token brace;
    public final Map<Token, Expr> keyValues;
  }

  public static class Typeof extends Expr {
    public Typeof(Expr.Variable var) {
      this.var = var;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitTypeofExpr(this);
    }

    public final Expr.Variable var;
  }

  public static class TupleLiteral extends Expr {
    public TupleLiteral(List<Expr> elements, Token token) {
      this.elements = elements;
      this.token = token;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitTupleLiteralExpr(this);
    }

    public final List<Expr> elements;
    public final Token token;
  }

  public static class Lazy extends Expr {
    public Lazy(Expr expr) {
      this.expr = expr;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) { return visitor.visitLazyExpr(this); }

    public final Expr expr;
  }

  public static class Spread extends Expr {
    public Spread(Token operator, Expr right) {
      this.operator = operator;
      this.right = right;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) { return visitor.visitSpreadExpr(this); }

    public final Token operator;
    public final Expr right;
  }

  public static class Ternary extends Expr {
    public Ternary(Expr condition, Expr thenBranch, Expr elseBranch) {
      this.condition = condition;
      this.thenBranch = thenBranch;
      this.elseBranch = elseBranch;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) { return visitor.visitTernaryExpr(this); }

    public final Expr condition;
    public final Expr thenBranch;
    public final Expr elseBranch;
  }

  public abstract <R> R accept(Visitor<R> visitor);
}
