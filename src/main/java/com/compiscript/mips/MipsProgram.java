package com.compiscript.mips;

import java.util.List;

/** MIPS text produced from a TAC program, or suppressed when analysis found errors. */
public final class MipsProgram {

    private final boolean suppressed;
    private final String text;
    private final List<RegisterDecision> decisions;
    private final ActivationNode activation;

    private MipsProgram(boolean suppressed, String text, List<RegisterDecision> decisions, ActivationNode activation) {
        this.suppressed = suppressed;
        this.text = text == null ? "" : text;
        this.decisions = decisions == null ? List.of() : List.copyOf(decisions);
        this.activation = activation;
    }

    public static MipsProgram suppressed() {
        return new MipsProgram(true, "", List.of(), null);
    }

    public static MipsProgram of(String text, List<RegisterDecision> decisions, ActivationNode activation) {
        return new MipsProgram(false, text, decisions, activation);
    }

    public boolean isSuppressed() {
        return suppressed;
    }

    public String render() {
        return suppressed ? "" : text;
    }

    public boolean contains(String fragment) {
        return text.contains(fragment);
    }

    public List<RegisterDecision> decisions() {
        return decisions;
    }

    public ActivationNode activation() {
        return activation;
    }
}
