package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.lexer.Token;

import java.util.*;

public record LoxTrait(Token name, Map<String, LoxFunction> methods) {
    @Override
    public String toString() {
        return "<trait " + name.lexeme + ">";
    }
}
