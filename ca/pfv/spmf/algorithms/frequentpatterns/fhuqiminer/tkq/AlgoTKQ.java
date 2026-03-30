/* This file is copyright (c) 2021 Mourad Nouioua et al.
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
package ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.tkq;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.EnumCombination;
import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.QItemTrans;
import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.Qitem;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * Implementation of the CHUQI-Miner algorithm for mining quantitative high
 * utility itemsets. This algorithm is presented in this paper: <br/>
 * <br/>
 * Nouioua, M., Fournier-Viger, P., Gan, W., Wu, Y., Lin, J. C.-W., Nouioua, F.
 * (2021). TKQ: Top-K Quantitative High Utility Itemset Mining. Proc. 16th
 * Intern. Conference on Advanced Data Mining and Applications (ADMA 2021)
 * Springer LNAI, 12 pages
 * 
 * @author Mourad Nouioua, copyright 2021
 */
public class AlgoTKQ {

	/** Output file path */
	public String outputFile;

	/** Input file path */
	public String inputDatabase;

	/** Object to write results to file */
	private BufferedWriter writer_hqui = null;

	// Maps
	/** map of a qitem to its TWU */
	private Hashtable<Qitem, Integer> mapItemToTwu;
	/** map of an item to its profit */
	private Hashtable<Integer, Integer> mapItemToProfit;
	/** map of transasction to its utility */
	private Hashtable<Integer, Integer> mapTransactionToUtility;

	/** map of an item to its FMAP */
	private Map<Qitem, Map<Qitem, InfoTKQ>> mapFMAP;

//For raising strategies
	/** map qitem to real utility */
	private Map<Qitem, Long> realUtility = new HashMap<Qitem, Long>();
	/** Map for the CUD strategy */
	private Map<String, Long> CUD = new HashMap<String, Long>();

//Algorithm Parameters
	/** Internal minimum utility threshold */
	public long minUtil;
	/** coefficient */
	private int coefficient;
	/** the type of combining method */
	private EnumCombination combiningMethod;
	/** the top-k patterns */
	private PriorityQueue<Qitemset> kPatterns = new PriorityQueue<Qitemset>();
	/** the parameter k */
	private int k;

//For Evaluation
	/** maximum memory usage */
	public long maxMemory = 0;
	/** start time */
	private long startTime;

	/** end time */
	private long endTime;

	/** number of patterns */
	private int HUQIcount = 0;

	/** number of construction operations */
	private int countConstruct = 0;
	/** the current q-item */
	private Qitem currentQitem;

	/** the size of a temporary buffer for storing itemsets */
	private final int BUFFERS_SIZE = 200;

	/** a temporary buffer for storing itemsets */
	private Qitem[] itemsetBuffer = null;

	/** if true, display debug information */
	private final boolean DEBUG_MODE = true;

	/**
	 * Constructor
	 */
	public AlgoTKQ() {
	}

	/**
	 * Run the algorithm
	 * 
	 * @param k                 the number of patterns to find
	 * @param inputData         path to the input data
	 * @param inputProfit       path to the profit information of each item
	 * @param coef              coefficient
	 * @param combinationmethod the combination method CombineMin, CombineMax,
	 *                          CombineAll)
	 * @param outputPath        the output file path
	 * @throws IOException if exception while reading or writing to file
	 */
	public void runAlgorithm(int topk, String inputData, String inputProfit, int coef,
			EnumCombination combinationmethod, String output) throws IOException {

		System.gc();

		// Initialization
		MemoryLogger.getInstance().reset();
		startTime = System.currentTimeMillis();

		k = topk;
		writer_hqui = new BufferedWriter(new FileWriter(output));
		coefficient = coef;
		combiningMethod = combinationmethod;

		itemsetBuffer = new Qitem[BUFFERS_SIZE];
		mapItemToProfit = new Hashtable<Integer, Integer>();
		mapTransactionToUtility = new Hashtable<Integer, Integer>();
//		totalU = 0;

		ArrayList<Qitem> qitemNameList = new ArrayList<Qitem>();
		Hashtable<Qitem, UtilityListTKQ> mapItemToUtilityList = new Hashtable<Qitem, UtilityListTKQ>();

		if (DEBUG_MODE)
			System.out.println("1. Build Initial Q-Utility Lists");
		buildInitialQUtilityLists(inputData, inputProfit, qitemNameList, mapItemToUtilityList);
		MemoryLogger.getInstance().checkMemory();

		if (DEBUG_MODE)
			System.out.println("2. Find Initial High Utility Range Q-items");
		ArrayList<Qitem> candidateList = new ArrayList<Qitem>();
		ArrayList<Qitem> hwQUI = new ArrayList<Qitem>();
		findInitialRHUQIs(qitemNameList, mapItemToUtilityList, candidateList, hwQUI);
		MemoryLogger.getInstance().checkMemory();

		if (DEBUG_MODE)
			System.out.println("3. Recursive Mining Procedure");
		miner(itemsetBuffer, 0, null, mapItemToUtilityList, qitemNameList, writer_hqui, hwQUI);
		MemoryLogger.getInstance().checkMemory();
		endTime = System.currentTimeMillis();
		if (DEBUG_MODE)
			System.out.println("4. Finished mining. The final internal minUtil value is: " + minUtil);
		writeResultTofile();

		writer_hqui.close();
	}

	/**
	 * Insert an itemset in the top-k patterns until now
	 * 
	 * @param prefix  the prefix of the itemset
	 * @param length  the length of the prefix
	 * @param item    the last item of the itemset
	 * @param utility the utility of the itemset
	 */
	private void insert(Qitem[] prefix, int length, Qitem item, long utility) {
		Qitemset temp =  new Qitemset(prefix, length, item, utility);
		kPatterns.add(temp);

		// if the size becomes larger than k
		if (kPatterns.size() > k) {

			// if the utility of the pattern that we haved added is higher than
			// the minimum utility, we will need to take out at least one rule
			if (utility >= minUtil) {
				// we recursively remove the pattern having the lowest utility,
				// until only k patterns are left
				do {
					kPatterns.poll();
				} while (kPatterns.size() > k);
			}
			// we raise the minimum utility to the lowest utility in the
			// set of top-k patterns
			minUtil = kPatterns.peek().utility;
		}

	}

