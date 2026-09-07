package com.compiscript.semantic;

import com.compiscript.analysis.AnalysisResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolTableTest extends SemanticTestSupport {

    @Test
    void insertsAndLooksUpSymbolsAcrossScopes() throws IOException {
        AnalysisResult result = analyzeResource("types_ok.cps");
        assertTrue(result.symbolRows().stream().anyMatch(row -> row.name().equals("a")));
        assertTrue(result.symbolRows().stream().anyMatch(row -> row.scope().contains("block")));
    }

    @Test
    void exposesInitializedAndMutableState() throws IOException {
        AnalysisResult result = analyzeResource("const_bad.cps");
        assertTrue(result.symbolRows().stream().anyMatch(row -> row.name().equals("PI") && row.initialized()));
        assertFalse(result.symbolRows().isEmpty());
    }
}
