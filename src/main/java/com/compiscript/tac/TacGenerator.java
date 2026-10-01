package com.compiscript.tac;

import com.compiscript.parser.CompiscriptBaseVisitor;
import com.compiscript.parser.CompiscriptParser;
import com.compiscript.symbols.Scope;
import com.compiscript.symbols.ScopeKind;
import com.compiscript.symbols.Symbol;
import com.compiscript.symbols.SymbolKind;
import com.compiscript.symbols.SymbolTable;
import com.compiscript.types.CompiscriptType;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Second-pass visitor that emits three-address code from an already checked tree. */
public class TacGenerator extends CompiscriptBaseVisitor<TacOperand> {

    private final SymbolTable symbolTable;
    private final TacProgram program = TacProgram.create();
    private final TempPool pool = new TempPool();
    private final Deque<LoopLabels> loops = new ArrayDeque<>();
    private final Set<Scope> usedScopes = new HashSet<>();

    private Scope tacScope;
    private int labelId;
    private int functionDepth;
    private String currentClassName;

    public TacGenerator(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
        this.tacScope = symbolTable.globalScope();
    }

    public TacProgram generate(ParseTree tree) {
        if (tree != null) {
            visit(tree);
        }
        program.setMaxLiveTemps(pool.maxLive());
        return program;
    }

    @Override
    public TacOperand visitProgram(CompiscriptParser.ProgramContext ctx) {
        for (CompiscriptParser.StatementContext statement : ctx.statement()) {
            visit(statement);
        }
        return null;
    }

    @Override
    public TacOperand visitVariableDeclaration(CompiscriptParser.VariableDeclarationContext ctx) {
        Symbol symbol = lookup(ctx.Identifier().getText());
        emitAlloc(symbol);
        if (ctx.initializer() != null) {
            TacOperand value = visit(ctx.initializer().expression());
            emitCopy(ctx.Identifier().getText(), value);
        }
        return null;
    }

    @Override
    public TacOperand visitConstantDeclaration(CompiscriptParser.ConstantDeclarationContext ctx) {
        Symbol symbol = lookup(ctx.Identifier().getText());
        emitAlloc(symbol);
        TacOperand value = visit(ctx.expression());
        emitCopy(ctx.Identifier().getText(), value);
        return null;
    }

    @Override
    public TacOperand visitAssignment(CompiscriptParser.AssignmentContext ctx) {
        if (ctx.expression().size() == 1) {
            TacOperand value = visit(ctx.expression(0));
            emitCopy(ctx.Identifier().getText(), value);
            return TacOperand.named(ctx.Identifier().getText(), value == null ? CompiscriptType.error() : value.type());
        }
        TacOperand object = visit(ctx.expression(0));
        TacOperand value = visit(ctx.expression(1));
        program.add(text(object) + "." + ctx.Identifier().getText() + " = " + text(value));
        release(object);
        release(value);
        return value;
    }

    @Override
    public TacOperand visitFunctionDeclaration(CompiscriptParser.FunctionDeclarationContext ctx) {
        Symbol function = lookup(ctx.Identifier().getText());
        if (function == null) {
            function = new Symbol(ctx.Identifier().getText(), SymbolKind.FUNCTION, CompiscriptType.voidType(), 0, false);
            function.setLabel("fn_" + ctx.Identifier().getText());
        }
        boolean pushed = pushScope(ScopeKind.FUNCTION, ctx.Identifier().getText());
        emitFunctionBody(function, ctx.block(), functionDepth > 0);
        if (pushed) {
            popScope();
        }
        return null;
    }

    @Override
    public TacOperand visitClassDeclaration(CompiscriptParser.ClassDeclarationContext ctx) {
        String className = ctx.Identifier(0).getText();
        Symbol classSymbol = lookup(className);
        if (classSymbol != null) {
            for (Symbol member : classSymbol.members().values()) {
                if (member.storage() != null && member.storage().name().equals("FIELD")) {
                    program.add("field " + className + "." + member.name() + " " + member.address());
                }
            }
        }
        boolean pushed = pushScope(ScopeKind.CLASS, className);
        String previousClass = currentClassName;
        currentClassName = className;
        for (CompiscriptParser.ClassMemberContext member : ctx.classMember()) {
            if (member.functionDeclaration() != null) {
                emitMethod(classSymbol, member.functionDeclaration());
            }
        }
        currentClassName = previousClass;
        if (pushed) {
            popScope();
        }
        return null;
    }