	/**
	 * Insert an itemset in the top-k patterns until now
	 * 
	 * @param prefix  the prefix of the itemset
	 * @param length  the length of the prefix
	 * @param item1   an item
	 * @param item2   another item
	 * @param utility the utility
	 */
	private void insert(Qitem[] prefix, int length, Qitem item1, Qitem item2, long utility) {
		Qitemset temp =  new Qitemset(prefix, length, item1, item2, utility);
		System.out.println("ADDHERE1" + temp);
		kPatterns.add(temp);

		// if the size becomes larger than k
		if (kPatterns.size() > k) {

			// if the utility of the pattern that we haved added is higher than
			// the minimum utility, we will need to take out at least one rule
			if (utility >= minUtil) {
				// we recursively remove the pattern having the lowest utility,
				// until only k patterns are left
				do {
					kPatterns.poll();
				} while (kPatterns.size() > k);
			}
			// we raise the minimum utility to the lowest utility in the
			// set of top-k patterns
			minUtil = kPatterns.peek().utility;
		}

	}

	/**
	 * Insert an item in the top-k patterns until now
	 * 
	 * @param item    the item
	 * @param utility its utility
	 */
	private void insert(Qitem item, long utility) {
		Qitemset temp = new Qitemset(item, utility);
		System.out.println("ADDHERE2" + temp);
		kPatterns.add(temp);

		if (kPatterns.size() > k) {

			// if the utility of the pattern that we haved added is higher than
			// the minimum utility, we will need to take out at least one rule
			if (utility >= minUtil) {
				// we recursively remove the pattern having the lowest utility,
				// until only k patterns are left
				do {
					kPatterns.poll();
				} while (kPatterns.size() > k);
			}
			// we raise the minimum utility to the lowest utility in the
			// set of top-k patterns
			minUtil = kPatterns.peek().utility;
		}
	}

	/**
	 * Insert an itemset with two qitems
	 * 
	 * @param item1   the first item
	 * @param item2   the second item
	 * @param utility the utility
	 */
	private void insertCUD(Qitem item1, Qitem item2, long utility) {
		Qitemset temp = new Qitemset(item1, item2, utility);
		System.out.println("ADDHERE3" + temp);
		kPatterns.add(temp);
		if (kPatterns.size() > k) {

			// if the utility of the pattern that we haved added is higher than
			// the minimum utility, we will need to take out at least one rule
			if (utility >= minUtil) {
				// we recursively remove the pattern having the lowest utility,
				// until only k patterns are left
				do {
					kPatterns.poll();
				} while (kPatterns.size() > k);
			}
			// we raise the minimum utility to the lowest utility in the
			// set of top-k patterns
			minUtil = kPatterns.peek().utility;
		}
	}

	/**
	 * Insert a value in priority queue
	 * 
	 * @param ktopls the priority queue
	 * @param value  the value
	 */
	private void insertIn(PriorityQueue<Long> ktopls, long value) {
		// Insert the priority queue Q-itemsets in the top-k queue
		if (ktopls.size() < k) {
			ktopls.add(value);
		} else if (value > ktopls.peek()) {
			ktopls.add(value);
			do {
				ktopls.poll();
			} while (ktopls.size() > k);
		}
	}

	/**
	 * Write the results to a file
	 * 
	 * @throws IOException if error while writing to file
	 */
	private void writeResultTofile() throws IOException {
		// Write the result in a file
		Iterator<Qitemset> iter = kPatterns.iterator();
		while (iter.hasNext()) {
			Qitemset pattern = (Qitemset) iter.next();
			StringBuilder buffer = new StringBuilder();
//			buffer.append(pattern.itemset.toString());
			// append the prefix
			for (int i = 0; i < pattern.itemset.size(); i++) {
				buffer.append(pattern.itemset.get(i).toString());
				buffer.append(' ');
			}
			// write separator
			buffer.append("#UTIL: ");
			// write support
			buffer.append(pattern.utility);
			writer_hqui.write(buffer.toString());
			writer_hqui.newLine();
		}
		writer_hqui.close();
	}

