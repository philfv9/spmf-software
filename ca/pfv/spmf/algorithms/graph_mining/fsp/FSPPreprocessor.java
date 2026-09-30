// ================= File: FSPPreprocessor.java =================
package ca.pfv.spmf.algorithms.graph_mining.fsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ca.pfv.spmf.algorithms.graph_mining.tkg.DFSCode;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Edge;
import ca.pfv.spmf.algorithms.graph_mining.tkg.ExtendedEdge;
import ca.pfv.spmf.algorithms.graph_mining.tkg.FrequentSubgraph;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Graph;
import ca.pfv.spmf.algorithms.graph_mining.tkg.SparseTriangularMatrix;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Vertex;

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
 * Implements the preprocessing steps of the FSP algorithm (see
 * {@link AlgoFSP} for the general description of FSP): FSP Algorithm 1,
 * lines 1-3, i.e. computing vertex/edge label frequencies, removing infrequent
 * labels, and relabeling the remaining ones by ascending/descending frequency
 * order, as well as the (gSpan-inherited) infrequent vertex-pair / edge-label
 * pruning strategy applied afterwards on the relabeled graph database.
 * <p>
 * This class holds no state of its own: it reads and updates the relevant
 * fields (support/statistics counters, label maps, etc.) directly on the
 * {@link AlgoFSP} instance it was created with, exactly as this code used to
 * behave when it was part of {@code AlgoFSP} itself.
 * </p>
 *
 * @author Chen Hui (based on the SPMF gSpan implementation by Chao Cheng)
 */
class FSPPreprocessor {

	/** the algorithm instance this preprocessor operates on */
	private final AlgoFSP algo;

	/**
	 * Create a new preprocessor bound to a given algorithm instance.
	 *
	 * @param algo the algorithm instance
	 */
	FSPPreprocessor(AlgoFSP algo) {
		this.algo = algo;
	}

	/**
	 * Compute a unique key for an (undirected) edge, used to avoid processing the
	 * same edge twice (it is referenced from both of its endpoint vertices).
	 *
	 * @param v1 first vertex id
	 * @param v2 second vertex id
	 * @return a unique key identifying the unordered pair (v1, v2)
	 */
	private long edgeKey(int v1, int v2) {
		int a = Math.min(v1, v2);
		int b = Math.max(v1, v2);
		return ((long) a << 32) | (b & 0xffffffffL);
	}

	/**
	 * Compare two labels by their support (frequency), using ascending or
	 * descending order depending on {@link AlgoFSP#useDescendingLabelOrder}, with
	 * the label id as tie-breaker.
	 *
	 * @param supportMap the map from label to its set of supporting graph ids
	 * @param a          the first label
	 * @param b          the second label
	 * @return a negative, zero, or positive value following the standard Comparator
	 *         contract
	 */
	private int compareByFrequency(Map<Integer, Set<Integer>> supportMap, int a, int b) {
		int supA = supportMap.get(a).size();
		int supB = supportMap.get(b).size();
		if (algo.useDescendingLabelOrder) {
			if (supB != supA) {
				return supB - supA;
			}
		} else {
			if (supA != supB) {
				return supA - supB;
			}
		}
		return a - b; // tie-break for determinism
	}

