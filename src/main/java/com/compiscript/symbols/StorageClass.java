package com.compiscript.symbols;

/** Where a symbol lives once addresses are assigned for code generation. */
public enum StorageClass {
    GLOBAL,
    LOCAL,
    PARAMETER,
    FIELD,
    TEMP,
    FRAME
}