    @Override
    public TacOperand visitBlock(CompiscriptParser.BlockContext ctx) {
        boolean pushed = pushScope(ScopeKind.BLOCK, "block");
        for (CompiscriptParser.StatementContext statement : ctx.statement()) {
            visit(statement);
        }
        if (pushed) {
            popScope();
        }
        return null;
    }

    @Override
    public TacOperand visitIfStatement(CompiscriptParser.IfStatementContext ctx) {
        TacOperand condition = visit(ctx.expression());
        String elseLabel = freshLabel("Lelse");
        String endLabel = freshLabel("Lend");
        program.add("ifFalse " + text(condition) + " goto " + (ctx.block().size() > 1 ? elseLabel : endLabel));
        release(condition);
        visit(ctx.block(0));
        if (ctx.block().size() > 1) {
            program.add("goto " + endLabel);
            program.add(elseLabel + ":");
            visit(ctx.block(1));
        }
        program.add(endLabel + ":");
        return null;
    }

    @Override
    public TacOperand visitWhileStatement(CompiscriptParser.WhileStatementContext ctx) {
        String condLabel = freshLabel("Lcond");
        String endLabel = freshLabel("Lend");
        program.add(condLabel + ":");
        TacOperand condition = visit(ctx.expression());
        program.add("ifFalse " + text(condition) + " goto " + endLabel);
        release(condition);
        loops.push(new LoopLabels(endLabel, condLabel));
        visit(ctx.block());
        loops.pop();
        program.add("goto " + condLabel);
        program.add(endLabel + ":");
        return null;
    }

    @Override
    public TacOperand visitDoWhileStatement(CompiscriptParser.DoWhileStatementContext ctx) {
        String bodyLabel = freshLabel("Lbody");
        String condLabel = freshLabel("Lcond");
        String endLabel = freshLabel("Lend");
        program.add(bodyLabel + ":");
        loops.push(new LoopLabels(endLabel, condLabel));
        visit(ctx.block());
        loops.pop();
        program.add(condLabel + ":");
        TacOperand condition = visit(ctx.expression());
        program.add("ifFalse " + text(condition) + " goto " + endLabel);
        release(condition);
        program.add("goto " + bodyLabel);
        program.add(endLabel + ":");
        return null;
    }

    @Override
    public TacOperand visitForStatement(CompiscriptParser.ForStatementContext ctx) {
        boolean pushed = pushScope(ScopeKind.LOOP, "for");
        if (ctx.variableDeclaration() != null) {
            visit(ctx.variableDeclaration());
        } else if (ctx.assignment() != null) {
            visit(ctx.assignment());
        }
        String condLabel = freshLabel("Lcond");
        String updateLabel = freshLabel("Lupdate");
        String endLabel = freshLabel("Lend");
        program.add(condLabel + ":");
        if (!ctx.expression().isEmpty()) {
            TacOperand condition = visit(ctx.expression(0));
            program.add("ifFalse " + text(condition) + " goto " + endLabel);
            release(condition);
        }
        loops.push(new LoopLabels(endLabel, updateLabel));
        visit(ctx.block());
        loops.pop();
        program.add(updateLabel + ":");
        if (ctx.expression().size() > 1) {
            release(visit(ctx.expression(1)));
        }
        program.add("goto " + condLabel);
        program.add(endLabel + ":");
        if (pushed) {
            popScope();
        }
        return null;
    }

    @Override
    public TacOperand visitForeachStatement(CompiscriptParser.ForeachStatementContext ctx) {
        boolean pushed = pushScope(ScopeKind.LOOP, "foreach");
        TacOperand collection = visit(ctx.expression());
        String item = ctx.Identifier().getText();
        emitAlloc(lookup(item));
        String index = pool.acquire();
        String length = pool.acquire();
        program.add(length + " = length " + text(collection));
        program.add(index + " = 0");
        String condLabel = freshLabel("Lcond");
        String updateLabel = freshLabel("Lupdate");
        String endLabel = freshLabel("Lend");
        program.add(condLabel + ":");
        String compare = pool.acquire();
        program.add(compare + " = " + index + " < " + length);
        program.add("ifFalse " + compare + " goto " + endLabel);
        pool.release(compare);
        program.add(item + " = " + text(collection) + "[" + index + "]");
        loops.push(new LoopLabels(endLabel, updateLabel));
        visit(ctx.block());
        loops.pop();
        program.add(updateLabel + ":");
        String next = pool.acquire();
        program.add(next + " = " + index + " + 1");
        program.add(index + " = " + next);
        pool.release(next);
        program.add("goto " + condLabel);
        program.add(endLabel + ":");
        pool.release(index);
        pool.release(length);
        release(collection);
        if (pushed) {
            popScope();
        }
        return null;
    }

