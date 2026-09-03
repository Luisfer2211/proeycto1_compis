package com.compiscript.gui;

import com.compiscript.analysis.AnalysisResult;
import com.compiscript.analysis.CompiscriptAnalyzer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Main IDE window for editing and analyzing Compiscript source files. */
public class MainWindow extends JFrame {

    private static final Color SUCCESS_COLOR = new Color(46, 125, 50);
    private static final Color ERROR_COLOR = new Color(198, 40, 40);

    private final CompiscriptAnalyzer analyzer = new CompiscriptAnalyzer();
    private final ErrorTableModel errorTableModel = new ErrorTableModel();
    private final SymbolTableModel symbolTableModel = new SymbolTableModel();

    private File currentFile;
    private JLabel fileLabel;
    private JTextArea editorArea;
    private JButton analyzeButton;
    private JButton saveButton;
    private JLabel bannerLabel;
    private JTree astTree;

    public MainWindow() {
        super("Compiscript Semantic Analyzer IDE");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 750);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildBanner(), BorderLayout.SOUTH);
    }

    private JComponent buildTopBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panel.setBorder(new EmptyBorder(5, 10, 5, 10));

        JButton openButton = new JButton("Open .cps");
        openButton.addActionListener(event -> openFile());

        saveButton = new JButton("Save");
        saveButton.setEnabled(false);
        saveButton.addActionListener(event -> saveFile());

        analyzeButton = new JButton("Analyze");
        analyzeButton.addActionListener(event -> analyzeCurrentSource());

        fileLabel = new JLabel("No file selected");

        panel.add(openButton);
        panel.add(saveButton);
        panel.add(analyzeButton);
        panel.add(fileLabel);
        return panel;
    }

    private JComponent buildCenter() {
        editorArea = new JTextArea();
        editorArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        editorArea.setTabSize(4);
        JScrollPane editorScroll = new JScrollPane(editorArea);
        editorScroll.setBorder(BorderFactory.createTitledBorder("Editor"));

        JTable errorTable = new JTable(errorTableModel);
        errorTable.setAutoCreateRowSorter(true);
        errorTable.setFillsViewportHeight(true);
        JScrollPane errorScroll = new JScrollPane(errorTable);
        errorScroll.setBorder(BorderFactory.createTitledBorder("Errors"));

        astTree = new JTree(new DefaultMutableTreeNode("AST"));
        astTree.setShowsRootHandles(true);
        JScrollPane astScroll = new JScrollPane(astTree);
        astScroll.setBorder(BorderFactory.createTitledBorder("AST"));

        JTable symbolTable = new JTable(symbolTableModel);
        symbolTable.setAutoCreateRowSorter(true);
        symbolTable.setFillsViewportHeight(true);
        JScrollPane symbolScroll = new JScrollPane(symbolTable);
        symbolScroll.setBorder(BorderFactory.createTitledBorder("Symbol Table"));

        JTabbedPane resultTabs = new JTabbedPane();
        resultTabs.addTab("Errors", errorScroll);
        resultTabs.addTab("AST", astScroll);
        resultTabs.addTab("Symbols", symbolScroll);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorScroll, resultTabs);
        splitPane.setResizeWeight(0.45);
        splitPane.setBorder(new EmptyBorder(0, 10, 0, 10));
        return splitPane;
    }

    private JComponent buildBanner() {
        JPanel bannerPanel = new JPanel(new BorderLayout());
        bannerPanel.setBorder(new EmptyBorder(10, 15, 10, 15));
        bannerLabel = new JLabel("Open or write Compiscript code, then press Analyze.");
        bannerLabel.setFont(bannerLabel.getFont().deriveFont(Font.BOLD, 14f));
        bannerPanel.add(bannerLabel, BorderLayout.CENTER);
        return bannerPanel;
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Compiscript files (*.cps)", "cps"));
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        currentFile = chooser.getSelectedFile();
        fileLabel.setText(currentFile.getName());
        saveButton.setEnabled(true);
        try {
            editorArea.setText(Files.readString(currentFile.toPath()));
            editorArea.setCaretPosition(0);
            bannerLabel.setText("File loaded. Press Analyze to run lexical, syntax, and semantic checks.");
            bannerLabel.setForeground(Color.DARK_GRAY);
        } catch (IOException exception) {
            showError("Could not read file: " + exception.getMessage());
        }
    }

    private void saveFile() {
        if (currentFile == null) {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Compiscript files (*.cps)", "cps"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            currentFile = chooser.getSelectedFile();
            fileLabel.setText(currentFile.getName());
            saveButton.setEnabled(true);
        }
        try {
            Files.writeString(currentFile.toPath(), editorArea.getText());
            bannerLabel.setText("File saved successfully.");
            bannerLabel.setForeground(SUCCESS_COLOR);
        } catch (IOException exception) {
            showError("Could not save file: " + exception.getMessage());
        }
    }

    private void analyzeCurrentSource() {
        AnalysisResult result = analyzer.analyze(editorArea.getText());
        errorTableModel.setErrors(result.errors());
        symbolTableModel.setRows(result.symbolRows());
        astTree.setModel(new javax.swing.tree.DefaultTreeModel(
                result.parseTree() == null
                        ? new DefaultMutableTreeNode("No parse tree")
                        : AstTreeBuilder.build(result.parseTree())));
        updateBanner(result);
    }

    private void updateBanner(AnalysisResult result) {
        if (result.successful()) {
            bannerLabel.setText("Analysis successful: no lexical, syntax, or semantic errors found.");
            bannerLabel.setForeground(SUCCESS_COLOR);
            return;
        }
        bannerLabel.setText("Found " + result.errors().size() + " error(s): "
                + result.lexicalErrorCount() + " lexical, "
                + result.syntaxErrorCount() + " syntax, "
                + result.semanticErrorCount() + " semantic.");
        bannerLabel.setForeground(ERROR_COLOR);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
