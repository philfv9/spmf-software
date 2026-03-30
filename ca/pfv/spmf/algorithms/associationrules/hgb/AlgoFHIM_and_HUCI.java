/* This file is copyright (c) Jayakrushna Sahoo
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

package ca.pfv.spmf.algorithms.associationrules.hgb;

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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the FHIM and HUCI_Miner algorithm to find high
 * utility itemsets. It was proposed in the paper "An efficient approach for
 * mining association rules from high utility itemsets" by Sahoo et al. (2015)
 *
 * @author Jayakrushna Sahoo
 */
public class AlgoFHIM_and_HUCI {
	/**  the maximum memory usage */
	public double maxMemory = 0; 
	/**  the time the algorithm started */
	public long startTimestamp = 0;
	/** the time the algorithm terminated */
	public long endTimestamp = 0; 
	/** the number of HUI generated */
	public int hui = 0; 
	/** the candidate count */
	public int candidate = 0;
	/** the number of closed itemsets */
	public int chui = 0;
	/** the number of generator itemsets */
	public int ghui = 0;
	/** the maximum length of itemsets */
	int maxlength = 0;
	/** the table of high utility itemsets */
	private HUTable tableHUI = null;
	/** the table of closed high utility itemsets and generators */
	private HUClosedTable tableHUCI = null;
	/** the minimum utility */
	int minUtility = 0;
	/** the input file path */
	String input = null;
	
	/** Object to write results to file */
	BufferedWriter writer = null;
	
	/** The choice of algorithm :  0 = FHIM   1  = HUCI-Miner*/
	int algo = 0;
	/** The HG list */
	private List<Itemset> HG = null; 
	/**  The FMAP structure */
	Map<Integer, Map<Integer, Integer>> mapFMAP;
	/** A map of items to their utility lists*/
	Map<Integer, UtilityList> mapItemToUtilityList;
	/** the LLFMAP */
	Map<Integer, Map<Integer, Map<Integer, Integer>>> mapLLFMAP;
	
	/** if true, HUCI-miner will not output the closed itemsets */
	boolean dontOutputClosedItemsets = false;
	
	/**
	 * Set a boolean variable to indicate that closed itemset should not be output by HUCI-Miner or should be output
	 * @param dontOutputClosedItemsets  true or false
	 */
	public void setDontOutputClosedItemsets(boolean dontOutputClosedItemsets) {
		this.dontOutputClosedItemsets = dontOutputClosedItemsets;
	}

	/**
	 * Set a boolean variable to indicate that generator itemset should not be output by HUCI-Miner or should be output
	 * @param dontOutputGeneratorItemsets  true or false
	 */
	public void setDontOutputGeneratorItemsets(boolean dontOutputGeneratorItemsets) {
		this.dontOutputGeneratorItemsets = dontOutputGeneratorItemsets;
	}

	/** if true HUCI-miner will not output the generator itemsets */
	boolean dontOutputGeneratorItemsets = false;

	/**
	 * A class representing an item and its utility
	 */
	class Pair {
		/** the item */
		int item = 0;
		/** the utility */
		int utility = 0;

		@Override
		public String toString() {
			return "[" + item + "," + utility + "]";
		}
	}

	/**
	 * Default constructor
	 */
	public AlgoFHIM_and_HUCI() {

	}

	/**
	 * Run the FHIM algorithm
	 * 
	 * @param input   an input file path
	 * @param output  an output file path
	 * @param minUtil the minimum utility threshold
	 * @return a table of closed
	 * @throws IOException if error while reading or writing to file
	 */
	public void runAlgorithmFHIM(String input, String output, int minUtil) throws IOException {

		runAlgo(input, output, minUtil, 0);
	}

	/**
	 * Run the HUCI-Miner algorithm
	 * 
	 * @param input   an input file path
	 * @param output  an output file path
	 * @param minUtil the minimum utility threshold
	 * @return a table of closed
	 * @throws IOException if error while reading or writing to file
	 */
	public HUClosedTable runAlgorithmHUCIMiner(String input, String output, int minUti) throws IOException {

		return runAlgo(input, output, minUti, 1);
	}

