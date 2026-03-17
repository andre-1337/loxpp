package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.interpreter.Interpreter;
import com.andre1337.loxpp.lexer.Token;

import java.util.List;

public class LoxUnionConstructor implements LoxCallable {
    private final String name;
    private final List<Token> parameters;
    private final LoxEnum enumType;

    public LoxUnionConstructor(String name, List<Token> parameters, LoxEnum enumType) {
        this.name = name;
        this.parameters = parameters;
        this.enumType = enumType;
    }

    @Override
    public int arity() {
        return parameters.size();
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        if (arguments.size() != parameters.size()) {
            throw new RuntimeError(null, "RuntimeError", "Expected " + parameters.size() + " arguments but got " + arguments.size() + " for case " + name, null);
        }

        return new LoxUnionInstance(enumType, name, parameters, arguments);
    }

    @Override
    public String toString() {
        return "<constructor " + enumType.name + "." + name + ">";
    }
}
