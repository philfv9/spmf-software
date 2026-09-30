// ===================== File: FSPMiner.java =====================
package ca.pfv.spmf.algorithms.graph_mining.fsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ca.pfv.spmf.algorithms.graph_mining.tkg.DFSCode;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Edge;
import ca.pfv.spmf.algorithms.graph_mining.tkg.ExtendedEdge;
import ca.pfv.spmf.algorithms.graph_mining.tkg.FrequentSubgraph;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Graph;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Vertex;
import ca.pfv.spmf.tools.MemoryLogger;

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
 * Implements the actual DFS-tree mining part of the FSP algorithm (see
 * {@link AlgoFSP} for the general description of FSP and its optimizations):
 * subgraph isomorphism matching, rightmost-path extension generation
 * (Generate_Children), the DFS-balance based fast pruning test, the canonical
 * form (MinCheck) test, and the recursive Build_DFS_Tree procedure
 * (Algorithms 2 and 3 of the paper), including the "Backward Graph-Index
 * Assignation" and "incremental isomorphism reuse" optimizations described in
 * {@link AlgoFSP}.
 * <p>
 * This class holds no state of its own: it reads and updates the relevant
 * fields (support/statistics counters, output list, flags, etc.) directly on
 * the {@link AlgoFSP} instance it was created with, exactly as this code used
 * to behave when it was part of {@code AlgoFSP} itself.
 * </p>
 *
 * @author Chen Hui (based on the SPMF gSpan implementation by Chao Cheng)
 */
class FSPMiner {

	/** the algorithm instance this miner operates on */
	private final AlgoFSP algo;

	/**
	 * Create a new miner bound to a given algorithm instance.
	 *
	 * @param algo the algorithm instance
	 */
	FSPMiner(AlgoFSP algo) {
		this.algo = algo;
	}

