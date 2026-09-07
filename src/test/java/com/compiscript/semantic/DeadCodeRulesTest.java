package com.compiscript.semantic;

import com.compiscript.analysis.AnalysisResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeadCodeRulesTest extends SemanticTestSupport {

    @Test
    void reportsDeadCodeAndInvalidCallableUsage() throws IOException {
        AnalysisResult result = analyzeResource("dead_code_bad.cps");
        assertTrue(hasSemanticMessage(result, "Unreachable"));
        assertTrue(hasSemanticMessage(result, "numeric"));
    }
}
