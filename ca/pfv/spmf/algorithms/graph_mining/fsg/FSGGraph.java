package ca.pfv.spmf.algorithms.graph_mining.fsg;

import java.util.ArrayList;
import java.util.Arrays;
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
 * Holds the graph-transaction representation ({@link Graph}) and the subgraph-isomorphism
 * matcher ({@link PatternMatcher}) used by {@link AlgoFSG} to discover frequent connected
 * subgraphs, based on Kuramochi &amp; Karypis, "Frequent Subgraph Discovery", ICDM 2001.
 * This class is only a namespace for the two related static classes below; it is not meant
 * to be instantiated.
 * 
 * @author Chen Hui
 */
public class FSGGraph {

    /** Not meant to be instantiated: this class only groups related static types. */
    private FSGGraph() { }

    /**
     * A single input graph transaction stored as flat primitive arrays after loading.
     * Dense vertex ids {@code 0..n-1} are assigned in declaration order; the original
     * input ids are only used during parsing and are discarded by {@link #finalizeGraph()}.
     */
    static class Graph {
        /** Identifier of this graph transaction. */
        int id;

        /** Maps original vertex ids to dense ids (construction only). */
        private Map<Integer, Integer> origToDense  = new HashMap<>();
        /** Vertex labels being built (construction only). */
        private List<Integer>         vlabelsBuild = new ArrayList<>();
        /** Adjacency lists being built (construction only). */
        private List<List<int[]>>     adjBuild     = new ArrayList<>();
        /** Set of edge keys already added, used to detect duplicates (construction only). */
        private Set<Long>             edgeSeen     = new HashSet<>();

        /** Vertex labels indexed by dense vertex id. */
        int[]   vlabels;
        /** Neighbor vertex ids for each vertex, sorted ascending (see {@link #finalizeGraph()}). */
        int[][] neighborIds;
        /** Edge labels matching {@link #neighborIds} for each vertex. */
        int[][] neighborLabels;

        /** Lazily built index from vertex label to vertex ids. */
        private Map<Integer, int[]> labelIndex;

        /**
         * Creates a new, empty graph transaction.
         * @param id identifier of the transaction
         */
        Graph(int id) { this.id = id; }

        /**
         * Declares (or updates the label of) a vertex using its original input id.
         * @param vid   original vertex id from the input file
         * @param label label of the vertex
         */
        void addVertex(int vid, int label) {
            Integer dense = origToDense.get(vid);
            if (dense == null) {
                // first time seeing this original id: assign the next dense id
                dense = vlabelsBuild.size();
                origToDense.put(vid, dense);
                vlabelsBuild.add(label);
                adjBuild.add(new ArrayList<>());
            } else {
                vlabelsBuild.set(dense, label);
            }
        }

        /**
         * Declares an undirected edge between two already-declared vertices.
         * @param p     original id of the first endpoint
         * @param q     original id of the second endpoint
         * @param label label of the edge
         * @throws IllegalArgumentException if the edge is a self-loop, a parallel edge,
         *                                   or references an undeclared vertex
         */
        void addEdge(int p, int q, int label) {
            if (p == q)
                throw new IllegalArgumentException(
                        "Self-loops are not supported (vertex " + p + ")");
            Integer dp = origToDense.get(p);
            Integer dq = origToDense.get(q);
            if (dp == null || dq == null)
                throw new IllegalArgumentException(
                        "Edge (" + p + "," + q + ") references an undeclared vertex");
            int  a  = Math.min(dp, dq), b = Math.max(dp, dq);
            long ek = ((long) a << 32) | (b & 0xFFFFFFFFL);
            if (!edgeSeen.add(ek)) {
                int existingLabel = label;
                for (int[] nb : adjBuild.get(dp))
                    if (nb[0] == dq) { existingLabel = nb[1]; break; }
                throw new IllegalArgumentException(
                        "Parallel edge (" + p + "," + q + ") existing=" + existingLabel
                        + " new=" + label);
            }
            // record the edge on both endpoints' adjacency lists
            adjBuild.get(dp).add(new int[]{dq, label});
            adjBuild.get(dq).add(new int[]{dp, label});
        }

