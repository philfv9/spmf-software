package ca.pfv.spmf.gui;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

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
 * A JTable with enhanced functionality that allows hiding and showing rows and
 * columns.
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class SortableJTable extends JTable {
	/**
	 * serial UID
	 */
	private static final long serialVersionUID = 2175315054801735453L;
	/**
	 * A set of indices representing the hidden rows.
	 */
	private Set<Integer> hiddenRows = new HashSet<>();

	/**
	 * A counter for the number of hidden columns.
	 */
	private int numberOfHiddenColumns = 0;

	/**
	 * Constructs a SortableJTable with a default table model and initializes the
	 * sorter and mouse listeners.
	 */
	public SortableJTable() {
		super(new DefaultTableModel());
		initializeSorter();
		addTableHeaderMouseListener();
		addTableMouseListener();
	}

	/**
	 * Initializes the row sorter for this table.
	 */
	private void initializeSorter() {
		TableRowSorter<TableModel> sorter = new TableRowSorter<>(getModel());
		setRowSorter(sorter);
	}

	/**
	 * Adds a mouse listener to the table for right-click events.
	 */
	private void addTableMouseListener() {
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isRightMouseButton(e)) {
					showPopup(e.getX(), e.getY());
				}
			}
		});
	}

	/**
	 * Adds a mouse listener to the table header for right-click events.
	 */
	private void addTableHeaderMouseListener() {
		getTableHeader().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (SwingUtilities.isRightMouseButton(e)) {
					int col = columnAtPoint(e.getPoint());
					showHeaderPopup(e.getX(), e.getY(), col);
				}
			}
		});
	}

	/**
	 * Creates a popup menu with specified conditions for showing menu items.
	 *
	 * @param showHideColumn     if true, adds the "Hide Column" menu item
	 * @param showShowAllColumns if true, adds the "Show All Columns" menu item
	 * @param showHideRow        if true, adds the "Hide Row" menu item
	 * @param showShowAllRows    if true, adds the "Show All Rows" menu item
	 * @return a JPopupMenu with the specified items based on the conditions
	 */
	private JPopupMenu createPopupMenu(int x, int y, boolean showHideColumn, boolean showShowAllColumns,
			boolean showHideRow, boolean showShowAllRows) {
		JPopupMenu popupMenu = new JPopupMenu();
		if (showHideColumn) {
			JMenuItem hideColumnItem = new JMenuItem("Hide Column");
			hideColumnItem.addActionListener(e -> hideColumnAt(getColumnModel().getColumnIndexAtX(x)));
			popupMenu.add(hideColumnItem);
		}
		if (showShowAllColumns && numberOfHiddenColumns > 0) {
			JMenuItem showAllColumnsItem = new JMenuItem("Show All Columns");
			showAllColumnsItem.addActionListener(e -> showAllColumns());
			popupMenu.add(showAllColumnsItem);
		}
		if (showHideRow) {
			JMenuItem hideRowItem = new JMenuItem("Hide Row");
			hideRowItem.addActionListener(e -> hideRowAt(rowAtPoint(new Point(x, y))));
			popupMenu.add(hideRowItem);
		}
		if (showShowAllRows && hiddenRows.hashCode() > 0) {
			JMenuItem showAllRowsItem = new JMenuItem("Show All Rows");
			showAllRowsItem.addActionListener(e -> showAllRows());
			popupMenu.add(showAllRowsItem);
		}
		return popupMenu;
	}

	/**
	 * Displays a popup menu at the specified x and y coordinates.
	 *
	 * @param x the x coordinate where the popup will be shown
	 * @param y the y coordinate where the popup will be shown
	 */
	private void showPopup(int x, int y) {
		JPopupMenu popupMenu = createPopupMenu(x, y, false, false, true, true);
		popupMenu.show(this, x, y);
	}

	/**
	 * Displays a header popup menu at the specified x and y coordinates for the
	 * given column index.
	 *
	 * @param x        the x coordinate where the header popup will be shown
	 * @param y        the y coordinate where the header popup will be shown
	 * @param colIndex the index of the column where the header popup is invoked
	 */
	private void showHeaderPopup(int x, int y, int colIndex) {
		JPopupMenu headerPopup = createPopupMenu(x, y, true, true, false, false);
		headerPopup.show(getTableHeader(), x, y);
	}

	/**
	 * Hides the column at the specified index.
	 *
	 * @param colIndex the index of the column to hide
	 */
	private void hideColumnAt(int colIndex) {
		TableColumn column = getColumnModel().getColumn(colIndex);
		column.setMinWidth(0);
		column.setMaxWidth(0);
		column.setWidth(0);

		numberOfHiddenColumns++;
	}

	/**
	 * Shows all columns that may have been previously hidden.
	 */
	private void showAllColumns() {
		Enumeration<TableColumn> columns = getColumnModel().getColumns();
		while (columns.hasMoreElements()) {
			TableColumn column = columns.nextElement();
			column.setMinWidth(15);
			column.setMaxWidth(Integer.MAX_VALUE);
			column.setPreferredWidth(100);
		}

		numberOfHiddenColumns = 0;
	}

	/**
	 * Hides the row at the specified index.
	 *
	 * @param rowIndex the index of the row to hide
	 */
	private void hideRowAt(int rowIndex) {
		int modelIndex = convertRowIndexToModel(rowIndex);
		if (!hiddenRows.contains(modelIndex)) {
			hiddenRows.add(modelIndex);
			updateRowFilter();
		}
	}

	/**
	 * Shows all rows that may have been previously hidden.
	 */
	private void showAllRows() {
		hiddenRows.clear();
		updateRowFilter();
	}

	/**
	 * Updates the row filter to hide or show rows based on the hiddenRows set.
	 */
	private void updateRowFilter() {
		TableRowSorter<?> sorter = (TableRowSorter<?>) getRowSorter();
		sorter.setRowFilter(new RowFilter<Object, Object>() {
			@Override
			public boolean include(Entry<? extends Object, ? extends Object> entry) {
				return !hiddenRows.contains(entry.getIdentifier());
			}
		});
	}

	/**
	 * Sets the data model for this table and updates the row sorter if necessary.
	 *
	 * @param dataModel the TableModel to set for this table
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void setModel(TableModel dataModel) {
		super.setModel(dataModel);
		if (getRowSorter() instanceof TableRowSorter) {
			((TableRowSorter<TableModel>) getRowSorter()).setModel(dataModel);
		}
	}
}
