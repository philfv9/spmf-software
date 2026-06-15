package ca.pfv.spmf.algorithms.frequentpatterns.mehuim_closed;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ca.pfv.spmf.tools.MemoryLogger;

/* This file is copyright (c) Yang Hongyang, Philippe Fournier-Viger
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
 * This is an implementation of the MEHUIM_Closed algorithm for mining closed high utility itemsets.
 *
 * @author Hongyang Yang, Philippe Fournier-Viger
 */
public class AlgoMEHUIMClosed {

    /** table lists indexed by item for the P-set structure */
    List<Transaction>[] TableLists;

    /** list of reusable buffer objects for memory management */
    List<Buffer> buffers;

    /** the set of high-utility itemsets */
    private Itemsets highUtilityItemsets;

    /** object to write the output file */
    BufferedWriter writer = null;

    /** the number of high-utility itemsets found */
    private int patternCount;

    /** the start time of the last algorithm execution */
    long startTimestamp;

    /** the end time of the last algorithm execution */
    long endTimestamp;

    /** the minimum utility threshold */
    int minUtil;

    /** if true, debug information will be printed to the console */
    final boolean DEBUG = false;

    /** utility bin array for subtree utility */
    private int[] utilityBinArraySU;

    /** utility bin array for local utility */
    private int[] utilityBinArrayLU;

    /** utility bin array for support counting */
    private int[] utilityBinArraySupport;

    /** temporary buffer used to store the current itemset prefix */
    private int[] temp = new int[500];

    /** array that maps an old item name to its new name */
    int[] oldNameToNewNames;

    /** array that maps a new item name to its old name */
    int[] newNamesToOldNames;

    /** the number of new items after renaming */
    int newItemCount;

    /** if true, transaction merging will be performed by the algorithm */
    boolean activateTransactionMerging;

    /** maximum transaction size allowed for merging */
    final int MAXIMUM_SIZE_MERGING = 1000;

    /** number of times a transaction was read */
    long transactionReadingCount;

    /** number of transaction merges performed */
    long mergeCount;

    /** number of itemsets from the search tree that were considered */
    private long candidateCount;

    /** if true, subtree utility pruning will be performed */
    private boolean activateSubtreeUtilityPruning;

    /** if true, the closed pattern jump strategy will be performed */
    private boolean activateClosedPatternJumping;

    /**
     * Constructor
     */
    public AlgoMEHUIMClosed() {

    }

