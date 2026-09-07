# Architecture

## Analysis pipeline

1. **Lexer** (`CompiscriptLexer`): tokenizes source code. ANTLR continues after unrecognized characters and reports lexical errors through `CollectingErrorListener`.
2. **Parser** (`CompiscriptParser`): builds a parse tree using ANTLR default error recovery so multiple syntax errors can be reported.
3. **Semantic visitor** (`SemanticVisitor`): walks the parse tree, maintains the symbol table, and reports semantic errors through `SemanticErrorCollector`.
4. **IDE** (`MainWindow`): displays errors, AST, and symbol table rows in Swing panels.

## Symbol table

- Implemented as a stack of `Scope` objects (`global`, `block`, `function`, `class`, `loop`, `catch`).
- `define` inserts symbols and detects redeclarations in the current scope.
- `resolve` walks from the current scope to parents for identifier lookup (supports closures).
- Class member lookup uses `resolveInClassHierarchy` for inheritance.
- Assignments call `updateInitialized` to track mutable symbol state.

## Semantic visitor

- Extends `CompiscriptBaseVisitor<CompiscriptType>`.
- Each expression node returns a `CompiscriptType`.
- On errors, nodes return `CompiscriptType.error()` and analysis continues.
- Duplicate errors are suppressed by line, column, and message.
- Unreachable code is tracked after `return`, `break`, and `continue`.

## Type system

- Primitive types: `integer`, `boolean`, `string`, `null`, `void`
- Composite types: arrays and class types
- Function types store parameter and return types for call validation
- `+` supports integer arithmetic and string concatenation

## Testing strategy

- JUnit 5 tests load `.cps` fixtures from `src/test/resources/cps/semantic`
- Each test class covers one semantic rule family with valid and invalid programs
- Lab examples in `ejemplos/` verify lexical and syntax behavior is preserved

## GUI layout

- Top bar: Open, Save, Analyze, current file label
- Center split: editor on top, tabbed results below
- Tabs: Errors, AST (`JTree`), Symbol Table
- Bottom banner: summary counts by error category
