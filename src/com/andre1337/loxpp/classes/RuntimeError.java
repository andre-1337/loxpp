package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.lexer.Token;

public class RuntimeError extends RuntimeException {
  public Token token;
  public String thrown;
  public String message;
  public String hint;

  public RuntimeError(Token token, String thrown, String message, String hint) {
    super(thrown + ": " + message);

    this.token = token;
    this.thrown = thrown;
    this.message = message;
    this.hint = hint;
  }
}
