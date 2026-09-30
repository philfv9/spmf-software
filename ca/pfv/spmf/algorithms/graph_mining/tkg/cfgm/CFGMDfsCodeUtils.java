// ============================================================
// File: CFGMDfsCodeUtils.java
// ============================================================
package ca.pfv.spmf.algorithms.graph_mining.tkg.cfgm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ca.pfv.spmf.algorithms.graph_mining.tkg.DFSCode;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Edge;
import ca.pfv.spmf.algorithms.graph_mining.tkg.ExtendedEdge;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Graph;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Vertex;

/* This file is copyright (c) 2018-2023 by Chao Cheng, Philippe Fournier-Viger
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
 * Utility class holding the DFS-code and embedding computations used by
 * {@code AlgoCFGM}: building and propagating embeddings, enumerating
 * extensions of a DFS code, checking canonical (minimum) DFS codes, and
 * testing "equivalent extensions" used for pruning (Theorem 2 / Corollary
 * 2.1 of the CFGM paper).
 *
 * @author Philippe Fournier-Viger (based on the GSPAN implementation of Chao
 *         Cheng)
 */
class CFGMDfsCodeUtils {

	/** enables edge-count pruning: skips graphs/extensions that cannot contain one more edge than the pattern already has */
	static final boolean EDGE_COUNT_PRUNING = true;

	/** number of extensions/graphs skipped so far because of edge-count pruning */
	private int edgeCountPruneCount = 0;

	/**
	 * Returns the number of extensions/graphs skipped so far due to edge-count pruning.
	 *
	 * @return the prune count
	 */
	int getEdgeCountPruneCount() {
		return edgeCountPruneCount;
	}

	/**
	 * Finds all isomorphisms of the subgraph described by a DFS code into a graph.
	 * Used only once, to bootstrap the embeddings of the initial 1-edge frequent patterns.
	 *
	 * @param c a DFS code representing a subgraph
	 * @param g a graph
	 * @return the list of all isomorphisms (code vertex id -> graph vertex id)
	 */
	List<Map<Integer, Integer>> subgraphIsomorphisms(DFSCode c, Graph g) {

		List<Map<Integer, Integer>> isoms = new ArrayList<>();

		// find all vertices matching the label of the first code vertex
		int startLabel = c.getEeL().get(0).getvLabel1();
		for (int vID : g.findAllWithLabel(startLabel)) {
			Map<Integer, Integer> map = new HashMap<>();
			map.put(0, vID);
			isoms.add(map);
		}

		// extend the partial mappings edge by edge
		for (ExtendedEdge ee : c.getEeL()) {
			int v1 = ee.getV1();
			int v2 = ee.getV2();
			int v2Label = ee.getvLabel2();
			int eLabel = ee.getEdgeLabel();

			List<Map<Integer, Integer>> updateIsoms = new ArrayList<>();
			for (Map<Integer, Integer> iso : isoms) {

				int mappedV1 = iso.get(v1);

				if (v1 < v2) {
					// forward edge: try to map v2 to a new matching neighbor of mappedV1.
					// use a HashSet (not the raw Map.values() view) so that "contains" below is O(1) instead of O(n)
					Set<Integer> mappedVertices = new HashSet<>(iso.values());

					for (Vertex mappedV2 : g.getAllNeighbors(mappedV1)) {

						if (v2Label == mappedV2.getLabel() && (!mappedVertices.contains(mappedV2.getId()))
								&& eLabel == g.getEdgeLabel(mappedV1, mappedV2.getId())) {

							Map<Integer, Integer> tempM = new HashMap<>(iso);
							tempM.put(v2, mappedV2.getId());

							updateIsoms.add(tempM);
						}
					}
				} else {
					// backward edge: check that the edge already exists between the mapped vertices
					int mappedV2 = iso.get(v2);
					if (g.isNeighboring(mappedV1, mappedV2) && eLabel == g.getEdgeLabel(mappedV1, mappedV2)) {
						updateIsoms.add(iso);
					}
				}
			}
			isoms = updateIsoms;
		}

		return isoms;
	}

