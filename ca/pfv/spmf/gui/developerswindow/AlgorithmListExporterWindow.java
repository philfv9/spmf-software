package ca.pfv.spmf.gui.developerswindow;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;

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
 * A class for developers that display the whole list of algorithms and allow to
 * export to HTML and CSV
 * 
 * @author Philippe Fournier-Viger, 2023
 *
 */
public class AlgorithmListExporterWindow {

	public AlgorithmListExporterWindow(boolean runAsStandalone) throws Exception {

		displayAlgorithms(AlgorithmManager.getInstance().getListOfAlgorithmsAsString(true,true,true, true, true), runAsStandalone);
	}

// Define the method
	public static void displayAlgorithms(List<String> algorithms, boolean runAsStandalone) throws Exception {
		// Create a new frame
		JFrame frame = new JFrame("Algorithm List Exporter");
		if(runAsStandalone) {
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}
		frame.setSize(800, 600);

		// Create a table model with six columns
		DefaultTableModel model = new DefaultTableModel();
		model.addColumn("Name");
		model.addColumn("Category");
		model.addColumn("Type");
		model.addColumn("Author");
		model.addColumn("Documentation");
		model.addColumn("Parameters");
		model.addColumn("Input File Types");
		model.addColumn("Output File Types");

		// Loop through the list of algorithms and add them as rows to the table model
		for (String algorithmName : algorithms) {
//			System.out.println(algorithmName);
			if (algorithmName.contains("---")) {
				continue;
			}
			DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
			model.addRow(new Object[] { algorithm.getName(), algorithm.getAlgorithmCategory(), algorithm.getAlgorithmType().toString(),
					algorithm.getImplementationAuthorNames(), algorithm.getURLOfDocumentation(),
					arrayToString(algorithm.getParametersDescription()), arrayToString(algorithm.getInputFileTypes()),  arrayToString(algorithm.getOutputFileTypes()) });
		}

		// Create a table with the table model
		JTable table = new JTable(model);

		// Add a scroll pane to the table
		JScrollPane scrollPane = new JScrollPane(table);

		// Add the scroll pane to the frame
		frame.add(scrollPane, BorderLayout.CENTER);

		// Create a button to export the table as an HTML file
		JButton exportButton = new JButton("Export as HTML");

		// Create a button to export the table as a CSV file
		JButton csvButton = new JButton("Export as CSV");

		// Add an action listener to the button
		csvButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// Create a file chooser to select the destination file
				JFileChooser fileChooser = new JFileChooser();
				fileChooser.setDialogTitle("Save as CSV");
				fileChooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));

				// Show the file chooser and get the user's choice
				int userChoice = fileChooser.showSaveDialog(frame);

				// If the user approves the choice
				if (userChoice == JFileChooser.APPROVE_OPTION) {
					// Get the selected file
					File file = fileChooser.getSelectedFile();

					// Add the .csv extension if not present
					if (!file.getName().endsWith(".csv")) {
						file = new File(file.getAbsolutePath() + ".csv");
					}

					// Try to write the table as a CSV file
					try {
						writeTableAsCSV(file, table);
						// Show a success message
						JOptionPane.showMessageDialog(frame, "The table has been exported as " + file.getName(),
								"Success", JOptionPane.INFORMATION_MESSAGE);
					} catch (Exception ex) {
						// Show an error message
						JOptionPane.showMessageDialog(frame,
								"An error occurred while exporting the table: " + ex.getMessage(), "Error",
								JOptionPane.ERROR_MESSAGE);
					}
				}
			}
		});

		// Add an action listener to the button
		exportButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// Create a file chooser to select the destination file
				JFileChooser fileChooser = new JFileChooser();
				fileChooser.setDialogTitle("Save as HTML");
				fileChooser.setFileFilter(new FileNameExtensionFilter("HTML files", "html"));

				// Show the file chooser and get the user's choice
				int userChoice = fileChooser.showSaveDialog(frame);

				// If the user approves the choice
				if (userChoice == JFileChooser.APPROVE_OPTION) {
					// Get the selected file
					File file = fileChooser.getSelectedFile();

					// Add the .html extension if not present
					if (!file.getName().endsWith(".html")) {
						file = new File(file.getAbsolutePath() + ".html");
					}

					// Try to write the table as an HTML file
					try {
						writeTableAsHTML(file, table);
						// Show a success message
						JOptionPane.showMessageDialog(frame, "The table has been exported as " + file.getName(),
								"Success", JOptionPane.INFORMATION_MESSAGE);
					} catch (Exception ex) {
						// Show an error message
						JOptionPane.showMessageDialog(frame,
								"An error occurred while exporting the table: " + ex.getMessage(), "Error",
								JOptionPane.ERROR_MESSAGE);
					}
				}
			}
		});
		
		JPanel panelPuttons = new JPanel();
		panelPuttons.add(csvButton);
		panelPuttons.add(exportButton);
		
		JLabel listLabel = new JLabel("List of algorithms", SwingConstants.CENTER);

		// Add the button to the frame
		frame.add(listLabel, BorderLayout.NORTH);

		// Add the button to the frame
		frame.add(panelPuttons, BorderLayout.SOUTH);

		// Set the window in the center of the screen
		frame.setLocationRelativeTo(null);

		// Make the frame visible
		frame.setVisible(true);
	}

