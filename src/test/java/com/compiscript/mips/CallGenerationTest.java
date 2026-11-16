package com.compiscript.mips;

import com.compiscript.analysis.AnalysisResult;
import com.compiscript.analysis.CompiscriptAnalyzer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallGenerationTest {

    private final CompiscriptAnalyzer analyzer = new CompiscriptAnalyzer();

    @Test
    void emitsCallAndReturnSequences() throws IOException {
        String asm = asmOf("ejemplos/tac/functions.cps");
        assertTrue(asm.contains("fn_add:"));
        assertTrue(asm.contains("sw $ra,"));
        assertTrue(asm.contains("jal fn_add"));
        assertTrue(asm.contains("jr $ra"));
        assertTrue(asm.contains("lw $v1, 16($fp)"));
    }

    @Test
    void emitsRecursiveCallThatSavesTheReturnAddress() throws IOException {
        AnalysisResult result = analyze("ejemplos/tac/recursion.cps");
        String asm = result.assembly().render();
        assertTrue(asm.contains("jal fn_factorial"));
        assertTrue(asm.contains("sw $ra,"));
        assertTrue(containsLabel(result.assembly().activation(), "fn_factorial recursive"));
    }

    @Test
    void passesTheStaticLinkForANestedFunction() throws IOException {
        String asm = asmOf("ejemplos/tac/closures.cps");
        assertTrue(asm.contains("makeAdder_inner:"));
        assertTrue(asm.contains("sw $fp, 0($sp)"));
        assertTrue(asm.contains("jal makeAdder_inner"));
        assertTrue(asm.contains("lw $a3, 0($a3)") || asm.contains("lw $a3, 0($fp)"));
    }

    private String asmOf(String path) throws IOException {
        return analyze(path).assembly().render();
    }

    private AnalysisResult analyze(String path) throws IOException {
        AnalysisResult result = analyzer.analyze(Files.readString(Path.of(path)));
        assertEquals(0, result.errors().size(), result.errors().toString());
        assertFalse(result.assembly().isSuppressed());
        return result;
    }

    private boolean containsLabel(ActivationNode node, String label) {
        if (node == null) {
            return false;
        }
        if (label.equals(node.label())) {
            return true;
        }
        return node.children().stream().anyMatch(child -> containsLabel(child, label));
    }
}
