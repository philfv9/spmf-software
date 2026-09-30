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

import ca.pfv.spmf.algorithms.graph_mining.ffsm.AlgoFFSM;
import ca.pfv.spmf.algorithms.graph_mining.fsg.AlgoFSG;
import ca.pfv.spmf.algorithms.graph_mining.fsg.FSGPattern;
import ca.pfv.spmf.algorithms.graph_mining.fsma.AlgoFSMA;
import ca.pfv.spmf.algorithms.graph_mining.fsp.AlgoFSP;
import ca.pfv.spmf.algorithms.graph_mining.tkg.AlgoGSPAN;

/**
 * Automated regression harness: FSG vs gSPAN vs FSP vs FSMA vs FFSM with
 * detailed debugging output. Tracks total runtime and memory usage for all five
 * algorithms across all tests. Displays a performance ranking at the end
 * showing which algorithm is fastest and uses least memory, and by how much.
 */
public class MainTestFSGvsFSPvsGSPANvsFSMA {

    // ================================================================== config
    private static final int    NUM_TESTS        = 10000;
    private static final double MIN_SUPPORT      = 0.2;
    private static final int    MIN_GRAPHS       = 300;
    private static final int    MAX_GRAPHS       = 500;
    private static final int    MIN_VERTICES     = 5;
    private static final int    MAX_VERTICES     = 8;
    private static final double EDGE_PROBABILITY = 0.35;
    private static final int    NUM_VERTEX_LABELS = 3;
    private static final int    NUM_EDGE_LABELS   = 2;
    private static final long   BASE_SEED        = 20240101L;

    private static final String TMP_INPUT  = System.getProperty("java.io.tmpdir") + File.separator + "fsg_test_input.txt";
    private static final String TMP_FSG    = System.getProperty("java.io.tmpdir") + File.separator + "fsg_test_out_fsg.txt";
    private static final String TMP_GSPAN  = System.getProperty("java.io.tmpdir") + File.separator + "fsg_test_out_gspan.txt";
    private static final String TMP_FSP    = System.getProperty("java.io.tmpdir") + File.separator + "fsg_test_out_fsp.txt";
    private static final String TMP_FSMA   = System.getProperty("java.io.tmpdir") + File.separator + "fsg_test_out_fsma.txt";
    private static final String TMP_FFSM   = System.getProperty("java.io.tmpdir") + File.separator + "fsg_test_out_ffsm.txt";
    private static final String FAIL_DIR   = System.getProperty("java.io.tmpdir") + File.separator + "fsg_failures";

    // ============================================================ performance tracking

    /** Holds runtime (ms) and peak memory (bytes) for a single algorithm run. */
    static class RunMetrics {
        long runtimeMs;
        long peakMemoryBytes;
    }

    // Accumulated totals across all successful test runs
    private static long fsgTotalTimeMs   = 0L;
    private static long gspanTotalTimeMs = 0L;
    private static long fspTotalTimeMs   = 0L;
    private static long fsmaTotalTimeMs  = 0L;
    private static long ffsmTotalTimeMs  = 0L;

    private static long fsgTotalMemBytes   = 0L;
    private static long gspanTotalMemBytes = 0L;
    private static long fspTotalMemBytes   = 0L;
    private static long fsmaTotalMemBytes  = 0L;
    private static long ffsmTotalMemBytes  = 0L;

    /** Tests where ALL FIVE algorithms succeeded. */
    private static int perfTestsCount = 0;

    // ===================================================================== main
    public static void main(String[] args) throws Exception {

        Files.createDirectories(Paths.get(FAIL_DIR));

        int passed = 0, failed = 0;

        for (int t = 0; t < NUM_TESTS; t++) {
            long   seed = BASE_SEED + t;
            Random rng  = new Random(seed);

            System.out.printf("\n");
            System.out.printf("═══════════════════════════════════════════════════════════════════════\n");
            System.out.printf("  TEST %d / %d  (seed = %d)\n", t + 1, NUM_TESTS, seed);
            System.out.printf("═══════════════════════════════════════════════════════════════════════\n");

            // 1. Generate synthetic graph database
            String graphDb = generateGraphDatabase(rng);
            writeFile(TMP_INPUT, graphDb);

            System.out.println("✓ Generated graph database:");
            printGraphDatabaseSummary(graphDb);

            // ── 2. Run FSG ────────────────────────────────────────────────────
            Map<String, PatternInfo> fsgResult;
            RunMetrics fsgMetrics;
            try {
                System.out.println("\n⏳ Running FSG...");
                fsgMetrics = new RunMetrics();
                fsgResult  = runFSG(fsgMetrics);
                System.out.printf("✓ FSG completed in %d ms  |  peak memory: %s\n",
                        fsgMetrics.runtimeMs, formatBytes(fsgMetrics.peakMemoryBytes));
                System.out.printf("  FSG found %d patterns\n", fsgResult.size());
            } catch (Exception e) {
                failed++;
                System.err.println("\n❌ FSG CRASHED!");
                printException(e);
                saveFailure(t, seed, graphDb, "", "", "", "", "", "FSG_EXCEPTION");
                printDebugFooter(t, seed);
                continue;
            }

            // ── 3. Run gSPAN ─────────────────────────────────────────────────
            Map<String, PatternInfo> gspanResult;
            RunMetrics gspanMetrics;
            try {
                System.out.println("\n⏳ Running gSPAN...");
                gspanMetrics = new RunMetrics();
                gspanResult  = runGSPAN(gspanMetrics);
                System.out.printf("✓ gSPAN completed in %d ms  |  peak memory: %s\n",
                        gspanMetrics.runtimeMs, formatBytes(gspanMetrics.peakMemoryBytes));
                System.out.printf("  gSPAN found %d patterns\n", gspanResult.size());
            } catch (Exception e) {
                failed++;
                System.err.println("\n❌ gSPAN CRASHED!");
                printException(e);
                saveFailure(t, seed, graphDb, readFile(TMP_FSG), "", "", "", "", "GSPAN_EXCEPTION");
                printDebugFooter(t, seed);
                continue;
            }

            // ── 4. Run FSP ───────────────────────────────────────────────────
            Map<String, PatternInfo> fspResult;
            RunMetrics fspMetrics;
            try {
                System.out.println("\n⏳ Running FSP...");
                fspMetrics = new RunMetrics();
                fspResult  = runFSP(fspMetrics);
                System.out.printf("✓ FSP completed in %d ms  |  peak memory: %s\n",
                        fspMetrics.runtimeMs, formatBytes(fspMetrics.peakMemoryBytes));
                System.out.printf("  FSP found %d patterns\n", fspResult.size());
            } catch (Exception e) {
                failed++;
                System.err.println("\n❌ FSP CRASHED!");
                printException(e);
                saveFailure(t, seed, graphDb, readFile(TMP_FSG), readFile(TMP_GSPAN), "", "", "", "FSP_EXCEPTION");
                printDebugFooter(t, seed);
                continue;
            }

            // ── 5. Run FSMA ──────────────────────────────────────────────────
            Map<String, PatternInfo> fsmaResult;
            RunMetrics fsmaMetrics;
            try {
                System.out.println("\n⏳ Running FSMA...");
                fsmaMetrics = new RunMetrics();
                fsmaResult  = runFSMA(fsmaMetrics);
                System.out.printf("✓ FSMA completed in %d ms  |  peak memory: %s\n",
                        fsmaMetrics.runtimeMs, formatBytes(fsmaMetrics.peakMemoryBytes));
                System.out.printf("  FSMA found %d patterns\n", fsmaResult.size());
            } catch (Exception e) {
                failed++;
                System.err.println("\n❌ FSMA CRASHED!");
                printException(e);
                saveFailure(t, seed, graphDb, readFile(TMP_FSG), readFile(TMP_GSPAN),
                        readFile(TMP_FSP), "", "", "FSMA_EXCEPTION");
                printDebugFooter(t, seed);
                continue;
            }

            // ── 6. Run FFSM ──────────────────────────────────────────────────
            Map<String, PatternInfo> ffsmResult;
            RunMetrics ffsmMetrics;
            try {
                System.out.println("\n⏳ Running FFSM...");
                ffsmMetrics = new RunMetrics();
                ffsmResult  = runFFSM(ffsmMetrics);
                System.out.printf("✓ FFSM completed in %d ms  |  peak memory: %s\n",
                        ffsmMetrics.runtimeMs, formatBytes(ffsmMetrics.peakMemoryBytes));
                System.out.printf("  FFSM found %d patterns\n", ffsmResult.size());
            } catch (Exception e) {
                failed++;
                System.err.println("\n❌ FFSM CRASHED!");
                printException(e);
                saveFailure(t, seed, graphDb, readFile(TMP_FSG), readFile(TMP_GSPAN),
                        readFile(TMP_FSP), readFile(TMP_FSMA), "", "FFSM_EXCEPTION");
                printDebugFooter(t, seed);
                continue;
            }

            // Accumulate performance totals (only when all five algorithms succeeded)
            fsgTotalTimeMs   += fsgMetrics.runtimeMs;
            gspanTotalTimeMs += gspanMetrics.runtimeMs;
            fspTotalTimeMs   += fspMetrics.runtimeMs;
            fsmaTotalTimeMs  += fsmaMetrics.runtimeMs;
            ffsmTotalTimeMs  += ffsmMetrics.runtimeMs;

            fsgTotalMemBytes   += fsgMetrics.peakMemoryBytes;
            gspanTotalMemBytes += gspanMetrics.peakMemoryBytes;
            fspTotalMemBytes   += fspMetrics.peakMemoryBytes;
            fsmaTotalMemBytes  += fsmaMetrics.peakMemoryBytes;
            ffsmTotalMemBytes  += ffsmMetrics.peakMemoryBytes;

            perfTestsCount++;

            // Per-test mini-ranking (all five)
            printPerTestRanking(fsgMetrics, gspanMetrics, fspMetrics, fsmaMetrics, ffsmMetrics);

            // ── 7. Compare results ────────────────────────────────────────────
            ComparisonResult comparison =
                    compareResults(fsgResult, gspanResult, fspResult, fsmaResult, ffsmResult);

            if (comparison.identical) {
                passed++;
                System.out.println("\n✅ PASS - Results match exactly");
                if ((t + 1) % 50 == 0) {
                    System.out.printf("\n🎯 Progress: %d / %d tests passed so far\n", passed, t + 1);
                    printRunningTotals();
                }
            } else {
                failed++;
                System.err.println("\n❌ FAIL - Results differ!");

                printDetailedComparison(comparison, fsgResult, gspanResult, fspResult, fsmaResult, ffsmResult);

                String fsgTxt   = readFile(TMP_FSG);
                String gspanTxt = readFile(TMP_GSPAN);
                String fspTxt   = readFile(TMP_FSP);
                String fsmaTxt  = readFile(TMP_FSMA);
                String ffsmTxt  = readFile(TMP_FFSM);
                saveFailure(t, seed, graphDb, fsgTxt, gspanTxt, fspTxt, fsmaTxt, ffsmTxt, "MISMATCH");

                printFullDebugInfo(t, seed, graphDb, fsgTxt, gspanTxt, fspTxt, fsmaTxt, ffsmTxt, comparison);

                printDebugFooter(t, seed);

                System.err.println("\n⛔ STOPPING AT FIRST FAILURE FOR DEBUGGING");
                System.err.println("   To continue through all tests, comment out the break statement.");
                break; // Comment this out to continue through all tests
            }
        }

        // ─── Final summary ─────────────────────────────────────────────────
        System.out.println("\n");
        System.out.println("═══════════════════════════════════════════════════════════════════════");
        System.out.println("                          FINAL RESULTS                                ");
        System.out.println("═══════════════════════════════════════════════════════════════════════");
        System.out.printf(" Passed : %d / %d\n", passed, NUM_TESTS);
        System.out.printf(" Failed : %d / %d\n", failed, NUM_TESTS);
        System.out.println("═══════════════════════════════════════════════════════════════════════");

        printFinalPerformanceRanking();

        if (failed > 0) {
            System.err.println("\n⚠ Failure artifacts saved to: " + FAIL_DIR);
        }

        System.exit(failed > 0 ? 1 : 0);
    }

