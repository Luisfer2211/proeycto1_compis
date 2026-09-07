package com.compiscript.semantic;

import com.compiscript.errors.SemanticErrorCollector;
import com.compiscript.parser.CompiscriptBaseVisitor;
import com.compiscript.parser.CompiscriptParser;
import com.compiscript.symbols.Scope;
import com.compiscript.symbols.ScopeKind;
import com.compiscript.symbols.Symbol;
import com.compiscript.symbols.SymbolKind;
import com.compiscript.symbols.SymbolTable;
import com.compiscript.types.CompiscriptType;
import com.compiscript.types.TypeResolver;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

/** Semantic analyzer implemented as an ANTLR visitor over the parse tree. */
public class SemanticVisitor extends CompiscriptBaseVisitor<CompiscriptType> {

    private final SymbolTable symbolTable;
    private final SemanticErrorCollector errors;

    private int loopDepth;
    private int functionDepth;
    private String currentClassName;
    private CompiscriptType currentReturnType = CompiscriptType.voidType();
    private boolean unreachable;

    public SemanticVisitor(SymbolTable symbolTable, SemanticErrorCollector errors) {
        this.symbolTable = symbolTable;
        this.errors = errors;
    }

    public SymbolTable symbolTable() {
        return symbolTable;
    }

    @Override
    public CompiscriptType visitProgram(CompiscriptParser.ProgramContext ctx) {
        for (CompiscriptParser.StatementContext statement : ctx.statement()) {
            visit(statement);
        }
        return CompiscriptType.voidType();
    }

    @Override
    public CompiscriptType visitVariableDeclaration(CompiscriptParser.VariableDeclarationContext ctx) {
        checkUnreachable(ctx);
        String name = ctx.Identifier().getText();
        int line = line(ctx);
        int column = column(ctx.Identifier());

        CompiscriptType declaredType = ctx.typeAnnotation() != null
                ? TypeResolver.resolve(ctx.typeAnnotation().type())
                : null;

        CompiscriptType initializerType = ctx.initializer() != null
                ? visit(ctx.initializer().expression())
                : CompiscriptType.error();

        if (declaredType == null) {
            if (ctx.initializer() == null) {
                report(line, column, name, "Variable '" + name + "' requires a type or initializer.");
                declaredType = CompiscriptType.error();
            } else {
                declaredType = initializerType;
            }
        } else if (ctx.initializer() != null && !declaredType.isAssignableFrom(initializerType)) {
            report(line, column, name,
                    "Cannot assign value of type '" + initializerType + "' to variable of type '" + declaredType + "'.");
        }

        Symbol symbol = new Symbol(name, SymbolKind.VARIABLE, declaredType, line, true);
        if (ctx.initializer() != null) {
            symbol.markInitialized();
        }
        if (!symbolTable.define(symbol)) {
            report(line, column, name, "Redeclaration of identifier '" + name + "' in the same scope.");
        }
        return declaredType;
    }

    @Override
    public CompiscriptType visitConstantDeclaration(CompiscriptParser.ConstantDeclarationContext ctx) {
        checkUnreachable(ctx);
        String name = ctx.Identifier().getText();
        int line = line(ctx);
        int column = column(ctx.Identifier());

        CompiscriptType declaredType = ctx.typeAnnotation() != null
                ? TypeResolver.resolve(ctx.typeAnnotation().type())
                : visit(ctx.expression());

        CompiscriptType valueType = visit(ctx.expression());
        if (!declaredType.isAssignableFrom(valueType)) {
            report(line, column, name,
                    "Cannot assign value of type '" + valueType + "' to constant of type '" + declaredType + "'.");
        }

        Symbol symbol = new Symbol(name, SymbolKind.CONSTANT, declaredType, line, false);
        symbol.markInitialized();
        if (!symbolTable.define(symbol)) {
            report(line, column, name, "Redeclaration of identifier '" + name + "' in the same scope.");
        }
        return declaredType;
    }

    @Override
    public CompiscriptType visitAssignment(CompiscriptParser.AssignmentContext ctx) {
        checkUnreachable(ctx);
        if (ctx.expression().size() == 1) {
            String name = ctx.Identifier().getText();
            CompiscriptType valueType = visit(ctx.expression(0));
            Symbol symbol = symbolTable.resolve(name).orElse(null);
            if (symbol == null) {
                report(line(ctx), column(ctx.Identifier()), name, "Use of undeclared variable '" + name + "'.");
                return CompiscriptType.error();
            }
            if (!symbol.isMutable()) {
                report(line(ctx), column(ctx.Identifier()), name, "Cannot assign to constant '" + name + "'.");
            } else if (!symbol.type().isAssignableFrom(valueType)) {
                report(line(ctx), column(ctx.Identifier()), name,
                        "Cannot assign value of type '" + valueType + "' to variable of type '" + symbol.type() + "'.");
            } else {
                symbolTable.updateInitialized(symbol);
            }
            return valueType;
        }

        CompiscriptType ownerType = visit(ctx.expression(0));
        CompiscriptType valueType = visit(ctx.expression(1));
        String property = ctx.Identifier().getText();
        CompiscriptType memberType = visitPropertyAccessType(ownerType, property, ctx);
        if (!memberType.isError() && !memberType.isAssignableFrom(valueType)) {
            report(line(ctx), column(ctx.Identifier()), property,
                    "Cannot assign value of type '" + valueType + "' to member of type '" + memberType + "'.");
        }
        return valueType;
    }

