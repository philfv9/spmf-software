package ca.pfv.spmf.algorithms.graph_mining.fsma;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
/* This file is copyright (c) 2018 by Chen Hui, Philippe Fournier-Viger
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
 * Input-graph representation for the FSMA algorithm ({@link AlgoFSMA}).
 *
 * <p>This file groups the graph-database data structures: {@link InputGraph}
 * itself, and the small helper {@link IntList} used to store vertex labels
 * without the boxing overhead of {@code List<Integer>}.
 *
 * @author Chen Hui
 */

/**
 * A minimal growable primitive {@code int} list, used in place of
 * {@code List<Integer>} for vertex labels to avoid autoboxing on every
 * add/get, which is otherwise performed for essentially every candidate
 * generated during mining.
 */
final class IntList {
    /** Backing storage; only the first {@link #size} entries are valid. */
    private int[] data;
    /** Number of valid entries currently stored. */
    private int size;

    /** Create an empty list with a small default capacity. */
    IntList() {
        data = new int[4];
    }

    /**
     * Create an empty list with the given initial capacity.
     *
     * @param capacity the initial capacity (at least 4)
     */
    IntList(int capacity) {
        data = new int[Math.max(capacity, 4)];
    }

    /**
     * Create a copy of {@code other}.
     *
     * @param other the list to copy
     */
    IntList(IntList other) {
        data = Arrays.copyOf(other.data, other.size);
        size = other.size;
    }

    /**
     * Append a value, growing the backing array if necessary.
     *
     * @param value the value to append
     */
    void add(int value) {
        if (size == data.length) {
            data = Arrays.copyOf(data, data.length == 0 ? 4 : data.length * 2);
        }
        data[size++] = value;
    }

    /**
     * Return the value at {@code index}.
     *
     * @param index the index to read
     * @return the value stored at {@code index}
     */
    int get(int index) {
        return data[index];
    }

    /**
     * Return the number of values stored.
     *
     * @return the current size
     */
    int size() {
        return size;
    }

    /**
     * Return a trimmed copy of the stored values.
     *
     * @return a new array containing exactly the stored values
     */
    int[] toArray() {
        return Arrays.copyOf(data, size);
    }
}

/** One graph from the input database. */
class InputGraph {

    /** Sentinel returned by {@link #getEdgeLabel} when no edge exists between two vertices. */
    static final int NO_EDGE = -1;

    /** The id of this graph within the database. */
    int id;
    /** The label of each vertex, indexed by 0-based vertex id. */
    IntList vertexLabels = new IntList();
    /** The edges of the graph, each stored as [v1, v2, edgeLabel]. */
    List<int[]> edges = new ArrayList<>();

    /**
     * Flat CSR-style (Compressed Sparse Row) adjacency representation, used
     * instead of a {@code List<List<int[]>>} to avoid allocating one
     * {@code ArrayList} per vertex plus one boxed {@code int[]} pair per
     * edge endpoint, and the associated iterator overhead of walking those
     * lists. This matters a great deal because neighbour traversal
     * ({@link #getEdgeLabel}, and the Type-A extension loop in
     * {@link AlgoFSMA#extendEmbedding}) is by far the hottest part of
     * candidate generation.
     *
     * <p>{@code adjStart[v] .. adjStart[v + 1] - 1} is the range, in
     * {@link #adjNeighbor}/{@link #adjLabel}, of vertex {@code v}'s incident edges.
     */
    int[] adjStart;
    /** Flattened neighbour ids, grouped by vertex according to {@link #adjStart}. */
    int[] adjNeighbor;
    /** Flattened edge labels, parallel to {@link #adjNeighbor}. */
    int[] adjLabel;

    /**
     * Build (or rebuild) the CSR adjacency representation from {@link #edges}.
     */
    void buildAdjacency() {
        int n = vertexLabels.size();
        int[] degree = new int[n];
        for (int[] e : edges) {
            degree[e[0]]++;
            degree[e[1]]++;
        }
        adjStart = new int[n + 1];
        for (int i = 0; i < n; i++) {
            adjStart[i + 1] = adjStart[i] + degree[i];
        }
        adjNeighbor = new int[adjStart[n]];
        adjLabel    = new int[adjStart[n]];
        int[] fill  = adjStart.clone();
        for (int[] e : edges) {
            int a = e[0];
            int b = e[1];
            int l = e[2];
            adjNeighbor[fill[a]] = b;
            adjLabel[fill[a]]    = l;
            fill[a]++;
            adjNeighbor[fill[b]] = a;
            adjLabel[fill[b]]    = l;
            fill[b]++;
        }
    }

    /**
     * Return the number of edges incident to vertex {@code v}.
     *
     * @param v the vertex
     * @return the degree of {@code v}
     */
    int degree(int v) {
        return adjStart[v + 1] - adjStart[v];
    }

    /**
     * Return the label of the edge between {@code u} and {@code v}, or
     * {@link #NO_EDGE} if no such edge exists. Assumes edge labels are non-negative.
     *
     * @param u one endpoint
     * @param v the other endpoint
     * @return the edge label, or {@link #NO_EDGE} if absent
     */
    int getEdgeLabel(int u, int v) {
        int s = adjStart[u];
        int e = adjStart[u + 1];
        for (int k = s; k < e; k++) {
            if (adjNeighbor[k] == v) {
                return adjLabel[k];
            }
        }
        return NO_EDGE;
    }

    /**
     * Remove vertices whose label is not in {@code freqLabels}, together
     * with all incident edges, and compact the vertex indices.
     *
     * @param freqLabels the set of vertex labels to keep
     */
    void pruneVertices(Set<Integer> freqLabels) {
        int   n        = vertexLabels.size();
        int[] newIndex = new int[n];
        Arrays.fill(newIndex, -1);

        // assign new contiguous indices to surviving vertices
        IntList newLabels = new IntList(n);
        for (int i = 0; i < n; i++) {
            int lbl = vertexLabels.get(i);
            if (freqLabels.contains(lbl)) {
                newIndex[i] = newLabels.size();
                newLabels.add(lbl);
            }
        }

        // keep only edges whose both endpoints survived
        List<int[]> newEdges = new ArrayList<>();
        for (int[] e : edges) {
            if (newIndex[e[0]] != -1 && newIndex[e[1]] != -1) {
                newEdges.add(new int[]{newIndex[e[0]], newIndex[e[1]], e[2]});
            }
        }

        vertexLabels = newLabels;
        edges        = newEdges;
        buildAdjacency();
    }

    /**
     * Remove edges whose (vertex-label, vertex-label, edge-label) type is
     * not in {@code freqEdgeTypes}, then rebuild the adjacency list.
     *
     * @param freqEdgeTypes the set of edge type keys to keep
     */
    void pruneEdgesByType(Set<String> freqEdgeTypes) {
        List<int[]> newEdges = new ArrayList<>();
        for (int[] e : edges) {
            int l0 = vertexLabels.get(e[0]);
            int l1 = vertexLabels.get(e[1]);
            if (freqEdgeTypes.contains(AlgoFSMA.edgeTypeKey(l0, l1, e[2]))) {
                newEdges.add(e);
            }
        }
        edges = newEdges;
        buildAdjacency();
    }
}