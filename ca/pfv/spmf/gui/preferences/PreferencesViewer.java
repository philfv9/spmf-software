package ca.pfv.spmf.gui.preferences;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Modifier;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import ca.pfv.spmf.gui.MainWindow;
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
 * This class is a simple window to visualize the preferences that are stored in
 * the registry for SPMF. It also includes a button to reset the preferences.
 *
 * <p>The table is populated via reflection: for every {@code public static final}
 * String field in {@link PreferencesManager} whose name starts with an upper-case
 * letter, the viewer looks for a matching {@code get<FieldName>()} method. Fields
 * that have no such getter (for example the private {@code RecentInputFilePrefix}
 * and {@code RecentOutputFilePrefix} constants, and the {@code MAX_RECENT_FILES}
 * constant) are silently skipped so the viewer does not crash when the
 * preferences class is extended.</p>
 *
 * @see MainWindow
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class PreferencesViewer extends JFrame implements ActionListener {

    /** The table model for the JTable */
    private DefaultTableModel tableModel;
    /** The JTable component */
    private JTable table;
    /** The JButton component */
    private JButton ResetButton;
    /** The PreferencesManager instance */
    private PreferencesManager prefsManager;

    /**
     * Constructor
     */
    public PreferencesViewer() {
        // Set the title of the window
        setTitle("SPMF Preferences Viewer");
        // Set the default close operation
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        // Set the layout of the content pane
        getContentPane().setLayout(new BorderLayout());
        // Get the PreferencesManager instance
        prefsManager = PreferencesManager.getInstance();
        // Create the table model with two columns
        tableModel = new DefaultTableModel(new String[] { "Property", "Value", "Registry key" }, 0);
        // Populate the table model with the preferences from the PreferencesManager
        populateTableModel();
        // Create the JTable with the table model
        table = new SortableJTable();
        table.setModel(tableModel);
        // Make the table cells non-editable
        table.setEnabled(false);
        // Create a scroll pane for the table
        JScrollPane scrollPane = new JScrollPane(table);
        // Add the scroll pane to the center of the content pane
        getContentPane().add(scrollPane, BorderLayout.CENTER);
        // Create the reset button with an action listener
        ResetButton = new JButton("Reset preferences");
        ResetButton.addActionListener(this);
        // Add the reset button to the south of the content pane
        getContentPane().add(ResetButton, BorderLayout.SOUTH);
        // Set the size of the window
        setSize(600, 400);
        // Set the window in the center of the screen
        this.setLocationRelativeTo(null);
        // Make the window visible
        setVisible(true);
    }

    /**
     * Populates the table model with the preferences from the PreferencesManager.
     *
     * <p>Only {@code public} fields whose names begin with an upper-case letter are
     * considered. For each such field the method looks for a corresponding
     * {@code get<FieldName>()} method. If no such method exists (e.g. for the
     * private recent-file prefix constants or for non-String fields like
     * {@code MAX_RECENT_FILES}) the field is silently skipped.</p>
     */
    private void populateTableModel() {
        for (java.lang.reflect.Field field : PreferencesManager.class.getDeclaredFields()) {
            try {
                // Skip non-public fields — private constants such as
                // RecentInputFilePrefix, RecentOutputFilePrefix, and
                // MAX_RECENT_FILES are not meant to be shown in this viewer.
                if (Modifier.isPrivate(field.getModifiers())) {
                    continue;
                }

                // Skip fields whose names do not start with an upper-case letter.
                // This guards against any future package-private or protected
                // helper fields that follow a different naming convention.
                String key = field.getName();
                if (key.isEmpty() || !Character.isUpperCase(key.charAt(0))) {
                    continue;
                }

                // Look for a getter method named get<FieldName>().
                // If no such method exists we skip this field silently.
                java.lang.reflect.Method getter;
                try {
                    getter = prefsManager.getClass().getMethod("get" + key);
                } catch (NoSuchMethodException nsme) {
                    // No matching getter — this field is not a user-visible preference
                    // (e.g. MAX_RECENT_FILES which is an int constant, not a registry key).
                    continue;
                }

                // Invoke the getter and skip the field if the result is null.
                Object result = getter.invoke(prefsManager);
                if (result == null) {
                    continue;
                }

                String value  = result.toString();
                String regKey = field.get(null).toString();

                // Add a row to the table model with the property name, its current
                // value, and the underlying registry key string.
                tableModel.addRow(new String[] { key, value, regKey });

            } catch (Exception e) {
                // Log unexpected reflection errors but continue so that one bad
                // field does not prevent all other preferences from being shown.
                e.printStackTrace();
            }
        }
    }

    /**
     * Handles button clicks. When the Reset button is clicked all preferences
     * are reset to their defaults (including clearing the recent-file lists)
     * and the window is closed.
     *
     * @param e the action event
     */
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == ResetButton) {
            PreferencesManager.getInstance().resetPreferences();
            JOptionPane.showMessageDialog(this, "Preferences reset successfully.", "Message",
                    JOptionPane.INFORMATION_MESSAGE);
            setVisible(false);
        }
    }

    /** Main method for testing */
    public static void main(String[] args) {
        // Create an instance of the PreferencesViewer class
        @SuppressWarnings("unused")
        PreferencesViewer gui = new PreferencesViewer();
    }
}