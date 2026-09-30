package ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

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
 * This is a 2024 implementation of the FEAT algorithm. FEAT was
 * proposed in a WWW2008 article by C. Gao, J. Wang, Y. He, L. Zhou.
 *
 * NOTE: This implementation saves the pattern to a file as soon as they are
 * found or can keep the pattern into memory, depending on what the user choose.
 *
 * Copyright (c) 2008-2026 Philippe Fournier-Viger
 */
public class AlgoFEAT2 {

	/** total execution time in milliseconds */
	long totalTime;

	/** the number of patterns found */
	int patternCount;

	/** absolute minimum support threshold (a number of sequences) */
	private int minsuppAbsolute;

	/** writer to write output file */
	BufferedWriter writer = null;

	/** the sequential patterns that are found (if kept in memory) */
	private List<SequentialPattern> generators = null;

	/** maximum pattern length in terms of item count */
	private int maximumPatternLength = 1000;

	/** if true, sequence identifiers of each pattern will be shown */
	boolean showSequenceIdentifiers = false;

	/** number of prefixes pruned, for statistics */
	public int prefixPrunedCount = 0;

	/** if true, FEAT's forward and backward pruning will be applied */
	boolean performPruning = true;

	/** size of the buffer used to store the pattern currently being mined */
	final int BUFFERS_SIZE = 2000;

	/**
	 * Buffer for storing the current pattern that is mined when performing mining.
	 * The idea is to always reuse the same buffer to reduce memory usage. Values
	 * stored are renamed (consecutive) item ids - see {@link #itemNameConverter}.
	 */
	private int[] patternBuffer = new int[BUFFERS_SIZE];

	/** original sequence count before removing infrequent items */
	int originalSequenceCount = 0;

	/** sequence count after pruning infrequent items */
	int sequenceCount = 0;

	/** the sequence database */
	SequenceDatabase sequenceDatabase;

	/** true if the database contains itemsets with multiple items */
	boolean containsItemsetsWithMultipleItems = false;

	/**
	 * Reusable buffer of sequence IDs for the projected database at each recursion
	 * depth. Unlike classic PrefixSpan, this buffer can contain MULTIPLE entries
	 * for the same sequence id (one per occurrence/embedding of the current
	 * prefix in that sequence), because FEAT's forward-pruning test requires
	 * examining every occurrence. Each depth's array is allocated lazily and can
	 * grow dynamically (see {@link #ensureNextLevelCapacity(int, int)}).
	 */
	private int[][] projSeqIDsBuffer;

	/**
	 * Reusable buffer of item indexes for the projected database at each recursion
	 * depth, parallel to {@link #projSeqIDsBuffer}.
	 */
	private int[][] projIndexesBuffer;

	/**
	 * Parent-projection sequence IDs, parallel to the depth-indexed projection buffers.
	 * Filled by copying {@link PairData#parentSeqIDs} into the next depth's buffers
	 * when a candidate is materialized.
	 */
	private int[][] parentProjSeqIDsBuffer;

	/**
	 * Parent-projection item indexes, parallel to the depth-indexed projection buffers.
	 * Filled by copying {@link PairData#parentIndexes} into the next depth's buffers
	 * when a candidate is materialized.
	 */
	private int[][] parentProjIndexesBuffer;

	/**
	 * Reusable array-based structures, indexed by recursion depth, for storing
	 * candidate item projections when the database does not contain itemsets with
	 * multiple items. Since all items appearing in the (pruned) sequence database
	 * are renamed to consecutive integers in [1, frequentItemCount] before mining
	 * (see {@link #itemNameConverter}), we can index directly into a plain array
	 * instead of using a HashMap, which avoids autoboxing and hashing overhead in
	 * this hot recursive path. At each depth, we reuse the same array by resetting
	 * PairData objects rather than allocating new ones, to reduce GC pressure.
	 */
	private ItemPairArray[] singleItemsArrays;

	/**
	 * Reusable array-based structures, indexed by recursion depth, for storing
	 * candidate item projections when the database contains itemsets with multiple
	 * items. See {@link #singleItemsArrays} for details on why arrays are used
	 * instead of a HashMap.
	 */
	private ArrayFrequentPairs[] frequentPairsArrays;

	/**
	 * Utility used to rename frequent items to consecutive integers starting at 1
	 * before mining. This allows the rest of the algorithm to use plain arrays
	 * indexed directly by item id instead of a HashMap in the hot recursive search,
	 * and also avoids a HashMap lookup per item occurrence when pruning infrequent
	 * items from the database. The original item ids are only restored when
	 * patterns are written to the output (see the savePattern methods).
	 */
	private ItemNameConverter itemNameConverter;

	/** the number of distinct frequent (single) items found in the database */
	private int frequentItemCount;

	/**
	 * Default constructor
	 */
	public AlgoFEAT2() {
	}

	/**
	 * Run the algorithm using a relative minimum support threshold.
	 *
	 * @param inputFile      the path to the input sequence database file
	 * @param minsupRelative the minimum support as a percentage (e.g. 50%) as a
	 *                       value in [0,1]
	 * @param outputFilePath the path of the output file to save the result, or
	 *                       null if you want the result to be saved into memory
	 * @return the result, if saved into memory, otherwise null
	 * @throws IOException exception if error while writing the file
	 */
	public List<SequentialPattern> runAlgorithm(String inputFile, double minsupRelative, String outputFilePath)
			throws IOException {
		totalTime = 0;
		long startTime = System.currentTimeMillis();

		// reset statistics
		patternCount = 0;
		prefixPrunedCount = 0;
		MemoryLogger.getInstance().reset();

		// load the database
		sequenceDatabase = new SequenceDatabase();
		sequenceDatabase.loadFile(inputFile);
		originalSequenceCount = sequenceDatabase.size();
		sequenceCount = sequenceDatabase.size();

		// convert relative support to absolute
		this.minsuppAbsolute = (int) Math.ceil((minsupRelative * sequenceCount) - 0.000001);
		if (this.minsuppAbsolute == 0) {
			this.minsuppAbsolute = 1;
		}

		// initialize output (file or memory)
		if (outputFilePath == null) {
			writer = null;
			generators = new ArrayList<SequentialPattern>();
		} else {
			generators = new ArrayList<SequentialPattern>();
			writer = new BufferedWriter(new FileWriter(outputFilePath));
			writer.write("@FILETYPE=\"Frequent sequential generators\"");
			writer.append(System.lineSeparator());
			writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
			writer.append(System.lineSeparator());
		}

		// run the main algorithm
		feat();

		sequenceDatabase = null;

		totalTime = System.currentTimeMillis() - startTime;
		if (writer != null) {
			writer.close();
		}
		return generators;
	}

	/**
	 * Run the algorithm using an absolute minimum support threshold.
	 *
	 * @param inputFile      the path to the input sequence database file
	 * @param outputFilePath the path of the output file to save the result, or
	 *                       null if you want the result to be saved into memory
	 * @param minsup         the minimum support as an integer (number of sequences)
	 * @return the result, if saved into memory, otherwise null
	 * @throws IOException exception if error while writing the file
	 */
	public List<SequentialPattern> runAlgorithm(String inputFile, String outputFilePath, int minsup)
			throws IOException {
		// reset statistics
		patternCount = 0;
		prefixPrunedCount = 0;
		MemoryLogger.getInstance().reset();
		this.minsuppAbsolute = minsup;
		totalTime = 0;
		long startTime = System.currentTimeMillis();

		// load the database
		sequenceDatabase = new SequenceDatabase();
		sequenceDatabase.loadFile(inputFile);
		originalSequenceCount = sequenceDatabase.size();
		sequenceCount = sequenceDatabase.size();

		// initialize output (file or memory)
		if (outputFilePath == null) {
			writer = null;
			generators = new ArrayList<SequentialPattern>();
		} else {
			generators = new ArrayList<SequentialPattern>();
			writer = new BufferedWriter(new FileWriter(outputFilePath));
		}

		// run the main algorithm
		feat();

		sequenceDatabase = null;

		totalTime = System.currentTimeMillis() - startTime;
		if (writer != null) {
			writer.close();
		}
		return generators;
	}