	/**
	 * Run the algorithm
	 * 
	 * @param input  the input file path
	 * @param minUti
	 * @param 0      for FHIM and 1 for HUCI-Miner
	 * @return
	 * @throws IOException exception if error while writing the file
	 */
	private HUClosedTable runAlgo(String input, String output, int minUtility, int alg) throws IOException {
		maxMemory = 0;
		ghui = 0;
		chui = 0;
		startTimestamp = System.currentTimeMillis();

		// if the user want to write to file
		if (output != null) {
			writer = new BufferedWriter(new FileWriter(output));
		}

		MemoryLogger.getInstance().getInstance().reset();
		this.algo = alg;
		Map<Integer, Integer> mapItemToTWU;
		mapFMAP = new HashMap<>();
		HG = new ArrayList<>(); // stores the generators
		tableHUI = new HUTable();// stores closed high utility itemsets
		tableHUCI = new HUClosedTable();// stores hiugh utility itmsets
//		int totalutility = 0;// Total Utility of the Database
		mapItemToTWU = new HashMap<>();
		BufferedReader myInput = null;
		String thisLine;
		try {
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			while ((thisLine = myInput.readLine()) != null) {
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}
				String split[] = thisLine.split(":");
				String items[] = split[0].split(" ");
				int transactionUtility = Integer.parseInt(split[1]);
//				totalutility += transactionUtility;
				for (int i = 0; i < items.length; i++) {
					Integer item = Integer.parseInt(items[i]);
					Integer twu = mapItemToTWU.get(item);
					twu = (twu == null) ? transactionUtility : twu + transactionUtility;
					mapItemToTWU.put(item, twu);
				}
			}
		} catch (IOException | NumberFormatException e) {
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}
//		int k = (int) (totalutility * (minUti / 100.0f));
		this.minUtility = minUtility;
		System.out.println("Absolute utility threshold = " + this.minUtility);
		// CREATE A LIST TO STORE THE UTILITY LIST OF ITEMS WITH TWU >= MIN_UTILITY.
		List<UtilityList> listOfUtilityLists = new ArrayList<>();
		// Key : item Value : utility list associated to that item
		mapItemToUtilityList = new HashMap<>();
		for (Integer item : mapItemToTWU.keySet()) {
			if (mapItemToTWU.get(item) >= minUtility) {
				// create an empty Utility List that we will fill later.
				UtilityList uList = new UtilityList(item);
				mapItemToUtilityList.put(item, uList);
				// add the item to the list of high TWU items
				listOfUtilityLists.add(uList);
			}
		}
// SORT THE LIST OF HIGH TWU ITEMS IN ASCENDING ORDER
		Collections.sort(listOfUtilityLists, new Comparator<UtilityList>() {
			@Override
			public int compare(UtilityList o1, UtilityList o2) {
				int compare = mapItemToTWU.get(o1.item) - mapItemToTWU.get(o2.item);
				return (compare == 0) ? o1.item - o2.item : compare;
			}
		});

// SECOND DATABASE PASS TO CONSTRUCT THE UTILITY LISTS 
// OF 1-ITEMSETS  HAVING TWU  >= minutil (promising items)
		try {
			// prepare object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			// variable to count the number of transaction
			int tid = 0;
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is a comment, is empty or is a
				// kind of metadata
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}

				String split[] = thisLine.split(":");
				String items[] = split[0].split(" ");
				String utilityValues[] = split[2].split(" ");
				int remainingUtility = 0;
				int newTWU = 0; // NEW OPTIMIZATION
				// Create a list to store items
				List<Pair> revisedTransaction = new ArrayList<>();
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

				// sort the transaction
				Collections.sort(revisedTransaction, new Comparator<Pair>() {
					@Override
					public int compare(Pair o1, Pair o2) {
						int compare = mapItemToTWU.get(o1.item) - mapItemToTWU.get(o2.item);
						return (compare == 0) ? o1.item - o2.item : compare;
					}
				});

