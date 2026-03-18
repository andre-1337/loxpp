package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.lexer.Token;

import java.util.*;

public class LoxNamespace {
    public final String name;
    public final Map<String, Object> members  = new HashMap<>();
    public final Set<String> publicNames = new HashSet<>();

    public LoxNamespace(String name) {
        this.name = name;
    }

    public void define(String name, Object value) {
        members.put(name, value);
    }

    public void markAsPublic(String name) {
        publicNames.add(name);
    }

    public Object getExported(Token name) {
        if (publicNames.contains(name.lexeme)) {
            return members.get(name.lexeme);
        }

        if (members.containsKey(name.lexeme)) {
            throw new RuntimeError(name, "RuntimeError", "Member '" + name.lexeme + "' is internal to namespace '" + this.name + "'.", null);
        }

        throw new RuntimeError(name, "RuntimeError", "Undefined member '" + name.lexeme + "'.", null);
    }

    @Override
    public String toString() {
        return "<namespace " + name + ">";
    }
}
