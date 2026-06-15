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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;

import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;

/**
 * JPanel that let the user enter parameter values for an algorithm. It is used
 * by the graphical user interface of SPMF.
 *
 * <p>Two methods support the run-history re-run feature:</p>
 * <ul>
 *   <li>{@link #getParameterCount()} — returns the number of parameter rows
 *       that are currently displayed, so the controller knows how many values
 *       it may safely restore without overshooting the algorithm's declared
 *       parameter list.</li>
 *   <li>{@link #setParameterValues(String[])} — pushes an array of previously
 *       recorded values back into the Value column so the user sees exactly
 *       what was used in the historical run.</li>
 * </ul>
 *
 * @author Philippe Fournier-Viger, 2024.
 */
public class ParameterSelectionPanel extends JPanel {

    /** Serial version UID */
    private static final long serialVersionUID = 1L;

    // -----------------------------------------------------------------------
    // Visual constants
    // -----------------------------------------------------------------------

    /** Background colour for read-only cells (even rows) */
    private static final Color COLOR_READONLY_BG  = new Color(245, 246, 248);

    /** Background colour for editable (value) cells */
    private static final Color COLOR_EDITABLE_BG  = Color.WHITE;

    /** Foreground colour for read-only cells */
    private static final Color COLOR_READONLY_FG  = new Color(70, 70, 75);

    /** Foreground colour for editable cells */
    private static final Color COLOR_EDITABLE_FG  = new Color(40, 40, 40);

    /** Alternating stripe colour for odd rows */
    private static final Color COLOR_ROW_STRIPE   = new Color(250, 251, 253);

    /** Selection background colour */
    private static final Color COLOR_SELECTION_BG = new Color(184, 207, 229);

    /** Selection foreground colour */
    private static final Color COLOR_SELECTION_FG = new Color(30, 30, 30);

    /**
     * Table header background – a medium steel-blue that is clearly distinct
     * from the white/light-grey table body but not as dark as near-black.
     */
    private static final Color COLOR_HEADER_BG    = new Color(100, 130, 170);

    /** Table header foreground – plain white for contrast against the blue */
    private static final Color COLOR_HEADER_FG    = Color.WHITE;

    /** Grid line colour */
    private static final Color COLOR_GRID         = new Color(220, 222, 225);

    /** Background color for empty space below the table rows */
    private static final Color COLOR_EMPTY_AREA   = new Color(238, 238, 238);

    /** Default preferred width of the panel */
    private static final int DEFAULT_WIDTH  = 500;

    /** Default preferred height of the panel */
    private static final int DEFAULT_HEIGHT = 200;

    /**
     * Row height in pixels – 22 px gives a compact but still readable table
     * without the large gaps that a 26 px height would produce.
     */
    private static final int ROW_HEIGHT = 22;

    /** Width of the "Parameter" column */
    private static final int COL_PARAM_WIDTH   = 200;

    /** Width of the "Value" column */
    private static final int COL_VALUE_WIDTH   = 150;

    /** Width of the "Example" column */
    private static final int COL_EXAMPLE_WIDTH = 150;

    // -----------------------------------------------------------------------
    // Instance fields
    // -----------------------------------------------------------------------

    /** The table to display the parameters */
    private JTable parameterTable;