				// for each item left in the transaction
				for (int i = 0; i < revisedTransaction.size(); i++) {
					Pair pair = revisedTransaction.get(i);
					int remain = remainingUtility; // FOR OPTIMIZATION
					// subtract the utility of this item from the remaining utility
					remainingUtility = remainingUtility - pair.utility;
					// get the utility list of this item
					UtilityList utilityListOfItem = mapItemToUtilityList.get(pair.item);
					// Add a new Element to the utility list of this item corresponding to this
					// transaction
					Element element = new Element(tid, pair.utility, remainingUtility);
					utilityListOfItem.addElement(element);
					// BEGIN NEW OPTIMIZATION for FHM
					Map<Integer, Integer> mapFMAPItem = mapFMAP.get(pair.item);
					if (mapFMAPItem == null) {
						mapFMAPItem = new HashMap<>();
						mapFMAP.put(pair.item, mapFMAPItem);
					}

					for (int j = i + 1; j < revisedTransaction.size(); j++) {
						Pair pairAfter = revisedTransaction.get(j);
						Integer twuSum = mapFMAPItem.get(pairAfter.item);
						if (twuSum == null) {
							mapFMAPItem.put(pairAfter.item, newTWU);
						} else {
							mapFMAPItem.put(pairAfter.item, twuSum + newTWU);
						}
					}

					// END OPTIMIZATION of FHM
				}
				tid++; // increase tid number for next transaction

			}
		} catch (IOException | NumberFormatException e) {
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}

		MemoryLogger.getInstance().checkMemory();

		mapLLFMAP = new HashMap<>();
		Itemset prefix = new Itemset();
		for (int i = 0; i < listOfUtilityLists.size(); i++) {
			UtilityList X = listOfUtilityLists.get(i);
			if (X.sumIutils >= minUtility) {
//                            int k1=prefix.length+1;
//                          if(maxlength<k1)
//                                maxlength=k1; 
				store(prefix, X);
				// writeOut(prefix, X.item, X.sumIutils,X);
			}
			if (X.sumIutils + X.sumRutils >= minUtility) {
				List<UtilityList> exULs = new ArrayList<>();
				for (int j = i + 1; j < listOfUtilityLists.size(); j++) {
					UtilityList Y = listOfUtilityLists.get(j);
					Map<Integer, Integer> mapTWUF = mapFMAP.get(X.item);
					if (mapTWUF != null) {
						Integer twuF = mapTWUF.get(Y.item);
						if (twuF != null && twuF < minUtility) {
							continue;
						}
					}
					candidate++;
					exULs.add(construct(null, X, Y));
				}
//                         int [] newPrefix = new int[prefix.length+1];
//		         System.arraycopy(prefix, 0, newPrefix, 0, prefix.length);
//			newPrefix[prefix.length] = X.item;
				Itemset newPrefix = prefix.clone();
				newPrefix.addItem(X.item);
				Map<Integer, Map<Integer, Integer>> LmapFMAP = mapLLFMAP.get(X.item);
				if (mapLLFMAP.get(X.item) == null) {
					LmapFMAP = new HashMap();
					mapLLFMAP.put(X.item, LmapFMAP);
				}

				huiMiner(X.item, true, newPrefix, X, exULs);
//                       System.out.print(X.item+"----"+mapLLFMAP.get(X.item));
				mapLLFMAP.put(X.item, null);
				// System.out.println();
			}
		}
		// huiMiner(true,Prefix, null, listOfUtilityLists, minUtility,LmapFMAP);

		// Identification of High utility closed itemsets with generators
		if (algo == 1) {

			huciMiner();
		}

		// check the memory usage again and close the file.
		MemoryLogger.getInstance().getInstance().checkMemory();

		maxMemory = MemoryLogger.getInstance().getInstance().getMaxMemory();
		endTimestamp = System.currentTimeMillis();

		// WRITE OUTPUT
		if (output != null) {
			/// =============== CODE ADDED BY PHILIPPE 2022 TO OUTPUT RESULTS =========

//			private HUTable tableHUI = null;
//			private HUClosedTable tableHUCI = null;

			StringBuilder buffer = new StringBuilder();
			
			for (List<Itemset> itemsets : tableHUCI.levels) {
				for (Itemset itemset : itemsets) {
				
					if(!dontOutputGeneratorItemsets && !dontOutputClosedItemsets) {
						buffer.append("CLOSED: ");
						buffer.append(System.lineSeparator());
					}
					
					if(!dontOutputClosedItemsets) {
						appendItemset(itemset, buffer);
						buffer.append(System.lineSeparator());
					}

					List<Itemset> generators = tableHUCI.mapGenerators.get(itemset);
					if(!dontOutputGeneratorItemsets  && generators != null && generators.size() >0) {
						if(!dontOutputClosedItemsets) {
							buffer.append("GENERATOR: ");
							buffer.append(System.lineSeparator());
						}
						for(Itemset generator : generators) {
							appendItemset(generator, buffer);
							buffer.append(System.lineSeparator());
							ghui++;
						}
					}
					
					
				}
			}
			writer.write(buffer.toString());
			//===================== END CODE ADDED BY PHILIPPE
			
		}

		// WRITE OUTPUT
		if (writer != null) {
			writer.close();
		}

		return tableHUCI;
	}

	/**
	 * Append an itemset to a StringBuffer for storing the final results
	 * @param itemset the itemset
	 * @param buffer the string buffer
	 */
	private void appendItemset(Itemset itemset, StringBuilder buffer) {
		// for each item in the closed itemset
		Iterator<Integer> iterItem = itemset.getItems().iterator();
		while (iterItem.hasNext()) {
			// append the item
			buffer.append(iterItem.next());
			// if it is not the last item, append a space
			if (iterItem.hasNext()) {
				buffer.append(' ');
			} else {
				break;
			}
		}
		// append the support
		buffer.append(" #UTIL: ");
		buffer.append(itemset.acutility);
		// append the support
		buffer.append(" #SUP: ");
		buffer.append(itemset.support);
	}

	/**
	 * Store an itemset
	 * 
	 * @param prefix the prefix
	 * @param X      the utility
	 * @throws IOException if error while writing to file
	 */
	private void store(Itemset prefix, UtilityList X) throws IOException {
		hui++;
		if (algo == 1) {
			Itemset newPrefix1 = prefix.clone();
			newPrefix1.addItem(X.item);
			int k1 = newPrefix1.size();
			if (maxlength < k1)
				maxlength = k1;
			newPrefix1.acutility = X.sumIutils;
			newPrefix1.support = X.elements.size();
			newPrefix1.sort();
			utilityunitarray(newPrefix1, X);
			tableHUI.addHuighUtilityItemset(newPrefix1);
			tableHUI.mapKey.put(newPrefix1, true);
			tableHUI.mapSupp.put(newPrefix1, newPrefix1.support);
			tableHUI.mapClosed.put(newPrefix1, true);
		} else if (algo == 0) {
			// =========== ADDED BY PHILIPPE =============

			// IF this is the FHIM algorithm, we save the high utility itemsets to file
			// instead of keeping them in memory
			if (writer != null) {
				// Make a temporary array
				int tempArray[] = new int[prefix.size() + 1];

				// copy all items from the prefix
				int pos = 0;
				for (; pos < prefix.size(); pos++) {
					tempArray[pos] = prefix.get(pos);
				}
				// add the item X from the utility list
				tempArray[pos] = X.item;

				// Sort the itemset
				Arrays.sort(tempArray);

				// Now we will write the itemset to file
				StringBuilder buffer = new StringBuilder();
				for (pos = 0; pos < tempArray.length; pos++) {
					buffer.append(tempArray[pos]);
					buffer.append(' ');
				}

				if (maxlength < tempArray.length)
					maxlength = tempArray.length;

				// append the support
				buffer.append(" #UTIL: ");
				buffer.append(X.sumIutils);
				// append the support
				buffer.append(" #SUP: ");
				buffer.append(X.elements.size());
				// append the buffer
				writer.write(buffer.toString());
				writer.newLine();
			}
		}
	}
