package ca.pfv.spmf.algorithms.frequentpatterns.etauim;

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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Implementation of the ETAUIM algorithm.
 * Obtained from Github liuxuan615 under the GPL v3 license
 * as it contains derived code from SPMF, which is under the GPL.
 */
public class AlgoETAUIM {
	double maxMemory = 0; // the maximum memory usage
	long startTimestamp = 0; // the time the algorithm started
	long endTimestamp = 0; // the time the algorithm terminated
//	int sfupCount =0;  // the number of SFUP generated
	/** the number of utility-list that was constructed */
	int joinCount = 0;
	Map<Integer, Integer> mapItemToAUUB;
	/** the top k rules found until now */
	PriorityQueue<Itemset> kItemsets;
	int BUFFERS_SIZE = 200;
	List<Integer> items = new ArrayList<>();
	Map<Integer, UtilityList> mapItemToUtilityList;
	BufferedWriter writer = null; // writer to write the output file
	int tid = 0;
	int k = 0;
	double minutil;
	int count = 0;// count the number of changes of the minautil value
	List<Integer> maxUtilities;

	// this class represent an item and its utility in a transaction
	class Pair {
		int item = 0;
		int utility = 0;
	}

	class ULs {
		List<UtilityList> newPrefixULs;
		List<UtilityList> newExULs;
	}

	public AlgoETAUIM() {

	}

	public void runAlgorithm(String input, String output, int k) throws IOException {
		// reset maximum
		maxMemory = 0;
		startTimestamp = System.currentTimeMillis();
		writer = new BufferedWriter(new FileWriter(output));
		// We create a map to store the TWU of each item
		mapItemToAUUB = new HashMap<Integer, Integer>();
		maxUtilities = new ArrayList<>();
		// Initialize the buffer for storing the current itemset
		// We scan the database a first time to calculate the TWU of each item.
		// update the umax by the support count and average-utiltiy of 1-itemsets
		kItemsets = new PriorityQueue<Itemset>();
		this.k = k;
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
				// the third part is the utilities of items
				String utilityValues[] = split[2].split(" ");
				// the second part is the transaction utility
				int transactionMUtility = Integer.MIN_VALUE;
				for (int i = 0; i < utilityValues.length; i++) {
					if (transactionMUtility < Integer.parseInt(utilityValues[i])) {
						transactionMUtility = Integer.parseInt(utilityValues[i]);
					}
				}
				// for each item, we add the transaction utility to its AUUB
				for (int i = 0; i < items.length; i++) {
					// convert item to integer
					Integer item = Integer.parseInt(items[i]);
					// get the current AUUB of that item
					Integer auub = mapItemToAUUB.get(item);
					// add the utility of the item in the current transaction to its AUUB
					auub = (auub == null) ? transactionMUtility : auub + transactionMUtility;
					mapItemToAUUB.put(item, auub);
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
		// System.out.println(minutil);
		List<UtilityList> listOfMAULists = new ArrayList<UtilityList>();
		// Key: item, Value:MAUList
		mapItemToUtilityList = new HashMap<Integer, UtilityList>();
		for (Integer item : mapItemToAUUB.keySet()) {
			int[] itemset = { item };
			UtilityList uList = new UtilityList(itemset);
			// add the item to the list of high TWU items
			mapItemToUtilityList.put(item, uList);
			listOfMAULists.add(uList);
		}
		Collections.sort(listOfMAULists, new Comparator<UtilityList>() {
			public int compare(UtilityList o1, UtilityList o2) {
				return compareItems(o1.itemset[0], o2.itemset[0]);
			}
		});
		// SECOND DATABASE PASS TO CONSTRUCT THE UTILITY LISTS OF ALL 1-ITEMSETS
		// variable to count the number of transaction
		try {
			// prepare object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// split the line according to the separator
				tid++;
				String split[] = thisLine.split(":");
				// get the list of items
				String items[] = split[0].split(" ");
				// get the list of utility values corresponding to each item
				// for that transaction
				String utilityValues[] = split[2].split(" ");
				// Create a list to store items
				List<Pair> revisedTransaction = new ArrayList<Pair>();
				// for each item
				for (int i = 0; i < items.length; i++) {
					/// convert values to integers
					Pair pair = new Pair();
					pair.item = Integer.parseInt(items[i]);
					pair.utility = Integer.parseInt(utilityValues[i]);
					revisedTransaction.add(pair);
				}
				// sort items according to the descending order of utilities of items
				Collections.sort(revisedTransaction, new Comparator<Pair>() {
					public int compare(Pair o1, Pair o2) {
						return compareItems(o1.item, o2.item);
					}
				});
				int rmau = 0, rn = 0;
				double rtaub;
				int max = Integer.MIN_VALUE;
				int smax = Integer.MIN_VALUE;
				// for each item left in the transaction
				for (int i = revisedTransaction.size() - 1; i >= 0; i--) {
					Pair pair = revisedTransaction.get(i);
					UtilityList utilityListOfItem = mapItemToUtilityList.get(pair.item);
					// Add a new Element to the utility list of this item corresponding to this
					// transaction
					Element element;
					if (i == revisedTransaction.size() - 1) {
						element = new Element(tid, pair.utility, rmau, rn, pair.utility);
						max = pair.utility;
					} else {
						int currUtility = revisedTransaction.get(i).utility;
						if (currUtility > max) {
							smax = max;
							max = currUtility;
						} else if (currUtility > smax) {
							smax = currUtility;
						}
						rtaub = (max + smax) / 2.0;
						element = new Element(tid, pair.utility, rmau, rn, rtaub);
					}
					rmau = rmau < pair.utility ? pair.utility : rmau;
					rn++;
					utilityListOfItem.addElement(element);
				}
			}

		} catch (Exception e) {
			// to catch error while reading the input file
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}
		// This method is used to mine all the PSFUPs
		Search(null, listOfMAULists);
		// This method is used to write out all the PSFUPs
		writeOut(output);
		// check the memory usage again and close the file.
		checkMemory();
		// close output file
		writer.close();
		// record end time
		endTimestamp = System.currentTimeMillis();
	}

