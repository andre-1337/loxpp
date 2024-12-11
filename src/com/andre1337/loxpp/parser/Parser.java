package com.andre1337.loxpp.parser;

import com.andre1337.loxpp.Lox;
import com.andre1337.loxpp.ast.Expr;
import com.andre1337.loxpp.ast.Stmt;
import com.andre1337.loxpp.lexer.Token;
import com.andre1337.loxpp.lexer.TokenType;

import java.util.*;

import static com.andre1337.loxpp.lexer.TokenType.*;

public class Parser {
  private static class ParseError extends RuntimeException {
  }

  private final List<Token> tokens;
  private int current = 0;

  public Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  public List<Stmt> parse() {
    List<Stmt> statements = new ArrayList<>();
    while (!isAtEnd()) {
      statements.add(declaration());
    }

    return statements;
  }

  private Expr expression() {
    return ternary();
  }

  private Expr ternary() {
    Expr expr = nullCoalescing();

    if (match(QUESTION)) {
      Expr thenBranch = expression();
      consume(COLON, "Expect ':' after then branch of ternary expression.");
      Expr elseBranch = ternary();
      expr = new Expr.Ternary(expr, thenBranch, elseBranch);
    }

    return expr;
  }

  private Expr nullCoalescing() {
    Expr expr = assignment();

    while (match(QUESTION_QUESTION)) {
      Token operator = previous();
      Expr right = assignment();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Stmt declaration() {
    try {
      if (match(CLASS))
        return classDeclaration();
      if (match(FN))
        return function();
      if (match(LET))
        return destructuringDeclaration();
      if (match(TRAIT))
        return traitDeclaration();
      if (match(ENUM))
        return enumDeclaration();
      if (match(NAMESPACE))
        return namespaceDeclaration();
      if (match(USING))
        return usingDeclaration();

      return statement();
    } catch (ParseError error) {
      synchronize();
      return null;
    }
  }

  private Stmt classDeclaration() {
    Token name = consume(IDENTIFIER, "Expect class name.");
    List<Expr.Variable> fields = new ArrayList<>();
    List<Stmt.Function> methods = new ArrayList<>();
    List<Stmt.Function> staticMethods = new ArrayList<>();

    boolean isDataclass = false;

    if (match(LEFT_PAREN) && !check(RIGHT_PAREN)) {
      isDataclass = true;
      do {
        consume(IDENTIFIER, "Expect a field name.");
        fields.add(new Expr.Variable(previous()));
      } while (match(COMMA));

      consume(RIGHT_PAREN, "Expect ')' after data class fields declaration.");
    }

    if (isDataclass) {
      List<Stmt> body = new ArrayList<>();
      for (Expr.Variable field : fields) {
        body.add(new Stmt.Expression(new Expr.Set(new Expr.This(new Token(THIS, "self", null, 0, 0)), field.name, field)));
      }

      methods.add(
              new Stmt.Function(
                      new Token(
                              IDENTIFIER,
                              "init",
                              null,
                              0,
                              0
                      ),
                      fields.stream().map(field -> new Token(IDENTIFIER, field.name.lexeme, null, 0, 0)).toList(),
                      body,
                      false,
                      false
              )
      );
    }

    Expr.Variable superclass = null;
    if (match(EXTENDS)) {
      consume(IDENTIFIER, "Expect superclass name.");
      superclass = new Expr.Variable(previous());
    }

    List<Expr> traits = withClause();

    if (match(SEMICOLON)) {
      return new Stmt.Class(name, fields, superclass, traits, methods, staticMethods);
    } else {
      consume(LEFT_BRACE, "Expect '{' before class body.");

      while (!check(RIGHT_BRACE) && !isAtEnd()) {
        boolean isStatic = match(STATIC);
        consume(FN, "Expect 'fn' keyword before method declaration.");
        boolean isAsync = match(ASYNC);
        (isStatic ? staticMethods : methods).add(function("method", isAsync));
      }

      consume(RIGHT_BRACE, "Expect '}' after class body.");
      return new Stmt.Class(name, null, superclass, traits, methods, staticMethods);
    }
  }

  private Stmt statement() {
    if (match(FOR))
      return forStatement();
    if (match(IF))
      return ifStatement();
    if (match(RETURN))
      return returnStatement();
    if (match(WHILE))
      return whileStatement();
    if (match(LEFT_BRACE))
      return new Stmt.Block(block());
    if (match(MATCH))
      return matchStatement();
    if (match(THROW))
      return throwStatement();
    if (match(TRY))
      return tryStatement();

    return expressionStatement();
  }

  private Stmt forStatement() {
    Token keyword = previous();

    if (match(IDENTIFIER)) {
      return forInStatement(keyword, previous());
    } else if (match(LEFT_PAREN)) {
      return cStyleForStatement();
    } else {
      throw error(peek(), "Expect variable declaration or expression after 'for'.");
    }
  }

  private Stmt forInStatement(Token keyword, Token key) {
    Token value = null;

    if (match(COMMA)) {
      value = consume(IDENTIFIER, "Expect value name.");
    }

    consume(IN, "Expect 'in' after variable name.");
    Expr expr = expression();
    consume(LEFT_BRACE, "Expect '{' before loop body.");

    List<Stmt> body = block();
    return new Stmt.ForIn(keyword, key, value, expr, body);
  }

  private Stmt cStyleForStatement() {
    Stmt initializer;
    if (match(SEMICOLON)) {
      initializer = null;
    } else if (match(LET)) {
      initializer = varDeclaration();
    } else {
      initializer = expressionStatement();
    }

    Expr condition = null;
    if (!check(SEMICOLON)) {
      condition = expression();
    }
    consume(SEMICOLON, "Expect ';' after loop condition.");

    Expr increment = null;
    if (!check(RIGHT_PAREN)) {
      increment = expression();
    }
    consume(RIGHT_PAREN, "Expect ')' after for clauses.");
    Stmt body = statement();

    if (increment != null) {
      body = new Stmt.Block(
              Arrays.asList(
                      body,
                      new Stmt.Expression(increment)));
    }

    if (condition == null) condition = new Expr.Literal(true);
    body = new Stmt.While(condition, body);

    if (initializer != null) {
      body = new Stmt.Block(Arrays.asList(initializer, body));
    }

    return body;
  }

  private Stmt ifStatement() {
    Expr condition = expression();
    Stmt thenBranch = statement();
    Stmt elseBranch = null;

    if (match(ELSE)) {
      elseBranch = statement();
    }

    return new Stmt.If(condition, thenBranch, elseBranch);
  }

  private Stmt returnStatement() {
    Token keyword = previous();
    Expr value = null;
    if (!check(SEMICOLON)) {
      value = expression();
    }

    consume(SEMICOLON, "Expect ';' after return value.");
    return new Stmt.Return(keyword, value);
  }

  private Stmt destructuringDeclaration() {
    Token keyword = previous();

    if (match(LEFT_BRACE)) {
      List<Token> bindings = new ArrayList<>();
      do {
        Token key = consume(IDENTIFIER, "Expect binding name.");
        bindings.add(key);
      } while (match(COMMA));

      consume(RIGHT_BRACE, "Expect '}' after object destructuring pattern.");
      consume(EQUAL, "Expect '=' after object destructuring pattern.");
      Expr initializer = expression();
      consume(SEMICOLON, "Expect ';' after variable declaration");

      return new Stmt.ObjectDestructuring(keyword, bindings, initializer);
    } else if (match(LEFT_BRACKET)) {
      List<Token> bindings = new ArrayList<>();
      do {
        Token key = consume(IDENTIFIER, "Expect binding name.");
        bindings.add(key);
      } while (match(COMMA));

      consume(RIGHT_BRACKET, "Expect ']' after array destructuring pattern.");
      consume(EQUAL, "Expect '=' after array destructuring pattern.");
      Expr initializer = expression();
      consume(SEMICOLON, "Expect ';' after variable declaration.");

      return new Stmt.ArrayDestructuring(keyword, bindings, initializer);
    }

    return varDeclaration();
  }

  private Stmt varDeclaration() {
    Token name = consume(IDENTIFIER, "Expect variable name");

    Expr initializer = null;
    if (match(EQUAL)) {
      initializer = expression();
    }

    consume(SEMICOLON, "Expect ';' after variable declaration.");
    return new Stmt.Var(name, initializer);
  }

  private List<Expr> withClause() {
    List<Expr> traits = new ArrayList<>();
    if (match(WITH)) {
      do {
        traits.add(dottedIdentifier());
      } while (match(COMMA));
    }

    return traits;
  }

  private Expr dottedIdentifier() {
    Expr expr = new Expr.Variable(consume(IDENTIFIER, "Expect identifier."));
    while (match(DOT)) {
      Token name = consume(IDENTIFIER, "Expect identifier after '.'.");
      expr = new Expr.Get(expr, name);
    }

    return expr;
  }

  private List<Stmt.Function> parseTraitMethods() {
    List<Stmt.Function> methods = new ArrayList<>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      boolean isAbstract = match(ABSTRACT);
      methods.add(traitMethod(isAbstract));
    }
    return methods;
  }

  private Stmt traitDeclaration() {
    Token name = consume(IDENTIFIER, "Expect trait name.");
    List<Expr> traits = withClause();
    consume(LEFT_BRACE, "Expect '{' before trait body.");
    List<Stmt.Function> methods = parseTraitMethods();
    consume(RIGHT_BRACE, "Expect '}' after trait body.");
    return new Stmt.Trait(name, traits, methods);
  }

  private Stmt.Enum.Variant enumVariant() {
    Token name = consume(IDENTIFIER, "Expect variant name.");
    List<Token> parameters = new ArrayList<>();

    if (match(LEFT_PAREN)) {
      do {
        parameters.add(consume(IDENTIFIER, "Expect parameter name."));
      } while (match(COMMA));

      consume(RIGHT_PAREN, "Expect ')' after parameters.");
    }

    return new Stmt.Enum.Variant(name, parameters);
  }

  private Stmt enumDeclaration() {
    Token name = consume(IDENTIFIER, "Expect enum name.");
    consume(LEFT_BRACE, "Expect '{' before enum body.");

    List<Stmt.Enum.Variant> variants = new ArrayList<>();

    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      variants.add(enumVariant());

      if (!check(RIGHT_BRACE)) {
        consume(COMMA, "Expect ',' between enum variants.");
      }
    }

    consume(RIGHT_BRACE, "Expect '}' after enum body.");
    return new Stmt.Enum(name, variants);
  }

