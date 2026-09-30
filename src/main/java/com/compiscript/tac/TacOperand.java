package com.compiscript.tac;

import com.compiscript.types.CompiscriptType;

/** A name, literal, or temporary that can appear in a three-address instruction. */
public final class TacOperand {

    private final String text;
    private final boolean temporary;
    private final CompiscriptType type;

    private TacOperand(String text, boolean temporary, CompiscriptType type) {
        this.text = text;
        this.temporary = temporary;
        this.type = type == null ? CompiscriptType.error() : type;
    }

    public static TacOperand named(String text, CompiscriptType type) {
        return new TacOperand(text, false, type);
    }

    public static TacOperand temp(String text, CompiscriptType type) {
        return new TacOperand(text, true, type);
    }

    public static TacOperand literal(String text, CompiscriptType type) {
        return new TacOperand(text, false, type);
    }

    public String text() {
        return text;
    }

    public boolean temporary() {
        return temporary;
    }

    public CompiscriptType type() {
        return type;
    }
}
