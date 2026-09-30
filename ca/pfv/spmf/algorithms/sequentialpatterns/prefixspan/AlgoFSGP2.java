package ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.ItemNameConverter;
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

/***
 * This is a highly optimized re-implementation of the FSGP algorithm (Yi et
 * al., 2011-2012) for mining frequent sequential GENERATOR patterns.
 * 
 * FSGP is essentially PrefixSpan plus (1) a search-space pruning rule and (2) a
 * final filtering step, both based on the "no strictly-smaller sub-pattern with
 * the exact same support AND the exact same future" definition of a generator
 *
 *
 * Copyright (c) 2008-2026 Philippe Fournier-Viger
 */
public class AlgoFSGP2 {

	/** the time at which the algorithm started */
	long startTime;
	/** the time at which the algorithm finished */
	long endTime;

	/** number of prefixes pruned by the generator-pruning rule */
	int prefixPrunedCount = 0;

	/** absolute minimum support */
	private int minsuppAbsolute;

	/** maximum pattern length in terms of item count */
	private int maximumPatternLength = 1000;

	/** if true, the search-time generator pruning rule is used */
	private boolean performPruning = true;

	/** if true, sequence identifiers of each pattern will be shown */
	boolean showSequenceIdentifiers = false;

	/** size of the buffer used to store the pattern currently being mined */
	final int BUFFERS_SIZE = 2000;

	/** reusable buffer for the pattern (prefix) currently being extended */
	private int[] patternBuffer = new int[BUFFERS_SIZE];

	/** number of sequences in the (pruned) database */
	private int sequenceCount = 0;

	/** the sequence database, stored as pruned/renamed int[] token arrays */
	private SequenceDatabase sequenceDatabase;

	/** cached reference to sequenceDatabase.getSequences(), used in the hot path */
	private List<int[]> dbSequencesList;

	/** true if some itemset in the database contains more than one item */
	private boolean containsItemsetsWithMultipleItems = false;

	/** reusable projected-database sequence ids buffers, indexed by recursion depth */
	private int[][] projSeqIDsBuffer;
	/** reusable projected-database start indexes buffers, indexed by recursion depth */
	private int[][] projIndexesBuffer;

	/** reusable candidate structures for the "single item per itemset" case */
	private ItemPairArray[] singleItemsArrays;

	/** reusable candidate structures for the "multiple items per itemset" case */
	private ArrayFrequentPairs[] frequentPairsArrays;

	/** renames frequent items to consecutive integers starting at 1 */
	private ItemNameConverter itemNameConverter;

	/** number of distinct frequent (renamed) items */
	private int frequentItemCount;

	/** candidate generator patterns found so far, indexed by their number of items */
	private List<List<FSGPPattern>> levels;

	/** the final list of generator patterns, computed at the end of the search */
	private List<FSGPPattern> generators;

	/**
	 * Default constructor
	 */
	public AlgoFSGP2() {
	}

	// =====================================================================
	// ============================ PUBLIC API ============================
	// =====================================================================

	/**
	 * Run the algorithm using a minimum support expressed as a relative value.
	 * 
	 * @param inputFile      a sequence database file
	 * @param minsupRelative the minimum support as a value in [0,1]
	 * @param performPruning whether the search-time generator pruning rule should
	 *                       be used
	 * @return the list of generator sequential patterns found
	 * @throws IOException if an error occurs while reading the file
	 */
	public List<FSGPPattern> runAlgorithm(String inputFile, double minsupRelative, boolean performPruning)
			throws IOException {
		this.performPruning = performPruning;
		prefixPrunedCount = 0;
		MemoryLogger.getInstance().reset();
		startTime = System.currentTimeMillis();

		// load the database and compute the absolute minimum support
		sequenceDatabase = new SequenceDatabase();
		sequenceDatabase.loadFile(inputFile);
		sequenceCount = sequenceDatabase.size();

		this.minsuppAbsolute = (int) Math.ceil(minsupRelative * sequenceCount);
		if (this.minsuppAbsolute == 0) {
			this.minsuppAbsolute = 1;
		}

		fsgp();

		endTime = System.currentTimeMillis();
		return generators;
	}

	/**
	 * Run the algorithm using a minimum support expressed as an absolute count.
	 * 
	 * @param inputFile      a sequence database file
	 * @param minsup         the minimum support as an integer
	 * @param performPruning whether the search-time generator pruning rule should
	 *                       be used
	 * @return the list of generator sequential patterns found
	 * @throws IOException if an error occurs while reading the file
	 */
	public List<FSGPPattern> runAlgorithm(String inputFile, int minsup, boolean performPruning) throws IOException {
		this.performPruning = performPruning;
		prefixPrunedCount = 0;
		MemoryLogger.getInstance().reset();
		startTime = System.currentTimeMillis();

		this.minsuppAbsolute = minsup;

		// load the database
		sequenceDatabase = new SequenceDatabase();
		sequenceDatabase.loadFile(inputFile);
		sequenceCount = sequenceDatabase.size();

		fsgp();

		endTime = System.currentTimeMillis();
		return generators;
	}

	// =====================================================================
	// =========================== MAIN ALGORITHM =========================
	// =====================================================================