    // ======================================================= algorithm runners

    /**
     * Runs FSG, measures wall-clock time and peak heap usage, and returns the
     * parsed pattern map.
     */
    static Map<String, PatternInfo> runFSG(RunMetrics m) throws Exception {
        Runtime rt = Runtime.getRuntime();
        rt.gc(); rt.gc();
        long memBefore = rt.totalMemory() - rt.freeMemory();

        long start = System.currentTimeMillis();
        AlgoFSG fsg = new AlgoFSG();
        fsg.runAlgorithm(TMP_INPUT, TMP_FSG, MIN_SUPPORT);
        m.runtimeMs = System.currentTimeMillis() - start;

        long memAfter = rt.totalMemory() - rt.freeMemory();
        m.peakMemoryBytes = Math.max(0L, memAfter - memBefore);

        return parseOutputDetailed(TMP_FSG, "FSG");
    }

    /**
     * Runs gSPAN, measures wall-clock time and peak heap usage, and returns the
     * parsed pattern map.
     */
    static Map<String, PatternInfo> runGSPAN(RunMetrics m) throws Exception {
        Runtime rt = Runtime.getRuntime();
        rt.gc(); rt.gc();
        long memBefore = rt.totalMemory() - rt.freeMemory();

        long start = System.currentTimeMillis();
        AlgoGSPAN gspan = new AlgoGSPAN();
        gspan.runAlgorithm(TMP_INPUT, TMP_GSPAN, MIN_SUPPORT, true, false, Integer.MAX_VALUE, true);
        m.runtimeMs = System.currentTimeMillis() - start;

        long memAfter = rt.totalMemory() - rt.freeMemory();
        m.peakMemoryBytes = Math.max(0L, memAfter - memBefore);

        return parseOutputDetailed(TMP_GSPAN, "gSPAN");
    }

    /**
     * Runs FSP, measures wall-clock time and peak heap usage, and returns the
     * parsed pattern map.
     */
    static Map<String, PatternInfo> runFSP(RunMetrics m) throws Exception {
        Runtime rt = Runtime.getRuntime();
        rt.gc(); rt.gc();
        long memBefore = rt.totalMemory() - rt.freeMemory();

        long start = System.currentTimeMillis();
        AlgoFSP fsp = new AlgoFSP();
        fsp.runAlgorithm(TMP_INPUT, TMP_FSP, MIN_SUPPORT, true, false, Integer.MAX_VALUE, true);
        m.runtimeMs = System.currentTimeMillis() - start;

        long memAfter = rt.totalMemory() - rt.freeMemory();
        m.peakMemoryBytes = Math.max(0L, memAfter - memBefore);

        return parseOutputDetailed(TMP_FSP, "FSP");
    }

    /**
     * Runs FSMA, measures wall-clock time and peak heap usage, and returns the
     * parsed pattern map.
     */
    static Map<String, PatternInfo> runFSMA(RunMetrics m) throws Exception {
        Runtime rt = Runtime.getRuntime();
        rt.gc(); rt.gc();
        long memBefore = rt.totalMemory() - rt.freeMemory();

        long start = System.currentTimeMillis();
        AlgoFSMA fsma = new AlgoFSMA();
        fsma.runAlgorithm(TMP_INPUT, TMP_FSMA, MIN_SUPPORT);
        m.runtimeMs = System.currentTimeMillis() - start;

        long memAfter = rt.totalMemory() - rt.freeMemory();
        m.peakMemoryBytes = Math.max(0L, memAfter - memBefore);

        return parseOutputDetailed(TMP_FSMA, "FSMA");
    }

