package ca.pfv.spmf.algorithms.frequentpatterns.neclatclosed;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
/*
 ** The implementation of the "NECLATCLOSED algorithm.
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
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * TThe implementation of the "NECLATCLOSED algorithm,
 *  provided by Nader Aryabarzan, available on GitHub via https://github.com/aryabarzan/NEclatClosed.
 * <br/><br/>
 * Aryabarzan, N., & Minaei-Bidgoli, B. (2021). NEclatClosed: A vertical algorithm for mining frequent closed itemsets. Expert Systems with Applications, 174, 114738.
 *
 * @author Nader Aryabarzan (Copyright 2021)
 * @Email aryabarzan@aut.ac.ir or aryabarzan@gmail.com
 */

public class AlgoNEclatClosed {
    // the start time and end time of the last algorithm execution
    long startTimestamp;
    long endTimestamp;

    // number of itemsets found
    int outputCount = 0;

    // object to write the output file
    BufferedWriter writer = null;


    public int numOfFItem; // Number of items
    public int minSupport; // minimum count
    public Item[] item; // list of items sorted by count

    // public FILE out;
    public int[] itemsetX; // the current itemset
    public int itemsetXLen = 0; // the size of the current itemset


    // Tree stuff
    public SetEnumerationTreeNode nlRoot;

    /**
     * A map containing the tidset (i.e. cover) of each item represented as a bitset
     */
    private Map<Integer, BitSet> mapItemTIDS;


    //    public int[] sameItems;
    //private CP_Tree cp_tree;
    private CPStorage cpStorage;


    /**
     * Comparator to sort items by decreasing order of frequency
     */
    static Comparator<Item> comp = new Comparator<Item>() {
        public int compare(Item a, Item b) {
            return ((Item) b).num - ((Item) a).num;
        }
    };

    private int numOfTrans;

    /**
     * Run the algorithm
     *
     * @param input_dataset the input file path
     * @param minsup   the minsup threshold
     * @param output   the output file path
     * @throws IOException if error while reading/writting to file
     */
    public void runAlgorithm(String input_dataset, double minsup, String output)
            throws IOException {
        nlRoot = new SetEnumerationTreeNode();

        MemoryLogger.getInstance().reset();

        // create object for writing the output file
        writer = new BufferedWriter(new FileWriter(output));

        // record the start time
        startTimestamp = System.currentTimeMillis();

        // ==========================
        // Read Dataset
        getData(input_dataset, minsup);

        itemsetXLen = 0;
        itemsetX = new int[numOfFItem];

        // Build tree
        buildTree(input_dataset);

        nlRoot.label = numOfFItem;
        nlRoot.firstChild = null;
        nlRoot.next = null;

        // Initialize tree
        initializeTree();
        cpStorage = new CPStorage();

        // Recursively constructing_frequent_itemset_tree the tree
        SetEnumerationTreeNode curNode = nlRoot.firstChild;
        nlRoot.firstChild = null;
        SetEnumerationTreeNode next = null;
        while (curNode != null) {

            // call the recursive "constructing_frequent_itemset_tree" method
            traverse(curNode, 1);
            next = curNode.next;
            curNode.next = null;
            curNode = next;
        }
        writer.close();

        MemoryLogger.getInstance().checkMemory();

        // record the end time
        endTimestamp = System.currentTimeMillis();
    }

    /**
     * Build the tree
     *
     * @param filename the input filename
     * @throws IOException if an exception while reading/writting to file
     */
    void buildTree(String filename) throws IOException {
        // READ THE FILE
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;

        mapItemTIDS = new HashMap<Integer, BitSet>();
        int tid = 1;

        // for each line (transaction) until the end of the file
        while (((line = reader.readLine()) != null)) {
            // if the line is a comment, is empty or is a
            // kind of metadata
            if (line.isEmpty() == true || line.charAt(0) == '#'
                    || line.charAt(0) == '%' || line.charAt(0) == '@') {
                continue;
            }

            // split the line into items
            String[] lineSplited = line.split(" ");

            // for each item in the transaction
            for (String itemString : lineSplited) {
                // get the item
                int itemX = Integer.parseInt(itemString);

                // add each item from the transaction except infrequent item
                for (int j = 0; j < numOfFItem; j++) {
                    // if the item appears in the list of frequent items, we add
                    // it
                    if (itemX == item[j].index) {
                        // Get the current tidset of that item
                        BitSet tids = mapItemTIDS.get(j);
                        // If none, then we create one
                        if (tids == null) {
                            tids = new BitSet();
                            mapItemTIDS.put(j, tids);
                        }
                        // we add the current transaction id to the tidset of the item
                        tids.set(tid);

                        break;
                    }
                }
            }


            tid++;
        }
        // close the input file
        reader.close();
    }


