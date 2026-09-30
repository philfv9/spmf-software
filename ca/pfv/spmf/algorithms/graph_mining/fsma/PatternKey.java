package ca.pfv.spmf.algorithms.graph_mining.fsma;

import java.util.Arrays;
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
 * Pattern representation and canonical-labeling procedure for the FSMA
 * algorithm ({@link AlgoFSMA}).
 *
 * <p>This file groups {@link PatternKey} (the allocation-light hashable key
 * used to identify isomorphism classes), {@link EdgeList} (the
 * allocation-light edge storage used by {@link Pattern}), and
 * {@link Pattern} itself, whose {@link Pattern#canonicalize()} method
 * implements color refinement (1-WL) followed by individualization-refinement
 * backtracking.
 *
 * @author Chen Hui
 */

final class PatternKey {
    /** The vertex-label sequence. */
    private final int[] vertexLabels;
    /** The (sorted) edge list flattened as consecutive (v1, v2, label) triples. */
    private final int[] edgeData;
    /** Precomputed hash code, since keys are hashed repeatedly during mining. */
    private final int hash;

    /**
     * Create a key from a vertex-label array and a flattened, sorted edge array.
     *
     * @param vertexLabels the vertex-label sequence
     * @param edgeData     the sorted edge triples, flattened
     */
    PatternKey(int[] vertexLabels, int[] edgeData) {
        this.vertexLabels = vertexLabels;
        this.edgeData     = edgeData;
        int h = 1;
        for (int v : vertexLabels) {
            h = 31 * h + v;
        }
        h = 31 * h + 17;
        for (int e : edgeData) {
            h = 31 * h + e;
        }
        this.hash = h;
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
        if (!(o instanceof PatternKey)) {
            return false;
        }
        PatternKey other = (PatternKey) o;
        return hash == other.hash
                && Arrays.equals(vertexLabels, other.vertexLabels)
                && Arrays.equals(edgeData, other.edgeData);
    }
}

/**
 * A minimal growable edge list storing each edge's two endpoint indices and
 * its label as three parallel primitive {@code int[]} arrays, used in place
 * of a {@code List<int[]>} of individually-allocated triples.
 *
 * <p>This matters a great deal because a brand-new edge is appended for
 * essentially every candidate pattern generated during mining (one
 * {@code add} call per single-edge extension), so avoiding one small object
 * allocation per edge removes what would otherwise be one of the very
 * hottest allocation sites in the algorithm. It also makes {@link Pattern#copy()}
 * cheap: copying an {@code EdgeList} is just three {@code System.arraycopy}
 * calls instead of copying a list of triple references.
 */
final class EdgeList {
    /** First endpoint of each edge. */
    private int[] v1;
    /** Second endpoint of each edge. */
    private int[] v2;
    /** Label of each edge. */
    private int[] label;
    /** Number of edges currently stored. */
    private int size;

    /** Create an empty edge list with a small default capacity. */
    EdgeList() {
        this(4);
    }

    /**
     * Create an empty edge list with the given initial capacity.
     *
     * @param capacity the initial capacity (at least 4)
     */
    EdgeList(int capacity) {
        capacity = Math.max(capacity, 4);
        v1    = new int[capacity];
        v2    = new int[capacity];
        label = new int[capacity];
    }

    /**
     * Create a copy of {@code other}.
     *
     * @param other the edge list to copy
     */
    EdgeList(EdgeList other) {
        size  = other.size;
        v1    = Arrays.copyOf(other.v1, size);
        v2    = Arrays.copyOf(other.v2, size);
        label = Arrays.copyOf(other.label, size);
    }

    /**
     * Append one edge, growing the backing arrays if necessary.
     *
     * @param a the first endpoint
     * @param b the second endpoint
     * @param l the edge label
     */
    void add(int a, int b, int l) {
        if (size == v1.length) {
            int newCap = v1.length == 0 ? 4 : v1.length * 2;
            v1    = Arrays.copyOf(v1, newCap);
            v2    = Arrays.copyOf(v2, newCap);
            label = Arrays.copyOf(label, newCap);
        }
        v1[size]    = a;
        v2[size]    = b;
        label[size] = l;
        size++;
    }

    /**
     * Return the number of edges stored.
     *
     * @return the current size
     */
    int size() {
        return size;
    }

    /**
     * Return the first endpoint of edge {@code i}.
     *
     * @param i the edge index
     * @return the first endpoint
     */
    int v1(int i) {
        return v1[i];
    }

    /**
     * Return the second endpoint of edge {@code i}.
     *
     * @param i the edge index
     * @return the second endpoint
     */
    int v2(int i) {
        return v2[i];
    }

    /**
     * Return the label of edge {@code i}.
     *
     * @param i the edge index
     * @return the edge label
     */
    int label(int i) {
        return label[i];
    }
}

/**
 * A small labeled graph pattern (candidate or frequent subgraph) with
 * contiguous 0-based vertex indices. The key operation is
 * {@link #canonicalize()}, which uses color refinement followed by
 * individualization-refinement backtracking.
 *
 * <p>Edges are stored in an {@link EdgeList} (parallel primitive arrays)
 * rather than as individually-allocated {@code int[3]} triples, so that
 * {@link #copy()} -- invoked for essentially every single-edge extension
 * candidate generated during mining -- only has to copy a handful of
 * primitive arrays instead of allocating one object per edge.
 */
class Pattern {

    /** The label of each vertex, indexed by 0-based vertex position. */
    IntList vertexLabels = new IntList();
    /** The edges of the pattern, stored as parallel primitive arrays. */
    EdgeList edges = new EdgeList();

    /** Cached result of {@link #toSortKey()}; non-null after first call. */
    private String sortKeyCache;

    /**
     * Create a copy of this pattern: both the vertex-label array and the
     * edge list are duplicated via cheap primitive-array copies.
     *
     * @return a new pattern with the same content as this one
     */
    Pattern copy() {
        Pattern p = new Pattern();
        p.vertexLabels = new IntList(vertexLabels);
        p.edges         = new EdgeList(edges);
        return p;
    }

    /**
     * Return {@code true} if this pattern already contains an edge between
     * vertices {@code i} and {@code j}.
     *
     * @param i index of the first vertex
     * @param j index of the second vertex
     * @return {@code true} if an edge (i,j) exists
     */
    boolean hasEdge(int i, int j) {
        int n = edges.size();
        for (int k = 0; k < n; k++) {
            int a = edges.v1(k);
            int b = edges.v2(k);
            if ((a == i && b == j) || (a == j && b == i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return a flattened, sorted copy of this pattern's edges as consecutive
     * (v1, v2, label) triples, sorted by (v1, v2, label). Operates directly
     * on the primitive parallel arrays of {@link #edges} via an index
     * permutation, so it neither boxes anything nor allocates one array per
     * edge, since this is invoked for essentially every candidate pattern
     * generated during mining (via {@link #rawKey()}).
     *
     * @return the flattened, sorted edge triples
     */
    private int[] sortedEdgesFlat() {
        int n   = edges.size();
        int[] idx = new int[n];
        for (int i = 0; i < n; i++) {
            idx[i] = i;
        }
        sortIndices(idx, (a, b) -> {
            int va = edges.v1(a);
            int vb = edges.v1(b);
            if (va != vb) return va - vb;
            va = edges.v2(a);
            vb = edges.v2(b);
            if (va != vb) return va - vb;
            return edges.label(a) - edges.label(b);
        });
        int[] flat = new int[n * 3];
        for (int i = 0; i < n; i++) {
            int e = idx[i];
            flat[i * 3]     = edges.v1(e);
            flat[i * 3 + 1] = edges.v2(e);
            flat[i * 3 + 2] = edges.label(e);
        }
        return flat;
    }

    /**
     * Build a key identifying this pattern's raw (non-canonical) representation,
     * used only to look up {@link AlgoFSMA#canonicalizeCached}.
     *
     * @return the raw representation key
     */
    PatternKey rawKey() {
        int[] vLabels  = vertexLabels.toArray();
        int[] edgeFlat = sortedEdgesFlat();
        return new PatternKey(vLabels, edgeFlat);
    }

    /**
     * Return a deterministic sort key for output ordering (edge count first,
     * then vertex labels, then edge list), cached after the first call.
     *
     * @return the sort key string
     */
    String toSortKey() {
        if (sortKeyCache != null) {
            return sortKeyCache;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(edges.size()).append("_");
        int m = vertexLabels.size();
        for (int i = 0; i < m; i++) {
            sb.append(vertexLabels.get(i)).append(",");
        }
        sb.append("_");
        int[] flat = sortedEdgesFlat();
        for (int i = 0; i < flat.length; i += 3) {
            sb.append(flat[i]).append("-").append(flat[i + 1]).append("-").append(flat[i + 2]).append(",");
        }
        sortKeyCache = sb.toString();
        return sortKeyCache;
    }

    /** The result of normalizing a pattern with the canonical-labeling procedure. */
    static class CanonicalResult {
        /** Key that uniquely identifies the isomorphism class. */
        PatternKey key;
        /** Vertex permutation: {@code perm[canonicalPosition] = originalVertexIndex}. */
        int[]   perm;
        /** A Pattern whose vertex and edge ordering follows the canonical form. */
        Pattern canonicalPattern;
    }

    /**
     * Holder for the best (lexicographically smallest) canonical candidate
     * found so far. The canonical edges are kept as three parallel arrays
     * ({@link #a}, {@link #b}, {@link #l}) instead of an {@code int[][]} of
     * triples, avoiding one allocation per edge per candidate order explored
     * during backtracking.
     */
    private static class BestHolder {
        /** The canonical vertex order of the best candidate. */
        int[] order;
        /** First endpoint of each canonical edge, sorted. */
        int[] a;
        /** Second endpoint of each canonical edge, sorted. */
        int[] b;
        /** Label of each canonical edge, sorted (parallel to {@link #a}/{@link #b}). */
        int[] l;
        /** Whether any candidate has been recorded yet. */
        boolean found = false;
    }

    /**
     * Flat CSR-style (Compressed Sparse Row) adjacency representation of a
     * pattern's edges, used during canonicalization instead of a
     * {@code List<List<int[]>>} to avoid allocating one {@code ArrayList}
     * per vertex plus one boxed pair object per edge endpoint.
     */
    private static final class Csr {
        /** {@code start[v] .. start[v+1]-1} is the range of {@code v}'s entries. */
        final int[] start;
        /** Flattened neighbor ids, grouped by vertex according to {@link #start}. */
        final int[] neighbor;
        /** Flattened edge labels, parallel to {@link #neighbor}. */
        final int[] label;

        Csr(int[] start, int[] neighbor, int[] label) {
            this.start    = start;
            this.neighbor = neighbor;
            this.label    = label;
        }
    }

    /**
     * Scratch buffers reused across every {@link #refine} and
     * {@link #individualize} call performed while canonicalizing a single
     * pattern. Color refinement and individualization are invoked
     * repeatedly during backtracking; without this workspace, each call
     * would reallocate a full {@code long[m][]} of per-vertex neighbour
     * signatures, the ping-pong coloring buffers, the ranking array, and
     * the individualization scratch, even though their sizes never change
     * once the adjacency is fixed.
     *
     * <p>Reuse of {@link #indivKeys}/{@link #indivOrder}/{@link #indivColors}
     * across recursive calls is safe because their contents are always
     * fully consumed (copied elsewhere) by the very next {@link #refine}
     * call before any deeper recursion has a chance to overwrite them again.
     */
    private static final class RefineWorkspace {
        /** Per-vertex neighbour signature scratch, sized once from vertex degree. */
        final long[][] neighborSig;
        /** Scratch array used to rank vertices by (color, signature) each round. */
        final int[] order;
        /** Ping-pong coloring buffers used while iterating refinement rounds. */
        final int[] bufA;
        /** Ping-pong coloring buffers used while iterating refinement rounds. */
        final int[] bufB;
        /** Scratch used by {@link Pattern#individualize}: packed (color, is-target) sort keys. */
        final long[] indivKeys;
        /** Scratch used by {@link Pattern#individualize}: vertex ranking order. */
        final int[] indivOrder;
        /** Scratch used by {@link Pattern#individualize}: the resulting coloring. */
        final int[] indivColors;

        RefineWorkspace(Csr csr, int m) {
            neighborSig = new long[m][];
            for (int i = 0; i < m; i++) {
                neighborSig[i] = new long[csr.start[i + 1] - csr.start[i]];
            }
            order       = new int[m];
            bufA        = new int[m];
            bufB        = new int[m];
            indivKeys   = new long[m];
            indivOrder  = new int[m];
            indivColors = new int[m];
        }
    }

    /**
     * Build a {@link Csr} adjacency structure from this pattern's {@link EdgeList}.
     *
     * @param edgeArr the pattern's edges
     * @param m       the number of vertices
     * @return the CSR adjacency representation
     */
    private static Csr buildCsr(EdgeList edgeArr, int m) {
        int n = edgeArr.size();
        int[] degree = new int[m];
        for (int i = 0; i < n; i++) {
            degree[edgeArr.v1(i)]++;
            degree[edgeArr.v2(i)]++;
        }
        int[] start = new int[m + 1];
        for (int i = 0; i < m; i++) {
            start[i + 1] = start[i] + degree[i];
        }
        int[] neighbor = new int[start[m]];
        int[] label    = new int[start[m]];
        int[] fill     = start.clone();
        for (int i = 0; i < n; i++) {
            int a = edgeArr.v1(i);
            int b = edgeArr.v2(i);
            int l = edgeArr.label(i);
            neighbor[fill[a]] = b;
            label[fill[a]]    = l;
            fill[a]++;
            neighbor[fill[b]] = a;
            label[fill[b]]    = l;
            fill[b]++;
        }
        return new Csr(start, neighbor, label);
    }

    /** Comparator over primitive {@code int} values, used to avoid boxing. */
    @FunctionalInterface
    private interface IntComparator {
        int compare(int a, int b);
    }

    /**
     * Sort {@code indices} in place using insertion sort and the given
     * comparator, without boxing any value.
     *
     * @param indices the array to sort
     * @param cmp     the comparator to sort by
     */
    private static void sortIndices(int[] indices, IntComparator cmp) {
        for (int i = 1; i < indices.length; i++) {
            int key = indices[i];
            int j   = i - 1;
            while (j >= 0 && cmp.compare(indices[j], key) > 0) {
                indices[j + 1] = indices[j];
                j--;
            }
            indices[j + 1] = key;
        }
    }

    /**
     * Compute the canonical form of this pattern using color refinement (1-WL)
     * followed by individualization-refinement backtracking on any remaining
     * ambiguity. Isomorphic patterns always produce the same canonical key.
     *
     * @return the canonical form of this pattern
     */
    CanonicalResult canonicalize() {

        int m = vertexLabels.size();

        int[] vLabel = vertexLabels.toArray();

        Csr csr = buildCsr(edges, m);

        // scratch buffers shared across every refine()/individualize() call
        // made while canonicalizing this pattern (see RefineWorkspace)
        RefineWorkspace ws = new RefineWorkspace(csr, m);

        // refine vertex labels to a stable coloring
        int[] stableColors = refine(vLabel, csr, m, ws);

        BestHolder best = new BestHolder();
        search(stableColors, csr, m, edges, best, ws);

        // build the canonical key directly from primitive arrays (no String)
        int[] keyLabels = new int[m];
        for (int i = 0; i < m; i++) {
            keyLabels[i] = vLabel[best.order[i]];
        }
        int   n        = best.a.length;
        int[] keyEdges = new int[n * 3];
        for (int i = 0; i < n; i++) {
            keyEdges[i * 3]     = best.a[i];
            keyEdges[i * 3 + 1] = best.b[i];
            keyEdges[i * 3 + 2] = best.l[i];
        }

        CanonicalResult cr  = new CanonicalResult();
        cr.key              = new PatternKey(keyLabels, keyEdges);
        cr.perm             = best.order;
        cr.canonicalPattern = buildCanonicalPatternFromEdges(best.order, vLabel, best.a, best.b, best.l);
        return cr;
    }

    /**
     * Iteratively refine a vertex coloring using the 1-WL rule until the
     * partition stabilizes. Uses primitive long-packed neighbor signatures
     * and reuses the ping-pong buffers and neighbor-signature scratch held
     * in {@code ws} instead of reallocating them, since this method is
     * invoked repeatedly during backtracking. Only the final result is a
     * freshly allocated, independent array (the shared buffers themselves
     * will be overwritten by the next call made anywhere else in the same
     * {@link #canonicalize()} invocation).
     *
     * @param colors the initial vertex coloring (vertex labels)
     * @param csr    the pattern's CSR adjacency representation
     * @param m      the number of vertices
     * @param ws     scratch buffers reused across refine() calls
     * @return the stable refined coloring (an independent array)
     */
    private int[] refine(int[] colors, Csr csr, int m, RefineWorkspace ws) {
        int[] bufA = ws.bufA;
        int[] bufB = ws.bufB;
        System.arraycopy(colors, 0, bufA, 0, m);
        int[] cur  = bufA;
        int[] next = bufB;
        int   prevDistinct = -1;

        // reused per-vertex signature scratch (sizes fixed by degree, contents refreshed each round)
        long[][] neighborSig = ws.neighborSig;
        int[] order = ws.order;

        for (int round = 0; round <= m; round++) {
            final int[] curSnapshot = cur;

            // compute sorted (neighbor-color, edge-label) signatures per vertex
            for (int i = 0; i < m; i++) {
                int    s   = csr.start[i];
                int    e   = csr.start[i + 1];
                long[] sig = neighborSig[i];
                for (int k = s; k < e; k++) {
                    sig[k - s] = (((long) curSnapshot[csr.neighbor[k]]) << 32)
                            | (csr.label[k] & 0xFFFFFFFFL);
                }
                Arrays.sort(sig);
            }

            // rank vertices by (own color, neighbor signature) to get the refined coloring
            for (int i = 0; i < m; i++) {
                order[i] = i;
            }
            sortIndices(order, (a, b) -> {
                if (curSnapshot[a] != curSnapshot[b]) {
                    return curSnapshot[a] - curSnapshot[b];
                }
                long[] sa  = neighborSig[a];
                long[] sb  = neighborSig[b];
                int    len = Math.min(sa.length, sb.length);
                for (int k = 0; k < len; k++) {
                    if (sa[k] != sb[k]) {
                        return Long.compare(sa[k], sb[k]);
                    }
                }
                return sa.length - sb.length;
            });

            // assign consecutive ranks to distinct (color, signature) groups
            int rank = 0;
            next[order[0]] = 0;
            for (int i = 1; i < m; i++) {
                int     prev = order[i - 1];
                int     curV = order[i];
                boolean same = curSnapshot[prev] == curSnapshot[curV]
                        && Arrays.equals(neighborSig[prev], neighborSig[curV]);
                if (!same) {
                    rank++;
                }
                next[curV] = rank;
            }

            int distinctNow = rank + 1;

            // swap buffers so 'next' becomes 'cur' for the following round
            int[] tmp = cur;
            cur  = next;
            next = tmp;

            if (distinctNow == prevDistinct) {
                break;
            }
            prevDistinct = distinctNow;
        }
        // return an independent copy: 'cur' aliases one of the workspace's
        // shared ping-pong buffers, which would otherwise be overwritten by
        // the next refine() call made anywhere else in this canonicalize()
        return Arrays.copyOf(cur, m);
    }

    /**
     * Split vertex {@code v} away from its color class, giving it a unique
     * color that sorts before the rest of the class (the individualization step).
     * Reuses {@code ws}'s scratch buffers instead of allocating three fresh
     * arrays on every call; this is safe because the returned buffer is
     * fully consumed (copied) by the very next {@link #refine} call before
     * any deeper recursion could overwrite it again.
     *
     * @param colors the current stable coloring
     * @param m      the number of vertices
     * @param v      the vertex to individualize
     * @param ws     scratch buffers reused across individualize() calls
     * @return a coloring (owned by {@code ws}) with {@code v} in its own singleton class
     */
    private int[] individualize(int[] colors, int m, int v, RefineWorkspace ws) {
        long[] keys = ws.indivKeys;
        for (int u = 0; u < m; u++) {
            long secondary = (u == v) ? 0L : 1L;
            keys[u] = (((long) colors[u]) << 1) | secondary;
        }

        int[] order = ws.indivOrder;
        for (int u = 0; u < m; u++) {
            order[u] = u;
        }
        sortIndices(order, (a, b) -> Long.compare(keys[a], keys[b]));

        int[] newColors = ws.indivColors;
        int   rank      = 0;
        newColors[order[0]] = 0;
        for (int i = 1; i < m; i++) {
            if (keys[order[i]] != keys[order[i - 1]]) {
                rank++;
            }
            newColors[order[i]] = rank;
        }
        return newColors;
    }

    /**
     * Recursively explore residual ambiguity after color refinement by
     * individualizing each vertex of the first non-singleton color class in turn,
     * refining again, and recursing until all classes are singletons.
     *
     * @param colors  the current stable coloring
     * @param csr     the pattern's CSR adjacency representation
     * @param m       the number of vertices
     * @param edgeArr the original edges of the pattern
     * @param best    the current best canonical candidate, updated in place
     * @param ws      scratch buffers reused across refine()/individualize() calls
     */
    private void search(int[] colors, Csr csr, int m,
                         EdgeList edgeArr, BestHolder best, RefineWorkspace ws) {

        // determine the number of distinct colors present
        int maxColor = 0;
        for (int i = 0; i < m; i++) {
            if (colors[i] > maxColor) {
                maxColor = colors[i];
            }
        }
        int numColors = maxColor + 1;

        // counting sort: bucket vertex indices by color, ascending vertex index within a color
        int[] count = new int[numColors];
        for (int i = 0; i < m; i++) {
            count[colors[i]]++;
        }
        int[] startIdx = new int[numColors];
        for (int c = 1; c < numColors; c++) {
            startIdx[c] = startIdx[c - 1] + count[c - 1];
        }
        int[] fillPos = startIdx.clone();
        int[] order   = new int[m];
        for (int i = 0; i < m; i++) {
            int c = colors[i];
            order[fillPos[c]++] = i;
        }

        // find the first color class that is not yet a singleton
        int targetColor = -1;
        for (int c = 0; c < numColors; c++) {
            if (count[c] > 1) {
                targetColor = c;
                break;
            }
        }

        if (targetColor == -1) {
            // all classes are singletons: this order is a candidate canonical order
            evaluateCandidate(order, edgeArr, best);
            return;
        }

        // branch on each vertex of the ambiguous class
        int start = startIdx[targetColor];
        int end   = start + count[targetColor];
        for (int idx = start; idx < end; idx++) {
            int   v              = order[idx];
            int[] individualized = individualize(colors, m, v, ws);
            int[] refined        = refine(individualized, csr, m, ws);
            search(refined, csr, m, edgeArr, best, ws);
        }
    }

    /**
     * Evaluate one candidate vertex order by transforming all edges into
     * canonical-position form and updating {@code best} if this candidate
     * produces a lexicographically smaller edge signature. Builds the
     * transformed edges as three parallel primitive arrays and sorts them
     * via an index permutation, instead of allocating one {@code int[3]}
     * triple per edge, since this runs once per candidate order explored
     * during backtracking.
     *
     * @param newOrder the candidate order ({@code newOrder[pos] = originalVertexIndex})
     * @param edgeArr  the original edges of the pattern
     * @param best     the current best candidate, updated in place
     */
    private void evaluateCandidate(int[] newOrder, EdgeList edgeArr, BestHolder best) {
        int   m         = newOrder.length;
        int[] origToPos = new int[m];
        for (int p = 0; p < m; p++) {
            origToPos[newOrder[p]] = p;
        }

        // transform edges to canonical positions as parallel arrays
        int   n  = edgeArr.size();
        int[] ea = new int[n];
        int[] eb = new int[n];
        int[] el = new int[n];
        for (int j = 0; j < n; j++) {
            int p1 = origToPos[edgeArr.v1(j)];
            int p2 = origToPos[edgeArr.v2(j)];
            ea[j] = Math.min(p1, p2);
            eb[j] = Math.max(p1, p2);
            el[j] = edgeArr.label(j);
        }

        // sort the edges by (ea, eb, el) via an index permutation (no boxing)
        int[] perm = new int[n];
        for (int j = 0; j < n; j++) {
            perm[j] = j;
        }
        sortIndices(perm, (x, y) -> {
            if (ea[x] != ea[y]) return ea[x] - ea[y];
            if (eb[x] != eb[y]) return eb[x] - eb[y];
            return el[x] - el[y];
        });

        int[] sa = new int[n];
        int[] sb = new int[n];
        int[] sl = new int[n];
        for (int j = 0; j < n; j++) {
            int p = perm[j];
            sa[j] = ea[p];
            sb[j] = eb[p];
            sl[j] = el[p];
        }

        // keep this candidate if it is smaller than the best found so far
        if (!best.found || compareEdges(sa, sb, sl, best.a, best.b, best.l) < 0) {
            best.found = true;
            best.order = newOrder.clone();
            best.a     = sa;
            best.b     = sb;
            best.l     = sl;
        }
    }

    /**
     * Lexicographically compare two sorted, parallel-array edge representations.
     *
     * @param a1 first endpoints of the first candidate's edges
     * @param b1 second endpoints of the first candidate's edges
     * @param l1 labels of the first candidate's edges
     * @param a2 first endpoints of the second candidate's edges
     * @param b2 second endpoints of the second candidate's edges
     * @param l2 labels of the second candidate's edges
     * @return negative, zero, or positive as the first candidate is smaller than,
     *         equal to, or greater than the second
     */
    private static int compareEdges(int[] a1, int[] b1, int[] l1,
                                     int[] a2, int[] b2, int[] l2) {
        int len = Math.min(a1.length, a2.length);
        for (int i = 0; i < len; i++) {
            if (a1[i] != a2[i]) {
                return a1[i] - a2[i];
            }
            if (b1[i] != b2[i]) {
                return b1[i] - b2[i];
            }
            if (l1[i] != l2[i]) {
                return l1[i] - l2[i];
            }
        }
        return a1.length - a2.length;
    }

    /**
     * Build a new {@link Pattern} whose vertex order follows {@code order}
     * and whose edges are the already canonical parallel-array edge data.
     *
     * @param order  canonical vertex order ({@code order[pos] = originalVertexIndex})
     * @param vLabel the label of each original vertex
     * @param a      first endpoints of the canonical edges
     * @param b      second endpoints of the canonical edges
     * @param l      labels of the canonical edges
     * @return the pattern in canonical vertex and edge order
     */
    private Pattern buildCanonicalPatternFromEdges(int[] order, int[] vLabel,
                                                    int[] a, int[] b, int[] l) {
        int     m = order.length;
        Pattern p = new Pattern();
        p.vertexLabels = new IntList(m);
        for (int i = 0; i < m; i++) {
            p.vertexLabels.add(vLabel[order[i]]);
        }
        int n = a.length;
        p.edges = new EdgeList(n);
        for (int i = 0; i < n; i++) {
            p.edges.add(a[i], b[i], l[i]);
        }
        return p;
    }
}