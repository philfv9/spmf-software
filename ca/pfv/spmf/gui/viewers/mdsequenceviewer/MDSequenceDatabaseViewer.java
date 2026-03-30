package ca.pfv.spmf.gui.viewers.mdsequenceviewer;

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
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.ItemSimple;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.Itemset;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.multidimensionalsequentialpatterns.MDSequence;
import ca.pfv.spmf.algorithms.sequentialpatterns.fournier2008_seqdim.multidimensionalsequentialpatterns.MDSequenceDatabase;
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
 * A tool to visualize a multi-dimensional sequence database in SPMF format
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class MDSequenceDatabaseViewer extends JFrame {

	/** generated serial UID */
	private static final long serialVersionUID = -8157144459993878439L;

	/** The table */
	private JTable table;
	
	/** The scroll pane */
	private JScrollPane scrollPane;
	
	/** The status label */
	private JLabel statusLabel;
	
	/** The name label */
	private JLabel nameLabel;

	/** if true, timestamps will be displayed. */
	private boolean displayTimestamps;

	/** The current database */
	private MDSequenceDatabase currentDatabase;

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
	 * @param filePath the input file path
	 * @param displayTimestamps      if true, timestamps will be displayed.
	 *                               Otherwise, not.
	 */
	public MDSequenceDatabaseViewer(boolean runAsStandaloneProgram, String filePath, boolean displayTimestamps) {
		// Initialize the frame title
		super("SPMF MD-Sequence Database Viewer " + Main.SPMF_VERSION);
		this.runAsStandalone = runAsStandaloneProgram;
		this.rightPanelVisible = true;

		if (runAsStandaloneProgram) {
			// Set the default close operation
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}
		
		// save the parameter
		this.displayTimestamps = displayTimestamps;

		// Create the menu bar
		JMenuBar menuBar = createMenuBar();
		setJMenuBar(menuBar);

		// Create a JTable object to display the md-sequence database
		table = new SortableJTable();
		// Set the auto resize mode to off
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

		// Create a JScrollPane object to wrap the table
		scrollPane = new JScrollPane(table);
		// Set the horizontal and vertical scroll bar policies to always show
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

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
		add(splitPane, BorderLayout.CENTER);

		// Create the status bar at the bottom
		JPanel statusPanel = new JPanel();
		statusPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		statusLabel = new JLabel();
		statusPanel.add(statusLabel);
		add(statusPanel, BorderLayout.SOUTH);

		// Create the top panel with the name label
		JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		nameLabel = new JLabel();
		// Set the text of the label to show the file name of the database
		nameLabel.setText("Database: " + (filePath != null ? filePath : "(none)"));
		topPanel.add(nameLabel);
		add(topPanel, BorderLayout.NORTH);
		
		if (filePath != null) {
			openDatabaseFile(filePath);
		}

		// Set the size and location of the frame
		setSize(1100, 600);
		setLocationRelativeTo(null);
		pack();
		// Make the frame visible
		setVisible(true);

		// Set the initial divider location after the frame is visible
		splitPane.setDividerLocation(getWidth() - 300);
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

		// View sequence length distribution
		JMenuItem seqLengthItem = new JMenuItem("View Sequence Length Distribution");
		seqLengthItem.setMnemonic(KeyEvent.VK_T);
		seqLengthItem.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/histogram.png")));
		seqLengthItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				viewSequenceLengthDistribution();
			}
		});
		toolsMenu.add(seqLengthItem);

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
	 * @param db the MD sequence database
	 * @return the populated statistics panel
	 */
	private JPanel createStatsPanel(MDSequenceDatabase db) {
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

		// Collect unique items across all sequences
		Set<Integer> uniqueItemsSet = new HashSet<Integer>();

		for (MDSequence mdsequence : db.getSequences()) {
			int len = mdsequence.getSequence().size();
			lengths.add(len);
			totalLength += len;
			if (len < minLength) {
				minLength = len;
			}
			if (len > maxLength) {
				maxLength = len;
			}
			for (Itemset itemset : mdsequence.getSequence().getItemsets()) {
				for (ItemSimple item : itemset.getItems()) {
					uniqueItemsSet.add(item.getId());
				}
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
		int uniqueItems = uniqueItemsSet.size();

		// Get dimension count
		int dimensionCount = db.getPatternDatabase().getDimensionCount();

		// Count singleton sequences (length = 1 itemset)
		int singletonCount = 0;
		for (int len : lengths) {
			if (len == 1) {
				singletonCount++;
			}
		}

		// Count empty sequences (length = 0)
		int emptyCount = 0;
		for (int len : lengths) {
			if (len == 0) {
				emptyCount++;
			}
		}

		// === Database Size Section ===
		panel.add(createSectionHeader("Database Size"));
		panel.add(createStatRow("MD-Sequences", String.valueOf(db.size())));
		panel.add(createStatRow("Unique items", String.valueOf(uniqueItems)));
		panel.add(createStatRow("Dimensions", String.valueOf(dimensionCount)));
		panel.add(createStatRow("Max item ID", String.valueOf(db.getMaxItemID())));
		panel.add(createStatRow("Total itemset count", String.valueOf(totalLength)));

		panel.add(createVerticalStrut(12));

		// === Sequence Length Section (itemset count per sequence) ===
		panel.add(createSectionHeader("Sequence Length (itemsets)"));
		panel.add(createStatRow("Minimum", String.valueOf(minLength)));
		panel.add(createStatRow("Maximum", String.valueOf(maxLength)));
		panel.add(createStatRow("Average", String.format("%.2f", avgLength)));
		panel.add(createStatRow("Median", String.format("%.1f", medianLength)));
		panel.add(createStatRow("Std. deviation", String.format("%.2f", stdDev)));

		panel.add(createVerticalStrut(12));

		// === Special Sequences Section ===
		panel.add(createSectionHeader("Special Sequences"));
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
	 * Create and populate the top-K frequent items panel.
	 * Support is calculated per sequence (a sequence supports an item if the item
	 * appears in at least one itemset of that sequence).
	 * 
	 * @param db the MD sequence database
	 * @return the populated top-K panel
	 */
	private JPanel createTopKPanel(MDSequenceDatabase db) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Item Frequency"));

		if (db == null || db.size() == 0) {
			panel.add(new JLabel("No data available"));
			return panel;
		}

		// Calculate item support (number of sequences containing each item)
		Map<Integer, Integer> itemSupport = new HashMap<Integer, Integer>();
		for (MDSequence mdsequence : db.getSequences()) {
			// Use a set to count each item only once per sequence
			Set<Integer> itemsInSequence = new HashSet<Integer>();
			for (Itemset itemset : mdsequence.getSequence().getItemsets()) {
				for (ItemSimple item : itemset.getItems()) {
					itemsInSequence.add(item.getId());
				}
			}
			for (Integer itemId : itemsInSequence) {
				Integer count = itemSupport.get(itemId);
				if (count == null) {
					itemSupport.put(itemId, 1);
				} else {
					itemSupport.put(itemId, count + 1);
				}
			}
		}

		// Sort items by support (descending)
		List<Map.Entry<Integer, Integer>> sortedItems = new ArrayList<Map.Entry<Integer, Integer>>(
				itemSupport.entrySet());
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

		JLabel countHeader = new JLabel("Seqs");
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
	 * @param frequency   the number of sequences containing the item
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
	 * View the distribution of item frequency using a histogram window
	 */
	private void viewItemFrequencyDistribution() {
		if (table.getModel() != null && currentDatabase != null) {
			MDSequenceDatabase db = currentDatabase;
//			Map<Integer, String> mapItemToString = db.getSequenceDatabase().getMapItemToStringValues();

			// Find the maximum size
			int maxItem = db.getMaxItemID();

			// Prepare the data (counts)
			int[] yValues = new int[maxItem + 1];
			for (MDSequence mdsequence : db.getSequences()) {
				for (Itemset itemset : mdsequence.getSequence().getItemsets()) {
					for (ItemSimple item : itemset.getItems()) {
						System.out.println(item.getId() + " " + maxItem);
						yValues[item.getId()]++;
					}
				}
			}

			// Prepare the X values data
			int[] xValues = new int[maxItem + 1];
			for (int i = 0; i < maxItem + 1; i++) {
				xValues[i] = i;
			}

			HistogramDistributionWindow frame2 = new HistogramDistributionWindow(false, yValues, xValues,
					"Item frequency distribution", true, true, "Item", "Count", null, Order.ASCENDING_Y);
		}
	}

	/**
	 * View the distribution of sequence length using a histogram window
	 */
	private void viewSequenceLengthDistribution() {
		if (table.getModel() != null && currentDatabase != null) {
			MDSequenceDatabase db = currentDatabase;

			// Find the maximum size
			int maxSize = 0;
			for (MDSequence mdsequence : db.getSequences()) {
				int size = mdsequence.getSequence().size();
				if (size > maxSize) {
					maxSize = size;
				}
			}

			// Prepare the data (counts)
			int[] yValues = new int[maxSize + 1];
			for (MDSequence mdsequence : db.getSequences()) {
				int size = mdsequence.getSequence().size();
				yValues[size]++;
			}

			// Prepare the X values data
			int[] xValues = new int[maxSize + 1];
			for (int i = 0; i < maxSize + 1; i++) {
				xValues[i] = i;
			}

			HistogramDistributionWindow frame2 = new HistogramDistributionWindow(false, yValues, xValues,
					"Sequence length frequency distribution", true, true, "Length", "Count", null, Order.ASCENDING_X);
		}

	}

	/**
	 * View the co-occurrence of the X most frequent items as a heatmap. Prompts the
	 * user for the number of top items to include.
	 */
	private void viewItemCooccurrenceHeatmap() {
		if (currentDatabase == null || currentDatabase.size() == 0) {
			JOptionPane.showMessageDialog(this, "Please load a database first.", "No Database",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		// Create an adapter for the MD sequence database
		ItemSetProvider provider = new MDSequenceDatabaseItemSetProvider(currentDatabase);

		// Use the reusable heatmap generator
		ItemCooccurrenceHeatmapGenerator.showCooccurrenceHeatmapDialog(this, provider, runAsStandalone);
	}

	/**
	 * Resize columns of the JTable
	 * 
	 * @param table the table
	 */
	public void resizeColumnWidth(JTable table) {
		final TableColumnModel columnModel = table.getColumnModel();
		for (int column = 0; column < table.getColumnCount(); column++) {
			int width = 60; // Min width
			for (int row = 0; row < table.getRowCount(); row++) {
				TableCellRenderer renderer = table.getCellRenderer(row, column);
				Component comp = table.prepareRenderer(renderer, row, column);
				width = Math.max(comp.getPreferredSize().width + 1, width);
			}
			if (width > 300)
				width = 300;
			columnModel.getColumn(column).setPreferredWidth(width);
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
		int result = fc.showOpenDialog(MDSequenceDatabaseViewer.this);
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
		// Create a new MDSequenceDatabase object
		MDSequenceDatabase db = new MDSequenceDatabase();
		try {
			// Load the file containing the md-sequence database
			db.loadFile(filepath);
		} catch (Exception ex) {
			// Get the exception message and the stack trace as a string
			String errorMessage = String.format("Error loading file. Reading error: %s%n", ex.getMessage());
			// Get the exception class name as a string
			String title = ex.getClass().getName();
			// Show a JDialog with the exception message and the stack trace, and set the
			// dialog title and the message type
			JOptionPane.showMessageDialog(this, errorMessage, title, JOptionPane.ERROR_MESSAGE);
		}

		// Store the current database
		currentDatabase = db;

		// Create a new MDSequenceTableModel object with the new database
		MDSequenceTableModel model = new MDSequenceTableModel(db, displayTimestamps);
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

		// Initialize the sum of sequence lengths to zero
		int sum = 0;
		// Loop through the md-sequences in the database
		for (MDSequence mdsequence : db.getSequences()) {
			// Add the sequence length to the sum
			sum += mdsequence.getSequence().size();
		}
		// Calculate the average sequence length by dividing the sum by the number of
		// md-sequences
		double average = (double) sum / db.size();
		// Format the average to two decimal places
		String averageString = String.format("%.2f", average);

		// Update the status label to show the new number of md-sequences, the min and
		// max
		// items, and the average sequence length
		statusLabel.setText("File size: " + fileSizeMBString + " MB | MD-Sequences: " + db.size()  + " | Dimension count: " +
																								 db.getPatternDatabase().getDimensionCount()
		 + " | Max item: " + db.getMaxItemID()
				+ " | Average itemset count per md-sequence: " + averageString);
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

		resizeColumnWidth(table);
	}
}