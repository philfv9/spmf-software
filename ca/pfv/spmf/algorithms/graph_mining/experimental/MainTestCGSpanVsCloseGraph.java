package ca.pfv.spmf.algorithms.graph_mining.experimental;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import ca.pfv.spmf.algorithms.graph_mining.closegraph.AlgoCloseGraph;
import ca.pfv.spmf.algorithms.graph_mining.fsg.FSGPattern;
import ca.pfv.spmf.algorithms.graph_mining.tkg.AlgoCGSPANSupport;

/**
 * Automated regression harness: CGSpan vs CloseGraph.
 *
 * Both algorithms mine CLOSED frequent subgraphs, so their output sets must be
 * identical (same patterns, same support values, same graph-id lists).
 *
 * The harness: 1. Generates a random labeled graph database. 2. Runs CGSpan and
 * CloseGraph on it. 3. Parses both output files into canonical-signature-keyed
 * maps. 4. Compares the maps and reports any discrepancy in full detail. 5.
 * Accumulates wall-clock time and heap-delta metrics across all tests and
 * prints a final performance ranking.
 *
 * Stopping behaviour: the harness stops at the first failure so that the
 * developer can inspect the generated database and both outputs immediately.
 * Comment out the {@code break} in main() to continue through all tests.
 */
public class MainTestCGSpanVsCloseGraph {

	// Both algorithms MUST agree on this, otherwise the single-vertex patterns
	// reported by one of them look like spurious differences.
	private static final boolean OUTPUT_SINGLE_VERTICES = false;

	// ---------------------------------------------------------------- config
	private static final int NUM_TESTS = 1000;
	private static final double MIN_SUPPORT = 0.5;
	private static final int MIN_GRAPHS = 3;
	private static final int MAX_GRAPHS = 3; // keep tests fast
	private static final int MIN_VERTICES = 2;
	private static final int MAX_VERTICES = 4;
	private static final double EDGE_PROBABILITY = 0.4;
	private static final int NUM_VERTEX_LABELS = 3;
	private static final int NUM_EDGE_LABELS = 2;
	private static final long BASE_SEED = 20240101L;

	// temp files
	private static final String TMP_INPUT = System.getProperty("java.io.tmpdir") + File.separator + "cgcg_input.txt";
	private static final String TMP_CGSPAN = System.getProperty("java.io.tmpdir") + File.separator
			+ "cgcg_out_cgspan.txt";
	private static final String TMP_CLOSEGRAPH = System.getProperty("java.io.tmpdir") + File.separator
			+ "cgcg_out_closegraph.txt";

	// failure artefact directory
	private static final String FAIL_DIR = System.getProperty("java.io.tmpdir") + File.separator + "cgcg_failures";

	// --------------------------------------------------------- perf accumulators
	private static long cgspanTotalTimeMs = 0L;
	private static long closeGraphTotalTimeMs = 0L;
	private static long cgspanTotalMemBytes = 0L;
	private static long closeGraphTotalMemBytes = 0L;
	private static int perfTestsCount = 0;

