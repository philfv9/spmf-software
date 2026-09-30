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
/***
 * This is a 2016 implementation of the PrefixSpan algorithm. PrefixSpan was
 * proposed by Pei et al. 2001.
 * 
 * NOTE: This implementation saves the pattern to a file as soon as they are
 * found or can keep the pattern into memory, depending on what the user choose.
 *
 * Copyright (c) 2008-2026 Philippe Fournier-Viger
 */
public class AlgoPrefixSpan {

	/** Total execution time of the algorithm in milliseconds. */
	long totalTime;

	/** Number of patterns found. */
	int patternCount;

	/** Minimum support as an absolute sequence count. */
	private int minsuppAbsolute;

	/** Writer used to output patterns to a file, or {@code null} if results are kept in memory. */
	BufferedWriter writer = null;

	/** In-memory collection of sequential patterns, or {@code null} if results are written to a file. */
	private SequentialPatterns patterns = null;

	/** Minimum pattern length in terms of item count. */
	private int minimumPatternLength = 0;

	/** Maximum pattern length in terms of item count (default: no limit). */
	private int maximumPatternLength = Integer.MAX_VALUE;

	/** True if sequence identifiers of each pattern should be shown. */
	boolean showSequenceIdentifiers = false;

	/** Size of the buffer used to store the pattern currently being mined. */
	final int BUFFERS_SIZE = 2000;

	/** Buffer storing the current pattern being mined, using renamed item ids. */
	private int[] patternBuffer = new int[BUFFERS_SIZE];

	/** Number of sequences in the original database. */
	int sequenceCount = 0;

	/** Sequence database on which the algorithm is run. */
	SequenceDatabase sequenceDatabase;

	/** True if the database contains itemsets with more than one item. */
	boolean containsItemsetsWithMultipleItems = false;

	/** Reusable buffers of sequence IDs for projected databases at each recursion depth. */
	private int[][] projSeqIDsBuffer;

	/** Reusable buffers of item indexes for projected databases at each recursion depth. */
	private int[][] projIndexesBuffer;

	/** Reusable per-depth candidate structures when itemsets contain a single item. */
	private ItemPairArray[] singleItemsArrays;

	/** Reusable per-depth candidate structures when itemsets may contain multiple items. */
	private ArrayFrequentPairs[] frequentPairsArrays;

	/** Utility to map original item ids to consecutive integer ids and back. */
	private ItemNameConverter itemNameConverter;

	/** Number of distinct frequent (single) items after pruning the database. */
	private int frequentItemCount;

	/** Creates a new instance of the PrefixSpan algorithm. */
	public AlgoPrefixSpan() {
	}

	/**
	 * Runs the algorithm on a sequence database using a relative minimum support and writes results to a file or memory.
	 * 
	 * @param inputFile      path to the input sequence database file
	 * @param minsupRelative minimum support as a relative value in [0,1]
	 * @param outputFilePath path to the output file, or {@code null} to keep results in memory
	 * @return the found sequential patterns if kept in memory, otherwise {@code null}
	 * @throws IOException if an error occurs while reading or writing files
	 */
	public SequentialPatterns runAlgorithm(String inputFile, double minsupRelative, String outputFilePath)
			throws IOException {
		// record start time
		totalTime = 0;
		long startTime = System.currentTimeMillis();

		// reset memory statistics
		MemoryLogger.getInstance().reset();

		// load the sequence database
		sequenceDatabase = new SequenceDatabase();
		sequenceDatabase.loadFile(inputFile);
		sequenceCount = sequenceDatabase.size();

		// convert relative minsup to absolute minsup
		this.minsuppAbsolute = (int) Math.ceil((minsupRelative * sequenceCount) - 0.00000001);
		if (this.minsuppAbsolute == 0) {
			this.minsuppAbsolute = 1;
		}

		// run PrefixSpan
		prefixSpan(sequenceDatabase, outputFilePath);

		// release reference to the database
		sequenceDatabase = null;

		// record end time
		totalTime = System.currentTimeMillis() - startTime;

		// close the output file if used
		if (writer != null) {
			writer.close();
		}
		return patterns;
	}

