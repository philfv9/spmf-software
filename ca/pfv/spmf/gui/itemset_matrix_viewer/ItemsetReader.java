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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads itemsets from a text file in SPMF output format.
 * <p>
 * Handles the full SPMF format including:
 * <ul>
 *   <li>Metadata lines starting with {@code @} (skipped, except {@code @ITEM=})</li>
 *   <li>Comment lines starting with {@code #} or {@code %} (skipped)</li>
 *   <li>Item-name mappings: {@code @ITEM=id=name}</li>
 *   <li>Multiple measures per itemset: {@code item1 item2 #SUP: 4 #CONF: 0.8}</li>
 * </ul>
 * <p>
 * Items within each itemset are sorted (numerically if all items are integers,
 * otherwise lexicographically) for consistent display.
 *
 * @author Philippe Fournier-Viger, 2024
 */
public class ItemsetReader {

    /** The file path to read from */
    private final String filePath;

    /** The parsed itemsets */
    private final List<Itemset> itemsets = new ArrayList<>();

    /** The set of all measure names discovered while reading (preserves insertion order) */
    private final Set<String> availableMeasures = new LinkedHashSet<>();

    /** Item ID-to-name mapping parsed from {@code @ITEM=id=name} lines */
    private final Map<String, String> itemMapping = new HashMap<>();

    /** The available sorting orders, built after reading */
    private final List<String> sortingOrders = new ArrayList<>();

    /**
     * Creates a reader for the given file.
     *
     * @param filePath path to the itemset file
     * @throws IllegalArgumentException if filePath is null or empty
     */
    public ItemsetReader(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("filePath must not be null or empty");
        }
        this.filePath = filePath;
    }

    /**
     * Reads and returns all itemsets from the file.
     *
     * @return the list of itemsets
     * @throws IOException if the file cannot be read
     */
    public List<Itemset> readItemsets() throws IOException {
        itemsets.clear();
        availableMeasures.clear();
        itemMapping.clear();
        sortingOrders.clear();

        File file = new File(filePath);

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                if (line.startsWith("#") || line.startsWith("%")) continue;

                if (line.startsWith("@ITEM=")) {
                    String remainder = line.substring(6);
                    String[] parts = remainder.split("=", 2);
                    if (parts.length == 2) {
                        itemMapping.put(parts[0].trim(), parts[1].trim());
                    }
                    continue;
                }

                if (line.startsWith("@")) continue;

                Itemset itemset = parseLine(line);
                if (itemset != null && !itemset.getItems().isEmpty()) {
                    itemsets.add(itemset);
                }
            }
        }

        buildSortingOrders();
        return itemsets;
    }

    /**
     * Parses a single data line into an Itemset.
     *
     * @param line the line to parse
     * @return the parsed itemset, or null if the line contains no items
     */
    private Itemset parseLine(String line) {
        String[] sides = line.split(" #");

        String itemsPart = sides[0].trim();
        if (itemsPart.isEmpty()) return null;

        List<String> items = new ArrayList<>();
        for (String token : itemsPart.split("\\s+")) {
            token = token.trim();
            if (!token.isEmpty()) {
                items.add(itemMapping.getOrDefault(token, token));
            }
        }

        sortItems(items);

        Map<String, String> measures = new LinkedHashMap<>();
        for (int i = 1; i < sides.length; i++) {
            String measurePart = sides[i].trim();
            int colonIndex = measurePart.indexOf(':');
            if (colonIndex > 0) {
                String name = measurePart.substring(0, colonIndex).trim();
                String value = measurePart.substring(colonIndex + 1).trim();
                measures.put(name, value);
                availableMeasures.add(name);
            }
        }

        return new Itemset(items, measures);
    }

    /**
     * Sorts a list of item names in place.
     *
     * @param items the list of item names to sort
     */
    private void sortItems(List<String> items) {
        if (items.size() <= 1) return;

        boolean allNumeric = true;
        for (String item : items) {
            try {
                Integer.parseInt(item);
            } catch (NumberFormatException e) {
                allNumeric = false;
                break;
            }
        }

        if (allNumeric) {
            items.sort(Comparator.comparingInt(Integer::parseInt));
        } else {
            items.sort(Comparator.comparing((String s) -> s.toLowerCase())
                    .thenComparing(Comparator.naturalOrder()));
        }
    }

    /** Builds the list of available sorting orders */
    private void buildSortingOrders() {
        for (String measure : availableMeasures) {
            sortingOrders.add(measure + " (asc)");
            sortingOrders.add(measure + " (desc)");
        }
        sortingOrders.add("Size (asc)");
        sortingOrders.add("Size (desc)");
        sortingOrders.add("Lexicographical (asc)");
        sortingOrders.add("Lexicographical (desc)");
    }

    /**
     * Sorts the itemsets in place according to the given sorting order string.
     *
     * @param sortingOrder the sorting order string
     */
    public void sortItemsets(String sortingOrder) {
        if (sortingOrder == null) return;

        if ("Size (asc)".equals(sortingOrder)) {
            itemsets.sort(Comparator.comparingInt(r -> r.getItems().size()));
        } else if ("Size (desc)".equals(sortingOrder)) {
            itemsets.sort(Comparator.comparingInt((Itemset r) -> r.getItems().size()).reversed());
        } else if ("Lexicographical (asc)".equals(sortingOrder)) {
            itemsets.sort(Comparator.comparing(r -> String.join(" ", r.getItems())));
        } else if ("Lexicographical (desc)".equals(sortingOrder)) {
            itemsets.sort(Comparator.comparing((Itemset r) -> String.join(" ", r.getItems())).reversed());
        } else if (sortingOrder.endsWith("(asc)")) {
            String measure = sortingOrder.substring(0, sortingOrder.length() - 6).trim();
            sortItemsetsByMeasure(measure, true);
        } else if (sortingOrder.endsWith("(desc)")) {
            String measure = sortingOrder.substring(0, sortingOrder.length() - 7).trim();
            sortItemsetsByMeasure(measure, false);
        }
    }

    private void sortItemsetsByMeasure(String measure, boolean ascending) {
        itemsets.sort((a, b) -> {
            String sa = a.getMeasures().getOrDefault(measure, "");
            String sb = b.getMeasures().getOrDefault(measure, "");
            try {
                double va = Double.parseDouble(sa);
                double vb = Double.parseDouble(sb);
                return ascending ? Double.compare(va, vb) : Double.compare(vb, va);
            } catch (NumberFormatException e) {
                int cmp = sa.compareTo(sb);
                return ascending ? cmp : -cmp;
            }
        });
    }

    // ---------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------

    /** Returns the itemsets that were read. */
    public List<Itemset> getItemsets() { return itemsets; }

    /** Returns the set of all measure names discovered during reading. */
    public Set<String> getAvailableMeasures() { return availableMeasures; }

    /** Returns the item ID-to-name mapping parsed from {@code @ITEM=} lines. */
    public Map<String, String> getItemMapping() { return itemMapping; }

    /** Returns the list of available sorting orders, built after reading. */
    public List<String> getSortingOrders() { return sortingOrders; }
}