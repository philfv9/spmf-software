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
import java.util.Set;

//import ca.pfv.spmf.algorithms.frequentpatterns.hui_miner.AlgoHUIMiner.Pair;
import ca.pfv.spmf.tools.MemoryLogger;

/* This file is copyright (c) jnfrancis ( https://github.com/jnfrancis/HUIM-SU ) 
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
/***
 * This is an implementation of the HUIM-SU algorithm, proposed in this
 * paper:<br/>
 * <br/>
 * 
 * An efficient utility-list based high-utility itemset mining algorithm <br/>
 * <br/>
 * 
 * The code was obtained from Github based on the GPL license, since it included
 * code from SPMF that is under the GPL. The code has been slightly adapted 
 * to integrate it with SPMF.
 * 
 * @author jnfrancis ( https://github.com/jnfrancis/HUIM-SU )
 *
 */
public class AlgoHUIMSU {

	/** the time at which the algorithm started */
	private long startTimestamp = 0;

	/** the time at which the algorithm ended */
	private long endTimestamp = 0;

	/** the number of high-utility itemsets generated */
	private int huiCount = 0;

	/** Map to remember the TWU of each item */
	private Map<Integer, Integer> mapItemToTWU;
	private Map<Integer, ArrayList<Integer>> mapALL;
	// Map<Integer, LinkedList<Integer>> mapALL1;

	/** writer to write the output file */
	private  BufferedWriter writer = null;

	/** new items */
	private ArrayList<Integer> newitems = new ArrayList<Integer>();

	/** item data */
	private List<ArrayList<Integer>> itemsdata = new ArrayList<ArrayList<Integer>>();
	
	/** fron items data */
	private ArrayList<ArrayList<Integer>> frontitemsdata = new ArrayList<ArrayList<Integer>>();
//	private ArrayList<ArrayList<Integer>> frontitemsdata1 = new ArrayList<ArrayList<Integer>>();
	
	/** items */
	private ArrayList<Integer> items = new ArrayList<Integer>();

	/** htwu */
	private List<Integer> htwu;
	
	/** items to TWU */
	private int[] ItemTWU;
	
	/** temporary array */
	private int[] temp;

	/** number of transactions */
	private int transactionsnum;

	/** the number of utility-list that was constructed */
	private int joinCount = 0;

	/** minimum utility threshold */
	private int min_utility;

	/**
	 * Run the algorithm
	 * 
	 * @param input  input file path
	 * @param output output file path
	 * @throws IOException if error occurs while reading or writing to file
	 */
	public void runAlgorithm(String input, String output, int min_utility) throws IOException {
		// reset maximum
		MemoryLogger.getInstance().reset();
		
		this.min_utility = min_utility;

		// initialize the buffer for storing the current itemset
		// itemsetBuffer = new int[BUFFERS_SIZE];

		startTimestamp = System.currentTimeMillis();

		writer = new BufferedWriter(new FileWriter(output));

		// We create a map to store the TWU of each item
		mapItemToTWU = new HashMap<Integer, Integer>();

		// mapALL = new HashMap<Integer, HashMap<Integer,Integer>>();

		mapALL = new HashMap<Integer, ArrayList<Integer>>();

		Map<Integer, Set<Integer>> frontitems = new HashMap<Integer, Set<Integer>>();

		// We scan the database a first time to calculate the TWU of each item.
		BufferedReader myInput = null;
		Integer j = 0;
		String thisLine;
		int maxitem = 0;
		ArrayList<ArrayList<Integer>> ff = new ArrayList<ArrayList<Integer>>();
		try {
			// prepare the object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			// for each line (transaction) until the end of file

			htwu = new ArrayList<>();

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
				String itemsvalue[] = split[2].split(" ");
				// the second part is the transaction utility
				int transactionUtility = Integer.parseInt(split[1]);
				// for each item, we add the transaction utility to its TWU
				htwu.add(transactionUtility);

				ArrayList<Integer> ff1 = new ArrayList<Integer>();
				for (int i = 0; i < items.length; i++) {
					// convert item to integer
					Integer item = Integer.parseInt(items[i]);
					ff1.add(item);
					if (item > maxitem) {
						maxitem = item;
					}
					Integer values = Integer.parseInt(itemsvalue[i]);
					// get the current TWU of that item
					Integer twu = mapItemToTWU.get(item);

					Set<Integer> set = frontitems.get(item);
					if (set == null) {
						HashSet<Integer> set1 = new HashSet<Integer>();
						for (int k = 0; k < items.length; k++) {
							set1.add(Integer.parseInt(items[k]));
						}
						frontitems.put(item, set1);
					} else {
						for (int k = 0; k < items.length; k++) {
							set.add(Integer.parseInt(items[k]));
						}
						frontitems.put(item, set);
					}

					ArrayList<Integer> twu1 = mapALL.get(item);
					if (twu1 == null) {
						ArrayList<Integer> tu = new ArrayList<>();
						tu.add(j);
						tu.add(values);
						mapALL.put(item, tu);
					} else {
						twu1.add(j);
						twu1.add(values);
						mapALL.put(item, twu1);
					}
					// add the utility of the item in the current transaction to its twu
					twu = (twu == null) ? transactionUtility : twu + transactionUtility;
					mapItemToTWU.put(item, twu);
				}
				ff.add(ff1);
				j++;
			}
			// System.out.println(j);

		} catch (Exception e) {
			// catches exception if error while reading the input file
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}