	/**
	 * Build the initial q-utility lists
	 * 
	 * @param inputData            the input file path for the database with
	 *                             quantities
	 * @param inputProfit          the input file path for items with profit
	 *                             information
	 * @param qitemNameList        the list of qitems
	 * @param mapItemToUtilityList a map of each qitem to its utility list
	 * @throws IOException if error while reading or writing to file
	 */
	private void buildInitialQUtilityLists(String inputData, String inputProfit, ArrayList<Qitem> qitemNameList,
			Hashtable<Qitem, UtilityListTKQ> mapItemToUtilityList) throws IOException {
		// This function: (1)builds the initial Q-item utility-lists, (2)raise the
		// mininmum utility acccording to RIU, (3)raise minimum utility accprding to CUD

		BufferedReader br_profitTable = new BufferedReader(new FileReader(inputProfit));
		BufferedReader br_inputDatabase = new BufferedReader(new FileReader(inputData));

		// 1. Build mapItemToProfit
		String str;
		while ((str = br_profitTable.readLine()) != null) {
			String[] itemProfit = str.split(", ");

			if (itemProfit.length >= 2) {
				int profit = Integer.parseInt(itemProfit[1]);
				if (profit == 0)
					profit = 1;
				int item = Integer.parseInt(itemProfit[0]);
				mapItemToProfit.put(item, profit);
			}
		}
		br_profitTable.close();

		// 2. Build mapItemToTWU
		mapItemToTwu = new Hashtable<Qitem, Integer>();
		int tid = 0;
		currentQitem = new Qitem(0, 0);
		Qitem Q;
		while ((str = br_inputDatabase.readLine()) != null) {
			tid++;
			String[] itemInfo = str.split(" ");// (A,2) (B, 5)
			int transactionU = 0;
			for (int i = 0; i < itemInfo.length; i++) {
				currentQitem.setItem(Integer.valueOf(new String(itemInfo[i].substring(0, itemInfo[i].indexOf(',')))));
				currentQitem.setQteMin(Integer.valueOf(
						new String(itemInfo[i].substring(itemInfo[i].indexOf(',') + 1, itemInfo[i].length()))));
				currentQitem.setQteMax(Integer.valueOf(
						new String(itemInfo[i].substring(itemInfo[i].indexOf(',') + 1, itemInfo[i].length()))));
				transactionU += currentQitem.getQteMin() * mapItemToProfit.get(currentQitem.getItem());
			}
			for (int i = 0; i < itemInfo.length; i++) {
				currentQitem.setItem(Integer.valueOf(new String(itemInfo[i].substring(0, itemInfo[i].indexOf(',')))));
				currentQitem.setQteMin(Integer.valueOf(
						new String(itemInfo[i].substring(itemInfo[i].indexOf(',') + 1, itemInfo[i].length()))));
				currentQitem.setQteMax(Integer.valueOf(
						new String(itemInfo[i].substring(itemInfo[i].indexOf(',') + 1, itemInfo[i].length()))));
				Q = new Qitem();
				Q.copy(currentQitem);
				if (!mapItemToTwu.containsKey(Q))
					mapItemToTwu.put(Q, transactionU);
				else
					mapItemToTwu.put(Q, mapItemToTwu.get(Q) + transactionU);
				long utility = Q.getQteMin() * mapItemToProfit.get(Q.getItem());
				Long real = realUtility.get(Q);
				real = (real == null) ? utility : utility + real;
				realUtility.put(Q, real);
			}
//			totalU += transactionU;
		}

		// 3. Apply Raising Threshold RIU
		if (DEBUG_MODE) {
			System.out.println("===============================================");
			System.out.println(" minutil is " + minUtil);
		}
		raisingThresholdRIU(realUtility, k);  ////////////////////////////////////   RIU  ////////
		if (DEBUG_MODE) {
			System.out.println("after RIU minUtil is " + minUtil);
		}

		// 4. Build mapItemToUtilityList
		for (Qitem item : mapItemToTwu.keySet()) {
			if (mapItemToTwu.get(item) >= Math.floor(minUtil / coefficient)) {
				UtilityListTKQ ul = new UtilityListTKQ(item, 0);
				mapItemToUtilityList.put(item, ul);
				qitemNameList.add(item);
			}
		}
		br_inputDatabase.close();
		MemoryLogger.getInstance().checkMemory();

		// 5. Build MapFmap with MapItemToUtilityList
		br_inputDatabase = new BufferedReader(new FileReader(inputData));
		str = "";
		tid = 0;
		PriorityQueue<Long> ktopls = new PriorityQueue<Long>();
		mapFMAP = new HashMap<Qitem, Map<Qitem, InfoTKQ>>();
		while ((str = br_inputDatabase.readLine()) != null) {
			tid++;
			String[] itemInfo = str.split(" ");
			int remainingUtility = 0;
			Integer newTWU = 0; // NEW OPTIMIZATION
			List<Qitem> revisedTransaction = new ArrayList<Qitem>();
			for (int i = 0; i < itemInfo.length; i++) {
				Q = new Qitem();
				Q.setItem(Integer.valueOf(new String(itemInfo[i].substring(0, itemInfo[i].indexOf(',')))));
				Q.setQteMin(Integer.valueOf(
						new String(itemInfo[i].substring(itemInfo[i].indexOf(',') + 1, itemInfo[i].length()))));
				Q.setQteMax(Integer.valueOf(
						new String(itemInfo[i].substring(itemInfo[i].indexOf(',') + 1, itemInfo[i].length()))));
				if (mapItemToUtilityList.containsKey(Q)) {
					revisedTransaction.add(Q);
					remainingUtility += Q.getQteMin() * mapItemToProfit.get(Q.getItem());
					newTWU += Q.getQteMin() * mapItemToProfit.get(Q.getItem());
				}
				mapTransactionToUtility.put(tid, newTWU);
			} // end for
			Collections.sort(revisedTransaction, new Comparator<Qitem>() {
				public int compare(Qitem o1, Qitem o2) {
					return compareQItems(o1, o2);
				}
			});
			insertIn(ktopls, remainingUtility);
//			int cumUtil, qAfterUtil;
//			boolean condition = true;
//			Qitem currentItem;
			for (int i = 0; i < revisedTransaction.size(); i++) {
				// System.out.println("current transaction is "+revisedTransaction.toString());
				Qitem current_q = revisedTransaction.get(i);
//				currentItem = current_q;
//				cumUtil = current_q.getQteMin() * mapItemToProfit.get(current_q.getItem());
//				condition = true;
				remainingUtility = remainingUtility - current_q.getQteMin() * mapItemToProfit.get(current_q.getItem());

				UtilityListTKQ utilityListOfItem = mapItemToUtilityList.get(current_q);
				// Add a new Element to the utility list of this item corresponding to this
				// transaction
				QItemTrans element = new QItemTrans(tid,
						current_q.getQteMin() * mapItemToProfit.get(current_q.getItem()), remainingUtility);
				utilityListOfItem.addTrans(element);
				utilityListOfItem.addTWU(mapTransactionToUtility.get(tid));
				// BEGIN NEW OPTIMIZATION
				Map<Qitem, InfoTKQ> mapFMAPItem = mapFMAP.get(current_q);
				if (mapFMAPItem == null) {
					mapFMAPItem = new HashMap<Qitem, InfoTKQ>();
					mapFMAP.put(current_q, mapFMAPItem);
				}

				for (int j = i + 1; j < revisedTransaction.size(); j++) {
					// this is for EUCP
					Qitem qAfter = revisedTransaction.get(j);
//					qAfterUtil = qAfter.getQteMin() * mapItemToProfit.get(qAfter.getItem());
					InfoTKQ infoItem = mapFMAPItem.get(qAfter);
					if (infoItem == null) {
						infoItem = new InfoTKQ();
					}
					infoItem.twu += newTWU;
					infoItem.utility += (long) current_q.getQteMin() * mapItemToProfit.get(current_q.getItem())
							+ qAfter.getQteMin() * mapItemToProfit.get(qAfter.getItem());
					mapFMAPItem.put(qAfter, infoItem);

				}
			}
		}
		MemoryLogger.getInstance().checkMemory();

		// 6. Apply Raising Threshold CUD
		if (DEBUG_MODE) {
			System.out.println("===================================================");
			System.out.println(" before CUD kpattertns is ... minutil is " + minUtil);
		}
		raisingThresholdCUDOptimize2();   /////////////////////////////////////////// CUD
		if (DEBUG_MODE) {
			System.out.println("after CUD minUtil is " + minUtil);
		}

		// 7. Sort the final list of Q-itemsets according to their utilities
		Collections.sort(qitemNameList, new Comparator<Qitem>() {
			public int compare(Qitem o1, Qitem o2) {
				return compareQItems(o1, o2);
			}
		});
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Raising the threshold with CUD strategy
	 */
	private void raisingThresholdCUDOptimize2() {
		// Raise the minutil according to CUD raising strategy
		long value = 0L;
		for (Map.Entry<Qitem, Map<Qitem, InfoTKQ>> entry : mapFMAP.entrySet()) {
			for (Map.Entry<Qitem, InfoTKQ> entry2 : entry.getValue().entrySet()) {
				value = entry2.getValue().utility;
				if (value >= minUtil) {
					CUD.put(entry.getKey() + "_" + entry2.getKey(), (long) (entry2.getValue().utility));
					insertCUD(entry.getKey(), entry2.getKey(), (long) (entry2.getValue().utility));
				}
			}
		}
	}

	/**
	 * Raising threshold with RIU strategy
	 * 
	 * @param map a map of QItems
	 * @param k   the parameter k
	 */
	private void raisingThresholdRIU(Map<Qitem, Long> map, int k) {
		// Raise the minutil according to RIU raising strategy
		List<Map.Entry<Qitem, Long>> list = new LinkedList<Map.Entry<Qitem, Long>>(map.entrySet());
		Collections.sort(list, new Comparator<Map.Entry<Qitem, Long>>() {
			@Override
			public int compare(Map.Entry<Qitem, Long> value1, Map.Entry<Qitem, Long> value2) {
				return (value2.getValue()).compareTo(value1.getValue());
			}
		});

		if ((list.size() >= k) && (k > 0)) {
			minUtil = list.get(k - 1).getValue();
		}
		for (int i = 0; i < list.size(); i++) {
			insert(list.get(i).getKey(), list.get(i).getValue());
		}
		list = null;
	}

	/**
	 * Find the initial RHUQIs
	 * 
	 * @param qitemNameList        a list of qitems
	 * @param mapItemToUtilityList a map from qitems to their utility lists
	 * @param candidateList        a list of candidate q-items
	 * @param hwQUI                another list
	 * @throws IOException if error while reading or writing to file
	 */
	private void findInitialRHUQIs(ArrayList<Qitem> qitemNameList,
			Hashtable<Qitem, UtilityListTKQ> mapItemToUtilityList, ArrayList<Qitem> candidateList,
			ArrayList<Qitem> hwQUI) throws IOException {
		// Check if a Q-itemset is:
		// 1. High,
		// 2. Candidate,
		// 3. To be explored or to be directly prunned
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < qitemNameList.size(); i++) {
			long utility = mapItemToUtilityList.get(qitemNameList.get(i)).getSumIutils();
			if (utility >= minUtil) {
				sb.delete(0, sb.length());
				sb.append(qitemNameList.get(i));
				sb.append(":");
				sb.append(utility);
				sb.append("\r\n");
				// writer_hqui.write(sb.toString());
				hwQUI.add(qitemNameList.get(i));
				HUQIcount++;
				// insert(qitemNameList.get(i),utility);
			} else {
				if ((combiningMethod != EnumCombination.COMBINEMAX && utility >= Math.floor(minUtil / coefficient))
						|| (combiningMethod == EnumCombination.COMBINEMAX && utility >= Math.floor(minUtil / 2))) {
					candidateList.add(qitemNameList.get(i));
				}
				if (utility + mapItemToUtilityList.get(qitemNameList.get(i)).getSumRutils() >= minUtil) {
					hwQUI.add(qitemNameList.get(i));
				}
			}

		}
		MemoryLogger.getInstance().checkMemory();
		// Perform the combination process on the candidate q-itemsets
		combineMethod(null, 0, candidateList, qitemNameList, mapItemToUtilityList, hwQUI);
	}

