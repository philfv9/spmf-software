package ca.pfv.spmf.algorithms.frequentpatterns.talkyg;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger

This file is part of the SPMF DATA MINING SOFTWARE
(http://www.philippe-fournier-viger.com/spmf).
SPMF is free software: you can redistribute it and/or modify it under the
terms of the GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version.
SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
A PARTICULAR PURPOSE. See the GNU General Public License for more details.
You should have received a copy of the GNU General Public License along with
SPMF. If not, see http://www.gnu.org/licenses/.
*/

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.ArraysAlgos;
import ca.pfv.spmf.datastructures.triangularmatrix.TriangularMatrix;
import ca.pfv.spmf.input.transaction_database_list_integers.TransactionDatabase;
import ca.pfv.spmf.patterns.itemset_array_integers_with_tids_bitset.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_tids_bitset.Itemsets;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is a diffset-based implementation of the TalkyG algorithm for mining frequent generators.
 * The difference between this and TalkyG_Bitset is that this version uses diffsets instead of tidsets.
 * In this implementation, diffsets are represented as bitsets.
 * 
 * This class extends AlgoTalkyG_Bitset and overrides the necessary methods to use diffset logic.
 * 
 * IMPORTANT: This version stores null for tidsets when keeping results in memory, 
 * since diffsets are not meaningful to keep.
 * 
 * The diffset approach was proposed by Zaki:
 * M. J. Zaki and K. Gouda. Fast vertical mining using Diffsets. Technical Report 01-1, 
 * Computer Science Dept., Rensselaer Polytechnic Institute, March 2001.
 * 
 * @see TriangularMatrix
 * @see TransactionDatabase
 * @see Itemset
 * @see Itemsets
 * @author Philippe Fournier-Viger
 */
public class AlgoTalkyG_Diffset extends AlgoTalkyG_Bitset {

    /**
     * Default constructor
     */
    public AlgoTalkyG_Diffset() {
    }

    /**
     * Run the algorithm and save the output to a file or keep it into memory.
     * 
     * @param output an output file path for writing the result or if null the
     *               result is saved into memory and returned
     * @param database a transaction database
     * @param minsup the minimum support
     * @param useTriangularMatrixOptimization if true the triangular matrix
     *               optimization will be applied.
     * @param hashTableSize the size of the hashtable (e.g. 10,000).
     * @return the set of frequent generators found if the result is kept into
     *         memory or null otherwise.
     * @throws IOException exception if error while writing the file.
     */
    @Override
    public Itemsets runAlgorithm(String output, TransactionDatabase database, double minsup,
            boolean useTriangularMatrixOptimization, int hashTableSize) throws IOException {

        // Reset the tool to assess the maximum memory usage (for statistics)
        MemoryLogger.getInstance().reset();

        // if the user wants to keep the result into memory
        if (output == null) {
            writer = null;
            frequentGenerators = new Itemsets("FREQUENT GENERATORS");
        } else {
            // if the user wants to save the result to a file
            frequentGenerators = null;
            writer = new BufferedWriter(new FileWriter(output));
        }

        // Create the hash table to store itemsets for generator checking
        this.generatorHash = new GeneratorHashTable(hashTableSize);

        // reset the number of generators found to 0
        generatorCount = 0;

        this.database = database;

        // record the start time
        startTimestamp = System.currentTimeMillis();

        // convert from an absolute minsup to a relative minsup
        int minsupRelative = (int) Math.ceil(minsup * database.size());

        // (1) First database pass: calculate diffsets of each item.
        final Map<Integer, BitSetSupport> mapItemDiffsets = new HashMap<Integer, BitSetSupport>();
        int maxItemId = calculateSupportSingleItems(database, mapItemDiffsets);

        // If the user chose to use the triangular matrix optimization
        TriangularMatrix matrix = null;
        if (useTriangularMatrixOptimization) {
            matrix = new TriangularMatrix(maxItemId + 1);
            for (List<Integer> itemset : database.getTransactions()) {
                Object[] array = itemset.toArray();
                for (int i = 0; i < itemset.size(); i++) {
                    Integer itemI = (Integer) array[i];
                    for (int j = i + 1; j < itemset.size(); j++) {
                        Integer itemJ = (Integer) array[j];
                        matrix.incrementCount(itemI, itemJ);
                    }
                }
            }
        }

        // (2) create the list of single frequent items
        List<Integer> frequentItems = new ArrayList<Integer>();

        for (Entry<Integer, BitSetSupport> entry : mapItemDiffsets.entrySet()) {
            BitSetSupport diffset = entry.getValue();
            int support = diffset.support;
            int item = entry.getKey();
            // if the item is frequent and not a full column (support < database size)
            if (support >= minsupRelative && support < database.size()) {
                frequentItems.add(item);
            }
        }

        // Check for empty set as generator (if there's NO item with full support)
        checkEmptySetGenerator(mapItemDiffsets, minsupRelative);

        // Sort the list of items by increasing support (total order)
        Collections.sort(frequentItems, new Comparator<Integer>() {
            @Override
            public int compare(Integer arg0, Integer arg1) {
                return mapItemDiffsets.get(arg0).support - mapItemDiffsets.get(arg1).support;
            }
        });

        // Save single item generators and build equivalence classes
        for (int i = 0; i < frequentItems.size(); i++) {
            Integer itemX = frequentItems.get(i);
            if (itemX == null) {
                continue;
            }

            BitSetSupport diffsetX = mapItemDiffsets.get(itemX);
            int[] itemsetX = new int[] { itemX };

            // Save the single item as a generator
            saveGenerator(null, itemsetX, diffsetX);

            // Skip building equivalence class if max pattern length is 1
            if (maximumPatternLength > 0 && maximumPatternLength <= 1) {
                continue;
            }

            // Build equivalence class for extensions
            List<int[]> equivalenceClassItemsets = new ArrayList<int[]>();
            List<BitSetSupport> equivalenceClassDiffsets = new ArrayList<BitSetSupport>();

            // For each item itemJ that is larger than i
            for (int j = i + 1; j < frequentItems.size(); j++) {
                Integer itemJ = frequentItems.get(j);
                if (itemJ == null) {
                    continue;
                }

                // Use triangular matrix optimization if available
                if (useTriangularMatrixOptimization) {
                    int supportIJ = matrix.getSupportForItems(itemX, itemJ);
                    if (supportIJ < minsupRelative) {
                        continue;
                    }
                }

                BitSetSupport diffsetJ = mapItemDiffsets.get(itemJ);

                // Calculate the diffset intersection
                BitSetSupport diffsetXJ = performAND(diffsetX, diffsetJ);

                // If infrequent, skip
                if (diffsetXJ.support < minsupRelative) {
                    continue;
                }

                // Generator check using diffsets:
                // If diffset(XJ|X) is empty, then support(XJ) = support(X) - not a generator
                if (diffsetXJ.bitset.cardinality() == 0) {
                    continue;
                }
                
                // Check if support(XJ) = support(J) by computing diffset(X) - diffset(J)
                BitSet diffXJ_J = (BitSet) diffsetX.bitset.clone();
                diffXJ_J.andNot(diffsetJ.bitset);
                if (diffXJ_J.cardinality() == 0) {
                    continue;
                }

                // The candidate {itemX, itemJ} is a potential generator
                equivalenceClassItemsets.add(new int[] { itemJ });
                equivalenceClassDiffsets.add(diffsetXJ);
            }

            // Process equivalence class to find larger generators
            if (equivalenceClassItemsets.size() > 0) {
                processEquivalenceClass(itemsetX, equivalenceClassItemsets, 
                        equivalenceClassDiffsets, minsupRelative);
            }
        }

        // close the output file if the result was saved to a file
        if (writer != null) {
            writer.close();
        }

        // check the memory usage
        MemoryLogger.getInstance().checkMemory();

        // record the end time for statistics
        endTime = System.currentTimeMillis();

        // Return all frequent generators found
        return frequentGenerators;
    }

    /**
     * Check if the empty set should be saved as a generator
     * (when there's NO item with support equal to database size)
     */
    private void checkEmptySetGenerator(Map<Integer, BitSetSupport> mapItemDiffsets, 
            int minsupRelative) throws IOException {
        // Check if any item has support equal to database size
        boolean hasFullColumn = false;
        for (Entry<Integer, BitSetSupport> entry : mapItemDiffsets.entrySet()) {
            if (entry.getValue().support == database.size()) {
                hasFullColumn = true;
                break;
            }
        }
        
        // If there is NO item with full support, empty set is a generator
        if (!hasFullColumn) {
            BitSetSupport emptyDiffset = new BitSetSupport();
            emptyDiffset.support = database.size();
            // For empty set, diffset is empty (no transaction is NOT in empty set)
            // The bitset is already initialized to all false
            saveGenerator(null, new int[0], emptyDiffset);
        }
    }

    /**
     * Calculate the support of single items using diffsets.
     * For diffsets, we set all bits to true initially, then set to false 
     * for transactions containing the item.
     */
    @Override
    int calculateSupportSingleItems(TransactionDatabase database,
            final Map<Integer, BitSetSupport> mapItemDiffsets) {
        int maxItemId = 0;
        // for each transaction
        for (int i = 0; i < database.size(); i++) {
            // For each item in that transaction
            for (Integer item : database.getTransactions().get(i)) {
                // Get the current diffset of that item
                BitSetSupport diffset = mapItemDiffsets.get(item);
                // If none, then we create one
                if (diffset == null) {
                    diffset = new BitSetSupport();
                    // For a new item, we set all the bits of its diffset to true
                    diffset.bitset.set(0, database.size(), true);
                    mapItemDiffsets.put(item, diffset);
                    // we remember the largest item seen until now
                    if (item > maxItemId) {
                        maxItemId = item;
                    }
                }
                // We set to false the bit corresponding to this transaction
                // in the diffset of that item (transaction contains the item)
                diffset.bitset.set(i, false);
                // we increase the support of that item
                diffset.support++;
            }
        }
        return maxItemId;
    }

    /**
     * Perform the intersection of two diffsets for itemsets containing more than one item.
     * For diffsets: diffset(IJ|I) = diffset(J) - diffset(I)
     * Support: support(IJ) = support(I) - |diffset(IJ|I)|
     */
    @Override
    BitSetSupport performAND(BitSetSupport diffsetI, BitSetSupport diffsetJ) {
        // Create the new diffset
        BitSetSupport bitsetSupportIJ = new BitSetSupport();
        // Calculate the diffset: diffset(IJ|I) = diffset(J) - diffset(I)
        bitsetSupportIJ.bitset = (BitSet) diffsetJ.bitset.clone();
        bitsetSupportIJ.bitset.andNot(diffsetI.bitset);
        // Calculate the support
        bitsetSupportIJ.support = diffsetI.support - bitsetSupportIJ.bitset.cardinality();
        // return the new diffset
        return bitsetSupportIJ;
    }

    /**
     * Perform the intersection of two diffsets representing single items.
     * For diffsets: diffset(IJ|I) = diffset(J) - diffset(I)
     * Note: supportIJ parameter is not used for diffsets since we calculate it from diffset
     */
    @Override
    BitSetSupport performANDFirstTime(BitSetSupport diffsetI,
            BitSetSupport diffsetJ, int supportIJ) {
        // Create the new diffset and perform the operation
        BitSetSupport bitsetSupportIJ = new BitSetSupport();
        // Calculate the diffset
        bitsetSupportIJ.bitset = (BitSet) diffsetJ.bitset.clone();
        bitsetSupportIJ.bitset.andNot(diffsetI.bitset);
        // Calculate the support
        bitsetSupportIJ.support = diffsetI.support - bitsetSupportIJ.bitset.cardinality();
        // return the new diffset
        return bitsetSupportIJ;
    }

    /**
     * Process all itemsets from an equivalence class to generate larger generators.
     * This version uses diffset-based generator checking.
     */
    void processEquivalenceClass(int[] prefix, List<int[]> equivalenceClassItemsets,
            List<BitSetSupport> equivalenceClassDiffsets,
            int minsupRelative) throws IOException {

        // Calculate the current prefix length for max pattern length check
        int prefixLength = (prefix == null) ? 0 : prefix.length;

        // If there is only one itemset in equivalence class
        if (equivalenceClassItemsets.size() == 1) {
            int[] itemsetI = equivalenceClassItemsets.get(0);
            BitSetSupport diffsetI = equivalenceClassDiffsets.get(0);

            // Check if it's a generator using the hash table
            int[] fullItemset = ArraysAlgos.concatenate(prefix, itemsetI);
            if (isGenerator(fullItemset, diffsetI)) {
                saveGenerator(prefix, itemsetI, diffsetI);
            }
            return;
        }

        // If there are only two itemsets in the equivalence class
        if (equivalenceClassItemsets.size() == 2) {
            int[] itemsetI = equivalenceClassItemsets.get(0);
            BitSetSupport diffsetI = equivalenceClassDiffsets.get(0);

            int[] itemsetJ = equivalenceClassItemsets.get(1);
            BitSetSupport diffsetJ = equivalenceClassDiffsets.get(1);

            // Calculate the diffset of the union
            BitSetSupport diffsetIJ = performAND(diffsetI, diffsetJ);

            // Check and save itemsetI as generator
            int[] fullItemsetI = ArraysAlgos.concatenate(prefix, itemsetI);
            if (isGenerator(fullItemsetI, diffsetI)) {
                saveGenerator(prefix, itemsetI, diffsetI);
            }

            // Check and save itemsetJ as generator
            int[] fullItemsetJ = ArraysAlgos.concatenate(prefix, itemsetJ);
            if (isGenerator(fullItemsetJ, diffsetJ)) {
                saveGenerator(prefix, itemsetJ, diffsetJ);
            }

            // Check if the union would exceed max pattern length
            int[] suffixIJ = ArraysAlgos.concatenate(itemsetI, itemsetJ);
            int unionLength = prefixLength + suffixIJ.length;

            // If the union is within max length, frequent and a generator
            if (maximumPatternLength <= 0 || unionLength <= maximumPatternLength) {
                if (diffsetIJ.support >= minsupRelative) {
                    // Generator check using diffsets:
                    // If diffset(IJ|I) is empty, support(IJ) = support(I)
                    boolean sameSupportAsI = (diffsetIJ.bitset.cardinality() == 0);
                    
                    // Check if support(IJ) = support(J)
                    BitSet diffIJ_J = (BitSet) diffsetI.bitset.clone();
                    diffIJ_J.andNot(diffsetJ.bitset);
                    boolean sameSupportAsJ = (diffIJ_J.cardinality() == 0);

                    if (!sameSupportAsI && !sameSupportAsJ) {
                        int[] fullItemsetIJ = ArraysAlgos.concatenate(prefix, suffixIJ);
                        if (isGenerator(fullItemsetIJ, diffsetIJ)) {
                            saveGenerator(prefix, suffixIJ, diffsetIJ);
                        }
                    }
                }
            }
            return;
        }

        // General case: combine pairs of itemsets
        for (int i = 0; i < equivalenceClassItemsets.size(); i++) {
            int[] itemsetX = equivalenceClassItemsets.get(i);
            if (itemsetX == null) {
                continue;
            }
            BitSetSupport diffsetX = equivalenceClassDiffsets.get(i);

            // Check and save itemsetX as generator
            int[] fullItemsetX = ArraysAlgos.concatenate(prefix, itemsetX);
            if (isGenerator(fullItemsetX, diffsetX)) {
                saveGenerator(prefix, itemsetX, diffsetX);
            }

            // Calculate the new prefix length after adding itemsetX
            int[] newPrefix = ArraysAlgos.concatenate(prefix, itemsetX);
            int newPrefixLength = newPrefix.length;

            // Skip building equivalence class if next level would exceed max pattern length
            if (maximumPatternLength > 0 && newPrefixLength >= maximumPatternLength) {
                continue;
            }

            // Create equivalence class for extensions of X
            List<int[]> newEquivalenceClassItemsets = new ArrayList<int[]>();
            List<BitSetSupport> newEquivalenceClassDiffsets = new ArrayList<BitSetSupport>();

            for (int j = i + 1; j < equivalenceClassItemsets.size(); j++) {
                int[] itemsetJ = equivalenceClassItemsets.get(j);
                if (itemsetJ == null) {
                    continue;
                }

                BitSetSupport diffsetJ = equivalenceClassDiffsets.get(j);

                // Calculate the diffset intersection
                BitSetSupport diffsetXJ = performAND(diffsetX, diffsetJ);

                // If infrequent, skip
                if (diffsetXJ.support < minsupRelative) {
                    continue;
                }

                // Generator check using diffsets:
                // If diffset(XJ|X) is empty, support(XJ) = support(X)
                if (diffsetXJ.bitset.cardinality() == 0) {
                    continue;
                }
                
                // Check if support(XJ) = support(J)
                BitSet diffXJ_J = (BitSet) diffsetX.bitset.clone();
                diffXJ_J.andNot(diffsetJ.bitset);
                if (diffXJ_J.cardinality() == 0) {
                    continue;
                }

                // Check if a subset with same support exists in hash
                int[] candidateItemset = ArraysAlgos.concatenate(
                        ArraysAlgos.concatenate(prefix, itemsetX), itemsetJ);
                if (generatorHash.containsSubsetWithSameSupport(candidateItemset, 
                        diffsetXJ.support)) {
                    continue;
                }

                // Add to new equivalence class
                newEquivalenceClassItemsets.add(itemsetJ);
                newEquivalenceClassDiffsets.add(diffsetXJ);
            }

            // Process the new equivalence class
            if (newEquivalenceClassItemsets.size() > 0) {
                processEquivalenceClass(newPrefix, newEquivalenceClassItemsets, 
                        newEquivalenceClassDiffsets, minsupRelative);
            }
        }

        // check the memory usage
        MemoryLogger.getInstance().checkMemory();
    }

    @Override
    /**
     * Print statistics about the algorithm execution to System.out.
     */
    public void printStats() {
        System.out.println("============= TalkyG Diffset - STATS =============");
        long temps = endTime - startTimestamp;
        System.out.println(" Frequent generators count : " + generatorCount);
        System.out.println(" Total time ~ " + temps + " ms");
        System.out.println(" Maximum memory usage : "
                + MemoryLogger.getInstance().getMaxMemory() + " mb");
        System.out.println("===================================================");
    }
}