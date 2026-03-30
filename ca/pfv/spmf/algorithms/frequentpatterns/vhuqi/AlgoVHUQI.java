/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.pfv.spmf.algorithms.frequentpatterns.vhuqi;

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
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.EnumCombination;
import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.QItemTrans;
/* This file is copyright (c) Cheng-Wei Wu et al. and obtained under GPL license from the UP-Miner software.
 *  The modification made for integration in SPMF are (c) Philippe Fournier-Viger
 * 
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 * 
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.Qitem;
import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.UtilityListFHUQIMiner;
import ca.pfv.spmf.tools.MemoryLogger;


/**
 * This is an implementation of the VHUQI algorithm. The code was obtained from
 * the UP-Miner library under the GPL 3 license. The VHUQI algorithm is
 * described in this research paper:<br/>
 * <br/>
 * 
 * C. H. Li, C. Wu and V. S. Tseng, “Efficient Vertical Mining of High Utility
 * Quantitative Itemsets, ”Proc. of Int’l Conf. on Granular Computing,pp.
 * 155-160, 2014.<br/>
 * <br/>
 * 
 * Modification to the original implementation were made by Philippe
 * Fournier-Viger and Mourad Nouia to integrate it in SPMF, fix a bug and make some improvements.
 * 
 * @author Cheng Wei wu et al.,  revision by Mourad Nououa and Philippe Fournier-Viger
 */
public class AlgoVHUQI {

//Input Output Parameters
	/** Output file path */
	public String outputFile;

	/** Input file path */
	public String inputDatabase;

	/** Object to write results to file */
	private BufferedWriter writer_hqui = null;

//Maps
	/** map of a qitem to  its TWU */
	private Hashtable<Qitem, Integer> mapItemToTwu;
	/** map of an item to its profit */
	private Hashtable<Integer, Integer> mapItemToProfit;
	/** map of transasction to its utility */
	private Hashtable<Integer, Integer> mapTransactionToUtility;

//Algorithm Parameters
	/** minimum utility threshold */
	private long minUtil;

	/** total utility */
	private long totalU;

	/** coefficient */
	private int coefficient;

	/** combining method */
	private EnumCombination combiningMethod;

//For Evaluation
	/** peak memory usage */
	 long maxMemory = 0;

	/** start time of last execution */
	long startTime;

	/** end time of last execution */
	long endTime;
	
	/** minimum support percentage */
	public float percent;

	/** number of HUQIs that have been found */
	private int HUQIcount = 0;

	/** number of utility lists */
	private int countUL = 0;

	/** number of construction for utility lists */
	private int countConstruct = 0;

	/** the current Qitem */
	private Qitem currentQitem;

	/** the size of a temporary buffer for storing itemsets */
	private final int BUFFERS_SIZE = 200;
	
	/** a temporary buffer for storing itemsets */
	private Qitem[] itemsetBuffer = null;

	/** 
	 * Constructor 
	 */
	public AlgoVHUQI() {
	}