	// ====================================================================== main
	public static void main(String[] args) throws Exception {

		Files.createDirectories(Paths.get(FAIL_DIR));

		int passed = 0, failed = 0;

		for (int t = 0; t < NUM_TESTS; t++) {
			long seed = BASE_SEED + t;
			Random rng = new Random(seed);

			System.out.printf("%n");
			System.out.printf("═══════════════════════════════════════════════════════════%n");
			System.out.printf("  TEST %d / %d  (seed = %d)%n", t + 1, NUM_TESTS, seed);
			System.out.printf("═══════════════════════════════════════════════════════════%n");

			// 1. generate database
			String graphDb = generateGraphDatabase(rng);
			writeFile(TMP_INPUT, graphDb);
			System.out.println("✓ Generated graph database:");
			printGraphDatabaseSummary(graphDb);

			// 2. run CGSpan
			RunMetrics cgspanMetrics = new RunMetrics();
			Map<String, PatternInfo> cgspanResult;
			try {
				System.out.println("\n⏳ Running CGSpan...");
				cgspanResult = runCGSpan(cgspanMetrics);
				System.out.printf("✓ CGSpan completed in %d ms  |  peak memory: %s%n", cgspanMetrics.runtimeMs,
						formatBytes(cgspanMetrics.peakMemoryBytes));
				System.out.printf("  CGSpan found %d closed patterns%n", cgspanResult.size());
			} catch (Exception e) {
				failed++;
				System.err.println("\n❌ CGSpan CRASHED!");
				printException(e);
				saveFailure(t, seed, graphDb, "", "", "CGSPAN_EXCEPTION");
				printDebugFooter(t, seed);
				continue;
			}

			// 3. run CloseGraph
			RunMetrics closeGraphMetrics = new RunMetrics();
			Map<String, PatternInfo> closeGraphResult;
			try {
				System.out.println("\n⏳ Running CloseGraph...");
				closeGraphResult = runCloseGraph(closeGraphMetrics);
				System.out.printf("✓ CloseGraph completed in %d ms  |  peak memory: %s%n", closeGraphMetrics.runtimeMs,
						formatBytes(closeGraphMetrics.peakMemoryBytes));
				System.out.printf("  CloseGraph found %d closed patterns%n", closeGraphResult.size());
			} catch (Exception e) {
				failed++;
				System.err.println("\n❌ CloseGraph CRASHED!");
				printException(e);
				saveFailure(t, seed, graphDb, readFile(TMP_CGSPAN), "", "CLOSEGRAPH_EXCEPTION");
				printDebugFooter(t, seed);
				continue;
			}

			// accumulate perf
			cgspanTotalTimeMs += cgspanMetrics.runtimeMs;
			closeGraphTotalTimeMs += closeGraphMetrics.runtimeMs;
			cgspanTotalMemBytes += cgspanMetrics.peakMemoryBytes;
			closeGraphTotalMemBytes += closeGraphMetrics.peakMemoryBytes;
			perfTestsCount++;

			printPerTestRanking(cgspanMetrics, closeGraphMetrics);

			// 4. compare
			ComparisonResult cmp = compareResults(cgspanResult, closeGraphResult);

			if (cmp.identical) {
				passed++;
				System.out.println("\n✅ PASS – results match exactly");
				if ((t + 1) % 50 == 0) {
					System.out.printf("%n🎯 Progress: %d / %d tests passed so far%n", passed, t + 1);
					printRunningTotals();
				}
			} else {
				failed++;
				System.err.println("\n❌ FAIL – results differ!");
				printDetailedComparison(cmp, cgspanResult, closeGraphResult);

				// NEW: arbitrate using the independent brute-force oracle
				arbitrateWithOracle(cmp, cgspanResult, closeGraphResult);

				String cgOut = readFile(TMP_CGSPAN);
				String clOut = readFile(TMP_CLOSEGRAPH);
				saveFailure(t, seed, graphDb, cgOut, clOut, "MISMATCH");
				printFullDebugInfo(graphDb, cgOut, clOut);
				printDebugFooter(t, seed);

				System.err.println("\n⛔ STOPPING AT FIRST FAILURE FOR DEBUGGING.");
				break;
			}
		}

		// ── final summary ────────────────────────────────────────────────────
		System.out.println("\n");
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("                       FINAL RESULTS                       ");
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.printf(" Passed : %d / %d%n", passed, NUM_TESTS);
		System.out.printf(" Failed : %d / %d%n", failed, NUM_TESTS);
		System.out.println("═══════════════════════════════════════════════════════════");
		printFinalPerformanceRanking();

		if (failed > 0) {
			System.err.println("\n⚠ Failure artefacts saved to: " + FAIL_DIR);
		}
		System.exit(failed > 0 ? 1 : 0);
	}