	private int compareItems(int item1, int item2) {
		int compare = mapItemToAUUB.get(item1) - mapItemToAUUB.get(item2);
		// if the same, use the lexical order otherwise use the TWU
		return (compare == 0) ? item1 - item2 : compare;
	}

	/**
	 * This is the recursive method to find all potential skyline frequent-utility
	 * patterns
	 * 
	 * @param prefix      This is the current prefix. Initially, it is empty.
	 * @param pUL         This is the Utility List of the prefix. Initially, it is
	 *                    empty.
	 * @param ULs         The utility lists corresponding to each extension of the
	 *                    prefix.
	 * @param psfupList   Current potential skyline frequent-utility
	 *                    patterns.Initially, it is empty.
	 * @param skylineList Current skyline frequent-utility patterns.Initially, it is
	 *                    empty.
	 * @param umax        The array of max utility value of each
	 *                    frequency.Initially, it is zero.
	 * @throws IOException
	 */
	private void Search(List<UtilityList> prefixULs, List<UtilityList> exULs) throws IOException {
		scan(exULs);
		ULs uls = new ULs();
		uls = extend(prefixULs, exULs);
		while (uls.newExULs.size() != 0) {
			scan(uls.newExULs);
			uls = extend(uls.newPrefixULs, uls.newExULs);
		}
	}

	private void scan(List<UtilityList> ULs) throws IOException {
		// breadth-first search
		for (int i = 0; i < ULs.size(); i++) {
			UtilityList X = ULs.get(i);
			// judge whether X is a PSFUP
			// if the utility of X equals to the PSFUP which has same frequency with X,
			// insert X to psfupList
			double au = (double) X.sumIutils / X.itemset.length;
			if (au >= minutil) {
				Update(X);
			}
		}
	}

	private void Update(UtilityList X) throws IOException {
		Itemset itemset = new Itemset(X.itemset, X.sumIutils);
		kItemsets.add(itemset);
		if (kItemsets.size() > k) {
			Itemset lower;
			do {
				lower = kItemsets.peek();
				if (lower == null) {
					break;
				}
				kItemsets.remove(lower);
			} while (kItemsets.size() > k);
			Itemset currentItemset = kItemsets.peek();
			this.minutil = currentItemset.utility / (double) currentItemset.itemset.length;
			count++;
			if (kItemsets.size() >= 3 && count % 10 == 0) {
				System.out.println(this.minutil);
			}
		}
	}

