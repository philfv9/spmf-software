package ca.pfv.spmf.algorithms.frequentpatterns.HMiner_CLosed;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the "HMiner_Closed" algorithm for High-Utility Itemsets
 * Mining as described in the conference paper : <br/>
 * <br/>
 * <p>
 * Nguyen L T T, Vu V V, Lam M T H, et al. An efficient method for mining high utility 
 * closed itemsets[J]. Information Sciences, 2019, 495: 78-99., https://doi.org/10.1016/j.ins.2019.05.006.
 * <br/>
 * <br/>
 * The code was obtained from Github repository of user "limuhangk" under the GPL license (since
 * the code is derived from GPL code from SPMF).
 *
 * @author Siddharth Dawar et al.
 * @see MCUL_List
 * @see Element_MCUL_List
 */
public class AlgoHMiner_Closed {

    // variable for statistics
    /**
     * the maximum memory usage
     */
    public double maxMemory = 0;

    /**
     * the time the algorithm started
     */
    public long startTimestamp = 0; //

    /**
     * the time the algorithm ended
     */
    public long endTimestamp = 0;

    /**
     * the time for constructing
     */
    public long construct_time = 0;

    /**
     * the number of HUI generated
     */
    public long huiCount = 0;

    /* Variables used for statistics */
    public long candidateCount = 0, construct_calls = 0, numberRecursions = 0;
    public long closure_time = 0, temp_closure_time = 0, p_laprune = 0,
            p_cprune = 0;
    public long recursive_calls = 0, merging_time = 0, temp_merging_time = 0;

    /**
     * Map to remember the TWU of each item
     */
    Map<Integer, Long> mapItemToTWU;
    /**
     * CHUIs
     * store all the closed high utility itemsets
     */
    CItemsets CHUIs = new CItemsets("Chuis");
    /**
     * writer to write the output file
     */
    BufferedWriter writer = null;
    int jumpnum1 = 0, jumpnum2 = 0, nojumpnum = 0;
    long time_Test = 0, temp_Test = 0;
    /**
     * Output file path
     */
    String outputFile;

    /**
     * EUCS map of the FHM algorithm (item -->  item --> twu
     */
    Map<Integer, Map<Integer, Long>> mapFMAP;
    /**
     * indicate to activate the merging optimization
     */
    public static boolean merging_flag;

    /**
     * indicate to activate the EUCS optimization of FHM
     */
    public static boolean eucs_flag;

    /**
     * activate the debug mode
     */
    boolean debug = false;

    /**
     * start time of the algorithm
     */
    long stats_time = 0;

    /**
     * this class represent an item and its utility in a transaction
     */
    class Pair {
        int item = 0;
        long utility = 0;

        public String toString() {
            return "[" + item + "," + utility + "]";
        }
    }

    /**
     * Get the number of HUIs stored in the HUI-trie structure. This is
     * performed by scanning the HUI-Trie and checking which itemsets are HUIs.
     *
     * @return the number of HUIs.
     */
    public int getRealCHUICount() {
        int count = 0;
        for (List<Itemset> entryHash : CHUIs.getLevels()) {
            if (entryHash == null) {
                continue;
            }
            count += entryHash.size();
        }
        return count;
    }

    /**
     * Write CHUIs found to a file. Note that this method write all CHUIs found
     * until now and erase the file by doing so, if the file already exists.
     *
     * @param output the output file path
     * @throws IOException if error writing to output file
     */
    public void writeCHUIsToFile(String output) throws IOException {
        // writer to write the output file
        BufferedWriter writer = new BufferedWriter(new FileWriter(output));
        //for(int i = 0; i < closedTable.table.length; i++){
        for (List<Itemset> entryHash : CHUIs.getLevels()) {
            if (entryHash == null) {
                continue;
            }
            for (Itemset itemset : entryHash) {
                writer.write(itemset + "\n");
                //chuidCount++;
            }
        }
        // close the file
        writer.close();
    }

    /**
     * Default constructor
     */
    public AlgoHMiner_Closed() {

    }

