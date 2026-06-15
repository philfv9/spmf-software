// ==================== GraphViewerPanel.java ====================
package ca.pfv.spmf.gui.viewers.graphviewer;

import java.awt.BasicStroke;

/*
 * Copyright (c) 2008-2022 Philippe Fournier-Viger
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
 * SPMF. If not, see http://www.gnu.org/licenses/.
 */

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.MouseInputListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import ca.pfv.spmf.gui.preferences.PreferencesManager;
import ca.pfv.spmf.gui.viewers.graphviewer.graphlayout.AbstractGraphLayout;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GEdge;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GNode;
/*
 * Copyright (c) 2008-2022 Philippe Fournier-Viger
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
 * SPMF. If not, see http://www.gnu.org/licenses/.
 * 
 * Do not remove license and copyright information.
 */
/**
 * A special type of JPanel to visualize subgraphs.
 * Enhanced with multi-selection, box selection, improved rendering,
 * gradient node fill, pill-shaped edge labels, and keyboard shortcuts.
 *
 * @author Philippe Fournier-Viger
 */
public class GraphViewerPanel extends JPanel implements MouseInputListener {

    /** Serial UID */
    private static final long serialVersionUID = -9054590513003092459L;

    // ==================== Rendering Constants ====================

    /** Padding in pixels around edge label text inside the pill background */
    private static final int   EDGE_LABEL_PADDING = 3;

    /** Stroke width for the border of selected nodes */
    private static final float SELECTED_STROKE_W  = 2.5f;

    /** Default stroke width */
    private static final float DEFAULT_STROKE_W   = 1.0f;

    // ==================== Graph Components ====================

    /** Graph layout class */
    private AbstractGraphLayout graphLayoutGenerator;

    /** The list of nodes */
    private List<GNode> nodes;

    /** The list of edges */
    private List<GEdge> edges;

    // ==================== Mouse Interaction State ====================

    /** The node that is currently dragged by the mouse */
    private GNode currentlyDraggedNode = null;

    /** The node that the mouse is currently over */
    private GNode currentlyMouseOverNode = null;

    /** Mouse button left is pressed */
    boolean mouseLeftIsPressed = false;

    /** Mouse button right is pressed */
    boolean mouseRightIsPressed = false;

    /** Mouse previous X position */
    int mouseXPosition;

    /** Mouse previous Y position */
    int mouseYPosition;

    // ==================== Selection Features ====================

    /** Selected nodes for multi-selection */
    private Set<GNode> selectedNodes = new LinkedHashSet<>();

    /** Selection rectangle start point */
    private Point selectionStart = null;

    /** Selection rectangle end point */
    private Point selectionEnd = null;

    /** Control key is pressed for multi-selection */
    boolean controlKeyPressed = false;

    // ==================== Canvas Properties ====================

    /** Maximum allowed value of X for nodes */
    int maxX;

    /** Maximum allowed value of Y for nodes */
    int maxY;

    /** This indicates the level of zoom */
    double scaleLevel = 1.0;

    /** width of the canvas */
    int width;

    /** height of the canvas */
    int height;

    // ==================== Visual Appearance - Colors ====================

    /** Highlight color for nodes when the cursor hovers over them */
    Color NODE_HIGHLIGHT_COLOR = new Color(255, 220, 50);

    /** Selection color for nodes when they are selected */
    Color NODE_SELECTION_COLOR = new Color(255, 180, 0);

    /** Default fill color for nodes */
    private Color NODE_COLOR = new Color(230, 240, 255);

    /** Color for edges */
    private Color EDGE_COLOR = new Color(80, 80, 200);

    /** Color for node labels */
    private Color NODE_LABEL_COLOR = Color.BLACK;

    /** Color for edge labels */
    private Color EDGE_LABEL_COLOR = new Color(40, 40, 120);

    /** Color for node borders */
    private Color NODE_BORDER_COLOR = new Color(60, 80, 140);

    /** Background color of the canvas */
    private Color CANVAS_COLOR = Color.WHITE;

    /** Fill color for the box-selection rectangle */
    private Color SELECTION_RECT_COLOR = new Color(100, 149, 237, 55);

    /** Border color for the box-selection rectangle */
    private Color SELECTION_RECT_BORDER_COLOR = new Color(65, 105, 225, 180);

    // ==================== Visual Appearance - Text ====================

    /** Node text size in points */
    private static int NODE_TEXT_SIZE = 11;

    /** Edge text size in points */
    private static int EDGE_TEXT_SIZE = 10;

    // ==================== Display Options ====================

    /** Anti-aliasing is activated */
    private boolean ANTI_ALIASING_ACTIVATED = true;

