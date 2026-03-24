package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.lexer.Token;

import java.util.ArrayList;

public class LoxInstance {
  public LoxClass klass;
  public Object[] fields;

  public LoxInstance(LoxClass klass) {
    this.klass = klass;

    if (klass != null) {
      this.fields = new Object[klass.fieldLayout.size()];
    }
  }

  public Object get(Token name) {
    Integer index = klass.fieldLayout.get(name.lexeme);
    if (index != null) {
      return fields[index];
    }

    LoxFunction method = klass.findMethod(name.lexeme);
    if (method != null) return method.bind(this);

    if (klass.meta != null) {
      LoxFunction staticMethod = klass.meta.findMethod(name.lexeme);

      if (staticMethod != null) {
        throw new RuntimeError(name, "RuntimeError", "Static method '" + name.lexeme + "' can only be called on the class '" + klass.name + "', not on an instance.", null);
      }
    }

    throw new RuntimeError(name, "RuntimeError", "Undefined property '" + name.lexeme + "'.", null);
  }

  public void set(Token name, Object value) {
    Integer index = klass.fieldLayout.get(name.lexeme);

    if (index == null) {
      if (klass.isShapeLocked) {
        throw new RuntimeError(name, "RuntimeError", "Cannot dynamically add property '" + name.lexeme + "' to locked class '" + klass.name + "'.", "Make sure all properties are declared inside the init() method.");
      }

      index = klass.fieldLayout.size();
      klass.fieldLayout.put(name.lexeme, index);

      Object[] newFields = new Object[index + 1];
      if (fields != null) {
        System.arraycopy(fields, 0, newFields, 0, fields.length);
      }

      fields = newFields;
    }

    fields[index] = value;
  }

  @Override
  public String toString() {
    if (klass.methods.containsKey("to_string") && !klass.traits.containsKey("Printable")) {
      throw new RuntimeError(klass.token, "RuntimeError", "Class must implement trait 'Printable' to declare a 'to_string' method.", null);
    } else if (klass.traits.containsKey("Printable") && klass.methods.containsKey("to_string")) {
      Object method = klass.methods.get("to_string").bind(this).call(klass.interpreter, new ArrayList<>(), false);
      if (method instanceof LoxString str) {
        return str.value;
      } else {
        return (String) method;
      }
    }

    return "<instance " + klass.name + ">";
  }
}