    @Override
    public CompiscriptType visitFunctionDeclaration(CompiscriptParser.FunctionDeclarationContext ctx) {
        checkUnreachable(ctx);
        String name = ctx.Identifier().getText();
        int line = line(ctx);
        int column = column(ctx.Identifier());

        List<CompiscriptType> parameterTypes = new ArrayList<>();
        List<Symbol> parameterSymbols = new ArrayList<>();
        if (ctx.parameters() != null) {
            for (CompiscriptParser.ParameterContext parameter : ctx.parameters().parameter()) {
                String paramName = parameter.Identifier().getText();
                if (parameterSymbols.stream().anyMatch(s -> s.name().equals(paramName))) {
                    report(line(parameter), column(parameter.Identifier()), paramName,
                            "Duplicate parameter name '" + paramName + "'.");
                }
                CompiscriptType paramType = parameter.type() != null
                        ? TypeResolver.resolve(parameter.type())
                        : CompiscriptType.error();
                Symbol paramSymbol = new Symbol(paramName, SymbolKind.PARAMETER, paramType, line(parameter), false);
                parameterSymbols.add(paramSymbol);
                parameterTypes.add(paramType);
            }
        }

        CompiscriptType returnType = ctx.type() != null
                ? TypeResolver.resolve(ctx.type())
                : CompiscriptType.voidType();

        CompiscriptType functionType = CompiscriptType.function(returnType, parameterTypes);
        Symbol functionSymbol = new Symbol(name, SymbolKind.FUNCTION, functionType, line, false);
        functionSymbol.parameters().addAll(parameterSymbols);

        if (!symbolTable.define(functionSymbol)) {
            report(line, column, name, "Redeclaration of function '" + name + "' in the same scope.");
        }

        symbolTable.enterScope(name, ScopeKind.FUNCTION);
        functionDepth++;
        CompiscriptType previousReturn = currentReturnType;
        currentReturnType = returnType;
        unreachable = false;

        for (Symbol parameter : parameterSymbols) {
            symbolTable.define(parameter);
        }

        visit(ctx.block());
        symbolTable.exitScope();
        functionDepth--;
        currentReturnType = previousReturn;
        return functionType;
    }

    @Override
    public CompiscriptType visitClassDeclaration(CompiscriptParser.ClassDeclarationContext ctx) {
        checkUnreachable(ctx);
        String name = ctx.Identifier(0).getText();
        int line = line(ctx);
        int column = column(ctx.Identifier(0));

        String parentName = ctx.Identifier().size() > 1 ? ctx.Identifier(1).getText() : null;
        if (parentName != null && symbolTable.resolveClass(parentName).isEmpty()) {
            report(line, column, parentName, "Undefined parent class '" + parentName + "'.");
        }

        Symbol classSymbol = new Symbol(name, SymbolKind.CLASS, CompiscriptType.classType(name), line, false);
        classSymbol.setParentClassName(parentName);
        if (!symbolTable.define(classSymbol)) {
            report(line, column, name, "Redeclaration of class '" + name + "' in the same scope.");
        }

        symbolTable.enterScope(name, ScopeKind.CLASS);
        String previousClass = currentClassName;
        currentClassName = name;
        for (CompiscriptParser.ClassMemberContext member : ctx.classMember()) {
            if (member.functionDeclaration() != null) {
                CompiscriptParser.FunctionDeclarationContext function = member.functionDeclaration();
                String memberName = function.Identifier().getText();
                Symbol memberSymbol = buildClassMemberFunction(function, classSymbol);
                classSymbol.members().put(memberName, memberSymbol);
                visitClassMemberFunction(function, memberSymbol);
            } else if (member.variableDeclaration() != null) {
                CompiscriptParser.VariableDeclarationContext variable = member.variableDeclaration();
                String memberName = variable.Identifier().getText();
                CompiscriptType memberType = variable.typeAnnotation() != null
                        ? TypeResolver.resolve(variable.typeAnnotation().type())
                        : CompiscriptType.error();
                Symbol memberSymbol = new Symbol(memberName, SymbolKind.VARIABLE, memberType, line(variable), true);
                if (variable.initializer() != null) {
                    CompiscriptType initType = visitInClassFieldInitializer(variable);
                    if (!memberType.isAssignableFrom(initType)) {
                        report(line(variable), column(variable.Identifier()), memberName,
                                "Cannot assign value of type '" + initType + "' to field of type '" + memberType + "'.");
                    }
                    memberSymbol.markInitialized();
                }
                classSymbol.members().put(memberName, memberSymbol);
            } else if (member.constantDeclaration() != null) {
                CompiscriptParser.ConstantDeclarationContext constant = member.constantDeclaration();
                String memberName = constant.Identifier().getText();
                CompiscriptType memberType = constant.typeAnnotation() != null
                        ? TypeResolver.resolve(constant.typeAnnotation().type())
                        : CompiscriptType.error();
                Symbol memberSymbol = new Symbol(memberName, SymbolKind.CONSTANT, memberType, line(constant), false);
                memberSymbol.markInitialized();
                classSymbol.members().put(memberName, memberSymbol);
            }
        }
        symbolTable.exitScope();
        currentClassName = previousClass;
        return classSymbol.type();
    }