	/**
	 * Run the algorithm
	 * 
	 * @param inputData         path to the input data
	 * @param inputProfit       path to the profit information of each item
	 * @param minUtility        percentage
	 * @param coef              coefficient
	 * @param combinationmethod the combination method (1 = CombineMin, 2 =
	 *                          CombineMax, 3 = CombineAll)
	 * @param outputPath            the output file path
	 * @throws IOException if exception while reading or writing to file
	 */
	public void runAlgorithm(String inputData, String inputProfit, float minUtility, int coef,
			EnumCombination combinationmethod, String outputPath) throws IOException {
		System.gc();

		// Initialization
		MemoryLogger.getInstance().reset();
		this.startTime = System.currentTimeMillis();
		this.writer_hqui = new BufferedWriter(new FileWriter(outputPath));
		this.itemsetBuffer = new Qitem[BUFFERS_SIZE];
		this.coefficient = coef;
		this.percent = minUtility;
		this.combiningMethod = combinationmethod;
		this.mapItemToProfit = new Hashtable<Integer, Integer>();
		this.mapTransactionToUtility = new Hashtable<Integer, Integer>();
		this.totalU = 0;

		ArrayList<Qitem> qitemNameList = new ArrayList<Qitem>();
		Hashtable<Qitem, UtilityListFHUQIMiner> mapItemToUtilityList = new Hashtable<Qitem, UtilityListFHUQIMiner>();

		System.out.println("1. Build Initial Q-Utility Lists");
		buildInitialQUtilityLists(inputData, inputProfit, qitemNameList, mapItemToUtilityList);
		MemoryLogger.getInstance().checkMemory();

		System.out.println("2. Find Initial High Utility Range Q-items");
		ArrayList<Qitem> candidateList = new ArrayList<Qitem>();
		ArrayList<Qitem> hwQUI = new ArrayList<Qitem>();
		findInitialRHUQIs(qitemNameList, mapItemToUtilityList, candidateList, hwQUI);
		MemoryLogger.getInstance().checkMemory();

		System.out.println("3. Recurcive Mining Procedure");
		miner(itemsetBuffer, 0, null, mapItemToUtilityList, qitemNameList, writer_hqui, hwQUI);
		MemoryLogger.getInstance().checkMemory();
		endTime = System.currentTimeMillis();

//		prinStatistics();
//		writeFileStatistics();
		writer_hqui.close();
	}
	/**
	 * Print statistics about the algorithm execution
	 * 
	 * @param inputData
	 */
	public void printStatistics() {
		System.out.println("============= VHUQI v 2.45 Statistical results===============");
		System.out.println("MinUtil(%): " + percent);
		System.out.println("Coefficient:" + coefficient);
		System.out.println("HUQIcount:" + HUQIcount);
		System.out.println("Runtime: " + (double) (endTime - startTime) / 1000 + " (s)");
		System.out.println("Memory usage: " + MemoryLogger.getInstance().getMaxMemory() + " (Mb)");
		System.out.println("Join opertaion count: " + countConstruct);
		System.out.println("================================================");
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
	public void buildInitialQUtilityLists(String inputData, String inputProfit, ArrayList<Qitem> qitemNameList,
			Hashtable<Qitem, UtilityListFHUQIMiner> mapItemToUtilityList) throws IOException {

		BufferedReader br_profitTable = new BufferedReader(new FileReader(inputProfit));
		BufferedReader br_inputDatabase = new BufferedReader(new FileReader(inputData));

		// Build mapItemToProfit
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

		// Build mapItemToTWU
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
			}
			totalU += transactionU;
		}
		minUtil = (long) (totalU * percent);

		// Build mapItemToUtilityList
		for (Qitem item : mapItemToTwu.keySet()) {
			if (mapItemToTwu.get(item) >= Math.floor(minUtil / coefficient)) {
				UtilityListFHUQIMiner ul = new UtilityListFHUQIMiner(item, 0);
				mapItemToUtilityList.put(item, ul);
				qitemNameList.add(item);
			}
		}
		br_inputDatabase.close();
		MemoryLogger.getInstance().checkMemory();

