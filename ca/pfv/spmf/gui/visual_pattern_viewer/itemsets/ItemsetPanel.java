package ca.pfv.spmf.gui.visual_pattern_viewer.itemsets;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
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
 * A Swing panel that visualizes a single itemset. The panel displays the
 * itemset as a rounded rectangle and shows measure values below.
 * 
 * @author Philippe Fournier-Viger
 */
public class ItemsetPanel extends JPanel {
	private static final long serialVersionUID = 5263914307631939188L;

	/** List of color for the measure rectangles */
	private static final List<Color> MEASURE_COLORS = List.of(Color.BLUE, Color.RED, Color.GREEN, Color.MAGENTA,
			Color.ORANGE, Color.CYAN, Color.PINK, Color.YELLOW, new Color(128, 0, 128), // Purple
			new Color(0, 128, 128) // Teal
	);

	/** The itemset to display. */
	private final Itemset itemset;

	/** Margin around the panel content. */
	private static final int CONTENT_MARGIN = 10;
	/** Vertical space between stacked nodes (now used as horizontal gap). */
	private static final int VERTICAL_GAP = 5;
	/** Horizontal padding inside each node box. */
	private static final int NODE_HORIZONTAL_PADDING = 10;
	/** Vertical padding inside each node box. */
	private static final int NODE_VERTICAL_PADDING = 5;

	/** Bounds for each antecedent node. */
	private final List<Rectangle> itemsetNodeBounds = new ArrayList<>();
	/** A map indicating the min value for each measure */
	private final Map<String, Double> minValues;
	/** A map indicating the max value for each measure */
	private final Map<String, Double> maxValues;
	/** bar width for measures */
	private static final int BAR_WIDTH = 100;

	/** bar height for measures */
	private static final int BAR_HEIGHT = 10;

	/** Consistent font across panel */
	private static final Font DEFAULT_FONT = UIManager.getFont("Label.font").deriveFont(Font.PLAIN);

	/**
	 * Constructs a panel for visualizing the given rule.
	 *
	 * @param rule      the association rule to display (must not be null)
	 * @param maxValues map indicating the max value for each measure if numeric,
	 *                  otherwise null
	 * @param minValues map indicating the min value for each measure if numeric,
	 *                  otherwise null
	 * @throws NullPointerException if rule is null
	 */
	public ItemsetPanel(Itemset rule, Map<String, Double> minValues, Map<String, Double> maxValues) {
		this.itemset = Objects.requireNonNull(rule, "rule must not be null");
		this.minValues = minValues;
		this.maxValues = maxValues;
		setBackground(Color.WHITE);
		setBorder(BorderFactory.createLineBorder(Color.GRAY));
		setFont(DEFAULT_FONT);
	}

	/**
	 * Calculates the preferred size based on node and measure text dimensions.
	 *
	 * @return preferred panel dimensions
	 */
	@Override
	public Dimension getPreferredSize() {
		FontMetrics metrics = getFontMetrics(getFont());
		int totalNodeWidth = VERTICAL_GAP;
		for (String label : itemset.getItems()) {
			int w = metrics.stringWidth(label) + NODE_HORIZONTAL_PADDING;
			totalNodeWidth += w + VERTICAL_GAP;
		}

		int nodeHeight = metrics.getHeight() + NODE_VERTICAL_PADDING;
		int measureHeight = itemset.getMeasures().size() * (metrics.getHeight() + VERTICAL_GAP);

		int totalHeight = Math.max(nodeHeight + 2 * CONTENT_MARGIN, 50) + measureHeight + CONTENT_MARGIN;
		int totalWidth = Math.max(totalNodeWidth, 300) + 2 * CONTENT_MARGIN;
		return new Dimension(totalWidth, totalHeight);
	}

	/**
	 * Paints nodes, a centered arrow, and measure values.
	 *
	 * @param g the Graphics context
	 */
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D graphics = (Graphics2D) g.create();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		FontMetrics metrics = graphics.getFontMetrics();

