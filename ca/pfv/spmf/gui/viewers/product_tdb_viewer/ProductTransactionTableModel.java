package ca.pfv.spmf.gui.viewers.product_tdb_viewer;

import javax.swing.table.AbstractTableModel;

import ca.pfv.spmf.input.product_transaction_database.ProductTransaction;
import ca.pfv.spmf.input.product_transaction_database.ProductTransactionDatabase;
/*
 * Copyright (c) 2024 Philippe Fournier-Viger
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
 * A JTable model for visualizing a transaction database with profit information (as used by erasable itemset mining algorithms such as VME) in SPMF format.
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class ProductTransactionTableModel extends AbstractTableModel {

	/** The database */
	private ProductTransactionDatabase database;

	/** Flag to indicate whether to display items as lists or as columns */
	private boolean displayAsLists;

	/**
	 * Constructor
	 * 
	 * @param database the database
	 */
	public ProductTransactionTableModel(ProductTransactionDatabase database) {
		this.database = database;
		this.displayAsLists = false;
	}

	/**
	 * Constructor
	 * 
	 * @param database       the database
	 * @param displayAsLists true to display items as lists, false to display as
	 *                       columns
	 */
	public ProductTransactionTableModel(ProductTransactionDatabase database, boolean displayAsLists) {
		this.database = database;
		this.displayAsLists = displayAsLists;
	}

	/**
	 * Get the number of columns
	 * 
	 * @return the number of columns
	 */
	public int getColumnCount() {
		if (displayAsLists) {
			return 4; // Transaction, Items, Profit, Length
		}
		return database.getItems().size() + 2;
	}

	/**
	 * Get the number of rows
	 * 
	 * @return the number of rows
	 */
	public int getRowCount() {
		if (displayAsLists) {
			return database.size();
		}
		return database.size() + 1; // +1 for total count row
	}

	/**
	 * Get the value at a given position in the table
	 * 
	 * @param row    the row number
	 * @param column the column number
	 * @return the value
	 */
	public Object getValueAt(int row, int column) {
		if (displayAsLists) {
			ProductTransaction transaction = database.getTransactions().get(row);
			switch (column) {
			case 0:
				return Integer.toString(row);
			case 1:
				StringBuilder sb = new StringBuilder();
				boolean first = true;
				for (Integer item : transaction.getItems()) {
					if (!first) {
						sb.append(", ");
					}
					sb.append("Item " + item);
					first = false;
				}
				return sb.toString();
			case 2:
				return transaction.getProfit();
			case 3:
				return transaction.size();
			default:
				return "";
			}
		} else {
			// Total count row (last row)
			if (row == database.size()) {
				if (column == 0) {
					return "Total count:";
				} else if (column == database.getItems().size() + 1) {
					// Sum of all profits
					int totalProfit = 0;
					for (ProductTransaction transaction : database.getTransactions()) {
						totalProfit += transaction.getProfit();
					}
					return totalProfit;
				} else {
					int item = database.getItems().get(column - 1);
					int count = 0;
					for (ProductTransaction transaction : database.getTransactions()) {
						if (transaction.contains(item)) {
							count++;
						}
					}
					return Integer.toString(count);
				}
			}

			if (column == 0) {
				return "T" + row;
			} else if (column == database.getItems().size() + 1) {
				return database.getTransactions().get(row).getProfit();
			} else {
				ProductTransaction transaction = database.getTransactions().get(row);
				Integer item = database.getItems().get(column - 1);
				if (transaction.contains(item)) {
					return "Item " + item;
				} else {
					return "";
				}
			}
		}
	}

	/**
	 * Get the name of a given column
	 * 
	 * @param column the column number
	 * @return the column name
	 */
	public String getColumnName(int column) {
		if (displayAsLists) {
			switch (column) {
			case 0:
				return "Transaction";
			case 1:
				return "Items";
			case 2:
				return "Profit";
			case 3:
				return "Length";
			default:
				return "";
			}
		} else {
			if (column == 0) {
				return "Transaction";
			} else if (column == database.getItems().size() + 1) {
				return "Profit";
			} else {
				return "Item " + database.getItems().get(column - 1);
			}
		}
	}

	/**
	 * Get the class of the values in the column at the given index.
	 * 
	 * @param columnIndex the index
	 * @return the class
	 */
	@Override
	public Class<?> getColumnClass(int columnIndex) {
		if (displayAsLists) {
			if (columnIndex == 2 || columnIndex == 3) {
				return Integer.class;
			}
			return String.class;
		} else {
			if (columnIndex == 0) {
				return String.class;
			}
			if (columnIndex == database.getItems().size() + 1) {
				return Integer.class;
			}
			return String.class;
		}
	}

	/**
	 * Get the database
	 * 
	 * @return the database
	 */
	public ProductTransactionDatabase getDatabase() {
		return database;
	}
}