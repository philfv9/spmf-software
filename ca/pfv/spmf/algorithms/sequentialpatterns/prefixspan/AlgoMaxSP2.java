package ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.ItemNameConverter;
import ca.pfv.spmf.patterns.itemset_list_integers_without_support.Itemset;
import ca.pfv.spmf.tools.MemoryLogger;
/*
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
 * Copyright (c) Philipe Fournier-Viger et al.
 *
 * Do not remove copyright and license information.
 */
/**
 * Optimized MaxSP implementation built on top of AlgoPrefixSpan structures.
 * 
 *  Copyright (c) 2008-2026 Philippe Fournier-Viger
 */
public class AlgoMaxSP2 {

	// -------------------------------------------------------------------------
	// Fields shared with AlgoPrefixSpan design
	// -------------------------------------------------------------------------

	/** Total execution time of the algorithm, in milliseconds. */
	long totalTime;

	/** Number of patterns found so far. */
	int patternCount;

	/** Minimum support threshold expressed as an absolute number of sequences. */
	private int minsuppAbsolute;

	/** Writer used to output patterns directly to a file, or null to keep them in memory. */
	BufferedWriter writer = null;

	/** Sequential patterns found, used only when no output file is specified. */
	private SequentialPatterns patterns = null;

	/** Maximum number of itemsets allowed in a discovered pattern. */
	private int maximumPatternLength = 1000;

	/** Whether the output should include the sequence IDs of each pattern's support set. */
	boolean showSequenceIdentifiers = false;

	/** Size of the reusable buffers used while a pattern is being grown. */
	final int BUFFERS_SIZE = 2000;

	/** Buffer holding the tokens of the pattern currently being grown. */
	private int[] patternBuffer = new int[BUFFERS_SIZE];

	/** Number of sequences in the input database. */
	int sequenceCount = 0;

	/** The sequence database currently being mined. */
	SequenceDatabase sequenceDatabase;

	/** Whether the database contains itemsets composed of more than one item. */
	boolean containsItemsetsWithMultipleItems = false;

	/** Per-depth buffer of sequence IDs of the projected database. */
	private int[][] projSeqIDsBuffer;

	/** Per-depth buffer of start indexes into each sequence of the projected database. */
	private int[][] projIndexesBuffer;

	/** Per-depth reusable arrays used to count frequent single-item extensions. */
	private AlgoPrefixSpan.ItemPairArray[] singleItemsArrays;

	/** Per-depth reusable arrays used to count frequent i-extensions and s-extensions. */
	private AlgoPrefixSpan.ArrayFrequentPairs[] frequentPairsArrays;

	/** Converts between original item names and their renamed consecutive ids. */
	private ItemNameConverter itemNameConverter;

	/** Number of items found to be frequent in the database. */
	private int frequentItemCount;

	// -------------------------------------------------------------------------
	// MaxSP-specific backward-extension structures
	// -------------------------------------------------------------------------

	/** Start position (inclusive) of each itemset of the current pattern in {@link #patternBuffer}. */
	private int[] itemsetStartBuf;

	/** End position (exclusive) of each itemset of the current pattern in {@link #patternBuffer}. */
	private int[] itemsetEndBuf;

	/** Occurrence count of each renamed item within the current support set. */
	private int[] localItemCount;

	/** Stamp recording the last call for which {@link #localItemCount} was initialised for an item. */
	private int[] itemCallStamp;

	/** Stamp recording the last sequence in which an item was already counted. */
	private int[] itemSeqStamp;

	/** Items whose {@link #localItemCount} entry is valid for the current call. */
	private int[] localTouchedItems;

	/** Number of valid entries in {@link #localTouchedItems}. */
	int localTouchedCount;

	/** Incremented once per call to {@link #findLocallyFrequentItems}. */
	private int callStampCounter = 0;

	/** Incremented once per sequence scanned inside {@link #findLocallyFrequentItems}. */
	private int seqStampCounter = 0;

	/** Reusable buffer holding root-level support-set sequence IDs. */
	private int[] rootSidBuffer;

	// -------------------------------------------------------------------------
	// Constructor
	// -------------------------------------------------------------------------

	/**
	 * Creates a new instance of the MaxSP algorithm.
	 */
	public AlgoMaxSP2() {
	}

	// -------------------------------------------------------------------------
	// Public run methods
	// -------------------------------------------------------------------------

	/**
	 * Runs the MaxSP algorithm using a relative minimum support threshold.
	 *
	 * @param inputFile      path to the input sequence database file
	 * @param minsupRelative minimum support as a fraction (between 0 and 1) of the database size
	 * @param outputFilePath path of the output file, or null to keep the patterns in memory
	 * @return the maximal sequential patterns found, or null if they were written to a file
	 * @throws IOException if an error occurs while reading the input or writing the output
	 */
	public SequentialPatterns runAlgorithm(String inputFile, double minsupRelative, String outputFilePath)
			throws IOException {
		totalTime = 0;
		long startTime = System.currentTimeMillis();
		MemoryLogger.getInstance().reset();
		// load the sequence database
		sequenceDatabase = new SequenceDatabase();
		sequenceDatabase.loadFile(inputFile);
		sequenceCount = sequenceDatabase.size();
		// convert the relative minsup to an absolute number of sequences
		this.minsuppAbsolute = (int) Math.ceil(minsupRelative * sequenceCount);
		if (this.minsuppAbsolute == 0)
			this.minsuppAbsolute = 1;
		// run the mining process
		maxSP(sequenceDatabase, outputFilePath);
		sequenceDatabase = null;
		totalTime = System.currentTimeMillis() - startTime;
		if (writer != null)
			writer.close();
		return patterns;
	}

	/**
	 * Runs the MaxSP algorithm using an absolute minimum support threshold.
	 *
	 * @param inputFile      path to the input sequence database file
	 * @param outputFilePath path of the output file, or null to keep the patterns in memory
	 * @param minsup         minimum support as an absolute number of sequences
	 * @return the maximal sequential patterns found, or null if they were written to a file
	 * @throws IOException if an error occurs while reading the input or writing the output
	 */
	public SequentialPatterns runAlgorithm(String inputFile, String outputFilePath, int minsup) throws IOException {
		patternCount = 0;
		MemoryLogger.getInstance().reset();
		this.minsuppAbsolute = minsup;
		totalTime = 0;
		long startTime = System.currentTimeMillis();
		// load the sequence database
		sequenceDatabase = new SequenceDatabase();
		sequenceDatabase.loadFile(inputFile);
		// run the mining process
		maxSP(sequenceDatabase, outputFilePath);
		sequenceDatabase = null;
		totalTime = System.currentTimeMillis() - startTime;
		if (writer != null)
			writer.close();
		return patterns;
	}

