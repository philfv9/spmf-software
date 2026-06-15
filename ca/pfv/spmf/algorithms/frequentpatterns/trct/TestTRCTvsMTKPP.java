package ca.pfv.spmf.algorithms.frequentpatterns.trct;

/* This file is copyright (c) 2008-2016 Philippe Fournier-Viger
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
*/

import ca.pfv.spmf.algorithms.frequentpatterns.mtkpp.AlgoMTKPP;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Test class that runs TRCT and MTKPP on the same random datasets and verifies
 * that both algorithms return identical top-K periodic pattern results.
 * MTKPP is used as the reference implementation since it is already validated.
 * All patterns tied at the K-th rank are expected to be returned by both algorithms.
 *
 * @author Philippe Fournier-Viger
 */
public class TestTRCTvsMTKPP {

    /** temporary input file path used for generated datasets */
    private static final String TEMP_INPUT = "temp_trct_test_input.txt";

    /** temporary output file path used for MTKPP results */
    private static final String TEMP_MTKPP = "temp_trct_test_mtkpp.txt";

    /** temporary output file path used for TRCT results */
    private static final String TEMP_TRCT = "temp_trct_test_trct.txt";

    /** the random number generator with fixed seed for reproducibility */
    private static final Random RNG = new Random(12345L);

