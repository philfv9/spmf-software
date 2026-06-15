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
 * Supports visualization of negative rules where the antecedent or consequent
 * is preceded by "NOT". Negated sides are displayed with a distinct visual style:
 * a red-tinted background, a "NOT" label above the node block.
 * Both sides are always vertically aligned regardless of whether a "NOT" label
 * is present on one side.
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

	/** List of colors for the measure rectangles */
	private static final List<Color> MEASURE_COLORS = List.of(
		Color.BLUE, Color.RED, Color.GREEN, Color.MAGENTA,
		Color.ORANGE, Color.CYAN, Color.PINK, Color.YELLOW,
		new Color(128, 0, 128), // Purple
		new Color(0, 128, 128)  // Teal
	);

	/** Background color for normal (positive) nodes */
	private static final Color NORMAL_NODE_COLOR = new Color(240, 240, 240);

	/** Background color for negated nodes (red-tinted) */
	private static final Color NEGATED_NODE_COLOR = new Color(255, 210, 210);

	/** Border color for negated nodes */
	private static final Color NEGATED_NODE_BORDER_COLOR = new Color(180, 0, 0);

	/** Text color for the "NOT" label drawn above negated nodes */
	private static final Color NOT_LABEL_COLOR = new Color(180, 0, 0);

	/** Font used for drawing the "NOT" label above negated nodes */
	private static final Font NOT_LABEL_FONT = UIManager.getFont("Label.font") != null
			? UIManager.getFont("Label.font").deriveFont(Font.BOLD, 9f)
			: new Font("SansSerif", Font.BOLD, 9);

	/** Margin around the panel content. */
	private static final int CONTENT_MARGIN = 10;

	/** Vertical space between stacked nodes. */
	private static final int VERTICAL_GAP = 5;

	/** Horizontal padding inside each node box. */
	private static final int NODE_HORIZONTAL_PADDING = 20;

	/** Vertical padding inside each node box. */
	private static final int NODE_VERTICAL_PADDING = 4;

	/** Margin between arrow and node edges */
	private static final int MARGIN_ARROW = 5;

	/** Horizontal gap between antecedent and consequent columns */
	private static final int HORIZONTAL_GAP = 50;

	/** Bar width for measures */
	private static final int BAR_WIDTH = 100;

	/** Bar height for measures */
	private static final int BAR_HEIGHT = 10;

	/**
	 * Extra vertical space reserved above the entire node area (both sides)
	 * to accommodate the "NOT" label when either side is negated.
	 * This is applied uniformly so both sides stay aligned.
	 */
	private static final int NOT_LABEL_EXTRA_HEIGHT = 16;

	/** The rectangles that will be drawn for the antecedent nodes */
	private final List<Rectangle> antecedentNodeBounds = new ArrayList<>();

	/** The rectangles that will be drawn for the consequent nodes */
	private final List<Rectangle> consequentNodeBounds = new ArrayList<>();

	/** Consistent font across panel */
	private static final Font DEFAULT_FONT = UIManager.getFont("Label.font") != null
			? UIManager.getFont("Label.font").deriveFont(Font.PLAIN)
			: new Font("SansSerif", Font.PLAIN, 12);

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

	/**
	 * Returns whether any side of this rule is negated.
	 * Used to determine if we need extra space at the top for the "NOT" label.
	 *
	 * @return true if at least one side (antecedent or consequent) is negated
	 */
	private boolean hasAnyNegation() {
		return rule.isAntecedentNegated() || rule.isConsequentNegated();
	}

	@Override
	public Dimension getPreferredSize() {
		FontMetrics metrics = getFontMetrics(getFont());

		// Width calculations for antecedent and consequent nodes
		int antecedentWidth = calculateMaxNodeWidth(rule.getAntecedent(), metrics);
		int consequentWidth = calculateMaxNodeWidth(rule.getConsequent(), metrics);

		// Width needed for measures area: fixed label + bar + spacing + some margin
		int measureLabelWidth = 150; // must match drawMeasureValues labelWidth
		int barSpacing = 15;         // must match drawMeasureValues barSpacing
		int measuresWidth = measureLabelWidth + barSpacing + BAR_WIDTH;

		// Calculate total width as max of nodes width and measures width
		int contentWidth = Math.max(antecedentWidth + HORIZONTAL_GAP + consequentWidth, measuresWidth);

		// Height for node area: take the max of both sides' node heights
		int antecedentNodeHeight = calculateTotalNodeHeight(rule.getAntecedent(), metrics);
		int consequentNodeHeight = calculateTotalNodeHeight(rule.getConsequent(), metrics);
		int nodeAreaHeight = Math.max(antecedentNodeHeight, consequentNodeHeight);

		// If either side is negated, add extra space at top for the "NOT" label.
		// This extra space is added once (uniformly) so both sides remain aligned.
		if (hasAnyNegation()) {
			nodeAreaHeight += NOT_LABEL_EXTRA_HEIGHT;
		}

		// Height reserved for measures: each line + vertical gaps
		int measureCount = rule.getMeasures().size();
		int lineHeight = metrics.getHeight() + VERTICAL_GAP;
		int measureHeight = measureCount * lineHeight;

		// Total height: node area + measures + top and bottom margins
		int totalHeight = nodeAreaHeight + measureHeight + 2 * CONTENT_MARGIN;
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
	 * Calculate the width required for the widest node in the given list.
	 *
	 * @param labels  list of item label strings
	 * @param metrics font metrics used for measuring text width
	 * @return the maximum node width in pixels
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
	 * Calculate the total height required to display all the nodes in the list.
	 *
	 * @param labels  list of item label strings
	 * @param metrics font metrics used for measuring text height
	 * @return the total height in pixels for all nodes stacked vertically
	 */
	private int calculateTotalNodeHeight(List<String> labels, FontMetrics metrics) {
		return labels.size() * (metrics.getHeight() + NODE_VERTICAL_PADDING)
				+ (labels.size() + 1) * VERTICAL_GAP;
	}

	/**
	 * Layout the node bounds based on the preferred size and font metrics.
	 * <p>
	 * Both antecedent and consequent are laid out using the same top Y offset
	 * so that they are always vertically aligned. When either side is negated,
	 * a uniform extra offset is added at the top for both sides, leaving room
	 * for the "NOT" label. The "NOT" label itself is drawn only above the
	 * negated side.
	 * </p>
	 *
	 * @param metrics the font metrics used for layout calculations
	 */
	private void layoutNodeBounds(FontMetrics metrics) {
		antecedentNodeBounds.clear();
		consequentNodeBounds.clear();

		// Compute height used by measures at the bottom
		int measureCount = rule.getMeasures().size();
		int lineHeight = metrics.getHeight() + VERTICAL_GAP;
		int measureHeight = measureCount * lineHeight;

		// Total node area height available (panel height minus margins and measures)
		int nodeAreaHeight = getHeight() - 2 * CONTENT_MARGIN - measureHeight;

		// Extra top offset applied uniformly to BOTH sides when any side is negated,
		// so both sides start at the same Y regardless of which one has "NOT".
		int uniformTopOffset = hasAnyNegation() ? NOT_LABEL_EXTRA_HEIGHT : 0;

		// The usable node area after reserving space for the "NOT" label
		int usableNodeAreaHeight = nodeAreaHeight - uniformTopOffset;

		// --- Antecedent layout ---
		int antecedentNodeHeight = calculateTotalNodeHeight(rule.getAntecedent(), metrics);
		// Center the antecedent block vertically within the usable area,
		// then shift down by uniformTopOffset so both sides share the same origin
		int antecedentX = CONTENT_MARGIN;
		int antecedentY = CONTENT_MARGIN
				+ uniformTopOffset
				+ Math.max(0, (usableNodeAreaHeight - antecedentNodeHeight) / 2)
				+ VERTICAL_GAP;

		for (String label : rule.getAntecedent()) {
			int width = metrics.stringWidth(label) + NODE_HORIZONTAL_PADDING;
			int height = metrics.getHeight() + NODE_VERTICAL_PADDING;
			antecedentNodeBounds.add(new Rectangle(antecedentX, antecedentY, width, height));
			antecedentY += height + VERTICAL_GAP;
		}

		// --- Consequent layout ---
		int consequentNodeHeight = calculateTotalNodeHeight(rule.getConsequent(), metrics);
		int largestAntecedentWidth = antecedentNodeBounds.stream()
				.mapToInt(rect -> rect.width).max().orElse(0);
		int consequentX = CONTENT_MARGIN + largestAntecedentWidth + HORIZONTAL_GAP;
		// Same uniform top offset so consequent aligns with antecedent
		int consequentY = CONTENT_MARGIN
				+ uniformTopOffset
				+ Math.max(0, (usableNodeAreaHeight - consequentNodeHeight) / 2)
				+ VERTICAL_GAP;

		for (String label : rule.getConsequent()) {
			int width = metrics.stringWidth(label) + NODE_HORIZONTAL_PADDING;
			int height = metrics.getHeight() + NODE_VERTICAL_PADDING;
			consequentNodeBounds.add(new Rectangle(consequentX, consequentY, width, height));
			consequentY += height + VERTICAL_GAP;
		}
	}

	/**
	 * Draw all nodes (antecedent and consequent).
	 * Negated sides receive a different visual treatment via {@link #drawSingleNode}.
	 * A "NOT" label is drawn above the node block for each negated side.
	 */
	private void drawNodes(Graphics2D graphics) {
		// Draw antecedent nodes
		for (int i = 0; i < antecedentNodeBounds.size(); i++) {
			drawSingleNode(graphics, rule.getAntecedent().get(i),
					antecedentNodeBounds.get(i), rule.isAntecedentNegated());
		}
		// Draw "NOT" label above antecedent block if negated
		if (rule.isAntecedentNegated() && !antecedentNodeBounds.isEmpty()) {
			drawNotLabel(graphics, antecedentNodeBounds);
		}

		// Draw consequent nodes
		for (int i = 0; i < consequentNodeBounds.size(); i++) {
			drawSingleNode(graphics, rule.getConsequent().get(i),
					consequentNodeBounds.get(i), rule.isConsequentNegated());
		}
		// Draw "NOT" label above consequent block if negated
		if (rule.isConsequentNegated() && !consequentNodeBounds.isEmpty()) {
			drawNotLabel(graphics, consequentNodeBounds);
		}
	}

	/**
	 * Draws a "NOT" label above the topmost node in a block of nodes.
	 * The label is centered horizontally over the widest node in the block.
	 *
	 * @param graphics   the Graphics2D context
	 * @param nodeBounds the list of node rectangles for one side
	 */
	private void drawNotLabel(Graphics2D graphics, List<Rectangle> nodeBounds) {
		// Find the top Y of the first (topmost) node
		int topY = nodeBounds.stream().mapToInt(r -> r.y).min().orElse(0);
		// Find the left X and max width of the block
		int leftX = nodeBounds.stream().mapToInt(r -> r.x).min().orElse(0);
		int maxWidth = nodeBounds.stream().mapToInt(r -> r.width).max().orElse(0);

		// Save current font and set NOT label font
		Font prevFont = graphics.getFont();
		graphics.setFont(NOT_LABEL_FONT);
		FontMetrics fm = graphics.getFontMetrics();

		String notText = "NOT";
		int textWidth = fm.stringWidth(notText);
		// Center the "NOT" text horizontally above the node block
		int textX = leftX + (maxWidth - textWidth) / 2;
		// Place it just above the top node, using font ascent for proper positioning
		int textY = topY - fm.getDescent() - 2;

		graphics.setColor(NOT_LABEL_COLOR);
		graphics.drawString(notText, textX, textY);

		// Restore previous font
		graphics.setFont(prevFont);
	}

	/**
	 * Draw an arrow between the antecedent block and the consequent block.
	 * The arrow is drawn horizontally between the vertical midpoints of each block.
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
		drawArrowHeadLine(graphics, arrowStartX, arrowStartY, arrowEndX, arrowEndY);
	}

	/**
	 * Draws the rule's measure text and bars.
	 * Each measure is shown as a label with its value and a colored progress bar
	 * indicating its relative position between min and max.
	 * The measures are drawn at the bottom of the panel.
	 *
	 * @param graphics the Graphics2D context
	 * @param metrics  the font metrics for the current font
	 */
	private void drawMeasureValues(Graphics2D graphics, FontMetrics metrics) {
		graphics.setFont(DEFAULT_FONT);

		int measureCount = rule.getMeasures().size();
		int lineHeight = metrics.getHeight() + VERTICAL_GAP;
		int totalMeasuresHeight = measureCount * lineHeight;

		// Pin measures to the bottom of the panel with a fixed margin
		int y = getHeight() - CONTENT_MARGIN - totalMeasuresHeight + metrics.getAscent();

		int x = CONTENT_MARGIN;
		int labelWidth = 150; // Fixed width for labels, must match getPreferredSize
		int barSpacing = 15;  // Gap between label column and bar, must match getPreferredSize

		int measureIndex = 0;
		for (Map.Entry<String, String> entry : rule.getMeasures().entrySet()) {
			String name = entry.getKey();
			String rawValue = entry.getValue();
			String strValue = rawValue;
			double value;
			try {
				value = Double.parseDouble(rawValue);
				// Format to max 5 digits after decimal point
				strValue = String.format("%.5f", value);
			} catch (NumberFormatException e) {
				value = 0;
			}

			// Draw the measure label and value
			String label = String.format("%-10s", name);
			String fullLabel = label + ": " + strValue;
			graphics.setColor(Color.BLACK);
			graphics.drawString(fullLabel, x, y);

			// Draw the normalized bar aligned in a fixed column
			Double min = minValues.get(name);
			Double max = maxValues.get(name);
			if (min != null && max != null && max > min) {
				double ratio = (value - min) / (max - min);
				int barLength = (int) (ratio * BAR_WIDTH);
				int barX = x + labelWidth + barSpacing;
				int barY = y - metrics.getAscent()
						+ (metrics.getAscent() + metrics.getDescent() - BAR_HEIGHT) / 2;

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
	 * Draw a single node.
	 * If the node is part of a negated side, it is drawn with a red-tinted
	 * background and a red border to visually indicate negation.
	 *
	 * @param graphics the Graphics2D context
	 * @param label    the text label to display inside the node
	 * @param bounds   the bounding rectangle of the node
	 * @param negated  true if the node belongs to a negated antecedent or consequent
	 */
	private void drawSingleNode(Graphics2D graphics, String label, Rectangle bounds, boolean negated) {
		// Choose background and border color based on negation
		Color bgColor = negated ? NEGATED_NODE_COLOR : NORMAL_NODE_COLOR;
		Color borderColor = negated ? NEGATED_NODE_BORDER_COLOR : Color.BLACK;

		// Fill the rounded rectangle background
		graphics.setColor(bgColor);
		graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10);

		// Draw the rounded rectangle border
		graphics.setColor(borderColor);
		graphics.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10);

		// Draw the label text centered inside the node
		FontMetrics metrics = graphics.getFontMetrics();
		int textX = bounds.x + (bounds.width - metrics.stringWidth(label)) / 2;
		int textY = bounds.y + (bounds.height - metrics.getHeight()) / 2 + metrics.getAscent();
		graphics.setColor(Color.BLACK);
		graphics.drawString(label, textX, textY);
	}

	/**
	 * Draw an arrow head line between two points.
	 *
	 * @param graphics the Graphics2D context
	 * @param x1       start X coordinate
	 * @param y1       start Y coordinate
	 * @param x2       end X coordinate
	 * @param y2       end Y coordinate
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