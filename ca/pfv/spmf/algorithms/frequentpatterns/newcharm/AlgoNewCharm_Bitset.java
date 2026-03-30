package ca.pfv.spmf.algorithms.frequentpatterns.newcharm;

/* This file is copyright (c) 2024
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

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.TreeMap;

import ca.pfv.spmf.algorithms.ArraysAlgos;
import ca.pfv.spmf.datastructures.triangularmatrix.TriangularMatrix;
import ca.pfv.spmf.input.transaction_database_list_integers.TransactionDatabase;
import ca.pfv.spmf.patterns.itemset_array_integers_with_tids_bitset.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_tids_bitset.Itemsets;
import ca.pfv.spmf.tools.MemoryLogger;
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
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
/**
 * Implementation of the NEWCHARM algorithm for mining frequent closed itemsets.
 * 
 * NEWCHARM is an optimization of CHARM that eliminates the need for a hash table
 * by using a checkSet mechanism to identify non-closed itemsets during the search
 * process. This makes it more memory-efficient and potentially faster.
 * 
 * The key insight is that we can identify non-closed itemsets by checking if their
 * tidset is a subset of any tidset in the checkSet (which contains tidsets of
 * previously processed nodes in the IT-tree).
 * 
 * Based on the paper: "An Optimization to CHARM for Mining Frequent Closed Itemsets"
 * 
 * @see TriangularMatrix
 * @see TransactionDatabase
 * @see Itemset
 * @see Itemsets
 * @author Philippe Fournier-Viger
 */
public class AlgoNewCharm_Bitset {

    /** Relative minimum support threshold */
    private int minsupRelative;
    
    /** Size of the transaction database (stored instead of full reference) */
    private int databaseSize;

    /** Start time of the last execution */
    private long startTimestamp;
    
    /** End time of the last execution */
    private long endTime;
    
    /** The patterns that are found (if the user wants to keep them in memory) */
    private Itemsets closedItemsets;
    
    /** Object to write the output file */
    private BufferedWriter writer = null;
    
    /** The number of patterns found */
    private int itemsetCount;
    
    /** Triangular matrix for counting itemsets of size 2 (optimization) */
    private TriangularMatrix matrix;
    
    /** If true, transaction identifiers of each pattern will be shown */
    private boolean showTransactionIdentifiers = false;

    /** Reuse StringBuilder across writes **/
    private final StringBuilder outputBuffer = new StringBuilder(256);
    
    /** Object pool for BitSetSupport to reduce allocations */
    private final ArrayDeque<BitSetSupport> bitsetPool = new ArrayDeque<>(1000);
    
    /** Maximum pool size to prevent unbounded memory growth */
    private static final int MAX_POOL_SIZE = 1000;
    
    /** 
     * Maximum bitset capacity (in bits) to keep in pool.
     * Avoids keeping huge bitsets that waste memory.
     * BitSet.size() returns capacity in bits.
     */
    private static final int MAX_POOLED_BITSET_CAPACITY = 8192; // ~8K transactions

    /**
     * Default constructor
     */
    public AlgoNewCharm_Bitset() {
    }

    /**
     * Run the NEWCHARM algorithm.
     * 
     * @param output an output file path for writing the result or null to keep in memory
     * @param database a transaction database
     * @param minsup the minimum support threshold (percentage)
     * @param useTriangularMatrixOptimization if true, use triangular matrix optimization
     * @return the set of closed itemsets found if kept in memory, null otherwise
     * @throws IOException if error while writing to file
     * @throws IllegalArgumentException if database is null or minsup is invalid
     */
    public Itemsets runAlgorithm(String output, TransactionDatabase database, double minsup,
            boolean useTriangularMatrixOptimization) throws IOException {

        // Input validation
        if (database == null) {
            throw new IllegalArgumentException("Database cannot be null");
        }
        if (minsup < 0 || minsup > 1) {
            throw new IllegalArgumentException("Minimum support must be between 0 and 1");
        }

        // Reset memory logger
        MemoryLogger.getInstance().reset();

        // Reset itemset count
        itemsetCount = 0;
        
        // Clear the object pool for fresh run
        bitsetPool.clear();

        // Store database size instead of full reference
        this.databaseSize = database.size();

        // Record start time
        startTimestamp = System.currentTimeMillis();

        // Calculate relative minimum support
        this.minsupRelative = (int) Math.ceil(minsup * databaseSize);

        // Setup output - use try-with-resources for file output
        if (output == null) {
            writer = null;
            closedItemsets = new Itemsets("FREQUENT CLOSED ITEMSETS");
            // Process without file output
            processDatabase(database, useTriangularMatrixOptimization);
        } else {
            closedItemsets = null;
            // Use try-with-resources for proper resource management
            try (BufferedWriter fileWriter = new BufferedWriter(new FileWriter(output))) {
                this.writer = fileWriter;
                processDatabase(database, useTriangularMatrixOptimization);
            } finally {
                this.writer = null; // Clear reference after use
            }
        }

        // Check memory usage
        MemoryLogger.getInstance().checkMemory();

        // Record end time
        endTime = System.currentTimeMillis();

        return closedItemsets;
    }
    