		transactionsnum = j;
		int Terminator = 0;
		ItemTWU = new int[maxitem + 1];
		ArrayList<Integer> list = new ArrayList<>();
//		ArrayList<Integer> list1=new ArrayList<>(); 
		for (Map.Entry<Integer, Integer> entry : mapItemToTWU.entrySet()) {
			ItemTWU[entry.getKey()] = entry.getValue();
//			if(entry.getValue()>=min_utility) {
//				list1.add(entry.getKey());
//			}
			list.add(entry.getKey());
		}
		// System.out.println(list1.size());
		ArrayList<Integer> list2 = new ArrayList<>();
		while (Terminator == 0) {
			Terminator = 1;
			for (int i = 0; i < list.size(); i++) {

				if (ItemTWU[list.get(i)] < min_utility) {
					list2.add(i);
					ArrayList<Integer> data = mapALL.get(list.get(i));
					for (int k = 0; k < data.size(); k += 2) {
						ArrayList<Integer> ff2 = ff.get(data.get(k));
						for (int kk = 0; kk < ff2.size(); kk++) {

							ItemTWU[ff2.get(kk)] -= data.get(k + 1);
						}
					}
					Terminator = 0;
					list.remove(i);
					i--;

				}

			}
			// System.out.println(list.size());
		}

		ValueComparator vc = new ValueComparator();

		Collections.sort(list, vc);
//		System.out.println(list.size());

		// System.out.println(list);

		temp = new int[list.size()];
		for (int k = 0; k < list.size(); k++) {
			frontitemsdata.add(getfrontitem(frontitems.get(list.get(k)), list, k));
			items.add(list.get(k));
			newitems.add(k);
			itemsdata.add(mapALL.get(list.get(k)));
		}
		ArrayList<Integer> jianzhiitems = firstjianzhi(newitems);
//		System.out.println(jianzhiitems.size());

		speedminer(jianzhiitems);
		
		// check the memory usage again and close the file.
		MemoryLogger.getInstance().checkMemory();
		// close output file