    @Override
    public TacOperand visitTryCatchStatement(CompiscriptParser.TryCatchStatementContext ctx) {
        String handler = freshLabel("Lcatch");
        String after = freshLabel("Lafter");
        program.add("begin_try " + handler);
        visit(ctx.block(0));
        program.add("end_try");
        program.add("goto " + after);
        program.add(handler + ":");
        boolean pushed = pushScope(ScopeKind.CATCH, "catch");
        String errorName = ctx.Identifier().getText();
        emitAlloc(lookup(errorName));
        program.add(errorName + " = caught");
        visit(ctx.block(1));
        if (pushed) {
            popScope();
        }
        program.add(after + ":");
        return null;
    }

    @Override
    public TacOperand visitSwitchStatement(CompiscriptParser.SwitchStatementContext ctx) {
        TacOperand selector = visit(ctx.expression());
        String stableName = pool.acquire();
        program.add(stableName + " = " + text(selector));
        release(selector);
        TacOperand stable = TacOperand.named(stableName, selector == null ? CompiscriptType.error() : selector.type());
        String endLabel = freshLabel("Lend");
        for (CompiscriptParser.SwitchCaseContext switchCase : ctx.switchCase()) {
            String next = freshLabel("Lnext");
            TacOperand caseValue = visit(switchCase.expression());
            TacOperand compared = emitBinary(stable, "==", caseValue, CompiscriptType.booleanType());
            program.add("ifFalse " + compared.text() + " goto " + next);
            release(compared);
            for (CompiscriptParser.StatementContext statement : switchCase.statement()) {
                visit(statement);
            }
            program.add("goto " + endLabel);
            program.add(next + ":");
        }
        if (ctx.defaultCase() != null) {
            for (CompiscriptParser.StatementContext statement : ctx.defaultCase().statement()) {
                visit(statement);
            }
        }
        pool.release(stableName);
        program.add(endLabel + ":");
        return null;
    }

    @Override
    public TacOperand visitBreakStatement(CompiscriptParser.BreakStatementContext ctx) {
        if (!loops.isEmpty()) {
            program.add("goto " + loops.peek().breakLabel);
        }
        return null;
    }

    @Override
    public TacOperand visitContinueStatement(CompiscriptParser.ContinueStatementContext ctx) {
        if (!loops.isEmpty()) {
            program.add("goto " + loops.peek().continueLabel);
        }
        return null;
    }

    @Override
    public TacOperand visitReturnStatement(CompiscriptParser.ReturnStatementContext ctx) {
        if (ctx.expression() == null) {
            program.add("return");
            return null;
        }
        TacOperand value = visit(ctx.expression());
        program.add("return " + text(value));
        release(value);
        return null;
    }

    @Override
    public TacOperand visitExpressionStatement(CompiscriptParser.ExpressionStatementContext ctx) {
        release(visit(ctx.expression()));
        return null;
    }

    @Override
    public TacOperand visitPrintStatement(CompiscriptParser.PrintStatementContext ctx) {
        TacOperand value = visit(ctx.expression());
        program.add("param " + text(value));
        release(value);
        program.add("call print, 1");
        return null;
    }

    @Override
    public TacOperand visitExprNoAssign(CompiscriptParser.ExprNoAssignContext ctx) {
        return visit(ctx.conditionalExpr());
    }

    @Override
    public TacOperand visitAssignExpr(CompiscriptParser.AssignExprContext ctx) {
        TacOperand value = visit(ctx.assignmentExpr());
        emitStore(ctx.leftHandSide(), value);
        return value;
    }

