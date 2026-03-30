package ca.pfv.spmf.algorithms.frequentpatterns.hui_miner;

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
* 
 */
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
 * This is an implementation of the "PUCP-MINER Algorithm" for High-Utility Itemsets Mining. <br/><br/>
 *
 * @see UtilityListPUCP
 * @see Element
 * @author obtained from the github of github.com/rashmin-gajera under the GPL License
 */
public class AlgoPUCPMiner {

    /**
     * the time at which the algorithm started
     */
    public long startTimestamp = 0;
    /**
     * the time at which the algorithm ended
     */
    public long endTimestamp = 0;

    /* * the number of high-utility itemsets generated */
    public int huiCount = 0;

    /**
     * Map to remember the TWU of each item
     */
    public Map<Integer, Integer> mapItemToTWU;
    Map<Integer, Integer> mapItemToSupport;
    /**
     * writer to write the output file
     */
    BufferedWriter writer = null;

    /**
     * the number of utility-list that was constructed
     */
    private int joinCount;
    private int cmp;
    /* * buffer for storing the current itemset that is mined when performing mining
	* the idea is to always reuse the same buffer to reduce memory usage. */
    final int BUFFERS_SIZE = 200;
    private int[] itemsetBuffer = null;
    int ct = 0;

    /* The eucs structure:  key: item   key: another item   value: twu */
    Map<Integer, Map<Integer, Long>> mapFMAP;
    // Map<Integer, Map<Integer, Long>> mapFMAPnew; 
    Map<Integer, Long> testmap;
    Map<Integer, Long> testmapnew;

    /**
     * this class represent an item and its utility in a transaction
     */
    class Pair {

        int item = 0;
        int utility = 0;
    }

    /**
     * Default constructor
     */
    public AlgoPUCPMiner() {
    }