    /**
     * Borrow a BitSetSupport from the object pool, or create a new one if pool is empty.
     * This reduces object allocation overhead in hot paths.
     * 
     * @return a BitSetSupport instance ready for use
     */
    private BitSetSupport borrowBitSetSupport() {
        BitSetSupport bs = bitsetPool.pollFirst();
        if (bs == null) {
            bs = new BitSetSupport();
        } else {
            bs.bitset.clear();
            bs.support = 0;
        }
        return bs;
    }
    
    /**
     * Return a BitSetSupport to the object pool for reuse.
     * Only pools BitSets that aren't too large to avoid memory bloat.
     * 
     * Note: BitSet.size() returns capacity in bits. We check capacity rather than
     * cardinality because clear() doesn't shrink the internal long[] array.
     * Large capacity BitSets would waste memory if kept in the pool.
     * 
     * @param bs the BitSetSupport to return to the pool
     */
    private void returnBitSetSupport(BitSetSupport bs) {
        if (bs != null && bitsetPool.size() < MAX_POOL_SIZE) {
            // Don't pool excessively large bitsets - they waste memory
            if (bs.bitset.size() <= MAX_POOLED_BITSET_CAPACITY) {
                bitsetPool.addFirst(bs);
            }
            // Large bitsets are left for GC - this is intentional
        }
    }

    /**
     * Process the database to find frequent closed itemsets.
     * This method contains the core algorithm logic extracted for proper resource management.
     * 
     * @param database the transaction database
     * @param useTriangularMatrixOptimization if true, use triangular matrix optimization
     * @throws IOException if error while writing to file
     */
    private void processDatabase(TransactionDatabase database, 
            boolean useTriangularMatrixOptimization) throws IOException {

        // (1) First database pass: calculate tidsets of each item
        // Also build triangular matrix in same pass if optimization enabled
        final Map<Integer, BitSetSupport> mapItemTIDS = new HashMap<Integer, BitSetSupport>();
        calculateSupportSingleItems(database, mapItemTIDS, useTriangularMatrixOptimization);

        // (2) Create list of frequent single items
        List<Integer> frequentItems = new ArrayList<Integer>();
        for (Entry<Integer, BitSetSupport> entry : mapItemTIDS.entrySet()) {
            BitSetSupport tidset = entry.getValue();
            int support = tidset.support;
            int item = entry.getKey();
            if (support >= minsupRelative) {
                frequentItems.add(item);
            }
        }

        // Sort by increasing support (total order f)
        Collections.sort(frequentItems, new Comparator<Integer>() {
            @Override
            public int compare(Integer arg0, Integer arg1) {
                return mapItemTIDS.get(arg0).support - mapItemTIDS.get(arg1).support;
            }
        });

        // Initialize bucketed checkSet for the root level
        BucketedCheckSet checkSet = new BucketedCheckSet();
        
        // Reusable BitSet for subset checking - passed to methods that need it
        BitSet tempBitset = new BitSet();

        // Process each frequent item according to total order
        for (int i = 0; i < frequentItems.size(); i++) {
            Integer itemX = frequentItems.get(i);
            // Null check required: items are nullified by CHARM Property 1 and 3
            if (itemX == null) {
                continue;
            }

            BitSetSupport tidsetX = mapItemTIDS.get(itemX);
            int[] itemsetX = new int[] { itemX };

            // Create empty equivalence class for children
            List<int[]> equivalenceClassIitemsets = new ArrayList<int[]>();
            List<BitSetSupport> equivalenceClassItidsets = new ArrayList<BitSetSupport>();

            // Combine with all items coming after in total order
            for (int j = i + 1; j < frequentItems.size(); j++) {
                Integer itemJ = frequentItems.get(j);
                // Null check required: items are nullified by CHARM Property 1 and 3
                if (itemJ == null) {
                    continue;
                }

                // Check support using triangular matrix if available (avoids intersection)
                if (useTriangularMatrixOptimization && itemsetX.length == 1) {
                    int supportIJ = matrix.getSupportForItems(itemX, itemJ);
                    if (supportIJ < minsupRelative) {
                        continue;
                    }
                }

                BitSetSupport tidsetJ = mapItemTIDS.get(itemJ);

                // Calculate tidset intersection
                // Note: We always need the full tidset, not just support, because:
                // 1. CHARM properties check subset relationships using support comparisons
                // 2. The tidset is added to equivalence classes for further processing
                // 3. The tidset may be added to checkSet
                BitSetSupport tidsetIntersection = performAND(tidsetX, tidsetJ);

                // Skip if infrequent
                if (tidsetIntersection.support < minsupRelative) {
                    // Return to pool since we won't use it
                    returnBitSetSupport(tidsetIntersection);
                    continue;
                }

                // Apply the four CHARM properties and process accordingly
                itemsetX = applyCharmProperty(itemsetX, itemJ, tidsetX, tidsetJ, 
                        tidsetIntersection, frequentItems, j, 
                        equivalenceClassIitemsets, equivalenceClassItidsets, checkSet, tempBitset);
            }

            // NEWCHARM: Write out Xi as closed itemset BEFORE processing children
            save(null, itemsetX, tidsetX);

            // Process equivalence class if non-empty
            if (!equivalenceClassIitemsets.isEmpty()) {
                // Track checkSet size for restoration after recursion
                int checkSetSizeBefore = checkSet.size();
                processEquivalenceClass(itemsetX, equivalenceClassIitemsets,
                        equivalenceClassItidsets, checkSet, tempBitset);
                // Restore checkSet to original size
                checkSet.restoreToSize(checkSetSizeBefore);
            }

            // Add tidset to checkSet for subsequent siblings
            checkSet.add(tidsetX);
        }
    }