// Define a helper method to convert an array of objects to a string
	private static String arrayToString(Object[] array) {
		if (array == null) {
			return "None";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (int i = 0; i < array.length; i++) {
			sb.append(array[i]);
			if (i < array.length - 1) {
				sb.append(", ");
			}
		}
		sb.append("]");
		return sb.toString();
	}

// Define a helper method to write a table as an HTML file
	private static void writeTableAsHTML(File file, JTable table) throws Exception {
		// Create a buffered writer to write to the file
		BufferedWriter writer = new BufferedWriter(new FileWriter(file));

		// Write the HTML header and title
		writer.write("<!DOCTYPE html>\n");
		writer.write("<html>\n");
		writer.write("<head>\n");
		writer.write("<title>Algorithms</title>\n");

		// Write some CSS style for the table
		writer.write("<style>\n");
		writer.write("table, th, td {\n");
		writer.write("  border: 1px solid black;\n");
		writer.write("  border-collapse: collapse;\n");
		writer.write("}\n");
		writer.write("th, td {\n");
		writer.write("  padding: 10px;\n");
		writer.write("}\n");
		writer.write("</style>\n");

		// Write the HTML body and table
		writer.write("</head>\n");
		writer.write("<body>\n");
		writer.write("<h1>Algorithms</h1>\n");
		writer.write("<table>\n");

		// Write the table header
		writer.write("<tr>\n");
		for (int i = 0; i < table.getColumnCount(); i++) {
			writer.write("<th>" + table.getColumnName(i) + "</th>\n");
		}
		writer.write("</tr>\n");

		// Write the table data
		for (int i = 0; i < table.getRowCount(); i++) {
			writer.write("<tr>\n");
			for (int j = 0; j < table.getColumnCount(); j++) {
				writer.write("<td>" + table.getValueAt(i, j) + "</td>\n");
			}
			writer.write("</tr>\n");
		}

		// Write the HTML footer
		writer.write("</table>\n");
		writer.write("</body>\n");
		writer.write("</html>\n");

		// Close the writer
		writer.close();
	}

	// Define a helper method to write a table as a CSV file
	private static void writeTableAsCSV(File file, JTable table) throws Exception {
		// Create a buffered writer to write to the file
		BufferedWriter writer = new BufferedWriter(new FileWriter(file));

		// Get the table model
		TableModel model = table.getModel();

		// Write the table header
		for (int i = 0; i < model.getColumnCount(); i++) {
			writer.write(model.getColumnName(i));
			if (i < model.getColumnCount() - 1) {
				writer.write(",");
			}
		}
		writer.newLine();

		// Write the table data
		for (int i = 0; i < model.getRowCount(); i++) {
			for (int j = 0; j < model.getColumnCount(); j++) {
				writer.write(model.getValueAt(i, j).toString());
				if (j < model.getColumnCount() - 1) {
					writer.write(",");
				}
			}
			writer.newLine();
		}

		// Close the writer
		writer.close();
	}

	public static void main(String[] args) throws Exception {
		@SuppressWarnings("unused")
		AlgorithmListExporterWindow viewer = new AlgorithmListExporterWindow(true);
	}
}
