package ca.pfv.spmf.algorithms.frequentpatterns.npfpm;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger
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


import ca.pfv.spmf.tools.MemoryLogger;

import java.io.*;
import java.util.*;

/**
 * This is an implementation of the NPFPM algorithm (Afriyie et al, 2021).
 * NPFPM is described here:
 * <br/><br/>
 * Afriyie, M. K, Nofong, V. M., Wondoh, J., Abdel-Fatao, H. (2021, January) Efficient Mining of Non-Redundant Periodic Frequent Patterns. In Vietnam Journal of Computer Science (Vol. 8, No. 4, pp 1-15). World Scientific Publishing Company.
 * Afriyie, M. K, Nofong, V. M., Wondoh, J., Abdel-Fatao, H. (2020, March) Mining Non-redundant Periodic Frequent Patterns.  In: Nguyen N., Jearanaitanakij K., Selamat A., Trawiński B., Chittayasothorn S. (eds) Intelligent Information and Database Systems. LNCS, Vol. 12033, pp 321-331. Springer
 * <br/><br/>
 * <p>
 * This is an optimized version that saves the result to a file
 * or keep it into memory if no output path is provided
 * by the user to the runAlgorithm method().
 *
 * @see PFPTree
 * @see Itemset
 * @see Itemsets
 * Vincent M. Nofong (modified from Philippe Fournier-Viger's implementation of FPGrowth)
 */
public class AlgoNPFPM {

    // for statistics
    private long startTimestamp; // start time of the latest execution
    private long endTime; // end time of the latest execution
    private int transactionCount = 0; // transaction count in the database
    private int itemsetCount; // number of freq. itemsets found

    //Hashmap to store unique items and their coversets in database
    //key = length-1 item; value = coverset
    final Map<Integer, Set<Integer>> mapCoverset = new HashMap<>();
    //Hashmap to store the non-redundant periodic frequent patterns
    //key = item; value = list of information: coverset, periods, min period, max period, avg period, stdv in periods
   // public final Map<List<Integer>, List<Double>> NPFPs = new HashMap<>();


    // parameters
    public int minSupportRelative;// the relative minimum support
    public double periodicity; //the user desired periodicity
    public double difference;  //the user desired periodicity difference
    public double lowPeriodThresh; //acceptable lowest periodicity
    public double highPeriodThresh; //acceptable highest periodicity

    BufferedWriter writer = null; // object to write the output file

    // The  patterns that are found
    // (if the user wants to keep them into memory)
    protected Itemsets patterns = null;

    // This variable is used to determine the size of buffers to store itemsets.
    // A value of 50 is enough because it allows up to 2^50 patterns!
    final int BUFFERS_SIZE = 2000;

    // buffer for storing the current itemset that is mined when performing mining
    // the idea is to always reuse the same buffer to reduce memory usage.
    private int[] itemsetBuffer = null;
    // another buffer for storing fpnodes in a single path of the tree
    private PFPNode[] fpNodeTempBuffer = null;

    // This buffer is used to store an itemset that will be written to file
    // so that the algorithm can sort the itemset before it is output to file
    // (when the user choose to output result to file).
    private int[] itemsetOutputBuffer = null;

    /**
     * maximum pattern length
     */
    private int maxPatternLength = 1000;

    /**
     * minimum pattern length
     */
    private int minPatternLength = 0;

    /**
     * Constructor
     */
    public AlgoNPFPM() {

    }

