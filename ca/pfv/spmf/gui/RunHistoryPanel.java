package ca.pfv.spmf.gui;

/*
 * Copyright (c) 2008-2021 Philippe Fournier-Viger
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * A panel that displays a scrollable table of past algorithm runs. Each row
 * shows the timestamp, algorithm name, duration, input file, output file,
 * parameters, and status (OK or STOPPED) of one completed execution.
 *
 * <p>Only runs that completed successfully or were stopped by the user/time
 * limit are recorded. Runs that failed due to incorrect parameters or other
 * errors are not added to the history.</p>
 *
 * <p>Entries are added thread-safely via {@link #addEntry(RunHistoryEntry)},
 * which always dispatches the table-model update to the Event Dispatch Thread.</p>
 *
 * <p>The toolbar below the table contains two horizontally centered controls:</p>
 * <ul>
 *   <li><b>Re-run</b> — re-runs the selected configuration (also triggered by
 *       double-clicking a row)</li>
 *   <li><b>Clear history</b> — removes all entries from the table</li>
 * </ul>
 *
 * <p>The "View input file" and "View output file" buttons have been removed
 * from this panel. Output viewing is now handled by the dedicated "Output" tab
 * in {@link AlgorithmRunnerPanel}.</p>
 *
 * <p>All buttons except "Clear history" are disabled when no row is selected
 * and become enabled as soon as the user selects a row.</p>
 *
 * <p>Double-clicking a row fires the {@link ActionListener} registered via
 * {@link #addRerunListener(ActionListener)}. The controller retrieves the
 * actual entry via {@link #getSelectedEntry()}.</p>
 *
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class RunHistoryPanel extends JPanel {

    // =========================================================================
    // Column indices — keep in sync with COLUMN_NAMES
    // =========================================================================

    /** Column index for the run timestamp */
    private static final int COL_TIMESTAMP = 0;

    /** Column index for the algorithm name */
    private static final int COL_ALGORITHM = 1;

    /** Column index for the run duration */
    private static final int COL_DURATION  = 2;

    /** Column index for the input file path */
    private static final int COL_INPUT     = 3;

    /** Column index for the output file path */
    private static final int COL_OUTPUT    = 4;

    /** Column index for the serialised parameter list */
    private static final int COL_PARAMS    = 5;

    /** Column index for the status (OK or STOPPED) */
    private static final int COL_STATUS    = 6;

    /** Total number of columns in the table */
    private static final int COLUMN_COUNT  = 7;

    /** Display names for each column */
    private static final String[] COLUMN_NAMES = {
            "Timestamp", "Algorithm", "Duration",
            "Input file", "Output file", "Parameters", "Status"
    };

    // =========================================================================
    // Widget fields
    // =========================================================================

    /** The table that renders the history rows */
    private final JTable table;

    /** The model that backs the table */
    private final HistoryTableModel model;

    /** Button that removes all rows from the history */
    private final JButton buttonClear;

    /** Button that re-runs the currently selected entry */
    private final JButton buttonRerun;

    // =========================================================================
    // Construction
    // =========================================================================

    /**
     * Constructs the history panel with an empty table and the toolbar.
     * The toolbar contains only "Re-run" and "Clear history" buttons, both
     * horizontally centered.
     * The "View input file" and "View output file" buttons have been removed;
     * output viewing is now handled by the dedicated "Output" tab.
     */
    public RunHistoryPanel() {
        setLayout(new BorderLayout(0, 4));

        // ---- Table ----
        model = new HistoryTableModel();
        table = new SortableJTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(22);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setToolTipText("Double-click a row to re-run that configuration");

        // Apply a custom renderer that colours successful rows green and
        // stopped rows yellow so the user can see at a glance what happened
        StatusCellRenderer statusRenderer = new StatusCellRenderer();
        for (int i = 0; i < COLUMN_COUNT; i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(statusRenderer);
        }

        // Set preferred column widths
        table.getColumnModel().getColumn(COL_TIMESTAMP).setPreferredWidth(140);
        table.getColumnModel().getColumn(COL_ALGORITHM).setPreferredWidth(200);
        table.getColumnModel().getColumn(COL_DURATION) .setPreferredWidth(80);
        table.getColumnModel().getColumn(COL_INPUT)    .setPreferredWidth(180);
        table.getColumnModel().getColumn(COL_OUTPUT)   .setPreferredWidth(180);
        table.getColumnModel().getColumn(COL_PARAMS)   .setPreferredWidth(160);
        table.getColumnModel().getColumn(COL_STATUS)   .setPreferredWidth(80);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(600, 130));
        add(scrollPane, BorderLayout.CENTER);

        // ---- Toolbar with centered buttons ----
        JPanel toolbar = new JPanel(new GridBagLayout());
        toolbar.setBorder(new EmptyBorder(2, 0, 0, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 4, 0, 4);
        gbc.anchor = GridBagConstraints.CENTER;

        // Re-run button — fires the registered rerun listener or double-click
        buttonRerun = new JButton("Re-run");
        buttonRerun.setToolTipText(
                "Re-run the selected algorithm with the same files and parameters");
        buttonRerun.setEnabled(false);
        gbc.gridx = 0;
        gbc.gridy = 0;
        toolbar.add(buttonRerun, gbc);

        // Clear history button — always enabled
        buttonClear = new JButton("Clear history");
        buttonClear.setToolTipText("Remove all entries from the run history");
        gbc.gridx = 1;
        gbc.gridy = 0;
        toolbar.add(buttonClear, gbc);

        add(toolbar, BorderLayout.SOUTH);

        // ---- Selection listener — enable/disable buttons ----
        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int row = table.getSelectedRow();
                    boolean hasSelection = row >= 0;
                    buttonRerun.setEnabled(hasSelection);
                }
            }
        });
    }

    // =========================================================================
    // Public API — entry management
    // =========================================================================

    /**
     * Adds a new entry to the top of the history table. Safe to call from any
     * thread — the model update is always dispatched to the EDT.
     *
     * @param entry the completed run entry to add
     */
    public void addEntry(final RunHistoryEntry entry) {
        if (entry == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            model.addEntry(entry);
            // Scroll to the top so the latest run is immediately visible
            if (table.getRowCount() > 0) {
                table.scrollRectToVisible(table.getCellRect(0, 0, true));
            }
        });
    }

    /**
     * Removes all entries from the history table. Safe to call from any thread.
     */
    public void clearHistory() {
        SwingUtilities.invokeLater(model::clear);
    }

    /**
     * Returns the {@link RunHistoryEntry} that is currently selected in the
     * table, or {@code null} if no row is selected.
     *
     * @return the selected entry, or null
     */
    public RunHistoryEntry getSelectedEntry() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return model.getEntry(row);
    }

    /**
     * Returns an unmodifiable snapshot of all entries currently in the history,
     * ordered from newest (index 0) to oldest (last index).
     *
     * @return list of all history entries
     */
    public List<RunHistoryEntry> getAllEntries() {
        return model.getAllEntries();
    }

    // =========================================================================
    // Listener registration
    // =========================================================================

    /**
     * Registers an {@link ActionListener} on the "Re-run" button. The listener
     * is also fired when the user double-clicks a table row. In both cases the
     * selected entry can be retrieved via {@link #getSelectedEntry()}.
     *
     * @param listener the listener to add
     */
    public void addRerunListener(ActionListener listener) {
        buttonRerun.addActionListener(listener);

        // Also fire the same listener on double-click so the user does not have
        // to move the mouse to the button after selecting a row
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0) {
                    listener.actionPerformed(
                            new java.awt.event.ActionEvent(
                                    RunHistoryPanel.this,
                                    java.awt.event.ActionEvent.ACTION_PERFORMED,
                                    "rerun"));
                }
            }
        });
    }

    /**
     * Registers an {@link ActionListener} on the "Clear history" button.
     *
     * @param listener the listener to add
     */
    public void addClearHistoryListener(ActionListener listener) {
        buttonClear.addActionListener(listener);
    }

    // =========================================================================
    // Inner class — table model
    // =========================================================================

    /**
     * A simple {@link AbstractTableModel} that stores {@link RunHistoryEntry}
     * objects in a list and exposes them as table rows. New entries are
     * inserted at position 0 (newest first).
     */
    private static final class HistoryTableModel extends AbstractTableModel {

        /** The backing list; entry at index 0 is the most recent */
        private final List<RunHistoryEntry> entries = new ArrayList<>();

        /**
         * Inserts a new entry at the top of the list and notifies listeners.
         *
         * @param entry the entry to insert
         */
        void addEntry(RunHistoryEntry entry) {
            entries.add(0, entry);
            fireTableRowsInserted(0, 0);
        }

        /**
         * Removes all entries and notifies listeners.
         */
        void clear() {
            int size = entries.size();
            if (size > 0) {
                entries.clear();
                fireTableRowsDeleted(0, size - 1);
            }
        }

        /**
         * Returns the entry at the given row index.
         *
         * @param row the row index
         * @return the entry at that row
         */
        RunHistoryEntry getEntry(int row) {
            return entries.get(row);
        }

        /**
         * Returns an unmodifiable snapshot of all entries.
         *
         * @return list of all entries
         */
        List<RunHistoryEntry> getAllEntries() {
            return java.util.Collections.unmodifiableList(
                    new ArrayList<>(entries));
        }

        /** {@inheritDoc} */
        @Override
        public int getRowCount() {
            return entries.size();
        }

        /** {@inheritDoc} */
        @Override
        public int getColumnCount() {
            return COLUMN_COUNT;
        }

        /** {@inheritDoc} */
        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        /** {@inheritDoc} */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            RunHistoryEntry e = entries.get(rowIndex);
            switch (columnIndex) {
                case COL_TIMESTAMP: return e.getFormattedTimestamp();
                case COL_ALGORITHM: return e.getAlgorithmName();
                case COL_DURATION:  return e.getFormattedDuration();
                case COL_INPUT:     return e.getInputFile();
                case COL_OUTPUT:    return e.getOutputFile();
                case COL_PARAMS:    return Arrays.toString(e.getParameters());
                case COL_STATUS:    return e.getStatus();
                default:            return "";
            }
        }

        /** {@inheritDoc} */
        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            // History is read-only
            return false;
        }
    }

    // =========================================================================
    // Inner class — cell renderer
    // =========================================================================

    /**
     * A cell renderer that colours an entire row light-green when the run
     * succeeded (status OK) and light-yellow when it was stopped, making
     * status visible at a glance without relying solely on the text in the
     * Status column.
     */
    private static final class StatusCellRenderer extends DefaultTableCellRenderer {

        /** Background used for rows whose run succeeded (status OK) */
        private static final Color COLOR_SUCCESS = new Color(220, 255, 220);

        /** Background used for rows whose run was stopped */
        private static final Color COLOR_STOPPED = new Color(255, 255, 200);

        /** Font used for cell text */
        private static final Font CELL_FONT =
                new Font(Font.DIALOG, Font.PLAIN, 11);

        /** {@inheritDoc} */
        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
            super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);

            setFont(CELL_FONT);

            if (!isSelected) {
                // Determine status from the Status column value of this row
                Object statusVal = table.getModel().getValueAt(row, COL_STATUS);
                boolean ok = RunHistoryEntry.STATUS_OK.equals(statusVal);
                setBackground(ok ? COLOR_SUCCESS : COLOR_STOPPED);
            } else {
                // Keep the default selection background when the row is highlighted
                setBackground(table.getSelectionBackground());
            }

            // Show the full cell text as a tooltip so truncated paths are readable
            setToolTipText(value != null ? value.toString() : "");

            return this;
        }
    }
}