	/**
	 * Combine method
	 * 
	 * @param prefix               a prefix
	 * @param prefixLength         the length of the prefix
	 * @param candidateList        the candidate list
	 * @param qItemNameList        the qtiem list
	 * @param mapItemToUtilityList a map of item to utility list
	 * @param hwQUI                hwQUI
	 * @return the result
	 */
	ArrayList<Qitem> combineMethod(Qitem[] prefix, int prefixLength, ArrayList<Qitem> candidateList,
			ArrayList<Qitem> qItemNameList, Hashtable<Qitem, UtilityListTKQ> mapItemToUtilityList,
			ArrayList<Qitem> hwQUI) {
		// Sort the candidate Q-itemsets according to items than Qte-Min than Qte-Max
		// System.out.println("cadidate is "+candidateList.toString());
		if (candidateList.size() > 2) {
			Collections.sort(candidateList, new Comparator<Qitem>() {
				public int compare(Qitem o1, Qitem o2) {
					return compareCandidateItems(o1, o2);
				}
			});

			if (EnumCombination.COMBINEALL.equals(combiningMethod)) {
				combineAll(prefix, prefixLength, candidateList, qItemNameList, mapItemToUtilityList, hwQUI);
			} else if (EnumCombination.COMBINEMIN.equals(combiningMethod)) {
				combineMin(prefix, prefixLength, candidateList, qItemNameList, mapItemToUtilityList, hwQUI);
			} else if (EnumCombination.COMBINEMAX.equals(combiningMethod)) {
				combineMax(prefix, prefixLength, candidateList, qItemNameList, mapItemToUtilityList, hwQUI);
			}
			MemoryLogger.getInstance().checkMemory();
		}
		return qItemNameList;
	}

