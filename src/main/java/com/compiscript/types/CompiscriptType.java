package com.compiscript.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Represents a Compiscript type used during semantic analysis. */
public final class CompiscriptType {

    public enum Kind {
        INTEGER,
        BOOLEAN,
        STRING,
        NULL,
        VOID,
        ERROR,
        CLASS,
        ARRAY,
        FUNCTION
    }

    private final Kind kind;
    private final String className;
    private final CompiscriptType elementType;
    private final CompiscriptType returnType;
    private final List<CompiscriptType> parameterTypes;

    private CompiscriptType(Kind kind, String className, CompiscriptType elementType,
                            CompiscriptType returnType, List<CompiscriptType> parameterTypes) {
        this.kind = kind;
        this.className = className;
        this.elementType = elementType;
        this.returnType = returnType;
        this.parameterTypes = parameterTypes == null ? List.of() : List.copyOf(parameterTypes);
    }

    public static CompiscriptType integer() {
        return new CompiscriptType(Kind.INTEGER, null, null, null, null);
    }

    public static CompiscriptType booleanType() {
        return new CompiscriptType(Kind.BOOLEAN, null, null, null, null);
    }

    public static CompiscriptType string() {
        return new CompiscriptType(Kind.STRING, null, null, null, null);
    }

    public static CompiscriptType nullType() {
        return new CompiscriptType(Kind.NULL, null, null, null, null);
    }

    public static CompiscriptType voidType() {
        return new CompiscriptType(Kind.VOID, null, null, null, null);
    }

    public static CompiscriptType error() {
        return new CompiscriptType(Kind.ERROR, null, null, null, null);
    }

    public static CompiscriptType classType(String name) {
        return new CompiscriptType(Kind.CLASS, name, null, null, null);
    }

    public static CompiscriptType array(CompiscriptType elementType) {
        return new CompiscriptType(Kind.ARRAY, null, elementType, null, null);
    }

    public static CompiscriptType function(CompiscriptType returnType, List<CompiscriptType> parameterTypes) {
        return new CompiscriptType(Kind.FUNCTION, null, null, returnType, parameterTypes);
    }

    public Kind kind() {
        return kind;
    }

    public String className() {
        return className;
    }

    public CompiscriptType elementType() {
        return elementType;
    }

    public CompiscriptType returnType() {
        return returnType;
    }

    public List<CompiscriptType> parameterTypes() {
        return parameterTypes;
    }

    public boolean isNumeric() {
        return kind == Kind.INTEGER;
    }

    public boolean isError() {
        return kind == Kind.ERROR;
    }

    public boolean isAssignableFrom(CompiscriptType valueType) {
        if (valueType == null || valueType.isError()) {
            return true;
        }
        if (kind == Kind.ERROR) {
            return true;
        }
        if (valueType.kind == Kind.NULL) {
            return kind == Kind.CLASS || kind == Kind.ARRAY || kind == Kind.STRING;
        }
        if (kind != valueType.kind) {
            return false;
        }
        if (kind == Kind.CLASS) {
            return className.equals(valueType.className);
        }
        if (kind == Kind.ARRAY) {
            return elementType.isAssignableFrom(valueType.elementType);
        }
        if (kind == Kind.FUNCTION) {
            if (parameterTypes.size() != valueType.parameterTypes.size()) {
                return false;
            }
            for (int i = 0; i < parameterTypes.size(); i++) {
                if (!parameterTypes.get(i).isAssignableFrom(valueType.parameterTypes.get(i))) {
                    return false;
                }
            }
            return returnType.isAssignableFrom(valueType.returnType);
        }
        return true;
    }

    public boolean isComparableWith(CompiscriptType other) {
        if (isError() || other == null || other.isError()) {
            return true;
        }
        if (kind == Kind.NULL || other.kind == Kind.NULL) {
            return kind == other.kind || kind == Kind.CLASS || other.kind == Kind.CLASS
                    || kind == Kind.ARRAY || other.kind == Kind.ARRAY;
        }
        if (kind != other.kind) {
            return false;
        }
        if (kind == Kind.CLASS) {
            return className.equals(other.className);
        }
        if (kind == Kind.ARRAY) {
            return elementType.isComparableWith(other.elementType);
        }
        return true;
    }

    public CompiscriptType withArrayDimensions(int dimensions) {
        CompiscriptType current = this;
        for (int i = 0; i < dimensions; i++) {
            current = array(current);
        }
        return current;
    }

    @Override
    public String toString() {
        return switch (kind) {
            case INTEGER -> "integer";
            case BOOLEAN -> "boolean";
            case STRING -> "string";
            case NULL -> "null";
            case VOID -> "void";
            case ERROR -> "error";
            case CLASS -> className;
            case ARRAY -> elementType + "[]";
            case FUNCTION -> {
                List<String> params = new ArrayList<>();
                for (CompiscriptType parameterType : parameterTypes) {
                    params.add(parameterType.toString());
                }
                yield "(" + String.join(", ", params) + ") -> " + returnType;
            }
        };
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CompiscriptType other)) {
            return false;
        }
        return kind == other.kind
                && Objects.equals(className, other.className)
                && Objects.equals(elementType, other.elementType)
                && Objects.equals(returnType, other.returnType)
                && Objects.equals(parameterTypes, other.parameterTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, className, elementType, returnType, parameterTypes);
    }
}
