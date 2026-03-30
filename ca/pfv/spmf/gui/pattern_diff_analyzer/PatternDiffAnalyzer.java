package ca.pfv.spmf.gui.pattern_diff_analyzer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import ca.pfv.spmf.gui.Main;
import ca.pfv.spmf.gui.preferences.PreferencesManager;
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
 * A GUI window for comparing and contrasting patterns from two SPMF output
 * files. This is the main UI controller class - business logic is delegated to
 * separate classes: - PatternFileReader: handles file I/O and parsing -
 * ContrastEngine: computes contrast patterns - ContrastExporter: exports
 * results to various formats
 * 
 * @author Philippe Fournier-Viger
 */
public class PatternDiffAnalyzer extends JFrame {

	/** Generated serial ID */
	private static final long serialVersionUID = 1L;

	/** Decimal formatter for display */
	private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.####");

	/** Map of contrast method names to their descriptions */
	private static final Map<String, String> METHOD_DESCRIPTIONS = createMethodDescriptions();

	// ==================== UI Components - Instructions ====================
	/** Panel containing instructions (can be hidden) */
	private JPanel instructionsPanel;
	/** Button to show instructions when hidden */
	private JButton btnShowInstructions;

	// ==================== UI Components - Tab 1: File Selection
	// ====================
	/** Text field for file 1 path */
	private JTextField textFieldFile1;
	/** Table for displaying file 1 patterns */
	private JTable tableFile1;
	/** Label showing file 1 pattern count */
	private JLabel labelFile1Count;

	/** Text field for file 2 path */
	private JTextField textFieldFile2;
	/** Table for displaying file 2 patterns */
	private JTable tableFile2;
	/** Label showing file 2 pattern count */
	private JLabel labelFile2Count;

	// ==================== UI Components - Tab 2: Contrast Options
	// ====================
	/** Combo box for selecting measure (shared for both files) */
	private JComboBox<String> comboMeasure;
	/** Combo box for contrast method selection */
	private JComboBox<String> comboContrastMethod;
	/** Text area for method description */
	private JTextArea textAreaMethodDescription;
	/** Text field for threshold value */
	private JTextField textFieldThreshold;
	/** Text field for minimum gap (interval method) */
	private JTextField textFieldMinGap;
	/** Text field for maximum gap (interval method) */
	private JTextField textFieldMaxGap;
	/** Text field for top-K value */
	private JTextField textFieldTopK;
	/** Label for threshold */
	private JLabel labelThreshold;
	/** Label for min gap */
	private JLabel labelMinGap;
	/** Label for max gap */
	private JLabel labelMaxGap;
	/** Label for top-K */
	private JLabel labelTopK;

	// ==================== UI Components - Results ====================
	/** Table for displaying contrast results */
	private JTable tableResults;
	/** Table model for results */
	private ContrastTableModel modelResults;
	/** Label showing results summary */
	private JLabel labelResultsSummary;

	// ==================== UI Components - Main ====================
	/** Tabbed pane for organizing the UI */
	private JTabbedPane tabbedPane;
	/** Panel that holds the show instructions button */
	private JPanel showInstructionsButtonPanel;

	// ==================== Data ====================
	/** Pattern data from file 1 */
	private PatternData patternDataFile1;
	/** Pattern data from file 2 */
	private PatternData patternDataFile2;
	/** Current contrast results */
	private List<ContrastResult> contrastResults;

	// ==================== Services ====================
	/** File reader service */
	private final GenericPatternFileReader fileReader;
	/** Contrast computation engine */
	private final ContrastEngine contrastEngine;
	/** Results exporter */
	private final ContrastExporter exporter;

