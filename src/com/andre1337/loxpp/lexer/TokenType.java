package com.andre1337.loxpp.lexer;

public enum TokenType {
  // Single character tokens
  LEFT_PAREN, RIGHT_PAREN,
  LEFT_BRACE, RIGHT_BRACE,
  LEFT_BRACKET, RIGHT_BRACKET,
  COLON, COMMA, SEMICOLON,
  SLASH, ARROW, PERCENT,
  QUESTION,

  // Tokens with single and multiple character representations
  BANG, BANG_EQUAL,
  EQUAL, EQUAL_EQUAL,
  GREATER, GREATER_EQUAL,
  LESS, LESS_EQUAL,
  DOT, DOT_DOT,
  STAR, EXPONENTIATION,
  PLUS, PLUS_PLUS,
  MINUS, MINUS_MINUS,
  SPREAD,
  QUESTION_QUESTION,
  BITWISE_AND, BITWISE_OR,
  LOGICAL_AND, LOGICAL_OR,
  MINUS_EQUAL, PLUS_EQUAL,

  // Representation types
  IDENTIFIER, STRING, NUMBER,

  // Keywords
  CLASS, ELSE, FALSE, FN, FOR, IF,
  NULL, RETURN, SUPER, THIS, TRUE,
  LET, WHILE, EXTENDS, IN, STATIC,
  MATCH, CASE, WITH, TRAIT, THROW,
  ENUM, IS, ABSTRACT, TYPEOF, LAZY,
  USING, CATCH, NAMESPACE, FROM,
  TRY, ASYNC, AWAIT, PRIVATE,

  // Special tokens
  EOF,
}
