// =============================================================================
package ca.pfv.spmf.algorithms.graph_mining.ffsm;

import java.util.ArrayList;
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
 * Holds the graph-transaction representation ({@link Graph}) used by {@link AlgoFFSM} to
 * discover frequent connected subgraphs, based on Huan, Wang &amp; Prins, "Efficient Mining
 * of Frequent Subgraph in the Presence of Isomorphism", ICDM 2003 (the FFSM algorithm).
 * <p>
 * FFSM grows every frequent pattern using an explicit embedding set (the set of vertex
 * mappings of the pattern into every graph transaction), so no subgraph-isomorphism
 * matcher is needed once a pattern's parent embedding set is known.
 *
 * @author Chen Hui
 */
public class FFSMGraph {

    /** Not meant to be instantiated: this class only groups related static types. */
    private FFSMGraph() { }

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
        /** Neighbor vertex ids for each vertex. */
        int[][] neighborIds;
        /** Edge labels matching {@link #neighborIds} for each vertex. */
        int[][] neighborLabels;

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
                // first time seeing this vertex: assign the next dense id
                dense = vlabelsBuild.size();
                origToDense.put(vid, dense);
                vlabelsBuild.add(label);
                adjBuild.add(new ArrayList<>());
            } else {
                vlabelsBuild.set(dense, label); // update label of an already-declared vertex
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
            long ek = ((long) a << 32) | (b & 0xFFFFFFFFL); // normalized undirected edge key
            if (!edgeSeen.add(ek)) {
                int existingLabel = label;
                for (int[] nb : adjBuild.get(dp))
                    if (nb[0] == dq) { existingLabel = nb[1]; break; }
                throw new IllegalArgumentException(
                        "Parallel edge (" + p + "," + q + ") existing=" + existingLabel
                        + " new=" + label);
            }
            adjBuild.get(dp).add(new int[]{dq, label}); // add both directions of the adjacency
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
            for (int i = 0; i < n; i++) vlabels[i] = vlabelsBuild.get(i); // copy vertex labels
            neighborIds    = new int[n][];
            neighborLabels = new int[n][];
            for (int i = 0; i < n; i++) { // flatten adjacency lists into arrays
                List<int[]> l    = adjBuild.get(i);
                int         sz   = l.size();
                int[]       ids  = new int[sz];
                int[]       labs = new int[sz];
                for (int k = 0; k < sz; k++) { ids[k] = l.get(k)[0]; labs[k] = l.get(k)[1]; }
                neighborIds[i]    = ids;
                neighborLabels[i] = labs;
            }
            origToDense = null; vlabelsBuild = null; adjBuild = null; edgeSeen = null; // release construction state
        }
    }
}