package com.compiscript.errors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Collects semantic errors with deduplication support. */
public class SemanticErrorCollector {

    private final List<AnalysisError> errors = new ArrayList<>();

    public void report(int line, int column, String symbol, String description) {
        AnalysisError candidate = new AnalysisError(ErrorType.SEMANTIC, line, column, symbol, description);
        boolean duplicate = errors.stream().anyMatch(existing ->
                existing.line() == candidate.line()
                        && existing.column() == candidate.column()
                        && existing.description().equals(candidate.description()));
        if (!duplicate) {
            errors.add(candidate);
        }
    }

    public List<AnalysisError> getErrors() {
        errors.sort(Comparator.comparingInt(AnalysisError::line).thenComparingInt(AnalysisError::column));
        return List.copyOf(errors);
    }
}
