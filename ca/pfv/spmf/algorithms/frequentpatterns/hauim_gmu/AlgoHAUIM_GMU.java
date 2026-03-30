package ca.pfv.spmf.algorithms.frequentpatterns.hauim_gmu;
/*This file is copyright (c) 2021 Wei Song, Lu Liu, Chaomin Huang
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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is an implementation of the "HAUIM-GMU Algorithm" for High
 * Average-Utility Itemsets Mining as described in the conference paper :
 * 
 * Generalized maximal utility for mining high average-utility itemsets
 * Knowledge and Information Systems (2021) 63:2947–2967
 *
 * @author Wei Song,Lu Liu,Chaomin Huang
 */

public class AlgoHAUIM_GMU {
	/** the mau */
	int mau = 0;
	
	/** map of item to AUUB */
	Map<Integer, Integer> mapItemToAUUB;
	
	/** list of items */
	List<Integer> itemLists = new ArrayList<Integer>();

	/** Class to store info about items */
	class ItemInfo {
		/** the bitset representing the transaction ids */
		BitSet tidset = new BitSet();
		/** a map from tid to utility */
		Map<Integer, Integer> mapTidToUtility = new HashMap<Integer, Integer>();

		/** get the tidset of this item
		 * 
		 * @return the tidset
		 */
		public BitSet getTidset() {
			return tidset;
		}

		/** Set the tidset of this item
		 * 
		 * @param tidset the tidset as a bitvector
		 */
		public void setTidset(BitSet tidset) {
			this.tidset = tidset;
		}
	}

	/**  A mapping from an item to the information about the item */
	Map<Integer, ItemInfo> mapItemToItemInfo = new HashMap<Integer, ItemInfo>();
	
	/** The max utility list */
	List<Integer> maxUtilityList = new ArrayList<Integer>();
	
	/** A map from transaction to utility */
	Map<Integer, List<Integer>> mapTransactionToUtility = new HashMap<Integer, List<Integer>>();
	
	/** the minimum support */
	int minSupport = 0;
	
	/** the maximum itemset size */
	int maxItemsetSize = 0;
	
	/** the maximum tid size */
	int maxTidSize = 0;
	
	/** the maximum memory */
	double maxMemory = 0;
	
	/** the start time of the last execution */
	long startTimestamp = 0;
	
	/** the end time of the last execution */
	long endTimestamp = 0;
	
	/** the number of high average utility itemsets that has been found */
	double HAUICount = 0;
	
	/** object to write the output file */
	BufferedWriter writer = null;
	