    /**
     * Method to run the FPGRowth algorithm.
     *
     * @param input       the path to an input file containing a transaction database.
     * @param output      the output file path for saving the result (if null, the result
     *                    will be returned by the method instead of being saved).
     * @param minsupp     the minimum support threshold.
     * @param periodicity the user periodicity threshold
     * @param difference  user acceptable difference in periodicities
     * @return the result if no output file path is provided.
     * @throws IOException exception if error reading or writing files
     */
    public Itemsets runAlgorithm(String input, String output, double minsupp, double periodicity, double difference) throws FileNotFoundException, IOException {
        // record start time
        startTimestamp = System.currentTimeMillis();
        // number of itemsets found
        itemsetCount = 0;

        //initialize tool to record memory usage
        MemoryLogger.getInstance().reset();
        MemoryLogger.getInstance().checkMemory();

        // if the user want to keep the result into memory
        if (output == null) {
            writer = null;
            patterns = new Itemsets("FREQUENT ITEMSETS");
        } else { // if the user want to save the result to a file
            patterns = null;
            writer = new BufferedWriter(new FileWriter(output));
            itemsetOutputBuffer = new int[BUFFERS_SIZE];
        }

        // (1) PREPROCESSING: Initial database scan to determine the frequency of each item
        // The frequency is stored in a map:
        //    key: item   value: support
        final Map<Integer, Integer> mapSupport = scanDatabaseToDetermineFrequencyOfSingleItems(input);

        // convert the minimum support as percentage to a
        // relative minimum support
        this.minSupportRelative = (int) Math.ceil(minsupp * transactionCount);

        //the periodicity threshold from user
        //the difference threshold from user
        this.periodicity = periodicity;
        this.difference = difference;

        //compute lowest acceptable periodicity threshold
        this.lowPeriodThresh = periodicity - difference;
        //compute highest user acceptable periodicity threhold
        this.highPeriodThresh = periodicity + difference;

        // (2) Scan the database again to build the initial FP-Tree
        // Before inserting a transaction in the FPTree, we sort the items
        // by descending order of support.  We ignore items that
        // do not have the minimum support.
        PFPTree tree = new PFPTree();

        // read the file
        BufferedReader reader = new BufferedReader(new FileReader(input));
        String line;
        // for each line (transaction) until the end of the file
        while (((line = reader.readLine()) != null)) {
            // if the line is  a comment, is  empty or is a
            // kind of metadata
            if (line.isEmpty() == true || line.charAt(0) == '#' || line.charAt(0) == '%'
                    || line.charAt(0) == '@') {
                continue;
            }

            String[] lineSplited = line.split(" ");

            List<Integer> transaction = new ArrayList<Integer>();

            // for each item in the transaction
            for (String itemString : lineSplited) {
                Integer item = Integer.parseInt(itemString);
                // only add items that have the minimum support
                if (mapSupport.get(item) >= minSupportRelative) {
                    transaction.add(item);
                }
            }

            // sort item in the transaction by descending order of support
            Collections.sort(transaction, new Comparator<Integer>() {
                public int compare(Integer item1, Integer item2) {
                    // compare the frequency
                    int compare = mapSupport.get(item2) - mapSupport.get(item1);
                    // if the same frequency, we check the lexical ordering!
                    if (compare == 0) {
                        return (item1 - item2);
                    }
                    // otherwise, just use the frequency
                    return compare;
                }
            });

            // add the sorted transaction to the fptree.
            tree.addTransaction(transaction);
        }
        // close the input file
        reader.close();

        // We create the header table for the tree using the calculated support of single items
        tree.createHeaderList(mapSupport);

        // (5) We start to mine the FP-Tree by calling the recursive method.
        // Initially, the prefix alpha is empty.
        // if at least an item is frequent
        if (tree.headerList.size() > 0) {

            // initialize the buffer for storing the current itemset
            itemsetBuffer = new int[BUFFERS_SIZE];
            // and another buffer
            fpNodeTempBuffer = new PFPNode[BUFFERS_SIZE];
            // recursively generate frequent itemsets using the fp-tree
            // Note: we assume that the initial FP-Tree has more than one path
            // which should generally be the case.
            fpgrowth(tree, itemsetBuffer, 0, transactionCount, mapSupport);
        }

        // close the output file if the result was saved to a file
        if (writer != null) {
            writer.close();
        }
        // record the execution end time
        endTime = System.currentTimeMillis();

        // check the memory usage
        MemoryLogger.getInstance().checkMemory();

        // return the result (if saved to memory)
        return patterns;
    }

