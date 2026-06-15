package ca.pfv.spmf.gui;

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
 *
 * Do not remove copyright or license information.
 */

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

/**
 * A JTable with enhanced functionality that allows hiding and showing rows and
 * columns via a right-click context menu on the table body and the table header.
 *
 * <p>Hidden columns have their original preferred width saved so that
 * {@link #showAllColumns()} can restore each column to its previous width
 * rather than resetting everything to a hardcoded default.</p>
 *
 * <p>Hidden rows are tracked by their model index and excluded via a
 * {@link RowFilter} on the {@link TableRowSorter}, so sorting and filtering
 * continue to work correctly after rows are hidden.</p>
 *
 * @author Philippe Fournier-Viger, 2024
 */
public class SortableJTable extends JTable {

    /** Serial UID */
    private static final long serialVersionUID = 2175315054801735453L;

    /**
     * A set of model-row indices representing the currently hidden rows.
     * Using model indices means the hidden set remains correct even after
     * the user re-sorts the table.
     */
    private final Set<Integer> hiddenRows = new HashSet<>();

    /**
     * A map from column model index to the preferred width the column had
     * before it was hidden. Used by {@link #showAllColumns()} to restore
     * each column to its original width instead of a hardcoded default.
     */
    private final Map<Integer, Integer> hiddenColumnWidths = new HashMap<>();

    /**
     * Constructs a SortableJTable with a default table model and initialises
     * the row sorter and right-click mouse listeners on both the body and
     * the header.
     */
    public SortableJTable() {
        super(new DefaultTableModel());
        initializeSorter();
        addTableHeaderMouseListener();
        addTableMouseListener();
    }

    /**
     * Constructs a SortableJTable with a  table model and initialises
     * the row sorter and right-click mouse listeners on both the body and
     * the header.
     * @param model the table model
     */
    public SortableJTable(TableModel model) {
        super(model);
        initializeSorter();
        addTableHeaderMouseListener();
        addTableMouseListener();
    }
    
    /**
     * Initialises the {@link TableRowSorter} for this table.
     */
    private void initializeSorter() {
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(getModel());
        setRowSorter(sorter);
    }