	/**
	 * Runs the algorithm on a sequence database using an absolute minimum support and writes results to a file or memory.
	 * 
	 * @param inputFile      path to the input sequence database file
	 * @param outputFilePath path to the output file, or {@code null} to keep results in memory
	 * @param minsup         minimum support as an absolute sequence count
	 * @return the found sequential patterns if kept in memory, otherwise {@code null}
	 * @throws IOException if an error occurs while reading or writing files
	 */
	public SequentialPatterns runAlgorithm(String inputFile, String outputFilePath, int minsup) throws IOException {
		// initialize statistics
		patternCount = 0;
		MemoryLogger.getInstance().reset();

		// store minsup chosen by the user
		this.minsuppAbsolute = minsup;

		// record start time
		totalTime = 0;
		long startTime = System.currentTimeMillis();

		// load the sequence database
		sequenceDatabase = new SequenceDatabase();
		sequenceDatabase.loadFile(inputFile);

		// run PrefixSpan
		prefixSpan(sequenceDatabase, outputFilePath);

		// release reference to the database
		sequenceDatabase = null;

		// record end time
		totalTime = System.currentTimeMillis() - startTime;

		// close the output file if used
		if (writer != null) {
			writer.close();
		}
		return patterns;
	}

	/**
	 * Executes the core PrefixSpan procedure on a given sequence database.
	 * 
	 * @param sequenceDatabase the sequence database to mine
	 * @param outputFilePath   path to the output file, or {@code null} to keep results in memory
	 * @throws IOException if an error occurs while writing the output file
	 */
	private void prefixSpan(SequenceDatabase sequenceDatabase, String outputFilePath) throws IOException {
		// initialize output destination (file or memory)
		if (outputFilePath == null) {
			writer = null;
			patterns = new SequentialPatterns("FREQUENT SEQUENTIAL PATTERNS");
		} else {
			patterns = null;
			writer = new BufferedWriter(new FileWriter(outputFilePath));
			writer.write("@FILETYPE=\"Frequent sequential patterns\"");
			writer.newLine();
			writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
			writer.newLine();
		}

		sequenceCount = sequenceDatabase.size();

		// allocate top-level buffers for projected databases with a small initial capacity that grows on demand
		int initialDepthCapacity = Math.max(2, Math.min(maximumPatternLength, 16) + 2);
		projSeqIDsBuffer = new int[initialDepthCapacity][];
		projIndexesBuffer = new int[initialDepthCapacity][];
		singleItemsArrays = new ItemPairArray[initialDepthCapacity];
		frequentPairsArrays = new ArrayFrequentPairs[initialDepthCapacity];

		// find sequences containing each item (frequencies of single items)
		Map<Integer, List<Integer>> mapSequenceID = findSequencesContainingItems();

		// count and rename frequent items to consecutive integers
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

		// explore projected databases depending on whether itemsets can contain multiple items
		if (containsItemsetsWithMultipleItems) {
			prefixspanWithMultipleItems(mapSequenceID);
		} else {
			prefixspanWithSingleItems(mapSequenceID);
		}

		// release reusable buffers
		projSeqIDsBuffer = null;
		projIndexesBuffer = null;
		singleItemsArrays = null;
		frequentPairsArrays = null;
		itemNameConverter = null;
	}

	/**
	 * Ensures that reusable projection buffers and candidate structures exist for a given recursion depth.
	 * 
	 * @param depth the recursion depth for which buffers are required
	 */
	private void ensureDepthBuffers(int depth) {
		// grow top-level buffer arrays if the requested depth exceeds their current capacity
		if (depth >= projSeqIDsBuffer.length) {
			int newCapacity = Math.max(depth + 1, projSeqIDsBuffer.length * 2);
			projSeqIDsBuffer = Arrays.copyOf(projSeqIDsBuffer, newCapacity);
			projIndexesBuffer = Arrays.copyOf(projIndexesBuffer, newCapacity);
			singleItemsArrays = Arrays.copyOf(singleItemsArrays, newCapacity);
			frequentPairsArrays = Arrays.copyOf(frequentPairsArrays, newCapacity);
		}
		// lazily allocate sequence-id and index buffers
		if (projSeqIDsBuffer[depth] == null) {
			projSeqIDsBuffer[depth] = new int[sequenceCount];
			projIndexesBuffer[depth] = new int[sequenceCount];
		}
		// lazily allocate candidate arrays
		if (singleItemsArrays[depth] == null) {
			singleItemsArrays[depth] = new ItemPairArray(frequentItemCount);
		}
		if (frequentPairsArrays[depth] == null) {
			frequentPairsArrays[depth] = new ArrayFrequentPairs(frequentItemCount);
		}
	}