    /**
     * Runs the MEHUIM-Closed algorithm on the given input file.
     *
     * @param minUtil the minimum utility threshold
     * @param inputPath path to the input transaction database file
     * @param outputPath path to the output file, or null to store results in memory
     * @param activateTransactionMerging if true, transaction merging is activated
     * @param maximumTransactionCount maximum number of transactions to read
     * @param activateSubtreeUtilityPruning if true, subtree utility pruning is activated
     * @param activateClosedPatternJump if true, the closed pattern jump strategy is activated
     * @return the set of closed high utility itemsets found, or null if results are saved to file
     * @throws IOException if an error occurs while reading or writing files
     */
    public Itemsets runAlgorithm(int minUtil, String inputPath,
                                 String outputPath, boolean activateTransactionMerging,
                                 int maximumTransactionCount, boolean activateSubtreeUtilityPruning, boolean activateClosedPatternJump)
            throws IOException {

        // reset variables for statistics
        mergeCount = 0;
        transactionReadingCount = 0;

        // save parameters about activating or not the optimizations
        this.activateTransactionMerging = activateTransactionMerging;
        this.activateSubtreeUtilityPruning = activateSubtreeUtilityPruning;
        this.activateClosedPatternJumping = activateClosedPatternJump;

        // record the start time
        startTimestamp = System.currentTimeMillis();

        // read the input file
        Dataset dataset = new Dataset(inputPath, maximumTransactionCount);

        // save the minUtil value selected by the user
        this.minUtil = minUtil;

        // if the user choose to save to file
        // create object for writing the output file
        if (outputPath != null) {
            writer = new BufferedWriter(new FileWriter(outputPath));
        } else {
            // if the user choose to save to memory
            writer = null;
            this.highUtilityItemsets = new Itemsets("Itemsets");
        }

        // reset the number of itemset found
        patternCount = 0;

        // reset the memory usage checking utility
        MemoryLogger.getInstance().reset();

        // if in debug mode, show the initial database in the console
        if (DEBUG) {
            System.out.println("===== Initial database === ");
            System.out.println(dataset.toString());
        }

        // Scan the database using utility-bin array to calculate the TWU
        // of each item
        useUtilityBinArrayToCalculateLocalUtilityFirstTime(dataset);

        // if in debug mode, show the TWU calculated using the utility-bin array
        if (DEBUG) {
            System.out.println("===== TWU OF SINGLE ITEMS === ");
            for (int i = 1; i < utilityBinArrayLU.length; i++) {
                System.out.println("item : " + i + " twu: "
                        + utilityBinArrayLU[i]);
            }
            System.out.println();
        }

        // Now, we keep only the promising items (those having a twu >= minutil)
        List<Integer> itemsToKeep = new ArrayList<Integer>();
        for (int j = 1; j < utilityBinArrayLU.length; j++) {
            if (utilityBinArrayLU[j] >= minUtil) {
                itemsToKeep.add(j);
            }
        }

        // Sort promising items according to the increasing order of TWU
        insertionSort(itemsToKeep, utilityBinArrayLU);

        // Rename promising items according to the increasing order of TWU.
        // This will allow very fast comparison between items later by the
        // algorithm
        // This structure will store the new name corresponding to each old name
        oldNameToNewNames = new int[dataset.getMaxItem() + 1];
        // This structure will store the old name corresponding to each new name
        newNamesToOldNames = new int[dataset.getMaxItem() + 1];
        // We will now give the new names starting from the name "1"
        int currentName = 1;
        // For each item in increasing order of TWU
        for (int j = 0; j < itemsToKeep.size(); j++) {
            // get the item old name
            int item = itemsToKeep.get(j);
            // give it the new name
            oldNameToNewNames[item] = currentName;
            // remember its old name
            newNamesToOldNames[currentName] = item;
            // replace its old name by the new name in the list of promising
            // items
            itemsToKeep.set(j, currentName);
            // increment by one the current name so that
            currentName++;
        }

        // remember the number of promising item
        newItemCount = itemsToKeep.size();
        // initialize the utility-bin array for counting the subtree utility
        utilityBinArraySU = new int[newItemCount + 1];

        if (activateClosedPatternJump) {
            utilityBinArraySupport = new int[newItemCount + 1];
        }

        // if in debug mode, print to the old names and new names to the console
        // to check if they are correct
        if (DEBUG) {
            System.out.println(itemsToKeep);
            System.out.println(Arrays.toString(oldNameToNewNames));
            System.out.println(Arrays.toString(newNamesToOldNames));
        }

        // We now loop over each transaction from the dataset
        // to remove unpromising items
        for (int i = 0; i < dataset.getTransactions().size(); i++) {
            // Get the transaction
            Transaction transaction = dataset.getTransactions().get(i);

            // Remove unpromising items from the transaction and at the same
            // time
            // rename the items in the transaction according to their new names
            // and sort the transaction by increasing TWU order
            transaction.removeUnpromisingItems(oldNameToNewNames);
        }

        // Now we will sort transactions in the database according to the
        // proposed
        // total order on transaction (the lexicographical order when
        // transactions
        // are read backward).
        // We only sort if transaction merging is activated
        if (activateTransactionMerging) {
            // Sort the dataset using a new comparator
            Collections.sort(dataset.getTransactions(),
                    new Comparator<Transaction>() {
                        @Override
                        /**
                         * Compare two transactions
                         */
                        public int compare(Transaction t1, Transaction t2) {
                            // we will compare the two transaction items by
                            // items starting
                            // from the last items.
                            int pos1 = t1.items.length - 1;
                            int pos2 = t2.items.length - 1;

                            // if the first transaction is smaller than the
                            // second one
                            if (t1.items.length < t2.items.length) {
                                // while the current position in the first
                                // transaction is >0
                                while (pos1 >= 0) {
                                    int subtraction = t2.items[pos2]
                                            - t1.items[pos1];
                                    if (subtraction != 0) {
                                        return subtraction;
                                    }
                                    pos1--;
                                    pos2--;
                                }
                                // if they ware the same, they we compare based
                                // on length
                                return -1;

                                // else if the second transaction is smaller
                                // than the first one
                            } else if (t1.items.length > t2.items.length) {
                                // while the current position in the second
                                // transaction is >0
                                while (pos2 >= 0) {
                                    int subtraction = t2.items[pos2]
                                            - t1.items[pos1];
                                    if (subtraction != 0) {
                                        return subtraction;
                                    }
                                    pos1--;
                                    pos2--;
                                }
                                // if they ware the same, they we compare based
                                // on length
                                return 1;

                            } else {
                                // else if both transactions have the same size
                                while (pos2 >= 0) {
                                    int subtraction = t2.items[pos2]
                                            - t1.items[pos1];
                                    if (subtraction != 0) {
                                        return subtraction;
                                    }
                                    pos1--;
                                    pos2--;
                                }
                                // if they ware the same, they we compare based
                                // on length
                                return 0;
                            }
                        }

                    });

            // After removing unpromising items, it may be possible that some
            // transactions are empty. We will now remove these transactions
            // from the database.
            int emptyTransactionCount = 0;
            // for each transaction
            for (int i = 0; i < dataset.getTransactions().size(); i++) {
                // if the transaction length is 0, increase the number of empty
                // transactions
                Transaction transaction = dataset.getTransactions().get(i);
                if (transaction.items.length == 0) {
                    emptyTransactionCount++;
                }
            }
            // To remove empty transactions, we just ignore the first
            // transactions from the dataset
            // The reason is that empty transactions are always at the beginning
            // of the dataset since transactions are sorted by size
            dataset.transactions = dataset.transactions.subList(
                    emptyTransactionCount, dataset.transactions.size());
        }

        // if in debug mode, print the database after sorting and removing
        // promising items
        if (DEBUG) {
            System.out.println("===== Database without unpromising items and sorted by TWU increasing order === ");
            System.out.println(dataset.toString());
        }

        // Use an utility-bin array to calculate the sub-tree utility of each
        // item
        useUtilityBinArrayToCalculateSubtreeUtilityFirstTime(dataset);

        // Calculate the set of items that pass the sub-tree utility pruning
        // condition
        List<Integer> itemsToExplore = new ArrayList<Integer>();
        // if subtree utility pruning is activated
        if (activateSubtreeUtilityPruning) {
            // for each item
            for (Integer item : itemsToKeep) {
                // if the subtree utility is higher or equal to minutil, then
                // keep it
                if (utilityBinArraySU[item] >= minUtil) {
                    itemsToExplore.add(item);
                }
            }
        }

        // If in debug mode, show the list of promising items
        if (DEBUG) {
            System.out.println("===== List of promising items === ");
            System.out.println(itemsToKeep);
        }

        // P-set
        TableLists = new ArrayList[itemsToKeep.size() + 1];
        List<Transaction>[] tableList = new List[itemsToKeep.size()];

        for (int it : itemsToKeep) {
            TableLists[it] = new ArrayList<Transaction>();
        }
        for (Transaction t : dataset.getTransactions()) {
            for (int i = 0; i < t.items.length; i++) {
                TableLists[t.items[i]].add(t);
            }
        }
        int tableLen = 0;
        for (int it : itemsToExplore) {
            tableList[tableLen] = TableLists[it];
            tableLen++;
            TableLists[it] = null;
        }

        // Initialize memory management buffers
        buffers = new ArrayList<>();
        Buffer.length = dataset.getTransactions().size();
        buffers.add(new Buffer(-1, false));
        // Assign values to the first layer of the buffer
        Buffer buff = buffers.get(0);
        buff.end = 0;
        for (Transaction t : dataset.getTransactions()) {
            buff.transactions[buff.end] = t;
            buff.end++;
        }

        // Recursive call to the algorithm
        // If subtree utility pruning is activated
        if (activateSubtreeUtilityPruning) {
            // We call the recursive algorithm with the database, secondary
            // items and primary items
            backtrackingMEHUIMClosed(dataset.getTransactions(), itemsToKeep, itemsToExplore, 0, tableList);
        } else {
            // We call the recursive algorithm with the database and secondary
            // items
            backtrackingMEHUIMClosed(dataset.getTransactions(), itemsToKeep, itemsToKeep, 0, tableList);
        }

        // record the end time
        endTimestamp = System.currentTimeMillis();

        // close the output file
        if (writer != null) {
            writer.close();
        }

        // check the maximum memory usage
        MemoryLogger.getInstance().checkMemory();

        // return the set of high-utility itemsets
        return highUtilityItemsets;
    }

