package ca.pfv.spmf.gui.visuals.histograms;

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
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;

import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.test.MainTestApriori_simple_saveToFile;

/**
 * A panel displaying a frequency histogram.
 * <p>
 * Features: nice-number Y axis (linear or logarithmic), sticky Y axis and
 * header while scrolling, gradient bars with hover card, several colour
 * schemes, optional mean line / percent mode, zoom (Ctrl + wheel),
 * PNG / CSV / clipboard export.
 *
 * @author Philippe Fournier-Viger, 2024
 */
public class HistogramDistributionPanel extends JPanel implements Scrollable {

	private static final long serialVersionUID = 1L;

	// ---------------------------------------------------------------
	// Public enums / interfaces
	// ---------------------------------------------------------------

	/** Sort orders for the bars. */
	public enum Order {
		ASCENDING_Y("Y value (ascending)"),
		DESCENDING_Y("Y value (descending)"),
		ASCENDING_X("X value (ascending)"),
		DESCENDING_X("X value (descending)");

		private final String label;

		Order(String label) {
			this.label = label;
		}

		/** @return a human readable name for menus */
		public String getLabel() {
			return label;
		}
	}

	/** Colour schemes for the bars. */
	public enum ColorScheme {
		RAINBOW("Rainbow"),
		OCEAN("Ocean blue"),
		HEAT_BY_VALUE("Heat (by value)"),
		BLUES_BY_VALUE("Blues (by value)"),
		SLATE("Slate grey");

		private final String label;

		ColorScheme(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	/** Listener notified when the hovered bar changes ({@code index == -1} when none). */
	public interface BarHoverListener {
		void barHovered(int index, String xLabel, int yValue);
	}

	// ---------------------------------------------------------------
	// Layout constants
	// ---------------------------------------------------------------

	private static final int MARGIN_TOP = 66;
	private static final int MARGIN_RIGHT = 28;
	private static final int MIN_MARGIN_LEFT = 48;
	private static final int MAX_ROTATED_LABEL_PX = 120;
	private static final int X_LABEL_GAP = 4;
	private static final int TICK_LEN = 5;
	private static final int Y_TICK_TARGET = 5;
	private static final int MIN_BAR_WIDTH_FOR_VALUES = 10;
	private static final int LABEL_ROTATION_THRESHOLD = 40;
	private static final int DEFAULT_PANEL_WIDTH = 600;
	private static final int PREFERRED_HEIGHT = 340;
	private static final int AUTO_WIDTH_BAR_LIMIT = 600;
	private static final int MIN_BAR_WIDTH = 1;
	private static final int MAX_BAR_WIDTH = 400;
	private static final int DEFAULT_BAR_WIDTH = 2;
	private static final float ZOOM_FACTOR = 1.25f;

	// ---------------------------------------------------------------
	// Fonts
	// ---------------------------------------------------------------

	private static final String FONT_FAMILY = chooseFontFamily();
	private static final Font FONT_NORMAL = new Font(FONT_FAMILY, Font.PLAIN, 11);
	private static final Font FONT_SMALL = new Font(FONT_FAMILY, Font.PLAIN, 10);
	private static final Font FONT_BOLD = new Font(FONT_FAMILY, Font.BOLD, 12);
	private static final Font FONT_TITLE = new Font(FONT_FAMILY, Font.BOLD, 17);

	// ---------------------------------------------------------------
	// Colours
	// ---------------------------------------------------------------

	private static final Color GRID_COLOR = new Color(228, 230, 235);
	private static final Color AXIS_COLOR = new Color(70, 74, 80);
	private static final Color TEXT_COLOR = new Color(40, 44, 52);
	private static final Color TEXT_MUTED = new Color(110, 115, 125);
	private static final Color TICK_LABEL_COLOR = new Color(95, 100, 110);
	private static final Color BAR_BORDER_COLOR = new Color(0, 0, 0, 70);
	private static final Color HOVER_BORDER_COLOR = new Color(30, 30, 30);
	private static final Color MEAN_LINE_COLOR = new Color(214, 64, 64);
	private static final Color CARD_BG = new Color(255, 255, 255, 242);
	private static final Color CARD_BORDER = new Color(160, 165, 175);
	private static final Color CARD_SHADOW = new Color(0, 0, 0, 30);
	private static final Color OCEAN_BASE = new Color(52, 120, 200);
	private static final Color SLATE_BASE = new Color(125, 135, 150);
	private static final Color[] HEAT_STOPS = {
			new Color(254, 229, 145), new Color(247, 138, 60), new Color(196, 30, 42) };
	private static final Color[] BLUE_STOPS = {
			new Color(198, 219, 239), new Color(66, 146, 198), new Color(8, 60, 120) };

	// golden-ratio hue stepping (kept from the original)
	private static final float HUE_STEP = 0.618033988749895f;
	private static final float SATURATION = 0.62f;
	private static final float BRIGHTNESS = 0.88f;

	// ---------------------------------------------------------------
	// Strokes
	// ---------------------------------------------------------------

	private static final Stroke AXIS_STROKE = new BasicStroke(1.4f);
	private static final Stroke GRID_STROKE = new BasicStroke(1f, BasicStroke.CAP_BUTT,
			BasicStroke.JOIN_MITER, 10f, new float[] { 3f, 4f }, 0f);
	private static final Stroke TICK_STROKE = new BasicStroke(1f);
	private static final Stroke BAR_STROKE = new BasicStroke(1f);
	private static final Stroke HOVER_STROKE = new BasicStroke(1.8f);
	private static final Stroke MEAN_STROKE = new BasicStroke(1.5f, BasicStroke.CAP_BUTT,
			BasicStroke.JOIN_MITER, 10f, new float[] { 6f, 4f }, 0f);

	// ---------------------------------------------------------------
	// Data
	// ---------------------------------------------------------------

	private int[] yValues;
	private int[] xLabels;
	private String title;
	private String xAxisName;
	private String yAxisName;
	private Map<Integer, String> mapXValuesToString;

	// statistics
	private int maxY;
	private int minY;
	private long sum;
	private double mean;
	private double median;
	private double stdDev;
	private int[] rankByValue;

	// ---------------------------------------------------------------
	// Options
	// ---------------------------------------------------------------

	private boolean showBarLabels;
	private boolean showBarValues;
	private boolean showPercent;
	private boolean showMeanLine;
	private boolean logScale;
	private ColorScheme colorScheme = ColorScheme.RAINBOW;
	private Order selectedOrder;
	private int barWidth = DEFAULT_BAR_WIDTH;

	// ---------------------------------------------------------------
	// Runtime state
	// ---------------------------------------------------------------

	private Color[] barColors = new Color[0];
	private int hoveredBarIndex = -1;
	private Point mousePoint;
	private boolean exporting;
	private int maxLabelWidthCache = -1;

	// layout cache (absolute panel coordinates), refreshed on every paint
	private int marginLeft;
	private int plotLeft;
	private int plotTop;
	private int plotRight;
	private int plotBottom;
	private int plotHeight;
	private YScale yScale;

	private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.US);
	private final NumberFormat decimalFormat = NumberFormat.getNumberInstance(Locale.US);
	private final List<BarHoverListener> hoverListeners = new CopyOnWriteArrayList<>();

