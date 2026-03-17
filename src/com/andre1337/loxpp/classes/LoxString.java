package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.interpreter.Interpreter;
import com.andre1337.loxpp.lexer.Token;

import java.util.*;

public class LoxString {
    public final Interpreter interpreter;
    public String value;
    private Map<String, LoxCallable> methods = new HashMap<>();

    public LoxString(Interpreter interpreter, String value) {
        this.interpreter = interpreter;
        this.value = value;
        this.methods = createMethods(this);
    }

    private String unpack_type(Object type) {
        if (type instanceof String str) {
            return str;
        } else if (type instanceof LoxString str) {
            return str.value;
        }

        throw new RuntimeException("unreachable");
    }

    private static Map<String, LoxCallable> createMethods(LoxString string) {
        Map<String, LoxCallable> methods = new HashMap<>();

        methods.put("length", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double) string.value.length();
            }
        });

        methods.put("is_empty", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return string.value.isEmpty();
            }
        });

        methods.put("char_at", new LoxCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return string.value.charAt((int) (double) arguments.getFirst());
            }
        });

        methods.put("substring", new LoxCallable() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                int start = (int) (double) arguments.getFirst();
                int end = (int) (double) arguments.get(1);
                return string.value.substring(start, end);
            }
        });

        methods.put("index_of", new LoxCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                String substring = ((LoxString) arguments.getFirst()).value;
                return string.value.indexOf(substring);
            }
        });

        methods.put("contains", new LoxCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                String substring = ((LoxString) arguments.getFirst()).value;
                return string.value.contains(substring);
            }
        });

        methods.put("to_upper", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return string.value.toUpperCase();
            }
        });

        methods.put("to_lower", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return string.value.toLowerCase();
            }
        });

        methods.put("trim", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return string.value.trim();
            }
        });

        methods.put("split", new LoxCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                String delimiter = ((LoxString) arguments.getFirst()).value;
                List<Object> elements = new ArrayList<>(Arrays.asList(string.value.split(delimiter)));
                return new LoxArray(interpreter, elements);
            }
        });

        methods.put("append", new LoxCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                String value = string.unpack_type(arguments.getFirst());
                string.value += value;
                return null;
            }
        });

        methods.put("equals", new LoxCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                String value = string.value;
                String other = ((LoxString) arguments.getFirst()).value;
                return value.equals(other);
            }
        });

        methods.put("repeat", new LoxCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                String value = string.value;
                int length = (int) (double) arguments.getFirst();
                return new LoxString(interpreter, value.repeat(length));
            }
        });

        methods.put("starts_with", new LoxCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                String value = string.value;
                String match = (String) arguments.getFirst();

                return value.startsWith(match);
            }
        });

        methods.put("ends_with", new LoxCallable() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                String value = string.value;
                String match = (String) arguments.getFirst();

                return value.endsWith(match);
            }
        });

        methods.put("to_number", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return Double.parseDouble(string.value);
            }
        });

        methods.put("is_number", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                try {
                    Double.parseDouble(string.value);
                    return true;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        });

        return methods;
    }

    public LoxCallable getMethod(Token name) {
        if (methods.containsKey(name.lexeme)) {
            return methods.get(name.lexeme);
        }

        throw new RuntimeError(name, "RuntimeError", "No such method.", null);
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        assert obj instanceof LoxString;
        return value.equals(((LoxString) obj).value);
    }
}