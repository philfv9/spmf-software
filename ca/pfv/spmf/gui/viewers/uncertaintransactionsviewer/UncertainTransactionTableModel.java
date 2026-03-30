package ca.pfv.spmf.gui.viewers.uncertaintransactionsviewer;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import ca.pfv.spmf.algorithms.frequentpatterns.uapriori.ItemUApriori;
import ca.pfv.spmf.algorithms.frequentpatterns.uapriori.ItemsetUApriori;
import ca.pfv.spmf.algorithms.frequentpatterns.uapriori.UncertainTransactionDatabase;
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
 * The table model to display the uncertain transactions
 *  @author Philippe Fournier-Viger, 2024
 */
class UncertainTransactionTableModel implements TableModel {

	/** The UncertainTransactionDatabase object that holds the data **/
	UncertainTransactionDatabase db;

	/** The list of listeners for this table model */
	private List<TableModelListener> listeners;

	/**
	 * The constructor that takes a UncertainTransactionDatabase object as parameter
	 * 
	 * @param db an uncertain transaction database object
	 */
	public UncertainTransactionTableModel(UncertainTransactionDatabase db) {
		// Initialize the fields
		this.db = db;
		this.listeners = new ArrayList<TableModelListener>();
	}

	/**
	 * Get the number of rows in the table
	 * 
	 * @return number of rows
	 */
	public int getRowCount() {
		// Return the number of transactions in the database plus one
		return db.size();
	}

	/**
	 * Get the number of columns in the table
	 * 
	 * @return number of columns
	 */
	public int getColumnCount() {
		// Return the number of items in the database plus two
		return db.getAllItems().size() + 1;
	}

	/**
	 * Get the name of the column at the given index
	 * 
	 * @param columnIndex the column index
	 * @return the name
	 */
	public String getColumnName(int columnIndex) {
		// If the column index is zero, return "TID"
		if (columnIndex == 0) {
			return "Transaction";
		}
		// Otherwise, return the name of the item at the given index minus one
		// Assume that the items are sorted in ascending order
//			return "Item " + columnIndex;
		return getItemName(columnIndex, true);
	}
	
	/**
	 * Get the item name for a given column index
	 * 
	 * @param itemID the item
	 * @return the item name
	 */
	private String getItemName(int itemID, boolean forColumnName) {
			if (forColumnName) {
				return "Item "+ itemID;
			} else {
				return "" + itemID;
			}
	}


	/**
	 * Get the class of the values in the column at the given index.
	 * 
	 * @param columnIndex the index
	 * @return the class
	 */
	public Class<?> getColumnClass(int columnIndex) {
		// If the column index is zero, return the class of Integer, since the TID is an
		// integer
		if (columnIndex == 0) {
			return Integer.class;
		}
		// If the column index is equal to the number of items plus one, return the
		// class of Double, since the TP is a
		// double
		if (columnIndex == db.getAllItems().size() + 1) {
			return Double.class;
		}
		// Otherwise, check the row index of the last row
		int rowIndex = db.size();

		// Otherwise, return the class of String, since the table will display item
		// names and probabilities
		return String.class;
	}

	/**
	 * Get the value at the given row and column index
	 * 
	 * @param rowIndex    the row
	 * @param columnIndex the column
	 * @return the object
	 */
	public Object getValueAt(int rowIndex, int columnIndex) {
		// Otherwise, use the modified code
		// If the column index is zero, return the row index
		if (columnIndex == 0) {
			return Integer.toString(rowIndex);
		}

		// Otherwise, get the transaction at the given row index
		ItemsetUApriori transaction = db.getTransactions().get(rowIndex);
		// Get the item at the given column index minus one
		// Assume that the items are sorted in ascending order
		int item = columnIndex;
		// Return the item name and probability if the transaction contains the item, empty
		// string otherwise
		for (ItemUApriori itemUApriori : transaction.getItems()) {
			if (itemUApriori.getId() == item) {
//					return getItemName(columnIndex, false) + " (" + itemUApriori.getProbability() + ")";
				return "Prob.: " + itemUApriori.getProbability();
			}
		}
		return "";
	}

	/**
	 * Get whether the cell at the given row and column index is editable
	 * 
	 * @param rowIndex    the row
	 * @param columnIndex the column
	 * @return a boolean indicating if the cell is editable
	 */
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		// Return false, since the table is not editable
		return false;
	}

	/**
	 * Set the value at the given row and column index
	 * 
	 * @param rowIndex    the row
	 * @param columnIndex the column
	 * @param aValue      the object
	 */
	public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
		// Do nothing, since the table is not editable
	}

	/**
	 * Add a listener to this table model
	 * 
	 * @param l a TableModelListener object
	 */
	public void addTableModelListener(TableModelListener l) {
		// Add the listener to the list
		listeners.add(l);
	}

	/**
	 * Remove a listener from this table model
	 * 
	 * @param l a TableModelListener object
	 */
	public void removeTableModelListener(TableModelListener l) {
		// Remove the listener from the list
		listeners.remove(l);
	}
}