    private Symbol buildClassMemberFunction(CompiscriptParser.FunctionDeclarationContext ctx, Symbol classSymbol) {
        List<CompiscriptType> parameterTypes = new ArrayList<>();
        List<Symbol> parameterSymbols = new ArrayList<>();
        if (ctx.parameters() != null) {
            for (CompiscriptParser.ParameterContext parameter : ctx.parameters().parameter()) {
                CompiscriptType paramType = parameter.type() != null
                        ? TypeResolver.resolve(parameter.type())
                        : CompiscriptType.error();
                Symbol paramSymbol = new Symbol(parameter.Identifier().getText(), SymbolKind.PARAMETER, paramType,
                        line(parameter), false);
                parameterSymbols.add(paramSymbol);
                parameterTypes.add(paramType);
            }
        }
        CompiscriptType returnType = ctx.type() != null
                ? TypeResolver.resolve(ctx.type())
                : CompiscriptType.voidType();
        Symbol memberSymbol = new Symbol(ctx.Identifier().getText(), SymbolKind.FUNCTION,
                CompiscriptType.function(returnType, parameterTypes), line(ctx), false);
        memberSymbol.parameters().addAll(parameterSymbols);
        return memberSymbol;
    }

    private void visitClassMemberFunction(CompiscriptParser.FunctionDeclarationContext ctx, Symbol memberSymbol) {
        symbolTable.enterScope(ctx.Identifier().getText(), ScopeKind.FUNCTION);
        functionDepth++;
        String previousClass = currentClassName;
        CompiscriptType previousReturn = currentReturnType;
        currentReturnType = memberSymbol.type().returnType();
        unreachable = false;
        for (Symbol parameter : memberSymbol.parameters()) {
            symbolTable.define(parameter);
        }
        visit(ctx.block());
        symbolTable.exitScope();
        functionDepth--;
        currentClassName = previousClass;
        currentReturnType = previousReturn;
    }

    private CompiscriptType visitInClassFieldInitializer(CompiscriptParser.VariableDeclarationContext ctx) {
        return visit(ctx.initializer().expression());
    }

    @Override
    public CompiscriptType visitBlock(CompiscriptParser.BlockContext ctx) {
        symbolTable.enterScope("block", ScopeKind.BLOCK);
        boolean previousUnreachable = unreachable;
        for (CompiscriptParser.StatementContext statement : ctx.statement()) {
            if (unreachable) {
                report(line(statement), column(statement), statement.getText(),
                        "Unreachable statement after return, break, or continue.");
            }
            visit(statement);
        }
        symbolTable.exitScope();
        unreachable = previousUnreachable;
        return CompiscriptType.voidType();
    }

    @Override
    public CompiscriptType visitIfStatement(CompiscriptParser.IfStatementContext ctx) {
        checkUnreachable(ctx);
        requireBoolean(ctx.expression(), "if condition");
        visit(ctx.block(0));
        if (ctx.block().size() > 1) {
            visit(ctx.block(1));
        }
        return CompiscriptType.voidType();
    }

    @Override
    public CompiscriptType visitWhileStatement(CompiscriptParser.WhileStatementContext ctx) {
        checkUnreachable(ctx);
        requireBoolean(ctx.expression(), "while condition");
        enterLoop(ctx.block());
        return CompiscriptType.voidType();
    }

    @Override
    public CompiscriptType visitDoWhileStatement(CompiscriptParser.DoWhileStatementContext ctx) {
        checkUnreachable(ctx);
        enterLoop(ctx.block());
        requireBoolean(ctx.expression(), "do-while condition");
        return CompiscriptType.voidType();
    }

    @Override
    public CompiscriptType visitForStatement(CompiscriptParser.ForStatementContext ctx) {
        checkUnreachable(ctx);
        symbolTable.enterScope("for", ScopeKind.LOOP);
        if (ctx.variableDeclaration() != null) {
            visit(ctx.variableDeclaration());
        } else if (ctx.assignment() != null) {
            visit(ctx.assignment());
        }
        if (ctx.expression().size() > 0 && ctx.expression(0) != null) {
            requireBoolean(ctx.expression(0), "for condition");
        }
        loopDepth++;
        unreachable = false;
        visit(ctx.block());
        if (ctx.expression().size() > 1 && ctx.expression(1) != null) {
            visit(ctx.expression(1));
        }
        loopDepth--;
        symbolTable.exitScope();
        return CompiscriptType.voidType();
    }

