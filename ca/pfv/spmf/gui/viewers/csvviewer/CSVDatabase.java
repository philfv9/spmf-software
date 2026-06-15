package ca.pfv.spmf.gui.viewers.csvviewer;

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

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * A tool to read a file in CSV format. This class reads CSV files with a
 * configurable separator (comma, semicolon, tab, etc.) and stores the data
 * as a list of records. The first row can optionally be treated as a header.
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class CSVDatabase {

    /** The list of records */
    private List<List<String>> records;

    /** The list of column names (from the header row, if present) */
    private List<String> columnNames;

    /** Whether the first row is a header row */
    private boolean hasHeader;

    /** The separator character used in the CSV file */
    private String separator;

    /**
     * Constructor
     */
    public CSVDatabase() {
        records = new ArrayList<List<String>>();
        columnNames = new ArrayList<String>();
        hasHeader = false;
        separator = ",";
    }

    /**
     * Load a file in CSV format with the default comma separator and no header
     * 
     * @param filepath the file path
     * @throws Exception if an error occurs while reading the file
     */
    public void loadFile(String filepath) throws Exception {
        loadFile(filepath, ",", false);
    }

    /**
     * Load a file in CSV format with the specified separator and header setting
     * 
     * @param filepath  the file path
     * @param separator the separator character (e.g., ",", ";", "\t", "|")
     * @param hasHeader whether the first row should be treated as column names
     * @throws Exception if an error occurs while reading the file
     */
    public void loadFile(String filepath, String separator, boolean hasHeader) throws Exception {
        // Clear existing data
        records.clear();
        columnNames.clear();
        this.separator = separator;
        this.hasHeader = hasHeader;

        // Create a buffered reader to read the file
        BufferedReader reader = new BufferedReader(new FileReader(filepath));
        String line = null;
        boolean firstRow = true;

        // Loop through the lines of the file
        while ((line = reader.readLine()) != null) {
            // Trim the line to remove leading and trailing spaces
            line = line.trim();
            
            // If the line is empty, skip it
            if (line.isEmpty()) {
                continue;
            }

            // Split the line by the separator
            String[] values = line.split(separator);
            
            // Create a list to store the record
            List<String> record = new ArrayList<String>();
            
            // Loop through the values
            for (String value : values) {
                // Trim the value to remove spaces
                value = value.trim();
                
                // If the value is quoted, remove the quotes
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length() - 1);
                }
                
                // Add the value to the record
                record.add(value);
            }

            // If this is the first row and hasHeader is true, treat it as column names
            if (firstRow && hasHeader) {
                columnNames.addAll(record);
                firstRow = false;
            } else {
                // Add the record to the list of records
                records.add(record);
                firstRow = false;
            }
        }

        // Close the reader
        reader.close();

        // If no header was provided, generate default column names
        if (columnNames.isEmpty() && !records.isEmpty()) {
            int numColumns = records.get(0).size();
            for (int i = 0; i < numColumns; i++) {
                columnNames.add("Column" + (i + 1));
            }
        }
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
     * Get the list of column names
     * 
     * @return the list of column names
     */
    public List<String> getColumnNames() {
        return columnNames;
    }

    /**
     * Get the size of the database
     * 
     * @return the number of records
     */
    public int size() {
        return records.size();
    }

    /**
     * Get the separator used in the CSV file
     * 
     * @return the separator character
     */
    public String getSeparator() {
        return separator;
    }

    /**
     * Check if the file has a header row
     * 
     * @return true if the first row is a header, false otherwise
     */
    public boolean hasHeader() {
        return hasHeader;
    }
}