    /**
     * Read the input file to find the frequent items
     *
     * @param filename   input file name
     * @param minSupport
     * @throws IOException
     */
    void getData(String filename, double minSupport) throws IOException {
        numOfTrans = 0;

        // (1) Scan the database and count the count of each item.
        // The count of items is stored in map where
        // key = item value = count count
        Map<Integer, Integer> mapItemCount = new HashMap<Integer, Integer>();
        // scan the database
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;
        // for each line (transaction) until the end of the file
        while (((line = reader.readLine()) != null)) {
            // if the line is a comment, is empty or is a
            // kind of metadata
            if (line.isEmpty() == true || line.charAt(0) == '#'
                    || line.charAt(0) == '%' || line.charAt(0) == '@') {
                continue;
            }

            numOfTrans++;

            // split the line into items
            String[] lineSplited = line.split(" ");
            // for each item in the transaction
            for (String itemString : lineSplited) {
                // increase the count count of the item by 1
                Integer item = Integer.parseInt(itemString);
                Integer count = mapItemCount.get(item);
                if (count == null) {
                    mapItemCount.put(item, 1);
                } else {
                    mapItemCount.put(item, ++count);
                }
            }

        }
        // close the input file
        reader.close();

        this.minSupport = (int) Math.ceil(minSupport * numOfTrans);

        numOfFItem = mapItemCount.size();

        Item[] tempItems = new Item[numOfFItem];
        int i = 0;
        for (Entry<Integer, Integer> entry : mapItemCount.entrySet()) {
            if (entry.getValue() >= this.minSupport) {
                tempItems[i] = new Item();
                tempItems[i].index = entry.getKey();
                tempItems[i].num = entry.getValue();
                i++;
            }
        }

        item = new Item[i];
        System.arraycopy(tempItems, 0, item, 0, i);

        numOfFItem = item.length;

        Arrays.sort(item, comp);
    }

    /**
     * Initialize the tree
     */
    void initializeTree() {

        SetEnumerationTreeNode lastChild = null;
        for (int t = numOfFItem - 1; t >= 0; t--) {
            SetEnumerationTreeNode nlNode = new SetEnumerationTreeNode();
            nlNode.label = t;
            nlNode.firstChild = null;
            nlNode.next = null;
            nlNode.tidSET = mapItemTIDS.get(nlNode.label);
            nlNode.count = nlNode.tidSET.cardinality();
//            nlNode.tidSET1 = mapItemTIDS.get(nlNode.label);
//            nlNode.support1= nlNode.tidSET1.cardinality();

            if (nlRoot.firstChild == null) {
                nlRoot.firstChild = nlNode;
                lastChild = nlNode;
            } else {
                lastChild.next = nlNode;
                lastChild = nlNode;
            }
        }
    }

    /**
     * Recursively constructing_frequent_itemset_tree the tree to find frequent itemsets
     *
     * @param curNode
     * @throws IOException if error while writing itemsets to file
     */
    public void traverse(SetEnumerationTreeNode curNode, int level) throws IOException {

        MemoryLogger.getInstance().checkMemory();
//        if(curNode.tidSET==null){//curNode was removed
//            return;
//        }
        SetEnumerationTreeNode prev = curNode;
        SetEnumerationTreeNode sibling = prev.next;

        SetEnumerationTreeNode lastChild = null;
        int sameCount = 0;
        itemsetX[itemsetXLen++] = curNode.label;
        while (sibling != null) {
//            if(sibling.tidSET==null){//sibling was removed
//                sibling=sibling.next;
//                continue;
//            }
            SetEnumerationTreeNode child = new SetEnumerationTreeNode();
//            nlNode.tidSET = (BitSet) curNode.tidSET.clone();
//            nlNode.tidSET.and(sibling.tidSET);
//            nlNode.count = nlNode.tidSET.cardinality();

            if (level == 1) {
                if (sibling.tidSET.cardinality() != 0) {
                    child.tidSET = (BitSet) curNode.tidSET.clone();
                    child.tidSET.andNot(sibling.tidSET);
                }
            } else {
                if (curNode.tidSET.cardinality() != 0) {
                    child.tidSET = (BitSet) sibling.tidSET.clone();
                    child.tidSET.andNot(curNode.tidSET);
                }
            }

            child.count = curNode.count - child.tidSET.cardinality();
            if (child.count >= minSupport) {
                if (curNode.count == child.count) {
                    itemsetX[itemsetXLen++] = sibling.label;
                    sameCount++;
                } else {
                    child.label = sibling.label;
                    child.firstChild = null;
                    child.next = null;
                    if (curNode.firstChild == null) {
                        curNode.firstChild = child;
                        lastChild = child;
                    } else {
                        lastChild.next = child;
                        lastChild = child;
                    }
                }
            }
//            if (sibling.count == child.count) {
//                sibling.tidSET = null;//It means this node is removed;
//                prev.next = sibling.next;
////                System.out.println("XXX");
//            }
//            if (sibling.tidSET != null) {
//                prev = sibling;
//            }
//            sibling = prev.next;
            sibling=sibling.next;
        }


        MyBitVector itemsetBitset = new MyBitVector(itemsetX, itemsetXLen);
        if (cpStorage.insertIfClose(itemsetBitset, curNode.count)) {
            // ============= Write itemset(s) to file ===========
            writeItemsetsToFile(curNode.count);
            // ======== end of write to file
        }

        SetEnumerationTreeNode child = curNode.firstChild;
        SetEnumerationTreeNode next;
        curNode.firstChild = null;
        while (child != null) {
            traverse(child, level + 1);
            next = child.next;
            child.next = null;
            child = next;
        }
        itemsetXLen -= (1 + sameCount);
    }

