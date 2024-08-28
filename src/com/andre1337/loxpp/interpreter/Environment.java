package com.andre1337.loxpp.interpreter;

import com.andre1337.loxpp.classes.RuntimeError;
import com.andre1337.loxpp.lexer.Token;

import java.util.*;

public class Environment {
  public Environment enclosing;
  public final Map<String, Object> values = new HashMap<>();

  public Environment() {
    enclosing = null;
  }

  public Environment(Environment enclosing) {
    this.enclosing = enclosing;
  }

  public Object get(Token name) {
    if (values.containsKey(name.lexeme)) {
      return values.get(name.lexeme);
    }

    if (enclosing != null)
      return enclosing.get(name);

    throw new RuntimeError(name, "RuntimeError", "Undefined variable '" + name.lexeme + "'.", null);
  }

  public void assign(Token name, Object value) {
    if (values.containsKey(name.lexeme)) {
      values.put(name.lexeme, value);
      return;
    }

    if (enclosing != null) {
      enclosing.assign(name, value);
      return;
    }

    throw new RuntimeError(name, "RuntimeError", "Undefined variable '" + name.lexeme + "'.", null);
  }

  public void define(String name, Object value) {
    values.put(name, value);
  }

  public Environment ancestor(int distance) {
    Environment environment = this;
    for (int i = 0; i < distance; i++) {
      environment = environment.enclosing;
    }

    return environment;
  }

  public Object getAt(int distance, String name) {
    return ancestor(distance).values.get(name);
  }

  public void assignAt(int distance, Token name, Object value) {
    ancestor(distance).values.put(name.lexeme, value);
  }

  @Override
  public String toString() {
    String result = values.toString();
    if (enclosing != null) {
      result += " -> " + enclosing;
    }

    return result;
  }
}
