package ca.pfv.spmf.gui.viewers.association_rule_explorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * A tool to explore association rules found by SPMF algorithms: a scatter plot
 * (X, Y, color, size = measures), a linked sortable table with data bars,
 * per-measure range filters, quick filters, item search, an item frequency
 * list, plain-language explanations of rules, statistics, CSV and PNG export.
 *
 * @author Philippe Fournier-Viger
 */
public class AssociationRuleExplorer extends JFrame {

	private static final long serialVersionUID = 1L;

	private static final String NONE = "(none)";
	/** name of the derived relative-support measure (in %) */
	private static final String RSUP = "RSUP";
	private static final int MAX_RECENT_FILES = 6;
	private static final String HINT_TEXT = "Hover: details  \u00b7  click or drag: select  \u00b7  wheel: zoom"
			+ "  \u00b7  right-drag: pan  \u00b7  double-click: fit";
	// ---- data
	private List<ARule> loadedRules = new ArrayList<>();
	private List<String> loadedMeasureNames = new ArrayList<>();
	private List<ARule> allRules = new ArrayList<>();
	private List<ARule> filteredRules = new ArrayList<>();
	private List<String> measureNames = new ArrayList<>();
	private final Map<String, double[]> ranges = new LinkedHashMap<>();
	private String fileType = "";
	private long transactionCount = -1;
	private File lastDirectory;
	private final Preferences prefs = Preferences.userNodeForPackage(AssociationRuleExplorer.class);

	// ---- main views
	private final RuleScatterPanel scatterPanel = new RuleScatterPanel();
	private final RuleTableModel tableModel = new RuleTableModel();
	private final JTable table = new JTable(tableModel) {
		private static final long serialVersionUID = 1L;

		@Override
		protected JTableHeader createDefaultTableHeader() {
			return new JTableHeader(columnModel) {
				private static final long serialVersionUID = 1L;

				@Override
				public String getToolTipText(MouseEvent e) {
					int col = columnModel.getColumnIndexAtX(e.getPoint().x);
					return col < 0 ? null : tableModel.getColumnTooltip(columnModel.getColumn(col).getModelIndex());
				}
			};
		}
	};
	private final RuleDetailPanel detailPanel = new RuleDetailPanel();
	private final ItemFilterPanel itemPanel = new ItemFilterPanel(this::toggleSearchTerm);
	private JScrollPane filterScroll;
	private JScrollPane tableScroll;
	private JSplitPane mainSplit;
	private JSplitPane rightSplit;

	// ---- plot controls
	private final JComboBox<String> xCombo = new JComboBox<>();
	private final JComboBox<String> yCombo = new JComboBox<>();
	private final JComboBox<String> colorCombo = new JComboBox<>();
	private final JComboBox<String> sizeCombo = new JComboBox<>();
	private final JCheckBox logXBox = new JCheckBox("log scale");
	private final JCheckBox logYBox = new JCheckBox("log scale");
	private final JButton swapAxesButton = new JButton("Swap X/Y");
	private final JButton zoomInButton = new JButton("Zoom in");
	private final JButton zoomOutButton = new JButton("Zoom out");
	private final JButton resetZoomButton = new JButton("Reset zoom");

