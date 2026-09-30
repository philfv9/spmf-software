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

import static ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionTableModel.COL_EXAMPLE;
import static ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionTableModel.COL_PARAMETER;
import static ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionTableModel.COL_VALUE;
import static ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionTableModel.isBlank;
import static ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionTableModel.isBooleanType;
import static ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionTableModel.isDecimalType;
import static ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionTableModel.isIntegerType;
import static ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionTableModel.isNumericType;
import static ca.pfv.spmf.gui.parameterselectionpanel.ParameterSelectionTableModel.isValidValueForType;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;

import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;

/**
 * JPanel that lets the user enter parameter values for an algorithm. It is
 * used by the graphical user interface of SPMF.
 *
 * <p>The Value column adapts its editor and renderer to the declared type of
 * each parameter:</p>
 * <ul>
 *   <li>{@code Boolean} — drop-down restricted to {@code true}/{@code false}
 *       (plus a blank entry when optional), rendered as a coloured bold
 *       badge; the list opens on the first click.</li>
 *   <li>{@code Integer}/{@code Long}/{@code Short} — right-aligned field that
 *       silently rejects non-integer input.</li>
 *   <li>{@code Double}/{@code Float} — right-aligned field accepting decimal
 *       numbers (including scientific notation) and a trailing {@code %};
 *       e.g. {@code 40%} is converted to {@code 0.4} when values are
 *       retrieved.</li>
 *   <li>Anything else — plain text field.</li>
 * </ul>
 *
 * <p>Visual conventions:</p>
 * <ul>
 *   <li>Optional parameters show a muted {@code (optional)} suffix.</li>
 *   <li>Required parameters whose Value is empty get a soft amber
 *       background.</li>
 *   <li>Values that are not syntactically valid for their type are shown in
 *       red.</li>
 *   <li>Hovering a row shows a tooltip with the parameter's name, type and
 *       example.</li>
 *   <li>Right-click on a Value cell offers "Use example value", "Clear value"
 *       and "Fill empty values with examples".</li>
 *   <li>Tab / Shift+Tab move between Value cells; text fields select all on
 *       focus.</li>
 * </ul>
 *
 * <p>Support for the run-history feature: {@link #getParameterCount()},
 * {@link #setParameterValues(String[])}. Support for pre-run validation:
 * {@link #hasAllRequiredValuesFilled()},
 * {@link #getMissingRequiredParameterNames()},
 * {@link #getInvalidParameterNames()}.</p>
 *
 * @author Philippe Fournier-Viger, 2024.
 */
public class ParameterSelectionPanel extends JPanel {

    /** Serial version UID */
    private static final long serialVersionUID = 1L;

    // -----------------------------------------------------------------------
    // Visual constants
    // -----------------------------------------------------------------------

    private static final Color COLOR_READONLY_BG    = new Color(245, 246, 248);
    private static final Color COLOR_EDITABLE_BG    = Color.WHITE;
    private static final Color COLOR_READONLY_FG    = new Color(70, 70, 75);
    private static final Color COLOR_EDITABLE_FG    = new Color(40, 40, 40);
    private static final Color COLOR_ROW_STRIPE     = new Color(250, 251, 253);
    private static final Color COLOR_SELECTION_BG   = new Color(184, 207, 229);
    private static final Color COLOR_SELECTION_FG   = new Color(30, 30, 30);
    private static final Color COLOR_HEADER_BG      = new Color(100, 130, 170);
    private static final Color COLOR_HEADER_FG      = Color.WHITE;
    private static final Color COLOR_GRID           = new Color(220, 222, 225);
    private static final Color COLOR_EMPTY_AREA     = new Color(238, 238, 238);
    private static final Color COLOR_TRUE_FG        = new Color(20, 120, 20);
    private static final Color COLOR_FALSE_FG       = new Color(170, 30, 30);
    private static final Color COLOR_INVALID_FG     = new Color(200, 0, 0);

    /** Background flagging a required parameter whose value is empty */
    private static final Color COLOR_NEEDS_INPUT_BG = new Color(255, 248, 222);

    /** HTML colour of the "(optional)" suffix in the Parameter column */
    private static final String COLOR_OPTIONAL_HEX  = "#888888";

    /** Padding shared by all cell renderers (borders are immutable, so sharing is safe) */
    private static final Border CELL_PADDING = new EmptyBorder(1, 6, 1, 6);

