/* This file is copyright (c) 2026 Philippe Fournier-Viger
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
* Do not remove copyright and license information
*/
package ca.pfv.spmf.algorithms.sequentialpatterns.huspull;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ca.pfv.spmf.tools.MemoryLogger;
/**
 * Implementation of the HUSP-ULL algorithm for high-utility sequential pattern
 * mining: Gan, Lin, Zhang, Fournier-Viger, Chao, Yu, "Fast Utility Mining on
 * Complex Sequences", arXiv:1904.12248, 2019.
 * <br/><br/>
 * Implementation notes (efficiency):
 * <ul>
 * <li>Items are remapped to dense ids preserving the original order.</li>
 * <li>Utilities and bounds are stored as long to avoid overflow.</li>
 * <li>The projected database of a pattern keeps, for each sequence, only the
 * end positions of its matches and the best utility for each end position.</li>
 * <li>Bounds are accumulated in reusable arrays with "touched" lists, and
 * per-sequence duplicates are avoided with stamp arrays instead of sets.</li>
 * <li>Ignored items are kept in one global array and restored (undone) when the
 * subtree of the node that ignored them is finished.</li>
 * </ul>
 */
public class AlgoHUSPULL {
	/** marker used inside the pattern buffer to separate two itemsets */
	private static final int SEPARATOR = -1;
	/** start time of the last execution (ms) */
	private long startTime;
	/** end time of the last execution (ms) */
	private long endTime;
	/** number of high-utility sequential patterns found */
	private long patternCount;
	/** absolute minimum utility threshold */
	private long minUtility;
	/** maximum number of items in a pattern */
	private int maxPatternLength = Integer.MAX_VALUE;
	/** writer for the output file */
	private BufferedWriter writer;
	/** utility-lists of the sequences that still contain a relevant item */
	private ULList[] database;
	/** original item id of each dense id */
	private int[] originalItemIds;
	/** number of relevant items (items with SWU >= minUtility) */
	private int itemCount;
	/** for each dense item, the indexes (in database) of the sequences containing it */
	private int[][] sequencesOfItem;
	/** items currently ignored by IPS (indexed by dense id) */
	private boolean[] ignored;
	/** stack of the items ignored, used to undo IPS when leaving a node */
	private int[] ignoredStack;
	/** number of items on the stack */
	private int ignoredTop;
	/** bound of each item for I-concatenation */
	private long[] iConcatenationBound;
	/** bound of each item for S-concatenation */
	private long[] sConcatenationBound;
	/** bound of each item for the union of I and S concatenation (IPS) */
	private long[] combinedBound;
	/** counter of each item, used as "already touched" flag, for I-concatenation */
	private int[] iConcatenationCount;
	/** counter of each item, used as "already touched" flag, for S-concatenation */
	private int[] sConcatenationCount;
	/** counter of each item, used as "already touched" flag, for the union bound */
	private int[] combinedCount;
	/** list of the items touched in the I-concatenation bound array, so reset is cheap */
	private int[] iConcatenationTouched;
	/** list of the items touched in the S-concatenation bound array, so reset is cheap */
	private int[] sConcatenationTouched;
	/** list of the items touched in the union bound array, so reset is cheap */
	private int[] combinedTouched;
	/** number of valid entries in the I-concatenation touched list */
	private int iConcatenationTouchedCount;
	/** number of valid entries in the S-concatenation touched list */
	private int sConcatenationTouchedCount;
	/** number of valid entries in the union touched list */
	private int combinedTouchedCount;
	/** stamp array avoiding counting an item twice for the same sequence (I-concatenation) */
	private int[] iConcatenationStamp;
	/** stamp array avoiding counting an item twice for the same sequence (S-concatenation) */
	private int[] sConcatenationStamp;
	/** stamp array avoiding counting an item twice for the same sequence (union bound) */
	private int[] combinedStamp;
	/** current stamp (one per sequence per scan) */
	private int currentStamp;
	/** current pattern: dense item ids, with SEPARATOR between itemsets */
	private int[] pattern = new int[64];
	/** number of valid entries in the pattern buffer */
	private int patternSize;
	/** scratch buffer of end positions used when building projected databases */
	private int[] bufferEndPositions = new int[64];
	/** scratch buffer of utilities used when building projected databases */
	private long[] bufferUtilities = new long[64];
	/**
	 * Projected database of a pattern. For each sequence containing the pattern,
	 * it stores the end positions of the matches (increasing, no duplicate) and,
	 * for each end position, the maximum utility of a match ending there.
	 */
	private static final class ProjectedDatabase {
		/** number of sequences stored */
		int size;
		/** index in database of each sequence */
		int[] sequenceIds;
		/** end positions of the matches in each sequence */
		int[][] endPositions;
		/** utility of the best match for each end position */
		long[][] utilities;
		/** utility of the pattern in the database (sum of the best per sequence) */
		long utility;
		/**
		 * Creates an empty projected database able to hold a given number of
		 * sequences.
		 *
		 * @param capacity the maximum number of sequences that will be added
		 */
		ProjectedDatabase(int capacity) {
			sequenceIds = new int[capacity];
			endPositions = new int[capacity][];
			utilities = new long[capacity][];
		}
		/**
		 * Adds a sequence to this projected database and updates the utility of
		 * the pattern.
		 *
		 * @param sequenceId       index of the sequence in database
		 * @param matchEndPositions end positions of the matches in the sequence
		 * @param matchUtilities   utility of the best match for each end position
		 */
		void add(int sequenceId, int[] matchEndPositions, long[] matchUtilities) {
			sequenceIds[size] = sequenceId;
			endPositions[size] = matchEndPositions;
			utilities[size] = matchUtilities;
			size++;
			long best = 0;
			for (long value : matchUtilities) {
				if (value > best) {
					best = value;
				}
			}
			utility += best;
		}
	}
	/** A sequence as read from the file, before pruning and remapping. */
	private static final class RawSequence {
		/** item of each element */
		int[] items;
		/** utility of each element */
		int[] utilities;
		/** index of the itemset of each element */
		int[] itemsetIds;
		/** total utility of the sequence (used for the SWU) */
		long total;
	}
	/**
	 * Runs the HUSP-ULL algorithm on an input file and writes the patterns found
	 * to an output file.
	 *
	 * @param input      path of the input file (SPMF utility sequence format)
	 * @param output     path of the output file
	 * @param minUtility absolute minimum utility threshold
	 * @throws IOException if an error occurs while reading or writing
	 */
	public void runAlgorithm(String input, String output, int minUtility) throws IOException {
		MemoryLogger.getInstance().reset();
		startTime = System.currentTimeMillis();
		this.minUtility = minUtility;
		patternCount = 0;
		patternSize = 0;
		ignoredTop = 0;
		currentStamp = 0;
		writer = new BufferedWriter(new FileWriter(output));
		try {
			List<RawSequence> rawSequences = readDatabase(input);
			buildUtilityLists(rawSequences);
			rawSequences = null; // allow garbage collection of the raw data
			allocateScratch();
			mineFirstLevel();
		} finally {
			writer.close();
		}
		MemoryLogger.getInstance().checkMemory();
		endTime = System.currentTimeMillis();
	}
	/**
	 * Reads all the sequences of the input file, skipping empty, comment and
	 * metadata lines.
	 *
	 * @param input path of the input file
	 * @return the list of sequences read
	 * @throws IOException if an error occurs while reading the file
	 */
	private List<RawSequence> readDatabase(String input) throws IOException {
		List<RawSequence> sequences = new ArrayList<RawSequence>();
		BufferedReader reader = new BufferedReader(new FileReader(input));
		try {
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				line = line.trim();
				// skip empty lines and comment / metadata lines
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}
				sequences.add(parseLine(line, lineNumber));
			}
		} finally {
			reader.close();
		}
		return sequences;
	}
	/**
	 * Parses one line such as {@code 1[5] 2[3] -1 3[4] -1 -2 SUtility:12},
	 * stopping at "-2" or at the "SUtility" token and recomputing the total
	 * utility from the items instead of trusting the SUtility value.
	 *
	 * @param line       the trimmed line to parse
	 * @param lineNumber the number of the line, used in error messages
	 * @return the sequence read from the line
	 * @throws IllegalArgumentException if a token is not a valid item[utility]
	 */
	private RawSequence parseLine(String line, int lineNumber) {
		String[] tokens = line.split("\\s+");
		int[] items = new int[tokens.length];
		int[] utilities = new int[tokens.length];
		int[] itemsetIds = new int[tokens.length];
		int elementCount = 0;
		int itemsetId = 0;
		boolean itemsetOpen = false; // true if the current itemset already has an item
		long total = 0;
		for (String token : tokens) {
			if (token.equals("-2") || token.startsWith("SUtility")) {
				break;
			}
			if (token.equals("-1")) {
				if (itemsetOpen) {
					itemsetId++;
					itemsetOpen = false;
				}
				continue;
			}
			int leftBracketPosition = token.indexOf('[');
			int rightBracketPosition = token.indexOf(']');
			if (leftBracketPosition <= 0 || rightBracketPosition <= leftBracketPosition + 1) {
				throw new IllegalArgumentException("Line " + lineNumber + ": invalid token '" + token + "'");
			}
			try {
				items[elementCount] = Integer.parseInt(token.substring(0, leftBracketPosition));
				utilities[elementCount] = Integer
						.parseInt(token.substring(leftBracketPosition + 1, rightBracketPosition));
			} catch (NumberFormatException exception) {
				throw new IllegalArgumentException("Line " + lineNumber + ": invalid token '" + token + "'");
			}
			if (items[elementCount] < 0) {
				throw new IllegalArgumentException("Line " + lineNumber + ": negative item '" + token + "'");
			}
			itemsetIds[elementCount] = itemsetId;
			itemsetOpen = true;
			total += utilities[elementCount];
			elementCount++;
		}
		RawSequence rawSequence = new RawSequence();
		rawSequence.items = Arrays.copyOf(items, elementCount);
		rawSequence.utilities = Arrays.copyOf(utilities, elementCount);
		rawSequence.itemsetIds = Arrays.copyOf(itemsetIds, elementCount);
		rawSequence.total = total;
		return rawSequence;
	}
	/**
	 * Computes the SWU of each item, removes the items below minUtility, remaps
	 * the remaining items to dense ids (same order as the original ids), and
	 * builds one UL-list per sequence while dropping sequences left empty.
	 *
	 * @param rawSequences the sequences read from the input file
	 */
	private void buildUtilityLists(List<RawSequence> rawSequences) {
		// 1) SWU of each item: entry = {swu, index of last sequence counted}
		Map<Integer, long[]> swu = new HashMap<Integer, long[]>();
		for (int sequenceIndex = 0; sequenceIndex < rawSequences.size(); sequenceIndex++) {
			RawSequence rawSequence = rawSequences.get(sequenceIndex);
			for (int itemIndex = 0; itemIndex < rawSequence.items.length; itemIndex++) {
				long[] entry = swu.get(rawSequence.items[itemIndex]);
				if (entry == null) {
					entry = new long[] { 0, -1 };
					swu.put(rawSequence.items[itemIndex], entry);
				}
				if (entry[1] != sequenceIndex) { // count each sequence once per item
					entry[0] += rawSequence.total;
					entry[1] = sequenceIndex;
				}
			}
		}
		// 2) keep relevant items and assign dense ids in increasing order
		List<Integer> kept = new ArrayList<Integer>();
		for (Map.Entry<Integer, long[]> swuEntry : swu.entrySet()) {
			if (swuEntry.getValue()[0] >= minUtility) {
				kept.add(swuEntry.getKey());
			}
		}
		Collections.sort(kept);
		itemCount = kept.size();
		originalItemIds = new int[itemCount];
		Map<Integer, Integer> denseOf = new HashMap<Integer, Integer>();
		for (int denseId = 0; denseId < itemCount; denseId++) {
			originalItemIds[denseId] = kept.get(denseId);
			denseOf.put(originalItemIds[denseId], denseId);
		}
		// 3) build the UL-lists
		List<ULList> lists = new ArrayList<ULList>();
		for (RawSequence rawSequence : rawSequences) {
			int length = rawSequence.items.length;
			int[] denseItems = new int[length];
			int[] itemUtilities = new int[length];
			int[] itemsetStarts = new int[length + 1];
			int elementCount = 0;
			int itemsetCount = 0;
			int readIndex = 0;
			while (readIndex < length) {
				int groupEnd = readIndex;
				int itemsetId = rawSequence.itemsetIds[readIndex];
				int groupStart = elementCount;
				while (groupEnd < length && rawSequence.itemsetIds[groupEnd] == itemsetId) {
					Integer denseId = denseOf.get(rawSequence.items[groupEnd]);
					if (denseId != null) {
						denseItems[elementCount] = denseId;
						itemUtilities[elementCount] = rawSequence.utilities[groupEnd];
						elementCount++;
					}
					groupEnd++;
				}
				if (elementCount > groupStart) {
					sortRange(denseItems, itemUtilities, groupStart, elementCount);
					// merge duplicate items inside the itemset (sum utilities)
					int writeIndex = groupStart;
					for (int position = groupStart + 1; position < elementCount; position++) {
						if (denseItems[position] == denseItems[writeIndex]) {
							itemUtilities[writeIndex] += itemUtilities[position];
						} else {
							writeIndex++;
							denseItems[writeIndex] = denseItems[position];
							itemUtilities[writeIndex] = itemUtilities[position];
						}
					}
					elementCount = writeIndex + 1;
					itemsetStarts[++itemsetCount] = elementCount;
				}
				readIndex = groupEnd;
			}
			if (elementCount > 0) {
				lists.add(new ULList(Arrays.copyOf(denseItems, elementCount),
						Arrays.copyOf(itemUtilities, elementCount), Arrays.copyOf(itemsetStarts, itemsetCount + 1)));
			}
		}
		database = lists.toArray(new ULList[0]);
		// 4) inverted index: item -> sequences containing it
		int[] sequenceCounts = new int[itemCount];
		for (ULList utilityList : database) {
			for (int item : utilityList.occItem) {
				sequenceCounts[item]++;
			}
		}
		sequencesOfItem = new int[itemCount][];
		for (int item = 0; item < itemCount; item++) {
			sequencesOfItem[item] = new int[sequenceCounts[item]];
			sequenceCounts[item] = 0;
		}
		for (int sequenceIndex = 0; sequenceIndex < database.length; sequenceIndex++) {
			for (int item : database[sequenceIndex].occItem) {
				sequencesOfItem[item][sequenceCounts[item]++] = sequenceIndex;
			}
		}
	}
	/**
	 * Sorts a range of an item array by increasing item id using insertion sort,
	 * and moves the parallel utility array accordingly.
	 *
	 * @param items     the item array
	 * @param utilities the utility array, parallel to items
	 * @param from      first index of the range (inclusive)
	 * @param to        last index of the range (exclusive)
	 */
	private static void sortRange(int[] items, int[] utilities, int from, int to) {
		for (int index = from + 1; index < to; index++) {
			int currentItem = items[index];
			int currentUtility = utilities[index];
			int shiftIndex = index - 1;
			while (shiftIndex >= from && items[shiftIndex] > currentItem) {
				items[shiftIndex + 1] = items[shiftIndex];
				utilities[shiftIndex + 1] = utilities[shiftIndex];
				shiftIndex--;
			}
			items[shiftIndex + 1] = currentItem;
			utilities[shiftIndex + 1] = currentUtility;
		}
	}
	/**
	 * Allocates the reusable arrays used during the search, sized by the number
	 * of relevant items.
	 */
	private void allocateScratch() {
		ignored = new boolean[itemCount];
		ignoredStack = new int[itemCount];
		iConcatenationBound = new long[itemCount];
		sConcatenationBound = new long[itemCount];
		combinedBound = new long[itemCount];
		iConcatenationCount = new int[itemCount];
		sConcatenationCount = new int[itemCount];
		combinedCount = new int[itemCount];
		iConcatenationTouched = new int[itemCount];
		sConcatenationTouched = new int[itemCount];
		combinedTouched = new int[itemCount];
		iConcatenationStamp = new int[itemCount];
		sConcatenationStamp = new int[itemCount];
		combinedStamp = new int[itemCount];
	}
	/**
	 * Explores the patterns made of a single item and recursively grows each of
	 * them.
	 *
	 * @throws IOException if an error occurs while writing a pattern
	 */
	private void mineFirstLevel() throws IOException {
		if (maxPatternLength < 1) {
			return;
		}
		for (int item = 0; item < itemCount; item++) {
			ProjectedDatabase projectedDatabase = buildFirstLevel(item);
			patternSize = 0;
			pushItem(item);
			savePatternIfHighUtility(projectedDatabase.utility);
			mine(projectedDatabase, 1);
			MemoryLogger.getInstance().checkMemory();
		}
	}
	/**
	 * Explores recursively all the patterns having the current pattern as prefix,
	 * applying IPS and LAS to select the I and S concatenations.
	 *
	 * @param projectedDatabase projected database of the current pattern
	 * @param patternLength     number of items in the current pattern
	 * @throws IOException if an error occurs while writing a pattern
	 */
	private void mine(ProjectedDatabase projectedDatabase, int patternLength) throws IOException {
		if (patternLength >= maxPatternLength) {
			return; // children would be too long
		}
		int undoMark = ignoredTop;
		// IPS + LAS: obtain the I and S candidates (sorted, increasing)
		int[][] candidates = analyze(projectedDatabase);
		int[] iCandidates = candidates[0];
		int[] sCandidates = candidates[1];
		int savedSize = patternSize;
		// I-concatenations
		for (int item : iCandidates) {
			ProjectedDatabase child = buildI(projectedDatabase, item);
			pushItem(item);
			savePatternIfHighUtility(child.utility);
			mine(child, patternLength + 1);
			patternSize = savedSize;
		}
		// S-concatenations
		for (int item : sCandidates) {
			ProjectedDatabase child = buildS(projectedDatabase, item);
			pushSeparator();
			pushItem(item);
			savePatternIfHighUtility(child.utility);
			mine(child, patternLength + 1);
			patternSize = savedSize;
		}
		// undo the items ignored by IPS at this node
		while (ignoredTop > undoMark) {
			ignored[ignoredStack[--ignoredTop]] = false;
		}
	}
	/**
	 * Computes the candidate items of the current pattern by applying IPS then
	 * LAS, marking the irrelevant items in {@code ignored} (the caller must undo
	 * this when leaving the node).
	 *
	 * @param projectedDatabase projected database of the current pattern
	 * @return an array {I-candidates, S-candidates}, each sorted by increasing
	 *         item id
	 */
	private int[][] analyze(ProjectedDatabase projectedDatabase) {
		int sequenceCount = projectedDatabase.size;
		long[][] remainingUtilities = new long[sequenceCount][];
		boolean reduced = ignoredTop > 0; // some items must be excluded
		for (int sequenceIndex = 0; sequenceIndex < sequenceCount; sequenceIndex++) {
			ULList utilityList = database[projectedDatabase.sequenceIds[sequenceIndex]];
			remainingUtilities[sequenceIndex] = reduced ? buildRem(utilityList) : utilityList.rem;
		}
		int ignoredBefore = ignoredTop;
		// first scan: I bounds, S bounds and the union bound used by IPS
		scan(projectedDatabase, remainingUtilities, true);
		for (int touchedIndex = 0; touchedIndex < combinedTouchedCount; touchedIndex++) {
			int item = combinedTouched[touchedIndex];
			if (combinedBound[item] < minUtility) {
				ignored[item] = true;
				ignoredStack[ignoredTop++] = item;
			}
		}
		// if items were ignored, the remaining utilities shrink: recompute
		// them and rescan to get tighter bounds for LAS
		if (ignoredTop > ignoredBefore) {
			resetBounds();
			for (int sequenceIndex = 0; sequenceIndex < sequenceCount; sequenceIndex++) {
				remainingUtilities[sequenceIndex] = buildRem(database[projectedDatabase.sequenceIds[sequenceIndex]]);
			}
			scan(projectedDatabase, remainingUtilities, false);
		}
		// LAS: keep the candidates whose bound reaches minUtility
		int[] iCandidates = extract(iConcatenationTouched, iConcatenationTouchedCount, iConcatenationBound);
		int[] sCandidates = extract(sConcatenationTouched, sConcatenationTouchedCount, sConcatenationBound);
		resetBounds();
		return new int[][] { iCandidates, sCandidates };
	}
	/**
	 * Scans the projected database and accumulates, for each candidate item, the
	 * sum of PEU(t,s) over the sequences where it can extend the pattern.
	 *
	 * @param projectedDatabase  projected database of the current pattern
	 * @param remainingUtilities remaining utilities to use for each sequence of
	 *                           projectedDatabase
	 * @param combine            true to also compute the union bound needed by IPS
	 */
	private void scan(ProjectedDatabase projectedDatabase, long[][] remainingUtilities, boolean combine) {
		for (int sequenceIndex = 0; sequenceIndex < projectedDatabase.size; sequenceIndex++) {
			ULList utilityList = database[projectedDatabase.sequenceIds[sequenceIndex]];
			int[] endPositions = projectedDatabase.endPositions[sequenceIndex];
			long[] utilities = projectedDatabase.utilities[sequenceIndex];
			long[] sequenceRemaining = remainingUtilities[sequenceIndex];
			// PEU(t,s): best (utility of a match + remaining utility after it)
			long peu = 0;
			for (int matchIndex = 0; matchIndex < endPositions.length; matchIndex++) {
				long candidateValue = utilities[matchIndex] + sequenceRemaining[endPositions[matchIndex]];
				if (candidateValue > peu) {
					peu = candidateValue;
				}
			}
			nextStamp();
			// I-candidates: items after a match end inside the same itemset.
			// Ends are increasing, so only the first end of each itemset needs
			// to be examined (its range contains the ranges of the later ones).
			int lastItemset = -1;
			for (int matchIndex = 0; matchIndex < endPositions.length; matchIndex++) {
				int endPosition = endPositions[matchIndex];
				int itemsetIndex = utilityList.setOf[endPosition];
				if (itemsetIndex == lastItemset) {
					continue;
				}
				lastItemset = itemsetIndex;
				int itemsetEnd = utilityList.setStart[itemsetIndex + 1];
				for (int position = endPosition + 1; position < itemsetEnd; position++) {
					int item = utilityList.item[position];
					if (ignored[item] || iConcatenationStamp[item] == currentStamp) {
						continue;
					}
					iConcatenationStamp[item] = currentStamp;
					if (iConcatenationCount[item]++ == 0) {
						iConcatenationTouched[iConcatenationTouchedCount++] = item;
					}
					iConcatenationBound[item] += peu;
					if (combine) {
						addCombined(item, peu);
					}
				}
			}
			// S-candidates: items in the itemsets after the first match
			int startPosition = utilityList.setStart[utilityList.setOf[endPositions[0]] + 1];
			for (int position = startPosition; position < utilityList.item.length; position++) {
				int item = utilityList.item[position];
				if (ignored[item] || sConcatenationStamp[item] == currentStamp) {
					continue;
				}
				sConcatenationStamp[item] = currentStamp;
				if (sConcatenationCount[item]++ == 0) {
					sConcatenationTouched[sConcatenationTouchedCount++] = item;
				}
				sConcatenationBound[item] += peu;
				if (combine) {
					addCombined(item, peu);
				}
			}
		}
	}
	/**
	 * Adds a PEU value to the union bound of an item, at most once per sequence.
	 *
	 * @param item the item (dense id)
	 * @param peu  the PEU of the pattern in the current sequence
	 */
	private void addCombined(int item, long peu) {
		if (combinedStamp[item] != currentStamp) {
			combinedStamp[item] = currentStamp;
			if (combinedCount[item]++ == 0) {
				combinedTouched[combinedTouchedCount++] = item;
			}
			combinedBound[item] += peu;
		}
	}
	/**
	 * Extracts, in increasing order, the touched items whose bound reaches
	 * minUtility.
	 *
	 * @param touched      the list of touched items
	 * @param touchedCount the number of valid entries in the touched list
	 * @param bound        the bound array indexed by item
	 * @return the sorted array of items that satisfy the threshold
	 */
	private int[] extract(int[] touched, int touchedCount, long[] bound) {
		int count = 0;
		for (int index = 0; index < touchedCount; index++) {
			if (bound[touched[index]] >= minUtility) {
				count++;
			}
		}
		int[] result = new int[count];
		int resultIndex = 0;
		for (int index = 0; index < touchedCount; index++) {
			if (bound[touched[index]] >= minUtility) {
				result[resultIndex++] = touched[index];
			}
		}
		Arrays.sort(result);
		return result;
	}
	/**
	 * Resets the bound arrays by clearing only the entries that were used.
	 */
	private void resetBounds() {
		for (int index = 0; index < iConcatenationTouchedCount; index++) {
			iConcatenationBound[iConcatenationTouched[index]] = 0;
			iConcatenationCount[iConcatenationTouched[index]] = 0;
		}
		for (int index = 0; index < sConcatenationTouchedCount; index++) {
			sConcatenationBound[sConcatenationTouched[index]] = 0;
			sConcatenationCount[sConcatenationTouched[index]] = 0;
		}
		for (int index = 0; index < combinedTouchedCount; index++) {
			combinedBound[combinedTouched[index]] = 0;
			combinedCount[combinedTouched[index]] = 0;
		}
		iConcatenationTouchedCount = 0;
		sConcatenationTouchedCount = 0;
		combinedTouchedCount = 0;
	}
	/**
	 * Advances the stamp used to detect repeated items within a sequence, and
	 * clears the stamp arrays if the counter would overflow.
	 */
	private void nextStamp() {
		if (++currentStamp == Integer.MAX_VALUE) {
			Arrays.fill(iConcatenationStamp, 0);
			Arrays.fill(sConcatenationStamp, 0);
			Arrays.fill(combinedStamp, 0);
			currentStamp = 1;
		}
	}
	/**
	 * Computes the remaining utilities of a sequence when the currently ignored
	 * items are removed, using one backward pass.
	 *
	 * @param utilityList the utility-list of the sequence
	 * @return the remaining utility after each element of the sequence
	 */
	private long[] buildRem(ULList utilityList) {
		int elementCount = utilityList.length();
		long[] remaining = new long[elementCount];
		long accumulatedUtility = 0;
		for (int position = elementCount - 1; position >= 0; position--) {
			remaining[position] = accumulatedUtility;
			if (!ignored[utilityList.item[position]]) {
				accumulatedUtility += utilityList.util[position];
			}
		}
		return remaining;
	}
	/**
	 * Builds the projected database of the pattern made of the single item.
	 *
	 * @param item the item (dense id)
	 * @return the projected database of the pattern containing only item
	 */
	private ProjectedDatabase buildFirstLevel(int item) {
		int[] sequenceIds = sequencesOfItem[item];
		ProjectedDatabase projectedDatabase = new ProjectedDatabase(sequenceIds.length);
		for (int sequenceId : sequenceIds) {
			ULList utilityList = database[sequenceId];
			int occurrenceIndex = utilityList.occIndex(item);
			int from = utilityList.occStart[occurrenceIndex];
			int to = utilityList.occStart[occurrenceIndex + 1];
			int[] endPositions = new int[to - from];
			long[] utilities = new long[to - from];
			for (int index = 0; index < endPositions.length; index++) {
				endPositions[index] = utilityList.occPos[from + index];
				utilities[index] = utilityList.util[endPositions[index]];
			}
			projectedDatabase.add(sequenceId, endPositions, utilities);
		}
		return projectedDatabase;
	}
	/**
	 * Builds the projected database of the pattern obtained by I-concatenation of
	 * an item, keeping only the best utility for each new end position.
	 *
	 * @param projectedDatabase projected database of the current pattern
	 * @param item              the item to append to the last itemset (dense id)
	 * @return the projected database of the extended pattern
	 */
	private ProjectedDatabase buildI(ProjectedDatabase projectedDatabase, int item) {
		ProjectedDatabase child = new ProjectedDatabase(projectedDatabase.size);
		for (int sequenceIndex = 0; sequenceIndex < projectedDatabase.size; sequenceIndex++) {
			ULList utilityList = database[projectedDatabase.sequenceIds[sequenceIndex]];
			int[] endPositions = projectedDatabase.endPositions[sequenceIndex];
			long[] utilities = projectedDatabase.utilities[sequenceIndex];
			ensureBuffers(endPositions.length);
			int matchCount = 0;
			for (int matchIndex = 0; matchIndex < endPositions.length; matchIndex++) {
				int endPosition = endPositions[matchIndex];
				int foundPosition = utilityList.findInRange(item, endPosition + 1,
						utilityList.setStart[utilityList.setOf[endPosition] + 1]);
				if (foundPosition < 0) {
					continue;
				}
				long newUtility = utilities[matchIndex] + utilityList.util[foundPosition];
				if (matchCount > 0 && bufferEndPositions[matchCount - 1] == foundPosition) {
					if (newUtility > bufferUtilities[matchCount - 1]) {
						bufferUtilities[matchCount - 1] = newUtility;
					}
				} else {
					bufferEndPositions[matchCount] = foundPosition;
					bufferUtilities[matchCount] = newUtility;
					matchCount++;
				}
			}
			if (matchCount > 0) {
				child.add(projectedDatabase.sequenceIds[sequenceIndex],
						Arrays.copyOf(bufferEndPositions, matchCount), Arrays.copyOf(bufferUtilities, matchCount));
			}
		}
		return child;
	}
	/**
	 * Builds the projected database of the pattern obtained by S-concatenation of
	 * an item, pairing each occurrence of the item with the best match of the
	 * pattern ending in a strictly earlier itemset.
	 *
	 * @param projectedDatabase projected database of the current pattern
	 * @param item              the item to append in a new itemset (dense id)
	 * @return the projected database of the extended pattern
	 */
	private ProjectedDatabase buildS(ProjectedDatabase projectedDatabase, int item) {
		ProjectedDatabase child = new ProjectedDatabase(projectedDatabase.size);
		for (int sequenceIndex = 0; sequenceIndex < projectedDatabase.size; sequenceIndex++) {
			ULList utilityList = database[projectedDatabase.sequenceIds[sequenceIndex]];
			int occurrenceIndex = utilityList.occIndex(item);
			if (occurrenceIndex < 0) {
				continue;
			}
			int[] endPositions = projectedDatabase.endPositions[sequenceIndex];
			long[] utilities = projectedDatabase.utilities[sequenceIndex];
			int from = utilityList.occStart[occurrenceIndex];
			int to = utilityList.occStart[occurrenceIndex + 1];
			ensureBuffers(to - from);
			int matchCount = 0;
			int matchIndex = 0;
			long best = 0;
			for (int occurrenceOffset = from; occurrenceOffset < to; occurrenceOffset++) {
				int position = utilityList.occPos[occurrenceOffset];
				int itemsetIndex = utilityList.setOf[position];
				// both lists are increasing, so one linear pass is enough
				while (matchIndex < endPositions.length && utilityList.setOf[endPositions[matchIndex]] < itemsetIndex) {
					if (utilities[matchIndex] > best) {
						best = utilities[matchIndex];
					}
					matchIndex++;
				}
				if (matchIndex > 0) { // at least one earlier match exists
					bufferEndPositions[matchCount] = position;
					bufferUtilities[matchCount] = best + utilityList.util[position];
					matchCount++;
				}
			}
			if (matchCount > 0) {
				child.add(projectedDatabase.sequenceIds[sequenceIndex],
						Arrays.copyOf(bufferEndPositions, matchCount), Arrays.copyOf(bufferUtilities, matchCount));
			}
		}
		return child;
	}
	/**
	 * Makes sure the scratch buffers can hold at least a given number of
	 * entries.
	 *
	 * @param capacity the minimum capacity required
	 */
	private void ensureBuffers(int capacity) {
		if (bufferEndPositions.length < capacity) {
			bufferEndPositions = new int[capacity * 2];
			bufferUtilities = new long[capacity * 2];
		}
	}
	/**
	 * Appends an item to the current pattern buffer.
	 *
	 * @param item the item to append (dense id)
	 */
	private void pushItem(int item) {
		ensurePatternCapacity();
		pattern[patternSize++] = item;
	}
	/**
	 * Appends an itemset separator to the current pattern buffer.
	 */
	private void pushSeparator() {
		ensurePatternCapacity();
		pattern[patternSize++] = SEPARATOR;
	}
	/**
	 * Doubles the size of the pattern buffer when it is full.
	 */
	private void ensurePatternCapacity() {
		if (patternSize == pattern.length) {
			pattern = Arrays.copyOf(pattern, pattern.length * 2);
		}
	}
	/**
	 * Writes the current pattern to the output file if its utility reaches the
	 * minimum utility threshold.
	 *
	 * @param utility the utility of the current pattern
	 * @throws IOException if an error occurs while writing
	 */
	private void savePatternIfHighUtility(long utility) throws IOException {
		if (utility < minUtility) {
			return;
		}
		StringBuilder stringBuilder = new StringBuilder();
		for (int index = 0; index < patternSize; index++) {
			if (pattern[index] == SEPARATOR) {
				stringBuilder.append("-1 ");
			} else {
				stringBuilder.append(originalItemIds[pattern[index]]).append(' ');
			}
		}
		stringBuilder.append("-1 #UTIL: ").append(utility);
		writer.write(stringBuilder.toString());
		writer.newLine();
		patternCount++;
	}
	/**
	 * Sets the maximum number of items in the patterns to be found.
	 *
	 * @param maxPatternLength the maximum pattern length (number of items)
	 */
	public void setMaxPatternLength(int maxPatternLength) {
		this.maxPatternLength = maxPatternLength;
	}
	/**
	 * Prints statistics about the last execution to the standard output.
	 */
	public void printStats() {
		System.out.println("=============  HUSP-ULL v. 2.69 - STATS =============");
		System.out.println(" Minimum utility: " + minUtility);
		System.out.println(" Total time: " + (endTime - startTime) + " ms");
		System.out.println(" Max memory: " + MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println(" High-utility sequential patterns count: " + patternCount);
		System.out.println("=============================================");
	}
}