    @Override
    public TacOperand visitPropertyAssignExpr(CompiscriptParser.PropertyAssignExprContext ctx) {
        TacOperand object = emitLeftHandSide(ctx.leftHandSide());
        TacOperand value = visit(ctx.assignmentExpr());
        program.add(text(object) + "." + ctx.Identifier().getText() + " = " + text(value));
        release(object);
        release(value);
        return value;
    }

    @Override
    public TacOperand visitTernaryExpr(CompiscriptParser.TernaryExprContext ctx) {
        if (ctx.logicalOrExpr() != null && ctx.expression().isEmpty()) {
            return visit(ctx.logicalOrExpr());
        }
        TacOperand condition = visit(ctx.logicalOrExpr());
        String elseLabel = freshLabel("Lelse");
        String endLabel = freshLabel("Lend");
        String result = pool.acquire();
        program.add("ifFalse " + text(condition) + " goto " + elseLabel);
        release(condition);
        TacOperand whenTrue = visit(ctx.expression(0));
        program.add(result + " = " + text(whenTrue));
        release(whenTrue);
        program.add("goto " + endLabel);
        program.add(elseLabel + ":");
        TacOperand whenFalse = visit(ctx.expression(1));
        program.add(result + " = " + text(whenFalse));
        release(whenFalse);
        program.add(endLabel + ":");
        CompiscriptType type = whenTrue == null ? CompiscriptType.error() : whenTrue.type();
        return TacOperand.temp(result, type);
    }

    @Override
    public TacOperand visitLogicalOrExpr(CompiscriptParser.LogicalOrExprContext ctx) {
        if (ctx.logicalAndExpr().size() == 1) {
            return visit(ctx.logicalAndExpr(0));
        }
        String result = pool.acquire();
        String end = freshLabel("Lend");
        String fail = freshLabel("Lfalse");
        for (int i = 0; i < ctx.logicalAndExpr().size(); i++) {
            TacOperand operand = visit(ctx.logicalAndExpr(i));
            boolean last = i + 1 == ctx.logicalAndExpr().size();
            String next = last ? fail : freshLabel("Lor");
            program.add("ifFalse " + text(operand) + " goto " + next);
            release(operand);
            program.add(result + " = 1");
            program.add("goto " + end);
            program.add(next + ":");
        }
        program.add(result + " = 0");
        program.add(end + ":");
        return TacOperand.temp(result, CompiscriptType.booleanType());
    }

    @Override
    public TacOperand visitLogicalAndExpr(CompiscriptParser.LogicalAndExprContext ctx) {
        if (ctx.equalityExpr().size() == 1) {
            return visit(ctx.equalityExpr(0));
        }
        String result = pool.acquire();
        String end = freshLabel("Lend");
        String fail = freshLabel("Lfalse");
        for (CompiscriptParser.EqualityExprContext operandCtx : ctx.equalityExpr()) {
            TacOperand operand = visit(operandCtx);
            program.add("ifFalse " + text(operand) + " goto " + fail);
            release(operand);
        }
        program.add(result + " = 1");
        program.add("goto " + end);
        program.add(fail + ":");
        program.add(result + " = 0");
        program.add(end + ":");
        return TacOperand.temp(result, CompiscriptType.booleanType());
    }

    @Override
    public TacOperand visitEqualityExpr(CompiscriptParser.EqualityExprContext ctx) {
        TacOperand left = visit(ctx.relationalExpr(0));
        if (ctx.relationalExpr().size() == 1) {
            return left;
        }
        for (int i = 1; i < ctx.relationalExpr().size(); i++) {
            String operator = ctx.getChild(2 * i - 1).getText();
            TacOperand right = visit(ctx.relationalExpr(i));
            left = emitBinary(left, operator, right, CompiscriptType.booleanType());
        }
        return left;
    }

    @Override
    public TacOperand visitRelationalExpr(CompiscriptParser.RelationalExprContext ctx) {
        TacOperand left = visit(ctx.additiveExpr(0));
        if (ctx.additiveExpr().size() == 1) {
            return left;
        }
        for (int i = 1; i < ctx.additiveExpr().size(); i++) {
            String operator = ctx.getChild(2 * i - 1).getText();
            TacOperand right = visit(ctx.additiveExpr(i));
            TacOperand[] promoted = promotePair(left, right);
            left = emitBinary(promoted[0], operator, promoted[1], CompiscriptType.booleanType());
        }
        return left;
    }

