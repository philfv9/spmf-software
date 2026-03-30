package ca.pfv.spmf.algorithms.frequentpatterns.lppgrowth;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.tools.MemoryLogger;

/*
 * This file is part of the SPMF DATA MINING SOFTWARE *
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the *
 * terms of the GNU General Public License as published by the Free Software *
 * Foundation, either version 3 of the License, or (at your option) any later *
 * version. SPMF is distributed in the hope that it will be useful, but WITHOUT
 * ANY * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Copyright Peng Yang  2019
 */
/**
 * This is an implementation of the LPP-Growth algorithm based on FP-growth
 *
 * It is fast and memory efficient because it uses a compact tree data
 * structure.
 *
 * The PFTIGrowth is to find all periodic time-intervals of patterns.
 * 
 * @author Peng yang
 */
public class AlgoLPPGrowth {

	/** start time of the latest execution */
	private long startTimestamp;

	/** end time of the latest execution */
	private long endTime;

	/** largest timestamp in the database */
	private int lastTimestamp = -1;

	/** number of freq. itemsets found */
	private int itemsetCount;

	/** object to write the output file */
	BufferedWriter writer = null;

	/**
	 * The patterns that are found (if the user want to keep them into memory)
	 */
	protected Itemsets patterns = null;

	/**
	 * This variable is used to determine the size of buffers to store itemsets. A
	 * value of 50 is enough because it allows up to 2^50 patterns!
	 */
	final int BUFFERS_SIZE = 2000;

	/**
	 * buffer for storing the current itemset that is mined when performing mining
	 * the idea is to always reuse the same buffer to reduce memory usage.
	 */
	private int[] itemsetBuffer = null;

	/**
	 * This buffer is used to store an itemset that will be written to file // so
	 * that the algorithm can sort the itemset before it is output to file // (when
	 * the user choose to output result to file).
	 **/
	private int[] itemsetOutputBuffer = null;

	/** maximum pattern length */
	private int maxPatternLength = 1000;

	/**
	 * whether the timestamps need self increment as step of 1 for each transcation
	 * Default is true
	 */
	private boolean selfIncrement;

	/** the minimum duration threshold. */
	private int minDur;

	/** the maximum periodicity threshold. */
	private int maxPer;

	/** the maxSoPer **/
	private int maxSoPer;

	/**
	 * Constructor
	 */
	public AlgoLPPGrowth() {
		// empty
	}

	/**
	 * Method to run the LPPGrowth algorithm.
	 * 
	 * @param input  the path to an input file containing a transaction database.
	 * @param output the output file path for saving the result (if null, the result
	 *               will be returned by the method instead of being saved).
	 * @return the result if no output file path is provided.
	 * @throws IOException exception if error reading or writing files
	 */
	public Itemsets runAlgorithm(String input, String output, int maxPer, int minDur, int maxSoPer,
			boolean selfIncrement) throws IOException {
		// record start time
		startTimestamp = System.currentTimeMillis();

		// number of itemsets found
		itemsetCount = 0;

		// initialize tool to record memory usage
		MemoryLogger.getInstance().reset();
		MemoryLogger.getInstance().checkMemory();

		this.minDur = minDur;
		this.maxPer = maxPer;
		this.selfIncrement = selfIncrement;
		this.maxSoPer = maxSoPer;

		// if the user want to keep the result into memory
		if (output == null) {
			writer = null;
			patterns = new Itemsets("Local Periodic Pattern");
		} else { // if the user want to save the result to a file
			patterns = null;
			writer = new BufferedWriter(new FileWriter(output));
			itemsetOutputBuffer = new int[BUFFERS_SIZE];
		}

		// (1) PREPROCESSING: Initial database scan to determine the time-interval of
		// each item
		// The time-interval is stored in a map:
		// key: item value: timeintervals
		final Map<Integer, TimeIntervals> mapTimeIntervals = scanDatabaseToDetermineTimeIntervalsOfSingleItems(input);

		// ============= for test ==================================
//        int len = 0;
//        for(int item:mapTimeIntervals.keySet()){
//            System.out.println(item);
//            len++;
//        }
//        System.out.println("total:"+len);

		// (2) Scan the database again to build the initial PFTI-Tree
		// Before inserting a transaction in the LPPTree, we sort the items
		// by descending order of duration of time-intervals. We ignore items that do
		// not have time-interval.
		LPPTree tree = new LPPTree();

		buildTreeByScanDataAgain(tree, input, mapTimeIntervals);

		// (3) We start to mine the PFTI-Tree by calling the recursive method.
		// Initially, the prefix alpha is empty.
		// if at least an item has periodic frequent time-interval
		if (tree.headerList.size() > 0) {
			// initialize the buffer for storing the current itemset
			itemsetBuffer = new int[BUFFERS_SIZE];

			// recursively generate the itemsets that have periodic frequent time-interval
			// using the pfti-tree
			// Note: we assume that the initial PFTI-Tree has more than one path
			// which should generally be the case.
			pftiGrowth(tree, itemsetBuffer, 0, mapTimeIntervals);
		}

		// close the output file if the result was saved to a file
		if (writer != null) {
			writer.close();
		}
		// record the execution end time
		endTime = System.currentTimeMillis();

		// check the memory usage
		MemoryLogger.getInstance().checkMemory();

		// return the result (if saved to memory)
		return patterns;

	}