		writer.close();
		// record end time
		endTimestamp = System.currentTimeMillis();
	}

	private ArrayList<Integer> firstjianzhi(ArrayList<Integer> newitems2) {
		int[] transactions = new int[transactionsnum];
		int total = 0;
		ArrayList<Integer> jianzhi = new ArrayList<Integer>();
		jianzhi.addAll(newitems2);
		for (int i = jianzhi.size() - 1; i >= 0; i--) {
			total = 0;
			ArrayList<Integer> data = itemsdata.get(jianzhi.get(i));
			for (int j = 0; j < data.size(); j += 2) {
				transactions[data.get(j)] += data.get(j + 1);
				total += transactions[data.get(j)];
			}

			if (total < min_utility) {
				jianzhi.remove(i);
			}
		}
		return jianzhi;
	}

	private ArrayList<Integer> getfrontitem(Set<Integer> set, ArrayList<Integer> list, int k) {

		ArrayList<Integer> beforeitem = new ArrayList<Integer>();
		for (int i = k + 1; i < list.size(); i++) {
			if (set.contains(list.get(i))) {
				beforeitem.add(i);
			}
		}
		return beforeitem;
	}

	private class ValueComparator implements Comparator<Integer> {
		public int compare(Integer mp1, Integer mp2) {
			return ItemTWU[mp1] - ItemTWU[mp2];
		}
	}

	private void speedminer(ArrayList<Integer> jianzhiitems) throws IOException {
		// LinkedList<Integer> HUI = new LinkedList<Integer>();
		joinCount += jianzhiitems.size();
		ArrayList<Integer> jianzhi;
		// System.out.println(jianzhiitems);
		int prefixLength = 0;
		for (int i = 0; i < jianzhiitems.size(); i++) {
			int item = jianzhiitems.get(i);
			ArrayList<Integer> twu = itemsdata.get(item);
			int total = 0;
			for (int j = 1; j < twu.size(); j = j + 2) {
				total = total + twu.get(j);
			}
			temp[0] = item;
			ArrayList<Integer> temp = frontitemsdata.get(item);
			// System.out.println(temp);
			if (total >= min_utility) {
				writeOut(prefixLength, total);
			}
			jianzhi = secondjianzhi(temp, twu);
			// System.out.println(jianzhi);
			construct(jianzhi, temp, twu, prefixLength);
		}

	}

	private void construct(ArrayList<Integer> jianzhi, ArrayList<Integer> temp2, ArrayList<Integer> twu,
			int prefixLength) throws IOException {
		prefixLength++;
		// System.out.println(jianzhi);
		joinCount += jianzhi.size();
		ArrayList<Integer> afterdata;
		ArrayList<Integer> afteritem;
		ArrayList<Integer> jianzhicurrentitem;
		ArrayList<Integer> currentdata = new ArrayList<Integer>();
		ArrayList<Integer> currentitem = new ArrayList<Integer>();
		int k = 0, j = 0;
		int total;
		int p1, p2;
		for (int i = 0; i < jianzhi.size(); i++) {
			int item = jianzhi.get(i);
			temp[prefixLength] = item;
			afterdata = itemsdata.get(item);

			k = 0;
			j = 0;
			total = 0;
			while (j < twu.size() && k < afterdata.size()) {
				p1 = twu.get(j);
				p2 = afterdata.get(k);
				if (p1 == p2) {
					total += twu.get(j + 1) + afterdata.get(k + 1);
					currentdata.add(twu.get(j));
					currentdata.add(twu.get(j + 1) + afterdata.get(k + 1));
					j += 2;
					k += 2;
				} else {
					if (p1 > p2) {
						k += 2;
					} else {
						j += 2;
					}
				}
			}
			if (total >= min_utility) {
				writeOut(prefixLength, total);
			}
			afteritem = frontitemsdata.get(item);
			k = i + 1;
			j = 0;

			while (k < temp2.size() && j < afteritem.size()) {
				p1 = temp2.get(k);
				p2 = afteritem.get(j);
				if (p1 == p2) {
					currentitem.add(temp2.get(k));
					k++;
					j++;
				} else {
					if (p1 > p2) {
						j++;
					} else {
						k++;
					}
				}
			}
			jianzhicurrentitem = secondjianzhi(currentitem, currentdata);
			construct(jianzhicurrentitem, currentitem, currentdata, prefixLength);
			currentdata.clear();
			currentitem.clear();
		}
	}

	private ArrayList<Integer> secondjianzhi(ArrayList<Integer> temp2, ArrayList<Integer> twu) {
		ArrayList<Integer> jian = new ArrayList<Integer>();
		ArrayList<Integer> jian2;
		jian.addAll(temp2);
		int altotal;
		int j, k;
		int p1, p2;
		int[] transactions = new int[transactionsnum];
		j = 0;
		for (j = 0; j < twu.size(); j += 2) {
			transactions[twu.get(j)] = twu.get(j + 1);
		}
		for (int i = jian.size() - 1; i >= 0; i--) {
			jian2 = itemsdata.get(jian.get(i));
			j = 0;
			k = 0;
			altotal = 0;
			while (j < twu.size() && k < jian2.size()) {
				p1 = twu.get(j);
				p2 = jian2.get(k);
				if (p1 == p2) {
					transactions[jian2.get(k)] += jian2.get(k + 1);
					altotal += transactions[jian2.get(k)];
					j += 2;
					k += 2;
				} else {
					if (p1 > p2) {
						k += 2;
					} else {
						j += 2;
					}
				}

			}

			if (altotal < min_utility) {
				jian.remove(i);
			}
		}
		return jian;
	}

	/**
	 * Write a high utility itemset to file
	 * 
	 * @param prefixLength the prefix length
	 * @param total        the utility
	 * @throws IOException if error while writting to file
	 */
	private void writeOut(int prefixLength, int total) throws IOException {
		huiCount++; // increase the number of high utility itemsets found
		// Create a string buffer
		StringBuilder buffer = new StringBuilder();
		// append the prefix
		for (int i = 0; i < prefixLength + 1; i++) {
			buffer.append(items.get(temp[i]));
			buffer.append(' ');
		}
		buffer.append("#UTIL: ");
		buffer.append(total);
		// write to file
		writer.write(buffer.toString());
		writer.newLine();
	}

	/**
	 * Print statistics about the last execution of the algorithm to the console.
	 */
	public void printStats() {
		System.out.println("=============  HUIM-SU ALGORITHM - STATS =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println(" High-utility itemsets count : " + huiCount);
		System.out.println(" Join count : " + joinCount);
		System.out.println("===================================================");
	}
}