    /**
     * Adds a right-click mouse listener to the table body. Right-clicking
     * on a row shows a popup menu that offers to hide that row or to
     * restore all hidden rows.
     */
    private void addTableMouseListener() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showRowPopup(e.getX(), e.getY());
                }
            }
        });
    }

    /**
     * Adds a right-click mouse listener to the table header. Right-clicking
     * on a column header shows a popup menu that offers to hide that column
     * or to restore all hidden columns.
     */
    private void addTableHeaderMouseListener() {
        getTableHeader().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showColumnPopup(e.getX(), e.getY());
                }
            }
        });
    }

    /**
     * Builds and displays a popup menu for right-clicks on the table body.
     * The menu contains a "Hide Row" item for the row under the cursor, and
     * a "Show All Rows" item if any rows are currently hidden. If neither
     * condition applies the popup is not shown.
     *
     * @param x the x coordinate of the right-click, in table coordinates
     * @param y the y coordinate of the right-click, in table coordinates
     */
    private void showRowPopup(int x, int y) {
        int rowIndex = rowAtPoint(new Point(x, y));

        JPopupMenu popup = new JPopupMenu();

        // Add "Hide Row" only when the click landed on a valid row
        if (rowIndex >= 0) {
            JMenuItem hideRowItem = new JMenuItem("Hide Row");
            hideRowItem.addActionListener(e -> hideRowAt(rowIndex));
            popup.add(hideRowItem);
        }

        // Add "Show All Rows" only when at least one row is currently hidden
        if (!hiddenRows.isEmpty()) {
            JMenuItem showAllRowsItem = new JMenuItem("Show All Rows");
            showAllRowsItem.addActionListener(e -> showAllRows());
            popup.add(showAllRowsItem);
        }

        // Do not show an empty popup
        if (popup.getComponentCount() > 0) {
            popup.show(this, x, y);
        }
    }

    /**
     * Builds and displays a popup menu for right-clicks on the table header.
     * The menu contains a "Hide Column" item for the column under the cursor,
     * and a "Show All Columns" item if any columns are currently hidden. If
     * neither condition applies the popup is not shown.
     *
     * @param x the x coordinate of the right-click, in header coordinates
     * @param y the y coordinate of the right-click, in header coordinates
     */
    private void showColumnPopup(int x, int y) {
        int colIndex = getColumnModel().getColumnIndexAtX(x);

        JPopupMenu popup = new JPopupMenu();

        // Add "Hide Column" only when the click landed on a valid column
        if (colIndex >= 0) {
            JMenuItem hideColumnItem = new JMenuItem("Hide Column");
            hideColumnItem.addActionListener(e -> hideColumnAt(colIndex));
            popup.add(hideColumnItem);
        }

        // Add "Show All Columns" only when at least one column is currently hidden
        if (!hiddenColumnWidths.isEmpty()) {
            JMenuItem showAllColumnsItem = new JMenuItem("Show All Columns");
            showAllColumnsItem.addActionListener(e -> showAllColumns());
            popup.add(showAllColumnsItem);
        }

        // Do not show an empty popup
        if (popup.getComponentCount() > 0) {
            popup.show(getTableHeader(), x, y);
        }
    }

    /**
     * Hides the column at the given column-model index by collapsing its
     * width to zero. The column's current preferred width is saved in
     * {@link #hiddenColumnWidths} so that {@link #showAllColumns()} can
     * restore it accurately.
     *
     * @param colIndex the column-model index of the column to hide
     */
    private void hideColumnAt(int colIndex) {
        if (colIndex < 0 || colIndex >= getColumnModel().getColumnCount()) {
            return;
        }
        TableColumn column = getColumnModel().getColumn(colIndex);

        // Save the preferred width before collapsing so we can restore it later
        hiddenColumnWidths.put(colIndex, column.getPreferredWidth());

        column.setMinWidth(0);
        column.setMaxWidth(0);
        column.setWidth(0);
    }

    /**
     * Restores all previously hidden columns to their saved preferred widths.
     * After restoration the saved-widths map is cleared.
     */
    public void showAllColumns() {
        Enumeration<TableColumn> columns = getColumnModel().getColumns();
        int index = 0;
        while (columns.hasMoreElements()) {
            TableColumn column = columns.nextElement();
            // Restore the saved width, or fall back to 100 for columns
            // that were never explicitly hidden through this method
            int restoredWidth = hiddenColumnWidths.getOrDefault(index, 100);
            column.setMinWidth(15);
            column.setMaxWidth(Integer.MAX_VALUE);
            column.setPreferredWidth(restoredWidth);
            index++;
        }
        hiddenColumnWidths.clear();
    }

    /**
     * Hides the row at the given view-row index. The index is converted to
     * a model index before being stored so that the hidden set remains
     * correct even after the user re-sorts the table. If the index is
     * invalid or the row is already hidden, this method does nothing.
     *
     * @param rowIndex the view-row index of the row to hide
     */
    private void hideRowAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= getRowCount()) {
            return;
        }
        int modelIndex = convertRowIndexToModel(rowIndex);
        if (hiddenRows.add(modelIndex)) {
            // add() returns true only if the element was not already present
            updateRowFilter();
        }
    }

    /**
     * Restores all previously hidden rows by clearing the hidden-rows set
     * and refreshing the row filter.
     */
    public void showAllRows() {
        hiddenRows.clear();
        updateRowFilter();
    }

    /**
     * Updates the {@link RowFilter} on the row sorter to exclude all rows
     * whose model index is present in {@link #hiddenRows}. Does nothing if
     * the current row sorter is not a {@link TableRowSorter}.
     */
    private void updateRowFilter() {
        if (!(getRowSorter() instanceof TableRowSorter)) {
            return;
        }
        @SuppressWarnings("unchecked")
        TableRowSorter<TableModel> sorter =
                (TableRowSorter<TableModel>) getRowSorter();
        sorter.setRowFilter(new RowFilter<TableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends TableModel, ? extends Integer> entry) {
                // entry.getIdentifier() returns the model row index
                return !hiddenRows.contains(entry.getIdentifier());
            }
        });
    }

    /**
     * Sets the data model for this table and updates the row sorter to use
     * the new model if the current sorter is a {@link TableRowSorter}.
     *
     * @param dataModel the {@link TableModel} to set for this table
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