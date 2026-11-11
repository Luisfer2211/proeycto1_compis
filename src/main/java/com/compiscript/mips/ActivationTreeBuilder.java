package com.compiscript.mips;

import com.compiscript.symbols.SymbolTable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Builds the static activation tree from TAC calls and the symbol-table frames. */
public final class ActivationTreeBuilder {

    public ActivationNode build(List<String> lines, SymbolTable table) {
        ProgramLayout layout = table == null ? new ProgramLayout() : ProgramLayout.from(table);
        ActivationNode program = new ActivationNode("program", false, List.of());
        ActivationNode main = new ActivationNode("__main", false, layout.frame("__main"));
        program.addChild(main);

        Deque<ActivationNode> stack = new ArrayDeque<>();
        stack.push(main);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isProcedureStart(lines, i)) {
                String name = line.substring(0, line.length() - 1);
                ActivationNode node = new ActivationNode(name, false, layout.frame(name));
                stack.peek().addChild(node);
                stack.push(node);
                continue;
            }
            if (line.startsWith("leave ")) {
                if (stack.size() > 1) {
                    stack.pop();
                }
                continue;
            }
            String callee = calleeOf(line);
            if (callee == null || stack.isEmpty()) {
                continue;
            }
            String current = stack.peek().label();
            boolean recursive = callee.equals(current);
            String callLabel = recursive ? callee + " recursive" : "call " + callee;
            stack.peek().addChild(new ActivationNode(callLabel, recursive, layout.frame(callee)));
        }
        return program;
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

    private static String calleeOf(String line) {
        int call = line.indexOf("call ");
        if (call < 0 || line.startsWith("alloc ")) {
            return null;
        }
        String rest = line.substring(call + "call ".length());
        int comma = rest.indexOf(',');
        if (comma < 0) {
            return null;
        }
        return rest.substring(0, comma).trim();
    }
}
