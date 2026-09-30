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
import ca.pfv.spmf.algorithms.graph_mining.fsg.AlgoFSG;
import ca.pfv.spmf.algorithms.graph_mining.fsg.FSGPattern;

/**
 * Automated regression harness: CloseGraph vs brute-force oracle.
 *
 * CGSpan is excluded from this harness because it is known to be buggy.
 * CloseGraph is tested in isolation against the independent
 * {@link ClosedPatternOracle}, which enumerates every connected subgraph of the
 * database and checks the closed-pattern definition directly.
 *
 * The harness: 1. Generates a random labelled graph database. 2. Runs
 * CloseGraph on it. 3. Asks the oracle for the ground-truth set of closed
 * frequent subgraphs. 4. Compares the two sets and reports every discrepancy in
 * full detail. 5. Accumulates wall-clock time and heap-delta metrics across all
 * tests and prints a final summary.
 *
 * Stopping behaviour: the harness stops at the first failure so that the
 * developer can inspect the generated database and both outputs immediately.
 * Comment out the {@code break} at the bottom of main() to continue through all
 * tests instead.
 */
public class MainTestCloseGraphVsOracle {

	// ---------------------------------------------------------------- config

	/**
	 * Whether single-vertex patterns are included in CloseGraph's output. Must
	 * match the flag passed to {@code algo.runAlgorithm(...)}.
	 */
	private static final boolean OUTPUT_SINGLE_VERTICES = false;

	private static final int NUM_TESTS = 1000;
	private static final double MIN_SUPPORT = 0.5; // relative support
	private static final int MIN_GRAPHS = 3;
	private static final int MAX_GRAPHS = 5; // keep tests fast
	private static final int MIN_VERTICES = 2;
	private static final int MAX_VERTICES = 8;
	private static final double EDGE_PROBABILITY = 0.4;
	private static final int NUM_VERTEX_LABELS = 3;
	private static final int NUM_EDGE_LABELS = 2;
	private static final long BASE_SEED = 20240101L;

	// temp files
	private static final String TMP_INPUT = System.getProperty("java.io.tmpdir") + File.separator
			+ "cg_oracle_input.txt";
	private static final String TMP_CLOSEGRAPH = System.getProperty("java.io.tmpdir") + File.separator
			+ "cg_oracle_out_closegraph.txt";

	// failure artefact directory
	private static final String FAIL_DIR = System.getProperty("java.io.tmpdir") + File.separator + "cg_oracle_failures";

	// --------------------------------------------------------- perf accumulators

	private static long closeGraphTotalTimeMs = 0L;
	private static long oracleTotalTimeMs = 0L;
	private static long closeGraphTotalMemBytes = 0L;
	private static long oracleTotalMemBytes = 0L;
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

