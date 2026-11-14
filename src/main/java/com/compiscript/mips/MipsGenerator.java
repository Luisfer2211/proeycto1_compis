package com.compiscript.mips;

import com.compiscript.symbols.SymbolTable;
import com.compiscript.tac.TacProgram;
import com.compiscript.types.CompiscriptType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates three-address lines into MIPS for MARS. It does not walk the parse tree.
 * Words are 4 bytes. Slot {@code n} of the current frame is {@code n * 4($fp)}.
 * {@code $ra} and the caller's {@code $fp} sit immediately after those slots.
 */
public final class MipsGenerator {

    static final int INT_SPILLS = 16;
    static final int FLOAT_SLOTS = 16;
    static final int RESERVE = INT_SPILLS + FLOAT_SLOTS;

    private static final Pattern INDEX_LOAD = Pattern.compile("^(\\S+) = (\\S+)\\[(.+)]$");
    private static final Pattern INDEX_STORE = Pattern.compile("^(\\S+)\\[(.+)] = (.+)$");
    private static final Pattern FIELD_LOAD = Pattern.compile("^(\\S+) = (\\S+)\\.([A-Za-z_][A-Za-z0-9_]*)$");
    private static final Pattern FIELD_STORE = Pattern.compile("^(\\S+)\\.([A-Za-z_][A-Za-z0-9_]*) = (.+)$");
    private static final String[] OPERATORS = {"<=", ">=", "==", "!=", "<", ">", "+", "-", "*", "/", "%"};

    private final RegisterAllocator allocator;
    private final List<String> output = new ArrayList<>();
    private final Map<String, String> stringLabels = new LinkedHashMap<>();
    private final Map<String, String> floatLabels = new LinkedHashMap<>();
    private final Map<String, ValueKind> types = new LinkedHashMap<>();
    private final Map<String, String> classNames = new LinkedHashMap<>();
    private final Map<String, Integer> floatHome = new LinkedHashMap<>();
    private final Set<String> pinned = new LinkedHashSet<>();
    private final List<String> scratches = new ArrayList<>();

    private ProgramLayout layout;
    private ProgramLayout.FuncEnv env;
    private int frameSize;
    private int lineIndex;
    private int scratchId;
    private int labelId;
    private int nextFloat;
    private int pushedParams;
    private final List<ValueKind> paramKinds = new ArrayList<>();

    public MipsGenerator() {
        this(10);
    }

    public MipsGenerator(int registerCount) {
        this.allocator = new RegisterAllocator(registerCount);
    }

    public MipsProgram generate(TacProgram program, SymbolTable table) {
        if (program == null || program.isSuppressed()) {
            return MipsProgram.suppressed();
        }
        output.clear();
        stringLabels.clear();
        floatLabels.clear();
        layout = ProgramLayout.from(table);
        List<String> lines = program.lines();
        collectStrings(lines);
        collectFloats(lines);
        List<Procedure> procedures = new ArrayList<>();
        List<String> main = new ArrayList<>();
        parseTop(lines, procedures, main);

        emitData();
        emit(".text");
        emit("main:");
        emit("j __main");
        for (Procedure procedure : procedures) {
            emitProcedure(procedure);
        }
        emit("__main:");
        emitPrologue(0);
        beginFragment("__main", null, 0);
        emitSteps(main);
        emitEpilogue(0);
        emit("li $v0, 10");
        emit("syscall");
        emitRuntime();

        List<String> optimized = new PeepholeOptimizer().optimize(output);
        ActivationNode tree = new ActivationTreeBuilder().build(lines, table);
        return MipsProgram.of(String.join("\n", optimized) + "\n", allocator.decisions(), tree);
    }

    private void emitProcedure(Procedure procedure) {
        for (Procedure nested : procedure.nested) {
            emitProcedure(nested);
        }
        ProgramLayout.FuncEnv function = layout.functions.get(procedure.name);
        int size = function == null ? procedure.size : function.frameSize;
        emit(procedure.name + ":");
        emitPrologue(size);
        copyParameters(function, size);
        beginFragment(procedure.name, function, size);
        emitSteps(procedure.body);
        emitEpilogue(size);
        emit("jr $ra");
    }

    private void beginFragment(String name, ProgramLayout.FuncEnv function, int size) {
        allocator.reset();
        allocator.setFunction(name);
        floatHome.clear();
        nextFloat = 0;
        types.clear();
        classNames.clear();
        env = function;
        frameSize = size;
        pushedParams = 0;
        paramKinds.clear();
    }

    private void emitSteps(List<String> lines) {
        Map<String, List<Integer>> uses = uses(lines);
        for (int i = 0; i < lines.size(); i++) {
            lineIndex = i;
            pinned.clear();
            scratches.clear();
            bindUses(uses, i);
            translate(lines.get(i));
            for (String scratch : scratches) {
                allocator.release(scratch);
            }
        }
    }

