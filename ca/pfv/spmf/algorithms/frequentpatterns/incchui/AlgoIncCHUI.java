package ca.pfv.spmf.algorithms.frequentpatterns.incchui;

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
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import ca.pfv.spmf.algorithms.frequentpatterns.hui_miner.Element;
import ca.pfv.spmf.algorithms.frequentpatterns.hui_miner.UtilityListEIHI;

/**
 * This is an implementation of the IncCHUI algorithm, which was obtained from
 * GitHub and included in SPMF under the GPL license. That algorithm was presented in this paper: <br/><br/>
 * 
 * Thu-Lan Dam, Heri Ramampiaro, Kjetil Nørvåg, Quang-Huy Duong: Towards
 * efficiently mining closed high utility itemsets from incremental databases.
 * Knowl. Based Syst. 165: 13-29 (2019)
 * 
 * @author Thu-Lan Dam et al.
 */
public class AlgoIncCHUI {

	/**
	 * the memory usage
	 */
	public double memory = 0;
	/**
	 * the time the algorithm started
	 */
	public long startTimestamp = 0;
	/**
	 * the time the algorithm terminated
	 */
	public long endTimestamp = 0;
	/**
	 * Store the total times for all runs of this algorithm
	 */
	public long totalTimeForAllRuns = 0;
	/**
	 * The number of candidates processed by the algorithm for all updates (runs)
	 */
	// public int totalCandidateCountForAllRuns = 0;
	/**
	 * The number of transactions processed until now by the algorithm
	 */
	public int transactionCount;
	// public int candidateCount = 0;
	/**
	 * Map to remember the TWU of each item
	 */
	Map<Integer, Integer> mapItemToTWU;
	/**
	 * During first database, the item are sorted by TWU.... Then we keep this
	 * ordering in the following map because if the ordering change in an updated
	 * database, then the result may be incorrect.
	 */
	// Map<Integer, Integer> mapItemToRank;
	/**
	 * The EUCS structure, as described in the FHM paper It stores pairs of items
	 * and their corresponding TWU.
	 */
	// Map<Integer, Map<Integer, Integer>> mapEUCS;
	/**
	 * If this variable is set to true, this algorithm will show debugging
	 * information in the console
	 */
	boolean debug = false;
	/**
	 * This is a map to store the utility-list of each item from the database
	 */
	private Map<Integer, UtilityListEIHI> mapItemToUtilityList;
	/**
	 * This is a list of all utility-lists of single items
	 */
	List<UtilityListEIHI> listOfUtilityLists;
	/**
	 * This is the total utility of all transactions
	 */
	int totalDBUtility = 0;
	/**
	 * The minimum utility threshold
	 */
	int minUtility;
	/**
	 * The first line to be read from the input file
	 */
	int firstLine;
	/**
	 * A buffer for storing the current itemset that is mined when performing mining
	 * the idea is to always reuse the same buffer to reduce memory usage.
	 *
	 */
	private int[] itemsetBuffer = null;
	/**
	 * The initial buffer size
	 */
	final int BUFFERS_SIZE = 400;
	/**
	 * Writer object to write to the output file if the user choose to
	 */
	// BufferedWriter writer = null;
	/**
	 * the number of CHUI generated
	 */
	public int chuidCount = 0;
	public HashTable closedTable = null;

	public static double maxMemoryUsed = 0;

	public List<Double> memData = new LinkedList<>();
	public List<Double> timeData = new LinkedList<>();
	// public double [] memData = new double[10];
	// public int count=0;

	/**
	 * this class represent an item and its utility in a transaction
	 */
	class Pair {

		/**
		 * An item
		 */
		int item = 0;
		/**
		 * The utility of the item in a given transaction
		 */
		int utility = 0;

		/**
		 * Return a String representation of this "pair" object. This is useful for
		 * debugging.
		 *
		 * @return the string representation
		 */
		public String toString() {
			return "[" + item + "," + utility + "]";
		}
	}