	// -------------------------------------------------------------------------
	// Core algorithm entry point
	// -------------------------------------------------------------------------

	/**
	 * Prepares the shared buffers, finds the frequent items and dispatches the mining process to
	 * the single-item or multi-item variant depending on the database content.
	 *
	 * @param sequenceDatabase the sequence database to mine
	 * @param outputFilePath   path of the output file, or null to keep the patterns in memory
	 * @throws IOException if an error occurs while writing the output
	 */
	private void maxSP(SequenceDatabase sequenceDatabase, String outputFilePath) throws IOException {
		// prepare the output: either write to a file or keep patterns in memory
		if (outputFilePath == null) {
			writer = null;
			patterns = new SequentialPatterns("MAXIMAL SEQUENTIAL PATTERNS");
		} else {
			patterns = null;
			writer = new BufferedWriter(new FileWriter(outputFilePath));
			writer.write("@FILETYPE=\"Frequent maximal sequential patterns\"");
			writer.append(System.lineSeparator());
			writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
			writer.append(System.lineSeparator());
		}

		sequenceCount = sequenceDatabase.size();
		// allocate the per-depth buffers
		projSeqIDsBuffer = new int[maximumPatternLength + 1][];
		projIndexesBuffer = new int[maximumPatternLength + 1][];
		singleItemsArrays = new AlgoPrefixSpan.ItemPairArray[maximumPatternLength + 1];
		frequentPairsArrays = new AlgoPrefixSpan.ArrayFrequentPairs[maximumPatternLength + 1];
		itemsetStartBuf = new int[BUFFERS_SIZE];
		itemsetEndBuf = new int[BUFFERS_SIZE];
		rootSidBuffer = new int[sequenceCount];

		// Scan for frequent items and rename them to consecutive ids.
		Map<Integer, List<Integer>> mapSequenceID = findSequencesContainingItems();
		frequentItemCount = 0;
		for (List<Integer> seqList : mapSequenceID.values())
			if (seqList.size() >= minsuppAbsolute)
				frequentItemCount++;
		itemNameConverter = new ItemNameConverter(frequentItemCount);
		for (Entry<Integer, List<Integer>> entry : mapSequenceID.entrySet())
			if (entry.getValue().size() >= minsuppAbsolute)
				itemNameConverter.assignNewName(entry.getKey());

		// Allocate stamp-based structures sized to the number of frequent items.
		localItemCount = new int[frequentItemCount + 1];
		itemCallStamp = new int[frequentItemCount + 1];
		itemSeqStamp = new int[frequentItemCount + 1];
		localTouchedItems = new int[frequentItemCount + 1];

		// dispatch to the appropriate variant
		if (containsItemsetsWithMultipleItems)
			maxspWithMultipleItems(mapSequenceID);
		else
			maxspWithSingleItems(mapSequenceID);

		// Release buffers.
		projSeqIDsBuffer = null;
		projIndexesBuffer = null;
		singleItemsArrays = null;
		frequentPairsArrays = null;
		itemNameConverter = null;
		itemsetStartBuf = null;
		itemsetEndBuf = null;
		localItemCount = null;
		itemCallStamp = null;
		itemSeqStamp = null;
		localTouchedItems = null;
		rootSidBuffer = null;
	}

	// -------------------------------------------------------------------------
	// Lazy buffer allocation
	// -------------------------------------------------------------------------

	/**
	 * Lazily allocates the projected-database and item-counting buffers needed at a given recursion depth.
	 *
	 * @param depth the recursion depth for which buffers must be available
	 */
	private void ensureDepthBuffers(int depth) {
		// allocate the projected sequence id / index buffers if needed
		if (projSeqIDsBuffer[depth] == null) {
			projSeqIDsBuffer[depth] = new int[sequenceCount];
			projIndexesBuffer[depth] = new int[sequenceCount];
		}
		// allocate the item counting arrays if needed
		if (singleItemsArrays[depth] == null)
			singleItemsArrays[depth] = new AlgoPrefixSpan.ItemPairArray(frequentItemCount);
		if (frequentPairsArrays[depth] == null)
			frequentPairsArrays[depth] = new AlgoPrefixSpan.ArrayFrequentPairs(frequentItemCount);
	}

	// =========================================================================
	// SINGLE-ITEM-PER-ITEMSET DATABASE VARIANT
	// =========================================================================
	//
	// After pruning/renaming, sequences contain ONLY positive item tokens and a
	// terminal -2. There are NO -1 separators. Each positive token represents
	// its own singleton itemset. This is important for the backward-extension
	// check (see matchesExtendedPatternSingleItem).

