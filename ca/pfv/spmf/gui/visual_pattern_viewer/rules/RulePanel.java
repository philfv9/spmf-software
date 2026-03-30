package ca.pfv.spmf.gui.visual_pattern_viewer.rules;

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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * This class is used to display a rule in the RuleViewer window.
 * @author Philippe Fournier-Viger
 */
public class RulePanel extends JPanel {

	private static final long serialVersionUID = 5263914307631939188L;

	/** The rule */
	private final Rule rule;

	/** A map indicating the min value for each measure */
	private final Map<String, Double> minValues;
	/** A map indicating the max value for each measure */
	private final Map<String, Double> maxValues;
	
	/** List of color for the measure rectangles */
	private static final List<Color> MEASURE_COLORS = List.of(
		Color.BLUE, Color.RED, Color.GREEN, Color.MAGENTA,
		Color.ORANGE, Color.CYAN, Color.PINK, Color.YELLOW,
		new Color(128, 0, 128), // Purple
		new Color(0, 128, 128)  // Teal
	);

	/** Margin around the panel content. */
	private static final int CONTENT_MARGIN = 10;
	/** Vertical space between stacked nodes (now used as horizontal gap). */
	private static final int VERTICAL_GAP = 5;
	/** Horizontal padding inside each node box. */
	private static final int NODE_HORIZONTAL_PADDING = 20;
	/** Vertical padding inside each node box. */
	private static final int NODE_VERTICAL_PADDING = 4;
	/** Margin arrow */
	private static final int MARGIN_ARROW = 5;

	/** Horizontal gap */
	private static final int HORIZONTAL_GAP = 50;

	/** bar width for measures */
	private static final int BAR_WIDTH = 100;
	/** bar height for measures */
	private static final int BAR_HEIGHT = 10;

	/** The rectangles that will be drawn for the antecedent nodes */
	private final List<Rectangle> antecedentNodeBounds = new ArrayList<>();
	/** The rectangles that will be drawn for the consequent nodes */
	private final List<Rectangle> consequentNodeBounds = new ArrayList<>();
	

	/** Consistent font across panel */
	private static final Font DEFAULT_FONT = UIManager.getFont("Label.font").deriveFont(Font.PLAIN);

	/**
	 * Constructor
	 *
	 * @param rule      The rule to be displayed
	 * @param minValues Map of minimum values for each measure
	 * @param maxValues Map of maximum values for each measure
	 */
	public RulePanel(Rule rule, Map<String, Double> minValues, Map<String, Double> maxValues) {
		this.rule = Objects.requireNonNull(rule, "rule must not be null");
		this.minValues = minValues;
		this.maxValues = maxValues;
		setBackground(Color.WHITE);
		setBorder(BorderFactory.createLineBorder(Color.GRAY));
		setFont(DEFAULT_FONT); 
	}

	@Override
	public Dimension getPreferredSize() {
	    FontMetrics metrics = getFontMetrics(getFont());

	    // Width calculations for antecedent and consequent nodes
	    int antecedentWidth = calculateMaxNodeWidth(rule.getAntecedent(), metrics);
	    int consequentWidth = calculateMaxNodeWidth(rule.getConsequent(), metrics);

	    // Width needed for measures area: fixed label + bar + spacing + some margin
	    int measureLabelWidth = 150; // must match drawMeasureValues labelWidth
	    int barSpacing = 15;          // must match drawMeasureValues barSpacing
	    int measureBarWidth = BAR_WIDTH;
	    int measuresWidth = measureLabelWidth + barSpacing + measureBarWidth;

	    // Calculate total width as max of nodes width and measures width
	    int contentWidth = Math.max(antecedentWidth + HORIZONTAL_GAP + consequentWidth, measuresWidth);

	    // Height calculations for nodes (antecedent and consequent)
	    int antecedentHeight = calculateTotalNodeHeight(rule.getAntecedent(), metrics);
	    int consequentHeight = calculateTotalNodeHeight(rule.getConsequent(), metrics);
	    int contentHeight = Math.max(antecedentHeight, consequentHeight);

	    // Height reserved for measures: each line + vertical gaps + margin
	    int measureCount = rule.getMeasures().size();
	    int lineHeight = metrics.getHeight() + VERTICAL_GAP;
	    int measureHeight = measureCount * lineHeight;

	    // Add top and bottom content margins
	    int totalHeight = contentHeight + measureHeight + 2 * CONTENT_MARGIN;

	    int totalWidth = contentWidth + 2 * CONTENT_MARGIN;

	    return new Dimension(totalWidth, totalHeight);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D graphics = (Graphics2D) g.create();
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		FontMetrics metrics = graphics.getFontMetrics();

		layoutNodeBounds(metrics);
		drawNodes(graphics);
		drawConnectingArrow(graphics);
		drawMeasureValues(graphics, metrics);
		graphics.dispose();
	}

