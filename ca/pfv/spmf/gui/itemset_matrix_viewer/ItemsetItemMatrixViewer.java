package ca.pfv.spmf.gui.itemset_matrix_viewer;

/*
 * Copyright (c) 2024 Philippe Fournier-Viger
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see http://www.gnu.org/licenses/.
 */
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
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
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * A standalone viewer window for the item-pattern matrix.
 *
 * <p>
 * <b>Standalone mode:</b> run {@link #main(String[])} or call
 * {@code new ItemPatternMatrixViewer(true)} to get a full window with
 * file-loading, filtering, sorting, and highlighting controls.
 *
 * <p>
 * <b>Embedded mode:</b> call {@link #ItemPatternMatrixViewer(String, boolean)}
 * with a file path to open a viewer pre-loaded with that file. The window uses
 * {@code DISPOSE_ON_CLOSE} so it does not kill the calling application.
 *
 * <p>
 * <b>Search syntax:</b> The search field accepts item names separated by
 * commas and/or spaces. A pattern matches only if it contains <b>all</b>
 * specified items (AND search). For example, {@code 1, 2, 3} or {@code 1 2 3}.
 *
 * @author Philippe Fournier-Viger, 2026
 */
public class ItemsetItemMatrixViewer extends JFrame {

	private static final long serialVersionUID = 1L;

	private static final String WINDOW_TITLE = "SPMF - Itemset-Item Matrix Viewer";

	private static final String INSTRUCTIONS =
			"Click row to select/deselect | \u2191\u2193 / PgUp/PgDn navigate | "
					+ "Drag to pan | Ctrl+Scroll zoom | Shift+Scroll horizontal | "
					+ "Ctrl+F search | Esc deselect";

	// ---------------------------------------------------------------
	// Data
	// ---------------------------------------------------------------

	private ItemsetReader reader;
	private List<Itemset> allItemsets = new ArrayList<>();
	private List<Itemset> filteredItemsets = new ArrayList<>();
	private Itemset selectedItemset;
	private File loadedFile;

	// ---------------------------------------------------------------
	// UI components
	// ---------------------------------------------------------------

	private JLabel fileInfoLabel;
	private JLabel statusLabel;
	private JLabel zoomLabel;
	private ItemsetItemMatrixPanel matrixPanel;
	private JTextField filterField;
	private JLabel filterResultLabel;
	private JComboBox<String> sortComboBox;
	private JCheckBox highlightSubsetsCheckbox;
	private JCheckBox highlightSupersetsCheckbox;
	private JSpinner minSizeSpinner;
	private JSpinner maxSizeSpinner;
	private JPanel legendPanel;
	private SizeHistogramPanel histogramPanel;
	private JCheckBoxMenuItem showCheckMarksItem;
	private JCheckBoxMenuItem showRowNumbersItem;
	private JCheckBoxMenuItem showMeasureColumnsItem;
	private JLabel fontSizeLabel;

	// ---------------------------------------------------------------
	// Constructors
	// ---------------------------------------------------------------

	/**
	 * Creates the viewer.
	 *
	 * @param standalone if true, {@code EXIT_ON_CLOSE}; otherwise {@code DISPOSE_ON_CLOSE}
	 */
	public ItemsetItemMatrixViewer(boolean standalone) {
		setTitle(WINDOW_TITLE);
		setDefaultCloseOperation(standalone ? JFrame.EXIT_ON_CLOSE : JFrame.DISPOSE_ON_CLOSE);
		setSize(1200, 750);
		setLocationRelativeTo(null);

		initComponents();
		buildMenuBar();
		layoutComponents();
		setupListeners();
	}

	/**
	 * Creates the viewer and immediately loads the given file.
	 *
	 * @param filePath   path to the itemset file to load
	 * @param standalone if true, {@code EXIT_ON_CLOSE}; otherwise {@code DISPOSE_ON_CLOSE}
	 */
	public ItemsetItemMatrixViewer(String filePath, boolean standalone) {
		this(standalone);
		if (filePath != null && !filePath.isEmpty()) {
			try {
				loadItemsetsFromFile(new File(filePath));
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(this,
						"Error loading file: " + ex.getMessage(),
						"Load Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	// ---------------------------------------------------------------
	// Initialisation
	// ---------------------------------------------------------------

	private void initComponents() {
		fileInfoLabel = new JLabel("No file loaded");
		fileInfoLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

		statusLabel = new JLabel(INSTRUCTIONS);
		statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
		statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

		zoomLabel = new JLabel("Zoom: 100%");
		zoomLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
		zoomLabel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
		zoomLabel.setForeground(new Color(100, 100, 100));

		matrixPanel = new ItemsetItemMatrixPanel();

		fontSizeLabel = new JLabel();
		updateFontSizeLabel();

		filterField = new JTextField(18);
		filterField.setToolTipText(
				"<html>Search for patterns containing specific items.<br>"
						+ "Separate multiple items with commas or spaces.<br>"
						+ "Example: <b>1, 2, 3</b> or <b>bread milk</b><br>"
						+ "Shows only patterns containing <b>all</b> listed items.</html>");

		filterResultLabel = new JLabel(" ");
		filterResultLabel.setFont(new Font("SansSerif", Font.ITALIC, 10));
		filterResultLabel.setForeground(new Color(100, 100, 100));

		highlightSubsetsCheckbox = new JCheckBox("Highlight Subsets", false);
		highlightSupersetsCheckbox = new JCheckBox("Highlight Supersets", false);

		minSizeSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 9999, 1));
		maxSizeSpinner = new JSpinner(new SpinnerNumberModel(9999, 0, 9999, 1));
		minSizeSpinner.setToolTipText("Minimum pattern size (0 = no minimum)");
		maxSizeSpinner.setToolTipText("Maximum pattern size (9999 = no maximum)");
		((JSpinner.DefaultEditor) minSizeSpinner.getEditor()).getTextField().setColumns(3);
		((JSpinner.DefaultEditor) maxSizeSpinner.getEditor()).getTextField().setColumns(3);

		sortComboBox = new JComboBox<>(new String[]{
				"Size (asc)", "Size (desc)",
				"Lexicographical (asc)", "Lexicographical (desc)"});

		legendPanel = buildLegendPanel();
		histogramPanel = new SizeHistogramPanel();
	}

	private void buildMenuBar() {
		JMenuBar menuBar = new JMenuBar();

		// ---- File ----
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);

		JMenuItem openItem = new JMenuItem("Open...");
		openItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O,
				java.awt.event.InputEvent.CTRL_DOWN_MASK));
		openItem.addActionListener(e -> chooseAndLoadFile());

		JMenuItem exportPng = new JMenuItem("Export as PNG...");
		exportPng.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P,
				java.awt.event.InputEvent.CTRL_DOWN_MASK));
		exportPng.addActionListener(e -> exportAsPNG());

		JMenuItem exportCsv = new JMenuItem("Export as CSV...");
		exportCsv.addActionListener(e -> exportAsCSV());

		JMenuItem exitItem = new JMenuItem("Exit");
		exitItem.addActionListener(e -> dispose());

		fileMenu.add(openItem);
		fileMenu.addSeparator();
		fileMenu.add(exportPng);
		fileMenu.add(exportCsv);
		fileMenu.addSeparator();
		fileMenu.add(exitItem);

		// ---- Edit ----
		JMenu editMenu = new JMenu("Edit");
		editMenu.setMnemonic(KeyEvent.VK_E);

		JMenuItem findItem = new JMenuItem("Find...");
		findItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F,
				java.awt.event.InputEvent.CTRL_DOWN_MASK));
		findItem.addActionListener(e -> {
			filterField.requestFocusInWindow();
			filterField.selectAll();
		});

		JMenuItem clearFilter = new JMenuItem("Clear Filter");
		clearFilter.addActionListener(e -> {
			filterField.setText("");
			minSizeSpinner.setValue(0);
			maxSizeSpinner.setValue(9999);
			applyFilter();
		});

		editMenu.add(findItem);
		editMenu.add(clearFilter);

		// ---- View ----
		JMenu viewMenu = new JMenu("View");
		viewMenu.setMnemonic(KeyEvent.VK_V);

		JMenu fontSizeMenu = new JMenu("Font Size");
		JMenuItem fontInc = new JMenuItem("Increase");
		fontInc.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS,
				java.awt.event.InputEvent.CTRL_DOWN_MASK));
		fontInc.addActionListener(e -> changeFontSize(1));
		JMenuItem fontDec = new JMenuItem("Decrease");
		fontDec.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS,
				java.awt.event.InputEvent.CTRL_DOWN_MASK));
		fontDec.addActionListener(e -> changeFontSize(-1));
		JMenuItem fontReset = new JMenuItem("Reset to Default");
		fontReset.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0,
				java.awt.event.InputEvent.CTRL_DOWN_MASK));
		fontReset.addActionListener(e -> {
			matrixPanel.setFontSize(11);
			updateFontSizeLabel();
		});
		fontSizeMenu.add(fontInc);
		fontSizeMenu.add(fontDec);
		fontSizeMenu.addSeparator();
		fontSizeMenu.add(fontReset);

		JMenu cellSizeMenu = new JMenu("Cell Size");
		int[] sizes = {16, 20, 25, 30, 40, 50};
		String[] sizeLabels = {"Tiny (16)", "Small (20)", "Medium (25)",
				"Default (30)", "Large (40)", "Extra Large (50)"};
		ButtonGroup cellGroup = new ButtonGroup();
		for (int i = 0; i < sizes.length; i++) {
			final int sz = sizes[i];
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(sizeLabels[i]);
			if (sz == 30) item.setSelected(true);
			cellGroup.add(item);
			item.addActionListener(e -> matrixPanel.setCellSize(sz));
			cellSizeMenu.add(item);
		}

		showCheckMarksItem = new JCheckBoxMenuItem("Show Check Marks", true);
		showCheckMarksItem.addActionListener(
				e -> matrixPanel.setShowCheckMarks(showCheckMarksItem.isSelected()));

		showRowNumbersItem = new JCheckBoxMenuItem("Show Row Numbers", true);
		showRowNumbersItem.addActionListener(
				e -> matrixPanel.setShowRowNumbers(showRowNumbersItem.isSelected()));

		showMeasureColumnsItem = new JCheckBoxMenuItem("Show Measure Columns", true);
		showMeasureColumnsItem.addActionListener(
				e -> matrixPanel.setShowMeasureColumns(showMeasureColumnsItem.isSelected()));

		viewMenu.add(fontSizeMenu);
		viewMenu.add(cellSizeMenu);
		viewMenu.addSeparator();
		viewMenu.add(showCheckMarksItem);
		viewMenu.add(showRowNumbersItem);
		viewMenu.add(showMeasureColumnsItem);

		menuBar.add(fileMenu);
		menuBar.add(editMenu);
		menuBar.add(viewMenu);
		setJMenuBar(menuBar);
	}

	private JPanel buildLegendPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Legend"));

		panel.add(legendEntry(ItemsetItemMatrixPanel.COLOR_CELL_PRESENT, "Item present"));
		panel.add(Box.createVerticalStrut(2));
		panel.add(legendEntry(ItemsetItemMatrixPanel.COLOR_CELL_ABSENT, "Item absent"));
		panel.add(Box.createVerticalStrut(2));
		panel.add(legendEntry(ItemsetItemMatrixPanel.COLOR_CELL_SELECTED, "Selected pattern"));
		panel.add(Box.createVerticalStrut(2));
		panel.add(legendEntry(ItemsetItemMatrixPanel.COLOR_CELL_HOVER, "Hovered pattern"));
		panel.add(Box.createVerticalStrut(2));
		panel.add(legendEntry(ItemsetItemMatrixPanel.COLOR_CELL_SUBSET, "Subset of selected"));
		panel.add(Box.createVerticalStrut(2));
		panel.add(legendEntry(ItemsetItemMatrixPanel.COLOR_CELL_SUPERSET, "Superset of selected"));
		return panel;
	}

	private JPanel legendEntry(Color color, String label) {
		JPanel entry = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		entry.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		JPanel swatch = new JPanel();
		swatch.setBackground(color);
		swatch.setPreferredSize(new Dimension(14, 14));
		swatch.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1));
		JLabel text = new JLabel(label);
		text.setFont(new Font("SansSerif", Font.PLAIN, 11));
		entry.add(swatch);
		entry.add(text);
		return entry;
	}

	// ---------------------------------------------------------------
	// Size distribution histogram panel
	// ---------------------------------------------------------------

	/**
	 * A small panel that draws a bar chart showing the distribution of
	 * pattern sizes (number of items per pattern).  Each bar represents
	 * one distinct size value; the height is proportional to the count.
	 * Hovering over a bar shows a tooltip with the exact count.
	 */
	private static class SizeHistogramPanel extends JPanel {

		private static final long serialVersionUID = 1L;

		private static final int HIST_HEIGHT = 90;
		private static final int BAR_GAP = 2;
		private static final int LABEL_AREA = 16;
		private static final int TOP_PAD = 4;
		private static final int SIDE_PAD = 6;

		private static final Color BAR_COLOR = new Color(46, 134, 193);
		private static final Color BAR_HOVER_COLOR = new Color(52, 152, 219);
		private static final Color AXIS_COLOR = new Color(160, 160, 160);
		private static final Color LABEL_COLOR = new Color(100, 100, 100);
		private static final Color EMPTY_COLOR = new Color(150, 150, 150);

		private static final Font FONT_LABEL = new Font("SansSerif", Font.PLAIN, 9);
		private static final Font FONT_COUNT = new Font("SansSerif", Font.BOLD, 9);
		private static final Font FONT_EMPTY = new Font("SansSerif", Font.ITALIC, 10);

		/** Sorted map: size → count */
		private TreeMap<Integer, Integer> distribution = new TreeMap<>();
		private int maxCount = 0;
		private int totalPatterns = 0;
		private int hoveredBarIndex = -1;

		SizeHistogramPanel() {
			setBorder(BorderFactory.createTitledBorder("Size Distribution"));
			setPreferredSize(new Dimension(190, HIST_HEIGHT + LABEL_AREA + TOP_PAD + 30));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, HIST_HEIGHT + LABEL_AREA + TOP_PAD + 30));
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
		 * Updates the histogram with data from the given itemset list.
		 */
		void updateData(List<Itemset> itemsets) {
			distribution.clear();
			maxCount = 0;
			totalPatterns = 0;
			hoveredBarIndex = -1;

			if (itemsets != null && !itemsets.isEmpty()) {
				totalPatterns = itemsets.size();
				for (Itemset it : itemsets) {
					int sz = it.getItems().size();
					distribution.merge(sz, 1, Integer::sum);
				}
				for (int count : distribution.values()) {
					if (count > maxCount) maxCount = count;
				}
			}
			repaint();
		}

		private int getBarIndexAt(int mx, int my) {
			if (distribution.isEmpty()) return -1;

			java.awt.Insets ins = getInsets();
			int drawLeft = ins.left + SIDE_PAD;
			int drawRight = getWidth() - ins.right - SIDE_PAD;
			int drawW = drawRight - drawLeft;
			int barAreaTop = ins.top + TOP_PAD;
			int barAreaBot = getHeight() - ins.bottom - LABEL_AREA;

			if (drawW <= 0 || my < barAreaTop || my > barAreaBot) return -1;

			int n = distribution.size();
			int totalGap = (n - 1) * BAR_GAP;
			int barW = Math.max(4, (drawW - totalGap) / n);
			int usedW = n * barW + totalGap;
			int startX = drawLeft + (drawW - usedW) / 2;

			int idx = 0;
			for (Map.Entry<Integer, Integer> entry : distribution.entrySet()) {
				int bx = startX + idx * (barW + BAR_GAP);
				if (mx >= bx && mx < bx + barW) {
					// Check if within bar height
					int barH = (maxCount > 0)
							? (int) ((double) entry.getValue() / maxCount * (barAreaBot - barAreaTop))
							: 0;
					int barTop = barAreaBot - barH;
					if (my >= barTop) return idx;
					return idx; // Still over the column area
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
					return String.format("<html><b>Size %d:</b> %d patterns (%.1f%%)</html>",
							entry.getKey(), entry.getValue(), pct);
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

			java.awt.Insets ins = getInsets();
			int drawLeft = ins.left + SIDE_PAD;
			int drawRight = getWidth() - ins.right - SIDE_PAD;
			int drawW = drawRight - drawLeft;
			int barAreaTop = ins.top + TOP_PAD;
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
			// Cap bar width so it doesn't look absurd with few bars
			barW = Math.min(barW, 30);
			int usedW = n * barW + totalGap;
			int startX = drawLeft + (drawW - usedW) / 2;

			// Axis line
			g2.setColor(AXIS_COLOR);
			g2.drawLine(drawLeft, barAreaBot, drawRight, barAreaBot);

			// Bars
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

				// Count label above bar (only if there's room)
				if (barH > 12 || barAreaH > 40) {
					g2.setFont(FONT_COUNT);
					g2.setColor(LABEL_COLOR);
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
	// Layout
	// ---------------------------------------------------------------

	private void layoutComponents() {
		setLayout(new BorderLayout(0, 0));

		// --- Top bar ---
		JPanel topPanel = new JPanel(new BorderLayout(8, 0));
		topPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)),
				BorderFactory.createEmptyBorder(6, 10, 6, 10)));
		topPanel.add(fileInfoLabel, BorderLayout.CENTER);
		topPanel.add(fontSizeLabel, BorderLayout.EAST);

		// --- Left sidebar ---
		JPanel sidebar = new JPanel();
		sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
		sidebar.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(200, 200, 200)),
				BorderFactory.createEmptyBorder(8, 8, 8, 8)));
		sidebar.setPreferredSize(new Dimension(210, 0));

		// Filter section
		JPanel filterSection = new JPanel();
		filterSection.setLayout(new BoxLayout(filterSection, BoxLayout.Y_AXIS));
		filterSection.setBorder(BorderFactory.createTitledBorder("Filter"));

		JPanel filterRow = new JPanel(new BorderLayout(4, 0));
		filterRow.add(new JLabel("Items:"), BorderLayout.WEST);
		filterRow.add(filterField, BorderLayout.CENTER);
		filterRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		JLabel searchHint = new JLabel(
				"<html><i style='font-size:9px;color:#999'>e.g. 1, 2, 3 (AND match)</i></html>");
		searchHint.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));

		JPanel sizeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		sizeRow.add(new JLabel("Size:"));
		sizeRow.add(minSizeSpinner);
		sizeRow.add(new JLabel("\u2013"));
		sizeRow.add(maxSizeSpinner);
		sizeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		JButton clearBtn = new JButton("Clear");
		clearBtn.addActionListener(e -> {
			filterField.setText("");
			minSizeSpinner.setValue(0);
			maxSizeSpinner.setValue(9999);
			applyFilter();
		});
		JPanel clearRow = new JPanel(new BorderLayout(4, 0));
		clearRow.add(filterResultLabel, BorderLayout.CENTER);
		clearRow.add(clearBtn, BorderLayout.EAST);
		clearRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

		filterSection.add(filterRow);
		filterSection.add(Box.createVerticalStrut(2));
		filterSection.add(searchHint);
		filterSection.add(Box.createVerticalStrut(4));
		filterSection.add(sizeRow);
		filterSection.add(Box.createVerticalStrut(4));
		filterSection.add(clearRow);
		filterSection.add(Box.createVerticalStrut(6));
		filterSection.add(highlightSubsetsCheckbox);
		filterSection.add(highlightSupersetsCheckbox);

		// Sort section
		JPanel sortSection = new JPanel();
		sortSection.setLayout(new BoxLayout(sortSection, BoxLayout.Y_AXIS));
		sortSection.setBorder(BorderFactory.createTitledBorder("Sort"));
		JPanel sortRow = new JPanel(new BorderLayout(4, 0));
		sortRow.add(new JLabel("Order:"), BorderLayout.WEST);
		sortRow.add(sortComboBox, BorderLayout.CENTER);
		sortRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		sortSection.add(sortRow);

		sidebar.add(filterSection);
		sidebar.add(Box.createVerticalStrut(6));
		sidebar.add(sortSection);
		sidebar.add(Box.createVerticalStrut(6));
		sidebar.add(legendPanel);
		sidebar.add(Box.createVerticalStrut(6));
		sidebar.add(histogramPanel);
		sidebar.add(Box.createVerticalGlue());

		// --- Status bar ---
		JPanel statusBar = new JPanel(new BorderLayout());
		statusBar.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 200)),
				BorderFactory.createEmptyBorder(2, 0, 2, 0)));
		statusBar.add(statusLabel, BorderLayout.CENTER);
		statusBar.add(zoomLabel, BorderLayout.EAST);

		// --- Assemble ---
		add(topPanel, BorderLayout.NORTH);
		add(sidebar, BorderLayout.WEST);
		add(matrixPanel, BorderLayout.CENTER);
		add(statusBar, BorderLayout.SOUTH);
	}

	private void setupListeners() {
		// Live item filter
		filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
			public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
			public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
		});

		// Size spinners
		minSizeSpinner.addChangeListener(e -> applyFilter());
		maxSizeSpinner.addChangeListener(e -> applyFilter());

		// Sort
		sortComboBox.addActionListener(e -> applySortAndRefresh());

		// Highlight
		highlightSubsetsCheckbox.addActionListener(e -> updateHighlighting());
		highlightSupersetsCheckbox.addActionListener(e -> updateHighlighting());

		// Selection
		matrixPanel.setItemsetSelectionListener(this::onItemsetSelected);

		// Zoom
		matrixPanel.setZoomChangeListener(percent -> updateStatusRight());

		// Resize
		matrixPanel.addComponentListener(new java.awt.event.ComponentAdapter() {
			@Override
			public void componentResized(java.awt.event.ComponentEvent e) { updateStatusRight(); }
		});
	}

	// ---------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------

	private void changeFontSize(int delta) {
		matrixPanel.setFontSize(matrixPanel.getFontSize() + delta);
		updateFontSizeLabel();
	}

	private void updateFontSizeLabel() {
		fontSizeLabel.setText("Font: " + matrixPanel.getFontSize() + "pt");
		fontSizeLabel.setForeground(new Color(100, 100, 100));
	}

	private void updateStatusRight() {
		int zoom = matrixPanel.getZoomPercent();
		int total = matrixPanel.getRowCount();
		StringBuilder sb = new StringBuilder();
		if (total > 0) {
			int first = matrixPanel.getFirstVisibleRow();
			int last = matrixPanel.getLastVisibleRow();
			if (first >= 0 && last >= 0) {
				sb.append("Rows ").append(first + 1).append("\u2013")
						.append(Math.min(last + 1, total)).append(" of ").append(total).append(" | ");
			}
		}
		sb.append("Zoom: ").append(zoom).append("%");
		zoomLabel.setText(sb.toString());
	}

	// ---------------------------------------------------------------
	// File loading
	// ---------------------------------------------------------------

	private void chooseAndLoadFile() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Select Itemsets File");
		chooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			try {
				loadItemsetsFromFile(chooser.getSelectedFile());
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(this,
						"Error loading file: " + ex.getMessage(),
						"Load Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void loadItemsetsFromFile(File file) {
		statusLabel.setText("Loading...");
		fileInfoLabel.setText("Loading: " + file.getName() + "...");
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

		SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
			@Override
			protected Void doInBackground() throws Exception {
				reader = new ItemsetReader(file.getAbsolutePath());
				reader.readItemsets();
				return null;
			}

			@Override
			protected void done() {
				try {
					get();
					loadedFile = file;
					allItemsets = reader.getItemsets();
					filteredItemsets = new ArrayList<>(allItemsets);
					selectedItemset = null;

					filterField.setText("");
					minSizeSpinner.setValue(0);
					maxSizeSpinner.setValue(9999);

					updateSortComboBox();
					applySortAndRefresh();

					// Update histogram with all data
					histogramPanel.updateData(allItemsets);

					fileInfoLabel.setText(String.format("%s  \u2014  %d patterns loaded",
							file.getName(), allItemsets.size()));
					setTitle(WINDOW_TITLE + " - " + file.getName());
					statusLabel.setText(INSTRUCTIONS);
					updateStatusRight();
				} catch (Exception ex) {
					fileInfoLabel.setText("Error loading file");
					statusLabel.setText("Error: " + ex.getMessage());
					JOptionPane.showMessageDialog(ItemsetItemMatrixViewer.this,
							"Error: " + ex.getMessage(), "Load Error", JOptionPane.ERROR_MESSAGE);
				} finally {
					setCursor(Cursor.getDefaultCursor());
				}
			}
		};
		worker.execute();
	}

	private void updateSortComboBox() {
		java.awt.event.ActionListener[] listeners = sortComboBox.getActionListeners();
		for (java.awt.event.ActionListener l : listeners) sortComboBox.removeActionListener(l);

		DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
		if (reader != null) {
			for (String order : reader.getSortingOrders()) model.addElement(order);
		} else {
			model.addElement("Size (asc)");
			model.addElement("Size (desc)");
			model.addElement("Lexicographical (asc)");
			model.addElement("Lexicographical (desc)");
		}
		sortComboBox.setModel(model);

		for (int i = 0; i < model.getSize(); i++) {
			if ("Size (asc)".equals(model.getElementAt(i))) {
				sortComboBox.setSelectedIndex(i);
				break;
			}
		}
		for (java.awt.event.ActionListener l : listeners) sortComboBox.addActionListener(l);
	}

	// ---------------------------------------------------------------
	// Export
	// ---------------------------------------------------------------

	private void exportAsPNG() {
		if (noData()) return;
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Export as PNG");
		chooser.setFileFilter(new FileNameExtensionFilter("PNG images (*.png)", "png"));
		if (loadedFile != null) {
			String base = loadedFile.getName().replaceFirst("\\.[^.]+$", "");
			chooser.setSelectedFile(new File(loadedFile.getParent(), base + ".png"));
		}
		if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			if (!file.getName().toLowerCase().endsWith(".png"))
				file = new File(file.getAbsolutePath() + ".png");
			try {
				matrixPanel.exportAsImage(file);
				JOptionPane.showMessageDialog(this,
						"Image exported to:\n" + file.getAbsolutePath(),
						"Export Complete", JOptionPane.INFORMATION_MESSAGE);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(this,
						"Error exporting: " + ex.getMessage(),
						"Export Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void exportAsCSV() {
		if (noData()) return;
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Export as CSV");
		chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
		if (loadedFile != null) {
			String base = loadedFile.getName().replaceFirst("\\.[^.]+$", "");
			chooser.setSelectedFile(new File(loadedFile.getParent(), base + ".csv"));
		}
		if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			File file = chooser.getSelectedFile();
			if (!file.getName().toLowerCase().endsWith(".csv"))
				file = new File(file.getAbsolutePath() + ".csv");
			try {
				matrixPanel.exportAsCSV(file);
				JOptionPane.showMessageDialog(this,
						"CSV exported to:\n" + file.getAbsolutePath(),
						"Export Complete", JOptionPane.INFORMATION_MESSAGE);
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(this,
						"Error exporting: " + ex.getMessage(),
						"Export Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private boolean noData() {
		if (filteredItemsets == null || filteredItemsets.isEmpty()) {
			JOptionPane.showMessageDialog(this,
					"No data to export. Load a file first.",
					"No Data", JOptionPane.WARNING_MESSAGE);
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------
	// Filter / sort
	// ---------------------------------------------------------------

	/**
	 * Parses the search query into a set of search terms.
	 */
	private Set<String> parseSearchTerms(String query) {
		Set<String> terms = new HashSet<>();
		if (query == null || query.trim().isEmpty()) return terms;
		for (String token : query.split("[,\\s]+")) {
			String t = token.trim().toLowerCase();
			if (!t.isEmpty()) terms.add(t);
		}
		return terms;
	}

	/**
	 * Tests whether an itemset contains all the given search terms
	 * (case-insensitive substring match).
	 */
	private boolean matchesAllTerms(Itemset itemset, Set<String> terms) {
		List<String> items = itemset.getItems();
		for (String term : terms) {
			boolean found = false;
			for (String item : items) {
				if (item.toLowerCase().contains(term)) { found = true; break; }
			}
			if (!found) return false;
		}
		return true;
	}

	private void applyFilter() {
		Set<String> terms = parseSearchTerms(filterField.getText());
		int minSize = (Integer) minSizeSpinner.getValue();
		int maxSize = (Integer) maxSizeSpinner.getValue();
		boolean hasItems = !terms.isEmpty();
		boolean hasSize = (minSize > 0 || maxSize < 9999);

		if (!hasItems && !hasSize) {
			filteredItemsets = new ArrayList<>(allItemsets);
		} else {
			filteredItemsets = new ArrayList<>();
			for (Itemset itemset : allItemsets) {
				int sz = itemset.getItems().size();
				if (sz < minSize || sz > maxSize) continue;
				if (hasItems && !matchesAllTerms(itemset, terms)) continue;
				filteredItemsets.add(itemset);
			}
		}

		applySortAndRefresh();

		// Update histogram to reflect filtered data
		histogramPanel.updateData(filteredItemsets);

		if (hasItems || hasSize) {
			filterResultLabel.setText(filteredItemsets.size() + " of " + allItemsets.size());
			fileInfoLabel.setText(String.format("Showing %d of %d patterns",
					filteredItemsets.size(), allItemsets.size()));
		} else {
			filterResultLabel.setText(" ");
			if (!allItemsets.isEmpty() && loadedFile != null) {
				fileInfoLabel.setText(String.format("%s  \u2014  %d patterns loaded",
						loadedFile.getName(), allItemsets.size()));
			}
		}
		updateStatusRight();
	}

	private void applySortAndRefresh() {
		applySort();
		matrixPanel.setItemsets(filteredItemsets, selectedItemset,
				highlightSubsetsCheckbox.isSelected(),
				highlightSupersetsCheckbox.isSelected());
	}

	private void updateHighlighting() {
		matrixPanel.updateSelectionState(selectedItemset,
				highlightSubsetsCheckbox.isSelected(),
				highlightSupersetsCheckbox.isSelected());
	}

	private void applySort() {
		String option = (String) sortComboBox.getSelectedItem();
		if (option == null || filteredItemsets.isEmpty()) return;
		sortFiltered(option);
	}

	private void sortFiltered(String order) {
		if ("Size (asc)".equals(order)) {
			filteredItemsets.sort(java.util.Comparator.comparingInt(r -> r.getItems().size()));
		} else if ("Size (desc)".equals(order)) {
			filteredItemsets.sort(java.util.Comparator.comparingInt(
					(Itemset r) -> r.getItems().size()).reversed());
		} else if ("Lexicographical (asc)".equals(order)) {
			filteredItemsets.sort(java.util.Comparator.comparing(
					r -> String.join(" ", r.getItems())));
		} else if ("Lexicographical (desc)".equals(order)) {
			filteredItemsets.sort(java.util.Comparator.comparing(
					(Itemset r) -> String.join(" ", r.getItems())).reversed());
		} else if (order.endsWith("(asc)")) {
			sortByMeasure(order.substring(0, order.length() - 6).trim(), true);
		} else if (order.endsWith("(desc)")) {
			sortByMeasure(order.substring(0, order.length() - 7).trim(), false);
		}
	}

	private void sortByMeasure(String measure, boolean asc) {
		filteredItemsets.sort((a, b) -> {
			String sa = a.getMeasures().getOrDefault(measure, "");
			String sb = b.getMeasures().getOrDefault(measure, "");
			try {
				double va = Double.parseDouble(sa), vb = Double.parseDouble(sb);
				return asc ? Double.compare(va, vb) : Double.compare(vb, va);
			} catch (NumberFormatException e) {
				int cmp = sa.compareTo(sb);
				return asc ? cmp : -cmp;
			}
		});
	}

	private void onItemsetSelected(Itemset itemset) {
		selectedItemset = itemset;
		updateStatusRight();
	}

	// ---------------------------------------------------------------
	// Main
	// ---------------------------------------------------------------

	/**
	 * Launches the viewer in standalone mode.
	 *
	 * @param args optional: args[0] = path to itemset file
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
			catch (Exception ignored) {}

			ItemsetItemMatrixViewer viewer = (args.length > 0 && args[0] != null && !args[0].isEmpty())
					? new ItemsetItemMatrixViewer(args[0], true)
					: new ItemsetItemMatrixViewer(true);
			viewer.setVisible(true);
		});
	}
}