	/**
	 * Removes infrequent items in a single-item-per-itemset database and explores projected databases for frequent items of size 1.
	 * 
	 * @param mapSequenceID a map from item to list of sequence IDs where it appears
	 * @throws IOException if an error occurs while writing the output file
	 */
	private void prefixspanWithSingleItems(Map<Integer, List<Integer>> mapSequenceID) throws IOException {
		// remove infrequent items and rename frequent ones in each sequence
		List<int[]> sequences = sequenceDatabase.getSequences();
		for (int i = 0, size = sequences.size(); i < size; i++) {
			int[] sequence = sequences.get(i);
			int currentPosition = 0;

			// scan tokens of the sequence and keep only renamed frequent items
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
						continue;
					} else {
						// drop empty sequences
						sequences.set(i, null);
					}
				}
			}
		}

		// explore each projected database for frequent single items
		for (Entry<Integer, List<Integer>> entry : mapSequenceID.entrySet()) {
			List<Integer> sequenceIDs = entry.getValue();
			int support = sequenceIDs.size();

			if (support >= minsuppAbsolute) {
				int item = entry.getKey();

				// save pattern of length 1 (original item id) if the minimum length allows it
				if (minimumPatternLength <= 1) {
					savePattern(item, support, sequenceIDs);
				}

				// grow pattern if longer patterns are allowed
				if (maximumPatternLength > 1) {
					int newItem = itemNameConverter.toNewName(item);
					patternBuffer[0] = newItem;

					// build projected database for this item
					ensureDepthBuffers(1);
					int projSize = buildProjectedDatabaseSingleItems(newItem, sequenceIDs, projSeqIDsBuffer[1],
							projIndexesBuffer[1]);

					// recurse on the projected database
					recursionSingleItems(projSeqIDsBuffer[1], projIndexesBuffer[1], projSize, 2, 0, 1);
				}
			}
		}
	}

	/**
	 * Removes infrequent items in a database with possible multi-item itemsets and explores projected databases for frequent items of size 1.
	 * 
	 * @param mapSequenceID a map from item to list of sequence IDs where it appears
	 * @throws IOException if an error occurs while writing the output file
	 */
	private void prefixspanWithMultipleItems(Map<Integer, List<Integer>> mapSequenceID) throws IOException {
		// remove infrequent items and rename frequent ones in each sequence, taking itemset separators into account
		List<int[]> sequences = sequenceDatabase.getSequences();
		for (int i = 0, size = sequences.size(); i < size; i++) {
			int[] sequence = sequences.get(i);
			int currentPosition = 0;
			int currentItemsetItemCount = 0;

			// scan tokens of the sequence and keep only renamed frequent items and non-empty itemsets
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
						int[] newSequence = new int[currentPosition + 1];
						System.arraycopy(sequence, 0, newSequence, 0, currentPosition + 1);
						sequences.set(i, newSequence);
						continue;
					} else {
						// drop empty sequences
						sequences.set(i, null);
					}
				}
			}
		}

		// explore each projected database for frequent single items
		for (Entry<Integer, List<Integer>> entry : mapSequenceID.entrySet()) {
			List<Integer> sequenceIDs = entry.getValue();
			int support = sequenceIDs.size();

			if (support >= minsuppAbsolute) {
				int item = entry.getKey();

				// save pattern of length 1 (original item id) if the minimum length allows it
				if (minimumPatternLength <= 1) {
					savePattern(item, support, sequenceIDs);
				}

				// grow pattern if longer patterns are allowed
				if (maximumPatternLength > 1) {
					int newItem = itemNameConverter.toNewName(item);
					patternBuffer[0] = newItem;

					// build projected database for this item
					ensureDepthBuffers(1);
					int projSize = buildProjectedDatabaseFirstTimeMultipleItems(newItem, sequenceIDs, projSeqIDsBuffer[1],
							projIndexesBuffer[1]);

					// recurse on the projected database
					recursion(patternBuffer, projSeqIDsBuffer[1], projIndexesBuffer[1], projSize, 2, 0, 1);
				}
			}
		}
	}

	/**
	 * Saves a sequential pattern containing a single item to the output file or memory.
	 * 
	 * @param item        the original (non-renamed) item id
	 * @param support     the support of the item
	 * @param sequenceIDs list of sequence IDs containing this item
	 * @throws IOException if an error occurs while writing the output file
	 */
	private void savePattern(int item, int support, List<Integer> sequenceIDs) throws IOException {
		// update statistics
		patternCount++;

		if (writer != null) {
			// write pattern to file
			StringBuilder r = new StringBuilder();
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
		} else {
			// store pattern in memory
			SequentialPattern pattern = new SequentialPattern();
			pattern.addItemset(new Itemset(item));
			pattern.setSequenceIDs(sequenceIDs);
			patterns.addSequence(pattern, 1);
		}
	}

	/**
	 * Saves a pattern containing two or more items to the output file or memory.
	 * 
	 * @param lastBufferPosition last position used in {@link #patternBuffer} for this pattern
	 * @param projSeqIDs         sequence IDs of the projected database
	 * @param projIndexes        item indexes of the projected database
	 * @param projSize           number of entries in the projected database
	 * @throws IOException if an error occurs while writing the output file
	 */
	private void savePattern(int lastBufferPosition, int[] projSeqIDs, int[] projIndexes, int projSize)
			throws IOException {
		// update statistics
		patternCount++;

		if (writer != null) {
			// write pattern to file
			StringBuilder r = new StringBuilder((lastBufferPosition + 2) * 8 + 20);
			for (int i = 0; i <= lastBufferPosition; i++) {
				int token = patternBuffer[i];
				r.append(token == -1 ? -1 : itemNameConverter.toOldName(token));
				r.append(" ");
			}
			// ensure pattern ends with an itemset separator
			if (patternBuffer[lastBufferPosition] != -1) {
				r.append("-1 ");
			}
			r.append("#SUP: ");
			r.append(projSize);
			if (showSequenceIdentifiers) {
				r.append(" #SID: ");
				for (int i = 0; i < projSize; i++) {
					r.append(projSeqIDs[i]);
					r.append(" ");
				}
			}
			writer.write(r.toString());
			writer.newLine();
		} else {
			// store pattern in memory
			SequentialPattern pattern = new SequentialPattern();
			int itemsetCount = 0;
			Itemset currentItemset = new Itemset();
			for (int i = 0; i <= lastBufferPosition; i++) {
				int token = patternBuffer[i];
				if (token > 0) {
					currentItemset.addItem(itemNameConverter.toOldName(token));
				} else if (token == -1) {
					pattern.addItemset(currentItemset);
					currentItemset = new Itemset();
					itemsetCount++;
				}
			}
			pattern.addItemset(currentItemset);
			itemsetCount++;

			List<Integer> sequencesIDs = new ArrayList<Integer>(projSize);
			for (int i = 0; i < projSize; i++) {
				sequencesIDs.add(projSeqIDs[i]);
			}
			pattern.setSequenceIDs(sequencesIDs);
			patterns.addSequence(pattern, itemsetCount);
		}
	}

	/**
	 * Builds a map from each item to the list of sequence IDs containing that item.
	 * 
	 * @return a map item → list of sequence IDs where the item appears
	 */
	private Map<Integer, List<Integer>> findSequencesContainingItems() {
		// track number of items per current itemset
		int itemCountInCurrentItemset;

		Map<Integer, List<Integer>> mapSequenceID = new HashMap<Integer, List<Integer>>();
		List<int[]> sequences = sequenceDatabase.getSequences();

		// scan each sequence
		for (int i = 0, dbSize = sequences.size(); i < dbSize; i++) {
			int[] sequence = sequences.get(i);
			itemCountInCurrentItemset = 0;

			// scan tokens of the sequence
			for (int j = 0, seqLen = sequence.length; j < seqLen; j++) {
				int token = sequence[j];

				if (token > 0) {
					// update list of sequence IDs for this item
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
	 * Builds a projected database by pseudo-projection for a given item (single-item-per-itemset case).
	 * 
	 * @param item        renamed item used for projection
	 * @param sequenceIDs sequence IDs containing the item
	 * @param outSeqIDs   output array to receive sequence IDs of the projection
	 * @param outIndexes  output array to receive item indexes of the projection
	 * @return the number of pseudo-sequences in the projected database
	 */
	private int buildProjectedDatabaseSingleItems(int item, List<Integer> sequenceIDs, int[] outSeqIDs,
			int[] outIndexes) {
		int seqIDsSize = sequenceIDs.size();
		List<int[]> sequences = sequenceDatabase.getSequences();
		int projSize = 0;

		// for each sequence that contains the item
		for (int idx = 0; idx < seqIDsSize; idx++) {
			int sequenceID = sequenceIDs.get(idx);
			int[] sequence = sequences.get(sequenceID);

			// scan tokens until end-of-sequence marker
			for (int j = 0; sequence[j] != -2; j++) {
				int token = sequence[j];

				if (token == item) {
					// add a pseudo-sequence starting after this item if not at end
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
	 * Builds a projected database by pseudo-projection for a given item (multi-item itemset case, first recursion).
	 * 
	 * @param item        renamed item used for projection
	 * @param sequenceIDs sequence IDs containing the item
	 * @param outSeqIDs   output array to receive sequence IDs of the projection
	 * @param outIndexes  output array to receive item indexes of the projection
	 * @return the number of pseudo-sequences in the projected database
	 */
	private int buildProjectedDatabaseFirstTimeMultipleItems(int item, List<Integer> sequenceIDs, int[] outSeqIDs,
			int[] outIndexes) {
		int seqIDsSize = sequenceIDs.size();
		List<int[]> sequences = sequenceDatabase.getSequences();
		int projSize = 0;

		// for each sequence that contains the item
		for (int idx = 0; idx < seqIDsSize; idx++) {
			int sequenceID = sequenceIDs.get(idx);
			int[] sequence = sequences.get(sequenceID);

			// scan tokens until end-of-sequence marker
			for (int j = 0; sequence[j] != -2; j++) {
				int token = sequence[j];

				if (token == item) {
					// create a pseudo-sequence if there is something after this item
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
	 * Recursively grows a sequential pattern in the single-item-per-itemset case.
	 * 
	 * @param projSeqIDs         sequence IDs of the current projected database
	 * @param projIndexes        item indexes of the current projected database
	 * @param projSize           number of entries in the projected database
	 * @param k                  current prefix length in terms of items
	 * @param lastBufferPosition last position used in the pattern buffer for the prefix
	 * @param depth              current recursion depth (for selecting buffers)
	 * @throws IOException if an error occurs while writing the output file
	 */
	private void recursionSingleItems(int[] projSeqIDs, int[] projIndexes, int projSize, int k, int lastBufferPosition,
			int depth) throws IOException {

		// ensure buffers and candidate arrays for this depth are allocated
		ensureDepthBuffers(depth);

		// find all frequent items and their projections in the current projected database
		ItemPairArray itemsPseudoSequences = singleItemsArrays[depth];
		findAllFrequentPairsSingleItems(projSeqIDs, projIndexes, projSize, itemsPseudoSequences);

		int nextDepth = depth + 1;
		// ensure buffers for the next depth are allocated
		ensureDepthBuffers(nextDepth);

		// explore each frequent item extension
		for (int idx = 0; idx < itemsPseudoSequences.touchedCount; idx++) {
			int item = itemsPseudoSequences.touchedItems[idx];
			PairData pairData = itemsPseudoSequences.dataByItem[item];

			if (pairData.projSize >= minsuppAbsolute) {
				// extend current pattern with a new itemset containing this item
				patternBuffer[lastBufferPosition + 1] = -1;
				patternBuffer[lastBufferPosition + 2] = item;

				// copy projection into next-depth buffers
				System.arraycopy(pairData.seqIDs, 0, projSeqIDsBuffer[nextDepth], 0, pairData.projSize);
				System.arraycopy(pairData.indexes, 0, projIndexesBuffer[nextDepth], 0, pairData.projSize);

				// save extended pattern if the minimum length allows it
				if (k >= minimumPatternLength) {
					savePattern(lastBufferPosition + 2, projSeqIDsBuffer[nextDepth], projIndexesBuffer[nextDepth],
							pairData.projSize);
				}

				// recursively grow extended pattern if allowed by maximum length
				if (k < maximumPatternLength) {
					recursionSingleItems(projSeqIDsBuffer[nextDepth], projIndexesBuffer[nextDepth], pairData.projSize,
							k + 1, lastBufferPosition + 2, nextDepth);
				}
			}
		}

		// reset touched PairData objects for reuse at this depth
		for (int idx = 0; idx < itemsPseudoSequences.touchedCount; idx++) {
			itemsPseudoSequences.dataByItem[itemsPseudoSequences.touchedItems[idx]].reset();
		}
	}

	/**
	 * Recursively grows a sequential pattern in the general (multi-item itemset) case.
	 * 
	 * @param patternBuffer      buffer containing the current prefix pattern
	 * @param projSeqIDs         sequence IDs of the current projected database
	 * @param projIndexes        item indexes of the current projected database
	 * @param projSize           number of entries in the projected database
	 * @param k                  current prefix length in terms of items
	 * @param lastBufferPosition last position used in the pattern buffer for the prefix
	 * @param depth              current recursion depth (for selecting buffers)
	 * @throws IOException if an error occurs while writing the output file
	 */
	private void recursion(int[] patternBuffer, int[] projSeqIDs, int[] projIndexes, int projSize, int k,
			int lastBufferPosition, int depth) throws IOException {

		// ensure buffers and candidate arrays for this depth are allocated
		ensureDepthBuffers(depth);

		// find frequent items and their projections (i-extensions and s-extensions)
		ArrayFrequentPairs mapsPairs = frequentPairsArrays[depth];
		findAllFrequentPairs(projSeqIDs, projIndexes, projSize, lastBufferPosition, mapsPairs);

		int nextDepth = depth + 1;
		// ensure buffers for the next depth are allocated
		ensureDepthBuffers(nextDepth);

		// process i-extensions (extend last itemset)
		ItemPairArray postfixArr = mapsPairs.pairsInPostfix;
		for (int idx = 0; idx < postfixArr.touchedCount; idx++) {
			int item = postfixArr.touchedItems[idx];
			PairData pairData = postfixArr.dataByItem[item];

			if (pairData.projSize >= minsuppAbsolute) {
				int newBufferPosition = lastBufferPosition + 1;
				patternBuffer[newBufferPosition] = item;

				// copy projection into next-depth buffers
				System.arraycopy(pairData.seqIDs, 0, projSeqIDsBuffer[nextDepth], 0, pairData.projSize);
				System.arraycopy(pairData.indexes, 0, projIndexesBuffer[nextDepth], 0, pairData.projSize);

				// save extended pattern if the minimum length allows it
				if (k >= minimumPatternLength) {
					savePattern(newBufferPosition, projSeqIDsBuffer[nextDepth], projIndexesBuffer[nextDepth],
							pairData.projSize);
				}

				// recursively grow extended pattern if allowed by maximum length
				if (k < maximumPatternLength) {
					recursion(patternBuffer, projSeqIDsBuffer[nextDepth], projIndexesBuffer[nextDepth],
							pairData.projSize, k + 1, newBufferPosition, nextDepth);
				}
			}
		}

		// process s-extensions (add new itemset)
		ItemPairArray pairsArr = mapsPairs.pairs;
		for (int idx = 0; idx < pairsArr.touchedCount; idx++) {
			int item = pairsArr.touchedItems[idx];
			PairData pairData = pairsArr.dataByItem[item];

			if (pairData.projSize >= minsuppAbsolute) {
				int newBufferPosition = lastBufferPosition + 1;
				patternBuffer[newBufferPosition] = -1;
				newBufferPosition++;
				patternBuffer[newBufferPosition] = item;

				// copy projection into next-depth buffers
				System.arraycopy(pairData.seqIDs, 0, projSeqIDsBuffer[nextDepth], 0, pairData.projSize);
				System.arraycopy(pairData.indexes, 0, projIndexesBuffer[nextDepth], 0, pairData.projSize);

				// save extended pattern if the minimum length allows it
				if (k >= minimumPatternLength) {
					savePattern(newBufferPosition, projSeqIDsBuffer[nextDepth], projIndexesBuffer[nextDepth],
							pairData.projSize);
				}

				// recursively grow extended pattern if allowed by maximum length
				if (k < maximumPatternLength) {
					recursion(patternBuffer, projSeqIDsBuffer[nextDepth], projIndexesBuffer[nextDepth],
							pairData.projSize, k + 1, newBufferPosition, nextDepth);
				}
			}
		}

		// reset touched PairData objects for reuse at this depth
		for (int idx = 0; idx < postfixArr.touchedCount; idx++) {
			postfixArr.dataByItem[postfixArr.touchedItems[idx]].reset();
		}
		for (int idx = 0; idx < pairsArr.touchedCount; idx++) {
			pairsArr.dataByItem[pairsArr.touchedItems[idx]].reset();
		}
	}

	/**
	 * Finds all frequent items in a projected database where each itemset has a single item, building their projections.
	 * 
	 * @param projSeqIDs  sequence IDs of the current projected database
	 * @param projIndexes item indexes of the current projected database
	 * @param projSize    number of entries in the projected database
	 * @param outArray    reusable structure that will be populated with item projections
	 */
	protected void findAllFrequentPairsSingleItems(int[] projSeqIDs, int[] projIndexes, int projSize,
			ItemPairArray outArray) {

		List<int[]> dbSequences = sequenceDatabase.getSequences();

		// reset the list of touched items for this call
		outArray.touchedCount = 0;

		// scan each projected sequence
		for (int seqIdx = 0; seqIdx < projSize; seqIdx++) {
			int sequenceID = projSeqIDs[seqIdx];
			int[] sequence = dbSequences.get(sequenceID);

			// scan tokens starting at projection index
			for (int i = projIndexes[seqIdx]; sequence[i] != -2; i++) {
				int token = sequence[i];

				if (token > 0) {
					// get or create projection data for this item
					PairData pairData = outArray.dataByItem[token];
					if (pairData == null) {
						pairData = new PairData();
						outArray.dataByItem[token] = pairData;
					}

					// record first touch of this item during this call
					if (pairData.projSize == 0) {
						outArray.touchedItems[outArray.touchedCount++] = token;
					}

					// add sequence to this item's projection if not already present
					if (!pairData.lastSeqIDEquals(sequenceID)) {
						pairData.add(sequenceID, i + 1);
					}
				}
			}
		}

		// update memory usage statistics
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Holds two array-based structures for counting item frequencies as i-extensions and s-extensions.
	 */
	public static class ArrayFrequentPairs {
		/** Items extending the pattern as a new itemset (s-extension). */
		public final ItemPairArray pairs;
		/** Items extending the pattern within the last itemset (i-extension, postfix). */
		public final ItemPairArray pairsInPostfix;

		/**
		 * Creates an ArrayFrequentPairs structure.
		 * 
		 * @param frequentItemCount number of distinct frequent (renamed) items
		 */
		public ArrayFrequentPairs(int frequentItemCount) {
			pairs = new ItemPairArray(frequentItemCount);
			pairsInPostfix = new ItemPairArray(frequentItemCount);
		}
	}

	/**
	 * Sparse array mapping renamed item ids directly to {@link PairData} projections, with a list of touched items.
	 */
	static class ItemPairArray {
		/** Projected data for each item, indexed by renamed item id. */
		final PairData[] dataByItem;
		/** Item ids that were touched during the current call. */
		final int[] touchedItems;
		/** Number of entries currently stored in {@link #touchedItems}. */
		int touchedCount;

		/**
		 * Creates an ItemPairArray for a given number of frequent items.
		 * 
		 * @param frequentItemCount number of distinct frequent (renamed) items
		 */
		ItemPairArray(int frequentItemCount) {
			dataByItem = new PairData[frequentItemCount + 1];
			touchedItems = new int[frequentItemCount + 1];
			touchedCount = 0;
		}
	}

	/**
	 * Holds the projected database (sequence IDs and indexes) for a single item extension.
	 */
	static class PairData {
		/** Sequence IDs of the projected database for this item. */
		int[] seqIDs;
		/** Item indexes in each sequence of the projected database for this item. */
		int[] indexes;
		/** Number of entries currently stored in this projection. */
		int projSize;

		/** Creates an empty PairData instance with a small initial capacity. */
		PairData() {
			seqIDs = new int[8];
			indexes = new int[8];
			projSize = 0;
		}

		/**
		 * Adds a sequence ID and index to this projection, growing internal arrays if needed.
		 * 
		 * @param seqID sequence ID to add
		 * @param index item index to add
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

		/** Resets this projection for reuse without reallocating its arrays. */
		void reset() {
			projSize = 0;
		}

		/**
		 * Checks whether the last added sequence ID equals the given sequence ID.
		 * 
		 * @param seqID sequence ID to compare with the last stored one
		 * @return {@code true} if the last stored sequence ID equals {@code seqID}
		 */
		boolean lastSeqIDEquals(int seqID) {
			return projSize > 0 && seqIDs[projSize - 1] == seqID;
		}
	}

	/**
	 * Finds all frequent items in a projected database (general case) and fills their projections.
	 * 
	 * @param projSeqIDs         sequence IDs of the current projected database
	 * @param projIndexes        item indexes of the current projected database
	 * @param projSize           number of entries in the projected database
	 * @param lastBufferPosition last position used in the pattern buffer for the current prefix
	 * @param mapsPairs          reusable structure to receive i-extension and s-extension projections
	 */
	protected void findAllFrequentPairs(int[] projSeqIDs, int[] projIndexes, int projSize, int lastBufferPosition,
			ArrayFrequentPairs mapsPairs) {

		// find the first position of the last itemset in the current prefix
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

		// reset lists of touched items for this call
		pairsArr.touchedCount = 0;
		postfixArr.touchedCount = 0;

		// scan each projected sequence
		for (int seqIdx = 0; seqIdx < projSize; seqIdx++) {
			int sequenceID = projSeqIDs[seqIdx];
			int[] sequence = dbSequences.get(sequenceID);

			// position in the prefix to match within the last itemset
			int positionToBeMatched = firstPositionOfLastItemsetInBuffer;

			// determine whether first itemset is a postfix
			int previousItem = sequence[projIndexes[seqIdx] - 1];
			boolean currentItemsetIsPostfix = (previousItem != -1);
			boolean isFirstItemset = true;

			// scan tokens of the sequence from the projection index
			for (int i = projIndexes[seqIdx]; sequence[i] != -2; i++) {
				int token = sequence[i];

				if (token > 0) {
					// choose correct array (postfix or not) for this item
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

					// if postfix but not first itemset, also consider non-postfix extension
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

					// try to match this item with the last itemset of the prefix
					if (!currentItemsetIsPostfix && patternBuffer[positionToBeMatched] == token) {
						positionToBeMatched++;
						if (positionToBeMatched > lastBufferPosition) {
							currentItemsetIsPostfix = true;
						}
					}
				} else if (token == -1) {
					// move to next itemset in the sequence
					isFirstItemset = false;
					currentItemsetIsPostfix = false;
					positionToBeMatched = firstPositionOfLastItemsetInBuffer;
				}
			}
		}

		// update memory usage statistics
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Prints statistics about the algorithm execution to the standard output.
	 */
	public void printStatistics() {
		StringBuilder r = new StringBuilder(200);
		r.append("=============  PREFIXSPAN - STATISTICS =============");
		r.append(System.lineSeparator());
		r.append("Total time ~ ");
		r.append(totalTime);
		r.append(System.lineSeparator());
		r.append(" Frequent sequences count : " + patternCount);
		r.append(System.lineSeparator());
		r.append(" Max memory (mb) : ");
		r.append(MemoryLogger.getInstance().getMaxMemory());
		r.append(System.lineSeparator());
		r.append(" minsup = " + minsuppAbsolute + " sequences.");
		r.append(System.lineSeparator());
		r.append(" Pattern count : ");
		r.append(patternCount);
		r.append(System.lineSeparator());
		r.append(" Minsup (as number of sequences)) : " + minsuppAbsolute);
		r.append(System.lineSeparator());
		r.append("===================================================");
		r.append(System.lineSeparator());
		// print patterns if they were kept in memory
		if (patterns != null) {
			patterns.printFrequentPatterns(sequenceCount, showSequenceIdentifiers);
		}
		System.out.println(r.toString());
	}

	/**
	 * Returns the minimum pattern length required for a pattern to be output, in number of items.
	 * 
	 * @return the minimum pattern length
	 */
	public int getMinimumPatternLength() {
		return minimumPatternLength;
	}

	/**
	 * Sets the minimum pattern length required for a pattern to be output, in number of items.
	 * 
	 * @param minimumPatternLength new minimum pattern length
	 */
	public void setMinimumPatternLength(int minimumPatternLength) {
		this.minimumPatternLength = minimumPatternLength;
	}

	/**
	 * Returns the maximum pattern length to be found, in number of items.
	 * 
	 * @return the maximum pattern length
	 */
	public int getMaximumPatternLength() {
		return maximumPatternLength;
	}

	/**
	 * Sets the maximum pattern length to be found, in number of items.
	 * 
	 * @param maximumPatternLength new maximum pattern length
	 */
	public void setMaximumPatternLength(int maximumPatternLength) {
		this.maximumPatternLength = maximumPatternLength;
	}

	/**
	 * Sets whether sequence identifiers should be shown for each pattern in the output.
	 * 
	 * @param showSequenceIdentifiers {@code true} to show sequence IDs, {@code false} otherwise
	 */
	public void setShowSequenceIdentifiers(boolean showSequenceIdentifiers) {
		this.showSequenceIdentifiers = showSequenceIdentifiers;
	}
}