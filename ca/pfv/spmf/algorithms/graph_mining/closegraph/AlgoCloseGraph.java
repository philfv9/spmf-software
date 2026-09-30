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
import ca.pfv.spmf.algorithms.graph_mining.tkg.ExtendedEdge;
import ca.pfv.spmf.algorithms.graph_mining.tkg.FrequentSubgraph;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Graph;
import ca.pfv.spmf.algorithms.graph_mining.tkg.Vertex;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * Implementation of the CloseGraph algorithm for mining CLOSED frequent
 * subgraphs, built on the gSpan search framework (right-most extension + DFS
 * lexicographic order + minimum-DFS-code canonical test).
 *
 * X. Yan, J. Han. "CloseGraph: Mining Closed Frequent Graph Patterns". KDD
 * 2003.
 *
 * CLOSEDNESS TEST (exact): A frequent graph g is closed iff NO one-edge
 * supergraph h = g diamond e has support(h) == support(g). Because every
 * one-edge extension h of g satisfies support(h) <= support(g), and because any
 * proper supergraph g' with support(g') == support(g) contains at least one
 * such h with support(g) >= support(h) >= support(g'), checking one-edge
 * extensions is both SOUND and COMPLETE.
 *
 * IMPORTANT: the extensions used for the closedness test must be ALL one-edge
 * extensions (an edge may be attached to ANY vertex of the pattern, and may
 * also connect two already-existing pattern vertices) -- NOT only the
 * right-most-path extensions used to drive the search. Using only right-most
 * extensions makes the test unsound (vertices off the right-most path can never
 * be extended) and produces non-closed patterns in the output.
 *
 * Reuses (unmodified): Graph, Vertex, Edge, ExtendedEdge, DFSCode,
 * FrequentSubgraph.
 *
 * NOTE: this class only contains the high-level orchestration (public API and
 * the mining/DFS logic itself). The gSpan-style subgraph-isomorphism /
 * canonical-form / extension-enumeration routines live in
 * {@link CloseGraphExtensionUtil}, and all file I/O lives in
 * {@link CloseGraphIO}.
 */
public class AlgoCloseGraph {

	/** the minimum support represented as a count (number of graphs) */
	private int minSup;

	/** The list of CLOSED frequent subgraphs found by the last execution */
	private List<FrequentSubgraph> frequentSubgraphs;

	/** runtime of the most recent execution (seconds) */
	private long runtime = 0;

	/** max memory usage of the most recent execution (MB) */
	private double maxmemory = 0;

	/** pattern count of the most recent execution */
	private int patternCount = 0;

	/** number of graphs in the input database */
	private int graphCount = 0;

	/** frequent vertex labels */
	List<Integer> frequentVertexLabels;

	/**
	 * vertex label -> ids of the graphs where it occurs (on a non-isolated vertex)
	 */
	Map<Integer, Set<Integer>> labelSupportMap;

	/** if true, debug mode is activated */
	private static final boolean DEBUG_MODE = false;

	/** eliminate infrequent vertex labels from the graphs (safe pruning) */
	private static final boolean ELIMINATE_INFREQUENT_VERTICES = true;

	/** stats */
	int infrequentVerticesRemovedCount;
	int emptyGraphsRemoved;
	int pruneByEdgeCountCount;
	int nonClosedFilteredCount;

	/** maximum number of edges in each closed frequent subgraph */
	int maxNumberOfEdges = Integer.MAX_VALUE;

	/** Output the ids of graphs containing each closed frequent subgraph */
	boolean outputGraphIds = true;

	/** If true, closed frequent single-vertex subgraphs are also output */
	boolean outputSingleVertices = false;

	/**
	 * bottleneck fix: pool of reusable "right-most path extensions" maps, indexed
	 * by recursion depth (== DFSCode size at the time closeGraphDFS() is called).
	 * Because the search is single-threaded and strictly depth-first, only one
	 * map per depth level is ever "in use" at a given instant, so instead of
	 * allocating a brand-new HashMap on every single recursive call, we borrow
	 * the map belonging to the current depth and clear() it before use. The Set
	 * values contained in the map are never mutated in place -- they are simply
	 * looked up and handed off to the recursive call or stored in a
	 * FrequentSubgraph -- so reusing (and later clearing) the map container
	 * itself does not affect anything that has already captured a reference to
	 * one of its values.
	 */
	private final List<Map<ExtendedEdge, Set<Integer>>> rmExtensionsPool = new ArrayList<>();

	/**
	 * bottleneck fix: same pooling idea as {@link #rmExtensionsPool}, but for the
	 * "all one-edge extensions" map used only transiently for the closedness
	 * test.
	 */
	private final List<Map<CloseGraphExtensionUtil.OneEdgeExtension, Set<Integer>>> allExtensionsPool = new ArrayList<>();

	// =====================================================================
	// Public API (signature aligned with AlgoGSPAN / AlgoCGSPANSupport)
	// =====================================================================

	/**
	 * Run CloseGraph.
	 *
	 * @param inPath               the input file
	 * @param outPath              the output file
	 * @param minSupport           minimum support (percentage between 0 and 1)
	 * @param outputSingleVertices if true, closed frequent single-vertex subgraphs
	 *                             are output as well
	 * @param outputDotFile        if true, a GraphViz DOT file is generated
	 * @param maxNumberOfEdges     maximum number of edges per pattern
	 * @param outputGraphIds       output the ids of the graphs containing each
	 *                             pattern
	 */
	public void runAlgorithm(String inPath, String outPath, double minSupport, boolean outputSingleVertices,
			boolean outputDotFile, int maxNumberOfEdges, boolean outputGraphIds)
			throws IOException, ClassNotFoundException {

		if (maxNumberOfEdges <= 0) {
			return;
		}

		this.maxNumberOfEdges = maxNumberOfEdges;
		this.outputGraphIds = outputGraphIds;
		this.outputSingleVertices = outputSingleVertices;

		infrequentVerticesRemovedCount = 0;
		emptyGraphsRemoved = 0;
		pruneByEdgeCountCount = 0;
		nonClosedFilteredCount = 0;

		frequentSubgraphs = new ArrayList<FrequentSubgraph>();

		// bottleneck fix: reset the map pools at the start of every run so that a
		// reused AlgoCloseGraph instance does not carry stale maps between runs.
		rmExtensionsPool.clear();
		allExtensionsPool.clear();

		MemoryLogger.getInstance().reset();
		patternCount = 0;

		long t1 = System.currentTimeMillis();

		List<Graph> graphDB = CloseGraphIO.readGraphs(inPath);
		graphCount = graphDB.size();

		minSup = (int) Math.ceil(minSupport * graphDB.size());

		closeGraphMining(graphDB);

		MemoryLogger.getInstance().checkMemory();

		CloseGraphIO.writeResultToFile(outPath, frequentSubgraphs, outputGraphIds);

		long t2 = System.currentTimeMillis();
		runtime = (t2 - t1) / 1000;
		maxmemory = MemoryLogger.getInstance().getMaxMemory();
		patternCount = frequentSubgraphs.size();

		if (outputDotFile) {
			CloseGraphIO.outputDotFile(outPath);
		}
	}

	/**
	 * Convenience overload: no single vertices, no size limit, output graph ids.
	 */
	public void runAlgorithm(String inPath, String outPath, double minSupport)
			throws IOException, ClassNotFoundException {
		runAlgorithm(inPath, outPath, minSupport, false, false, Integer.MAX_VALUE, true);
	}

	/** Convenience overload kept for backward compatibility. */
	public void runAlgorithm(String inPath, String outPath, double minSupport, int maxNumberOfEdges,
			boolean outputGraphIds) throws IOException, ClassNotFoundException {
		runAlgorithm(inPath, outPath, minSupport, false, false, maxNumberOfEdges, outputGraphIds);
	}

	// =====================================================================
	// Mining
	// =====================================================================

	private void closeGraphMining(List<Graph> graphDB) throws IOException, ClassNotFoundException {

		findAllOnlyOneVertex(graphDB);

		for (Graph g : graphDB) {
			g.precalculateVertexList();
		}

		// bottleneck fix: pre-size to the known final size to avoid internal
		// rehashing while the set grows from empty to graphDB.size() entries.
		Set<Integer> graphIds = new HashSet<Integer>(graphDB.size() * 2);
		for (int i = 0; i < graphDB.size(); i++) {
			Graph g = graphDB.get(i);
			if (g.vertices == null || g.vertices.length != 0) {
				if (infrequentVerticesRemovedCount > 0) {
					g.precalculateVertexList();
				}
				graphIds.add(i);
				g.precalculateVertexNeighbors();
				g.precalculateLabelsToVertices();
			} else {
				emptyGraphsRemoved++;
			}
		}

		if (frequentVertexLabels == null || frequentVertexLabels.isEmpty()) {
			return;
		}

		// All 1-edge patterns with their support sets
		Map<ExtendedEdge, Set<Integer>> initialExtensions = CloseGraphExtensionUtil.oneEdgeExtensions(graphDB,
				graphIds);

		// ---- closed frequent single-vertex patterns (optional) --------------
		if (outputSingleVertices) {
			for (int label : frequentVertexLabels) {
				Set<Integer> supportSet = labelSupportMap.get(label);
				int sup = supportSet.size();

				// A single vertex v:L is non-closed iff some 1-edge pattern
				// containing L has exactly the same support.
				boolean closed = true;
				for (Entry<ExtendedEdge, Set<Integer>> entry : initialExtensions.entrySet()) {
					ExtendedEdge ee = entry.getKey();
					if ((ee.getvLabel1() == label || ee.getvLabel2() == label) && entry.getValue().size() == sup) {
						closed = false;
						break;
					}
				}

				if (closed) {
					DFSCode tempD = new DFSCode();
					tempD.add(new ExtendedEdge(0, 0, label, label, -1));
					frequentSubgraphs.add(new FrequentSubgraph(tempD, supportSet, sup));
				} else {
					nonClosedFilteredCount++;
				}
			}
		}

		// ---- depth-first search from every frequent canonical 1-edge pattern --
		for (Entry<ExtendedEdge, Set<Integer>> entry : initialExtensions.entrySet()) {
			Set<Integer> newGraphIDs = entry.getValue();
			if (newGraphIDs.size() >= minSup) {
				DFSCode newC = new DFSCode();
				newC.add(entry.getKey());
				if (CloseGraphExtensionUtil.isCanonical(newC)) {
					closeGraphDFS(newC, graphDB, newGraphIDs);
				}
			}
		}

		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * bottleneck fix: fetches (allocating on first use only) the pooled
	 * right-most extensions map for the given recursion depth, clearing it so it
	 * looks "brand new" to the caller.
	 */
	private Map<ExtendedEdge, Set<Integer>> borrowRmExtensionsMap(int depth) {
		while (rmExtensionsPool.size() <= depth) {
			rmExtensionsPool.add(new HashMap<ExtendedEdge, Set<Integer>>());
		}
		Map<ExtendedEdge, Set<Integer>> map = rmExtensionsPool.get(depth);
		map.clear();
		return map;
	}

	/**
	 * bottleneck fix: same as {@link #borrowRmExtensionsMap(int)} but for the
	 * "all one-edge extensions" map used by the closedness test.
	 */
	private Map<CloseGraphExtensionUtil.OneEdgeExtension, Set<Integer>> borrowAllExtensionsMap(int depth) {
		while (allExtensionsPool.size() <= depth) {
			allExtensionsPool.add(new HashMap<CloseGraphExtensionUtil.OneEdgeExtension, Set<Integer>>());
		}
		Map<CloseGraphExtensionUtil.OneEdgeExtension, Set<Integer>> map = allExtensionsPool.get(depth);
		map.clear();
		return map;
	}

	/**
	 * Recursive right-most-extension search with the (exact) closedness test.
	 */
	private void closeGraphDFS(DFSCode c, List<Graph> graphDB, Set<Integer> graphIds)
			throws IOException, ClassNotFoundException {

		// bottleneck fix: borrow pooled maps instead of allocating two brand-new
		// HashMaps on every single recursive call (see field javadoc above).
		int depth = c.size();
		Map<ExtendedEdge, Set<Integer>> rmExtensions = borrowRmExtensionsMap(depth);
		Map<CloseGraphExtensionUtil.OneEdgeExtension, Set<Integer>> allExtensions = borrowAllExtensionsMap(depth);
		pruneByEdgeCountCount += CloseGraphExtensionUtil.enumerateExtensions(c, graphDB, graphIds, rmExtensions,
				allExtensions);

		int curSup = graphIds.size();

		// ---- CLOSEDNESS TEST over ALL one-edge extensions --------------------
		boolean closed = true;
		for (Set<Integer> idSet : allExtensions.values()) {
			if (idSet.size() == curSup) { // a proper supergraph has the same support
				closed = false;
				break;
			}
		}

		if (closed) {
			frequentSubgraphs.add(new FrequentSubgraph(c, graphIds, curSup));
		} else {
			nonClosedFilteredCount++;
		}

		// ---- size limit: output first (fix), only then stop growing ----------
		if (c.size() >= maxNumberOfEdges) {
			return;
		}

		// Keep exploring every frequent canonical right-most extension, even when
		// c itself is not closed: descendants may still be closed patterns.
		for (Entry<ExtendedEdge, Set<Integer>> entry : rmExtensions.entrySet()) {
			Set<Integer> newGraphIDs = entry.getValue();
			if (newGraphIDs.size() >= minSup) {
				DFSCode newC = c.copy();
				newC.add(entry.getKey());
				if (CloseGraphExtensionUtil.isCanonical(newC)) {
					closeGraphDFS(newC, graphDB, newGraphIDs);
				}
			}
		}

		MemoryLogger.getInstance().checkMemory();
	}

	private void findAllOnlyOneVertex(List<Graph> graphDB) {

		frequentVertexLabels = new ArrayList<Integer>();
		labelSupportMap = new HashMap<Integer, Set<Integer>>();

		Map<Integer, Set<Integer>> labelM = new HashMap<>();

		for (Graph g : graphDB) {
			for (Vertex v : g.getNonPrecalculatedAllVertices()) {
				if (!v.getEdgeList().isEmpty()) { // gSpan/CGSpan convention
					Integer vLabel = v.getLabel();
					// bottleneck fix: computeIfAbsent performs a single hash lookup
					// instead of the previous get() + null-check + put() sequence
					labelM.computeIfAbsent(vLabel, k -> new HashSet<>()).add(g.getId());
				}
			}
		}

		for (Entry<Integer, Set<Integer>> entry : labelM.entrySet()) {
			int label = entry.getKey();
			Set<Integer> tempSupG = entry.getValue();
			if (tempSupG.size() >= minSup) {
				frequentVertexLabels.add(label);
				labelSupportMap.put(label, tempSupG);
			} else if (ELIMINATE_INFREQUENT_VERTICES) {
				for (Integer graphid : tempSupG) {
					graphDB.get(graphid).removeInfrequentLabel(label);
					infrequentVerticesRemovedCount++;
				}
			}
		}
	}

	public void printStats() {
		System.out.println("=============  CLOSEGRAPH - STATS =============");
		System.out.println(" Number of graphs in the input database: " + graphCount);
		System.out.println(" Closed frequent subgraph count : " + patternCount);
		System.out.println(" Non-closed patterns filtered out : " + nonClosedFilteredCount);
		System.out.println(" Total time ~ " + runtime + " s");
		System.out.println(" Minsup : " + minSup + " graphs");
		System.out.println(" Maximum memory usage : " + maxmemory + " mb");
		System.out.println("===================================================");
	}
}