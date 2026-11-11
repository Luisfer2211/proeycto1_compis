package com.compiscript.mips;

import com.compiscript.symbols.Scope;
import com.compiscript.symbols.ScopeKind;
import com.compiscript.symbols.StorageClass;
import com.compiscript.symbols.Symbol;
import com.compiscript.symbols.SymbolKind;
import com.compiscript.symbols.SymbolTable;
import com.compiscript.types.CompiscriptType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Addresses already recorded by the symbol table, grouped for code generation. */
final class ProgramLayout {

    final Map<String, NamedSlot> globals = new LinkedHashMap<>();
    final Map<String, FuncEnv> functions = new LinkedHashMap<>();
    final Map<String, Symbol> classes = new LinkedHashMap<>();

    static ProgramLayout from(SymbolTable table) {
        ProgramLayout layout = new ProgramLayout();
        Map<Scope, List<Scope>> children = new HashMap<>();
        for (Scope scope : table.allScopes()) {
            if (scope.parent() != null) {
                children.computeIfAbsent(scope.parent(), key -> new ArrayList<>()).add(scope);
            }
            for (Symbol symbol : scope.symbols().values()) {
                if (symbol.kind() == SymbolKind.CLASS) {
                    layout.classes.put(symbol.name(), symbol);
                }
            }
        }

        Map<Scope, FuncEnv> byScope = new HashMap<>();
        for (Scope scope : table.allScopes()) {
            if (scope.kind() != ScopeKind.FUNCTION || scope.parent() == null) {
                continue;
            }
            Symbol function = scope.parent().symbols().get(scope.name());
            if (function == null || function.label() == null) {
                continue;
            }
            int size = function.activationRecord() == null ? 0 : function.activationRecord().size();
            FuncEnv env = new FuncEnv(function.label(), function.name(), size, function.needsStaticLink(),
                    function.type());
            if (scope.parent().kind() == ScopeKind.CLASS) {
                env.className = scope.parent().name();
            }
            byScope.put(scope, env);
            layout.functions.put(function.label(), env);
        }
        for (Map.Entry<Scope, FuncEnv> entry : byScope.entrySet()) {
            Scope parent = entry.getKey().parent();
            while (parent != null && parent.kind() != ScopeKind.FUNCTION) {
                parent = parent.parent();
            }
            if (parent != null) {
                entry.getValue().parent = byScope.get(parent);
            }
            collectSlots(entry.getKey(), entry.getValue(), children);
        }
        collectGlobals(table.globalScope(), layout, children);
        return layout;
    }

    List<FrameSlot> frame(String label) {
        if ("__main".equals(label)) {
            return List.of(new FrameSlot(0, "$ra", "SAVED"), new FrameSlot(1, "$fp parent", "SAVED"));
        }
        FuncEnv env = functions.get(label);
        if (env == null) {
            return List.of();
        }
        List<FrameSlot> slots = new ArrayList<>();
        env.slots.values().stream()
                .sorted(Comparator.comparingInt(slot -> slot.address))
                .forEach(slot -> slots.add(new FrameSlot(slot.address, slot.name, slot.storage.name())));
        slots.add(new FrameSlot(env.frameSize, "$ra", "SAVED"));
        slots.add(new FrameSlot(env.frameSize + 1, "$fp parent", "SAVED"));
        return slots;
    }

    Integer fieldOffset(String className, String field) {
        if (className != null) {
            Symbol current = classes.get(className);
            while (current != null) {
                Symbol member = current.members().get(field);
                if (member != null && member.storage() == StorageClass.FIELD) {
                    return member.address();
                }
                if (current.parentClassName() == null) {
                    break;
                }
                current = classes.get(current.parentClassName());
            }
        }
        for (Symbol classSymbol : classes.values()) {
            Symbol member = classSymbol.members().get(field);
            if (member != null && member.storage() == StorageClass.FIELD) {
                return member.address();
            }
        }
        return null;
    }

    int fieldWords(String className) {
        Symbol classSymbol = classes.get(className);
        if (classSymbol == null) {
            return 1;
        }
        return Math.max(1, classSymbol.fieldSize());
    }

    Location locate(FuncEnv env, String name) {
        int depth = 0;
        for (FuncEnv current = env; current != null; current = current.parent) {
            NamedSlot slot = current.slots.get(name);
            if (slot != null) {
                return new Location(depth, slot);
            }
            depth++;
        }
        NamedSlot global = globals.get(name);
        if (global != null) {
            return new Location(-1, global);
        }
        return null;
    }

    private static void collectSlots(Scope scope, FuncEnv env, Map<Scope, List<Scope>> children) {
        for (Symbol symbol : scope.symbols().values()) {
            if (symbol.storage() == StorageClass.LOCAL || symbol.storage() == StorageClass.PARAMETER) {
                env.slots.put(symbol.name(), NamedSlot.of(symbol));
            }
        }
        for (Scope child : children.getOrDefault(scope, List.of())) {
            if (child.kind() != ScopeKind.FUNCTION) {
                collectSlots(child, env, children);
            }
        }
    }

    private static void collectGlobals(Scope scope, ProgramLayout layout, Map<Scope, List<Scope>> children) {
        if (scope.kind() == ScopeKind.FUNCTION) {
            return;
        }
        for (Symbol symbol : scope.symbols().values()) {
            if (symbol.storage() == StorageClass.GLOBAL) {
                layout.globals.put(symbol.name(), NamedSlot.of(symbol));
            }
        }
        for (Scope child : children.getOrDefault(scope, List.of())) {
            collectGlobals(child, layout, children);
        }
    }

    static final class NamedSlot {
        final String name;
        final int address;
        final StorageClass storage;
        final CompiscriptType type;

        private NamedSlot(String name, int address, StorageClass storage, CompiscriptType type) {
            this.name = name;
            this.address = address;
            this.storage = storage;
            this.type = type;
        }

        static NamedSlot of(Symbol symbol) {
            return new NamedSlot(symbol.name(), symbol.address(), symbol.storage(), symbol.type());
        }
    }

    static final class FuncEnv {
        final String label;
        final String name;
        final int frameSize;
        final boolean needsStaticLink;
        final CompiscriptType type;
        final Map<String, NamedSlot> slots = new LinkedHashMap<>();
        FuncEnv parent;
        String className;

        FuncEnv(String label, String name, int frameSize, boolean needsStaticLink, CompiscriptType type) {
            this.label = label;
            this.name = name;
            this.frameSize = frameSize;
            this.needsStaticLink = needsStaticLink;
            this.type = type;
        }
    }

    record Location(int depth, NamedSlot slot) {
        boolean global() {
            return depth < 0;
        }
    }
}
