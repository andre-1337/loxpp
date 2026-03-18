package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.lexer.Token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoxUnionInstance {
    public final LoxEnum enumType;
    public final String caseName;
    public final Map<String, Object> fields = new HashMap<>();
    private final List<Token> parameters;

    public LoxUnionInstance(LoxEnum enumType, String caseName, List<Token> parameters, List<Object> values) {
        this.enumType = enumType;
        this.caseName = caseName;
        this.parameters = parameters;

        for (int i = 0; i < parameters.size(); i++) {
            fields.put(parameters.get(i).lexeme, values.get(i));
        }
    }

    public Object get(Token name) {
        if (fields.containsKey(name.lexeme)) {
            return fields.get(name.lexeme);
        }

        LoxFunction method = enumType.getMethod(name.lexeme);
        if (method != null) {
            return method.bind(this);
        }

        return switch (name.lexeme) {
            case "case" -> caseName;
            case "is" -> new LoxUnionPredicate(name);
            default -> throw new RuntimeError(name, "RuntimeError", "Undefined property '" + name.lexeme + "'.", null);
        };
    }

    public String getFieldName(int idx) {
        if (idx < 0 || idx >= parameters.size()) {
            throw new IndexOutOfBoundsException("Invalid field index.");
        }

        return parameters.get(idx).lexeme;
    }

    @Override
    public String toString() {
        return enumType.name + "." + caseName + fields.values();
    }
}