	/**
	 * Mines maximal sequential patterns for a database whose itemsets each contain a single item.
	 *
	 * @param mapSequenceID map from each item to the list of sequence IDs that contain it
	 * @throws IOException if an error occurs while writing the output
	 */
	private void maxspWithSingleItems(Map<Integer, List<Integer>> mapSequenceID) throws IOException {
		// Prune and rename in-place.
		List<int[]> sequences = sequenceDatabase.getSequences();
		for (int i = 0, size = sequences.size(); i < size; i++) {
			int[] sequence = sequences.get(i);
			int currentPosition = 0;
			for (int j = 0, seqLen = sequence.length; j < seqLen; j++) {
				int token = sequence[j];
				if (token > 0) {
					int newName = itemNameConverter.toNewName(token);
					if (newName != 0)
						sequence[currentPosition++] = newName;
					// -1 tokens are intentionally skipped (single-item db: no separators needed)
				} else if (token == -2) {
					if (currentPosition > 0) {
						sequence[currentPosition] = -2;
						int[] newSeq = new int[currentPosition + 1];
						System.arraycopy(sequence, 0, newSeq, 0, currentPosition + 1);
						sequences.set(i, newSeq);
					} else {
						sequences.set(i, null);
					}
				}
			}
		}

		// Explore each frequent item as a length-1 prefix.
		for (Entry<Integer, List<Integer>> entry : mapSequenceID.entrySet()) {
			List<Integer> sequenceIDs = entry.getValue();
			int support = sequenceIDs.size();
			if (support < minsuppAbsolute)
				continue;

			int item = entry.getKey();
			int newItem = itemNameConverter.toNewName(item);
			patternBuffer[0] = newItem;

			// Build projected database and recurse.
			boolean hasForwardExtension = false;
			if (maximumPatternLength > 1) {
				ensureDepthBuffers(1);
				int projSize = buildProjectedDatabaseSingleItems(newItem, sequenceIDs, projSeqIDsBuffer[1],
						projIndexesBuffer[1]);
				if (projSize > 0)
					hasForwardExtension = recursionSingleItems(projSeqIDsBuffer[1], projIndexesBuffer[1], projSize, 2,
							0, 1);
			}

			// Maximality check for this length-1 pattern.
			if (!hasForwardExtension) {
				int c = 0;
				for (int idx = 0, sz = sequenceIDs.size(); idx < sz; idx++)
					rootSidBuffer[c++] = sequenceIDs.get(idx);
				// allowMerge=false: merging an extra item into a singleton itemset
				// would create a 2-item itemset, which can never appear in a
				// single-item database, so all merge extensions are trivially not
				// frequent and we skip them.
				if (!checkBackwardExtension(rootSidBuffer, support, 0, false))
					savePattern(item, support, sequenceIDs);
			}
		}
	}

	/**
	 * Recursively grows the current pattern (single-item variant) and saves it if it is maximal.
	 *
	 * @param projSeqIDs         sequence IDs of the projected database at this depth
	 * @param projIndexes        start indexes into each sequence of the projected database
	 * @param projSize           number of entries in the projected database
	 * @param k                  current pattern length
	 * @param lastBufferPosition last filled position of the pattern in {@link #patternBuffer}
	 * @param depth              current recursion depth
	 * @return true if at least one child pattern with sufficient support was found
	 * @throws IOException if an error occurs while writing the output
	 */
	private boolean recursionSingleItems(int[] projSeqIDs, int[] projIndexes, int projSize, int k,
			int lastBufferPosition, int depth) throws IOException {
		ensureDepthBuffers(depth);
		AlgoPrefixSpan.ItemPairArray itemsArr = singleItemsArrays[depth];
		// find items that can extend the current pattern
		findAllFrequentPairsSingleItems(projSeqIDs, projIndexes, projSize, itemsArr);

		int nextDepth = depth + 1;
		ensureDepthBuffers(nextDepth);
		boolean hasFrequentChild = false;

		for (int idx = 0; idx < itemsArr.touchedCount; idx++) {
			int item = itemsArr.touchedItems[idx];
			AlgoPrefixSpan.PairData pd = itemsArr.dataByItem[item];
			if (pd.projSize < minsuppAbsolute)
				continue;

			hasFrequentChild = true;
			// Every extension is a new singleton itemset: write a -1 separator.
			patternBuffer[lastBufferPosition + 1] = -1;
			patternBuffer[lastBufferPosition + 2] = item;
			int newBufPos = lastBufferPosition + 2;

			System.arraycopy(pd.seqIDs, 0, projSeqIDsBuffer[nextDepth], 0, pd.projSize);
			System.arraycopy(pd.indexes, 0, projIndexesBuffer[nextDepth], 0, pd.projSize);

			// recurse to grow the pattern further
			boolean childHasFwd = false;
			if (k < maximumPatternLength)
				childHasFwd = recursionSingleItems(projSeqIDsBuffer[nextDepth], projIndexesBuffer[nextDepth],
						pd.projSize, k + 1, newBufPos, nextDepth);

			// save the pattern if it has no forward or backward extension
			if (!childHasFwd) {
				if (!checkBackwardExtension(projSeqIDsBuffer[nextDepth], pd.projSize, newBufPos, false))
					savePattern(newBufPos, projSeqIDsBuffer[nextDepth], pd.projSize);
			}
		}

		// reset the counting structures for reuse at this depth
		for (int idx = 0; idx < itemsArr.touchedCount; idx++)
			itemsArr.dataByItem[itemsArr.touchedItems[idx]].reset();
		return hasFrequentChild;
	}

	// =========================================================================
	// MULTI-ITEM-PER-ITEMSET DATABASE VARIANT
	// =========================================================================

	/**
	 * Mines maximal sequential patterns for a database whose itemsets may contain several items.
	 *
	 * @param mapSequenceID map from each item to the list of sequence IDs that contain it
	 * @throws IOException if an error occurs while writing the output
	 */
	private void maxspWithMultipleItems(Map<Integer, List<Integer>> mapSequenceID) throws IOException {
		// prune and rename the database in-place
		List<int[]> sequences = sequenceDatabase.getSequences();
		for (int i = 0, size = sequences.size(); i < size; i++) {
			int[] sequence = sequences.get(i);
			int currentPosition = 0;
			int currentItemsetItemCount = 0;
			for (int j = 0, seqLen = sequence.length; j < seqLen; j++) {
				int token = sequence[j];
				if (token > 0) {
					int newName = itemNameConverter.toNewName(token);
					if (newName != 0) {
						sequence[currentPosition++] = newName;
						currentItemsetItemCount++;
					}
				} else if (token == -1) {
					if (currentItemsetItemCount > 0) {
						sequence[currentPosition++] = -1;
						currentItemsetItemCount = 0;
					}
				} else if (token == -2) {
					if (currentPosition > 0) {
						sequence[currentPosition] = -2;
						int[] newSeq = new int[currentPosition + 1];
						System.arraycopy(sequence, 0, newSeq, 0, currentPosition + 1);
						sequences.set(i, newSeq);
					} else {
						sequences.set(i, null);
					}
				}
			}
		}

		// explore each frequent item as a length-1 prefix
		for (Entry<Integer, List<Integer>> entry : mapSequenceID.entrySet()) {
			List<Integer> sequenceIDs = entry.getValue();
			int support = sequenceIDs.size();
			if (support < minsuppAbsolute)
				continue;

			int item = entry.getKey();
			int newItem = itemNameConverter.toNewName(item);
			patternBuffer[0] = newItem;

			// build projected database and recurse
			boolean hasForwardExtension = false;
			if (maximumPatternLength > 1) {
				ensureDepthBuffers(1);
				int projSize = buildProjectedDatabaseFirstTimeMultipleItems(newItem, sequenceIDs, projSeqIDsBuffer[1],
						projIndexesBuffer[1]);
				if (projSize > 0)
					hasForwardExtension = recursion(projSeqIDsBuffer[1], projIndexesBuffer[1], projSize, 2, 0, 1);
			}

			// maximality check for this length-1 pattern
			if (!hasForwardExtension) {
				int c = 0;
				for (int idx = 0, sz = sequenceIDs.size(); idx < sz; idx++)
					rootSidBuffer[c++] = sequenceIDs.get(idx);
				if (!checkBackwardExtension(rootSidBuffer, support, 0, true))
					savePattern(item, support, sequenceIDs);
			}
		}
	}

