package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.lexer.Token;

public class UserRuntimeError extends RuntimeError {
    public LoxInstance instance;
    public Token token;

    public UserRuntimeError(LoxInstance instance, String thrown, String message, Token token) {
        super(token, thrown, message, null);
        this.instance = instance;
        this.token = token;
    }
}