    private void translate(String line) {
        if (line.isBlank() || line.startsWith("//") || line.startsWith("field ") || line.startsWith("alloc ")
                || line.startsWith("enter ") || line.startsWith("leave ")) {
            return;
        }
        if (line.startsWith("begin_try ")) {
            String handler = line.substring("begin_try ".length()).trim();
            emit("la $v1, " + handler);
            emit("lw $a0, __hsp");
            emit("la $a1, __hstack");
            emit("add $a1, $a1, $a0");
            emit("sw $v1, 0($a1)");
            emit("addi $a0, $a0, 4");
            emit("sw $a0, __hsp");
            return;
        }
        if (line.equals("end_try")) {
            emit("lw $v1, __hsp");
            emit("addi $v1, $v1, -4");
            emit("sw $v1, __hsp");
            return;
        }
        if (line.startsWith("ifFalse ")) {
            String condition = line.substring("ifFalse ".length(), line.indexOf(" goto ")).trim();
            String label = line.substring(line.lastIndexOf(' ') + 1);
            emit("beq " + materialize(condition) + ", $zero, " + label);
            return;
        }
        if (line.startsWith("goto ")) {
            emit("j " + line.substring("goto ".length()).trim());
            return;
        }
        if (line.endsWith(":") && !line.contains(" ")) {
            emit(line);
            return;
        }
        if (line.startsWith("param ")) {
            pushParam(line.substring("param ".length()).trim());
            return;
        }
        if (line.startsWith("return")) {
            emitReturn(line.equals("return") ? null : line.substring("return ".length()).trim());
            return;
        }
        if (line.startsWith("call ") || line.contains(" = call ")) {
            emitCall(line);
            return;
        }
        Matcher indexStore = INDEX_STORE.matcher(line);
        if (indexStore.matches()) {
            emitIndexStore(indexStore.group(1), indexStore.group(2), indexStore.group(3));
            return;
        }
        Matcher indexLoad = INDEX_LOAD.matcher(line);
        if (indexLoad.matches()) {
            emitIndexLoad(indexLoad.group(1), indexLoad.group(2), indexLoad.group(3));
            return;
        }
        Matcher fieldStore = FIELD_STORE.matcher(line);
        if (fieldStore.matches()) {
            emitFieldStore(fieldStore.group(1), fieldStore.group(2), fieldStore.group(3));
            return;
        }
        Matcher fieldLoad = FIELD_LOAD.matcher(line);
        if (fieldLoad.matches()) {
            emitFieldLoad(fieldLoad.group(1), fieldLoad.group(2), fieldLoad.group(3));
            return;
        }
        int eq = line.indexOf(" = ");
        if (eq < 0) {
            throw new IllegalArgumentException("Unrecognized TAC: " + line);
        }
        String dest = line.substring(0, eq).trim();
        String rhs = line.substring(eq + 3).trim();
        if (rhs.startsWith("newarray ")) {
            emitNewArray(dest, Integer.parseInt(rhs.substring("newarray ".length()).trim()));
            return;
        }
        if (rhs.startsWith("new ")) {
            emitNew(dest, rhs.substring("new ".length()).trim());
            return;
        }
        if (rhs.startsWith("length ")) {
            emitLength(dest, rhs.substring("length ".length()).trim());
            return;
        }
        if (rhs.startsWith("cast.i2f ")) {
            emitCast(dest, rhs.substring("cast.i2f ".length()).trim());
            return;
        }
        if (rhs.startsWith("not ")) {
            emitNot(dest, rhs.substring("not ".length()).trim());
            return;
        }
        if (rhs.startsWith("- ")) {
            emitNeg(dest, rhs.substring(2).trim());
            return;
        }
        int concat = indexOfOperator(rhs, " concat ");
        if (concat >= 0) {
            emitConcat(dest, rhs.substring(0, concat).trim(), rhs.substring(concat + " concat ".length()).trim());
            return;
        }
        Binary binary = splitBinary(rhs);
        if (binary != null) {
            emitBinary(dest, binary.left, binary.op, binary.right);
            return;
        }
        emitCopy(dest, rhs);
    }

    private void emitCopy(String dest, String source) {
        if (isTemporary(dest)) {
            if (floating(source)) {
                loadFloat(source, "$f4");
                storeFloatTemp(dest, "$f4");
            } else {
                String register = define(dest);
                loadWord(source, register);
                remember(dest, source);
            }
            return;
        }
        if (floating(source) || floating(dest)) {
            loadFloat(source, "$f4");
            storeFloatName(dest, "$f4");
        } else {
            String register = materialize(source);
            storeWord(dest, register);
        }
    }

    private void emitBinary(String dest, String left, String op, String right) {
        if (isCompare(op)) {
            emitCompare(dest, left, op, right);
            return;
        }
        if (floating(left) || floating(right)) {
            emitFloatBinary(dest, left, op, right);
            return;
        }
        if ("+".equals(op) && "0".equals(right)) {
            String leftReg = materialize(left);
            String destReg = define(dest);
            emit("add " + destReg + ", " + leftReg + ", $zero");
            types.put(dest, ValueKind.INT);
            return;
        }
        if ("*".equals(op) && isInteger(right)) {
            String leftReg = materialize(left);
            String destReg = define(dest);
            emit("mul " + destReg + ", " + leftReg + ", " + right);
            types.put(dest, ValueKind.INT);
            return;
        }
        if (("+".equals(op) || "-".equals(op)) && isInteger(right)) {
            int immediate = Integer.parseInt(right);
            if ("-".equals(op)) {
                immediate = -immediate;
            }
            String leftReg = materialize(left);
            String destReg = define(dest);
            emit("addi " + destReg + ", " + leftReg + ", " + immediate);
            types.put(dest, ValueKind.INT);
            return;
        }
        String leftReg = materialize(left);
        String rightReg = materialize(right);
        String destReg = define(dest);
        switch (op) {
            case "+" -> emit("add " + destReg + ", " + leftReg + ", " + rightReg);
            case "-" -> emit("sub " + destReg + ", " + leftReg + ", " + rightReg);
            case "*" -> emit("mul " + destReg + ", " + leftReg + ", " + rightReg);
            case "/" -> {
                emit("div " + leftReg + ", " + rightReg);
                emit("mflo " + destReg);
            }
            case "%" -> {
                emit("div " + leftReg + ", " + rightReg);
                emit("mfhi " + destReg);
            }
            default -> throw new IllegalArgumentException(op);
        }
        types.put(dest, ValueKind.INT);
    }

