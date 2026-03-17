package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.lexer.Token;

public interface LoxIndexable {
    Object get(Token token, Object index);

    void set(Token token, Object index, Object item);

    int length();
}
