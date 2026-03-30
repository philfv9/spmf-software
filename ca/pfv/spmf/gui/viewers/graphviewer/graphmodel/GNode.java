package ca.pfv.spmf.gui.viewers.graphviewer.graphmodel;

import ca.pfv.spmf.gui.viewers.graphviewer.GraphViewerPanel;

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
/**
 * This class represents a node as used by the GraphViewerPanel
 * 
 * @author Philippe Fournier-Viger
 * @see GraphViewerPanel
 */
public class GNode extends GraphElement {
	/** the center of the node on the X axis */
	int centerX;

	/** the center of the node on the Y axis */
	int centerY;

	/** top left corner of the square around the node, on the X axis */
	int topLeftX;

	/** top left corner of the square around the node, on the Y axis */
	int topLeftY;

	/** bottom right corner of the square around the node, on the X axis */
	int bottomRightX;

	/** bottom right corner of the square around the node, on the Y axis */
	int bottomRightY;

	/** Diameter of a node */
	private static int DIAMETER = 30;

	/** Radius of a node */
	private static int RADIUS = getDiameter() / 2;

	/**
	 * Constructor where only a name is given to the node
	 * 
	 * @param name the name
	 */
	public GNode(final String name) {
		super(name);
		updatePosition(0, 0);
	}
	
	/**
	 * Constructor where a name and id is given to the node
	 * 
	 * @param name the name
	 */
	public GNode(final String name, final String id) {
		super(name,id);
		updatePosition(0, 0);
	}

	/**
	 * Check if a point is inside a rectangle around the node
	 * 
	 * @param x a position x
	 * @param y a position y
	 * @return true if it is inside
	 */
	public boolean contains(int x, int y) {
		// If the position x is outside, return false
		if (x < getTopLeftX() || x > bottomRightX) {
			return false;
		}
		// If the position y is outside return false
		if (y < getTopLeftY() || y > bottomRightY) {
			return false;
		}
		// Else, returnn true
		return true;
	}

	/**
	 * Update the center position of this node
	 * 
	 * @param posX the position on the x axis
	 * @param posY the position on the y axis
	 */
	public final void updatePosition(int posX, int posY) {
		this.centerX = posX;
		this.centerY = posY;
		this.topLeftX = posX - getRadius();
		this.topLeftY = posY - getRadius();
		this.bottomRightX = posX + getRadius();
		this.bottomRightY = posY + getRadius();
	}

	/**
	 * Update the center position of this node on the x axis
	 * 
	 * @param x the position
	 */
	public void setPosX(double x) {
		int posX = (int) x;
		this.centerX = posX;
		this.topLeftX = posX - getRadius();
		this.bottomRightX = posX + getRadius();
	}

	/**
	 * Update the center position of this node on the y axis
	 * 
	 * @param y the position
	 */
	public void setPosY(double y) {
		int posY = (int) y;
		this.centerY = posY;
		this.topLeftY = posY - getRadius();
		this.bottomRightY = posY + getRadius();
	}


	/**
	 * Get the top left X axis position of the square around this node
	 * @return the x position
	 */
	public int getTopLeftX() {
		return topLeftX;
	}
	/**
	 * Get the top left Y axis position of the square around this node
	 * @return the x position
	 */
	public int getTopLeftY() {
		return topLeftY;
	}

	/**
	 * Get the center X axis position of this node
	 * @return the x position
	 */
	public int getCenterX() {
		return centerX;
	}
	/**
	 * Get the center Y axis position of this node
	 * @return the x position
	 */
	public int getCenterY() {
		return centerY;
	}

	/** Change the radius size
	 * 
	 * @param newRadius the new size
	 */
	public static void changeRadiusSize(int newRadius) {
		RADIUS = newRadius;
		DIAMETER = newRadius *2;
	}

	/**
	 * Returns the radius value.
	 * 
	 * @return the constant radius 
	 */
	public static int getRadius() {
	    return RADIUS;
	}

	/**
	 * Returns the diameter value.
	 * 
	 * @return the constant diameter 
	 */
	public static int getDiameter() {
	    return DIAMETER;
	}

}