    /**
     * Apply the appropriate CHARM property and update data structures accordingly.
     * 
     * Properties (where X = Xi ∪ Xj):
     * - Property 1: t(Xi) = t(Xj) → c(Xi) = c(Xj) = c(X). Remove Xj, replace Xi with X
     * - Property 2: t(Xi) ⊂ t(Xj) → c(Xi) = c(X), c(Xj) ≠ c(X). Replace Xi with X  
     * - Property 3: t(Xi) ⊃ t(Xj) → c(Xj) = c(X), c(Xi) ≠ c(X). Remove Xj, add X to [Pi]
     * - Property 4: t(Xi) ≁ t(Xj) → c(Xi) ≠ c(Xj) ≠ c(X). Add X to [Pi]
     *
     * @param itemsetX current itemset X
     * @param itemJ item J to combine with
     * @param tidsetX tidset of X
     * @param tidsetJ tidset of J
     * @param tidsetIJ tidset of X ∪ J (intersection of tidsets: t(Xi) ∩ t(Xj))
     * @param itemList list of items (for nullifying in Property 1 and 3)
     * @param indexJ index of J in itemList
     * @param equivItemsets equivalence class itemsets to add to
     * @param equivTidsets equivalence class tidsets to add to
     * @param checkSet checkSet for superset checking
     * @param tempBitset reusable BitSet for subset checking
     * @return the (possibly modified) itemsetX
     */
    private int[] applyCharmProperty(int[] itemsetX, int itemJ,
            BitSetSupport tidsetX, BitSetSupport tidsetJ, BitSetSupport tidsetIJ,
            List<Integer> itemList, int indexJ,
            List<int[]> equivItemsets, List<BitSetSupport> equivTidsets,
            BucketedCheckSet checkSet, BitSet tempBitset) {
        
        // tidsetIJ is the INTERSECTION t(Xi) ∩ t(Xj)
        // Check subset/superset relationships using intersection properties:
        // - t(Xi) ⊆ t(Xj) iff t(Xi) ∩ t(Xj) = t(Xi) iff |intersection| = |t(Xi)|
        // - t(Xi) ⊇ t(Xj) iff t(Xi) ∩ t(Xj) = t(Xj) iff |intersection| = |t(Xj)|
        
        boolean xiSubsetOfXj = (tidsetIJ.support == tidsetX.support);
        boolean xjSubsetOfXi = (tidsetIJ.support == tidsetJ.support);
        
        if (xiSubsetOfXj && xjSubsetOfXi) {
            // Property 1: t(Xi) = t(Xj) (equal tidsets)
            // c(Xi) = c(Xj) = c(X)
            // Action: Remove Xj from [P], replace all Xi with X = Xi ∪ Xj
            itemList.set(indexJ, null);
            // Return tidsetIJ to pool since we won't keep it
            returnBitSetSupport(tidsetIJ);
            return appendItem(itemsetX, itemJ);
            
        } else if (xiSubsetOfXj) {
            // Property 2: t(Xi) ⊂ t(Xj) (Xi's tidset is proper subset of Xj's)
            // c(Xi) = c(X), but c(Xj) ≠ c(X)
            // Action: Replace all Xi with X = Xi ∪ Xj (Xj stays in [P])
            // Return tidsetIJ to pool since we won't keep it
            returnBitSetSupport(tidsetIJ);
            return appendItem(itemsetX, itemJ);
            
        } else if (xjSubsetOfXi) {
            // Property 3: t(Xi) ⊃ t(Xj) (Xi's tidset is proper superset of Xj's)
            // c(Xj) = c(X), but c(Xi) ≠ c(X)
            // Action: Remove Xj from [P], add X = Xi ∪ Xj to [Pi] if not subsumed
            itemList.set(indexJ, null);
            if (!checkSet.existSuperset(tidsetIJ, tempBitset)) {
                equivItemsets.add(new int[] { itemJ });
                equivTidsets.add(tidsetIJ);
            } else {
                // Return to pool if not used
                returnBitSetSupport(tidsetIJ);
            }
            return itemsetX;
            
        } else {
            // Property 4: t(Xi) ≁ t(Xj) (tidsets are incomparable)
            // c(Xi) ≠ c(Xj) ≠ c(X)
            // Action: Add X = Xi ∪ Xj to [Pi] if not subsumed (both Xi and Xj stay)
            if (!checkSet.existSuperset(tidsetIJ, tempBitset)) {
                equivItemsets.add(new int[] { itemJ });
                equivTidsets.add(tidsetIJ);
            } else {
                // Return to pool if not used
                returnBitSetSupport(tidsetIJ);
            }
            return itemsetX;
        }
    }
    
