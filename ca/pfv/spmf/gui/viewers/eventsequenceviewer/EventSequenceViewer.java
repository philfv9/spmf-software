package ca.pfv.spmf.gui.viewers.eventsequenceviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
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
import javax.swing.JButton;
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
import ca.pfv.spmf.gui.visuals.timeline.EventT;
import ca.pfv.spmf.gui.visuals.timeline.TimelineViewer;
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
 * This class is an event sequence viewer tool for visualizing the content of an
 * event sequence file with timestamps.
 * 
 * @author Philippe Fournier-Viger
 */
public class EventSequenceViewer extends JFrame {

	/** The table */
	private JTable table;
	/** The scroll pane */
	private JScrollPane scrollPane;
	/** The status label */
	private JLabel statusLabel;
	/** The name label */
	private JLabel nameLabel;

	// The JPanel object that holds the timeline chart
	private JPanel timelinePanel;
	private EventSequence es;

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
	 * @param runAsStandaloneProgram if true, this tool will be run in standalone
	 *                               mode (close the window will close the program).
	 *                               Otherwise not.
	 */
	public EventSequenceViewer(boolean runAsStandaloneProgram, String filePath) {
		// Initialize the frame title
		super("SPMF Event Sequence Viewer " + Main.SPMF_VERSION);
		this.runAsStandalone = runAsStandaloneProgram;
		this.rightPanelVisible = true;

		if (runAsStandaloneProgram) {
			// Set the default close operation
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}
		// Set the layout to border layout
		setLayout(new BorderLayout());

		// Create the menu bar
		JMenuBar menuBar = createMenuBar();
		setJMenuBar(menuBar);

		// Create a JTable object to display the event sequence
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

		// Create the status bar at the bottom with buttons
		JPanel bottomPanel = new JPanel(new BorderLayout());

		JPanel statusPanel = new JPanel();
		statusPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		statusLabel = new JLabel();
		statusPanel.add(statusLabel);
		bottomPanel.add(statusPanel, BorderLayout.NORTH);

		// // Create a JPanel object to hold the buttons
		JPanel buttonPanel = new JPanel();

		// Create a JButton object for the first button
		JButton button1 = new JButton("View with Timeline Viewer");
		// Add an action listener to the button
		button1.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				viewAsTimeline();
			}

		});
		button1.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/timeline20b.png")));

		// Add the buttons to the panel using a FlowLayout manager
		buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
		buttonPanel.add(button1);
		bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

		// Add the bottom panel to the frame using the BorderLayout.SOUTH position
		add(bottomPanel, BorderLayout.SOUTH);

		// Create a JPanel object to hold the top components
		JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		// Create a JLabel object to display the sequence name
		nameLabel = new JLabel();
		// Set the text of the label to show the file name of the sequence
		nameLabel.setText("Sequence: " + (filePath != null ? filePath : "(none)"));
		// Add the name label to the panel
		topPanel.add(nameLabel);
		// Add the panel to the frame using the BorderLayout.NORTH position
		add(topPanel, BorderLayout.NORTH);

		if (filePath != null) {
			openSequenceFile(filePath);
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
	 * @return the File menu
	 */
	private JMenu createFileMenu() {
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);

		// Open sequence
		JMenuItem openItem = new JMenuItem("Open Sequence...");
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
	 * @return the Tools menu
	 */
	private JMenu createToolsMenu() {
		JMenu toolsMenu = new JMenu("Tools");
		toolsMenu.setMnemonic(KeyEvent.VK_T);

		// View with Timeline Viewer
		JMenuItem timelineItem = new JMenuItem("View with Timeline Viewer");
		timelineItem.setMnemonic(KeyEvent.VK_L);
		timelineItem.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/timeline20b.png")));
		timelineItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				viewAsTimeline();
			}
		});
		toolsMenu.add(timelineItem);

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
	 * Create an empty statistics panel (placeholder before sequence is loaded)
	 * @return the empty statistics panel
	 */
	private JPanel createEmptyStatsPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Summary Statistics"));
		panel.add(new JLabel("Load a sequence to view statistics"));
		return panel;
	}

	/**
	 * Create an empty top-K items panel (placeholder before sequence is loaded)
	 * @return the empty top-K panel
	 */
	private JPanel createEmptyTopKPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Item Frequency"));
		panel.add(new JLabel("Load a sequence to view item frequency"));
		return panel;
	}

	/**
	 * Create and populate the statistics panel with sequence statistics
	 * @param seq the event sequence
	 * @return the populated statistics panel
	 */
	private JPanel createStatsPanel(EventSequence seq) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Summary Statistics"));

		if (seq == null || seq.size() == 0) {
			panel.add(new JLabel("No data available"));
			return panel;
		}

		// Count unique events
		int uniqueEventCount = seq.getUniqueEvents().size();

		// Get the minimum and maximum timestamps in the sequence
		long minTimestamp = seq.getMinTimestamp();
		long maxTimestamp = seq.getMaxTimestamp();

		// Calculate the duration of the sequence
		long duration = maxTimestamp - minTimestamp;

		// Calculate event frequencies
		Map<Integer, Integer> eventFrequencies = new HashMap<Integer, Integer>();
		for (int i = 0; i < seq.size(); i++) {
			Event event = seq.get(i);
			int item = event.getItem();
			Integer count = eventFrequencies.get(item);
			if (count == null) {
				eventFrequencies.put(item, 1);
			} else {
				eventFrequencies.put(item, count + 1);
			}
		}

		// Calculate min, max, avg frequency
		int minFreq = Integer.MAX_VALUE;
		int maxFreq = 0;
		int totalFreq = 0;
		for (int freq : eventFrequencies.values()) {
			totalFreq += freq;
			if (freq < minFreq) {
				minFreq = freq;
			}
			if (freq > maxFreq) {
				maxFreq = freq;
			}
		}
		double avgFreq = (double) totalFreq / uniqueEventCount;

		// Calculate standard deviation of frequencies
		double sumSquaredDiff = 0;
		for (int freq : eventFrequencies.values()) {
			sumSquaredDiff += Math.pow(freq - avgFreq, 2);
		}
		double stdDevFreq = Math.sqrt(sumSquaredDiff / uniqueEventCount);

		// Calculate median frequency
		List<Integer> freqList = new ArrayList<Integer>(eventFrequencies.values());
		Collections.sort(freqList);
		double medianFreq;
		int size = freqList.size();
		if (size % 2 == 0) {
			medianFreq = (freqList.get(size / 2 - 1) + freqList.get(size / 2)) / 2.0;
		} else {
			medianFreq = freqList.get(size / 2);
		}

		// Count distinct timestamps
		java.util.Set<Long> distinctTimestamps = new java.util.HashSet<Long>();
		for (int i = 0; i < seq.size(); i++) {
			distinctTimestamps.add(seq.get(i).getTimestamp());
		}

		// === Sequence Size Section ===
		panel.add(createSectionHeader("Sequence Size"));
		panel.add(createStatRow("Total events", String.valueOf(seq.size())));
		panel.add(createStatRow("Unique event types", String.valueOf(uniqueEventCount)));
		panel.add(createStatRow("Distinct timestamps", String.valueOf(distinctTimestamps.size())));

		panel.add(createVerticalStrut(12));

		// === Timestamp Section ===
		panel.add(createSectionHeader("Timestamps"));
		panel.add(createStatRow("Minimum", String.valueOf(minTimestamp)));
		panel.add(createStatRow("Maximum", String.valueOf(maxTimestamp)));
		panel.add(createStatRow("Duration", String.valueOf(duration)));

		panel.add(createVerticalStrut(12));

		// === Event Frequency Section ===
		panel.add(createSectionHeader("Event Frequency"));
		panel.add(createStatRow("Minimum", String.valueOf(minFreq)));
		panel.add(createStatRow("Maximum", String.valueOf(maxFreq)));
		panel.add(createStatRow("Average", String.format("%.2f", avgFreq)));
		panel.add(createStatRow("Median", String.format("%.1f", medianFreq)));
		panel.add(createStatRow("Std. deviation", String.format("%.2f", stdDevFreq)));

		return panel;
	}

	/**
	 * Create and populate the top-K frequent items panel
	 * @param seq the event sequence
	 * @return the populated top-K panel
	 */
	private JPanel createTopKPanel(EventSequence seq) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Item Frequency"));

		if (seq == null || seq.size() == 0) {
			panel.add(new JLabel("No data available"));
			return panel;
		}

		// Calculate event frequencies
		Map<Integer, Integer> eventFrequencies = new HashMap<Integer, Integer>();
		for (int i = 0; i < seq.size(); i++) {
			Event event = seq.get(i);
			int item = event.getItem();
			Integer count = eventFrequencies.get(item);
			if (count == null) {
				eventFrequencies.put(item, 1);
			} else {
				eventFrequencies.put(item, count + 1);
			}
		}

		// Sort items by frequency (descending)
		List<Map.Entry<Integer, Integer>> sortedItems = new ArrayList<Map.Entry<Integer, Integer>>(
				eventFrequencies.entrySet());
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
			double support = (frequency * 100.0) / seq.size();

			// Get item name if available
			String itemName = seq.getNameForItem(itemId);
			String displayName;
			if (itemName != null) {
				displayName = itemName + " (" + itemId + ")";
			} else {
				displayName = "Item " + itemId;
			}

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
			double support = (frequency * 100.0) / seq.size();

			// Get item name if available
			String itemName = seq.getNameForItem(itemId);
			String displayName;
			if (itemName != null) {
				displayName = itemName + " (" + itemId + ")";
			} else {
				displayName = "Item " + itemId;
			}

			// Create row for item
			panel.add(createItemRow(displayName, frequency, support));
		}

		return panel;
	}

	/**
	 * Create a section header label with bold formatting
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
	 * @param name the statistic name
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
	 * Create a header row for the item frequency table
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

		JLabel supportHeader = new JLabel("Pct%");
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
	 * @param displayName the display name of the item
	 * @param frequency the frequency count of the item
	 * @param support the support percentage of the item
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
		int result = fc.showOpenDialog(EventSequenceViewer.this);
		// If the user approved the file selection
		if (result == JFileChooser.APPROVE_OPTION) {
			// Get the selected file
			File file = fc.getSelectedFile();
			// Get the file path
			String filepath = file.getPath();
			openSequenceFile(filepath);
		}
		// remember this folder for next time.
		if (fc.getSelectedFile() != null) {
			PreferencesManager.getInstance().setInputFilePath(fc.getSelectedFile().getParent());
		}
	}

	protected void viewAsTimeline() {
		if (table.getModel() != null) {
			EventSequence db = ((EventSequenceTableModel) table.getModel()).es;
			Map<Integer, String> mapItemToString = db.getMapItemToStringValues();

			// Create a list of Events
			List<EventT> events = new ArrayList<>();
			for (int i = 0; i < db.size(); i++) {
				Event event = db.get(i);
				String name;
				if (mapItemToString == null) {
					name = Integer.toString(event.getItem());
				} else {
					name = mapItemToString.get(event.getItem());
				}
				long time = event.getTimestamp();
				events.add(new EventT(name, time));
			}

			// Instantiate the TimelineViewer
			TimelineViewer timelineViewer = new TimelineViewer(false, events, null);

			// You may need to add additional code here to display the timelineViewer
		}
	}

	private void viewItemFrequencyDistribution() {
		if (table.getModel() != null) {
			EventSequence db = ((EventSequenceTableModel) table.getModel()).es;
			Map<Integer, String> mapItemToString = db.getMapItemToStringValues();

			// Find the maximum item
			int maxItem = es.getMaxItemID();

			// Prepare the data (counts)
			int[] yValues = new int[maxItem + 1];
			for (int i = 0; i < db.size(); i++) {
				Event event = db.get(i);
				int item = event.getItem();
				yValues[item]++;
			}

			// Prepare the X values data
			int[] xValues = new int[maxItem + 1];
			for (int i = 0; i < maxItem + 1; i++) {
				xValues[i] = i;
			}

			HistogramDistributionWindow frame2 = new HistogramDistributionWindow(false, yValues, xValues,
					"Item frequency distribution", true, true, "Item", "Count", mapItemToString, Order.ASCENDING_Y);
		}

	}

	/**
	 * View the co-occurrence of the X most frequent items as a heatmap. Prompts the
	 * user for the number of top items to include.
	 */
	private void viewItemCooccurrenceHeatmap() {
		if (es == null || es.size() == 0) {
			JOptionPane.showMessageDialog(this, "Please load a sequence first.", "No Sequence",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		// Create an adapter for the event sequence
		ItemSetProvider provider = new EventSequenceItemSetProvider(es);

		// Use the reusable heatmap generator
		ItemCooccurrenceHeatmapGenerator.showCooccurrenceHeatmapDialog(this, provider, runAsStandalone);
	}

	/**
	 * Open a sequence in the viewer
	 * 
	 * @param filepath the file path
	 */
	private void openSequenceFile(String filepath) {
		es = new EventSequence();
		try {
			// Load the file containing the event sequence
			es.loadFile(filepath);
		} catch (Exception ex) {
			// Get the exception message and the stack trace as a string
			String errorMessage = String.format("Error loading file. Reading error: %s%n", ex.getMessage());
			// Get the exception class name as a string
			String title = ex.getClass().getName();
			// Show a JDialog with the exception message and the stack trace, and set the
			// dialog title and the message type
			JOptionPane.showMessageDialog(this, errorMessage, title, JOptionPane.ERROR_MESSAGE);
		}
		// Create a new EventSequenceTableModel object with the new sequence
		EventSequenceTableModel model = new EventSequenceTableModel(es);
		// Set the table model to the new model
		table.setModel(model);

		// Get the file object of the sequence
		File file = new File(filepath);
		// Get the file size in bytes
		long fileSize = file.length();
		// Convert the file size to megabytes
		double fileSizeMB = fileSize / (1024.0 * 1024.0);
		// Format the file size to two decimal places
		String fileSizeMBString = String.format("%.2f", fileSizeMB);

		// Update the status label to show the file size only
		statusLabel.setText("File size: " + fileSizeMBString + " MB");

		// Update the name label to show the new file name of the sequence
		nameLabel.setText("Sequence: " + filepath);

		// Update the statistics panel
		rightPanel.remove(statsPanel);
		statsPanel = createStatsPanel(es);
		rightPanel.add(statsPanel, 0);

		// Update the top-K panel
		rightPanel.remove(topKPanel);
		topKPanel = createTopKPanel(es);
		rightPanel.add(topKPanel, 1);

		// Refresh the right panel
		rightPanel.revalidate();
		rightPanel.repaint();
	}

	/** The table model for this tool */
	public class EventSequenceTableModel implements TableModel {

		// The EventSequence object that holds the data
		private EventSequence es;
		// The list of listeners for this table model
		private List<TableModelListener> listeners;

		// The constructor that takes an EventSequence object as a parameter
		public EventSequenceTableModel(EventSequence es) {
			// Initialize the fields
			this.es = es;
			this.listeners = new ArrayList<TableModelListener>();
		}

		// The method that returns the number of rows in the table
		public int getRowCount() {
			// Return three, since there are event type, and timestamp
			return 2;
		}

		// The method that returns the number of columns in the table
		public int getColumnCount() {
			// Return the number of events in the sequence plus one for the first column
			return es.size() + 1;
		}

		// The method that returns the name of the column at the given index
		public String getColumnName(int columnIndex) {
			// If the column index is zero, return an empty string
			if (columnIndex == 0) {
				return "";
			}
			// Otherwise, return the index of the event minus one
			return String.valueOf(columnIndex - 1);
		}

		// The method that returns the class of the values in the column at the given
		// index
		public Class<?> getColumnClass(int columnIndex) {
			// Return the class of String, since the table will display event values or null
			// values
			return String.class;
		}

		// The method that returns the value at the given row and column index
		public Object getValueAt(int rowIndex, int columnIndex) {
			// If the column index is zero, return the row name
			if (columnIndex == 0) {
				switch (rowIndex) {
				case 0:
					return "Event Type";
				case 1:
					return "Timestamp";
				default:
					return null;
				}
			}
			// Otherwise, get the event at the given column index minus one
			Event event = es.get(columnIndex - 1);
			// Return the value of the event based on the row index
			switch (rowIndex) {
			case 0:
				return getItemName(event.getItem());
			case 1:
				return Long.toString(event.getTimestamp());
			default:
				return null;
			}
		}

		// The method that returns whether the cell at the given row and column index is
		// editable
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			// Return false, since the table is not editable
			return false;
		}

		// The method that sets the value at the given row and column index
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			// Do nothing, since the table is not editable
		}

		// The method that adds a listener to this table model
		public void addTableModelListener(TableModelListener l) {
			// Add the listener to the list
			listeners.add(l);
		}

		// The method that removes a listener from this table model
		public void removeTableModelListener(TableModelListener l) {
			// Remove the listener from the list
			listeners.remove(l);
		}

		/**
		 * Get the item name for a given column index
		 * 
		 * @param itemID the item
		 * @return the item name
		 */
		private String getItemName(int itemID) {
			String itemName = es.getNameForItem(itemID);
			if (itemName != null) {
				return itemName;
			} else {

				return "Item " + itemID;
			}
		}
	}

	// The method that draws a timeline chart using the Graphics2D class
	public void drawTimeline(Graphics2D g2d) {
		// Get the width and height of the frame
		int width = getWidth();
		int height = getHeight();
		// Set the margin for the chart
		int margin = 50;
		// Set the font for the chart
		g2d.setFont(new Font("Arial", Font.PLAIN, 12));
		// Set the color for the chart
		g2d.setColor(Color.BLACK);
		// Draw the x-axis and the y-axis
		g2d.drawLine(margin, height - margin, width - margin, height - margin);
		g2d.drawLine(margin, margin, margin, height - margin);
		// Draw the x-axis label
		g2d.drawString("Time (s)", width / 2, height - margin / 2);
		// Draw the y-axis label
		g2d.rotate(-Math.PI / 2);
		g2d.drawString("Event Type", -height / 2, margin / 2);
		g2d.rotate(Math.PI / 2);
		// Get the minimum and maximum timestamps in the sequence
		long minTimestamp = es.getMinTimestamp();
		long maxTimestamp = es.getMaxTimestamp();
		// Calculate the scale factor for the x-axis
		double xScale = (double) (width - 2 * margin) / (maxTimestamp - minTimestamp);
		// Get the unique events in the sequence
		List<String> uniqueEvents = new ArrayList<String>();
		for (Integer elm : es.getUniqueEvents()) {
			uniqueEvents.add("" + elm);
		}
		// Calculate the scale factor for the y-axis
		double yScale = (double) (height - 2 * margin) / uniqueEvents.size();
		// Draw the x-axis ticks and labels
		for (int i = 0; i <= 10; i++) {
			// Calculate the x-coordinate of the tick
			int x = margin + (int) (i * (width - 2 * margin) / 10.0);
			// Draw the tick
			g2d.drawLine(x, height - margin - 5, x, height - margin + 5);
			// Calculate the corresponding timestamp
			long timestamp = minTimestamp + (long) (i * (maxTimestamp - minTimestamp) / 10.0);
			// Format the timestamp to seconds
			String timestampString = String.format("%.2f", timestamp / 1000.0);
			// Draw the label
			g2d.drawString(timestampString, x - 10, height - margin + 20);
		}
		// Draw the y-axis ticks and labels
		for (int i = 0; i < uniqueEvents.size(); i++) {
			// Get the event type
			String eventType = uniqueEvents.get(i);
			// Calculate the y-coordinate of the tick
			int y = margin + (int) ((i + 0.5) * yScale);
			// Draw the tick
			g2d.drawLine(margin - 5, y, margin + 5, y);
			// Draw the label
			g2d.drawString(eventType, margin - 30, y + 5);
		}
		// Draw the points and lines for the events in the sequence
		for (int i = 0; i < es.size(); i++) {
			// Get the event
			Event event = es.get(i);
			// Get the event type and the timestamp
			String eventType = "" + event.getItem();
			long timestamp = event.getTimestamp();
			// Calculate the x-coordinate and the y-coordinate of the point
			int x = margin + (int) ((timestamp - minTimestamp) * xScale);
			int y = margin + (int) ((uniqueEvents.indexOf(eventType) + 0.5) * yScale);
			// Draw the point
			g2d.fillOval(x - 3, y - 3, 6, 6);
			// If this is not the first event, draw a line to connect with the previous
			// event
			if (i > 0) {
				// Get the previous event
				Event prevEvent = es.get(i - 1);
				// Get the previous event type and the previous timestamp
				String prevEventType = "" + prevEvent.getItem();
				long prevTimestamp = prevEvent.getTimestamp();
				// Calculate the x-coordinate and the y-coordinate of the previous point
				int prevX = margin + (int) ((prevTimestamp - minTimestamp) * xScale);
				int prevY = margin + (int) ((uniqueEvents.indexOf(prevEventType) + 0.5) * yScale);
				// Draw the line
				g2d.drawLine(prevX, prevY, x, y);
			}
		}
	}
	// // The method that creates and adds the timeline panel to the frame
	// public void addTimelinePanel() {
	// // Create a new JPanel object with a preferred size
	// timelinePanel = new JPanel() {
	// @Override
	// protected void paintComponent(Graphics g) {
	// // Call the super method
	// super.paintComponent(g);
	// // Cast the Graphics object to a Graphics2D object
	// Graphics2D g2d = (Graphics2D) g;
	// // Call the drawTimeline method and pass the Graphics2D object as a parameter
	// drawTimeline(g2d);
	// }
	// };
	// timelinePanel.setPreferredSize(new Dimension(300, 600));
	// // Override the paintComponent method of the JPanel class
	//
	// // Add the timeline panel to the frame using the BorderLayout.EAST position
	// add(timelinePanel, BorderLayout.SOUTH);
	// }

	// // The method that adds a listener to the table model to repaint the timeline panel
	// public void addTableModelListener() {
	// // Get the table model
	// TableModel model = table.getModel();
	// // Add a table model listener to the model
	// model.addTableModelListener(new TableModelListener() {
	// @Override
	// public void tableChanged(TableModelEvent e) {
	// // Repaint the timeline panel
	// timelinePanel.repaint();
	// }
	// });
	// }

}