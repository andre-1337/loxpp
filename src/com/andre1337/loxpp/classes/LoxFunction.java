package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.ast.Stmt;
import com.andre1337.loxpp.interpreter.Environment;
import com.andre1337.loxpp.interpreter.Interpreter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public record LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer, boolean isAbstract, boolean isAsync) implements LoxCallable {
  public LoxFunction bind(LoxInstance instance) {
    Environment environment = new Environment(closure);
    environment.define("self", instance);

    return new LoxFunction(declaration, environment, isInitializer, isAbstract, isAsync);
  }

  @Override
  public String toString() {
    return declaration.name == null ? "<lambda fn>" : "<fn " + declaration.name.lexeme + ">";
  }

  @Override
  public int arity() {
    return declaration.params.size();
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    Environment environment = new Environment(closure);

    for (int i = 0; i < declaration.params.size(); i++) {
      environment.define(declaration.params.get(i).lexeme, arguments.get(i));
    }

    if (isAsync) {
      return CompletableFuture.supplyAsync(() -> {
        try {
          interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {
          if (isInitializer) return closure.getAt(0, "self");
          return returnValue.value;
        }

        if (isInitializer) return closure.getAt(0, "self");

        return null;
      });
    } else {
      try {
        interpreter.executeBlock(declaration.body, environment);
      } catch (Return returnValue) {
        if (isInitializer) return closure.getAt(0, "self");
        return returnValue.value;
      }

      if (isInitializer) return closure.getAt(0, "self");
    }

    return null;
  }
}