    private void emitCompare(String dest, String left, String op, String right) {
        if (floating(left) || floating(right)) {
            loadFloat(left, "$f4");
            loadFloat(right, "$f6");
            String trueLabel = fresh("ft");
            String endLabel = fresh("fe");
            String condition = switch (op) {
                case "<" -> "c.lt.s $f4, $f6";
                case ">" -> "c.lt.s $f6, $f4";
                case "<=" -> "c.le.s $f4, $f6";
                case ">=" -> "c.le.s $f6, $f4";
                case "==" -> "c.eq.s $f4, $f6";
                default -> "c.eq.s $f4, $f6";
            };
            emit(condition);
            if ("!=".equals(op)) {
                emit("bc1t " + trueLabel);
                emit("li $v1, 1");
                emit("j " + endLabel);
                emit(trueLabel + ":");
                emit("li $v1, 0");
            } else {
                emit("bc1f " + trueLabel);
                emit("li $v1, 1");
                emit("j " + endLabel);
                emit(trueLabel + ":");
                emit("li $v1, 0");
            }
            emit(endLabel + ":");
            String destReg = define(dest);
            emit("move " + destReg + ", $v1");
            types.put(dest, ValueKind.INT);
            return;
        }
        String leftReg = materialize(left);
        String rightReg = materialize(right);
        String destReg = define(dest);
        switch (op) {
            case "<" -> emit("slt " + destReg + ", " + leftReg + ", " + rightReg);
            case ">" -> emit("slt " + destReg + ", " + rightReg + ", " + leftReg);
            case "<=" -> {
                emit("slt " + destReg + ", " + rightReg + ", " + leftReg);
                emit("xori " + destReg + ", " + destReg + ", 1");
            }
            case ">=" -> {
                emit("slt " + destReg + ", " + leftReg + ", " + rightReg);
                emit("xori " + destReg + ", " + destReg + ", 1");
            }
            case "==" -> {
                emit("sub " + destReg + ", " + leftReg + ", " + rightReg);
                emit("sltiu " + destReg + ", " + destReg + ", 1");
            }
            case "!=" -> {
                emit("sub " + destReg + ", " + leftReg + ", " + rightReg);
                emit("sltu " + destReg + ", $zero, " + destReg);
            }
            default -> throw new IllegalArgumentException(op);
        }
        types.put(dest, ValueKind.INT);
    }

    private void emitFloatBinary(String dest, String left, String op, String right) {
        loadFloat(promote(left), "$f4");
        loadFloat(promote(right), "$f6");
        switch (op) {
            case "+" -> emit("add.s $f4, $f4, $f6");
            case "-" -> emit("sub.s $f4, $f4, $f6");
            case "*" -> emit("mul.s $f4, $f4, $f6");
            case "/" -> emit("div.s $f4, $f4, $f6");
            default -> throw new IllegalArgumentException("float " + op);
        }
        storeFloatTemp(dest, "$f4");
    }

    private String promote(String operand) {
        if (floating(operand)) {
            return operand;
        }
        String register = materialize(operand);
        emit("mtc1 " + register + ", $f8");
        emit("cvt.s.w $f8, $f8");
        String temp = "__f" + scratchId++;
        storeFloatTemp(temp, "$f8");
        return temp;
    }

    private void emitNeg(String dest, String source) {
        if (floating(source)) {
            loadFloat(source, "$f4");
            emit("neg.s $f4, $f4");
            storeFloatTemp(dest, "$f4");
            return;
        }
        String register = materialize(source);
        String destReg = define(dest);
        emit("sub " + destReg + ", $zero, " + register);
        types.put(dest, ValueKind.INT);
    }

    private void emitNot(String dest, String source) {
        String register = materialize(source);
        String destReg = define(dest);
        emit("xori " + destReg + ", " + register + ", 1");
        types.put(dest, ValueKind.INT);
    }

    private void emitCast(String dest, String source) {
        String register = materialize(source);
        emit("mtc1 " + register + ", $f4");
        emit("cvt.s.w $f4, $f4");
        storeFloatTemp(dest, "$f4");
    }

    private void emitConcat(String dest, String left, String right) {
        String leftReg = materialize(left);
        String rightReg = materialize(right);
        emit("move $a0, " + leftReg);
        emit("move $a1, " + rightReg);
        for (String store : allocator.spillAll()) {
            emit(store);
        }
        emit("jal __concat");
        String destReg = define(dest);
        emit("move " + destReg + ", $v0");
        types.put(dest, ValueKind.STRING);
    }

