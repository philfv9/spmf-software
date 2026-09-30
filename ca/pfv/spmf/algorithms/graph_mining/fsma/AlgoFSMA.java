package ca.pfv.spmf.algorithms.graph_mining.fsma;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
/* This file is copyright (c) 2018 by Chao Cheng, Philippe Fournier-Viger
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
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
* 
* Do not remove copyright and license.
*/
/**
 * Implementation of the FSMA algorithm (Fast Frequent Subgraph Mining Algorithm)
 * as described in:
 *   Jia Wu, Ling Chen, "A Fast Frequent Subgraph Mining Algorithm", ICYCS 2008.
 *
 * <p>Canonical labeling uses color refinement (1-WL) followed by
 * individualization-refinement backtracking on any remaining ambiguity,
 * which avoids the factorial-time cost of exhaustively permuting all
 * same-label groups. A cache maps raw pattern representations to their
 * already-computed canonical results to avoid redundant work.
 *
 * <p>Several representations are chosen specifically to minimize allocation
 * and autoboxing overhead in the hot candidate-generation path:
 * <ul>
 *   <li>Pattern identity ({@link PatternKey}) is a small object holding
 *       primitive {@code int[]} data with a precomputed hash code, instead
 *       of a formatted {@code String}.</li>
 *   <li>Vertex labels ({@link IntList}) are stored in a growable primitive
 *       {@code int[]} instead of {@code List<Integer>}, avoiding boxing on
 *       every candidate extension.</li>
 *   <li>Pattern edges ({@code EdgeList}, see {@code Pattern.java}) are
 *       stored as three parallel growable primitive {@code int[]} arrays
 *       instead of a {@code List<int[]>} of individually-allocated
 *       triples, since a new edge is appended for essentially every
 *       candidate generated during mining.</li>
 *   <li>The adjacency structure used during canonicalization, and the
 *       adjacency structure of each {@link InputGraph}, are flat
 *       CSR-style ({@code int[]}-based) representations instead of a
 *       {@code List<List<int[]>>}, so that walking a vertex's neighbours in
 *       the hot candidate-extension loop touches only primitive arrays.</li>
 *   <li>Per-graph embedding bookkeeping ({@link PatternInfo#embeddingsByGraph})
 *       uses a small open-addressing {@link IntObjectMap} keyed directly by
 *       the primitive graph id, instead of {@code Map<Integer, ...>}, to
 *       avoid boxing the graph id on every embedding registered.</li>
 * </ul>
 *
 * <p>Mining follows Algorithm 2 (FSMA): frequent 1-vertex subgraphs are
 * found first, infrequent vertices and edge types are pruned, then subgraphs
 * are grown one edge at a time until no more frequent patterns can be found.
 *
 * <p>This class holds the mining driver logic. The input-graph representation
 * lives in {@link InputGraph} (and its helper {@link IntList}), while the
 * pattern representation and canonical-labeling procedure live in
 * {@link Pattern} (and its key type {@link PatternKey}).
 *
 * @author Chen Hui
 */
public class AlgoFSMA {

    /** Timestamp (ms) when the algorithm started. */
    private long startTimestamp;
    /** Timestamp (ms) when the algorithm finished. */
    private long endTimestamp;
    /** Number of frequent subgraphs found and written to the output file. */
    private int patternCount;

    /** The graph database being mined. */
    private List<InputGraph> graphs;
    /** Minimum support threshold as an absolute count of graphs. */
    private int minSupportAbsolute;
    /** Writer used to write frequent subgraphs to the output file. */
    private BufferedWriter writer;
    /** If true, the ids of the graphs containing each pattern are written to the output file. */
    private boolean outputGraphIds;

    /** Cache mapping raw pattern representations to their canonical results. */
    private Map<PatternKey, Pattern.CanonicalResult> canonCache;

    /** Precompiled whitespace splitter used when parsing input file lines. */
    private static final java.util.regex.Pattern WHITESPACE = java.util.regex.Pattern.compile("\\s+");

    /**
     * Run the FSMA algorithm on the given input graph database. The ids of the
     * graphs containing each pattern are written to the output file (equivalent
     * to calling {@link #runAlgorithm(String, String, double, boolean)} with
     * {@code true}).
     *
     * @param input      path of the input file (graph database in SPMF format)
     * @param output     path of the output file (frequent subgraphs)
     * @param minSupport minimum support; values {@code <= 1.0} are treated as
     *                   relative (fraction of graphs), values {@code > 1} as absolute counts
     * @throws IOException if an I/O error occurs
     */
    public void runAlgorithm(String input, String output, double minSupport)
            throws IOException {
        runAlgorithm(input, output, minSupport, false);
    }

