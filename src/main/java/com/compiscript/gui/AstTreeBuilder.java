package com.compiscript.gui;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import javax.swing.tree.DefaultMutableTreeNode;

/** Builds a Swing tree model from an ANTLR parse tree. */
public final class AstTreeBuilder {

    private AstTreeBuilder() {
    }

    public static DefaultMutableTreeNode build(ParseTree tree) {
        return toNode(tree);
    }

    private static DefaultMutableTreeNode toNode(ParseTree tree) {
        String label = formatLabel(tree);
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(label);
        for (int i = 0; i < tree.getChildCount(); i++) {
            node.add(toNode(tree.getChild(i)));
        }
        return node;
    }

    private static String formatLabel(ParseTree tree) {
        if (tree instanceof TerminalNode terminal) {
            String text = terminal.getText();
            if (text == null || text.isBlank()) {
                return terminal.getSymbol().getType() == -1 ? "EOF" : "token";
            }
            return "'" + text + "'";
        }
        if (tree instanceof ParserRuleContext context) {
            return context.getClass().getSimpleName().replace("Context", "");
        }
        return tree.getClass().getSimpleName();
    }
}
