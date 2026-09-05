package com.compiscript.analysis;

import com.compiscript.errors.AnalysisError;
import com.compiscript.errors.CollectingErrorListener;
import com.compiscript.errors.ErrorType;
import com.compiscript.errors.SemanticErrorCollector;
import com.compiscript.parser.CompiscriptLexer;
import com.compiscript.parser.CompiscriptParser;
import com.compiscript.semantic.SemanticVisitor;
import com.compiscript.symbols.SymbolTable;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Runs lexical, syntactic, and semantic analysis over Compiscript source code. */
public class CompiscriptAnalyzer {

    public AnalysisResult analyze(File file) throws IOException {
        CharStream input = CharStreams.fromPath(file.toPath(), StandardCharsets.UTF_8);
        return analyze(input);
    }

    public AnalysisResult analyze(String source) {
        CharStream input = CharStreams.fromString(source);
        return analyze(input);
    }

    private AnalysisResult analyze(CharStream input) {
        CollectingErrorListener lexerListener = new CollectingErrorListener(ErrorType.LEXICAL);
        CollectingErrorListener parserListener = new CollectingErrorListener(ErrorType.SYNTAX);

        CompiscriptLexer lexer = new CompiscriptLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(lexerListener);

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CompiscriptParser parser = new CompiscriptParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(parserListener);

        ParseTree tree = parser.program();

        SymbolTable symbolTable = new SymbolTable();
        SemanticErrorCollector semanticErrors = new SemanticErrorCollector();
        SemanticVisitor visitor = new SemanticVisitor(symbolTable, semanticErrors);
        visitor.visit(tree);

        List<AnalysisError> errors = new ArrayList<>();
        errors.addAll(lexerListener.getErrors());
        errors.addAll(parserListener.getErrors());
        errors.addAll(semanticErrors.getErrors());
        errors.sort(Comparator.comparingInt(AnalysisError::line).thenComparingInt(AnalysisError::column));

        return AnalysisResult.of(errors, tree, symbolTable);
    }
}