	// ---- filter controls
	private final JTextField searchField = new JTextField(20);
	private final JPanel filtersBox = new JPanel();
	private final List<MeasureRangeFilter> rangeFilters = new ArrayList<>();
	private final JCheckBox positiveOnlyBox = new JCheckBox("Only positive correlations (lift > 1)");
	private final JCheckBox topKBox = new JCheckBox("Keep only the top");
	private final JSpinner topKSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 1000000, 10));
	private final JComboBox<String> topKCombo = new JComboBox<>();
	private final JTextField transactionsField = new JTextField(8);
	private final JLabel statusLabel = new JLabel(" ");

	// ---- menu items whose state changes
	private JMenu recentMenu;
	private JMenuItem exportItem;
	private JMenuItem csvItem;
	private JMenuItem pngItem;
	private JMenuItem copyItem;
	private JMenuItem clearSelectionItem;
	private JMenuItem resetZoomItem;
	private JCheckBoxMenuItem showFiltersItem;
	private JCheckBoxMenuItem showDetailsItem;

	/** guard against feedback loops when components are updated programmatically */
	private boolean updating = false;

	/**
	 * Create the explorer and load a file.
	 * 
	 * @param filePath path to an SPMF association rule file
	 * @throws IOException if the file cannot be read
	 */
	public AssociationRuleExplorer(String filePath) throws IOException {
		this();
		loadFile(filePath);
	}

	/** Create an empty explorer (use File > Open, or drop a file on the window). */
	public AssociationRuleExplorer() {
		super("SPMF Association Rule Explorer");
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		buildUI();
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		setSize(Math.min(1400, (int) (screen.width * 0.92)), Math.min(840, (int) (screen.height * 0.88)));
		restoreWindowBounds();
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				saveWindowBounds();
			}
		});
		updateEnabledStates();
	}

	// =============================================================== UI
	// construction

	private void buildUI() {
		setJMenuBar(buildMenuBar());

		filterScroll = buildFilterPanel();
		JComponent plotPanel = buildPlotPanel();
		tableScroll = buildTablePane();

		rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailPanel);
		rightSplit.setResizeWeight(0.68);
		rightSplit.setOneTouchExpandable(true);

		JSplitPane plotSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, plotPanel, rightSplit);
		plotSplit.setResizeWeight(0.55);
		plotSplit.setOneTouchExpandable(true);
		plotPanel.setMinimumSize(new Dimension(320, 250));

		mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, filterScroll, plotSplit);
		mainSplit.setResizeWeight(0);
		mainSplit.setOneTouchExpandable(true);

		statusLabel.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(mainSplit, BorderLayout.CENTER);
		getContentPane().add(statusLabel, BorderLayout.SOUTH);

		// ---- plot interactions
		scatterPanel.setSelectionListener(list -> {
			selectRulesInTable(list, true);
			detailPanel.setRules(list);
			updateStatus();
		});
		scatterPanel.setPopupHandler((e, under) -> {
			List<ARule> context = scatterPanel.getSelectedRules();
			if (context.isEmpty()) {
				context = under;
			}
			buildPopup(context).show(e.getComponent(), e.getX(), e.getY());
		});
		scatterPanel.setViewListener(this::updateEnabledStates);

		// ---- Esc clears the selection anywhere in the window
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
				"clearSelection");
		getRootPane().getActionMap().put("clearSelection", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				table.clearSelection();
			}
		});

		// ---- drag and drop of a file anywhere on the window
		installFileDrop(getContentPane());
		installFileDrop(scatterPanel);
		installFileDrop(table);
		installFileDrop(filtersBox);
	}

	// --------------------------------------------------------------- menu bar

	private JMenuBar buildMenuBar() {
		JMenuBar bar = new JMenuBar();

		// ---- File
		JMenu file = new JMenu("File");
		file.setMnemonic(KeyEvent.VK_F);
		file.add(item("Open rule file...", KeyEvent.VK_O, ctrl(KeyEvent.VK_O), e -> chooseAndOpen()));
		recentMenu = new JMenu("Open recent");
		recentMenu.setMnemonic(KeyEvent.VK_R);
		file.add(recentMenu);
		rebuildRecentMenu();
		file.addSeparator();
		exportItem = item("Export filtered rules...", KeyEvent.VK_E, ctrl(KeyEvent.VK_E), e -> exportFiltered());
		exportItem.setToolTipText("Save the rules currently shown to a new file in SPMF format");
		file.add(exportItem);
		csvItem = item("Export shown rules as CSV...", KeyEvent.VK_S, ctrlShift(KeyEvent.VK_E),
				e -> exportCsv());
		csvItem.setToolTipText("Save the rules currently shown as a CSV file (opens in Excel / LibreOffice)");
		file.add(csvItem);
		pngItem = item("Export plot as PNG...", KeyEvent.VK_P, null, e -> exportPlotPng());
		pngItem.setToolTipText("Save the current scatter plot as a PNG image");
		file.add(pngItem);
		file.addSeparator();
		file.add(item("Close", KeyEvent.VK_C, ctrl(KeyEvent.VK_W), e -> dispose()));
		bar.add(file);

		// ---- Edit
		JMenu edit = new JMenu("Edit");
		edit.setMnemonic(KeyEvent.VK_E);
		copyItem = item("Copy selected rules", KeyEvent.VK_C, ctrl(KeyEvent.VK_C), e -> copySelectedRules());
		edit.add(copyItem);
		edit.add(item("Select all rules", KeyEvent.VK_A, ctrl(KeyEvent.VK_A), e -> table.selectAll()));
		clearSelectionItem = item("Clear selection", KeyEvent.VK_L, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
				e -> table.clearSelection());
		edit.add(clearSelectionItem);
		edit.addSeparator();
		edit.add(item("Reset all filters", KeyEvent.VK_R, ctrl(KeyEvent.VK_R), e -> resetFilters()));
		bar.add(edit);

		// ---- View
		JMenu view = new JMenu("View");
		view.setMnemonic(KeyEvent.VK_V);
		view.add(item("Zoom in", KeyEvent.VK_I, ctrl(KeyEvent.VK_PLUS), e -> scatterPanel.zoomIn()));
		view.add(item("Zoom out", KeyEvent.VK_O, ctrl(KeyEvent.VK_MINUS), e -> scatterPanel.zoomOut()));
		resetZoomItem = item("Reset zoom", KeyEvent.VK_Z, ctrl(KeyEvent.VK_0), e -> scatterPanel.resetView());
		view.add(resetZoomItem);
		view.add(item("Restore default plot settings", KeyEvent.VK_D, null, e -> restoreDefaultPlot()));
		view.addSeparator();
		showFiltersItem = new JCheckBoxMenuItem("Show filter panel", true);
		showFiltersItem.setMnemonic(KeyEvent.VK_F);
		showFiltersItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F9, 0));
		showFiltersItem.addActionListener(e -> setPanelVisible(filterScroll, mainSplit, showFiltersItem.isSelected()));
		view.add(showFiltersItem);
		showDetailsItem = new JCheckBoxMenuItem("Show rule details", true);
		showDetailsItem.setMnemonic(KeyEvent.VK_R);
		showDetailsItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0));
		showDetailsItem.addActionListener(e -> setPanelVisible(detailPanel, rightSplit, showDetailsItem.isSelected()));
		view.add(showDetailsItem);
		bar.add(view);

		// ---- Help
		JMenu help = new JMenu("Help");
		help.setMnemonic(KeyEvent.VK_H);
		help.add(item("Help contents", KeyEvent.VK_H, KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), e -> showHelp()));
		help.add(item("Measures in this file...", KeyEvent.VK_M, null, e -> showMeasures()));
		help.add(item("Dataset statistics...", KeyEvent.VK_S, null, e -> showStatistics()));
		help.addSeparator();
		help.add(item("About", KeyEvent.VK_A, null, e -> showAbout()));
		bar.add(help);

		return bar;
	}

	private static JMenuItem item(String text, int mnemonic, KeyStroke accelerator, ActionListener listener) {
		JMenuItem item = new JMenuItem(text);
		item.setMnemonic(mnemonic);
		if (accelerator != null) {
			item.setAccelerator(accelerator);
		}
		item.addActionListener(listener);
		return item;
	}

	private static KeyStroke ctrl(int key) {
		return KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK);
	}

	private static KeyStroke ctrlShift(int key) {
		return KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
	}

	// --------------------------------------------------------------- plot panel

	/** The plot with its controls placed next to the axis they affect. */
	private JComponent buildPlotPanel() {
		DefaultListCellRenderer prettyRenderer = new DefaultListCellRenderer() {
			private static final long serialVersionUID = 1L;

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
					boolean cellHasFocus) {
				Object shown = value instanceof String && !NONE.equals(value) ? MeasureUtils.prettyName((String) value)
						: value;
				return super.getListCellRendererComponent(list, shown, index, isSelected, cellHasFocus);
			}
		};
		for (JComboBox<String> c : List.of(xCombo, yCombo, colorCombo, sizeCombo, topKCombo)) {
			c.setRenderer(prettyRenderer);
		}
		// combos can shrink on small screens (no fixed width)
		for (JComboBox<String> c : List.of(xCombo, yCombo, colorCombo, sizeCombo)) {
			c.setPrototypeDisplayValue("Confidence (%)");
			c.setMinimumSize(new Dimension(70, c.getPreferredSize().height));
		}
		xCombo.setToolTipText("Measure shown on the horizontal axis");
		yCombo.setToolTipText("Measure shown on the vertical axis");
		colorCombo.setToolTipText("Measure encoded by the color of the points");
		sizeCombo.setToolTipText("Measure encoded by the size of the points");
		logXBox.setToolTipText("Logarithmic scale on the X axis (spreads out small values)");
		logYBox.setToolTipText("Logarithmic scale on the Y axis (spreads out small values)");

		ActionListener axisListener = e -> {
			if (!updating) {
				updateScatterAxes();
			}
		};
		for (JComboBox<String> c : List.of(xCombo, yCombo, colorCombo, sizeCombo)) {
			c.addActionListener(axisListener);
		}
		logXBox.addActionListener(axisListener);
		logYBox.addActionListener(axisListener);

		// ---- top: two short rows (Y axis / point appearance)
		JPanel settings = new JPanel(new GridBagLayout());
		settings.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		GridBagConstraints gc = new GridBagConstraints();
		gc.insets = new Insets(1, 3, 1, 3);
		gc.anchor = GridBagConstraints.WEST;

		gc.gridy = 0;
		gc.gridx = 0;
		settings.add(new JLabel("Y axis:"), gc);
		gc.gridx = 1;
		gc.fill = GridBagConstraints.HORIZONTAL;
		gc.weightx = 1;
		settings.add(yCombo, gc);
		gc.gridx = 2;
		gc.gridwidth = 2;
		gc.fill = GridBagConstraints.NONE;
		gc.weightx = 0;
		settings.add(logYBox, gc);

		gc.gridy = 1;
		gc.gridx = 0;
		gc.gridwidth = 1;
		settings.add(new JLabel("Color:"), gc);
		gc.gridx = 1;
		gc.fill = GridBagConstraints.HORIZONTAL;
		gc.weightx = 1;
		settings.add(colorCombo, gc);
		gc.gridx = 2;
		gc.fill = GridBagConstraints.NONE;
		gc.weightx = 0;
		settings.add(new JLabel("Size:"), gc);
		gc.gridx = 3;
		gc.fill = GridBagConstraints.HORIZONTAL;
		gc.weightx = 1;
		settings.add(sizeCombo, gc);

		// ---- right: vertical zoom strip (takes no horizontal space from the controls)
		zoomInButton.setText("+");
		zoomOutButton.setText("\u2212");
		resetZoomButton.setText("Fit");
		zoomInButton.setToolTipText("Zoom in (or use the mouse wheel)");
		zoomOutButton.setToolTipText("Zoom out (or use the mouse wheel)");
		resetZoomButton.setToolTipText("Reset the zoom to show all rules (or double-click the plot)");
		Font zoomFont = zoomInButton.getFont().deriveFont(Font.BOLD, 14f);
		zoomInButton.setFont(zoomFont);
		zoomOutButton.setFont(zoomFont);
		resetZoomButton.setFont(resetZoomButton.getFont().deriveFont(10f));
		JPanel zoomStrip = new JPanel();
		zoomStrip.setLayout(new BoxLayout(zoomStrip, BoxLayout.Y_AXIS));
		zoomStrip.setBorder(BorderFactory.createEmptyBorder(6, 2, 2, 2));
		for (JButton b : List.of(zoomInButton, zoomOutButton, resetZoomButton)) {
			b.setMargin(new Insets(0, 0, 0, 0));
			b.setFocusable(false);
			Dimension square = new Dimension(30, 28);
			b.setPreferredSize(square);
			b.setMinimumSize(square);
			b.setMaximumSize(square);
			b.setAlignmentX(Component.CENTER_ALIGNMENT);
			zoomStrip.add(b);
			zoomStrip.add(Box.createVerticalStrut(3));
		}
		zoomStrip.add(Box.createVerticalGlue());
		zoomInButton.addActionListener(e -> scatterPanel.zoomIn());
		zoomOutButton.addActionListener(e -> scatterPanel.zoomOut());
		resetZoomButton.addActionListener(e -> scatterPanel.resetView());

		// ---- bottom: X axis row, then a one-line hint
		swapAxesButton.setText("Swap axes");
		swapAxesButton.setToolTipText("Exchange the measures of the X and Y axes");
		swapAxesButton.setMargin(new Insets(1, 6, 1, 6));
		swapAxesButton.setFocusable(false);
		swapAxesButton.addActionListener(e -> swapAxes());
		JPanel xRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
		xRow.add(new JLabel("X axis:"));
		xRow.add(xCombo);
		xRow.add(logXBox);
		xRow.add(swapAxesButton);
		JLabel hintLabel = hint(HINT_TEXT);
		hintLabel.setHorizontalAlignment(SwingConstants.CENTER);
		JPanel bottom = new JPanel(new BorderLayout(0, 2));
		bottom.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		bottom.add(xRow, BorderLayout.NORTH);
		bottom.add(hintLabel, BorderLayout.SOUTH);

		JPanel plotPanel = new JPanel(new BorderLayout());
		plotPanel.setBorder(BorderFactory.createTitledBorder("Plot - each point is one rule"));
		plotPanel.add(settings, BorderLayout.NORTH);
		plotPanel.add(scatterPanel, BorderLayout.CENTER);
		plotPanel.add(zoomStrip, BorderLayout.EAST);
		plotPanel.add(bottom, BorderLayout.SOUTH);
		return plotPanel;
	}

	private static JComponent verticalSeparator() {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
		p.add(Box.createHorizontalStrut(10));
		JSeparator s = new JSeparator(SwingConstants.VERTICAL);
		s.setMaximumSize(new Dimension(2, 24));
		p.add(s);
		p.add(Box.createHorizontalStrut(10));
		p.setMaximumSize(new Dimension(22, 24));
		return p;
	}

	// --------------------------------------------------------------- filter panel

	private JScrollPane buildFilterPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

		// ---- search
		JPanel search = titledBox("Search items");
		searchField.setToolTipText(
				"Type one or more items separated by spaces. Only rules containing all of them are kept.");
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				applyFilters();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				applyFilters();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				applyFilters();
			}
		});
		JButton clearSearch = new JButton("Clear");
		clearSearch.setMargin(new Insets(1, 6, 1, 6));
		clearSearch.setToolTipText("Clear the search");
		clearSearch.addActionListener(e -> searchField.setText(""));
		search.add(row(searchField, clearSearch));
		search.add(hint("Rules must contain every item typed here."));
		panel.add(search);

		// ---- items in the shown rules (click to filter)
		JPanel items = titledBox("Items in the shown rules");
		items.add(itemPanel);
		items.add(hint("Click an item to add it to (or remove from) the search."));
		panel.add(items);

		// ---- measure ranges
		JPanel rangesPanel = titledBox("Measure ranges");
		filtersBox.setLayout(new BoxLayout(filtersBox, BoxLayout.Y_AXIS));
		filtersBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		rangesPanel.add(filtersBox);
		rangesPanel.add(hint("Drag the handles or type values. Double-click a slider to reset it."));
		panel.add(rangesPanel);

		// ---- quick filters
		JPanel quick = titledBox("Quick filters");
		positiveOnlyBox.setToolTipText("Keep only rules whose lift is greater than 1");
		positiveOnlyBox.addActionListener(e -> applyFilters());
		quick.add(row(positiveOnlyBox));
		topKBox.setToolTipText("Keep only the K rules with the highest value of the chosen measure");
		topKBox.addActionListener(e -> applyFilters());
		topKSpinner.addChangeListener(e -> applyFilters());
		((JSpinner.DefaultEditor) topKSpinner.getEditor()).getTextField().setColumns(5);
		topKCombo.addActionListener(e -> applyFilters());
		quick.add(row(topKBox, topKSpinner, new JLabel("rules by"), topKCombo));
		panel.add(quick);

		// ---- database size
		JPanel db = titledBox("Database");
		transactionsField.setToolTipText("Number of transactions in the input database used to mine the rules");
		JButton applyButton = new JButton("Apply");
		applyButton.addActionListener(e -> applyTransactionCount());
		transactionsField.addActionListener(e -> applyTransactionCount());
		db.add(row(new JLabel("Transactions in database:"), transactionsField, applyButton));
		db.add(hint("Optional: adds a Support (%) measure and percentages in explanations."));
		panel.add(db);

		// ---- reset
		JButton resetButton = new JButton("Reset all filters");
		resetButton.setToolTipText("Clear the search, the measure ranges and the quick filters (Ctrl+R)");
		resetButton.addActionListener(e -> resetFilters());
		panel.add(row(resetButton));
		panel.add(Box.createVerticalGlue());

		JScrollPane scroll = new JScrollPane(panel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createTitledBorder("Filters"));
		scroll.setPreferredSize(new Dimension(340, 100));
		scroll.setMinimumSize(new Dimension(150, 100));
		return scroll;
	}

	// --------------------------------------------------------------- table

	private JScrollPane buildTablePane() {
		table.setAutoCreateRowSorter(true);
		table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		table.setFillsViewportHeight(true);
		table.setRowHeight(20);
		table.setDefaultRenderer(Double.class, new DataBarRenderer());
		table.getTableHeader().setToolTipText("Click a column header to sort");

		// Ctrl+C in the table copies rules in readable form (same as Edit > Copy)
		table.getActionMap().put("copy", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				copySelectedRules();
			}
		});

		table.getSelectionModel().addListSelectionListener(e -> {
			if (e.getValueIsAdjusting() || updating) {
				return;
			}
			List<ARule> sel = selectedRulesFromTable();
			scatterPanel.setSelectedRules(sel);
			detailPanel.setRules(sel);
			updateStatus();
		});
		table.addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				int row = table.rowAtPoint(e.getPoint());
				scatterPanel.setHoverRule(row < 0 ? null : tableModel.getRule(table.convertRowIndexToModel(row)));
			}
		});
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseExited(MouseEvent e) {
				scatterPanel.setHoverRule(null);
			}

			@Override
			public void mousePressed(MouseEvent e) {
				maybeShowTablePopup(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				maybeShowTablePopup(e);
			}
		});

		JScrollPane scroll = new JScrollPane(table);
		scroll.setBorder(BorderFactory.createTitledBorder("Rules"));
		return scroll;
	}

	private static JPanel titledBox(String title) {
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBorder(BorderFactory.createTitledBorder(title));
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		return p;
	}

	private static JPanel row(Component... components) {
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		for (Component c : components) {
			p.add(c);
		}
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
		return p;
	}

	private static JLabel hint(String text) {
		JLabel l = new JLabel(text);
		l.setFont(l.getFont().deriveFont(Font.PLAIN, 10.5f));
		l.setForeground(Color.GRAY);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		l.setBorder(BorderFactory.createEmptyBorder(0, 4, 2, 0));
		return l;
	}

	// =============================================================== loading

	/**
	 * Load a rule file and refresh all views.
	 * 
	 * @param filePath the path of the file
	 * @throws IOException if the file cannot be read
	 */
	public void loadFile(String filePath) throws IOException {
		ARuleReader reader = new ARuleReader(filePath);
		loadedRules = reader.getRules();
		loadedMeasureNames = reader.getMeasureNames();
		fileType = reader.getFileType();
		File file = new File(filePath);
		if (file.getParentFile() != null) {
			lastDirectory = file.getParentFile();
			try {
				prefs.put("lastDir", lastDirectory.getAbsolutePath());
			} catch (Exception ignored) {
				// preferences unavailable
			}
		}
		setTitle("SPMF Association Rule Explorer - " + file.getName());
		addRecentFile(file.getAbsolutePath());

		updating = true;
		try {
			searchField.setText("");
			positiveOnlyBox.setSelected(false);
			topKBox.setSelected(false);
			transactionCount = -1;
			transactionsField.setText("");
		} finally {
			updating = false;
		}
		rebuildDerivedRules();
		scatterPanel.setSelectedRules(new ArrayList<>());
		detailPanel.setRules(new ArrayList<>());
		refreshAll(false);
		scatterPanel.resetView();
		if (loadedRules.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					"No association rules were found in this file.\n"
							+ "Expected lines such as:  1 2 ==> 3 #SUP: 4 #CONF: 0.75 #LIFT: 1.2",
					"Empty file", JOptionPane.WARNING_MESSAGE);
		}
	}

	/**
	 * Recompute the list of rules including derived measures (relative support).
	 */
	private void rebuildDerivedRules() {
		String supKey = findMeasure(loadedMeasureNames, "SUP", "SUPPORT");
		measureNames = new ArrayList<>(loadedMeasureNames);
		measureNames.remove(RSUP);
		allRules = new ArrayList<>(loadedRules.size());
		if (transactionCount > 0 && supKey != null) {
			measureNames.add(measureNames.indexOf(supKey) + 1, RSUP);
			for (ARule r : loadedRules) {
				double sup = r.getMeasure(supKey);
				allRules.add(Double.isNaN(sup) ? r : r.withMeasureAfter(supKey, RSUP, sup * 100.0 / transactionCount));
			}
		} else {
			allRules.addAll(loadedRules);
		}
	}

	/** Rebuild every view from allRules / measureNames. */
	private void refreshAll(boolean keepFilterPositions) {
		Map<String, int[]> savedPositions = new HashMap<>();
		if (keepFilterPositions) {
			for (MeasureRangeFilter f : rangeFilters) {
				savedPositions.put(f.getMeasure(), f.getSliderValues());
			}
		}
		computeRanges();
		rebuildCombos();
		rebuildFilters(savedPositions);
		rebuildTable();
		scatterPanel.setMeasureRanges(ranges);
		detailPanel.setMeasureInfo(measureNames, ranges);
		detailPanel.setTransactionCount(transactionCount);
		updateScatterAxes();
		applyFilters();
	}

	private void computeRanges() {
		ranges.clear();
		for (String m : measureNames) {
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
			for (ARule rule : allRules) {
				double v = rule.getMeasure(m);
				if (Double.isNaN(v) || Double.isInfinite(v)) {
					continue;
				}
				min = Math.min(min, v);
				max = Math.max(max, v);
			}
			if (min <= max) {
				ranges.put(m, new double[] { min, max });
			}
		}
	}

	private void rebuildCombos() {
		updating = true;
		try {
			for (JComboBox<String> c : List.of(xCombo, yCombo, colorCombo, sizeCombo, topKCombo)) {
				c.removeAllItems();
			}
			colorCombo.addItem(NONE);
			sizeCombo.addItem(NONE);
			for (String m : ranges.keySet()) {
				xCombo.addItem(m);
				yCombo.addItem(m);
				colorCombo.addItem(m);
				sizeCombo.addItem(m);
				topKCombo.addItem(m);
			}
			applyDefaultPlotSettings();
			List<String> available = new ArrayList<>(ranges.keySet());
			String lift = findMeasure(available, "LIFT");
			String conf = findMeasure(available, "CONF", "CONFIDENCE");
			topKCombo.setSelectedItem(lift != null ? lift : conf != null ? conf : pick(available, 0));
			positiveOnlyBox.setEnabled(lift != null);
			if (lift == null) {
				positiveOnlyBox.setSelected(false);
			}
		} finally {
			updating = false;
		}
	}

	/** X = support, Y = confidence, color = lift, no size, linear scales. */
	private void applyDefaultPlotSettings() {
		List<String> available = new ArrayList<>(ranges.keySet());
		String sup = findMeasure(available, RSUP, "SUP", "SUPPORT");
		String conf = findMeasure(available, "CONF", "CONFIDENCE");
		String lift = findMeasure(available, "LIFT");
		xCombo.setSelectedItem(sup != null ? sup : pick(available, 0));
		yCombo.setSelectedItem(conf != null ? conf : pick(available, 1));
		colorCombo.setSelectedItem(lift != null ? lift : NONE);
		sizeCombo.setSelectedItem(NONE);
		logXBox.setSelected(false);
		logYBox.setSelected(false);
	}

	private static String pick(List<String> available, int index) {
		return available.isEmpty() ? null : available.get(Math.min(index, available.size() - 1));
	}

	/**
	 * First measure name in the collection matching one of the candidates
	 * (case-insensitive).
	 */
	private static String findMeasure(Collection<String> names, String... candidates) {
		for (String candidate : candidates) {
			for (String n : names) {
				if (n.equalsIgnoreCase(candidate)) {
					return n;
				}
			}
		}
		return null;
	}

	private void rebuildFilters(Map<String, int[]> savedPositions) {
		updating = true;
		try {
			rangeFilters.clear();
			filtersBox.removeAll();
			for (Map.Entry<String, double[]> e : ranges.entrySet()) {
				MeasureRangeFilter f = new MeasureRangeFilter(e.getKey(), e.getValue(), this::applyFilters);
				int[] saved = savedPositions.get(e.getKey());
				if (saved != null) {
					f.setSliderValues(saved);
				}
				rangeFilters.add(f);
				filtersBox.add(f);
			}
			if (ranges.isEmpty()) {
				filtersBox.add(hint("No numeric measures found (open a rule file first)."));
			}
		} finally {
			updating = false;
		}
		filtersBox.revalidate();
		filtersBox.repaint();
	}

	private void rebuildTable() {
		tableModel.setStructure(measureNames);
		TableColumnModel cm = table.getColumnModel();
		for (int i = 0; i < cm.getColumnCount(); i++) {
			cm.getColumn(i).setPreferredWidth(i == 0 ? 220 : i == 1 ? 170 : 100);
		}
	}

	// =============================================================== behaviour

	private void updateScatterAxes() {
		updating = true;
		try {
			String x = (String) xCombo.getSelectedItem();
			String y = (String) yCombo.getSelectedItem();
			String color = (String) colorCombo.getSelectedItem();
			String size = (String) sizeCombo.getSelectedItem();
			if (NONE.equals(color)) {
				color = null;
			}
			if (NONE.equals(size)) {
				size = null;
			}
			boolean canLogX = x != null && ranges.containsKey(x) && ranges.get(x)[0] > 0;
			boolean canLogY = y != null && ranges.containsKey(y) && ranges.get(y)[0] > 0;
			logXBox.setEnabled(canLogX);
			logYBox.setEnabled(canLogY);
			if (!canLogX) {
				logXBox.setSelected(false);
			}
			if (!canLogY) {
				logYBox.setSelected(false);
			}
			scatterPanel.setAxes(x, y, color, size, logXBox.isSelected(), logYBox.isSelected());
		} finally {
			updating = false;
		}
		updateEnabledStates();
	}

	private void swapAxes() {
		Object x = xCombo.getSelectedItem();
		Object y = yCombo.getSelectedItem();
		boolean lx = logXBox.isSelected();
		boolean ly = logYBox.isSelected();
		updating = true;
		try {
			xCombo.setSelectedItem(y);
			yCombo.setSelectedItem(x);
			logXBox.setSelected(ly);
			logYBox.setSelected(lx);
		} finally {
			updating = false;
		}
		updateScatterAxes();
	}

	private void restoreDefaultPlot() {
		updating = true;
		try {
			applyDefaultPlotSettings();
		} finally {
			updating = false;
		}
		updateScatterAxes();
		scatterPanel.resetView();
	}

	private void setPanelVisible(Component panel, JSplitPane split, boolean visible) {
		panel.setVisible(visible);
		split.revalidate();
		if (visible) {
			split.resetToPreferredSizes();
		}
		split.repaint();
	}

	/** Enable/disable menu items and buttons according to the current state. */
	private void updateEnabledStates() {
		boolean hasRules = !allRules.isEmpty();
		boolean zoomed = scatterPanel.isZoomed();
		int selected = scatterPanel.getSelectedRules().size();
		if (exportItem != null) {
			exportItem.setEnabled(hasRules);
			csvItem.setEnabled(hasRules);
			pngItem.setEnabled(scatterPanel.getWidth() > 50 && scatterPanel.getHeight() > 50);
			copyItem.setEnabled(selected > 0);
			clearSelectionItem.setEnabled(selected > 0);
			resetZoomItem.setEnabled(zoomed);
		}
		resetZoomButton.setEnabled(zoomed);
		zoomInButton.setEnabled(hasRules);
		zoomOutButton.setEnabled(hasRules);
		swapAxesButton.setEnabled(hasRules);
	}

	private void applyFilters() {
		if (updating) {
			return;
		}
		List<MeasureRangeFilter> active = new ArrayList<>();
		for (MeasureRangeFilter f : rangeFilters) {
			if (f.isActive()) {
				active.add(f);
			}
		}
		String liftKey = findMeasure(ranges.keySet(), "LIFT");
		boolean positiveOnly = positiveOnlyBox.isEnabled() && positiveOnlyBox.isSelected() && liftKey != null;
		String text = searchField.getText().trim().toLowerCase();
		String[] terms = text.isEmpty() ? new String[0] : text.split("\\s+");

		List<ARule> result = new ArrayList<>();
		for (ARule rule : allRules) {
			boolean ok = true;
			for (MeasureRangeFilter f : active) {
				double v = rule.getMeasure(f.getMeasure());
				// a rule that does not have the measure (NaN) does not satisfy
				// the filter either
				if (Double.isNaN(v) || v < f.getMin() || v > f.getMax()) {
					ok = false;
					break;
				}
			}
			if (ok && positiveOnly && !(rule.getMeasure(liftKey) > 1)) {
				ok = false;
			}
			if (ok) {
				for (String term : terms) {
					if (!rule.containsItem(term)) {
						ok = false;
						break;
					}
				}
			}
			if (ok) {
				result.add(rule);
			}
		}
		String topMeasure = (String) topKCombo.getSelectedItem();
		if (topKBox.isSelected() && topMeasure != null) {
			int k = (Integer) topKSpinner.getValue();
			result.sort((r1, r2) -> Double.compare(nanToLowest(r2.getMeasure(topMeasure)),
					nanToLowest(r1.getMeasure(topMeasure))));
			if (result.size() > k) {
				result = new ArrayList<>(result.subList(0, k));
			}
		}
		filteredRules = result;

		List<ARule> selected = scatterPanel.getSelectedRules();
		selected.retainAll(filteredRules);
		updating = true;
		try {
			tableModel.setRules(filteredRules);
			scatterPanel.setRules(filteredRules);
		} finally {
			updating = false;
		}
		selectRulesInTable(selected, false);
		detailPanel.setRules(selected);
		updateItemsPanel();
		updateStatus();
	}

	private static double nanToLowest(double v) {
		return Double.isNaN(v) ? Double.NEGATIVE_INFINITY : v;
	}

	/** Refresh the "Items in the shown rules" list from the current filters. */
	private void updateItemsPanel() {
		List<Map.Entry<String, Integer>> items = ItemFilterPanel.countItems(filteredRules);
		Set<String> terms = new LinkedHashSet<>();
		String text = searchField.getText().trim();
		if (!text.isEmpty()) {
			terms.addAll(Arrays.asList(text.split("\\s+")));
		}
		itemPanel.setItems(items, items.size(), terms);
	}

	/** Add the given item to the search box, or remove it if it is already there. */
	private void toggleSearchTerm(String term) {
		String text = searchField.getText().trim();
		List<String> terms = new ArrayList<>(Arrays.asList(text.isEmpty() ? new String[0] : text.split("\\s+")));
		if (!terms.remove(term)) {
			terms.add(term);
		}
		searchField.setText(String.join(" ", terms));
	}

	private void updateStatus() {
		boolean filtersActive = filteredRules.size() != allRules.size();
		int selected = scatterPanel.getSelectedRules().size();
		StringBuilder sb = new StringBuilder();
		if (allRules.isEmpty()) {
			sb.append("No file loaded. Use File > Open, or drop a rule file on this window.");
		} else {
			sb.append("Showing ").append(filteredRules.size()).append(" of ").append(allRules.size()).append(" rules");
			if (filtersActive) {
				sb.append("  (filters active)");
			}
			if (selected > 0) {
				sb.append("   |   ").append(selected).append(" selected");
			}
			if (transactionCount > 0) {
				sb.append("   |   ").append(transactionCount).append(" transactions");
			}
		}
		statusLabel.setText(sb.toString());
		((TitledBorder) tableScroll.getBorder()).setTitle("Rules (" + filteredRules.size() + " shown"
				+ (selected > 0 ? ", " + selected + " selected" : "") + ")");
		tableScroll.repaint();
		updateEnabledStates();
	}

	private List<ARule> selectedRulesFromTable() {
		List<ARule> list = new ArrayList<>();
		for (int viewRow : table.getSelectedRows()) {
			list.add(tableModel.getRule(table.convertRowIndexToModel(viewRow)));
		}
		return list;
	}

	private void selectRulesInTable(Collection<ARule> rules, boolean scrollToFirst) {
		updating = true;
		try {
			table.clearSelection();
			int first = -1;
			for (ARule r : rules) {
				int modelRow = tableModel.indexOf(r);
				if (modelRow >= 0) {
					int viewRow = table.convertRowIndexToView(modelRow);
					table.addRowSelectionInterval(viewRow, viewRow);
					if (first < 0 || viewRow < first) {
						first = viewRow;
					}
				}
			}
			if (scrollToFirst && first >= 0) {
				table.scrollRectToVisible(table.getCellRect(first, 0, true));
			}
		} finally {
			updating = false;
		}
	}

	private void resetFilters() {
		updating = true;
		try {
			for (MeasureRangeFilter f : rangeFilters) {
				f.reset();
			}
			searchField.setText("");
			positiveOnlyBox.setSelected(false);
			topKBox.setSelected(false);
		} finally {
			updating = false;
		}
		applyFilters();
	}

	private void applyTransactionCount() {
		String text = transactionsField.getText().trim().replace(" ", "").replace(",", "");
		long n = -1;
		if (!text.isEmpty()) {
			try {
				n = Long.parseLong(text);
			} catch (NumberFormatException ex) {
				showError("Please enter a whole number of transactions.");
				return;
			}
			if (n <= 0) {
				n = -1;
			}
		}
		if (n == transactionCount) {
			return;
		}
		transactionCount = n;
		rebuildDerivedRules();
		scatterPanel.setSelectedRules(new ArrayList<>());
		detailPanel.setRules(new ArrayList<>());
		refreshAll(true);
	}

	private void copySelectedRules() {
		List<ARule> rules = scatterPanel.getSelectedRules();
		if (rules.isEmpty()) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (ARule r : rules) {
			sb.append(RuleDetailPanel.toReadableText(r)).append(System.lineSeparator());
		}
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
		statusLabel.setText("Copied " + rules.size() + (rules.size() == 1 ? " rule" : " rules") + " to the clipboard");
	}

	// =============================================================== popup menu

	private void maybeShowTablePopup(MouseEvent e) {
		if (!e.isPopupTrigger()) {
			return;
		}
		int row = table.rowAtPoint(e.getPoint());
		if (row >= 0 && !table.isRowSelected(row)) {
			table.setRowSelectionInterval(row, row);
		}
		buildPopup(selectedRulesFromTable()).show(table, e.getX(), e.getY());
	}

	private JPopupMenu buildPopup(List<ARule> context) {
		JPopupMenu menu = new JPopupMenu();
		int n = context.size();

		JMenuItem copy = new JMenuItem(n == 1 ? "Copy rule" : "Copy " + n + " rules");
		copy.setEnabled(n > 0);
		copy.addActionListener(e -> {
			StringBuilder sb = new StringBuilder();
			for (ARule r : context) {
				sb.append(RuleDetailPanel.toReadableText(r)).append(System.lineSeparator());
			}
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
		});
		menu.add(copy);

		JMenuItem focus = new JMenuItem("Show only rules containing all these items");
		focus.setEnabled(n > 0);
		focus.addActionListener(e -> {
			LinkedHashSet<String> items = new LinkedHashSet<>();
			for (ARule r : context) {
				items.addAll(r.getAllItems());
			}
			searchField.setText(String.join(" ", items));
		});
		menu.add(focus);
		menu.addSeparator();

		JMenuItem clear = new JMenuItem("Clear selection (Esc)");
		clear.addActionListener(e -> table.clearSelection());
		menu.add(clear);
		JMenuItem resetView = new JMenuItem("Reset zoom");
		resetView.setEnabled(scatterPanel.isZoomed());
		resetView.addActionListener(e -> scatterPanel.resetView());
		menu.add(resetView);
		JMenuItem resetFilters = new JMenuItem("Reset all filters");
		resetFilters.addActionListener(e -> resetFilters());
		menu.add(resetFilters);
		return menu;
	}

	// =============================================================== files

	private void chooseAndOpen() {
		JFileChooser chooser = new JFileChooser(lastDirectory);
		chooser.setDialogTitle("Open an SPMF association rule file");
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			openQuietly(chooser.getSelectedFile().getAbsolutePath());
		}
	}

	private void openQuietly(String path) {
		try {
			loadFile(path);
		} catch (IOException ex) {
			showError("Could not read the file:\n" + path + "\n\n" + ex.getMessage());
		}
	}

	private void exportFiltered() {
		if (filteredRules.isEmpty()) {
			showError("There are no rules to export (check the filters).");
			return;
		}
		JFileChooser chooser = new JFileChooser(lastDirectory);
		chooser.setDialogTitle("Export the " + filteredRules.size() + " filtered rules");
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File out = chooser.getSelectedFile();
		if (out.exists() && JOptionPane.showConfirmDialog(this, "The file already exists. Replace it?", "Confirm",
				JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
			return;
		}
		try (PrintWriter w = new PrintWriter(out, StandardCharsets.UTF_8.name())) {
			if (!fileType.isEmpty()) {
				w.println("@FILETYPE=\"" + fileType + "\"");
			}
			w.println("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
			for (ARule r : filteredRules) {
				w.println(withoutDerivedMeasures(r).toSpmfLine());
			}
			statusLabel.setText("Exported " + filteredRules.size() + " rules to " + out.getName());
		} catch (IOException ex) {
			showError("Could not write the file:\n" + ex.getMessage());
		}
	}

	/** Export the currently shown rules as a CSV file (Excel friendly). */
	private void exportCsv() {
		if (filteredRules.isEmpty()) {
			showError("There are no rules to export (check the filters).");
			return;
		}
		JFileChooser chooser = new JFileChooser(lastDirectory);
		chooser.setDialogTitle("Export the " + filteredRules.size() + " shown rules as CSV");
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File out = chooser.getSelectedFile();
		if (out.exists() && JOptionPane.showConfirmDialog(this, "The file already exists. Replace it?", "Confirm",
				JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
			return;
		}
		try (PrintWriter w = new PrintWriter(new FileOutputStream(out), true, StandardCharsets.UTF_8)) {
			w.print('\uFEFF'); // UTF-8 BOM so that Excel detects the encoding
			StringBuilder header = new StringBuilder("IF,THEN");
			for (String m : measureNames) {
				header.append(',').append(csv(MeasureUtils.prettyName(m)));
			}
			w.println(header);
			for (ARule r : filteredRules) {
				StringBuilder sb = new StringBuilder();
				sb.append(csv(r.getAntecedentText())).append(',').append(csv(r.getConsequentText()));
				for (String m : measureNames) {
					sb.append(',').append(csv(MeasureUtils.format(r.getMeasure(m))));
				}
				w.println(sb);
			}
			statusLabel.setText("Exported " + filteredRules.size() + " rules to " + out.getName());
		} catch (IOException ex) {
			showError("Could not write the file:\n" + ex.getMessage());
		}
	}

	private static String csv(String s) {
		if (s == null) {
			return "";
		}
		if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
			return "\"" + s.replace("\"", "\"\"") + "\"";
		}
		return s;
	}

	/** Export the current scatter plot as a PNG file (2x resolution). */
	private void exportPlotPng() {
		int w = scatterPanel.getWidth();
		int h = scatterPanel.getHeight();
		if (w < 50 || h < 50) {
			showError("The plot area is too small to export. Resize the window and try again.");
			return;
		}
		JFileChooser chooser = new JFileChooser(lastDirectory);
		chooser.setDialogTitle("Export the plot as PNG");
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}
		File out = chooser.getSelectedFile();
		if (out.exists() && JOptionPane.showConfirmDialog(this, "The file already exists. Replace it?", "Confirm",
				JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
			return;
		}
		int scale = 2;
		BufferedImage image = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.scale(scale, scale);
		scatterPanel.paint(g);
		g.dispose();
		try {
			ImageIO.write(image, "png", out);
			statusLabel.setText("Saved the plot to " + out.getName());
		} catch (IOException ex) {
			showError("Could not write the image:\n" + ex.getMessage());
		}
	}

	/** Remove measures computed by this tool (relative support) before export. */
	private static ARule withoutDerivedMeasures(ARule r) {
		if (!r.getMeasures().containsKey(RSUP)) {
			return r;
		}
		Map<String, Double> m = new LinkedHashMap<>(r.getMeasures());
		m.remove(RSUP);
		return new ARule(r.getAntecedent(), r.isAntecedentNegated(), r.getConsequent(), r.isConsequentNegated(), m);
	}

	private void installFileDrop(Component component) {
		new DropTarget(component, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
			@Override
			public void drop(DropTargetDropEvent e) {
				try {
					e.acceptDrop(DnDConstants.ACTION_COPY);
					Object data = e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					if (data instanceof List<?> && !((List<?>) data).isEmpty()) {
						Object f = ((List<?>) data).get(0);
						if (f instanceof File) {
							loadFile(((File) f).getAbsolutePath());
						}
					}
					e.dropComplete(true);
				} catch (Exception ex) {
					e.dropComplete(false);
					showError("Could not open the dropped file:\n" + ex.getMessage());
				}
			}
		}, true);
	}

	// ---- recent files (stored with java.util.prefs)

	private List<String> getRecentFiles() {
		List<String> list = new ArrayList<>();
		try {
			for (int i = 0; i < MAX_RECENT_FILES; i++) {
				String p = prefs.get("recent" + i, null);
				if (p != null && !p.isEmpty()) {
					list.add(p);
				}
			}
		} catch (Exception ignored) {
			// preferences unavailable: no recent files
		}
		return list;
	}

	private void addRecentFile(String path) {
		try {
			List<String> list = getRecentFiles();
			list.remove(path);
			list.add(0, path);
			for (int i = 0; i < MAX_RECENT_FILES; i++) {
				if (i < list.size()) {
					prefs.put("recent" + i, list.get(i));
				} else {
					prefs.remove("recent" + i);
				}
			}
		} catch (Exception ignored) {
			// preferences unavailable
		}
		rebuildRecentMenu();
	}

	private void rebuildRecentMenu() {
		recentMenu.removeAll();
		List<String> list = getRecentFiles();
		if (list.isEmpty()) {
			JMenuItem empty = new JMenuItem("(no recent files)");
			empty.setEnabled(false);
			recentMenu.add(empty);
			return;
		}
		for (String path : list) {
			JMenuItem item = new JMenuItem(new File(path).getName());
			item.setToolTipText(path);
			item.addActionListener(e -> openQuietly(path));
			recentMenu.add(item);
		}
		recentMenu.addSeparator();
		JMenuItem clear = new JMenuItem("Clear list");
		clear.addActionListener(e -> {
			try {
				for (int i = 0; i < MAX_RECENT_FILES; i++) {
					prefs.remove("recent" + i);
				}
			} catch (Exception ignored) {
				// preferences unavailable
			}
			rebuildRecentMenu();
		});
		recentMenu.add(clear);
	}

	/** Restore the last window bounds if they are still visible on screen. */
	private void restoreWindowBounds() {
		try {
			int x = prefs.getInt("windowX", Integer.MIN_VALUE);
			int y = prefs.getInt("windowY", Integer.MIN_VALUE);
			int w = prefs.getInt("windowW", 0);
			int h = prefs.getInt("windowH", 0);
			if (w >= 400 && h >= 300 && x != Integer.MIN_VALUE && y != Integer.MIN_VALUE) {
				Rectangle saved = new Rectangle(x, y, w, h);
				Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
				if (saved.intersects(screen)) {
					setBounds(saved);
					return;
				}
			}
		} catch (Exception ignored) {
			// preferences unavailable or invalid
		}
		setLocationRelativeTo(null);
	}

	private void saveWindowBounds() {
		try {
			prefs.putInt("windowX", getX());
			prefs.putInt("windowY", getY());
			prefs.putInt("windowW", getWidth());
			prefs.putInt("windowH", getHeight());
		} catch (Exception ignored) {
			// preferences unavailable
		}
	}

	private void showError(String message) {
		JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
	}

	// =============================================================== help

	private void showHelp() {
		StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif;font-size:11px;width:540px'>");
		sb.append("<h2>Association Rule Explorer</h2>");
		sb.append("<p>Each <b>point</b> of the plot is one rule. The lists placed <b>next to each axis</b> choose the "
				+ "measure shown on that axis; <b>Point color</b> and <b>Point size</b> add a third and fourth measure. "
				+ "With the default settings (X = support, Y = confidence, color = lift), the best rules are in the "
				+ "<b>upper-right corner</b> and drawn in <b>red</b>; gray points are close to independence and blue "
				+ "points indicate a negative correlation.</p>");
		sb.append("<h3>Plot</h3><ul>"
				+ "<li><b>Hover</b> a point to see the rule and its measures (several rules may share one point).</li>"
				+ "<li><b>Click</b> a point to select it; <b>drag</b> a rectangle to select several rules; "
				+ "<b>Ctrl/Shift-click</b> to add or remove points from the selection.</li>"
				+ "<li><b>Mouse wheel</b> or the <b>Zoom in / Zoom out</b> buttons: zoom. <b>Right-drag</b>: pan. "
				+ "<b>Reset zoom</b> or <b>double-click</b>: show everything again.</li>"
				+ "<li><b>Swap axes</b>: exchange the two axes. <b>log scale</b>: useful when most values are small.</li>"
				+ "<li><b>Right-click</b>: copy rules, focus on their items, reset.</li></ul>");
		sb.append("<h3>Table</h3><p>Click a column header to sort. The bars inside the cells show each value "
				+ "relative to the minimum and maximum of that measure. Moving the mouse over a row highlights "
				+ "the corresponding point in the plot. Use Ctrl/Shift-click to select several rules; "
				+ "<b>Ctrl+C</b> copies them, <b>Esc</b> clears the selection.</p>");
		sb.append("<h3>Filters (left panel)</h3><ul>"
				+ "<li><b>Search items</b>: keep only rules containing all the items typed. "
				+ "The list under it shows the most frequent items of the shown rules; <b>click an item to toggle it</b> "
				+ "in the search.</li>"
				+ "<li><b>Measure ranges</b>: drag the two handles or type a minimum / maximum. "
				+ "Double-click a slider (or use the arrow keys) to adjust it.</li>"
				+ "<li><b>Quick filters</b>: keep only positively correlated rules (lift &gt; 1), "
				+ "or only the top K rules by a measure.</li>"
				+ "<li><b>Transactions in database</b>: enter the size of the database to get support as a "
				+ "percentage.</li></ul>");
		sb.append("<h3>Menus</h3><p><b>File</b>: open (Ctrl+O), recent files, export the filtered rules as SPMF "
				+ "(Ctrl+E) or CSV (Ctrl+Shift+E), export the plot as PNG. "
				+ "<b>Edit</b>: copy / select / clear, reset filters (Ctrl+R). "
				+ "<b>View</b>: zoom, restore default plot settings, show or hide the side panels (F9, F10). "
				+ "<b>Help</b>: this page, the meaning of each measure, dataset statistics. "
				+ "You can also <b>drop a file</b> on the window.</p>");
		sb.append("</body></html>");
		showHtmlDialog("Help - Association Rule Explorer", sb.toString(), 640, 560);
	}

	private void showMeasures() {
		StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif;font-size:11px;width:480px'>");
		sb.append("<h2>Measures in this file</h2>");
		if (measureNames.isEmpty()) {
			sb.append("<p>No file is loaded.</p>");
		} else {
			sb.append("<table cellpadding='4'>");
			for (String m : measureNames) {
				double[] r = ranges.get(m);
				sb.append("<tr><td valign='top'><b>").append(esc(MeasureUtils.prettyName(m))).append("</b><br><small>#")
						.append(esc(m)).append("</small></td><td>").append(esc(MeasureUtils.describe(m)));
				if (r != null) {
					sb.append("<br><i>Range in this file: ").append(MeasureUtils.format(r[0])).append(" to ")
							.append(MeasureUtils.format(r[1])).append("</i>");
				}
				sb.append("</td></tr>");
			}
			sb.append("</table>");
		}
		sb.append("</body></html>");
		showHtmlDialog("Measures", sb.toString(), 580, 420);
	}

	/** Show general statistics about the loaded dataset and each measure. */
	private void showStatistics() {
		StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif;font-size:11px;width:560px'>");
		sb.append("<h2>Dataset statistics</h2>");
		if (allRules.isEmpty()) {
			sb.append("<p>No file is loaded.</p></body></html>");
			showHtmlDialog("Dataset statistics", sb.toString(), 600, 300);
			return;
		}
		Set<String> distinctItems = new LinkedHashSet<>();
		double itemSum = 0;
		int negated = 0;
		for (ARule r : allRules) {
			distinctItems.addAll(r.getAllItems());
			itemSum += r.getItemCount();
			if (r.isAntecedentNegated() || r.isConsequentNegated()) {
				negated++;
			}
		}
		sb.append("<table cellpadding='3'>");
		statRow(sb, "Rules in the file", Integer.toString(allRules.size()));
		if (filteredRules.size() != allRules.size()) {
			statRow(sb, "Rules shown (after filters)", Integer.toString(filteredRules.size()));
		}
		statRow(sb, "Distinct items", Integer.toString(distinctItems.size()));
		statRow(sb, "Average items per rule", MeasureUtils.format(itemSum / Math.max(1, allRules.size())));
		statRow(sb, "Negated rules", Integer.toString(negated));
		if (transactionCount > 0) {
			statRow(sb, "Transactions in the database", Long.toString(transactionCount));
		}
		if (!fileType.isEmpty()) {
			statRow(sb, "File type", esc(fileType));
		}
		sb.append("</table>");
		sb.append("<h3>Measures (over the ").append(allRules.size()).append(" rules of the file)</h3>");
		sb.append("<table cellpadding='3' cellspacing='0' border='0'>");
		sb.append("<tr style='background:#EEE'><td></td><td><b>Count</b></td><td><b>Min</b></td><td><b>Q1</b></td>"
				+ "<td><b>Median</b></td><td><b>Mean</b></td><td><b>Q3</b></td><td><b>Max</b></td><td><b>Missing</b></td></tr>");
		for (String m : measureNames) {
			List<Double> values = new ArrayList<>();
			int missing = 0;
			for (ARule r : allRules) {
				double v = r.getMeasure(m);
				if (Double.isNaN(v) || Double.isInfinite(v)) {
					missing++;
				} else {
					values.add(v);
				}
			}
			Collections.sort(values);
			double[] q = quantiles(values);
			sb.append("<tr><td><b>").append(esc(MeasureUtils.prettyName(m))).append("</b><br><small>#")
					.append(esc(m)).append("</small></td>");
			sb.append("<td>").append(values.size()).append("</td>");
			if (q == null) {
				sb.append("<td colspan='7'>no numeric values</td>");
			} else {
				sb.append("<td>").append(MeasureUtils.format(q[0])).append("</td>");
				sb.append("<td>").append(MeasureUtils.format(q[1])).append("</td>");
				sb.append("<td>").append(MeasureUtils.format(q[2])).append("</td>");
				double mean = 0;
				for (double v : values) {
					mean += v;
				}
				sb.append("<td>").append(MeasureUtils.format(values.isEmpty() ? 0 : mean / values.size()))
						.append("</td>");
				sb.append("<td>").append(MeasureUtils.format(q[3])).append("</td>");
				sb.append("<td>").append(MeasureUtils.format(q[4])).append("</td>");
			}
			sb.append("<td>").append(missing).append("</td></tr>");
		}
		sb.append("</table>");
		sb.append("</body></html>");
		showHtmlDialog("Dataset statistics", sb.toString(), 640, 460);
	}

	private static void statRow(StringBuilder sb, String label, String value) {
		sb.append("<tr><td><b>").append(esc(label)).append("</b></td><td>").append(esc(value)).append("</td></tr>");
	}

	/** min, Q1, median, Q3, max of a sorted list of values (null if empty). */
	private static double[] quantiles(List<Double> sorted) {
		int n = sorted.size();
		if (n == 0) {
			return null;
		}
		double min = sorted.get(0);
		double max = sorted.get(n - 1);
		return new double[] { min, quantile(sorted, 0.25), quantile(sorted, 0.5), quantile(sorted, 0.75), max };
	}

	private static double quantile(List<Double> a, double p) {
		int n = a.size();
		if (n == 1) {
			return a.get(0);
		}
		double pos = p * (n - 1);
		int lo = (int) Math.floor(pos);
		int hi = (int) Math.ceil(pos);
		if (lo == hi) {
			return a.get(lo);
		}
		double frac = pos - lo;
		return a.get(lo) + (a.get(hi) - a.get(lo)) * frac;
	}

	/** Escape text that will be inserted into an HTML dialog. */
	private static String esc(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private void showAbout() {
		JOptionPane.showMessageDialog(this, "<html><b>SPMF Association Rule Explorer</b><br><br>"
				+ "A tool to visualize and filter association rules found by the algorithms<br>"
				+ "of the SPMF data mining library.<br><br>" + "Copyright (c) 2008-2025 Philippe Fournier-Viger<br>"
				+ "https://www.philippe-fournier-viger.com/spmf/<br><br>"
				+ "This build was fixed and extended by <b>Arena.ai Agent</b>: item frequency list,<br>"
				+ "CSV and PNG export, dataset statistics, window position memory, and several fixes.<br><br>"
				+ "Released under the GNU General Public License v3.</html>", "About", JOptionPane.INFORMATION_MESSAGE);
	}

	private void showHtmlDialog(String title, String html, int width, int height) {
		JEditorPane pane = new JEditorPane("text/html", html);
		pane.setEditable(false);
		pane.setCaretPosition(0);
		JScrollPane scroll = new JScrollPane(pane);
		scroll.setPreferredSize(new Dimension(width, height));
		JOptionPane.showMessageDialog(this, scroll, title, JOptionPane.PLAIN_MESSAGE);
	}

	// =============================================================== table
	// renderer

	/** Renders a measure value with a colored data bar behind the number. */
	private class DataBarRenderer extends DefaultTableCellRenderer {
		private static final long serialVersionUID = 1L;
		private double fraction;
		private Color barColor;
		private boolean hasBar;

		DataBarRenderer() {
			setHorizontalAlignment(SwingConstants.RIGHT);
		}

		@Override
		public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
				int row, int column) {
			super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
			String measure = tableModel.getMeasureAt(t.convertColumnIndexToModel(column));
			double[] r = measure == null ? null : ranges.get(measure);
			if (value instanceof Double) {
				double v = (Double) value;
				setText(MeasureUtils.format(v));
				hasBar = r != null;
				if (hasBar) {
					fraction = MeasureUtils.fraction(v, r);
					barColor = MeasureUtils.colorFor(v, measure, r);
				}
			} else {
				setText("-");
				hasBar = false;
			}
			return this;
		}

		@Override
		protected void paintComponent(Graphics g) {
			if (!hasBar) {
				super.paintComponent(g);
				return;
			}
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setColor(getBackground());
			g2.fillRect(0, 0, getWidth(), getHeight());
			int w = (int) Math.round((getWidth() - 4) * fraction);
			g2.setColor(new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), 90));
			g2.fillRect(2, 3, w, getHeight() - 6);
			g2.dispose();
			setOpaque(false);
			super.paintComponent(g);
			setOpaque(true);
		}
	}
}
