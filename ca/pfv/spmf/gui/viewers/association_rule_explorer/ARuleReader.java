package ca.pfv.spmf.gui.viewers.association_rule_explorer;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Copyright (c) 2008-2026 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license.
 */

/**
 * Reads association rules in the SPMF output format.
 * <p>
 * Supported line format: {@code [NOT] item1 item2 ==> [NOT] item3 #SUP: 3 #CONF: 0,75 #LIFT: 1.5 ...}
 * <br>Items may be integers or names. Decimal values may use "," or "." as
 * separator. Metadata lines starting with "@" are skipped, except
 * {@code @ITEM=id=name} lines which are used to replace item ids by names.
 * Non-numeric measure values are ignored (this tool only plots numbers).
 *
 * @author Philippe Fournier-Viger
 */
public class ARuleReader {

	/** the rules that were read */
	private final List<ARule> rules = new ArrayList<>();
	/** the names of the measures, in order of first appearance */
	private final List<String> measureNames = new ArrayList<>();
	/** the @FILETYPE metadata value, if any */
	private String fileType = "";
	/** number of lines that had to be skipped because of a syntax problem */
	private int skippedLines = 0;
	/** number of duplicate measure occurrences that were ignored */
	private int duplicatedMeasures = 0;

	/** the keyword used for negation */
	private static final String NOT_KEYWORD = "NOT";

	/**
	 * Read a file of rules.
	 * 
	 * @param filePath the path of the file
	 * @throws IOException if the file cannot be read
	 */
	public ARuleReader(String filePath) throws IOException {
		Map<String, String> itemMapping = new HashMap<>();
		Set<String> seenMeasures = new LinkedHashSet<>();

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				// skip empty lines and comments
				if (line.isEmpty() || line.startsWith("#") || line.startsWith("%")) {
					continue;
				}
				// metadata
				if (line.startsWith("@")) {
					if (line.startsWith("@ITEM=")) {
						String[] parts = line.substring(6).split("=", 2);
						if (parts.length == 2) {
							itemMapping.put(parts[0], parts[1]);
						}
					} else if (line.startsWith("@FILETYPE=")) {
						fileType = line.substring(10).replace("\"", "").trim();
					}
					continue;
				}
				int arrow = line.indexOf("==>");
				if (arrow < 0) {
					skippedLines++;
					continue;
				}

				String left = line.substring(0, arrow).trim();
				String right = line.substring(arrow + 3).trim();

				// separate consequent from measures
				int hash = right.indexOf('#');
				String consequentSide = hash < 0 ? right : right.substring(0, hash).trim();
				String measurePart = hash < 0 ? "" : right.substring(hash + 1);

				// negation
				boolean antecedentNegated = false;
				if (left.equals(NOT_KEYWORD) || left.startsWith(NOT_KEYWORD + " ")) {
					antecedentNegated = true;
					left = left.substring(NOT_KEYWORD.length()).trim();
				}
				boolean consequentNegated = false;
				if (consequentSide.equals(NOT_KEYWORD) || consequentSide.startsWith(NOT_KEYWORD + " ")) {
					consequentNegated = true;
					consequentSide = consequentSide.substring(NOT_KEYWORD.length()).trim();
				}

				List<String> antecedent = parseItems(left, itemMapping);
				List<String> consequent = parseItems(consequentSide, itemMapping);
				if (antecedent.isEmpty() && consequent.isEmpty()) {
					skippedLines++;
					continue;
				}

				// measures
				Map<String, Double> measures = new LinkedHashMap<>();
				if (!measurePart.isEmpty()) {
					for (String token : measurePart.split("#")) {
						int colon = token.indexOf(':');
						if (colon <= 0) {
							continue;
						}
						String name = token.substring(0, colon).trim();
						String value = token.substring(colon + 1).trim();
						try {
							double parsed = parseNumber(value);
							// a measure name should appear only once per rule;
							// if it does, keep the FIRST occurrence (the file is
							// probably a corrupt merge) but warn the user.
							if (measures.containsKey(name)) {
								duplicatedMeasures++;
								System.out.println("ARuleReader: ignoring duplicate measure #" + name
										+ " on line: " + line);
								continue;
							}
							measures.put(name, parsed);
							seenMeasures.add(name);
						} catch (NumberFormatException ex) {
							// non-numeric measure: ignored by this tool
						}
					}
				}
				rules.add(new ARule(antecedent, antecedentNegated, consequent, consequentNegated, measures));
			}
		}
		measureNames.addAll(seenMeasures);
		if (!rules.isEmpty()) {
			System.out.println("ARuleReader: loaded " + rules.size() + " rule(s) in "
					+ filePath.substring(filePath.lastIndexOf(java.io.File.separatorChar) + 1));
		}
	}

	/**
	 * Split a side of a rule into items and apply the id -> name mapping.
	 */
	private static List<String> parseItems(String text, Map<String, String> mapping) {
		List<String> items = new ArrayList<>();
		if (text.isEmpty()) {
			return items;
		}
		for (String token : text.split("\\s+")) {
			if (!token.isEmpty()) {
				items.add(mapping.getOrDefault(token, token));
			}
		}
		return items;
	}

	/**
	 * Parse a number written with "." or "," as decimal separator. Also
	 * accepts "Infinity", "-Infinity", "NaN" and percentages ("75%").
	 */
	static double parseNumber(String text) {
		String s = text.trim().replace(',', '.');
		if (s.endsWith("%")) {
			return Double.parseDouble(s.substring(0, s.length() - 1).trim()) / 100.0;
		}
		return Double.parseDouble(s);
	}

	public List<ARule> getRules() {
		return rules;
	}

	public List<String> getMeasureNames() {
		return measureNames;
	}

	public String getFileType() {
		return fileType;
	}

	/** @return how many lines had to be skipped (not valid rule lines) */
	public int getSkippedLines() {
		return skippedLines;
	}

	/** @return how many duplicated measure tags were ignored */
	public int getDuplicatedMeasures() {
		return duplicatedMeasures;
	}
}
