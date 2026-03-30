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
import java.util.Arrays;
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
 * This is an implementation of the TalkyG algorithm for mining frequent generators
 * that relies on bitsets to implement tidsets, following the CHARM implementation style.
 * TalkyG finds all frequent generators (FGs) from a transaction database.
 * A generator is an itemset that has no subset with the same support.
 * <br/><br/>
 * This version saves the result to a file or keeps it in memory if no output path
 * is provided by the user to the runAlgorithm method().
 * 
 * @see TriangularMatrix
 * @see TransactionDatabase
 * @see Itemset
 * @see Itemsets
 * @author Philippe Fournier-Viger
 */
public class AlgoTalkyG_Bitset {

    /** relative minimum support **/
	protected int minsupRelative;

    /** the transaction database **/
    protected TransactionDatabase database;

    /** start time of the last execution */
    protected long startTimestamp;

    /** end time of the last execution */
    protected long endTime;

    /**
     * The patterns that are found (if the user wants to keep them into memory)
     */
    protected Itemsets frequentGenerators;
    
    /** object to write the output file */
    protected BufferedWriter writer = null;

    /** the number of patterns found */
    protected int generatorCount;

    /** For optimization with a triangular matrix for counting itemsets of size 2. */
    protected TriangularMatrix matrix;

    /**
     * The hash table for storing itemsets for generator checking.
     * Used to check if a candidate has a subset with the same support.
     */
    protected GeneratorHashTable generatorHash;
    
    /** buffer size for storing itemsets */
    protected final int BUFFERS_SIZE = 2000;

    /** if true, transaction identifiers of each pattern will be shown */
    protected boolean showTransactionIdentifiers = false;

    /** maximum pattern length */
    protected int maximumPatternLength;

