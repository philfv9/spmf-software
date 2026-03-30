package ca.pfv.spmf.gui.parameterselectionpanel;

import javax.swing.table.AbstractTableModel;

import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;

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
/**
 * This is a custom table model used by the ParameterSelectionPanel of SPMF
 * 
 * @see ParameterSelectionPanel
 * 
 * @author Philippe Fournier-Viger, 2024.
 */
@SuppressWarnings("serial")
public class ParameterSelectionTableModel extends AbstractTableModel {

	/** Names of columns */
	private String[] columnNames = { "Parameter", "Value", "Example" };

	/** The parameter values entered by the user */
	private String[] data;

	/** The descriptions of the parameters */
	private DescriptionOfParameter[] parameters;

	/**
	 * Constructor
	 * 
	 * @param descriptionOfAlgorithm the description of an algorithm
	 */
	public ParameterSelectionTableModel(DescriptionOfAlgorithm descriptionOfAlgorithm) {
		if (descriptionOfAlgorithm != null) {
			parameters = descriptionOfAlgorithm.getParametersDescription();
		} else {
			parameters = new DescriptionOfParameter[] {};
		}
		data = new String[parameters.length];
	}

	@Override
	/**
	 * Get the number of rows in the table
	 * 
	 * @return number of rows
	 */
	public int getRowCount() {
		if (parameters == null) {
			return 0;
		}
		return parameters.length;
	}

	@Override
	/**
	 * Get the number of columns
	 * 
	 * @return the number of columns
	 */
	public int getColumnCount() {
		return columnNames.length;
	}

	@Override
	/**
	 * Get the name of a column
	 * 
	 * @param column the column index
	 * @return the name
	 */
	public String getColumnName(int column) {
		return columnNames[column];
	}

	@Override
	/**
	 * Get the value in a cell
	 * 
	 * @param rowIndex    the row index
	 * @param columnIndex the column index
	 */
	public Object getValueAt(int rowIndex, int columnIndex) {
		DescriptionOfParameter parameter = parameters[rowIndex];
		if (columnIndex == 0) {
			return parameter.name + (parameter.isOptional() ? " (optional)" : "");
		} else if (columnIndex == 2) {
			return parameter.example;
		}
		return data[rowIndex];
	}

	@Override
	/**
	 * Check if a cell is editable
	 * 
	 * @param rowIndex    the row index
	 * @param columnIndex the column index
	 * @return true if editable. Otherwise false.
	 */
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return columnIndex == 1; // Only the "Value" column is editable
	}

	@Override
	/**
	 * Set the value in a cell
	 * 
	 * @param rowIndex    the row index
	 * @param columnIndex the column index
	 * @param aValue      the value
	 */
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		data[rowIndex] = (String) aValue;
		fireTableCellUpdated(rowIndex, columnIndex);
	}

	/**
	 * Set the data to display in the table
	 * 
	 * @param parameters the list of descriptions of parameters to be displayed
	 */
	public void setData(DescriptionOfParameter[] parameters) {
		// Try to copy the previous data:
		String[] previousdata = data;
		data = new String[parameters.length];

		for (int i = 0; i < data.length && i < previousdata.length; i++) {
			data[i] = previousdata[i];
		}

		this.parameters = parameters;
		fireTableDataChanged(); // Notify table of data change
	}

	/**
	 * Set the parameter values using an array of values
	 */
	public void setParameterValues(String[] parameterValues) {
		if (parameterValues == null) {
			return;
		}
		for (int i = 0; i < data.length && i < parameterValues.length; i++) {
			data[i] = parameterValues[i];
		}
	}

	/**
	 * Get the array of parameter values entered by the user
	 * 
	 * @return an array of String values
	 */
	public String[] getParameterValues() {
		// We make a copy and remove the null values for optional parameters
		int numberOfNonOptionalParameters = 0;
		for (int i = 0; i < parameters.length; i++) {
			DescriptionOfParameter param = parameters[i];
			boolean toRemove = param.isOptional() == true && (data[i] == null || "".equals(data[i]));
			if (toRemove == false) {
				numberOfNonOptionalParameters++;
			}
		}

		String[] trimmedData = new String[numberOfNonOptionalParameters];
		System.arraycopy(data, 0, trimmedData, 0, numberOfNonOptionalParameters);

		return trimmedData;
	}

}
