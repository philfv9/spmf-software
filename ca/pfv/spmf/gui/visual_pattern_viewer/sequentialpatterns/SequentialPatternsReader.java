package ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
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
 * Class to read sequential patterns from an SPMF-formatted file. Each line
 * represents a frequent sequential pattern: - Items are positive integers
 * separated by spaces. - "-1" denotes the end of an itemset within the
 * sequence. - After the sequence, "#SUP:" is followed by an integer support
 * count.
 * @author Philippe Fournier-Viger
 */
public class SequentialPatternsReader extends PatternsReader {

    /** The parsed sequential patterns **/
    private final List<SequentialPattern> patterns = new ArrayList<>();

    /**
     * Constructor
     * 
     * @param filePath input file path
     * @throws IOException if file I/O fails
     */
    public SequentialPatternsReader(String filePath) throws IOException {
        readFile(filePath);
    }

    /**
     * Reads the SPMF file and parses sequences and support values.
     * 
     * @param filePath input file path
     * @throws IOException if file I/O fails
     */
    protected void readFileHelper(String filePath) throws IOException {
        
        File file = new File(filePath);
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Skip empty or comment lines
                if (line.isEmpty() || line.startsWith("#")  || line.startsWith("%")) {
                    continue;
                }
                // Split sequence and support
                // Split right-hand side into consequents + measures
                int posMeasures = line.indexOf('#');
                String sequencePart = line.substring(0, posMeasures-1);
                String measuresPart = line.substring(posMeasures-1);
//                System.out.println(line);
//                System.out.println(sequencePart + "-");
//                System.out.println(measurePart+ "-");
                


                List<List<String>> itemsets = new ArrayList<>();
                parsePattern(sequencePart, itemsets);
                
                // Map measures
//                Map<String, String> measures = new LinkedHashMap<>();
//                measures.put(SUPPORT_LABEL, supportValue);
                // Parse measures with their corresponding values
                String[] sides = measuresPart.split(" #");
                Map<String, String> measures = new LinkedHashMap<>();
                for (int i = 1; i < sides.length; i++) {
                    String token = sides[i];
                    int colon = token.indexOf(':');
                    if (colon > 0) {
                        String name = token.substring(0, colon);
                        String value = token.substring(colon + 1);
                        measures.put(name, value);
                        availableMeasures.add(name);
                    }
                }

                // Add to list
                patterns.add(new SequentialPattern(itemsets, measures));
            }
        }
        
        // Add each interestingness measure with ascending
        // and descending as a possible order for sorting the rules.
        for (String measure : availableMeasures) {
            orders.add(measure + " (asc)");
            orders.add(measure + " (desc)");
        }
        // Define sorting orders for number of items and number of itemsets
        orders.add("Number of items (asc)");
        orders.add("Number of items (desc)");
        orders.add("Number of itemsets (asc)");
        orders.add("Number of itemsets (desc)");
        // Define lexicographical sorting orders
        orders.add("Lexicographical (asc)");
        orders.add("Lexicographical (desc)");
    }

  

    /**
     * Parse the tokens that form a sequential pattern
     * @param sequencePart the sequencePart
     * @param itemsets a list of itemsets for storing the pattern after it has been parsed.
     */
	protected void parsePattern(String sequencePart, List<List<String>> itemsets) {
        // Parse sequence into itemsets
        String[] tokens = sequencePart.split(" ");
		List<String> currentSet = new ArrayList<>();
		for (String token : tokens) {
		    if (token.equals("-1")) {
		        if (!currentSet.isEmpty()) {
		            itemsets.add(new ArrayList<>(currentSet));
		            currentSet.clear();
		        }
		    } else {
		        currentSet.add(token);
		    }
		}
	}

    /**
     * @return list of parsed sequential patterns
     */
    public List<SequentialPattern> getPatterns() {
        return patterns;
    }

    /**
     * Sorts patterns according to the given order.
     * 
     * @param sortingOrder the sorting order selected by the user
     */
    public void sortPatterns(String sortingOrder) {
        if ("Number of items (asc)".equals(sortingOrder)) {
            // Ascending on total number of items in pattern
            patterns.sort(Comparator.comparingInt(
                    p -> p.getItemsets().stream().mapToInt(List::size).sum()));
        } else if ("Number of items (desc)".equals(sortingOrder)) {
            // Descending on total number of items in pattern
            patterns.sort(Comparator.comparingInt(
                    (SequentialPattern p) -> p.getItemsets().stream().mapToInt(List::size).sum()).reversed());
        } else if ("Number of itemsets (asc)".equals(sortingOrder)) {
            // Ascending on number of itemsets in pattern
            patterns.sort(Comparator.comparingInt(
                    p -> p.getItemsets().size()));
        } else if ("Number of itemsets (desc)".equals(sortingOrder)) {
            // Descending on number of itemsets in pattern
            patterns.sort(Comparator.comparingInt(
                    (SequentialPattern p) -> p.getItemsets().size()).reversed());
        } else if ("Lexicographical (asc)".equals(sortingOrder)) {
            // Ascending lexicographical order of sequence representation
            patterns.sort(Comparator.comparing(
                    p -> p.getItemsets().toString()));
        } else if ("Lexicographical (desc)".equals(sortingOrder)) {
            // Descending lexicographical order of sequence representation
            patterns.sort(Comparator.comparing(
                    (SequentialPattern p) -> p.getItemsets().toString()).reversed());
        } else if (sortingOrder.endsWith("(asc)")) {
            String measure = sortingOrder.substring(0, sortingOrder.length() - 6);
            sortPatternsByMeasure(measure, true);
        } else if (sortingOrder.endsWith("(desc)")) {
            // If descending sort
            String measure = sortingOrder.substring(0, sortingOrder.length() - 7);
            sortPatternsByMeasure(measure, false);
        } 
    }
    
    /**
     * Sorts patterns numerically by the given measure, then rebuilds the display.
     *
     * @param measure key of the measure to sort by
     * @param ascen   ding true for ascending order; false for descending
     */
    protected void sortPatternsByMeasure(String measure, boolean ascending) {
        // Sort rules
        patterns.sort((r1, r2) -> {
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
        for (SequentialPattern pattern : patterns) {
            Map<String, String> measuresMap = pattern.getMeasures();
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