    /**
     * Mine an FP-Tree having more than one path.
     *
     * @param tree       the FP-tree
     * @param prefix     the current prefix, named "alpha"
     * @param mapSupport the frequency of items in the FP-Tree
     * @throws IOException exception if error writing the output file
     */
    private void fpgrowth(PFPTree tree, int[] prefix, int prefixLength, int prefixSupport, Map<Integer, Integer> mapSupport) throws IOException {

        if (prefixLength == maxPatternLength) {
            return;
        }

        // We will check if the FPtree contains a single path
        boolean singlePath = true;
        // This variable is used to count the number of items in the single path
        // if there is one
        int position = 0;
        // if the root has more than one child, than it is not a single path
        if (tree.root.childs.size() > 1) {
            singlePath = false;
        } else {

            // Otherwise,
            // if the root has exactly one child, we need to recursively check childs
            // of the child to see if they also have one child
            PFPNode currentNode = tree.root.childs.get(0);

            while (true) {
                // if the current child has more than one child, it is not a single path!
                if (currentNode.childs.size() > 1) {
                    singlePath = false;
                    break;
                }
                // otherwise, we copy the current item in the buffer and move to the child
                // the buffer will be used to store all items in the path
                fpNodeTempBuffer[position] = currentNode;

                position++;
                // if this node has no child, that means that this is the end of this path
                // and it is a single path, so we break
                if (currentNode.childs.size() == 0) {
                    break;
                }
                currentNode = currentNode.childs.get(0);
            }
        }

        // Case 1: the FPtree contains a single path
        if (singlePath) {
            // We save the path, because it is a maximal itemset

            saveAllCombinationsOfPrefixPath(fpNodeTempBuffer, position, prefix, prefixLength);
        } else {

            // For each frequent item in the header table list of the tree in reverse order.
            for (int i = tree.headerList.size() - 1; i >= 0; i--) {
                // get the item
                Integer item = tree.headerList.get(i);

                // get the item support
                int support = mapSupport.get(item);

                // Create Beta by concatening prefix Alpha by adding the current item to alpha
                prefix[prefixLength] = item;

                // calculate the support of the new prefix beta
                int betaSupport = (prefixSupport < support) ? prefixSupport : support;


                // save beta to the output file
                saveItemset(prefix, prefixLength + 1, betaSupport);

                if (prefixLength + 1 < maxPatternLength) {

                    // === (A) Construct beta's conditional pattern base ===
                    // It is a subdatabase which consists of the set of prefix paths
                    // in the FP-tree co-occuring with the prefix pattern.
                    List<List<PFPNode>> prefixPaths = new ArrayList<List<PFPNode>>();
                    PFPNode path = tree.mapItemNodes.get(item);

                    // Map to count the support of items in the conditional prefix tree
                    // Key: item   Value: support
                    Map<Integer, Integer> mapSupportBeta = new HashMap<Integer, Integer>();

                    while (path != null) {
                        // if the path is not just the root node
                        if (path.parent.itemID != -1) {
                            // create the prefixpath
                            List<PFPNode> prefixPath = new ArrayList<PFPNode>();
                            // add this node.
                            prefixPath.add(path);   // NOTE: we add it just to keep its support,
                            // actually it should not be part of the prefixPath

                            // ####
                            int pathCount = path.counter;

                            //Recursively add all the parents of this node.
                            PFPNode parent = path.parent;
                            while (parent.itemID != -1) {
                                prefixPath.add(parent);

                                // FOR EACH PATTERN WE ALSO UPDATE THE ITEM SUPPORT AT THE SAME TIME
                                // if the first time we see that node id
                                if (mapSupportBeta.get(parent.itemID) == null) {
                                    // just add the path count
                                    mapSupportBeta.put(parent.itemID, pathCount);
                                } else {
                                    // otherwise, make the sum with the value already
                                    mapSupportBeta.put(parent.itemID, mapSupportBeta.get(parent.itemID) + pathCount);
                                }
                                parent = parent.parent;
                            }
                            // add the path to the list of prefixpaths
                            prefixPaths.add(prefixPath);
                        }
                        // We will look for the next prefixpath
                        path = path.nodeLink;
                    }

                    // (B) Construct beta's conditional FP-Tree
                    // Create the tree.
                    PFPTree treeBeta = new PFPTree();
                    // Add each prefixpath in the FP-tree.
                    for (List<PFPNode> prefixPath : prefixPaths) {
                        treeBeta.addPrefixPath(prefixPath, mapSupportBeta, minSupportRelative);
                    }

                    // Mine recursively the Beta tree if the root has child(s)
                    if (treeBeta.root.childs.size() > 0) {
                        // Create the header list.
                        treeBeta.createHeaderList(mapSupportBeta);
                        // recursive call
                        fpgrowth(treeBeta, prefix, prefixLength + 1, betaSupport, mapSupportBeta);
                    }
                }
            }
        }
    }


