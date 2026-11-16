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

class MipsGenerationTest {

    private final CompiscriptAnalyzer analyzer = new CompiscriptAnalyzer();

    @Test
    void emitsGlobalsForDeclarations() throws IOException {
        String asm = asmOf("ejemplos/tac/declarations.cps");
        assertTrue(asm.contains("g_a: .word 0"));
        assertTrue(asm.contains("g_PI: .word 0"));
        assertTrue(asm.contains("sw $t0, g_a"));
        assertTrue(asm.contains("move $v1, $fp"));
    }

    @Test
    void emitsArithmeticAndFloatPromotion() throws IOException {
        String asm = asmOf("ejemplos/tac/arithmetic.cps");
        assertTrue(asm.contains("cvt.s.w"));
        assertTrue(asm.contains("add.s"));
        assertTrue(asm.contains("sub $t0, $zero, $t0") || asm.contains("sub $t1, $zero, $t1")
                || asm.contains("neg"));
    }

    @Test
    void emitsBranchesForLogicAndControl() throws IOException {
        assertTrue(asmOf("ejemplos/tac/logical.cps").contains("beq "));
        String control = asmOf("ejemplos/tac/control.cps");
        assertTrue(control.contains("beq "));
        assertTrue(control.contains("\nj ") || control.contains("\nj"));
        assertTrue(control.contains("li $v0, 9"));
    }

    @Test
    void emitsArrayAllocationAndBoundsCheck() throws IOException {
        String asm = asmOf("ejemplos/tac/arrays.cps");
        assertTrue(asm.contains("li $v0, 9"));
        assertTrue(asm.contains("__bounds_fail"));
        assertTrue(asm.contains("sw $v1, 0("));
    }

    @Test
    void emitsObjectConstructionAndInheritedCalls() throws IOException {
        String classes = asmOf("ejemplos/tac/classes.cps");
        assertTrue(classes.contains("jal Animal_constructor"));
        assertTrue(classes.contains("jal Animal_hablar"));
        assertTrue(classes.contains("li $v0, 9"));
        String inheritance = asmOf("ejemplos/tac/inheritance.cps");
        assertTrue(inheritance.contains("jal Animal_constructor"));
        assertTrue(inheritance.contains("jal Perro_hablar"));
    }

    @Test
    void emitsTryHandlerStackAndCatchValue() throws IOException {
        String asm = asmOf("ejemplos/tac/try_catch.cps");
        assertTrue(asm.contains("__hsp"));
        assertTrue(asm.contains("__caught"));
        assertTrue(asm.contains("__hstack"));
        assertTrue(asm.contains("la $v1, Lcatch"));
    }

    @Test
    void peepholeRewritesAddZeroAndMultiplyByTwo() throws IOException {
        String asm = asmOf("ejemplos/mips/peephole.cps");
        assertTrue(asm.contains("sll "));
        assertFalse(asm.contains("mul "));
        assertTrue(asm.contains("move "));
    }

    @Test
    void suppressesAssemblyWhenTheProgramHasErrors() {
        AnalysisResult result = analyzer.analyze("let a: integer = true;\n");
        assertFalse(result.errors().isEmpty());
        assertTrue(result.intermediateCode().isSuppressed());
        assertTrue(result.assembly().isSuppressed());
        assertEquals("", result.assembly().render());
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
}
