package ca.pfv.spmf.gui.visual_pattern_viewer.subgraphs;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.UIManager;

import ca.pfv.spmf.gui.visual_pattern_viewer.subgraphs.Subgraph.Edge;
import ca.pfv.spmf.gui.visual_pattern_viewer.subgraphs.Subgraph.Vertex;

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
 * 
 * Do not remove copyright and license information.
 */
/**
 * A Swing panel that visualizes a single subgraph pattern. Vertices are drawn
 * as circles arranged in a circular layout and edges are drawn as lines between
 * them. Measure values are shown as labelled bars below the graph drawing area.
 *
 * @author Philippe Fournier-Viger
 */
public class SubgraphPanel extends JPanel {

    private static final long serialVersionUID = 7312945670193847561L;

    /** Colours cycled through when drawing measure bars. */
    private static final List<Color> MEASURE_COLORS = List.of(
            Color.BLUE, Color.RED, Color.GREEN, Color.MAGENTA,
            Color.ORANGE, Color.CYAN, Color.PINK, Color.YELLOW,
            new Color(128, 0, 128),   // Purple
            new Color(0, 128, 128)    // Teal
    );

    /** Radius of each vertex circle in pixels. */
    private static final int VERTEX_RADIUS = 20;

    /** Margin around the panel content. */
    private static final int CONTENT_MARGIN = 20;

    /** Vertical gap used between items (e.g. measure rows). */
    private static final int VERTICAL_GAP = 5;

    /** Width of the measure progress bar. */
    private static final int BAR_WIDTH = 100;

    /** Height of the measure progress bar. */
    private static final int BAR_HEIGHT = 10;

    /** Preferred side length of the graph drawing area (square). */
    private static final int GRAPH_AREA_SIZE = 200;

    /** Consistent font across the panel. */
    private static final Font DEFAULT_FONT =
            UIManager.getFont("Label.font").deriveFont(Font.PLAIN);

    /** Stroke used to draw edges. */
    private static final BasicStroke EDGE_STROKE = new BasicStroke(1.5f);

    /** Stroke used to draw the vertex circle border. */
    private static final BasicStroke VERTEX_STROKE = new BasicStroke(1.5f);

    /** The subgraph to display. */
    private final Subgraph subgraph;

    /** Pre-computed centre positions for each vertex (keyed by vertex id). */
    private final Map<Integer, Point> vertexPositions = new HashMap<>();

    /** Minimum observed value for each numeric measure (for bar scaling). */
    private final Map<String, Double> minValues;

    /** Maximum observed value for each numeric measure (for bar scaling). */
    private final Map<String, Double> maxValues;