	/**
	 * Computes all single starting edges (in both directions) and the graph ids where each occurs,
	 * restricted to the given graph ids. Used only once to find the initial 1-edge frequent subgraphs.
	 *
	 * @param graphDatabase the graph database
	 * @param graphIds      the ids of the graphs to consider
	 * @return a map of extended edge (in either direction) to the set of graph ids where it occurs
	 */
	Map<ExtendedEdge, Set<Integer>> computeInitialExtensions(List<Graph> graphDatabase, Set<Integer> graphIds) {

		Map<ExtendedEdge, Set<Integer>> extensions = new HashMap<>();

		for (Integer graphId : graphIds) {
			Graph g = graphDatabase.get(graphId);

			// skip graphs with no edges
			if (EDGE_COUNT_PRUNING && g.getEdgeCount() <= 0) {
				edgeCountPruneCount++;
				continue;
			}

			for (Vertex vertex : g.getAllVertices()) {
				for (Edge e : vertex.getEdgeList()) {
					// an undirected edge appears in the list of both its endpoints; process it only once
					if (vertex.getId() != e.v1) {
						continue;
					}

					// vertex.getId() == e.v1 here, so its label is already known, no lookup needed
					int v1L = vertex.getLabel();
					int v2L = g.getVLabel(e.v2);
					int edgeLabel = e.getEdgeLabel();

					// register the edge in the forward direction
					ExtendedEdge forward = new ExtendedEdge(0, 1, v1L, v2L, edgeLabel);
					extensions.computeIfAbsent(forward, k -> new HashSet<>()).add(graphId);

					// and, if not symmetric, in the backward direction too
					if (v1L != v2L) {
						ExtendedEdge backward = new ExtendedEdge(0, 1, v2L, v1L, edgeLabel);
						extensions.computeIfAbsent(backward, k -> new HashSet<>()).add(graphId);
					}
				}
			}
		}
		return extensions;
	}

	/**
	 * Checks whether a DFS code is canonical (minimal), i.e. the smallest possible DFS code among
	 * all depth-first traversals of the graph it encodes. Mirrors Algorithm 3 ("DFSCodeMin") of the
	 * CFGM paper by growing the canonical prefix incrementally instead of recomputing isomorphisms.
	 *
	 * @param c a DFS code
	 * @return true if it is canonical, false otherwise
	 */
	boolean isCanonical(DFSCode c) {
		Graph g = new Graph(c);

		DFSCode canC = new DFSCode();
		List<Map<Integer, Integer>> mappings = null;

		// grow the canonical code one edge at a time and compare it to c
		for (int i = 0; i < c.size(); i++) {
			Map<ExtendedEdge, List<Map<Integer, Integer>>> extensions = rightMostExtensionsFromMappings(canC, g,
					mappings);

			// find the smallest candidate extension, keeping its mappings directly to
			// avoid a second map lookup once it is chosen
			Entry<ExtendedEdge, List<Map<Integer, Integer>>> minEntry = null;
			for (Entry<ExtendedEdge, List<Map<Integer, Integer>>> entry : extensions.entrySet()) {
				if (minEntry == null || entry.getKey().smallerThan(minEntry.getKey())) {
					minEntry = entry;
				}
			}
			ExtendedEdge minEE = minEntry.getKey();

			// if it is smaller than what c actually has, c is not canonical
			if (minEE.smallerThan(c.getAt(i))) {
				return false;
			}

			canC.add(minEE);
			// keep only the embeddings that realize the chosen minimum extension
			mappings = minEntry.getValue();
		}
		return true;
	}

