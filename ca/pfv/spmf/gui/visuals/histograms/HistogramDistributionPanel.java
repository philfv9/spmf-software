package ca.pfv.spmf.gui.visuals.histograms;

/*
 * 
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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ToolTipManager;

import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.test.MainTestApriori_simple_saveToFile;

/**
 * A panel displaying a frequency histogram with configurable axes, bar labels,
 * grid lines, hover effects, and export capabilities (PNG and CSV).
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class HistogramDistributionPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	// ---------------------------------------------------------------
	// Constants
	// ---------------------------------------------------------------

	private static final int MIN_BAR_WIDTH_FOR_LABELS = 15;
	private static final int MARGIN_LEFT = 70;
	private static final int MARGIN_RIGHT = 25;
	private static final int MARGIN_TOP = 70;
	private static final int MARGIN_BOTTOM = 100;
	private static final int Y_GRID_LINE_COUNT = 5;
	private static final int DEFAULT_PANEL_WIDTH = 600;
	private static final int DEFAULT_PANEL_HEIGHT = 50;
	private static final int AUTO_WIDTH_BAR_LIMIT = 600;
	private static final int MIN_BAR_WIDTH = 1;
	private static final int DEFAULT_BAR_WIDTH = 2;
	private static final int BAR_GAP = 1;
	private static final int LABEL_ROTATION_THRESHOLD = 40;

	/** Gap in pixels between the X-axis line and the start of bar labels below it */
	private static final int X_LABEL_GAP = 4;

	/** Gap in pixels between the stats summary line and the top of the chart area */
	private static final int STATS_GAP = 18;

	// --- Fonts (unchanged) ---
	private static final Font FONT_NORMAL = new Font("Arial", Font.PLAIN, 10);
	private static final Font FONT_BOLD = new Font("Arial", Font.BOLD, 12);
	private static final Font FONT_TITLE = new Font("Arial", Font.BOLD, 16);
	private static final Font FONT_STATS = new Font("Arial", Font.PLAIN, 10);

	// --- Colors (unchanged bar colors, polished chrome) ---
	private static final Color GRID_COLOR = new Color(220, 220, 220);
	private static final Color AXIS_COLOR = new Color(60, 60, 60);
	private static final Color BAR_BORDER_COLOR = new Color(100, 100, 100);
	private static final Color TICK_LABEL_COLOR = new Color(90, 90, 90);
	private static final Color HOVER_BORDER_COLOR = new Color(40, 40, 40);

	// --- HSB parameters (unchanged) ---
	private static final float HUE_STEP = 0.618033988749895f;
	private static final float SATURATION = 0.70f;
	private static final float BRIGHTNESS = 0.85f;

	// --- Strokes ---
	private static final Stroke AXIS_STROKE = new BasicStroke(1.5f);
	private static final Stroke GRID_STROKE = new BasicStroke(0.5f);
	private static final Stroke TICK_STROKE = new BasicStroke(1.0f);
	private static final Stroke BAR_STROKE = new BasicStroke(0.5f);
	private static final Stroke HOVER_STROKE = new BasicStroke(1.5f);

	// ---------------------------------------------------------------
	// Sort orders
	// ---------------------------------------------------------------

	public enum Order {
		ASCENDING_Y, DESCENDING_Y, ASCENDING_X, DESCENDING_X
	}

	// ---------------------------------------------------------------
	// Instance fields
	// ---------------------------------------------------------------

	private String xAxisName;
	private String yAxisName;
	private int[] yValues;
	private int[] xLabels;
	private int maxX;
	private int maxY;
	private double scaleFactor;
	private Order selectedOrder;
	private boolean showBarLabels;
	private boolean showBarValues;
	private String title;
	private int barWidth = DEFAULT_BAR_WIDTH;
	private Map<Integer, String> mapXValuesToString;
	private Color[] barColors;
	private int hoveredBarIndex = -1;
	private final NumberFormat numberFormat;

	// ---------------------------------------------------------------
	// Constructors
	// ---------------------------------------------------------------

	public HistogramDistributionPanel(int[] yValues, int[] xLabels, String title,
			boolean showBarLabels, boolean showBarValues,
			String xAxisName, String yAxisName,
			Map<Integer, String> mapXValuesToString, Order order) {
		validateArrays(yValues, xLabels);
		this.mapXValuesToString = mapXValuesToString;
		this.numberFormat = NumberFormat.getNumberInstance(Locale.US);
		initializeHistogram(yValues, xLabels, title, showBarLabels, showBarValues,
				xAxisName, yAxisName, order);
	}

	public HistogramDistributionPanel(Vector<List<Object>> data, int width, int height,
			int index, String title,
			boolean showBarLabels, boolean showBarValues,
			String xAxisName, String yAxisName, Order order) {
		super();
		this.numberFormat = NumberFormat.getNumberInstance(Locale.US);

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
	// Initialisation helpers
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
		this.yValues = yValues;
		this.xLabels = xLabels;
		this.title = title;
		this.showBarLabels = showBarLabels;
		this.showBarValues = showBarValues;
		this.xAxisName = xAxisName;
		this.yAxisName = yAxisName;

		maxY = findMax(yValues);

		if (order != null) {
			setSortOrder(order);
		}
		maxX = findMax(xLabels);

		setBackground(Color.WHITE);
		autoSizeBarWidth();
		generateBarColors();

		ToolTipManager ttm = ToolTipManager.sharedInstance();
		ttm.registerComponent(this);
		ttm.setInitialDelay(150);
		ttm.setDismissDelay(4000);

		addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				int newHover = getBarIndexAt(e.getX(), e.getY());
				if (newHover != hoveredBarIndex) {
					hoveredBarIndex = newHover;
					repaint();
				}
			}
		});

		recalculatePreferredSize();
	}

	private void autoSizeBarWidth() {
		int barCount = yValues.length;
		if (barCount > 0 && barCount < AUTO_WIDTH_BAR_LIMIT) {
			barWidth = Math.max(DEFAULT_BAR_WIDTH, DEFAULT_PANEL_WIDTH / barCount);
		} else {
			barWidth = DEFAULT_BAR_WIDTH;
		}
	}

	// ---------------------------------------------------------------
	// Public API
	// ---------------------------------------------------------------

	public int getBarWidth() {
		return barWidth;
	}

	public void setBarWidth(int width) {
		barWidth = Math.max(MIN_BAR_WIDTH, width);
		recalculatePreferredSize();
		repaint();
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
		maxX = findMax(xLabels);
		generateBarColors();
	}

	// ---------------------------------------------------------------
	// Export methods
	// ---------------------------------------------------------------

	public void exportAsImage() {
		String outputFilePath = promptForFilePath("png");
		if (outputFilePath == null) return;
		if (!outputFilePath.endsWith(".png")) {
			outputFilePath += ".png";
		}

		try {
			BufferedImage image = new BufferedImage(
					getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = image.createGraphics();
			applyRenderingHints(g2d);
			paint(g2d);
			g2d.dispose();
			ImageIO.write(image, "png", new File(outputFilePath));
		} catch (IOException e) {
			showErrorDialog("Failed to save PNG image: " + e.getMessage());
		}
	}

	public void exportAsCSV() {
		String outputFilePath = promptForFilePath("csv");
		if (outputFilePath == null) return;
		if (!outputFilePath.endsWith(".csv")) {
			outputFilePath += ".csv";
		}

		try (PrintWriter pw = new PrintWriter(new File(outputFilePath))) {
			pw.println(title);
			pw.println(xAxisName + "," + yAxisName);
			for (int i = 0; i < yValues.length; i++) {
				pw.println(getXValueAsString(i) + "," + yValues[i]);
			}
		} catch (IOException e) {
			showErrorDialog("Failed to save CSV file: " + e.getMessage());
		}
	}

	// ---------------------------------------------------------------
	// Tooltip support
	// ---------------------------------------------------------------

	@Override
	public String getToolTipText(MouseEvent event) {
		int idx = getBarIndexAt(event.getX(), event.getY());
		if (idx >= 0) {
			return xAxisName + ": " + getXValueAsString(idx)
					+ " | " + yAxisName + ": " + numberFormat.format(yValues[idx]);
		}
		return null;
	}

	private int getBarIndexAt(int mouseX, int mouseY) {
		if (yValues == null || yValues.length == 0) return -1;

		int yAxisBottom = getHeight() - MARGIN_BOTTOM;

		for (int i = 0; i < yValues.length; i++) {
			int barX = MARGIN_LEFT + i * barWidth;
			int barHeight = (int) (yValues[i] * scaleFactor);
			int barY = yAxisBottom - barHeight;

			if (mouseX >= barX && mouseX < barX + barWidth - BAR_GAP
					&& mouseY >= barY && mouseY <= yAxisBottom) {
				return i;
			}
		}
		return -1;
	}

	// ---------------------------------------------------------------
	// Painting
	// ---------------------------------------------------------------

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		if (yValues == null || yValues.length == 0) {
			drawEmptyState((Graphics2D) g);
			return;
		}

		Graphics2D g2d = (Graphics2D) g;
		applyRenderingHints(g2d);

		int availableHeight = getHeight() - MARGIN_TOP - MARGIN_BOTTOM;
		scaleFactor = maxY > 0 ? (double) availableHeight / maxY : 0;

		int yAxisBottom = getHeight() - MARGIN_BOTTOM;
		int histogramWidth = yValues.length * barWidth;
		int xAxisEnd = MARGIN_LEFT + histogramWidth;

		// Grid
		drawGridLines(g2d, yAxisBottom, xAxisEnd, availableHeight);

		// Axes
		drawAxes(g2d, yAxisBottom, xAxisEnd);

		// Bars
		boolean labelsVisible = showBarLabels && barWidth >= MIN_BAR_WIDTH_FOR_LABELS;
		boolean valuesVisible = showBarValues && barWidth >= MIN_BAR_WIDTH_FOR_LABELS;
		drawBars(g2d, yAxisBottom, labelsVisible, valuesVisible);

		// Axis labels
		drawAxisLabels(g2d, yAxisBottom, xAxisEnd);

		// Title
		drawCenteredTitle(g2d);

		// Stats
		drawStatsSummary(g2d, xAxisEnd);
	}

	// ---------------------------------------------------------------
	// Painting helpers
	// ---------------------------------------------------------------

	private void applyRenderingHints(Graphics2D g2d) {
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	}

	private void drawEmptyState(Graphics2D g2d) {
		applyRenderingHints(g2d);
		g2d.setColor(TICK_LABEL_COLOR);
		g2d.setFont(FONT_BOLD);
		String msg = "No data to display";
		FontMetrics fm = g2d.getFontMetrics();
		g2d.drawString(msg,
				(getWidth() - fm.stringWidth(msg)) / 2,
				getHeight() / 2);
	}

	private void drawGridLines(Graphics2D g2d, int yAxisBottom,
			int xAxisEnd, int availableHeight) {
		g2d.setFont(FONT_NORMAL);
		FontMetrics fm = g2d.getFontMetrics();
		Stroke originalStroke = g2d.getStroke();

		for (int i = 1; i <= Y_GRID_LINE_COUNT; i++) {
			int gridY = yAxisBottom - (i * availableHeight / Y_GRID_LINE_COUNT);
			int gridValue = (i * maxY) / Y_GRID_LINE_COUNT;
			String label = numberFormat.format(gridValue);

			// Grid line
			g2d.setColor(GRID_COLOR);
			g2d.setStroke(GRID_STROKE);
			g2d.drawLine(MARGIN_LEFT, gridY, xAxisEnd, gridY);

			// Tick mark
			g2d.setColor(AXIS_COLOR);
			g2d.setStroke(TICK_STROKE);
			g2d.drawLine(MARGIN_LEFT - 4, gridY, MARGIN_LEFT, gridY);

			// Label
			g2d.setColor(TICK_LABEL_COLOR);
			int labelWidth = fm.stringWidth(label);
			g2d.drawString(label, MARGIN_LEFT - labelWidth - 8, gridY + fm.getAscent() / 2);
		}

		g2d.setStroke(originalStroke);
	}

	private void drawAxes(Graphics2D g2d, int yAxisBottom, int xAxisEnd) {
		g2d.setColor(AXIS_COLOR);
		g2d.setStroke(AXIS_STROKE);
		g2d.drawLine(MARGIN_LEFT, yAxisBottom, xAxisEnd, yAxisBottom); // X axis
		g2d.drawLine(MARGIN_LEFT, MARGIN_TOP, MARGIN_LEFT, yAxisBottom); // Y axis
		g2d.setStroke(new BasicStroke(1.0f));
	}

	/**
	 * Calculates the skip interval for X-axis labels to prevent overlap.
	 * Measures the width of each label text and determines how many bars
	 * must be skipped so that adjacent drawn labels do not collide.
	 */
	private int calculateLabelSkip(FontMetrics fm, boolean rotate) {
		if (yValues.length <= 1) {
			return 1;
		}

		if (rotate) {
			// When rotated 90 degrees, the text width becomes vertical and the
			// text height becomes the horizontal footprint. So labels overlap
			// horizontally when the font height exceeds the bar width.
			int neededPixels = fm.getHeight() + 2;
			int skip = Math.max(1, (int) Math.ceil((double) neededPixels / barWidth));
			return skip;
		} else {
			// Non-rotated: find the maximum label width and ensure labels don't overlap
			int maxLabelWidth = 0;
			for (int i = 0; i < yValues.length; i++) {
				String text = getXValueAsString(i);
				int w = fm.stringWidth(text);
				if (w > maxLabelWidth) {
					maxLabelWidth = w;
				}
			}
			// Need at least maxLabelWidth + small gap between label centers
			int neededPixels = maxLabelWidth + 6;
			int skip = Math.max(1, (int) Math.ceil((double) neededPixels / barWidth));
			return skip;
		}
	}

	/**
	 * Calculates the skip interval for bar value annotations (numbers above bars)
	 * to prevent them from overlapping with adjacent value annotations.
	 * Measures the pixel width of the longest value string and determines how many
	 * bars must be skipped so that adjacent drawn values do not collide.
	 */
	private int calculateValueSkip(FontMetrics fm) {
		if (yValues.length <= 1) {
			return 1;
		}

		// Find the maximum value text width
		int maxValueWidth = 0;
		for (int i = 0; i < yValues.length; i++) {
			if (yValues[i] > 0) {
				String valueText = String.valueOf(yValues[i]);
				int w = fm.stringWidth(valueText);
				if (w > maxValueWidth) {
					maxValueWidth = w;
				}
			}
		}

		// Need at least maxValueWidth + small gap between value centers
		int neededPixels = maxValueWidth + 4;
		int skip = Math.max(1, (int) Math.ceil((double) neededPixels / barWidth));
		return skip;
	}

	private void drawBars(Graphics2D g2d, int yAxisBottom,
			boolean drawLabels, boolean drawValues) {
		FontMetrics fm = g2d.getFontMetrics(FONT_NORMAL);
		boolean rotateLabels = drawLabels && barWidth < LABEL_ROTATION_THRESHOLD;

		// Calculate skip intervals to prevent label and value overlap
		int labelSkip = drawLabels ? calculateLabelSkip(fm, rotateLabels) : 1;
		int valueSkip = drawValues ? calculateValueSkip(fm) : 1;

		for (int i = 0; i < yValues.length; i++) {
			int barHeight = (int) (yValues[i] * scaleFactor);
			int barX = MARGIN_LEFT + i * barWidth;
			int barY = yAxisBottom - barHeight;
			int effectiveBarWidth = Math.max(1, barWidth - BAR_GAP);

			if (barHeight <= 0) {
				if (drawLabels && (i % labelSkip == 0)) {
					drawBarLabel(g2d, fm, i, barX, effectiveBarWidth, yAxisBottom, rotateLabels);
				}
				continue;
			}

			boolean isHovered = (i == hoveredBarIndex);

			// Fill
			g2d.setColor(barColors[i]);
			g2d.fillRect(barX, barY, effectiveBarWidth, barHeight);

			// Hover highlight: brighter overlay
			if (isHovered) {
				g2d.setColor(new Color(255, 255, 255, 60));
				g2d.fillRect(barX, barY, effectiveBarWidth, barHeight);
			}

			// Border
			if (isHovered) {
				g2d.setColor(HOVER_BORDER_COLOR);
				g2d.setStroke(HOVER_STROKE);
			} else {
				g2d.setColor(BAR_BORDER_COLOR);
				g2d.setStroke(BAR_STROKE);
			}
			g2d.drawRect(barX, barY, effectiveBarWidth, barHeight);

			// X label below bar (only draw every labelSkip-th label)
			if (drawLabels && (i % labelSkip == 0)) {
				drawBarLabel(g2d, fm, i, barX, effectiveBarWidth, yAxisBottom, rotateLabels);
			}

			// Value above bar (only draw every valueSkip-th value to prevent overlap)
			g2d.setFont(FONT_NORMAL);
			g2d.setColor(AXIS_COLOR);
			if (drawValues && barHeight > fm.getHeight() && (i % valueSkip == 0)) {
				String value = String.valueOf(yValues[i]);
				int valueWidth = fm.stringWidth(value);
				int textX = barX + (effectiveBarWidth - valueWidth) / 2;

				// Clamp so the value text does not extend beyond the histogram area
				if (textX < MARGIN_LEFT) {
					textX = MARGIN_LEFT;
				}
				int histogramRight = MARGIN_LEFT + yValues.length * barWidth;
				if (textX + valueWidth > histogramRight) {
					textX = histogramRight - valueWidth;
				}

				g2d.drawString(value, textX, barY - 3);
			}
		}
	}

	/**
	 * Draws an X-axis label beneath the bar at the given index.
	 * <p>
	 * When {@code rotate} is {@code true} the label is drawn at 90 degrees
	 * (reading bottom-to-top). The label is horizontally centered on the
	 * tick mark by offsetting by half the font height (which becomes the
	 * horizontal extent after rotation), and vertically positioned so the
	 * start of the text (which becomes the top after rotation) begins just
	 * below the tick mark.
	 * <p>
	 * A small tick mark is drawn from the axis down to the label to visually
	 * connect the label to its bar.
	 */
	private void drawBarLabel(Graphics2D g2d, FontMetrics fm, int index,
			int barX, int effectiveBarWidth, int yAxisBottom,
			boolean rotate) {
		g2d.setFont(FONT_NORMAL);
		String text = getXValueAsString(index);

		// Tick mark center: middle of the bar
		int tickX = barX + effectiveBarWidth / 2;

		// Draw small tick mark below axis
		g2d.setColor(AXIS_COLOR);
		g2d.setStroke(TICK_STROKE);
		g2d.drawLine(tickX, yAxisBottom, tickX, yAxisBottom + X_LABEL_GAP);

		g2d.setColor(TICK_LABEL_COLOR);

		if (rotate) {
			/*
			 * 90-degree rotation (reading bottom-to-top):
			 *
			 * After rotating -90 degrees around the anchor point:
			 *   - The text's width dimension runs vertically (downward).
			 *   - The text's height dimension runs horizontally.
			 *
			 * To CENTER the label on the tick mark:
			 *   - Horizontally: the anchor X must be offset so that half
			 *     the text height is on each side of tickX.
			 *     anchor X = tickX + fm.getAscent()/2   (ascent ≈ visual height above baseline)
			 *     Then drawString at y=0 puts the baseline at the anchor,
			 *     with ascent going right and descent going left after rotation.
			 *     Net: center ≈ tickX.
			 *
			 *   - Vertically: the text starts at the anchor and runs downward
			 *     (since width becomes vertical). We want it to start just
			 *     below the tick mark.
			 *     anchor Y = yAxisBottom + X_LABEL_GAP + 2
			 *     drawString x=0 means the left edge of the text is at the
			 *     anchor, which after -90 rotation becomes the top edge.
			 */
			AffineTransform original = g2d.getTransform();

			// Anchor: horizontally centered on tick, vertically just below tick
			int anchorX = tickX - fm.getAscent() / 2;
			int anchorY = yAxisBottom + X_LABEL_GAP + 2;

			g2d.translate(anchorX, anchorY);
			g2d.rotate(Math.PI / 2);

			// In rotated coordinates:
			//   x runs upward (negative = downward on screen)
			//   y runs rightward
			// Drawing at (x=0, y=0) places left edge of text at anchor.
			// After -90° rotation, left edge = top of vertical text.
			// To vertically center: we don't need to shift x since we want
			// text hanging downward from the tick.
			// The baseline is at y=0; ascent goes in -y direction (leftward
			// on screen = toward tickX center). We already offset anchorX
			// by ascent/2 to compensate.
			g2d.drawString(text, 0, 0);

			g2d.setTransform(original);
		} else {
			int textWidth = fm.stringWidth(text);
			int textX = barX + (effectiveBarWidth - textWidth) / 2;

			// Clamp so the label does not extend beyond the histogram area on the right
			int histogramRight = MARGIN_LEFT + yValues.length * barWidth;
			if (textX + textWidth > histogramRight + MARGIN_RIGHT) {
				textX = histogramRight + MARGIN_RIGHT - textWidth;
			}
			if (textX < MARGIN_LEFT) {
				textX = MARGIN_LEFT;
			}

			// Draw text below the tick mark with proper clearance
			g2d.drawString(text, textX, yAxisBottom + X_LABEL_GAP + 2 + fm.getAscent());
		}
	}

	private void drawAxisLabels(Graphics2D g2d, int yAxisBottom, int xAxisEnd) {
		g2d.setFont(FONT_BOLD);
		g2d.setColor(AXIS_COLOR);
		FontMetrics fm = g2d.getFontMetrics();

		// Y axis label (rotated)
		AffineTransform original = g2d.getTransform();
		int yLabelX = 15;
		int yLabelY = MARGIN_TOP + (yAxisBottom - MARGIN_TOP) / 2;
		g2d.translate(yLabelX, yLabelY);
		g2d.rotate(-Math.PI / 2);
		g2d.drawString(yAxisName, -fm.stringWidth(yAxisName) / 2, 0);
		g2d.setTransform(original);

		// X axis label (centered below, at the very bottom of MARGIN_BOTTOM)
		int xLabelWidth = fm.stringWidth(xAxisName);
		int centerX = MARGIN_LEFT + (xAxisEnd - MARGIN_LEFT) / 2;
		int xLabelX = centerX - xLabelWidth / 2;
		// Place at the bottom of the panel with a small inset
		int xLabelY = getHeight() - 8;

		// Clamp within bounds
		if (xLabelX + xLabelWidth > xAxisEnd + MARGIN_RIGHT) {
			xLabelX = xAxisEnd + MARGIN_RIGHT - xLabelWidth;
		}
		if (xLabelX < MARGIN_LEFT) {
			xLabelX = MARGIN_LEFT;
		}

		g2d.drawString(xAxisName, xLabelX, xLabelY);
	}

	private void drawCenteredTitle(Graphics2D g2d) {
		g2d.setFont(FONT_TITLE);
		g2d.setColor(AXIS_COLOR);
		FontMetrics fm = g2d.getFontMetrics();
		int titleX = (getWidth() - fm.stringWidth(title)) / 2;
		// Draw near the very top of the panel
		g2d.drawString(title, titleX, fm.getAscent() + 4);
	}

	private void drawStatsSummary(Graphics2D g2d, int xAxisEnd) {
		if (yValues.length == 0) return;

		long sum = 0;
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int v : yValues) {
			sum += v;
			if (v < min) min = v;
			if (v > max) max = v;
		}
		double avg = (double) sum / yValues.length;

		String statsText = String.format("N=%s  Min=%s  Max=%s  Avg=%.1f  Sum=%s",
				numberFormat.format(yValues.length),
				numberFormat.format(min),
				numberFormat.format(max),
				avg,
				numberFormat.format(sum));

		g2d.setFont(FONT_STATS);
		g2d.setColor(TICK_LABEL_COLOR);
		FontMetrics fm = g2d.getFontMetrics();

		int statsX = xAxisEnd - fm.stringWidth(statsText);
		// Position well above the chart top edge so it never overlaps bars
		int statsY = MARGIN_TOP - STATS_GAP;

		g2d.drawString(statsText, statsX, statsY);
	}

	// ---------------------------------------------------------------
	// Preferred-size management
	// ---------------------------------------------------------------

	private void recalculatePreferredSize() {
		int histogramWidth = yValues.length * barWidth + MARGIN_LEFT + MARGIN_RIGHT;
		super.setPreferredSize(new Dimension(histogramWidth,
				Math.max(getHeight(), DEFAULT_PANEL_HEIGHT)));
	}

	@Override
	public void setPreferredSize(Dimension preferredSize) {
		int histogramWidth = yValues.length * barWidth + MARGIN_LEFT + MARGIN_RIGHT;
		int height = (preferredSize != null) ? preferredSize.height : DEFAULT_PANEL_HEIGHT;
		super.setPreferredSize(new Dimension(histogramWidth, height));
	}

	// ---------------------------------------------------------------
	// Colour generation (unchanged golden-ratio HSB)
	// ---------------------------------------------------------------

	private void generateBarColors() {
		if (yValues == null) return;
		barColors = new Color[yValues.length];
		for (int i = 0; i < yValues.length; i++) {
			float hue = (i * HUE_STEP) % 1.0f;
			barColors[i] = Color.getHSBColor(hue, SATURATION, BRIGHTNESS);
		}
	}

	// ---------------------------------------------------------------
	// Array / data utilities
	// ---------------------------------------------------------------

	private static int findMax(int[] array) {
		if (array == null || array.length == 0) return 0;
		int max = array[0];
		for (int i = 1; i < array.length; i++) {
			if (array[i] > max) max = array[i];
		}
		return max;
	}

	private String getXValueAsString(int i) {
		int value = xLabels[i];
		if (mapXValuesToString != null) {
			String name = mapXValuesToString.get(value);
			if (name != null) return name;
		}
		return Integer.toString(value);
	}

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

	// ---------------------------------------------------------------
	// Data-record helpers
	// ---------------------------------------------------------------

	private static int findMaxLabelFromData(Vector<List<Object>> data, int index) {
		int maxLabel = 0;
		for (List<Object> record : data) {
			if (index < record.size()) {
				int value = convertToInt(record.get(index));
				if (value > maxLabel) maxLabel = value;
			}
		}
		return maxLabel;
	}

	private static void populateValuesFromData(Vector<List<Object>> data,
			int index, int[] valuesArray) {
		for (List<Object> record : data) {
			if (index < record.size()) {
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

	private String promptForFilePath(String extension) {
		try {
			File initialDir = getInitialDirectory();
			JFileChooser fc = (initialDir != null)
					? new JFileChooser(initialDir) : new JFileChooser();

			if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
				File file = fc.getSelectedFile();
				PreferencesManager.getInstance()
						.setOutputFilePath(file.getParent());
				return file.getPath();
			}
		} catch (Exception e) {
			showErrorDialog("Failed to open save dialog: " + e.getMessage());
		}
		return null;
	}

	private static File getInitialDirectory() {
		String previousPath = PreferencesManager.getInstance().getOutputFilePath();
		if (previousPath != null) return new File(previousPath);

		URL main = MainTestApriori_simple_saveToFile.class
				.getResource("MainTestApriori_saveToFile.class");
		if (main != null && "file".equalsIgnoreCase(main.getProtocol())) {
			return new File(main.getPath());
		}
		return null;
	}

	private void showErrorDialog(String message) {
		JOptionPane.showMessageDialog(this, message,
				"Error", JOptionPane.ERROR_MESSAGE);
	}

}