	private ULs extend(List<UtilityList> prefixULs, List<UtilityList> exULs) {
		ULs uls = new ULs();
		uls.newExULs = new ArrayList<UtilityList>();
		uls.newPrefixULs = new ArrayList<UtilityList>();
		for (int i = 0; i < exULs.size(); i++) {
			UtilityList pX = exULs.get(i);
			int length_pX = pX.itemset.length;
			if (length_pX == 1) {
				if (mapItemToAUUB.get(pX.itemset[0]) < minutil) {
					continue;
				}
			} else if (pX.sumRmu < minutil) {
				continue;
			}
			double mau = 0;
			for (Element tran : pX.elements) {
				if (tran.remu > tran.iutils / (double) length_pX && tran.rn != 0) {
					mau = mau + (tran.iutils + tran.rn * tran.remu) / (double) (length_pX + tran.rn);
				} else if (tran.remu <= tran.iutils / (double) length_pX && tran.rn != 0) {
					mau = mau + (tran.iutils + tran.remu) / (length_pX + 1);
				}
			}
			pX.sumMau = mau;
			if (mau < minutil) {
				continue;
			}
			uls.newPrefixULs.add(pX);
			for (int j = i + 1; j < exULs.size(); j++) {
				UtilityList pY = exULs.get(j);
				if (pX.prefixIndex != pY.prefixIndex)
					break;
				if (prefixULs != null) {
					UtilityList pXY = construct(prefixULs.get(pY.prefixIndex), pX, pY);
					if (pXY != null) {
						joinCount++;
						pXY.prefixIndex = uls.newPrefixULs.size() - 1;
						uls.newExULs.add(pXY);
					}
				} else {
					UtilityList pXY = construct(null, pX, pY);
					if (pXY != null) {
						pXY.prefixIndex = uls.newPrefixULs.size() - 1;
						uls.newExULs.add(pXY);
						joinCount++;
					}
				}
			}
		}
		return uls;
	}

	/**
	 * This method constructs the utility list of pXY
	 * 
	 * @param P  : the utility list of prefix P.
	 * @param px : the utility list of pX
	 * @param py : the utility list of pY
	 * @return the utility list of pXY
	 */
	private UtilityList construct(UtilityList PUL, UtilityList px, UtilityList py) {
		// create an empy utility list for pXY
		int[] pxy = new int[px.itemset.length + 1];
		for (int i = 0; i <= pxy.length - 2; i++) {
			pxy[i] = px.itemset[i];
		}
		pxy[pxy.length - 1] = py.itemset[pxy.length - 2];
		UtilityList pxyUL = new UtilityList(pxy);
		double sumRmu = px.sumRmu;
		for (Element ex : px.elements) {
			// do a binary search to find element ey in py with tid = ex.tid
			Element ey = findElementWithTID(py, ex.tid);
			if (ey == null) {
				sumRmu -= ex.rmu;
				if (sumRmu < minutil) {
					return null;
				}
			} else {
				Element eXY;
				if (PUL != null) {
					Element p = findElementWithTID(PUL, ex.tid);
					eXY = new Element(ex.tid, ex.iutils + ey.iutils - p.iutils, ey.remu, ey.rn, ex.rmu);
				} else {
					eXY = new Element(ex.tid, ex.iutils + ey.iutils, ey.remu, ey.rn, ex.rmu);
				}
				// add the new element to the utility list of pXY
				pxyUL.addElement(eXY);

			}
		}
		// return the utility list of pXY.
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

	/**
	 * Write the result to a file
	 * 
	 * @param path the output file path
	 * @throws IOException if an exception for reading/writing to file
	 */
	public void writeOut(String path) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(path));
		Iterator<Itemset> iter = kItemsets.iterator();
		while (iter.hasNext()) {
			StringBuffer buffer = new StringBuffer();
			Itemset itemset = (Itemset) iter.next();
			// append the prefix
			for (int i = 0; i < itemset.getItemset().length; i++) {
				buffer.append(itemset.getItemset()[i]);
				buffer.append(' ');
			}
			// append the utility value
			buffer.append("#AUTIL: ");
			buffer.append(itemset.utility / (double) itemset.itemset.length);
			// write to file
			writer.write(buffer.toString());
			if (iter.hasNext()) {
				writer.newLine();
			}
		}
		writer.close();
	}

	/**
	 * Method to check the memory usage and keep the maximum memory usage.
	 */
	private void checkMemory() {
		// get the current memory usage
		double currentMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024d / 1024d;
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
		System.out.println("=============  ETAUIM v 2.60 - STATS =============");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) / (double) 1000 + " s");
		System.out.println(" Memory ~ " + maxMemory + " MB");
		System.out.println(" minutil = " + minutil);
		System.out.println(" k = " + k);
		System.out.println(" Join itemsets count : " + joinCount);
		System.out.println("===================================================");
	}
}