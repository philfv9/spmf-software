package ca.pfv.spmf.gui.viewers.timeintervaldbviewer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * A time interval sequence  database as used by algorithms like FastTIRP and VertTIRP
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class TimeIntervalDatabase {

	/** the list of sequences */
	private List<TimeIntervalSequence> sequences;
	/** the map from item IDs to item names */
	private Map<Integer, String> mapItemToStringValues;
	/** the minimum item ID */
	public int minItem;
	/** the maximum item ID */
	public int maxItem;

	/**
	 * Constructor
	 */
	public TimeIntervalDatabase() {
		sequences = new ArrayList<TimeIntervalSequence>();
		mapItemToStringValues = new HashMap<Integer, String>();
		minItem = Integer.MAX_VALUE;
		maxItem = 0;
	}

	/**
	 * Load a file containing a time interval database
	 * 
	 * @param path the file path
	 * @throws IOException if an error occurs while reading the file
	 */
	public void loadFile(String path) throws IOException {
		// Create a buffered reader to read the file
		BufferedReader reader = new BufferedReader(new FileReader(path));
		// Read each line of the file until the end
		String line;
		while ((line = reader.readLine()) != null) {
			
			if (line.startsWith("@ITEM")) {
				// remove "@ITEM="
				line = line.substring(6);
				// get the position of the first = in the remaining string
				int index = line.indexOf("=");
				int itemID = Integer.parseInt(line.substring(0, index));
				String stringValue = line.substring(index + 1);
//				System.out.println(itemID);
//				System.out.println(stringValue);
				if(mapItemToStringValues == null) {
					mapItemToStringValues = new HashMap<Integer, String>();
				}
				mapItemToStringValues.put(itemID, stringValue);
			} else if (line.isEmpty() == false && line.charAt(0) != '#' && line.charAt(0) != '%'
					&& line.charAt(0) != '@') {
//			
				// Create a new sequence object
				TimeIntervalSequence sequence = new TimeIntervalSequence();
				// Split the line into tokens by the ";" separator
				String[] tokens = line.split(";");
				// For each token
				for (String token : tokens) {
					// If the token is not empty
					if (token.length() != 0) {
						// Split the token into three parts by the "," separator
						String[] parts = token.split(",");
						// Get the item ID, the start time and the end time as integers
						int start = Integer.parseInt(parts[0]);
						int end = Integer.parseInt(parts[1]);
						int itemID = Integer.parseInt(parts[2]);
						// Create a new symbolic time interval object
						SymbolicTimeInterval item = new SymbolicTimeInterval(itemID, start, end);
						// Add the item to the sequence
						sequence.add(item);
						// Update the min and max item IDs
						if (itemID < minItem) {
							minItem = itemID;
						}
						if (itemID > maxItem) {
							maxItem = itemID;
						}
					}
				}
				// Add the sequence to the database
				sequences.add(sequence);
			}
		}
		// Close the reader
		reader.close();
	}

	/**
	 * Get the size of the database
	 * 
	 * @return the size
	 */
	public int size() {
		return sequences.size();
	}

	/**
	 * Get the list of sequences in the database
	 * 
	 * @return the list of sequences
	 */
	public List<TimeIntervalSequence> getSequences() {
		return sequences;
	}

	/**
	 * Get the name of an item for a given item ID
	 * 
	 * @param itemID the item ID
	 * @return the name of the item, or null if not found
	 */
	public String getNameForItem(int itemID) {
		return mapItemToStringValues.get(itemID);
	}

	/**
	 * Get the map from item IDs to item names
	 * 
	 * @return the map
	 */
	public Map<Integer, String> getMapItemToStringValues() {
		return mapItemToStringValues;
	}

	/**
	 * Get the maximum item ID in the database
	 * 
	 * @return the maximum item ID
	 */
	public int getMaxItemID() {
		return maxItem;
	}

	/**
	 * Get a string representation of the database
	 * 
	 * @return the string
	 */
	public String toString() {
		StringBuilder builder = new StringBuilder();
		for (TimeIntervalSequence sequence : sequences) {
			builder.append(sequence.toString());
			builder.append('\n');
		}
		return builder.toString();
	}

}
