# Compiscript Semantic Analyzer

Lexical, syntax, and semantic analyzer for the **Compiscript** language (`.cps`), built with **Java 17 + ANTLR4 + Swing**.

Project 01 — Compiler Construction, UVG.

## Requirements

- JDK 17 or newer
- Maven 3.9+

## Build and run

```bash
mvn clean package
java -jar target/compiscript-analyzer-1.0-SNAPSHOT.jar
```

The IDE opens without command-line arguments. Use **Open .cps** to load a file or write code directly in the editor, then press **Analyze**.

## Run tests

```bash
mvn test
```

## Project structure

```
src/main/antlr4/com/compiscript/parser/Compiscript.g4   ANTLR grammar
src/main/java/com/compiscript/errors/                  error model and collectors
src/main/java/com/compiscript/symbols/                 symbol table and scopes
src/main/java/com/compiscript/types/                   semantic type system
src/main/java/com/compiscript/semantic/                semantic visitor
src/main/java/com/compiscript/analysis/                analysis pipeline
src/main/java/com/compiscript/gui/                     Swing IDE
src/test/resources/cps/semantic/                       semantic test fixtures
ejemplos/                                              sample .cps files for grading
docs/architecture.md                                   implementation notes
```

## IDE features

- Editable source editor
- File picker for `.cps` files
- Error table (lexical, syntax, semantic)
- AST tree visualization
- Symbol table with scope information

## Sample files

| File | Purpose |
|------|---------|
| `ejemplos/01_baja_sin_errores.cps` | Low complexity, no lexical/syntax errors |
| `ejemplos/02_baja_errores_lexicos.cps` | Lexical errors |
| `ejemplos/03_baja_errores_sintacticos.cps` | Syntax errors |
| `src/test/resources/cps/semantic/types_bad.cps` | Type rule violations |
| `src/test/resources/cps/semantic/scope_bad.cps` | Scope rule violations |
| `src/test/resources/cps/semantic/functions_bad.cps` | Function rule violations |
| `src/test/resources/cps/semantic/classes_ok.cps` | Valid classes and inheritance |
| `src/test/resources/cps/semantic/dead_code_bad.cps` | Dead code detection |

## Semantic rules implemented

- Type checking for arithmetic, logical, comparison, and assignment expressions
- Constant initialization and immutability
- Scope resolution, redeclaration, and nested blocks
- Function arity, return types, recursion, nested functions, and closures
- Control-flow conditions, `break`, `continue`, and `return` placement
- Classes, constructors, `this`, inheritance, and member access
- Array homogeneity and index validation
- Dead code after `return`, `break`, or `continue`