    /**
     * Apply the appropriate CHARM property for equivalence class processing.
     * 
     * @param itemsetX current itemset X (suffix)
     * @param itemsetJ itemset J (suffix) to combine with
     * @param tidsetX tidset of X
     * @param tidsetJ tidset of J
     * @param tidsetIJ tidset of X ∪ J (intersection of tidsets)
     * @param equivItemsets equivalence class itemsets list (for nullifying)
     * @param equivTidsets equivalence class tidsets list (for nullifying)
     * @param indexJ index of J in equivalence class
     * @param childItemsets child equivalence class itemsets to add to
     * @param childTidsets child equivalence class tidsets to add to
     * @param checkSet checkSet for superset checking
     * @param tempBitset reusable BitSet for subset checking
     * @return the (possibly modified) itemsetX
     */
    private int[] applyCharmPropertyEquiv(int[] itemsetX, int[] itemsetJ,
            BitSetSupport tidsetX, BitSetSupport tidsetJ, BitSetSupport tidsetIJ,
            List<int[]> equivItemsets, List<BitSetSupport> equivTidsets, int indexJ,
            List<int[]> childItemsets, List<BitSetSupport> childTidsets,
            BucketedCheckSet checkSet, BitSet tempBitset) {
        
        boolean xiSubsetOfXj = (tidsetIJ.support == tidsetX.support);
        boolean xjSubsetOfXi = (tidsetIJ.support == tidsetJ.support);
        
        if (xiSubsetOfXj && xjSubsetOfXi) {
            // Property 1: t(Xi) = t(Xj)
            equivItemsets.set(indexJ, null);
            // FIX: Return the nullified tidset to pool before discarding
            BitSetSupport oldTidset = equivTidsets.set(indexJ, null);
            returnBitSetSupport(oldTidset);
            returnBitSetSupport(tidsetIJ);
            return ArraysAlgos.concatenate(itemsetX, itemsetJ);
            
        } else if (xiSubsetOfXj) {
            // Property 2: t(Xi) ⊂ t(Xj)
            returnBitSetSupport(tidsetIJ);
            return ArraysAlgos.concatenate(itemsetX, itemsetJ);
            
        } else if (xjSubsetOfXi) {
            // Property 3: t(Xi) ⊃ t(Xj)
            equivItemsets.set(indexJ, null);
            // FIX: Return the nullified tidset to pool before discarding
            BitSetSupport oldTidset = equivTidsets.set(indexJ, null);
            returnBitSetSupport(oldTidset);
            if (!checkSet.existSuperset(tidsetIJ, tempBitset)) {
                childItemsets.add(itemsetJ);
                childTidsets.add(tidsetIJ);
            } else {
                returnBitSetSupport(tidsetIJ);
            }
            return itemsetX;
            
        } else {
            // Property 4: t(Xi) ≁ t(Xj)
            if (!checkSet.existSuperset(tidsetIJ, tempBitset)) {
                childItemsets.add(itemsetJ);
                childTidsets.add(tidsetIJ);
            } else {
                returnBitSetSupport(tidsetIJ);
            }
            return itemsetX;
        }
    }
    
