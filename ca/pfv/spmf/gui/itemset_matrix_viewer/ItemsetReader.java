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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
 * otherwise case-insensitively) for consistent display. Files are read as
 * UTF-8 (a leading byte-order mark is ignored).
 * <p>
 * This class also provides the sorting logic shared by the viewer
 * ({@link #sort(List, String)}).
 *
 * @author Philippe Fournier-Viger, 2024
 */
public class ItemsetReader {

    // ---------------------------------------------------------------
    // Sorting order names
    // ---------------------------------------------------------------

    public static final String SORT_SIZE_ASC = "Size (asc)";
    public static final String SORT_SIZE_DESC = "Size (desc)";
    public static final String SORT_LEX_ASC = "Lexicographical (asc)";
    public static final String SORT_LEX_DESC = "Lexicographical (desc)";

    private static final String SUFFIX_ASC = " (asc)";
    private static final String SUFFIX_DESC = " (desc)";

    private static final List<String> DEFAULT_SORTING_ORDERS = Collections.unmodifiableList(
            Arrays.asList(SORT_SIZE_ASC, SORT_SIZE_DESC, SORT_LEX_ASC, SORT_LEX_DESC));

    // ---------------------------------------------------------------
    // Fields
    // ---------------------------------------------------------------

    /** The file path to read from */
    private final String filePath;

    /** The parsed itemsets */
    private final List<Itemset> itemsets = new ArrayList<>();

    /** The set of all measure names discovered while reading (insertion order) */
    private final Set<String> availableMeasures = new LinkedHashSet<>();

    /** Item ID-to-name mapping parsed from {@code @ITEM=id=name} lines */
    private final Map<String, String> itemMapping = new HashMap<>();

    /** The available sorting orders, built after reading */
    private final List<String> sortingOrders = new ArrayList<>();

    /** Number of non-empty, non-comment lines that yielded no itemset */
    private int ignoredLineCount;

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
        ignoredLineCount = 0;

        File file = new File(filePath);
        if (!file.isFile()) {
            throw new IOException("File not found: " + filePath);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false;
                    if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
                        line = line.substring(1); // strip UTF-8 BOM
                    }
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.startsWith("#") || trimmed.startsWith("%")) continue;

                if (trimmed.startsWith("@ITEM=")) {
                    String remainder = trimmed.substring(6);
                    String[] parts = remainder.split("=", 2);
                    if (parts.length == 2) {
                        itemMapping.put(parts[0].trim(), parts[1].trim());
                    }
                    continue;
                }

                if (trimmed.startsWith("@")) continue;

                Itemset itemset = parseLine(trimmed);
                if (itemset != null && !itemset.getItems().isEmpty()) {
                    itemsets.add(itemset);
                } else {
                    ignoredLineCount++;
                }
            }
        }

        buildSortingOrders();
        return itemsets;
    }

    /**
     * Parses a single data line into an Itemset.
     *
     * @param line the (trimmed) line to parse
     * @return the parsed itemset, or null if the line contains no items
     */
    private Itemset parseLine(String line) {
        String[] sides = line.split(" #");

        String itemsPart = sides[0].trim();
        if (itemsPart.isEmpty()) return null;

        List<String> items = new ArrayList<>();
        for (String token : itemsPart.split("\\s+")) {
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
     * Sorts a list of item names in place (numerically when possible).
     *
     * @param items the list of item names to sort
     */
    private void sortItems(List<String> items) {
        if (items.size() <= 1) return;

        Map<String, Long> numeric = new HashMap<>();
        for (String item : items) {
            try {
                numeric.put(item, Long.parseLong(item));
            } catch (NumberFormatException e) {
                numeric = null;
                break;
            }
        }

        if (numeric != null) {
            final Map<String, Long> num = numeric;
            items.sort(Comparator.comparing(num::get));
        } else {
            items.sort(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()));
        }
    }

    /** Builds the list of available sorting orders */
    private void buildSortingOrders() {
        for (String measure : availableMeasures) {
            sortingOrders.add(measure + SUFFIX_ASC);
            sortingOrders.add(measure + SUFFIX_DESC);
        }
        sortingOrders.addAll(DEFAULT_SORTING_ORDERS);
    }

    // ---------------------------------------------------------------
    // Sorting (shared with the viewer)
    // ---------------------------------------------------------------

    /**
     * Sorts the itemsets read by this reader in place.
     *
     * @param sortingOrder the sorting order string
     */
    public void sortItemsets(String sortingOrder) {
        sort(itemsets, sortingOrder);
    }

    /**
     * Sorts a list of itemsets in place according to a sorting order string
     * such as {@code "Size (asc)"}, {@code "Lexicographical (desc)"} or
     * {@code "SUP (desc)"}.
     *
     * @param list         the list to sort
     * @param sortingOrder the sorting order string (unknown orders are ignored)
     */
    public static void sort(List<Itemset> list, String sortingOrder) {
        if (list == null || list.size() < 2 || sortingOrder == null) return;
        Comparator<Itemset> cmp = comparatorFor(sortingOrder);
        if (cmp != null) {
            list.sort(cmp);
        }
    }

    /**
     * Returns the comparator corresponding to a sorting order string.
     *
     * @param sortingOrder the sorting order string
     * @return the comparator, or null if the order is unknown
     */
    public static Comparator<Itemset> comparatorFor(String sortingOrder) {
        if (SORT_SIZE_ASC.equals(sortingOrder)) return BY_SIZE;
        if (SORT_SIZE_DESC.equals(sortingOrder)) return BY_SIZE.reversed();
        if (SORT_LEX_ASC.equals(sortingOrder)) return BY_LEX;
        if (SORT_LEX_DESC.equals(sortingOrder)) return BY_LEX.reversed();
        if (sortingOrder.endsWith(SUFFIX_ASC)) {
            return byMeasure(sortingOrder.substring(0,
                    sortingOrder.length() - SUFFIX_ASC.length()).trim(), true);
        }
        if (sortingOrder.endsWith(SUFFIX_DESC)) {
            return byMeasure(sortingOrder.substring(0,
                    sortingOrder.length() - SUFFIX_DESC.length()).trim(), false);
        }
        return null;
    }

    private static final Comparator<Itemset> BY_SIZE = Comparator.comparingInt(Itemset::size);

    private static final Comparator<Itemset> BY_LEX = (a, b) -> {
        List<String> x = a.getItems();
        List<String> y = b.getItems();
        int n = Math.min(x.size(), y.size());
        for (int i = 0; i < n; i++) {
            int c = x.get(i).compareTo(y.get(i));
            if (c != 0) return c;
        }
        return Integer.compare(x.size(), y.size());
    };

    /**
     * Comparator on a measure. Numeric values are compared numerically;
     * non-numeric values are compared as strings after all numeric values;
     * itemsets without the measure always come last, regardless of direction.
     */
    private static Comparator<Itemset> byMeasure(String measure, boolean ascending) {
        return (a, b) -> {
            String sa = a.getMeasure(measure);
            String sb = b.getMeasure(measure);
            if (sa == null && sb == null) return 0;
            if (sa == null) return 1;
            if (sb == null) return -1;
            Double va = a.getNumericMeasure(measure);
            Double vb = b.getNumericMeasure(measure);
            int cmp;
            if (va != null && vb != null) {
                cmp = Double.compare(va, vb);
            } else if (va != null) {
                return -1;
            } else if (vb != null) {
                return 1;
            } else {
                cmp = sa.compareTo(sb);
            }
            return ascending ? cmp : -cmp;
        };
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

    /** Returns the default sorting orders (without measure-based orders). */
    public static List<String> getDefaultSortingOrders() { return DEFAULT_SORTING_ORDERS; }

    /** Returns the number of data lines that could not be parsed into an itemset. */
    public int getIgnoredLineCount() { return ignoredLineCount; }
}