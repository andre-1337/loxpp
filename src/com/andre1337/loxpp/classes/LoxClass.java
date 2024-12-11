package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.interpreter.Interpreter;
import com.andre1337.loxpp.lexer.Token;

import java.util.*;

public class LoxClass extends LoxInstance implements LoxCallable {
  public final String name;
  public final Token token;
  public final LoxClass superclass;
  public final Map<String, LoxTrait> traits;
  public final Map<String, LoxFunction> methods;
  public final Interpreter interpreter;

  public LoxClass(
          LoxClass meta,
          String name,
          Token token,
          LoxClass superclass,
          Map<String, LoxFunction> methods,
          Interpreter interpreter
  ) {
    super(meta);

    this.superclass = superclass;
    this.traits = new HashMap<>();
    this.name = name;
    this.token = token;
    this.methods = methods;
    this.interpreter = interpreter;
  }

  public void addTrait(LoxTrait trait) {
    traits.put(trait.name().lexeme, trait);
  }

  public LoxFunction findMethod(String name) {
    if (methods.containsKey(name)) {
      return methods.get(name);
    }

    if (superclass != null) {
      return superclass.findMethod(name);
    }

    return null;
  }

  public boolean hasTrait(LoxTrait trait) {
    return traits.containsKey(trait.name().lexeme) || (superclass != null && superclass.traits.containsKey(trait.name().lexeme));
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    LoxInstance instance = new LoxInstance(this);
    LoxFunction initializer = findMethod("init");

    if (initializer != null) {
      initializer.bind(instance).call(interpreter, arguments);
    }

    return instance;
  }

  @Override
  public int arity() {
    LoxFunction initializer = findMethod("init");
    if (initializer == null)
      return 0;
    return initializer.arity();
  }
}
