package ca.pfv.spmf.gui.workflow_editor;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;

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
 * A class that represents a node with a label and a position, to be drawn on
 * the DrawPanel of the Workflow Editor
 * 
 * @author Philippe Fournier-Viger
 * @see WorkflowEditorWindow
 */
abstract class Node {
	/** The padding for the text in this node */
	final int TEXT_PADDING = 5;

	/** The name of this node */
	String name;

	/** The X and Y positions of this node */
	protected int x, y;

	/** The rectangle of this node (for click detection */
	Rectangle rectangle = null;

	/** The width of the text displayed in this node */
	int textWidth;

	/** The height of the text displayed in this node */
	int textHeight;

	/** The group that this node belongs to */
	GroupOfNodes group = null;

	/**
	 * Constructor
	 * 
	 * @param label the name of the node
	 * @param x     the X position
	 * @param y     the Y position
	 */
	public Node(String label, int x, int y) {
		this.name = label;
		this.x = x;
		this.y = y;
	}

	/**
	 * Get the X position
	 * 
	 * @return the position as an integer
	 */
	public int getX() {
		return x;
	}

	/**
	 * Get the Y position
	 * 
	 * @return the position as an integer
	 */
	public int getY() {
		return y;
	}

	/**
	 * Method to paint this node on a Panel
	 * 
	 * @param g          the Graphics object on which the node should be drawn
	 * @param isSelected whether the node is selected or not
	 */
	void paintNode(Graphics g, boolean isSelected) {
		// If the node is selected, draw a red border around it
		if (isSelected) {
			g.setColor(Color.RED);
			g.fillOval(x - 15, y - 15, 30, 30);
		} else {
			g.setColor(Color.BLUE);
			g.fillOval(x - 15, y - 15, 30, 30);
		}
		g.setColor(Color.WHITE);
		g.drawString(name, x - 5, y + 5);
	}

	/**
	 * Get the type of this node
	 * 
	 * @return a String
	 */
	public abstract String getType();

	/**
	 * Set the group corresponding to this node
	 * 
	 * @param group the group
	 */
	public void setGroup(GroupOfNodes group) {
		this.group = group;
	}

	/**
	 * Recalculate the rectangle that is used for detecting mouse clicks for this
	 * node
	 * 
	 * @param g the Graphics object used for drawing the node
	 */
	void recalculateRectangle(Graphics g) {
		// Get the font metrics for the current font
		FontMetrics fm = g.getFontMetrics();

		// Calculate the width and height of the text
		textWidth = fm.stringWidth(name);
		textHeight = fm.getHeight();

		// Calculate the rectangle size
		int rectWidth = textWidth + TEXT_PADDING * 2;
		int rectHeight = textHeight + TEXT_PADDING / 2;

		rectangle = new Rectangle(x - rectWidth / 2 - (TEXT_PADDING / 2), y - rectHeight / 2 - (TEXT_PADDING / 2),
				rectWidth + (TEXT_PADDING), rectHeight + (TEXT_PADDING));
	}

	/**
	 * Check if a point is inside this node for mouse click detection
	 * 
	 * @param x the point X value
	 * @param y the point Y value
	 * @resturn true if inside. false if outside
	 */
	public boolean contains(int x, int y) {
		if (rectangle == null) {
			return false;
		}
		return rectangle.contains(x, y);
	}

	/**
	 * Change the position of this node
	 * 
	 * @param x the new X position
	 * @param y the new Y position
	 */
	public void updatePosition(int x, int y) {
		this.x = x;
		this.y = y;
		rectangle = null;
	}

}