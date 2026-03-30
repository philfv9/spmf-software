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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single itemset (a set of items) together with optional
 * named measures such as support, confidence, etc.
 *
 * @author Philippe Fournier-Viger, 2024
 */
public class Itemset {

    /** The items in this itemset */
    private final List<String> items;

    /** Optional named measures (e.g. "support" → "0.6") */
    private final Map<String, String> measures;

    /**
     * Creates an itemset with the given items and no measures.
     *
     * @param items the items
     */
    public Itemset(List<String> items) {
        this.items = (items != null)
                ? new ArrayList<>(items)
                : new ArrayList<>();
        this.measures = new LinkedHashMap<>();
    }

    /**
     * Creates an itemset with the given items and measures.
     *
     * @param items    the items
     * @param measures the named measures
     */
    public Itemset(List<String> items, Map<String, String> measures) {
        this.items = (items != null)
                ? new ArrayList<>(items)
                : new ArrayList<>();
        this.measures = (measures != null)
                ? new LinkedHashMap<>(measures)
                : new LinkedHashMap<>();
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
     * Returns the named measures for this itemset.
     *
     * @return an unmodifiable view of the measures map
     */
    public Map<String, String> getMeasures() {
        return Collections.unmodifiableMap(measures);
    }

    /**
     * Adds or replaces a named measure.
     *
     * @param name  the measure name
     * @param value the measure value
     */
    public void putMeasure(String name, String value) {
        measures.put(name, value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        sb.append(String.join(", ", items));
        sb.append("}");
        if (!measures.isEmpty()) {
            sb.append(" ").append(measures);
        }
        return sb.toString();
    }
}