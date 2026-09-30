package ca.pfv.spmf.gui.preferences;

import java.awt.*;
import java.awt.event.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

import ca.pfv.spmf.gui.SortableJTable;

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
 * An enhanced window for viewing AND editing the preferences stored in the
 * system registry for SPMF.
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Inline editing: double-click any Value cell to edit it directly.</li>
 *   <li>Type-aware editing: booleans use a drop-down, integers/doubles use a
 *       spinner, charset uses a combo-box, strings use a text field.</li>
 *   <li>Live search/filter bar at the top to quickly find a preference.</li>
 *   <li>Status bar shows how many preferences are displayed vs. total.</li>
 *   <li>Unsaved changes are highlighted in a distinct colour and a toolbar
 *       badge shows the pending-change count.</li>
 *   <li>Save, Discard and Reset buttons with confirmation dialogs.</li>
 *   <li>Copy-to-clipboard button for the selected row's value.</li>
 *   <li>Export-to-text button to dump all preferences as a text report.</li>
 *   <li>Column tooltips and row striping for readability.</li>
 *   <li>Keyboard shortcuts: Ctrl+S → Save, Ctrl+F → focus search,
 *       Escape → Discard.</li>
 * </ul>
 *
 * <p>The table is populated via reflection exactly as in the original viewer:
 * only {@code public} fields whose names begin with an upper-case letter and
 * that have a matching {@code get<FieldName>()} method are shown.</p>
 *
 * @author Philippe Fournier-Viger (original), enhanced version
 */
@SuppressWarnings("serial")
public class PreferencesViewer extends JFrame {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** Column indices */
    private static final int COL_PROPERTY = 0;
    private static final int COL_VALUE     = 1;
    private static final int COL_TYPE      = 2;
    private static final int COL_REG_KEY   = 3;

    /** Alternating row background colours */
    private static final Color ROW_ODD        = new Color(245, 245, 250);
    private static final Color ROW_EVEN       = Color.WHITE;
    /** Highlight colour for rows with unsaved changes */
    private static final Color ROW_MODIFIED   = new Color(255, 243, 205);
    /** Highlight colour for the modified cell value */
    private static final Color CELL_MODIFIED  = new Color(255, 220, 100);

    // -----------------------------------------------------------------------
    // Model / data
    // -----------------------------------------------------------------------

    /** Backing table model (non-editable at model level; we handle editing ourselves). */
    private DefaultTableModel tableModel;

    /** Row sorter used for live search filtering. */
    private TableRowSorter<DefaultTableModel> rowSorter;

    /** Maps property name → pending (unsaved) new value. */
    private final Map<String, String> pendingChanges = new LinkedHashMap<>();

    /**
     * Metadata record for each preference row so we can invoke the right
     * setter when the user saves.
     */
    private final List<PrefRow> prefRows = new ArrayList<>();

    // -----------------------------------------------------------------------
    // UI components
    // -----------------------------------------------------------------------

    private JTable      table;
    private JTextField  searchField;
    private JLabel      statusLabel;
    private JButton     saveButton;
    private JButton     discardButton;
    private JButton     resetButton;
    private JButton     copyButton;
    private JButton     exportButton;
    private JLabel      pendingLabel;

    /** The single PreferencesManager instance. */
    private final PreferencesManager prefsManager = PreferencesManager.getInstance();

    // -----------------------------------------------------------------------
    // Inner helper class
    // -----------------------------------------------------------------------

    /**
     * Holds reflective metadata for one preference entry so we can invoke
     * the correct setter with the correctly typed argument when the user saves.
     */
    private static class PrefRow {
        final String  name;     // e.g. "NightMode"
        final String  regKey;   // e.g. "ca.pfv.spmf.experiments.nightmode"
        final Class<?> type;    // return type of the getter (Boolean, Integer, …)
        final Method  getter;
        final Method  setter;