    @Override
    public CompiscriptType visitForeachStatement(CompiscriptParser.ForeachStatementContext ctx) {
        checkUnreachable(ctx);
        CompiscriptType collectionType = visit(ctx.expression());
        if (!collectionType.isError() && collectionType.kind() != CompiscriptType.Kind.ARRAY) {
            report(line(ctx.expression()), column(ctx.expression()), ctx.expression().getText(),
                    "foreach requires an array expression.");
        }
        symbolTable.enterScope("foreach", ScopeKind.LOOP);
        CompiscriptType elementType = collectionType.kind() == CompiscriptType.Kind.ARRAY
                ? collectionType.elementType()
                : CompiscriptType.error();
        Symbol iterator = new Symbol(ctx.Identifier().getText(), SymbolKind.VARIABLE, elementType, line(ctx), true);
        iterator.markInitialized();
        symbolTable.define(iterator);
        loopDepth++;
        unreachable = false;
        visit(ctx.block());
        loopDepth--;
        symbolTable.exitScope();
        return CompiscriptType.voidType();
    }

    @Override
    public CompiscriptType visitTryCatchStatement(CompiscriptParser.TryCatchStatementContext ctx) {
        checkUnreachable(ctx);
        visit(ctx.block(0));
        symbolTable.enterScope("catch", ScopeKind.CATCH);
        Symbol errorSymbol = new Symbol(ctx.Identifier().getText(), SymbolKind.VARIABLE, CompiscriptType.string(),
                line(ctx.Identifier()), true);
        errorSymbol.markInitialized();
        symbolTable.define(errorSymbol);
        visit(ctx.block(1));
        symbolTable.exitScope();
        return CompiscriptType.voidType();
    }

    @Override
    public CompiscriptType visitSwitchStatement(CompiscriptParser.SwitchStatementContext ctx) {
        checkUnreachable(ctx);
        CompiscriptType switchType = visit(ctx.expression());
        for (CompiscriptParser.SwitchCaseContext switchCase : ctx.switchCase()) {
            CompiscriptType caseType = visit(switchCase.expression());
            if (!switchType.isComparableWith(caseType)) {
                report(line(switchCase.expression()), column(switchCase.expression()), switchCase.expression().getText(),
                        "Switch case type '" + caseType + "' is not compatible with switch expression type '"
                                + switchType + "'.");
            }
            for (CompiscriptParser.StatementContext statement : switchCase.statement()) {
                visit(statement);
            }
        }
        if (ctx.defaultCase() != null) {
            for (CompiscriptParser.StatementContext statement : ctx.defaultCase().statement()) {
                visit(statement);
            }
        }
        return CompiscriptType.voidType();
    }

    @Override
    public CompiscriptType visitBreakStatement(CompiscriptParser.BreakStatementContext ctx) {
        checkUnreachable(ctx);
        if (loopDepth == 0) {
            report(line(ctx), column(ctx), "break", "break statement must be inside a loop.");
        }
        unreachable = true;
        return CompiscriptType.voidType();
    }

    @Override
    public CompiscriptType visitContinueStatement(CompiscriptParser.ContinueStatementContext ctx) {
        checkUnreachable(ctx);
        if (loopDepth == 0) {
            report(line(ctx), column(ctx), "continue", "continue statement must be inside a loop.");
        }
        unreachable = true;
        return CompiscriptType.voidType();
    }

    @Override
    public CompiscriptType visitReturnStatement(CompiscriptParser.ReturnStatementContext ctx) {
        checkUnreachable(ctx);
        if (functionDepth == 0) {
            report(line(ctx), column(ctx), "return", "return statement must be inside a function.");
            return CompiscriptType.voidType();
        }
        if (ctx.expression() == null) {
            if (currentReturnType.kind() != CompiscriptType.Kind.VOID) {
                report(line(ctx), column(ctx), "return", "Function must return a value of type '" + currentReturnType + "'.");
            }
        } else {
            CompiscriptType valueType = visit(ctx.expression());
            if (!currentReturnType.isAssignableFrom(valueType)) {
                report(line(ctx), column(ctx.expression()), ctx.expression().getText(),
                        "Return type '" + valueType + "' does not match declared return type '" + currentReturnType + "'.");
            }
        }
        unreachable = true;
        return CompiscriptType.voidType();
    }

    @Override
    public CompiscriptType visitExpressionStatement(CompiscriptParser.ExpressionStatementContext ctx) {
        checkUnreachable(ctx);
        visit(ctx.expression());
        return CompiscriptType.voidType();
    }

    @Override
    public CompiscriptType visitPrintStatement(CompiscriptParser.PrintStatementContext ctx) {
        checkUnreachable(ctx);
        visit(ctx.expression());
        return CompiscriptType.voidType();
    }