	/**
	 * Recursively grows the current pattern (multi-item variant) using i-extensions and s-extensions,
	 * and saves it if it is maximal.
	 *
	 * @param projSeqIDs         sequence IDs of the projected database at this depth
	 * @param projIndexes        start indexes into each sequence of the projected database
	 * @param projSize           number of entries in the projected database
	 * @param k                  current pattern length
	 * @param lastBufferPosition last filled position of the pattern in {@link #patternBuffer}
	 * @param depth              current recursion depth
	 * @return true if at least one child pattern with sufficient support was found
	 * @throws IOException if an error occurs while writing the output
	 */
	private boolean recursion(int[] projSeqIDs, int[] projIndexes, int projSize, int k, int lastBufferPosition,
			int depth) throws IOException {
		ensureDepthBuffers(depth);
		AlgoPrefixSpan.ArrayFrequentPairs mapsPairs = frequentPairsArrays[depth];
		// find items that can extend the current pattern, split by extension type
		findAllFrequentPairs(projSeqIDs, projIndexes, projSize, lastBufferPosition, mapsPairs);

		int nextDepth = depth + 1;
		ensureDepthBuffers(nextDepth);
		boolean hasFrequentChild = false;

		// i-extensions (item appended within last itemset of prefix)
		AlgoPrefixSpan.ItemPairArray postfixArr = mapsPairs.pairsInPostfix;
		for (int idx = 0; idx < postfixArr.touchedCount; idx++) {
			int item = postfixArr.touchedItems[idx];
			AlgoPrefixSpan.PairData pd = postfixArr.dataByItem[item];
			if (pd.projSize < minsuppAbsolute)
				continue;

			hasFrequentChild = true;
			int newBufPos = lastBufferPosition + 1;
			patternBuffer[newBufPos] = item;

			System.arraycopy(pd.seqIDs, 0, projSeqIDsBuffer[nextDepth], 0, pd.projSize);
			System.arraycopy(pd.indexes, 0, projIndexesBuffer[nextDepth], 0, pd.projSize);

			boolean childHasFwd = false;
			if (k < maximumPatternLength)
				childHasFwd = recursion(projSeqIDsBuffer[nextDepth], projIndexesBuffer[nextDepth], pd.projSize, k + 1,
						newBufPos, nextDepth);

			if (!childHasFwd) {
				if (!checkBackwardExtension(projSeqIDsBuffer[nextDepth], pd.projSize, newBufPos, true))
					savePattern(newBufPos, projSeqIDsBuffer[nextDepth], pd.projSize);
			}
		}

		// s-extensions (item becomes a new singleton itemset)
		AlgoPrefixSpan.ItemPairArray pairsArr = mapsPairs.pairs;
		for (int idx = 0; idx < pairsArr.touchedCount; idx++) {
			int item = pairsArr.touchedItems[idx];
			AlgoPrefixSpan.PairData pd = pairsArr.dataByItem[item];
			if (pd.projSize < minsuppAbsolute)
				continue;

			hasFrequentChild = true;
			int newBufPos = lastBufferPosition + 1;
			patternBuffer[newBufPos] = -1;
			newBufPos++;
			patternBuffer[newBufPos] = item;

			System.arraycopy(pd.seqIDs, 0, projSeqIDsBuffer[nextDepth], 0, pd.projSize);
			System.arraycopy(pd.indexes, 0, projIndexesBuffer[nextDepth], 0, pd.projSize);

			boolean childHasFwd = false;
			if (k < maximumPatternLength)
				childHasFwd = recursion(projSeqIDsBuffer[nextDepth], projIndexesBuffer[nextDepth], pd.projSize, k + 1,
						newBufPos, nextDepth);

			if (!childHasFwd) {
				if (!checkBackwardExtension(projSeqIDsBuffer[nextDepth], pd.projSize, newBufPos, true))
					savePattern(newBufPos, projSeqIDsBuffer[nextDepth], pd.projSize);
			}
		}

		// reset the counting structures for reuse at this depth
		for (int idx = 0; idx < postfixArr.touchedCount; idx++)
			postfixArr.dataByItem[postfixArr.touchedItems[idx]].reset();
		for (int idx = 0; idx < pairsArr.touchedCount; idx++)
			pairsArr.dataByItem[pairsArr.touchedItems[idx]].reset();

		return hasFrequentChild;
	}

	// =========================================================================
	// BACKWARD EXTENSION CHECK
	// =========================================================================

	/**
	 * Checks whether the current pattern has a frequent one-item backward extension (a merge into an
	 * existing itemset or an insertion of a new singleton itemset), which would mean it is not maximal.
	 *
	 * @param sidArray           support-set sequence IDs of the current pattern
	 * @param sidCount           number of valid entries in sidArray
	 * @param lastBufferPosition last position of the current pattern in {@link #patternBuffer}
	 * @param allowMerge         whether merge extensions should also be tested
	 * @return true if a frequent backward extension exists
	 */
	private boolean checkBackwardExtension(int[] sidArray, int sidCount, int lastBufferPosition, boolean allowMerge) {

		// determine the itemset boundaries of the current pattern
		int itemsetCount = computeItemsetBoundaries(lastBufferPosition);
		// find items that are locally frequent within the support set
		findLocallyFrequentItems(sidArray, sidCount);

		for (int t = 0; t < localTouchedCount; t++) {
			int item = localTouchedItems[t];
			if (localItemCount[item] < minsuppAbsolute)
				continue;

			// (a) Merge: add item to one of the pattern's existing itemsets.
			if (allowMerge) {
				for (int ii = 0; ii < itemsetCount; ii++) {
					// Skip if item is already in this itemset (no duplicates allowed).
					boolean present = false;
					for (int p = itemsetStartBuf[ii]; p < itemsetEndBuf[ii]; p++) {
						if (patternBuffer[p] == item) {
							present = true;
							break;
						}
					}
					if (!present && hasSufficientSupport(sidArray, sidCount, itemsetCount, ii, item, -1))
						return true;
				}
			}

			// (b) Insert: add item as a new singleton itemset at each possible gap.
			for (int gap = 0; gap <= itemsetCount; gap++) {
				if (hasSufficientSupport(sidArray, sidCount, itemsetCount, -1, item, gap))
					return true;
			}
		}
		return false;
	}

