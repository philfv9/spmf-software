package ca.pfv.spmf.algorithms.graph_mining.fsg;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * Implementation of the FSG algorithm for discovering frequent connected subgraphs
 * in a database of labeled graphs, based on Kuramochi &amp; Karypis, "Frequent Subgraph
 * Discovery", ICDM 2001.
 * <p>
 * The first reported level consists of frequent single-vertex (0-edge) subgraphs; every
 * subsequent level is obtained by growing a frequent k-edge pattern by one edge using its
 * embeddings, instead of literally joining two k-subgraphs that share a core as in the
 * paper's Algorithm 2. This is still complete, since every frequent (k+1)-edge connected
 * subgraph can be reached by extending one of its frequent k-edge connected subgraphs by
 * one edge. The paper's downward-closure test (every edge-removed connected sub-pattern
 * of a candidate must already be frequent) is enforced explicitly, and the intersection
 * of the corresponding TID-lists is used to restrict frequency counting, as described in
 * Sections 2.2-2.3 of the paper.
 * <p>
 * Graph transactions are identified internally by their position (dense index) in the
 * graph list rather than by their (possibly sparse/arbitrary) input id; this lets every
 * TID-list be represented as a fixed-size {@link BitSet} instead of a collection of boxed
 * {@code Integer}s, which is both far more compact and far cheaper to intersect/union in
 * the innermost loops of {@link #grow}.
 * <p>
 * This class is the top-level driver: it parses the input, orchestrates the level-by-level
 * mining loop (see {@link #fsg}) and writes the discovered patterns to the output file.
 * The graph/matching data structures live in {@link FSGGraph} ({@code Graph},
 * {@code PatternMatcher}) and the pattern/canonicalization data structures live in
 * {@link FSGPattern} ({@code Pattern}, {@code CanonResult}, {@code IntArrayKey}, etc).
 * 
 * @author Chen Hui
 */
public class AlgoFSG {

    /** Timestamp (ms) when the algorithm run started. */
    private long startTime;
    /** Timestamp (ms) when the algorithm run ended. */
    private long endTime;
    /** Total number of frequent subgraphs found. */
    private int  patternCount;
    /** Number of graph transactions in the input. */
    private int  graphCount;

    /**
     * Runs the FSG algorithm end to end: parses the input, mines frequent subgraphs,
     * writes the result and records timing statistics. The ids of the graphs containing
     * each pattern are written to the output file (equivalent to calling
     * {@link #runAlgorithm(String, String, double, boolean)} with {@code true}).
     *
     * @param inputFile          path to the input graph database file
     * @param outputFile         path where the discovered patterns will be written
     * @param minSupportFraction minimum support as a fraction of the number of graphs (0-1)
     * @throws IOException if the input cannot be read or the output cannot be written
     */
    public void runAlgorithm(String inputFile, String outputFile,
                              double minSupportFraction) throws IOException {
        runAlgorithm(inputFile, outputFile, minSupportFraction, false);
    }

    /**
     * Runs the FSG algorithm end to end: parses the input, mines frequent subgraphs,
     * writes the result and records timing statistics.
     *
     * @param inputFile          path to the input graph database file
     * @param outputFile         path where the discovered patterns will be written
     * @param minSupportFraction minimum support as a fraction of the number of graphs (0-1)
     * @param outputGraphIds     if true, the ids of the graphs containing each pattern
     *                           are written to the output file (as an "x" line)
     * @throws IOException if the input cannot be read or the output cannot be written
     */
    public void runAlgorithm(String inputFile, String outputFile,
                              double minSupportFraction, boolean outputGraphIds) throws IOException {
        startTime = System.currentTimeMillis();
        List<FSGGraph.Graph> graphs = parseInput(inputFile);
        graphCount = graphs.size();
        List<List<FSGPattern.Pattern>> allLevels = fsg(graphs, minSupportFraction);
        patternCount = 0;
        for (List<FSGPattern.Pattern> level : allLevels) patternCount += level.size();
        writeOutput(allLevels, outputFile, outputGraphIds);
        endTime = System.currentTimeMillis();
    }

    /**
     * Prints statistics about the last algorithm run (number of graphs, number of
     * frequent subgraphs found and total running time) to the standard output.
     */
    public void printStats() {
        System.out.println("=============  FSG v.2.67 - STATS  =============");
        System.out.println(" Number of graph transactions : " + graphCount);
        System.out.println(" Number of frequent subgraphs : " + patternCount);
        System.out.println(" Total time                   : " + (endTime - startTime) + " ms");
        System.out.println("=========================================");
    }

    /**
     * Parses an input graph database file in the standard "t/v/e" text format.
     * @param filename path to the input file
     * @return the list of parsed graph transactions
     * @throws IOException if the file cannot be read or is malformed
     */
    static List<FSGGraph.Graph> parseInput(String filename) throws IOException {
        List<FSGGraph.Graph> graphs  = new ArrayList<>();
        FSGGraph.Graph       current = null;
        int         lineNo  = 0;
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filename))) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                lineNo++;
                String line = raw.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                try {
                    switch (parts[0]) {
                        case "t":
                            // start a new graph transaction
                            if (current != null) current.finalizeGraph();
                            current = new FSGGraph.Graph(Integer.parseInt(parts[2]));
                            graphs.add(current);
                            break;
                        case "v":
                            if (current == null) throw new IOException(
                                    "'v' before 't' at line " + lineNo);
                            current.addVertex(Integer.parseInt(parts[1]),
                                              Integer.parseInt(parts[2]));
                            break;
                        case "e":
                            if (current == null) throw new IOException(
                                    "'e' before 't' at line " + lineNo);
                            current.addEdge(Integer.parseInt(parts[1]),
                                            Integer.parseInt(parts[2]),
                                            Integer.parseInt(parts[3]));
                            break;
                        default: break;
                    }
                } catch (IllegalArgumentException ex) {
                    throw new IOException(
                            "Error at line " + lineNo + ": \"" + raw + "\" -> " + ex.getMessage(), ex);
                }
            }
        }
        if (current != null) current.finalizeGraph();
        return graphs;
    }

    /**
     * Computes the level-0 frequent patterns: single vertices (with no edges) whose
     * label occurs, on at least one non-isolated vertex, in at least {@code minCount} graphs.
     * <p>
     * Support sets are accumulated as {@link BitSet}s indexed by the graph's position in
     * {@code graphs} (its dense index), rather than as a {@code TreeSet<Integer>} of graph
     * ids: this avoids boxing every graph id and makes the (already small) per-label
     * membership test/insert O(1) instead of O(log n).
     * @param graphs   the graph transactions
     * @param minCount minimum number of supporting transactions
     * @return frequent single-vertex patterns, sorted by canonical key
     */
    static List<FSGPattern.Pattern> seedVertices(List<FSGGraph.Graph> graphs, double minCount) {
        int n = graphs.size();
        Map<Integer, BitSet> labelGraphs = new HashMap<>();
        for (int idx = 0; idx < n; idx++) {
            FSGGraph.Graph g = graphs.get(idx);
            Set<Integer> labelsInGraph = new HashSet<>();
            for (int vid = 0; vid < g.vlabels.length; vid++)
                if (g.neighborIds[vid].length > 0)
                    labelsInGraph.add(g.vlabels[vid]);
            for (int lab : labelsInGraph)
                labelGraphs.computeIfAbsent(lab, k -> new BitSet(n)).set(idx);
        }
        List<FSGPattern.Pattern> patterns = new ArrayList<>();
        for (Map.Entry<Integer, BitSet> e : labelGraphs.entrySet()) {
            BitSet bits = e.getValue();
            if (bits.cardinality() >= minCount) {
                List<Integer> tidlist = new ArrayList<>();
                for (int idx = bits.nextSetBit(0); idx >= 0; idx = bits.nextSetBit(idx + 1))
                    tidlist.add(graphs.get(idx).id);
                Collections.sort(tidlist);
                int[] vl  = {e.getKey()};
                int[] key = {e.getKey()};
                patterns.add(new FSGPattern.Pattern(vl, new int[0][], tidlist, bits, key));
            }
        }
        patterns.sort((a, b) -> FSGPattern.compareIntArrays(a.canonKey, b.canonKey));
        return patterns;
    }

    /** A candidate pattern being assembled during growth, before final support counting. */
    static class CandidateEntry {
        /** Vertex labels of the candidate pattern. */
        int[]        vlabels;
        /** Edges of the candidate pattern. */
        int[][]      edges;
        /** Canonical key of the candidate pattern. */
        int[]        canonKey;
        /** Graph (dense) indices the candidate must be re-checked against. A fixed-size
         *  {@link BitSet} is used instead of a {@code Set<Integer>} so that narrowing this
         *  set as more supporting patterns are found (via {@link BitSet#and}) never
         *  allocates a new collection, unlike {@code retainAll} on a hash/tree set. */
        BitSet searchGraphs;

        /**
         * Creates an empty candidate entry.
         * @param graphCount total number of graph transactions (sizes the bit vector)
         */
        CandidateEntry(int graphCount) {
            searchGraphs = new BitSet(graphCount);
        }
    }

    /**
     * Extends every pattern in Fk by one edge to produce Fk+1.
     * <p>
     * For each candidate, every connected edge-removed sub-pattern except the one obtained
     * by removing the newly added edge (which always reconstructs the known-frequent parent
     * {@code pattern}) must already belong to Fk. The intersection of the corresponding
     * TID-bitsets bounds the set of graphs the candidate is re-checked against in the final
     * support-counting pass. The parent's bitset is only cloned lazily (the first time it
     * must actually be narrowed), and is otherwise reused as-is without ever being mutated.
     *
     * @param Fk         frequent k-edge patterns to extend
     * @param graphList  all graph transactions; a graph's position in this list is its
     *                    dense index, used consistently as the bit index in every TID-bitset
     * @param minCount   minimum number of supporting transactions
     * @return the frequent (k+1)-edge patterns, sorted by canonical key
     */
    static List<FSGPattern.Pattern> grow(List<FSGPattern.Pattern> Fk,
                               List<FSGGraph.Graph> graphList,
                               double minCount) {

        int graphCount = graphList.size();

        Map<FSGPattern.IntArrayKey, FSGPattern.Pattern> fkIndex = new HashMap<>((int) (Fk.size() / 0.75f) + 1);
        for (FSGPattern.Pattern p : Fk) fkIndex.put(new FSGPattern.IntArrayKey(p.canonKey), p);

        Map<FSGPattern.IntArrayKey, CandidateEntry> candidateMap = new HashMap<>();
        Map<FSGPattern.IntArrayKey, FSGPattern.CanonResult>    canonCache   = new HashMap<>();

        for (FSGPattern.Pattern pattern : Fk) {
            Set<Long>      pEdgeSet = pattern.edgeKeySet();
            FSGGraph.PatternMatcher matcher  = new FSGGraph.PatternMatcher(pattern.vlabels, pattern.edges);
            int            pn       = pattern.vlabels.length;

            // Reusable buffers for every extension of this particular pattern: the first
            // pn vertex labels and all of the parent's edges never change while growing
            // this pattern, so they are written into these buffers once, and only the
            // single "new" slot is overwritten per candidate below - instead of allocating
            // a fresh Arrays.copyOf of the (growing) vlabels/edges arrays for every single
            // embedding/pattern-vertex/neighbor combination examined.
            int[] pendantVlabelsBuf = new int[pn + 1];
            System.arraycopy(pattern.vlabels, 0, pendantVlabelsBuf, 0, pn);
            int[][] newEdgesBuf = new int[pattern.edges.length + 1][];
            System.arraycopy(pattern.edges, 0, newEdgesBuf, 0, pattern.edges.length);
            int[] addedEdgeBuf = new int[3];
            newEdgesBuf[pattern.edges.length] = addedEdgeBuf;
            int newEdgesCount = newEdgesBuf.length;

            for (int gIdx = pattern.tidBits.nextSetBit(0); gIdx >= 0; gIdx = pattern.tidBits.nextSetBit(gIdx + 1)) {
                FSGGraph.Graph       g          = graphList.get(gIdx);
                int         gn         = g.vlabels.length;
                List<int[]> embeddings = matcher.findEmbeddings(g, true);
                if (embeddings.isEmpty()) continue;

                // reused across every embedding of this pattern in this graph
                boolean[] image = new boolean[gn];
                int[]     inv   = new int[gn];

                for (int[] emb : embeddings) {
                    for (int pv = 0; pv < pn; pv++) { image[emb[pv]] = true; inv[emb[pv]] = pv; }

                    for (int pVid = 0; pVid < pn; pVid++) {
                        int   tVid      = emb[pVid];
                        int[] nbrIds    = g.neighborIds[tVid];
                        int[] nbrLabels = g.neighborLabels[tVid];

                        for (int k = 0; k < nbrIds.length; k++) {
                            int nbrT = nbrIds[k];
                            int elab = nbrLabels[k];

                            int[] newVlabels;

                            if (image[nbrT]) {
                                // neighbor already mapped: this extension closes a cycle
                                int pVid2 = inv[nbrT];
                                if (pVid2 == pVid) continue;
                                int a = Math.min(pVid, pVid2), b = Math.max(pVid, pVid2);
                                if (pEdgeSet.contains(((long) a << 32) | (b & 0xFFFFFFFFL))) continue;

                                addedEdgeBuf[0] = a; addedEdgeBuf[1] = b; addedEdgeBuf[2] = elab;
                                newVlabels = pattern.vlabels; // no new vertex: safe to reuse (read-only)

                            } else {
                                // neighbor not mapped: this extension adds a pendant vertex
                                int newVid = pn;
                                addedEdgeBuf[0] = Math.min(pVid, newVid);
                                addedEdgeBuf[1] = Math.max(pVid, newVid);
                                addedEdgeBuf[2] = elab;
                                pendantVlabelsBuf[newVid] = g.vlabels[nbrT];
                                newVlabels = pendantVlabelsBuf;
                            }

                            // the candidate's edge array: newEdgesBuf's first entries are
                            // always the parent's (unchanged) edges, and the last slot
                            // always points at addedEdgeBuf, just updated above - so no
                            // allocation is needed here, unlike a fresh Arrays.copyOf.
                            int[][] newEdges = newEdgesBuf;

                            int maxVid = newVlabels.length - 1;

                            // downward-closure test: every other edge-removed connected
                            // sub-pattern must already be frequent
                            BitSet  boundTid       = pattern.tidBits;
                            boolean boundCloned     = false;
                            boolean allSubFrequent = true;

                            for (int ei = 0; ei < newEdgesCount - 1; ei++) {
                                if (!FSGPattern.isConnectedIgnoringIsolated(newEdges, newEdgesCount, ei, maxVid))
                                    continue;

                                FSGPattern.StrippedGraph sg =
                                        FSGPattern.stripIsolated(newVlabels, newEdges, newEdgesCount, ei);
                                FSGPattern.IntArrayKey subRawKey =
                                        new FSGPattern.IntArrayKey(FSGPattern.buildRawKeyForStripped(sg.vlabels, sg.edges));
                                FSGPattern.CanonResult subCanon =
                                        FSGPattern.canonicalizeCached(canonCache, subRawKey, sg.vlabels, sg.edges);
                                FSGPattern.Pattern subPattern = fkIndex.get(new FSGPattern.IntArrayKey(subCanon.key));

                                if (subPattern == null) { allSubFrequent = false; break; }

                                if (!boundCloned) { boundTid = (BitSet) boundTid.clone(); boundCloned = true; }
                                boundTid.and(subPattern.tidBits);
                                if (boundTid.isEmpty()) { allSubFrequent = false; break; }
                            }
                            if (!allSubFrequent) continue;

                            // canonicalize the candidate and merge it into the candidate map
                            FSGPattern.IntArrayKey candidateRawKey = new FSGPattern.IntArrayKey(
                                    FSGPattern.buildRawCanonicalizationKey(newVlabels, pattern.edges, addedEdgeBuf));
                            FSGPattern.CanonResult cr = FSGPattern.canonicalizeCached(canonCache, candidateRawKey,
                                                                 newVlabels, newEdges);
                            FSGPattern.IntArrayKey ks = new FSGPattern.IntArrayKey(cr.key);

                            CandidateEntry entry = candidateMap.get(ks);
                            if (entry == null) {
                                entry          = new CandidateEntry(graphCount);
                                entry.vlabels  = cr.vlabels;
                                entry.edges    = cr.edges;
                                entry.canonKey = cr.key;
                                entry.searchGraphs.or(boundTid);
                                candidateMap.put(ks, entry);
                            } else {
                                entry.searchGraphs.and(boundTid);
                            }
                        }
                    }

                    for (int pv = 0; pv < pn; pv++) image[emb[pv]] = false;
                }
            }
        }

        // exact support counting restricted to the (now tight) searchGraphs
        List<FSGPattern.Pattern> Fk1 = new ArrayList<>();
        for (CandidateEntry entry : candidateMap.values()) {
            if (entry.searchGraphs.cardinality() < minCount) continue;
            List<Integer>  tidlist = new ArrayList<>();
            BitSet         tidBits = new BitSet(graphCount);
            FSGGraph.PatternMatcher matcher = new FSGGraph.PatternMatcher(entry.vlabels, entry.edges);
            for (int idx = entry.searchGraphs.nextSetBit(0); idx >= 0; idx = entry.searchGraphs.nextSetBit(idx + 1)) {
                FSGGraph.Graph g = graphList.get(idx);
                if (matcher.containsSubgraph(g)) { tidlist.add(g.id); tidBits.set(idx); }
            }
            if (tidlist.size() >= minCount) {
                Collections.sort(tidlist);
                Fk1.add(new FSGPattern.Pattern(entry.vlabels, entry.edges, tidlist, tidBits, entry.canonKey));
            }
        }
        Fk1.sort((a, b) -> FSGPattern.compareIntArrays(a.canonKey, b.canonKey));
        return Fk1;
    }

    /**
     * Top-level driver that mines all levels of frequent connected subgraphs, starting
     * from frequent single vertices and growing one edge at a time until no candidate
     * remains frequent.
     * @param graphs              the graph transactions
     * @param minSupportFraction  minimum support as a fraction of the number of graphs (0-1)
     * @return the list of frequent-pattern levels, in increasing order of edge count
     */
    static List<List<FSGPattern.Pattern>> fsg(List<FSGGraph.Graph> graphs, double minSupportFraction) {
        // graphs' own list order is used consistently as the dense-index space for every
        // TID-bitset built below, so no id-to-index map is needed at all.
        double minCount = minSupportFraction * graphs.size() - 1e-9;

        List<List<FSGPattern.Pattern>> allLevels = new ArrayList<>();
        List<FSGPattern.Pattern>       F0        = seedVertices(graphs, minCount);
        if (F0.isEmpty()) return allLevels;
        allLevels.add(F0);

        List<FSGPattern.Pattern> Fk = F0;
        while (!Fk.isEmpty()) {
            List<FSGPattern.Pattern> Fk1 = grow(Fk, graphs, minCount);
            if (Fk1.isEmpty()) break;
            allLevels.add(Fk1);
            Fk = Fk1;
        }
        return allLevels;
    }

    /**
     * Writes all discovered pattern levels to a text file in the standard "t/v/e/x"
     * format, always including the ids of the graphs containing each pattern
     * (equivalent to calling {@link #writeOutput(List, String, boolean)} with {@code true}).
     *
     * @param allLevels the frequent-pattern levels to write
     * @param filename  path of the output file
     * @throws IOException if the file cannot be written
     */
    static void writeOutput(List<List<FSGPattern.Pattern>> allLevels, String filename)
            throws IOException {
        writeOutput(allLevels, filename, true);
    }

    /**
     * Writes all discovered pattern levels to a text file in the standard "t/v/e/x" format.
     *
     * @param allLevels      the frequent-pattern levels to write
     * @param filename       path of the output file
     * @param outputGraphIds if true, an "x" line listing the ids of the graphs
     *                       containing each pattern is written after its edges
     * @throws IOException if the file cannot be written
     */
    static void writeOutput(List<List<FSGPattern.Pattern>> allLevels, String filename,
                             boolean outputGraphIds) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename))) {
    		bw.write("@FILETYPE=\"Frequent subgraphs\"");
    		bw.append(System.lineSeparator());
    		bw.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
    		bw.append(System.lineSeparator());
    		
            int           idx   = 0;
            boolean       first = true;
            StringBuilder sb    = new StringBuilder();
            for (List<FSGPattern.Pattern> level : allLevels) {
                for (FSGPattern.Pattern pat : level) {
                    sb.setLength(0);
                    if (!first) sb.append('\n');
                    first = false;
                    // pattern header with id and support count
                    sb.append("t # ").append(idx).append(" * ").append(pat.tidlist.size()).append('\n');
                    for (int vid = 0; vid < pat.vlabels.length; vid++)
                        sb.append("v ").append(vid).append(' ').append(pat.vlabels[vid]).append('\n');
                    for (int[] e : pat.edges)
                        sb.append("e ").append(e[0]).append(' ').append(e[1])
                          .append(' ').append(e[2]).append('\n');
                    // If the user chose to output the graph ids where the frequent
                    // subgraph appears, we output it
                    if (outputGraphIds) {
                        sb.append('x');
                        for (int gid : pat.tidlist) sb.append(' ').append(gid);
                        sb.append('\n');
                    }
                    bw.append(sb);
                    idx++;
                }
            }
        }
    }
}