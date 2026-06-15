
package ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.chuqiminer;

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
* Do not remove the copyright and license information from this file.
*/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.EnumCombination;
import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.QItemTrans;
import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.Qitem;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * Implementation of the CHUQI-Miner algorithm for mining quantitative high
 * utility itemsets. This algorithm is presented in this paper: <br/>
 * <br/>
 * Nouioua, M., Fournier-Viger, P., Qu, J.-F., Lin, J.-C., Gan, W., Song, W.
 * (2021). CHUQI-Miner: Correlated High Utility Quantitative Itemset Mining.
 * Proc. 4th International Workshop on Utility-Driven Mining (UDML 2021), in
 * conjunction with the ICDM 2021 conference, IEEE ICDM workshop proceedings,
 * 
 * @author Mourad Nouioua, copyright 2021
 */
public class AlgoCHUQIMiner {

//Input Output Parameters
	/** Object to write results to file */
	private BufferedWriter writer_hqui = null;

//Maps
	/** map of a qitem to its TWU */
	private Hashtable<Qitem, Integer> mapItemToTwu;

	/** map of an item to its profit */
	private Hashtable<Integer, Integer> mapItemToProfit;

	/** The SMAP structure */
	private Map<Qitem, Map<Qitem, TwuSupportPair>> mapSMAP;

	/** map of transasction to its utility */
	private Hashtable<Integer, Integer> mapTransactionToUtility;

	/** map qitem to real utility */
	private Map<Qitem, Long> realUtility = new HashMap<Qitem, Long>();

//Algorithm Parameters
	/** minimum bond */
	private double minBond;
	/** minimum utility threshold */
	private long minUtil;
	public int coun = 0;
	/** total utility */
	private long totalU;

	/** Combine method */
	CombineCHUQI_Miner c = new CombineCHUQI_Miner();

	/** coefficient */
	// private int coefficient;

	/** combining method */
	// private EnumCombination combiningMethod;

	/** the current Qitem */
	private Qitem currentQitem;

	/** the size of a temporary buffer for storing itemsets */
	private final int BUFFERS_SIZE = 200;

	/** a temporary buffer for storing itemsets */
	private Qitem[] itemsetBuffer = null;

	/** enable disable strategies */
	/** LA-PRUNE strategy */
	private boolean ENABLE_LA_PRUNE = true;

	/** FHM-PRUNE strategy */
	private boolean ENABLE_FHM_PRUNING = true;

	/** BOND PAIR PRUNING strategy */
	private boolean ENABLE_BOND_PAIR_PRUNING = true;

	/** SLA-PRUNE strategy */
	private boolean ENABLE_SLA_PRUNE = true;

	/** for evaluation */
	/** start time */
	private long startTime;

	/** end time */
	private long endTime;

	/** percent */
	private float percent;

	/** number of HUQIs that have been found */
	private int HUQIcount = 0;

	/** number of candidates pruned by LAPrune */
	private int candidateEliminatedByLAPrune = 0;

	/** number of candidates pruned by SLAPrune */
	private int candidateEliminatedBySLAPrune = 0;

	/** number of candidates pruned by FHM pruning */
	private int candidateEliminatedByFHMPruning = 0;

	/** number of candidates pruned by Bond Pruning */
	private int candidateEliminatedByBondPruning = 0;

	/** number of candidates pruned by ACU2B */
	private int candidateEliminatedByACU2B = 0;

	/** number of candidates */
	private int candidateCount = 0;

	/** number of construction operations done */
	private int countConstruct = 0;

	/** if true, display debug information */
	private final boolean DEBUG_MODE = true;

	class TwuSupportPair implements Serializable {
		private static final long serialVersionUID = -131200309501702633L;
		int support = 0;
		long twu = 0;

		public String toString() {
			String str = "";
			str += "(support=" + this.support + ",twu=" + this.twu + ")";
			return str;
		}
	}