    /**
     * Run the algorithm
     *
     * @param transactionFile the input file path
     * @param outputFile      the output file path
     * @param minUtility      the minimum utility threshold
     * @throws IOException exception if error while writing the file
     */
    public void runAlgorithm(String transactionFile, String outputFile, long minUtility,
                             boolean merging, boolean EUCS) throws IOException {
        // reset maximum
        // reset maximum
        MemoryLogger.getInstance().reset();

        merging_flag = merging;
        eucs_flag = EUCS;
        mapFMAP = new HashMap<Integer, Map<Integer, Long>>();

        startTimestamp = System.currentTimeMillis();

        // We create a map to store the TWU of each item
        mapItemToTWU = new HashMap<Integer, Long>();


        // We scan the database a first time to calculate the TWU of each item.
        BufferedReader myInput = null;
        String thisLine;
        try {
            // prepare the object for reading the file
            myInput = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(transactionFile))));
            // readUtilityFile(utilityFile);
            // for each line (transaction) until the end of file
            while ((thisLine = myInput.readLine()) != null) {
                // if the line is a comment, is empty or is a
                // kind of metadata
                if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#'
                        || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }

                // ======= PHIL ====
                // split the line according to the separator
                String split[] = thisLine.split(":");
                // get the list of items
                String items[] = split[0].split(" ");
                // the second part is the transaction utility
                int transactionUtility = Integer.parseInt(split[1]);


                // for each item, we add the transaction utility to its TWU
                for (int i = 0; i < items.length; i++) {
                    // convert item to integer
                    Integer item = Integer.parseInt(items[i]);
                    // get the current TWU of that item
                    Long twu = mapItemToTWU.get(item);
                    // add the utility of the item in the current transaction to its twu
                    twu = (twu == null) ?
                            transactionUtility : twu + transactionUtility;
                    mapItemToTWU.put(item, twu);
                }
                // ======= END PHIL ====
            }
        } catch (Exception e) {
            // catches exception if error while reading the input file
            e.printStackTrace();
        } finally {
            if (myInput != null) {
                myInput.close();
            }
        }

        // CREATE A LIST TO STORE THE UTILITY LIST OF ITEMS WITH TWU >=
        // MIN_UTILITY.
        ArrayList<MCUL_List> listOfCULLists = new ArrayList<MCUL_List>();

        HashMap<ArrayList<Integer>, Integer> HT = new HashMap<ArrayList<Integer>, Integer>();
        // CREATE A MAP TO STORE THE UTILITY LIST FOR EACH ITEM.
        // Key : item Value : utility list associated to that item
        Map<Integer, MCUL_List> mapItemToCULList = new HashMap<Integer, MCUL_List>();
        // For each item
        // int counter=0;
        for (Integer item : mapItemToTWU.keySet()) {
            // if the item is promising (TWU >= minutility)

            if (mapItemToTWU.get(item) >= minUtility) {
                // create an empty Utility List that we will fill later.
                MCUL_List uList = new MCUL_List(item);
                mapItemToCULList.put(item, uList);
                // add the item to the list of high TWU items
                listOfCULLists.add(uList);
                // counter++;

            }

        }
        // ul_count_map.put(1, counter);
        // SORT THE LIST OF HIGH TWU ITEMS IN ASCENDING ORDER
        Collections.sort(listOfCULLists, new Comparator<MCUL_List>() {
            public int compare(MCUL_List o1, MCUL_List o2) {
                // compare the TWU of the items
                return compareItems(o1.item, o2.item);
            }
        });

        long time_EUCS = 0, temp_EUCS = 0;
        // SECOND DATABASE PASS TO CONSTRUCT THE CUL LISTS
        // OF 1-ITEMSETS HAVING TWU >= minutil (promising items)
        try {
            // prepare object for reading the file
            myInput = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File(transactionFile))));
            // variable to count the number of transaction
            int tid = 1;
            // for each line (transaction) until the end of file
            while ((thisLine = myInput.readLine()) != null) {
                // if the line is a comment, is empty or is a
                // kind of metadata
                if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#'
                        || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }

                // ======= PHIL ====
                // split the line according to the separator
                String split[] = thisLine.split(":");
                // get the list of items
                String items[] = split[0].split(" ");
                // get the list of utility values corresponding to each item
                // for that transaction
                String utilityValues[] = split[2].split(" ");

                // ======= END PHIL ====


                long remainingUtility = 0;

                long newTWU = 0; // NEW OPTIMIZATION
                ArrayList<Integer> tx_key = new ArrayList<Integer>();
                List<Pair> revisedTransaction = new ArrayList<Pair>();
                // for each item
                for (int i = 0; i < items.length; i++) {
                    /// convert values to integers
                    Pair pair = new Pair();
                    pair.item = Integer.parseInt(items[i]);
                    pair.utility = Integer.parseInt(utilityValues[i]);

                    if (mapItemToTWU.get(pair.item) >= minUtility) {
                        // add it
                        revisedTransaction.add(pair);
                        tx_key.add(pair.item);
                        // remainingUtility += pair.utility;
                        newTWU += pair.utility; // NEW OPTIMIZATION
                    }
                }

                // sort the transaction
                Collections.sort(revisedTransaction, new Comparator<Pair>() {
                    public int compare(Pair o1, Pair o2) {
                        return compareItems(o1.item, o2.item);
                    }
                });
                if (revisedTransaction.size() > 0) {
                    if (merging_flag) {
                        if (!HT.containsKey(tx_key)) {
                            // System.out.println(revisedTransaction.get(revisedTransaction.size()-1));
                            temp_merging_time = System.currentTimeMillis();
                            HT.put(tx_key,
                                    mapItemToCULList.get(revisedTransaction
                                            .get(revisedTransaction.size() - 1).item).elements
                                            .size());
                            merging_time += System.currentTimeMillis()
                                    - temp_merging_time;
                            // for each item left in the transaction
                            for (int i = revisedTransaction.size() - 1; i >= 0; i--) {
                                Pair pair = revisedTransaction.get(i);

                                // float remain = remainingUtility; // FOR
                                // OPTIMIZATION

                                // subtract the utility of this item from the
                                // remaining utility
                                // remainingUtility = remainingUtility -
                                // pair.utility;

                                // get the utility list of this item
                                MCUL_List CULListOfItem = mapItemToCULList
                                        .get(pair.item);

                                // Add a new Element to the utility list of this
                                // item corresponding to this transaction
                                Element_MCUL_List element = new Element_MCUL_List(
                                        tid, pair.utility, remainingUtility, 0, 1,    //sixtuple
                                        0);

                                if (i > 0)// PPOS
                                    element.Ppos = mapItemToCULList
                                            .get(revisedTransaction.get(i - 1).item).elements
                                            .size();
                                else
                                    element.Ppos = -1;

                                CULListOfItem.addElement(element);
                                CULListOfItem.NSupport++;
                                remainingUtility += pair.utility;
                            }
                        }// if HT contains key
                        else // duplicate exists
                        {
                            temp_merging_time = System.currentTimeMillis();
                            int pos = HT.get(tx_key);
                            remainingUtility = 0;
                            // for each item left in the transaction
                            for (int i = revisedTransaction.size() - 1; i >= 0; i--) {

                                // get the utility list of this item
                                MCUL_List CULListOfItem = mapItemToCULList
                                        .get(revisedTransaction.get(i).item);

                                CULListOfItem.elements.get(pos).Nu += revisedTransaction
                                        .get(i).utility;
                                CULListOfItem.elements.get(pos).Nru += remainingUtility;
                                CULListOfItem.sumNu += revisedTransaction
                                        .get(i).utility;
                                CULListOfItem.sumNru += remainingUtility;
                                CULListOfItem.elements.get(pos).WXTj++; //same transaction update wxtj and nsupport
                                CULListOfItem.NSupport++;
                                remainingUtility += revisedTransaction.get(i).utility;
                                pos = CULListOfItem.elements.get(pos).Ppos;
                            }
                            merging_time += System.currentTimeMillis()
                                    - temp_merging_time;

                        }// end of else
                    } else// if merging is disable
                    {
                        // for each item left in the transaction
                        for (int i = revisedTransaction.size() - 1; i >= 0; i--) {
                            Pair pair = revisedTransaction.get(i);

                            // float remain = remainingUtility; // FOR
                            // OPTIMIZATION

                            // subtract the utility of this item from the
                            // remaining utility
                            // remainingUtility = remainingUtility -
                            // pair.utility;

                            // get the utility list of this item
                            MCUL_List CULListOfItem = mapItemToCULList
                                    .get(pair.item);

                            // Add a new Element to the utility list of this
                            // item corresponding to this transaction
                            Element_MCUL_List element = new Element_MCUL_List(
                                    tid, pair.utility, remainingUtility, 0, 1, 0);

                            if (i > 0)// PPOS
                                element.Ppos = mapItemToCULList
                                        .get(revisedTransaction.get(i - 1).item).elements
                                        .size();
                            else
                                element.Ppos = -1;

                            CULListOfItem.addElement(element);
                            CULListOfItem.NSupport++;
                            remainingUtility += pair.utility;

                        }

                    }

                }

                // Build EUCS
                if (eucs_flag) {
                    temp_EUCS = System.currentTimeMillis();
                    for (int i = revisedTransaction.size() - 1; i >= 0; i--) {
                        Pair pair = revisedTransaction.get(i);
                        // BEGIN NEW OPTIMIZATION for FHM
                        Map<Integer, Long> mapFMAPItem = mapFMAP.get(pair.item);
                        if (mapFMAPItem == null) {
                            mapFMAPItem = new HashMap<Integer, Long>();
                            mapFMAP.put(pair.item, mapFMAPItem);
                        }

                        for (int j = i + 1; j < revisedTransaction.size(); j++) {
                            Pair pairAfter = revisedTransaction.get(j);
                            Long twuSum = mapFMAPItem.get(pairAfter.item);
                            if (twuSum == null) {
                                mapFMAPItem.put(pairAfter.item, newTWU);
                            } else {
                                mapFMAPItem
                                        .put(pairAfter.item, twuSum + newTWU);
                            }
                        }

                        // END OPTIMIZATION of FHM

                    }
                    time_EUCS += System.currentTimeMillis() - temp_EUCS;
                }

                tid++; // increase tid number for next transaction

            }
        } catch (Exception e) {
            // to catch error while reading the input file
            e.printStackTrace();
        } finally {
            if (myInput != null) {
                myInput.close();
            }
        }

        // check the memory usage
        checkMemory();

        long initial_time = System.currentTimeMillis() - startTimestamp;
        if (debug) {
            System.out.println("Initial time taken before mining: " + initial_time);
            System.out.println("EUCS time taken before mining: " + time_EUCS);
            System.out.println("Initial merging time: " + merging_time);
        }

        // check the memory usage
        MemoryLogger.getInstance().checkMemory();


