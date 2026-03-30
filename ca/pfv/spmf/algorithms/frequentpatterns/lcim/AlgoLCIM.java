package ca.pfv.spmf.algorithms.frequentpatterns.lcim;
/* This file is copyright (c) 2008-2021 Philippe Fournier-Viger
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the "LCIM" algorithm for Cost Efficient Itemset Mining
 * as described in the conference paper : <br/><br/>
 *
 * @see CostList
 * @see ElementT
 * @author Philippe Fournier-Viger, M. Saqib Nawaz
 */
public class AlgoLCIM {
	
	/** the time at which the algorithm started */
	public long startTimestamp = 0;  
	
	/** the time at which the algorithm ended */
	public long endTimestamp = 0; 
	
	/** the number of itemsets generated */
	public int patternCount; 
	
	/** the number of candidate itemsets */
	public int candidateCount;

	/** the number of constructed lists */
	public int constructedListCount;
	
	/** Map to remember the cost of each item (NOTE: only used when using the total order on cost)*/
	Map<Integer, Long> mapItemToCost;
	
	/** Map to remember the lower bound of each item (NOTE: only used when using the total order on cost lower bound)*/
	Map<Integer, Double> mapItemToLowerBound;
	
	/** Map to remember the support of each item */
	Map<Integer, Integer> mapItemToSupport;
	
	/** writer to write the output file  */
	BufferedWriter writer = null;  
	
	/** transaction count */
	Integer[] transactionsToUtility;
	
	/** The Matrix Support structure:  key: item   key: another item   value: support  (only used if optimization is activated)*/
	Map<Integer, Map<Integer, Long>> mapMatrixSupport;  
	
	//////////////////// OPTIONS //////////////////////////////////////////////////////////
	//  Below, there are a few hidden options that can be used to do performance experiments:
	
	/**  Different types of total orders on items that the algorithm can use to search for itemsets*/
	enum OrderType{ascendingSupport,descendingSupport,lexicographicalOrder, ascendingCost, descendingCost, ascendingLowerBound, descendingLowerBound};
	
	// OPTION 1 - THIS IS TO TEST DIFFERENT ORDERS ON ITEMS
	/** The type of ordering that the algorithm will use to search for itemset. You can change this variable to use a different order.*/
	OrderType TOTAL_ORDER = OrderType.ascendingSupport;
	
	// OPTION 2 - STRATEGY TO PRUNE USING THE LOWER BOUND ON THE COST
	/** If true, search space pruning using the lower-bound on the cost will be activated. Otherwise, not. */
	boolean COST_PRUNING = true;	

	// OPTION 3 - STRATEGY TO REDUCE THE NUMBER OF CANDIDATES
	/** If true, activate the Matrix Support Pruning optimization. Otherwise, not.*/
	boolean MATRIX_SUPPORT_PRUNING = true;
	
	/**  Print the size of the support matrix in the statistics about the algorithm execution */
	boolean PRINT_MATRIX_SUPPORT_SIZE = true;
	
	// OPTION 4  - STRATEGY TO REDUCE THE NUMBER OF LISTS THAT ARE CONSTRUCTED
	/** If true, activate the LA PRUNE strategy. Otherwise, not.*/
	boolean ENABLE_LA_PRUNE = true;
	
	/** if true, the debug mode will be activated. Otherwise, not.*/
	boolean DEBUG = false;
	
	/////////////////////////////////////////////////////////////////////////////////////////
	
	/** buffer for storing the current itemset that is mined when performing mining
	* the idea is to always reuse the same buffer to reduce memory usage. */
	final int BUFFERS_SIZE = 200;
	private int[] itemsetBuffer = null;
	
	/** minimum support threshold **/
	int minsupRelative;
	
	/** this class represent an item and its cost in a transaction */
	class Pair{
		int item = 0;
		int cost = 0;
	}
	
	/**
	 * Default constructor
	 */
	public AlgoLCIM() {
		
	}

