package ca.pfv.spmf.gui.parameterselectionpanel;

/*
 * Copyright (c) 2022 Philippe Fournier-Viger
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

import javax.swing.table.AbstractTableModel;

import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;

/**
 * This is a custom table model used by the ParameterSelectionPanel of SPMF
 *
 * @see ParameterSelectionPanel
 *
 * @author Philippe Fournier-Viger, 2024.
 */
@SuppressWarnings("serial")
public class ParameterSelectionTableModel extends AbstractTableModel {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** Index of the "Parameter" column */
    private static final int COL_PARAMETER = 0;

    /** Index of the "Value" column */
    private static final int COL_VALUE = 1;

    /** Index of the "Example" column */
    private static final int COL_EXAMPLE = 2;

    /** Suffix appended to the parameter name when the parameter is optional */
    private static final String OPTIONAL_SUFFIX = " (optional)";

    // -----------------------------------------------------------------------
    // Instance fields
    // -----------------------------------------------------------------------

    /** Names of columns */
    private final String[] columnNames = { "Parameter", "Value", "Example" };

    /** The parameter values entered by the user */
    private String[] data;

    /** The descriptions of the parameters */
    private DescriptionOfParameter[] parameters;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Constructor
     *
     * @param descriptionOfAlgorithm the description of an algorithm
     */
    public ParameterSelectionTableModel(DescriptionOfAlgorithm descriptionOfAlgorithm) {
        if (descriptionOfAlgorithm != null) {
            parameters = descriptionOfAlgorithm.getParametersDescription();
        } else {
            // No algorithm selected – start with an empty parameter list
            parameters = new DescriptionOfParameter[0];
        }
        // Allocate value storage; entries are null until the user types something
        data = new String[parameters.length];
    }

    // -----------------------------------------------------------------------
    // AbstractTableModel overrides
    // -----------------------------------------------------------------------

    /**
     * Get the number of rows in the table
     *
     * @return number of rows
     */
    @Override
    public int getRowCount() {
        if (parameters == null) {
            return 0;
        }
        return parameters.length;
    }

    /**
     * Get the number of columns
     *
     * @return the number of columns
     */
    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    /**
     * Get the name of a column
     *
     * @param column the column index
     * @return the name
     */
    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    /**
     * Get the value in a cell
     *
     * @param rowIndex    the row index
     * @param columnIndex the column index
     * @return the cell value as an Object
     */
    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        DescriptionOfParameter parameter = parameters[rowIndex];

        if (columnIndex == COL_PARAMETER) {
            // Append "(optional)" suffix so the user knows the field is not mandatory
            return parameter.name + (parameter.isOptional() ? OPTIONAL_SUFFIX : "");
        } else if (columnIndex == COL_EXAMPLE) {
            return parameter.example;
        }

        // COL_VALUE – return whatever the user has typed (may be null)
        return data[rowIndex];
    }

    /**
     * Check if a cell is editable
     *
     * @param rowIndex    the row index
     * @param columnIndex the column index
     * @return true if editable. Otherwise false.
     */
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        // Only the "Value" column is editable
        return columnIndex == COL_VALUE;
    }

    /**
     * Set the value in a cell
     *
     * @param aValue      the value
     * @param rowIndex    the row index
     * @param columnIndex the column index
     */
    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        // Guard: only accept edits in the value column
        if (columnIndex != COL_VALUE) {
            return;
        }
        data[rowIndex] = (String) aValue;
        fireTableCellUpdated(rowIndex, columnIndex);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Set the data to display in the table.
     * Previously entered values are preserved when the new parameter list is
     * at least as long as the old one.
     *
     * @param parameters the list of descriptions of parameters to be displayed
     */
    public void setData(DescriptionOfParameter[] parameters) {
        // Guard against a null argument
        if (parameters == null) {
            parameters = new DescriptionOfParameter[0];
        }

        // Try to copy the previous data so the user does not lose typed values
        String[] previousData = data;
        data = new String[parameters.length];

        for (int i = 0; i < data.length && i < previousData.length; i++) {
            data[i] = previousData[i];
        }

        this.parameters = parameters;
        fireTableDataChanged(); // Notify the table that the data has changed
    }

    /**
     * Pre-populate the Value column with an array of values. Used by the
     * run-history re-run feature to restore previously recorded parameter
     * values. Silently ignores null or empty arrays. Extra entries beyond
     * the current row count are ignored.
     *
     * @param parameterValues the values to set; must not be null
     */
    public void setParameterValues(String[] parameterValues) {
        if (parameterValues == null || parameterValues.length == 0) {
            return;
        }
        for (int i = 0; i < data.length && i < parameterValues.length; i++) {
            data[i] = parameterValues[i];
        }
        // Notify the view so it repaints with the new values
        fireTableDataChanged();
    }

    /**
     * Get the array of parameter values entered by the user. Optional
     * parameters that have been left blank are excluded from the returned
     * array so that the caller receives only meaningful values that should be
     * passed to the algorithm.
     *
     * <p><b>Important:</b> This method compacts the array by removing empty
     * optional parameters, which means the length of the returned array may be
     * less than {@link #getRowCount()}. When re-running a history entry the
     * controller must NOT call this method to determine how many values to
     * restore — it should call {@link #getRowCount()} instead, which always
     * returns the full count of parameter rows.</p>
     *
     * @return an array of String values (never null)
     */
    public String[] getParameterValues() {
        // Count how many parameters should be included in the result
        int numberOfNonOptionalParameters = 0;
        for (int i = 0; i < parameters.length; i++) {
            DescriptionOfParameter param = parameters[i];
            // Skip optional parameters that were left empty
            boolean toRemove = param.isOptional() 
                    && (data[i] == null || data[i].isEmpty());
            if (!toRemove) {
                numberOfNonOptionalParameters++;
            }
        }

        // Copy only the relevant entries into the trimmed result array
        String[] trimmedData = new String[numberOfNonOptionalParameters];
        int j = 0;
        for (int i = 0; i < parameters.length; i++) {
            DescriptionOfParameter param = parameters[i];
            boolean toRemove = param.isOptional() 
                    && (data[i] == null || data[i].isEmpty());
            if (!toRemove) {
                trimmedData[j] = data[i];
                j++;
            }
        }

        return trimmedData;
    }
}