    /**
     * Default constructor
     */
    public AlgoTalkyG_Bitset() {
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
        this.minsupRelative = (int) Math.ceil(minsup * database.size());

        // (1) First database pass: calculate tidsets of each item.
        final Map<Integer, BitSetSupport> mapItemTIDS = new HashMap<Integer, BitSetSupport>();
        int maxItemId = calculateSupportSingleItems(database, mapItemTIDS);

        // If the user chose to use the triangular matrix optimization
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

        for (Entry<Integer, BitSetSupport> entry : mapItemTIDS.entrySet()) {
            BitSetSupport tidset = entry.getValue();
            int support = tidset.support;
            int item = entry.getKey();
            // if the item is frequent and not a full column (support < database size)
            if (support >= minsupRelative && support < database.size()) {
                frequentItems.add(item);
            }
        }

        // Check for empty set as generator (if there's NO full column)
        checkEmptySetGenerator(mapItemTIDS);

        // Sort the list of items by increasing support (total order)
        Collections.sort(frequentItems, new Comparator<Integer>() {
            @Override
            public int compare(Integer arg0, Integer arg1) {
                return mapItemTIDS.get(arg0).support - mapItemTIDS.get(arg1).support;
            }
        });

        // Save single item generators and build equivalence classes
        for (int i = 0; i < frequentItems.size(); i++) {
            Integer itemX = frequentItems.get(i);
            if (itemX == null) {
                continue;
            }

            BitSetSupport tidsetX = mapItemTIDS.get(itemX);
            int[] itemsetX = new int[] { itemX };

            // Save the single item as a generator
            saveGenerator(null, itemsetX, tidsetX);

            // Skip building equivalence class if max pattern length is 1
            if (maximumPatternLength > 0 && maximumPatternLength <= 1) {
                continue;
            }

            // Build equivalence class for extensions
            List<int[]> equivalenceClassItemsets = new ArrayList<int[]>();
            List<BitSetSupport> equivalenceClassTidsets = new ArrayList<BitSetSupport>();

            // For each item itemJ that is larger than i
            for (int j = i + 1; j < frequentItems.size(); j++) {
                Integer itemJ = frequentItems.get(j);
                if (itemJ == null) {
                    continue;
                }

                // Use triangular matrix optimization if available
                int supportIJ = -1;
                if (useTriangularMatrixOptimization) {
                    supportIJ = matrix.getSupportForItems(itemX, itemJ);
                    if (supportIJ < minsupRelative) {
                        continue;
                    }
                }

                BitSetSupport tidsetJ = mapItemTIDS.get(itemJ);

                // Calculate the tidset intersection
                BitSetSupport bitsetSupportUnion;
                if (useTriangularMatrixOptimization) {
                    bitsetSupportUnion = performANDFirstTime(tidsetX, tidsetJ, supportIJ);
                } else {
                    bitsetSupportUnion = performAND(tidsetX, tidsetJ);
                }

                // If infrequent, skip
                if (bitsetSupportUnion.support < minsupRelative) {
                    continue;
                }

                // Generator check: if the intersection equals either tidset,
                // then the candidate is NOT a generator (has subset with same support)
                if (bitsetSupportUnion.bitset.equals(tidsetX.bitset) || 
                    bitsetSupportUnion.bitset.equals(tidsetJ.bitset)) {
                    continue;
                }

                // The candidate {itemX, itemJ} is a potential generator
                equivalenceClassItemsets.add(new int[] { itemJ });
                equivalenceClassTidsets.add(bitsetSupportUnion);
            }

            // Process equivalence class to find larger generators
            if (equivalenceClassItemsets.size() > 0) {
                processEquivalenceClass(itemsetX, equivalenceClassItemsets, 
                        equivalenceClassTidsets, mapItemTIDS);
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
    private void checkEmptySetGenerator(Map<Integer, BitSetSupport> mapItemTIDS) throws IOException {
        // Check if any item has support equal to database size
        boolean hasFullColumn = false;
        for (Entry<Integer, BitSetSupport> entry : mapItemTIDS.entrySet()) {
            if (entry.getValue().support == database.size()) {
                hasFullColumn = true;
                break;
            }
        }
        
        // If there is NO item with full support, empty set is a generator
        if (!hasFullColumn) {
            BitSetSupport emptyTidset = new BitSetSupport();
            emptyTidset.support = database.size();
            for (int i = 0; i < database.size(); i++) {
                emptyTidset.bitset.set(i);
            }
            saveGenerator(null, new int[0], emptyTidset);
        }
    }

    /**
     * Calculate the support of single items.
     */
    int calculateSupportSingleItems(TransactionDatabase database,
            final Map<Integer, BitSetSupport> mapItemTIDS) {
        int maxItemId = 0;
        for (int i = 0; i < database.size(); i++) {
            for (Integer item : database.getTransactions().get(i)) {
                BitSetSupport tids = mapItemTIDS.get(item);
                if (tids == null) {
                    tids = new BitSetSupport();
                    mapItemTIDS.put(item, tids);
                    if (item > maxItemId) {
                        maxItemId = item;
                    }
                }
                tids.bitset.set(i);
                tids.support++;
            }
        }
        return maxItemId;
    }

    /**
     * Perform the intersection of two tidsets (first time, with known support).
     */
    BitSetSupport performANDFirstTime(BitSetSupport tidsetI, BitSetSupport tidsetJ, int supportIJ) {
        BitSetSupport bitsetSupportIJ = new BitSetSupport();
        bitsetSupportIJ.bitset = (BitSet) tidsetI.bitset.clone();
        bitsetSupportIJ.bitset.and(tidsetJ.bitset);
        bitsetSupportIJ.support = supportIJ;
        return bitsetSupportIJ;
    }

    /**
     * Perform the intersection of two tidsets.
     */
    BitSetSupport performAND(BitSetSupport tidsetI, BitSetSupport tidsetJ) {
        BitSetSupport bitsetSupportIJ = new BitSetSupport();
        bitsetSupportIJ.bitset = (BitSet) tidsetI.bitset.clone();
        bitsetSupportIJ.bitset.and(tidsetJ.bitset);
        bitsetSupportIJ.support = bitsetSupportIJ.bitset.cardinality();
        return bitsetSupportIJ;
    }

    /**
     * Process all itemsets from an equivalence class to generate larger generators.
     */
    void processEquivalenceClass(int[] prefix, List<int[]> equivalenceClassItemsets,
            List<BitSetSupport> equivalenceClassTidsets,
            Map<Integer, BitSetSupport> mapItemTIDS) throws IOException {

        // Calculate the current prefix length for max pattern length check
        int prefixLength = (prefix == null) ? 0 : prefix.length;

        // If there is only one itemset in equivalence class
        if (equivalenceClassItemsets.size() == 1) {
            int[] itemsetI = equivalenceClassItemsets.get(0);
            BitSetSupport tidsetI = equivalenceClassTidsets.get(0);

            // Check if it's a generator using the hash table
            int[] fullItemset = ArraysAlgos.concatenate(prefix, itemsetI);
            if (isGenerator(fullItemset, tidsetI)) {
                saveGenerator(prefix, itemsetI, tidsetI);
            }
            return;
        }

        // If there are only two itemsets in the equivalence class
        if (equivalenceClassItemsets.size() == 2) {
            int[] itemsetI = equivalenceClassItemsets.get(0);
            BitSetSupport tidsetI = equivalenceClassTidsets.get(0);

            int[] itemsetJ = equivalenceClassItemsets.get(1);
            BitSetSupport tidsetJ = equivalenceClassTidsets.get(1);

            // Calculate the tidset of the union
            BitSetSupport bitsetSupportIJ = performAND(tidsetI, tidsetJ);

            // Check and save itemsetI as generator
            int[] fullItemsetI = ArraysAlgos.concatenate(prefix, itemsetI);
            if (isGenerator(fullItemsetI, tidsetI)) {
                saveGenerator(prefix, itemsetI, tidsetI);
            }

            // Check and save itemsetJ as generator
            int[] fullItemsetJ = ArraysAlgos.concatenate(prefix, itemsetJ);
            if (isGenerator(fullItemsetJ, tidsetJ)) {
                saveGenerator(prefix, itemsetJ, tidsetJ);
            }

            // Check if the union would exceed max pattern length
            int[] suffixIJ = ArraysAlgos.concatenate(itemsetI, itemsetJ);
            int unionLength = prefixLength + suffixIJ.length;

            // If the union is within max length, frequent and a generator
            if (maximumPatternLength <= 0 || unionLength <= maximumPatternLength) {
                if (bitsetSupportIJ.support >= minsupRelative) {
                    // Generator check: union's tidset should not equal either parent's tidset
                    if (!bitsetSupportIJ.bitset.equals(tidsetI.bitset) && 
                        !bitsetSupportIJ.bitset.equals(tidsetJ.bitset)) {
                        int[] fullItemsetIJ = ArraysAlgos.concatenate(prefix, suffixIJ);
                        if (isGenerator(fullItemsetIJ, bitsetSupportIJ)) {
                            saveGenerator(prefix, suffixIJ, bitsetSupportIJ);
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
            BitSetSupport tidsetX = equivalenceClassTidsets.get(i);

            // Check and save itemsetX as generator
            int[] fullItemsetX = ArraysAlgos.concatenate(prefix, itemsetX);
            if (isGenerator(fullItemsetX, tidsetX)) {
                saveGenerator(prefix, itemsetX, tidsetX);
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
            List<BitSetSupport> newEquivalenceClassTidsets = new ArrayList<BitSetSupport>();

            for (int j = i + 1; j < equivalenceClassItemsets.size(); j++) {
                int[] itemsetJ = equivalenceClassItemsets.get(j);
                if (itemsetJ == null) {
                    continue;
                }

                BitSetSupport tidsetJ = equivalenceClassTidsets.get(j);

                // Calculate the tidset intersection
                BitSetSupport bitsetSupportUnion = performAND(tidsetX, tidsetJ);

                // If infrequent, skip
                if (bitsetSupportUnion.support < minsupRelative) {
                    continue;
                }

                // Generator check: if intersection equals either parent's tidset, not a generator
                if (bitsetSupportUnion.bitset.equals(tidsetX.bitset) || 
                    bitsetSupportUnion.bitset.equals(tidsetJ.bitset)) {
                    continue;
                }

                // Check if a subset with same support exists in hash
                int[] candidateItemset = ArraysAlgos.concatenate(
                        ArraysAlgos.concatenate(prefix, itemsetX), itemsetJ);
                if (generatorHash.containsSubsetWithSameSupport(candidateItemset, 
                        bitsetSupportUnion.support)) {
                    continue;
                }

                // Add to new equivalence class
                newEquivalenceClassItemsets.add(itemsetJ);
                newEquivalenceClassTidsets.add(bitsetSupportUnion); 
            }

            // Process the new equivalence class
            if (newEquivalenceClassItemsets.size() > 0) {
                processEquivalenceClass(newPrefix, newEquivalenceClassItemsets, 
                        newEquivalenceClassTidsets, mapItemTIDS);
            }
        }

        // check the memory usage
        MemoryLogger.getInstance().checkMemory();
    }

    /**
     * Check if an itemset is a generator (no subset has the same support).
     */
    protected boolean isGenerator(int[] itemset, BitSetSupport tidset) {
        return !generatorHash.containsSubsetWithSameSupport(itemset, tidset.support);
    }

    /**
     * Set that the transaction identifiers should be shown (true) or not (false).
     */
    public void setShowTransactionIdentifiers(boolean showTransactionIdentifiers) {
        this.showTransactionIdentifiers = showTransactionIdentifiers;
    }

    /**
     * Print statistics about the algorithm execution to System.out.
     */
    public void printStats() {
        System.out.println("============= TalkyG Bitset - STATS =============");
        long temps = endTime - startTimestamp;
        System.out.println(" Frequent generators count : " + generatorCount);
        System.out.println(" Total time ~ " + temps + " ms");
        System.out.println(" Maximum memory usage : "
                + MemoryLogger.getInstance().getMaxMemory() + " mb");
        System.out.println("===================================================");
    }

    /**
     * Get the set of frequent generators.
     */
    public Itemsets getFrequentGenerators() {
        return frequentGenerators;
    }

    /**
     * Anonymous inner class to store a bitset and its cardinality.
     */
    public class BitSetSupport {
        BitSet bitset = new BitSet();
        int support;
    }

    /**
     * Save a generator itemset.
     */
    void saveGenerator(int[] prefix, int[] suffix, BitSetSupport tidset) throws IOException {
        // Concatenate prefix and suffix
        int[] fullItemset;
        if (prefix == null || prefix.length == 0) {
            fullItemset = suffix;
        } else {
            fullItemset = ArraysAlgos.concatenate(prefix, suffix);
        }

        // Check maximum pattern length constraint
        if (maximumPatternLength > 0 && fullItemset.length > maximumPatternLength) {
            return;
        }

        // Sort the itemset
        Arrays.sort(fullItemset);

        // Create itemset for hash table
        ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset itemset =
            new ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset(fullItemset);
        itemset.setAbsoluteSupport(tidset.support);

        // Add to hash table
        generatorHash.put(itemset, tidset.support);

        // Increase the generator count
        generatorCount++;

        // Save to memory or file
        if (writer == null) {
            Itemset itemsetWithTidset = new Itemset(fullItemset, tidset.bitset, tidset.support);
            frequentGenerators.addItemset(itemsetWithTidset, itemset.size());
        } else {
        	String itemsetString = itemset.size() == 0 ? "" : itemset.toString();
            writer.write(itemsetString + "#SUP: " + itemset.support);
            if (showTransactionIdentifiers) {
                BitSet bitset = tidset.bitset;
                writer.append(" #TID:");
                for (int tid = bitset.nextSetBit(0); tid != -1; tid = bitset.nextSetBit(tid + 1)) {
                    writer.append(" " + tid);
                }
            }
            writer.newLine();
        }
    }

    /**
     * Set the maximum pattern length
     * @param length the maximum length (use 0 or negative for no limit)
     */
    public void setMaximumPatternLength(int length) {
        maximumPatternLength = length;
    }
}