    @Override
    public CompiscriptType visitAssignExpr(CompiscriptParser.AssignExprContext ctx) {
        CompiscriptType valueType = visit(ctx.assignmentExpr());
        CompiscriptType targetType = evaluateLeftHandSide(ctx.leftHandSide());
        if (!targetType.isError() && !targetType.isAssignableFrom(valueType)) {
            report(line(ctx), column(ctx), ctx.getText(),
                    "Cannot assign value of type '" + valueType + "' to target of type '" + targetType + "'.");
        }
        return valueType;
    }

    @Override
    public CompiscriptType visitPropertyAssignExpr(CompiscriptParser.PropertyAssignExprContext ctx) {
        CompiscriptType valueType = visit(ctx.assignmentExpr());
        visitPropertyAccess(ctx.leftHandSide(), ctx.Identifier().getText(), true);
        return valueType;
    }

    @Override
    public CompiscriptType visitTernaryExpr(CompiscriptParser.TernaryExprContext ctx) {
        if (ctx.expression().isEmpty()) {
            return visit(ctx.logicalOrExpr());
        }
        requireBoolean(ctx.logicalOrExpr(), "ternary condition");
        CompiscriptType trueType = visit(ctx.expression(0));
        CompiscriptType falseType = visit(ctx.expression(1));
        if (!trueType.isAssignableFrom(falseType) && !falseType.isAssignableFrom(trueType)) {
            report(line(ctx), column(ctx), "?", "Ternary branches must have compatible types.");
            return CompiscriptType.error();
        }
        return trueType;
    }

    @Override
    public CompiscriptType visitLogicalOrExpr(CompiscriptParser.LogicalOrExprContext ctx) {
        if (ctx.logicalAndExpr().size() == 1) {
            return visit(ctx.logicalAndExpr(0));
        }
        for (CompiscriptParser.LogicalAndExprContext operand : ctx.logicalAndExpr()) {
            requireBooleanOperand(operand, "||");
        }
        return CompiscriptType.booleanType();
    }

    @Override
    public CompiscriptType visitLogicalAndExpr(CompiscriptParser.LogicalAndExprContext ctx) {
        if (ctx.equalityExpr().size() == 1) {
            return visit(ctx.equalityExpr(0));
        }
        for (CompiscriptParser.EqualityExprContext operand : ctx.equalityExpr()) {
            requireBooleanOperand(operand, "&&");
        }
        return CompiscriptType.booleanType();
    }

    @Override
    public CompiscriptType visitEqualityExpr(CompiscriptParser.EqualityExprContext ctx) {
        CompiscriptType left = visit(ctx.relationalExpr(0));
        for (int i = 1; i < ctx.relationalExpr().size(); i++) {
            CompiscriptType right = visit(ctx.relationalExpr(i));
            if (!left.isComparableWith(right)) {
                report(line(ctx), column(ctx.relationalExpr(i)), ctx.getChild(2 * i - 1).getText(),
                        "Operands of equality comparison must have compatible types.");
            }
        }
        return ctx.relationalExpr().size() > 1 ? CompiscriptType.booleanType() : left;
    }

    @Override
    public CompiscriptType visitRelationalExpr(CompiscriptParser.RelationalExprContext ctx) {
        CompiscriptType left = visit(ctx.additiveExpr(0));
        for (int i = 1; i < ctx.additiveExpr().size(); i++) {
            CompiscriptType right = visit(ctx.additiveExpr(i));
            if (!left.isNumeric() || !right.isNumeric()) {
                report(line(ctx), column(ctx.additiveExpr(i)), ctx.getChild(2 * i - 1).getText(),
                        "Relational operators require numeric operands.");
            }
            left = CompiscriptType.booleanType();
        }
        return left;
    }

    @Override
    public CompiscriptType visitAdditiveExpr(CompiscriptParser.AdditiveExprContext ctx) {
        CompiscriptType left = visit(ctx.multiplicativeExpr(0));
        for (int i = 1; i < ctx.multiplicativeExpr().size(); i++) {
            String operator = ctx.getChild(2 * i - 1).getText();
            CompiscriptType right = visit(ctx.multiplicativeExpr(i));
            if ("+".equals(operator)) {
                if (left.kind() == CompiscriptType.Kind.STRING || right.kind() == CompiscriptType.Kind.STRING) {
                    left = CompiscriptType.string();
                } else if (!left.isNumeric() || !right.isNumeric()) {
                    report(line(ctx), column(ctx.multiplicativeExpr(i)), operator,
                            "Addition requires numeric operands or string concatenation.");
                    left = CompiscriptType.error();
                } else {
                    left = CompiscriptType.numericResult(left, right);
                }
            } else {
                if (!left.isNumeric() || !right.isNumeric()) {
                    report(line(ctx), column(ctx.multiplicativeExpr(i)), operator,
                            "Subtraction requires numeric operands.");
                    left = CompiscriptType.error();
                } else {
                    left = CompiscriptType.numericResult(left, right);
                }
            }
        }
        return left;
    }

