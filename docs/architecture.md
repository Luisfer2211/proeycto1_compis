# Architecture

## Analysis pipeline

1. **Lexer** (`CompiscriptLexer`): tokenizes source code. ANTLR continues after unrecognized characters and reports lexical errors through `CollectingErrorListener`.
2. **Parser** (`CompiscriptParser`): builds a parse tree using ANTLR default error recovery so multiple syntax errors can be reported.
3. **Semantic visitor** (`SemanticVisitor`): walks the parse tree, maintains the symbol table, and reports semantic errors through `SemanticErrorCollector`.
4. **TAC generator** (`TacGenerator`): runs only when the error list is empty and emits three-address code. Each procedure ends with `leave`.
5. **MIPS generator** (`MipsGenerator`): translates those TAC lines. It allocates `$t` registers with `getReg`, builds activation frames, and runs six peephole rewrites. It does not walk the parse tree.
6. **IDE** (`MainWindow`): displays errors, a drawn AST, the symbol table, the intermediate code, the MIPS text, the activation tree, and the register decisions.

## Symbol table

- Implemented as a stack of `Scope` objects (`global`, `block`, `function`, `class`, `loop`, `catch`).
- `define` inserts symbols and detects redeclarations in the current scope.
- `resolve` walks from the current scope to parents for identifier lookup (supports closures).
- Class member lookup uses `resolveInClassHierarchy` for inheritance.
- Assignments call `updateInitialized` to track mutable symbol state.
- Each symbol stores a storage class (`GLOBAL`, `LOCAL`, `PARAMETER`, `FIELD`, `FRAME`) and an address.
- Functions store an `ActivationRecord` with the entry label and frame size. Nested functions record a static link.

## Semantic visitor

- Extends `CompiscriptBaseVisitor<CompiscriptType>`.
- Each expression node returns a `CompiscriptType`.
- On errors, nodes return `CompiscriptType.error()` and analysis continues.
- Duplicate errors are suppressed by line, column, and message.
- Unreachable code is tracked after `return`, `break`, and `continue`.

## Type system

- Primitive types: `integer`, `float`, `boolean`, `string`, `null`, `void`
- Composite types: arrays and class types
- Function types store parameter and return types for call validation
- `+` supports integer/float arithmetic, integer-to-float promotion, and string concatenation

## Testing strategy

- JUnit 5 tests load `.cps` fixtures from `src/test/resources/cps/semantic` and `src/test/resources/cps/tac`
- Semantic tests cover each rule family with valid and invalid programs
- TAC tests assert concrete instructions for declarations, expressions, control flow, functions, recursion, classes, inheritance, and `try/catch`
- MIPS tests cover register allocation, the six peephole rewrites, every rubric construct, and a program with errors, which must suppress both TAC and assembly
- Lab examples in `ejemplos/` and `ejemplos/mips/` are the files used during grading

## GUI layout

- Top bar: Open, Save, Save .asm, Analyze, current file label
- Center split: editor on top, tabbed results below
- Tabs: Errors, AST (drawn boxes and edges), Symbol Table (including Storage and Address), Intermediate Code, Assembly, Activation, Registers
- Bottom banner: summary counts by error category
