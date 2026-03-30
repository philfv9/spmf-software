package ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import ca.pfv.spmf.gui.visual_pattern_viewer.Pattern;

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
 * Represents a single sequential pattern, which is a sequence of itemsets
 * (each itemset is a list of integer items), along with associated measures.
 * @author Philippe Fournier-Viger
 */
public class SequentialPattern implements Pattern {

    /**
     * Sequence of itemsets; each itemset is a list of items.
     */
    private final List<List<String>> itemsets;

    /**
     * Map of measure names to their string values (e.g., support, confidence).
     */
    private final Map<String, String> measures;
    
	/**
	 * Cached string representation
	 */
	private final String cachedToString;

    /**
     * Constructs a new SequentialPattern instance.
     *
     * @param itemsets the sequence of itemsets (cannot be null)
     * @param measures mapping of measure names to their values (cannot be null)
     * @throws NullPointerException if any argument is null
     */
    public SequentialPattern(List<List<String>> itemsets, Map<String, String> measures) {
        this.itemsets = Objects.requireNonNull(itemsets, "itemsets cannot be null");
        this.measures = Objects.requireNonNull(measures, "measures cannot be null");
        
        // Precompute the string
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < itemsets.size(); i++) {
            sb.append(itemsets.get(i));
            if (i < itemsets.size() - 1) {
                sb.append(" -> ");
            }
        }
        sb.append("]");
        cachedToString = sb.toString();
    }

    /**
     * @return the sequence of itemsets
     */
    public List<List<String>> getItemsets() {
        return itemsets;
    }

    /**
     * @return map of measure names to their values
     */
    public Map<String, String> getMeasures() {
        return measures;
    }

    /**
     * Builds a human-readable representation of the sequential pattern.
     * Example: [[1, 2] -> [3] -> [4, 5]]
     *
     * @return string representation of the pattern
     */
    @Override
    public String toString() {
    	return cachedToString;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SequentialPattern)) return false;
        SequentialPattern that = (SequentialPattern) o;
        return itemsets.equals(that.itemsets) && measures.equals(that.measures);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemsets, measures);
    }
}