    /**
     * Constructs a panel for visualising the given subgraph.
     *
     * @param subgraph  the subgraph pattern to display (must not be null)
     * @param minValues map of measure name → minimum numeric value, or null
     * @param maxValues map of measure name → maximum numeric value, or null
     * @throws NullPointerException if subgraph is null
     */
    public SubgraphPanel(Subgraph subgraph,
            Map<String, Double> minValues,
            Map<String, Double> maxValues) {
        this.subgraph = Objects.requireNonNull(subgraph, "subgraph must not be null");
        this.minValues = minValues;
        this.maxValues = maxValues;
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createLineBorder(Color.GRAY));
        setFont(DEFAULT_FONT);
    }

    /**
     * Calculates the preferred size based on the graph drawing area and the space
     * required for measure rows below it.
     *
     * @return preferred panel dimensions
     */
    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(DEFAULT_FONT);
        int measureHeight = subgraph.getMeasures().size() * (fm.getHeight() + VERTICAL_GAP);
        int totalWidth  = GRAPH_AREA_SIZE + 2 * CONTENT_MARGIN;
        int totalHeight = GRAPH_AREA_SIZE + 2 * CONTENT_MARGIN + measureHeight + CONTENT_MARGIN;
        return new Dimension(Math.max(totalWidth, 250), Math.max(totalHeight, 120));
    }

    /**
     * Paints the subgraph (edges then vertices) and the measure bars below.
     *
     * @param g the Graphics context provided by Swing
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(DEFAULT_FONT);

        computeVertexPositions();
        drawEdges(g2);
        drawVertices(g2);
        drawMeasureValues(g2, g2.getFontMetrics());

        g2.dispose();
    }

    /**
     * Computes the (x, y) centre position for each vertex using a circular layout.
     * When there is only one vertex it is placed at the centre of the drawing area.
     */
    private void computeVertexPositions() {
        vertexPositions.clear();

        List<Vertex> vertices = subgraph.getVertices();
        int n = vertices.size();
        if (n == 0) {
            return;
        }

        // Centre of the drawing area
        int cx = CONTENT_MARGIN + GRAPH_AREA_SIZE / 2;
        int cy = CONTENT_MARGIN + GRAPH_AREA_SIZE / 2;

        if (n == 1) {
            vertexPositions.put(vertices.get(0).getId(), new Point(cx, cy));
            return;
        }

        // Radius of the circle on which vertices are arranged
        int layoutRadius = GRAPH_AREA_SIZE / 2 - VERTEX_RADIUS - 5;

        for (int i = 0; i < n; i++) {
            double angle = 2.0 * Math.PI * i / n - Math.PI / 2.0;
            int x = (int) Math.round(cx + layoutRadius * Math.cos(angle));
            int y = (int) Math.round(cy + layoutRadius * Math.sin(angle));
            vertexPositions.put(vertices.get(i).getId(), new Point(x, y));
        }
    }

    /**
     * Draws all edges of the subgraph as lines between the pre-computed vertex
     * centres. The edge label is drawn at the midpoint of each edge.
     *
     * @param g2 the Graphics2D context
     */
    private void drawEdges(Graphics2D g2) {
        g2.setStroke(EDGE_STROKE);
        FontMetrics fm = g2.getFontMetrics();

        for (Edge edge : subgraph.getEdges()) {
            Point src = vertexPositions.get(edge.getSourceId());
            Point dst = vertexPositions.get(edge.getTargetId());
            if (src == null || dst == null) {
                continue;
            }

            // Draw the line
            g2.setColor(Color.DARK_GRAY);
            g2.drawLine(src.x, src.y, dst.x, dst.y);

            // Draw the edge label at the midpoint
            String edgeLabel = edge.getLabel();
            if (edgeLabel != null && !edgeLabel.isEmpty()) {
                int midX = (src.x + dst.x) / 2;
                int midY = (src.y + dst.y) / 2;
                int labelW = fm.stringWidth(edgeLabel);
                // Small white background pill behind the label for readability
                g2.setColor(Color.WHITE);
                g2.fillRect(midX - labelW / 2 - 2,
                        midY - fm.getAscent(),
                        labelW + 4,
                        fm.getHeight());
                g2.setColor(new Color(80, 80, 80));
                g2.drawString(edgeLabel, midX - labelW / 2, midY);
            }
        }
    }

    /**
     * Draws all vertices as filled circles with their labels centred inside.
     *
     * @param g2 the Graphics2D context
     */
    private void drawVertices(Graphics2D g2) {
        g2.setStroke(VERTEX_STROKE);
        FontMetrics fm = g2.getFontMetrics();

        for (Vertex vertex : subgraph.getVertices()) {
            Point centre = vertexPositions.get(vertex.getId());
            if (centre == null) {
                continue;
            }

            int x = centre.x - VERTEX_RADIUS;
            int y = centre.y - VERTEX_RADIUS;
            int d = VERTEX_RADIUS * 2;

            // Fill
            g2.setColor(new Color(180, 210, 255));
            g2.fillOval(x, y, d, d);

            // Border
            g2.setColor(new Color(60, 100, 180));
            g2.drawOval(x, y, d, d);

            // Vertex label centred in circle
            String label = vertex.getLabel();
            int textX = centre.x - fm.stringWidth(label) / 2;
            int textY = centre.y + (fm.getAscent() - fm.getDescent()) / 2;
            g2.setColor(Color.BLACK);
            g2.drawString(label, textX, textY);
        }
    }

    /**
     * Draws the measure labels and progress bars below the graph drawing area.
     *
     * @param g2      the Graphics2D context
     * @param metrics font metrics for the current font
     */
    private void drawMeasureValues(Graphics2D g2, FontMetrics metrics) {
        g2.setFont(DEFAULT_FONT);
        int lineHeight = metrics.getHeight() + VERTICAL_GAP;
        int measureCount = subgraph.getMeasures().size();
        int totalMeasuresHeight = measureCount * lineHeight;

        // Place measures so the last one sits CONTENT_MARGIN above the bottom edge
        int y = getHeight() - CONTENT_MARGIN - totalMeasuresHeight + metrics.getAscent();
        int x = CONTENT_MARGIN;

        int measureIndex = 0;
        for (Map.Entry<String, String> entry : subgraph.getMeasures().entrySet()) {
            String name     = entry.getKey();
            String strValue = entry.getValue();

            boolean isDouble = false;
            double value = 0;
            try {
                value = Double.parseDouble(strValue);
                isDouble = true;
            } catch (NumberFormatException e) {
                // non-numeric: fall through
            }

            if (isDouble) {
                String text = name + ": " + formatValue(value);
                g2.setColor(Color.BLACK);
                g2.drawString(text, x, y);

                Double min = (minValues != null) ? minValues.get(name) : null;
                Double max = (maxValues != null) ? maxValues.get(name) : null;
                if (min != null && max != null && max > min) {
                    double ratio    = (value - min) / (max - min);
                    int    barLength = (int) (ratio * BAR_WIDTH);
                    int    barX      = x + metrics.stringWidth(text) + 10;
                    int    barY      = y - metrics.getAscent()
                            + (metrics.getAscent() + metrics.getDescent() - BAR_HEIGHT) / 2;

                    Color color = MEASURE_COLORS.get(measureIndex % MEASURE_COLORS.size());
                    g2.setColor(color);
                    g2.fillRect(barX, barY, barLength, BAR_HEIGHT);
                    g2.setColor(Color.GRAY);
                    g2.drawRect(barX, barY, BAR_WIDTH, BAR_HEIGHT);
                }
            } else {
                g2.setColor(Color.BLACK);
                g2.drawString(name + ": " + strValue, x, y);
            }

            y += lineHeight;
            measureIndex++;
        }
    }

    /**
     * Formats a double value to four decimal places.
     *
     * @param val the value to format
     * @return formatted string
     */
    private String formatValue(double val) {
        return String.format("%.4f", val);
    }

    /**
     * Returns the subgraph displayed by this panel.
     *
     * @return the subgraph
     */
    public Subgraph getSubgraph() {
        return subgraph;
    }
}