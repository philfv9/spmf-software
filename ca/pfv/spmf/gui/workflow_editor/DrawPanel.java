package ca.pfv.spmf.gui.workflow_editor;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GEdge;
import ca.pfv.spmf.gui.viewers.graphviewer.graphmodel.GNode;

/*
 * Copyright (c) 2022 Philippe Fournier-Viger
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
 * A class that represents a panel for drawing nodes and links
 * 
 * @author Philippe Fournier-Viger
 * @see WorkflowEditorWindow
 */
class DrawPanel extends JPanel {
	/**
	 * serial UID
	 */
	private static final long serialVersionUID = -6682097480349196519L;

	/** The Y position of the first node in the workflow */
	private static final int INITIAL_NODE_Y_POSITION = 17;

	/** The vertical gap between nodes */
	private static final int NODE_Y_GAP = 40;

	/** The arrow head size for edges */
	private static final int ARROWHEAD_SIZE = 5;

	/** The line style for edges */
	Stroke dashedStroke = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f,
			new float[] { 2f, 2f }, 2.0f);

	/** The list of listeners for the draw panel events */
	List<DrawPanelListener> listeners = new ArrayList<DrawPanelListener>();

	/**
	 * A list of group of nodes where each group is an algorithm with its input and
	 * output (three nodes)
	 */
	List<GroupOfNodes> groups;

	/** The selected node, if any */
	Node selected;

	/**
	 * Constructor
	 */
	public DrawPanel() {
		groups = new ArrayList<>();
		selected = null;
		setBackground(Color.WHITE);

		registerForMouseEvents();
	}

	/**
	 * Method to add a new algorithm
	 */
	public void addAlgorithmNode() {
		int x = calculateNewNodeX();
		int y = calculateNodeY();
		boolean isFirstAlgorithm = groups.size() == 0;
		NodeFileInput nodeInput = null;
		if (isFirstAlgorithm) {
			nodeInput = new NodeFileInput("", x, y);
			y += NODE_Y_GAP;
		}

		// Create the new algorithm node
		NodeAlgorithm nodeAlgo = new NodeAlgorithm("Algorithm", x, y);

		// Create the new output node
		y += NODE_Y_GAP;
		NodeFileOutput nodeOutput = new NodeFileOutput("Output" + (groups.size() + 1) + ".txt", x, y);

		GroupOfNodes group = new GroupOfNodes(nodeInput, nodeAlgo, nodeOutput);
		groups.add(group);

		// Set the selected node
		selected = nodeAlgo;

		notifyListenersOfNodeSelection();

		revalidate();
		repaint();
	}

	/**
	 * Method to calculate the Y position of a new node that is added to the workflow
	 */
	private int calculateNodeY() {
		int y;
		if (groups.isEmpty()) {
			y = INITIAL_NODE_Y_POSITION;
		} else {
			// If there is a previous node, place the node to the right of it with some gap
			Node prev = groups.get(groups.size() - 1).nodeOutput;
			y = prev.getY() + NODE_Y_GAP;
		}
		return y;
	}

	/**
	 * Method to calculate the X position of a new node that is added to the workflow
	 */
	private int calculateNewNodeX() {
		int x;
		if (groups.isEmpty()) {
			// If there is no previous node, place the node at the center of the panel
			x = getWidth() / 2;
		} else {
			// If there is a previous node, place the node to the right of it with some gap
			Node prev = groups.get(groups.size() - 1).nodeOutput;
			x = prev.getX();
		}
		return x;
	}

	// Override the paintComponent method to draw the nodes and links
	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D) g;

		// Set the color for the links
		g.setColor(Color.BLACK);

		Node lastNodeOfPreviousGroup = null;

		for (GroupOfNodes group : groups) {
			if (lastNodeOfPreviousGroup != null) {
				drawEdgeBetweenNodes(g, lastNodeOfPreviousGroup, group.nodeAlgorithm);
			} else if (group.showInput) {
				drawEdgeBetweenNodes(g, group.nodeInput, group.nodeAlgorithm);
			}
			if (group.showOutput) {
				drawEdgeBetweenNodes(g, group.nodeAlgorithm, group.nodeOutput);
				lastNodeOfPreviousGroup = group.nodeOutput;
			} else {
				lastNodeOfPreviousGroup = group.nodeAlgorithm;
			}

			// Draw dashed line
			if (group != groups.getLast()) {
				Stroke currentStroke = g2d.getStroke();
				g2d.setStroke(dashedStroke);
				int yLine = lastNodeOfPreviousGroup.y + NODE_Y_GAP / 2;
				g2d.drawLine(0, yLine, getWidth(), yLine);
				g2d.setStroke(currentStroke);
			}

		}

		for (GroupOfNodes group : groups) {

			if (group.nodeInput != null) {
				if (group.showInput) {
					group.nodeInput.paintNode(g, group.nodeInput == selected);
				}

			}

			group.nodeAlgorithm.paintNode(g, group.nodeAlgorithm == selected);

			if (group.showOutput) {
				group.nodeOutput.paintNode(g, group.nodeOutput == selected);
			}
		}

		notifyListenersOfGroupCount();
		notifyListenersOfHasOutputInput();
	}

	private void drawEdgeBetweenNodes(Graphics g, Node current, Node next) {
		// Draw a line between the node centers
		g.drawLine(current.getX(), current.getY(), next.getX(), next.getY());

		// Draw an arrow head at the end of the line
		drawArrowHead((Graphics2D) g, current.getX(), current.getY(), next.getX(), next.getY());
	}