	/**
	 * Print statistics about the algorithm execution
	 * 
	 * @param inputData
	 */
	public void printStatistics() {
		System.out.println("============= CHUQI-MINER v 2.66 ===============");
		System.out.println("MinUtil(%): " + percent);
		System.out.println("Coefficient:" + c.getCoefficient());
		System.out.println("HUQIcount: " + HUQIcount);
		System.out.println("Runtime: " + (double) (endTime - startTime) / 1000 + " (s)");
		System.out.println("Memory usage: " + MemoryLogger.getInstance().getMaxMemory() + " (Mb)");
		System.out.println("coun is : " + this.coun);
		if (DEBUG_MODE) {
			System.out.println("Join opertaion count: " + countConstruct);
		}
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
	private void buildInitialQUtilityLists(String inputData, String inputProfit, ArrayList<Qitem> qitemNameList,
			Hashtable<Qitem, QUtilityListCorr> mapItemToUtilityList) throws IOException {
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

		// 2. Build mapItemToTWU, Real Utility
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
		minUtil = (long) (totalU * percent) / 100;
		System.out.println(" " + minUtil);
		c.setMinUtil(minUtil);
		// 3. build mapItemToTwu
		for (Qitem item : mapItemToTwu.keySet()) {
			if (mapItemToTwu.get(item) >= Math.floor(minUtil / c.getCoefficient())) {
				QUtilityListCorr ul = new QUtilityListCorr(item);
				mapItemToUtilityList.put(item, ul);
				qitemNameList.add(item);
			}
		}

		// 4. Sort the final list of Q-itemsets according to their utilities
		Collections.sort(qitemNameList, new Comparator<Qitem>() {
			public int compare(Qitem o1, Qitem o2) {
				return compareQItems(o1, o2);
			}
		});
		br_inputDatabase.close();
		MemoryLogger.getInstance().checkMemory();

		// 5. Second database scan to fill MAPSMAP
		br_inputDatabase = new BufferedReader(new FileReader(inputData));
		str = "";
		tid = 0;
		mapSMAP = new HashMap<Qitem, Map<Qitem, TwuSupportPair>>();
		while ((str = br_inputDatabase.readLine()) != null) {
			tid++;
			String[] itemInfo = str.split(" ");
//			ArrayList<Qitem> qItemset = new ArrayList<Qitem>();// line qItemset
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
			}
			mapTransactionToUtility.put(tid, newTWU);
			Collections.sort(revisedTransaction, new Comparator<Qitem>() {
				public int compare(Qitem o1, Qitem o2) {
					return compareQItems(o1, o2);
				}
			});
//			int qAfterUtil;
			for (int i = 0; i < revisedTransaction.size(); i++) {
				Qitem current_q = revisedTransaction.get(i);
				// subtract the utility of this item from the remaining utility
				remainingUtility = remainingUtility - current_q.getQteMin() * mapItemToProfit.get(current_q.getItem());
				// get the utility list of this item
				QUtilityListCorr utilityListOfItem = mapItemToUtilityList.get(current_q);
				// Add a new Element to the utility list of this item corresponding to this
				// transaction
				utilityListOfItem.getBitSet().set(tid);
				// utilityListOfItem.support++;
				QItemTrans element = new QItemTrans(tid,
						current_q.getQteMin() * mapItemToProfit.get(current_q.getItem()), remainingUtility);
				utilityListOfItem.addTrans(element);
				utilityListOfItem.addTWU(mapTransactionToUtility.get(tid));
				// BEGIN NEW OPTIMIZATION
				Map<Qitem, TwuSupportPair> mapFMAPItem = mapSMAP.get(current_q);
				if (mapFMAPItem == null) {
					mapFMAPItem = new HashMap<Qitem, TwuSupportPair>();
					mapSMAP.put(current_q, mapFMAPItem);
				}
				for (int j = i + 1; j < revisedTransaction.size(); j++) {
					Qitem qAfter = revisedTransaction.get(j);
//					qAfterUtil = qAfter.getQteMin() * mapItemToProfit.get(qAfter.getItem());
					TwuSupportPair infoItem = mapFMAPItem.get(qAfter);
					if (infoItem == null) {
						infoItem = new TwuSupportPair();
					}
					infoItem.twu += newTWU;
					infoItem.support++;
					mapFMAPItem.put(qAfter, infoItem);

				}
			}
		}
		br_inputDatabase.close();
		MemoryLogger.getInstance().checkMemory();

		// 5. Sort the final list of Q-itemsets according to their utilities
		Collections.sort(qitemNameList, new Comparator<Qitem>() {
			public int compare(Qitem o1, Qitem o2) {
				return compareQItems(o1, o2);
			}
		});

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
			Hashtable<Qitem, QUtilityListCorr> mapItemToUtilityList, ArrayList<Qitem> hwQUI,
			ArrayList<Qitem> candidateList) throws IOException {
		// Check if a Q-itemset is:
		// 1. High,
		// 2. Candidate,
		// 3. To be explored or to be directly prunned

//		StringBuilder sb = new StringBuilder();
		System.out.println("hwQUI size is is " + hwQUI.size() + "  minutil " + minUtil);
		System.out.println("qitemNameList size is is " + qitemNameList.size() + "  minutil " + minUtil);
		for (int i = 0; i < qitemNameList.size(); i++) {
			long utility = mapItemToUtilityList.get(qitemNameList.get(i)).getSumIutils();
			if (utility >= minUtil) {
				writeOut(mapItemToUtilityList.get(qitemNameList.get(i)).getSingleItemsetName(),
						mapItemToUtilityList.get(qitemNameList.get(i)).getSumIutils(), 1);
				HUQIcount++;
				hwQUI.add(qitemNameList.get(i));
			} else {

				if ((c.getCombiningMethod() != EnumCombination.COMBINEMAX
						&& utility >= Math.floor(minUtil / c.getCoefficient())
						&& mapItemToUtilityList.get(qitemNameList.get(i)).getBond() >= minBond)
						|| (c.getCombiningMethod() == EnumCombination.COMBINEMAX && utility >= Math.floor(minUtil / 2)
								&& mapItemToUtilityList.get(qitemNameList.get(i)).getBond() >= minBond)) {
					candidateList.add(qitemNameList.get(i));
				}
				if (utility + mapItemToUtilityList.get(qitemNameList.get(i)).getSumRutils() >= minUtil) {

					hwQUI.add(qitemNameList.get(i));
				}

			}
		}
		MemoryLogger.getInstance().checkMemory();
		// Perform the combination process on the candidate q-itemsets
		// if (candidateList.size() > 0){
		// c.setRangeHUQItoZero();
		// qitemNameList = c.combineMethod(null, 0, candidateList, qitemNameList,
		// mapItemToUtilityList, hwQUI,writer_hqui);
		// HUQIcount=HUQIcount+c.getRangeHUQI();
		// }
	}