	/**
	 * Run the algorithm
	 * @param input the input file path
	 * @param output the output file path
	 * @param minUtility the minimum utility threshold
	 * @param minsupp the minsup threshold as a percentage
	 * @throws IOException exception if error while writing the file
	 */
	public void runAlgorithm(String input, String output, double minUtility, double maxcost, double minsupp) throws IOException {
		// reset maximum
		MemoryLogger.getInstance().reset();
		
		// initialize the buffer for storing the current itemset
		itemsetBuffer = new int[BUFFERS_SIZE];
		
		// reset the number of patterns visited by the algorithm
		patternCount =0; 
		
		// reset the number of candidates generated by the algorithm
		candidateCount =0;
		
		// reset the number of lists constructed by the algorithm
		constructedListCount = 0;
		
		// record the start time
		startTimestamp = System.currentTimeMillis();
		
		// Create an object to write result to a file
		writer = new BufferedWriter(new FileWriter(output));

		// We create a map to store the support of each item
		mapItemToSupport  = new HashMap<Integer, Integer>();
		
		// If we will use the ascending or descending order of cost to sort items, we need
		// to create a map to store that information
		if(OrderType.ascendingCost.equals(TOTAL_ORDER) || OrderType.descendingCost.equals(TOTAL_ORDER)) {
			mapItemToCost = new HashMap<Integer, Long>();
		}

		// If we will use the ascending or descending order of lower bound to sort items, we need
				// to create a map to store that information
		if(OrderType.ascendingLowerBound.equals(TOTAL_ORDER) || OrderType.descendingLowerBound.equals(TOTAL_ORDER)) {
			mapItemToLowerBound = new HashMap<Integer, Double>();
		}
		
		// if matrix support pruning is activated
		if(MATRIX_SUPPORT_PRUNING) {
			mapMatrixSupport = new HashMap<Integer, Map<Integer, Long>> ();
		}

		// We scan the database a first time to calculate the support of each item.
		BufferedReader myInput = null;
		int databaseSize = 0;
		String thisLine;
		try {
			// prepare the object for reading the file
			myInput = new BufferedReader(new InputStreamReader( new FileInputStream(new File(input))));
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is  a comment, is  empty or is a
				// kind of metadata
				if (thisLine.isEmpty() == true ||
						thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
								|| thisLine.charAt(0) == '@') {
					continue;
				}
				
				// split the transaction according to the : separator
				String split[] = thisLine.split(":"); 
				// the first part is the list of items
				String items[] = split[0].split(" "); 
				
				// increase the number of transactions
				databaseSize++;
				
				// for each item
				for(int i=0; i <items.length; i++){
					// convert item to integer
					Integer item = Integer.parseInt(items[i]);

					// we also add 1 to the support of the item
					Integer support = mapItemToSupport.get(item);
					support = (support == null)? 1 : support + 1;
					mapItemToSupport.put(item, support);
				}
			}
		} catch (Exception e) {
			// catches exception if error while reading the input file
			e.printStackTrace();
		}finally {
			if(myInput != null){
				myInput.close();
			}
	    }
		
		// convert from an absolute minsup to a relative minsup by multiplying
		// by the database size
		this.minsupRelative = (int) Math.ceil(minsupp * databaseSize);
		
		if(DEBUG) {
			System.out.println("======== PARAMETERS =========");
			System.out.println(" MINUTILITY: "+ minUtility);
			System.out.println(" MAXCOST: "+ maxcost);
			System.out.println(" MINSUP: "+ minsupRelative);
		}
		
		transactionsToUtility = new Integer[databaseSize];
		
		
		// CREATE A MAP TO STORE THE UTILITY LIST FOR EACH ITEM.
		// Key : item    Value :  utility list associated to that item
		Map<Integer, CostList> mapItemToCostList = new HashMap<Integer, CostList>();
		
		// For each item
		for(Integer item: mapItemToSupport.keySet()){
			// if the item is frequent
			if(mapItemToSupport.get(item) >= minsupRelative){
				// create an empty cost List that we will fill later.
				CostList uList = new CostList(item);
				mapItemToCostList.put(item, uList);
				
			}
		}
		
