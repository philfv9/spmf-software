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
 * This class represents a graph element (node or edge) as used by the
 * GraphViewerPanel
 * 
 * @author Philippe Fournier-Viger
 * @see GraphViewerPanel
 */
public abstract class GraphElement {
	/** Name of the element */
	private String name;
	
	/** Id of the element */
	private String id;

	/**
	 * Constructor
	 * 
	 * @param name name of the element
	 * @param id the id of the element
	 */
	public GraphElement(String name, String id) {
		this.name = name;
		this.id = id;
	}
	
	/**
	 * Constructor
	 * 
	 * @param name name of the element
	 */
	public GraphElement(String name) {
		this.name = name;
	}

	/**
	 * Get the name of this graph element
	 * 
	 * @return a String
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Get the name of this graph element
	 * 
	 * @return a String
	 */
	public String getId() {
		return id;
	}
	
	public String getIdAndNameAsString(boolean displayID, boolean displayName) {
		if(displayID && displayName && getId() != null) {
			return getId() + ":" + getName();
		}else if(displayID && getId() != null) {
			return getId();
		}else if(displayName) {
			return getName();
		}
		return "";
	}

}