//
//	/**
//	 * Write an itemset that is found to the output file.
//	 */
//	private void writeOut(Itemset itemset) throws IOException {
//
//		StringBuilder buffer = new StringBuilder();
//		// for each item in the closed itemset
//		Iterator<Integer> iterItem = itemset.getItems().iterator();
//		while (iterItem.hasNext()) {
//			// append the item
//			buffer.append(iterItem.next());
//			// if it is not the last item, append a space
//			if (iterItem.hasNext()) {
//				buffer.append(' ');
//			} else {
//				break;
//			}
//		}
//		// append the support
//		buffer.append(" #UTIL: ");
//		buffer.append(itemset.acutility);
//		// append the support
//		buffer.append(" #SUP: ");
//		buffer.append(itemset.support);
//		// append the buffer
//		writer.write(buffer.toString());
//		writer.newLine();
//	}

	// For getting all HUIs
	public HUTable getTableHU() {
		return tableHUI;
	}

	// for getting absolute minimum utility
	public int getminutil() {
		return minUtility;
	}

	private void huciMiner() {
		for (int iter = 2; iter < maxlength + 1; iter++) {
			if (tableHUI.getLevelFor(iter) != null) {
				if (tableHUI.getLevelFor(iter - 1) != null) {
					for (Itemset l : tableHUI.getLevelFor(iter)) {
						for (Itemset s : subset(tableHUI.getLevelFor(iter - 1), l)) { // 38, 39
							if (s.support == l.support) { // 40
								tableHUI.mapClosed.put(s, false);
								tableHUI.mapKey.put(l, false);
							}
						}
					}
					for (Itemset l : tableHUI.getLevelFor(iter - 1)) {
						if (tableHUI.mapClosed.get(l) == true) {
							tableHUCI.addHighUtilityClosedItemset(l);
							chui++;
							List<Itemset> s = subset(HG, l); // 3
							tableHUCI.mapGenerators.put(l, s); // 4
							HG.removeAll(s);
						}
						if (tableHUI.mapKey.get(l) == true && tableHUI.mapClosed.get(l) == false) {
							HG.add(l);
						}
					}
				}
			} else if (tableHUI.getLevelFor(iter - 1) != null) {
				for (Itemset l : tableHUI.getLevelFor(iter - 1)) {
					if (tableHUI.mapClosed.get(l) == true) {
						tableHUCI.addHighUtilityClosedItemset(l);
						chui++;
						List<Itemset> s = subset(HG, l); // 3
						tableHUCI.mapGenerators.put(l, s); // 4
						HG.removeAll(s);
					}
					if (tableHUI.mapKey.get(l) == true && tableHUI.mapClosed.get(l) == false) {
						HG.add(l);
					}
				}
			}
		}

		if (tableHUI.getLevelFor(maxlength) != null) {
			for (Itemset l : tableHUI.getLevelFor(maxlength)) {
				if (tableHUI.mapClosed.get(l) == true) {
					tableHUCI.addHighUtilityClosedItemset(l);
					chui++;
					List<Itemset> s = subset(HG, l); // 3
					tableHUCI.mapGenerators.put(l, s); // 4
					HG.removeAll(s);
				}
			}
		}

	}// end of HUCI-Miner

	private void utilityunitarray(Itemset l, UtilityList Z) {
		for (int it = 0; it < l.size(); it++) {
			Integer ite = l.get(it);
			UtilityList jk = mapItemToUtilityList.get(ite);
			int v = 0;
			for (Element e : Z.elements) {
				Element ey = findElementWithTID(jk, e.tid);
				if (ey != null) {
					v += ey.iutils;
				}
			}
			l.addutility(v);
		}
	}

	/**
	 * This is the recursive method to find all high utility itemsets. It writes the
	 * itemsets to the output file.
	 * 
	 * @param prefix     This is the current prefix. Initially, it is empty.
	 * @param pUL        This is the Utility List of the prefix. Initially, it is
	 *                   empty.
	 * @param ULs        The utility lists corresponding to each extension of the
	 *                   prefix.
	 * @param minUtility The minUtility threshold.
	 * @throws IOException
	 */

	private void huiMiner(int k, boolean ft, Itemset prefix, UtilityList pUL, List<UtilityList> ULs)
			throws IOException {
		Map<Integer, Map<Integer, Integer>> LmapFMAP = mapLLFMAP.get(k);
		for (int i = ULs.size() - 1; i >= 0; i--) {
			UtilityList X = ULs.get(i);
			if (X.sumIutils >= minUtility) {
//                            int k1=prefix.length+1;
//                          if(maxlength<k1)
//                                maxlength=k1;   
				store(prefix, X);
			}
			if (X.sumIutils + X.sumRutils >= minUtility) {
				List<UtilityList> exULs = new ArrayList<>();
				for (int j = i + 1; j < ULs.size(); j++) {
					UtilityList Y = ULs.get(j);

					// OPTIMIZATION STARTS...........................................
					if (Y.exutil >= minUtility) {
						if ((prefix.size() < 2)) {
							Map<Integer, Integer> mapTWUF = mapFMAP.get(X.item);
							if (mapTWUF != null) {
								Integer twuF = mapTWUF.get(Y.item);
								if (twuF != null && twuF < minUtility) {
									continue;
								}
							}
						}
						// IF THE SIZE IS MORE THAN 2
						else if (Y.exutil < minUtility) {
							continue;
						} else {
							// System.out.println("Hiiiiiiiiiiii");
							Map<Integer, Integer> mapTWUF = LmapFMAP.get(X.item);
							if (mapTWUF != null) {

								Integer twuF = mapTWUF.get(Y.item);
								if (twuF != null && twuF < minUtility) {

									continue;
								}
							}
						}
						candidate++;
						if ((ft == true) && (prefix.size() == 1)) {
							exULs.add(constructL(pUL, X, Y, k));
						} else {
							exULs.add(construct(pUL, X, Y));
						}
					}
				}

				Itemset newPrefix = prefix.clone();
				newPrefix.addItem(X.item);
				huiMiner(k, true, newPrefix, X, exULs);

			}
		}
	}

	/**
	 * This method constructs the utility list of pXY
	 * 
	 * @param P  : the utility list of prefix P.
	 * @param px : the utility list of pX
	 * @param py : the utility list of pY
	 * @return the utility list of pXY
	 */
	private UtilityList construct(UtilityList P, UtilityList px, UtilityList py) {
		UtilityList pxyUL = new UtilityList(py.item);
		int newTWU = 0;// OPTIMIZATION
		for (Element ex : px.elements) {
			Element ey = findElementWithTID(py, ex.tid);
			if (!(ey == null)) {
				if (P == null) {
					Element eXY = new Element(ex.tid, ex.iutils + ey.iutils, ey.rutils);
					pxyUL.addElement(eXY);
					newTWU = newTWU + ex.iutils + ex.rutils;

				} else {
					Element e = findElementWithTID(P, ex.tid);
					if (e != null) {
						Element eXY = new Element(ex.tid, ex.iutils + ey.iutils - e.iutils, ey.rutils);
						pxyUL.addElement(eXY);
						newTWU = newTWU + ex.iutils + ex.rutils;// OPTIMIZATION
					}
				}
			}
		}
		pxyUL.setexutil(newTWU);// OPTIMIZATION
		return pxyUL;
	}

	private UtilityList constructL(UtilityList P, UtilityList px, UtilityList py, int k) {
		UtilityList pxyUL = new UtilityList(py.item);
		int newTWU = 0;// OPTIMIZATION
		int newex = 0;
		for (Element ex : px.elements) {
			Element ey = findElementWithTID(py, ex.tid);
			if (ey == null) {
				continue;
			}
			if (P == null) {
				Element eXY = new Element(ex.tid, ex.iutils + ey.iutils, ey.rutils);
				pxyUL.addElement(eXY);

			} else {
				Element e = findElementWithTID(P, ex.tid);
				if (e != null) {
					Element eXY = new Element(ex.tid, ex.iutils + ey.iutils - e.iutils, ey.rutils);
					newTWU = newTWU + e.iutils + e.rutils;// OPTIMIZATION
					newex = newex + ex.iutils + ex.rutils;
					pxyUL.addElement(eXY);
				}
			}
		}

		// OPTIMIZATION
		Map<Integer, Map<Integer, Integer>> LmapFMAP = mapLLFMAP.get(k);
		if (LmapFMAP.get(px.item) == null) {
			LmapFMAP.put(px.item, new HashMap<>());
		}

		LmapFMAP.get(px.item).put(py.item, newTWU);
		pxyUL.setexutil(newex);
		return pxyUL;
	}

	/**
	 * Do a binary search to find the element with a given tid in a utility list
	 * 
	 * @param ulist the utility list
	 * @param tid   the tid
	 * @return the element or null if none has the tid.
	 */
	private Element findElementWithTID(UtilityList ulist, int tid) {
		List<Element> list = ulist.elements;

		// perform a binary search to check if the subset appears in level k-1.
		int first = 0;
		int last = list.size() - 1;

		// the binary search
		while (first <= last) {
			int middle = (first + last) >>> 1; // divide by 2

			if (list.get(middle).tid < tid) {
				first = middle + 1; // the itemset compared is larger than the subset according to the lexical order
			} else if (list.get(middle).tid > tid) {
				last = middle - 1; // the itemset compared is smaller than the subset is smaller according to the
									// lexical order
			} else {
				return list.get(middle);
			}
		}
		return null;
	}

