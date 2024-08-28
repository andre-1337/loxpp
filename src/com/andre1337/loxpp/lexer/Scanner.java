package com.andre1337.loxpp.lexer;

import com.andre1337.loxpp.Lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.andre1337.loxpp.lexer.TokenType.*;

public class Scanner {
  public static final Map<String, TokenType> keywords;

  static {
    keywords = new HashMap<>();
    keywords.put("class", CLASS);
    keywords.put("else", ELSE);
    keywords.put("false", FALSE);
    keywords.put("for", FOR);
    keywords.put("fn", FN);
    keywords.put("if", IF);
    keywords.put("null", NULL);
    keywords.put("return", RETURN);
    keywords.put("super", SUPER);
    keywords.put("self", THIS);
    keywords.put("true", TRUE);
    keywords.put("let", LET);
    keywords.put("while", WHILE);
    keywords.put("extends", EXTENDS);
    keywords.put("in", IN);
    keywords.put("static", STATIC);
    keywords.put("match", MATCH);
    keywords.put("case", CASE);
    keywords.put("trait", TRAIT);
    keywords.put("with", WITH);
    keywords.put("throw", THROW);
    keywords.put("enum", ENUM);
    keywords.put("is", IS);
    keywords.put("abstract", ABSTRACT);
    keywords.put("typeof", TYPEOF);
    keywords.put("lazy", LAZY);
    keywords.put("break", BREAK);
    keywords.put("try", TRY);
    keywords.put("catch", CATCH);
    keywords.put("finally", FINALLY);
    keywords.put("namespace", NAMESPACE);
    keywords.put("using", USING);
    keywords.put("from", FROM);
    keywords.put("coroutine", COROUTINE);
    keywords.put("yield", YIELD);
    keywords.put("resume", RESUME);
  }

  private final String source;
  private final List<Token> tokens = new ArrayList<>();
  private int start = 0;
  private int current = 0;
  private int line = 1;
  private int column = 0;

  public Scanner(String source) {
    this.source = source;
  }

  public List<Token> scanTokens() {
    while (!isAtEnd()) {
      start = current;
      scanToken();
    }

    tokens.add(new Token(EOF, "", null, line, column + 1));
    return tokens;
  }

  private void scanToken() {
    char c = advance();
    switch (c) {
      case '(':
        addToken(LEFT_PAREN);
        break;

      case ')':
        addToken(RIGHT_PAREN);
        break;

      case '{':
        addToken(LEFT_BRACE);
        break;

      case '}':
        addToken(RIGHT_BRACE);
        break;

      case '[':
        addToken(LEFT_BRACKET);
        break;

      case ']':
        addToken(RIGHT_BRACKET);
        break;

      case ':':
        addToken(COLON);
        break;

      case ',':
        addToken(COMMA);
        break;

      case '%':
        addToken(PERCENT);
        break;

      case '|':
        addToken(match('|') ? LOGICAL_OR : BITWISE_OR);
        break;

      case '&':
        addToken(match('&') ? LOGICAL_AND : BITWISE_AND);
        break;

      case '?':
        addToken(match('?') ? QUESTION_QUESTION : QUESTION);
        break;

      case '.':
        addToken(match('.') ? (match('.') ? SPREAD : DOT_DOT) : DOT);
        break;

      case '-':
        if (match('>'))
          addToken(ARROW);
        else if (match('-'))
          addToken(MINUS_MINUS);
        else if (match('='))
          addToken(MINUS_EQUAL);
        else
          addToken(MINUS);
        break;

      case '+':
        if (match('+'))
          addToken(PLUS_PLUS);
        else if (match('='))
          addToken(PLUS_EQUAL);
        else
          addToken(PLUS);
        break;

      case ';':
        addToken(SEMICOLON);
        break;

      case '*':
        addToken(match('*') ? EXPONENTIATION : STAR);
        break;

      case '!':
        addToken(match('=') ? BANG_EQUAL : BANG);
        break;

      case '=':
        addToken(match('=') ? EQUAL_EQUAL : EQUAL);
        break;

      case '<':
        addToken(match('=') ? LESS_EQUAL : LESS);
        break;

      case '>':
        addToken(match('=') ? GREATER_EQUAL : GREATER);
        break;

      case '/':
        if (match('/')) {
          while (peek() != '\n' && !isAtEnd())
            advance();
        } else {
          addToken(SLASH);
        }
        break;

      case ' ':
      case '\r':
      case '\t':
        break;

      case '\n':
        line++;
        column = 0;
        break;

      case '"':
        string();
        break;

      default:
        if (isDigit(c)) {
          number();
        } else if (isAlpha(c)) {
          identifier();
        } else {
          Lox.error(line, column, "Unexpected character.");
        }
        break;
    }
  }

  private void identifier() {
    while (isAlphaNumeric(peek()))
      advance();

    String text = source.substring(start, current);
    TokenType type = keywords.get(text);
    if (type == null)
      type = IDENTIFIER;
    addToken(type);
  }

  private void number() {
    while (isDigit(peek()))
      advance();

    if (peek() == '.' && isDigit(peekNext())) {
      advance();

      while (isDigit(peek()))
        advance();
    }

    addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
  }

  private char lexEscapeCharacter() {
    if (isAtEnd()) return '\0';
    char next = advance();
    return switch (next) {
      case '0' -> '\0';
      case 'n' -> '\n';
      case 't' -> '\t';
      case 'r' -> '\r';
      case 'b' -> '\b';
      case 'f' -> '\f';
      case '"' -> '"';
      case '\\' -> '\\';
      default -> next;
    };
  }

  private void string() {
    StringBuilder value = new StringBuilder();

    while (peek() != '"' && !isAtEnd()) {
      if (peek() == '\n') {
        line++;
      } else if (peek() == '\\') {
        advance();
        char escapedChar = lexEscapeCharacter();
        value.append(escapedChar);
      } else {
        value.append(advance());
      }
    }

    if (isAtEnd()) {
      Lox.error(line, column, "Unterminated string.");
      return;
    }

    advance();
    addToken(STRING, value.toString());
  }

  private boolean match(char expected) {
    if (isAtEnd())
      return false;
    if (source.charAt(current) != expected)
      return false;

    current++;
    column++;
    return true;
  }

  private boolean checkSequence(String expected) {
    if (isAtEnd())
      return false;
    if (current + expected.length() > source.length())
      return false;

    advance();
    advance();
    return source.startsWith(expected, start);
  }

  private char peek() {
    if (isAtEnd())
      return '\0';
    return source.charAt(current);
  }

  private char peekNext() {
    if (current + 1 >= source.length())
      return '\0';
    return source.charAt(current + 1);
  }

  private boolean isAlpha(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
  }

  private boolean isAlphaNumeric(char c) {
    return isAlpha(c) || isDigit(c);
  }

  private boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private boolean isAtEnd() {
    return current >= source.length();
  }

  private char advance() {
    column++;
    return source.charAt(current++);
  }

  private void addToken(TokenType type) {
    addToken(type, null);
  }

  private void addToken(TokenType type, Object literal) {
    String text = source.substring(start, current);
    tokens.add(new Token(type, text, literal, line, column));
  }
}
