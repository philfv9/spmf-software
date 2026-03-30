package ca.pfv.spmf.gui.visual_pattern_viewer.rules;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.gui.visual_pattern_viewer.PatternsReader;

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
 */
/**
 * Class to read rules from a file in SPMF format with their measures
 * @author Philippe Fournier-Viger
 */
public class RulesReader extends PatternsReader {

	/** The parsed rules **/
	private final List<Rule> rules = new ArrayList<>();

	/** Sorting order by lexicographical order of antecedents */
	private static final String ANTECEDENT_LABEL = "Antecedent (A-Z)";

	/** Sorting order by lexicographical order of consequents */
	private static final String CONSEQUENT_LABEL = "Consequent (A-Z)";

	/**
	 * Constructor
	 * 
	 * @param filePath input file path
	 * @throws IOException
	 */
	public RulesReader(String filePath) throws IOException {
		readFile(filePath);
	}

	/**
	 * Reads a file containing association rules in SPMF format
	 *
	 * @param filePath input file path
	 * @throws IOException if file I/O fails
	 */
	protected void readFileHelper(String filePath) throws IOException {

		File file = new File(filePath);

		/** Item ID-to-name mapping (from extended SPMF files) **/
		Map<String, String> itemMapping = new HashMap<>();

		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			// For each line
			while ((line = reader.readLine()) != null) {

				// Skip empty or comment/metadata lines
				if (line.isEmpty() || line.startsWith("#") || line.startsWith("%")) {
					continue;
				}
				// Parse extended mapping lines: "@ITEM=id=name"
				if (line.startsWith("@ITEM=")) {
					String[] parts = line.substring(6).split("=", 2);
					if (parts.length == 2) {
						itemMapping.put(parts[0], parts[1]);
					}
					continue;
				}
				// Skip other SPMF metadata (e.g. "@..."), preserve only mapping
				if (line.startsWith("@")) {
					continue;
				}
				// Only process rule lines
				if (!line.contains("==>")) {
					continue;
				}

				String[] sides = line.split(" ==> ");
				if (sides.length < 2) {
					continue;
				}
				// Read the antecedent items of the rule.
				List<String> antecedentItems = new ArrayList<>();
				for (String token : sides[0].split(" ")) {
					// If the item has a name, replace it by its name
					antecedentItems.add(itemMapping.getOrDefault(token, token));
				}

				// Split right-hand side into consequents + measures
				String[] rhs = sides[1].split(" #");

				List<String> consequentItems = new ArrayList<>();
				
				// Read the consequent items of the rule.
				for (String token : rhs[0].split(" ")) {
					// If the item has a name, replace it by its name
					consequentItems.add(itemMapping.getOrDefault(token, token));
				}

				// Parse measures with their corresponding values
				Map<String, String> measures = new LinkedHashMap<>();
				for (int i = 1; i < rhs.length; i++) {
					String token = rhs[i];
					int colon = token.indexOf(':');
					if (colon > 0) {
						String name = token.substring(0, colon);
						String value = token.substring(colon + 2);
						measures.put(name, value);
						availableMeasures.add(name);
					}
				}
				// Add the rule to the set of rules
				getPatterns().add(new Rule(antecedentItems, consequentItems,
						measures));

			}
		}

		// Add each interestingness measure with ascending
		// and descending as a possible order for sorting the rules.
		for (String measure : availableMeasures) {
			orders.add(measure + " (asc)");
			orders.add(measure + " (desc)");
		}
		// Finally, add the order of antecedent and consequent
		orders.add(ANTECEDENT_LABEL);
		orders.add(CONSEQUENT_LABEL);
	}

	/**
	 * Get the list of rules
	 * 
	 * @return the rules
	 */
	protected List<Rule> getPatterns() {
		return rules;
	}

	/**
	 * Sorts rules numerically by the given measure, then rebuilds the display.
	 *
	 * @param measure key of the measure to sort by
	 * @param ascen   ding true for ascending order; false for descending
	 */
    protected void sortPatternsByMeasure(String measure, boolean ascending) {
        // Sort rules
        rules.sort((r1, r2) -> {
            String s1 = r1.getMeasures().get(measure);
            String s2 = r2.getMeasures().get(measure);
            if (s1 == null) s1 = "";
            if (s2 == null) s2 = "";
            // Try numeric comparison first
            try {
                double v1 = Double.parseDouble(s1);
                double v2 = Double.parseDouble(s2);
                return ascending ? Double.compare(v1, v2) : Double.compare(v2, v1);
            } catch (NumberFormatException e) {
                // Fallback to lexicographical comparison for non-numeric values
                int cmp = s1.compareTo(s2);
                return ascending ? cmp : -cmp;
            }
        });
    }

	/**
	 * Sort the rules based on a sorting order selected by the user
	 * 
	 * @param sortingOrder the sorting order selected by the user
	 */
	public void sortPatterns(String sortingOrder) {
		// If ascending sort
		if (sortingOrder.endsWith("(asc)")) {
			String measure = sortingOrder.substring(0, sortingOrder.length() - 6);
			sortPatternsByMeasure(measure, true);
		} else if (sortingOrder.endsWith("(desc)")) {
			// If descending sort
			String measure = sortingOrder.substring(0, sortingOrder.length() - 7);
			sortPatternsByMeasure(measure, false);
		} else if (ANTECEDENT_LABEL.equals(sortingOrder)) {
			// If sort by antecedent lexicographical order
			rules.sort(Comparator.comparing(r -> String.join(" ", r.getAntecedent())));
		} else if (CONSEQUENT_LABEL.equals(sortingOrder)) {
			// If sort by consequent lexicographical order
			rules.sort(Comparator.comparing(r -> String.join(" ", r.getConsequent())));
		}
	}
	
    /**
     * Iterates over all itemsets and, for each measure in 'availableMeasures',
     * parses its value as a double (if possible) and updates min/max maps.
     */
    protected void computeMinMaxForMeasures(Set<String> availableMeasures) {
        // 1) Initialize min/max maps
        for (String measure : availableMeasures) {
            minMeasureValuesAdjusted.put(measure, Double.POSITIVE_INFINITY);
            maxMeasureValuesAdjusted.put(measure, Double.NEGATIVE_INFINITY);
        }
        // 2) Scan through all itemsets
        for (Rule rule : rules) {
            Map<String, String> measuresMap = rule.getMeasures();
            for (String measure : availableMeasures) {
                String valueStr = measuresMap.get(measure);
                if (valueStr == null) {
                    continue; // no value for this measure in that itemset
                }
                try {
                    double v = Double.parseDouble(valueStr);
                    // update min
                    double currentMin = minMeasureValuesAdjusted.get(measure);
                    if (v < currentMin) {
                        minMeasureValuesAdjusted.put(measure, v);
                    }
                    // update max
                    double currentMax = maxMeasureValuesAdjusted.get(measure);
                    if (v > currentMax) {
                        maxMeasureValuesAdjusted.put(measure, v);
                    }
                } catch (NumberFormatException ex) {
                    // skip non‐numeric values
                }
            }
        }
        // 3) Replace +∞/−∞ by null if never updated
        for (String measure : availableMeasures) {
            if (minMeasureValuesAdjusted.get(measure).isInfinite()) {
                minMeasureValuesAdjusted.put(measure, null);
            }
            if (maxMeasureValuesAdjusted.get(measure).isInfinite()) {
                maxMeasureValuesAdjusted.put(measure, null);
            }
        }
    }
}
