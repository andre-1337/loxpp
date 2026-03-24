package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.lexer.Token;

import java.util.HashMap;
import java.util.Map;

public class LoxModule {
    private final Map<String, Object> members = new HashMap<>();

    public void addMember(String name, Object member) {
        members.put(name, member);
    }

    public Object getMember(Token name) {
        if (members.containsKey(name.lexeme)) {
            return members.get(name.lexeme);
        }

        throw new RuntimeError(name, "RuntimeError", "Module does not contain member '" + name.lexeme + "'.", "Check the spelling of the namespace name.");
    }
}
