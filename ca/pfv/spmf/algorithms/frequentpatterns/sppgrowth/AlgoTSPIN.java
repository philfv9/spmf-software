package ca.pfv.spmf.algorithms.frequentpatterns.sppgrowth;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import ca.pfv.spmf.tools.MemoryLogger;

/*
 * Copyright (c) 2019 Peng Yang, Philippe Fournier-Viger et al.

 * This file is part of the SPMF DATA MINING SOFTWARE *
 * (http://www.philippe-fournier-viger.com/spmf).
 * 
 * SPMF is free software: you can redistribute it and/or modify it under the *
 * terms of the GNU General Public License as published by the Free Software *
 * Foundation, either version 3 of the License, or (at your option) any later *
 * version. SPMF is distributed in the hope that it will be useful, but WITHOUT
 * ANY * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * This is an implementation of the TPSIN algorithm presented in this paper:
 * <br/><br/>
 * Fournier-Viger, P., Wang Y., Yang, P., Lin, J. C.-W., Yun, U. (2021). TSPIN: Mining Top-k Stable Periodic Patterns. Applied Intelligence,
 * 
 * @see SPPTree
 * @see SPPNode
 * @see Support_maxla
 */

public class AlgoTSPIN {
	
	/**  start time of the latest execution */
    private long startTimestamp; 
    /** end time of the latest execution */
    private long endTimestamp; 

    /** largest TID in the database */
    private int lastTID = -1; 
    
    /** number of freq. itemsets found */
    private int itemsetCount; 

    /** object to write the output file */
    BufferedWriter writer = null; 

    /** The  patterns that are found (if the user wants to keep them into memory) */
    protected Itemsets patterns = null;

    /** This variable is used to determine the size of buffers to store itemsets.
    // A value of 50 is enough because it allows up to 2^50 patterns! */
    final int BUFFERS_SIZE = 2000;

    /** buffer for storing the current itemset that is mined when performing mining
    // the idea is to always reuse the same buffer to reduce memory usage. */
    private int[] itemsetBuffer = null;

    /** This buffer is used to store an itemset that will be written to file
    // so that the algorithm can sort the itemset before it is output to file
    // (when the user choose to output result to file). */
    private int[] itemsetOutputBuffer = null;

    /** maximum pattern length */
    private int maxPatternLength = 1000;

    /** whether the timestamps need self increment as step of 1 for each transcation
     * or timestamps is provided in the input file
    // default as true */
    private boolean self_increment;

    /** the minimum duration threshold. */
    private int minSup;

    /** the maximum periodicity threshold. */
    private int maxPer;

    /** the maxLa */
    private int maxla;
    
    /** the k */
    private int k;
	
	/** the top k rules found until now */
	PriorityQueue<Itemset> kItemsets;
	
	private boolean usePlus = true;
	
	/**
     * Constructor
     */
	public AlgoTSPIN(int maxPer, int maxla, int k, boolean self_increment) {
		
		this.maxPer = maxPer;
		this.maxla = maxla;
		this.k = k;
		this.self_increment = self_increment;
		
	}
	
