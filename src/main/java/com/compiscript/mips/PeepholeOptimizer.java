package com.compiscript.mips;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local rewrites applied to the generated MIPS text. Six transformations, no global optimizer. */
public final class PeepholeOptimizer {

    private static final Pattern MOVE_SELF = Pattern.compile("move (\\$\\w+), \\1");
    private static final Pattern ADDU_SELF = Pattern.compile("addu (\\$\\w+), \\1, \\$zero");
    private static final Pattern ADD_ZERO = Pattern.compile("add (\\$\\w+), (\\$\\w+), \\$zero");
    private static final Pattern ADDU_ZERO = Pattern.compile("addu (\\$\\w+), (\\$\\w+), \\$zero");
    private static final Pattern ADDI_ZERO = Pattern.compile("addi[u]? (\\$\\w+), (\\$\\w+), 0");
    private static final Pattern MUL_IMM = Pattern.compile("mul (\\$\\w+), (\\$\\w+), (-?\\d+)");
    private static final Pattern DIV_IMM = Pattern.compile("div (\\$\\w+), (\\$\\w+), (-?\\d+)");
    private static final Pattern STORE = Pattern.compile("sw (\\$\\w+), (\\S+)");
    private static final Pattern LOAD = Pattern.compile("lw (\\$\\w+), (\\S+)");
    private static final Pattern JUMP = Pattern.compile("j (\\S+)");

    public List<String> optimize(List<String> lines) {
        List<String> current = new ArrayList<>(lines);
        for (int pass = 0; pass < 8; pass++) {
            List<String> next = deleteMoveSelf(current);
            next = algebraic(next);
            next = strength(next);
            next = deleteJumpToNext(next);
            next = deleteRedundantLoad(next);
            next = deleteUnreachable(next);
            if (next.equals(current)) {
                return next;
            }
            current = next;
        }
        return current;
    }

    private List<String> deleteMoveSelf(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (MOVE_SELF.matcher(line).matches() || ADDU_SELF.matcher(line).matches()) {
                continue;
            }
            result.add(line);
        }
        return result;
    }

    private List<String> algebraic(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            Matcher add = ADD_ZERO.matcher(line);
            Matcher addu = ADDU_ZERO.matcher(line);
            Matcher addi = ADDI_ZERO.matcher(line);
            if (add.matches()) {
                result.add("move " + add.group(1) + ", " + add.group(2));
            } else if (addu.matches() && !addu.group(1).equals(addu.group(2))) {
                result.add("move " + addu.group(1) + ", " + addu.group(2));
            } else if (addi.matches()) {
                result.add("move " + addi.group(1) + ", " + addi.group(2));
            } else {
                Matcher mul = MUL_IMM.matcher(line);
                if (mul.matches() && "1".equals(mul.group(3))) {
                    result.add("move " + mul.group(1) + ", " + mul.group(2));
                } else {
                    result.add(line);
                }
            }
        }
        return result;
    }

    private List<String> strength(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            Matcher mul = MUL_IMM.matcher(line);
            Matcher div = DIV_IMM.matcher(line);
            if (mul.matches()) {
                int value = Integer.parseInt(mul.group(3));
                int shift = shiftAmount(value);
                if (shift > 0) {
                    result.add("sll " + mul.group(1) + ", " + mul.group(2) + ", " + shift);
                    continue;
                }
            }
            if (div.matches()) {
                int value = Integer.parseInt(div.group(3));
                int shift = shiftAmount(value);
                if (shift > 0) {
                    result.add("sra " + div.group(1) + ", " + div.group(2) + ", " + shift);
                    continue;
                }
            }
            result.add(line);
        }
        return result;
    }

    private List<String> deleteJumpToNext(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Matcher jump = JUMP.matcher(lines.get(i));
            if (jump.matches()) {
                String target = jump.group(1);
                int next = nextCode(lines, i + 1);
                if (next >= 0 && lines.get(next).equals(target + ":")) {
                    continue;
                }
            }
            result.add(lines.get(i));
        }
        return result;
    }

    private List<String> deleteRedundantLoad(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                Matcher store = STORE.matcher(result.isEmpty() ? "" : result.get(result.size() - 1));
                Matcher load = LOAD.matcher(lines.get(i));
                if (store.matches() && load.matches()
                        && store.group(1).equals(load.group(1))
                        && store.group(2).equals(load.group(2))) {
                    continue;
                }
            }
            result.add(lines.get(i));
        }
        return result;
    }

    private List<String> deleteUnreachable(List<String> lines) {
        List<String> result = new ArrayList<>();
        boolean dead = false;
        for (String line : lines) {
            if (dead) {
                if (isLabel(line)) {
                    dead = false;
                    result.add(line);
                }
                continue;
            }
            result.add(line);
            if (isUnconditional(line)) {
                dead = true;
            }
        }
        return result;
    }

    private static boolean isUnconditional(String line) {
        return line.startsWith("j ") || line.startsWith("jr ");
    }

    private static boolean isLabel(String line) {
        return line.endsWith(":") && !line.contains(" ");
    }

    private static int nextCode(List<String> lines, int start) {
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#") || line.startsWith(".")) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static int shiftAmount(int value) {
        if (value <= 1 || (value & (value - 1)) != 0) {
            return 0;
        }
        return Integer.numberOfTrailingZeros(value);
    }
}
