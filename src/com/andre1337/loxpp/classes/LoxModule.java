package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.lexer.Token;

import java.util.HashMap;
import java.util.Map;

public class LoxModule {
    private final Map<String, LoxNamespace> namespaces = new HashMap<>();

    public void addNamespace(String name, LoxNamespace namespace) {
        namespaces.put(name, namespace);
    }

    public Object getMember(Token name) {
        if (namespaces.containsKey(name.lexeme)) {
            return namespaces.get(name.lexeme);
        }

        throw new RuntimeError(name, "RuntimeError", "Module does not contain member '" + name.lexeme + "'.", "Check the spelling of the namespace name.");
    }
}