	/**
     * Method to run the FPGRowth algorithm.
     * @param input the path to an input file containing a transaction database.
     * @param output the output file path for saving the result (if null, the result
     *        will be returned by the method instead of being saved).
     * @return the result if no output file path is provided.
     * @throws IOException exception if error reading or writing files
     */	
	public Itemsets runAlgorithm(String input, String output) throws FileNotFoundException, IOException{
		
		// record the start time
		startTimestamp = System.currentTimeMillis();
		
		// the number of itemsets found
		itemsetCount = 0;
		
		// initialize tool to record memory usage
		MemoryLogger.getInstance().reset();
		MemoryLogger.getInstance().checkMemory();
		
		this.minSup = 1;
		//this.k = k;
		
		this.kItemsets = new PriorityQueue<Itemset>();
		
		// if the user wants to keep the result into memory
		if (output == null) {
			writer = null;
			patterns = new Itemsets(("Top "+k+" Stable periodic frequent itemsets"));
		} else {
			patterns = null;
			writer = new BufferedWriter(new FileWriter(output));
			itemsetOutputBuffer = new int[BUFFERS_SIZE];
			
		}
		
		// (1) PREPROCESSING: Initial database scan to determine the maxla of each item
        // The TID is stored in a map:
        //    key: item   value: maxla
		final Map<Integer, Support_maxla> mapSPP_list = scanDatabaseToDetermineSPPlistOfSingleItems(input);
		
		// (2) Scan the database again to build the initial SPP-Tree
        // Before inserting a transaction in the SPPTree, we sort the items
        // by descending order of item's support.
		SPPTree tree = new SPPTree();

        buildTreeByScanDataAgain(tree, input, mapSPP_list);
        System.out.println("# of node : "+tree.numberOfNode);
        
     // (3) We start to mine the SPP-Tree by calling the recursive method.
        // Initially, the prefix alpha is empty.
        // if at least an item has periodic frequent time-interval
        if (tree.headerList.size() > 0) {
        	
            // initialize the buffer for storing the current itemset
            itemsetBuffer = new int[BUFFERS_SIZE];

            // recursively generate the itemsets that have periodic frequent time-interval  using the SPP-tree
            // Note: we assume that the initial SPP-Tree has more than one path
            // which should generally be the case.
        	TSPIN(tree, itemsetBuffer, 0, mapSPP_list);
			
		}
        

        // close the output file if the result was saved to a file
        writeResultToFile(output);
        
        if (writer != null) {
			
        	writer.close();
		}
        
        // record the execution end time
        endTimestamp = System.currentTimeMillis();
        
        // check the memory usage
        MemoryLogger.getInstance().checkMemory();
        
        // return the result (if saved to memory)
        return patterns;
		
	}
	
	
	private Map<Integer, Support_maxla> scanDatabaseToDetermineSPPlistOfSingleItems(String input) throws IOException {
        // read file
        BufferedReader reader = new BufferedReader(new FileReader(input));
        String line;

        // The maxla is stored in a map:
        //    key: item   value: maxla
        Map<Integer, Support_maxla> mapSPP_list = new HashMap<>();

        // this save the previous timestamp of item
        //    key:   item ,     value: previous TID
        Map<Integer,Integer> preTID = new HashMap<>();

        // this save the current lability of a item
        //   key:   item ,     value: i-th lability
        Map<Integer,Integer> prela = new HashMap<>();

        Set<Integer> pruningSet = new HashSet<>();

        if(self_increment) { // the timestamp is self-increment
            int current_TID = 1;
            while (((line = reader.readLine()) != null)) {
                // if the line is  a comment, is  empty or is a
                // kind of metadata
                if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%'
                        || line.charAt(0) == '@') {
                    continue;
                }
                String[] lineSplited = line.split(" ");

                for (String itemString : lineSplited) {
                    Integer item_name = Integer.parseInt(itemString);
                    if(usePlus && pruningSet.contains(item_name)){
                        continue;
                    }

                    int pre_TID = preTID.getOrDefault(item_name, 0);
                    // calculate the periodicity
                    int per = current_TID - pre_TID;
                    // if a transaction has same item
                    if (per == 0) continue;

//                    // for test
//                    if (item_name == 449)
//                        testRes.add(per);
                    
                    int current_la = Math.max(0, prela.getOrDefault(item_name, 0) + per - maxPer);
                    if(usePlus && current_la > maxla){
                        if(mapSPP_list.containsKey(item_name)){
                            mapSPP_list.remove(item_name);
                        }
                        continue;
                    }

                    if (!mapSPP_list.containsKey(item_name)) mapSPP_list.put(item_name, new Support_maxla());

                    mapSPP_list.get(item_name).setMaxla(current_la);
                    
                    prela.put(item_name,current_la);
                    preTID.put(item_name,current_TID);
                    mapSPP_list.get(item_name).increaseSupport();
                }
                current_TID++;
            }
            lastTID = current_TID - 1;

        }else {  //// the timestamp exist in file
            int current_TID=1;
            while( ((line = reader.readLine())!= null)) {
                if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
                    continue;
                }

                String[] lineSplited = line.split("\\|");
                String[] lineItems = lineSplited[0].split(" ");
                System.out.print(lineSplited[1]);
                current_TID = Integer.parseInt(lineSplited[1]);
                for (String itemString : lineItems) {
                    Integer item_name = Integer.parseInt(itemString);


                    int pre_TID = preTID.getOrDefault(item_name, 0);
                    // calculate the periodicity
                    int per = current_TID - pre_TID;
                    // if a transaction has same item
                    if (per == 0) continue;



                    int current_la = Math.max(0, prela.getOrDefault(item_name, 0) + per - maxPer);

                    if(usePlus && current_la > maxla){
                        if(mapSPP_list.containsKey(item_name)){
                            mapSPP_list.remove(item_name);
                        }
                        continue;
                    }

                    if(!mapSPP_list.containsKey(item_name)) mapSPP_list.put(item_name,new Support_maxla());
                    mapSPP_list.get(item_name).setMaxla(current_la);

                    prela.put(item_name,current_la);
                    preTID.put(item_name,current_TID);
                    mapSPP_list.get(item_name).increaseSupport();
                }
            }
            lastTID = current_TID;
        }
        // close the input file
        reader.close();

        // Deal with the last TID

        Iterator<Map.Entry<Integer, Support_maxla>> it = mapSPP_list.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Support_maxla> entry = it.next();
            int item_name = entry.getKey();

