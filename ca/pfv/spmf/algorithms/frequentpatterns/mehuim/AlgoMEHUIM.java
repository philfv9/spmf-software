package ca.pfv.spmf.algorithms.frequentpatterns.mehuim;

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
 * This is an implementation of the MEHUIM algorithm for mining high utility itemsets.
 *
 * @author Hongyang Yang, Philippe Fournier-Viger
 */
public class AlgoMEHUIM {

    /** the minimum utility threshold */
    int minUtil;

    /** flag value used to track whether an item is in the keep set */
    int isItemInKeepFlag;

    /** array indicating whether each item is currently in the keep set */
    int[] isItemInKeep;

    /** current length of the search node buffer */
    int snLen;

    /** maximum length reached by the search node buffer */
    int snMaxLen;

    /** buffer of reusable search nodes */
    ArrayList<SearchNode> snBuff;

    /** table lists indexed by item for sub-search nodes */
    List<SubSearchNode>[] TableLists;

    /** current length of the prefix being explored */
    int prefixLength;

    /** timestamp when the algorithm started */
    long startTimestamp;

    /** timestamp when the algorithm ended */
    long endTimestamp;

    /** writer used to write results to file */
    BufferedWriter writer = null;

    /** stores high utility itemsets when saving to memory */
    private Itemsets highUtilityItemsets;

    /** flag to enable debug output */
    final boolean DEBUG = false;

    /** utility bin array for subtree utility */
    private int[] utilityBinArraySU;

    /** utility bin array for local utility */
    private int[] utilityBinArrayLU;

    /** array that maps an old item name to its new name */
    int[] oldNameToNewNames;

    /** array that maps a new item name to its old name */
    int[] newNamesToOldNames;

    /** the number of new items after renaming */
    int newItemCount;

    /** time spent sorting transactions */
    long timeSort;

    /** temporary buffer used to store the current itemset prefix */
    private int[] temp = new int[500];

    /** number of high utility itemsets found */
    private int patternCount;

    /**
     * Runs the MEHUIM algorithm on the given input file.
     *
     * @param minUtil the minimum utility threshold
     * @param inputPath path to the input transaction database file
     * @param outputPath path to the output file, or null to store results in memory
     * @param activateTransactionMerging if true, transaction merging is activated
     * @param maximumTransactionCount maximum number of transactions to read
     * @param activateSubtreeUtilityPruning if true, subtree utility pruning is activated
     * @return the set of high utility itemsets found
     * @throws IOException if an error occurs while reading or writing files
     */
    public Itemsets runAlgorithm(int minUtil, String inputPath,
                                 String outputPath, boolean activateTransactionMerging,
                                 int maximumTransactionCount, boolean activateSubtreeUtilityPruning) throws IOException {

        startTimestamp = System.currentTimeMillis();

        Dataset dataset = new Dataset(inputPath, maximumTransactionCount);

        this.minUtil = minUtil;

        if (outputPath != null) {
            writer = new BufferedWriter(new FileWriter(outputPath));
        } else {
            writer = null;
            this.highUtilityItemsets = new Itemsets("Itemsets");
        }

        MemoryLogger.getInstance().reset();

        if (DEBUG) {
            System.out.println("===== Initial database === ");
            System.out.println(dataset.toString());
        }

        useUtilityBinArrayToCalculateLocalUtilityFirstTime(dataset);

        if (DEBUG) {
            System.out.println("===== TWU OF SINGLE ITEMS === ");
            for (int i = 1; i < utilityBinArrayLU.length; i++) {
                System.out.println("item : " + i + " twu: " + utilityBinArrayLU[i]);
            }
            System.out.println();
        }

        // Now, we keep only the promising items (those having a twu >= minutil)
        List<Integer> itemsToKeep = new ArrayList<Integer>();
        List<Integer> itemsToRevoke = new ArrayList<Integer>();
        for (int j = 1; j < utilityBinArrayLU.length; j++) {
            if (utilityBinArrayLU[j] >= minUtil) {
                itemsToKeep.add(j);
            } else {
                itemsToRevoke.add(j);
            }
        }

        // Sort promising items according to the increasing order of TWU
        insertionSort(itemsToKeep, utilityBinArrayLU);

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
            // replace its old name by the new name in the list of promising items
            itemsToKeep.set(j, currentName);
            // increment by one the current name so that
            currentName++;
        }

        // remember the number of promising item
        newItemCount = itemsToKeep.size();
        // initialize the utility-bin array for counting the subtree utility
        utilityBinArraySU = new int[newItemCount + 1];