        PrefRow(String name, String regKey, Class<?> type,
                Method getter, Method setter) {
            this.name   = name;
            this.regKey = regKey;
            this.type   = type;
            this.getter = getter;
            this.setter = setter;
        }

        /** Human-readable type label shown in the Type column. */
        String typeName() {
            if (type == Boolean.TYPE   || type == Boolean.class)   return "Boolean";
            if (type == Integer.TYPE   || type == Integer.class)   return "Integer";
            if (type == Double.TYPE    || type == Double.class)    return "Double";
            if (type == Float.TYPE     || type == Float.class)     return "Float";
            if (type == Charset.class)                             return "Charset";
            return "String";
        }
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------

    /** Creates and shows the enhanced preferences viewer/editor. */
    public PreferencesViewer() {
        super("SPMF Preferences Editor");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                attemptClose();
            }
        });

        buildUI();
        populateTable();
        installKeyboardShortcuts();

        setSize(820, 560);
        setMinimumSize(new Dimension(640, 400));
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // -----------------------------------------------------------------------
    // UI construction
    // -----------------------------------------------------------------------

    /** Assembles all UI panels and wires them together. */
    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBorder(new EmptyBorder(6, 6, 6, 6));
        setContentPane(root);

        root.add(buildToolBar(),   BorderLayout.NORTH);
        root.add(buildTablePanel(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
    }

    // ---- toolbar -----------------------------------------------------------

    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(new EmptyBorder(4, 0, 4, 0));

        // Save
        saveButton = makeButton("💾 Save", "Save pending changes to the registry (Ctrl+S)");
        saveButton.addActionListener(e -> saveChanges());
        bar.add(saveButton);

        // Discard
        discardButton = makeButton("↩ Discard", "Discard all unsaved changes (Escape)");
        discardButton.addActionListener(e -> discardChanges());
        bar.add(discardButton);

        bar.addSeparator();

        // Reset
        resetButton = makeButton("🔄 Reset All", "Reset ALL preferences to their defaults");
        resetButton.addActionListener(e -> resetPreferences());
        bar.add(resetButton);

        bar.addSeparator();

        // Copy
        copyButton = makeButton("📋 Copy Value", "Copy the selected preference value to the clipboard");
        copyButton.addActionListener(e -> copySelectedValue());
        bar.add(copyButton);

        // Export
        exportButton = makeButton("📄 Export", "Export all preferences to a text file");
        exportButton.addActionListener(e -> exportPreferences());
        bar.add(exportButton);

        bar.addSeparator(new Dimension(12, 0));

        // Pending-change badge
        pendingLabel = new JLabel("No unsaved changes");
        pendingLabel.setForeground(new Color(100, 100, 100));
        pendingLabel.setFont(pendingLabel.getFont().deriveFont(Font.ITALIC));
        bar.add(pendingLabel);

        return bar;
    }

    // ---- search + table panel ---------------------------------------------

    private JPanel buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));

        // Search bar
        JPanel searchPanel = new JPanel(new BorderLayout(6, 0));
        searchPanel.setBorder(new EmptyBorder(0, 0, 4, 0));
        searchPanel.add(new JLabel("🔍 Filter:"), BorderLayout.WEST);
        searchField = new JTextField();
        searchField.setToolTipText("Type to filter preferences by name, value, or registry key (Ctrl+F)");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { applyFilter(); }
            public void removeUpdate(DocumentEvent e)  { applyFilter(); }
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        searchPanel.add(searchField, BorderLayout.CENTER);
        JButton clearSearch = new JButton("✕");
        clearSearch.setToolTipText("Clear filter");
        clearSearch.setMargin(new Insets(2, 6, 2, 6));
        clearSearch.addActionListener(e -> searchField.setText(""));
        searchPanel.add(clearSearch, BorderLayout.EAST);
        panel.add(searchPanel, BorderLayout.NORTH);

        // Table
        tableModel = new DefaultTableModel(
                new String[]{"Property", "Value", "Type", "Registry Key"}, 0) {
            @Override public boolean isCellEditable(int row, int col) {
                return false; // We handle editing via mouse listener
            }
        };

        table = new JTable(tableModel);
        rowSorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(rowSorter);
        table.setRowHeight(22);
        table.setShowGrid(true);
        table.setGridColor(new Color(220, 220, 220));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getTableHeader().setReorderingAllowed(false);

        // Column widths
        table.getColumnModel().getColumn(COL_PROPERTY).setPreferredWidth(180);
        table.getColumnModel().getColumn(COL_VALUE).setPreferredWidth(220);
        table.getColumnModel().getColumn(COL_TYPE).setPreferredWidth(70);
        table.getColumnModel().getColumn(COL_REG_KEY).setPreferredWidth(300);

        // Custom renderer for striping and change highlighting
        TableCellRendererImpl renderer = new TableCellRendererImpl();
        for (int i = 0; i < tableModel.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        // Double-click to edit
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = table.rowAtPoint(e.getPoint());
                    int viewCol = table.columnAtPoint(e.getPoint());
                    if (viewRow >= 0 && viewCol == COL_VALUE) {
                        editSelectedRow(viewRow);
                    }
                }
            }
        });

        // Tooltip: show full value when truncated
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row >= 0 && col >= 0) {
                    Object val = table.getValueAt(row, col);
                    table.setToolTipText(val == null ? null : val.toString());
                }
            }
        });

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    // ---- status bar -------------------------------------------------------

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY),
                new EmptyBorder(2, 4, 2, 4)));
        statusLabel = new JLabel("Loading…");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        bar.add(statusLabel, BorderLayout.WEST);

        JLabel hint = new JLabel("Double-click a Value cell to edit  |  Ctrl+S to save  |  Ctrl+F to search");
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 10f));
        hint.setForeground(Color.GRAY);
        bar.add(hint, BorderLayout.EAST);
        return bar;
    }

    // -----------------------------------------------------------------------
    // Table population
    // -----------------------------------------------------------------------

    /**
     * Populates {@link #tableModel} and {@link #prefRows} via reflection,
     * discovering every preference that has both a public getter and a setter.
     */
    private void populateTable() {
        tableModel.setRowCount(0);
        prefRows.clear();

        for (Field field : PreferencesManager.class.getDeclaredFields()) {
            try {
                // Only public fields whose names start with an upper-case letter
                if (Modifier.isPrivate(field.getModifiers())) continue;
                String name = field.getName();
                if (name.isEmpty() || !Character.isUpperCase(name.charAt(0))) continue;

                // Must have a matching getter
                Method getter;
                try {
                    getter = prefsManager.getClass().getMethod("get" + name);
                } catch (NoSuchMethodException e) {
                    continue;
                }

                // Try to find a setter with a single parameter matching the getter's
                // return type, or a String (for convenience).
                Class<?> returnType = getter.getReturnType();
                Method setter = findSetter(name, returnType);
                // If no typed setter found, skip (read-only row would be unhelpful)
                // We still show it but mark it read-only via PrefRow.setter == null.

                Object result = getter.invoke(prefsManager);
                String value  = (result == null) ? "" : result.toString();
                String regKey = field.get(null).toString();

                PrefRow pr = new PrefRow(name, regKey, returnType, getter, setter);
                prefRows.add(pr);

                tableModel.addRow(new Object[]{name, value, pr.typeName(), regKey});

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        updateStatus();
    }

    /**
     * Looks for a setter named {@code set<Name>} accepting either the exact
     * return type of the getter or a {@link String}.
     */
    private Method findSetter(String name, Class<?> paramType) {
        String setterName = "set" + name;
        // Try exact type first
        try {
            return prefsManager.getClass().getMethod(setterName, paramType);
        } catch (NoSuchMethodException ignored) {}
        // Try primitive counterpart
        try {
            if (paramType == Boolean.class)
                return prefsManager.getClass().getMethod(setterName, boolean.class);
            if (paramType == Integer.class)
                return prefsManager.getClass().getMethod(setterName, int.class);
            if (paramType == Double.class)
                return prefsManager.getClass().getMethod(setterName, double.class);
            if (paramType == Float.class)
                return prefsManager.getClass().getMethod(setterName, float.class);
        } catch (NoSuchMethodException ignored) {}
        // Try String
        try {
            return prefsManager.getClass().getMethod(setterName, String.class);
        } catch (NoSuchMethodException ignored) {}
        return null;
    }

    // -----------------------------------------------------------------------
    // Editing logic
    // -----------------------------------------------------------------------

    /**
     * Opens a type-appropriate editor dialog for the row at the given
     * <em>view</em> index (after sorting/filtering).
     */
    private void editSelectedRow(int viewRow) {
        int modelRow = table.convertRowIndexToModel(viewRow);
        if (modelRow < 0 || modelRow >= prefRows.size()) return;

        PrefRow pr = prefRows.get(modelRow);
        if (pr.setter == null) {
            JOptionPane.showMessageDialog(this,
                    "\"" + pr.name + "\" is read-only (no setter found).",
                    "Read-only preference", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String currentValue = (String) tableModel.getValueAt(modelRow, COL_VALUE);
        // If there is a pending change, show that instead
        if (pendingChanges.containsKey(pr.name)) {
            currentValue = pendingChanges.get(pr.name);
        }

        String newValue = promptForValue(pr, currentValue);
        if (newValue == null) return; // cancelled

        if (!newValue.equals(currentValue)) {
            pendingChanges.put(pr.name, newValue);
            tableModel.setValueAt(newValue, modelRow, COL_VALUE);
            updatePendingBadge();
            table.repaint();
        }
    }

    /**
     * Shows a type-aware input dialog and returns the new value as a
     * {@link String}, or {@code null} if the user cancelled.
     */
    private String promptForValue(PrefRow pr, String currentValue) {
        Class<?> t = pr.type;

        // --- Boolean ---
        if (t == Boolean.TYPE || t == Boolean.class) {
            String[] options = {"true", "false"};
            int idx = "true".equalsIgnoreCase(currentValue) ? 0 : 1;
            JComboBox<String> combo = new JComboBox<>(options);
            combo.setSelectedIndex(idx);
            int res = JOptionPane.showConfirmDialog(this,
                    buildEditorPanel(pr, combo),
                    "Edit: " + pr.name, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            return (res == JOptionPane.OK_OPTION)
                    ? (String) combo.getSelectedItem() : null;
        }

        // --- Charset ---
        if (t == Charset.class) {
            List<String> charsets = new ArrayList<>(Charset.availableCharsets().keySet());
            JComboBox<String> combo = new JComboBox<>(charsets.toArray(new String[0]));
            combo.setSelectedItem(currentValue);
            combo.setEditable(true);
            int res = JOptionPane.showConfirmDialog(this,
                    buildEditorPanel(pr, combo),
                    "Edit: " + pr.name, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            return (res == JOptionPane.OK_OPTION)
                    ? (String) combo.getSelectedItem() : null;
        }

        // --- Integer ---
        if (t == Integer.TYPE || t == Integer.class) {
            int current;
            try { current = Integer.parseInt(currentValue); }
            catch (NumberFormatException e) { current = 0; }
            SpinnerNumberModel model =
                    new SpinnerNumberModel(current, Integer.MIN_VALUE, Integer.MAX_VALUE, 1);
            JSpinner spinner = new JSpinner(model);
            int res = JOptionPane.showConfirmDialog(this,
                    buildEditorPanel(pr, spinner),
                    "Edit: " + pr.name, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            return (res == JOptionPane.OK_OPTION)
                    ? String.valueOf(model.getValue()) : null;
        }

        // --- Double / Float ---
        if (t == Double.TYPE || t == Double.class
                || t == Float.TYPE || t == Float.class) {
            double current;
            try { current = Double.parseDouble(currentValue); }
            catch (NumberFormatException e) { current = 0.0; }
            SpinnerNumberModel model =
                    new SpinnerNumberModel(current, -Double.MAX_VALUE, Double.MAX_VALUE, 0.1);
            JSpinner spinner = new JSpinner(model);
            int res = JOptionPane.showConfirmDialog(this,
                    buildEditorPanel(pr, spinner),
                    "Edit: " + pr.name, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            return (res == JOptionPane.OK_OPTION)
                    ? String.valueOf(model.getValue()) : null;
        }

        // --- String (default) ---
        JTextField field = new JTextField(currentValue, 40);
        field.selectAll();
        int res = JOptionPane.showConfirmDialog(this,
                buildEditorPanel(pr, field),
                "Edit: " + pr.name, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        return (res == JOptionPane.OK_OPTION) ? field.getText() : null;
    }

    /**
     * Builds a small panel shown inside the edit dialog containing a label
     * with the registry key and the supplied editor component.
     */
    private JPanel buildEditorPanel(PrefRow pr, JComponent editor) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setBorder(new EmptyBorder(4, 4, 4, 4));

        JLabel info = new JLabel("<html><b>" + pr.name + "</b>"
                + "<br><font color='gray' size='2'>Registry key: " + pr.regKey + "</font>"
                + "<br><font color='gray' size='2'>Type: " + pr.typeName() + "</font></html>");
        p.add(info, BorderLayout.NORTH);

        JPanel editorRow = new JPanel(new BorderLayout(4, 0));
        editorRow.add(new JLabel("New value:"), BorderLayout.WEST);
        editorRow.add(editor, BorderLayout.CENTER);
        p.add(editorRow, BorderLayout.CENTER);
        return p;
    }

    // -----------------------------------------------------------------------
    // Save / discard / reset
    // -----------------------------------------------------------------------

    /** Persists all pending changes to the registry. */
    private void saveChanges() {
        if (pendingChanges.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No unsaved changes.",
                    "Nothing to save", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<String> errors = new ArrayList<>();
        List<String> saved  = new ArrayList<>();

        for (Map.Entry<String, String> entry : pendingChanges.entrySet()) {
            String name = entry.getKey();
            String raw  = entry.getValue();

            // Find the PrefRow
            PrefRow pr = prefRows.stream()
                    .filter(r -> r.name.equals(name))
                    .findFirst().orElse(null);
            if (pr == null || pr.setter == null) {
                errors.add(name + ": no setter available");
                continue;
            }

            try {
                Object arg = coerce(raw, pr.setter.getParameterTypes()[0]);
                pr.setter.invoke(prefsManager, arg);
                saved.add(name);
            } catch (Exception ex) {
                errors.add(name + ": " + ex.getMessage());
            }
        }

        pendingChanges.clear();
        table.repaint();
        updatePendingBadge();

        StringBuilder msg = new StringBuilder();
        if (!saved.isEmpty()) {
            msg.append("Saved ").append(saved.size()).append(" preference(s).\n");
        }
        if (!errors.isEmpty()) {
            msg.append("\nErrors:\n");
            for (String e : errors) msg.append("  • ").append(e).append("\n");
        }

        JOptionPane.showMessageDialog(this, msg.toString().trim(),
                errors.isEmpty() ? "Saved" : "Saved with errors",
                errors.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Converts a raw String value to the type required by the setter parameter.
     */
    private Object coerce(String raw, Class<?> target) {
        if (target == String.class)        return raw;
        if (target == boolean.class
                || target == Boolean.class) return Boolean.parseBoolean(raw);
        if (target == int.class
                || target == Integer.class) return Integer.parseInt(raw);
        if (target == double.class
                || target == Double.class)  return Double.parseDouble(raw);
        if (target == float.class
                || target == Float.class)   return Float.parseFloat(raw);
        if (target == Charset.class)        return Charset.forName(raw);
        return raw; // fallback
    }

    /** Discards all pending changes and reloads from the registry. */
    private void discardChanges() {
        if (pendingChanges.isEmpty()) return;
        int res = JOptionPane.showConfirmDialog(this,
                "Discard " + pendingChanges.size() + " unsaved change(s)?",
                "Discard changes", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (res != JOptionPane.YES_OPTION) return;

        pendingChanges.clear();
        // Reload original values from registry
        for (int i = 0; i < prefRows.size(); i++) {
            PrefRow pr = prefRows.get(i);
            try {
                Object val = pr.getter.invoke(prefsManager);
                tableModel.setValueAt(val == null ? "" : val.toString(), i, COL_VALUE);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        updatePendingBadge();
        table.repaint();
    }

    /** Resets all preferences to defaults after a confirmation dialog. */
    private void resetPreferences() {
        int res = JOptionPane.showConfirmDialog(this,
                "Reset ALL preferences to their defaults?\n"
                        + "This will also clear all recent-file lists.\n"
                        + "This action cannot be undone.",
                "Reset preferences", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (res != JOptionPane.YES_OPTION) return;

        pendingChanges.clear();
        prefsManager.resetPreferences();
        populateTable();
        updatePendingBadge();

        JOptionPane.showMessageDialog(this,
                "All preferences have been reset to their defaults.",
                "Reset complete", JOptionPane.INFORMATION_MESSAGE);
    }

    // -----------------------------------------------------------------------
    // Clipboard / export
    // -----------------------------------------------------------------------

    /** Copies the value of the currently selected row to the system clipboard. */
    private void copySelectedValue() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a row first.",
                    "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String value = (String) table.getValueAt(viewRow, COL_VALUE);
        if (value == null) value = "";
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(value), null);
        statusLabel.setText("Value copied to clipboard.");
    }

    /** Saves a plain-text dump of all preferences to a user-chosen file. */
    private void exportPreferences() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export preferences to text file");
        chooser.setSelectedFile(new java.io.File("spmf_preferences.txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

        java.io.File file = chooser.getSelectedFile();
        try (java.io.PrintWriter pw = new java.io.PrintWriter(
                new java.io.BufferedWriter(new java.io.FileWriter(file)))) {
            pw.println("SPMF Preferences Export");
            pw.println("Generated: " + new java.util.Date());
            pw.println("=".repeat(70));
            pw.printf("%-30s %-25s %s%n", "Property", "Value", "Registry Key");
            pw.println("-".repeat(70));
            for (int i = 0; i < prefRows.size(); i++) {
                PrefRow pr = prefRows.get(i);
                String value = (String) tableModel.getValueAt(i, COL_VALUE);
                pw.printf("%-30s %-25s %s%n", pr.name, value, pr.regKey);
            }
        } catch (java.io.IOException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not write file:\n" + ex.getMessage(),
                    "Export error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(this,
                "Preferences exported to:\n" + file.getAbsolutePath(),
                "Export successful", JOptionPane.INFORMATION_MESSAGE);
    }

    // -----------------------------------------------------------------------
    // Filter
    // -----------------------------------------------------------------------

    /** Applies the text from the search field as a case-insensitive row filter. */
    private void applyFilter() {
        String text = searchField.getText().trim();
        if (text.isEmpty()) {
            rowSorter.setRowFilter(null);
        } else {
            rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" +
                    java.util.regex.Pattern.quote(text)));
        }
        updateStatus();
    }

    // -----------------------------------------------------------------------
    // Status / badge updates
    // -----------------------------------------------------------------------

    private void updateStatus() {
        int visible = table.getRowCount();
        int total   = tableModel.getRowCount();
        statusLabel.setText(visible + " of " + total + " preferences shown");
    }

    private void updatePendingBadge() {
        int n = pendingChanges.size();
        if (n == 0) {
            pendingLabel.setText("No unsaved changes");
            pendingLabel.setForeground(new Color(100, 100, 100));
            saveButton.setEnabled(true);
            discardButton.setEnabled(false);
        } else {
            pendingLabel.setText("⚠  " + n + " unsaved change" + (n > 1 ? "s" : ""));
            pendingLabel.setForeground(new Color(180, 100, 0));
            saveButton.setEnabled(true);
            discardButton.setEnabled(true);
        }
    }

    // -----------------------------------------------------------------------
    // Keyboard shortcuts
    // -----------------------------------------------------------------------

    private void installKeyboardShortcuts() {
        // Ctrl+S → Save
        KeyStroke saveKS = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK);
        getRootPane().registerKeyboardAction(e -> saveChanges(),
                saveKS, JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Ctrl+F → Focus search
        KeyStroke searchKS = KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK);
        getRootPane().registerKeyboardAction(e -> searchField.requestFocusInWindow(),
                searchKS, JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Escape → Discard
        KeyStroke escKS = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        getRootPane().registerKeyboardAction(e -> discardChanges(),
                escKS, JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Enter → Edit selected row
        KeyStroke enterKS = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        table.registerKeyboardAction(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) editSelectedRow(row);
        }, enterKS, JComponent.WHEN_FOCUSED);
    }

    // -----------------------------------------------------------------------
    // Close handling
    // -----------------------------------------------------------------------

    private void attemptClose() {
        if (!pendingChanges.isEmpty()) {
            int res = JOptionPane.showConfirmDialog(this,
                    "You have " + pendingChanges.size() + " unsaved change(s).\n"
                            + "Save before closing?",
                    "Unsaved changes",
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (res == JOptionPane.CANCEL_OPTION) return;
            if (res == JOptionPane.YES_OPTION)    saveChanges();
        }
        dispose();
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private static JButton makeButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        btn.setToolTipText(tooltip);
        btn.setFocusPainted(false);
        return btn;
    }

    // -----------------------------------------------------------------------
    // Custom cell renderer
    // -----------------------------------------------------------------------

    /**
     * Renders table cells with alternating row colours, pending-change
     * highlights, and grey-out for read-only rows.
     */
    private class TableCellRendererImpl extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable tbl, Object value,
                boolean isSelected, boolean hasFocus, int viewRow, int viewCol) {

            super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, viewRow, viewCol);

            int modelRow = tbl.convertRowIndexToModel(viewRow);
            String propName = (modelRow >= 0 && modelRow < prefRows.size())
                    ? prefRows.get(modelRow).name : null;
            boolean modified  = propName != null && pendingChanges.containsKey(propName);
            boolean readOnly  = propName != null
                    && modelRow < prefRows.size()
                    && prefRows.get(modelRow).setter == null;

            if (!isSelected) {
                if (modified) {
                    setBackground(viewCol == COL_VALUE ? CELL_MODIFIED : ROW_MODIFIED);
                } else {
                    setBackground(viewRow % 2 == 0 ? ROW_EVEN : ROW_ODD);
                }
                setForeground(readOnly ? Color.GRAY : Color.BLACK);
            }

            // Align type column centrally
            setHorizontalAlignment(viewCol == COL_TYPE ? CENTER : LEFT);

            // Bold the property name
            if (viewCol == COL_PROPERTY) {
                setFont(getFont().deriveFont(Font.BOLD));
            } else {
                setFont(getFont().deriveFont(Font.PLAIN));
            }

            // Tooltip for truncated content
            setText(value == null ? "" : value.toString());
            setToolTipText(value == null ? null : value.toString());

            return this;
        }
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    /** Main method for standalone testing. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(PreferencesViewer::new);
    }
}