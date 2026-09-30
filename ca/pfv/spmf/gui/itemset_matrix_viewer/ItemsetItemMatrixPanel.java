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
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;

/**
 * A panel that displays an item-pattern matrix (rows = itemsets, columns = items).
 * Each cell indicates whether an item is present in a given itemset.
 *
 * <p>
 * Measure values (e.g. support, confidence) are displayed as frozen columns
 * between the row labels and the item matrix. The width of the row-label
 * column can be changed by dragging its right border.
 *
 * <p>
 * Internally the matrix is stored as one {@link BitSet} per row so that
 * painting, hit-testing and subset/superset computation are cheap even for
 * large result files.
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
	/** Cell size change per zoom step */
	private static final int ZOOM_STEP = 2;
	/** Height reserved for column headers at the top */
	private static final int HEADER_HEIGHT = 100;
	/** Extra banner height above the header for stats info */
	private static final int BANNER_HEIGHT = 22;
	/** Total top area = banner + header */
	private static final int TOP_HEIGHT = BANNER_HEIGHT + HEADER_HEIGHT;
	/** Default width of the row label column */
	private static final int DEFAULT_ROW_LABEL_WIDTH = 200;
	/** Minimum width of the row label column (user resizable) */
	private static final int MIN_ROW_LABEL_WIDTH = 80;
	/** Maximum width of the row label column (user resizable) */
	private static final int MAX_ROW_LABEL_WIDTH = 600;
	/** Width reserved for the row-number column */
	private static final int ROW_NUMBER_WIDTH = 40;
	/** Minimum width per measure column */
	private static final int MIN_MEASURE_COL_WIDTH = 60;
	/** Padding added to computed measure column width */
	private static final int MEASURE_COL_PADDING = 16;
	/** Rows scrolled per mouse-wheel notch */
	private static final int ROWS_PER_WHEEL_NOTCH = 3;
	/** Columns scrolled per left/right key press */
	private static final int KEY_SCROLL_COLUMNS = 3;
	/** Half-width (px) of the grab zone for resizing the label column */
	private static final int RESIZE_GRIP = 4;
	/** Smallest cell size at which check marks are drawn */
	private static final int CHECK_MARK_MIN_CELL = MIN_CELL_SIZE + 4;
	private static final String CHECK_MARK = "\u2713";
	private static final String ELLIPSIS = "\u2026";

	// ---------------------------------------------------------------
	// Font size configuration
	// ---------------------------------------------------------------

	public static final int MIN_FONT_SIZE = 8;
	public static final int MAX_FONT_SIZE = 24;
	public static final int DEFAULT_FONT_SIZE = 11;

	// ---------------------------------------------------------------
	// Column ordering
	// ---------------------------------------------------------------

	/** How the item columns are ordered */
	public enum ColumnOrder {
		NAME("Item name"),
		FREQUENCY_DESC("Frequency (most frequent first)"),
		FREQUENCY_ASC("Frequency (least frequent first)");

		private final String label;

		ColumnOrder(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	/** Row relation codes with respect to the selected itemset */
	private static final byte REL_NONE = 0;
	private static final byte REL_SUBSET = 1;
	private static final byte REL_SUPERSET = 2;

	// ---------------------------------------------------------------
	// Fonts (rebuilt when font size changes)
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
	private static final Color COLOR_COLUMN_HIGHLIGHT = new Color(52, 152, 219, 40);
	private static final Color COLOR_ROW_HIGHLIGHT = new Color(52, 152, 219, 25);
	private static final Color COLOR_GRID = new Color(210, 210, 210);
	private static final Color COLOR_HEADER_BG = new Color(52, 73, 94);
	private static final Color COLOR_HEADER_HOVER_BG = new Color(70, 95, 120);
	private static final Color COLOR_BANNER_BG = new Color(44, 62, 80);
	private static final Color COLOR_ROW_LABEL_BG = new Color(236, 240, 241);
	private static final Color COLOR_ROW_ALT_BG = new Color(246, 248, 250);
	private static final Color COLOR_ROW_SELECTED_BG = new Color(255, 235, 230);
	private static final Color COLOR_ROW_HOVER_BG = new Color(230, 240, 250);
	private static final Color COLOR_ROW_SUBSET_BG = new Color(232, 248, 238);
	private static final Color COLOR_ROW_SUPERSET_BG = new Color(253, 247, 224);
	private static final Color COLOR_ROW_NUM_BG = new Color(225, 228, 232);
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
	private List<Itemset> itemsets = new ArrayList<>();
	/** The ordered list of all distinct items (columns) */
	private List<String> allItems = new ArrayList<>();
	/** Item name → column index */
	private Map<String, Integer> itemIndex = new HashMap<>();
	/** Number of itemsets containing each item (indexed by column) */
	private int[] itemFrequency = new int[0];
	/** Presence matrix: one bit set per row, bit = column index */
	private BitSet[] rowBits = new BitSet[0];
	/** Relation of each row to the selected itemset (REL_* codes) */
	private byte[] rowRelation = new byte[0];
	/** Number of rows flagged as subset / superset of the selection */
	private int subsetCount;
	private int supersetCount;
	/** Ordered list of measure names to display as columns */
	private List<String> measureNames = new ArrayList<>();
	/** Computed width for each measure column */
	private int[] measureColWidths = new int[0];
	/** Total width of all measure columns combined */
	private int totalMeasureWidth;
	/** Current column ordering */
	private ColumnOrder columnOrder = ColumnOrder.NAME;

	/** Currently selected itemset (may be null, may be absent from the list) */
	private Itemset selectedItemset;
	/** Index of the selected itemset in the list, or -1 */
	private int selectedIndex = -1;
	/** Row the mouse is currently hovering over, or -1 */
	private int hoveredRow = -1;
	/** Item-column the mouse is currently hovering over, or -1 */
	private int hoveredColumn = -1;
	private boolean highlightSubsets;
	private boolean highlightSupersets;
	private boolean showCheckMarks = true;
	private boolean showRowNumbers = true;
	private boolean showMeasureColumns = true;

	// ---------------------------------------------------------------
	// View state
	// ---------------------------------------------------------------

	private int cellSize = DEFAULT_CELL_SIZE;
	private int rowLabelWidth = DEFAULT_ROW_LABEL_WIDTH;
	/** Horizontal scroll offset (applies to item matrix only), always <= 0 */
	private int offsetX = 0;
	/** Vertical scroll offset, always <= 0 */
	private int offsetY = 0;
	private Point lastMousePos;
	private boolean isDragging = false;
	private boolean isResizingLabels = false;

	// ---------------------------------------------------------------
	// Listeners
	// ---------------------------------------------------------------

	private ItemsetSelectionListener selectionListener;
	private ViewChangeListener viewListener;

	/** Listener for itemset selection events. */
	public interface ItemsetSelectionListener {
		void onItemsetSelected(Itemset itemset);
	}

	/** Listener for view changes (scroll, zoom, resize, data change). */
	public interface ViewChangeListener {
		void onViewChanged();
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
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				clampOffsets();
				repaint();
				fireViewChanged();
			}
		});
		ToolTipManager.sharedInstance().registerComponent(this);
	}

	// ---------------------------------------------------------------
	// Public API — data
	// ---------------------------------------------------------------

	/**
	 * Replaces the displayed data with a new list of itemsets. Resets the scroll
	 * position (and scrolls to the selected itemset if it is in the list).
	 *
	 * @param newItemsets        the itemsets to display (rows)
	 * @param selected           the selected itemset, or null
	 * @param highlightSubsets   whether to highlight subsets of the selection
	 * @param highlightSupersets whether to highlight supersets of the selection
	 */
	public void setItemsets(List<Itemset> newItemsets, Itemset selected,
			boolean highlightSubsets, boolean highlightSupersets) {
		this.highlightSubsets = highlightSubsets;
		this.highlightSupersets = highlightSupersets;
		this.itemsets = (newItemsets != null) ? new ArrayList<>(newItemsets) : new ArrayList<>();

		rebuildColumns();
		extractMeasureNames();
		computeMeasureColumnWidths();

		hoveredRow = -1;
		hoveredColumn = -1;
		applySelection(selected);

		offsetX = 0;
		offsetY = 0;
		if (selectedIndex >= 0) {
			scrollToRow(selectedIndex);
		} else {
			clampOffsets();
		}
		repaint();
		fireViewChanged();
	}

	/**
	 * Updates only the selection and highlighting state without resetting the
	 * scroll position or changing data.
	 */
	public void updateSelectionState(Itemset selected, boolean highlightSubsets,
			boolean highlightSupersets) {
		this.highlightSubsets = highlightSubsets;
		this.highlightSupersets = highlightSupersets;
		applySelection(selected);
		repaint();
	}

	public void setItemsetSelectionListener(ItemsetSelectionListener listener) {
		this.selectionListener = listener;
	}

	public void setViewChangeListener(ViewChangeListener listener) {
		this.viewListener = listener;
	}

	public Itemset getSelectedItemset() {
		return selectedItemset;
	}

	public int getSelectedIndex() {
		return selectedIndex;
	}

	public int getRowCount() {
		return itemsets.size();
	}

	public int getColumnCount() {
		return allItems.size();
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
			clampOffsets();
			repaint();
			fireViewChanged();
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
			fireViewChanged();
		}
	}

	public ColumnOrder getColumnOrder() {
		return columnOrder;
	}
	/**
	 * Changes the ordering of the item columns.
	 */
	public void setColumnOrder(ColumnOrder order) {
		if (order == null || order == columnOrder) return;
		columnOrder = order;
		rebuildColumns();
		hoveredColumn = -1;
		offsetX = 0;
		clampOffsets();
		repaint();
		fireViewChanged();
	}

	// ---------------------------------------------------------------
	// Public API — zoom / layout
	// ---------------------------------------------------------------

	public int getCellSize() {
		return cellSize;
	}

	public int getZoomPercent() {
		return Math.round(100f * cellSize / DEFAULT_CELL_SIZE);
	}

	/** Sets the cell size (zoom level), keeping the centre of the view fixed. */
	public void setCellSize(int size) {
		zoomTo(size, Math.max(0, getWidth() - getFrozenWidth()) / 2,
				Math.max(0, getHeight() - TOP_HEIGHT) / 2);
	}

	public void zoomIn() {
		setCellSize(cellSize + ZOOM_STEP);
	}

	public void zoomOut() {
		setCellSize(cellSize - ZOOM_STEP);
	}

	public void resetZoom() {
		setCellSize(DEFAULT_CELL_SIZE);
	}

	/**
	 * Zooms so that the matrix point under (ax, ay) — coordinates relative to
	 * the top-left corner of the scrollable matrix area — stays in place.
	 */
	private void zoomTo(int newSize, int ax, int ay) {
		int clamped = Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, newSize));
		if (clamped == cellSize) return;
		double scale = (double) clamped / cellSize;
		cellSize = clamped;
		offsetX = (int) Math.round((offsetX - ax) * scale + ax);
		offsetY = (int) Math.round((offsetY - ay) * scale + ay);
		clampOffsets();
		repaint();
		fireViewChanged();
	}

	public int getRowLabelWidth() {
		return rowLabelWidth;
	}

	/** Sets the width of the row-label column (clamped to a sensible range). */
	public void setRowLabelWidth(int width) {
		int clamped = Math.max(MIN_ROW_LABEL_WIDTH, Math.min(MAX_ROW_LABEL_WIDTH, width));
		if (clamped != rowLabelWidth) {
			rowLabelWidth = clamped;
			clampOffsets();
			repaint();
			fireViewChanged();
		}
	}

	public int getFirstVisibleRow() {
		if (itemsets.isEmpty()) return -1;
		return Math.min(-offsetY / cellSize, itemsets.size() - 1);
	}

	public int getLastVisibleRow() {
		if (itemsets.isEmpty()) return -1;
		int viewH = Math.max(0, getHeight() - TOP_HEIGHT);
		int row = (-offsetY + viewH - 1) / cellSize;
		return Math.max(0, Math.min(row, itemsets.size() - 1));
	}

	/** Size in pixels of the complete matrix (frozen area + all cells). */
	public Dimension getFullMatrixSize() {
		return new Dimension(getFrozenWidth() + allItems.size() * cellSize,
				TOP_HEIGHT + itemsets.size() * cellSize);
	}

	/** Maximum number of pixels allowed for a full-matrix export. */
	public static final long MAX_EXPORT_PIXELS = 30_000_000L;

	// ---------------------------------------------------------------
	// Public API — selection & clipboard
	// ---------------------------------------------------------------

	/** Selects the given row (scrolling it into view) and notifies the listener. */
	public void selectRow(int index) {
		selectRow(index, true);
	}

	/** Clears the selection and notifies the listener. */
	public void clearSelection() {
		applySelection(null);
		repaint();
		fireViewChanged();
		fireSelection();
	}

	/**
	 * Copies the selected itemset to the system clipboard in SPMF line format
	 * (e.g. {@code 1 2 3 #SUP: 4}).
	 *
	 * @return true if something was copied
	 */
	public boolean copySelectionToClipboard() {
		if (selectedItemset == null) return false;
		Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(toSpmfLine(selectedItemset)), null);
		return true;
	}

	/** Formats an itemset as one SPMF output line. */
	public static String toSpmfLine(Itemset itemset) {
		StringBuilder sb = new StringBuilder(String.join(" ", itemset.getItems()));
		for (Map.Entry<String, String> e : itemset.getMeasures().entrySet()) {
			sb.append(" #").append(e.getKey()).append(": ").append(e.getValue());
		}
		return sb.toString();
	}

	// ---------------------------------------------------------------
	// Public API — export
	// ---------------------------------------------------------------

	/** Exports the current view (what is on screen) to a PNG image file. */
	public void exportAsImage(File file) throws IOException {
		renderToFile(file, Math.max(getWidth(), 800), Math.max(getHeight(), 600), false);
	}

	/**
	 * Exports the whole matrix (all rows and columns) to a PNG image file.
	 *
	 * @throws IOException if the image would be too large or cannot be written
	 */
	public void exportFullMatrixAsImage(File file) throws IOException {
		if (itemsets.isEmpty()) throw new IOException("Nothing to export.");
		Dimension d = getFullMatrixSize();
		if ((long) d.width * d.height > MAX_EXPORT_PIXELS) {
			throw new IOException(String.format(
					"The image would be too large (%d x %d pixels). "
							+ "Reduce the cell size or filter the patterns.", d.width, d.height));
		}
		renderToFile(file, d.width, d.height, true);
	}

	private void renderToFile(File file, int w, int h, boolean fullMatrix) throws IOException {
		int savedX = offsetX, savedY = offsetY, savedRow = hoveredRow, savedCol = hoveredColumn;
		hoveredRow = -1; // never export hover state
		hoveredColumn = -1;
		if (fullMatrix) {
			offsetX = 0;
			offsetY = 0;
		}
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = image.createGraphics();
		try {
			g2.setColor(COLOR_BACKGROUND);
			g2.fillRect(0, 0, w, h);
			render(g2, w, h);
		} finally {
			g2.dispose();
			offsetX = savedX;
			offsetY = savedY;
			hoveredRow = savedRow;
			hoveredColumn = savedCol;
		}
		if (!ImageIO.write(image, "png", file)) {
			throw new IOException("No PNG writer available.");
		}
	}

	/** Exports the matrix data to a UTF-8 CSV file. */
	public void exportAsCSV(File file) throws IOException {
		try (PrintWriter pw = new PrintWriter(
				new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
			StringBuilder sb = new StringBuilder("Row,Pattern,Size");
			for (String m : measureNames) sb.append(',').append(escapeCSV(m));
			for (String item : allItems) sb.append(',').append(escapeCSV(item));
			pw.println(sb);

			for (int r = 0; r < itemsets.size(); r++) {
				Itemset it = itemsets.get(r);
				sb.setLength(0);
				sb.append(r + 1).append(',').append(escapeCSV(it.getLabel())).append(',').append(it.size());
				for (String m : measureNames) {
					String v = it.getMeasure(m);
					sb.append(',').append(v == null ? "" : escapeCSV(v));
				}
				BitSet bits = rowBits[r];
				for (int c = 0; c < allItems.size(); c++) {
					sb.append(',').append(bits.get(c) ? '1' : '0');
				}
				pw.println(sb);
			}
		}
	}

	private static String escapeCSV(String value) {
		if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
				|| value.indexOf('\r') >= 0) {
			return "\"" + value.replace("\"", "\"\"") + "\"";
		}
		return value;
	}

	// ---------------------------------------------------------------
	// Data model construction
	// ---------------------------------------------------------------

	/** Rebuilds the column list, item index, frequencies and row bit sets. */
	private void rebuildColumns() {
		Map<String, Integer> freq = new HashMap<>();
		for (Itemset it : itemsets) {
			for (String item : it.getItemSet()) {
				freq.merge(item, 1, Integer::sum);
			}
		}
		List<String> items = new ArrayList<>(freq.keySet());
		Comparator<String> byName = itemNameComparator(items);
		Comparator<String> cmp;
		switch (columnOrder) {
		case FREQUENCY_DESC:
			cmp = Comparator.comparingInt((String s) -> -freq.get(s)).thenComparing(byName);
			break;
		case FREQUENCY_ASC:
			cmp = Comparator.comparingInt((String s) -> freq.get(s)).thenComparing(byName);
			break;
		default:
			cmp = byName;
		}
		items.sort(cmp);

		allItems = items;
		itemIndex = new HashMap<>(items.size() * 2);
		itemFrequency = new int[items.size()];
		for (int i = 0; i < items.size(); i++) {
			itemIndex.put(items.get(i), i);
			itemFrequency[i] = freq.get(items.get(i));
		}

		rowBits = new BitSet[itemsets.size()];
		for (int r = 0; r < itemsets.size(); r++) {
			BitSet bits = new BitSet(items.size());
			for (String item : itemsets.get(r).getItems()) {
				bits.set(itemIndex.get(item));
			}
			rowBits[r] = bits;
		}
		recomputeRelations();
	}

	/**
	 * Numeric-aware comparator: if every item parses as an integer, compare
	 * numerically; otherwise case-insensitively.
	 */
	private static Comparator<String> itemNameComparator(Collection<String> items) {
		Map<String, Long> numeric = new HashMap<>();
		for (String s : items) {
			try {
				numeric.put(s, Long.parseLong(s));
			} catch (NumberFormatException e) {
				return String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
			}
		}
		return Comparator.<String, Long>comparing(numeric::get);
	}

	private void extractMeasureNames() {
		Set<String> names = new LinkedHashSet<>();
		for (Itemset it : itemsets) {
			names.addAll(it.getMeasures().keySet());
		}
		measureNames = new ArrayList<>(names);
	}

	private void applySelection(Itemset selected) {
		selectedItemset = selected;
		selectedIndex = (selected != null) ? itemsets.indexOf(selected) : -1;
		recomputeRelations();
	}

	/**
	 * Recomputes, for every row, whether it is a proper subset / superset of
	 * the selected itemset. Done once per selection change instead of per cell.
	 */
	private void recomputeRelations() {
		int n = itemsets.size();
		if (rowRelation.length != n) {
			rowRelation = new byte[n];
		} else {
			java.util.Arrays.fill(rowRelation, REL_NONE);
		}
		subsetCount = 0;
		supersetCount = 0;
		if (selectedItemset == null || !(highlightSubsets || highlightSupersets)
				|| rowBits.length != n) {
			return;
		}

		// Bit set of the selected itemset (it may not be part of the current rows)
		BitSet sel = new BitSet(allItems.size());
		boolean allItemsKnown = true;
		for (String item : selectedItemset.getItemSet()) {
			Integer c = itemIndex.get(item);
			if (c == null) allItemsKnown = false;
			else sel.set(c);
		}
		int selSize = selectedItemset.getItemSet().size();
		BitSet tmp = new BitSet(allItems.size());

		for (int r = 0; r < n; r++) {
			if (r == selectedIndex) continue;
			BitSet row = rowBits[r];
			int rowSize = row.cardinality();
			if (highlightSubsets && rowSize < selSize && isSubset(row, sel, tmp)) {
				rowRelation[r] = REL_SUBSET;
				subsetCount++;
			} else if (highlightSupersets && allItemsKnown && rowSize > selSize
					&& isSubset(sel, row, tmp)) {
				rowRelation[r] = REL_SUPERSET;
				supersetCount++;
			}
		}
	}

	private static boolean isSubset(BitSet a, BitSet b, BitSet scratch) {
		scratch.clear();
		scratch.or(a);
		scratch.andNot(b);
		return scratch.isEmpty();
	}

	// ---------------------------------------------------------------
	// Fonts & measure column widths
	// ---------------------------------------------------------------

	private void rebuildFonts() {
		int small = Math.max(MIN_FONT_SIZE, fontSize - 1);
		fontHeader = new Font("SansSerif", Font.BOLD, fontSize);
		fontRow = new Font("SansSerif", Font.PLAIN, fontSize);
		fontRowNum = new Font("SansSerif", Font.PLAIN, small);
		fontCell = new Font("SansSerif", Font.BOLD, small);
		fontBanner = new Font("SansSerif", Font.PLAIN, small);
		fontEmpty = new Font("SansSerif", Font.PLAIN, fontSize + 5);
		fontMeasure = new Font("SansSerif", Font.PLAIN, small);
	}

	private void computeMeasureColumnWidths() {
		int n = measureNames.size();
		measureColWidths = new int[n];
		totalMeasureWidth = 0;
		if (n == 0) return;

		FontMetrics fmHeader = getFontMetrics(fontHeader);
		FontMetrics fmValue = getFontMetrics(fontMeasure);
		for (int m = 0; m < n; m++) {
			String name = measureNames.get(m);
			int maxW = fmHeader.stringWidth(name);
			for (Itemset it : itemsets) {
				String v = it.getMeasure(name);
				if (v != null) maxW = Math.max(maxW, fmValue.stringWidth(v));
			}
			int w = Math.max(MIN_MEASURE_COL_WIDTH, maxW + MEASURE_COL_PADDING);
			measureColWidths[m] = w;
			totalMeasureWidth += w;
		}
	}

	private int getMeasureColLeft(int measureIndex) {
		int x = 0;
		for (int i = 0; i < measureIndex && i < measureColWidths.length; i++) {
			x += measureColWidths[i];
		}
		return x;
	}

	// ---------------------------------------------------------------
	// Layout helpers
	// ---------------------------------------------------------------

	private int getRowLabelLeft() {
		return showRowNumbers ? ROW_NUMBER_WIDTH : 0;
	}

	private int getMeasureAreaWidth() {
		return showMeasureColumns ? totalMeasureWidth : 0;
	}

	private int getFrozenWidth() {
		return rowLabelWidth + getMeasureAreaWidth();
	}

	/**
	 * Shortens a string with an ellipsis so that it fits in {@code maxWidth}
	 * pixels (binary search on the length).
	 */
	private static String fitString(String s, FontMetrics fm, int maxWidth) {
		if (s == null || maxWidth <= 0) return "";
		if (fm.stringWidth(s) <= maxWidth) return s;
		int ellW = fm.stringWidth(ELLIPSIS);
		if (ellW > maxWidth) return "";
		int lo = 0, hi = s.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) >>> 1;
			if (fm.stringWidth(s.substring(0, mid)) + ellW <= maxWidth) lo = mid;
			else hi = mid - 1;
		}
		return s.substring(0, lo) + ELLIPSIS;
	}

	/**
	 * Draws a column header label horizontally if it fits, otherwise rotated
	 * -90° (and truncated with an ellipsis if even that does not fit).
	 */
	private void drawHeaderLabel(Graphics2D g2, String label, int x, int colWidth, int headerY,
			int headerH, FontMetrics fm) {
		if (fm.stringWidth(label) <= colWidth - 6) {
			int tx = x + (colWidth - fm.stringWidth(label)) / 2;
			int ty = headerY + (headerH + fm.getAscent()) / 2 - 2;
			g2.drawString(label, tx, ty);
		} else {
			String text = fitString(label, fm, headerH - 12);
			AffineTransform original = g2.getTransform();
			int anchorX = x + colWidth / 2 - fm.getAscent() / 2;
			int anchorY = headerY + headerH - 6;
			g2.translate(anchorX, anchorY);
			g2.rotate(-Math.PI / 2);
			g2.drawString(text, 0, fm.getAscent());
			g2.setTransform(original);
		}
	}

	// ---------------------------------------------------------------
	// Painting
	// ---------------------------------------------------------------

	/** Size of the surface currently being painted (screen or export image). */
	private int paintWidth;
	private int paintHeight;

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			render(g2, getWidth(), getHeight());
		} finally {
			g2.dispose();
		}
	}

	private void render(Graphics2D g2, int width, int height) {
		paintWidth = width;
		paintHeight = height;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		if (itemsets.isEmpty()) {
			drawEmptyState(g2);
			return;
		}

		int frozenW = getFrozenWidth();

		// --- Scrollable item matrix area ---
		Shape oldClip = g2.getClip();
		g2.clipRect(frozenW, TOP_HEIGHT, paintWidth - frozenW, paintHeight - TOP_HEIGHT);
		drawGridBackground(g2);
		drawCells(g2);
		drawGridLines(g2);
		drawHoverOverlays(g2);
		g2.setClip(oldClip);

		// --- Frozen areas ---
		drawBanner(g2);
		drawItemColumnHeaders(g2);
		drawMeasureColumnHeaders(g2);
		drawRowNumberAndPatternHeaders(g2);
		drawMeasureColumns(g2);
		drawRowLabels(g2);
	}

	private void drawEmptyState(Graphics2D g2) {
		g2.setColor(Color.GRAY);
		g2.setFont(fontEmpty);
		String msg = "No itemsets to display. Use File \u2192 Open or drop a file here.";
		FontMetrics fm = g2.getFontMetrics();
		g2.drawString(fitString(msg, fm, paintWidth - 20),
				Math.max(10, (paintWidth - fm.stringWidth(msg)) / 2), paintHeight / 2);
	}

	private void drawBanner(Graphics2D g2) {
		g2.setColor(COLOR_BANNER_BG);
		g2.fillRect(0, 0, paintWidth, BANNER_HEIGHT);

		g2.setFont(fontBanner);
		g2.setColor(COLOR_BANNER_TEXT);
		FontMetrics fm = g2.getFontMetrics();

		StringBuilder sb = new StringBuilder();
		sb.append(itemsets.size()).append(" patterns \u00B7 ")
				.append(allItems.size()).append(" distinct items");
		if (!measureNames.isEmpty()) {
			sb.append(" \u00B7 ").append(measureNames.size())
					.append(measureNames.size() == 1 ? " measure" : " measures");
		}
		if (selectedItemset != null) {
			sb.append(" \u00B7 selected: {").append(selectedItemset.getLabel()).append('}');
			if (highlightSubsets) sb.append(" \u00B7 ").append(subsetCount).append(" subsets");
			if (highlightSupersets) sb.append(" \u00B7 ").append(supersetCount).append(" supersets");
		}
		g2.drawString(fitString(sb.toString(), fm, paintWidth - 20), 10,
				(BANNER_HEIGHT + fm.getAscent()) / 2 - 1);
	}

	private void drawRowNumberAndPatternHeaders(Graphics2D g2) {
		g2.setColor(COLOR_HEADER_BG);
		g2.fillRect(0, BANNER_HEIGHT, rowLabelWidth, HEADER_HEIGHT);

		g2.setFont(fontHeader);
		g2.setColor(COLOR_TEXT_LIGHT);
		FontMetrics fm = g2.getFontMetrics();

		if (showRowNumbers) {
			drawHeaderLabel(g2, "#", 0, ROW_NUMBER_WIDTH, BANNER_HEIGHT, HEADER_HEIGHT, fm);
			g2.setColor(COLOR_GRID);
			g2.setStroke(STROKE_GRID);
			g2.drawLine(ROW_NUMBER_WIDTH, BANNER_HEIGHT, ROW_NUMBER_WIDTH, TOP_HEIGHT);
			g2.setColor(COLOR_TEXT_LIGHT);
			drawHeaderLabel(g2, "Pattern", ROW_NUMBER_WIDTH, rowLabelWidth - ROW_NUMBER_WIDTH,
					BANNER_HEIGHT, HEADER_HEIGHT, fm);
		} else {
			drawHeaderLabel(g2, "Pattern", 0, rowLabelWidth, BANNER_HEIGHT, HEADER_HEIGHT, fm);
		}

		g2.setColor(COLOR_GRID);
		g2.setStroke(STROKE_SEPARATOR);
		g2.drawLine(rowLabelWidth, BANNER_HEIGHT, rowLabelWidth, TOP_HEIGHT);
		g2.drawLine(0, TOP_HEIGHT, rowLabelWidth, TOP_HEIGHT);
	}

	private void drawMeasureColumnHeaders(Graphics2D g2) {
		if (!showMeasureColumns || measureNames.isEmpty()) return;

		int left = rowLabelWidth;
		int areaW = getMeasureAreaWidth();
		g2.setColor(COLOR_HEADER_BG);
		g2.fillRect(left, BANNER_HEIGHT, areaW, HEADER_HEIGHT);

		g2.setFont(fontHeader);
		FontMetrics fm = g2.getFontMetrics();
		for (int m = 0; m < measureNames.size(); m++) {
			int x = left + getMeasureColLeft(m);
			int colW = measureColWidths[m];
			g2.setColor(COLOR_TEXT_LIGHT);
			drawHeaderLabel(g2, measureNames.get(m), x, colW, BANNER_HEIGHT, HEADER_HEIGHT, fm);
			g2.setColor(COLOR_GRID);
			g2.setStroke(STROKE_GRID);
			g2.drawLine(x + colW, BANNER_HEIGHT, x + colW, TOP_HEIGHT);
		}

		g2.setColor(COLOR_GRID);
		g2.setStroke(STROKE_SEPARATOR);
		g2.drawLine(left, TOP_HEIGHT, left + areaW, TOP_HEIGHT);
	}

	private void drawItemColumnHeaders(Graphics2D g2) {
		int frozenW = getFrozenWidth();
		int viewW = paintWidth - frozenW;

		g2.setColor(COLOR_HEADER_BG);
		g2.fillRect(frozenW, BANNER_HEIGHT, viewW, HEADER_HEIGHT);

		g2.setFont(fontHeader);
		FontMetrics fm = g2.getFontMetrics();

		int startCol = Math.max(0, -offsetX / cellSize);
		int endCol = Math.min(allItems.size(), (-offsetX + viewW) / cellSize + 1);

		Shape oldClip = g2.getClip();
		g2.clipRect(frozenW, BANNER_HEIGHT, viewW, HEADER_HEIGHT);
		for (int col = startCol; col < endCol; col++) {
			int x = frozenW + col * cellSize + offsetX;
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
		g2.drawLine(frozenW, TOP_HEIGHT, paintWidth, TOP_HEIGHT);
	}

	private void drawGridBackground(Graphics2D g2) {
		int frozenW = getFrozenWidth();
		g2.setColor(COLOR_BACKGROUND);
		g2.fillRect(frozenW, TOP_HEIGHT, paintWidth - frozenW, paintHeight - TOP_HEIGHT);
		// "absent" colour for the whole matrix extent; present cells are painted on top
		g2.setColor(COLOR_CELL_ABSENT);
		g2.fillRect(frozenW + offsetX, TOP_HEIGHT + offsetY,
				allItems.size() * cellSize, itemsets.size() * cellSize);
	}

	private void drawCells(Graphics2D g2) {
		int frozenW = getFrozenWidth();
		int viewW = paintWidth - frozenW;
		int viewH = paintHeight - TOP_HEIGHT;
		int startCol = Math.max(0, -offsetX / cellSize);
		int endCol = Math.min(allItems.size(), (-offsetX + viewW) / cellSize + 1);
		int startRow = Math.max(0, -offsetY / cellSize);
		int endRow = Math.min(itemsets.size(), (-offsetY + viewH) / cellSize + 1);

		boolean marks = showCheckMarks && cellSize >= CHECK_MARK_MIN_CELL;
		FontMetrics fm = null;
		int markW = 0;
		if (marks) {
			g2.setFont(fontCell);
			fm = g2.getFontMetrics();
			markW = fm.stringWidth(CHECK_MARK);
		}

		for (int row = startRow; row < endRow; row++) {
			BitSet bits = rowBits[row];
			Color color = rowCellColor(row);
			int y = TOP_HEIGHT + row * cellSize + offsetY;
			for (int col = bits.nextSetBit(startCol); col >= 0 && col < endCol;
					col = bits.nextSetBit(col + 1)) {
				int x = frozenW + col * cellSize + offsetX;
				g2.setColor(color);
				g2.fillRect(x, y, cellSize, cellSize);
				if (marks) {
					g2.setColor(COLOR_TEXT_LIGHT);
					g2.drawString(CHECK_MARK, x + (cellSize - markW) / 2,
							y + (cellSize + fm.getAscent()) / 2 - 2);
				}
			}
		}
	}

	private Color rowCellColor(int row) {
		if (row == selectedIndex) return COLOR_CELL_SELECTED;
		if (row == hoveredRow) return COLOR_CELL_HOVER;
		byte rel = rowRelation[row];
		if (rel == REL_SUBSET) return COLOR_CELL_SUBSET;
		if (rel == REL_SUPERSET) return COLOR_CELL_SUPERSET;
		return COLOR_CELL_PRESENT;
	}

	private void drawGridLines(Graphics2D g2) {
		int frozenW = getFrozenWidth();
		int viewW = paintWidth - frozenW;
		int viewH = paintHeight - TOP_HEIGHT;
		int startCol = Math.max(0, -offsetX / cellSize);
		int endCol = Math.min(allItems.size(), (-offsetX + viewW) / cellSize + 1);
		int startRow = Math.max(0, -offsetY / cellSize);
		int endRow = Math.min(itemsets.size(), (-offsetY + viewH) / cellSize + 1);
		int matrixBottom = TOP_HEIGHT + offsetY + itemsets.size() * cellSize;
		int matrixRight = frozenW + offsetX + allItems.size() * cellSize;

		g2.setColor(COLOR_GRID);
		g2.setStroke(STROKE_GRID);
		for (int col = startCol; col <= endCol; col++) {
			int x = frozenW + col * cellSize + offsetX;
			g2.drawLine(x, TOP_HEIGHT, x, Math.min(paintHeight, matrixBottom));
		}
		for (int row = startRow; row <= endRow; row++) {
			int y = TOP_HEIGHT + row * cellSize + offsetY;
			g2.drawLine(frozenW, y, Math.min(paintWidth, matrixRight), y);
		}
	}

	/** Translucent hover overlays, drawn after the cells so they stay visible. */
	private void drawHoverOverlays(Graphics2D g2) {
		int frozenW = getFrozenWidth();
		if (hoveredColumn >= 0 && hoveredColumn < allItems.size()) {
			g2.setColor(COLOR_COLUMN_HIGHLIGHT);
			g2.fillRect(frozenW + hoveredColumn * cellSize + offsetX, TOP_HEIGHT + offsetY,
					cellSize, itemsets.size() * cellSize);
		}
		if (hoveredRow >= 0 && hoveredRow < itemsets.size()) {
			g2.setColor(COLOR_ROW_HIGHLIGHT);
			g2.fillRect(frozenW + offsetX, TOP_HEIGHT + hoveredRow * cellSize + offsetY,
					allItems.size() * cellSize, cellSize);
		}
	}

	private void drawMeasureColumns(Graphics2D g2) {
		if (!showMeasureColumns || measureNames.isEmpty()) return;

		int left = rowLabelWidth;
		int viewH = paintHeight - TOP_HEIGHT;
		int startRow = Math.max(0, -offsetY / cellSize);
		int endRow = Math.min(itemsets.size(), (-offsetY + viewH) / cellSize + 1);

		g2.setFont(fontMeasure);
		FontMetrics fm = g2.getFontMetrics();

		for (int row = startRow; row < endRow; row++) {
			int y = TOP_HEIGHT + row * cellSize + offsetY;
			Itemset it = itemsets.get(row);
			Color bg = getRowBackground(row);
			int textY = y + (cellSize + fm.getAscent()) / 2 - 2;

			for (int m = 0; m < measureNames.size(); m++) {
				int x = left + getMeasureColLeft(m);
				int colW = measureColWidths[m];
				g2.setColor(bg);
				g2.fillRect(x, y, colW, cellSize);
				g2.setColor(COLOR_GRID);
				g2.setStroke(STROKE_GRID);
				g2.drawRect(x, y, colW, cellSize);

				String v = it.getMeasure(measureNames.get(m));
				if (v != null) {
					String text = fitString(v, fm, colW - 12);
					g2.setColor(COLOR_TEXT_MEASURE);
					g2.drawString(text, x + colW - fm.stringWidth(text) - 6, textY);
				}
			}
		}

		int frozenW = getFrozenWidth();
		g2.setColor(COLOR_GRID);
		g2.setStroke(STROKE_SEPARATOR);
		g2.drawLine(frozenW, TOP_HEIGHT, frozenW, paintHeight);
	}

	private void drawRowLabels(Graphics2D g2) {
		int viewH = paintHeight - TOP_HEIGHT;
		int startRow = Math.max(0, -offsetY / cellSize);
		int endRow = Math.min(itemsets.size(), (-offsetY + viewH) / cellSize + 1);
		int labelLeft = getRowLabelLeft();
		int labelW = rowLabelWidth - labelLeft - 12;

		FontMetrics fmNum = g2.getFontMetrics(fontRowNum);
		FontMetrics fmRow = g2.getFontMetrics(fontRow);

		for (int row = startRow; row < endRow; row++) {
			int y = TOP_HEIGHT + row * cellSize + offsetY;
			Color bg = getRowBackground(row);

			if (showRowNumbers) {
				g2.setColor(COLOR_ROW_NUM_BG);
				g2.fillRect(0, y, ROW_NUMBER_WIDTH, cellSize);
				g2.setColor(bg);
				g2.fillRect(ROW_NUMBER_WIDTH, y, rowLabelWidth - ROW_NUMBER_WIDTH, cellSize);

				g2.setFont(fontRowNum);
				g2.setColor(COLOR_TEXT_MUTED);
				String num = String.valueOf(row + 1);
				g2.drawString(num, ROW_NUMBER_WIDTH - fmNum.stringWidth(num) - 5,
						y + (cellSize + fmNum.getAscent()) / 2 - 2);

				g2.setColor(COLOR_GRID);
				g2.setStroke(STROKE_GRID);
				g2.drawLine(ROW_NUMBER_WIDTH, y, ROW_NUMBER_WIDTH, y + cellSize);
			} else {
				g2.setColor(bg);
				g2.fillRect(0, y, rowLabelWidth, cellSize);
			}

			g2.setColor(COLOR_GRID);
			g2.setStroke(STROKE_GRID);
			g2.drawLine(0, y + cellSize, rowLabelWidth, y + cellSize);

			g2.setFont(fontRow);
			g2.setColor(COLOR_TEXT_DARK);
			g2.drawString(fitString(itemsets.get(row).getLabel(), fmRow, labelW),
					labelLeft + 6, y + (cellSize + fmRow.getAscent()) / 2 - 2);
		}

		g2.setColor(COLOR_GRID);
		g2.setStroke(STROKE_SEPARATOR);
		g2.drawLine(rowLabelWidth, TOP_HEIGHT, rowLabelWidth, paintHeight);
	}

	private Color getRowBackground(int row) {
		if (row == selectedIndex) return COLOR_ROW_SELECTED_BG;
		if (row == hoveredRow) return COLOR_ROW_HOVER_BG;
		byte rel = rowRelation[row];
		if (rel == REL_SUBSET) return COLOR_ROW_SUBSET_BG;
		if (rel == REL_SUPERSET) return COLOR_ROW_SUPERSET_BG;
		return (row & 1) == 1 ? COLOR_ROW_ALT_BG : COLOR_ROW_LABEL_BG;
	}

	// ---------------------------------------------------------------
	// Tooltip
	// ---------------------------------------------------------------

	@Override
	public String getToolTipText(MouseEvent e) {
		if (itemsets.isEmpty()) return null;
		int x = e.getX(), y = e.getY();
		int frozenW = getFrozenWidth();
		boolean inBody = y >= TOP_HEIGHT;
		boolean inHeader = y >= BANNER_HEIGHT && y < TOP_HEIGHT;

		if (inBody) {
			int row = getRowAtScreenY(y);
			if (row < 0) return null;
			Itemset it = itemsets.get(row);
			if (x >= frozenW) {
				int col = getColumnAtScreenX(x);
				if (col < 0) return null;
				boolean present = rowBits[row].get(col);
				return "<html><b>" + esc(allItems.get(col)) + "</b> \u2014 "
						+ (present ? "\u2713 present" : "\u2717 absent")
						+ "<br><b>Pattern:</b> {" + esc(it.getLabel()) + "}</html>";
			}
			if (x >= rowLabelWidth) {
				int mi = getMeasureIndexAtX(x);
				if (mi < 0) return null;
				String name = measureNames.get(mi);
				String v = it.getMeasure(name);
				return "<html><b>" + esc(name) + ":</b> " + (v == null ? "\u2014" : esc(v)) + "</html>";
			}
			return buildRowTooltip(row, it);
		}

		if (inHeader) {
			if (x >= frozenW) {
				int col = getColumnAtScreenX(x);
				if (col < 0) return null;
				return "<html><b>" + esc(allItems.get(col)) + "</b><br>Appears in "
						+ itemFrequency[col] + " of " + itemsets.size() + " patterns</html>";
			}
			if (x >= rowLabelWidth) {
				int mi = getMeasureIndexAtX(x);
				return (mi >= 0) ? "<html><b>" + esc(measureNames.get(mi)) + "</b></html>" : null;
			}
			return "Drag the right border of this column to resize it";
		}
		return null;
	}

	private String buildRowTooltip(int row, Itemset it) {
		StringBuilder sb = new StringBuilder("<html>");
		sb.append("<b>Row ").append(row + 1).append("</b><br>");
		sb.append("<b>Pattern:</b> {").append(esc(it.getLabel())).append("}");
		sb.append("<br><b>Size:</b> ").append(it.size());
		for (Map.Entry<String, String> en : it.getMeasures().entrySet()) {
			sb.append("<br><b>").append(esc(en.getKey())).append(":</b> ").append(esc(en.getValue()));
		}
		byte rel = rowRelation[row];
		if (rel == REL_SUBSET) sb.append("<br><i>Subset of the selected pattern</i>");
		else if (rel == REL_SUPERSET) sb.append("<br><i>Superset of the selected pattern</i>");
		return sb.append("</html>").toString();
	}

	private static String esc(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	// ---------------------------------------------------------------
	// Hit-testing
	// ---------------------------------------------------------------

	private int getRowAtScreenY(int screenY) {
		if (itemsets.isEmpty() || screenY < TOP_HEIGHT) return -1;
		int y = screenY - TOP_HEIGHT - offsetY;
		if (y < 0) return -1;
		int row = y / cellSize;
		return (row < itemsets.size()) ? row : -1;
	}

	private int getColumnAtScreenX(int screenX) {
		if (allItems.isEmpty()) return -1;
		int x = screenX - getFrozenWidth() - offsetX;
		if (screenX < getFrozenWidth() || x < 0) return -1;
		int col = x / cellSize;
		return (col < allItems.size()) ? col : -1;
	}

	private int getMeasureIndexAtX(int screenX) {
		if (!showMeasureColumns) return -1;
		int rel = screenX - rowLabelWidth;
		if (rel < 0) return -1;
		int cumulative = 0;
		for (int i = 0; i < measureColWidths.length; i++) {
			cumulative += measureColWidths[i];
			if (rel < cumulative) return i;
		}
		return -1;
	}

	private boolean isOverResizeGrip(Point p) {
		return !itemsets.isEmpty() && p.y >= BANNER_HEIGHT
				&& Math.abs(p.x - rowLabelWidth) <= RESIZE_GRIP;
	}

	// ---------------------------------------------------------------
	// Scrolling
	// ---------------------------------------------------------------

	private void clampOffsets() {
		int viewW = Math.max(0, getWidth() - getFrozenWidth());
		int viewH = Math.max(0, getHeight() - TOP_HEIGHT);
		offsetX = clampOffset(offsetX, allItems.size() * cellSize, viewW);
		offsetY = clampOffset(offsetY, itemsets.size() * cellSize, viewH);
	}

	private static int clampOffset(int offset, int total, int view) {
		if (total <= view) return 0;
		return Math.min(0, Math.max(-(total - view), offset));
	}

	private void scrollBy(int dx, int dy) {
		offsetX += dx;
		offsetY += dy;
		clampOffsets();
		repaint();
		fireViewChanged();
	}

	/** Scrolls so that the row is vertically centred. */
	private void scrollToRow(int row) {
		if (row < 0) return;
		offsetY = -row * cellSize + Math.max(0, getHeight() - TOP_HEIGHT - cellSize) / 2;
		clampOffsets();
	}

	private void ensureRowVisible(int row) {
		if (row < 0) return;
		int rowTop = TOP_HEIGHT + row * cellSize + offsetY;
		int rowBot = rowTop + cellSize;
		if (rowTop < TOP_HEIGHT) offsetY += (TOP_HEIGHT - rowTop);
		else if (rowBot > getHeight()) offsetY -= (rowBot - getHeight());
		clampOffsets();
	}

	private void fireViewChanged() {
		if (viewListener != null) viewListener.onViewChanged();
	}

	private void fireSelection() {
		if (selectionListener != null) selectionListener.onItemsetSelected(selectedItemset);
	}

	// ---------------------------------------------------------------
	// Mouse handling
	// ---------------------------------------------------------------

	private void setupMouseListeners() {
		MouseAdapter handler = new MouseAdapter() {

			@Override
			public void mousePressed(MouseEvent e) {
				requestFocusInWindow();
				lastMousePos = e.getPoint();
				if (isOverResizeGrip(e.getPoint())) {
					isResizingLabels = true;
					return;
				}
				if (e.getX() >= getFrozenWidth() && e.getY() >= TOP_HEIGHT) {
					isDragging = true;
					setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (isDragging || isResizingLabels) {
					isDragging = false;
					isResizingLabels = false;
					updateCursor(e.getPoint());
				}
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getButton() != MouseEvent.BUTTON1 || itemsets.isEmpty()) return;
				if (isOverResizeGrip(e.getPoint()) || e.getY() < TOP_HEIGHT) return;
				int row = getRowAtScreenY(e.getY());
				if (row < 0) return;
				if (row == selectedIndex) clearSelection();
				else selectRow(row, false);
			}

			@Override
			public void mouseMoved(MouseEvent e) {
				updateHover(e.getPoint());
				updateCursor(e.getPoint());
			}

			@Override
			public void mouseExited(MouseEvent e) {
				if (!isDragging && !isResizingLabels) setHover(-1, -1);
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				if (lastMousePos == null) return;
				if (isResizingLabels) {
					setRowLabelWidth(e.getX());
				} else if (isDragging) {
					scrollBy(e.getX() - lastMousePos.x, e.getY() - lastMousePos.y);
				}
				lastMousePos = e.getPoint();
			}
		};
		addMouseListener(handler);
		addMouseMotionListener(handler);

		addMouseWheelListener(e -> {
			if (itemsets.isEmpty()) return;
			int rot = e.getWheelRotation();
			if (rot == 0) return;
			if (e.isControlDown()) {
				zoomTo(cellSize - rot * ZOOM_STEP, e.getX() - getFrozenWidth(), e.getY() - TOP_HEIGHT);
			} else if (e.isShiftDown()) {
				scrollBy(-rot * ROWS_PER_WHEEL_NOTCH * cellSize, 0);
			} else {
				scrollBy(0, -rot * ROWS_PER_WHEEL_NOTCH * cellSize);
			}
			updateHover(e.getPoint());
		});
	}

	private void updateHover(Point p) {
		int row = (p.y >= TOP_HEIGHT) ? getRowAtScreenY(p.y) : -1;
		int col = (p.y >= BANNER_HEIGHT) ? getColumnAtScreenX(p.x) : -1;
		setHover(row, col);
	}

	private void setHover(int row, int col) {
		if (row != hoveredRow || col != hoveredColumn) {
			hoveredRow = row;
			hoveredColumn = col;
			repaint();
		}
	}

	private void updateCursor(Point p) {
		int type;
		if (isDragging) type = Cursor.MOVE_CURSOR;
		else if (isResizingLabels || isOverResizeGrip(p)) type = Cursor.E_RESIZE_CURSOR;
		else if (p.x < rowLabelWidth && p.y >= TOP_HEIGHT && getRowAtScreenY(p.y) >= 0)
			type = Cursor.HAND_CURSOR;
		else type = Cursor.DEFAULT_CURSOR;
		if (getCursor().getType() != type) setCursor(Cursor.getPredefinedCursor(type));
	}

	// ---------------------------------------------------------------
	// Keyboard handling
	// ---------------------------------------------------------------

	private void setupKeyListeners() {
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (itemsets.isEmpty()) return;
				switch (e.getKeyCode()) {
				case KeyEvent.VK_UP:        moveSelection(-1); break;
				case KeyEvent.VK_DOWN:      moveSelection(1); break;
				case KeyEvent.VK_PAGE_UP:   moveSelectionByPage(-1); break;
				case KeyEvent.VK_PAGE_DOWN: moveSelectionByPage(1); break;
				case KeyEvent.VK_HOME:      selectRow(0, true); break;
				case KeyEvent.VK_END:       selectRow(itemsets.size() - 1, true); break;
				case KeyEvent.VK_LEFT:      scrollBy(KEY_SCROLL_COLUMNS * cellSize, 0); break;
				case KeyEvent.VK_RIGHT:     scrollBy(-KEY_SCROLL_COLUMNS * cellSize, 0); break;
				case KeyEvent.VK_ESCAPE:    clearSelection(); break;
				case KeyEvent.VK_C:
					if (e.isControlDown()) copySelectionToClipboard();
					break;
				default:
					break;
				}
			}
		});
	}

	private void moveSelection(int delta) {
		int target = (selectedIndex < 0) ? (delta > 0 ? 0 : itemsets.size() - 1)
				: selectedIndex + delta;
		target = Math.max(0, Math.min(itemsets.size() - 1, target));
		if (target != selectedIndex) selectRow(target, true);
	}

	private void moveSelectionByPage(int direction) {
		int rowsPerPage = Math.max(1, (getHeight() - TOP_HEIGHT) / cellSize - 1);
		moveSelection(direction * rowsPerPage);
	}

	private void selectRow(int index, boolean scroll) {
		if (index < 0 || index >= itemsets.size()) return;
		applySelection(itemsets.get(index));
		if (scroll) ensureRowVisible(index);
		repaint();
		fireViewChanged();
		fireSelection();
	}
}