	/**
	 * The combine all combination method
	 * 
	 * @param prefix               a prefix of an itemset
	 * @param prefixLength         the length of the prefix
	 * @param candidateList        a list of candidate qitems
	 * @param qItemNameList        another list of qitems
	 * @param mapItemToUtilityList a map of qitems to utility list
	 * @param hwQUI                another list of qitems
	 */
	private void combineAll(Qitem[] prefix, int prefixLength, ArrayList<Qitem> candidateList,
			ArrayList<Qitem> qItemNameList, Hashtable<Qitem, UtilityListTKQ> mapItemToUtilityList,
			ArrayList<Qitem> hwQUI) {
		// delete non necessary candidate q-items
		int s = 1;
		while (s < candidateList.size() - 1) {
			if (((candidateList.get(s).getQteMin() == candidateList.get(s - 1).getQteMax() + 1)
					&& (candidateList.get(s).getItem() == candidateList.get(s - 1).getItem()))
					|| ((candidateList.get(s).getQteMax() == candidateList.get(s + 1).getQteMin() - 1)
							&& (candidateList.get(s).getItem() == candidateList.get(s + 1).getItem())))
				s++;
			else
				candidateList.remove(s);
		}
		if (candidateList.size() > 2) {
			if ((candidateList.get(candidateList.size() - 1)
					.getQteMin() != candidateList.get(candidateList.size() - 2).getQteMax() + 1)
					|| (candidateList.get(candidateList.size() - 2).getItem() != candidateList
							.get(candidateList.size() - 1).getItem()))
				candidateList.remove(candidateList.size() - 1);
		}

		// make the combination process
		Map<Qitem, UtilityListTKQ> mapRangeToUtilityList = new HashMap<Qitem, UtilityListTKQ>();

		int count;
		for (int i = 0; i < candidateList.size(); i++) {
			int currentItem = candidateList.get(i).getItem();

			mapRangeToUtilityList.clear();
			count = 1;
			for (int j = i + 1; j < candidateList.size(); j++) {
				int nextItem = candidateList.get(j).getItem();
				if (currentItem != nextItem)
					break;
				else {
					UtilityListTKQ res;

					if (j == i + 1) {

						if (candidateList.get(j).getQteMin() != candidateList.get(i).getQteMax() + 1)
							break;

						res = constructForCombine(mapItemToUtilityList.get(candidateList.get(i)),
								mapItemToUtilityList.get(candidateList.get(j)));
						count++;
						if (count > coefficient)
							break;

						mapRangeToUtilityList.put(res.getSingleItemsetName(), res);
						if (res.getSumIutils() > minUtil) {
							HUQIcount++;
							// writeOut2(prefix,
							// prefixLength,res.getSingleItemsetName(),res.getSumIutils());
							if (prefixLength == 0)
								insert(res.getSingleItemsetName(), res.getSumIutils());
							else
								insert(prefix, prefixLength, res.getSingleItemsetName(), res.getSumIutils());

							hwQUI.add(res.getSingleItemsetName());
							mapItemToUtilityList.put(res.getSingleItemsetName(), res);
							int site = qItemNameList.indexOf(candidateList.get(j));
							qItemNameList.add(site, res.getSingleItemsetName());
						}
					} else {
						if (candidateList.get(j).getQteMin() != candidateList.get(j - 1).getQteMax() + 1)
							break;
						Qitem qItem1 = new Qitem(currentItem, candidateList.get(i).getQteMin(),
								candidateList.get(j - 1).getQteMax());
						UtilityListTKQ ulQitem1 = mapRangeToUtilityList.get(qItem1);
						res = constructForCombine(ulQitem1, mapItemToUtilityList.get(candidateList.get(j)));
						count++;
						if (count > coefficient)
							break;
						mapRangeToUtilityList.put(res.getSingleItemsetName(), res);
						if (res.getSumIutils() > minUtil) {
							HUQIcount++;
							// writeOut2(prefix,
							// prefixLength,res.getSingleItemsetName(),res.getSumIutils());
							if (prefixLength == 0)
								insert(res.getSingleItemsetName(), res.getSumIutils());
							else
								insert(prefix, prefixLength, res.getSingleItemsetName(), res.getSumIutils());
							hwQUI.add(res.getSingleItemsetName());
							mapItemToUtilityList.put(res.getSingleItemsetName(), res);
							int site = qItemNameList.indexOf(candidateList.get(j));
							qItemNameList.add(site, res.getSingleItemsetName());
						}
					}
				}
			}
		}
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * The combine min combination method
	 * 
	 * @param prefix               a prefix of an itemset
	 * @param prefixLength         the length of the prefix
	 * @param candidateList        a list of candidate qitems
	 * @param qItemNameList        another list of qitems
	 * @param mapItemToUtilityList a map of qitems to utility list
	 * @param hwQUI                another list of qitems
	 * @throws IOException if error while reading or writing to file
	 */
	private void combineMin(Qitem[] prefix, int prefixLength, ArrayList<Qitem> candidateList,
			ArrayList<Qitem> qItemNameList, Hashtable<Qitem, UtilityListTKQ> mapItemToUtilityList,
			ArrayList<Qitem> hwQUI) {

		// delete non necessary candidate q-items
		int s = 1;
		while (s < candidateList.size() - 1) {
			if (((candidateList.get(s).getQteMin() == candidateList.get(s - 1).getQteMax() + 1)
					&& (candidateList.get(s).getItem() == candidateList.get(s - 1).getItem()))
					|| ((candidateList.get(s).getQteMax() == candidateList.get(s + 1).getQteMin() - 1)
							&& (candidateList.get(s).getItem() == candidateList.get(s + 1).getItem())))
				s++;
			else
				candidateList.remove(s);
		}
		if (candidateList.size() > 2) {
			if ((candidateList.get(candidateList.size() - 1)
					.getQteMin() != candidateList.get(candidateList.size() - 2).getQteMax() + 1)
					|| (candidateList.get(candidateList.size() - 2).getItem() != candidateList
							.get(candidateList.size() - 1).getItem()))
				candidateList.remove(candidateList.size() - 1);
		}

		// make the combination process
		int count;
		ArrayList<Qitem> temporaryArrayList = new ArrayList<Qitem>();
		Map<Qitem, UtilityListTKQ> temporaryMap = new HashMap<Qitem, UtilityListTKQ>();
		Map<Qitem, UtilityListTKQ> mapRangeToUtilityList = new HashMap<Qitem, UtilityListTKQ>();

		for (int i = 0; i < candidateList.size(); i++) {
			int currentItem = candidateList.get(i).getItem();
			mapRangeToUtilityList.clear();
			count = 1;
			for (int j = i + 1; j < candidateList.size(); j++) {
				int nextItem = candidateList.get(j).getItem();
				if (currentItem != nextItem)
					break;

				else {
					UtilityListTKQ res;
					if (j == i + 1) {
						if (candidateList.get(j).getQteMin() != candidateList.get(i).getQteMax() + 1)
							break;
						res = constructForCombine(mapItemToUtilityList.get(candidateList.get(i)),
								mapItemToUtilityList.get(candidateList.get(j)));
						count++;
						if (count > coefficient)
							break;
						mapRangeToUtilityList.put(res.getSingleItemsetName(), res);
						if (res.getSumIutils() > minUtil) {
							if ((temporaryArrayList.isEmpty())
									|| (res.getSingleItemsetName().getItem() != temporaryArrayList
											.get(temporaryArrayList.size() - 1).getItem())
									|| (res.getSingleItemsetName().getQteMax() > temporaryArrayList
											.get(temporaryArrayList.size() - 1).getQteMax())) {
								temporaryArrayList.add(res.getSingleItemsetName());
								temporaryMap.put(res.getSingleItemsetName(), res);
							} else {
								temporaryMap.remove(temporaryArrayList.get(temporaryArrayList.size() - 1));
								temporaryArrayList.remove(temporaryArrayList.size() - 1);
								temporaryArrayList.add(res.getSingleItemsetName());
								temporaryMap.put(res.getSingleItemsetName(), res);
							}
							;
							break;
						}
					} else {
						if (candidateList.get(j).getQteMin() != candidateList.get(j - 1).getQteMax() + 1)
							break;
						Qitem qItem1 = new Qitem(currentItem, candidateList.get(i).getQteMin(),
								candidateList.get(j - 1).getQteMax());
						UtilityListTKQ ulQitem1 = mapRangeToUtilityList.get(qItem1);
						res = constructForCombine(ulQitem1, mapItemToUtilityList.get(candidateList.get(j)));
						count++;
						if (count > coefficient)
							break;
						mapRangeToUtilityList.put(res.getSingleItemsetName(), res);
						if (res.getSumIutils() > minUtil) {
							if ((temporaryArrayList.isEmpty())
									|| (res.getSingleItemsetName().getItem() != temporaryArrayList
											.get(temporaryArrayList.size() - 1).getItem())
									|| (res.getSingleItemsetName().getQteMax() > temporaryArrayList
											.get(temporaryArrayList.size() - 1).getQteMax())) {
								temporaryArrayList.add(res.getSingleItemsetName());
								temporaryMap.put(res.getSingleItemsetName(), res);
							} else {
								temporaryMap.remove(temporaryArrayList.get(temporaryArrayList.size() - 1));
								temporaryArrayList.remove(temporaryArrayList.size() - 1);
								temporaryArrayList.add(res.getSingleItemsetName());
								temporaryMap.put(res.getSingleItemsetName(), res);

							}

							break;
						}
					}
				}
			}
		}
		for (int k = 0; k < temporaryArrayList.size(); k++) {
			Qitem currentQitem = temporaryArrayList.get(k);
			mapItemToUtilityList.put(currentQitem, temporaryMap.get(currentQitem));
			// writeOut2(prefix,
			// prefixLength,currentQitem,temporaryMap.get(currentQitem).getSumIutils());
			insert(prefix, prefixLength, currentQitem, temporaryMap.get(currentQitem).getSumIutils());
			HUQIcount++;
			hwQUI.add(currentQitem);
			Qitem q = new Qitem(currentQitem.getItem(), currentQitem.getQteMax());
			int site = qItemNameList.indexOf(q);
			qItemNameList.add(site, currentQitem);
		}
		temporaryArrayList.clear();
		temporaryMap.clear();
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * The combine max combination method
	 * 
	 * @param prefix               a prefix of an itemset
	 * @param prefixLength         the length of the prefix
	 * @param candidateList        a list of candidate qitems
	 * @param qItemNameList        another list of qitems
	 * @param mapItemToUtilityList a map of qitems to utility list
	 * @param hwQUI                another list of qitems
	 * @throws IOException if error while reading or writing to file
	 */
	private void combineMax(Qitem[] prefix, int prefixLength, ArrayList<Qitem> candidateList,
			ArrayList<Qitem> qItemNameList, Hashtable<Qitem, UtilityListTKQ> mapItemToUtilityList,
			ArrayList<Qitem> hwQUI) {
		// delete non necessary candidate q-items
		int s = 1;
		while (s < candidateList.size() - 1) {
			if (((candidateList.get(s).getQteMin() == candidateList.get(s - 1).getQteMax() + 1)
					&& (candidateList.get(s).getItem() == candidateList.get(s - 1).getItem()))
					|| ((candidateList.get(s).getQteMax() == candidateList.get(s + 1).getQteMin() - 1)
							&& (candidateList.get(s).getItem() == candidateList.get(s + 1).getItem())))
				s++;
			else
				candidateList.remove(s);
		}
		if (candidateList.size() > 2) {
			if ((candidateList.get(candidateList.size() - 1)
					.getQteMin() != candidateList.get(candidateList.size() - 2).getQteMax() + 1)
					|| (candidateList.get(candidateList.size() - 2).getItem() != candidateList
							.get(candidateList.size() - 1).getItem()))
				candidateList.remove(candidateList.size() - 1);
		}

		// make the combination process
		ArrayList<Qitem> temporaryArrayList = new ArrayList<Qitem>();
		Map<Qitem, UtilityListTKQ> temporaryMap = new HashMap<Qitem, UtilityListTKQ>();
		Map<Qitem, UtilityListTKQ> mapRangeToUtilityList = new HashMap<Qitem, UtilityListTKQ>();
		int count;
		for (int i = 0; i < candidateList.size(); i++) {
			UtilityListTKQ res = new UtilityListTKQ();
			int currentItem = candidateList.get(i).getItem();
			mapRangeToUtilityList.clear();
			count = 1;
			for (int j = i + 1; j < candidateList.size(); j++) {
				int nextItem = candidateList.get(j).getItem();
				// System.out.println("nextItem is "+nextItem);
				if (currentItem != nextItem)
					break;
				else {
					if (j == i + 1) {
						if (candidateList.get(j).getQteMin() != candidateList.get(i).getQteMax() + 1)
							break;
						res = constructForCombine(mapItemToUtilityList.get(candidateList.get(i)),
								mapItemToUtilityList.get(candidateList.get(j)));
						count++;
						// System.out.println("name is "+res.getItemsetName()+", count is "+count);
						if (count > coefficient - 1)
							break;
						mapRangeToUtilityList.put(res.getSingleItemsetName(), res);
					} else {
						if (candidateList.get(j).getQteMin() != candidateList.get(j - 1).getQteMax() + 1)
							break;
						Qitem qItem1 = new Qitem(currentItem, candidateList.get(i).getQteMin(),
								candidateList.get(j - 1).getQteMax());
						UtilityListTKQ ulQitem1 = mapRangeToUtilityList.get(qItem1);
						res = constructForCombine(ulQitem1, mapItemToUtilityList.get(candidateList.get(j)));
						count++;
						if (count >= coefficient)
							break;
						mapRangeToUtilityList.put(res.getSingleItemsetName(), res);
					}

				}
			}
			if (res.getSumIutils() > minUtil) {
				if ((temporaryMap.isEmpty())
						|| (res.getSingleItemsetName().getItem() != temporaryArrayList
								.get(temporaryArrayList.size() - 1).getItem())
						|| (res.getSingleItemsetName().getQteMax() > temporaryArrayList
								.get(temporaryArrayList.size() - 1).getQteMax())) {
					temporaryMap.put(res.getSingleItemsetName(), res);
					temporaryArrayList.add(res.getSingleItemsetName());
				}
			}
		}
		for (int k = 0; k < temporaryArrayList.size(); k++) {
			Qitem currentQitem = temporaryArrayList.get(k);
			mapItemToUtilityList.put(currentQitem, temporaryMap.get(currentQitem));
			// writeOut2(prefix,
			// prefixLength,currentQitem,temporaryMap.get(currentQitem).getSumIutils());
			insert(prefix, prefixLength, currentQitem, temporaryMap.get(currentQitem).getSumIutils());
			HUQIcount++;
			hwQUI.add(currentQitem);
			Qitem q = new Qitem(currentQitem.getItem(), currentQitem.getQteMax());
			int site = qItemNameList.indexOf(q);
			qItemNameList.add(site, currentQitem);
		}

		temporaryArrayList.clear();
		temporaryMap.clear();
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Method to construct the utility list of an itemset
	 * 
	 * @param ulQitem1 the utility list of a qitem
	 * @param ulQitem2 the utility list of another qitem
	 * @return the resulting utility list
	 */
	private UtilityListTKQ constructForCombine(UtilityListTKQ ulQitem1, UtilityListTKQ ulQitem2) {

		UtilityListTKQ result = new UtilityListTKQ(new Qitem(ulQitem1.getSingleItemsetName().getItem(),
				ulQitem1.getSingleItemsetName().getQteMin(), ulQitem2.getSingleItemsetName().getQteMax()));

		ArrayList<QItemTrans> temp1 = ulQitem1.getQItemTrans();
		ArrayList<QItemTrans> temp2 = ulQitem2.getQItemTrans();

		ArrayList<QItemTrans> mainlist = new ArrayList<QItemTrans>();

		result.setSumIutils(ulQitem1.getSumIutils() + ulQitem2.getSumIutils());

		result.setSumRutils(ulQitem1.getSumRutils() + ulQitem2.getSumRutils());

		result.setTwu(ulQitem1.getTwu() + ulQitem2.getTwu());

		int i = 0, j = 0;
		while (i < temp1.size() && j < temp2.size()) {
			int t1 = temp1.get(i).getTid();
			int t2 = temp2.get(j).getTid();
			if (t1 > t2) {
				mainlist.add(temp2.get(j));
				j++;
			} else {
				mainlist.add(temp1.get(i));
				i++;
			}
		}
		if (i == temp1.size()) {
			while (j < temp2.size())
				mainlist.add(temp2.get(j++));
		} else if (j == temp2.size()) {
			while (i < temp1.size()) {
				mainlist.add(temp1.get(i++));
			}
		}
		result.setQItemTrans(mainlist);
		MemoryLogger.getInstance().checkMemory();
		return result;
	}

	/**
	 * Method to join two utility lists
	 * 
	 * @param ul1 the utility list of an item
	 * @param ul2 the utility list of another item
	 * @param ul0 the utility list of the prefix
	 * @return the resulting utility list
	 */
	private UtilityListTKQ constructForJoin(UtilityListTKQ ul1, UtilityListTKQ ul2, UtilityListTKQ ul0) {
		if (ul1.getSingleItemsetName().getItem() == ul2.getSingleItemsetName().getItem())
			return null;

		ArrayList<QItemTrans> qT1 = ul1.getQItemTrans();
		ArrayList<QItemTrans> qT2 = ul2.getQItemTrans();
		UtilityListTKQ res = new UtilityListTKQ(ul2.getItemsetName());

		if (ul0 == null) {
			int i = 0, j = 0;
			while (i < qT1.size() && j < qT2.size()) {
				int tid1 = qT1.get(i).getTid();
				int tid2 = qT2.get(j).getTid();

				if (tid1 == tid2) {
					// QItemTrans combine = new QItemTrans();
					int eu1 = qT1.get(i).getEu();
					// int ru = qT1.get(i).getRu();
					int eu2 = qT2.get(j).getEu();

					if (qT1.get(i).getRu() >= qT2.get(j).getRu()) {
						QItemTrans temp = new QItemTrans(tid1, eu1 + eu2, qT2.get(j).getRu());
						res.addTrans(temp, mapTransactionToUtility.get(tid1));
					}
					i++;
					j++;
				} else if (tid1 > tid2) {
					j++;
				} else {
					i++;
				}
			}
		} else {
			ArrayList<QItemTrans> preQT = ul0.getQItemTrans();
			int i = 0, j = 0, k = 0;
			while (i < qT1.size() && j < qT2.size()) {
				int tid1 = qT1.get(i).getTid();
				int tid2 = qT2.get(j).getTid();

				if (tid1 == tid2) {
					// QItemTrans combine = new QItemTrans();
					int eu1 = qT1.get(i).getEu();
//					int ru1 = qT1.get(i).getRu();
					int eu2 = qT2.get(j).getEu();

					// тユ preitemutility
					while (preQT.get(k).getTid() != tid1) {
						k++;
					}
					int preEU = preQT.get(k).getEu();

					if (qT1.get(i).getRu() >= qT2.get(j).getRu()) {
						QItemTrans temp = new QItemTrans(tid1, eu1 + eu2 - preEU, qT2.get(j).getRu());
						res.addTrans(temp, mapTransactionToUtility.get(tid1));
					}
					i++;
					j++;
				} else if (tid1 > tid2) {
					j++;
				} else {
					i++;
				}
			}
			// return res;
		}
		MemoryLogger.getInstance().checkMemory();
		if (!res.getQItemTrans().isEmpty())
			return res;
		return null;
	}

	/**
	 * The main pattern mining procedure
	 * 
	 * @param prefix         a prefix itemset
	 * @param prefixLength   the length of the prefix
	 * @param prefixUL       the utility list of the prefix
	 * @param ULs            the utility lists of some extensions of the prefix
	 * @param qItemNameList  a list of qitems
	 * @param br_writer_hqui the buffered writer for writing the output
	 * @param hwQUI          list of hWQUIs
	 */
	private void miner(Qitem[] prefix, int prefixLength, UtilityListTKQ prefixUL, Hashtable<Qitem, UtilityListTKQ> ULs,
			ArrayList<Qitem> qItemNameList, BufferedWriter br_writer_hqui, ArrayList<Qitem> hwQUI) throws IOException {
		// For pruning range Q-itemsets using MapFmap
		int[] t2 = new int[coefficient];
		ArrayList<Qitem> nextNameList = new ArrayList<Qitem>();

		for (int i = 0; i < qItemNameList.size(); i++) {

			nextNameList.clear();
			ArrayList<Qitem> nextHWQUI = new ArrayList<Qitem>();
			ArrayList<Qitem> candidateList = new ArrayList<Qitem>();
			Hashtable<Qitem, UtilityListTKQ> nextHUL = new Hashtable<Qitem, UtilityListTKQ>();
			Hashtable<Qitem, UtilityListTKQ> candidateHUL = new Hashtable<Qitem, UtilityListTKQ>();

			if (!hwQUI.contains(qItemNameList.get(i)))
				continue;

			if (qItemNameList.get(i).isRange()) {
				for (int ii = qItemNameList.get(i).getQteMin(); ii <= qItemNameList.get(i).getQteMax(); ii++) {
					t2[ii - qItemNameList.get(i).getQteMin()] = qItemNameList
							.indexOf(new Qitem(qItemNameList.get(i).getItem(), ii));
				}
			}

			for (int j = i + 1; j < qItemNameList.size(); j++) {

				if (qItemNameList.get(j).isRange())
					continue;

				if (qItemNameList.get(i).isRange() && j == i + 1)
					continue;

				UtilityListTKQ afterUL = null;

				// Co-occurence pruning strategy
				Map<Qitem, InfoTKQ> mapTWUF = mapFMAP.get(qItemNameList.get(i));
				if (mapTWUF != null) {
					InfoTKQ twuF = mapTWUF.get(qItemNameList.get(j));
					if (twuF == null || twuF.twu < Math.floor(minUtil / coefficient))
						continue;
					else {
						afterUL = constructForJoin(ULs.get(qItemNameList.get(i)), ULs.get(qItemNameList.get(j)),
								prefixUL);
						countConstruct++;
						if (afterUL == null || afterUL.getTwu() < Math.floor(minUtil / coefficient))
							continue;
					}
				} else {// In case of range Q-itemsets
					long sumtwu = 0;
					long sum = 0;
					for (int ii = qItemNameList.get(i).getQteMin(); ii <= qItemNameList.get(i).getQteMax(); ii++) {
						if (mapFMAP.get(qItemNameList.get(Math.min(t2[ii - qItemNameList.get(i).getQteMin()], j)))
								.get(qItemNameList.get(Math.max(t2[ii - qItemNameList.get(i).getQteMin()], j))) != null)
							sum = mapFMAP.get(qItemNameList.get(Math.min(t2[ii - qItemNameList.get(i).getQteMin()], j)))
									.get(qItemNameList.get(Math.max(t2[ii - qItemNameList.get(i).getQteMin()], j))).twu;
						else
							continue;
						sumtwu = sumtwu + sum;
					}

					if (sumtwu == 0 || sumtwu < Math.floor(minUtil / coefficient))
						continue;
					else {
						afterUL = constructForJoin(ULs.get(qItemNameList.get(i)), ULs.get(qItemNameList.get(j)),
								prefixUL);
						countConstruct++;
						if (afterUL == null || afterUL.getTwu() < Math.floor(minUtil / coefficient))
							continue;
					}
				}

				if (afterUL != null && afterUL.getTwu() >= Math.floor(minUtil / coefficient)) {
					nextNameList.add(afterUL.getSingleItemsetName()); // item can be explored
					nextHUL.put(afterUL.getSingleItemsetName(), afterUL);
					if (afterUL.getSumIutils() >= minUtil) {
						// writeOut1(prefix,prefixLength,qItemNameList.get(i),qItemNameList.get(j),afterUL.getSumIutils());
						insert(prefix, prefixLength, qItemNameList.get(i), qItemNameList.get(j),
								afterUL.getSumIutils());
						HUQIcount++;
						nextHWQUI.add(afterUL.getSingleItemsetName());
						// System.out.println("next is "+afterUL.getSingleItemsetName()+"util is
						// "+afterUL.getSumIutils());
					} else {
						if ((combiningMethod != EnumCombination.COMBINEMAX
								&& afterUL.getSumIutils() >= Math.floor(minUtil / coefficient))
								|| (combiningMethod == EnumCombination.COMBINEMAX
										&& afterUL.getSumIutils() >= Math.floor(minUtil / 2))) {
							candidateList.add(afterUL.getSingleItemsetName());
							candidateHUL.put(afterUL.getSingleItemsetName(), afterUL);
						}
						if (afterUL.getSumIutils() + afterUL.getSumRutils() >= minUtil) {
							nextHWQUI.add(afterUL.getSingleItemsetName());
						}
					}
				}
			}

			if (candidateList.size() > 0) { // combine process
				nextNameList = combineMethod(prefix, prefixLength, candidateList, nextNameList, nextHUL, nextHWQUI);
				candidateHUL.clear();
				candidateList.clear();
			}
			MemoryLogger.getInstance().checkMemory();
			if (nextNameList.size() >= 1) { // recurcive call
				itemsetBuffer[prefixLength] = qItemNameList.get(i);
				miner(itemsetBuffer, prefixLength + 1, ULs.get(qItemNameList.get(i)), nextHUL, nextNameList,
						br_writer_hqui, nextHWQUI);
			}

		}
	}

	/**
	 * Comparator to order qItems
	 * 
	 * @param q1 a qitem
	 * @param q2 another qitem
	 * @return the comparison result
	 */
	private int compareQItems(Qitem q1, Qitem q2) {
		int compare = (int) ((q2.getQteMin() * mapItemToProfit.get(q2.getItem()))
				- (q1.getQteMin() * mapItemToProfit.get(q1.getItem())));
		// if the same, use the lexical order otherwise use the TWU
		return (compare == 0) ? q1.getItem() - q2.getItem() : compare;
	}

	/**
	 * Comparator to order candidate qItems
	 * 
	 * @param q1 a qitem
	 * @param q2 another qitem
	 * @return the comparison result
	 */
	private int compareCandidateItems(Qitem q1, Qitem q2) {
		int compare = q1.getItem() - q2.getItem();
		if (compare == 0)
			compare = q1.getQteMin() - q2.getQteMin();
		if (compare == 0)
			compare = q1.getQteMax() - q2.getQteMax();
		return compare;
	}

//	private int compareQItemsTWU(Qitem q1, Qitem q2) {
//		int compare = (int) mapItemToTwu.get(q1) - mapItemToTwu.get(q2);
//		// if the same, use the lexical order otherwise use the TWU
//		return (compare == 0) ? q1.getItem() - q2.getItem() : compare;
//	}

	/**
	 * Print statistics about the algorithm execution
	 * 
	 * @param inputData
	 */
	public void printStatistics() {
		System.out.println("============= TKQ v 2.52 Statistical results===============");
		if (DEBUG_MODE) {
			System.out.println("K: " + k + " coefficient: " + coefficient);
		}
		System.out.println("HUQIcount:" + HUQIcount);
		System.out.println("Runtime: " + (double) (endTime - startTime) / 1000 + " (s)");
		System.out.println("Memory usage: " + MemoryLogger.getInstance().getMaxMemory() + " (Mb)");
		if (DEBUG_MODE) {
			System.out.println("Join operation count: " + countConstruct);
		}
		System.out.println("================================================");
	}

}
