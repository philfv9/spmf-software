package ca.pfv.spmf.gui.pattern_diff_analyzer;

import java.util.List;

import javax.swing.table.AbstractTableModel;
/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * Table model for displaying pattern data and contrast results.
 * 
 * @author Philippe Fournier-Viger
 */
public class ContrastTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;

    /** The data rows */
    private List<List<Object>> data;
    
    /** Column names */
    private List<String> columnNames;
    
    /** Column classes */
    private List<Class<?>> columnClasses;

    /**
     * Constructor.
     * @param data the data rows
     * @param columnNames the column names
     * @param columnClasses the column classes
     */
    public ContrastTableModel(List<List<Object>> data, List<String> columnNames,
            List<Class<?>> columnClasses) {
        this.data = data;
        this.columnNames = columnNames;
        this.columnClasses = columnClasses;
    }

    @Override
    public int getRowCount() {
        return data != null ? data.size() : 0;
    }

    @Override
    public int getColumnCount() {
        return columnNames != null ? columnNames.size() : 0;
    }

    @Override
    public String getColumnName(int column) {
        if (columnNames != null && column >= 0 && column < columnNames.size()) {
            return columnNames.get(column);
        }
        return super.getColumnName(column);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (columnClasses != null && columnIndex >= 0 && columnIndex < columnClasses.size()) {
            return columnClasses.get(columnIndex);
        }
        return Object.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (data == null || rowIndex < 0 || rowIndex >= data.size()) {
            return null;
        }
        List<Object> row = data.get(rowIndex);
        if (row == null || columnIndex < 0 || columnIndex >= row.size()) {
            return null;
        }
        return row.get(columnIndex);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    /**
     * Update the data in this model.
     * @param data the new data
     * @param columnNames the new column names
     * @param columnClasses the new column classes
     */
    public void setData(List<List<Object>> data, List<String> columnNames,
            List<Class<?>> columnClasses) {
        this.data = data;
        this.columnNames = columnNames;
        this.columnClasses = columnClasses;
        fireTableStructureChanged();
    }

    /**
     * Get the underlying data.
     * @return the data list
     */
    public List<List<Object>> getData() {
        return data;
    }
}