	/**
	 * Computes the start and end positions of each itemset of the current pattern within {@link #patternBuffer}.
	 *
	 * @param lastBufferPosition last position of the pattern in patternBuffer
	 * @return the number of itemsets in the current pattern
	 */
	private int computeItemsetBoundaries(int lastBufferPosition) {
		int count = 0;
		int start = 0;
		// scan the pattern buffer and record the boundaries at each -1 separator
		for (int i = 0; i <= lastBufferPosition; i++) {
			if (patternBuffer[i] == -1) {
				itemsetStartBuf[count] = start;
				itemsetEndBuf[count] = i;
				count++;
				start = i + 1;
			}
		}
		// record the last itemset, which has no trailing -1
		itemsetStartBuf[count] = start;
		itemsetEndBuf[count] = lastBufferPosition + 1;
		count++;
		return count;
	}

	/**
	 * Counts, for each renamed item, the number of sequences of sidArray in which it occurs, storing
	 * the result in {@link #localItemCount}.
	 *
	 * @param sidArray array of sequence IDs forming the support set
	 * @param sidCount number of valid entries in sidArray
	 */
	private void findLocallyFrequentItems(int[] sidArray, int sidCount) {
		callStampCounter++;
		localTouchedCount = 0;
		List<int[]> sequences = sequenceDatabase.getSequences();

		for (int s = 0; s < sidCount; s++) {
			seqStampCounter++;
			int sid = sidArray[s];
			int[] sequence = sequences.get(sid);

			for (int i = 0; sequence[i] != -2; i++) {
				int token = sequence[i];
				if (token <= 0)
					continue; // skip -1 separators (multi-item db) and -2 (caught by loop cond)

				// Count each item at most once per sequence.
				if (itemSeqStamp[token] != seqStampCounter) {
					itemSeqStamp[token] = seqStampCounter;
					if (itemCallStamp[token] != callStampCounter) {
						itemCallStamp[token] = callStampCounter;
						localItemCount[token] = 0;
						localTouchedItems[localTouchedCount++] = token;
					}
					localItemCount[token]++;
				}
			}
		}
	}

	/**
	 * Checks whether the virtual extended pattern (current pattern plus one extra item, either merged
	 * into an existing itemset or inserted as a new singleton) has support at least minsuppAbsolute
	 * within sidArray.
	 *
	 * @param sidArray     support-set sequence IDs to test
	 * @param sidCount     number of valid entries in sidArray
	 * @param itemsetCount number of itemsets in the base (un-extended) pattern
	 * @param mergeIndex   index of the itemset to receive the merged item, or -1
	 * @param extraItem    the renamed item to add
	 * @param insertGap    gap position for the new singleton itemset, or -1
	 * @return true if the virtual extended pattern is frequent within sidArray
	 */
	private boolean hasSufficientSupport(int[] sidArray, int sidCount, int itemsetCount, int mergeIndex, int extraItem,
			int insertGap) {

		List<int[]> sequences = sequenceDatabase.getSequences();
		int support = 0;
		int remaining = sidCount;

		for (int s = 0; s < sidCount; s++) {
			int sid = sidArray[s];
			remaining--;
			int[] sequence = sequences.get(sid);

			// dispatch to the per-format containment test
			boolean matches = containsItemsetsWithMultipleItems
					? matchesExtendedPatternMultiItem(sequence, itemsetCount, mergeIndex, extraItem, insertGap)
					: matchesExtendedPatternSingleItem(sequence, itemsetCount, mergeIndex, extraItem, insertGap);

			if (matches) {
				support++;
				if (support >= minsuppAbsolute)
					return true;
			}
			// stop early if minsup can no longer be reached
			if (support + remaining < minsuppAbsolute)
				return false;
		}
		return false;
	}

	// -------------------------------------------------------------------------
	// Containment tests – single-item database
	// -------------------------------------------------------------------------

	/**
	 * Tests whether a sequence from a single-item-per-itemset database contains the virtual extended pattern.
	 *
	 * @param sequence     the full pruned renamed sequence (no -1 separators)
	 * @param itemsetCount number of itemsets in the base pattern
	 * @param mergeIndex   itemset index to merge extraItem into, or -1
	 * @param extraItem    the renamed extra item
	 * @param insertGap    gap position for the new singleton, or -1
	 * @return true if the sequence contains the virtual extended pattern
	 */
	private boolean matchesExtendedPatternSingleItem(int[] sequence, int itemsetCount, int mergeIndex, int extraItem,
			int insertGap) {

		// Virtual pattern has one extra itemset when a new singleton is inserted.
		int patternSize = itemsetCount + (insertGap >= 0 ? 1 : 0);
		int patternPos = 0;

		// Scan one token at a time; each token is one complete singleton itemset.
		for (int j = 0; sequence[j] != -2 && patternPos < patternSize; j++) {
			int token = sequence[j];
			// token is always > 0 here (single-item db has no -1 separators)
			if (matchesVirtualItemsetSingleItem(token, mergeIndex, extraItem, insertGap, patternPos)) {
				patternPos++;
			}
		}
		return patternPos == patternSize;
	}

