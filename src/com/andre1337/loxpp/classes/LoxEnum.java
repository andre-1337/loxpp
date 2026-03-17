package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.lexer.Token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoxEnum {
    public final String name;
    public final boolean isUnion;
    private final Map<String, LoxUnionConstructor> constructors = new HashMap<>();
    private final Map<String, LoxFunction> methods = new HashMap<>();

    public LoxEnum(String name, boolean isUnion) {
        this.name = name;
        this.isUnion = isUnion;
    }

    public void addConstructor(String caseName, List<Token> parameters) {
        constructors.put(caseName, new LoxUnionConstructor(caseName, parameters, this));
    }

    public LoxUnionConstructor getConstructor(String caseName) {
        return constructors.get(caseName);
    }

    public boolean hasCase(String caseName) {
        return constructors.containsKey(caseName);
    }

    public void addMethod(String name, LoxFunction method) {
        methods.put(name, method);
    }

    public LoxFunction getMethod(String name) {
        return methods.get(name);
    }

    public boolean hasMethod(String name) {
        return methods.containsKey(name);
    }

    @Override
    public String toString() {
        return "<enum " + name + ">";
    }
}