    /**
     * Run the FSMA algorithm on the given input graph database.
     *
     * @param input          path of the input file (graph database in SPMF format)
     * @param output         path of the output file (frequent subgraphs)
     * @param minSupport     minimum support; values {@code <= 1.0} are treated as
     *                       relative (fraction of graphs), values {@code > 1} as absolute counts
     * @param outputGraphIds if true, the ids of the graphs containing each pattern
     *                       are written to the output file (as an "x" line)
     * @throws IOException if an I/O error occurs
     */
    public void runAlgorithm(String input, String output, double minSupport,
                              boolean outputGraphIds)
            throws IOException {

        startTimestamp = System.currentTimeMillis();
        patternCount   = 0;
        canonCache     = new HashMap<>();
        this.outputGraphIds = outputGraphIds;

        graphs = readGraphs(input);

        // resolve relative or absolute support to an absolute count
        if (minSupport <= 1.0) {
            minSupportAbsolute = (int) Math.ceil(minSupport * graphs.size());
        } else {
            minSupportAbsolute = (int) minSupport;
        }
        if (minSupportAbsolute < 1) {
            minSupportAbsolute = 1;
        }

        writer = new BufferedWriter(new FileWriter(output));
        writer.write("@FILETYPE=\"Frequent subgraphs\"");
        writer.append(System.lineSeparator());
        writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
        writer.append(System.lineSeparator());
        
        try {
            // find frequent 1-vertex subgraphs (isolated vertices are excluded)
            Map<PatternKey, PatternInfo> level0Candidates = new HashMap<>();

            for (InputGraph g : graphs) {
                for (int v = 0; v < g.vertexLabels.size(); v++) {
                    if (g.degree(v) == 0) {
                        continue;
                    }
                    int label = g.vertexLabels.get(v);
                    Pattern p = new Pattern();
                    p.vertexLabels.add(label);
                    Pattern.CanonicalResult cr = canonicalizeCached(p);
                    PatternInfo info = level0Candidates.computeIfAbsent(
                            cr.key, k -> new PatternInfo(cr.canonicalPattern));
                    info.addEmbedding(g.id, new int[]{v});
                }
            }

            Map<PatternKey, PatternInfo> level0Frequent = filterFrequent(level0Candidates);

            // prune vertices with infrequent labels from all graphs
            Set<Integer> frequentVertexLabels = new HashSet<>();
            for (PatternInfo pi : level0Frequent.values()) {
                frequentVertexLabels.add(pi.pattern.vertexLabels.get(0));
            }
            for (InputGraph g : graphs) {
                g.pruneVertices(frequentVertexLabels);
            }

            outputFrequent(level0Frequent);

            // grow subgraphs one edge at a time until no new frequent patterns appear
            Map<PatternKey, PatternInfo> currentLevel = level0Frequent;
            boolean edgePruningDone = false;

            while (!currentLevel.isEmpty()) {
                Map<PatternKey, PatternInfo> candidates = generateNextLevel(currentLevel);
                Map<PatternKey, PatternInfo> frequent   = filterFrequent(candidates);

                // prune globally infrequent edge types after the first extension
                if (!edgePruningDone) {
                    Set<String> frequentEdgeTypes = new HashSet<>();
                    for (PatternInfo pi : frequent.values()) {
                        if (pi.pattern.edges.size() == 1
                                && pi.pattern.vertexLabels.size() == 2) {
                            frequentEdgeTypes.add(edgeTypeKey(pi.pattern));
                        }
                    }
                    for (InputGraph g : graphs) {
                        g.pruneEdgesByType(frequentEdgeTypes);
                    }
                    edgePruningDone = true;
                }

                outputFrequent(frequent);
                currentLevel = frequent;
            }
        } finally {
            writer.close();
        }

        endTimestamp = System.currentTimeMillis();
    }

    /**
     * Print statistics about the last algorithm execution to standard output.
     */
    public void printStats() {
        System.out.println("=============  FSMA v.2.67 - STATS =============");
        System.out.println(" Number of graphs in database        : "
                + (graphs == null ? 0 : graphs.size()));
        System.out.println(" Minimum support (absolute count)    : "
                + minSupportAbsolute);
        System.out.println(" Number of frequent subgraphs found  : "
                + patternCount);
        System.out.println(" Total time                          : "
                + (endTimestamp - startTimestamp) + " ms");
        System.out.println("===========================================");
    }