    /**
     * This method saves all combinations of a prefix path if it has enough support
     *
     * @param //prefixPath     the prefix path
     * @param fpNodeTempBuffer
     * @param prefix           the current prefix
     * @param prefixLength     the current prefix length
     * @throws IOException if exception while writting to output file
     */
    private void saveAllCombinationsOfPrefixPath(PFPNode[] fpNodeTempBuffer, int position,
                                                 int[] prefix, int prefixLength) throws IOException {

        int support = 0;
        // Generate all subsets of the prefixPath except the empty set
        // and output them
        // We use bits to generate all subsets.
        loop1:
        for (long i = 1, max = 1 << position; i < max; i++) {

            // we create a new subset
            int newPrefixLength = prefixLength;

            // for each bit
            for (int j = 0; j < position; j++) {
                // check if the j bit is set to 1
                int isSet = (int) i & (1 << j);
                // if yes, add the bit position as an item to the new subset
                if (isSet > 0) {
                    if (newPrefixLength == maxPatternLength) {
                        continue loop1;
                    }

                    prefix[newPrefixLength++] = fpNodeTempBuffer[j].itemID;
                    // 2018-03-18: REMOVED THE FOLLOWING "IF" to fix
                    // support counting error.
                    //					if(support == 0) {
                    support = fpNodeTempBuffer[j].counter;
                    //					}
                }
            }
            // save the itemset
            saveItemset(prefix, newPrefixLength, support);
        }
    }


    /**
     * This method scans the input database to calculate the support of single items
     *
     * @param input the path of the input file
     * @return a map for storing the support of each item (key: item, value: support)
     * @throws IOException exception if error while writing the file
     */
    private Map<Integer, Integer> scanDatabaseToDetermineFrequencyOfSingleItems(String input)
            throws FileNotFoundException, IOException {
        // a map for storing the support of each item (key: item, value: support)
        Map<Integer, Integer> mapSupport = new HashMap<Integer, Integer>();
        //Create object for reading the input file
        BufferedReader reader = new BufferedReader(new FileReader(input));
        String line;

        int TID = 0;//TID is used to store the transaction IDs (coverset) of unique length-1 items

        // for each line (transaction) until the end of file
        while (((line = reader.readLine()) != null)) {
            // if the line is  a comment, is  empty or is a
            // kind of metadata
            if (line.isEmpty() == true || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
                continue;
            }
            //Increment TID if line is not a comment, is empty or is some kind of metadata
            TID = TID + 1;

            // split the line into items
            String[] lineSplited = line.split(" ");
            // for each item
            for (String itemString : lineSplited) {
                // increase the support count of the item
                Integer item = Integer.parseInt(itemString);
                // increase the support count of the item
                Integer count = mapSupport.get(item);

                if (count == null) {
                    //newCov is to store the set of transaction IDs
                    Set<Integer> newCov = new HashSet<>();
                    newCov.add(TID);
                    mapSupport.put(item, 1);
                    //update or put item and its coverset into mapCoverset
                    mapCoverset.put(item, newCov);
                } else {
                    //newCov is to store the set of transaction IDs
                    Set<Integer> newCov = mapCoverset.get(item);
                    newCov.add(TID);
                    //update or put item and its coverset into mapCoverset
                    mapCoverset.put(item, newCov);
                    mapSupport.put(item, ++count);
                }
            }
            // increase the transaction count
            transactionCount++;
        }
        // close the input file
        reader.close();

        return mapSupport;
    }