	/**
	 * Main method of the algorithm: mines all frequent single items, then
	 * recursively extends each of them and finally filters non-generator
	 * patterns.
	 * 
	 * @throws IOException if an I/O error occurs
	 */
	private void fsgp() throws IOException {

		// Allocate the depth-indexed reusable buffers. Allocated lazily per depth
		// (see ensureDepthBuffers) since maximumPatternLength is often much larger
		// than the depth actually reached while mining.
		projSeqIDsBuffer = new int[maximumPatternLength + 1][];
		projIndexesBuffer = new int[maximumPatternLength + 1][];
		singleItemsArrays = new ItemPairArray[maximumPatternLength + 1];
		frequentPairsArrays = new ArrayFrequentPairs[maximumPatternLength + 1];

		levels = new ArrayList<List<FSGPPattern>>();
		ensureLevel(0);

		// ============== CALCULATE FREQUENCY OF SINGLE ITEMS =============
		Map<Integer, List<Integer>> mapSequenceID = findSequencesContainingItems();

		// ============== RENAME FREQUENT ITEMS =============
		frequentItemCount = 0;
		for (List<Integer> seqList : mapSequenceID.values()) {
			if (seqList.size() >= minsuppAbsolute) {
				frequentItemCount++;
			}
		}
		itemNameConverter = new ItemNameConverter(frequentItemCount);
		for (Entry<Integer, List<Integer>> entry : mapSequenceID.entrySet()) {
			if (entry.getValue().size() >= minsuppAbsolute) {
				itemNameConverter.assignNewName(entry.getKey());
			}
		}

		// ====== Remove infrequent items and rename frequent ones =============
		if (containsItemsetsWithMultipleItems) {
			pruneAndRenameSequencesMultipleItems();
		} else {
			pruneAndRenameSequencesSingleItems();
		}
		dbSequencesList = sequenceDatabase.getSequences();

		// ============= EXPLORE EACH PROJECTED DATABASE ================
		for (Entry<Integer, List<Integer>> entry : mapSequenceID.entrySet()) {
			List<Integer> sequenceIDs = entry.getValue();
			int support = sequenceIDs.size();
			if (support >= minsuppAbsolute) {
				int item = entry.getKey();
				int newItem = itemNameConverter.toNewName(item);

				// The single-item pattern is always kept: there is nothing smaller than it
				// to compare against (the "same support as the empty sequence" case is
				// handled uniformly for ALL levels at the very end, in filterNonGenerator).
				int[] seqIDsArray = toIntArray(sequenceIDs);
				ensureLevel(1);
				levels.get(1).add(new FSGPPattern(new int[] { newItem }, 1, 1, seqIDsArray));

				if (maximumPatternLength > 1) {
					patternBuffer[0] = newItem;
					ensureDepthBuffers(1);

					// recurse using the appropriate strategy depending on the database shape
					if (containsItemsetsWithMultipleItems) {
						int projSize = buildProjectedDatabaseFirstTimeMultipleItems(newItem, sequenceIDs,
								projSeqIDsBuffer[1], projIndexesBuffer[1]);
						recursion(patternBuffer, projSeqIDsBuffer[1], projIndexesBuffer[1], projSize, 2, 0, 1, 1);
					} else {
						int projSize = buildProjectedDatabaseSingleItems(newItem, sequenceIDs, projSeqIDsBuffer[1],
								projIndexesBuffer[1]);
						recursionSingleItems(projSeqIDsBuffer[1], projIndexesBuffer[1], projSize, 2, 0, 1);
					}
				}
			}
		}

		// ============= FINAL GENERATOR FILTERING ================
		filterNonGenerator();

		// release everything that is no longer needed
		projSeqIDsBuffer = null;
		projIndexesBuffer = null;
		singleItemsArrays = null;
		frequentPairsArrays = null;
		sequenceDatabase = null;
		dbSequencesList = null;
	}

	/**
	 * Make sure the {@code levels} list has an (possibly empty) entry for the
	 * given level.
	 * 
	 * @param level the level (number of items) that must exist
	 */
	private void ensureLevel(int level) {
		while (levels.size() <= level) {
			levels.add(new ArrayList<FSGPPattern>());
		}
	}

	/**
	 * Lazily allocate the reusable buffers for the given recursion depth. Only the
	 * structure actually needed (single-item or multi-item candidate counting) is
	 * allocated, since {@link #containsItemsetsWithMultipleItems} is known and
	 * fixed for the whole run.
	 * 
	 * @param depth the recursion depth for which buffers must be available
	 */
	private void ensureDepthBuffers(int depth) {
		if (projSeqIDsBuffer[depth] == null) {
			projSeqIDsBuffer[depth] = new int[sequenceCount];
			projIndexesBuffer[depth] = new int[sequenceCount];
		}
		if (containsItemsetsWithMultipleItems) {
			if (frequentPairsArrays[depth] == null) {
				frequentPairsArrays[depth] = new ArrayFrequentPairs(frequentItemCount);
			}
		} else {
			if (singleItemsArrays[depth] == null) {
				singleItemsArrays[depth] = new ItemPairArray(frequentItemCount);
			}
		}
	}

	// =====================================================================
	// ================= RECURSION - SINGLE ITEM PER ITEMSET ==============
	// =====================================================================

	/**
	 * Recursively grow a pattern, for the optimized case where every itemset of the
	 * database contains a single item (patterns then only grow via S-extensions).
	 * 
	 * @param projSeqIDs         sequence ids of the current projected database
	 * @param projIndexes        start index, in each sequence, of the projection
	 * @param projSize           number of entries in the projected database
	 * @param k                  number of items that the extended pattern will have
	 * @param lastBufferPosition position of the last token of the current prefix in {@code patternBuffer}
	 * @param depth              current recursion depth (used to pick reusable buffers)
	 * @throws IOException if an I/O error occurs
	 */
	private void recursionSingleItems(int[] projSeqIDs, int[] projIndexes, int projSize, int k, int lastBufferPosition,
			int depth) throws IOException {

		ensureDepthBuffers(depth);

		// count candidate extension items in the current projected database
		ItemPairArray itemsPseudoSequences = singleItemsArrays[depth];
		findAllFrequentPairsSingleItems(projSeqIDs, projIndexes, projSize, itemsPseudoSequences);

		int nextDepth = depth + 1;
		ensureDepthBuffers(nextDepth);
		ensureLevel(k);
		List<FSGPPattern> levelBelow = levels.get(k - 1);
		boolean canPrune = performPruning && !levelBelow.isEmpty();

		for (int idx = 0; idx < itemsPseudoSequences.touchedCount; idx++) {
			int item = itemsPseudoSequences.touchedItems[idx];
			PairData pairData = itemsPseudoSequences.dataByItem[item];

			if (pairData.projSize >= minsuppAbsolute) {

				// append the item as a new itemset (S-extension)
				patternBuffer[lastBufferPosition + 1] = -1;
				patternBuffer[lastBufferPosition + 2] = item;
				int newBufferPosition = lastBufferPosition + 2;
				int newLen = newBufferPosition + 1;

				boolean keep = true;
				if (canPrune) {
					keep = pruningCheckSingleItems(patternBuffer, newLen, levelBelow, pairData.seqIDs,
							pairData.projSize);
				}

				if (keep) {
					// store the new pattern and recurse further
					int[] patternCopy = Arrays.copyOfRange(patternBuffer, 0, newLen);
					int[] seqIDsCopy = Arrays.copyOfRange(pairData.seqIDs, 0, pairData.projSize);
					levels.get(k).add(new FSGPPattern(patternCopy, k, k, seqIDsCopy));

					System.arraycopy(pairData.seqIDs, 0, projSeqIDsBuffer[nextDepth], 0, pairData.projSize);
					System.arraycopy(pairData.indexes, 0, projIndexesBuffer[nextDepth], 0, pairData.projSize);

					if (k < maximumPatternLength) {
						recursionSingleItems(projSeqIDsBuffer[nextDepth], projIndexesBuffer[nextDepth],
								pairData.projSize, k + 1, newBufferPosition, nextDepth);
					}
				} else {
					prefixPrunedCount++;
				}
			}
		}

		// reset the reusable candidate structure for reuse at this depth
		for (int idx = 0; idx < itemsPseudoSequences.touchedCount; idx++) {
			itemsPseudoSequences.dataByItem[itemsPseudoSequences.touchedItems[idx]].reset();
		}
	}

