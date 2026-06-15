package ca.pfv.spmf.algorithms.frequentpatterns.defme_fast;
/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger
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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import ca.pfv.spmf.algorithms.ItemNameConverter;
import ca.pfv.spmf.patterns.itemset_array_integers_with_tids_bitset.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_tids_bitset.Itemsets;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is a recent implementation of the DefMe algorithm that uses bitsets to
 * represent tidsets, and is implemented to mine itemsets.
 *
 * Defme was proposed by Soulet et al (2014). <br/>
 * <br/>
 *
 * See this article for details about DefMe: <br/>
 * <br/>
 *
 * Soulet, A., Rioult, F. (2014). Efficiently Depth-First Minimal Pattern
 * Mining, PAKDD 2014. <br/>
 * <br/>
 *
 * This version saves the result to a file or keeps it into memory if no output
 * path is provided by the user to the runAlgorithm method().
 *
 * @see Itemset
 * @see Itemsets
 * @author Philippe Fournier-Viger
 */
public class AlgoDefMe_FAST {

	/** relative minimum support **/
	private int minsupRelative;

	/** number of transactions in the database **/
	private int tidCount;

	/** start time of the last execution */
	private long startTimestamp;

	/** end time of the last execution */
	private long endTime;

	/** Patterns found (if the user want to keep them into memory) */
	protected Itemsets generators;

	/** object to write the output file */
	private BufferedWriter writer = null;

	/** the number of patterns found */
	private int itemsetCount;

	/**
	 * Tidset (primitive arrays) for each item, indexed by renamed item id
	 * (1..frequentItemsCount).
	 */
	private long[][] itemTIDSarray;

	/**
	 * buffer for storing the current itemset that is mined when performing mining
	 */
	private int[] itemsetBuffer = null;

	/** Special parameter to set the maximum size of itemsets to be discovered */
	private int maxItemsetSize = Integer.MAX_VALUE;

	/**
	 * The strict limit of maximum depth achievable by the DFS algorithm to avoid
	 * array bounds issues
	 */
	private int searchMaxDepth;

	/**
	 * Lazily allocated tidset buffers, one per recursion depth. tidsetBuffers[d] is
	 * null until depth d is first visited.
	 */
	private long[][] tidsetBuffers;

	/**
	 * Dynamic 3D Matrix for critical objects:
	 * critMatrix[depth][itemIndex][wordIndex]. Lazily allocated to prevent massive
	 * memory spikes on datasets with long transactions.
	 */
	private long[][][] critMatrix;

	/** number of frequent items */
	private int frequentItemsCount;

	/** reusable buffer for building output lines */
	private StringBuilder outputBuffer;

	/** converter for mapping original item ids to renamed ids and back */
	private ItemNameConverter nameConverter;

	/** The largest item name in the input database */
	private int maxOldItemId = -1;

	/** The longest transaction */
	private int longestTransactionSize = 0;

	/** The number of 64-bit words used to represent tidsets */
	private int wordsInUse;

	/**
	 * Default constructor
	 */
	public AlgoDefMe_FAST() {

	}

