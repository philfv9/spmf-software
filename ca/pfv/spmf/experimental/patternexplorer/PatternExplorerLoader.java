package ca.pfv.spmf.experimental.patternexplorer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.gui.visual_pattern_viewer.PatternType;
import ca.pfv.spmf.gui.visual_pattern_viewer.itemsets.Itemset;
import ca.pfv.spmf.gui.visual_pattern_viewer.itemsets.ItemsetsReader;
import ca.pfv.spmf.gui.visual_pattern_viewer.itemsets.UncertainItemsetsReader;

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
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
/**
 * Loads SPMF pattern files into a {@link PatternCollection}. Itemset files are
 * read with the readers of the Visual Pattern Viewer. Other pattern types
 * (rules, sequential patterns...) are read with a generic parser that keeps
 * the set of items as the pattern and stores the original structure in a
 * {@value #STRUCTURE_ATTRIBUTE} attribute.
 * 
 * @author Philippe Fournier-Viger
 */
public final class PatternExplorerLoader {

	/** Attribute used to keep the original textual structure of a pattern */
	public static final String STRUCTURE_ATTRIBUTE = "STRUCTURE";

	/** Tokens that are structure markers, not items */
	private static final Set<String> IGNORED_TOKENS = Set.of("==>", "-->", "=>", "->", "|", "<", ">", "(", ")");

	private PatternExplorerLoader() {
	}

	/**
	 * Load a pattern file.
	 * 
	 * @param filePath the file
	 * @param type     the pattern type (null = itemsets)
	 * @return the collection
	 * @throws IOException if the file cannot be read
	 */
	public static PatternCollection load(String filePath, PatternType type) throws IOException {
		List<ExplorerPattern> list;
		if (type == null || type == PatternType.ITEMSETS || type == PatternType.UNCERTAIN_ITEMSETS) {
			ItemsetsReader reader = type == PatternType.UNCERTAIN_ITEMSETS ? new UncertainItemsetsReader(filePath)
					: new ItemsetsReader(filePath);
			list = new ArrayList<>(reader.getPatterns().size());
			for (Itemset itemset : reader.getPatterns()) {
				List<String> items = new ArrayList<>(itemset.getItems().size());
				for (String item : itemset.getItems()) {
					items.add(item.trim());
				}
				list.add(new ExplorerPattern(items, itemset.getMeasures()));
			}
		} else {
			list = readGenericFile(filePath);
		}
		String description = new File(filePath).getName() + (type == null ? "" : " (" + type + ")");
		return new PatternCollection(list, description);
	}

	/**
	 * Generic parser: each line is "structure #NAME: value #NAME: value".
	 */
	static List<ExplorerPattern> readGenericFile(String filePath) throws IOException {
		List<ExplorerPattern> list = new ArrayList<>();
		Map<String, String> itemMapping = new HashMap<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isEmpty() || line.startsWith("#") || line.startsWith("%")) {
					continue;
				}
				if (line.startsWith("@ITEM=")) {
					String[] parts = line.substring(6).split("=", 2);
					if (parts.length == 2) {
						itemMapping.put(parts[0], parts[1]);
					}
					continue;
				}
				if (line.startsWith("@")) {
					continue;
				}
				String[] sides = line.split(" #");
				String structure = sides[0].trim();
				List<String> items = new ArrayList<>();
				StringBuilder mapped = new StringBuilder();
				for (String token : structure.split("\\s+")) {
					if (token.isEmpty()) {
						continue;
					}
					String name = itemMapping.getOrDefault(token, token);
					if (mapped.length() > 0) {
						mapped.append(' ');
					}
					mapped.append(name);
					if (IGNORED_TOKENS.contains(token) || token.matches("-\\d+")) {
						continue;
					}
					items.add(name);
				}
				Map<String, String> attributes = new LinkedHashMap<>();
				attributes.put(STRUCTURE_ATTRIBUTE, mapped.toString());
				for (int i = 1; i < sides.length; i++) {
					String token = sides[i];
					int colon = token.indexOf(':');
					if (colon > 0) {
						attributes.put(token.substring(0, colon).trim(), token.substring(colon + 1).trim());
					}
				}
				list.add(new ExplorerPattern(items, attributes));
			}
		}
		return list;
	}
}