package com.compiscript.mips;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterAllocatorTest {

    @Test
    void reusesTheRegisterThatAlreadyHoldsTheTemporary() {
        RegisterAllocator allocator = new RegisterAllocator(2);
        RegisterAllocator.Allocation first = allocator.getReg("t0", 0, Set.of());
        RegisterAllocator.Allocation again = allocator.getReg("t0", 1, Set.of());

        assertEquals("$t0", first.register());
        assertEquals("$t0", again.register());
        assertFalse(again.spilled());
    }

    @Test
    void spillsTheTemporaryWhoseNextUseIsFarthest() {
        RegisterAllocator allocator = new RegisterAllocator(2);
        allocator.setNextUse("t0", 100);
        allocator.setNextUse("t1", 40);
        allocator.setNextUse("t2", 5);

        assertEquals("$t0", allocator.getReg("t0", 1, Set.of()).register());
        assertEquals("$t1", allocator.getReg("t1", 2, Set.of()).register());
        RegisterAllocator.Allocation third = allocator.getReg("t2", 3, Set.of());

        assertTrue(third.spilled());
        assertEquals("$t0", third.register());
        assertTrue(third.spillBefore().contains("sw $t0"));
        assertTrue(third.spillBefore().contains("($fp)"));
    }
}
