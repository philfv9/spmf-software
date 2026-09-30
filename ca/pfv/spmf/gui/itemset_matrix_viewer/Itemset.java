package ca.pfv.spmf.gui.itemset_matrix_viewer;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a single itemset (a set of items) together with optional named
 * measures such as support, confidence, etc.
 * <p>
 * Instances use identity semantics for {@code equals}/{@code hashCode} on
 * purpose: two different lines of a file may describe the same items and must
 * still be distinguishable as two rows.
 * <p>
 * The item set view and parsed numeric measure values are cached lazily so
 * that repeated lookups (filtering, sorting, painting) are cheap.
 *
 * @author Philippe Fournier-Viger, 2024
 */
public class Itemset {

    /** The items in this itemset (in display order) */
    private final List<String> items;

    /** Optional named measures (e.g. "SUP" → "4") */
    private final Map<String, String> measures;

    /** Lazily built unmodifiable set view of the items */
    private Set<String> itemSetView;

    /** Lazily built cache of parsed numeric measures (a null value = not numeric) */
    private Map<String, Double> numericMeasures;

    /**
     * Creates an itemset with the given items and no measures.
     *
     * @param items the items
     */
    public Itemset(List<String> items) {
        this(items, null);
    }

    /**
     * Creates an itemset with the given items and measures.
     *
     * @param items    the items
     * @param measures the named measures (may be null)
     */
    public Itemset(List<String> items, Map<String, String> measures) {
        this.items = (items != null) ? new ArrayList<>(items) : new ArrayList<>();
        this.measures = (measures != null) ? new LinkedHashMap<>(measures) : new LinkedHashMap<>();
    }

    /**
     * Returns the items in this itemset.
     *
     * @return an unmodifiable view of the item list
     */
    public List<String> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * Returns the number of items.
     *
     * @return the number of items
     */
    public int size() {
        return items.size();
    }

    /**
     * Returns the items as a set (cached).
     *
     * @return an unmodifiable set of the items
     */
    public Set<String> getItemSet() {
        Set<String> s = itemSetView;
        if (s == null) {
            s = Collections.unmodifiableSet(new HashSet<>(items));
            itemSetView = s;
        }
        return s;
    }

    /**
     * Checks whether this itemset contains the given item.
     *
     * @param item the item
     * @return true if present
     */
    public boolean containsItem(String item) {
        return getItemSet().contains(item);
    }

    /**
     * Checks whether this itemset is a proper subset of another itemset.
     *
     * @param other the other itemset
     * @return true if every item of this itemset is in {@code other} and
     *         {@code other} is strictly larger
     */
    public boolean isProperSubsetOf(Itemset other) {
        if (other == null) {
            return false;
        }
        Set<String> mine = getItemSet();
        Set<String> theirs = other.getItemSet();
        if (mine.size() >= theirs.size()) {
            return false;
        }
        for (String item : mine) {
            if (!theirs.contains(item)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the named measures for this itemset.
     *
     * @return an unmodifiable view of the measures map
     */
    public Map<String, String> getMeasures() {
        return Collections.unmodifiableMap(measures);
    }

    /**
     * Returns the raw string value of a measure.
     *
     * @param name the measure name
     * @return the value, or null if absent
     */
    public String getMeasure(String name) {
        return measures.get(name);
    }

    /**
     * Returns the numeric value of a measure, parsed once and cached.
     *
     * @param name the measure name
     * @return the value as a Double, or null if absent or not numeric
     */
    public Double getNumericMeasure(String name) {
        if (numericMeasures == null) {
            numericMeasures = new HashMap<>();
        }
        if (numericMeasures.containsKey(name)) {
            return numericMeasures.get(name);
        }
        Double parsed = parseDouble(measures.get(name));
        numericMeasures.put(name, parsed);
        return parsed;
    }

    private static Double parseDouble(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Adds or replaces a named measure.
     *
     * @param name  the measure name
     * @param value the measure value
     */
    public void putMeasure(String name, String value) {
        measures.put(name, value);
        if (numericMeasures != null) {
            numericMeasures.remove(name);
        }
    }

    /**
     * Returns a human-readable label of the items, e.g. {@code "1, 2, 3"}.
     *
     * @return the label
     */
    public String getLabel() {
        return String.join(", ", items);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        sb.append(getLabel()).append("}");
        if (!measures.isEmpty()) {
            sb.append(" ").append(measures);
        }
        return sb.toString();
    }
}