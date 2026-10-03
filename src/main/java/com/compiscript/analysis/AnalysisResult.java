package com.compiscript.analysis;

import com.compiscript.errors.AnalysisError;
import com.compiscript.symbols.Scope;
import com.compiscript.symbols.Symbol;
import com.compiscript.symbols.SymbolTable;
import com.compiscript.tac.TacProgram;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

/** Result of analyzing a Compiscript source file or snippet. */
public record AnalysisResult(
        boolean successful,
        List<AnalysisError> errors,
        ParseTree parseTree,
        SymbolTable symbolTable,
        TacProgram intermediateCode
) {

    public static AnalysisResult of(List<AnalysisError> errors, ParseTree parseTree, SymbolTable symbolTable,
                                    TacProgram intermediateCode) {
        return new AnalysisResult(errors.isEmpty(), errors, parseTree, symbolTable, intermediateCode);
    }

    public long lexicalErrorCount() {
        return errors.stream().filter(error -> error.type().name().equals("LEXICAL")).count();
    }

    public long syntaxErrorCount() {
        return errors.stream().filter(error -> error.type().name().equals("SYNTAX")).count();
    }

    public long semanticErrorCount() {
        return errors.stream().filter(error -> error.type().name().equals("SEMANTIC")).count();
    }

    public List<SymbolRow> symbolRows() {
        if (symbolTable == null) {
            return List.of();
        }
        List<SymbolRow> rows = new ArrayList<>();
        for (Scope scope : symbolTable.allScopes()) {
            String scopePath = buildScopePath(scope);
            for (Symbol symbol : scope.symbols().values()) {
                rows.add(toRow(scopePath, symbol));
                if (symbol.kind().name().equals("CLASS")) {
                    for (Symbol member : symbol.members().values()) {
                        if (member.storage() != null && member.storage().name().equals("FIELD")) {
                            rows.add(toRow(scopePath + "/" + symbol.name(), member));
                        }
                    }
                }
            }
        }
        return rows;
    }

    private SymbolRow toRow(String scopePath, Symbol symbol) {
        String storage = symbol.storage() == null ? "" : symbol.storage().name();
        return new SymbolRow(scopePath, symbol.name(), symbol.kind().name(), symbol.type().toString(),
                storage, symbol.address(), symbol.isInitialized(), symbol.line());
    }

    private String buildScopePath(Scope scope) {
        if (scope.parent() == null) {
            return scope.name();
        }
        return buildScopePath(scope.parent()) + "/" + scope.name();
    }

    public record SymbolRow(String scope, String name, String kind, String type, String storage, int address,
                            boolean initialized, int line) {
    }
}
