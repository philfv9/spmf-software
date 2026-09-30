package ca.pfv.spmf.gui.visuals.heatmap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.Locale;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

/*
 * Copyright (c) 2008-2023 Philippe Fournier-Viger
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
 * A JPanel that displays a heat map inside a scroll pane.
 * <p>
 * All geometry is computed by a single {@link #computeLayout(int, int)} method
 * that is shared by painting, tooltips, preferred-size computation and image
 * export, so the different code paths can never disagree.
 * <p>
 * Changes to the display settings fire {@link java.beans.PropertyChangeEvent}s
 * (see the {@code PROP_*} constants) so that a host UI can keep its controls in
 * sync regardless of whether the change came from the UI, from code, or from
 * Ctrl + mouse wheel zooming.
 *
 * @author Philippe Fournier-Viger
 */
@SuppressWarnings("serial")
public class HeatMap extends JPanel {

	/** Property name fired when the minimum cell size changes (old/new: Dimension) */
	public static final String PROP_CELL_SIZE = "heatmap.cellSize";
	/** Property name fired when the margin changes (old/new: Integer) */
	public static final String PROP_MARGIN = "heatmap.margin";
	/** Property name fired when the label font changes (old/new: Font) */
	public static final String PROP_LABEL_FONT = "heatmap.labelFont";
	/** Property name fired when any of the draw* flags changes (old/new: Boolean) */
	public static final String PROP_DISPLAY_OPTIONS = "heatmap.displayOptions";

	public static final int DEFAULT_MIN_CELL_SIZE = 20;
	public static final int DEFAULT_MARGIN = 10;
	public static final int MAX_CELL_SIZE = 500;

	private static final int LABEL_PADDING = 6;
	private static final int MAX_LABEL_LENGTH_PX = 250;
	private static final int COLOR_SCALE_GAP = 10;
	private static final int COLOR_SCALE_BAR_WIDTH = 20;
	private static final int COLOR_SCALE_LABEL_GAP = 5;
	private static final int MIN_CELL_SIZE_FOR_GRID = 3;
	private static final String ELLIPSIS = "...";
	private static final Color GRID_COLOR = new Color(200, 200, 200);
	private static final Color NO_DATA_COLOR = Color.LIGHT_GRAY;

	private final double[][] data;
	private final String[] rowNames;
	private final String[] columnNames;
	private final int rowCount;
	private final int columnCount;
	private final double min;
	private final double max;

	private boolean drawRowLabels = true;
	private boolean drawColumnLabels = true;
	private boolean drawColorScale = true;
	private boolean drawColumnLabelsVertically = false;
	private Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
	private int margin = DEFAULT_MARGIN;
	private int minCellWidth = DEFAULT_MIN_CELL_SIZE;
	private int minCellHeight = DEFAULT_MIN_CELL_SIZE;

	private final DrawingPanel drawingPanel;
	private final JScrollPane scrollPane;

	/** Cached text measurements (-1 = needs recomputation). */
	private int cachedRowLabelWidth = -1;
	private int cachedColumnLabelHeight = -1;
	private int cachedColorScaleWidth = -1;
	private int cachedMaxColumnNameWidth = -1;