    /**
     * Canonicalize a pattern, reusing {@link #canonCache} when the same raw pattern was seen before.
     *
     * @param p the pattern to canonicalize
     * @return the canonical result for {@code p}
     */
    private Pattern.CanonicalResult canonicalizeCached(Pattern p) {
        PatternKey rawKey = p.rawKey();
        Pattern.CanonicalResult cr = canonCache.get(rawKey);
        if (cr == null) {
            cr = p.canonicalize();
            canonCache.put(rawKey, cr);
        }
        return cr;
    }

    /**
     * Generate all (k+1)-edge candidate subgraphs by extending every stored
     * embedding of every k-edge frequent pattern by exactly one edge.
     *
     * @param currentLevel the k-edge frequent patterns to extend
     * @return map of generated candidate patterns keyed by canonical form
     */
    private Map<PatternKey, PatternInfo> generateNextLevel(
            Map<PatternKey, PatternInfo> currentLevel) {

        Map<PatternKey, PatternInfo> candidates = new HashMap<>();

        // extend every embedding of every current frequent pattern by one edge;
        // iterating via IntObjectMap#forEach avoids boxing the graph id that a
        // Map<Integer, ...> entry-set iteration would otherwise require here,
        // in what is the hottest loop of the whole mining process
        for (PatternInfo baseInfo : currentLevel.values()) {
            Pattern basePattern = baseInfo.pattern;
            baseInfo.embeddingsByGraph.forEach((graphId, embSet) -> {
                InputGraph g = graphs.get(graphId);
                for (EmbeddingKey ek : embSet) {
                    extendEmbedding(basePattern, ek.mapping, graphId, g, candidates);
                }
            });
        }
        return candidates;
    }

    /**
     * Check whether {@code val} occurs among the first {@code len} entries of {@code arr}.
     * Avoids a {@code HashSet<Integer>} allocation and autoboxing in this hot path.
     *
     * @param arr the array to scan
     * @param len the number of valid entries in {@code arr}
     * @param val the value to search for
     * @return {@code true} if {@code val} is present among the first {@code len} entries
     */
    private static boolean containsValue(int[] arr, int len, int val) {
        for (int i = 0; i < len; i++) {
            if (arr[i] == val) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extend a single embedding by one edge, either by attaching a new vertex
     * (Type A) or by closing a cycle between two already-mapped vertices (Type B).
     *
     * @param basePattern the pattern being extended
     * @param mapping     the current embedding (pattern vertex index to host vertex index)
     * @param graphId     the id of the host graph
     * @param g           the host graph
     * @param candidates  the map into which new candidates are registered
     */
    private void extendEmbedding(Pattern basePattern, int[] mapping,
                                  int graphId, InputGraph g,
                                  Map<PatternKey, PatternInfo> candidates) {
        int n = mapping.length;

        // Type A: attach a brand-new neighbouring vertex via a new edge.
        // Walks the host graph's flat CSR adjacency directly (no ArrayList
        // iterator, no boxed int[] pair objects) since this is the hottest
        // loop in candidate generation.
        for (int i = 0; i < n; i++) {
            int gi    = mapping[i];
            int start = g.adjStart[gi];
            int end   = g.adjStart[gi + 1];
            for (int k = start; k < end; k++) {
                int gj        = g.adjNeighbor[k];
                int edgeLabel = g.adjLabel[k];
                if (!containsValue(mapping, n, gj)) {
                    Pattern newPattern = basePattern.copy();
                    newPattern.vertexLabels.add(g.vertexLabels.get(gj));
                    int newIdx = newPattern.vertexLabels.size() - 1;
                    newPattern.edges.add(i, newIdx, edgeLabel);
                    int[] newMapping = Arrays.copyOf(mapping, n + 1);
                    newMapping[n] = gj;
                    registerCandidate(newPattern, newMapping, graphId, candidates);
                }
            }
        }

        // Type B: close a cycle between two already-mapped vertices
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (basePattern.hasEdge(i, j)) {
                    continue;
                }
                int gi        = mapping[i];
                int gj        = mapping[j];
                int edgeLabel = g.getEdgeLabel(gi, gj);
                if (edgeLabel != InputGraph.NO_EDGE) {
                    Pattern newPattern = basePattern.copy();
                    newPattern.edges.add(i, j, edgeLabel);
                    registerCandidate(newPattern, mapping.clone(), graphId, candidates);
                }
            }
        }
    }