//        writer = new BufferedWriter(new FileWriter(outputFile));

        Search_CHUI(new int[0], listOfCULLists, minUtility);

//        writer.close();

        if (debug) {
            System.out.println("Closure time: " + closure_time);
            System.out.println("Final merging time: " + merging_time);
            System.out.println("#recursive calls: " + recursive_calls);
            System.out.println("#LA prune successful: " + p_laprune);
            System.out.println("#C prune + LA prune successful: " + p_cprune);
        }
        // writer_stats.close();

        // System.out.println("HMINER Time: "+fhm_time+" Construct time: "+construct_time+" Number of calls: "+construct_calls);
        // record end time
        endTimestamp = System.currentTimeMillis();
        // check the memory usage again and close the file.
        // check the memory usage again and close the file.
        MemoryLogger.getInstance().checkMemory();

        // writer.close();
        // tempwriter.close();
        // close output file
        // writer.close();

    }

    /*
     * public void readUtilityFile(String utilityFileName) throws
     * FileNotFoundException, IOException { // read the file BufferedReader
     * reader = new BufferedReader(new FileReader( utilityFileName)); String
     * line; // for each line (transaction) until the end of the file while
     * (((line = reader.readLine()) != null)) { // if the line is a comment, is
     * empty or is a // kind of metadata if (line.isEmpty() == true ||
     * line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@')
     * { continue; }
     *
     * String[] lineSplited = line.split(" "); String itemString =
     * lineSplited[0]; String utilityString = lineSplited[1]; Integer item =
     * Integer.parseInt(itemString); Long utility =
     * Long.parseLong(utilityString); utilityMap.put(item, utility); } // close
     * the input file reader.close(); // Read the utility file }
     */

    /**
     * 每次插入项集后，对该项集所在的list按照支持度从小到大进行排序
     *
     * @param level
     */
    public void sortCHUIs(List<Itemset> level) {
        // SORT THE LIST OF HIGH TWU ITEMS IN ASCENDING ORDER
        Collections.sort(level, new Comparator<Itemset>() {
            public int compare(Itemset o1, Itemset o2) {
                // compare the TWU of the items
                return compareItemsbysupport(o1, o2);
            }
        });
    }

    /**
     * Method to compare items by their Support
     *
     * @param o1 an itemset
     * @param o2 another itemset
     * @return 0 if the same item, >0 if item1 is larger than item2, <0
     * otherwise
     */
    private int compareItemsbysupport(Itemset o1, Itemset o2) {
        int compare = o1.support - o2.support;
        // if the same, use the lexical order otherwise use the TWU
        return compare;
    }

    /**
     * Method to compare items by their TWU
     *
     * @param item1 an item
     * @param item2 another item
     * @return 0 if the same item, >0 if item1 is larger than item2, <0
     * otherwise
     */
    private int compareItems(int item1, int item2) {
        int compare = (int) (mapItemToTWU.get(item1) - mapItemToTWU.get(item2));
        // if the same, use the lexical order otherwise use the TWU
        return (compare == 0) ? item1 - item2 : compare;
    }

    /**
     * This is the recursive method to find all high utility itemsets. It writes
     * the itemsets to the output file.
     *
     * @param prefix     This is the current prefix. Initially, it is empty.
     * @param MCULs      The utility lists corresponding to each extension of the
     *                   prefix.
     * @param minUtility The minUtility threshold.
     * @throws IOException
     */
