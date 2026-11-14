package com.compiscript.gui;

import com.compiscript.mips.RegisterDecision;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/** Table of getReg decisions for the current compilation. */
public class RegisterTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"Function", "Temporary", "Register", "Spilled"};

    private List<RegisterDecision> decisions = new ArrayList<>();

    public void setDecisions(List<RegisterDecision> newDecisions) {
        this.decisions = new ArrayList<>(newDecisions == null ? List.of() : newDecisions);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return decisions.size();
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
        return columnIndex == 3 ? Boolean.class : String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        RegisterDecision decision = decisions.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> decision.function();
            case 1 -> decision.temporary();
            case 2 -> decision.register();
            case 3 -> decision.spilled();
            default -> "";
        };
    }
}