	/**
	 * Calculate the width required for the widest node in the rule.
	 */
	private int calculateMaxNodeWidth(List<String> labels, FontMetrics metrics) {
		int maxWidth = 0;
		for (String label : labels) {
			int width = metrics.stringWidth(label) + NODE_HORIZONTAL_PADDING;
			maxWidth = Math.max(maxWidth, width);
		}
		return maxWidth;
	}

	/**
	 * Calculate the total height required to display all the nodes.
	 */
	private int calculateTotalNodeHeight(List<String> labels, FontMetrics metrics) {
		int totalHeight = labels.size() * (metrics.getHeight() + NODE_VERTICAL_PADDING)
				+ (labels.size() + 1) * VERTICAL_GAP;
		return totalHeight;
	}

	/**
	 * Layout the node bounds based on the preferred size and font metrics.
	 */
	private void layoutNodeBounds(FontMetrics metrics) {
		antecedentNodeBounds.clear();
		consequentNodeBounds.clear();

		Dimension prefSize = getPreferredSize();
		int availableHeight = prefSize.height - rule.getMeasures().size() * (metrics.getHeight() + 6) - CONTENT_MARGIN;

		int antecedentX = CONTENT_MARGIN;
		int antecedentY = CONTENT_MARGIN
				+ (availableHeight - calculateTotalNodeHeight(rule.getAntecedent(), metrics)) / 2 + VERTICAL_GAP;
		for (String label : rule.getAntecedent()) {
			int width = metrics.stringWidth(label) + NODE_HORIZONTAL_PADDING;
			int height = metrics.getHeight() + NODE_VERTICAL_PADDING;
			antecedentNodeBounds.add(new Rectangle(antecedentX, antecedentY, width, height));
			antecedentY += height + VERTICAL_GAP;
		}

		int largestAntecedentWidth = antecedentNodeBounds.stream().mapToInt(rect -> rect.width).max().orElse(0);
		int consequentX = CONTENT_MARGIN + largestAntecedentWidth + HORIZONTAL_GAP;
		int consequentY = CONTENT_MARGIN
				+ (availableHeight - calculateTotalNodeHeight(rule.getConsequent(), metrics)) / 2 + VERTICAL_GAP;
		for (String label : rule.getConsequent()) {
			int width = metrics.stringWidth(label) + NODE_HORIZONTAL_PADDING;
			int height = metrics.getHeight() + NODE_VERTICAL_PADDING;
			consequentNodeBounds.add(new Rectangle(consequentX, consequentY, width, height));
			consequentY += height + VERTICAL_GAP;
		}
	}

	/**
	 * Draw all nodes (antecedent and consequent).
	 */
	private void drawNodes(Graphics2D graphics) {
		for (int i = 0; i < antecedentNodeBounds.size(); i++) {
			drawSingleNode(graphics, rule.getAntecedent().get(i), antecedentNodeBounds.get(i));
		}
		for (int i = 0; i < consequentNodeBounds.size(); i++) {
			drawSingleNode(graphics, rule.getConsequent().get(i), consequentNodeBounds.get(i));
		}
	}

	/**
	 * Draw an arrow between the last antecedent and the first consequent.
	 */
	private void drawConnectingArrow(Graphics2D graphics) {
		if (antecedentNodeBounds.isEmpty() || consequentNodeBounds.isEmpty()) {
			return;
		}
		int antecedentMinY = antecedentNodeBounds.stream().mapToInt(rect -> rect.y).min().orElse(0);
		int antecedentMaxY = antecedentNodeBounds.stream().mapToInt(rect -> rect.y + rect.height).max().orElse(0);
		int antecedentMaxWidth = antecedentNodeBounds.stream().mapToInt(rect -> rect.width).max().orElse(0);

		int consequentMinY = consequentNodeBounds.stream().mapToInt(rect -> rect.y).min().orElse(0);
		int consequentMaxY = consequentNodeBounds.stream().mapToInt(rect -> rect.y + rect.height).max().orElse(0);
		int consequentX = consequentNodeBounds.stream().mapToInt(rect -> rect.x).min().orElse(CONTENT_MARGIN);

		int arrowStartX = CONTENT_MARGIN + antecedentMaxWidth + MARGIN_ARROW;
		int arrowEndX = consequentX - MARGIN_ARROW;
		int arrowStartY = (antecedentMinY + antecedentMaxY) / 2;
		int arrowEndY = (consequentMinY + consequentMaxY) / 2;

		graphics.setColor(Color.BLUE);
//		graphics.setStroke(new BasicStroke(2));
		drawArrowHeadLine(graphics, arrowStartX, arrowStartY, arrowEndX, arrowEndY);
	}