    /**
     * Sorts a list of items in increasing order of their TWU values using insertion sort.
     *
     * @param items list of integers to be sorted
     * @param utilityBinArrayTWU the utility-bin array indicating the TWU of each item
     */
    public static void insertionSort(List<Integer> items,
                                     int[] utilityBinArrayTWU) {
        // the following lines are simply a modified an insertion sort

        for (int j = 1; j < items.size(); j++) {
            Integer itemJ = items.get(j);
            int i = j - 1;
            Integer itemI = items.get(i);

            // we compare the twu of items i and j
            int comparison = utilityBinArrayTWU[itemI]
                    - utilityBinArrayTWU[itemJ];
            // if the twu is equal, we use the lexicographical order to decide
            // whether i is greater
            // than j or not.
            if (comparison == 0) {
                comparison = itemI - itemJ;
            }

            while (comparison > 0) {
                items.set(i + 1, itemI);

                i--;
                if (i < 0) {
                    break;
                }

                itemI = items.get(i);
                comparison = utilityBinArrayTWU[itemI]
                        - utilityBinArrayTWU[itemJ];
                // if the twu is equal, we use the lexicographical order to
                // decide whether i is greater
                // than j or not.
                if (comparison == 0) {
                    comparison = itemI - itemJ;
                }
            }
            items.set(i + 1, itemJ);
        }
    }

