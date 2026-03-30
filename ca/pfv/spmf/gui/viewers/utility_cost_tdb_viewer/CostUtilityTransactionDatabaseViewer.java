package ca.pfv.spmf.gui.viewers.utility_cost_tdb_viewer;

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
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableModel;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.SortableJTable;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.gui.viewers.ItemCooccurrenceHeatmapGenerator;
import ca.pfv.spmf.gui.viewers.ItemCooccurrenceHeatmapGenerator.ItemSetProvider;
import ca.pfv.spmf.gui.visuals.histograms.HistogramDistributionPanel.Order;
import ca.pfv.spmf.gui.visuals.histograms.HistogramDistributionWindow;
import ca.pfv.spmf.input.cost_utility_transaction_database.CostUtilityTransactionDatabase;
import ca.pfv.spmf.input.cost_utility_transaction_database.ItemCost;
import ca.pfv.spmf.input.cost_utility_transaction_database.TransactionCost;
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
 * A tool to visualize a utility transaction database in SPMF format.
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class CostUtilityTransactionDatabaseViewer {

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
	private CostUtilityTransactionDatabase currentDatabase;
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
	/** Menu item for toggling display mode */
	private JCheckBoxMenuItem displayAsListsMenuItem;
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
	public CostUtilityTransactionDatabaseViewer(boolean runAsStandaloneProgram, String filePath) {
		this.runAsStandalone = runAsStandaloneProgram;
		this.rightPanelVisible = true;

		frame = new JFrame("SPMF Cost Utility Transaction Database Viewer " + Main.SPMF_VERSION);
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

		// Display as Lists toggle
		displayAsListsMenuItem = new JCheckBoxMenuItem("Display as Lists");
		displayAsListsMenuItem.setMnemonic(KeyEvent.VK_L);
		displayAsListsMenuItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				toggleDisplayMode();
			}
		});
		viewMenu.add(displayAsListsMenuItem);

		viewMenu.addSeparator();

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

		// View item cost distribution
		JMenuItem itemCostItem = new JMenuItem("View Item Cost Distribution");
		itemCostItem.setMnemonic(KeyEvent.VK_C);
		itemCostItem.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/histogram.png")));
		itemCostItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				viewItemCostDistribution();
			}
		});
		toolsMenu.add(itemCostItem);

		// View transaction utility distribution
		JMenuItem transUtilityItem = new JMenuItem("View Transaction Utility Distribution");
		transUtilityItem.setMnemonic(KeyEvent.VK_U);
		transUtilityItem.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/histogram.png")));
		transUtilityItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				viewTransactionUtilityDistribution();
			}
		});
		toolsMenu.add(transUtilityItem);

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
		panel.setBorder(BorderFactory.createTitledBorder("Item Frequency & Cost"));
		panel.add(new JLabel("Load a database to view item info"));
		return panel;
	}

	/**
	 * Create and populate the statistics panel with database statistics
	 * 
	 * @param db the cost utility transaction database
	 * @return the populated statistics panel
	 */
	private JPanel createStatsPanel(CostUtilityTransactionDatabase db) {
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
		int minUtility = Integer.MAX_VALUE;
		int maxUtility = 0;
		long totalUtility = 0;
		List<Integer> lengths = new ArrayList<Integer>();
		List<Integer> utilities = new ArrayList<Integer>();

		// Collect all individual item costs across all transactions
		List<Integer> allItemCosts = new ArrayList<Integer>();
		// Track per-item total cost and frequency for aggregated cost stats
		Map<Integer, Long> itemTotalCostMap = new HashMap<Integer, Long>();
		Map<Integer, Integer> itemFrequencyMap = new HashMap<Integer, Integer>();

		for (TransactionCost transaction : db.getTransactions()) {
			int len = transaction.size();
			int utility = transaction.getTransactionUtility();
			lengths.add(len);
			utilities.add(utility);
			totalLength += len;
			totalUtility += utility;
			if (len < minLength) {
				minLength = len;
			}
			if (len > maxLength) {
				maxLength = len;
			}
			if (utility < minUtility) {
				minUtility = utility;
			}
			if (utility > maxUtility) {
				maxUtility = utility;
			}

			// Collect item costs
			for (ItemCost itemCost : transaction.getItems()) {
				allItemCosts.add(itemCost.cost);

				Long prevCost = itemTotalCostMap.get(itemCost.item);
				if (prevCost == null) {
					itemTotalCostMap.put(itemCost.item, (long) itemCost.cost);
				} else {
					itemTotalCostMap.put(itemCost.item, prevCost + itemCost.cost);
				}

				Integer prevFreq = itemFrequencyMap.get(itemCost.item);
				if (prevFreq == null) {
					itemFrequencyMap.put(itemCost.item, 1);
				} else {
					itemFrequencyMap.put(itemCost.item, prevFreq + 1);
				}
			}
		}

		// Calculate average length
		double avgLength = (double) totalLength / db.size();

		// Calculate median length
		Collections.sort(lengths);
		double medianLength;
		int size = lengths.size();
		if (size % 2 == 0) {
			medianLength = (lengths.get(size / 2 - 1) + lengths.get(size / 2)) / 2.0;
		} else {
			medianLength = lengths.get(size / 2);
		}

		// Calculate standard deviation of length
		double sumSquaredDiff = 0;
		for (int len : lengths) {
			sumSquaredDiff += Math.pow(len - avgLength, 2);
		}
		double stdDevLength = Math.sqrt(sumSquaredDiff / db.size());

		// Calculate average utility
		double avgUtility = (double) totalUtility / db.size();

		// Calculate median utility
		Collections.sort(utilities);
		double medianUtility;
		int uSize = utilities.size();
		if (uSize % 2 == 0) {
			medianUtility = (utilities.get(uSize / 2 - 1) + utilities.get(uSize / 2)) / 2.0;
		} else {
			medianUtility = utilities.get(uSize / 2);
		}

		// Calculate standard deviation of utility
		double sumSquaredDiffUtility = 0;
		for (int u : utilities) {
			sumSquaredDiffUtility += Math.pow(u - avgUtility, 2);
		}
		double stdDevUtility = Math.sqrt(sumSquaredDiffUtility / db.size());

		// === Item Cost Statistics ===
		// Total cost across all item occurrences
		long totalCost = 0;
		int minItemCost = Integer.MAX_VALUE;
		int maxItemCost = 0;
		for (int c : allItemCosts) {
			totalCost += c;
			if (c < minItemCost) {
				minItemCost = c;
			}
			if (c > maxItemCost) {
				maxItemCost = c;
			}
		}

		// Average item cost (per item occurrence)
		double avgItemCost = allItemCosts.isEmpty() ? 0 : (double) totalCost / allItemCosts.size();

		// Median item cost
		Collections.sort(allItemCosts);
		double medianItemCost;
		if (allItemCosts.isEmpty()) {
			medianItemCost = 0;
		} else {
			int cSize = allItemCosts.size();
			if (cSize % 2 == 0) {
				medianItemCost = (allItemCosts.get(cSize / 2 - 1) + allItemCosts.get(cSize / 2)) / 2.0;
			} else {
				medianItemCost = allItemCosts.get(cSize / 2);
			}
		}

		// Standard deviation of item cost
		double sumSquaredDiffCost = 0;
		for (int c : allItemCosts) {
			sumSquaredDiffCost += Math.pow(c - avgItemCost, 2);
		}
		double stdDevItemCost = allItemCosts.isEmpty() ? 0 : Math.sqrt(sumSquaredDiffCost / allItemCosts.size());

		// Per-transaction cost statistics
		List<Long> transactionCosts = new ArrayList<Long>();
		for (TransactionCost transaction : db.getTransactions()) {
			long transCost = 0;
			for (ItemCost itemCost : transaction.getItems()) {
				transCost += itemCost.cost;
			}
			transactionCosts.add(transCost);
		}

		long minTransCost = Long.MAX_VALUE;
		long maxTransCost = 0;
		long totalTransCost = 0;
		for (long tc : transactionCosts) {
			totalTransCost += tc;
			if (tc < minTransCost) {
				minTransCost = tc;
			}
			if (tc > maxTransCost) {
				maxTransCost = tc;
			}
		}
		double avgTransCost = transactionCosts.isEmpty() ? 0 : (double) totalTransCost / transactionCosts.size();

		Collections.sort(transactionCosts);
		double medianTransCost;
		if (transactionCosts.isEmpty()) {
			medianTransCost = 0;
		} else {
			int tcSize = transactionCosts.size();
			if (tcSize % 2 == 0) {
				medianTransCost = (transactionCosts.get(tcSize / 2 - 1) + transactionCosts.get(tcSize / 2)) / 2.0;
			} else {
				medianTransCost = transactionCosts.get(tcSize / 2);
			}
		}

		double sumSquaredDiffTransCost = 0;
		for (long tc : transactionCosts) {
			sumSquaredDiffTransCost += Math.pow(tc - avgTransCost, 2);
		}
		double stdDevTransCost = transactionCosts.isEmpty() ? 0
				: Math.sqrt(sumSquaredDiffTransCost / transactionCosts.size());

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
		panel.add(createStatRow("Total item occurrences", String.valueOf(totalLength)));
		panel.add(createStatRow("Density", String.format("%.4f", density)));

		panel.add(createVerticalStrut(12));

		// === Transaction Length Section ===
		panel.add(createSectionHeader("Transaction Length"));
		panel.add(createStatRow("Minimum", String.valueOf(minLength)));
		panel.add(createStatRow("Maximum", String.valueOf(maxLength)));
		panel.add(createStatRow("Average", String.format("%.2f", avgLength)));
		panel.add(createStatRow("Median", String.format("%.1f", medianLength)));
		panel.add(createStatRow("Std. deviation", String.format("%.2f", stdDevLength)));

		panel.add(createVerticalStrut(12));

		// === Transaction Utility Section ===
		panel.add(createSectionHeader("Transaction Utility"));
		panel.add(createStatRow("Total utility", String.valueOf(totalUtility)));
		panel.add(createStatRow("Minimum", String.valueOf(minUtility)));
		panel.add(createStatRow("Maximum", String.valueOf(maxUtility)));
		panel.add(createStatRow("Average", String.format("%.2f", avgUtility)));
		panel.add(createStatRow("Median", String.format("%.1f", medianUtility)));
		panel.add(createStatRow("Std. deviation", String.format("%.2f", stdDevUtility)));

		panel.add(createVerticalStrut(12));

		// === Item Cost Section (per item occurrence) ===
		panel.add(createSectionHeader("Item Cost (per occurrence)"));
		panel.add(createStatRow("Total cost", String.valueOf(totalCost)));
		if (!allItemCosts.isEmpty()) {
			panel.add(createStatRow("Minimum", String.valueOf(minItemCost)));
			panel.add(createStatRow("Maximum", String.valueOf(maxItemCost)));
			panel.add(createStatRow("Average", String.format("%.2f", avgItemCost)));
			panel.add(createStatRow("Median", String.format("%.1f", medianItemCost)));
			panel.add(createStatRow("Std. deviation", String.format("%.2f", stdDevItemCost)));
		}

		panel.add(createVerticalStrut(12));

		// === Transaction Cost Section (sum of item costs per transaction) ===
		panel.add(createSectionHeader("Transaction Cost (sum per trans.)"));
		panel.add(createStatRow("Total", String.valueOf(totalTransCost)));
		if (!transactionCosts.isEmpty()) {
			panel.add(createStatRow("Minimum", String.valueOf(minTransCost)));
			panel.add(createStatRow("Maximum", String.valueOf(maxTransCost)));
			panel.add(createStatRow("Average", String.format("%.2f", avgTransCost)));
			panel.add(createStatRow("Median", String.format("%.1f", medianTransCost)));
			panel.add(createStatRow("Std. deviation", String.format("%.2f", stdDevTransCost)));
		}

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
	 * Create and populate the top-K frequent items panel, including cost info
	 * 
	 * @param db the cost utility transaction database
	 * @return the populated top-K panel
	 */
	private JPanel createTopKPanel(CostUtilityTransactionDatabase db) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Item Frequency & Cost"));

		if (db == null || db.size() == 0) {
			panel.add(new JLabel("No data available"));
			return panel;
		}

		// Calculate item frequencies and total costs
		Map<Integer, Integer> itemFrequencies = new HashMap<Integer, Integer>();
		Map<Integer, Long> itemTotalCosts = new HashMap<Integer, Long>();
		for (TransactionCost transaction : db.getTransactions()) {
			for (ItemCost itemCost : transaction.getItems()) {
				Integer count = itemFrequencies.get(itemCost.item);
				if (count == null) {
					itemFrequencies.put(itemCost.item, 1);
				} else {
					itemFrequencies.put(itemCost.item, count + 1);
				}

				Long prevCost = itemTotalCosts.get(itemCost.item);
				if (prevCost == null) {
					itemTotalCosts.put(itemCost.item, (long) itemCost.cost);
				} else {
					itemTotalCosts.put(itemCost.item, prevCost + itemCost.cost);
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
			long totalItemCost = itemTotalCosts.containsKey(itemId) ? itemTotalCosts.get(itemId) : 0;

			// Get item name if available
			String itemName = db.getNameForItem(itemId);
			String displayName;
			if (itemName != null) {
				displayName = itemName + " (" + itemId + ")";
			} else {
				displayName = "Item " + itemId;
			}

			// Create row for item
			panel.add(createItemRow(displayName, frequency, support, totalItemCost));

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
			long totalItemCost = itemTotalCosts.containsKey(itemId) ? itemTotalCosts.get(itemId) : 0;

			// Get item name if available
			String itemName = db.getNameForItem(itemId);
			String displayName;
			if (itemName != null) {
				displayName = itemName + " (" + itemId + ")";
			} else {
				displayName = "Item " + itemId;
			}

			// Create row for item
			panel.add(createItemRow(displayName, frequency, support, totalItemCost));
		}

		panel.add(createVerticalStrut(16));

		// === Top-K Highest Total Cost Items Section ===
		List<Map.Entry<Integer, Long>> sortedByCost = new ArrayList<Map.Entry<Integer, Long>>(
				itemTotalCosts.entrySet());
		Collections.sort(sortedByCost, new Comparator<Map.Entry<Integer, Long>>() {
			@Override
			public int compare(Map.Entry<Integer, Long> e1, Map.Entry<Integer, Long> e2) {
				return e2.getValue().compareTo(e1.getValue());
			}
		});

		int topCostCount = Math.min(TOP_K_COUNT, sortedByCost.size());
		panel.add(createSectionHeader("Top " + topCostCount + " Highest Total Cost"));
		panel.add(createVerticalStrut(4));
		panel.add(createItemTableHeader());
		panel.add(createVerticalStrut(2));

		count = 0;
		for (Map.Entry<Integer, Long> entry : sortedByCost) {
			if (count >= TOP_K_COUNT) {
				break;
			}

			int itemId = entry.getKey();
			long totalItemCost = entry.getValue();
			int frequency = itemFrequencies.containsKey(itemId) ? itemFrequencies.get(itemId) : 0;
			double support = (frequency * 100.0) / db.size();

			// Get item name if available
			String itemName = db.getNameForItem(itemId);
			String displayName;
			if (itemName != null) {
				displayName = itemName + " (" + itemId + ")";
			} else {
				displayName = "Item " + itemId;
			}

			panel.add(createItemRow(displayName, frequency, support, totalItemCost));
			count++;
		}

		panel.add(createVerticalStrut(16));

		// === Top-K Lowest Total Cost Items Section ===
		int bottomCostCount = Math.min(BOTTOM_K_COUNT, sortedByCost.size());
		panel.add(createSectionHeader("Top " + bottomCostCount + " Lowest Total Cost"));
		panel.add(createVerticalStrut(4));
		panel.add(createItemTableHeader());
		panel.add(createVerticalStrut(2));

		for (int i = sortedByCost.size() - bottomCostCount; i < sortedByCost.size(); i++) {
			Map.Entry<Integer, Long> entry = sortedByCost.get(i);
			int itemId = entry.getKey();
			long totalItemCost = entry.getValue();
			int frequency = itemFrequencies.containsKey(itemId) ? itemFrequencies.get(itemId) : 0;
			double support = (frequency * 100.0) / db.size();

			// Get item name if available
			String itemName = db.getNameForItem(itemId);
			String displayName;
			if (itemName != null) {
				displayName = itemName + " (" + itemId + ")";
			} else {
				displayName = "Item " + itemId;
			}

			panel.add(createItemRow(displayName, frequency, support, totalItemCost));
		}

		return panel;
	}

	/**
	 * Create a header row for the item frequency/cost table
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

		JLabel costHeader = new JLabel("Cost");
		costHeader.setFont(costHeader.getFont().deriveFont(Font.ITALIC));
		costHeader.setPreferredSize(new Dimension(55, 16));

		rightHeaderPanel.add(countHeader);
		rightHeaderPanel.add(supportHeader);
		rightHeaderPanel.add(costHeader);

		headerRow.add(nameHeader, BorderLayout.WEST);
		headerRow.add(rightHeaderPanel, BorderLayout.EAST);

		return headerRow;
	}

	/**
	 * Create a row displaying an item with its frequency, support, and total cost
	 * 
	 * @param displayName the display name of the item
	 * @param frequency   the frequency count of the item
	 * @param support     the support percentage of the item
	 * @param totalCost   the total cost of the item across all transactions
	 * @return a JPanel containing the formatted row
	 */
	private JPanel createItemRow(String displayName, int frequency, double support, long totalCost) {
		JPanel row = new JPanel();
		row.setLayout(new BorderLayout());
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Truncate long names
		String truncatedName = displayName;
		if (truncatedName.length() > 15) {
			truncatedName = truncatedName.substring(0, 12) + "...";
		}

		JLabel itemNameLabel = new JLabel("  " + truncatedName);
		itemNameLabel.setToolTipText(displayName); // Show full name on hover

		// Create right-aligned panel for count, support, and cost
		JPanel rightItemPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		rightItemPanel.setOpaque(false);

		JLabel countLabel = new JLabel(String.valueOf(frequency));
		countLabel.setPreferredSize(new Dimension(45, 16));
		countLabel.setHorizontalAlignment(JLabel.RIGHT);

		JLabel supportLabel = new JLabel(String.format("%.1f%%", support));
		supportLabel.setPreferredSize(new Dimension(50, 16));
		supportLabel.setHorizontalAlignment(JLabel.RIGHT);

		JLabel costLabel = new JLabel(String.valueOf(totalCost));
		costLabel.setPreferredSize(new Dimension(55, 16));
		costLabel.setHorizontalAlignment(JLabel.RIGHT);

		rightItemPanel.add(countLabel);
		rightItemPanel.add(supportLabel);
		rightItemPanel.add(costLabel);

		row.add(itemNameLabel, BorderLayout.WEST);
		row.add(rightItemPanel, BorderLayout.EAST);

		return row;
	}

	/**
	 * Toggle between column display mode and list display mode
	 */
	private void toggleDisplayMode() {
		if (currentDatabase != null) {
			boolean displayAsLists = displayAsListsMenuItem.isSelected();

			// Create a new TransactionTableModel object with the current display mode
			TransactionTableModel model = new TransactionTableModel(currentDatabase, displayAsLists);
			// Set the table model to the new model
			table.setModel(model);

			// Adjust auto-resize mode based on display mode
			if (displayAsLists) {
				table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
			} else {
				table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
			}
		}
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
		// Create a new CostUtilityTransactionDatabase object
		CostUtilityTransactionDatabase db = new CostUtilityTransactionDatabase();
		try {
			// Load the file containing the utility transaction database
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

		// Create a new TransactionTableModel object with the new database and current
		// display mode
		boolean displayAsLists = displayAsListsMenuItem != null && displayAsListsMenuItem.isSelected();
		TransactionTableModel model = new TransactionTableModel(db, displayAsLists);
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

	private void viewItemCostDistribution() {
		if (table.getModel() != null && currentDatabase != null) {
			CostUtilityTransactionDatabase db = currentDatabase;

			Map<Integer, String> mapItemToString = db.getMapItemToStringValues();

			// Find the maximum item
			int maxItem = db.getMaxItemID();

			// Prepare the data (utilities)
			int[] yValues = new int[maxItem + 1];
			for (TransactionCost transaction : db.getTransactions()) {
				for (ItemCost item : transaction.getItems()) {
					yValues[item.item] += item.cost;
				}
			}

			// Prepare the X values data
			int[] xValues = new int[maxItem + 1];
			for (int i = 0; i < maxItem + 1; i++) {
				xValues[i] = i;
			}

			///// Remove zero values ////////////////////////////33
			// SPECIAL CASE: Check if some item did not exist in the previous array
			// and create a new array to remove them.
			// This case only occurs if the input file has item that are not
			// consecutive and starting from 1. For example: fruithut_utility.txt
			int count = db.getAllItems().size();
			if (count != maxItem) {

				// Initialize new arrays to store the non-zero pairs
				int[] newXValues = new int[count];
				int[] newYValues = new int[count];

				// Keep track of the index for the new arrays
				int index = 0;

				// Loop over the original arrays
				for (int i = 0; i < maxItem + 1; i++) {
					// If the Y value is not zero, add the pair to the new arrays
					if (yValues[i] != 0) {
						newXValues[index] = xValues[i];
						newYValues[index] = yValues[i];
						index++;
					} else {
						if (mapItemToString.get(xValues[i]) != null) {
							System.out.println(xValues[i]);
						}
					}
				}
				xValues = newXValues;
				yValues = newYValues;
			}
			//////////////////////////////////////////////

			HistogramDistributionWindow frame2 = new HistogramDistributionWindow(false, yValues, xValues,
					"Item cost distribution", true, true, "Item", "Cost", mapItemToString, Order.ASCENDING_Y);
		}
	}

	// Define a method to view the transaction utility distribution using a
	// histogram window
	private void viewTransactionUtilityDistribution() {
		if (table.getModel() != null && currentDatabase != null) {
			CostUtilityTransactionDatabase db = currentDatabase;

			// Find the maximum utility
			int maxUtility = 0;
			for (TransactionCost transaction : db.getTransactions()) {
				int utility = transaction.getTransactionUtility();
				if (utility > maxUtility) {
					maxUtility = utility;
				}
			}

			// Prepare the data (counts)
			int[] yValues = new int[maxUtility + 1];
			for (TransactionCost transaction : db.getTransactions()) {
				int utility = transaction.getTransactionUtility();
				yValues[utility]++;
			}

			// Prepare the X values data
			int[] xValues = new int[maxUtility + 1];
			for (int i = 0; i < maxUtility + 1; i++) {
				xValues[i] = i;
			}

			HistogramDistributionWindow frame2 = new HistogramDistributionWindow(false, yValues, xValues,
					"Transaction utility frequency distribution", true, true, "Utility", "Count", null,
					Order.ASCENDING_X);
		}
	}

	/**
	 * View the distribution of transaction length using a histogram window
	 */
	private void viewTransactionLengthDistribution() {
		if (table.getModel() != null && currentDatabase != null) {
			CostUtilityTransactionDatabase db = currentDatabase;

			// Find the maximum size
			int maxSize = 0;
			for (TransactionCost transaction : db.getTransactions()) {
				int size = transaction.size();
				if (size > maxSize) {
					maxSize = size;
				}
			}

			// Prepare the data (utilities)
			int[] yValues = new int[maxSize + 1];
			for (TransactionCost transaction : db.getTransactions()) {
				int size = transaction.size();
				yValues[size] += 1;
			}

			// Prepare the X values data
			int[] xValues = new int[maxSize + 1];
			for (int i = 0; i < maxSize + 1; i++) {
				xValues[i] = i;
			}

			HistogramDistributionWindow frame2 = new HistogramDistributionWindow(false, yValues, xValues,
					"Transaction length distribution", true, true, "Length", "Count", null, Order.ASCENDING_X);
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

		// Create an adapter for the cost utility transaction database
		ItemSetProvider provider = new CostUtilityTransactionDatabaseItemSetProvider(currentDatabase);

		// Use the reusable heatmap generator
		ItemCooccurrenceHeatmapGenerator.showCooccurrenceHeatmapDialog(frame, provider, runAsStandalone);
	}

	/**
	 * The table model to display the utility transactions
	 */
	class TransactionTableModel implements TableModel {

		/** The CostUtilityTransactionDatabase object that holds the data **/
		CostUtilityTransactionDatabase db;

		/** The list of listeners for this table model */
		private List<TableModelListener> listeners;

		/** Flag to indicate whether to display items as lists or as columns */
		private boolean displayAsLists;

		/**
		 * The constructor that takes a CostUtilityTransactionDatabase object as
		 * parameter
		 * 
		 * @param db             a utility transaction database object
		 * @param displayAsLists true to display items as lists, false to display as
		 *                       columns
		 */
		public TransactionTableModel(CostUtilityTransactionDatabase db, boolean displayAsLists) {
			// Initialize the fields
			this.db = db;
			this.displayAsLists = displayAsLists;
			this.listeners = new ArrayList<TableModelListener>();
		}

		/**
		 * Get the number of rows in the table
		 * 
		 * @return number of rows
		 */
		public int getRowCount() {
			// In list mode, no extra summary row; in column mode, add one for totals
			return displayAsLists ? db.size() : db.size() + 1;
		}

		/**
		 * Get the number of columns in the table
		 * 
		 * @return number of columns
		 */
		public int getColumnCount() {
			if (displayAsLists) {
				// Transaction, Items, Length, Utility
				return 4;
			} else {
				// Return the number of items in the database plus two
				return db.getAllItems().size() + 2;
			}
		}

		/**
		 * Get the name of the column at the given index
		 * 
		 * @param columnIndex the column index
		 * @return the name
		 */
		public String getColumnName(int columnIndex) {
			if (displayAsLists) {
				switch (columnIndex) {
				case 0:
					return "Transaction";
				case 1:
					return "Items (Cost)";
				case 2:
					return "Length";
				case 3:
					return "Utility";
				default:
					return "";
				}
			} else {
				// If the column index is zero, return "TID"
				if (columnIndex == 0) {
					return "Transaction";
				}
				// If the column index is equal to the number of items plus one, return "TU"
				if (columnIndex == db.getAllItems().size() + 1) {
					return "Utility";
				}
				// Otherwise, return the name of the item at the given index minus one
				// Assume that the items are sorted in ascending order
				return getItemName(columnIndex, true);
			}
		}

		/**
		 * Get the item name for a given column index
		 * 
		 * @param itemID        the item
		 * @param forColumnName true if this is for a column name
		 * @return the item name
		 */
		private String getItemName(int itemID, boolean forColumnName) {
			String itemName = db.getNameForItem(itemID);
			if (itemName != null) {
				if (forColumnName) {
					return itemName + " (" + itemID + ")";
				} else {
					return itemName;
				}
			} else {
				return "Item " + itemID;
			}
		}

		/**
		 * Get the class of the values in the column at the given index.
		 * 
		 * @param columnIndex the index
		 * @return the class
		 */
		public Class<?> getColumnClass(int columnIndex) {
			if (displayAsLists) {
				if (columnIndex == 2 || columnIndex == 3) {
					return Integer.class;
				}
				return String.class;
			} else {
				// If the column index is zero, return the class of Integer, since the TID is an
				// integer
				if (columnIndex == 0) {
					return Integer.class;
				}
				// If the column index is equal to the number of items plus one, return the
				// class of Integer, since the TU is an
				// integer
				if (columnIndex == db.getAllItems().size() + 1) {
					return Integer.class;
				}
				// Otherwise, check the row index of the last row
				int rowIndex = db.size();
				// If the row index is equal to the number of transactions, return the class of
				// Integer, since the utility is an integer
				if (rowIndex == db.size()) {
					return Integer.class;
				}
				// Otherwise, return the class of String, since the table will display item
				// names and utilities
				return String.class;
			}
		}

		/**
		 * Get the value at the given row and column index
		 * 
		 * @param rowIndex    the row
		 * @param columnIndex the column
		 * @return the object
		 */
		public Object getValueAt(int rowIndex, int columnIndex) {
			if (displayAsLists) {
				TransactionCost transaction = db.getTransactions().get(rowIndex);
				switch (columnIndex) {
				case 0:
					return Integer.toString(rowIndex);
				case 1:
					StringBuilder sb = new StringBuilder();
					boolean first = true;
					for (ItemCost itemCost : transaction.getItems()) {
						if (!first) {
							sb.append(", ");
						}
						sb.append(getItemName(itemCost.item, false));
						sb.append(" (cost:");
						sb.append(itemCost.cost);
						sb.append(")");
						first = false;
					}
					return sb.toString();
				case 2:
					return transaction.size();
				case 3:
					return transaction.getTransactionUtility();
				default:
					return "";
				}
			} else {
				// If the row index is equal to the number of transactions, return the utility
				// for
				// the column
				if (rowIndex == db.size()) {
					// If the column index is zero, return "Total utility:"
					if (columnIndex == 0) {
						return "Total cost:";
					}
					// If the column index is equal to the number of items plus one, return the
					// total utility of the database
					if (columnIndex == db.getAllItems().size() + 1) {
						return Long.toString(db.getTotalUtility());
					}
					// Otherwise, get the item at the given column index minus one
					// Assume that the items are sorted in ascending order
					int item = columnIndex;
					// Initialize the utility to zero
					int utility = 0;
					// Loop through the transactions in the database
					for (TransactionCost transaction : db.getTransactions()) {
						// Loop through the items in the transaction
						for (ItemCost ItemCost : transaction.getItems()) {
							// If the item matches, add the utility to the sum
							if (ItemCost.item == item) {
								utility += ItemCost.cost;
							}
						}
					}
					// Return the utility as an Integer object
					return Integer.toString(utility);
				}
				// Otherwise, use the modified code
				// If the column index is zero, return the row index
				if (columnIndex == 0) {
					return Integer.toString(rowIndex);
				}
				// If the column index is equal to the number of items plus one, return the
				// transaction utility
				if (columnIndex == db.getAllItems().size() + 1) {
					return Integer.toString(db.getTransactions().get(rowIndex).getTransactionUtility());
				}
				// Otherwise, get the transaction at the given row index
				TransactionCost transaction = db.getTransactions().get(rowIndex);
				// Get the item at the given column index minus one
				// Assume that the items are sorted in ascending order
				int item = columnIndex;
				// Return the item name and utility if the transaction contains the item, empty
				// string otherwise
				for (ItemCost ItemCost : transaction.getItems()) {
					if (ItemCost.item == item) {
						return "Cost: " + ItemCost.cost;
					}
				}
				return "";
			}
		}

		/**
		 * Get whether the cell at the given row and column index is editable
		 * 
		 * @param rowIndex    the row
		 * @param columnIndex the column
		 * @return a boolean indicating if the cell is editable
		 */
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			// Return false, since the table is not editable
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
			// Add the listener to the list
			listeners.add(l);
		}

		/**
		 * Remove a listener from this table model
		 * 
		 * @param l a TableModelListener object
		 */
		public void removeTableModelListener(TableModelListener l) {
			// Remove the listener from the list
			listeners.remove(l);
		}
	}

}