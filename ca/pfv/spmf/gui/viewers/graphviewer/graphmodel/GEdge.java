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
 * This class represents an edge as used by the GraphViewerPanel
 * 
 * @author Philippe Fournier-Viger
 * @see GraphViewerPanel
 */
public class GEdge extends GraphElement {
	/** A node */
	private final GNode fromNode;
	/** Another node */
	private final GNode toNode;
	/** If this edge is directed */
	private final boolean directed;

	/** Size of an arrow head */
	private  static int ARROW_HEAD_SIZE = 4;
	
	/** Edge thickness */
	private  static int EDGE_THICKNESS = 1;

	/**
	 * Constructor
	 * 
	 * @param fromNode node
	 * @param toNode   second node
	 * @param name     name of the edge if any
	 * @param directed true if directed, otherwise false
	 */
	public GEdge(final GNode fromNode, final GNode toNode, final String name, final boolean directed) {
		super(name);
		this.fromNode = fromNode;
		this.toNode = toNode;
		this.directed = directed;
	}
	
	/**
	 * Constructor
	 * 
	 * @param fromNode node
	 * @param toNode   second node
	 * @param name     name of the edge if any
	 * @param id       id of the edge if any
	 * @param directed true if directed, otherwise false
	 */
	public GEdge(final GNode fromNode, final GNode toNode, final String name, String id, final boolean directed) {
		super(name, id);
		this.fromNode = fromNode;
		this.toNode = toNode;
		this.directed = directed;
	}

	/**
	 * Get the "from" node
	 * 
	 * @return the from node
	 */
	public GNode getFromNode() {
		return fromNode;
	}

	/**
	 * Get the "to" node
	 * 
	 * @return the node
	 */
	public GNode getToNode() {
		return toNode;
	}

	/**
	 * Check if this edge is directed
	 * 
	 * @return true if directed, otherwise false
	 */
	public boolean isDirected() {
		return directed;
	}

	/** 
	 * Change the edge thickness
	 * @param newThickness the new thickness, a positive integer
	 */
	public static void changeThickness(int newThickness) {
		EDGE_THICKNESS = newThickness;
		ARROW_HEAD_SIZE = newThickness * 3;
	}

	public static int getArrowHeadSize() {
		return ARROW_HEAD_SIZE;
	}

	public static int getEdgeThickness() {
		return EDGE_THICKNESS;
	}

//	public boolean contains(int x, int y) {
//
//		if (x < Math.min(fromNode.centerX, toNode.centerX) || x > Math.max(fromNode.centerX, toNode.centerX)
//				|| y < Math.min(fromNode.centerY, toNode.centerY) || y > Math.max(fromNode.centerY, toNode.centerY)) {
//			return false;
//		}
//
//		int differenceY = toNode.centerY - fromNode.centerY;
//		int differenceX = toNode.centerX - fromNode.centerX;
//
//		double distance = Math
//				.abs(differenceY * x - differenceX * y + toNode.centerX * fromNode.centerY
//						- toNode.centerY * fromNode.centerX)
//				/ Math.sqrt(differenceY * differenceY + differenceX * differenceX);
//		return distance <= GNode.RADIUS;
//	}

}