	// =====================================================================
	// ================ RECURSION - MULTIPLE ITEMS PER ITEMSET ============
	// =====================================================================

	/**
	 * Recursively grow a pattern, for the general case where itemsets of the
	 * database may contain more than one item (patterns can grow via both
	 * I-extensions and S-extensions).
	 * 
	 * @param patternBuffer       the buffer holding the current prefix
	 * @param projSeqIDs          sequence ids of the current projected database
	 * @param projIndexes         start index, in each sequence, of the projection
	 * @param projSize            number of entries in the projected database
	 * @param k                   number of items that the extended pattern will have
	 * @param lastBufferPosition  position of the last token of the current prefix in {@code patternBuffer}
	 * @param depth               current recursion depth (used to pick reusable buffers)
	 * @param itemsetCount        number of itemsets in the current prefix
	 * @throws IOException if an I/O error occurs
	 */
	private void recursion(int[] patternBuffer, int[] projSeqIDs, int[] projIndexes, int projSize, int k,
			int lastBufferPosition, int depth, int itemsetCount) throws IOException {

		ensureDepthBuffers(depth);

		// count candidate I-extension and S-extension items
		ArrayFrequentPairs mapsPairs = frequentPairsArrays[depth];
		findAllFrequentPairs(projSeqIDs, projIndexes, projSize, lastBufferPosition, mapsPairs);

		int nextDepth = depth + 1;
		ensureDepthBuffers(nextDepth);
		ensureLevel(k);
		List<FSGPPattern> levelBelow = levels.get(k - 1);
		boolean canPrune = performPruning && !levelBelow.isEmpty();

		// I-extensions: items found within the "cut" (postfix) itemset are appended
		// to the LAST itemset of the current pattern (no new itemset separator).
		ItemPairArray postfixArr = mapsPairs.pairsInPostfix;
		for (int idx = 0; idx < postfixArr.touchedCount; idx++) {
			int item = postfixArr.touchedItems[idx];
			PairData pairData = postfixArr.dataByItem[item];
			if (pairData.projSize >= minsuppAbsolute) {
				int newBufferPosition = lastBufferPosition + 1;
				patternBuffer[newBufferPosition] = item;
				processCandidate(patternBuffer, newBufferPosition, k, itemsetCount, pairData, nextDepth, depth,
						levelBelow, canPrune);
			}
		}

		// S-extensions: items found NOT in the postfix are appended as a new itemset.
		ItemPairArray pairsArr = mapsPairs.pairs;
		for (int idx = 0; idx < pairsArr.touchedCount; idx++) {
			int item = pairsArr.touchedItems[idx];
			PairData pairData = pairsArr.dataByItem[item];
			if (pairData.projSize >= minsuppAbsolute) {
				int newBufferPosition = lastBufferPosition + 1;
				patternBuffer[newBufferPosition] = -1;
				newBufferPosition++;
				patternBuffer[newBufferPosition] = item;
				processCandidate(patternBuffer, newBufferPosition, k, itemsetCount + 1, pairData, nextDepth, depth,
						levelBelow, canPrune);
			}
		}

		// reset the reusable candidate structures for reuse at this depth
		for (int idx = 0; idx < postfixArr.touchedCount; idx++) {
			postfixArr.dataByItem[postfixArr.touchedItems[idx]].reset();
		}
		for (int idx = 0; idx < pairsArr.touchedCount; idx++) {
			pairsArr.dataByItem[pairsArr.touchedItems[idx]].reset();
		}
	}

	/**
	 * Shared logic for handling one candidate extension found during
	 * {@link #recursion}: run the pruning check, store the pattern if it survives,
	 * and recurse.
	 * 
	 * @param patternBuffer      the buffer holding the current pattern
	 * @param newBufferPosition  position of the last token of the new pattern
	 * @param k                  number of items of the new pattern
	 * @param newItemsetCount    number of itemsets of the new pattern
	 * @param pairData           projected database of the candidate item
	 * @param nextDepth          recursion depth to use for the recursive call
	 * @param depth              current recursion depth
	 * @param levelBelow         patterns having one item less than the new pattern
	 * @param canPrune           true if the pruning check should be applied
	 * @throws IOException if an I/O error occurs
	 */
	private void processCandidate(int[] patternBuffer, int newBufferPosition, int k, int newItemsetCount,
			PairData pairData, int nextDepth, int depth, List<FSGPPattern> levelBelow, boolean canPrune)
			throws IOException {

		int newLen = newBufferPosition + 1;

		boolean keep = true;
		if (canPrune) {
			keep = pruningCheckMultiItems(patternBuffer, newLen, levelBelow, pairData.seqIDs, pairData.projSize);
		}

		if (keep) {
			// store the new pattern and recurse further
			int[] patternCopy = Arrays.copyOfRange(patternBuffer, 0, newLen);
			int[] seqIDsCopy = Arrays.copyOfRange(pairData.seqIDs, 0, pairData.projSize);
			levels.get(k).add(new FSGPPattern(patternCopy, k, newItemsetCount, seqIDsCopy));

			System.arraycopy(pairData.seqIDs, 0, projSeqIDsBuffer[nextDepth], 0, pairData.projSize);
			System.arraycopy(pairData.indexes, 0, projIndexesBuffer[nextDepth], 0, pairData.projSize);

			if (k < maximumPatternLength) {
				recursion(patternBuffer, projSeqIDsBuffer[nextDepth], projIndexesBuffer[nextDepth], pairData.projSize,
						k + 1, newBufferPosition, nextDepth, newItemsetCount);
			}
		} else {
			prefixPrunedCount++;
		}
	}

