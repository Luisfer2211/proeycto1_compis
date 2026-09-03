package com.compiscript.gui;

import com.compiscript.analysis.AnalysisResult;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** Table model for symbol table rows displayed in the IDE. */
public class SymbolTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Scope", "Name", "Kind", "Type", "Initialized", "Line"};

    private List<AnalysisResult.SymbolRow> rows = new ArrayList<>();

    public void setRows(List<AnalysisResult.SymbolRow> newRows) {
        this.rows = new ArrayList<>(newRows);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 5 -> Integer.class;
            case 4 -> Boolean.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        AnalysisResult.SymbolRow row = rows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> row.scope();
            case 1 -> row.name();
            case 2 -> row.kind();
            case 3 -> row.type();
            case 4 -> row.initialized();
            case 5 -> row.line();
            default -> "";
        };
    }
}
