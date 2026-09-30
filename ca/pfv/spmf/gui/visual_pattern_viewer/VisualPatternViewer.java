package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import ca.pfv.spmf.gui.Main;

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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 *
 * Do not remove copyright and license information
 */
/**
 * A GUI window (a viewer) for viewing patterns found by data mining
 * algorithms.
 * <p>
 * Layout: a toolbar (layout / sort / active-filter indicator) on top, a
 * resizable left sidebar (search, filters, statistics, size histogram) with
 * collapsible sections, the pattern cards in the center, and a pagination bar
 * and status bar at the bottom.
 *
 * @author Philippe Fournier-Viger 2025
 */
public class VisualPatternViewer extends JFrame {

	private static final long serialVersionUID = 3545413603350311234L;

	private static final String WINDOW_TITLE = "SPMF Visual Pattern Viewer ";
	static final String DEFAULT_EXPORT_FILENAME = "patterns_visualization.png";

	static final int SIDEBAR_WIDTH = 380;
	private static final int SIDEBAR_MIN_WIDTH = 260;
	private static final int SIDEBAR_SECTION_GAP = 8;

	/** Datasets larger than this threshold automatically activate pagination. */
	private static final int LARGE_DATASET_THRESHOLD = 200;
	private static final int DEFAULT_PAGE_SIZE = 50;
	/** Ask for confirmation before "Show all" above this many patterns. */
	private static final int SHOW_ALL_WARNING_THRESHOLD = 2000;
	private static final String SHOW_ALL = "Show all";

	/** Delay (ms) between the last keystroke and the live search. */
	private static final int SEARCH_DELAY_MS = 300;

	private final String filePath;
	LayoutMode mode = LayoutMode.GRID;
	private final PatternsPanel patternsPanel;
	private JScrollPane patternScrollPane;

	// --- Toolbar ---
	private JToolBar toolbar;
	private final Map<LayoutMode, JToggleButton> layoutButtons = new EnumMap<>(LayoutMode.class);
	private final Map<LayoutMode, JRadioButtonMenuItem> layoutMenuItems = new EnumMap<>(LayoutMode.class);
	private JComboBox<String> sortCombo;
	private final Map<String, JRadioButtonMenuItem> sortMenuItems = new LinkedHashMap<>();
	private boolean suppressSortEvents;
	private JButton filterChip;
	private JToggleButton sidebarToggle;
	private JCheckBoxMenuItem sidebarMenuItem;

	// --- Sidebar ---
	private JSplitPane splitPane;
	private int lastDividerLocation = SIDEBAR_WIDTH;
	private JPanel sidebarPanel;
	private JScrollPane sidebarScroll;
	private SidebarSection searchSection;
	private SidebarSection filterSection;
	private SidebarSection statsSection;
	private SidebarSection histogramSection;
	private StatisticsPanel statisticsPanel;
	private SizeDistributionHistogram histogramPanel;
	private PlaceholderTextField searchField;
	private JButton clearSearchButton;
	private Timer searchTimer;
	private final Map<String, MeasureFilterRow> filterRows = new LinkedHashMap<>();
	private JButton clearAllButton;
	private boolean suppressFilterEvents;

	// --- Status bar ---
	private JPanel statusBar;
	private JLabel statusLabel;
	private JLabel countLabel;

	// --- Pagination bar ---
	private JPanel paginationBar;
	private JPanel navPanel;
	private JButton btnFirst;
	private JButton btnPrev;
	private JButton btnNext;
	private JButton btnLast;
	private JTextField pageField;
	private JLabel pageTotalLabel;
	private JLabel rangeLabel;
	private JComboBox<String> pageSizeCombo;

	// ---------------------------------------------------------------
	// Constructor
	// ---------------------------------------------------------------