	/**
	 * Checks whether a single sequence token satisfies the virtual-pattern itemset at a given position,
	 * in the single-item database context.
	 *
	 * @param token      the single item token at the current sequence position
	 * @param mergeIndex base-pattern itemset index receiving the merge, or -1
	 * @param extraItem  the renamed extra item
	 * @param insertGap  virtual-pattern position of the inserted singleton, or -1
	 * @param patternPos virtual-pattern itemset index being matched
	 * @return true if the token satisfies the virtual-pattern itemset at patternPos
	 */
	private boolean matchesVirtualItemsetSingleItem(int token, int mergeIndex, int extraItem, int insertGap,
			int patternPos) {

		// Case A: this virtual-pattern position is the newly inserted singleton.
		if (insertGap == patternPos)
			return token == extraItem;

		// Map virtual patternPos back to the real (base-pattern) itemset index.
		int realIndex = (insertGap >= 0 && patternPos > insertGap) ? patternPos - 1 : patternPos;

		// The base-pattern itemset at realIndex.
		// In the single-item database, every base-pattern itemset is a singleton
		// (itemsetEnd - itemsetStart == 1), so it requires exactly one item.
		int start = itemsetStartBuf[realIndex];
		int end = itemsetEndBuf[realIndex];

		if (realIndex == mergeIndex) {
			// Merge case: requires BOTH the base item AND extraItem in one itemset.
			// A single token can never satisfy two distinct items → always false.
			// (Caller uses allowMerge=false so this branch is not reachable in practice.)
			return false;
		}

		// Normal case: the base-pattern itemset is a singleton.
		// It matches iff the token equals that single required item.
		// (end - start should always be 1 here, but we iterate for safety.)
		for (int p = start; p < end; p++) {
			if (patternBuffer[p] != token)
				return false;
		}
		return true;
	}

	// -------------------------------------------------------------------------
	// Containment tests – multi-item database
	// -------------------------------------------------------------------------

	/**
	 * Tests whether a sequence from a multi-item-per-itemset database contains the virtual extended pattern.
	 *
	 * @param sequence     the full pruned renamed sequence (with -1 separators)
	 * @param itemsetCount number of itemsets in the base pattern
	 * @param mergeIndex   itemset index to merge extraItem into, or -1
	 * @param extraItem    the renamed extra item
	 * @param insertGap    gap position for the new singleton, or -1
	 * @return true if the sequence contains the virtual extended pattern
	 */
	private boolean matchesExtendedPatternMultiItem(int[] sequence, int itemsetCount, int mergeIndex, int extraItem,
			int insertGap) {

		int patternSize = itemsetCount + (insertGap >= 0 ? 1 : 0);
		int patternPos = 0;
		int j = 0;

		while (sequence[j] != -2 && patternPos < patternSize) {
			// j points to the start of the current sequence itemset.
			if (matchesVirtualItemsetMultiItem(sequence, j, mergeIndex, extraItem, insertGap, patternPos)) {
				patternPos++;
			}
			// Advance j past the current sequence itemset:
			// skip all positive item tokens belonging to this itemset.
			while (sequence[j] > 0)
				j++;
			// sequence[j] is now -1 or -2; skip the -1 separator if present.
			if (sequence[j] == -1)
				j++;
			// If it's -2, the outer while condition will terminate the loop.
		}
		return patternPos == patternSize;
	}

	/**
	 * Checks whether the sequence itemset starting at a given position satisfies the virtual-pattern
	 * itemset at a given position, in the multi-item database context.
	 *
	 * @param sequence        the full sequence array
	 * @param seqItemsetStart position where the current sequence itemset starts
	 * @param mergeIndex      base-pattern itemset index receiving the merge, or -1
	 * @param extraItem       the renamed extra item
	 * @param insertGap       virtual-pattern position of the inserted singleton, or -1
	 * @param patternPos      virtual-pattern itemset index being matched
	 * @return true if the sequence itemset satisfies the virtual-pattern itemset
	 */
	private boolean matchesVirtualItemsetMultiItem(int[] sequence, int seqItemsetStart, int mergeIndex, int extraItem,
			int insertGap, int patternPos) {

		// Case A: inserted singleton.
		if (insertGap == patternPos)
			return containsItemInSeqItemset(sequence, seqItemsetStart, extraItem);

		// Map virtual patternPos to real base-pattern itemset index.
		int realIndex = (insertGap >= 0 && patternPos > insertGap) ? patternPos - 1 : patternPos;

		int start = itemsetStartBuf[realIndex];
		int end = itemsetEndBuf[realIndex];
		for (int p = start; p < end; p++) {
			if (!containsItemInSeqItemset(sequence, seqItemsetStart, patternBuffer[p]))
				return false;
		}

		// Case B: merged extra item must also be present.
		if (realIndex == mergeIndex)
			return containsItemInSeqItemset(sequence, seqItemsetStart, extraItem);

		return true;
	}

	/**
	 * Checks whether the sequence itemset starting at the given position contains the given item.
	 *
	 * @param sequence the sequence array
	 * @param start    start position of the itemset in the sequence
	 * @param item     the item to look for
	 * @return true if the item is found in the itemset
	 */
	private boolean containsItemInSeqItemset(int[] sequence, int start, int item) {
		for (int k = start; sequence[k] > 0; k++) {
			if (sequence[k] == item)
				return true;
		}
		return false;
	}

	// =========================================================================
	// SHARED PREFIXSPAN-STYLE ROUTINES
	// =========================================================================

	/**
	 * Scans the database once to build, for each item, the list of sequences containing it, and
	 * detects whether the database contains itemsets with more than one item.
	 *
	 * @return a map from each item to the list of sequence IDs containing it
	 */
	private Map<Integer, List<Integer>> findSequencesContainingItems() {
		Map<Integer, List<Integer>> mapSequenceID = new java.util.HashMap<>();
		List<int[]> sequences = sequenceDatabase.getSequences();
		for (int i = 0, dbSize = sequences.size(); i < dbSize; i++) {
			int[] sequence = sequences.get(i);
			int itemCountInCurrentItemset = 0;
			for (int j = 0, seqLen = sequence.length; j < seqLen; j++) {
				int token = sequence[j];
				if (token > 0) {
					// register this sequence for the item, avoiding duplicates
					List<Integer> seqIDs = mapSequenceID.get(token);
					if (seqIDs == null) {
						seqIDs = new java.util.ArrayList<>();
						mapSequenceID.put(token, seqIDs);
					}
					int listSize = seqIDs.size();
					if (listSize == 0 || seqIDs.get(listSize - 1) != i)
						seqIDs.add(i);
					itemCountInCurrentItemset++;
					if (itemCountInCurrentItemset > 1)
						containsItemsetsWithMultipleItems = true;
				} else if (token == -1) {
					// a new itemset starts, reset the item count
					itemCountInCurrentItemset = 0;
				}
			}
		}
		return mapSequenceID;
	}

