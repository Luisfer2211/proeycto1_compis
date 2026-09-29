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
    private int globalOffset;

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
        assignStorage(symbol);
        scope.symbols().put(symbol.name(), symbol);
        return true;
    }

    public boolean isInsideFunction() {
        return enclosingFunctionScope() != null;
    }

    public String mintLabel(String name) {
        Scope scope = currentScope();
        while (scope != null) {
            if (scope.kind() == ScopeKind.CLASS || scope.kind() == ScopeKind.FUNCTION) {
                return scope.name() + "_" + name;
            }
            scope = scope.parent();
        }
        return "fn_" + name;
    }

    public void prepareClassLayout(Symbol classSymbol) {
        if (classSymbol.parentClassName() == null) {
            classSymbol.setFieldOffsetBase(0);
            return;
        }
        Symbol parent = resolveClass(classSymbol.parentClassName()).orElse(null);
        classSymbol.setFieldOffsetBase(parent == null ? 0 : parent.fieldSize());
    }

    public void defineField(Symbol classSymbol, Symbol field) {
        field.setStorage(StorageClass.FIELD);
        field.setAddress(classSymbol.allocateFieldOffset());
        classSymbol.members().put(field.name(), field);
    }

    public void finishFunction(Symbol function) {
        Scope frame = enclosingFunctionScope();
        int size = frame == null ? 0 : frame.frameSize();
        if (function.label() == null) {
            function.setLabel(mintLabel(function.name()));
        }
        function.setStorage(StorageClass.FRAME);
        function.setAddress(size);
        function.setActivationRecord(new ActivationRecord(function.label(), size, function.needsStaticLink()));
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

    private void assignStorage(Symbol symbol) {
        if (symbol.kind() == SymbolKind.FUNCTION || symbol.kind() == SymbolKind.CLASS) {
            if (symbol.label() == null) {
                symbol.setLabel(symbol.kind() == SymbolKind.CLASS ? symbol.name() : mintLabel(symbol.name()));
            }
            return;
        }
        if (symbol.kind() == SymbolKind.PARAMETER) {
            Scope frame = enclosingFunctionScope();
            symbol.setStorage(StorageClass.PARAMETER);
            symbol.setAddress(frame == null ? globalOffset++ : frame.allocateFrameSlot());
            return;
        }
        Scope frame = enclosingFunctionScope();
        if (frame == null) {
            symbol.setStorage(StorageClass.GLOBAL);
            symbol.setAddress(globalOffset++);
            return;
        }
        symbol.setStorage(StorageClass.LOCAL);
        symbol.setAddress(frame.allocateFrameSlot());
    }

    private Scope enclosingFunctionScope() {
        Scope scope = currentScope();
        while (scope != null) {
            if (scope.kind() == ScopeKind.FUNCTION) {
                return scope;
            }
            scope = scope.parent();
        }
        return null;
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
