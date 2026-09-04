package com.compiscript.symbols;

import com.compiscript.types.CompiscriptType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** Stack-based symbol table with insert, lookup, update, and scope management. */
public class SymbolTable {

    private final Scope globalScope = new Scope("global", ScopeKind.GLOBAL, null);
    private final Deque<Scope> scopeStack = new ArrayDeque<>();
    private final List<Scope> allScopes = new ArrayList<>();

    public SymbolTable() {
        scopeStack.push(globalScope);
        allScopes.add(globalScope);
    }

    public Scope currentScope() {
        return scopeStack.peek();
    }

    public Scope globalScope() {
        return globalScope;
    }

    public List<Scope> allScopes() {
        return List.copyOf(allScopes);
    }

    public void enterScope(String name, ScopeKind kind) {
        Scope scope = new Scope(name, kind, currentScope());
        scopeStack.push(scope);
        allScopes.add(scope);
    }

    public void exitScope() {
        if (scopeStack.size() > 1) {
            scopeStack.pop();
        }
    }

    public boolean define(Symbol symbol) {
        Scope scope = currentScope();
        if (scope.symbols().containsKey(symbol.name())) {
            return false;
        }
        scope.symbols().put(symbol.name(), symbol);
        return true;
    }

    public Optional<Symbol> resolve(String name) {
        Scope scope = currentScope();
        while (scope != null) {
            Symbol symbol = scope.symbols().get(name);
            if (symbol != null) {
                return Optional.of(symbol);
            }
            scope = scope.parent();
        }
        return Optional.empty();
    }

    public Optional<Symbol> resolveClass(String className) {
        return resolve(className).filter(symbol -> symbol.kind() == SymbolKind.CLASS);
    }

    public Optional<Symbol> resolveInClassHierarchy(Symbol classSymbol, String memberName) {
        Symbol current = classSymbol;
        while (current != null) {
            Symbol member = current.members().get(memberName);
            if (member != null) {
                return Optional.of(member);
            }
            if (current.parentClassName() == null) {
                break;
            }
            current = resolveClass(current.parentClassName()).orElse(null);
        }
        return Optional.empty();
    }

    public boolean updateInitialized(Symbol symbol) {
        if (!symbol.isMutable()) {
            return false;
        }
        symbol.markInitialized();
        return true;
    }

    public String currentScopePath() {
        List<String> parts = new ArrayList<>();
        for (Scope scope : scopeStack) {
            if (scope.kind() != ScopeKind.GLOBAL) {
                parts.add(0, scope.name());
            }
        }
        if (parts.isEmpty()) {
            return "global";
        }
        return "global/" + String.join("/", parts);
    }
}
