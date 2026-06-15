package ca.pfv.spmf.gui.visual_pattern_viewer.subgraphs;

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
import ca.pfv.spmf.gui.visual_pattern_viewer.subgraphs.Subgraph.Edge;
import ca.pfv.spmf.gui.visual_pattern_viewer.subgraphs.Subgraph.Vertex;

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
 * Do not remove copyright and license information.
 */
/**
 * Reads subgraph patterns from a file in SPMF / gSpan format and exposes them
 * as a list of {@link Subgraph} objects together with their interestingness
 * measures.
 *
 * <p><b>File format</b><br>
 * Each graph block starts with a line of the form:
 * <pre>
 *   t # &lt;id&gt; * &lt;support&gt;
 * </pre>
 * followed by zero or more vertex lines:
 * <pre>
 *   v &lt;vertexId&gt; &lt;vertexLabel&gt;
 * </pre>
 * zero or more edge lines:
 * <pre>
 *   e &lt;srcId&gt; &lt;dstId&gt; &lt;edgeLabel&gt;
 * </pre>
 * and exactly one support / occurrence line:
 * <pre>
 *   x &lt;graphId1&gt; &lt;graphId2&gt; …
 * </pre>
 * The {@code x} line lists the identifiers of all graphs in the database that
 * contain the current subgraph. It is <em>not</em> a list of measure values –
 * the only interestingness measure is the support {@code Z} taken from the
 * {@code * Z} suffix of the {@code t} line.
 *
 * <p>Lines starting with {@code #}, {@code %} or {@code @} are treated as
 * comments / metadata and are skipped.
 *
 * @author Philippe Fournier-Viger
 */
public class SubgraphsReader extends PatternsReader {

    /** Name of the single interestingness measure provided by this format. */
    private static final String MEASURE_SUPPORT = "SUP";

    /** Parsed subgraph patterns. */
    private final List<Subgraph> subgraphs = new ArrayList<>();

    /**
     * Constructs a reader and immediately parses the given file.
     *
     * @param filePath path to the input file
     * @throws IOException if an I/O error occurs while reading the file
     */
    public SubgraphsReader(String filePath) throws IOException {
        readFile(filePath);
    }

    /**
     * Parses the subgraph pattern file at {@code filePath}.
     *
     * <p>Each graph block is delimited by a {@code t} line and terminated by an
     * {@code x} line. The support value is read from the {@code * Z} token on
     * the {@code t} line and stored as the single measure {@code "SUP"}. The
     * identifiers on the {@code x} line are stored as the list of containing
     * graph ids and are <em>not</em> treated as additional measures.
     *
     * @param filePath path to the input file
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void readFileHelper(String filePath) throws IOException {

        File file = new File(filePath);

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;

            // ---- State for the block currently being parsed ----
            String       currentId      = null;
            String       currentSupport = null;
            List<Vertex> currentVertices = null;
            List<Edge>   currentEdges   = null;

            while ((line = br.readLine()) != null) {
                line = line.trim();

                // Skip blank lines and comment / metadata lines
                if (line.isEmpty()
                        || line.startsWith("#")
                        || line.startsWith("%")
                        || line.startsWith("@")) {
                    continue;
                }

                // ---- New graph block ----
                if (line.startsWith("t")) {
                    // If a previous block was collected without an "x" line
                    // (should not happen in well-formed files, but handle it
                    // defensively by flushing with an empty containing-id list).
                    if (currentId != null) {
                        flushBlock(currentId, currentSupport,
                                currentVertices, currentEdges,
                                new ArrayList<>());
                    }

                    // Parse "t # <id>" or "t # <id> * <support>"
                    // Example: "t # 0 * 3"
                    String[] parts = line.split("\\s+");
                    currentId       = (parts.length >= 3) ? parts[2] : "?";
                    currentSupport  = (parts.length >= 5) ? parts[4] : null;
                    currentVertices = new ArrayList<>();
                    currentEdges    = new ArrayList<>();
                    continue;
                }

                // Lines before the first "t" – skip
                if (currentId == null) {
                    continue;
                }

                String[] items = line.split("\\s+");

                if (line.startsWith("v")) {
                    // Vertex line: "v <id> <label>"
                    if (items.length >= 3) {
                        int    vId    = Integer.parseInt(items[1]);
                        String vLabel = items[2];
                        currentVertices.add(new Vertex(vId, vLabel));
                    }

                } else if (line.startsWith("e")) {
                    // Edge line: "e <src> <dst> <label>"
                    if (items.length >= 4) {
                        int    src    = Integer.parseInt(items[1]);
                        int    dst    = Integer.parseInt(items[2]);
                        String eLabel = items[3];
                        currentEdges.add(new Edge(src, dst, eLabel));
                    }

                } else if (line.startsWith("x")) {
                    // Occurrence line: "x <graphId1> <graphId2> …"
                    // These are the ids of graphs that contain the subgraph –
                    // NOT additional measure values.
                    List<String> containingIds = new ArrayList<>();
                    for (int i = 1; i < items.length; i++) {
                        containingIds.add(items[i]);
                    }

                    // Flush the completed block
                    flushBlock(currentId, currentSupport,
                            currentVertices, currentEdges, containingIds);

                    // Reset state for the next block
                    currentId       = null;
                    currentSupport  = null;
                    currentVertices = null;
                    currentEdges    = null;
                }
            } // end while

            // Flush any trailing block that had no "x" line
            if (currentId != null) {
                flushBlock(currentId, currentSupport,
                        currentVertices, currentEdges, new ArrayList<>());
            }
        }

        // Register the single measure name "SUP" so sorting / filtering work
        if (!subgraphs.isEmpty()) {
            availableMeasures.add(MEASURE_SUPPORT);
        }

        // Build sorting-order labels
        for (String measure : availableMeasures) {
            orders.add(measure + " (asc)");
            orders.add(measure + " (desc)");
        }
        orders.add("Size (asc)");
        orders.add("Size (desc)");
        orders.add("Lexicographical (asc)");
        orders.add("Lexicographical (desc)");
    }

    /**
     * Creates a {@link Subgraph} from the accumulated block data and adds it to
     * {@link #subgraphs}. The support value (if present) is stored as the single
     * measure {@code "SUP"}.
     *
     * @param id             the graph identifier string
     * @param support        the support string parsed from the {@code t} line
     *                       (may be {@code null} if not present)
     * @param vertices       the accumulated vertex list
     * @param edges          the accumulated edge list
     * @param containingIds  the graph ids listed on the {@code x} line
     */
    private void flushBlock(String id, String support,
            List<Vertex> vertices, List<Edge> edges,
            List<String> containingIds) {

        Map<String, String> measures = new LinkedHashMap<>();
        if (support != null) {
            measures.put(MEASURE_SUPPORT, support);
        }

        subgraphs.add(new Subgraph(id, vertices, edges, containingIds, measures));
    }

