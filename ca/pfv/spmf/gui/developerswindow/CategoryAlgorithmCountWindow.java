package ca.pfv.spmf.gui.developerswindow;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.gui.SortableJTable;

/*
 * Copyright (c) 2008-2015 Philippe Fournier-Viger
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
 * A class for developers that display the number of algorithms by category in SPMF.
 * 
 * @author Philippe Fournier-Viger
 *
 */
public class CategoryAlgorithmCountWindow extends JFrame {

	/** generated serial UID */
	private static final long serialVersionUID = -7259382386338730L;

	/**
	 * Constructor
	 * 
	 * @param runAsStandalone set to true if the window is run as a standalone
	 *                        program. Otherwise false.
	 * @throws Exception if some exception occurs
	 */
	/**
	 * Constructor
	 * 
	 * @param runAsStandalone set to true if the window is run as a standalone
	 *                        program. Otherwise false.
	 * @throws Exception if some exception occurs
	 */
	public CategoryAlgorithmCountWindow(boolean runAsStandalone) throws Exception {
		// Create a map to store the category names and their counts
		Map<String, Integer> categoryCountMap = new HashMap<>();

		// Loop through the list of algorithms and get their categories
		for (String algorithmName : AlgorithmManager.getInstance().getListOfAlgorithmsAsString(true,true,true, true, true)) {
			if (algorithmName.contains("---")) {
				continue;
			}
			DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
			String category = algorithm.getAlgorithmCategory();

			// If the map already contains the category, increment its count by one
			categoryCountMap.put(category, categoryCountMap.getOrDefault(category, 0) + 1);
		}

		// Create a list to store the map entries
		List<Map.Entry<String, Integer>> categoryCountList = new ArrayList<>(categoryCountMap.entrySet());

		// Sort the list by the count value in decreasing order
		Collections.sort(categoryCountList, (e1, e2) -> e2.getValue().compareTo(e1.getValue()));

		// Create a table model to store the data for the JTable
		DefaultTableModel tableModel = new DefaultTableModel();
		tableModel.addColumn("Category Name");
		tableModel.addColumn("Count");

		// Loop through the sorted list and add the entries to the table model
		for (Map.Entry<String, Integer> entry : categoryCountList) {
			tableModel.addRow(new Object[] { entry.getKey(), entry.getValue() });
		}

		// Create a JTable with the table model
		JTable table = new SortableJTable();
		table.setModel(tableModel);
		table.setVisible(true);

		// Create a scroll pane to hold the JTable
		JScrollPane scrollPane = new JScrollPane(table);

		// Create a label for the JTable
		JLabel label = new JLabel("Number of algorithm per category", SwingConstants.CENTER);

		// Create a button to export the table data to a CSV file
		JButton button = new JButton("Export to CSV");
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// Ask the user to choose a file name and location
				JFileChooser fileChooser = new JFileChooser();
				fileChooser.setDialogTitle("Save as CSV file");
				int userSelection = fileChooser.showSaveDialog(CategoryAlgorithmCountWindow.this);
				if (userSelection == JFileChooser.APPROVE_OPTION) {
					File fileToSave = fileChooser.getSelectedFile();
					// Call the export method with the table and the file name
					exportToCSV(table, fileToSave.getAbsolutePath());
				}
			}
		});

		// Create a panel to hold the label, the scroll pane, and the button
		JPanel panel = new JPanel(new BorderLayout());

		// Add the label, the scroll pane, and the button to the panel
		panel.add(label, BorderLayout.NORTH);
		panel.add(scrollPane, BorderLayout.CENTER);
		panel.add(button, BorderLayout.SOUTH);

		// Add the panel to the frame
		add(panel, BorderLayout.CENTER);

		setSize(800, 600);
		if (runAsStandalone) {
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}

		// Set the window in the center of the screen
		setLocationRelativeTo(null);

		// Make the frame visible
		setVisible(true);

	}

	public static void main(String[] args) throws Exception {
		@SuppressWarnings("unused")
		CategoryAlgorithmCountWindow viewer = new CategoryAlgorithmCountWindow(true);
	}

	// Method to export the table data to a CSV file
	public static void exportToCSV(JTable table, String fileName) {
		try {
			// Get the table model
			TableModel model = table.getModel();
			// Create a file writer
			FileWriter csv = new FileWriter(new File(fileName));
			// Write the column names as the first line
			for (int i = 0; i < model.getColumnCount(); i++) {
				csv.write(model.getColumnName(i) + ",");
			}
			csv.write("\n");
			// Write the data for each row
			for (int i = 0; i < model.getRowCount(); i++) {
				for (int j = 0; j < model.getColumnCount(); j++) {
					csv.write(model.getValueAt(i, j).toString() + ",");
				}
				csv.write("\n");
			}
			// Close the file writer
			csv.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