    /**
     * Append an item to an itemset array.
     * This is more efficient than ArraysAlgos.concatenate for single items
     * as it avoids creating an intermediate int[] array for the single item.
     * 
     * @param itemset the original itemset
     * @param item the item to append
     * @return new array with item appended
     */
    private int[] appendItem(int[] itemset, int item) {
        int[] result = new int[itemset.length + 1];
        System.arraycopy(itemset, 0, result, 0, itemset.length);
        result[itemset.length] = item;
        return result;
    }

    /**
     * Calculate the support of single items and store their tidsets.
     * Also builds the triangular matrix in the same pass if optimization is enabled.
     * 
     * @param database the transaction database
     * @param mapItemTIDS map to store item -> tidset mapping
     * @param useTriangularMatrixOptimization if true, build triangular matrix
     */
    private void calculateSupportSingleItems(TransactionDatabase database,
            final Map<Integer, BitSetSupport> mapItemTIDS,
            boolean useTriangularMatrixOptimization) {
        
        // First pass to find maxItemId (needed for triangular matrix initialization)
        int maxItemId = 0;
        List<List<Integer>> transactions = database.getTransactions();
        for (int i = 0; i < transactions.size(); i++) {
            for (Integer item : transactions.get(i)) {
                if (item > maxItemId) {
                    maxItemId = item;
                }
            }
        }
        
        // Initialize triangular matrix if needed
        if (useTriangularMatrixOptimization) {
            matrix = new TriangularMatrix(maxItemId + 1);
        }
        
        // Second pass: build tidsets and triangular matrix together
        for (int i = 0; i < transactions.size(); i++) {
            List<Integer> transaction = transactions.get(i);
            int transactionSize = transaction.size();
            
            // For each item in the transaction
            for (int j = 0; j < transactionSize; j++) {
                Integer item = transaction.get(j);
                
                // Get the current tidset of that item
                BitSetSupport tids = mapItemTIDS.get(item);
                // If no tidset exists, create one
                if (tids == null) {
                    tids = new BitSetSupport();
                    mapItemTIDS.put(item, tids);
                }
                // Add current transaction id to the tidset
                tids.bitset.set(i);
                // Increase the support
                tids.support++;
                
                // Build triangular matrix in same pass
                if (useTriangularMatrixOptimization) {
                    for (int k = j + 1; k < transactionSize; k++) {
                        Integer itemK = transaction.get(k);
                        matrix.incrementCount(item, itemK);
                    }
                }
            }
        }
    }

    /**
     * Perform the intersection of two tidsets.
     * Uses object pooling to reduce allocations.
     * 
     * @param tidsetI the first tidset
     * @param tidsetJ the second tidset
     * @return the resulting tidset and its support
     */
    private BitSetSupport performAND(BitSetSupport tidsetI, BitSetSupport tidsetJ) {
        BitSetSupport bitsetSupportIJ = borrowBitSetSupport();
        bitsetSupportIJ.bitset.or(tidsetI.bitset);
        bitsetSupportIJ.bitset.and(tidsetJ.bitset);
        bitsetSupportIJ.support = bitsetSupportIJ.bitset.cardinality();
        return bitsetSupportIJ;
    }

    /**
     * Process an equivalence class to find larger closed itemsets.
     * 
     * @param prefix the prefix of all itemsets in the equivalence class
     * @param equivalenceClassItemsets the suffixes of itemsets in the equivalence class
     * @param equivalenceClassTidsets the tidsets of itemsets in the equivalence class
     * @param checkSet the current checkSet for identifying non-closed itemsets
     * @param tempBitset reusable BitSet for subset checking
     * @throws IOException if error while writing to file
     */
    private void processEquivalenceClass(int[] prefix, List<int[]> equivalenceClassItemsets,
            List<BitSetSupport> equivalenceClassTidsets, BucketedCheckSet checkSet,
            BitSet tempBitset) throws IOException {

        int size = equivalenceClassItemsets.size();
        
        // Special case: only one itemset in equivalence class
        if (size == 1) {
            save(prefix, equivalenceClassItemsets.get(0), equivalenceClassTidsets.get(0));
            return;
        }

        // Special case: two itemsets in equivalence class
        if (size == 2) {
            processTwoItemEquivalenceClass(prefix, equivalenceClassItemsets, 
                    equivalenceClassTidsets, checkSet, tempBitset);
            return;
        }

        // General case: more than two itemsets
        processGeneralEquivalenceClass(prefix, equivalenceClassItemsets, 
                equivalenceClassTidsets, checkSet, tempBitset);
    }
    