    /**
     * Returns the list of parsed subgraph patterns.
     *
     * @return list of {@link Subgraph} instances (never null)
     */
    public List<Subgraph> getPatterns() {
        return subgraphs;
    }

    /**
     * Sorts the subgraph patterns numerically by the given measure.
     *
     * @param measure   the measure name to sort by (e.g. {@code "SUP"})
     * @param ascending {@code true} for ascending order; {@code false} for
     *                  descending
     */
    public void sortPatternsByMeasure(String measure, boolean ascending) {
        subgraphs.sort((a, b) -> {
            String s1 = a.getMeasures().get(measure);
            String s2 = b.getMeasures().get(measure);
            if (s1 == null) s1 = "";
            if (s2 == null) s2 = "";
            try {
                double v1 = Double.parseDouble(s1);
                double v2 = Double.parseDouble(s2);
                return ascending ? Double.compare(v1, v2) : Double.compare(v2, v1);
            } catch (NumberFormatException e) {
                int cmp = s1.compareTo(s2);
                return ascending ? cmp : -cmp;
            }
        });
    }

    /**
     * Sorts subgraph patterns according to the given sorting-order string (as
     * returned by {@link #getListOfSortingOrders()}).
     *
     * @param sortingOrder the sorting order chosen by the user
     */
    @Override
    public void sortPatterns(String sortingOrder) {
        if ("Size (asc)".equals(sortingOrder)) {
            subgraphs.sort(Comparator.comparingInt(Subgraph::size));
        } else if ("Size (desc)".equals(sortingOrder)) {
            subgraphs.sort(Comparator.comparingInt(Subgraph::size).reversed());
        } else if ("Lexicographical (asc)".equals(sortingOrder)) {
            subgraphs.sort(Comparator.comparing(Subgraph::toString));
        } else if ("Lexicographical (desc)".equals(sortingOrder)) {
            subgraphs.sort(Comparator.comparing(Subgraph::toString).reversed());
        } else if (sortingOrder.endsWith("(asc)")) {
            String measure = sortingOrder.substring(0, sortingOrder.length() - 6).trim();
            sortPatternsByMeasure(measure, true);
        } else if (sortingOrder.endsWith("(desc)")) {
            String measure = sortingOrder.substring(0, sortingOrder.length() - 7).trim();
            sortPatternsByMeasure(measure, false);
        }
    }

    /**
     * Iterates over all subgraph patterns and, for each measure in
     * {@code availableMeasures}, parses its value as a double (if possible) and
     * updates the internal min / max maps used for bar scaling.
     *
     * @param availableMeasures the set of measure names to process
     */
    @Override
    public void computeMinMaxForMeasures(Set<String> availableMeasures) {
        // Initialise
        for (String measure : availableMeasures) {
            minMeasureValuesAdjusted.put(measure, Double.POSITIVE_INFINITY);
            maxMeasureValuesAdjusted.put(measure, Double.NEGATIVE_INFINITY);
        }
        // Scan all subgraphs
        for (Subgraph subgraph : subgraphs) {
            for (String measure : availableMeasures) {
                String valueStr = subgraph.getMeasures().get(measure);
                if (valueStr == null) {
                    continue;
                }
                try {
                    double v = Double.parseDouble(valueStr);
                    if (v < minMeasureValuesAdjusted.get(measure)) {
                        minMeasureValuesAdjusted.put(measure, v);
                    }
                    if (v > maxMeasureValuesAdjusted.get(measure)) {
                        maxMeasureValuesAdjusted.put(measure, v);
                    }
                } catch (NumberFormatException ex) {
                    // skip non-numeric values
                }
            }
        }
        // Replace infinities with null where no numeric value was found
        for (String measure : availableMeasures) {
            if (Double.isInfinite(minMeasureValuesAdjusted.getOrDefault(measure, Double.POSITIVE_INFINITY))) {
                minMeasureValuesAdjusted.put(measure, null);
            }
            if (Double.isInfinite(maxMeasureValuesAdjusted.getOrDefault(measure, Double.NEGATIVE_INFINITY))) {
                maxMeasureValuesAdjusted.put(measure, null);
            }
        }
    }
}