	/**
	 * Run the algorithm.
	 * 
	 * @param input  an input file path containing the transaction database
	 * @param output an output file path for writing the result or if null the
	 *               result is saved into memory and returned
	 * @param minsup the minimum support as a fraction between 0 and 1
	 * @return the set of generators if the user chose to save the result to memory.
	 *         Otherwise, null.
	 * @throws IOException exception if error while writing the file.
	 */
	public Itemsets runAlgorithm(String input, String output, double minsup) throws IOException {

		// Reset the tool to assess the maximum memory usage (for statistics)
		MemoryLogger.getInstance().reset();

		// if the user want to keep the result into memory
		if (output == null) {
			writer = null;
			generators = new Itemsets("FREQUENT ITEMSETS");
		} else {
			// if the user want to save the result to a file
			generators = null;
			writer = new BufferedWriter(new FileWriter(output));
		}

		// reset the number of itemset found to 0
		itemsetCount = 0;

		// initialize the reusable output string builder
		outputBuffer = new StringBuilder();

		// record the start time
		startTimestamp = System.currentTimeMillis();

		// -----------------------------------------------------------------------
		// PHASE 1: read transactions and count item support at the same time.
		// This avoids a second pass over the transactions just for counting.
		// -----------------------------------------------------------------------
		List<int[]> transactions = new ArrayList<>(2048);
		int[] supportArray = readTransactionsAndCount(input, transactions);

		tidCount = transactions.size();
		this.minsupRelative = (int) Math.ceil(minsup * tidCount);

		// calculate how many 64-bit words are needed to store tidCount bits
		this.wordsInUse = (tidCount + 63) >> 6;

		// -----------------------------------------------------------------------
		// PHASE 2: collect frequent items, sort by increasing support,
		// then assign consecutive new names 1..n in that sorted order.
		// -----------------------------------------------------------------------
		int[] tempFrequent = new int[maxOldItemId + 1];
		int tempCount = 0;
		for (int item = 1; item <= maxOldItemId; item++) {
			if (supportArray[item] >= minsupRelative) {
				tempFrequent[tempCount++] = item;
			}
		}

		// sort by increasing support — zero boxing
		sortItemsBySupport(tempFrequent, supportArray, 0, tempCount - 1);

		frequentItemsCount = tempCount;

		// assign new names 1..n in support order
		nameConverter = new ItemNameConverter(frequentItemsCount, 1);
		for (int k = 0; k < frequentItemsCount; k++) {
			nameConverter.assignNewName(tempFrequent[k]);
		}

		// -----------------------------------------------------------------------
		// PHASE 3: build tidsets directly from original transactions,
		// renaming items and skipping infrequent ones in a single pass.
		// -----------------------------------------------------------------------
		itemTIDSarray = new long[frequentItemsCount + 1][];
		for (int k = 1; k <= frequentItemsCount; k++) {
			itemTIDSarray[k] = new long[wordsInUse];
		}

		// for each transaction
		for (int tid = 0; tid < tidCount; tid++) {
			int[] transaction = transactions.get(tid);
			// for each item in the transaction
			for (int i = 0; i < transaction.length; i++) {
				int originalItem = transaction[i];
				int newName = nameConverter.toNewName(originalItem);

				// if the item is frequent
				if (newName != 0) {
					// set the bit corresponding to the transaction ID
					itemTIDSarray[newName][tid >> 6] |= (1L << tid);
				}
			}
		}

		// For garbage collection: 
		transactions.clear();
		transactions = null;
		supportArray = null;

		// -----------------------------------------------------------------------
		// PHASE 4: Allocate reference arrays for the search (no bitsets yet).
		// -----------------------------------------------------------------------

		// Calculate max search depth
		searchMaxDepth = Math.min(Math.min(longestTransactionSize, frequentItemsCount), maxItemsetSize);

		itemsetBuffer = new int[searchMaxDepth + 1];

		// Allocate reference arrays; slots start null
		tidsetBuffers = new long[searchMaxDepth + 1][];
		critMatrix = new long[searchMaxDepth + 1][][];

		// -----------------------------------------------------------------------
		// PHASE 5: run the recursive DefMe search.
		// -----------------------------------------------------------------------

		// create a virtual "empty set" tidset representing all transactions
		long[] tidsetEmptySet = new long[wordsInUse];
		if (wordsInUse > 0) {
			for (int i = 0; i < wordsInUse - 1; i++) {
				tidsetEmptySet[i] = ~0L; // fill with 1s
			}
			// handle the last word to ensure no out-of-bounds bits are set
			int remainder = tidCount & 63;
			tidsetEmptySet[wordsInUse - 1] = (remainder == 0) ? ~0L : (1L << remainder) - 1L;
		}

		// Initial call of the defme procedure
		defme(itemsetBuffer, 0, tidsetEmptySet, tidCount, 0);

		// we check the memory usage
		MemoryLogger.getInstance().checkMemory();

		// close the output file if the result was saved to a file
		if (writer != null) {
			writer.close();
		}

		// record the end time for statistics
		endTime = System.currentTimeMillis();

		// Return all frequent itemsets found!
		return generators;
	}

