package com.andre1337.loxpp.interpreter;

import com.andre1337.loxpp.Lox;
import com.andre1337.loxpp.ast.Expr;
import com.andre1337.loxpp.ast.Stmt;
import com.andre1337.loxpp.classes.*;
import com.andre1337.loxpp.lexer.Scanner;
import com.andre1337.loxpp.lexer.Token;
import com.andre1337.loxpp.lexer.TokenType;
import com.andre1337.loxpp.parser.Parser;
import com.andre1337.loxpp.sema.Resolver;

import java.io.File;
import java.net.http.HttpClient;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
  private Map<Expr, Integer> locals = new HashMap<>();
  private static final Object uninitialized = new Object();
  private static final Map<String, LoxModule> moduleCache = new HashMap<>();
  private static final Set<String> loadingModules = new HashSet<>();
  public Environment globals = new Environment();
  public Environment environment = globals;

  private Interpreter(Environment globals, Map<Expr, Integer> locals) {
    this.globals = globals;
    this.environment = globals;
    this.locals = locals;
  }

  public Interpreter spawnAsyncWorker() {
    return new Interpreter(this.globals, this.locals);
  }

  public Interpreter() {
    globals.define("clock", new LoxNative.Clock());
    globals.define("___random___", new LoxNative.Random());
    globals.define("___sin___", new LoxNative.Sin());
    globals.define("___cos___", new LoxNative.Cos());
    globals.define("___tan___", new LoxNative.Tan());
    globals.define("print", new LoxNative.Print());
    globals.define("println", new LoxNative.Println());
    globals.define("debug", new LoxNative.Debug());
    globals.define("___sleep___", new LoxNative.Sleep());
    globals.define("___to_string___", new LoxNative.ToString());

    // Java Types
    globals.define("Double", Double.class);
    globals.define("String", LoxString.class);
    globals.define("Boolean", Boolean.class);
    globals.define("Array", LoxArray.class);
    globals.define("Object", Map.class);

    // TCP SERVER
    globals.define("___tcp_bind___", new LoxCallable() {
      @Override public int arity() { return 1; }
      @Override public Object call(Interpreter interpreter, List<Object> args, boolean isNew) {
        return LoxTcpCore.___tcp_bind___((int)(double)args.getFirst());
      }
    });

    globals.define("___tcp_bind_s___", new LoxCallable() {
      @Override
      public int arity() {
        return 3; // 1: port, 2: key store path, 3: key store pass
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        int port = (int)(double) arguments.getFirst();
        String keyStorePath = (arguments.get(1) instanceof LoxString loxStr) ? loxStr.value : arguments.get(1).toString();
        String keyStorePass = (arguments.get(2) instanceof LoxString loxStr) ? loxStr.value : arguments.get(2).toString();

        return LoxTcpCore.___tcp_bind_s___(port, keyStorePath, keyStorePass);
      }
    });

    globals.define("___tcp_accept___", new LoxCallable() {
      @Override public int arity() { return 1; }
      @Override public Object call(Interpreter interpreter, List<Object> args, boolean isNew) {
        return LoxTcpCore.___tcp_accept___(args.getFirst());
      }
    });

    globals.define("___tcp_server_close___", new LoxCallable() {
      @Override public int arity() { return 1; }
      @Override public Object call(Interpreter interpreter, List<Object> args, boolean isNew) {
        return LoxTcpCore.___tcp_server_close___((AsynchronousServerSocketChannel)args.getFirst());
      }
    });

    // TCP CLIENT
    globals.define("___tcp_read___", new LoxCallable() {
      @Override public int arity() {
        return 2; // 1: socket channel, 2: buffer size
      }
      @Override public Object call(Interpreter interpreter, List<Object> args, boolean isNew) {
        return LoxTcpCore.___tcp_read___(args.getFirst(), args.get(1));
      }
    });

    globals.define("___tcp_write___", new LoxCallable() {
      @Override public int arity() {
        return 2; // 1: socket channel, 2: data object
      }
      @Override public Object call(Interpreter interpreter, List<Object> args, boolean isNew) {
        return LoxTcpCore.___tcp_write___(args.getFirst(), args.get(1));
      }
    });

    globals.define("___tcp_close___", new LoxCallable() {
      @Override public int arity() { return 1; }
      @Override public Object call(Interpreter interpreter, List<Object> args, boolean isNew) {
        return LoxTcpCore.___tcp_close___(args.getFirst());
      }
    });

    globals.define("parse_json", new LoxCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        Object arg = arguments.getFirst();
        String jsonStr = (arg instanceof LoxString loxStr) ? loxStr.value : arg.toString();
        LoxJsonParser jsonParser = new LoxJsonParser(interpreter, jsonStr);

        return jsonParser.parse();
      }
    });

    globals.define("stringify_json", new LoxCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        String jsonResult = LoxJsonStringifier.stringify(arguments.getFirst());
        return new LoxString(jsonResult);
      }
    });

    globals.define("read_file", new LoxCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        Object arg = arguments.getFirst();
        String filePath = (arg instanceof LoxString loxStr) ? loxStr.value : arg.toString();
        filePath = filePath.replace("\"", "").trim();

        try {
          byte[] bytes = Files.readAllBytes(Path.of(filePath));
          String content = new String(bytes, StandardCharsets.ISO_8859_1);
          return new LoxString(content);
        } catch (Exception e) {
          return null;
        }
      }
    });

    globals.define("render_template", new LoxCallable() {
      @Override
      public int arity() {
        return 2; // 1: html, 2: a map/dictionary
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        Object htmlArg = arguments.getFirst();
        String html = (htmlArg instanceof LoxString loxStr) ? loxStr.value : htmlArg.toString();

        Object dictArg = arguments.get(1);

        if (dictArg instanceof Map<?, ?> data) {
          for (Map.Entry<?, ?> entry : data.entrySet()) {
            String key = entry.getKey() instanceof LoxString ? ((LoxString) entry.getKey()).value : entry.getKey().toString();

            key = key.replace("\"", "");

            String value;
            Object valObj = entry.getValue();

            if (valObj instanceof LoxString loxStr) {
              value = loxStr.value;
            } else if (valObj instanceof String str) {
              value = str;
            } else if (valObj != null) {
              value = valObj.toString();
              if (value.endsWith(".0")) value = value.substring(0, value.length() - 2);
            } else {
              value = "null";
            }

            // replace {{ }} tags with the actual value
            html = html.replace("{{ " + key + " }}", value).replace("{{" + key + "}}", value);
          }
        }

        return new LoxString(html);
      }
    });

    globals.define("file_size", new LoxCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        String path = arguments.getFirst().toString().replace("\"", "");

        try {
          long size = Files.size(Path.of(path));
          return new LoxString(String.valueOf(size));
        } catch (Exception e) {
          return new LoxString("-1");
        }
      }
    });

    globals.define("write_file", new LoxCallable() {
      @Override
      public int arity() {
        return 2; // 1: path, 2: content
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        Object pathArg = arguments.getFirst();
        String filePath = (pathArg instanceof LoxString loxStr) ? loxStr.value : pathArg.toString();
        filePath = filePath.replace("\"", "").trim();

        Object contentArg = arguments.get(1);
        String content = (contentArg instanceof LoxString loxStr) ? loxStr.value : contentArg.toString();

        try {
          Files.writeString(Path.of(filePath), content, StandardCharsets.ISO_8859_1);
          return true;
        } catch (Exception e) {
          System.err.println("Error while reading file: " + e.getMessage());
          return false;
        }
      }
    });

    globals.define("___fetch___", new LoxCallable() {
      // reduces load times by having a shared client
      // first hit takes longer, since it is "warming up"
      // all the other hits are way faster
      private final HttpClient sharedClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

      @Override
      public int arity() {
        return 2; // 1: url, 2: method
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        String url = arguments.get(0).toString().replace("\"", "").trim();
        String method = arguments.get(1).toString().replace("\"", "").toUpperCase().trim();

        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
          try {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("User-Agent", "lox-std-http/1.0")
                    .header("Accept", "application/json")
                    .method(method, java.net.http.HttpRequest.BodyPublishers.noBody())
                    .build();

            java.net.http.HttpResponse<String> response = sharedClient.send(
                    request,
                    java.net.http.HttpResponse.BodyHandlers.ofString()
            );

            List<Object> result = new java.util.ArrayList<>();
            result.add((double) response.statusCode());
            result.add(response.body());

            return new LoxArray(interpreter, result);

          } catch (Exception e) {
            System.err.println("Native Fetch Error: " + e.getMessage());

            List<Object> result = new java.util.ArrayList<>();
            result.add(0.0);
            result.add(e.getMessage());
            return new LoxArray(interpreter, result);
          }
        });
      }
    });

    globals.define("___ws_handshake___", new LoxCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        try {
          String clientKey = (arguments.getFirst() instanceof LoxString loxStr) ? loxStr.value.trim() : arguments.getFirst().toString().trim();
          String magicKey = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
          MessageDigest md = MessageDigest.getInstance("SHA-1");
          byte[] hashed = md.digest((clientKey + magicKey).getBytes(StandardCharsets.UTF_8));

          return Base64.getEncoder().encodeToString(hashed);
        } catch (Exception e) {
          return null;
        }
      }
    });

    globals.define("___ws_encode___", new LoxCallable() {
      @Override
      public int arity() { return 1; }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        String message = arguments.getFirst().toString();
        byte[] msgBytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();

        baos.write(129);

        if (msgBytes.length <= 125) {
          baos.write(msgBytes.length);
        } else if (msgBytes.length <= 65535) {
          baos.write(126);
          baos.write((msgBytes.length >> 8) & 255);
          baos.write(msgBytes.length & 255);
        }

        try {
          baos.write(msgBytes);
        } catch(Exception ignored) {}

        return new LoxString(baos.toString(StandardCharsets.ISO_8859_1));
      }
    });

    globals.define("___ws_decode___", new LoxCallable() {
      @Override
      public int arity() { return 1; }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        try {
          if (arguments.getFirst() == null) return null;

          Object arg = arguments.getFirst();
          String rawStr = arg.toString();
          try {
            java.lang.reflect.Field valueField = arg.getClass().getField("value");
            rawStr = (String) valueField.get(arg);
          } catch (Exception ignored) {}

          byte[] raw = rawStr.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
          if (raw.length < 2) return null;

          int opcode = raw[0] & 0x0F;
          if (opcode == 8) return "DISCONNECT_SIGNAL";

          boolean masked = (raw[1] & 0x80) != 0;
          int payloadLength = raw[1] & 0x7F;
          int offset = 2;

          if (payloadLength == 126) { offset += 2; }
          else if (payloadLength == 127) { offset += 8; }

          byte[] masks = new byte[4];
          if (masked) {
            System.arraycopy(raw, offset, masks, 0, 4);
            offset += 4;
          }

          int actualLen = raw.length - offset;
          if (actualLen <= 0) return "";

          byte[] decoded = new byte[actualLen];
          for (int i = 0; i < actualLen; i++) {
            decoded[i] = (byte) (raw[offset + i] ^ (masked ? masks[i % 4] : 0));
          }

          return new LoxString(new String(decoded, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
          return null;
        }
      }
    });

    globals.define("stream_to_socket", new LoxCallable() {
      @Override
      public int arity() {
        return 3; // 1: socket, 2: file path, 3: chunk size
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        Object socket = arguments.get(0);
        String filePath = arguments.get(1).toString().replace("\"", "").trim();
        int chunkSize = (int)(double) arguments.get(2);

        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
          try (java.io.InputStream is = java.nio.file.Files.newInputStream(java.nio.file.Path.of(filePath))) {
            byte[] buffer = new byte[chunkSize];
            int bytesRead;

            if (socket instanceof javax.net.ssl.SSLSocket sslSocket) {
              java.io.OutputStream os = sslSocket.getOutputStream();
              while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
              }
              os.flush();
            } else {
              java.nio.channels.AsynchronousSocketChannel nioSocket = (java.nio.channels.AsynchronousSocketChannel) socket;
              while ((bytesRead = is.read(buffer)) != -1) {
                java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(buffer, 0, bytesRead);
                while (bb.hasRemaining()) {
                  nioSocket.write(bb).get();
                }
              }
            }
            return true;
          } catch (Exception e) {
            System.out.println("Java Stream Error: " + e.getMessage());
            return false;
          }
        });
      }
    });

    globals.define("___uuid___", new LoxCallable() {
      @Override
      public int arity() {
        return 0;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        return new LoxString(UUID.randomUUID().toString());
      }
    });

    globals.define("___rename_file___", new LoxCallable() {
      @Override
      public int arity() {
        return 2; // 1: old path, 2: new path
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        String oldPath = arguments.getFirst() instanceof LoxString loxStr ? loxStr.value : arguments.getFirst().toString();
        oldPath = oldPath.replace("\"", "").trim();

        String newPath = arguments.get(1) instanceof LoxString loxStr ? loxStr.value : arguments.get(1).toString();
        newPath = newPath.replace("\"", "").trim();

        try {
          Files.move(Path.of(oldPath), Path.of(newPath), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
          return true;
        } catch (Exception e) {
          System.err.println("Java Replace Error: " + e.getMessage());
          return false;
        }
      }
    });

    globals.define("___sort_docs___", new LoxCallable() {
      @Override
      public int arity() {
        return 3; //1: array, 2: sort key, 3: ascending
      }

      private Object getMapValue(Map<?, ?> map, String searchKey) {
        for (Map.Entry<?, ?> entry: map.entrySet()) {
          String k = entry.getKey() instanceof LoxString loxStr ? loxStr.value : entry.getKey().toString();
          k = k.replace("\"", "");
          if (k.equals(searchKey)) return entry.getValue();
        }

        return null;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        if (!(arguments.getFirst() instanceof LoxArray loxArray)) return arguments.getFirst();

        String key = arguments.get(1) instanceof LoxString loxStr ? loxStr.value : arguments.get(1).toString();
        key = key.replace("\"", "");

        boolean ascending = (boolean) arguments.get(2);
        List<Object> sorted = new ArrayList<>(loxArray.elements);
        String finalKey = key;

        sorted.sort((a, b) -> {
          if (!(a instanceof Map<?, ?> mapA) || !(b instanceof Map<?, ?> mapB)) return 0;

          Object valA = getMapValue(mapA, finalKey);
          Object valB = getMapValue(mapB, finalKey);

          if (valA == null && valB == null) return 0;
          if (valA == null) return ascending ? -1 : 1;
          if (valB == null) return ascending ? 1 : -1;

          int comparison;
          if (valA instanceof Double dA && valB instanceof Double dB) {
            comparison = Double.compare(dA, dB);
          } else if (valA instanceof Boolean bA && valB instanceof Boolean bB) {
            comparison = Boolean.compare(bA, bB);
          } else {
            String strA = valA instanceof LoxString loxStr ? loxStr.value : valA.toString();
            String strB = valB instanceof LoxString loxStr ? loxStr.value : valB.toString();

            comparison = strA.compareTo(strB);
          }

          return ascending ? comparison : -comparison;
        });

        return new LoxArray(interpreter, sorted);
      }
    });

    globals.define("___cli_args___", new LoxCallable() {
      @Override
      public int arity() {
        return 0;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        List<Object> argsList = new ArrayList<>();

        if (Lox.cliArgs != null) {
          for (int i = 1; i < Lox.cliArgs.length; i++) {
            argsList.add(new LoxString(Lox.cliArgs[i]));
          }
        }

        return new LoxArray(interpreter, argsList);
      }
    });

    globals.define("___os_mkdir___", new LoxCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        String dirPath = arguments.getFirst() instanceof LoxString loxStr ? loxStr.value : arguments.getFirst().toString();
        dirPath = dirPath.replace("\"", "").trim();

        try {
          Files.createDirectories(Path.of(dirPath));
          return true;
        } catch (Exception e) {
          System.err.println("Java MKDIR Error: " + e.getMessage());
          return false;
        }
      }
    });

    globals.define("___os_remove___", new LoxCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        String filePath = arguments.getFirst() instanceof LoxString loxStr ? loxStr.value : arguments.getFirst().toString();
        filePath = filePath.replace("\"", "").trim();

        try {
          return Files.deleteIfExists(Path.of(filePath));
        } catch (Exception e) {
          System.err.println("Java OS REMOVE error: " + e.getMessage());
          return false;
        }
      }
    });

    globals.define("___os_exec___", new LoxCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        String command = arguments.getFirst() instanceof LoxString loxStr ? loxStr.value : arguments.getFirst().toString();

        try {
          ProcessBuilder pb;

          if (System.getProperty("os.name").toLowerCase().contains("win")) {
            pb = new ProcessBuilder("cmd.exe", "/c", command);
          } else {
            pb = new ProcessBuilder("bash", "-c", command);
          }

          pb.inheritIO();
          Process process = pb.start();
          process.waitFor();

          return (double) process.exitValue();
        } catch (Exception e) {
          System.err.println("Java Exec Error: " + e.getMessage());
          return -1.0;
        }
      }
    });

    globals.define("___os_rmdir___", new LoxCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments, boolean isNewCall) {
        String dirPath = arguments.getFirst() instanceof LoxString loxStr ? loxStr.value : arguments.getFirst().toString();
        dirPath = dirPath.replace("\"", "");

        try {
          Path rootPath = Path.of(dirPath);
          if (Files.exists(rootPath)) {
            Files.walk(rootPath).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
          }

          return true;
        } catch (Exception e) {
          System.err.println("Java OS RMDIR Error: " + e.getMessage());
          return false;
        }
      }
    });
  }

  public void interpret(List<Stmt> statements) {
    try {
      for (Stmt statement : statements) {
        execute(statement);
      }
    } catch (RuntimeError error) {
      Lox.runtimeError(error);
    }
  }

  private Object evaluate(Expr expr) {
    if (expr == null) return null;
    return expr.accept(this);
  }

  private void execute(Stmt stmt) {
    stmt.accept(this);
  }

  public void resolve(Expr expr, int depth) {
    locals.put(expr, depth);
  }

  public void executeBlock(List<Stmt> statements, Environment env) {
    Environment previous = this.environment;

    try {
      this.environment = env;

      for (Stmt statement : statements) {
        execute(statement);
      }
    } finally {
      this.environment = previous;
    }
  }

  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    executeBlock(stmt.statements, new Environment(environment));
    return null;
  }

  @Override
  public Void visitClassStmt(Stmt.Class stmt) {
    environment.define(stmt.name.lexeme, null);
    Object superclass = null;

    if (stmt.superclass != null) {
      superclass = evaluate(stmt.superclass);
      if (!(superclass instanceof LoxClass)) {
        throw new RuntimeError(stmt.superclass.name, "RuntimeError", "Superclass must be a class.", null);
      }
    }

    if (stmt.superclass != null) {
      environment = new Environment(environment);
      environment.define("super", superclass);
    }

    LoxClass klass = getClass(stmt, (LoxClass) superclass);

    for (Expr traitExpr : stmt.traits) {
      LoxTrait trait = (LoxTrait) evaluate(traitExpr);
      klass.addTrait(trait);

      try {
        verifyMethods(klass, trait);
      } catch (RuntimeError error) {
        throw new RuntimeError(stmt.name, "RuntimeError", error.getMessage(), null);
      }

      addNonAbstractMethods(klass, trait);
    }

    if (superclass != null) {
      environment = environment.enclosing;
    }

    environment.assign(stmt.name, klass);
    return null;
  }

  private LoxClass getClass(Stmt.Class stmt, LoxClass superclass) {
    Map<String, LoxFunction> methods = new HashMap<>();
    for (Stmt.Function method : stmt.methods) {
      LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("init"), false, method.isAsync);
      methods.put(method.name.lexeme, function);
    }

    Map<String, LoxFunction> staticMethods = new HashMap<>();
    for (Stmt.Function method : stmt.staticMethods) {
      LoxFunction function = new LoxFunction(method, environment, false, false, method.isAsync);
      staticMethods.put(method.name.lexeme, function);
    }

    LoxClass meta = new LoxClass(null, stmt.name.lexeme + "_meta", stmt.name, null, staticMethods, this);
    return new LoxClass(meta, stmt.name.lexeme, stmt.name, superclass, methods, this);
  }

  private void verifySignature(LoxFunction traitDef, LoxFunction classImpl) {
    String classImplName = classImpl.declaration().name.lexeme;

    if (classImplName.equals("init")) {
      return;
    }

    if (traitDef.isAsync() != classImpl.isAsync()) {
      throw new RuntimeError(
              classImpl.declaration().name,
              "RuntimeError",
              "Method '" + classImplName + "' must " + (traitDef.isAsync() ? "be async." : "not be async."),
              "Please consider " + (traitDef.isAsync() ? "adding" : "removing") + "the 'async' modifier from the implementation"
      );
    }

    if (traitDef.declaration().params.size() != classImpl.declaration().params.size()) {
      throw new RuntimeError(
              classImpl.declaration().name,
              "RuntimeError",
              "Method '" + classImplName + "' has a mismatched parameter count.",
              "The method should have " + traitDef.declaration().params.size() + " arguments, but it has " + classImpl.declaration().params.size() + " instead."
      );
    }

    for (int i = 0; i < traitDef.declaration().params.size(); i++) {
      String traitDefParamName = traitDef.declaration().params.get(i).name().lexeme;
      String classImplParamName = classImpl.declaration().params.get(i).name().lexeme;

      if (!traitDefParamName.equals(classImplParamName)) {
        throw new RuntimeError(
                classImpl.declaration().name,
                "RuntimeError",
                "Parameter name mismatch in method '" + classImplName + "'.",
                "The name of the parameter should be '" + traitDefParamName + "', but it is '" + classImplParamName + "' instead."
        );
      }
    }
  }

  private void verifyMethods(LoxClass klass, LoxTrait trait) {
    for (Map.Entry<String, LoxFunction> entry : trait.methods().entrySet()) {
      LoxFunction fn = getLoxFunction(klass, trait, entry);

      for (Map.Entry<String, LoxFunction> classEntry : klass.methods.entrySet()) {
        LoxFunction classMethod = classEntry.getValue();

        if (!trait.methods().containsKey(classMethod.declaration().name.lexeme)) {
          return;
        }

        verifySignature(fn, classMethod);
      }
    }
  }

  private static LoxFunction getLoxFunction(LoxClass klass, LoxTrait trait, Map.Entry<String, LoxFunction> entry) {
    String methodName = entry.getKey();
    LoxFunction fn = entry.getValue();

    if (fn.isAbstract() && (!klass.methods.containsKey(methodName))) {
      throw new RuntimeError(fn.declaration().name, "RuntimeError",
              "Class '" + klass.name + "' does not implement abstract method '" + fn.declaration().name.lexeme + "' from trait '" + trait.name().lexeme + "'.",
              "Consider implementing the abstract method '" + fn.declaration().name.lexeme + "' from trait '" + trait.name().lexeme + "' in class '" + klass.name + "'."
      );
    }
    return fn;
  }

  private void addNonAbstractMethods(LoxClass klass, LoxTrait trait) {
    for (LoxFunction function : trait.methods().values()) {
      if (!function.isAbstract()) {
        klass.methods.put(function.declaration().name.lexeme, function);
      }
    }
  }

  private Map<String, LoxFunction> applyTraits(List<Expr> traits) {
    Map<String, LoxFunction> methods = new HashMap<>();

    for (Expr traitExpr : traits) {
      Object traitObj = evaluate(traitExpr);
      if (!(traitObj instanceof  LoxTrait(Token name1, Map<String, LoxFunction> methods1))) {
        Token name = ((Expr.Variable) traitExpr).name;
        throw new RuntimeError(name, "RuntimeError", "'" + name.lexeme + "' is not a trait.", null);
      }

      for (String name : methods1.keySet()) {
        if (methods.containsKey(name)) {
          throw new RuntimeError(name1, "RuntimeError",
              "A previously implemented trait already declares method '" + name + "'.",
                  "Consider removing or renaming the method '" + name + "'."
          );
        }

        methods.put(name, methods1.get(name));
      }
    }

    return methods;
  }

  @Override
  public Void visitTraitStmt(Stmt.Trait stmt) {
    environment.define(stmt.name.lexeme, null);
    Map<String, LoxFunction> methods = applyTraits(stmt.traits);

    for (Stmt.Function method : stmt.methods) {
      if (methods.containsKey(method.name.lexeme)) {
        throw new RuntimeError(method.name, "RuntimeError",
            "A previously implemented trait already declares method '" + method.name.lexeme + "'.",
                "Consider removing or renaming the method '" + method.name.lexeme + "'."
        );
      }

      LoxFunction function = new LoxFunction(method, environment, false, method.isAbstract, method.isAsync);
      methods.put(method.name.lexeme, function);
    }

    LoxTrait trait = new LoxTrait(stmt.name, methods);
    environment.assign(stmt.name, trait);
    return null;
  }

  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    evaluate(stmt.expression);
    return null;
  }

  @Override
  public Void visitFunctionStmt(Stmt.Function stmt) {
    LoxFunction function = new LoxFunction(stmt, environment, false, false, stmt.isAsync);
    environment.define(stmt.name.lexeme, function);
    return null;
  }

  @Override
  public Void visitIfStmt(Stmt.If stmt) {
    if (isTruthy(evaluate(stmt.condition))) {
      execute(stmt.thenBranch);
    } else if (stmt.elseBranch != null) {
      execute(stmt.elseBranch);
    }
    return null;
  }

  @Override
  public Void visitReturnStmt(Stmt.Return stmt) {
    Object value = null;
    if (stmt.value != null)
      value = evaluate(stmt.value);

    throw new Return(value);
  }

  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    Object value = uninitialized;

    if (stmt.initializer != null) {
      value = evaluate(stmt.initializer);
    }

    environment.define(stmt.name.lexeme, value);

    return null;
  }

  @Override
  public Void visitObjectDestructuringStmt(Stmt.ObjectDestructuring stmt) {
    Object value = getValue(evaluate(stmt.initializer));

    if (!(value instanceof Map<?, ?> dict)) {
      throw new RuntimeError(stmt.keyword, "RuntimeError", "Cannot destructure non-object.", "Object must be a dictionary in order to be destructible.");
    }

    for (Token key : stmt.bindings) {
      if (key.lexeme.equals("_")) {
        continue;
      }

      if (!dict.containsKey(key.lexeme)) {
        throw new RuntimeError(key, "RuntimeError", "Key '" + key.lexeme + "' is not present in the dictionary.", null);
      }
      environment.define(key.lexeme, dict.get(key.lexeme));
    }

    return null;
  }

  @Override
  public Void visitArrayDestructuringStmt(Stmt.ArrayDestructuring stmt) {
    Object value = getValue(evaluate(stmt.initializer));

    if (!(value instanceof LoxArray array)) {
      throw new RuntimeError(stmt.keyword, "RuntimeError", "Cannot destructure non-array.", "Object must be an array in order to be destructible.");
    }

    for (Token key : stmt.bindings) {
      if (key.lexeme.equals("_")) {
        continue;
      }

      for (int i = 0; i < stmt.bindings.size(); i++) {
        environment.define(stmt.bindings.get(i).lexeme, array.elements.get(i));
      }
    }

    return null;
  }

  @Override
  public Void visitWhileStmt(Stmt.While stmt) {
    while (isTruthy(evaluate(stmt.condition))) {
      execute(stmt.body);
    }

    return null;
  }

  @Override
  public Void visitForInStmt(Stmt.ForIn stmt) {
    Object iterable = getValue(evaluate(stmt.iterable));

    switch (iterable) {
      case LoxArray loxArray -> {
        int i = 0;

        for (Object element : loxArray.elements) {
          environment.define(stmt.key.lexeme, element);

          if (stmt.value != null) {
            environment.define(stmt.value.lexeme, i);
          }

          i++;
          executeBlock(stmt.body, environment);
        }
      }

      case String str -> {
        for (int i = 0; i < str.length(); i++) {
          environment.define(stmt.key.lexeme, str.charAt(i));
          executeBlock(stmt.body, environment);
        }
      }

      case LoxTuple tuple -> {
        for (int i = 0; i < tuple.size(); i++) {
          environment.define(stmt.key.lexeme, tuple.elements().get(i));
          executeBlock(stmt.body, environment);
        }
      }

      case Map<?, ?> dictionary -> {
        for (Map.Entry<?, ?> entry : dictionary.entrySet()) {
          Object mapKey = entry.getKey();
          Object mapVal = entry.getValue();

          if (mapKey instanceof String str) mapKey = new LoxString(str);
          if (mapVal instanceof String str) mapVal = new LoxString(str);

          environment.define(stmt.key.lexeme, mapKey);

          if (stmt.value != null) {
            environment.define(stmt.value.lexeme, mapVal);
          }

          executeBlock(stmt.body, environment);
        }
      }

      case LoxInstance instance -> {
        LoxTrait iterableTrait = (LoxTrait) environment.get("Iterable");
        LoxClass klass = instance.klass;

        if (!klass.hasTrait(iterableTrait)) {
          throw new RuntimeError(
                  stmt.keyword,
                  "RuntimeError",
                  "Object must implement the 'Iterable' trait to be iterable.",
                  "Consider implementing the 'Iterable' trait on this object."
          );
        }

        LoxFunction hasNext = klass.findMethod("has_next").bind(instance);
        LoxFunction next = klass.findMethod("next").bind(instance);

        while ((boolean) Objects.requireNonNull(hasNext.call(this, List.of(), false))) {
          Object currentValue = next.call(this, Collections.emptyList(), false);
          environment.define(stmt.key.lexeme, currentValue);

          executeBlock(stmt.body, environment);
        }
      }

      case null, default ->
        throw new RuntimeError(
                stmt.keyword,
                "RuntimeError",
                "For-in loops only work with arrays, tuples, dictionaries, strings and objects that implement the 'Iterable' trait.",
                null
        );
    }

    return null;
  }

  @Override
  public Void visitThrowStmt(Stmt.Throw stmt) {
    LoxInstance thrown = (LoxInstance) evaluate(stmt.thrown);
    LoxTrait errorTrait = (LoxTrait) environment.get("Throwable");

    if (!thrown.klass.hasTrait(errorTrait)) {
      throw new RuntimeError(
              stmt.keyword,
              "RuntimeError",
              "Object must implement the 'Error' trait to be throwable.",
              "Consider implementing the 'Error' trait on this object."
      );
    }

    Token messageToken = thrown.klass.findMethod("message").declaration().name;
    Object messageMethod = ((LoxCallable) thrown.get(messageToken)).call(this, new ArrayList<>(), false);

    throw new UserRuntimeError(thrown, thrown.klass.name, stringify(messageMethod), stmt.keyword);
  }

  @Override
  public Void visitEnumStmt(Stmt.Enum stmt) {
    LoxEnum loxEnum = new LoxEnum(stmt.name.lexeme, stmt.isUnion);

    for (Stmt.EnumCase kase : stmt.cases) {
      loxEnum.addConstructor(kase.name().lexeme, kase.parameters());
    }

    environment.define(stmt.name.lexeme, loxEnum);

    for (Stmt.EnumCase kase : stmt.cases) {
      environment.define(kase.name().lexeme, loxEnum.getConstructor(kase.name().lexeme));
    }

    return null;
  }

  @Override
  public Void visitTryCatchStmt(Stmt.TryCatch stmt) {
    try {
      executeBlock(stmt.tryBody, environment);
    } catch (UserRuntimeError error) {
      environment.define(stmt.exception.lexeme, error.instance);
      executeBlock(stmt.catchBody, environment);
    } catch (RuntimeError error) {
      environment.define(stmt.exception.lexeme, error.getMessage());
      executeBlock(stmt.catchBody, environment);
    }

    return null;
  }

  @Override
  public Void visitNamespaceStmt(Stmt.Namespace stmt) {
    LoxNamespace namespace = new LoxNamespace(stmt.name.lexeme);

    Environment previous = environment;
    this.environment = new Environment(previous);

    try {
      for (Stmt statement : stmt.body) {
        execute(statement);
      }

      for (Map.Entry<String, Object> entry : environment.values.entrySet()) {
        namespace.define(entry.getKey(), entry.getValue());
      }

      for (Stmt statement : stmt.body) {
        if (statement instanceof Stmt.Export exportStmt) {
          for (Token name : exportStmt.names) {
            if (!environment.values.containsKey(name.lexeme)) {
              throw new RuntimeError(name, "RuntimeError", "Cannot export undefined name '" + name.lexeme + "'.", null);
            }

            namespace.markAsPublic(name.lexeme);
          }
        }
      }
    } finally {
      this.environment = previous;
    }

    environment.define(stmt.name.lexeme, namespace);
    return null;
  }

  @Override
  public Void visitExportStmt(Stmt.Export stmt) {
    return null;
  }

  private LoxModule loadModule(String importPath, Token keyword) {
    String resolvedPath = resolveImportPath(importPath);

    try {
      java.nio.file.Path path = java.nio.file.Path.of(resolvedPath).toAbsolutePath().normalize();
      String absoluteString = path.toString();

      if (moduleCache.containsKey(absoluteString)) {
        return moduleCache.get(absoluteString);
      }

      if (loadingModules.contains(absoluteString)) {
        throw new RuntimeError(keyword, "CircularDependencyError",
                "Circular dependency detected! '" + absoluteString + "' is already loading.", null);
      }

      loadingModules.add(absoluteString);

      byte[] bytes = java.nio.file.Files.readAllBytes(path);
      String source = new String(bytes, java.nio.charset.Charset.defaultCharset());

      LoxModule module = executeAsModule(source);

      moduleCache.put(absoluteString, module);
      loadingModules.remove(absoluteString);

      return module;

    } catch (java.io.IOException e) {
      throw new RuntimeError(keyword, "RuntimeError", "Could not load module: " + importPath, null);
    }
  }

  private String resolveImportPath(String importPath) {
    if (importPath.endsWith(".lox") || importPath.endsWith(".loxlib")) return importPath;

    Path directPath = Path.of(importPath);
    if (Files.exists(directPath) && !Files.isDirectory(directPath)) {
      return importPath;
    }

    String modulePath = "lox_modules/" + importPath + "/index.loxlib";
    Path modPath = Path.of(modulePath);

    if (Files.exists(modPath)) {
      return modulePath;
    }

    return importPath;
  }

  private LoxModule executeAsModule(String source) {
    Scanner scanner = new Scanner(source);
    List<Token> tokens = scanner.scanTokens();
    Parser parser = new Parser(tokens);
    List<Stmt> stmts = parser.parse();

    Resolver resolver = new Resolver(this);
    resolver.resolve(stmts);

    Interpreter moduleInterpreter = new Interpreter();
    moduleInterpreter.interpret(stmts);

    LoxModule module = new LoxModule();

    for (Map.Entry<String, Object> entry : moduleInterpreter.environment.values.entrySet()) {
        module.addMember(entry.getKey(), entry.getValue());
    }

    return module;
  }

  @Override
  public Void visitUsingStmt(Stmt.Using stmt) {
    Object source = evaluate(stmt.source);

    if (source instanceof LoxString path) {
      String resolvedPath = resolveImportPath(path.value);
      LoxModule container = loadModule(resolvedPath, stmt.keyword);

      for (Expr.Variable nameExpr : stmt.names) {
        Object member = container.getMember(nameExpr.name);
        environment.define(nameExpr.name.lexeme, member);
      }
    } else if (source instanceof LoxNamespace ns) {
      for (Expr.Variable nameExpr : stmt.names) {
        environment.define(nameExpr.name.lexeme, ns.getExported(nameExpr.name));
      }
    } else {
      throw new RuntimeError(stmt.keyword, "RuntimeError", "Source must be either a path or a namespace.", null);
    }

    return null;
  }

  @Override
  public Void visitForStmt(Stmt.For stmt) {
    if (stmt.initializer != null) {
      execute(stmt.initializer);
    }

    while (stmt.condition == null || isTruthy(evaluate(stmt.condition))) {
      for (Stmt statement : stmt.body) {
        execute(statement);
      }

      if (stmt.increment != null) {
        evaluate(stmt.increment);
      }
    }

    return null;
  }

  @Override
  public Void visitImplStmt(Stmt.Impl stmt) {
    Token name = ((Expr.Variable) stmt.name).name;
    Object type = environment.get(name);

    if (!(type instanceof LoxEnum loxEnum) || !loxEnum.isUnion) {
      throw new RuntimeError(stmt.keyword, "RuntimeError", "Can only implement methods on unions.", "Make sure the type is a union.");
    }

    for (Stmt.Function method : stmt.methods) {
      LoxFunction function = new LoxFunction(method, environment, false, false, method.isAsync);
      loxEnum.addMethod(method.name.lexeme, function);
    }

    return null;
  }

  @Override
  public Object visitNewExpr(Expr.New expr) {
    Object callee = evaluate(expr.constructor.callee);

    if (!(callee instanceof LoxClass klass)) {
      throw new RuntimeError(expr.keyword, "RuntimeError", "Can only use 'new' with classes.", null);
    }

    List<Object> arguments = new ArrayList<>();
    for (Expr argument : expr.constructor.arguments) {
      arguments.add(evaluate(argument));
    }

    LoxFunction init = klass.findMethod("init");
    if (init != null) {
      int providedArgs = arguments.size();
      int expectedArgs = init.declaration().params.size();

      for (int i = providedArgs; i < expectedArgs; i++) {
        Expr defaultExpr = init.declaration().params.get(i).defaultValue();

        if (defaultExpr != null) arguments.add(evaluate(defaultExpr));
        else throw new RuntimeError(expr.keyword, "RuntimeError", "Expected " + expectedArgs + ", but got " + providedArgs + " instead.", null);
      }
    }

    return klass.call(this, arguments, true);
  }

  @Override
  public Object visitAwaitExpr(Expr.Await expr) {
    Object value = getValue(evaluate(expr.value));

    if (!(value instanceof CompletableFuture<?> promise)) {
      throw new RuntimeError(expr.keyword, "RuntimeError", "Can only await a promise.", null);
    }

    try {
      return promise.get();
    } catch (CompletionException | ExecutionException | InterruptedException e) {
      Throwable cause = e.getCause();

      if (cause instanceof RuntimeError runtimeError) {
        throw runtimeError;
      }

      if (cause instanceof Return ret) {
        throw ret;
      }

      throw new RuntimeError(expr.keyword, "RuntimeError", "Failed to await promise.", cause != null ? cause.getMessage() : e.getMessage());
    }
  }

  @Override
  public Object visitMatchExpr(Expr.Match expr) {
    Object value = getValue(evaluate(expr.value));
    Environment originalEnv = environment;

    try {
      for (Expr.MatchCase kase : expr.cases) {
        environment = new Environment(originalEnv);

        if (matchPattern(value, kase.pattern(), environment)) {
          if (kase.guard() == null || isTruthy(evaluate(kase.guard()))) {
            try {
              executeBlock(kase.body(), environment);
            } catch (Return returnValue) {
              return returnValue.value;
            }

            return null;
          }
        }

        environment = originalEnv;
      }
    } finally {
      environment = originalEnv;
    }

    throw new RuntimeError(expr.keyword, "RuntimeError", "No case matched the value", null);
  }

  @Override
  public Object visitWildcardPatternExpr(Expr.WildcardPattern expr) {
    return null;
  }

  @Override
  public Object visitUnionPatternExpr(Expr.UnionPattern expr) {
    return null;
  }

  @Override
  public Object visitListPatternExpr(Expr.ListPattern expr) { return null; }

  @Override
  public Object visitObjectPatternExpr(Expr.ObjectPattern expr) { return null; }

  private boolean matchPattern(Object value, Expr pattern, Environment env) {
    if (pattern instanceof Expr.WildcardPattern) {
      return true;
    }

    if (pattern instanceof Expr.UnionPattern unionPattern) {
      if (!(value instanceof LoxUnionInstance loxUnionInstance)) {
        return false;
      }

      if (!loxUnionInstance.caseName.equals(unionPattern.caseName.lexeme)) {
        return false;
      }

      for (int i = 0; i < unionPattern.bindings.size(); i++) {
        String fieldName = loxUnionInstance.getFieldName(i);
        env.define(unionPattern.bindings.get(i).lexeme, loxUnionInstance.fields.get(fieldName));
      }

      return true;
    }

    if (pattern instanceof Expr.Variable var) {
      Token name = var.name;
      env.define(name.lexeme, value);
      return true;
    }

    if (pattern instanceof Expr.ListPattern listPattern) {
      return matchListPattern(value, listPattern, env);
    }

    if (pattern instanceof Expr.ObjectPattern objectPattern) {
      return matchObjectPattern(value, objectPattern, env);
    }

    try {
      Object patternValue = evaluate(pattern);
      return valuesEqual(value, patternValue);
    } catch (RuntimeError e) {
      return false;
    }
  }

  private boolean valuesEqual(Object a, Object b) {
    if (a == null && b == null) return true;
    if (a == null) return false;
    return a.equals(b);
  }

  private boolean matchListPattern(Object value, Expr.ListPattern listPattern, Environment env) {
    if (!(value instanceof LoxArray array)) {
      return false;
    }

    int patternSize = listPattern.elements.size();

    if (array.elements.size() < patternSize) {
      return false;
    }

    if (listPattern.rest == null && array.elements.size() != patternSize) {
      return false;
    }

    for (int i = 0; i < patternSize; i++) {
      Object element = array.elements.get(i);
      if (!matchPattern(element, listPattern.elements.get(i), env)) {
        return false;
      }
    }

    if (listPattern.rest != null) {
      List<?> rest = array.elements.subList(patternSize, array.elements.size());
      if (listPattern.rest instanceof Expr.Variable var) {
        env.define(var.name.lexeme, rest);
      } else {
        return false;
      }
    }

    return true;
  }

  private boolean matchObjectPattern(Object value, Expr.ObjectPattern pattern, Environment env) {
    if (!(value instanceof Map<?, ?> instance)) {
      return false;
    }

    for (Expr.ObjectPattern.Property property : pattern.properties) {
      try {
        Object propertyValue = instance.get(property.name().lexeme);
        if (!matchPattern(propertyValue, property.pattern(), env)) {
          return false;
        }
      } catch (RuntimeError e) {
        return false;
      }
    }

    if (pattern.rest != null) {
      if (pattern.rest instanceof Expr.Variable) {
        env.define(((Expr.Variable) pattern.rest).name.lexeme, new HashMap<>());
      } else {
        return false;
      }
    }

    return true;
  }

  @Override
  public Object visitTernaryExpr(Expr.Ternary expr) {
    Object condition = evaluate(expr.condition);

    if (isTruthy(condition)) {
      return evaluate(expr.thenBranch);
    } else {
      return evaluate(expr.elseBranch);
    }
  }

  @Override
  public Object visitSpreadExpr(Expr.Spread expr) {
    Object value = getValue(evaluate(expr.right));

    if (value instanceof LoxArray array) {
      return array.elements;
    }

    if (value instanceof Map<?, ?> dictionary) {
      return dictionary.values();
    }

    throw new RuntimeError(expr.operator, "RuntimeError", "Only arrays and objects can be spread.", null);
  }

  @Override
  public Object visitLazyExpr(Expr.Lazy expr) {
    Environment closure = environment;

    return new LoxLazy(() -> {
      Environment previous = environment;

      try {
        this.environment = new Environment(closure);

        if (expr.expr != null) {
          return evaluate(expr.expr);
        } else {
          executeBlock(expr.statements, environment);
          return null;
        }
      } catch (Return returnValue) {
        return returnValue.value;
      } finally {
        environment = previous;
      }
    });
  }

  @Override
  public Object visitTupleLiteralExpr(Expr.TupleLiteral expr) {
    List<Object> values = new ArrayList<>();
    for (Expr element : expr.elements) {
      values.add(evaluate(element));
    }

    return new LoxTuple(values);
  }

  @Override
  public Object visitTypeofExpr(Expr.Typeof expr) {
    Object value = evaluate(expr.var);

    if (value != null) {
      String ty = switch (value.getClass().getSimpleName()) {
        case "Double", "Integer" -> "Double";
        case "String" -> "String";
        case "LoxString" -> "LoxString";
        case "Boolean" -> "Boolean";
        case "LoxClass" -> "Class";
        case "LoxInstance" -> "Instance";
        case "LoxTrait" -> "Trait";
        case "LoxEnum" -> "Enum";
        case "HashMap" -> "Dict";
        case "LoxFunction", "Lambda" -> "Function";
        case "LoxArray" -> "Array";
        case "LoxCallable" -> "Callable";
        case "LoxLazy" -> "Lazy";
        case "LoxNamespace" -> "Namespace";

        default -> null;
      };

      return new LoxString(ty);
    }

    return null;
  }

  @Override
  public Object visitDictionaryExpr(Expr.Dictionary expr) {
    Map<LoxString, Object> dict = new HashMap<>();
    environment = new Environment(environment);

    for (Map.Entry<Token, Expr> entry : expr.keyValues.entrySet()) {
      if (entry.getValue() instanceof Expr.Spread spread) {
        Object value = evaluate(spread.right);
        if (value instanceof Map<?, ?> dictionary) {
          for (Map.Entry<?, ?> spreadDictEntry : dictionary.entrySet()) {
            dict.put(new LoxString(spreadDictEntry.getKey().toString()), spreadDictEntry.getValue());
          }
        } else {
          throw new RuntimeError(entry.getKey(), "RuntimeError", "Only dictionaries can be spread inside other dictionaries.", null);
        }
      } else {
        Object value = evaluate(entry.getValue());
        environment.define(entry.getKey().lexeme, value);
        dict.put(new LoxString(entry.getKey().lexeme), value);
      }
    }

    environment = environment.enclosing;
    return dict;
  }

  @Override
  public Object visitLambdaExpr(Expr.Lambda expr) {
    List<Stmt.Function.Param> params = new ArrayList<>(expr.params);
    return new LoxFunction(new Stmt.Function(null, params, expr.body, false, expr.isAsync, false), environment, false, false, expr.isAsync);
  }

  @Override
  public Object visitArrayExpr(Expr.Array expr) {
    List<Object> elements = new ArrayList<>();
    for (Expr element : expr.elements) {
      Object value = evaluate(element);
      if (element instanceof Expr.Spread) {
        if (value instanceof List<?> array) {
          elements.addAll(array);
        }
      } else {
        elements.add(value);
      }
    }

    return new LoxArray(this, elements);
  }

  @Override
  public Object visitArraySubscriptGetExpr(Expr.SubscriptGet expr) {
    Object indexee = getValue(evaluate(expr.indexee));
    Object index = getValue(evaluate(expr.index));

    if (indexee instanceof Map<?, ?> dict) {
      String searchKey = (index instanceof LoxString loxStr) ? loxStr.value : index.toString();
      searchKey = searchKey.replace("\"", "");

      for (Map.Entry<?, ?> entry : dict.entrySet()) {
        Object mapKey = entry.getKey();

        String currentKey = (mapKey instanceof LoxString lStr) ? lStr.value : mapKey.toString();
        currentKey = currentKey.replace("\"", "");

        if (searchKey.equals(currentKey)) {
          return entry.getValue();
        }
      }

      return null;
    }

    LoxTrait indexableTrait = (LoxTrait) environment.get("Indexable");
    if (indexee instanceof LoxInstance instance) {
      if (!instance.klass.hasTrait(indexableTrait)) {
        throw new RuntimeError(instance.klass.token, "RuntimeError", "Class '" + instance.klass.name + "' does not implement trait 'Indexable'.", "Consider implementing the 'Indexable' trait and its methods: 'get' and 'set'.");
      }

      if (instance.klass.methods.containsKey("get")) {
        return instance.klass.methods.get("get").bind(instance).call(this, List.of(index), false);
      }
    }

    if (indexee instanceof LoxIndexable indexable) {
      return indexable.get(expr.bracket, index);
    }

    throw new RuntimeError(expr.bracket, "RuntimeError", "Object cannot be indexable.", null);
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object visitArraySubscriptSetExpr(Expr.SubscriptSet expr) {
    Object indexee = getValue(evaluate(expr.indexee));
    Object index = getValue(evaluate(expr.index));
    Object value = getValue(evaluate(expr.value));

    switch (indexee) {
      case Map<?, ?> dictionary -> {
        String keyStr = (index instanceof LoxString loxStr) ? loxStr.value : index.toString();

        Map<String, Object> dict = (Map<String, Object>) dictionary;
        dict.put(keyStr, value);
        return value;
      }
      case LoxIndexable indexable -> {
        indexable.set(expr.bracket, index, value);

        return value;
      }
      case LoxInstance instance -> {
        LoxTrait indexableTrait = (LoxTrait) environment.get("Indexable");
        if (!instance.klass.hasTrait(indexableTrait)) {
          throw new RuntimeError(instance.klass.token, "RuntimeError", "Class '" + instance.klass.name + "' does not implement trait 'Indexable'.", "Consider implementing the 'Indexable' trait and its methods: 'get' and 'set'.");
        }

        if (instance.klass.methods.containsKey("set")) {
          return instance.klass.methods.get("set").bind(instance).call(this, List.of(index, value), false);
        }
      }
      case null, default -> throw new RuntimeError(expr.bracket, "RuntimeError", "Variable is not indexable.", null);
    }

    throw new RuntimeError(expr.bracket, "RuntimeError", "THIS CODE SHOULD BE UNREACHABLE!!!!!", "Please report this over at https://github.com/andre-1337/loxpp/issues/new");
  }

  @Override
  public Object visitAssignExpr(Expr.Assign expr) {
    Object value = evaluate(expr.value);
    Integer distance = locals.get(expr);

    if (distance != null) {
      environment.assignAt(distance, expr.name, value);
    } else {
      globals.assign(expr.name, value);
    }

    return value;
  }

  private String getMethodName(TokenType op) {
    return switch (op) {
      case PLUS -> "_add";
      case MINUS -> "_sub";
      case STAR -> "_mul";
      case SLASH -> "_div";
      case EQUAL_EQUAL -> "_eq";
      case BANG_EQUAL -> "_neq";
      case LESS -> "_lt";
      case GREATER -> "_gt";
      case LESS_EQUAL -> "_lte";
      case GREATER_EQUAL -> "_gte";
      case null, default -> null;
    };
  }

  @Override
  public Object visitBinaryExpr(Expr.Binary expr) {
    Object left = getValue(evaluate(expr.left));
    Object right = getValue(evaluate(expr.right));

    String operatorMethod = getMethodName(expr.operator.type);
    LoxTrait computableTrait = (LoxTrait) environment.get("Computable");
    LoxTrait comparableTrait = (LoxTrait) environment.get("Comparable");

    if (operatorMethod != null) {
      if (operatorMethod.equals("_add") || operatorMethod.equals("_sub") || operatorMethod.equals("_mul") || operatorMethod.equals("_div")) {
        if (left instanceof LoxInstance instance) {
          if (!instance.klass.hasTrait(computableTrait)) {
            throw new RuntimeError(instance.klass.token, "RuntimeError", "Class '" + instance.klass.name + "' does not implement trait 'Computable'.", "Consider implementing the 'Computable' trait and its methods: '_add', '_sub', '_mul' and '_div'.");
          }

          if (instance.klass.methods.containsKey(operatorMethod)) {
            return instance.klass.methods.get(operatorMethod).bind(instance).call(this, Collections.singletonList(right), false);
          }
        }
      }

      if (operatorMethod.equals("_eq") || operatorMethod.equals("_neq") || operatorMethod.equals("_lt") || operatorMethod.equals("_gt") || operatorMethod.equals("_lte") || operatorMethod.equals("_gte")) {
        if (left instanceof LoxInstance instance) {
          if (!instance.klass.hasTrait(comparableTrait)) {
            throw new RuntimeError(instance.klass.token, "RuntimeError", "Class '" + instance.klass.name + "' does not implement trait 'Comparable'.", "Consider implementing the 'Comparable' trait and its methods: '_eq' and '_neq'.");
          }

          if (instance.klass.methods.containsKey(operatorMethod)) {
            return instance.klass.methods.get(operatorMethod).bind(instance).call(this, Collections.singletonList(right), false);
          }
        }
      }
    }

    switch (expr.operator.type) {
      case QUESTION_QUESTION:
        return (left != null) ? left : evaluate(expr.right);
      case BANG_EQUAL:
        return !isEqual(left, right);
      case EQUAL_EQUAL:
        return isEqual(left, right);
      case GREATER:
        checkNumberOperands(expr.operator, left, right);
        return (double) left > (double) right;
      case GREATER_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double) left >= (double) right;
      case LESS:
        checkNumberOperands(expr.operator, left, right);
        return (double) left < (double) right;
      case LESS_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double) left <= (double) right;
      case MINUS:
        checkNumberOperands(expr.operator, left, right);
        return (double) left - (double) right;
      case PERCENT:
        checkNumberOperands(expr.operator, left, right);
        return (double) left % (double) right;
      case PLUS:
        if (left instanceof Double && right instanceof Double) {
          return (double) left + (double) right;
        }

        if (left instanceof String || left instanceof LoxString || right instanceof String || right instanceof LoxString) {
          String leftStr = (left instanceof LoxString lox) ? lox.value : stringify(left);
          String rightStr = (right instanceof LoxString lox) ? lox.value : stringify(right);

          return new LoxString(leftStr + rightStr);
        }

        if ((left instanceof Double || left instanceof Map<?, ?> || left instanceof LoxArray) && (right instanceof Double || right instanceof Map<?, ?> || right instanceof LoxArray)) {
          return stringify(left) + stringify(right);
        }

        throw new RuntimeError(expr.operator, "RuntimeError", "Operands must be two numbers or two strings.", null);
      case SLASH:
        checkNumberOperands(expr.operator, left, right);
        return (double) left / (double) right;
      case STAR:
        checkNumberOperands(expr.operator, left, right);
        return (double) left * (double) right;

      case EXPONENTIATION:
        checkNumberOperands(expr.operator, left, right);
        return Math.pow((double) left, (double) right);

      case IS:
        if (right instanceof LoxClass kls) return loxIsInstance(left, kls);
        else if (right instanceof Class<?> kls) return kls.isInstance(left);
        return false;

      case DOT_DOT:
        if (!(right instanceof Double) || !(left instanceof Double)) {
          throw new RuntimeError(expr.operator, "RuntimeError", "Range bounds must be numbers.", null);
        }

        LoxClass rangeClass = (LoxClass) environment.get("Range");
        return rangeClass.call(this, List.of(left, right), false);
      case null, default: return null;
    }
  }

  private boolean loxIsInstance(Object left, LoxClass klass) {
    if (left instanceof LoxInstance instance) {
      LoxClass kls = instance.klass;

      while (kls != null && !kls.equals(klass)) {
        kls = kls.superclass;
      }

      return kls != null;
    }

    return false;
  }

  private List<Object> fillDefaultArguments(LoxFunction function, List<Object> providedArgs, Token paren) {
    List<Object> filledArgs = new ArrayList<>(providedArgs);
    Stmt.Function declaration = function.declaration();

    for (int i = filledArgs.size(); i < declaration.params.size(); i++) {
      Stmt.Function.Param param = declaration.params.get(i);

      if (param.defaultValue() != null) {
        filledArgs.add(evaluate(param.defaultValue()));
      } else {
        throw new RuntimeError(
                paren,
                "RuntimeError",
                "Expected " + declaration.params.size() + " arguments but got " + providedArgs.size() + " instead.",
                null
        );
      }
    }

    if (filledArgs.size() > declaration.params.size()) {
      throw new RuntimeError(
              paren,
              "RuntimeError",
              "Expected " + declaration.params.size() + " arguments but got " + providedArgs.size() + " instead.",
              null
      );
    }

    return filledArgs;
  }

  @Override
  public Object visitCallExpr(Expr.Call expr) {
    Object callee = getValue(evaluate(expr.callee));

      switch (callee) {
          case LoxCallable function -> {
              List<Object> arguments = new ArrayList<>();
              for (Expr argument : expr.arguments) {
                  arguments.add(getValue(evaluate(argument)));
              }

              if (callee instanceof LoxUnionConstructor loxUnionConstructor) {
                return loxUnionConstructor.call(this, arguments, false);
              }

              if (function instanceof LoxFunction fn) {
                arguments = fillDefaultArguments(fn, arguments, expr.paren);
              }

              return function.call(this, arguments, false);
          }

          case LoxTrait trait -> throw new RuntimeError(trait.name(), "RuntimeError", "Traits cannot be constructed nor instantiated.", null);

        case null, default -> throw new RuntimeError(expr.paren, "RuntimeError", "Can only call functions and classes.", null);
      }
  }

  @Override
  public Object visitGetExpr(Expr.Get expr) {
    Object object = getValue(evaluate(expr.object));

    if (object instanceof LoxInstance instance) {
      LoxFunction method = instance.klass.findMethod(expr.name.lexeme);
      if (method != null) {
        return method.bind(instance);
      }

      return instance.get(expr.name);
    }

    if (object instanceof LoxArray array) {
      if (expr.name.literal instanceof Double index) {
        int idx = (int) Math.floor(index);

        try {
          return array.elements.get(idx);
        } catch (IndexOutOfBoundsException e) {
          throw new RuntimeError(expr.name, "RuntimeError", "Array index is out of bounds", null);
        }
      } else {
        return array.getMethod(expr.name);
      }
    }

    if (object instanceof Map<?, ?> dict) {
      return dict.get(expr.name.lexeme);
    }

    if (object instanceof LoxEnum loxEnum) {
      String caseName = expr.name.lexeme;

      if (loxEnum.hasCase(caseName)) {
        return loxEnum.getConstructor(caseName);
      }

      throw new RuntimeError(expr.name, "RuntimeError", "Undefined case '" + caseName + "' in " + loxEnum.name, null);
    }

    if (object instanceof LoxUnionInstance loxUnionInstance) {
      return loxUnionInstance.get(expr.name);
    }

    if (object instanceof LoxTuple tuple) {
      int index = Integer.parseInt(expr.name.lexeme);

      if (index < 0 || index >= tuple.size()) {
        throw new RuntimeError(expr.name, "RuntimeError", "Index out of bounds for tuple access.", null);
      }

      return tuple.get(index);
    }

    if (object instanceof LoxNamespace namespace) {
      return namespace.getExported(expr.name);
    }

    if (object instanceof LoxString string) {
      return string.getMethod(expr.name);
    }

    if (object instanceof String str) {
      return new LoxString(str).getMethod(expr.name);
    }

    throw new RuntimeError(
            expr.name,
            "RuntimeError",
            "Only instances, arrays, dictionaries and tuples have properties.",
            null
    );
  }

  @Override
  public Object visitGroupingExpr(Expr.Grouping expr) {
    return evaluate(expr.expression);
  }

  @Override
  public Object visitLiteralExpr(Expr.Literal expr) {
    if (expr.value instanceof String string) {
      return new LoxString(string);
    } else {
      return expr.value;
    }
  }

  @Override
  public Object visitLogicalExpr(Expr.Logical expr) {
    Object left = evaluate(expr.left);

    if (expr.operator.type == TokenType.LOGICAL_OR) {
      if (isTruthy(left)) {
        return left;
      }
    } else {
      if (!isTruthy(left)) {
        return left;
      }
    }

    return evaluate(expr.right);
  }

  @Override
  public Object visitSetExpr(Expr.Set expr) {
    Object object = evaluate(expr.object);

    if (!(object instanceof LoxInstance)) {
      throw new RuntimeError(expr.name, "RuntimeError", "Only instances have fields.", null);
    }

    Object value = evaluate(expr.value);
    ((LoxInstance) object).set(expr.name, value);
    return value;
  }

  @Override
  public Object visitSuperExpr(Expr.Super expr) {
    int distance = locals.get(expr);
    LoxClass superclass = (LoxClass) environment.getAt(distance, "super");
    LoxInstance object = (LoxInstance) environment.getAt(distance - 1, "self");
    LoxFunction method = superclass.findMethod(expr.method.lexeme);

    if (method == null) {
      throw new RuntimeError(expr.method, "RuntimeError", "Undefined property '" + expr.method.lexeme + "'.", null);
    }

    return method.bind(object);
  }

  @Override
  public Object visitThisExpr(Expr.This expr) {
    return lookUpVariable(expr.keyword, expr);
  }

  @Override
  public Object visitUnaryExpr(Expr.Unary expr) {
    Object right = getValue(evaluate(expr.right));

    if (expr.operator.type == TokenType.MINUS) {
      checkNumberOperand(expr.operator, right);
      return -(double) right;
    } else if (expr.operator.type == TokenType.BANG) {
      return !isTruthy(right);
    }

    if (expr.operator.type == TokenType.MINUS_MINUS || expr.operator.type == TokenType.PLUS_PLUS) {
      if (expr.right instanceof Expr.Variable) {
        Token var = ((Expr.Variable) expr.right).name;
        checkNumberOperand(expr.operator, right);
        double newValue = (double) right + (expr.operator.type == TokenType.MINUS_MINUS ? -1 : 1);
        environment.assign(var, newValue);
        return newValue;
      } else if (expr.right instanceof Expr.Literal) {
        checkNumberOperand(expr.operator, right);
        return (double) right + (expr.operator.type == TokenType.MINUS_MINUS ? -1 : 1);
      } else {
        throw new RuntimeError(expr.operator, "RuntimeError", "Operand must be a variable.", null);
      }
    }

    return null;
  }

  @Override
  public Object visitVariableExpr(Expr.Variable expr) {
    Object value = environment.get(expr.name);
    if (value == uninitialized) {
      throw new RuntimeError(expr.name, "RuntimeError", "Variable must be initialized before use.", null);
    }

    return lookUpVariable(expr.name, expr);
  }

  private Object lookUpVariable(Token name, Expr expr) {
    Integer distance = locals.get(expr);
    if (distance != null) {
      return environment.getAt(distance, name.lexeme);
    } else {
      return globals.get(name);
    }
  }

  private void checkNumberOperand(Token operator, Object operand) {
    if (operand instanceof Double) {
      return;
    }

    throw new RuntimeError(operator, "RuntimeError", "Operand must be a number.", null);
  }

  private void checkNumberOperands(Token operator, Object left, Object right) {
    if (left instanceof Double && right instanceof Double) {
      return;
    }

    throw new RuntimeError(operator, "RuntimeError", "Operands must be numbers.", null);
  }

  private boolean isTruthy(Object object) {
    object = getValue(object);

    if (object == null) return false;
    if (object instanceof Boolean bool) return bool;

    return true;
  }

  private boolean isEqual(Object a, Object b) {
    a = getValue(a);
    b = getValue(b);

    if (a == null && b == null) return true;
    if (a == null) return false;

    return a.equals(b);
  }

  public static String stringify(Object object) {
      return switch (object) {
          case null -> "null";

          case Double ignored -> {
              String text = object.toString();
              if (text.endsWith(".0")) {
                  text = text.substring(0, text.length() - 2);
              }

              yield text;
          }

          case Map<?, ?> dictionary -> {
              StringBuilder sb = new StringBuilder();
              sb.append("{ ");
              boolean first = true;
              for (Map.Entry<?, ?> entry : dictionary.entrySet()) {
                  if (!first) {
                      sb.append(", ");
                  }
                  first = false;
                  sb.append(entry.getKey().toString())
                          .append(": ")
                          .append(stringify(entry.getValue()));
              }
              sb.append(" }");

              yield sb.toString();
          }

          default -> object.toString();
      };
  }

  private Object getValue(Object obj) {
    if (obj instanceof LoxLazy lazy) {
      return lazy.get();
    }

    return obj;
  }
}