    /**
     * Recursively explores the search space to find closed high utility itemsets.
     *
     * @param transactionsOfP the list of transactions in the projected database of the current prefix P
     * @param itemsToKeep the list of secondary items that can extend the prefix
     * @param itemsToExplore the list of primary items that will be used to extend the prefix
     * @param prefixLength the current length of the prefix
     * @param tableLists array of transaction lists indexed by item position
     * @return the maximum support found among extensions of the current prefix
     * @throws IOException if an error occurs while writing results
     */
    private int backtrackingMEHUIMClosed(List<Transaction> transactionsOfP,
                                          List<Integer> itemsToKeep, List<Integer> itemsToExplore,
                                          int prefixLength, List<Transaction>[] tableLists) throws IOException {

        int maxSupport = 0;

        // Get the transactions for the child node
        if (buffers.size() <= prefixLength + 1) {
            buffers.add(new Buffer(-1, false));
        }
        // Get the transactions for the current node
        Buffer buff = buffers.get(prefixLength);
        Buffer subBuff = buffers.get(prefixLength + 1);

        // ======== for each frequent item e =============
        for (int j = 0; j < itemsToExplore.size(); j++) {
            Integer e = itemsToExplore.get(j);

            // ========== BEGIN PERFORMING INTERSECTION =====================
            // Calculate transactions containing P U {e}
            // At the same time project transactions to keep what appears after
            // "e"
            List<Transaction> transactionsPe = new ArrayList<Transaction>();

            // variable to calculate the utility of P U {e}
            int utilityPe = 0;
            int supportPe = 0;

            List<Transaction> nowEmptyTransactionsPe = new ArrayList<Transaction>(); // EFIM-closed

            // For merging transactions, we will keep track of the last
            // transaction read
            // and the number of identical consecutive transactions
            Transaction previousTransaction = null;
            int consecutiveMergeCount = 0;

            subBuff.end = 0;
            // For each transaction
            List<Transaction> transactions = tableLists[j];
            for (Transaction transaction : transactions) {
                // Increase the number of transaction read
                transactionReadingCount++;

                // we remember the position where e appears.
                // we will call this position an "offset"
                int positionE = -1;
                // Variables low and high for binary search
                int low = transaction.offset;
                int high = transaction.items.length - 1;

                // perform binary search to find e in the transaction
                while (high >= low) {
                    int middle = (low + high) >>> 1; // divide by 2
                    if (transaction.items[middle] < e) {
                        low = middle + 1;
                    } else if (transaction.items[middle] == e) {
                        positionE = middle;
                        break;
                    } else {
                        high = middle - 1;
                    }
                }
                // record the time spent for performing the binary search

                // if 'e' was found in the transaction
                if (positionE > -1) {
                    supportPe += transaction.originalTransactions.length;

                    // optimization: if the 'e' is the last one in this
                    // transaction,
                    // we don't keep the transaction
                    if (transaction.getLastPosition() == positionE) {
                        // but we still update the sum of the utility of P U {e}
                        utilityPe += transaction.utilities[positionE]
                                + transaction.prefixUtility;
                        nowEmptyTransactionsPe.add(transaction);
                    } else {
                        // otherwise
                        if (activateTransactionMerging
                                && MAXIMUM_SIZE_MERGING >= (transaction.items.length - positionE)) {
                            // we cut the transaction starting from position 'e'
                            Transaction projectedTransaction = subBuff.GetNextTransaction().
                                    SetTransaction(transaction, positionE);
                            utilityPe += projectedTransaction.prefixUtility;

                            // if it is the first transaction that we read
                            if (previousTransaction == null) {
                                // we keep the transaction in memory
                                previousTransaction = projectedTransaction;
                            } else if (isEqualTo(projectedTransaction,
                                    previousTransaction)) {
                                mergeCount++;

                                // if the first consecutive merge
                                if (consecutiveMergeCount == 0) {
                                    // copy items and their profit from the
                                    // previous transaction
                                    int itemsCount = previousTransaction.items.length
                                            - previousTransaction.offset;
                                    int[] items = new int[itemsCount];
                                    System.arraycopy(previousTransaction.items,
                                            previousTransaction.offset, items,
                                            0, itemsCount);
                                    int[] utilities = new int[itemsCount];
                                    System.arraycopy(
                                            previousTransaction.utilities,
                                            previousTransaction.offset,
                                            utilities, 0, itemsCount);

                                    // make the sum of utilities from the
                                    // previous transaction
                                    int positionPrevious = 0;
                                    int positionProjection = projectedTransaction.offset;
                                    while (positionPrevious < itemsCount) {
                                        utilities[positionPrevious] += projectedTransaction.utilities[positionProjection];
                                        positionPrevious++;
                                        positionProjection++;
                                    }

                                    // make the sum of prefix utilities
                                    int sumUtilities = previousTransaction.prefixUtility += projectedTransaction.prefixUtility;

                                    int[][] mergeOriginalTransactions = mergeOriginalTransactions(
                                            previousTransaction,
                                            projectedTransaction);

                                    // create the new transaction replacing the
                                    // two merged transactions
                                    previousTransaction = subBuff.GetNextTransaction().
                                            SetTransaction(items, utilities,
                                                    previousTransaction.transactionUtility
                                                            + projectedTransaction.transactionUtility,
                                                    mergeOriginalTransactions);
                                    previousTransaction.prefixUtility = sumUtilities;

                                } else {
                                    // if not the first consecutive merge

                                    // add the utilities in the projected
                                    // transaction to the previously
                                    // merged transaction
                                    int positionPrevious = 0;
                                    int positionProjected = projectedTransaction.offset;
                                    int itemsCount = previousTransaction.items.length;
                                    while (positionPrevious < itemsCount) {
                                        previousTransaction.utilities[positionPrevious] += projectedTransaction.utilities[positionProjected];
                                        positionPrevious++;
                                        positionProjected++;
                                    }

                                    int[][] mergeOriginalTransactions = mergeOriginalTransactions(
                                            previousTransaction,
                                            projectedTransaction);

                                    // make also the sum of transaction utility
                                    // and prefix utility
                                    previousTransaction.transactionUtility += projectedTransaction.transactionUtility;
                                    previousTransaction.prefixUtility += projectedTransaction.prefixUtility;
                                    previousTransaction.originalTransactions = mergeOriginalTransactions;
                                }
                                // increment the number of consecutive
                                // transaction merged
                                consecutiveMergeCount++;
                            } else {
                                // if the transaction is not equal to the
                                // preceding transaction
                                // we cannot merge it so we just add it to the
                                // database
                                transactionsPe.add(previousTransaction);
                                // the transaction becomes the previous
                                // transaction
                                previousTransaction = projectedTransaction;
                                // and we reset the number of consecutive
                                // transactions merged
                                consecutiveMergeCount = 0;
                            }
                        } else {
                            // Otherwise, if merging has been deactivated
                            // then we just create the projected transaction
                            Transaction projectedTransaction = subBuff.GetNextTransaction().
                                    SetTransaction(transaction, positionE);

                            // we add the utility of Pe in that transaction to
                            // the total utility of Pe
                            utilityPe += projectedTransaction.prefixUtility;
                            // we put the projected transaction in the projected
                            // database of Pe
                            transactionsPe.add(projectedTransaction);
                        }
                    }
                    // This is an optimization for binary search:
                    // we remember the position of E so that for the next item,
                    // we will not search
                    // before "e" in the transaction since items are visited in
                    // lexicographical order
                    transaction.offset = positionE;
                } else {
                    // This is an optimization for binary search:
                    // we remember the position of E so that for the next item,
                    // we will not search
                    // before "e" in the transaction since items are visited in
                    // lexicographical order
                    transaction.offset = low;
                }
            }

            // Add the last read transaction to the database if there is one
            if (previousTransaction != null) {
                transactionsPe.add(previousTransaction);
            }

            // ==================== END OF INTERSECTION ===========================
            // ====== Check if PU{e...} has a backward extension ======
            if (hasNoBackwardExtension(temp, prefixLength, transactionsPe, nowEmptyTransactionsPe, e)) {

                // Append item "e" to P to obtain P U {e}
                temp[prefixLength] = e;

                if (supportPe > maxSupport) {
                    maxSupport = supportPe;
                }

                for (int it : itemsToKeep) {
                    TableLists[it] = new ArrayList<Transaction>();
                }
                List<Transaction>[] tableList = new List[itemsToKeep.size()];

                // ==== Next, we will calculate the Local Utility and Sub-tree
                // utility of all items that could be appended to PU{e} ====
                int utilityOfRemainingItemsJumpingClosure = useUtilityBinArraysToCalculateUpperBounds_P(transactionsPe, j, itemsToKeep);

                boolean shouldJumpToClosure = false;
                int utilityOfJumpingClosure = utilityPe + utilityOfRemainingItemsJumpingClosure;
                if (activateClosedPatternJumping) {
                    if (utilityOfJumpingClosure >= minUtil) {
                        shouldJumpToClosure = true;
                        for (int i = j + 1; i < itemsToKeep.size(); i++) {
                            int item = itemsToKeep.get(i);
                            if (utilityBinArraySupport[item] != supportPe) {
                                shouldJumpToClosure = false;
                                break;
                            }
                        }
                    }
                }

                if (shouldJumpToClosure) {
                    // PERFORM JUMPING CLOSURE
                    int newLength = prefixLength + 1;
                    for (int i = j + 1; i < itemsToKeep.size(); i++) {
                        temp[newLength++] = itemsToKeep.get(i);
                    }
                    output(newLength - 1, utilityOfJumpingClosure);
                    // END PERFORM JUMPING CLOSURE

                    // update the number of candidates explored so far
                    candidateCount += 1;

                } else {
                    // We will create the new list of secondary items
                    List<Integer> newItemsToKeep = new ArrayList<Integer>();
                    // We will create the new list of primary items
                    List<Integer> newItemsToExplore = new ArrayList<Integer>();

                    int tableLen = 0;
                    // for each item
                    for (int k = j + 1; k < itemsToKeep.size(); k++) {
                        Integer itemk = itemsToKeep.get(k);

                        // if the sub-tree utility is no less than min util
                        if (utilityBinArraySU[itemk] >= minUtil) {
                            // and if sub-tree utility pruning is activated
                            if (activateSubtreeUtilityPruning) {
                                // consider that item as a primary item
                                newItemsToExplore.add(itemk);
                                tableList[tableLen] = TableLists[itemk];
                                tableLen++;
                                TableLists[itemk] = null;
                            }
                            // consider that item as a secondary item
                            newItemsToKeep.add(itemk);
                        } else if (utilityBinArrayLU[itemk] >= minUtil) {
                            // otherwise, if local utility is no less than minutil,
                            // consider this item to be a secondary item
                            newItemsToKeep.add(itemk);
                        }
                    }

                    int recursiveSupport = 0;
                    // === recursive call to explore larger itemsets
                    if (activateSubtreeUtilityPruning) {
                        // if sub-tree utility pruning is activated, we consider
                        // primary and secondary items
                        recursiveSupport = backtrackingMEHUIMClosed(transactionsPe, newItemsToKeep, newItemsToExplore, prefixLength + 1, tableList);
                    } else {
                        // if sub-tree utility pruning is deactivated, we consider
                        // secondary items also as primary items
                        recursiveSupport = backtrackingMEHUIMClosed(transactionsPe, newItemsToKeep, newItemsToKeep, prefixLength + 1, tableList);
                    }

                    boolean hasNoForwardExtension = supportPe > recursiveSupport;

                    // if the utility of PU{e} is enough to be a high utility
                    // itemset
                    if (hasNoForwardExtension && utilityPe >= minUtil) {
                        // update the number of candidates explored so far
                        candidateCount += 1;
                        // output PU{e}
                        output(prefixLength, utilityPe);
                    } // end if has forward extension
                } // end jumping closure if
            } // end if has backward extension

        }

        // check the maximum memory usage for statistics purpose
        MemoryLogger.getInstance().checkMemory();

        return maxSupport;
    }

