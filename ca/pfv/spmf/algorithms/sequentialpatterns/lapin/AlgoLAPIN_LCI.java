package ca.pfv.spmf.algorithms.sequentialpatterns.lapin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.datastructures.triangularmatrix.AbstractTriangularMatrix;
import ca.pfv.spmf.datastructures.triangularmatrix.SparseTriangularMatrix;
import ca.pfv.spmf.input.sequence_database_array_integers.SequenceDatabase;
import ca.pfv.spmf.tools.MemoryLogger;

/***
 * This is an implementation of the LAPIN algorithm (a.k.a LAPIN-SPAM or
 * LAPIN-LCI). This implementation tries to be faithful to the original
 * technical report. There is only a minor difference in how the I-Step is
 * performed. When an I-step is performed such thats the resulting last itemset
 * of the prefix would have 3 or more items, position lists are scanned to
 * ensure that only positions where the full itemset appear are considered. In
 * the original LAPIN-SPAM, position-lists are instead updated. But because this
 * would be consume too much memory, we took the design decision of doing it
 * differently.
 *
 * The LAPIN-SPAM algorithm was originally described in this paper:
 *
 * Zhenlu Yang and Masaru Kitsuregawa. LAPIN-SPAM: An improved algorithm for
 * mining sequential pattern In Proc. of Int'l Special Workshop on Databases For
 * Next Generation Researchers (SWOD'05) in conjunction with ICDE'05, pp. 8-11,
 * Tokyo, Japan, Apr. 2005.
 *
 * Copyright (c) 2008-2013 Philippe Fournier-Viger
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license from this file.
 */
public class AlgoLAPIN_LCI {

	/** for statistics **/
	private long totalTime;

	/** Number of patterns found **/
	private int patternCount;

	/** Minimum support threshold **/
	private int minsup = 0;

	/**
	 * Minimum number of items that a pattern must contain to be output
	 * (default = 0, meaning no minimum constraint).
	 **/
	private int minPatternLength = 0;

	/**
	 * Maximum number of items that a pattern is allowed to contain
	 * (default = Integer.MAX_VALUE, meaning no maximum constraint).
	 **/
	private int maxPatternLength = Integer.MAX_VALUE;

	/** Buffered writer for output **/
	BufferedWriter writer = null;

	/** Item-is-exist table, one per sequence **/
	Table[] tables = null;

	/** Position lists for SE (Sequence Extension), one per sequence **/
	SEPositionList[] sePositionList;

	/** Position lists for IE (Item Extension) 2-itemsets, one per sequence **/
	IEPositionList[] iePositionList;

	/** Enable or disable debug mode **/
	final boolean DEBUG = false;

	/** Sequence database used for debugging **/
	SequenceDatabase seqDB = null;

	/** Matrix to count support for 2-itemsets **/
	private AbstractTriangularMatrix matrixPairCount;

	/** Path to the input file **/
	String input = null;

	/**
	 * Flat in-memory encoding of all sequences (each is a run of tokens with -1 as
	 * itemset separator and -2 as sequence terminator), used so the file is read only once.
	 */
	private int[][] sequenceTokens;

	/**
	 * Default constructor
	 */
	public AlgoLAPIN_LCI() {
	}

	/**
	 * This optional parameter allows to specify the minimum pattern length
	 * (the minimum number of items that a pattern must contain).
	 *
	 * @param minPatternLength the minimum pattern length
	 */
	public void setMinimumPatternLength(int minPatternLength) {
		this.minPatternLength = minPatternLength;
	}

	/**
	 * This optional parameter allows to specify the maximum pattern length
	 * (the maximum number of items that a pattern is allowed to contain).
	 *
	 * @param maxPatternLength the maximum pattern length
	 */
	public void setMaximumPatternLength(int maxPatternLength) {
		this.maxPatternLength = maxPatternLength;
	}