    /**
     * Write a frequent itemset that is found to the output file or
     * keep into memory if the user prefer that the result be saved into memory.
     */
    private void saveItemset(int[] itemset, int itemsetLength, int supp) throws IOException {
        if (itemsetLength < minPatternLength) {
            return;
        }
        int support = supp;
        // if the result should be saved to a file
        if (writer != null) {
            // copy the itemset in the output buffer and sort items
            System.arraycopy(itemset, 0, itemsetOutputBuffer, 0, itemsetLength);
            Arrays.sort(itemsetOutputBuffer, 0, itemsetLength);

            // Create a string buffer
            StringBuilder buffer = new StringBuilder();
            // write the items of the itemset

            //create set to store coverset of each item
            Set<Integer> itemCoverset = new HashSet<>();
            //create list of items that will be saved
            List<Integer> SavedItem = new ArrayList<>();
            //create set for to store items - this will be used in generating all
            //subsets for non-redundance check
            Set<Integer> itemForSubset = new HashSet<>();

            //for each item in the itemsetLength
            for (int i = 0; i < itemsetLength; i++) {
                if (i == 0) {
                    //get the coverset of the item at the i-th index
                    itemCoverset.addAll(mapCoverset.get(itemsetOutputBuffer[i]));
                }
                //this will ensure if there are more than one items, the coverset
                //will be the intersection of the coversets of all items
                itemCoverset.retainAll(mapCoverset.get(itemsetOutputBuffer[i]));
                //add item to savedItem
                SavedItem.add(itemsetOutputBuffer[i]);
                //add item to itemForSubset
                itemForSubset.add(itemsetOutputBuffer[i]);
            }

            Collections.sort(SavedItem);

             /*
                 Check non-redundance of length 2 and above itemsets
             */
            if (itemForSubset.size() >= 2) {


                //variable to count the number of possible combinations based on
                //non-redundance test
                int count = 0;
                //variable to check if itemset is redundant or not
                int Redundant = 0;

                //Generate all subsets of itemForSubset and for any subset
                //check if the main itemset is redundant or not
                for (Set<Integer> s : powerSet(itemForSubset)) {

                    //if one subset indicates itemset is redundant, stop the non-redundance test
                    //if count == count == (powerSet(itemForSubset).size() - 2 indicates all subsets have been checked
                    if (count == (powerSet(itemForSubset).size() - 2) || Redundant > 0) {
                        break;
                    }

                    //if the subset selected has no elements or is same as the itemset itself
                    //do nothing - continue. This is because the powerset will return all proper
                    // subsets of the itemset including null set and the itemset itself
                    if (s.size() == 0 || s.size() == itemForSubset.size()) {
                        continue;
                    }

                    //Hashset to store the selected subset
                    Set<Integer> union = new HashSet<>();
                    union.addAll(s);

                    //increment count by 1
                    count++;

                    //Create list of the subset selected - this is to ensure easy
                    //retrieval of their supports from FPs or mapCoverset since these
                    //hashmaps save the item as a list
                    List<Integer> firstPart = new ArrayList<>(s);
                    Collections.sort(firstPart);

                    /*
                     Get the support of the first selected subset
                    */
                    double supportFirstPart;


                        Set<Integer> itemCov = new HashSet<>();
                        for (int i = 0; i < firstPart.size(); i++) {
                            if (i == 0) {
                                itemCov.addAll(mapCoverset.get(firstPart.get(i)));
                            }
                            itemCov.retainAll(mapCoverset.get(firstPart.get(i)));
                        }
                        supportFirstPart = Math.round(itemCov.size() / (transactionCount * 1.0) * 10000) / 10000.0;

                    if ((Math.round(support / (transactionCount * 1.0) * 10000) / 10000.0) == supportFirstPart) {
                        Redundant++;

                    } else {
                        //Itemset is likely non-redundant, do nothing
                    }
                }

                if (Redundant > 0) {
                    //Itemset is redundant, hence no need to check if it is periodic or not
                } else {
                 /*
                 Itemset is non-redundant
                 Compute its periodicity and add to PFPs if periodic
                  */
                    List<Integer> itemCovList = new ArrayList<>(itemCoverset);
                    Collections.sort(itemCovList);

                    //get set of periods from coverset of itemset
                    List<Integer> periodList = getPeriods(itemCovList, transactionCount);
                    //get minimum and maximum periods from set of periods
                    int minPeriod = Collections.min(periodList);
                    int maxPeriod = Collections.max(periodList);

                    //get mean and standard deviation in periods among set of periods
                    double meanPeriod = Math.round(mean(periodList) * 10000) / 10000.0;
                    double stdDevPeriod = Math.round(StandardDev(periodList) * 10000) / 10000.0;

                    //find the period thresholds (highest period threshold and lowest period threshold)
                    double itemLowPeriod = meanPeriod - stdDevPeriod;
                    double itemHighPeriod = meanPeriod + stdDevPeriod;

                    //check if the periodicity of the itemset is within the user specified thresholds
                    if ((lowPeriodThresh <= itemLowPeriod) && (highPeriodThresh >= itemHighPeriod)) {
                        //itemset is periodic

                        // increase the number of periodic frequent itemsets found for statistics purpose
                        itemsetCount++;

                        //add details of pfp to a list
//                        List<Double> details = new ArrayList<>();
//                        details.add(Math.round(support / (1.0 * transactionCount) * 10000) / 10000.0);
//                        details.add((double) minPeriod);
//                        details.add((double) maxPeriod);
//                        details.add(meanPeriod);
//                        details.add(stdDevPeriod);
//
//                        //add itemset to PPFPs
//                        NPFPs.put(SavedItem, details);

                        //begin writing itemset to text file with its details
                		for (int i = 0; i < SavedItem.size(); i++) {
                			buffer.append(SavedItem.get(i));
                			buffer.append(' ');
                		}
                        buffer.append("#SUP: ");
                        buffer.append(support);
                        // buffer.append(" Coverset ");
                        // buffer.append(itemCoverset);
                        // buffer.append("  periods ");
                        // buffer.append(periodList);
                        buffer.append(" #MAXPER: ");
                        buffer.append(Collections.max(periodList));
                        buffer.append(" #MINPER: ");
                        buffer.append(Collections.min(periodList));
                        buffer.append(" #AVGPER: ");
                        buffer.append(mean(periodList));
//                    buffer.append(" stDev ");
//                    buffer.append(StandardDev(periodList));

                        // write to file and create a new line
                        writer.write(buffer.toString());
                        writer.newLine();
                    }
                }

              /*
              Itemset is a length-1 itemset.
              Check if it is redundant or not
               */
            } else if (itemForSubset.size() < 2 && support != transactionCount) {  //if support == transactionCount, and its length-1, it is redundant
                 /*
                 Length one redundant itemset
                 Compute its periodicity and add to PFPs if periodic
                  */
                List<Integer> itemCovList = new ArrayList<>(itemCoverset);
                Collections.sort(itemCovList);

                //get set of periods from coverset
                List<Integer> periodList = getPeriods(itemCovList, transactionCount);

                //get minimum and maximum periods from set of periods
                int minPeriod = Collections.min(periodList);
                int maxPeriod = Collections.max(periodList);

                //get mean and standard deviation in periods among set of periods
                double meanPeriod = Math.round(mean(periodList) * 10000) / 10000.0;
                double stdDevPeriod = Math.round(StandardDev(periodList) * 10000) / 10000.0;

                //find the period thresholds (highest period threshold and lowest period threshold)
                double itemLowPeriod = meanPeriod - stdDevPeriod;
                double itemHighPeriod = meanPeriod + stdDevPeriod;


                //check if the periodicity of the itemset is within the user specified thresholds
                if ((lowPeriodThresh <= itemLowPeriod) && (highPeriodThresh >= itemHighPeriod)) {
                    //itemset is periodic

                    // increase the number of periodic frequent itemsets found for statistics purpose
                    itemsetCount++;

                    //add details of pfp to a list
//                    List<Double> details = new ArrayList<>();
//                    details.add(Math.round(support / (1.0 * transactionCount) * 10000) / 10000.0);
//                    details.add((double) minPeriod);
//                    details.add((double) maxPeriod);
//                    details.add(meanPeriod);
//                    details.add(stdDevPeriod);
//
//                    //add itemset to PPFPs
//                    NPFPs.put(SavedItem, details);

                    //begin writing itemset to text file with its details
            		for (int i = 0; i < SavedItem.size(); i++) {
            			buffer.append(SavedItem.get(i));
            			buffer.append(' ');
            		}
                    buffer.append("#SUP: ");
                    buffer.append(support);
                    // buffer.append(" Coverset ");
                    // buffer.append(itemCoverset);
                    // buffer.append("  periods ");
                    // buffer.append(periodList);
                    buffer.append(" #MAXPER: ");
                    buffer.append(Collections.max(periodList));
                    buffer.append(" #MINPER: ");
                    buffer.append(Collections.min(periodList));
                    buffer.append(" #AVGPER: ");
                    buffer.append(mean(periodList));
//                    buffer.append(" stDev ");
//                    buffer.append(StandardDev(periodList));

                    // write to file and create a new line
                    writer.write(buffer.toString());
                    writer.newLine();
                }

            }


        } else {  // otherwise the result is kept into memory
            // If user does not provide output file name, a default text file will be created as output.txt.
            //codes will be written to take care of storing to memory
        }
    }