    /** Show node IDs */
    private boolean SHOW_NODE_IDS = true;

    /** Show edge IDs */
    private boolean SHOW_EDGE_IDS = true;

    /** Show node labels */
    private boolean SHOW_NODE_LABELS = true;

    /** Show edge labels */
    private boolean SHOW_EDGE_LABELS = true;

    // ==================== Constructor ====================

    /**
     * Constructor
     *
     * @param graphLayoutGenerator the graph layout generator to use
     * @param i                    initial width of the canvas
     * @param j                    initial height of the canvas
     */
    public GraphViewerPanel(AbstractGraphLayout graphLayoutGenerator,
                            int i, int j) {
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resized(false);
            }
        });

        width  = i;
        height = j;

        nodes = new ArrayList<GNode>();
        edges = new ArrayList<GEdge>();

        this.graphLayoutGenerator = graphLayoutGenerator;

        addMouseMotionListener(this);
        addMouseListener(this);

        // Enable keyboard focus so Delete / Ctrl+A etc. work
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                handleKeyPressed(e);
            }
        });

        setBackground(CANVAS_COLOR);

        // Enable double buffering for smooth rendering
        setDoubleBuffered(true);

        resized(false);
    }

    // ==================== Keyboard Handling ====================

    /**
     * Handle key-press events for keyboard shortcuts.
     *
     * @param e the KeyEvent
     */
    private void handleKeyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_DELETE || code == KeyEvent.VK_BACK_SPACE) {
            // Delete selected nodes and their connected edges
            deleteSelectedNodes();
        } else if (code == KeyEvent.VK_A && e.isControlDown()) {
            // Ctrl+A: select all nodes
            selectAllNodes();
        } else if (code == KeyEvent.VK_ESCAPE) {
            // Escape: clear selection
            clearSelection();
        }
    }

    // ==================== Getters and Setters ====================

    /**
     * Get the canvas width.
     *
     * @return the width in pixels
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get the canvas height.
     *
     * @return the height in pixels
     */
    public int getHeight() {
        return height;
    }

    /**
     * Change the graph layout generator for this panel.
     *
     * @param graphLayoutGenerator a new graph layout generator
     */
    public void setGraphLayoutGenerator(AbstractGraphLayout graphLayoutGenerator) {
        this.graphLayoutGenerator = graphLayoutGenerator;
    }

    // ==================== Graph Management ====================

    /**
     * Add an edge to the graph.
     *
     * @param newEdge a new edge
     */
    public void addEdge(GEdge newEdge) {
        edges.add(newEdge);
    }

    /**
     * Add a node to the graph.
     *
     * @param newNode a new node
     */
    public void addNode(GNode newNode) {
        nodes.add(newNode);
    }

    /**
     * Remove all edges and nodes from the graph.
     */
    public void clear() {
        edges.clear();
        nodes.clear();
        selectedNodes.clear();
        currentlyDraggedNode   = null;
        currentlyMouseOverNode = null;
        selectionStart         = null;
        selectionEnd           = null;
        repaint();
    }

    /**
     * Get the number of edges in the graph.
     *
     * @return the edge count
     */
    public int getEdgeCount() {
        return edges.size();
    }

    /**
     * Get the number of nodes in the graph.
     *
     * @return the node count
     */
    public int getNodeCount() {
        return nodes.size();
    }

    // ==================== Selection Management ====================

    /**
     * Get the set of currently selected nodes.
     *
     * @return a new Set containing the selected nodes
     */
    public Set<GNode> getSelectedNodes() {
        return new HashSet<>(selectedNodes);
    }

    /**
     * Clear all node selections.
     */
    public void clearSelection() {
        selectedNodes.clear();
        repaint();
    }

    /**
     * Select all nodes in the graph.
     */
    public void selectAllNodes() {
        selectedNodes.addAll(nodes);
        repaint();
    }

    /**
     * Delete all currently selected nodes and any edges connected to them.
     */
    public void deleteSelectedNodes() {
        if (selectedNodes.isEmpty()) {
            return;
        }
        // Remove edges connected to selected nodes
        edges.removeIf(edge -> selectedNodes.contains(edge.getFromNode())
                            || selectedNodes.contains(edge.getToNode()));
        // Remove the selected nodes themselves
        nodes.removeAll(selectedNodes);
        selectedNodes.clear();
        repaint();
    }

    /**
     * Select all nodes whose centre falls within the current selection rectangle.
     */
    private void selectNodesInRectangle() {
        if (selectionStart == null || selectionEnd == null) {
            return;
        }

        int x1 = Math.min(selectionStart.x, selectionEnd.x);
        int y1 = Math.min(selectionStart.y, selectionEnd.y);
        int x2 = Math.max(selectionStart.x, selectionEnd.x);
        int y2 = Math.max(selectionStart.y, selectionEnd.y);

        // Only clear existing selection if Ctrl is not held
        if (!controlKeyPressed) {
            selectedNodes.clear();
        }

        for (GNode node : nodes) {
            if (node.getCenterX() >= x1 && node.getCenterX() <= x2
                    && node.getCenterY() >= y1 && node.getCenterY() <= y2) {
                selectedNodes.add(node);
            }
        }
    }

    // ==================== Layout and Sizing ====================

    /**
     * Called when the JPanel is resized; recalculates constraint values
     * and optionally re-runs the layout.
     *
     * @param doAutoLayout whether to run auto-layout after resize
     */
    protected void resized(boolean doAutoLayout) {
        maxX = getWidth()  - GNode.getRadius();
        maxY = getHeight() - GNode.getRadius();

        // Make sure all nodes remain inside the canvas
        for (GNode node : nodes) {
            boolean outsideX = (node.getCenterX() >= maxX);
            boolean outsideY = (node.getCenterY() >= maxY);

            if (outsideX || outsideY) {
                int x = outsideX ? maxX : node.getCenterX();
                int y = outsideY ? maxY : node.getCenterY();
                node.updatePosition(x, y);
            }
        }

        setPreferredSize(new Dimension(width, height));
        revalidate();
        repaint();

        if (doAutoLayout) {
            autoLayout();
        }
    }

    /**
     * Perform automatic layout of graph nodes using the current layout generator.
     */
    public void autoLayout() {
        if (nodes.size() == 0) {
            return;
        }
        graphLayoutGenerator.autoLayout(edges, nodes, getWidth(), getHeight());
        repaint();
    }

    /**
     * Update the size of this panel.
     *
     * @param newWidth     the new width in pixels
     * @param newHeight    the new height in pixels
     * @param doAutoLayout true to run auto-layout after resizing
     */
    public void updateSize(int newWidth, int newHeight, boolean doAutoLayout) {
        width  = newWidth;
        height = newHeight;
        resized(doAutoLayout);
    }

    /**
     * Return the preferred size of the panel, which equals the canvas dimensions.
     *
     * @return the preferred Dimension
     */
    public Dimension getPreferredSize() {
        if (width == 0) {
            return super.getPreferredSize();
        } else {
            return new Dimension(width, height);
        }
    }

    // ==================== Visual Property Updates ====================

    /**
     * Update the node radius.
     *
     * @param newRadius the new radius in pixels
     */
    public void updateNodeSize(int newRadius) {
        GNode.changeRadiusSize(newRadius);
        maxX = getWidth()  - GNode.getRadius();
        maxY = getHeight() - GNode.getRadius();

        for (GNode node : nodes) {
            node.updatePosition(node.getCenterX(), node.getCenterY());
        }
        repaint();
    }

    /**
     * Update the node text size.
     *
     * @param newTextSize the new text size in points
     */
    public void updateNodeTextSize(int newTextSize) {
        NODE_TEXT_SIZE = newTextSize;

        for (GNode node : nodes) {
            node.updatePosition(node.getCenterX(), node.getCenterY());
        }
        repaint();
    }

    /**
     * Update the edge text size.
     *
     * @param newTextSize the new text size in points
     */
    public void updateEdgeTextSize(int newTextSize) {
        EDGE_TEXT_SIZE = newTextSize;
        repaint();
    }

    /**
     * Update the edge thickness.
     *
     * @param newThickness the new thickness in pixels
     */
    public void updateEdgeThickness(int newThickness) {
        GEdge.changeThickness(newThickness);
        repaint();
    }

    // ==================== Rendering ====================

    /**
     * Paint the component: draws the graph and then the selection rectangle
     * (if active).
     *
     * @param g the Graphics context
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Create a Graphics2D copy so we can safely dispose it
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            // Apply rendering hints for quality
            applyRenderingHints(g2);

            // Draw the graph (background, edges, nodes)
            drawTheVisual(g2);

            // Draw selection rectangle if a drag-selection is in progress
            if (selectionStart != null && selectionEnd != null) {
                drawSelectionRectangle(g2);
            }
        } finally {
            g2.dispose();
        }
    }

    /**
     * Apply rendering hints to the Graphics2D context.
     *
     * @param g2 the Graphics2D to configure
     */
    private void applyRenderingHints(Graphics2D g2) {
        if (ANTI_ALIASING_ACTIVATED) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                                RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                                RenderingHints.VALUE_STROKE_PURE);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        }
    }

    /**
     * Draw the full visual representation of the graph (background, edges, nodes).
     *
     * @param g2 the Graphics2D context
     */
    private void drawTheVisual(Graphics2D g2) {
        // Draw background
        g2.setColor(CANVAS_COLOR);
        g2.fillRect(0, 0, getWidth(), getHeight());

        // Draw edges first so nodes appear on top
        drawAllEdges(g2);

        // Draw nodes
        drawAllNodes(g2);
    }

    /**
     * Draw all edges in the graph.
     *
     * @param g2 the Graphics2D context
     */
    private void drawAllEdges(Graphics2D g2) {
        // Set the font for edge labels
        Font edgeFont = g2.getFont().deriveFont((float) EDGE_TEXT_SIZE);
        g2.setFont(edgeFont);

        g2.setColor(getEdgeColor());
        for (GEdge edge : edges) {
            drawEdge(g2,
                    edge.getFromNode().getCenterX(),
                    edge.getFromNode().getCenterY(),
                    edge.getToNode().getCenterX(),
                    edge.getToNode().getCenterY(),
                    edge.isDirected(), edge);
        }
    }

    /**
     * Draw all nodes in the graph.
     *
     * @param g2 the Graphics2D context
     */
    private void drawAllNodes(Graphics2D g2) {
        // Set the font for node labels
        Font nodeFont = g2.getFont().deriveFont(Font.PLAIN, (float) NODE_TEXT_SIZE);
        g2.setFont(nodeFont);

        for (GNode node : nodes) {
            drawNode(g2, node);
        }
    }

    /**
     * Draw a single node.
     *
     * @param g    the Graphics2D context
     * @param node the node to draw
     */
    private void drawNode(Graphics2D g, GNode node) {
        int tlx = node.getTopLeftX();
        int tly = node.getTopLeftY();
        int d   = GNode.getDiameter();

        boolean isSelected = selectedNodes.contains(node);
        boolean isHovered  = (node == currentlyMouseOverNode);

        // ---- Determine fill color based on node state ----
        Color fill = isSelected ? NODE_SELECTION_COLOR
                   : isHovered  ? NODE_HIGHLIGHT_COLOR
                                : NODE_COLOR;

        // Draw the vertex shape with a subtle gradient for a polished look
        GradientPaint gp = new GradientPaint(
                tlx,     tly,     fill.brighter(),
                tlx,     tly + d, fill.darker());
        g.setPaint(gp);
        g.fillOval(tlx, tly, d, d);

        // ---- Draw the border (thicker for selected nodes) ----
        float strokeW = isSelected ? SELECTED_STROKE_W : DEFAULT_STROKE_W;
        Color border  = isSelected ? NODE_SELECTION_COLOR.darker()
                                   : NODE_BORDER_COLOR;
        g.setStroke(new BasicStroke(strokeW,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(border);
        g.drawOval(tlx, tly, d, d);

        // ---- Draw a highlight ring for hovered (but not selected) nodes ----
        if (isHovered && !isSelected) {
            g.setColor(new Color(255, 200, 0, 120));
            g.setStroke(new BasicStroke(2.5f));
            g.drawOval(tlx - 2, tly - 2, d + 4, d + 4);
        }

        // Restore default stroke
        g.setStroke(new BasicStroke(DEFAULT_STROKE_W));

        // ---- Draw the node label ----
        drawNodeLabel(g, node);
    }

    /**
     * Draw the label (ID and/or name) for a node.
     *
     * @param g    the Graphics context
     * @param node the node whose label is drawn
     */
    private void drawNodeLabel(Graphics g, GNode node) {
        if (!SHOW_NODE_IDS && !SHOW_NODE_LABELS) {
            return;
        }

        String text = node.getIdAndNameAsString(SHOW_NODE_IDS, SHOW_NODE_LABELS);
        if (text == null || text.isEmpty()) {
            return;
        }

        FontMetrics fm = g.getFontMetrics();
        int sw = fm.stringWidth(text);
        int sh = fm.getAscent();

        int xlabel = node.getCenterX() - sw / 2;
        int ylabel = node.getCenterY() + sh / 2 - 1;

        g.setColor(NODE_LABEL_COLOR);
        g.drawString(text, xlabel, ylabel);
    }

    /**
     * Draw an edge (line with optional arrow head).
     *
     * @param g          the Graphics2D context
     * @param x1         start x position
     * @param y1         start y position
     * @param x2         end x position
     * @param y2         end y position
     * @param isDirected true if the edge should have an arrow head
     * @param edge       the GEdge model object
     */
    private void drawEdge(Graphics2D g, final int x1, final int y1,
                          final int x2, final int y2,
                          boolean isDirected, GEdge edge) {

        // Save the current transform so we can restore it after drawing
        AffineTransform currentTransform = g.getTransform();

        // Calculate the angle of the edge so we can rotate the drawing context
        double distanceX = x2 - x1;
        double distanceY = y2 - y1;
        double newAngle  = Math.atan2(distanceY, distanceX);

        AffineTransform newTransform =
                AffineTransform.getTranslateInstance(x1, y1);
        newTransform.concatenate(AffineTransform.getRotateInstance(newAngle));

        // Apply the rotation transform
        g.transform(newTransform);

        g.setColor(getEdgeColor());

        // Draw the line from the edge of the source node to the edge of the target node
        int arrowLength = (int) Math.sqrt(
                distanceX * distanceX + distanceY * distanceY)
                - GNode.getRadius();

        g.setStroke(new BasicStroke(GEdge.getEdgeThickness(),
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(0 + GNode.getRadius(), 0, arrowLength, 0);
        g.setStroke(new BasicStroke(DEFAULT_STROKE_W));

        // Draw the arrow head if the edge is directed
        if (isDirected) {
            int ah = GEdge.getArrowHeadSize();
            g.fillPolygon(
                    new int[]{ arrowLength, arrowLength - ah, arrowLength - ah, arrowLength },
                    new int[]{ 0,          -ah,               ah,               0 },
                    4);
        }

        // Restore the original transform before drawing the label
        g.setTransform(currentTransform);

        // Draw the edge label at the midpoint
        drawEdgeLabel(g, x1, y1, x2, y2, edge);
    }

    /**
     * Draw the label for an edge at its midpoint, with a pill-shaped
     * semi-transparent background for readability.
     *
     * @param g    the Graphics2D context
     * @param x1   start x position
     * @param y1   start y position
     * @param x2   end x position
     * @param y2   end y position
     * @param edge the GEdge model object
     */
    private void drawEdgeLabel(Graphics2D g, int x1, int y1,
                               int x2, int y2, GEdge edge) {
        if (!SHOW_EDGE_IDS && !SHOW_EDGE_LABELS) {
            return;
        }

        String text = edge.getIdAndNameAsString(SHOW_EDGE_IDS, SHOW_EDGE_LABELS);
        if (text == null || text.isEmpty()) {
            return;
        }

        FontMetrics fm = g.getFontMetrics();
        int sw = fm.stringWidth(text);
        int sh = fm.getHeight();

        // Centre of the edge
        int cx = (x1 + x2) / 2;
        int cy = (y1 + y2) / 2;

        // Pill background rectangle coordinates
        int rx = cx - sw / 2 - EDGE_LABEL_PADDING;
        int ry = cy - sh / 2 - EDGE_LABEL_PADDING;
        int rw = sw + EDGE_LABEL_PADDING * 2;
        int rh = sh + EDGE_LABEL_PADDING * 2;

        // Draw a semi-transparent canvas-coloured pill background
        g.setColor(new Color(
                CANVAS_COLOR.getRed(),
                CANVAS_COLOR.getGreen(),
                CANVAS_COLOR.getBlue(), 210));
        g.fillRoundRect(rx, ry, rw, rh, 6, 6);

        // Draw a subtle border around the pill
        g.setColor(new Color(
                EDGE_LABEL_COLOR.getRed(),
                EDGE_LABEL_COLOR.getGreen(),
                EDGE_LABEL_COLOR.getBlue(), 120));
        g.setStroke(new BasicStroke(0.8f));
        g.drawRoundRect(rx, ry, rw, rh, 6, 6);
        g.setStroke(new BasicStroke(DEFAULT_STROKE_W));

        // Draw the label text centred on the pill
        g.setColor(EDGE_LABEL_COLOR);
        g.drawString(text,
                cx - sw / 2,
                cy + fm.getAscent() / 2 - 1);
    }

    /**
     * Draw the box-selection rectangle using a dashed border.
     *
     * @param g2 the Graphics2D context
     */
    private void drawSelectionRectangle(Graphics2D g2) {
        int x = Math.min(selectionStart.x, selectionEnd.x);
        int y = Math.min(selectionStart.y, selectionEnd.y);
        int w = Math.abs(selectionEnd.x - selectionStart.x);
        int h = Math.abs(selectionEnd.y - selectionStart.y);

        // Semi-transparent fill
        g2.setColor(SELECTION_RECT_COLOR);
        g2.fillRoundRect(x, y, w, h, 4, 4);

        // Dashed border
        g2.setColor(SELECTION_RECT_BORDER_COLOR);
        g2.setStroke(new BasicStroke(1.5f,
                BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                0, new float[]{ 6f, 3f }, 0));
        g2.drawRoundRect(x, y, w, h, 4, 4);
        g2.setStroke(new BasicStroke(DEFAULT_STROKE_W));
    }

    // ==================== Mouse Event Handling ====================

    @Override
    public void mouseClicked(MouseEvent e) {
        // Reserved for future use
    }

    @Override
    public void mousePressed(MouseEvent event) {
        // Give the panel keyboard focus when the user clicks on it
        requestFocusInWindow();

        mouseXPosition = event.getX();
        mouseYPosition = event.getY();
        controlKeyPressed = event.isControlDown();

        if (event.getButton() == MouseEvent.BUTTON1) {
            mouseLeftIsPressed = true;
        }
        if (event.getButton() == MouseEvent.BUTTON3) {
            mouseRightIsPressed = true;
        }

        handleNodeSelection(event);
        updateMouseCursor();
    }

    /**
     * Handle node selection logic when the mouse is pressed.
     *
     * @param event the mouse event
     */
    private void handleNodeSelection(MouseEvent event) {
        Point mousePosition = this.getMousePosition();
        if (mousePosition == null) {
            return;
        }

        // Search nodes in reverse order so the topmost node is hit-tested first
        boolean foundNode = false;
        for (int i = nodes.size() - 1; i >= 0; i--) {
            GNode node = nodes.get(i);
            if (node.contains(mousePosition.x, mousePosition.y)) {
                currentlyDraggedNode   = node;
                currentlyMouseOverNode = node;

                // Handle multi-selection with Ctrl key
                if (controlKeyPressed) {
                    toggleNodeSelection(node);
                } else {
                    selectSingleNode(node);
                }

                foundNode = true;
                repaint();
                return;
            }
        }

        // If no node was clicked, start a box-selection or clear the selection
        if (!foundNode) {
            if (mouseLeftIsPressed && !controlKeyPressed) {
                selectedNodes.clear();
                selectionStart = event.getPoint();
                selectionEnd   = null;
            }
            currentlyDraggedNode = null;
        }
    }

    /**
     * Toggle the selection state of a node.
     *
     * @param node the node to toggle
     */
    private void toggleNodeSelection(GNode node) {
        if (selectedNodes.contains(node)) {
            selectedNodes.remove(node);
        } else {
            selectedNodes.add(node);
        }
    }

    /**
     * Select a single node, clearing all other selections first
     * (unless the node is already in the current selection).
     *
     * @param node the node to select
     */
    private void selectSingleNode(GNode node) {
        if (!selectedNodes.contains(node)) {
            selectedNodes.clear();
            selectedNodes.add(node);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // Finish box selection
        if (selectionStart != null && selectionEnd != null) {
            selectNodesInRectangle();
        }
        selectionStart       = null;
        selectionEnd         = null;
        currentlyDraggedNode = null;
        mouseLeftIsPressed   = false;
        mouseRightIsPressed  = false;
        updateMouseCursor();
        repaint();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // Reserved for future use
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // Reserved for future use
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        // Scroll the viewport to follow the drag
        Rectangle r = new Rectangle(e.getX(), e.getY(), 1, 1);
        scrollRectToVisible(r);

        Point mousePosition = this.getMousePosition();

        if (mouseLeftIsPressed && selectionStart != null
                && currentlyDraggedNode == null) {
            // Extend the box-selection rectangle
            selectionEnd = e.getPoint();
            repaint();

        } else if (mousePosition != null && mouseLeftIsPressed
                && currentlyDraggedNode != null) {
            // Drag the node (or all selected nodes if the dragged node is selected)
            handleNodeDragging(mousePosition);
        }

        updateMouseCursor();
        repaint();

        mouseXPosition = e.getX();
        mouseYPosition = e.getY();
    }

    /**
     * Handle the dragging of nodes.
     *
     * @param mousePosition the current mouse position
     */
    private void handleNodeDragging(Point mousePosition) {
        int newX = constrainX(mousePosition.x);
        int newY = constrainY(mousePosition.y);

        // If the dragged node is part of a multi-selection, move all selected nodes
        if (selectedNodes.contains(currentlyDraggedNode)) {
            moveSelectedNodes(newX, newY);
        } else {
            currentlyDraggedNode.updatePosition(newX, newY);
        }
    }

    /**
     * Move all selected nodes by the same delta as the dragged node.
     *
     * @param newX new X position for the dragged node
     * @param newY new Y position for the dragged node
     */
    private void moveSelectedNodes(int newX, int newY) {
        int deltaX = newX - currentlyDraggedNode.getCenterX();
        int deltaY = newY - currentlyDraggedNode.getCenterY();

        for (GNode node : selectedNodes) {
            int nodeX = constrainX(node.getCenterX() + deltaX);
            int nodeY = constrainY(node.getCenterY() + deltaY);
            node.updatePosition(nodeX, nodeY);
        }
    }

    /**
     * Constrain an X coordinate so that a node stays within the canvas bounds.
     *
     * @param x the raw x coordinate
     * @return the clamped x coordinate
     */
    private int constrainX(int x) {
        if (x < GNode.getRadius()) {
            return GNode.getRadius();
        } else if (x > maxX) {
            return maxX;
        }
        return x;
    }

    /**
     * Constrain a Y coordinate so that a node stays within the canvas bounds.
     *
     * @param y the raw y coordinate
     * @return the clamped y coordinate
     */
    private int constrainY(int y) {
        if (y < GNode.getRadius()) {
            return GNode.getRadius();
        } else if (y > maxY) {
            return maxY;
        }
        return y;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        Point mousePosition = this.getMousePosition();
        if (mousePosition == null) {
            return;
        }

        GNode previous = currentlyMouseOverNode;
        findNodeThatCursorIsOver(mousePosition);

        // Only repaint if the hovered node has changed (avoids unnecessary redraws)
        if (currentlyMouseOverNode != previous) {
            GraphViewerPanel.this.repaint();
        }

        updateMouseCursor();
    }

    /**
     * Find the topmost node that the cursor is currently over.
     *
     * @param mousePosition the current mouse position
     */
    private void findNodeThatCursorIsOver(Point mousePosition) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            GNode node = nodes.get(i);
            if (node.contains(mousePosition.x, mousePosition.y)) {
                currentlyMouseOverNode = node;
                return;
            }
        }
        currentlyMouseOverNode = null;
    }

    /**
     * Update the mouse cursor based on the current interaction state.
     */
    protected void updateMouseCursor() {
        if (currentlyMouseOverNode != null) {
            if (mouseLeftIsPressed) {
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            } else {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
        } else if (selectionStart != null) {
            // Show a crosshair while drawing a box selection
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        } else {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    // ==================== Export Functionality ====================

    /**
     * Ask the user for a file path and export the current graph as a PNG image
     * with high-quality rendering.
     */
    protected void exportAsPNG() {
        // Ask the user to choose the filename and path
        String outputFilePath = null;
        try {
            File path;
            // Get the last path used by the user, if there is one
            String previousPath = PreferencesManager.getInstance().getOutputFilePath();
            // If there is no previous path (first time user),
            // show the files in the "examples" package of the spmf distribution
            if (previousPath == null) {
                URL main = MainTestGraphViewer_GraphsFile.class
                        .getResource("MainTestGraphViewer_GraphsFile.class");
                if (!"file".equalsIgnoreCase(main.getProtocol())) {
                    path = null;
                } else {
                    path = new File(main.getPath());
                }
            } else {
                // Otherwise use the last path used by the user
                path = new File(previousPath);
            }

            // Ask the user to choose a file
            final JFileChooser fc;
            if (path != null) {
                fc = new JFileChooser(path.getAbsolutePath());
            } else {
                fc = new JFileChooser();
            }

            fc.setDialogTitle("Export graph as PNG");

            // Add file filter for PNG files
            FileNameExtensionFilter filter =
                    new FileNameExtensionFilter("PNG Images (*.png)", "png");
            fc.setFileFilter(filter);
            fc.setSelectedFile(new File("graph.png"));

            int returnVal = fc.showSaveDialog(GraphViewerPanel.this);

            // If the user chose a file
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File file = fc.getSelectedFile();
                outputFilePath = file.getPath();
                // Save the path of this folder for next time
                if (fc.getSelectedFile() != null) {
                    PreferencesManager.getInstance()
                            .setOutputFilePath(fc.getSelectedFile().getParent());
                }
            } else {
                // The user cancelled, so return
                return;
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "An error occurred while opening the save plot dialog."
                    + " ERROR MESSAGE = " + e.toString(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }

        try {
            // Add the .png extension if not already present
            if (!outputFilePath.toLowerCase().endsWith("png")) {
                outputFilePath = outputFilePath + ".png";
            }
            File outputFile = new File(outputFilePath);

            // Create a high-quality image and write it to disk
            BufferedImage image = createHighQualityImage();
            ImageIO.write(image, "png", outputFile);

            JOptionPane.showMessageDialog(this,
                    "Graph exported successfully to:\n" + outputFilePath,
                    "Export Successful", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "An error occurred while attempting to save the plot."
                    + " ERROR MESSAGE = " + e.toString(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Create a high-quality BufferedImage of the current graph.
     * The selection rectangle is intentionally excluded from the export.
     *
     * @return a BufferedImage with high-quality rendering
     */
    private BufferedImage createHighQualityImage() {
        BufferedImage image = new BufferedImage(
                getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        try {
            // Enable the highest quality rendering hints for the exported image
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                                RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION,
                                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
                                RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                                RenderingHints.VALUE_STROKE_PURE);

            // Draw the graph without the selection rectangle
            drawTheVisual(g2);

        } finally {
            g2.dispose();
        }
        return image;
    }

    // ==================== Update Override ====================

    @Override
    public void update(Graphics g) {
        super.update(g);
        paintComponent(g);
    }

    // ==================== Color Getters and Setters ====================

    /**
     * Get the node fill color.
     *
     * @return the current node color
     */
    public Color getNodeColor() {
        return NODE_COLOR;
    }

    /**
     * Set the node fill color and repaint.
     *
     * @param color the new node color
     */
    public void setNodeColor(Color color) {
        NODE_COLOR = color;
        repaint();
    }

    /**
     * Set the node text color and repaint.
     *
     * @param color the new node text color
     */
    public void setNodeTextColor(Color color) {
        NODE_LABEL_COLOR = color;
        repaint();
    }

    /**
     * Get the node text color.
     *
     * @return the current node text color
     */
    public Color getNodeTextColor() {
        return NODE_LABEL_COLOR;
    }

    /**
     * Get the edge text color.
     *
     * @return the current edge text color
     */
    public Color getEdgeTextColor() {
        return EDGE_LABEL_COLOR;
    }

    /**
     * Set the edge text color and repaint.
     *
     * @param color the new edge text color
     */
    public void setEdgeTextColor(Color color) {
        EDGE_LABEL_COLOR = color;
        repaint();
    }

    /**
     * Get the edge color.
     *
     * @return the current edge color
     */
    public Color getEdgeColor() {
        return EDGE_COLOR;
    }

    /**
     * Set the edge color and repaint.
     *
     * @param color the new edge color
     */
    public void setEdgeColor(Color color) {
        EDGE_COLOR = color;
        repaint();
    }

    /**
     * Get the canvas background color.
     *
     * @return the current canvas color
     */
    public Color getCanvasColor() {
        return CANVAS_COLOR;
    }

    /**
     * Set the canvas background color and repaint.
     *
     * @param cANVAS_COLOR the new canvas color
     */
    public void setCanvasColor(Color cANVAS_COLOR) {
        CANVAS_COLOR = cANVAS_COLOR;
        setBackground(cANVAS_COLOR);
        repaint();
    }

    /**
     * Get the node border color.
     *
     * @return the current node border color
     */
    public Color getNodeBorderColor() {
        return NODE_BORDER_COLOR;
    }

    /**
     * Set the node border color and repaint.
     *
     * @param color the new node border color
     */
    public void setNodeBorderColor(Color color) {
        NODE_BORDER_COLOR = color;
        repaint();
    }

    // ==================== Display Option Setters ====================

    /**
     * Activate or deactivate anti-aliasing.
     *
     * @param selected true to activate, false to deactivate
     */
    public void setAntiAliasing(boolean selected) {
        ANTI_ALIASING_ACTIVATED = selected;
        repaint();
    }

    /**
     * Get the current node text size in points.
     *
     * @return the node text size
     */
    public static int getNodeTextSize() {
        return NODE_TEXT_SIZE;
    }

    /**
     * Get the current edge text size in points.
     *
     * @return the edge text size
     */
    public static int getEdgeTextSize() {
        return EDGE_TEXT_SIZE;
    }

    /**
     * Show or hide edge IDs.
     *
     * @param selected true to show, false to hide
     */
    public void showEdgeIDs(boolean selected) {
        SHOW_EDGE_IDS = selected;
        repaint();
    }

    /**
     * Show or hide node IDs.
     *
     * @param selected true to show, false to hide
     */
    public void showNodeIDs(boolean selected) {
        SHOW_NODE_IDS = selected;
        repaint();
    }

    /**
     * Show or hide edge labels.
     *
     * @param selected true to show, false to hide
     */
    public void showEdgeLabels(boolean selected) {
        SHOW_EDGE_LABELS = selected;
        repaint();
    }

    /**
     * Show or hide node labels.
     *
     * @param selected true to show, false to hide
     */
    public void showNodeLabels(boolean selected) {
        SHOW_NODE_LABELS = selected;
        repaint();
    }
}