//    private void Search_CHUI(int[] prefix, ArrayList<MCUL_List> MCULs,
//                             long minUtility) throws IOException {
//        recursive_calls++;
//        // For each extension X of prefix P
//        for (int i = 0; i < MCULs.size(); i++) {
//
//            //L2 X = P∪ MUCULs[i];sup = Sup(X)
//            int[] X = appendItem(prefix, MCULs.get(i).item);
//            MCUL_List UL_X = MCULs.get(i);
//            //construct MCUL X
//            int support = UL_X.getSupport();
//
//            //L3 U(X) + RU(X) ≥ e AND HasBackwardExtension(X, sup, CHUIs)
//            if ((UL_X.sumNu + UL_X.sumCu + UL_X.sumNru + UL_X.sumCru >= minUtility && !HasBackwardExtension(X, support, CHUIs.getLevels()))) {
//                candidateCount++;
//                //L4 if(MCULs[i].Tidlist = NULL)
//                if (MCULs.get(i).elements.size() == 0) {//该项集所属的事务都对其具有完全扩展，类似闭包跳跃
//                    //l5  B=X∪{y = MCULs[j].item: j<MUCLs.size and j>i}
//                    int[] Xy = X;
//                    long utilityOfJumpingCLosure = UL_X.sumNu + UL_X.sumCu;
//                    for (int j = i + 1; j < MCULs.size(); j++) {
//                        Xy = appendItem(Xy, MCULs.get(j).item);
//                        utilityOfJumpingCLosure += MCULs.get(j).sumNu + MCULs.get(j).sumCu;
//                    }
//                    //L6 save to file  if(U(B) >= minutil) then CHUI∪ β
//                    if (utilityOfJumpingCLosure >= minUtility) {
//                        CHUIs.addItemset(new Itemset(Xy, (long) utilityOfJumpingCLosure, UL_X.getSupport()), Xy.length);
//                    }
//                } else {
//                    //L8 construct MCULs
//                    ArrayList<MCUL_List> exULs = Construct_MCUL(UL_X, MCULs, i, minUtility, X.length);
//                    //L9
//                    int count = 0;
//                    for (MCUL_List ml : exULs) {
//                        if (ml.getSupport() == UL_X.getSupport())
//                            count++;
//                    }
//                    //L10
//                    if (count == MCULs.size() - i) {    //closure jumping
//                        /////////////////////////////执行闭包跳跃的操作。如果效用大于minutil则输出
//                        int[] Xy = X;
//                        long utilityOfJumpingCLosure = UL_X.sumNu + UL_X.sumCu;
//                        for (int j = i + 1; j < MCULs.size(); j++) {
//                            Xy = appendItem(Xy, MCULs.get(j).item);
//                            utilityOfJumpingCLosure += MCULs.get(j).sumNu + MCULs.get(j).sumCu;
//                        }
//                        if (utilityOfJumpingCLosure >= minUtility) {
//                            CHUIs.addItemset(new Itemset(X, (long) utilityOfJumpingCLosure, UL_X.getSupport()), X.length);
//                        }
//                    } else {
//                        if (count == 0 && UL_X.sumNu + UL_X.sumCu >= minUtility) {//count == 0应该是说明不存在前向扩展
//                            CHUIs.addItemset(new Itemset(X, (long) UL_X.sumNu + UL_X.sumCu, UL_X.getSupport()), X.length);
//                        }
//                        Search_CHUI(X, exULs, minUtility);//存在前向扩展，则项集X不是CHUI，继续搜索其超集
//                    }
//                }
//            }
//        }
//        // check the maximum memory usage for statistics purpose
//        MemoryLogger.getInstance().checkMemory();
//    }
    private void Search_CHUI(int[] prefix, ArrayList<MCUL_List> MCULs,
                             long minUtility) throws IOException {
        recursive_calls++;
        // For each extension X of prefix P
        for (int i = 0; i < MCULs.size(); i++) {

            //L2 X = P∪ MUCULs[i];sup = Sup(X)
            int[] X = appendItem(prefix, MCULs.get(i).item);
            MCUL_List UL_X = MCULs.get(i);
            //construct MCUL X
            int support = UL_X.getSupport();

            //L3 U(X) + RU(X) ≥ e AND HasBackwardExtension(X, sup, CHUIs)
            if (UL_X.sumNu + UL_X.sumCu + UL_X.sumNru + UL_X.sumCru >= minUtility && !HasBackwardExtension(X, support, CHUIs.getLevels())) {
                candidateCount++;
                //L4 if(MCULs[i].Tidlist = NULL) 该项集所在的事务都满足闭合条件。该项集与其后面的所有项的扩展即是闭合项集
                if (MCULs.get(i).elements.size() == 0) {//该项集所属的事务都对其具有完全扩展，类似闭包跳跃
                    //l5  B=X∪{y = MCULs[j].item: j<MUCLs.size and j>i}
                    int[] Xy = X;
                    long utilityOfJumpingCLosure = UL_X.sumNu + UL_X.sumCu;
                    for (int j = i + 1; j < MCULs.size(); j++) {
                        Xy = appendItem(Xy, MCULs.get(j).item);
                        utilityOfJumpingCLosure += (MCULs.get(j).sumCu - MCULs.get(j).sumCpu);
//                        utilityOfJumpingCLosure += (MCULs.get(j).sumNu + MCULs.get(j).sumCu);
                    }
                    //L6 save to file  if(U(B) >= minutil) then CHUI∪ β
                    if (utilityOfJumpingCLosure >= minUtility) {
                        jumpnum1++;
                        CHUIs.addItemset(new Itemset(Xy, (long) utilityOfJumpingCLosure, UL_X.getSupport()), Xy.length);
                        sortCHUIs(CHUIs.getLevels().get(Xy.length));
                    }
                } else {
                    //L8 construct MCULs
                    temp_Test = System.currentTimeMillis();
                    ArrayList<MCUL_List> exULs = Construct_MCUL(UL_X, MCULs, i, minUtility, X.length);
                    time_Test += System.currentTimeMillis() - temp_Test;
                    //L9
                    int count = 0;
                    for (MCUL_List ml : exULs) {
                        if (ml.getSupport() == UL_X.getSupport())
                            count++;
                    }
                    //L10
                    if (count == MCULs.size() - (i + 1) && count != 0) {    //closure jumping
                        /////////////////////////////执行闭包跳跃的操作。如果效用大于minutil则输出
                        int[] Xy = X;
//                        long utilityOfJumpingCLosure = UL_X.sumNu + UL_X.sumCu;
                        long utilityOfJumpingCLosure = 0;
                        for (int j = i + 1; j < MCULs.size(); j++) {
                            Xy = appendItem(Xy, MCULs.get(j).item);
//                            utilityOfJumpingCLosure += MCULs.get(j).sumNu + MCULs.get(j).sumCu;
                        }
                        utilityOfJumpingCLosure += utilityOfJumpingClosure(exULs);
                        if (utilityOfJumpingCLosure >= minUtility) {
                            jumpnum2++;
                            CHUIs.addItemset(new Itemset(Xy, (long) utilityOfJumpingCLosure, UL_X.getSupport()), Xy.length);
                            sortCHUIs(CHUIs.getLevels().get(Xy.length));
                        }
                    } else {
                        if (count == 0 && UL_X.sumNu + UL_X.sumCu >= minUtility) {//count == 0 说明不存在前向扩展
                            nojumpnum++;
                            CHUIs.addItemset(new Itemset(X, (long) UL_X.sumNu + UL_X.sumCu, UL_X.getSupport()), X.length);
                            sortCHUIs(CHUIs.getLevels().get(X.length));
                        }
                        Search_CHUI(X, exULs, minUtility);//存在前向扩展，则项集X不是CHUI，继续搜索其超集
                    }
                }
            }
        }
        // check the maximum memory usage for statistics purpose
        MemoryLogger.getInstance().checkMemory();
    }

    /**
     * @param X     Candidate
     * @param sup   support
     * @param CHUIs set of closed HUIs
     *              它这里的意思应该是CHUIs是按照长度存放的，
     *              长度相同的闭合项集存放在一起,长度相同的CHUIs应该按照支持度大小有序排列
     *              (1 the length of the CHUI is greater than |X|
     *              (2 its support is equal to SUP(X)
     * @return true if P U {e} has no backward extension
     */
    boolean HasBackwardExtension(int[] X, int sup, List<List<Itemset>> CHUIs) {
        int k = X.length;
        int n = CHUIs.size() - 1;//获取当前CHUIs中的闭合项集的最大长度
        if (k >= n)//项集X的长度是否大于CHUIs中最长项集的长度
            return false;
        for (int i = k + 1; i <= n; i++) {   //从长度为k + 1的项集一直检查到长度为n的项集
            int vt = -1;
            int prev = 0;
            vt = binarySearchOverCHUIs(sup, CHUIs.get(i));//在长度为i的CHUIs中找出支持度为sup的闭合项集的位置
            if (vt != -1) {
                prev = vt;
                //这tm感觉不太对啊
                while (prev >= 0 && CHUIs.get(i).get(prev).support == sup) {
                    if (CHUIs.get(i).get(prev).contains(new Itemset(X, 0, 0)))
                        return true;
                    else
                        prev--;
                }
                int next = vt + 1;
                while (next < CHUIs.get(i).size() && CHUIs.get(i).get(next).support == sup) {
                    if (CHUIs.get(i).get(next).contains(new Itemset(X, 0, 0)))
                        return true;
                    else
                        next++;
                }
            }
        }
        return false;
    }

    /**
     * @param support
     * @param CHUIs
     * @return 返回CHUIs中支持度为support的项集在长度为i的ArrayList<Itemset>中的位置,没找到的话就返回-1
     */
    int binarySearchOverCHUIs(int support, List<Itemset> CHUIs) {
        // perform a binary search to check if  the subset appears in  level k-1.
        int first = 0;
        int last = CHUIs.size() - 1;

        // the binary search
        while (first <= last) {
            int middle = (first + last) >>> 1; // divide by 2

            if (CHUIs.get(middle).support < support) {
                first = middle + 1;  //  the itemset compared is larger than the subset according to the lexical order
            } else if (CHUIs.get(middle).support > support) {
                last = middle - 1; //  the itemset compared is smaller than the subset  is smaller according to the lexical order
            } else {
                return middle;
            }
        }
        return -1;//如果找不到就返回 -1
    }

    /**
     * Append an item to an itemset
     *
     * @param itemset an itemset represented as an array of integers
     * @param item    the item to be appended
     * @return the resulting itemset as an array of integers
     */
    private int[] appendItem(int[] itemset, int item) {
        int[] newgen = new int[itemset.length + 1];
        System.arraycopy(itemset, 0, newgen, 0, itemset.length);
        newgen[itemset.length] = item;
        return newgen;
    }

    /**
     * calculate the utility of closure jumping
     */
    private long utilityOfJumpingClosure(ArrayList<MCUL_List> exULs) {
        int utilityOfRemainingItemsJumpingClosure = 0;
//        if(exULs.size() != 0){
        utilityOfRemainingItemsJumpingClosure += exULs.get(0).sumNu + exULs.get(0).sumCu;
        for (int st = 1; st < exULs.size(); st++) {
            utilityOfRemainingItemsJumpingClosure += exULs.get(st).sumNu - exULs.get(st).sumNpu
                    + exULs.get(st).sumCu - exULs.get(st).sumCpu;
//            }
        }
        return utilityOfRemainingItemsJumpingClosure;
    }

    /**
     * @param tid
     * @param elements
     * @return 返回CHUIs中支持度为support的项集在长度为i的ArrayList<Itemset>中的位置,没找到的话就返回-1
     */
    int binarySearchtid(int tid, List<Element_MCUL_List> elements) {
        // perform a binary search to check if  the subset appears in  level k-1.
        int first = 0;
        int last = elements.size() - 1;

        // the binary search
        while (first <= last) {
            int middle = (first + last) >>> 1; // divide by 2

            if (elements.get(middle).tid < tid) {
                first = middle + 1;  //  the itemset compared is larger than the subset according to the lexical order
            } else if (elements.get(middle).tid > tid) {
                last = middle - 1; //  the itemset compared is smaller than the subset  is smaller according to the lexical order
            } else {
                return middle;
            }
        }
        return -1;//如果找不到就返回 -1
    }

    /***
     * Construct a CUL
     * @param X
     * @param MCULs
     * @param st
     * @param minutil
     * @return
     */
    private ArrayList<MCUL_List> Construct_MCUL(MCUL_List X, ArrayList<MCUL_List> MCULs, int st, long minutil, int length) {
        // Need to initialize exCULs;
        ArrayList<MCUL_List> exCULs = new ArrayList<MCUL_List>();
        ArrayList<Long> LAU = new ArrayList<Long>();
        ArrayList<Long> CUTIL = new ArrayList<Long>();
        ArrayList<Integer> ey_tid = new ArrayList<Integer>();
        // int flag=0;
        // Initialization
        for (int i = 0; i <= MCULs.size() - 1; i++) {
            MCUL_List uList = new MCUL_List(MCULs.get(i).item);
            exCULs.add(uList);
            LAU.add((long) 0);
            CUTIL.add((long) 0);
            ey_tid.add(0);
        }
        //L1 sz = |MCULs| -st
        int sz = MCULs.size() - (st + 1);
        int extSz = sz; // number of extensions for closure
        // System.out.println("CUL size: "+CULs.size()+" st: "+st+" sz: "+sz);
        //L3
        for (int j = st + 1; j <= MCULs.size() - 1; j++) {
            // Check EUCS
            if (eucs_flag) {
                Map<Integer, Long> mapTWUF = mapFMAP.get(X.item);
                if (mapTWUF != null) {
                    Long twuF = mapTWUF.get(MCULs.get(j).item);
                    if (twuF != null && twuF < minutil) {
                        exCULs.set(j, null);
                        extSz = sz - 1;
                        // possible error that no value in exCULs and trying to
                        // set in first position.
                    } else //  L5 EUCS successful
                    {      //  L6 Initialize exMCULs L7 Initialize LAU[i] and CUTIL[i]
                        MCUL_List uList = new MCUL_List(MCULs.get(j).item);
                        exCULs.set(j, uList);
                        ey_tid.set(j, 0); // track tid position in CUL
                        LAU.set(j, X.sumCu + X.sumCru + X.sumNu + X.sumNru);
                        CUTIL.set(j, X.sumCu + X.sumCru);
                    }
                }
            } else // EUCS flag disabled
            {
                MCUL_List uList = new MCUL_List(MCULs.get(j).item);
                exCULs.set(j, uList);
                ey_tid.set(j, 0); // track tid position in CUL
                LAU.set(j, X.sumCu + X.sumCru + X.sumNu + X.sumNru);
                CUTIL.set(j, X.sumCu + X.sumCru);
            }

        }

        HashMap<ArrayList<Integer>, Integer> HT = new HashMap<ArrayList<Integer>, Integer>();
        ArrayList<Integer> newT = null;
        for (Element_MCUL_List ex : X.elements) {
            newT = new ArrayList<Integer>();
            // System.out.println("ex.tid processing: "+ex.tid);
            for (int j = st + 1; j <= MCULs.size() - 1; j++) {
                if (exCULs.get(j) == null)
                    continue;
                // compute CULs[st+j].tidlist naive approach
                List<Element_MCUL_List> eylist = MCULs.get(j).elements;
                /*
                 * for(Element_CUL_List e:CULs.get(st+j).elements) {
                 * eylist.add(e.tid); }
                 */

                while (ey_tid.get(j) < eylist.size()
                        && eylist.get(ey_tid.get(j)).tid < ex.tid) {
                    ey_tid.set(j, ey_tid.get(j) + 1); // increment tid
                }

                if (ey_tid.get(j) < eylist.size()
                        && eylist.get(ey_tid.get(j)).tid == ex.tid)
                    newT.add(j); // adding extension position where tid found
                else // apply LA prune
                {
                    LAU.set(j, LAU.get(j) - ex.Nu - ex.Nru); // LA prune
                    if (LAU.get(j) < minutil) {
                        exCULs.set(j, null);
                        extSz = extSz - 1; // might cause an error
                        p_laprune++;
                    }
                }
            } // end for line 24 of Algo 1C

            if (newT.size() == extSz)// all extensions present in tx
            {

                temp_closure_time = System.currentTimeMillis();
                //Update CPU,CU,,CRU,CSUP in all exMCULs
                UpdateClosed(X, MCULs, st, exCULs, newT, ex, ey_tid, length); // Algo
                // 1D
                closure_time += System.currentTimeMillis() - temp_closure_time;

                // flag=1;
            } else
            // if(newT.size()>0)
            {
                if (newT.size() == 0)
                    continue;
                long remainingUtility = 0;
                // System.out.println("newT size: "+newT.size());

                if (merging_flag) {
                    if (!HT.containsKey(newT)) // new transaction
                    {
                        // CULs or exCULs?
                        temp_merging_time = System.currentTimeMillis();
                        HT.put(newT,
                                exCULs.get(newT.get(newT.size() - 1)).elements
                                        .size());
                        merging_time += System.currentTimeMillis()
                                - temp_merging_time;

                        // Insert new entries in exCULs for each newT
                        for (int i = newT.size() - 1; i >= 0; i--) {
                            MCUL_List CULListOfItem = exCULs.get(newT.get(i));
                            Element_MCUL_List Y = MCULs.get(newT.get(i)).elements
                                    .get(ey_tid.get(newT.get(i)));

                            // Add a new Element to the utility list of this
                            // item corresponding to this transaction
                            Element_MCUL_List element = new Element_MCUL_List(
                                    ex.tid, ex.Nu + Y.Nu - ex.Npu,
                                    remainingUtility, ex.Nu, ex.WXTj, 0);

                            if (i > 0)// PPOS
                                element.Ppos = exCULs.get(newT.get(i - 1)).elements
                                        .size();
                            else
                                element.Ppos = -1;

                            CULListOfItem.addElement(element);
                            CULListOfItem.NSupport += ex.WXTj;
                            CULListOfItem.sumNpu += ex.Nu;//update sumNPU
                            remainingUtility += Y.Nu - ex.Npu; // changed check
                            // once

                        }

                    } else // duplicate transaction, update utilities Algo 1E
                    {
                        temp_merging_time = System.currentTimeMillis();
                        int dupPos = HT.get(newT);
                        UpdateElement(X, MCULs, st, exCULs, newT, ex, dupPos,
                                ey_tid);
                        merging_time += System.currentTimeMillis()
                                - temp_merging_time;
                    } // end if
                } else // tx merging disabled
                {
                    // Insert new entries in exCULs for each newT
                    for (int i = newT.size() - 1; i >= 0; i--) {
                        MCUL_List CULListOfItem = exCULs.get(newT.get(i));
                        Element_MCUL_List Y = MCULs.get(newT.get(i)).elements
                                .get(ey_tid.get(newT.get(i)));

                        // Add a new Element to the utility list of this item
                        // corresponding to this transaction
                        Element_MCUL_List element = new Element_MCUL_List(ex.tid,
                                ex.Nu + Y.Nu - ex.Npu, remainingUtility, ex.Nu, 1,
                                0);

                        if (i > 0)// PPOS
                            element.Ppos = exCULs.get(newT.get(i - 1)).elements
                                    .size();
                        else
                            element.Ppos = -1;

                        CULListOfItem.addElement(element);
                        remainingUtility += Y.Nu - ex.Npu; // changed check once

                    }

                }

            }// end if
            for (int j = st + 1; j <= MCULs.size() - 1; j++)
                CUTIL.set(j, CUTIL.get(j) + ex.Nu + ex.Nru);
        }// end for

        // filter
        ArrayList<MCUL_List> filter_CULs = new ArrayList<MCUL_List>();
        for (int j = st + 1; j <= MCULs.size() - 1; j++) {
            if (CUTIL.get(j) < minutil || exCULs.get(j) == null) {
                p_cprune++;
                continue;
            } else {
                if (length > 1) {
                    exCULs.get(j).sumCu += MCULs.get(j).sumCu + X.sumCu
                            - X.sumCpu;
                    exCULs.get(j).sumCru += MCULs.get(j).sumCru;
                    exCULs.get(j).sumCpu += X.sumCu;
                    exCULs.get(j).CSupport += X.CSupport;//new
                }
                filter_CULs.add(exCULs.get(j));

            }
        }

        return filter_CULs;
    }

    /***
     *
     * @param X
     * @param MCULs
     * @param st
     * @param exCULs
     * @param newT
     * @param ey_tid
     */
    private void UpdateClosed(MCUL_List X, ArrayList<MCUL_List> MCULs, int st,
                              ArrayList<MCUL_List> exCULs, ArrayList<Integer> newT,
                              Element_MCUL_List ex, ArrayList<Integer> ey_tid, int length) {
        long nru = 0;
        for (int j = newT.size() - 1; j >= 0; j--) {
            // matched extension at location st+j, j in newT.
            // System.out.println("st: "+st+" newT j: "+newT.get(j));
            MCUL_List ey = MCULs.get(newT.get(j));
            Element_MCUL_List eyy = ey.elements.get(ey_tid.get(newT.get(j)));
            // System.out.println(ex.Nu+ey.elements.get(ey_tid.get(newT.get(j))).Nu
            // -ex.Pu);
            exCULs.get(newT.get(j)).sumCu += ex.Nu + eyy.Nu - ex.Npu;// how to
            // subtract
            // NPU
            exCULs.get(newT.get(j)).sumCru += nru;
            exCULs.get(newT.get(j)).sumCpu += ex.Nu; // ex.Pu or ex.Nu?
            nru = nru + eyy.Nu - ex.Npu;
            exCULs.get(newT.get(j)).CSupport += eyy.WXTj;//new
        }
    }

    /**
     * Update an element
     *
     * @param X
     * @param MCULs
     * @param st
     * @param exCULs
     * @param newT
     * @param ex
     * @param dupPos
     * @param ey_tid
     */
    private void UpdateElement(MCUL_List X, ArrayList<MCUL_List> MCULs, int st,
                               ArrayList<MCUL_List> exCULs, ArrayList<Integer> newT,
                               Element_MCUL_List ex, int dupPos, ArrayList<Integer> ey_tid) {
        long nru = 0;
        int pos = dupPos;
        for (int j = newT.size() - 1; j >= 0; j--) {
            MCUL_List ey = MCULs.get(newT.get(j));
            Element_MCUL_List eyy = ey.elements.get(ey_tid.get(newT.get(j)));
            // System.out.println(exCULs.get(newT.get(j)).elements.size()+" "+pos);
            exCULs.get(newT.get(j)).elements.get(pos).Nu += ex.Nu + eyy.Nu
                    - ex.Npu;
            exCULs.get(newT.get(j)).sumNu += ex.Nu + eyy.Nu - ex.Npu;
            exCULs.get(newT.get(j)).elements.get(pos).Nru += nru;
            exCULs.get(newT.get(j)).sumNru += nru;
            exCULs.get(newT.get(j)).elements.get(pos).Npu += ex.Nu;
            exCULs.get(newT.get(j)).sumNpu += ex.Npu;
//            exCULs.get(newT.get(j)).sumNpu = ex.Nu; //new
            nru = nru + eyy.Nu - ex.Npu;
            exCULs.get(newT.get(j)).elements.get(pos).WXTj += eyy.WXTj; //same transaction update wxtj and nsupport
            exCULs.get(newT.get(j)).NSupport += eyy.WXTj;
            // pos should come from exCULs only.
            // pos=ey.elements.get(ey_tid.get(newT.get(j))).Ppos;
            pos = exCULs.get(newT.get(j)).elements.get(pos).Ppos;
        }
    }


    /**
     * Method to check the memory usage and keep the maximum memory usage.
     */
    private void checkMemory() {
        // get the current memory usage
        double currentMemory = (Runtime.getRuntime().totalMemory() - Runtime
                .getRuntime().freeMemory()) / 1024d / 1024d;
        // if higher than the maximum until now
        if (currentMemory > maxMemory) {
            // replace the maximum with the current memory usage
            maxMemory = currentMemory;
        }
    }

    /**
     * Print statistics about the latest execution to System.out.
     *
     * @throws IOException
     */
    public void printStats() throws IOException {

        System.out.println("=============  HMINER-Closed ALGORITHM v.1.0 - STATS =============");
        System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
        System.out.println(" Max Memory ~ "
                + MemoryLogger.getInstance().getMaxMemory() + " MB");
//        System.out.println(" High-utility Closed itemsets count : "
//                + huiCount);getRealCHUICount
        System.out.println(" High-utility Closed itemsets count : "
                + getRealCHUICount());
        System.out.println(" CandidateCount:" + recursive_calls);
        System.out.println(" Test time taken before mining: " + time_Test);
        System.out.println(" jump1 || jump2 || nojump: " + jumpnum1 + "||" + jumpnum2 + "||" + nojumpnum);
        System.out.println("================================================");
    }

}