    /**
     * Print statistics about the algorithm execution to System.out.
     */
    public void printStats() {
        System.out.println("=============  NPFPM 1.0 - STATS =============");
        long temps = endTime - startTimestamp;
        System.out.println(" Transactions count from database : " + transactionCount);
        System.out.print(" Max memory usage: " + MemoryLogger.getInstance().getMaxMemory() + " mb \n");
        System.out.println(" Non-Redundant Periodic Frequent itemsets count : " + itemsetCount);
        System.out.println(" Total time ~ " + temps + " ms");
        System.out.println("===================================================");
    }

    /**
     * Get the number of transactions in the last transaction database read.
     *
     * @return the number of transactions.
     */
    public int getDatabaseSize() {
        return transactionCount;
    }

    /**
     * This method takes a sorted coverset of an itemset an returns the set of periods for the given itemset
     *
     * @param itemCoverList    an integer list of the coverset (TIDs) of the itemset
     * @param transactionCount the size of the database
     * @return the set of periods for the given coverset
     */
    public List<Integer> getPeriods(List<Integer> itemCoverList, int transactionCount) {
        List<Integer> periods = new ArrayList<>();

        for (int i = 0; i < itemCoverList.size(); i++) {
            if (i == 0) {
                periods.add(itemCoverList.get(i));
            } else if (i == (itemCoverList.size() - 1)) {
                int current = itemCoverList.get(i);
                int lastbutOne = itemCoverList.get(i - 1);
                periods.add(current - lastbutOne);
                periods.add(transactionCount - current);
            } else {
                int current = itemCoverList.get(i);
                int beforeCurrent = itemCoverList.get(i - 1);
                periods.add(current - beforeCurrent);
            }
        }
        return periods;
    }

