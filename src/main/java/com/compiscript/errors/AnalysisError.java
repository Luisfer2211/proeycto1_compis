package com.compiscript.errors;

/** A single analysis finding: type, location, symbol, and description. */
public record AnalysisError(ErrorType type, int line, int column, String symbol, String description) {
}