    /**
     * Canonicalize {@code pattern}, re-index its embedding to canonical order,
     * and register the result in {@code candidates}, merging with any existing
     * isomorphic candidate already stored under the same key.
     *
     * @param pattern    the candidate pattern to normalize and register
     * @param mapping    the embedding of the pattern in the host graph
     * @param graphId    the id of the host graph
     * @param candidates the map into which the candidate is registered
     */
    private void registerCandidate(Pattern pattern, int[] mapping,
                                    int graphId,
                                    Map<PatternKey, PatternInfo> candidates) {
        Pattern.CanonicalResult cr = canonicalizeCached(pattern);
        int   n            = pattern.vertexLabels.size();
        int[] canonMapping = new int[n];

        // re-index the host-graph vertex array to follow the canonical permutation
        for (int k = 0; k < n; k++) {
            canonMapping[k] = mapping[cr.perm[k]];
        }

        PatternInfo info = candidates.computeIfAbsent(
                cr.key, k -> new PatternInfo(cr.canonicalPattern));
        info.addEmbedding(graphId, canonMapping);
    }

    /**
     * Remove, in place, every entry whose support is below {@link #minSupportAbsolute}.
     * Filtering in place avoids an extra full-map copy at every mining level.
     *
     * @param candidates the candidate patterns to filter (mutated in place)
     * @return {@code candidates}, containing only the frequent entries
     */
    private Map<PatternKey, PatternInfo> filterFrequent(
            Map<PatternKey, PatternInfo> candidates) {
        candidates.values().removeIf(info -> info.getSupport() < minSupportAbsolute);
        return candidates;
    }

    /**
     * Build a symmetric edge-type key from two vertex labels and an edge label,
     * so that edges (A-B) and (B-A) produce the same key.
     *
     * <p>Package-visible (rather than private) because {@link InputGraph#pruneEdgesByType}
     * also needs to build the same kind of key when filtering its edges.
     *
     * @param label1    label of one endpoint
     * @param label2    label of the other endpoint
     * @param edgeLabel the edge label
     * @return a string uniquely identifying the edge type
     */
    static String edgeTypeKey(int label1, int label2, int edgeLabel) {
        int a = Math.min(label1, label2);
        int b = Math.max(label1, label2);
        return a + "_" + b + "_" + edgeLabel;
    }

    /**
     * Build a symmetric edge-type key for a 1-edge, 2-vertex pattern.
     *
     * @param p a 1-edge, 2-vertex pattern
     * @return a string uniquely identifying the edge type
     */
    private static String edgeTypeKey(Pattern p) {
        return edgeTypeKey(p.vertexLabels.get(0), p.vertexLabels.get(1),
                p.edges.label(0));
    }

    /**
     * Write all patterns in {@code frequent} to the output file in SPMF format,
     * ordered by edge count then lexicographically on vertex labels and edges.
     *
     * @param frequent the frequent patterns to write
     * @throws IOException if an I/O error occurs
     */
    private void outputFrequent(Map<PatternKey, PatternInfo> frequent)
            throws IOException {

        // sort patterns for deterministic output order (only done once per level, not hot)
        List<PatternInfo> list = new ArrayList<>(frequent.values());
        list.sort((a, b) -> a.pattern.toSortKey().compareTo(b.pattern.toSortKey()));

        for (PatternInfo info : list) {
            writer.write("t # " + patternCount + " * " + info.getSupport());
            writer.newLine();

            // write vertices
            for (int i = 0; i < info.pattern.vertexLabels.size(); i++) {
                writer.write("v " + i + " " + info.pattern.vertexLabels.get(i));
                writer.newLine();
            }

            // write edges (indexed access into the parallel-array EdgeList,
            // no int[] triple objects involved)
            int edgeCount = info.pattern.edges.size();
            for (int i = 0; i < edgeCount; i++) {
                writer.write("e " + info.pattern.edges.v1(i) + " "
                        + info.pattern.edges.v2(i) + " "
                        + info.pattern.edges.label(i));
                writer.newLine();
            }

            // If the user chose to output the graph ids where the frequent
            // subgraph appears, we output it. keysSorted() returns a
            // primitive int[], avoiding the boxed List<Integer> +
            // Collections.sort that would otherwise be used
            if (outputGraphIds) {
                int[] graphIds = info.embeddingsByGraph.keysSorted();
                StringBuilder sb = new StringBuilder("x");
                for (int gid : graphIds) {
                    sb.append(" ").append(gid);
                }
                writer.write(sb.toString());
                writer.newLine();
            }
            writer.newLine();

            patternCount++;
        }
    }