	/**
	 * @param runAsStandaloneProgram if true, closing the window exits the JVM
	 * @param filePath               path to the pattern file to visualize
	 * @param patternType            type of pattern to display
	 * @throws IOException if error reading the input file
	 */
	public VisualPatternViewer(boolean runAsStandaloneProgram, String filePath, PatternType patternType)
			throws IOException {
		super(WINDOW_TITLE + Main.SPMF_VERSION);
		setDefaultCloseOperation(runAsStandaloneProgram ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
		this.filePath = filePath;

		PatternsPanel panel = PatternPanelFactory.getPatternPanel(filePath, patternType, mode);
		if (panel == null) {
			throw new IOException("This type of pattern cannot be displayed by the viewer: " + patternType);
		}
		patternsPanel = panel;
		patternsPanel.setClearFiltersAction(this::clearAllFilters);

		if (patternsPanel.getTotalPatternCount() > LARGE_DATASET_THRESHOLD) {
			patternsPanel.setPageSize(DEFAULT_PAGE_SIZE);
		}

		Map<String, Double> minMap = new LinkedHashMap<>();
		Map<String, Double> maxMap = new LinkedHashMap<>();
		for (String measure : patternsPanel.getAllMeasures()) {
			Double minVal = patternsPanel.getMinForMeasureOriginal(measure);
			Double maxVal = patternsPanel.getMaxForMeasureOriginal(measure);
			if (minVal != null && maxVal != null) {
				minMap.put(measure, minVal);
				maxMap.put(measure, maxVal);
			}
		}

		buildSidebar(minMap, maxMap);
		toolbar = buildToolbar();
		paginationBar = buildPaginationBar();
		statusBar = buildStatusBar();

		// === Layout assembly ===
		patternScrollPane = new JScrollPane(patternsPanel);
		patternScrollPane.setBorder(BorderFactory.createEmptyBorder());
		patternScrollPane.getViewport().setBackground(ViewerStyle.CANVAS_BG);

		splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, sidebarScroll, patternScrollPane);
		splitPane.setBorder(null);
		splitPane.setDividerSize(6);
		splitPane.setResizeWeight(0); // extra space goes to the patterns
		splitPane.setDividerLocation(SIDEBAR_WIDTH);

		JPanel southPanel = new JPanel(new BorderLayout());
		southPanel.add(paginationBar, BorderLayout.NORTH);
		southPanel.add(statusBar, BorderLayout.SOUTH);

		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(toolbar, BorderLayout.NORTH);
		getContentPane().add(splitPane, BorderLayout.CENTER);
		getContentPane().add(southPanel, BorderLayout.SOUTH);

		setJMenuBar(createMenuBar());
		installKeyBindings();
		setTitle(WINDOW_TITLE + Main.SPMF_VERSION + " - " + new File(filePath).getName());

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent e) {
				patternsPanel.requestFocusInWindow();
			}
		});

		configureFrameSize();
		refreshPaginationBar();
		updateStatusBar();
	}

	// ---------------------------------------------------------------
	// Toolbar
	// ---------------------------------------------------------------

	private JToolBar buildToolbar() {
		JToolBar tb = new JToolBar();
		tb.setFloatable(false);
		tb.setRollover(true);
		tb.setOpaque(true);
		tb.setBackground(ViewerStyle.CARD_BG);
		tb.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, ViewerStyle.CARD_BORDER),
				BorderFactory.createEmptyBorder(4, 8, 4, 8)));

		// Sidebar toggle
		sidebarToggle = new JToggleButton("\u2630 Sidebar", true);
		ViewerStyle.styleButton(sidebarToggle);
		sidebarToggle.setFocusable(false);
		sidebarToggle.setToolTipText("Show / hide the search, filter and statistics panel (" + menuKeyName() + "+B)");
		sidebarToggle.addActionListener(e -> setSidebarVisible(sidebarToggle.isSelected()));
		tb.add(sidebarToggle);
		tb.addSeparator(new Dimension(14, 0));

		// Layout
		tb.add(toolbarLabel("Layout:"));
		tb.add(Box.createHorizontalStrut(4));
		ButtonGroup layoutGroup = new ButtonGroup();
		for (LayoutMode lm : LayoutMode.values()) {
			JToggleButton b = new JToggleButton(layoutLabel(lm), lm == mode);
			ViewerStyle.styleButton(b);
			b.setFocusable(false);
			b.setToolTipText(layoutTooltip(lm));
			b.addActionListener(e -> setLayoutMode(lm));
			layoutGroup.add(b);
			layoutButtons.put(lm, b);
			tb.add(b);
			tb.add(Box.createHorizontalStrut(2));
		}

		// Sort
		List<String> orders = patternsPanel.getListOfSortingOrders();
		if (orders != null && !orders.isEmpty()) {
			tb.addSeparator(new Dimension(14, 0));
			tb.add(toolbarLabel("Sort:"));
			tb.add(Box.createHorizontalStrut(4));
			sortCombo = new JComboBox<>(orders.toArray(new String[0]));
			sortCombo.setSelectedIndex(-1); // file order until the user picks one
			sortCombo.setFont(ViewerStyle.measureValueFont());
			sortCombo.setFocusable(false);
			sortCombo.setMaximumSize(new Dimension(280, 26));
			sortCombo.setToolTipText("Change the order in which the patterns are displayed");
			sortCombo.setRenderer(new DefaultListCellRenderer() {
				private static final long serialVersionUID = 1L;

				@Override
				public Component getListCellRendererComponent(JList<?> list, Object value, int index,
						boolean isSelected, boolean cellHasFocus) {
					super.getListCellRendererComponent(list, value == null ? "Sort by\u2026" : value, index,
							isSelected, cellHasFocus);
					if (value == null) {
						setForeground(ViewerStyle.TEXT_SECONDARY);
					}
					return this;
				}
			});
			sortCombo.addActionListener(e -> {
				Object sel = sortCombo.getSelectedItem();
				if (!suppressSortEvents && sel != null) {
					applySort((String) sel);
				}
			});
			tb.add(sortCombo);
		}

		tb.add(Box.createHorizontalGlue());

		// Active-filter indicator (hidden until a filter is active)
		filterChip = new JButton();
		ViewerStyle.styleButton(filterChip);
		filterChip.setFocusable(false);
		filterChip.setForeground(ViewerStyle.ACCENT_DARK);
		filterChip.setBackground(ViewerStyle.ACCENT_SOFT);
		filterChip.setToolTipText("A search or filter is active. Click to remove it and show all patterns.");
		filterChip.setVisible(false);
		filterChip.addActionListener(e -> clearAllFilters());
		tb.add(filterChip);
		return tb;
	}

	private static JLabel toolbarLabel(String text) {
		JLabel l = new JLabel(text);
		l.setFont(ViewerStyle.measureValueFont());
		l.setForeground(ViewerStyle.TEXT_SECONDARY);
		return l;
	}

	private static String layoutLabel(LayoutMode lm) {
		switch (lm.name()) {
		case "GRID":
			return "\u25A6 Grid";
		case "VERTICAL":
			return "\u2630 List";
		case "HORIZONTAL":
			return "\u2A1D Row";
		default:
			return capitalize(lm.name());
		}
	}

	private static String layoutTooltip(LayoutMode lm) {
		switch (lm.name()) {
		case "GRID":
			return "Grid: cards arranged in rows and columns (default)";
		case "VERTICAL":
			return "List: one card per line, from top to bottom";
		case "HORIZONTAL":
			return "Row: all cards on one line, from left to right";
		default:
			return capitalize(lm.name());
		}
	}

	private void setLayoutMode(LayoutMode lm) {
		if (lm == null) {
			return;
		}
		mode = lm;
		JToggleButton b = layoutButtons.get(lm);
		if (b != null && !b.isSelected()) {
			b.setSelected(true);
		}
		JRadioButtonMenuItem mi = layoutMenuItems.get(lm);
		if (mi != null && !mi.isSelected()) {
			mi.setSelected(true);
		}
		if (patternsPanel.getLayoutMode() != lm) {
			patternsPanel.setLayoutModeWithCallback(lm, this::scrollPatternPanelToTop);
		}
	}

	private void applySort(String order) {
		try {
			patternsPanel.sortPatterns(order);
		} catch (Exception ex) {
			showErrorDialog(this, "Sort failed: " + ex.getMessage());
			return;
		}
		suppressSortEvents = true;
		try {
			if (sortCombo != null && !order.equals(sortCombo.getSelectedItem())) {
				sortCombo.setSelectedItem(order);
			}
			JRadioButtonMenuItem item = sortMenuItems.get(order);
			if (item != null && !item.isSelected()) {
				item.setSelected(true);
			}
		} finally {
			suppressSortEvents = false;
		}
		afterContentChanged();
	}

	/** Everything to refresh when the set / order of displayed patterns changes. */
	private void afterContentChanged() {
		scrollPatternPanelToTop();
		refreshPaginationBar();
		updateStatusBar();
	}

	// ---------------------------------------------------------------
	// Sidebar
	// ---------------------------------------------------------------

	private void buildSidebar(Map<String, Double> minMap, Map<String, Double> maxMap) {
		sidebarPanel = new ScrollableColumn();
		sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
		sidebarPanel.setBackground(ViewerStyle.SIDEBAR_BG);
		sidebarPanel.setOpaque(true);
		sidebarPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		searchSection = new SidebarSection("Search", buildSearchContent());
		filterSection = new SidebarSection("Filters", buildFilterContent(minMap, maxMap));
		statisticsPanel = new StatisticsPanel(patternsPanel.getAllMeasures());
		statsSection = new SidebarSection("Statistics", statisticsPanel);
		histogramPanel = new SizeDistributionHistogram();
		histogramSection = new SidebarSection("Size distribution", histogramPanel);

		for (SidebarSection section : List.of(searchSection, filterSection, statsSection, histogramSection)) {
			sidebarPanel.add(section);
			sidebarPanel.add(Box.createVerticalStrut(SIDEBAR_SECTION_GAP));
		}
		sidebarPanel.add(Box.createVerticalGlue());

		sidebarScroll = new JScrollPane(sidebarPanel);
		sidebarScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		sidebarScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		sidebarScroll.setBorder(BorderFactory.createEmptyBorder());
		sidebarScroll.getViewport().setBackground(ViewerStyle.SIDEBAR_BG);
		sidebarScroll.getVerticalScrollBar().setUnitIncrement(16);
		sidebarScroll.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
		sidebarScroll.setMinimumSize(new Dimension(SIDEBAR_MIN_WIDTH, 200));
	}

	private JComponent buildSearchContent() {
		JPanel panel = new JPanel(new BorderLayout(4, 4));
		panel.setOpaque(false);

		searchField = new PlaceholderTextField("Type items to search\u2026", 10);
		searchField.setFont(ViewerStyle.baseFont());
		searchField.setToolTipText("Patterns are filtered as you type. Enter applies immediately, Esc clears the text.");

		clearSearchButton = new JButton("\u2715");
		clearSearchButton.setToolTipText("Clear search text (Esc)");
		clearSearchButton.setFocusable(false);
		clearSearchButton.setBorderPainted(false);
		clearSearchButton.setContentAreaFilled(false);
		clearSearchButton.setMargin(new Insets(0, 4, 0, 4));
		clearSearchButton.setForeground(ViewerStyle.TEXT_SECONDARY);
		clearSearchButton.setVisible(false);
		clearSearchButton.addActionListener(e -> {
			searchField.setText("");
			searchField.requestFocusInWindow();
		});

		JPanel fieldRow = new JPanel(new BorderLayout());
		fieldRow.setOpaque(false);
		fieldRow.add(searchField, BorderLayout.CENTER);
		fieldRow.add(clearSearchButton, BorderLayout.EAST);
		panel.add(fieldRow, BorderLayout.CENTER);

		JLabel hint = new JLabel("Shows only the patterns containing all the words typed");
		hint.setFont(ViewerStyle.smallFont());
		hint.setForeground(ViewerStyle.TEXT_SECONDARY);
		panel.add(hint, BorderLayout.SOUTH);

		// Live search with a small delay
		searchTimer = new Timer(SEARCH_DELAY_MS, e -> applyCurrentFilters());
		searchTimer.setRepeats(false);
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				onSearchTextChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				onSearchTextChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				onSearchTextChanged();
			}
		});
		searchField.addActionListener(e -> {
			searchTimer.stop();
			applyCurrentFilters();
		});
		// Esc in the search field clears only the search text
		searchField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
				"clearSearchText");
		searchField.getActionMap().put("clearSearchText", new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				if (!searchField.getText().isEmpty()) {
					searchField.setText("");
				}
			}
		});
		return panel;
	}

	private void onSearchTextChanged() {
		clearSearchButton.setVisible(!searchField.getText().isEmpty());
		if (!suppressFilterEvents) {
			searchTimer.restart();
		}
	}

	private JComponent buildFilterContent(Map<String, Double> minMap, Map<String, Double> maxMap) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);

		if (minMap.isEmpty()) {
			JLabel none = new JLabel("No numeric measures available");
			none.setFont(ViewerStyle.smallFont().deriveFont(java.awt.Font.ITALIC));
			none.setForeground(ViewerStyle.TEXT_SECONDARY);
			none.setAlignmentX(LEFT_ALIGNMENT);
			panel.add(none);
		} else {
			JLabel hint = new JLabel("Keep only the patterns whose measure satisfies a threshold");
			hint.setFont(ViewerStyle.smallFont());
			hint.setForeground(ViewerStyle.TEXT_SECONDARY);
			hint.setAlignmentX(LEFT_ALIGNMENT);
			panel.add(hint);
			panel.add(Box.createVerticalStrut(6));
			for (Map.Entry<String, Double> entry : minMap.entrySet()) {
				String measure = entry.getKey();
				MeasureFilterRow row = new MeasureFilterRow(measure, entry.getValue(), maxMap.get(measure),
						this::onFilterChanged);
				row.setAlignmentX(LEFT_ALIGNMENT);
				filterRows.put(measure, row);
				panel.add(row);
				panel.add(Box.createVerticalStrut(6));
			}
		}

		clearAllButton = new JButton("Clear search & filters");
		ViewerStyle.styleButton(clearAllButton);
		clearAllButton.setEnabled(false);
		clearAllButton.setToolTipText("Remove the search text and all measure filters");
		clearAllButton.addActionListener(e -> clearAllFilters());
		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		buttonRow.setOpaque(false);
		buttonRow.add(clearAllButton);
		buttonRow.setAlignmentX(LEFT_ALIGNMENT);
		buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttonRow.getPreferredSize().height));
		panel.add(Box.createVerticalStrut(2));
		panel.add(buttonRow);
		return panel;
	}

	private void onFilterChanged() {
		if (!suppressFilterEvents) {
			applyCurrentFilters();
		}
	}

	/**
	 * Reads the search text and every filter row and applies the resulting
	 * filter to the patterns panel (or clears it if nothing is active).
	 */
	private void applyCurrentFilters() {
		String text = searchField == null ? "" : searchField.getText().trim();
		Map<String, Double> thresholds = new LinkedHashMap<>();
		Map<String, String> operators = new LinkedHashMap<>();
		for (MeasureFilterRow row : filterRows.values()) {
			if (row.isActive()) {
				thresholds.put(row.getMeasure(), row.getThreshold());
				operators.put(row.getMeasure(), row.getOperator());
			}
		}
		boolean active = !text.isEmpty() || !thresholds.isEmpty();
		if (active) {
			patternsPanel.applySearchAndFilters(text, thresholds, operators);
		} else {
			patternsPanel.clearSearchAndFilters();
		}

		if (clearAllButton != null) {
			clearAllButton.setEnabled(active);
		}
		if (searchSection != null) {
			searchSection.setInfo(text.isEmpty() ? "" : "active");
		}
		if (filterSection != null) {
			filterSection.setInfo(thresholds.isEmpty() ? "" : thresholds.size() + " active");
		}
		if (filterChip != null) {
			if (active) {
				filterChip.setText(String.format("%d of %d shown   \u2715 Clear filters",
						patternsPanel.getNumberOfVisiblePatterns(), patternsPanel.getTotalPatternCount()));
			}
			filterChip.setVisible(active);
			toolbar.revalidate();
			toolbar.repaint();
		}
		afterContentChanged();
	}

	private void clearAllFilters() {
		suppressFilterEvents = true;
		try {
			if (searchTimer != null) {
				searchTimer.stop();
			}
			searchField.setText("");
			for (MeasureFilterRow row : filterRows.values()) {
				row.reset();
			}
		} finally {
			suppressFilterEvents = false;
		}
		applyCurrentFilters();
	}

	private void focusSearchField() {
		setSidebarVisible(true);
		searchSection.setCollapsed(false);
		searchField.requestFocusInWindow();
		searchField.selectAll();
		SwingUtilities.invokeLater(() -> sidebarPanel.scrollRectToVisible(searchSection.getBounds()));
	}

	private void setSidebarVisible(boolean visible) {
		if (sidebarScroll.isVisible() != visible) {
			if (!visible) {
				lastDividerLocation = splitPane.getDividerLocation();
			}
			sidebarScroll.setVisible(visible);
			if (visible) {
				splitPane.setDividerLocation(Math.max(SIDEBAR_MIN_WIDTH, lastDividerLocation));
			}
			splitPane.revalidate();
			splitPane.repaint();
		}
		if (sidebarToggle != null && sidebarToggle.isSelected() != visible) {
			sidebarToggle.setSelected(visible);
		}
		if (sidebarMenuItem != null && sidebarMenuItem.isSelected() != visible) {
			sidebarMenuItem.setSelected(visible);
		}
	}

	/**
	 * Recomputes everything that depends on the set of visible patterns.
	 */
	private void refreshStatistics() {
		Map<String, List<Double>> values = new LinkedHashMap<>();
		for (String measure : patternsPanel.getAllMeasures()) {
			values.put(measure, new ArrayList<>());
		}
		patternsPanel.populateMeasureValues(values);

		Map<String, DescriptiveStats> stats = new LinkedHashMap<>();
		for (Map.Entry<String, List<Double>> entry : values.entrySet()) {
			stats.put(entry.getKey(), DescriptiveStats.of(entry.getValue()));
			MeasureFilterRow row = filterRows.get(entry.getKey());
			if (row != null) {
				row.getBar().updateDistribution(entry.getValue());
			}
		}
		if (statisticsPanel != null) {
			statisticsPanel.update(patternsPanel.getTotalPatternCount(), patternsPanel.getNumberOfVisiblePatterns(),
					stats);
		}
		if (histogramPanel != null) {
			histogramPanel.updateData(patternsPanel);
			histogramSection.setInfo(histogramPanel.getSummaryText());
		}
	}

	/** A panel that fills the viewport width but scrolls vertically. */
	private static final class ScrollableColumn extends JPanel implements Scrollable {
		private static final long serialVersionUID = 1L;

		@Override
		public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
			return Math.max(16, visibleRect.height - 16);
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			return false;
		}
	}

	// ---------------------------------------------------------------
	// Status bar
	// ---------------------------------------------------------------

	private JPanel buildStatusBar() {
		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(ViewerStyle.CARD_BG);
		bar.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, ViewerStyle.CARD_BORDER),
				BorderFactory.createEmptyBorder(4, 10, 4, 10)));
		statusLabel = new JLabel();
		statusLabel.setFont(ViewerStyle.measureValueFont());
		statusLabel.setForeground(ViewerStyle.TEXT_SECONDARY);
		countLabel = new JLabel();
		countLabel.setFont(ViewerStyle.measureNameFont());
		countLabel.setForeground(ViewerStyle.TEXT_PRIMARY);
		bar.add(statusLabel, BorderLayout.WEST);
		bar.add(countLabel, BorderLayout.EAST);
		return bar;
	}

	private void updateStatusBar() {
		int total = patternsPanel.getTotalPatternCount();
		int visible = patternsPanel.getNumberOfVisiblePatterns();
		boolean filtered = total != visible;
		countLabel.setText(filtered ? visible + " / " + total + " patterns" : total + " patterns");

		Set<String> measures = patternsPanel.getAllMeasures();
		StringBuilder sb = new StringBuilder(new File(filePath).getName());
		sb.append(" \u00B7 ").append(total).append(total == 1 ? " pattern" : " patterns");
		if (!measures.isEmpty()) {
			sb.append(" \u00B7 ").append(measures.size()).append(measures.size() == 1 ? " measure" : " measures");
		}
		if (filtered) {
			sb.append(" \u00B7 filtered: ").append(visible).append(" shown");
		}
		statusLabel.setText(sb.toString());
		refreshStatistics();
	}

	// ---------------------------------------------------------------
	// Pagination bar
	// ---------------------------------------------------------------

	private JPanel buildPaginationBar() {
		JPanel bar = new JPanel(new BorderLayout(8, 0));
		bar.setBackground(ViewerStyle.CARD_BG);
		bar.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, ViewerStyle.CARD_BORDER),
				BorderFactory.createEmptyBorder(4, 8, 4, 8)));

		// Navigation: |<  <  Page [ 3 ] of 12  >  >|
		navPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		navPanel.setOpaque(false);
		btnFirst = navButton("|\u25C0", "First page (Alt+Home)", () -> navigateToPage(0));
		btnPrev = navButton("\u25C0", "Previous page (Alt+\u2190)",
				() -> navigateToPage(patternsPanel.getCurrentPage() - 1));
		btnNext = navButton("\u25B6", "Next page (Alt+\u2192)",
				() -> navigateToPage(patternsPanel.getCurrentPage() + 1));
		btnLast = navButton("\u25B6|", "Last page (Alt+End)",
				() -> navigateToPage(patternsPanel.getTotalPages() - 1));

		JLabel pageLabel = toolbarLabel("Page");
		pageField = new JTextField(3);
		pageField.setFont(ViewerStyle.measureValueFont());
		pageField.setHorizontalAlignment(SwingConstants.CENTER);
		pageField.setToolTipText("Type a page number and press Enter");
		pageField.addActionListener(e -> {
			try {
				navigateToPage(Integer.parseInt(pageField.getText().trim()) - 1);
			} catch (NumberFormatException ex) {
				refreshPaginationBar();
			}
			patternsPanel.requestFocusInWindow();
		});
		pageTotalLabel = toolbarLabel("of 1");

		navPanel.add(btnFirst);
		navPanel.add(btnPrev);
		navPanel.add(Box.createHorizontalStrut(4));
		navPanel.add(pageLabel);
		navPanel.add(pageField);
		navPanel.add(pageTotalLabel);
		navPanel.add(Box.createHorizontalStrut(4));
		navPanel.add(btnNext);
		navPanel.add(btnLast);

		rangeLabel = new JLabel("", SwingConstants.CENTER);
		rangeLabel.setFont(ViewerStyle.measureValueFont());
		rangeLabel.setForeground(ViewerStyle.TEXT_PRIMARY);

		JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		sizePanel.setOpaque(false);
		JLabel sizeLabel = toolbarLabel("Patterns per page:");
		pageSizeCombo = new JComboBox<>(new String[] { "10", "25", "50", "100", "200", "500", SHOW_ALL });
		pageSizeCombo.setFont(ViewerStyle.measureValueFont());
		pageSizeCombo.setPreferredSize(new Dimension(92, 24));
		pageSizeCombo.setFocusable(false);
		pageSizeCombo.setToolTipText("How many patterns to display per page. Fewer patterns = faster display.");
		syncPageSizeCombo();
		pageSizeCombo.addActionListener(e -> onPageSizeSelected());
		sizePanel.add(sizeLabel);
		sizePanel.add(pageSizeCombo);

		bar.add(navPanel, BorderLayout.WEST);
		bar.add(rangeLabel, BorderLayout.CENTER);
		bar.add(sizePanel, BorderLayout.EAST);
		return bar;
	}

	private static JButton navButton(String text, String tooltip, Runnable action) {
		JButton b = new JButton(text);
		b.setToolTipText(tooltip);
		ViewerStyle.styleButton(b);
		b.setFocusable(false);
		b.setMargin(new Insets(2, 7, 2, 7));
		b.addActionListener(e -> action.run());
		return b;
	}

	private void onPageSizeSelected() {
		String sel = (String) pageSizeCombo.getSelectedItem();
		if (sel == null) {
			return;
		}
		int newSize = SHOW_ALL.equals(sel) ? Integer.MAX_VALUE : Integer.parseInt(sel);
		if (newSize == patternsPanel.getPageSize()) {
			return;
		}
		int visible = patternsPanel.getNumberOfVisiblePatterns();
		if (newSize == Integer.MAX_VALUE && visible > SHOW_ALL_WARNING_THRESHOLD) {
			int r = JOptionPane.showConfirmDialog(this,
					"Displaying " + visible + " patterns at once may make the viewer slow.\nContinue?",
					"Show all patterns", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (r != JOptionPane.YES_OPTION) {
				syncPageSizeCombo();
				return;
			}
		}
		patternsPanel.setPageSize(newSize);
		afterContentChanged();
	}

	/** Selects the combo entry matching the current page size, silently. */
	private void syncPageSizeCombo() {
		if (pageSizeCombo == null) {
			return;
		}
		int current = patternsPanel.getPageSize();
		String target = (current == Integer.MAX_VALUE) ? SHOW_ALL : String.valueOf(current);
		var listeners = pageSizeCombo.getActionListeners();
		for (var l : listeners) {
			pageSizeCombo.removeActionListener(l);
		}
		try {
			for (int i = 0; i < pageSizeCombo.getItemCount(); i++) {
				if (target.equals(pageSizeCombo.getItemAt(i))) {
					pageSizeCombo.setSelectedIndex(i);
					break;
				}
			}
		} finally {
			for (var l : listeners) {
				pageSizeCombo.addActionListener(l);
			}
		}
	}

	private void navigateToPage(int page) {
		if (!patternsPanel.isPaginated()) {
			return;
		}
		patternsPanel.goToPage(page);
		afterContentChanged();
	}

	private void refreshPaginationBar() {
		if (paginationBar == null) {
			return;
		}
		boolean paginated = patternsPanel.isPaginated();
		int visible = patternsPanel.getNumberOfVisiblePatterns();
		int cp = patternsPanel.getCurrentPage();
		int tp = patternsPanel.getTotalPages();

		navPanel.setVisible(paginated);
		if (paginated) {
			btnFirst.setEnabled(cp > 0);
			btnPrev.setEnabled(cp > 0);
			btnNext.setEnabled(cp < tp - 1);
			btnLast.setEnabled(cp < tp - 1);
			pageField.setText(String.valueOf(cp + 1));
			pageTotalLabel.setText("of " + tp);
			long ps = patternsPanel.getPageSize();
			long from = visible == 0 ? 0 : cp * ps + 1;
			long to = Math.min(visible, (cp + 1) * ps);
			rangeLabel.setText(visible == 0 ? "No patterns to show"
					: String.format("Showing %d\u2013%d of %d", from, to, visible));
		} else {
			rangeLabel.setText(visible == 0 ? "No patterns to show" : "Showing all " + visible + " patterns");
		}
		syncPageSizeCombo();
		paginationBar.revalidate();
		paginationBar.repaint();
	}

	private void scrollPatternPanelToTop() {
		if (patternScrollPane != null) {
			SwingUtilities.invokeLater(() -> patternScrollPane.getViewport().setViewPosition(new Point(0, 0)));
		}
	}

	// ---------------------------------------------------------------
	// Keyboard shortcuts (window-wide)
	// ---------------------------------------------------------------

	private void installKeyBindings() {
		InputMap im = getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		ActionMap am = getRootPane().getActionMap();
		bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK), "nextPage",
				() -> navigateToPage(patternsPanel.getCurrentPage() + 1));
		bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK), "prevPage",
				() -> navigateToPage(patternsPanel.getCurrentPage() - 1));
		bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_HOME, InputEvent.ALT_DOWN_MASK), "firstPage",
				() -> navigateToPage(0));
		bind(im, am, KeyStroke.getKeyStroke(KeyEvent.VK_END, InputEvent.ALT_DOWN_MASK), "lastPage",
				() -> navigateToPage(patternsPanel.getTotalPages() - 1));
	}

	private static void bind(InputMap im, ActionMap am, KeyStroke ks, String name, Runnable r) {
		im.put(ks, name);
		am.put(name, new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(ActionEvent e) {
				r.run();
			}
		});
	}

	// ---------------------------------------------------------------
	// Menu bar
	// ---------------------------------------------------------------

	private JMenuBar createMenuBar() {
		JMenuBar menuBar = new JMenuBar();
		int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

		// ---- File ----
		JMenu menuFile = new JMenu("File");
		menuFile.setMnemonic(KeyEvent.VK_F);
		menuFile.add(menuItem("Open in system text editor", null, this::openInSystemEditor));
		menuFile.add(menuItem("Export as PNG\u2026", KeyStroke.getKeyStroke(KeyEvent.VK_E, menuMask),
				() -> new ExportDialog(this, patternsPanel).setVisible(true)));
		menuFile.addSeparator();
		menuFile.add(menuItem("Close", KeyStroke.getKeyStroke(KeyEvent.VK_W, menuMask),
				() -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING))));

		// ---- View ----
		JMenu menuView = new JMenu("View");
		menuView.setMnemonic(KeyEvent.VK_V);
		sidebarMenuItem = new JCheckBoxMenuItem("Sidebar", true);
		sidebarMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_B, menuMask));
		sidebarMenuItem.addActionListener(e -> setSidebarVisible(sidebarMenuItem.isSelected()));
		menuView.add(sidebarMenuItem);
		menuView.add(toggleItem("Toolbar", toolbar));
		menuView.add(toggleItem("Pagination bar", paginationBar));
		menuView.add(toggleItem("Status bar", statusBar));
		menuView.addSeparator();

		JMenu menuLayout = new JMenu("Layout");
		ButtonGroup layoutGroup = new ButtonGroup();
		for (LayoutMode lm : LayoutMode.values()) {
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(layoutLabel(lm).substring(2)); // drop the icon
			layoutGroup.add(item);
			item.setSelected(lm == mode);
			item.addActionListener(e -> setLayoutMode(lm));
			layoutMenuItems.put(lm, item);
			menuLayout.add(item);
		}
		menuView.add(menuLayout);

		List<String> orders = patternsPanel.getListOfSortingOrders();
		if (orders != null && !orders.isEmpty()) {
			JMenu menuSort = new JMenu("Sort by");
			ButtonGroup sortGroup = new ButtonGroup();
			for (String opt : orders) {
				JRadioButtonMenuItem item = new JRadioButtonMenuItem(opt);
				sortGroup.add(item);
				item.addActionListener(e -> {
					if (!suppressSortEvents) {
						applySort(opt);
					}
				});
				sortMenuItems.put(opt, item);
				menuSort.add(item);
			}
			menuView.add(menuSort);
		}

		// ---- Tools ----
		JMenu menuTools = new JMenu("Tools");
		menuTools.setMnemonic(KeyEvent.VK_T);
		menuTools.add(menuItem("Find\u2026", KeyStroke.getKeyStroke(KeyEvent.VK_F, menuMask), this::focusSearchField));
		menuTools.add(menuItem("Clear search & filters",
				KeyStroke.getKeyStroke(KeyEvent.VK_F, menuMask | InputEvent.SHIFT_DOWN_MASK), this::clearAllFilters));
		menuTools.addSeparator();
		menuTools.add(menuItem("Measure distribution histogram\u2026", KeyStroke.getKeyStroke(KeyEvent.VK_H, menuMask),
				this::showMeasureHistogram));

		// ---- Help ----
		JMenu menuHelp = new JMenu("Help");
		menuHelp.setMnemonic(KeyEvent.VK_H);
		menuHelp.add(menuItem("Quick help & keyboard shortcuts", KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
				this::showQuickHelp));

		menuBar.add(menuFile);
		menuBar.add(menuView);
		menuBar.add(menuTools);
		menuBar.add(menuHelp);
		return menuBar;
	}

	private static JMenuItem menuItem(String text, KeyStroke accelerator, Runnable action) {
		JMenuItem item = new JMenuItem(text);
		if (accelerator != null) {
			item.setAccelerator(accelerator);
		}
		item.addActionListener(e -> action.run());
		return item;
	}

	/** A check-box menu item that shows / hides a component. */
	private JCheckBoxMenuItem toggleItem(String text, JComponent target) {
		JCheckBoxMenuItem item = new JCheckBoxMenuItem(text, target != null && target.isVisible());
		item.addActionListener(e -> {
			if (target == null) {
				return;
			}
			target.setVisible(item.isSelected());
			getContentPane().revalidate();
			getContentPane().repaint();
		});
		return item;
	}

	private void showMeasureHistogram() {
		try {
			Map<String, List<Double>> mv = MeasureValuesExtractor.extractMeasureValues(patternsPanel);
			if (mv == null || mv.values().stream().allMatch(List::isEmpty)) {
				JOptionPane.showMessageDialog(this, "No numeric measure values found.", "No data",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			new MeasureHistogramDialog(this, patternsPanel, mv).setVisible(true);
		} catch (Exception ex) {
			showErrorDialog(this, "Failed to create histogram: " + ex.getMessage());
		}
	}

	private void showQuickHelp() {
		String k = menuKeyName();
		String html = "<html><body style='width:420px;font-family:sans-serif'>"
				+ "<h3 style='margin-top:0'>How to use the viewer</h3><ul>"
				+ "<li><b>Search</b> (sidebar): type item names separated by spaces; only patterns containing "
				+ "<i>all</i> of them are shown. The list updates as you type.</li>"
				+ "<li><b>Filters</b> (sidebar): keep only patterns whose measure (support, confidence, \u2026) is "
				+ "above or below a threshold. Several filters can be combined.</li>"
				+ "<li><b>Sort</b> and <b>Layout</b>: use the toolbar at the top.</li>"
				+ "<li>Click a sidebar section title to collapse / expand it. Drag the divider to resize the sidebar.</li>"
				+ "<li>Hover a pattern card for details. Large files are split into pages (see bottom bar).</li>"
				+ "</ul><h3>Keyboard shortcuts</h3><table cellpadding='2'>"
				+ row(k + "+F", "Go to the search box") + row("Esc (in search box)", "Clear the search text")
				+ row(k + "+Shift+F", "Clear search and all filters") + row(k + "+B", "Show / hide the sidebar")
				+ row("Alt+\u2190 / Alt+\u2192", "Previous / next page")
				+ row("Alt+Home / Alt+End", "First / last page")
				+ row("Page Up / Page Down", "Scroll the patterns") + row(k + "+E", "Export as PNG")
				+ row(k + "+H", "Measure distribution histogram") + row(k + "+W", "Close the viewer")
				+ "</table></body></html>";
		JOptionPane.showMessageDialog(this, html, "Quick help", JOptionPane.PLAIN_MESSAGE);
	}

	private static String row(String key, String desc) {
		return "<tr><td><b>" + key + "</b></td><td style='padding-left:14px'>" + desc + "</td></tr>";
	}

	private static String menuKeyName() {
		String os = System.getProperty("os.name", "").toLowerCase();
		return os.contains("mac") ? "Cmd" : "Ctrl";
	}

	private void openInSystemEditor() {
		try {
			if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
				JOptionPane.showMessageDialog(this, "Desktop API not supported on this platform.", "Not supported",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
			File f = new File(filePath);
			if (f.exists() && f.canRead()) {
				Desktop.getDesktop().open(f);
			} else {
				JOptionPane.showMessageDialog(this, "Cannot open file:\n" + filePath, "File not found",
						JOptionPane.ERROR_MESSAGE);
			}
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this, "Failed to open file:\n" + ex.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private static String capitalize(String s) {
		if (s == null || s.isEmpty()) {
			return s;
		}
		return s.charAt(0) + s.substring(1).toLowerCase();
	}

	// ---------------------------------------------------------------
	// Frame size
	// ---------------------------------------------------------------

	private void configureFrameSize() {
		pack();
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		int maxW = (int) (screen.width * 0.9);
		int maxH = (int) (screen.height * 0.9);

		Insets ins = getInsets();
		int chromeH = ins.top + ins.bottom + (getJMenuBar() != null ? getJMenuBar().getPreferredSize().height : 0)
				+ toolbar.getPreferredSize().height + statusBar.getPreferredSize().height
				+ paginationBar.getPreferredSize().height;
		int wantedH = Math.max(600, sidebarPanel.getPreferredSize().height + chromeH);
		int wantedW = Math.max(getWidth(), SIDEBAR_WIDTH + 760);

		setSize(Math.min(wantedW, maxW), Math.min(wantedH, maxH));
		setMinimumSize(new Dimension(SIDEBAR_MIN_WIDTH + 420, 450));
		setLocationRelativeTo(null);
		splitPane.setDividerLocation(SIDEBAR_WIDTH);
	}

	private static void showErrorDialog(JFrame parent, String msg) {
		JOptionPane.showMessageDialog(parent, msg, "Error", JOptionPane.ERROR_MESSAGE);
	}
}