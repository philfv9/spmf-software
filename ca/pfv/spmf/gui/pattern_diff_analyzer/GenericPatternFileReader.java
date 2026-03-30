package ca.pfv.spmf.gui.pattern_diff_analyzer;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
 * Reads and parses SPMF pattern files. Supports both standard SPMF format and
 * extended format with @ITEM mappings.
 * 
 * @author Philippe Fournier-Viger
 */
public class GenericPatternFileReader {

	/** Debug mode flag */
	private boolean debugMode = false;

	/**
	 * Default constructor.
	 */
	public GenericPatternFileReader() {
	}

	/**
	 * Constructor with debug mode.
	 * 
	 * @param debugMode true to enable debug output
	 */
	public GenericPatternFileReader(boolean debugMode) {
		this.debugMode = debugMode;
	}

	/**
	 * Read a pattern file and return parsed data.
	 * 
	 * @param filePath       the file path
	 * @param matchingMethod the pattern matching method for normalization
	 * @return the parsed PatternData
	 * @throws IOException if error reading file
	 */
	public PatternData readFile(String filePath, String matchingMethod) throws IOException {
		PatternData result = new PatternData();
		result.setFilePath(filePath);

		List<List<Object>> data = new ArrayList<>();
		List<String> columnNames = new ArrayList<>();
		List<Class<?>> columnClasses = new ArrayList<>();
		Map<String, Integer> patternIndexMap = new HashMap<>();
		Map<String, String> itemMapping = new HashMap<>();

		columnNames.add("Pattern");
		columnClasses.add(String.class);

		boolean columnsInitialized = false;
		int numberOfPatterns = 0;

		try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
			String line;
			int lineNumber = 0;

			while ((line = br.readLine()) != null) {
				lineNumber++;
				line = line.trim();

				// Skip blank lines and comments
				if (line.isEmpty() || line.startsWith("%")) {
					continue;
				}

				// Handle extended mapping lines: @ITEM=id=name
				if (line.startsWith("@ITEM=")) {
					String[] parts = line.substring(6).split("=", 2);
					if (parts.length == 2) {
						itemMapping.put(parts[0].trim(), parts[1].trim());
					}
					continue;
				}

				// Skip other metadata lines starting with @
				if (line.startsWith("@")) {
					continue;
				}

				if (debugMode) {
					System.out.println("Processing line " + lineNumber + ": " + line);
				}

				// Process data line
				List<Object> lineData = new ArrayList<>();

				// Check if line contains "#" delimiter for measures
				if (!line.contains("#")) {
					// No measures, just pattern
					String mappedPattern = mapPattern(line.trim(), itemMapping);
					lineData.add(mappedPattern);

					String normalizedPattern = normalizePattern(mappedPattern, matchingMethod);
					patternIndexMap.put(normalizedPattern, numberOfPatterns);

					data.add(lineData);
					numberOfPatterns++;
					continue;
				}

				// Find all # positions
				List<Integer> hashPositions = findHashPositions(line);

				if (debugMode) {
					System.out.println("  Found # at positions: " + hashPositions);
				}

				// Pattern is everything before the first #
				String patternPart = hashPositions.isEmpty() ? line : line.substring(0, hashPositions.get(0)).trim();

				String mappedPattern = mapPattern(patternPart, itemMapping);
				lineData.add(mappedPattern);

				if (debugMode) {
					System.out.println("  Pattern: " + mappedPattern);
				}

				// Store pattern index for later matching
				String normalizedPattern = normalizePattern(mappedPattern, matchingMethod);
				patternIndexMap.put(normalizedPattern, numberOfPatterns);

				// Parse each measure segment
				for (int i = 0; i < hashPositions.size(); i++) {
					int startPos = hashPositions.get(i) + 1;
					int endPos = (i + 1 < hashPositions.size()) ? hashPositions.get(i + 1) : line.length();

					String segment = line.substring(startPos, endPos).trim();

					if (debugMode) {
						System.out.println("  Segment " + i + ": '" + segment + "'");
					}

					if (segment.isEmpty()) {
						continue;
					}

					// Parse attribute name and value
					ParsedAttribute attr = parseAttribute(segment);

					if (debugMode) {
						System.out.println("    Attr: '" + attr.name + "' = '" + attr.value + "'");
					}

					if (attr.name == null || attr.name.isEmpty()) {
						continue;
					}

					// Register columns on first pattern
					if (!columnsInitialized) {
						columnNames.add(attr.name);
						columnClasses.add(determineType(attr.value));
					}

					// Add value with appropriate type
					lineData.add(parseValue(attr.value));
				}

				// Mark columns as initialized after first pattern with measures
				if (!columnsInitialized && lineData.size() > 1) {
					columnsInitialized = true;
				}

				data.add(lineData);
				numberOfPatterns++;

				if (debugMode) {
					System.out.println("  LineData size: " + lineData.size());
					System.out.println("  ColumnNames: " + columnNames);
				}
			}
		}