//	/**
//	 * Method to check the memory usage and keep the maximum memory usage.
//	 */
//	private void checkMemory() {
//		// get the current memory usage
//		double currentMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024d / 1024d;
//		// if higher than the maximum until now
//		if (currentMemory > maxMemory) {
//			// replace the maximum with the current memory usage
//			maxMemory = currentMemory;
//		}
//	}

	/**
	 * Print statistics about the latest execution to System.out.
	 * 
	 * @throws IOException
	 */
	public void printStats() throws IOException {
		if (algo == 0) // FHIM
			System.out.println("=============  FHIM ALGORITHM - STATS =============");
		else  // HUCI-Miner
			System.out.println("=============  HUCI-Miner ALGORITHM - STATS =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + maxMemory + " MB");
		System.out.println(" Candidate count : " + candidate);
		System.out.println(" High-utility itemsets count : " + hui);
		if (algo == 1  && !dontOutputClosedItemsets) { // NOT FHIM
			System.out.println(" Closed High-utility itemsets count : " + chui);
		}
		if (algo == 1  && !dontOutputGeneratorItemsets && writer != null) { // NOT FHIM
			System.out.println(" Generator High-utility itemsets count : " + ghui);
		}
		System.out.println("===================================================");
	}

	/**
	 * Find the subsets of an itemset in a list
	 * @param s the list of itemsets
	 * @param l the itemset
	 * @return the subset
	 */
	private List<Itemset> subset(List<Itemset> s, Itemset l) {
		List<Itemset> result = new ArrayList<>();
		for (Itemset itemsetS : s) {
			boolean allIncluded = true;
			for (Integer itemS : itemsetS.getItems()) {
				if (l.contains(itemS) == false) {
					allIncluded = false;
				}
			}
			if (allIncluded) {
				result.add(itemsetS);

			}
		}
		return result;
	}

}
