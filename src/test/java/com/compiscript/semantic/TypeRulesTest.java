package com.compiscript.semantic;

import com.compiscript.analysis.AnalysisResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeRulesTest extends SemanticTestSupport {

    @Test
    void acceptsValidTypeUsage() throws IOException {
        AnalysisResult result = analyzeResource("types_ok.cps");
        assertEquals(0, countSemanticErrors(result));
    }

    @Test
    void reportsInvalidArithmeticAndLogicalTypes() throws IOException {
        AnalysisResult result = analyzeResource("types_bad.cps");
        assertTrue(countSemanticErrors(result) >= 2);
        assertTrue(hasSemanticMessage(result, "boolean"));
        assertTrue(hasSemanticMessage(result, "numeric"));
    }

    @Test
    void rejectsConstReassignment() throws IOException {
        AnalysisResult result = analyzeResource("const_bad.cps");
        assertTrue(hasSemanticMessage(result, "Cannot assign to constant"));
    }
}