    /**
     * Main entry point: runs 500 randomised tests and reports pass/fail counts.
     * @param args command line arguments (not used)
     * @throws IOException exception if error while reading or writing files
     */
    public static void main(String[] args) throws IOException {
        int totalTests = 500;
        int passed = 0;
        int failed = 0;

        System.out.println("Running " + totalTests + " tests for TRCT vs MTKPP...");

        for (int t = 0; t < totalTests; t++) {
            TestConfig cfg = buildRandomConfig(t);

            try {
                boolean ok = runOneTest(cfg, t);
                if (ok) {
                    passed++;
                } else {
                    failed++;
                    System.out.println("FAIL  test #" + t + "  " + cfg);
                }
            } catch (Exception e) {
                failed++;
                System.out.println("ERROR test #" + t + "  " + cfg
                        + "  exception: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println("-------------------------------------------");
        System.out.println("Passed : " + passed + " / " + totalTests);
        System.out.println("Failed : " + failed + " / " + totalTests);

        new File(TEMP_INPUT).delete();
        new File(TEMP_MTKPP).delete();
        new File(TEMP_TRCT).delete();
    }

    /**
     * Holds the parameters for one randomised test run.
     */
    static class TestConfig {
        /** number of transactions in the generated database */
        int numTransactions;
        /** number of distinct items in the generated database */
        int numItems;
        /** average number of items per transaction */
        int avgTransactionLength;
        /** maximum periodicity threshold */
        int maxPeriodicity;
        /** k value for both algorithms */
        int k;

        /**
         * Return a readable summary of this configuration.
         * @return string description of the config
         */
        public String toString() {
            return "[T=" + numTransactions
                    + " I=" + numItems
                    + " L=" + avgTransactionLength
                    + " maxPer=" + maxPeriodicity
                    + " k=" + k + "]";
        }
    }

    /**
     * Build a randomised test configuration, cycling through edge cases and normal cases.
     * @param testIndex the index of the current test
     * @return a TestConfig describing the test parameters
     */
    private static TestConfig buildRandomConfig(int testIndex) {
        TestConfig cfg = new TestConfig();

        switch (testIndex % 50) {
            case 0:
                // edge case: tiny database
                cfg.numTransactions = 1 + RNG.nextInt(3);
                cfg.numItems = 1 + RNG.nextInt(3);
                cfg.avgTransactionLength = 1;
                cfg.maxPeriodicity = 1 + RNG.nextInt(2);
                cfg.k = 1 + RNG.nextInt(3);
                break;
            case 1:
                // edge case: very large maxPeriodicity (accepts all patterns)
                cfg.numTransactions = 5 + RNG.nextInt(20);
                cfg.numItems = 3 + RNG.nextInt(10);
                cfg.avgTransactionLength = 2 + RNG.nextInt(4);
                cfg.maxPeriodicity = 10000;
                cfg.k = 1 + RNG.nextInt(50);
                break;
            case 2:
                // edge case: maxPeriodicity = 1 (very strict)
                cfg.numTransactions = 5 + RNG.nextInt(15);
                cfg.numItems = 2 + RNG.nextInt(5);
                cfg.avgTransactionLength = cfg.numItems;
                cfg.maxPeriodicity = 1;
                cfg.k = 1 + RNG.nextInt(10);
                break;
            case 3:
                // edge case: k = 1 (only the single best pattern)
                cfg.numTransactions = 5 + RNG.nextInt(30);
                cfg.numItems = 3 + RNG.nextInt(10);
                cfg.avgTransactionLength = 2 + RNG.nextInt(5);
                cfg.maxPeriodicity = 2 + RNG.nextInt(Math.max(1, cfg.numTransactions - 2));
                cfg.k = 1;
                break;
            case 4:
                // edge case: k much larger than possible result set
                cfg.numTransactions = 5 + RNG.nextInt(15);
                cfg.numItems = 2 + RNG.nextInt(5);
                cfg.avgTransactionLength = 2 + RNG.nextInt(4);
                cfg.maxPeriodicity = 3 + RNG.nextInt(Math.max(1, cfg.numTransactions - 3));
                cfg.k = 10000;
                break;
            case 5:
                // edge case: single item universe
                cfg.numTransactions = 3 + RNG.nextInt(20);
                cfg.numItems = 1;
                cfg.avgTransactionLength = 1;
                cfg.maxPeriodicity = 1 + RNG.nextInt(Math.max(1, cfg.numTransactions));
                cfg.k = 1 + RNG.nextInt(5);
                break;
            case 6:
                // edge case: very sparse transactions (length 1)
                cfg.numTransactions = 10 + RNG.nextInt(20);
                cfg.numItems = 5 + RNG.nextInt(10);
                cfg.avgTransactionLength = 1;
                cfg.maxPeriodicity = 2 + RNG.nextInt(5);
                cfg.k = 1 + RNG.nextInt(10);
                break;
            case 7:
                // edge case: dense transactions (all items in every transaction)
                cfg.numTransactions = 5 + RNG.nextInt(15);
                cfg.numItems = 3 + RNG.nextInt(8);
                cfg.avgTransactionLength = cfg.numItems;
                cfg.maxPeriodicity = 1 + RNG.nextInt(5);
                cfg.k = 1 + RNG.nextInt(20);
                break;
            case 8:
                // edge case: maxPeriodicity equals numTransactions (very permissive)
                cfg.numTransactions = 5 + RNG.nextInt(20);
                cfg.numItems = 3 + RNG.nextInt(8);
                cfg.avgTransactionLength = 2 + RNG.nextInt(4);
                cfg.maxPeriodicity = cfg.numTransactions;
                cfg.k = 1 + RNG.nextInt(15);
                break;
            default:
                // normal random case
                cfg.numTransactions = 5 + RNG.nextInt(50);
                cfg.numItems = 3 + RNG.nextInt(20);
                cfg.avgTransactionLength = 1 + RNG.nextInt(Math.max(1, cfg.numItems / 2));
                cfg.maxPeriodicity = 1 + RNG.nextInt(Math.max(1, cfg.numTransactions));
                cfg.k = 1 + RNG.nextInt(30);
                break;
        }

        return cfg;
    }

    /**
     * Generate a random transaction database and write it to the temporary input file.
     * @param cfg the test configuration describing the database parameters
     * @throws IOException exception if error while writing the file
     */
    private static void generateDatabase(TestConfig cfg) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(TEMP_INPUT))) {
            for (int t = 0; t < cfg.numTransactions; t++) {
                int len = Math.max(1, cfg.avgTransactionLength - 1 + RNG.nextInt(3));
                len = Math.min(len, cfg.numItems);

                Set<Integer> chosen = new HashSet<Integer>();
                while (chosen.size() < len) {
                    chosen.add(1 + RNG.nextInt(cfg.numItems));
                }
                List<Integer> row = new ArrayList<Integer>(chosen);
                Collections.sort(row);

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) sb.append(' ');
                    sb.append(row.get(i));
                }
                bw.write(sb.toString());
                bw.newLine();
            }
        }
    }

    /**
     * Represents a parsed pattern entry with its items, support, and largest periodicity.
     */
    static class ParsedPattern {
        /** the sorted items of the pattern */
        int[] items;
        /** the support of the pattern */
        int support;
        /** the largest periodicity of the pattern */
        int largestPeriodicity;

        /**
         * Constructor for a parsed pattern.
         * @param items the sorted items of the pattern
         * @param support the support of the pattern
         * @param largestPeriodicity the largest periodicity of the pattern
         */
        ParsedPattern(int[] items, int support, int largestPeriodicity) {
            this.items = items;
            this.support = support;
            this.largestPeriodicity = largestPeriodicity;
        }

        /**
         * Return a canonical string key for this pattern based on its sorted items.
         * @return the key string
         */
        String key() {
            return Arrays.toString(items);
        }

        /**
         * Return a readable string representation of this pattern.
         * @return string with items, support, and largest periodicity
         */
        public String toString() {
            return key() + " sup=" + support + " maxPer=" + largestPeriodicity;
        }
    }

    /**
     * Parse an output file produced by either MTKPP or TRCT into a list of ParsedPattern objects.
     * @param filePath the path of the output file to parse
     * @return the list of parsed patterns found in the file
     * @throws IOException exception if error while reading the file
     */
    private static List<ParsedPattern> parseOutputFile(String filePath) throws IOException {
        List<ParsedPattern> results = new ArrayList<ParsedPattern>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                ParsedPattern pp = parseOutputLine(line);
                if (pp != null) {
                    results.add(pp);
                }
            }
        }
        return results;
    }

    /**
     * Parse a single output line in the SPMF format into a ParsedPattern.
     * @param line the line to parse (format: "item1 item2 ... #SUP: s #MAXPER: p")
     * @return the parsed pattern, or null if the line cannot be parsed
     */
    private static ParsedPattern parseOutputLine(String line) {
        try {
            int supIdx = line.indexOf("#SUP:");
            int maxPerIdx = line.indexOf("#MAXPER:");
            if (supIdx < 0) return null;

            String itemsPart = line.substring(0, supIdx).trim();

            String supPart;
            if (maxPerIdx >= 0) {
                supPart = line.substring(supIdx + 5, maxPerIdx).trim();
            } else {
                supPart = line.substring(supIdx + 5).trim();
            }
            int support = Integer.parseInt(supPart.split("\\s+")[0]);

            int largestPeriodicity = 0;
            if (maxPerIdx >= 0) {
                String maxPerPart = line.substring(maxPerIdx + 8).trim();
                largestPeriodicity = Integer.parseInt(maxPerPart.split("\\s+")[0]);
            }

            if (itemsPart.isEmpty()) return null;
            String[] itemTokens = itemsPart.split("\\s+");
            int[] items = new int[itemTokens.length];
            for (int i = 0; i < itemTokens.length; i++) {
                items[i] = Integer.parseInt(itemTokens[i]);
            }
            Arrays.sort(items);

            return new ParsedPattern(items, support, largestPeriodicity);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Run one complete test: generate a database, run MTKPP as reference, run TRCT,
     * and verify that both return the same set of top-K periodic patterns including ties.
     * @param cfg the test configuration
     * @param testIndex the index of the current test for logging purposes
     * @return true if TRCT and MTKPP return the same result, false otherwise
     * @throws IOException exception if error while reading or writing files
     */
    private static boolean runOneTest(TestConfig cfg, int testIndex) throws IOException {

        // 1. Generate random database
        generateDatabase(cfg);

        // 2. Run MTKPP as the reference implementation
        AlgoMTKPP mtkpp = new AlgoMTKPP();
        mtkpp.runAlgorithm(TEMP_INPUT, TEMP_MTKPP, cfg.k, cfg.maxPeriodicity);

        // 3. Run TRCT as the algorithm under test
        AlgoTRCT trct = new AlgoTRCT();
        trct.runAlgorithm(TEMP_INPUT, TEMP_TRCT, cfg.k, cfg.maxPeriodicity);

        // 4. Parse both output files
        List<ParsedPattern> mtkppResults = parseOutputFile(TEMP_MTKPP);
        List<ParsedPattern> trctResults = parseOutputFile(TEMP_TRCT);

        // 5. Build maps from key -> support for both result sets
        Map<String, Integer> mtkppMap = new HashMap<String, Integer>();
        for (ParsedPattern pp : mtkppResults) {
            mtkppMap.put(pp.key(), pp.support);
        }

        Map<String, Integer> trctMap = new HashMap<String, Integer>();
        for (ParsedPattern pp : trctResults) {
            trctMap.put(pp.key(), pp.support);
        }

        // 6. Both must return the same number of patterns
        if (mtkppResults.size() != trctResults.size()) {
            System.out.println("  Size mismatch: MTKPP=" + mtkppResults.size()
                    + " TRCT=" + trctResults.size());
            printDebugInfo(mtkppResults, trctResults, cfg);
            return false;
        }

        // 7. Every pattern in MTKPP must also appear in TRCT with the same support
        for (ParsedPattern pp : mtkppResults) {
            Integer trctSupport = trctMap.get(pp.key());
            if (trctSupport == null) {
                System.out.println("  Pattern in MTKPP but missing from TRCT: " + pp);
                printDebugInfo(mtkppResults, trctResults, cfg);
                return false;
            }
            if (trctSupport != pp.support) {
                System.out.println("  Support mismatch for pattern " + pp.key()
                        + ": MTKPP=" + pp.support + " TRCT=" + trctSupport);
                printDebugInfo(mtkppResults, trctResults, cfg);
                return false;
            }
        }

        // 8. Every pattern in TRCT must also appear in MTKPP with the same support
        for (ParsedPattern pp : trctResults) {
            Integer mtkppSupport = mtkppMap.get(pp.key());
            if (mtkppSupport == null) {
                System.out.println("  Pattern in TRCT but missing from MTKPP: " + pp);
                printDebugInfo(mtkppResults, trctResults, cfg);
                return false;
            }
            if (mtkppSupport != pp.support) {
                System.out.println("  Support mismatch for pattern " + pp.key()
                        + ": TRCT=" + pp.support + " MTKPP=" + mtkppSupport);
                printDebugInfo(mtkppResults, trctResults, cfg);
                return false;
            }
        }

        // 9. Verify periodicity values agree between MTKPP and TRCT for each pattern
        for (ParsedPattern mtkppPP : mtkppResults) {
            for (ParsedPattern trctPP : trctResults) {
                if (Arrays.equals(mtkppPP.items, trctPP.items)) {
                    if (mtkppPP.largestPeriodicity != trctPP.largestPeriodicity) {
                        System.out.println("  Periodicity mismatch for pattern " + mtkppPP.key()
                                + ": MTKPP=" + mtkppPP.largestPeriodicity
                                + " TRCT=" + trctPP.largestPeriodicity);
                        printDebugInfo(mtkppResults, trctResults, cfg);
                        return false;
                    }
                    break;
                }
            }
        }

        return true;
    }

    /**
     * Print debug information showing the MTKPP and TRCT results side by side.
     * @param mtkppResults the patterns returned by MTKPP
     * @param trctResults the patterns returned by TRCT
     * @param cfg the test configuration that produced these results
     */
    private static void printDebugInfo(List<ParsedPattern> mtkppResults,
                                       List<ParsedPattern> trctResults,
                                       TestConfig cfg) {
        System.out.println("  Config: " + cfg);

        // Sort both by support descending for readable comparison
        List<ParsedPattern> mtkppSorted = new ArrayList<ParsedPattern>(mtkppResults);
        List<ParsedPattern> trctSorted = new ArrayList<ParsedPattern>(trctResults);
        Comparator<ParsedPattern> bySup = new Comparator<ParsedPattern>() {
            public int compare(ParsedPattern a, ParsedPattern b) {
                return b.support - a.support;
            }
        };
        Collections.sort(mtkppSorted, bySup);
        Collections.sort(trctSorted, bySup);

        System.out.println("  --- MTKPP result (" + mtkppSorted.size() + " patterns) ---");
        for (ParsedPattern pp : mtkppSorted) {
            System.out.println("    " + pp);
        }
        System.out.println("  --- TRCT result (" + trctSorted.size() + " patterns) ---");
        for (ParsedPattern pp : trctSorted) {
            System.out.println("    " + pp);
        }

        // Print patterns in MTKPP but not in TRCT
        Set<String> trctKeys = new HashSet<String>();
        for (ParsedPattern pp : trctResults) trctKeys.add(pp.key());
        boolean missingFromTRCT = false;
        for (ParsedPattern pp : mtkppResults) {
            if (!trctKeys.contains(pp.key())) {
                if (!missingFromTRCT) {
                    System.out.println("  --- Patterns in MTKPP but NOT in TRCT ---");
                    missingFromTRCT = true;
                }
                System.out.println("    " + pp);
            }
        }

        // Print patterns in TRCT but not in MTKPP
        Set<String> mtkppKeys = new HashSet<String>();
        for (ParsedPattern pp : mtkppResults) mtkppKeys.add(pp.key());
        boolean missingFromMTKPP = false;
        for (ParsedPattern pp : trctResults) {
            if (!mtkppKeys.contains(pp.key())) {
                if (!missingFromMTKPP) {
                    System.out.println("  --- Patterns in TRCT but NOT in MTKPP ---");
                    missingFromMTKPP = true;
                }
                System.out.println("    " + pp);
            }
        }
    }
}