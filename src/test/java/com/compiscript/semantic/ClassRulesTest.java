package com.compiscript.semantic;

import com.compiscript.analysis.AnalysisResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassRulesTest extends SemanticTestSupport {

    @Test
    void acceptsValidClassesAndInheritance() throws IOException {
        AnalysisResult result = analyzeResource("classes_ok.cps");
        assertEquals(0, countSemanticErrors(result));
    }

    @Test
    void reportsMissingMembersParentAndThisMisuse() throws IOException {
        AnalysisResult result = analyzeResource("classes_bad.cps");
        assertTrue(hasSemanticMessage(result, "no member"));
        assertTrue(hasSemanticMessage(result, "'this'"));
        assertTrue(hasSemanticMessage(result, "Undefined parent class"));
    }
}
