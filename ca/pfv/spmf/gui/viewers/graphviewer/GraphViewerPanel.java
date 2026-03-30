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
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
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
import ca.pfv.spmf.test.MainTestApriori_simple_saveToFile;

/**
 * A special type of JPanel to visualize subgraphs.
 * Enhanced with multi-selection, improved rendering, and better code organization.
 * 
 * @author Philippe Fournier-Viger
 *
 */
public class GraphViewerPanel extends JPanel implements MouseInputListener {
	/** Serial UID */
	private static final long serialVersionUID = -9054590513003092459L;

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
	private Set<GNode> selectedNodes = new HashSet<>();

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

	/** width */
	int width;

	/** height */
	int height;

	// ==================== Visual Appearance - Colors ====================
	/** Highlight color for nodes */
	Color NODE_HIGHLIGHT_COLOR = Color.YELLOW;

	/** Selection color for nodes */
	Color NODE_SELECTION_COLOR = new Color(255, 200, 0);

	/** Color for nodes */
	private Color NODE_COLOR = new Color(235, 235, 235);

	/** Color for edges */
	private Color EDGE_COLOR = Color.BLUE;

	/** Color for node labels */
	private Color NODE_LABEL_COLOR = Color.BLACK;

	/** Color for edge labels */
	private Color EDGE_LABEL_COLOR = Color.BLACK;

	/** Color for node borders */
	private Color NODE_BORDER_COLOR = Color.BLACK;

	/** Background color */
	private Color CANVAS_COLOR = Color.WHITE;

	/** Selection rectangle color */
	private Color SELECTION_RECT_COLOR = new Color(100, 100, 255, 50);

	/** Selection rectangle border color */
	private Color SELECTION_RECT_BORDER_COLOR = new Color(100, 100, 255, 150);

	// ==================== Visual Appearance - Text ====================
	/** Node text size */
	private static int NODE_TEXT_SIZE = 10;

	/** Edge text size */
	private static int EDGE_TEXT_SIZE = 10;

	// ==================== Display Options ====================
	/** Anti-aliasing is activated (enabled by default for better quality) */
	private boolean ANTI_ALIASING_ACTIVATED = true;

	/** Show node labels */
	private boolean SHOW_NODE_IDS = true;

	/** Show edge labels */
	private boolean SHOW_EDGE_IDS = true;

	/** Show node names */
	private boolean SHOW_NODE_LABELS = true;

	/** Show edge names */
	private boolean SHOW_EDGE_LABELS = true;