    /**
     * Run the algorithm
     *
     * @param input the input file path
     * @param output the output file path
     * @param minUtility the minimum utility threshold
     * @throws IOException exception if error while writing the file
     */
    public void runAlgorithm(String input, String output, int minUtility) throws IOException {
        // reset maximum
        MemoryLogger.getInstance().reset();

        // initialize the buffer for storing the current itemset
        itemsetBuffer = new int[BUFFERS_SIZE];

        startTimestamp = System.currentTimeMillis();

        mapFMAP = new HashMap<Integer, Map<Integer, Long>>();
        writer = new BufferedWriter(new FileWriter(output));

        //  We create a  map to store the TWU of each item
        mapItemToTWU = new HashMap<Integer, Integer>();
        mapItemToSupport = new HashMap<Integer, Integer>();

        testmap = new HashMap<Integer, Long>();
        testmapnew = new HashMap<Integer, Long>();

        // We scan the database a first time to calculate the TWU of each item.
        BufferedReader myInput = null;
        String thisLine;
        try {
            // prepare the object for reading the file
            myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
            // for each line (transaction) until the end of file
            while ((thisLine = myInput.readLine()) != null) {
                // if the line is  a comment, is  empty or is a
                // kind of metadata
                if (thisLine.isEmpty() == true
                        || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }

                // split the transaction according to the : separator
                String split[] = thisLine.split(":");
                // the first part is the list of items
                String items[] = split[0].split(" ");
                // the second part is the transaction utility
                int transactionUtility = Integer.parseInt(split[1]);
                // for each item, we add the transaction utility to its TWU
                for (int i = 0; i < items.length; i++) {
                    // convert item to integer
                    Integer item = Integer.parseInt(items[i]);
                    // get the current TWU of that item
                    Integer twu = mapItemToTWU.get(item);

                    //calculate support count of item
                    Integer s = mapItemToSupport.get(item);
                    s = (s == null) ? 1 : s + 1;
                    // add the utility of the item in the current transaction to its twu
                    twu = (twu == null)
                            ? transactionUtility : twu + transactionUtility;
                    mapItemToTWU.put(item, twu);
                    mapItemToSupport.put(item, s);
                }
            }
        } catch (Exception e) {
            // catches exception if error while reading the input file
            e.printStackTrace();
        } finally {
            if (myInput != null) {
                myInput.close();
            }
        }
        
         
//        Integer s;Integer twu; Integer new_support;
//        for(int k:mapItemToSupport.keySet())
//                         {
//                             s=mapItemToSupport.get(k);
//                             twu = mapItemToTWU.get(k);
//                             new_support=(s*twu)/minUtility;
//                             //System.out.println("item Name---> "+k+"   support count---> "+s +"   TWU---> "+twu +"   New Measurement---> "+new_support);
//                             mapItemToSupport.put(k, new_support);
//                         }
        

        // CREATE A LIST TO STORE THE UTILITY LIST OF ITEMS WITH TWU  >= MIN_UTILITY.
        List<UtilityListPUCP> listOfUtilityListPUCPs = new ArrayList<UtilityListPUCP>();
        // CREATE A MAP TO STORE THE UTILITY LIST FOR EACH ITEM.
        // Key : item    Value :  utility list associated to that item
        Map<Integer, UtilityListPUCP> mapItemToUtilityListPUCP = new HashMap<Integer, UtilityListPUCP>();

        // For each item
        for (Integer item : mapItemToTWU.keySet()) {
            // if the item is promising  (TWU >= minutility)
            if (mapItemToTWU.get(item) >= minUtility) {
                // create an empty Utility List that we will fill later.
                UtilityListPUCP uList = new UtilityListPUCP(item);
                mapItemToUtilityListPUCP.put(item, uList);
                // add the item to the list of high TWU items
                listOfUtilityListPUCPs.add(uList);

            }
        }
        // SORT THE LIST OF HIGH TWU ITEMS IN ASCENDING ORDER of support count
        Collections.sort(listOfUtilityListPUCPs, new Comparator<UtilityListPUCP>() {
            public int compare(UtilityListPUCP o1, UtilityListPUCP o2) {
                // compare the TWU of the items
                return compareItems(o1.item, o2.item);
            }
        });

        //System.out.println(listOfUtilityListPUCPs.get(4).item);
        // SECOND DATABASE PASS TO CONSTRUCT THE UTILITY LISTS 
        // OF 1-ITEMSETS  HAVING TWU  >= minutil (promising items)
        try {
            // prepare object for reading the file
            myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
            // variable to count the number of transaction
            int tid = 0;
            // for each line (transaction) until the end of file
            while ((thisLine = myInput.readLine()) != null) {
                // if the line is  a comment, is  empty or is a
                // kind of metadata
                if (thisLine.isEmpty() == true
                        || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
                        || thisLine.charAt(0) == '@') {
                    continue;
                }

                // split the line according to the separator
                String split[] = thisLine.split(":");
                // get the list of items
                String items[] = split[0].split(" ");
                // get the list of utility values corresponding to each item
                // for that transaction
                String utilityValues[] = split[2].split(" ");

                // Copy the transaction into lists but 
                // without items with TWU < minutility
                int remainingUtility = 0;

                long newTWU = 0;  // NEW OPTIMIZATION 

                // Create a list to store items
                List<Pair> revisedTransaction = new ArrayList<Pair>();
                // for each item
                for (int i = 0; i < items.length; i++) {
                    /// convert values to integers
                    Pair pair = new Pair();
                    pair.item = Integer.parseInt(items[i]);
                    pair.utility = Integer.parseInt(utilityValues[i]);
                    // if the item has enough utility
                    if (mapItemToTWU.get(pair.item) >= minUtility) {
                        // add it
                        revisedTransaction.add(pair);
                        remainingUtility += pair.utility;
                        newTWU += pair.utility; // NEW OPTIMIZATION
                    }
                }

                Collections.sort(revisedTransaction, new Comparator<Pair>() {
                    public int compare(Pair o1, Pair o2) {
                        return compareItems(o1.item, o2.item);
                    }
                });

                // for each item left in the transaction
                for (int i = 0; i < revisedTransaction.size(); i++) {
                    Pair pair = revisedTransaction.get(i);

                    int TU = remainingUtility;

                    // System.out.println("TU="+TU);
                    // subtract the utility of this item from the remaining utility
                    remainingUtility = remainingUtility - pair.utility;
                    int pairru = remainingUtility;
                    // get the utility list of this item
                    UtilityListPUCP UtilityListPUCPOfItem = mapItemToUtilityListPUCP.get(pair.item);

                    // Add a new Element to the utility list of this item corresponding to this transaction
                    Element element = new Element(tid, pair.utility, remainingUtility);

                    UtilityListPUCPOfItem.addElement(element);

                    // BEGIN NEW OPTIMIZATION for FHM
                    Map<Integer, Long> mapFMAPItem = mapFMAP.get(pair.item);
                    if (mapFMAPItem == null) {
                        mapFMAPItem = new HashMap<Integer, Long>();
                        mapFMAP.put(pair.item, mapFMAPItem);
                    }
                    for (int j = i + 1; j < revisedTransaction.size(); j++) {
                        Pair pairAfter = revisedTransaction.get(j);

                        //calculate the new expected utility
                        pairru = pairru - pairAfter.utility;
                        //    System.out.println("Pair item="+pair.item+"pair after="+pairAfter.item+"pair utility="+pair.utility+"pair after utiltiy="+pairAfter.utility);
                        long neweu = pair.utility + pairAfter.utility + pairru;
                        //  System.out.println("Neweu="+neweu);                                

                        Long eu = mapFMAPItem.get(pairAfter.item);
                        //  System.out.println("eu="+eu);

                        if (eu == null) {
                            mapFMAPItem.put(pairAfter.item, neweu);
                        } else {
                            mapFMAPItem.put(pairAfter.item, eu + neweu);
                        }

                    }
                    // END OPTIMIZATION of FHM

                }
                tid++; // increase tid number for next transaction

            }

            //Display the REUCS 
            /*  
                        
                          for(int k:mapFMAP.keySet())
                         {
                             testmap=mapFMAP.get(k);
                             for(int j:testmap.keySet())
                             {
                                 System.out.println(k+" "+j+" "+testmap.get(j));
                             }
                         }
                                    System.out.println("Transaction:"+(tid)); 
                                    
                                    
             */
        } catch (Exception e) {
            // to catch error while reading the input file
            e.printStackTrace();
        } finally {
            if (myInput != null) {
                myInput.close();
            }
        }

        // check the memory usage
        MemoryLogger.getInstance().checkMemory();

        Collections.sort(listOfUtilityListPUCPs, new Comparator<UtilityListPUCP>() {
            public int compare(UtilityListPUCP o1, UtilityListPUCP o2) {
                // compare the TWU of the items
                return compareItems(o1.item, o2.item);
            }
        });

        //create a templist of utility list for mining
        List<UtilityListPUCP> LUL1 = new ArrayList<UtilityListPUCP>();

        // Mine the database recursively
        for (int i = 0; i < listOfUtilityListPUCPs.size(); i++) {
            // System.out.println("hello");
            huiMiner(itemsetBuffer, 0, null, listOfUtilityListPUCPs, minUtility, i, LUL1, 0);
        }

        // check the memory usage again and close the file.
        MemoryLogger.getInstance().checkMemory();
        // close output file
        writer.close();
        // record end time
        endTimestamp = System.currentTimeMillis();
    }