	/**
	 * This is the main method for the FEAT algorithm. It initializes reusable
	 * buffers, renames frequent items to consecutive IDs, then launches the
	 * appropriate recursive search depending on whether the database contains
	 * multi-item itemsets.
	 *
	 * @throws IOException exception if an error while writing the output file
	 */
	private void feat() throws IOException {
		// allocate reusable depth-indexed buffers
		projSeqIDsBuffer = new int[maximumPatternLength + 1][];
		projIndexesBuffer = new int[maximumPatternLength + 1][];
		parentProjSeqIDsBuffer = new int[maximumPatternLength + 1][];
		parentProjIndexesBuffer = new int[maximumPatternLength + 1][];
		singleItemsArrays = new ItemPairArray[maximumPatternLength + 1];
		frequentPairsArrays = new ArrayFrequentPairs[maximumPatternLength + 1];

		// scan the database to find which sequences contain each item
		Map<Integer, List<Integer>> mapSequenceID = findSequencesContainingItems();

		// count frequent items and assign consecutive renamed IDs
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

		// launch the appropriate recursive search
		if (containsItemsetsWithMultipleItems) {
			prefixspanWithMultipleItemsFEAT(mapSequenceID);
		} else {
			prefixspanWithSingleItemsFEAT(mapSequenceID);
		}

		// release all reusable buffers
		projSeqIDsBuffer = null;
		projIndexesBuffer = null;
		parentProjSeqIDsBuffer = null;
		parentProjIndexesBuffer = null;
		singleItemsArrays = null;
		frequentPairsArrays = null;
		itemNameConverter = null;
	}

	/**
	 * Lazily allocate the reusable projected-database buffers and candidate
	 * array-based structures for the given recursion depth, if they have not been
	 * allocated already. This avoids allocating buffers for every depth up to
	 * maximumPatternLength up-front, since only the depths actually reached during
	 * mining need them.
	 *
	 * @param depth the recursion depth for which the buffers are needed
	 */
	private void ensureDepthBuffers(int depth) {
		if (projSeqIDsBuffer[depth] == null) {
			projSeqIDsBuffer[depth] = new int[sequenceCount];
			projIndexesBuffer[depth] = new int[sequenceCount];
			parentProjSeqIDsBuffer[depth] = new int[sequenceCount];
			parentProjIndexesBuffer[depth] = new int[sequenceCount];
		}
		if (singleItemsArrays[depth] == null) {
			singleItemsArrays[depth] = new ItemPairArray(frequentItemCount);
		}
		if (frequentPairsArrays[depth] == null) {
			frequentPairsArrays[depth] = new ArrayFrequentPairs(frequentItemCount);
		}
	}

	/**
	 * Ensure that the projection buffers at the given depth can hold at least
	 * {@code requiredSize} entries, growing them (by doubling) if necessary.
	 * Unlike classic PrefixSpan, the number of pseudo-sequence entries at a given
	 * depth is NOT bounded by {@link #sequenceCount}, because a single original
	 * sequence can contribute several entries (one per occurrence/embedding of
	 * the current prefix) - this is required for the correctness of FEAT's
	 * forward-pruning test.
	 *
	 * @param depth        the recursion depth
	 * @param requiredSize the minimum required capacity
	 */
	private void ensureNextLevelCapacity(int depth, int requiredSize) {
		int[] arr = projSeqIDsBuffer[depth];
		if (arr == null || arr.length < requiredSize) {
			// double the capacity or use requiredSize if larger
			int newCap = Math.max(requiredSize, (arr == null ? 8 : arr.length * 2));
			projSeqIDsBuffer[depth] = arr == null ? new int[newCap] : Arrays.copyOf(arr, newCap);

			int[] idxArr = projIndexesBuffer[depth];
			projIndexesBuffer[depth] = idxArr == null ? new int[newCap] : Arrays.copyOf(idxArr, newCap);

			int[] pArr = parentProjSeqIDsBuffer[depth];
			parentProjSeqIDsBuffer[depth] = pArr == null ? new int[newCap] : Arrays.copyOf(pArr, newCap);

			int[] piArr = parentProjIndexesBuffer[depth];
			parentProjIndexesBuffer[depth] = piArr == null ? new int[newCap] : Arrays.copyOf(piArr, newCap);
		}
	}

	/**
	 * Remove infrequent items from the database and explore each frequent single
	 * item as a length-1 prefix, applying FEAT's forward and backward pruning.
	 *
	 * @param mapSequenceID the map of items to the sequence IDs that contain them
	 * @throws IOException if error writing to file
	 */
	private void prefixspanWithSingleItemsFEAT(Map<Integer, List<Integer>> mapSequenceID) throws IOException {
		List<int[]> sequences = sequenceDatabase.getSequences();

		// rewrite each sequence in-place, keeping only frequent items
		for (int i = 0, size = sequences.size(); i < size; i++) {
			int[] sequence = sequences.get(i);
			int currentPosition = 0;

			for (int j = 0, seqLen = sequence.length; j < seqLen; j++) {
				int token = sequence[j];
				if (token > 0) {
					int newName = itemNameConverter.toNewName(token);
					if (newName != 0) {
						sequence[currentPosition++] = newName;
					}
				} else if (token == -2) {
					if (currentPosition > 0) {
						sequence[currentPosition] = -2;
						int[] newSequence = new int[currentPosition + 1];
						System.arraycopy(sequence, 0, newSequence, 0, currentPosition + 1);
						sequences.set(i, newSequence);
						break;
					} else {
						sequences.set(i, null);
					}
				}
			}
		}

		// count non-null sequences after pruning
		int cnt = 0;
		for (int[] s : sequences)
			if (s != null)
				cnt++;
		sequenceCount = cnt;

		// explore each frequent item as a length-1 prefix
		for (Entry<Integer, List<Integer>> entry : mapSequenceID.entrySet()) {
			List<Integer> sequenceIDs = entry.getValue();
			int support = sequenceIDs.size();
			if (support >= minsuppAbsolute) {
				int item = entry.getKey();
				int newItem = itemNameConverter.toNewName(item);
				if (newItem == 0) {
					continue;
				}
				patternBuffer[0] = newItem;

				ensureDepthBuffers(1);
				int projSize = buildProjectedDatabaseSingleItems(newItem, sequenceIDs, 1);

				// no parent occurrence for length-1 prefixes
				Arrays.fill(parentProjSeqIDsBuffer[1], 0, projSize, -1);
				Arrays.fill(parentProjIndexesBuffer[1], 0, projSize, -1);

				boolean canPrune = false;
				boolean isGenerator = true;

				// If the item appears in every original sequence, it cannot be a generator.
				// However, forward ("forced extension") pruning for size-1 prefixes is
				// unsafe in the presence of multiple occurrences per sequence and is
				// also unnecessary for correctness: concrete forced extensions are
				// handled by checkForwardPruningGeneral when exploring children.
				if (originalSequenceCount == support) {
					isGenerator = false;
					// DO NOT set canPrune here: we still need to grow this prefix.
				}

				if (isGenerator) {
					savePattern1Item(item, support, sequenceIDs);
				}

				// recurse unless pruning applies or max length reached
				if ((!performPruning || !canPrune) && maximumPatternLength > 1) {
					featRecursionSingleItems(projSeqIDsBuffer[1], projIndexesBuffer[1], parentProjSeqIDsBuffer[1],
							parentProjIndexesBuffer[1], projSize, 2, 0, 1, support, newItem);
				} else {
					prefixPrunedCount++;
				}
			}
		}
	}