        /**
         * Converts construction-time lists to flat primitive arrays and releases all
         * construction-only state. Must be called exactly once after all "v"/"e" lines
         * for this transaction have been processed.
         */
        void finalizeGraph() {
            int n   = vlabelsBuild.size();
            vlabels = new int[n];
            for (int i = 0; i < n; i++) vlabels[i] = vlabelsBuild.get(i);
            neighborIds    = new int[n][];
            neighborLabels = new int[n][];
            // flatten each vertex's adjacency list into parallel primitive arrays
            for (int i = 0; i < n; i++) {
                List<int[]> l    = adjBuild.get(i);
                int         sz   = l.size();
                int[]       ids  = new int[sz];
                int[]       labs = new int[sz];
                for (int k = 0; k < sz; k++) { ids[k] = l.get(k)[0]; labs[k] = l.get(k)[1]; }
                // sort once, by neighbor id, so that edge-existence checks done during
                // subgraph matching (see PatternMatcher.backtrack) can binary-search a
                // vertex's neighbor list instead of scanning it linearly every time
                sortNeighborsByTarget(ids, labs);
                neighborIds[i]    = ids;
                neighborLabels[i] = labs;
            }
            origToDense = null; vlabelsBuild = null; adjBuild = null; edgeSeen = null;
        }

        /**
         * Sorts a vertex's (neighbor id, edge label) pairs by neighbor id, both arrays
         * reordered in lock-step. Encodes each pair as a single {@code long} (neighbor id
         * in the high bits, original position in the low bits) so a single primitive
         * {@code long[]} sort can be used instead of boxing into {@code Integer[]}.
         * @param ids  neighbor ids (sorted in place)
         * @param labs edge labels matching {@code ids} (reordered in place to match)
         */
        private static void sortNeighborsByTarget(int[] ids, int[] labs) {
            int k = ids.length;
            if (k < 2) return;
            long[] combined = new long[k];
            for (int i = 0; i < k; i++) combined[i] = ((long) ids[i] << 32) | (i & 0xFFFFFFFFL);
            Arrays.sort(combined);
            int[] newIds  = new int[k];
            int[] newLabs = new int[k];
            for (int i = 0; i < k; i++) {
                int orig   = (int) (combined[i] & 0xFFFFFFFFL);
                newIds[i]  = ids[orig];
                newLabs[i] = labs[orig];
            }
            System.arraycopy(newIds, 0, ids, 0, k);
            System.arraycopy(newLabs, 0, labs, 0, k);
        }

        /**
         * Binary-searches this vertex's (sorted) neighbor list for {@code neighbor}. Used
         * to check whether an edge exists in O(log degree) instead of scanning the
         * neighbor list linearly, which matters because this check runs once per
         * already-mapped pattern neighbor for every candidate vertex examined during
         * subgraph matching.
         * @param vertex   the vertex whose neighbor list is searched
         * @param neighbor the neighbor id being looked for
         * @return the index into {@link #neighborIds}[vertex] / {@link #neighborLabels}[vertex]
         *         at which {@code neighbor} is stored, or -1 if it is not a neighbor
         */
        int findNeighborIndex(int vertex, int neighbor) {
            int[] ids = neighborIds[vertex];
            int   lo  = 0, hi = ids.length - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                int v   = ids[mid];
                if (v == neighbor) return mid;
                if (v < neighbor) lo = mid + 1; else hi = mid - 1;
            }
            return -1;
        }

