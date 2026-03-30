package ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.UIManager;

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
 * A Swing panel that visualizes a single sequential pattern. Each itemset
 * appears as stacked nodes with a surrounding rectangle, arrows connecting
 * successive itemsets, and numeric measures shown as bars below.
 *
 * The vertical layout is now calculated dynamically to ensure itemsets never
 * overlap the measures area at the bottom.
 *
 * @author Philippe Fournier-Viger
 */
public class SequentialPatternPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	/** The sequential pattern to display */
	private final SequentialPattern pattern;
	/** Precomputed bounds for each item in each itemset */
	private final List<List<Rectangle>> itemsetBounds = new ArrayList<>();
	/** Minimum values for numeric measures (used for bar scaling) */
	private final Map<String, Double> minValues;
	/** Maximum values for numeric measures (used for bar scaling) */
	private final Map<String, Double> maxValues;

	/** Colors for the measure bars */
	private static final List<Color> MEASURE_COLORS = List.of(Color.BLUE, Color.RED, Color.GREEN, Color.MAGENTA,
			Color.ORANGE, Color.CYAN, Color.PINK, Color.YELLOW, new Color(128, 0, 128), // Purple
			new Color(0, 128, 128) // Teal
	);

	private static final int CONTENT_MARGIN = 5;
	private static final int HORIZONTAL_GAP = 30;
	private static final int VERTICAL_GAP = 4;
	private static final int NODE_HORIZONTAL_PADDING = 10;
	private static final int NODE_VERTICAL_PADDING = 4;
	private static final int MARGIN_ARROW = 5;
	private static final int CLUSTER_MARGIN = 5;
	private static final int BAR_WIDTH = 60;
	private static final int BAR_HEIGHT = 8;
	private static final int TOP_EXTRA_GAP = 2;

	/** Font used for labels and measures */
	private static final Font DEFAULT_FONT = UIManager.getFont("Label.font").deriveFont(Font.PLAIN);

	/**
	 * Constructs the panel for a sequential pattern.
	 *
	 * @param pattern   the sequential pattern (not null)
	 * @param minValues map of minimum measure values (for scaling), or null
	 * @param maxValues map of maximum measure values (for scaling), or null
	 */
	public SequentialPatternPanel(SequentialPattern pattern, Map<String, Double> minValues,
			Map<String, Double> maxValues) {
		this.pattern = Objects.requireNonNull(pattern, "pattern must not be null");
		this.minValues = minValues;
		this.maxValues = maxValues;
		setBackground(Color.WHITE);
		setBorder(BorderFactory.createLineBorder(Color.GRAY));
		setFont(DEFAULT_FONT);
	}

	/**
	 * Computes and returns the preferred panel size based on the layout of itemsets
	 * and measure display.
	 *
	 * @return the preferred dimensions for this panel
	 */
	@Override
	public Dimension getPreferredSize() {
		FontMetrics m = getFontMetrics(DEFAULT_FONT);
		List<List<String>> itemsets = pattern.getItemsets();

		// Width: itemsets (nodes)
		int totalNodeWidth = itemsets.stream().mapToInt(set -> calculateMaxNodeWidth(set, m)).sum();
		totalNodeWidth += (itemsets.size() - 1) * HORIZONTAL_GAP;

		// Width: measure area
		int measureLabelWidth = 100;
		int measureAreaWidth = measureLabelWidth + 15 + BAR_WIDTH;
		int contentWidth = Math.max(totalNodeWidth, measureAreaWidth) + 2 * CONTENT_MARGIN;

		// Height: find tallest itemset block
		int maxNodeBlock = itemsets.stream().mapToInt(set -> calculateTotalNodeHeight(set, m)).max().orElse(0);
		int measureCount = pattern.getMeasures().size();
		int lineHeight = m.getHeight() + VERTICAL_GAP;
		int measureArea = measureCount * lineHeight;

		// Final height = top margin + extra gap + node block + gap + measure area +
		// bottom margin
		int totalHeight = CONTENT_MARGIN + TOP_EXTRA_GAP + maxNodeBlock + measureArea + CONTENT_MARGIN;

		return new Dimension(contentWidth, totalHeight);
	}

	/**
	 * Computes the bounding rectangles for each item in each itemset, centering
	 * them vertically above the measures area.
	 *
	 * @param m font metrics used to compute dimensions
	 */
	private void layoutItemsetBounds(FontMetrics m) {
		itemsetBounds.clear();
		int panelHeight = getHeight();

		// Reserve bottom space for measures
		int measureCount = pattern.getMeasures().size();
		int lineHeight = m.getHeight() + VERTICAL_GAP;
		int measureArea = measureCount * lineHeight;

		// Available height for node blocks
		int availableHeight = panelHeight - CONTENT_MARGIN - TOP_EXTRA_GAP - measureArea - CONTENT_MARGIN;

		int yBase = CONTENT_MARGIN + TOP_EXTRA_GAP;

		int x = CONTENT_MARGIN;
		for (List<String> set : pattern.getItemsets()) {
			int blockW = calculateMaxNodeWidth(set, m);
			int blockH = calculateTotalNodeHeight(set, m);
			int y = yBase + Math.max(0, (availableHeight - blockH) / 2);

			List<Rectangle> cluster = new ArrayList<>();
			for (String item : set) {
				int w = m.stringWidth(item) + NODE_HORIZONTAL_PADDING;
				int h = m.getHeight() + NODE_VERTICAL_PADDING;
				cluster.add(new Rectangle(x, y, w, h));
				y += h + VERTICAL_GAP;
			}
			itemsetBounds.add(cluster);
			x += blockW + HORIZONTAL_GAP;
		}
	}

	/**
	 * Paints the panel including itemsets, arrows, and measure values with color
	 * bars.
	 *
	 * @param g the graphics context
	 */
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		FontMetrics m = g2.getFontMetrics();

		layoutItemsetBounds(m);
		drawClusterBounds(g2);
		drawNodes(g2);
		drawConnectingArrows(g2);
		drawMeasureValues(g2, m);
		g2.dispose();
	}

	/**
	 * Draws the rounded rectangles surrounding each itemset.
	 *
	 * @param g2 the graphics context
	 */
	private void drawClusterBounds(Graphics2D g2) {
		g2.setColor(Color.DARK_GRAY);
		g2.setStroke(new BasicStroke(1));
		for (List<Rectangle> cluster : itemsetBounds) {
			if (cluster.isEmpty())
				continue;
			Rectangle first = cluster.get(0);
			Rectangle last = cluster.get(cluster.size() - 1);
			int x = first.x - CLUSTER_MARGIN;
			int y = first.y - CLUSTER_MARGIN;
			int w = first.width + 2 * CLUSTER_MARGIN;
			int h = (last.y + last.height - first.y) + 2 * CLUSTER_MARGIN;
			g2.drawRoundRect(x, y, w, h, 10, 10);
		}
	}

	/**
	 * Draws all nodes (items) of each itemset.
	 *
	 * @param g2 the graphics context
	 */
	private void drawNodes(Graphics2D g2) {
		for (int i = 0; i < itemsetBounds.size(); i++) {
			List<Rectangle> cluster = itemsetBounds.get(i);
			List<String> items = pattern.getItemsets().get(i);
			for (int j = 0; j < cluster.size(); j++) {
				drawSingleNode(g2, items.get(j), cluster.get(j));
			}
		}
	}

	/**
	 * Draws arrows between consecutive itemsets.
	 *
	 * @param g2 the graphics context
	 */
	private void drawConnectingArrows(Graphics2D g2) {
		if (itemsetBounds.size() < 2)
			return;
		g2.setColor(Color.BLUE);
		g2.setStroke(new BasicStroke(1));
		for (int i = 0; i < itemsetBounds.size() - 1; i++) {
			List<Rectangle> c1 = itemsetBounds.get(i);
			List<Rectangle> c2 = itemsetBounds.get(i + 1);
			int startX = c1.get(0).x + c1.get(0).width + MARGIN_ARROW;
			int startY = (c1.get(0).y + c1.get(c1.size() - 1).y + c1.get(c1.size() - 1).height) / 2;
			int endX = c2.get(0).x - MARGIN_ARROW;
			int endY = (c2.get(0).y + c2.get(c2.size() - 1).y + c2.get(c2.size() - 1).height) / 2;
			drawArrowHeadLine(g2, startX, startY, endX, endY);
		}
	}

	/**
	 * Draws the values of each measure and a normalized bar for numeric ones.
	 *
	 * @param g2 the graphics context
	 * @param m  the font metrics
	 */
	private void drawMeasureValues(Graphics2D g2, FontMetrics m) {
		g2.setFont(DEFAULT_FONT);
		int x = CONTENT_MARGIN;

		int measureCount = pattern.getMeasures().size();
		int lineHeight = m.getHeight() + VERTICAL_GAP;
		int totalHeight = measureCount * lineHeight;
		int yStart = getHeight() - CONTENT_MARGIN - totalHeight;
		int labelWidth = 100;
		int barSpacing = 15;

		int index = 0;
		int y = yStart + 10;
		for (Map.Entry<String, String> e : pattern.getMeasures().entrySet()) {
			String name = e.getKey();
			String raw = e.getValue();
			double val;
			String strVal;
			try {
				val = Double.parseDouble(raw);
				strVal = String.format("%.4f", val);
			} catch (NumberFormatException ex) {
				val = 0;
				strVal = raw;
			}

			String label = String.format("%-10s: %s", name, strVal);
			g2.setColor(Color.BLACK);
			g2.drawString(label, x, y + m.getAscent());

			Double min = minValues.get(name);
			Double max = maxValues.get(name);
			if (min != null && max != null && max > min) {
				double ratio = (val - min) / (max - min);
				int barLen = (int) (ratio * BAR_WIDTH);
				int barX = x + labelWidth + barSpacing;
				int barY = y + (m.getAscent() + m.getDescent() - BAR_HEIGHT) / 2;
				g2.setColor(MEASURE_COLORS.get(index % MEASURE_COLORS.size()));
				g2.fillRect(barX, barY, barLen, BAR_HEIGHT);
				g2.setColor(Color.GRAY);
				g2.drawRect(barX, barY, BAR_WIDTH, BAR_HEIGHT);
			}

			y += lineHeight;
			index++;
		}
	}

	/**
	 * Computes the width required for the widest item label in an itemset.
	 *
	 * @param items the list of item labels
	 * @param m     the font metrics
	 * @return the maximum width of a node
	 */
	private int calculateMaxNodeWidth(List<String> items, FontMetrics m) {
		return items.stream().mapToInt(item -> m.stringWidth(item) + NODE_HORIZONTAL_PADDING).max().orElse(0);
	}

	/**
	 * Computes the total height needed for all nodes in an itemset.
	 *
	 * @param items the list of item labels
	 * @param m     the font metrics
	 * @return the total height of the itemset block
	 */
	private int calculateTotalNodeHeight(List<String> items, FontMetrics m) {
		int nodeH = m.getHeight() + NODE_VERTICAL_PADDING;
		return items.size() * nodeH + (items.size() + 1) * VERTICAL_GAP;
	}

	/**
	 * Draws a rounded rectangular node with the given label.
	 *
	 * @param g2    the graphics context
	 * @param label the text label of the node
	 * @param b     the bounding rectangle
	 */
	private void drawSingleNode(Graphics2D g2, String label, Rectangle b) {
		g2.setColor(new Color(240, 240, 240));
		g2.fillRoundRect(b.x, b.y, b.width, b.height, 10, 10);
		g2.setColor(Color.BLACK);
		g2.drawRoundRect(b.x, b.y, b.width, b.height, 10, 10);
		FontMetrics m = g2.getFontMetrics();
		int tx = b.x + (b.width - m.stringWidth(label)) / 2;
		int ty = b.y + (b.height - m.getHeight()) / 2 + m.getAscent();
		g2.drawString(label, tx, ty);
	}

	/**
	 * Draws a line with an arrowhead between two points.
	 *
	 * @param g2 the graphics context
	 * @param x1 start x-coordinate
	 * @param y1 start y-coordinate
	 * @param x2 end x-coordinate
	 * @param y2 end y-coordinate
	 */
	private void drawArrowHeadLine(Graphics2D g2, int x1, int y1, int x2, int y2) {
		Line2D line = new Line2D.Double(x1, y1, x2, y2);
		g2.draw(line);
		double angle = Math.atan2(y2 - y1, x2 - x1);
		int size = 8;
		int x3 = x2 - (int) (size * Math.cos(angle - Math.PI / 6));
		int y3 = y2 - (int) (size * Math.sin(angle - Math.PI / 6));
		int x4 = x2 - (int) (size * Math.cos(angle + Math.PI / 6));
		int y4 = y2 - (int) (size * Math.sin(angle + Math.PI / 6));
		g2.draw(new Line2D.Double(x2, y2, x3, y3));
		g2.draw(new Line2D.Double(x2, y2, x4, y4));
	}

	/**
	 * Get the pattern
	 *
	 * @return the pattern
	 */
	public SequentialPattern getPattern() {
		return pattern;
	}
}
