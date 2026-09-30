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
import ca.pfv.spmf.algorithms.graph_mining.tkg.AlgoGSPAN;
import ca.pfv.spmf.algorithms.graph_mining.tkg.cfgm.AlgoCFGM;

/**
 * Automated regression harness: CloseGraph vs GSpan+post-processing vs CFGM,
 * arbitrated by a brute-force closed-pattern oracle.
 *
 * <p>
 * When all three algorithms agree the test passes immediately. When they
 * disagree the oracle ({@link ClosedPatternOracle}) is consulted to determine
 * which algorithm(s) are wrong:
 * <ul>
 * <li>A pattern present in an algorithm output but absent from the oracle is a
 * false-positive for that algorithm.</li>
 * <li>A pattern present in the oracle but absent from an algorithm output is a
 * false-negative for that algorithm.</li>
 * <li>A support mismatch is adjudicated by the oracle's support value.</li>
 * </ul>
 *
 * The harness: 1. Generates a random labelled graph database. 2. Runs
 * CloseGraph, GSpan+post, and CFGM on it. 3. If the outputs differ, runs the
 * oracle to find the ground truth. 4. Reports every discrepancy with a verdict
 * (which algorithm is at fault). 5. Accumulates wall-clock time and heap-delta
 * metrics and prints a final performance summary.
 *
 * Stopping behaviour: stops at the first failure.
 */
public class MainTestCloseGraphVsGSpanVsCFGM {

	// ---------------------------------------------------------------- config

	/**
	 * Whether single-vertex patterns are included in the output. Must match the
	 * flag passed to all algorithms.
	 */
	private static final boolean OUTPUT_SINGLE_VERTICES = false;

	private static final int NUM_TESTS = 100;
	private static final double MIN_SUPPORT = 0.3; // relative support
	private static final int MIN_GRAPHS = 3000;
	private static final int MAX_GRAPHS = 5000;
	private static final int MIN_VERTICES = 8;
	private static final int MAX_VERTICES = 20;
	private static final double EDGE_PROBABILITY = 0.4;
	private static final int NUM_VERTEX_LABELS = 20;
	private static final int NUM_EDGE_LABELS = 20;
	private static final long BASE_SEED = 20240101L;

	// temp files
	private static final String TMP_INPUT = System.getProperty("java.io.tmpdir") + File.separator
			+ "cg_gspan_input.txt";
	private static final String TMP_CLOSEGRAPH = System.getProperty("java.io.tmpdir") + File.separator
			+ "cg_gspan_out_closegraph.txt";
	private static final String TMP_GSPAN = System.getProperty("java.io.tmpdir") + File.separator
			+ "cg_gspan_out_gspan.txt";
	private static final String TMP_CFGM = System.getProperty("java.io.tmpdir") + File.separator
			+ "cg_gspan_out_cfgm.txt";

	// failure artefact directory
	private static final String FAIL_DIR = System.getProperty("java.io.tmpdir") + File.separator + "cg_gspan_failures";

	// --------------------------------------------------------- perf accumulators

	private static long closeGraphTotalTimeMs = 0L;
	private static long gspanTotalTimeMs = 0L;
	private static long cfgmTotalTimeMs = 0L;
	private static long oracleTotalTimeMs = 0L;
	private static long closeGraphTotalMemBytes = 0L;
	private static long gspanTotalMemBytes = 0L;
	private static long cfgmTotalMemBytes = 0L;
	private static long oracleTotalMemBytes = 0L;
	private static int perfTestsCount = 0;