		if (debugMode) {
			System.out.println("\n=== SUMMARY ===");
			System.out.println("Total patterns: " + numberOfPatterns);
			System.out.println("Column names: " + columnNames);
			System.out.println("Column classes: " + columnClasses);
		}

		// Populate result
		result.setData(data);
		result.setColumnNames(columnNames);
		result.setColumnClasses(columnClasses);
		result.setPatternIndexMap(patternIndexMap);
		result.setItemMapping(itemMapping);

		return result;
	}

	/**
	 * Find all positions of '#' character in a line.
	 * 
	 * @param line the line to search
	 * @return list of positions
	 */
	private List<Integer> findHashPositions(String line) {
		List<Integer> positions = new ArrayList<>();
		for (int i = 0; i < line.length(); i++) {
			if (line.charAt(i) == '#') {
				positions.add(i);
			}
		}
		return positions;
	}

	/**
	 * Parse an attribute segment into name and value. Handles formats like "NAME:
	 * VALUE" or "NAME VALUE"
	 */
	private ParsedAttribute parseAttribute(String segment) {
		String attrName = null;
		String attrVal = null;

		// Try splitting by colon first
		int colonPos = segment.indexOf(':');
		if (colonPos > 0) {
			attrName = segment.substring(0, colonPos).trim();
			attrVal = segment.substring(colonPos + 1).trim();
		} else {
			// Try splitting by space
			int spacePos = segment.indexOf(' ');
			if (spacePos > 0) {
				attrName = segment.substring(0, spacePos).trim();
				attrVal = segment.substring(spacePos + 1).trim();
			} else {
				// Just an attribute name with no value
				attrName = segment;
				attrVal = "";
			}
		}

		return new ParsedAttribute(attrName, attrVal);
	}

	/**
	 * Simple holder for parsed attribute.
	 */
	private static class ParsedAttribute {
		final String name;
		final String value;

		ParsedAttribute(String name, String value) {
			this.name = name;
			this.value = value;
		}
	}

	/**
	 * Determine the class type for a value string.
	 * 
	 * @param value the value string
	 * @return the appropriate class
	 */
	private Class<?> determineType(String value) {
		if (parseInteger(value) != null) {
			return Integer.class;
		} else if (parseDouble(value) != null) {
			return Double.class;
		} else {
			return String.class;
		}
	}

	/**
	 * Parse a value string into appropriate type.
	 * 
	 * @param value the value string
	 * @return the parsed value
	 */
	private Object parseValue(String value) {
		Integer intVal = parseInteger(value);
		if (intVal != null) {
			return intVal;
		}

		Double dblVal = parseDouble(value);
		if (dblVal != null) {
			return dblVal;
		}

		return value;
	}

	/**
	 * Map pattern items using the item mapping.
	 * 
	 * @param pattern     the raw pattern
	 * @param itemMapping the item ID to name mapping
	 * @return the mapped pattern
	 */
	public String mapPattern(String pattern, Map<String, String> itemMapping) {
		if (itemMapping == null || itemMapping.isEmpty()) {
			return pattern;
		}
		return Arrays.stream(pattern.trim().split("\\s+")).map(tok -> itemMapping.getOrDefault(tok, tok))
				.collect(Collectors.joining(" "));
	}

	/**
	 * Normalize a pattern string based on the matching method.
	 * 
	 * @param pattern        the pattern string
	 * @param matchingMethod the matching method
	 * @return the normalized pattern string
	 */
	public static String normalizePattern(String pattern, String matchingMethod) {
		if (matchingMethod == null) {
			matchingMethod = "Exact itemset match";
		}

		switch (matchingMethod) {
		case "Itemset match (ignore order)":
			String[] items = pattern.trim().split("\\s+");
			Arrays.sort(items);
			return String.join(" ", items);
		case "String match (raw)":
			return pattern.trim();
		default: // Exact match
			return pattern.trim();
		}
	}

	/**
	 * Parse a string as Double.
	 * 
	 * @param token the string to parse
	 * @return the Double value, or null if not valid
	 */
	public static Double parseDouble(String token) {
		try {
			return Double.valueOf(token.trim());
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Parse a string as Integer.
	 * 
	 * @param token the string to parse
	 * @return the Integer value, or null if not valid
	 */
	public static Integer parseInteger(String token) {
		try {
			return Integer.valueOf(token.trim());
		} catch (Exception e) {
			return null;
		}
	}

	// ==================== Getters and Setters ====================

	public boolean isDebugMode() {
		return debugMode;
	}

	public void setDebugMode(boolean debugMode) {
		this.debugMode = debugMode;
	}
}