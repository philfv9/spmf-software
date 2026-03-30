package ca.pfv.spmf.gui.visual_pattern_viewer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.ToolTipManager;

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
 */
/**
 * This class implements a GUI Window (a viewer) for viewing rules found by data
 * mining algorithms.
 * <p>
 * It provides sorting controls for different rule measures and an export button
 * to save the visualization as a PNG file.
 * <p>
 * A left sidebar contains summary statistics, a size distribution histogram,
 * and search/filter controls.
 * 
 * @author Philippe Fournier-Viger 2025
 */
public class VisualPatternViewer extends JFrame {
	/** Serial UID */
	private static final long serialVersionUID = 3545413603350311234L;

	/** Window title */
	private static final String WINDOW_TITLE = "SPMF Visual Pattern Viewer ";

	/** Label for exporting to PNG */
	private static final String DEFAULT_EXPORT_FILENAME = "rules_visualization.png";

	/** Preferred width for the left sidebar */
	private static final int SIDEBAR_WIDTH = 330;

	/** Store the path to the original pattern file, so we can re-open it */
	private final String filePath;

	/** Selected layout mode */
	LayoutMode mode = LayoutMode.GRID;

	/** Panel reference for toggling summary statistics */
	private JPanel statsPanel;

	/** Panel for showing patterns */
	private PatternsPanel patternsPanel;

	/** Left sidebar containing stats, histogram, and search */
	private JPanel sidebarPanel;

	/** Panel for search/filtering */
	private JPanel searchPanel;

	/** Panel for summary statistics */
	private JPanel summaryPanel;

	/** Histogram panel for size distribution */
	private SizeDistributionHistogram histogramPanel;

	/** Label for displaying pattern count */
	private JLabel countLabel;

	/** Status bar at the bottom */
	private JPanel statusBar;

	/** Status bar label */
	private JLabel statusLabel;