    private int compareItems(int item1, int item2) {
        int compare = mapItemToSupport.get(item1) - mapItemToSupport.get(item2);
        // if the same, use the lexical order otherwise use the TWU
        return (compare == 0) ? item1 - item2 : compare;
    }

    // items are sorted as per TWU ascending order
    private int compareItems1(int item1, int item2) {
        int compare = mapItemToTWU.get(item1) - mapItemToTWU.get(item2);
        // if the same, use the lexical order otherwise use the TWU
        return (compare == 0) ? item1 - item2 : compare;
    }

    /* *
	 * This is the recursive method to find all high utility itemsets. It writes
	 * the itemsets to the output file.
	 * @param prefix  This is the current prefix. Initially, it is empty.
	 * @param pUL This is the Utility List of the prefix. Initially, it is empty.
	 * @param ULs The utility lists corresponding to each extension of the prefix.
	 * @param minUtility The minUtility threshold.
	 * @param prefixLength The current prefix length
	 * @throws IOException
     */
    private void huiMiner(int[] prefix,
            int prefixLength, UtilityListPUCP pUL, List<UtilityListPUCP> ULs, int minUtility, int i, List<UtilityListPUCP> TULs, int f)
            throws IOException {
        //("\n\n");
        // For each extension X of prefix P
        //for(int i=0; i< ULs.size(); i++){
        //    System.out.println("call");

        UtilityListPUCP X;
        if (f == 0) {
            X = ULs.get(i);
        } else {
            X = TULs.get(0);
        }

        //(X.item);
        // If pX is a high utility itemset.
        // we save the itemset:  pX 
        //    for(int p=0;p<prefixLength;p++)
        //     {
        //         System.out.print(prefix[p]+" ");
        //     }
        //System.out.print(X.item);
        if (X.sumIutils >= minUtility) {
            // save to file
            writeOut(prefix, prefixLength, X.item, X.sumIutils);
        }

        // If the sum of the remaining utilities for pX
        // is higher than minUtility, we explore extensions of pX.
        // (this is the pruning condition)
        //      System.out.println("sum iutilrutil="+(X.sumIutils+X.sumRutils));
        if ((X.sumIutils + X.sumRutils) - X.sumIutils_ZR >= minUtility) {
            // This list will contain the utility lists of pX extensions.
            //List<UtilityListPUCP> exULs = new ArrayList<UtilityListPUCP>();
            // For each extension of p appearing
            // after X according to the ascending order
            for (int j = i + 1; j < ULs.size(); j++) {
                ct = ct + 1;
                //      System.out.println("i="+i+"j="+j);
                UtilityListPUCP Y = ULs.get(j);

                // ======================== NEW OPTIMIZATION USED IN FHM
                if (f == 0) {
                    Map<Integer, Long> mapTWUF = mapFMAP.get(X.item);
                    if (mapTWUF != null) {
                        Long twuF = mapTWUF.get(Y.item);
                        //  System.out.println("twu="+twuF);
                        if (twuF == null || twuF < minUtility) {
                            continue;
                        }
                    }
                }

                //    System.out.println("x="+X.item+"y="+Y.item);
                // we construct the extension pXY 
                // and add it to the list of extensions of pX
                List<UtilityListPUCP> exULs = new ArrayList<UtilityListPUCP>();
                exULs.add(construct(pUL, X, Y));
                joinCount++;

                // We create new prefix pX
                itemsetBuffer[prefixLength] = X.item;
                prefixLength = prefixLength + 1;
                // We make a recursive call to discover all itemsets with the prefix pXY
                huiMiner(itemsetBuffer, prefixLength, X, ULs, minUtility, j, exULs, 1);
                //        System.out.println("return");
                prefixLength = prefixLength - 1;
            }
        }
        //}
    }
    private int while_loop;
    /**
     * This method constructs the utility list of pXY
     *
     * @param P : the utility list of prefix P.
     * @param px : the utility list of pX
     * @param py : the utility list of pY
     * @return the utility list of pXY
     */
    private UtilityListPUCP construct(UtilityListPUCP P, UtilityListPUCP px, UtilityListPUCP py) {
        // create an empy utility list for pXY
        // System.out.println("Construct initialization");
        UtilityListPUCP pxyUL = new UtilityListPUCP(py.item);


        // improved join methods
        int xsize = px.elements.size() - 1;
        int ysize = py.elements.size() - 1;
        int pxcount = 0;
        int pycount = 0;
        Element ex;
        Element ey;
//        System.out.println(xsize + "   " + ysize);
        while (pxcount <= xsize && pycount <= ysize) {
            while_loop++;
            ex = px.elements.get(pxcount);
            ey = py.elements.get(pycount);
            //  System.out.println("pxcount="+pxcount);
            //  System.out.println("Pycount="+pycount);

            if (ex.rutils == 0) {
                pxcount++;
                continue;
            }

            if (ex.tid == ey.tid) {
                if (P == null) {
                    // Create the new element

                    Element eXY = new Element(ex.tid, ex.iutils + ey.iutils, ey.rutils);
                    // add the new element to the utility list of pXY
                    pxyUL.addElement(eXY);

                } else {
                    // find the element in the utility list of p wih the same tid
                    Element e = findElementWithTID(P, ex.tid);
                    if (e != null) {
                        // Create new element
                        Element eXY = new Element(ex.tid, ex.iutils + ey.iutils,
                                ey.rutils);
                        // add the new element to the utility list of pXY
                        // System.out.println(pxyUL.item);
                        pxyUL.addElement(eXY);
                    }
                }
                pxcount++;
                pycount++;
            } else if (ex.tid > ey.tid) {
                pycount++;

            } else {
                pxcount++;
            }
        }

        // end of improved join
        // return the utility list of pXY.
        return pxyUL;
    }

