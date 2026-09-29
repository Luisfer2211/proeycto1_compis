package com.compiscript.symbols;

/** Runtime frame reserved for one function or method activation. */
public final class ActivationRecord {

    private final String entryLabel;
    private final int size;
    private final boolean needsStaticLink;

    public ActivationRecord(String entryLabel, int size, boolean needsStaticLink) {
        this.entryLabel = entryLabel;
        this.size = size;
        this.needsStaticLink = needsStaticLink;
    }

    public String entryLabel() {
        return entryLabel;
    }

    public int size() {
        return size;
    }

    public boolean needsStaticLink() {
        return needsStaticLink;
    }
}