        for (int i = 0; i < dataset.getTransactions().size(); i++) {
            Transaction transaction = dataset.getTransactions().get(i);
            transaction.removeUnpromisingItems(oldNameToNewNames);
        }

        long timeStartSorting = System.currentTimeMillis();
        // We only sort if transaction merging is activated
        if (activateTransactionMerging) {
            Collections.sort(dataset.getTransactions(), new Comparator<Transaction>() {
                @Override
                /**
                 * Compare two transactions
                 */
                public int compare(Transaction t1, Transaction t2) {
                    // we will compare the two transaction items by items starting
                    // from the last items.
                    int pos1 = t1.items.length - 1;
                    int pos2 = t2.items.length - 1;

                    // if the first transaction is smaller than the second one
                    if (t1.items.length < t2.items.length) {
                        // while the current position in the first transaction is >0
                        while (pos1 >= 0) {
                            int subtraction = t2.items[pos2] - t1.items[pos1];
                            if (subtraction != 0) {
                                return subtraction;
                            }
                            pos1--;
                            pos2--;
                        }
                        // if they are the same, they we compare based on length
                        return -1;

                        // else if the second transaction is smaller than the first one
                    } else if (t1.items.length > t2.items.length) {
                        // while the current position in the second transaction is >0
                        while (pos2 >= 0) {
                            int subtraction = t2.items[pos2] - t1.items[pos1];
                            if (subtraction != 0) {
                                return subtraction;
                            }
                            pos1--;
                            pos2--;
                        }
                        // if they are the same, they we compare based on length
                        return 1;

                    } else {
                        // else if both transactions have the same size
                        while (pos2 >= 0) {
                            int subtraction = t2.items[pos2] - t1.items[pos1];
                            if (subtraction != 0) {
                                return subtraction;
                            }
                            pos1--;
                            pos2--;
                        }
                        // if they ware the same, they we compare based on length
                        return 0;
                    }
                }
            });
            int emptyTransactionCount = 0;
            // for each transaction
            for (int i = 0; i < dataset.getTransactions().size(); i++) {
                // if the transaction length is 0, increase the number of empty transactions
                Transaction transaction = dataset.getTransactions().get(i);
                if (transaction.items.length == 0) {
                    emptyTransactionCount++;
                }
            }
            dataset.transactions = dataset.transactions.subList(emptyTransactionCount, dataset.transactions.size());
        }

        useUtilityBinArrayToCalculateSubtreeUtilityFirstTime(dataset);

        timeSort = System.currentTimeMillis() - timeStartSorting;

        Arrays.fill(utilityBinArrayLU, 0);
        Arrays.fill(utilityBinArraySU, 0);

        // Generate search nodes and table lists
        List<SearchNode> sNodes = new ArrayList<SearchNode>(dataset.getTransactions().size());
        List<SubSearchNode>[] tableList = new ArrayList[itemsToKeep.size()];
        TableLists = new ArrayList[itemsToKeep.size() + 1];
        int i = 0;
        for (int it : itemsToKeep) {
            tableList[i] = new ArrayList<SubSearchNode>();
            TableLists[it] = tableList[i++];
        }

        for (i = 0; i < dataset.getTransactions().size(); i++) {
            Transaction t = dataset.getTransactions().get(i);
            sNodes.add(new SearchNode(t, t.transactionUtility, 0));
            for (int j = 0; j < t.items.length; j++) {
                TableLists[t.items[j]].add(new SubSearchNode(sNodes.get(i), j));
            }
        }

        isItemInKeepFlag = 0;
        isItemInKeep = new int[itemsToKeep.size() + 1];

        prefixLength = -1;
        snLen = 0;
        snMaxLen = 0;
        snBuff = new ArrayList<>();

        if (activateSubtreeUtilityPruning) {
            MeHuim(itemsToKeep, tableList);
        }

        endTimestamp = System.currentTimeMillis();