    @Override
    public TacOperand visitAdditiveExpr(CompiscriptParser.AdditiveExprContext ctx) {
        TacOperand left = visit(ctx.multiplicativeExpr(0));
        for (int i = 1; i < ctx.multiplicativeExpr().size(); i++) {
            String operator = ctx.getChild(2 * i - 1).getText();
            TacOperand right = visit(ctx.multiplicativeExpr(i));
            if ("+".equals(operator) && (isString(left) || isString(right))) {
                left = emitBinary(left, "concat", right, CompiscriptType.string());
            } else {
                TacOperand[] promoted = promotePair(left, right);
                CompiscriptType type = numericResult(promoted[0], promoted[1]);
                left = emitBinary(promoted[0], operator, promoted[1], type);
            }
        }
        return left;
    }

    @Override
    public TacOperand visitMultiplicativeExpr(CompiscriptParser.MultiplicativeExprContext ctx) {
        TacOperand left = visit(ctx.unaryExpr(0));
        for (int i = 1; i < ctx.unaryExpr().size(); i++) {
            String operator = ctx.getChild(2 * i - 1).getText();
            TacOperand right = visit(ctx.unaryExpr(i));
            TacOperand[] promoted = promotePair(left, right);
            left = emitBinary(promoted[0], operator, promoted[1], numericResult(promoted[0], promoted[1]));
        }
        return left;
    }

    @Override
    public TacOperand visitUnaryExpr(CompiscriptParser.UnaryExprContext ctx) {
        if (ctx.getChildCount() == 2) {
            TacOperand operand = visit(ctx.unaryExpr());
            String operator = ctx.getChild(0).getText();
            if ("!".equals(operator)) {
                return emitUnary("not", operand, CompiscriptType.booleanType());
            }
            CompiscriptType type = operand != null && operand.type().kind() == CompiscriptType.Kind.FLOAT
                    ? CompiscriptType.floatType()
                    : CompiscriptType.integer();
            return emitUnary("-", operand, type);
        }
        return visit(ctx.primaryExpr());
    }

    @Override
    public TacOperand visitPrimaryExpr(CompiscriptParser.PrimaryExprContext ctx) {
        if (ctx.expression() != null) {
            return visit(ctx.expression());
        }
        if (ctx.literalExpr() != null) {
            return visit(ctx.literalExpr());
        }
        if (ctx.leftHandSide() != null) {
            return emitLeftHandSide(ctx.leftHandSide());
        }
        return TacOperand.literal("0", CompiscriptType.error());
    }

    @Override
    public TacOperand visitLiteralExpr(CompiscriptParser.LiteralExprContext ctx) {
        if (ctx.Literal() != null) {
            String literal = ctx.Literal().getText();
            if (literal.startsWith("\"")) {
                return TacOperand.literal(literal, CompiscriptType.string());
            }
            if (literal.contains(".")) {
                return TacOperand.literal(literal, CompiscriptType.floatType());
            }
            return TacOperand.literal(literal, CompiscriptType.integer());
        }
        if (ctx.arrayLiteral() != null) {
            return visit(ctx.arrayLiteral());
        }
        if ("true".equals(ctx.getText())) {
            return TacOperand.literal("1", CompiscriptType.booleanType());
        }
        if ("false".equals(ctx.getText())) {
            return TacOperand.literal("0", CompiscriptType.booleanType());
        }
        if ("null".equals(ctx.getText())) {
            return TacOperand.literal("null", CompiscriptType.nullType());
        }
        return TacOperand.literal(ctx.getText(), CompiscriptType.error());
    }

    @Override
    public TacOperand visitArrayLiteral(CompiscriptParser.ArrayLiteralContext ctx) {
        String array = pool.acquire();
        program.add(array + " = newarray " + ctx.expression().size());
        CompiscriptType elementType = CompiscriptType.error();
        for (int i = 0; i < ctx.expression().size(); i++) {
            TacOperand element = visit(ctx.expression(i));
            if (i == 0 && element != null) {
                elementType = element.type();
            }
            program.add(array + "[" + i + "] = " + text(element));
            release(element);
        }
        return TacOperand.temp(array, CompiscriptType.array(elementType));
    }

