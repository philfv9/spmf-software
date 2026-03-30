package ca.pfv.spmf.input.event_sequence;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * This class represents an event sequence as used by algorithms such as TKE,
 * EMMA etc.
 * 
 * @author Philippe Fournier-Viger
 */
public class EventSequence {

	/** The list of events in this sequence */
	private List<Event> events;
	/** The set of unique events in this sequence */
	private Set<Integer> uniqueEvents;
	/** The minimum timestamp in this sequence */
	private long minTimestamp;
	/** the maximum timestamp */
	private long maxTimestamp;
	
	private int maxItemID;

	/**
	 * A map, where an entry in the map is key = String (attribute value), value =
	 * Integer (item id)
	 */
	Map<Integer, String> mapItemIDtoStringValue = null;

	/** The constructor that creates an empty sequence */
	public EventSequence() {
		// Initialize the fields
		this.events = new ArrayList<Event>();
		this.uniqueEvents = new HashSet<Integer>();
		this.minTimestamp = Long.MAX_VALUE;
		this.maxTimestamp = Long.MIN_VALUE;
	}

	/**
	 * The method that loads a sequence from a file
	 * 
	 * @param filepath the input file path
	 */
	public void loadFile(String filepath) throws IOException {
		
		maxItemID = 0;
		// Create a buffered reader to read the file
		BufferedReader reader = new BufferedReader(new FileReader(filepath));
		// Read the first line of the file
		String line = reader.readLine();
		// Loop until the end of the file
		while (line != null) {
			if (line.isEmpty() == false) {
				if (line.startsWith("@ITEM")) {
					// remove "@ITEM="
					line = line.substring(6);
					// get the position of the first = in the remaining string
					int index = line.indexOf("=");
					int itemID = Integer.parseInt(line.substring(0, index));
					String stringValue = line.substring(index + 1);
//					System.out.println(itemID);
//					System.out.println(stringValue);
					if (mapItemIDtoStringValue == null) {
						mapItemIDtoStringValue = new HashMap<Integer, String>();
					}
					mapItemIDtoStringValue.put(itemID, stringValue);
					
					if(itemID > maxItemID) {
						maxItemID = itemID;
					}
				} else if (line.isEmpty() == false && line.charAt(0) != '#' && line.charAt(0) != '%'
						&& line.charAt(0) != '@') {
					// Split the line by the "|" character
					String[] split = line.split("\\|");
					// Get the event set as the first part of the split
					String eventSet = split[0];
					// Get the timestamp as the second part of the split, or use the current size of
					// the sequence as the timestamp if not provided
					long timestamp = split.length > 1 ? Long.parseLong(split[1]) : events.size();
					// Split the event set by the space character
					String[] items = eventSet.split(" ");
					// Loop through the items in the event set
					for (String item : items) {
						// Parse the item as an integer
						int event = Integer.parseInt(item);
						
						if(event > maxItemID) {
							maxItemID = event;
						}
						// Create a new Event object with the item and the timestamp
						Event e = new Event(event, timestamp);
						// Add the event to the sequence
						events.add(e);
						// Add the event to the set of unique events
						uniqueEvents.add(event);
						// Update the minimum and maximum timestamps
						if (timestamp < minTimestamp) {
							minTimestamp = timestamp;
						}
						if (timestamp > maxTimestamp) {
							maxTimestamp = timestamp;
						}
					}
				}
			}

			// Read the next line of the file
			line = reader.readLine();
		}
		// Close the reader
		reader.close();
	}

	/**
	 * The method that returns the size of the sequence (number of events)
	 * 
	 * @return the size of the sequence
	 */
	public int size() {
		return events.size();
	}

	/**
	 * The method that returns the event at a given index
	 * 
	 * @return the i-th event
	 */
	public Event get(int index) {
		return events.get(index);
	}

	/**
	 * The method that returns the set of unique events in the sequence
	 * 
	 * @return the set of unique events
	 */
	public Set<Integer> getUniqueEvents() {
		return uniqueEvents;
	}

	/**
	 * The method that returns the minimum timestamp in the sequence
	 * 
	 * @return the minimum timestamp
	 */
	public long getMinTimestamp() {
		return minTimestamp;
	}

	/** The method that returns the maximum timestamp in the sequence */
	public long getMaxTimestamp() {
		return maxTimestamp;
	}

	/**
	 * The method that returns the string representation of the sequence
	 * 
	 * @return a string
	 */
	public String toString() {
		// Use a string builder to append the events
		StringBuilder sb = new StringBuilder();
		// Loop through the events in the sequence
		for (Event e : events) {
			// Append the event and a space
			sb.append(e).append(" ");
		}
		// Remove the last space
		sb.setLength(sb.length() - 1);
		// Return the string
		return sb.toString();
	}
	
	/**
	 * Get the name corresponding to an item id, if one is known. Otherwise returns
	 * null.
	 * 
	 * @param item the item
	 * @return a string or null
	 */
	public String getNameForItem(Integer item) {
		if (mapItemIDtoStringValue == null) {
			return null;
		}
		String name = mapItemIDtoStringValue.get(item);
		if (name == null) {
			return null;
		}
		return name;
	}
	
	/**
	 * Return a map to convert item names to strings (if applicable)
	 * @return a map or null;
	 */
	public Map<Integer, String> getMapItemToStringValues() {
		return mapItemIDtoStringValue;
	}
	
	/** Get the maximum Item ID
	 * 
	 * @return the id
	 */
	public int getMaxItemID() {
		return maxItemID;
	}
//	
}
