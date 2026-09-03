package com.compiscript.errors;

/** Converts raw ANTLR messages into user-friendly English descriptions. */
public final class ErrorMessageTranslator {

    private ErrorMessageTranslator() {
    }

    public static String translate(String antlrMessage, String symbol) {
        if (antlrMessage == null) {
            return "Unspecified error near '" + symbol + "'.";
        }
        if (antlrMessage.startsWith("token recognition error")) {
            return "The character or sequence '" + symbol + "' is not valid in Compiscript.";
        }
        if (antlrMessage.startsWith("mismatched input")) {
            return "Found '" + symbol + "', which does not match what is expected at this point.";
        }
        if (antlrMessage.startsWith("missing")) {
            return "A required symbol is missing before '" + symbol + "'.";
        }
        if (antlrMessage.startsWith("extraneous input")) {
            return "The symbol '" + symbol + "' is unexpected at this point.";
        }
        if (antlrMessage.startsWith("no viable alternative")) {
            return "The symbol '" + symbol + "' does not fit any valid Compiscript structure here.";
        }
        return "Syntax error near '" + symbol + "'.";
    }
}