	/**
	 * FSP Algorithm 1, lines 1-3: compute the frequency of vertex and edge labels,
	 * remove the infrequent ones, and relabel the remaining ones by
	 * ascending/descending order of frequency. This also fills
	 * {@link AlgoFSP#frequentVertexLabels} and, if requested, adds the frequent
	 * single-vertex subgraphs to the result.
	 *
	 * @param graphDB                the original graph database
	 * @param outputFrequentVertices if true, single-vertex frequent subgraphs are
	 *                               added to the result
	 * @return a new graph database where labels have been relabeled and infrequent
	 *         vertices/edges removed
	 */
	List<Graph> relabelAndFilter(List<Graph> graphDB, boolean outputFrequentVertices) {

		algo.frequentVertexLabels = new ArrayList<Integer>();

		// compute vertex label support (number of graphs containing each label)
		final Map<Integer, Set<Integer>> vLabelSupport = new HashMap<>();
		for (Graph g : graphDB) {
			Set<Integer> seenLabels = new HashSet<>();
			for (Vertex v : g.getNonPrecalculatedAllVertices()) {
				if (!v.getEdgeList().isEmpty()) {
					seenLabels.add(v.getLabel());
				}
			}
			for (Integer label : seenLabels) {
				Set<Integer> set = vLabelSupport.get(label);
				if (set == null) {
					set = new HashSet<>();
					vLabelSupport.put(label, set);
				}
				set.add(g.getId());
			}
		}

		// compute edge label support (number of graphs containing each label)
		final Map<Integer, Set<Integer>> eLabelSupport = new HashMap<>();
		for (Graph g : graphDB) {
			Set<Integer> seenELabels = new HashSet<>();
			for (Vertex v : g.getNonPrecalculatedAllVertices()) {
				for (Edge e : v.getEdgeList()) {
					seenELabels.add(e.getEdgeLabel());
				}
			}
			for (Integer eLabel : seenELabels) {
				Set<Integer> set = eLabelSupport.get(eLabel);
				if (set == null) {
					set = new HashSet<>();
					eLabelSupport.put(eLabel, set);
				}
				set.add(g.getId());
			}
		}

		// determine which labels are frequent
		List<Integer> freqVLabels = new ArrayList<>();
		for (Entry<Integer, Set<Integer>> e : vLabelSupport.entrySet()) {
			if (e.getValue().size() >= algo.minSup) {
				freqVLabels.add(e.getKey());
			} else {
				algo.infrequentVerticesRemovedCount++;
			}
		}
		List<Integer> freqELabels = new ArrayList<>();
		for (Entry<Integer, Set<Integer>> e : eLabelSupport.entrySet()) {
			if (e.getValue().size() >= algo.minSup) {
				freqELabels.add(e.getKey());
			} else {
				algo.edgeRemovedByLabel++;
			}
		}

		// sort labels by frequency to determine the new (relabeled) ids
		Collections.sort(freqVLabels, (a, b) -> compareByFrequency(vLabelSupport, a, b));
		Collections.sort(freqELabels, (a, b) -> compareByFrequency(eLabelSupport, a, b));

		// build the old<->new label maps
		algo.vertexLabelOldToNew = new HashMap<>();
		algo.vertexLabelNewToOld = new HashMap<>();
		for (int i = 0; i < freqVLabels.size(); i++) {
			algo.vertexLabelOldToNew.put(freqVLabels.get(i), i);
			algo.vertexLabelNewToOld.put(i, freqVLabels.get(i));
			algo.frequentVertexLabels.add(i);
		}
		algo.edgeLabelOldToNew = new HashMap<>();
		algo.edgeLabelNewToOld = new HashMap<>();
		for (int i = 0; i < freqELabels.size(); i++) {
			algo.edgeLabelOldToNew.put(freqELabels.get(i), i);
			algo.edgeLabelNewToOld.put(i, freqELabels.get(i));
		}

		// rebuild the graph database, keeping only frequent vertices/edges with
		// relabeled ids
		List<Graph> newGraphDB = new ArrayList<>();
		for (Graph g : graphDB) {
			Map<Integer, Vertex> newVMap = new HashMap<>();

			// create the vertices that survive (frequent label)
			for (Vertex v : g.getNonPrecalculatedAllVertices()) {
				Integer newLabel = algo.vertexLabelOldToNew.get(v.getLabel());
				if (newLabel != null) {
					newVMap.put(v.getId(), new Vertex(v.getId(), newLabel));
				}
			}

			// add the edges that survive (frequent label + both endpoints survive)
			Set<Long> addedEdges = new HashSet<>();
			for (Vertex v : g.getNonPrecalculatedAllVertices()) {
				if (!newVMap.containsKey(v.getId())) {
					continue;
				}
				for (Edge e : v.getEdgeList()) {
					int other = e.another(v.getId());
					if (!newVMap.containsKey(other)) {
						continue;
					}
					Integer newEdgeLabel = algo.edgeLabelOldToNew.get(e.getEdgeLabel());
					if (newEdgeLabel == null) {
						continue;
					}
					long key = edgeKey(e.v1, e.v2);
					if (addedEdges.contains(key)) {
						continue;
					}
					addedEdges.add(key);
					Edge newEdge = new Edge(e.v1, e.v2, newEdgeLabel);
					newVMap.get(e.v1).addEdge(newEdge);
					newVMap.get(e.v2).addEdge(newEdge);
				}
			}

			newGraphDB.add(new Graph(g.getId(), newVMap));
		}

		// output single-vertex frequent subgraphs, if requested
		if (outputFrequentVertices) {
			for (int i = 0; i < freqVLabels.size(); i++) {
				Set<Integer> support = vLabelSupport.get(freqVLabels.get(i));
				DFSCode tempD = new DFSCode();
				tempD.add(new ExtendedEdge(0, 0, i, i, -1));
				algo.frequentSubgraphs.add(new FrequentSubgraph(tempD, support, support.size()));
			}
		}

		return newGraphDB;
	}

	/**
	 * Pair of vertex labels, used by the vertex-pair pruning matrix.
	 */
	class Pair {
		/** the smaller of the two labels */
		int x;
		/** the larger of the two labels */
		int y;

