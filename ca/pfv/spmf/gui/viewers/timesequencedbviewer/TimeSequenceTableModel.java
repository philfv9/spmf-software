package ca.pfv.spmf.gui.viewers.timesequencedbviewer;

import javax.swing.table.AbstractTableModel;

import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.Itemset;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.Sequence;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.SequenceDatabase;
/*
 * Copyright (c) 2008-2022 Philippe Fournier-Viger
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
 * This class extends AbstractTableModel to create a model for representing a
 * multi-dimensional sequence database in a JTable.
 * 
 * @see SequenceDatabase
 * @see AbstractTableModel
 */
public class TimeSequenceTableModel extends AbstractTableModel {

	/** The column types for the table */
	private final Class<?>[] columnTypes = { String.class, String.class };

	/** The maximum number of itemsets in the database */
	private int maxItemsets;

	/** The multi-dimensional sequence database to display */
	SequenceDatabase db;

	/**
	 * Constructor
	 * 
	 * @param db the multi-dimensional sequence database to display
	 */
	public TimeSequenceTableModel(SequenceDatabase db) {
		this.db = db;

		// Find the maximum number of itemsets in the database
		maxItemsets = 0;
		for (Sequence sequence : db.getSequences()) {
			if (sequence.size() > maxItemsets) {
				maxItemsets = sequence.size();
			}
		}
	}

	/**
	 * Get the number of rows in the table
	 * 
	 * @return the number of rows
	 */
	@Override
	public int getRowCount() {
		return db.size();
	}

	/**
	 * Get the number of columns in the table
	 * 
	 * @return the number of columns
	 */
	@Override
	public int getColumnCount() {
		return maxItemsets + 1;
	}

	/**
	 * Get the value at a given row and column in the table
	 * 
	 * @param rowIndex    the row index
	 * @param columnIndex the column index
	 * @return the value
	 */
	@Override
	// The method that returns the value at the given row and column index
	public Object getValueAt(int rowIndex, int columnIndex) {
		// If the column index is zero, return the row index
		if (columnIndex == 0) {
			return rowIndex;
		}
		// Otherwise, get the sequence at the given row index
		Sequence sequence = db.getSequences().get(rowIndex);
		// Check if the sequence has an itemset at the given column index minus one
		if (columnIndex <= sequence.size()) {
			// If yes, get the itemset at the given column index minus one
			Itemset itemset = sequence.get(columnIndex - 1);
			// Return the itemset as a string
			return "{t=" + itemset.getTimestamp() + ","+ itemset.toPrettyString() + "}";
		}
		return "";
	}

	/**
	 * Get the name of a given column in the table
	 * 
	 * @param columnIndex the column index
	 * @return the column name
	 */
	@Override
	public String getColumnName(int columnIndex) {
		// If the column index is zero, return "SID"
		if (columnIndex == 0) {
			return "Sequence ID";
		}
		// Otherwise, return the name of the itemset at the given index minus one
		return "Itemset " + (columnIndex - 1);
	}

	/**
	 * Get the type of a given column in the table
	 * 
	 * @param columnIndex the column index
	 * @return the column type
	 */
	@Override
	// The method that returns the class of the values in the column at the given
	// index
	public Class<?> getColumnClass(int columnIndex) {
		// If the column index is zero, return the class of Integer, since the SID is an
		// integer
		if (columnIndex == 0) {
			return Integer.class;
		}
		// Otherwise, return the class of String, since the table will display itemset
		// values or null values
		return String.class;
	}

	/**
	 * Check if a cell is editable
	 * 
	 * @param rowIndex    the row index
	 * @param columnIndex the column index
	 * @return false, as the cells are not editable
	 */
	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return false;
	}
}