		if(DEBUG) {
			System.out.println("======== MAP ITEM TO SUPPORT =========");
			for(Entry<Integer, Integer> entry : mapItemToSupport.entrySet()) {
				System.out.println(" ITEM " + entry.getKey() + "  support: " + entry.getValue());
			}
		}
		
//		if(DEBUG) {
//			System.out.println("======== MAP ITEM TO UTILITY =========");
//			for(Entry<Integer, Long> entry : mapItemToUtility.entrySet()) {
//				System.out.println(" ITEM " + entry.getKey() + "  utility: " + entry.getValue());
//			}
//		}
		
		
		// SECOND DATABASE PASS TO CONSTRUCT THE COST LISTS 
		try {
			// prepare object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			// variable to count the number of transaction
			int tid =0;
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is  a comment, is  empty or is a
				// kind of metadata
				if (thisLine.isEmpty() == true ||
						thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
								|| thisLine.charAt(0) == '@') {
					continue;
				}
				
				// split the line according to the separator
				String split[] = thisLine.split(":");
				// get the list of items
				String items[] = split[0].split(" ");
				// get the list of cost values corresponding to each item
				// for that transaction
				String costValues[] = split[2].split(" ");

				// the second part is the transaction utility
				transactionsToUtility[tid] = Integer.valueOf(split[1]);  
				
				// Create a list to store items
				List<Pair> revisedTransaction = new ArrayList<Pair>();
				
				// for each item
				for(int i=0; i <items.length; i++){
					int item = Integer.parseInt(items[i]);
					double support = mapItemToSupport.get(item);
					// if the item has enough utility
					if(support >= minsupRelative){

						/// convert values to integers
						Pair pair = new Pair();
						pair.item = Integer.parseInt(items[i]);
						pair.cost = Integer.parseInt(costValues[i]);
						// add it
						revisedTransaction.add(pair);
					}
				}
				
//				// sort the transaction
//				Collections.sort(revisedTransaction, new Comparator<Pair>(){
//					public int compare(Pair o1, Pair o2) {
//						return compareItems(o1.item, o2.item);
//					}});

								
				// for each item left in the transaction
				for(int i = 0; i< revisedTransaction.size(); i++){
					Pair pair =  revisedTransaction.get(i);
					
					// Update the cost list of this item for this transaction
					CostList costListOfItem = mapItemToCostList.get(pair.item);
					costListOfItem.addElement(tid, transactionsToUtility[tid], pair.cost);
										
					// BEGIN MATRIX SUPPORT PRUNING OPTIMIZATION
					if(MATRIX_SUPPORT_PRUNING) {
						Map<Integer, Long> mapFMAPItem = mapMatrixSupport.get(pair.item);
						if(mapFMAPItem == null) {
							mapFMAPItem = new HashMap<Integer, Long>();
							mapMatrixSupport.put(pair.item, mapFMAPItem);
						}
	
						for(int j = 0; j< revisedTransaction.size(); j++){
							if(j!=i) {
								Pair pairAfter = revisedTransaction.get(j);
								Long support = mapFMAPItem.get(pairAfter.item);
								if(support == null) {
									mapFMAPItem.put(pairAfter.item, 1l);
								}else {
									mapFMAPItem.put(pairAfter.item, support + 1l);
								}
							}
						}
					}
					// END MATRIX SUPPORT OPTIMIZATION
				}
				tid++; // increase tid number for next transaction

			}
		} catch (Exception e) {
			// to catch error while reading the input file
			e.printStackTrace();
		}finally {
			if(myInput != null){
				myInput.close();
			}
	    }
		
		// check the memory usage
		MemoryLogger.getInstance().checkMemory();
		
		// CREATE A LIST TO STORE THE COST LIST OF ITEMS 
		List<CostList> promisingCostLists = new ArrayList<CostList>();
		
		for(Entry<Integer, CostList> entry : mapItemToCostList.entrySet()) {
			CostList costList = entry.getValue();
			constructedListCount++;
			candidateCount++;
			
			if(costList.getSupport() >= minsupRelative) {
				if(!COST_PRUNING || costList.getCostLowerBound(minsupRelative) <= maxcost) {
					promisingCostLists.add(costList);
					

					if(OrderType.ascendingCost.equals(TOTAL_ORDER) || OrderType.descendingCost.equals(TOTAL_ORDER)) {
						mapItemToCost.put(entry.getKey(), costList.cost);
					}
					
					if(OrderType.ascendingLowerBound.equals(TOTAL_ORDER) || OrderType.descendingLowerBound.equals(TOTAL_ORDER)) {
						mapItemToLowerBound.put(entry.getKey(), costList.getCostLowerBound(minsupRelative));
					}
				}
			}
		}
		
		// SORT THE LIST OF IN ASCENDING ORDER
		Collections.sort(promisingCostLists, new Comparator<CostList>(){
			public int compare(CostList o1, CostList o2) {
				// compare the items
				return compareItems(o1.item, o2.item);
			}
			} );
		

		if(DEBUG) {
			System.out.println("======== TRANSACTION UTILITY VALUES ====== ");
			for(int i = 0; i < transactionsToUtility.length; i++) {
				System.out.print("  tid: " + i  + " util: " + transactionsToUtility[i]);
			}
			System.out.println();
			
			System.out.println("======== COST LISTS =========");
			for(CostList costList : promisingCostLists) {
				System.out.println(costList.toString());
			}
			
			System.out.println("======== START SEARCH =========");
		}
		

		// Mine the database recursively
		search(itemsetBuffer, 0, promisingCostLists, minUtility, maxcost);
		
		// check the memory usage again and close the file.
		MemoryLogger.getInstance().checkMemory();
		// close output file
		writer.close();
		// record end time
		endTimestamp = System.currentTimeMillis();
	}
	
	/**
	 * Method to compare items by their TWU
	 * @param item1 an item
	 * @param item2 another item
	 * @return 0 if the same item, >0 if item1 is larger than item2,  <0 otherwise
	 */
	private int compareItems(int item1, int item2) {
		
		// if we use the total order of ascending support of items
		if(OrderType.ascendingSupport.equals(TOTAL_ORDER)) {
			int compare = (int)( mapItemToSupport.get(item1) - mapItemToSupport.get(item2));
			return (compare == 0)? item1 - item2 :  compare;
			
		// if we use the total order of descending support of items
		}else if(OrderType.descendingSupport.equals(TOTAL_ORDER)) {
			int compare = (int)( mapItemToSupport.get(item2) - mapItemToSupport.get(item1));
			return (compare == 0)? item1 - item2 :  compare;
		
		// if we use the total order of ascending cost of items
		}else if(OrderType.ascendingCost.equals(TOTAL_ORDER)) {
			int compare = (int)( mapItemToCost.get(item1) - mapItemToCost.get(item2));
			return (compare == 0)? item1 - item2 :  compare;
			
			// if we use the total order of descending support of items
		}else if(OrderType.descendingCost.equals(TOTAL_ORDER)) {
			int compare = (int)( mapItemToCost.get(item2) - mapItemToCost.get(item1));
			return (compare == 0)? item1 - item2 :  compare;
			
		// if we use the total order of ascending lower bound on cost of items
		}else if(OrderType.ascendingLowerBound.equals(TOTAL_ORDER)) {
			int compare = (int)( mapItemToLowerBound.get(item1) - mapItemToLowerBound.get(item2));
			return (compare == 0)? item1 - item2 :  compare;
			
			// if we use the total order of descending lower bound on cost  of items
		}else if(OrderType.descendingLowerBound.equals(TOTAL_ORDER)) {
			int compare = (int)( mapItemToLowerBound.get(item2) - mapItemToLowerBound.get(item1));
			return (compare == 0)? item1 - item2 :  compare;
		
		
		// Otherwise, if we use the lexicographical order
		}else {// if(OrderType.lexicographicalOrder.equals(TOTAL_ORDER)) {
			return item1 - item2;
		}
		
	}
	
	/**
	 * This is the recursive method to find all itemsets. It writes
	 * the itemsets to the output file.
	 * @param prefix  This is the current prefix. Initially, it is empty.
	 * @param ULs The cost lists corresponding to each extension of the prefix.
	 * @param minUtility The minUtility threshold.
	 * @param maxcost the maximum cost threshold.
	 * @param prefixLength The current prefix length
	 * @throws IOException if error writing or reading from file
	 */
	private void search(int [] prefix,
			int prefixLength, List<CostList> ULs, double minUtility, double maxcost)
			throws IOException {
		
		// For each extension X of prefix P
		for(int i=0; i< ULs.size(); i++){
			CostList X = ULs.get(i);

			
			// If itemset X is promising
//			if(X.getCostLowerBound(minsupRelative) <= maxcost){
				

				// If pX is a cost efficient itemset itemset.
				// we save the itemset:  pX 
				if(X.getAverageUtility() >= minUtility  && X.getAverageCost() <= maxcost){
					// save to file
					writeOut(prefix, prefixLength, X);
				}
				
				// This list will contain the utility lists of pX extensions.
				List<CostList> exULs = new ArrayList<CostList>();
				// For each extension of p appearing
				// after X according to the ascending order
				for(int j=i+1; j < ULs.size(); j++){
					CostList Y = ULs.get(j);
					
					// ======================== BEGIN MATRIX SUPPORT OPTIMIZATION
					if(MATRIX_SUPPORT_PRUNING) {
						Map<Integer, Long> mapItemSupport = mapMatrixSupport.get(X.item);
						if(mapItemSupport != null) {
							Long support = mapItemSupport.get(Y.item);
							if(support == null || support < minsupRelative) {
								continue;
							}
						}
					}
					// =========================== END  MATRIX SUPPORT OPTIMIZATION
					candidateCount++;
					
					// we construct the extension pXY 
					// and add it to the list of extensions of pX
					CostList temp = construct(X, Y, minUtility, maxcost);
					if(temp != null 
							&& temp.getSupport() >= minsupRelative ) {
						
						if(!COST_PRUNING || temp.getCostLowerBound(minsupRelative) <= maxcost) {
							exULs.add(temp);
						}
					}
				}
				// We create new prefix pX
				itemsetBuffer[prefixLength] = X.item;
				// We make a recursive call to discover all itemsets with the prefix pXY
				search(itemsetBuffer, prefixLength+1, exULs, minUtility, maxcost); 
			}
//		}
		MemoryLogger.getInstance().checkMemory();
	}
	
	/**
	 * This method constructs the utility list of pXY
	 * @param px : the utility list of pX
	 * @param py : the utility list of pY
	 * @return the utility list of pXY
	 */
	private CostList construct(CostList px, CostList py, double minUtility, double maxcost) {
		// create an empy utility list for pXY
		CostList xyCostList = new CostList(py.item);
		
		//== new optimization - LA-prune  == /
		// Initialize the sum of total utility
		long totalSupport;
		if(ENABLE_LA_PRUNE) {
			int xSize = px.tids.size();
			int ySize = py.tids.size();
			totalSupport = (xSize < ySize)?  xSize : ySize;
		}else {
			totalSupport = 0;
		}
		
		// A similar strategy to LA-prune will be applied for the support
		// Initialize the sum of support
		// ================================================
		
		// for each element in the utility list of pX
		for(int i =0; i< px.tids.size(); i++){
			Integer tid = px.tids.get(i);
			Integer costX = px.costs.get(i);
			
			// do a binary search to find element ey in py with tid = ex.tid
			int positionY = findElementWithTID(py, tid);
			if(positionY == -1){
				//== new optimization - LA-prune == /
				if(ENABLE_LA_PRUNE) {
					// decrease the support by one transaction
					totalSupport -= 1;
					if(totalSupport < minsupRelative) {
						if(DEBUG) {
							System.out.println("----- LA PRUNE APPLIED! -------");
						}
						return null;
					}
				}
				// =============================================== /
				continue;
			}
		
			Integer costY = py.costs.get(positionY);
			int transactionUtility = transactionsToUtility[tid];
			
			xyCostList.addElement(tid, transactionUtility, costX + costY);

		}
		
		constructedListCount++;
		// return the utility list of pXY.
		return xyCostList;
	}
	
	/**
	 * Do a binary search to find the element with a given tid in a cost list
	 * @param ulist the cost list
	 * @param tid  the tid
	 * @return  the position of the tid
	 */
	private Integer findElementWithTID(CostList ulist, int tid){
		List<Integer> list = ulist.tids;
		
		// perform a binary search to check if  the subset appears in  level k-1.
        int first = 0;
        int last = list.size() - 1;
       
        // the binary search
        while( first <= last )
        {
        	int middle = ( first + last ) >>> 1; // divide by 2

            if(list.get(middle) < tid){
            	first = middle + 1;  //  the itemset compared is larger than the subset according to the lexical order
            }
            else if(list.get(middle) > tid){
            	last = middle - 1; //  the itemset compared is smaller than the subset  is smaller according to the lexical order
            }
            else{
            	return middle; //list.get(middle);
            }
        }
		return -1;
	}
	

	/**
	 * Method to write a high utility itemset to the output file.
	 * @param the prefix to be writent o the output file
	 * @param an item to be appended to the prefix
	 * @param utility the utility of the prefix concatenated with the item
	 * @param support the support of the itemset
	 * @param prefixLength the prefix length
	 */
	private void writeOut(int[] prefix, int prefixLength, CostList costList) throws IOException {
		patternCount++; // increase the number of itemset
		
		//Create a string buffer
		StringBuilder buffer = new StringBuilder();
		
		// append the prefix
		for (int i = 0; i < prefixLength; i++) {
			buffer.append(prefix[i]);
			buffer.append(' ');
		}
		// append the last item
		buffer.append(costList.item);
		// append the utility value
		buffer.append(" #AUTIL: ");
		buffer.append(costList.getAverageUtility());
		// append the cost value
		buffer.append(" #ACOST: ");
		buffer.append(costList.getAverageCost());
		// append the support value
		buffer.append(" #SUP: ");
		buffer.append(costList.getSupport());
		// write to file
		writer.write(buffer.toString());
		writer.newLine();
		if(DEBUG) {
			System.out.println("    WRITEOUT: " + buffer.toString());
		}
	}

	
	/**
	 * Print statistics about the latest execution to System.out.
	 * @throws IOException 
	 */
	public void printStats() throws IOException {
		System.out.println("=============  LCIM ALGORITHM v2.50 - STATS =============");
		System.out.println(" Total time ~ "                  + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Max memory ~ "                      + MemoryLogger.getInstance().getMaxMemory()  + " MB");
		System.out.println(" Itemset count: " + patternCount); 

		System.out.println(" Candidate count: "             + candidateCount);
		System.out.println(" Fully constructed cost-list count: "             + constructedListCount);
		
		if(PRINT_MATRIX_SUPPORT_SIZE) {
			int pairCount = 0;
			double maxMemory = getObjectSize(mapItemToCost);
			for(Entry<Integer, Map<Integer, Long>> entry : mapMatrixSupport.entrySet()) {
				maxMemory += getObjectSize(entry.getKey());
				for(Entry<Integer, Long> entry2 :entry.getValue().entrySet()) {
					pairCount++;
					maxMemory += getObjectSize(entry2.getKey()) + getObjectSize(entry2.getValue());
				}
			}
			System.out.println(" --- Matrix for support pruning --");
			System.out.println(" Size: " + maxMemory + " MB");
			System.out.println(" Pair count: " + pairCount);
		}
		System.out.println("===================================================");
	}
	
	
	/**
	 * Get the size of a Java object (for debugging purposes)
	 * @param object the object
	 * @return the size in MB
	 * @throws IOException
	 */
    private double getObjectSize(
            Object object)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(object);
        oos.close();
        double maxMemory = baos.size() / 1024d / 1024d;
        return maxMemory;
    }
}