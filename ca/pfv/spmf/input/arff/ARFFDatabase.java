package ca.pfv.spmf.input.arff;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
/*
 * Copyright (c) 2024 Philippe Fournier-Viger
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
 * A tool to read a file in ARFF format. This class is very basic and just read the information in the simplest way
 * to be able to display it as a table.
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class ARFFDatabase {

	/** The list of records **/
	private List<List<String>> records;
	
	/** The list of attributes **/
	private List<String> attributeNames;

	/**
	 * Constructor
	 */
	public ARFFDatabase() {
		// Initialize the fields
		records = new ArrayList<List<String>>();
		attributeNames = new ArrayList<String>();
	}

	/**
	 * Load a file in the ARFF format
	 * 
	 * @param filepath the file path
	 * @throws Exception if an error occurs while reading the file
	 */
	public void loadFile(String filepath) throws Exception {
		// Create a buffered reader to read the file
		BufferedReader reader = new BufferedReader(new FileReader(filepath));
		// Initialize a variable to store the current line
		String line = null;
		// Initialize a flag to indicate if we are in the data section
		boolean inData = false;
		// Loop through the lines of the file
		while ((line = reader.readLine()) != null) {
			// Trim the line to remove leading and trailing spaces
			line = line.trim();
			// If the line is empty or starts with a comment, skip it
			if (line.isEmpty() || line.startsWith("%")) {
				continue;
			}
			// If the line starts with @RELATION, skip it
			if (line.startsWith("@RELATION") || line.startsWith("@relation")) {
				continue;
			}
			// If the line starts with @ATTRIBUTE, parse the attribute name and type
			if (line.startsWith("@ATTRIBUTE") || line.startsWith("@attribute")) {
				// Split the line by spaces
				String[] parts = line.split("\\s+");
				// Get the attribute name, which is the second part
				String name = parts[1];
				// If the name is quoted, remove the quotes
				if (name.startsWith("'") && name.endsWith("'")) {
					name = name.substring(1, name.length() - 1);
				}
				attributeNames.add(name);
				
				// Get the attribute type, which is the third part
//				String type = parts[2];
				// ****** *We do nothing with the type information at this moment  *****
				// ****** Because this is just a simple class to display the basic content of an ARFF file ***
			}
			// If the line starts with @DATA, set the flag to true
			if (line.startsWith("@DATA") || line.startsWith("@data")) {
				inData = true;
				continue;
			}
			// If we are in the data section, parse the record
			if (inData) {
				// Split the line by commas
				String[] values = line.split(",");
				// Create a list to store the record
				List<String> record = new ArrayList<String>();
				// Loop through the values
				for (String value : values) {
					// Trim the value to remove spaces
					value = value.trim();
					// If the value is quoted, remove the quotes
					if (value.startsWith("'") && value.endsWith("'")) {
						value = value.substring(1, value.length() - 1);
					}
					// Add the value to the record
					record.add(value);
				}
				// Add the record to the list of records
				records.add(record);
			}
		}
		// Close the reader
		reader.close();
	}

	/**
	 * Get the list of records
	 * 
	 * @return the list of records
	 */
	public List<List<String>> getRecords() {
		return records;
	}

	/**
	 * Get the list of items
	 * 
	 * @return the list of items
	 */
	public List<String> getAttributeNames() {
		return attributeNames;
	}

	/**
	 * Get the size of the database
	 * 
	 * @return the number of records
	 */
	public int size() {
		return records.size();
	}
}
