// =============================================================================
package ca.pfv.spmf.algorithms.graph_mining.ffsm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
 * FFSM (Fast Frequent Subgraph Mining): mines frequent connected subgraphs from a
 * database of labeled graphs. Based on Huan, Wang &amp; Prins, "Efficient Mining of
 * Frequent Subgraph in the Presence of Isomorphism", ICDM 2003.
 * <p>
 * Each pattern is represented by its Canonical Adjacency Matrix (CAM): the vertex
 * ordering that maximizes the adjacency-matrix code ({@link FFSMPattern#canonicalizeMax}).
 * Frequent single vertices and edges are computed first ({@link #seedVertices},
 * {@link #seedEdges}); the frequent edges then seed a depth-first join/extension
 * recursion ({@link #ffsmExplore}) that grows patterns using only embedding-set
 * operations - joining two patterns sharing a parent ({@link #tryJoinPair} and related
 * methods) or extending a pattern by one pendant vertex ({@link #extendByPendant}) -
 * without ever requiring a subgraph-isomorphism test.
 * <p>
 * This class is the driver: it parses the input, runs the mining recursion, and writes
 * the results using the same "t/v/e/x" format as other SPMF graph-mining algorithms.
 *
 * @author Chen Hui
 */
public class AlgoFFSM {

    /** Precompiled whitespace pattern used to tokenize input lines. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** Start time of the algorithm run, in milliseconds. */
    private long startTime;
    /** End time of the algorithm run, in milliseconds. */
    private long endTime;
    /** Number of frequent patterns found. */
    private int  patternCount;
    /** Number of graph transactions in the input database. */
    private int  graphCount;

    /**
     * Runs FFSM end to end: parses the input, mines frequent subgraphs, writes the
     * output, and records timing statistics. The ids of the graphs containing each
     * pattern are written to the output file (equivalent to calling
     * {@link #runAlgorithm(String, String, double, boolean)} with {@code true}).
     *
     * @param inputFile          input graph database file
     * @param outputFile         output file for discovered patterns
     * @param minSupportFraction minimum support as a fraction of the number of graphs (0-1)
     * @throws IOException if the input cannot be read or the output cannot be written
     */
    public void runAlgorithm(String inputFile, String outputFile,
                              double minSupportFraction) throws IOException {
        runAlgorithm(inputFile, outputFile, minSupportFraction, false);
    }

    /**
     * Runs FFSM end to end: parses the input, mines frequent subgraphs, writes the
     * output, and records timing statistics.
     *
     * @param inputFile          input graph database file
     * @param outputFile         output file for discovered patterns
     * @param minSupportFraction minimum support as a fraction of the number of graphs (0-1)
     * @param outputGraphIds     if true, the ids of the graphs containing each pattern
     *                           are written to the output file (as an "x" line)
     * @throws IOException if the input cannot be read or the output cannot be written
     */
    public void runAlgorithm(String inputFile, String outputFile,
                              double minSupportFraction, boolean outputGraphIds) throws IOException {
        startTime = System.currentTimeMillis();
        List<FFSMGraph.Graph> graphs = parseInput(inputFile); // load graph database
        graphCount = graphs.size();
        List<List<FFSMPattern.Pattern>> allLevels = ffsm(graphs, minSupportFraction); // run mining
        patternCount = 0;
        for (List<FFSMPattern.Pattern> level : allLevels) patternCount += level.size();
        writeOutput(allLevels, outputFile, outputGraphIds); // write results
        endTime = System.currentTimeMillis();
    }

    /** Prints run statistics (graph count, pattern count, running time). */
    public void printStats() {
        System.out.println("=============  FFSM v.2.67 - STATS  =============");
        System.out.println(" Number of graph transactions : " + graphCount);
        System.out.println(" Number of frequent subgraphs : " + patternCount);
        System.out.println(" Total time                   : " + (endTime - startTime) + " ms");
        System.out.println("=========================================");
    }

    /**
     * Parses a graph database file in the "t/v/e" text format into a list of graphs.
     *
     * @param filename path to the input file
     * @return the parsed graph transactions
     * @throws IOException if the file cannot be read or its content is malformed
     */
    static List<FFSMGraph.Graph> parseInput(String filename) throws IOException {
        List<FFSMGraph.Graph> graphs  = new ArrayList<>();
        FFSMGraph.Graph       current = null;
        int         lineNo  = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(filename)))) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                lineNo++;
                String line = raw.trim();
                if (line.isEmpty() || line.charAt(0) == '@') continue;
                String[] parts = WHITESPACE.split(line);
                try {
                    switch (parts[0]) {
                        case "t":
                            if (current != null) current.finalizeGraph(); // close previous graph
                            current = new FFSMGraph.Graph(Integer.parseInt(parts[2]));
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
                    }
                } catch (IllegalArgumentException ex) {
                    throw new IOException(
                            "Error at line " + lineNo + ": \"" + raw + "\" -> " + ex.getMessage(), ex);
                }
            }
        }
        if (current != null) current.finalizeGraph(); // close last graph
        return graphs;
    }

    /**
     * Builds the frequent single-vertex patterns. Every vertex occurrence counts, and
     * each resulting pattern is reported directly, never grown further.
     *
     * @param graphs   graph transactions to mine
     * @param minCount minimum number of supporting transactions
     * @return frequent single-vertex patterns, sorted by canonical code
     */
    static List<FFSMPattern.Pattern> seedVertices(List<FFSMGraph.Graph> graphs, double minCount) {
        int n = graphs.size();
        Map<Integer, List<FFSMPattern.Embedding>> labelEmbeddings = new HashMap<>();
        // group every vertex occurrence by its label
        for (int idx = 0; idx < n; idx++) {
            FFSMGraph.Graph g = graphs.get(idx);
            for (int vid = 0; vid < g.vlabels.length; vid++) {
                int label = g.vlabels[vid];
                labelEmbeddings
                        .computeIfAbsent(label, k -> new ArrayList<>())
                        .add(new FFSMPattern.Embedding(idx, new int[]{vid}));
            }
        }
        List<FFSMPattern.Pattern> patterns = new ArrayList<>();
        for (Map.Entry<Integer, List<FFSMPattern.Embedding>> e : labelEmbeddings.entrySet()) {
            List<FFSMPattern.Embedding> embs = e.getValue();
            BitSet bits = new BitSet();
            for (FFSMPattern.Embedding em : embs) bits.set(em.graphIndex); // mark supporting graphs
            int card = bits.cardinality();
            if (card >= minCount) {
                List<Integer> tidlist = new ArrayList<>(card);
                for (int idx = bits.nextSetBit(0); idx >= 0; idx = bits.nextSetBit(idx + 1))
                    tidlist.add(graphs.get(idx).id);
                Collections.sort(tidlist);
                int[] vl  = {e.getKey()};
                int[] key = {e.getKey()}; // code of a 1x1 CAM is just the vertex label
                patterns.add(new FFSMPattern.Pattern(vl, new int[0][], embs, tidlist, key));
            }
        }
        patterns.sort((a, b) -> FFSMPattern.compareIntArrays(a.canonKey, b.canonKey));
        return patterns;
    }

    /**
     * Builds the frequent single-edge patterns by scanning the database directly;
     * these seed the join/extension recursion.
     *
     * @param graphs   graph transactions to mine
     * @param minCount minimum number of supporting transactions
     * @return frequent single-edge patterns, sorted by canonical code
     */
    static List<FFSMPattern.Pattern> seedEdges(List<FFSMGraph.Graph> graphs, double minCount) {
        int graphCount = graphs.size();
        Map<FFSMPattern.IntArrayKey, CandidateEntry>          candidateMap = new HashMap<>();
        Map<FFSMPattern.IntArrayKey, FFSMPattern.CanonResult> canonCache   = new HashMap<>();

        for (int idx = 0; idx < graphCount; idx++) {
            FFSMGraph.Graph g = graphs.get(idx);
            for (int vid = 0; vid < g.vlabels.length; vid++) {
                int[] nbrIds    = g.neighborIds[vid];
                int[] nbrLabels = g.neighborLabels[vid];
                for (int k = 0; k < nbrIds.length; k++) {
                    int nbrT = nbrIds[k];
                    if (nbrT <= vid) continue; // each undirected edge visited exactly once
                    int elab = nbrLabels[k];

                    int[]   rawVlabels = {g.vlabels[vid], g.vlabels[nbrT]};
                    int[][] rawEdges   = {{0, 1, elab}};
                    int[]   rawVerts   = {vid, nbrT};

                    FFSMPattern.CanonResult cr =
                            FFSMPattern.canonicalizeCached(canonCache, rawVlabels, rawEdges);
                    addToCandidateMap(candidateMap, cr, rawVerts, idx); // record this occurrence
                }
            }
        }

        return filterFrequent(candidateMap, graphs, minCount);
    }

    /** A candidate pattern being assembled during growth, before final filtering. */
    static class CandidateEntry {
        /** Vertex labels of the candidate. */
        int[]   vlabels;
        /** Edges of the candidate. */
        int[][] edges;
        /** Canonical code (canonical map) or identity code (suboptimal map). */
        int[]   canonKey;
        /** Embeddings collected so far for this candidate. */
        List<FFSMPattern.Embedding> embeddings = new ArrayList<>();
        /** De-duplicates embeddings reached via different growth paths. */
        Set<FFSMPattern.EmbeddingKey> seenEmbeddings = new HashSet<>();
    }

    /**
     * Groups the lookup structures needed while building one pool member's children,
     * so join/extension methods take a single parameter instead of several maps.
     */
    private static final class GrowthContext {
        /** Accumulates canonical (output-eligible) candidates. */
        final Map<FFSMPattern.IntArrayKey, CandidateEntry>          candidateMap  = new HashMap<>();
        /** Accumulates suboptimal (join-only) candidates. */
        final Map<FFSMPattern.IntArrayKey, CandidateEntry>          subMap        = new HashMap<>();
        /** Cache of canonicalization results, keyed by raw candidate structure. */
        final Map<FFSMPattern.IntArrayKey, FFSMPattern.CanonResult> canonCache    = new HashMap<>();
        /** Caches, per raw candidate structure, whether it is eligible as a suboptimal CAM. */
        final Map<FFSMPattern.IntArrayKey, Boolean> subCheckCache = new HashMap<>();
    }

    /**
     * One member of a pool explored by {@link #ffsmExplore}: a pattern (genuine or
     * suboptimal CAM) plus the outer/inner status, last-edge information and parent
     * key needed to test join-compatibility with other pool members.
     */
    private static final class PoolMember {
        /** The pattern this member wraps. */
        FFSMPattern.Pattern pattern;
        /** True if this member's last vertex has exactly one incident edge (pendant). */
        boolean outer;
        /** Target vertex of the last vertex's rightmost edge. */
        int lastEdgeTarget;
        /** Label of the last vertex's rightmost edge. */
        int lastEdgeLabel;
        /** Label of the pendant vertex; meaningful only when {@link #outer} is true. */
        int newVertexLabel;
        /** True if this member is a genuine CAM; false if it is a join-only suboptimal CAM. */
        boolean canonical;
        /** Canonical key of this member's maximal proper submatrix (parent). */
        FFSMPattern.IntArrayKey parentKey;

        /** Cached embedding identity-key set, built lazily by {@link #embeddingKeys()}. */
        private Set<FFSMPattern.EmbeddingKey> embeddingKeySet;
        /** Cached embeddings grouped by graph index, built lazily by {@link #embeddingsByGraph()}. */
        private Map<Integer, List<FFSMPattern.Embedding>> embeddingsByGraphMap;

        /**
         * Returns this member's embedding identity-key set, building and caching it
         * on first call.
         *
         * @return the set of embedding keys for this member's pattern
         */
        Set<FFSMPattern.EmbeddingKey> embeddingKeys() {
            if (embeddingKeySet == null) {
                int size = pattern.embeddings.size();
                embeddingKeySet = new HashSet<>((int) (size / 0.75f) + 1);
                for (FFSMPattern.Embedding e : pattern.embeddings)
                    embeddingKeySet.add(embeddingKey(e));
            }
            return embeddingKeySet;
        }

        /**
         * Returns this member's embeddings grouped by graph index, building and
         * caching the grouping on first call.
         *
         * @return map from graph index to the embeddings occurring in that graph
         */
        Map<Integer, List<FFSMPattern.Embedding>> embeddingsByGraph() {
            if (embeddingsByGraphMap == null) {
                embeddingsByGraphMap = new HashMap<>();
                for (FFSMPattern.Embedding e : pattern.embeddings)
                    embeddingsByGraphMap
                            .computeIfAbsent(e.graphIndex, k -> new ArrayList<>())
                            .add(e);
            }
            return embeddingsByGraphMap;
        }
    }

    /**
     * Grows an outer, canonical pattern by one pendant vertex attached to a neighbor
     * of each embedding's last vertex. Results feed both the canonical and (if not
     * canonical) suboptimal accumulators of {@code ctx}.
     *
     * @param pattern the pattern to extend
     * @param outer   whether {@code pattern}'s last vertex has exactly one incident edge
     * @param graphs  graph transactions to search for extensions
     * @param ctx     shared per-level growth context
     */
    private static void extendByPendant(FFSMPattern.Pattern pattern,
                                         boolean outer,
                                         List<FFSMGraph.Graph> graphs,
                                         GrowthContext ctx) {

        if (!outer) return;

        int pn = pattern.vlabels.length;
        int pv = pn - 1; // fixed canonical-last vertex

        for (FFSMPattern.Embedding emb : pattern.embeddings) {
            FFSMGraph.Graph g     = graphs.get(emb.graphIndex);
            int[]           verts = emb.vertices;

            int   tVid      = verts[pv];
            int[] nbrIds    = g.neighborIds[tVid];
            int[] nbrLabels = g.neighborLabels[tVid];

            for (int k = 0; k < nbrIds.length; k++) {
                int nbrT = nbrIds[k];
                int elab = nbrLabels[k];

                // skip neighbors already part of the embedding (would close a cycle)
                boolean alreadyUsed = false;
                for (int u : verts) { if (u == nbrT) { alreadyUsed = true; break; } }
                if (alreadyUsed) continue;

                int newVid = pn;
                int[] rawVlabels = new int[pn + 1];
                System.arraycopy(pattern.vlabels, 0, rawVlabels, 0, pn);
                rawVlabels[newVid] = g.vlabels[nbrT];

                int[][] rawEdges = new int[pattern.edges.length + 1][];
                System.arraycopy(pattern.edges, 0, rawEdges, 0, pattern.edges.length);
                rawEdges[pattern.edges.length] =
                        new int[]{Math.min(pv, newVid), Math.max(pv, newVid), elab};

                int[] rawVerts = new int[pn + 1];
                System.arraycopy(verts, 0, rawVerts, 0, pn);
                rawVerts[newVid] = nbrT;

                FFSMPattern.IntArrayKey rawKey =
                        new FFSMPattern.IntArrayKey(FFSMPattern.buildRawKey(rawVlabels, rawEdges));
                FFSMPattern.CanonResult cr =
                        FFSMPattern.canonicalizeCached(ctx.canonCache, rawKey, rawVlabels, rawEdges);

                addToCandidateMap(ctx.candidateMap, cr, rawVerts, emb.graphIndex);
                maybeAddSuboptimal(ctx, rawKey, rawVlabels, rawEdges, rawVerts, cr.key, emb.graphIndex);
            }
        }
    }

    /**
     * Builds a pattern's {@link PoolMember} description (outer/inner status,
     * last-edge information, parent key).
     *
     * @param p         the pattern to describe
     * @param canonical whether {@code p} is a genuine CAM
     * @return the pool member, or {@code null} if {@code p} has fewer than 2 vertices
     *         or its last vertex is disconnected
     */
    private static PoolMember buildPoolMember(FFSMPattern.Pattern p, boolean canonical) {
        if (p.vlabels.length < 2) return null;

        int pn   = p.vlabels.length;
        int last = pn - 1;
        int degree = 0, target = -1, label = -1;
        for (int[] e : p.edges) {
            if (e[0] == last || e[1] == last) {
                int other = (e[0] == last) ? e[1] : e[0];
                degree++;
                if (other > target) { target = other; label = e[2]; } // rightmost in code
            }
        }
        if (degree == 0) return null;

        PoolMember m = new PoolMember();
        m.pattern        = p;
        m.canonical      = canonical;
        m.outer          = (degree == 1);
        m.lastEdgeTarget = target;
        m.lastEdgeLabel  = label;
        if (m.outer) m.newVertexLabel = p.vlabels[last];
        m.parentKey = computeParentKey(p, m.outer, m.lastEdgeTarget);
        return m;
    }

    /**
     * Computes the canonical key of a pattern's maximal proper submatrix (its
     * parent).
     *
     * @param p              the pattern
     * @param outer          whether {@code p}'s last vertex is a pendant
     * @param lastEdgeTarget target of the last vertex's rightmost edge
     * @return the parent's canonical key
     */
    private static FFSMPattern.IntArrayKey computeParentKey(FFSMPattern.Pattern p,
                                                             boolean outer,
                                                             int lastEdgeTarget) {
        RawGraph parent = buildParentRawGraph(p.vlabels, p.edges, outer, lastEdgeTarget);
        return new FFSMPattern.IntArrayKey(FFSMPattern.buildRawKey(parent.vlabels, parent.edges));
    }

    /**
     * Applies the join tie-break rule: a pairing is rejected when the other operand
     * is a genuine CAM with a strictly greater canonical code, since the symmetric
     * pairing will perform the join instead.
     *
     * @param a first pool member
     * @param b second pool member
     * @return true if the join may proceed for this ordered pair
     */
    private static boolean tieBreakAllows(PoolMember a, PoolMember b) {
        return !b.canonical
               || FFSMPattern.compareIntArrays(a.pattern.canonKey, b.pattern.canonKey) >= 0;
    }

    /**
     * Dispatches the join of two pool members sharing the same parent to the
     * appropriate case (1, 2 or 3).
     *
     * @param X genuine-CAM pool member
     * @param Y other pool member
     * @param ctx shared per-level growth context
     */
    private static void tryJoinPair(PoolMember X, PoolMember Y, GrowthContext ctx) {

        if (!X.parentKey.equals(Y.parentKey)) return; // no shared parent: join undefined

        if (X.outer && Y.outer) {
            handleCase3(X, Y, ctx);
        } else if (X.outer != Y.outer) {
            if (!tieBreakAllows(X, Y)) return;
            PoolMember innerM = X.outer ? Y : X;
            PoolMember outerM = X.outer ? X : Y;
            buildCase2(innerM, outerM, ctx);
        } else {
            if (!tieBreakAllows(X, Y)) return;
            buildCase1(X, Y, ctx);
        }
    }

    /**
     * Candidate structure produced by joining two outer pool members: fixed for a
     * given pair of pool members and shared across every matching pair of embeddings.
     */
    private static final class Case3Structure {
        /** Vertex labels of the candidate. */
        final int[] rawVlabels;
        /** Edges of the candidate. */
        final int[][] rawEdges;
        /** Raw memoization key of the candidate structure. */
        final FFSMPattern.IntArrayKey rawKey;
        /** Canonicalization result of the candidate. */
        final FFSMPattern.CanonResult cr;
        /** Candidate-map lookup key for {@link #cr}. */
        final FFSMPattern.IntArrayKey ck;

        Case3Structure(int[] rawVlabels, int[][] rawEdges,
                       FFSMPattern.IntArrayKey rawKey, FFSMPattern.CanonResult cr) {
            this.rawVlabels = rawVlabels;
            this.rawEdges   = rawEdges;
            this.rawKey     = rawKey;
            this.cr         = cr;
            this.ck         = cr.candidateKey();
        }
    }

    /**
     * Join case 3 (both operands outer): for every pair of embeddings agreeing on
     * the shared parent prefix, builds a case-3b candidate (pendants on distinct
     * targets) or a case-3a candidate (pendants on the same target).
     *
     * @param mx  first outer pool member
     * @param my  second outer pool member
     * @param ctx shared per-level growth context
     */
    private static void handleCase3(PoolMember mx, PoolMember my, GrowthContext ctx) {

        int pnParent = mx.pattern.vlabels.length - 1;

        boolean allowCase3a = tieBreakAllows(mx, my);
        boolean tryCase3a   = allowCase3a && mx.lastEdgeTarget != my.lastEdgeTarget;

        Map<Integer, List<FFSMPattern.Embedding>> myByGraph = my.embeddingsByGraph();

        Case3Structure struct3a = null;
        Case3Structure struct3b = null;

        for (FFSMPattern.Embedding ex : mx.pattern.embeddings) {
            List<FFSMPattern.Embedding> candidates = myByGraph.get(ex.graphIndex);
            if (candidates == null) continue;

            int lastX = ex.vertices[pnParent];

            for (FFSMPattern.Embedding ey : candidates) {
                // check both embeddings agree on the shared parent prefix
                boolean prefixMatch = true;
                for (int t = 0; t < pnParent; t++) {
                    if (ex.vertices[t] != ey.vertices[t]) { prefixMatch = false; break; }
                }
                if (!prefixMatch) continue;

                int lastY = ey.vertices[pnParent];

                if (lastX != lastY) {
                    if (struct3b == null) struct3b = buildCase3bStructure(mx, my, pnParent, ctx);
                    emitCase3b(struct3b, ex, ey, lastX, lastY, pnParent, ctx);
                } else if (tryCase3a) {
                    if (struct3a == null) struct3a = buildCase3aStructure(mx, my, pnParent, ctx);
                    emitCase3a(struct3a, ex, lastX, pnParent, ctx);
                }
            }
        }
    }

    /**
     * Builds the vertex-label prefix shared by both case-3 structures: {@code mx}'s
     * labels up to the shared parent, followed by {@code mx}'s pendant label.
     *
     * @param mx        first outer pool member
     * @param pnParent  size of the shared parent
     * @param totalSize final size of the returned array
     * @return the partially filled vertex-label array
     */
    private static int[] buildCase3BaseVlabels(PoolMember mx, int pnParent, int totalSize) {
        int[] rawVlabels = new int[totalSize];
        System.arraycopy(mx.pattern.vlabels, 0, rawVlabels, 0, pnParent);
        rawVlabels[pnParent] = mx.newVertexLabel;
        return rawVlabels;
    }

    /**
     * Builds the edge array shared by both case-3 structures: every edge of
     * {@code mx} not touching the shared parent's last vertex, plus {@code mx}'s own
     * pendant edge, leaving one trailing slot for the caller to fill.
     *
     * @param mx       first outer pool member
     * @param pnParent size of the shared parent
     * @return the edge array with its last slot left empty
     */
    private static int[][] buildCase3BaseEdges(PoolMember mx, int pnParent) {
        int baseEdgeCount = mx.pattern.edges.length - 1;
        int[][] rawEdges = new int[baseEdgeCount + 2][];
        int idx = 0;
        for (int[] e : mx.pattern.edges) {
            if (e[0] != pnParent && e[1] != pnParent) rawEdges[idx++] = e;
        }
        rawEdges[idx] = new int[]{mx.lastEdgeTarget, pnParent, mx.lastEdgeLabel};
        return rawEdges; // last slot left for the caller to fill
    }

    /**
     * Builds the case-3b candidate structure: the shared parent extended with both
     * pendants, producing a branching, larger candidate.
     *
     * @param mx       first outer pool member
     * @param my       second outer pool member
     * @param pnParent size of the shared parent
     * @param ctx      shared per-level growth context
     * @return the resulting candidate structure
     */
    private static Case3Structure buildCase3bStructure(PoolMember mx, PoolMember my,
                                                         int pnParent, GrowthContext ctx) {
        int[] rawVlabels = buildCase3BaseVlabels(mx, pnParent, pnParent + 2);
        rawVlabels[pnParent + 1] = my.newVertexLabel;

        int[][] rawEdges = buildCase3BaseEdges(mx, pnParent);
        rawEdges[rawEdges.length - 1] = new int[]{my.lastEdgeTarget, pnParent + 1, my.lastEdgeLabel};

        FFSMPattern.IntArrayKey rawKey =
                new FFSMPattern.IntArrayKey(FFSMPattern.buildRawKey(rawVlabels, rawEdges));
        FFSMPattern.CanonResult cr =
                FFSMPattern.canonicalizeCached(ctx.canonCache, rawKey, rawVlabels, rawEdges);
        return new Case3Structure(rawVlabels, rawEdges, rawKey, cr);
    }

    /**
     * Records one case-3b embedding pair against a precomputed candidate structure.
     *
     * @param s        precomputed case-3b structure
     * @param ex       embedding of the first operand
     * @param ey       embedding of the second operand
     * @param lastX    pendant target of {@code ex}
     * @param lastY    pendant target of {@code ey}
     * @param pnParent size of the shared parent
     * @param ctx      shared per-level growth context
     */
    private static void emitCase3b(Case3Structure s, FFSMPattern.Embedding ex, FFSMPattern.Embedding ey,
                                    int lastX, int lastY, int pnParent, GrowthContext ctx) {
        int[] rawVerts = new int[pnParent + 2];
        System.arraycopy(ex.vertices, 0, rawVerts, 0, pnParent);
        rawVerts[pnParent]     = lastX;
        rawVerts[pnParent + 1] = lastY;

        addToCandidateMap(ctx.candidateMap, s.cr, s.ck, rawVerts, ex.graphIndex);
        maybeAddSuboptimal(ctx, s.rawKey, s.rawVlabels, s.rawEdges, rawVerts, s.cr.key, ex.graphIndex);
    }

    /**
     * Builds the case-3a candidate structure: both pendants coincide on the same
     * target vertex, producing a cycle-closing candidate of the same size.
     *
     * @param mx       first outer pool member
     * @param my       second outer pool member
     * @param pnParent size of the shared parent
     * @param ctx      shared per-level growth context
     * @return the resulting candidate structure
     */
    private static Case3Structure buildCase3aStructure(PoolMember mx, PoolMember my,
                                                         int pnParent, GrowthContext ctx) {
        int[] rawVlabels = buildCase3BaseVlabels(mx, pnParent, pnParent + 1);

        int[][] rawEdges = buildCase3BaseEdges(mx, pnParent);
        rawEdges[rawEdges.length - 1] = new int[]{my.lastEdgeTarget, pnParent, my.lastEdgeLabel};

        FFSMPattern.IntArrayKey rawKey =
                new FFSMPattern.IntArrayKey(FFSMPattern.buildRawKey(rawVlabels, rawEdges));
        FFSMPattern.CanonResult cr =
                FFSMPattern.canonicalizeCached(ctx.canonCache, rawKey, rawVlabels, rawEdges);
        return new Case3Structure(rawVlabels, rawEdges, rawKey, cr);
    }

    /**
     * Records one case-3a embedding pair against a precomputed candidate structure.
     *
     * @param s        precomputed case-3a structure
     * @param ex       embedding of the first operand
     * @param last     shared pendant target vertex
     * @param pnParent size of the shared parent
     * @param ctx      shared per-level growth context
     */
    private static void emitCase3a(Case3Structure s, FFSMPattern.Embedding ex, int last,
                                    int pnParent, GrowthContext ctx) {
        int[] rawVerts = new int[pnParent + 1];
        System.arraycopy(ex.vertices, 0, rawVerts, 0, pnParent);
        rawVerts[pnParent] = last;

        addToCandidateMap(ctx.candidateMap, s.cr, s.ck, rawVerts, ex.graphIndex);
        maybeAddSuboptimal(ctx, s.rawKey, s.rawVlabels, s.rawEdges, rawVerts, s.cr.key, ex.graphIndex);
    }

    /**
     * Join case 1 (both operands inner, same parent): folds {@code my}'s
     * distinguishing edge into {@code mx}'s matrix. Embeddings are the exact
     * intersection of both operands' embedding sets.
     *
     * @param mx  first inner pool member
     * @param my  second inner pool member
     * @param ctx shared per-level growth context
     */
    private static void buildCase1(PoolMember mx, PoolMember my, GrowthContext ctx) {

        if (mx.lastEdgeTarget == my.lastEdgeTarget) return;

        int m    = mx.pattern.vlabels.length;
        int last = m - 1;

        int[] rawVlabels = mx.pattern.vlabels;

        int[][] rawEdges = new int[mx.pattern.edges.length + 1][];
        System.arraycopy(mx.pattern.edges, 0, rawEdges, 0, mx.pattern.edges.length);
        rawEdges[mx.pattern.edges.length] =
                new int[]{Math.min(last, my.lastEdgeTarget), Math.max(last, my.lastEdgeTarget),
                          my.lastEdgeLabel};

        FFSMPattern.IntArrayKey rawKey =
                new FFSMPattern.IntArrayKey(FFSMPattern.buildRawKey(rawVlabels, rawEdges));
        FFSMPattern.CanonResult cr =
                FFSMPattern.canonicalizeCached(ctx.canonCache, rawKey, rawVlabels, rawEdges);
        FFSMPattern.IntArrayKey ck = cr.candidateKey();

        Set<FFSMPattern.EmbeddingKey> myEmbKeys = my.embeddingKeys();

        for (FFSMPattern.Embedding ex : mx.pattern.embeddings) {
            if (!myEmbKeys.contains(embeddingKey(ex))) continue; // keep only shared embeddings

            addToCandidateMap(ctx.candidateMap, cr, ck, ex.vertices, ex.graphIndex);
            maybeAddSuboptimal(ctx, rawKey, rawVlabels, rawEdges, ex.vertices, cr.key, ex.graphIndex);
        }
    }

    /**
     * Join case 2 (one inner, one outer operand, same parent): combines the outer
     * sibling's structure (pendant included) with the inner sibling's extra edge. An
     * outer embedding is kept only if its parent-sized prefix already matches an
     * embedding of the inner sibling.
     *
     * @param inner inner pool member
     * @param outer outer pool member
     * @param ctx   shared per-level growth context
     */
    private static void buildCase2(PoolMember inner, PoolMember outer, GrowthContext ctx) {

        int m = inner.pattern.vlabels.length;
        int n = outer.pattern.vlabels.length;
        if (n != m + 1) return; // defensive: shared parent key already guarantees this

        int last = m - 1;

        int[] rawVlabels = outer.pattern.vlabels;

        int[][] rawEdges = new int[outer.pattern.edges.length + 1][];
        System.arraycopy(outer.pattern.edges, 0, rawEdges, 0, outer.pattern.edges.length);
        rawEdges[outer.pattern.edges.length] =
                new int[]{Math.min(last, inner.lastEdgeTarget), Math.max(last, inner.lastEdgeTarget),
                          inner.lastEdgeLabel};

        FFSMPattern.IntArrayKey rawKey =
                new FFSMPattern.IntArrayKey(FFSMPattern.buildRawKey(rawVlabels, rawEdges));
        FFSMPattern.CanonResult cr =
                FFSMPattern.canonicalizeCached(ctx.canonCache, rawKey, rawVlabels, rawEdges);
        FFSMPattern.IntArrayKey ck = cr.candidateKey();

        Set<FFSMPattern.EmbeddingKey> innerEmbKeys = inner.embeddingKeys();

        for (FFSMPattern.Embedding eo : outer.pattern.embeddings) {
            if (!innerEmbKeys.contains(new FFSMPattern.EmbeddingKey(eo.graphIndex, eo.vertices, m)))
                continue; // outer's parent-sized prefix must match an inner embedding

            addToCandidateMap(ctx.candidateMap, cr, ck, eo.vertices, eo.graphIndex);
            maybeAddSuboptimal(ctx, rawKey, rawVlabels, rawEdges, eo.vertices, cr.key, eo.graphIndex);
        }
    }

    /** A raw, explicitly-numbered (vlabels, edges) pair not yet wrapped in a Pattern. */
    private static final class RawGraph {
        /** Vertex labels. */
        final int[]   vlabels;
        /** Edges as (v1, v2, label) triples. */
        final int[][] edges;
        RawGraph(int[] vlabels, int[][] edges) { this.vlabels = vlabels; this.edges = edges; }
    }

    /**
     * Computes the maximal proper submatrix of a (vlabels, edges) pair by deleting
     * the last vertex if it has degree &lt;= 1, or just its rightmost edge otherwise.
     *
     * @param vlabels vertex labels
     * @param edges   edges
     * @return the resulting submatrix
     */
    private static RawGraph maximalProperSubmatrix(int[] vlabels, int[][] edges) {
        int n    = vlabels.length;
        int last = n - 1;
        int degree = 0, target = -1;
        for (int[] e : edges) {
            if (e[0] == last || e[1] == last) {
                int other = (e[0] == last) ? e[1] : e[0];
                degree++;
                if (other > target) target = other;
            }
        }
        return buildParentRawGraph(vlabels, edges, degree <= 1, target);
    }

    /**
     * Builds the maximal proper submatrix of a (vlabels, edges) pair: deletes the
     * last vertex when {@code outer}, or just its edge to {@code lastEdgeTarget}
     * otherwise.
     *
     * @param vlabels        vertex labels
     * @param edges          edges
     * @param outer          whether the last vertex is a pendant
     * @param lastEdgeTarget target of the edge to remove when {@code outer} is false
     * @return the resulting submatrix
     */
    private static RawGraph buildParentRawGraph(int[] vlabels, int[][] edges,
                                                 boolean outer, int lastEdgeTarget) {
        int n    = vlabels.length;
        int last = n - 1;
        if (outer) {
            // drop the last vertex and every edge touching it
            int[] parentVlabels = new int[n - 1];
            System.arraycopy(vlabels, 0, parentVlabels, 0, n - 1);

            int keep = 0;
            for (int[] e : edges) if (e[0] != last && e[1] != last) keep++;
            int[][] parentEdges = new int[keep][];
            int idx = 0;
            for (int[] e : edges) if (e[0] != last && e[1] != last) parentEdges[idx++] = e;

            return new RawGraph(parentVlabels, parentEdges);
        } else {
            // keep every vertex, drop just the last edge
            int keep = 0;
            for (int[] e : edges) {
                boolean isLastEdge = (e[0] == last && e[1] == lastEdgeTarget)
                                  || (e[1] == last && e[0] == lastEdgeTarget);
                if (!isLastEdge) keep++;
            }
            int[][] parentEdges = new int[keep][];
            int idx = 0;
            for (int[] e : edges) {
                boolean isLastEdge = (e[0] == last && e[1] == lastEdgeTarget)
                                  || (e[1] == last && e[0] == lastEdgeTarget);
                if (!isLastEdge) parentEdges[idx++] = e;
            }
            return new RawGraph(vlabels, parentEdges);
        }
    }

    /**
     * Checks whether a candidate structure is a genuine suboptimal CAM: its maximal
     * proper submatrix must already be canonical.
     *
     * @param vlabels    vertex labels
     * @param edges      edges
     * @param canonCache shared canonicalization cache
     * @return true if the candidate is a genuine suboptimal CAM
     */
    private static boolean isGenuineSuboptimalCam(int[] vlabels, int[][] edges,
                                                   Map<FFSMPattern.IntArrayKey, FFSMPattern.CanonResult> canonCache) {
        RawGraph parent = maximalProperSubmatrix(vlabels, edges);
        int[] parentIdCode = FFSMPattern.identityCode(parent.vlabels, parent.edges);
        FFSMPattern.CanonResult parentCanon =
                FFSMPattern.canonicalizeCached(canonCache, parent.vlabels, parent.edges);
        return FFSMPattern.compareIntArrays(parentIdCode, parentCanon.key) == 0;
    }

    /**
     * Records a freshly-built candidate in {@code ctx.subMap} if it is not already
     * canonical and passes the suboptimal-CAM check, caching the eligibility result
     * per raw structure.
     *
     * @param ctx        shared per-level growth context
     * @param rawKey     raw memoization key of the candidate
     * @param rawVlabels candidate vertex labels
     * @param rawEdges   candidate edges
     * @param rawVerts   embedding vertex tuple to record
     * @param canonKey   canonical code of the candidate
     * @param graphIndex index of the graph the embedding comes from
     */
    private static void maybeAddSuboptimal(GrowthContext ctx,
                                            FFSMPattern.IntArrayKey rawKey,
                                            int[] rawVlabels, int[][] rawEdges, int[] rawVerts,
                                            int[] canonKey, int graphIndex) {
        Boolean cachedEligible = ctx.subCheckCache.get(rawKey);
        boolean eligible;
        int[]   idCode = null;
        if (cachedEligible != null) {
            eligible = cachedEligible;
        } else {
            idCode   = FFSMPattern.identityCode(rawVlabels, rawEdges);
            eligible = FFSMPattern.compareIntArrays(idCode, canonKey) != 0 // not already canonical
                       && isGenuineSuboptimalCam(rawVlabels, rawEdges, ctx.canonCache);
            ctx.subCheckCache.put(rawKey, eligible);
        }
        if (!eligible) return;

        CandidateEntry entry = ctx.subMap.get(rawKey);
        if (entry == null) {
            entry          = new CandidateEntry();
            entry.vlabels  = rawVlabels;
            entry.edges    = rawEdges;
            entry.canonKey = idCode;
            ctx.subMap.put(rawKey, entry);
        }
        FFSMPattern.EmbeddingKey embKey = embeddingKey(graphIndex, rawVerts);
        if (entry.seenEmbeddings.add(embKey)) {
            entry.embeddings.add(new FFSMPattern.Embedding(graphIndex, rawVerts));
        }
    }

    /**
     * Builds the identity key of an embedding.
     *
     * @param graphIndex index of the graph
     * @param vertices   vertex tuple of the embedding
     * @return the identity key
     */
    private static FFSMPattern.EmbeddingKey embeddingKey(int graphIndex, int[] vertices) {
        return new FFSMPattern.EmbeddingKey(graphIndex, vertices);
    }

    /**
     * Builds the identity key of an embedding.
     *
     * @param e the embedding
     * @return the identity key
     */
    private static FFSMPattern.EmbeddingKey embeddingKey(FFSMPattern.Embedding e) {
        return embeddingKey(e.graphIndex, e.vertices);
    }

    /**
     * Inserts a candidate's embedding(s) into the accumulator, creating the entry on
     * first sight. One embedding is added per vertex ordering tied for the maximum
     * code, so patterns with a non-trivial automorphism get every required embedding.
     *
     * @param candidateMap accumulator map
     * @param cr           canonicalization result of the candidate
     * @param rawVerts     as-constructed embedding vertex tuple
     * @param graphIndex   index of the graph the embedding comes from
     */
    private static void addToCandidateMap(Map<FFSMPattern.IntArrayKey, CandidateEntry> candidateMap,
                                           FFSMPattern.CanonResult cr,
                                           int[] rawVerts,
                                           int graphIndex) {
        addToCandidateMap(candidateMap, cr, cr.candidateKey(), rawVerts, graphIndex);
    }

    /**
     * Same as the four-argument overload, but with the candidate-map lookup key
     * already computed by the caller.
     *
     * @param candidateMap accumulator map
     * @param cr           canonicalization result of the candidate
     * @param ck           candidate-map lookup key for {@code cr}
     * @param rawVerts     as-constructed embedding vertex tuple
     * @param graphIndex   index of the graph the embedding comes from
     */
    private static void addToCandidateMap(Map<FFSMPattern.IntArrayKey, CandidateEntry> candidateMap,
                                           FFSMPattern.CanonResult cr,
                                           FFSMPattern.IntArrayKey ck,
                                           int[] rawVerts,
                                           int graphIndex) {
        CandidateEntry entry = candidateMap.get(ck);
        if (entry == null) {
            entry          = new CandidateEntry();
            entry.vlabels  = cr.vlabels;
            entry.edges    = cr.edges;
            entry.canonKey = cr.key;
            candidateMap.put(ck, entry);
        }

        for (int[] order : cr.allOrders) { // one embedding per automorphic ordering
            int[] canonVerts = new int[order.length];
            for (int t = 0; t < order.length; t++) canonVerts[t] = rawVerts[order[t]];

            FFSMPattern.EmbeddingKey embKey = embeddingKey(graphIndex, canonVerts);
            if (entry.seenEmbeddings.add(embKey)) {
                entry.embeddings.add(new FFSMPattern.Embedding(graphIndex, canonVerts));
            }
        }
    }

    /**
     * Builds the support bit set (one bit per distinct supporting graph index) of a
     * candidate's embedding list.
     *
     * @param embeddings embeddings to summarize
     * @return the support bit set
     */
    private static BitSet supportBitSet(List<FFSMPattern.Embedding> embeddings) {
        BitSet tidBits = new BitSet();
        for (FFSMPattern.Embedding e : embeddings) tidBits.set(e.graphIndex);
        return tidBits;
    }

    /**
     * Filters a candidate map down to frequent, output-eligible patterns, building
     * full {@link FFSMPattern.Pattern}s including a sorted tidlist.
     *
     * @param map      candidate accumulator
     * @param graphs   graph transactions (for id lookup)
     * @param minCount minimum number of supporting transactions
     * @return frequent patterns, sorted by canonical code
     */
    private static List<FFSMPattern.Pattern> filterFrequent(Map<FFSMPattern.IntArrayKey, CandidateEntry> map,
                                                              List<FFSMGraph.Graph> graphs,
                                                              double minCount) {
        List<FFSMPattern.Pattern> result = new ArrayList<>();
        for (CandidateEntry entry : map.values()) {
            BitSet tidBits = supportBitSet(entry.embeddings);
            int    card    = tidBits.cardinality();
            if (card < minCount) continue; // discard infrequent candidates

            List<Integer> tidlist = new ArrayList<>(card);
            for (int idx = tidBits.nextSetBit(0); idx >= 0; idx = tidBits.nextSetBit(idx + 1))
                tidlist.add(graphs.get(idx).id);
            Collections.sort(tidlist);

            result.add(new FFSMPattern.Pattern(entry.vlabels, entry.edges, entry.embeddings,
                                                tidlist, entry.canonKey));
        }
        result.sort((a, b) -> FFSMPattern.compareIntArrays(a.canonKey, b.canonKey));
        return result;
    }

    /**
     * Filters a candidate map down to frequent patterns kept only as join-only pool
     * material; unlike {@link #filterFrequent}, no tidlist is built since these
     * patterns are never added to the output set.
     *
     * @param map      candidate accumulator
     * @param minCount minimum number of supporting transactions
     * @return frequent patterns, sorted by canonical code
     */
    private static List<FFSMPattern.Pattern> filterFrequentPoolOnly(Map<FFSMPattern.IntArrayKey, CandidateEntry> map,
                                                                      double minCount) {
        List<FFSMPattern.Pattern> result = new ArrayList<>();
        for (CandidateEntry entry : map.values()) {
            if (supportBitSet(entry.embeddings).cardinality() < minCount) continue;

            result.add(new FFSMPattern.Pattern(entry.vlabels, entry.edges, entry.embeddings,
                                                Collections.emptyList(), entry.canonKey));
        }
        result.sort((a, b) -> FFSMPattern.compareIntArrays(a.canonKey, b.canonKey));
        return result;
    }

    /**
     * Builds the child pool of a pool member by joining it with every member of its
     * pool and extending it by one pendant vertex, keeping only frequent results
     * that are either canonical or genuine suboptimal CAMs.
     *
     * @param X        pool member to grow
     * @param U        pool containing {@code X}
     * @param graphs   graph transactions to mine
     * @param minCount minimum number of supporting transactions
     * @return the resulting child pool, sorted by canonical code
     */
    private static List<PoolMember> buildChildren(PoolMember X, List<PoolMember> U,
                                                   List<FFSMGraph.Graph> graphs, double minCount) {

        GrowthContext ctx = new GrowthContext();

        for (PoolMember Y : U) {
            tryJoinPair(X, Y, ctx); // join X with every sibling
        }
        extendByPendant(X.pattern, X.outer, graphs, ctx); // extend X by one pendant vertex

        List<FFSMPattern.Pattern> canonicalChildren  = filterFrequent(ctx.candidateMap, graphs, minCount);
        List<FFSMPattern.Pattern> suboptimalChildren = filterFrequentPoolOnly(ctx.subMap, minCount);

        List<PoolMember> C = new ArrayList<>();
        for (FFSMPattern.Pattern p : canonicalChildren) {
            PoolMember m = buildPoolMember(p, true);
            if (m != null) C.add(m);
        }
        for (FFSMPattern.Pattern p : suboptimalChildren) {
            PoolMember m = buildPoolMember(p, false);
            if (m != null) C.add(m);
        }
        C.sort((a, b) -> FFSMPattern.compareIntArrays(a.pattern.canonKey, b.pattern.canonKey));
        return C;
    }

    /** One level of the explicit-stack DFS performed by {@link #ffsmExplore}. */
    private static final class Frame {
        /** Pool being traversed at this level. */
        final List<PoolMember> U;
        /** Index of the next pool member to process. */
        int index;
        Frame(List<PoolMember> U) { this.U = U; }
    }

    /**
     * Depth-first traversal of the join/extension recursion (FFSM-Explore): every
     * genuine-CAM pool member is added to the output, its children are built, and the
     * traversal descends into them before moving to the next member. Implemented
     * iteratively with an explicit stack so mining depth is not limited by the JVM
     * call stack.
     *
     * @param rootU    initial pool (frequent single edges)
     * @param graphs   all graph transactions
     * @param minCount minimum number of supporting transactions
     * @param output   set of discovered patterns, keyed by canonical form, mutated in place
     */
    private static void ffsmExplore(List<PoolMember> rootU,
                                     List<FFSMGraph.Graph> graphs,
                                     double minCount,
                                     Map<FFSMPattern.IntArrayKey, FFSMPattern.Pattern> output) {
        Deque<Frame> stack = new ArrayDeque<>();
        stack.push(new Frame(rootU));

        while (!stack.isEmpty()) {
            Frame top = stack.peek();
            if (top.index >= top.U.size()) {
                stack.pop(); // done with this level
                continue;
            }
            PoolMember X = top.U.get(top.index++);
            if (!X.canonical) continue; // join-only member: never output or grown further

            output.putIfAbsent(new FFSMPattern.IntArrayKey(X.pattern.canonKey), X.pattern);

            List<PoolMember> C = buildChildren(X, top.U, graphs, minCount);
            stack.push(new Frame(C)); // descend depth-first before resuming this level
        }
    }

    /**
     * Top-level FFSM procedure: computes frequent vertices and edges, then runs the
     * join/extension recursion starting from the frequent edges.
     *
     * @param graphs             graph transactions to mine
     * @param minSupportFraction minimum support as a fraction of the number of graphs
     * @return groups of frequent patterns: single vertices (if any), then frequent
     *         edges, then every larger pattern in discovery order
     */
    static List<List<FFSMPattern.Pattern>> ffsm(List<FFSMGraph.Graph> graphs,
                                                  double minSupportFraction) {
        double minCount = minSupportFraction * graphs.size() - 1e-9;

        List<List<FFSMPattern.Pattern>> allLevels = new ArrayList<>();

        List<FFSMPattern.Pattern> F0 = seedVertices(graphs, minCount);
        if (!F0.isEmpty()) allLevels.add(F0);

        List<FFSMPattern.Pattern> F1 = seedEdges(graphs, minCount);
        if (F1.isEmpty()) return allLevels;

        List<PoolMember> P = new ArrayList<>();
        Map<FFSMPattern.IntArrayKey, FFSMPattern.Pattern> discovered = new LinkedHashMap<>();
        for (FFSMPattern.Pattern p : F1) {
            PoolMember m = buildPoolMember(p, true);
            if (m != null) P.add(m);
            discovered.putIfAbsent(new FFSMPattern.IntArrayKey(p.canonKey), p); // pre-seed output with F1
        }
        ffsmExplore(P, graphs, minCount, discovered);

        if (!discovered.isEmpty()) allLevels.add(new ArrayList<>(discovered.values()));
        return allLevels;
    }

    /**
     * Writes all discovered patterns to a "t/v/e/x" text file, always including the
     * ids of the graphs containing each pattern (equivalent to calling
     * {@link #writeOutput(List, String, boolean)} with {@code true}).
     *
     * @param allLevels groups of discovered patterns to write
     * @param filename  destination file path
     * @throws IOException if the file cannot be written
     */
    static void writeOutput(List<List<FFSMPattern.Pattern>> allLevels,
                             String filename) throws IOException {
        writeOutput(allLevels, filename, true);
    }

    /**
     * Writes all discovered patterns to a "t/v/e/x" text file.
     *
     * @param allLevels      groups of discovered patterns to write
     * @param filename       destination file path
     * @param outputGraphIds if true, an "x" line listing the ids of the graphs
     *                       containing each pattern is written after its edges
     * @throws IOException if the file cannot be written
     */
    static void writeOutput(List<List<FFSMPattern.Pattern>> allLevels,
                             String filename, boolean outputGraphIds) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(filename)))) {
    		bw.write("@FILETYPE=\"Frequent subgraphs\"");
    		bw.append(System.lineSeparator());
    		bw.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
    		bw.append(System.lineSeparator());
    		
            int           idx   = 0;
            boolean       first = true;
            StringBuilder sb    = new StringBuilder();
            for (List<FFSMPattern.Pattern> level : allLevels) {
                for (FFSMPattern.Pattern pat : level) {
                    sb.setLength(0);
                    if (!first) sb.append('\n');
                    first = false;
                    sb.append("t # ").append(idx)
                      .append(" * ").append(pat.tidlist.size()).append('\n');
                    for (int vid = 0; vid < pat.vlabels.length; vid++) // write vertices
                        sb.append("v ").append(vid).append(' ')
                          .append(pat.vlabels[vid]).append('\n');
                    for (int[] e : pat.edges) // write edges
                        sb.append("e ").append(e[0]).append(' ').append(e[1])
                          .append(' ').append(e[2]).append('\n');
                    // If the user chose to output the graph ids where the frequent
                    // subgraph appears, we output it
                    if (outputGraphIds) {
                        sb.append('x');
                        for (int gid : pat.tidlist) sb.append(' ').append(gid); // write tidlist
                        sb.append('\n');
                    }
                    bw.append(sb);
                    idx++;
                }
            }
        }
    }
}