	/** Y scale description: tick values (raw) and the top value (transformed). */
	private static final class YScale {
		double[] ticks;
		double top;
	}

	// ---------------------------------------------------------------
	// Constructors (signatures unchanged)
	// ---------------------------------------------------------------

	public HistogramDistributionPanel(int[] yValues, int[] xLabels, String title,
			boolean showBarLabels, boolean showBarValues,
			String xAxisName, String yAxisName,
			Map<Integer, String> mapXValuesToString, Order order) {
		validateArrays(yValues, xLabels);
		this.mapXValuesToString = mapXValuesToString;
		initializeHistogram(yValues, xLabels, title, showBarLabels, showBarValues,
				xAxisName, yAxisName, order);
	}

	public HistogramDistributionPanel(Vector<List<Object>> data, int width, int height,
			int index, String title,
			boolean showBarLabels, boolean showBarValues,
			String xAxisName, String yAxisName, Order order) {
		super();
		int maxLabel = findMaxLabelFromData(data, index);
		int[] valuesArray = new int[maxLabel + 1];
		int[] labelsArray = new int[maxLabel + 1];
		for (int i = 0; i <= maxLabel; i++) {
			labelsArray[i] = i;
		}
		populateValuesFromData(data, index, valuesArray);
		initializeHistogram(valuesArray, labelsArray, title,
				showBarLabels, showBarValues, xAxisName, yAxisName, order);
	}

	// ---------------------------------------------------------------
	// Initialisation
	// ---------------------------------------------------------------

	private static void validateArrays(int[] yValues, int[] xLabels) {
		if (yValues == null || xLabels == null) {
			throw new IllegalArgumentException("yValues and xLabels must not be null.");
		}
		if (yValues.length != xLabels.length) {
			throw new IllegalArgumentException(
					"yValues and xLabels must have the same length (got "
							+ yValues.length + " vs " + xLabels.length + ").");
		}
	}

	private void initializeHistogram(int[] yValues, int[] xLabels, String title,
			boolean showBarLabels, boolean showBarValues,
			String xAxisName, String yAxisName, Order order) {
		this.yValues = yValues.clone();
		this.xLabels = xLabels.clone();
		this.title = title == null ? "" : title;
		this.showBarLabels = showBarLabels;
		this.showBarValues = showBarValues;
		this.xAxisName = xAxisName == null ? "" : xAxisName;
		this.yAxisName = yAxisName == null ? "" : yAxisName;

		decimalFormat.setMaximumFractionDigits(2);

		computeStatistics();
		if (order != null) {
			setSortOrder(order);
		} else {
			generateBarColors();
		}

		setBackground(Color.WHITE);
		setOpaque(true);
		autoSizeBarWidth();
		installMouseHandling();
	}

	private void autoSizeBarWidth() {
		int barCount = yValues.length;
		if (barCount > 0 && barCount < AUTO_WIDTH_BAR_LIMIT) {
			barWidth = Math.max(DEFAULT_BAR_WIDTH, DEFAULT_PANEL_WIDTH / barCount);
		} else {
			barWidth = DEFAULT_BAR_WIDTH;
		}
		barWidth = Math.min(barWidth, MAX_BAR_WIDTH);
	}

	private void installMouseHandling() {
		MouseAdapter adapter = new MouseAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				updateHover(e.getPoint());
			}

			@Override
			public void mouseDragged(MouseEvent e) {
				updateHover(e.getPoint());
			}