	/**
	 * Draw the measure values under the rule with a horizontal bar showing the
	 * normalized value for each measure, using a different color for each.
	 */
	/**
	 * Draws the rule's measure text and bars
	 */
	private void drawMeasureValues(Graphics2D graphics, FontMetrics metrics) {
	    graphics.setFont(DEFAULT_FONT);

	    int measureCount = rule.getMeasures().size();
	    int lineHeight = metrics.getHeight() + VERTICAL_GAP;

	    int totalMeasuresHeight = measureCount * lineHeight;

	    // Start y so the bottom of last measure is CONTENT_MARGIN above panel bottom
	    int y = getHeight() - CONTENT_MARGIN - totalMeasuresHeight + metrics.getAscent();

	    int x = CONTENT_MARGIN;
	    int labelWidth = 150; // Fixed width for labels
	    int barSpacing = 15;  // Gap between label and bar

	    int measureIndex = 0;
	    for (Map.Entry<String, String> entry : rule.getMeasures().entrySet()) {
	        String name = entry.getKey();
	        String rawValue = entry.getValue();
	        String strValue = rawValue;
	        double value;
	        try {
	            value = Double.parseDouble(rawValue);
	            strValue = String.format("%.5f", value); // format to max 5 digits after decimal
	        } catch (NumberFormatException e) {
	            value = 0;
	        }

	        // Draw the label with value
	        String label = String.format("%-10s", name);
	        String fullLabel = label + ": " + strValue;
	        graphics.setColor(Color.BLACK);
	        graphics.drawString(fullLabel, x, y);

	        // Draw the bar aligned in a fixed column
	        Double min = minValues.get(name);
	        Double max = maxValues.get(name);
	        if (min != null && max != null && max > min) {
	            double ratio = (value - min) / (max - min);
	            int barLength = (int) (ratio * BAR_WIDTH);
	            int barX = x + labelWidth + barSpacing;
	            int barY = y - metrics.getAscent() + (metrics.getAscent() + metrics.getDescent() - BAR_HEIGHT) / 2;

	            Color color = MEASURE_COLORS.get(measureIndex % MEASURE_COLORS.size());
	            graphics.setColor(color);
	            graphics.fillRect(barX, barY, barLength, BAR_HEIGHT);

	            graphics.setColor(Color.GRAY);
	            graphics.drawRect(barX, barY, BAR_WIDTH, BAR_HEIGHT);
	        }

	        y += lineHeight;
	        measureIndex++;
	    }
	}


	/**
	 * Get the max width of the text for any measure.
	 */
	private int getMaxMeasureTextWidth(FontMetrics metrics) {
		int max = 0;
		for (Map.Entry<String, String> entry : rule.getMeasures().entrySet()) {
			String text = entry.getKey() + ": " + entry.getValue();
			max = Math.max(max, metrics.stringWidth(text) + 110); // extra space for bar
		}
		return max;
	}

	/**
	 * Draw a single node.
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
	 * Draw an arrow head line between two points.
	 */
	private void drawArrowHeadLine(Graphics2D graphics, int x1, int y1, int x2, int y2) {
		Line2D line = new Line2D.Double(x1, y1, x2, y2);
		graphics.draw(line);
		double angle = Math.atan2(y2 - y1, x2 - x1);
		int arrowSize = 10;
		int x3 = x2 - (int) (arrowSize * Math.cos(angle - Math.PI / 6));
		int y3 = y2 - (int) (arrowSize * Math.sin(angle - Math.PI / 6));
		int x4 = x2 - (int) (arrowSize * Math.cos(angle + Math.PI / 6));
		int y4 = y2 - (int) (arrowSize * Math.sin(angle + Math.PI / 6));
		graphics.draw(new Line2D.Double(x2, y2, x3, y3));
		graphics.draw(new Line2D.Double(x2, y2, x4, y4));
	}

	/** Get the rule **/
	public Rule getRule() {
		return rule;
	}
}