		layoutNodeBounds(metrics);
		drawNodes(graphics);
		drawMeasureValues(graphics, metrics);
		graphics.dispose();
	}

	/**
	 * Lays out rectangles for antecedent and consequent nodes horizontally.
	 */
	private void layoutNodeBounds(FontMetrics metrics) {
		itemsetNodeBounds.clear();

		Dimension prefSize = getPreferredSize();
		int nodeHeight = metrics.getHeight() + NODE_VERTICAL_PADDING;
		int availableHeight = prefSize.height - itemset.getMeasures().size() * (metrics.getHeight() + VERTICAL_GAP)
				- 2 * CONTENT_MARGIN;
		int itemsetY = CONTENT_MARGIN + (availableHeight - nodeHeight) / 2;

		int itemsetX = CONTENT_MARGIN + VERTICAL_GAP;

		for (String label : itemset.getItems()) {
			int width = metrics.stringWidth(label) + NODE_HORIZONTAL_PADDING;
			itemsetNodeBounds.add(new Rectangle(itemsetX, itemsetY, width, nodeHeight));
			itemsetX += width + VERTICAL_GAP;
		}
	}

	/**
	 * Draws all antecedent and consequent nodes.
	 */
	private void drawNodes(Graphics2D graphics) {
		for (int i = 0; i < itemsetNodeBounds.size(); i++) {
			drawSingleNode(graphics, itemset.getItems().get(i), itemsetNodeBounds.get(i));
		}
	}

	/**
	 * Draws the rule's measure text and bars
	 */
	private void drawMeasureValues(Graphics2D graphics, FontMetrics metrics) {
		graphics.setFont(DEFAULT_FONT);
		int measureCount = itemset.getMeasures().size();
		int lineHeight = metrics.getHeight() + VERTICAL_GAP;

		// Calculate total height occupied by all measures
		int totalMeasuresHeight = measureCount * lineHeight;

		// Start y so the last measure is above CONTENT_MARGIN from bottom
		int y = getHeight() - CONTENT_MARGIN - totalMeasuresHeight + metrics.getAscent();

		int x = CONTENT_MARGIN;
		int measureIndex = 0;
		for (Map.Entry<String, String> entry : itemset.getMeasures().entrySet()) {
			String name = entry.getKey();
			String strValue = entry.getValue();
			boolean isDouble = false;
			double value = 0;
			try {
				value = Double.parseDouble(strValue);
				isDouble = true;
			} catch (NumberFormatException e) {
			}

			

			// Draw the bar
			if(isDouble) {
				graphics.setColor(Color.BLACK);
				String text = name + ": " + formatValue(value); // Use your formatting method here
				graphics.drawString(text, x, y);
				
				Double min = minValues.get(name);
				Double max = maxValues.get(name);
				if (min != null && max != null && max > min) {
					double ratio = (value - min) / (max - min);
					int barLength = (int) (ratio * BAR_WIDTH);
					int barX = x + metrics.stringWidth(text) + 10;
					int barY = y - metrics.getAscent() + (metrics.getAscent() + metrics.getDescent() - BAR_HEIGHT) / 2;
	
					Color color = MEASURE_COLORS.get(measureIndex % MEASURE_COLORS.size());
					graphics.setColor(color);
					graphics.fillRect(barX, barY, barLength, BAR_HEIGHT);
	
					graphics.setColor(Color.GRAY);
					graphics.drawRect(barX, barY, BAR_WIDTH, BAR_HEIGHT);
				}
			}else {
				graphics.setColor(Color.BLACK);
				String text = name + ": " + strValue; // Use your formatting method here
				graphics.drawString(text, x, y);
			}

			y += lineHeight;
			measureIndex++;
		}
	}

	// Helper method to format double with max 5 digits after decimal
	private String formatValue(double val) {
		return String.format("%.4f", val);
	}

	/**
	 * Renders a rounded rectangle node with centered text label.
	 */
	private void drawSingleNode(Graphics2D graphics, String label, Rectangle bounds) {
		graphics.setColor(new Color(240, 240, 240));
		graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10);
		graphics.setColor(Color.BLACK);
		graphics.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10);
		FontMetrics metrics = graphics.getFontMetrics();
		int textX = bounds.x + (bounds.width - metrics.stringWidth(label)) / 2;
		int textY = bounds.y + (bounds.height - metrics.getHeight()) / 2 + metrics.getAscent();
		graphics.drawString(label, textX, textY);
	}

	/**
	 * Get itemset
	 * 
	 * @return the itemset
	 */
	public Itemset getItemset() {
		return itemset;
	}
}