	/**
	 * Create the map of method descriptions.
	 * 
	 * @return the method descriptions map
	 */
	private static Map<String, String> createMethodDescriptions() {
		Map<String, String> descriptions = new HashMap<>();

		descriptions.put("Absolute Difference",
				"Computes |Value_A - Value_B| for each pattern. "
						+ "Returns patterns where the absolute difference >= threshold. "
						+ "Useful for finding patterns with significant measure changes regardless of direction.");

		descriptions.put("Directional: A > B",
				"Computes (Value_A - Value_B) for each pattern. "
						+ "Returns patterns where A's value exceeds B's by >= threshold. "
						+ "Useful for finding patterns that are stronger/more frequent in dataset A.");

		descriptions.put("Directional: B > A",
				"Computes (Value_B - Value_A) for each pattern. "
						+ "Returns patterns where B's value exceeds A's by >= threshold. "
						+ "Useful for finding patterns that are stronger/more frequent in dataset B.");

		descriptions.put("Relative Ratio (A/B)", "Computes Value_A / Value_B for each pattern. "
				+ "Returns patterns where the ratio >= threshold. "
				+ "Useful for finding patterns relatively more prominent in A (e.g., ratio >= 2 means twice as strong).");

		descriptions.put("Relative Ratio (B/A)",
				"Computes Value_B / Value_A for each pattern. " + "Returns patterns where the ratio >= threshold. "
						+ "Useful for finding patterns relatively more prominent in B.");

		descriptions.put("Minimum Interval Gap",
				"Computes |Value_A - Value_B| for each pattern. "
						+ "Returns patterns where the difference is within [Min Gap, Max Gap]. "
						+ "Useful for finding patterns with moderate (not extreme) differences.");

		descriptions.put("Exclusive in File 1", "Returns patterns that appear ONLY in File A (not in File B). "
				+ "No threshold needed. " + "Useful for finding patterns unique to dataset A.");

		descriptions.put("Exclusive in File 2", "Returns patterns that appear ONLY in File B (not in File A). "
				+ "No threshold needed. " + "Useful for finding patterns unique to dataset B.");

		descriptions.put("Symmetric Difference",
				"Returns patterns that appear in exactly one file (either A or B, but not both). "
						+ "No threshold needed. " + "Useful for finding all patterns unique to either dataset.");

		descriptions.put("Fold Change", "Computes Value_A / Value_B for each pattern. "
				+ "Returns patterns where ratio >= threshold OR ratio <= 1/threshold. "
				+ "Common in bioinformatics; threshold=2 finds patterns with 2-fold change in either direction.");

		descriptions.put("Top-K Contrast",
				"Computes |Value_A - Value_B| for all patterns. "
						+ "Returns the K patterns with the largest absolute differences. "
						+ "Useful for finding the most contrasting patterns without setting a threshold.");

		return descriptions;
	}

	/**
	 * Constructor - creates and displays the Contrast Pattern Comparator window.
	 * 
	 * @param runAsStandaloneProgram if the viewer is run as a standalone program.
	 */
	public PatternDiffAnalyzer(boolean runAsStandaloneProgram) {

		if (runAsStandaloneProgram) {
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}



		// Initialize services
		this.fileReader = new GenericPatternFileReader();
		this.contrastEngine = new ContrastEngine();
		this.exporter = new ContrastExporter();

		// Initialize UI
		initializeWindow();
		initializeComponents();
		setupEventHandlers();
		finalizeWindow();
	}

	// ========================================================================
	// INITIALIZATION METHODS
	// ========================================================================

	/**
	 * Initialize the main window properties.
	 */
	private void initializeWindow() {
		setTitle("SPMF Pattern Diff Analyzer " + Main.SPMF_VERSION);
		setSize(1200, 800);
		setMinimumSize(new Dimension(900, 600));
		setLocationRelativeTo(null);
		getContentPane().setLayout(new BorderLayout(5, 5));
		
		Image icon = new ImageIcon(
			    getClass().getResource("/ca/pfv/spmf/gui/icons/viewdatatwice24.png")
			).getImage();
		setIconImage(icon);
	}

	/**
	 * Initialize all GUI components.
	 */
	private void initializeComponents() {
		// Create top panel that will hold instructions or the show button
		JPanel topPanel = new JPanel(new BorderLayout());

		// Create instructions panel (closeable)
		instructionsPanel = createInstructionsPanel();
		topPanel.add(instructionsPanel, BorderLayout.CENTER);

		// Create the "Show Instructions" button panel (initially hidden)
		showInstructionsButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
		btnShowInstructions = new JButton("? Show Instructions");
		btnShowInstructions.setFont(btnShowInstructions.getFont().deriveFont(11f));
		btnShowInstructions.addActionListener(e -> showInstructions());
		showInstructionsButtonPanel.add(btnShowInstructions);
		showInstructionsButtonPanel.setVisible(false);
		topPanel.add(showInstructionsButtonPanel, BorderLayout.SOUTH);

		getContentPane().add(topPanel, BorderLayout.NORTH);

		// Create tabbed pane
		tabbedPane = new JTabbedPane();

		// Tab 1: File Selection
		JPanel fileSelectionTab = createFileSelectionTab();
		tabbedPane.addTab("1. Select Files", fileSelectionTab);

		// Tab 2: Compute Contrast
		JPanel contrastTab = createContrastTab();
		tabbedPane.addTab("2. Compute Contrast", contrastTab);

		getContentPane().add(tabbedPane, BorderLayout.CENTER);
	}

