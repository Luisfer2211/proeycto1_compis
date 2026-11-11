package com.compiscript.mips;

import java.util.ArrayList;
import java.util.List;

/** One procedure or call in the static activation tree. */
public final class ActivationNode {

    private final String label;
    private final boolean recursive;
    private final List<FrameSlot> slots;
    private final List<ActivationNode> children = new ArrayList<>();

    public ActivationNode(String label, boolean recursive, List<FrameSlot> slots) {
        this.label = label;
        this.recursive = recursive;
        this.slots = List.copyOf(slots);
    }

    public String label() {
        return label;
    }

    public boolean recursive() {
        return recursive;
    }

    public List<FrameSlot> slots() {
        return slots;
    }

    public List<ActivationNode> children() {
        return children;
    }

    public void addChild(ActivationNode child) {
        children.add(child);
    }
}