    @Override
    public CompiscriptType visitMultiplicativeExpr(CompiscriptParser.MultiplicativeExprContext ctx) {
        CompiscriptType left = visit(ctx.unaryExpr(0));
        for (int i = 1; i < ctx.unaryExpr().size(); i++) {
            CompiscriptType right = visit(ctx.unaryExpr(i));
            if (!left.isNumeric() || !right.isNumeric()) {
                report(line(ctx), column(ctx.unaryExpr(i)), ctx.getChild(2 * i - 1).getText(),
                        "Multiplicative operators require numeric operands.");
                left = CompiscriptType.error();
            } else {
                left = CompiscriptType.numericResult(left, right);
            }
        }
        return left;
    }

    @Override
    public CompiscriptType visitUnaryExpr(CompiscriptParser.UnaryExprContext ctx) {
        if (ctx.getChildCount() == 2) {
            CompiscriptType operand = visit(ctx.unaryExpr());
            String operator = ctx.getChild(0).getText();
            if ("!".equals(operator)) {
                if (!operand.isError() && operand.kind() != CompiscriptType.Kind.BOOLEAN) {
                    report(line(ctx), column(ctx), operator, "Logical negation requires a boolean operand.");
                }
                return CompiscriptType.booleanType();
            }
            if (!operand.isNumeric()) {
                report(line(ctx), column(ctx), operator, "Unary minus requires a numeric operand.");
                return CompiscriptType.error();
            }
            return operand.kind() == CompiscriptType.Kind.FLOAT
                    ? CompiscriptType.floatType()
                    : CompiscriptType.integer();
        }
        return visit(ctx.primaryExpr());
    }

    @Override
    public CompiscriptType visitLiteralExpr(CompiscriptParser.LiteralExprContext ctx) {
        if (ctx.Literal() != null) {
            String literal = ctx.Literal().getText();
            if (literal.startsWith("\"")) {
                return CompiscriptType.string();
            }
            if (literal.contains(".")) {
                return CompiscriptType.floatType();
            }
            return CompiscriptType.integer();
        }
        if (ctx.arrayLiteral() != null) {
            return visit(ctx.arrayLiteral());
        }
        if (ctx.getText().equals("null")) {
            return CompiscriptType.nullType();
        }
        if (ctx.getText().equals("true") || ctx.getText().equals("false")) {
            return CompiscriptType.booleanType();
        }
        return CompiscriptType.error();
    }

    @Override
    public CompiscriptType visitArrayLiteral(CompiscriptParser.ArrayLiteralContext ctx) {
        if (ctx.expression().isEmpty()) {
            return CompiscriptType.array(CompiscriptType.error());
        }
        CompiscriptType elementType = visit(ctx.expression(0));
        for (int i = 1; i < ctx.expression().size(); i++) {
            CompiscriptType current = visit(ctx.expression(i));
            if (!elementType.isAssignableFrom(current) && !current.isAssignableFrom(elementType)) {
                report(line(ctx.expression(i)), column(ctx.expression(i)), ctx.expression(i).getText(),
                        "Array elements must share a compatible type.");
            }
        }
        return CompiscriptType.array(elementType);
    }

    @Override
    public CompiscriptType visitIdentifierExpr(CompiscriptParser.IdentifierExprContext ctx) {
        return visitIdentifierUsage(ctx.Identifier(), false);
    }

    @Override
    public CompiscriptType visitNewExpr(CompiscriptParser.NewExprContext ctx) {
        String className = ctx.Identifier().getText();
        Symbol classSymbol = symbolTable.resolveClass(className).orElse(null);
        if (classSymbol == null) {
            report(line(ctx), column(ctx.Identifier()), className, "Undefined class '" + className + "'.");
            return CompiscriptType.error();
        }
        Symbol constructor = symbolTable.resolveInClassHierarchy(classSymbol, "constructor").orElse(null);
        if (constructor != null) {
            validateCallArguments(ctx.arguments(), constructor, ctx);
        }
        return CompiscriptType.classType(className);
    }

    @Override
    public CompiscriptType visitThisExpr(CompiscriptParser.ThisExprContext ctx) {
        if (currentClassName == null) {
            report(line(ctx), column(ctx), "this", "'this' can only be used inside class methods.");
            return CompiscriptType.error();
        }
        return CompiscriptType.classType(currentClassName);
    }

    @Override
    public CompiscriptType visitCallExpr(CompiscriptParser.CallExprContext ctx) {
        CompiscriptType calleeType = visitParent(ctx);
        Symbol functionSymbol = resolveCallableSymbol(ctx);
        if (calleeType.kind() != CompiscriptType.Kind.FUNCTION) {
            report(line(ctx), column(ctx), ctx.getText(), "Expression is not callable.");
            return CompiscriptType.error();
        }
        if (functionSymbol != null) {
            validateCallArguments(ctx.arguments(), functionSymbol, ctx);
        }
        return calleeType.returnType();
    }