	/**
	 * Run the algorithm
	 * @param input the input file path
	 * @param output the output file path
	 * @param threshold the minimum utility threshold
	 * @throws IOException if error while writing or reading the files
	 */
	public void runAlgorithm(String input, String output, int threshold) throws IOException {
		writer = new BufferedWriter(new FileWriter(output));
		startTimestamp = System.currentTimeMillis();
		mau = threshold;
		BufferedReader myInput = null;
		String thisLine;
		mapItemToAUUB = new HashMap<Integer, Integer>();
		try {
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			while ((thisLine = myInput.readLine()) != null) {
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}
				String split[] = thisLine.split(":");
				String items[] = split[0].split(" ");
				String utilityValues[] = split[2].split(" ");
				Integer MU = Integer.MIN_VALUE;
				for (int i = 0; i < utilityValues.length; i++) {
					if (MU < Integer.parseInt(utilityValues[i])) {
						MU = Integer.parseInt(utilityValues[i]);
					}
				}
				for (int i = 0; i < items.length; i++) {
					Integer item = Integer.parseInt(items[i]);
					Integer auub = mapItemToAUUB.get(item);
					auub = (auub == null) ? MU : auub + MU;
					mapItemToAUUB.put(item, auub);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}
		maxItemsetSize = mapItemToAUUB.keySet().size();
		for (Integer item : mapItemToAUUB.keySet()) {
			if (mapItemToAUUB.get(item) >= mau) {
				itemLists.add(item);
			}
		}
		Collections.sort(itemLists);
		try {
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			int tid = 0;
			while ((thisLine = myInput.readLine()) != null) {
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%'
						|| thisLine.charAt(0) == '@') {
					continue;
				}
				String split[] = thisLine.split(":");
				String items[] = split[0].split(" ");
				String utilityValues[] = split[2].split(" ");
				int maxUtility = 0;
				ArrayList<Integer> tempUtilityList = new ArrayList<Integer>();
				for (int i = 0; i < utilityValues.length; i++) {
					if (mapItemToAUUB.get(Integer.parseInt(items[i])) >= mau) {
						if (mapItemToItemInfo.containsKey(Integer.parseInt(items[i]))) {
							// true
							mapItemToItemInfo.get(Integer.parseInt(items[i])).tidset.set(tid);
							mapItemToItemInfo.get(Integer.parseInt(items[i])).mapTidToUtility.put(tid,
									Integer.parseInt(utilityValues[i]));
						} else {
							// false
							ItemInfo iteminfo = new ItemInfo();
							iteminfo.tidset.set(tid);
							iteminfo.mapTidToUtility.put(tid, Integer.parseInt(utilityValues[i]));
							mapItemToItemInfo.put(Integer.parseInt(items[i]), iteminfo);
						}
						tempUtilityList.add(Integer.parseInt(utilityValues[i]));
						if (maxUtility < Integer.parseInt(utilityValues[i])) {
							maxUtility = Integer.parseInt(utilityValues[i]);
						}
					}
				}
				maxUtilityList.add(maxUtility);
				if (!tempUtilityList.isEmpty()) {
					Collections.sort(tempUtilityList);
					Collections.reverse(tempUtilityList);
					List<Integer> utilityCumulationList = new ArrayList<Integer>();
					utilityCumulationList.add(tempUtilityList.get(0));
					for (int i = 1; i < tempUtilityList.size(); i++) {
						utilityCumulationList.add(tempUtilityList.get(i) + utilityCumulationList.get(i - 1));
					}
					mapTransactionToUtility.put(tid, utilityCumulationList);
				}
				tid++;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}
		maxTidSize = mapTransactionToUtility.size();
		Collections.sort(maxUtilityList);
		Collections.reverse(maxUtilityList);
		int sum = 0;
		for (Integer maxUtility : maxUtilityList) {
			sum = sum + maxUtility;
			if (sum >= mau) {
				minSupport = minSupport + 1;
				break;
			} else {
				minSupport = minSupport + 1;
			}
		}

		for (int i = 0; i < itemLists.size(); i++) {
			List<Integer> itemsetBuffer = new ArrayList<Integer>();
			Integer itemI = itemLists.get(i);
			itemsetBuffer.add(itemI);
			ItemInfo iteminfoI = mapItemToItemInfo.get(itemI);
			BitSet tidsetI = iteminfoI.getTidset();
			saveItem(itemI, tidsetI);
			List<Integer> equivalenceClassIitems = new ArrayList<Integer>();
			List<BitSet> equivalenceClassItidsets = new ArrayList<BitSet>();
			for (int j = i + 1; j < itemLists.size(); j++) {
				int itemJ = itemLists.get(j);
				ItemInfo iteminfoJ = mapItemToItemInfo.get(itemJ);
//				BitSet tidsetJ = iteminfoJ.getTidset();
				BitSet tidsetIJ = (BitSet) tidsetI.clone();
				tidsetIJ.and(iteminfoJ.getTidset());
				if (tidsetIJ.cardinality() >= minSupport) {
					int auubIJ = 0;
					for (int tid = 0; tid < maxTidSize; tid++) {
						if (tidsetIJ.get(tid)) {
							auubIJ = auubIJ + mapTransactionToUtility.get(tid).get(1);
						}

					}
					auubIJ = auubIJ / 2;
					if (auubIJ >= mau) {
						equivalenceClassIitems.add(itemJ);
						equivalenceClassItidsets.add(tidsetIJ);
					}
				}
			}
			if (equivalenceClassIitems.size() > 0) {
				processEquivalenceClass(itemsetBuffer, 1, equivalenceClassIitems, equivalenceClassItidsets);
			}
		}
		checkMemory();
		checkMemory();
		writer.close();
		endTimestamp = System.currentTimeMillis();
	}

	/** Check the memory usage */
	private void checkMemory() {
		double currentMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024d / 1024d;
		if (currentMemory > maxMemory) {
			maxMemory = currentMemory;
		}
	}

	/** 
	 * Process an equivalence class
	 * @param prefix the prefix itemset
	 * @param prefixLength the length of the prefix
	 * @param equivalenceClassItems the items in the equivalence class
	 * @param equivalenceClassTidsets the tidsets of the items in the equivalence class
	 * @throws IOException if error while reading or writing to file
	 */
	private void processEquivalenceClass(List<Integer> prefix, int prefixLength, List<Integer> equivalenceClassItems,
			List<BitSet> equivalenceClassTidsets) throws IOException {
		if (equivalenceClassItems.size() == 1) {
			int itemI = equivalenceClassItems.get(0);
			BitSet tidsetItemset = equivalenceClassTidsets.get(0);
			save(prefix, itemI, tidsetItemset);
			return;
		}
		if (equivalenceClassItems.size() == 2) {
			int itemI = equivalenceClassItems.get(0);
			BitSet tidsetI = equivalenceClassTidsets.get(0);
			save(prefix, itemI, tidsetI);
			int itemJ = equivalenceClassItems.get(1);
			BitSet tidsetJ = equivalenceClassTidsets.get(1);
			save(prefix, itemJ, tidsetJ);
			if (prefixLength + 2 <= maxItemsetSize) {
				BitSet tidsetPIJ = (BitSet) tidsetI.clone();
				tidsetPIJ.and(tidsetJ);
				if (tidsetPIJ.cardinality() >= minSupport) {
					int newPrefixLength = prefixLength + 1;
					int auubPIJ = 0;
					for (int tid = 0; tid < maxTidSize; tid++) {
						if (tidsetPIJ.get(tid)) {
							auubPIJ = auubPIJ + mapTransactionToUtility.get(tid).get(newPrefixLength);
						}
					}
					auubPIJ = auubPIJ / (newPrefixLength + 1);
					if (auubPIJ >= mau) {
						prefix.add(itemI);
						save(prefix, itemJ, tidsetPIJ);
					}
				}
			}
			return;
		}
		for (int i = 0; i < equivalenceClassItems.size(); i++) {
			int suffixI = equivalenceClassItems.get(i);
			BitSet tidsetI = equivalenceClassTidsets.get(i);
			save(prefix, suffixI, tidsetI);
			if (prefixLength + 2 <= maxItemsetSize) {
				List<Integer> equivalenceClassISuffixItems = new ArrayList<Integer>();
				List<BitSet> equivalenceClassISuffixTidsets = new ArrayList<BitSet>();
				for (int j = i + 1; j < equivalenceClassItems.size(); j++) {
					int suffixJ = equivalenceClassItems.get(j);
//					BitSet tidsetJ = equivalenceClassTidsets.get(j);
					BitSet tidsetIJ = (BitSet) tidsetI.clone();
					tidsetIJ.and(equivalenceClassTidsets.get(j));
					if (tidsetIJ.cardinality() >= minSupport) {
						int auub = 0;
						for (int t = 0; t < maxTidSize; t++) {
							if (tidsetIJ.get(t)) {
								auub = auub + mapTransactionToUtility.get(t).get(prefixLength + 1);
							}
						}
						auub = auub / (prefixLength + 2);
						if (auub >= mau) {
							equivalenceClassISuffixItems.add(suffixJ);
							equivalenceClassISuffixTidsets.add(tidsetIJ);
						}
					}
				}
				if (equivalenceClassISuffixItems.size() > 0) {
					List<Integer> newPrefix = new ArrayList<Integer>(prefix);
					newPrefix.add(suffixI);
					int newPrefixLength = prefixLength + 1;
					processEquivalenceClass(newPrefix, newPrefixLength, equivalenceClassISuffixItems,
							equivalenceClassISuffixTidsets);
				}
			}
		}
	}

	/**
	 * Save an itemset to the file
	 * @param prefix the prefix of the itemset
	 * @param itemI the last item of the itemset
	 * @param tidsetItemset the tidset of the itemset
	 * @throws IOException if error while reading or writing to file
	 */
	private void save(List<Integer> prefix, int itemI, BitSet tidsetItemset) throws IOException {
		List<Integer> candidate = new ArrayList<Integer>(prefix);
		candidate.add(itemI);
		int au = 0;
		for (int j = 0; j < tidsetItemset.length(); j++) {
			if (tidsetItemset.get(j)) {
				for (int k = 0; k < candidate.size(); k++) {
					au = au + mapItemToItemInfo.get(candidate.get(k)).mapTidToUtility.get(j);
				}
			}
		}
		au = au / (candidate.size());
		if (au >= mau) {
			HAUICount++;
//			writeOut(candidate.toString(),au);
			StringBuffer buffer = new StringBuffer();
			for(int i = 0; i< candidate.size(); i++) {
				buffer.append(candidate.get(i));
				if(i != candidate.size()-1) {
					buffer.append(' ');
				}
				
			}
			buffer.append(" #AUTIL: ");
			buffer.append(au);
			writer.write(buffer.toString());
			writer.newLine();
		}
	}

	/** Save an item to the file
	 * 
	 * @param item the item
	 * @param tidset the tidset of the item
	 * @throws IOException if error while reading or writing to file
	 */
	private void saveItem(Integer item, BitSet tidset) throws IOException {
//		candidateCount++;
		int au = 0;
		for (int j = 0; j < tidset.length(); j++) {
			if (tidset.get(j)) {
				au = au + mapItemToItemInfo.get(item).mapTidToUtility.get(j);
			}
		}
		if (au >= mau) {
			HAUICount++;
//			writeOutItem(Integer.toString(item),au);
			StringBuffer buffer = new StringBuffer();
			buffer.append(Integer.toString(item));
			buffer.append(" #AUTIL: ");
			buffer.append(au);
			writer.write(buffer.toString());
			writer.newLine();

		}
	}

	/**
	 * Print statistics about the last execution of this algorithm
	 */
	public void printStats() {
		System.out.println("=============  HAUIM-GMU ALGORITHM  v2.50 =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Max memory: " + maxMemory + " MB");
		System.out.println(" HAUI count: " + HAUICount);
		System.out.println("===================================================");

	}
}