	/**
	 * Runs the sequence mining algorithm with the given parameters.
	 *
	 * @param input          the path to the input file
	 * @param outputFilePath the path to the output file
	 * @param minsupRel      the minimum support threshold as a percentage
	 * @throws IOException if an error occurs while writing to the file
	 */
	public void runAlgorithm(String input, String outputFilePath, double minsupRel) throws IOException {
		this.input = input;
		writer = new BufferedWriter(new FileWriter(outputFilePath));
		writer.write("@FILETYPE=\"Frequent sequential patterns\"");
		writer.newLine();
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.newLine();
		
		patternCount = 0;
		MemoryLogger.getInstance().reset();
		long startTime = System.currentTimeMillis();
		lapin(input, minsupRel);
		totalTime = System.currentTimeMillis() - startTime;
		writer.close();
	}

	/**
	 * Grows the given int array by doubling its capacity when it is already full.
	 *
	 * @param array the current array
	 * @param size  the current number of used slots
	 * @return the same array if there was room, or a new larger copy
	 */
	private static int[] growIfNeeded(int[] array, int size) {
		if (size == array.length) {
			return Arrays.copyOf(array, array.length * 2);
		}
		return array;
	}

	/**
	 * Parses a line into an int array without using String.split, avoiding regex overhead.
	 *
	 * @param line the input line
	 * @return an int array of all token values in the line
	 */
	private static int[] parseLine(String line) {
		int spaces = 0;
		int len = line.length();
		for (int i = 0; i < len; i++) {
			if (line.charAt(i) == ' ') spaces++;
		}
		int[] result = new int[spaces + 1];
		int idx = 0;
		int start = 0;
		for (int i = 0; i <= len; i++) {
			if (i == len || line.charAt(i) == ' ') {
				boolean negative = false;
				int j = start;
				if (line.charAt(j) == '-') {
					negative = true;
					j++;
				}
				int val = 0;
				while (j < i) {
					val = val * 10 + (line.charAt(j) - '0');
					j++;
				}
				result[idx++] = negative ? -val : val;
				start = i + 1;
			}
		}
		return result;
	}

