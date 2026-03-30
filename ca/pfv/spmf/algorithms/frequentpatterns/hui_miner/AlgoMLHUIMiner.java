package ca.pfv.spmf.algorithms.frequentpatterns.hui_miner;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ca.pfv.spmf.tools.MemoryLogger;



/**
 * This is an implementation of MLHUI-Miner, which was proposed in this paper:
 * 
 * 
 * 
 * Note that this implementation is an alternative implementation (not the one used in the paper).
 * It has the following differences:
 * -  this implementation does not compute the utility unit arrays for recovery of
 * all itemsets. 
 * - this implementation adds the EUCP strategy from the FHM algorithm to improve the performance
 *   of CHUI-Miner.
 * 
 * @see UtilityList
 * @see Element
 * @author Philippe Fournier-Viger 2014
 */
	public class AlgoMLHUIMiner {
		/** the time at which the algorithm started */
		public long startTimeStamp = 0;
		
		/** the time at which the algorithm ended */
		public long endTimeStamp = 0;
		
		/** the number of cross-level high utility itemsets generated */
		public int huiCount = 0;
		
		/** Map to remember the taxonomy information of each item */
		Map<Integer,Integer> mapItemToGeneralizedItem;
		
		/** Map to remember the GWU of each item */
		Map<Integer, Double> mapItemToGWU;
		
		/** Map to remember the level of each item */
		Map<Integer, Integer> mapItemToLevel;
		
		/** Map to remember the tax-utility-list for all items */
		Map<Integer, UtilityListMLHUIMiner> mapItemToUtilityListMLHUIMiner;
		
		/** Map to remember the tax-utility-list for all itemsets */
		Map<List<Integer>, UtilityListMLHUIMiner> mapItemsetToUtilityListMLHUIMiner;
		
		/** Map to remember all the ancestors of the item */
		Map<Integer, List<Integer>> mapItemToAncestor;
		
		final int BUFFERS_SIZE = 500;
		private int[] itemsetBuffer = null;
		
		/**Array to remember the transaction utility of each transaction */
		double[] transactionTU;
		
		List<List<Integer>> storeResult = new ArrayList<>();
		
		BufferedWriter writer = null;
		
		class Pair {
			int item = 0;
			double utility = 0;
		}
		
		/**
		 * Constructor
		 */
		public AlgoMLHUIMiner() {
			
		}
		
		/**
		 * Run the algorithm
		 * @param inputTransactions
		 * @param inputTaxonomy
		 * @param output
		 * @param min_utility
		 * @throws IOException
		 */
		public void runAlgorithm(String inputTransactions, String inputTaxonomy, String output, double min_utility) throws IOException {
			
			// reset maximum
			MemoryLogger.getInstance().reset();
			
			// record the start time
			startTimeStamp = System.currentTimeMillis();
			
			writer = new BufferedWriter(new FileWriter(output));
			
			// we create a map to store the taxonomy information to avoid scanning the database repeatedly
			mapItemToGeneralizedItem = new LinkedHashMap<Integer, Integer>();
			
			// we create a map to store the GWU of each item
			mapItemToGWU = new HashMap<Integer, Double>();
			
			// we create a map to store the level of each item
			mapItemToLevel = new HashMap<Integer, Integer>();
			
			// we create a map to store the tax-utility-list of each itemset
			mapItemsetToUtilityListMLHUIMiner = new HashMap<List<Integer>, UtilityListMLHUIMiner>();
			
			// we create a map to store all the ancestors of each item
			mapItemToAncestor = new HashMap<Integer, List<Integer>>();
			
			// we scan the database the first time to calculate the GWU of each item and generalized item		
			BufferedReader myInputTaxnomy = null;
			String thisLineTaxonomy;
			
			try {
				// prepare the object for reading the taxonomy file
				myInputTaxnomy = new BufferedReader(new InputStreamReader(new FileInputStream(new File(inputTaxonomy))));
				
				// for each line (is-a relationship) until the end of file
				while((thisLineTaxonomy = myInputTaxnomy.readLine()) != null) {
					
					// if the line is a comment, is empty or is a kind of metadata
					if (thisLineTaxonomy.isEmpty() == true || thisLineTaxonomy.charAt(0) == '#' || thisLineTaxonomy.charAt(0) == '@') {
						continue;
					}
					
					// split the line according to the , separator
					String splitTaxonomy[] = thisLineTaxonomy.split(",");
					
					// store the relationship in the map to avoid scanning the taxonomy file repeatedly
					mapItemToGeneralizedItem.put(Integer.parseInt(splitTaxonomy[0]), Integer.parseInt(splitTaxonomy[1]));
				}
				
			} catch(Exception e) {
				e.printStackTrace();
			} finally {
				if (myInputTaxnomy != null) {
					myInputTaxnomy.close();
				}
				// TODO: handle finally clause
			}
			
			
			BufferedReader myInputTransaction = null;
			String thisLineTransaction;
			int tidCount = 0;
			
			try {
				
				// prepare the object for reading the transaction file
				myInputTransaction = new BufferedReader(new InputStreamReader(new FileInputStream(new File(inputTransactions))));
				
				// for each line (transaction) until the end of file
				while((thisLineTransaction = myInputTransaction.readLine()) != null) {
					
					// if the line is a comment, is empty or is a kind of metadata
					if(thisLineTransaction.isEmpty() == true || thisLineTransaction.charAt(0) == '#' || thisLineTransaction.charAt(0) == '@') {
						continue;
					}
					
					// split the transaction according to the : separator
					String split[] = thisLineTransaction.split(":");
					
					// the first part is the list of items
					String items[] = split[0].split(" ");
					
					ArrayList<Integer> ancestantExist = new ArrayList<Integer>();
					
					//the second part is the transaction utility
					double transactionUtility = Double.parseDouble(split[1]);
					
					for(int i=0; i<items.length; i++) {
						
						// convert the item to integer
						Integer item = Integer.parseInt(items[i]);
						
						// get the current GWU of that item
						Double gwu = mapItemToGWU.get(item);
						
						// add the utility of the item in the current transaction to its GWU
						gwu = (gwu == null) ? transactionUtility : gwu + transactionUtility;
						
						// create a list to remember the ancestor of the item
						ArrayList<Integer> ancestor = new ArrayList<Integer>();
						
						ancestor.add(item);
						
						// remember the GWU of each item
						mapItemToGWU.put(item, gwu);
						
						// if the map does not hold the key for the item, we create it
						if (mapItemToAncestor.get(item) == null) {
							
							Integer itemCopy = item;
							
							// for each key-value
							for (Map.Entry<Integer, Integer> entry : mapItemToGeneralizedItem.entrySet()) { 
								 
								Integer childItem = entry.getKey();
								Integer parentItem = entry.getValue();
								
								// if the key equals the itemCopy
								if(childItem.equals(itemCopy)) {
									
									// add it to the ancestor list
									ancestor.add(parentItem);
									
									// if the transaction utility of current transaction has not been calculated
									if(!ancestantExist.contains(parentItem)) {
										
										ancestantExist.add(parentItem);
										
										// get the current GWU of the generalized item
										Double twuParent = mapItemToGWU.get(parentItem);
										
										// add the utility of the current transaction to its GWU
										twuParent = (twuParent == null) ? transactionUtility : twuParent + transactionUtility;
										
										// store the GWU of generalized item
										mapItemToGWU.put(parentItem, twuParent);
									}
									
									itemCopy = parentItem;
								}
							}
							
							
							// get the current size of the ancestor list
							int k = ancestor.size();
							
							
							// remember the level of each item
							for(int j=0; j<ancestor.size(); j++, k--) {
								mapItemToLevel.put(ancestor.get(j), k);
							}
							
							for(int itemKey=0;itemKey<ancestor.size();itemKey++) {
								List<Integer> itemValue= new ArrayList<>();
								
								for(int listValue = itemKey+1;listValue<ancestor.size();listValue++) {
									itemValue.add(ancestor.get(listValue));
								}
								
								// store the ancestor of the current item
								mapItemToAncestor.put(ancestor.get(itemKey), itemValue);
							}
							
						} else {
							
							// get the current ancestor of the item and update the GWU
							List<Integer> listAncestorOfItem = mapItemToAncestor.get(item);
							
							for(int k=0;k<listAncestorOfItem.size();k++) {
								
								if(!ancestantExist.contains(listAncestorOfItem.get(k))) {
									
									ancestantExist.add(listAncestorOfItem.get(k));
									
									Double twuParent = mapItemToGWU.get(listAncestorOfItem.get(k));
									
									twuParent = (twuParent == null) ? transactionUtility : twuParent + transactionUtility;
									
									mapItemToGWU.put(listAncestorOfItem.get(k), twuParent);
								}
							}
						}
						
					}
					
					// increase tid number for next transaction
					tidCount++;
				}
				
				
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if(myInputTransaction != null) {
					myInputTransaction.close();
				}
			}
			
			// create a list to store the tax-utility-list of items with gwu >= min_utility
			List<UtilityListMLHUIMiner> listOfTaxUtilityListMLHUIMiner = new ArrayList<UtilityListMLHUIMiner>();
			
			// create a map to store the tax-utility-list for each item
			mapItemToUtilityListMLHUIMiner = new HashMap<Integer, UtilityListMLHUIMiner>();
			
			// for each item
			for(Entry<Integer, Double> entry: mapItemToGWU.entrySet()) {
				Integer item = entry.getKey();
				
				// if the item is promising (GWU >= min_utility)
				if(mapItemToGWU.get(item) >= min_utility) {			
					
					List<Integer> itemList = new ArrayList<Integer>();
					
					itemList.add(item);
					
					// create an empty tuList that we will fill later
					UtilityListMLHUIMiner tuList = new UtilityListMLHUIMiner(itemList);
					
					mapItemToUtilityListMLHUIMiner.put(item, tuList);
					
					// add the item to the list of high GWU items
					listOfTaxUtilityListMLHUIMiner.add(tuList);
					
				} else {
					
					// if the generalized item is promising, add the item to the list of high GWU items also
					List<Integer> listAncestorOfItem= mapItemToAncestor.get(item);
					for(int k=0;k<listAncestorOfItem.size();k++) {
						if(mapItemToGWU.get(listAncestorOfItem.get(k))>=min_utility) {
							List<Integer> itemList = new ArrayList<Integer>();
							
							itemList.add(item);
							
							UtilityListMLHUIMiner tuList = new UtilityListMLHUIMiner(itemList);
													
							// store the tax-utility-lists of 1-itemset
							mapItemToUtilityListMLHUIMiner.put(item, tuList);
							
							listOfTaxUtilityListMLHUIMiner.add(tuList);
							break;
						}
					}
					
				}
			}
			
				// sort the list of high GWU items in ascending order
				Collections.sort(listOfTaxUtilityListMLHUIMiner, new Comparator<UtilityListMLHUIMiner>() {
				public int compare(UtilityListMLHUIMiner o1, UtilityListMLHUIMiner o2) {
					
					// compare the GWU of the items
					return compareItems(o1.item.get(0), o2.item.get(0));
				}
			});
			
			//System.out.println("first database scan......");
			
			//second database scan to construct the tax-utility-lists of items/generalized items
			transactionTU = new double[tidCount];
			try {
				// prepare object for reading the transaction file
				myInputTransaction = new BufferedReader(new InputStreamReader(new FileInputStream(new File(inputTransactions))));
				
				// variable to count the number of transaction
				int tid = 0;
				
				// for each line (transaction) until the end of file
				while((thisLineTransaction = myInputTransaction.readLine())!=null) {
					
					// if the line is a comment, is empty or is a kind of metadata
					if(thisLineTransaction.isEmpty()==true || thisLineTransaction.charAt(0)=='#'||thisLineTransaction.charAt(0)=='%'||thisLineTransaction.charAt(0)=='@') {
						continue;
					}
					
					// split the line according to the separator
					String split[] = thisLineTransaction.split(":");
					
					// get the list of items
					String items[] = split[0].split(" ");
					
					// get the transaction utility
					transactionTU[tid] = Double.parseDouble(split[1]);
					
					//get the list of utility values corresponding to each item
					// for that transaction
					String utilityValues[] = split[2].split(" ");
					
					// Copy the transaction into lists but without items with GWU < minutility
					double remainingUtility = 0;
					
					// create a list to store items
					List<Pair> revisedTransaction = new ArrayList<Pair>();
					
					// for each item
					for(int i=0;i<items.length;i++) {
						
						// convert values to integers
						Pair pair = new Pair();
						pair.item = Integer.parseInt(items[i]);
						pair.utility = Double.parseDouble(utilityValues[i]);
						
						// if the item has enough utility
						if(mapItemToGWU.get(pair.item)>=min_utility) {
							// add it
							revisedTransaction.add(pair);
							remainingUtility += pair.utility;
						} else {
							// check the utility of its generalized item, if enough, add it
							List<Integer> listAncestorOfItem = mapItemToAncestor.get(pair.item);
							for(int k=0;k<listAncestorOfItem.size();k++) {
								if(mapItemToGWU.get(listAncestorOfItem.get(k))>=min_utility) {
										
									revisedTransaction.add(pair);
									remainingUtility += pair.utility;
									break;
								}
							}
							
						}
					}
					
					// sort the revisedTransaction according to the total order
					Collections.sort(revisedTransaction, new Comparator<Pair>() {
						public int compare(Pair o1, Pair o2) {
							return compareItems(o1.item, o2.item);
						}
					});
					
					Map<Integer, Double> ancestor = new LinkedHashMap<>();
					Map<Integer, Double> firstChildItemUtility = new HashMap<>();
					Map<Integer, Double> firstChildItemRemainingUtility = new HashMap<>();
					
					
					// for each item left in the transaction
					for(Pair pair: revisedTransaction) {
						
						// subtract the utility of this item from the remaining utility
						remainingUtility = remainingUtility - pair.utility;
						
						// get the tax-utility-list of this item
						UtilityListMLHUIMiner taxUtilityListMLHUIMinerOfItem = mapItemToUtilityListMLHUIMiner.get(pair.item);
						
						// add a new element to the tax-utility-list of this item corresponding to this transaction
						ElementMLHUIMiner element = new ElementMLHUIMiner(tid, pair.utility, remainingUtility);
						taxUtilityListMLHUIMinerOfItem.addElement(element);
						
						// store the tax-utility-list in the map
						mapItemToUtilityListMLHUIMiner.put(pair.item, taxUtilityListMLHUIMinerOfItem);
						
						// get the current ancestor list of the item
						List<Integer> listAncestorOfItem = mapItemToAncestor.get(pair.item);
						
//						Integer itemCopy = pair.item;
//						UtilityListMLHUIMiner taxUtilityListMLHUIMinerOfItemCopy = taxUtilityListCMLHUIMinerOfItem;
						
						// for each item and generalized item in this list
						for(int k=0;k<listAncestorOfItem.size();k++) {
							
							// get the tax-utility-list		
							UtilityListMLHUIMiner taxUtilityListMLHUIMinerOfParentItem = mapItemToUtilityListMLHUIMiner.get(listAncestorOfItem.get(k));
							double parentUtility = 0;
							
							// if this is the first item it has been calculated in this transaction
							if(!ancestor.containsKey(listAncestorOfItem.get(k))) {
								
								// store the utility and remaining utility of the first item
								firstChildItemUtility.put(listAncestorOfItem.get(k), pair.utility);
								firstChildItemRemainingUtility.put(listAncestorOfItem.get(k), remainingUtility);
										
								// get the current utility		
								parentUtility = pair.utility;
								
								// store the utiltiy of current item/generalized item
								ancestor.put(listAncestorOfItem.get(k), parentUtility);
								
								// create the tuple
								ElementMLHUIMiner parentElementMLHUIMiner = new ElementMLHUIMiner(tid, pair.utility, firstChildItemRemainingUtility.get(listAncestorOfItem.get(k)));
								
								// add it to the tax-utility-list
								taxUtilityListMLHUIMinerOfParentItem.addElement(parentElementMLHUIMiner);
										
										
							}  else {
								
								// get the current utility
								//parentUtility = ancestor.get(listAncestorOfItem.get(k))+pair.utility;
								
								// create the tuple
								
								//ElementMLHUIMiner temp = taxUtilityListMLHUIMinerOfParentItem.elements.get(tid);
								//ElementMLHUIMiner parentElementMLHUIMiner = new ElementMLHUIMiner(tid, ancestor.get(listAncestorOfItem.get(k))+pair.utility, remainingUtility);
								//taxUtilityListMLHUIMinerOfParentItem.deleteElementMLHUIMiner(temp);
								
								ElementMLHUIMiner parentElementMLHUIMiner = new ElementMLHUIMiner(tid, pair.utility, -pair.utility);
								
								// add it to the tax-utility-list
								taxUtilityListMLHUIMinerOfParentItem.addElement(parentElementMLHUIMiner);
							}
										
							// update the child field of the tax-utility-list		
							//taxUtilityListMLHUIMinerOfParentItem.childs.put(itemCopy, taxUtilityListMLHUIMinerOfItemCopy);
									
							// store the tax-utility-list of generalized item
							mapItemToUtilityListMLHUIMiner.put(listAncestorOfItem.get(k), taxUtilityListMLHUIMinerOfParentItem);
							
							// update the current item and tax-utility-list
//							itemCopy = listAncestorOfItem.get(k);
//							taxUtilityListMLHUIMinerOfItemCopy = taxUtilityListMLHUIMinerOfParentItem;
						}
					
					}
					tid++; // increase tid number for next transaction
				}
				
			} catch(Exception e) {
				// to catch error while reading the input file
				e.printStackTrace();
			} finally {
				if(myInputTransaction!=null) {
					myInputTransaction.close();
				}
			}
			
//			for(Map.Entry<Integer, UtilityListMLHUIMiner> entry : mapItemToUtilityListMLHUIMiner.entrySet()){
//				Integer mapKey = entry.getKey();
//				UtilityListMLHUIMiner mapValue = entry.getValue();
//			    System.out.println(mapKey+":"+mapValue.item+" " + mapValue.sumIutils + " " + mapValue.sumRutils);
//			}
			
			List<List<UtilityListMLHUIMiner>> listOfUtilityListMLHUIMiners = new ArrayList<>();
			for(int i=0;i<getMaxLevel(mapItemToLevel);i++) {
				
				List<UtilityListMLHUIMiner> UtilityListMLHUIMinerOfILevel = new ArrayList<>();
				for(Entry<Integer, Double> entry: mapItemToGWU.entrySet()){
					Integer item = entry.getKey();
					
					// if the item is promising  (TWU >= minutility)
					if(mapItemToGWU.get(item) >= min_utility){
						
						if (mapItemToLevel.get(item) == i+1) {
							
							// create an empty Utility List that we will fill later.
							UtilityListMLHUIMiner uList = mapItemToUtilityListMLHUIMiner.get(item);
							//mapItemToUtilityListMLHUIMiner.put(item, uList);
							// add the item to the list of high TWU items
							UtilityListMLHUIMinerOfILevel.add(uList);
						}
									
						//listOfUtilityListMLHUIMiners.add(uList); 					
					} 
								
				}
				
				listOfUtilityListMLHUIMiners.add(UtilityListMLHUIMinerOfILevel);			
			}
			
			
			System.out.println("algorithm is running......");
			// check the memory usage
			MemoryLogger.getInstance().checkMemory();

			// Mine the database recursively
			for(int i=0;i<getMaxLevel(mapItemToLevel);i++) {
				mlhuiminer(itemsetBuffer, 0, null, listOfUtilityListMLHUIMiners.get(i), min_utility);
			}
			
			
			// check the memory usage again and close the file.
			MemoryLogger.getInstance().checkMemory();
			// close output file
			writer.close();
			// record end time
			endTimeStamp = System.currentTimeMillis();
			
			System.out.println("finished......");
		}
		
		private static Integer getMaxLevel(Map<Integer, Integer> map) {
	        if (map == null)
	            return null;
	        int length =map.size();
	        Collection<Integer> c = map.values();
	        Object[] obj = c.toArray();
	        Arrays.sort(obj);
	        return Integer.parseInt(obj[length-1].toString());
	    }
		
		
		private int compareItems(int item1, int item2) {
			
			double compare = mapItemToGWU.get(item1) - mapItemToGWU.get(item2);
			
			// if the same, use the lexical order, otherwise use the GWU
			if(Math.abs(compare)<0.01)
				return item1 - item2;
			else if(compare>0)
				return 1;
			else
				return -1;
		}
		
		private void mlhuiminer(int [] prefix,
				int prefixLength, UtilityListMLHUIMiner pUL, List<UtilityListMLHUIMiner> ULs, Double minUtility)
				throws IOException {
			
			// For each extension X of prefix P
			for(int i=0; i< ULs.size(); i++){
				UtilityListMLHUIMiner X = ULs.get(i);

				// If pX is a high utility itemset.
				// we save the itemset:  pX 
				if(X.sumIutils >= minUtility){
					// save to file
					writeOut(X.item, X.sumIutils);
				}
				
				// If the sum of the remaining utilities for pX
				// is higher than minUtility, we explore extensions of pX.
				// (this is the pruning condition)
				//if(X.sumIutils + X.sumRutils >= minUtility){
					// This list will contain the utility lists of pX extensions.
					List<UtilityListMLHUIMiner> exULs = new ArrayList<UtilityListMLHUIMiner>();
					// For each extension of p appearing
					// after X according to the ascending order
					for(int j=i+1; j < ULs.size(); j++){
						UtilityListMLHUIMiner Y = ULs.get(j);
							
						// we construct the extension pXY 
						// and add it to the list of extensions of pX
						UtilityListMLHUIMiner temp = construct(pUL, X, Y);
						if(temp != null) {
							exULs.add(temp);
						}
					//}
						
						
					
					// We create new prefix pX
					//itemsetBuffer[prefixLength] = X.item;
					// We make a recursive call to discover all itemsets with the prefix pXY
					mlhuiminer(itemsetBuffer, prefixLength+1, X, exULs, minUtility); 
				}
			}
			MemoryLogger.getInstance().checkMemory();
		}
		
		
		
		private UtilityListMLHUIMiner construct(UtilityListMLHUIMiner P, UtilityListMLHUIMiner py, UtilityListMLHUIMiner px) {
			
			List<Integer> itemOfPXY = new ArrayList<Integer>();

			Set<Integer> itemTemp = new LinkedHashSet<>();
			
			// generate pXY
			itemTemp.addAll(py.item);
			itemTemp.addAll(px.item);
			itemOfPXY.addAll(itemTemp);
				
			// create an empty tax-utility-list for pXY
			UtilityListMLHUIMiner pxyUL = new UtilityListMLHUIMiner(itemOfPXY);
			
			//TaxUtilityListMLHUIMiner lastItem = new TaxUtilityListMLHUIMiner();
				
			// for each element in the tax-utility-list of pX
			for(ElementMLHUIMiner ex:px.elements) {
				
				// do a binary search to find element ey in py with tid=ex.tid
				ElementMLHUIMiner ey = findElementMLHUIMinerWithTID(py, ex.tid);
				
				if(ey==null) {
					continue;
				}
				
				// if the prefix p is null
				if(P==null) {
					
					// create the new element
					ElementMLHUIMiner eXY = new ElementMLHUIMiner(ex.tid, ex.iutils + ey.iutils, ey.rutils);
					
					// add the new element to the tax-utility-list of pXY
					pxyUL.addElement(eXY);
				} else {
					
					// find the element in the tax-utility-list of p with the same tid
					ElementMLHUIMiner e = findElementMLHUIMinerWithTID(P, ex.tid);
					
					if(e!=null) {
						
						// create new element
						ElementMLHUIMiner eXY = new ElementMLHUIMiner(ex.tid, ex.iutils + ey.iutils - e.iutils, ey.rutils);
						
						// add the new element to the tax-utility-list of pXY
						pxyUL.addElement(eXY);
					}
				}
			}
			
			// return the tax-utility-list of pXY
			return pxyUL;
		}
		
		private ElementMLHUIMiner findElementMLHUIMinerWithTID(UtilityListMLHUIMiner tulist, int tid) {
			
			List<ElementMLHUIMiner> list = tulist.elements;
			
			// perform a binary search to check if the subset appears in level k-1
			int first = 0;
			int last = list.size()-1;
			
			// the binary search
			while(first <= last) {
				int middle = (first + last) >>> 1; // divide by 2
				
				if(list.get(middle).tid < tid) {
					// the itemset compared is larger than the subset according to the lexical order
					first = middle + 1;
				} else if(list.get(middle).tid > tid) {
					
					// the itemset compared is smaller than the subset according to the lexical order
					last = middle - 1;
				} else {
					return list.get(middle);
				}
			}
			return null;
		}
		
		private void writeOut(List<Integer> item, double utility) throws IOException {
	
			if(!storeResult.contains(item)) {
				storeResult.add(item);
				huiCount++; // increase the number of high utility itemsets found
				
				StringBuilder buffer = new StringBuilder();
				
				// append each item
				for(int i=0;i<item.size();i++) {
					buffer.append(item.get(i));
					if (i!= item.size() - 1) {
						buffer.append(' ');
					}			
				}
				
				// append the utility value
				buffer.append(" #UTIL: ");
				buffer.append(utility);
				
				//write to the file
				writer.write(buffer.toString());
				writer.newLine();
			}
			
			//System.out.println(storeResult);
			
		}
		
		public void printStatistics() throws IOException {
			System.out.println("=============  MLHUIMiner ALGORITHM - SPMF 0.97e - STATS =============");
			//System.out.println("minutil: ");
			System.out.println(" Total time ~ "                  + (endTimeStamp - startTimeStamp) + " ms");
			System.out.println(" Memory ~ "                      + MemoryLogger.getInstance().getMaxMemory()  + " MB");
			System.out.println(" High-utility itemsets count : " + huiCount); 
			//System.out.println(" Candidate count : "             + candidateCount);	
			System.out.println("===================================================");
		}
}