	/**
	 * Constructor
	 * 
	 * @param graphLayoutGenerator the graph layout generator to use
	 * @param i initial width
	 * @param j initial height
	 */
	public GraphViewerPanel(AbstractGraphLayout graphLayoutGenerator, int i, int j) {
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				resized(false);
			}
		});

		width = i;
		height = j;

		nodes = new ArrayList<GNode>();
		edges = new ArrayList<GEdge>();

		this.graphLayoutGenerator = graphLayoutGenerator;

		addMouseMotionListener(this);
		addMouseListener(this);

		setBackground(CANVAS_COLOR);
		
		// Enable double buffering for smooth animation
		setDoubleBuffered(true);
		
		resized(false);
	}

	// ==================== Getters and Setters ====================
	
	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	/**
	 * Change the graph layout generator for this panel
	 * 
	 * @param graphLayoutGenerator a new graph layout generator
	 */
	public void setGraphLayoutGenerator(AbstractGraphLayout graphLayoutGenerator) {
		this.graphLayoutGenerator = graphLayoutGenerator;
	}

	// ==================== Graph Management ====================
	
	/**
	 * Add an edge
	 * 
	 * @param newEdge a new edge
	 */
	public void addEdge(GEdge newEdge) {
		edges.add(newEdge);
	}

	/**
	 * Add a node
	 * 
	 * @param newNode a new node
	 */
	public void addNode(GNode newNode) {
		nodes.add(newNode);
	}

	/**
	 * Remove all edges and nodes.
	 */
	public void clear() {
		edges.clear();
		nodes.clear();
		selectedNodes.clear();
		currentlyDraggedNode = null;
		currentlyMouseOverNode = null;
		repaint();
	}

	/**
	 * Get edge count
	 * 
	 * @return the count
	 */
	public int getEdgeCount() {
		return edges.size();
	}

	/**
	 * Get node count
	 * 
	 * @return the count
	 */
	public int getNodeCount() {
		return nodes.size();
	}

	// ==================== Selection Management ====================
	
	/**
	 * Get the set of selected nodes
	 * 
	 * @return a new HashSet containing the selected nodes
	 */
	public Set<GNode> getSelectedNodes() {
		return new HashSet<>(selectedNodes);
	}

	/**
	 * Clear all node selections
	 */
	public void clearSelection() {
		selectedNodes.clear();
		repaint();
	}

	/**
	 * Select all nodes
	 */
	public void selectAllNodes() {
		selectedNodes.addAll(nodes);
		repaint();
	}

	/**
	 * Delete selected nodes and their connected edges
	 */
	public void deleteSelectedNodes() {
		if (selectedNodes.isEmpty()) {
			return;
		}

		// Remove edges connected to selected nodes
		edges.removeIf(edge -> selectedNodes.contains(edge.getFromNode())
				|| selectedNodes.contains(edge.getToNode()));

		// Remove nodes
		nodes.removeAll(selectedNodes);
		selectedNodes.clear();

		repaint();
	}

	/**
	 * Select all nodes within the selection rectangle
	 */
	private void selectNodesInRectangle() {
		if (selectionStart == null || selectionEnd == null) {
			return;
		}

		int x1 = Math.min(selectionStart.x, selectionEnd.x);
		int y1 = Math.min(selectionStart.y, selectionEnd.y);
		int x2 = Math.max(selectionStart.x, selectionEnd.x);
		int y2 = Math.max(selectionStart.y, selectionEnd.y);

		if (!controlKeyPressed) {
			selectedNodes.clear();
		}

		for (GNode node : nodes) {
			if (node.getCenterX() >= x1 && node.getCenterX() <= x2 && node.getCenterY() >= y1
					&& node.getCenterY() <= y2) {
				selectedNodes.add(node);
			}
		}
	}

	// ==================== Layout and Sizing ====================
	
	/**
	 * Method that is called when the JPanel is resized to recalculate some values
	 * that depends on the size.
	 * 
	 * @param doAutoLayout whether to perform auto layout after resize
	 */
	protected void resized(boolean doAutoLayout) {
		maxX = getWidth() - GNode.getRadius();
		maxY = getHeight() - GNode.getRadius();

		// Make sure that all nodes remains inside the canvas
		for (GNode node : nodes) {
			boolean nodeIsOutsideCanvasX = (node.getCenterX() >= maxX);
			boolean nodeIsOutsideCanvasY = (node.getCenterY() >= maxY);

			if (nodeIsOutsideCanvasX || nodeIsOutsideCanvasY) {
				int x = nodeIsOutsideCanvasX ? maxX : node.getCenterX();
				int y = nodeIsOutsideCanvasY ? maxY : node.getCenterY();
				node.updatePosition(x, y);
			}
		}

		repaint();

		setPreferredSize(new Dimension(width, height));

		revalidate();

		if (doAutoLayout) {
			autoLayout();
		}
	}

	/**
	 * Perform automatic layout of graph nodes.
	 */
	public void autoLayout() {
		if (nodes.size() == 0) {
			return;
		}
		graphLayoutGenerator.autoLayout(edges, nodes, getWidth(), getHeight());
		repaint();
	}

	/**
	 * Update the size of this panel
	 * 
	 * @param newWidth     the new width
	 * @param newHeight    the new height
	 * @param doAutoLayout do auto layout
	 */
	public void updateSize(int newWidth, int newHeight, boolean doAutoLayout) {
		width = newWidth;
		height = newHeight;
		resized(doAutoLayout);
	}

	public Dimension getPreferredSize() {
		if (width == 0) {
			return super.getPreferredSize();
		} else {
			return new Dimension(width, height);
		}
	}

	// ==================== Visual Property Updates ====================
	
	/**
	 * Update the node size
	 * 
	 * @param newRadius the new radius
	 */
	public void updateNodeSize(int newRadius) {
		GNode.changeRadiusSize(newRadius);
		maxX = getWidth() - GNode.getRadius();
		maxY = getHeight() - GNode.getRadius();

		for (GNode node : nodes) {
			node.updatePosition(node.getCenterX(), node.getCenterY());
		}
		repaint();
	}

	/**
	 * Update the node text size
	 * 
	 * @param newTextSize the new size
	 */
	public void updateNodeTextSize(int newTextSize) {
		NODE_TEXT_SIZE = newTextSize;

		for (GNode node : nodes) {
			node.updatePosition(node.getCenterX(), node.getCenterY());
		}

		repaint();
	}

	/**
	 * Update the edge text size
	 * 
	 * @param newTextSize the new size
	 */
	public void updateEdgeTextSize(int newTextSize) {
		EDGE_TEXT_SIZE = newTextSize;
		repaint();
	}

	/**
	 * Update edge thickness
	 * 
	 * @param newThickness the new thickness
	 */
	public void updateEdgeThickness(int newThickness) {
		GEdge.changeThickness(newThickness);
		repaint();
	}

	// ==================== Rendering ====================
	
	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		Graphics2D g2 = (Graphics2D) g;

		// Draw the graph
		drawTheVisual(g2);

		// Draw selection rectangle if active
		if (selectionStart != null && selectionEnd != null) {
			drawSelectionRectangle(g2);
		}
	}

	/**
	 * Draw the visual representation of the graph
	 * 
	 * @param g2 Graphics2D object to draw on
	 */
	private void drawTheVisual(Graphics2D g2) {
		// Apply high-quality rendering hints
		if (ANTI_ALIASING_ACTIVATED) {
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		}

		// Draw background
		g2.setColor(CANVAS_COLOR);
		g2.fillRect(0, 0, getWidth(), getHeight());

		// Draw edges
		drawAllEdges(g2);

		// Draw nodes
		drawAllNodes(g2);
	}

	/**
	 * Draw all edges
	 * 
	 * @param g2 Graphics2D object
	 */
	private void drawAllEdges(Graphics2D g2) {
		if (SHOW_EDGE_IDS || SHOW_EDGE_LABELS) {
			java.awt.Font newFont = g2.getFont().deriveFont((float) EDGE_TEXT_SIZE);
			g2.setFont(newFont);
		}
		g2.setColor(getEdgeColor());
		for (GEdge edge : edges) {
			drawEdge(g2, edge.getFromNode().getCenterX(), edge.getFromNode().getCenterY(),
					edge.getToNode().getCenterX(), edge.getToNode().getCenterY(), edge.isDirected(), edge);
		}
	}

	/**
	 * Draw all nodes
	 * 
	 * @param g2 Graphics2D object
	 */
	private void drawAllNodes(Graphics2D g2) {
		if (SHOW_NODE_IDS || SHOW_NODE_LABELS) {
			java.awt.Font newFont = g2.getFont().deriveFont((float) NODE_TEXT_SIZE);
			g2.setFont(newFont);
		}
		for (GNode node : nodes) {
			drawNode(g2, node);
		}
	}

	/**
	 * Draw a node
	 * 
	 * @param g    the Graphics object
	 * @param node a node
	 */
	private void drawNode(Graphics2D g, GNode node) {
		// Determine node color based on state
		Color nodeColor = determineNodeColor(node);
		g.setColor(nodeColor);

		// Draw the vertex shape
		g.fillOval(node.getTopLeftX(), node.getTopLeftY(), GNode.getDiameter(), GNode.getDiameter());

		// Draw border (thicker for selected nodes)
		if (selectedNodes.contains(node)) {
			g.setStroke(new BasicStroke(3));
		}
		g.setColor(NODE_BORDER_COLOR);
		g.drawOval(node.getTopLeftX(), node.getTopLeftY(), GNode.getDiameter(), GNode.getDiameter());
		g.setStroke(new BasicStroke(1));

		// Draw the vertex label
		drawNodeLabel(g, node);
	}

	/**
	 * Determine the color for a node based on its state
	 * 
	 * @param node the node
	 * @return the color to use
	 */
	private Color determineNodeColor(GNode node) {
		if (selectedNodes.contains(node)) {
			return NODE_SELECTION_COLOR;
		} else if (node == currentlyMouseOverNode) {
			return NODE_HIGHLIGHT_COLOR;
		} else {
			return getNodeColor();
		}
	}

	/**
	 * Draw the label for a node
	 * 
	 * @param g    Graphics object
	 * @param node the node
	 */
	private void drawNodeLabel(Graphics g, GNode node) {
		if (SHOW_NODE_IDS || SHOW_NODE_LABELS) {
			String text = node.getIdAndNameAsString(SHOW_NODE_IDS, SHOW_NODE_LABELS);

			int stringWidth = g.getFontMetrics().stringWidth(text);
			int stringHeight = g.getFontMetrics().getHeight();

			int xlabel = node.getCenterX() - stringWidth / 2;
			int ylabel = node.getCenterY() + stringHeight / 2;

			g.setColor(NODE_LABEL_COLOR);
			g.drawString(text, xlabel, ylabel);
		}
	}

	/**
	 * Draw an edge
	 * 
	 * @param g          Graphics2D object
	 * @param x1         position x where arrow starts
	 * @param y1         position y where arrow starts
	 * @param x2         position x where arrow ends
	 * @param y2         position y where arrow ends
	 * @param isDirected boolean indicating if the graph is directed
	 * @param edge       the edge
	 */
	private void drawEdge(Graphics2D g, final int x1, final int y1, final int x2, final int y2, boolean isDirected,
			GEdge edge) {

		// Get the current transform
		java.awt.geom.AffineTransform currentTransform = g.getTransform();

		// We prepare a transform to draw the arrow head
		double distanceX = x2 - x1;
		double distanceY = y2 - y1;

		double newAngle = Math.atan2(distanceY, distanceX);

		AffineTransform newTransform = AffineTransform.getTranslateInstance(x1, y1);
		newTransform.concatenate(AffineTransform.getRotateInstance(newAngle));

		// Do the transformation
		g.transform(newTransform);

		g.setColor(getEdgeColor());

		// Draw the line
		int arrowLength = (int) Math.sqrt(distanceX * distanceX + distanceY * distanceY) - (GNode.getRadius());
		g.setStroke(new BasicStroke(GEdge.getEdgeThickness()));
		g.drawLine(0 + GNode.getRadius(), 0, (int) arrowLength, 0);
		g.setStroke(new BasicStroke(1));

		// Draw the arrow from position (0, 0)
		if (isDirected) {
			g.fillPolygon(
					new int[] { arrowLength, arrowLength - GEdge.getArrowHeadSize(),
							arrowLength - GEdge.getArrowHeadSize(), arrowLength },
					new int[] { 0, -GEdge.getArrowHeadSize(), GEdge.getArrowHeadSize(), 0 }, 4);
		}

		// We finished drawing the arrow to we restore the previous transform
		g.setTransform(currentTransform);

		// Draw the edge label
		drawEdgeLabel(g, x1, y1, x2, y2, edge);
	}

	/**
	 * Draw the label for an edge
	 * 
	 * @param g    Graphics2D object
	 * @param x1   start x position
	 * @param y1   start y position
	 * @param x2   end x position
	 * @param y2   end y position
	 * @param edge the edge
	 */
	private void drawEdgeLabel(Graphics2D g, int x1, int y1, int x2, int y2, GEdge edge) {
		if (SHOW_EDGE_IDS || SHOW_EDGE_LABELS) {
			String text = edge.getIdAndNameAsString(SHOW_EDGE_IDS, SHOW_EDGE_LABELS);
			int stringWidth = g.getFontMetrics().stringWidth(text);
			int xlabel = (x1 + x2) / 2 - stringWidth / 2;
			int ylabel = (y1 + y2) / 2 + g.getFontMetrics().getHeight() / 2;
			
			// Draw background for better readability
			g.setColor(CANVAS_COLOR);
			g.fillRect(xlabel, ylabel - g.getFontMetrics().getHeight(), stringWidth, g.getFontMetrics().getHeight());
			
			// Draw label text
			g.setColor(EDGE_LABEL_COLOR);
			g.drawString(text, xlabel, ylabel);
		}
	}

	/**
	 * Draw the selection rectangle
	 * 
	 * @param g2 Graphics2D object
	 */
	private void drawSelectionRectangle(Graphics2D g2) {
		int x = Math.min(selectionStart.x, selectionEnd.x);
		int y = Math.min(selectionStart.y, selectionEnd.y);
		int w = Math.abs(selectionEnd.x - selectionStart.x);
		int h = Math.abs(selectionEnd.y - selectionStart.y);

		g2.setColor(SELECTION_RECT_COLOR);
		g2.fillRect(x, y, w, h);
		g2.setColor(SELECTION_RECT_BORDER_COLOR);
		g2.setStroke(new BasicStroke(2));
		g2.drawRect(x, y, w, h);
		g2.setStroke(new BasicStroke(1));
	}

	// ==================== Mouse Event Handling ====================
	
	@Override
	public void mouseClicked(MouseEvent e) {
		// Reserved for future use
	}

	@Override
	public void mousePressed(MouseEvent event) {
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
	 * Handle node selection on mouse press
	 * 
	 * @param event the mouse event
	 */
	private void handleNodeSelection(MouseEvent event) {
		Point mousePosition = this.getMousePosition();
		if (mousePosition == null) {
			return;
		}

		// Find the node that is clicked
		boolean foundNode = false;
		for (int i = nodes.size() - 1; i >= 0; i--) {
			GNode node = nodes.get(i);
			if (node.contains(mousePosition.x, mousePosition.y)) {
				currentlyDraggedNode = node;
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

		// If no node was clicked, start selection rectangle or clear selection
		if (!foundNode) {
			if (mouseLeftIsPressed && !controlKeyPressed) {
				selectedNodes.clear();
				selectionStart = event.getPoint();
			}
			currentlyDraggedNode = null;
		}
	}

	/**
	 * Toggle selection of a node
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
	 * Select a single node (clear other selections)
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
		// Finish selection rectangle
		if (selectionStart != null && selectionEnd != null) {
			selectNodesInRectangle();
			selectionStart = null;
			selectionEnd = null;
		}

		currentlyDraggedNode = null;
		mouseLeftIsPressed = false;
		mouseRightIsPressed = false;
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
		// The user is dragging us, so scroll!
		Rectangle r = new Rectangle(e.getX(), e.getY(), 1, 1);
		scrollRectToVisible(r);

		Point mousePosition = this.getMousePosition();

		// Draw selection rectangle
		if (mouseLeftIsPressed && selectionStart != null && currentlyDraggedNode == null) {
			selectionEnd = e.getPoint();
			repaint();
		}
		// Drag nodes
		else if (mousePosition != null && mouseLeftIsPressed && currentlyDraggedNode != null) {
			handleNodeDragging(mousePosition);
		}

		updateMouseCursor();
		repaint();

		mouseXPosition = e.getX();
		mouseYPosition = e.getY();
	}

	/**
	 * Handle dragging of nodes
	 * 
	 * @param mousePosition current mouse position
	 */
	private void handleNodeDragging(Point mousePosition) {
		int newX = constrainX(mousePosition.x);
		int newY = constrainY(mousePosition.y);

		// Move all selected nodes if current node is selected
		if (selectedNodes.contains(currentlyDraggedNode)) {
			moveSelectedNodes(newX, newY);
		} else {
			currentlyDraggedNode.updatePosition(newX, newY);
		}
	}

	/**
	 * Move all selected nodes by the same delta
	 * 
	 * @param newX new X position for dragged node
	 * @param newY new Y position for dragged node
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
	 * Constrain X coordinate within canvas bounds
	 * 
	 * @param x the x coordinate
	 * @return constrained x coordinate
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
	 * Constrain Y coordinate within canvas bounds
	 * 
	 * @param y the y coordinate
	 * @return constrained y coordinate
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

		findNodeThatCursorIsOver(mousePosition);
		updateMouseCursor();
		GraphViewerPanel.this.repaint();
	}

	/**
	 * Find the node that the cursor is currently over
	 * 
	 * @param mousePosition current mouse position
	 */
	private void findNodeThatCursorIsOver(Point mousePosition) {
		// Find the node that the cursor is over it
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
	 * Update the mouse cursor based on current state
	 */
	protected void updateMouseCursor() {
		if (currentlyMouseOverNode != null) {
			if (mouseLeftIsPressed) {
				setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
			} else {
				setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			}
		} else {
			setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		}
	}

	// ==================== Export Functionality ====================
	
	/**
	 * This method is called when the user click on the button to export the current
	 * plot to a file with high quality
	 * 
	 * @throws IOException if an error occurs
	 */
	protected void exportAsPNG() {
		// ask the user to choose the filename and path
		String outputFilePath = null;
		try {
			File path;
			// Get the last path used by the user, if there is one
			String previousPath = PreferencesManager.getInstance().getOutputFilePath();
			// If there is no previous path (first time user),
			// show the files in the "examples" package of
			// the spmf distribution.
			if (previousPath == null) {
				URL main = MainTestApriori_simple_saveToFile.class.getResource("MainTestApriori_saveToFile.class");
				if (!"file".equalsIgnoreCase(main.getProtocol())) {
					path = null;
				} else {
					path = new File(main.getPath());
				}
			} else {
				// Otherwise, use the last path used by the user.
				path = new File(previousPath);
			}

			// ASK THE USER TO CHOOSE A FILE
			final JFileChooser fc;
			if (path != null) {
				fc = new JFileChooser(path.getAbsolutePath());
			} else {
				fc = new JFileChooser();
			}

			// Add file filter for PNG files
			FileNameExtensionFilter filter = new FileNameExtensionFilter("PNG Images", "png");
			fc.setFileFilter(filter);

			int returnVal = fc.showSaveDialog(GraphViewerPanel.this);

			// If the user chose a file
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File file = fc.getSelectedFile();
				outputFilePath = file.getPath(); // save the file path
				// save the path of this folder for next time.
				if (fc.getSelectedFile() != null) {
					PreferencesManager.getInstance().setOutputFilePath(fc.getSelectedFile().getParent());
				}
			} else {
				// the user did not choose so we return
				return;
			}

		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
					"An error occured while opening the save plot dialog. ERROR MESSAGE = " + e.toString(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}

		try {
			// add the .png extension
			if (outputFilePath.endsWith("png") == false) {
				outputFilePath = outputFilePath + ".png";
			}
			File outputFile = new File(outputFilePath);

			// Create high-quality image
			BufferedImage image = createHighQualityImage();
			ImageIO.write(image, "png", outputFile);

			JOptionPane.showMessageDialog(this, "Graph exported successfully to:\n" + outputFilePath,
					"Export Successful", JOptionPane.INFORMATION_MESSAGE);

		} catch (Exception e) {
			JOptionPane.showMessageDialog(null,
					"An error occured while attempting to save the plot. ERROR MESSAGE = " + e.toString(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	/**
	 * Create a high-quality image of the current graph
	 * 
	 * @return BufferedImage with high-quality rendering
	 */
	private BufferedImage createHighQualityImage() {
		BufferedImage image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = image.createGraphics();

		// Enable high-quality rendering
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
		g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);

		// Draw the graph
		drawTheVisual(g2);
		g2.dispose();

		return image;
	}

	// ==================== Update Method ====================
	
	@Override
	public void update(Graphics g) {
		super.update(g);
		paintComponent(g);
	}

	// ==================== Color Getters and Setters ====================
	
	public Color getNodeColor() {
		return NODE_COLOR;
	}

	public void setNodeColor(Color color) {
		NODE_COLOR = color;
		repaint();
	}

	public void setNodeTextColor(Color color) {
		NODE_LABEL_COLOR = color;
		repaint();
	}

	public Color getNodeTextColor() {
		return NODE_LABEL_COLOR;
	}

	public Color getEdgeTextColor() {
		return EDGE_LABEL_COLOR;
	}

	public void setEdgeTextColor(Color color) {
		EDGE_LABEL_COLOR = color;
		repaint();
	}

	public Color getEdgeColor() {
		return EDGE_COLOR;
	}

	public void setEdgeColor(Color color) {
		EDGE_COLOR = color;
		repaint();
	}

	public Color getCanvasColor() {
		return CANVAS_COLOR;
	}

	public void setCanvasColor(Color cANVAS_COLOR) {
		CANVAS_COLOR = cANVAS_COLOR;
		setBackground(cANVAS_COLOR);
		repaint();
	}

	public Color getNodeBorderColor() {
		return NODE_BORDER_COLOR;
	}

	public void setNodeBorderColor(Color color) {
		NODE_BORDER_COLOR = color;
		repaint();
	}

	// ==================== Display Option Setters ====================
	
	public void setAntiAliasing(boolean selected) {
		ANTI_ALIASING_ACTIVATED = selected;
		repaint();
	}

	public static int getNodeTextSize() {
		return NODE_TEXT_SIZE;
	}

	public static int getEdgeTextSize() {
		return EDGE_TEXT_SIZE;
	}

	public void showEdgeIDs(boolean selected) {
		SHOW_EDGE_IDS = selected;
		repaint();
	}

	public void showNodeIDs(boolean selected) {
		SHOW_NODE_IDS = selected;
		repaint();
	}

	public void showEdgeLabels(boolean selected) {
		SHOW_EDGE_LABELS = selected;
		repaint();
	}

	public void showNodeLabels(boolean selected) {
		SHOW_NODE_LABELS = selected;
		repaint();
	}
}