    /**
     * Process an equivalence class with exactly two itemsets.
     * 
     * @param prefix the prefix of all itemsets in the equivalence class
     * @param equivalenceClassItemsets the suffixes of itemsets in the equivalence class
     * @param equivalenceClassTidsets the tidsets of itemsets in the equivalence class
     * @param checkSet the current checkSet for identifying non-closed itemsets
     * @param tempBitset reusable BitSet for subset checking
     * @throws IOException if error while writing to file
     */
    private void processTwoItemEquivalenceClass(int[] prefix, List<int[]> equivalenceClassItemsets,
            List<BitSetSupport> equivalenceClassTidsets, BucketedCheckSet checkSet,
            BitSet tempBitset) throws IOException {
        
        int[] itemsetI = equivalenceClassItemsets.get(0);
        BitSetSupport tidsetI = equivalenceClassTidsets.get(0);
        int[] itemsetJ = equivalenceClassItemsets.get(1);
        BitSetSupport tidsetJ = equivalenceClassTidsets.get(1);

        // Calculate intersection
        BitSetSupport tidsetIJ = performAND(tidsetI, tidsetJ);
        int supportIJ = tidsetIJ.support;

        if (supportIJ >= minsupRelative) {
            // Check if superset exists before saving the combined itemset
            if (!checkSet.existSuperset(tidsetIJ, tempBitset)) {
                int[] suffixIJ = ArraysAlgos.concatenate(itemsetI, itemsetJ);
                save(prefix, suffixIJ, tidsetIJ);
            }
        }
        
        // Return tidsetIJ to pool after use
        returnBitSetSupport(tidsetIJ);

        // Save prefix+I if it has different support than prefix+I+J
        if (supportIJ != tidsetI.support) {
            save(prefix, itemsetI, tidsetI);
        }
        // Save prefix+J if it has different support than prefix+I+J
        if (supportIJ != tidsetJ.support) {
            save(prefix, itemsetJ, tidsetJ);
        }
    }
    
    /**
     * Process an equivalence class with more than two itemsets.
     * 
     * @param prefix the prefix of all itemsets in the equivalence class
     * @param equivalenceClassItemsets the suffixes of itemsets in the equivalence class
     * @param equivalenceClassTidsets the tidsets of itemsets in the equivalence class
     * @param checkSet the current checkSet for identifying non-closed itemsets
     * @param tempBitset reusable BitSet for subset checking
     * @throws IOException if error while writing to file
     */
    private void processGeneralEquivalenceClass(int[] prefix, List<int[]> equivalenceClassItemsets,
            List<BitSetSupport> equivalenceClassTidsets, BucketedCheckSet checkSet,
            BitSet tempBitset) throws IOException {
        
        for (int i = 0; i < equivalenceClassItemsets.size(); i++) {
            int[] itemsetX = equivalenceClassItemsets.get(i);
            // Null check required: items are nullified by CHARM Property 1 and 3
            if (itemsetX == null) {
                continue;
            }
            BitSetSupport tidsetX = equivalenceClassTidsets.get(i);

            // Create empty equivalence class for children of X
            List<int[]> childItemsets = new ArrayList<int[]>();
            List<BitSetSupport> childTidsets = new ArrayList<BitSetSupport>();

            // Combine X with all itemsets coming after in total order
            for (int j = i + 1; j < equivalenceClassItemsets.size(); j++) {
                int[] itemsetJ = equivalenceClassItemsets.get(j);
                // Null check required: items are nullified by CHARM Property 1 and 3
                if (itemsetJ == null) {
                    continue;
                }

                BitSetSupport tidsetJ = equivalenceClassTidsets.get(j);
                BitSetSupport tidsetIJ = performAND(tidsetX, tidsetJ);

                // Skip if infrequent
                if (tidsetIJ.support < minsupRelative) {
                    // Return to pool since we won't use it
                    returnBitSetSupport(tidsetIJ);
                    continue;
                }

                // Apply CHARM property
                itemsetX = applyCharmPropertyEquiv(itemsetX, itemsetJ, tidsetX, tidsetJ,
                        tidsetIJ, equivalenceClassItemsets, equivalenceClassTidsets, j,
                        childItemsets, childTidsets, checkSet, tempBitset);
            }

            // Save current itemset
            save(prefix, itemsetX, tidsetX);

            // Process children if equivalence class is non-empty
            if (!childItemsets.isEmpty()) {
                int[] newPrefix = ArraysAlgos.concatenate(prefix, itemsetX);
                // Track checkSet size for restoration after recursion
                int checkSetSizeBefore = checkSet.size();
                processEquivalenceClass(newPrefix, childItemsets, childTidsets, checkSet, tempBitset);
                // Restore checkSet to original size
                checkSet.restoreToSize(checkSetSizeBefore);
            }

            // Add tidset to checkSet for subsequent siblings
            checkSet.add(tidsetX);
        }

        // Check memory usage
        MemoryLogger.getInstance().checkMemory();
    }