    /**
     * Runs FFSM, measures wall-clock time and peak heap usage, and returns the
     * parsed pattern map. FFSM (Fast Frequent Subgraph Mining) is called with the
     * same input file and minimum support threshold used by all other algorithms so
     * that results are directly comparable.
     */
    static Map<String, PatternInfo> runFFSM(RunMetrics m) throws Exception {
        Runtime rt = Runtime.getRuntime();
        rt.gc(); rt.gc();
        long memBefore = rt.totalMemory() - rt.freeMemory();

        long start = System.currentTimeMillis();
        AlgoFFSM ffsm = new AlgoFFSM();
        ffsm.runAlgorithm(TMP_INPUT, TMP_FFSM, MIN_SUPPORT);
        m.runtimeMs = System.currentTimeMillis() - start;

        long memAfter = rt.totalMemory() - rt.freeMemory();
        m.peakMemoryBytes = Math.max(0L, memAfter - memBefore);

        return parseOutputDetailed(TMP_FFSM, "FFSM");
    }

    // ======================================================= ranking printers

    /**
     * Prints a per-test speed/memory comparison between all five algorithms.
     */
    static void printPerTestRanking(RunMetrics fsg, RunMetrics gspan,
                                     RunMetrics fsp, RunMetrics fsma, RunMetrics ffsm) {

        // index: 0=FSG, 1=gSPAN, 2=FSP, 3=FSMA, 4=FFSM
        List<long[]> timeList = new ArrayList<>();
        timeList.add(new long[]{fsg.runtimeMs,   0});
        timeList.add(new long[]{gspan.runtimeMs,  1});
        timeList.add(new long[]{fsp.runtimeMs,    2});
        timeList.add(new long[]{fsma.runtimeMs,   3});
        timeList.add(new long[]{ffsm.runtimeMs,   4});
        timeList.sort((a, b) -> Long.compare(a[0], b[0]));

        List<long[]> memList = new ArrayList<>();
        memList.add(new long[]{fsg.peakMemoryBytes,   0});
        memList.add(new long[]{gspan.peakMemoryBytes,  1});
        memList.add(new long[]{fsp.peakMemoryBytes,    2});
        memList.add(new long[]{fsma.peakMemoryBytes,   3});
        memList.add(new long[]{ffsm.peakMemoryBytes,   4});
        memList.sort((a, b) -> Long.compare(a[0], b[0]));

        String[] names = {"FSG", "gSPAN", "FSP", "FSMA", "FFSM"};

        System.out.println("\n  ┌─────────────────── Per-test ranking ───────────────────────┐");
        System.out.printf ("  │  ⏱  Time  rank: %s > %s > %s > %s > %s%n",
                names[(int)timeList.get(0)[1]], names[(int)timeList.get(1)[1]],
                names[(int)timeList.get(2)[1]], names[(int)timeList.get(3)[1]],
                names[(int)timeList.get(4)[1]]);
        System.out.printf ("  │     %s=%dms  %s=%dms  %s=%dms  %s=%dms  %s=%dms%n",
                names[(int)timeList.get(0)[1]], timeList.get(0)[0],
                names[(int)timeList.get(1)[1]], timeList.get(1)[0],
                names[(int)timeList.get(2)[1]], timeList.get(2)[0],
                names[(int)timeList.get(3)[1]], timeList.get(3)[0],
                names[(int)timeList.get(4)[1]], timeList.get(4)[0]);
        System.out.printf ("  │  🧠 Memory rank: %s > %s > %s > %s > %s%n",
                names[(int)memList.get(0)[1]], names[(int)memList.get(1)[1]],
                names[(int)memList.get(2)[1]], names[(int)memList.get(3)[1]],
                names[(int)memList.get(4)[1]]);
        System.out.printf ("  │     %s=%s  %s=%s  %s=%s  %s=%s  %s=%s%n",
                names[(int)memList.get(0)[1]], formatBytes(memList.get(0)[0]),
                names[(int)memList.get(1)[1]], formatBytes(memList.get(1)[0]),
                names[(int)memList.get(2)[1]], formatBytes(memList.get(2)[0]),
                names[(int)memList.get(3)[1]], formatBytes(memList.get(3)[0]),
                names[(int)memList.get(4)[1]], formatBytes(memList.get(4)[0]));
        System.out.println("  └────────────────────────────────────────────────────────────┘");
    }

    /**
     * Prints running accumulated totals every 50 tests (all five algorithms).
     */
    static void printRunningTotals() {
        if (perfTestsCount == 0) return;
        System.out.println("\n  ┌──────────────────── Running totals ────────────────────────┐");
        System.out.printf ("  │  Tests measured : %d%n", perfTestsCount);
        System.out.printf ("  │  FSG   total time : %s  │  avg: %s/test%n",
                formatMs(fsgTotalTimeMs),   formatMs(fsgTotalTimeMs   / perfTestsCount));
        System.out.printf ("  │  gSPAN total time : %s  │  avg: %s/test%n",
                formatMs(gspanTotalTimeMs), formatMs(gspanTotalTimeMs / perfTestsCount));
        System.out.printf ("  │  FSP   total time : %s  │  avg: %s/test%n",
                formatMs(fspTotalTimeMs),   formatMs(fspTotalTimeMs   / perfTestsCount));
        System.out.printf ("  │  FSMA  total time : %s  │  avg: %s/test%n",
                formatMs(fsmaTotalTimeMs),  formatMs(fsmaTotalTimeMs  / perfTestsCount));
        System.out.printf ("  │  FFSM  total time : %s  │  avg: %s/test%n",
                formatMs(ffsmTotalTimeMs),  formatMs(ffsmTotalTimeMs  / perfTestsCount));
        System.out.printf ("  │  FSG   total mem  : %s  │  avg: %s/test%n",
                formatBytes(fsgTotalMemBytes),   formatBytes(fsgTotalMemBytes   / perfTestsCount));
        System.out.printf ("  │  gSPAN total mem  : %s  │  avg: %s/test%n",
                formatBytes(gspanTotalMemBytes), formatBytes(gspanTotalMemBytes / perfTestsCount));
        System.out.printf ("  │  FSP   total mem  : %s  │  avg: %s/test%n",
                formatBytes(fspTotalMemBytes),   formatBytes(fspTotalMemBytes   / perfTestsCount));
        System.out.printf ("  │  FSMA  total mem  : %s  │  avg: %s/test%n",
                formatBytes(fsmaTotalMemBytes),  formatBytes(fsmaTotalMemBytes  / perfTestsCount));
        System.out.printf ("  │  FFSM  total mem  : %s  │  avg: %s/test%n",
                formatBytes(ffsmTotalMemBytes),  formatBytes(ffsmTotalMemBytes  / perfTestsCount));
        System.out.println("  └────────────────────────────────────────────────────────────┘");
    }