	/**
	 * Constructor.
	 *
	 * @param data        matrix of values, indexed as data[row][column]
	 * @param rowNames    one name per row
	 * @param columnNames one name per column
	 */
	public HeatMap(double[][] data, String[] rowNames, String[] columnNames) {
		if (data == null || rowNames == null || columnNames == null) {
			throw new IllegalArgumentException("Data, rowNames, and columnNames must not be null");
		}
		if (data.length == 0 || data[0] == null || data[0].length == 0) {
			throw new IllegalArgumentException("Data must not be empty");
		}
		if (data.length != rowNames.length || data[0].length != columnNames.length) {
			throw new IllegalArgumentException("Mismatch between data and names dimensions");
		}
		for (int i = 0; i < data.length; i++) {
			if (data[i] == null || data[i].length != data[0].length) {
				throw new IllegalArgumentException("All rows must have the same length (row " + i + " differs)");
			}
		}
		this.data = data;
		this.rowNames = rowNames;
		this.columnNames = columnNames;
		this.rowCount = data.length;
		this.columnCount = data[0].length;

		double lo = Double.POSITIVE_INFINITY;
		double hi = Double.NEGATIVE_INFINITY;
		for (double[] row : data) {
			for (double v : row) {
				if (Double.isFinite(v)) {
					if (v < lo) lo = v;
					if (v > hi) hi = v;
				}
			}
		}
		if (lo > hi) { // no finite values at all
			lo = hi = 0;
		}
		this.min = lo;
		this.max = hi;

		setLayout(new BorderLayout());
		drawingPanel = new DrawingPanel();
		scrollPane = new JScrollPane(drawingPanel);
		scrollPane.getVerticalScrollBar().setUnitIncrement(minCellHeight);
		scrollPane.getHorizontalScrollBar().setUnitIncrement(minCellWidth);
		add(scrollPane, BorderLayout.CENTER);
	}

	// =====================================================================
	// Layout
	// =====================================================================

	/** Immutable snapshot of all geometry needed to draw the heat map. */
	private static final class Layout {
		int rowLabelWidth;
		int columnLabelHeight;
		int colorScaleWidth;
		int startX;
		int startY;
		int cellWidth;
		int cellHeight;
		int gridWidth;
		int gridHeight;
		int totalWidth;
		int totalHeight;
	}

	/**
	 * Compute the geometry for a given available area. Cells are never smaller
	 * than the configured minimum but grow to fill extra space. Passing (0, 0)
	 * yields the minimum (preferred) layout.
	 */
	private Layout computeLayout(int availableWidth, int availableHeight) {
		FontMetrics fm = getFontMetrics(labelFont);
		Layout l = new Layout();
		l.rowLabelWidth = getRowLabelWidth(fm);
		l.columnLabelHeight = getColumnLabelHeight(fm);
		l.colorScaleWidth = getColorScaleWidth(fm);
		l.startX = margin + l.rowLabelWidth;
		l.startY = margin + l.columnLabelHeight;

		int freeWidth = availableWidth - l.startX - l.colorScaleWidth - margin;
		int freeHeight = availableHeight - l.startY - margin;
		l.cellWidth = Math.max(minCellWidth, freeWidth / columnCount);
		l.cellHeight = Math.max(minCellHeight, freeHeight / rowCount);

		l.gridWidth = l.cellWidth * columnCount;
		l.gridHeight = l.cellHeight * rowCount;
		l.totalWidth = l.startX + l.gridWidth + l.colorScaleWidth + margin;
		l.totalHeight = l.startY + l.gridHeight + margin;
		return l;
	}

	private int getRowLabelWidth(FontMetrics fm) {
		if (!drawRowLabels) {
			return 0;
		}
		if (cachedRowLabelWidth < 0) {
			cachedRowLabelWidth = Math.min(maxStringWidth(rowNames, fm), MAX_LABEL_LENGTH_PX) + LABEL_PADDING;
		}
		return cachedRowLabelWidth;
	}

	private int getColumnLabelHeight(FontMetrics fm) {
		if (!drawColumnLabels) {
			return 0;
		}
		if (cachedColumnLabelHeight < 0) {
			cachedColumnLabelHeight = drawColumnLabelsVertically
					? Math.min(getMaxColumnNameWidth(fm), MAX_LABEL_LENGTH_PX) + LABEL_PADDING
					: fm.getHeight() + LABEL_PADDING;
		}
		return cachedColumnLabelHeight;
	}

	private int getMaxColumnNameWidth(FontMetrics fm) {
		if (cachedMaxColumnNameWidth < 0) {
			cachedMaxColumnNameWidth = maxStringWidth(columnNames, fm);
		}
		return cachedMaxColumnNameWidth;
	}

