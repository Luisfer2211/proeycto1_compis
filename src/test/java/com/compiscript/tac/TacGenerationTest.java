package com.compiscript.tac;

import com.compiscript.analysis.AnalysisResult;
import com.compiscript.analysis.CompiscriptAnalyzer;
import com.compiscript.errors.ErrorType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacGenerationTest {

    private final CompiscriptAnalyzer analyzer = new CompiscriptAnalyzer();

    @Test
    void emitsDeclarationsAndAssignments() throws IOException {
        TacProgram program = tac("declarations.cps");
        assertTrue(program.contains("alloc a global"));
        assertTrue(program.contains("a = 10"));
        assertTrue(program.contains("alloc PI global"));
        assertTrue(program.contains("a = PI"));
    }

    @Test
    void emitsArithmeticWithFloatPromotion() throws IOException {
        TacProgram program = tac("arithmetic.cps");
        assertTrue(program.contains("cast.i2f"));
        assertTrue(program.contains(" + "));
        assertTrue(program.contains(" = - "));
    }

    @Test
    void emitsShortCircuitLogic() throws IOException {
        TacProgram program = tac("logical.cps");
        assertTrue(program.contains("ifFalse"));
        assertTrue(program.contains(" = not "));
    }

    @Test
    void emitsArrayLoadAndStore() throws IOException {
        TacProgram program = tac("arrays.cps");
        assertTrue(program.contains("newarray 3"));
        assertTrue(program.contains("nums[0]"));
        assertTrue(program.contains("nums[1] = 9"));
    }

    @Test
    void emitsControlFlowJumps() throws IOException {
        TacProgram program = tac("control.cps");
        assertTrue(program.contains("ifFalse"));
        assertTrue(program.contains("goto "));
        assertTrue(program.contains("length "));
        assertTrue(program.contains(" == "));
    }

    @Test
    void emitsFunctionCallsAndParameters() throws IOException {
        TacProgram program = tac("functions.cps");
        assertTrue(program.contains("fn_add:"));
        assertTrue(program.contains("enter fn_add"));
        assertTrue(program.contains("param 1"));
        assertTrue(program.contains("param 2"));
        assertTrue(program.contains("call fn_add, 2"));
        assertTrue(program.contains("return "));
    }

    @Test
    void emitsRecursiveCallToTheSameLabel() throws IOException {
        TacProgram program = tac("recursion.cps");
        assertTrue(program.contains("fn_factorial:"));
        assertTrue(program.contains("call fn_factorial, 1"));
    }

    @Test
    void emitsNestedFunctionWithStaticLink() throws IOException {
        TacProgram program = tac("closures.cps");
        assertTrue(program.contains("makeAdder_inner:"), program.render());
        assertTrue(program.contains("param static_link"), program.render());
        assertTrue(program.contains("call makeAdder_inner, 2"), program.render());
    }

    @Test
    void emitsClassConstructionAndMethodCall() throws IOException {
        TacProgram program = tac("classes.cps");
        assertTrue(program.contains("field Animal.nombre 0"));
        assertTrue(program.contains("Animal_constructor:"));
        assertTrue(program.contains(" = new Animal"));
        assertTrue(program.contains("call Animal_constructor, 2"));
        assertTrue(program.contains("call Animal_hablar, 1"));
        assertTrue(program.contains("this.nombre"));
    }

    @Test
    void emitsInheritedConstructorAndOverriddenMethod() throws IOException {
        TacProgram program = tac("inheritance.cps");
        assertTrue(program.contains("field Animal.nombre 0"), program.render());
        assertTrue(program.contains(" = new Perro"), program.render());
        assertTrue(program.contains("call Animal_constructor, 2"), program.render());
        assertTrue(program.contains("Perro_hablar:"), program.render());
        assertTrue(program.contains("call Perro_hablar, 1"), program.render());
        assertTrue(result("inheritance.cps").symbolRows().stream()
                .anyMatch(row -> row.name().equals("nombre") && row.storage().equals("FIELD") && row.address() == 0));
    }

    @Test
    void emitsTryAndCatchRegion() throws IOException {
        TacProgram program = tac("try_catch.cps");
        assertTrue(program.contains("begin_try "));
        assertTrue(program.contains("end_try"));
        assertTrue(program.contains("err = caught"));
    }

    @Test
    void recyclesTemporariesInArithmetic() throws IOException {
        TacProgram program = tac("temps.cps");
        assertTrue(program.maxLiveTemps() <= 2, program.render());
    }

    @Test
    void suppressesIntermediateCodeWhenSemanticAnalysisFails() {
        AnalysisResult result = analyzer.analyze("let a: integer = true;");
        assertTrue(result.errors().stream().anyMatch(error -> error.type() == ErrorType.SEMANTIC));
        assertTrue(result.intermediateCode().isSuppressed());
        assertEquals("", result.intermediateCode().render());
    }

    @Test
    void storesLocalsInTheFunctionActivationRecord() {
        AnalysisResult result = analyzer.analyze("""
                function add(a: integer): integer {
                  let b: integer = a;
                  return b;
                }
                """);
        assertTrue(result.symbolRows().stream().anyMatch(row -> row.name().equals("a") && row.storage().equals("PARAMETER")));
        assertTrue(result.symbolRows().stream().anyMatch(row -> row.name().equals("b") && row.storage().equals("LOCAL")));
        assertTrue(result.symbolRows().stream().anyMatch(row -> row.name().equals("add") && row.storage().equals("FRAME")));
        assertFalse(result.intermediateCode().isSuppressed());
    }

    private TacProgram tac(String fileName) throws IOException {
        AnalysisResult result = result(fileName);
        assertEquals(0, result.errors().size(), result.errors().toString() + "\n" + result.intermediateCode().render());
        return result.intermediateCode();
    }

    private AnalysisResult result(String fileName) throws IOException {
        Path path = Path.of("src/test/resources/cps/tac", fileName);
        return analyzer.analyze(Files.readString(path, StandardCharsets.UTF_8));
    }
}
