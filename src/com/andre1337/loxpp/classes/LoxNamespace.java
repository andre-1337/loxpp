package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.lexer.Token;

import java.util.*;

public class LoxNamespace {
    public final String name;
    public final Map<String, Object> members  = new HashMap<>();

    public LoxNamespace(String name) {
        this.name = name;
    }

    public void define(String name, Object value) {
        members.put(name, value);
    }

    public Object get(Token obj) {
        if (members.containsKey(obj.lexeme)) {
            return members.get(obj.lexeme);
        }

        throw new RuntimeError(obj, "RuntimeError", "Undefined member '" + obj.lexeme + "' in namespace '" + name + "'.", null);
    }

    @Override
    public String toString() {
        return "<namespace " + name + ">";
    }
}
