package ca.pfv.spmf.gui.viewers.mdsequenceviewer;

import javax.swing.table.AbstractTableModel;

import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.multidimensionalsequentialpatterns.MDSequence;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.multidimensionalsequentialpatterns.MDSequenceDatabase;

/**
 * This class extends AbstractTableModel to create a model for representing a
 * multi-dimensional sequence database in a JTable.
 * 
 * @see MDSequenceDatabase
 * @see AbstractTableModel
 */
public class MDSequenceTableModel extends AbstractTableModel {

	/** The column names for the table */
	private final String[] columnNames = { "MD-Pattern", "Sequence" };

	/** The column types for the table */
	private final Class<?>[] columnTypes = { String.class, String.class };

	/** The multi-dimensional sequence database to display */
	MDSequenceDatabase db;

	/** if true, timestamps will be displayed. */
	private boolean displayTimestamps;

	/**
	 * Constructor
	 * 
	 * @param db the multi-dimensional sequence database to display
	 * @param displayTimeStamps      if true, timestamps will be displayed.
	 *                               Otherwise, not.
	 */
	public MDSequenceTableModel(MDSequenceDatabase db, boolean displayTimestamps) {
		this.db = db;
		this.displayTimestamps = displayTimestamps;
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
		return columnNames.length;
	}

	/**
	 * Get the value at a given row and column in the table
	 * 
	 * @param rowIndex    the row index
	 * @param columnIndex the column index
	 * @return the value
	 */
	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		// Get the md-sequence at the given row
		MDSequence mdsequence = db.getSequences().get(rowIndex);
		// Depending on the column, return the md-pattern or the sequence as a string
		switch (columnIndex) {
		case 0:
			return mdsequence.getMdpattern().toString();
		case 1:
			return mdsequence.getSequence().toString(displayTimestamps);
		default:
			return null;
		}
	}

	/**
	 * Get the name of a given column in the table
	 * 
	 * @param columnIndex the column index
	 * @return the column name
	 */
	@Override
	public String getColumnName(int columnIndex) {
		return columnNames[columnIndex];
	}

	/**
	 * Get the type of a given column in the table
	 * 
	 * @param columnIndex the column index
	 * @return the column type
	 */
	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return columnTypes[columnIndex];
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