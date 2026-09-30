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
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntConsumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
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
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * A standalone viewer window for the item-pattern matrix.
 *
 * <p>
 * <b>Standalone mode:</b> run {@link #main(String[])} or call
 * {@code new ItemsetItemMatrixViewer(true)} to get a full window with
 * file-loading, filtering, sorting, and highlighting controls.
 *
 * <p>
 * <b>Embedded mode:</b> call {@link #ItemsetItemMatrixViewer(String, boolean)}
 * with a file path to open a viewer pre-loaded with that file. The window uses
 * {@code DISPOSE_ON_CLOSE} so it does not kill the calling application.
 *
 * <p>
 * <b>Search syntax:</b> The item filter accepts item names separated by commas
 * and/or spaces. A pattern matches only if it contains <b>all</b> specified
 * items (AND search), by substring or — with "exact names" checked — by exact
 * (case-insensitive) name.
 *
 * @author Philippe Fournier-Viger, 2024
 */
public class ItemsetItemMatrixViewer extends JFrame {

	private static final long serialVersionUID = 1L;

	private static final String WINDOW_TITLE = "SPMF - Itemset-Item Matrix Viewer";
	private static final String NO_MEASURE = "(none)";
	private static final int MAX_SIZE = 9999;
	private static final int FILTER_DEBOUNCE_MS = 200;
	private static final Color COLOR_INVALID_FIELD = new Color(255, 225, 225);

	private static final String INSTRUCTIONS =
			"Click row to select | \u2191\u2193 PgUp/PgDn navigate | \u2190\u2192 scroll | "
					+ "Drag to pan | Ctrl+Scroll zoom | Shift+Scroll horizontal | "
					+ "Ctrl+F search | Ctrl+C copy | Esc deselect | Drop a file to open";

	// ---------------------------------------------------------------
	// Data
	// ---------------------------------------------------------------

	private ItemsetReader reader;
	private List<Itemset> allItemsets = new ArrayList<>();
	private List<Itemset> filteredItemsets = new ArrayList<>();
	private Itemset selectedItemset;
	private File loadedFile;
	private File lastDirectory;
	/** Size selected by clicking in the histogram, or null */
	private Integer histogramSizeFilter;
	/** When true, control events do not trigger filtering/sorting */
	private boolean suppressEvents;

	// ---------------------------------------------------------------
	// UI components
	// ---------------------------------------------------------------

	private JLabel fileInfoLabel;
	private JLabel statusLabel;
	private JLabel zoomLabel;
	private JLabel fontSizeLabel;
	private ItemsetItemMatrixPanel matrixPanel;
	private JTextField filterField;
	private JCheckBox exactMatchCheckbox;
	private JLabel filterResultLabel;
	private JSpinner minSizeSpinner;
	private JSpinner maxSizeSpinner;
	private JComboBox<String> measureFilterCombo;
	private JTextField measureMinField;
	private JTextField measureMaxField;
	private JComboBox<String> sortComboBox;
	private JCheckBox highlightSubsetsCheckbox;
	private JCheckBox highlightSupersetsCheckbox;
	private JTextArea selectionArea;
	private JButton copyButton;
	private JPanel legendPanel;
	private SizeHistogramPanel histogramPanel;
	private JCheckBoxMenuItem showCheckMarksItem;
	private JCheckBoxMenuItem showRowNumbersItem;
	private JCheckBoxMenuItem showMeasureColumnsItem;
	private final Map<Integer, JRadioButtonMenuItem> cellSizeItems = new HashMap<>();
	private ButtonGroup cellSizeGroup;
	private Timer filterTimer;

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
		setSize(1250, 780);
		setLocationRelativeTo(null);

		initComponents();
		buildMenuBar();
		layoutComponents();
		setupListeners();
		setupDragAndDrop();
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
			loadItemsetsFromFile(new File(filePath));
		}
	}

	// ---------------------------------------------------------------
	// Initialisation
	// ---------------------------------------------------------------

	private void initComponents() {
		fileInfoLabel = new JLabel("No file loaded \u2014 use File \u2192 Open or drop a file on the window");
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
		fontSizeLabel.setForeground(new Color(100, 100, 100));
		updateFontSizeLabel();

		filterField = new JTextField(14);
		filterField.setToolTipText("<html>Show only patterns containing <b>all</b> listed items.<br>"
				+ "Separate items with commas or spaces, e.g. <b>1, 2, 3</b> or <b>bread milk</b>.</html>");
		exactMatchCheckbox = new JCheckBox("Match exact item names", false);
		exactMatchCheckbox.setToolTipText("Unchecked: substring match (\"1\" matches \"10\"). "
				+ "Checked: whole item name must match.");

		filterResultLabel = new JLabel(" ");
		filterResultLabel.setFont(new Font("SansSerif", Font.ITALIC, 10));
		filterResultLabel.setForeground(new Color(100, 100, 100));

		minSizeSpinner = new JSpinner(new SpinnerNumberModel(0, 0, MAX_SIZE, 1));
		maxSizeSpinner = new JSpinner(new SpinnerNumberModel(MAX_SIZE, 0, MAX_SIZE, 1));
		minSizeSpinner.setToolTipText("Minimum pattern size (0 = no minimum)");
		maxSizeSpinner.setToolTipText("Maximum pattern size (" + MAX_SIZE + " = no maximum)");
		((JSpinner.DefaultEditor) minSizeSpinner.getEditor()).getTextField().setColumns(3);
		((JSpinner.DefaultEditor) maxSizeSpinner.getEditor()).getTextField().setColumns(3);

		measureFilterCombo = new JComboBox<>(new String[] { NO_MEASURE });
		measureMinField = new JTextField(5);
		measureMaxField = new JTextField(5);
		measureMinField.setToolTipText("Minimum value (inclusive), empty = no minimum");
		measureMaxField.setToolTipText("Maximum value (inclusive), empty = no maximum");

		sortComboBox = new JComboBox<>(ItemsetReader.getDefaultSortingOrders().toArray(new String[0]));

		highlightSubsetsCheckbox = new JCheckBox("Highlight subsets of selection", false);
		highlightSupersetsCheckbox = new JCheckBox("Highlight supersets of selection", false);

		selectionArea = new JTextArea(5, 18);
		selectionArea.setEditable(false);
		selectionArea.setLineWrap(true);
		selectionArea.setWrapStyleWord(true);
		selectionArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
		copyButton = new JButton("Copy");
		copyButton.setToolTipText("Copy the selected pattern in SPMF format (Ctrl+C in the matrix)");
		copyButton.setEnabled(false);

		legendPanel = buildLegendPanel();
		histogramPanel = new SizeHistogramPanel();

		filterTimer = new Timer(FILTER_DEBOUNCE_MS, e -> applyFilter());
		filterTimer.setRepeats(false);
	}

	private static KeyStroke ctrl(int key) {
		return KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK);
	}

	private static KeyStroke ctrlShift(int key) {
		return KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
	}

	private void buildMenuBar() {
		JMenuBar menuBar = new JMenuBar();

		// ---- File ----
		JMenu fileMenu = new JMenu("File");
		fileMenu.setMnemonic(KeyEvent.VK_F);

		JMenuItem openItem = new JMenuItem("Open...");
		openItem.setAccelerator(ctrl(KeyEvent.VK_O));
		openItem.addActionListener(e -> chooseAndLoadFile());

		JMenuItem reloadItem = new JMenuItem("Reload");
		reloadItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
		reloadItem.addActionListener(e -> {
			if (loadedFile != null) loadItemsetsFromFile(loadedFile);
		});

		JMenuItem exportView = new JMenuItem("Export View as PNG...");
		exportView.setAccelerator(ctrl(KeyEvent.VK_P));
		exportView.addActionListener(e -> exportAsPNG(false));

		JMenuItem exportFull = new JMenuItem("Export Full Matrix as PNG...");
		exportFull.addActionListener(e -> exportAsPNG(true));

		JMenuItem exportCsv = new JMenuItem("Export as CSV...");
		exportCsv.addActionListener(e -> exportAsCSV());

		JMenuItem exitItem = new JMenuItem("Exit");
		exitItem.addActionListener(e -> dispose());

		fileMenu.add(openItem);
		fileMenu.add(reloadItem);
		fileMenu.addSeparator();
		fileMenu.add(exportView);
		fileMenu.add(exportFull);
		fileMenu.add(exportCsv);
		fileMenu.addSeparator();
		fileMenu.add(exitItem);

		// ---- Edit ----
		JMenu editMenu = new JMenu("Edit");
		editMenu.setMnemonic(KeyEvent.VK_E);

		JMenuItem findItem = new JMenuItem("Find...");
		findItem.setAccelerator(ctrl(KeyEvent.VK_F));
		findItem.addActionListener(e -> {
			filterField.requestFocusInWindow();
			filterField.selectAll();
		});

		JMenuItem clearFilter = new JMenuItem("Clear Filter");
		clearFilter.addActionListener(e -> clearFilters());

		JMenuItem copyItem = new JMenuItem("Copy Selected Pattern");
		copyItem.setAccelerator(ctrlShift(KeyEvent.VK_C));
		copyItem.addActionListener(e -> copySelection());

		JMenuItem clearSel = new JMenuItem("Clear Selection");
		clearSel.addActionListener(e -> matrixPanel.clearSelection());

		editMenu.add(findItem);
		editMenu.add(clearFilter);
		editMenu.addSeparator();
		editMenu.add(copyItem);
		editMenu.add(clearSel);

		// ---- View ----
		JMenu viewMenu = new JMenu("View");
		viewMenu.setMnemonic(KeyEvent.VK_V);

		JMenu zoomMenu = new JMenu("Zoom");
		JMenuItem zoomIn = new JMenuItem("Zoom In");
		zoomIn.setAccelerator(ctrl(KeyEvent.VK_EQUALS));
		zoomIn.addActionListener(e -> matrixPanel.zoomIn());
		JMenuItem zoomOut = new JMenuItem("Zoom Out");
		zoomOut.setAccelerator(ctrl(KeyEvent.VK_MINUS));
		zoomOut.addActionListener(e -> matrixPanel.zoomOut());
		JMenuItem zoomReset = new JMenuItem("Reset Zoom");
		zoomReset.setAccelerator(ctrl(KeyEvent.VK_0));
		zoomReset.addActionListener(e -> matrixPanel.resetZoom());
		zoomMenu.add(zoomIn);
		zoomMenu.add(zoomOut);
		zoomMenu.add(zoomReset);

		JMenu cellSizeMenu = new JMenu("Cell Size");
		int[] sizes = { 16, 20, 25, 30, 40, 50 };
		String[] sizeLabels = { "Tiny (16)", "Small (20)", "Medium (25)", "Default (30)",
				"Large (40)", "Extra Large (50)" };
		cellSizeGroup = new ButtonGroup();
		for (int i = 0; i < sizes.length; i++) {
			final int sz = sizes[i];
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(sizeLabels[i], sz == 30);
			cellSizeGroup.add(item);
			cellSizeItems.put(sz, item);
			item.addActionListener(e -> matrixPanel.setCellSize(sz));
			cellSizeMenu.add(item);
		}

		JMenu fontSizeMenu = new JMenu("Font Size");
		JMenuItem fontInc = new JMenuItem("Increase");
		fontInc.setAccelerator(ctrlShift(KeyEvent.VK_EQUALS));
		fontInc.addActionListener(e -> changeFontSize(1));
		JMenuItem fontDec = new JMenuItem("Decrease");
		fontDec.setAccelerator(ctrlShift(KeyEvent.VK_MINUS));
		fontDec.addActionListener(e -> changeFontSize(-1));
		JMenuItem fontReset = new JMenuItem("Reset to Default");
		fontReset.setAccelerator(ctrlShift(KeyEvent.VK_0));
		fontReset.addActionListener(e -> {
			matrixPanel.setFontSize(ItemsetItemMatrixPanel.DEFAULT_FONT_SIZE);
			updateFontSizeLabel();
		});
		fontSizeMenu.add(fontInc);
		fontSizeMenu.add(fontDec);
		fontSizeMenu.addSeparator();
		fontSizeMenu.add(fontReset);

		JMenu columnOrderMenu = new JMenu("Column Order");
		ButtonGroup orderGroup = new ButtonGroup();
		for (ItemsetItemMatrixPanel.ColumnOrder order : ItemsetItemMatrixPanel.ColumnOrder.values()) {
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(order.getLabel(),
					order == matrixPanel.getColumnOrder());
			item.addActionListener(e -> matrixPanel.setColumnOrder(order));
			orderGroup.add(item);
			columnOrderMenu.add(item);
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

		viewMenu.add(zoomMenu);
		viewMenu.add(cellSizeMenu);
		viewMenu.add(fontSizeMenu);
		viewMenu.add(columnOrderMenu);
		viewMenu.addSeparator();
		viewMenu.add(showCheckMarksItem);
		viewMenu.add(showRowNumbersItem);
		viewMenu.add(showMeasureColumnsItem);

		// ---- Help ----
		JMenu helpMenu = new JMenu("Help");
		helpMenu.setMnemonic(KeyEvent.VK_H);
		JMenuItem shortcuts = new JMenuItem("Keyboard Shortcuts...");
		shortcuts.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
		shortcuts.addActionListener(e -> showShortcutsDialog());
		helpMenu.add(shortcuts);

		menuBar.add(fileMenu);
		menuBar.add(editMenu);
		menuBar.add(viewMenu);
		menuBar.add(helpMenu);
		setJMenuBar(menuBar);
	}

	private JPanel buildLegendPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("Legend"));
		panel.add(legendEntry(ItemsetItemMatrixPanel.COLOR_CELL_PRESENT, "Item present"));
		panel.add(legendEntry(ItemsetItemMatrixPanel.COLOR_CELL_ABSENT, "Item absent"));
		panel.add(legendEntry(ItemsetItemMatrixPanel.COLOR_CELL_SELECTED, "Selected pattern"));
		panel.add(legendEntry(ItemsetItemMatrixPanel.COLOR_CELL_HOVER, "Hovered pattern"));
		panel.add(legendEntry(ItemsetItemMatrixPanel.COLOR_CELL_SUBSET, "Subset of selected"));
		panel.add(legendEntry(ItemsetItemMatrixPanel.COLOR_CELL_SUPERSET, "Superset of selected"));
		return panel;
	}

	private JPanel legendEntry(Color color, String label) {
		JPanel entry = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
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
	 * A small bar chart of the distribution of pattern sizes. Hovering a bar
	 * shows the exact count; clicking a bar filters on that size (clicking the
	 * same bar again clears the filter).
	 */
	private static class SizeHistogramPanel extends JPanel {

		private static final long serialVersionUID = 1L;

		private static final int HIST_HEIGHT = 90;
		private static final int BAR_GAP = 2;
		private static final int LABEL_AREA = 16;
		private static final int TOP_PAD = 4;
		private static final int SIDE_PAD = 6;
		private static final int MAX_BAR_WIDTH = 30;

		private static final Color BAR_COLOR = new Color(46, 134, 193);
		private static final Color BAR_HOVER_COLOR = new Color(52, 152, 219);
		private static final Color BAR_SELECTED_COLOR = new Color(231, 76, 60);
		private static final Color AXIS_COLOR = new Color(160, 160, 160);
		private static final Color LABEL_COLOR = new Color(100, 100, 100);
		private static final Color EMPTY_COLOR = new Color(150, 150, 150);

		private static final Font FONT_LABEL = new Font("SansSerif", Font.PLAIN, 9);
		private static final Font FONT_COUNT = new Font("SansSerif", Font.BOLD, 9);
		private static final Font FONT_EMPTY = new Font("SansSerif", Font.ITALIC, 10);

		/** Parallel arrays (sorted by size) for O(1) access by bar index */
		private int[] sizes = new int[0];
		private int[] counts = new int[0];
		private int maxCount = 0;
		private int totalPatterns = 0;
		private int hoveredBarIndex = -1;
		private Integer selectedSize;
		private IntConsumer clickListener;

		SizeHistogramPanel() {
			setBorder(BorderFactory.createTitledBorder("Size Distribution (click to filter)"));
			int h = HIST_HEIGHT + LABEL_AREA + TOP_PAD + 30;
			setPreferredSize(new Dimension(190, h));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
			ToolTipManager.sharedInstance().registerComponent(this);

			MouseAdapter ma = new MouseAdapter() {
				@Override
				public void mouseMoved(MouseEvent e) {
					int idx = getBarIndexAt(e.getX(), e.getY());
					if (idx != hoveredBarIndex) {
						hoveredBarIndex = idx;
						setCursor(Cursor.getPredefinedCursor(
								idx >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
						repaint();
					}
				}

				@Override
				public void mouseExited(MouseEvent e) {
					if (hoveredBarIndex != -1) {
						hoveredBarIndex = -1;
						repaint();
					}
				}

				@Override
				public void mouseClicked(MouseEvent e) {
					int idx = getBarIndexAt(e.getX(), e.getY());
					if (idx >= 0 && clickListener != null) clickListener.accept(sizes[idx]);
				}
			};
			addMouseListener(ma);
			addMouseMotionListener(ma);
		}

		void setClickListener(IntConsumer listener) {
			this.clickListener = listener;
		}

		/** Updates the histogram; {@code selected} is the size currently filtered on (or null). */
		void updateData(List<Itemset> itemsets, Integer selected) {
			TreeMap<Integer, Integer> distribution = new TreeMap<>();
			totalPatterns = 0;
			maxCount = 0;
			if (itemsets != null) {
				totalPatterns = itemsets.size();
				for (Itemset it : itemsets) distribution.merge(it.size(), 1, Integer::sum);
			}
			sizes = new int[distribution.size()];
			counts = new int[distribution.size()];
			int i = 0;
			for (Map.Entry<Integer, Integer> en : distribution.entrySet()) {
				sizes[i] = en.getKey();
				counts[i] = en.getValue();
				maxCount = Math.max(maxCount, en.getValue());
				i++;
			}
			selectedSize = selected;
			hoveredBarIndex = -1;
			repaint();
		}

		/** Returns {drawLeft, drawRight, barAreaTop, barAreaBot, barW, startX}. */
		private int[] layoutB() {
			Insets ins = getInsets();
			int drawLeft = ins.left + SIDE_PAD;
			int drawRight = getWidth() - ins.right - SIDE_PAD;
			int drawW = Math.max(0, drawRight - drawLeft);
			int barAreaTop = ins.top + TOP_PAD;
			int barAreaBot = getHeight() - ins.bottom - LABEL_AREA;
			int n = Math.max(1, sizes.length);
			int totalGap = (n - 1) * BAR_GAP;
			int barW = Math.min(MAX_BAR_WIDTH, Math.max(4, (drawW - totalGap) / n));
			int usedW = n * barW + totalGap;
			int startX = drawLeft + (drawW - usedW) / 2;
			return new int[] { drawLeft, drawRight, barAreaTop, barAreaBot, barW, startX };
		}

		private int getBarIndexAt(int mx, int my) {
			if (sizes.length == 0) return -1;
			int[] l = layoutB();
			if (my < l[2] || my > l[3]) return -1;
			int rel = mx - l[5];
			if (rel < 0) return -1;
			int idx = rel / (l[4] + BAR_GAP);
			if (idx >= sizes.length || rel % (l[4] + BAR_GAP) >= l[4]) return -1;
			return idx;
		}

		@Override
		public String getToolTipText(MouseEvent e) {
			int idx = getBarIndexAt(e.getX(), e.getY());
			if (idx < 0) return null;
			double pct = (totalPatterns > 0) ? 100.0 * counts[idx] / totalPatterns : 0;
			boolean sel = selectedSize != null && selectedSize == sizes[idx];
			return String.format("<html><b>Size %d:</b> %d patterns (%.1f%%)<br><i>%s</i></html>",
					sizes[idx], counts[idx], pct,
					sel ? "Click to clear the size filter" : "Click to show only this size");
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
					RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

			int[] l = layoutB();
			int drawLeft = l[0], drawRight = l[1], barAreaTop = l[2], barAreaBot = l[3];
			int barW = l[4], startX = l[5];
			int barAreaH = barAreaBot - barAreaTop;

			if (sizes.length == 0) {
				g2.setFont(FONT_EMPTY);
				g2.setColor(EMPTY_COLOR);
				String msg = "No data";
				FontMetrics fm = g2.getFontMetrics();
				g2.drawString(msg, drawLeft + (drawRight - drawLeft - fm.stringWidth(msg)) / 2,
						barAreaTop + barAreaH / 2 + fm.getAscent() / 2);
				return;
			}

			g2.setColor(AXIS_COLOR);
			g2.drawLine(drawLeft, barAreaBot, drawRight, barAreaBot);

			FontMetrics fmC = g2.getFontMetrics(FONT_COUNT);
			FontMetrics fmL = g2.getFontMetrics(FONT_LABEL);

			for (int idx = 0; idx < sizes.length; idx++) {
				int barH = Math.max(2, (int) ((double) counts[idx] / maxCount * barAreaH));
				int bx = startX + idx * (barW + BAR_GAP);
				int by = barAreaBot - barH;

				boolean selected = selectedSize != null && selectedSize == sizes[idx];
				g2.setColor(selected ? BAR_SELECTED_COLOR
						: (idx == hoveredBarIndex ? BAR_HOVER_COLOR : BAR_COLOR));
				g2.fillRect(bx, by, barW, barH);

				String countStr = String.valueOf(counts[idx]);
				int cw = fmC.stringWidth(countStr);
				if (cw <= barW + BAR_GAP && by - 2 >= barAreaTop + fmC.getAscent()) {
					g2.setFont(FONT_COUNT);
					g2.setColor(LABEL_COLOR);
					g2.drawString(countStr, bx + (barW - cw) / 2, by - 2);
				}

				String sizeStr = String.valueOf(sizes[idx]);
				int lw = fmL.stringWidth(sizeStr);
				if (lw <= barW + BAR_GAP) {
					g2.setFont(FONT_LABEL);
					g2.setColor(LABEL_COLOR);
					g2.drawString(sizeStr, bx + (barW - lw) / 2, barAreaBot + fmL.getAscent() + 1);
				}
			}
		}
	}
	
	// ---------------------------------------------------------------
	// Sidebar container that follows the width of its scroll pane
	// ---------------------------------------------------------------

	/**
	 * A vertical panel that always takes the width of the enclosing scroll
	 * pane's viewport (so its children re-flow when the split pane divider is
	 * dragged) while still scrolling vertically when there is not enough height.
	 */
	private static class SidebarPanel extends JPanel implements Scrollable {

		private static final long serialVersionUID = 1L;

		SidebarPanel() {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		}

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
			return (orientation == SwingConstants.VERTICAL) ? visibleRect.height : visibleRect.width;
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			return true; // key: width follows the viewport, never clipped horizontally
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			// Fill the viewport when content is shorter than it; scroll when taller
			return getParent() instanceof javax.swing.JViewport
					&& getPreferredSize().height < getParent().getHeight();
		}
	}
	
	



	// ---------------------------------------------------------------
	// Layout
	// ---------------------------------------------------------------

	/** Default width of the left sidebar (split pane divider position) */
	private static final int SIDEBAR_DEFAULT_WIDTH = 320;
	/** Minimum width the sidebar can be shrunk to by dragging */
	private static final int SIDEBAR_MIN_WIDTH = 220;
	/** Minimum width kept for the matrix */
	private static final int MATRIX_MIN_WIDTH = 300;

	private static JPanel row(JComponent... comps) {
		JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		for (JComponent c : comps) p.add(c);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		p.setAlignmentX(LEFT_ALIGNMENT);
		return p;
	}

	/** Wraps a label + component in a row that stretches horizontally. */
	private static JPanel labelled(String label, JComponent comp) {
		JPanel p = new JPanel(new BorderLayout(4, 0));
		p.add(new JLabel(label), BorderLayout.WEST);
		p.add(comp, BorderLayout.CENTER);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		p.setAlignmentX(LEFT_ALIGNMENT);
		return p;
	}

	private void layoutComponents() {
		setLayout(new BorderLayout(0, 0));

		// --- Top bar ---
		JPanel topPanel = new JPanel(new BorderLayout(8, 0));
		topPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 200, 200)),
				BorderFactory.createEmptyBorder(6, 10, 6, 10)));
		topPanel.add(fileInfoLabel, BorderLayout.CENTER);
		topPanel.add(fontSizeLabel, BorderLayout.EAST);

		// --- Filter section ---
		JPanel filterSection = new JPanel();
		filterSection.setLayout(new BoxLayout(filterSection, BoxLayout.Y_AXIS));
		filterSection.setBorder(BorderFactory.createTitledBorder("Filter"));

		JLabel searchHint = new JLabel(
				"<html><i style='font-size:9px;color:#999'>e.g. 1, 2, 3 (all must be present)</i></html>");
		searchHint.setAlignmentX(LEFT_ALIGNMENT);
		exactMatchCheckbox.setAlignmentX(LEFT_ALIGNMENT);

		JButton clearBtn = new JButton("Clear");
		clearBtn.addActionListener(e -> clearFilters());
		JPanel clearRow = new JPanel(new BorderLayout(4, 0));
		clearRow.add(filterResultLabel, BorderLayout.CENTER);
		clearRow.add(clearBtn, BorderLayout.EAST);
		clearRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		clearRow.setAlignmentX(LEFT_ALIGNMENT);

		// Let the text fields stretch with the sidebar instead of using fixed columns
		filterField.setColumns(0);
		measureMinField.setColumns(6);
		measureMaxField.setColumns(6);

		JPanel measureRange = new JPanel(new java.awt.GridLayout(1, 2, 4, 0));
		measureRange.add(labelled("Min:", measureMinField));
		measureRange.add(labelled("Max:", measureMaxField));
		measureRange.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		measureRange.setAlignmentX(LEFT_ALIGNMENT);

		filterSection.add(labelled("Items:", filterField));
		filterSection.add(searchHint);
		filterSection.add(exactMatchCheckbox);
		filterSection.add(Box.createVerticalStrut(4));
		filterSection.add(row(new JLabel("Size:"), minSizeSpinner, new JLabel("\u2013"), maxSizeSpinner));
		filterSection.add(Box.createVerticalStrut(4));
		filterSection.add(labelled("Measure:", measureFilterCombo));
		filterSection.add(Box.createVerticalStrut(2));
		filterSection.add(measureRange);
		filterSection.add(Box.createVerticalStrut(4));
		filterSection.add(clearRow);

		// --- Sort & highlight section ---
		JPanel sortSection = new JPanel();
		sortSection.setLayout(new BoxLayout(sortSection, BoxLayout.Y_AXIS));
		sortSection.setBorder(BorderFactory.createTitledBorder("Sort & Highlight"));
		highlightSubsetsCheckbox.setAlignmentX(LEFT_ALIGNMENT);
		highlightSupersetsCheckbox.setAlignmentX(LEFT_ALIGNMENT);
		sortSection.add(labelled("Order:", sortComboBox));
		sortSection.add(Box.createVerticalStrut(4));
		sortSection.add(highlightSubsetsCheckbox);
		sortSection.add(highlightSupersetsCheckbox);

		// --- Selected pattern section ---
		JPanel selectionSection = new JPanel(new BorderLayout(4, 4));
		selectionSection.setBorder(BorderFactory.createTitledBorder("Selected Pattern"));
		JScrollPane selScroll = new JScrollPane(selectionArea);
		selScroll.setPreferredSize(new Dimension(100, 95));
		selectionSection.add(selScroll, BorderLayout.CENTER);
		JPanel copyRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		copyRow.add(copyButton);
		selectionSection.add(copyRow, BorderLayout.SOUTH);
		selectionSection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
		updateSelectionPanel();

		// --- Sidebar (tracks the viewport width, scrolls vertically if needed) ---
		SidebarPanel sidebar = new SidebarPanel();
		sidebar.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		for (JComponent c : new JComponent[] { filterSection, sortSection, selectionSection,
				legendPanel, histogramPanel }) {
			c.setAlignmentX(LEFT_ALIGNMENT);
			c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getMaximumSize().height));
			sidebar.add(c);
			sidebar.add(Box.createVerticalStrut(6));
		}
		sidebar.add(Box.createVerticalGlue());

		JScrollPane sidebarScroll = new JScrollPane(sidebar,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		sidebarScroll.setBorder(BorderFactory.createEmptyBorder());
		sidebarScroll.setMinimumSize(new Dimension(SIDEBAR_MIN_WIDTH, 0));
		sidebarScroll.setPreferredSize(new Dimension(SIDEBAR_DEFAULT_WIDTH, 0));

		matrixPanel.setMinimumSize(new Dimension(MATRIX_MIN_WIDTH, 0));

		// --- Split pane: user can drag the divider or collapse either side ---
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarScroll, matrixPanel);
		splitPane.setDividerLocation(SIDEBAR_DEFAULT_WIDTH);
		splitPane.setResizeWeight(0.0);        // extra space goes to the matrix
		splitPane.setContinuousLayout(true);   // matrix repaints while dragging
		splitPane.setOneTouchExpandable(true); // arrows to collapse/expand the sidebar
		splitPane.setDividerSize(8);
		splitPane.setBorder(BorderFactory.createEmptyBorder());

		// --- Status bar ---
		JPanel statusBar = new JPanel(new BorderLayout());
		statusBar.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(200, 200, 200)),
				BorderFactory.createEmptyBorder(2, 0, 2, 0)));
		statusBar.add(statusLabel, BorderLayout.CENTER);
		statusBar.add(zoomLabel, BorderLayout.EAST);

		add(topPanel, BorderLayout.NORTH);
		add(splitPane, BorderLayout.CENTER);
		add(statusBar, BorderLayout.SOUTH);
	}

	private void setupListeners() {
		DocumentListener debounced = new DocumentListener() {
			public void insertUpdate(DocumentEvent e) { scheduleFilter(); }
			public void removeUpdate(DocumentEvent e) { scheduleFilter(); }
			public void changedUpdate(DocumentEvent e) { scheduleFilter(); }
		};
		filterField.getDocument().addDocumentListener(debounced);
		measureMinField.getDocument().addDocumentListener(debounced);
		measureMaxField.getDocument().addDocumentListener(debounced);
		exactMatchCheckbox.addActionListener(e -> scheduleFilter());
		minSizeSpinner.addChangeListener(e -> scheduleFilter());
		maxSizeSpinner.addChangeListener(e -> scheduleFilter());
		measureFilterCombo.addActionListener(e -> scheduleFilter());

		sortComboBox.addActionListener(e -> {
			if (!suppressEvents) applySortAndRefresh();
		});

		highlightSubsetsCheckbox.addActionListener(e -> updateHighlighting());
		highlightSupersetsCheckbox.addActionListener(e -> updateHighlighting());

		copyButton.addActionListener(e -> copySelection());

		histogramPanel.setClickListener(size -> {
			histogramSizeFilter = (histogramSizeFilter != null && histogramSizeFilter == size)
					? null : Integer.valueOf(size);
			applyFilter();
		});

		matrixPanel.setItemsetSelectionListener(this::onItemsetSelected);
		matrixPanel.setViewChangeListener(() -> {
			updateStatusRight();
			syncCellSizeMenu();
		});
	}

	private void setupDragAndDrop() {
		new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
			@Override
			public void drop(DropTargetDropEvent dtde) {
				try {
					dtde.acceptDrop(DnDConstants.ACTION_COPY);
					Transferable t = dtde.getTransferable();
					if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
						List<?> files = (List<?>) t.getTransferData(DataFlavor.javaFileListFlavor);
						if (!files.isEmpty() && files.get(0) instanceof File) {
							dtde.dropComplete(true);
							loadItemsetsFromFile((File) files.get(0));
							return;
						}
					}
					dtde.dropComplete(false);
				} catch (Exception ex) {
					dtde.dropComplete(false);
				}
			}
		}, true);
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
	}

	private void syncCellSizeMenu() {
		JRadioButtonMenuItem item = cellSizeItems.get(matrixPanel.getCellSize());
		if (item != null) item.setSelected(true);
		else cellSizeGroup.clearSelection();
	}

	private void updateStatusRight() {
		StringBuilder sb = new StringBuilder();
		int total = matrixPanel.getRowCount();
		if (total > 0) {
			int first = matrixPanel.getFirstVisibleRow();
			int last = matrixPanel.getLastVisibleRow();
			if (first >= 0 && last >= 0) {
				sb.append("Rows ").append(first + 1).append("\u2013").append(last + 1)
						.append(" of ").append(total).append(" | ");
			}
			int sel = matrixPanel.getSelectedIndex();
			if (sel >= 0) sb.append("Row ").append(sel + 1).append(" selected | ");
		}
		sb.append("Zoom: ").append(matrixPanel.getZoomPercent()).append('%');
		zoomLabel.setText(sb.toString());
	}

	private void updateSelectionPanel() {
		if (selectedItemset == null) {
			selectionArea.setText("No pattern selected.\nClick a row in the matrix.");
			copyButton.setEnabled(false);
			return;
		}
		StringBuilder sb = new StringBuilder();
		int idx = matrixPanel.getSelectedIndex();
		if (idx >= 0) sb.append("Row ").append(idx + 1).append('\n');
		sb.append('{').append(selectedItemset.getLabel()).append("}\n");
		sb.append("Size: ").append(selectedItemset.size());
		for (Map.Entry<String, String> en : selectedItemset.getMeasures().entrySet()) {
			sb.append('\n').append(en.getKey()).append(": ").append(en.getValue());
		}
		selectionArea.setText(sb.toString());
		selectionArea.setCaretPosition(0);
		copyButton.setEnabled(true);
	}

	private void copySelection() {
		if (matrixPanel.copySelectionToClipboard()) {
			statusLabel.setText("Copied: " + ItemsetItemMatrixPanel.toSpmfLine(selectedItemset));
		} else {
			statusLabel.setText("Nothing selected to copy.");
		}
	}

	private void showShortcutsDialog() {
		String html = "<html><table cellpadding='2'>"
				+ "<tr><td><b>Ctrl+O</b></td><td>Open file</td><td>&nbsp;&nbsp;<b>F5</b></td><td>Reload file</td></tr>"
				+ "<tr><td><b>Ctrl+F</b></td><td>Focus item filter</td><td>&nbsp;&nbsp;<b>Ctrl+P</b></td><td>Export view as PNG</td></tr>"
				+ "<tr><td><b>Ctrl+C</b></td><td>Copy selected pattern (matrix focused)</td><td>&nbsp;&nbsp;<b>Ctrl+Shift+C</b></td><td>Copy selected pattern</td></tr>"
				+ "<tr><td><b>Ctrl + / Ctrl -</b></td><td>Zoom in / out</td><td>&nbsp;&nbsp;<b>Ctrl 0</b></td><td>Reset zoom</td></tr>"
				+ "<tr><td><b>Ctrl+Shift + / -</b></td><td>Font size</td><td>&nbsp;&nbsp;<b>Ctrl+Shift 0</b></td><td>Reset font size</td></tr>"
				+ "<tr><td><b>\u2191 \u2193</b></td><td>Select previous / next row</td><td>&nbsp;&nbsp;<b>PgUp PgDn</b></td><td>Move selection by a page</td></tr>"
				+ "<tr><td><b>Home / End</b></td><td>First / last row</td><td>&nbsp;&nbsp;<b>\u2190 \u2192</b></td><td>Scroll columns</td></tr>"
				+ "<tr><td><b>Esc</b></td><td>Clear selection</td><td>&nbsp;&nbsp;<b>Mouse wheel</b></td><td>Scroll (Shift: horizontal, Ctrl: zoom)</td></tr>"
				+ "<tr><td><b>Drag matrix</b></td><td>Pan</td><td>&nbsp;&nbsp;<b>Drag column border</b></td><td>Resize pattern column</td></tr>"
				+ "</table></html>";
		JOptionPane.showMessageDialog(this, html, "Keyboard Shortcuts", JOptionPane.INFORMATION_MESSAGE);
	}

	private JFileChooser newFileChooser(String title, FileNameExtensionFilter filter) {
		JFileChooser chooser = new JFileChooser(lastDirectory);
		chooser.setDialogTitle(title);
		chooser.setFileFilter(filter);
		return chooser;
	}

	private File suggestedExportFile(String extension) {
		if (loadedFile == null) return null;
		String base = loadedFile.getName().replaceFirst("\\.[^.]+$", "");
		return new File(loadedFile.getParentFile(), base + "." + extension);
	}

	// ---------------------------------------------------------------
	// File loading
	// ---------------------------------------------------------------

	private void chooseAndLoadFile() {
		JFileChooser chooser = newFileChooser("Select Itemsets File",
				new FileNameExtensionFilter("Text files (*.txt)", "txt"));
		if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			lastDirectory = chooser.getCurrentDirectory();
			loadItemsetsFromFile(chooser.getSelectedFile());
		}
	}

	private void loadItemsetsFromFile(File file) {
		statusLabel.setText("Loading " + file.getName() + "...");
		fileInfoLabel.setText("Loading: " + file.getName() + "...");
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

		SwingWorker<ItemsetReader, Void> worker = new SwingWorker<ItemsetReader, Void>() {
			@Override
			protected ItemsetReader doInBackground() throws Exception {
				ItemsetReader r = new ItemsetReader(file.getAbsolutePath());
				r.readItemsets();
				return r;
			}

			@Override
			protected void done() {
				try {
					reader = get();
					loadedFile = file;
					lastDirectory = file.getParentFile();
					allItemsets = new ArrayList<>(reader.getItemsets());
					selectedItemset = null;
					histogramSizeFilter = null;

					suppressEvents = true;
					try {
						filterField.setText("");
						exactMatchCheckbox.setSelected(false);
						minSizeSpinner.setValue(0);
						maxSizeSpinner.setValue(MAX_SIZE);
						measureMinField.setText("");
						measureMaxField.setText("");
						updateSortComboBox();
						updateMeasureFilterCombo();
					} finally {
						suppressEvents = false;
					}
					filterTimer.stop();
					applyFilter();
					updateSelectionPanel();

					setTitle(WINDOW_TITLE + " - " + file.getName());
					String msg = INSTRUCTIONS;
					if (reader.getIgnoredLineCount() > 0) {
						msg = reader.getIgnoredLineCount() + " line(s) could not be parsed and were ignored.";
					}
					statusLabel.setText(msg);
				} catch (Exception ex) {
					Throwable cause = (ex.getCause() != null) ? ex.getCause() : ex;
					fileInfoLabel.setText("Error loading file");
					statusLabel.setText("Error: " + cause.getMessage());
					JOptionPane.showMessageDialog(ItemsetItemMatrixViewer.this,
							"Error loading file:\n" + cause.getMessage(), "Load Error",
							JOptionPane.ERROR_MESSAGE);
				} finally {
					setCursor(Cursor.getDefaultCursor());
				}
			}
		};
		worker.execute();
	}

	private void updateSortComboBox() {
		List<String> orders = (reader != null) ? reader.getSortingOrders()
				: ItemsetReader.getDefaultSortingOrders();
		DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
		for (String o : orders) model.addElement(o);
		sortComboBox.setModel(model);
		sortComboBox.setSelectedItem(ItemsetReader.SORT_SIZE_ASC);
	}

	private void updateMeasureFilterCombo() {
		DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
		model.addElement(NO_MEASURE);
		if (reader != null) {
			for (String m : reader.getAvailableMeasures()) model.addElement(m);
		}
		measureFilterCombo.setModel(model);
		measureFilterCombo.setSelectedIndex(0);
		boolean hasMeasures = model.getSize() > 1;
		measureFilterCombo.setEnabled(hasMeasures);
		measureMinField.setEnabled(hasMeasures);
		measureMaxField.setEnabled(hasMeasures);
	}

	// ---------------------------------------------------------------
	// Export
	// ---------------------------------------------------------------

	private void exportAsPNG(boolean fullMatrix) {
		if (noData()) return;
		if (fullMatrix) {
			Dimension d = matrixPanel.getFullMatrixSize();
			long pixels = (long) d.width * d.height;
			if (pixels > ItemsetItemMatrixPanel.MAX_EXPORT_PIXELS) {
				JOptionPane.showMessageDialog(this, String.format(
						"The full matrix would be %d x %d pixels, which is too large to export.%n"
								+ "Reduce the cell size (View \u2192 Cell Size) or filter the patterns.",
						d.width, d.height), "Image Too Large", JOptionPane.WARNING_MESSAGE);
				return;
			}
			if (pixels > 8_000_000L && JOptionPane.showConfirmDialog(this,
					String.format("The image will be %d x %d pixels. Continue?", d.width, d.height),
					"Large Image", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
				return;
			}
		}
		JFileChooser chooser = newFileChooser(fullMatrix ? "Export Full Matrix as PNG" : "Export View as PNG",
				new FileNameExtensionFilter("PNG images (*.png)", "png"));
		File suggested = suggestedExportFile("png");
		if (suggested != null) chooser.setSelectedFile(suggested);
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
		lastDirectory = chooser.getCurrentDirectory();
		File file = chooser.getSelectedFile();
		if (!file.getName().toLowerCase().endsWith(".png")) {
			file = new File(file.getAbsolutePath() + ".png");
		}
		try {
			if (fullMatrix) matrixPanel.exportFullMatrixAsImage(file);
			else matrixPanel.exportAsImage(file);
			statusLabel.setText("Image exported to " + file.getAbsolutePath());
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "Error exporting: " + ex.getMessage(),
					"Export Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void exportAsCSV() {
		if (noData()) return;
		JFileChooser chooser = newFileChooser("Export as CSV",
				new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
		File suggested = suggestedExportFile("csv");
		if (suggested != null) chooser.setSelectedFile(suggested);
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
		lastDirectory = chooser.getCurrentDirectory();
		File file = chooser.getSelectedFile();
		if (!file.getName().toLowerCase().endsWith(".csv")) {
			file = new File(file.getAbsolutePath() + ".csv");
		}
		try {
			matrixPanel.exportAsCSV(file);
			statusLabel.setText("CSV exported to " + file.getAbsolutePath());
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "Error exporting: " + ex.getMessage(),
					"Export Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private boolean noData() {
		if (filteredItemsets.isEmpty()) {
			JOptionPane.showMessageDialog(this, "No data to export. Load a file first.",
					"No Data", JOptionPane.WARNING_MESSAGE);
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------
	// Filter / sort
	// ---------------------------------------------------------------

	private void scheduleFilter() {
		if (!suppressEvents) filterTimer.restart();
	}

	private void clearFilters() {
		suppressEvents = true;
		try {
			filterField.setText("");
			minSizeSpinner.setValue(0);
			maxSizeSpinner.setValue(MAX_SIZE);
			measureMinField.setText("");
			measureMaxField.setText("");
			if (measureFilterCombo.getItemCount() > 0) measureFilterCombo.setSelectedIndex(0);
		} finally {
			suppressEvents = false;
		}
		histogramSizeFilter = null;
		filterTimer.stop();
		applyFilter();
	}

	/** Parses the item query into lower-case search terms. */
	private static Set<String> parseSearchTerms(String query) {
		Set<String> terms = new HashSet<>();
		if (query == null || query.trim().isEmpty()) return terms;
		for (String token : query.split("[,\\s]+")) {
			String t = token.trim().toLowerCase();
			if (!t.isEmpty()) terms.add(t);
		}
		return terms;
	}

	private static boolean matchesAllTerms(Itemset itemset, Set<String> terms, boolean exact) {
		for (String term : terms) {
			boolean found = false;
			for (String item : itemset.getItems()) {
				String lower = item.toLowerCase();
				if (exact ? lower.equals(term) : lower.contains(term)) {
					found = true;
					break;
				}
			}
			if (!found) return false;
		}
		return true;
	}

	/** Parses a numeric field; marks it red if non-empty but invalid. */
	private static Double parseNumberField(JTextField field) {
		String text = field.getText().trim();
		if (text.isEmpty()) {
			field.setBackground(UIManager.getColor("TextField.background"));
			return null;
		}
		try {
			Double v = Double.valueOf(text);
			field.setBackground(UIManager.getColor("TextField.background"));
			return v;
		} catch (NumberFormatException e) {
			field.setBackground(COLOR_INVALID_FIELD);
			return null;
		}
	}

	private void applyFilter() {
		if (suppressEvents) return;

		Set<String> terms = parseSearchTerms(filterField.getText());
		boolean exact = exactMatchCheckbox.isSelected();
		int minSize = (Integer) minSizeSpinner.getValue();
		int maxSize = (Integer) maxSizeSpinner.getValue();
		String measure = (String) measureFilterCombo.getSelectedItem();
		Double mMin = parseNumberField(measureMinField);
		Double mMax = parseNumberField(measureMaxField);
		boolean measureActive = measure != null && !NO_MEASURE.equals(measure)
				&& (mMin != null || mMax != null);

		// "base" = patterns passing every filter except the histogram size filter,
		// so the histogram keeps showing the other sizes when one size is selected.
		List<Itemset> base = new ArrayList<>();
		filteredItemsets = new ArrayList<>();
		for (Itemset it : allItemsets) {
			int sz = it.size();
			if (sz < minSize || sz > maxSize) continue;
			if (!terms.isEmpty() && !matchesAllTerms(it, terms, exact)) continue;
			if (measureActive) {
				Double v = it.getNumericMeasure(measure);
				if (v == null || (mMin != null && v < mMin) || (mMax != null && v > mMax)) continue;
			}
			base.add(it);
			if (histogramSizeFilter == null || sz == histogramSizeFilter) {
				filteredItemsets.add(it);
			}
		}

		boolean anyFilter = !terms.isEmpty() || minSize > 0 || maxSize < MAX_SIZE
				|| measureActive || histogramSizeFilter != null;

		applySortAndRefresh();
		histogramPanel.updateData(base, histogramSizeFilter);

		if (anyFilter) {
			filterResultLabel.setText(filteredItemsets.size() + " of " + allItemsets.size());
			fileInfoLabel.setText(String.format("%s  \u2014  showing %d of %d patterns",
					loadedFile != null ? loadedFile.getName() : "", filteredItemsets.size(),
					allItemsets.size()));
		} else {
			filterResultLabel.setText(" ");
			if (loadedFile != null) {
				fileInfoLabel.setText(String.format("%s  \u2014  %d patterns loaded",
						loadedFile.getName(), allItemsets.size()));
			}
		}
		updateStatusRight();
	}

	private void applySortAndRefresh() {
		ItemsetReader.sort(filteredItemsets, (String) sortComboBox.getSelectedItem());
		matrixPanel.setItemsets(filteredItemsets, selectedItemset,
				highlightSubsetsCheckbox.isSelected(), highlightSupersetsCheckbox.isSelected());
		updateSelectionPanel();
	}

	private void updateHighlighting() {
		matrixPanel.updateSelectionState(selectedItemset,
				highlightSubsetsCheckbox.isSelected(), highlightSupersetsCheckbox.isSelected());
	}

	private void onItemsetSelected(Itemset itemset) {
		selectedItemset = itemset;
		updateSelectionPanel();
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
//			try {
//				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//			} catch (Exception ignored) {
//				// keep default look and feel
//			}
			ItemsetItemMatrixViewer viewer = (args.length > 0 && args[0] != null && !args[0].isEmpty())
					? new ItemsetItemMatrixViewer(args[0], true)
					: new ItemsetItemMatrixViewer(true);
			viewer.setVisible(true);
		});
	}
}