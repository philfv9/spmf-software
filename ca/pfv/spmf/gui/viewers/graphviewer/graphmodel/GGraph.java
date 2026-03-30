package ca.pfv.spmf.gui.viewers.graphviewer.graphmodel;

import java.util.ArrayList;
import java.util.List;

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
 * This class represents a graph as used by the GraphViewerPanel
 * 
 * @author Philippe Fournier-Viger
 * @see GraphViewerPanel
 */
public class GGraph extends GraphElement {

	/** List of edges */
	List<GEdge> edges;

	/** List of nodes */
	List<GNode> nodes;

	/** Support value (optional) */
	private int support = -1;

	/**
	 * Constructor
	 * 
	 * @param name name of the edge if any
	 */
	public GGraph(final String name) {
		super(name);
		this.edges = new ArrayList<>();
		this.nodes = new ArrayList<>();
	}

	/**
	 * Get the list of edges
	 * 
	 * @return the list of edges
	 */
	public List<GEdge> getEdges() {
		return edges;
	}

	/**
	 * Get the list of nodes
	 * 
	 * @return the list of nodes
	 */
	public List<GNode> getNodes() {
		return nodes;
	}

	/**
	 * Get the support
	 * 
	 * @return the support
	 */
	public int getSupport() {
		return support;
	}

	/**
	 * Set the support of this graph
	 * 
	 * @param support the support
	 */
	public void setSupport(int support) {
		this.support = support;
	}

}