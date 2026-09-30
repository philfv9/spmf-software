package ca.pfv.spmf.gui;

/*
 * Copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * Do not remove copyright and license
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

/**
 * A panel that displays a scrollable, sortable table of past algorithm runs.
 * Each row shows the timestamp, algorithm name, duration, input file, output
 * file, parameters, and status (OK or STOPPED) of one completed execution.
 *
 * <p>Only runs that completed successfully or were stopped by the user/time
 * limit are recorded. Runs that failed due to incorrect parameters or other
 * errors are not added to the history.</p>
 *
 * <p>Entries are added thread-safely via {@link #addEntry(RunHistoryEntry)},
 * which always performs the table-model update on the Event Dispatch Thread.
 * At most {@link #MAX_ENTRIES} entries are kept; the oldest are discarded.</p>
 *
 * <p>The toolbar below the table contains two horizontally centered controls:</p>
 * <ul>
 *   <li><b>Re-run</b> — re-runs the selected configuration (also triggered by
 *       double-clicking a row or pressing <b>Enter</b> on a selected row)</li>
 *   <li><b>Clear history</b> — removes all entries from the table</li>
 * </ul>
 *
 * <p>"Re-run" is disabled when no row is selected. "Clear history" is only
 * enabled when the table contains at least one entry.</p>
 *
 * <p>Listeners registered via {@link #addRerunListener(ActionListener)} are
 * notified for the button click, double-click and Enter key alike; the
 * controller retrieves the actual entry via {@link #getSelectedEntry()}.
 * The panel clears itself when "Clear history" is clicked and then notifies
 * listeners registered via {@link #addClearHistoryListener(ActionListener)}.</p>
 *
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class RunHistoryPanel extends JPanel {

    // =========================================================================
    // Constants
    // =========================================================================

    /** Maximum number of entries kept in the history; oldest are discarded */
    public static final int MAX_ENTRIES = 200;

    /** Action command used when the re-run action is fired */
    public static final String ACTION_RERUN = "rerun";

    /** Action command used when the clear-history action is fired */
    public static final String ACTION_CLEAR = "clearHistory";

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

    /** Display names for each column — index must match the COL_* constants */
    private static final String[] COLUMN_NAMES = {
            "Timestamp", "Algorithm", "Duration",
            "Input file", "Output file", "Parameters", "Status"
    };

    /** Preferred pixel widths for each column — index must match COL_* */
    private static final int[] COLUMN_WIDTHS = {
            140, 200, 80, 180, 180, 160, 80
    };

    /** Total number of columns in the table */
    private static final int COLUMN_COUNT = COLUMN_NAMES.length;

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

    /** External listeners notified after the history has been cleared */
    private final List<ActionListener> clearListeners = new CopyOnWriteArrayList<>();

    // =========================================================================
    // Construction
    // =========================================================================

    /**
     * Constructs the history panel with an empty table and the toolbar
     * containing the horizontally centered "Re-run" and "Clear history"
     * buttons.
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
        table.setToolTipText("Double-click a row (or press Enter) to re-run that configuration");

        // Colour rows by status. Registering as the default renderer (rather
        // than per column) means it survives column re-creation by the table.
        table.setDefaultRenderer(Object.class, new StatusCellRenderer());

        TableColumnModel columns = table.getColumnModel();
        for (int i = 0; i < COLUMN_COUNT; i++) {
            columns.getColumn(i).setPreferredWidth(COLUMN_WIDTHS[i]);
        }

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(600, 130));
        add(scrollPane, BorderLayout.CENTER);

        // ---- Toolbar with centered buttons ----
        JPanel toolbar = new JPanel(new GridBagLayout());
        toolbar.setBorder(new EmptyBorder(2, 0, 0, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 4, 0, 4);
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.gridy  = 0;

        buttonRerun = new JButton("Re-run");
        buttonRerun.setActionCommand(ACTION_RERUN);
        buttonRerun.setToolTipText(
                "Re-run the selected algorithm with the same files and parameters");
        buttonRerun.setEnabled(false);
        gbc.gridx = 0;
        toolbar.add(buttonRerun, gbc);

        buttonClear = new JButton("Clear history");
        buttonClear.setActionCommand(ACTION_CLEAR);
        buttonClear.setToolTipText("Remove all entries from the run history");
        buttonClear.setEnabled(false);
        buttonClear.addActionListener(e -> {
            // Clear first, then tell interested parties (in registration order)
            model.clear();
            fireClearListeners();
        });
        gbc.gridx = 1;
        toolbar.add(buttonClear, gbc);

        add(toolbar, BorderLayout.SOUTH);

        // ---- Selection listener — enable/disable "Re-run" ----
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                buttonRerun.setEnabled(table.getSelectedRow() >= 0);
            }
        });

        // ---- Model listener — keep "Clear history" in sync with row count ----
        model.addTableModelListener(e -> buttonClear.setEnabled(model.getRowCount() > 0));

        // ---- Double-click on a row re-runs that row ----
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                int viewRow = table.rowAtPoint(e.getPoint());
                if (viewRow >= 0) {
                    // Make sure the clicked row is the selected one before firing
                    table.setRowSelectionInterval(viewRow, viewRow);
                    fireRerun();
                }
            }
        });

        // ---- Enter on a selected row re-runs it ----
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
             .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), ACTION_RERUN);
        table.getActionMap().put(ACTION_RERUN, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fireRerun();
            }
        });
    }

    // =========================================================================
    // Public API — entry management
    // =========================================================================

    /**
     * Adds a new entry to the top of the history table. Safe to call from any
     * thread — the model update is always performed on the EDT. If the history
     * exceeds {@link #MAX_ENTRIES}, the oldest entry is discarded.
     *
     * @param entry the completed run entry to add (ignored if null)
     */
    public void addEntry(final RunHistoryEntry entry) {
        if (entry == null) {
            return;
        }
        runOnEdt(() -> {
            model.addEntry(entry);
            // Scroll so the newest run (model row 0) is visible, even if the
            // user has sorted the table and it is not at view row 0.
            int viewRow = table.convertRowIndexToView(0);
            if (viewRow >= 0) {
                table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
            }
        });
    }

    /**
     * Removes all entries from the history table. Safe to call from any thread.
     * Does <em>not</em> notify listeners registered via
     * {@link #addClearHistoryListener(ActionListener)}; those are only fired
     * when the user clicks the button.
     */
    public void clearHistory() {
        runOnEdt(model::clear);
    }

    /**
     * Returns the {@link RunHistoryEntry} that is currently selected in the
     * table, or {@code null} if no row is selected. Sorting is taken into
     * account. Should be called on the EDT.
     *
     * @return the selected entry, or null
     */
    public RunHistoryEntry getSelectedEntry() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        return model.getEntry(modelRow);
    }

    /**
     * Returns an unmodifiable snapshot of all entries currently in the history,
     * ordered from newest (index 0) to oldest (last index), regardless of how
     * the table is currently sorted. Should be called on the EDT.
     *
     * @return list of all history entries
     */
    public List<RunHistoryEntry> getAllEntries() {
        return model.getAllEntries();
    }

    /**
     * Returns the number of entries currently in the history.
     *
     * @return the entry count
     */
    public int getEntryCount() {
        return model.getRowCount();
    }

    // =========================================================================
    // Listener registration
    // =========================================================================

    /**
     * Registers an {@link ActionListener} that is notified when the user
     * requests a re-run, either by clicking "Re-run", double-clicking a row,
     * or pressing Enter on a selected row. The selected entry can be
     * retrieved via {@link #getSelectedEntry()}.
     *
     * @param listener the listener to add (ignored if null)
     */
    public void addRerunListener(ActionListener listener) {
        if (listener != null) {
            buttonRerun.addActionListener(listener);
        }
    }

    /**
     * Unregisters a listener previously added with
     * {@link #addRerunListener(ActionListener)}.
     *
     * @param listener the listener to remove
     */
    public void removeRerunListener(ActionListener listener) {
        buttonRerun.removeActionListener(listener);
    }

    /**
     * Registers an {@link ActionListener} that is notified <em>after</em> the
     * history has been cleared because the user clicked "Clear history".
     *
     * @param listener the listener to add (ignored if null)
     */
    public void addClearHistoryListener(ActionListener listener) {
        if (listener != null) {
            clearListeners.add(listener);
        }
    }

    /**
     * Unregisters a listener previously added with
     * {@link #addClearHistoryListener(ActionListener)}.
     *
     * @param listener the listener to remove
     */
    public void removeClearHistoryListener(ActionListener listener) {
        clearListeners.remove(listener);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Fires the re-run action through the button so that all registered
     * listeners are notified exactly as if the button had been clicked.
     * Does nothing if the button is disabled (i.e. no row is selected).
     */
    private void fireRerun() {
        if (buttonRerun.isEnabled()) {
            buttonRerun.doClick(0);
        }
    }

    /**
     * Notifies all clear-history listeners in registration order.
     */
    private void fireClearListeners() {
        ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ACTION_CLEAR);
        for (ActionListener listener : clearListeners) {
            listener.actionPerformed(event);
        }
    }

    /**
     * Runs the given task immediately if already on the EDT, otherwise
     * schedules it with {@link SwingUtilities#invokeLater(Runnable)}.
     *
     * @param task the task to run
     */
    private static void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    // =========================================================================
    // Inner class — table model
    // =========================================================================

    /**
     * A simple {@link AbstractTableModel} that stores {@link RunHistoryEntry}
     * objects in a list and exposes them as table rows. New entries are
     * inserted at position 0 (newest first) and the list is capped at
     * {@link RunHistoryPanel#MAX_ENTRIES}.
     */
    private static final class HistoryTableModel extends AbstractTableModel {

        /** The backing list; entry at index 0 is the most recent */
        private final List<RunHistoryEntry> entries = new ArrayList<>();

        /**
         * Inserts a new entry at the top of the list, drops the oldest entry if
         * the cap is exceeded, and notifies listeners.
         *
         * @param entry the entry to insert
         */
        void addEntry(RunHistoryEntry entry) {
            entries.add(0, entry);
            fireTableRowsInserted(0, 0);
            if (entries.size() > MAX_ENTRIES) {
                int last = entries.size() - 1;
                entries.remove(last);
                fireTableRowsDeleted(last, last);
            }
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
         * Returns the entry at the given <em>model</em> row index.
         *
         * @param row the model row index
         * @return the entry at that row
         */
        RunHistoryEntry getEntry(int row) {
            return entries.get(row);
        }

        /**
         * Returns an unmodifiable snapshot of all entries.
         *
         * @return list of all entries, newest first
         */
        List<RunHistoryEntry> getAllEntries() {
            return Collections.unmodifiableList(new ArrayList<>(entries));
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
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
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
                case COL_PARAMS:    return e.getParametersAsString();
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
     * succeeded (status OK) and light-yellow when it was stopped, making the
     * status visible at a glance. The status is looked up in the model using
     * the <em>model</em> row index so that colouring stays correct when the
     * table is sorted.
     */
    private static final class StatusCellRenderer extends DefaultTableCellRenderer {

        /** Background used for rows whose run succeeded (status OK) */
        private static final Color COLOR_SUCCESS = new Color(220, 255, 220);

        /** Background used for rows whose run was stopped */
        private static final Color COLOR_STOPPED = new Color(255, 255, 200);

        /** Font used for cell text */
        private static final Font CELL_FONT = new Font(Font.DIALOG, Font.PLAIN, 11);

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
            setHorizontalAlignment(
                    column == COL_STATUS || column == COL_DURATION
                            ? SwingConstants.CENTER
                            : SwingConstants.LEADING);

            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
            } else {
                setBackground(isSuccessRow(table, row) ? COLOR_SUCCESS : COLOR_STOPPED);
                setForeground(table.getForeground());
            }

            // Show the full cell text as a tooltip so truncated paths are
            // readable; suppress the tooltip entirely for empty cells.
            String text = value != null ? value.toString() : "";
            setToolTipText(text.isEmpty() ? null : text);

            return this;
        }

        /**
         * Determines whether the given view row represents a successful run.
         * Tolerates transient index mismatches that can occur while the model
         * is being updated.
         *
         * @param table   the table being rendered
         * @param viewRow the view row index
         * @return true if the row's status is OK
         */
        private static boolean isSuccessRow(JTable table, int viewRow) {
            TableModel tableModel = table.getModel();
            if (viewRow < 0 || viewRow >= table.getRowCount()) {
                return false;
            }
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (modelRow < 0 || modelRow >= tableModel.getRowCount()) {
                return false;
            }
            return RunHistoryEntry.STATUS_OK.equals(tableModel.getValueAt(modelRow, COL_STATUS));
        }
    }
}