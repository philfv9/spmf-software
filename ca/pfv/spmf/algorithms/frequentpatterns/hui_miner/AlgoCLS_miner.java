package ca.pfv.spmf.algorithms.frequentpatterns.hui_miner;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/* This file is copyright (c) 
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

/**
 * Implementation of the CLS-Miner algorithm for mining closed high utility
 * itemsets. This algorithm was proposed in this paper: <br/>
 * * <br/>
 * 
 * Dam T L, Li K, Fournier-Viger P, et al. CLS-Miner: efficient and effective
 * closed high-utility itemset mining[J]. Frontiers of Computer Science, 2019,
 * 13(2): 357-381., https://doi.org/10.1007/s11704-016-6245-4.
 * 
 * The code was obtained from Github repository of user "limuhangk" under the
 * GPL license, since the code is derived from GPL code from SPMF.
 * 
 * @author limuhangk
 *
 */
public class AlgoCLS_miner {

	/**
	 * the time the algorithm started
	 */
	public long startTimestamp = 0;
	/**
	 * the time the algorithm terminated
	 */
	public long endTimestamp = 0;

	/**
	 * the number of CHUI generated
	 */
	public int chuidCount = 0;

	/**
	 * the number of candidates considered
	 */
	public int candidateCount = 0;

	/**
	 * Map to remember the TWU of each item
	 */
	Map<Integer, Integer> mapItemToTWU;

	/**
	 * Writer object to write to the output file if the user choose to
	 */
	BufferedWriter writer = null;

	/**
	 * The minimum utility threshold
	 */
	int minUtility = 0;
	int count1 = 0;
	int count2 = 0;
	/**
	 * OPTIMIZATION SPECIFIC TO CHUIMINER. We integrate the structure used by the
	 * EUCP strategy proposed in FHM (Fournier-Viger et al., 2014) for pruning
	 * candidates. Key: for an item x, Value: a map of item where key is an item y
	 * and value is the TWU of {x,y}
	 */
	Map<Integer, Map<Integer, Integer>> mapFMAP; // PAIR OF ITEMS , item --> item, twu
	Map<Integer, List<Integer>> Cov; // PAIR OF ITEMS , item --> item, coverage
	// ======================================================
	// ===== STRUCTURE TO STORE CHUIs IN MEMORY IF THE USER CHOOSE TO
	// ======================================================

	/**
	 * The set of closed high utility itemsets (CHUIs) ordered by their support and
	 * then by their size. It is implemented as a list of list. In the first list,
	 * the i-th position contains the list of itemsets containing i items ordered by
	 * their support.
	 */
	List<List<Itemset>> listItemsetsBySize = null;

	/**
	 * The set of items appearing in CHUIs. This information is used by the
	 * GHUI-Miner algorithm when it is applied after this algorithm.
	 */
	public Set<Integer> setOfItemsInClosedItemsets = null;

	/**
	 * Save a closed high utility itemset to memory
	 *
	 * @param itemset   the itemset
	 * @param sumIutils its utility
	 * @param support   its support
	 */
	public void saveToMemory(int[] itemset, long sumIutils, int support) {
		// if the itemset is larger than the largest CHUI found until now
		if (itemset.length >= listItemsetsBySize.size()) {
			// create some new list in the structure for storing CHUIs to store
			// the itemset
			int i = listItemsetsBySize.size();
			while (i <= itemset.length) {
				listItemsetsBySize.add(new ArrayList<Itemset>());
				i++;
			}
		}
		// add the itemset to the list of CHUIs having the same size
		List<Itemset> listToAdd = listItemsetsBySize.get(itemset.length);
		listToAdd.add(new Itemset(itemset, sumIutils, support));

		// Add all items from that CHUI to the set of items from CHUIs
		for (int item : itemset) {
			setOfItemsInClosedItemsets.add(item);
		}
	}

	// ======================================================
	// ======================================================

