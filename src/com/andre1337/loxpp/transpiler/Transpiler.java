package com.andre1337.loxpp.transpiler;

import com.andre1337.loxpp.ast.Expr;
import com.andre1337.loxpp.ast.Stmt;
import com.andre1337.loxpp.classes.LoxLazy;
import com.andre1337.loxpp.classes.RuntimeError;
import com.andre1337.loxpp.lexer.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Transpiler implements Stmt.Visitor<Void>, Expr.Visitor<String> {
    public StringBuilder output;

    public Transpiler() {
        this.output = new StringBuilder();
    }

    public void transpile(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError ignored) {}
    }

    public String getOutput() {
        return output.toString();
    }

    private Object evaluate(Expr expr) {
        if (expr == null) return null;
        return getValue(expr.accept(this));
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    private void executeBody(List<Stmt> block) {
        for (Stmt stmt : block) {
            execute(stmt);
        }
    }

    public static String stringify(Object object) {
        if (object == null)
            return "null";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        if (object instanceof Map<?, ?> dictionary) {
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
        return object.toString();
    }

    private Object getValue(Object obj) {
        if (obj instanceof LoxLazy lazy) {
            return lazy.get();
        }

        return obj;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        return null;
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        String name = stmt.name.lexeme;
        List<String> params = new ArrayList<>();
        for (Token param : stmt.params) {
            params.add(param.lexeme);
        }

        if (params.size() == 1) {
            output.append(String.format("const %s = %s => {\n", name, params.getFirst()));
            for (Stmt statement : stmt.body) {
                execute(statement);
            }
            output.append("}\n\n");
        } else {
            String paramsString = params.toString();
            String paramString = paramsString.substring(1).substring(0, paramsString.length() - 2);
            output.append(String.format("const %s = (%s) => {\n", name, paramString));
            for (Stmt statement : stmt.body) {
                execute(statement);
            }
            output.append("}\n\n");
        }

        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        if (stmt.value != null) {
            output.append(String.format("return %s;\n", evaluate(stmt.value)));
        } else {
            output.append("return;\n");
        }

        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        String name = stmt.name.lexeme;

        switch (stmt.initializer) {
            case Expr.Lambda lambda -> {
                List<String> params = new ArrayList<>();
                for (Token param : lambda.params) {
                    params.add(param.lexeme);
                }

                if (params.size() == 1) {
                    output.append(String.format("const %s = %s => {\n", name, params.getFirst()));
                    for (Stmt statement : lambda.body) {
                        execute(statement);
                    }
                    output.append("}\n\n");
                } else {
                    String paramsString = params.toString();
                    String paramString = paramsString.substring(1).substring(0, paramsString.length() - 2);
                    output.append(String.format("const %s = (%s) => {\n", name, paramString));
                    for (Stmt statement : lambda.body) {
                        execute(statement);
                    }
                    output.append("}\n\n");
                }
            }

            case null -> output.append(String.format("let %s = null;\n\n", name));

            default -> output.append(String.format("let %s = %s;\n\n", name, evaluate(stmt.initializer)));
        }

        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        return null;
    }

    @Override
    public Void visitForInStmt(Stmt.ForIn stmt) {
        output.append(String.format("for (const %s in %s) {\n", stmt.key.lexeme, stmt.iterable));
        executeBody(stmt.body);
        output.append("}\n\n");

        return null;
    }

    @Override
    public Void visitMatchStmt(Stmt.Match stmt) {
        return null;
    }

    @Override
    public Void visitMatchCaseStmt(Stmt.MatchCase stmt) {
        return null;
    }

    @Override
    public Void visitTraitStmt(Stmt.Trait stmt) {
        return null;
    }

    @Override
    public Void visitThrowStmt(Stmt.Throw stmt) {
        output.append(String.format("throw new %s;\n\n", stmt.thrown));
        return null;
    }

    @Override
    public Void visitEnumStmt(Stmt.Enum stmt) {
        output.append(String.format("const %s = Object.freeze({\n", stmt.name.lexeme));

        int idx = 0;
        for (Stmt.Enum.Variant variant : stmt.variants) {
            output.append(String.format("    %s: %d\n", variant.name().lexeme, idx));
            idx++;
        }

        output.append("});\n\n");

        return null;
    }

    @Override
    public Void visitTryCatchStmt(Stmt.TryCatch stmt) {
        return null;
    }

    @Override
    public Void visitNamespaceStmt(Stmt.Namespace stmt) {
        return null;
    }

    @Override
    public Void visitObjectDestructuringStmt(Stmt.ObjectDestructuring stmt) {
        return null;
    }

    @Override
    public Void visitArrayDestructuringStmt(Stmt.ArrayDestructuring stmt) {
        return null;
    }

    @Override
    public Void visitUsingStmt(Stmt.Using stmt) {
        return null;
    }

    @Override
    public Void visitForStmt(Stmt.For stmt) {
        Stmt.Var initializer = (Stmt.Var) stmt.initializer;
        output.append(String.format("for (let %s = %s; %s; %s) {\n", initializer.name, initializer.initializer, stmt.condition, stmt.increment));
        executeBody(stmt.body);
        output.append("}\n\n");

        return null;
    }

    @Override
    public String visitAssignExpr(Expr.Assign expr) {
        return String.format("%s = %s", expr.name, expr.value);
    }

    @Override
    public String visitBinaryExpr(Expr.Binary expr) {
        Object left = evaluate(expr.left);

        switch (expr.operator) {


            case null, default -> throw new RuntimeError(
                    expr.operator,
                    "TranspilerError",
                    "Unreachable",
                    "Please report this error on the GitHub repository!"
            );
        }
    }

    @Override
    public String visitCallExpr(Expr.Call expr) {
        StringBuilder result = new StringBuilder();
        Expr.Variable callee = (Expr.Variable) expr.callee;

        result.append(String.format("%s(", callee.name.lexeme));

        boolean isFirst = true;
        for (Expr argument : expr.arguments) {
            result.append(String.format("%s", evaluate(argument)));
            if (!isFirst) result.append(", ");
            isFirst = false;
        }

        return result.toString();
    }

    @Override
    public String visitGetExpr(Expr.Get expr) {
        return String.format("%s.%s", evaluate(expr.object), expr.name.lexeme);
    }

    @Override
    public String visitGroupingExpr(Expr.Grouping expr) {
        return null;
    }

    @Override
    public String visitLiteralExpr(Expr.Literal expr) {
        return stringify(expr.value);
    }

    @Override
    public String visitLogicalExpr(Expr.Logical expr) {
        return null;
    }

    @Override
    public String visitSetExpr(Expr.Set expr) {
        return null;
    }

    @Override
    public String visitSuperExpr(Expr.Super expr) {
        return "super";
    }

    @Override
    public String visitThisExpr(Expr.This expr) {
        return "this";
    }

    @Override
    public String visitUnaryExpr(Expr.Unary expr) {
        return null;
    }

    @Override
    public String visitVariableExpr(Expr.Variable expr) {
        return null;
    }

    @Override
    public String visitArrayExpr(Expr.Array expr) {
        return stringify(expr.elements);
    }

    @Override
    public String visitArraySubscriptGetExpr(Expr.SubscriptGet expr) {
        return null;
    }

    @Override
    public String visitArraySubscriptSetExpr(Expr.SubscriptSet expr) {
        return null;
    }

    @Override
    public String visitLambdaExpr(Expr.Lambda expr) {
        List<String> parametersList = new ArrayList<>();
        for (Token parameter : expr.params) {
            parametersList.add(parameter.lexeme);
        }

        int size = parametersList.toString().length();
        String parameters = parametersList.toString().substring(1).substring(0, size - 2);

        StringBuilder result = new StringBuilder();
        result.append(String.format("(%s) => {\n", parameters));
        executeBody(expr.body);
        result.append("})");

        return result.toString();
    }

    @Override
    public String visitDictionaryExpr(Expr.Dictionary expr) {
        return null;
    }

    @Override
    public String visitTypeofExpr(Expr.Typeof expr) {
        return null;
    }

    @Override
    public String visitTupleLiteralExpr(Expr.TupleLiteral expr) {
        return null;
    }

    @Override
    public String visitLazyExpr(Expr.Lazy expr) {
        return null;
    }

    @Override
    public String visitSpreadExpr(Expr.Spread expr) {
        return null;
    }

    @Override
    public String visitTernaryExpr(Expr.Ternary expr) {
        return null;
    }
}