	/**
	 * Builds the projected database for a length-1 extension in a single-item-per-itemset database.
	 *
	 * @param item        the renamed item used as the projection point
	 * @param sequenceIDs sequence IDs containing the item
	 * @param outSeqIDs   output array receiving the projected sequence IDs
	 * @param outIndexes  output array receiving the projected start indexes
	 * @return the number of entries written to the projected database
	 */
	private int buildProjectedDatabaseSingleItems(int item, List<Integer> sequenceIDs, int[] outSeqIDs,
			int[] outIndexes) {
		List<int[]> sequences = sequenceDatabase.getSequences();
		int projSize = 0;
		for (int idx = 0, sz = sequenceIDs.size(); idx < sz; idx++) {
			int sid = sequenceIDs.get(idx);
			int[] seq = sequences.get(sid);
			// find the first occurrence of the item and project from the next token
			for (int j = 0; seq[j] != -2; j++) {
				if (seq[j] == item) {
					if (seq[j + 1] != -2) {
						outSeqIDs[projSize] = sid;
						outIndexes[projSize] = j + 1;
						projSize++;
					}
					break;
				}
			}
		}
		return projSize;
	}

	/**
	 * Builds the projected database for a length-1 extension in a multi-item-per-itemset database.
	 *
	 * @param item        the renamed item used as the projection point
	 * @param sequenceIDs sequence IDs containing the item
	 * @param outSeqIDs   output array receiving the projected sequence IDs
	 * @param outIndexes  output array receiving the projected start indexes
	 * @return the number of entries written to the projected database
	 */
	private int buildProjectedDatabaseFirstTimeMultipleItems(int item, List<Integer> sequenceIDs, int[] outSeqIDs,
			int[] outIndexes) {
		List<int[]> sequences = sequenceDatabase.getSequences();
		int projSize = 0;
		for (int idx = 0, sz = sequenceIDs.size(); idx < sz; idx++) {
			int sid = sequenceIDs.get(idx);
			int[] seq = sequences.get(sid);
			// find the first occurrence of the item and project from the next token
			for (int j = 0; seq[j] != -2; j++) {
				if (seq[j] == item) {
					boolean isEnd = seq[j + 1] == -1 && seq[j + 2] == -2;
					if (!isEnd) {
						outSeqIDs[projSize] = sid;
						outIndexes[projSize] = j + 1;
						projSize++;
					}
					break;
				}
			}
		}
		return projSize;
	}