    private void emitNewArray(String dest, int length) {
        emit("li $a0, " + ((length + 1) * 4));
        emit("li $v0, 9");
        emit("syscall");
        String destReg = define(dest);
        emit("move " + destReg + ", $v0");
        emit("li $v1, " + length);
        emit("sw $v1, 0(" + destReg + ")");
        types.put(dest, ValueKind.ADDR);
    }

    private void emitNew(String dest, String className) {
        int words = layout.fieldWords(className);
        emit("li $a0, " + (words * 4));
        emit("li $v0, 9");
        emit("syscall");
        String destReg = define(dest);
        emit("move " + destReg + ", $v0");
        String loop = fresh("zero");
        String done = fresh("zerod");
        emit("move $a1, " + destReg);
        emit("li $a2, " + words);
        emit(loop + ":");
        emit("beq $a2, $zero, " + done);
        emit("sw $zero, 0($a1)");
        emit("addi $a1, $a1, 4");
        emit("addi $a2, $a2, -1");
        emit("j " + loop);
        emit(done + ":");
        types.put(dest, ValueKind.ADDR);
        classNames.put(dest, className);
    }

    private void emitLength(String dest, String array) {
        String base = materialize(array);
        if (isTemporary(dest)) {
            String destReg = define(dest);
            emit("lw " + destReg + ", 0(" + base + ")");
            types.put(dest, ValueKind.INT);
            return;
        }
        emit("lw $v0, 0(" + base + ")");
        storeWord(dest, "$v0");
    }

    private void emitIndexLoad(String dest, String array, String index) {
        String base = materialize(array);
        String indexReg = materialize(index);
        emitBounds(base, indexReg);
        emit("sll $v1, " + indexReg + ", 2");
        emit("add $v1, $v1, " + base);
        emit("addi $v1, $v1, 4");
        ValueKind element = elementKind(array);
        if (element == ValueKind.FLOAT) {
            emit("lwc1 $f4, 0($v1)");
            if (isTemporary(dest)) {
                storeFloatTemp(dest, "$f4");
            } else {
                storeFloatName(dest, "$f4");
            }
            return;
        }
        if (isTemporary(dest)) {
            String destReg = define(dest);
            emit("lw " + destReg + ", 0($v1)");
            types.put(dest, element);
            return;
        }
        emit("lw $v0, 0($v1)");
        storeWord(dest, "$v0");
    }

    private void emitIndexStore(String array, String index, String value) {
        String base = materialize(array);
        String indexReg = materialize(index);
        emitBounds(base, indexReg);
        emit("sll $v1, " + indexReg + ", 2");
        emit("add $v1, $v1, " + base);
        emit("addi $v1, $v1, 4");
        if (floating(value)) {
            loadFloat(value, "$f4");
            emit("swc1 $f4, 0($v1)");
            return;
        }
        String valueReg = materialize(value);
        emit("sw " + valueReg + ", 0($v1)");
    }

    private void emitBounds(String base, String index) {
        emit("lw $a3, 0(" + base + ")");
        emit("bltz " + index + ", __bounds_fail");
        emit("bge " + index + ", $a3, __bounds_fail");
    }

    private void emitFieldLoad(String dest, String object, String field) {
        Integer offset = layout.fieldOffset(classOf(object), field);
        if (offset == null) {
            throw new IllegalArgumentException("Unknown field " + field);
        }
        String base = materialize(object);
        CompiscriptType fieldType = fieldType(classOf(object), field);
        int byteOffset = offset * 4;
        if (fieldType != null && fieldType.kind() == CompiscriptType.Kind.FLOAT) {
            emit("lwc1 $f4, " + byteOffset + "(" + base + ")");
            if (isTemporary(dest)) {
                storeFloatTemp(dest, "$f4");
            } else {
                storeFloatName(dest, "$f4");
            }
            return;
        }
        ValueKind kind = ValueKind.INT;
        if (fieldType != null && fieldType.kind() == CompiscriptType.Kind.STRING) {
            kind = ValueKind.STRING;
        } else if (fieldType != null && fieldType.kind() == CompiscriptType.Kind.CLASS) {
            kind = ValueKind.ADDR;
        }
        if (isTemporary(dest)) {
            String destReg = define(dest);
            emit("lw " + destReg + ", " + byteOffset + "(" + base + ")");
            types.put(dest, kind);
            if (kind == ValueKind.ADDR && fieldType != null) {
                classNames.put(dest, fieldType.className());
            }
            return;
        }
        emit("lw $v0, " + byteOffset + "(" + base + ")");
        storeWord(dest, "$v0");
    }

    private void emitFieldStore(String object, String field, String value) {
        Integer offset = layout.fieldOffset(classOf(object), field);
        if (offset == null) {
            throw new IllegalArgumentException("Unknown field " + field);
        }
        String base = materialize(object);
        if (floating(value)) {
            loadFloat(value, "$f4");
            emit("swc1 $f4, " + (offset * 4) + "(" + base + ")");
            return;
        }
        String valueReg = materialize(value);
        emit("sw " + valueReg + ", " + (offset * 4) + "(" + base + ")");
    }