    /**
     * Merges the original transactions of two transactions into a single array.
     *
     * @param transaction1 the first transaction
     * @param transaction2 the second transaction
     * @return the union of the original transactions of both transactions
     */
    private int[][] mergeOriginalTransactions(Transaction transaction1, Transaction transaction2) {
        int OriginalTransactionsCount = transaction1.originalTransactions.length
                + transaction2.originalTransactions.length;

        int[][] mergeOriginalTransactions = new int[OriginalTransactionsCount][];
        System.arraycopy(transaction1.originalTransactions, 0,
                mergeOriginalTransactions, 0,
                transaction1.originalTransactions.length);
        System.arraycopy(transaction2.originalTransactions, 0,
                mergeOriginalTransactions,
                transaction1.originalTransactions.length,
                transaction2.originalTransactions.length);
        return mergeOriginalTransactions;
    }

    /**
     * Checks if the extension of a prefix with an item e has no backward extension.
     *
     * @param prefix the current prefix itemset
     * @param prefixLength the length of the current prefix
     * @param transactionsPe the projected transactions containing the prefix P U e that are not empty
     * @param nowEmptyTransactions the projected transactions containing the prefix P U e that are empty
     * @param e the item being appended to the prefix
     * @return true if P U e has no backward extension, false otherwise
     */
    private boolean hasNoBackwardExtension(int[] prefix, int prefixLength,
                                           List<Transaction> transactionsPe,
                                           List<Transaction> nowEmptyTransactions, Integer e) {
        // We do a loop on each item i of the first transaction
        int[] firstTrans;
        if (transactionsPe.size() == 0) {
            firstTrans = nowEmptyTransactions.get(0).originalTransactions[0];
        } else {
            firstTrans = transactionsPe.get(0).originalTransactions[0];
        }

        // for each item i < e
        for (int item : firstTrans) {
            if (item == e) {
                break;
            }
            // if p does not contain i and i is present in all transactions,
            // then PUe has a backward extension
            if (!containsByBinarySearch(prefix, prefixLength, item)
                    && isItemInAllTransactions(transactionsPe, item)
                    && isItemInAllTransactions(nowEmptyTransactions, item)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if an itemset contains a given item using binary search.
     *
     * @param items the itemset represented as a sorted array
     * @param itemsLength the number of valid items in the array
     * @param item the item to search for
     * @return true if the item appears in the itemset, false otherwise
     */
    public boolean containsByBinarySearch(int[] items, int itemsLength, int item) {
        if (itemsLength == 0 || item > items[itemsLength - 1]) {
            return false;
        }

        // We just do a binary search
        int low = 0;
        int high = itemsLength - 1;

        while (high >= low) {
            int middle = (low + high) >>> 1; // divide by 2
            if (items[middle] == item) {
                return true;
            }
            if (items[middle] < item) {
                low = middle + 1;
            }
            if (items[middle] > item) {
                high = middle - 1;
            }
        }
        return false;
    }

    /**
     * Checks if a given item appears in all original transactions of a list of merged transactions.
     *
     * @param transactionsPe the list of transactions to check
     * @param item the item to search for
     * @return true if the item appears in all transactions, false otherwise
     */
    private boolean isItemInAllTransactions(List<Transaction> transactionsPe, int item) {

        for (Transaction mergedTransaction : transactionsPe) {
            for (int[] trans : mergedTransaction.originalTransactions) {
                if (containsByBinarySearch(trans, trans.length, item) == false) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Checks if two transactions are identical by comparing items position by position.
     *
     * @param t1 the first transaction
     * @param t2 the second transaction
     * @return true if both transactions contain the same items in the same order
     */
    private boolean isEqualTo(Transaction t1, Transaction t2) {
        // we first compare the transaction lengths
        int length1 = t1.items.length - t1.offset;
        int length2 = t2.items.length - t2.offset;
        // if not same length, then transactions are not identical
        if (length1 != length2) {
            return false;
        }
        // if same length, we need to compare each element position by position,
        // to see if they are the same
        int position1 = t1.offset;
        int position2 = t2.offset;

        // for each position in the first transaction
        while (position1 < t1.items.length) {
            // if different from corresponding position in transaction 2
            // return false because they are not identical
            if (t1.items[position1] != t2.items[position2]) {
                return false;
            }
            // if the same, then move to next position
            position1++;
            position2++;
        }
        // if all items are identical, then return to true
        return true;
    }

    /**
     * Scans the initial database to calculate the local utility of each item using a utility-bin array.
     *
     * @param dataset the transaction database
     */
    public void useUtilityBinArrayToCalculateLocalUtilityFirstTime(Dataset dataset) {

        // Initialize utility bins for all items
        utilityBinArrayLU = new int[dataset.getMaxItem() + 1];

        // Scan the database to fill the utility bins
        // For each transaction
        for (Transaction transaction : dataset.getTransactions()) {
            // for each item
            for (Integer item : transaction.getItems()) {
                // we add the transaction utility to the utility bin of the item
                utilityBinArrayLU[item] += transaction.transactionUtility;
            }
        }
    }

    /**
     * Scans the initial database to calculate the subtree utility of each item using a utility-bin array.
     *
     * @param dataset the transaction database
     */
    public void useUtilityBinArrayToCalculateSubtreeUtilityFirstTime(Dataset dataset) {

        int sumSU;
        // Scan the database to fill the utility-bins of each item
        // For each transaction
        for (Transaction transaction : dataset.getTransactions()) {
            // We will scan the transaction backward. Thus,
            // the current sub-tree utility in that transaction is zero
            // for the last item of the transaction.
            sumSU = 0;

            // For each item when reading the transaction backward
            for (int i = transaction.getItems().length - 1; i >= 0; i--) {
                // get the item
                Integer item = transaction.getItems()[i];

                // we add the utility of the current item to its sub-tree
                // utility
                sumSU += transaction.getUtilities()[i];
                // we add the current sub-tree utility to the utility-bin of the
                // item
                utilityBinArraySU[item] += sumSU;
            }
        }
    }

    /**
     * Calculates upper bounds on utility for all items that can extend the current prefix,
     * and updates the table lists used for the P-set structure.
     *
     * @param transactionsPe the projected transactions of the current prefix P U e
     * @param j the index of item e in the itemsToKeep list
     * @param itemsToKeep the list of secondary items
     * @return the total utility of remaining items used for the jumping closure optimization
     */
    private int useUtilityBinArraysToCalculateUpperBounds_P(List<Transaction> transactionsPe,
                                                             int j, List<Integer> itemsToKeep) {

        int utilityOfRemainingItemsJumpingClosure = 0;

        // we will record the time used by this method for statistics purpose
        long initialTime = System.currentTimeMillis();

        // For each promising item > e according to the total order
        for (int i = j + 1; i < itemsToKeep.size(); i++) {
            int item = itemsToKeep.get(i);
            // We reset the utility bins of that item for computing the sub-tree
            // utility and local utility
            utilityBinArraySU[item] = 0;
            utilityBinArrayLU[item] = 0;
            if (activateClosedPatternJumping) {
                utilityBinArraySupport[item] = 0;
            }
        }

        int sumRemainingUtility;
        // for each transaction
        for (Transaction transaction : transactionsPe) {
            // count the number of transactions read
            transactionReadingCount++;

            // We reset the sum of remaining utility to 0
            sumRemainingUtility = 0;
            // we set high to the last promising item for doing the binary
            // search
            int high = itemsToKeep.size() - 1;

            // for each item in the transaction that is greater than i when
            // reading the transaction backward
            // Note: >= is correct here. It should not be >.
            for (int i = transaction.getItems().length - 1; i >= transaction.offset; i--) {
                // get the item
                int item = transaction.getItems()[i];

                // We will check if this item is promising using a binary search
                // over promising items.

                // This variable will be used as a flag to indicate that we
                // found the item or not using the binary search
                boolean contains = false;
                // we set "low" for the binary search to the first promising
                // item position
                int low = 0;

                // do the binary search
                while (high >= low) {
                    int middle = (low + high) >>> 1; // divide by 2
                    int itemMiddle = itemsToKeep.get(middle);
                    if (itemMiddle == item) {
                        // if we found the item, then we stop
                        contains = true;
                        break;
                    } else if (itemMiddle < item) {
                        low = middle + 1;
                    } else {
                        high = middle - 1;
                    }
                }
                // if the item is promising
                if (contains) {
                    // We add the utility of this item to the sum of remaining
                    // utility
                    sumRemainingUtility += transaction.getUtilities()[i];
                    // We update the sub-tree utility of that item in its
                    // utility-bin
                    utilityBinArraySU[item] += sumRemainingUtility
                            + transaction.prefixUtility;
                    // We update the local utility of that item in its
                    // utility-bin
                    utilityBinArrayLU[item] += transaction.transactionUtility
                            + transaction.prefixUtility;
                    // update support
                    if (activateClosedPatternJumping) {
                        utilityBinArraySupport[item] += transaction.originalTransactions.length;
                        utilityOfRemainingItemsJumpingClosure += transaction.getUtilities()[i];
                        TableLists[item].add(transaction);
                    }
                }
            }
        }

        return utilityOfRemainingItemsJumpingClosure;
    }

    /**
     * Calculates upper bounds on utility for all items that can extend the current prefix,
     * without updating table lists.
     *
     * @param transactionsPe the projected transactions of the current prefix P U e
     * @param j the index of item e in the itemsToKeep list
     * @param itemsToKeep the list of secondary items
     * @return the total utility of remaining items used for the jumping closure optimization
     */
    private int useUtilityBinArraysToCalculateUpperBounds(List<Transaction> transactionsPe,
                                                           int j, List<Integer> itemsToKeep) {

        int utilityOfRemainingItemsJumpingClosure = 0;

        // we will record the time used by this method for statistics purpose
        long initialTime = System.currentTimeMillis();

        // For each promising item > e according to the total order
        for (int i = j + 1; i < itemsToKeep.size(); i++) {
            int item = itemsToKeep.get(i);
            // We reset the utility bins of that item for computing the sub-tree
            // utility and local utility
            utilityBinArraySU[item] = 0;
            utilityBinArrayLU[item] = 0;
            if (activateClosedPatternJumping) {
                utilityBinArraySupport[item] = 0;
            }
        }

        int sumRemainingUtility;
        // for each transaction
        for (Transaction transaction : transactionsPe) {
            // count the number of transactions read
            transactionReadingCount++;

            // We reset the sum of remaining utility to 0
            sumRemainingUtility = 0;
            // we set high to the last promising item for doing the binary
            // search
            int high = itemsToKeep.size() - 1;

            // for each item in the transaction that is greater than i when
            // reading the transaction backward
            // Note: >= is correct here. It should not be >.
            for (int i = transaction.getItems().length - 1; i >= transaction.offset; i--) {
                // get the item
                int item = transaction.getItems()[i];

                // We will check if this item is promising using a binary search
                // over promising items.

                // This variable will be used as a flag to indicate that we
                // found the item or not using the binary search
                boolean contains = false;
                // we set "low" for the binary search to the first promising
                // item position
                int low = 0;

                // do the binary search
                while (high >= low) {
                    int middle = (low + high) >>> 1; // divide by 2
                    int itemMiddle = itemsToKeep.get(middle);
                    if (itemMiddle == item) {
                        // if we found the item, then we stop
                        contains = true;
                        break;
                    } else if (itemMiddle < item) {
                        low = middle + 1;
                    } else {
                        high = middle - 1;
                    }
                }
                // if the item is promising
                if (contains) {
                    // We add the utility of this item to the sum of remaining
                    // utility
                    sumRemainingUtility += transaction.getUtilities()[i];
                    // We update the sub-tree utility of that item in its
                    // utility-bin
                    utilityBinArraySU[item] += sumRemainingUtility
                            + transaction.prefixUtility;
                    // We update the local utility of that item in its
                    // utility-bin
                    utilityBinArrayLU[item] += transaction.transactionUtility
                            + transaction.prefixUtility;
                    // update support
                    if (activateClosedPatternJumping) {
                        utilityBinArraySupport[item] += transaction.originalTransactions.length;
                        utilityOfRemainingItemsJumpingClosure += transaction.getUtilities()[i];
                    }
                }
            }
        }

        return utilityOfRemainingItemsJumpingClosure;
    }

    /**
     * Outputs a closed high utility itemset to file or memory.
     *
     * @param tempPosition the index of the last item in the temp buffer representing the itemset
     * @param utility the utility value of the itemset
     * @throws IOException if an error occurs while writing to file
     */
    private void output(int tempPosition, int utility) throws IOException {
        patternCount++;

        // if user wants to save the results to memory
        if (writer == null) {
            // we copy the temporary buffer into a new int array
            int[] copy = new int[tempPosition + 1];
            for (int i = 0; i <= tempPosition; i++) {
                copy[i] = newNamesToOldNames[temp[i]];
            }
            // we create the itemset using this array and add it to the list of
            // itemsets found until now
            highUtilityItemsets.addItemset(new Itemset(copy, utility), copy.length);
        } else {
            // if user wants to save the results to file
            // create a stringbuffer
            StringBuffer buffer = new StringBuffer();
            // append each item from the itemset to the stringbuffer, separated
            // by spaces
            for (int i = 0; i <= tempPosition; i++) {
                buffer.append(newNamesToOldNames[temp[i]]);
                if (i != tempPosition) {
                    buffer.append(' ');
                }
            }
            // append the utility of the itemset
            buffer.append("#UTIL: ");
            buffer.append(utility);

            // write the stringbuffer to file and create a new line
            // so that we are ready for writing the next itemset.
            writer.write(buffer.toString());
            writer.newLine();
        }
    }

    /**
     * Prints statistics about the latest execution of the MEHUIM-Closed algorithm to the console.
     */
    public void printStats() {

        System.out.println("========== MEHUIM-Closed v.2.66 - STATS ============");
        System.out.println(" minUtil = " + minUtil);
        System.out.println(" Closed High utility itemsets count: " + patternCount);
        System.out.println(" Total time ~: " + (endTimestamp - startTimestamp)
                + " ms");
        // if in debug mode, we show more information
        if (DEBUG) {
            System.out.println(" Transaction merge count ~: " + mergeCount);
            System.out.println(" Transaction read count ~: " + transactionReadingCount);
        }
        System.out.println(" Max memory:" + MemoryLogger.getInstance().getMaxMemory());
        System.out.println(" Visited node count : " + candidateCount);
        System.out.println("=====================================");
    }
}