		// Build MapFmap with MapItemToUtilityList
		br_inputDatabase = new BufferedReader(new FileReader(inputData));
		str = "";
		tid = 0;
		while ((str = br_inputDatabase.readLine()) != null) {
			tid++;
			String[] itemInfo = str.split(" ");
			ArrayList<Qitem> qItemset = new ArrayList<Qitem>();// line qItemset
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

			for (int i = 0; i < revisedTransaction.size(); i++) {
				Qitem q = revisedTransaction.get(i);
				// subtract the utility of this item from the remaining utility
				remainingUtility = remainingUtility - q.getQteMin() * mapItemToProfit.get(q.getItem());
				// get the utility list of this item
				UtilityListFHUQIMiner utilityListOfItem = mapItemToUtilityList.get(q);
				// Add a new Element to the utility list of this item corresponding to this
				// transaction
				QItemTrans element = new QItemTrans(tid, q.getQteMin() * mapItemToProfit.get(q.getItem()),
						remainingUtility);
				utilityListOfItem.addTrans(element);
				utilityListOfItem.addTWU(mapTransactionToUtility.get(tid));

			}
		}
		MemoryLogger.getInstance().checkMemory();
		// Sort the final list of Q-itemsets according to their utilities
		Collections.sort(qitemNameList, new Comparator<Qitem>() {
			public int compare(Qitem o1, Qitem o2) {
				return compareQItems(o1, o2);
			}
		});
		MemoryLogger.getInstance().checkMemory();
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
	public void findInitialRHUQIs(ArrayList<Qitem> qitemNameList,
			Hashtable<Qitem, UtilityListFHUQIMiner> mapItemToUtilityList, ArrayList<Qitem> candidateList,
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
				sb.append(" #UTIL: ");
				sb.append(utility);
				sb.append("\r\n");
				writer_hqui.write(sb.toString());
				hwQUI.add(qitemNameList.get(i));
				HUQIcount++;
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
	 * @param prefix a prefix
	 * @param prefixLength the length of the prefix
	 * @param candidateList the candidate list
	 * @param qItemNameList the qtiem list
	 * @param mapItemToUtilityList a map of item to utility list
	 * @param hwQUI hwQUI
	 * @return the result
	 * @throws IOException
	 */
	public ArrayList<Qitem> combineMethod(Qitem[] prefix, int prefixLength, ArrayList<Qitem> candidateList,
			ArrayList<Qitem> qItemNameList, Hashtable<Qitem, UtilityListFHUQIMiner> mapItemToUtilityList,
			ArrayList<Qitem> hwQUI) throws IOException {
		// Sort the candidate Q-itemsets according to items than Qte-Min than Qte-Max
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
	 * @throws IOException if error while reading or writing to file
	 */
	public void combineAll(Qitem[] prefix, int prefixLength, ArrayList<Qitem> candidateList,
			ArrayList<Qitem> qItemNameList, Hashtable<Qitem, UtilityListFHUQIMiner> mapItemToUtilityList,
			ArrayList<Qitem> hwQUI) throws IOException {
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
		Map<Qitem, UtilityListFHUQIMiner> mapRangeToUtilityList = new HashMap<Qitem, UtilityListFHUQIMiner>();

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
					UtilityListFHUQIMiner res;

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
							writeOut2(prefix, prefixLength, res.getSingleItemsetName(), res.getSumIutils());
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
						UtilityListFHUQIMiner ulQitem1 = mapRangeToUtilityList.get(qItem1);
						res = constructForCombine(ulQitem1, mapItemToUtilityList.get(candidateList.get(j)));
						count++;
						if (count > coefficient)
							break;
						mapRangeToUtilityList.put(res.getSingleItemsetName(), res);
						if (res.getSumIutils() > minUtil) {
							HUQIcount++;
							writeOut2(prefix, prefixLength, res.getSingleItemsetName(), res.getSumIutils());
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
	public void combineMin(Qitem[] prefix, int prefixLength, ArrayList<Qitem> candidateList,
			ArrayList<Qitem> qItemNameList, Hashtable<Qitem, UtilityListFHUQIMiner> mapItemToUtilityList,
			ArrayList<Qitem> hwQUI) throws IOException {

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
		Map<Qitem, UtilityListFHUQIMiner> temporaryMap = new HashMap<Qitem, UtilityListFHUQIMiner>();
		Map<Qitem, UtilityListFHUQIMiner> mapRangeToUtilityList = new HashMap<Qitem, UtilityListFHUQIMiner>();

		for (int i = 0; i < candidateList.size(); i++) {
			int currentItem = candidateList.get(i).getItem();
			mapRangeToUtilityList.clear();
			count = 1;
			for (int j = i + 1; j < candidateList.size(); j++) {
				int nextItem = candidateList.get(j).getItem();
				if (currentItem != nextItem)
					break;

				else {
					UtilityListFHUQIMiner res;
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
						UtilityListFHUQIMiner ulQitem1 = mapRangeToUtilityList.get(qItem1);
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
			writeOut2(prefix, prefixLength, currentQitem, temporaryMap.get(currentQitem).getSumIutils());
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

	public void combineMax(Qitem[] prefix, int prefixLength, ArrayList<Qitem> candidateList,
			ArrayList<Qitem> qItemNameList, Hashtable<Qitem, UtilityListFHUQIMiner> mapItemToUtilityList,
			ArrayList<Qitem> hwQUI) throws IOException {
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
		Map<Qitem, UtilityListFHUQIMiner> temporaryMap = new HashMap<Qitem, UtilityListFHUQIMiner>();
		Map<Qitem, UtilityListFHUQIMiner> mapRangeToUtilityList = new HashMap<Qitem, UtilityListFHUQIMiner>();
		int count;
		for (int i = 0; i < candidateList.size(); i++) {
			UtilityListFHUQIMiner res = new UtilityListFHUQIMiner();
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
						UtilityListFHUQIMiner ulQitem1 = mapRangeToUtilityList.get(qItem1);
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
			writeOut2(prefix, prefixLength, currentQitem, temporaryMap.get(currentQitem).getSumIutils());
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
	public UtilityListFHUQIMiner constructForCombine(UtilityListFHUQIMiner ulQitem1,
			UtilityListFHUQIMiner ulQitem2) {

		UtilityListFHUQIMiner result = new UtilityListFHUQIMiner(
				new Qitem(ulQitem1.getSingleItemsetName().getItem(), ulQitem1.getSingleItemsetName().getQteMin(),
						ulQitem2.getSingleItemsetName().getQteMax()));

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
	public UtilityListFHUQIMiner constructForJoin(UtilityListFHUQIMiner ul1, UtilityListFHUQIMiner ul2,
			UtilityListFHUQIMiner ul0) {
		if (ul1.getSingleItemsetName().getItem() == ul2.getSingleItemsetName().getItem())
			return null;

		ArrayList<QItemTrans> qT1 = ul1.getQItemTrans();
		ArrayList<QItemTrans> qT2 = ul2.getQItemTrans();
		UtilityListFHUQIMiner res = new UtilityListFHUQIMiner(ul2.getItemsetName());

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
					int ru1 = qT1.get(i).getRu();
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
	 * @throws IOException if error reading or writing to file
	 */
	public void miner(Qitem[] prefix, int prefixLength, UtilityListFHUQIMiner prefixUL,
			Hashtable<Qitem, UtilityListFHUQIMiner> ULs, ArrayList<Qitem> qItemNameList,
			BufferedWriter br_writer_hqui, ArrayList<Qitem> hwQUI) throws IOException {
		// For pruning range Q-itemsets using MapFmap

		ArrayList<Qitem> nextNameList = new ArrayList<Qitem>();

		for (int i = 0; i < qItemNameList.size(); i++) {

			nextNameList.clear();
			ArrayList<Qitem> nextHWQUI = new ArrayList<Qitem>();
			ArrayList<Qitem> candidateList = new ArrayList<Qitem>();
			Hashtable<Qitem, UtilityListFHUQIMiner> nextHUL = new Hashtable<Qitem, UtilityListFHUQIMiner>();
			// Hashtable<Qitem, UtilityListFHUQIMiner> candidateHUL = new Hashtable<Qitem,
			// UtilityListFHUQIMiner>();

			if (!hwQUI.contains(qItemNameList.get(i)))
				continue;

			for (int j = i + 1; j < qItemNameList.size(); j++) {

				if (qItemNameList.get(j).isRange())
					continue;

//            if (qItemNameList.get(i).isRange()&&j==i+1)
				// continue;

				UtilityListFHUQIMiner afterUL = null;
				afterUL = constructForJoin(ULs.get(qItemNameList.get(i)), ULs.get(qItemNameList.get(j)), prefixUL);

				if (afterUL != null && afterUL.getTwu() >= Math.floor(minUtil / coefficient)) {
					nextNameList.add(afterUL.getSingleItemsetName()); // item can be explored

					nextHUL.put(afterUL.getSingleItemsetName(), afterUL);
					countUL++;
					if (afterUL.getSumIutils() >= minUtil) {
						writeOut1(prefix, prefixLength, qItemNameList.get(i), qItemNameList.get(j),
								afterUL.getSumIutils());
						HUQIcount++;
						nextHWQUI.add(afterUL.getSingleItemsetName());
						// System.out.println("next is "+afterUL.getSingleItemsetName()+"util is
						// "+afterUL.getSumIutils());
					} else {
						if ((combiningMethod != EnumCombination.COMBINEMAX && afterUL.getSumIutils() >= Math.floor(minUtil / coefficient))
								|| (combiningMethod != EnumCombination.COMBINEMAX && afterUL.getSumIutils() >= Math.floor(minUtil / 2))) {
							candidateList.add(afterUL.getSingleItemsetName());
							// candidateHUL.put(afterUL.getSingleItemsetName(), afterUL);
						}
						if (afterUL.getSumIutils() + afterUL.getSumRutils() >= minUtil) {
							nextHWQUI.add(afterUL.getSingleItemsetName());
						}
					}
				}
			}

			if (candidateList.size() > 0) { // combine process
				nextNameList = combineMethod(prefix, prefixLength, candidateList, nextNameList, nextHUL, nextHWQUI);
				// candidateHUL.clear();
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
	 * Write an itemset to file
	 * 
	 * @param prefix       the prefix
	 * @param prefixLength the length of the prefix
	 * @param x            an item x that is appended to the prefix
	 * @param y            an item y that is appended to the prefix
	 * @param utility      the utility of the itemset
	 * @throws IOException if error while writing to file
	 */
	private void writeOut1(Qitem[] prefix, int prefixLength, Qitem x, Qitem y, long utility) throws IOException {

		// Create a string buffer
		StringBuilder buffer = new StringBuilder();
		// append the prefix
		for (int i = 0; i < prefixLength; i++) {
			buffer.append(prefix[i].toString());
			buffer.append(' ');
		}
		// append the last item
		buffer.append(x.toString() + " " + y.toString() + " #UTIL: ");

		// append the utility value
		buffer.append(utility);

		// write to file
		writer_hqui.write(buffer.toString());
		writer_hqui.newLine();

	}

	/**
	 * Write an itemset to file
	 * 
	 * @param prefix       the prefix
	 * @param prefixLength the length of the prefix
	 * @param x            an item x that is appended to the prefix
	 * @param utility      the utility of the itemset
	 * @throws IOException if error while writing to file
	 */
	private void writeOut2(Qitem[] prefix, int prefixLength, Qitem x, long utility) throws IOException {

		// Create a string buffer
		StringBuilder buffer = new StringBuilder();
		// append the prefix
		for (int i = 0; i < prefixLength; i++) {
			buffer.append(prefix[i].toString());
			buffer.append(' ');
		}
		// append the last item
		buffer.append(x.toString() + " #UTIL: ");

		// append the utility value

		buffer.append(utility);
		// write to file
		writer_hqui.write(buffer.toString());
		writer_hqui.newLine();

	}
	
	/**
	 * Write statistics about the algorithm execution to the file
	 * 
	 * @throws IOException if error while writing to file
	 */
	private void writeFileStatistics() throws IOException {

		StringBuilder buffer = new StringBuilder();

		buffer.append("#HUQIcount:");
		buffer.append(HUQIcount);
		buffer.append(System.lineSeparator());

		buffer.append("#runTime:");
		buffer.append((double) (endTime - startTime) / 1000);
		buffer.append(System.lineSeparator());

		buffer.append("#memory use:");
		buffer.append(MemoryLogger.getInstance().getMaxMemory());
		buffer.append(System.lineSeparator());

		buffer.append("#countUL:");
		buffer.append(countUL);
		buffer.append(System.lineSeparator());

		buffer.append("#countJoin:");
		buffer.append(countConstruct);
		buffer.append(System.lineSeparator());

		// write to file
		writer_hqui.write(buffer.toString());
		writer_hqui.newLine();

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

}
