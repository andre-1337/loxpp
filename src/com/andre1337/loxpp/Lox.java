package com.andre1337.loxpp;

import com.andre1337.loxpp.ast.Stmt;
import com.andre1337.loxpp.classes.RuntimeError;
import com.andre1337.loxpp.interpreter.Interpreter;
import com.andre1337.loxpp.lexer.Scanner;
import com.andre1337.loxpp.lexer.Token;
import com.andre1337.loxpp.lexer.TokenType;
import com.andre1337.loxpp.parser.Parser;
import com.andre1337.loxpp.sema.Resolver;
import com.andre1337.loxpp.transpiler.Transpiler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Lox {
  private static final Interpreter interpreter = new Interpreter(false);
  private static final Transpiler transpiler = new Transpiler();
  static boolean hadError = false;
  static boolean hadRuntimeError = false;
  private static List<String> sourceFile;

  private static void checkFileExtension(String path) {
    if (path.endsWith(".lox") || path.endsWith(".loxlib") || path.endsWith(".loxtest")) {
      return;
    } else {
      throw new RuntimeError(new Token(TokenType.EOF, "", null, 0, 0), "RuntimeError", "The provided file extension is not supported by Lox++.", "Please consider changing it to '.lox' if you're writing a program or '.loxlib' if you're writing a library.");
    }
  }

  private static void loadStandardLibrary() throws IOException {
    List<String> libraries = new ArrayList<>() {{
      add("natives/stdlib.loxlib");
    }};

    String line;
    StringBuilder source = new StringBuilder();

    for (String library : libraries) {
      InputStreamReader reader = new InputStreamReader(Objects.requireNonNull(Lox.class.getResourceAsStream(library)));
      BufferedReader bufferedReader = new BufferedReader(reader);

      while ((line = bufferedReader.readLine()) != null) {
        source.append(line).append("\n");
      }

      sourceFile = Arrays.asList(source.toString().split("\n"));
      Lox.run(source.toString(), "natives/stdlib.loxlib");
      source = new StringBuilder();
    }
  }

  public static void main(String[] args) throws IOException {
    if (args.length > 1) {
      System.out.println("Usage: loxpp [script]");
      System.exit(64);
    } else if (args.length == 1) {
      checkFileExtension(args[0]);
      runFile(args[0]);
    } else {
      runPrompt();
    }
  }

  private static void runFile(String path) throws IOException {
    byte[] bytes = Files.readAllBytes(Paths.get(path));
    String source = new String(bytes, Charset.defaultCharset());
    sourceFile = Arrays.asList(source.split("\n"));
    loadStandardLibrary();
    run(source, path);

    if (hadError) System.exit(65);
    if (hadRuntimeError) System.exit(70);
  }

  private static void runPrompt() throws IOException {
    InputStreamReader input = new InputStreamReader(System.in);
    BufferedReader reader = new BufferedReader(input);
    loadStandardLibrary();
    sourceFile = new ArrayList<>();

    for (;;) {
      if (!hadError) System.out.print("> ");

      String line = reader.readLine();
      if (line == null) break;

      sourceFile.add(line);
      run(line + "\n", "console.loxlib");

      hadError = false;
    }
  }

  private static void run(String source, String ignored) {
    List<Stmt> statements = getStmts(source);
    if (hadError) return;

    Resolver resolver = new Resolver(interpreter);
    resolver.resolve(statements);

    if (hadError) return;

    interpreter.interpret(statements);
    transpiler.transpile(statements);
    System.out.println(transpiler.getOutput());
  }

  private static List<Stmt> getStmts(String source) {
    Scanner scanner = new Scanner(source);
    List<Token> tokens = scanner.scanTokens();
    Parser parser = new Parser(tokens);
    return parser.parse();
  }

  public static void error(int line, int column, String message) {
    report(line, column, "", message);
  }

  private static void report(int line, int column, String where, String message) {
    StringBuilder msg = new StringBuilder();
    msg.append("┌ [").append(line).append(":").append(column).append("] Error").append(where).append(": ").append(message).append("\n");

    if (line > 0 && line <= sourceFile.size()) {
      String sourceLine = sourceFile.get(line);
      msg.append("├ ").append(sourceLine).append("\n");

      if (column > 0 && column <= sourceLine.length()) {
        msg.append("│").append(" ".repeat(Math.max(0, column - 1))).append(" ^\n");
      } else {
        msg.append("│").append(" ".repeat(sourceLine.length())).append(" ^\n");
      }
    }

    msg.append("└");
    System.err.println(msg);
    hadError = true;
  }

  public static void error(Token token, String message) {
    if (token.type == TokenType.EOF) {
      report(token.line - 1, token.column - 1, " at end", message);
    } else {
      report(token.line - 1, token.column - 1, " at '" + token.lexeme + "'", message);
    }
  }

  public static void runtimeError(RuntimeError error) {
    StringBuilder message = new StringBuilder();

    try {
      message.append("┌ [")
              .append(error.token.line)
              .append(":")
              .append(error.token.column)
              .append("] RuntimeError: ")
              .append(error.getMessage())
              .append("\n├ ")
              .append(sourceFile.get(error.token.line - 1))
              .append("\n│");

      message.append(" ".repeat(Math.max(0, error.token.column - 1)));

      if (error.hint != null) {
        message.append(" ^\n│\n└ Hint: ").append(error.hint);
      } else {
        message.append(" ^\n│\n└");
      }
    } catch (IndexOutOfBoundsException e) {
      message = new StringBuilder();
      message.append("[")
              .append(error.token.line)
              .append(":")
              .append(error.token.column)
              .append("] RuntimeError: ")
              .append(error.getMessage());
    }

    System.err.println(message);
    hadRuntimeError = true;
  }
}
