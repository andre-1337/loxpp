package com.andre1337.loxpp.interpreter;

import com.andre1337.loxpp.ast.Expr;
import com.andre1337.loxpp.ast.Stmt;

import java.util.*;

public class Optimizer implements Expr.Visitor<Expr>, Stmt.Visitor<Stmt> {
    private final Map<String, Stmt.Function> functionTable = new HashMap<>();
    private final Map<String, Expr> expressionCache = new HashMap<>();

    public List<Stmt> optimize(List<Stmt> stmts) {
        List<Stmt> optimized = new ArrayList<>();

        for (Stmt stmt : stmts) {
            Stmt optStmt = stmt.accept(this);

            if (optStmt != null) {
                optimized.add(optStmt);
            }
        }

        return optimized;
    }


    @Override
    public Expr visitAssignExpr(Expr.Assign expr) {
        return expr;
    }

    @Override
    public Expr visitBinaryExpr(Expr.Binary expr) {
        Expr left = expr.left.accept(this);
        Expr right = expr.right.accept(this);

        String key = left.toString() + expr.operator.type + right.toString();

        if (left instanceof Expr.Literal leftLit && right instanceof Expr.Literal rightLit) {
            Object leftValue = leftLit.value;
            Object rightValue = rightLit.value;

            if (expressionCache.containsKey(key)) {
                return expressionCache.get(key);
            }

            if (leftValue instanceof Double l && rightValue instanceof Double r) {
                switch (expr.operator.type) {
                    case PLUS: {
                        Expr optExpr = new Expr.Literal(l + r);
                        expressionCache.put(key, optExpr);
                        return optExpr;
                    }
                    case MINUS: {
                        Expr optExpr = new Expr.Literal(l - r);
                        expressionCache.put(key, optExpr);
                        return optExpr;
                    }
                    case STAR: {
                        Expr optExpr = new Expr.Literal(l * r);
                        expressionCache.put(key, optExpr);
                        return optExpr;
                    }
                    case SLASH: {
                        Expr optExpr = new Expr.Literal(r != 0 ? l / r : Double.NaN);
                        expressionCache.put(key, optExpr);
                        return optExpr;
                    }

                    default: {
                        expressionCache.put(key, expr);
                        return expr;
                    }
                }
            }
        }

        Expr result = new Expr.Binary(left, expr.operator, right);
        expressionCache.put(key, result);
        return result;
    }

    @Override
    public Expr visitCallExpr(Expr.Call expr) {
        Expr callee = expr.callee.accept(this);
        Expr instance = expr.instance != null ? expr.instance.accept(this) : null;
        List<Expr> args = new ArrayList<>();

        for (Expr arg : expr.arguments) {
            args.add(arg.accept(this));
        }

        return new Expr.Call(callee, expr.paren, args, instance);
    }

    @Override
    public Expr visitGetExpr(Expr.Get expr) {
        return expr;
    }

    @Override
    public Expr visitGroupingExpr(Expr.Grouping expr) {
        Expr opt = expr.expression.accept(this);

        if (opt instanceof Expr.Literal || opt instanceof Expr.Variable) {
            return opt;
        }

        return new Expr.Grouping(opt);
    }

    @Override
    public Expr visitLiteralExpr(Expr.Literal expr) {
        return expr;
    }

    @Override
    public Expr visitLogicalExpr(Expr.Logical expr) {
        return expr;
    }

    @Override
    public Expr visitSetExpr(Expr.Set expr) {
        return expr;
    }

    @Override
    public Expr visitSuperExpr(Expr.Super expr) {
        return expr;
    }

    @Override
    public Expr visitThisExpr(Expr.This expr) {
        return expr;
    }

    @Override
    public Expr visitUnaryExpr(Expr.Unary expr) {
        Expr right = expr.right.accept(this);

        if (right instanceof Expr.Literal lit) {
            switch (expr.operator.type) {
                case BANG:
                    if (lit.value instanceof Boolean bool) {
                        return new Expr.Literal(!bool);
                    }
                    break;
                case MINUS:
                    if (lit.value instanceof Double dbl) {
                        return new Expr.Literal(-dbl);
                    }
                    break;
            }
        }

        return new Expr.Unary(expr.operator, right);
    }

    @Override
    public Expr visitVariableExpr(Expr.Variable expr) {
        return expr;
    }

    @Override
    public Expr visitArrayExpr(Expr.Array expr) {
        return expr;
    }

    @Override
    public Expr visitArraySubscriptGetExpr(Expr.SubscriptGet expr) {
        return expr;
    }

    @Override
    public Expr visitArraySubscriptSetExpr(Expr.SubscriptSet expr) {
        return expr;
    }

    @Override
    public Expr visitLambdaExpr(Expr.Lambda expr) {
        return expr;
    }

    @Override
    public Expr visitDictionaryExpr(Expr.Dictionary expr) {
        return expr;
    }

    @Override
    public Expr visitTypeofExpr(Expr.Typeof expr) {
        return expr;
    }

    @Override
    public Expr visitTupleLiteralExpr(Expr.TupleLiteral expr) {
        return expr;
    }

    @Override
    public Expr visitLazyExpr(Expr.Lazy expr) {
        return expr;
    }

    @Override
    public Expr visitSpreadExpr(Expr.Spread expr) {
        return expr;
    }