	/**
	 * Constructor
	 * 
	 * @param runAsStandaloneProgram if true, this tool will be run in standalone
	 *                               mode (close the window will close the program).
	 *                               Otherwise not.
	 * @param filePath               path to the pattern file to visualize
	 * @param patternType            type of pattern to display (e.g., rules vs.
	 *                               itemsets)
	 * @throws IOException if error reading the input file
	 */
	public VisualPatternViewer(boolean runAsStandaloneProgram, String filePath, PatternType patternType)
			throws IOException {
		super(WINDOW_TITLE + Main.SPMF_VERSION);
		if (runAsStandaloneProgram) {
			setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		}

		this.filePath = filePath;

		patternsPanel = PatternPanelFactory.getPatternPanel(filePath, patternType, mode);

		// Retrieve all measures and compute min/max
		Set<String> measures = patternsPanel.getAllMeasures();
		Map<String, Double> minMap = new LinkedHashMap<>();
		Map<String, Double> maxMap = new LinkedHashMap<>();

		for (String measure : measures) {
			Double minVal = patternsPanel.getMinForMeasureOriginal(measure);
			Double maxVal = patternsPanel.getMaxForMeasureOriginal(measure);
			if (minVal != null && maxVal != null) {
				minMap.put(measure, minVal);
				maxMap.put(measure, maxVal);
			}
		}

		// === Build the left sidebar ===
		sidebarPanel = new JPanel();
		sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
		sidebarPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(200, 200, 200)),
				BorderFactory.createEmptyBorder(6, 8, 6, 8)));
		

		// --- Search & Filter section (below histogram) ---
		searchPanel = buildSearchPanel(minMap, maxMap);
		sidebarPanel.add(wrapForSidebar(searchPanel));
		sidebarPanel.add(Box.createVerticalStrut(6));

		// --- Summary Statistics section (top) ---
		summaryPanel = buildSummaryPanel(minMap, maxMap);
		sidebarPanel.add(wrapForSidebar(summaryPanel));
		sidebarPanel.add(Box.createVerticalStrut(6));

		// --- Size Distribution Histogram (below stats) ---
		histogramPanel = new SizeDistributionHistogram();
		histogramPanel.updateData(patternsPanel);
		sidebarPanel.add(wrapForSidebar(histogramPanel));


		// Glue to push everything up
		sidebarPanel.add(Box.createVerticalGlue());

		// Keep statsPanel reference for menu toggle (pointing to summaryPanel)
		this.statsPanel = summaryPanel;

		// === Status bar at the bottom ===
		statusBar = new JPanel(new BorderLayout());
		statusBar.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 200)),
				BorderFactory.createEmptyBorder(3, 8, 3, 8)));
		statusLabel = new JLabel(buildStatusText());
		statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
		statusLabel.setForeground(new Color(80, 80, 80));
		statusBar.add(statusLabel, BorderLayout.WEST);

		countLabel = new JLabel();
		countLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
		countLabel.setForeground(new Color(60, 60, 60));
		updateCountLabel();
		statusBar.add(countLabel, BorderLayout.EAST);

		// === Layout assembly ===
		getContentPane().setLayout(new BorderLayout());
		getContentPane().add(new JScrollPane((JPanel) patternsPanel), BorderLayout.CENTER);

		// Wrap sidebar in a scroll pane so it scrolls if the window is short
		JScrollPane sidebarScroll = new JScrollPane(sidebarPanel);
		sidebarScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		sidebarScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		sidebarScroll.setPreferredSize(new Dimension(SIDEBAR_WIDTH + 30, 0));
		sidebarScroll.setMinimumSize(new Dimension(SIDEBAR_WIDTH + 30, 200));
		sidebarScroll.setBorder(null);
		getContentPane().add(sidebarScroll, BorderLayout.WEST);

		getContentPane().add(statusBar, BorderLayout.SOUTH);

		// === Menu bar ===
		this.setJMenuBar(createMenuBar(this, patternsPanel, this.filePath));

		// === Window title with file name ===
		String fileName = new java.io.File(filePath).getName();
		this.setTitle(WINDOW_TITLE + Main.SPMF_VERSION + " - " + fileName);

		configureFrameSize(this, patternsPanel);
	}

	// ---------------------------------------------------------------
	// Sidebar wrapper helper
	// ---------------------------------------------------------------

	/**
	 * Wraps a component in a panel that forces it to stretch to the full width of
	 * the sidebar. This prevents BoxLayout from sizing children to different widths
	 * based on their individual preferred sizes.
	 *
	 * @param content the component to wrap
	 * @return a wrapper panel using BorderLayout.CENTER
	 */
	private static JPanel wrapForSidebar(JPanel content) {
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.add(content, BorderLayout.CENTER);
		int h = content.getPreferredSize().height;
		wrapper.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, h));
		wrapper.setMinimumSize(new Dimension(SIDEBAR_WIDTH - 20, h));
		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
		return wrapper;
	}

	// ---------------------------------------------------------------
	// Search & Filter panel builder
	// ---------------------------------------------------------------

	/**
	 * Builds the search and filter panel with text search and measure sliders. Uses
	 * a fixed-width layout that doesn't get squeezed.
	 */
	private JPanel buildSearchPanel(Map<String, Double> minMap, Map<String, Double> maxMap) {
		JPanel outerPanel = new JPanel();
		outerPanel.setLayout(new BoxLayout(outerPanel, BoxLayout.Y_AXIS));
		outerPanel.setBorder(BorderFactory.createTitledBorder("Search & Filter"));

		// --- Text search row ---
		JPanel searchRow = new JPanel(new BorderLayout(4, 0));
		searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		JLabel searchLabel = new JLabel(" Search: ");
		JTextField searchField = new JTextField();
		JButton searchButton = new JButton("Go");
		searchButton.setMargin(new Insets(2, 6, 2, 6));
		searchRow.add(searchLabel, BorderLayout.WEST);
		searchRow.add(searchField, BorderLayout.CENTER);
		searchRow.add(searchButton, BorderLayout.EAST);
		outerPanel.add(searchRow);
		outerPanel.add(Box.createVerticalStrut(6));

		// --- Measure sliders ---
		Map<String, JSlider> sliders = new LinkedHashMap<>();
		Map<String, JComboBox<String>> operatorCombos = new LinkedHashMap<>();

		for (String measure : minMap.keySet()) {
			double min = minMap.get(measure);
			double max = maxMap.get(measure);

			// Measure label + operator row
			JPanel labelRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
			labelRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
			JLabel label = new JLabel(measure + ":");
			label.setFont(new Font("SansSerif", Font.BOLD, 11));
			String[] ops = { "\u2265", "\u2264" };
			JComboBox<String> combo = new JComboBox<>(ops);
			combo.setPreferredSize(new Dimension(48, 22));
			labelRow.add(label);
			labelRow.add(combo);
			outerPanel.add(labelRow);

			// Slider with min/max labels
			JPanel sliderPanel = new JPanel(new BorderLayout(2, 0));
			sliderPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
			JSlider slider = new JSlider(0, 1000);
			slider.setPaintTicks(false);
			slider.setPaintLabels(false);
			slider.setPreferredSize(new Dimension(150, 24));

			slider.addMouseMotionListener(new MouseMotionAdapter() {
				@Override
				public void mouseMoved(MouseEvent e) {
					int sliderValue = slider.getValue();
					double realValue = min + ((sliderValue / 1000.0) * (max - min));
					slider.setToolTipText(String.format("%.3f", realValue));
				}
			});

			JLabel minLabel = new JLabel(String.format("%.2f", min));
			minLabel.setFont(new Font("SansSerif", Font.PLAIN, 9));
			JLabel maxLabel = new JLabel(String.format("%.2f", max));
			maxLabel.setFont(new Font("SansSerif", Font.PLAIN, 9));
			sliderPanel.add(minLabel, BorderLayout.WEST);
			sliderPanel.add(slider, BorderLayout.CENTER);
			sliderPanel.add(maxLabel, BorderLayout.EAST);

			outerPanel.add(sliderPanel);
			outerPanel.add(Box.createVerticalStrut(4));

			sliders.put(measure, slider);
			operatorCombos.put(measure, combo);
		}

		// --- Clear button ---
		outerPanel.add(Box.createVerticalStrut(4));
		JPanel clearRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		clearRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		JButton clearButton = new JButton("Clear Filters");
		clearButton.setEnabled(false);
		clearRow.add(clearButton);
		outerPanel.add(clearRow);

		// --- Action listeners ---
		Runnable enableClearButton = () -> clearButton.setEnabled(true);

		searchField.addActionListener(e -> {
			applySearchAndFilter(searchField.getText().trim(), sliders, operatorCombos, minMap, maxMap);
			enableClearButton.run();
		});

		searchButton.addActionListener(e -> {
			applySearchAndFilter(searchField.getText().trim(), sliders, operatorCombos, minMap, maxMap);
			enableClearButton.run();
		});

		clearButton.addActionListener(e -> {
			searchField.setText("");
			for (Map.Entry<String, JSlider> entry : sliders.entrySet()) {
				entry.getValue().setValue(0);
			}
			for (Map.Entry<String, JComboBox<String>> entry : operatorCombos.entrySet()) {
				entry.getValue().setSelectedItem("\u2265");
			}
			patternsPanel.clearSearchAndFilters();
			clearButton.setEnabled(false);
			updateStatusBar();
		});

		for (Map.Entry<String, JSlider> entry : sliders.entrySet()) {
			JSlider slider = entry.getValue();
			slider.addChangeListener(e -> {
				if (!slider.getValueIsAdjusting()) {
					applySearchAndFilter(searchField.getText().trim(), sliders, operatorCombos, minMap, maxMap);
					enableClearButton.run();
				}
			});
		}

		for (Map.Entry<String, JComboBox<String>> entry : operatorCombos.entrySet()) {
			JComboBox<String> combo = entry.getValue();
			combo.addActionListener(e -> {
				applySearchAndFilter(searchField.getText().trim(), sliders, operatorCombos, minMap, maxMap);
				enableClearButton.run();
			});
		}

		// Fix sizing
		int searchH = outerPanel.getPreferredSize().height + 20;
		outerPanel.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, searchH));
		outerPanel.setMinimumSize(new Dimension(SIDEBAR_WIDTH - 20, searchH));
		outerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, searchH));

		return outerPanel;
	}

	// ---------------------------------------------------------------
	// Summary Statistics panel builder
	// ---------------------------------------------------------------

	/**
	 * Builds the summary statistics panel showing pattern count and measure min/max
	 * values. Enforces a minimum height so it doesn't look smaller than the other
	 * sidebar panels when there are no numeric measures.
	 */
	private JPanel buildSummaryPanel(Map<String, Double> minMap, Map<String, Double> maxMap) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Statistics"));

		// Pattern count
		int total = patternsPanel.getTotalPatternCount();
		JLabel totalLabel = new JLabel(String.format(" %d patterns total", total));
		totalLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
		panel.add(totalLabel);
		panel.add(Box.createVerticalStrut(6));

		// Measures count
		Set<String> measures = patternsPanel.getAllMeasures();
		if (!measures.isEmpty()) {
			JLabel measuresLabel = new JLabel(String.format(" %d measures", measures.size()));
			measuresLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
			measuresLabel.setForeground(new Color(100, 100, 100));
			panel.add(measuresLabel);
			panel.add(Box.createVerticalStrut(6));
		}

		// Min/max for each measure
		if (!minMap.isEmpty()) {
			for (Map.Entry<String, Double> entry : minMap.entrySet()) {
				String measure = entry.getKey();
				double min = entry.getValue();
				double max = maxMap.getOrDefault(measure, min);

				JPanel measureRow = new JPanel(new BorderLayout(4, 0));
				measureRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

				JLabel nameLabel = new JLabel("  " + measure + ":");
				nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
				nameLabel.setForeground(new Color(70, 70, 70));
				measureRow.add(nameLabel, BorderLayout.WEST);

				JLabel rangeLabel = new JLabel(String.format("%.4f \u2013 %.4f  ", min, max));
				rangeLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
				rangeLabel.setForeground(new Color(46, 134, 193));
				rangeLabel.setHorizontalAlignment(JLabel.RIGHT);
				measureRow.add(rangeLabel, BorderLayout.EAST);

				panel.add(measureRow);
				panel.add(Box.createVerticalStrut(2));
			}
		} else {
			JLabel noMeasures = new JLabel("  No numeric measures found");
			noMeasures.setFont(new Font("SansSerif", Font.ITALIC, 10));
			noMeasures.setForeground(new Color(150, 150, 150));
			panel.add(noMeasures);
			panel.add(Box.createVerticalStrut(6));
		}

		// Bottom padding
		panel.add(Box.createVerticalStrut(4));

		// Enforce minimum height so the panel doesn't look tiny
		int contentH = panel.getPreferredSize().height;
		int minH = 80;
		int finalH = Math.max(contentH, minH);
		panel.setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, finalH));
		panel.setMinimumSize(new Dimension(SIDEBAR_WIDTH - 20, finalH));
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, finalH));

		return panel;
	}

	// ---------------------------------------------------------------
	// Size Distribution Histogram
	// ---------------------------------------------------------------

	/**
	 * A compact panel that draws a bar chart showing the distribution of pattern
	 * sizes. Each bar represents one distinct size value; the height is proportional
	 * to the count. Hovering over a bar shows a tooltip with the exact count and
	 * percentage.
	 */
	private static class SizeDistributionHistogram extends JPanel {

		private static final long serialVersionUID = 1L;

		private static final int HIST_HEIGHT = 100;
		private static final int BAR_GAP = 2;
		private static final int LABEL_AREA = 16;
		private static final int TOP_PAD = 4;
		private static final int SIDE_PAD = 8;

		private static final Color BAR_COLOR = new Color(46, 134, 193);
		private static final Color BAR_HOVER_COLOR = new Color(52, 152, 219);
		private static final Color AXIS_COLOR = new Color(160, 160, 160);
		private static final Color LABEL_COLOR = new Color(100, 100, 100);
		private static final Color EMPTY_COLOR = new Color(150, 150, 150);
		private static final Color COUNT_COLOR = new Color(60, 60, 60);

		private static final Font FONT_LABEL = new Font("SansSerif", Font.PLAIN, 9);
		private static final Font FONT_COUNT = new Font("SansSerif", Font.BOLD, 9);
		private static final Font FONT_EMPTY = new Font("SansSerif", Font.ITALIC, 10);
		private static final Font FONT_SUBTITLE = new Font("SansSerif", Font.PLAIN, 10);

		/** Sorted map: size -> count */
		private TreeMap<Integer, Integer> distribution = new TreeMap<>();
		private int maxCount = 0;
		private int totalPatterns = 0;
		private int hoveredBarIndex = -1;
		private double avgSize = 0;
		private int minSize = 0;
		private int maxSize = 0;

		SizeDistributionHistogram() {
			setBorder(BorderFactory.createTitledBorder("Size Distribution"));
			int totalH = HIST_HEIGHT + LABEL_AREA + TOP_PAD + 55;
			setPreferredSize(new Dimension(SIDEBAR_WIDTH - 20, totalH));
			setMinimumSize(new Dimension(SIDEBAR_WIDTH - 20, totalH));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, totalH));
			ToolTipManager.sharedInstance().registerComponent(this);

			addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
				@Override
				public void mouseMoved(java.awt.event.MouseEvent e) {
					int newHover = getBarIndexAt(e.getX(), e.getY());
					if (newHover != hoveredBarIndex) {
						hoveredBarIndex = newHover;
						repaint();
					}
				}
			});

			addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mouseExited(java.awt.event.MouseEvent e) {
					if (hoveredBarIndex != -1) {
						hoveredBarIndex = -1;
						repaint();
					}
				}
			});
		}

		/**
		 * Updates the histogram with data from the given patterns panel.
		 */
		void updateData(PatternsPanel panel) {
			distribution.clear();
			maxCount = 0;
			totalPatterns = 0;
			hoveredBarIndex = -1;
			avgSize = 0;
			minSize = 0;
			maxSize = 0;

			if (panel == null) {
				repaint();
				return;
			}

			List<Integer> sizes = panel.getVisiblePatternSizes();
			if (sizes == null || sizes.isEmpty()) {
				repaint();
				return;
			}

			totalPatterns = sizes.size();
			long sumSize = 0;
			minSize = Integer.MAX_VALUE;
			maxSize = Integer.MIN_VALUE;

			for (int sz : sizes) {
				distribution.merge(sz, 1, Integer::sum);
				sumSize += sz;
				if (sz < minSize) minSize = sz;
				if (sz > maxSize) maxSize = sz;
			}

			avgSize = (totalPatterns > 0) ? (double) sumSize / totalPatterns : 0;

			for (int count : distribution.values()) {
				if (count > maxCount) maxCount = count;
			}

			repaint();
		}

		private int getBarIndexAt(int mx, int my) {
			if (distribution.isEmpty()) return -1;

			Insets ins = getInsets();
			int drawLeft = ins.left + SIDE_PAD;
			int drawRight = getWidth() - ins.right - SIDE_PAD;
			int drawW = drawRight - drawLeft;
			int subtitleH = 18;
			int barAreaTop = ins.top + TOP_PAD + subtitleH;
			int barAreaBot = getHeight() - ins.bottom - LABEL_AREA;

			if (drawW <= 0 || my < barAreaTop || my > barAreaBot) return -1;

			int n = distribution.size();
			int totalGap = (n - 1) * BAR_GAP;
			int barW = Math.max(4, (drawW - totalGap) / n);
			barW = Math.min(barW, 28);
			int usedW = n * barW + totalGap;
			int startX = drawLeft + (drawW - usedW) / 2;

			int idx = 0;
			for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
				int bx = startX + idx * (barW + BAR_GAP);
				if (mx >= bx && mx < bx + barW) {
					return idx;
				}
				idx++;
			}
			return -1;
		}

		@Override
		public String getToolTipText(java.awt.event.MouseEvent e) {
			int idx = getBarIndexAt(e.getX(), e.getY());
			if (idx < 0 || idx >= distribution.size()) return null;

			int i = 0;
			for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
				if (i == idx) {
					double pct = (totalPatterns > 0)
							? (100.0 * entry.getValue() / totalPatterns) : 0;
					return String.format(
							"<html><b>Size %d:</b> %d pattern%s (%.1f%%)</html>",
							entry.getKey(), entry.getValue(),
							entry.getValue() != 1 ? "s" : "", pct);
				}
				i++;
			}
			return null;
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			Insets ins = getInsets();
			int drawLeft = ins.left + SIDE_PAD;
			int drawRight = getWidth() - ins.right - SIDE_PAD;
			int drawW = drawRight - drawLeft;

			// Subtitle line with summary stats
			int subtitleH = 18;
			if (totalPatterns > 0) {
				g2.setFont(FONT_SUBTITLE);
				g2.setColor(LABEL_COLOR);
				String subtitle = String.format("min=%d  avg=%.1f  max=%d",
						minSize, avgSize, maxSize);
				FontMetrics fmS = g2.getFontMetrics();
				g2.drawString(subtitle,
						drawLeft + (drawW - fmS.stringWidth(subtitle)) / 2,
						ins.top + TOP_PAD + fmS.getAscent());
			}

			int barAreaTop = ins.top + TOP_PAD + subtitleH;
			int barAreaBot = getHeight() - ins.bottom - LABEL_AREA;
			int barAreaH = barAreaBot - barAreaTop;

			if (distribution.isEmpty()) {
				g2.setFont(FONT_EMPTY);
				g2.setColor(EMPTY_COLOR);
				String msg = "No data";
				FontMetrics fm = g2.getFontMetrics();
				g2.drawString(msg,
						drawLeft + (drawW - fm.stringWidth(msg)) / 2,
						barAreaTop + barAreaH / 2 + fm.getAscent() / 2);
				return;
			}

			int n = distribution.size();
			int totalGap = (n - 1) * BAR_GAP;
			int barW = Math.max(4, (drawW - totalGap) / n);
			barW = Math.min(barW, 28);
			int usedW = n * barW + totalGap;
			int startX = drawLeft + (drawW - usedW) / 2;

			// Axis line
			g2.setColor(AXIS_COLOR);
			g2.drawLine(drawLeft, barAreaBot, drawRight, barAreaBot);

			// Draw bars
			int idx = 0;
			for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
				int size = entry.getKey();
				int count = entry.getValue();

				int barH = (maxCount > 0)
						? Math.max(2, (int) ((double) count / maxCount * barAreaH))
						: 2;
				int bx = startX + idx * (barW + BAR_GAP);
				int by = barAreaBot - barH;

				// Bar fill
				g2.setColor(idx == hoveredBarIndex ? BAR_HOVER_COLOR : BAR_COLOR);
				g2.fillRect(bx, by, barW, barH);

				// Count label above bar
				if (barH > 12 || barAreaH > 40) {
					g2.setFont(FONT_COUNT);
					g2.setColor(COUNT_COLOR);
					FontMetrics fmC = g2.getFontMetrics();
					String countStr = String.valueOf(count);
					int cw = fmC.stringWidth(countStr);
					int cx = bx + (barW - cw) / 2;
					int cy = by - 2;
					if (cy >= barAreaTop + fmC.getAscent()) {
						g2.drawString(countStr, cx, cy);
					}
				}

				// Size label below axis
				g2.setFont(FONT_LABEL);
				g2.setColor(LABEL_COLOR);
				FontMetrics fmL = g2.getFontMetrics();
				String sizeStr = String.valueOf(size);
				int lw = fmL.stringWidth(sizeStr);
				int lx = bx + (barW - lw) / 2;
				g2.drawString(sizeStr, lx, barAreaBot + fmL.getAscent() + 1);

				idx++;
			}
		}
	}

	// ---------------------------------------------------------------
	// Filter / status helpers
	// ---------------------------------------------------------------

	/**
	 * Applies search text and numeric filters to the patternsPanel.
	 */
	private void applySearchAndFilter(String text, Map<String, JSlider> sliders,
			Map<String, JComboBox<String>> operatorCombos, Map<String, Double> minMap, Map<String, Double> maxMap) {
		Map<String, Double> thresholds = new LinkedHashMap<>();
		Map<String, String> operators = new LinkedHashMap<>();
		for (Map.Entry<String, JSlider> entry : sliders.entrySet()) {
			String measure = entry.getKey();
			JSlider slider = entry.getValue();
			double min = minMap.get(measure);
			double max = maxMap.get(measure);
			double val = slider.getValue() / 1000.0 * (max - min) + min;
			thresholds.put(measure, val);
			operators.put(measure, (String) operatorCombos.get(measure).getSelectedItem());
		}
		patternsPanel.applySearchAndFilters(text, thresholds, operators);
		updateStatusBar();
	}

	/**
	 * Update the status bar and histogram after search/filter changes.
	 */
	private void updateStatusBar() {
		int totalPatterns = patternsPanel.getTotalPatternCount();
		int visiblePatterns = patternsPanel.getNumberOfVisiblePatterns();

		updateCountLabel();

		// Update status label
		if (totalPatterns != visiblePatterns) {
			statusLabel.setText(String.format("Showing %d of %d patterns (filtered)",
					visiblePatterns, totalPatterns));
		} else {
			statusLabel.setText(buildStatusText());
		}

		// Update histogram to reflect filtered data
		if (histogramPanel != null) {
			histogramPanel.updateData(patternsPanel);
		}
	}

	/**
	 * Updates the count label in the status bar.
	 */
	private void updateCountLabel() {
		int totalPatterns = patternsPanel.getTotalPatternCount();
		int visiblePatterns = patternsPanel.getNumberOfVisiblePatterns();
		if (totalPatterns != visiblePatterns) {
			countLabel.setText(visiblePatterns + " / " + totalPatterns + " patterns ");
		} else {
			countLabel.setText(totalPatterns + " patterns ");
		}
	}

	/**
	 * Builds the default status bar text.
	 */
	private String buildStatusText() {
		int total = patternsPanel.getTotalPatternCount();
		Set<String> measures = patternsPanel.getAllMeasures();
		StringBuilder sb = new StringBuilder();
		sb.append(" ").append(total).append(" patterns");
		if (!measures.isEmpty()) {
			sb.append(" \u00B7 ").append(measures.size()).append(" measures");
		}
		return sb.toString();
	}

	// ---------------------------------------------------------------
	// Menu bar
	// ---------------------------------------------------------------

	/**
	 * Builds the menu bar.
	 */
	private static JMenuBar createMenuBar(JFrame parentFrame, PatternsPanel rulesPanel, String originalFilePath) {
		JMenuBar menuBar = new JMenuBar();

		// === View Menu ===
		JMenu menuView = new JMenu("View");

		// --- Submenu: Sort ---
		JMenu menuSort = new JMenu("Sort by");
		ButtonGroup sortGroup = new ButtonGroup();
		for (String sortOption : rulesPanel.getListOfSortingOrders()) {
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(sortOption);
			sortGroup.add(item);
			item.addActionListener(e -> {
				try {
					rulesPanel.sortPatterns(sortOption);
					rulesPanel.revalidate();
					rulesPanel.repaint();
				} catch (Exception ex) {
					showErrorDialog(parentFrame, "Sort failed: " + ex.getMessage());
				}
			});
			menuSort.add(item);
		}

		// Toggle statistics panel
		JRadioButtonMenuItem itemShowStats = new JRadioButtonMenuItem("Statistics");
		itemShowStats.setSelected(true);
		itemShowStats.addActionListener(e -> {
			if (parentFrame instanceof VisualPatternViewer viewer) {
				viewer.summaryPanel.setVisible(itemShowStats.isSelected());
				viewer.sidebarPanel.revalidate();
				viewer.sidebarPanel.repaint();
			}
		});
		menuView.add(itemShowStats);

		// Toggle search panel
		JRadioButtonMenuItem itemShowSearchPanel = new JRadioButtonMenuItem("Search & Filter Panel");
		itemShowSearchPanel.setSelected(true);
		itemShowSearchPanel.addActionListener(e -> {
			if (parentFrame instanceof VisualPatternViewer viewer) {
				viewer.searchPanel.setVisible(itemShowSearchPanel.isSelected());
				viewer.sidebarPanel.revalidate();
				viewer.sidebarPanel.repaint();
			}
		});
		menuView.add(itemShowSearchPanel);

		// Toggle histogram
		JRadioButtonMenuItem itemShowHistogram = new JRadioButtonMenuItem("Size Histogram");
		itemShowHistogram.setSelected(true);
		itemShowHistogram.addActionListener(e -> {
			if (parentFrame instanceof VisualPatternViewer viewer) {
				viewer.histogramPanel.setVisible(itemShowHistogram.isSelected());
				viewer.sidebarPanel.revalidate();
				viewer.sidebarPanel.repaint();
			}
		});
		menuView.add(itemShowHistogram);

		// Toggle entire sidebar
		JRadioButtonMenuItem itemShowSidebar = new JRadioButtonMenuItem("Sidebar");
		itemShowSidebar.setSelected(true);
		itemShowSidebar.addActionListener(e -> {
			if (parentFrame instanceof VisualPatternViewer viewer) {
				java.awt.Component sidebarScroll = viewer.sidebarPanel.getParent();
				if (sidebarScroll != null) {
					java.awt.Component scrollPane = sidebarScroll.getParent();
					if (scrollPane instanceof JScrollPane) {
						scrollPane.setVisible(itemShowSidebar.isSelected());
					} else {
						sidebarScroll.setVisible(itemShowSidebar.isSelected());
					}
					viewer.getContentPane().revalidate();
					viewer.getContentPane().repaint();
				}
			}
		});
		menuView.add(itemShowSidebar);

		menuView.addSeparator();

		// --- Submenu: Layout modes ---
		JMenu menuLayout = new JMenu("Layout");
		ButtonGroup group = new ButtonGroup();
		LayoutMode currentMode = LayoutMode.GRID;
		if (parentFrame instanceof VisualPatternViewer viewerFrame) {
			currentMode = viewerFrame.mode;
		}
		for (LayoutMode mode : LayoutMode.values()) {
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(mode.toString());
			group.add(item);
			if (mode == currentMode) {
				item.setSelected(true);
			}
			item.addActionListener(e -> {
				if (parentFrame instanceof VisualPatternViewer viewer) {
					viewer.mode = mode;
				}
				if (rulesPanel instanceof PatternsPanel patternsPanel) {
					patternsPanel.setLayoutModeWithCallback(mode, () -> configureFrameSize(parentFrame, patternsPanel));
				} else {
					rulesPanel.setLayoutMode(mode);
				}
			});
			menuLayout.add(item);
		}
		menuView.add(menuLayout);

		// === Tools Menu ===
		JMenu menuTools = new JMenu("Tools");

		JMenuItem itemOpenOriginal = new JMenuItem("Open file with system text editor");
		itemOpenOriginal.addActionListener(e -> {
			try {
				if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
					File fileToOpen = new File(originalFilePath);
					if (fileToOpen.exists() && fileToOpen.canRead()) {
						Desktop.getDesktop().open(fileToOpen);
					} else {
						JOptionPane.showMessageDialog(parentFrame, "Cannot open file:\n" + originalFilePath,
								"File Not Found / Unreadable", JOptionPane.ERROR_MESSAGE);
					}
				} else {
					JOptionPane.showMessageDialog(parentFrame,
							"Desktop API is not supported on this platform.\nCannot open the file.",
							"Operation Not Supported", JOptionPane.ERROR_MESSAGE);
				}
			} catch (IOException ex) {
				JOptionPane.showMessageDialog(parentFrame, "Failed to open file:\n" + ex.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		});
		menuTools.add(itemOpenOriginal);

		JMenuItem itemExport = new JMenuItem("Export as PNG");
		itemExport.addActionListener(e -> {
			try {
				JFileChooser fileChooser = new JFileChooser();
				fileChooser.setSelectedFile(new File(DEFAULT_EXPORT_FILENAME));
				int result = fileChooser.showSaveDialog(parentFrame);
				if (result == JFileChooser.APPROVE_OPTION) {
					File file = fileChooser.getSelectedFile();
					rulesPanel.exportAsPNG(file.getAbsolutePath());
				}
			} catch (Exception ex) {
				showErrorDialog(parentFrame, "Export failed: " + ex.getMessage());
			}
		});
		menuTools.add(itemExport);

		// === Histogram Menu Item ===
		menuTools.addSeparator();
		JMenuItem itemHistogram = new JMenuItem("View Measure Distribution Histogram");
		itemHistogram.addActionListener(e -> {
			try {
				Map<String, List<Double>> measureValuesMap = MeasureValuesExtractor.extractMeasureValues(rulesPanel);

				boolean hasValues = false;
				for (List<Double> values : measureValuesMap.values()) {
					if (!values.isEmpty()) {
						hasValues = true;
						break;
					}
				}

				if (!hasValues) {
					JOptionPane.showMessageDialog(parentFrame,
							"No numeric measure values found in the patterns.",
							"No Data", JOptionPane.INFORMATION_MESSAGE);
					return;
				}

				MeasureHistogramDialog histogramDialog = new MeasureHistogramDialog(
						(JFrame) parentFrame, rulesPanel, measureValuesMap);
				histogramDialog.setVisible(true);
			} catch (Exception ex) {
				showErrorDialog(parentFrame, "Failed to create histogram: " + ex.getMessage());
				ex.printStackTrace();
			}
		});
		menuTools.add(itemHistogram);

		menuBar.add(menuView);
		menuBar.add(menuSort);
		menuBar.add(menuTools);

		return menuBar;
	}

	/**
	 * Configure the frame size to fit within 90% of the screen and set minimum
	 * size. Also considers the sidebar's preferred height so the window is tall
	 * enough to show both the sidebar and the patterns panel.
	 */
	private static void configureFrameSize(JFrame frame, PatternsPanel patternsPanel) {
		frame.pack();
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int maxWidth = (int) (screenSize.width * 0.9);
		int maxHeight = (int) (screenSize.height * 0.9);
		Dimension preferred = frame.getSize();

		if (frame instanceof VisualPatternViewer viewer && viewer.sidebarPanel != null) {
			int sidebarH = viewer.sidebarPanel.getPreferredSize().height;
			Insets insets = frame.getInsets();
			int menuBarH = (frame.getJMenuBar() != null) ? frame.getJMenuBar().getPreferredSize().height : 0;
			int statusBarH = (viewer.statusBar != null) ? viewer.statusBar.getPreferredSize().height : 0;
			int minFrameH = sidebarH + insets.top + insets.bottom + menuBarH + statusBarH;
			preferred = new Dimension(preferred.width, Math.max(preferred.height, minFrameH));
		}

		if (preferred.width > maxWidth || preferred.height > maxHeight) {
			frame.setSize(Math.min(preferred.width, maxWidth), Math.min(preferred.height, maxHeight));
		} else {
			frame.setSize(preferred);
		}
		frame.setMinimumSize(new Dimension(700, 450));
		frame.setLocationRelativeTo(null);
	}

	/**
	 * Show an error dialog with the given message.
	 */
	private static void showErrorDialog(JFrame parentFrame, String message) {
		JOptionPane.showMessageDialog(parentFrame, message, "Error", JOptionPane.ERROR_MESSAGE);
	}
}