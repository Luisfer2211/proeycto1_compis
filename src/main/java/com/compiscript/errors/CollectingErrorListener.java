package com.compiscript.errors;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects lexer or parser errors instead of printing them to the console. */
public class CollectingErrorListener extends BaseErrorListener {

    private final ErrorType type;
    private final List<AnalysisError> errors = new ArrayList<>();

    public CollectingErrorListener(ErrorType type) {
        this.type = type;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                            int charPositionInLine, String msg, RecognitionException e) {
        String symbol = extractSymbol(offendingSymbol, msg);
        String description = ErrorMessageTranslator.translate(msg, symbol);
        AnalysisError candidate = new AnalysisError(type, line, charPositionInLine + 1, symbol, description);
        if (isImmediateDuplicate(candidate)) {
            return;
        }
        errors.add(candidate);
    }

    private boolean isImmediateDuplicate(AnalysisError candidate) {
        if (errors.isEmpty()) {
            return false;
        }
        AnalysisError last = errors.get(errors.size() - 1);
        return last.line() == candidate.line()
                && last.column() == candidate.column()
                && last.description().equals(candidate.description());
    }

    private String extractSymbol(Object offendingSymbol, String msg) {
        if (offendingSymbol instanceof Token token) {
            return sanitize(token.getText());
        }
        int start = msg.indexOf('\'');
        int end = msg.lastIndexOf('\'');
        if (start >= 0 && end > start) {
            return sanitize(msg.substring(start + 1, end));
        }
        return "?";
    }

    private String sanitize(String text) {
        String clean = text.replace("\r", "").replace("\n", "\\n");
        int limit = 30;
        return clean.length() > limit ? clean.substring(0, limit) + "..." : clean;
    }

    public List<AnalysisError> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