	private int getColorScaleWidth(FontMetrics fm) {
		if (!drawColorScale) {
			return 0;
		}
		if (cachedColorScaleWidth < 0) {
			int labelWidth = Math.max(fm.stringWidth(formatValue(min)),
					Math.max(fm.stringWidth(formatValue(max)), fm.stringWidth(formatValue((min + max) / 2.0))));
			cachedColorScaleWidth = COLOR_SCALE_GAP + COLOR_SCALE_BAR_WIDTH + COLOR_SCALE_LABEL_GAP + labelWidth;
		}
		return cachedColorScaleWidth;
	}

	private void invalidateLabelCache() {
		cachedRowLabelWidth = -1;
		cachedColumnLabelHeight = -1;
		cachedColorScaleWidth = -1;
		cachedMaxColumnNameWidth = -1;
	}

	private static int maxStringWidth(String[] strings, FontMetrics fm) {
		int maxWidth = 0;
		for (String s : strings) {
			if (s != null) {
				maxWidth = Math.max(maxWidth, fm.stringWidth(s));
			}
		}
		return maxWidth;
	}

	/**
	 * Truncate a string (adding an ellipsis) so that it fits in maxWidth pixels.
	 */
	private static String truncate(String text, FontMetrics fm, int maxWidth) {
		if (text == null) {
			return "";
		}
		if (fm.stringWidth(text) <= maxWidth) {
			return text;
		}
		int ellipsisWidth = fm.stringWidth(ELLIPSIS);
		int width = 0;
		int end = 0;
		while (end < text.length()) {
			int cw = fm.charWidth(text.charAt(end));
			if (width + cw + ellipsisWidth > maxWidth) {
				break;
			}
			width += cw;
			end++;
		}
		return end == 0 ? "" : text.substring(0, end) + ELLIPSIS;
	}

	/**
	 * How many cells to skip between labels so that labels do not overlap.
	 */
	private static int labelStep(int neededPixels, int availablePixels) {
		if (availablePixels <= 0) {
			return Integer.MAX_VALUE;
		}
		return availablePixels >= neededPixels ? 1 : (int) Math.ceil(neededPixels / (double) availablePixels);
	}

	// =====================================================================
	// Painting
	// =====================================================================

	/**
	 * Paint the complete heat map into the given graphics context. This is used
	 * both for on-screen painting and for image export.
	 *
	 * @param g2     graphics to paint on (not disposed by this method)
	 * @param width  available width in pixels
	 * @param height available height in pixels
	 */
	protected void paintHeatMap(Graphics2D g2, int width, int height) {
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setColor(Color.WHITE);
		g2.fillRect(0, 0, width, height);
		g2.setFont(labelFont);
		FontMetrics fm = g2.getFontMetrics();

		Layout l = computeLayout(width, height);

		// Cells
		for (int row = 0; row < rowCount; row++) {
			int y = l.startY + row * l.cellHeight;
			for (int col = 0; col < columnCount; col++) {
				g2.setColor(colorFor(data[row][col]));
				g2.fillRect(l.startX + col * l.cellWidth, y, l.cellWidth, l.cellHeight);
			}
		}

		// Grid lines (skipped when cells are so tiny that lines would hide them)
		if (l.cellWidth >= MIN_CELL_SIZE_FOR_GRID && l.cellHeight >= MIN_CELL_SIZE_FOR_GRID) {
			g2.setColor(GRID_COLOR);
			int right = l.startX + l.gridWidth;
			int bottom = l.startY + l.gridHeight;
			for (int row = 0; row <= rowCount; row++) {
				int y = l.startY + row * l.cellHeight;
				g2.drawLine(l.startX, y, right, y);
			}
			for (int col = 0; col <= columnCount; col++) {
				int x = l.startX + col * l.cellWidth;
				g2.drawLine(x, l.startY, x, bottom);
			}
		}

		g2.setColor(Color.BLACK);
		if (drawRowLabels) {
			paintRowLabels(g2, fm, l);
		}
		if (drawColumnLabels) {
			paintColumnLabels(g2, fm, l);
		}
		if (drawColorScale) {
			paintColorScale(g2, fm, l);
		}
	}