    /**
     * Prints the comprehensive final performance ranking table after all tests.
     * Shows totals, averages, and the ranked order for all five algorithms.
     */
    static void printFinalPerformanceRanking() {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║           PERFORMANCE RANKING – FINAL SUMMARY (5 algorithms)        ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");

        if (perfTestsCount == 0) {
            System.out.println("║  No performance data collected (all runs crashed or were skipped).   ║");
            System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
            return;
        }

        System.out.printf("║  Tests measured          : %-43d║%n", perfTestsCount);
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");

        // ── runtime table ──────────────────────────────────────────────────────
        System.out.println("║  ⏱  TOTAL RUNTIME                                                    ║");
        System.out.printf("║    FSG   total : %-52s║%n", formatMs(fsgTotalTimeMs));
        System.out.printf("║    gSPAN total : %-52s║%n", formatMs(gspanTotalTimeMs));
        System.out.printf("║    FSP   total : %-52s║%n", formatMs(fspTotalTimeMs));
        System.out.printf("║    FSMA  total : %-52s║%n", formatMs(fsmaTotalTimeMs));
        System.out.printf("║    FFSM  total : %-52s║%n", formatMs(ffsmTotalTimeMs));
        System.out.printf("║    FSG   avg   : %-52s║%n", formatMs(fsgTotalTimeMs   / perfTestsCount) + " / test");
        System.out.printf("║    gSPAN avg   : %-52s║%n", formatMs(gspanTotalTimeMs / perfTestsCount) + " / test");
        System.out.printf("║    FSP   avg   : %-52s║%n", formatMs(fspTotalTimeMs   / perfTestsCount) + " / test");
        System.out.printf("║    FSMA  avg   : %-52s║%n", formatMs(fsmaTotalTimeMs  / perfTestsCount) + " / test");
        System.out.printf("║    FFSM  avg   : %-52s║%n", formatMs(ffsmTotalTimeMs  / perfTestsCount) + " / test");

        String[] timeRanked = buildRanking5(
                "FSG",   fsgTotalTimeMs,
                "gSPAN", gspanTotalTimeMs,
                "FSP",   fspTotalTimeMs,
                "FSMA",  fsmaTotalTimeMs,
                "FFSM",  ffsmTotalTimeMs,
                "ms", false);
        System.out.println("║                                                                       ║");
        System.out.printf("║    🏆 #1 (fastest)  : %-47s║%n", timeRanked[0]);
        System.out.printf("║       #2             : %-47s║%n", timeRanked[1]);
        System.out.printf("║       #3             : %-47s║%n", timeRanked[2]);
        System.out.printf("║       #4             : %-47s║%n", timeRanked[3]);
        System.out.printf("║       #5 (slowest)   : %-47s║%n", timeRanked[4]);
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");

        // ── memory table ───────────────────────────────────────────────────────
        System.out.println("║  🧠 TOTAL MEMORY USAGE                                               ║");
        System.out.printf("║    FSG   total : %-52s║%n", formatBytes(fsgTotalMemBytes));
        System.out.printf("║    gSPAN total : %-52s║%n", formatBytes(gspanTotalMemBytes));
        System.out.printf("║    FSP   total : %-52s║%n", formatBytes(fspTotalMemBytes));
        System.out.printf("║    FSMA  total : %-52s║%n", formatBytes(fsmaTotalMemBytes));
        System.out.printf("║    FFSM  total : %-52s║%n", formatBytes(ffsmTotalMemBytes));
        System.out.printf("║    FSG   avg   : %-52s║%n", formatBytes(fsgTotalMemBytes   / perfTestsCount) + " / test");
        System.out.printf("║    gSPAN avg   : %-52s║%n", formatBytes(gspanTotalMemBytes / perfTestsCount) + " / test");
        System.out.printf("║    FSP   avg   : %-52s║%n", formatBytes(fspTotalMemBytes   / perfTestsCount) + " / test");
        System.out.printf("║    FSMA  avg   : %-52s║%n", formatBytes(fsmaTotalMemBytes  / perfTestsCount) + " / test");
        System.out.printf("║    FFSM  avg   : %-52s║%n", formatBytes(ffsmTotalMemBytes  / perfTestsCount) + " / test");

        String[] memRanked = buildRanking5(
                "FSG",   fsgTotalMemBytes,
                "gSPAN", gspanTotalMemBytes,
                "FSP",   fspTotalMemBytes,
                "FSMA",  fsmaTotalMemBytes,
                "FFSM",  ffsmTotalMemBytes,
                "B", false);
        System.out.println("║                                                                       ║");
        System.out.printf("║    🏆 #1 (leanest)  : %-47s║%n", memRanked[0]);
        System.out.printf("║       #2             : %-47s║%n", memRanked[1]);
        System.out.printf("║       #3             : %-47s║%n", memRanked[2]);
        System.out.printf("║       #4             : %-47s║%n", memRanked[3]);
        System.out.printf("║       #5 (heaviest)  : %-47s║%n", memRanked[4]);
        System.out.println("╠═══════════════════════════════════════════════════════════════════════╣");

        // ── overall winner ─────────────────────────────────────────────────────
        // Award 5 points for #1 down to 1 point for #5 in each category
        int[] scores = new int[5]; // 0=FSG,1=gSPAN,2=FSP,3=FSMA,4=FFSM
        awardPoints5(scores, fsgTotalTimeMs, gspanTotalTimeMs, fspTotalTimeMs, fsmaTotalTimeMs, ffsmTotalTimeMs, false);
        awardPoints5(scores, fsgTotalMemBytes, gspanTotalMemBytes, fspTotalMemBytes, fsmaTotalMemBytes, ffsmTotalMemBytes, false);

        String[] names = {"FSG", "gSPAN", "FSP", "FSMA", "FFSM"};
        List<int[]> ranked = new ArrayList<>();
        for (int i = 0; i < 5; i++) ranked.add(new int[]{scores[i], i});
        ranked.sort((a, b) -> Integer.compare(b[0], a[0])); // descending score

        System.out.println("║  🎖  OVERALL RANKING (by cumulative points)                           ║");
        for (int rank = 0; rank < ranked.size(); rank++) {
            int algo = ranked.get(rank)[1];
            int pts  = ranked.get(rank)[0];
            System.out.printf("║    #%d %s  (%d/10 pts)%s║%n",
                    rank + 1, padRight(names[algo], 8), pts,
                    padRight("", 47 - names[algo].length() - 11));
        }
        System.out.println("╚═══════════════════════════════════════════════════════════════════════╝");
    }

    // ======================================================= ranking helpers

    /**
     * Awards 5/4/3/2/1 points to positions 1st–5th into the scores array for
     * five algorithms (lower value wins when {@code higherIsBetter} is false).
     */
    private static void awardPoints5(int[] scores,
                                      long valA, long valB, long valC, long valD, long valE,
                                      boolean higherIsBetter) {
        long[]    vals = {valA, valB, valC, valD, valE};
        List<int[]> idx = new ArrayList<>();
        for (int i = 0; i < 5; i++) idx.add(new int[]{i, (int) vals[i]});
        idx.sort((a, b) -> higherIsBetter
                ? Integer.compare(b[1], a[1])
                : Integer.compare(a[1], b[1]));
        int[] pts = {5, 4, 3, 2, 1};
        for (int r = 0; r < 5; r++) scores[idx.get(r)[0]] += pts[r];
    }

    /**
     * Awards 4/3/2/1 points to positions 1st–4th into the scores array for
     * four algorithms (kept for any callers that still use the four-algorithm variant).
     */
    private static void awardPoints4(int[] scores,
                                      long valA, long valB, long valC, long valD,
                                      boolean higherIsBetter) {
        long[]    vals = {valA, valB, valC, valD};
        List<int[]> idx = new ArrayList<>();
        for (int i = 0; i < 4; i++) idx.add(new int[]{i, (int) vals[i]});
        idx.sort((a, b) -> higherIsBetter
                ? Integer.compare(b[1], a[1])
                : Integer.compare(a[1], b[1]));
        int[] pts = {4, 3, 2, 1};
        for (int r = 0; r < 4; r++) scores[idx.get(r)[0]] += pts[r];
    }

    /**
     * Builds a five-element String array representing the ranked entries for five
     * algorithms (lower value wins when {@code higherIsBetter} is false).
     */
    static String[] buildRanking5(String nameA, long valA,
                                   String nameB, long valB,
                                   String nameC, long valC,
                                   String nameD, long valD,
                                   String nameE, long valE,
                                   String unit, boolean higherIsBetter) {
        String[] names = {nameA, nameB, nameC, nameD, nameE};
        long[]   vals  = {valA,  valB,  valC,  valD,  valE};

        Integer[] idx = {0, 1, 2, 3, 4};
        Arrays.sort(idx, (i, j) -> higherIsBetter
                ? Long.compare(vals[j], vals[i])
                : Long.compare(vals[i], vals[j]));

        String[] result = new String[5];
        for (int r = 0; r < 5; r++) {
            int    i      = idx[r];
            String valStr = unit.equals("B") ? formatBytes(vals[i]) : formatMs(vals[i]);
            if (r == 0) {
                result[r] = names[i] + "  (" + valStr + ")";
            } else {
                long   diff    = Math.abs(vals[i] - vals[idx[0]]);
                double pctDiff = pct(diff, vals[idx[0]]);
                String diffStr = unit.equals("B") ? formatBytes(diff) : diff + " ms";
                result[r] = String.format("%s  (%s, +%s = +%.1f%% vs #1)",
                        names[i], valStr, diffStr, pctDiff);
            }
        }
        return result;
    }