	// =====================================================================
	// ======================= GENERATOR PRUNING CHECKS ====================
	// =====================================================================

	/**
	 * Check the FSGP generator-pruning rule for the "single item per itemset" case:
	 * a pattern can be pruned if there is a strictly smaller sub-pattern (one level
	 * below) with the exact same support that also has the exact same "future" in
	 * every sequence where the new pattern occurs.
	 * 
	 * @param newPattern the new pattern's token buffer
	 * @param newLen     length of the new pattern in the buffer
	 * @param levelBelow patterns having one item less than the new pattern
	 * @param seqIDs     sequence ids where the new pattern occurs
	 * @param seqIDsLen  number of valid entries in {@code seqIDs}
	 * @return true if the pattern should be KEPT (passed the check)
	 */
	private boolean pruningCheckSingleItems(int[] newPattern, int newLen, List<FSGPPattern> levelBelow, int[] seqIDs,
			int seqIDsLen) {
		outer: for (int pi = 0, size = levelBelow.size(); pi < size; pi++) {
			FSGPPattern pattern2 = levelBelow.get(pi);
			if (pattern2.support == seqIDsLen
					&& strictlyContainsSingleItems(newPattern, newLen, pattern2.items, pattern2.items.length)) {
				// compare the "future" (match end position) in every sequence
				for (int idx = 0; idx < seqIDsLen; idx++) {
					int[] seq = dbSequencesList.get(seqIDs[idx]);
					int pos1 = findMatchEndPositionIndexSingleItems(seq, newPattern, newLen);
					int pos2 = findMatchEndPositionIndexSingleItems(seq, pattern2.items, pattern2.items.length);
					if (pos1 == -1 || pos1 != pos2) {
						continue outer;
					}
				}
				return false; // same support and same future everywhere : not a generator
			}
		}
		return true;
	}

	/**
	 * Same as {@link #pruningCheckSingleItems} but for the general case where
	 * itemsets may contain more than one item.
	 * 
	 * @param newPattern the new pattern's token buffer
	 * @param newLen     length of the new pattern in the buffer
	 * @param levelBelow patterns having one item less than the new pattern
	 * @param seqIDs     sequence ids where the new pattern occurs
	 * @param seqIDsLen  number of valid entries in {@code seqIDs}
	 * @return true if the pattern should be KEPT (passed the check)
	 */
	private boolean pruningCheckMultiItems(int[] newPattern, int newLen, List<FSGPPattern> levelBelow, int[] seqIDs,
			int seqIDsLen) {
		outer: for (int pi = 0, size = levelBelow.size(); pi < size; pi++) {
			FSGPPattern pattern2 = levelBelow.get(pi);
			if (pattern2.support == seqIDsLen
					&& strictlyContains(newPattern, newLen, pattern2.items, pattern2.items.length)) {
				// compare the "future" (match end position) in every sequence
				for (int idx = 0; idx < seqIDsLen; idx++) {
					int[] seq = dbSequencesList.get(seqIDs[idx]);
					int pos1 = findMatchEndPositionIndex(seq, newPattern, newLen);
					int pos2 = findMatchEndPositionIndex(seq, pattern2.items, pattern2.items.length);
					if (pos1 == -1 || pos1 != pos2) {
						continue outer;
					}
				}
				return false;
			}
		}
		return true;
	}

	/**
	 * Find the absolute token index, in {@code sequence}, of the last item matched
	 * by {@code pattern} (using leftmost/greedy matching), for the "single item per
	 * itemset" case. Since every itemset has one item, matching reduces to plain
	 * (leftmost) subsequence matching; -1 separators in the pattern are simply
	 * skipped, and -1 separators in the sequence are automatically skipped by the
	 * search-for-next-occurrence loop.
	 * 
	 * @param sequence   the sequence to match against
	 * @param pattern    the pattern's token buffer
	 * @param patternLen length of the pattern in the buffer
	 * @return the absolute index in {@code sequence} of the last matched item, or
	 *         -1 if the pattern does not match (should not normally happen)
	 */
	private int findMatchEndPositionIndexSingleItems(int[] sequence, int[] pattern, int patternLen) {
		int seqPos = 0;
		int lastIdx = -1;
		for (int p = 0; p < patternLen; p++) {
			int token = pattern[p];
			if (token <= 0) {
				continue;
			}
			// advance until the next occurrence of the item is found
			while (sequence[seqPos] != token) {
				if (sequence[seqPos] == -2) {
					return -1;
				}
				seqPos++;
			}
			lastIdx = seqPos;
			seqPos++;
		}
		return lastIdx;
	}

	/**
	 * Find the absolute token index, in {@code sequence}, of the last item matched
	 * by {@code pattern} (using leftmost matching with the standard
	 * sequential-pattern-matching semantics: each pattern itemset is matched as a
	 * subset of a single, strictly-later, data itemset), for the general case.
	 * 
	 * @param sequence   the sequence to match against
	 * @param pattern    the pattern's token buffer
	 * @param patternLen length of the pattern in the buffer
	 * @return the absolute index in {@code sequence} of the last matched item
	 *         (within the data itemset that matches the LAST itemset of the
	 *         pattern), or -1 if the pattern does not match.
	 */
	private int findMatchEndPositionIndex(int[] sequence, int[] pattern, int patternLen) {
		int pStart = 0;
		int seqPos = 0;

		outer: while (true) {
			// determine the boundaries of the current pattern itemset
			int pEnd = pStart;
			while (pEnd < patternLen && pattern[pEnd] != -1) {
				pEnd++;
			}
			boolean isLastPatternItemset = (pEnd == patternLen);

			int i = seqPos;
			while (sequence[i] != -2) {
				int itemsetStart = i;
				while (sequence[i] != -1 && sequence[i] != -2) {
					i++;
				}
				int itemsetEnd = i;

				// check if the current data itemset contains all items of the pattern itemset
				int maxIdx = -1;
				boolean allFound = true;
				for (int p = pStart; p < pEnd; p++) {
					int target = pattern[p];
					int foundIdx = -1;
					for (int k = itemsetStart; k < itemsetEnd; k++) {
						if (sequence[k] == target) {
							foundIdx = k;
							break;
						}
					}
					if (foundIdx == -1) {
						allFound = false;
						break;
					}
					if (foundIdx > maxIdx) {
						maxIdx = foundIdx;
					}
				}

				if (allFound) {
					if (isLastPatternItemset) {
						return maxIdx;
					}
					// move on to the next pattern itemset, in a strictly later data itemset
					pStart = pEnd + 1;
					seqPos = (sequence[itemsetEnd] == -1) ? itemsetEnd + 1 : itemsetEnd;
					continue outer;
				}

				if (sequence[itemsetEnd] == -1) {
					i = itemsetEnd + 1;
				} else {
					break;
				}
			}
			return -1;
		}
	}

