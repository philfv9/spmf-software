package ca.pfv.spmf.gui.viewers.timeintervaldbviewer;

import java.util.ArrayList;
import java.util.List;
/*
 * Copyright (c) 2008-2024 Philippe Fournier-Viger
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
 * A time interval sequence as used by algorithms like FastTIRP and VertTIRP
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class TimeIntervalSequence {

	/** the list of time intervals */
	private List<SymbolicTimeInterval> timeIntervals;

	/**
	 * Constructor
	 */
	public TimeIntervalSequence() {
		timeIntervals = new ArrayList<SymbolicTimeInterval>();
	}

	/**
	 * Add a time interval to this sequence
	 * 
	 * @param item the time interval
	 */
	public void add(SymbolicTimeInterval item) {
		timeIntervals.add(item);
	}

	/**
	 * Get the size of this sequence
	 * 
	 * @return the size
	 */
	public int size() {
		return timeIntervals.size();
	}

	/**
	 * Get the time interval at a given position in this sequence
	 * 
	 * @param index the position
	 * @return the time interval
	 */
	public SymbolicTimeInterval get(int index) {
		return timeIntervals.get(index);
	}

	/**
	 * Get the list of time intervals in this sequence
	 * 
	 * @return the list of time intervals
	 */
	public List<SymbolicTimeInterval> getTimeIntervals() {
		return timeIntervals;
	}

	/**
	 * Get a string representation of this sequence
	 * 
	 * @return the string
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (SymbolicTimeInterval item : timeIntervals) {
			builder.append(item.toString());
			builder.append(' ');
		}
		return builder.toString();
	}

}