    private void emitMethod(Symbol classSymbol, CompiscriptParser.FunctionDeclarationContext ctx) {
        String methodName = ctx.Identifier().getText();
        Symbol method = classSymbol == null ? null : classSymbol.members().get(methodName);
        if (method == null && classSymbol != null) {
            method = symbolTable.resolveInClassHierarchy(classSymbol, methodName).orElse(null);
        }
        if (method == null) {
            method = new Symbol(methodName, SymbolKind.FUNCTION, CompiscriptType.voidType(), 0, false);
            method.setLabel((currentClassName == null ? "Class" : currentClassName) + "_" + methodName);
            method.setNeedsReceiver(true);
        }
        boolean pushed = pushScope(ScopeKind.FUNCTION, methodName);
        emitFunctionBody(method, ctx.block(), false);
        if (pushed) {
            popScope();
        }
    }

    private void emitFunctionBody(Symbol function, CompiscriptParser.BlockContext block, boolean nested) {
        String skip = null;
        if (nested) {
            skip = freshLabel("Lskip");
            program.add("goto " + skip);
        }
        functionDepth++;
        program.add(function.label() + ":");
        if (function.activationRecord() != null) {
            program.add("enter " + function.label() + " " + function.activationRecord().size());
        } else {
            program.add("enter " + function.label() + " 0");
        }
        if (function.needsStaticLink()) {
            program.add("// static_link");
        }
        visit(block);
        if (program.lines().isEmpty() || !program.lines().get(program.lines().size() - 1).startsWith("return")) {
            program.add("return");
        }
        functionDepth--;
        if (skip != null) {
            program.add(skip + ":");
        }
    }

    private TacOperand emitLeftHandSide(CompiscriptParser.LeftHandSideContext ctx) {
        Atom atom = emitAtom(ctx.primaryAtom());
        TacOperand current = atom.operand;
        Symbol pendingFunction = atom.function;
        TacOperand receiver = atom.receiver;
        for (CompiscriptParser.SuffixOpContext suffix : ctx.suffixOp()) {
            if (suffix instanceof CompiscriptParser.CallExprContext call) {
                current = emitCall(pendingFunction, receiver, call, current);
                pendingFunction = null;
                receiver = null;
            } else if (suffix instanceof CompiscriptParser.IndexExprContext index) {
                TacOperand indexOperand = visit(index.expression());
                String result = pool.acquire();
                program.add(result + " = " + text(current) + "[" + text(indexOperand) + "]");
                release(current);
                release(indexOperand);
                CompiscriptType element = current != null && current.type().kind() == CompiscriptType.Kind.ARRAY
                        ? current.type().elementType()
                        : CompiscriptType.error();
                current = TacOperand.temp(result, element);
                pendingFunction = null;
                receiver = null;
            } else if (suffix instanceof CompiscriptParser.PropertyAccessExprContext property) {
                String propertyName = property.Identifier().getText();
                Symbol member = resolveMember(current, propertyName);
                if (member != null && member.kind() == SymbolKind.FUNCTION) {
                    receiver = current;
                    pendingFunction = member;
                    current = TacOperand.named(member.label(), member.type());
                } else {
                    String result = pool.acquire();
                    program.add(result + " = " + text(current) + "." + propertyName);
                    CompiscriptType type = member == null ? CompiscriptType.error() : member.type();
                    release(current);
                    current = TacOperand.temp(result, type);
                    pendingFunction = null;
                    receiver = null;
                }
            }
        }
        return current;
    }

    private void emitStore(CompiscriptParser.LeftHandSideContext ctx, TacOperand value) {
        if (ctx.suffixOp().isEmpty() && ctx.primaryAtom() instanceof CompiscriptParser.IdentifierExprContext identifier) {
            emitCopy(identifier.Identifier().getText(), value);
            return;
        }
        List<CompiscriptParser.SuffixOpContext> suffixes = ctx.suffixOp();
        CompiscriptParser.SuffixOpContext last = suffixes.get(suffixes.size() - 1);
        if (last instanceof CompiscriptParser.IndexExprContext index) {
            CompiscriptParser.LeftHandSideContext target = ctx;
            TacOperand base = emitAtom(target.primaryAtom()).operand;
            for (int i = 0; i < suffixes.size() - 1; i++) {
                base = applyLoadSuffix(base, suffixes.get(i));
            }
            TacOperand indexOperand = visit(index.expression());
            program.add(text(base) + "[" + text(indexOperand) + "] = " + text(value));
            release(base);
            release(indexOperand);
            release(value);
            return;
        }
        emitCopy(text(emitLeftHandSide(ctx)), value);
    }