    /**
     * Read a graph database from the SPMF graph-mining text format, remapping
     * file vertex ids to contiguous 0-based indices internally.
     *
     * @param input path of the input file
     * @return the list of parsed graphs
     * @throws IOException if an I/O error occurs
     */
    private List<InputGraph> readGraphs(String input) throws IOException {

        List<InputGraph>      result  = new ArrayList<>();
        InputGraph            current = null;
        Map<Integer, Integer> idMap   = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(input))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (line.startsWith("t")) {
                    // commit the previous graph before starting a new one
                    if (current != null) {
                        current.buildAdjacency();
                        result.add(current);
                    }
                    current = new InputGraph();
                    current.id = result.size();
                    idMap = new HashMap<>();

                } else if (line.startsWith("v")) {
                    // parse vertex and remap its id to a 0-based index
                    String[] parts = WHITESPACE.split(line);
                    int rawId = Integer.parseInt(parts[1]);
                    int label = Integer.parseInt(parts[2]);
                    idMap.put(rawId, current.vertexLabels.size());
                    current.vertexLabels.add(label);

                } else if (line.startsWith("e")) {
                    // parse edge and translate endpoints via idMap
                    String[] parts = WHITESPACE.split(line);
                    int p     = Integer.parseInt(parts[1]);
                    int q     = Integer.parseInt(parts[2]);
                    int label = Integer.parseInt(parts[3]);
                    int v1    = idMap.get(p);
                    int v2    = idMap.get(q);
                    current.edges.add(new int[]{v1, v2, label});
                }
            }
        }

        // commit the last graph
        if (current != null) {
            current.buildAdjacency();
            result.add(current);
        }

        return result;
    }
}

/**
 * Bookkeeping for one canonical pattern: stores the pattern itself and its
 * distinct embeddings, grouped by graph id.
 */
class PatternInfo {

    /** The canonical pattern this entry describes. */
    Pattern pattern;
    /**
     * Distinct embeddings found for this pattern, keyed by graph id. Uses a
     * primitive-int-keyed {@link IntObjectMap} rather than
     * {@code Map<Integer, Set<EmbeddingKey>>} to avoid boxing the graph id
     * on every embedding registered, which happens extremely often during
     * candidate generation.
     */
    IntObjectMap<Set<EmbeddingKey>> embeddingsByGraph = new IntObjectMap<>();

    /**
     * Create a bookkeeping entry for the given canonical pattern.
     *
     * @param p the canonical pattern to track
     */
    PatternInfo(Pattern p) {
        this.pattern = p;
    }

    /**
     * Record an embedding for {@code graphId}; duplicates are silently
     * ignored since embeddings are stored in a set.
     *
     * @param graphId the id of the graph containing the embedding
     * @param mapping the embedding to add
     */
    void addEmbedding(int graphId, int[] mapping) {
        Set<EmbeddingKey> set = embeddingsByGraph.computeIfAbsent(graphId, HashSet::new);
        set.add(new EmbeddingKey(mapping));
    }

    /**
     * Return the number of distinct graphs that contain at least one embedding.
     *
     * @return the support of this pattern
     */
    int getSupport() {
        return embeddingsByGraph.size();
    }
}

/**
 * Lightweight hash/equals wrapper around an {@code int[]} embedding,
 * used to store and deduplicate embeddings in {@link PatternInfo#embeddingsByGraph}.
 */
final class EmbeddingKey {
    /** The embedding array being wrapped. */
    final int[] mapping;
    /** Pre-computed hash code of {@link #mapping}. */
    private final int hash;

    /**
     * Create an EmbeddingKey wrapping the given mapping array.
     *
     * @param mapping the embedding array to wrap
     */
    EmbeddingKey(int[] mapping) {
        this.mapping = mapping;
        this.hash    = Arrays.hashCode(mapping);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EmbeddingKey)) {
            return false;
        }
        return Arrays.equals(mapping, ((EmbeddingKey) o).mapping);
    }
}

