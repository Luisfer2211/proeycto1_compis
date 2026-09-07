package com.compiscript.semantic;

import com.compiscript.analysis.AnalysisResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopeRulesTest extends SemanticTestSupport {

    @Test
    void reportsUndeclaredAndRedeclaredIdentifiers() throws IOException {
        AnalysisResult result = analyzeResource("scope_bad.cps");
        assertTrue(hasSemanticMessage(result, "undeclared"));
        assertTrue(hasSemanticMessage(result, "Redeclaration"));
    }
}