        /**
         * Returns (building lazily) a map from vertex label to the dense vertex ids that
         * carry it, stored as a primitive {@code int[]} to avoid boxing in the search.
         * @return map from vertex label to array of vertex ids having that label
         */
        Map<Integer, int[]> verticesByLabel() {
            if (labelIndex == null) {
                // build the index once and cache it
                Map<Integer, List<Integer>> tmp = new HashMap<>();
                for (int i = 0; i < vlabels.length; i++)
                    tmp.computeIfAbsent(vlabels[i], k -> new ArrayList<>()).add(i);
                labelIndex = new HashMap<>();
                for (Map.Entry<Integer, List<Integer>> e : tmp.entrySet()) {
                    List<Integer> l   = e.getValue();
                    int[]         arr = new int[l.size()];
                    for (int i = 0; i < arr.length; i++) arr[i] = l.get(i);
                    labelIndex.put(e.getKey(), arr);
                }
            }
            return labelIndex;
        }
    }

    /**
     * Precomputes everything about a pattern needed for isomorphism search, so it is
     * computed once per pattern and reused across every target graph tested.
     */
    static final class PatternMatcher {

        /** Reusable empty array returned for "no candidates" without allocating. */
        private static final int[] EMPTY_INT_ARRAY = new int[0];

        /** Vertex labels of the pattern being matched. */
        final int[]   vlabels;
        /** Pattern adjacency: neighbor vertex ids for each vertex (parallel to {@link #adjLbl}).
         *  Stored as primitive arrays rather than {@code List<int[]>} because this is walked
         *  once per candidate vertex in the hot backtracking loop, and array iteration avoids
         *  the per-call Iterator allocation that {@code List} iteration would incur. */
        final int[][] adjNbr;
        /** Pattern adjacency: edge labels matching {@link #adjNbr}. */
        final int[][] adjLbl;
        /** BFS visiting order of pattern vertices. */
        final int[]   order;
        /** BFS parent of each pattern vertex (-1 for roots). */
        final int[]   parent;
        /** Edge label connecting each vertex to its BFS parent. */
        final int[]   parentLabel;
        /** Number of vertices in the pattern. */
        final int     n;

        /**
         * Builds the matcher for a given pattern.
         * @param vlabels vertex labels of the pattern
         * @param edges   edges of the pattern
         */
        PatternMatcher(int[] vlabels, int[][] edges) {
            this.vlabels = vlabels;
            this.n       = vlabels.length;
            int[][][] adjPair = buildPatternAdj(n, edges);
            this.adjNbr  = adjPair[0];
            this.adjLbl  = adjPair[1];
            this.order   = bfsOrder(n, adjNbr);
            int[] pos    = new int[n];
            for (int i = 0; i < n; i++) pos[order[i]] = i;
            this.parent      = new int[n];
            this.parentLabel = new int[n];
            Arrays.fill(parent, -1);
            // record, for each non-root vertex, its first-visited neighbor as BFS parent
            for (int i = 1; i < n; i++) {
                int   v    = order[i];
                int[] nbrs = adjNbr[v];
                int[] lbls = adjLbl[v];
                for (int k = 0; k < nbrs.length; k++)
                    if (pos[nbrs[k]] < i) { parent[v] = nbrs[k]; parentLabel[v] = lbls[k]; break; }
            }
        }

        /**
         * Builds an adjacency representation for a pattern graph as two parallel primitive
         * arrays per vertex: neighbor ids and matching edge labels.
         * @param n     number of vertices
         * @param edges edges as (v1, v2, label) triples
         * @return a 2-element array {@code {neighborIds, neighborLabels}}, each indexed by vertex id
         */
        private static int[][][] buildPatternAdj(int n, int[][] edges) {
            int[] degree = new int[n];
            for (int[] e : edges) { degree[e[0]]++; degree[e[1]]++; }
            int[][] nbr = new int[n][];
            int[][] lbl = new int[n][];
            for (int i = 0; i < n; i++) { nbr[i] = new int[degree[i]]; lbl[i] = new int[degree[i]]; }
            int[] fillPos = new int[n];
            for (int[] e : edges) {
                int a = e[0], b = e[1], l = e[2];
                nbr[a][fillPos[a]] = b; lbl[a][fillPos[a]] = l; fillPos[a]++;
                nbr[b][fillPos[b]] = a; lbl[b][fillPos[b]] = l; fillPos[b]++;
            }
            return new int[][][]{nbr, lbl};
        }

        /**
         * Computes a breadth-first visiting order of all pattern vertices, restarting from
         * a new unvisited vertex whenever the current component is exhausted.
         * @param n      number of vertices
         * @param adjNbr pattern adjacency (neighbor ids) indexed by vertex id
         * @return vertex ids in BFS order
         */
        private static int[] bfsOrder(int n, int[][] adjNbr) {
            boolean[] visited = new boolean[n];
            int[]     order   = new int[n];
            int[]     queue   = new int[n];
            int       oi      = 0;
            for (int start = 0; start < n; start++) {
                if (visited[start]) continue;
                // start a new BFS from this unvisited vertex
                int head = 0, tail = 0;
                visited[start] = true;
                queue[tail++]  = start;
                while (head < tail) {
                    int   u    = queue[head++];
                    order[oi++] = u;
                    int[] nbrs = adjNbr[u];
                    for (int k = 0; k < nbrs.length; k++) {
                        int v = nbrs[k];
                        if (!visited[v]) { visited[v] = true; queue[tail++] = v; }
                    }
                }
            }
            return order;
        }

        /**
         * Finds embeddings of this pattern in the given target graph.
         * @param target  the graph to search in
         * @param findAll if true, finds all embeddings; if false, stops at the first one
         * @return the list of found embeddings (each an array mapping pattern vertex to target vertex)
         */
        List<int[]> findEmbeddings(Graph target, boolean findAll) {
            List<int[]> results = new ArrayList<>();
            if (n == 0) return results;
            int[]     mapping = new int[n];
            Arrays.fill(mapping, -1);
            boolean[] used    = new boolean[target.vlabels.length];
            backtrack(0, n, order, parent, parentLabel, vlabels, adjNbr, adjLbl,
                      target, mapping, used, results, findAll);
            return results;
        }

        /**
         * Tests whether this pattern occurs at least once in the given target graph.
         * @param target the graph to search in
         * @return true if at least one embedding exists
         */
        boolean containsSubgraph(Graph target) {
            if (n == 0) return false;
            int[]     mapping = new int[n];
            Arrays.fill(mapping, -1);
            boolean[] used    = new boolean[target.vlabels.length];
            // backtrack's return value already tells us whether a match was found (findAll=false),
            // so no results list is needed here
            return backtrack(0, n, order, parent, parentLabel, vlabels, adjNbr, adjLbl,
                              target, mapping, used, null, false);
        }

        /**
         * Recursive backtracking step of the subgraph-isomorphism search. Root candidates
         * come from the target's label index; non-root candidates come from the target
         * neighbors of the already-mapped BFS parent, filtered inline with no intermediate
         * allocation. Back-edge verification against already-mapped pattern neighbors uses
         * {@link Graph#findNeighborIndex} (binary search over each target vertex's sorted
         * neighbor list) instead of a linear scan.
         * @param i           index into {@code order} of the pattern vertex being mapped
         * @param n           total number of pattern vertices
         * @param order       BFS order of pattern vertices
         * @param parent      BFS parent of each pattern vertex
         * @param parentLabel edge label to the BFS parent of each pattern vertex
         * @param vlabels     pattern vertex labels
         * @param adjNbr      pattern adjacency (neighbor ids) indexed by vertex id
         * @param adjLbl      pattern adjacency (edge labels) indexed by vertex id
         * @param target      target graph being searched
         * @param mapping     current partial pattern-to-target vertex mapping
         * @param used        marks which target vertices are already used
         * @param results     accumulator for found embeddings; may be {@code null} when
         *                     {@code findAll} is false and only existence matters
         * @param findAll     if true, keep searching after the first embedding is found
         * @return true if the search should stop (a match was found and {@code findAll} is false)
         */
        private static boolean backtrack(int i, int n, int[] order,
                                  int[] parent, int[] parentLabel,
                                  int[] vlabels,
                                  int[][] adjNbr, int[][] adjLbl,
                                  Graph target,
                                  int[] mapping, boolean[] used,
                                  List<int[]> results, boolean findAll) {
            if (i == n) {
                // only clone/store the mapping when the caller actually collects embeddings
                if (findAll) results.add(mapping.clone());
                return !findAll;
            }
            int v = order[i];

            if (i == 0) {
                // root vertex: candidates come from the target's label index
                int[] candidates = target.verticesByLabel()
                                         .getOrDefault(vlabels[v], EMPTY_INT_ARRAY);
                for (int t : candidates) {
                    if (used[t]) continue;
                    mapping[v] = t; used[t] = true;
                    boolean stop = backtrack(i + 1, n, order, parent, parentLabel,
                                             vlabels, adjNbr, adjLbl, target, mapping, used,
                                             results, findAll);
                    mapping[v] = -1; used[t] = false;
                    if (stop) return true;
                }
                return false;
            }

            // non-root vertex: candidates come from the target neighbors of the mapped parent
            int   pt              = mapping[parent[v]];
            int   neededEdgeLabel = parentLabel[v];
            int   neededVLabel    = vlabels[v];
            int[] nbrIds          = target.neighborIds[pt];
            int[] nbrLabels       = target.neighborLabels[pt];

            int[] vNbrIds  = adjNbr[v];
            int[] vNbrLbls = adjLbl[v];

            for (int k = 0; k < nbrIds.length; k++) {
                int t = nbrIds[k];
                if (nbrLabels[k] != neededEdgeLabel) continue;
                if (target.vlabels[t] != neededVLabel) continue;
                if (used[t]) continue;

                // verify all back-edges to already-mapped pattern neighbors exist in the
                // target, using a binary search over t's sorted neighbor list instead of
                // scanning it linearly
                boolean ok = true;
                for (int idx2 = 0; idx2 < vNbrIds.length; idx2++) {
                    int mv = mapping[vNbrIds[idx2]];
                    if (mv == -1) continue;
                    int nIdx = target.findNeighborIndex(t, mv);
                    if (nIdx < 0 || target.neighborLabels[t][nIdx] != vNbrLbls[idx2]) { ok = false; break; }
                }
                if (!ok) continue;

                mapping[v] = t; used[t] = true;
                boolean stop = backtrack(i + 1, n, order, parent, parentLabel,
                                         vlabels, adjNbr, adjLbl, target, mapping, used,
                                         results, findAll);
                mapping[v] = -1; used[t] = false;
                if (stop) return true;
            }
            return false;
        }
    }
}