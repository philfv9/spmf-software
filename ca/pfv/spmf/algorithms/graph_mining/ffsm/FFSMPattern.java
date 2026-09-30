// =============================================================================
package ca.pfv.spmf.algorithms.graph_mining.ffsm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
 * Frequent-pattern representation ({@link Pattern}), embeddings ({@link Embedding}) and
 * canonical-adjacency-matrix (CAM) machinery ({@link CanonResult}, {@link #canonicalizeMax},
 * {@link #identityCode}) used by {@link AlgoFFSM}, based on Huan, Wang &amp; Prins,
 * "Efficient Mining of Frequent Subgraph in the Presence of Isomorphism", ICDM 2003.
 * <p>
 * A pattern is represented as an n x n matrix: diagonal entries hold vertex labels,
 * off-diagonal entries hold edge labels (or -1 if absent). Its code is the row-by-row
 * concatenation of the matrix entries, and its canonical form is the vertex ordering
 * that maximizes this code ({@link #canonicalizeMax}).
 * <p>
 * A pattern's embedding set must contain every label-preserving vertex mapping into a
 * target graph, not just one per physical occurrence: when several vertex orderings tie
 * for the maximum code (a non-trivial automorphism), {@link #canonicalizeMax} exposes
 * all of them via {@link CanonResult#allOrders} so that every required embedding can be
 * built.
 *
 * @author Chen Hui
 */
public class FFSMPattern {

    private FFSMPattern() { }

    /**
     * One embedding (vertex mapping) of a pattern into a graph transaction.
     * {@code vertices[i]} is the target vertex mapped to pattern vertex {@code i}.
     */
    static class Embedding {
        /** Index of the graph transaction this embedding maps into. */
        int graphIndex;
        /** Target vertex mapped to each pattern vertex. */
        int[] vertices;

        /**
         * Creates a new embedding.
         *
         * @param graphIndex index of the graph transaction
         * @param vertices   target vertex mapped to each pattern vertex
         */
        Embedding(int graphIndex, int[] vertices) {
            this.graphIndex = graphIndex;
            this.vertices   = vertices;
        }
    }

    /**
     * A frequent pattern together with its full embedding set and canonical code.
     * <p>
     * Also used to represent proper suboptimal CAMs: patterns whose maximal proper
     * submatrix is a genuine CAM but which are not themselves in canonical order. Such
     * instances are join-only material, never output and never extended further; for
     * them {@link #canonKey} holds the pattern's own identity code (see {@link #identityCode}).
     */
    static class Pattern {
        /** Vertex labels, in canonical (or, for a suboptimal CAM, as-constructed) order. */
        int[]   vlabels;
        /** Edges as (v1, v2, label) triples, matching {@link #vlabels}'s numbering. */
        int[][] edges;
        /** Embedding set (one entry per occurrence). */
        List<Embedding> embeddings;
        /** Sorted ids of graphs containing this pattern (empty for join-only, non-canonical members). */
        List<Integer> tidlist;
        /** Canonical (maximum) code, or - for a suboptimal CAM - its own identity code. */
        int[] canonKey;

        /**
         * Creates a new pattern.
         *
         * @param vlabels    vertex labels
         * @param edges      edges
         * @param embeddings embedding set
         * @param tidlist    sorted ids of supporting graphs
         * @param canonKey   canonical or identity code
         */
        Pattern(int[] vlabels, int[][] edges, List<Embedding> embeddings,
                List<Integer> tidlist, int[] canonKey) {
            this.vlabels    = vlabels;
            this.edges      = edges;
            this.embeddings = embeddings;
            this.tidlist    = tidlist;
            this.canonKey   = canonKey;
        }
    }

    /** Wraps an {@code int[]} for use as a hash map/set key by value. */
    static final class IntArrayKey {
        /** The wrapped array. */
        final int[] key;
        /** Cached hash code of {@link #key}. */
        private final int hash;

        /**
         * Wraps an array as a hashable key.
         *
         * @param key the array to wrap
         */
        IntArrayKey(int[] key) { this.key = key; this.hash = Arrays.hashCode(key); }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof IntArrayKey)) return false;
            IntArrayKey other = (IntArrayKey) o;
            return hash == other.hash && Arrays.equals(key, other.key); // hash mismatch short-circuits
        }

        @Override public int hashCode() { return hash; }
    }

    /**
     * Identity key for an embedding (graph index + vertex tuple), used for embedding-set
     * membership tests and de-duplication. Unlike {@link IntArrayKey}, it reuses the
     * embedding's existing vertices array by reference instead of copying it, and can be
     * scoped to a {@code length}-element prefix of a longer array without copying it either.
     */
    static final class EmbeddingKey {
        /** Index of the graph transaction. */
        final int graphIndex;
        /** Vertex tuple of the embedding (possibly longer than {@link #length}). */
        final int[] vertices;
        /** Number of leading elements of {@link #vertices} actually used by this key. */
        final int length;
        /** Cached hash code of this key. */
        private final int hash;

        /**
         * Creates a key covering the whole vertex tuple.
         *
         * @param graphIndex index of the graph transaction
         * @param vertices   vertex tuple of the embedding
         */
        EmbeddingKey(int graphIndex, int[] vertices) {
            this(graphIndex, vertices, vertices.length);
        }

        /**
         * Creates a key covering only the first {@code length} elements of {@code vertices}.
         *
         * @param graphIndex index of the graph transaction
         * @param vertices   backing vertex array
         * @param length     number of leading elements to use
         */
        EmbeddingKey(int graphIndex, int[] vertices, int length) {
            this.graphIndex = graphIndex;
            this.vertices   = vertices;
            this.length     = length;
            int h = graphIndex;
            for (int i = 0; i < length; i++) h = 31 * h + vertices[i]; // fold in each used vertex
            this.hash = h;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EmbeddingKey)) return false;
            EmbeddingKey other = (EmbeddingKey) o;
            if (hash != other.hash || graphIndex != other.graphIndex || length != other.length)
                return false;
            for (int i = 0; i < length; i++) if (vertices[i] != other.vertices[i]) return false;
            return true;
        }

        @Override public int hashCode() { return hash; }
    }

    /** Result of canonicalizing a pattern: its maximum code and the relabeled graph. */
    static class CanonResult {
        /** Canonical (maximum) code. */
        int[]   key;
        /** Vertex labels in canonical order. */
        int[]   vlabels;
        /** Edges renumbered to canonical order. */
        int[][] edges;
        /** Every vertex ordering tied for the maximum code; {@code allOrders[0]} is the representative one. */
        int[][] allOrders;

        /** Cached candidate-map lookup key view of {@link #key}, built lazily by {@link #candidateKey()}. */
        private IntArrayKey candidateKeyCache;

        /**
         * Returns the {@link IntArrayKey} view of {@link #key}, building and caching it
         * on first call.
         *
         * @return the candidate-map lookup key for this result
         */
        IntArrayKey candidateKey() {
            if (candidateKeyCache == null) candidateKeyCache = new IntArrayKey(key);
            return candidateKeyCache;
        }
    }

    /** Running best (maximum) code and tied orderings found so far by {@link #canonicalizeMax}. */
    private static final class CanonSearchState {
        /** Best code found so far. */
        int[] bestKey;
        /** Every vertex ordering tied for {@link #bestKey}. */
        final List<int[]> bestOrders = new ArrayList<>();
        /** Whether a complete ordering has been found yet. */
        boolean found;
    }

    /**
     * Lexicographically compares two int arrays (a shorter array that is a prefix of
     * a longer one is considered smaller).
     *
     * @param a first array
     * @param b second array
     * @return a negative, zero, or positive value as {@code a} is less than, equal to,
     *         or greater than {@code b}
     */
    static int compareIntArrays(int[] a, int[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
        return Integer.compare(a.length, b.length);
    }

    /**
     * Computes the length of the FFSM code of an n-vertex matrix: {@code n} diagonal
     * (vertex-label) entries plus {@code n*(n-1)/2} off-diagonal (edge-label) entries.
     *
     * @param n number of vertices
     * @return the code length
     */
    private static int codeLength(int n) {
        return n + n * (n - 1) / 2;
    }

    /**
     * Builds the flat, row-major {@code n x n} adjacency matrix (index {@code i*n+j}):
     * off-diagonal cell {@code (i,j)} holds the edge label between {@code i} and
     * {@code j}, or {@code -1} if absent.
     *
     * @param n     number of vertices
     * @param edges edges as (v1, v2, label) triples
     * @return the flat adjacency matrix
     */
    private static int[] buildAdjMatrix(int n, int[][] edges) {
        int[] adjMatrix = new int[n * n]; // flat, row-major matrix
        Arrays.fill(adjMatrix, -1);
        for (int[] e : edges) { // fill both symmetric entries
            adjMatrix[e[0] * n + e[1]] = e[2];
            adjMatrix[e[1] * n + e[0]] = e[2];
        }
        return adjMatrix;
    }

    /**
     * Computes the FFSM code of a pattern using its vertices in the given (identity)
     * order, without searching for the maximum. Used to test whether a freshly-built
     * matrix already happens to be canonical.
     *
     * @param vlabels vertex labels
     * @param edges   edges
     * @return the identity code
     */
    static int[] identityCode(int[] vlabels, int[][] edges) {
        int n = vlabels.length;
        int[] adjMatrix = buildAdjMatrix(n, edges);

        int[] key = new int[codeLength(n)];
        int   idx = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) key[idx++] = adjMatrix[i * n + j]; // edge entries
            key[idx++] = vlabels[i]; // vertex-label entry
        }
        return key;
    }

    /**
     * Computes the canonical adjacency matrix (CAM) of a pattern by a backtracking
     * search over vertex orderings, maximizing the FFSM code. Every prefix of a
     * maximal code is itself maximal for the corresponding partial ordering, so
     * partial orderings already behind the current best are pruned.
     *
     * @param vlabels vertex labels
     * @param edges   edges
     * @return the canonical code, relabeled graph, and every ordering tied for the
     *         maximum (automorphisms; {@code allOrders[0]} is the representative one)
     */
    static CanonResult canonicalizeMax(int[] vlabels, int[][] edges) {
        int n = vlabels.length;
        CanonResult res = new CanonResult();

        int[] adjMatrix = buildAdjMatrix(n, edges);

        int     codeLen = codeLength(n);
        int[]   order   = new int[n];
        boolean[] used  = new boolean[n];
        int[]   key     = new int[codeLen];

        CanonSearchState state = new CanonSearchState();
        state.bestKey   = new int[codeLen];

        search(0, n, order, used, adjMatrix, vlabels, key, state, false); // find maximum-code ordering(s)

        int[] bestOrder    = state.bestOrders.get(0);
        int[] canonVlabels = new int[n];
        for (int i = 0; i < n; i++) canonVlabels[i] = vlabels[bestOrder[i]]; // relabel vertices

        int edgeCount = 0;
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                if (adjMatrix[bestOrder[i] * n + bestOrder[j]] != -1) edgeCount++;

        int[][] canonEdges = new int[edgeCount][];
        int     eIdx       = 0;
        for (int i = 0; i < n; i++) // renumber edges to canonical order
            for (int j = i + 1; j < n; j++) {
                int lab = adjMatrix[bestOrder[i] * n + bestOrder[j]];
                if (lab != -1) canonEdges[eIdx++] = new int[]{i, j, lab};
            }

        res.key       = state.bestKey;
        res.vlabels   = canonVlabels;
        res.edges     = canonEdges;
        res.allOrders = state.bestOrders.toArray(new int[0][]);
        return res;
    }

    /**
     * Backtracking search over vertex orderings, building the code one row at a time
     * and pruning any partial ordering whose code so far is already strictly smaller
     * than the current best complete code.
     *
     * @param pos         index of the vertex position (row) being assigned
     * @param n           total number of vertices
     * @param order       partial ordering being built
     * @param used        marks which vertices are already placed in {@code order}
     * @param adjMatrix   flat, row-major {@code n x n} adjacency matrix
     * @param vlabels     vertex labels
     * @param key         code being built for the current ordering
     * @param state       running best code and tied orderings
     * @param aheadOfBest whether this branch is already guaranteed to not lose to the current best
     */
    private static void search(int pos, int n, int[] order, boolean[] used,
                                int[] adjMatrix, int[] vlabels, int[] key,
                                CanonSearchState state, boolean aheadOfBest) {
        if (pos == n) {
            finalizeOrder(order, key, state);
            return;
        }
        int keyIdx = pos * (pos + 1) / 2; // start offset of row 'pos' in the code

        for (int v = 0; v < n; v++) {
            if (used[v]) continue;

            boolean branchAhead = aheadOfBest;
            boolean pruned      = false;
            int     vRow        = v * n;

            for (int j = 0; j <= pos; j++) {
                int val = (j < pos) ? adjMatrix[vRow + order[j]] : vlabels[v];
                key[keyIdx + j] = val;

                if (state.found && !branchAhead) {
                    int cmp = Integer.compare(val, state.bestKey[keyIdx + j]);
                    if (cmp < 0) { pruned = true; break; } // strictly worse: prune this branch
                    if (cmp > 0) branchAhead = true;       // strictly better: rest of branch is safe
                }
            }
            if (pruned) continue;

            order[pos] = v;
            used[v]    = true;
            search(pos + 1, n, order, used, adjMatrix, vlabels, key, state, branchAhead);
            used[v]    = false;
        }
    }

    /**
     * Records a completed vertex ordering's code against the running best: a
     * strictly larger code replaces the best; an equal code is a tied automorphism
     * and is added to the list of best orderings.
     *
     * @param order the completed vertex ordering
     * @param key   the ordering's code
     * @param state running best code and tied orderings, updated in place
     */
    private static void finalizeOrder(int[] order, int[] key, CanonSearchState state) {
        if (!state.found) {
            state.found = true;
            System.arraycopy(key, 0, state.bestKey, 0, key.length);
            state.bestOrders.clear();
            state.bestOrders.add(order.clone());
            return;
        }
        int cmp = compareIntArrays(key, state.bestKey);
        if (cmp > 0) { // new strict maximum
            System.arraycopy(key, 0, state.bestKey, 0, key.length);
            state.bestOrders.clear();
            state.bestOrders.add(order.clone());
        } else if (cmp == 0) {
            state.bestOrders.add(order.clone()); // tied: genuine automorphism
        }
    }

    /**
     * Builds a raw memoization key (vertex labels + sorted edge triples) for a
     * candidate pattern, used to avoid recomputing {@link #canonicalizeMax} for
     * structurally identical candidates proposed from different embeddings.
     *
     * @param vlabels vertex labels
     * @param edges   edges
     * @return the raw memoization key
     */
    static int[] buildRawKey(int[] vlabels, int[][] edges) {
        int[][] sorted = edges.clone();
        Arrays.sort(sorted, (a, b) -> { // sort edges for a structure-independent key
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
     * Memoized {@link #canonicalizeMax}, using a raw key already computed by the
     * caller.
     *
     * @param cache   shared canonicalization cache
     * @param rawKey  raw memoization key of the candidate
     * @param vlabels vertex labels
     * @param edges   edges
     * @return the (possibly cached) canonicalization result
     */
    static CanonResult canonicalizeCached(Map<IntArrayKey, CanonResult> cache,
                                           IntArrayKey rawKey,
                                           int[] vlabels, int[][] edges) {
        CanonResult cached = cache.get(rawKey);
        if (cached != null) return cached;
        CanonResult cr = canonicalizeMax(vlabels, edges);
        cache.put(rawKey, cr);
        return cr;
    }

    /**
     * Memoized {@link #canonicalizeMax}, computing the raw key from {@code vlabels}/{@code edges}.
     *
     * @param cache   shared canonicalization cache
     * @param vlabels vertex labels
     * @param edges   edges
     * @return the (possibly cached) canonicalization result
     */
    static CanonResult canonicalizeCached(Map<IntArrayKey, CanonResult> cache,
                                           int[] vlabels, int[][] edges) {
        return canonicalizeCached(cache, new IntArrayKey(buildRawKey(vlabels, edges)), vlabels, edges);
    }
}