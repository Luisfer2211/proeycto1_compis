package com.compiscript.gui;

import com.compiscript.mips.ActivationNode;
import com.compiscript.mips.FrameSlot;

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

/** Draws activation records as boxes and calls as edges. */
public class ActivationDiagramPanel extends JPanel {

    private static final int LINE_HEIGHT = 16;
    private static final int LEVEL_GAP = 36;
    private static final int SIBLING_GAP = 18;
    private static final int MARGIN = 24;

    private LayoutNode root;

    public ActivationDiagramPanel() {
        setBackground(Color.WHITE);
        setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    }

    public void setActivation(ActivationNode activation) {
        root = activation == null ? null : build(activation);
        if (root != null) {
            place(root, MARGIN, MARGIN);
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
            int y1 = node.y + node.nodeHeight;
            int x2 = child.x + child.nodeWidth / 2;
            int y2 = child.y;
            canvas.setColor(new Color(120, 144, 156));
            canvas.drawLine(x1, y1, x2, y2);
            paintNode(canvas, child);
        }
        canvas.setColor(node.recursive ? new Color(255, 243, 224) : new Color(232, 245, 233));
        canvas.fillRoundRect(node.x, node.y, node.nodeWidth, node.nodeHeight, 12, 12);
        canvas.setColor(node.recursive ? new Color(239, 108, 0) : new Color(46, 125, 50));
        canvas.drawRoundRect(node.x, node.y, node.nodeWidth, node.nodeHeight, 12, 12);
        canvas.setColor(new Color(33, 33, 33));
        FontMetrics metrics = canvas.getFontMetrics();
        int textY = node.y + 4 + metrics.getAscent();
        for (String line : node.lines) {
            canvas.drawString(line, node.x + 8, textY);
            textY += LINE_HEIGHT;
        }
    }

    private LayoutNode build(ActivationNode activation) {
        LayoutNode node = new LayoutNode(linesOf(activation), activation.recursive());
        for (ActivationNode child : activation.children()) {
            node.children.add(build(child));
        }
        return node;
    }

    private List<String> linesOf(ActivationNode activation) {
        List<String> lines = new ArrayList<>();
        lines.add(activation.label());
        for (FrameSlot slot : activation.slots()) {
            lines.add(slot.text());
        }
        if (activation.slots().isEmpty()) {
            lines.add("(no frame)");
        }
        return lines;
    }

    private void place(LayoutNode node, int left, int top) {
        FontMetrics metrics = getFontMetrics(getFont());
        int textWidth = 72;
        for (String line : node.lines) {
            textWidth = Math.max(textWidth, metrics.stringWidth(line) + 20);
        }
        node.nodeWidth = textWidth;
        node.nodeHeight = node.lines.size() * LINE_HEIGHT + 10;
        node.y = top;
        if (node.children.isEmpty()) {
            node.x = left;
            node.subtreeWidth = node.nodeWidth;
            node.bottom = node.y + node.nodeHeight;
            return;
        }
        int cursor = left;
        int childTop = top + node.nodeHeight + LEVEL_GAP;
        int childBottom = childTop;
        for (LayoutNode child : node.children) {
            place(child, cursor, childTop);
            cursor += child.subtreeWidth + SIBLING_GAP;
            childBottom = Math.max(childBottom, child.bottom);
        }
        int childrenWidth = cursor - left - SIBLING_GAP;
        node.subtreeWidth = Math.max(node.nodeWidth, childrenWidth);
        node.x = left + (node.subtreeWidth - node.nodeWidth) / 2;
        node.bottom = childBottom;
    }

    private static final class LayoutNode {
        private final List<String> lines;
        private final boolean recursive;
        private final List<LayoutNode> children = new ArrayList<>();
        private int x;
        private int y;
        private int nodeWidth;
        private int nodeHeight;
        private int subtreeWidth;
        private int bottom;

        private LayoutNode(List<String> lines, boolean recursive) {
            this.lines = lines;
            this.recursive = recursive;
        }
    }
}
