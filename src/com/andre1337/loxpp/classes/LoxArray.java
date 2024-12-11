package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.interpreter.Interpreter;
import com.andre1337.loxpp.lexer.Token;

import java.util.*;
import java.util.stream.Collectors;

public class LoxArray implements LoxIndexable {
    public final Interpreter interpreter;
    public final List<Object> elements;
    private final Map<String, LoxCallable> methods;

    private final static String BOUNDS_ERROR_MSG = "Array index is out of bounds.";
    private final static String EMPTY_ERROR_MSG = "Array is empty.";
    private static final String INVALID_INDEX_ERROR_MSG = "Index is invalid.";

    public LoxArray(Interpreter interpreter, List<Object> elements) {
        this.interpreter = interpreter;
        this.elements = elements;

        this.methods = createMethods(this);
    }

    private static Map<String, LoxCallable> createMethods(LoxArray array) {
        Map<String, LoxCallable> methods = new HashMap<>();

        methods.put("get", new LoxCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                int idx = (Integer) arguments.getFirst();

                try {
                    System.out.println(array.elements.get(idx));
                    return array.elements.get(idx);
                } catch (NumberFormatException | ClassCastException e) {
                    throw new RuntimeError((Token) array.elements.get(idx), "RuntimeError", INVALID_INDEX_ERROR_MSG, null);
                } catch (IndexOutOfBoundsException e) {
                    throw new RuntimeError((Token) array.elements.get(idx), "RuntimeError", BOUNDS_ERROR_MSG, null);
                }
            }
        });

        methods.put("insert", new LoxCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                array.elements.addAll(arguments);
                return null;
            }
        });

        methods.put("pop", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                try {
                    return array.elements.removeFirst();
                } catch (IndexOutOfBoundsException e) {
                    throw new RuntimeError((Token) array.elements.getFirst(), "RuntimeError", EMPTY_ERROR_MSG, null);
                }
            }
        });

        methods.put("remove", new LoxCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Double index = (Double) arguments.getFirst();
                int idx = index.intValue();

                try {
                    return array.elements.remove(idx);
                } catch (NumberFormatException e) {
                    throw new RuntimeError((Token) array.elements.get(idx), "RuntimeError", INVALID_INDEX_ERROR_MSG, null);
                } catch (IndexOutOfBoundsException e) {
                    throw new RuntimeError((Token) array.elements.get(idx), "RuntimeError", BOUNDS_ERROR_MSG, null);
                }
            }
        });

        methods.put("len", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double) array.length();
            }
        });

        methods.put("shuffle", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Collections.shuffle(array.elements);
                return null;
            }
        });

        methods.put("is_empty", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return array.elements.isEmpty();
            }
        });

        methods.put("clear", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                array.elements.clear();
                return null;
            }
        });

        methods.put("map", new LoxCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                LoxFunction callback = (LoxFunction) arguments.getFirst();
                List<Object> result = new ArrayList<>();

                for (Object item : array.elements) {
                    result.add(callback.call(interpreter, List.of(item)));
                }

                return new LoxArray(interpreter, result);
            }
        });

        methods.put("filter", new LoxCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                LoxFunction callback = (LoxFunction) arguments.getFirst();
                List<Object> result = new ArrayList<>();

                for (Object item : array.elements) {
                    Object returnValue = callback.call(array.interpreter, List.of(item));
                    if (returnValue != null && (boolean) returnValue) {
                        result.add(item);
                    }
                }

                return new LoxArray(array.interpreter, result);
            }
        });

        methods.put("reduce", new LoxCallable() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                LoxFunction callback = (LoxFunction) arguments.getFirst();
                Object accumulator = arguments.get(1);

                for (Object item : array.elements) {
                    assert accumulator != null;
                    accumulator = callback.call(array.interpreter, List.of(accumulator, item));
                }

                return accumulator;
            }
        });

        return methods;
    }

    public LoxCallable getMethod(Token name) {
        if (methods.containsKey(name.lexeme)) {
            return methods.get(name.lexeme);
        }

        throw new RuntimeError(name, "RuntimeError", "No such method '" + name.lexeme + "'.", null);
    }

    @Override
    public Object get(Token token, Object index) {
        int i = indexToInteger(token, index);

        try {
            return elements.get(i);
        } catch (IndexOutOfBoundsException e) {
            throw new RuntimeError(token, "RuntimeError", BOUNDS_ERROR_MSG, null);
        }
    }

    @Override
    public void set(Token token, Object index, Object item) {
        int i = indexToInteger(token, index);

        try {
            elements.set(i, item);
        } catch (IndexOutOfBoundsException e) {
            throw new RuntimeError(token, "RuntimeError", BOUNDS_ERROR_MSG, null);
        }
    }

    @Override
    public int length() {
        return elements.size();
    }

    private int indexToInteger(Token token, Object index) {
        if (index instanceof Double) {
            double idx = (Double) index;

            if (idx == Math.floor(idx)) {
                return (idx < 0) ? Math.floorMod((int) idx, elements.size()) : (int) idx;
            }
        }

        throw new RuntimeError(token, "RuntimeError", INVALID_INDEX_ERROR_MSG, null);
    }

    @Override
    public String toString() {
        List<String> elementsString = elements.stream().map(Interpreter::stringify).collect(Collectors.toList());
        return "[ " + String.join(", ", elementsString) + " ]";
    }
}
