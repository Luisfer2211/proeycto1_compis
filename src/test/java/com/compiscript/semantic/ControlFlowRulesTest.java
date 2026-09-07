package com.compiscript.semantic;

import com.compiscript.analysis.AnalysisResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlFlowRulesTest extends SemanticTestSupport {

    @Test
    void reportsInvalidConditionsBreakContinueAndArrayRules() throws IOException {
        AnalysisResult result = analyzeResource("control_flow_bad.cps");
        assertTrue(hasSemanticMessage(result, "boolean"));
        assertTrue(hasSemanticMessage(result, "inside a loop"));
        assertTrue(hasSemanticMessage(result, "Array index"));
        assertTrue(hasSemanticMessage(result, "compatible type"));
    }
}
