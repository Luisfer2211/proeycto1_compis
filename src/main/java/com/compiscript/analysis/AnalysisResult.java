package com.compiscript.analysis;

import com.compiscript.errors.AnalysisError;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;

/** Bootstrap analysis result without symbol table integration. */
public record AnalysisResult(
        boolean successful,
        List<AnalysisError> errors,
        ParseTree parseTree,
        Object symbolTable
) {

    public static AnalysisResult of(List<AnalysisError> errors, ParseTree parseTree, Object symbolTable) {
        return new AnalysisResult(errors.isEmpty(), errors, parseTree, symbolTable);
    }

    public long lexicalErrorCount() {
        return errors.stream().filter(error -> error.type().name().equals("LEXICAL")).count();
    }

    public long syntaxErrorCount() {
        return errors.stream().filter(error -> error.type().name().equals("SYNTAX")).count();
    }

    public long semanticErrorCount() {
        return 0;
    }

    public List<Object> symbolRows() {
        return List.of();
    }
}
