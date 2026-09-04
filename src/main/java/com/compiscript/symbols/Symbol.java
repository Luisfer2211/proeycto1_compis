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
}