    /**
     * Builds a four-element String array representing the ranked entries for four
     * algorithms (kept for backward compatibility).
     */
    static String[] buildRanking4(String nameA, long valA,
                                   String nameB, long valB,
                                   String nameC, long valC,
                                   String nameD, long valD,
                                   String unit, boolean higherIsBetter) {
        String[] names = {nameA, nameB, nameC, nameD};
        long[]   vals  = {valA,  valB,  valC,  valD};

        Integer[] idx = {0, 1, 2, 3};
        Arrays.sort(idx, (i, j) -> higherIsBetter
                ? Long.compare(vals[j], vals[i])
                : Long.compare(vals[i], vals[j]));

        String[] result = new String[4];
        for (int r = 0; r < 4; r++) {
            int    i      = idx[r];
            String valStr = unit.equals("B") ? formatBytes(vals[i]) : formatMs(vals[i]);
            if (r == 0) {
                result[r] = names[i] + "  (" + valStr + ")";
            } else {
                long   diff    = Math.abs(vals[i] - vals[idx[0]]);
                double pctDiff = pct(diff, vals[idx[0]]);
                String diffStr = unit.equals("B") ? formatBytes(diff) : diff + " ms";
                result[r] = String.format("%s  (%s, +%s = +%.1f%% vs #1)",
                        names[i], valStr, diffStr, pctDiff);
            }
        }
        return result;
    }

    // ================================================================ helpers

    /** Percentage that {@code part} represents of {@code total}. */
    static double pct(long part, long total) {
        return total == 0 ? 0.0 : 100.0 * part / total;
    }

    /** Human-readable byte count (B / KB / MB / GB). */
    static String formatBytes(long bytes) {
        if (bytes < 0)          return "0 B";
        if (bytes < 1_024L)     return bytes + " B";
        if (bytes < 1_048_576L) return String.format("%.2f KB", bytes / 1_024.0);
        if (bytes < 1_073_741_824L) return String.format("%.2f MB", bytes / 1_048_576.0);
        return String.format("%.2f GB", bytes / 1_073_741_824.0);
    }

    /** Human-readable millisecond duration. */
    static String formatMs(long ms) {
        if (ms < 1_000)  return ms + " ms";
        if (ms < 60_000) return String.format("%.2f s", ms / 1_000.0);
        return String.format("%.2f min", ms / 60_000.0);
    }

    /** Right-pads a string with spaces to the requested width. */
    static String padRight(String s, int width) {
        if (s.length() >= width) return s;
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    /** Prints exception class, message, and stack trace to stderr. */
    static void printException(Exception e) {
        System.err.println("Exception: " + e.getClass().getName());
        System.err.println("Message: "   + e.getMessage());
        System.err.println("\nStack trace:");
        e.printStackTrace(System.err);
    }

    // ============================================================ graph generator

    static String generateGraphDatabase(Random rng) {
        int numGraphs = MIN_GRAPHS + rng.nextInt(MAX_GRAPHS - MIN_GRAPHS + 1);
        StringBuilder sb = new StringBuilder();

        for (int g = 0; g < numGraphs; g++) {
            int nv = MIN_VERTICES + rng.nextInt(MAX_VERTICES - MIN_VERTICES + 1);

            List<Integer> vlabels = new ArrayList<>(nv);
            List<int[]>   edges   = new ArrayList<>();

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
                for (int i = 0; i < nv; i++) order[i] = i;
                for (int i = nv - 1; i > 0; i--) {
                    int j = rng.nextInt(i + 1);
                    int tmp = order[i]; order[i] = order[j]; order[j] = tmp;
                }
                for (int i = 1; i < nv; i++) {
                    int u  = order[i];
                    int w  = order[rng.nextInt(i)];
                    int a  = Math.min(u, w), b = Math.max(u, w);
                    int el = 20 + rng.nextInt(NUM_EDGE_LABELS);
                    edges.add(new int[]{a, b, el});
                    sb.append("e ").append(a).append(" ").append(b).append(" ").append(el).append("\n");
                    inTree[a][b] = true;
                }
            }

            // 2) extra edges
            for (int u = 0; u < nv; u++) {
                for (int w = u + 1; w < nv; w++) {
                    if (inTree[u][w]) continue;
                    if (rng.nextDouble() < EDGE_PROBABILITY) {
                        int el = 20 + rng.nextInt(NUM_EDGE_LABELS);
                        edges.add(new int[]{u, w, el});
                        sb.append("e ").append(u).append(" ").append(w).append(" ").append(el).append("\n");
                    }
                }
            }

            validateGraphTransaction(g, nv, edges);
            sb.append("\n");
        }
        return sb.toString();
    }

    static void validateGraphTransaction(int gid, int nv, List<int[]> edges) {
        if (nv <= 0)
            throw new IllegalStateException("Graph " + gid + " has no vertices (nv=" + nv + ")");

        @SuppressWarnings("unchecked")
        List<Integer>[] adj = new List[nv];
        for (int i = 0; i < nv; i++) adj[i] = new ArrayList<>();

        Set<Long> seenPairs = new TreeSet<>();

        for (int[] e : edges) {
            if (e == null || e.length != 3)
                throw new IllegalStateException("Graph " + gid + " has a malformed edge record");
            int u = e[0], v = e[1];
            if (u < 0 || u >= nv || v < 0 || v >= nv)
                throw new IllegalStateException("Graph " + gid + " has edge out of bounds: (" + u + "," + v + ")");
            if (u == v)
                throw new IllegalStateException("Graph " + gid + " has self-loop on vertex " + u);
            if (u > v)
                throw new IllegalStateException(
                        "Graph " + gid + " has non-canonical edge order: e " + u + " " + v + " " + e[2]);
            long key = (((long) u) << 32) | (v & 0xffffffffL);
            if (!seenPairs.add(key))
                throw new IllegalStateException("Graph " + gid + " has duplicate edge between " + u + " and " + v);
            adj[u].add(v);
            adj[v].add(u);
        }

        boolean[] vis   = new boolean[nv];
        int       visited = 0;
        int[]     stack = new int[nv];
        int       top   = 0;
        stack[top++] = 0;
        vis[0] = true;
        while (top > 0) {
            int cur = stack[--top];
            visited++;
            for (int nxt : adj[cur])
                if (!vis[nxt]) { vis[nxt] = true; stack[top++] = nxt; }
        }
        if (visited != nv)
            throw new IllegalStateException(
                    "Graph " + gid + " is disconnected: visited " + visited + " / " + nv);
    }

    // =========================================================== output parser

    static class PatternInfo {
        int          patternId;
        int          support;
        List<Integer> vlabels  = new ArrayList<>();
        List<int[]>  edges    = new ArrayList<>();
        List<Integer> graphIds = new ArrayList<>();
        String       signature;
        String       rawText;
    }

    static Map<String, PatternInfo> parseOutputDetailed(String filename, String algoName)
            throws IOException {
        Map<String, PatternInfo> result = new LinkedHashMap<>();
        List<String> lines = Files.readAllLines(Paths.get(filename));

        PatternInfo current   = null;
        boolean     inPattern = false;

        for (String raw : lines) {
            String   line = raw.trim();
            if (line.isEmpty()) continue;
            String[] p    = line.split("\\s+");

            if (p[0].equals("t")) {
                if (current != null && inPattern) storePattern(result, current, algoName);
                current           = new PatternInfo();
                current.patternId = Integer.parseInt(p[2]);
                current.support   = (p.length >= 5) ? Integer.parseInt(p[4]) : 0;
                current.rawText   = line + "\n";
                inPattern         = true;

            } else if (p[0].equals("v") && inPattern) {
                current.vlabels.add(Integer.parseInt(p[2]));
                current.rawText += line + "\n";

            } else if (p[0].equals("e") && inPattern) {
                current.edges.add(new int[]{
                        Integer.parseInt(p[1]),
                        Integer.parseInt(p[2]),
                        Integer.parseInt(p[3])});
                current.rawText += line + "\n";

            } else if (p[0].equals("x") && inPattern) {
                for (int i = 1; i < p.length; i++)
                    current.graphIds.add(Integer.parseInt(p[i]));
                if (current.support == 0) current.support = p.length - 1;
                current.rawText += line + "\n";
            }
        }
        if (current != null && inPattern) storePattern(result, current, algoName);
        return result;
    }

