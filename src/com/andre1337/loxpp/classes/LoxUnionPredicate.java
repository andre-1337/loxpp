package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.interpreter.Interpreter;
import com.andre1337.loxpp.lexer.Token;

import java.util.List;

public class LoxUnionPredicate implements LoxCallable {
    private final Token methodName;

    public LoxUnionPredicate(Token methodName) {
        this.methodName = methodName;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        if (arguments.size() != 1) {
            throw new RuntimeError(methodName, "RuntimeError", "Expected 1 argument for 'is' method.", null);
        }

        return null;
    }

    @Override
    public int arity() {
        return 1;
    }
}
