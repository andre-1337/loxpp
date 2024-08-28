package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.interpreter.Interpreter;
import com.google.common.base.Supplier;

public class LoxLazy {
    private final Supplier<Object> supplier;
    private Object value;
    private boolean isEvaluated = false;

    public LoxLazy(Supplier<Object> supplier) {
        this.supplier = supplier;
    }

    public Object get() {
        if (!isEvaluated) {
            value = supplier.get();
            isEvaluated = true;
        }

        return value;
    }

    @Override
    public String toString() {
        return Interpreter.stringify(get());
    }
}