    @Override
    public Expr visitTernaryExpr(Expr.Ternary expr) {
        Expr cond = expr.condition.accept(this);
        Expr thenExpr = expr.thenBranch.accept(this);
        Expr elseExpr = expr.elseBranch.accept(this);

        if (cond instanceof Expr.Literal lit) {
            if (lit.value instanceof Boolean bool) {
                return bool ? thenExpr : elseExpr;
            }
        }

        return new Expr.Ternary(cond, thenExpr, elseExpr);
    }

    @Override
    public Expr visitMatchExpr(Expr.Match expr) {
        return expr;
    }

    @Override
    public Expr visitWildcardPatternExpr(Expr.WildcardPattern expr) {
        return expr;
    }

    @Override
    public Expr visitUnionPatternExpr(Expr.UnionPattern expr) {
        return expr;
    }

    @Override
    public Expr visitListPatternExpr(Expr.ListPattern expr) {
        return expr;
    }

    @Override
    public Expr visitObjectPatternExpr(Expr.ObjectPattern expr) {
        return expr;
    }

    @Override
    public Expr visitAwaitExpr(Expr.Await expr) {
        return expr;
    }

    @Override
    public Expr visitNewExpr(Expr.New expr) {
        return null;
    }

    @Override
    public Stmt visitBlockStmt(Stmt.Block stmt) {
        List<Stmt> optimized = new ArrayList<>();

        for (Stmt statement : stmt.statements) {
            Stmt optStmt = statement.accept(this);

            if (optStmt != null) {
                optimized.add(optStmt);
            }
        }

        if (optimized.isEmpty()) {
            return null;
        }

        if (optimized.size() == 1 && !(optimized.getFirst() instanceof Stmt.Block)) {
            return optimized.getFirst();
        }

        return new Stmt.Block(optimized);
    }

    @Override
    public Stmt visitClassStmt(Stmt.Class stmt) {
        return stmt;
    }

    @Override
    public Stmt visitExpressionStmt(Stmt.Expression stmt) {
        Expr opt = stmt.expression.accept(this);

        if (opt instanceof Expr.Literal lit && lit.value == null) {
            return null;
        }

        if (opt instanceof Expr.Call call) {
            if (call.callee instanceof Expr.Variable var) {
                String fnName = var.name.lexeme;

                if (functionTable.containsKey(fnName)) {
                    return functionTable.get(fnName);
                }
            }
        }

        return new Stmt.Expression(opt);
    }

    @Override
    public Stmt visitFunctionStmt(Stmt.Function stmt) {
        functionTable.put(stmt.name.lexeme, stmt);
        return stmt;
    }

    @Override
    public Stmt visitIfStmt(Stmt.If stmt) {
        Expr cond = stmt.condition.accept(this);
        Stmt thenBranch = stmt.thenBranch.accept(this);
        Stmt elseBranch = stmt.elseBranch == null ? null : stmt.elseBranch.accept(this);

        if (cond instanceof Expr.Literal lit) {
            Object val = lit.value;

            if (val instanceof Boolean bool) {
                return bool ? thenBranch : elseBranch;
            }
        }

        return new Stmt.If(cond, thenBranch, elseBranch);
    }

    @Override
    public Stmt visitReturnStmt(Stmt.Return stmt) {
        return stmt;
    }

    @Override
    public Stmt visitVarStmt(Stmt.Var stmt) {
        Expr init = null;

        if (stmt.initializer != null) {
            if (stmt.initializer instanceof Expr.Binary bin) {
                init = bin.accept(this);
            } else {
                init = stmt.initializer;
            }
        }

        return new Stmt.Var(stmt.name, init);
    }

    @Override
    public Stmt visitWhileStmt(Stmt.While stmt) {
        Expr cond = stmt.condition.accept(this);
        Stmt body = stmt.body.accept(this);

        if (cond instanceof Expr.Literal lit && Boolean.FALSE.equals(lit.value)) {
            return null;
        }

        if (cond instanceof Expr.Literal lit && Boolean.TRUE.equals(lit.value)) {
            return new Stmt.Block(Arrays.asList(body, new Stmt.While(cond, body)));
        }

        return new Stmt.While(cond, body);
    }

    @Override
    public Stmt visitForInStmt(Stmt.ForIn stmt) {
        return stmt;
    }

    @Override
    public Stmt visitTraitStmt(Stmt.Trait stmt) {
        return stmt;
    }

    @Override
    public Stmt visitThrowStmt(Stmt.Throw stmt) {
        return stmt;
    }

    @Override
    public Stmt visitEnumStmt(Stmt.Enum stmt) {
        return stmt;
    }

    @Override
    public Stmt visitTryCatchStmt(Stmt.TryCatch stmt) {
        return stmt;
    }

    @Override
    public Stmt visitNamespaceStmt(Stmt.Namespace stmt) {
        return stmt;
    }

    @Override
    public Stmt visitObjectDestructuringStmt(Stmt.ObjectDestructuring stmt) {
        return stmt;
    }

    @Override
    public Stmt visitArrayDestructuringStmt(Stmt.ArrayDestructuring stmt) {
        return stmt;
    }

    @Override
    public Stmt visitUsingStmt(Stmt.Using stmt) {
        return stmt;
    }

    @Override
    public Stmt visitForStmt(Stmt.For stmt) {
        return stmt;
    }

    @Override
    public Stmt visitImplStmt(Stmt.Impl stmt) {
        return stmt;
    }

    @Override
    public Stmt visitExportStmt(Stmt.Export Stmt) {
        return null;
    }
}
