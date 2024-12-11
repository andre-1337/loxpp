package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.ast.Stmt;
import com.andre1337.loxpp.lexer.Token;

import java.util.*;

public class LoxInstance {
  public LoxClass klass;
  public final Map<String, Object> fields = new HashMap<>();

  public LoxInstance(LoxClass klass) {
    this.klass = klass;
  }

  public Object get(Token name) {
    if (fields.containsKey(name.lexeme)) {
      return fields.get(name.lexeme);
    }

    LoxFunction method = klass.findMethod(name.lexeme);
    if (method != null) {
      return method.bind(this);
    }

    throw new RuntimeError(name, "RuntimeError", "Undefined property '" + name.lexeme + "'.", null);
  }

  public void set(Token name, Object value) {
    fields.put(name.lexeme, value);
  }

  @Override
  public String toString() {
    if (klass.methods.containsKey("to_string") && !klass.traits.containsKey("Printable")) {
      throw new RuntimeError(klass.token, "RuntimeError", "Class must implement trait 'Printable' to declare a 'to_string' method.", null);
    } else if (klass.traits.containsKey("Printable") && klass.methods.containsKey("to_string")) {
      Object method = klass.methods.get("to_string").bind(this).call(klass.interpreter, new ArrayList<>());
      if (method instanceof LoxString str) {
        return str.value;
      } else {
        return (String) method;
      }
    }

    return "<instance " + klass.name + ">";
  }
}