	private void paintRowLabels(Graphics2D g2, FontMetrics fm, Layout l) {
		int maxWidth = l.rowLabelWidth - LABEL_PADDING;
		int right = margin + maxWidth;
		int step = labelStep(fm.getHeight(), l.cellHeight);
		for (int row = 0; row < rowCount; row += step) {
			String label = truncate(rowNames[row], fm, maxWidth);
			int x = right - fm.stringWidth(label);
			int y = l.startY + row * l.cellHeight + (l.cellHeight - fm.getHeight()) / 2 + fm.getAscent();
			g2.drawString(label, x, y);
		}
	}

	private void paintColumnLabels(Graphics2D g2, FontMetrics fm, Layout l) {
		if (drawColumnLabelsVertically) {
			int maxLength = l.columnLabelHeight - LABEL_PADDING;
			int baselineY = margin + maxLength; // text runs upward from here
			// after a -90 deg rotation the ascent extends to the left of the baseline
			int centerOffset = (fm.getAscent() - fm.getDescent()) / 2;
			int step = labelStep(fm.getHeight(), l.cellWidth);
			for (int col = 0; col < columnCount; col += step) {
				String label = truncate(columnNames[col], fm, maxLength);
				int x = l.startX + col * l.cellWidth + l.cellWidth / 2 + centerOffset;
				drawRotate(g2, x, baselineY, -90, label);
			}
		} else {
			int y = margin + fm.getAscent();
			// If names are wider than a cell, draw only every k-th name across k cells
			int step = labelStep(getMaxColumnNameWidth(fm) + LABEL_PADDING, l.cellWidth);
			int maxWidth = Math.min(step, columnCount) * l.cellWidth - 2;
			for (int col = 0; col < columnCount; col += step) {
				String label = truncate(columnNames[col], fm, maxWidth);
				if (label.isEmpty()) {
					continue;
				}
				int cellX = l.startX + col * l.cellWidth;
				int x = (step == 1) ? cellX + (l.cellWidth - fm.stringWidth(label)) / 2 : cellX + 1;
				g2.drawString(label, x, y);
			}
		}
	}

	private void paintColorScale(Graphics2D g2, FontMetrics fm, Layout l) {
		int x = l.startX + l.gridWidth + COLOR_SCALE_GAP;
		int y = l.startY;
		int h = l.gridHeight;
		if (h <= 0) {
			return;
		}
		int denominator = Math.max(1, h - 1);
		for (int i = 0; i < h; i++) {
			g2.setColor(calculateColor(max - (max - min) * i / (double) denominator));
			g2.fillRect(x, y + i, COLOR_SCALE_BAR_WIDTH, 1);
		}
		g2.setColor(Color.GRAY);
		g2.drawRect(x, y, COLOR_SCALE_BAR_WIDTH, h);

		g2.setColor(Color.BLACK);
		int labelX = x + COLOR_SCALE_BAR_WIDTH + COLOR_SCALE_LABEL_GAP;
		g2.drawString(formatValue(max), labelX, y + fm.getAscent());
		g2.drawString(formatValue(min), labelX, y + h);
		if (h >= 3 * fm.getHeight()) {
			g2.drawString(formatValue((max + min) / 2.0), labelX, y + h / 2 + fm.getAscent() / 2);
		}
	}

	private Color colorFor(double value) {
		return Double.isNaN(value) ? NO_DATA_COLOR : calculateColor(value);
	}

