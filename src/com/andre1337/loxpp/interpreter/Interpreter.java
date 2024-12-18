package com.andre1337.loxpp.interpreter;

import com.andre1337.loxpp.Lox;
import com.andre1337.loxpp.ast.Expr;
import com.andre1337.loxpp.ast.Stmt;
import com.andre1337.loxpp.classes.*;
import com.andre1337.loxpp.lexer.Token;
import com.andre1337.loxpp.lexer.TokenType;

import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {
  private final Map<Expr, Integer> locals = new HashMap<>();
  private Object currentMatchValue = null;
  public final Environment globals = new Environment();
  public Environment environment = globals;

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

    // networking
    globals.define("___socket_connect___", new LoxCallable() {
      @Override
      public int arity() {
        return 2;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        String host = ((LoxString) arguments.getFirst()).value;
        int port = (int) (double) arguments.get(1);

        return LoxAsyncSocket.___connect___(host, port);
      }
    });

    globals.define("___socket_send___", new LoxCallable() {
      @Override
      public int arity() {
        return 2;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        AsynchronousSocketChannel socket = (AsynchronousSocketChannel) arguments.getFirst();
        String data = ((LoxString) arguments.get(1)).value;

        return LoxAsyncSocket.___send___(socket, data);
      }
    });

    globals.define("___socket_receive___", new LoxCallable() {
      @Override
      public int arity() {
        return 2;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        AsynchronousSocketChannel socket = (AsynchronousSocketChannel) arguments.getFirst();
        int bufferSize = (int) (double) arguments.get(1);

        return LoxAsyncSocket.___receive___(socket, bufferSize);
      }
    });

    globals.define("___socket_close___", new LoxCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        AsynchronousSocketChannel socket = (AsynchronousSocketChannel) arguments.getFirst();
        return LoxAsyncSocket.___close___(socket);
      }
    });

    globals.define("___server_bind_and_listen___", new LoxCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        int port = (int) (double) arguments.getFirst();
        return LoxAsyncSocket.___bind_and_listen___(port);
      }
    });

    globals.define("___server_accept___", new LoxCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        AsynchronousServerSocketChannel serverSocket = (AsynchronousServerSocketChannel) arguments.getFirst();
        return LoxAsyncSocket.___accept___(serverSocket);
      }
    });

    globals.define("___server_close___", new LoxCallable() {
      @Override
      public int arity() {
        return 1;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        AsynchronousServerSocketChannel serverSocket = (AsynchronousServerSocketChannel) arguments.getFirst();
        return LoxAsyncSocket.___close_server___(serverSocket);
      }
    });

    // concurrency
    globals.define("___channel_new___", new LoxCallable() {
      @Override
      public int arity() {
        return 0;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        return null;
      }
    });

    globals.define("___channel_send___", new LoxCallable() {
      @Override
      public int arity() {
        return 0;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        return null;
      }
    });

    globals.define("___channel_receive___", new LoxCallable() {
      @Override
      public int arity() {
        return 0;
      }

      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        return null;
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
    return getValue(expr.accept(this));
  }

  private void execute(Stmt stmt) {
    stmt.accept(this);
  }

  public void resolve(Expr expr, int depth) {
    locals.put(expr, depth);
  }

  public void executeBlock(List<Stmt> statements, Environment environment) {
    Environment previous = this.environment;
    try {
      this.environment = environment;

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
        throw new RuntimeError(stmt.name, "RuntimeError", error.getMessage(), error.hint);
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
      String traitDefParamName = traitDef.declaration().params.get(i).lexeme;
      String classImplParamName = classImpl.declaration().params.get(i).lexeme;

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
      String methodName = entry.getKey();
      LoxFunction fn = entry.getValue();

      if (fn.isAbstract() && (!klass.methods.containsKey(methodName))) {
        throw new RuntimeError(fn.declaration().name, "RuntimeError",
            "Class '" + klass.name + "' does not implement abstract method '" + fn.declaration().name.lexeme + "' from trait '" + trait.name().lexeme + "'.",
                "Consider implementing the abstract method '" + fn.declaration().name.lexeme + "' from trait '" + trait.name().lexeme + "' in class '" + klass.name + "'."
        );
      }

      for (Map.Entry<String, LoxFunction> classEntry : klass.methods.entrySet()) {
        LoxFunction classMethod = classEntry.getValue();

        if (!trait.methods().containsKey(classMethod.declaration().name.lexeme)) {
          return;
        }

        verifySignature(fn, classMethod);
      }
    }
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
      if (!(traitObj instanceof LoxTrait trait)) {
        Token name = ((Expr.Variable) traitExpr).name;
        throw new RuntimeError(name, "RuntimeError", "'" + name.lexeme + "' is not a trait.", null);
      }

      for (String name : trait.methods().keySet()) {
        if (methods.containsKey(name)) {
          throw new RuntimeError(trait.name(), "RuntimeError",
              "A previously implemented trait already declares method '" + name + "'.",
                  "Consider removing or renaming the method '" + name + "'."
          );
        }

        methods.put(name, trait.methods().get(name));
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
    Object value = null;

    if (stmt.initializer != null) {
      value = evaluate(stmt.initializer);
    }

    environment.define(stmt.name.lexeme, value);

    return null;
  }

  @Override
  public Void visitObjectDestructuringStmt(Stmt.ObjectDestructuring stmt) {
    Object value = evaluate(stmt.initializer);

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
    Object value = evaluate(stmt.initializer);

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
    Object iterable = evaluate(stmt.iterable);

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
          environment.define(stmt.key.lexeme, entry.getKey());

          if (stmt.value != null) {
            environment.define(stmt.value.lexeme, entry.getValue());
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

        while ((boolean) hasNext.call(this, List.of())) {
          Object currentValue = next.call(this, Collections.emptyList());
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

  private boolean matchValues(Object value, Object pattern) {
    if (value == null || pattern == null) {
      return value == pattern;
    }

    return value.equals(pattern);
  }

  @Override
  public Void visitMatchStmt(Stmt.Match stmt) {
    currentMatchValue = evaluate(stmt.expression);
    for (Stmt.MatchCase matchCase : stmt.cases) {
      matchCase.accept(this);
    }
    return null;
  }

  @Override
  public Void visitMatchCaseStmt(Stmt.MatchCase stmt) {
    if (matchValues(currentMatchValue, evaluate(stmt.pattern))) {
      execute(stmt.statement);
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
    Object messageMethod = ((LoxCallable) thrown.get(messageToken)).call(this, new ArrayList<>());

    throw new UserRuntimeError(thrown, thrown.klass.name, stringify(messageMethod), stmt.keyword);
  }

  @Override
  public Void visitEnumStmt(Stmt.Enum stmt) {
    Map<String, LoxEnum.Variant> variants = new HashMap<>();

    for (Stmt.Enum.Variant variant : stmt.variants) {
      List<Token> fields = new ArrayList<>(variant.parameters());
      LoxEnum.Variant enumVariant = new LoxEnum.Variant(variant.name(), fields);
      variants.put(variant.name().lexeme, enumVariant);
    }

    LoxEnum enumDef = new LoxEnum(stmt.name, variants);
    environment.define(stmt.name.lexeme, enumDef);

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
    environment = new Environment(environment);

    for (Stmt statement : stmt.body) {
      execute(statement);
    }

    for (Map.Entry<String, Object> entry : environment.values.entrySet()) {
      namespace.define(entry.getKey(), entry.getValue());
    }

    environment = environment.enclosing;
    environment.define(stmt.name.lexeme, namespace);
    return null;
  }

  @Override
  public Void visitUsingStmt(Stmt.Using stmt) {
    Object source = evaluate(stmt.source);

    if (!(source instanceof LoxNamespace namespace)) {
      throw new RuntimeError(stmt.keyword, "RuntimeError", "Source must be a namespace.", null);
    }

    for (Expr.Variable nameExpr : stmt.names) {
      Token name = nameExpr.name;
      Object member = namespace.get(name);

      environment.define(name.lexeme, member);
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
  public Object visitAwaitExpr(Expr.Await expr) {
    Object value = evaluate(expr.value);

    if (!(value instanceof CompletableFuture promise)) {
      throw new RuntimeError(expr.keyword, "RuntimeError", "Can only await a promise.", null);
    }

    try {
      return promise.get();
    } catch (InterruptedException | ExecutionException e) {
      System.out.println(Arrays.toString(e.getStackTrace()));
      throw new RuntimeError(expr.keyword, "", e.getMessage(), null);
    }
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
    Object value = evaluate(expr.right);

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
    return new LoxLazy(() -> evaluate(expr.expr));
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
      return switch (value.getClass().getSimpleName()) {
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
    }

    return null;
  }

  @Override
  public Object visitDictionaryExpr(Expr.Dictionary expr) {
    Map<String, Object> dict = new HashMap<>();
    environment = new Environment(environment);

    for (Map.Entry<Token, Expr> entry : expr.keyValues.entrySet()) {
      if (entry.getValue() instanceof Expr.Spread spread) {
        Object value = evaluate(spread.right);
        if (value instanceof Map<?, ?> dictionary) {
          for (Map.Entry<?, ?> spreadDictEntry : dictionary.entrySet()) {
            dict.put(spreadDictEntry.getKey().toString(), spreadDictEntry.getValue());
          }
        } else {
          throw new RuntimeError(entry.getKey(), "RuntimeError", "Only dictionaries can be spread inside other dictionaries.", null);
        }
      } else {
        Object value = evaluate(entry.getValue());
        environment.define(entry.getKey().lexeme, value);
        dict.put(entry.getKey().lexeme, value);
      }
    }

    environment = environment.enclosing;
    return dict;
  }

  @Override
  public Object visitLambdaExpr(Expr.Lambda expr) {
    List<Token> params = new ArrayList<>(expr.params);
    return new LoxFunction(
            new Stmt.Function(
                    null,
                    params,
                    expr.body,
                    false,
                    expr.isAsync
            ),
            environment,
            false,
            false,
            expr.isAsync
    );
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
    Object indexee = evaluate(expr.indexee);
    Object index = evaluate(expr.index);

    LoxTrait indexableTrait = (LoxTrait) environment.get("Indexable");
    if (indexee instanceof LoxInstance instance) {
      if (!instance.klass.hasTrait(indexableTrait)) {
        throw new RuntimeError(instance.klass.token, "RuntimeError", "Class '" + instance.klass.name + "' does not implement trait 'Indexable'.", "Consider implementing the 'Indexable' trait and its methods: 'get' and 'set'.");
      }

      if (instance.klass.methods.containsKey("get")) {
        return instance.klass.methods.get("get").bind(instance).call(this, List.of(index));
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
    Object indexee = evaluate(expr.indexee);

    switch (indexee) {
      case Map<?, ?> dictionary -> {
        Object index = evaluate(expr.index);
        Object value = evaluate(expr.value);

        ((Map<LoxString, Object>) dictionary).put((LoxString) index, value);

        return value;
      }
      case LoxIndexable indexable -> {
        Object index = evaluate(expr.index);
        Object value = evaluate(expr.value);

        indexable.set(expr.bracket, index, value);

        return value;
      }
      case LoxInstance instance -> {
        Object index = evaluate(expr.index);
        Object value = evaluate(expr.value);

        LoxTrait indexableTrait = (LoxTrait) environment.get("Indexable");
        if (!instance.klass.hasTrait(indexableTrait)) {
          throw new RuntimeError(instance.klass.token, "RuntimeError", "Class '" + instance.klass.name + "' does not implement trait 'Indexable'.", "Consider implementing the 'Indexable' trait and its methods: 'get' and 'set'.");
        }

        if (instance.klass.methods.containsKey("set")) {
          return instance.klass.methods.get("set").bind(instance).call(this, List.of(index, value));
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
    Object left = evaluate(expr.left);
    Object right = evaluate(expr.right);

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
            return instance.klass.methods.get(operatorMethod).bind(instance).call(this, List.of(right));
          }
        }
      }

      if (operatorMethod.equals("_eq") || operatorMethod.equals("_neq") || operatorMethod.equals("_lt") || operatorMethod.equals("_gt") || operatorMethod.equals("_lte") || operatorMethod.equals("_gte")) {
        if (left instanceof LoxInstance instance) {
          if (!instance.klass.hasTrait(comparableTrait)) {
            throw new RuntimeError(instance.klass.token, "RuntimeError", "Class '" + instance.klass.name + "' does not implement trait 'Comparable'.", "Consider implementing the 'Comparable' trait and its methods: '_eq' and '_neq'.");
          }

          if (instance.klass.methods.containsKey(operatorMethod)) {
            return instance.klass.methods.get(operatorMethod).bind(instance).call(this, List.of(right));
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

        if ((left instanceof Double || left instanceof String || left instanceof LoxString || left instanceof Map<?, ?> || left instanceof LoxArray) && (right instanceof Double || right instanceof String || right instanceof LoxString || right instanceof Map<?, ?> || right instanceof LoxArray)) {
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
        return rangeClass.call(this, List.of(left, right));
      case null, default: return null;
    }
  }

  private boolean loxIsInstance(Object left, LoxClass klass) {
    if (left instanceof LoxInstance) {
      LoxClass kls = ((LoxInstance) left).klass;

      while (kls != null && !kls.equals(klass)) {
        kls = kls.superclass;
      }

      return kls != null;
    }

    return false;
  }

  @Override
  public Object visitCallExpr(Expr.Call expr) {
    Object callee = evaluate(expr.callee);

      switch (callee) {
          case LoxCallable function -> {
              List<Object> arguments = new ArrayList<>();
              for (Expr argument : expr.arguments) {
                  arguments.add(evaluate(argument));
              }

              if (function.arity() != -1 && arguments.size() != function.arity()) {
                  throw new RuntimeError(expr.paren, "RuntimeError", "Expected " + function.arity() + " arguments but got " + expr.arguments.size() + ".", null);
              }

              return function.call(this, arguments);
          }

          case LoxTrait trait -> throw new RuntimeError(trait.name(), "RuntimeError", "Traits cannot be constructed nor instantiated.", null);

        case null, default -> throw new RuntimeError(expr.paren, "RuntimeError", "Can only call functions and classes.", null);
      }
  }

  @Override
  public Object visitGetExpr(Expr.Get expr) {
    Object object = evaluate(expr.object);

    if (object instanceof LoxInstance instance) {
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

    if (object instanceof LoxEnum enumDef) {
      LoxEnum.Variant enumVariant = enumDef.getVariant(expr.name);
      if (enumVariant.parameters().isEmpty()) {
        return enumVariant.name().lexeme;
      } else {
        return enumVariant;
      }
    }

    if (object instanceof LoxTuple tuple) {
      int index = Integer.parseInt(expr.name.lexeme);

      if (index < 0 || index >= tuple.size()) {
        throw new RuntimeError(expr.name, "RuntimeError", "Index out of bounds for tuple access.", null);
      }

      return tuple.get(index);
    }

    if (object instanceof LoxNamespace namespace) {
      return namespace.get(expr.name);
    }

    if (object instanceof LoxString string) {
      return string.getMethod(expr.name);
    }

    if (object instanceof String str) {
      return new LoxString(this, str).getMethod(expr.name);
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
      return new LoxString(this, string);
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
    Object right = evaluate(expr.right);

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
    if (object == null) {
      return false;
    }

    if (object instanceof Boolean) {
      return (boolean) object;
    }
    return true;
  }

  private boolean isEqual(Object a, Object b) {
    if (a == null && b == null) {
      return true;
    }

    if (a == null) {
      return false;
    }

    return a.equals(b);
  }

  public static String stringify(Object object) {
      switch (object) {
          case null -> {
              return "null";
          }
          case Double ignored -> {
              String text = object.toString();
              if (text.endsWith(".0")) {
                  text = text.substring(0, text.length() - 2);
              }
              return text;
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
              return sb.toString();
          }
          default -> {
          }
      }

      return object.toString();
  }

  private Object getValue(Object obj) {
    if (obj instanceof LoxLazy lazy) {
      return lazy.get();
    }

    return obj;
  }
}