	/**
	 * Run the LAPIN algorithm.
	 *
	 * @param input     the input file path
	 * @param minsupRel the minsup threshold as a percentage
	 */
	private void lapin(String input, double minsupRel) throws IOException {

		if (DEBUG) {
			System.out.println("=== First database scan to count number of sequences and support of single items ===");
		}

		// FIRST DATABASE SCAN:
		// Read all sequences into memory as int arrays, count sequences,
		// find the largest item ID, and compute per-item first-occurrence borders.
		int sequenceCount = 0;
		int largestItemID = 0;

		Map<Integer, int[]> mapItemBorderSids = new HashMap<>();
		Map<Integer, short[]> mapItemBorderPos = new HashMap<>();
		Map<Integer, Integer> mapItemBorderSize = new HashMap<>();

		List<int[]> allSequenceTokensList = new ArrayList<>();

		// reusable seen-item tracker: indexed by item id, stores sequenceCount+1 when seen
		int[] seenInSeq = new int[1024];

		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			String thisLine;
			while ((thisLine = reader.readLine()) != null) {
				if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
					continue;
				}
				int[] tokens = parseLine(thisLine);
				allSequenceTokensList.add(tokens);

				short itemsetID = 0;
				for (int tok : tokens) {
					if (tok == -1) {
						itemsetID++;
					} else if (tok == -2) {
						sequenceCount++;
					} else {
						if (tok >= seenInSeq.length) {
							seenInSeq = Arrays.copyOf(seenInSeq, Math.max(tok + 1, seenInSeq.length * 2));
						}
						if (seenInSeq[tok] != sequenceCount + 1) {
							seenInSeq[tok] = sequenceCount + 1;
							Integer item = tok;
							int sz = mapItemBorderSize.getOrDefault(item, 0);
							int[] sids = mapItemBorderSids.get(item);
							short[] poss = mapItemBorderPos.get(item);
							if (sids == null) {
								sids = new int[4];
								poss = new short[4];
								mapItemBorderSids.put(item, sids);
								mapItemBorderPos.put(item, poss);
							} else if (sz == sids.length) {
								sids = Arrays.copyOf(sids, sz * 2);
								poss = Arrays.copyOf(poss, sz * 2);
								mapItemBorderSids.put(item, sids);
								mapItemBorderPos.put(item, poss);
							}
							sids[sz] = sequenceCount;
							poss[sz] = itemsetID;
							mapItemBorderSize.put(item, sz + 1);
							if (tok > largestItemID) largestItemID = tok;
						}
					}
				}
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Store all parsed sequences for reuse in the following scans
		sequenceTokens = allSequenceTokensList.toArray(new int[0][]);

		tables = new Table[sequenceCount];

		minsup = (int) Math.ceil(minsupRel * sequenceCount - 0.0000001);
		if (minsup == 0) {
			minsup = 1;
		}

		if (DEBUG) {
			System.out.println("Sequence count:  " + sequenceCount);
			System.out.println("Abs. minsup: " + minsup);
		}

		// Collect frequent items (as a primitive array, no boxing) and their flat borders
		int[] frequentItemsArr = new int[16];
		int frequentItemsCount = 0;

		int[][] borderSids = new int[largestItemID + 1][];
		short[][] borderPos = new short[largestItemID + 1][];
		int[] borderSizes = new int[largestItemID + 1];

		for (Entry<Integer, Integer> entry : mapItemBorderSize.entrySet()) {
			int item = entry.getKey();
			int sz = entry.getValue();
			if (sz >= minsup) {
				// A single item forms a pattern of length 1 -- only output it
				// if 1 satisfies the min/max pattern length constraints.
				if (isValidLength(1)) {
					savePattern(item, sz);
				}
				if (frequentItemsCount == frequentItemsArr.length) {
					frequentItemsArr = Arrays.copyOf(frequentItemsArr, frequentItemsArr.length * 2);
				}
				frequentItemsArr[frequentItemsCount++] = item;
				borderSids[item] = Arrays.copyOf(mapItemBorderSids.get(item), sz);
				borderPos[item] = Arrays.copyOf(mapItemBorderPos.get(item), sz);
				borderSizes[item] = sz;
				if (DEBUG) {
					System.out.println(" Item " + item + " frequent, support=" + sz);
				}
			}
		}
		mapItemBorderSids.clear();
		mapItemBorderPos.clear();
		mapItemBorderSize.clear();

		frequentItemsArr = Arrays.copyOf(frequentItemsArr, frequentItemsCount);
		Arrays.sort(frequentItemsArr);

		// Build a lookup array so that checking if an item is frequent is an O(1) array access
		boolean[] isFrequentItem = new boolean[largestItemID + 1];
		for (int item : frequentItemsArr) {
			isFrequentItem[item] = true;
		}

		if (DEBUG) {
			System.out.println("=== Second database scan to construct item-is-exist tables ===");
		}

		// SECOND DATABASE SCAN (in-memory):
		// Create Item-is-exist tables and count support of 2-IE-sequences.
		matrixPairCount = new SparseTriangularMatrix(largestItemID + 1);
		sePositionList = new SEPositionList[sequenceCount];
		iePositionList = new IEPositionList[sequenceCount];

		// Allocated once and reused across sequences; the sequence-id trick avoids clearing.
		AbstractTriangularMatrix matrixPairLastSeenInSID = new SparseTriangularMatrix(largestItemID + 1);

		boolean[] itemSeenBool = new boolean[largestItemID + 1];
		int[] itemSeenList = new int[largestItemID + 1];
		int itemSeenCount = 0;
		BitSet currentBitset = new BitSet(largestItemID + 1);
		int[] currentItemset = new int[64];
		int currentItemsetSize = 0;

		for (int currentSequenceID = 0; currentSequenceID < sequenceCount; currentSequenceID++) {
			int[] tokens = sequenceTokens[currentSequenceID];

			for (int ci = 0; ci < itemSeenCount; ci++) itemSeenBool[itemSeenList[ci]] = false;
			itemSeenCount = 0;
			currentBitset.clear();
			currentItemsetSize = 0;

			int positionCount = -1;
			for (int tok : tokens) {
				if (tok < 0) positionCount++;
			}

			Table table = new Table();
			boolean seenNewItem = false;

			int currentPosition = positionCount;
			for (int i = tokens.length - 1; i >= 0; i--) {
				int tok = tokens[i];
				if (tok == -1) {
					for (int k = 0; k < currentItemsetSize; k++) {
						int item1 = currentItemset[k];
						for (int m = k + 1; m < currentItemsetSize; m++) {
							int item2 = currentItemset[m];
							int smaller = item1 < item2 ? item1 : item2;
							int larger = item1 < item2 ? item2 : item1;
							int sid = matrixPairLastSeenInSID.getSupportForItems(smaller, larger);
							if (sid != currentSequenceID + 1) {
								matrixPairCount.incrementCount(smaller, larger);
								matrixPairLastSeenInSID.setSupport(smaller, larger, currentSequenceID + 1);
							}
						}
					}
					currentItemsetSize = 0;
					currentPosition--;
					if (seenNewItem) {
						table.add(currentPosition, (BitSet) currentBitset.clone());
						seenNewItem = false;
					}
				} else if (tok == -2) {
					// end of sequence marker, nothing to do
				} else {
					int item = tok;
					if (item <= largestItemID && isFrequentItem[item]) {
						if (!itemSeenBool[item]) {
							itemSeenBool[item] = true;
							itemSeenList[itemSeenCount++] = item;
							seenNewItem = true;
							currentBitset.set(item);
						}
						currentItemset = growIfNeeded(currentItemset, currentItemsetSize);
						currentItemset[currentItemsetSize++] = item;
					}
				}
			}

			for (int k = 0; k < currentItemsetSize; k++) {
				int item1 = currentItemset[k];
				for (int m = k + 1; m < currentItemsetSize; m++) {
					int item2 = currentItemset[m];
					int smaller = item1 < item2 ? item1 : item2;
					int larger = item1 < item2 ? item2 : item1;
					int sid = matrixPairLastSeenInSID.getSupportForItems(smaller, larger);
					if (sid != currentSequenceID + 1) {
						matrixPairCount.incrementCount(smaller, larger);
						matrixPairLastSeenInSID.setSupport(smaller, larger, currentSequenceID + 1);
					}
				}
			}

			if (itemSeenCount > 0) {
				table.add(-1, (BitSet) currentBitset.clone());
			}

			sePositionList[currentSequenceID] = new SEPositionList(itemSeenList, itemSeenCount);
			iePositionList[currentSequenceID] = new IEPositionList();
			tables[currentSequenceID] = table;
		}

		// THIRD SCAN (in-memory):
		// Build SE-position lists and IE-position lists for frequent 2-IE-sequences.
		currentItemsetSize = 0;
		for (int currentSequenceID = 0; currentSequenceID < sequenceCount; currentSequenceID++) {
			int[] tokens = sequenceTokens[currentSequenceID];
			short itemsetID = 0;
			currentItemsetSize = 0;

			for (int tok : tokens) {
				if (tok == -1) {
					if (currentItemsetSize > 1) {
						for (int k = 0; k < currentItemsetSize; k++) {
							int item1 = currentItemset[k];
							for (int m = k + 1; m < currentItemsetSize; m++) {
								int item2 = currentItemset[m];
								int support = matrixPairCount.getSupportForItems(item1, item2);
								if (support >= minsup) {
									int smaller = item1 < item2 ? item1 : item2;
									int larger = item1 < item2 ? item2 : item1;
									iePositionList[currentSequenceID].register(smaller, larger, itemsetID);
								}
							}
						}
					}
					itemsetID++;
					currentItemsetSize = 0;
				} else if (tok == -2) {
					// end of sequence, nothing to do
				} else {
					int item = tok;
					if (item <= largestItemID && isFrequentItem[item]) {
						sePositionList[currentSequenceID].register(item, itemsetID);
						currentItemset = growIfNeeded(currentItemset, currentItemsetSize);
						currentItemset[currentItemsetSize++] = item;
					}
				}
			}

			iePositionList[currentSequenceID].sort();

			if (DEBUG) {
				System.out.println("SE Position list for sequence " + currentSequenceID);
				System.out.println(sePositionList[currentSequenceID]);
				System.out.println("IE Position list for sequence " + currentSequenceID);
				System.out.println(iePositionList[currentSequenceID]);
			}
		}

		// free the in-memory sequence copy — no longer needed
		sequenceTokens = null;

		if (DEBUG) {
			System.out.println("=== Starting sequential pattern generation ===");
		}

		patternGeneration(frequentItemsArr, borderSids, borderPos, borderSizes);

		MemoryLogger.getInstance().checkMemory();
		writer.close();
	}