		/**
		 * Create a new (unordered) pair of labels.
		 *
		 * @param x first label
		 * @param y second label
		 */
		Pair(int x, int y) {
			if (x < y) {
				this.x = x;
				this.y = y;
			} else {
				this.x = y;
				this.y = x;
			}
		}

		@Override
		public boolean equals(Object obj) {
			Pair other = (Pair) obj;
			return other.x == this.x && other.y == this.y;
		}

		@Override
		public int hashCode() {
			return x + 100 * y;
		}
	}

	/**
	 * Create the pruning matrix and remove infrequent vertex pairs / edge labels
	 * (identical strategy as gSpan, applied here to the already relabeled graph
	 * database).
	 *
	 * @param graphDB the relabeled graph database (modified in place)
	 */
	void removeInfrequentVertexPairs(List<Graph> graphDB) {

		Set<Pair> alreadySeenPair = null;
		SparseTriangularMatrix matrix = null;
		if (AlgoFSP.ELIMINATE_INFREQUENT_VERTEX_PAIRS) {
			matrix = new SparseTriangularMatrix();
			alreadySeenPair = new HashSet<Pair>();
		}

		Set<Integer> alreadySeenEdgeLabel = null;
		Map<Integer, Integer> mapEdgeLabelToSupport = null;
		if (AlgoFSP.ELIMINATE_INFREQUENT_EDGE_LABELS) {
			mapEdgeLabelToSupport = new HashMap<Integer, Integer>();
			alreadySeenEdgeLabel = new HashSet<Integer>();
		}

		// count, per graph, how many graphs contain each vertex-pair / edge label
		for (Graph g : graphDB) {
			for (Vertex v1 : g.getAllVertices()) {
				int labelV1 = v1.getLabel();

				for (Edge edge : v1.getEdgeList()) {
					int v2 = edge.another(v1.getId());
					int labelV2 = g.getVLabel(v2);

					if (AlgoFSP.ELIMINATE_INFREQUENT_VERTEX_PAIRS) {
						Pair pair = new Pair(labelV1, labelV2);
						boolean seen = alreadySeenPair.contains(pair);
						if (!seen) {
							matrix.incrementCount(labelV1, labelV2);
							alreadySeenPair.add(pair);
						}
					}

					if (AlgoFSP.ELIMINATE_INFREQUENT_EDGE_LABELS) {
						int edgeLabel = edge.getEdgeLabel();
						if (!alreadySeenEdgeLabel.contains(edgeLabel)) {
							alreadySeenEdgeLabel.add(edgeLabel);

							Integer edgeSupport = mapEdgeLabelToSupport.get(edgeLabel);
							if (edgeSupport == null) {
								mapEdgeLabelToSupport.put(edgeLabel, 1);
							} else {
								mapEdgeLabelToSupport.put(edgeLabel, edgeSupport + 1);
							}
						}
					}
				}
			}
			if (AlgoFSP.ELIMINATE_INFREQUENT_VERTEX_PAIRS) {
				alreadySeenPair.clear();
			}
			if (AlgoFSP.ELIMINATE_INFREQUENT_EDGE_LABELS) {
				alreadySeenEdgeLabel.clear();
			}
		}

		if (AlgoFSP.ELIMINATE_INFREQUENT_VERTEX_PAIRS) {
			if (AlgoFSP.DEBUG_MODE) {
				System.out.println("Removing infrequent pairs...  minsup = " + algo.minSup);
			}
			matrix.removeInfrequentEntriesFromMatrix(algo.minSup);
		}

		// remove edges whose vertex-pair or edge label is infrequent
		if (AlgoFSP.ELIMINATE_INFREQUENT_VERTEX_PAIRS || AlgoFSP.ELIMINATE_INFREQUENT_EDGE_LABELS) {
			for (Graph g : graphDB) {
				for (Vertex v1 : g.getAllVertices()) {
					int labelV1 = v1.getLabel();

					Iterator<Edge> iter = v1.getEdgeList().iterator();
					while (iter.hasNext()) {
						Edge edge = iter.next();
						int v2 = edge.another(v1.getId());
						int labelV2 = g.getVLabel(v2);

						if (AlgoFSP.ELIMINATE_INFREQUENT_VERTEX_PAIRS) {
							int count = matrix.getSupportForItems(labelV1, labelV2);
							if (count < algo.minSup) {
								iter.remove();
								algo.infrequentVertexPairsRemoved++;
								continue;
							}
						}

						if (AlgoFSP.ELIMINATE_INFREQUENT_EDGE_LABELS) {
							Integer sup = mapEdgeLabelToSupport.get(edge.getEdgeLabel());
							if (sup == null || sup < algo.minSup) {
								iter.remove();
								algo.edgeRemovedByLabel++;
							}
						}
					}
				}
			}
		}
	}
}