    static void storePattern(Map<String, PatternInfo> result, PatternInfo pattern,
                              String algoName) throws IOException {
        pattern.signature = buildSignature(pattern.vlabels, pattern.edges);
        PatternInfo previous = result.putIfAbsent(pattern.signature, pattern);
        if (previous != null)
            throw new IOException(algoName + " produced duplicate isomorphic patterns: IDs "
                    + previous.patternId + " and " + pattern.patternId
                    + ", canonical signature = " + pattern.signature
                    + "\n--- first occurrence ---\n"  + previous.rawText
                    + "\n--- second occurrence ---\n" + pattern.rawText);
    }

    static String buildSignature(List<Integer> vlabels, List<int[]> edges) {
        int[]   labelArray = new int[vlabels.size()];
        for (int i = 0; i < vlabels.size(); i++) labelArray[i] = vlabels.get(i);

        int[][] edgeArray = new int[edges.size()][];
        for (int i = 0; i < edges.size(); i++) edgeArray[i] = edges.get(i).clone();

        FSGPattern.CanonResult canonical = FSGPattern.canonicalize(labelArray, edgeArray);
        return Arrays.toString(canonical.key);
    }

    // ============================================================ comparison
    // ComparisonResult now tracks mismatches across all FIVE algorithms.

    static class ComparisonResult {
        boolean identical = true;

        // ── patterns present in only one algorithm ────────────────────────────
        Set<String> onlyInFSG   = new TreeSet<>();
        Set<String> onlyInGSPAN = new TreeSet<>();
        Set<String> onlyInFSP   = new TreeSet<>();
        Set<String> onlyInFSMA  = new TreeSet<>();
        Set<String> onlyInFFSM  = new TreeSet<>();

        // ── patterns present in exactly two (missing from the other three) ────
        Set<String> inFSGandGSPAN_notRest    = new TreeSet<>();
        Set<String> inFSGandFSP_notRest      = new TreeSet<>();
        Set<String> inFSGandFSMA_notRest     = new TreeSet<>();
        Set<String> inFSGandFFSM_notRest     = new TreeSet<>();
        Set<String> inGSPANandFSP_notRest    = new TreeSet<>();
        Set<String> inGSPANandFSMA_notRest   = new TreeSet<>();
        Set<String> inGSPANandFFSM_notRest   = new TreeSet<>();
        Set<String> inFSPandFSMA_notRest     = new TreeSet<>();
        Set<String> inFSPandFFSM_notRest     = new TreeSet<>();
        Set<String> inFSMAandFFSM_notRest    = new TreeSet<>();

        // ── patterns present in exactly three (missing from the other two) ────
        Set<String> notInFSGandGSPAN         = new TreeSet<>();
        Set<String> notInFSGandFSP           = new TreeSet<>();
        Set<String> notInFSGandFSMA          = new TreeSet<>();
        Set<String> notInFSGandFFSM          = new TreeSet<>();
        Set<String> notInGSPANandFSP         = new TreeSet<>();
        Set<String> notInGSPANandFSMA        = new TreeSet<>();
        Set<String> notInGSPANandFFSM        = new TreeSet<>();
        Set<String> notInFSPandFSMA          = new TreeSet<>();
        Set<String> notInFSPandFFSM          = new TreeSet<>();
        Set<String> notInFSMAandFFSM         = new TreeSet<>();

        // ── patterns present in exactly four (missing from exactly one) ───────
        Set<String> missingOnlyFSG           = new TreeSet<>();
        Set<String> missingOnlyGSPAN         = new TreeSet<>();
        Set<String> missingOnlyFSP           = new TreeSet<>();
        Set<String> missingOnlyFSMA          = new TreeSet<>();
        Set<String> missingOnlyFFSM          = new TreeSet<>();

        // ── support mismatches (among patterns found by all five) ─────────────
        /** sig -> [fsgSup, gspanSup, fspSup, fsmaSup, ffsmSup] */
        Map<String, int[]> supportMismatch = new LinkedHashMap<>();
    }

    static ComparisonResult compareResults(Map<String, PatternInfo> fsg,
                                            Map<String, PatternInfo> gspan,
                                            Map<String, PatternInfo> fsp,
                                            Map<String, PatternInfo> fsma,
                                            Map<String, PatternInfo> ffsm) {
        ComparisonResult result = new ComparisonResult();

        Set<String> allSigs = new TreeSet<>();
        allSigs.addAll(fsg.keySet());
        allSigs.addAll(gspan.keySet());
        allSigs.addAll(fsp.keySet());
        allSigs.addAll(fsma.keySet());
        allSigs.addAll(ffsm.keySet());

        for (String sig : allSigs) {
            PatternInfo fInfo  = fsg.get(sig);
            PatternInfo gInfo  = gspan.get(sig);
            PatternInfo pInfo  = fsp.get(sig);
            PatternInfo mInfo  = fsma.get(sig);
            PatternInfo ffInfo = ffsm.get(sig);

            boolean hasFSG   = fInfo  != null;
            boolean hasGSPAN = gInfo  != null;
            boolean hasFSP   = pInfo  != null;
            boolean hasFSMA  = mInfo  != null;
            boolean hasFFSM  = ffInfo != null;

            int present = (hasFSG   ? 1 : 0)
                        + (hasGSPAN ? 1 : 0)
                        + (hasFSP   ? 1 : 0)
                        + (hasFSMA  ? 1 : 0)
                        + (hasFFSM  ? 1 : 0);

            if (present == 5) {
                // All five found it – check support agreement
                int fSup  = fInfo.support;
                int gSup  = gInfo.support;
                int pSup  = pInfo.support;
                int mSup  = mInfo.support;
                int ffSup = ffInfo.support;
                if (fSup != gSup || fSup != pSup || fSup != mSup || fSup != ffSup) {
                    result.supportMismatch.put(sig, new int[]{fSup, gSup, pSup, mSup, ffSup});
                    result.identical = false;
                }

            } else if (present == 4) {
                result.identical = false;
                if (!hasFSG)   result.missingOnlyFSG.add(sig);
                else if (!hasGSPAN) result.missingOnlyGSPAN.add(sig);
                else if (!hasFSP)   result.missingOnlyFSP.add(sig);
                else if (!hasFSMA)  result.missingOnlyFSMA.add(sig);
                else                result.missingOnlyFFSM.add(sig);

            } else if (present == 3) {
                result.identical = false;
                // record which two are absent
                boolean missFSG   = !hasFSG;
                boolean missGSPAN = !hasGSPAN;
                boolean missFSP   = !hasFSP;
                boolean missFSMA  = !hasFSMA;
                boolean missFFSM  = !hasFFSM;
                if      (missFSG   && missGSPAN) result.notInFSGandGSPAN.add(sig);
                else if (missFSG   && missFSP)   result.notInFSGandFSP.add(sig);
                else if (missFSG   && missFSMA)  result.notInFSGandFSMA.add(sig);
                else if (missFSG   && missFFSM)  result.notInFSGandFFSM.add(sig);
                else if (missGSPAN && missFSP)   result.notInGSPANandFSP.add(sig);
                else if (missGSPAN && missFSMA)  result.notInGSPANandFSMA.add(sig);
                else if (missGSPAN && missFFSM)  result.notInGSPANandFFSM.add(sig);
                else if (missFSP   && missFSMA)  result.notInFSPandFSMA.add(sig);
                else if (missFSP   && missFFSM)  result.notInFSPandFFSM.add(sig);
                else                             result.notInFSMAandFFSM.add(sig);

            } else if (present == 2) {
                result.identical = false;
                if      (hasFSG   && hasGSPAN) result.inFSGandGSPAN_notRest.add(sig);
                else if (hasFSG   && hasFSP)   result.inFSGandFSP_notRest.add(sig);
                else if (hasFSG   && hasFSMA)  result.inFSGandFSMA_notRest.add(sig);
                else if (hasFSG   && hasFFSM)  result.inFSGandFFSM_notRest.add(sig);
                else if (hasGSPAN && hasFSP)   result.inGSPANandFSP_notRest.add(sig);
                else if (hasGSPAN && hasFSMA)  result.inGSPANandFSMA_notRest.add(sig);
                else if (hasGSPAN && hasFFSM)  result.inGSPANandFFSM_notRest.add(sig);
                else if (hasFSP   && hasFSMA)  result.inFSPandFSMA_notRest.add(sig);
                else if (hasFSP   && hasFFSM)  result.inFSPandFFSM_notRest.add(sig);
                else                           result.inFSMAandFFSM_notRest.add(sig);

            } else {
                // Only one algorithm found this pattern
                result.identical = false;
                if      (hasFSG)   result.onlyInFSG.add(sig);
                else if (hasGSPAN) result.onlyInGSPAN.add(sig);
                else if (hasFSP)   result.onlyInFSP.add(sig);
                else if (hasFSMA)  result.onlyInFSMA.add(sig);
                else               result.onlyInFFSM.add(sig);
            }
        }
        return result;
    }

