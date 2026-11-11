package com.compiscript.mips;

/** One word of an activation record, addressed as word * 4($fp). */
public record FrameSlot(int word, String name, String storage) {

    public String text() {
        return word + "  " + name + "  " + storage;
    }
}