    /** The custom table model used to display parameters */
    private ParameterSelectionTableModel tableModel;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Constructor
     *
     * @param descriptionOfAlgorithm The algorithm for which the parameters
     *                               will be displayed, or null for an empty panel
     */
    public ParameterSelectionPanel(DescriptionOfAlgorithm descriptionOfAlgorithm) {
        super(new BorderLayout());
        setBorder(null);
        update(descriptionOfAlgorithm);

        setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
        setMinimumSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Method to refresh the panel to display a different algorithm's parameters.
     *
     * @param descriptionOfAlgorithm the description of the algorithm, or null
     *                               to show an empty parameter table
     */
    @SuppressWarnings("serial")
    public void update(DescriptionOfAlgorithm descriptionOfAlgorithm) {

        // Initialize on first call
        if (tableModel == null) {

            // Create the table model
            tableModel = new ParameterSelectionTableModel(descriptionOfAlgorithm);

            // Create the table with alternating row colours and improved rendering
            parameterTable = new JTable(tableModel) {
                @Override
                public Component prepareRenderer(TableCellRenderer renderer,
                                                 int row, int column) {
                    Component c = super.prepareRenderer(renderer, row, column);

                    // Apply alternating row stripe for non-selected rows
                    if (!isRowSelected(row)) {
                        if (column != 1) {
                            // Read-only columns get a light grey tint,
                            // alternating by row
                            c.setBackground(
                                    row % 2 == 0 ? COLOR_READONLY_BG
                                                 : COLOR_ROW_STRIPE);
                            c.setForeground(COLOR_READONLY_FG);
                        } else {
                            // Editable "Value" column is always white
                            c.setBackground(COLOR_EDITABLE_BG);
                            c.setForeground(COLOR_EDITABLE_FG);
                        }
                    } else {
                        // Highlighted selection colours
                        c.setBackground(COLOR_SELECTION_BG);
                        c.setForeground(COLOR_SELECTION_FG);
                    }
                    return c;
                }
            };

            // Commit edits when the table loses focus (prevents data loss)
            parameterTable.putClientProperty(
                    "terminateEditOnFocusLost", Boolean.TRUE);

            // Allow only single-row selection for clarity
            parameterTable.setSelectionMode(
                    ListSelectionModel.SINGLE_SELECTION);

            // Visual tweaks
            parameterTable.setRowHeight(ROW_HEIGHT);
            parameterTable.setShowVerticalLines(true);
            parameterTable.setShowHorizontalLines(true);
            parameterTable.setGridColor(COLOR_GRID);
            parameterTable.setFillsViewportHeight(false);
            parameterTable.setFont(
                    parameterTable.getFont().deriveFont(Font.PLAIN, 12f));

            // Style the table header
            styleTableHeader(parameterTable);

            // Add a padding renderer to all columns for a more polished look
            applyPaddedRenderer(parameterTable);

            // Wrap in a scroll pane with a subtle border matching the grid colour
            JScrollPane scrollPane = new JScrollPane(parameterTable);
            scrollPane.setBorder(
                    BorderFactory.createLineBorder(COLOR_GRID, 1));
            scrollPane.getViewport().setBackground(COLOR_EMPTY_AREA);

            // Set the preferred width of each column
            parameterTable.getColumnModel()
                    .getColumn(0).setPreferredWidth(COL_PARAM_WIDTH);
            parameterTable.getColumnModel()
                    .getColumn(1).setPreferredWidth(COL_VALUE_WIDTH);
            parameterTable.getColumnModel()
                    .getColumn(2).setPreferredWidth(COL_EXAMPLE_WIDTH);

            add(scrollPane, BorderLayout.CENTER);

        } else {

            // Update existing table model with the new algorithm's parameters
            if (descriptionOfAlgorithm != null) {
                tableModel.setData(
                        descriptionOfAlgorithm.getParametersDescription());
            } else {
                tableModel.setData(new DescriptionOfParameter[0]);
            }
            // Trigger table repaint after the data change
            tableModel.fireTableDataChanged();
        }
    }

    /**
     * Returns the number of parameter rows that the table currently displays.
     * This equals the number of parameters declared by the algorithm that was
     * most recently passed to {@link #update(DescriptionOfAlgorithm)}.
     *
     * <p>The controller uses this count when re-running a history entry so
     * that it can truncate the stored parameter array to exactly the number of
     * fields that exist, preventing "wrong number of arguments" errors.</p>
     *
     * @return the current number of parameter rows, or 0 if no algorithm has
     *         been loaded yet
     */
    public int getParameterCount() {
        if (tableModel == null) {
            return 0;
        }
        return tableModel.getRowCount();
    }

    /**
     * Gets the values for parameters that have been entered by the user.
     *
     * @return the values as an array of String objects, one per parameter row
     */
    public String[] getParameterValues() {
        return tableModel.getParameterValues();
    }

    /**
     * Pushes an array of previously recorded parameter values back into the
     * Value column of the table. This is called by the controller when the
     * user re-runs a past configuration from the run-history panel.
     *
     * <p>Only {@code Math.min(values.length, getParameterCount())} values are
     * written. If {@code values} contains fewer entries than there are rows,
     * the remaining rows keep whatever value they already hold. If
     * {@code values} contains more entries than there are rows the extra
     * values are silently ignored — the caller is responsible for truncating
     * the array to {@link #getParameterCount()} before calling this method,
     * but defensive behaviour here prevents any index-out-of-bounds errors
     * if that contract is violated.</p>
     *
     * @param values the parameter values to restore, one per table row;
     *               must not be null
     */
    public void setParameterValues(String[] values) {
        if (values == null || tableModel == null) {
            return;
        }
        // Delegate to the table model, which owns the backing data
        tableModel.setParameterValues(values);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Styles the table header with a medium-blue background and white bold
     * text. The colour is intentionally softer than near-black so it blends
     * better with the rest of the SPMF user interface.
     *
     * @param table the table whose header should be styled
     */
    private void styleTableHeader(JTable table) {
        JTableHeader header = table.getTableHeader();
        header.setBackground(COLOR_HEADER_BG);
        header.setForeground(COLOR_HEADER_FG);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        header.setReorderingAllowed(false);
        header.setResizingAllowed(true);
        header.setBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_GRID));

        // Override the default header renderer so our colours are respected
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {

                JLabel label = (JLabel) super.getTableCellRendererComponent(
                        t, value, isSelected, hasFocus, row, column);
                label.setBackground(COLOR_HEADER_BG);
                label.setForeground(COLOR_HEADER_FG);
                label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
                label.setHorizontalAlignment(JLabel.LEFT);
                label.setBorder(new EmptyBorder(3, 8, 3, 8));
                label.setOpaque(true);
                return label;
            }
        });
    }

    /**
     * Applies a left-padded cell renderer to every column so text is not
     * flush against the cell border.
     *
     * @param table the table to which the renderer should be applied
     */
    private void applyPaddedRenderer(JTable table) {
        DefaultTableCellRenderer paddedRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {

                JLabel label = (JLabel) super.getTableCellRendererComponent(
                        t, value, isSelected, hasFocus, row, column);
                label.setBorder(new EmptyBorder(1, 6, 1, 6));
                return label;
            }
        };

        // Apply to the read-only columns (Parameter and Example)
        table.getColumnModel().getColumn(0).setCellRenderer(paddedRenderer);
        table.getColumnModel().getColumn(2).setCellRenderer(paddedRenderer);
    }
}