  private Stmt namespaceDeclaration() {
    Token name = consume(IDENTIFIER, "Expect namespace name.");
    consume(LEFT_BRACE, "Expect '{' before namespace body.");

    List<Stmt> body = new ArrayList<>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      body.add(declaration());
    }
    consume(RIGHT_BRACE, "Expect '}' after namespace body.");

    return new Stmt.Namespace(name, body);
  }

  private Stmt usingDeclaration() {
    Token keyword = previous();
    List<Expr.Variable> names = new ArrayList<>();

    if (match(LEFT_BRACE)) {
      do {
        Token name = consume(IDENTIFIER, "Expect identifier.");
        names.add(new Expr.Variable(name));
      } while (match(COMMA));

      consume(RIGHT_BRACE, "Expect '}' after identifiers.");
    } else {
      Token name = consume(IDENTIFIER, "Expect identifier.");
      names.add(new Expr.Variable(name));
    }

    consume(FROM, "Expect 'from' keyword.");
    Expr source = expression();
    consume(SEMICOLON, "Expect ';' after 'using' statement.");

    return new Stmt.Using(keyword, names, source);
  }

  private Stmt whileStatement() {
    Expr condition = expression();

    Stmt body = statement();
    return new Stmt.While(condition, body);
  }

  private Stmt expressionStatement() {
    Expr expr = expression();
    consume(SEMICOLON, "Expect ';' after expression.");
    return new Stmt.Expression(expr);
  }

  private Stmt function() {
    boolean isAsync = match(ASYNC);
    return function(isAsync ? "async function" : "function", isAsync);
  }

  private Stmt.Function function(String kind, boolean isAsync) {
    Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
    List<Token> parameters = new ArrayList<>();
    List<Stmt> body = new ArrayList<>();

    consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");

    if (!check(RIGHT_PAREN)) {
      do {
        if (parameters.size() >= 255) {
          throw error(peek(), "Can't have more than 255 parameters.");
        }

        parameters.add(consume(IDENTIFIER, "Expect parameter name."));
      } while (match(COMMA));
    }

    consume(RIGHT_PAREN, "Expect ')' after parameters.");

    if (match(ARROW)) {
      body.add(new Stmt.Return(new Token(RETURN, "return", "return", previous().line++, previous().column++), expression()));
      consume(SEMICOLON, "Expect ';' after arrow " + kind + " declaration.");
    } else {
      consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
      body = block();
    }

    return new Stmt.Function(name, parameters, body, false, isAsync);
  }

  private Stmt.Function traitMethod(boolean isAbstract) {
    boolean isAsync;

    if (isAbstract) {
      consume(FN, "Expect 'fn' keyword before method name");
      isAsync = match(ASYNC);
      Token name = consume(IDENTIFIER, "Expect trait method name.");
      consume(LEFT_PAREN, "Expect '(' after trait method name.");

      List<Token> parameters = new ArrayList<>();
      parseParameters(parameters);
      consume(SEMICOLON, "Expect ';' after abstract trait method.");

      return new Stmt.Function(name, parameters, null, true, isAsync);
    } else if (match(FN)) {
      isAsync = match(ASYNC);
      return function("trait method", isAsync);
    }

    return null;
  }

  private void parseParameters(List<Token> parameters) {
    if (!check(RIGHT_PAREN)) {
      do {
        if (parameters.size() >= 255) {
          throw error(peek(), "Can't have more than 255 parameters.");
        }

        parameters.add(consume(IDENTIFIER, "Expect parameter name."));
      } while (match(COMMA));
    }

    consume(RIGHT_PAREN, "Expect ')' after parameters.");
  }

  private List<Stmt> block() {
    List<Stmt> statements = new ArrayList<>();

    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration());
    }

    consume(RIGHT_BRACE, "Expect '}' after block.");
    return statements;
  }

  private Stmt.MatchCase parseMatchCase() {
    Expr expr = expression();
    consume(ARROW, "Expect '->' after match pattern.");
    Stmt statement = statement();
    return new Stmt.MatchCase(expr, statement);
  }

  private List<Stmt.MatchCase> parseMatchCases() {
    List<Stmt.MatchCase> cases = new ArrayList<>();
    while (match(CASE)) {
      cases.add(parseMatchCase());
    }
    return cases;
  }

  private Stmt matchStatement() {
    Expr expression = expression();
    consume(LEFT_BRACE, "Expect '{' before match cases.");
    List<Stmt.MatchCase> cases = parseMatchCases();
    consume(RIGHT_BRACE, "Expect '}' after match cases.");
    return new Stmt.Match(expression, cases);
  }

  private Stmt throwStatement() {
    Token keyword = previous();
    Expr thrown = expression();
    consume(SEMICOLON, "Expect ';' after 'throw' value.");
    return new Stmt.Throw(keyword, thrown);
  }

  private Stmt tryStatement() {
    consume(LEFT_BRACE, "Expect '{' after 'try'.");
    List<Stmt> tryBody = block();

    consume(CATCH, "Expect 'catch' after 'try' block.");
    Token exception = consume(IDENTIFIER, "Expect exception name.");
    consume(LEFT_BRACE, "Expect '{' after exception name.");

    List<Stmt> catchBody = block();
    return new Stmt.TryCatch(tryBody, catchBody, exception);
  }

  private Expr dictionary() {
    Token brace = previous();

    Map<Token, Expr> keyValues = new HashMap<>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      if (match(SPREAD)) {
        Token spread = previous();
        Expr expr = expression();
        keyValues.put(spread, new Expr.Spread(spread, expr));
      } else {
        Token key = consume(IDENTIFIER, "Expect dictionary key.");
        consume(COLON, "Expect ':' after dictionary key.");

        Expr value = expression();
        keyValues.put(key, value);
      }

      if (!check(RIGHT_BRACE)) {
        consume(COMMA, "Expect ',' after dictionary value.");
      }
    }

    consume(RIGHT_BRACE, "Expect '}' at end of dictionary.");
    return new Expr.Dictionary(brace, keyValues);
  }

  private Expr array() {
    List<Expr> elements = new ArrayList<>();

    if (!check(RIGHT_BRACKET)) {
      do {
        elements.add(arrayElement());
      } while (match(COMMA));
    }

    Token bracket = consume(RIGHT_BRACKET, "Expect ']' after list initializer.");

    return new Expr.Array(bracket, elements);
  }

  private Expr arrayElement() {
    if (match(SPREAD)) {
      Token spread = previous();
      Expr expr = expression();
      return new Expr.Spread(spread, expr);
    } else {
      return expression();
    }
  }

  private Expr lambda() {
    boolean isAsync = match(ASYNC);
    consume(LEFT_PAREN, "Expect '(' after '" + (isAsync ? "async" : "fn") + "'.");
    List<Token> parameters = new ArrayList<>();
    List<Stmt> body = new ArrayList<>();

    if (!check(RIGHT_PAREN)) {
      do {
        if (parameters.size() >= 255) {
          throw error(peek(), "Can't have more than 255 parameters.");
        }

        parameters.add(consume(IDENTIFIER, "Expect parameter name."));
      } while (match(COMMA));
    }

    consume(RIGHT_PAREN, "Expect ')' after list of parameters.");

    if (match(ARROW)) {
      body.add(new Stmt.Return(new Token(RETURN, "return", "return", previous().line++, previous().column++), expression()));
    } else {
      consume(LEFT_BRACE, "Expect '{' before lambda body.");
      body = block();
    }

    return new Expr.Lambda(parameters, body, isAsync);
  }

  private Expr typeof() {
    consume(IDENTIFIER, "Expect type name.");
    Expr.Variable var = new Expr.Variable(previous());

    return new Expr.Typeof(var);
  }

  private Expr lazy() {
    Expr expr = expression();
    return new Expr.Lazy(expr);
  }

  private Expr assignment() {
    Expr expr = or();

    if (match(EQUAL)) {
      Token equals = previous();
      Expr value = assignment();

      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable) expr).name;
        return new Expr.Assign(name, value);
      } else if (expr instanceof Expr.Get get) {
        return new Expr.Set(get.object, get.name, value);
      } else if (expr instanceof Expr.SubscriptGet get) {
        return new Expr.SubscriptSet(get.indexee, get.bracket, get.index, value);
      }

      throw error(equals, "Invalid assignment target.");
    }

    if (match(PLUS_EQUAL)) {
      Token plusEqual = previous();
      Expr value = assignment();

      if (expr instanceof Expr.Variable var) {
        Token name = var.name;
        return new Expr.Assign(name, new Expr.Binary(expr, new Token(PLUS, "+", null, plusEqual.line, plusEqual.column + 1), value));
      }
    }

    if (match(MINUS_EQUAL)) {
      Token plusEqual = previous();
      Expr value = assignment();

      if (expr instanceof Expr.Variable var) {
        Token name = var.name;
        return new Expr.Assign(name, new Expr.Binary(expr, new Token(MINUS, "-", null, plusEqual.line, plusEqual.column + 1), value));
      }
    }

    return expr;
  }

  private Expr or() {
    Expr expr = and();

    while (match(LOGICAL_OR)) {
      Token operator = previous();
      Expr right = and();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr and() {
    Expr expr = equality();

    while (match(LOGICAL_AND)) {
      Token operator = previous();
      Expr right = equality();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  private Expr equality() {
    Expr expr = comparison();

    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr comparison() {
    Expr expr = instance();

    while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL, DOT_DOT)) {
      Token operator = previous();
      Expr right = instance();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr instance() {
    Expr expr = term();
    while (match(IS)) {
      Token op = previous();
      Expr right = term();
      expr = new Expr.Binary(expr, op, right);
    }

    return expr;
  }

  private Expr term() {
    Expr expr = factor();

    while (match(MINUS, PLUS)) {
      Token operator = previous();
      Expr right = factor();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr factor() {
    Expr expr = unary();

    while (match(SLASH, STAR, EXPONENTIATION, PERCENT)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private Expr unary() {
    if (match(BANG, MINUS, PLUS_PLUS, MINUS_MINUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }

    return call();
  }

  private Expr finishCall(Expr callee) {
    List<Expr> arguments = new ArrayList<>();
    if (!check(RIGHT_PAREN)) {
      do {
        if (arguments.size() >= 255) {
          throw error(peek(), "Can't have more than 255 arguments.");
        }

        arguments.add(expression());
      } while (match(COMMA));
    }

    Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

    return new Expr.Call(callee, paren, arguments);
  }

  private Expr finishIndexGet(Expr indexee) {
    Expr index = expression();
    Token bracket = consume(RIGHT_BRACKET, "Expect ']' after index.");
    return new Expr.SubscriptGet(indexee, bracket, index);
  }

  private Expr call() {
    Expr expr = primary();

    while (true) {
      if (match(LEFT_PAREN)) {
        expr = finishCall(expr);
      } else if (match(DOT)) {
        Token name;

        if (match(NUMBER)) {
          name = previous();
        } else {
          name = consume(IDENTIFIER, "Expect property name after '.'.");
        }

        expr = new Expr.Get(expr, name);
      } else if (match(LEFT_BRACKET)) {
        expr = finishIndexGet(expr);
      } else {
        break;
      }
    }

    return expr;
  }

  private Expr primary() {
    if (match(FALSE))
      return new Expr.Literal(false);
    if (match(TRUE))
      return new Expr.Literal(true);
    if (match(NULL))
      return new Expr.Literal(null);

    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }

    if (match(SUPER)) {
      Token keyword = previous();
      consume(DOT, "Expect '.' after 'super'.");
      Token method = consume(IDENTIFIER, "Expect superclass method name.");
      return new Expr.Super(keyword, method);
    }

    if (match(THIS))
      return new Expr.This(previous());

    if (match(IDENTIFIER)) {
      return new Expr.Variable(previous());
    }

    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      List<Expr> elements = new ArrayList<>();
      if (match(COMMA)) {
        Token token = previous();
        elements.add(expr);
        do {
          elements.add(expression());
        } while (match(COMMA));

        consume(RIGHT_PAREN, "Expect ')' after tuple expression.");
        return new Expr.TupleLiteral(elements, token);
      } else {
        consume(RIGHT_PAREN, "Expect ')' after expression.");
        return new Expr.Grouping(expr);
      }
    }

    if (match(LEFT_BRACKET)) {
      return array();
    }

    if (match(LEFT_BRACE)) {
      return dictionary();
    }

    if (match(FN)) {
      return lambda();
    }

    if (match(TYPEOF)) {
      return typeof();
    }

    if (match(LAZY)) {
      return lazy();
    }

    if (match(AWAIT)) {
      Expr value = expression();
      return new Expr.Await(previous(), value);
    }

    throw error(peek(), "Expect expression.");
  }

  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }

    return false;
  }

  private Token consume(TokenType type, String message) {
    if (check(type))
      return advance();
    throw error(peek(), message);
  }

  private boolean check(TokenType type) {
    if (isAtEnd())
      return false;
    return peek().type == type;
  }

  private Token advance() {
    if (!isAtEnd())
      current++;
    return previous();
  }

  private boolean isAtEnd() {
    return peek().type == EOF;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(current - 1);
  }

  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }

  @SuppressWarnings("incomplete-switch")
  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      if (previous().type == SEMICOLON)
        return;

      switch (peek().type) {
        case CLASS, ELSE, FALSE, FN, FOR, IF,
             NULL, RETURN, SUPER, THIS, TRUE,
             LET, WHILE, EXTENDS, IN, STATIC,
             MATCH, CASE, WITH, TRAIT, THROW,
             ENUM, IS, ABSTRACT, TYPEOF, LAZY,
             USING, CATCH, NAMESPACE, FROM,
             TRY, ASYNC, AWAIT:
          return;
      }

      advance();
    }
  }
}