    private TacOperand applyLoadSuffix(TacOperand current, CompiscriptParser.SuffixOpContext suffix) {
        if (suffix instanceof CompiscriptParser.IndexExprContext index) {
            TacOperand indexOperand = visit(index.expression());
            String result = pool.acquire();
            program.add(result + " = " + text(current) + "[" + text(indexOperand) + "]");
            release(current);
            release(indexOperand);
            return TacOperand.temp(result, CompiscriptType.error());
        }
        if (suffix instanceof CompiscriptParser.PropertyAccessExprContext property) {
            String result = pool.acquire();
            program.add(result + " = " + text(current) + "." + property.Identifier().getText());
            release(current);
            return TacOperand.temp(result, CompiscriptType.error());
        }
        return current;
    }

    private Atom emitAtom(CompiscriptParser.PrimaryAtomContext atom) {
        if (atom instanceof CompiscriptParser.IdentifierExprContext identifier) {
            String name = identifier.Identifier().getText();
            Symbol symbol = lookup(name);
            CompiscriptType type = symbol == null ? CompiscriptType.error() : symbol.type();
            TacOperand operand = TacOperand.named(name, type);
            if (symbol != null && symbol.kind() == SymbolKind.FUNCTION) {
                return new Atom(operand, symbol, null);
            }
            return new Atom(operand, null, null);
        }
        if (atom instanceof CompiscriptParser.NewExprContext newExpr) {
            return new Atom(emitNew(newExpr), null, null);
        }
        if (atom instanceof CompiscriptParser.ThisExprContext) {
            CompiscriptType type = currentClassName == null
                    ? CompiscriptType.error()
                    : CompiscriptType.classType(currentClassName);
            return new Atom(TacOperand.named("this", type), null, null);
        }
        return new Atom(TacOperand.literal("0", CompiscriptType.error()), null, null);
    }

    private TacOperand emitNew(CompiscriptParser.NewExprContext ctx) {
        String className = ctx.Identifier().getText();
        String result = pool.acquire();
        program.add(result + " = new " + className);
        Symbol classSymbol = symbolTable.resolveClass(className).orElse(null);
        Symbol constructor = classSymbol == null
                ? null
                : symbolTable.resolveInClassHierarchy(classSymbol, "constructor").orElse(null);
        if (constructor != null) {
            int argc = 1;
            program.add("param " + result);
            if (ctx.arguments() != null) {
                for (CompiscriptParser.ExpressionContext argument : ctx.arguments().expression()) {
                    TacOperand value = visit(argument);
                    program.add("param " + text(value));
                    release(value);
                    argc++;
                }
            }
            program.add("call " + constructor.label() + ", " + argc);
        }
        return TacOperand.temp(result, CompiscriptType.classType(className));
    }

    private TacOperand emitCall(Symbol function, TacOperand receiver, CompiscriptParser.CallExprContext ctx,
                                TacOperand current) {
        int argc = 0;
        if (function != null && function.needsStaticLink()) {
            program.add("param static_link");
            argc++;
        }
        if (receiver != null) {
            program.add("param " + text(receiver));
            if (receiver.temporary()) {
                pool.release(receiver.text());
            }
            argc++;
        } else if (function != null && function.needsReceiver()) {
            program.add("param this");
            argc++;
        }
        if (ctx.arguments() != null) {
            for (CompiscriptParser.ExpressionContext argument : ctx.arguments().expression()) {
                TacOperand value = visit(argument);
                program.add("param " + text(value));
                release(value);
                argc++;
            }
        }
        String label = function != null && function.label() != null ? function.label() : text(current);
        String result = pool.acquire();
        CompiscriptType returnType = function != null && function.type().kind() == CompiscriptType.Kind.FUNCTION
                ? function.type().returnType()
                : CompiscriptType.error();
        program.add(result + " = call " + label + ", " + argc);
        return TacOperand.temp(result, returnType);
    }

