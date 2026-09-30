package ca.pfv.spmf.algorithms.graph_mining.fsg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
 * Holds the frequent-pattern representation ({@link Pattern}) and all of the canonical-form
 * machinery ({@link CanonResult}, {@link IntArrayKey}, {@link #canonicalize}) used by
 * {@link AlgoFSG} to recognize equivalent subgraphs and to enforce the downward-closure test
 * during candidate growth, based on Kuramochi &amp; Karypis, "Frequent Subgraph Discovery",
 * ICDM 2001. This class is only a namespace for the related static types below; it is not
 * meant to be instantiated.
 * 
 * @author Chen Hui
 */
public class FSGPattern {

    /** Not meant to be instantiated: this class only groups related static types. */
    private FSGPattern() { }

    /**
     * A frequent pattern (connected subgraph) discovered by the algorithm, together with
     * the list of graph transactions that support it and its canonical form key.
     */
    static class Pattern {
        /** Vertex labels of this pattern. */
        int[]         vlabels;
        /** Edges of this pattern as (v1, v2, label) triples. */
        int[][]       edges;
        /** Sorted list of ids of graphs containing this pattern (used for reporting/output). */
        List<Integer> tidlist;
        /** Support set as a {@link BitSet}, indexed by a graph's dense position in the graph
         *  list (not by its input id). Used for all internal intersection/union work (the
         *  downward-closure test in {@code AlgoFSG.grow}) since word-at-a-time bitset
         *  operations are far cheaper than boxed-{@code Integer} hash/tree set operations. */
        BitSet        tidBits;
        /** Canonical form key used to identify equivalent patterns. */
        int[]         canonKey;
        /** Lazily built set of endpoint keys for this pattern's edges. */
        private Set<Long> edgeKeySet;

        /**
         * Creates a new pattern.
         * @param vlabels vertex labels
         * @param edges   edges as (v1, v2, label) triples
         * @param tidlist sorted list of ids of the graphs supporting this pattern
         * @param tidBits dense-index bitset of the graphs supporting this pattern
         * @param canonKey canonical form key of this pattern
         */
        Pattern(int[] vlabels, int[][] edges, List<Integer> tidlist, BitSet tidBits, int[] canonKey) {
            this.vlabels  = vlabels;
            this.edges    = edges;
            this.tidlist  = tidlist;
            this.tidBits  = tidBits;
            this.canonKey = canonKey;
        }

        /**
         * Lazily-cached set of {@code min<<32|max} endpoint keys for this pattern's edges,
         * used by {@code AlgoFSG.grow} to test whether a proposed extension edge already exists.
         * @return set of endpoint keys for this pattern's edges
         */
        Set<Long> edgeKeySet() {
            if (edgeKeySet == null) {
                edgeKeySet = new HashSet<>();
                for (int[] e : edges) {
                    int a = Math.min(e[0], e[1]), b = Math.max(e[0], e[1]);
                    edgeKeySet.add(((long) a << 32) | (b & 0xFFFFFFFFL));
                }
            }
            return edgeKeySet;
        }
    }

    /** A pattern graph with isolated vertices removed and vertices re-indexed to {@code 0..m-1}. */
    static class StrippedGraph {
        /** Vertex labels after removing isolated vertices. */
        int[]   vlabels;
        /** Edges after re-indexing vertices to {@code 0..m-1}. */
        int[][] edges;
    }

    /** Wraps an {@code int[]} so it can be used as a hash map key by value. */
    static final class IntArrayKey {
        /** Underlying array wrapped by this key. */
        final int[] key;
        /** Precomputed hash code of {@link #key}. */
        private final int hash;

        /**
         * Creates a key wrapping the given array.
         * @param key array to wrap
         */
        IntArrayKey(int[] key) { this.key = key; this.hash = Arrays.hashCode(key); }

        /**
         * Compares this key to another by array content.
         * @param o object to compare against
         * @return true if {@code o} is an {@code IntArrayKey} with equal content
         */
        @Override public boolean equals(Object o) {
            return this == o || (o instanceof IntArrayKey && Arrays.equals(key, ((IntArrayKey) o).key));
        }

        /**
         * Returns the precomputed hash code of the wrapped array.
         * @return hash code
         */
        @Override public int hashCode() { return hash; }
    }

    /** Result of canonicalizing a pattern graph: the comparison key plus the relabeled graph. */
    public static class CanonResult {
        /** Canonical form comparison key. */
        public int[]   key;
        /** Vertex labels in canonical order. */
        int[]   vlabels;
        /** Edges renumbered according to the canonical order. */
        int[][] edges;
    }

    /**
     * Mutable holder carrying the best canonical key/order found so far while exploring
     * vertex orderings in {@link #canonicalize}, so it can be threaded through the
     * recursive permutation search without repeated allocation.
     */
    private static final class CanonSearchState {
        /** Smallest key found so far. */
        int[]    bestKey;
        /** Vertex ordering that produced {@link #bestKey}. */
        int[]    bestOrder;
        /** Whether at least one ordering has been evaluated yet. */
        boolean  found;
    }

    /**
     * Lexicographically compares two int arrays, treating a shorter array as smaller
     * when one is a prefix of the other.
     * @param a first array
     * @param b second array
     * @return negative, zero or positive as {@code a} is less than, equal to, or greater than {@code b}
     */
    static int compareIntArrays(int[] a, int[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        return Integer.compare(a.length, b.length);
    }

    /**
     * Computes the canonical form of a pattern graph by trying every vertex ordering
     * consistent with the (label, degree) partition and keeping the smallest resulting key.
     * <p>
     * Orderings are generated and evaluated one at a time via {@link #permuteBlocks} /
     * {@link #permuteInPlace} (in-place, Heap's-algorithm-style permutation generation)
     * instead of first materializing every block permutation and their full cartesian
     * product into lists: for patterns with large automorphism groups this avoids
     * allocating and holding in memory the entire (potentially huge) set of candidate
     * orderings at once.
     * @param vlabels vertex labels
     * @param edges   edges as (v1, v2, label) triples
     * @return the canonical key together with the relabeled vertices and edges
     */
    public static CanonResult canonicalize(int[] vlabels, int[][] edges) {
        int n = vlabels.length;
        CanonResult res = new CanonResult();
        if (n == 0) {
            res.key = new int[0]; res.vlabels = new int[0]; res.edges = new int[0][];
            return res;
        }
        // build the adjacency matrix and vertex degrees
        int[][] adjMatrix = new int[n][n];
        for (int[] row : adjMatrix) Arrays.fill(row, -1);
        int[] degree = new int[n];
        for (int[] e : edges) {
            adjMatrix[e[0]][e[1]] = e[2]; adjMatrix[e[1]][e[0]] = e[2];
            degree[e[0]]++; degree[e[1]]++;
        }
        // partition vertices by (label, degree) to limit the orderings to try
        Map<Long, List<Integer>> groups = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            long key = ((long) vlabels[i] << 32) | (degree[i] & 0xFFFFFFFFL);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
        }
        int[][] blocks = new int[groups.size()][];
        int bi = 0;
        for (List<Integer> g : groups.values()) {
            int[] arr = new int[g.size()];
            for (int i = 0; i < g.size(); i++) arr[i] = g.get(i);
            blocks[bi++] = arr;
        }

        // try every ordering compatible with the partition and keep the smallest key
        int upperCount = n * (n - 1) / 2;
        int[] order       = new int[n];
        int[] scratchKey  = new int[n + upperCount];
        CanonSearchState state = new CanonSearchState();
        state.bestKey   = new int[n + upperCount];
        state.bestOrder = new int[n];
        state.found     = false;

        permuteBlocks(blocks, 0, order, 0, adjMatrix, vlabels, scratchKey, state);

        // rebuild vertex labels and edges under the best ordering found
        int[] bestOrder = state.bestOrder;
        int[] canonVlabels = new int[n];
        for (int i = 0; i < n; i++) canonVlabels[i] = vlabels[bestOrder[i]];
        List<int[]> canonEdgesList = new ArrayList<>();
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                if (adjMatrix[bestOrder[i]][bestOrder[j]] != -1)
                    canonEdgesList.add(new int[]{i, j, adjMatrix[bestOrder[i]][bestOrder[j]]});
        res.key     = state.bestKey;
        res.vlabels = canonVlabels;
        res.edges   = canonEdgesList.toArray(new int[0][]);
        return res;
    }

    /**
     * Recursively assigns full vertex orderings block by block. For each block, every
     * permutation of its vertices is generated in place (via {@link #permuteInPlace}) and
     * copied into {@code order}; once all blocks have been assigned, the resulting
     * complete ordering is evaluated directly (see {@link #evaluateOrder}). This streams
     * through every valid ordering without ever materializing the full cartesian product.
     * @param blocks     vertex groups (by label/degree) whose internal order may vary
     * @param blockIdx   index of the block currently being placed
     * @param order      ordering being assembled (reused across the whole search)
     * @param pos        next free position in {@code order}
     * @param adjMatrix  adjacency-label matrix of the pattern ({@code -1} = no edge)
     * @param vlabels    vertex labels of the pattern
     * @param scratchKey reusable buffer used to build each candidate key
     * @param state      running best key/order found so far
     */
    private static void permuteBlocks(int[][] blocks, int blockIdx, int[] order, int pos,
                                       int[][] adjMatrix, int[] vlabels, int[] scratchKey,
                                       CanonSearchState state) {
        if (blockIdx == blocks.length) {
            evaluateOrder(order, adjMatrix, vlabels, scratchKey, state);
            return;
        }
        int[] work = blocks[blockIdx].clone();
        permuteInPlace(work, work.length, blocks, blockIdx, order, pos,
                        adjMatrix, vlabels, scratchKey, state);
    }

    /**
     * Generates every permutation of {@code work[0..k-1]} in place, using the classic
     * Heap's algorithm swap pattern, copying each completed permutation into {@code order}
     * and recursing into the next block - instead of collecting permutations into
     * intermediate lists as an earlier implementation did.
     * @param work       block being permuted (mutated in place via swaps)
     * @param k          number of leading elements of {@code work} still to be permuted
     * @param blocks     all vertex groups
     * @param blockIdx   index of the block currently being permuted
     * @param order      ordering being assembled
     * @param pos        position in {@code order} where {@code work} should be copied
     * @param adjMatrix  adjacency-label matrix of the pattern
     * @param vlabels    vertex labels of the pattern
     * @param scratchKey reusable buffer used to build each candidate key
     * @param state      running best key/order found so far
     */
    private static void permuteInPlace(int[] work, int k, int[][] blocks, int blockIdx,
                                        int[] order, int pos, int[][] adjMatrix, int[] vlabels,
                                        int[] scratchKey, CanonSearchState state) {
        if (k == 1) {
            System.arraycopy(work, 0, order, pos, work.length);
            permuteBlocks(blocks, blockIdx + 1, order, pos + work.length,
                          adjMatrix, vlabels, scratchKey, state);
            return;
        }
        for (int i = 0; i < k; i++) {
            permuteInPlace(work, k - 1, blocks, blockIdx, order, pos,
                           adjMatrix, vlabels, scratchKey, state);
            if (k % 2 == 0) { int t = work[i];    work[i]    = work[k - 1]; work[k - 1] = t; }
            else             { int t = work[0];    work[0]    = work[k - 1]; work[k - 1] = t; }
        }
    }

    /**
     * Evaluates one fully-assembled vertex ordering: builds its key (vertex labels
     * followed by the flattened upper-triangular adjacency matrix under that ordering)
     * and keeps it if it is smaller than the best key found so far.
     * @param order     the complete ordering to evaluate
     * @param adjMatrix adjacency-label matrix of the pattern
     * @param vlabels   vertex labels of the pattern
     * @param key       reusable buffer to build the candidate key into
     * @param state     running best key/order found so far (updated in place)
     */
    private static void evaluateOrder(int[] order, int[][] adjMatrix, int[] vlabels,
                                       int[] key, CanonSearchState state) {
        int n = order.length;
        for (int i = 0; i < n; i++) key[i] = vlabels[order[i]];
        int idx = n;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                key[idx++] = adjMatrix[order[i]][order[j]];
        if (!state.found || compareIntArrays(key, state.bestKey) < 0) {
            state.found = true;
            System.arraycopy(key, 0, state.bestKey, 0, key.length);
            System.arraycopy(order, 0, state.bestOrder, 0, n);
        }
    }

    /**
     * Builds a raw memoization key for an already-stripped sub-pattern whose edges may be
     * in any order (stripping renumbers vertices, so the parent pattern's sorted-edge
     * invariant used by {@link #buildRawCanonicalizationKey} does not apply here).
     * @param vlabels vertex labels of the stripped sub-pattern
     * @param edges   edges of the stripped sub-pattern
     * @return a raw key suitable for use with {@link #canonicalizeCached}
     */
    static int[] buildRawKeyForStripped(int[] vlabels, int[][] edges) {
        int[][] sorted = edges.clone();
        Arrays.sort(sorted, (a, b) -> {
            if (a[0] != b[0]) return Integer.compare(a[0], b[0]);
            if (a[1] != b[1]) return Integer.compare(a[1], b[1]);
            return Integer.compare(a[2], b[2]);
        });
        int[] key = new int[vlabels.length + sorted.length * 3];
        System.arraycopy(vlabels, 0, key, 0, vlabels.length);
        int idx = vlabels.length;
        for (int[] e : sorted) { key[idx++] = e[0]; key[idx++] = e[1]; key[idx++] = e[2]; }
        return key;
    }

    /**
     * Builds the memoization key for a full candidate (parent pattern's edges plus one
     * new edge). {@code baseEdges} are already sorted by {@code (v1,v2,label)}, so the new
     * edge is inserted into its sorted position in a single linear pass.
     * @param vlabels   vertex labels of the candidate
     * @param baseEdges sorted edges of the parent pattern
     * @param newEdge   the single new edge being added
     * @return a raw key suitable for use with {@link #canonicalizeCached}
     */
    static int[] buildRawCanonicalizationKey(int[] vlabels, int[][] baseEdges, int[] newEdge) {
        int insertPos = baseEdges.length; // default: new edge sorts after everything
        for (int i = 0; i < baseEdges.length; i++) {
            int[] e = baseEdges[i];
            if (newEdge[0] < e[0]
                    || (newEdge[0] == e[0] && newEdge[1] < e[1])
                    || (newEdge[0] == e[0] && newEdge[1] == e[1] && newEdge[2] < e[2])) {
                insertPos = i;
                break;
            }
        }
        int totalEdges = baseEdges.length + 1;
        int[] key      = new int[vlabels.length + totalEdges * 3];
        System.arraycopy(vlabels, 0, key, 0, vlabels.length);
        int ki = vlabels.length;
        for (int i = 0; i < totalEdges; i++) {
            int[] e = (i < insertPos) ? baseEdges[i]
                    : (i == insertPos) ? newEdge
                    : baseEdges[i - 1];
            key[ki++] = e[0]; key[ki++] = e[1]; key[ki++] = e[2];
        }
        return key;
    }

    /**
     * Memoized {@link #canonicalize}, scoped to one {@code AlgoFSG.grow} call. The caller
     * supplies the raw key (built by {@link #buildRawKeyForStripped} or
     * {@link #buildRawCanonicalizationKey}) so it is only ever built once.
     * @param cache   memoization cache
     * @param rawKey  raw key identifying the (vlabels, edges) pair
     * @param vlabels vertex labels to canonicalize
     * @param edges   edges to canonicalize
     * @return the canonical form, computed once and cached for subsequent calls
     */
    static CanonResult canonicalizeCached(Map<IntArrayKey, CanonResult> cache,
                                           IntArrayKey rawKey,
                                           int[] vlabels, int[][] edges) {
        CanonResult cached = cache.get(rawKey);
        if (cached != null) return cached;
        CanonResult cr = canonicalize(vlabels, edges);
        cache.put(rawKey, cr);
        return cr;
    }

    /**
     * Checks whether the edge-touched vertices of {@code edges[0..count-1]}, excluding the
     * edge at {@code skipIdx}, form a single connected component.
     * <p>
     * Operates directly on the caller's (reused) edge buffer with a skip index instead of
     * requiring a pre-shrunk copy of the edge array with that edge removed: this method
     * used to be fed the output of a {@code removeEdgeAt} helper that allocated a new
     * array on every call from the innermost downward-closure-test loop.
     * @param edges   edge buffer, only indices {@code [0,count)} excluding {@code skipIdx} are considered
     * @param count   number of valid edges in {@code edges} (including the one to skip)
     * @param skipIdx index of the edge to exclude
     * @param maxVid  highest possible vertex id (pattern vertices are always {@code 0..maxVid})
     * @return true if all edge-touched vertices are connected
     */
    static boolean isConnectedIgnoringIsolated(int[][] edges, int count, int skipIdx, int maxVid) {
        int m = count - 1;
        if (m <= 0) return false;

        boolean[] withEdge = new boolean[maxVid + 1];

        // build a compact singly-linked adjacency list (head/next/to arrays) once, skipping
        // the excluded edge index directly instead of first materializing a shrunk copy
        int[] head = new int[maxVid + 1];
        Arrays.fill(head, -1);
        int[] next = new int[m * 2];
        int[] to   = new int[m * 2];
        int   ptr  = 0;
        for (int i = 0; i < count; i++) {
            if (i == skipIdx) continue;
            int[] e = edges[i];
            withEdge[e[0]] = true; withEdge[e[1]] = true;
            to[ptr] = e[1]; next[ptr] = head[e[0]]; head[e[0]] = ptr++;
            to[ptr] = e[0]; next[ptr] = head[e[1]]; head[e[1]] = ptr++;
        }

        int start = -1;
        for (int i = 0; i <= maxVid; i++) if (withEdge[i]) { start = i; break; }
        if (start == -1) return false;

        // BFS from the first edge-touched vertex, using the adjacency list built above
        boolean[] visited = new boolean[maxVid + 1];
        int[]     queue   = new int[maxVid + 1];
        int       headq = 0, tail = 0;
        visited[start] = true; queue[tail++] = start;
        while (headq < tail) {
            int u = queue[headq++];
            for (int e = head[u]; e != -1; e = next[e]) {
                int v = to[e];
                if (!visited[v]) { visited[v] = true; queue[tail++] = v; }
            }
        }
        for (int i = 0; i <= maxVid; i++) if (withEdge[i] && !visited[i]) return false;
        return true;
    }

    /**
     * Removes isolated vertices from a pattern graph and re-indexes the remaining
     * vertices to {@code 0..m-1}, working on {@code edges[0..count-1]} excluding the edge
     * at {@code skipIdx} directly, without requiring a pre-shrunk copy of the edge array.
     * @param vlabels vertex labels before stripping
     * @param edges   edge buffer, only indices {@code [0,count)} excluding {@code skipIdx} are considered
     * @param count   number of valid edges in {@code edges} (including the one to skip)
     * @param skipIdx index of the edge to exclude
     * @return the stripped, re-indexed graph
     */
    static StrippedGraph stripIsolated(int[] vlabels, int[][] edges, int count, int skipIdx) {
        boolean[] used = new boolean[vlabels.length];
        for (int i = 0; i < count; i++) {
            if (i == skipIdx) continue;
            int[] e = edges[i];
            used[e[0]] = true; used[e[1]] = true;
        }
        int[] map  = new int[vlabels.length];
        int   keep = 0;
        for (int i = 0; i < vlabels.length; i++) if (used[i]) map[i] = keep++;

        StrippedGraph sg = new StrippedGraph();
        sg.vlabels = new int[keep];
        int ki = 0;
        for (int i = 0; i < vlabels.length; i++) if (used[i]) sg.vlabels[ki++] = vlabels[i];
        sg.edges = new int[count - 1][];
        int ei = 0;
        for (int i = 0; i < count; i++) {
            if (i == skipIdx) continue;
            int[] e = edges[i];
            sg.edges[ei++] = new int[]{map[e[0]], map[e[1]], e[2]};
        }
        return sg;
    }
}