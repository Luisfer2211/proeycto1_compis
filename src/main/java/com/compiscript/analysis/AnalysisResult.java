package com.compiscript.analysis;

import com.compiscript.errors.AnalysisError;
import com.compiscript.symbols.Scope;
import com.compiscript.symbols.Symbol;
import com.compiscript.symbols.SymbolTable;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;

/** Result of analyzing a Compiscript source file or snippet. */
public record AnalysisResult(
        boolean successful,
        List<AnalysisError> errors,
        ParseTree parseTree,
        SymbolTable symbolTable
) {

    public static AnalysisResult of(List<AnalysisError> errors, ParseTree parseTree, SymbolTable symbolTable) {
        return new AnalysisResult(errors.isEmpty(), errors, parseTree, symbolTable);
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
        return symbolTable.allScopes().stream()
                .flatMap(scope -> scope.symbols().values().stream().map(symbol -> toRow(scope, symbol)))
                .toList();
    }

    private SymbolRow toRow(Scope scope, Symbol symbol) {
        String scopePath = buildScopePath(scope);
        return new SymbolRow(scopePath, symbol.name(), symbol.kind().name(), symbol.type().toString(),
                symbol.isInitialized(), symbol.line());
    }

    private String buildScopePath(Scope scope) {
        if (scope.parent() == null) {
            return scope.name();
        }
        return buildScopePath(scope.parent()) + "/" + scope.name();
    }

    public record SymbolRow(String scope, String name, String kind, String type, boolean initialized, int line) {
    }
}