	/**
	 * Checks whether a given pattern length (total number of items) satisfies
	 * the minimum/maximum pattern length constraints.
	 *
	 * @param length the pattern length (total item count)
	 * @return true if the length is within [minPatternLength, maxPatternLength]
	 */
	private boolean isValidLength(int length) {
		return length >= minPatternLength && length <= maxPatternLength;
	}

	/**
	 * Generates sequential patterns using a prefix-based approach from the list of
	 * frequent items and their respective first occurrence positions (borders) in
	 * the sequence database.
	 *
	 * @param frequentItems a sorted primitive array of all frequent items
	 * @param borderSids    flat int arrays of sequence ids for each item's border, indexed by item id
	 * @param borderPos     flat short arrays of itemset positions for each item's border, indexed by item id
	 * @param borderSizes   number of valid entries in each border array, indexed by item id
	 * @throws IOException if an error occurs while saving the patterns
	 */
	private void patternGeneration(int[] frequentItems,
			int[][] borderSids, short[][] borderPos, int[] borderSizes) throws IOException {

		// Reuse a single Prefix object for the whole search; genPatterns backtracks in place.
		Prefix prefix = new Prefix();

		for (int i = 0; i < frequentItems.length; i++) {
			int item1 = frequentItems[i];
			int sz1 = borderSizes[item1];

			if (DEBUG) {
				System.out.println("=== Considering item " + item1);
			}

			if (sz1 >= minsup) {
				List<Integer> firstItemset = new ArrayList<>(1);
				firstItemset.add(item1);
				prefix.itemsets.add(firstItemset);

				// Recursively find s-extensions (I-extensions disabled; 2-IE handled separately)
				// The single item itself has length 1; genPatterns will prune internally
				// if the maximum pattern length does not allow any further extension.
				genPatterns(prefix, borderSids[item1], borderPos[item1], sz1,
						frequentItems, frequentItems.length, frequentItems, frequentItems.length, item1, true, 1);

				prefix.itemsets.remove(prefix.itemsets.size() - 1);
			}

			// For each frequent 2-IE sequence starting with item1
			for (int k = i + 1; k < frequentItems.length; k++) {
				int item2 = frequentItems[k];
				int support = matrixPairCount.getSupportForItems(item1, item2);

				if (support >= minsup) {
					int sz2 = borderSizes[item2];

					int[] ie12Sids = new int[Math.min(sz1, sz2)];
					short[] ie12Pos = new short[Math.min(sz1, sz2)];
					int ie12Size = 0;

					int[] iterSids = sz2 < sz1 ? borderSids[item2] : borderSids[item1];
					int iterSz = sz2 < sz1 ? sz2 : sz1;

					for (int bi = 0; bi < iterSz; bi++) {
						int sid = iterSids[bi];
						short[] lp1 = sePositionList[sid].getPrimitiveListForItem(item1);
						short[] lp2 = sePositionList[sid].getPrimitiveListForItem(item2);
						int lp1sz = sePositionList[sid].getSizeForItem(item1);
						int lp2sz = sePositionList[sid].getSizeForItem(item2);
						if (lp1 == null || lp2 == null || lp1sz == 0 || lp2sz == 0) continue;

						int idx1 = 0, idx2 = 0;
						while (idx1 < lp1sz && idx2 < lp2sz) {
							short p1 = lp1[idx1];
							short p2 = lp2[idx2];
							if (p1 < p2) { idx1++; }
							else if (p1 > p2) { idx2++; }
							else {
								if (ie12Size == ie12Sids.length) {
									ie12Sids = Arrays.copyOf(ie12Sids, ie12Size * 2);
									ie12Pos = Arrays.copyOf(ie12Pos, ie12Size * 2);
								}
								ie12Sids[ie12Size] = sid;
								ie12Pos[ie12Size] = p1;
								ie12Size++;
								break;
							}
						}
					}

					if (DEBUG) {
						System.out.println("=== 2-IE {" + item1 + "," + item2 + "} support=" + support);
					}

					List<Integer> itemset = new ArrayList<>(2);
					itemset.add(item1);
					itemset.add(item2);
					prefix.itemsets.add(itemset);

					// This 2-IE pattern has length 2 (two items in one itemset)
					if (isValidLength(2)) {
						savePattern(prefix, support);
					}

					genPatterns(prefix, ie12Sids, ie12Pos, ie12Size,
							frequentItems, frequentItems.length, frequentItems, frequentItems.length, item2, false, 2);

					prefix.itemsets.remove(prefix.itemsets.size() - 1);
				}
			}
		}
	}

