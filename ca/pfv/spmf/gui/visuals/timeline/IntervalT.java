package ca.pfv.spmf.gui.visuals.timeline;

import java.awt.Color;

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
 * Represents an interval as used by the TimelineViewer of SPMF
 * 
 * @author Philippe Fournier-Viger
 */
public class IntervalT extends ElementT {
	/** Start time of the interval */
	final long start;
	/** End time of the interval */
	final long end;
	/** The color of the interval */
	private final Color color;
	/** An option group that the interval belongs to */
	private int group = 0;

	/**
	 * Constructor
	 * 
	 * @param name      name of the interval
	 * @param startTime start time
	 * @param endTime   end time
	 * @param group  the group number
	 */
	public IntervalT(String name, long startTime, long endTime, int group) {
		super(name);
		this.start = startTime;
		this.end = endTime;
		this.color = Color.red;
		this.group = group;
	}

	/**
	 * Constructor
	 * 
	 * @param name      name of the interval
	 * @param startTime start time
	 * @param endTime   end time
	 */
	public IntervalT(int startTime, int endTime) {
		super("");
		this.start = startTime;
		this.end = endTime;
		this.color = Color.red;
		this.group = 0;
	}
	
	/**
	 * Constructor
	 * 
	 * @param name      name of the interval
	 * @param startTime start time
	 * @param endTime   end time
	 * @param group  the group number
	 */
	public IntervalT(int startTime, int endTime, int group) {
		super("");
		this.start = startTime;
		this.end = endTime;
		this.color = Color.red;
		this.group = group;
	}

	/**
	 * Get the start time of the interval
	 * 
	 * @return the start time
	 */
	public long getStartTime() {
		return start;
	}

	/**
	 * Get the end time of the interval
	 * 
	 * @return the end time
	 */
	public long getEndTime() {
		return end;
	}

	/**
	 * Get the color of this interval
	 * 
	 * @return the Color
	 */
	public Color getColor() {
		return color;
	}
	
	/** Get the group
	 * @return the group
	 */
	public int getGroup() {
		return group;
	}
}