    /**
     * This method write an itemset to file + all itemsets that can be made
     * using its node list.
     *
     * @throws IOException exception if error reading/writting to file
     */
    private void writeItemsetsToFile(int support)
            throws IOException {

        // create a stringuffer
        StringBuilder buffer = new StringBuilder();

        outputCount++;
        // append items from the itemset to the StringBuilder
        for (int i = 0; i < itemsetXLen; i++) {
            buffer.append(item[itemsetX[i]].index);
            buffer.append(' ');
        }

        // append the count of the itemset
        buffer.append("#SUP: ");
        buffer.append(support);
        buffer.append("\n");


        // so that we are ready for writing the next itemset.
        writer.write(buffer.toString());
    }


    /**
     * Print statistics about the latest execution of the algorithm to
     * System.out.
     */

    public void printStats() {
        System.out.println("========== NEclatClosed - STATS ============");
        System.out.println("minSupport : " + (int) (100.0 * this.minSupport / numOfTrans) + "%");
        System.out.println(" Total time ~: " + (endTimestamp - startTimestamp) + " ms");
        System.out.println(" Max memory:" + MemoryLogger.getInstance().getMaxMemory() + " MB");
        System.out.println("=====================================");
    }


    class Item {
        public int index;
        public int num;
    }

    class SetEnumerationTreeNode {
        public int label;
        public SetEnumerationTreeNode firstChild;
        public SetEnumerationTreeNode next;
        BitSet tidSET;
        int count;
    }
}

class MyBitVector {
    static long[] TWO_POWER;

    static {
        TWO_POWER = new long[64];
        for (int i = 0; i < TWO_POWER.length; i++) {
            TWO_POWER[i] = (long) Math.pow(2, i);
        }
    }

    long[] bits;
    public int cardinality;


    public MyBitVector(int[] itemset, int last) {
        int length = itemset[0];
        bits = new long[(length / 64) + 1];
        cardinality = last;
        int item;
        for (int i = 0; i < last; i++) {
            item = itemset[i];
            bits[item / 64] |= MyBitVector.TWO_POWER[item % 64];
        }
    }

    public boolean isSubSet(MyBitVector q) {
        if (cardinality >= q.cardinality) {
            return false;
        }
        for (int i = 0; i < bits.length; i++) {
            if ((bits[i] & (~q.bits[i])) != 0) {
                return false;
            }
        }
        return true;
    }
}

class CPStorage {
    public Map<Integer, ArrayList<MyBitVector>> mapSupportMyBitVector;

    public CPStorage() {
        mapSupportMyBitVector = new HashMap<>();
    }

    public boolean insertIfClose(MyBitVector itemsetBitVector, int support) {
        boolean result = true;
        ArrayList<MyBitVector> bitvectorList = mapSupportMyBitVector.get(support);
        if (bitvectorList == null) {
            bitvectorList = new ArrayList<>();
            mapSupportMyBitVector.put(support, bitvectorList);
            bitvectorList.add(itemsetBitVector);
        } else {
            int index = 0;
            for (MyBitVector q : bitvectorList) {
                if (itemsetBitVector.cardinality >= q.cardinality) {
                    break;
                }
                if (itemsetBitVector.isSubSet(q)) {
                    result = false;
                    break;
                }
                index++;
            }
            if (result != false) {
                bitvectorList.add(index, itemsetBitVector);
            }
        }
        return result;
    }
}