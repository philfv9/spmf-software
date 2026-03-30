package ca.pfv.spmf.gui.pattern_diff_analyzer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger
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
 * Data transfer object holding pattern file data. Contains the parsed patterns,
 * column information, and index mappings.
 * 
 * @author Philippe Fournier-Viger
 */
public class PatternData {

	/** The raw data rows (each row is a list of column values) */
	private List<List<Object>> data;

	/** Column names */
	private List<String> columnNames;

	/** Column classes (types) */
	private List<Class<?>> columnClasses;

	/** Map of normalized pattern string to row index */
	private Map<String, Integer> patternIndexMap;

	/** Item mapping (ID -> name) for extended format */
	private Map<String, String> itemMapping;

	/** Source file path */
	private String filePath;

	/**
	 * Default constructor.
	 */
	public PatternData() {
		this.data = new ArrayList<>();
		this.columnNames = new ArrayList<>();
		this.columnClasses = new ArrayList<>();
		this.patternIndexMap = new HashMap<>();
		this.itemMapping = new HashMap<>();
	}

	/**
	 * Full constructor.
	 * 
	 * @param data            the pattern data rows
	 * @param columnNames     the column names
	 * @param columnClasses   the column classes
	 * @param patternIndexMap the pattern to index mapping
	 * @param itemMapping     the item ID to name mapping
	 */
	public PatternData(List<List<Object>> data, List<String> columnNames, List<Class<?>> columnClasses,
			Map<String, Integer> patternIndexMap, Map<String, String> itemMapping) {
		this.data = data;
		this.columnNames = columnNames;
		this.columnClasses = columnClasses;
		this.patternIndexMap = patternIndexMap;
		this.itemMapping = itemMapping;
	}

	/**
	 * Get the number of patterns.
	 * 
	 * @return pattern count
	 */
	public int getPatternCount() {
		return data != null ? data.size() : 0;
	}

	/**
	 * Get the number of columns.
	 * 
	 * @return column count
	 */
	public int getColumnCount() {
		return columnNames != null ? columnNames.size() : 0;
	}

	/**
	 * Check if data is empty.
	 * 
	 * @return true if no patterns loaded
	 */
	public boolean isEmpty() {
		return data == null || data.isEmpty();
	}

	/**
	 * Get a specific row.
	 * 
	 * @param index row index
	 * @return the row data
	 */
	public List<Object> getRow(int index) {
		if (data == null || index < 0 || index >= data.size()) {
			return null;
		}
		return data.get(index);
	}

	/**
	 * Get the pattern string from a row.
	 * 
	 * @param rowIndex the row index
	 * @return the pattern string
	 */
	public String getPattern(int rowIndex) {
		List<Object> row = getRow(rowIndex);
		if (row != null && !row.isEmpty()) {
			return row.get(0).toString();
		}
		return null;
	}

	/**
	 * Get column index by name.
	 * 
	 * @param columnName the column name
	 * @return the index, or -1 if not found
	 */
	public int getColumnIndex(String columnName) {
		if (columnNames == null || columnName == null) {
			return -1;
		}
		return columnNames.indexOf(columnName);
	}

	/**
	 * Get numeric column names (for measure selection).
	 * 
	 * @return list of numeric column names
	 */
	public List<String> getNumericColumnNames() {
		List<String> numericColumns = new ArrayList<>();
		if (columnNames == null || columnClasses == null) {
			return numericColumns;
		}

		// Start from index 1 to skip "Pattern" column
		for (int i = 1; i < columnNames.size(); i++) {
			Class<?> colClass = (i < columnClasses.size()) ? columnClasses.get(i) : Object.class;
			if (isNumericClass(colClass)) {
				numericColumns.add(columnNames.get(i));
			}
		}
		return numericColumns;
	}

	/**
	 * Check if a class represents a numeric type.
	 * 
	 * @param clazz the class to check
	 * @return true if numeric
	 */
	private boolean isNumericClass(Class<?> clazz) {
		return clazz.equals(Integer.class) || clazz.equals(Double.class) || clazz.equals(Float.class)
				|| clazz.equals(Long.class) || Number.class.isAssignableFrom(clazz);
	}

	/**
	 * Get numeric value from a row at specified column index.
	 * 
	 * @param rowIndex    the row index
	 * @param columnIndex the column index
	 * @return the numeric value, or null if not available
	 */
	public Double getNumericValue(int rowIndex, int columnIndex) {
		List<Object> row = getRow(rowIndex);
		if (row == null || columnIndex < 0 || columnIndex >= row.size()) {
			return null;
		}

		Object value = row.get(columnIndex);
		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		}
		if (value instanceof String) {
			try {
				return Double.valueOf(((String) value).trim());
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	/**
	 * Rebuild the pattern index map with a new matching method.
	 * 
	 * @param matchingMethod the pattern matching method
	 */
	public void rebuildPatternIndexMap(String matchingMethod) {
		patternIndexMap = new HashMap<>();
		for (int i = 0; i < data.size(); i++) {
			String pattern = data.get(i).get(0).toString();
			String normalized = GenericPatternFileReader.normalizePattern(pattern, matchingMethod);
			patternIndexMap.put(normalized, i);
		}
	}

	// ==================== Getters and Setters ====================

	public List<List<Object>> getData() {
		return data;
	}

	public void setData(List<List<Object>> data) {
		this.data = data;
	}

	public List<String> getColumnNames() {
		return columnNames;
	}

	public void setColumnNames(List<String> columnNames) {
		this.columnNames = columnNames;
	}

	public List<Class<?>> getColumnClasses() {
		return columnClasses;
	}

	public void setColumnClasses(List<Class<?>> columnClasses) {
		this.columnClasses = columnClasses;
	}

	public Map<String, Integer> getPatternIndexMap() {
		return patternIndexMap;
	}

	public void setPatternIndexMap(Map<String, Integer> patternIndexMap) {
		this.patternIndexMap = patternIndexMap;
	}

	public Map<String, String> getItemMapping() {
		return itemMapping;
	}

	public void setItemMapping(Map<String, String> itemMapping) {
		this.itemMapping = itemMapping;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}
}