package com.compiscript.types;

import com.compiscript.parser.CompiscriptParser;

/** Utility methods for converting grammar type nodes into semantic types. */
public final class TypeResolver {

    private TypeResolver() {
    }

    public static CompiscriptType resolve(CompiscriptParser.TypeContext ctx) {
        if (ctx == null) {
            return CompiscriptType.error();
        }
        CompiscriptType base = resolveBase(ctx.baseType());
        int dimensions = 0;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            if ("[".equals(ctx.getChild(i).getText())) {
                dimensions++;
            }
        }
        return base.withArrayDimensions(dimensions);
    }

    private static CompiscriptType resolveBase(CompiscriptParser.BaseTypeContext ctx) {
        if (ctx == null) {
            return CompiscriptType.error();
        }
        if (ctx.getText().equals("integer")) {
            return CompiscriptType.integer();
        }
        if (ctx.getText().equals("float")) {
            return CompiscriptType.floatType();
        }
        if (ctx.getText().equals("boolean")) {
            return CompiscriptType.booleanType();
        }
        if (ctx.getText().equals("string")) {
            return CompiscriptType.string();
        }
        return CompiscriptType.classType(ctx.getText());
    }
}