	/**
	 * Calculate the color for a given value based on the min/max range.
	 * Subclasses may override this to use another color gradient.
	 *
	 * @param value the data value
	 * @return the interpolated color
	 */
	protected Color calculateColor(double value) {
		if (max == min) {
			return Color.GREEN;
		}
		double t = (value - min) / (max - min);
		t = Math.max(0.0, Math.min(1.0, t));
		if (t < 0.5) {
			return interpolate(Color.RED, Color.GREEN, t * 2);
		}
		return interpolate(Color.GREEN, Color.BLUE, (t - 0.5) * 2);
	}

	private static Color interpolate(Color a, Color b, double ratio) {
		int r = (int) Math.round(a.getRed() * (1 - ratio) + b.getRed() * ratio);
		int g = (int) Math.round(a.getGreen() * (1 - ratio) + b.getGreen() * ratio);
		int bl = (int) Math.round(a.getBlue() * (1 - ratio) + b.getBlue() * ratio);
		return new Color(clamp(r, 0, 255), clamp(g, 0, 255), clamp(bl, 0, 255));
	}

	/**
	 * Format a value for display, avoiding excessive decimal places.
	 *
	 * @param value the value to format
	 * @return formatted string
	 */
	protected String formatValue(double value) {
		if (Double.isNaN(value)) {
			return "NaN";
		}
		if (value == Math.rint(value) && Math.abs(value) < 1e12) {
			return String.valueOf((long) value);
		}
		double abs = Math.abs(value);
		if (abs >= 1e6 || abs < 1e-3) {
			return String.format(Locale.ROOT, "%.3e", value);
		}
		return String.format(Locale.ROOT, "%.2f", value);
	}

	/**
	 * Draw rotated text at the specified position. The graphics transform is
	 * restored afterwards.
	 *
	 * @param g2d   the graphics context
	 * @param x     the x position of the text baseline origin
	 * @param y     the y position of the text baseline origin
	 * @param angle the rotation angle in degrees
	 * @param text  the text to draw
	 */
	public static void drawRotate(Graphics2D g2d, double x, double y, int angle, String text) {
		AffineTransform saved = g2d.getTransform();
		try {
			g2d.translate(x, y);
			g2d.rotate(Math.toRadians(angle));
			g2d.drawString(text, 0, 0);
		} finally {
			g2d.setTransform(saved);
		}
	}

	/**
	 * Render the complete heat map (regardless of scroll position) to an image.
	 *
	 * @param scale scale factor (1.0 = screen size, 2.0 = double resolution)
	 * @return the rendered image
	 */
	public BufferedImage renderToImage(double scale) {
		if (scale <= 0) {
			throw new IllegalArgumentException("scale must be > 0");
		}
		Layout l = computeLayout(0, 0);
		int w = (int) Math.ceil(l.totalWidth * scale);
		int h = (int) Math.ceil(l.totalHeight * scale);
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = image.createGraphics();
		try {
			g2.scale(scale, scale);
			paintHeatMap(g2, l.totalWidth, l.totalHeight);
		} finally {
			g2.dispose();
		}
		return image;
	}

	// =====================================================================
	// Inner drawing panel
	// =====================================================================

	private final class DrawingPanel extends JPanel implements Scrollable {

		DrawingPanel() {
			setOpaque(true);
			setBackground(Color.WHITE);
			ToolTipManager.sharedInstance().registerComponent(this);
			addMouseWheelListener(HeatMap.this::handleMouseWheel);
		}

		@Override
		public Dimension getPreferredSize() {
			Layout l = computeLayout(0, 0);
			return new Dimension(l.totalWidth, l.totalHeight);
		}

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			try {
				paintHeatMap(g2, getWidth(), getHeight());
			} finally {
				g2.dispose();
			}
		}

