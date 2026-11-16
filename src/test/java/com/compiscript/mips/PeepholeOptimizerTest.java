package com.compiscript.mips;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PeepholeOptimizerTest {

    private final PeepholeOptimizer optimizer = new PeepholeOptimizer();

    @Test
    void removesMoveToTheSameRegister() {
        assertEquals(List.of("add $t1, $t2, $t3"),
                optimizer.optimize(List.of("move $t0, $t0", "add $t1, $t2, $t3")));
        assertFalse(optimizer.optimize(List.of("addu $t0, $t0, $zero")).contains("addu $t0, $t0, $zero"));
    }

    @Test
    void removesJumpToTheNextLabel() {
        assertEquals(List.of("L:"), optimizer.optimize(List.of("j L", "L:")));
    }

    @Test
    void removesUnreachableInstructionsAfterAnUnconditionalJump() {
        assertEquals(List.of("L:"),
                optimizer.optimize(List.of("j L", "add $t0, $t0, $t1", "L:")));
        assertEquals(List.of("j L2", "L1:", "L2:"),
                optimizer.optimize(List.of("j L2", "add $t0, $t0, $t1", "L1:", "L2:")));
        assertEquals(List.of("jr $ra", "next:"),
                optimizer.optimize(List.of("jr $ra", "addi $sp, $sp, 4", "next:")));
    }

    @Test
    void reducesPowerOfTwoMulAndDivToShifts() {
        assertEquals(List.of("sll $t0, $t1, 2"), optimizer.optimize(List.of("mul $t0, $t1, 4")));
        assertEquals(List.of("sra $t0, $t1, 3"), optimizer.optimize(List.of("div $t0, $t1, 8")));
    }

    @Test
    void foldsAddZeroAndMulOneIntoMove() {
        assertEquals(List.of("move $t0, $t1"), optimizer.optimize(List.of("add $t0, $t1, $zero")));
        assertEquals(List.of("move $t0, $t1"), optimizer.optimize(List.of("mul $t0, $t1, 1")));
    }

    @Test
    void removesLoadThatRepeatsThePreviousStore() {
        assertEquals(List.of("sw $t0, 0($fp)"),
                optimizer.optimize(List.of("sw $t0, 0($fp)", "lw $t0, 0($fp)")));
    }
}
