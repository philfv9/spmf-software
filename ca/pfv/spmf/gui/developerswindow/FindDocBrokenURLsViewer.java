package ca.pfv.spmf.gui.developerswindow;

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
 * A class for developers that display the algorithms that have a broken link for the documentation
 * @author Philippe Fournier-Viger, 2024
 *
 */

//Import the necessary classes
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * This is a tool to find the list of broken URLs in the documentation of SPMF.
 * @author Philippe Fournier-Viger, 2024.
 */
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;

public class FindDocBrokenURLsViewer {

	private static JButton btnCheck;
	private static JProgressBar progressBar;
	private static JFrame frame;

	public FindDocBrokenURLsViewer() throws Exception {
		createAndShowGUI();
	}

	public void createAndShowGUI() {
		frame = new JFrame("SPMF Tool to Find Broken Documentation URLs (for SPMF developpers)");
		frame.setSize(800, 100);
		frame.setLayout(new BorderLayout());
		frame.setLocationRelativeTo(null);

		btnCheck = new JButton("Check for Broken URLs");
		btnCheck.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				// Disable the button
				btnCheck.setEnabled(false);

				// Run the check in a separate thread to keep the GUI responsive
				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							List<String> algorithms = AlgorithmManager.getInstance().getListOfAlgorithmsAsString(true,true,true,
									true, true);
							progressBar.setMaximum(algorithms.size());
							progressBar.setValue(0);
							progressBar.setStringPainted(true);

							List<DescriptionOfAlgorithm> brokenURLs = checkAlgorithmsForBrokenURLs(progressBar,
									algorithms);
							displayBrokenURLsInJTable(brokenURLs);
						} catch (Exception ex) {
							ex.printStackTrace();
						}

						// After checking, enable the button
						SwingUtilities.invokeLater(new Runnable() {
							@Override
							public void run() {
								btnCheck.setEnabled(true);
							}
						});
					}
				}).start();
			}
		});

		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(true);

		frame.getContentPane().add(btnCheck, BorderLayout.NORTH);
		frame.getContentPane().add(progressBar, BorderLayout.CENTER);
		frame.setVisible(true);
	}

	public static void main(String[] args) throws Exception {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				try {
					new FindDocBrokenURLsViewer();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	// Define a method to check the algorithms for broken URLs and return a list of
	// them
	// Modify the checkAlgorithmsForBrokenURLs method to accept the progress bar and
	// list of algorithms
	public static List<DescriptionOfAlgorithm> checkAlgorithmsForBrokenURLs(JProgressBar progressBar,
			List<String> algorithms) throws Exception {
		System.out.println("Checking algorithms for broken URLs...");

		// Create an empty list to store the algorithms with broken URLs
		List<DescriptionOfAlgorithm> brokenURLs = new ArrayList<>();
		int progress = 0;

		// Loop through the list of algorithms
		for (String algorithmName : AlgorithmManager.getInstance().getListOfAlgorithmsAsString(true,true,true, true, true)) {
			// If the algorithm name contains "---", skip it
			if (algorithmName.contains("---")) {
				progress++;
				continue;
			}
			// Get the description of the algorithm
			DescriptionOfAlgorithm algorithm = AlgorithmManager.getInstance().getDescriptionOfAlgorithm(algorithmName);
			// Get the documentation URL from the description
			String url = algorithm.getURLOfDocumentation();
			// Check if the URL is accessible
			boolean isAccessible = true;
			try {
				// Create a URL object from the string
				URL urlObject = new URI(url).toURL();
				// Open a connection to the URL
				HttpURLConnection connection = (HttpURLConnection) urlObject.openConnection();
				// Set the request method to HEAD
				connection.setRequestMethod("HEAD");
				// Get the response code
				int responseCode = connection.getResponseCode();
				// If the response code is not OK (200), then the URL is not accessible
				if (responseCode != HttpURLConnection.HTTP_OK) {
					isAccessible = false;
				}
				// Disconnect the connection
				connection.disconnect();
			} catch (Exception e) {
				// If an exception occurs, then the URL is not accessible
				isAccessible = false;
			}
			// If the URL is not accessible, add the algorithm to the list of broken URLs
			if (!isAccessible) {
				brokenURLs.add(algorithm);
				System.out.println(algorithm.getName() + " ... URL IS BROKEN: " + url);
			} else {
				System.out.println(algorithm.getName() + " ... URL IS OK");
			}

			progress++;
			final int currentProgress = progress;
			progressBar.setValue(currentProgress);
		}
		System.out.println("Broken URLs: " + brokenURLs);
		
//		progressBar.setValue(100);
		// Return the list of broken URLs
		return brokenURLs;
	}

	// Define a method to display the algorithms with broken URLs in a JTable
	public static void displayBrokenURLsInJTable(List<DescriptionOfAlgorithm> brokenURLs) {
		// Create a new frame
		JFrame frame = new JFrame("List of broken URLs from the SPMF documentation");
		frame.setSize(800, 600);

		// Create a table model with two columns
		DefaultTableModel model = new DefaultTableModel();
		model.addColumn("Name");
		model.addColumn("Documentation");

		// Loop through the list of broken URLs and add them as rows to the table model
		for (DescriptionOfAlgorithm algorithm : brokenURLs) {
			model.addRow(new Object[] { algorithm.getName(), algorithm.getURLOfDocumentation() });
		}

		// Create a table with the table model
		JTable table = new JTable(model);

		// Create a custom table cell renderer that extends the default one
		class CustomTableCellRenderer extends DefaultTableCellRenderer {
			/**
			 * default serial UID
			 */
			private static final long serialVersionUID = 1L;

			// Override the getTableCellRendererComponent method
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				// Call the super method to get the default component
				Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
						column);
				// Set the font color to red
				component.setForeground(Color.RED);
				// Return the component
				return component;
			}
		}

		// Set the custom table cell renderer to the table for both columns
		table.getColumnModel().getColumn(0).setCellRenderer(new CustomTableCellRenderer());
		table.getColumnModel().getColumn(1).setCellRenderer(new CustomTableCellRenderer());
		System.out.println("");
		System.out.println();

		// Add a scroll pane to the table
		JScrollPane scrollPane = new JScrollPane(table);

		// Add the scroll pane to the frame
		frame.add(scrollPane, BorderLayout.CENTER);
		frame.add(new JLabel("The number of broken URLs is " + model.getRowCount()), BorderLayout.SOUTH);

		// Set the window in the center of the screen
		frame.setLocationRelativeTo(null);

		// Make the frame visible
		frame.setVisible(true);
	}

}