    private static final int DEFAULT_WIDTH     = 500;
    private static final int DEFAULT_HEIGHT    = 200;
    private static final int ROW_HEIGHT        = 24;
    private static final int COL_PARAM_WIDTH   = 200;
    private static final int COL_VALUE_WIDTH   = 150;
    private static final int COL_EXAMPLE_WIDTH = 150;
    private static final float FONT_SIZE       = 12f;

    // -----------------------------------------------------------------------
    // Instance fields
    // -----------------------------------------------------------------------

    /** The table model */
    private final ParameterSelectionTableModel tableModel;

    /** The table displaying the parameters */
    private final JTable parameterTable;

    /** Renderers — one instance per column, reused for every row */
    private final TableCellRenderer parameterRenderer = new ParameterNameCellRenderer();
    private final TableCellRenderer valueRenderer     = new ValueCellRenderer();
    private final TableCellRenderer exampleRenderer   = new ReadOnlyCellRenderer();

    /** Editors — created once and reused (JTable only edits one cell at a time) */
    private TableCellEditor requiredBooleanEditor;
    private TableCellEditor optionalBooleanEditor;
    private TableCellEditor integerEditor;
    private TableCellEditor decimalEditor;
    private TableCellEditor textEditor;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /**
     * Constructor.
     *
     * @param descriptionOfAlgorithm the algorithm whose parameters will be
     *                               displayed, or {@code null} for an empty panel
     */
    public ParameterSelectionPanel(DescriptionOfAlgorithm descriptionOfAlgorithm) {
        super(new BorderLayout());

        tableModel = new ParameterSelectionTableModel(descriptionOfAlgorithm);
        parameterTable = new ParameterTable(tableModel);

        configureTable();
        createEditors();
        styleTableHeader();
        installValueColumnContextMenu();
        configureValueColumnTabNavigation();

        JScrollPane scrollPane = new JScrollPane(parameterTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(COLOR_GRID, 1));
        scrollPane.getViewport().setBackground(COLOR_EMPTY_AREA);
        add(scrollPane, BorderLayout.CENTER);

        setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
        setMinimumSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Refreshes the panel to display the parameters of a different algorithm.
     * Any edit in progress is committed first so that it can be carried over
     * where applicable.
     *
     * @param descriptionOfAlgorithm the algorithm description, or {@code null}
     *                               to show an empty parameter table
     */
    public void update(DescriptionOfAlgorithm descriptionOfAlgorithm) {
        commitPendingEdit();
        tableModel.setData(descriptionOfAlgorithm == null
                ? null
                : descriptionOfAlgorithm.getParametersDescription());
    }

    /**
     * @return the number of parameter rows currently displayed
     */
    public int getParameterCount() {
        return tableModel.getRowCount();
    }

    /**
     * Returns the parameter values entered by the user, normalised for the
     * algorithm (percentages converted, numbers/booleans trimmed) and with
     * blank optional parameters omitted. Any edit in progress is committed
     * first, so the user does not need to press Enter before clicking "Run".
     *
     * @return an array of values (never null; see
     *         {@link ParameterSelectionTableModel#getParameterValues()})
     */
    public String[] getParameterValues() {
        commitPendingEdit();
        return tableModel.getParameterValues();
    }

    /**
     * Restores previously recorded values into the Value column (run-history
     * re-run feature). Any edit in progress is discarded.
     *
     * @param values the values to restore; ignored if null
     */
    public void setParameterValues(String[] values) {
        if (values == null) {
            return;
        }
        cancelPendingEdit();
        tableModel.setParameterValues(values);
    }

    /**
     * @return true if every required parameter has a non-blank value
     */
    public boolean hasAllRequiredValuesFilled() {
        return getMissingRequiredParameterNames().isEmpty();
    }

    /**
     * Returns the names of required parameters whose value is blank.
     *
     * @return a list of names (never null, possibly empty)
     */
    public List<String> getMissingRequiredParameterNames() {
        commitPendingEdit();
        List<String> missing = new ArrayList<>();
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            DescriptionOfParameter param = tableModel.getParameterAt(row);
            if (param != null && !param.isOptional()
                    && isBlank(tableModel.getRawValue(row))) {
                missing.add(param.getName());
            }
        }
        return missing;
    }

