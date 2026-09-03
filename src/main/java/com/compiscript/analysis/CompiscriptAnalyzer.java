package com.compiscript.analysis;

import com.compiscript.errors.AnalysisError;
import com.compiscript.errors.CollectingErrorListener;
import com.compiscript.errors.ErrorType;
import com.compiscript.parser.CompiscriptLexer;
import com.compiscript.parser.CompiscriptParser;
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

/** Bootstrap analyzer: lexical and syntax analysis only. */
public class CompiscriptAnalyzer {

    public AnalysisResult analyze(File file) throws IOException {
        return analyze(CharStreams.fromPath(file.toPath(), StandardCharsets.UTF_8));
    }

    public AnalysisResult analyze(String source) {
        return analyze(CharStreams.fromString(source));
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

        List<AnalysisError> errors = new ArrayList<>();
        errors.addAll(lexerListener.getErrors());
        errors.addAll(parserListener.getErrors());
        errors.sort(Comparator.comparingInt(AnalysisError::line).thenComparingInt(AnalysisError::column));

        return AnalysisResult.of(errors, tree, null);
    }
}