//Override the getPreferredSize method to return the size of the panel based on the nodes
	@Override
	public Dimension getPreferredSize() {
		// Initialize the width and height to zero
		int width = 0;
		int height = 0;

		// Loop through the nodes and update the width and height
		for (GroupOfNodes group : groups) {
			for (Node node : group.getNodes()) {
				// The width is the maximum of the current width and the node's right edge
				width = Math.max(width, node.getX() + 15);

				// The height is the maximum of the current height and the node's bottom edge
				height = Math.max(height, node.getY() + 15);
			}
		}

		// Return a new dimension with the width and height
		return new Dimension(width, height);
	}

	// A helper method to draw an arrow head at the end of a line
	public void drawArrowHead(Graphics2D g, int x1, int y1, int x2, int y2) {

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

		g.setColor(Color.BLACK);

		// Draw the line
		int arrowLength = (int) Math.sqrt(distanceX * distanceX + distanceY * distanceY) - (GNode.getRadius());
		g.setStroke(new BasicStroke(GEdge.getEdgeThickness()));
		g.drawLine(0 + GNode.getRadius(), 0, (int) arrowLength, 0);
		g.setStroke(new BasicStroke(1));

		// Draw the arrow from position (0, 0)
		g.fillPolygon(
				new int[] { arrowLength, arrowLength - ARROWHEAD_SIZE, arrowLength - ARROWHEAD_SIZE, arrowLength },
				new int[] { 0, -ARROWHEAD_SIZE, ARROWHEAD_SIZE, 0 }, 4);

		// We finished drawing the arrow to we restore the previous transform
		g.setTransform(currentTransform);
	}

	/**
	 * Add a listener for events from this panel
	 * @param listener a listener
	 */
	public void addDrawPanelListener(DrawPanelListener listener) {
		listeners.add(listener);
	}

	/**
	 * Remove a listener
	 * @param listener the listener
	 */
	public void removeDrawPanelListener(DrawPanelListener listener) {
		listeners.remove(listener);
	}

	/**
	 * Notify the listeners that a node was selected
	 */
	private void notifyListenersOfNodeSelection() {
		for (DrawPanelListener listener : listeners) {
			listener.notifyNodeSelected(selected);
		}
	}

	/**
	 * Notify the listeners that the number of groups (algorithms) has changed in the panel
	 */
	private void notifyListenersOfGroupCount() {
		for (DrawPanelListener listener : listeners) {
			listener.notifyOfListOfGroups(groups);
		}
	}

	/**
	 * Notify listeners that the last node of the workflow has an output.
	 * This is important to determine if aditional nodes can be added.
	 */
	private void notifyListenersOfHasOutputInput() {
		boolean hasOutput = groups.size() == 0 || groups.getLast().showOutput;

		for (DrawPanelListener listener : listeners) {
			listener.notifyHasOutputNode(hasOutput);
		}
	}

	/**
	 * Prepare the panel to listen for mouse events.
	 */
	private void registerForMouseEvents() {
//		// Add a mouse motion listener to handle dragging of nodes
//		addMouseMotionListener(new MouseAdapter() {
//			@Override
//			public void mouseDragged(MouseEvent e) {
//				// Check if a node is selected
//				if (selected != null) {
////					int diffX = e.getX() - selected.getX();
////					int diffY = e.getY() - selected.getY();
//					
//					// Update the position of the selected node to the mouse position
//					selected.updatePosition(e.getX(), e.getY());
////					
////					for(Node node: selected.group.getNodes()) {
////						selected.updatePosition(node.getX()+diffX, node.getY()+diffY);
////					}
//
//					// Repaint the panel to reflect the position change
//					repaint();
//				}
//			}
//		});

		// Add a mouse listener to handle clicks on nodes
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				// Get the coordinates of the mouse click
				int x = e.getX();
				int y = e.getY();
				selected = null;

				// Loop through the nodes to check if any node is clicked
				for (GroupOfNodes group : groups) {
					for (Node node : group.getNodes()) {
//					double distance = Math.sqrt(Math.pow(node.x - x, 2) + Math.pow(node.y - y, 2));
						if (node.contains(x, y)) {
							selected = node;
							break;
						}
					}
				}
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				// Get the coordinates of the mouse click
				int x = e.getX();
				int y = e.getY();

				selected = null;

				// Loop through the nodes and check if any of them contains the mouse click
				for (GroupOfNodes group : groups) {
					for (Node node : group.getNodes()) {
						// If the distance is less than the node radius, the node is clicked
						if (node.contains(x, y)) {
							// Set the selected node to the clicked node
							selected = node;

							// Stop the loop
							break;
						}
					}
				}
				notifyListenersOfNodeSelection();
				// Repaint the panel to show the selection
				repaint();
			}
		});
	}

	/**
	 * Remove the last algorithm from the workflow
	 */
	public void removeLastNode() {
		// Remove the selected node from the list
		GroupOfNodes removed = groups.removeLast();

		// Set the selected node to null
		if (selected == removed.nodeInput || selected == removed.nodeOutput || selected == removed.nodeAlgorithm) {
			selected = null;
		}

		// Revalidate the panel to update the scrollbars
		revalidate();

		// Repaint the panel to update the view
		repaint();

		notifyListenersOfNodeSelection();
	}

	/**
	 * Check if a workflow is valid
	 * 
	 * @return null if valid, and otherwise a String to explain why it is not valid
	 *         otherwise
	 */
	public String validateTheWorkflow() {
		for (GroupOfNodes group : groups) {
			String errorMessage = validateTheGroup(group);
			if (errorMessage != null) {
				return errorMessage;
			}
		}
		return null;
	}

	/**
	 * Check if a group from the workflow is valid
	 * 
	 * @param group a group
	 * @return null if valid, and otherwise a String to explain why it is not valid
	 *         otherwise
	 */
	private String validateTheGroup(GroupOfNodes group) {
		// If it is the first group and it has an input file, we check if it has been
		// set
		if (group.showInput && groups.getFirst() == group) {
			if (group.nodeInput.name == null || group.nodeInput.name.equals("")) {
				return "The input file is not set. Please select an input file.";
			}
		}

		// We check the algorithm to see if it has been set properly
		if (group.nodeAlgorithm.name == null || group.nodeAlgorithm.name.equals("Algorithm")
				|| group.nodeAlgorithm.name.startsWith(" --")) {
			return "An algorithm has not been selected. Please select an algorithm.";
		}

		return null;
	}
}