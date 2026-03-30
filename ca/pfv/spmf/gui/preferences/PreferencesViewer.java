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
		// Make the table cells editable
		table.setEnabled(false);
		// Create a scroll pane for the table
		JScrollPane scrollPane = new JScrollPane(table);
		// Add the scroll pane to the center of the content pane
		getContentPane().add(scrollPane, BorderLayout.CENTER);
		// Create the save button with an action listener
		ResetButton = new JButton("Reset preferences");
		ResetButton.addActionListener(this);
		// Add the save button to the south of the content pane
		getContentPane().add(ResetButton, BorderLayout.SOUTH);
		// Set the size of the window
		setSize(600, 400);

		// Set the window in the center of the screen
		this.setLocationRelativeTo(null);
		// Make the window visible
		setVisible(true);
	}

	/**
	 * This method populates the table model with the preferences from the
	 * PreferencesManager
	 */
	private void populateTableModel() {
		// Get all the preference keys from the PreferencesManager class using
		// reflection
		for (java.lang.reflect.Field field : PreferencesManager.class.getDeclaredFields()) {
			try {
				if (Modifier.isPrivate(field.getModifiers())) {
					continue;
				}
				// Get the field name and value as strings
				String key = field.getName();
				Object temp = prefsManager.getClass().getMethod("get" + key).invoke(prefsManager);
				if(temp != null) {
					String value = prefsManager.getClass().getMethod("get" + key).invoke(prefsManager).toString();
					String regKey = field.get(null).toString();
	
					// Add a row to the table model with the key and value
					tableModel.addRow(new String[] { key, value, regKey });
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		// If the save button is clicked, save the preferences and show a confirmation
		// message
		if (e.getSource() == ResetButton) {
			PreferencesManager.getInstance().resetPreferences();
			JOptionPane.showMessageDialog(this, "Preferences reset successfully.", "Message",
					JOptionPane.INFORMATION_MESSAGE);
			setVisible(false);
		}
	}

	/** Main method for testing */
	public static void main(String[] args) {
		// Create an instance of the PreferencesGUI class
		@SuppressWarnings("unused")
		PreferencesViewer gui = new PreferencesViewer();

	}
}