	/**
	 * Counts, for each item, the sequences of the projected database (single-item variant) in which
	 * it occurs.
	 *
	 * @param projSeqIDs  sequence IDs of the projected database
	 * @param projIndexes start indexes into each sequence of the projected database
	 * @param projSize    number of entries in the projected database
	 * @param outArray    array receiving the counted items and their projections
	 */
	protected void findAllFrequentPairsSingleItems(int[] projSeqIDs, int[] projIndexes, int projSize,
			AlgoPrefixSpan.ItemPairArray outArray) {
		List<int[]> dbSequences = sequenceDatabase.getSequences();
		outArray.touchedCount = 0;
		for (int seqIdx = 0; seqIdx < projSize; seqIdx++) {
			int sid = projSeqIDs[seqIdx];
			int[] seq = dbSequences.get(sid);
			// scan the projected part of the sequence and count each item once
			for (int i = projIndexes[seqIdx]; seq[i] != -2; i++) {
				int token = seq[i];
				if (token > 0) {
					AlgoPrefixSpan.PairData pd = outArray.dataByItem[token];
					if (pd == null) {
						pd = new AlgoPrefixSpan.PairData();
						outArray.dataByItem[token] = pd;
					}
					if (pd.projSize == 0)
						outArray.touchedItems[outArray.touchedCount++] = token;
					if (!pd.lastSeqIDEquals(sid))
						pd.add(sid, i + 1);
				}
			}
		}
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Counts, for each item, the sequences of the projected database (multi-item variant) in which it
	 * occurs, separately for i-extensions (postfix) and s-extensions.
	 *
	 * @param projSeqIDs         sequence IDs of the projected database
	 * @param projIndexes        start indexes into each sequence of the projected database
	 * @param projSize           number of entries in the projected database
	 * @param lastBufferPosition last filled position of the current pattern in {@link #patternBuffer}
	 * @param mapsPairs          arrays receiving the counted i-extensions and s-extensions
	 */
	protected void findAllFrequentPairs(int[] projSeqIDs, int[] projIndexes, int projSize, int lastBufferPosition,
			AlgoPrefixSpan.ArrayFrequentPairs mapsPairs) {

		// locate the start of the last itemset of the current pattern
		int firstPosOfLastItemset = lastBufferPosition;
		while (lastBufferPosition > 0) {
			firstPosOfLastItemset--;
			if (firstPosOfLastItemset < 0 || patternBuffer[firstPosOfLastItemset] == -1) {
				firstPosOfLastItemset++;
				break;
			}
		}

		List<int[]> dbSequences = sequenceDatabase.getSequences();
		AlgoPrefixSpan.ItemPairArray pairsArr = mapsPairs.pairs;
		AlgoPrefixSpan.ItemPairArray postfixArr = mapsPairs.pairsInPostfix;
		pairsArr.touchedCount = 0;
		postfixArr.touchedCount = 0;

		for (int seqIdx = 0; seqIdx < projSize; seqIdx++) {
			int sid = projSeqIDs[seqIdx];
			int[] seq = dbSequences.get(sid);
			int posToMatch = firstPosOfLastItemset;
			int prevItem = seq[projIndexes[seqIdx] - 1];
			boolean isPostfix = (prevItem != -1);
			boolean isFirstItemset = true;

			for (int i = projIndexes[seqIdx]; seq[i] != -2; i++) {
				int token = seq[i];
				if (token > 0) {
					// count this token as an i-extension or s-extension candidate
					AlgoPrefixSpan.ItemPairArray target = isPostfix ? postfixArr : pairsArr;
					AlgoPrefixSpan.PairData pd = target.dataByItem[token];
					if (pd == null) {
						pd = new AlgoPrefixSpan.PairData();
						target.dataByItem[token] = pd;
					}
					if (pd.projSize == 0)
						target.touchedItems[target.touchedCount++] = token;
					if (!pd.lastSeqIDEquals(sid))
						pd.add(sid, i + 1);

					// a postfix item also counts as an s-extension candidate
					if (isPostfix && !isFirstItemset) {
						AlgoPrefixSpan.PairData pd2 = pairsArr.dataByItem[token];
						if (pd2 == null) {
							pd2 = new AlgoPrefixSpan.PairData();
							pairsArr.dataByItem[token] = pd2;
						}
						if (pd2.projSize == 0)
							pairsArr.touchedItems[pairsArr.touchedCount++] = token;
						if (!pd2.lastSeqIDEquals(sid))
							pd2.add(sid, i + 1);
					}

					// track progress matching the last itemset of the pattern
					if (!isPostfix && patternBuffer[posToMatch] == token) {
						posToMatch++;
						if (posToMatch > lastBufferPosition)
							isPostfix = true;
					}
				} else if (token == -1) {
					// a new sequence itemset starts, reset the match state
					isFirstItemset = false;
					isPostfix = false;
					posToMatch = firstPosOfLastItemset;
				}
			}
		}
		MemoryLogger.getInstance().checkMemory();
	}

	// =========================================================================
	// SAVE PATTERNS
	// =========================================================================

	/**
	 * Saves a length-1 maximal pattern, either to the output file or in memory.
	 *
	 * @param item        the original (un-renamed) item forming the pattern
	 * @param support     support count of the pattern
	 * @param sequenceIDs sequence IDs of the pattern's support set
	 * @throws IOException if an error occurs while writing the output
	 */
	private void savePattern(int item, int support, List<Integer> sequenceIDs) throws IOException {
		patternCount++;
		if (writer != null) {
			// write the pattern directly to the output file
			StringBuilder r = new StringBuilder();
			r.append(item).append(" -1 #SUP: ").append(support);
			if (showSequenceIdentifiers) {
				r.append(" #SID: ");
				for (int i = 0, sz = sequenceIDs.size(); i < sz; i++)
					r.append(sequenceIDs.get(i)).append(" ");
			}
			writer.write(r.toString());
			writer.newLine();
		} else {
			// keep the pattern in memory
			SequentialPattern pattern = new SequentialPattern();
			pattern.addItemset(new Itemset(item));
			pattern.setSequenceIDs(sequenceIDs);
			patterns.addSequence(pattern, 1);
		}
	}

	/**
	 * Saves a maximal pattern of length greater than one, stored in {@link #patternBuffer}, either to
	 * the output file or in memory.
	 *
	 * @param lastBufferPosition last filled position of the pattern in patternBuffer
	 * @param projSeqIDs         sequence IDs of the pattern's support set
	 * @param projSize           number of valid entries in projSeqIDs
	 * @throws IOException if an error occurs while writing the output
	 */
	private void savePattern(int lastBufferPosition, int[] projSeqIDs, int projSize) throws IOException {
		patternCount++;
		if (writer != null) {
			// build and write the pattern's textual representation
			StringBuilder r = new StringBuilder((lastBufferPosition + 2) * 8 + 20);
			for (int i = 0; i <= lastBufferPosition; i++) {
				int token = patternBuffer[i];
				r.append(token == -1 ? -1 : itemNameConverter.toOldName(token)).append(" ");
			}
			if (patternBuffer[lastBufferPosition] != -1)
				r.append("-1 ");
			r.append("#SUP: ").append(projSize);
			if (showSequenceIdentifiers) {
				r.append(" #SID: ");
				for (int i = 0; i < projSize; i++)
					r.append(projSeqIDs[i]).append(" ");
			}
			writer.write(r.toString());
			writer.newLine();
		} else {
			// rebuild the pattern as a list of itemsets and keep it in memory
			SequentialPattern pattern = new SequentialPattern();
			int itemsetCount = 0;
			Itemset currentItemset = new Itemset();
			for (int i = 0; i <= lastBufferPosition; i++) {
				int token = patternBuffer[i];
				if (token > 0)
					currentItemset.addItem(itemNameConverter.toOldName(token));
				else if (token == -1) {
					pattern.addItemset(currentItemset);
					currentItemset = new Itemset();
					itemsetCount++;
				}
			}
			pattern.addItemset(currentItemset);
			itemsetCount++;
			List<Integer> sids = new java.util.ArrayList<>(projSize);
			for (int i = 0; i < projSize; i++)
				sids.add(projSeqIDs[i]);
			pattern.setSequenceIDs(sids);
			patterns.addSequence(pattern, itemsetCount);
		}
	}

	// =========================================================================
	// STATISTICS / CONFIGURATION
	// =========================================================================

	/**
	 * Prints statistics about the last execution of the algorithm to the standard output.
	 */
	public void printStatistics() {
		StringBuilder r = new StringBuilder(200);
		r.append("=============  MAXSP 2.67 - STATISTICS =============\n");
		r.append("Total time ~ ").append(totalTime).append(" ms\n");
		r.append(" Maximal sequential patterns count : ").append(patternCount).append("\n");
		r.append(" Max memory (mb) : ").append(MemoryLogger.getInstance().getMaxMemory()).append("\n");
		r.append(" minsup = ").append(minsuppAbsolute).append(" sequences.\n");
		r.append("===================================================\n");
		if (patterns != null)
			patterns.printFrequentPatterns(sequenceCount, showSequenceIdentifiers);
		System.out.println(r.toString());
	}

	/**
	 * Returns the maximum pattern length allowed.
	 *
	 * @return the maximum number of itemsets allowed in a pattern
	 */
	public int getMaximumPatternLength() {
		return maximumPatternLength;
	}

	/**
	 * Sets the maximum pattern length allowed.
	 *
	 * @param maximumPatternLength the maximum number of itemsets allowed in a pattern
	 */
	public void setMaximumPatternLength(int maximumPatternLength) {
		this.maximumPatternLength = maximumPatternLength;
	}

	/**
	 * Sets whether the sequence IDs of each pattern's support set should be included in the output.
	 *
	 * @param showSequenceIdentifiers true to show sequence identifiers
	 */
	public void setShowSequenceIdentifiers(boolean showSequenceIdentifiers) {
		this.showSequenceIdentifiers = showSequenceIdentifiers;
	}
}