    private void pushParam(String operand) {
        paramKinds.add(kindOf(operand));
        pushedParams++;
        if ("static_link".equals(operand)) {
            emit("addi $sp, $sp, -4");
            emit("sw $fp, 0($sp)");
            return;
        }
        if (floating(operand)) {
            loadFloat(operand, "$f4");
            emit("addi $sp, $sp, -4");
            emit("swc1 $f4, 0($sp)");
            return;
        }
        String register = materialize(operand);
        emit("addi $sp, $sp, -4");
        emit("sw " + register + ", 0($sp)");
    }

    private void emitCall(String line) {
        String dest = null;
        String rest = line;
        int eq = line.indexOf(" = call ");
        if (eq >= 0) {
            dest = line.substring(0, eq).trim();
            rest = line.substring(eq + 3).trim();
        }
        String callee = rest.substring("call ".length(), rest.indexOf(',')).trim();
        int argc = pushedParams;
        if ("print".equals(callee)) {
            emitPrint(paramKinds.isEmpty() ? ValueKind.INT : paramKinds.get(paramKinds.size() - 1));
            pushedParams = 0;
            paramKinds.clear();
            return;
        }
        for (String store : allocator.spillAll()) {
            emit(store);
        }
        reverseArgs(argc);
        emit("jal " + callee);
        if (argc > 0) {
            emit("addi $sp, $sp, " + (argc * 4));
        }
        pushedParams = 0;
        paramKinds.clear();
        if (dest != null) {
            ProgramLayout.FuncEnv calleeEnv = layout.functions.get(callee);
            if (calleeEnv != null && calleeEnv.type != null && calleeEnv.type.returnType() != null
                    && calleeEnv.type.returnType().kind() == CompiscriptType.Kind.FLOAT) {
                storeFloatTemp(dest, "$f0");
            } else {
                String destReg = define(dest);
                emit("move " + destReg + ", $v0");
                types.put(dest, returnKind(calleeEnv));
                if (returnKind(calleeEnv) == ValueKind.ADDR && calleeEnv != null && calleeEnv.className != null) {
                    classNames.put(dest, calleeEnv.className);
                }
            }
        }
    }

    private void emitPrint(ValueKind kind) {
        if (kind == ValueKind.FLOAT) {
            emit("lwc1 $f12, 0($sp)");
            emit("addi $sp, $sp, 4");
            emit("li $v0, 2");
        } else if (kind == ValueKind.STRING) {
            emit("lw $a0, 0($sp)");
            emit("addi $sp, $sp, 4");
            emit("li $v0, 4");
        } else {
            emit("lw $a0, 0($sp)");
            emit("addi $sp, $sp, 4");
            emit("li $v0, 1");
        }
        emit("syscall");
        emit("li $a0, 10");
        emit("li $v0, 11");
        emit("syscall");
    }

    private void emitReturn(String value) {
        if (value != null) {
            if (floating(value)) {
                loadFloat(value, "$f0");
            } else {
                String register = materialize(value);
                emit("move $v0, " + register);
            }
        }
        emitEpilogue(frameSize);
        emit("jr $ra");
    }

    private void reverseArgs(int count) {
        if (count <= 1) {
            return;
        }
        String loop = fresh("rev");
        String done = fresh("revd");
        emit("li $a1, 0");
        emit("li $a2, " + (count - 1));
        emit(loop + ":");
        emit("bge $a1, $a2, " + done);
        emit("sll $t0, $a1, 2");
        emit("add $t0, $t0, $sp");
        emit("sll $t1, $a2, 2");
        emit("add $t1, $t1, $sp");
        emit("lw $a3, 0($t0)");
        emit("lw $v1, 0($t1)");
        emit("sw $v1, 0($t0)");
        emit("sw $a3, 0($t1)");
        emit("addi $a1, $a1, 1");
        emit("addi $a2, $a2, -1");
        emit("j " + loop);
        emit(done + ":");
    }

    private String materialize(String operand) {
        if ("static_link".equals(operand)) {
            String scratch = scratch();
            emit("move " + scratch + ", $fp");
            return scratch;
        }
        if (isTemporary(operand)) {
            return reg(operand);
        }
        String scratch = scratch();
        loadWord(operand, scratch);
        return scratch;
    }

    private String define(String dest) {
        if (!pinned.contains(dest)) {
            allocator.discardMemory(dest);
        }
        return reg(dest);
    }

    private String reg(String temporary) {
        RegisterAllocator.Allocation allocation = allocator.getReg(temporary, lineIndex, pinned);
        if (allocation.spillBefore() != null) {
            emit(allocation.spillBefore());
        }
        if (allocation.reload() != null) {
            emit(allocation.reload());
        }
        pinned.add(temporary);
        return allocation.register();
    }

    private String scratch() {
        String name = "__s" + scratchId++;
        scratches.add(name);
        allocator.setNextUse(name, Integer.MAX_VALUE);
        return reg(name);
    }

