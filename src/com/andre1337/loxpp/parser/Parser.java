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
      if (match(IMPL))
        return implDeclaration();
      if (match(EXPORT))
        return exportDeclaration();

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
        body.add(
                new Stmt.Expression(
                        new Expr.Set(
                                new Expr.This(
                                        new Token(
                                                THIS,
                                                "self",
                                                null,
                                                0,
                                                0
                                        )
                                ),
                                field.name,
                                field
                        )
                )
        );
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
                      fields.stream().map(field -> new Stmt.Function.Param(
                              new Token(IDENTIFIER, field.name.lexeme, null, 0, 0),
                              null)
                      ).toList(),
                      body,
                      false,
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
        boolean isPrivate = match(PRIVATE);
        boolean isStatic = match(STATIC);
        consume(FN, "Expect 'fn' keyword before method declaration.");
        boolean isAsync = match(ASYNC);
        (isStatic ? staticMethods : methods).add(function("method", isAsync, isPrivate));
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
      return cStyleForStatement(previous());
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

  private Stmt cStyleForStatement(Token keyword) {
    Stmt initializer = varDeclaration();

    Expr condition = null;
    if (!check(SEMICOLON)) {
      condition = expression();
    }

    consume(SEMICOLON, "Expect ';' after loop condition.");

    Expr increment = null;
    if (!check(RIGHT_PAREN)) {
      increment = expression();
    }

    consume(RIGHT_PAREN, "Expect ')' after 'for' clauses.");
    consume(LEFT_BRACE, "Expect '{' because 'for' body.");

    List<Stmt> body = block();

    if (condition == null) condition = new Expr.Literal(true);

    return new Stmt.For(keyword, initializer, condition, increment, body);
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

  private Stmt enumDeclaration() {
    Token name = consume(IDENTIFIER, "Expect enum name.");
    boolean isUnion = match(UNION);

    consume(LEFT_BRACE, "Expect '{' before enum body.");

    List<Stmt.EnumCase> cases = new ArrayList<>();

    do {
      Token caseName = consume(IDENTIFIER, "Expect case name.");
      List<Token> parameters = new ArrayList<>();

      if (match(LEFT_PAREN) && isUnion) {
        if (!check(RIGHT_PAREN)) {
          do {
            parameters.add(consume(IDENTIFIER, "Expect parameter name."));
          } while (match(COMMA));
        }

        consume(RIGHT_PAREN, "Expect ')' after parameters.");
      }

      cases.add(new Stmt.EnumCase(caseName, parameters));
    } while (match(COMMA, BITWISE_OR) && !check(RIGHT_BRACE));

    consume(RIGHT_BRACE, "Expect '}' after enum cases.");

    return new Stmt.Enum(name, cases, isUnion);
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

  private Stmt implDeclaration() {
    Token keyword = previous();
    consume(IDENTIFIER, "Expect type name.");
    Expr.Variable name = new Expr.Variable(previous());
    consume(LEFT_BRACE, "Expect '{' before impl body.");

    List<Stmt.Function> methods = new ArrayList<>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      consume(FN, "Expect 'fn' before method name.");
      boolean isAsync = match(ASYNC);
      methods.add(function("method", isAsync, false));
    }

    consume(RIGHT_BRACE, "Expect '}' after impl body.");
    return new Stmt.Impl(keyword, name, methods);
  }

  private Stmt exportDeclaration() {
    List<Token> names = new ArrayList<>();

    do {
      names.add(consume(IDENTIFIER, "Expect identifier to export."));
    } while (match(COMMA));

    consume(SEMICOLON, "Expect ';' after export name.");
    return new Stmt.Export(names);
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
    return function(isAsync ? "async function" : "function", isAsync, false);
  }

  private List<Stmt.Function.Param> parameters() {
    List<Stmt.Function.Param> params = new ArrayList<>();
    boolean hasSeenDefault = false;

    if (!check(RIGHT_PAREN)) {
      do {
        Token name = consume(IDENTIFIER, "Expect parameter name.");
        Expr defaultValue = null;

        if (match(EQUAL)) {
          hasSeenDefault = true;
          defaultValue = expression();
        } else {
          if (hasSeenDefault) {
            throw error(previous(), "Required parameter '" + name.lexeme + "' cannot come after a parameter with a default value.");
          }
        }

        if (params.size() >= 255) {
          throw error(peek(), "Can't have more than 255 parameters.");
        }

        params.add(new Stmt.Function.Param(name, defaultValue));
      } while (match(COMMA));
    }

    consume(RIGHT_PAREN, "Expect ')' after parameters.");

    return params;
  }

  private Stmt.Function function(String kind, boolean isAsync, boolean isPrivate) {
    Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
    consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");

    List<Stmt.Function.Param> parameters = parameters();
    List<Stmt> body = new ArrayList<>();

    if (match(ARROW)) {
      body.add(new Stmt.Return(new Token(RETURN, "return", "return", previous().line++, previous().column++), expression()));
      consume(SEMICOLON, "Expect ';' after arrow " + kind + " declaration.");
    } else {
      consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
      body = block();
    }

    return new Stmt.Function(name, parameters, body, false, isAsync, isPrivate);
  }

  private Stmt.Function traitMethod(boolean isAbstract) {
    boolean isAsync;

    if (isAbstract) {
      consume(FN, "Expect 'fn' keyword before method name");
      isAsync = match(ASYNC);

      Token name = consume(IDENTIFIER, "Expect trait method name.");
      consume(LEFT_PAREN, "Expect '(' after trait method name.");

      List<Stmt.Function.Param> parameters = parameters();
      consume(SEMICOLON, "Expect ';' after abstract trait method.");

      return new Stmt.Function(name, parameters, null, true, isAsync, false);
    } else if (match(FN)) {
      isAsync = match(ASYNC);
      return function("trait method", isAsync, false);
    }

    return null;
  }

  private List<Stmt> block() {
    List<Stmt> statements = new ArrayList<>();

    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration());
    }

    consume(RIGHT_BRACE, "Expect '}' after block.");
    return statements;
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
        Token key = consume(STRING, "Expect dictionary key.");
        key.literal = ((String) key.literal).replace("\"", "");
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
    consume(LEFT_PAREN, "Expect '(' after 'fn'.");
    List<Stmt.Function.Param> parameters = parameters();
    List<Stmt> body = new ArrayList<>();

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

    return new Expr.Call(callee, paren, arguments, null);
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

    if (match(STRING_PART)) {
      return finishInterpolation(previous().literal);
    }

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

    if (match(MATCH))
      return matchExpression();

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

    if (match(NEW)) {
      Token keyword = previous();
      Expr callee = primary();

      while (match(DOT)) {
        Token name = consume(IDENTIFIER, "Expect property name after '.'.");
        callee = new Expr.Get(callee, name);
      }

      consume(LEFT_PAREN, "Expect '(' after class name.");
      List<Expr> arguments = new ArrayList<>();
      if (!check(RIGHT_PAREN)) {
        do {
          arguments.add(expression());
        } while (match(COMMA));
      }
      Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

      Expr.Call constructorCall = new Expr.Call(callee, paren, arguments, null);

      return new Expr.New(keyword, constructorCall);
    }

    throw error(peek(), "Expect expression.");
  }

  private Expr finishInterpolation(Object firstPart) {
    Expr expr = new Expr.Literal(firstPart);

    while (true) {
      if (match(INTERPOLATION_START)) {
        Expr inner = expression();
        consume(RIGHT_BRACE, "Expect '}' after interpolation expression.");

        Token plus = new Token(PLUS, "+", null, previous().line, previous().column);
        expr = new Expr.Binary(expr, plus, inner);
      }

      if (match(STRING_PART)) {
        Token plus = new Token(PLUS, "+", null, previous().line, previous().column);
        expr = new Expr.Binary(expr, plus, new Expr.Literal(previous().literal));
      } else if (match(STRING)) {
        Token plus = new Token(PLUS, "+", null, previous().line, previous().column);
        expr = new Expr.Binary(expr, plus, new Expr.Literal(previous().literal));
        break;
      } else {
        break;
      }
    }

    return expr;
  }

  private Expr matchExpression() {
    Token keyword = previous();
    Expr value = expression();
    consume(LEFT_BRACE, "Expect '{' before match cases.");

    List<Expr.MatchCase> cases = new ArrayList<>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      cases.add(matchCase());
    }

    consume(RIGHT_BRACE, "Expect '}' after match cases.");
    return new Expr.Match(keyword, value, cases);
  }

  private Expr.MatchCase matchCase() {
    Expr pattern = pattern();
    Expr guard = null;

    if (match(IF)) {
      guard = expression();
    }

    consume(ARROW, "Expect '->' after match pattern.");
    List<Stmt> body;

    if (match(LEFT_BRACE)) {
      body = block();
    } else {
      Expr expr = expression();
      body = List.of(new Stmt.Return(new Token(RETURN, "return", "return", previous().line++, previous().column++), expr));
      consume(SEMICOLON, "Expect ';' after match case body.");
    }

    return new Expr.MatchCase(pattern, guard, body);
  }

  private Expr pattern() {
    if (match(STAR)) {
      return new Expr.WildcardPattern(previous());
    }

    if (match(LEFT_BRACKET)) {
      return listPattern();
    }

    if (match(LEFT_BRACE)) {
      return objectPattern();
    }

    if (check(IDENTIFIER) && checkNext(LEFT_PAREN)) {
      Token caseName = consume(IDENTIFIER, "Expect case name.");
      consume(LEFT_PAREN, "Expect '(' after case name.");

      List<Token> bindings = new ArrayList<>();
      if (!check(RIGHT_PAREN)) {
        do {
          bindings.add(consume(IDENTIFIER, "Expect variable name."));
        } while (match(COMMA));
      }

      consume(RIGHT_PAREN, "Expect ')' after pattern bindings.");
      return new Expr.UnionPattern(caseName, bindings);
    }

    if (match(IDENTIFIER)) {
      return new Expr.Variable(previous());
    }

    return primary();
  }

  private Expr listPattern() {
    List<Expr> elements = new ArrayList<>();
    Expr rest = null;

    while (!check(RIGHT_BRACKET) && !isAtEnd()) {
      if (match(SPREAD)) {
        rest = expression();
        break;
      }

      elements.add(expression());

      if (!check(RIGHT_BRACKET)) {
        consume(COMMA, "Expect ',' after list pattern elements.");
      }
    }

    consume(RIGHT_BRACKET, "Expect ']' after list pattern.");
    return new Expr.ListPattern(elements, rest);
  }

  private Expr objectPattern() {
    List<Expr.ObjectPattern.Property> properties = new ArrayList<>();
    Expr rest = null;

    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      if (match(SPREAD)) {
        rest = expression();
        break;
      }

      Token name = consume(IDENTIFIER, "Expect property name.");
      consume(COLON, "Expect ':' after property name.");
      Expr pattern = expression();

      properties.add(new Expr.ObjectPattern.Property(name, pattern));

      if (!check(RIGHT_BRACE)) {
        consume(COMMA, "Expect ',' after property pattern.");
      }
    }

    consume(RIGHT_BRACE, "Expect '}' after object pattern.");
    return new Expr.ObjectPattern(properties, rest);
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

  @SuppressWarnings("SameParameterValue")
  private boolean checkNext(TokenType type) {
    if (isAtEnd()) return false;
    if (tokens.get(current + 1).type == EOF) return false;
    return tokens.get(current + 1).type == type;
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
        case CLASS, ELSE, FALSE, FN, FOR, IF, NULL,
             RETURN, SUPER, THIS, TRUE, LET, WHILE,
             EXTENDS, IN, STATIC, WITH,
             TRAIT, THROW, ENUM, IS, ABSTRACT, TYPEOF,
             LAZY, TRY, CATCH, FINALLY, NAMESPACE,
             USING, FROM:

          return;
      }

      advance();
    }
  }
}