    private Symbol resolveMember(TacOperand object, String propertyName) {
        if (object == null || object.type().kind() != CompiscriptType.Kind.CLASS) {
            if (currentClassName != null && "this".equals(object == null ? "" : object.text())) {
                Symbol current = symbolTable.resolveClass(currentClassName).orElse(null);
                return current == null ? null : symbolTable.resolveInClassHierarchy(current, propertyName).orElse(null);
            }
            return null;
        }
        Symbol classSymbol = symbolTable.resolveClass(object.type().className()).orElse(null);
        if (classSymbol == null) {
            return null;
        }
        return symbolTable.resolveInClassHierarchy(classSymbol, propertyName).orElse(null);
    }

    private TacOperand emitBinary(TacOperand left, String operator, TacOperand right, CompiscriptType type) {
        String leftText = text(left);
        String rightText = text(right);
        release(left);
        release(right);
        String result = pool.acquire();
        program.add(result + " = " + leftText + " " + operator + " " + rightText);
        return TacOperand.temp(result, type);
    }

    private TacOperand emitUnary(String operator, TacOperand operand, CompiscriptType type) {
        String source = text(operand);
        release(operand);
        String result = pool.acquire();
        if ("-".equals(operator)) {
            program.add(result + " = - " + source);
        } else {
            program.add(result + " = not " + source);
        }
        return TacOperand.temp(result, type);
    }

    private TacOperand[] promotePair(TacOperand left, TacOperand right) {
        boolean leftFloat = isFloat(left);
        boolean rightFloat = isFloat(right);
        if (leftFloat && !rightFloat && isNumeric(right)) {
            right = emitUnaryCast(right);
        } else if (rightFloat && !leftFloat && isNumeric(left)) {
            left = emitUnaryCast(left);
        }
        return new TacOperand[] {left, right};
    }

    private TacOperand emitUnaryCast(TacOperand operand) {
        String source = text(operand);
        release(operand);
        String result = pool.acquire();
        program.add(result + " = cast.i2f " + source);
        return TacOperand.temp(result, CompiscriptType.floatType());
    }

    private void emitCopy(String target, TacOperand value) {
        program.add(target + " = " + text(value));
        release(value);
    }

    private void emitAlloc(Symbol symbol) {
        if (symbol == null || symbol.storage() == null) {
            return;
        }
        if (symbol.storage() == com.compiscript.symbols.StorageClass.PARAMETER
                || symbol.storage() == com.compiscript.symbols.StorageClass.FRAME) {
            return;
        }
        program.add("alloc " + symbol.name() + " " + symbol.storage().name().toLowerCase() + " " + symbol.address());
    }

    private Symbol lookup(String name) {
        Scope scope = tacScope;
        while (scope != null) {
            Symbol symbol = scope.symbols().get(name);
            if (symbol != null) {
                return symbol;
            }
            scope = scope.parent();
        }
        return null;
    }

    private boolean pushScope(ScopeKind kind, String name) {
        for (Scope scope : symbolTable.allScopes()) {
            if (!usedScopes.contains(scope) && scope.parent() == tacScope && scope.kind() == kind && name.equals(scope.name())) {
                usedScopes.add(scope);
                tacScope = scope;
                return true;
            }
        }
        return false;
    }

    private void popScope() {
        if (tacScope != null && tacScope.parent() != null) {
            tacScope = tacScope.parent();
        }
    }

    private void release(TacOperand operand) {
        if (operand != null && operand.temporary()) {
            pool.release(operand.text());
        }
    }

    private String text(TacOperand operand) {
        return operand == null ? "0" : operand.text();
    }

    private String freshLabel(String prefix) {
        return prefix + "_" + (labelId++);
    }

    private boolean isString(TacOperand operand) {
        return operand != null && operand.type().kind() == CompiscriptType.Kind.STRING;
    }

    private boolean isFloat(TacOperand operand) {
        return operand != null && operand.type().kind() == CompiscriptType.Kind.FLOAT;
    }

    private boolean isNumeric(TacOperand operand) {
        return operand != null && operand.type().isNumeric();
    }

    private CompiscriptType numericResult(TacOperand left, TacOperand right) {
        if (isFloat(left) || isFloat(right)) {
            return CompiscriptType.floatType();
        }
        return CompiscriptType.integer();
    }

    private record LoopLabels(String breakLabel, String continueLabel) {
    }

    private record Atom(TacOperand operand, Symbol function, TacOperand receiver) {
    }
}