	/**
	 * For every pattern that differs between CGSpan and CloseGraph, ask the
	 * independent brute-force oracle who is right, and print a verdict.
	 */
	static void arbitrateWithOracle(ComparisonResult cmp, Map<String, PatternInfo> cgspan,
			Map<String, PatternInfo> closeGraph) throws IOException {

		List<ClosedPatternOracle.SimpleGraph> db = ClosedPatternOracle.readGraphs(TMP_INPUT);

		System.err.println("\n┌──────────────────────────────────────────────────────────────┐");
		System.err.println("│              ORACLE ARBITRATION (ground truth)               │");
		System.err.println("└──────────────────────────────────────────────────────────────┘");

		Set<String> toCheck = new TreeSet<>();
		toCheck.addAll(cmp.onlyInCGSpan);
		toCheck.addAll(cmp.onlyInCloseGraph);

		for (String sig : toCheck) {
			PatternInfo p = cgspan.containsKey(sig) ? cgspan.get(sig) : closeGraph.get(sig);

			int[] vlabels = p.vlabels.stream().mapToInt(Integer::intValue).toArray();
			ClosedPatternOracle.Pattern pattern = new ClosedPatternOracle.Pattern(vlabels, p.edges);

			boolean actuallyClosed = ClosedPatternOracle.isClosed(pattern, db);
			Set<Integer> trueSupport = ClosedPatternOracle.support(pattern, db);

			boolean foundByCGSpan = cgspan.containsKey(sig);
			boolean foundByCloseGraph = closeGraph.containsKey(sig);

			System.err.printf("%n  Pattern %s  vlabels=%s edges=%s%n", sig, p.vlabels, edgesToString(p.edges));
			System.err.printf("    True support (oracle)   : %s%n", trueSupport);
			System.err.printf("    Oracle verdict           : %s%n",
					actuallyClosed ? "CLOSED (should be in output)" : "NOT CLOSED (should be excluded)");
			System.err.printf("    Found by CGSpan          : %s%n", foundByCGSpan);
			System.err.printf("    Found by CloseGraph      : %s%n", foundByCloseGraph);

			boolean cgspanCorrect = (foundByCGSpan == actuallyClosed);
			boolean closeGraphCorrect = (foundByCloseGraph == actuallyClosed);

			if (cgspanCorrect && !closeGraphCorrect) {
				System.err.println("    ⚠ VERDICT: CloseGraph is WRONG on this pattern.");
			} else if (closeGraphCorrect && !cgspanCorrect) {
				System.err.println("    ⚠ VERDICT: CGSpan is WRONG on this pattern.");
			} else if (!cgspanCorrect && !closeGraphCorrect) {
				System.err.println("    ⚠ VERDICT: BOTH algorithms are wrong on this pattern!");
			} else {
				System.err.println("    (unexpected: oracle agrees with both — check parsing)");
			}
		}
	}

	// ================================================================== runners

	/** Wall-clock time (ms) and heap-delta (bytes) for one algorithm run. */
	static class RunMetrics {
		long runtimeMs;
		long peakMemoryBytes;
	}

	static Map<String, PatternInfo> runCGSpan(RunMetrics m) throws Exception {
		Runtime rt = Runtime.getRuntime();
		rt.gc();
		rt.gc();
		long memBefore = rt.totalMemory() - rt.freeMemory();
		long start = System.currentTimeMillis();

		System.out.println("=========" + MIN_SUPPORT);
		AlgoCGSPANSupport algo = new AlgoCGSPANSupport();
		algo.setDetectEarlyTerminationFailure(true);
		algo.setPdfsAutomorphismOptimization(true);
		algo.runAlgorithm(TMP_INPUT, TMP_CGSPAN, MIN_SUPPORT, OUTPUT_SINGLE_VERTICES, false, Integer.MAX_VALUE, true);

		m.runtimeMs = System.currentTimeMillis() - start;
		m.peakMemoryBytes = Math.max(0L, rt.totalMemory() - rt.freeMemory() - memBefore);
		return parseOutputDetailed(TMP_CGSPAN, "CGSpan");
	}

	static Map<String, PatternInfo> runCloseGraph(RunMetrics m) throws Exception {
		Runtime rt = Runtime.getRuntime();
		rt.gc();
		rt.gc();
		long memBefore = rt.totalMemory() - rt.freeMemory();
		long start = System.currentTimeMillis();

		AlgoCloseGraph algo = new AlgoCloseGraph();
		algo.runAlgorithm(TMP_INPUT, TMP_CLOSEGRAPH, MIN_SUPPORT, OUTPUT_SINGLE_VERTICES, false, Integer.MAX_VALUE,
				true);

		m.runtimeMs = System.currentTimeMillis() - start;
		m.peakMemoryBytes = Math.max(0L, rt.totalMemory() - rt.freeMemory() - memBefore);
		return parseOutputDetailed(TMP_CLOSEGRAPH, "CloseGraph");
	}

	// ============================================================ graph generator