    /**
     * Save a closed itemset.
     * 
     * @param prefix the prefix part of this itemset (may be null)
     * @param suffix the suffix part of this itemset
     * @param tidset the tidset of this itemset
     * @throws IOException if error while writing to file
     */
    private void save(int[] prefix, int[] suffix, BitSetSupport tidset) throws IOException {
        // Concatenate prefix and suffix
        int[] prefixSuffix;
        if (prefix == null) {
            // Clone suffix to avoid modifying the original when sorting
            prefixSuffix = suffix.clone();
        } else {
            prefixSuffix = ArraysAlgos.concatenate(prefix, suffix);
        }
        
        // Sort the itemset by item ID for consistent, human-readable output format
        // Note: Items are processed in support order during mining, but output 
        // is sorted by ID for user convenience and consistency
        Arrays.sort(prefixSuffix);

        // Increment count
        itemsetCount++;

        // Save to memory or file
        if (writer == null) {
            Itemset itemsetWithTidset = new Itemset(prefixSuffix, tidset.bitset, tidset.support);
            closedItemsets.addItemset(itemsetWithTidset, prefixSuffix.length);
        } else {
            writeToFile(prefixSuffix, tidset);
        }
    }
    
    /**
     * Write an itemset to the output file.
     * 
     * @param itemset the itemset to write
     * @param tidset the tidset of the itemset
     * @throws IOException if error while writing to file
     */
    private void writeToFile(int[] itemset, BitSetSupport tidset) throws IOException {
        outputBuffer.setLength(0); // Reset instead of new allocation
        
        for (int i = 0; i < itemset.length; i++) {
            if (i > 0) outputBuffer.append(' ');
            outputBuffer.append(itemset[i]);
        }
        outputBuffer.append(" #SUP: ").append(tidset.support);
        
        if (showTransactionIdentifiers) {
            outputBuffer.append(" #TID:");
            BitSet bitset = tidset.bitset;
            for (int tid = bitset.nextSetBit(0); tid != -1; tid = bitset.nextSetBit(tid + 1)) {
                outputBuffer.append(' ');
                outputBuffer.append(tid);
            }
        }
        
        writer.write(outputBuffer.toString());
        writer.newLine();
    }
    
    /**
     * Set whether transaction identifiers should be shown for each pattern found.
     * 
     * @param showTransactionIdentifiers true to show transaction identifiers, false otherwise
     */
    public void setShowTransactionIdentifiers(boolean showTransactionIdentifiers) {
        this.showTransactionIdentifiers = showTransactionIdentifiers;
    }

    /**
     * Print statistics about the algorithm execution to System.out.
     */
    public void printStats() {
        System.out.println("=============  NEWCHARM Bitset - STATS =============");
        long temps = endTime - startTimestamp;
        System.out.println(" Transactions count from database : " + databaseSize);
        System.out.println(" Frequent closed itemsets count : " + itemsetCount);
        System.out.println(" Total time ~ " + temps + " ms");
        System.out.println(" Maximum memory usage : "
                + MemoryLogger.getInstance().getMaxMemory() + " mb");
        System.out.println("===================================================");
    }

    /**
     * Get the set of frequent closed itemsets.
     * 
     * @return the frequent closed itemsets (Itemsets)
     */
    public Itemsets getClosedItemsets() {
        return closedItemsets;
    }