	/**
	 * Computes every candidate next extension of the canonical DFS code prefix being built by
	 * {@link #isCanonical(DFSCode)}, together with the embeddings that support each candidate.
	 *
	 * @param canC     the canonical DFS code built so far (possibly empty)
	 * @param g        the graph encoded by the original DFS code being tested
	 * @param mappings the embeddings of canC into g (ignored when canC is empty)
	 * @return a map from candidate extended edge to the list of embeddings that realize it
	 */
	private Map<ExtendedEdge, List<Map<Integer, Integer>>> rightMostExtensionsFromMappings(DFSCode canC, Graph g,
			List<Map<Integer, Integer>> mappings) {

		Map<ExtendedEdge, List<Map<Integer, Integer>>> extensions = new HashMap<>();

		if (canC.isEmpty()) {
			// bootstrap: every edge of g is a candidate first edge
			for (Vertex vertex : g.getAllVertices()) {
				for (Edge e : vertex.getEdgeList()) {
					if (vertex.getId() != e.v1) {
						continue;
					}

					// vertex.getId() == e.v1 here, so its label is already known, no lookup needed
					int v1L = vertex.getLabel();
					int v2L = g.getVLabel(e.v2);

					// the endpoint with the smaller label becomes code vertex 0
					int codeV0, codeV1, labelV0, labelV1;
					if (v1L <= v2L) {
						codeV0 = e.v1;
						codeV1 = e.v2;
						labelV0 = v1L;
						labelV1 = v2L;
					} else {
						codeV0 = e.v2;
						codeV1 = e.v1;
						labelV0 = v2L;
						labelV1 = v1L;
					}

					ExtendedEdge ee = new ExtendedEdge(0, 1, labelV0, labelV1, e.getEdgeLabel());
					Map<Integer, Integer> map = new HashMap<>();
					map.put(0, codeV0);
					map.put(1, codeV1);
					extensions.computeIfAbsent(ee, k -> new ArrayList<>()).add(map);
				}
			}
		} else {
			int rightMost = canC.getRightMost();

			for (Map<Integer, Integer> mapping : mappings) {

				// build the inverse mapping (graph vertex id -> code vertex id)
				Map<Integer, Integer> inverted = new HashMap<>();
				for (Entry<Integer, Integer> entry : mapping.entrySet()) {
					inverted.put(entry.getValue(), entry.getKey());
				}

				int mappedRM = mapping.get(rightMost);
				int mappedRMlabel = g.getVLabel(mappedRM);

				// backward candidate extensions from the rightmost vertex
				for (Vertex x : g.getAllNeighbors(mappedRM)) {
					Integer invertedX = inverted.get(x.getId());
					if (invertedX != null && canC.onRightMostPath(invertedX) && canC.notPreOfRM(invertedX)
							&& !canC.containEdge(rightMost, invertedX)) {
						ExtendedEdge ee = new ExtendedEdge(rightMost, invertedX, mappedRMlabel, x.getLabel(),
								g.getEdgeLabel(mappedRM, x.getId()));
						extensions.computeIfAbsent(ee, k -> new ArrayList<>()).add(mapping);
					}
				}

				// forward candidate extensions from vertices on the rightmost path.
				// use a HashSet (not the raw Map.values() view) so "contains" is O(1), reused across both loops above/below
				Set<Integer> mappedVertices = new HashSet<>(mapping.values());
				for (int v : canC.getRightMostPath()) {
					int mappedV = mapping.get(v);
					int mappedVlabel = g.getVLabel(mappedV);
					for (Vertex x : g.getAllNeighbors(mappedV)) {
						if (!mappedVertices.contains(x.getId())) {
							ExtendedEdge ee = new ExtendedEdge(v, rightMost + 1, mappedVlabel, x.getLabel(),
									g.getEdgeLabel(mappedV, x.getId()));
							Map<Integer, Integer> extended = new HashMap<>(mapping);
							extended.put(rightMost + 1, x.getId());
							extensions.computeIfAbsent(ee, k -> new ArrayList<>()).add(extended);
						}
					}
				}
			}
		}
		return extensions;
	}

	/**
	 * Holds, for a single candidate one-edge extension, the graph ids where it occurs and the
	 * concrete per-graph embeddings of the extended pattern.
	 */
	static class ExtensionInfo {

		/** the ids of the graphs where this extension occurs */
		final Set<Integer> graphIds = new HashSet<>();

		/** the embeddings of the extended pattern, per graph id */
		final Map<Integer, List<Map<Integer, Integer>>> embeddings = new HashMap<>();

