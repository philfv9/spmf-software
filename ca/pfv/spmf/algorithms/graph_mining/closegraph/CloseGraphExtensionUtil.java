package ca.pfv.spmf.algorithms.graph_mining.closegraph;

import java.io.IOException;
import java.util.ArrayList;
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

/**
 * Helper class used by {@link AlgoCloseGraph}, gathering the gSpan-style
 * routines: subgraph isomorphism enumeration, the DFS-code minimality
 * (canonical form) test, and one-edge extension enumeration (both the
 * right-most-path extensions used to drive the search, and the full set of
 * one-edge extensions used by the exact closedness test).
 *
 * Extracted out of AlgoCloseGraph simply to keep the code split into smaller,
 * easier-to-navigate files; the logic itself is unchanged.
 */
class CloseGraphExtensionUtil {

	/** apply the edge-count pruning strategy (speed only) */
	private static final boolean EDGE_COUNT_PRUNING = true;

	private CloseGraphExtensionUtil() {
		// utility class, not meant to be instantiated
	}

	// =====================================================================
	// Subgraph isomorphism / canonical test (identical to gSpan)
	// =====================================================================

	private static List<Map<Integer, Integer>> subgraphIsomorphisms(DFSCode c, Graph g) {
		List<Map<Integer, Integer>> isoms = new ArrayList<>();

		int startLabel = c.getEeL().get(0).getvLabel1();
		for (int vID : g.findAllWithLabel(startLabel)) {
			Map<Integer, Integer> map = new HashMap<>();
			map.put(0, vID);
			isoms.add(map);
		}

		for (ExtendedEdge ee : c.getEeL()) {
			int v1 = ee.getV1();
			int v2 = ee.getV2();
			int v2Label = ee.getvLabel2();
			int eLabel = ee.getEdgeLabel();

			List<Map<Integer, Integer>> updateIsoms = new ArrayList<>();

			// bottleneck fix: instead of allocating a brand-new HashSet copy of
			// iso.values() for EVERY candidate isomorphism (which can number in the
			// thousands/millions across a large graph database), allocate a single
			// reusable buffer for this edge and just clear()+refill it for each
			// "iso" processed below. This is safe because the buffer's contents are
			// only read within the same loop iteration and never escape it.
			Set<Integer> mappedVerticesBuffer = (v1 < v2) ? new HashSet<Integer>() : null;

			for (Map<Integer, Integer> iso : isoms) {
				int mappedV1 = iso.get(v1);
				if (v1 < v2) {
					mappedVerticesBuffer.clear();
					mappedVerticesBuffer.addAll(iso.values());
					for (Vertex mappedV2 : g.getAllNeighbors(mappedV1)) {
						if (v2Label == mappedV2.getLabel() && (!mappedVerticesBuffer.contains(mappedV2.getId()))
								&& eLabel == g.getEdgeLabel(mappedV1, mappedV2.getId())) {
							// bottleneck fix: the HashMap copy constructor sizes the new
							// map correctly in one pass, avoiding the extra internal
							// resize that "new HashMap<>(iso.size() + 1)" + putAll could trigger
							Map<Integer, Integer> tempM = new HashMap<>(iso);
							tempM.put(v2, mappedV2.getId());
							updateIsoms.add(tempM);
						}
					}
				} else {
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

	private static Map<ExtendedEdge, Set<Integer>> rightMostPathExtensionsFromSingle(DFSCode c, Graph g) {
		int gid = g.getId();
		Map<ExtendedEdge, Set<Integer>> extensions = new HashMap<>();

		if (c.isEmpty()) {
			for (Vertex vertex : g.vertices) {
				for (Edge e : vertex.getEdgeList()) {
					int v1L = g.getVLabel(e.v1);
					int v2L = g.getVLabel(e.v2);
					ExtendedEdge ee1 = (v1L < v2L) ? new ExtendedEdge(0, 1, v1L, v2L, e.getEdgeLabel())
							: new ExtendedEdge(0, 1, v2L, v1L, e.getEdgeLabel());
					addTo(extensions, ee1, gid);
				}
			}
		} else {
			int rightMost = c.getRightMost();

			// bottleneck fix: hoist the two per-isomorphism scratch containers out of
			// the loop below and reuse (clear + refill) them for each isomorphism
			// instead of allocating brand-new HashMap/HashSet instances every time.
			// Neither container escapes past a single loop iteration.
			Map<Integer, Integer> invertedISOM = new HashMap<>();
			Set<Integer> mappedVertices = new HashSet<>();

			for (Map<Integer, Integer> isom : subgraphIsomorphisms(c, g)) {
				invertedISOM.clear();
				for (Entry<Integer, Integer> entry : isom.entrySet()) {
					invertedISOM.put(entry.getValue(), entry.getKey());
				}
				int mappedRM = isom.get(rightMost);
				int mappedRMlabel = g.getVLabel(mappedRM);
				for (Vertex x : g.getAllNeighbors(mappedRM)) {
					Integer invertedX = invertedISOM.get(x.getId());
					if (invertedX != null && c.onRightMostPath(invertedX) && c.notPreOfRM(invertedX)
							&& !c.containEdge(rightMost, invertedX)) {
						addTo(extensions, new ExtendedEdge(rightMost, invertedX, mappedRMlabel, x.getLabel(),
								g.getEdgeLabel(mappedRM, x.getId())), gid);
					}
				}
				mappedVertices.clear();
				mappedVertices.addAll(isom.values());
				for (int v : c.getRightMostPath()) {
					int mappedV = isom.get(v);
					int mappedVlabel = g.getVLabel(mappedV);
					for (Vertex x : g.getAllNeighbors(mappedV)) {
						if (!mappedVertices.contains(x.getId())) {
							addTo(extensions, new ExtendedEdge(v, rightMost + 1, mappedVlabel, x.getLabel(),
									g.getEdgeLabel(mappedV, x.getId())), gid);
						}
					}
				}
			}
		}
		return extensions;
	}

	static boolean isCanonical(DFSCode c) throws IOException, ClassNotFoundException {
		DFSCode canC = new DFSCode();
		// bottleneck fix: new Graph(c) depends only on c, which never changes
		// across this loop (only canC grows). The original code rebuilt this
		// identical graph on every one of the c.size() iterations; building it
		// once here is semantically identical but avoids the redundant work.
		Graph g = new Graph(c);
		for (int i = 0; i < c.size(); i++) {
			Map<ExtendedEdge, Set<Integer>> extensions = rightMostPathExtensionsFromSingle(canC, g);
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

	// =====================================================================
	// Extension enumeration
	// =====================================================================

	/**
	 * A generic one-edge extension of the current pattern, used ONLY for the
	 * closedness test. Two forms exist: - forward : (v1 = existing pattern vertex,
	 * v2 = -1, newVLabel, eLabel) => a brand new vertex is attached to v1 -
	 * backward : (v1 &lt; v2 both existing pattern vertices, newVLabel = -1,
	 * eLabel) => a new edge closes a cycle between two existing vertices
	 *
	 * Each distinct key corresponds to exactly one distinct one-edge supergraph.
	 */
	static final class OneEdgeExtension {
		private final int v1;
		private final int v2;
		private final int newVLabel;
		private final int eLabel;
		private final int hash;

		OneEdgeExtension(int v1, int v2, int newVLabel, int eLabel) {
			this.v1 = v1;
			this.v2 = v2;
			this.newVLabel = newVLabel;
			this.eLabel = eLabel;
			int h = 17;
			h = 31 * h + v1;
			h = 31 * h + v2;
			h = 31 * h + newVLabel;
			h = 31 * h + eLabel;
			this.hash = h;
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof OneEdgeExtension))
				return false;
			OneEdgeExtension t = (OneEdgeExtension) o;
			return v1 == t.v1 && v2 == t.v2 && newVLabel == t.newVLabel && eLabel == t.eLabel;
		}
	}

	private static <T> void addTo(Map<T, Set<Integer>> map, T key, int graphId) {
		// bottleneck fix: computeIfAbsent does a single lookup instead of the
		// previous get() + null-check + put() sequence when the key is absent
		map.computeIfAbsent(key, k -> new HashSet<>()).add(graphId);
	}

	/**
	 * Single pass over all embeddings of the (non-empty) DFS code c that fills BOTH
	 * maps at once:
	 *
	 * - rmExtensions : the right-most-path extensions, used to drive the
	 * depth-first search (exactly gSpan's semantics). - allExtensions : EVERY
	 * one-edge extension (any attachment vertex, plus cycle-closing edges), used
	 * for the closedness test.
	 *
	 * @return the number of times the edge-count pruning strategy discarded a
	 *         graph during this call (used for statistics only, tallied by the
	 *         caller).
	 */
	static int enumerateExtensions(DFSCode c, List<Graph> graphDatabase, Set<Integer> graphIds,
			Map<ExtendedEdge, Set<Integer>> rmExtensions, Map<OneEdgeExtension, Set<Integer>> allExtensions) {

		int rightMost = c.getRightMost();
		int pruneCount = 0;

		// bottleneck fix: this map used to be allocated brand-new for EVERY single
		// isomorphism found in EVERY graph of the database. It never escapes the
		// body of the innermost loop, so we allocate it once here and simply
		// clear()+refill it before each use below.
		Map<Integer, Integer> inverted = new HashMap<>();

		for (Integer graphId : graphIds) {
			Graph g = graphDatabase.get(graphId);

			// If the pattern already uses every edge of g, no extension is possible here.
			if (EDGE_COUNT_PRUNING && c.size() >= g.getEdgeCount()) {
				pruneCount++;
				continue;
			}

			for (Map<Integer, Integer> isom : subgraphIsomorphisms(c, g)) {

				inverted.clear();
				for (Entry<Integer, Integer> e : isom.entrySet()) {
					inverted.put(e.getValue(), e.getKey());
				}

				// Iterate over EVERY vertex of the pattern (not only the right-most path)
				for (Entry<Integer, Integer> e : isom.entrySet()) {
					int u = e.getKey();
					int mappedU = e.getValue();
					int uLabel = g.getVLabel(mappedU);
					boolean uOnRm = c.onRightMostPath(u);

					for (Vertex x : g.getAllNeighbors(mappedU)) {
						int mappedX = x.getId();
						int eLabel = g.getEdgeLabel(mappedU, mappedX);
						Integer w = inverted.get(mappedX);

						if (w == null) {
							// ---- forward extension: a new vertex is attached to u ----
							addTo(allExtensions, new OneEdgeExtension(u, -1, x.getLabel(), eLabel), graphId);

							if (uOnRm) {
								addTo(rmExtensions, new ExtendedEdge(u, rightMost + 1, uLabel, x.getLabel(), eLabel),
										graphId);
							}
						} else if (!c.containEdge(u, w)) {
							// ---- backward extension: cycle-closing edge u--w ----
							int a = (u < w) ? u : w;
							int b = (u < w) ? w : u;
							addTo(allExtensions, new OneEdgeExtension(a, b, -1, eLabel), graphId);

							if (u == rightMost && c.onRightMostPath(w) && c.notPreOfRM(w)) {
								addTo(rmExtensions, new ExtendedEdge(rightMost, w, uLabel, x.getLabel(), eLabel),
										graphId);
							}
						}
					}
				}
			}
		}
		return pruneCount;
	}

	/** All frequent-candidate 1-edge patterns (extensions of the empty graph). */
	static Map<ExtendedEdge, Set<Integer>> oneEdgeExtensions(List<Graph> graphDatabase, Set<Integer> graphIds) {
		Map<ExtendedEdge, Set<Integer>> extensions = new HashMap<>();
		for (Integer graphId : graphIds) {
			Graph g = graphDatabase.get(graphId);
			for (Vertex vertex : g.vertices) {
				for (Edge e : vertex.getEdgeList()) {
					int v1L = g.getVLabel(e.v1);
					int v2L = g.getVLabel(e.v2);
					ExtendedEdge ee1 = (v1L < v2L) ? new ExtendedEdge(0, 1, v1L, v2L, e.getEdgeLabel())
							: new ExtendedEdge(0, 1, v2L, v1L, e.getEdgeLabel());
					addTo(extensions, ee1, graphId);
				}
			}
		}
		return extensions;
	}
}