    static void printDetailedComparison(ComparisonResult comp,
                                         Map<String, PatternInfo> fsg,
                                         Map<String, PatternInfo> gspan,
                                         Map<String, PatternInfo> fsp,
                                         Map<String, PatternInfo> fsma,
                                         Map<String, PatternInfo> ffsm) {
        System.err.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.err.println("│                    DETAILED COMPARISON                          │");
        System.err.println("└─────────────────────────────────────────────────────────────────┘");

        System.err.printf("\nPattern count: FSG=%d, gSPAN=%d, FSP=%d, FSMA=%d, FFSM=%d\n",
                fsg.size(), gspan.size(), fsp.size(), fsma.size(), ffsm.size());

        // ── only in one ───────────────────────────────────────────────────────
        printOnlyIn(comp.onlyInFSG,   fsg,  "FSG");
        printOnlyIn(comp.onlyInGSPAN, gspan,"gSPAN");
        printOnlyIn(comp.onlyInFSP,   fsp,  "FSP");
        printOnlyIn(comp.onlyInFSMA,  fsma, "FSMA");
        printOnlyIn(comp.onlyInFFSM,  ffsm, "FFSM");

        // ── found by exactly two ──────────────────────────────────────────────
        printFoundByTwo(comp.inFSGandGSPAN_notRest,  fsg,  "FSG+gSPAN",  "FSP, FSMA, FFSM");
        printFoundByTwo(comp.inFSGandFSP_notRest,    fsg,  "FSG+FSP",    "gSPAN, FSMA, FFSM");
        printFoundByTwo(comp.inFSGandFSMA_notRest,   fsg,  "FSG+FSMA",   "gSPAN, FSP, FFSM");
        printFoundByTwo(comp.inFSGandFFSM_notRest,   fsg,  "FSG+FFSM",   "gSPAN, FSP, FSMA");
        printFoundByTwo(comp.inGSPANandFSP_notRest,  gspan,"gSPAN+FSP",  "FSG, FSMA, FFSM");
        printFoundByTwo(comp.inGSPANandFSMA_notRest, gspan,"gSPAN+FSMA", "FSG, FSP, FFSM");
        printFoundByTwo(comp.inGSPANandFFSM_notRest, gspan,"gSPAN+FFSM", "FSG, FSP, FSMA");
        printFoundByTwo(comp.inFSPandFSMA_notRest,   fsp,  "FSP+FSMA",   "FSG, gSPAN, FFSM");
        printFoundByTwo(comp.inFSPandFFSM_notRest,   fsp,  "FSP+FFSM",   "FSG, gSPAN, FSMA");
        printFoundByTwo(comp.inFSMAandFFSM_notRest,  fsma, "FSMA+FFSM",  "FSG, gSPAN, FSP");

        // ── found by exactly three (two missing) ──────────────────────────────
        printFoundByThree(comp.notInFSGandGSPAN,  fsp,  "FSG, gSPAN");
        printFoundByThree(comp.notInFSGandFSP,    gspan,"FSG, FSP");
        printFoundByThree(comp.notInFSGandFSMA,   gspan,"FSG, FSMA");
        printFoundByThree(comp.notInFSGandFFSM,   gspan,"FSG, FFSM");
        printFoundByThree(comp.notInGSPANandFSP,  fsg,  "gSPAN, FSP");
        printFoundByThree(comp.notInGSPANandFSMA, fsg,  "gSPAN, FSMA");
        printFoundByThree(comp.notInGSPANandFFSM, fsg,  "gSPAN, FFSM");
        printFoundByThree(comp.notInFSPandFSMA,   fsg,  "FSP, FSMA");
        printFoundByThree(comp.notInFSPandFFSM,   fsg,  "FSP, FFSM");
        printFoundByThree(comp.notInFSMAandFFSM,  fsg,  "FSMA, FFSM");

        // ── found by exactly four (one missing) ───────────────────────────────
        printMissingFromOne(comp.missingOnlyFSG,   gspan, "FSG");
        printMissingFromOne(comp.missingOnlyGSPAN, fsg,   "gSPAN");
        printMissingFromOne(comp.missingOnlyFSP,   fsg,   "FSP");
        printMissingFromOne(comp.missingOnlyFSMA,  fsg,   "FSMA");
        printMissingFromOne(comp.missingOnlyFFSM,  fsg,   "FFSM");

        // ── support mismatches ────────────────────────────────────────────────
        if (!comp.supportMismatch.isEmpty()) {
            System.err.printf("\n❌ %d pattern(s) with SUPPORT MISMATCH:\n",
                    comp.supportMismatch.size());
            int count = 0;
            for (Map.Entry<String, int[]> e : comp.supportMismatch.entrySet()) {
                String sig  = e.getKey();
                int[]  sups = e.getValue();
                PatternInfo fInfo  = fsg.get(sig);
                PatternInfo gInfo  = gspan.get(sig);
                PatternInfo pInfo  = fsp.get(sig);
                PatternInfo mInfo  = fsma.get(sig);
                PatternInfo ffInfo = ffsm.get(sig);

                System.err.printf("\n  Pattern #%d:\n", ++count);
                System.err.printf("    Signature: %s\n", sig);
                if (fInfo != null) {
                    System.err.printf("    Vertex labels: %s\n", fInfo.vlabels);
                    System.err.printf("    Edges: %s\n", edgesToString(fInfo.edges));
                }
                System.err.printf("    FSG   support=%d graphs: %s\n", sups[0],
                        fInfo  != null ? fInfo.graphIds  : "n/a");
                System.err.printf("    gSPAN support=%d graphs: %s\n", sups[1],
                        gInfo  != null ? gInfo.graphIds  : "n/a");
                System.err.printf("    FSP   support=%d graphs: %s\n", sups[2],
                        pInfo  != null ? pInfo.graphIds  : "n/a");
                System.err.printf("    FSMA  support=%d graphs: %s\n", sups[3],
                        mInfo  != null ? mInfo.graphIds  : "n/a");
                System.err.printf("    FFSM  support=%d graphs: %s\n", sups[4],
                        ffInfo != null ? ffInfo.graphIds : "n/a");
            }
        }
    }

    // ── comparison printing helpers ───────────────────────────────────────────

    private static void printOnlyIn(Set<String> sigs,
                                     Map<String, PatternInfo> src,
                                     String algoName) {
        if (sigs.isEmpty()) return;
        System.err.printf("\n❌ %d pattern(s) found ONLY by %s:\n", sigs.size(), algoName);
        int count = 0;
        for (String sig : sigs) {
            PatternInfo p = src.get(sig);
            System.err.printf("\n  Pattern #%d (support=%d):\n", ++count, p.support);
            System.err.printf("    Signature: %s\n",       sig);
            System.err.printf("    Vertex labels: %s\n",   p.vlabels);
            System.err.printf("    Edges: %s\n",           edgesToString(p.edges));
            System.err.printf("    Found in graphs: %s\n", p.graphIds);
        }
    }