/**
 * A minimal open-addressing hash map from primitive {@code int} keys to
 * arbitrary object values, used in place of {@code Map<Integer, V>} to
 * avoid boxing the key on every lookup/insertion. Graph ids are always
 * {@code >= 0}, so {@code -1} is used as the "empty slot" sentinel.
 *
 * <p>Only the small set of operations actually needed by
 * {@link PatternInfo#embeddingsByGraph} are implemented.
 *
 * @param <V> the value type
 */
final class IntObjectMap<V> {

    /** Sentinel marking an empty slot in {@link #keys}. */
    private static final int EMPTY_KEY = -1;

    /** Keys, one slot per bucket; {@link #EMPTY_KEY} marks an empty bucket. */
    private int[] keys;
    /** Values, parallel to {@link #keys}. */
    private Object[] values;
    /** Number of entries currently stored. */
    private int size;

    /** Create an empty map with a small default capacity. */
    IntObjectMap() {
        this(8);
    }

    /**
     * Create an empty map able to hold at least {@code initialCapacity}
     * entries before its first resize.
     *
     * @param initialCapacity the desired initial capacity
     */
    IntObjectMap(int initialCapacity) {
        int cap = 8;
        while (cap < initialCapacity) {
            cap <<= 1;
        }
        keys   = new int[cap];
        Arrays.fill(keys, EMPTY_KEY);
        values = new Object[cap];
    }

    /**
     * Return the number of entries stored.
     *
     * @return the current size
     */
    int size() {
        return size;
    }

    /**
     * Return the value mapped to {@code key}, or the value produced by
     * {@code supplier} (which is stored and returned) if absent.
     *
     * @param key      the key to look up
     * @param supplier produces the default value if {@code key} is absent
     * @return the existing or newly-inserted value for {@code key}
     */
    @SuppressWarnings("unchecked")
    V computeIfAbsent(int key, Supplier<V> supplier) {
        ensureCapacity();
        int mask = keys.length - 1;
        int idx  = (key * 0x9E3779B1) & mask;
        while (true) {
            int k = keys[idx];
            if (k == EMPTY_KEY) {
                keys[idx]   = key;
                V v         = supplier.get();
                values[idx] = v;
                size++;
                return v;
            }
            if (k == key) {
                return (V) values[idx];
            }
            idx = (idx + 1) & mask;
        }
    }

    /**
     * Grow the backing arrays (doubling capacity) once the load factor
     * would exceed 0.75 after the next insertion.
     */
    private void ensureCapacity() {
        if ((size + 1) * 4 >= keys.length * 3) {
            resize(keys.length * 2);
        }
    }

    /**
     * Rehash all entries into new backing arrays of the given capacity.
     *
     * @param newCapacity the new capacity (must be a power of two)
     */
    private void resize(int newCapacity) {
        int[]    oldKeys   = keys;
        Object[] oldValues = values;
        keys   = new int[newCapacity];
        Arrays.fill(keys, EMPTY_KEY);
        values = new Object[newCapacity];
        int mask = newCapacity - 1;
        for (int i = 0; i < oldKeys.length; i++) {
            int k = oldKeys[i];
            if (k != EMPTY_KEY) {
                int idx = (k * 0x9E3779B1) & mask;
                while (keys[idx] != EMPTY_KEY) {
                    idx = (idx + 1) & mask;
                }
                keys[idx]   = k;
                values[idx] = oldValues[i];
            }
        }
    }

    /**
     * Functional interface used by {@link #forEach}, taking a primitive
     * {@code int} key directly instead of a boxed {@code Integer}.
     *
     * @param <V> the value type
     */
    interface EntryConsumer<V> {
        /**
         * Process one entry.
         *
         * @param key   the primitive key
         * @param value the associated value
         */
        void accept(int key, V value);
    }

    /**
     * Invoke {@code consumer} once for every entry stored in this map.
     *
     * @param consumer the callback to invoke for each entry
     */
    @SuppressWarnings("unchecked")
    void forEach(EntryConsumer<V> consumer) {
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != EMPTY_KEY) {
                consumer.accept(keys[i], (V) values[i]);
            }
        }
    }

    /**
     * Return this map's keys in ascending sorted order, as a primitive
     * array. Used only for deterministic output ordering.
     *
     * @return the sorted keys
     */
    int[] keysSorted() {
        int[] result = new int[size];
        int   idx    = 0;
        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != EMPTY_KEY) {
                result[idx++] = keys[i];
            }
        }
        Arrays.sort(result);
        return result;
    }
}