    @Override
    public CompiscriptType visitIndexExpr(CompiscriptParser.IndexExprContext ctx) {
        CompiscriptType arrayType = visitParent(ctx);
        CompiscriptType indexType = visit(ctx.expression());
        if (indexType.kind() != CompiscriptType.Kind.INTEGER) {
            report(line(ctx.expression()), column(ctx.expression()), ctx.expression().getText(),
                    "Array index must be an integer.");
        }
        if (arrayType.kind() != CompiscriptType.Kind.ARRAY) {
            report(line(ctx), column(ctx), ctx.getText(), "Index operator requires an array expression.");
            return CompiscriptType.error();
        }
        return arrayType.elementType();
    }

    @Override
    public CompiscriptType visitPropertyAccessExpr(CompiscriptParser.PropertyAccessExprContext ctx) {
        return visitPropertyAccessFromSuffix(ctx);
    }

    @Override
    public CompiscriptType visitPrimaryExpr(CompiscriptParser.PrimaryExprContext ctx) {
        if (ctx.expression() != null) {
            return visit(ctx.expression());
        }
        return visit(ctx.getChild(0));
    }

    @Override
    public CompiscriptType visitLeftHandSide(CompiscriptParser.LeftHandSideContext ctx) {
        return evaluateLeftHandSide(ctx);
    }

    private CompiscriptType evaluateLeftHandSide(CompiscriptParser.LeftHandSideContext ctx) {
        CompiscriptType current = visit(ctx.primaryAtom());
        for (CompiscriptParser.SuffixOpContext suffix : ctx.suffixOp()) {
            if (suffix instanceof CompiscriptParser.CallExprContext call) {
                if (current.kind() != CompiscriptType.Kind.FUNCTION) {
                    report(line(call), column(call), call.getText(), "Expression is not callable.");
                    current = CompiscriptType.error();
                } else {
                    Symbol functionSymbol = resolveCallableSymbol(call);
                    if (functionSymbol != null) {
                        validateCallArguments(call.arguments(), functionSymbol, call);
                    }
                    current = current.returnType();
                }
            } else if (suffix instanceof CompiscriptParser.IndexExprContext index) {
                CompiscriptType indexType = visit(index.expression());
                if (indexType.kind() != CompiscriptType.Kind.INTEGER) {
                    report(line(index.expression()), column(index.expression()), index.expression().getText(),
                            "Array index must be an integer.");
                }
                if (current.kind() != CompiscriptType.Kind.ARRAY) {
                    report(line(index), column(index), index.getText(), "Index operator requires an array expression.");
                    current = CompiscriptType.error();
                } else {
                    current = current.elementType();
                }
            } else if (suffix instanceof CompiscriptParser.PropertyAccessExprContext property) {
                current = visitPropertyAccessType(current, property.Identifier().getText(), property);
            }
        }
        return current;
    }

    private CompiscriptType visitPropertyAccess(CompiscriptParser.LeftHandSideContext lhs, String property, boolean assignment) {
        CompiscriptType ownerType = evaluateLeftHandSide(lhs);
        return visitPropertyAccessType(ownerType, property, lhs);
    }

    private CompiscriptType visitPropertyAccessFromSuffix(CompiscriptParser.PropertyAccessExprContext ctx) {
        CompiscriptType ownerType = visitParent(ctx);
        return visitPropertyAccessType(ownerType, ctx.Identifier().getText(), ctx);
    }

    private CompiscriptType visitPropertyAccessType(CompiscriptType ownerType, String property, ParserRuleContext ctx) {
        if (ownerType.kind() != CompiscriptType.Kind.CLASS) {
            report(line(ctx), column(ctx), property, "Property access requires a class instance.");
            return CompiscriptType.error();
        }
        Symbol classSymbol = symbolTable.resolveClass(ownerType.className()).orElse(null);
        if (classSymbol == null) {
            report(line(ctx), column(ctx), property, "Undefined class '" + ownerType.className() + "'.");
            return CompiscriptType.error();
        }
        Symbol member = symbolTable.resolveInClassHierarchy(classSymbol, property).orElse(null);
        if (member == null) {
            report(line(ctx), column(ctx), property,
                    "Class '" + ownerType.className() + "' has no member '" + property + "'.");
            return CompiscriptType.error();
        }
        return member.type();
    }

    private Symbol resolveCallableSymbol(CompiscriptParser.CallExprContext ctx) {
        ParserRuleContext parent = ctx.getParent();
        if (parent instanceof CompiscriptParser.LeftHandSideContext lhs
                && lhs.primaryAtom() instanceof CompiscriptParser.IdentifierExprContext identifierExpr) {
            return symbolTable.resolve(identifierExpr.Identifier().getText()).orElse(null);
        }
        return null;
    }

    private CompiscriptType visitIdentifierUsage(TerminalNode identifier, boolean callContext) {
        String name = identifier.getText();
        Symbol symbol = symbolTable.resolve(name).orElse(null);
        if (symbol == null) {
            report(line(identifier), column(identifier), name, "Use of undeclared identifier '" + name + "'.");
            return CompiscriptType.error();
        }
        if (!symbol.isInitialized() && symbol.kind() == SymbolKind.VARIABLE) {
            report(line(identifier), column(identifier), name, "Variable '" + name + "' may be used before initialization.");
        }
        return symbol.type();
    }

