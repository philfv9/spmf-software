package ca.pfv.spmf.gui.viewers.eventsequenceviewer;
/*
 * Copyright (c) 2008-2015 Philippe Fournier-Viger
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
 * This class represent an event from an event sequence
 * @author Philippe Fournier-Viger
 */
public class Event {

	/** The item (event) value */
	private int item;
	/** The timestamp of the event */
	private long timestamp;

	/**
	 * The constructor that takes an item and a timestamp as parameters
	 * 
	 * @param item       item
	 * @param timestamps timestamp
	 */
	public Event(int item, long timestamp) {
		// Initialize the fields
		this.item = item;
		this.timestamp = timestamp;
	}

	/**
	 * The method that returns the item value
	 * 
	 * @return the item
	 */
	public int getItem() {
		return item;
	}

	/**
	 * The method that returns the timestamp
	 * 
	 * @return the timestamp
	 */
	public long getTimestamp() {
		return timestamp;
	}

	/**
	 * The method that returns the string representation of the event
	 * 
	 * @return a string
	 */
	public String toString() {
		// Return the item and the timestamp separated by a "|"
		return item + "|" + timestamp;
	}
}