	/**
	 * Create the instructions panel at the top of the window.
	 * 
	 * @return the instructions panel
	 */
	private JPanel createInstructionsPanel() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

		// Instructions content
		JPanel contentPanel = new JPanel(new BorderLayout());
		contentPanel.setBackground(new Color(240, 248, 255));
		contentPanel
				.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(100, 149, 237)),
						BorderFactory.createEmptyBorder(8, 10, 8, 10)));

		// Title and close button row
		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setOpaque(false);

		JLabel titleLabel = new JLabel("How to Use the Pattern Diff Analyzer");
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
		titleLabel.setForeground(new Color(25, 25, 112));
		headerPanel.add(titleLabel, BorderLayout.WEST);

		JButton btnClose = new JButton("✕");
		btnClose.setFont(btnClose.getFont().deriveFont(12f));
		btnClose.setMargin(new Insets(0, 5, 0, 5));
		btnClose.setFocusPainted(false);
		btnClose.setBorderPainted(false);
		btnClose.setContentAreaFilled(false);
		btnClose.setToolTipText("Hide instructions");
		btnClose.addActionListener(e -> hideInstructions());
		headerPanel.add(btnClose, BorderLayout.EAST);

		contentPanel.add(headerPanel, BorderLayout.NORTH);

		// Instructions text
		JLabel instructionsLabel = new JLabel("<html><div style='margin-top: 5px;'>"
				+ "<b>Step 1:</b> In the 'Select Files' tab, load two pattern files (File A and File B) by clicking 'Browse...'<br>"
				+ "<b>Step 2:</b> Switch to the 'Compute Contrast' tab to configure the analysis.<br>"
				+ "<b>Step 3:</b> Select a measure (e.g., support) and choose a contrast method with threshold.<br>"
				+ "<b>Step 4:</b> Click 'Compute Contrast Patterns' and review/export the results." + "</div></html>");
		instructionsLabel.setFont(instructionsLabel.getFont().deriveFont(12f));
		contentPanel.add(instructionsLabel, BorderLayout.CENTER);

		panel.add(contentPanel, BorderLayout.CENTER);

		return panel;
	}

	/**
	 * Hide the instructions panel.
	 */
	private void hideInstructions() {
		instructionsPanel.setVisible(false);
		showInstructionsButtonPanel.setVisible(true);
		revalidate();
		repaint();
	}

	/**
	 * Show the instructions panel.
	 */
	private void showInstructions() {
		instructionsPanel.setVisible(true);
		showInstructionsButtonPanel.setVisible(false);
		revalidate();
		repaint();
	}

	/**
	 * Create the file selection tab (Tab 1).
	 * 
	 * @return the file selection panel
	 */
	private JPanel createFileSelectionTab() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.BOTH;
		gbc.insets = new Insets(5, 5, 5, 5);

		// File 1 Panel
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0.5;
		gbc.weighty = 1.0;
		JPanel file1Panel = createFilePanel(1);
		panel.add(file1Panel, gbc);

		// File 2 Panel
		gbc.gridx = 1;
		JPanel file2Panel = createFilePanel(2);
		panel.add(file2Panel, gbc);

		return panel;
	}

	/**
	 * Create a file loading panel for either file 1 or file 2.
	 * 
	 * @param fileNumber 1 or 2
	 * @return the file panel
	 */
	private JPanel createFilePanel(int fileNumber) {
		JPanel panel = new JPanel(new BorderLayout(5, 5));
		panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(),
				"File " + fileNumber + (fileNumber == 1 ? " (A)" : " (B)"), TitledBorder.LEFT, TitledBorder.TOP));

		// Top section: file path and browse button
		JPanel topPanel = new JPanel(new BorderLayout(5, 5));

		JPanel filePathPanel = new JPanel(new BorderLayout(5, 0));
		JLabel labelPath = new JLabel("Path:");
		JTextField textFieldPath = new JTextField();
		textFieldPath.setEditable(false);
		JButton btnBrowse = new JButton("Browse...");

		filePathPanel.add(labelPath, BorderLayout.WEST);
		filePathPanel.add(textFieldPath, BorderLayout.CENTER);
		filePathPanel.add(btnBrowse, BorderLayout.EAST);

		topPanel.add(filePathPanel, BorderLayout.CENTER);
		panel.add(topPanel, BorderLayout.NORTH);

		// Center: table with scroll pane
		JTable table = new JTable();
		table.setAutoCreateRowSorter(true);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		panel.add(scrollPane, BorderLayout.CENTER);

		// Bottom: pattern count
		JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		JLabel labelCount = new JLabel("Patterns: 0");
		labelCount.setFont(labelCount.getFont().deriveFont(Font.BOLD));
		bottomPanel.add(labelCount);
		panel.add(bottomPanel, BorderLayout.SOUTH);

		// Store references and add listeners based on file number
		if (fileNumber == 1) {
			textFieldFile1 = textFieldPath;
			tableFile1 = table;
			labelFile1Count = labelCount;
			btnBrowse.addActionListener(e -> openFile(1));
		} else {
			textFieldFile2 = textFieldPath;
			tableFile2 = table;
			labelFile2Count = labelCount;
			btnBrowse.addActionListener(e -> openFile(2));
		}

		return panel;
	}

	/**
	 * Create the contrast computation tab (Tab 2).
	 * 
	 * @return the contrast tab panel
	 */
	private JPanel createContrastTab() {
		JPanel panel = new JPanel(new BorderLayout(10, 10));
		panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		// Left side: Options panel
		JPanel optionsPanel = createOptionsPanel();

		// Right side: Results panel
		JPanel resultsPanel = createResultsPanel();

		// Use split pane for options and results
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		splitPane.setLeftComponent(optionsPanel);
		splitPane.setRightComponent(resultsPanel);
		splitPane.setResizeWeight(0.35);
		splitPane.setDividerLocation(400);

		panel.add(splitPane, BorderLayout.CENTER);

		return panel;
	}

	/**
	 * Create the contrast options panel.
	 * 
	 * @return the options panel
	 */
	private JPanel createOptionsPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Contrast Options",
				TitledBorder.LEFT, TitledBorder.TOP));
		panel.setPreferredSize(new Dimension(380, 400));
		panel.setMinimumSize(new Dimension(350, 300));

		// Measure selection (shared for both files)
		JPanel measurePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		measurePanel.add(new JLabel("Measure:"));
		comboMeasure = new JComboBox<>();
		comboMeasure.setPreferredSize(new Dimension(200, 25));
		measurePanel.add(comboMeasure);
		panel.add(measurePanel);

		// Add separator
		panel.add(Box.createVerticalStrut(10));

		// Contrast Method
		JPanel methodPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		methodPanel.add(new JLabel("Contrast Method:"));
		comboContrastMethod = new JComboBox<>(ContrastOptions.CONTRAST_METHODS);
		comboContrastMethod.setPreferredSize(new Dimension(200, 25));
		methodPanel.add(comboContrastMethod);
		panel.add(methodPanel);

		// Method Description
		JPanel descriptionPanel = new JPanel(new BorderLayout(5, 5));
		descriptionPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

		textAreaMethodDescription = new JTextArea(5, 30);
		textAreaMethodDescription.setEditable(false);
		textAreaMethodDescription.setLineWrap(true);
		textAreaMethodDescription.setWrapStyleWord(true);
		textAreaMethodDescription.setBackground(new Color(245, 245, 245));
		textAreaMethodDescription.setFont(textAreaMethodDescription.getFont().deriveFont(11f));
		textAreaMethodDescription.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(Color.LIGHT_GRAY), BorderFactory.createEmptyBorder(5, 5, 5, 5)));

		JScrollPane descScrollPane = new JScrollPane(textAreaMethodDescription);
		descScrollPane.setPreferredSize(new Dimension(350, 100));
		descScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		descScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		descriptionPanel.add(descScrollPane, BorderLayout.CENTER);
		panel.add(descriptionPanel);

		// Threshold
		JPanel thresholdPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		labelThreshold = new JLabel("Threshold:");
		textFieldThreshold = new JTextField("0.0", 10);
		thresholdPanel.add(labelThreshold);
		thresholdPanel.add(textFieldThreshold);
		panel.add(thresholdPanel);

		// Interval Gap (for interval method)
		JPanel gapPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		labelMinGap = new JLabel("Min Gap:");
		textFieldMinGap = new JTextField("0.0", 6);
		labelMaxGap = new JLabel("Max Gap:");
		textFieldMaxGap = new JTextField("1.0", 6);
		gapPanel.add(labelMinGap);
		gapPanel.add(textFieldMinGap);
		gapPanel.add(labelMaxGap);
		gapPanel.add(textFieldMaxGap);
		panel.add(gapPanel);

		// Top-K
		JPanel topKPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		labelTopK = new JLabel("Top-K:");
		textFieldTopK = new JTextField("10", 6);
		topKPanel.add(labelTopK);
		topKPanel.add(textFieldTopK);
		panel.add(topKPanel);

		// Add glue to push buttons down
		panel.add(Box.createVerticalGlue());

		// Buttons panel
		JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

		JButton btnCompute = new JButton("Compute Contrast Patterns");
		btnCompute.addActionListener(e -> computeContrast());
		buttonsPanel.add(btnCompute);

		JButton btnExport = new JButton("Export Results...");
		btnExport.addActionListener(e -> exportResults());
		buttonsPanel.add(btnExport);

		panel.add(buttonsPanel);

		// Initialize visibility and description
		updateOptionFieldsVisibility();
		updateMethodDescription();

		return panel;
	}

	/**
	 * Create the results panel.
	 * 
	 * @return the results panel
	 */
	private JPanel createResultsPanel() {
		JPanel panel = new JPanel(new BorderLayout(5, 5));
		panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Contrast Results",
				TitledBorder.LEFT, TitledBorder.TOP));

		// Results table
		tableResults = new JTable();
		tableResults.setAutoCreateRowSorter(true);
		tableResults.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

		// Custom cell renderer for highlighting
		ContrastResultCellRenderer renderer = new ContrastResultCellRenderer();
		tableResults.setDefaultRenderer(Object.class, renderer);
		tableResults.setDefaultRenderer(Double.class, renderer);
		tableResults.setDefaultRenderer(String.class, renderer);

		JScrollPane scrollPane = new JScrollPane(tableResults);
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		panel.add(scrollPane, BorderLayout.CENTER);

		// Summary label
		labelResultsSummary = new JLabel(
				"No contrast computed yet. Select files in Tab 1, then configure options and click 'Compute'.");
		labelResultsSummary.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		panel.add(labelResultsSummary, BorderLayout.SOUTH);

		return panel;
	}

	/**
	 * Setup event handlers for components.
	 */
	private void setupEventHandlers() {
		// Update option fields visibility and description when contrast method changes
		comboContrastMethod.addActionListener(e -> {
			updateOptionFieldsVisibility();
			updateMethodDescription();
		});

		// Update measure combo box when switching to tab 2
		tabbedPane.addChangeListener(e -> {
			if (tabbedPane.getSelectedIndex() == 1) {
				updateMeasureComboBox();
			}
		});
	}

	/**
	 * Finalize window setup.
	 */
	private void finalizeWindow() {
		validate();
		pack();
		setVisible(true);
	}

	// ========================================================================
	// UI UPDATE METHODS
	// ========================================================================

	/**
	 * Update the visibility of option fields based on selected contrast method.
	 */
	private void updateOptionFieldsVisibility() {
		String method = (String) comboContrastMethod.getSelectedItem();

		// Use ContrastOptions to determine which fields are needed
		ContrastOptions tempOptions = ContrastOptions.builder().contrastMethod(method).build();

		boolean showThreshold = tempOptions.requiresThreshold();
		boolean showGap = tempOptions.requiresGapParameters();
		boolean showTopK = tempOptions.requiresTopK();

		labelThreshold.setVisible(showThreshold);
		textFieldThreshold.setVisible(showThreshold);
		labelMinGap.setVisible(showGap);
		textFieldMinGap.setVisible(showGap);
		labelMaxGap.setVisible(showGap);
		textFieldMaxGap.setVisible(showGap);
		labelTopK.setVisible(showTopK);
		textFieldTopK.setVisible(showTopK);
	}

	/**
	 * Update the method description text area based on selected method.
	 */
	private void updateMethodDescription() {
		String method = (String) comboContrastMethod.getSelectedItem();
		String description = METHOD_DESCRIPTIONS.get(method);

		if (description != null) {
			textAreaMethodDescription.setText(description);
			textAreaMethodDescription.setCaretPosition(0); // Scroll to top
		} else {
			textAreaMethodDescription.setText("No description available for this method.");
		}
	}

	/**
	 * Update measure combo box with common numeric columns from both files.
	 */
	private void updateMeasureComboBox() {
		DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();

		if (patternDataFile1 != null && patternDataFile2 != null) {
			// Find common numeric columns
			List<String> numericColumns1 = patternDataFile1.getNumericColumnNames();
			List<String> numericColumns2 = patternDataFile2.getNumericColumnNames();

			Set<String> commonColumns = new HashSet<>(numericColumns1);
			commonColumns.retainAll(numericColumns2);

			for (String colName : numericColumns1) {
				if (commonColumns.contains(colName)) {
					model.addElement(colName);
				}
			}

			if (model.getSize() == 0) {
				// No common columns, show all from file 1 with a warning
				for (String colName : numericColumns1) {
					model.addElement(colName);
				}
				showWarning("No common measures found between the two files. " + "Using measures from File A.",
						"Warning");
			}
		} else if (patternDataFile1 != null) {
			// Only file 1 loaded
			for (String colName : patternDataFile1.getNumericColumnNames()) {
				model.addElement(colName);
			}
		} else if (patternDataFile2 != null) {
			// Only file 2 loaded
			for (String colName : patternDataFile2.getNumericColumnNames()) {
				model.addElement(colName);
			}
		}

		comboMeasure.setModel(model);
	}

	/**
	 * Adjust column widths based on content.
	 * 
	 * @param table the table to adjust
	 */
	private void adjustColumnWidths(JTable table) {
		for (int column = 0; column < table.getColumnCount(); column++) {
			int width = 50; // Min width

			// Check header width
			String headerValue = table.getColumnName(column);
			width = Math.max(width, headerValue.length() * 8 + 20);

			// Check data width (sample first 100 rows)
			for (int row = 0; row < Math.min(table.getRowCount(), 100); row++) {
				Object value = table.getValueAt(row, column);
				if (value != null) {
					width = Math.max(width, value.toString().length() * 7 + 10);
				}
			}

			width = Math.min(width, 400); // Max width
			table.getColumnModel().getColumn(column).setPreferredWidth(width);
		}
	}

	/**
	 * Update the results table with contrast results.
	 */
	private void updateResultsTable() {
		List<String> columnNames = new ArrayList<>();
		columnNames.add("Pattern");
		columnNames.add("Value (A)");
		columnNames.add("Value (B)");
		columnNames.add("Contrast");
		columnNames.add("Type");

		List<Class<?>> columnClasses = new ArrayList<>();
		columnClasses.add(String.class);
		columnClasses.add(String.class);
		columnClasses.add(String.class);
		columnClasses.add(Double.class);
		columnClasses.add(String.class);

		List<List<Object>> data = new ArrayList<>();

		for (ContrastResult result : contrastResults) {
			List<Object> row = new ArrayList<>();
			row.add(result.getPattern());
			row.add(result.getValueA() != null ? DECIMAL_FORMAT.format(result.getValueA()) : "N/A");
			row.add(result.getValueB() != null ? DECIMAL_FORMAT.format(result.getValueB()) : "N/A");
			row.add(result.getContrastValue());
			row.add(result.getContrastType());
			data.add(row);
		}

		modelResults = new ContrastTableModel(data, columnNames, columnClasses);
		tableResults.setModel(modelResults);
		tableResults.setRowSorter(new TableRowSorter<>(modelResults));
		adjustColumnWidths(tableResults);
	}

	// ========================================================================
	// FILE OPERATIONS
	// ========================================================================

	/**
	 * Browse for and load a file.
	 * 
	 * @param fileNumber 1 or 2
	 */
	private void openFile(int fileNumber) {
		try {
			File path = null;
			String previousPath = PreferencesManager.getInstance().getOutputFilePath();

			if (previousPath == null) {
				URL main = MainTestApriori_simple_saveToFile.class.getResource("MainTestApriori_saveToFile.class");
				if (main != null && "file".equalsIgnoreCase(main.getProtocol())) {
					path = new File(main.getPath());
				}
			} else {
				path = new File(previousPath);
			}

			JFileChooser fc = (path != null) ? new JFileChooser(path.getAbsolutePath()) : new JFileChooser();

			int returnVal = fc.showOpenDialog(this);

			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = fc.getSelectedFile();
				String filePath = file.getPath();

				if (fileNumber == 1) {
					textFieldFile1.setText(filePath);
				} else {
					textFieldFile2.setText(filePath);
				}

				PreferencesManager.getInstance().setOutputFilePath(file.getParent());

				// Load the file immediately after selection
				loadFile(fileNumber);
			}
		} catch (Exception e) {
			showError("Error opening file dialog: " + e.getMessage());
		}
	}

	/**
	 * Load a pattern file.
	 * 
	 * @param fileNumber 1 or 2
	 */
	private void loadFile(int fileNumber) {
		String filePath = (fileNumber == 1) ? textFieldFile1.getText() : textFieldFile2.getText();

		if (filePath == null || filePath.trim().isEmpty()) {
			showWarning("Please select a file first.", "No File Selected");
			return;
		}

		try {
			// Use default matching method (exact match)
			String matchingMethod = "Exact itemset match";

			// Read the file using the file reader service
			PatternData patternData = fileReader.readFile(filePath, matchingMethod);

			// Check if file was empty
			if (patternData.isEmpty()) {
				showWarning("No patterns found in the file.", "Empty File");
				return;
			}

			// Update UI based on file number
			if (fileNumber == 1) {
				patternDataFile1 = patternData;
				updateFilePanel(tableFile1, patternData, labelFile1Count);
			} else {
				patternDataFile2 = patternData;
				updateFilePanel(tableFile2, patternData, labelFile2Count);
			}

		} catch (IOException e) {
			showError("Error reading file: " + e.getMessage());
		}
	}

	/**
	 * Update file panel with loaded data.
	 * 
	 * @param table       the table to update
	 * @param patternData the loaded pattern data
	 * @param labelCount  the count label
	 */
	private void updateFilePanel(JTable table, PatternData patternData, JLabel labelCount) {

		// Create and set table model
		ContrastTableModel model = new ContrastTableModel(patternData.getData(), patternData.getColumnNames(),
				patternData.getColumnClasses());
		table.setModel(model);
		table.setRowSorter(new TableRowSorter<>(model));

		// Adjust column widths
		adjustColumnWidths(table);

		// Update count label
		labelCount.setText("Patterns: " + patternData.getPatternCount());
	}

	// ========================================================================
	// CONTRAST COMPUTATION
	// ========================================================================

	/**
	 * Compute contrast patterns based on selected options.
	 */
	private void computeContrast() {
		// Validate that both files are loaded
		if (patternDataFile1 == null || patternDataFile1.isEmpty()) {
			showWarning("Please load File 1 (A) first in Tab 1.", "Missing Data");
			return;
		}
		if (patternDataFile2 == null || patternDataFile2.isEmpty()) {
			showWarning("Please load File 2 (B) first in Tab 1.", "Missing Data");
			return;
		}

		// Get selected measure (same for both files)
		String measure = (String) comboMeasure.getSelectedItem();

		if (measure == null) {
			showWarning("Please select a measure for contrast computation.", "Missing Measure");
			return;
		}

		// Build contrast options from UI
		ContrastOptions options = buildContrastOptions(measure);

		// Compute contrast using the engine
		contrastResults = contrastEngine.computeContrast(patternDataFile1, patternDataFile2, options);

		// Update results table
		updateResultsTable();

		// Update summary label
		labelResultsSummary.setText(String.format(
				"File A: %d patterns | File B: %d patterns | Matched: %d | Contrast patterns: %d | Time: %d ms",
				patternDataFile1.getPatternCount(), patternDataFile2.getPatternCount(),
				contrastEngine.getMatchedPatternCount(), contrastEngine.getResultCount(),
				contrastEngine.getComputationTimeMs()));
	}

	/**
	 * Build ContrastOptions from current UI settings.
	 * 
	 * @param measure measure name for both files
	 * @return the built options
	 */
	private ContrastOptions buildContrastOptions(String measure) {
		ContrastOptions.Builder builder = ContrastOptions.builder()
				.contrastMethod((String) comboContrastMethod.getSelectedItem()).matchingMethod("Exact itemset match") // Default
																														// matching
																														// method
				.measureName1(measure).measureName2(measure).treatMissingAsZero(true); // Default to true since checkbox
																						// is removed

		// Parse threshold
		try {
			builder.threshold(Double.parseDouble(textFieldThreshold.getText().trim()));
		} catch (NumberFormatException e) {
			builder.threshold(0.0);
		}

		// Parse min/max gap
		try {
			builder.minGap(Double.parseDouble(textFieldMinGap.getText().trim()));
		} catch (NumberFormatException e) {
			builder.minGap(0.0);
		}

		try {
			builder.maxGap(Double.parseDouble(textFieldMaxGap.getText().trim()));
		} catch (NumberFormatException e) {
			builder.maxGap(Double.MAX_VALUE);
		}

		// Parse top-K
		try {
			builder.topK(Integer.parseInt(textFieldTopK.getText().trim()));
		} catch (NumberFormatException e) {
			builder.topK(10);
		}

		return builder.build();
	}

	// ========================================================================
	// EXPORT OPERATIONS
	// ========================================================================

	/**
	 * Export results to a file.
	 */
	private void exportResults() {
		if (contrastResults == null || contrastResults.isEmpty()) {
			showWarning("No results to export. Please compute contrast patterns first.", "No Results");
			return;
		}

		try {
			String previousPath = PreferencesManager.getInstance().getOutputFilePath();
			JFileChooser fc = (previousPath != null) ? new JFileChooser(previousPath) : new JFileChooser();

			// Add file filters
			fc.addChoosableFileFilter(new FileNameExtensionFilter("SPMF Format (*.txt)", "txt"));
			fc.addChoosableFileFilter(new FileNameExtensionFilter("CSV Format (*.csv)", "csv"));
			fc.addChoosableFileFilter(new FileNameExtensionFilter("HTML Format (*.html)", "html"));
			fc.setAcceptAllFileFilterUsed(true);

			int returnVal = fc.showSaveDialog(this);

			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = fc.getSelectedFile();
				PreferencesManager.getInstance().setOutputFilePath(file.getParent());

				// Build options for export
				String measure = (String) comboMeasure.getSelectedItem();
				ContrastOptions options = buildContrastOptions(measure);

				// Determine format based on file extension
				String filePath = file.getPath();
				String lowerPath = filePath.toLowerCase();

				if (lowerPath.endsWith(".csv")) {
					exporter.exportToCSV(filePath, contrastResults, options);
				} else if (lowerPath.endsWith(".html") || lowerPath.endsWith(".htm")) {
					exporter.exportToHTML(filePath, contrastResults, options);
				} else {
					// Default to SPMF format
					if (!lowerPath.endsWith(".txt")) {
						filePath += ".txt";
					}
					exporter.exportToSPMFFormat(filePath, contrastResults, options);
				}

				showInfo("Results exported successfully to:\n" + filePath, "Export Complete");
			}
		} catch (Exception e) {
			showError("Error exporting results: " + e.getMessage());
		}
	}

	// ========================================================================
	// DIALOG HELPER METHODS
	// ========================================================================

	/**
	 * Show an error message dialog.
	 * 
	 * @param message the message
	 */
	private void showError(String message) {
		JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
	}

	/**
	 * Show a warning message dialog.
	 * 
	 * @param message the message
	 * @param title   the dialog title
	 */
	private void showWarning(String message, String title) {
		JOptionPane.showMessageDialog(this, message, title, JOptionPane.WARNING_MESSAGE);
	}

	/**
	 * Show an information message dialog.
	 * 
	 * @param message the message
	 * @param title   the dialog title
	 */
	private void showInfo(String message, String title) {
		JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
	}

	// ========================================================================
	// INNER CLASSES
	// ========================================================================

	/**
	 * Custom cell renderer for highlighting contrast results in the table.
	 */
	private static class ContrastResultCellRenderer extends DefaultTableCellRenderer {

		private static final long serialVersionUID = 1L;

		/** Light red color for exclusive patterns */
		private static final Color LIGHT_RED = new Color(255, 230, 230);
		/** Light green color for patterns in both files */
		private static final Color LIGHT_GREEN = new Color(230, 255, 230);
		/** Light yellow for high contrast */
		private static final Color LIGHT_YELLOW = new Color(255, 255, 230);

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {

			Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

			if (!isSelected && table.getColumnCount() > 4) {
				// Get the type column value (last column)
				Object typeValue = table.getValueAt(row, 4);
				if (typeValue != null) {
					String type = typeValue.toString();
					if (type.contains("Exclusive") || type.contains("Only")) {
						c.setBackground(LIGHT_RED);
					} else if (type.contains("Top-K")) {
						c.setBackground(LIGHT_YELLOW);
					} else {
						c.setBackground(LIGHT_GREEN);
					}
				} else {
					c.setBackground(Color.WHITE);
				}
			}

			// Right-align numeric columns (Value A, Value B, Contrast)
			if (column >= 1 && column <= 3) {
				setHorizontalAlignment(SwingConstants.RIGHT);
			} else {
				setHorizontalAlignment(SwingConstants.LEFT);
			}

			return c;
		}
	}

}