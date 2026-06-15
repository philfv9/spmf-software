package ca.pfv.spmf.algorithms.frequentpatterns.uhmine;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see http://www.gnu.org/licenses/.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import ca.pfv.spmf.algorithms.frequentpatterns.uapriori.AlgoUApriori;
import ca.pfv.spmf.algorithms.frequentpatterns.uapriori.UncertainTransactionDatabase;
import ca.pfv.spmf.algorithms.frequentpatterns.uveclat.AlgoUVEclat;

/**
 * This class performs correctness testing of the UH-Mine algorithm by
 * comparing its output against both U-Apriori and UV-Eclat on randomly
 * generated uncertain transaction databases.
 *
 * All three algorithms implement the same expected-support semantics:
 * expSup(X, DB) = sum_i product_{x in X} P(x, t_i)
 * and must therefore return exactly the same set of frequent itemsets
 * (same items, same expected support up to floating-point rounding)
 * for the same database and minsup threshold.
 *
 * The test suite runs NUM_TESTS randomised trials. Each trial:
 * 1. Generates a random uncertain transaction database.
 * 2. Selects a random minsup threshold.
 * 3. Runs U-Apriori, UV-Eclat and UH-Mine on the database.
 * 4. Parses all three output files into sets of ParsedItemset objects.
 * 5. Compares the three result sets and reports PASS or FAIL.
 *
 * Extreme cases deliberately included:
 * - Empty database.
 * - Single-transaction database.
 * - Single-item database.
 * - All probabilities equal to 1.0 (deterministic data).
 * - All probabilities close to 0.0.
 * - minsup = 0.0 (every itemset is frequent).
 * - minsup extremely large (nothing is frequent).
 * - Dense database (every item in every transaction).
 * - Example 1 from the UH-Mine / UV-Eclat paper.
 *
 * Based on:
 * Aggarwal et al. (2009) Frequent Pattern Mining with Uncertain Data. KDD 2009.
 * Chui, Kao, Hung (2007) Mining Frequent Itemsets from Uncertain Data. PAKDD 2007.
 * Leung and Sun (2011) Equivalence Class Transformation Based Mining. ACM SAC 2011.
 *
 * @see AlgoUHMine
 * @see AlgoUVEclat
 * @see AlgoUApriori
 * @see UncertainTransactionDatabase
 * @author Philippe Fournier-Viger
 */
public class TestCorrectnessUHMineVsUApriori {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------

    /** Total number of random test trials to execute */
    private static final int NUM_TESTS = 1000;

    /**
     * Number of decimal places to which expected support values are rounded
     * before comparison, to absorb floating-point differences caused by
     * different multiplication orders in the three algorithms.
     */
    private static final int ROUNDING_DECIMALS = 5;

    /** Temporary output file for U-Apriori */
    private static final String UAPRIORI_OUTPUT = "temp_uapriori_output.txt";

    /** Temporary output file for UV-Eclat */
    private static final String UVECLAT_OUTPUT = "temp_uveclat_output.txt";

    /** Temporary output file for UH-Mine */
    private static final String UHMINE_OUTPUT = "temp_uhmine_output.txt";

    /** Temporary file for the generated database */
    private static final String DB_FILE = "temp_test_database.txt";

    // -----------------------------------------------------------------------
    // Inner class - ParsedItemset
    // -----------------------------------------------------------------------

    /**
     * Represents a parsed frequent itemset read from an algorithm output file.
     * Stores the sorted list of item IDs and the expected support rounded to
     * ROUNDING_DECIMALS decimal places.
     * Two ParsedItemset objects are equal when they have the same sorted item
     * list and the same rounded expected support.
     */
    private static class ParsedItemset {

        /** sorted list of item identifiers */
        final List<Integer> items;

        /** expected support rounded to ROUNDING_DECIMALS decimal places */
        final double roundedSupport;

        /**
         * Constructor.
         *
         * @param items          item identifiers (sorted internally)
         * @param roundedSupport rounded expected support
         */
        ParsedItemset(List<Integer> items, double roundedSupport) {
            this.items = new ArrayList<Integer>(items);
            Collections.sort(this.items);
            this.roundedSupport = roundedSupport;
        }

