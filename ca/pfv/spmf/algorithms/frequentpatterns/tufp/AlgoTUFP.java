package ca.pfv.spmf.algorithms.frequentpatterns.tufp;
/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * Do not remove copyright or license information.
 */

import ca.pfv.spmf.algorithms.frequentpatterns.uapriori.ItemUApriori;
import ca.pfv.spmf.algorithms.frequentpatterns.uapriori.ItemsetUApriori;
import ca.pfv.spmf.algorithms.frequentpatterns.uapriori.UncertainTransactionDatabase;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is an implementation of the TUFP algorithm as described by:
 *
 * Le, T., Vo, B., Huynh, V.-N., Nguyen, N.T., Baik, S.W. (2020). Mining top-k frequent
 * patterns from uncertain databases. Applied Intelligence.
 * https://doi.org/10.1007/s10489-019-01622-1
 *
 * TUFP mines top-k uncertain frequent patterns from uncertain transaction databases.
 * It uses CUP-Lists and effective threshold raising strategies to find the top-k
 * patterns without requiring a minimum support threshold. When ties occur, all patterns
 * with the same expected support as the k-th pattern are included in the result.
 *
 * @see UncertainTransactionDatabase
 * @see CUPList
 * @see PatternTUFP
 * @author Philippe Fournier-Viger
 */
public class AlgoTUFP {

    /** the uncertain transaction database */
    protected UncertainTransactionDatabase database;

    /** start time of latest execution */
    protected long startTimestamp;

    /** end time of latest execution */
    protected long endTimestamp;

    /** number of top-k patterns found */
    protected int itemsetCount;

    /** writer for output file */
    BufferedWriter writer = null;

    /** the desired number of top-k patterns */
    protected int k;

    /** current minimum expected support threshold (raised dynamically) */
    protected double minExpSup;

    /** the top-k result list sorted by ascending expected support */
    protected List<PatternTUFP> topK;

    /** map from item ID to its base CUP-List */
    protected Map<Integer, CUPList> itemToCupList;

    /** tolerance for floating point comparisons */
    private static final double EPSILON = 1e-9;

    /**
     * Constructor.
     * @param database the uncertain transaction database
     */
    public AlgoTUFP(UncertainTransactionDatabase database) {
        this.database = database;
    }

    /**
     * Run the TUFP algorithm to find top-k uncertain frequent patterns.
     * @param k the desired number of top-k patterns
     * @param output the output file path for writing the result
     * @throws IOException if an error occurs reading or writing files
     */
    public void runAlgorithm(int k, String output) throws IOException {
        this.k = k;
        this.minExpSup = 0.0;
        this.topK = new ArrayList<PatternTUFP>();
        this.itemsetCount = 0;

        startTimestamp = System.currentTimeMillis();
        writer = new BufferedWriter(new FileWriter(output));

        // Step 1: scan database to build CUP-Lists for all single items
        itemToCupList = new HashMap<Integer, CUPList>();
        buildCUPListsForAllItems();

        // Step 2: collect all 1-patterns sorted by ascending expected support
        List<PatternTUFP> allSingle = buildSinglePatterns();
        Collections.sort(allSingle, new Comparator<PatternTUFP>() {
            public int compare(PatternTUFP a, PatternTUFP b) {
                return Double.compare(a.getExpectedSupport(), b.getExpectedSupport());
            }
        });

        // Step 3: insert all 1-patterns into the top-k result
        for (PatternTUFP p : allSingle) {
            insertIntoTopK(p);
        }

        // Step 4: mine longer patterns using divide-and-conquer with threshold raising
        tufpSearch(allSingle);

        // Step 5: write results to file
        writeResults();

        writer.close();
        endTimestamp = System.currentTimeMillis();
    }

    /**
     * Scan the database once to build CUP-Lists for all single items.
     */
    protected void buildCUPListsForAllItems() {
        int tid = 0;
        for (ItemsetUApriori transaction : database.getTransactions()) {
            for (ItemUApriori item : transaction.getItems()) {
                int itemId = item.getId();
                double prob = item.getProbability();
                CUPList cupList = itemToCupList.get(itemId);
                if (cupList == null) {
                    cupList = new CUPList();
                    itemToCupList.put(itemId, cupList);
                }
                cupList.addEntry(tid, prob);
            }
            tid++;
        }
    }

    /**
     * Build the list of all 1-patterns from the item CUP-Lists.
     * @return list of all single-item patterns
     */
    protected List<PatternTUFP> buildSinglePatterns() {
        List<PatternTUFP> singles = new ArrayList<PatternTUFP>();
        for (Map.Entry<Integer, CUPList> entry : itemToCupList.entrySet()) {
            int itemId = entry.getKey();
            CUPList cupList = entry.getValue();
            double expSup = cupList.getExpectedSupport();
            List<Integer> items = new ArrayList<Integer>();
            items.add(itemId);
            singles.add(new PatternTUFP(items, cupList, expSup));
        }
        return singles;
    }