	/**
	 * Write an item to file
	 * 
	 * @param x       the item
	 * @param utility its utility
	 * @param bound   its bond value
	 * @throws IOException if error while writing to file
	 */
	private void writeOut(Qitem x, long utility, double bound) throws IOException {

		// Create a string buffer
		StringBuilder buffer = new StringBuilder();
		// append the prefix

		// append the last item
		buffer.append(x.toString() + " #UTIL: ");
		buffer.append(utility);
		buffer.append(" #BOND: ");
		buffer.append(bound);

		// write to file
		writer_hqui.write(buffer.toString());
		writer_hqui.newLine();

	}

	private void writeOut1(Qitem[] prefix, int prefixLength, Qitem x, Qitem y, long utility, double bound)
			throws IOException {

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
		buffer.append(" #BOND: ");
		buffer.append(bound);

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
	 * Method to construct the utility list of an itemset
	 * 
	 * @param ulQitem1 the utility list of a qitem
	 * @param ulQitem2 the utility list of another qitem
	 * @return the resulting utility list
	 */
	private QUtilityListCorr construct(QUtilityListCorr ul1, QUtilityListCorr ul2, QUtilityListCorr ul0,
			long minUtility, BitSet bitsetPXY) {

		if (ul1.getSingleItemsetName().getItem() == ul2.getSingleItemsetName().getItem())
			return null;
		// Initialize the sum of total utility
		double maxdisjunctivesupport = bitsetPXY.cardinality();

		double pxsupport = ul1.getSupport();
		int minSup = (int) Math.ceil(maxdisjunctivesupport * minBond);

		// double pysupport = ul2.getqItemTransLength();

		ArrayList<QItemTrans> qT1 = ul1.getTransactions();
		ArrayList<QItemTrans> qT2 = ul2.getTransactions();
		long totalUtility = ul1.getSumIutils() + ul1.getSumRutils();
		QUtilityListCorr res = new QUtilityListCorr(ul2.getSingleItemsetName(), bitsetPXY);

		if (ul0 == null) {
			int i = 0, j = 0;
			while (i < qT1.size() && j < qT2.size()) {
				int tid1 = qT1.get(i).getTid();
				int tid2 = qT2.get(j).getTid();

				if (tid1 == tid2) {

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
					/*
					 * if (ENABLE_LA_PRUNE) { totalUtility -= (qT1.get(i).getEu() +
					 * qT1.get(i).getRu()); if (totalUtility < minUtility) {
					 * candidateEliminatedByLAPrune++; return null; } } if (ENABLE_SLA_PRUNE) {
					 * pxsupport--; if (pxsupport < minSup) { candidateEliminatedBySLAPrune++;
					 * return null; } }
					 */
					i++;
				}
			}
		} else {
			ArrayList<QItemTrans> preQT = ul0.getTransactions();
			int i = 0, j = 0, k = 0;
			while (i < qT1.size() && j < qT2.size()) {
				int tid1 = qT1.get(i).getTid();
				int tid2 = qT2.get(j).getTid();

				if (tid1 == tid2) {

					// QItemTrans combine = new QItemTrans();
					int eu1 = qT1.get(i).getEu();
//					int ru1 = qT1.get(i).getRu();
					int eu2 = qT2.get(j).getEu();

					// Ñ‚ãƒ¦î ‡î€™ preitemî€™utilityî…
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
					/*
					 * if (ENABLE_LA_PRUNE) { totalUtility -= (qT1.get(i).getEu() +
					 * qT1.get(i).getRu()); if (totalUtility < minUtility) {
					 * candidateEliminatedByLAPrune++; return null; } } if (ENABLE_SLA_PRUNE) {
					 * pxsupport--; if (pxsupport < minSup) { candidateEliminatedBySLAPrune++;
					 * return null; } }
					 */
					i++;
				}
			}
		}

		MemoryLogger.getInstance().checkMemory();
		if (!res.getTransactions().isEmpty()) {
			// countConstruct++;
			// System.out.println("XY is "+res.toString());
			return res;
		}
		return null;
	}

	/**
	 * Method to construct the utility list of an itemset
	 * 
	 * @param ulQitem1 the utility list of a qitem
	 * @param ulQitem2 the utility list of another qitem
	 * @return the resulting utility list
	 */
	private QUtilityListCorr constructvide(QUtilityListCorr ul1, QUtilityListCorr ul2, QUtilityListCorr ul0,
			BitSet bitsetPXY) {

		if (ul1.getSingleItemsetName().getItem() == ul2.getSingleItemsetName().getItem())
			return null;
		// Initialize the sum of total utility
		double maxdisjunctivesupport = bitsetPXY.cardinality();

		double pxsupport = ul1.getSupport();
		int minSup = (int) Math.ceil(maxdisjunctivesupport * minBond);

		ArrayList<QItemTrans> qT1 = ul1.getTransactions();
		ArrayList<QItemTrans> qT2 = ul2.getTransactions();
		long totalUtility = ul1.getSumIutils() + ul1.getSumRutils();
		QUtilityListCorr res = new QUtilityListCorr(ul2.getSingleItemsetName(), bitsetPXY);

		if (ul0 == null) {
			int i = 0, j = 0;
			while (i < qT1.size() && j < qT2.size()) {
				int tid1 = qT1.get(i).getTid();
				int tid2 = qT2.get(j).getTid();

				if (tid1 == tid2) {
					int eu1 = qT1.get(i).getEu();
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
					if (ENABLE_LA_PRUNE) {
						totalUtility -= (qT1.get(i).getEu() + qT1.get(i).getRu());
						if (totalUtility < minUtil) {
							candidateEliminatedByLAPrune++;
							return null;
						}
					}
					i++;
				}
			}
		} else {
			ArrayList<QItemTrans> preQT = ul0.getTransactions();
			int i = 0, j = 0, k = 0;
			while (i < qT1.size() && j < qT2.size()) {
				int tid1 = qT1.get(i).getTid();
				int tid2 = qT2.get(j).getTid();

				if (tid1 == tid2) {

					// QItemTrans combine = new QItemTrans();
					int eu1 = qT1.get(i).getEu();
//					int ru1 = qT1.get(i).getRu();
					int eu2 = qT2.get(j).getEu();
					// Ñ‚ãƒ¦î ‡î€™ preitemî€™utilityî…
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
					if (ENABLE_LA_PRUNE) {
						totalUtility -= (qT1.get(i).getEu() + qT1.get(i).getRu());
						if (totalUtility < minUtil) {
							candidateEliminatedByLAPrune++;
							return null;
						}
					}
					i++;
				}
			}
		}

		MemoryLogger.getInstance().checkMemory();
		if (!res.getTransactions().isEmpty()) {
			return res;
		}
		return null;
	}

	public void miner(Qitem[] prefix, int prefixLength, QUtilityListCorr prefixUL,
			Hashtable<Qitem, QUtilityListCorr> ULs, ArrayList<Qitem> qItemNameList, BufferedWriter br_writer_hqui,
			ArrayList<Qitem> hwQUI) throws IOException {
		int[] t2 = new int[c.getCoefficient()];

		for (int i = 0; i < qItemNameList.size(); i++) {

			QUtilityListCorr X = ULs.get(qItemNameList.get(i));
			ArrayList<Qitem> nextNameList = new ArrayList<Qitem>();
			ArrayList<Qitem> nextHWQUI = new ArrayList<Qitem>();
			ArrayList<Qitem> candidateList = new ArrayList<Qitem>();
			Hashtable<Qitem, QUtilityListCorr> nextHUL = new Hashtable<Qitem, QUtilityListCorr>();

			if (!hwQUI.contains(qItemNameList.get(i))) {
				// System.out.println("yes");
				continue;
			}

			for (int j = i + 1; j < qItemNameList.size(); j++) {

				QUtilityListCorr Y = ULs.get(qItemNameList.get(j));
				// System.out.println("X is "+X.toString());
				// System.out.println("Y is "+Y.toString());
				// Co-occurence pruning strategy
				Map<Qitem, TwuSupportPair> mapTWUF = mapSMAP.get(qItemNameList.get(i));
				int min_consup = 0;
				if (mapTWUF != null) {
					TwuSupportPair twuF = mapTWUF.get(qItemNameList.get(j));
					if (twuF == null || twuF.twu < minUtil) {
						continue;
					}
					/*
					 * if(ENABLE_BOND_PAIR_PRUNING) { int
					 * max_dissup=Y.getDisjunctive().cardinality()>X.getDisjunctive().cardinality()?
					 * Y.getDisjunctive().cardinality():X.getDisjunctive().cardinality();
					 * if((min_consup=twuF.support)>X.getSupport()){ min_consup=X.getSupport(); }
					 * if((min_consup)>Y.getSupport()){ min_consup=Y.getSupport(); } }
					 */
				} else {
					long sumtwu = 0;
					long sum = 0;
					for (int ii = qItemNameList.get(i).getQteMin(); ii <= qItemNameList.get(i).getQteMax(); ii++) {
						if (mapSMAP.get(qItemNameList.get(Math.min(t2[ii - qItemNameList.get(i).getQteMin()], j)))
								.get(qItemNameList.get(Math.max(t2[ii - qItemNameList.get(i).getQteMin()], j))) != null)
							sum = mapSMAP.get(qItemNameList.get(Math.min(t2[ii - qItemNameList.get(i).getQteMin()], j)))
									.get(qItemNameList.get(Math.max(t2[ii - qItemNameList.get(i).getQteMin()], j))).twu;
						else
							continue;
						sumtwu = sumtwu + sum;
					}
					if (sumtwu == 0 || sumtwu < Math.floor(minUtil / c.getCoefficient())) {
						continue;
					}

				}
				candidateCount++;
				// =======new optimization avoiding construct utility_list using up_bound
				BitSet bitsetPX = X.getDisjunctive();
				BitSet bitsetPY = Y.getDisjunctive();
				BitSet bitsetPXY = performOR(bitsetPX, bitsetPY);

				if (min_consup / (double) bitsetPXY.cardinality() < minBond) {
					candidateEliminatedByACU2B++;
					continue;
				}

				QUtilityListCorr temp = construct(X, Y, prefixUL, minUtil, bitsetPXY);

				// UtilityListFCHM_bond temp = construct(prefixUL,X, Y,minUtil, bitsetPXY);
				if (temp != null && temp.getBond() >= minBond
						&& temp.getTwu() >= Math.floor(minUtil / c.getCoefficient())) {

					nextHUL.put(temp.getSingleItemsetName(), temp);
					nextNameList.add(temp.getSingleItemsetName());
					if (temp.getSumIutils() >= minUtil) {
						writeOut1(prefix, prefixLength, qItemNameList.get(i), qItemNameList.get(j), temp.getSumIutils(),
								temp.getBond());
						nextHWQUI.add(temp.getSingleItemsetName());

						HUQIcount++;
					} else {
						if (temp.getSumIutils() + temp.getSumRutils() >= minUtil) {

							nextHWQUI.add(temp.getSingleItemsetName());
						}
						if ((c.getCombiningMethod() != EnumCombination.COMBINEMAX
								&& temp.getSumIutils() >= Math.floor(minUtil / c.getCoefficient()))
								|| (c.getCombiningMethod() == EnumCombination.COMBINEMAX
										&& temp.getSumIutils() >= Math.floor(minUtil / 2))) {
							candidateList.add(temp.getSingleItemsetName());

						}
					}
				}
			}

			if (candidateList.size() > 0) {
				c.setRangeHUQItoZero();
				nextNameList = c.combineMethod(null, 0, candidateList, nextNameList, nextHUL, nextHWQUI, writer_hqui);
				HUQIcount = HUQIcount + c.getRangeHUQI();
				candidateList.clear();
			}
			if (nextNameList.size() >= 1) { // recurcive call
				itemsetBuffer[prefixLength] = qItemNameList.get(i);

				miner(itemsetBuffer, prefixLength + 1, ULs.get(qItemNameList.get(i)), nextHUL, nextNameList,
						br_writer_hqui, nextHWQUI);

			}
		}
	}

	public void miner2(Qitem[] prefix, int prefixLength, QUtilityListCorr prefixUL,
			Hashtable<Qitem, QUtilityListCorr> ULs, ArrayList<Qitem> qItemNameList, BufferedWriter br_writer_hqui,
			ArrayList<Qitem> hwQUI) throws IOException {
		int[] t2 = new int[c.getCoefficient()];
		ArrayList<Qitem> nextNameList = new ArrayList<Qitem>();
		for (int i = 0; i < qItemNameList.size(); i++) {

			nextNameList.clear();
			ArrayList<Qitem> nextHWQUI = new ArrayList<Qitem>();
			ArrayList<Qitem> candidateList = new ArrayList<Qitem>();
			Hashtable<Qitem, QUtilityListCorr> nextHUL = new Hashtable<Qitem, QUtilityListCorr>();
			Hashtable<Qitem, QUtilityListCorr> candidateHUL = new Hashtable<Qitem, QUtilityListCorr>();

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

				QUtilityListCorr afterUL = null;
				// Co-occurence pruning strategy
				Map<Qitem, TwuSupportPair> mapTWUF = mapSMAP.get(qItemNameList.get(i));

				if (mapTWUF != null) {
					this.coun++;
					TwuSupportPair twuF = mapTWUF.get(qItemNameList.get(j));
					if (twuF == null || twuF.twu < Math.floor(minUtil / c.getCoefficient())) {
						if (twuF == null)
							continue;
					} else {
						BitSet bitsetPX = ULs.get(qItemNameList.get(i)).getDisjunctive();
						BitSet bitsetPY = ULs.get(qItemNameList.get(j)).getDisjunctive();
						BitSet bitsetPXY = performOR(bitsetPX, bitsetPY);
						afterUL = constructvide(ULs.get(qItemNameList.get(i)), ULs.get(qItemNameList.get(j)), prefixUL,
								bitsetPXY);
						countConstruct++;
						if (afterUL == null || afterUL.getTwu() < Math.floor(minUtil / c.getCoefficient()))
							continue;
					}
				}
				/*
				 * else{
				 * 
				 * long sumtwu=0; long sum=0; for (int
				 * ii=qItemNameList.get(i).getQteMin();ii<=qItemNameList.get(i).getQteMax();ii++
				 * ){ if
				 * (mapSMAP.get(qItemNameList.get(Math.min(t2[ii-qItemNameList.get(i).getQteMin(
				 * )],j))).get(qItemNameList.get(Math.max(t2[ii-qItemNameList.get(i).getQteMin()
				 * ],j)))!=null)
				 * sum=mapSMAP.get(qItemNameList.get(Math.min(t2[ii-qItemNameList.get(i).
				 * getQteMin()],j))).get(qItemNameList.get(Math.max(t2[ii-qItemNameList.get(i).
				 * getQteMin()],j))).twu; else continue; sumtwu=sumtwu+sum; }
				 * 
				 * if(sumtwu == 0 || sumtwu < Math.floor(minUtil/c.getCoefficient())){ continue;
				 * } else{ BitSet bitsetPX = ULs.get(qItemNameList.get(i)).getDisjunctive();
				 * BitSet bitsetPY = ULs.get(qItemNameList.get(j)).getDisjunctive(); BitSet
				 * bitsetPXY = performOR(bitsetPX, bitsetPY);
				 * afterUL=constructvide(ULs.get(qItemNameList.get(i)),
				 * ULs.get(qItemNameList.get(j)), prefixUL,bitsetPXY); countConstruct++;
				 * if(afterUL ==null || afterUL.getTwu()<Math.floor(minUtil/c.getCoefficient()))
				 * continue; }
				 * 
				 * }
				 */

				if (afterUL != null && afterUL.getTwu() >= Math.floor(minUtil / c.getCoefficient())) {
					nextNameList.add(afterUL.getSingleItemsetName()); // item can be explored
					nextHUL.put(afterUL.getSingleItemsetName(), afterUL);
					if (afterUL.getSumIutils() >= minUtil) {
						writeOut1(prefix, prefixLength, qItemNameList.get(i), qItemNameList.get(j),
								afterUL.getSumIutils(), 1);
						HUQIcount++;
						nextHWQUI.add(afterUL.getSingleItemsetName());
					} else {
						if ((c.getCombiningMethod() != EnumCombination.COMBINEMAX
								&& afterUL.getSumIutils() >= Math.floor(minUtil / c.getCoefficient()))
								|| (c.getCombiningMethod() == EnumCombination.COMBINEMAX
										&& afterUL.getSumIutils() >= Math.floor(minUtil / 2))) {
							candidateList.add(afterUL.getSingleItemsetName());
							candidateHUL.put(afterUL.getSingleItemsetName(), afterUL);
						}
						if (afterUL.getSumRutils() + afterUL.sumIutilsNonZero() >= minUtil) {
							nextHWQUI.add(afterUL.getSingleItemsetName());
						}
					}
				}
			}
			if (nextNameList.size() >= 1) { // recurcive call
				itemsetBuffer[prefixLength] = qItemNameList.get(i);
				miner2(itemsetBuffer, prefixLength + 1, ULs.get(qItemNameList.get(i)), nextHUL, nextNameList,
						br_writer_hqui, nextHWQUI);
			}

		}

	}

