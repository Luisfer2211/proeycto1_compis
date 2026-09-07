package com.compiscript.semantic;

import com.compiscript.analysis.AnalysisResult;
import com.compiscript.analysis.CompiscriptAnalyzer;
import com.compiscript.errors.ErrorType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LexicalSyntaxExamplesTest {

    private final CompiscriptAnalyzer analyzer = new CompiscriptAnalyzer();

    @Test
    void labExamplesContinueReportingLexicalAndSyntaxErrors() throws IOException {
        Path examples = Path.of("ejemplos");
        assertTrue(Files.exists(examples), "Lab examples folder must exist");
        for (Path file : Files.list(examples).filter(path -> path.toString().endsWith(".cps")).toList()) {
            AnalysisResult result = analyzer.analyze(file.toFile());
            String name = file.getFileName().toString();
            if (name.contains("sin_errores")) {
                assertTrue(result.errors().stream().noneMatch(error ->
                        error.type() == ErrorType.LEXICAL || error.type() == ErrorType.SYNTAX));
            } else {
                assertTrue(result.errors().stream().anyMatch(error ->
                        error.type() == ErrorType.LEXICAL || error.type() == ErrorType.SYNTAX));
            }
        }
    }
}
