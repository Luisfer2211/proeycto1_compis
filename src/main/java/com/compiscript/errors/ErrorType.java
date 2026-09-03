package com.compiscript.errors;

/** Distinguishes whether an error was detected by the lexer, parser, or semantic visitor. */
public enum ErrorType {
    LEXICAL("Lexical"),
    SYNTAX("Syntax"),
    SEMANTIC("Semantic");

    private final String label;

    ErrorType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