	/**
	 * Remove infrequent items from the database and explore each frequent single
	 * item as a length-1 prefix, applying FEAT's forward and backward pruning,
	 * for databases that contain itemsets with multiple items.
	 *
	 * @param mapSequenceID the map of items to the sequence IDs that contain them
	 * @throws IOException if error writing to file
	 */
	private void prefixspanWithMultipleItemsFEAT(Map<Integer, List<Integer>> mapSequenceID) throws IOException {
		List<int[]> sequences = sequenceDatabase.getSequences();

		// rewrite each sequence in-place, keeping only frequent items and non-empty itemsets
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
					// only keep itemset separator if the itemset has items
					if (currentItemsetItemCount > 0) {
						sequence[currentPosition++] = -1;
						currentItemsetItemCount = 0;
					}
				} else if (token == -2) {
					if (currentPosition > 0) {
						sequence[currentPosition] = -2;
						int[] newSequence = new int[currentPosition + 1];
						System.arraycopy(sequence, 0, newSequence, 0, currentPosition + 1);
						sequences.set(i, newSequence);
						break;
					} else {
						sequences.set(i, null);
					}
				}
			}
		}

		// count non-null sequences after pruning
		int cnt = 0;
		for (int[] s : sequences)
			if (s != null)
				cnt++;
		sequenceCount = cnt;

		// explore each frequent item as a length-1 prefix
		for (Entry<Integer, List<Integer>> entry : mapSequenceID.entrySet()) {
			List<Integer> sequenceIDs = entry.getValue();
			int support = sequenceIDs.size();
			if (support >= minsuppAbsolute) {
				int item = entry.getKey();
				int newItem = itemNameConverter.toNewName(item);
				if (newItem == 0) {
					continue;
				}
				patternBuffer[0] = newItem;

				ensureDepthBuffers(1);
				int projSize = buildProjectedDatabaseFirstTimeMultipleItems(newItem, sequenceIDs, 1);

				// no parent occurrence for length-1 prefixes
				Arrays.fill(parentProjSeqIDsBuffer[1], 0, projSize, -1);
				Arrays.fill(parentProjIndexesBuffer[1], 0, projSize, -1);

				boolean canPrune = false;
				boolean isGenerator = true;

				// Same reasoning as in the single-itemset case:
				// item present in every sequence => not a generator,
				// but we do NOT prune its subtree here.
				if (originalSequenceCount == support) {
					isGenerator = false;
				}

				if (isGenerator) {
					savePattern1Item(item, support, sequenceIDs);
				}

				// recurse unless pruning applies or max length reached
				if ((!performPruning || !canPrune) && maximumPatternLength > 1) {
					featRecursionMultipleItems(projSeqIDsBuffer[1], projIndexesBuffer[1], parentProjSeqIDsBuffer[1],
							parentProjIndexesBuffer[1], projSize, 2, 0, 1, support, newItem);
				} else {
					prefixPrunedCount++;
				}
			}
		}
	}

	/**
	 * This method saves a sequential pattern containing a single item to the output
	 * file or in memory, depending on if the user provided an output file path or
	 * not when he launched the algorithm.
	 *
	 * @param item        the pattern to be saved (the original, non-renamed item id)
	 * @param support     the support of this item
	 * @param sequenceIDs the list of sequences containing this item
	 * @throws IOException exception if error while writing the output file
	 */
	private void savePattern1Item(int item, int support, List<Integer> sequenceIDs) throws IOException {
		patternCount++;

		// store the pattern in memory
		SequentialPattern pattern = new SequentialPattern();
		pattern.addItemset(new Itemset(item));
		pattern.setSequenceIDs(sequenceIDs);
		generators.add(pattern);

		// write pattern to file if needed
		if (writer != null) {
			StringBuilder r = new StringBuilder(32);
			r.append(item);
			r.append(" -1 #SUP: ");
			r.append(support);
			if (showSequenceIdentifiers) {
				r.append(" #SID: ");
				for (int i = 0, size = sequenceIDs.size(); i < size; i++) {
					r.append(sequenceIDs.get(i));
					r.append(" ");
				}
			}
			writer.write(r.toString());
			writer.newLine();
		}
	}

	/**
	 * Save a pattern containing two or more items to the output file or in memory.
	 * The pattern is stored internally in {@link #patternBuffer} using renamed item
	 * ids, so each item is converted back to its original id via
	 * {@link #itemNameConverter} before being output.
	 *
	 * @param lastBufferPosition the last position in the buffer for this pattern
	 * @param distinctSeqIDs     the distinct sequence IDs supporting this pattern
	 * @param support            the number of entries in {@code distinctSeqIDs}
	 * @throws IOException if error when writing to file
	 */
	private void savePatternMulti(int lastBufferPosition, int[] distinctSeqIDs, int support) throws IOException {
		patternCount++;

		// reconstruct the pattern from the buffer using original item IDs
		SequentialPattern pattern = new SequentialPattern();
		Itemset currentItemset = new Itemset();
		for (int i = 0; i <= lastBufferPosition; i++) {
			int token = patternBuffer[i];
			if (token > 0) {
				currentItemset.addItem(itemNameConverter.toOldName(token));
			} else if (token == -1) {
				pattern.addItemset(currentItemset);
				currentItemset = new Itemset();
			}
		}
		pattern.addItemset(currentItemset);

		// store the support IDs and add to results
		List<Integer> sids = new ArrayList<Integer>(support);
		for (int i = 0; i < support; i++)
			sids.add(distinctSeqIDs[i]);
		pattern.setSequenceIDs(sids);
		generators.add(pattern);

		// write pattern to file if needed
		if (writer != null) {
			StringBuilder r = new StringBuilder((lastBufferPosition + 2) * 8 + 20);
			for (int i = 0; i <= lastBufferPosition; i++) {
				int token = patternBuffer[i];
				r.append(token == -1 ? -1 : itemNameConverter.toOldName(token));
				r.append(" ");
			}
			if (patternBuffer[lastBufferPosition] != -1) {
				r.append("-1 ");
			}
			r.append("#SUP: ");
			r.append(support);
			if (showSequenceIdentifiers) {
				r.append(" #SID: ");
				for (int i = 0; i < support; i++) {
					r.append(distinctSeqIDs[i]);
					r.append(" ");
				}
			}
			writer.write(r.toString());
			writer.newLine();
		}
	}

	/**
	 * Scan the database to find, for each item, the list of sequence IDs that
	 * contain it. Also sets {@link #containsItemsetsWithMultipleItems} if any
	 * itemset has more than one item.
	 *
	 * @return a map of items to the sequence IDs that contain each item
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
					// add this sequence to the item's support list if not already present
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
	 * Create the initial (depth 1) projected database for a given item, for
	 * databases where each itemset contains only one item. Unlike classic
	 * PrefixSpan, EVERY occurrence of {@code item} in each sequence produces its
	 * own entry (no {@code break} after the first match), because FEAT's
	 * forward-pruning test must examine every occurrence, not just a canonical
	 * (leftmost) one.
	 *
	 * IMPORTANT: entries are added grouped contiguously by original sequence id,
	 * which is an invariant relied upon by the O(1) distinct-support dedup in
	 * {@link PairData#addOccurrence(int, int, int, int)}.
	 *
	 * @param item        the (renamed) item to use to make the pseudo-projection
	 * @param sequenceIDs the set of sequence ids containing the item
	 * @param depth       the recursion depth (buffers are written directly into
	 *                    {@link #projSeqIDsBuffer}[depth] / {@link #projIndexesBuffer}[depth],
	 *                    growing them as needed)
	 * @return the number of pseudo-sequence entries added to the projected database
	 */
	private int buildProjectedDatabaseSingleItems(int item, List<Integer> sequenceIDs, int depth) {
		int seqIDsSize = sequenceIDs.size();
		List<int[]> sequences = sequenceDatabase.getSequences();
		int projSize = 0;

		for (int idx = 0; idx < seqIDsSize; idx++) {
			int sequenceID = sequenceIDs.get(idx);
			int[] sequence = sequences.get(sequenceID);
			if (sequence == null)
				continue;

			// scan every occurrence of item in this sequence
			for (int j = 0; sequence[j] != -2; j++) {
				int token = sequence[j];
				if (token == item) {
					if (sequence[j + 1] != -2) {
						ensureNextLevelCapacity(depth, projSize + 1);
						projSeqIDsBuffer[depth][projSize] = sequenceID;
						projIndexesBuffer[depth][projSize] = j + 1;
						projSize++;
					}
					// NOTE: intentionally no "break" here - we must keep scanning to find
					// every occurrence of "item" in this sequence.
				}
			}
		}
		return projSize;
	}

	/**
	 * Create the initial (depth 1) projected database for a given item, for
	 * databases that may contain multiple items per itemset. See
	 * {@link #buildProjectedDatabaseSingleItems(int, List, int)} for why every
	 * occurrence (not just the first) is kept, and for the sequence-grouping
	 * invariant that is relied upon elsewhere.
	 *
	 * @param item        the (renamed) item to use to make the pseudo-projection
	 * @param sequenceIDs the set of sequence ids containing the item
	 * @param depth       the recursion depth
	 * @return the number of pseudo-sequence entries added to the projected database
	 */
	private int buildProjectedDatabaseFirstTimeMultipleItems(int item, List<Integer> sequenceIDs, int depth) {
		int seqIDsSize = sequenceIDs.size();
		List<int[]> sequences = sequenceDatabase.getSequences();
		int projSize = 0;

		for (int idx = 0; idx < seqIDsSize; idx++) {
			int sequenceID = sequenceIDs.get(idx);
			int[] sequence = sequences.get(sequenceID);
			if (sequence == null)
				continue;

			// scan every occurrence of item in this sequence
			for (int j = 0; sequence[j] != -2; j++) {
				int token = sequence[j];
				if (token == item) {
					// only add if there is something remaining after this item
					boolean isEndOfSequence = sequence[j + 1] == -1 && sequence[j + 2] == -2;
					if (isEndOfSequence == false) {
						ensureNextLevelCapacity(depth, projSize + 1);
						projSeqIDsBuffer[depth][projSize] = sequenceID;
						projIndexesBuffer[depth][projSize] = j + 1;
						projSize++;
					}
					// NOTE: intentionally no "break" here.
				}
			}
		}
		return projSize;
	}

	/**
	 * Recursively grow a given sequential pattern by exploring single-item
	 * extensions, applying FEAT's forward and backward pruning logic.
	 *
	 * @param projSeqIDs         the sequence IDs of the current projected database
	 * @param projIndexes        the item indexes of the current projected database
	 * @param parentProjSeqIDs   the sequence IDs of the parent projected database
	 * @param parentProjIndexes  the item indexes of the parent projected database
	 * @param projSize           the number of entries in the projected database
	 * @param k                  the prefix length in terms of items
	 * @param lastBufferPosition the last position used in the buffer for the current prefix
	 * @param depth              the current recursion depth
	 * @param parentSupport      the distinct support of the parent prefix
	 * @param lastItem           the last item added to the prefix
	 * @throws IOException exception if there is an error writing to the output file
	 */
	private void featRecursionSingleItems(int[] projSeqIDs, int[] projIndexes, int[] parentProjSeqIDs,
			int[] parentProjIndexes, int projSize, int k, int lastBufferPosition, int depth, int parentSupport,
			int lastItem) throws IOException {

		ensureDepthBuffers(depth);

		// collect all candidate extensions from the projected database
		ItemPairArray itemsPseudoSequences = singleItemsArrays[depth];
		findAllFrequentPairsSingleItems(projSeqIDs, projIndexes, projSize, itemsPseudoSequences);

		int nextDepth = depth + 1;
		ensureDepthBuffers(nextDepth);

		for (int idx = 0; idx < itemsPseudoSequences.touchedCount; idx++) {
			int item = itemsPseudoSequences.touchedItems[idx];
			PairData pairData = itemsPseudoSequences.dataByItem[item];

			if (pairData != null && pairData.support >= minsuppAbsolute) {

				int childSupport = pairData.support;
				int childEntryCount = pairData.size;
				// update the pattern buffer with the new item
				int newBufferPosition = lastBufferPosition + 2;
				patternBuffer[lastBufferPosition + 1] = -1;
				patternBuffer[newBufferPosition] = item;

				// copy occurrence and parent data into the next depth's buffers
				ensureNextLevelCapacity(nextDepth, childEntryCount);
				System.arraycopy(pairData.seqIDs, 0, projSeqIDsBuffer[nextDepth], 0, childEntryCount);
				System.arraycopy(pairData.indexes, 0, projIndexesBuffer[nextDepth], 0, childEntryCount);
				System.arraycopy(pairData.parentSeqIDs, 0, parentProjSeqIDsBuffer[nextDepth], 0, childEntryCount);
				System.arraycopy(pairData.parentIndexes, 0, parentProjIndexesBuffer[nextDepth], 0, childEntryCount);

				boolean canPrune = false;
				boolean isGenerator = true;

				// check forward pruning if support did not decrease
				if (parentSupport == childSupport) {
					canPrune = checkForwardPruningGeneral(pairData.seqIDs, pairData.indexes, pairData.parentSeqIDs,
							pairData.parentIndexes, childEntryCount, item);
					isGenerator = false;
				}

				// check backward pruning if not already prunable
				if (!canPrune) {
					boolean[] res = checkBackwardPruning(k, childSupport, isGenerator);
					isGenerator = res[0];
					canPrune = res[1];
				}

				if (isGenerator) {
					savePatternMulti(newBufferPosition, pairData.distinctSeqIDs, childSupport);
				}

				// recurse unless pruning applies or max length reached
				if ((!performPruning || !canPrune) && k < maximumPatternLength) {
					featRecursionSingleItems(projSeqIDsBuffer[nextDepth], projIndexesBuffer[nextDepth],
							parentProjSeqIDsBuffer[nextDepth], parentProjIndexesBuffer[nextDepth], childEntryCount,
							k + 1, newBufferPosition, nextDepth, childSupport, item);
				} else {
					prefixPrunedCount++;
				}
			}
		}

		// reset all touched PairData objects for reuse
		for (int idx = 0; idx < itemsPseudoSequences.touchedCount; idx++) {
			PairData pd = itemsPseudoSequences.dataByItem[itemsPseudoSequences.touchedItems[idx]];
			if (pd != null)
				pd.reset();
		}
	}

	/**
	 * Recursively grow a given sequential pattern by exploring all extensions
	 * (i-extensions and s-extensions), applying FEAT's forward and backward pruning
	 * logic, used when the database has multiple items per itemset.
	 *
	 * @param projSeqIDs         the sequence IDs of the current projected database
	 * @param projIndexes        the item indexes of the current projected database
	 * @param parentProjSeqIDs   the sequence IDs of the parent projected database
	 * @param parentProjIndexes  the item indexes of the parent projected database
	 * @param projSize           the number of entries in the projected database
	 * @param k                  the prefix length in terms of items
	 * @param lastBufferPosition the last position used in the buffer for the current prefix
	 * @param depth              the current recursion depth
	 * @param parentSupport      the distinct support of the parent prefix
	 * @param lastItem           the last item added to the prefix
	 * @throws IOException exception if there is an error writing to the output file
	 */
	private void featRecursionMultipleItems(int[] projSeqIDs, int[] projIndexes, int[] parentProjSeqIDs,
			int[] parentProjIndexes, int projSize, int k, int lastBufferPosition, int depth, int parentSupport,
			int lastItem) throws IOException {

		ensureDepthBuffers(depth);

		// collect all candidate extensions from the projected database
		ArrayFrequentPairs mapsPairs = frequentPairsArrays[depth];
		findAllFrequentPairs(projSeqIDs, projIndexes, projSize, lastBufferPosition, mapsPairs);

		int nextDepth = depth + 1;
		ensureDepthBuffers(nextDepth);

		// process i-extensions (postfix: item added to the current itemset)
		ItemPairArray postfixArr = mapsPairs.pairsInPostfix;
		for (int idx = 0; idx < postfixArr.touchedCount; idx++) {
			int item = postfixArr.touchedItems[idx];
			PairData pairData = postfixArr.dataByItem[item];
			if (pairData == null || pairData.support < minsuppAbsolute)
				continue;

			int childSupport = pairData.support;
			int childEntryCount = pairData.size;
			// update the pattern buffer with the new item (i-extension, no separator)
			int newBufferPosition = lastBufferPosition + 1;
			patternBuffer[newBufferPosition] = item;

			// copy occurrence and parent data into the next depth's buffers
			ensureNextLevelCapacity(nextDepth, childEntryCount);
			System.arraycopy(pairData.seqIDs, 0, projSeqIDsBuffer[nextDepth], 0, childEntryCount);
			System.arraycopy(pairData.indexes, 0, projIndexesBuffer[nextDepth], 0, childEntryCount);
			System.arraycopy(pairData.parentSeqIDs, 0, parentProjSeqIDsBuffer[nextDepth], 0, childEntryCount);
			System.arraycopy(pairData.parentIndexes, 0, parentProjIndexesBuffer[nextDepth], 0, childEntryCount);

			boolean canPrune = false;
			boolean isGenerator = true;

			// check forward pruning if support did not decrease
			if (parentSupport == childSupport) {
				canPrune = checkForwardPruningGeneral(pairData.seqIDs, pairData.indexes, pairData.parentSeqIDs,
						pairData.parentIndexes, childEntryCount, item);
				isGenerator = false;
			}

			// check backward pruning if not already prunable
			if (!canPrune) {
				boolean[] res = checkBackwardPruning(k, childSupport, isGenerator);
				isGenerator = res[0];
				canPrune = res[1];
			}

			if (isGenerator) {
				savePatternMulti(newBufferPosition, pairData.distinctSeqIDs, childSupport);
			}

			// recurse unless pruning applies or max length reached
			if ((!performPruning || !canPrune) && k < maximumPatternLength) {
				featRecursionMultipleItems(projSeqIDsBuffer[nextDepth], projIndexesBuffer[nextDepth],
						parentProjSeqIDsBuffer[nextDepth], parentProjIndexesBuffer[nextDepth], childEntryCount, k + 1,
						newBufferPosition, nextDepth, childSupport, item);
			} else {
				prefixPrunedCount++;
			}
		}

		// process s-extensions (item added as a new itemset, separated by -1)
		ItemPairArray pairsArr = mapsPairs.pairs;
		for (int idx = 0; idx < pairsArr.touchedCount; idx++) {
			int item = pairsArr.touchedItems[idx];
			PairData pairData = pairsArr.dataByItem[item];
			if (pairData == null || pairData.support < minsuppAbsolute)
				continue;

			int childSupport = pairData.support;
			int childEntryCount = pairData.size;
			// update the pattern buffer with a separator then the new item (s-extension)
			int newBufferPosition = lastBufferPosition + 1;
			patternBuffer[newBufferPosition] = -1;
			newBufferPosition++;
			patternBuffer[newBufferPosition] = item;

			// copy occurrence and parent data into the next depth's buffers
			ensureNextLevelCapacity(nextDepth, childEntryCount);
			System.arraycopy(pairData.seqIDs, 0, projSeqIDsBuffer[nextDepth], 0, childEntryCount);
			System.arraycopy(pairData.indexes, 0, projIndexesBuffer[nextDepth], 0, childEntryCount);
			System.arraycopy(pairData.parentSeqIDs, 0, parentProjSeqIDsBuffer[nextDepth], 0, childEntryCount);
			System.arraycopy(pairData.parentIndexes, 0, parentProjIndexesBuffer[nextDepth], 0, childEntryCount);

			boolean canPrune = false;
			boolean isGenerator = true;

			// check forward pruning if support did not decrease
			if (parentSupport == childSupport) {
				canPrune = checkForwardPruningGeneral(pairData.seqIDs, pairData.indexes, pairData.parentSeqIDs,
						pairData.parentIndexes, childEntryCount, item);
				isGenerator = false;
			}

			// check backward pruning if not already prunable
			if (!canPrune) {
				boolean[] res = checkBackwardPruning(k, childSupport, isGenerator);
				isGenerator = res[0];
				canPrune = res[1];
			}

			if (isGenerator) {
				savePatternMulti(newBufferPosition, pairData.distinctSeqIDs, childSupport);
			}

			// recurse unless pruning applies or max length reached
			if ((!performPruning || !canPrune) && k < maximumPatternLength) {
				featRecursionMultipleItems(projSeqIDsBuffer[nextDepth], projIndexesBuffer[nextDepth],
						parentProjSeqIDsBuffer[nextDepth], parentProjIndexesBuffer[nextDepth], childEntryCount, k + 1,
						newBufferPosition, nextDepth, childSupport, item);
			} else {
				prefixPrunedCount++;
			}
		}

		// reset all touched PairData objects for reuse
		for (int idx = 0; idx < postfixArr.touchedCount; idx++) {
			PairData pd = postfixArr.dataByItem[postfixArr.touchedItems[idx]];
			if (pd != null)
				pd.reset();
		}
		for (int idx = 0; idx < pairsArr.touchedCount; idx++) {
			PairData pd = pairsArr.dataByItem[pairsArr.touchedItems[idx]];
			if (pd != null)
				pd.reset();
		}
	}

	/**
	 * This method checks the forward pruning for a sequence with a single item.
	 * NOTE: in the corrected version of FEAT2 this method is no longer used as
	 * a pruning criterion for 1-item prefixes (see prefixspanWithSingleItemsFEAT
	 * and prefixspanWithMultipleItemsFEAT). Forward ("forced extension") is
	 * now only checked at the level of concrete children via
	 * {@link #checkForwardPruningGeneral}. The method is kept for completeness
	 * but always returns {@code false}.
	 *
	 * @param item     the item being tested
	 * @param seqIDs   the sequence IDs of the projected database
	 * @param indexes  the item indexes of the projected database
	 * @param projSize the number of entries in the projected database
	 * @return always false
	 */
	@SuppressWarnings("unused")
	private boolean checkForwardPruningFor1Item(int item, int[] seqIDs, int[] indexes, int projSize) {
		return false;
	}

	/**
	 * Check if a prefix should be pruned due to a forced forward extension.
	 * For each (parent occurrence -> child occurrence) pair, the first item at
	 * the parent projection point must equal {@code item}, and the child
	 * projection point must be exactly one token after the parent projection point
	 * (skipping an itemset separator if any). If any pair violates this condition,
	 * the prefix cannot be pruned.
	 *
	 * @param childSeqIDs   the sequence IDs of the child occurrences
	 * @param childIndexes  the item indexes of the child occurrences
	 * @param parentSeqIDs  the sequence IDs of the parent occurrence that produced
	 *                      each child occurrence (parallel to childSeqIDs)
	 * @param parentIndexes the item indexes of the parent occurrence that produced
	 *                      each child occurrence (parallel to childIndexes)
	 * @param count         the number of (parent, child) occurrence pairs
	 * @param item          the extending item
	 * @return true if the prefix should be pruned
	 */
	private boolean checkForwardPruningGeneral(int[] childSeqIDs, int[] childIndexes, int[] parentSeqIDs,
			int[] parentIndexes, int count, int item) {
		List<int[]> sequences = sequenceDatabase.getSequences();

		for (int i = 0; i < count; i++) {
			int parentSeqID = parentSeqIDs[i];
			int parentIndex = parentIndexes[i];

			// invalid parent: cannot prune
			if (parentIndex < 0)
				return false;
			int[] parentSeq = sequences.get(parentSeqID);
			if (parentSeq[parentIndex] == -2)
				return false;

			// parent projection point must be the extending item
			if (parentSeq[parentIndex] != item)
				return false;

			// compute the expected child position (skip itemset separator if present)
			int expItem = parentIndex + 1;
			if (parentSeq[expItem] == -2)
				return false;
			if (parentSeq[expItem] == -1)
				expItem++;

			// child must be in the same sequence at the expected position
			if (childSeqIDs[i] != parentSeqID)
				return false;
			if (childIndexes[i] != expItem)
				return false;
		}
		return true;
	}

	/**
	 * Check if there is a backward extension that prevents the current pattern
	 * from being a generator, or that allows its subtree to be pruned.
	 * For each item position i in the pattern and each prefix length j, compares
	 * the projection sets of the prefix with and without item i. If they are
	 * identical, the subtree can be pruned. If the support of the prefix without
	 * item i equals the child support, the pattern is not a generator.
	 *
	 * @param prefixTotalItems the total number of items in the pattern
	 * @param childSupport     the distinct support of the current pattern
	 * @param isGeneratorParam whether the pattern is currently considered a generator
	 * @return a boolean array where [0] = isGenerator and [1] = canPrune
	 */
	private boolean[] checkBackwardPruning(int prefixTotalItems, int childSupport, boolean isGeneratorParam) {
		boolean isGenerator = isGeneratorParam;
		boolean canPrune = false;

		List<int[]> sequences = sequenceDatabase.getSequences();

		// precompute itemsets for all sequences to avoid redundant parsing
		List<List<int[]>> dbItemsets = new ArrayList<List<int[]>>();
		for (int i = 0; i < sequences.size(); i++) {
			int[] seq = sequences.get(i);
			if (seq != null) {
				dbItemsets.add(getSequenceItemsets(seq));
			}
		}

		int dbSize = dbItemsets.size();

		// for each sub-prefix length and each item position, test backward extension
		for (int len = 1; len < prefixTotalItems; len++) {
			// compute the end of the prefix of length 'len + 1'
			int prefixEnd = 0;
			int itemsFound = 0;
			while (itemsFound < len + 1) {
				if (patternBuffer[prefixEnd] > 0)
					itemsFound++;
				prefixEnd++;
			}

			List<int[]> prefixTruncated = getPatternItemsets(0, prefixEnd);

			for (int i = 0; i < len; i++) {
				List<int[]> prefixWithoutI = copyPrefixWithoutItem(prefixTruncated, i);

				boolean same = true;
				int sup2 = 0;
				int seqRemaining = dbSize;

				// compare projections with and without item i for each sequence
				for (int s = 0; s < dbItemsets.size(); s++) {
					seqRemaining--;
					List<int[]> seqItemsets = dbItemsets.get(s);

					int res = sameProjection(seqItemsets, prefixTruncated, prefixWithoutI);

					if (res == 1 || res == 3) { // SAME_PROJECTION or CONTAIN_PREFIX_WITHOUT_I
						sup2++;
					}

					if (res != 1 && res != 2) { // projection differs and not both absent
						same = false;
						if (!isGenerator) {
							break;
						} else if (sup2 + seqRemaining < childSupport) {
							break;
						}
					}
				}

				if (same) {
					canPrune = true;
					if (!isGenerator) {
						return new boolean[] { isGenerator, canPrune };
					}
				}
				if (sup2 == childSupport) {
					isGenerator = false;
				}
			}
		}

		return new boolean[] { isGenerator, canPrune };
	}

	/**
	 * Compute the projection endpoints for both a pattern and the pattern without
	 * a specific item in a sequence, and compare them. Returns an int code:
	 * 1 = SAME_PROJECTION, 2 = SAME_PROJECTION_NOT_CONTAINED_IN,
	 * 3 = CONTAIN_PREFIX_WITHOUT_I.
	 *
	 * @param seqItemsets    the itemsets of the sequence
	 * @param pattern        the full pattern itemsets
	 * @param patternWithoutI the pattern itemsets with one item removed
	 * @return the projection comparison code
	 */
	private int sameProjection(List<int[]> seqItemsets, List<int[]> pattern, List<int[]> patternWithoutI) {
		Set<Long> projWithI = findAllProjectionEndpoints(seqItemsets, pattern);
		Set<Long> projWithoutI = findAllProjectionEndpoints(seqItemsets, patternWithoutI);

		if (projWithI.equals(projWithoutI)) {
			if (projWithI.isEmpty()) {
				return 2; // SAME_PROJECTION_NOT_CONTAINED_IN
			}
			return 1; // SAME_PROJECTION
		}
		return 3; // CONTAIN_PREFIX_WITHOUT_I
	}

	/**
	 * Return every distinct pseudo-projection endpoint for a pattern in a sequence.
	 * Uses dynamic programming over possible ending itemsets, mirroring the
	 * original FEAT implementation exactly.
	 *
	 * @param seqItemsets the itemsets of the sequence
	 * @param pattern     the pattern itemsets
	 * @return the set of encoded (itemset index, item position) endpoints
	 */
	private Set<Long> findAllProjectionEndpoints(List<int[]> seqItemsets, List<int[]> pattern) {
		Set<Long> result = new HashSet<Long>();

		if (pattern.isEmpty()) {
			return result;
		}

		Set<Integer> previousEnds = new HashSet<Integer>();
		previousEnds.add(-1);

		int[] lastPatternItemset = null;

		// greedily match each pattern itemset and track all ending positions
		for (int[] patItemset : pattern) {
			if (patItemset.length == 0) {
				continue;
			}

			Set<Integer> currentEnds = new HashSet<Integer>();

			for (Integer previousEnd : previousEnds) {
				for (int s = previousEnd + 1; s < seqItemsets.size(); s++) {
					int[] seqItemset = seqItemsets.get(s);
					if (containsAll(seqItemset, patItemset)) {
						currentEnds.add(s);
					}
				}
			}

			if (currentEnds.isEmpty()) {
				return result;
			}

			previousEnds = currentEnds;
			lastPatternItemset = patItemset;
		}

		if (lastPatternItemset == null) {
			return result;
		}

		// encode each endpoint as (itemset index << 32 | item position)
		for (Integer endItemsetPos : previousEnds) {
			int[] seqItemset = seqItemsets.get(endItemsetPos);
			int endItemPos = -1;
			for (int pItem : lastPatternItemset) {
				int position = -1;
				for (int k = 0; k < seqItemset.length; k++) {
					if (seqItemset[k] == pItem) {
						position = k;
						break;
					}
				}
				if (position > endItemPos) {
					endItemPos = position;
				}
			}

			if (endItemPos >= 0) {
				result.add(((long) endItemsetPos << 32) | (endItemPos & 0xFFFFFFFFL));
			}
		}

		return result;
	}

	/**
	 * Check whether a sequence itemset contains all items of a pattern itemset.
	 *
	 * @param seqItemset the sequence itemset
	 * @param patItemset the pattern itemset
	 * @return true if all items of patItemset are found in seqItemset
	 */
	private boolean containsAll(int[] seqItemset, int[] patItemset) {
		for (int p : patItemset) {
			boolean found = false;
			for (int s : seqItemset) {
				if (s == p) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		return true;
	}

	/**
	 * Create a copy of the given pattern with the item at position {@code itemToRemove}
	 * (counted across all items in all itemsets) removed. Empty itemsets are dropped.
	 *
	 * @param pattern      the original pattern itemsets
	 * @param itemToRemove the zero-based index of the item to remove across all items
	 * @return the pattern itemsets with the specified item removed
	 */
	private List<int[]> copyPrefixWithoutItem(List<int[]> pattern, int itemToRemove) {
		List<int[]> result = new ArrayList<>();
		int itemIndex = 0;

		for (int[] itemset : pattern) {
			List<Integer> newItemset = new ArrayList<>();
			for (int item : itemset) {
				// skip the item at position itemToRemove
				if (itemIndex != itemToRemove) {
					newItemset.add(item);
				}
				itemIndex++;
			}
			// only add non-empty itemsets
			if (!newItemset.isEmpty()) {
				int[] arr = new int[newItemset.size()];
				for (int i = 0; i < newItemset.size(); i++)
					arr[i] = newItemset.get(i);
				result.add(arr);
			}
		}
		return result;
	}

	/**
	 * Extract the itemsets of the pattern currently stored in {@link #patternBuffer}
	 * between positions {@code bufStart} (inclusive) and {@code prefixEnd} (exclusive).
	 *
	 * @param bufStart  the start position in the buffer
	 * @param prefixEnd the end position (exclusive) in the buffer
	 * @return the list of itemsets as int arrays
	 */
	private List<int[]> getPatternItemsets(int bufStart, int prefixEnd) {
		List<int[]> itemsets = new ArrayList<>();
		int b = bufStart;
		while (b < prefixEnd) {
			// skip leading separators
			while (b < prefixEnd && patternBuffer[b] == -1)
				b++;
			if (b >= prefixEnd)
				break;
			// collect items until the next separator
			List<Integer> items = new ArrayList<>();
			while (b < prefixEnd && patternBuffer[b] != -1) {
				items.add(patternBuffer[b]);
				b++;
			}
			int[] arr = new int[items.size()];
			for (int j = 0; j < items.size(); j++)
				arr[j] = items.get(j);
			itemsets.add(arr);
		}
		return itemsets;
	}

	/**
	 * Parse a sequence array into a list of itemsets, where each itemset is an
	 * int array of items. For single-item databases, each positive token is its
	 * own itemset (separators were removed during pruning).
	 *
	 * @param sequence the sequence array (terminated by -2)
	 * @return the list of itemsets
	 */
	private List<int[]> getSequenceItemsets(int[] sequence) {
		List<int[]> itemsets = new ArrayList<>();
		int i = 0;
		if (containsItemsetsWithMultipleItems) {
			// parse itemsets delimited by -1
			while (sequence[i] != -2) {
				if (sequence[i] == -1) {
					i++;
					continue;
				}
				List<Integer> items = new ArrayList<>();
				while (sequence[i] > 0) {
					items.add(sequence[i]);
					i++;
				}
				int[] arr = new int[items.size()];
				for (int j = 0; j < items.size(); j++)
					arr[j] = items.get(j);
				itemsets.add(arr);
			}
		} else {
			// each positive token is its own itemset (no separators after pruning)
			while (sequence[i] != -2) {
				itemsets.add(new int[] { sequence[i] });
				i++;
			}
		}
		return itemsets;
	}

	/**
	 * Find all frequent items in a projected sequence database (single-item
	 * itemsets only). Results are written into the provided reusable
	 * {@link ItemPairArray}. Every occurrence of every item found in the remaining
	 * part of every pseudo-sequence is recorded as a separate entry (parallel-tracked
	 * with the exact parent projection point that produced it), since FEAT's
	 * forward-pruning test needs to see all occurrences, not just one per sequence.
	 *
	 * @param projSeqIDs  the sequence IDs of the current projected database
	 * @param projIndexes the item indexes of the current projected database
	 * @param projSize    the number of entries in the projected database
	 * @param outArray    the reusable array-based structure to populate
	 */
	protected void findAllFrequentPairsSingleItems(int[] projSeqIDs, int[] projIndexes, int projSize,
			ItemPairArray outArray) {

		List<int[]> dbSequences = sequenceDatabase.getSequences();
		outArray.touchedCount = 0;

		for (int seqIdx = 0; seqIdx < projSize; seqIdx++) {
			int sequenceID = projSeqIDs[seqIdx];
			int[] sequence = dbSequences.get(sequenceID);
			int parentIndex = projIndexes[seqIdx];

			// scan all items from the parent projection point onward
			for (int i = parentIndex; sequence[i] != -2; i++) {
				int token = sequence[i];

				if (token > 0) {
					PairData pairData = outArray.dataByItem[token];
					if (pairData == null) {
						pairData = new PairData();
						outArray.dataByItem[token] = pairData;
					}

					if (pairData.size == 0) {
						outArray.touchedItems[outArray.touchedCount++] = token;
					}

					// record this occurrence along with its parent projection point
					pairData.addOccurrence(sequenceID, i + 1, sequenceID, parentIndex);
				}
			}
		}

		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Contains two {@link ItemPairArray} structures used for counting item
	 * frequencies in projected databases when itemsets may have multiple items:
	 * one for s-extensions (new itemsets) and one for i-extensions (postfix itemsets).
	 */
	public static class ArrayFrequentPairs {
		/** candidate s-extensions (items in new itemsets) */
		public final ItemPairArray pairs;
		/** candidate i-extensions (items in the current postfix itemset) */
		public final ItemPairArray pairsInPostfix;

		/**
		 * Construct an ArrayFrequentPairs for the given number of frequent items.
		 *
		 * @param frequentItemCount the number of distinct frequent items
		 */
		public ArrayFrequentPairs(int frequentItemCount) {
			pairs = new ItemPairArray(frequentItemCount);
			pairsInPostfix = new ItemPairArray(frequentItemCount);
		}
	}

	/**
	 * A sparse array-based structure mapping a renamed (consecutive) item id
	 * directly to its {@link PairData} projection. Used instead of a
	 * {@code Map<Integer, PairData>} to avoid autoboxing and hashing overhead.
	 */
	static class ItemPairArray {
		/** projection data indexed by renamed item id */
		final PairData[] dataByItem;
		/** items that have been touched (have at least one occurrence) */
		final int[] touchedItems;
		/** number of touched items */
		int touchedCount;

		/**
		 * Construct an ItemPairArray for the given number of frequent items.
		 *
		 * @param frequentItemCount the number of distinct frequent items
		 */
		ItemPairArray(int frequentItemCount) {
			dataByItem = new PairData[frequentItemCount + 1];
			touchedItems = new int[frequentItemCount + 1];
			touchedCount = 0;
		}
	}

	/**
	 * Holds the projected database for a single item extension. Uses parallel int
	 * arrays instead of List&lt;PseudoSequence&gt; to reduce memory overhead.
	 *
	 * IMPORTANT: {@code seqIDs}/{@code indexes} hold ONE ENTRY PER OCCURRENCE of
	 * this item (i.e. the same sequence id can appear multiple times), and
	 * {@code parentSeqIDs}/{@code parentIndexes} record, for each such occurrence,
	 * the exact parent projection point that produced it (needed by FEAT's
	 * forward-pruning test). The DISTINCT sequence support is tracked separately
	 * in {@code distinctSeqIDs}/{@code support}.
	 *
	 * <p><b>Distinct-support dedup correctness:</b> a naive dedup using a single
	 * marker array shared across all items/PairData objects (indexed only by
	 * sequence id) is UNSOUND: a different item's PairData can overwrite another
	 * item's mark for the same sequence id, causing spurious re-counting later.
	 * Instead, this class relies on an invariant that holds by construction at
	 * every recursion depth of this algorithm: all occurrence-entries for a
	 * given original sequence id are always added *contiguously* to any given
	 * PairData. This makes a simple O(1) "does this occurrence's sequence id
	 * differ from the last occurrence added to *this* PairData" check both
	 * correct and sufficient, without needing any shared/global marker
	 * structure.</p>
	 *
	 * Supports reset() so the object can be reused across recursive calls at the
	 * same depth without reallocation.
	 */
	static class PairData {
		/** sequence IDs of all occurrences (one entry per occurrence) */
		int[] seqIDs;
		/** item positions of all occurrences, parallel to seqIDs */
		int[] indexes;
		/** parent sequence IDs, parallel to seqIDs */
		int[] parentSeqIDs;
		/** parent item positions, parallel to seqIDs */
		int[] parentIndexes;
		/** number of occurrences stored */
		int size;

		/** sequence IDs of distinct supporting sequences */
		int[] distinctSeqIDs;
		/** number of distinct supporting sequences */
		int support;

		/**
		 * The sequence id of the most recently added occurrence for THIS PairData
		 * (local to this object only - see class javadoc). Initialized to -1 so that
		 * the first occurrence added after a reset() is always counted as distinct.
		 */
		private int lastSeqID = -1;

		/** Construct an empty PairData with an initial capacity of 8. */
		PairData() {
			seqIDs = new int[8];
			indexes = new int[8];
			parentSeqIDs = new int[8];
			parentIndexes = new int[8];
			distinctSeqIDs = new int[8];
			size = 0;
			support = 0;
		}

		/**
		 * Add an occurrence of the item at the given position, tagged with its parent
		 * projection point. Automatically updates the distinct support count.
		 *
		 * @param seqID       the sequence id of this occurrence
		 * @param index       the item position of this occurrence (projection start)
		 * @param parentSeqID the sequence id of the parent projection point
		 * @param parentIndex the item position of the parent projection point
		 */
		void addOccurrence(int seqID, int index, int parentSeqID, int parentIndex) {
			// update distinct support if this is a new sequence id
			if (lastSeqID != seqID) {
				addDistinct(seqID);
				lastSeqID = seqID;
			}

			// grow arrays if needed
			if (size == seqIDs.length) {
				int newCap = size * 2;
				seqIDs = Arrays.copyOf(seqIDs, newCap);
				indexes = Arrays.copyOf(indexes, newCap);
				parentSeqIDs = Arrays.copyOf(parentSeqIDs, newCap);
				parentIndexes = Arrays.copyOf(parentIndexes, newCap);
			}
			seqIDs[size] = seqID;
			indexes[size] = index;
			parentSeqIDs[size] = parentSeqID;
			parentIndexes[size] = parentIndex;
			size++;
		}

		/**
		 * Record a new distinct supporting sequence.
		 *
		 * @param seqID the sequence id to add
		 */
		private void addDistinct(int seqID) {
			if (support == distinctSeqIDs.length) {
				distinctSeqIDs = Arrays.copyOf(distinctSeqIDs, distinctSeqIDs.length * 2);
			}
			distinctSeqIDs[support++] = seqID;
		}

		/** Reset this object for reuse at the same depth, without reallocation. */
		void reset() {
			size = 0;
			support = 0;
			lastSeqID = -1;
		}
	}

	/**
	 * Find all frequent items in a projected sequence database for the
	 * multiple-items-per-itemset case. Results are written into the provided
	 * reusable {@link ArrayFrequentPairs}. For each pseudo-sequence entry, items
	 * found in the remaining part of the sequence are classified as i-extension
	 * candidates (postfix itemset) or s-extension candidates (new itemset).
	 *
	 * @param projSeqIDs         the sequence IDs of the current projected database
	 * @param projIndexes        the item indexes of the current projected database
	 * @param projSize           the number of entries in the projected database
	 * @param lastBufferPosition the last position used in the buffer for the current prefix
	 * @param mapsPairs          the reusable ArrayFrequentPairs to populate
	 */
	protected void findAllFrequentPairs(int[] projSeqIDs, int[] projIndexes, int projSize, int lastBufferPosition,
			ArrayFrequentPairs mapsPairs) {

		// find the start of the last itemset in the pattern buffer
		int firstPositionOfLastItemsetInBuffer = lastBufferPosition;
		while (lastBufferPosition > 0) {
			firstPositionOfLastItemsetInBuffer--;
			if (firstPositionOfLastItemsetInBuffer < 0 || patternBuffer[firstPositionOfLastItemsetInBuffer] == -1) {
				firstPositionOfLastItemsetInBuffer++;
				break;
			}
		}

		List<int[]> dbSequences = sequenceDatabase.getSequences();

		ItemPairArray pairsArr = mapsPairs.pairs;
		ItemPairArray postfixArr = mapsPairs.pairsInPostfix;
		pairsArr.touchedCount = 0;
		postfixArr.touchedCount = 0;

		for (int seqIdx = 0; seqIdx < projSize; seqIdx++) {
			int sequenceID = projSeqIDs[seqIdx];
			int[] sequence = dbSequences.get(sequenceID);
			int parentIndex = projIndexes[seqIdx];

			int positionToBeMatched = firstPositionOfLastItemsetInBuffer;

			// determine if we are currently inside a postfix itemset
			int previousItem = sequence[parentIndex - 1];
			boolean currentItemsetIsPostfix = (previousItem != -1);
			boolean isFirstItemset = true;

			// scan forward in the sequence from the projection point
			for (int i = parentIndex; sequence[i] != -2; i++) {
				int token = sequence[i];

				if (token > 0) {
					// add to postfix or s-extension array based on current itemset type
					ItemPairArray targetArr = currentItemsetIsPostfix ? postfixArr : pairsArr;
					PairData pairData = targetArr.dataByItem[token];
					if (pairData == null) {
						pairData = new PairData();
						targetArr.dataByItem[token] = pairData;
					}
					if (pairData.size == 0) {
						targetArr.touchedItems[targetArr.touchedCount++] = token;
					}
					pairData.addOccurrence(sequenceID, i + 1, sequenceID, parentIndex);

					if (currentItemsetIsPostfix && isFirstItemset == false) {
						// NOTE: with the current classification logic, this branch is
						// unreachable (currentItemsetIsPostfix can only be true again
						// after a fresh S-extension match completes within the very
						// first itemset scanned after a "-1"). It is kept for
						// structural fidelity with the original FEAT logic and is
						// harmless either way.
						PairData pairData2 = pairsArr.dataByItem[token];
						if (pairData2 == null) {
							pairData2 = new PairData();
							pairsArr.dataByItem[token] = pairData2;
						}
						if (pairData2.size == 0) {
							pairsArr.touchedItems[pairsArr.touchedCount++] = token;
						}
						pairData2.addOccurrence(sequenceID, i + 1, sequenceID, parentIndex);
					}

					// advance postfix matching position if applicable
					if (currentItemsetIsPostfix == false && patternBuffer[positionToBeMatched] == token) {
						positionToBeMatched++;
						if (positionToBeMatched > lastBufferPosition) {
							currentItemsetIsPostfix = true;
						}
					}

				} else if (token == -1) {
					// crossed an itemset boundary: next items are s-extensions
					isFirstItemset = false;
					currentItemsetIsPostfix = false;
					positionToBeMatched = firstPositionOfLastItemsetInBuffer;
				}
			}
		}
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Print statistics about the algorithm execution to System.out.
	 */
	public void printStatistics() {
		StringBuilder r = new StringBuilder(200);
		r.append("============= FEAT 2.67 - STATISTICS =============\n Total time ~ ");
		r.append(totalTime);
		r.append(" ms\n");
		r.append(" Frequent sequences count : ").append(getPatternCount());
		r.append(" + 1 (the empty sequence) \n");
		r.append(" Prefix pruned count: ").append(prefixPrunedCount).append('\n');
		r.append(" Max memory (mb) : ").append(MemoryLogger.getInstance().getMaxMemory()).append('\n');
		r.append("===================================================\n");
		System.out.println(r.toString());
	}

	/**
	 * Get the number of generator patterns found. Note that this method does not
	 * count the empty sequence.
	 *
	 * @return the number of generators
	 */
	public long getPatternCount() {
		return generators == null ? 0 : generators.size();
	}

	/**
	 * Get the maximum length of patterns to be found (in terms of item count).
	 *
	 * @return the maximumPatternLength
	 */
	public int getMaximumPatternLength() {
		return maximumPatternLength;
	}

	/**
	 * Set the maximum length of patterns to be found (in terms of item count).
	 *
	 * @param maximumPatternLength the maximumPatternLength to set
	 */
	public void setMaximumPatternLength(int maximumPatternLength) {
		this.maximumPatternLength = maximumPatternLength;
	}

	/**
	 * Set whether the sequence identifiers should be shown for each pattern found.
	 *
	 * @param showSequenceIdentifiers true to show sequence identifiers, false otherwise
	 */
	public void setShowSequenceIdentifiers(boolean showSequenceIdentifiers) {
		this.showSequenceIdentifiers = showSequenceIdentifiers;
	}

	/**
	 * Set whether FEAT's forward and backward pruning should be applied.
	 *
	 * @param performPruning true to enable pruning, false to disable it
	 */
	public void setPerformPruning(boolean performPruning) {
		this.performPruning = performPruning;
	}

	/**
	 * Write the result to an output file.
	 *
	 * @param path the output file path
	 * @throws IOException exception if an error occur when writing the file
	 */
	public void writeResultTofile(String path) throws IOException {
		BufferedWriter w = new BufferedWriter(new FileWriter(path));
		if (generators != null) {
			for (SequentialPattern pattern : generators) {
				StringBuilder buffer = new StringBuilder();
				// write each itemset followed by a separator
				for (Itemset itemset : pattern.getItemsets()) {
					for (Integer item : itemset.getItems()) {
						buffer.append(item).append(' ');
					}
					buffer.append("-1 ");
				}
				buffer.append("#SUP: ");
				buffer.append(pattern.getAbsoluteSupport());
				if (showSequenceIdentifiers) {
					buffer.append(" #SID: ");
					for (Integer sid : pattern.getSequenceIDs()) {
						buffer.append(sid).append(' ');
					}
				}
				w.write(buffer.toString());
				w.newLine();
			}
		}
		w.close();
	}
}