	/**
	 * Find all isomorphisms between the graph described by a DFS code and a given
	 * graph. This is only used (a) as a fallback when incremental embedding reuse
	 * is disabled/unavailable, and (b) by
	 * {@link #rightMostPathExtensionsFromSingle(DFSCode, Graph)} for the
	 * canonical-form check (which operates on a small synthetic graph, not the
	 * database, so it does not need incremental reuse).
	 *
	 * @param c a dfs code representing a subgraph
	 * @param g a graph
	 * @return the list of all isomorphisms, each represented by a map from DFS
	 *         vertex id to graph vertex id
	 */
	private List<Map<Integer, Integer>> subgraphIsomorphisms(DFSCode c, Graph g) {

		List<Map<Integer, Integer>> isoms = new ArrayList<>();

		// seed the isomorphisms with all vertices matching the start label
		int startLabel = c.getEeL().get(0).getvLabel1();
		for (int vID : g.findAllWithLabel(startLabel)) {
			Map<Integer, Integer> map = new HashMap<>();
			map.put(0, vID);
			isoms.add(map);
		}

		// extend the partial isomorphisms edge by edge
		for (ExtendedEdge ee : c.getEeL()) {
			int v1 = ee.getV1();
			int v2 = ee.getV2();
			int v2Label = ee.getvLabel2();
			int eLabel = ee.getEdgeLabel();

			List<Map<Integer, Integer>> updateIsoms = new ArrayList<>();
			for (Map<Integer, Integer> iso : isoms) {

				int mappedV1 = iso.get(v1);

				if (v1 < v2) {
					// forward edge: try to map the new vertex v2 to an unused neighbor
					Set<Integer> mappedVertices = new HashSet<>(iso.values());

					for (Vertex mappedV2 : g.getAllNeighbors(mappedV1)) {

						if (v2Label == mappedV2.getLabel() && (!mappedVertices.contains(mappedV2.getId()))
								&& eLabel == g.getEdgeLabel(mappedV1, mappedV2.getId())) {

							HashMap<Integer, Integer> tempM = new HashMap<>(iso.size() + 1);
							tempM.putAll(iso);
							tempM.put(v2, mappedV2.getId());

							updateIsoms.add(tempM);
						}
					}
				} else {
					// backward edge: both endpoints already mapped, just verify the edge exists
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
	 * Get the graph-id set associated with an extended edge, creating and
	 * registering a new empty one if none exists yet.
	 *
	 * @param extensions the extensions map
	 * @param ee         the extended edge key
	 * @return the (possibly newly created) set of graph ids for ee
	 */
	private Set<Integer> getOrCreateSet(Map<ExtendedEdge, Set<Integer>> extensions, ExtendedEdge ee) {
		Set<Integer> set = extensions.get(ee);
		if (set == null) {
			set = new HashSet<>();
			extensions.put(ee, set);
		}
		return set;
	}

	/**
	 * Same purpose as {@link #rightMostPathExtensions}, but operating on a single,
	 * small, synthetic graph representing a DFS code's own structure. Used only by
	 * {@link #isCanonical(DFSCode)}. Since this operates on a tiny self-contained
	 * graph (not the database), incremental embedding reuse is not needed here.
	 *
	 * @param c the current (partial) canonical DFS code
	 * @param g the synthetic graph representing the code being checked
	 * @return a map of extended edges to the set of graph ids in which they occur
	 *         (always a single id here)
	 */
	private Map<ExtendedEdge, Set<Integer>> rightMostPathExtensionsFromSingle(DFSCode c, Graph g) {
		int gid = g.getId();

		Map<ExtendedEdge, Set<Integer>> extensions = new HashMap<>();

		if (c.isEmpty()) {

			// initial case: every edge of the graph is a candidate 1-edge extension
			for (Vertex vertex : g.vertices) {
				for (Edge e : vertex.getEdgeList()) {
					int v1L = g.getVLabel(e.v1);
					int v2L = g.getVLabel(e.v2);
					ExtendedEdge ee1;
					if (v1L < v2L) {
						ee1 = new ExtendedEdge(0, 1, v1L, v2L, e.getEdgeLabel());
					} else {
						ee1 = new ExtendedEdge(0, 1, v2L, v1L, e.getEdgeLabel());
					}
					getOrCreateSet(extensions, ee1).add(gid);
				}
			}
		} else {
			int rightMost = c.getRightMost();

			List<Map<Integer, Integer>> isoms = subgraphIsomorphisms(c, g);

			for (Map<Integer, Integer> isom : isoms) {

				Map<Integer, Integer> invertedISOM = new HashMap<>();
				for (Entry<Integer, Integer> entry : isom.entrySet()) {
					invertedISOM.put(entry.getValue(), entry.getKey());
				}
				// try backward extensions from the rightmost vertex
				int mappedRM = isom.get(rightMost);
				int mappedRMlabel = g.getVLabel(mappedRM);
				for (Vertex x : g.getAllNeighbors(mappedRM)) {
					Integer invertedX = invertedISOM.get(x.getId());
					if (invertedX != null && c.onRightMostPath(invertedX) && c.notPreOfRM(invertedX)
							&& !c.containEdge(rightMost, invertedX)) {
						ExtendedEdge ee = new ExtendedEdge(rightMost, invertedX, mappedRMlabel, x.getLabel(),
								g.getEdgeLabel(mappedRM, x.getId()));
						getOrCreateSet(extensions, ee).add(g.getId());
					}
				}
				// try forward extensions from every vertex on the rightmost path
				Set<Integer> mappedVertices = new HashSet<>(isom.values());
				for (int v : c.getRightMostPath()) {
					int mappedV = isom.get(v);
					int mappedVlabel = g.getVLabel(mappedV);
					for (Vertex x : g.getAllNeighbors(mappedV)) {
						if (!mappedVertices.contains(x.getId())) {
							ExtendedEdge ee = new ExtendedEdge(v, rightMost + 1, mappedVlabel, x.getLabel(),
									g.getEdgeLabel(mappedV, x.getId()));
							getOrCreateSet(extensions, ee).add(g.getId());
						}
					}
				}
			}
		}
		return extensions;
	}

	/**
	 * Holds, for one candidate rightmost-path extension edge of a DFS code, both
	 * its support (the set of graph indices in which it was found) and, for each
	 * supporting graph, the list of resulting embeddings (isomorphism mappings of
	 * the EXTENDED code, i.e. of {@code code + this edge}). Retaining the
	 * embeddings (instead of only the graph id support) lets the next recursion
	 * level reuse them directly, instead of recomputing
	 * {@link FSPMiner#subgraphIsomorphisms(DFSCode, Graph)} from scratch, which is
	 * the mechanism used here to reduce the number of subgraph isomorphism tests
	 * performed (the central goal stated in the FSP paper's abstract).
	 */
	private static final class ExtensionInfo {
		/** graph indices (into the graph database) supporting this extension */
		final Set<Integer> graphIds = new HashSet<>();
		/**
		 * per supporting graph, the list of resulting embeddings of the extended code
		 */
		final Map<Integer, List<Map<Integer, Integer>>> embeddings = new HashMap<>();

		/**
		 * Record a new embedding found for this extension in a given graph.
		 *
		 * @param graphId   the graph index
		 * @param embedding the resulting isomorphism mapping
		 */
		void addEmbedding(int graphId, Map<Integer, Integer> embedding) {
			graphIds.add(graphId);
			List<Map<Integer, Integer>> list = embeddings.get(graphId);
			if (list == null) {
				list = new ArrayList<>();
				embeddings.put(graphId, list);
			}
			list.add(embedding);
		}

		/**
		 * Get the support of this extension.
		 *
		 * @return the number of distinct supporting graphs
		 */
		int support() {
			return graphIds.size();
		}
	}

	/**
	 * Get the {@link ExtensionInfo} associated with an extended edge, creating and
	 * registering a new empty one if none exists yet.
	 *
	 * @param extensions the extensions map
	 * @param ee         the extended edge key
	 * @return the (possibly newly created) extension info for ee
	 */
	private ExtensionInfo getOrCreateInfo(Map<ExtendedEdge, ExtensionInfo> extensions, ExtendedEdge ee) {
		ExtensionInfo info = extensions.get(ee);
		if (info == null) {
			info = new ExtensionInfo();
			extensions.put(ee, info);
		}
		return info;
	}

	/**
	 * Record a new 2-vertex embedding (mapping DFS vertex 0 and 1) for a candidate
	 * 1-edge extension found in the initial (empty code) case.
	 *
	 * @param extensions the extensions map
	 * @param ee         the extended edge key
	 * @param graphId    the supporting graph index
	 * @param vFor0      the graph vertex mapped to DFS vertex 0
	 * @param vFor1      the graph vertex mapped to DFS vertex 1
	 */
	private void addInitialEmbedding(Map<ExtendedEdge, ExtensionInfo> extensions, ExtendedEdge ee, int graphId,
			int vFor0, int vFor1) {
		Map<Integer, Integer> embedding = new HashMap<>(2);
		embedding.put(0, vFor0);
		embedding.put(1, vFor1);
		getOrCreateInfo(extensions, ee).addEmbedding(graphId, embedding);
	}

	/**
	 * Compute all rightmost path extensions of a DFS code together with their
	 * support AND, for each extension, the resulting embeddings per supporting
	 * graph (see {@link ExtensionInfo}).
	 *
	 * <p>
	 * <b>Incremental reuse:</b> if {@code currentEmbeddings} is non-null (and
	 * {@link AlgoFSP#useIncrementalIsomorphism} is enabled), the already-known
	 * embeddings of {@code c} (computed as a byproduct of finding {@code c} itself
	 * as an extension of its own parent) are reused directly instead of calling
	 * {@link #subgraphIsomorphisms(DFSCode, Graph)} again. This is always
	 * semantically equivalent to recomputing from scratch: the embeddings passed in
	 * are, by construction/invariant (see {@link #fspDFS}), exactly the full and
	 * correct set of isomorphisms of {@code c} in that graph, so using them changes
	 * nothing about the result, only the amount of work performed.
	 * </p>
	 *
	 * <p>
	 * <b>Note on graph identifiers:</b> all graph id sets/keys here are indices
	 * into {@code graphDatabase} (the same convention used everywhere in this
	 * class), not the graph's own declared id from the input file.
	 * </p>
	 *
	 * @param c                 a DFS code
	 * @param graphDatabase     the graph database
	 * @param graphIds          the ids (indices into graphDatabase) of the graphs
	 *                          in which "c" occurs
	 * @param currentEmbeddings the already-known embeddings of "c" per graph id, or
	 *                          {@code null} if "c" is the empty code (initial call)
	 *                          or embeddings are otherwise unavailable
	 * @return a map of extended edges to an {@link ExtensionInfo}
	 */
	private Map<ExtendedEdge, ExtensionInfo> rightMostPathExtensions(DFSCode c, List<Graph> graphDatabase,
			Set<Integer> graphIds, Map<Integer, List<Map<Integer, Integer>>> currentEmbeddings) {

		Map<ExtendedEdge, ExtensionInfo> extensions = new HashMap<>();

		if (c.isEmpty()) {

			// initial case: consider every edge of every supporting graph as a candidate
			for (Integer graphId : graphIds) {
				Graph g = graphDatabase.get(graphId);

				if (AlgoFSP.EDGE_COUNT_PRUNING && c.size() >= g.getEdgeCount()) {
					algo.pruneByEdgeCountCount++;
					continue;
				}

				for (Vertex vertex : g.vertices) {
					for (Edge e : vertex.getEdgeList()) {

						// each undirected edge is referenced from both of its endpoints' edge
						// lists; only process it once (from its lower-id endpoint)
						if (vertex.getId() != Math.min(e.v1, e.v2)) {
							continue;
						}

						int v1L = g.getVLabel(e.v1);
						int v2L = g.getVLabel(e.v2);

						if (v1L < v2L) {
							ExtendedEdge ee1 = new ExtendedEdge(0, 1, v1L, v2L, e.getEdgeLabel());
							addInitialEmbedding(extensions, ee1, graphId, e.v1, e.v2);
						} else if (v1L > v2L) {
							ExtendedEdge ee1 = new ExtendedEdge(0, 1, v2L, v1L, e.getEdgeLabel());
							addInitialEmbedding(extensions, ee1, graphId, e.v2, e.v1);
						} else {
							// when both vertex labels are equal, the DFS-vertex assignment (0 or 1)
							// is ambiguous but embeddings are orientation-sensitive, so both
							// orientations must be kept as separate embeddings
							ExtendedEdge ee1 = new ExtendedEdge(0, 1, v1L, v2L, e.getEdgeLabel());
							addInitialEmbedding(extensions, ee1, graphId, e.v1, e.v2);
							addInitialEmbedding(extensions, ee1, graphId, e.v2, e.v1);
						}
					}
				}
			}
		} else {
			int rightMost = c.getRightMost();
			for (Integer graphId : graphIds) {
				Graph g = graphDatabase.get(graphId);

				if (AlgoFSP.EDGE_COUNT_PRUNING && c.size() >= g.getEdgeCount()) {
					algo.pruneByEdgeCountCount++;
					continue;
				}

				// try to reuse embeddings carried down from the previous recursion level;
				// fall back to a full recomputation if unavailable
				List<Map<Integer, Integer>> isoms = null;
				if (algo.useIncrementalIsomorphism && currentEmbeddings != null) {
					isoms = currentEmbeddings.get(graphId);
					if (isoms != null) {
						algo.embeddingReuseCount++;
					}
				}
				if (isoms == null) {
					isoms = subgraphIsomorphisms(c, g);
				}

				for (Map<Integer, Integer> isom : isoms) {

					Map<Integer, Integer> invertedISOM = new HashMap<>();
					for (Entry<Integer, Integer> entry : isom.entrySet()) {
						invertedISOM.put(entry.getValue(), entry.getKey());
					}
					// try backward extensions from the rightmost vertex
					int mappedRM = isom.get(rightMost);
					int mappedRMlabel = g.getVLabel(mappedRM);
					for (Vertex x : g.getAllNeighbors(mappedRM)) {
						Integer invertedX = invertedISOM.get(x.getId());
						if (invertedX != null && c.onRightMostPath(invertedX) && c.notPreOfRM(invertedX)
								&& !c.containEdge(rightMost, invertedX)) {
							ExtendedEdge ee = new ExtendedEdge(rightMost, invertedX, mappedRMlabel, x.getLabel(),
									g.getEdgeLabel(mappedRM, x.getId()));
							// backward edge: no new vertex is added, so the child's embedding is
							// identical to the current one and the map can safely be shared
							getOrCreateInfo(extensions, ee).addEmbedding(graphId, isom);
						}
					}
					// try forward extensions from every vertex on the rightmost path
					Set<Integer> mappedVertices = new HashSet<>(isom.values());
					for (int v : c.getRightMostPath()) {
						int mappedV = isom.get(v);
						int mappedVlabel = g.getVLabel(mappedV);
						for (Vertex x : g.getAllNeighbors(mappedV)) {
							if (!mappedVertices.contains(x.getId())) {
								ExtendedEdge ee = new ExtendedEdge(v, rightMost + 1, mappedVlabel, x.getLabel(),
										g.getEdgeLabel(mappedV, x.getId()));
								// forward edge: a new vertex is added, so a new map must be created
								// rather than mutating the shared "isom" object
								Map<Integer, Integer> extended = new HashMap<>(isom.size() + 1);
								extended.putAll(isom);
								extended.put(rightMost + 1, x.getId());
								getOrCreateInfo(extensions, ee).addEmbedding(graphId, extended);
							}
						}
					}
				}
			} // end for graph id
		}
		return extensions;
	}

	/**
	 * A (target vertex, edge label) pair identifying a candidate backward edge from
	 * the current rightmost vertex. Used by the "Backward Graph-Index Assignation"
	 * optimization to detect matching sibling backward extensions.
	 */
	private static final class BackwardTarget {
		/** the target vertex id of the backward edge */
		final int target;
		/** the label of the backward edge */
		final int edgeLabel;

		/**
		 * Create a new backward target descriptor.
		 *
		 * @param target    the target vertex id
		 * @param edgeLabel the edge label
		 */
		BackwardTarget(int target, int edgeLabel) {
			this.target = target;
			this.edgeLabel = edgeLabel;
		}
	}

	/**
	 * A key identifying a specific unordered pair of backward edges (each described
	 * by its target vertex and edge label) added to a common parent DFS code. Two
	 * backward extensions {@code backward(t1,l1)} and {@code backward(t2,l2)} of
	 * the same parent code, when combined, always produce the exact same compound
	 * subgraph regardless of the order in which they were added; this key is
	 * therefore built in an order-independent way so that it can be used to detect
	 * this situation.
	 */
	private static final class BackwardPairKey {
		/** target vertex of the first backward edge (order-independent) */
		final int t1;
		/** edge label of the first backward edge (order-independent) */
		final int l1;
		/** target vertex of the second backward edge (order-independent) */
		final int t2;
		/** edge label of the second backward edge (order-independent) */
		final int l2;

		/**
		 * Create a key for an unordered pair of backward edges.
		 *
		 * @param targetA target vertex of the first edge
		 * @param labelA  edge label of the first edge
		 * @param targetB target vertex of the second edge
		 * @param labelB  edge label of the second edge
		 */
		BackwardPairKey(int targetA, int labelA, int targetB, int labelB) {
			if (targetA <= targetB) {
				t1 = targetA;
				l1 = labelA;
				t2 = targetB;
				l2 = labelB;
			} else {
				t1 = targetB;
				l1 = labelB;
				t2 = targetA;
				l2 = labelA;
			}
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof BackwardPairKey)) {
				return false;
			}
			BackwardPairKey k = (BackwardPairKey) obj;
			return t1 == k.t1 && l1 == k.l1 && t2 == k.t2 && l2 == k.l2;
		}

		@Override
		public int hashCode() {
			return ((t1 * 31 + l1) * 31 + t2) * 31 + l2;
		}
	}

	/**
	 * Build the list of (target, edgeLabel) pairs describing the "sibling"
	 * backward-edge children of a common parent, excluding {@code exclude} itself.
	 *
	 * @param backwardChildEdges all backward-edge extensions of the common parent
	 * @param exclude            the extension to exclude (the one being processed)
	 * @return the list of sibling backward targets
	 */
	private List<BackwardTarget> buildSiblingTargets(List<ExtendedEdge> backwardChildEdges, ExtendedEdge exclude) {
		if (backwardChildEdges.size() < 2) {
			return Collections.emptyList();
		}
		List<BackwardTarget> list = new ArrayList<>(backwardChildEdges.size() - 1);
		for (ExtendedEdge ee : backwardChildEdges) {
			if (ee == exclude) {
				continue;
			}
			list.add(new BackwardTarget(ee.getV2(), ee.getEdgeLabel()));
		}
		return list;
	}

	/**
	 * Initial (top-level) call: the empty code has no parent, no sibling
	 * information, and no pre-computed embeddings (the very first level of
	 * matching, based on vertex/edge labels, has to be done from scratch exactly
	 * once here).
	 *
	 * @param c        the empty DFS code
	 * @param graphDB  the graph database
	 * @param graphIds the ids (indices) of the non-empty graphs
	 * @throws IOException            exception if error writing/reading to file
	 * @throws ClassNotFoundException if error casting a class
	 */
	void fspDFS(DFSCode c, List<Graph> graphDB, Set<Integer> graphIds)
			throws java.io.IOException, ClassNotFoundException {
		fspDFS(c, graphDB, graphIds, null, Collections.<BackwardTarget>emptyList(), null);
	}

	/**
	 * Recursive method implementing FSP's Build_DFS_Tree / Generate_Children
	 * (Algorithms 2 and 3 of the paper), combined with the incremental embedding
	 * reuse mechanism (see class-level documentation, optimization 4) and the
	 * Backward Graph-Index Assignation optimization (optimization 3).
	 *
	 * @param c                      the current DFS code
	 * @param graphDB                the graph database
	 * @param graphIds               the ids (indices) of graphs where "c" appears
	 * @param embeddings             the already-known embeddings (isomorphism
	 *                               mappings) of "c" per graph id, or {@code null}
	 *                               if "c" is the empty code
	 * @param siblingBackwardTargets the (target, edgeLabel) pairs of "c"'s own
	 *                               siblings (other backward-edge children of c's
	 *                               parent), empty if not applicable
	 * @param incomingCompoundCache  the cache shared between all backward-edge
	 *                               children of c's parent, used to detect
	 *                               duplicate compound subgraphs; may be
	 *                               {@code null} if not applicable
	 * @throws IOException            exception if error writing/reading to file
	 * @throws ClassNotFoundException if error casting a class
	 */
	private void fspDFS(DFSCode c, List<Graph> graphDB, Set<Integer> graphIds,
			Map<Integer, List<Map<Integer, Integer>>> embeddings, List<BackwardTarget> siblingBackwardTargets,
			Map<BackwardPairKey, FrequentSubgraph> incomingCompoundCache) throws java.io.IOException, ClassNotFoundException {

		if (c.size() >= algo.maxNumberOfEdges) {
			return;
		}

		// Generate_Children: find all rightmost path extensions (with their support
		// and embeddings), reusing embeddings from the previous recursion level
		Map<ExtendedEdge, ExtensionInfo> extensions = rightMostPathExtensions(c, graphDB, graphIds, embeddings);

		int rightMostOfC = c.isEmpty() ? -1 : c.getRightMost();
		ExtendedEdge lastEdgeOfC = c.isEmpty() ? null : c.getAt(c.size() - 1);
		boolean cIsBackwardChild = algo.useBackwardAssignation && lastEdgeOfC != null
				&& lastEdgeOfC.getV2() < lastEdgeOfC.getV1() && !siblingBackwardTargets.isEmpty();

		// collect c's own direct backward-edge extensions: these form the sibling
		// group that will be handed down to each of c's backward children
		List<ExtendedEdge> backwardChildEdges = new ArrayList<>();
		if (algo.useBackwardAssignation && !c.isEmpty()) {
			for (ExtendedEdge ee : extensions.keySet()) {
				if (ee.getV1() == rightMostOfC && ee.getV2() < ee.getV1()) {
					backwardChildEdges.add(ee);
				}
			}
		}
		Map<BackwardPairKey, FrequentSubgraph> childCompoundCache = (algo.useBackwardAssignation
				&& backwardChildEdges.size() > 1) ? new HashMap<BackwardPairKey, FrequentSubgraph>() : null;

		for (Map.Entry<ExtendedEdge, ExtensionInfo> entry : extensions.entrySet()) {

			ExtendedEdge extension = entry.getKey();
			ExtensionInfo info = entry.getValue();
			int sup = info.support();

			if (sup < algo.minSup) {
				continue;
			}

			boolean isBackwardEdgeOfC = extension.getV1() == rightMostOfC && extension.getV2() < extension.getV1();

			// try the "Backward Graph-Index Assignation" shortcut
			if (cIsBackwardChild && isBackwardEdgeOfC && incomingCompoundCache != null) {
				int tOwn = lastEdgeOfC.getV2();
				int lOwn = lastEdgeOfC.getEdgeLabel();
				int tCand = extension.getV2();
				int lCand = extension.getEdgeLabel();

				boolean isSiblingTarget = false;
				for (BackwardTarget bt : siblingBackwardTargets) {
					if (bt.target == tCand && bt.edgeLabel == lCand) {
						isSiblingTarget = true;
						break;
					}
				}

				if (isSiblingTarget) {
					BackwardPairKey key = new BackwardPairKey(tOwn, lOwn, tCand, lCand);

					if (incomingCompoundCache.containsKey(key)) {
						// already discovered and fully mined through a sibling branch: skip
						// the redundant fast-pruning/MinCheck test and recursive exploration
						algo.backwardAssignationCount++;
						continue;
					}

					DFSCode newC = c.copy();
					newC.add(extension);

					if (algo.useFastPruning && !isDFSBalanced(newC)) {
						algo.fastPruningCount++;
						continue;
					}

					if (isCanonical(newC)) {
						FrequentSubgraph subgraph = new FrequentSubgraph(newC, info.graphIds, sup);
						algo.frequentSubgraphs.add(subgraph);
						incomingCompoundCache.put(key, subgraph);

						List<BackwardTarget> nextSiblingTargets = buildSiblingTargets(backwardChildEdges, extension);
						fspDFS(newC, graphDB, info.graphIds, info.embeddings, nextSiblingTargets, childCompoundCache);
					}
					continue;
				}
			}

			// normal path: no assignation shortcut applicable
			DFSCode newC = c.copy();
			newC.add(extension);

			if (algo.useFastPruning && !isDFSBalanced(newC)) {
				algo.fastPruningCount++;
				continue;
			}

			if (isCanonical(newC)) {

				FrequentSubgraph subgraph = new FrequentSubgraph(newC, info.graphIds, sup);
				algo.frequentSubgraphs.add(subgraph);

				List<BackwardTarget> nextSiblingTargets = isBackwardEdgeOfC
						? buildSiblingTargets(backwardChildEdges, extension)
						: Collections.<BackwardTarget>emptyList();

				// recursive call: Build_DFS_Tree(child), reusing embeddings
				fspDFS(newC, graphDB, info.graphIds, info.embeddings, nextSiblingTargets, childCompoundCache);
			}
		}
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * A small container class used to hold the (local, renumbered) edge sequence of
	 * a sub-DFS-tree, together with a flag telling whether the extraction was
	 * "complete" (i.e., no edge of the subtree escapes to a vertex outside of the
	 * subtree's vertex range).
	 */
	private static class SubtreeCode {
		/** the local (renumbered from 0) edge sequence of the subtree */
		List<ExtendedEdge> code;
		/** true if no edge of the subtree escapes its own vertex range */
		boolean complete;

		/**
		 * Create a subtree code descriptor.
		 *
		 * @param code     the local edge sequence
		 * @param complete whether the extraction was self-contained
		 */
		SubtreeCode(List<ExtendedEdge> code, boolean complete) {
			this.code = code;
			this.complete = complete;
		}
	}

	/**
	 * Check whether a DFS code is "DFS balanced", i.e., for every vertex in its DFS
	 * tree, the sub-DFS-trees rooted at its children are arranged in descending
	 * order under the DFS lexicographic order. If a code is not DFS balanced then,
	 * by the Fast Pruning Rule of the FSP paper, it cannot be a minimum DFS code.
	 * <p>
	 * For safety (to guarantee that we never discard a code that could actually be
	 * minimum), the comparison between two sibling sub-DFS-trees is only performed
	 * when it can be done in a fully self-contained way (i.e. none of their edges
	 * connects to a vertex outside of their own vertex range). When this is not the
	 * case, the comparison is skipped (i.e., considered balanced), which only
	 * forfeits a pruning opportunity but never causes an incorrect pruning.
	 *
	 * @param c a (non-empty) DFS code
	 * @return true if c is (conservatively) considered DFS balanced
	 */
	private boolean isDFSBalanced(DFSCode c) {

		int n = c.getRightMost() + 1; // number of vertices in the DFS code
		if (n <= 2) {
			return true; // trivial case: cannot have two siblings yet
		}

		// build the children lists (forward edges only), in DFS-code order
		Map<Integer, List<Integer>> childrenMap = new HashMap<>();
		for (ExtendedEdge ee : c.getEeL()) {
			if (ee.getV1() < ee.getV2()) {
				List<Integer> kids = childrenMap.get(ee.getV1());
				if (kids == null) {
					kids = new ArrayList<>();
					childrenMap.put(ee.getV1(), kids);
				}
				kids.add(ee.getV2());
			}
		}

		if (childrenMap.isEmpty()) {
			return true;
		}

		// compute the size of the subtree rooted at each vertex (bottom-up)
		int[] size = new int[n];
		for (int v = n - 1; v >= 0; v--) {
			int s = 1;
			List<Integer> kids = childrenMap.get(v);
			if (kids != null) {
				for (int k : kids) {
					s += size[k];
				}
			}
			size[v] = s;
		}

		// for every vertex having 2 or more children, check that consecutive
		// children's sub-DFS-trees appear in descending DFS lexicographic order
		for (Entry<Integer, List<Integer>> entry : childrenMap.entrySet()) {
			List<Integer> kids = entry.getValue();
			if (kids.size() < 2) {
				continue;
			}
			for (int i = 0; i < kids.size() - 1; i++) {
				int c1 = kids.get(i);
				int c2 = kids.get(i + 1);

				SubtreeCode code1 = extractLocalSubtreeCode(c, c1, size[c1]);
				SubtreeCode code2 = extractLocalSubtreeCode(c, c2, size[c2]);

				if (!code1.complete || !code2.complete) {
					// cannot safely compare -> skip (conservative, no pruning)
					continue;
				}

				if (compareCode(code1.code, code2.code) < 0) {
					// the later child's sub-DFS-tree is strictly LARGER than the earlier
					// child's one -> violates the descending order -> DFS unbalanced
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Extract the (local, renumbered starting from 0) edge sequence of the
	 * sub-DFS-tree rooted at vertex "root" (whose subtree occupies the contiguous
	 * vertex-id range [root, root+size-1] in the DFS code).
	 *
	 * @param c    the DFS code
	 * @param root the root vertex of the subtree
	 * @param size the number of vertices in the subtree
	 * @return the local subtree code, flagged as complete only if self-contained
	 */
	private SubtreeCode extractLocalSubtreeCode(DFSCode c, int root, int size) {
		List<ExtendedEdge> local = new ArrayList<>();
		int start = root;
		int end = root + size - 1;
		boolean complete = true;

		for (ExtendedEdge ee : c.getEeL()) {
			int v1 = ee.getV1();
			int v2 = ee.getV2();
			boolean v1In = v1 >= start && v1 <= end;
			boolean v2In = v2 >= start && v2 <= end;

			if (v1In && v2In) {
				local.add(
						new ExtendedEdge(v1 - start, v2 - start, ee.getvLabel1(), ee.getvLabel2(), ee.getEdgeLabel()));
			} else if (v1In || v2In) {
				// an edge escapes the vertex range of this subtree: we cannot safely
				// build a fully self-contained comparison
				complete = false;
			}
		}
		return new SubtreeCode(local, complete);
	}

	/**
	 * Compare two (local) edge sequences using the same pairwise ordering as used
	 * for minimum DFS code checking.
	 *
	 * @param a the first edge sequence
	 * @param b the second edge sequence
	 * @return -1 if a &lt; b, 0 if equal, 1 if a &gt; b
	 */
	private int compareCode(List<ExtendedEdge> a, List<ExtendedEdge> b) {
		int len = Math.min(a.size(), b.size());
		for (int i = 0; i < len; i++) {
			ExtendedEdge ea = a.get(i);
			ExtendedEdge eb = b.get(i);
			if (ea.equals(eb)) {
				continue;
			}
			if (ea.smallerThan(eb)) {
				return -1;
			} else {
				return 1;
			}
		}
		if (a.size() < b.size()) {
			return -1;
		}
		if (a.size() > b.size()) {
			return 1;
		}
		return 0;
	}

	/**
	 * Check if a DFS code is canonical (i.e. is the minimum DFS code of the
	 * subgraph it represents).
	 *
	 * @param c a DFS code
	 * @return true if it is canonical, and otherwise, false.
	 */
	private boolean isCanonical(DFSCode c) {
		DFSCode canC = new DFSCode();
		for (int i = 0; i < c.size(); i++) {
			// find the smallest possible extension at this position
			Map<ExtendedEdge, Set<Integer>> extensions = rightMostPathExtensionsFromSingle(canC, new Graph(c));
			ExtendedEdge minEE = null;
			for (ExtendedEdge ee : extensions.keySet()) {
				if (ee.smallerThan(minEE))
					minEE = ee;
			}

			if (minEE.smallerThan(c.getAt(i)))
				return false;
			canC.add(minEE);
		}
		return true;
	}
}