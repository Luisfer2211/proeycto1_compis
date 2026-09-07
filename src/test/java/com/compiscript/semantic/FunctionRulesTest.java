package com.compiscript.semantic;

import com.compiscript.analysis.AnalysisResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionRulesTest extends SemanticTestSupport {

    @Test
    void acceptsValidFunctionsRecursionAndClosures() throws IOException {
        AnalysisResult result = analyzeResource("functions_ok.cps");
        assertEquals(0, countSemanticErrors(result));
    }

    @Test
    void reportsInvalidCallsReturnsAndDuplicateParameters() throws IOException {
        AnalysisResult result = analyzeResource("functions_bad.cps");
        assertTrue(hasSemanticMessage(result, "expects"));
        assertTrue(hasSemanticMessage(result, "Return type"));
        assertTrue(hasSemanticMessage(result, "inside a function"));
        assertTrue(hasSemanticMessage(result, "Duplicate parameter"));
    }
}
