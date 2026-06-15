package ca.pfv.spmf.algorithms.frequentpatterns.uveclat;
/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger
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
 * This is an implementation of the UV-Eclat algorithm as described by:
 * 
 * Leung, C.K.-S. and Sun, L. (2011). Equivalence Class Transformation Based
 * Mining of Frequent Itemsets from Uncertain Data. In Proc. ACM SAC 2011, pp. 983-984.
 * 
 * UV-Eclat mines frequent itemsets from uncertain transaction databases using vertical
 * format and equivalence class transformation. It transforms the database from horizontal
 * to vertical format using augmented tidlists that store transaction IDs and existential
 * probabilities. Frequent itemsets are mined bottom-up using tidlist intersection.
 *
 * @see UncertainTransactionDatabase
 * @see AugmentedTidList
 * @see ItemsetUVEclat
 * @author Philippe Fournier-Viger
 */
public class AlgoUVEclat {

    /** the uncertain transaction database */
    protected UncertainTransactionDatabase database;

    /** start time of latest execution */
    protected long startTimestamp;

    /** end time of latest execution */
    protected long endTimestamp;

    /** number of frequent itemsets found */
    protected int itemsetCount;

    /** writer for output file */
    BufferedWriter writer = null;

    /** maximum size of itemsets to be discovered */
    int maxItemsetSize = Integer.MAX_VALUE;

    /** map from item ID to augmented tidlist for single items */
    protected Map<Integer, AugmentedTidList> itemToTidList;

    /**
     * Constructor
     * @param database the uncertain transaction database
     */
    public AlgoUVEclat(UncertainTransactionDatabase database) {
        this.database = database;
    }

    /**
     * Run the UV-Eclat algorithm.
     * @param minsupp minimum expected support threshold
     * @param output output file path for writing frequent itemsets
     * @throws IOException if error reading or writing files
     */
    public void runAlgorithm(double minsupp, String output) throws IOException {
        startTimestamp = System.currentTimeMillis();
        itemsetCount = 0;
        writer = new BufferedWriter(new FileWriter(output));

        // Phase 1: vertical transformation
        itemToTidList = new HashMap<Integer, AugmentedTidList>();
        buildAugmentedTidListsForAllItems();

        // Phase 2: mine frequent 1-itemsets
        List<ItemsetUVEclat> frequentSize1 = buildAndSaveFrequent1Itemsets(minsupp);

        // Phase 3: mine frequent k-itemsets (k >= 2)
        if (maxItemsetSize >= 2) {
            mineEquivalenceClasses(frequentSize1, minsupp);
        }

        writer.close();
        endTimestamp = System.currentTimeMillis();
    }

    /**
     * Transform the database from horizontal to vertical format by building
     * augmented tidlists for all items.
     */
    protected void buildAugmentedTidListsForAllItems() {
        int tid = 0;
        for (ItemsetUApriori transaction : database.getTransactions()) {
            for (ItemUApriori item : transaction.getItems()) {
                int itemId = item.getId();
                double prob = item.getProbability();

                AugmentedTidList tidList = itemToTidList.get(itemId);
                if (tidList == null) {
                    tidList = new AugmentedTidList();
                    itemToTidList.put(itemId, tidList);
                }
                tidList.addEntry(tid, prob);
            }
            tid++;
        }
    }

    /**
     * Build and save frequent 1-itemsets. Returns them sorted by item ID.
     * @param minsupp minimum expected support threshold
     * @return list of frequent 1-itemsets sorted by item ID
     * @throws IOException if error writing to output file
     */
    protected List<ItemsetUVEclat> buildAndSaveFrequent1Itemsets(double minsupp) 
            throws IOException {
        List<ItemsetUVEclat> frequentSize1 = new ArrayList<ItemsetUVEclat>();

        for (Map.Entry<Integer, AugmentedTidList> entry : itemToTidList.entrySet()) {
            int itemId = entry.getKey();
            AugmentedTidList tidList = entry.getValue();
            double expSup = tidList.getExpectedSupport();

            if (expSup >= minsupp) {
                List<Integer> items = new ArrayList<Integer>();
                items.add(itemId);
                ItemsetUVEclat itemset = new ItemsetUVEclat(items, tidList, expSup);
                frequentSize1.add(itemset);
                if (maxItemsetSize >= 1) {
                    saveItemsetToFile(itemset);
                }
            }
        }

        Collections.sort(frequentSize1, new Comparator<ItemsetUVEclat>() {
            public int compare(ItemsetUVEclat a, ItemsetUVEclat b) {
                return Integer.compare(a.getItems().get(0), b.getItems().get(0));
            }
        });

        return frequentSize1;
    }