		/**
		 * Records an occurrence of this extension in a graph.
		 *
		 * @param gid          the graph id
		 * @param extendedIsom the embedding of the extended pattern in that graph
		 */
		void add(int gid, Map<Integer, Integer> extendedIsom) {
			graphIds.add(gid);
			embeddings.computeIfAbsent(gid, k -> new ArrayList<>()).add(extendedIsom);
		}
	}

	/**
	 * Computes every possible one-edge extension of a DFS code (from any existing vertex), the
	 * graph ids where each occurs, and the embeddings of each extension, reusing the parent
	 * pattern's embeddings instead of recomputing isomorphism from scratch.
	 *
	 * @param c              the current (non empty) DFS code
	 * @param nodeEmbeddings the concrete per-graph embeddings of c
	 * @param graphDB        the graph database
	 * @return a map from extended edge to {@link ExtensionInfo}
	 */
	Map<ExtendedEdge, ExtensionInfo> computeFullExtensions(DFSCode c,
			Map<Integer, List<Map<Integer, Integer>>> nodeEmbeddings, List<Graph> graphDB) {

		Map<ExtendedEdge, ExtensionInfo> result = new HashMap<>();

		for (Entry<Integer, List<Map<Integer, Integer>>> entry : nodeEmbeddings.entrySet()) {
			int gid = entry.getKey();
			Graph g = graphDB.get(gid);

			// skip graphs that cannot possibly contain any extension of c
			if (EDGE_COUNT_PRUNING && c.size() >= g.getEdgeCount()) {
				edgeCountPruneCount++;
				continue;
			}

			for (Map<Integer, Integer> isom : entry.getValue()) {

				// build the inverse mapping (graph vertex id -> code vertex id)
				Map<Integer, Integer> inverted = new HashMap<>();
				for (Entry<Integer, Integer> e2 : isom.entrySet()) {
					inverted.put(e2.getValue(), e2.getKey());
				}

				int numVertices = isom.size();

				// try extending from every vertex of the current pattern; iterate the map's
				// own entries directly instead of doing a HashMap lookup per index 0..numVertices-1
				for (Entry<Integer, Integer> vEntry : isom.entrySet()) {
					int v = vEntry.getKey();
					int mappedV = vEntry.getValue();
					int labelV = g.getVLabel(mappedV);

					for (Vertex x : g.getAllNeighbors(mappedV)) {
						Integer u = inverted.get(x.getId());

						ExtendedEdge ee;
						Map<Integer, Integer> extendedIsom;

						if (u != null) {
							// candidate edge between two existing code vertices (backward-like);
							// filter out the cheap-to-reject cases before touching the graph again
							if (u.intValue() == v || c.containEdge(v, u)) {
								continue;
							}
							// realize each backward-like edge only once, from its smaller-index endpoint
							if (v > u) {
								continue;
							}
							// only fetch the edge label once the candidate has survived every cheap check
							int edgeLabel = g.getEdgeLabel(mappedV, x.getId());
							ee = new ExtendedEdge(u, v, x.getLabel(), labelV, edgeLabel);
							extendedIsom = isom; // no new vertex: isomorphism is unchanged
						} else {
							// candidate edge to a brand new vertex (forward-like)
							int edgeLabel = g.getEdgeLabel(mappedV, x.getId());
							ee = new ExtendedEdge(v, numVertices, labelV, x.getLabel(), edgeLabel);
							extendedIsom = new HashMap<>(isom);
							extendedIsom.put(numVertices, x.getId());
						}

						result.computeIfAbsent(ee, k -> new ExtensionInfo()).add(gid, extendedIsom);
					}
				}
			}
		}

		return result;
	}

	/**
	 * Checks whether an extended edge is a valid gSpan rightmost path extension of a DFS code.
	 *
	 * @param c  the current DFS code (assumed non empty)
	 * @param ee the candidate extended edge
	 * @return true if this is a rightmost extension
	 */
	boolean isRightmostExtension(DFSCode c, ExtendedEdge ee) {
		int v1 = ee.getV1();
		int v2 = ee.getV2();
		if (v1 < v2) {
			// forward extension: must start on the rightmost path and add the next vertex
			return c.onRightMostPath(v1) && v2 == c.getRightMost() + 1;
		} else {
			// backward extension: must go from the rightmost vertex to the rightmost path
			return v1 == c.getRightMost() && c.onRightMostPath(v2) && c.notPreOfRM(v2);
		}
	}