    private void loadWord(String operand, String register) {
        if ("null".equals(operand)) {
            emit("li " + register + ", 0");
            return;
        }
        if ("caught".equals(operand)) {
            emit("lw " + register + ", __caught");
            return;
        }
        if (isInteger(operand)) {
            emit("li " + register + ", " + operand);
            return;
        }
        if (isString(operand)) {
            emit("la " + register + ", " + stringLabels.get(unquote(operand)));
            return;
        }
        if (isTemporary(operand)) {
            String home = reg(operand);
            if (!home.equals(register)) {
                emit("move " + register + ", " + home);
            }
            return;
        }
        ProgramLayout.Location location = layout.locate(env, operand);
        if (location == null) {
            throw new IllegalArgumentException("Unknown name " + operand);
        }
        if (location.global()) {
            emit("lw " + register + ", g_" + operand);
            return;
        }
        if (location.depth() == 0) {
            emit("lw " + register + ", " + (location.slot().address * 4) + "($fp)");
            return;
        }
        follow(location.depth());
        emit("lw " + register + ", " + (location.slot().address * 4) + "($a3)");
    }

    private void storeWord(String name, String register) {
        ProgramLayout.Location location = layout.locate(env, name);
        if (location == null) {
            throw new IllegalArgumentException("Unknown name " + name);
        }
        if (location.global()) {
            emit("sw " + register + ", g_" + name);
            return;
        }
        if (location.depth() == 0) {
            emit("sw " + register + ", " + (location.slot().address * 4) + "($fp)");
            return;
        }
        follow(location.depth());
        emit("sw " + register + ", " + (location.slot().address * 4) + "($a3)");
    }

    private void loadFloat(String operand, String register) {
        if (isFloatLiteral(operand)) {
            emit("l.s " + register + ", " + floatLabels.get(operand));
            return;
        }
        if (isTemporary(operand)) {
            Integer slot = floatHome.get(operand);
            if (slot == null) {
                throw new IllegalArgumentException("Float temporary has no home: " + operand);
            }
            emit("lwc1 " + register + ", " + floatOffset(slot) + "($fp)");
            return;
        }
        ProgramLayout.Location location = layout.locate(env, operand);
        if (location == null) {
            throw new IllegalArgumentException("Unknown float " + operand);
        }
        if (location.global()) {
            emit("lwc1 " + register + ", g_" + operand);
            return;
        }
        if (location.depth() == 0) {
            emit("lwc1 " + register + ", " + (location.slot().address * 4) + "($fp)");
            return;
        }
        follow(location.depth());
        emit("lwc1 " + register + ", " + (location.slot().address * 4) + "($a3)");
    }

    private void storeFloatTemp(String dest, String register) {
        int slot = floatHome.computeIfAbsent(dest, key -> {
            if (nextFloat >= FLOAT_SLOTS) {
                throw new IllegalStateException("Out of float slots");
            }
            return nextFloat++;
        });
        emit("swc1 " + register + ", " + floatOffset(slot) + "($fp)");
        types.put(dest, ValueKind.FLOAT);
        allocator.release(dest);
        allocator.discardMemory(dest);
    }

    private void storeFloatName(String name, String register) {
        ProgramLayout.Location location = layout.locate(env, name);
        if (location == null) {
            throw new IllegalArgumentException("Unknown float " + name);
        }
        if (location.global()) {
            emit("swc1 " + register + ", g_" + name);
            return;
        }
        if (location.depth() == 0) {
            emit("swc1 " + register + ", " + (location.slot().address * 4) + "($fp)");
            return;
        }
        follow(location.depth());
        emit("swc1 " + register + ", " + (location.slot().address * 4) + "($a3)");
    }

    private void follow(int depth) {
        emit("move $a3, $fp");
        for (int i = 0; i < depth; i++) {
            emit("lw $a3, 0($a3)");
        }
    }

    private void emitPrologue(int size) {
        emit("move $v1, $fp");
        emit("addi $sp, $sp, " + (-(size + 2 + RESERVE) * 4));
        emit("addi $fp, $sp, " + (RESERVE * 4));
        emit("sw $ra, " + (size * 4) + "($fp)");
        emit("sw $v1, " + ((size + 1) * 4) + "($fp)");
    }

    private void emitEpilogue(int size) {
        emit("lw $ra, " + (size * 4) + "($fp)");
        emit("lw $v1, " + ((size + 1) * 4) + "($fp)");
        emit("addi $sp, $fp, " + ((size + 2) * 4));
        emit("move $fp, $v1");
    }

    private void copyParameters(ProgramLayout.FuncEnv function, int size) {
        if (function == null) {
            return;
        }
        function.slots.values().stream()
                .filter(slot -> slot.storage == com.compiscript.symbols.StorageClass.PARAMETER)
                .sorted(java.util.Comparator.comparingInt(slot -> slot.address))
                .forEach(slot -> {
                    int source = (size + 2 + slot.address) * 4;
                    int target = slot.address * 4;
                    emit("lw $v1, " + source + "($fp)");
                    emit("sw $v1, " + target + "($fp)");
                });
    }

    private void emitData() {
        emit(".data");
        emit("__hsp: .word 0");
        emit("__caught: .word 0");
        emit("__hstack: .space 256");
        emit("__msg_bounds: .asciiz \"index out of bounds\"");
        layout.globals.values().stream()
                .sorted(java.util.Comparator.comparingInt(slot -> slot.address))
                .forEach(slot -> emit("g_" + slot.name + ": .word 0"));
        for (Map.Entry<String, String> entry : stringLabels.entrySet()) {
            emit(entry.getValue() + ": .asciiz \"" + escape(entry.getKey()) + "\"");
        }
        for (Map.Entry<String, String> entry : floatLabels.entrySet()) {
            emit(entry.getValue() + ": .float " + entry.getKey());
        }
    }