			// 2. run CloseGraph
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
				saveFailure(t, seed, graphDb, "", "CLOSEGRAPH_EXCEPTION");
				printDebugFooter(t, seed);
				continue;
			}

			// 3. run oracle
			RunMetrics oracleMetrics = new RunMetrics();
			Map<String, PatternInfo> oracleResult;
			try {
				System.out.println("\n⏳ Running brute-force oracle...");
				oracleResult = runOracle(oracleMetrics);
				System.out.printf("✓ Oracle completed in %d ms  |  peak memory: %s%n", oracleMetrics.runtimeMs,
						formatBytes(oracleMetrics.peakMemoryBytes));
				System.out.printf("  Oracle found %d closed patterns%n", oracleResult.size());
			} catch (Exception e) {
				failed++;
				System.err.println("\n❌ Oracle CRASHED!");
				printException(e);
				saveFailure(t, seed, graphDb, readFile(TMP_CLOSEGRAPH), "ORACLE_EXCEPTION");
				printDebugFooter(t, seed);
				continue;
			}

			// accumulate perf
			closeGraphTotalTimeMs += closeGraphMetrics.runtimeMs;
			oracleTotalTimeMs += oracleMetrics.runtimeMs;
			closeGraphTotalMemBytes += closeGraphMetrics.peakMemoryBytes;
			oracleTotalMemBytes += oracleMetrics.peakMemoryBytes;
			perfTestsCount++;

			printPerTestRanking(closeGraphMetrics, oracleMetrics);

			// 4. compare
			ComparisonResult cmp = compareResults(closeGraphResult, oracleResult);

			if (cmp.identical) {
				passed++;
				System.out.println("\n✅ PASS – CloseGraph output matches oracle exactly");
				if ((t + 1) % 50 == 0) {
					System.out.printf("%n🎯 Progress: %d / %d tests passed so far%n", passed, t + 1);
					printRunningTotals();
				}
			} else {
				failed++;
				System.err.println("\n❌ FAIL – CloseGraph output differs from oracle!");
				printDetailedComparison(cmp, closeGraphResult, oracleResult);

				String clOut = readFile(TMP_CLOSEGRAPH);
				saveFailure(t, seed, graphDb, clOut, "MISMATCH");
				printFullDebugInfo(graphDb, clOut);
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

	// ================================================================== runners

	/** Wall-clock time (ms) and heap-delta (bytes) for one algorithm run. */
	static class RunMetrics {
		long runtimeMs;
		long peakMemoryBytes;
	}

	// ── CloseGraph ───────────────────────────────────────────────────────────

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

	// ── Oracle ───────────────────────────────────────────────────────────────

	/**
	 * Runs the brute-force {@link ClosedPatternOracle} and converts its output into
	 * the same {@code Map<signature, PatternInfo>} format used by the CloseGraph
	 * parser so that the two maps can be compared directly.
	 */
	static Map<String, PatternInfo> runOracle(RunMetrics m) throws Exception {
		Runtime rt = Runtime.getRuntime();
		rt.gc();
		rt.gc();
		long memBefore = rt.totalMemory() - rt.freeMemory();
		long start = System.currentTimeMillis();

		List<ClosedPatternOracle.SimpleGraph> db = ClosedPatternOracle.readGraphs(TMP_INPUT);

		// Compute absolute minimum support from relative threshold
		int minSupAbs = (int) Math.ceil(MIN_SUPPORT * db.size());

		List<ClosedPatternOracle.Pattern> closedPatterns = ClosedPatternOracle.mineClosedPatterns(db, minSupAbs,
				OUTPUT_SINGLE_VERTICES);

		m.runtimeMs = System.currentTimeMillis() - start;
		m.peakMemoryBytes = Math.max(0L, rt.totalMemory() - rt.freeMemory() - memBefore);

		// Convert to PatternInfo map
		Map<String, PatternInfo> result = new LinkedHashMap<>();
		int id = 0;
		for (ClosedPatternOracle.Pattern p : closedPatterns) {
			PatternInfo pi = oraclePatternToInfo(p, id++);
			PatternInfo prev = result.putIfAbsent(pi.signature, pi);
			if (prev != null) {
				throw new IOException("Oracle produced duplicate isomorphic patterns: IDs " + prev.patternId + " and "
						+ pi.patternId + "\n  canonical sig = " + pi.signature);
			}
		}
		return result;
	}

	/**
	 * Converts a {@link ClosedPatternOracle.Pattern} (which already carries its
	 * support-graph-id set) into a {@link PatternInfo} ready for comparison.
	 */
	private static PatternInfo oraclePatternToInfo(ClosedPatternOracle.Pattern p, int id) {

		PatternInfo pi = new PatternInfo();
		pi.patternId = id;

		// vertex labels
		for (int vl : p.vLabels) {
			pi.vlabels.add(vl);
		}

		// edges (oracle stores them as int[3]: {src, dst, label})
		for (int[] e : p.edges) {
			pi.edges.add(e.clone());
		}

		// graph-id list (oracle stores them in a Set<Integer>)
		pi.graphIds.addAll(p.graphIds);
		pi.support = pi.graphIds.size();

		pi.signature = buildSignature(pi.vlabels, pi.edges);
		pi.rawText = "oracle#" + id;
		return pi;
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
	 * Parses the output file produced by CloseGraph. Returns a map from canonical
	 * signature → PatternInfo. Throws if duplicate isomorphic patterns are found.
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
	 * Builds a canonical string signature for a pattern by delegating to
	 * {@link AlgoFSG#canonicalize}, which produces a graph-isomorphism– invariant
	 * key.
	 */
	static String buildSignature(List<Integer> vlabels, List<int[]> edges) {
		int[] labelArray = new int[vlabels.size()];
		for (int i = 0; i < vlabels.size(); i++) {
			labelArray[i] = vlabels.get(i);
		}
		int[][] edgeArray = new int[edges.size()][];
		for (int i = 0; i < edges.size(); i++) {
			edgeArray[i] = edges.get(i).clone();
		}
		FSGPattern.CanonResult canonical = FSGPattern.canonicalize(labelArray, edgeArray);
		return Arrays.toString(canonical.key);
	}

	// ============================================================= comparison

	/** Summary of differences between CloseGraph and the oracle. */
	static class ComparisonResult {
		boolean identical = true;
		/** Patterns CloseGraph reported but the oracle says are NOT closed. */
		Set<String> onlyInCloseGraph = new TreeSet<>();
		/** Patterns the oracle found but CloseGraph missed. */
		Set<String> onlyInOracle = new TreeSet<>();
		/** Patterns both found but with different support counts. */
		Map<String, int[]> supportMismatch = new LinkedHashMap<>();
	}

	static ComparisonResult compareResults(Map<String, PatternInfo> closeGraph, Map<String, PatternInfo> oracle) {

		ComparisonResult result = new ComparisonResult();

		Set<String> allSigs = new TreeSet<>();
		allSigs.addAll(closeGraph.keySet());
		allSigs.addAll(oracle.keySet());

		for (String sig : allSigs) {
			PatternInfo clInfo = closeGraph.get(sig);
			PatternInfo orInfo = oracle.get(sig);

			if (orInfo == null) {
				// CloseGraph emitted a pattern the oracle does not recognise
				result.onlyInCloseGraph.add(sig);
				result.identical = false;
			} else if (clInfo == null) {
				// Oracle found a closed pattern that CloseGraph missed
				result.onlyInOracle.add(sig);
				result.identical = false;
			} else if (clInfo.support != orInfo.support) {
				result.supportMismatch.put(sig, new int[] { clInfo.support, orInfo.support });
				result.identical = false;
			}
		}
		return result;
	}

	// ======================================================== comparison printer

	static void printDetailedComparison(ComparisonResult cmp, Map<String, PatternInfo> closeGraph,
			Map<String, PatternInfo> oracle) {
		System.err.println("\n┌──────────────────────────────────────────────────────────────┐");
		System.err.println("│                   DETAILED COMPARISON                        │");
		System.err.println("└──────────────────────────────────────────────────────────────┘");
		System.err.printf("%nPattern counts: CloseGraph = %d, Oracle = %d%n", closeGraph.size(), oracle.size());

		// patterns emitted by CloseGraph that are not in the oracle (false positives)
		if (!cmp.onlyInCloseGraph.isEmpty()) {
			System.err.printf(
					"%n❌ %d FALSE-POSITIVE pattern(s): CloseGraph reported these "
							+ "but the oracle says they are NOT closed (or not frequent):%n",
					cmp.onlyInCloseGraph.size());
			printPatternSet(cmp.onlyInCloseGraph, closeGraph, "CloseGraph");
		}

		// patterns the oracle found that CloseGraph missed (false negatives)
		if (!cmp.onlyInOracle.isEmpty()) {
			System.err.printf("%n❌ %d FALSE-NEGATIVE pattern(s): Oracle found these closed "
					+ "patterns but CloseGraph missed them:%n", cmp.onlyInOracle.size());
			printPatternSet(cmp.onlyInOracle, oracle, "Oracle");
		}

		// support mismatches
		if (!cmp.supportMismatch.isEmpty()) {
			System.err.printf("%n❌ %d pattern(s) with SUPPORT MISMATCH:%n", cmp.supportMismatch.size());
			int idx = 0;
			for (Map.Entry<String, int[]> e : cmp.supportMismatch.entrySet()) {
				String sig = e.getKey();
				int clSup = e.getValue()[0];
				int orSup = e.getValue()[1];
				PatternInfo clInfo = closeGraph.get(sig);
				PatternInfo orInfo = oracle.get(sig);
				System.err.printf("%n  Pattern #%d:%n", ++idx);
				System.err.printf("    Signature     : %s%n", sig);
				if (clInfo != null) {
					System.err.printf("    Vertex labels : %s%n", clInfo.vlabels);
					System.err.printf("    Edges         : %s%n", edgesToString(clInfo.edges));
				}
				System.err.printf("    CloseGraph support = %d  graph-ids: %s%n", clSup,
						clInfo != null ? clInfo.graphIds : "n/a");
				System.err.printf("    Oracle     support = %d  graph-ids: %s%n", orSup,
						orInfo != null ? orInfo.graphIds : "n/a");
			}
		}
	}

	private static void printPatternSet(Set<String> sigs, Map<String, PatternInfo> src, String sourceName) {
		int idx = 0;
		for (String sig : sigs) {
			PatternInfo p = src.get(sig);
			System.err.printf("%n  Pattern #%d [%s]  (support = %d):%n", ++idx, sourceName, p.support);
			System.err.printf("    Signature     : %s%n", sig);
			System.err.printf("    Vertex labels : %s%n", p.vlabels);
			System.err.printf("    Edges         : %s%n", edgesToString(p.edges));
			System.err.printf("    Graph ids     : %s%n", p.graphIds);
		}
	}

	static String edgesToString(List<int[]> edges) {
		return edges.stream().map(e -> String.format("(%d-%d:%d)", e[0], e[1], e[2])).collect(Collectors.joining(", "));
	}

	// ======================================================= ranking printers

	static void printPerTestRanking(RunMetrics cg, RunMetrics oracle) {
		boolean cgFasterTime = cg.runtimeMs <= oracle.runtimeMs;
		boolean cgLessMemory = cg.peakMemoryBytes <= oracle.peakMemoryBytes;

		System.out.println("\n  ┌──────────────── Per-test ranking ──────────────────────┐");
		System.out.printf("  │  ⏱  Time  : CloseGraph=%dms  Oracle=%dms  (%s faster)%n", cg.runtimeMs, oracle.runtimeMs,
				cgFasterTime ? "CloseGraph" : "Oracle");
		System.out.printf("  │  🧠 Memory: CloseGraph=%s  Oracle=%s  (%s leaner)%n", formatBytes(cg.peakMemoryBytes),
				formatBytes(oracle.peakMemoryBytes), cgLessMemory ? "CloseGraph" : "Oracle");
		System.out.println("  └────────────────────────────────────────────────────────┘");
	}

	static void printRunningTotals() {
		if (perfTestsCount == 0)
			return;
		System.out.println("\n  ┌──────────────── Running totals ────────────────────────┐");
		System.out.printf("  │  Tests measured   : %d%n", perfTestsCount);
		System.out.printf("  │  CloseGraph total time : %s  avg: %s/test%n", formatMs(closeGraphTotalTimeMs),
				formatMs(closeGraphTotalTimeMs / perfTestsCount));
		System.out.printf("  │  Oracle     total time : %s  avg: %s/test%n", formatMs(oracleTotalTimeMs),
				formatMs(oracleTotalTimeMs / perfTestsCount));
		System.out.printf("  │  CloseGraph total mem  : %s  avg: %s/test%n", formatBytes(closeGraphTotalMemBytes),
				formatBytes(closeGraphTotalMemBytes / perfTestsCount));
		System.out.printf("  │  Oracle     total mem  : %s  avg: %s/test%n", formatBytes(oracleTotalMemBytes),
				formatBytes(oracleTotalMemBytes / perfTestsCount));
		System.out.println("  └────────────────────────────────────────────────────────┘");
	}

	static void printFinalPerformanceRanking() {
		System.out.println();
		System.out.println("╔═══════════════════════════════════════════════════════════════╗");
		System.out.println("║      PERFORMANCE RANKING – CloseGraph vs Oracle              ║");
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
		System.out.printf("║    CloseGraph total : %-39s║%n", formatMs(closeGraphTotalTimeMs));
		System.out.printf("║    Oracle     total : %-39s║%n", formatMs(oracleTotalTimeMs));
		System.out.printf("║    CloseGraph avg   : %-39s║%n",
				formatMs(closeGraphTotalTimeMs / perfTestsCount) + " / test");
		System.out.printf("║    Oracle     avg   : %-39s║%n", formatMs(oracleTotalTimeMs / perfTestsCount) + " / test");

		boolean cgFasterTotal = closeGraphTotalTimeMs <= oracleTotalTimeMs;
		String timeFaster = cgFasterTotal ? "CloseGraph" : "Oracle";
		long timeDiff = Math.abs(closeGraphTotalTimeMs - oracleTotalTimeMs);
		double timePct = pct(timeDiff, cgFasterTotal ? oracleTotalTimeMs : closeGraphTotalTimeMs);
		System.out.printf("║    🏆 Faster (total): %-39s║%n",
				String.format("%s  (+%s = +%.1f%%)", timeFaster, formatMs(timeDiff), timePct));
		System.out.println("╠═══════════════════════════════════════════════════════════════╣");

		// ── memory ──────────────────────────────────────────────────────────
		System.out.println("║  🧠 MEMORY USAGE                                              ║");
		System.out.printf("║    CloseGraph total : %-39s║%n", formatBytes(closeGraphTotalMemBytes));
		System.out.printf("║    Oracle     total : %-39s║%n", formatBytes(oracleTotalMemBytes));
		System.out.printf("║    CloseGraph avg   : %-39s║%n",
				formatBytes(closeGraphTotalMemBytes / perfTestsCount) + " / test");
		System.out.printf("║    Oracle     avg   : %-39s║%n",
				formatBytes(oracleTotalMemBytes / perfTestsCount) + " / test");

		boolean cgLeanerTotal = closeGraphTotalMemBytes <= oracleTotalMemBytes;
		String memLeaner = cgLeanerTotal ? "CloseGraph" : "Oracle";
		long memDiff = Math.abs(closeGraphTotalMemBytes - oracleTotalMemBytes);
		double memPct = pct(memDiff, cgLeanerTotal ? oracleTotalMemBytes : closeGraphTotalMemBytes);
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

	static void printFullDebugInfo(String graphDb, String closeGraphOut) {
		System.err.println("\n┌──────────────────────────────────────────────────────────────┐");
		System.err.println("│                   FULL DEBUG OUTPUT                          │");
		System.err.println("└──────────────────────────────────────────────────────────────┘");
		System.err.println("\n▼▼▼ INPUT GRAPH DATABASE ▼▼▼");
		System.err.println(graphDb);
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
	 * Saves failure artefacts (input, CloseGraph output, summary) to
	 * {@link #FAIL_DIR}.
	 */
	static void saveFailure(int testIdx, long seed, String input, String closeGraphOut, String reason) {
		try {
			String base = FAIL_DIR + File.separator + "test_" + testIdx + "_seed_" + seed;
			writeFile(base + "_input.txt",
					String.format("# Test: %d%n# Seed: %d%n# Reason: %s%n" + "# MinSupport: %.2f%n%n%s", testIdx, seed,
							reason, MIN_SUPPORT, input));
			writeFile(base + "_closegraph.txt", closeGraphOut);
			writeFile(base + "_summary.txt",
					String.format("Test %d failed (seed %d)%nReason: %s%n", testIdx, seed, reason));
		} catch (IOException ex) {
			System.err.println("⚠ Could not save failure files: " + ex.getMessage());
		}
	}
}