	/**
	 * Sorts the items by their support in increasing order using QuickSort. Ties
	 * are broken using item IDs to guarantee determinism.
	 * 
	 * @param items   the array of items
	 * @param support the support array
	 * @param left    the left boundary
	 * @param right   the right boundary
	 */
	private void sortItemsBySupport(int[] items, int[] support, int left, int right) {
		if (left < right) {
			int pivotIndex = left + (right - left) / 2;
			int pivotItem = items[pivotIndex];
			int pivotSupport = support[pivotItem];
			int i = left, j = right;
			while (i <= j) {
				while (support[items[i]] < pivotSupport || (support[items[i]] == pivotSupport && items[i] < pivotItem))
					i++;
				while (support[items[j]] > pivotSupport || (support[items[j]] == pivotSupport && items[j] > pivotItem))
					j--;
				if (i <= j) {
					int temp = items[i];
					items[i] = items[j];
					items[j] = temp;
					i++;
					j--;
				}
			}
			sortItemsBySupport(items, support, left, j);
			sortItemsBySupport(items, support, i, right);
		}
	}

	/**
	 * Read the input file, store all transactions in the provided list, and count
	 * item support at the same time. Uses manual character-level parsing to avoid
	 * String.split and Integer.parseInt overhead.
	 *
	 * @param input        the input file path
	 * @param transactions the list to populate with parsed transactions
	 * @return an array indicating the support of each item by original id
	 * @throws IOException if an error occurs while reading the file
	 */
	private int[] readTransactionsAndCount(String input, List<int[]> transactions) throws IOException {
		maxOldItemId = 0;
		longestTransactionSize = 0;

		// reusable buffer for items on a single line; grows if a transaction is
		// unusually long
		int[] itemBuffer = new int[128];

		// support array; grows dynamically as large item ids are encountered
		int[] supportArray = new int[2048];

		try (BufferedReader reader = new BufferedReader(new FileReader(input))) {
			String line;
			while ((line = reader.readLine()) != null) {
				int len = line.length();
				if (len == 0) {
					continue;
				}
				// skip comment lines
				char first = line.charAt(0);
				if (first == '#' || first == '%' || first == '@') {
					continue;
				}

				// parse items manually: scan char by char, build int without parseInt
				int itemCount = 0;
				int currentVal = 0;
				boolean hasVal = false;

				for (int pos = 0; pos < len; pos++) {
					char c = line.charAt(pos);
					if (c == ' ' || c == '\t') {
						if (hasVal) {
							if (currentVal >= supportArray.length) {
								supportArray = growArray(supportArray, currentVal + 1);
							}
							// count this item's support immediately
							supportArray[currentVal]++;
							if (currentVal > maxOldItemId) {
								maxOldItemId = currentVal;
							}
							if (itemCount >= itemBuffer.length) {
								itemBuffer = growArray(itemBuffer, itemCount + 1);
							}
							itemBuffer[itemCount++] = currentVal;
							currentVal = 0;
							hasVal = false;
						}
					} else if (c >= '0' && c <= '9') {
						currentVal = currentVal * 10 + (c - '0');
						hasVal = true;
					}
					// ignore any other character (e.g. '\r' on Windows)
				}
				// flush the last item on the line
				if (hasVal) {
					if (currentVal >= supportArray.length) {
						supportArray = growArray(supportArray, currentVal + 1);
					}
					supportArray[currentVal]++;
					if (currentVal > maxOldItemId) {
						maxOldItemId = currentVal;
					}
					if (itemCount >= itemBuffer.length) {
						itemBuffer = growArray(itemBuffer, itemCount + 1);
					}
					itemBuffer[itemCount++] = currentVal;
				}

				// skip lines with no items
				if (itemCount > 0) {
					int[] items = new int[itemCount];
					System.arraycopy(itemBuffer, 0, items, 0, itemCount);
					transactions.add(items);
					if (itemCount > longestTransactionSize) {
						longestTransactionSize = itemCount;
					}
				}
			}
		}
		return supportArray;
	}