    private void emitRuntime() {
        emit("__strlen:");
        emit("li $v0, 0");
        emit("__strlen_loop:");
        emit("lb $t0, 0($a0)");
        emit("beq $t0, $zero, __strlen_done");
        emit("addi $v0, $v0, 1");
        emit("addi $a0, $a0, 1");
        emit("j __strlen_loop");
        emit("__strlen_done:");
        emit("jr $ra");
        emit("__concat:");
        emit("addi $sp, $sp, -16");
        emit("sw $ra, 12($sp)");
        emit("sw $a0, 8($sp)");
        emit("sw $a1, 4($sp)");
        emit("jal __strlen");
        emit("sw $v0, 0($sp)");
        emit("lw $a0, 4($sp)");
        emit("jal __strlen");
        emit("lw $t1, 0($sp)");
        emit("add $t2, $t1, $v0");
        emit("addi $a0, $t2, 1");
        emit("li $v0, 9");
        emit("syscall");
        emit("sw $v0, 0($sp)");
        emit("move $t3, $v0");
        emit("lw $t4, 8($sp)");
        emit("__copy_left:");
        emit("lb $t5, 0($t4)");
        emit("beq $t5, $zero, __copy_right_start");
        emit("sb $t5, 0($t3)");
        emit("addi $t4, $t4, 1");
        emit("addi $t3, $t3, 1");
        emit("j __copy_left");
        emit("__copy_right_start:");
        emit("lw $t4, 4($sp)");
        emit("__copy_right:");
        emit("lb $t5, 0($t4)");
        emit("sb $t5, 0($t3)");
        emit("beq $t5, $zero, __concat_done");
        emit("addi $t4, $t4, 1");
        emit("addi $t3, $t3, 1");
        emit("j __copy_right");
        emit("__concat_done:");
        emit("lw $v0, 0($sp)");
        emit("lw $ra, 12($sp)");
        emit("addi $sp, $sp, 16");
        emit("jr $ra");
        emit("__bounds_fail:");
        emit("lw $v1, __hsp");
        emit("beq $v1, $zero, __bounds_abort");
        emit("addi $v1, $v1, -4");
        emit("sw $v1, __hsp");
        emit("la $a0, __hstack");
        emit("add $a0, $a0, $v1");
        emit("lw $a0, 0($a0)");
        emit("la $v1, __msg_bounds");
        emit("sw $v1, __caught");
        emit("jr $a0");
        emit("__bounds_abort:");
        emit("la $a0, __msg_bounds");
        emit("li $v0, 4");
        emit("syscall");
        emit("li $a0, 10");
        emit("li $v0, 11");
        emit("syscall");
        emit("li $v0, 10");
        emit("syscall");
    }

    private void remember(String dest, String source) {
        types.put(dest, kindOf(source));
        if (classNames.containsKey(source)) {
            classNames.put(dest, classNames.get(source));
        }
    }

    private ValueKind kindOf(String operand) {
        if (isString(operand)) {
            return ValueKind.STRING;
        }
        if (isFloatLiteral(operand)) {
            return ValueKind.FLOAT;
        }
        if (isTemporary(operand) && types.containsKey(operand)) {
            return types.get(operand);
        }
        if (isTemporary(operand) && floatHome.containsKey(operand)) {
            return ValueKind.FLOAT;
        }
        ProgramLayout.Location location = layout.locate(env, operand);
        if (location != null) {
            return kindOfType(location.slot().type);
        }
        return ValueKind.INT;
    }

    private ValueKind kindOfType(CompiscriptType type) {
        if (type == null) {
            return ValueKind.INT;
        }
        return switch (type.kind()) {
            case FLOAT -> ValueKind.FLOAT;
            case STRING -> ValueKind.STRING;
            case CLASS, ARRAY -> ValueKind.ADDR;
            default -> ValueKind.INT;
        };
    }

    private boolean floating(String operand) {
        return kindOf(operand) == ValueKind.FLOAT;
    }

    private ValueKind elementKind(String array) {
        ProgramLayout.Location location = layout.locate(env, array);
        if (location != null && location.slot().type.kind() == CompiscriptType.Kind.ARRAY
                && location.slot().type.elementType() != null) {
            return kindOfType(location.slot().type.elementType());
        }
        return ValueKind.INT;
    }

    private String classOf(String object) {
        if ("this".equals(object) && env != null) {
            return env.className;
        }
        if (classNames.containsKey(object)) {
            return classNames.get(object);
        }
        ProgramLayout.Location location = layout.locate(env, object);
        if (location != null && location.slot().type.kind() == CompiscriptType.Kind.CLASS) {
            return location.slot().type.className();
        }
        return null;
    }

    private CompiscriptType fieldType(String className, String field) {
        if (className == null) {
            return null;
        }
        com.compiscript.symbols.Symbol current = layout.classes.get(className);
        while (current != null) {
            com.compiscript.symbols.Symbol member = current.members().get(field);
            if (member != null) {
                return member.type();
            }
            if (current.parentClassName() == null) {
                break;
            }
            current = layout.classes.get(current.parentClassName());
        }
        return null;
    }

