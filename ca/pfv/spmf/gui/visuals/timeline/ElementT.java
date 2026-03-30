package ca.pfv.spmf.gui.visuals.timeline;
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
 * An element that can be either an event or a time interval.
 * Used by the Timeline Viewer of SPMF.
 * 
 * @author Philippe Fournier-Viger
 */
public class ElementT {
	/** A name given to this element */
	private final String name;

	/**
	 * The index of the layer on which this time element should be drawn by the
	 * TimelineViewer
	 */
	int layerIndex = 0;
	
	public ElementT(String name) {
		this.name = name;
	}
	

    /**
     * Get the name
     * @return the event name
     */
    public String getName() {
        return name;
    }


}