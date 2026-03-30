package ca.pfv.spmf.algorithms.frequentpatterns.emsfui_b;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the EMSFUI_B algorithm.
 * Obtained from Github liuxuan615 under the GPL v3 license
 * as it contains derived code from SPMF, which is under the GPL.
 */
public class AlgoEMSFUI_B {
	
	double maxMemory = 0;     // the maximum memory usage
	long startTimestamp = 0;  // the time the algorithm started
	long endTimestamp = 0;   // the time the algorithm terminated
	int sfupCount =0;  // the number of SFUP generated
	int joinCount =0;  //the number of join operation

	Map<Integer, Integer> mapItemToTWU;
	
	BufferedWriter writer = null;  // writer to write the output file
	
	// this class represent an item and its utility in a transaction
	class Pair{
		int item = 0;
		int utility = 0;
	}
	class ULs{
		List<UtilityList> newPrefixULs;
		List<UtilityList> newExULs;
	}
	
	public AlgoEMSFUI_B() {
	}


	public void runAlgorithm(String input, String output) throws IOException {
		// reset maximum
		maxMemory =0;		
		startTimestamp = System.currentTimeMillis();		
		writer = new BufferedWriter(new FileWriter(output));
		//  We create a  map to store the TWU of each item
		mapItemToTWU = new HashMap<Integer, Integer>();	
		
		// We scan the database a first time to calculate the TWU of each item.
		BufferedReader myInput = null;
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
				// the second part is the transaction utility
				int transactionUtility = Integer.parseInt(split[1]);  
				// for each item, we add the transaction utility to its TWU
				for(int i=0; i <items.length; i++){
					// convert item to integer
					Integer item = Integer.parseInt(items[i]);
					// get the current TWU of that item
					Integer twu = mapItemToTWU.get(item);
					// add the utility of the item in the current transaction to its twu
					twu = (twu == null)? 
							transactionUtility : twu + transactionUtility;
					mapItemToTWU.put(item, twu);
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
				
		// CREATE A LIST TO STORE THE UTILITY LIST OF ITEMS
		List<UtilityList> listOfUtilityLists = new ArrayList<UtilityList>();
		// CREATE A MAP TO STORE THE UTILITY LIST FOR EACH ITEM.
		// Key : item    Value :  utility list associated to that item
		Map<Integer, UtilityList> mapItemToUtilityList = new HashMap<Integer, UtilityList>();
		
		// For each item
		for(Integer item: mapItemToTWU.keySet()){		
			// create an empty Utility List that we will fill later.
			int[] itemset = {item};
			UtilityList uList = new UtilityList(itemset);
			mapItemToUtilityList.put(item, uList);
			// add the item to the list of high TWU items
			listOfUtilityLists.add(uList); 
		}
		
		// SORT THE LIST OF HIGH TWU ITEMS IN ASCENDING ORDER
		Collections.sort(listOfUtilityLists, new Comparator<UtilityList>(){
			public int compare(UtilityList o1, UtilityList o2) {
				// compare the TWU of the items
				return compareItems(o1.itemset[0], o2.itemset[0]);
			}
			} );
		
		// SECOND DATABASE PASS TO CONSTRUCT THE UTILITY LISTS OF ALL 1-ITEMSETS 
		// variable to count the number of transaction
		int tid =0;
		try {
			// prepare object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			
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
				// get the list of utility values corresponding to each item
				// for that transaction
				String utilityValues[] = split[2].split(" ");
				
				// Copy the transaction into lists
				
				int remainingUtility =0;
				
				// Create a list to store items
				List<Pair> revisedTransaction = new ArrayList<Pair>();
				// for each item
				for(int i=0; i <items.length; i++){
					/// convert values to integers
					Pair pair = new Pair();
					pair.item = Integer.parseInt(items[i]);
					pair.utility = Integer.parseInt(utilityValues[i]);
					// add it
					revisedTransaction.add(pair);
					remainingUtility += pair.utility;
				
				}
				
				Collections.sort(revisedTransaction, new Comparator<Pair>(){
					public int compare(Pair o1, Pair o2) {
						return compareItems(o1.item, o2.item);
					}});

								
				// for each item left in the transaction
				for(Pair pair : revisedTransaction){
					// subtract the utility of this item from the remaining utility
					remainingUtility = remainingUtility - pair.utility;
					
					// get the utility list of this item
					UtilityList utilityListOfItem = mapItemToUtilityList.get(pair.item);
					
					// Add a new Element to the utility list of this item corresponding to this transaction
					Element element = new Element(tid, pair.utility, remainingUtility);
					
					utilityListOfItem.addElement(element);
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
		checkMemory();

		// Mine the database recursively
		//This array is used to store the max utility value of each frequency,uEmax[0] is meaningless
		//uEmax[1] stored the max utiliey value of all the itemsets which have frequency equals to 1
		int umax[]=new int[tid+1];
		//The list is used to store the current skyline frequent-utility patterns (SFUPs)
		//psfupList[1]store the psfup has frequent equals to 1
		SkylineList SFUA[] = new SkylineList[tid+1];
			
		//test
		//This method is used to mine all the PSFUPs
		
		B_Mine(null, listOfUtilityLists, SFUA,  umax);
		//This method is used to write out all the PSFUPs
		writeOut(SFUA);
		
		// check the memory usage again and close the file.
		checkMemory();
		// close output file
		writer.close();
		// record end time
		endTimestamp = System.currentTimeMillis();
	}
	
	
	private int compareItems(int item1, int item2) {
		int compare = mapItemToTWU.get(item1) - mapItemToTWU.get(item2);
		// if the same, use the lexical order otherwise use the TWU
		return (compare == 0)? item1 - item2 :  compare;
	}
	
	/**
	 * This is the recursive method to find all potential skyline frequent-utility patterns
	 * @param prefix  This is the current prefix. Initially, it is empty.
	 * @param pUL This is the Utility List of the prefix. Initially, it is empty.
	 * @param ULs The utility lists corresponding to each extension of the prefix.
	 * @param psfupList Current potential skyline frequent-utility patterns.Initially, it is empty.
	 * @param skylineList Current skyline frequent-utility patterns.Initially, it is empty.
	 * @param umax The array of max utility value of each frequency.Initially, it is zero.
	 * @throws IOException
	 */
	private void B_Mine( List<UtilityList> prefixULs, List<UtilityList> exULs, SkylineList SFUA[],  int [] umax)
			throws IOException {		
		scan(exULs, SFUA, umax);
		ULs uls = new ULs();
		uls = extend(prefixULs, exULs,  umax);
		while(uls.newExULs.size()!=0){
			scan(uls.newExULs, SFUA, umax);
			uls =extend(uls.newPrefixULs, uls.newExULs, umax);
        }
	}
	
	//generate the candidate SFUIs
	private void scan(List<UtilityList> ULs, SkylineList SFUA[],  int [] umax) throws IOException{
		//breadth-first search
		for(int i=0;i<ULs.size();i++){
			UtilityList X = ULs.get(i);
			//temp store the frequency of X
			int temp=X.elements.size();	
			//judge whether X is a PSFUP
			//if the utility of X equals to the PSFUP which has same frequency with X, insert X to psfupList
			if(X.sumIutils>=umax[temp]){
				judge( X, SFUA, umax);
			}
		}
	}
	//generate the candidate SFUIs by level
	private ULs extend(List<UtilityList> prefixULs, List<UtilityList> exULs,  int [] umax){
		ULs uls = new ULs();
		uls.newExULs = new ArrayList<UtilityList>();
		uls.newPrefixULs = new ArrayList<UtilityList>();
		for(int i=0;i<exULs.size();i++){
        	UtilityList pX = exULs.get(i);
        	int temp=pX.elements.size();	
        	if(pX.sumIutils + pX.sumRutils >= umax[temp] && umax[temp]!=0){
        		uls.newPrefixULs.add(pX);
				for(int j=i+1; j<exULs.size();j++){
            		UtilityList pY = exULs.get(j);
            		if(pX.prefixIndex!=pY.prefixIndex)
            			break;
            		if(prefixULs!=null){
            			UtilityList pXY = construct(prefixULs.get(pY.prefixIndex), pX, pY,umax);
            			if (pXY!=null) {
            				joinCount++;
            				pXY.prefixIndex = uls.newPrefixULs.size()-1;
                			uls.newExULs.add(pXY);
						}            			
            		}else{
            			UtilityList pXY = construct(null, pX, pY,umax);
            			if(pXY!=null){
            			pXY.prefixIndex = uls.newPrefixULs.size()-1;
            			uls.newExULs.add(pXY);
            			joinCount++;
            		    }
            	    }
        	    }
           }
		}
		return uls;
	}
	
	
	
	/**
	 * Method to judge whether the PSFUP is a SFUP
	 * @param skylineList The skyline frequent-utility itemset list
	 * @param psfupList The potential skyline frequent-utility itemset list
	 * @param umax The max utility value of each frequency
	 * @throws IOException 
	 */
	private void judge(UtilityList X, SkylineList SFUA[], int[] umax) throws IOException {
		//get the support count of X
		int supCount = X.elements.size();
		/*if(X.sumIutils<=uEmax[supCount+1]){
			break;
		}*/
		if(X.sumIutils==umax[supCount]&&umax[supCount]!=0&&umax[supCount]>umax[supCount+1]){
			if(SFUA[supCount]==null){
				SkylineList tempList= new SkylineList();
				Skyline tempPoint=new Skyline();
				tempPoint.itemSet=X.itemset;
				tempPoint.frequent=supCount;
				tempPoint.utility=X.sumIutils;
				tempList.add(tempPoint);
				SFUA[supCount]=tempList;
			}else{
				Skyline Xul = new Skyline();
				Xul.frequent = supCount;
				Xul.utility = X.sumIutils;
				Xul.itemSet=X.itemset;
				SFUA[supCount].add(Xul);
				}
			}
			else if(X.sumIutils>umax[supCount]){
			umax[supCount]=X.sumIutils;
			if(SFUA[supCount]==null){
				SkylineList tempList= new SkylineList();
				Skyline tempPoint=new Skyline();
				tempPoint.itemSet=X.itemset;
				tempPoint.frequent=supCount;
				tempPoint.utility=X.sumIutils;
				tempList.add(tempPoint);
				SFUA[supCount]=tempList;
			}
			else{
				int templength = SFUA[supCount].size();
				if(templength==1){
					SFUA[supCount].get(0).utility=X.sumIutils;
					SFUA[supCount].get(0).itemSet=X.itemset;
				}else if(templength==0){
					Skyline tempPoint=new Skyline();
					tempPoint.itemSet=X.itemset;
					tempPoint.frequent=supCount;
					tempPoint.utility=X.sumIutils;
					SFUA[supCount].add(tempPoint);				
				}else{
					for(int i=SFUA[supCount].size()-1;i>0;i--){
						SFUA[supCount].remove(i);		
					}
					SFUA[supCount].get(0).utility=X.sumIutils;
					SFUA[supCount].get(0).itemSet=X.itemset;
					
				}						
			}		
		}
		for(int i=1;i<supCount;i++){
			if(X.sumIutils>=umax[i]){
				umax[i]=X.sumIutils;
				if(SFUA[i]!=null){
					for(int j=SFUA[i].size()-1; j>=0; j--){
						SFUA[i].remove(j);
					}
				}
			}
		}			
	}
	
	/**
	 * This method constructs the utility list of pXY
	 * @param P :  the utility list of prefix P.
	 * @param px : the utility list of pX
	 * @param py : the utility list of pY
	 * @return the utility list of pXY
	 */
	private UtilityList construct(UtilityList P, UtilityList px, UtilityList py,int[] umax) {
		// create an empy utility list for pXY
		int[] pxy = new int[px.itemset.length+1];
		for(int i=0;i<=pxy.length-2;i++){
			pxy[i]=px.itemset[i];
		}
		pxy[pxy.length-1]=py.itemset[pxy.length-2];
		int totalUtility = px.sumIutils+px.sumRutils;
		int totalSup = px.elements.size();
		UtilityList pxyUL = new UtilityList(pxy);
		// for each element in the utility list of pX
		for(Element ex : px.elements){
			// do a binary search to find element ey in py with tid = ex.tid
			Element ey = findElementWithTID(py, ex.tid);
			if(ey == null){
				//prunning strategy 2
				totalUtility-=(ex.iutils+ex.rutils);
				totalSup = totalSup-1;
				if(totalUtility<umax[totalSup]){
					return null;
				}
				continue;
			}
			// if the prefix p is null
			if(P == null){
				// Create the new element
				Element eXY = new Element(ex.tid, ex.iutils + ey.iutils, ey.rutils);
				// add the new element to the utility list of pXY
				pxyUL.addElement(eXY);
				
			}else{
				// find the element in the utility list of p wih the same tid
				Element e = findElementWithTID(P, ex.tid);
				if(e != null){
					// Create new element
					Element eXY = new Element(ex.tid, ex.iutils + ey.iutils - e.iutils,
								ey.rutils);
					// add the new element to the utility list of pXY
					pxyUL.addElement(eXY);
				}
			}
			
		}
		
		// return the utility list of pXY.
		
		return pxyUL;
	}
	/**
	 * Do a binary search to find the element with a given tid in a utility list
	 * @param ulist the utility list
	 * @param tid  the tid
	 * @return  the element or null if none has the tid.
	 */
	private Element findElementWithTID(UtilityList ulist, int tid){
		List<Element> list = ulist.elements;
		
		// perform a binary search to check if  the subset appears in  level k-1.
        int first = 0;
        int last = list.size() - 1;
       
        // the binary search
        while( first <= last )
        {
        	int middle = ( first + last ) >>> 1; // divide by 2

            if(list.get(middle).tid < tid){
            	first = middle + 1;  //  the itemset compared is larger than the subset according to the lexical order
            }
            else if(list.get(middle).tid > tid){
            	last = middle - 1; //  the itemset compared is smaller than the subset  is smaller according to the lexical order
            }
            else{
            	return list.get(middle);
            }
        }
		return null;
	}

	
	/**
	 * Method to write skyline frequent-utility itemset to the output file.
	 * @param skylineList The list of skyline frequent-utility itemsets 
	 */
	private void writeOut(SkylineList skylineList[]) throws IOException {
		//Create a string buffer
		StringBuilder buffer = new StringBuilder();
		for(int i=1; i<skylineList.length; i++){
			if(skylineList[i]!=null){
				for(int j=0; j<skylineList[i].size(); j++){
//					buffer.append("Total skyline frequent-utility itemset: ");
//					buffer.append(sfupCount);
//					buffer.append(System.lineSeparator());				
//					buffer.append(Arrays.toString(skylineList[i].get(j).itemSet));
//					StringBuffer buffer = new StringBuffer();
					// append each item from the itemset to the stringbuffer, separated by spaces
					for (int item : skylineList[i].get(j).itemSet) {
						buffer.append(item);
						buffer.append(' ');
					}
					buffer.append("#SUP:");
					buffer.append(skylineList[i].get(j).frequent);
					buffer.append(" #UTIL:");
					buffer.append(skylineList[i].get(j).utility);
					buffer.append(System.lineSeparator());
					// write to file
					//the count of SFUIs
					sfupCount=sfupCount+1;
				}
								
			}			
		}
		writer.write(buffer.toString());
	}
	

	/**
	 * Method to check the memory usage and keep the maximum memory usage.
	 */
	private void checkMemory() {
		// get the current memory usage
		double currentMemory = (Runtime.getRuntime().totalMemory() -  Runtime.getRuntime().freeMemory())
				/ 1024d / 1024d;
		// if higher than the maximum until now
		if (currentMemory > maxMemory) {
			// replace the maximum with the current memory usage
			maxMemory = currentMemory;
		}
	}
	
	/**
	 * Print statistics about the latest execution to System.out.
	 */
	public void printStats() {
		System.out.println("=============  EMSFUI_B skyline ALGORITHM v 2.60 - STATS =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + maxMemory+ " MB");
		System.out.println(" Skyline itemsets count : " + sfupCount);
		System.out.println(" join itemsets count : " + joinCount);
		System.out.println("===================================================");
	}
}