    private static void printFoundByTwo(Set<String> sigs,
                                         Map<String, PatternInfo> src,
                                         String foundByLabel,
                                         String missingLabel) {
        if (sigs.isEmpty()) return;
        System.err.printf("\n❌ %d pattern(s) found by %s but NOT by %s:\n",
                sigs.size(), foundByLabel, missingLabel);
        int count = 0;
        for (String sig : sigs) {
            PatternInfo p = src.get(sig);
            System.err.printf("\n  Pattern #%d (support=%d):\n", ++count, p.support);
            System.err.printf("    Signature: %s\n",       sig);
            System.err.printf("    Vertex labels: %s\n",   p.vlabels);
            System.err.printf("    Edges: %s\n",           edgesToString(p.edges));
            System.err.printf("    Found in graphs: %s\n", p.graphIds);
        }
    }

    private static void printFoundByThree(Set<String> sigs,
                                           Map<String, PatternInfo> src,
                                           String missingLabel) {
        if (sigs.isEmpty()) return;
        System.err.printf("\n❌ %d pattern(s) NOT found by %s (but found by the other three):\n",
                sigs.size(), missingLabel);
        int count = 0;
        for (String sig : sigs) {
            PatternInfo p = src.get(sig);
            System.err.printf("\n  Pattern #%d (support=%d):\n", ++count, p.support);
            System.err.printf("    Signature: %s\n",       sig);
            System.err.printf("    Vertex labels: %s\n",   p.vlabels);
            System.err.printf("    Edges: %s\n",           edgesToString(p.edges));
            System.err.printf("    Found in graphs: %s\n", p.graphIds);
        }
    }

    private static void printMissingFromOne(Set<String> sigs,
                                             Map<String, PatternInfo> src,
                                             String missingAlgoName) {
        if (sigs.isEmpty()) return;
        System.err.printf("\n❌ %d pattern(s) NOT found by %s (found by the other four):\n",
                sigs.size(), missingAlgoName);
        int count = 0;
        for (String sig : sigs) {
            PatternInfo p = src.get(sig);
            System.err.printf("\n  Pattern #%d (support=%d):\n", ++count, p.support);
            System.err.printf("    Signature: %s\n",       sig);
            System.err.printf("    Vertex labels: %s\n",   p.vlabels);
            System.err.printf("    Edges: %s\n",           edgesToString(p.edges));
            System.err.printf("    Found in graphs: %s\n", p.graphIds);
        }
    }

    static String edgesToString(List<int[]> edges) {
        return edges.stream()
                .map(e -> String.format("(%d-%d:%d)", e[0], e[1], e[2]))
                .collect(Collectors.joining(", "));
    }

    static void printGraphDatabaseSummary(String graphDb) {
        String[] lines    = graphDb.split("\n");
        int graphCount    = 0, totalVertices = 0, totalEdges = 0;
        for (String line : lines) {
            String t = line.trim();
            if      (t.startsWith("t #")) graphCount++;
            else if (t.startsWith("v "))  totalVertices++;
            else if (t.startsWith("e "))  totalEdges++;
        }
        System.out.printf("  Graphs: %d, Vertices: %d, Edges: %d\n",
                graphCount, totalVertices, totalEdges);
    }

    static void printFullDebugInfo(int testIdx, long seed, String input,
                                    String fsgOut, String gspanOut,
                                    String fspOut,  String fsmaOut, String ffsmOut,
                                    ComparisonResult comp) {
        System.err.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.err.println("│                    FULL DEBUG OUTPUT                            │");
        System.err.println("└─────────────────────────────────────────────────────────────────┘");
        System.err.println("\n▼▼▼ INPUT GRAPH DATABASE ▼▼▼");
        System.err.println(input);
        System.err.println("\n▼▼▼ FSG OUTPUT ▼▼▼");
        System.err.println(fsgOut);
        System.err.println("\n▼▼▼ gSPAN OUTPUT ▼▼▼");
        System.err.println(gspanOut);
        System.err.println("\n▼▼▼ FSP OUTPUT ▼▼▼");
        System.err.println(fspOut);
        System.err.println("\n▼▼▼ FSMA OUTPUT ▼▼▼");
        System.err.println(fsmaOut);
        System.err.println("\n▼▼▼ FFSM OUTPUT ▼▼▼");
        System.err.println(ffsmOut);
    }

    static void printDebugFooter(int testIdx, long seed) {
        System.err.println("\n┌─────────────────────────────────────────────────────────────────┐");
        System.err.println("│                    DEBUG INFORMATION                            │");
        System.err.println("└─────────────────────────────────────────────────────────────────┘");
        System.err.printf("Test index : %d\n", testIdx);
        System.err.printf("Seed       : %d\n", seed);
        System.err.printf("To reproduce: use seed %d\n", seed);
        System.err.printf("Files saved to: %s\n", FAIL_DIR);
        System.err.printf("  - test_%d_seed_%d_input.txt\n",  testIdx, seed);
        System.err.printf("  - test_%d_seed_%d_fsg.txt\n",    testIdx, seed);
        System.err.printf("  - test_%d_seed_%d_gspan.txt\n",  testIdx, seed);
        System.err.printf("  - test_%d_seed_%d_fsp.txt\n",    testIdx, seed);
        System.err.printf("  - test_%d_seed_%d_fsma.txt\n",   testIdx, seed);
        System.err.printf("  - test_%d_seed_%d_ffsm.txt\n",   testIdx, seed);
    }

    // ============================================================ I/O helpers

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
     * Saves failure artefacts for all five algorithms to {@link #FAIL_DIR}.
     */
    static void saveFailure(int testIdx, long seed,
                             String input,
                             String fsgOut, String gspanOut,
                             String fspOut,  String fsmaOut, String ffsmOut,
                             String reason) {
        try {
            String base = FAIL_DIR + File.separator
                    + "test_" + testIdx + "_seed_" + seed;

            writeFile(base + "_input.txt",
                    String.format("# Test: %d\n# Seed: %d\n# Reason: %s\n# Support: %.2f\n\n%s",
                            testIdx, seed, reason, MIN_SUPPORT, input));
            writeFile(base + "_fsg.txt",   fsgOut);
            writeFile(base + "_gspan.txt", gspanOut);
            writeFile(base + "_fsp.txt",   fspOut);
            writeFile(base + "_fsma.txt",  fsmaOut);
            writeFile(base + "_ffsm.txt",  ffsmOut);

            StringBuilder summary = new StringBuilder();
            summary.append(String.format("Test %d failed (seed %d)\n", testIdx, seed));
            summary.append(String.format("Reason: %s\n\n", reason));
            summary.append("Input database summary:\n");
            summary.append(getSummary(input));
            writeFile(base + "_summary.txt", summary.toString());

        } catch (IOException ex) {
            System.err.println("⚠ Could not save failure files: " + ex.getMessage());
        }
    }

    static String getSummary(String graphDb) {
        StringBuilder sb = new StringBuilder();
        String[] lines = graphDb.split("\n");
        int currentGraph = -1, vertices = 0, edges = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("t #")) {
                if (currentGraph >= 0)
                    sb.append(String.format("  Graph %d: %d vertices, %d edges\n",
                            currentGraph, vertices, edges));
                currentGraph = Integer.parseInt(trimmed.split("\\s+")[2]);
                vertices = 0; edges = 0;
            } else if (trimmed.startsWith("v ")) {
                vertices++;
            } else if (trimmed.startsWith("e ")) {
                edges++;
            }
        }
        if (currentGraph >= 0)
            sb.append(String.format("  Graph %d: %d vertices, %d edges\n",
                    currentGraph, vertices, edges));
        return sb.toString();
    }
}