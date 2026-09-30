package ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;

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
 * Implementation of the TSP_nonClosed algorithm for mining top-k frequent
 * sequential patterns that are not necessarily closed.
 * <br/><br/>
 * The original TSP algorithm was proposed in:
 * <br/>
 * Petre Tzvetkov, Xifeng Yan, Jiawei Han: TSP: Mining top-k closed sequential
 * patterns. Knowledge and Information Systems 7(4): 438-457, 2005.
 * <br/><br/>
 * This implementation supports optional minimum and maximum pattern length
 * constraints (in terms of number of items), which can be set using
 * {@link #setMinimumPatternLength(int)} and {@link #setMaximumPatternLength(int)}.
 *
 * @author Philippe Fournier-Viger et al.
 */
public class AlgoTSP_nonClosed2 {

	/** Timestamp recorded when the algorithm starts. */
	private long startTime;

	/** Timestamp recorded when the algorithm ends. */
	private long endTime;

	/** Current absolute minimum support, raised dynamically as top-k fills up. */
	private int minsuppAbsolute;

	/** The number of top-k patterns requested by the user. */
	private int k = 0;

	/** Min-heap of the top-k patterns found so far, ordered by increasing support. */
	private PriorityQueue<SequentialPattern> kPatterns;

	/** Max-heap of candidates waiting to be expanded, ordered by decreasing support. */
	private PriorityQueue<Candidate> candidates;

	/** If true, sequence identifiers are included in the output for each pattern. */
	private boolean showSequenceIdentifiers = false;

	/** Minimum pattern length in terms of item count. Default: 0 (no constraint). */
	private int minimumPatternLength = 0;

	/** Maximum pattern length in terms of item count. Default: infinity (no constraint). */
	private int maximumPatternLength = Integer.MAX_VALUE;

	/** Number of sequences in the input database. */
	private int sequenceCount = 0;

	/** The sequence database being mined. */
	private SequenceDatabase sequenceDatabase;

	/** True if the database contains at least one itemset with multiple items. */
	private boolean containsItemsetsWithMultipleItems = false;

	/**
	 * Converts original item ids to consecutive integers before mining,
	 * and restores them when patterns are finalized.
	 */
	private ItemNameConverter itemNameConverter;

	/** Number of distinct frequent (renamed) single items found in the database. */
	private int frequentItemCount;

	/**
	 * Reusable counting structure for single-item extensions, used when all
	 * itemsets contain exactly one item (only s-extensions needed).
	 */
	private ItemPairArray singleItemsArray;

	/**
	 * Reusable counting structure for both i-extensions and s-extensions, used
	 * when some itemsets may contain multiple items.
	 */
	private ArrayFrequentPairs frequentPairsArray;

	/** Counter used to assign a unique, increasing id to each candidate for tie-breaking. */
	private long candidateId = 0;

	/**
	 * Default constructor.
	 */
	public AlgoTSP_nonClosed2() {
	}

	/**
	 * Run the algorithm from an input file path.
	 *
	 * @param inputFile path to the input sequence database file
	 * @param k         number of top patterns to mine
	 * @return the top-k sequential patterns found, ordered by increasing support
	 * @throws IOException if an error occurs while reading the input file
	 */
	public PriorityQueue<SequentialPattern> runAlgorithm(String inputFile, int k) throws IOException {
		SequenceDatabase database = new SequenceDatabase();
		database.loadFile(inputFile);
		return runAlgorithm(database, k);
	}

	/**
	 * Run the algorithm on a given sequence database.
	 * Note: the database is modified in-place (infrequent items are removed
	 * and frequent items are renamed to consecutive integers).
	 *
	 * @param database a sequence database to mine
	 * @param k        number of top patterns to mine
	 * @return the top-k sequential patterns found, ordered by increasing support
	 * @throws IOException if an error occurs during mining
	 */
	public PriorityQueue<SequentialPattern> runAlgorithm(SequenceDatabase database, int k) throws IOException {
		MemoryLogger.getInstance().reset();

		// Initialize algorithm state
		this.k = k;
		this.sequenceDatabase = database;
		this.sequenceCount = database.size();
		this.containsItemsetsWithMultipleItems = false;
		this.minsuppAbsolute = 1;
		this.candidateId = 0;

		// Min-heap: poll() removes the pattern with the lowest support
		kPatterns = new PriorityQueue<SequentialPattern>(11, new Comparator<SequentialPattern>() {
			@Override
			public int compare(SequentialPattern p1, SequentialPattern p2) {
				return p1.getAbsoluteSupport() - p2.getAbsoluteSupport();
			}
		});

		// Max-heap: poll() removes the candidate with the highest support; ties broken by creation order
		candidates = new PriorityQueue<Candidate>(11, new Comparator<Candidate>() {
			@Override
			public int compare(Candidate c1, Candidate c2) {
				if (c1.support != c2.support) {
					return c2.support - c1.support;
				}
				return Long.compare(c1.id, c2.id);
			}
		});

		startTime = System.currentTimeMillis();

		// Run the main mining procedure only if meaningful input is provided
		if (k > 0 && sequenceCount > 0) {
			prefixSpan();
		}

		endTime = System.currentTimeMillis();

		// Release memory held by internal structures
		itemNameConverter = null;
		singleItemsArray = null;
		frequentPairsArray = null;
		sequenceDatabase = null;

		return kPatterns;
	}

	/**
	 * Main mining procedure. Prepares the database (finds frequent items, renames
	 * them, prunes infrequent ones), then explores candidates in best-first order
	 * (highest support first) as required by the top-k TSP strategy.
	 *
	 * @throws IOException if an error occurs during mining
	 */
	private void prefixSpan() throws IOException {
		// Find all items and the sequence IDs where they appear
		Map<Integer, List<Integer>> mapSequenceID = findSequencesContainingItems();

		// Save all size-1 patterns that meet the current minimum support threshold
		// and the minimum pattern length constraint
		for (Entry<Integer, List<Integer>> entry : mapSequenceID.entrySet()) {
			if (entry.getValue().size() >= minsuppAbsolute && minimumPatternLength <= 1) {
				saveSingleItemPattern(entry.getKey(), entry.getValue());
			}
		}

		// Count how many items are still frequent after the threshold may have been raised
		frequentItemCount = 0;
		for (List<Integer> sequenceIDs : mapSequenceID.values()) {
			if (sequenceIDs.size() >= minsuppAbsolute) {
				frequentItemCount++;
			}
		}

		// Stop early if no items can be extended or the user only wants length-1 patterns
		if (frequentItemCount == 0 || maximumPatternLength <= 1) {
			return;
		}

		// Assign new consecutive integer names to frequent items
		itemNameConverter = new ItemNameConverter(frequentItemCount);
		for (Entry<Integer, List<Integer>> entry : mapSequenceID.entrySet()) {
			if (entry.getValue().size() >= minsuppAbsolute) {
				itemNameConverter.assignNewName(entry.getKey());
			}
		}

		// Prune infrequent items from the database and rename frequent ones in-place
		if (containsItemsetsWithMultipleItems) {
			removeInfrequentItemsAndRenameForMultipleItems();
			frequentPairsArray = new ArrayFrequentPairs(frequentItemCount);
		} else {
			removeInfrequentItemsAndRenameForSingleItems();
			singleItemsArray = new ItemPairArray(frequentItemCount);
		}

		// Create one initial candidate per frequent single item
		createInitialCandidates(mapSequenceID);

		// Expand candidates in best-first order until none remain or all fall below minsup
		while (!candidates.isEmpty()) {
			Candidate candidate = candidates.poll();

			// Stop if the best remaining candidate is below the current threshold (anti-monotonicity)
			if (candidate.support < minsuppAbsolute) {
				break;
			}

			if (containsItemsetsWithMultipleItems) {
				expandCandidateWithMultipleItems(candidate);
			} else {
				expandCandidateWithSingleItems(candidate);
			}
		}
	}

	/**
	 * Create one initial candidate for each frequent single item.
	 *
	 * @param mapSequenceID map from original item ids to the sequence IDs where they appear
	 */
	private void createInitialCandidates(Map<Integer, List<Integer>> mapSequenceID) {
		// Temporary buffers reused for each item's initial projected database
		int[] tempSeqIDs = new int[sequenceCount];
		int[] tempIndexes = new int[sequenceCount];

		for (Entry<Integer, List<Integer>> entry : mapSequenceID.entrySet()) {
			List<Integer> sequenceIDs = entry.getValue();
			int support = sequenceIDs.size();

			// Skip items that no longer meet the minimum support threshold
			if (support < minsuppAbsolute) {
				continue;
			}

			int newItem = itemNameConverter.toNewName(entry.getKey());
			if (newItem == 0) {
				continue;
			}

			// Build the projected database for this item
			int projSize;
			if (containsItemsetsWithMultipleItems) {
				projSize = buildProjectedDatabaseFirstTimeMultipleItems(newItem, sequenceIDs, tempSeqIDs, tempIndexes);
			} else {
				projSize = buildProjectedDatabaseSingleItems(newItem, sequenceIDs, tempSeqIDs, tempIndexes);
			}

			if (projSize > 0) {
				int[] pattern = new int[] { newItem };
				// Register the size-1 pattern as a candidate for further expansion
				// (this is done regardless of the minimum pattern length constraint,
				// since longer patterns may still need to be explored from here)
				registerAsCandidate(new Candidate(pattern, 0, 0, 1, support, tempSeqIDs, tempIndexes, projSize,
						candidateId++));
			}
		}
	}

	/**
	 * Save a single-item pattern using its original (non-renamed) item id.
	 *
	 * @param item        the original item id
	 * @param sequenceIDs the sequence IDs where the item appears
	 */
	private void saveSingleItemPattern(int item, List<Integer> sequenceIDs) {
		SequentialPattern pattern = new SequentialPattern();
		pattern.addItemset(new Itemset(item));
		pattern.setSequenceIDs(sequenceIDs);
		save(pattern);
	}

	/**
	 * Add a pattern to the current top-k set, evicting weaker patterns if needed
	 * and raising the minimum support threshold accordingly.
	 * The caller must ensure that the pattern's support is >= {@link #minsuppAbsolute}.
	 *
	 * @param pattern the pattern to save
	 */
	private void save(SequentialPattern pattern) {
		if (k <= 0) {
			return;
		}

		// Add the pattern to the top-k set
		kPatterns.add(pattern);

		// If the set exceeds k, remove the weakest patterns until exactly k remain
		if (kPatterns.size() > k) {
			if (pattern.getAbsoluteSupport() > minsuppAbsolute) {
				do {
					kPatterns.poll();
				} while (kPatterns.size() > k);
			}
			// Raise the threshold to the lowest support still in the top-k set
			minsuppAbsolute = kPatterns.peek().getAbsoluteSupport();
		}
	}

	/**
	 * Build a {@link SequentialPattern} from a renamed-item buffer and save it in
	 * the top-k set. The caller must ensure that {@code supportSize >= minsuppAbsolute}.
	 * The pattern is only saved if its length (number of items) respects the
	 * minimum pattern length constraint (the maximum length constraint is already
	 * guaranteed by the caller, since candidates are never expanded past it).
	 *
	 * @param buffer      pattern buffer of renamed item ids, with -1 as itemset separator
	 * @param lastPos     last valid position in the buffer for this pattern
	 * @param seqIDs      sequence IDs of this pattern's projected database
	 * @param supportSize support of the pattern (number of sequences)
	 * @param itemCount   the number of items contained in the pattern
	 */
	private void savePattern(int[] buffer, int lastPos, int[] seqIDs, int supportSize, int itemCount) {
		// Enforce the minimum pattern length constraint
		if (itemCount < minimumPatternLength) {
			return;
		}

		SequentialPattern pattern = new SequentialPattern();

		// Reconstruct the pattern itemset by itemset, converting renamed ids back to originals
		Itemset currentItemset = new Itemset();
		for (int i = 0; i <= lastPos; i++) {
			int token = buffer[i];

			if (token > 0) {
				currentItemset.addItem(itemNameConverter.toOldName(token));
			} else if (token == -1) {
				pattern.addItemset(currentItemset);
				currentItemset = new Itemset();
			}
		}
		pattern.addItemset(currentItemset);

		// Copy the sequence IDs into a list of the exact required size
		List<Integer> sequenceIDs = new ArrayList<Integer>(supportSize);
		for (int i = 0; i < supportSize; i++) {
			sequenceIDs.add(seqIDs[i]);
		}
		pattern.setSequenceIDs(sequenceIDs);

		save(pattern);
	}

	/**
	 * Add a candidate to the priority queue for future expansion.
	 *
	 * @param candidate the candidate to register
	 */
	private void registerAsCandidate(Candidate candidate) {
		candidates.add(candidate);
	}

	/**
	 * Expand a candidate by generating all frequent s-extensions, for the optimized
	 * case where every itemset contains exactly one item.
	 *
	 * @param candidate the candidate to expand
	 */
	private void expandCandidateWithSingleItems(Candidate candidate) {
		if (candidate.itemCount >= maximumPatternLength) {
			return;
		}

		// Count all frequent single-item extensions in the projected database
		findAllFrequentPairsSingleItems(candidate.projSeqIDs, candidate.projIndexes, candidate.projSize,
				singleItemsArray);

		for (int idx = 0; idx < singleItemsArray.touchedCount; idx++) {
			int item = singleItemsArray.touchedItems[idx];
			PairData pairData = singleItemsArray.dataByItem[item];

			if (pairData.projSize >= minsuppAbsolute) {
				// Build the child pattern by appending -1 (itemset separator) and the new item
				int[] childPattern = new int[candidate.pattern.length + 2];
				System.arraycopy(candidate.pattern, 0, childPattern, 0, candidate.pattern.length);

				int newLastPos = candidate.lastBufferPosition + 2;
				childPattern[candidate.lastBufferPosition + 1] = -1;
				childPattern[newLastPos] = item;

				// Save (subject to the minimum pattern length constraint) and register
				// the child pattern as a candidate if within the maximum length limit
				savePattern(childPattern, newLastPos, pairData.seqIDs, pairData.projSize, candidate.itemCount + 1);

				if (candidate.itemCount + 1 < maximumPatternLength) {
					registerAsCandidate(new Candidate(childPattern, newLastPos, newLastPos, candidate.itemCount + 1,
							pairData.projSize, pairData.seqIDs, pairData.indexes, pairData.projSize, candidateId++));
				}
			}
		}

		// Reset all touched PairData objects so they can be reused for the next expansion
		for (int idx = 0; idx < singleItemsArray.touchedCount; idx++) {
			singleItemsArray.dataByItem[singleItemsArray.touchedItems[idx]].reset();
		}
	}

	/**
	 * Expand a candidate by generating all frequent i-extensions and s-extensions,
	 * for the general case where itemsets may contain multiple items.
	 *
	 * @param candidate the candidate to expand
	 */
	private void expandCandidateWithMultipleItems(Candidate candidate) {
		if (candidate.itemCount >= maximumPatternLength) {
			return;
		}

		// Count all frequent i-extensions and s-extensions in the projected database
		findAllFrequentPairs(candidate.pattern, candidate.firstPositionOfLastItemset, candidate.projSeqIDs,
				candidate.projIndexes, candidate.projSize, candidate.lastBufferPosition, frequentPairsArray);

		// Process i-extensions: append item to the last itemset of the pattern
		ItemPairArray postfixArray = frequentPairsArray.pairsInPostfix;
		for (int idx = 0; idx < postfixArray.touchedCount; idx++) {
			int item = postfixArray.touchedItems[idx];
			PairData pairData = postfixArray.dataByItem[item];

			if (pairData.projSize >= minsuppAbsolute) {
				// Build the child pattern by appending the item to the current last itemset
				int[] childPattern = new int[candidate.pattern.length + 1];
				System.arraycopy(candidate.pattern, 0, childPattern, 0, candidate.pattern.length);

				int newLastPos = candidate.lastBufferPosition + 1;
				childPattern[newLastPos] = item;

				savePattern(childPattern, newLastPos, pairData.seqIDs, pairData.projSize, candidate.itemCount + 1);

				if (candidate.itemCount + 1 < maximumPatternLength) {
					registerAsCandidate(new Candidate(childPattern, newLastPos, candidate.firstPositionOfLastItemset,
							candidate.itemCount + 1, pairData.projSize, pairData.seqIDs, pairData.indexes,
							pairData.projSize, candidateId++));
				}
			}
		}

		// Process s-extensions: append item as a new itemset after a -1 separator
		ItemPairArray pairsArray = frequentPairsArray.pairs;
		for (int idx = 0; idx < pairsArray.touchedCount; idx++) {
			int item = pairsArray.touchedItems[idx];
			PairData pairData = pairsArray.dataByItem[item];

			if (pairData.projSize >= minsuppAbsolute) {
				// Build the child pattern by appending -1 and the new item as a new itemset
				int[] childPattern = new int[candidate.pattern.length + 2];
				System.arraycopy(candidate.pattern, 0, childPattern, 0, candidate.pattern.length);

				int newLastPos = candidate.lastBufferPosition + 2;
				childPattern[candidate.lastBufferPosition + 1] = -1;
				childPattern[newLastPos] = item;

				savePattern(childPattern, newLastPos, pairData.seqIDs, pairData.projSize, candidate.itemCount + 1);

				if (candidate.itemCount + 1 < maximumPatternLength) {
					registerAsCandidate(new Candidate(childPattern, newLastPos, newLastPos, candidate.itemCount + 1,
							pairData.projSize, pairData.seqIDs, pairData.indexes, pairData.projSize, candidateId++));
				}
			}
		}

		// Reset all touched PairData objects for reuse in subsequent expansions
		for (int idx = 0; idx < postfixArray.touchedCount; idx++) {
			postfixArray.dataByItem[postfixArray.touchedItems[idx]].reset();
		}
		for (int idx = 0; idx < pairsArray.touchedCount; idx++) {
			pairsArray.dataByItem[pairsArray.touchedItems[idx]].reset();
		}
	}

	/**
	 * Scan the database to find, for each item, the list of sequence IDs where it appears.
	 * Also detects whether any itemset contains more than one item.
	 *
	 * @return a map from item id to the list of sequence IDs containing that item
	 */
	private Map<Integer, List<Integer>> findSequencesContainingItems() {
		Map<Integer, List<Integer>> mapSequenceID = new HashMap<Integer, List<Integer>>();
		List<int[]> sequences = sequenceDatabase.getSequences();

		for (int i = 0, dbSize = sequences.size(); i < dbSize; i++) {
			int[] sequence = sequences.get(i);
			int itemCountInCurrentItemset = 0;

			for (int j = 0, seqLen = sequence.length; j < seqLen; j++) {
				int token = sequence[j];

				if (token > 0) {
					// Register this sequence ID for the item (avoid duplicates)
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
	 * Remove infrequent items and rename frequent ones in-place, for databases
	 * where every itemset contains exactly one item.
	 */
	private void removeInfrequentItemsAndRenameForSingleItems() {
		List<int[]> sequences = sequenceDatabase.getSequences();

		for (int i = 0, size = sequences.size(); i < size; i++) {
			int[] sequence = sequences.get(i);
			int currentPosition = 0;

			for (int j = 0, seqLen = sequence.length; j < seqLen; j++) {
				int token = sequence[j];

				if (token > 0) {
					// Keep only frequent items and write their new name in-place
					int newName = itemNameConverter.toNewName(token);
					if (newName != 0) {
						sequence[currentPosition++] = newName;
					}
				} else if (token == -2) {
					// Trim the sequence to its compacted length or mark it as empty
					if (currentPosition > 0) {
						sequence[currentPosition] = -2;
						int[] newSequence = new int[currentPosition + 1];
						System.arraycopy(sequence, 0, newSequence, 0, currentPosition + 1);
						sequences.set(i, newSequence);
					} else {
						sequences.set(i, null);
					}
					break;
				}
			}
		}
	}

	/**
	 * Remove infrequent items and rename frequent ones in-place, for databases
	 * where some itemsets may contain multiple items.
	 */
	private void removeInfrequentItemsAndRenameForMultipleItems() {
		List<int[]> sequences = sequenceDatabase.getSequences();

		for (int i = 0, size = sequences.size(); i < size; i++) {
			int[] sequence = sequences.get(i);
			int currentPosition = 0;
			int currentItemsetItemCount = 0;

			for (int j = 0, seqLen = sequence.length; j < seqLen; j++) {
				int token = sequence[j];

				if (token > 0) {
					// Keep only frequent items and write their new name in-place
					int newName = itemNameConverter.toNewName(token);
					if (newName != 0) {
						sequence[currentPosition++] = newName;
						currentItemsetItemCount++;
					}
				} else if (token == -1) {
					// Write itemset separator only if the current itemset is non-empty
					if (currentItemsetItemCount > 0) {
						sequence[currentPosition++] = -1;
						currentItemsetItemCount = 0;
					}
				} else if (token == -2) {
					// Trim the sequence or mark it as empty
					if (currentPosition > 0) {
						sequence[currentPosition] = -2;
						int[] newSequence = new int[currentPosition + 1];
						System.arraycopy(sequence, 0, newSequence, 0, currentPosition + 1);
						sequences.set(i, newSequence);
					} else {
						sequences.set(i, null);
					}
					break;
				}
			}
		}
	}

	/**
	 * Build a projected database for a given item, for databases where every itemset
	 * has a single item. The projection starts immediately after the first occurrence
	 * of the item in each sequence.
	 *
	 * @param item        the renamed item to project on
	 * @param sequenceIDs sequence IDs containing the item
	 * @param outSeqIDs   output array populated with the projected sequence IDs
	 * @param outIndexes  output array populated with the projected item indexes
	 * @return the number of entries written to the output arrays
	 */
	private int buildProjectedDatabaseSingleItems(int item, List<Integer> sequenceIDs, int[] outSeqIDs,
			int[] outIndexes) {
		int projSize = 0;
		List<int[]> sequences = sequenceDatabase.getSequences();

		for (int idx = 0, seqIDsSize = sequenceIDs.size(); idx < seqIDsSize; idx++) {
			int sequenceID = sequenceIDs.get(idx);
			int[] sequence = sequences.get(sequenceID);

			// Find the first occurrence of the item and project right after it
			for (int j = 0; sequence[j] != -2; j++) {
				if (sequence[j] == item) {
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
	 * Build the initial projected database for a given item, for databases where
	 * itemsets may contain multiple items. The projection starts right after the
	 * first occurrence of the item in each sequence.
	 *
	 * @param item        the renamed item to project on
	 * @param sequenceIDs sequence IDs containing the item
	 * @param outSeqIDs   output array populated with the projected sequence IDs
	 * @param outIndexes  output array populated with the projected item indexes
	 * @return the number of entries written to the output arrays
	 */
	private int buildProjectedDatabaseFirstTimeMultipleItems(int item, List<Integer> sequenceIDs, int[] outSeqIDs,
			int[] outIndexes) {
		int projSize = 0;
		List<int[]> sequences = sequenceDatabase.getSequences();

		for (int idx = 0, seqIDsSize = sequenceIDs.size(); idx < seqIDsSize; idx++) {
			int sequenceID = sequenceIDs.get(idx);
			int[] sequence = sequences.get(sequenceID);

			// Find the first occurrence of the item and project right after it (unless end of sequence)
			for (int j = 0; sequence[j] != -2; j++) {
				if (sequence[j] == item) {
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
	 * Find all frequent single-item s-extensions in a projected database, for the
	 * optimized case where every itemset has exactly one item.
	 *
	 * @param projSeqIDs  sequence IDs of the projected database
	 * @param projIndexes item indexes of the projected database
	 * @param projSize    number of entries in the projected database
	 * @param outArray    reusable structure to populate with the results
	 */
	protected void findAllFrequentPairsSingleItems(int[] projSeqIDs, int[] projIndexes, int projSize,
			ItemPairArray outArray) {
		List<int[]> dbSequences = sequenceDatabase.getSequences();
		outArray.touchedCount = 0;

		for (int seqIdx = 0; seqIdx < projSize; seqIdx++) {
			int sequenceID = projSeqIDs[seqIdx];
			int[] sequence = dbSequences.get(sequenceID);

			// Scan from the projected index to the end of the sequence
			for (int i = projIndexes[seqIdx]; sequence[i] != -2; i++) {
				int token = sequence[i];

				if (token > 0) {
					PairData pairData = outArray.dataByItem[token];
					if (pairData == null) {
						pairData = new PairData();
						outArray.dataByItem[token] = pairData;
					}

					// Record first touch so the item can be reset later
					if (pairData.projSize == 0) {
						outArray.touchedItems[outArray.touchedCount++] = token;
					}

					// Add the sequence only once per sequence (avoid duplicates)
					if (!pairData.lastSeqIDEquals(sequenceID)) {
						pairData.add(sequenceID, i + 1);
					}
				}
			}
		}

		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Find all frequent i-extensions and s-extensions in a projected database, for
	 * the general case where itemsets may contain multiple items.
	 *
	 * @param pattern                            the current pattern buffer (renamed item ids)
	 * @param firstPositionOfLastItemsetInBuffer position of the first item of the last itemset in {@code pattern}
	 * @param projSeqIDs                         sequence IDs of the projected database
	 * @param projIndexes                        item indexes of the projected database
	 * @param projSize                           number of entries in the projected database
	 * @param lastBufferPosition                 last valid position used in {@code pattern}
	 * @param mapsPairs                          reusable structure to populate with i- and s-extension results
	 */
	protected void findAllFrequentPairs(int[] pattern, int firstPositionOfLastItemsetInBuffer, int[] projSeqIDs,
			int[] projIndexes, int projSize, int lastBufferPosition, ArrayFrequentPairs mapsPairs) {

		List<int[]> dbSequences = sequenceDatabase.getSequences();

		ItemPairArray pairsArr = mapsPairs.pairs;
		ItemPairArray postfixArr = mapsPairs.pairsInPostfix;
		pairsArr.touchedCount = 0;
		postfixArr.touchedCount = 0;

		for (int seqIdx = 0; seqIdx < projSize; seqIdx++) {
			int sequenceID = projSeqIDs[seqIdx];
			int[] sequence = dbSequences.get(sequenceID);

			int positionToBeMatched = firstPositionOfLastItemsetInBuffer;

			// Determine whether we start inside the last itemset of the pattern (postfix)
			int previousItem = sequence[projIndexes[seqIdx] - 1];
			boolean currentItemsetIsPostfix = previousItem != -1;
			boolean isFirstItemset = true;

			for (int i = projIndexes[seqIdx]; sequence[i] != -2; i++) {
				int token = sequence[i];

				if (token > 0) {
					// Route the item to postfix (i-extension) or pairs (s-extension) array
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

					// Also record as an s-extension if we are past the first itemset in a postfix
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

					// Advance the pattern matching position when not in a postfix
					if (!currentItemsetIsPostfix && pattern[positionToBeMatched] == token) {
						positionToBeMatched++;
						if (positionToBeMatched > lastBufferPosition) {
							currentItemsetIsPostfix = true;
						}
					}
				} else if (token == -1) {
					// Move to the next itemset and reset matching position
					isFirstItemset = false;
					currentItemsetIsPostfix = false;
					positionToBeMatched = firstPositionOfLastItemsetInBuffer;
				}
			}
		}

		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Print execution statistics (pattern count, time, memory, final minsup) to System.out.
	 */
	public void printStatistics() {
		StringBuilder r = new StringBuilder(200);
		r.append("=============  TSP_non_closed2 - STATISTICS =============");
		r.append(System.lineSeparator());
		r.append("Pattern found count : ");
		r.append(kPatterns == null ? 0 : kPatterns.size());
		r.append(System.lineSeparator());
		r.append("Total time: ");
		r.append(endTime - startTime);
		r.append(" ms");
		r.append(System.lineSeparator());
		r.append("Max memory (mb) : ");
		r.append(MemoryLogger.getInstance().getMaxMemory());
		r.append(System.lineSeparator());
		r.append("Final minsup value: ");
		r.append(minsuppAbsolute);
		r.append(System.lineSeparator());
		r.append("===================================================");
		r.append(System.lineSeparator());
		System.out.println(r.toString());
	}

	/**
	 * Write all top-k patterns to an output file, one pattern per line.
	 *
	 * @param path the path of the output file to write
	 * @throws IOException if an error occurs while writing the file
	 */
	public void writeResultTofile(String path) throws IOException {
		BufferedWriter writer = new BufferedWriter(new FileWriter(path));
		writer.write("@FILETYPE=\"Top-k frequent sequential patterns\"");
		writer.newLine();
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.newLine();

		for (SequentialPattern pattern : kPatterns) {
			StringBuilder buffer = new StringBuilder();

			// Write each itemset followed by -1 separator
			for (Itemset itemset : pattern.getItemsets()) {
				for (Integer item : itemset.getItems()) {
					buffer.append(item);
					buffer.append(' ');
				}
				buffer.append("-1 ");
			}

			buffer.append("#SUP: ");
			buffer.append(pattern.getAbsoluteSupport());

			// Optionally append the list of sequence identifiers
			if (showSequenceIdentifiers) {
				buffer.append(" #SID: ");
				for (Integer sid : pattern.getSequenceIDs()) {
					buffer.append(sid);
					buffer.append(' ');
				}
			}

			writer.write(buffer.toString());
			writer.newLine();
		}

		writer.close();
	}

	/**
	 * Get the minimum pattern length in terms of item count.
	 *
	 * @return the minimum pattern length
	 */
	public int getMinimumPatternLength() {
		return minimumPatternLength;
	}

	/**
	 * Set the minimum pattern length in terms of item count. This optional
	 * parameter allows to specify the minimum number of items that a pattern
	 * must contain to be output. The default value is 0 (no constraint).
	 *
	 * @param minimumPatternLength the minimum number of items required in a pattern
	 */
	public void setMinimumPatternLength(int minimumPatternLength) {
		this.minimumPatternLength = minimumPatternLength;
	}

	/**
	 * Get the maximum pattern length in terms of item count.
	 *
	 * @return the maximum pattern length
	 */
	public int getMaximumPatternLength() {
		return maximumPatternLength;
	}

	/**
	 * Set the maximum pattern length in terms of item count. This optional
	 * parameter allows to specify the maximum number of items that a pattern
	 * can contain. The default value is infinity (no constraint).
	 *
	 * @param maximumPatternLength the maximum number of items allowed in a pattern
	 */
	public void setMaximumPatternLength(int maximumPatternLength) {
		this.maximumPatternLength = maximumPatternLength;
	}

	/**
	 * Set whether sequence identifiers should be included in the output for each pattern.
	 *
	 * @param showSequenceIdentifiers true to show sequence identifiers, false otherwise
	 */
	public void setShowSequenceIdentifiers(boolean showSequenceIdentifiers) {
		this.showSequenceIdentifiers = showSequenceIdentifiers;
	}

	/**
	 * Get the current (possibly raised) absolute minimum support threshold.
	 *
	 * @return the minimum support as a number of sequences
	 */
	public int getMinsuppAbsolute() {
		return minsuppAbsolute;
	}

	/**
	 * A candidate pattern waiting to be expanded during the best-first search.
	 * Each candidate owns its own copy of its projected database so that multiple
	 * candidates can coexist simultaneously in the priority queue.
	 */
	private static class Candidate {
		/** Pattern buffer using renamed item IDs, with -1 as itemset separator. */
		final int[] pattern;

		/** Last valid position in {@link #pattern} that is used. */
		final int lastBufferPosition;

		/** Position of the first item of the last itemset in {@link #pattern}. */
		final int firstPositionOfLastItemset;

		/** Number of items in the pattern. */
		final int itemCount;

		/** Support of the pattern (number of sequences it appears in). */
		final int support;

		/** Projected sequence IDs, owned copy sized exactly {@link #projSize}. */
		final int[] projSeqIDs;

		/** Projected item indexes, owned copy sized exactly {@link #projSize}. */
		final int[] projIndexes;

		/** Number of entries in the projected database. */
		final int projSize;

		/** Unique increasing id used to break ties when supports are equal. */
		final long id;

		/**
		 * Create a candidate, copying the projected database from the given source arrays.
		 *
		 * @param pattern                    pattern buffer (renamed item ids)
		 * @param lastBufferPosition         last valid position in {@code pattern}
		 * @param firstPositionOfLastItemset position of the first item of the last itemset in {@code pattern}
		 * @param itemCount                  number of items in the pattern
		 * @param support                    support of the pattern
		 * @param seqIDs                     source array of sequence IDs to copy
		 * @param indexes                    source array of item indexes to copy
		 * @param projSize                   number of entries to copy from the source arrays
		 * @param id                         unique id for tie-breaking
		 */
		Candidate(int[] pattern, int lastBufferPosition, int firstPositionOfLastItemset, int itemCount, int support,
				int[] seqIDs, int[] indexes, int projSize, long id) {
			this.pattern = pattern;
			this.lastBufferPosition = lastBufferPosition;
			this.firstPositionOfLastItemset = firstPositionOfLastItemset;
			this.itemCount = itemCount;
			this.support = support;
			this.projSize = projSize;
			this.id = id;

			// Copy projected database into owned arrays
			this.projSeqIDs = new int[projSize];
			this.projIndexes = new int[projSize];
			System.arraycopy(seqIDs, 0, this.projSeqIDs, 0, projSize);
			System.arraycopy(indexes, 0, this.projIndexes, 0, projSize);
		}
	}

	/**
	 * Holds two {@link ItemPairArray} instances, one for i-extensions (postfix itemset)
	 * and one for s-extensions (new itemset), used during candidate expansion.
	 */
	public static class ArrayFrequentPairs {
		/** Items extending the pattern within the last itemset (i-extension). */
		public final ItemPairArray pairs;

		/** Items extending the pattern as a new itemset (s-extension). */
		public final ItemPairArray pairsInPostfix;

		/**
		 * Create an instance sized for the given number of frequent items.
		 *
		 * @param frequentItemCount the number of distinct frequent (renamed) items
		 */
		public ArrayFrequentPairs(int frequentItemCount) {
			pairs = new ItemPairArray(frequentItemCount);
			pairsInPostfix = new ItemPairArray(frequentItemCount);
		}
	}

	/**
	 * Sparse array mapping a renamed item id directly to its {@link PairData} projection.
	 * Avoids autoboxing and hashing overhead compared to a {@code Map<Integer, PairData>}.
	 */
	static class ItemPairArray {
		/** Projected data for each item, indexed directly by renamed item id. */
		final PairData[] dataByItem;

		/** Ids of the items that were accessed during the current expansion call. */
		final int[] touchedItems;

		/** Number of items currently recorded in {@link #touchedItems}. */
		int touchedCount;

		/**
		 * Create an instance sized for the given number of frequent items.
		 *
		 * @param frequentItemCount the number of distinct frequent (renamed) items
		 */
		ItemPairArray(int frequentItemCount) {
			dataByItem = new PairData[frequentItemCount + 1];
			touchedItems = new int[frequentItemCount + 1];
			touchedCount = 0;
		}
	}

	/**
	 * Holds the projected database for a single item extension, using parallel int
	 * arrays for efficiency. Supports {@link #reset()} for reuse across expansions.
	 */
	static class PairData {
		/** Sequence IDs in this item's projected database. */
		int[] seqIDs;

		/** Item indexes corresponding to each entry in {@link #seqIDs}. */
		int[] indexes;

		/** Number of entries currently stored in this projection. */
		int projSize;

		/** Create an empty projection with a small initial capacity. */
		PairData() {
			seqIDs = new int[8];
			indexes = new int[8];
			projSize = 0;
		}

		/**
		 * Add a sequence ID and its corresponding index to this projection,
		 * growing the underlying arrays if necessary.
		 *
		 * @param seqID the sequence ID to add
		 * @param index the item index to add
		 */
		void add(int seqID, int index) {
			// Grow the arrays if capacity is exhausted
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

		/**
		 * Reset this projection for reuse without deallocating the underlying arrays.
		 */
		void reset() {
			projSize = 0;
		}

		/**
		 * Check whether the last recorded sequence ID equals the given value.
		 *
		 * @param seqID the sequence ID to compare against
		 * @return true if the last recorded sequence ID equals {@code seqID}
		 */
		boolean lastSeqIDEquals(int seqID) {
			return projSize > 0 && seqIDs[projSize - 1] == seqID;
		}
	}
}