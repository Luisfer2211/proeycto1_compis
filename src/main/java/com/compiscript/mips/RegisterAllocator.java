package com.compiscript.mips;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dragon-book {@code getReg}: reuse a register that already holds the temporary,
 * otherwise take the free {@code $t} with the lowest index, otherwise spill the
 * temporary whose next use is farthest away.
 */
public final class RegisterAllocator {

    private final String[] registers;
    private final String[] occupant;
    private final Map<String, Integer> assigned = new HashMap<>();
    private final Map<String, Integer> nextUse = new HashMap<>();
    private final Map<String, Integer> spillSlot = new HashMap<>();
    private final Set<String> inMemory = new java.util.HashSet<>();
    private final List<RegisterDecision> decisions = new ArrayList<>();
    private int nextSpill;
    private String function = "main";

    public RegisterAllocator(int registerCount) {
        if (registerCount < 1) {
            throw new IllegalArgumentException("registerCount must be positive");
        }
        this.registers = new String[registerCount];
        this.occupant = new String[registerCount];
        for (int i = 0; i < registerCount; i++) {
            registers[i] = "$t" + i;
        }
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public void setNextUse(String temporary, int nextInstruction) {
        nextUse.put(temporary, nextInstruction);
    }

    public void reset() {
        assigned.clear();
        Arrays.fill(occupant, null);
        inMemory.clear();
        spillSlot.clear();
        nextSpill = 0;
        nextUse.clear();
    }

    public List<RegisterDecision> decisions() {
        return List.copyOf(decisions);
    }

    public Allocation getReg(String temporary, int atIndex, Set<String> pinned) {
        Integer current = assigned.get(temporary);
        if (current != null) {
            decisions.add(new RegisterDecision(function, temporary, registers[current], false));
            return Allocation.keep(registers[current]);
        }

        Integer free = firstFree();
        String spillInstruction = null;
        boolean spilled = false;
        if (free == null) {
            String victim = chooseVictim(pinned, atIndex);
            free = assigned.remove(victim);
            occupant[free] = null;
            if (!isDead(victim, atIndex)) {
                int slot = spillSlot.computeIfAbsent(victim, key -> nextSpill++);
                spillInstruction = "sw " + registers[free] + ", " + spillOffset(slot) + "($fp)";
                inMemory.add(victim);
                spilled = true;
            }
        }

        String reload = null;
        if (inMemory.contains(temporary)) {
            int slot = spillSlot.computeIfAbsent(temporary, key -> nextSpill++);
            reload = "lw " + registers[free] + ", " + spillOffset(slot) + "($fp)";
        }
        assigned.put(temporary, free);
        occupant[free] = temporary;
        decisions.add(new RegisterDecision(function, temporary, registers[free], spilled));
        return new Allocation(registers[free], spilled, spillInstruction, reload);
    }

    public List<String> spillAll() {
        List<String> stores = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : new ArrayList<>(assigned.entrySet())) {
            int slot = spillSlot.computeIfAbsent(entry.getKey(), key -> nextSpill++);
            stores.add("sw " + registers[entry.getValue()] + ", " + spillOffset(slot) + "($fp)");
            inMemory.add(entry.getKey());
        }
        assigned.clear();
        Arrays.fill(occupant, null);
        return stores;
    }

    public void release(String temporary) {
        Integer index = assigned.remove(temporary);
        if (index != null) {
            occupant[index] = null;
        }
    }

    /** Drop a previous home so the next getReg writes a new value instead of reloading it. */
    public void discardMemory(String temporary) {
        inMemory.remove(temporary);
    }

    public static int spillOffset(int slot) {
        return -(slot + 1) * 4;
    }

    private Integer firstFree() {
        for (int i = 0; i < occupant.length; i++) {
            if (occupant[i] == null) {
                return i;
            }
        }
        return null;
    }

    private String chooseVictim(Set<String> pinned, int atIndex) {
        String victim = null;
        int farthest = Integer.MIN_VALUE;
        boolean victimDead = false;
        for (String temporary : assigned.keySet()) {
            if (pinned.contains(temporary)) {
                continue;
            }
            boolean dead = isDead(temporary, atIndex);
            int next = nextUse.getOrDefault(temporary, Integer.MAX_VALUE);
            if (victim == null || (dead && !victimDead) || (dead == victimDead && next > farthest)) {
                victim = temporary;
                farthest = next;
                victimDead = dead;
            }
        }
        if (victim == null) {
            throw new IllegalStateException("getReg found no register to spill");
        }
        return victim;
    }

    private boolean isDead(String temporary, int atIndex) {
        int next = nextUse.getOrDefault(temporary, Integer.MAX_VALUE);
        return next <= atIndex;
    }

    public record Allocation(String register, boolean spilled, String spillBefore, String reload) {

        static Allocation keep(String register) {
            return new Allocation(register, false, null, null);
        }
    }
}
