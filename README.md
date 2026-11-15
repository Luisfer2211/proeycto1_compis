# Compiscript Compiler

Lexical, syntax, and semantic analyzer, three-address code, and MIPS generator for the **Compiscript** language (`.cps`). Built with **Java 17 + ANTLR4 + Swing**. The assembly runs in [MARS 4.5](https://dpetersanderson.github.io/MARS/).

Project 03 — Compiler Construction, UVG. The generator reads the TAC and the symbol table. If the program has any error, it produces neither intermediate code nor assembly.

## Requirements

- JDK 17 or newer
- Maven 3.9+

## Build and run

```bash
mvn clean package
java -jar target/compiscript-analyzer-1.0-SNAPSHOT.jar
```

The IDE opens without command-line arguments. Use **Open .cps** to load a file or write code directly in the editor, then press **Analyze**. **Save .asm** writes the MIPS text. The IDE does not execute it.

Download MARS from the link above and run a saved program with:

```bash
java -jar Mars4_5.jar nc programa.asm
```

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
src/main/java/com/compiscript/tac/                     three-address code generator
src/main/java/com/compiscript/mips/                    MIPS generator, getReg, frames, peephole
src/main/java/com/compiscript/analysis/                analysis pipeline
src/main/java/com/compiscript/gui/                     Swing IDE
src/test/resources/cps/semantic/                       semantic test fixtures
src/test/resources/cps/tac/                            intermediate-code fixtures
src/test/java/com/compiscript/mips/                    assembly, spill, and peephole tests
ejemplos/                                              sample .cps files for grading
ejemplos/mips/                                         programs whose console output is checked in MARS
docs/architecture.md                                   implementation notes
docs/intermediate-code.md                              intermediate-language design
docs/code-generation.md                                MIPS convention, getReg, peephole, and MARS
```

## IDE features

- Editable source editor
- File picker for `.cps` files
- Error table (lexical, syntax, semantic)
- AST drawn as boxes and edges
- Symbol table with scope, storage class, and address
- Intermediate code tab. It stays empty when the program has errors
- Assembly tab. It explains that assembly was not generated when the program has errors
- Activation tab with the call tree and the slots of each frame
- Registers tab with every `getReg` decision
- **Save .asm**, which does not run the simulator

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
| `ejemplos/tac/` | Intermediate-code samples for grading |
| `ejemplos/mips/arithmetic_print.cps` | Prints `7` |
| `ejemplos/mips/factorial_print.cps` | Prints `120` |
| `ejemplos/mips/array_print.cps` | Prints `1`, then `9` |
| `ejemplos/mips/animal_print.cps` | Prints `Toby makes noise` |
| `ejemplos/mips/perro_print.cps` | Prints `Toby barks` |
| `ejemplos/mips/try_bounds.cps` | Prints `index out of bounds` |
| `ejemplos/mips/closure_print.cps` | Prints `15` |
| `ejemplos/mips/float_print.cps` | Prints `3.5` |
| `ejemplos/mips/peephole.cps` | Prints `8` |
| `ejemplos/mips/logical_print.cps` | Prints `0`, then `1` |

## Semantic rules implemented

- Type checking for arithmetic, logical, comparison, and assignment expressions
- Constant initialization and immutability
- Scope resolution, redeclaration, and nested blocks
- Function arity, return types, recursion, nested functions, and closures
- Control-flow conditions, `break`, `continue`, and `return` placement
- Classes, constructors, `this`, inheritance, and member access
- Array homogeneity and index validation
- Dead code after `return`, `break`, or `continue`
