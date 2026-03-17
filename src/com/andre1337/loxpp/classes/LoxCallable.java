package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.interpreter.Interpreter;

import java.util.List;

public interface LoxCallable {
  int arity();

  Object call(Interpreter interpreter, List<Object> arguments);
}