    /**
     * Insert a pattern into the top-k list, trim patterns below the k-th threshold,
     * and raise minExpSup accordingly. Ties at the k-th support are always kept.
     * @param pattern the pattern to insert
     */
    protected void insertIntoTopK(PatternTUFP pattern) {
        topK.add(pattern);

        // Sort ascending by expected support (lowest first)
        Collections.sort(topK, new Comparator<PatternTUFP>() {
            public int compare(PatternTUFP a, PatternTUFP b) {
                return Double.compare(a.getExpectedSupport(), b.getExpectedSupport());
            }
        });

        // Only trim if we have strictly more than k patterns
        if (topK.size() > k) {
            // The k-th largest support is at index (size - k) in ascending order
            double kthSupport = topK.get(topK.size() - k).getExpectedSupport();

            // Remove everything strictly below the k-th support
            List<PatternTUFP> kept = new ArrayList<PatternTUFP>();
            for (PatternTUFP p : topK) {
                if (p.getExpectedSupport() >= kthSupport - EPSILON) {
                    kept.add(p);
                }
            }
            topK = kept;

            // Raise the threshold to the minimum support in the kept set
            minExpSup = topK.get(0).getExpectedSupport();
        }
    }

    /**
     * Recursively search for top-k patterns using a divide-and-conquer strategy with
     * threshold raising (Strategy 1 and Strategy 2 from the TUFP paper).
     * The equivalence class contains patterns that all share the same prefix.
     * Pattern X at position i is combined with pattern Y at position j > i by
     * intersecting X's CUP-List with the BASE CUP-List of Y's suffix item
     * to produce a new pattern whose items are X's items plus Y's last item.
     * @param equivalenceClass list of patterns in the current equivalence class
     * @throws IOException if an error occurs writing to the output file
     */
    protected void tufpSearch(List<PatternTUFP> equivalenceClass) throws IOException {
        for (int i = 0; i < equivalenceClass.size(); i++) {
            PatternTUFP patternX = equivalenceClass.get(i);

            // Strategy 2: prune if X's expected support is below threshold
            if (patternX.getExpectedSupport() < minExpSup - EPSILON) {
                continue;
            }

            List<PatternTUFP> nextClass = new ArrayList<PatternTUFP>();

            for (int j = i + 1; j < equivalenceClass.size(); j++) {
                PatternTUFP patternY = equivalenceClass.get(j);

                // Strategy 2: prune if Y's expected support is below threshold
                if (patternY.getExpectedSupport() < minExpSup - EPSILON) {
                    continue;
                }

                // Get the suffix item from Y (the last item in Y's itemset)
                int suffixItem = patternY.getItems().get(patternY.getItems().size() - 1);
                
                // Get the BASE CUP-List for this single item (not Y's full CUP-List)
                CUPList baseItemCupList = itemToCupList.get(suffixItem);
                if (baseItemCupList == null) {
                    continue;
                }
                
                // Overestimation: expSup(X) * Max(suffixItem) is an upper bound for expSup(X+suffixItem)
                double overEstimate = patternX.getExpectedSupport() * baseItemCupList.getMax();
                if (overEstimate < minExpSup - EPSILON) {
                    continue;
                }

                // Build the CUP-List for X+suffixItem by intersecting X's CUP-List 
                // with the BASE CUP-List of the suffix item
                CUPList newCupList = constructCUPList(patternX.getCupList(), baseItemCupList);
                double expSup = newCupList.getExpectedSupport();

                // Skip if below threshold
                if (expSup < minExpSup - EPSILON) {
                    continue;
                }

                // New pattern = X's items + suffix item
                List<Integer> newItems = new ArrayList<Integer>(patternX.getItems());
                newItems.add(suffixItem);
                PatternTUFP newPattern = new PatternTUFP(newItems, newCupList, expSup);

                // Insert into top-k and raise threshold (Strategy 1)
                insertIntoTopK(newPattern);

                nextClass.add(newPattern);
            }

            if (!nextClass.isEmpty()) {
                tufpSearch(nextClass);
            }
        }
    }

    /**
     * Construct a new CUP-List for pattern XY by intersecting the CUP-Lists of X and Y.
     * @param cupListX the CUP-List of pattern X
     * @param cupListY the CUP-List of item Y (or pattern Y)
     * @return the new CUP-List for pattern XY
     */
    protected CUPList constructCUPList(CUPList cupListX, CUPList cupListY) {
        CUPList result = new CUPList();
        int ix = 0, iy = 0;

        while (ix < cupListX.size() && iy < cupListY.size()) {
            int tidX = cupListX.getTid(ix);
            int tidY = cupListY.getTid(iy);

            if (tidX == tidY) {
                double prob = cupListX.getProbability(ix) * cupListY.getProbability(iy);
                result.addEntry(tidX, prob);
                ix++;
                iy++;
            } else if (tidX < tidY) {
                ix++;
            } else {
                iy++;
            }
        }
        return result;
    }

    /**
     * Write all top-k patterns to the output file sorted by descending expected support.
     * @throws IOException if an error occurs writing to the output file
     */
    protected void writeResults() throws IOException {
        List<PatternTUFP> output = new ArrayList<PatternTUFP>(topK);
        Collections.sort(output, new Comparator<PatternTUFP>() {
            public int compare(PatternTUFP a, PatternTUFP b) {
                return Double.compare(b.getExpectedSupport(), a.getExpectedSupport());
            }
        });

        for (PatternTUFP pattern : output) {
            writer.write(pattern.toString() + " #SUP: " + pattern.getExpectedSupport());
            writer.newLine();
            itemsetCount++;
        }
    }

    /**
     * Print statistics about the latest execution to the console.
     */
    public void printStats() {
        System.out.println("============= TUFP - STATS =============");
        long duration = endTimestamp - startTimestamp;
        System.out.println(" Transactions count from database : " + database.size());
        System.out.println(" The value of k : " + k);
        System.out.println(" Patterns in top-k result : " + itemsetCount);
        System.out.println(" Minimum expected support found : " + minExpSup);
        System.out.println(" Total time ~ " + duration + " ms");
        System.out.println("==========================================");
    }
}