	static String generateGraphDatabase(Random rng) {
		int numGraphs = MIN_GRAPHS + rng.nextInt(MAX_GRAPHS - MIN_GRAPHS + 1);
		StringBuilder sb = new StringBuilder();

		for (int g = 0; g < numGraphs; g++) {
			int nv = MIN_VERTICES + rng.nextInt(MAX_VERTICES - MIN_VERTICES + 1);

			List<Integer> vlabels = new ArrayList<>(nv);
			List<int[]> edges = new ArrayList<>();

			sb.append("t # ").append(g).append("\n");

			for (int v = 0; v < nv; v++) {
				int vl = 10 + rng.nextInt(NUM_VERTEX_LABELS);
				vlabels.add(vl);
				sb.append("v ").append(v).append(" ").append(vl).append("\n");
			}

			boolean[][] inTree = new boolean[nv][nv];

			// 1) spanning tree (guarantee connected)
			if (nv > 1) {
				int[] order = new int[nv];
				for (int i = 0; i < nv; i++)
					order[i] = i;

				for (int i = nv - 1; i > 0; i--) {
					int j = rng.nextInt(i + 1);
					int tmp = order[i];
					order[i] = order[j];
					order[j] = tmp;
				}

				for (int i = 1; i < nv; i++) {
					int u = order[i];
					int w = order[rng.nextInt(i)];

					int a = Math.min(u, w), b = Math.max(u, w);
					int el = 20 + rng.nextInt(NUM_EDGE_LABELS);

					edges.add(new int[] { a, b, el });
					sb.append("e ").append(a).append(" ").append(b).append(" ").append(el).append("\n");
					inTree[a][b] = true;
				}
			}

			// 2) extra edges
			for (int u = 0; u < nv; u++) {
				for (int w = u + 1; w < nv; w++) {
					if (inTree[u][w])
						continue;
					if (rng.nextDouble() < EDGE_PROBABILITY) {
						int el = 20 + rng.nextInt(NUM_EDGE_LABELS);
						edges.add(new int[] { u, w, el });
						sb.append("e ").append(u).append(" ").append(w).append(" ").append(el).append("\n");
					}
				}
			}

			// 3) validate this graph transaction
			validateGraphTransaction(g, nv, edges);

			sb.append("\n");
		}
		return sb.toString();
	}

	static void validateGraphTransaction(int gid, int nv, List<int[]> edges) {
		// Vertex ids check: generator *should* already ensure 0..nv-1, but validate
		// anyway.
		if (nv <= 0) {
			throw new IllegalStateException("Graph " + gid + " has no vertices (nv=" + nv + ")");
		}

		// Edge checks + adjacency for connectivity
		@SuppressWarnings("unchecked")
		List<Integer>[] adj = new List[nv];
		for (int i = 0; i < nv; i++)
			adj[i] = new ArrayList<>();

		// Track duplicates ignoring label: undirected (min,max)
		Set<Long> seenPairs = new TreeSet<>();

		for (int[] e : edges) {
			if (e == null || e.length != 3) {
				throw new IllegalStateException("Graph " + gid + " has a malformed edge record");
			}
			int u = e[0], v = e[1], lab = e[2];

			// bounds
			if (u < 0 || u >= nv || v < 0 || v >= nv) {
				throw new IllegalStateException("Graph " + gid + " has edge out of bounds: (" + u + "," + v + ")");
			}

			// no self-loop
			if (u == v) {
				throw new IllegalStateException("Graph " + gid + " has self-loop on vertex " + u);
			}

			// enforce canonical ordering u < v (nice-to-have, keeps consistency)
			if (u > v) {
				throw new IllegalStateException(
						"Graph " + gid + " has non-canonical edge order: e " + u + " " + v + " " + lab);
			}

			// no duplicate undirected pair (ignoring label)
			long key = (((long) u) << 32) | (v & 0xffffffffL);
			if (!seenPairs.add(key)) {
				throw new IllegalStateException("Graph " + gid + " has duplicate edge between " + u + " and " + v);
			}

			// build adjacency
			adj[u].add(v);
			adj[v].add(u);
		}

		// connectedness check (BFS/DFS from vertex 0)
		boolean[] vis = new boolean[nv];
		int visited = 0;
		int[] stack = new int[nv];
		int top = 0;
		stack[top++] = 0;
		vis[0] = true;

		while (top > 0) {
			int cur = stack[--top];
			visited++;
			for (int nxt : adj[cur]) {
				if (!vis[nxt]) {
					vis[nxt] = true;
					stack[top++] = nxt;
				}
			}
		}

		if (visited != nv) {
			throw new IllegalStateException("Graph " + gid + " is disconnected: visited " + visited + " / " + nv);
		}
	}
	// ============================================================ output parser

