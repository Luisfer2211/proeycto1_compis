package com.compiscript.gui;

import com.compiscript.analysis.AnalysisResult;
import com.compiscript.analysis.CompiscriptAnalyzer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/** Bootstrap main window: open a .cps file, analyze it, and show lexical/syntax errors. */
public class MainWindow extends JFrame {

    private static final Color SUCCESS_COLOR = new Color(46, 125, 50);
    private static final Color ERROR_COLOR = new Color(198, 40, 40);

    private final CompiscriptAnalyzer analyzer = new CompiscriptAnalyzer();
    private final ErrorTableModel tableModel = new ErrorTableModel();

    private File currentFile;
    private JLabel fileLabel;
    private JTextArea codeArea;
    private JButton analyzeButton;
    private JLabel bannerLabel;

    public MainWindow() {
        super("Compiscript Lexical and Syntax Analyzer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 650);
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

        analyzeButton = new JButton("Analyze");
        analyzeButton.setEnabled(false);
        analyzeButton.addActionListener(event -> analyzeFile());

        fileLabel = new JLabel("No file selected");
        panel.add(openButton);
        panel.add(analyzeButton);
        panel.add(fileLabel);
        return panel;
    }

    private JComponent buildCenter() {
        codeArea = new JTextArea();
        codeArea.setEditable(false);
        codeArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane codeScroll = new JScrollPane(codeArea);
        codeScroll.setBorder(BorderFactory.createTitledBorder("Source code"));

        JTable table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Errors"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, codeScroll, tableScroll);
        splitPane.setResizeWeight(0.5);
        splitPane.setBorder(new EmptyBorder(0, 10, 0, 10));
        return splitPane;
    }

    private JComponent buildBanner() {
        JPanel bannerPanel = new JPanel(new BorderLayout());
        bannerPanel.setBorder(new EmptyBorder(10, 15, 10, 15));
        bannerLabel = new JLabel("Select a .cps file to begin.");
        bannerLabel.setFont(bannerLabel.getFont().deriveFont(Font.BOLD, 14f));
        bannerPanel.add(bannerLabel, BorderLayout.CENTER);
        return bannerPanel;
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Compiscript files (*.cps)", "cps"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        currentFile = chooser.getSelectedFile();
        fileLabel.setText(currentFile.getName());
        analyzeButton.setEnabled(true);
        try {
            codeArea.setText(Files.readString(currentFile.toPath()));
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void analyzeFile() {
        try {
            AnalysisResult result = analyzer.analyze(currentFile);
            tableModel.setErrors(result.errors());
            if (result.successful()) {
                bannerLabel.setText("Analysis successful: no lexical or syntax errors found.");
                bannerLabel.setForeground(SUCCESS_COLOR);
            } else {
                bannerLabel.setText("Found " + result.errors().size() + " lexical and/or syntax errors.");
                bannerLabel.setForeground(ERROR_COLOR);
            }
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
