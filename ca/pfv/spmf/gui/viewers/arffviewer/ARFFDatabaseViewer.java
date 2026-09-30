package ca.pfv.spmf.gui.viewers.arffviewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
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
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.MainWindow;
import ca.pfv.spmf.gui.SortableJTable;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.gui.viewers.arffviewer.ARFFDatabase.ARFFAttribute;
import ca.pfv.spmf.gui.viewers.arffviewer.ARFFDatabase.AttributeType;
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
 * A viewer for ARFF database files with a split-pane layout showing a data
 * table on the left and a statistics/attribute diversity panel on the right.
 * The UI organization mirrors that of TransactionDatabaseViewer.
 * <p>
 * Numeric columns are sorted numerically, missing values are shown in gray,
 * and a Tools menu gives access to per-attribute details, a histogram of
 * distinct value counts, and the warnings produced while loading.
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class ARFFDatabaseViewer {

	/** The number of top-K attributes to display */
	private static final int TOP_K_COUNT = 5;
	/** The number of bottom-K attributes to display */
	private static final int BOTTOM_K_COUNT = 5;
	/** Preferred width of the right (statistics) panel */
	private static final int RIGHT_PANEL_WIDTH = 300;
	/** Size of the split pane divider */
	private static final int DIVIDER_SIZE = 8;

	/** The table */
	private final JTable table;
	/** The status label */
	private final JLabel statusLabel;
	/** The name label */
	private final JLabel nameLabel;
	/** The frame */
	private final JFrame frame;
	/** The current database (null if none is loaded) */
	private ARFFDatabase currentDatabase;
	/** The path of the current database (null if none is loaded) */
	private String currentFilePath;
	/** The statistics panel */
	private JPanel statsPanel;
	/** The top-K attributes panel */
	private JPanel topKPanel;
	/** The right side panel containing stats and top-K */
	private final JPanel rightPanel;
	/** The scroll pane wrapping the right panel */
	private final JScrollPane rightScrollPane;
	/** The split pane separating table and right panel */
	private final JSplitPane splitPane;
	/** Whether the right panel is currently visible */
	private boolean rightPanelVisible = true;
	/** The saved divider location when hiding the right panel */
	private int savedDividerLocation;
	/** Menu item for toggling the statistics panel */
	private JCheckBoxMenuItem showStatsPanelMenuItem;
	/** Menu item for showing the load warnings (enabled only if there are some) */
	private JMenuItem warningsMenuItem;

	/**
	 * Constructor
	 * 
	 * @param runAsStandaloneProgram if true, closing the window closes the program
	 * @param filePath               the path to the database file to open, or null
	 */
	public ARFFDatabaseViewer(boolean runAsStandaloneProgram, String filePath) {
		frame = new JFrame("SPMF ARFF Database Viewer " + Main.SPMF_VERSION);
		frame.setDefaultCloseOperation(
				runAsStandaloneProgram ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
		frame.setJMenuBar(createMenuBar());

		// ---- the table ----
		table = new SortableJTable();
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setFillsViewportHeight(true);
		ARFFCellRenderer renderer = new ARFFCellRenderer();
		table.setDefaultRenderer(Object.class, renderer);
		table.setDefaultRenderer(Number.class, renderer);
		table.setDefaultRenderer(Double.class, renderer);

		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

		// ---- the right side panel ----
		rightPanel = new JPanel();
		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
		rightPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		statsPanel = createPlaceholderPanel("Summary Statistics", "Load a database to view statistics");
		rightPanel.add(statsPanel);
		topKPanel = createPlaceholderPanel("Attribute Diversity", "Load a database to view attribute diversity");
		rightPanel.add(topKPanel);

		rightScrollPane = new JScrollPane(rightPanel);
		rightScrollPane.setPreferredSize(new Dimension(RIGHT_PANEL_WIDTH - 20, 400));
		rightScrollPane.setMinimumSize(new Dimension(200, 100));
		rightScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		// ---- split pane ----
		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, rightScrollPane);
		splitPane.setResizeWeight(1.0); // extra space goes to the table
		splitPane.setOneTouchExpandable(true);
		splitPane.setDividerSize(DIVIDER_SIZE);
		frame.add(splitPane, BorderLayout.CENTER);

		// ---- status bar ----
		JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		statusLabel = new JLabel(" ");
		statusPanel.add(statusLabel);
		frame.add(statusPanel, BorderLayout.SOUTH);

		// ---- top panel ----
		JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		nameLabel = new JLabel("Database: (none)");
		topPanel.add(nameLabel);
		frame.add(topPanel, BorderLayout.NORTH);

		// Size and show the frame (do not call pack(): it would undo setSize())
		frame.setSize(1100, 600);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		splitPane.setDividerLocation(frame.getWidth() - RIGHT_PANEL_WIDTH);

		// Load the file if a path was provided (after the frame is visible so that
		// error dialogs have a parent)
		if (filePath != null) {
			openDatabaseFile(filePath);
		}
	}

	// =====================================================================
	// Menus
	// =====================================================================

	private JMenuBar createMenuBar() {
		JMenuBar menuBar = new JMenuBar();
		menuBar.add(createFileMenu());
		menuBar.add(createViewMenu());
		menuBar.add(createToolsMenu());
		return menuBar;
	}

	private JMenu createFileMenu() {
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);

		JMenuItem openItem = new JMenuItem("Open Database...");
		openItem.setMnemonic(KeyEvent.VK_O);
		openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK));
		openItem.setIcon(loadIcon("/ca/pfv/spmf/gui/icons/Open24.gif"));
		openItem.addActionListener(e -> chooseAFile());
		fileMenu.add(openItem);

		JMenuItem reloadItem = new JMenuItem("Reload");
		reloadItem.setMnemonic(KeyEvent.VK_R);
		reloadItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
		reloadItem.addActionListener(e -> {
			if (currentFilePath != null) {
				openDatabaseFile(currentFilePath);
			}
		});
		fileMenu.add(reloadItem);

		fileMenu.addSeparator();

		JMenuItem closeItem = new JMenuItem("Close");
		closeItem.setMnemonic(KeyEvent.VK_C);
		closeItem.addActionListener(
				e -> frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING)));
		fileMenu.add(closeItem);

		return fileMenu;
	}

	private JMenu createViewMenu() {
		JMenu viewMenu = new JMenu("View");
		viewMenu.setMnemonic(KeyEvent.VK_V);

		showStatsPanelMenuItem = new JCheckBoxMenuItem("Show Statistics Panel", true);
		showStatsPanelMenuItem.setMnemonic(KeyEvent.VK_S);
		showStatsPanelMenuItem.addActionListener(e -> toggleStatisticsPanel());
		viewMenu.add(showStatsPanelMenuItem);

		return viewMenu;
	}

	private JMenu createToolsMenu() {
		JMenu toolsMenu = new JMenu("Tools");
		toolsMenu.setMnemonic(KeyEvent.VK_T);

		JMenuItem detailsItem = new JMenuItem("View Attribute Details...");
		detailsItem.setMnemonic(KeyEvent.VK_D);
		detailsItem.addActionListener(e -> showAttributeDetails());
		toolsMenu.add(detailsItem);

		JMenuItem attrDistItem = new JMenuItem("View Attribute Distinct Value Distribution");
		attrDistItem.setMnemonic(KeyEvent.VK_A);
		attrDistItem.setIcon(loadIcon("/ca/pfv/spmf/gui/icons/histogram.png"));
		attrDistItem.addActionListener(e -> viewAttributeDistinctValueDistribution());
		toolsMenu.add(attrDistItem);

		toolsMenu.addSeparator();

		warningsMenuItem = new JMenuItem("View Load Warnings...");
		warningsMenuItem.setMnemonic(KeyEvent.VK_W);
		warningsMenuItem.setEnabled(false);
		warningsMenuItem.addActionListener(e -> showLoadWarnings());
		toolsMenu.add(warningsMenuItem);

		return toolsMenu;
	}

	/** Load an icon from the classpath, returning null (instead of throwing) if absent. */
	private static ImageIcon loadIcon(String resource) {
		URL url = MainWindow.class.getResource(resource);
		return url == null ? null : new ImageIcon(url);
	}

	/**
	 * Toggle the visibility of the statistics panel (right side)
	 */
	private void toggleStatisticsPanel() {
		if (rightPanelVisible) {
			savedDividerLocation = splitPane.getDividerLocation();
			rightScrollPane.setVisible(false);
			splitPane.setDividerSize(0);
			splitPane.setDividerLocation(splitPane.getWidth());
		} else {
			rightScrollPane.setVisible(true);
			splitPane.setDividerSize(DIVIDER_SIZE);
			splitPane.setDividerLocation(savedDividerLocation);
		}
		rightPanelVisible = !rightPanelVisible;
		showStatsPanelMenuItem.setSelected(rightPanelVisible);
		splitPane.revalidate();
		splitPane.repaint();
	}

	// =====================================================================
	// Loading
	// =====================================================================

	/**
	 * Ask the user to choose a file.
	 */
	private void chooseAFile() {
		File path = null;
		String previousPath = PreferencesManager.getInstance().getInputFilePath();
		if (previousPath != null) {
			// The user used SPMF before: start from the last folder used
			path = new File(previousPath);
		} else {
			// First time: show the "examples" folder of the SPMF distribution
			URL main = MainTestApriori_SaveToFile.class.getResource("MainTestApriori_saveToFile.class");
			if (main != null && "file".equalsIgnoreCase(main.getProtocol())) {
				path = new File(main.getPath());
			}
		}

		JFileChooser fc = new JFileChooser(path);
		fc.setFileFilter(new FileNameExtensionFilter("ARFF Files (*.arff)", "arff"));
		if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
			File file = fc.getSelectedFile();
			PreferencesManager.getInstance().setInputFilePath(file.getParent());
			openDatabaseFile(file.getPath());
		}
	}

	/**
	 * Open a database in the viewer. If loading fails, an error is shown and the
	 * currently displayed database (if any) is kept.
	 * 
	 * @param filepath the file path
	 */
	private void openDatabaseFile(String filepath) {
		ARFFDatabase db = new ARFFDatabase();
		long startTime = System.currentTimeMillis();
		frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		try {
			db.loadFile(filepath);
		} catch (Exception ex) {
			String message = "Could not load the file:\n" + filepath + "\n\n" + ex.getMessage();
			JOptionPane.showMessageDialog(frame, message, "Error loading ARFF file", JOptionPane.ERROR_MESSAGE);
			return;
		} finally {
			frame.setCursor(Cursor.getDefaultCursor());
		}
		long loadTime = System.currentTimeMillis() - startTime;

		currentDatabase = db;
		currentFilePath = filepath;

		// Update the table
		table.setModel(new ARFFTableModel(db));
		configureColumnWidths(db);

		// Update the labels
		String relation = db.getRelationName().isEmpty() ? "" : "    |    Relation: " + db.getRelationName();
		nameLabel.setText("Database: " + filepath + relation);

		double fileSizeMB = new File(filepath).length() / (1024.0 * 1024.0);
		StringBuilder status = new StringBuilder();
		status.append(String.format("File size: %.2f MB", fileSizeMB));
		status.append("    |    Records: ").append(db.size());
		status.append("    |    Attributes: ").append(db.getAttributeCount());
		status.append("    |    Loaded in ").append(loadTime).append(" ms");
		if (db.getWarningCount() > 0) {
			status.append("    |    ").append(db.getWarningCount())
					.append(" warning(s) - see Tools > View Load Warnings");
		}
		statusLabel.setText(status.toString());
		warningsMenuItem.setEnabled(db.getWarningCount() > 0);

		// Update the right panel
		rightPanel.remove(statsPanel);
		statsPanel = createStatsPanel(db);
		rightPanel.add(statsPanel, 0);
		rightPanel.remove(topKPanel);
		topKPanel = createTopKPanel(db);
		rightPanel.add(topKPanel, 1);
		rightPanel.revalidate();
		rightPanel.repaint();
	}

	/**
	 * Give each column a reasonable width based on its header.
	 */
	private void configureColumnWidths(ARFFDatabase db) {
		if (table.getColumnCount() == 0) {
			return;
		}
		TableColumn rowColumn = table.getColumnModel().getColumn(0);
		rowColumn.setPreferredWidth(60);
		rowColumn.setMaxWidth(120);
		for (int i = 1; i < table.getColumnCount(); i++) {
			String name = table.getColumnName(i);
			int width = Math.max(80, Math.min(250, name.length() * 8 + 24));
			table.getColumnModel().getColumn(i).setPreferredWidth(width);
		}
	}

	/**
	 * Show a message if no database is loaded.
	 * 
	 * @return true if a database is loaded
	 */
	private boolean ensureDatabaseLoaded() {
		if (currentDatabase == null) {
			JOptionPane.showMessageDialog(frame, "Please open an ARFF database first.", "No database",
					JOptionPane.INFORMATION_MESSAGE);
			return false;
		}
		return true;
	}

	// =====================================================================
	// Statistics panels
	// =====================================================================

	/**
	 * Create a titled panel with a single message (used before a database is loaded)
	 */
	private JPanel createPlaceholderPanel(String title, String message) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder(title));
		panel.add(new JLabel(message));
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

		int numRecords = db.size();
		int numAttributes = db.getAttributeCount();
		long totalCells = (long) numRecords * numAttributes;
		int missingCount = db.getTotalMissingValueCount();

		// === Database Size Section ===
		panel.add(createSectionHeader("Database Size"));
		panel.add(createStatRow("Records", String.valueOf(numRecords)));
		panel.add(createStatRow("Attributes", String.valueOf(numAttributes)));
		panel.add(createStatRow("Total cells", String.valueOf(totalCells)));
		panel.add(createStatRow("Missing values", String.valueOf(missingCount)));
		if (totalCells > 0) {
			panel.add(createStatRow("Missing %", String.format("%.2f%%", missingCount * 100.0 / totalCells)));
		}

		panel.add(createVerticalStrut(12));

		// === Attribute Types Section ===
		panel.add(createSectionHeader("Attribute Types"));
		for (AttributeType type : AttributeType.values()) {
			int count = db.countAttributesOfType(type);
			// Always show the common types; show the rare ones only if present
			boolean common = type == AttributeType.NUMERIC || type == AttributeType.NOMINAL;
			if (common || count > 0) {
				panel.add(createStatRow(type.getLabel(), String.valueOf(count)));
			}
		}

		panel.add(createVerticalStrut(12));

		// === Attribute Value Diversity Section ===
		panel.add(createSectionHeader("Attribute Value Diversity"));
		if (numRecords == 0) {
			panel.add(new JLabel("  No records available"));
			return panel;
		}

		int[] distinctCounts = new int[numAttributes];
		int minDistinct = Integer.MAX_VALUE;
		int maxDistinct = 0;
		long totalDistinct = 0;
		for (int i = 0; i < numAttributes; i++) {
			int count = db.getDistinctValueCount(i);
			distinctCounts[i] = count;
			minDistinct = Math.min(minDistinct, count);
			maxDistinct = Math.max(maxDistinct, count);
			totalDistinct += count;
		}
		double avgDistinct = (double) totalDistinct / numAttributes;
		double sumSquaredDiff = 0;
		for (int count : distinctCounts) {
			sumSquaredDiff += (count - avgDistinct) * (count - avgDistinct);
		}
		double stdDev = Math.sqrt(sumSquaredDiff / numAttributes);

		panel.add(createStatRow("Min distinct values", String.valueOf(minDistinct)));
		panel.add(createStatRow("Max distinct values", String.valueOf(maxDistinct)));
		panel.add(createStatRow("Avg distinct values", String.format("%.2f", avgDistinct)));
		panel.add(createStatRow("Median distinct values", String.format("%.1f", median(distinctCounts))));
		panel.add(createStatRow("Std. deviation", String.format("%.2f", stdDev)));
		panel.add(new JLabel("  (missing values are not counted as distinct)"));

		return panel;
	}

	/** Compute the median of an array of ints (the array is not modified). */
	private static double median(int[] values) {
		if (values.length == 0) {
			return 0;
		}
		int[] sorted = values.clone();
		Arrays.sort(sorted);
		int n = sorted.length;
		return n % 2 == 0 ? (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0 : sorted[n / 2];
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

		int numRecords = db.size();
		int numAttributes = db.getAttributeCount();
		if (numRecords == 0) {
			panel.add(new JLabel("No records available"));
			return panel;
		}

		// Sort attribute indexes by distinct value count (descending), ties by position
		List<Integer> sorted = new ArrayList<>(numAttributes);
		for (int i = 0; i < numAttributes; i++) {
			sorted.add(i);
		}
		sorted.sort((a, b) -> {
			int cmp = Integer.compare(db.getDistinctValueCount(b), db.getDistinctValueCount(a));
			return cmp != 0 ? cmp : Integer.compare(a, b);
		});

		// === Top-K Most Diverse ===
		int topCount = Math.min(TOP_K_COUNT, numAttributes);
		panel.add(createSectionHeader("Top " + topCount + " Most Diverse"));
		panel.add(createVerticalStrut(4));
		panel.add(createAttributeTableHeader());
		panel.add(createVerticalStrut(2));
		for (int i = 0; i < topCount; i++) {
			panel.add(createAttributeRow(db, sorted.get(i)));
		}

		panel.add(createVerticalStrut(16));

		// === Bottom-K Least Diverse ===
		int bottomCount = Math.min(BOTTOM_K_COUNT, numAttributes);
		panel.add(createSectionHeader("Top " + bottomCount + " Least Diverse"));
		panel.add(createVerticalStrut(4));
		panel.add(createAttributeTableHeader());
		panel.add(createVerticalStrut(2));
		for (int i = numAttributes - bottomCount; i < numAttributes; i++) {
			panel.add(createAttributeRow(db, sorted.get(i)));
		}

		return panel;
	}

	/**
	 * Create a section header label with bold formatting
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
	 */
	private JPanel createStatRow(String name, String value) {
		JPanel row = new JPanel(new BorderLayout());
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(new JLabel("  " + name + ":"), BorderLayout.WEST);
		row.add(new JLabel(value + "  "), BorderLayout.EAST);
		return row;
	}

	/**
	 * Create a vertical strut (empty space) for spacing in panels
	 */
	private Component createVerticalStrut(int height) {
		JPanel strut = new JPanel();
		strut.setPreferredSize(new Dimension(0, height));
		strut.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
		strut.setOpaque(false);
		return strut;
	}

	/**
	 * Create a header row for the attribute diversity table
	 */
	private JPanel createAttributeTableHeader() {
		JPanel headerRow = new JPanel(new BorderLayout());
		headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		headerRow.add(createItalicLabel("  Attribute", 0), BorderLayout.WEST);

		JPanel rightHeaderPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		rightHeaderPanel.setOpaque(false);
		rightHeaderPanel.add(createItalicLabel("Distinct", 55));
		rightHeaderPanel.add(createItalicLabel("Div%", 50));
		headerRow.add(rightHeaderPanel, BorderLayout.EAST);

		return headerRow;
	}

	private JLabel createItalicLabel(String text, int width) {
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.ITALIC));
		if (width > 0) {
			label.setPreferredSize(new Dimension(width, 16));
			label.setHorizontalAlignment(SwingConstants.RIGHT);
		}
		return label;
	}

	/**
	 * Create a row displaying an attribute with its distinct value count and
	 * diversity percentage (distinct values / records * 100)
	 * 
	 * @param db             the database
	 * @param attributeIndex the index of the attribute
	 * @return a JPanel containing the formatted row
	 */
	private JPanel createAttributeRow(ARFFDatabase db, int attributeIndex) {
		ARFFAttribute attribute = db.getAttribute(attributeIndex);
		int distinctCount = db.getDistinctValueCount(attributeIndex);
		int missingCount = db.getMissingValueCount(attributeIndex);
		double pct = distinctCount * 100.0 / db.size();

		JPanel row = new JPanel(new BorderLayout());
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		// Truncate long names (full name shown as tooltip)
		String name = attribute.getName();
		String truncatedName = name.length() > 18 ? name.substring(0, 15) + "..." : name;
		JLabel attrNameLabel = new JLabel("  " + truncatedName);
		attrNameLabel.setToolTipText("<html><b>" + escapeHtml(name) + "</b> (" + attribute.getType().getLabel()
				+ ")<br>Distinct values: " + distinctCount + "<br>Missing values: " + missingCount + "</html>");

		JPanel rightItemPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		rightItemPanel.setOpaque(false);

		JLabel countLabel = new JLabel(String.valueOf(distinctCount));
		countLabel.setPreferredSize(new Dimension(55, 16));
		countLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		JLabel pctLabel = new JLabel(String.format("%.1f%%", pct));
		pctLabel.setPreferredSize(new Dimension(50, 16));
		pctLabel.setHorizontalAlignment(SwingConstants.RIGHT);

		rightItemPanel.add(countLabel);
		rightItemPanel.add(pctLabel);

		row.add(attrNameLabel, BorderLayout.WEST);
		row.add(rightItemPanel, BorderLayout.EAST);
		return row;
	}

	private static String escapeHtml(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	// =====================================================================
	// Tools
	// =====================================================================

	/**
	 * Show a dialog listing every attribute with its type and statistics.
	 */
	private void showAttributeDetails() {
		if (!ensureDatabaseLoaded()) {
			return;
		}
		final ARFFDatabase db = currentDatabase;
		final String[] columnNames = { "#", "Name", "Type", "Declaration", "Distinct", "Missing", "Missing %" };
		final Class<?>[] columnClasses = { Integer.class, String.class, String.class, String.class, Integer.class,
				Integer.class, Double.class };

		final Object[][] rows = new Object[db.getAttributeCount()][];
		for (int i = 0; i < rows.length; i++) {
			ARFFAttribute attribute = db.getAttribute(i);
			int missing = db.getMissingValueCount(i);
			double missingPct = db.size() == 0 ? 0 : missing * 100.0 / db.size();
			rows[i] = new Object[] { i + 1, attribute.getName(), attribute.getType().getLabel(),
					attribute.getTypeDeclaration(), db.getDistinctValueCount(i), missing, missingPct };
		}

		JTable detailsTable = new SortableJTable();
		detailsTable.setModel(new AbstractTableModel() {
			private static final long serialVersionUID = 1L;

			@Override
			public int getRowCount() {
				return rows.length;
			}

			@Override
			public int getColumnCount() {
				return columnNames.length;
			}

			@Override
			public String getColumnName(int column) {
				return columnNames[column];
			}

			@Override
			public Class<?> getColumnClass(int column) {
				return columnClasses[column];
			}

			@Override
			public Object getValueAt(int rowIndex, int columnIndex) {
				return rows[rowIndex][columnIndex];
			}
		});
		detailsTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		detailsTable.setFillsViewportHeight(true);
		detailsTable.getColumnModel().getColumn(0).setPreferredWidth(50);
		detailsTable.getColumnModel().getColumn(1).setPreferredWidth(200);
		detailsTable.getColumnModel().getColumn(2).setPreferredWidth(80);
		detailsTable.getColumnModel().getColumn(3).setPreferredWidth(250);
		detailsTable.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
			private static final long serialVersionUID = 1L;
			{
				setHorizontalAlignment(SwingConstants.RIGHT);
			}

			@Override
			protected void setValue(Object value) {
				setText(value == null ? "" : String.format("%.2f%%", ((Number) value).doubleValue()));
			}
		});

		String title = "Attribute Details" + (db.getRelationName().isEmpty() ? "" : " - " + db.getRelationName());
		JDialog dialog = new JDialog(frame, title, false);
		dialog.add(new JScrollPane(detailsTable), BorderLayout.CENTER);
		dialog.setSize(800, 400);
		dialog.setLocationRelativeTo(frame);
		dialog.setVisible(true);
	}

	/**
	 * View the distribution of distinct value counts per attribute using a
	 * histogram window
	 */
	private void viewAttributeDistinctValueDistribution() {
		if (!ensureDatabaseLoaded()) {
			return;
		}
		ARFFDatabase db = currentDatabase;
		int numAttributes = db.getAttributeCount();

		int[] xValues = new int[numAttributes];
		int[] yValues = new int[numAttributes];
		Map<Integer, String> mapIndexToName = new HashMap<>();
		for (int i = 0; i < numAttributes; i++) {
			xValues[i] = i;
			yValues[i] = db.getDistinctValueCount(i);
			mapIndexToName.put(i, db.getAttribute(i).getName());
		}

		new HistogramDistributionWindow(false, yValues, xValues, "Attribute distinct value count distribution",
				true, true, "Attribute", "Distinct Values", mapIndexToName, Order.ASCENDING_Y);
	}

	/**
	 * Show the warnings produced while loading the current database.
	 */
	private void showLoadWarnings() {
		if (!ensureDatabaseLoaded()) {
			return;
		}
		ARFFDatabase db = currentDatabase;
		if (db.getWarningCount() == 0) {
			JOptionPane.showMessageDialog(frame, "The file was loaded without warnings.", "Load warnings",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		StringBuilder text = new StringBuilder();
		for (String warning : db.getWarnings()) {
			text.append(warning).append('\n');
		}
		if (db.getWarningCount() > db.getWarnings().size()) {
			text.append("... and ").append(db.getWarningCount() - db.getWarnings().size()).append(" more.\n");
		}
		JTextArea area = new JTextArea(text.toString(), 15, 70);
		area.setEditable(false);
		area.setCaretPosition(0);
		JOptionPane.showMessageDialog(frame, new JScrollPane(area),
				"Load warnings (" + db.getWarningCount() + ")", JOptionPane.WARNING_MESSAGE);
	}

	// =====================================================================
	// Table model and renderer
	// =====================================================================

	/**
	 * The table model to display the ARFF records. Column 0 is the (1-based) row
	 * number; the other columns are the attributes. Missing values are returned as
	 * null; numeric attributes are returned as Double so that they sort correctly.
	 */
	static class ARFFTableModel extends AbstractTableModel {
		private static final long serialVersionUID = 1L;

		/** The database that holds the data */
		private final ARFFDatabase db;

		/**
		 * Constructor
		 * 
		 * @param db an ARFF database
		 */
		ARFFTableModel(ARFFDatabase db) {
			this.db = db;
		}

		@Override
		public int getRowCount() {
			return db.size();
		}

		@Override
		public int getColumnCount() {
			return db.getAttributeCount() + 1;
		}

		@Override
		public String getColumnName(int columnIndex) {
			return columnIndex == 0 ? "#" : db.getAttribute(columnIndex - 1).getName();
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			if (columnIndex == 0) {
				return Integer.class;
			}
			return db.isNumericColumn(columnIndex - 1) ? Double.class : String.class;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) {
			if (columnIndex == 0) {
				return rowIndex + 1;
			}
			int attributeIndex = columnIndex - 1;
			String value = db.getRecords().get(rowIndex).get(attributeIndex);
			if (ARFFDatabase.isMissing(value)) {
				return null;
			}
			if (db.isNumericColumn(attributeIndex)) {
				return Double.valueOf(value); // guaranteed parseable by isNumericColumn()
			}
			return value;
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) {
			return false;
		}
	}

	/**
	 * A cell renderer that shows missing values (null) as a gray italic "?", and
	 * numbers right-aligned without digit grouping.
	 */
	static class ARFFCellRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;

		private final DecimalFormat numberFormat = new DecimalFormat("0.##########");
		private Font baseFont;
		private Font italicFont;

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
				boolean hasFocus, int row, int column) {
			boolean missing = value == null;
			Object display;
			if (missing) {
				display = ARFFDatabase.MISSING_VALUE;
			} else if (value instanceof Number) {
				display = numberFormat.format(((Number) value).doubleValue());
			} else {
				display = value;
			}
			super.getTableCellRendererComponent(table, display, isSelected, hasFocus, row, column);

			if (baseFont != table.getFont()) {
				baseFont = table.getFont();
				italicFont = baseFont.deriveFont(Font.ITALIC);
			}
			setFont(missing ? italicFont : baseFont);
			setHorizontalAlignment(value instanceof Number ? SwingConstants.RIGHT : SwingConstants.LEFT);
			if (missing && !isSelected) {
				setForeground(Color.GRAY);
			} else {
				setForeground(isSelected ? table.getSelectionForeground() : table.getForeground());
			}
			return this;
		}
	}
}