    /**
     * Returns the names of parameters whose (non-blank) value is not
     * syntactically valid for the parameter's declared type — e.g. a decimal
     * field left as {@code "3."} or {@code "-"}.
     *
     * @return a list of names (never null, possibly empty)
     */
    public List<String> getInvalidParameterNames() {
        commitPendingEdit();
        List<String> invalid = new ArrayList<>();
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            DescriptionOfParameter param = tableModel.getParameterAt(row);
            String value = tableModel.getRawValue(row);
            if (param != null && !isBlank(value)
                    && !isValidValueForType(value, param.getParameterType())) {
                invalid.add(param.getName());
            }
        }
        return invalid;
    }

    // -----------------------------------------------------------------------
    // Table set-up
    // -----------------------------------------------------------------------

    /** Applies general table settings (selection, grid, fonts, widths). */
    private void configureTable() {
        // Commit edits when the table loses focus (prevents data loss)
        parameterTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        parameterTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        parameterTable.setSelectionBackground(COLOR_SELECTION_BG);
        parameterTable.setSelectionForeground(COLOR_SELECTION_FG);

        parameterTable.setRowHeight(ROW_HEIGHT);
        parameterTable.setShowGrid(true);
        parameterTable.setGridColor(COLOR_GRID);
        parameterTable.setFillsViewportHeight(false);
        parameterTable.setFont(parameterTable.getFont().deriveFont(Font.PLAIN, FONT_SIZE));

        parameterTable.getColumnModel().getColumn(COL_PARAMETER).setPreferredWidth(COL_PARAM_WIDTH);
        parameterTable.getColumnModel().getColumn(COL_VALUE).setPreferredWidth(COL_VALUE_WIDTH);
        parameterTable.getColumnModel().getColumn(COL_EXAMPLE).setPreferredWidth(COL_EXAMPLE_WIDTH);
    }

    /** Creates the reusable cell editors (must run after the table font is set). */
    private void createEditors() {
        Font font = parameterTable.getFont();
        requiredBooleanEditor = createBooleanEditor(false, font);
        optionalBooleanEditor = createBooleanEditor(true, font);
        integerEditor         = createNumericEditor(false, font);
        decimalEditor         = createNumericEditor(true, font);
        textEditor            = createTextEditor(font);
    }

    /** Commits the edit in progress, if any (cancels it if it cannot be committed). */
    private void commitPendingEdit() {
        if (parameterTable != null && parameterTable.isEditing()) {
            TableCellEditor editor = parameterTable.getCellEditor();
            if (!editor.stopCellEditing()) {
                editor.cancelCellEditing();
            }
        }
    }

    /** Discards the edit in progress, if any. */
    private void cancelPendingEdit() {
        if (parameterTable != null && parameterTable.isEditing()) {
            parameterTable.getCellEditor().cancelCellEditing();
        }
    }

    // -----------------------------------------------------------------------
    // The table
    // -----------------------------------------------------------------------

    /**
     * JTable subclass that picks renderers/editors per column and parameter
     * type, and shows a descriptive tooltip for each row.
     */
    @SuppressWarnings("serial")
    private final class ParameterTable extends JTable {

        ParameterTable(TableModel model) {
            super(model);
        }

        /** Renderers are chosen by column so they survive column-model rebuilds. */
        @Override
        public TableCellRenderer getCellRenderer(int row, int column) {
            switch (convertColumnIndexToModel(column)) {
                case COL_PARAMETER: return parameterRenderer;
                case COL_VALUE:     return valueRenderer;
                case COL_EXAMPLE:   return exampleRenderer;
                default:            return super.getCellRenderer(row, column);
            }
        }

        /** The Value editor depends on the parameter's declared type. */
        @Override
        public TableCellEditor getCellEditor(int row, int column) {
            if (convertColumnIndexToModel(column) != COL_VALUE) {
                return super.getCellEditor(row, column);
            }
            DescriptionOfParameter param =
                    tableModel.getParameterAt(convertRowIndexToModel(row));
            Class<?> type = param == null ? null : param.getParameterType();

            if (isBooleanType(type)) {
                return param.isOptional() ? optionalBooleanEditor : requiredBooleanEditor;
            }
            if (isIntegerType(type)) {
                return integerEditor;
            }
            if (isDecimalType(type)) {
                return decimalEditor;
            }
            return textEditor;
        }

        /** Tooltip describing the parameter under the mouse. */
        @Override
        public String getToolTipText(MouseEvent e) {
            int viewRow = rowAtPoint(e.getPoint());
            if (viewRow < 0) {
                return null;
            }
            DescriptionOfParameter param =
                    tableModel.getParameterAt(convertRowIndexToModel(viewRow));
            if (param == null) {
                return null;
            }
            Class<?> type = param.getParameterType();

            StringBuilder sb = new StringBuilder("<html><b>")
                    .append(escapeHtml(param.getName())).append("</b>")
                    .append(param.isOptional() ? " &ndash; optional" : " &ndash; required");
            if (type != null) {
                sb.append("<br>Type: ").append(type.getSimpleName());
            }
            if (!isBlank(param.example)) {
                sb.append("<br>Example: ").append(escapeHtml(param.example));
            }
            if (isDecimalType(type)) {
                sb.append("<br><i>Tip: a percentage such as 40% is converted to 0.4</i>");
            }
            return sb.append("</html>").toString();
        }
    }

    // -----------------------------------------------------------------------
    // Renderers
    // -----------------------------------------------------------------------

    /** Padded renderer with alternating stripes, used for read-only columns. */
    @SuppressWarnings("serial")
    private static class ReadOnlyCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBorder(CELL_PADDING);
            if (!isSelected) {
                setBackground(row % 2 == 0 ? COLOR_READONLY_BG : COLOR_ROW_STRIPE);
                setForeground(COLOR_READONLY_FG);
            }
            return this;
        }
    }

    /** Parameter column: name, plus a muted "(optional)" suffix when applicable. */
    @SuppressWarnings("serial")
    private final class ParameterNameCellRenderer extends ReadOnlyCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            DescriptionOfParameter param =
                    tableModel.getParameterAt(table.convertRowIndexToModel(row));
            if (param != null) {
                String name = escapeHtml(param.getName());
                setText(param.isOptional()
                        ? "<html>" + name + " <font color='" + COLOR_OPTIONAL_HEX
                                + "'>(optional)</font></html>"
                        : name);
            }
            return this;
        }
    }

    /**
     * Value column: boolean badge (bold, centred, green/red), right-aligned
     * numbers, amber background for empty required values, red text for
     * syntactically invalid values.
     */
    @SuppressWarnings("serial")
    private final class ValueCellRenderer extends DefaultTableCellRenderer {

        /** Cached derived fonts (re-derived only when the table font changes) */
        private Font baseFont;
        private Font plainFont;
        private Font boldFont;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBorder(CELL_PADDING);

            DescriptionOfParameter param =
                    tableModel.getParameterAt(table.convertRowIndexToModel(row));
            Class<?> type = param == null ? null : param.getParameterType();
            String text = value == null ? "" : value.toString();
            String trimmed = text.trim();
            boolean isBoolean = isBooleanType(type);

            updateFonts(table.getFont());
            setFont(isBoolean ? boldFont : plainFont);
            setHorizontalAlignment(isBoolean ? CENTER
                    : isNumericType(type) ? RIGHT : LEFT);
            setText(text);

            if (!isSelected) {
                boolean needsInput = param != null && !param.isOptional() && trimmed.isEmpty();
                setBackground(needsInput ? COLOR_NEEDS_INPUT_BG : COLOR_EDITABLE_BG);

                if (!trimmed.isEmpty() && !isValidValueForType(trimmed, type)) {
                    setForeground(COLOR_INVALID_FG);
                } else if (isBoolean && "true".equalsIgnoreCase(trimmed)) {
                    setForeground(COLOR_TRUE_FG);
                } else if (isBoolean && "false".equalsIgnoreCase(trimmed)) {
                    setForeground(COLOR_FALSE_FG);
                } else {
                    setForeground(COLOR_EDITABLE_FG);
                }
            }
            return this;
        }

        private void updateFonts(Font tableFont) {
            if (tableFont != baseFont) {
                baseFont  = tableFont;
                plainFont = tableFont.deriveFont(Font.PLAIN);
                boldFont  = tableFont.deriveFont(Font.BOLD);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Editors
    // -----------------------------------------------------------------------

    /**
     * Combo-box editor restricted to {@code true}/{@code false}, with a blank
     * entry for optional parameters. The list opens on the first click.
     */
    @SuppressWarnings("serial")
    private static TableCellEditor createBooleanEditor(boolean optional, Font font) {
        JComboBox<String> combo = new JComboBox<>();
        if (optional) {
            combo.addItem("");
        }
        combo.addItem("true");
        combo.addItem("false");
        combo.setFont(font);

        DefaultCellEditor editor = new DefaultCellEditor(combo) {
            @Override
            public Component getTableCellEditorComponent(JTable table, Object value,
                    boolean isSelected, int row, int column) {
                Component c = super.getTableCellEditorComponent(table, value, isSelected, row, column);
                // Open the list immediately rather than waiting for a second click
                SwingUtilities.invokeLater(() -> {
                    if (combo.isShowing()) {
                        combo.showPopup();
                    }
                });
                return c;
            }
        };
        editor.setClickCountToStart(1);
        return editor;
    }

    /**
     * Right-aligned text-field editor that rejects keystrokes producing an
     * invalid (partial) number. In decimal mode a trailing {@code %} and
     * scientific notation are accepted.
     */
    private static TableCellEditor createNumericEditor(boolean allowDecimal, Font font) {
        JTextField field = new JTextField();
        field.setFont(font);
        field.setHorizontalAlignment(JTextField.RIGHT);
        ((AbstractDocument) field.getDocument())
                .setDocumentFilter(new NumericDocumentFilter(allowDecimal));
        installSelectAllOnFocus(field);
        return new DefaultCellEditor(field);
    }

    /** Plain text-field editor for {@code String} and unknown types. */
    private static TableCellEditor createTextEditor(Font font) {
        JTextField field = new JTextField();
        field.setFont(font);
        installSelectAllOnFocus(field);
        return new DefaultCellEditor(field);
    }

    /** Selects the whole content of {@code field} whenever it gains focus. */
    private static void installSelectAllOnFocus(JTextField field) {
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                SwingUtilities.invokeLater(field::selectAll);
            }
        });
    }

    /**
     * {@link DocumentFilter} that only lets the document reach states that are
     * a valid <em>prefix</em> of a number, so the field can be typed
     * left-to-right: {@code ""}, {@code "-"}, {@code "3."}, {@code "1e"} and
     * {@code "40%"} are all allowed in decimal mode.
     */
    private static final class NumericDocumentFilter extends DocumentFilter {

        private static final Pattern PARTIAL_INTEGER = Pattern.compile("-?\\d*");
        private static final Pattern PARTIAL_DECIMAL =
                Pattern.compile("-?\\d*(\\.\\d*)?([eE][-+]?\\d*)?%?");

        private final Pattern pattern;

        NumericDocumentFilter(boolean allowDecimal) {
            pattern = allowDecimal ? PARTIAL_DECIMAL : PARTIAL_INTEGER;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string,
                AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length)
                throws BadLocationException {
            if (isAcceptable(fb, offset, length, "")) {
                super.remove(fb, offset, length);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text,
                AttributeSet attrs) throws BadLocationException {
            if (isAcceptable(fb, offset, length, text == null ? "" : text)) {
                super.replace(fb, offset, length, text, attrs);
            }
            // otherwise the edit is silently swallowed
        }

        private boolean isAcceptable(FilterBypass fb, int offset, int length, String text)
                throws BadLocationException {
            Document doc = fb.getDocument();
            String current = doc.getText(0, doc.getLength());
            String candidate = current.substring(0, offset) + text
                    + current.substring(offset + length);
            return pattern.matcher(candidate).matches();
        }
    }

    // -----------------------------------------------------------------------
    // Tab navigation between Value cells
    // -----------------------------------------------------------------------

    /** Makes Tab / Shift+Tab jump directly between Value cells. */
    @SuppressWarnings("serial")
    private void configureValueColumnTabNavigation() {
        int condition = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;
        parameterTable.getInputMap(condition)
                .put(KeyStroke.getKeyStroke("TAB"), "spmf.nextValueCell");
        parameterTable.getInputMap(condition)
                .put(KeyStroke.getKeyStroke("shift TAB"), "spmf.previousValueCell");

        parameterTable.getActionMap().put("spmf.nextValueCell", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveToAdjacentValueCell(1);
            }
        });
        parameterTable.getActionMap().put("spmf.previousValueCell", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                moveToAdjacentValueCell(-1);
            }
        });
    }

    /**
     * Commits the current edit and starts editing the Value cell
     * {@code direction} rows away (wrapping around).
     */
    private void moveToAdjacentValueCell(int direction) {
        int rowCount = parameterTable.getRowCount();
        if (rowCount == 0) {
            return;
        }
        int current = parameterTable.isEditing()
                ? parameterTable.getEditingRow()
                : parameterTable.getSelectedRow();
        commitPendingEdit();

        int next = current < 0
                ? (direction > 0 ? 0 : rowCount - 1)
                : Math.floorMod(current + direction, rowCount);
        int viewColumn = parameterTable.convertColumnIndexToView(COL_VALUE);

        parameterTable.changeSelection(next, viewColumn, false, false);
        if (parameterTable.editCellAt(next, viewColumn)) {
            Component editor = parameterTable.getEditorComponent();
            if (editor != null) {
                editor.requestFocusInWindow(); // select-all happens via focus listener
            }
        } else {
            parameterTable.requestFocusInWindow();
        }
    }

    // -----------------------------------------------------------------------
    // Right-click context menu on the Value column
    // -----------------------------------------------------------------------

    /** Installs the right-click menu on the Value column. */
    private void installValueColumnContextMenu() {
        parameterTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showValueContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showValueContextMenu(e);
                }
            }
        });
    }

    private void showValueContextMenu(MouseEvent e) {
        Point p = e.getPoint();
        int viewRow = parameterTable.rowAtPoint(p);
        int viewCol = parameterTable.columnAtPoint(p);
        if (viewRow < 0 || viewCol < 0
                || parameterTable.convertColumnIndexToModel(viewCol) != COL_VALUE) {
            return;
        }

        // Make sure a pending edit does not overwrite what the menu sets
        commitPendingEdit();
        parameterTable.changeSelection(viewRow, viewCol, false, false);

        int modelRow = parameterTable.convertRowIndexToModel(viewRow);
        DescriptionOfParameter param = tableModel.getParameterAt(modelRow);
        if (param == null) {
            return;
        }

        JPopupMenu menu = new JPopupMenu();

        String example = param.example;
        if (!isBlank(example)) {
            JMenuItem useExample = new JMenuItem("Use example value  (\"" + example + "\")");
            useExample.addActionListener(a -> tableModel.setValueAt(example, modelRow, COL_VALUE));
            menu.add(useExample);
        }

        JMenuItem clear = new JMenuItem("Clear value");
        clear.setEnabled(!isBlank(tableModel.getRawValue(modelRow)));
        clear.addActionListener(a -> tableModel.setValueAt("", modelRow, COL_VALUE));
        menu.add(clear);

        menu.addSeparator();

        JMenuItem fillAll = new JMenuItem("Fill empty values with examples");
        fillAll.setEnabled(hasEmptyRowWithExample());
        fillAll.addActionListener(a -> fillEmptyValuesWithExamples());
        menu.add(fillAll);

        menu.show(parameterTable, e.getX(), e.getY());
    }

    private boolean hasEmptyRowWithExample() {
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            DescriptionOfParameter param = tableModel.getParameterAt(row);
            if (param != null && !isBlank(param.example)
                    && isBlank(tableModel.getRawValue(row))) {
                return true;
            }
        }
        return false;
    }

    private void fillEmptyValuesWithExamples() {
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            DescriptionOfParameter param = tableModel.getParameterAt(row);
            if (param != null && !isBlank(param.example)
                    && isBlank(tableModel.getRawValue(row))) {
                tableModel.setValueAt(param.example, row, COL_VALUE);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Header styling
    // -----------------------------------------------------------------------

    /**
     * Installs a header with a medium-blue background, white bold text, a
     * tooltip per column, and no column reordering.
     */
    @SuppressWarnings("serial")
    private void styleTableHeader() {
        JTableHeader header = new JTableHeader(parameterTable.getColumnModel()) {
            @Override
            public String getToolTipText(MouseEvent e) {
                int col = columnAtPoint(e.getPoint());
                if (col < 0) {
                    return super.getToolTipText(e);
                }
                switch (getColumnModel().getColumn(col).getModelIndex()) {
                    case COL_PARAMETER:
                        return "Name of the parameter";
                    case COL_VALUE:
                        return "Value to use for this parameter (for decimal parameters "
                                + "you may also type a percentage, e.g. 40% instead of 0.4)";
                    case COL_EXAMPLE:
                        return "Example of a valid value";
                    default:
                        return super.getToolTipText(e);
                }
            }
        };

        header.setToolTipText(""); // registers the header with the ToolTipManager
        header.setBackground(COLOR_HEADER_BG);
        header.setForeground(COLOR_HEADER_FG);
        header.setReorderingAllowed(false);
        header.setResizingAllowed(true);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_GRID));

        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            private final Border padding = new EmptyBorder(3, 8, 3, 8);

            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setBackground(COLOR_HEADER_BG);
                setForeground(COLOR_HEADER_FG);
                setFont(getFont().deriveFont(Font.BOLD, FONT_SIZE));
                setHorizontalAlignment(JLabel.LEFT);
                setBorder(padding);
                setOpaque(true);
                return this;
            }
        });

        parameterTable.setTableHeader(header);
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    /**
     * Escapes {@code &}, {@code <} and {@code >} so arbitrary text can be
     * embedded in HTML-formatted Swing labels.
     */
    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}