package ca.pfv.spmf.gui.viewers.arffviewer;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
 * A lightweight reader for files in the ARFF format, intended for displaying
 * the content of a file as a table.
 * <p>
 * Supported features:
 * <ul>
 * <li>{@code @relation}, {@code @attribute} and {@code @data} declarations
 * (case-insensitive)</li>
 * <li>attribute types: numeric/real/integer, nominal, string, date and
 * relational (nested attributes of relational attributes are skipped)</li>
 * <li>single- and double-quoted names and values, including escape sequences
 * such as {@code \'}, {@code \"}, {@code \\}, {@code \n}, {@code \t}</li>
 * <li>dense and sparse ({@code {index value, ...}}) data lines</li>
 * <li>instance weights (ignored)</li>
 * <li>missing values ({@code ?})</li>
 * </ul>
 * Malformed lines do not abort the loading: they are repaired when possible
 * (padding/truncation) and a warning is recorded (see {@link #getWarnings()}).
 * <p>
 * After loading, some per-attribute statistics (distinct value count, missing
 * value count) are precomputed so that viewers do not need to scan the data
 * repeatedly.
 * 
 * @author Philippe Fournier-Viger, 2024
 */
public class ARFFDatabase {

	/** The token used to represent a missing value in ARFF files */
	public static final String MISSING_VALUE = "?";

	/** Maximum number of warning messages kept in memory */
	private static final int MAX_STORED_WARNINGS = 200;

	/** The types of attributes that can appear in an ARFF file */
	public enum AttributeType {
		NUMERIC("Numeric"), NOMINAL("Nominal"), STRING("String"), DATE("Date"), RELATIONAL("Relational"),
		UNKNOWN("Unknown");

		private final String label;

		AttributeType(String label) {
			this.label = label;
		}

		/** @return a human readable label for this type */
		public String getLabel() {
			return label;
		}
	}

	/**
	 * An attribute declared in the header of an ARFF file.
	 */
	public static final class ARFFAttribute {
		private final String name;
		private final AttributeType type;
		private final String typeDeclaration;
		private final List<String> nominalValues;

		ARFFAttribute(String name, AttributeType type, String typeDeclaration, List<String> nominalValues) {
			this.name = name;
			this.type = type;
			this.typeDeclaration = typeDeclaration;
			this.nominalValues = Collections.unmodifiableList(nominalValues);
		}

		/** @return the attribute name (without quotes) */
		public String getName() {
			return name;
		}

		/** @return the attribute type */
		public AttributeType getType() {
			return type;
		}

		/** @return the raw type declaration as written in the file */
		public String getTypeDeclaration() {
			return typeDeclaration;
		}

		/** @return the declared values of a nominal attribute (empty otherwise) */
		public List<String> getNominalValues() {
			return nominalValues;
		}

		/** @return true if this attribute is declared as numeric */
		public boolean isNumeric() {
			return type == AttributeType.NUMERIC;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	/** The relation name (may be empty) */
	private String relationName = "";

	/** The list of attributes */
	private final List<ARFFAttribute> attributes = new ArrayList<>();

	/** The attribute names (kept in sync with {@link #attributes}) */
	private final List<String> attributeNames = new ArrayList<>();

	/** The list of records; every record has exactly one value per attribute */
	private final List<List<String>> records = new ArrayList<>();

	/** Warnings produced while loading */
	private final List<String> warnings = new ArrayList<>();

	/** Total number of warnings (may exceed the number of stored warnings) */
	private int warningCount;

	// ---- precomputed statistics ----
	private int[] distinctValueCounts = new int[0];
	private int[] missingValueCounts = new int[0];
	private boolean[] numericColumns = new boolean[0];
	private int totalMissingValues;

	// ---- transient parsing state ----
	private String[] sparseDefaults;
	private Map<String, String> valuePool;

	/**
	 * Constructor
	 */
	public ARFFDatabase() {
		// nothing to do
	}

	/**
	 * Load a file in the ARFF format. Previously loaded content is discarded.
	 * <p>
	 * The parser is lenient: keywords are recognized by prefix and case-insensitively,
	 * a missing {@code @data} section yields a database with attributes but no records,
	 * and a missing header yields attributes generated from the first record. Every
	 * such repair is reported through {@link #getWarnings()}.
	 * 
	 * @param filepath the file path
	 * @throws IOException if the file cannot be read
	 */
	public void loadFile(String filepath) throws IOException {
		clear();
		valuePool = new HashMap<>();
		Set<String> seenNames = new HashSet<>();

		boolean inData = false;
		int relationalDepth = 0;
		int lineNumber = 0;

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(filepath), StandardCharsets.UTF_8))) {

			String line;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				// Strip a UTF-8 byte order mark, if any
				if (lineNumber == 1 && line.startsWith("\uFEFF")) {
					line = line.substring(1);
				}
				String trimmed = line.trim();

				// Skip blank lines and comments
				if (trimmed.isEmpty() || trimmed.startsWith("%")) {
					continue;
				}

				if (inData) {
					parseDataLine(trimmed, lineNumber);
					continue;
				}

				// ---- header section ----
				// Keywords are matched by prefix (like the original reader) so that
				// slightly damaged declarations such as "@DATAxyz" are still recognized.
				String lower = trimmed.toLowerCase(Locale.ROOT);

				if (lower.startsWith("@relation")) {
					relationName = firstToken(trimmed.substring("@relation".length()));

				} else if (lower.startsWith("@attribute")) {
					if (relationalDepth > 0) {
						continue; // nested attribute of a relational attribute: not a column
					}
					ARFFAttribute attribute = parseAttributeDeclaration(
							trimmed.substring("@attribute".length()), lineNumber);
					if (attribute != null) {
						if (!seenNames.add(attribute.getName())) {
							addWarning(lineNumber, "duplicate attribute name '" + attribute.getName() + "'");
						}
						attributes.add(attribute);
						attributeNames.add(attribute.getName());
						if (attribute.getType() == AttributeType.RELATIONAL) {
							relationalDepth++;
						}
					}

				} else if (lower.startsWith("@end")) {
					if (relationalDepth > 0) {
						relationalDepth--;
					} else {
						addWarning(lineNumber, "unexpected @end ignored");
					}

				} else if (lower.startsWith("@data")) {
					String rest = trimmed.substring("@data".length()).trim();
					if (!rest.isEmpty()) {
						addWarning(lineNumber, "unexpected text after @data ignored: " + abbreviate(rest));
					}
					if (attributes.isEmpty()) {
						addWarning(lineNumber, "@data found but no @attribute was declared; "
								+ "attribute names will be generated from the first record");
					}
					inData = true;
					prepareSparseDefaults();

				} else {
					addWarning(lineNumber, "unexpected line in header ignored: " + abbreviate(trimmed));
				}
			}
		} finally {
			valuePool = null;
			sparseDefaults = null;
		}

		if (!inData) {
			addWarning(lineNumber, "no @data section was found; the file contains no records");
		}

		computeStatistics();
	}

	/**
	 * Remove all loaded content.
	 */
	public void clear() {
		relationName = "";
		attributes.clear();
		attributeNames.clear();
		records.clear();
		warnings.clear();
		warningCount = 0;
		distinctValueCounts = new int[0];
		missingValueCounts = new int[0];
		numericColumns = new boolean[0];
		totalMissingValues = 0;
	}

	// =====================================================================
	// Header parsing
	// =====================================================================

	/**
	 * Parse the remainder of an {@code @attribute} line (everything after the
	 * keyword).
	 * 
	 * @param rest       the text after "@attribute"
	 * @param lineNumber the line number (for warnings)
	 * @return the attribute, or null if the declaration is unusable
	 */
	private ARFFAttribute parseAttributeDeclaration(String rest, int lineNumber) {
		String s = rest.trim();
		if (s.isEmpty()) {
			addWarning(lineNumber, "@attribute without a name was ignored");
			return null;
		}

		// The name may be quoted (and then contain spaces)
		StringBuilder nameBuilder = new StringBuilder();
		int pos = readToken(s, 0, nameBuilder);
		String name = nameBuilder.toString();
		String typeDeclaration = s.substring(pos).trim();

		AttributeType type;
		List<String> nominalValues = Collections.emptyList();

		if (typeDeclaration.isEmpty()) {
			type = AttributeType.UNKNOWN;
			addWarning(lineNumber, "attribute '" + name + "' has no type");

		} else if (typeDeclaration.startsWith("{")) {
			type = AttributeType.NOMINAL;
			int close = typeDeclaration.lastIndexOf('}');
			if (close < 0) {
				addWarning(lineNumber, "missing closing '}' in declaration of attribute '" + name + "'");
				close = typeDeclaration.length();
			}
			String inner = typeDeclaration.substring(1, close);
			nominalValues = isBlank(inner) ? Collections.<String>emptyList() : splitCommaSeparated(inner);

		} else {
			String keyword = typeDeclaration.toLowerCase(Locale.ROOT).split("\\s+")[0];
			switch (keyword) {
			case "numeric":
			case "real":
			case "integer":
				type = AttributeType.NUMERIC;
				break;
			case "string":
				type = AttributeType.STRING;
				break;
			case "date":
				type = AttributeType.DATE;
				break;
			case "relational":
				type = AttributeType.RELATIONAL;
				break;
			default:
				type = AttributeType.UNKNOWN;
				addWarning(lineNumber, "unknown type '" + typeDeclaration + "' for attribute '" + name + "'");
			}
		}
		return new ARFFAttribute(name, type, typeDeclaration, nominalValues);
	}

	/**
	 * Precompute the value used for attributes not listed in a sparse data line
	 * (0 for numeric attributes, the first declared value for nominal ones).
	 */
	private void prepareSparseDefaults() {
		sparseDefaults = new String[attributes.size()];
		for (int i = 0; i < sparseDefaults.length; i++) {
			ARFFAttribute attribute = attributes.get(i);
			if (attribute.getType() == AttributeType.NOMINAL && !attribute.getNominalValues().isEmpty()) {
				sparseDefaults[i] = attribute.getNominalValues().get(0);
			} else {
				sparseDefaults[i] = "0";
			}
		}
	}

	// =====================================================================
	// Data parsing
	// =====================================================================

	/**
	 * Parse one (non-empty, trimmed) line of the data section.
	 */
	private void parseDataLine(String line, int lineNumber) {
		int numAttributes = attributes.size();
		List<String> values;

		if (line.startsWith("{")) {
			values = parseSparseLine(line, lineNumber);
		} else {
			values = splitCommaSeparated(line);
			// Drop an optional instance weight: "v1, v2, ..., {weight}"
			int last = values.size() - 1;
			if (last > 0 && values.size() == numAttributes + 1 && isWeightToken(values.get(last))) {
				values.remove(last);
			}
		}

		// Repair records with a wrong number of values
		if (values.size() != numAttributes) {
			addWarning(lineNumber, "expected " + numAttributes + " values but found " + values.size());
			if (values.size() < numAttributes) {
				while (values.size() < numAttributes) {
					values.add(MISSING_VALUE);
				}
			} else {
				values = new ArrayList<>(values.subList(0, numAttributes));
			}
		}

		// Canonicalize values to reduce memory usage: the missing marker is shared,
		// and nominal values (which repeat a lot) are pooled.
		for (int i = 0; i < numAttributes; i++) {
			String value = values.get(i);
			if (MISSING_VALUE.equals(value)) {
				values.set(i, MISSING_VALUE);
			} else if (attributes.get(i).getType() == AttributeType.NOMINAL) {
				String pooled = valuePool.putIfAbsent(value, value);
				if (pooled != null) {
					values.set(i, pooled);
				}
			}
		}
		records.add(values);
	}

	/**
	 * Parse a sparse data line of the form {@code {index value, index value, ...}}.
	 */
	private List<String> parseSparseLine(String line, int lineNumber) {
		int numAttributes = attributes.size();
		List<String> values = new ArrayList<>(numAttributes);
		for (int i = 0; i < numAttributes; i++) {
			values.add(sparseDefaults[i]);
		}

		int close = findClosingBrace(line);
		if (close < 0) {
			addWarning(lineNumber, "missing closing '}' in sparse record");
			close = line.length();
		}
		String inner = line.substring(1, close);
		if (isBlank(inner)) {
			return values; // "{}" is a valid (all-default) sparse record
		}

		for (String pair : splitCommaSeparated(inner)) {
			if (pair.isEmpty()) {
				continue;
			}
			int space = indexOfWhitespace(pair);
			if (space < 0) {
				addWarning(lineNumber, "malformed sparse entry '" + pair + "' ignored");
				continue;
			}
			int index;
			try {
				index = Integer.parseInt(pair.substring(0, space));
			} catch (NumberFormatException e) {
				addWarning(lineNumber, "invalid attribute index in sparse entry '" + pair + "'");
				continue;
			}
			if (index < 0 || index >= numAttributes) {
				addWarning(lineNumber, "attribute index " + index + " out of range in sparse record");
				continue;
			}
			values.set(index, pair.substring(space + 1).trim());
		}
		return values;
	}

	// =====================================================================
	// Tokenizing helpers
	// =====================================================================

	/**
	 * Split a comma-separated list of possibly quoted values (as used in data lines
	 * and nominal declarations). Quotes are removed and escape sequences are
	 * resolved. Whitespace around unquoted values is trimmed.
	 * 
	 * @param text the text to split
	 * @return the list of values (never empty)
	 */
	static List<String> splitCommaSeparated(String text) {
		List<String> values = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int n = text.length();
		char quote = 0; // active quote character, 0 when outside quotes
		int quotedEnd = -1; // length of 'current' when the last closing quote was seen

		for (int i = 0; i < n; i++) {
			char c = text.charAt(i);
			if (quote != 0) {
				if (c == '\\' && i + 1 < n) {
					current.append(unescape(text.charAt(++i)));
				} else if (c == quote) {
					quote = 0;
					quotedEnd = current.length();
				} else {
					current.append(c);
				}
			} else if (c == '\'' || c == '"') {
				quote = c;
				if (isBlank(current)) {
					current.setLength(0); // drop whitespace preceding the opening quote
				}
			} else if (c == ',') {
				values.add(finishToken(current, quotedEnd));
				current.setLength(0);
				quotedEnd = -1;
			} else {
				current.append(c);
			}
		}
		values.add(finishToken(current, quotedEnd));
		return values;
	}

	/** Finalize a token: trim it unless it was quoted (then only trim what followed the quote). */
	private static String finishToken(StringBuilder current, int quotedEnd) {
		if (quotedEnd < 0) {
			return current.toString().trim();
		}
		return current.substring(0, quotedEnd) + current.substring(quotedEnd).trim();
	}

	/**
	 * Read a single token (quoted or delimited by whitespace) starting at
	 * {@code start}, appending its unquoted content to {@code out}.
	 * 
	 * @return the index just after the token
	 */
	private static int readToken(String s, int start, StringBuilder out) {
		int n = s.length();
		int i = start;
		char first = s.charAt(i);
		if (first == '\'' || first == '"') {
			i++;
			while (i < n) {
				char c = s.charAt(i);
				if (c == '\\' && i + 1 < n) {
					out.append(unescape(s.charAt(i + 1)));
					i += 2;
				} else if (c == first) {
					return i + 1;
				} else {
					out.append(c);
					i++;
				}
			}
			return n; // unterminated quote: take the rest of the line
		}
		while (i < n && !Character.isWhitespace(s.charAt(i))) {
			out.append(s.charAt(i));
			i++;
		}
		return i;
	}

	/** Return the first (possibly quoted) token of a string, or "" if none. */
	private static String firstToken(String s) {
		String trimmed = s.trim();
		if (trimmed.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		readToken(trimmed, 0, out);
		return out.toString();
	}

	/** Find the index of the '}' closing the '{' at position 0, ignoring quoted text. */
	private static int findClosingBrace(String s) {
		char quote = 0;
		for (int i = 1; i < s.length(); i++) {
			char c = s.charAt(i);
			if (quote != 0) {
				if (c == '\\') {
					i++;
				} else if (c == quote) {
					quote = 0;
				}
			} else if (c == '\'' || c == '"') {
				quote = c;
			} else if (c == '}') {
				return i;
			}
		}
		return -1;
	}

	/** Resolve the character following a backslash in a quoted string. */
	private static char unescape(char c) {
		switch (c) {
		case 'n':
			return '\n';
		case 't':
			return '\t';
		case 'r':
			return '\r';
		default:
			return c; // \' \" \\ \% and anything else
		}
	}

	private static boolean startsWithKeyword(String lower, String keyword) {
		return lower.startsWith(keyword)
				&& (lower.length() == keyword.length() || Character.isWhitespace(lower.charAt(keyword.length())));
	}

	private static boolean isWeightToken(String token) {
		return token.length() >= 2 && token.charAt(0) == '{' && token.charAt(token.length() - 1) == '}';
	}

	private static int indexOfWhitespace(String s) {
		for (int i = 0; i < s.length(); i++) {
			if (Character.isWhitespace(s.charAt(i))) {
				return i;
			}
		}
		return -1;
	}

	private static boolean isBlank(CharSequence s) {
		for (int i = 0; i < s.length(); i++) {
			if (!Character.isWhitespace(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static String abbreviate(String s) {
		return s.length() > 60 ? s.substring(0, 57) + "..." : s;
	}

	private void addWarning(int lineNumber, String message) {
		warningCount++;
		if (warnings.size() < MAX_STORED_WARNINGS) {
			warnings.add("Line " + lineNumber + ": " + message);
		}
	}

	// =====================================================================
	// Statistics
	// =====================================================================

	/**
	 * Compute per-attribute statistics after loading.
	 */
	private void computeStatistics() {
		int n = attributes.size();
		distinctValueCounts = new int[n];
		missingValueCounts = new int[n];
		numericColumns = new boolean[n];
		totalMissingValues = 0;

		Set<String> distinct = new HashSet<>();
		for (int i = 0; i < n; i++) {
			distinct.clear();
			int missing = 0;
			boolean numeric = attributes.get(i).isNumeric();
			for (List<String> record : records) {
				String value = record.get(i);
				if (isMissing(value)) {
					missing++;
				} else if (distinct.add(value) && numeric && !isNumber(value)) {
					// only validate each distinct value once
					numeric = false;
				}
			}
			distinctValueCounts[i] = distinct.size();
			missingValueCounts[i] = missing;
			numericColumns[i] = numeric;
			totalMissingValues += missing;
		}
	}

	private static boolean isNumber(String value) {
		try {
			Double.parseDouble(value);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	// =====================================================================
	// Public accessors
	// =====================================================================

	/** @return true if the given cell value denotes a missing value */
	public static boolean isMissing(String value) {
		return MISSING_VALUE.equals(value);
	}

	/** @return the relation name, or an empty string if none was declared */
	public String getRelationName() {
		return relationName;
	}

	/** @return the attributes (read-only) */
	public List<ARFFAttribute> getAttributes() {
		return Collections.unmodifiableList(attributes);
	}

	/**
	 * @param index the attribute index
	 * @return the attribute at that index
	 */
	public ARFFAttribute getAttribute(int index) {
		return attributes.get(index);
	}

	/** @return the attribute names (read-only) */
	public List<String> getAttributeNames() {
		return Collections.unmodifiableList(attributeNames);
	}

	/** @return the number of attributes */
	public int getAttributeCount() {
		return attributes.size();
	}

	/**
	 * @return the records (read-only). Every record has exactly
	 *         {@link #getAttributeCount()} values; missing values are
	 *         {@link #MISSING_VALUE}.
	 */
	public List<List<String>> getRecords() {
		return Collections.unmodifiableList(records);
	}

	/** @return the number of records */
	public int size() {
		return records.size();
	}

	/**
	 * @param attributeIndex the attribute index
	 * @return the number of distinct non-missing values of that attribute
	 */
	public int getDistinctValueCount(int attributeIndex) {
		return distinctValueCounts[attributeIndex];
	}

	/**
	 * @param attributeIndex the attribute index
	 * @return the number of missing values of that attribute
	 */
	public int getMissingValueCount(int attributeIndex) {
		return missingValueCounts[attributeIndex];
	}

	/** @return the total number of missing values in the database */
	public int getTotalMissingValueCount() {
		return totalMissingValues;
	}

	/**
	 * @param attributeIndex the attribute index
	 * @return true if the attribute is declared numeric and all of its non-missing
	 *         values can be parsed as numbers (so it is safe to sort numerically)
	 */
	public boolean isNumericColumn(int attributeIndex) {
		return numericColumns[attributeIndex];
	}

	/**
	 * @param type an attribute type
	 * @return the number of attributes of that type
	 */
	public int countAttributesOfType(AttributeType type) {
		int count = 0;
		for (ARFFAttribute attribute : attributes) {
			if (attribute.getType() == type) {
				count++;
			}
		}
		return count;
	}

	/** @return the stored warning messages (at most {@value #MAX_STORED_WARNINGS}) */
	public List<String> getWarnings() {
		return Collections.unmodifiableList(warnings);
	}

	/** @return the total number of warnings produced while loading */
	public int getWarningCount() {
		return warningCount;
	}
}