	/**
	 * The main method of this algorithm to mine an LPPTree
	 * 
	 * @param tree             an LPPTree
	 * @param prefix           the prefix of the current itemset
	 * @param prefixLength     the prefix length
	 * @param mapTimeIntervals a map of time intervals of items
	 * @throws IOException if error while reading or writing files
	 */
	@SuppressWarnings("serial")
	private void pftiGrowth(LPPTree tree, int[] prefix, int prefixLength, Map<Integer, TimeIntervals> mapTimeIntervals)
			throws IOException {
		if (prefixLength == maxPatternLength) {
			return;
		}

		// For each item in the header table list of the tree in reverse order.
		while (tree.headerList.size() > 0) {
			// get the tail item
			Integer item = tree.headerList.get(tree.headerList.size() - 1);

			// Create Beta by concatening prefix Alpha by adding the current item to alpha
			prefix[prefixLength] = item;

			// save beta to the output file
			saveItemset(prefix, prefixLength + 1, mapTimeIntervals.get(item).intervals);

			if (prefixLength + 1 < maxPatternLength) {

				// === (A) Construct beta's prefix tree ===
				// It is a subdatabase which consists of the set of prefix paths
				// in the PFTI-tree co-occuring with the prefix pattern.
				List<List<LPPNode>> prefixPaths = new ArrayList<List<LPPNode>>();

				LPPNode path = tree.mapItemNodes.get(item);

				// Map to count the timestamps of items in the conditional prefix tree
				// Key: item Value: timestamps
				Map<Integer, List<Integer>> mapTimestampsBeta = new HashMap<Integer, List<Integer>>();

				while (path != null) {
					// if the path is not just the root node
					if (path.parent.itemID != -1) {
						// create the prefixpath
						List<LPPNode> prefixPath = new ArrayList<LPPNode>();
						// add this node.
						prefixPath.add(path); // NOTE: we add it just to keep its timestamps,
						// actually it should not be part of the prefixPath

						List<Integer> pathTimestamps = path.timestamps;

						// Recursively add all the parents of this node.
						LPPNode parent = path.parent;

						while (parent.itemID != -1) {
							prefixPath.add(parent);

							// FOR EACH PATTERN WE ALSO UPDATE THE ITEM TIMESTAMPS AT THE SAME TIME
							// if the first time we see that node id
							if (mapTimestampsBeta.get(parent.itemID) == null) {
								// just add the path timestamps
								mapTimestampsBeta.put(parent.itemID, new ArrayList<Integer>() {
									{
										addAll(pathTimestamps);
									}
								});
							} else {
								// otherwise, add all of timestamps to map
								mapTimestampsBeta.get(parent.itemID).addAll(pathTimestamps);
							}
							parent = parent.parent;
						}
						// add the path to the list of prefixpaths
						prefixPaths.add(prefixPath);
					}
					// We will look for the next prefixpath
					path = path.nodeLink;
				}

				// refreshing PFTI-Tree by removing the tail item
				// the timestamps of tail item should be moved to its parent.
				tree.removeTailItem();

				// convert beta's timestamps to time-intervals
				Map<Integer, TimeIntervals> mapBetaTimeIntervals = getMapBetaTimeIntervals(mapTimestampsBeta);

				// header table have pattern that has time-interval
				if (mapBetaTimeIntervals.size() > 0) {
					// (B) Construct beta's conditional PFTI-Tree
					// Create the tree.
					LPPTree treeBeta = new LPPTree();
					// Add each prefixpath in the PFTI-tree.
					for (List<LPPNode> prefixPath : prefixPaths) {
						treeBeta.addPrefixPath(prefixPath, mapBetaTimeIntervals);
					}

					// Mine recursively the Beta tree if the root has child(s)
					if (treeBeta.root.childs.size() > 0) {

						// Create the header list.
						treeBeta.createHeaderList(tree.headerList, mapBetaTimeIntervals);
						// recursive call
						pftiGrowth(treeBeta, prefix, prefixLength + 1, mapBetaTimeIntervals);
					}
				}
			}
		}

		// check the memory usage
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Convert beta's timestamps to time-intervals
	 * 
	 * @param mapTimestampsBeta A map of items to timestamps
	 * @return A map of items to time intervals
	 */
	private Map<Integer, TimeIntervals> getMapBetaTimeIntervals(Map<Integer, List<Integer>> mapTimestampsBeta) {

		Map<Integer, TimeIntervals> mapBetaTimeIntervals = new HashMap<>();
		Map<Integer, Integer> soPer = new HashMap<>();

		for (Map.Entry<Integer, List<Integer>> entry : mapTimestampsBeta.entrySet()) {

			TimeIntervals timeIntervals = new TimeIntervals();
			List<Integer> timestamps = entry.getValue();
			// 1,sort the timestamps
			Collections.sort(timestamps);

			// 2.scan the timestamps
			int preTS = -1;
			for (int timestamp : timestamps) {
				if (preTS != -1) {
					int per = timestamp - preTS;

					// search for left endpoint
					if (per <= maxPer && timeIntervals.left == -1) {
						timeIntervals.left = preTS;
						soPer.put(entry.getKey(), maxSoPer);
					}

					// search for concept drift point
					if (timeIntervals.left != -1) {
						soPer.put(entry.getKey(), Math.max(0, soPer.get(entry.getKey()) + (per - maxPer)));
						if (soPer.get(entry.getKey()) > maxSoPer) {
							// pre_ts is change point
							if (preTS - timeIntervals.left >= minDur) {
								timeIntervals.addTimeInterval(preTS);
							}
							timeIntervals.left = -1;

						}
					}

				}
				preTS = timestamp;
			}

			// 3. Deal with the last timestamp

			if (timeIntervals.left != -1) {
				soPer.put(entry.getKey(), Math.max(0, soPer.get(entry.getKey()) + (lastTimestamp - preTS - maxPer)));
				if (soPer.get(entry.getKey()) <= maxSoPer && lastTimestamp - timeIntervals.left >= minDur) {
					timeIntervals.addTimeInterval(lastTimestamp);
				}
				if (soPer.get(entry.getKey()) > maxSoPer && preTS - timeIntervals.left >= minDur) {
					timeIntervals.addTimeInterval(preTS);
				}
			}

			// 4. save time-interval
			if (timeIntervals.intervals.size() > 0) {
				mapBetaTimeIntervals.put(entry.getKey(), timeIntervals);
			}
		}
		// clear the memory of mapTimestampsBeta
		mapTimestampsBeta.clear();

		return mapBetaTimeIntervals;
	}

	/**
	 * Save itemset to the results
	 * 
	 * @param itemset       the name of itemset
	 * @param itemsetLength the size of itemset
	 * @param timeIntervals the periodic time-interval of itemset
	 * @throws IOException if error while writing to the output file
	 */
	private void saveItemset(int[] itemset, int itemsetLength, List<int[]> timeIntervals) throws IOException {

		// increase the number of itemsets found for statistics purpose
		itemsetCount++;

		// if the result should be saved to a file
		if (writer != null) {
			// copy the itemset in the output buffer and sort items
			System.arraycopy(itemset, 0, itemsetOutputBuffer, 0, itemsetLength);
			Arrays.sort(itemsetOutputBuffer, 0, itemsetLength);

			// Create a string buffer
			StringBuilder buffer = new StringBuilder();
			// write the items of the itemset
			for (int i = 0; i < itemsetLength; i++) {
				buffer.append(itemsetOutputBuffer[i]);
				if (i != itemsetLength - 1) {
					buffer.append(' ');
				}
			}
			// Then, write the time-intervals
			buffer.append(" #TIME-INTERVALS: ");
			for (int[] timeInterval : timeIntervals) {
				buffer.append("[");
				buffer.append(timeInterval[0]);
				buffer.append(",");
				buffer.append(timeInterval[1]);
				buffer.append("]   ");
			}
			// write to file and create a new line
			writer.write(buffer.toString());
			writer.newLine();

		} // otherwise the result is kept into memory
		else {
			// create an object Itemset and add it to the set of patterns
			// found.
			int[] itemsetArray = new int[itemsetLength];
			System.arraycopy(itemset, 0, itemsetArray, 0, itemsetLength);

			// sort the itemset so that it is sorted according to lexical ordering before we
			// show it to the user
			Arrays.sort(itemsetArray);

			Itemset itemsetObj = new Itemset(itemsetArray, timeIntervals);
			patterns.addItemset(itemsetObj, itemsetLength);
		}
	}

	/**
	 * Build a tree by scanning the database again
	 * 
	 * @param tree             a new tree
	 * @param input            the input path
	 * @param mapTimeIntervals the items' periodic time-intervals
	 * @throws IOException if error while reading or writing to file
	 */
	private void buildTreeByScanDataAgain(LPPTree tree, String input, Map<Integer, TimeIntervals> mapTimeIntervals)
			throws IOException {
		// read file
		BufferedReader reader = new BufferedReader(new FileReader(input));
		String line;

		if (selfIncrement) { // the timestamp is self-increment

			int ts = 1;

			while (((line = reader.readLine()) != null)) {
				// if the line is a comment, is empty or is a
				// kind of metadata
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}

				String[] lineSplited = line.trim().split(" ");

				List<Integer> transaction = new ArrayList<Integer>();

				for (String itemString : lineSplited) {
					Integer itemName = Integer.parseInt(itemString);

					// only the item has periodic frequent time-interval
					// and the current timestamp in its time-interval
					// then this item can be added to the transaction (tree) .
					if (mapTimeIntervals.containsKey(itemName) && mapTimeIntervals.get(itemName).isInside(ts)
							&& !transaction.contains(itemName)) {
						transaction.add(itemName);
					}
				}
				// sort item in the transaction by descending order of total duration
				Collections.sort(transaction, new Comparator<Integer>() {
					public int compare(Integer item1, Integer item2) {
						// compare the total duration
						int compare = mapTimeIntervals.get(item2).getTotalDuration()
								- mapTimeIntervals.get(item1).getTotalDuration();
						// if the same total duration, we check the lexical ordering!
						if (compare == 0) {
							return (item1 - item2);
						}
						// otherwise, just use the total duration
						return compare;
//                        return item1- item2;
					}
				});

				// add the sorted transaction and current timestamp into tree.
				tree.addTransaction(transaction, ts);

				// self increment
				ts++;
			}

		} else { //// the timestamp exist in file

			int ts = -1;

			while (((line = reader.readLine()) != null)) {
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}

				String[] lineSplited = line.trim().split("\\|");
				String[] lineItems = lineSplited[0].trim().split(" ");

				ts = Integer.parseInt(lineSplited[1]);

				List<Integer> transaction = new ArrayList<Integer>();

				for (String itemString : lineItems) {
					Integer itemName = Integer.parseInt(itemString);

					// only the item has periodic frequent time-interval
					// and the current timestamp in its time-interval
					// then this item can be added to the transaction (tree) .
					if (mapTimeIntervals.containsKey(itemName) && mapTimeIntervals.get(itemName).isInside(ts)
							&& !transaction.contains(itemName)) {
						transaction.add(itemName);
					}

				}
				// sort item in the transaction by descending order of total duration
				Collections.sort(transaction, new Comparator<Integer>() {
					public int compare(Integer item1, Integer item2) {

						// compare the total duration
						int compare = mapTimeIntervals.get(item2).getTotalDuration()
								- mapTimeIntervals.get(item1).getTotalDuration();
						// if the same total duration, we check the lexical ordering!
						if (compare == 0) {
							return (item1 - item2);
						}
						// otherwise, just use the total duration
						return compare;
//                        return item1 -item2;
					}
				});
				// add the sorted transaction and current timestamp into tree.
				if (transaction.size() > 0) {
					tree.addTransaction(transaction, ts);
				}
			}
		}

