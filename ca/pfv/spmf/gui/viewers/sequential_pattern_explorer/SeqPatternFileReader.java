package ca.pfv.spmf.gui.viewers.sequential_pattern_explorer;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * Reader for sequential pattern files in the SPMF output format, e.g.
 * <pre>
 *   1 2 -1 3 -1 #SUP: 5
 *   1 -1 3 -1 -2 #SUP: 4 #CONF: 0.8 #SID: 0 3 7
 * </pre>
 * Any number of measures of the form <code>#NAME: value</code> is accepted; the
 * set of measure names is discovered while reading. Lines without any
 * <code>-1</code> separator are interpreted as one item per itemset.
 *
 * @author Philippe Fournier-Viger 2025
 */
public class SeqPatternFileReader {

	/** Result of reading a file. */
	public static class Result {
		public final List<SeqPattern> patterns;
		public final List<String> measureNames;

		Result(List<SeqPattern> patterns, List<String> measureNames) {
			this.patterns = patterns;
			this.measureNames = measureNames;
		}
	}

	/**
	 * Read a pattern file.
	 *
	 * @param filePath the path
	 * @return the patterns and the ordered list of measure names found
	 * @throws IOException if the file cannot be read
	 */
	public static Result read(String filePath) throws IOException {
		List<SeqPattern> patterns = new ArrayList<>();
		Set<String> measureNames = new LinkedHashSet<>();

		try (BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(filePath), StandardCharsets.UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				SeqPattern p = parseLine(line);
				if (p == null) continue;
				patterns.add(p);
				measureNames.addAll(p.getMeasures().keySet());
			}
		}
		return new Result(patterns, new ArrayList<>(measureNames));
	}

	/**
	 * Parse one line. Returns null for blank / comment lines.
	 */
	public static SeqPattern parseLine(String rawLine) {
		if (rawLine == null) return null;
		String line = rawLine.trim();
		if (line.isEmpty()) return null;
		char c = line.charAt(0);
		if (c == '#' || c == '@' || c == '%') return null; // comment / metadata / no items

		int hash = line.indexOf('#');
		String itemPart = hash >= 0 ? line.substring(0, hash).trim() : line;
		String measurePart = hash >= 0 ? line.substring(hash) : "";

		// ---- itemsets ----
		List<List<String>> itemsets = new ArrayList<>();
		if (!itemPart.isEmpty()) {
			String[] tokens = itemPart.split("\\s+");
			boolean standard = false;
			for (String t : tokens) if ("-1".equals(t)) { standard = true; break; }

			List<String> current = new ArrayList<>();
			for (String t : tokens) {
				if (t.isEmpty()) continue;
				if ("-2".equals(t)) break;
				if ("-1".equals(t)) {
					if (!current.isEmpty()) { itemsets.add(current); current = new ArrayList<>(); }
					continue;
				}
				if (standard) {
					current.add(t);
				} else {
					List<String> single = new ArrayList<>(1);
					single.add(t);
					itemsets.add(single);
				}
			}
			if (!current.isEmpty()) itemsets.add(current);
		}
		if (itemsets.isEmpty()) return null;

		// ---- measures ----
		Map<String, String> measures = new LinkedHashMap<>();
		if (!measurePart.isEmpty()) {
			String[] pieces = measurePart.split("#");
			for (String piece : pieces) {
				String s = piece.trim();
				if (s.isEmpty()) continue;
				int colon = s.indexOf(':');
				String name, value;
				if (colon >= 0) {
					name = s.substring(0, colon).trim();
					value = s.substring(colon + 1).trim();
				} else {
					name = s;
					value = "";
				}
				if (!name.isEmpty()) measures.put(name, value);
			}
		}
		return new SeqPattern(itemsets, measures, rawLine);
	}
}