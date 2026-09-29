package com.compiscript.symbols;

import com.compiscript.types.CompiscriptType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A symbol entry stored in the symbol table. */
public class Symbol {

    private final String name;
    private final SymbolKind kind;
    private CompiscriptType type;
    private final int line;
    private boolean initialized;
    private final boolean mutable;
    private final List<Symbol> parameters = new ArrayList<>();
    private final Map<String, Symbol> members = new LinkedHashMap<>();
    private String parentClassName;
    private StorageClass storage;
    private int address = -1;
    private String label;
    private ActivationRecord activationRecord;
    private boolean needsStaticLink;
    private boolean needsReceiver;
    private int nextFieldOffset;
    private int fieldSize;

    public Symbol(String name, SymbolKind kind, CompiscriptType type, int line, boolean mutable) {
        this.name = name;
        this.kind = kind;
        this.type = type;
        this.line = line;
        this.mutable = mutable;
        this.initialized = kind == SymbolKind.CONSTANT || kind == SymbolKind.PARAMETER;
    }

    public String name() {
        return name;
    }

    public SymbolKind kind() {
        return kind;
    }

    public CompiscriptType type() {
        return type;
    }

    public void setType(CompiscriptType type) {
        this.type = type;
    }

    public int line() {
        return line;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void markInitialized() {
        this.initialized = true;
    }

    public boolean isMutable() {
        return mutable;
    }

    public List<Symbol> parameters() {
        return parameters;
    }

    public Map<String, Symbol> members() {
        return members;
    }

    public String parentClassName() {
        return parentClassName;
    }

    public void setParentClassName(String parentClassName) {
        this.parentClassName = parentClassName;
    }

    public StorageClass storage() {
        return storage;
    }

    public void setStorage(StorageClass storage) {
        this.storage = storage;
    }

    public int address() {
        return address;
    }

    public void setAddress(int address) {
        this.address = address;
    }

    public String label() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public ActivationRecord activationRecord() {
        return activationRecord;
    }

    public void setActivationRecord(ActivationRecord activationRecord) {
        this.activationRecord = activationRecord;
    }

    public boolean needsStaticLink() {
        return needsStaticLink;
    }

    public void setNeedsStaticLink(boolean needsStaticLink) {
        this.needsStaticLink = needsStaticLink;
    }

    public boolean needsReceiver() {
        return needsReceiver;
    }

    public void setNeedsReceiver(boolean needsReceiver) {
        this.needsReceiver = needsReceiver;
    }

    public void setFieldOffsetBase(int base) {
        this.nextFieldOffset = base;
        this.fieldSize = base;
    }

    public int allocateFieldOffset() {
        int offset = nextFieldOffset;
        nextFieldOffset++;
        fieldSize = nextFieldOffset;
        return offset;
    }

    public int fieldSize() {
        return fieldSize;
    }
}
