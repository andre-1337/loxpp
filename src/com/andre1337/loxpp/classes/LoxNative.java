package com.andre1337.loxpp.classes;

import com.andre1337.loxpp.interpreter.Interpreter;

import java.util.*;

public abstract class LoxNative implements LoxCallable {
    public static class Clock extends LoxNative {
        @Override
        public int arity() {
            return 0;
        }

        @Override
        public Object call(Interpreter interpreter, List<Object> arguments) {
            return (double) System.currentTimeMillis();
        }

        @Override
        public String toString() {
            return "<native fn>";
        }
    }

    public static class Random extends LoxNative {
        @Override
        public int arity() {
            return 0;
        }

        @Override
        public Object call(Interpreter interpreter, List<Object> arguments) {
            return Math.random();
        }

        @Override
        public String toString() {
            return "<native fn>";
        }
    }

    public static class Sin extends LoxNative {
        @Override
        public int arity() {
            return 1;
        }

        @Override
        public Object call(Interpreter interpreter, List<Object> arguments) {
            return Math.sin((Double) arguments.getFirst());
        }

        @Override
        public String toString() {
            return "<native fn>";
        }
    }

    public static class Cos extends LoxNative {
        @Override
        public int arity() {
            return 1;
        }

        @Override
        public Object call(Interpreter interpreter, List<Object> arguments) {
            return Math.cos((Double) arguments.getFirst());
        }

        @Override
        public String toString() {
            return "<native fn>";
        }
    }

    public static class Tan extends LoxNative {
        @Override
        public int arity() {
            return 1;
        }

        @Override
        public Object call(Interpreter interpreter, List<Object> arguments) {
            return Math.tan((Double) arguments.getFirst());
        }

        @Override
        public String toString() {
            return "<native fn>";
        }
    }

    public static class Print extends LoxNative {
        @Override
        public int arity() {
            return -1;
        }

        @Override
        public Object call(Interpreter interpreter, List<Object> arguments) {
            List<String> strings = new ArrayList<>();
            for (Object arg : arguments) {
                strings.add(Interpreter.stringify(arg));
            }
            String formattedString = String.join(" ", strings);
            System.out.print(formattedString);
            return null;
        }

        @Override
        public String toString() {
            return "<native fn>";
        }
    }

    public static class Println extends LoxNative {
        @Override
        public int arity() {
            return -1;
        }

        @Override
        public Object call(Interpreter interpreter, List<Object> arguments) {
            List<String> strings = new ArrayList<>();
            for (Object arg : arguments) {
                strings.add(Interpreter.stringify(arg));
            }
            String formattedString = String.join(" ", strings);
            System.out.println(formattedString);
            return null;
        }

        @Override
        public String toString() {
            return "<native fn>";
        }
    }

    public static class Debug extends LoxNative {
        @Override
        public int arity() {
            return -1;
        }

        @Override
        public Object call(Interpreter interpreter, List<Object> arguments) {
            List<String> strings = new ArrayList<>();
            for (Object arg : arguments) {
                strings.add(Interpreter.stringify(arg));
            }
            String formattedString = String.join(" ", strings);
            System.err.format("[debug] %s\n", formattedString);
            return null;
        }

        @Override
        public String toString() {
            return "<native fn>";
        }
    }

    public static class Sleep extends LoxNative {
        @Override
        public int arity() {
            return 1;
        }

        @Override
        public Object call(Interpreter interpreter, List<Object> arguments) {
            int time = (int) (double) arguments.getFirst();
            try {
                Thread.sleep(time);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            return null;
        }

        @Override
        public String toString() {
            return "<native fn>";
        }
    }

    public static class ToString extends LoxNative {

        @Override
        public int arity() {
            return 1;
        }

        @Override
        public Object call(Interpreter interpreter, List<Object> arguments) {
            return Interpreter.stringify(arguments.getFirst());
        }
    }
}