	/**
	 * Determine if the EUCP strategy will be used
	 */
	boolean useChain_EUCP;
	boolean useCoverage;
	boolean useLBP;
	boolean usePreCheck;

	/**
	 * Default constructor
	 */
	public AlgoCLS_miner(boolean useChain_EUCP, boolean useCoverage, boolean useLBP, boolean usePreCheck) {
		this.useChain_EUCP = useChain_EUCP;
		this.useCoverage = useCoverage;
		this.useLBP = useLBP;
		this.usePreCheck = usePreCheck;
	}

	/**
	 * Run the algorithm and save the result to file or to memory
	 *
	 * @param input      the input file path
	 * @param output     the output file path
	 * @param minUtility the minimum utility threshold
	 * @param output     the output file path or null if the user wants to keep
	 *                   result in memory
	 * @return the set of CHUIs if the user chose to save the result to memory
	 * @throws IOException exception if error while writing the file
	 */
	public List<List<Itemset>> runAlgorithm(String input, int minUtility, String output) throws IOException {
		// reset maximum
		MemoryLogger.getInstance().reset();

		// keep the min-util threshold
		this.minUtility = minUtility;

		// initialize object to write output file if the user chose to write the output
		// to file
		if (output != null) {
			writer = new BufferedWriter(new FileWriter(output));
		} else {
			// If the user chose to save to memory, we initialize the structures
			// for storing CHUIs into memory.
			listItemsetsBySize = new ArrayList<List<Itemset>>();
			setOfItemsInClosedItemsets = new HashSet<Integer>();
		}

		// Initialize the structure for EUCP strategy that is not included in
		// the original CHUIMiner algorithm but is included here to
		// improve performances.
		if (useChain_EUCP) {
			mapFMAP = new HashMap<Integer, Map<Integer, Integer>>();
		}
		if (useCoverage) {
			Cov = new HashMap<Integer, List<Integer>>();// 初始化 COV结果
		}
		// record the start time of the algorithm
		startTimestamp = System.currentTimeMillis();

		// We create a map to store the TWU of each item
		mapItemToTWU = new HashMap<Integer, Integer>();

		// We scan the database a first time to calculate the TWU of each item.
		BufferedReader myInput = null;
		String thisLine;
		try {
			// prepare the object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
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
				// for each item, we add the transaction utility to its TWU
				for (int i = 0; i < items.length; i++) {
					// convert item to integer
					Integer item = Integer.parseInt(items[i]);
					// get the current TWU of that item
					Integer twu = mapItemToTWU.get(item);
					// add the utility of the item in the current transaction to its twu
					twu = (twu == null) ? transactionUtility : twu + transactionUtility;
					mapItemToTWU.put(item, twu);
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

		// CREATE A LIST TO STORE THE UTILITY LIST OF ITEMS WITH TWU >= MIN_UTILITY.
		List<UtilityList> listOfUtilityLists = new ArrayList<UtilityList>();
		// CREATE A MAP TO STORE THE UTILITY LIST FOR EACH ITEM.
		// Key : item Value : utility list associated to that item
		Map<Integer, UtilityList> mapItemToUtilityList = new HashMap<Integer, UtilityList>();

		// For each item
		for (Entry<Integer, Integer> theEntry : mapItemToTWU.entrySet()) {
			Integer item = theEntry.getKey();

			// if the item is promising (TWU >= minutility)
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
			public int compare(UtilityList o1, UtilityList o2) {
				// compare the TWU of the items
				return compareItems(o1.item, o2.item);
			}
		});

		// SECOND DATABASE PASS TO CONSTRUCT THE UTILITY LISTS
		// OF 1-ITEMSETS HAVING TWU >= minutil (promising items)
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
				tid++; // increase tid number for next transaction
				// split the line according to the separator
				String split[] = thisLine.split(":");
				// get the list of items
				String items[] = split[0].split(" ");
				// get the list of utility values corresponding to each item
				// for that transaction
				String utilityValues[] = split[2].split(" "); 

				int newTU = 0;

				// Create a list to store items
				List<PairItemUtility> revisedTransaction = new ArrayList<PairItemUtility>();
				// for each item
				for (int i = 0; i < items.length; i++) {
					/// convert values to integers
					PairItemUtility pair = new PairItemUtility();
					pair.item = Integer.parseInt(items[i]);
					pair.utility = Integer.parseInt(utilityValues[i]);
					// if the item has enough utility
					if (mapItemToTWU.get(pair.item) >= minUtility) {
						// add it
						revisedTransaction.add(pair);
						newTU += pair.utility; // NEW OPTIMIZATION
					}
				}

				// sort the transaction
				Collections.sort(revisedTransaction, new Comparator<PairItemUtility>() {
					public int compare(PairItemUtility o1, PairItemUtility o2) {
						return compareItems(o1.item, o2.item);
					}
				});

				// This variable will be used to calculate the remaining utility
				// in a transaction after the some items have been processed
				int remainingUtility = newTU;

				// for each item left in the transaction
				for (int i = 0; i < revisedTransaction.size(); i++) {
					PairItemUtility pair = revisedTransaction.get(i);

					// subtract the utility of this item from the remaining utility
					remainingUtility = remainingUtility - pair.utility;

					// get the utility list of this item
					UtilityList utilityListOfItem = mapItemToUtilityList.get(pair.item);

					// Add a new Element to the utility list of this item corresponding to this
					// transaction
					Element element = new Element(tid, pair.utility, remainingUtility);

					utilityListOfItem.addElement(element);

					// BEGIN CODE for updating the structure used
					// BY THE EUCP STRATEGY INTRODUCED IN CHUIMiner
					if (useChain_EUCP) {
						Map<Integer, Integer> mapFMAPItem = mapFMAP.get(pair.item);
						if (mapFMAPItem == null) {
							mapFMAPItem = new HashMap<Integer, Integer>();
							mapFMAP.put(pair.item, mapFMAPItem);
						}

						for (int j = i + 1; j < revisedTransaction.size(); j++) {
							PairItemUtility pairAfter = revisedTransaction.get(j);
							Integer twuSum = mapFMAPItem.get(pairAfter.item);
							if (twuSum == null) {
								mapFMAPItem.put(pairAfter.item, newTU);
							} else {
								mapFMAPItem.put(pairAfter.item, twuSum + newTU);
							}
						}
					}
					// END OF CODE FOR EUCP STRATEGY

				}
//                tid++; // increase tid number for next transaction

			}
		} catch (Exception e) {
			// to catch error while reading the input file
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}

