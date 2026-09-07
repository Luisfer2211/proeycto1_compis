package com.compiscript.semantic;

import com.compiscript.analysis.AnalysisResult;
import com.compiscript.analysis.CompiscriptAnalyzer;
import com.compiscript.errors.AnalysisError;
import com.compiscript.errors.ErrorType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Shared helpers for semantic analysis tests. */
abstract class SemanticTestSupport {

    protected final CompiscriptAnalyzer analyzer = new CompiscriptAnalyzer();

    protected AnalysisResult analyzeResource(String resourcePath) throws IOException {
        Path path = Path.of("src/test/resources/cps/semantic", resourcePath);
        String source = Files.readString(path, StandardCharsets.UTF_8);
        return analyzer.analyze(source);
    }

    protected long countSemanticErrors(AnalysisResult result) {
        return result.errors().stream().filter(error -> error.type() == ErrorType.SEMANTIC).count();
    }

    protected boolean hasSemanticMessage(AnalysisResult result, String fragment) {
        return result.errors().stream()
                .filter(error -> error.type() == ErrorType.SEMANTIC)
                .map(AnalysisError::description)
                .anyMatch(description -> description.contains(fragment));
    }
}