	/**
	 * The main recursive method of LAPIN; uses flat primitive arrays for item lists and
	 * borders throughout to avoid boxing on the hottest path of the algorithm.
	 *
	 * @param prefix                     the current prefix (mutated in place; callers backtrack)
	 * @param prefixBorderSids           sequence ids of the prefix border
	 * @param prefixBorderPos            itemset positions of the prefix border
	 * @param prefixBorderSize           number of valid entries in the border arrays
	 * @param sn                         items that could be appended by s-extension
	 * @param snSize                     number of valid entries in sn
	 * @param in                         items that could be appended by i-extension
	 * @param inSize                     number of valid entries in in
	 * @param hasToBeGreaterThanForIStep the lower bound for i-extension item ids
	 * @param doNotPerformIExtensions    if true, skip the I-step entirely
	 * @param currentLength              the total number of items in the current prefix
	 * @throws IOException if error while writing to file
	 */
	private void genPatterns(Prefix prefix,
			int[] prefixBorderSids, short[] prefixBorderPos, int prefixBorderSize,
			int[] sn, int snSize, int[] in, int inSize,
			int hasToBeGreaterThanForIStep, boolean doNotPerformIExtensions, int currentLength) throws IOException {

		// If the current prefix has already reached the maximum allowed pattern
		// length, any extension (S-step or I-step) would exceed it, so there is
		// no point in doing any further work here.
		if (currentLength >= maxPatternLength) {
			return;
		}

		// ====== S-STEPS ======
		int[] sTempItems = new int[snSize];
		int sTempCount = 0;
		int[][] sTempBorderSids = new int[snSize][];
		short[][] sTempBorderPos = new short[snSize][];
		int[] sTempBorderSizes = new int[snSize];

		// Scratch buffers reused across every candidate item below, instead of allocating
		// a fresh pair of arrays for each candidate (which was pure garbage for rejected items).
		int[] scratchSids = new int[prefixBorderSize];
		short[] scratchPos = new short[prefixBorderSize];

		for (int si = 0; si < snSize; si++) {
			int item = sn[si];
			int newBorderSize = 0;

			for (int bi = 0; bi < prefixBorderSize; bi++) {
				int sid = prefixBorderSids[bi];
				int prevPos = prefixBorderPos[bi];
				short[] listPositions = sePositionList[sid].getPrimitiveListForItem(item);
				if (listPositions == null) continue;
				int listSz = sePositionList[sid].getSizeForItem(item);
				for (int pi = 0; pi < listSz; pi++) {
					short pos = listPositions[pi];
					if (pos > prevPos) {
						scratchSids[newBorderSize] = sid;
						scratchPos[newBorderSize] = pos;
						newBorderSize++;
						break;
					}
				}
			}

			if (newBorderSize >= minsup) {
				sTempBorderSids[sTempCount] = Arrays.copyOf(scratchSids, newBorderSize);
				sTempBorderPos[sTempCount] = Arrays.copyOf(scratchPos, newBorderSize);
				sTempBorderSizes[sTempCount] = newBorderSize;
				sTempItems[sTempCount] = item;
				sTempCount++;
			}
		}

		// for each pattern recorded for the s-step
		for (int k = 0; k < sTempCount; k++) {
			int item = sTempItems[k];
			List<Integer> itemset = new ArrayList<>(1);
			itemset.add(item);
			prefix.itemsets.add(itemset);

			int newLength = currentLength + 1;

			if (isValidLength(newLength)) {
				savePattern(prefix, sTempBorderSizes[k]);
			}

			genPatterns(prefix, sTempBorderSids[k], sTempBorderPos[k], sTempBorderSizes[k],
					sTempItems, sTempCount, sTempItems, sTempCount, item, false, newLength);

			prefix.itemsets.remove(prefix.itemsets.size() - 1);
		}

		if (doNotPerformIExtensions) {
			return;
		}

		// ======== I STEPS =======
		int[] iTempItems = new int[inSize];
		int iTempCount = 0;
		int[][] iTempBorderSids = new int[inSize][];
		short[][] iTempBorderPos = new short[inSize][];
		int[] iTempBorderSizes = new int[inSize];

		// the item has to be greater than hasToBeGreaterThanForIStep
		int index = Arrays.binarySearch(in, 0, inSize, hasToBeGreaterThanForIStep);

		List<Integer> lastItemset = prefix.itemsets.get(prefix.itemsets.size() - 1);
		boolean willAddSecondItem = lastItemset.size() == 1;

		// Precompute, once per border entry, the bitset that applies at that position.
		// This lookup is identical for every candidate item (only the final bit check
		// differs), so computing it once here instead of once per candidate item turns
		// the per-item cost from O(log V) table lookups into a single O(1) bit test.
		BitSet[] activeBitsets = new BitSet[prefixBorderSize];
		for (int bi = 0; bi < prefixBorderSize; bi++) {
			Table table = tables[prefixBorderSids[bi]];
			activeBitsets[bi] = table.findBitsetBefore(prefixBorderPos[bi]);
		}

		// Scratch buffers reused across every candidate item below
		int[] iScratchSids = new int[prefixBorderSize];
		short[] iScratchPos = new short[prefixBorderSize];

		for (int ii = index; ii < inSize; ii++) {
			int item = in[ii];

			// perform the I-STEP (estimated support, an upper bound)
			int support = 0;
			for (int bi = 0; bi < prefixBorderSize; bi++) {
				BitSet bs = activeBitsets[bi];
				if (bs != null && bs.get(item)) {
					support++;
				}
			}
			if (support < minsup) {
				continue;
			}

			int newBorderSize = recalculateBorderForIExtensionPrimitive(
					lastItemset, prefixBorderSids, prefixBorderPos, prefixBorderSize,
					hasToBeGreaterThanForIStep, item, willAddSecondItem,
					iScratchSids, iScratchPos);

			if (newBorderSize >= minsup) {
				iTempBorderSids[iTempCount] = Arrays.copyOf(iScratchSids, newBorderSize);
				iTempBorderPos[iTempCount] = Arrays.copyOf(iScratchPos, newBorderSize);
				iTempBorderSizes[iTempCount] = newBorderSize;
				iTempItems[iTempCount] = item;
				iTempCount++;
			}
		}

		// for each pattern recorded for the i-step
		for (int k = 0; k < iTempCount; k++) {
			int item = iTempItems[k];
			lastItemset.add(item);

			int newLength = currentLength + 1;

			if (isValidLength(newLength)) {
				savePattern(prefix, iTempBorderSizes[k]);
			}

			genPatterns(prefix, iTempBorderSids[k], iTempBorderPos[k], iTempBorderSizes[k],
					sTempItems, sTempCount, iTempItems, iTempCount, item, false, newLength);

			lastItemset.remove(lastItemset.size() - 1);
		}

		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Recalculate the prefix border following an i-extension using primitive arrays.
	 *
	 * @param prefixLastItemset last itemset of the current prefix
	 * @param borderSids        sequence ids of the current border
	 * @param borderPos         itemset positions of the current border
	 * @param borderSize        number of valid border entries
	 * @param item1             the anchor item (last item of the pair)
	 * @param item2             the item being appended
	 * @param willAddSecondItem true if the last itemset currently has only one item
	 * @param outSids           output array for new border sequence ids (caller-allocated, reused)
	 * @param outPos            output array for new border positions (caller-allocated, reused)
	 * @return the number of valid entries written into outSids/outPos
	 */
	private int recalculateBorderForIExtensionPrimitive(
			List<Integer> prefixLastItemset,
			int[] borderSids, short[] borderPos, int borderSize,
			int item1, int item2, boolean willAddSecondItem,
			int[] outSids, short[] outPos) {

		int newBorderSize = 0;

		for (int bi = 0; bi < borderSize; bi++) {
			int sid = borderSids[bi];
			short previousItemsetID = borderPos[bi];
			IEPositionList positionLists = iePositionList[sid];

			short[] listPositions = positionLists.getPrimitiveListForPair(item1, item2);
			int listSz = positionLists.getSizeForPair(item1, item2);
			if (listPositions == null) continue;

			loop: for (int pi = 0; pi < listSz; pi++) {
				short pos = listPositions[pi];
				if (pos >= previousItemsetID) {
					if (!willAddSecondItem) {
						SEPositionList plists = sePositionList[sid];
						for (int li = 0; li < prefixLastItemset.size() - 1; li++) {
							int itemX = prefixLastItemset.get(li);
							short[] plistX = plists.getPrimitiveListForItem(itemX);
							int plistXSz = plists.getSizeForItem(itemX);
							int lo = 0, hi = plistXSz - 1, found = -1;
							while (lo <= hi) {
								int mid = (lo + hi) >>> 1;
								if (plistX[mid] == pos) { found = mid; break; }
								else if (plistX[mid] < pos) lo = mid + 1;
								else hi = mid - 1;
							}
							if (found < 0) continue loop;
						}
					}
					outSids[newBorderSize] = sid;
					outPos[newBorderSize] = pos;
					newBorderSize++;
					break;
				}
			}
		}

		return newBorderSize;
	}

	/**
	 * Save a single-item pattern that has been found.
	 *
	 * @param item    the item
	 * @param support the support of the item
	 * @throws IOException if error while writing to file
	 */
	private void savePattern(int item, int support) throws IOException {
		patternCount++;
		StringBuilder patternBuilder = new StringBuilder();
		patternBuilder.append(item).append(" -1 #SUP: ").append(support);
		writer.write(patternBuilder.toString());
		writer.newLine();
		if (DEBUG) {
			System.out.println(patternBuilder);
		}
	}

	/**
	 * Save a prefix (pattern) to file.
	 *
	 * @param prefix  the prefix
	 * @param support the prefix support
	 * @throws IOException if error occurs when writing to file
	 */
	private void savePattern(Prefix prefix, int support) throws IOException {
		patternCount++;
		StringBuilder patternBuilder = new StringBuilder();
		for (List<Integer> itemset : prefix.itemsets) {
			for (Integer item : itemset) {
				patternBuilder.append(item).append(' ');
			}
			patternBuilder.append("-1 ");
		}
		patternBuilder.append("#SUP: ").append(support);
		writer.write(patternBuilder.toString());
		writer.newLine();
		if (DEBUG) {
			System.out.println("OUTPUT PATTERN: " + patternBuilder);
		}
	}

	/**
	 * Print statistics about the algorithm execution time.
	 */
	public void printStatistics() {
		String lineSep = System.lineSeparator();
		StringBuilder stats = new StringBuilder(200);
		stats.append("=============  LAPIN v.2.67 - STATISTICS  =============").append(lineSep)
				.append("Total time (ms)             : ").append(totalTime).append(lineSep)
				.append("Frequent sequences count    : ").append(patternCount).append(lineSep)
				.append("Max memory usage (MB)       : ").append(MemoryLogger.getInstance().getMaxMemory())
				.append(lineSep).append("Minimum support (absolute)  : ").append(minsup).append(lineSep)
				.append("=================================================").append(lineSep);
		System.out.print(stats);
	}
}