    /**
     * Inner class to store a bitset and its cardinality (support).
     */
    public class BitSetSupport {
        /** The bitset representing transaction ids */
        public BitSet bitset = new BitSet();
        /** The support (cardinality of the bitset) */
        public int support;
    }
    
    /**
     * Inner class for bucketed checkSet that groups entries by support.
     * Uses TreeMap for efficient range queries (only check buckets with support >= query).
     * 
     * This allows for faster superset lookups by skipping buckets where
     * the support is less than the query support (since a subset cannot
     * have more elements than its superset).
     * 
     * According to NEWCHARM Theorem 3: if t(X) ⊆ t(Xi) for some Xi in checkSet,
     * then the closure of X has already been discovered via a different path
     * in the IT-tree, so X can be safely pruned.
     */
    private class BucketedCheckSet {
        /** 
         * Map from support value to list of tidsets with that support.
         * TreeMap allows efficient tailMap() for range queries.
         */
        private final NavigableMap<Integer, List<BitSetSupport>> supportBuckets = new TreeMap<>();
        
        /** 
         * Stack-like structure tracking additions for O(1) restoration.
         * Each entry stores the support value of the added tidset.
         */
        private final List<Integer> additionOrder = new ArrayList<>();
        
        /** Track the count of items in each bucket for O(1) removal */
        private final Map<Integer, Integer> bucketAddCounts = new HashMap<>();
        
        /**
         * Add a tidset to the bucketed checkSet.
         * 
         * @param tidset the tidset to add
         */
        public void add(BitSetSupport tidset) {
            int support = tidset.support;
            List<BitSetSupport> bucket = supportBuckets.get(support);
            if (bucket == null) {
                bucket = new ArrayList<>();
                supportBuckets.put(support, bucket);
            }
            bucket.add(tidset);
            additionOrder.add(support);
            
            // Track how many we've added to this bucket
            bucketAddCounts.merge(support, 1, Integer::sum);
        }
        
        /**
         * Get the current size of the checkSet.
         * 
         * @return the number of entries in the checkSet
         */
        public int size() {
            return additionOrder.size();
        }
        
        /**
         * Restore the checkSet to a previous size by removing entries added after that point.
         * Uses O(1) removal per entry by tracking addition order and bucket positions.
         * 
         * @param targetSize the size to restore to
         */
        public void restoreToSize(int targetSize) {
            while (additionOrder.size() > targetSize) {
                int lastIndex = additionOrder.size() - 1;
                int support = additionOrder.remove(lastIndex);
                
                // Get the bucket and remove the last element (O(1) for ArrayList)
                List<BitSetSupport> bucket = supportBuckets.get(support);
                bucket.remove(bucket.size() - 1);
                
                // Update count
                int newCount = bucketAddCounts.get(support) - 1;
                if (newCount == 0) {
                    bucketAddCounts.remove(support);
                    supportBuckets.remove(support);
                } else {
                    bucketAddCounts.put(support, newCount);
                }
            }
        }
        
        /**
         * Check if there exists a tidset in the checkSet that is a superset of tidsetX.
         * 
         * According to NEWCHARM Theorem 3: if t(X) ⊆ t(Xi) for some Xi in checkSet,
         * then the closure of X has already been discovered via a different path
         * in the IT-tree, so X can be safely pruned.
         * 
         * We check buckets with support >= querySupport because:
         * - If t(X) ⊆ t(Xi), then |t(X)| <= |t(Xi)|
         * - So support(X) <= support(Xi)
         * - Buckets with lower support cannot contain supersets of t(X)
         * 
         * @param tidsetX the tidset of itemset X
         * @param tempBitset a reusable BitSet for computation (avoids allocation)
         * @return true if a superset tidset exists (meaning X can be pruned), false otherwise
         */
        public boolean existSuperset(BitSetSupport tidsetX, BitSet tempBitset) {
            int querySupport = tidsetX.support;
            
            // Use tailMap to efficiently get only buckets with support >= querySupport
            // This is O(log n) for TreeMap instead of O(n) for HashMap
            for (List<BitSetSupport> bucket : supportBuckets.tailMap(querySupport, true).values()) {
                for (int i = 0; i < bucket.size(); i++) {
                    BitSetSupport tidsetXi = bucket.get(i);
                    
                    // Check if t(X) ⊆ t(Xi) by verifying (t(X) AND NOT t(Xi)) is empty
                    tempBitset.clear();
                    tempBitset.or(tidsetX.bitset);
                    tempBitset.andNot(tidsetXi.bitset);
                    
                    if (tempBitset.isEmpty()) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}