		// L5 Call CoverageConstruct();
		if (useCoverage) {
			CoverageConstructProcedure(listOfUtilityLists);
		}
		// check the memory usage
		MemoryLogger.getInstance().checkMemory();

		// Mine the database recursively
		// (3) CALL THE recursive procedure
		CLS_Miner(true, new int[0], null, new ArrayList<UtilityList>(), listOfUtilityLists);

		// check the memory usage again and close the file.
		MemoryLogger.getInstance().checkMemory();

		// close output file if the user chose to save output to file
		if (writer != null) {
			writer.close();
		}

		// record end time
		endTimestamp = System.currentTimeMillis();
		return listItemsetsBySize;
	}

	/**
	 * This is the recursive method to find all high utility itemsets. It writes the
	 * itemsets to the output file.
	 *
	 * @param closedSet   This is the current prefix. Initially, it is empty.
	 * @param closedSetUL This is the Utility List of the prefix. Initially, it is
	 *                    empty.
	 * @param firstTime   The minUtility threshold.
	 * @throws IOException
	 */
	private void CLS_Miner(boolean firstTime, int[] closedSet, UtilityList closedSetUL, List<UtilityList> preset,
			List<UtilityList> postset) throws IOException {

		// L2: for all i in postset
		for (UtilityList iUL : postset) {
			// L4 Calculate the tidset of the new GENERATOR "closedset U {i}"
			UtilityList newgen_TIDs;
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
				// L3: newgen = closedset U {i}
				// Create the itemset for newgen
				int[] newGen = appendItem(closedSet, iUL.item);

				// L5: if newgen is not a duplicate #use improved isSubsumedCheck //use precheck
				// method
				if (improved_is_dup(newgen_TIDs, preset) == false) {
					// L6: ClosedsetNew = newGen
					int[] closedSetNew = newGen;

					// calculate tidset
					UtilityList closedsetNewTIDs = newgen_TIDs;

//					// L7 : PostsetNew = emptyset
					List<UtilityList> postsetNew = new ArrayList<UtilityList>();

					// for each item J in the postset
					boolean passedHUIPruning = true;
					for (UtilityList jUL : postset) {
						// if J is smaller than I according to the total order on items, we skip it
						if (jUL.item.equals(iUL.item) || compareItems(jUL.item, iUL.item) < 0) {
							continue;
						}
						// L9 if(con(Px,y)<minutil) continue; con(X,y) is the Conjunction upper-bound
						// utility LBP 使用EUCS结构时，剪枝效果不太明显。。。
						if (useLBP && calculate_Con(newgen_TIDs, jUL) < minUtility) {
							continue;
						}
						// L12 if(∃i∈px such that EUCS(i,y)<minutil) continue; Generalized EUCP property
						// ---first strategy
						if (useChain_EUCP) {
							boolean shouldpassEUCS = false;
//                            for (Integer i = closedSetNew.length - 1; i >= 0; i--) {
							for (Integer i = 0; i < closedSetNew.length; i++) {
								shouldpassEUCS = checkGenEUCPStrategy(closedSetNew[i], jUL.item);
								if (shouldpassEUCS)
									break;
							}
							if (shouldpassEUCS) {
								count1++;
								continue;
							}
						}
						// ======================== CHECK THE EUCP STRATEGY
						// ======================= WHICH WAS INTRODUCED IN FHM (Fournier-Viger et al,
						// 2014)
//                        boolean shouldPrune = useChain_EUCP && checkEUCPStrategy(iUL.item, jUL.item);
//                        if (shouldPrune) {
////							System.out.println("PRUNE");
//                              count2++;
//                            continue;
//                        }
//                        candidateCount++;
						// =========================== END OF CODE FOR EUCP STRATEGY

						// L15 if(y∈Cov(x) OR (preCheckContain(Px,y) AND TidSet(Px)⊆TidSet(y))) Coverage
						// concept ---second strategy
						if (usePreCheck || useCoverage) {
							if (useCoverage && ifBelongToCov(iUL.item, jUL.item) || usePreCheck
									&& (preCheckContain(jUL, newgen_TIDs) && containsAllTIDS(jUL, newgen_TIDs))) {
								closedSetNew = appendItem(closedSetNew, jUL.item);
								closedsetNewTIDs = construct(closedsetNewTIDs, jUL);
								if (isPassingHUIPruning(closedsetNewTIDs) == false) {
									passedHUIPruning = false;
									break;
								}
							} else {
								postsetNew.add(jUL);
							}
						} else {
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
						candidateCount++;
					}

					if (passedHUIPruning) {
						// L15 : write out Closed_setNew and its support
						if (closedsetNewTIDs.sumIutils >= minUtility) {
							saveCHUI(closedSetNew, closedsetNewTIDs.sumIutils, closedsetNewTIDs.elements.size());
						}

						// L16: recursive call
						// FIXED: we have to make a copy of preset before the recursive call
						List<UtilityList> presetNew = new ArrayList<UtilityList>(preset);
						CLS_Miner(false, closedSetNew, closedsetNewTIDs, presetNew, postsetNew);
					}

					// L17 : Preset = Preset U {i}
					preset.add(iUL);
				}
			}
		}

	}

	/**
	 * Check the HUI pruning condition of HUI-Miner for the utilitylist of an
	 * itemset
	 *
	 * @param utilitylist the utility list of an itemset
	 * @return true if it passes the pruning condition. Otherwise false.
	 */
	private boolean isPassingHUIPruning(UtilityList utilitylist) {
		return utilitylist.sumIutils + utilitylist.sumRutils >= minUtility;
	}

	/**
	 * Check if a utility list contains all tids from another utility list
	 *
	 * @param ul1 the first utility list
	 * @param ul2 the second utility list
	 * @return true if it contains all tids, otherwise false.
	 */
	private boolean containsAllTIDS(UtilityList ul1, UtilityList ul2) {
		// for each integer j in preset
		for (Element elmX : ul2.elements) {
			// do a binary search to find element ey in py with tid = ex.tid
			Element elmE = findElementWithTID(ul1, elmX.tid);
			if (elmE == null) {
				return false;
			}
		}
		return true;
	}

	/**
	 * This method performs the EUCP pruning from the FHM algorithm (see FHM paper
	 * at ISMIS 2014)
	 *
	 * @param itemX an item X
	 * @param itemY an item Y
	 * @return true if TWU({x,y} < minutil. Otherwise return false
	 */
	private boolean checkEUCPStrategy(int itemX, int itemY) {
		Map<Integer, Integer> mapTWUF = mapFMAP.get(itemX);
		if (mapTWUF != null) {
			Integer twuF = mapTWUF.get(itemY);
			if (twuF == null || twuF < minUtility) {
				return true;
			}
		}
		return false;
	}

	private boolean checkGenEUCPStrategy(int itemX, int itemY) {
		int temp = 0;
		if (compareItems(itemX, itemY) > 0) {
			temp = itemX;
			itemX = itemY;
			itemY = temp;
		}
		Map<Integer, Integer> mapTWUF = mapFMAP.get(itemX);
		if (mapTWUF != null) {
			Integer twuF = mapTWUF.get(itemY);
			if (twuF == null || twuF < minUtility) {
				return true;
			}
		}
		return false;
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

	/*
	 * LBP strategy 计算conjunction upper bound utility
	 */
	private int calculate_Con(UtilityList X, UtilityList Y) {
		int Con = (int) (X.sumIutils + X.sumRutils - cdiff(X, Y) * getMinValueofUL(X));
		return Con;
	}

	/*
	 * Tidset difference diff(X, Y) #支持度之差（绝对值）
	 */
	private int Tidset_diff(UtilityList X, UtilityList Y) {
		int diff = Math.abs(X.getSupport() - Y.getSupport());
		return diff;
	}

	/*
	 * cdiff(X, Y) #计算cdiff(X, Y)
	 */
	private int cdiff(UtilityList X, UtilityList Y) {
//        int diffXY = 0;
//        if (X.getSupport() < Y.getSupport())
//            diffXY = X.getSupport();
//        else
//            diffXY = Y.getSupport();
//        int cdiff = X.getSupport() - diffXY;
//        return cdiff;
		if (X.getSupport() < Y.getSupport())
			return 0;
		else
			return X.getSupport() - Y.getSupport();
	}

	/*
	 * ULmin(X) #一个项集X的效用列表中，效用+剩余效用最小的元组的iutil+rutil值
	 */
	private int getMinValueofUL(UtilityList X) {
		int minValue = X.elements.get(0).iutils + X.elements.get(0).rutils;
		for (Element element : X.elements) {
			if (element.iutils + element.rutils < minValue)
				minValue = element.iutils + element.rutils;
		}
		return minValue;
	}

	/**
	 * 在执行包含性检查之前执行时那个预检查条件，满足三个条件之后在做包含性检查，否则不执行包含性检查操作 The improved
	 * isSubsumedCheck procedure The method "is_dup" as described in the paper.
	 *
	 * @param newgenTIDs the tidset of newgen
	 * @param preset     the itemset "preset"
	 */
	private boolean improved_is_dup(UtilityList newgenTIDs, List<UtilityList> preset) {
		// L25
		// for each integer j in preset
		for (UtilityList j : preset) {
			if (preCheckContain(j, newgenTIDs) == true) {
				// for each element in the utility list of pX
				boolean containsAll = true;
				for (Element elmX : newgenTIDs.elements) {
					// do a binary search to find element ey in py with tid = ex.tid
					Element elmE = findElementWithTID(j, elmX.tid);
					if (elmE == null) {
						containsAll = false;
						break;
					}
				}
				// L26 :
				// If tidset of newgen is included in tids of j, return true
				if (containsAll) {
					// IMPORTANT
					// NOTE THAT IN ORIGINAL PAPER THEY WROTE FALSE, BUT IT SHOULD BE TRUE
					return true;
				}
			}
		} // End for
		return false; // NOTE THAT IN ORIGINAL PAPER THEY WROTE TRUE, BUT IT SHOULD BE FALSE
	}

	/*
	 * PreCheckContain(X,Y) Procedure
	 */
	private boolean preCheckContain(UtilityList X, UtilityList Y) {
		int lenX = X.getSupport();// L1 get the size of TidSet(X)
		int lenY = Y.getSupport();// L2 get the size of TidSet(Y)
		if (lenX < lenY)// first condition
			return false;
		for (int i = 0; i < lenY; i++) {// lenY 不是lenX，lenY好像是短一点
			if (X.elements.get(i).tid > Y.elements.get(i).tid)// second condition
				return false;
			if (X.elements.get(lenX - i - 1).tid < Y.elements.get(lenY - i - 1).tid)// third condition
				return false;
		}
		return true;
	}

	private boolean is_dup(UtilityList newgenTIDs, List<UtilityList> preset) {
		// L25
		// for each integer j in preset
		for (UtilityList j : preset) {

			// for each element in the utility list of pX
			boolean containsAll = true;
			for (Element elmX : newgenTIDs.elements) {
				// do a binary search to find element ey in py with tid = ex.tid
				Element elmE = findElementWithTID(j, elmX.tid);
				if (elmE == null) {
					containsAll = false;
					break;
				}
			}

			// L26 :
			// If tidset of newgen is included in tids of j, return true
			if (containsAll) {
				// IMPORTANT
				// NOTE THAT IN ORIGINAL PAPER THEY WROTE FALSE, BUT IT SHOULD BE TRUE
				return true;
			}
		}
		return false; // NOTE THAT IN ORIGINAL PAPER THEY WROTE TRUE, BUT IT SHOULD BE FALSE
	}

	/**
	 * This method constructs the utility list of Xe
	 *
	 * @param uX : the utility list of X
	 * @param uE : the utility list of e
	 */
	private UtilityList construct(UtilityList uX, UtilityList uE) {

		// create an empy utility list for pXY
		UtilityList uXE = new UtilityList(uE.item);

		// for each element in the utility list of pX
		for (Element elmX : uX.elements) {
			// do a binary search to find element ey in py with tid = ex.tid
			Element elmE = findElementWithTID(uE, elmX.tid);
			if (elmE == null) {
				continue;
			}
			// Create the new element
			// IMPORTANT : TRICKY PART : WE NEED TO SUBTRACT ELMX.RUTIL - ELME.iutil
			// THIS IS BECAUSE DCI DOES NOT ADD ITEMS TO AN ITEMSET ACCORDING TO THE TOTAL
			// ORDER
			Element elmXe = new Element(elmX.tid, elmX.iutils + elmE.iutils, elmX.rutils - elmE.iutils);
			// add the new element to the utility list of pXY
			uXE.addElement(elmXe);
		}
		// return the utility list of Xe.
		return uXE;
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

	/**
	 * binary search search a value in a list
	 */
	private int binarySearch(List list, int value) {

		// perform a binary search to check if the subset appears in level k-1.
		int first = 0;
		int last = list.size() - 1;

		// the binary search
		while (first <= last) {
			int middle = (first + last) >>> 1; // divide by 2

			if ((int) list.get(middle) < value) {
				first = middle + 1; // the itemset compared is larger than the subset according to the lexical order
			} else if ((int) list.get(middle) > value) {
				last = middle - 1; // the itemset compared is smaller than the subset is smaller according to the
									// lexical order
			} else {
				return (int) list.get(middle);
			}
		}
		return -1;
	}

	/**
	 * The Coverage Construct Procedure Input: The set of items I Output: The
	 * coverage of each item in I
	 */
	private void CoverageConstructProcedure(List<UtilityList> listOfUtilityLists) {
		int EUCSXY = 0;
		for (UtilityList ulX : listOfUtilityLists) {
			Integer itemX = ulX.item;
			Map<Integer, Integer> mapTWUF = mapFMAP.get(itemX);
			List<Integer> listofX = new ArrayList<Integer>();
			for (UtilityList uly : listOfUtilityLists) {
				Integer itemY = uly.item;
				if (itemX.equals(itemY) || compareItems(itemY, itemX) < 0) {
					continue;
				}
				if (mapTWUF != null && mapTWUF.containsKey(itemY)) {
					EUCSXY = mapTWUF.get(itemY);
					if (EUCSXY == mapItemToTWU.get(itemX))
						listofX.add(itemY);
				}
			}
			Cov.put(itemX, listofX);
		}
	}

	/*
	 * 判断一个项y是否属于项x的Cov
	 */
	private boolean ifBelongToCov(int x, int y) {

//        for (Integer item : Cov.get(x)) {
//            if (y == item.intValue())
//                return true;
//        }
		if (Cov.get(x).contains(y))
			return true;
		return false;
	}

	/**
	 * Method to write a high utility itemset to the output file or memory.
	 *
	 * @param itemset   prefix to be writent o the output file
	 * @param support   item to be appended to the prefix
	 * @param sumIutils the utility of the prefix concatenated with the item
	 */
	private void saveCHUI(int[] itemset, long sumIutils, int support) throws IOException {
		chuidCount++; // increase the number of high utility itemsets found

		// if the user chose to save to memory
		if (writer == null) {
			saveToMemory(itemset, sumIutils, support);
		} else {
			// If the user decide to save to file
			// Create a string buffer
			StringBuilder buffer = new StringBuilder();
			// append the prefix
			for (int i = 0; i < itemset.length; i++) {
				buffer.append(itemset[i]);
				buffer.append(' ');
			}

			// append the support value
			buffer.append("#SUP: ");
			buffer.append(support);
			// append the utility value
			buffer.append(" #UTIL: ");
			buffer.append(sumIutils);
			// write to file
			writer.write(buffer.toString());
			writer.newLine();
		}
	}

	/**
	 * Print statistics about the latest execution to System.out.
	 *
	 * @throws IOException
	 */
	public void printStats() throws IOException {

		System.out.println("=============  CLS-Miner ALGORITHM SPMF 1.0 - STATS =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println(" Closed High-utility itemsets count : " + chuidCount);
		System.out.println(" Candidate count : " + candidateCount);
//        System.out.println("  GenEUCScount1 : " + count1);
//        System.out.println("  EUCScount2 : " + count2);
		System.out.println("=====================================================");
	}
	

	/**
	 * Method to compare items by their TWU
	 *
	 * @param item1 an item
	 * @param item2 another item
	 * @return 0 if the same item, >0 if item1 is larger than item2, <0 otherwise
	 */
	private int compareItems(int item1, int item2) {
		int compare = mapItemToTWU.get(item1) - mapItemToTWU.get(item2);
		// if the same, use the lexical order otherwise use the TWU
		return (compare == 0) ? item1 - item2 : compare;
	}
}
