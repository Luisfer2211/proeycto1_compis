package com.compiscript.gui;

import com.compiscript.analysis.AnalysisResult;
import com.compiscript.errors.AnalysisError;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** Table model for analysis errors shown in the IDE. */
public class ErrorTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Type", "Line", "Column", "Symbol", "Description"};

    private List<AnalysisError> errors = new ArrayList<>();

    public void setErrors(List<AnalysisError> newErrors) {
        this.errors = new ArrayList<>(newErrors);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return errors.size();
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
            case 1, 2 -> Integer.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        AnalysisError error = errors.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> error.type().label();
            case 1 -> error.line();
            case 2 -> error.column();
            case 3 -> error.symbol();
            case 4 -> error.description();
            default -> "";
        };
    }
}
