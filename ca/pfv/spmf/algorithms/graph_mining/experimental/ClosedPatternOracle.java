package ca.pfv.spmf.algorithms.graph_mining.experimental;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.algorithms.graph_mining.fsg.AlgoFSG;
import ca.pfv.spmf.algorithms.graph_mining.fsg.FSGPattern;

/**
 * A fully independent (naive, backtracking-based) closed-frequent-subgraph
 * oracle used ONLY to arbitrate disagreements between two mining algorithms
 * during testing. It does not reuse any DFS-code / right-most-extension
 * machinery, so it cannot share a bug with either AlgoCloseGraph or
 * AlgoCGSPANSupport.
 *
 * It is intentionally simple/slow (fine for the small synthetic graphs used in
 * the regression harness).
 */
public class ClosedPatternOracle {

	// ================================================================ SimpleGraph

	/**
	 * A graph read from the SPMF text format: vertex labels + adjacency (labeled).
	 */
	public static class SimpleGraph {
		int id;
		int[] vLabel; // vLabel[localIndex] = label
		List<int[]>[] adj; // adj[u] = list of {v, edgeLabel}
	}

	@SuppressWarnings("unchecked")
	public static List<SimpleGraph> readGraphs(String path) throws IOException {
		List<SimpleGraph> graphs = new ArrayList<>();
		List<String> lines = Files.readAllLines(Paths.get(path));

		List<Integer> vIds = new ArrayList<>();
		List<Integer> vLabels = new ArrayList<>();
		List<int[]> edges = new ArrayList<>();
		int curId = -1;
		boolean has = false;

		for (String raw : lines) {
			String line = raw.trim();
			if (line.isEmpty())
				continue;
			String[] p = line.split("\\s+");

			if (p[0].equals("t")) {
				if (has)
					graphs.add(build(curId, vIds, vLabels, edges));
				curId = Integer.parseInt(p[2]);
				vIds = new ArrayList<>();
				vLabels = new ArrayList<>();
				edges = new ArrayList<>();
				has = true;
			} else if (p[0].equals("v")) {
				vIds.add(Integer.parseInt(p[1]));
				vLabels.add(Integer.parseInt(p[2]));
			} else if (p[0].equals("e")) {
				edges.add(new int[] { Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]) });
			}
		}
		if (has)
			graphs.add(build(curId, vIds, vLabels, edges));
		return graphs;
	}

	@SuppressWarnings("unchecked")
	private static SimpleGraph build(int id, List<Integer> vIds, List<Integer> vLabels, List<int[]> edges) {
		SimpleGraph g = new SimpleGraph();
		g.id = id;
		int n = vIds.size();

		// map global vertex id -> local index
		Map<Integer, Integer> idx = new HashMap<>();
		for (int i = 0; i < n; i++)
			idx.put(vIds.get(i), i);

		g.vLabel = new int[n];
		for (int i = 0; i < n; i++)
			g.vLabel[i] = vLabels.get(i);

		g.adj = new List[n];
		for (int i = 0; i < n; i++)
			g.adj[i] = new ArrayList<>();
		for (int[] e : edges) {
			int u = idx.get(e[0]), v = idx.get(e[1]), l = e[2];
			g.adj[u].add(new int[] { v, l });
			g.adj[v].add(new int[] { u, l });
		}
		return g;
	}

	// ================================================================ Pattern

	/**
	 * A small pattern: vertex labels + edge list (u, v, edgeLabel) referring to
	 * local indices.
	 *
	 * <p>
	 * {@code graphIds} is populated by {@link #mineClosedPatterns} and by
	 * {@link #support}; it is {@code null} when a Pattern is constructed without
	 * support information.
	 */
	public static class Pattern {
		/** Vertex labels (local indices 0..n-1). */
		public int[] vLabels;
		/** Edges as {u, v, edgeLabel} over local indices. */
		public List<int[]> edges;
		/**
		 * IDs of the database graphs that contain at least one embedding of this
		 * pattern. Populated by {@link #mineClosedPatterns}.
		 */
		public Set<Integer> graphIds;

		public Pattern(int[] vLabel, List<int[]> edges) {
			this.vLabels = vLabel;
			this.edges = edges;
			this.graphIds = null;
		}

		/** Convenience copy-constructor that also copies graphIds. */
		Pattern(Pattern src) {
			this.vLabels = src.vLabels.clone();
			this.edges = new ArrayList<>(src.edges);
			this.graphIds = (src.graphIds == null) ? null : new HashSet<>(src.graphIds);
		}
	}

	// ================================================================ embeddings

	/**
	 * Finds ALL injective embeddings of {@code p} into {@code g} via naive
	 * backtracking (fine for patterns with a handful of vertices).
	 *
	 * @return list of mappings; mapping[i] = graph-vertex index mapped to
	 *         pattern-vertex i.
	 */
	public static List<int[]> findEmbeddings(Pattern p, SimpleGraph g) {
		List<int[]> results = new ArrayList<>();
		int[] mapping = new int[p.vLabels.length];
		boolean[] used = new boolean[g.vLabel.length];
		backtrack(p, g, 0, mapping, used, results);
		return results;
	}

	private static void backtrack(Pattern p, SimpleGraph g, int i, int[] mapping, boolean[] used, List<int[]> results) {
		if (i == p.vLabels.length) {
			results.add(mapping.clone());
			return;
		}
		for (int cand = 0; cand < g.vLabel.length; cand++) {
			if (used[cand] || g.vLabel[cand] != p.vLabels[i])
				continue;

			// verify all pattern edges incident to vertex i against already-
			// assigned vertices (indices < i)
			boolean ok = true;
			for (int[] e : p.edges) {
				int a = e[0], b = e[1], el = e[2];
				if (a == i && b < i) {
					if (!hasEdge(g, cand, mapping[b], el)) {
						ok = false;
						break;
					}
				} else if (b == i && a < i) {
					if (!hasEdge(g, cand, mapping[a], el)) {
						ok = false;
						break;
					}
				}
			}
			if (!ok)
				continue;

			mapping[i] = cand;
			used[cand] = true;
			backtrack(p, g, i + 1, mapping, used, results);
			used[cand] = false;
		}
	}

	private static boolean hasEdge(SimpleGraph g, int u, int v, int label) {
		for (int[] e : g.adj[u]) {
			if (e[0] == v && e[1] == label)
				return true;
		}
		return false;
	}

	// ================================================================ support

	/**
	 * Returns the graph-level support: the set of graph ids that contain at least
	 * one embedding of {@code p}.
	 */
	public static Set<Integer> support(Pattern p, List<SimpleGraph> db) {
		Set<Integer> ids = new HashSet<>();
		for (SimpleGraph g : db) {
			if (!findEmbeddings(p, g).isEmpty())
				ids.add(g.id);
		}
		return ids;
	}

	// ================================================================ isClosed

	/**
	 * Ground-truth closedness check: tries every possible one-extension (forward
	 * edge to new vertex, or backward edge between existing vertices) using every
	 * label combination observed in the database, and returns {@code false} as soon
	 * as an extension with equal support is found.
	 */
	public static boolean isClosed(Pattern p, List<SimpleGraph> db) {
		Set<Integer> baseSupport = support(p, db);
		int baseSup = baseSupport.size();
		if (baseSup == 0)
			return true; // infrequent – degenerate case

		// collect all vertex/edge labels that appear anywhere in the database
		Set<Integer> allVLabels = new HashSet<>();
		Set<Integer> allELabels = new HashSet<>();
		for (SimpleGraph g : db) {
			for (int l : g.vLabel)
				allVLabels.add(l);
			for (List<int[]> lst : g.adj)
				for (int[] e : lst)
					allELabels.add(e[1]);
		}

		int n = p.vLabels.length;

		// 1) forward extensions: attach a new vertex to any existing vertex
		for (int u = 0; u < n; u++) {
			for (int newLabel : allVLabels) {
				for (int eLabel : allELabels) {
					int[] newVL = Arrays.copyOf(p.vLabels, n + 1);
					newVL[n] = newLabel;
					List<int[]> newE = new ArrayList<>(p.edges);
					newE.add(new int[] { u, n, eLabel });
					Pattern child = new Pattern(newVL, newE);

					Set<Integer> childSup = support(child, db);
					if (childSup.size() == baseSup && !childSup.isEmpty())
						return false;
				}
			}
		}

		// 2) backward extensions: add an edge between two existing vertices
		// that are not already directly connected in the pattern
		for (int u = 0; u < n; u++) {
			for (int v = u + 1; v < n; v++) {
				boolean already = false;
				for (int[] e : p.edges) {
					if ((e[0] == u && e[1] == v) || (e[0] == v && e[1] == u)) {
						already = true;
						break;
					}
				}
				if (already)
					continue;

				for (int eLabel : allELabels) {
					List<int[]> newE = new ArrayList<>(p.edges);
					newE.add(new int[] { u, v, eLabel });
					Pattern child = new Pattern(p.vLabels.clone(), newE);

					Set<Integer> childSup = support(child, db);
					if (childSup.size() == baseSup && !childSup.isEmpty())
						return false;
				}
			}
		}

		return true;
	}

	// ================================================================
	// mineClosedPatterns

	/**
	 * Enumerates ALL closed frequent subgraphs in {@code db} with absolute support
	 * ≥ {@code minSupAbs}.
	 *
	 * <p>
	 * Strategy:
	 * <ol>
	 * <li>Enumerate every connected subgraph (up to isomorphism) by growing
	 * single-vertex seeds with the same forward/backward extension scheme used by
	 * {@link #isClosed}.</li>
	 * <li>Deduplicate by canonical signature.</li>
	 * <li>Keep only those that are frequent AND closed.</li>
	 * </ol>
	 *
	 * @param db             the graph database
	 * @param minSupAbs      minimum absolute support (number of graphs)
	 * @param includeSingleV whether to include single-vertex patterns
	 * @return list of closed frequent patterns, each with {@code graphIds} set
	 */
	public static List<Pattern> mineClosedPatterns(List<SimpleGraph> db, int minSupAbs, boolean includeSingleV) {

		// collect all vertex/edge labels from the database
		Set<Integer> allVLabels = new HashSet<>();
		Set<Integer> allELabels = new HashSet<>();
		for (SimpleGraph g : db) {
			for (int l : g.vLabel)
				allVLabels.add(l);
			for (List<int[]> lst : g.adj)
				for (int[] e : lst)
					allELabels.add(e[1]);
		}

		// map: canonical-signature -> Pattern (with graphIds filled)
		Map<String, Pattern> found = new LinkedHashMap<>();

		// seed: one vertex per label
		for (int vl : allVLabels) {
			Pattern seed = new Pattern(new int[] { vl }, new ArrayList<>());
			seed.graphIds = support(seed, db);
			if (seed.graphIds.size() < minSupAbs)
				continue;

			// BFS/DFS-style growth
			growPattern(seed, db, minSupAbs, allVLabels, allELabels, found);

			// register the seed itself (after recursion so larger patterns
			// that subsume it with equal support are seen first)
			if (includeSingleV) {
				String sig = canonicalSignature(seed);
				found.putIfAbsent(sig, seed);
			}
		}

		// filter: keep only closed patterns
		List<Pattern> result = new ArrayList<>();
		for (Pattern p : found.values()) {
			if (isClosed(p, db))
				result.add(p);
		}
		return result;
	}

	/**
	 * Recursively grows {@code p} by one edge (forward or backward) and registers
	 * all resulting frequent patterns.
	 */
	private static void growPattern(Pattern p, List<SimpleGraph> db, int minSupAbs, Set<Integer> allVLabels,
			Set<Integer> allELabels, Map<String, Pattern> found) {
		int n = p.vLabels.length;

		// 1) forward extensions
		for (int u = 0; u < n; u++) {
			for (int newVL : allVLabels) {
				for (int eL : allELabels) {
					int[] newVLabels = Arrays.copyOf(p.vLabels, n + 1);
					newVLabels[n] = newVL;
					List<int[]> newEdges = new ArrayList<>(p.edges);
					newEdges.add(new int[] { u, n, eL });

					Pattern child = new Pattern(newVLabels, newEdges);
					child.graphIds = support(child, db);
					if (child.graphIds.size() < minSupAbs)
						continue;

					String sig = canonicalSignature(child);
					if (!found.containsKey(sig)) {
						found.put(sig, child);
						growPattern(child, db, minSupAbs, allVLabels, allELabels, found);
					}
				}
			}
		}

		// 2) backward extensions (add edge between existing vertices)
		for (int u = 0; u < n; u++) {
			for (int v = u + 1; v < n; v++) {
				boolean already = false;
				for (int[] e : p.edges) {
					if ((e[0] == u && e[1] == v) || (e[0] == v && e[1] == u)) {
						already = true;
						break;
					}
				}
				if (already)
					continue;

				for (int eL : allELabels) {
					List<int[]> newEdges = new ArrayList<>(p.edges);
					newEdges.add(new int[] { u, v, eL });

					Pattern child = new Pattern(p.vLabels.clone(), newEdges);
					child.graphIds = support(child, db);
					if (child.graphIds.size() < minSupAbs)
						continue;

					String sig = canonicalSignature(child);
					if (!found.containsKey(sig)) {
						found.put(sig, child);
						growPattern(child, db, minSupAbs, allVLabels, allELabels, found);
					}
				}
			}
		}
	}

	// ================================================================ canonical
	// signature

	/**
	 * Computes an isomorphism-invariant canonical signature for a pattern by
	 * delegating to {@link AlgoFSG#canonicalize}.
	 */
	static String canonicalSignature(Pattern p) {
		int[][] edgeArr = new int[p.edges.size()][];
		for (int i = 0; i < p.edges.size(); i++)
			edgeArr[i] = p.edges.get(i).clone();
		FSGPattern.CanonResult cr = FSGPattern.canonicalize(p.vLabels.clone(), edgeArr);
		return Arrays.toString(cr.key);
	}
}