	BitSet performOR(BitSet tidsetI, BitSet tidsetJ) {
		// Create the new tidset and perform the logical AND to intersect the tidset
		BitSet bitsetSupportIJ = new BitSet();
		bitsetSupportIJ = (BitSet) tidsetI.clone();
		bitsetSupportIJ.or(tidsetJ);
		return bitsetSupportIJ;
	}

	/**
	 * Run the algorithm
	 * 
	 * @param inputData         path to the input data
	 * @param inputProfit       path to the profit information of each item
	 * @param percentage        percentage
	 * @param minbond           minimum bond threshold
	 * @param coef              coefficient
	 * @param combinationmethod the combination method CombineMin, CombineMax,
	 *                          CombineAll)
	 * @param outputPath        the output file path
	 * @throws IOException if exception while reading or writing to file
	 */
	public void runAlgorithm(String inputData, String inputProfit, float percentage, double minBond, int coef,
			EnumCombination combineMethod, String output) throws IOException {
		System.gc();

		// Initialization
		MemoryLogger.getInstance().reset();
		startTime = System.currentTimeMillis();
		writer_hqui = new BufferedWriter(new FileWriter(output));
		itemsetBuffer = new Qitem[BUFFERS_SIZE];
		mapItemToProfit = new Hashtable<Integer, Integer>();
		mapTransactionToUtility = new Hashtable<Integer, Integer>();
		totalU = 0;
		this.minBond = minBond;
		c.setCoefficient(coef);
		c.setCombiningMethod(combineMethod);
		// this.combiningMethod = combineMethod;
		// this.coefficient = coef;
		this.percent = percentage;

		ArrayList<Qitem> qitemNameList = new ArrayList<Qitem>();
		Hashtable<Qitem, QUtilityListCorr> mapItemToUtilityList = new Hashtable<Qitem, QUtilityListCorr>();

		if (DEBUG_MODE) {
			System.out.println("1. Build Initial Q-Utility Lists");
		}
		buildInitialQUtilityLists(inputData, inputProfit, qitemNameList, mapItemToUtilityList);
		MemoryLogger.getInstance().checkMemory();

		if (DEBUG_MODE) {
			System.out.println("2. Find Initial High Utility Range Q-items");
		}
		ArrayList<Qitem> candidateList = new ArrayList<Qitem>();
		ArrayList<Qitem> hwQUI = new ArrayList<Qitem>();
		findInitialRHUQIs(qitemNameList, mapItemToUtilityList, hwQUI, candidateList);
		MemoryLogger.getInstance().checkMemory();

		if (DEBUG_MODE) {
			System.out.println("3. Recurcive Mining Procedure");
			System.out.println("qitemNameList size is " + qitemNameList.size());
			System.out.println("hwQUI after findInitialRHUQIs is " + hwQUI.size());
		}
		miner2(itemsetBuffer, 0, null, mapItemToUtilityList, qitemNameList, writer_hqui, hwQUI);
		MemoryLogger.getInstance().checkMemory();
		writer_hqui.close();
		endTime = System.currentTimeMillis();

	}
}
