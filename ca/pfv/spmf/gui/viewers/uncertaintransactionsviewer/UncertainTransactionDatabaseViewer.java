package ca.pfv.spmf.gui.viewers.uncertaintransactionsviewer;

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
import java.util.List;
import java.util.Map;

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
import javax.swing.filechooser.FileNameExtensionFilter;

import ca.pfv.spmf.algorithms.frequentpatterns.uapriori.ItemUApriori;
import ca.pfv.spmf.algorithms.frequentpatterns.uapriori.ItemsetUApriori;
import ca.pfv.spmf.algorithms.frequentpatterns.uapriori.UncertainTransactionDatabase;
import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.SortableJTable;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.gui.viewers.ItemCooccurrenceHeatmapGenerator;
import ca.pfv.spmf.gui.viewers.ItemCooccurrenceHeatmapGenerator.ItemSetProvider;
import ca.pfv.spmf.gui.visuals.histograms.HistogramDistributionPanel.Order;
import ca.pfv.spmf.gui.visuals.histograms.HistogramDistributionWindow;
import ca.pfv.spmf.test.MainTestApriori_simple_saveToFile;

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
 * A tool to visualize an uncertain transaction database in SPMF format.
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class UncertainTransactionDatabaseViewer {

	/** The table */
	private JTable table;
	/** The scroll pane */
	private JScrollPane scrollPane;
	/** The status label */
	private JLabel statusLabel;
	/** The name label */
	private JLabel nameLabel;
	/** The frame */
	private JFrame frame;
	/** The current database */
	private UncertainTransactionDatabase currentDatabase;
	/** The statistics panel */
	private JPanel statsPanel;
	/** The top-K items panel */
	private JPanel topKPanel;
	/** The right side panel containing stats and top-K */
	private JPanel rightPanel;
	/** The scroll pane wrapping the right panel */
	private JScrollPane rightScrollPane;
	/** The split pane separating table and right panel */
	private JSplitPane splitPane;
	/** The number of top-K items to display */
	private static final int TOP_K_COUNT = 5;
	/** The number of bottom-K items to display */
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
	public UncertainTransactionDatabaseViewer(boolean runAsStandaloneProgram, String filePath) {
		this.runAsStandalone = runAsStandaloneProgram;
		this.rightPanelVisible = true;

		frame = new JFrame("SPMF Uncertain Transaction Database Viewer " + Main.SPMF_VERSION);
		if (runAsStandaloneProgram) {
			// Set the default close operation
			frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}

		// Create the menu bar
		JMenuBar menuBar = createMenuBar();
		frame.setJMenuBar(menuBar);

		// Create the table
		table = new SortableJTable();
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

		scrollPane = new JScrollPane(table);
		// Set the horizontal scroll bar policy of the scroll pane to always show
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

		// Create the right side panel for statistics and top-K items
		rightPanel = new JPanel();
		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
		rightPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		// Create the statistics panel
		statsPanel = createEmptyStatsPanel();
		rightPanel.add(statsPanel);

		// Create the top-K items panel
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
		// Set the text of the label to show the file name of the database
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
		// Make the frame visible
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

		// View transaction length distribution
		JMenuItem transLengthItem = new JMenuItem("View Transaction Length Distribution");
		transLengthItem.setMnemonic(KeyEvent.VK_T);
		transLengthItem
				.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/histogram.png")));
		transLengthItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				viewTransactionLengthDistribution();
			}
		});
		toolsMenu.add(transLengthItem);

		// View item frequency distribution
		JMenuItem itemFreqItem = new JMenuItem("View Item Frequency Distribution");
		itemFreqItem.setMnemonic(KeyEvent.VK_I);
		itemFreqItem.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/histogram.png")));
		itemFreqItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				viewItemFrequencyDistribution();
			}
		});
		toolsMenu.add(itemFreqItem);

		toolsMenu.addSeparator();

		// View item co-occurrence heatmap
		JMenuItem cooccurrenceHeatmapItem = new JMenuItem("View Item Co-occurrence Heatmap");
		cooccurrenceHeatmapItem.setMnemonic(KeyEvent.VK_H);
		cooccurrenceHeatmapItem
				.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/heatmap20.png")));
		cooccurrenceHeatmapItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				viewItemCooccurrenceHeatmap();
			}
		});
		toolsMenu.add(cooccurrenceHeatmapItem);

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
	 * Create an empty top-K items panel (placeholder before database is loaded)
	 * 
	 * @return the empty top-K panel
	 */
	private JPanel createEmptyTopKPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Item Frequency"));
		panel.add(new JLabel("Load a database to view item frequency"));
		return panel;
	}

	/**
	 * Create and populate the statistics panel with database statistics
	 * 
	 * @param db the uncertain transaction database
	 * @return the populated statistics panel
	 */
	private JPanel createStatsPanel(UncertainTransactionDatabase db) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Summary Statistics"));

		if (db == null || db.size() == 0) {
			panel.add(new JLabel("No data available"));
			return panel;
		}

		// Calculate statistics
		int minLength = Integer.MAX_VALUE;
		int maxLength = 0;
		int totalLength = 0;
		List<Integer> lengths = new ArrayList<Integer>();

		for (ItemsetUApriori transaction : db.getTransactions()) {
			int len = transaction.size();
			lengths.add(len);
			totalLength += len;
			if (len < minLength) {
				minLength = len;
			}
			if (len > maxLength) {
				maxLength = len;
			}
		}

		// Calculate average
		double avgLength = (double) totalLength / db.size();

		// Calculate median
		Collections.sort(lengths);
		double medianLength;
		int size = lengths.size();
		if (size % 2 == 0) {
			medianLength = (lengths.get(size / 2 - 1) + lengths.get(size / 2)) / 2.0;
		} else {
			medianLength = lengths.get(size / 2);
		}

		// Calculate standard deviation
		double sumSquaredDiff = 0;
		for (int len : lengths) {
			sumSquaredDiff += Math.pow(len - avgLength, 2);
		}
		double stdDev = Math.sqrt(sumSquaredDiff / db.size());

		// Count unique items
		int uniqueItems = db.getAllItems().size();

		// Calculate density
		double density = (double) totalLength / (db.size() * uniqueItems);

		// Count singleton transactions (length = 1)
		int singletonCount = 0;
		for (int len : lengths) {
			if (len == 1) {
				singletonCount++;
			}
		}

		// Count empty transactions (length = 0)
		int emptyCount = 0;
		for (int len : lengths) {
			if (len == 0) {
				emptyCount++;
			}
		}

		// === Database Size Section ===
		panel.add(createSectionHeader("Database Size"));
		panel.add(createStatRow("Transactions", String.valueOf(db.size())));
		panel.add(createStatRow("Unique items", String.valueOf(uniqueItems)));
		panel.add(createStatRow("Total item count", String.valueOf(totalLength)));
		panel.add(createStatRow("Density", String.format("%.4f", density)));

		panel.add(createVerticalStrut(12));

		// === Transaction Length Section ===
		panel.add(createSectionHeader("Transaction Length"));
		panel.add(createStatRow("Minimum", String.valueOf(minLength)));
		panel.add(createStatRow("Maximum", String.valueOf(maxLength)));
		panel.add(createStatRow("Average", String.format("%.2f", avgLength)));
		panel.add(createStatRow("Median", String.format("%.1f", medianLength)));
		panel.add(createStatRow("Std. deviation", String.format("%.2f", stdDev)));

		panel.add(createVerticalStrut(12));

		// === Special Transactions Section ===
		panel.add(createSectionHeader("Special Transactions"));
		panel.add(createStatRow("Singletons (length=1)", String.valueOf(singletonCount)));
		if (emptyCount > 0) {
			panel.add(createStatRow("Empty (length=0)", String.valueOf(emptyCount)));
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
	 * Create and populate the top-K frequent items panel
	 * 
	 * @param db the uncertain transaction database
	 * @return the populated top-K panel
	 */
	private JPanel createTopKPanel(UncertainTransactionDatabase db) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Item Frequency"));

		if (db == null || db.size() == 0) {
			panel.add(new JLabel("No data available"));
			return panel;
		}

		// Calculate item frequencies
		Map<Integer, Integer> itemFrequencies = new HashMap<Integer, Integer>();
		for (ItemsetUApriori transaction : db.getTransactions()) {
			for (ItemUApriori item : transaction.getItems()) {
				Integer count = itemFrequencies.get(item);
				if (count == null) {
					itemFrequencies.put(item.getId(), 1);
				} else {
					itemFrequencies.put(item.getId(), count + 1);
				}
			}
		}

		// Sort items by frequency (descending)
		List<Map.Entry<Integer, Integer>> sortedItems = new ArrayList<Map.Entry<Integer, Integer>>(
				itemFrequencies.entrySet());
		Collections.sort(sortedItems, new Comparator<Map.Entry<Integer, Integer>>() {
			@Override
			public int compare(Map.Entry<Integer, Integer> e1, Map.Entry<Integer, Integer> e2) {
				return e2.getValue().compareTo(e1.getValue());
			}
		});

		// === Top-K Most Frequent Items Section ===
		panel.add(createSectionHeader("Top " + TOP_K_COUNT + " Most Frequent"));
		panel.add(createVerticalStrut(4));

		// Create header row for top items
		panel.add(createItemTableHeader());
		panel.add(createVerticalStrut(2));

		// Display top-K items
		int count = 0;
		for (Map.Entry<Integer, Integer> entry : sortedItems) {
			if (count >= TOP_K_COUNT) {
				break;
			}

			int itemId = entry.getKey();
			int frequency = entry.getValue();
			double support = (frequency * 100.0) / db.size();

			String displayName = "Item " + itemId;

			// Create row for item
			panel.add(createItemRow(displayName, frequency, support));

			count++;
		}

		panel.add(createVerticalStrut(16));

		// === Bottom-K Least Frequent Items Section ===
		int bottomCount = Math.min(BOTTOM_K_COUNT, sortedItems.size());
		panel.add(createSectionHeader("Top " + bottomCount + " Least Frequent"));
		panel.add(createVerticalStrut(4));

		// Create header row for bottom items
		panel.add(createItemTableHeader());
		panel.add(createVerticalStrut(2));

		// Display bottom-K items (least frequent)
		for (int i = sortedItems.size() - bottomCount; i < sortedItems.size(); i++) {
			Map.Entry<Integer, Integer> entry = sortedItems.get(i);
			int itemId = entry.getKey();
			int frequency = entry.getValue();
			double support = (frequency * 100.0) / db.size();

			String displayName = "Item " + itemId;

			// Create row for item
			panel.add(createItemRow(displayName, frequency, support));
		}

		return panel;
	}

	/**
	 * Create a header row for the item frequency table
	 * 
	 * @return a JPanel containing the header row
	 */
	private JPanel createItemTableHeader() {
		JPanel headerRow = new JPanel();
		headerRow.setLayout(new BorderLayout());
		headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel nameHeader = new JLabel("  Item");
		nameHeader.setFont(nameHeader.getFont().deriveFont(Font.ITALIC));

		JPanel rightHeaderPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		rightHeaderPanel.setOpaque(false);

		JLabel countHeader = new JLabel("Count");
		countHeader.setFont(countHeader.getFont().deriveFont(Font.ITALIC));
		countHeader.setPreferredSize(new Dimension(45, 16));

		JLabel supportHeader = new JLabel("Sup%");
		supportHeader.setFont(supportHeader.getFont().deriveFont(Font.ITALIC));
		supportHeader.setPreferredSize(new Dimension(50, 16));

		rightHeaderPanel.add(countHeader);
		rightHeaderPanel.add(supportHeader);

		headerRow.add(nameHeader, BorderLayout.WEST);
		headerRow.add(rightHeaderPanel, BorderLayout.EAST);

		return headerRow;
	}

	/**
	 * Create a row displaying an item with its frequency and support
	 * 
	 * @param displayName the display name of the item
	 * @param frequency   the frequency count of the item
	 * @param support     the support percentage of the item
	 * @return a JPanel containing the formatted row
	 */
	private JPanel createItemRow(String displayName, int frequency, double support) {
		JPanel row = new JPanel();
		row.setLayout(new BorderLayout());
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Truncate long names
		String truncatedName = displayName;
		if (truncatedName.length() > 18) {
			truncatedName = truncatedName.substring(0, 15) + "...";
		}

		JLabel itemNameLabel = new JLabel("  " + truncatedName);
		itemNameLabel.setToolTipText(displayName); // Show full name on hover

		// Create right-aligned panel for count and support
		JPanel rightItemPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		rightItemPanel.setOpaque(false);

		JLabel countLabel = new JLabel(String.valueOf(frequency));
		countLabel.setPreferredSize(new Dimension(45, 16));
		countLabel.setHorizontalAlignment(JLabel.RIGHT);

		JLabel supportLabel = new JLabel(String.format("%.1f%%", support));
		supportLabel.setPreferredSize(new Dimension(50, 16));
		supportLabel.setHorizontalAlignment(JLabel.RIGHT);

		rightItemPanel.add(countLabel);
		rightItemPanel.add(supportLabel);

		row.add(itemNameLabel, BorderLayout.WEST);
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
			URL main = MainTestApriori_simple_saveToFile.class.getResource("MainTestApriori_saveToFile.class");
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
		// Set the file filter to accept only text files
		fc.setFileFilter(new FileNameExtensionFilter("Text Files", "txt"));

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
		// Create a new UncertainTransactionDatabase object
		UncertainTransactionDatabase db = new UncertainTransactionDatabase();
		try {
			// Load the file containing the uncertain transaction database
			db.loadFile(filepath);
		} catch (Exception ex) {
			// Get the exception message and the stack trace as a string
			String errorMessage = String.format("Error loading file. Reading error: %s%n", ex.getMessage());
			// Get the exception class name as a string
			String title = ex.getClass().getName();
			// Show a JDialog with the exception message and the stack trace, and set the
			// dialog title and the message type
			JOptionPane.showMessageDialog(frame, errorMessage, title, JOptionPane.ERROR_MESSAGE);
		}

		// Store the current database
		currentDatabase = db;

		// Create a new TransactionTableModel object with the new database
		UncertainTransactionTableModel model = new UncertainTransactionTableModel(db);
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
	 * View the distribution of item frequency using a histogram window
	 */
	private void viewItemFrequencyDistribution() {
		if (table.getModel() != null && currentDatabase != null) {
			UncertainTransactionDatabase db = currentDatabase;

			// Find the maximum item ID
			int maxItem = 0;
			for (ItemUApriori item : db.getAllItems()) {
				if (item.getId() > maxItem) {
					maxItem = item.getId();
				}
			}

			// Prepare the data (counts)
			int[] yValues = new int[maxItem + 1];
			for (ItemsetUApriori transaction : db.getTransactions()) {
				for (ItemUApriori item : transaction.getItems()) {
					yValues[item.getId()]++;
				}
			}

			// Prepare the X values data
			int[] xValues = new int[maxItem + 1];
			for (int i = 0; i < maxItem + 1; i++) {
				xValues[i] = i;
			}
			new HistogramDistributionWindow(false, yValues, xValues, "Item frequency distribution", true, true, "Item",
					"Count", null, Order.ASCENDING_Y);
		}
	}

	/**
	 * A method to view the transaction length distribution using a histogram
	 * window.
	 */
	private void viewTransactionLengthDistribution() {
		if (table.getModel() != null && currentDatabase != null) {
			UncertainTransactionDatabase db = currentDatabase;

			// Find the maximum size
			int maxSize = 0;
			for (ItemsetUApriori transaction : db.getTransactions()) {
				int size = transaction.size();
				if (size > maxSize) {
					maxSize = size;
				}
			}

			// Prepare the data (probabilities)
			int[] yValues = new int[maxSize + 1];
			for (ItemsetUApriori transaction : db.getTransactions()) {
				int size = transaction.size();
				yValues[size] += 1;
			}

			// Prepare the X values data
			int[] xValues = new int[maxSize + 1];
			for (int i = 0; i < maxSize + 1; i++) {
				xValues[i] = i;
			}

			new HistogramDistributionWindow(false, yValues, xValues,
					"Transaction length frequency distribution", true,
					true, "Length", "Count", null, Order.ASCENDING_X);
		}
	}

	/**
	 * View the co-occurrence of the X most frequent items as a heatmap. Prompts the
	 * user for the number of top items to include.
	 */
	private void viewItemCooccurrenceHeatmap() {
		if (currentDatabase == null || currentDatabase.size() == 0) {
			JOptionPane.showMessageDialog(frame, "Please load a database first.", "No Database",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		// Create an adapter for the uncertain transaction database
		ItemSetProvider provider = new UncertainTransactionDatabaseItemSetProvider(currentDatabase);

		// Use the reusable heatmap generator
		ItemCooccurrenceHeatmapGenerator.showCooccurrenceHeatmapDialog(frame, provider, runAsStandalone);
	}

}