	/**
	 * Check if pattern {@code p2} is strictly contained in pattern {@code p1}, for
	 * the "single item per itemset" case: this reduces to plain subsequence
	 * containment (ignoring -1 separators).
	 * 
	 * @param p1   the first pattern's token buffer
	 * @param len1 length of the first pattern
	 * @param p2   the second pattern's token buffer
	 * @param len2 length of the second pattern
	 * @return true if p1 strictly contains p2
	 */
	private boolean strictlyContainsSingleItems(int[] p1, int len1, int[] p2, int len2) {
		int i1 = 0;
		for (int i2 = 0; i2 < len2; i2++) {
			int token2 = p2[i2];
			if (token2 <= 0) {
				continue;
			}
			boolean found = false;
			while (i1 < len1) {
				int token1 = p1[i1++];
				if (token1 == token2) {
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check if pattern {@code p2} is strictly contained in pattern {@code p1}, for
	 * the general case: every itemset of p2 must be a subset of some itemset of p1,
	 * matched in strictly increasing itemset order.
	 * 
	 * @param p1   the first pattern's token buffer
	 * @param len1 length of the first pattern
	 * @param p2   the second pattern's token buffer
	 * @param len2 length of the second pattern
	 * @return true if p1 strictly contains p2
	 */
	private boolean strictlyContains(int[] p1, int len1, int[] p2, int len2) {
		int i1 = 0, i2 = 0;
		int itemsetsLeft1 = countItemsets(p1, len1);
		int itemsetsLeft2 = countItemsets(p2, len2);

		while (true) {
			// find the boundaries of the current itemsets of p1 and p2
			int i1End = i1;
			while (i1End < len1 && p1[i1End] != -1) {
				i1End++;
			}
			int i2End = i2;
			while (i2End < len2 && p2[i2End] != -1) {
				i2End++;
			}

			// check if p1's current itemset contains all items of p2's current itemset
			boolean containsAll = true;
			for (int k = i2; k < i2End; k++) {
				boolean found = false;
				for (int m = i1; m < i1End; m++) {
					if (p1[m] == p2[k]) {
						found = true;
						break;
					}
				}
				if (!found) {
					containsAll = false;
					break;
				}
			}

			if (containsAll) {
				itemsetsLeft2--;
				if (i2End >= len2) {
					return true;
				}
				i2 = i2End + 1;
			}

			itemsetsLeft1--;
			if (i1End >= len1) {
				return false;
			}
			i1 = i1End + 1;

			// stop early if there is no way to complete the match
			if (itemsetsLeft1 < itemsetsLeft2) {
				return false;
			}
		}
	}

	/**
	 * Count the number of itemsets (separated by -1) in a pattern buffer.
	 * 
	 * @param p   the pattern's token buffer
	 * @param len length of the pattern
	 * @return the number of itemsets
	 */
	private static int countItemsets(int[] p, int len) {
		int count = 1;
		for (int i = 0; i < len; i++) {
			if (p[i] == -1) {
				count++;
			}
		}
		return count;
	}

	// =====================================================================
	// ==================== FINAL GENERATOR FILTERING ======================
	// =====================================================================

	/**
	 * Final pass removing any remaining non-generator pattern (necessary because
	 * the search-time pruning check only compares against the IMMEDIATELY smaller
	 * level and uses the stronger "same future" condition; a complete, correct
	 * generator check must compare against every smaller level using only the "same
	 * support + strictly contains" condition). This pass is required whether or not
	 * search-time pruning was enabled.
	 */
	private void filterNonGenerator() {
		int emptySequenceSupport = sequenceCount;
		generators = new ArrayList<FSGPPattern>();
		boolean singleItems = !containsItemsetsWithMultipleItems;

		// for each level, keep only patterns that have no smaller sub-pattern with the same support
		for (int i = 1; i < levels.size(); i++) {
			List<FSGPPattern> levelI = levels.get(i);
			patLoop: for (int pIdx = 0, psize = levelI.size(); pIdx < psize; pIdx++) {
				FSGPPattern pattern = levelI.get(pIdx);

				if (pattern.support == emptySequenceSupport) {
					continue patLoop;
				}

				for (int j = 1; j < i; j++) {
					List<FSGPPattern> levelJ = levels.get(j);
					for (int qIdx = 0, qsize = levelJ.size(); qIdx < qsize; qIdx++) {
						FSGPPattern pattern2 = levelJ.get(qIdx);
						if (pattern2.support == pattern.support) {
							boolean contains = singleItems
									? strictlyContainsSingleItems(pattern.items, pattern.items.length, pattern2.items,
											pattern2.items.length)
									: strictlyContains(pattern.items, pattern.items.length, pattern2.items,
											pattern2.items.length);
							if (contains) {
								continue patLoop;
							}
						}
					}
				}
				generators.add(pattern);
			}
		}
	}

	// =====================================================================
	// ============= DATABASE SCANNING / PRUNING (as in PrefixSpan) ========
	// =====================================================================

	/**
	 * For each item, calculate the sequence ids of sequences containing that item,
	 * and detect if the database contains itemsets with more than one item.
	 * 
	 * @return a map from item to the list of sequence ids containing it
	 */
	private Map<Integer, List<Integer>> findSequencesContainingItems() {
		int itemCountInCurrentItemset;
		Map<Integer, List<Integer>> mapSequenceID = new HashMap<Integer, List<Integer>>();
		List<int[]> sequences = sequenceDatabase.getSequences();
		for (int i = 0, dbSize = sequences.size(); i < dbSize; i++) {
			int[] sequence = sequences.get(i);
			itemCountInCurrentItemset = 0;

			for (int j = 0, seqLen = sequence.length; j < seqLen; j++) {
				int token = sequence[j];
				if (token > 0) {
					List<Integer> sequenceIDs = mapSequenceID.get(token);
					if (sequenceIDs == null) {
						sequenceIDs = new ArrayList<Integer>();
						mapSequenceID.put(token, sequenceIDs);
					}
					int listSize = sequenceIDs.size();
					if (listSize == 0 || sequenceIDs.get(listSize - 1) != i) {
						sequenceIDs.add(i);
					}
					itemCountInCurrentItemset++;
					if (itemCountInCurrentItemset > 1) {
						containsItemsetsWithMultipleItems = true;
					}
				} else if (token == -1) {
					itemCountInCurrentItemset = 0;
				}
			}
		}
		return mapSequenceID;
	}

	/**
	 * Remove infrequent items and rename frequent ones (single item per itemset
	 * case).
	 */
	private void pruneAndRenameSequencesSingleItems() {
		List<int[]> sequences = sequenceDatabase.getSequences();
		for (int i = 0, size = sequences.size(); i < size; i++) {
			int[] sequence = sequences.get(i);
			int currentPosition = 0;
			for (int j = 0, seqLen = sequence.length; j < seqLen; j++) {
				int token = sequence[j];
				if (token > 0) {
					// keep the item only if it is frequent, using its renamed id
					int newName = itemNameConverter.toNewName(token);
					if (newName != 0) {
						sequence[currentPosition++] = newName;
					}
				} else if (token == -2) {
					// truncate the sequence array to its actual (pruned) size
					if (currentPosition > 0) {
						sequence[currentPosition] = -2;
						int[] newSequence = new int[currentPosition + 1];
						System.arraycopy(sequence, 0, newSequence, 0, currentPosition + 1);
						sequences.set(i, newSequence);
						continue;
					} else {
						sequences.set(i, null);
					}
				}
			}
		}
	}

	/**
	 * Remove infrequent items and rename frequent ones (multiple items per itemset
	 * case), also removing now-empty itemsets.
	 */
	private void pruneAndRenameSequencesMultipleItems() {
		List<int[]> sequences = sequenceDatabase.getSequences();
		for (int i = 0, size = sequences.size(); i < size; i++) {
			int[] sequence = sequences.get(i);
			int currentPosition = 0;
			int currentItemsetItemCount = 0;

			for (int j = 0, seqLen = sequence.length; j < seqLen; j++) {
				int token = sequence[j];
				if (token > 0) {
					// keep the item only if it is frequent, using its renamed id
					int newName = itemNameConverter.toNewName(token);
					if (newName != 0) {
						sequence[currentPosition++] = newName;
						currentItemsetItemCount++;
					}
				} else if (token == -1) {
					// only keep the separator if the itemset still has items left
					if (currentItemsetItemCount > 0) {
						sequence[currentPosition++] = -1;
						currentItemsetItemCount = 0;
					}
				} else if (token == -2) {
					// truncate the sequence array to its actual (pruned) size
					if (currentPosition > 0) {
						sequence[currentPosition] = -2;
						int[] newSequence = new int[currentPosition + 1];
						System.arraycopy(sequence, 0, newSequence, 0, currentPosition + 1);
						sequences.set(i, newSequence);
						continue;
					} else {
						sequences.set(i, null);
					}
				}
			}
		}
	}

	/**
	 * Build the projected database for a single item (single item per itemset
	 * case).
	 * 
	 * @param item        the item used to build the projection
	 * @param sequenceIDs sequence ids containing the item
	 * @param outSeqIDs   output array receiving the sequence ids of the projection
	 * @param outIndexes  output array receiving the start index of the projection in each sequence
	 * @return the number of entries written into the output arrays
	 */
	private int buildProjectedDatabaseSingleItems(int item, List<Integer> sequenceIDs, int[] outSeqIDs,
			int[] outIndexes) {
		int seqIDsSize = sequenceIDs.size();
		List<int[]> sequences = sequenceDatabase.getSequences();
		int projSize = 0;

		for (int idx = 0; idx < seqIDsSize; idx++) {
			int sequenceID = sequenceIDs.get(idx);
			int[] sequence = sequences.get(sequenceID);

			// find the item and keep what remains after it, if anything
			for (int j = 0; sequence[j] != -2; j++) {
				int token = sequence[j];
				if (token == item) {
					if (sequence[j + 1] != -2) {
						outSeqIDs[projSize] = sequenceID;
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
	 * Build the projected database for a single item (multiple items per itemset
	 * case, first level).
	 * 
	 * @param item        the item used to build the projection
	 * @param sequenceIDs sequence ids containing the item
	 * @param outSeqIDs   output array receiving the sequence ids of the projection
	 * @param outIndexes  output array receiving the start index of the projection in each sequence
	 * @return the number of entries written into the output arrays
	 */
	private int buildProjectedDatabaseFirstTimeMultipleItems(int item, List<Integer> sequenceIDs, int[] outSeqIDs,
			int[] outIndexes) {
		int seqIDsSize = sequenceIDs.size();
		List<int[]> sequences = sequenceDatabase.getSequences();
		int projSize = 0;

		for (int idx = 0; idx < seqIDsSize; idx++) {
			int sequenceID = sequenceIDs.get(idx);
			int[] sequence = sequences.get(sequenceID);

			// find the item and keep what remains after it, unless the sequence ends there
			for (int j = 0; sequence[j] != -2; j++) {
				int token = sequence[j];
				if (token == item) {
					boolean isEndOfSequence = sequence[j + 1] == -1 && sequence[j + 2] == -2;
					if (!isEndOfSequence) {
						outSeqIDs[projSize] = sequenceID;
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
	 * Find all frequent items (candidates) in a projected database, single item per
	 * itemset case.
	 * 
	 * @param projSeqIDs  sequence ids of the projected database
	 * @param projIndexes start index, in each sequence, of the projection
	 * @param projSize    number of entries in the projected database
	 * @param outArray    structure receiving the candidate items and their projections
	 */
	private void findAllFrequentPairsSingleItems(int[] projSeqIDs, int[] projIndexes, int projSize,
			ItemPairArray outArray) {

		List<int[]> dbSequences = dbSequencesList;
		outArray.touchedCount = 0;

		// scan the remaining part of each sequence and record each item found
		for (int seqIdx = 0; seqIdx < projSize; seqIdx++) {
			int sequenceID = projSeqIDs[seqIdx];
			int[] sequence = dbSequences.get(sequenceID);

			for (int i = projIndexes[seqIdx]; sequence[i] != -2; i++) {
				int token = sequence[i];
				if (token > 0) {
					PairData pairData = outArray.dataByItem[token];
					if (pairData == null) {
						pairData = new PairData();
						outArray.dataByItem[token] = pairData;
					}
					if (pairData.projSize == 0) {
						outArray.touchedItems[outArray.touchedCount++] = token;
					}
					if (!pairData.lastSeqIDEquals(sequenceID)) {
						pairData.add(sequenceID, i + 1);
					}
				}
			}
		}
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Find all frequent items (candidates) in a projected database, general
	 * (multiple items per itemset) case. This is a direct transposition of the
	 * corresponding method in the optimized PrefixSpan.
	 * 
	 * @param projSeqIDs         sequence ids of the projected database
	 * @param projIndexes        start index, in each sequence, of the projection
	 * @param projSize           number of entries in the projected database
	 * @param lastBufferPosition position of the last token of the current prefix
	 * @param mapsPairs          structure receiving the I-extension and S-extension candidates
	 */
	private void findAllFrequentPairs(int[] projSeqIDs, int[] projIndexes, int projSize, int lastBufferPosition,
			ArrayFrequentPairs mapsPairs) {

		// locate the start of the last itemset of the current prefix
		int firstPositionOfLastItemsetInBuffer = lastBufferPosition;
		while (lastBufferPosition > 0) {
			firstPositionOfLastItemsetInBuffer--;
			if (firstPositionOfLastItemsetInBuffer < 0 || patternBuffer[firstPositionOfLastItemsetInBuffer] == -1) {
				firstPositionOfLastItemsetInBuffer++;
				break;
			}
		}

		List<int[]> dbSequences = dbSequencesList;

		ItemPairArray pairsArr = mapsPairs.pairs;
		ItemPairArray postfixArr = mapsPairs.pairsInPostfix;
		pairsArr.touchedCount = 0;
		postfixArr.touchedCount = 0;

		for (int seqIdx = 0; seqIdx < projSize; seqIdx++) {
			int sequenceID = projSeqIDs[seqIdx];
			int[] sequence = dbSequences.get(sequenceID);

			int positionToBeMatched = firstPositionOfLastItemsetInBuffer;
			int previousItem = sequence[projIndexes[seqIdx] - 1];
			boolean currentItemsetIsPostfix = (previousItem != -1);
			boolean isFirstItemset = true;

			for (int i = projIndexes[seqIdx]; sequence[i] != -2; i++) {
				int token = sequence[i];

				if (token > 0) {
					// record the item as an I-extension or S-extension candidate
					ItemPairArray targetArr = currentItemsetIsPostfix ? postfixArr : pairsArr;
					PairData pairData = targetArr.dataByItem[token];
					if (pairData == null) {
						pairData = new PairData();
						targetArr.dataByItem[token] = pairData;
					}
					if (pairData.projSize == 0) {
						targetArr.touchedItems[targetArr.touchedCount++] = token;
					}
					if (!pairData.lastSeqIDEquals(sequenceID)) {
						pairData.add(sequenceID, i + 1);
					}

					if (currentItemsetIsPostfix && !isFirstItemset) {
						PairData pairData2 = pairsArr.dataByItem[token];
						if (pairData2 == null) {
							pairData2 = new PairData();
							pairsArr.dataByItem[token] = pairData2;
						}
						if (pairData2.projSize == 0) {
							pairsArr.touchedItems[pairsArr.touchedCount++] = token;
						}
						if (!pairData2.lastSeqIDEquals(sequenceID)) {
							pairData2.add(sequenceID, i + 1);
						}
					}

					if (!currentItemsetIsPostfix && patternBuffer[positionToBeMatched] == token) {
						positionToBeMatched++;
						if (positionToBeMatched > lastBufferPosition) {
							currentItemsetIsPostfix = true;
						}
					}
				} else if (token == -1) {
					// move to the next itemset of the sequence
					isFirstItemset = false;
					currentItemsetIsPostfix = false;
					positionToBeMatched = firstPositionOfLastItemsetInBuffer;
				}
			}
		}
		MemoryLogger.getInstance().checkMemory();
	}

	// =====================================================================
	// ============================ UTILITIES ==============================
	// =====================================================================

	/**
	 * Convert a list of Integer objects to a primitive int array.
	 * 
	 * @param list the list to convert
	 * @return the resulting int array
	 */
	private static int[] toIntArray(List<Integer> list) {
		int[] arr = new int[list.size()];
		for (int i = 0; i < arr.length; i++) {
			arr[i] = list.get(i);
		}
		return arr;
	}

	/**
	 * A frequent sequential GENERATOR pattern found by FSGP2. Items are stored
	 * using the internal renamed ids; use the owning {@code AlgoFSGP2} instance's
	 * {@link AlgoFSGP2#writeResultToFile(String)} (or convert manually via the item
	 * name converter) to get back original item ids.
	 */
	public static class FSGPPattern {
		/** pattern content: item &gt; 0, -1 = itemset separator (renamed item ids) */
		public final int[] items;
		/** number of items (not itemsets) in this pattern */
		public final int itemCount;
		/** number of itemsets in this pattern */
		public final int itemsetCount;
		/** sequence ids (into the pruned database) where this pattern occurs */
		public final int[] sequenceIDs;
		/** the support of this pattern */
		public final int support;

		FSGPPattern(int[] items, int itemCount, int itemsetCount, int[] sequenceIDs) {
			this.items = items;
			this.itemCount = itemCount;
			this.itemsetCount = itemsetCount;
			this.sequenceIDs = sequenceIDs;
			this.support = sequenceIDs.length;
		}

		/**
		 * Build a string representation of this pattern.
		 * 
		 * @param showSequenceIdentifiers true to append the sequence ids where the pattern occurs
		 * @return the string representation
		 */
		public String toString(boolean showSequenceIdentifiers) {
			StringBuilder sb = new StringBuilder();

			// Append items and itemset separators
			for (int i = 0; i < items.length; i++) {
				if (items[i] == -1) {
					sb.append("-1 ");
				} else {
					sb.append(items[i]).append(" ");
				}
			}
			sb.append("-1");

			// Append support information
			sb.append(" #SUP: ").append(support);

			// Append sequence IDs
			if (showSequenceIdentifiers) {
				sb.append(" #SID: ");
				for (int i = 0; i < sequenceIDs.length; i++) {
					sb.append(sequenceIDs[i]);
					if (i < sequenceIDs.length - 1) {
						sb.append(" ");
					}
				}
			}

			return sb.toString();
		}
	}

	/**
	 * Sparse array-based structure mapping a (renamed) item id directly to its
	 * {@link PairData} projection. See the optimized PrefixSpan for a detailed
	 * rationale.
	 */
	static class ItemPairArray {
		/** projection data indexed directly by item id */
		final PairData[] dataByItem;
		/** items that currently have projection data (to allow fast reset) */
		final int[] touchedItems;
		/** number of valid entries in touchedItems */
		int touchedCount;

		ItemPairArray(int frequentItemCount) {
			dataByItem = new PairData[frequentItemCount + 1];
			touchedItems = new int[frequentItemCount + 1];
			touchedCount = 0;
		}
	}

	/** Holds the projected database for a single item extension. */
	static class PairData {
		/** sequence ids where the item occurs in the projected database */
		int[] seqIDs;
		/** start index of the projection, for each entry in seqIDs */
		int[] indexes;
		/** number of valid entries in seqIDs / indexes */
		int projSize;

		PairData() {
			seqIDs = new int[8];
			indexes = new int[8];
			projSize = 0;
		}

		/**
		 * Add a new (sequence id, index) entry to this projection, growing the
		 * internal arrays if needed.
		 * 
		 * @param seqID the sequence id
		 * @param index the start index of the projection in that sequence
		 */
		void add(int seqID, int index) {
			if (projSize == seqIDs.length) {
				int newCap = projSize * 2;
				int[] newSeqIDs = new int[newCap];
				int[] newIndexes = new int[newCap];
				System.arraycopy(seqIDs, 0, newSeqIDs, 0, projSize);
				System.arraycopy(indexes, 0, newIndexes, 0, projSize);
				seqIDs = newSeqIDs;
				indexes = newIndexes;
			}
			seqIDs[projSize] = seqID;
			indexes[projSize] = index;
			projSize++;
		}

		/** Reset this projection so it can be reused. */
		void reset() {
			projSize = 0;
		}

		/**
		 * Check if the last recorded sequence id equals the given one.
		 * 
		 * @param seqID the sequence id to compare
		 * @return true if it matches the last recorded sequence id
		 */
		boolean lastSeqIDEquals(int seqID) {
			return projSize > 0 && seqIDs[projSize - 1] == seqID;
		}
	}

	/** Contains the two array-based structures used for I-/S-extension counting. */
	public static class ArrayFrequentPairs {
		/** candidate items found as S-extensions (new itemset) */
		public final ItemPairArray pairs;
		/** candidate items found as I-extensions (within the cut/postfix itemset) */
		public final ItemPairArray pairsInPostfix;

		public ArrayFrequentPairs(int frequentItemCount) {
			pairs = new ItemPairArray(frequentItemCount);
			pairsInPostfix = new ItemPairArray(frequentItemCount);
		}
	}

	// =====================================================================
	// =========================== OUTPUT / STATS ==========================
	// =====================================================================

	/**
	 * Write the generator patterns found to an output file.
	 * 
	 * @param path the output file path
	 * @throws IOException if an I/O error occurs
	 */
	public void writeResultToFile(String path) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(path));
		writer.write("@FILETYPE=\"Frequent sequential generators\"");
		writer.append(System.lineSeparator());
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.append(System.lineSeparator());
		
		for (FSGPPattern pattern : generators) {
			StringBuilder sb = new StringBuilder((pattern.items.length + 2) * 8 + 20);
			// convert renamed items back to their original ids
			for (int i = 0; i < pattern.items.length; i++) {
				int token = pattern.items[i];
				sb.append(token == -1 ? -1 : itemNameConverter.toOldName(token));
				sb.append(' ');
			}
			sb.append("-1 #SUP: ");
			sb.append(pattern.support);
			if (showSequenceIdentifiers) {
				sb.append(" #SID: ");
				for (int sid : pattern.sequenceIDs) {
					sb.append(sid);
					sb.append(' ');
				}
			}
			writer.write(sb.toString());
			writer.newLine();
		}
		writer.close();
	}

	/**
	 * Print statistics about the algorithm execution to System.out.
	 */
	public void printStatistics() {
		StringBuilder r = new StringBuilder(200);
		r.append("=============  FSGP 2.67 - STATISTICS =============");
		r.append(System.lineSeparator());
		r.append("Total time ~ ").append(endTime - startTime).append(" ms");
		r.append(System.lineSeparator());
		r.append(" Frequent generator sequences count : ").append(generators == null ? 0 : generators.size());
		r.append(System.lineSeparator());
		r.append(" Prefix pruned count : ").append(prefixPrunedCount);
		r.append(System.lineSeparator());
		r.append(" Max memory (mb) : ").append(MemoryLogger.getInstance().getMaxMemory());
		r.append(System.lineSeparator());
		r.append(" minsup = ").append(minsuppAbsolute).append(" sequences.");
		r.append(System.lineSeparator());
		r.append("===================================================");
		r.append(System.lineSeparator());
		System.out.println(r.toString());
	}

	/**
	 * Get the number of generator patterns found.
	 * @return the pattern count
	 */
	public long getPatternCount() {
		return generators == null ? 0 : generators.size();
	}

	/**
	 * Get the list of generator patterns found by the algorithm.
	 * @return the list of generator patterns
	 */
	public List<FSGPPattern> getGenerators() {
		return generators;
	}

	/**
	 * Get the number of prefixes pruned by the generator-pruning rule.
	 * @return the prefix pruned count
	 */
	public int getPrefixPrunedCount() {
		return prefixPrunedCount;
	}

	/**
	 * Get the maximum length of patterns to be found (in terms of item count).
	 * @return the maximum pattern length
	 */
	public int getMaximumPatternLength() {
		return maximumPatternLength;
	}

	/**
	 * Set the maximum length of patterns to be found (in terms of item count).
	 * @param maximumPatternLength the maximum pattern length to set
	 */
	public void setMaximumPatternLength(int maximumPatternLength) {
		this.maximumPatternLength = maximumPatternLength;
	}

	/**
	 * Set whether sequence identifiers should be shown for each pattern found.
	 * @param showSequenceIdentifiers true to show sequence identifiers, false otherwise
	 */
	public void setShowSequenceIdentifiers(boolean showSequenceIdentifiers) {
		this.showSequenceIdentifiers = showSequenceIdentifiers;
	}
}