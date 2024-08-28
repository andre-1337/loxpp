package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.interpreter.Interpreter;
import com.andre1337.loxpp.lexer.Token;
import java.util.*;

public record LoxEnum(Token name, Map<String, Variant> variants) implements LoxCallable {
    public record Variant(Token name, List<Token> parameters) implements LoxCallable {

        @Override
        public int arity() {
            return parameters.size();
        }

        @Override
        public Object call(Interpreter interpreter, List<Object> arguments) {
            if (parameters.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("result", name);
                return result;
            } else {
                Map<String, Object> dict = new HashMap<>();
                for (int i = 0; i < parameters.size(); i++) {
                    dict.put(parameters.get(i).lexeme, arguments.get(i));
                }

                return dict;
            }
        }
    }

    public Variant getVariant(Token variant) {
        if (variants.containsKey(variant.lexeme)) return variants.get(variant.lexeme);
        else throw new RuntimeError(
                name,
                "RuntimeError",
                "Variant '" + variant.lexeme + "' does not exist in enum '" + name.lexeme + "'.",
                null
        );
    }

    @Override
    public int arity() {
        return -1;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        throw new RuntimeError(name, "RuntimeError", "Cannot directly call an enum. Call a variant or a method instead.", null);
    }
}
