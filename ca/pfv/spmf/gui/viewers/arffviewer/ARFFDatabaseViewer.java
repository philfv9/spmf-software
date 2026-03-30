package ca.pfv.spmf.gui.viewers.arffviewer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableModel;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.SortableJTable;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.gui.visuals.histograms.HistogramDistributionPanel.Order;
import ca.pfv.spmf.gui.visuals.histograms.HistogramDistributionWindow;
import ca.pfv.spmf.test.MainTestApriori_SaveToFile;

/*
 * Copyright (c) 2024 Philippe Fournier-Viger
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
 * A viewer for ARFF database files with a split-pane layout showing
 * a data table on the left and a statistics/attribute diversity panel on the right.
 * The UI organization mirrors that of TransactionDatabaseViewer.
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class ARFFDatabaseViewer {

	/** The table */
	private JTable table;
	/** The scroll pane for the table */
	private JScrollPane scrollPane;
	/** The status label */
	private JLabel statusLabel;
	/** The name label */
	private JLabel nameLabel;
	/** The frame */
	private JFrame frame;
	/** The current database */
	private ARFFDatabase currentDatabase;
	/** The statistics panel */
	private JPanel statsPanel;
	/** The top-K attributes panel */
	private JPanel topKPanel;
	/** The right side panel containing stats and top-K */
	private JPanel rightPanel;
	/** The scroll pane wrapping the right panel */
	private JScrollPane rightScrollPane;
	/** The split pane separating table and right panel */
	private JSplitPane splitPane;
	/** The number of top-K attributes to display */
	private static final int TOP_K_COUNT = 5;
	/** The number of bottom-K attributes to display */
	private static final int BOTTOM_K_COUNT = 5;
	/** Whether the right panel is currently visible */
	private boolean rightPanelVisible;
	/** The saved divider location when hiding the right panel */
	private int savedDividerLocation;
	/** Menu item for toggling the statistics panel */
	private JCheckBoxMenuItem showStatsPanelMenuItem;
	/** Whether running as standalone program */
	private boolean runAsStandalone;

	/**
	 * Constructor
	 * 
	 * @param runAsStandaloneProgram if true, this tool will be run in standalone
	 *                               mode (close the window will close the program).
	 *                               Otherwise not.
	 * @param filePath               the path to the database file to open, or null
	 */
	public ARFFDatabaseViewer(boolean runAsStandaloneProgram, String filePath) {
		this.runAsStandalone = runAsStandaloneProgram;
		this.rightPanelVisible = true;

		frame = new JFrame("SPMF ARFF Database Viewer " + Main.SPMF_VERSION);
		if (runAsStandaloneProgram) {
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}

		// Create the menu bar
		JMenuBar menuBar = createMenuBar();
		frame.setJMenuBar(menuBar);

		// Create the table
		table = new SortableJTable();
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

		scrollPane = new JScrollPane(table);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

		// Create the right side panel for statistics and top-K attributes
		rightPanel = new JPanel();
		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
		rightPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		// Create the statistics panel
		statsPanel = createEmptyStatsPanel();
		rightPanel.add(statsPanel);

		// Create the top-K attributes panel
		topKPanel = createEmptyTopKPanel();
		rightPanel.add(topKPanel);

		// Wrap the right panel in a scroll pane
		rightScrollPane = new JScrollPane(rightPanel);
		rightScrollPane.setPreferredSize(new Dimension(280, 400));
		rightScrollPane.setMinimumSize(new Dimension(200, 100));
		rightScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		rightScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

		// Create a split pane with the table on the left and stats on the right
		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, rightScrollPane);
		splitPane.setResizeWeight(1.0); // Give extra space to the table
		splitPane.setOneTouchExpandable(true); // Add collapse/expand arrows
		splitPane.setDividerSize(8);
		frame.add(splitPane, BorderLayout.CENTER);

		// Create the status bar at the bottom
		JPanel statusPanel = new JPanel();
		statusPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		statusLabel = new JLabel();
		statusPanel.add(statusLabel);
		frame.add(statusPanel, BorderLayout.SOUTH);

		// Create the top panel with the name label
		JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		nameLabel = new JLabel();
		nameLabel.setText("Database: " + (filePath != null ? filePath : "(none)"));
		topPanel.add(nameLabel);
		frame.add(topPanel, BorderLayout.NORTH);

		// Load the file if a path was provided
		if (filePath != null) {
			openDatabaseFile(filePath);
		}

		// Set the size and location of the frame
		frame.setSize(1100, 600);
		frame.setLocationRelativeTo(null);
		frame.pack();
		frame.setVisible(true);

		// Set the initial divider location after the frame is visible
		splitPane.setDividerLocation(frame.getWidth() - 300);
	}

	/**
	 * Create the menu bar with all menus
	 * 
	 * @return the configured menu bar
	 */
	private JMenuBar createMenuBar() {
		JMenuBar menuBar = new JMenuBar();

		// File menu
		JMenu fileMenu = createFileMenu();
		menuBar.add(fileMenu);

		// View menu
		JMenu viewMenu = createViewMenu();
		menuBar.add(viewMenu);

		// Tools menu
		JMenu toolsMenu = createToolsMenu();
		menuBar.add(toolsMenu);

		return menuBar;
	}

	/**
	 * Create the File menu
	 * 
	 * @return the File menu
	 */
	private JMenu createFileMenu() {
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);

		// Open database
		JMenuItem openItem = new JMenuItem("Open Database...");
		openItem.setMnemonic(KeyEvent.VK_O);
		openItem.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/Open24.gif")));
		openItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				chooseAFile();
			}
		});
		fileMenu.add(openItem);

		return fileMenu;
	}

	/**
	 * Create the View menu
	 * 
	 * @return the View menu
	 */
	private JMenu createViewMenu() {
		JMenu viewMenu = new JMenu("View");
		viewMenu.setMnemonic(KeyEvent.VK_V);

		// Show/Hide statistics panel
		showStatsPanelMenuItem = new JCheckBoxMenuItem("Show Statistics Panel");
		showStatsPanelMenuItem.setSelected(true);
		showStatsPanelMenuItem.setMnemonic(KeyEvent.VK_S);
		showStatsPanelMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				toggleStatisticsPanel();
			}
		});
		viewMenu.add(showStatsPanelMenuItem);

		return viewMenu;
	}

	/**
	 * Create the Tools menu
	 * 
	 * @return the Tools menu
	 */
	private JMenu createToolsMenu() {
		JMenu toolsMenu = new JMenu("Tools");
		toolsMenu.setMnemonic(KeyEvent.VK_T);

		// View attribute distinct value count distribution
		JMenuItem attrDistItem = new JMenuItem("View Attribute Distinct Value Distribution");
		attrDistItem.setMnemonic(KeyEvent.VK_A);
		attrDistItem.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/histogram.png")));
		attrDistItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				viewAttributeDistinctValueDistribution();
			}
		});
		toolsMenu.add(attrDistItem);

		return toolsMenu;
	}

	/**
	 * Toggle the visibility of the statistics panel (right side)
	 */
	private void toggleStatisticsPanel() {
		if (rightPanelVisible) {
			// Hide the right panel
			savedDividerLocation = splitPane.getDividerLocation();
			rightScrollPane.setVisible(false);
			splitPane.setDividerSize(0);
			splitPane.setDividerLocation(splitPane.getWidth());
			rightPanelVisible = false;
		} else {
			// Show the right panel
			rightScrollPane.setVisible(true);
			splitPane.setDividerSize(8);
			splitPane.setDividerLocation(savedDividerLocation);
			rightPanelVisible = true;
		}
		splitPane.revalidate();
		splitPane.repaint();
	}

	/**
	 * Create an empty statistics panel (placeholder before database is loaded)
	 * 
	 * @return the empty statistics panel
	 */
	private JPanel createEmptyStatsPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Summary Statistics"));
		panel.add(new JLabel("Load a database to view statistics"));
		return panel;
	}

	/**
	 * Create an empty top-K attributes panel (placeholder before database is loaded)
	 * 
	 * @return the empty top-K panel
	 */
	private JPanel createEmptyTopKPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Attribute Diversity"));
		panel.add(new JLabel("Load a database to view attribute diversity"));
		return panel;
	}

	/**
	 * Create and populate the statistics panel with database statistics
	 * 
	 * @param db the ARFF database
	 * @return the populated statistics panel
	 */
	private JPanel createStatsPanel(ARFFDatabase db) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Summary Statistics"));

		if (db == null || db.size() == 0) {
			panel.add(new JLabel("No data available"));
			return panel;
		}

		int numRecords = db.size();
		int numAttributes = db.getAttributeNames().size();
		int totalCells = numRecords * numAttributes;

		// Count missing values (represented as "?" in ARFF)
		int missingCount = 0;
		for (List<String> record : db.getRecords()) {
			for (String value : record) {
				if ("?".equals(value)) {
					missingCount++;
				}
			}
		}

		// Calculate distinct values per attribute
		List<Integer> distinctCounts = new ArrayList<Integer>();
		for (int i = 0; i < numAttributes; i++) {
			Set<String> distinctValues = new HashSet<String>();
			for (List<String> record : db.getRecords()) {
				if (i < record.size()) {
					distinctValues.add(record.get(i));
				}
			}
			distinctCounts.add(distinctValues.size());
		}

		// Calculate min/max/avg of distinct value counts
		int minDistinct = Integer.MAX_VALUE;
		int maxDistinct = 0;
		int totalDistinct = 0;
		for (int count : distinctCounts) {
			totalDistinct += count;
			if (count < minDistinct) {
				minDistinct = count;
			}
			if (count > maxDistinct) {
				maxDistinct = count;
			}
		}

		double avgDistinct = numAttributes > 0 ? (double) totalDistinct / numAttributes : 0;

		// Calculate median
		List<Integer> sortedDistinctCounts = new ArrayList<Integer>(distinctCounts);
		Collections.sort(sortedDistinctCounts);
		double medianDistinct;
		int size = sortedDistinctCounts.size();
		if (size == 0) {
			medianDistinct = 0;
		} else if (size % 2 == 0) {
			medianDistinct = (sortedDistinctCounts.get(size / 2 - 1) + sortedDistinctCounts.get(size / 2)) / 2.0;
		} else {
			medianDistinct = sortedDistinctCounts.get(size / 2);
		}

		// Calculate standard deviation
		double sumSquaredDiff = 0;
		for (int count : distinctCounts) {
			sumSquaredDiff += Math.pow(count - avgDistinct, 2);
		}
		double stdDev = numAttributes > 0 ? Math.sqrt(sumSquaredDiff / numAttributes) : 0;

		// === Database Size Section ===
		panel.add(createSectionHeader("Database Size"));
		panel.add(createStatRow("Records", String.valueOf(numRecords)));
		panel.add(createStatRow("Attributes", String.valueOf(numAttributes)));
		panel.add(createStatRow("Total cells", String.valueOf(totalCells)));
		panel.add(createStatRow("Missing values", String.valueOf(missingCount)));
		if (totalCells > 0) {
			double missingPct = (missingCount * 100.0) / totalCells;
			panel.add(createStatRow("Missing %", String.format("%.2f%%", missingPct)));
		}

		panel.add(createVerticalStrut(12));

		// === Attribute Value Diversity Section ===
		panel.add(createSectionHeader("Attribute Value Diversity"));
		if (numAttributes > 0) {
			panel.add(createStatRow("Min distinct values", String.valueOf(minDistinct)));
			panel.add(createStatRow("Max distinct values", String.valueOf(maxDistinct)));
			panel.add(createStatRow("Avg distinct values", String.format("%.2f", avgDistinct)));
			panel.add(createStatRow("Median distinct values", String.format("%.1f", medianDistinct)));
			panel.add(createStatRow("Std. deviation", String.format("%.2f", stdDev)));
		} else {
			panel.add(new JLabel("  No attributes available"));
		}

		return panel;
	}

	/**
	 * Create a section header label with bold formatting
	 * 
	 * @param title the section title
	 * @return a JLabel with the section header
	 */
	private JLabel createSectionHeader(String title) {
		JLabel label = new JLabel(title);
		label.setFont(label.getFont().deriveFont(Font.BOLD));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
		return label;
	}

	/**
	 * Create a row displaying a statistic with name and value
	 * 
	 * @param name  the statistic name
	 * @param value the statistic value
	 * @return a JPanel containing the formatted row
	 */
	private JPanel createStatRow(String name, String value) {
		JPanel row = new JPanel();
		row.setLayout(new BorderLayout());
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel nameLabel = new JLabel("  " + name + ":");
		JLabel valueLabel = new JLabel(value + "  ");

		row.add(nameLabel, BorderLayout.WEST);
		row.add(valueLabel, BorderLayout.EAST);

		return row;
	}

	/**
	 * Create a vertical strut (empty space) for spacing in panels
	 * 
	 * @param height the height of the strut in pixels
	 * @return a Component representing the vertical strut
	 */
	private Component createVerticalStrut(int height) {
		JPanel strut = new JPanel();
		strut.setPreferredSize(new Dimension(0, height));
		strut.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		strut.setOpaque(false);
		return strut;
	}

	/**
	 * Create and populate the top-K attributes panel showing attribute diversity
	 * (number of distinct values per attribute, sorted)
	 * 
	 * @param db the ARFF database
	 * @return the populated top-K panel
	 */
	private JPanel createTopKPanel(ARFFDatabase db) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Attribute Diversity"));

		if (db == null || db.size() == 0) {
			panel.add(new JLabel("No data available"));
			return panel;
		}

		int numAttributes = db.getAttributeNames().size();
		int numRecords = db.size();

		// Calculate distinct values per attribute using a LinkedHashMap to preserve order
		Map<String, Integer> attributeDistinctMap = new LinkedHashMap<String, Integer>();
		for (int i = 0; i < numAttributes; i++) {
			Set<String> distinctValues = new HashSet<String>();
			for (List<String> record : db.getRecords()) {
				if (i < record.size()) {
					distinctValues.add(record.get(i));
				}
			}
			attributeDistinctMap.put(db.getAttributeNames().get(i), distinctValues.size());
		}

		// Sort attributes by distinct value count (descending)
		List<Map.Entry<String, Integer>> sortedAttributes = new ArrayList<Map.Entry<String, Integer>>(
				attributeDistinctMap.entrySet());
		Collections.sort(sortedAttributes, new Comparator<Map.Entry<String, Integer>>() {
			@Override
			public int compare(Map.Entry<String, Integer> e1, Map.Entry<String, Integer> e2) {
				return e2.getValue().compareTo(e1.getValue());
			}
		});

		// === Top-K Most Diverse Attributes Section ===
		int topCount = Math.min(TOP_K_COUNT, sortedAttributes.size());
		panel.add(createSectionHeader("Top " + topCount + " Most Diverse"));
		panel.add(createVerticalStrut(4));

		// Create header row for top attributes
		panel.add(createAttributeTableHeader());
		panel.add(createVerticalStrut(2));

		// Display top-K attributes
		int count = 0;
		for (Map.Entry<String, Integer> entry : sortedAttributes) {
			if (count >= TOP_K_COUNT) {
				break;
			}

			String attrName = entry.getKey();
			int distinctCount = entry.getValue();
			double pct = (distinctCount * 100.0) / numRecords;

			// Create row for attribute
			panel.add(createAttributeRow(attrName, distinctCount, pct));

			count++;
		}

		panel.add(createVerticalStrut(16));

		// === Bottom-K Least Diverse Attributes Section ===
		int bottomCount = Math.min(BOTTOM_K_COUNT, sortedAttributes.size());
		panel.add(createSectionHeader("Top " + bottomCount + " Least Diverse"));
		panel.add(createVerticalStrut(4));

		// Create header row for bottom attributes
		panel.add(createAttributeTableHeader());
		panel.add(createVerticalStrut(2));

		// Display bottom-K attributes (least diverse)
		for (int i = sortedAttributes.size() - bottomCount; i < sortedAttributes.size(); i++) {
			Map.Entry<String, Integer> entry = sortedAttributes.get(i);
			String attrName = entry.getKey();
			int distinctCount = entry.getValue();
			double pct = (distinctCount * 100.0) / numRecords;

			// Create row for attribute
			panel.add(createAttributeRow(attrName, distinctCount, pct));
		}

		return panel;
	}

	/**
	 * Create a header row for the attribute diversity table
	 * 
	 * @return a JPanel containing the header row
	 */
	private JPanel createAttributeTableHeader() {
		JPanel headerRow = new JPanel();
		headerRow.setLayout(new BorderLayout());
		headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel nameHeader = new JLabel("  Attribute");
		nameHeader.setFont(nameHeader.getFont().deriveFont(Font.ITALIC));

		JPanel rightHeaderPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		rightHeaderPanel.setOpaque(false);

		JLabel countHeader = new JLabel("Distinct");
		countHeader.setFont(countHeader.getFont().deriveFont(Font.ITALIC));
		countHeader.setPreferredSize(new Dimension(50, 16));

		JLabel pctHeader = new JLabel("Div%");
		pctHeader.setFont(pctHeader.getFont().deriveFont(Font.ITALIC));
		pctHeader.setPreferredSize(new Dimension(50, 16));

		rightHeaderPanel.add(countHeader);
		rightHeaderPanel.add(pctHeader);

		headerRow.add(nameHeader, BorderLayout.WEST);
		headerRow.add(rightHeaderPanel, BorderLayout.EAST);

		return headerRow;
	}

	/**
	 * Create a row displaying an attribute with its distinct value count and
	 * diversity percentage
	 * 
	 * @param displayName   the display name of the attribute
	 * @param distinctCount the number of distinct values for the attribute
	 * @param pct           the diversity percentage (distinct values / total records
	 *                      * 100)
	 * @return a JPanel containing the formatted row
	 */
	private JPanel createAttributeRow(String displayName, int distinctCount, double pct) {
		JPanel row = new JPanel();
		row.setLayout(new BorderLayout());
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Truncate long names
		String truncatedName = displayName;
		if (truncatedName.length() > 18) {
			truncatedName = truncatedName.substring(0, 15) + "...";
		}

		JLabel attrNameLabel = new JLabel("  " + truncatedName);
		attrNameLabel.setToolTipText(displayName); // Show full name on hover

		// Create right-aligned panel for count and percentage
		JPanel rightItemPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		rightItemPanel.setOpaque(false);

		JLabel countLabel = new JLabel(String.valueOf(distinctCount));
		countLabel.setPreferredSize(new Dimension(50, 16));
		countLabel.setHorizontalAlignment(JLabel.RIGHT);

		JLabel pctLabel = new JLabel(String.format("%.1f%%", pct));
		pctLabel.setPreferredSize(new Dimension(50, 16));
		pctLabel.setHorizontalAlignment(JLabel.RIGHT);

		rightItemPanel.add(countLabel);
		rightItemPanel.add(pctLabel);

		row.add(attrNameLabel, BorderLayout.WEST);
		row.add(rightItemPanel, BorderLayout.EAST);

		return row;
	}

	/**
	 * Ask the user to choose a file.
	 */
	private void chooseAFile() {
		File path;
		// Get the last path used by the user, if there is one
		String previousPath = PreferencesManager.getInstance().getInputFilePath();
		if (previousPath == null) {
			// If there is no previous path (first time user),
			// show the files in the "examples" package of
			// the spmf distribution.
			URL main = MainTestApriori_SaveToFile.class.getResource("MainTestApriori_saveToFile.class");
			if (!"file".equalsIgnoreCase(main.getProtocol())) {
				path = null;
			} else {
				path = new File(main.getPath());
			}
		} else {
			// Otherwise, the user used SPMF before, so
			// we show the last path that he used.
			path = new File(previousPath);
		}
		// Create a JFileChooser object to select a file
		JFileChooser fc = new JFileChooser(path);
		// Set the file filter to accept ARFF files
		fc.setFileFilter(new FileNameExtensionFilter("ARFF Files", "arff"));

		// Show the file chooser dialog and get the result
		int result = fc.showOpenDialog(frame);
		// If the user approved the file selection
		if (result == JFileChooser.APPROVE_OPTION) {
			// Get the selected file
			File file = fc.getSelectedFile();
			// Get the file path
			String filepath = file.getPath();
			openDatabaseFile(filepath);
		}
		// remember this folder for next time.
		if (fc.getSelectedFile() != null) {
			PreferencesManager.getInstance().setInputFilePath(fc.getSelectedFile().getParent());
		}
	}

	/**
	 * Open a database in the viewer
	 * 
	 * @param filepath the file path
	 */
	private void openDatabaseFile(String filepath) {
		// Create a new ARFFDatabase object
		ARFFDatabase db = new ARFFDatabase();
		try {
			// Load the file containing the ARFF database
			db.loadFile(filepath);
		} catch (Exception ex) {
			// Get the exception message and the stack trace as a string
			String errorMessage = String.format("Error loading file. Reading error: %s%n", ex.getMessage());
			// Get the exception class name as a string
			String title = ex.getClass().getName();
			// Show a JDialog with the exception message
			JOptionPane.showMessageDialog(frame, errorMessage, title, JOptionPane.ERROR_MESSAGE);
		}

		// Store the current database
		currentDatabase = db;

		// Create a new ARFFTableModel object with the new database
		ARFFTableModel model = new ARFFTableModel(db);
		// Set the table model to the new model
		table.setModel(model);

		// Get the file object of the database
		File file = new File(filepath);
		// Get the file size in bytes
		long fileSize = file.length();
		// Convert the file size to megabytes
		double fileSizeMB = fileSize / (1024.0 * 1024.0);
		// Format the file size to two decimal places
		String fileSizeMBString = String.format("%.2f", fileSizeMB);

		// Update the status label to show the file size only
		statusLabel.setText("File size: " + fileSizeMBString + " MB");

		// Update the name label to show the new file name of the database
		nameLabel.setText("Database: " + filepath);

		// Update the statistics panel
		rightPanel.remove(statsPanel);
		statsPanel = createStatsPanel(db);
		rightPanel.add(statsPanel, 0);

		// Update the top-K panel
		rightPanel.remove(topKPanel);
		topKPanel = createTopKPanel(db);
		rightPanel.add(topKPanel, 1);

		// Refresh the right panel
		rightPanel.revalidate();
		rightPanel.repaint();
	}

	/**
	 * View the distribution of distinct value counts per attribute using a
	 * histogram window
	 */
	private void viewAttributeDistinctValueDistribution() {
		if (table.getModel() != null && currentDatabase != null) {
			ARFFDatabase db = currentDatabase;

			int numAttributes = db.getAttributeNames().size();

			// Prepare the data (distinct value counts per attribute)
			int[] yValues = new int[numAttributes];
			for (int i = 0; i < numAttributes; i++) {
				Set<String> distinctValues = new HashSet<String>();
				for (List<String> record : db.getRecords()) {
					if (i < record.size()) {
						distinctValues.add(record.get(i));
					}
				}
				yValues[i] = distinctValues.size();
			}

			// Prepare the X values data
			int[] xValues = new int[numAttributes];
			for (int i = 0; i < numAttributes; i++) {
				xValues[i] = i;
			}

			// Create a map from attribute index to attribute name
			Map<Integer, String> mapIndexToName = new HashMap<Integer, String>();
			for (int i = 0; i < numAttributes; i++) {
				mapIndexToName.put(i, db.getAttributeNames().get(i));
			}

			new HistogramDistributionWindow(false, yValues, xValues,
					"Attribute distinct value count distribution", true, true, "Attribute", "Distinct Values",
					mapIndexToName, Order.ASCENDING_Y);
		}
	}

	/**
	 * The table model to display the ARFF records
	 */
	class ARFFTableModel implements TableModel {

		/** The ARFFDatabase object that holds the data **/
		ARFFDatabase db;

		/** The list of listeners for this table model */
		private List<TableModelListener> listeners;

		/**
		 * The constructor that takes an ARFFDatabase object as parameter
		 * 
		 * @param db an ARFF database object
		 */
		public ARFFTableModel(ARFFDatabase db) {
			this.db = db;
			this.listeners = new ArrayList<TableModelListener>();
		}

		/**
		 * Get the number of rows in the table
		 * 
		 * @return number of rows
		 */
		public int getRowCount() {
			return db.size();
		}

		/**
		 * Get the number of columns in the table
		 * 
		 * @return number of columns
		 */
		public int getColumnCount() {
			return db.getAttributeNames().size() + 1;
		}

		/**
		 * Get the name of the column at the given index
		 * 
		 * @param columnIndex the column index
		 * @return the name
		 */
		public String getColumnName(int columnIndex) {
			if (columnIndex == 0) {
				return "Row";
			}
			return db.getAttributeNames().get(columnIndex - 1);
		}

		/**
		 * Get the class of the values in the column at the given index.
		 * 
		 * @param columnIndex the index
		 * @return the class
		 */
		public Class<?> getColumnClass(int columnIndex) {
			if (columnIndex == 0) {
				return Integer.class;
			}
			return String.class;
		}

		/**
		 * Get the value at the given row and column index
		 * 
		 * @param rowIndex    the row
		 * @param columnIndex the column
		 * @return the object
		 */
		public Object getValueAt(int rowIndex, int columnIndex) {
			if (columnIndex == 0) {
				return rowIndex;
			}
			List<String> record = db.getRecords().get(rowIndex);
			int attrIndex = columnIndex - 1;
			if (attrIndex < record.size()) {
				return record.get(attrIndex);
			}
			return "";
		}

		/**
		 * Get whether the cell at the given row and column index is editable
		 * 
		 * @param rowIndex    the row
		 * @param columnIndex the column
		 * @return a boolean indicating if the cell is editable
		 */
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return false;
		}

		/**
		 * Set the value at the given row and column index
		 * 
		 * @param rowIndex    the row
		 * @param columnIndex the column
		 * @param aValue      the object
		 */
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			// Do nothing, since the table is not editable
		}

		/**
		 * Add a listener to this table model
		 * 
		 * @param l a TableModelListener object
		 */
		public void addTableModelListener(TableModelListener l) {
			listeners.add(l);
		}

		/**
		 * Remove a listener from this table model
		 * 
		 * @param l a TableModelListener object
		 */
		public void removeTableModelListener(TableModelListener l) {
			listeners.remove(l);
		}
	}
}