package com.compiscript.gui;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/** Draws a parse tree as boxes and edges instead of a folder-style outline. */
public class AstDiagramPanel extends JPanel {

    private static final int NODE_HEIGHT = 32;
    private static final int LEVEL_GAP = 56;
    private static final int SIBLING_GAP = 18;
    private static final int MARGIN = 24;

    private LayoutNode root;

    public AstDiagramPanel() {
        setBackground(Color.WHITE);
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    }

    public void setTree(ParseTree tree) {
        root = tree == null ? null : build(tree);
        if (root != null) {
            place(root, MARGIN, 0);
        }
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        if (root == null) {
            return new Dimension(640, 220);
        }
        return new Dimension(Math.max(640, root.subtreeWidth + MARGIN * 2), root.bottom + MARGIN);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (root == null) {
            return;
        }
        Graphics2D canvas = (Graphics2D) graphics.create();
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        canvas.setFont(getFont());
        paintNode(canvas, root);
        canvas.dispose();
    }

    private void paintNode(Graphics2D canvas, LayoutNode node) {
        for (LayoutNode child : node.children) {
            int x1 = node.x + node.nodeWidth / 2;
            int y1 = node.y + NODE_HEIGHT;
            int x2 = child.x + child.nodeWidth / 2;
            int y2 = child.y;
            canvas.setColor(new Color(120, 144, 156));
            canvas.drawLine(x1, y1, x2, y2);
            paintNode(canvas, child);
        }
        canvas.setColor(new Color(227, 242, 253));
        canvas.fillRoundRect(node.x, node.y, node.nodeWidth, NODE_HEIGHT, 12, 12);
        canvas.setColor(new Color(25, 118, 210));
        canvas.drawRoundRect(node.x, node.y, node.nodeWidth, NODE_HEIGHT, 12, 12);
        canvas.setColor(new Color(33, 33, 33));
        FontMetrics metrics = canvas.getFontMetrics();
        int textX = node.x + (node.nodeWidth - metrics.stringWidth(node.label)) / 2;
        int textY = node.y + (NODE_HEIGHT + metrics.getAscent() - metrics.getDescent()) / 2;
        canvas.drawString(node.label, textX, textY);
    }

    private LayoutNode build(ParseTree tree) {
        LayoutNode node = new LayoutNode(labelOf(tree));
        for (int i = 0; i < tree.getChildCount(); i++) {
            node.children.add(build(tree.getChild(i)));
        }
        return node;
    }

    private void place(LayoutNode node, int left, int depth) {
        FontMetrics metrics = getFontMetrics(getFont());
        node.nodeWidth = Math.max(72, metrics.stringWidth(node.label) + 20);
        node.y = MARGIN + depth * (NODE_HEIGHT + LEVEL_GAP);
        if (node.children.isEmpty()) {
            node.x = left;
            node.subtreeWidth = node.nodeWidth;
            node.bottom = node.y + NODE_HEIGHT;
            return;
        }
        int cursor = left;
        int childBottom = node.y + NODE_HEIGHT;
        for (LayoutNode child : node.children) {
            place(child, cursor, depth + 1);
            cursor += child.subtreeWidth + SIBLING_GAP;
            childBottom = Math.max(childBottom, child.bottom);
        }
        int childrenWidth = cursor - left - SIBLING_GAP;
        node.subtreeWidth = Math.max(node.nodeWidth, childrenWidth);
        node.x = left + (node.subtreeWidth - node.nodeWidth) / 2;
        node.bottom = childBottom;
    }

    private String labelOf(ParseTree tree) {
        String label;
        if (tree instanceof TerminalNode terminal) {
            String text = terminal.getText();
            label = text == null || text.isBlank() ? "EOF" : text;
        } else if (tree instanceof ParserRuleContext context) {
            label = context.getClass().getSimpleName().replace("Context", "");
        } else {
            label = tree.getClass().getSimpleName();
        }
        if (label.length() > 28) {
            return label.substring(0, 25) + "...";
        }
        return label;
    }

    private static final class LayoutNode {
        private final String label;
        private final List<LayoutNode> children = new ArrayList<>();
        private int x;
        private int y;
        private int nodeWidth;
        private int subtreeWidth;
        private int bottom;

        private LayoutNode(String label) {
            this.label = label;
        }
    }
}
