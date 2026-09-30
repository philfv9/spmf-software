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
	/** Optional colour (null = colour of the group) */
	private final Color color;
	/** The group that the interval belongs to (e.g. a sequence id) */
	private final int group;

	/**
	 * Constructor
	 * @param name      name of the interval
	 * @param startTime start time
	 * @param endTime   end time
	 * @param group     the group number
	 */
	public IntervalT(String name, long startTime, long endTime, int group) {
		this(name, startTime, endTime, group, null);
	}

	/**
	 * Constructor
	 * @param name      name of the interval
	 * @param startTime start time
	 * @param endTime   end time
	 * @param group     the group number
	 * @param color     the colour of the interval (null = colour of the group)
	 */
	public IntervalT(String name, long startTime, long endTime, int group, Color color) {
		super(name);
		this.start = Math.min(startTime, endTime);
		this.end = Math.max(startTime, endTime);
		this.group = group;
		this.color = color;
	}

	/**
	 * Constructor (unnamed interval in group 0)
	 * @param startTime start time
	 * @param endTime   end time
	 */
	public IntervalT(int startTime, int endTime) {
		this("", startTime, endTime, 0, null);
	}

	/**
	 * Constructor (unnamed interval)
	 * @param startTime start time
	 * @param endTime   end time
	 * @param group     the group number
	 */
	public IntervalT(int startTime, int endTime, int group) {
		this("", startTime, endTime, group, null);
	}

	/** @return the start time */
	public long getStartTime() {
		return start;
	}

	/** @return the end time */
	public long getEndTime() {
		return end;
	}

	/** @return the duration (end - start) */
	public long getDuration() {
		return end - start;
	}

	/** @return the colour of this interval, or null if the group colour is used */
	public Color getColor() {
		return color;
	}

	/** @return the group */
	public int getGroup() {
		return group;
	}

	@Override
	public String toString() {
		return getName() + "[" + start + "," + end + "]";
	}
}