        /** @return true if the other object has the same items and support */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ParsedItemset)) return false;
            ParsedItemset other = (ParsedItemset) o;
            return Double.compare(this.roundedSupport, other.roundedSupport) == 0
                    && this.items.equals(other.items);
        }

        /** @return hash code based on items and rounded support */
        @Override
        public int hashCode() {
            int result = items.hashCode();
            long bits = Double.doubleToLongBits(roundedSupport);
            result = 31 * result + (int) (bits ^ (bits >>> 32));
            return result;
        }

        /** @return string representation */
        @Override
        public String toString() {
            return items + " #SUP: " + roundedSupport;
        }
    }

    // -----------------------------------------------------------------------
    // Inner class - RandomDatabase
    // -----------------------------------------------------------------------

    /**
     * Holds the transactions of a randomly generated uncertain database.
     * Each transaction is represented as parallel lists of item IDs and
     * existential probabilities.
     */
    private static class RandomDatabase {

        /** item IDs per transaction */
        final List<List<Integer>> transactionItems;

        /** existential probabilities per transaction (aligned with items) */
        final List<List<Double>> transactionProbs;

        /** number of transactions */
        final int numTransactions;

        /** number of distinct items */
        final int numDistinctItems;

        /**
         * Constructor.
         *
         * @param transactionItems item IDs per transaction
         * @param transactionProbs probabilities per transaction
         * @param numDistinctItems number of distinct items
         */
        RandomDatabase(List<List<Integer>> transactionItems,
                       List<List<Double>> transactionProbs,
                       int numDistinctItems) {
            this.transactionItems = transactionItems;
            this.transactionProbs = transactionProbs;
            this.numTransactions = transactionItems.size();
            this.numDistinctItems = numDistinctItems;
        }
    }

    // -----------------------------------------------------------------------
    // Inner enum - TestResult
    // -----------------------------------------------------------------------

    /** Possible outcomes of a single test trial */
    private enum TestResult {
        PASS, FAIL, ERROR
    }

    // -----------------------------------------------------------------------
    // Inner class - TestCase
    // -----------------------------------------------------------------------

    /**
     * Container for a hand-crafted extreme test case: a database, a minsup
     * threshold, and a human-readable description.
     */
    private static class TestCase {

        /** the database */
        final RandomDatabase db;

        /** minimum expected support threshold */
        final double minsup;

        /** human-readable description */
        final String description;

        /**
         * Constructor.
         *
         * @param db          the database
         * @param minsup      the minsup threshold
         * @param description human-readable description
         */
        TestCase(RandomDatabase db, double minsup, String description) {
            this.db = db;
            this.minsup = minsup;
            this.description = description;
        }
    }

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------

    /**
     * Entry point. Runs NUM_TESTS correctness trials and prints a summary.
     *
     * @param args command-line arguments (not used)
     */
    public static void main(String[] args) {
        new TestCorrectnessUHMineVsUApriori().runAllTests();
    }

    // -----------------------------------------------------------------------
    // Test runner
    // -----------------------------------------------------------------------

    /**
     * Run all NUM_TESTS trials (extreme cases first, then random) and print
     * a final summary comparing U-Apriori, UV-Eclat and UH-Mine.
     */
    public void runAllTests() {
        Random rng = new Random(42);
        int passed = 0;
        int failed = 0;
        int errors = 0;
        int testNumber = 0;

        System.out.println(
                "======================================================");
        System.out.println(
                " Correctness test: UH-Mine vs U-Apriori vs UV-Eclat");
        System.out.println(
                " Number of trials : " + NUM_TESTS);
        System.out.println(
                "======================================================");

        // extreme / hand-crafted cases
        List<TestCase> extremeCases = buildExtremeCases();
        for (TestCase tc : extremeCases) {
            testNumber++;
            System.out.printf("Test %3d [EXTREME - %s] ... ",
                    testNumber, tc.description);
            System.out.flush();
            TestResult r = runSingleTest(tc.db, tc.minsup);
            if (r == TestResult.PASS) {
                passed++;
                System.out.println("PASS");
            } else if (r == TestResult.FAIL) {
                failed++;
                System.out.println("FAIL  <-- MISMATCH");
            } else {
                errors++;
                System.out.println("ERROR");
            }
        }

        // fully random cases
        while (testNumber < NUM_TESTS) {
            testNumber++;
            RandomDatabase db = generateRandomDatabase(rng);
            double minsup = generateRandomMinsup(rng, db);

            System.out.printf(
                    "Test %3d [RANDOM - %2d tx, %2d items, minsup=%.4f] ... ",
                    testNumber, db.numTransactions, db.numDistinctItems, minsup);
            System.out.flush();

            TestResult r = runSingleTest(db, minsup);
            if (r == TestResult.PASS) {
                passed++;
                System.out.println("PASS");
            } else if (r == TestResult.FAIL) {
                failed++;
                System.out.println("FAIL  <-- MISMATCH");
            } else {
                errors++;
                System.out.println("ERROR");
            }
        }

        // summary
        System.out.println(
                "======================================================");
        System.out.println(" Results:");
        System.out.println("   PASS  : " + passed);
        System.out.println("   FAIL  : " + failed);
        System.out.println("   ERROR : " + errors);
        System.out.println("   TOTAL : " + NUM_TESTS);
        if (failed == 0 && errors == 0) {
            System.out.println(
                    " --> ALL TESTS PASSED."
                    + " UH-Mine matches U-Apriori and UV-Eclat on every trial.");
        } else {
            System.out.println(
                    " --> SOME TESTS FAILED OR ERRORED. See details above.");
        }
        System.out.println(
                "======================================================");

        // clean up temporary files
        new File(UAPRIORI_OUTPUT).delete();
        new File(UVECLAT_OUTPUT).delete();
        new File(UHMINE_OUTPUT).delete();
        new File(DB_FILE).delete();
    }

    // -----------------------------------------------------------------------
    // Single test execution
    // -----------------------------------------------------------------------

    /**
     * Execute one test trial: write the database, run all three algorithms,
     * parse the outputs and compare them pairwise.
     *
     * @param db     the uncertain database to mine
     * @param minsup the minimum expected support threshold
     * @return PASS if all three agree, FAIL on mismatch, ERROR on exception
     */
    private TestResult runSingleTest(RandomDatabase db, double minsup) {
        try {
            // write database to disk in SPMF uncertain-DB format
            writeDatabaseToFile(db, DB_FILE);

            // load via SPMF's own loader (shared by UApriori and UVEclat)
            UncertainTransactionDatabase uncertainDB =
                    new UncertainTransactionDatabase();
            uncertainDB.loadFile(DB_FILE);

            // load via UHMine's own loader
            UncertainTransactionDatabaseUHMine uhMineDB =
                    new UncertainTransactionDatabaseUHMine();
            uhMineDB.loadFile(DB_FILE);

            // run U-Apriori
            AlgoUApriori uApriori = new AlgoUApriori(uncertainDB);
            uApriori.runAlgorithm(minsup, UAPRIORI_OUTPUT);

            // run UV-Eclat
            AlgoUVEclat uvEclat = new AlgoUVEclat(uncertainDB);
            uvEclat.runAlgorithm(minsup, UVECLAT_OUTPUT);

            // run UH-Mine
            AlgoUHMine uhMine = new AlgoUHMine();
            uhMine.runAlgorithm(uhMineDB, minsup, UHMINE_OUTPUT);

            // parse all three output files
            Set<ParsedItemset> uAprioriResult = parseOutputFile(UAPRIORI_OUTPUT);
            Set<ParsedItemset> uvEclatResult  = parseOutputFile(UVECLAT_OUTPUT);
            Set<ParsedItemset> uhMineResult   = parseOutputFile(UHMINE_OUTPUT);

            // compare: all three must agree
            boolean uaVsUv = uAprioriResult.equals(uvEclatResult);
            boolean uaVsUh = uAprioriResult.equals(uhMineResult);

            if (uaVsUv && uaVsUh) {
                return TestResult.PASS;
            } else {
                printMismatchDetails(uAprioriResult, uvEclatResult,
                        uhMineResult, db, minsup);
                return TestResult.FAIL;
            }

        } catch (Exception e) {
            System.out.println("\n   Exception: " + e.getMessage());
            e.printStackTrace();
            return TestResult.ERROR;
        }
    }

    // -----------------------------------------------------------------------
    // Database generation
    // -----------------------------------------------------------------------

    /**
     * Generate a random uncertain transaction database.
     * Dimensions: 1-30 transactions, 1-15 distinct items.
     * Each item appears in a given transaction with probability 0.6,
     * and its existential probability is sampled uniformly from (0.01, 1.0].
     *
     * @param rng the random number generator
     * @return the generated database
     */
    private RandomDatabase generateRandomDatabase(Random rng) {
        int numTransactions = 1 + rng.nextInt(30);
        int numDistinctItems = 1 + rng.nextInt(15);

        List<List<Integer>> allItems = new ArrayList<List<Integer>>();
        List<List<Double>> allProbs = new ArrayList<List<Double>>();

        for (int t = 0; t < numTransactions; t++) {
            List<Integer> tItems = new ArrayList<Integer>();
            List<Double> tProbs = new ArrayList<Double>();
            for (int item = 1; item <= numDistinctItems; item++) {
                if (rng.nextDouble() < 0.6) {
                    tItems.add(item);
                    tProbs.add(0.01 + rng.nextDouble() * 0.99);
                }
            }
            allItems.add(tItems);
            allProbs.add(tProbs);
        }
        return new RandomDatabase(allItems, allProbs, numDistinctItems);
    }

    /**
     * Generate a random minsup threshold in [0, 0.8 * numTransactions].
     *
     * @param rng the random number generator
     * @param db  the database (used to scale the threshold)
     * @return a non-negative minsup value
     */
    private double generateRandomMinsup(Random rng, RandomDatabase db) {
        double upper = Math.max(0.01, db.numTransactions * 0.8);
        return rng.nextDouble() * upper;
    }

    // -----------------------------------------------------------------------
    // Extreme / hand-crafted test cases
    // -----------------------------------------------------------------------

    /**
     * Build the list of hand-crafted extreme test cases targeting boundary
     * conditions most likely to expose correctness bugs.
     *
     * @return the list of extreme test cases
     */
    private List<TestCase> buildExtremeCases() {
        List<TestCase> cases = new ArrayList<TestCase>();

        // Case 1: Empty database
        cases.add(new TestCase(
                new RandomDatabase(
                        new ArrayList<List<Integer>>(),
                        new ArrayList<List<Double>>(),
                        0),
                1.0,
                "empty database"));

        // Case 2: Single transaction, single item, P=1.0
        {
            List<List<Integer>> items = new ArrayList<List<Integer>>();
            List<List<Double>> probs = new ArrayList<List<Double>>();
            List<Integer> t1i = new ArrayList<Integer>();
            List<Double> t1p = new ArrayList<Double>();
            t1i.add(1); t1p.add(1.0);
            items.add(t1i); probs.add(t1p);
            cases.add(new TestCase(
                    new RandomDatabase(items, probs, 1),
                    0.5,
                    "single tx single item P=1.0"));
        }

        // Case 3: Single transaction, 8 items, all P=1.0
        {
            List<List<Integer>> items = new ArrayList<List<Integer>>();
            List<List<Double>> probs = new ArrayList<List<Double>>();
            List<Integer> t1i = new ArrayList<Integer>();
            List<Double> t1p = new ArrayList<Double>();
            for (int i = 1; i <= 8; i++) { t1i.add(i); t1p.add(1.0); }
            items.add(t1i); probs.add(t1p);
            cases.add(new TestCase(
                    new RandomDatabase(items, probs, 8),
                    0.5,
                    "single tx 8 items all P=1.0"));
        }

        // Case 4: 10 transactions, single item, varying P
        {
            List<List<Integer>> items = new ArrayList<List<Integer>>();
            List<List<Double>> probs = new ArrayList<List<Double>>();
            for (int t = 0; t < 10; t++) {
                List<Integer> ti = new ArrayList<Integer>();
                List<Double> tp = new ArrayList<Double>();
                ti.add(1); tp.add(0.3 + t * 0.05);
                items.add(ti); probs.add(tp);
            }
            cases.add(new TestCase(
                    new RandomDatabase(items, probs, 1),
                    1.0,
                    "10 tx single item varying P"));
        }

        // Case 5: All probabilities = 1.0 (deterministic)
        {
            List<List<Integer>> items = new ArrayList<List<Integer>>();
            List<List<Double>> probs = new ArrayList<List<Double>>();
            int[][] raw = {{1, 2, 3}, {2, 3, 4}, {1, 3, 4, 5}};
            for (int[] row : raw) {
                List<Integer> ti = new ArrayList<Integer>();
                List<Double> tp = new ArrayList<Double>();
                for (int id : row) { ti.add(id); tp.add(1.0); }
                items.add(ti); probs.add(tp);
            }
            cases.add(new TestCase(
                    new RandomDatabase(items, probs, 5),
                    1.5,
                    "all P=1.0 deterministic"));
        }

        // Case 6: All probabilities very small
        {
            List<List<Integer>> items = new ArrayList<List<Integer>>();
            List<List<Double>> probs = new ArrayList<List<Double>>();
            for (int t = 0; t < 5; t++) {
                List<Integer> ti = new ArrayList<Integer>();
                List<Double> tp = new ArrayList<Double>();
                ti.add(1); tp.add(0.01);
                ti.add(2); tp.add(0.02);
                items.add(ti); probs.add(tp);
            }
            cases.add(new TestCase(
                    new RandomDatabase(items, probs, 2),
                    0.001,
                    "all probabilities near 0"));
        }

        // Case 7: minsup = 0.0 (everything is frequent)
        {
            List<List<Integer>> items = new ArrayList<List<Integer>>();
            List<List<Double>> probs = new ArrayList<List<Double>>();
            int[][] raw = {{1, 2}, {2, 3}, {1, 3}};
            for (int[] row : raw) {
                List<Integer> ti = new ArrayList<Integer>();
                List<Double> tp = new ArrayList<Double>();
                for (int id : row) { ti.add(id); tp.add(0.5); }
                items.add(ti); probs.add(tp);
            }
            cases.add(new TestCase(
                    new RandomDatabase(items, probs, 3),
                    0.0,
                    "minsup=0.0 all frequent"));
        }

        // Case 8: minsup extremely large (nothing frequent)
        {
            List<List<Integer>> items = new ArrayList<List<Integer>>();
            List<List<Double>> probs = new ArrayList<List<Double>>();
            int[][] raw = {{1, 2}, {2, 3}};
            for (int[] row : raw) {
                List<Integer> ti = new ArrayList<Integer>();
                List<Double> tp = new ArrayList<Double>();
                for (int id : row) { ti.add(id); tp.add(0.5); }
                items.add(ti); probs.add(tp);
            }
            cases.add(new TestCase(
                    new RandomDatabase(items, probs, 3),
                    9999.0,
                    "minsup very large nothing frequent"));
        }

        // Case 9: Dense database - every item in every transaction
        {
            List<List<Integer>> items = new ArrayList<List<Integer>>();
            List<List<Double>> probs = new ArrayList<List<Double>>();
            for (int t = 0; t < 5; t++) {
                List<Integer> ti = new ArrayList<Integer>();
                List<Double> tp = new ArrayList<Double>();
                for (int i = 1; i <= 5; i++) {
                    ti.add(i); tp.add(0.6 + i * 0.05);
                }
                items.add(ti); probs.add(tp);
            }
            cases.add(new TestCase(
                    new RandomDatabase(items, probs, 5),
                    1.5,
                    "dense DB every item in every tx"));
        }

        // Case 10: Example from the UH-Mine paper (Figure 1)
        // t1={c:0.7, d:0.8, e:0.7, g:0.6}
        // t2={a:0.8, c:0.7, d:0.6, e:0.9}
        // t3={a:0.8, c:0.7, d:0.8}
        // t4={a:0.7, d:0.6, e:0.8, g:0.8}
        // Items: a=1, c=2, d=3, e=4, g=5
        {
            List<List<Integer>> items = new ArrayList<List<Integer>>();
            List<List<Double>> probs = new ArrayList<List<Double>>();

            List<Integer> t1i = new ArrayList<Integer>();
            List<Double> t1p = new ArrayList<Double>();
            t1i.add(2); t1p.add(0.7);
            t1i.add(3); t1p.add(0.8);
            t1i.add(4); t1p.add(0.7);
            t1i.add(5); t1p.add(0.6);
            items.add(t1i); probs.add(t1p);

            List<Integer> t2i = new ArrayList<Integer>();
            List<Double> t2p = new ArrayList<Double>();
            t2i.add(1); t2p.add(0.8);
            t2i.add(2); t2p.add(0.7);
            t2i.add(3); t2p.add(0.6);
            t2i.add(4); t2p.add(0.9);
            items.add(t2i); probs.add(t2p);

            List<Integer> t3i = new ArrayList<Integer>();
            List<Double> t3p = new ArrayList<Double>();
            t3i.add(1); t3p.add(0.8);
            t3i.add(2); t3p.add(0.7);
            t3i.add(3); t3p.add(0.8);
            items.add(t3i); probs.add(t3p);

            List<Integer> t4i = new ArrayList<Integer>();
            List<Double> t4p = new ArrayList<Double>();
            t4i.add(1); t4p.add(0.7);
            t4i.add(3); t4p.add(0.6);
            t4i.add(4); t4p.add(0.8);
            t4i.add(5); t4p.add(0.8);
            items.add(t4i); probs.add(t4p);

            cases.add(new TestCase(
                    new RandomDatabase(items, probs, 5),
                    1.0,
                    "paper Figure 1 example minsup=1.0"));
        }

        // Case 11: Two identical transactions
        {
            List<List<Integer>> items = new ArrayList<List<Integer>>();
            List<List<Double>> probs = new ArrayList<List<Double>>();
            for (int t = 0; t < 2; t++) {
                List<Integer> ti = new ArrayList<Integer>();
                List<Double> tp = new ArrayList<Double>();
                ti.add(1); tp.add(0.8);
                ti.add(2); tp.add(0.6);
                items.add(ti); probs.add(tp);
            }
            cases.add(new TestCase(
                    new RandomDatabase(items, probs, 2),
                    0.5,
                    "two identical transactions"));
        }

        // Case 12: Disjoint transactions - one item per transaction
        {
            List<List<Integer>> items = new ArrayList<List<Integer>>();
            List<List<Double>> probs = new ArrayList<List<Double>>();
            for (int t = 1; t <= 5; t++) {
                List<Integer> ti = new ArrayList<Integer>();
                List<Double> tp = new ArrayList<Double>();
                ti.add(t); tp.add(0.9);
                items.add(ti); probs.add(tp);
            }
            cases.add(new TestCase(
                    new RandomDatabase(items, probs, 5),
                    0.5,
                    "disjoint tx one item each"));
        }

        // Case 13: contextUncertain.txt example (minsup = 0.10)
        // t1: 1(0.5) 2(0.4) 4(0.3) 5(0.7)
        // t2: 2(0.5) 3(0.4) 5(0.4)
        // t3: 1(0.6) 2(0.5) 4(0.1) 5(0.5)
        // t4: 1(0.7) 2(0.4) 3(0.3) 5(0.9)
        {
            List<List<Integer>> items = new ArrayList<List<Integer>>();
            List<List<Double>> probs = new ArrayList<List<Double>>();

            List<Integer> t1i = new ArrayList<Integer>();
            List<Double> t1p = new ArrayList<Double>();
            t1i.add(1); t1p.add(0.5);
            t1i.add(2); t1p.add(0.4);
            t1i.add(4); t1p.add(0.3);
            t1i.add(5); t1p.add(0.7);
            items.add(t1i); probs.add(t1p);

            List<Integer> t2i = new ArrayList<Integer>();
            List<Double> t2p = new ArrayList<Double>();
            t2i.add(2); t2p.add(0.5);
            t2i.add(3); t2p.add(0.4);
            t2i.add(5); t2p.add(0.4);
            items.add(t2i); probs.add(t2p);

            List<Integer> t3i = new ArrayList<Integer>();
            List<Double> t3p = new ArrayList<Double>();
            t3i.add(1); t3p.add(0.6);
            t3i.add(2); t3p.add(0.5);
            t3i.add(4); t3p.add(0.1);
            t3i.add(5); t3p.add(0.5);
            items.add(t3i); probs.add(t3p);

            List<Integer> t4i = new ArrayList<Integer>();
            List<Double> t4p = new ArrayList<Double>();
            t4i.add(1); t4p.add(0.7);
            t4i.add(2); t4p.add(0.4);
            t4i.add(3); t4p.add(0.3);
            t4i.add(5); t4p.add(0.9);
            items.add(t4i); probs.add(t4p);

            cases.add(new TestCase(
                    new RandomDatabase(items, probs, 5),
                    0.10,
                    "contextUncertain.txt minsup=0.10"));
        }

        // Case 14: Single item appearing in every transaction with P=0.5
        {
            List<List<Integer>> items = new ArrayList<List<Integer>>();
            List<List<Double>> probs = new ArrayList<List<Double>>();
            for (int t = 0; t < 8; t++) {
                List<Integer> ti = new ArrayList<Integer>();
                List<Double> tp = new ArrayList<Double>();
                ti.add(1); tp.add(0.5);
                items.add(ti); probs.add(tp);
            }
            cases.add(new TestCase(
                    new RandomDatabase(items, probs, 1),
                    2.0,
                    "single item all tx P=0.5"));
        }

        // Case 15: Large minsup just below total expected support
        {
            List<List<Integer>> items = new ArrayList<List<Integer>>();
            List<List<Double>> probs = new ArrayList<List<Double>>();
            for (int t = 0; t < 5; t++) {
                List<Integer> ti = new ArrayList<Integer>();
                List<Double> tp = new ArrayList<Double>();
                ti.add(1); tp.add(0.8);
                ti.add(2); tp.add(0.6);
                items.add(ti); probs.add(tp);
            }
            // expSup({1}) = 5 * 0.8 = 4.0, set minsup just below
            cases.add(new TestCase(
                    new RandomDatabase(items, probs, 2),
                    3.99,
                    "minsup just below expSup of item 1"));
        }

        return cases;
    }

    // -----------------------------------------------------------------------
    // File I/O helpers
    // -----------------------------------------------------------------------

    /**
     * Write a RandomDatabase to disk in SPMF uncertain-database format.
     * Each line is a transaction; items are written as itemId(probability)
     * tokens separated by spaces, e.g. "1(0.8) 2(0.5) 3(1.0)".
     *
     * @param db       the database to write
     * @param filePath destination file path
     * @throws IOException if an error occurs while writing
     */
    private void writeDatabaseToFile(RandomDatabase db,
                                     String filePath) throws IOException {
        PrintWriter pw = new PrintWriter(new File(filePath));
        try {
            for (int t = 0; t < db.numTransactions; t++) {
                List<Integer> tItems = db.transactionItems.get(t);
                List<Double> tProbs = db.transactionProbs.get(t);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < tItems.size(); i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(tItems.get(i))
                            .append("(")
                            .append(tProbs.get(i))
                            .append(")");
                }
                pw.println(sb.toString());
            }
        } finally {
            pw.close();
        }
    }

    /**
     * Parse an algorithm output file and return the set of frequent itemsets
     * as ParsedItemset objects.
     *
     * Output line format: "item1 item2 ... itemN #SUP: expectedSupport"
     * Item tokens may be plain integers (UV-Eclat, UH-Mine) or itemId(prob)
     * (U-Apriori). Both formats are handled transparently by stripping any
     * parenthesised probability suffix before parsing the item ID.
     * Support values are rounded to ROUNDING_DECIMALS decimal places.
     *
     * @param filePath path to the output file
     * @return the set of parsed frequent itemsets
     * @throws IOException if an error occurs while reading the file
     */
    private Set<ParsedItemset> parseOutputFile(String filePath)
            throws IOException {

        Set<ParsedItemset> result = new HashSet<ParsedItemset>();
        File f = new File(filePath);
        if (!f.exists() || f.length() == 0) {
            return result;
        }

        BufferedReader br = new BufferedReader(new FileReader(f));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                int supIdx = line.indexOf("#SUP:");
                if (supIdx < 0) continue;

                String itemsPart  = line.substring(0, supIdx).trim();
                String supportStr = line.substring(supIdx + 5).trim();

                double support = Double.parseDouble(supportStr.trim());
                double rounded = roundToDecimals(support, ROUNDING_DECIMALS);

                List<Integer> items = new ArrayList<Integer>();
                if (!itemsPart.isEmpty()) {
                    for (String token : itemsPart.split("\\s+")) {
                        token = token.trim();
                        if (token.isEmpty()) continue;
                        // strip "(probability)" suffix if present
                        int parenIdx = token.indexOf('(');
                        if (parenIdx >= 0) {
                            token = token.substring(0, parenIdx).trim();
                        }
                        if (!token.isEmpty()) {
                            items.add(Integer.parseInt(token));
                        }
                    }
                }
                result.add(new ParsedItemset(items, rounded));
            }
        } finally {
            br.close();
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // Mismatch diagnostic
    // -----------------------------------------------------------------------

    /**
     * Print diagnostic information about a three-way mismatch to standard
     * output, showing the database, minsup, result set sizes and the
     * symmetric differences between all three algorithm outputs.
     *
     * @param uAprioriResult itemsets found by U-Apriori
     * @param uvEclatResult  itemsets found by UV-Eclat
     * @param uhMineResult   itemsets found by UH-Mine
     * @param db             the database on which all algorithms ran
     * @param minsup         the minsup threshold used
     */
    private void printMismatchDetails(Set<ParsedItemset> uAprioriResult,
                                      Set<ParsedItemset> uvEclatResult,
                                      Set<ParsedItemset> uhMineResult,
                                      RandomDatabase db,
                                      double minsup) {
        System.out.println("\n--- MISMATCH DETAILS ---");
        System.out.println("  minsup = " + minsup);
        System.out.println("  Database (" + db.numTransactions
                + " transactions, " + db.numDistinctItems + " distinct items):");
        for (int t = 0; t < db.numTransactions; t++) {
            System.out.print("    t" + t + ": ");
            List<Integer> tItems = db.transactionItems.get(t);
            List<Double> tProbs = db.transactionProbs.get(t);
            for (int i = 0; i < tItems.size(); i++) {
                System.out.printf("%d:%.4f ", tItems.get(i), tProbs.get(i));
            }
            System.out.println();
        }
        System.out.println("  U-Apriori count : " + uAprioriResult.size());
        System.out.println("  UV-Eclat  count : " + uvEclatResult.size());
        System.out.println("  UH-Mine   count : " + uhMineResult.size());

        printSymmetricDiff("U-Apriori", uAprioriResult, "UV-Eclat",  uvEclatResult);
        printSymmetricDiff("U-Apriori", uAprioriResult, "UH-Mine",   uhMineResult);
        printSymmetricDiff("UV-Eclat",  uvEclatResult,  "UH-Mine",   uhMineResult);

        System.out.println("------------------------");
    }

    /**
     * Print the symmetric difference between two result sets.
     *
     * @param nameA   name of the first algorithm
     * @param setA    result set of the first algorithm
     * @param nameB   name of the second algorithm
     * @param setB    result set of the second algorithm
     */
    private void printSymmetricDiff(String nameA, Set<ParsedItemset> setA,
                                    String nameB, Set<ParsedItemset> setB) {
        Set<ParsedItemset> onlyInA = new HashSet<ParsedItemset>(setA);
        onlyInA.removeAll(setB);
        if (!onlyInA.isEmpty()) {
            System.out.println("  In " + nameA + " but NOT in " + nameB + ":");
            for (ParsedItemset p : onlyInA) {
                System.out.println("    " + p);
            }
        }
        Set<ParsedItemset> onlyInB = new HashSet<ParsedItemset>(setB);
        onlyInB.removeAll(setA);
        if (!onlyInB.isEmpty()) {
            System.out.println("  In " + nameB + " but NOT in " + nameA + ":");
            for (ParsedItemset p : onlyInB) {
                System.out.println("    " + p);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Utility
    // -----------------------------------------------------------------------

    /**
     * Round a double to the specified number of decimal places (half-up).
     * Used to absorb floating-point differences between the three algorithms.
     *
     * @param value    the value to round
     * @param decimals number of decimal places to retain
     * @return the rounded value
     */
    private double roundToDecimals(double value, int decimals) {
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }
}