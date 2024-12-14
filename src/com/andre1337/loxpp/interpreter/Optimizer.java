package com.andre1337.loxpp.interpreter;

import com.andre1337.loxpp.ast.Expr;
import com.andre1337.loxpp.ast.Stmt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Optimizer implements Expr.Visitor<Expr>, Stmt.Visitor<Stmt> {
    private final Map<String, Stmt.Function> functionTable = new HashMap<>();

    public List<Stmt> optimize(List<Stmt> statements) {
        List<Stmt> optimized = new ArrayList<>();

        for (Stmt stmt : statements) {
            Stmt optimizedStmt = stmt.accept(this);
            if (optimizedStmt != null) {
                optimized.add(optimizedStmt);
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

        if (left instanceof Expr.Literal leftLit && right instanceof Expr.Literal rightLit) {
            Object leftValue = leftLit.value;
            Object rightValue = rightLit.value;

            if (leftValue instanceof Double && rightValue instanceof Double) {
                double l = (double) leftValue;
                double r = (double) rightValue;

                switch (expr.operator.type) {
                    case PLUS: return new Expr.Literal(l + r);
                    case MINUS: return new Expr.Literal(l - r);
                    case STAR: return new Expr.Literal(l * r);
                    case SLASH: return new Expr.Literal(r != 0 ? l / r : Double.NaN);
                }
            }
        }

        return new Expr.Binary(left, expr.operator, right);
    }

    @Override
    public Expr visitCallExpr(Expr.Call expr) {
        return expr;
    }

    @Override
    public Expr visitGetExpr(Expr.Get expr) {
        return expr;
    }

    @Override
    public Expr visitGroupingExpr(Expr.Grouping expr) {
        Expr optimized = expr.expression.accept(this);

        if (optimized instanceof Expr.Literal || optimized instanceof Expr.Variable) {
            return optimized;
        }

        return new Expr.Grouping(optimized);
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
        return expr;
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
        return expr;
    }

    @Override
    public Expr visitAwaitExpr(Expr.Await expr) {
        return expr;
    }

    @Override
    public Stmt visitBlockStmt(Stmt.Block stmt) {
        return stmt;
    }

    @Override
    public Stmt visitClassStmt(Stmt.Class stmt) {
        return stmt;
    }

    @Override
    public Stmt visitExpressionStmt(Stmt.Expression stmt) {
        Expr optimized = stmt.expression.accept(this);

        if (optimized instanceof Expr.Literal lit && lit.value == null) {
            return null;
        }

        return new Stmt.Expression(optimized);
    }

    @Override
    public Stmt visitFunctionStmt(Stmt.Function stmt) {
        functionTable.put(stmt.name.lexeme, stmt);
        return stmt;
    }

    @Override
    public Stmt visitIfStmt(Stmt.If stmt) {
        Expr condition = stmt.condition.accept(this);
        Stmt thenBranch = stmt.thenBranch.accept(this);
        Stmt elseBranch = stmt.elseBranch == null ? null : stmt.elseBranch.accept(this);

        if (condition instanceof Expr.Literal lit) {
            Object value = lit.value;
            if (value instanceof Boolean bool) {
                return bool ? thenBranch : elseBranch;
            }
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    @Override
    public Stmt visitReturnStmt(Stmt.Return stmt) {
        return stmt;
    }

    @Override
    public Stmt visitVarStmt(Stmt.Var stmt) {
        Expr initializer = null;
        if (stmt.initializer != null) {
            if (stmt.initializer instanceof Expr.Binary bin) {
                initializer = bin.accept(this);
            } else {
                initializer = stmt.initializer;
            }
        }

        return new Stmt.Var(stmt.name, initializer);
    }

    @Override
    public Stmt visitWhileStmt(Stmt.While stmt) {
        Expr condition = stmt.condition.accept(this);
        Stmt body = stmt.body.accept(this);

        if (condition instanceof Expr.Literal lit && Boolean.FALSE.equals(lit.value)) {
            return null;
        }

        return new Stmt.While(condition, body);
    }

    @Override
    public Stmt visitForInStmt(Stmt.ForIn stmt) {
        return stmt;
    }

    @Override
    public Stmt visitMatchStmt(Stmt.Match stmt) {
        return stmt;
    }

    @Override
    public Stmt visitMatchCaseStmt(Stmt.MatchCase stmt) {
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
}