	/**
	 * Grow an int array optimally to handle dynamic length changes.
	 *
	 * @param array       the array to grow
	 * @param minCapacity the minimum required length
	 * @return a new array with the old contents copied in
	 */
	private int[] growArray(int[] array, int minCapacity) {
		int newLen = Math.max(array.length * 2, minCapacity);
		int[] grown = new int[newLen];
		System.arraycopy(array, 0, grown, 0, array.length);
		return grown;
	}

	/**
	 * This is the main procedure of DefMe, which is called recursively to grow
	 * patterns.
	 * 
	 * @param itemsetX      The itemset X.
	 * @param itemsetLength the length of itemset X
	 * @param tidsetX       The tidset (cover) of X.
	 * @param supportX      The support of X
	 * @param posTail       The set "tail" is defined as the interval [posTail,
	 *                      frequentItemsCount-1]. Because items are renamed 1..n in
	 *                      support order, item at position i is (i+1).
	 * @throws IOException if an error occured while writing result to disk
	 */
	private void defme(int[] itemsetX, int itemsetLength, long[] tidsetX, int supportX, int posTail)
			throws IOException {

		// save the itemset
		save(itemsetX, itemsetLength, tidsetX, supportX);

		// If we did not reach maximum depth
		if (itemsetLength < searchMaxDepth) {

			int newItemsetLength = itemsetLength + 1;

			// allocate the tidset buffer for this depth on first visit
			if (tidsetBuffers[itemsetLength] == null) {
				tidsetBuffers[itemsetLength] = new long[wordsInUse];
			}
			long[] tidsetXeBuffer = tidsetBuffers[itemsetLength];

			// -----------------------------------------------------------------
			// Pre-allocate memory
			// Lazily initialize the matrix layer for the child depth.
			// -----------------------------------------------------------------
			if (critMatrix[newItemsetLength] == null) {
				critMatrix[newItemsetLength] = new long[newItemsetLength][];
			}
			for (int c = 0; c < newItemsetLength; c++) {
				if (critMatrix[newItemsetLength][c] == null) {
					critMatrix[newItemsetLength][c] = new long[wordsInUse];
				}
			}

			// Reference to the pre-allocated slot for the new item 'e'
			long[] critESlot = critMatrix[newItemsetLength][itemsetLength];

			// for all e in tail
			for (int i = posTail; i < frequentItemsCount; i++) {

				// items are renamed 1..n in support order, so item at position i is (i+1)
				int e = i + 1;

				// Calculate Cov(e), i.e. the tidset of e
				long[] tidsetEBits = itemTIDSarray[e];

				// Calculate Xe, i.e. X U {e}
				itemsetX[itemsetLength] = e;

				// Merged branchless calculation of intersection and critical object
				int supportXe = 0;
				long critAccumulator = 0;

				// Iterate over the 64-bit words without any conditional branches!
				for (int j = 0; j < wordsInUse; j++) {
					long wX = tidsetX[j];
					long wE = tidsetEBits[j];

					// cov(X) AND cov(e)
					long wAnd = wX & wE;
					tidsetXeBuffer[j] = wAnd;

					// add bits to support
					supportXe += Long.bitCount(wAnd);

					// calculate the critical object for item e: cov(X) AND NOT cov(e)
					long wCrit = wX & ~wE;
					critESlot[j] = wCrit;

					// Accumulate critical object bits to verify emptiness later
					critAccumulator |= wCrit;
				}

				// The support of XU{e} is the cardinality of its tidset
				// If XU{e} is infrequent or its critical object is empty, we don't need to
				// consider it
				if (supportXe < minsupRelative || critAccumulator == 0) {
					continue;
				}

				boolean validPattern = true;

				// for each existing item c already in X, narrow its crit by tidset(e).
				// (if itemsetLength == 0, this loop naturally and safely skips itself).
				for (int c = 0; c < itemsetLength; c++) {

					// direct reference to pre-allocated buffers (no null checks needed)
					long[] slot = critMatrix[newItemsetLength][c];
					long[] prevSlot = critMatrix[itemsetLength][c];

					// Branchless accumulator for the child critical object
					long cAccumulator = 0;

					// calculate cov*(X U {e}, c) = cov*(X, c) AND cov(e) branchlessly
					for (int j = 0; j < wordsInUse; j++) {
						// intersect with the tidset of e
						long w = prevSlot[j] & tidsetEBits[j];
						slot[j] = w;

						// aggregate bit values
						cAccumulator |= w;
					}

					// if the critical object is entirely empty, pattern is not a generator
					if (cAccumulator == 0) {
						validPattern = false;
						break;
					}
				}

				// stop evaluating this branch if any critical object is empty
				if (!validPattern) {
					continue;
				}

				// recursive call to explore patterns by extending XU{e} with items from "tail"
				defme(itemsetX, newItemsetLength, tidsetXeBuffer, supportXe, i + 1);
			}
		}
	}