		// close the input file
		reader.close();

		// We create the header table for the tree using the calculated support of
		// single items
		tree.createHeaderList(null, mapTimeIntervals);

	}

	/**
	 * Scan database to determine the periodic time-intervals of single items.
	 * 
	 * @param input the path of database
	 * @return a map of items to time intervals
	 * @throws IOException if error while readin the input file
	 */
	private Map<Integer, TimeIntervals> scanDatabaseToDetermineTimeIntervalsOfSingleItems(String input)
			throws IOException {
		// read file
		BufferedReader reader = new BufferedReader(new FileReader(input));
		String line;

		// The time-interval is stored in a map:
		// key: item value: timeintervals
		Map<Integer, TimeIntervals> mapTimeIntervals = new HashMap<>();

		// this save the previous timestamp of item
		// key: item , value: previous timestamp
		Map<Integer, Integer> preTimestamp = new HashMap<>();

		// this save the soPer of an interval of a item
		// key: item , value: soPer
		Map<Integer, Integer> soPer = new HashMap<>();

		if (selfIncrement) { // the timestamp is self-increment
			int ts = 1;
			while (((line = reader.readLine()) != null)) {
				// if the line is a comment, is empty or is a
				// kind of metadata
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}
				String[] lineSplited = line.split(" ");

				for (String itemString : lineSplited) {
					Integer itemName = Integer.parseInt(itemString);

					// if the item have appeared before ( the item has previous timestamp)
					if (mapTimeIntervals.containsKey(itemName)) {
						// get the previous timestamp of the item
						int pre_ts = preTimestamp.get(itemName);

						// calculate the periodicity
						int per = ts - pre_ts;

						// if a transaction has same item
						if (per == 0)
							continue;

						TimeIntervals timeIntervals = mapTimeIntervals.get(itemName);

						// search for left point of a interval
						if (per <= maxPer && timeIntervals.left == -1) {
							timeIntervals.left = pre_ts;
							soPer.put(itemName, maxSoPer);
						}

						// search for concept drift endponit
						if (timeIntervals.left != -1) {
							soPer.put(itemName, Math.max(0, soPer.get(itemName) + (per - maxPer)));
							if (soPer.get(itemName) > maxSoPer) {
								// pre_ts is change point
								if (pre_ts - timeIntervals.left >= minDur) {
									timeIntervals.addTimeInterval(pre_ts);
								}
								timeIntervals.left = -1;

							}
						}

					} else { // if the item firstly appear
						mapTimeIntervals.put(itemName, new TimeIntervals());

					}
					// reflesh the previous timestamp
					preTimestamp.put(itemName, ts);
				}
				// the timestamp self increment
				ts++;
			}
			lastTimestamp = ts - 1;

		} else { //// the timestamp exist in file
			int ts = -1;
			while (((line = reader.readLine()) != null)) {
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}

				String[] lineSplited = line.split("\\|");
				String[] lineItems = lineSplited[0].split(" ");
				ts = Integer.parseInt(lineSplited[1]);
				for (String itemString : lineItems) {
					Integer itemName = Integer.parseInt(itemString);

					// if the item have appeared before ( the item has previous timestamp)
					if (preTimestamp.containsKey(itemName)) {
						// get the previous timestamp of the item
						int preTS = preTimestamp.get(itemName);

						// calculate the periodicity
						int per = ts - preTS;

						TimeIntervals timeIntervals = mapTimeIntervals.get(itemName);

						// search for left point of a interval
						if (per <= maxPer && timeIntervals.left == -1) {
							timeIntervals.left = preTS;
							soPer.put(itemName, maxSoPer);
						}

						// search for concept drift endponit
						if (timeIntervals.left != -1) {
							soPer.put(itemName, Math.max(0, soPer.get(itemName) + (per - maxPer)));
							if (soPer.get(itemName) > maxSoPer) {
								// pre_ts is change point
								if (preTS - timeIntervals.left >= minDur) {
									timeIntervals.addTimeInterval(preTS);
								}
								timeIntervals.left = -1;

							}
						}

					} else {
						// if the item firstly appear
						mapTimeIntervals.put(itemName, new TimeIntervals());

					}
					// reflesh the previous timestamp
					preTimestamp.put(itemName, ts);
				}
			}
			lastTimestamp = ts;
		}
		// close the input file
		reader.close();

		// Deal with the last timestamp

		Iterator<Map.Entry<Integer, TimeIntervals>> it = mapTimeIntervals.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, TimeIntervals> entry = it.next();

			// if current time-interval of the item has left point but has not right point
			if (entry.getValue().left != -1) {
				soPer.put(entry.getKey(), Math.max(0,
						soPer.get(entry.getKey()) + (lastTimestamp - preTimestamp.get(entry.getKey()) - maxPer)));

				if (soPer.get(entry.getKey()) <= maxSoPer && lastTimestamp - entry.getValue().left >= minDur) {
					entry.getValue().addTimeInterval(lastTimestamp);
				}

				if (soPer.get(entry.getKey()) > maxSoPer
						&& preTimestamp.get(entry.getKey()) - entry.getValue().left >= minDur) {
					entry.getValue().addTimeInterval(preTimestamp.get(entry.getKey()));
				}
				entry.getValue().left = -1;
			}

			// the item has not periodic frequent time-interval
			if (entry.getValue().intervals.size() <= 0) {
				// remove it.
				it.remove();
			}
		}

		return mapTimeIntervals;
	}

	/**
	 * Print statistics about the algorithm execution to System.out.
	 */
	public void printStats() {
		System.out.println("=============  LPP-Growth  - STATS ===============");
		long temps = endTime - startTimestamp;
		System.out.print(" Max memory usage: " + MemoryLogger.getInstance().getMaxMemory() + " mb \n");
		System.out.println(" Itemset counts : " + this.itemsetCount);
		System.out.println(" Total time ~ " + temps + " ms");
		System.out.println("===================================================");
	}

	/**
	 * Set the maximum pattern length
	 * 
	 * @param length the maximum length
	 */
	public void setMaximumPatternLength(int length) {
		maxPatternLength = length;
	}

	/**
	 * Desactivate self-increment for transaction timestamps.
	 */
	public void cancelSelfIncrement() {
		this.selfIncrement = false;
	}

}