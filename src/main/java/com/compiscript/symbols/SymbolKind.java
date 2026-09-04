package com.compiscript.symbols;

import com.compiscript.types.CompiscriptType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Describes what a symbol represents in the program. */
public enum SymbolKind {
    VARIABLE,
    CONSTANT,
    PARAMETER,
    FUNCTION,
    CLASS
}