	/**
	 * Save an itemset to disk or memory (depending on what the user chose). Item
	 * names are converted back to original names before output.
	 * 
	 * @param prefix       the itemset to be saved (contains renamed item ids)
	 * @param prefixLength the prefix length
	 * @param tidset       the tidset of this itemset
	 * @param support      the support of that itemset
	 * @throws IOException if an error occurs when writing to disk.
	 */
	private void save(int[] prefix, int prefixLength, long[] tidset, int support) throws IOException {
		// increase the itemset count
		itemsetCount++;
		// if the result should be saved to memory
		if (writer == null) {
			// copy the prefix into a new array of exact size, converting names
			int[] itemsetArray = new int[prefixLength];
			for (int i = 0; i < prefixLength; i++) {
				itemsetArray[i] = nameConverter.toOldName(prefix[i]);
			}
			// Create an object "Itemset" and add it to the set of frequent itemsets
			Itemset itemset = new Itemset(itemsetArray);
			BitSet bs = BitSet.valueOf(tidset);
			itemset.setTIDs(bs, support);
			generators.addItemset(itemset, itemset.size());
		} else {
			// if the result should be saved to a file
			outputBuffer.setLength(0);
			for (int i = 0; i < prefixLength; i++) {
				// Use method chaining for faster execution pipeline
				outputBuffer.append(nameConverter.toOldName(prefix[i])).append(' ');
			}
			// as well as its support
			outputBuffer.append("#SUP: ").append(support);

			// append() interface directly streams chars, avoiding a new String() allocation
			writer.append(outputBuffer);
			writer.newLine();
		}
	}

	/**
	 * Print statistics about the algorithm execution to System.out.
	 */
	public void printStats() {
		System.out.println("=============  DefMe v 2.66- STATS =============");
		long temps = endTime - startTimestamp;
		System.out.println(" Transactions count from database : " + tidCount);
		System.out.println(" Generator itemsets count : " + itemsetCount);
		System.out.println(" Total time ~ " + temps + " ms");
		System.out.println(" Maximum memory usage : " + MemoryLogger.getInstance().getMaxMemory() + " mb");
		System.out.println("===================================================");
	}

	/**
	 * Get the set of frequent itemsets.
	 * 
	 * @return the frequent itemsets (Itemsets).
	 */
	public Itemsets getItemsets() {
		return generators;
	}

	/**
	 * Set the maximum pattern length
	 * 
	 * @param length the maximum length
	 */
	public void setMaximumPatternLength(int length) {
		this.maxItemsetSize = length;
	}
}