package com.compiscript.mips;

/** One getReg choice, shown in the IDE and asserted by tests. */
public record RegisterDecision(String function, String temporary, String register, boolean spilled) {
}