	/**
	 * Get the number of HUIs stored in the HUI-trie structure. This is performed by
	 * scanning the HUI-Trie and checking which itemsets are HUIs.
	 *
	 * @return the number of HUIs.
	 */
	public int getRealCHUICount() {
		int count = 0;
		for (List<ItemsetIncCHUI> entryHash : closedTable.table) {
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
		// for(int i = 0; i < closedTable.table.length; i++){
		for (List<ItemsetIncCHUI> entryHash : closedTable.table) {
			if (entryHash == null) {
				continue;
			}
			for (ItemsetIncCHUI itemset : entryHash) {
				// Itemset temp = closedTable.
				writer.write(itemset + "\n");
				// chuidCount++;
			}
		}
		// close the file
		writer.close();
	}

	/**
	 * Default constructor
	 */
	public AlgoIncCHUI() {
	}

	/**
	 * Run the algorithm
	 *
	 * @param input      the input file path
	 * @param minUtility the minimum utility threshold
	 * @param firstline  the first line to be read
	 * @param lastline   the last line to be read
	 * @throws IOException exception if error while writing the file
	 */
	public void runAlgorithm(String input, int minUtil, int firstLine, int lastLine, int hashTableSize)
			throws IOException {
		startTimestamp = System.currentTimeMillis();
		// reset memory usage
		memory = 0;

		// Reset statistics
		// candidateCount = 0;
		chuidCount = 0;

		// writer = new BufferedWriter(new FileWriter("ichui.txt"));
		this.firstLine = firstLine;

		// if first time
		boolean firstTime = (closedTable == null);
		if (firstTime) {
			closedTable = new HashTable(hashTableSize);
			// mapEUCS = new HashMap<Integer, Map<Integer, Integer>>();
//			writer = new BufferedWriter(new FileWriter(output));
			listOfUtilityLists = new LinkedList<UtilityListEIHI>();
			// mapItemToRank = new HashMap<Integer, Integer>();
			mapItemToUtilityList = new HashMap<Integer, UtilityListEIHI>();
			mapItemToTWU = new HashMap<Integer, Integer>();
			totalDBUtility = 0;
		} else {
			// Add utility from previous DP to D if not first time that the algorithm
			// is run
			for (UtilityListEIHI ulist : listOfUtilityLists) {
				ulist.switchDPtoD();
			}
		}

		// create a list to store the utility list of new items so that they can be
		// sorted by TWU order
		// List<UtilityListEIHI> newItemsUtilityLists = new
		// LinkedList<UtilityListEIHI>();
		// We create a map to store the TWU of each item
//        if (mapItemToTWU == null) {
//            mapItemToTWU = new HashMap<Integer, Integer>();
//        }
		// We scan the database a first time to calculate the TWU of each item.
		BufferedReader myInput = null;
		String thisLine;
		long startSkip = 0;
		long endSkip = 0;
		try {
			// prepare the object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			// for each line (transaction) until the end of file
			// int tid = 0;
			startSkip = System.currentTimeMillis();
			for (int i = 0; i < firstLine; i++) {
				myInput.readLine();
			}
			endSkip = System.currentTimeMillis();

			int tid = firstLine;
			while ((thisLine = myInput.readLine()) != null) {
				// if (tid >= firstLine) {
				// if the line is a comment, is empty or is a
				// kind of metadata
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}

				// split the transaction according to the : separator
				String split[] = thisLine.split(":");
				// the first part is the list of items
				String items[] = split[0].split(" ");
				// the second part is the transaction utility
				int transactionUtility = Integer.parseInt(split[1]);
				// the third part is the items' utilies

				String utilityValues[] = split[2].split(" ");

				// for each item, we add the transaction utility to its TWU
				for (int i = 0; i < items.length; i++) {
					// convert item to integer
					Integer item = Integer.parseInt(items[i]);
					// get the current TWU of that item
					Integer twu = mapItemToTWU.get(item);
					// add the utility of the item in the current transaction to its twu
					UtilityListEIHI uList;
					if (twu == null) {
						uList = new UtilityListEIHI(item);
						mapItemToUtilityList.put(item, uList);
						// newItemsUtilityLists.add(uList);
						listOfUtilityLists.add(uList);
						twu = transactionUtility;
					} else {
						twu = twu + transactionUtility;
						uList = mapItemToUtilityList.get(item);
					}
					mapItemToTWU.put(item, twu);
					// get the utility list of this item
					uList.addElementDP(new Element(tid, Integer.parseInt(utilityValues[i]), 0));
				}

				totalDBUtility += transactionUtility;
				// }

				tid++;
				if (tid == lastLine) {
					break;
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

		minUtility = minUtil;

		// Sort the items by TWU
		Collections.sort(listOfUtilityLists, new Comparator<UtilityListEIHI>() {
			public int compare(UtilityListEIHI o1, UtilityListEIHI o2) {
				// compare the TWU of the items
				return compareItems(o1.item, o2.item);
			}
		});

		// update the ru of items appear in D's transaction according to the new TWU
		// order
		// Map<Integer, Integer> TA = new HashMap<Integer, Integer>();
		int[] TA = new int[lastLine];
		Arrays.fill(TA, 0);
		// get the ul of item having largest TWU, then initialize the temp utility array
		// TA
		UtilityListEIHI uli = (UtilityListEIHI) listOfUtilityLists.get(listOfUtilityLists.size() - 1);
		for (Element ele : uli.elementsD) {
			ele.rutils = 0;
			// TA.put(ele.tid, ele.iutils);
			TA[ele.tid] = ele.iutils;
		}

		uli.sumRutilsD = 0;
		for (Element ele : uli.elementsDP) {
			ele.rutils = 0;
			// TA.put(ele.tid, ele.iutils);
			TA[ele.tid] = ele.iutils;
		}

		// re-calculate ru of smaller items
		ListIterator<UtilityListEIHI> li = listOfUtilityLists.listIterator(listOfUtilityLists.size() - 1);
		while (li.hasPrevious()) {
			UtilityListEIHI ul = (UtilityListEIHI) li.previous();
			ul.sumRutilsD = 0;
			for (Element ele : ul.elementsD) {
				ele.rutils = TA[ele.tid];
				ul.sumRutilsD += TA[ele.tid];
				TA[ele.tid] += ele.iutils;

			}
			for (Element ele : ul.elementsDP) {
				ele.rutils = TA[ele.tid];
				ul.sumRutilsDP += TA[ele.tid];
				TA[ele.tid] += ele.iutils;
			}
		}

		TA = null;
		// Runtime.getRuntime().gc();
//        // Remove itemsets of size 1 that do not appear in DP
		List<UtilityListEIHI> listULForRecursion = new LinkedList<UtilityListEIHI>();

		for (UtilityListEIHI temp : listOfUtilityLists) {
			// we keep only utility lists of items in DP temp.sumIutilsDP != 0 &&
			if (mapItemToTWU.get(temp.item) >= minUtility && temp.sumIutilsDP != 0) {
				listULForRecursion.add(temp);
			}
		}

		// Mine the database recursively
		incCHUI(true, new int[0], null, new ArrayList<UtilityListEIHI>(), listULForRecursion);

		// check the memory usage again and close the file.
		checkMemory();
		// memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed()/
		// 1024 / 1024;// (Runtime.getRuntime().totalMemory() -
		// Runtime.getRuntime().freeMemory()) / 1024d / 1024d;
		if (maxMemoryUsed < memory) {
			maxMemoryUsed = memory;
		}

		// closeFile();
		// writer.close();
		// record end time
		endTimestamp = System.currentTimeMillis();
		memData.add(memory);
		timeData.add((endTimestamp - startTimestamp - (endSkip - startSkip)) / 1000.0);
		totalTimeForAllRuns += (endTimestamp - startTimestamp);

		// Runtime.getRuntime().gc();
	}

	/**
	 * This is the recursive method to find all closed high utility itemsets. It
	 * writes the itemsets to the output file.
	 *
	 * @param closedSet       This is the current prefix. Initially, it is empty.
	 * @param closedSetUL     This is the Utility List of the prefix. Initially, it
	 *                        is empty.
	 * @param minUtilityRatio The minUtility threshold.
	 * @throws IOException
	 */
	private void incCHUI(boolean firstTime, int[] closedSet, UtilityListEIHI closedSetUL, List<UtilityListEIHI> preset,
			List<UtilityListEIHI> postset) throws IOException {

		// L2: for all i in postset
		for (UtilityListEIHI iUL : postset) {

			// L4 Calculate the tidset of the new GENERATOR "closedset U {i}"
			UtilityListEIHI newgen_TIDs;
			// if the first time
			if (firstTime) {
				// it is the tidset of it
				newgen_TIDs = iUL;
			} else {
				// otherwise we intersect the tidset of closedset and the
				// tidset of i
				newgen_TIDs = construct(closedSetUL, iUL);
			}
			// if newgen has high utility supersets
			if (isPassingHUIPruning(newgen_TIDs)) {
				// {
				// L3: newgen = closedset U {i}
				// Create the itemset for newgen
				int[] newGen = appendItem(closedSet, iUL.item);

				// L5: if newgen is not a duplicate
				if (is_dup(newgen_TIDs, preset) == false) {
					// L6: ClosedsetNew = newGen
					int[] closedSetNew = newGen;

					// calculate tidset
					UtilityListEIHI closedsetNewTIDs = newgen_TIDs;
					// L7 : PostsetNew = emptyset
					List<UtilityListEIHI> postsetNew = new ArrayList<UtilityListEIHI>();

					// for each item J in the postset
					boolean passedHUIPruning = true;
					for (UtilityListEIHI jUL : postset) {

						// if J is smaller than I according to the total order on items, we skip it
						if (jUL.item == iUL.item || compareItems(jUL.item, iUL.item) < 0) {
							continue;
						}
						// If J does not appear in DP, then we can skip it
						if (jUL.sumIutilsDP == 0) {
							continue;
						}
						// if (precheckContain(jUL, newgen_TIDs) && containsAllTIDS(jUL, newgen_TIDs)) {
						if (containsAllTIDS(jUL, newgen_TIDs)) {
							closedSetNew = appendItem(closedSetNew, jUL.item);
							closedsetNewTIDs = construct(closedsetNewTIDs, jUL);

							if (isPassingHUIPruning(closedsetNewTIDs) == false) {
								passedHUIPruning = false;
								break;
							}
						} else {
							postsetNew.add(jUL);
						}
					}

					if (passedHUIPruning) {
						// L15 : write out Closed_setNew and its support
						if (closedsetNewTIDs.sumIutilsD + closedsetNewTIDs.sumIutilsDP >= minUtility) {
							int hashcode = closedTable.hashCode(closedSetNew);
							ItemsetIncCHUI itemsetRetrieved = closedTable.retrieveItemset(closedSetNew, hashcode);
							if (itemsetRetrieved == null) {
								// put new
								itemsetRetrieved = new ItemsetIncCHUI(closedSetNew,
										closedsetNewTIDs.sumIutilsD + closedsetNewTIDs.sumIutilsDP,
										closedsetNewTIDs.elementsD.size() + closedsetNewTIDs.elementsDP.size());
								closedTable.put(itemsetRetrieved, hashcode);
								chuidCount++;

							} else {
								// update
								itemsetRetrieved.utility = closedsetNewTIDs.sumIutilsD + closedsetNewTIDs.sumIutilsDP;
								itemsetRetrieved.supp = closedsetNewTIDs.elementsD.size()
										+ closedsetNewTIDs.elementsDP.size();
							}
						}

						checkMemory();
						// L16: recursive call
						// FIXED: we have to make a copy of preset before the recursive call
						List<UtilityListEIHI> presetNew = new ArrayList<UtilityListEIHI>(preset);
						incCHUI(false, closedSetNew, closedsetNewTIDs, presetNew, postsetNew);
					}

					// L17 : Preset = Preset U {i}
					preset.add(iUL);
				}
			}
		}
	}

	private boolean containsAllTIDS(UtilityListEIHI ul1, UtilityListEIHI ul2) {
		// for each integer j in preset

		for (Element elmX : ul2.elementsDP) {
			// do a binary search to find element ey in py with tid = ex.tid
			Element elmE = findElementWithTID(ul1.elementsDP, elmX.tid);
			if (elmE == null) {
				return false;
			}
		}
		for (Element elmX : ul2.elementsD) {
			// do a binary search to find element ey in py with tid = ex.tid
			Element elmE = findElementWithTID(ul1.elementsD, elmX.tid);
			if (elmE == null) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Method to compare items by their TWU
	 *
	 * @param item1 an item
	 * @param item2 another item
	 * @return 0 if the same item, >0 if item1 is larger than item2, <0 otherwise
	 */
	private int compareItems(int item1, int item2) {
		// int compare = mapItemToTWU.get(item1) - mapItemToTWU.get(item2);
		int value = Integer.compare(mapItemToTWU.get(item1), mapItemToTWU.get(item2));
		// if the same, use the lexical order otherwise use the TWU
		// return (compare == 0) ? item1 - item2 : compare;
		if (value != 0) {
			return value;
		} else {
			return item1 - item2;
		}
	}

	private UtilityListEIHI construct(UtilityListEIHI uX, UtilityListEIHI uE) {

		// create an empy utility list for pXY
		UtilityListEIHI uXE = new UtilityListEIHI(uE.item);

		for (Element ex : uX.elementsDP) {
//            // do a binary search to find element ey in py with tid = ex.tid
			Element ey = findElementWithTID(uE.elementsDP, ex.tid);
			if (ey == null) {
				continue;
			}
//            
			// Create new element
			// Element eXY = new Element(ex.tid, ex.iutils + ey.iutils - ey.iutils,
			// ey.rutils);
			Element eXY = new Element(ex.tid, ex.iutils + ey.iutils, ex.rutils - ey.iutils);
			// Element elmXe = new Element(elmX.tid, elmX.iutils + elmE.iutils, elmX.rutils
			// - elmE.iutils);
			// add the new element to the utility list of pXY
			uXE.addElementDP(eXY);

		}
		// PRUNING: IF THERE IS NO ELEMENT IN DP, WE DON'T NEED TO CONTINUE
		if (uXE.elementsDP.isEmpty()) {
			return null;
		}

		// for each element in the utility list of pX
		for (Element elmX : uX.elementsD) {
			// do a binary search to find element ey in py with tid = ex.tid
			Element elmE = findElementWithTID(uE.elementsD, elmX.tid);
			if (elmE == null) {
				continue;
			}
			// Create the new element
			Element elmXe = new Element(elmX.tid, elmX.iutils + elmE.iutils, elmX.rutils - elmE.iutils);
			// add the new element to the utility list of pXY
			uXE.addElementD(elmXe);
		}
		return uXE;
	}

	/**
	 * Do a binary search to find the element with a given tid in a utility list
	 *
	 * @param ulist the utility list
	 * @param tid   the tid
	 * @return the element or null if none has the tid.
	 */
	private Element findElementWithTID(List<Element> list, int tid) {
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

	/**
	 * Method to check the memory usage and keep the maximum memory usage.
	 */
	private void checkMemory() {

		// get the current memory usage
		double currentMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024d / 1024d;
		// if higher than the maximum until now
		if (currentMemory > memory) {
			// replace the maximum with the current memory usage
			memory = currentMemory;
		}
	}

	/**
	 * Print statistics about the latest execution to System.out.
	 *
	 * @throws IOException
	 */
	public void printStats() throws IOException {
		System.out.println("=============  ICHUI ALGORITHM - STATS =============");
		System.out.println(" Execution time ~ " + (endTimestamp - startTimestamp) + " ms");

		System.out.println(" Memory ~ " + memory + " MB");
		System.out.println(" New closed High-utility itemsets found : " + chuidCount);
		System.out.println("TOTAL TIME FOR ALL RUNS: " + (totalTimeForAllRuns / 1000.0) + " sec");
		System.out.println("===================================================");

	}

	/**
	 * Check the HUI pruning condition of HUI-Miner for the utilitylist of an
	 * itemset
	 *
	 * @param utilitylist the utility list of an itemset
	 * @return true if it passes the pruning condition. Otherwise false.
	 */
	private boolean isPassingHUIPruning(UtilityListEIHI utilitylist) {
		return (utilitylist != null) && ((utilitylist.sumIutilsD + utilitylist.sumIutilsDP + utilitylist.sumRutilsD
				+ utilitylist.sumRutilsDP) >= minUtility);//

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
	 * The method "is_dup" as described in the paper.
	 *
	 * @param newgenTIDs the tidset of newgen
	 * @param preset     the itemset "preset"
	 */
	private boolean is_dup(UtilityListEIHI newgenTIDs, List<UtilityListEIHI> preset) {
		// L25
		// for each integer j in preset
		for (UtilityListEIHI j : preset) {

			// for each element in the utility list of pX
			boolean containsAllinDP = true;
			boolean containsAllinD = true;
//            if (precheckContain(j, newgenTIDs) == false) {
//                //containsAll = false;
//                continue;
//            }
			for (Element elmX : newgenTIDs.elementsDP) {
				// do a binary search to find element ey in py with tid = ex.tid
				Element elmE = findElementWithTID(j.elementsDP, elmX.tid);
				if (elmE == null) {
					containsAllinDP = false;
					break;
				}
			}
			for (Element elmX : newgenTIDs.elementsD) {
				// do a binary search to find element ey in py with tid = ex.tid
				Element elmE = findElementWithTID(j.elementsD, elmX.tid);
				if (elmE == null) {
					containsAllinD = false;
					break;
				}
			}

			// L26 :
			// If tidset of newgen is included in tids of j, return true
			if (containsAllinD && containsAllinDP) {
				return true;
			}
		}
		return false; // NOTE THAT IN ORIGINAL PAPER THEY WROTE TRUE, BUT IT SHOULD BE FALSE
	}

}