    /**
     * Set the maximum pattern length
     *
     * @param length the maximum length
     */
    public void setMaximumPatternLength(int length) {
        maxPatternLength = length;
    }

    /**
     * Set the minimum pattern length
     *
     * @param //length the minimum length
     */
    public void setMinimumPatternLength(int minPatternLength) {
        this.minPatternLength = minPatternLength;
    }

    /**
     * This method takes the set of periods of a given itemset and returns the mean period
     *
     * @param itemsetPeriods the set of periods for a given itemset
     * @return the mean period among the set of periods of a given itemset
     */
    public double mean(List<Integer> itemsetPeriods) {
        double tot = 0.0;
        for (int i = 0; i < itemsetPeriods.size(); i++)
            tot += itemsetPeriods.get(i);
        return Math.round(tot / itemsetPeriods.size() * 10000) / 10000.0;
    }


    /**
     * This method takes the set of periods of a given itemset and returns the standard deviation among the set of periods
     *
     * @param itemsetPeriod the set of periods for a given itemset
     * @return the standard deviation among the set of periods of a given itemset
     */
    public double StandardDev(List<Integer> itemsetPeriod) {
        double mu = mean(itemsetPeriod);
        double sumsq = 0.0;
        for (int i = 0; i < itemsetPeriod.size(); i++) {
            sumsq += Math.pow(Math.abs(mu - (double) itemsetPeriod.get(i)), 2);
        }
        return Math.round(Math.sqrt(sumsq / (double) (itemsetPeriod.size())) * 10000) / 10000.0;
    }

    /**
     * This method takes a set and returns all subsets of the given set (including the null set and the set itself)
     * E.g. given (a, b, c), the return will be [{ }, {a}, {b}, {c} {a,b}, {a, c}, {b, c}, {a, b,c}]
     *
     * @param originalSet the original set of e.g
     * @param <T>         Generic type of set can be called on a set of integers, strings, doubles, etc
     * @return all proper subsets of the given set
     */
    public <T> Set<Set<T>> powerSet(Set<T> originalSet) {
        Set<Set<T>> sets = new HashSet<Set<T>>();
        if (originalSet.isEmpty()) {
            sets.add(new HashSet<T>());
            return sets;
        }

        List<T> list = new ArrayList<T>(originalSet);
        T head = list.get(0);
        Set<T> rest = new HashSet<T>(list.subList(1, list.size()));
        for (Set<T> set : powerSet(rest)) {
            Set<T> newSet = new HashSet<T>();
            newSet.add(head);
            newSet.addAll(set);
            sets.add(newSet);
            sets.add(set);
        }
        return sets;
    }

}