//            // for test
//            if (item_name == 449)
//                testRes.add(lastTID-preTID.get(item_name));

            entry.getValue().setMaxla(prela.get(item_name)+lastTID - preTID.get(item_name) - maxPer);

            //  the item has not periodic frequent time-interval
            if(entry.getValue().getSupport() < minSup || entry.getValue().getMaxla() > maxla){
                // remove it.
                it.remove();
            }
        }
        prela.clear();
        preTID.clear();

        return mapSPP_list;
    }
	
	private void buildTreeByScanDataAgain(SPPTree tree, String input, Map<Integer, Support_maxla> mapSPP_list) throws IOException {
        // read file
        BufferedReader reader = new BufferedReader(new FileReader(input));

        String line;

        if(self_increment) { // the timestamp is self-increment

            int current_TID = 1;
            while (((line = reader.readLine()) != null)) {
                // if the line is  a comment, is  empty or is a
                // kind of metadata
                if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%'
                        || line.charAt(0) == '@') {
                    continue;
                }
                String[] lineSplited = line.split(" ");

                List<Integer> transaction = new ArrayList<Integer>();

                for (String itemString : lineSplited) {
                    Integer item_name = Integer.parseInt(itemString);

                    // only the item is SPP
                    // and the current timestamp in its time-interval
                    // then this item can be added to the transaction (tree) .
                    if(mapSPP_list.containsKey(item_name) && !transaction.contains(item_name)){
                        transaction.add(item_name);
                    }
                }
                // sort item in the transaction by descending order of total duration
                Collections.sort(transaction, new Comparator<Integer>(){
                    public int compare(Integer item1, Integer item2){
                         //compare the support
                        int compare = mapSPP_list.get(item2).getSupport() -  mapSPP_list.get(item1).getSupport();
                        // if the same support, we check the lexical ordering!
                        if(compare == 0){
                            return (item1 - item2);
                        }
                        // otherwise, just use the total duration
                        return compare;
//                        return item1- item2;
                    }
                });

                // add the sorted transaction and current timestamp into tree.
                if(transaction.size()>0){
                    tree.addTransaction(transaction,current_TID);
                }
                // self increment
                current_TID++;
            }

        }else {  //// the timestamp exist in file

            int current_TID = 1;

            while (((line = reader.readLine()) != null)) {
                if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
                    continue;
                }

                String[] lineSplited = line.trim().split("\\|");
                String[] lineItems = lineSplited[0].trim().split(" ");

                current_TID = Integer.parseInt(lineSplited[1]);

                List<Integer> transaction = new ArrayList<Integer>();

                for (String itemString : lineItems) {
                    Integer item_name = Integer.parseInt(itemString);

                    // only the item has periodic frequent time-interval
                    // and the current timestamp in its time-interval
                    // then this item can be added to the transaction (tree) .
                    if(mapSPP_list.containsKey(item_name) && !transaction.contains(item_name)){
                        transaction.add(item_name);
                    }

                }
                // sort item in the transaction by descending order of total duration
                Collections.sort(transaction, new Comparator<Integer>(){
                    public int compare(Integer item1, Integer item2){

                        // compare the support
                        int compare = mapSPP_list.get(item2).getSupport() - mapSPP_list.get(item1).getSupport();
                        // if the same support, we check the lexical ordering!
                        if(compare == 0){
                            return (item1 - item2);
                        }
                        // otherwise, just use the total duration
                        return compare;
//                        return item1 -item2;
                    }
                });
                // add the sorted transaction and current timestamp into tree.
                if(transaction.size()>0){
                    tree.addTransaction(transaction,current_TID);
                }
            }
        }

        // close the input file
        reader.close();

        // We create the header table for the tree using the calculated support of single items
        tree.createHeaderList(null,mapSPP_list);

    }
	
	
	private void TSPIN(SPPTree tree, int[] prefix, int prefixLength, Map<Integer, Support_maxla> mapSPP_list) throws IOException{
		
		if (prefixLength == maxPatternLength) {
			return;
		}
		
		// for each item in the header table list of the tree in reverse order.
		while (tree.headerList.size() > 0) {
			
			// get the tail item
			Integer item = tree.headerList.get(tree.headerList.size() - 1);
			
			// create beta by concatenating prefix alpha by adding the current item to alpha
			prefix[prefixLength] = item;
						
			// save beta to the output file
			saveItemset(prefix, prefixLength+1, mapSPP_list.get(item).getSupport(), mapSPP_list.get(item).getMaxla());
			
			if (prefixLength+1 < maxPatternLength) {
				
				// === (A) Construct beta's prefix tree ===
                // It is a subdatabase which consists of the set of prefix paths
                // in the SPP-tree co-occuring with the prefix pattern.
				List<List<SPPNode>> prefixPaths = new ArrayList<List<SPPNode>>();
				
				SPPNode path = tree.mapItemNodes.get(item);
				
				// map to count the TIDs of items in the conditional prefix tree
				// key: item  value: TIDs
				Map<Integer, List<Integer>>  mapBetaTIDs = new HashMap<Integer, List<Integer>>();
				
				while(path != null) {
                    // if the path is not just the root node
                    if (path.parent.itemID != -1) {
                        // create the prefixpath
                        List<SPPNode> prefixPath = new ArrayList<SPPNode>();
                        // add this node.
                        prefixPath.add(path);   // NOTE: we add it just to keep its TID,
                        // actually it should not be part of the prefixPath

                        List<Integer> pathTIDs = path.TIDs;

                        //Recursively add all the parents of this node.
                        SPPNode parent = path.parent;

                        while (parent.itemID != -1) {
                            prefixPath.add(parent);

                            // FOR EACH PATTERN WE ALSO UPDATE THE ITEM TIMESTAMPS AT THE SAME TIME
                            // if the first time we see that node id
                            if (mapBetaTIDs.get(parent.itemID) == null) {
                                // just add the path timestamps
                                mapBetaTIDs.put(parent.itemID, new ArrayList<Integer>(){{addAll(pathTIDs);}});
                            } else {
                                // otherwise, add all of timestamps to map
                                mapBetaTIDs.get(parent.itemID).addAll(pathTIDs);
                            }
                            parent = parent.parent;
                        }
                        // add the path to the list of prefixpaths
                        prefixPaths.add(prefixPath);
                    }
                    // We will look for the next prefixpath
                    path = path.nodeLink;
                }
				
				// convert beta's timestamps to support and maxla
                Map<Integer, Support_maxla> mapBetaSPPlist = getMapBetaSPPlist(mapBetaTIDs);

                // header table has SPP
                if(mapBetaSPPlist.size()>0) {
                    // (B) Construct beta's conditional SPPTree
                    // Create the tree.
                    SPPTree treeBeta = new SPPTree();
                    // Add each prefixpath in the SPPTree.
                    for (List<SPPNode> prefixPath : prefixPaths) {
                        treeBeta.addPrefixPath(prefixPath, mapBetaSPPlist);
                    }

                    // Mine recursively the Beta tree if the root has child(s)
                    if (treeBeta.root.childs.size() > 0) {

                        // Create the header list.
                        treeBeta.createHeaderList(tree.headerList,mapBetaSPPlist);
                        // recursive call
                        TSPIN(treeBeta, prefix, prefixLength + 1, mapBetaSPPlist);
                    }
                }
                // refreshing SPP-Tree by removing the tail item
                // the timestamps of tail item should be moved to its parent.
                tree.removeTailItem();
            }
        }

        // check the memory usage
        MemoryLogger.getInstance().checkMemory();
		
		
	}
	

	private void saveItemset(int[] itemset, int itemsetLength, int support, int maxla) throws IOException{
		// increase the number of itemsets found for statistics purpose
		itemsetCount++;
		
		System.arraycopy(itemset, 0, itemsetOutputBuffer, 0, itemsetLength);
		Arrays.sort(itemsetOutputBuffer, 0, itemsetLength);
		
		// update the kItemsets and current support
		List<Integer> testItem = new ArrayList<>();
		for (int i = 0; i < itemsetLength; i++) {
			
			testItem.add(itemsetOutputBuffer[i]);
			
		}
		
		Itemset testItemset = new Itemset(testItem, support, maxla);
		
		kItemsets.add(testItemset);
		
		if (kItemsets.size() > k) {
			
			if (support > this.minSup) {
							
				do {
					
					kItemsets.poll();
					
				} while (kItemsets.size() > k);
				
				this.minSup = kItemsets.peek().getAbsoluteSupport();
			}
		}
		
	}
	
	
	/**
     *    convert beta's timestamps to time-intervals
     * @param mapBetaTIDs
     * @return
     */
	private Map<Integer, Support_maxla> getMapBetaSPPlist(Map<Integer, List<Integer>> mapBetaTIDs){

        Map<Integer, Support_maxla> mapBetaSPPlist = new HashMap<>();


        loop1: for(Map.Entry<Integer,List<Integer>> entry:mapBetaTIDs.entrySet()) {

            Support_maxla sm = new Support_maxla();
            List<Integer> TIDs = entry.getValue();
            // 1,sort the timestamps
            Collections.sort(TIDs);

            // 2.scan the timestamps
            int pre_TID = 0;
            int pre_la = 0;
            for (int current_TID : TIDs) {

                int current_la = Math.max(0,pre_la+current_TID-pre_TID-maxPer);
                if(usePlus && current_la > maxla){
                    continue loop1;
                }
                sm.setMaxla(current_la);
                sm.increaseSupport();

                pre_TID = current_TID;
                pre_la = current_la;
            }

            // 3. Deal with the last timestamp

            int current_la = Math.max(0,pre_la+lastTID-pre_TID-maxPer);

            sm.setMaxla(current_la);

            // 4. save time-interval
            if(sm.getSupport() >= minSup && sm.getMaxla() <= maxla){
                mapBetaSPPlist.put(entry.getKey(),sm);
            }
        }
        // clear the memory of mapTimestampsBeta
        mapBetaTIDs.clear();

        return mapBetaSPPlist;
    }
	
	
	/**
	 *  write the result to a file
	 */
	
	public void writeResultToFile(String output) throws IOException{
		
		Iterator<Itemset> iter = kItemsets.iterator();
		while(iter.hasNext()) {
			
			StringBuffer buffer = new StringBuffer();
			Itemset itemset = (Itemset) iter.next();
			
			for(int i=0; i<itemset.itemset.length;i++) {
				
				buffer.append(itemset.getItems()[i]);
				
				if (i != itemset.itemset.length-1) {
					buffer.append(' ');
				}
			
			}
			
			buffer.append("  #SUP: ");
			buffer.append(itemset.support);
			
			buffer.append("  #MAXLA: ");
			buffer.append(itemset.maxla);
			
			
			writer.write(buffer.toString());
			if (iter.hasNext()) {
				writer.newLine();
			}
			
		}
		
		writer.close();
	}
	
	/**
	 *  print statistics about the algorithm execution to System.out
	 */
	
	public void printStats() {
		
		System.out.println("=============  TSPIN  - STATS ===============");
        long temps = endTimestamp - startTimestamp;
        System.out.print(" Max memory usage: " + MemoryLogger.getInstance().getMaxMemory() + " mb \n");
        System.out.println(" Itemset counts : " + this.itemsetCount);
        System.out.println(" k itemset count: " + kItemsets.size());
        System.out.println(" Total time ~ " + temps + " ms");
        System.out.println("minSup: "+this.minSup);
        System.out.println("===================================================");
		
	}
	
	/**
     * Set the maximum pattern length
     * @param length the maximum length
     */
    public void setMaximumPatternLength(int length) {
        maxPatternLength = length;
    }

    public void cancelSelfIncrement(){
        this.self_increment = false;
    }
	
}