    /**
     * Mine frequent k-itemsets (k >= 2) recursively using equivalence classes.
     * @param equivalenceClass list of itemsets in current equivalence class
     * @param minsupp minimum expected support threshold
     * @throws IOException if error writing to output file
     */
    protected void mineEquivalenceClasses(List<ItemsetUVEclat> equivalenceClass,
                                          double minsupp) throws IOException {
        for (int i = 0; i < equivalenceClass.size(); i++) {
            ItemsetUVEclat itemsetAlphaY = equivalenceClass.get(i);
            List<ItemsetUVEclat> nextClass = new ArrayList<ItemsetUVEclat>();

            for (int j = i + 1; j < equivalenceClass.size(); j++) {
                ItemsetUVEclat itemsetAlphaZ = equivalenceClass.get(j);
                int suffixItemZ = itemsetAlphaZ.getLastItem();

                AugmentedTidList tidListOfItemZ = itemToTidList.get(suffixItemZ);
                AugmentedTidList newTidList = intersectTidLists(
                        itemsetAlphaY.getTidList(), tidListOfItemZ);

                double expSup = newTidList.getExpectedSupport();
                if (expSup >= minsupp) {
                    List<Integer> newItems = new ArrayList<Integer>(itemsetAlphaY.getItems());
                    newItems.add(suffixItemZ);
                    ItemsetUVEclat newItemset = new ItemsetUVEclat(newItems, newTidList, expSup);

                    if (newItems.size() <= maxItemsetSize) {
                        saveItemsetToFile(newItemset);
                    }
                    nextClass.add(newItemset);
                }
            }

            if (!nextClass.isEmpty() && itemsetAlphaY.size() + 1 < maxItemsetSize) {
                mineEquivalenceClasses(nextClass, minsupp);
            }
        }
    }

    /**
     * Intersect two augmented tidlists to form the tidlist of a combined itemset.
     * @param tidListA augmented tidlist of first itemset
     * @param tidListB augmented tidlist of second item
     * @return new augmented tidlist for combined itemset
     */
    protected AugmentedTidList intersectTidLists(AugmentedTidList tidListA,
                                                  AugmentedTidList tidListB) {
        AugmentedTidList result = new AugmentedTidList();
        int ia = 0, ib = 0;

        while (ia < tidListA.size() && ib < tidListB.size()) {
            int tidA = tidListA.getTid(ia);
            int tidB = tidListB.getTid(ib);

            if (tidA == tidB) {
                double productProb = tidListA.getProbability(ia) * tidListB.getProbability(ib);
                result.addEntry(tidA, productProb);
                ia++;
                ib++;
            } else if (tidA < tidB) {
                ia++;
            } else {
                ib++;
            }
        }
        return result;
    }

    /**
     * Save a frequent itemset to the output file.
     * @param itemset the frequent itemset to save
     * @throws IOException if error writing to file
     */
    private void saveItemsetToFile(ItemsetUVEclat itemset) throws IOException {
        writer.write(itemset.toString() + " #SUP: " + itemset.getExpectedSupport());
        writer.newLine();
        itemsetCount++;
    }

    /**
     * Print statistics about the latest execution.
     */
    public void printStats() {
        System.out.println("============= UV-ECLAT 2.66 - STATS =============");
        long duration = endTimestamp - startTimestamp;
        System.out.println(" Transactions count from database : " + database.size());
        System.out.println(" Uncertain frequent itemsets count : " + itemsetCount);
        System.out.println(" Total time ~ " + duration + " ms");
        System.out.println("============================================");
    }

    /**
     * Set the maximum pattern length.
     * @param length the maximum itemset size
     */
    public void setMaximumPatternLength(int length) {
        this.maxItemsetSize = length;
    }
}