package ca.pfv.spmf.gui.viewers.timeintervaldbviewer;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.SortableJTable;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.gui.viewers.ItemCooccurrenceHeatmapGenerator;
import ca.pfv.spmf.gui.viewers.ItemCooccurrenceHeatmapGenerator.ItemSetProvider;
import ca.pfv.spmf.gui.visuals.histograms.HistogramDistributionPanel.Order;
import ca.pfv.spmf.gui.visuals.histograms.HistogramDistributionWindow;
import ca.pfv.spmf.gui.visuals.timeline.IntervalT;
import ca.pfv.spmf.gui.visuals.timeline.TimelineViewer;
import ca.pfv.spmf.test.MainTestApriori_simple_saveToFile;

/*
 * Copyright (c) 2008-2024 Philippe Fournier-Viger
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
 * A tool to visualize a time-interval sequence database in SPMF format
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class TimeIntervalDatabaseViewer extends JFrame {

	/** Generated UID */
	private static final long serialVersionUID = 4566970490061070156L;
	/** The table */
	private JTable table;
	/** The scroll pane for the table */
	private JScrollPane scrollPane;
	/** The status label */
	private JLabel statusLabel;
	/** The name label */
	private JLabel nameLabel;
	/** The current database */
	private TimeIntervalDatabase currentDatabase;
	/** The statistics panel */
	private JPanel statsPanel;
	/** The right side panel containing stats */
	private JPanel rightPanel;
	/** The scroll pane wrapping the right panel */
	private JScrollPane rightScrollPane;
	/** The split pane separating table and right panel */
	private JSplitPane splitPane;
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
	public TimeIntervalDatabaseViewer(boolean runAsStandaloneProgram, String filePath) {
		// Initialize the frame title
		super("SPMF Time Interval Database Viewer " + Main.SPMF_VERSION);
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

		// Create a JTable object to display the time interval database
		table = new SortableJTable();
		// Set the auto resize mode to off
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

		// Create a JScrollPane object to wrap the table
		scrollPane = new JScrollPane(table);
		// Set the horizontal and vertical scroll bar policies to always show
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

		// Create the right side panel for statistics
		rightPanel = new JPanel();
		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
		rightPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		// Create the statistics panel
		statsPanel = createEmptyStatsPanel();
		rightPanel.add(statsPanel);

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

		// Create the bottom panel with status bar and timeline button
		JPanel bottomPanel = new JPanel();
		bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));

		// Create the status bar
		JPanel statusPanel = new JPanel();
		statusPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
		statusLabel = new JLabel();
		statusPanel.add(statusLabel);
		bottomPanel.add(statusPanel);

		// Create a JPanel object to hold the timeline viewer button
		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));

		// Create a JButton object for the timeline viewer
		JButton button3 = new JButton("View with Timeline Viewer");
		// Add an action listener to the button
		button3.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				viewAsTimeline();
			}
		});
		button3.setIcon(new ImageIcon(MainWindow.class.getResource("/ca/pfv/spmf/gui/icons/timeline20b.png")));
		buttonPanel.add(button3);
		bottomPanel.add(buttonPanel);

		add(bottomPanel, BorderLayout.SOUTH);

		// Create the top panel with the name label
		JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		nameLabel = new JLabel();
		nameLabel.setText("Database: " + (filePath != null ? filePath : "(none)"));
		topPanel.add(nameLabel);
		add(topPanel, BorderLayout.NORTH);

		// Load the file if a path was provided
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
		seqLengthItem.setMnemonic(KeyEvent.VK_L);
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
	 * Get the display name for an item, showing its name if available,
	 * otherwise showing its numeric ID.
	 * 
	 * @param db     the time interval database
	 * @param itemID the item ID
	 * @return a display string such as "ItemName (3)" or "Item 3"
	 */
	private String getDisplayNameForItem(TimeIntervalDatabase db, int itemID) {
		String itemName = db.getNameForItem(itemID);
		if (itemName != null) {
			return itemName + " (" + itemID + ")";
		} else {
			return "Item " + itemID;
		}
	}

	/**
	 * Create and populate the statistics panel with database statistics
	 * 
	 * @param db the time interval database
	 * @return the populated statistics panel
	 */
	private JPanel createStatsPanel(TimeIntervalDatabase db) {
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

		for (TimeIntervalSequence sequence : db.getSequences()) {
			int len = sequence.size();
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
		Set<Integer> uniqueItemSet = new HashSet<Integer>();
		for (TimeIntervalSequence sequence : db.getSequences()) {
			for (SymbolicTimeInterval interval : sequence.getTimeIntervals()) {
				uniqueItemSet.add(interval.symbol);
			}
		}
		int uniqueItems = uniqueItemSet.size();

		// Calculate density
		double density = (uniqueItems > 0) ? (double) totalLength / (db.size() * uniqueItems) : 0;

		// Count singleton sequences (length = 1)
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

		// Calculate time span statistics
		long minTimeSpan = Long.MAX_VALUE;
		long maxTimeSpan = 0;
		long totalTimeSpan = 0;
		int sequencesWithIntervals = 0;
		for (TimeIntervalSequence sequence : db.getSequences()) {
			if (sequence.size() > 0) {
				long seqMinStart = Long.MAX_VALUE;
				long seqMaxEnd = Long.MIN_VALUE;
				for (SymbolicTimeInterval interval : sequence.getTimeIntervals()) {
					if (interval.start < seqMinStart) {
						seqMinStart = interval.start;
					}
					if (interval.end > seqMaxEnd) {
						seqMaxEnd = interval.end;
					}
				}
				long span = seqMaxEnd - seqMinStart;
				totalTimeSpan += span;
				sequencesWithIntervals++;
				if (span < minTimeSpan) {
					minTimeSpan = span;
				}
				if (span > maxTimeSpan) {
					maxTimeSpan = span;
				}
			}
		}
		double avgTimeSpan = (sequencesWithIntervals > 0) ? (double) totalTimeSpan / sequencesWithIntervals : 0;

		// === Database Size Section ===
		panel.add(createSectionHeader("Database Size"));
		panel.add(createStatRow("Sequences", String.valueOf(db.size())));
		panel.add(createStatRow("Unique items", String.valueOf(uniqueItems)));
		panel.add(createStatRow("Total interval count", String.valueOf(totalLength)));
		panel.add(createStatRow("Density", String.format("%.4f", density)));
		panel.add(createStatRow("Min item", getDisplayNameForItem(db, db.minItem)));
		panel.add(createStatRow("Max item", getDisplayNameForItem(db, db.maxItem)));

		panel.add(createVerticalStrut(12));

		// === Sequence Length Section ===
		panel.add(createSectionHeader("Sequence Length (intervals)"));
		panel.add(createStatRow("Minimum", String.valueOf(minLength)));
		panel.add(createStatRow("Maximum", String.valueOf(maxLength)));
		panel.add(createStatRow("Average", String.format("%.2f", avgLength)));
		panel.add(createStatRow("Median", String.format("%.1f", medianLength)));
		panel.add(createStatRow("Std. deviation", String.format("%.2f", stdDev)));

		panel.add(createVerticalStrut(12));

		// === Time Span Section ===
		if (sequencesWithIntervals > 0) {
			panel.add(createSectionHeader("Time Span"));
			panel.add(createStatRow("Min time span", String.valueOf(minTimeSpan)));
			panel.add(createStatRow("Max time span", String.valueOf(maxTimeSpan)));
			panel.add(createStatRow("Avg time span", String.format("%.2f", avgTimeSpan)));

			panel.add(createVerticalStrut(12));
		}

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

		JLabel rowNameLabel = new JLabel("  " + name + ":");
		JLabel valueLabel = new JLabel(value + "  ");

		row.add(rowNameLabel, BorderLayout.WEST);
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
	 * View using the timeline viewer
	 */
	protected void viewAsTimeline() {
	    if (table.getModel() instanceof TimeIntervalTableModel) {
	        TimeIntervalDatabase db = ((TimeIntervalTableModel) table.getModel()).db;

	        // Create a list of Events
	        List<IntervalT> intervals = new ArrayList<>();
	        int i=0;
	        for (TimeIntervalSequence sequence : db.getSequences()) {
	            for (SymbolicTimeInterval interval : sequence.getTimeIntervals()) {
	                String name =  db.getMapItemToStringValues().get(interval.symbol);
	                if(name == null) {
	                	name = Integer.toString(interval.symbol);
	                }
	                long startTime = interval.start;
	                long endTime = interval.end;
	                // Assuming EventT constructor can take start and end times
	                intervals.add(new IntervalT(name, startTime, endTime, i)); 
	            }
	            i++;
	        }

	        // Instantiate the TimelineViewer
	        TimelineViewer timelineViewer = new TimelineViewer(false, null, intervals);

	        // You may need to add additional code here to display the timelineViewer
	    }
	}

	/**
	 * View the distribution of item frequency using a histogram window
	 */
	private void viewItemFrequencyDistribution() {
		if (table.getModel() != null && currentDatabase != null) {
			TimeIntervalDatabase db = currentDatabase;
			Map<Integer, String> mapItemToString = db.getMapItemToStringValues();

			// Find the maximum size
			int maxItem = db.getMaxItemID();

			// Prepare the data (counts)
			int[] yValues = new int[maxItem + 1];
			for (TimeIntervalSequence sequence : db.getSequences()) {
				for (SymbolicTimeInterval item : sequence.getTimeIntervals()) {
					yValues[item.symbol]++;
				}
			}

			// Prepare the X values data
			int[] xValues = new int[maxItem + 1];
			for (int i = 0; i < maxItem + 1; i++) {
				xValues[i] = i;
			}

			new HistogramDistributionWindow(false, yValues, xValues,
					"Item frequency distribution", true, true, "Item", "Count", mapItemToString, Order.ASCENDING_Y);
		}
	}

	/**
	 * View the distribution of sequence length using a histogram window
	 */
	private void viewSequenceLengthDistribution() {
		if (table.getModel() != null && currentDatabase != null) {
			TimeIntervalDatabase db = currentDatabase;

			// Find the maximum size
			int maxSize = 0;
			for (TimeIntervalSequence sequence : db.getSequences()) {
				int size = sequence.size();
				if (size > maxSize) {
					maxSize = size;
				}
			}

			// Prepare the data (counts)
			int[] yValues = new int[maxSize + 1];
			for (TimeIntervalSequence sequence : db.getSequences()) {
				int size = sequence.size();
				yValues[size]++;
			}

			// Prepare the X values data
			int[] xValues = new int[maxSize + 1];
			for (int i = 0; i < maxSize + 1; i++) {
				xValues[i] = i;
			}

			new HistogramDistributionWindow(false, yValues, xValues,
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

		// Create an adapter for the time interval database
		ItemSetProvider provider = new TimeIntervalDatabaseItemSetProvider(currentDatabase);

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
		int result = fc.showOpenDialog(TimeIntervalDatabaseViewer.this);
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
		// Create a new TimeIntervalDatabase object
		TimeIntervalDatabase db = new TimeIntervalDatabase();
		try {
			// Load the file containing the time interval database
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

		// Create a new TimeIntervalTableModel object with the new database
		TimeIntervalTableModel model = new TimeIntervalTableModel(db);
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

		// Refresh the right panel
		rightPanel.revalidate();
		rightPanel.repaint();

		resizeColumnWidth(table);
	}

	/** The table model for this tool */
	public class TimeIntervalTableModel implements TableModel {

		/** The TimeIntervalDatabase object that holds the data */
		private TimeIntervalDatabase db;

		/** The list of listeners for this table model */
		private List<TableModelListener> listeners;

		/** The maximum number of intervals in the database */
		private int maxIntervalCountPerSequence;

		/**
		 * The constructor that takes a TimeIntervalDatabase object as a parameter
		 * 
		 * @param db a TimeIntervalDatabase
		 */
		public TimeIntervalTableModel(TimeIntervalDatabase db) {
			// Initialize the fields
			this.db = db;
			this.listeners = new ArrayList<TableModelListener>();
			// Find the maximum number of intervals per sequence in the database
			maxIntervalCountPerSequence = 0;
			for (TimeIntervalSequence sequence : db.getSequences()) {
				if (sequence.size() > maxIntervalCountPerSequence) {
					maxIntervalCountPerSequence = sequence.size();
				}
			}
		}

		/**
		 * Get the number of rows in the table
		 * 
		 * @return the number of rows
		 */
		public int getRowCount() {
			// Return the number of sequences in the database
			return db.size();
		}

		/**
		 * Get the number of columns in the table
		 * 
		 * @return the number
		 */
		public int getColumnCount() {
			// Return the maximum number of intervals in the database plus one
			return maxIntervalCountPerSequence + 1;
		}

		/**
		 * Get the name of the column at the given index
		 * 
		 * @param columnIndex the index
		 * @return a String
		 */
		public String getColumnName(int columnIndex) {
			// If the column index is zero, return "Sequence"
			if (columnIndex == 0) {
				return "Sequence";
			}
			// Otherwise, return the name of the interval at the given index minus one
			return "Interval " + (columnIndex - 1);
		}

		/**
		 * Get the class of the values in the column at the given index
		 * 
		 * @param columnIndex the index
		 * @return the class
		 */
		public Class<?> getColumnClass(int columnIndex) {
			// If the column index is zero, return the class of Integer, since the SID is an
			// integer
			if (columnIndex == 0) {
				return Integer.class;
			}
			// Otherwise, return the class of String, since the table will display item
			// values or null values
			return String.class;
		}

		/**
		 * The method that returns the value at the given row and column index
		 * 
		 * @param rowIndex    the row index
		 * @param columnIndex the column index
		 * @return the interval as a string or null
		 */
		public Object getValueAt(int rowIndex, int columnIndex) {
			// If the column index is zero, return the row index
			if (columnIndex == 0) {
				return rowIndex;
			}
			// Otherwise, get the sequence at the given row index
			TimeIntervalSequence sequence = db.getSequences().get(rowIndex);
			// Check if the sequence has an item at the given column index minus one
			if (columnIndex <= sequence.size()) {
				// If yes, get the interval at the given column index minus one
				SymbolicTimeInterval interval = sequence.get(columnIndex - 1);
				// Return the interval as a string
				return asString(interval);
			} else {
				// If no, return null
				return null;
			}
		}

		/**
		 * Get a string representation of an interval
		 * 
		 * @param interval an interval
		 * @return the String
		 */
		private String asString(SymbolicTimeInterval interval) {
			StringBuilder builder = new StringBuilder();
			builder.append(getItemName(interval.symbol)).append(' ');
			builder.append('[').append(interval.start).append(',').append(interval.end).append(']');
			return builder.toString();
		}

		/**
		 * The method that returns whether the cell at the given row and column index is
		 * editable
		 * 
		 * @param rowIndex    the row index
		 * @param columnIndex the column index
		 * @return true of false to indicate if it is editable
		 */
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			// Return false, since the table is not editable
			return false;
		}

		/**
		 * Get the item name for a given item ID
		 * 
		 * @param itemID the item
		 * @return the item name
		 */
		private String getItemName(int itemID) {
			String itemName = db.getNameForItem(itemID);
			if (itemName != null) {
				return itemName;
			} else {

				return "Item " + itemID;
			}
		}

		/**
		 * Sets the value at the given row and column index
		 * 
		 * @param rowIndex    the row index
		 * @param columnIndex the column index
		 * @param aValue      the value
		 */
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
			// Do nothing, since the table is not editable
		}

		/**
		 * Add a listener to this table model
		 * 
		 * @param l a listener
		 */
		public void addTableModelListener(TableModelListener l) {
			// Add the listener to the list
			listeners.add(l);
		}

		/**
		 * Remove a listener to this table model
		 * 
		 * @param l a listener
		 */
		public void removeTableModelListener(TableModelListener l) {
			// Remove the listener from the list
			listeners.remove(l);
		}
	}
}