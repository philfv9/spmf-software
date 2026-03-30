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
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;

/**
 * A panel that displays an item-pattern matrix (rows = itemsets, columns = items).
 * Each cell indicates whether an item is present in a given itemset.
 *
 * <p>
 * Measure values (e.g. support, confidence) are displayed as frozen columns
 * between the row labels and the item matrix.
 *
 * <p>
 * Column headers use a consistent style: horizontal text when it fits,
 * rotated vertically when the label is too wide for the column.
 *
 * @author Philippe Fournier-Viger, 2024
 */
public class ItemsetItemMatrixPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	// ---------------------------------------------------------------
	// Layout constants
	// ---------------------------------------------------------------

	/** Default cell size in pixels */
	private static final int DEFAULT_CELL_SIZE = 30;
	/** Minimum cell size when zooming out */
	private static final int MIN_CELL_SIZE = 14;
	/** Maximum cell size when zooming in */
	private static final int MAX_CELL_SIZE = 60;
	/** Height reserved for column headers at the top */
	private static final int HEADER_HEIGHT = 100;
	/** Extra banner height above the header for stats info */
	private static final int BANNER_HEIGHT = 22;
	/** Total top area = banner + header */
	private static final int TOP_HEIGHT = BANNER_HEIGHT + HEADER_HEIGHT;
	/** Width reserved for row labels on the left */
	private static final int ROW_LABEL_WIDTH = 200;
	/** Width reserved for the row-number column */
	private static final int ROW_NUMBER_WIDTH = 40;
	/** Minimum width per measure column */
	private static final int MIN_MEASURE_COL_WIDTH = 60;
	/** Padding added to computed measure column width */
	private static final int MEASURE_COL_PADDING = 16;
	/** Scroll speed in pixels per mouse-wheel notch */
	private static final int SCROLL_SPEED = 16;
	/** Maximum characters shown in a row label before truncation */
	private static final int MAX_ROW_LABEL_LENGTH = 24;
	/** Threshold: if a header label is wider than this fraction of the column, rotate it */
	private static final int MAX_HORIZONTAL_CHARS = 6;

	// ---------------------------------------------------------------
	// Font size configuration
	// ---------------------------------------------------------------

	/** Minimum configurable font size */
	public static final int MIN_FONT_SIZE = 8;
	/** Maximum configurable font size */
	public static final int MAX_FONT_SIZE = 24;
	/** Default font size */
	private static final int DEFAULT_FONT_SIZE = 11;

	// ---------------------------------------------------------------
	// Fonts (mutable — rebuilt when font size changes)
	// ---------------------------------------------------------------

	private int fontSize = DEFAULT_FONT_SIZE;
	private Font fontHeader;
	private Font fontRow;
	private Font fontRowNum;
	private Font fontCell;
	private Font fontBanner;
	private Font fontEmpty;
	private Font fontMeasure;

	// ---------------------------------------------------------------
	// Colors
	// ---------------------------------------------------------------

	static final Color COLOR_CELL_PRESENT = new Color(46, 134, 193);
	static final Color COLOR_CELL_ABSENT = new Color(240, 240, 240);
	static final Color COLOR_CELL_SELECTED = new Color(231, 76, 60);
	static final Color COLOR_CELL_SUBSET = new Color(46, 204, 113);
	static final Color COLOR_CELL_SUPERSET = new Color(241, 196, 15);
	static final Color COLOR_CELL_HOVER = new Color(52, 152, 219);
	private static final Color COLOR_COLUMN_HIGHLIGHT = new Color(52, 152, 219, 25);
	private static final Color COLOR_ROW_HIGHLIGHT = new Color(52, 152, 219, 15);
	private static final Color COLOR_GRID = new Color(210, 210, 210);
	private static final Color COLOR_HEADER_BG = new Color(52, 73, 94);
	private static final Color COLOR_HEADER_HOVER_BG = new Color(70, 95, 120);
	private static final Color COLOR_BANNER_BG = new Color(44, 62, 80);
	private static final Color COLOR_ROW_LABEL_BG = new Color(236, 240, 241);
	private static final Color COLOR_ROW_ALT_BG = new Color(246, 248, 250);
	private static final Color COLOR_ROW_SELECTED_BG = new Color(255, 235, 230);
	private static final Color COLOR_ROW_HOVER_BG = new Color(230, 240, 250);
	private static final Color COLOR_ROW_NUM_BG = new Color(225, 228, 232);
	private static final Color COLOR_MEASURE_BG = new Color(248, 249, 250);
	private static final Color COLOR_MEASURE_ALT_BG = new Color(240, 242, 244);
	private static final Color COLOR_TEXT_DARK = new Color(44, 62, 80);
	private static final Color COLOR_TEXT_LIGHT = Color.WHITE;
	private static final Color COLOR_TEXT_MUTED = new Color(120, 130, 140);
	private static final Color COLOR_TEXT_MEASURE = new Color(50, 60, 70);
	private static final Color COLOR_BACKGROUND = Color.WHITE;
	private static final Color COLOR_BANNER_TEXT = new Color(180, 195, 210);

	// ---------------------------------------------------------------
	// Strokes
	// ---------------------------------------------------------------

	private static final Stroke STROKE_GRID = new BasicStroke(1f);
	private static final Stroke STROKE_SEPARATOR = new BasicStroke(2f);

	// ---------------------------------------------------------------
	// Data
	// ---------------------------------------------------------------

	/** The list of itemsets currently displayed (rows) */
	private List<Itemset> itemsets;
	/** The sorted list of all distinct items across all itemsets (columns) */
	private List<String> allItems;
	/** Count of how many itemsets contain each item (keyed by item name) */
	private Map<String, Integer> itemFrequency;
	/** Ordered list of measure names to display as columns */
	private List<String> measureNames;
	/** Computed width for each measure column (indexed same as measureNames) */
	private List<Integer> measureColWidths;
	/** Total width of all measure columns combined */
	private int totalMeasureWidth;
	/** Currently selected itemset (may be null) */
	private Itemset selectedItemset;
	/** Index of the selected itemset in the list, or -1 */
	private int selectedIndex = -1;
	/** Itemset the mouse is currently hovering over (may be null) */
	private Itemset hoveredItemset;
	/** Item-column the mouse is currently hovering over, or -1 */
	private int hoveredColumn = -1;
	/** Whether to highlight subsets of the selected itemset */
	private boolean highlightSubsets;
	/** Whether to highlight supersets of the selected itemset */
	private boolean highlightSupersets;
	/** Whether to show check marks inside cells */
	private boolean showCheckMarks = true;
	/** Whether to show row numbers */
	private boolean showRowNumbers = true;
	/** Whether to show measure columns */
	private boolean showMeasureColumns = true;

	// ---------------------------------------------------------------
	// View state
	// ---------------------------------------------------------------

	/** Current cell size in pixels */
	private int cellSize = DEFAULT_CELL_SIZE;
	/** Horizontal scroll offset (applies to item matrix only) */
	private int offsetX = 0;
	/** Vertical scroll offset */
	private int offsetY = 0;
	/** Last mouse position for drag-panning */
	private Point lastMousePos;
	/** Whether a drag-pan is in progress */
	private boolean isDragging = false;

	// ---------------------------------------------------------------
	// Listeners
	// ---------------------------------------------------------------

	/** Callback for row-selection events */
	private ItemsetSelectionListener selectionListener;
	/** Callback for zoom-level changes */
	private ZoomChangeListener zoomListener;

	/**
	 * Functional interface for listening to itemset selection events.
	 */
	public interface ItemsetSelectionListener {
		void onItemsetSelected(Itemset itemset);
	}

	/**
	 * Functional interface for listening to zoom-level changes.
	 */
	public interface ZoomChangeListener {
		void onZoomChanged(int zoomPercent);
	}

	// ---------------------------------------------------------------
	// Constructor
	// ---------------------------------------------------------------

	/**
	 * Creates an empty matrix panel. Call {@link #setItemsets} to populate it.
	 */
	public ItemsetItemMatrixPanel() {
		setBackground(COLOR_BACKGROUND);
		setPreferredSize(new Dimension(800, 600));
		setFocusable(true);
		rebuildFonts();
		setupMouseListeners();
		setupKeyListeners();
		ToolTipManager.sharedInstance().registerComponent(this);
	}

	// ---------------------------------------------------------------
	// Public API — data
	// ---------------------------------------------------------------

	/**
	 * Replaces the displayed data with a new list of itemsets. Resets scroll position
	 * and selection state.
	 */
	public void setItemsets(List<Itemset> itemsets, Itemset selected,
			boolean highlightSubsets, boolean highlightSupersets) {
		this.selectedItemset = selected;
		this.highlightSubsets = highlightSubsets;
		this.highlightSupersets = highlightSupersets;

		if (itemsets == null || itemsets.isEmpty()) {
			this.itemsets = new ArrayList<>();
			this.allItems = new ArrayList<>();
			this.itemFrequency = new HashMap<>();
			this.measureNames = new ArrayList<>();
			this.measureColWidths = new ArrayList<>();
			this.totalMeasureWidth = 0;
			this.selectedIndex = -1;
		} else {
			this.itemsets = new ArrayList<>(itemsets);
			extractAllItems();
			computeItemFrequency();
			extractMeasureNames();
			computeMeasureColumnWidths();
			this.selectedIndex = (selected != null) ? this.itemsets.indexOf(selected) : -1;
		}

		offsetX = 0;
		offsetY = 0;

		if (selected != null && selectedIndex >= 0) {
			scrollToRow(selectedIndex);
		}
		repaint();
	}

	/**
	 * Updates only the selection and highlighting state without resetting scroll
	 * position or changing data.
	 */
	public void updateSelectionState(Itemset selected, boolean highlightSubsets,
			boolean highlightSupersets) {
		this.selectedItemset = selected;
		this.highlightSubsets = highlightSubsets;
		this.highlightSupersets = highlightSupersets;
		this.selectedIndex = (selected != null && itemsets != null) ? itemsets.indexOf(selected) : -1;
		repaint();
	}

	public void setItemsetSelectionListener(ItemsetSelectionListener listener) {
		this.selectionListener = listener;
	}

	public void setZoomChangeListener(ZoomChangeListener listener) {
		this.zoomListener = listener;
	}

	// ---------------------------------------------------------------
	// Public API — display configuration
	// ---------------------------------------------------------------

	public int getFontSize() {
		return fontSize;
	}

	public void setFontSize(int size) {
		int clamped = Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, size));
		if (clamped != fontSize) {
			fontSize = clamped;
			rebuildFonts();
			computeMeasureColumnWidths();
			repaint();
		}
	}

	public boolean isShowCheckMarks() {
		return showCheckMarks;
	}

	public void setShowCheckMarks(boolean show) {
		if (show != showCheckMarks) {
			showCheckMarks = show;
			repaint();
		}
	}

	public boolean isShowRowNumbers() {
		return showRowNumbers;
	}

	public void setShowRowNumbers(boolean show) {
		if (show != showRowNumbers) {
			showRowNumbers = show;
			repaint();
		}
	}

	public boolean isShowMeasureColumns() {
		return showMeasureColumns;
	}

	public void setShowMeasureColumns(boolean show) {
		if (show != showMeasureColumns) {
			showMeasureColumns = show;
			clampOffsets();
			repaint();
		}
	}

	public int getZoomPercent() {
		return Math.round(100f * cellSize / DEFAULT_CELL_SIZE);
	}

	public void setCellSize(int size) {
		int clamped = Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, size));
		if (clamped != cellSize) {
			cellSize = clamped;
			clampOffsets();
			fireZoomChanged();
			repaint();
		}
	}

	public int getRowCount() {
		return (itemsets != null) ? itemsets.size() : 0;
	}

	public int getColumnCount() {
		return (allItems != null) ? allItems.size() : 0;
	}

	public int getFirstVisibleRow() {
		if (itemsets == null || itemsets.isEmpty())
			return -1;
		return Math.min(Math.max(0, -offsetY / cellSize), itemsets.size() - 1);
	}

	public int getLastVisibleRow() {
		if (itemsets == null || itemsets.isEmpty())
			return -1;
		int viewHeight = getHeight() - TOP_HEIGHT;
		int row = (-offsetY + viewHeight) / cellSize;
		return Math.min(row, itemsets.size() - 1);
	}

	// ---------------------------------------------------------------
	// Public API — export
	// ---------------------------------------------------------------

	/**
	 * Exports the current view to a PNG image file.
	 */
	public void exportAsImage(File file) throws IOException {
		int w = Math.max(getWidth(), 800);
		int h = Math.max(getHeight(), 600);
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = image.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		paint(g2);
		g2.dispose();
		ImageIO.write(image, "png", file);
	}

	/**
	 * Exports the matrix data to a CSV file.
	 */
	public void exportAsCSV(File file) throws IOException {
		if (itemsets == null || itemsets.isEmpty())
			return;

		List<String> allMeasures = (measureNames != null) ? measureNames : new ArrayList<>();

		try (PrintWriter pw = new PrintWriter(file)) {
			StringBuilder header = new StringBuilder();
			header.append("Row,Pattern,Size");
			for (String m : allMeasures)
				header.append(",").append(escapeCSV(m));
			for (String item : allItems)
				header.append(",").append(escapeCSV(item));
			pw.println(header);

			for (int i = 0; i < itemsets.size(); i++) {
				Itemset it = itemsets.get(i);
				Set<String> items = new HashSet<>(it.getItems());
				StringBuilder row = new StringBuilder();
				row.append(i + 1);
				row.append(",\"").append(String.join(", ", it.getItems())).append("\"");
				row.append(",").append(it.getItems().size());
				for (String m : allMeasures) {
					row.append(",").append(escapeCSV(it.getMeasures().getOrDefault(m, "")));
				}
				for (String item : allItems) {
					row.append(",").append(items.contains(item) ? "1" : "0");
				}
				pw.println(row);
			}
		}
	}

	// ---------------------------------------------------------------
	// Font management
	// ---------------------------------------------------------------

	private void rebuildFonts() {
		fontHeader = new Font("SansSerif", Font.BOLD, fontSize);
		fontRow = new Font("SansSerif", Font.PLAIN, fontSize);
		fontRowNum = new Font("SansSerif", Font.PLAIN, Math.max(MIN_FONT_SIZE, fontSize - 1));
		fontCell = new Font("SansSerif", Font.BOLD, Math.max(MIN_FONT_SIZE, fontSize - 1));
		fontBanner = new Font("SansSerif", Font.PLAIN, Math.max(MIN_FONT_SIZE, fontSize - 1));
		fontEmpty = new Font("SansSerif", Font.PLAIN, fontSize + 5);
		fontMeasure = new Font("SansSerif", Font.PLAIN, Math.max(MIN_FONT_SIZE, fontSize - 1));
	}

	// ---------------------------------------------------------------
	// Measure column width computation
	// ---------------------------------------------------------------

	/**
	 * Computes the width of each measure column based on the header name
	 * and the widest value in that column across all itemsets.
	 * Also updates {@link #totalMeasureWidth}.
	 */
	private void computeMeasureColumnWidths() {
		if (measureNames == null || measureNames.isEmpty() || itemsets == null) {
			measureColWidths = new ArrayList<>();
			totalMeasureWidth = 0;
			return;
		}

		// We need FontMetrics, so create a temporary image to get a Graphics context
		BufferedImage tmpImg = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		Graphics2D tmpG = tmpImg.createGraphics();
		tmpG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		FontMetrics fmHeader = tmpG.getFontMetrics(fontHeader);
		FontMetrics fmValue = tmpG.getFontMetrics(fontMeasure);

		measureColWidths = new ArrayList<>(measureNames.size());
		totalMeasureWidth = 0;

		for (int m = 0; m < measureNames.size(); m++) {
			String name = measureNames.get(m);
			// Start with the header text width
			int maxW = fmHeader.stringWidth(name);

			// Check all values for this measure
			for (Itemset itemset : itemsets) {
				String val = itemset.getMeasures().getOrDefault(name, "");
				int valW = fmValue.stringWidth(val);
				if (valW > maxW) {
					maxW = valW;
				}
			}

			// Add padding and enforce minimum
			int colW = Math.max(MIN_MEASURE_COL_WIDTH, maxW + MEASURE_COL_PADDING);
			measureColWidths.add(colW);
			totalMeasureWidth += colW;
		}

		tmpG.dispose();
	}

	/**
	 * Returns the computed width for the measure column at the given index.
	 */
	private int getMeasureColWidth(int measureIndex) {
		if (measureColWidths == null || measureIndex < 0
				|| measureIndex >= measureColWidths.size()) {
			return MIN_MEASURE_COL_WIDTH;
		}
		return measureColWidths.get(measureIndex);
	}

	/**
	 * Returns the left x-coordinate of the measure column at the given index,
	 * relative to the start of the measure area (i.e., relative to ROW_LABEL_WIDTH).
	 */
	private int getMeasureColLeft(int measureIndex) {
		int x = 0;
		for (int i = 0; i < measureIndex && i < measureColWidths.size(); i++) {
			x += measureColWidths.get(i);
		}
		return x;
	}

	// ---------------------------------------------------------------
	// Computed layout helpers
	// ---------------------------------------------------------------

	/** Returns the left edge of the row label text */
	private int getRowLabelLeft() {
		return showRowNumbers ? ROW_NUMBER_WIDTH : 0;
	}

	/** Returns the total width of the measure columns area */
	private int getMeasureAreaWidth() {
		if (!showMeasureColumns || measureNames == null || measureNames.isEmpty())
			return 0;
		return totalMeasureWidth;
	}

	/** Returns the total frozen area width (row labels + measure columns) */
	private int getFrozenWidth() {
		return ROW_LABEL_WIDTH + getMeasureAreaWidth();
	}

	/**
	 * Decides whether a header label should be drawn horizontally or rotated.
	 * Horizontal if it fits within the column width; rotated otherwise.
	 */
	private boolean shouldDrawHorizontal(String label, int colWidth, FontMetrics fm) {
		return fm.stringWidth(label) <= colWidth - 6;
	}

	// ---------------------------------------------------------------
	// Header drawing: unified approach
	// ---------------------------------------------------------------

	/**
	 * Draws a single column header label, either horizontally (centred) or rotated
	 * -90° if it doesn't fit, within the given rectangular area.
	 */
	private void drawHeaderLabel(Graphics2D g2, String label, int x, int colWidth, int headerY,
			int headerH, FontMetrics fm) {
		if (shouldDrawHorizontal(label, colWidth, fm)) {
			int tx = x + (colWidth - fm.stringWidth(label)) / 2;
			int ty = headerY + (headerH + fm.getAscent()) / 2 - 2;
			g2.drawString(label, tx, ty);
		} else {
			AffineTransform original = g2.getTransform();
			int cx = x + colWidth / 2;
			int anchorX = cx - fm.getAscent() / 2;
			int anchorY = headerY + headerH - 6;
			g2.translate(anchorX, anchorY);
			g2.rotate(-Math.PI / 2);
			g2.drawString(label, 0, fm.getAscent());
			g2.setTransform(original);
		}
	}

	// ---------------------------------------------------------------
	// Tooltip
	// ---------------------------------------------------------------

	@Override
	public String getToolTipText(MouseEvent e) {
		int frozenW = getFrozenWidth();

		// Over item matrix cells
		if (e.getX() >= frozenW && e.getY() >= TOP_HEIGHT) {
			int row = getRowAtScreenY(e.getY());
			int col = getColumnAtPoint(e.getPoint());
			if (row >= 0 && row < itemsets.size() && col >= 0 && col < allItems.size()) {
				Itemset itemset = itemsets.get(row);
				String item = allItems.get(col);
				boolean present = itemset.getItems().contains(item);
				return "<html><b>" + item + "</b> \u2014 "
						+ (present ? "\u2713 present" : "\u2717 absent") + "<br><b>Pattern:</b> {"
						+ String.join(", ", itemset.getItems()) + "}</html>";
			}
		}

		// Over measure columns
		if (e.getX() >= ROW_LABEL_WIDTH && e.getX() < frozenW && e.getY() >= TOP_HEIGHT) {
			int row = getRowAtScreenY(e.getY());
			if (row >= 0 && row < itemsets.size()) {
				int mi = getMeasureIndexAtX(e.getX());
				if (mi >= 0 && mi < measureNames.size()) {
					String mName = measureNames.get(mi);
					String mVal = itemsets.get(row).getMeasures().getOrDefault(mName, "");
					return "<html><b>" + mName + ":</b> " + mVal + "</html>";
				}
			}
		}

		// Over row labels
		if (e.getX() < ROW_LABEL_WIDTH && e.getY() >= TOP_HEIGHT) {
			int row = getRowAtScreenY(e.getY());
			if (row >= 0 && row < itemsets.size()) {
				return buildRowTooltip(itemsets.get(row));
			}
		}

		// Over item column headers
		if (e.getY() >= BANNER_HEIGHT && e.getY() < TOP_HEIGHT && e.getX() >= frozenW) {
			int col = getColumnAtPoint(e.getPoint());
			if (col >= 0 && col < allItems.size()) {
				String item = allItems.get(col);
				int freq = itemFrequency.getOrDefault(item, 0);
				return "<html><b>" + item + "</b><br>Appears in " + freq + " of " + itemsets.size()
						+ " patterns</html>";
			}
		}

		// Over measure column headers
		if (e.getY() >= BANNER_HEIGHT && e.getY() < TOP_HEIGHT && e.getX() >= ROW_LABEL_WIDTH
				&& e.getX() < frozenW) {
			int mi = getMeasureIndexAtX(e.getX());
			if (mi >= 0 && mi < measureNames.size()) {
				return "<html><b>" + measureNames.get(mi) + "</b></html>";
			}
		}

		return null;
	}

	/**
	 * Determines which measure column index the given screen x-coordinate falls
	 * into, accounting for variable-width columns.
	 * 
	 * @return the measure index, or -1 if not over a measure column
	 */
	private int getMeasureIndexAtX(int screenX) {
		if (measureNames == null || measureColWidths == null)
			return -1;
		int rel = screenX - ROW_LABEL_WIDTH;
		if (rel < 0)
			return -1;
		int cumulative = 0;
		for (int i = 0; i < measureColWidths.size(); i++) {
			cumulative += measureColWidths.get(i);
			if (rel < cumulative)
				return i;
		}
		return -1;
	}

	// ---------------------------------------------------------------
	// Painting
	// ---------------------------------------------------------------

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		if (itemsets == null || itemsets.isEmpty()) {
			drawEmptyState((Graphics2D) g);
			return;
		}

		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		int frozenW = getFrozenWidth();

		// --- Scrollable item matrix area ---
		Shape oldClip = g2.getClip();
		g2.setClip(frozenW, TOP_HEIGHT, getWidth() - frozenW, getHeight() - TOP_HEIGHT);
		drawGridBackground(g2);
		drawColumnHighlight(g2);
		drawRowHighlight(g2);
		drawCells(g2);
		drawGridLines(g2);
		g2.setClip(oldClip);

		// --- Frozen areas ---
		drawBanner(g2);
		drawItemColumnHeaders(g2);
		drawMeasureColumnHeaders(g2);
		drawRowNumberAndPatternHeaders(g2);
		drawMeasureColumns(g2);
		drawRowLabels(g2);
	}

	// ---------------------------------------------------------------
	// Painting: banner (top strip with stats)
	// ---------------------------------------------------------------

	private void drawBanner(Graphics2D g2) {
		g2.setColor(COLOR_BANNER_BG);
		g2.fillRect(0, 0, getWidth(), BANNER_HEIGHT);

		g2.setFont(fontBanner);
		g2.setColor(COLOR_BANNER_TEXT);
		if (itemsets != null && allItems != null) {
			String info = itemsets.size() + " patterns \u00B7 " + allItems.size() + " distinct items";
			if (measureNames != null && !measureNames.isEmpty()) {
				info += " \u00B7 " + measureNames.size() + " measures";
			}
			FontMetrics fm = g2.getFontMetrics();
			g2.drawString(info, 10, (BANNER_HEIGHT + fm.getAscent()) / 2 - 1);
		}
	}

	// ---------------------------------------------------------------
	// Painting: column headers for #, Pattern, measures, items
	// ---------------------------------------------------------------

	private void drawRowNumberAndPatternHeaders(Graphics2D g2) {
		g2.setColor(COLOR_HEADER_BG);
		g2.fillRect(0, BANNER_HEIGHT, ROW_LABEL_WIDTH, HEADER_HEIGHT);

		g2.setFont(fontHeader);
		g2.setColor(COLOR_TEXT_LIGHT);
		FontMetrics fm = g2.getFontMetrics();

		if (showRowNumbers) {
			drawHeaderLabel(g2, "#", 0, ROW_NUMBER_WIDTH, BANNER_HEIGHT, HEADER_HEIGHT, fm);

			g2.setColor(COLOR_GRID);
			g2.setStroke(STROKE_GRID);
			g2.drawLine(ROW_NUMBER_WIDTH, BANNER_HEIGHT, ROW_NUMBER_WIDTH,
					BANNER_HEIGHT + HEADER_HEIGHT);

			g2.setColor(COLOR_TEXT_LIGHT);
			drawHeaderLabel(g2, "Pattern", ROW_NUMBER_WIDTH, ROW_LABEL_WIDTH - ROW_NUMBER_WIDTH,
					BANNER_HEIGHT, HEADER_HEIGHT, fm);
		} else {
			drawHeaderLabel(g2, "Pattern", 0, ROW_LABEL_WIDTH, BANNER_HEIGHT, HEADER_HEIGHT, fm);
		}

		g2.setColor(COLOR_GRID);
		g2.setStroke(STROKE_SEPARATOR);
		g2.drawLine(ROW_LABEL_WIDTH, BANNER_HEIGHT, ROW_LABEL_WIDTH, TOP_HEIGHT);
		g2.drawLine(0, TOP_HEIGHT, ROW_LABEL_WIDTH, TOP_HEIGHT);
	}

	private void drawMeasureColumnHeaders(Graphics2D g2) {
		if (!showMeasureColumns || measureNames == null || measureNames.isEmpty())
			return;

		int measureLeft = ROW_LABEL_WIDTH;
		int measureAreaW = getMeasureAreaWidth();

		g2.setColor(COLOR_HEADER_BG);
		g2.fillRect(measureLeft, BANNER_HEIGHT, measureAreaW, HEADER_HEIGHT);

		g2.setFont(fontHeader);
		g2.setColor(COLOR_TEXT_LIGHT);
		FontMetrics fm = g2.getFontMetrics();

		for (int m = 0; m < measureNames.size(); m++) {
			int x = measureLeft + getMeasureColLeft(m);
			int colW = getMeasureColWidth(m);

			drawHeaderLabel(g2, measureNames.get(m), x, colW, BANNER_HEIGHT, HEADER_HEIGHT, fm);

			g2.setColor(COLOR_GRID);
			g2.setStroke(STROKE_GRID);
			g2.drawLine(x + colW, BANNER_HEIGHT, x + colW, TOP_HEIGHT);
			g2.setColor(COLOR_TEXT_LIGHT);
		}

		g2.setColor(COLOR_GRID);
		g2.setStroke(STROKE_SEPARATOR);
		g2.drawLine(measureLeft, TOP_HEIGHT, measureLeft + measureAreaW, TOP_HEIGHT);
	}

	private void drawItemColumnHeaders(Graphics2D g2) {
		int frozenW = getFrozenWidth();

		g2.setColor(COLOR_HEADER_BG);
		g2.fillRect(frozenW, BANNER_HEIGHT, getWidth() - frozenW, HEADER_HEIGHT);

		g2.setFont(fontHeader);
		FontMetrics fm = g2.getFontMetrics();

		int startCol = Math.max(0, -offsetX / cellSize);
		int endCol = Math.min(allItems.size(), (-offsetX + getWidth() - frozenW) / cellSize + 1);

		Shape oldClip = g2.getClip();
		g2.setClip(frozenW, BANNER_HEIGHT, getWidth() - frozenW, HEADER_HEIGHT);

		for (int col = startCol; col < endCol; col++) {
			int x = frozenW + col * cellSize + offsetX;
			if (x + cellSize < frozenW || x > getWidth())
				continue;

			if (col == hoveredColumn) {
				g2.setColor(COLOR_HEADER_HOVER_BG);
				g2.fillRect(x, BANNER_HEIGHT, cellSize, HEADER_HEIGHT);
			}

			g2.setColor(COLOR_TEXT_LIGHT);
			drawHeaderLabel(g2, allItems.get(col), x, cellSize, BANNER_HEIGHT, HEADER_HEIGHT, fm);
		}

		g2.setClip(oldClip);

		g2.setColor(COLOR_GRID);
		g2.setStroke(STROKE_SEPARATOR);
		g2.drawLine(frozenW, TOP_HEIGHT, getWidth(), TOP_HEIGHT);
	}

	// ---------------------------------------------------------------
	// Painting: grid, cells, highlights
	// ---------------------------------------------------------------

	private void drawEmptyState(Graphics2D g2) {
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setColor(Color.GRAY);
		g2.setFont(fontEmpty);
		String msg = "No itemsets to display. Use File \u2192 Open to load a file.";
		FontMetrics fm = g2.getFontMetrics();
		g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
	}

	private void drawGridBackground(Graphics2D g2) {
		int frozenW = getFrozenWidth();
		g2.setColor(COLOR_BACKGROUND);
		g2.fillRect(frozenW, TOP_HEIGHT, getWidth() - frozenW, getHeight() - TOP_HEIGHT);
	}

	private void drawColumnHighlight(Graphics2D g2) {
		if (hoveredColumn < 0 || hoveredColumn >= allItems.size())
			return;
		int frozenW = getFrozenWidth();
		int x = frozenW + hoveredColumn * cellSize + offsetX;
		g2.setColor(COLOR_COLUMN_HIGHLIGHT);
		g2.fillRect(x, TOP_HEIGHT, cellSize, getHeight() - TOP_HEIGHT);
	}

	private void drawRowHighlight(Graphics2D g2) {
		if (hoveredItemset == null || itemsets == null)
			return;
		int idx = itemsets.indexOf(hoveredItemset);
		if (idx < 0)
			return;
		int frozenW = getFrozenWidth();
		int y = TOP_HEIGHT + idx * cellSize + offsetY;
		g2.setColor(COLOR_ROW_HIGHLIGHT);
		g2.fillRect(frozenW, y, getWidth() - frozenW, cellSize);
	}

	private void drawCells(Graphics2D g2) {
		int frozenW = getFrozenWidth();
		int startCol = Math.max(0, -offsetX / cellSize - 1);
		int endCol = Math.min(allItems.size(), (-offsetX + getWidth() - frozenW) / cellSize + 2);
		int startRow = Math.max(0, -offsetY / cellSize - 1);
		int endRow = Math.min(itemsets.size(),
				(-offsetY + getHeight() - TOP_HEIGHT) / cellSize + 2);

		for (int row = startRow; row < endRow; row++) {
			Itemset itemset = itemsets.get(row);
			Set<String> items = new HashSet<>(itemset.getItems());

			for (int col = startCol; col < endCol; col++) {
				String item = allItems.get(col);
				boolean present = items.contains(item);

				int x = frozenW + col * cellSize + offsetX;
				int y = TOP_HEIGHT + row * cellSize + offsetY;

				g2.setColor(determineCellColor(present, itemset));
				g2.fillRect(x, y, cellSize, cellSize);

				if (present && showCheckMarks && cellSize >= MIN_CELL_SIZE + 4) {
					g2.setColor(COLOR_TEXT_LIGHT);
					g2.setFont(fontCell);
					String mark = "\u2713";
					FontMetrics fm = g2.getFontMetrics();
					int tx = x + (cellSize - fm.stringWidth(mark)) / 2;
					int ty = y + (cellSize + fm.getAscent()) / 2 - 2;
					g2.drawString(mark, tx, ty);
				}
			}
		}
	}

	private Color determineCellColor(boolean present, Itemset itemset) {
		if (!present)
			return COLOR_CELL_ABSENT;
		if (itemset.equals(selectedItemset))
			return COLOR_CELL_SELECTED;
		if (itemset.equals(hoveredItemset))
			return COLOR_CELL_HOVER;
		if (selectedItemset != null) {
			if (highlightSubsets && isSubset(itemset, selectedItemset))
				return COLOR_CELL_SUBSET;
			if (highlightSupersets && isSubset(selectedItemset, itemset))
				return COLOR_CELL_SUPERSET;
		}
		return COLOR_CELL_PRESENT;
	}

	private void drawGridLines(Graphics2D g2) {
		int frozenW = getFrozenWidth();
		g2.setColor(COLOR_GRID);
		g2.setStroke(STROKE_GRID);

		int startCol = Math.max(0, -offsetX / cellSize);
		int endCol = Math.min(allItems.size(), (-offsetX + getWidth() - frozenW) / cellSize + 2);
		int startRow = Math.max(0, -offsetY / cellSize);
		int endRow = Math.min(itemsets.size(),
				(-offsetY + getHeight() - TOP_HEIGHT) / cellSize + 2);

		for (int col = startCol; col <= endCol; col++) {
			int x = frozenW + col * cellSize + offsetX;
			g2.drawLine(x, TOP_HEIGHT, x, getHeight());
		}
		for (int row = startRow; row <= endRow; row++) {
			int y = TOP_HEIGHT + row * cellSize + offsetY;
			g2.drawLine(frozenW, y, getWidth(), y);
		}
	}

	// ---------------------------------------------------------------
	// Painting: measure value columns
	// ---------------------------------------------------------------

	private void drawMeasureColumns(Graphics2D g2) {
		if (!showMeasureColumns || measureNames == null || measureNames.isEmpty())
			return;

		int measureLeft = ROW_LABEL_WIDTH;
		int startRow = Math.max(0, -offsetY / cellSize);
		int endRow = Math.min(itemsets.size(),
				(-offsetY + getHeight() - TOP_HEIGHT) / cellSize + 1);

		for (int row = startRow; row < endRow; row++) {
			int y = TOP_HEIGHT + row * cellSize + offsetY;
			if (y + cellSize < TOP_HEIGHT || y > getHeight())
				continue;

			Itemset itemset = itemsets.get(row);
			Map<String, String> measures = itemset.getMeasures();
			Color bgColor = getRowBackground(row, itemset);

			for (int m = 0; m < measureNames.size(); m++) {
				int x = measureLeft + getMeasureColLeft(m);
				int colW = getMeasureColWidth(m);
				String mValue = measures.getOrDefault(measureNames.get(m), "");

				// Background
				g2.setColor(bgColor);
				g2.fillRect(x, y, colW, cellSize);

				// Border
				g2.setColor(COLOR_GRID);
				g2.setStroke(STROKE_GRID);
				g2.drawRect(x, y, colW, cellSize);

				// Value text (right-aligned)
				g2.setColor(COLOR_TEXT_MEASURE);
				g2.setFont(fontMeasure);
				FontMetrics fm = g2.getFontMetrics();
				int textY = y + (cellSize + fm.getAscent()) / 2 - 2;
				int textX = x + colW - fm.stringWidth(mValue) - 6;
				g2.drawString(mValue, textX, textY);
			}
		}

		// Right separator
		int frozenW = getFrozenWidth();
		g2.setColor(COLOR_GRID);
		g2.setStroke(STROKE_SEPARATOR);
		g2.drawLine(frozenW, TOP_HEIGHT, frozenW, getHeight());
	}

	// ---------------------------------------------------------------
	// Painting: row labels
	// ---------------------------------------------------------------

	private void drawRowLabels(Graphics2D g2) {
		int startRow = Math.max(0, -offsetY / cellSize);
		int endRow = Math.min(itemsets.size(),
				(-offsetY + getHeight() - TOP_HEIGHT) / cellSize + 1);

		int labelLeft = getRowLabelLeft();

		for (int row = startRow; row < endRow; row++) {
			int y = TOP_HEIGHT + row * cellSize + offsetY;
			if (y + cellSize < TOP_HEIGHT || y > getHeight())
				continue;

			Itemset itemset = itemsets.get(row);
			Color bgColor = getRowBackground(row, itemset);

			if (showRowNumbers) {
				g2.setColor(COLOR_ROW_NUM_BG);
				g2.fillRect(0, y, ROW_NUMBER_WIDTH, cellSize);

				g2.setColor(bgColor);
				g2.fillRect(ROW_NUMBER_WIDTH, y, ROW_LABEL_WIDTH - ROW_NUMBER_WIDTH, cellSize);

				g2.setFont(fontRowNum);
				g2.setColor(COLOR_TEXT_MUTED);
				FontMetrics fmNum = g2.getFontMetrics();
				String numStr = String.valueOf(row + 1);
				int numX = ROW_NUMBER_WIDTH - fmNum.stringWidth(numStr) - 5;
				int textY = y + (cellSize + fmNum.getAscent()) / 2 - 2;
				g2.drawString(numStr, numX, textY);

				g2.setColor(COLOR_GRID);
				g2.drawLine(ROW_NUMBER_WIDTH, y, ROW_NUMBER_WIDTH, y + cellSize);
			} else {
				g2.setColor(bgColor);
				g2.fillRect(0, y, ROW_LABEL_WIDTH, cellSize);
			}

			g2.setColor(COLOR_TEXT_DARK);
			g2.setFont(fontRow);
			FontMetrics fmRow = g2.getFontMetrics();
			int textY = y + (cellSize + fmRow.getAscent()) / 2 - 2;
			g2.drawString(truncateLabel(itemset), labelLeft + 6, textY);
		}

		g2.setColor(COLOR_GRID);
		g2.setStroke(STROKE_SEPARATOR);
		g2.drawLine(ROW_LABEL_WIDTH, TOP_HEIGHT, ROW_LABEL_WIDTH, getHeight());
	}

	private Color getRowBackground(int row, Itemset itemset) {
		if (row == selectedIndex)
			return COLOR_ROW_SELECTED_BG;
		if (itemset.equals(hoveredItemset))
			return COLOR_ROW_HOVER_BG;
		if (row % 2 == 1)
			return COLOR_ROW_ALT_BG;
		return COLOR_ROW_LABEL_BG;
	}

	// ---------------------------------------------------------------
	// Data helpers
	// ---------------------------------------------------------------

	private void extractAllItems() {
		Set<String> itemSet = new TreeSet<>();
		for (Itemset itemset : itemsets)
			itemSet.addAll(itemset.getItems());
		allItems = new ArrayList<>(itemSet);
	}

	private void computeItemFrequency() {
		itemFrequency = new HashMap<>();
		for (Itemset itemset : itemsets) {
			for (String item : itemset.getItems()) {
				itemFrequency.merge(item, 1, Integer::sum);
			}
		}
	}

	private void extractMeasureNames() {
		Set<String> names = new java.util.LinkedHashSet<>();
		for (Itemset itemset : itemsets)
			names.addAll(itemset.getMeasures().keySet());
		measureNames = new ArrayList<>(names);
	}

	private String truncateLabel(Itemset itemset) {
		String label = String.join(", ", itemset.getItems());
		if (label.length() > MAX_ROW_LABEL_LENGTH) {
			return label.substring(0, MAX_ROW_LABEL_LENGTH - 3) + "\u2026";
		}
		return label;
	}

	private boolean isSubset(Itemset smaller, Itemset larger) {
		if (smaller.getItems().size() >= larger.getItems().size())
			return false;
		Set<String> largerSet = new HashSet<>(larger.getItems());
		for (String item : smaller.getItems()) {
			if (!largerSet.contains(item))
				return false;
		}
		return true;
	}

	private String buildRowTooltip(Itemset itemset) {
		StringBuilder sb = new StringBuilder("<html>");
		sb.append("<b>Pattern:</b> {").append(String.join(", ", itemset.getItems())).append("}");
		sb.append("<br><b>Size:</b> ").append(itemset.getItems().size());
		Map<String, String> measures = itemset.getMeasures();
		if (measures != null && !measures.isEmpty()) {
			for (Map.Entry<String, String> entry : measures.entrySet()) {
				sb.append("<br><b>").append(entry.getKey()).append(":</b> ")
						.append(entry.getValue());
			}
		}
		sb.append("</html>");
		return sb.toString();
	}

	private String escapeCSV(String value) {
		if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	// ---------------------------------------------------------------
	// Hit-testing
	// ---------------------------------------------------------------

	private int getRowAtScreenY(int screenY) {
		if (itemsets == null || itemsets.isEmpty())
			return -1;
		if (screenY < TOP_HEIGHT)
			return -1;
		int y = screenY - TOP_HEIGHT - offsetY;
		if (y < 0)
			return -1;
		int row = y / cellSize;
		return (row >= 0 && row < itemsets.size()) ? row : -1;
	}

	private int getColumnAtPoint(Point p) {
		if (allItems == null || allItems.isEmpty())
			return -1;
		int frozenW = getFrozenWidth();
		if (p.x < frozenW)
			return -1;
		int x = p.x - frozenW - offsetX;
		if (x < 0)
			return -1;
		int col = x / cellSize;
		return (col >= 0 && col < allItems.size()) ? col : -1;
	}

	// ---------------------------------------------------------------
	// Scroll / zoom
	// ---------------------------------------------------------------

	private void clampOffsets() {
		if (itemsets == null || allItems == null)
			return;

		int frozenW = getFrozenWidth();
		int totalW = allItems.size() * cellSize;
		int totalH = itemsets.size() * cellSize;
		int viewW = getWidth() - frozenW;
		int viewH = getHeight() - TOP_HEIGHT;

		if (totalW <= viewW) {
			offsetX = 0;
		} else {
			offsetX = Math.min(0, Math.max(-(totalW - viewW), offsetX));
		}

		if (totalH <= viewH) {
			offsetY = 0;
		} else {
			offsetY = Math.min(0, Math.max(-(totalH - viewH), offsetY));
		}
	}

	private void scrollToRow(int row) {
		if (row < 0)
			return;
		offsetY = -row * cellSize + (getHeight() - TOP_HEIGHT) / 2;
		clampOffsets();
	}

	private void ensureRowVisible(int row) {
		if (row < 0 || itemsets == null)
			return;
		int rowTop = TOP_HEIGHT + row * cellSize + offsetY;
		int rowBot = rowTop + cellSize;
		if (rowTop >= TOP_HEIGHT && rowBot <= getHeight())
			return;
		if (rowTop < TOP_HEIGHT)
			offsetY += (TOP_HEIGHT - rowTop) + 4;
		else if (rowBot > getHeight())
			offsetY -= (rowBot - getHeight()) + 4;
		clampOffsets();
	}

	private void fireZoomChanged() {
		if (zoomListener != null)
			zoomListener.onZoomChanged(getZoomPercent());
	}

	// ---------------------------------------------------------------
	// Mouse handling
	// ---------------------------------------------------------------

	private void setupMouseListeners() {
		MouseAdapter mouseHandler = new MouseAdapter() {

			@Override
			public void mousePressed(MouseEvent e) {
				requestFocusInWindow();
				lastMousePos = e.getPoint();
				int frozenW = getFrozenWidth();
				if (e.getX() >= frozenW && e.getY() >= TOP_HEIGHT) {
					isDragging = true;
					setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (isDragging) {
					isDragging = false;
					setCursor(Cursor.getDefaultCursor());
				}
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getY() >= TOP_HEIGHT) {
					int row = getRowAtScreenY(e.getY());
					if (row >= 0 && row < itemsets.size()) {
						if (row == selectedIndex)
							clearSelection();
						else
							selectRowNoScroll(row);
					}
				}
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				boolean needsRepaint = false;

				Itemset newHovered = null;
				if (e.getY() >= TOP_HEIGHT) {
					int row = getRowAtScreenY(e.getY());
					if (row >= 0 && row < itemsets.size())
						newHovered = itemsets.get(row);
				}
				if (!Objects.equals(newHovered, hoveredItemset)) {
					hoveredItemset = newHovered;
					needsRepaint = true;
				}

				int newHoveredCol = -1;
				if (e.getX() >= getFrozenWidth()) {
					newHoveredCol = getColumnAtPoint(e.getPoint());
				}
				if (newHoveredCol != hoveredColumn) {
					hoveredColumn = newHoveredCol;
					needsRepaint = true;
				}

				if (needsRepaint)
					repaint();

				boolean overRowLabels = e.getX() < ROW_LABEL_WIDTH && e.getY() >= TOP_HEIGHT
						&& newHovered != null;
				setCursor(Cursor.getPredefinedCursor(
						overRowLabels ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (isDragging && lastMousePos != null) {
					offsetX += e.getX() - lastMousePos.x;
					offsetY += e.getY() - lastMousePos.y;
					clampOffsets();
					lastMousePos = e.getPoint();
					repaint();
				}
			}
		};

		addMouseListener(mouseHandler);
		addMouseMotionListener(mouseHandler);

		addMouseWheelListener(e -> {
			if (e.isControlDown()) {
				int delta = -e.getWheelRotation();
				int oldSize = cellSize;
				cellSize = Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, cellSize + delta * 2));
				if (oldSize != cellSize) {
					int frozenW = getFrozenWidth();
					double scale = (double) cellSize / oldSize;
					int mx = e.getX() - frozenW;
					int my = e.getY() - TOP_HEIGHT;
					offsetX = (int) ((offsetX - mx) * scale + mx);
					offsetY = (int) ((offsetY - my) * scale + my);
					clampOffsets();
					fireZoomChanged();
					repaint();
				}
			} else if (e.isShiftDown()) {
				offsetX -= e.getWheelRotation() * SCROLL_SPEED;
				clampOffsets();
				repaint();
			} else {
				offsetY -= e.getWheelRotation() * SCROLL_SPEED;
				clampOffsets();
				repaint();
			}
		});
	}

	// ---------------------------------------------------------------
	// Keyboard handling
	// ---------------------------------------------------------------

	private void setupKeyListeners() {
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (itemsets == null || itemsets.isEmpty())
					return;
				switch (e.getKeyCode()) {
				case KeyEvent.VK_UP:
					moveSelection(-1);
					break;
				case KeyEvent.VK_DOWN:
					moveSelection(1);
					break;
				case KeyEvent.VK_PAGE_UP:
					moveSelectionByPage(-1);
					break;
				case KeyEvent.VK_PAGE_DOWN:
					moveSelectionByPage(1);
					break;
				case KeyEvent.VK_HOME:
					selectRowWithScroll(0);
					break;
				case KeyEvent.VK_END:
					selectRowWithScroll(itemsets.size() - 1);
					break;
				case KeyEvent.VK_ESCAPE:
					clearSelection();
					break;
				}
			}
		});
	}

	private void moveSelection(int delta) {
		int newIndex = (selectedIndex < 0) ? (delta > 0 ? 0 : itemsets.size() - 1)
				: selectedIndex + delta;
		if (newIndex >= 0 && newIndex < itemsets.size())
			selectRowWithScroll(newIndex);
	}

	private void moveSelectionByPage(int direction) {
		int rowsPerPage = Math.max(1, (getHeight() - TOP_HEIGHT) / cellSize - 1);
		moveSelection(direction * rowsPerPage);
	}

	private void selectRowWithScroll(int index) {
		if (index < 0 || index >= itemsets.size())
			return;
		selectedIndex = index;
		selectedItemset = itemsets.get(index);
		ensureRowVisible(index);
		repaint();
		if (selectionListener != null)
			selectionListener.onItemsetSelected(selectedItemset);
	}

	private void selectRowNoScroll(int index) {
		if (index < 0 || index >= itemsets.size())
			return;
		selectedIndex = index;
		selectedItemset = itemsets.get(index);
		repaint();
		if (selectionListener != null)
			selectionListener.onItemsetSelected(selectedItemset);
	}

	private void clearSelection() {
		selectedIndex = -1;
		selectedItemset = null;
		repaint();
		if (selectionListener != null)
			selectionListener.onItemsetSelected(null);
	}
}