        if (writer != null) {
            writer.close();
        }
        // check the maximum memory usage
        MemoryLogger.getInstance().checkMemory();
        // return the set of high-utility itemsets
        return highUtilityItemsets;
    }

    /** Constructor */
    public AlgoMEHUIM() {
        ;
    }

    /**
     * Recursively explores the search space to find high utility itemsets.
     *
     * @param itemsToKeep list of promising items to explore
     * @param tableList array of sub-search node lists indexed by item position
     * @throws IOException if an error occurs while writing results
     */
    public void MeHuim(List<Integer> itemsToKeep, List<SubSearchNode>[] tableList) throws IOException {
        prefixLength++;
        int preLen = snLen;
        List<SubSearchNode> ssnlst;
        int subTwu = 0;
        int utilityPe = 0;

        isItemInKeepFlag++;
        int keepFlag1 = isItemInKeepFlag;
        int i = 0;
        for (int it : itemsToKeep) {
            isItemInKeep[it] = keepFlag1;
        }

        for (int j = 0; j < itemsToKeep.size(); j++) {
            long timeBinaryLocal = System.currentTimeMillis();

            int e = itemsToKeep.get(j);
            subTwu = 0;
            ssnlst = tableList[j];

            // Calculate the secondary TWU
            for (SubSearchNode t : ssnlst) {
                subTwu += t.sn.transactionUtility;
            }
            // Apply secondary pruning
            if (subTwu >= minUtil) {

                utilityPe = computeKeepArray(ssnlst, keepFlag1);

                temp[prefixLength] = newNamesToOldNames[e];
                if (utilityPe >= minUtil) {
                    output(prefixLength, utilityPe);
                }

                List<Integer> newItemsToKeep = new ArrayList<Integer>();
                for (int it : itemsToKeep) {
                    if (utilityBinArrayLU[it] >= minUtil) {
                        newItemsToKeep.add(it);
                    }
                    utilityBinArrayLU[it] = 0;
                }
                if (newItemsToKeep.size() != 0) {
                    if (newItemsToKeep.size() == 1) {
                        // Fast processing for the case where only one item remains
                        utilityPe = computeKeep1(ssnlst, newItemsToKeep.get(0));
                        if (utilityPe >= minUtil) {
                            temp[prefixLength + 1] = newNamesToOldNames[newItemsToKeep.get(0)];
                            output(prefixLength, utilityPe);
                        }
                    } else {
                        // Mark the isItemInKeepFlag for this recursive level
                        isItemInKeepFlag++;
                        i = 0;
                        List<SubSearchNode>[] subTableList = new ArrayList[newItemsToKeep.size()];
                        for (int it : newItemsToKeep) {
                            isItemInKeep[it] = isItemInKeepFlag;
                            subTableList[i] = new ArrayList<SubSearchNode>();
                            TableLists[it] = subTableList[i++];
                        }

                        // Expand the search node buffer to the required size
                        for (i = snMaxLen - snLen; i <= ssnlst.size(); i++) {
                            snBuff.add(new SearchNode());
                            snMaxLen++;
                        }

                        updateTransactions(ssnlst, isItemInKeepFlag);

                        MeHuim(newItemsToKeep, subTableList);
                        // Restore the used length of the search node list to this node
                        snLen = preLen;
                    }
                }
            } else {
                for (SubSearchNode t : ssnlst) {
                    t.sn.transactionUtility -= t.sn.t.utilities[t.index];
                }
            }
        }
        prefixLength--;
        MemoryLogger.getInstance().checkMemory();
    }

    /**
     * Updates projected transactions for the next recursive call by extending the prefix.
     *
     * @param ssnlst list of sub-search nodes representing the current projected database
     * @param keepFlag the current keep flag value used to identify items in the keep set
     */
    private void updateTransactions(List<SubSearchNode> ssnlst, int keepFlag) {
        int i;
        Transaction t;
        SearchNode sn;
        for (SubSearchNode ssn : ssnlst) {
            t = ssn.sn.t;
            sn = snBuff.get(snLen++);
            sn.t = t;
            sn.prefixUtility = ssn.sn.prefixUtility + t.utilities[ssn.index];
            sn.transactionUtility = sn.prefixUtility;
            for (i = ssn.index + 1; i < t.items.length; i++) {
                if (isItemInKeep[t.items[i]] >= keepFlag) {
                    TableLists[t.items[i]].add(new SubSearchNode(sn, i));
                    sn.transactionUtility += t.utilities[i];
                }
            }
            if (sn.transactionUtility == sn.prefixUtility) {
                snLen--;
            }
        }
    }

    /**
     * Computes the utility of extending the current prefix with a single item using binary search.
     *
     * @param ssnlst list of sub-search nodes in the projected database
     * @param e the single candidate item to extend the prefix with
     * @return the total utility of the prefix extended by item e
     */
    private int computeKeep1(List<SubSearchNode> ssnlst, int e) {
        int low;
        int high;
        int tu = 0;
        Transaction t;
        for (SubSearchNode ssn : ssnlst) {
            t = ssn.sn.t;
            low = ssn.index;
            high = t.items.length - 1;
            do {
                int middle = (low + high) >>> 1; // divide by 2
                if (t.items[middle] < e) {
                    low = middle + 1;
                } else if (t.items[middle] > e) {
                    high = middle - 1;
                } else {
                    tu += ssn.sn.prefixUtility + t.utilities[ssn.index] + t.utilities[middle];
                    break;
                }
            } while (high >= low);
        }
        return tu;
    }

    /**
     * Computes the utility of the current prefix and updates local utility bins for candidate extensions.
     *
     * @param ssnlst list of sub-search nodes in the projected database
     * @param keepflag the current keep flag value used to identify items in the keep set
     * @return the total utility of the current prefix itemset
     */
    private int computeKeepArray(List<SubSearchNode> ssnlst, int keepflag) {
        int tu = 0;
        Transaction t;
        SearchNode sn;
        for (SubSearchNode ssn : ssnlst) {
            t = ssn.sn.t;
            sn = ssn.sn;
            tu += sn.prefixUtility + t.utilities[ssn.index];
            for (int i = ssn.index + 1; i < t.items.length; i++) {
                if (isItemInKeep[t.items[i]] >= keepflag) {
                    utilityBinArrayLU[t.items[i]] += sn.transactionUtility;
                }
            }
            sn.transactionUtility -= t.utilities[ssn.index];
        }
        return tu;
    }

    /**
     * Calculates the local utility of each item by scanning the database for the first time.
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
     * Sorts a list of items in increasing order of their TWU values using insertion sort.
     *
     * @param items the list of items to sort
     * @param utilityBinArrayTWU array containing the TWU value for each item
     */
    public static void insertionSort(List<Integer> items, int[] utilityBinArrayTWU) {
        // the following lines are simply a modified an insertion sort

        for (int j = 1; j < items.size(); j++) {
            Integer itemJ = items.get(j);
            int i = j - 1;
            Integer itemI = items.get(i);

            // we compare the twu of items i and j
            int comparison = utilityBinArrayTWU[itemI] - utilityBinArrayTWU[itemJ];
            // if the twu is equal, we use the lexicographical order to decide whether i is greater
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
                comparison = utilityBinArrayTWU[itemI] - utilityBinArrayTWU[itemJ];
                // if the twu is equal, we use the lexicographical order to decide whether i is greater
                // than j or not.
                if (comparison == 0) {
                    comparison = itemI - itemJ;
                }
            }
            items.set(i + 1, itemJ);
        }
    }

    /**
     * Calculates the subtree utility of each item by scanning the database for the first time.
     *
     * @param dataset the transaction database
     */
    public void useUtilityBinArrayToCalculateSubtreeUtilityFirstTime(Dataset dataset) {

        int sumSU;
        for (Transaction transaction : dataset.getTransactions()) {
            sumSU = 0;
            for (int i = transaction.getItems().length - 1; i >= 0; i--) {
                Integer item = transaction.getItems()[i];
                sumSU += transaction.getUtilities()[i];
                utilityBinArraySU[item] += sumSU;
            }
        }
    }

    /**
     * Outputs a high utility itemset to file or memory.
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
            System.arraycopy(temp, 0, copy, 0, tempPosition + 1);
            // we create the itemset using this array and add it to the list of itemsets
            // found until now
            highUtilityItemsets.addItemset(new Itemset(copy, utility), copy.length);
        } else {
            // if user wants to save the results to file
            // create a stringuffer
            StringBuffer buffer = new StringBuffer();
            // append each item from the itemset to the stringbuffer, separated by spaces
            for (int i = 0; i <= tempPosition; i++) {
                buffer.append(temp[i]);
                if (i != tempPosition) {
                    buffer.append(' ');
                }
            }
            // append the utility of the itemset
            buffer.append(" #UTIL: ");
            buffer.append(utility);

            // write the stringbuffer to file and create a new line
            // so that we are ready for writing the next itemset.
            writer.write(buffer.toString());
            writer.newLine();
        }
    }

    /**
     * Prints statistics about the last algorithm execution to the console.
     */
    public void printStats() {

        System.out.println("========== MEHUIM v 2.66 - STATS ============");
        System.out.println(" minUtil = " + minUtil);
        System.out.println(" High utility itemsets count: " + patternCount);
        System.out.println(" Total time ~: " + (endTimestamp - startTimestamp)
                + " ms");
        System.out.println(" Max memory:" + MemoryLogger.getInstance().getMaxMemory());
        System.out.println("=====================================");
    }
}