    private ValueKind returnKind(ProgramLayout.FuncEnv callee) {
        if (callee == null || callee.type == null || callee.type.returnType() == null) {
            return ValueKind.INT;
        }
        return kindOfType(callee.type.returnType());
    }

    private boolean isTemporary(String operand) {
        return operand.matches("t\\d+") && (layout == null || layout.locate(env, operand) == null);
    }

    private static boolean isInteger(String operand) {
        return operand.matches("-?\\d+");
    }

    private static boolean isFloatLiteral(String operand) {
        return operand.matches("-?\\d+\\.\\d+");
    }

    private static boolean isString(String operand) {
        return operand.startsWith("\"") && operand.endsWith("\"");
    }

    private static boolean isCompare(String op) {
        return "<".equals(op) || ">".equals(op) || "<=".equals(op) || ">=".equals(op)
                || "==".equals(op) || "!=".equals(op);
    }

    private static String unquote(String operand) {
        return operand.substring(1, operand.length() - 1);
    }

    private void collectFloats(List<String> lines) {
        Pattern pattern = Pattern.compile("-?\\d+\\.\\d+");
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            while (matcher.find()) {
                floatLabels.computeIfAbsent(matcher.group(), key -> "flt_" + floatLabels.size());
            }
        }
    }

    private void collectStrings(List<String> lines) {
        int id = 0;
        for (String line : lines) {
            for (int i = 0; i < line.length(); i++) {
                if (line.charAt(i) != '"') {
                    continue;
                }
                int end = line.indexOf('"', i + 1);
                if (end < 0) {
                    break;
                }
                String text = line.substring(i + 1, end);
                stringLabels.computeIfAbsent(text, key -> "str_" + stringLabels.size());
                id++;
                i = end;
            }
        }
        if (id < 0) {
            stringLabels.clear();
        }
    }

    private void parseTop(List<String> lines, List<Procedure> procedures, List<String> main) {
        int[] index = {0};
        while (index[0] < lines.size()) {
            if (isProcedureStart(lines, index[0])) {
                procedures.add(parseProcedure(lines, index));
            } else {
                main.add(lines.get(index[0]));
                index[0]++;
            }
        }
    }

    private Procedure parseProcedure(List<String> lines, int[] index) {
        String header = lines.get(index[0]);
        String name = header.substring(0, header.length() - 1);
        int size = Integer.parseInt(lines.get(index[0] + 1).split(" ")[2]);
        index[0] += 2;
        Procedure procedure = new Procedure(name, size);
        while (index[0] < lines.size()) {
            String line = lines.get(index[0]);
            if (line.equals("leave " + name)) {
                index[0]++;
                return procedure;
            }
            if (isProcedureStart(lines, index[0])) {
                procedure.nested.add(parseProcedure(lines, index));
            } else {
                procedure.body.add(line);
                index[0]++;
            }
        }
        throw new IllegalStateException("Missing leave for " + name);
    }

    private static boolean isProcedureStart(List<String> lines, int index) {
        String line = lines.get(index);
        if (!line.endsWith(":") || line.contains(" ")) {
            return false;
        }
        if (index + 1 >= lines.size()) {
            return false;
        }
        String name = line.substring(0, line.length() - 1);
        return lines.get(index + 1).startsWith("enter " + name + " ");
    }

    private Map<String, List<Integer>> uses(List<String> lines) {
        Map<String, List<Integer>> uses = new LinkedHashMap<>();
        Pattern pattern = Pattern.compile("\\bt\\d+\\b");
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = pattern.matcher(lines.get(i));
            while (matcher.find()) {
                uses.computeIfAbsent(matcher.group(), key -> new ArrayList<>()).add(i);
            }
        }
        return uses;
    }

    private void bindUses(Map<String, List<Integer>> uses, int index) {
        for (Map.Entry<String, List<Integer>> entry : uses.entrySet()) {
            int next = Integer.MAX_VALUE;
            for (int point : entry.getValue()) {
                if (point > index) {
                    next = point;
                    break;
                }
            }
            allocator.setNextUse(entry.getKey(), next);
        }
    }

    private Binary splitBinary(String rhs) {
        for (String op : OPERATORS) {
            String token = " " + op + " ";
            int at = indexOfOperator(rhs, token);
            if (at >= 0) {
                return new Binary(rhs.substring(0, at).trim(), op, rhs.substring(at + token.length()).trim());
            }
        }
        return null;
    }

    private static int indexOfOperator(String text, String token) {
        boolean quote = false;
        for (int i = 0; i + token.length() <= text.length(); i++) {
            if (text.charAt(i) == '"') {
                quote = !quote;
            }
            if (!quote && text.startsWith(token, i)) {
                return i;
            }
        }
        return -1;
    }

    private void emit(String line) {
        output.add(line);
    }

    private String fresh(String prefix) {
        return "__" + prefix + "_" + (labelId++);
    }

    private static int floatOffset(int slot) {
        return -(INT_SPILLS + slot + 1) * 4;
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private enum ValueKind {
        INT, FLOAT, STRING, ADDR
    }

    private record Binary(String left, String op, String right) {
    }

    private static final class Procedure {
        final String name;
        final int size;
        final List<Procedure> nested = new ArrayList<>();
        final List<String> body = new ArrayList<>();

        Procedure(String name, int size) {
            this.name = name;
            this.size = size;
        }
    }

}