    /* *
	 * Do a binary search to find the element with a given tid in a utility list
	 * @param ulist the utility list
	 * @param tid  the tid
	 * @return  the element or null if none has the tid.
     */
    private Element findElementWithTID(UtilityListPUCP ulist, int tid) {
        List<Element> list = ulist.elements;

        // perform a binary search to check if  the subset appears in  level k-1.
        int first = 0;
        int last = list.size() - 1;

        // the binary search
        while (first <= last) {
            int middle = (first + last) >>> 1; // divide by 2
            cmp++;
            if (list.get(middle).tid < tid) {
                first = middle + 1;  //  the itemset compared is larger than the subset according to the lexical order
            } else if (list.get(middle).tid > tid) {
                last = middle - 1; //  the itemset compared is smaller than the subset  is smaller according to the lexical order
            } else {
                return list.get(middle);
            }
        }
        return null;
    }

    /* *
	 * Method to write a high utility itemset to the output file.
	 * @param the prefix to be writent o the output file
	 * @param an item to be appended to the prefix
	 * @param utility the utility of the prefix concatenated with the item
	 * @param prefixLength the prefix length
     */
    private void writeOut(int[] prefix, int prefixLength, int item, long utility) throws IOException {
        huiCount++; // increase the number of high utility itemsets found

        //Create a string buffer
        StringBuilder buffer = new StringBuilder();
        // append the prefix
        for (int i = 0; i < prefixLength; i++) {
            buffer.append(prefix[i]);
            buffer.append(' ');
        }
        // append the last item
        buffer.append(item);
        // append the utility value
        buffer.append(" #UTIL: ");
        buffer.append(utility);
        // write to file
        writer.write(buffer.toString());
        writer.newLine();
    }

    /**
     * Print statistics about the latest execution to System.out.
     */
    public void printStats() {
        System.out.println("============= PUCP-MINER ALGORITHM - STATS =============");
        System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
        System.out.println(" Memory ~ " + MemoryLogger.getInstance().getMaxMemory() + " MB");
        System.out.println(" High-utility itemsets count : " + huiCount);
        System.out.println(" Join count : " + joinCount);
        System.out.println(" Comparisons : " + cmp);
        System.out.println(" While loop Comparisons : " + while_loop);
        System.out.println("===================================================================");
        System.out.println("count=" + ct);
    }
}