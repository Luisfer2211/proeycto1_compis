package com.compiscript.symbols;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** A lexical scope containing symbol definitions. */
public class Scope {

    private final String name;
    private final ScopeKind kind;
    private final Scope parent;
    private final Map<String, Symbol> symbols = new LinkedHashMap<>();

    public Scope(String name, ScopeKind kind, Scope parent) {
        this.name = name;
        this.kind = kind;
        this.parent = parent;
    }

    public String name() {
        return name;
    }

    public ScopeKind kind() {
        return kind;
    }

    public Scope parent() {
        return parent;
    }

    public Map<String, Symbol> symbols() {
        return symbols;
    }

    public Optional<Symbol> lookupLocal(String identifier) {
        return Optional.ofNullable(symbols.get(identifier));
    }
}