		@Override
		public String getToolTipText(MouseEvent e) {
			Layout l = computeLayout(getWidth(), getHeight());
			int dx = e.getX() - l.startX;
			int dy = e.getY() - l.startY;
			if (dx < 0 || dy < 0) {
				return null;
			}
			int col = dx / l.cellWidth;
			int row = dy / l.cellHeight;
			if (col >= columnCount || row >= rowCount) {
				return null;
			}
			double v = data[row][col];
			return "<html><b>Row:</b> " + escapeHtml(rowNames[row])
					+ "<br><b>Column:</b> " + escapeHtml(columnNames[col])
					+ "<br><b>Value:</b> " + (Double.isNaN(v) ? "N/A" : Double.toString(v)) + "</html>";
		}

		// --- Scrollable: fill the viewport when small, scroll by cells otherwise

		@Override
		public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
			return orientation == SwingConstants.HORIZONTAL ? minCellWidth : minCellHeight;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
			return orientation == SwingConstants.HORIZONTAL ? visibleRect.width : visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			Container parent = getParent();
			return parent instanceof JViewport && parent.getWidth() > getPreferredSize().width;
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			Container parent = getParent();
			return parent instanceof JViewport && parent.getHeight() > getPreferredSize().height;
		}
	}

	/** Ctrl/Cmd + wheel zooms; plain wheel is forwarded to the scroll pane. */
	private void handleMouseWheel(MouseWheelEvent e) {
		int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		if ((e.getModifiersEx() & menuMask) != 0) {
			double factor = e.getWheelRotation() < 0 ? 1.1 : 1.0 / 1.1;
			setMinCellSize(scaledSize(minCellWidth, factor), scaledSize(minCellHeight, factor));
			e.consume();
		} else {
			scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(drawingPanel, e, scrollPane));
		}
	}

	private static int scaledSize(int size, double factor) {
		int result = (int) Math.round(size * factor);
		if (result == size) { // guarantee progress for small sizes
			result += factor > 1 ? 1 : -1;
		}
		return clamp(result, 1, MAX_CELL_SIZE);
	}

	private static String escapeHtml(String s) {
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	// =====================================================================
	// Public API
	// =====================================================================

	/**
	 * Refresh the display (recalculates the size of the drawing area and repaints).
	 */
	public void refreshDisplay() {
		drawingPanel.revalidate();
		drawingPanel.repaint();
	}

	/**
	 * Choose a (square) cell size so that the whole heat map fits in the
	 * currently visible area, then apply it.
	 */
	public void fitToViewport() {
		Insets in = scrollPane.getInsets();
		int availableWidth = scrollPane.getWidth() - in.left - in.right;
		int availableHeight = scrollPane.getHeight() - in.top - in.bottom;
		if (availableWidth <= 0 || availableHeight <= 0) {
			return;
		}
		Layout l = computeLayout(0, 0);
		int overheadWidth = l.totalWidth - l.gridWidth;
		int overheadHeight = l.totalHeight - l.gridHeight;
		int cellW = (availableWidth - overheadWidth) / columnCount;
		int cellH = (availableHeight - overheadHeight) / rowCount;
		int size = clamp(Math.min(cellW, cellH), 1, MAX_CELL_SIZE);
		setMinCellSize(size, size);
	}

	/**
	 * Set the minimum width and height of a cell in one step.
	 */
	public void setMinCellSize(int width, int height) {
		int newW = clamp(width, 1, MAX_CELL_SIZE);
		int newH = clamp(height, 1, MAX_CELL_SIZE);
		if (newW == minCellWidth && newH == minCellHeight) {
			return;
		}
		Dimension old = new Dimension(minCellWidth, minCellHeight);
		minCellWidth = newW;
		minCellHeight = newH;
		scrollPane.getVerticalScrollBar().setUnitIncrement(minCellHeight);
		scrollPane.getHorizontalScrollBar().setUnitIncrement(minCellWidth);
		refreshDisplay();
		firePropertyChange(PROP_CELL_SIZE, old, new Dimension(newW, newH));
	}

	public void setMinCellWidth(int minCellWidth) {
		setMinCellSize(minCellWidth, this.minCellHeight);
	}

	public void setMinCellHeight(int minCellHeight) {
		setMinCellSize(this.minCellWidth, minCellHeight);
	}

	public int getMinCellWidth() {
		return minCellWidth;
	}

	public int getMinCellHeight() {
		return minCellHeight;
	}

	public void setMargin(int margin) {
		int newMargin = Math.max(0, margin);
		if (newMargin == this.margin) {
			return;
		}
		int old = this.margin;
		this.margin = newMargin;
		refreshDisplay();
		firePropertyChange(PROP_MARGIN, old, newMargin);
	}

	public int getMargin() {
		return margin;
	}

	/**
	 * Change the font used for labels.
	 *
	 * @param font the font (must not be null)
	 */
	public void setLabelFont(Font font) {
		if (font == null || font.equals(labelFont)) {
			return;
		}
		Font old = labelFont;
		labelFont = font;
		invalidateLabelCache();
		refreshDisplay();
		firePropertyChange(PROP_LABEL_FONT, old, font);
	}

	/**
	 * Convenience method to change only the size of the label font.
	 */
	public void setLabelFontSize(int size) {
		setLabelFont(labelFont.deriveFont((float) Math.max(1, size)));
	}

	public Font getLabelFont() {
		return labelFont;
	}

	public void setDrawRowLabels(boolean drawRowLabels) {
		if (this.drawRowLabels != drawRowLabels) {
			this.drawRowLabels = drawRowLabels;
			displayOptionsChanged(!drawRowLabels, drawRowLabels);
		}
	}

	public void setDrawColumnLabels(boolean drawColumnLabels) {
		if (this.drawColumnLabels != drawColumnLabels) {
			this.drawColumnLabels = drawColumnLabels;
			displayOptionsChanged(!drawColumnLabels, drawColumnLabels);
		}
	}

	public void setDrawColorScale(boolean drawColorScale) {
		if (this.drawColorScale != drawColorScale) {
			this.drawColorScale = drawColorScale;
			displayOptionsChanged(!drawColorScale, drawColorScale);
		}
	}

	public void setDrawColumnLabelsVertically(boolean vertically) {
		if (this.drawColumnLabelsVertically != vertically) {
			this.drawColumnLabelsVertically = vertically;
			displayOptionsChanged(!vertically, vertically);
		}
	}

	private void displayOptionsChanged(boolean oldValue, boolean newValue) {
		invalidateLabelCache();
		refreshDisplay();
		firePropertyChange(PROP_DISPLAY_OPTIONS, oldValue, newValue);
	}

	public boolean isDrawRowLabels() {
		return drawRowLabels;
	}

	public boolean isDrawColumnLabels() {
		return drawColumnLabels;
	}

	public boolean isDrawColorScale() {
		return drawColorScale;
	}

	public boolean isDrawColumnLabelsVertically() {
		return drawColumnLabelsVertically;
	}

	public double[][] getData() {
		return data;
	}

	public String[] getRowNames() {
		return rowNames;
	}

	public String[] getColumnNames() {
		return columnNames;
	}

	public int getRowCount() {
		return rowCount;
	}

	public int getColumnCount() {
		return columnCount;
	}

	public double getMinValue() {
		return min;
	}

	public double getMaxValue() {
		return max;
	}

	/**
	 * Request a total drawing size. Cell sizes are derived from it.
	 *
	 * @deprecated use {@link #setMinCellSize(int, int)} or {@link #fitToViewport()}
	 */
	@Deprecated
	public void setCanvasSize(int width, int height) {
		if (width <= 0 || height <= 0) {
			return;
		}
		Layout l = computeLayout(0, 0);
		int cellW = (width - (l.totalWidth - l.gridWidth)) / columnCount;
		int cellH = (height - (l.totalHeight - l.gridHeight)) / rowCount;
		setMinCellSize(cellW, cellH);
	}
}