			@Override
			public void mouseExited(MouseEvent e) {
				updateHover(null);
			}

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (e.isControlDown()) {
					zoom(-e.getWheelRotation(), e.getX());
					e.consume();
					return;
				}
				// let the enclosing scroll pane handle normal scrolling
				Container parent = getParent();
				if (parent != null) {
					parent.dispatchEvent(SwingUtilities.convertMouseEvent(
							HistogramDistributionPanel.this, e, parent));
				}
			}
		};
		addMouseListener(adapter);
		addMouseMotionListener(adapter);
		addMouseWheelListener(adapter);
	}

	@Override
	public void addNotify() {
		super.addNotify();
		// The header and Y axis are "sticky"; blit scrolling would smear them.
		Container p = getParent();
		if (p instanceof JViewport) {
			((JViewport) p).setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
		}
	}

	private void computeStatistics() {
		int n = yValues.length;
		rankByValue = new int[n];
		if (n == 0) {
			maxY = 0;
			minY = 0;
			sum = 0;
			mean = 0;
			median = 0;
			stdDev = 0;
			return;
		}
		long s = 0;
		int mn = Integer.MAX_VALUE;
		int mx = Integer.MIN_VALUE;
		for (int v : yValues) {
			s += v;
			if (v < mn) mn = v;
			if (v > mx) mx = v;
		}
		sum = s;
		minY = mn;
		maxY = mx;
		mean = (double) s / n;

		double var = 0;
		for (int v : yValues) {
			double d = v - mean;
			var += d * d;
		}
		stdDev = Math.sqrt(var / n);

		int[] sorted = yValues.clone();
		Arrays.sort(sorted);
		median = (n % 2 == 1) ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;

		for (int i = 0; i < n; i++) {
			int greater = n - upperBound(sorted, yValues[i]);
			rankByValue[i] = greater + 1;
		}
	}

	/** Index of the first element strictly greater than {@code key}. */
	private static int upperBound(int[] sorted, int key) {
		int lo = 0;
		int hi = sorted.length;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (sorted[mid] <= key) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return lo;
	}

	// ---------------------------------------------------------------
	// Public API
	// ---------------------------------------------------------------

	public int getBarWidth() {
		return barWidth;
	}

	/** Sets the bar width in pixels (fires the {@code "barWidth"} property change). */
	public void setBarWidth(int width) {
		int clamped = Math.max(MIN_BAR_WIDTH, Math.min(MAX_BAR_WIDTH, width));
		if (clamped == barWidth) return;
		int old = barWidth;
		barWidth = clamped;
		firePropertyChange("barWidth", old, barWidth);
		revalidate();
		repaint();
	}

	public int getBarCount() {
		return yValues.length;
	}

	public Order getSelectedOrder() {
		return selectedOrder;
	}

	public void setSortOrder(Order order) {
		if (order == null) return;
		switch (order) {
		case ASCENDING_Y:
			sortParallel(yValues, xLabels, true);
			break;
		case DESCENDING_Y:
			sortParallel(yValues, xLabels, false);
			break;
		case ASCENDING_X:
			sortParallel(xLabels, yValues, true);
			break;
		case DESCENDING_X:
			sortParallel(xLabels, yValues, false);
			break;
		}
		selectedOrder = order;
		computeStatistics();
		generateBarColors();
		maxLabelWidthCache = -1;
		hoveredBarIndex = -1;
		repaint();
	}

	public ColorScheme getColorScheme() {
		return colorScheme;
	}

	public void setColorScheme(ColorScheme scheme) {
		if (scheme == null || scheme == colorScheme) return;
		colorScheme = scheme;
		generateBarColors();
		repaint();
	}

	public boolean isShowBarLabels() {
		return showBarLabels;
	}

	public void setShowBarLabels(boolean show) {
		showBarLabels = show;
		repaint();
	}

	public boolean isShowBarValues() {
		return showBarValues;
	}

	public void setShowBarValues(boolean show) {
		showBarValues = show;
		repaint();
	}

	public boolean isShowPercent() {
		return showPercent;
	}

	/** Shows bar values as a percentage of the total instead of raw counts. */
	public void setShowPercent(boolean show) {
		showPercent = show;
		repaint();
	}

	public boolean isShowMeanLine() {
		return showMeanLine;
	}

	public void setShowMeanLine(boolean show) {
		showMeanLine = show;
		repaint();
	}

	public boolean isLogarithmicScale() {
		return logScale;
	}

	public void setLogarithmicScale(boolean log) {
		logScale = log;
		revalidate();
		repaint();
	}

	public void addBarHoverListener(BarHoverListener l) {
		if (l != null) hoverListeners.add(l);
	}

	public void removeBarHoverListener(BarHoverListener l) {
		hoverListeners.remove(l);
	}

	/** Zooms in one step around the centre of the visible area. */
	public void zoomIn() {
		Rectangle vis = getVisibleRect();
		zoom(1, vis.x + vis.width / 2);
	}

	/** Zooms out one step around the centre of the visible area. */
	public void zoomOut() {
		Rectangle vis = getVisibleRect();
		zoom(-1, vis.x + vis.width / 2);
	}

	/**
	 * Changes the bar width by one zoom step, keeping the bar under
	 * {@code anchorX} (panel coordinate) at the same on-screen position.
	 */
	public void zoom(int direction, int anchorX) {
		if (direction == 0) return;
		int old = barWidth;
		int nw = direction > 0
				? Math.max(old + 1, Math.round(old * ZOOM_FACTOR))
				: Math.min(old - 1, Math.round(old / ZOOM_FACTOR));
		nw = Math.max(MIN_BAR_WIDTH, Math.min(MAX_BAR_WIDTH, nw));
		if (nw == old) return;

		final JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
		final int left = computeMarginLeft();
		final double frac = (anchorX - left) / (double) old;
		final int viewOffset = (vp != null) ? anchorX - vp.getViewPosition().x : 0;

		setBarWidth(nw);

		if (vp != null && frac >= 0) {
			SwingUtilities.invokeLater(() -> {
				Container sp = vp.getParent();
				if (sp != null) {
					sp.validate();
				} else {
					vp.validate();
				}
				int newAnchor = left + (int) Math.round(frac * barWidth);
				int maxX = Math.max(0, getWidth() - vp.getExtentSize().width);
				int nx = Math.max(0, Math.min(newAnchor - viewOffset, maxX));
				vp.setViewPosition(new Point(nx, vp.getViewPosition().y));
			});
		}
	}

	/** Chooses the bar width so that all bars fit in the visible width. */
	public void fitToWidth() {
		if (yValues.length == 0) return;
		Rectangle vis = getVisibleRect();
		int available = vis.width - computeMarginLeft() - MARGIN_RIGHT;
		if (available <= 0) return;
		setBarWidth(Math.max(MIN_BAR_WIDTH, available / yValues.length));
	}

	// ---------------------------------------------------------------
	// Export
	// ---------------------------------------------------------------

	/** Renders the whole histogram to an image at the given scale (2 = HiDPI). */
	public BufferedImage renderToImage(double scale) {
		int w = Math.max(1, getWidth());
		int h = Math.max(1, getHeight());
		BufferedImage image = new BufferedImage(
				(int) Math.ceil(w * scale), (int) Math.ceil(h * scale), BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = image.createGraphics();
		try {
			applyRenderingHints(g2);
			g2.scale(scale, scale);
			exporting = true;
			printAll(g2);
		} finally {
			exporting = false;
			g2.dispose();
		}
		return image;
	}

	public void exportAsImage() {
		String path = promptForFilePath("png", "PNG image");
		if (path == null) return;
		try {
			ImageIO.write(renderToImage(2.0), "png", new File(path));
		} catch (IOException e) {
			showErrorDialog("Failed to save PNG image: " + e.getMessage());
		}
	}

	public void exportAsCSV() {
		String path = promptForFilePath("csv", "CSV file");
		if (path == null) return;
		try (PrintWriter pw = new PrintWriter(new File(path))) {
			pw.println(csvQuote(title));
			pw.println(csvQuote(xAxisName) + "," + csvQuote(yAxisName) + ",percent");
			for (int i = 0; i < yValues.length; i++) {
				pw.println(csvQuote(getXValueAsString(i)) + "," + yValues[i] + ","
						+ String.format(Locale.US, "%.4f", percentOf(yValues[i])));
			}
		} catch (IOException e) {
			showErrorDialog("Failed to save CSV file: " + e.getMessage());
		}
	}

	/** Copies the histogram image to the system clipboard. */
	public void copyImageToClipboard() {
		try {
			final BufferedImage image = renderToImage(2.0);
			Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
			cb.setContents(new Transferable() {
				@Override
				public DataFlavor[] getTransferDataFlavors() {
					return new DataFlavor[] { DataFlavor.imageFlavor };
				}

				@Override
				public boolean isDataFlavorSupported(DataFlavor flavor) {
					return DataFlavor.imageFlavor.equals(flavor);
				}

				@Override
				public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
					if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
					return image;
				}
			}, null);
		} catch (Exception e) {
			showErrorDialog("Failed to copy image: " + e.getMessage());
		}
	}

	private static String csvQuote(String s) {
		if (s == null) return "";
		if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
			return "\"" + s.replace("\"", "\"\"") + "\"";
		}
		return s;
	}

	// ---------------------------------------------------------------
	// Hover support
	// ---------------------------------------------------------------

	private void updateHover(Point p) {
		mousePoint = p;
		int idx = (p == null) ? -1 : getBarIndexAt(p.x, p.y);
		boolean changed = idx != hoveredBarIndex;
		hoveredBarIndex = idx;
		if (idx >= 0 || changed) {
			repaint(getVisibleRect());
		}
		if (changed) {
			fireHover(idx);
		}
	}

	private void fireHover(int idx) {
		String label = idx >= 0 ? getXValueAsString(idx) : null;
		int value = idx >= 0 ? yValues[idx] : 0;
		for (BarHoverListener l : hoverListeners) {
			l.barHovered(idx, label, value);
		}
	}

	/** Column-based hit test: any point inside the plot column of a bar hits it. */
	private int getBarIndexAt(int mx, int my) {
		int n = yValues.length;
		if (n == 0 || plotBottom <= plotTop) return -1;
		if (my < plotTop - 2 || my > plotBottom) return -1;
		if (mx < getVisibleRect().x + marginLeft) return -1; // under the sticky axis
		int rel = mx - plotLeft;
		if (rel < 0) return -1;
		int i = rel / barWidth;
		return i < n ? i : -1;
	}

	// ---------------------------------------------------------------
	// Painting
	// ---------------------------------------------------------------

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			applyRenderingHints(g2);
			Rectangle vis = exporting
					? new Rectangle(0, 0, getWidth(), getHeight())
					: getVisibleRect();

			if (yValues.length == 0) {
				drawEmptyState(g2, vis);
				return;
			}

			Rectangle clip = g2.getClipBounds();
			if (clip == null) clip = new Rectangle(0, 0, getWidth(), getHeight());

			FontMetrics fm = g2.getFontMetrics(FONT_NORMAL);
			yScale = buildYScale();
			int maxLabelW = showBarLabels ? maxLabelWidth(fm) : 0;
			boolean rotate = showBarLabels
					&& (barWidth < LABEL_ROTATION_THRESHOLD || maxLabelW > barWidth - 6);

			marginLeft = computeMarginLeft();
			int marginBottom = computeMarginBottom(fm, rotate, maxLabelW);
			plotLeft = marginLeft;
			plotTop = MARGIN_TOP;
			plotBottom = Math.max(plotTop + 10, getHeight() - marginBottom);
			plotRight = plotLeft + yValues.length * barWidth;
			plotHeight = plotBottom - plotTop;

			int axisX = vis.x + marginLeft; // sticky Y axis
			int axisRight = Math.max(plotRight, getWidth() - MARGIN_RIGHT);

			drawGrid(g2, axisX, axisRight);
			drawMeanLine(g2, vis, axisRight);
			drawBars(g2, clip, fm, rotate);
			drawYAxis(g2, vis, axisX, fm);

			// X axis line (after the gutter so it starts at the sticky axis)
			g2.setColor(AXIS_COLOR);
			g2.setStroke(AXIS_STROKE);
			g2.drawLine(axisX, plotBottom, axisRight, plotBottom);

			drawHeader(g2, vis);
			drawXAxisName(g2, vis, axisX);
			drawHoverCard(g2, vis);
		} finally {
			g2.dispose();
		}
	}

	private void applyRenderingHints(Graphics2D g2) {
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	}

	private void drawEmptyState(Graphics2D g2, Rectangle vis) {
		g2.setFont(FONT_BOLD);
		g2.setColor(TEXT_MUTED);
		String msg = "No data to display";
		FontMetrics fm = g2.getFontMetrics();
		g2.drawString(msg,
				vis.x + (vis.width - fm.stringWidth(msg)) / 2,
				vis.y + vis.height / 2);
	}

	private void drawGrid(Graphics2D g2, int axisX, int axisRight) {
		g2.setColor(GRID_COLOR);
		g2.setStroke(GRID_STROKE);
		for (double tv : yScale.ticks) {
			if (tv <= 0) continue;
			int y = yPos(tv);
			g2.drawLine(axisX, y, axisRight, y);
		}
	}

	private void drawMeanLine(Graphics2D g2, Rectangle vis, int axisRight) {
		if (!showMeanLine || yValues.length < 2 || mean <= 0) return;
		int y = yPos(mean);
		g2.setColor(MEAN_LINE_COLOR);
		g2.setStroke(MEAN_STROKE);
		g2.drawLine(plotLeft, y, axisRight, y);

		g2.setFont(FONT_SMALL);
		FontMetrics fm = g2.getFontMetrics();
		String text = "mean = " + decimalFormat.format(mean);
		int right = Math.min(axisRight, vis.x + vis.width - MARGIN_RIGHT);
		g2.drawString(text, right - fm.stringWidth(text) - 4, y - 4);
	}

	private void drawBars(Graphics2D g2, Rectangle clip, FontMetrics fm, boolean rotate) {
		int n = yValues.length;
		int first = Math.max(0, (clip.x - plotLeft) / barWidth - 1);
		int last = Math.min(n - 1, (clip.x + clip.width - plotLeft) / barWidth + 1);
		if (first > last) return;

		int gap = barGap();
		int effW = Math.max(1, barWidth - gap);
		boolean fancy = effW >= 4;
		boolean valuesVisible = showBarValues && barWidth >= MIN_BAR_WIDTH_FOR_VALUES;

		int labelSkip = showBarLabels ? calculateLabelSkip(fm, rotate) : 1;
		int valueSkip = valuesVisible ? calculateValueSkip(fm) : 1;

		for (int i = first; i <= last; i++) {
			int barX = plotLeft + i * barWidth;
			int h = barPixelHeight(yValues[i]);
			int barY = plotBottom - h;
			boolean hovered = (i == hoveredBarIndex);

			if (h > 0) {
				Color base = barColors[i];
				if (fancy) {
					Shape shape = barShape(barX, barY, effW, h);
					g2.setPaint(new GradientPaint(
							barX, barY, lighten(base, hovered ? 0.45f : 0.30f),
							barX, plotBottom, hovered ? lighten(base, 0.12f) : base));
					g2.fill(shape);
					g2.setColor(hovered ? HOVER_BORDER_COLOR : BAR_BORDER_COLOR);
					g2.setStroke(hovered ? HOVER_STROKE : BAR_STROKE);
					g2.draw(shape);
				} else {
					g2.setColor(hovered ? lighten(base, 0.35f) : base);
					g2.fillRect(barX, barY, effW, h);
					if (hovered) {
						g2.setColor(HOVER_BORDER_COLOR);
						g2.setStroke(BAR_STROKE);
						g2.drawRect(barX, barY, effW, h);
					}
				}
			}

			if (showBarLabels && i % labelSkip == 0) {
				drawBarLabel(g2, fm, i, barX, effW, rotate);
			}
			if (valuesVisible && h > 0 && i % valueSkip == 0) {
				drawBarValue(g2, fm, i, barX, effW, barY, h);
			}
		}
	}

	private Shape barShape(int x, int y, int w, int h) {
		int r = Math.min(4, Math.min(w / 2, h));
		if (r < 2) {
			return new Rectangle(x, y, w, h);
		}
		Path2D.Float p = new Path2D.Float();
		p.moveTo(x, y + h);
		p.lineTo(x, y + r);
		p.quadTo(x, y, x + r, y);
		p.lineTo(x + w - r, y);
		p.quadTo(x + w, y, x + w, y + r);
		p.lineTo(x + w, y + h);
		p.closePath();
		return p;
	}

	/** Gap between adjacent bars, proportional to the bar width. */
	private int barGap() {
		if (barWidth <= 2) return 0;
		if (barWidth < 8) return 1;
		return Math.max(1, Math.min(6, barWidth / 6));
	}

	private void drawBarLabel(Graphics2D g2, FontMetrics fm, int i, int barX,
			int effW, boolean rotate) {
		String text = getXValueAsString(i);
		boolean hovered = (i == hoveredBarIndex);
		int tickX = barX + effW / 2;

		// small tick under the axis
		g2.setColor(AXIS_COLOR);
		g2.setStroke(TICK_STROKE);
		g2.drawLine(tickX, plotBottom, tickX, plotBottom + X_LABEL_GAP);

		g2.setFont(hovered ? FONT_BOLD : FONT_NORMAL);
		g2.setColor(hovered ? TEXT_COLOR : TICK_LABEL_COLOR);
		int textTop = plotBottom + X_LABEL_GAP + 3;

		if (rotate) {
			// Rotated 90 degrees (reads top-to-bottom, starts right under the tick).
			// After rotate(PI/2) the glyphs extend to the right of the baseline by
			// "ascent", so shifting the anchor left by ascent/2 centres the text.
			String shown = ellipsize(text, fm, MAX_ROTATED_LABEL_PX);
			AffineTransform old = g2.getTransform();
			g2.translate(tickX - fm.getAscent() / 2 + 1, textTop);
			g2.rotate(Math.PI / 2);
			g2.drawString(shown, 0, 0);
			g2.setTransform(old);
		} else {
			FontMetrics m = g2.getFontMetrics();
			int tw = m.stringWidth(text);
			int tx = tickX - tw / 2;
			tx = Math.max(plotLeft, Math.min(tx, plotRight - tw));
			g2.drawString(text, tx, textTop + m.getAscent());
		}
	}

	private void drawBarValue(Graphics2D g2, FontMetrics fm, int i, int barX,
			int effW, int barY, int h) {
		String text = formatValue(yValues[i]);
		g2.setFont(FONT_NORMAL);
		int tw = fm.stringWidth(text);
		int tx = barX + (effW - tw) / 2;
		int ty = barY - 4;
		// Not enough head-room above the bar? Put the value inside it.
		if (ty - fm.getAscent() < plotTop - 14 && h > fm.getHeight() + 8) {
			ty = barY + fm.getAscent() + 4;
		}
		g2.setColor(i == hoveredBarIndex ? TEXT_COLOR : TICK_LABEL_COLOR);
		g2.drawString(text, tx, ty);
	}

	private void drawYAxis(Graphics2D g2, Rectangle vis, int axisX, FontMetrics fmNormal) {
		// Sticky gutter: hides bars scrolled underneath the axis
		g2.setColor(getBackground());
		g2.fillRect(vis.x, vis.y, marginLeft, vis.height);

		if (vis.x > 0 && !exporting) {
			g2.setPaint(new GradientPaint(axisX, 0, new Color(0, 0, 0, 26),
					axisX + 10, 0, new Color(0, 0, 0, 0)));
			g2.fillRect(axisX, plotTop - 10, 10, plotHeight + 10);
		}

		// Axis line
		g2.setColor(AXIS_COLOR);
		g2.setStroke(AXIS_STROKE);
		g2.drawLine(axisX, plotTop - 6, axisX, plotBottom);

		// Ticks + labels
		g2.setFont(FONT_SMALL);
		FontMetrics fm = g2.getFontMetrics();
		g2.setStroke(TICK_STROKE);
		for (double tv : yScale.ticks) {
			int y = yPos(tv);
			g2.setColor(AXIS_COLOR);
			g2.drawLine(axisX - TICK_LEN, y, axisX, y);
			String label = compactNumber(tv);
			g2.setColor(TICK_LABEL_COLOR);
			g2.drawString(label, axisX - TICK_LEN - 4 - fm.stringWidth(label),
					y + fm.getAscent() / 2 - 1);
		}

		// Axis name (rotated, sticky)
		if (!yAxisName.isEmpty()) {
			g2.setFont(FONT_BOLD);
			FontMetrics bfm = g2.getFontMetrics();
			AffineTransform old = g2.getTransform();
			g2.translate(vis.x + 4 + bfm.getAscent(), plotTop + plotHeight / 2);
			g2.rotate(-Math.PI / 2);
			g2.setColor(TEXT_COLOR);
			g2.drawString(yAxisName, -bfm.stringWidth(yAxisName) / 2, 0);
			g2.setTransform(old);
		}
	}

	private void drawHeader(Graphics2D g2, Rectangle vis) {
		int headerH = MARGIN_TOP - 14;
		g2.setColor(getBackground());
		g2.fillRect(vis.x, 0, vis.width, headerH);

		int cx = vis.x + vis.width / 2;

		g2.setFont(FONT_TITLE);
		g2.setColor(TEXT_COLOR);
		FontMetrics tf = g2.getFontMetrics();
		String t = ellipsize(title, tf, vis.width - 20);
		int y = 8 + tf.getAscent();
		g2.drawString(t, cx - tf.stringWidth(t) / 2, y);

		g2.setFont(FONT_SMALL);
		g2.setColor(TEXT_MUTED);
		FontMetrics sf = g2.getFontMetrics();
		String stats = statsText(sf, vis.width - 20);
		g2.drawString(stats, cx - sf.stringWidth(stats) / 2, y + 6 + sf.getAscent());
	}

	private String statsText(FontMetrics fm, int maxW) {
		String sep = "   \u00B7   ";
		String full = "N = " + numberFormat.format(yValues.length)
				+ sep + "Sum = " + numberFormat.format(sum)
				+ sep + "Min = " + numberFormat.format(minY)
				+ sep + "Max = " + numberFormat.format(maxY)
				+ sep + "Mean = " + decimalFormat.format(mean)
				+ sep + "Median = " + decimalFormat.format(median)
				+ sep + "Std = " + decimalFormat.format(stdDev);
		if (fm.stringWidth(full) <= maxW) return full;

		String shorter = "N = " + numberFormat.format(yValues.length)
				+ sep + "Sum = " + numberFormat.format(sum)
				+ sep + "Max = " + numberFormat.format(maxY)
				+ sep + "Mean = " + decimalFormat.format(mean);
		if (fm.stringWidth(shorter) <= maxW) return shorter;
		return ellipsize(shorter, fm, maxW);
	}

	private void drawXAxisName(Graphics2D g2, Rectangle vis, int axisX) {
		if (xAxisName.isEmpty()) return;
		g2.setFont(FONT_BOLD);
		g2.setColor(TEXT_COLOR);
		FontMetrics fm = g2.getFontMetrics();
		int axisRight = Math.max(plotRight, getWidth() - MARGIN_RIGHT);
		int left = axisX;
		int right = Math.min(vis.x + vis.width - MARGIN_RIGHT, axisRight);
		if (right <= left) return;
		String t = ellipsize(xAxisName, fm, right - left);
		int x = left + (right - left - fm.stringWidth(t)) / 2;
		g2.drawString(t, x, getHeight() - 8);
	}

	private void drawHoverCard(Graphics2D g2, Rectangle vis) {
		if (exporting || hoveredBarIndex < 0 || mousePoint == null) return;
		int i = hoveredBarIndex;

		FontMetrics bf = g2.getFontMetrics(FONT_BOLD);
		FontMetrics nf = g2.getFontMetrics(FONT_NORMAL);

		String head = ellipsize(getXValueAsString(i), bf, 260);
		String[] lines = {
				(yAxisName.isEmpty() ? "Value" : yAxisName) + ": " + numberFormat.format(yValues[i]),
				"Share: " + String.format(Locale.US, "%.2f%%", percentOf(yValues[i])),
				"Rank: " + numberFormat.format(rankByValue[i]) + " of "
						+ numberFormat.format(yValues.length) };

		int pad = 10;
		int swatch = 10;
		int w = bf.stringWidth(head) + swatch + 6;
		for (String l : lines) {
			w = Math.max(w, nf.stringWidth(l));
		}
		w += pad * 2;
		int h = pad + bf.getHeight() + 4 + lines.length * nf.getHeight() + pad;

		int x = mousePoint.x + 16;
		int y = mousePoint.y + 18;
		if (x + w > vis.x + vis.width - 4) x = mousePoint.x - w - 12;
		if (y + h > vis.y + vis.height - 4) y = mousePoint.y - h - 12;
		x = Math.max(vis.x + 4, x);
		y = Math.max(vis.y + 4, y);

		g2.setColor(CARD_SHADOW);
		g2.fill(new RoundRectangle2D.Float(x + 2, y + 3, w, h, 10, 10));
		RoundRectangle2D card = new RoundRectangle2D.Float(x, y, w, h, 10, 10);
		g2.setColor(CARD_BG);
		g2.fill(card);
		g2.setColor(CARD_BORDER);
		g2.setStroke(TICK_STROKE);
		g2.draw(card);

		int ty = y + pad + bf.getAscent();
		g2.setColor(barColors[i]);
		g2.fillRoundRect(x + pad, ty - swatch + 1, swatch, swatch, 3, 3);
		g2.setColor(BAR_BORDER_COLOR);
		g2.drawRoundRect(x + pad, ty - swatch + 1, swatch, swatch, 3, 3);

		g2.setFont(FONT_BOLD);
		g2.setColor(TEXT_COLOR);
		g2.drawString(head, x + pad + swatch + 6, ty);

		g2.setFont(FONT_NORMAL);
		g2.setColor(TEXT_MUTED);
		int ly = ty + 4 + nf.getHeight();
		for (String l : lines) {
			g2.drawString(l, x + pad, ly);
			ly += nf.getHeight();
		}
	}

	// ---------------------------------------------------------------
	// Layout / scale helpers
	// ---------------------------------------------------------------

	private double transform(double v) {
		if (v <= 0) return 0;
		return logScale ? Math.log10(v + 1) : v;
	}

	private int barPixelHeight(int v) {
		if (v <= 0 || yScale == null || yScale.top <= 0) return 0;
		int h = (int) Math.round(transform(v) / yScale.top * plotHeight);
		return Math.max(1, h);
	}

	private int yPos(double v) {
		if (yScale == null || yScale.top <= 0) return plotBottom;
		return plotBottom - (int) Math.round(transform(v) / yScale.top * plotHeight);
	}

	/** Builds "nice" tick values (1-2-5 steps, or decades for log scale) with headroom. */
	private YScale buildYScale() {
		YScale s = new YScale();
		if (maxY <= 0) {
			s.ticks = new double[] { 0 };
			s.top = 1;
			return s;
		}
		if (logScale) {
			s.top = Math.log10(maxY + 1) * 1.08;
			int decades = (int) Math.ceil(Math.log10(maxY));
			boolean minor = decades <= 3;
			int cap = 2 + (decades + 1) * (minor ? 3 : 1);
			double[] tmp = new double[cap];
			int n = 0;
			tmp[n++] = 0;
			for (int d = 0; d <= decades; d++) {
				double base = Math.pow(10, d);
				double[] mult = minor ? new double[] { 1, 2, 5 } : new double[] { 1 };
				for (double m : mult) {
					double v = base * m;
					if (transform(v) <= s.top && n < cap) {
						tmp[n++] = v;
					}
				}
			}
			s.ticks = Arrays.copyOf(tmp, n);
			return s;
		}
		double range = maxY * 1.06;
		double step = Math.max(1, niceStep(range / Y_TICK_TARGET));
		int count = (int) Math.ceil(range / step);
		s.top = count * step;
		s.ticks = new double[count + 1];
		for (int i = 0; i <= count; i++) {
			s.ticks[i] = i * step;
		}
		return s;
	}

	private static double niceStep(double raw) {
		if (raw <= 0) return 1;
		double exp = Math.floor(Math.log10(raw));
		double base = Math.pow(10, exp);
		double frac = raw / base;
		double nice;
		if (frac <= 1) nice = 1;
		else if (frac <= 2) nice = 2;
		else if (frac <= 5) nice = 5;
		else nice = 10;
		return nice * base;
	}

	private int computeMarginLeft() {
		FontMetrics sm = getFontMetrics(FONT_SMALL);
		int maxW = 0;
		for (double tv : buildYScale().ticks) {
			maxW = Math.max(maxW, sm.stringWidth(compactNumber(tv)));
		}
		int nameW = yAxisName.isEmpty() ? 0 : getFontMetrics(FONT_BOLD).getHeight() + 6;
		return Math.max(MIN_MARGIN_LEFT, 6 + nameW + maxW + 4 + TICK_LEN + 4);
	}

	private int computeMarginBottom(FontMetrics fm, boolean rotate, int maxLabelW) {
		int labelArea;
		if (!showBarLabels) {
			labelArea = 10;
		} else if (rotate) {
			labelArea = X_LABEL_GAP + 4 + Math.min(maxLabelW, MAX_ROTATED_LABEL_PX) + 6;
		} else {
			labelArea = X_LABEL_GAP + 4 + fm.getHeight() + 4;
		}
		int nameArea = xAxisName.isEmpty() ? 6 : getFontMetrics(FONT_BOLD).getHeight() + 10;
		return labelArea + nameArea;
	}

	private int maxLabelWidth(FontMetrics fm) {
		if (maxLabelWidthCache < 0) {
			int m = 0;
			for (int i = 0; i < yValues.length; i++) {
				m = Math.max(m, fm.stringWidth(getXValueAsString(i)));
			}
			maxLabelWidthCache = m;
		}
		return maxLabelWidthCache;
	}

	private int calculateLabelSkip(FontMetrics fm, boolean rotate) {
		int needed = rotate ? fm.getHeight() + 2 : maxLabelWidth(fm) + 8;
		return niceSkip((int) Math.ceil(needed / (double) barWidth));
	}

	private int calculateValueSkip(FontMetrics fm) {
		int w = fm.stringWidth(formatValue(maxY));
		if (showPercent) w = Math.max(w, fm.stringWidth("100.0%"));
		return niceSkip((int) Math.ceil((w + 6) / (double) barWidth));
	}

	/** Rounds a skip interval up to 1, 2, 5, 10, 20, 50, ... so labels look regular. */
	private static int niceSkip(int skip) {
		if (skip <= 1) return 1;
		int mag = 1;
		while (mag * 10 < skip) {
			mag *= 10;
		}
		for (int m : new int[] { 1, 2, 5, 10 }) {
			if (m * mag >= skip) return m * mag;
		}
		return skip;
	}

	// ---------------------------------------------------------------
	// Formatting helpers
	// ---------------------------------------------------------------

	private String formatValue(int v) {
		return showPercent
				? String.format(Locale.US, "%.1f%%", percentOf(v))
				: numberFormat.format(v);
	}

	private double percentOf(int v) {
		return sum > 0 ? 100.0 * v / sum : 0;
	}

	private String compactNumber(double v) {
		double a = Math.abs(v);
		if (a >= 1_000_000_000) return trimDecimal(v / 1e9) + "B";
		if (a >= 1_000_000) return trimDecimal(v / 1e6) + "M";
		if (a >= 10_000) return trimDecimal(v / 1e3) + "k";
		if (v == Math.rint(v)) return numberFormat.format((long) v);
		return decimalFormat.format(v);
	}

	private static String trimDecimal(double v) {
		String s = String.format(Locale.US, "%.1f", v);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	private static String ellipsize(String s, FontMetrics fm, int maxW) {
		if (s == null) return "";
		if (maxW <= 0 || fm.stringWidth(s) <= maxW) return s;
		String ell = "\u2026";
		int ew = fm.stringWidth(ell);
		int end = s.length();
		while (end > 0 && fm.stringWidth(s.substring(0, end)) + ew > maxW) {
			end--;
		}
		return end <= 0 ? ell : s.substring(0, end) + ell;
	}

	private String getXValueAsString(int i) {
		int value = xLabels[i];
		if (mapXValuesToString != null) {
			String name = mapXValuesToString.get(value);
			if (name != null) return name;
		}
		return Integer.toString(value);
	}

	private static String chooseFontFamily() {
		try {
			Set<String> available = new HashSet<>(Arrays.asList(
					GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
			for (String f : new String[] { "Segoe UI", "Helvetica Neue", "DejaVu Sans", "Liberation Sans" }) {
				if (available.contains(f)) return f;
			}
		} catch (Throwable ignored) {
			// headless or font system unavailable: fall through
		}
		return Font.SANS_SERIF;
	}

	// ---------------------------------------------------------------
	// Colours
	// ---------------------------------------------------------------

	private void generateBarColors() {
		int n = yValues.length;
		barColors = new Color[n];
		for (int i = 0; i < n; i++) {
			double t = maxY > 0 ? yValues[i] / (double) maxY : 0;
			switch (colorScheme) {
			case OCEAN:
				barColors[i] = OCEAN_BASE;
				break;
			case SLATE:
				barColors[i] = SLATE_BASE;
				break;
			case HEAT_BY_VALUE:
				barColors[i] = interpolate(HEAT_STOPS, t);
				break;
			case BLUES_BY_VALUE:
				barColors[i] = interpolate(BLUE_STOPS, t);
				break;
			case RAINBOW:
			default:
				barColors[i] = Color.getHSBColor((i * HUE_STEP) % 1.0f, SATURATION, BRIGHTNESS);
			}
		}
	}

	private static Color interpolate(Color[] stops, double t) {
		t = Math.max(0, Math.min(1, t));
		double pos = t * (stops.length - 1);
		int i = (int) Math.floor(pos);
		if (i >= stops.length - 1) return stops[stops.length - 1];
		double f = pos - i;
		Color a = stops[i];
		Color b = stops[i + 1];
		return new Color(mix(a.getRed(), b.getRed(), f),
				mix(a.getGreen(), b.getGreen(), f),
				mix(a.getBlue(), b.getBlue(), f));
	}

	private static int mix(int a, int b, double f) {
		return clamp((int) Math.round(a + (b - a) * f));
	}

	private static Color lighten(Color c, float f) {
		return new Color(
				clamp(c.getRed() + Math.round((255 - c.getRed()) * f)),
				clamp(c.getGreen() + Math.round((255 - c.getGreen()) * f)),
				clamp(c.getBlue() + Math.round((255 - c.getBlue()) * f)));
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(255, v));
	}

	// ---------------------------------------------------------------
	// Sizing / Scrollable
	// ---------------------------------------------------------------

	@Override
	public Dimension getPreferredSize() {
		int w = computeMarginLeft() + yValues.length * barWidth + MARGIN_RIGHT;
		return new Dimension(w, PREFERRED_HEIGHT);
	}

	@Override
	public Dimension getPreferredScrollableViewportSize() {
		Dimension d = getPreferredSize();
		return new Dimension(Math.min(d.width, 900), PREFERRED_HEIGHT);
	}

	@Override
	public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
		return orientation == SwingConstants.HORIZONTAL ? Math.max(16, barWidth * 3) : 16;
	}

	@Override
	public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
		return orientation == SwingConstants.HORIZONTAL
				? Math.max(1, visibleRect.width - marginLeft - 20)
				: visibleRect.height;
	}

	@Override
	public boolean getScrollableTracksViewportWidth() {
		Container p = getParent();
		return p instanceof JViewport && ((JViewport) p).getWidth() > getPreferredSize().width;
	}

	@Override
	public boolean getScrollableTracksViewportHeight() {
		return true;
	}

	// ---------------------------------------------------------------
	// Array / data utilities
	// ---------------------------------------------------------------

	private static void sortParallel(int[] primary, int[] secondary, boolean ascending) {
		Integer[] indices = new Integer[primary.length];
		for (int i = 0; i < indices.length; i++) {
			indices[i] = i;
		}
		Arrays.sort(indices, (a, b) -> {
			int cmp = Integer.compare(primary[a], primary[b]);
			return ascending ? cmp : -cmp;
		});
		int[] tmpP = new int[primary.length];
		int[] tmpS = new int[secondary.length];
		for (int i = 0; i < indices.length; i++) {
			tmpP[i] = primary[indices[i]];
			tmpS[i] = secondary[indices[i]];
		}
		System.arraycopy(tmpP, 0, primary, 0, primary.length);
		System.arraycopy(tmpS, 0, secondary, 0, secondary.length);
	}

	private static int findMaxLabelFromData(Vector<List<Object>> data, int index) {
		int maxLabel = 0;
		if (data == null) return 0;
		for (List<Object> record : data) {
			if (record != null && index < record.size()) {
				int value = convertToInt(record.get(index));
				if (value > maxLabel) maxLabel = value;
			}
		}
		return maxLabel;
	}

	private static void populateValuesFromData(Vector<List<Object>> data,
			int index, int[] valuesArray) {
		if (data == null) return;
		for (List<Object> record : data) {
			if (record != null && index < record.size()) {
				int value = convertToInt(record.get(index));
				if (value >= 0 && value < valuesArray.length) {
					valuesArray[value]++;
				}
			}
		}
	}

	private static int convertToInt(Object value) {
		if (value instanceof Number) return ((Number) value).intValue();
		if (value instanceof Boolean) return ((Boolean) value) ? 1 : 0;
		return -1;
	}

	// ---------------------------------------------------------------
	// File-chooser helpers
	// ---------------------------------------------------------------

	private String promptForFilePath(String extension, String description) {
		try {
			File initialDir = getInitialDirectory();
			JFileChooser fc = (initialDir != null) ? new JFileChooser(initialDir) : new JFileChooser();
			fc.setDialogTitle("Save as " + extension.toUpperCase(Locale.ROOT));
			fc.setFileFilter(new FileNameExtensionFilter(
					description + " (*." + extension + ")", extension));
			fc.setSelectedFile(new File(fc.getCurrentDirectory(), suggestFileName(extension)));

			while (true) {
				if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
				File file = fc.getSelectedFile();
				if (!file.getName().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
					file = new File(file.getPath() + "." + extension);
				}
				if (file.exists()) {
					int r = JOptionPane.showConfirmDialog(this,
							"\"" + file.getName() + "\" already exists. Overwrite?",
							"Confirm overwrite", JOptionPane.YES_NO_CANCEL_OPTION,
							JOptionPane.WARNING_MESSAGE);
					if (r == JOptionPane.CANCEL_OPTION || r == JOptionPane.CLOSED_OPTION) return null;
					if (r == JOptionPane.NO_OPTION) continue;
				}
				PreferencesManager.getInstance().setOutputFilePath(file.getParent());
				return file.getPath();
			}
		} catch (Exception e) {
			showErrorDialog("Failed to open save dialog: " + e.getMessage());
		}
		return null;
	}

	private String suggestFileName(String extension) {
		String base = title.trim().replaceAll("[^A-Za-z0-9._-]+", "_");
		if (base.isEmpty() || base.equals("_")) base = "histogram";
		return base + "." + extension;
	}

	private static File getInitialDirectory() {
		try {
			String previousPath = PreferencesManager.getInstance().getOutputFilePath();
			if (previousPath != null) return new File(previousPath);

			URL main = MainTestApriori_simple_saveToFile.class
					.getResource("MainTestApriori_saveToFile.class");
			if (main != null && "file".equalsIgnoreCase(main.getProtocol())) {
				return new File(main.getPath()).getParentFile();
			}
		} catch (Exception ignored) {
			// fall back to the default directory
		}
		return null;
	}

	private void showErrorDialog(String message) {
		JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
	}
}