	// ── fault counters ───────────────────────────────────────────────────────
	private static int closeGraphFaultCount = 0;
	private static int gspanFaultCount = 0;
	private static int cfgmFaultCount = 0;
	private static int bothFaultCount = 0; // all three wrong on same test

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
				saveFailure(t, seed, graphDb, "", "", "", "", "CLOSEGRAPH_EXCEPTION");
				printDebugFooter(t, seed);
				continue;
			}

			// 3. run GSpan + post-processing
			RunMetrics gspanMetrics = new RunMetrics();
			Map<String, PatternInfo> gspanResult;
			try {
				System.out.println("\n⏳ Running GSpan + post-processing...");
				gspanResult = runGSpanWithPostProcessing(gspanMetrics);
				System.out.printf("✓ GSpan+post completed in %d ms  |  peak memory: %s%n", gspanMetrics.runtimeMs,
						formatBytes(gspanMetrics.peakMemoryBytes));
				System.out.printf("  GSpan+post found %d closed patterns%n", gspanResult.size());
			} catch (Exception e) {
				failed++;
				System.err.println("\n❌ GSpan+post CRASHED!");
				printException(e);
				saveFailure(t, seed, graphDb, readFile(TMP_CLOSEGRAPH), "", "", "", "GSPAN_EXCEPTION");
				printDebugFooter(t, seed);
				continue;
			}

			// 4. run CFGM
			RunMetrics cfgmMetrics = new RunMetrics();
			Map<String, PatternInfo> cfgmResult;
			try {
				System.out.println("\n⏳ Running CFGM...");
				cfgmResult = runCFGM(cfgmMetrics);
				System.out.printf("✓ CFGM completed in %d ms  |  peak memory: %s%n", cfgmMetrics.runtimeMs,
						formatBytes(cfgmMetrics.peakMemoryBytes));
				System.out.printf("  CFGM found %d closed patterns%n", cfgmResult.size());
			} catch (Exception e) {
				failed++;
				System.err.println("\n❌ CFGM CRASHED!");
				printException(e);
				saveFailure(t, seed, graphDb, readFile(TMP_CLOSEGRAPH), readFile(TMP_GSPAN), "", "", "CFGM_EXCEPTION");
				printDebugFooter(t, seed);
				continue;
			}

			// accumulate perf (all three always run)
			closeGraphTotalTimeMs += closeGraphMetrics.runtimeMs;
			gspanTotalTimeMs += gspanMetrics.runtimeMs;
			cfgmTotalTimeMs += cfgmMetrics.runtimeMs;
			closeGraphTotalMemBytes += closeGraphMetrics.peakMemoryBytes;
			gspanTotalMemBytes += gspanMetrics.peakMemoryBytes;
			cfgmTotalMemBytes += cfgmMetrics.peakMemoryBytes;
			perfTestsCount++;

			printPerTestRanking(closeGraphMetrics, gspanMetrics, cfgmMetrics);

			// 5. primary comparison – all three must agree
			ThreeWayComparisonResult cmp = compareThreeWay(closeGraphResult, gspanResult, cfgmResult);

			if (cmp.identical) {
				passed++;
				System.out.println("\n✅ PASS – CloseGraph, GSpan+post, and CFGM all agree exactly");
				if ((t + 1) % 50 == 0) {
					System.out.printf("%n🎯 Progress: %d / %d tests passed so far%n", passed, t + 1);
					printRunningTotals();
				}
				continue; // no need to run oracle
			}

			// 6. mismatch detected – consult the oracle
			failed++;
			System.err.println("\n⚠ Algorithms disagree – consulting oracle...");

			RunMetrics oracleMetrics = new RunMetrics();
			Map<String, PatternInfo> oracleResult;
			try {
				oracleResult = runOracle(oracleMetrics);
				oracleTotalTimeMs += oracleMetrics.runtimeMs;
				oracleTotalMemBytes += oracleMetrics.peakMemoryBytes;
				System.out.printf("✓ Oracle completed in %d ms  |  peak memory: %s%n", oracleMetrics.runtimeMs,
						formatBytes(oracleMetrics.peakMemoryBytes));
				System.out.printf("  Oracle found %d closed patterns%n", oracleResult.size());
			} catch (Exception e) {
				System.err.println("\n❌ Oracle CRASHED – cannot arbitrate!");
				printException(e);
				saveFailure(t, seed, graphDb, readFile(TMP_CLOSEGRAPH), readFile(TMP_GSPAN), readFile(TMP_CFGM), "",
						"ORACLE_EXCEPTION");
				printDebugFooter(t, seed);
				break;
			}

			// 7. arbitrate: compare all algorithms against the oracle
			ThreeWayVerdict verdict = arbitrateThreeWay(cmp, closeGraphResult, gspanResult, cfgmResult, oracleResult);

			System.err.println("\n❌ FAIL – mismatch detected!");
			printArbitratedComparison(verdict, closeGraphResult, gspanResult, cfgmResult, oracleResult);

			if (verdict.closeGraphIsWrong)
				closeGraphFaultCount++;
			if (verdict.gspanIsWrong)
				gspanFaultCount++;
			if (verdict.cfgmIsWrong)
				cfgmFaultCount++;
			if (verdict.closeGraphIsWrong && verdict.gspanIsWrong && verdict.cfgmIsWrong)
				bothFaultCount++;

			String clOut = readFile(TMP_CLOSEGRAPH);
			String gspanOut = readFile(TMP_GSPAN);
			String cfgmOut = readFile(TMP_CFGM);
			saveFailure(t, seed, graphDb, clOut, gspanOut, cfgmOut, oracleResultToString(oracleResult), "MISMATCH");
			printFullDebugInfo(graphDb, clOut, gspanOut, cfgmOut);
			printDebugFooter(t, seed);

			System.err.println("\n⛔ STOPPING AT FIRST FAILURE FOR DEBUGGING.");
			break;
		}

		// ── final summary ────────────────────────────────────────────────────
		System.out.println("\n");
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.println("                       FINAL RESULTS                       ");
		System.out.println("═══════════════════════════════════════════════════════════");
		System.out.printf(" Passed : %d / %d%n", passed, NUM_TESTS);
		System.out.printf(" Failed : %d / %d%n", failed, NUM_TESTS);
		System.out.println("═══════════════════════════════════════════════════════════");
		printFaultSummary();
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

	// ── GSpan + post-processing ──────────────────────────────────────────────

	/**
	 * Runs GSpan to enumerate all frequent subgraphs, then applies a
	 * post-processing filter that retains only closed patterns.
	 *
	 * <p>
	 * A pattern P is <em>closed</em> if there is no other frequent pattern P' in
	 * the GSpan output such that:
	 * <ol>
	 * <li>P' has strictly more edges than P (i.e. P is a proper subgraph of P'),
	 * and</li>
	 * <li>support(P') == support(P).</li>
	 * </ol>
	 */
	static Map<String, PatternInfo> runGSpanWithPostProcessing(RunMetrics m) throws Exception {

		Runtime rt = Runtime.getRuntime();
		rt.gc();
		rt.gc();
		long memBefore = rt.totalMemory() - rt.freeMemory();
		long start = System.currentTimeMillis();

		AlgoGSPAN gspan = new AlgoGSPAN();
		gspan.runAlgorithm(TMP_INPUT, TMP_GSPAN, MIN_SUPPORT, OUTPUT_SINGLE_VERTICES, false, Integer.MAX_VALUE, true);

		Map<String, PatternInfo> allFrequent = parseOutputDetailed(TMP_GSPAN, "GSpan");

		Map<String, PatternInfo> closed = filterClosed(new ArrayList<>(allFrequent.values()));

		m.runtimeMs = System.currentTimeMillis() - start;
		m.peakMemoryBytes = Math.max(0L, rt.totalMemory() - rt.freeMemory() - memBefore);
		return closed;
	}

	// ── CFGM ────────────────────────────────────────────────────────────────

	/**
	 * Runs the CFGM closed frequent graph mining algorithm directly.
	 */
	static Map<String, PatternInfo> runCFGM(RunMetrics m) throws Exception {
		Runtime rt = Runtime.getRuntime();
		rt.gc();
		rt.gc();
		long memBefore = rt.totalMemory() - rt.freeMemory();
		long start = System.currentTimeMillis();

		AlgoCFGM algo = new AlgoCFGM();
		algo.runAlgorithm(TMP_INPUT, TMP_CFGM, MIN_SUPPORT, OUTPUT_SINGLE_VERTICES, false, Integer.MAX_VALUE, true);

		m.runtimeMs = System.currentTimeMillis() - start;
		m.peakMemoryBytes = Math.max(0L, rt.totalMemory() - rt.freeMemory() - memBefore);
		return parseOutputDetailed(TMP_CFGM, "CFGM");
	}

	// ── Oracle ───────────────────────────────────────────────────────────────

	/**
	 * Runs the brute-force {@link ClosedPatternOracle} and converts its output into
	 * the same {@code Map<signature, PatternInfo>} format used by the other
	 * parsers.
	 */
	static Map<String, PatternInfo> runOracle(RunMetrics m) throws Exception {
		Runtime rt = Runtime.getRuntime();
		rt.gc();
		rt.gc();
		long memBefore = rt.totalMemory() - rt.freeMemory();
		long start = System.currentTimeMillis();

		List<ClosedPatternOracle.SimpleGraph> db = ClosedPatternOracle.readGraphs(TMP_INPUT);

		int minSupAbs = (int) Math.ceil(MIN_SUPPORT * db.size());

		List<ClosedPatternOracle.Pattern> closedPatterns = ClosedPatternOracle.mineClosedPatterns(db, minSupAbs,
				OUTPUT_SINGLE_VERTICES);

		m.runtimeMs = System.currentTimeMillis() - start;
		m.peakMemoryBytes = Math.max(0L, rt.totalMemory() - rt.freeMemory() - memBefore);

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
	 * Converts a {@link ClosedPatternOracle.Pattern} into a {@link PatternInfo}
	 * ready for comparison.
	 */
	private static PatternInfo oraclePatternToInfo(ClosedPatternOracle.Pattern p, int id) {

		PatternInfo pi = new PatternInfo();
		pi.patternId = id;
		for (int vl : p.vLabels)
			pi.vlabels.add(vl);
		for (int[] e : p.edges)
			pi.edges.add(e.clone());
		pi.graphIds.addAll(p.graphIds);
		pi.support = pi.graphIds.size();
		pi.signature = buildSignature(pi.vlabels, pi.edges);
		pi.rawText = "oracle#" + id;
		return pi;
	}

	// ── closed-pattern filter (post-processing for GSpan) ───────────────────

	/**
	 * Filters a list of frequent patterns (GSpan output) to retain only the closed
	 * ones.
	 *
	 * <p>
	 * Algorithm (O(n²) pairs):
	 * <ol>
	 * <li>Sort by edge count ascending.</li>
	 * <li>For every pattern P, if any pattern P' with strictly more edges has the
	 * same support AND the same graph-id set AND P is a subgraph of P', mark P as
	 * not closed.</li>
	 * </ol>
	 */
	static Map<String, PatternInfo> filterClosed(List<PatternInfo> frequent) throws IOException {

		frequent.sort((a, b) -> {
			int ea = a.edges.size(), eb = b.edges.size();
			if (ea != eb)
				return Integer.compare(ea, eb);
			return Integer.compare(a.vlabels.size(), b.vlabels.size());
		});

		int n = frequent.size();
		boolean[] notClosed = new boolean[n];

		for (int i = 0; i < n; i++) {
			if (notClosed[i])
				continue;
			PatternInfo pi = frequent.get(i);

			for (int j = i + 1; j < n; j++) {
				PatternInfo pj = frequent.get(j);

				// pj has >= edges (sorted); we need strictly more
				if (pj.edges.size() == pi.edges.size() && pj.vlabels.size() == pi.vlabels.size())
					continue;

				if (pj.support != pi.support)
					continue;
				if (!sameGraphIds(pi.graphIds, pj.graphIds))
					continue;
				if (isSubgraphOf(pi, pj)) {
					notClosed[i] = true;
					break;
				}
			}
		}

		Map<String, PatternInfo> result = new LinkedHashMap<>();
		for (int i = 0; i < n; i++) {
			if (!notClosed[i]) {
				PatternInfo pi = frequent.get(i);
				result.put(pi.signature, pi);
			}
		}
		return result;
	}

	// ── subgraph isomorphism ─────────────────────────────────────────────────

	static boolean sameGraphIds(List<Integer> a, List<Integer> b) {
		if (a.size() != b.size())
			return false;
		int[] aa = a.stream().mapToInt(Integer::intValue).sorted().toArray();
		int[] bb = b.stream().mapToInt(Integer::intValue).sorted().toArray();
		return Arrays.equals(aa, bb);
	}

	/**
	 * Returns {@code true} if {@code sub} is isomorphic to a subgraph of
	 * {@code sup}. Uses backtracking VF2-style search.
	 */
	static boolean isSubgraphOf(PatternInfo sub, PatternInfo sup) {
		int ns = sub.vlabels.size();
		int nt = sup.vlabels.size();
		if (ns > nt || sub.edges.size() > sup.edges.size())
			return false;

		// label-frequency pre-filter
		int[] subFreq = new int[1000], supFreq = new int[1000];
		for (int l : sub.vlabels)
			subFreq[l]++;
		for (int l : sup.vlabels)
			supFreq[l]++;
		for (int l = 0; l < 1000; l++) {
			if (subFreq[l] > supFreq[l])
				return false;
		}

		int[][] supAdj = new int[nt][nt];
		for (int[] e : sup.edges) {
			supAdj[e[0]][e[1]] = e[2] + 1;
			supAdj[e[1]][e[0]] = e[2] + 1;
		}
		int[][] subAdj = new int[ns][ns];
		for (int[] e : sub.edges) {
			subAdj[e[0]][e[1]] = e[2] + 1;
			subAdj[e[1]][e[0]] = e[2] + 1;
		}

		int[] mapping = new int[ns];
		Arrays.fill(mapping, -1);
		return backtrack(0, mapping, new boolean[nt], subAdj, supAdj, sub.vlabels, sup.vlabels, ns, nt);
	}

	private static boolean backtrack(int depth, int[] mapping, boolean[] used, int[][] subAdj, int[][] supAdj,
			List<Integer> subL, List<Integer> supL, int ns, int nt) {
		if (depth == ns)
			return true;
		int subLabel = subL.get(depth);

		for (int t = 0; t < nt; t++) {
			if (used[t] || supL.get(t) != subLabel)
				continue;

			boolean ok = true;
			for (int prev = 0; prev < depth; prev++) {
				int subVal = subAdj[depth][prev];
				// Only constrain when sub actually has an edge here.
				// (Non-induced / monomorphism semantics: extra edges in
				// the super-pattern are allowed and expected.)
				if (subVal != 0 && supAdj[t][mapping[prev]] != subVal) {
					ok = false;
					break;
				}
			}
			if (!ok)
				continue;

			mapping[depth] = t;
			used[t] = true;
			if (backtrack(depth + 1, mapping, used, subAdj, supAdj, subL, supL, ns, nt))
				return true;
			mapping[depth] = -1;
			used[t] = false;
		}
		return false;
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
		if (nv <= 0) {
			throw new IllegalStateException("Graph " + gid + " has no vertices (nv=" + nv + ")");
		}

		@SuppressWarnings("unchecked")
		List<Integer>[] adj = new List[nv];
		for (int i = 0; i < nv; i++)
			adj[i] = new ArrayList<>();

		Set<Long> seenPairs = new TreeSet<>();

		for (int[] e : edges) {
			if (e == null || e.length != 3) {
				throw new IllegalStateException("Graph " + gid + " has a malformed edge record");
			}
			int u = e[0], v = e[1], lab = e[2];

			if (u < 0 || u >= nv || v < 0 || v >= nv) {
				throw new IllegalStateException("Graph " + gid + " has edge out of bounds: (" + u + "," + v + ")");
			}

			if (u == v) {
				throw new IllegalStateException("Graph " + gid + " has self-loop on vertex " + u);
			}

			if (u > v) {
				throw new IllegalStateException(
						"Graph " + gid + " has non-canonical edge order: e " + u + " " + v + " " + lab);
			}

			long key = (((long) u) << 32) | (v & 0xffffffffL);
			if (!seenPairs.add(key)) {
				throw new IllegalStateException("Graph " + gid + " has duplicate edge between " + u + " and " + v);
			}

			adj[u].add(v);
			adj[v].add(u);
		}

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
				if (current != null && inPattern)
					storePattern(result, current, algoName);
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
				for (int i = 1; i < p.length; i++)
					current.graphIds.add(Integer.parseInt(p[i]));
				if (current.support == 0)
					current.support = p.length - 1;
				current.rawText += line + "\n";
			}
		}
		if (current != null && inPattern)
			storePattern(result, current, algoName);
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

	/**
	 * Three-way comparison result among CloseGraph, GSpan+post, and CFGM.
	 */
	static class ThreeWayComparisonResult {
		boolean identical = true;

		// patterns present in exactly some but not all algorithms
		Set<String> onlyInCloseGraph = new TreeSet<>();
		Set<String> onlyInGSpan = new TreeSet<>();
		Set<String> onlyInCFGM = new TreeSet<>();
		Set<String> inCloseGraphAndGSpanNotCFGM = new TreeSet<>();
		Set<String> inCloseGraphAndCFGMNotGSpan = new TreeSet<>();
		Set<String> inGSpanAndCFGMNotCloseGraph = new TreeSet<>();

		// support mismatches (at least one algorithm differs from the others)
		// key = signature, value = int[3] {closeGraph support, gspan support, cfgm
		// support}
		Map<String, int[]> supportMismatch = new LinkedHashMap<>();
	}

	static ThreeWayComparisonResult compareThreeWay(Map<String, PatternInfo> closeGraph, Map<String, PatternInfo> gspan,
			Map<String, PatternInfo> cfgm) {

		ThreeWayComparisonResult result = new ThreeWayComparisonResult();
		Set<String> allSigs = new TreeSet<>();
		allSigs.addAll(closeGraph.keySet());
		allSigs.addAll(gspan.keySet());
		allSigs.addAll(cfgm.keySet());

		for (String sig : allSigs) {
			PatternInfo cl = closeGraph.get(sig);
			PatternInfo gs = gspan.get(sig);
			PatternInfo cf = cfgm.get(sig);

			boolean hasCl = cl != null;
			boolean hasGs = gs != null;
			boolean hasCf = cf != null;

			if (hasCl && hasGs && hasCf) {
				// all three have it – check support agreement
				if (cl.support != gs.support || cl.support != cf.support || gs.support != cf.support) {
					result.supportMismatch.put(sig, new int[] { cl.support, gs.support, cf.support });
					result.identical = false;
				}
				// else: all agree – nothing to report
			} else if (hasCl && !hasGs && !hasCf) {
				result.onlyInCloseGraph.add(sig);
				result.identical = false;
			} else if (!hasCl && hasGs && !hasCf) {
				result.onlyInGSpan.add(sig);
				result.identical = false;
			} else if (!hasCl && !hasGs && hasCf) {
				result.onlyInCFGM.add(sig);
				result.identical = false;
			} else if (hasCl && hasGs && !hasCf) {
				result.inCloseGraphAndGSpanNotCFGM.add(sig);
				result.identical = false;
			} else if (hasCl && !hasGs && hasCf) {
				result.inCloseGraphAndCFGMNotGSpan.add(sig);
				result.identical = false;
			} else if (!hasCl && hasGs && hasCf) {
				result.inGSpanAndCFGMNotCloseGraph.add(sig);
				result.identical = false;
			}
		}
		return result;
	}

	// ============================================================== arbitration

	/**
	 * Structured verdict produced by comparing all three algorithms against the
	 * oracle.
	 */
	static class ThreeWayVerdict {
		boolean closeGraphIsWrong = false;
		boolean gspanIsWrong = false;
		boolean cfgmIsWrong = false;

		/**
		 * sig → human-readable description of the verdict for this pattern, e.g.
		 * "CFGM_FP", "CLOSEGRAPH_FN", "GSPAN_WRONG_SUPPORT", etc.
		 */
		Map<String, String> patternVerdicts = new LinkedHashMap<>();
	}

	/**
	 * Consults the oracle to determine which algorithm(s) are at fault for each
	 * discrepancy reported in {@code cmp}.
	 */
	static ThreeWayVerdict arbitrateThreeWay(ThreeWayComparisonResult cmp, Map<String, PatternInfo> closeGraph,
			Map<String, PatternInfo> gspan, Map<String, PatternInfo> cfgm, Map<String, PatternInfo> oracle) {

		ThreeWayVerdict v = new ThreeWayVerdict();

		// helper: for a pattern with a given sig, flag the wrong algorithms and
		// record a description. present[0]=closeGraph present[1]=gspan present[2]=cfgm
		// The oracle is used to determine who is right.

		// ── only in CloseGraph ───────────────────────────────────────────────
		for (String sig : cmp.onlyInCloseGraph) {
			if (oracle.containsKey(sig)) {
				// oracle confirms it → GSpan and CFGM both missed it
				v.gspanIsWrong = true;
				v.cfgmIsWrong = true;
				v.patternVerdicts.put(sig, "GSPAN_FN + CFGM_FN (only CloseGraph found it; oracle confirms)");
			} else {
				// oracle rejects it → CloseGraph invented it
				v.closeGraphIsWrong = true;
				v.patternVerdicts.put(sig, "CLOSEGRAPH_FP (oracle does not have it)");
			}
		}

		// ── only in GSpan ────────────────────────────────────────────────────
		for (String sig : cmp.onlyInGSpan) {
			if (oracle.containsKey(sig)) {
				v.closeGraphIsWrong = true;
				v.cfgmIsWrong = true;
				v.patternVerdicts.put(sig, "CLOSEGRAPH_FN + CFGM_FN (only GSpan found it; oracle confirms)");
			} else {
				v.gspanIsWrong = true;
				v.patternVerdicts.put(sig, "GSPAN_FP (oracle does not have it)");
			}
		}

		// ── only in CFGM ─────────────────────────────────────────────────────
		for (String sig : cmp.onlyInCFGM) {
			if (oracle.containsKey(sig)) {
				v.closeGraphIsWrong = true;
				v.gspanIsWrong = true;
				v.patternVerdicts.put(sig, "CLOSEGRAPH_FN + GSPAN_FN (only CFGM found it; oracle confirms)");
			} else {
				v.cfgmIsWrong = true;
				v.patternVerdicts.put(sig, "CFGM_FP (oracle does not have it)");
			}
		}

		// ── in CloseGraph + GSpan but not CFGM ──────────────────────────────
		for (String sig : cmp.inCloseGraphAndGSpanNotCFGM) {
			if (oracle.containsKey(sig)) {
				v.cfgmIsWrong = true;
				v.patternVerdicts.put(sig, "CFGM_FN (CloseGraph and GSpan agree and oracle confirms; CFGM missed it)");
			} else {
				// oracle doesn't have it → both CloseGraph and GSpan are wrong
				v.closeGraphIsWrong = true;
				v.gspanIsWrong = true;
				v.patternVerdicts.put(sig, "CLOSEGRAPH_FP + GSPAN_FP (oracle does not have it; CFGM correctly absent)");
			}
		}

		// ── in CloseGraph + CFGM but not GSpan ──────────────────────────────
		for (String sig : cmp.inCloseGraphAndCFGMNotGSpan) {
			if (oracle.containsKey(sig)) {
				v.gspanIsWrong = true;
				v.patternVerdicts.put(sig, "GSPAN_FN (CloseGraph and CFGM agree and oracle confirms; GSpan missed it)");
			} else {
				v.closeGraphIsWrong = true;
				v.cfgmIsWrong = true;
				v.patternVerdicts.put(sig, "CLOSEGRAPH_FP + CFGM_FP (oracle does not have it; GSpan correctly absent)");
			}
		}

		// ── in GSpan + CFGM but not CloseGraph ──────────────────────────────
		for (String sig : cmp.inGSpanAndCFGMNotCloseGraph) {
			if (oracle.containsKey(sig)) {
				v.closeGraphIsWrong = true;
				v.patternVerdicts.put(sig,
						"CLOSEGRAPH_FN (GSpan and CFGM agree and oracle confirms; CloseGraph missed it)");
			} else {
				v.gspanIsWrong = true;
				v.cfgmIsWrong = true;
				v.patternVerdicts.put(sig, "GSPAN_FP + CFGM_FP (oracle does not have it; CloseGraph correctly absent)");
			}
		}

		// ── support mismatches ───────────────────────────────────────────────
		for (Map.Entry<String, int[]> e : cmp.supportMismatch.entrySet()) {
			String sig = e.getKey();
			int clSup = e.getValue()[0];
			int gsSup = e.getValue()[1];
			int cfSup = e.getValue()[2];

			PatternInfo orInfo = oracle.get(sig);
			if (orInfo == null) {
				v.closeGraphIsWrong = true;
				v.gspanIsWrong = true;
				v.cfgmIsWrong = true;
				v.patternVerdicts.put(sig,
						String.format("ALL_WRONG_SUPPORT (oracle does not have this pattern; CG=%d GS=%d CF=%d)", clSup,
								gsSup, cfSup));
			} else {
				int orSup = orInfo.support;
				boolean clOk = (clSup == orSup);
				boolean gsOk = (gsSup == orSup);
				boolean cfOk = (cfSup == orSup);

				if (!clOk)
					v.closeGraphIsWrong = true;
				if (!gsOk)
					v.gspanIsWrong = true;
				if (!cfOk)
					v.cfgmIsWrong = true;

				v.patternVerdicts.put(sig, String.format("SUPPORT_MISMATCH (oracle=%d | CG=%d%s | GS=%d%s | CF=%d%s)",
						orSup, clSup, clOk ? "✓" : "✗", gsSup, gsOk ? "✓" : "✗", cfSup, cfOk ? "✓" : "✗"));
			}
		}

		return v;
	}

	// ================================================ arbitrated-result printer

	/**
	 * Prints a detailed, human-readable arbitration report showing for every
	 * discrepant pattern which algorithm is wrong and why.
	 */
	static void printArbitratedComparison(ThreeWayVerdict verdict, Map<String, PatternInfo> closeGraph,
			Map<String, PatternInfo> gspan, Map<String, PatternInfo> cfgm, Map<String, PatternInfo> oracle) {

		System.err.println("\n╔══════════════════════════════════════════════════════════════╗");
		System.err.println("║      ARBITRATED COMPARISON  (oracle as ground truth)        ║");
		System.err.println("╚══════════════════════════════════════════════════════════════╝");

		System.err.printf("%nPattern counts:%n" + "  CloseGraph : %d%n" + "  GSpan+post : %d%n" + "  CFGM       : %d%n"
				+ "  Oracle     : %d%n", closeGraph.size(), gspan.size(), cfgm.size(), oracle.size());

		// ── overall verdict ──────────────────────────────────────────────────
		System.err.println();
		List<String> wrongAlgos = new ArrayList<>();
		if (verdict.closeGraphIsWrong)
			wrongAlgos.add("CloseGraph");
		if (verdict.gspanIsWrong)
			wrongAlgos.add("GSpan+post");
		if (verdict.cfgmIsWrong)
			wrongAlgos.add("CFGM");

		if (wrongAlgos.isEmpty()) {
			System.err.println("🟡 VERDICT: Discrepancy found but oracle arbitration inconclusive");
		} else if (wrongAlgos.size() == 3) {
			System.err.println("🔴 VERDICT: ALL THREE algorithms have errors!");
		} else {
			String wrong = String.join(" AND ", wrongAlgos);
			List<String> correct = new ArrayList<>(Arrays.asList("CloseGraph", "GSpan+post", "CFGM"));
			correct.removeAll(wrongAlgos);
			String right = String.join(" AND ", correct);
			System.err.printf("🔴 VERDICT: %s is WRONG  |  %s is correct%n", wrong, right);
		}

		// ── per-pattern detail ───────────────────────────────────────────────
		System.err.println("\n──────────────────────────────────────────────────────────────");
		System.err.println("  PER-PATTERN DETAIL");
		System.err.println("──────────────────────────────────────────────────────────────");

		int idx = 0;
		for (Map.Entry<String, String> pv : verdict.patternVerdicts.entrySet()) {
			String sig = pv.getKey();
			String verdictStr = pv.getValue();

			PatternInfo clInfo = closeGraph.get(sig);
			PatternInfo gsInfo = gspan.get(sig);
			PatternInfo cfInfo = cfgm.get(sig);
			PatternInfo orInfo = oracle.get(sig);

			PatternInfo display = clInfo != null ? clInfo : gsInfo != null ? gsInfo : cfInfo != null ? cfInfo : orInfo;

			System.err.printf("%n  [%d] %s%n", ++idx, verdictStr);
			System.err.printf("      Signature     : %s%n", sig);
			if (display != null) {
				System.err.printf("      Vertex labels : %s%n", display.vlabels);
				System.err.printf("      Edges         : %s%n", edgesToString(display.edges));
			}
			System.err.printf("      CloseGraph    : %s%n",
					clInfo != null ? "support=" + clInfo.support + "  graphIds=" + clInfo.graphIds : "ABSENT");
			System.err.printf("      GSpan+post    : %s%n",
					gsInfo != null ? "support=" + gsInfo.support + "  graphIds=" + gsInfo.graphIds : "ABSENT");
			System.err.printf("      CFGM          : %s%n",
					cfInfo != null ? "support=" + cfInfo.support + "  graphIds=" + cfInfo.graphIds : "ABSENT");
			System.err.printf("      Oracle        : %s%n",
					orInfo != null ? "support=" + orInfo.support + "  graphIds=" + orInfo.graphIds : "ABSENT");
		}

		// ── patterns in oracle but missing from all three ─────────────────────
		Set<String> allMissed = new TreeSet<>(oracle.keySet());
		allMissed.removeAll(closeGraph.keySet());
		allMissed.removeAll(gspan.keySet());
		allMissed.removeAll(cfgm.keySet());
		if (!allMissed.isEmpty()) {
			System.err.printf("%n  ⚠ %d pattern(s) in Oracle that ALL algorithms missed:%n", allMissed.size());
			int i = 0;
			for (String sig : allMissed) {
				PatternInfo or = oracle.get(sig);
				System.err.printf("    [%d] sig=%s  support=%d  graphIds=%s%n", ++i, sig, or.support, or.graphIds);
				System.err.printf("         labels=%s  edges=%s%n", or.vlabels, edgesToString(or.edges));
			}
		}

		// ── patterns in all three algorithms but not in oracle ───────────────
		Set<String> allInvented = new TreeSet<>(closeGraph.keySet());
		allInvented.retainAll(gspan.keySet());
		allInvented.retainAll(cfgm.keySet());
		allInvented.removeAll(oracle.keySet());
		if (!allInvented.isEmpty()) {
			System.err.printf("%n  ⚠ %d pattern(s) reported by ALL algorithms but NOT in Oracle:%n",
					allInvented.size());
			int i = 0;
			for (String sig : allInvented) {
				PatternInfo cl = closeGraph.get(sig);
				PatternInfo gs = gspan.get(sig);
				PatternInfo cf = cfgm.get(sig);
				System.err.printf("    [%d] sig=%s  support(CG)=%d  support(GS)=%d  support(CF)=%d%n", ++i, sig,
						cl.support, gs.support, cf.support);
				System.err.printf("         labels=%s  edges=%s%n", cl.vlabels, edgesToString(cl.edges));
			}
		}
	}

	// ============================================================ fault summary

	static void printFaultSummary() {
		System.out.println();
		System.out.println("╔══════════════════════════════════════════════════════════════════╗");
		System.out.println("║                     FAULT ATTRIBUTION                           ║");
		System.out.println("╠══════════════════════════════════════════════════════════════════╣");
		System.out.printf("║  Tests where CloseGraph was wrong  : %-29d║%n", closeGraphFaultCount);
		System.out.printf("║  Tests where GSpan+post was wrong  : %-29d║%n", gspanFaultCount);
		System.out.printf("║  Tests where CFGM was wrong        : %-29d║%n", cfgmFaultCount);
		System.out.printf("║  Tests where all three were wrong  : %-29d║%n", bothFaultCount);
		System.out.println("╚══════════════════════════════════════════════════════════════════╝");
	}

	// ======================================================== comparison printer

	static String edgesToString(List<int[]> edges) {
		return edges.stream().map(e -> String.format("(%d-%d:%d)", e[0], e[1], e[2])).collect(Collectors.joining(", "));
	}

	// ======================================================= ranking printers

	static void printPerTestRanking(RunMetrics cg, RunMetrics gs, RunMetrics cf) {
		// time ranking
		long minTime = Math.min(cg.runtimeMs, Math.min(gs.runtimeMs, cf.runtimeMs));
		String fastestTime = cg.runtimeMs == minTime ? "CloseGraph" : gs.runtimeMs == minTime ? "GSpan+post" : "CFGM";

		// memory ranking
		long minMem = Math.min(cg.peakMemoryBytes, Math.min(gs.peakMemoryBytes, cf.peakMemoryBytes));
		String leanestMem = cg.peakMemoryBytes == minMem ? "CloseGraph"
				: gs.peakMemoryBytes == minMem ? "GSpan+post" : "CFGM";

		System.out.println("\n  ┌──────────────── Per-test ranking ─────────────────────────┐");
		System.out.printf("  │  ⏱  Time  : CG=%dms  GS=%dms  CF=%dms  (%s fastest)%n", cg.runtimeMs, gs.runtimeMs,
				cf.runtimeMs, fastestTime);
		System.out.printf("  │  🧠 Memory: CG=%s  GS=%s  CF=%s  (%s leanest)%n", formatBytes(cg.peakMemoryBytes),
				formatBytes(gs.peakMemoryBytes), formatBytes(cf.peakMemoryBytes), leanestMem);
		System.out.println("  └────────────────────────────────────────────────────────────┘");
	}

	static void printRunningTotals() {
		if (perfTestsCount == 0)
			return;
		System.out.println("\n  ┌──────────────── Running totals ────────────────────────────┐");
		System.out.printf("  │  Tests measured          : %d%n", perfTestsCount);
		System.out.printf("  │  CloseGraph  total time  : %s  avg: %s/test%n", formatMs(closeGraphTotalTimeMs),
				formatMs(closeGraphTotalTimeMs / perfTestsCount));
		System.out.printf("  │  GSpan+post  total time  : %s  avg: %s/test%n", formatMs(gspanTotalTimeMs),
				formatMs(gspanTotalTimeMs / perfTestsCount));
		System.out.printf("  │  CFGM        total time  : %s  avg: %s/test%n", formatMs(cfgmTotalTimeMs),
				formatMs(cfgmTotalTimeMs / perfTestsCount));
		System.out.printf("  │  Oracle      total time  : %s  avg: %s/test%n", formatMs(oracleTotalTimeMs),
				formatMs(oracleTotalTimeMs == 0 ? 0 : oracleTotalTimeMs / perfTestsCount));
		System.out.printf("  │  CloseGraph  total mem   : %s  avg: %s/test%n", formatBytes(closeGraphTotalMemBytes),
				formatBytes(closeGraphTotalMemBytes / perfTestsCount));
		System.out.printf("  │  GSpan+post  total mem   : %s  avg: %s/test%n", formatBytes(gspanTotalMemBytes),
				formatBytes(gspanTotalMemBytes / perfTestsCount));
		System.out.printf("  │  CFGM        total mem   : %s  avg: %s/test%n", formatBytes(cfgmTotalMemBytes),
				formatBytes(cfgmTotalMemBytes / perfTestsCount));
		System.out.println("  └────────────────────────────────────────────────────────────┘");
	}

	static void printFinalPerformanceRanking() {
		System.out.println();
		System.out.println("╔══════════════════════════════════════════════════════════════════╗");
		System.out.println("║   PERFORMANCE RANKING – CloseGraph vs GSpan+post vs CFGM       ║");
		System.out.println("╠══════════════════════════════════════════════════════════════════╣");
		if (perfTestsCount == 0) {
			System.out.println("║  No performance data collected.                                  ║");
			System.out.println("╚══════════════════════════════════════════════════════════════════╝");
			return;
		}
		System.out.printf("║  Tests measured: %-47d║%n", perfTestsCount);
		System.out.println("╠══════════════════════════════════════════════════════════════════╣");

		System.out.println("║  ⏱  RUNTIME                                                      ║");
		System.out.printf("║    CloseGraph  total : %-42s║%n", formatMs(closeGraphTotalTimeMs));
		System.out.printf("║    GSpan+post  total : %-42s║%n", formatMs(gspanTotalTimeMs));
		System.out.printf("║    CFGM        total : %-42s║%n", formatMs(cfgmTotalTimeMs));
		System.out.printf("║    CloseGraph  avg   : %-42s║%n",
				formatMs(closeGraphTotalTimeMs / perfTestsCount) + " / test");
		System.out.printf("║    GSpan+post  avg   : %-42s║%n", formatMs(gspanTotalTimeMs / perfTestsCount) + " / test");
		System.out.printf("║    CFGM        avg   : %-42s║%n", formatMs(cfgmTotalTimeMs / perfTestsCount) + " / test");

		long minTotalTime = Math.min(closeGraphTotalTimeMs, Math.min(gspanTotalTimeMs, cfgmTotalTimeMs));
		String fastestTotal = closeGraphTotalTimeMs == minTotalTime ? "CloseGraph"
				: gspanTotalTimeMs == minTotalTime ? "GSpan+post" : "CFGM";
		System.out.printf("║    🏆 Fastest (total): %-42s║%n", fastestTotal);

		System.out.println("╠══════════════════════════════════════════════════════════════════╣");
		System.out.println("║  🧠 MEMORY USAGE                                                 ║");
		System.out.printf("║    CloseGraph  total : %-42s║%n", formatBytes(closeGraphTotalMemBytes));
		System.out.printf("║    GSpan+post  total : %-42s║%n", formatBytes(gspanTotalMemBytes));
		System.out.printf("║    CFGM        total : %-42s║%n", formatBytes(cfgmTotalMemBytes));
		System.out.printf("║    CloseGraph  avg   : %-42s║%n",
				formatBytes(closeGraphTotalMemBytes / perfTestsCount) + " / test");
		System.out.printf("║    GSpan+post  avg   : %-42s║%n",
				formatBytes(gspanTotalMemBytes / perfTestsCount) + " / test");
		System.out.printf("║    CFGM        avg   : %-42s║%n",
				formatBytes(cfgmTotalMemBytes / perfTestsCount) + " / test");

		long minTotalMem = Math.min(closeGraphTotalMemBytes, Math.min(gspanTotalMemBytes, cfgmTotalMemBytes));
		String leanestTotal = closeGraphTotalMemBytes == minTotalMem ? "CloseGraph"
				: gspanTotalMemBytes == minTotalMem ? "GSpan+post" : "CFGM";
		System.out.printf("║    🏆 Leanest  (total): %-42s║%n", leanestTotal);
		System.out.println("╚══════════════════════════════════════════════════════════════════╝");
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

	static void printFullDebugInfo(String graphDb, String closeGraphOut, String gspanOut, String cfgmOut) {
		System.err.println("\n┌──────────────────────────────────────────────────────────────┐");
		System.err.println("│                   FULL DEBUG OUTPUT                          │");
		System.err.println("└──────────────────────────────────────────────────────────────┘");
		System.err.println("\n▼▼▼ INPUT GRAPH DATABASE ▼▼▼");
		System.err.println(graphDb);
		System.err.println("\n▼▼▼ CLOSEGRAPH OUTPUT ▼▼▼");
		System.err.println(closeGraphOut);
		System.err.println("\n▼▼▼ GSPAN RAW OUTPUT (before post-processing) ▼▼▼");
		System.err.println(gspanOut);
		System.err.println("\n▼▼▼ CFGM OUTPUT ▼▼▼");
		System.err.println(cfgmOut);
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
	 * Serialises the oracle result map into a human-readable string suitable for
	 * saving as a failure artefact.
	 */
	static String oracleResultToString(Map<String, PatternInfo> oracle) {
		StringBuilder sb = new StringBuilder();
		sb.append("# Oracle result  (").append(oracle.size()).append(" closed patterns)\n\n");
		int i = 0;
		for (PatternInfo p : oracle.values()) {
			sb.append("t # ").append(i++).append(" * ").append(p.support).append("\n");
			for (int j = 0; j < p.vlabels.size(); j++) {
				sb.append("v ").append(j).append(" ").append(p.vlabels.get(j)).append("\n");
			}
			for (int[] e : p.edges) {
				sb.append("e ").append(e[0]).append(" ").append(e[1]).append(" ").append(e[2]).append("\n");
			}
			sb.append("x");
			for (int gid : p.graphIds)
				sb.append(" ").append(gid);
			sb.append("\n\n");
		}
		return sb.toString();
	}

	static void saveFailure(int testIdx, long seed, String input, String closeGraphOut, String gspanOut, String cfgmOut,
			String oracleOut, String reason) {
		try {
			String base = FAIL_DIR + File.separator + "test_" + testIdx + "_seed_" + seed;
			writeFile(base + "_input.txt",
					String.format("# Test: %d%n# Seed: %d%n# Reason: %s%n" + "# MinSupport: %.2f%n%n%s", testIdx, seed,
							reason, MIN_SUPPORT, input));
			writeFile(base + "_closegraph.txt", closeGraphOut);
			writeFile(base + "_gspan_raw.txt", gspanOut);
			writeFile(base + "_cfgm.txt", cfgmOut);
			writeFile(base + "_oracle.txt", oracleOut);
			writeFile(base + "_summary.txt",
					String.format("Test %d failed (seed %d)%nReason: %s%n", testIdx, seed, reason));
		} catch (IOException ex) {
			System.err.println("⚠ Could not save failure files: " + ex.getMessage());
		}
	}
}