    private void validateCallArguments(CompiscriptParser.ArgumentsContext argumentsContext,
                                       Symbol functionSymbol,
                                       ParserRuleContext ctx) {
        List<CompiscriptParser.ExpressionContext> arguments = argumentsContext == null
                ? List.of()
                : argumentsContext.expression();
        List<Symbol> parameters = functionSymbol.parameters();
        if (arguments.size() != parameters.size()) {
            report(line(ctx), column(ctx), functionSymbol.name(),
                    "Function '" + functionSymbol.name() + "' expects " + parameters.size()
                            + " arguments but received " + arguments.size() + ".");
        }
        int count = Math.min(arguments.size(), parameters.size());
        for (int i = 0; i < count; i++) {
            CompiscriptType argumentType = visit(arguments.get(i));
            CompiscriptType parameterType = parameters.get(i).type();
            if (!parameterType.isAssignableFrom(argumentType)) {
                report(line(arguments.get(i)), column(arguments.get(i)), arguments.get(i).getText(),
                        "Argument type '" + argumentType + "' does not match parameter type '" + parameterType + "'.");
            }
        }
    }

    private CompiscriptType visitParent(ParserRuleContext ctx) {
        ParserRuleContext parent = ctx.getParent();
        if (parent instanceof CompiscriptParser.LeftHandSideContext lhs) {
            CompiscriptType current = visit(lhs.primaryAtom());
            List<CompiscriptParser.SuffixOpContext> suffixes = lhs.suffixOp();
            for (CompiscriptParser.SuffixOpContext suffix : suffixes) {
                if (suffix == ctx) {
                    break;
                }
                current = applySuffix(current, suffix);
            }
            return current;
        }
        return CompiscriptType.error();
    }

    private CompiscriptType applySuffix(CompiscriptType current, CompiscriptParser.SuffixOpContext suffix) {
        if (suffix instanceof CompiscriptParser.CallExprContext call) {
            if (current.kind() != CompiscriptType.Kind.FUNCTION) {
                return CompiscriptType.error();
            }
            Symbol resolved = findFunctionForCall(current);
            if (resolved != null) {
                validateCallArguments(call.arguments(), resolved, call);
            }
            return current.returnType();
        }
        if (suffix instanceof CompiscriptParser.IndexExprContext index) {
            CompiscriptType indexType = visit(index.expression());
            if (indexType.kind() != CompiscriptType.Kind.INTEGER) {
                report(line(index.expression()), column(index.expression()), index.expression().getText(),
                        "Array index must be an integer.");
            }
            return current.kind() == CompiscriptType.Kind.ARRAY ? current.elementType() : CompiscriptType.error();
        }
        if (suffix instanceof CompiscriptParser.PropertyAccessExprContext property) {
            return visitPropertyAccessType(current, property.Identifier().getText(), property);
        }
        return current;
    }

    private Symbol findFunctionForCall(CompiscriptType functionType) {
        for (Scope scope : symbolTable.allScopes()) {
            for (Symbol symbol : scope.symbols().values()) {
                if (symbol.kind() == SymbolKind.FUNCTION && symbol.type().equals(functionType)) {
                    return symbol;
                }
            }
        }
        return null;
    }

    private void enterLoop(CompiscriptParser.BlockContext block) {
        loopDepth++;
        unreachable = false;
        visit(block);
        loopDepth--;
    }

    private void requireBoolean(ParserRuleContext ctx, String contextLabel) {
        CompiscriptType type = visit(ctx);
        if (!type.isError() && type.kind() != CompiscriptType.Kind.BOOLEAN) {
            report(line(ctx), column(ctx), ctx.getText(),
                    "Expression in " + contextLabel + " must be boolean.");
        }
    }

    private void requireBooleanOperand(ParserRuleContext ctx, String operator) {
        CompiscriptType type = visit(ctx);
        if (!type.isError() && type.kind() != CompiscriptType.Kind.BOOLEAN) {
            report(line(ctx), column(ctx), operator,
                    "Logical operator '" + operator + "' requires boolean operands.");
        }
    }

    private void checkUnreachable(ParserRuleContext ctx) {
        if (unreachable) {
            report(line(ctx), column(ctx), ctx.getText(),
                    "Unreachable statement after return, break, or continue.");
        }
    }

    private void report(int line, int column, String symbol, String description) {
        errors.report(line, column, symbol, description);
    }

    private int line(ParserRuleContext ctx) {
        return ctx.getStart() != null ? ctx.getStart().getLine() : 1;
    }

    private int column(ParserRuleContext ctx) {
        return ctx.getStart() != null ? ctx.getStart().getCharPositionInLine() + 1 : 1;
    }

    private int line(TerminalNode node) {
        return node.getSymbol().getLine();
    }

    private int column(TerminalNode node) {
        return node.getSymbol().getCharPositionInLine() + 1;
    }
}