	/** All information extracted from a single pattern block in an output file. */
	static class PatternInfo {
		int patternId;
		int support;
		List<Integer> vlabels = new ArrayList<>();
		List<int[]> edges = new ArrayList<>();
		List<Integer> graphIds = new ArrayList<>();
		String signature;
		String rawText;
	}

	/**
	 * Parses the output file produced by a closed-graph mining algorithm. Returns a
	 * map from canonical signature → PatternInfo. Throws if duplicate isomorphic
	 * patterns are found in one file.
	 */
	static Map<String, PatternInfo> parseOutputDetailed(String filename, String algoName) throws IOException {

		Map<String, PatternInfo> result = new LinkedHashMap<>();
		List<String> lines = Files.readAllLines(Paths.get(filename));

		PatternInfo current = null;
		boolean inPattern = false;

		for (String raw : lines) {
			String line = raw.trim();
			if (line.isEmpty())
				continue;
			String[] p = line.split("\\s+");

			if (p[0].equals("t")) {
				if (current != null && inPattern) {
					storePattern(result, current, algoName);
				}
				current = new PatternInfo();
				current.patternId = Integer.parseInt(p[2]);
				current.support = (p.length >= 5) ? Integer.parseInt(p[4]) : 0;
				current.rawText = line + "\n";
				inPattern = true;

			} else if (p[0].equals("v") && inPattern) {
				current.vlabels.add(Integer.parseInt(p[2]));
				current.rawText += line + "\n";

			} else if (p[0].equals("e") && inPattern) {
				current.edges.add(new int[] { Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]) });
				current.rawText += line + "\n";

			} else if (p[0].equals("x") && inPattern) {
				for (int i = 1; i < p.length; i++) {
					current.graphIds.add(Integer.parseInt(p[i]));
				}
				if (current.support == 0)
					current.support = p.length - 1;
				current.rawText += line + "\n";
			}
		}
		if (current != null && inPattern) {
			storePattern(result, current, algoName);
		}
		return result;
	}

	static void storePattern(Map<String, PatternInfo> result, PatternInfo pattern, String algoName) throws IOException {
		pattern.signature = buildSignature(pattern.vlabels, pattern.edges);
		PatternInfo previous = result.putIfAbsent(pattern.signature, pattern);
		if (previous != null) {
			throw new IOException(algoName + " produced duplicate isomorphic patterns: IDs " + previous.patternId
					+ " and " + pattern.patternId + "\n  canonical sig = " + pattern.signature + "\n--- first ---\n"
					+ previous.rawText + "\n--- second ---\n" + pattern.rawText);
		}
	}

	/**
	 * Builds a canonical string signature for a pattern by delegating to AlgoFSG's
	 * canonicalize helper (the same one used by the FSG comparison harness), which
	 * produces a graph-isomorphism–invariant key.
	 */
	static String buildSignature(List<Integer> vlabels, List<int[]> edges) {
		int[] labelArray = new int[vlabels.size()];
		for (int i = 0; i < vlabels.size(); i++)
			labelArray[i] = vlabels.get(i);

		int[][] edgeArray = new int[edges.size()][];
		for (int i = 0; i < edges.size(); i++)
			edgeArray[i] = edges.get(i).clone();

		FSGPattern.CanonResult canonical = FSGPattern.canonicalize(labelArray, edgeArray);
		return Arrays.toString(canonical.key);
	}

	// ============================================================= comparison

	/** Summary of differences between the two output sets. */
	static class ComparisonResult {
		boolean identical = true;
		Set<String> onlyInCGSpan = new TreeSet<>();
		Set<String> onlyInCloseGraph = new TreeSet<>();
		/** sig -> [cgspanSupport, closeGraphSupport] */
		Map<String, int[]> supportMismatch = new LinkedHashMap<>();
	}

	static ComparisonResult compareResults(Map<String, PatternInfo> cgspan, Map<String, PatternInfo> closeGraph) {
		ComparisonResult result = new ComparisonResult();

		Set<String> allSigs = new TreeSet<>();
		allSigs.addAll(cgspan.keySet());
		allSigs.addAll(closeGraph.keySet());

		for (String sig : allSigs) {
			PatternInfo cgInfo = cgspan.get(sig);
			PatternInfo clInfo = closeGraph.get(sig);

			if (cgInfo == null) {
				result.onlyInCloseGraph.add(sig);
				result.identical = false;
			} else if (clInfo == null) {
				result.onlyInCGSpan.add(sig);
				result.identical = false;
			} else if (cgInfo.support != clInfo.support) {
				result.supportMismatch.put(sig, new int[] { cgInfo.support, clInfo.support });
				result.identical = false;
			}
		}
		return result;
	}

	// ======================================================== comparison printer

	static void printDetailedComparison(ComparisonResult cmp, Map<String, PatternInfo> cgspan,
			Map<String, PatternInfo> closeGraph) {
		System.err.println("\n┌──────────────────────────────────────────────────────────────┐");
		System.err.println("│                   DETAILED COMPARISON                        │");
		System.err.println("└──────────────────────────────────────────────────────────────┘");
		System.err.printf("%nPattern counts: CGSpan = %d, CloseGraph = %d%n", cgspan.size(), closeGraph.size());

		// patterns only in CGSpan
		if (!cmp.onlyInCGSpan.isEmpty()) {
			System.err.printf("%n❌ %d pattern(s) found ONLY by CGSpan (missing from CloseGraph):%n",
					cmp.onlyInCGSpan.size());
			printPatternSet(cmp.onlyInCGSpan, cgspan);
		}

		// patterns only in CloseGraph
		if (!cmp.onlyInCloseGraph.isEmpty()) {
			System.err.printf("%n❌ %d pattern(s) found ONLY by CloseGraph (missing from CGSpan):%n",
					cmp.onlyInCloseGraph.size());
			printPatternSet(cmp.onlyInCloseGraph, closeGraph);
		}

		// support mismatches
		if (!cmp.supportMismatch.isEmpty()) {
			System.err.printf("%n❌ %d pattern(s) with SUPPORT MISMATCH:%n", cmp.supportMismatch.size());
			int idx = 0;
			for (Map.Entry<String, int[]> e : cmp.supportMismatch.entrySet()) {
				String sig = e.getKey();
				int cgSup = e.getValue()[0];
				int clSup = e.getValue()[1];
				PatternInfo p = cgspan.get(sig);
				System.err.printf("%n  Pattern #%d:%n", ++idx);
				System.err.printf("    Signature   : %s%n", sig);
				if (p != null) {
					System.err.printf("    Vertex labels: %s%n", p.vlabels);
					System.err.printf("    Edges        : %s%n", edgesToString(p.edges));
				}
				System.err.printf("    CGSpan support     = %d%n", cgSup);
				System.err.printf("    CloseGraph support = %d%n", clSup);
				PatternInfo clInfo = closeGraph.get(sig);
				System.err.printf("    CGSpan    graph ids: %s%n", p != null ? p.graphIds : "n/a");
				System.err.printf("    CloseGraph graph ids: %s%n", clInfo != null ? clInfo.graphIds : "n/a");
			}
		}
	}

	private static void printPatternSet(Set<String> sigs, Map<String, PatternInfo> src) {
		int idx = 0;
		for (String sig : sigs) {
			PatternInfo p = src.get(sig);
			System.err.printf("%n  Pattern #%d (support = %d):%n", ++idx, p.support);
			System.err.printf("    Signature   : %s%n", sig);
			System.err.printf("    Vertex labels: %s%n", p.vlabels);
			System.err.printf("    Edges        : %s%n", edgesToString(p.edges));
			System.err.printf("    Graph ids    : %s%n", p.graphIds);
		}
	}

	static String edgesToString(List<int[]> edges) {
		return edges.stream().map(e -> String.format("(%d-%d:%d)", e[0], e[1], e[2])).collect(Collectors.joining(", "));
	}

	// ======================================================= ranking printers

	static void printPerTestRanking(RunMetrics cgspan, RunMetrics closeGraph) {

		boolean cgspanFasterTime = cgspan.runtimeMs <= closeGraph.runtimeMs;
		boolean cgspanLessMemory = cgspan.peakMemoryBytes <= closeGraph.peakMemoryBytes;

		System.out.println("\n  ┌──────────────── Per-test ranking ──────────────────────┐");
		System.out.printf("  │  ⏱  Time  : CGSpan=%dms  CloseGraph=%dms  (%s faster)%n", cgspan.runtimeMs,
				closeGraph.runtimeMs, cgspanFasterTime ? "CGSpan" : "CloseGraph");
		System.out.printf("  │  🧠 Memory: CGSpan=%s  CloseGraph=%s  (%s leaner)%n",
				formatBytes(cgspan.peakMemoryBytes), formatBytes(closeGraph.peakMemoryBytes),
				cgspanLessMemory ? "CGSpan" : "CloseGraph");
		System.out.println("  └────────────────────────────────────────────────────────┘");
	}

	static void printRunningTotals() {
		if (perfTestsCount == 0)
			return;
		System.out.println("\n  ┌──────────────── Running totals ────────────────────────┐");
		System.out.printf("  │  Tests measured : %d%n", perfTestsCount);
		System.out.printf("  │  CGSpan     total time : %s  avg: %s/test%n", formatMs(cgspanTotalTimeMs),
				formatMs(cgspanTotalTimeMs / perfTestsCount));
		System.out.printf("  │  CloseGraph total time : %s  avg: %s/test%n", formatMs(closeGraphTotalTimeMs),
				formatMs(closeGraphTotalTimeMs / perfTestsCount));
		System.out.printf("  │  CGSpan     total mem  : %s  avg: %s/test%n", formatBytes(cgspanTotalMemBytes),
				formatBytes(cgspanTotalMemBytes / perfTestsCount));
		System.out.printf("  │  CloseGraph total mem  : %s  avg: %s/test%n", formatBytes(closeGraphTotalMemBytes),
				formatBytes(closeGraphTotalMemBytes / perfTestsCount));
		System.out.println("  └────────────────────────────────────────────────────────┘");
	}

	static void printFinalPerformanceRanking() {
		System.out.println();
		System.out.println("╔═══════════════════════════════════════════════════════════════╗");
		System.out.println("║      PERFORMANCE RANKING – CGSpan vs CloseGraph              ║");
		System.out.println("╠═══════════════════════════════════════════════════════════════╣");
		if (perfTestsCount == 0) {
			System.out.println("║  No performance data collected.                               ║");
			System.out.println("╚═══════════════════════════════════════════════════════════════╝");
			return;
		}
		System.out.printf("║  Tests measured: %-44d║%n", perfTestsCount);
		System.out.println("╠═══════════════════════════════════════════════════════════════╣");

		// ── time ────────────────────────────────────────────────────────────
		System.out.println("║  ⏱  RUNTIME                                                   ║");
		System.out.printf("║    CGSpan     total : %-39s║%n", formatMs(cgspanTotalTimeMs));
		System.out.printf("║    CloseGraph total : %-39s║%n", formatMs(closeGraphTotalTimeMs));
		System.out.printf("║    CGSpan     avg   : %-39s║%n", formatMs(cgspanTotalTimeMs / perfTestsCount) + " / test");
		System.out.printf("║    CloseGraph avg   : %-39s║%n",
				formatMs(closeGraphTotalTimeMs / perfTestsCount) + " / test");

		boolean cgspanFasterTotal = cgspanTotalTimeMs <= closeGraphTotalTimeMs;
		String timeFaster = cgspanFasterTotal ? "CGSpan" : "CloseGraph";
		long timeDiff = Math.abs(cgspanTotalTimeMs - closeGraphTotalTimeMs);
		double timePct = pct(timeDiff, cgspanFasterTotal ? closeGraphTotalTimeMs : cgspanTotalTimeMs);
		System.out.printf("║    🏆 Faster (total): %-39s║%n",
				String.format("%s  (+%s = +%.1f%%)", timeFaster, formatMs(timeDiff), timePct));
		System.out.println("╠═══════════════════════════════════════════════════════════════╣");

		// ── memory ──────────────────────────────────────────────────────────
		System.out.println("║  🧠 MEMORY USAGE                                              ║");
		System.out.printf("║    CGSpan     total : %-39s║%n", formatBytes(cgspanTotalMemBytes));
		System.out.printf("║    CloseGraph total : %-39s║%n", formatBytes(closeGraphTotalMemBytes));
		System.out.printf("║    CGSpan     avg   : %-39s║%n",
				formatBytes(cgspanTotalMemBytes / perfTestsCount) + " / test");
		System.out.printf("║    CloseGraph avg   : %-39s║%n",
				formatBytes(closeGraphTotalMemBytes / perfTestsCount) + " / test");

		boolean cgspanLeanerTotal = cgspanTotalMemBytes <= closeGraphTotalMemBytes;
		String memLeaner = cgspanLeanerTotal ? "CGSpan" : "CloseGraph";
		long memDiff = Math.abs(cgspanTotalMemBytes - closeGraphTotalMemBytes);
		double memPct = pct(memDiff, cgspanLeanerTotal ? closeGraphTotalMemBytes : cgspanTotalMemBytes);
		System.out.printf("║    🏆 Leaner  (total): %-39s║%n",
				String.format("%s  (+%s = +%.1f%%)", memLeaner, formatBytes(memDiff), memPct));
		System.out.println("╚═══════════════════════════════════════════════════════════════╝");
	}

	// ================================================================= helpers

	static void printGraphDatabaseSummary(String graphDb) {
		int graphs = 0, vertices = 0, edges = 0;
		for (String line : graphDb.split("\n")) {
			String t = line.trim();
			if (t.startsWith("t #"))
				graphs++;
			else if (t.startsWith("v "))
				vertices++;
			else if (t.startsWith("e "))
				edges++;
		}
		System.out.printf("  Graphs: %d, Vertices: %d, Edges: %d%n", graphs, vertices, edges);
	}

	static void printFullDebugInfo(String graphDb, String cgspanOut, String closeGraphOut) {
		System.err.println("\n┌──────────────────────────────────────────────────────────────┐");
		System.err.println("│                   FULL DEBUG OUTPUT                          │");
		System.err.println("└──────────────────────────────────────────────────────────────┘");
		System.err.println("\n▼▼▼ INPUT GRAPH DATABASE ▼▼▼");
		System.err.println(graphDb);
		System.err.println("\n▼▼▼ CGSPAN OUTPUT ▼▼▼");
		System.err.println(cgspanOut);
		System.err.println("\n▼▼▼ CLOSEGRAPH OUTPUT ▼▼▼");
		System.err.println(closeGraphOut);
	}

	static void printDebugFooter(int testIdx, long seed) {
		System.err.println("\n┌──────────────────────────────────────────────────────────────┐");
		System.err.println("│                   DEBUG INFORMATION                          │");
		System.err.println("└──────────────────────────────────────────────────────────────┘");
		System.err.printf("Test index : %d%n", testIdx);
		System.err.printf("Seed       : %d%n", seed);
		System.err.printf("To reproduce: use seed %d%n", seed);
		System.err.printf("Artefacts saved to: %s%n", FAIL_DIR);
	}

	static void printException(Exception e) {
		System.err.println("Exception : " + e.getClass().getName());
		System.err.println("Message   : " + e.getMessage());
		e.printStackTrace(System.err);
	}

	// ─── formatting ─────────────────────────────────────────────────────────

	static double pct(long part, long total) {
		return total == 0 ? 0.0 : 100.0 * part / total;
	}

	static String formatBytes(long bytes) {
		if (bytes < 0)
			return "0 B";
		if (bytes < 1_024L)
			return bytes + " B";
		if (bytes < 1_048_576L)
			return String.format("%.2f KB", bytes / 1_024.0);
		if (bytes < 1_073_741_824L)
			return String.format("%.2f MB", bytes / 1_048_576.0);
		return String.format("%.2f GB", bytes / 1_073_741_824.0);
	}

	static String formatMs(long ms) {
		if (ms < 1_000)
			return ms + " ms";
		if (ms < 60_000)
			return String.format("%.2f s", ms / 1_000.0);
		return String.format("%.2f min", ms / 60_000.0);
	}

	// ─── I/O ────────────────────────────────────────────────────────────────

	static void writeFile(String path, String content) throws IOException {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
			bw.write(content);
		}
	}

	static String readFile(String path) {
		try {
			return new String(Files.readAllBytes(Paths.get(path)));
		} catch (IOException e) {
			return "(file not found: " + e.getMessage() + ")";
		}
	}

	/**
	 * Saves failure artefacts (input, both outputs, a summary) to FAIL_DIR.
	 */
	static void saveFailure(int testIdx, long seed, String input, String cgspanOut, String closeGraphOut,
			String reason) {
		try {
			String base = FAIL_DIR + File.separator + "test_" + testIdx + "_seed_" + seed;
			writeFile(base + "_input.txt",
					String.format("# Test: %d%n# Seed: %d%n# Reason: %s%n" + "# MinSupport: %.2f%n%n%s", testIdx, seed,
							reason, MIN_SUPPORT, input));
			writeFile(base + "_cgspan.txt", cgspanOut);
			writeFile(base + "_closegraph.txt", closeGraphOut);
			writeFile(base + "_summary.txt",
					String.format("Test %d failed (seed %d)%nReason: %s%n", testIdx, seed, reason));
		} catch (IOException ex) {
			System.err.println("⚠ Could not save failure files: " + ex.getMessage());
		}
	}
}