	/**
	 * Checks whether, in a graph, a forward edge introduces a pendant tree, i.e. every vertex
	 * reachable from the new vertex is not already part of the pattern occurrence and the
	 * reachable region contains no cycle (Definition 3 / Corollary 2.1 of the CFGM paper).
	 *
	 * @param g              the graph
	 * @param fromVertex     the existing (already mapped) endpoint of the edge
	 * @param toVertex       the brand new vertex introduced by the edge
	 * @param mappedVertices the graph vertex ids already part of the current pattern occurrence
	 * @return true if the edge (fromVertex -> toVertex) is a general forward leaf edge
	 */
	private boolean isGeneralForwardLeafEdge(Graph g, int fromVertex, int toVertex, Set<Integer> mappedVertices) {

		Set<Integer> visited = new HashSet<>();
		visited.add(toVertex);

		Deque<int[]> stack = new ArrayDeque<>();
		stack.push(new int[] { toVertex, fromVertex });

		// explore the region reachable from toVertex without going back through the added edge
		while (!stack.isEmpty()) {
			int[] cur = stack.pop();
			int v = cur[0];
			int parent = cur[1];

			for (Vertex nb : g.getAllNeighbors(v)) {
				int nbId = nb.getId();

				if (nbId == parent) {
					continue;
				}
				if (mappedVertices.contains(nbId)) {
					return false; // reconnects to the existing pattern
				}
				if (!visited.add(nbId)) {
					return false; // cycle among the new vertices
				}
				stack.push(new int[] { nbId, v });
			}
		}
		return true;
	}

	/**
	 * Checks whether a rightmost extension of a DFS code is an "equivalent extension" (Theorem 2
	 * of the CFGM paper): it must occur in every embedding of the current pattern, and be either
	 * a backward edge, or a forward edge whose new vertex is a general forward leaf edge in every
	 * embedding.
	 *
	 * @param nodeEmbeddings the concrete per-graph embeddings of the current pattern
	 * @param graphDB        the graph database
	 * @param ee             the candidate extension edge
	 * @return true if ee is an equivalent extension of the current pattern
	 */
	boolean isEquivalentExtension(Map<Integer, List<Map<Integer, Integer>>> nodeEmbeddings, List<Graph> graphDB,
			ExtendedEdge ee) {

		int v1 = ee.getV1();
		int v2 = ee.getV2();
		boolean isForward = v1 < v2;

		for (Entry<Integer, List<Map<Integer, Integer>>> entry : nodeEmbeddings.entrySet()) {
			Graph g = graphDB.get(entry.getKey());

			for (Map<Integer, Integer> f : entry.getValue()) {
				int mappedV1 = f.get(v1);

				boolean found = false;

				if (!isForward) {
					// backward edge: v2 is already mapped by f
					Integer mappedV2 = f.get(v2);
					if (mappedV2 != null && g.isNeighboring(mappedV1, mappedV2)
							&& g.getEdgeLabel(mappedV1, mappedV2) == ee.getEdgeLabel()) {
						found = true;
					}
				} else {
					// forward edge: needs a new vertex that is a general forward leaf edge in every realization
					Set<Integer> mappedVertices = new HashSet<>(f.values());
					for (Vertex x : g.getAllNeighbors(mappedV1)) {
						if (!mappedVertices.contains(x.getId()) && x.getLabel() == ee.getvLabel2()
								&& g.getEdgeLabel(mappedV1, x.getId()) == ee.getEdgeLabel()) {
							found = true;
							if (!isGeneralForwardLeafEdge(g, mappedV1, x.getId(), mappedVertices)) {
								return false;
							}
						}
					}
				}

				if (!found) {
					// this embedding cannot be extended by ee: not fully occurring
					return false;
				}
			}
		}

		return true;
	}
}