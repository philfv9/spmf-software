/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * 
 * Do not remove copyright and license information.
 */
package ca.pfv.spmf.algorithms.sequentialpatterns.flosm_fgensm_gfscr;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.datastructures.collections.list.ArrayListInt;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the FGenSM algorithm for mining frequent
 * generator sequential patterns using the local pruning strategy (LPGEN)
 * described by Bac Le, Hai Duong, Tin Truong, and Philippe Fournier-Viger in
 * Knowledge and Information Systems (2017, volume 53, issue 1, pages 71-107).
 * 
 * It is a re-implementation based on the paper.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoFGENSM {

	/** Time at which the algorithm execution started in milliseconds. */
	private long startTime;

	/** Time at which the algorithm execution ended in milliseconds. */
	private long endTime;

	/** Number of frequent generator sequential patterns discovered. */
	private int patternCount;

	/** Number of generator candidate patterns evaluated by UpdateGeneratorPattern. */
	private int candidateCount;

	/** Minimum support threshold expressed as an absolute sequence count. */
	private int minimumSupport = 0;

	/** Minimum number of items a pattern must contain to be written to output. */
	private int minimumPatternLength = 0;

	/** Maximum number of items a pattern may contain. */
	private int maximumPatternLength = 1000;

	/** Whether to include supporting sequence identifiers in the output file. */
	private boolean outputSequenceIdentifiers;

	/** Writer used to write discovered generator patterns to the output file. */
	private BufferedWriter writer = null;

	/** In-memory buffer accumulating output text before flushing to disk. */
	private final StringBuilder outputBuffer = new StringBuilder(4096);

	/** Output buffer size threshold that triggers a flush to disk. */
	private static final int OUTPUT_BUFFER_FLUSH_THRESHOLD = 1 << 18;

	/** Buffer size in bytes used when reading the input file. */
	private static final int INPUT_BUFFER_SIZE = 1 << 16;

	/** Bitset word arrays of each frequent item indexed by contiguous internal item identifier. */
	private long[][] verticalBitmapWords = null;

	/** Number of active 64-bit words in each frequent item's vertical bitset. */
	private int[] verticalBitmapWordCount = null;

	/** Maps contiguous internal item identifiers back to original item identifiers. */
	private int[] internalToOriginalItem = null;

	/** Ending bit index of each sequence in the flattened database representation. */
	private int[] sequenceEndBit = null;

	/** Maps each global bit position directly to the sequence identifier that owns it. */
	private int[] bitToSequenceId = null;

	/** Scratch buffer holding occupied sequence identifiers while building a candidate extension. */
	private int[] candidateSequenceIdBuffer = null;

	/** Scratch buffer holding the first set bit per occupied sequence while building a candidate extension. */
	private int[] candidateFirstBitBuffer = null;

	/** Reusable scratch array of 64-bit words used when intersecting bitmaps during an I-step. */
	private long[] scratchBitmapWords = new long[4];

	/** True if every itemset in the database contains at most one item. */
	private boolean onlySingletonItemsets = true;

	/** Counter used to throttle periodic memory usage checks during search. */
	private int memoryCheckCounter = 0;

	/** Number of search nodes visited between two memory usage checks. */
	private static final int MEMORY_CHECK_INTERVAL = 1000;

	/** Reusable buffer storing integer tokens parsed from a line of the input file. */
	private int[] tokenBuffer = new int[64];

	/** Reusable string builder used to format a pattern line before output. */
	private final StringBuilder patternStringBuilder = new StringBuilder(64);

	/** Flat token buffer holding the sequence currently being explored (with -1 separators). */
	private int[] patternBuffer = new int[64];

	/** Number of valid tokens currently stored in patternBuffer. */
	private int patternLength = 0;

	/** Number of frequent items in the dense internal item identifier space. */
	private int frequentItemCount = 0;

	/** Dense co-occurrence matrix for item pairs appearing in subsequent itemsets. */
	private int[][] cooccurrenceAfterMatrix = null;

	/** Dense co-occurrence matrix for item pairs appearing within the same itemset. */
	private int[][] cooccurrenceEqualsMatrix = null;

	/** Sparse fallback co-occurrence map for item pairs appearing in subsequent itemsets. */
	private Map<Integer, Map<Integer, Integer>> cooccurrenceMapAfter = null;

	/** Sparse fallback co-occurrence map for item pairs appearing within the same itemset. */
	private Map<Integer, Map<Integer, Integer>> cooccurrenceMapEquals = null;

	/** Indicates whether each internal item has at least one frequent same-itemset partner with a larger identifier. */
	private boolean[] itemHasEqualsPartner = null;

	/** Indicates whether each internal item has at least one frequent subsequent-itemset partner. */
	private boolean[] itemHasAfterPartner = null;

	/** Maximum frequent item count for which dense 2D co-occurrence matrices are used. */
	private static final int DENSE_COOCCURRENCE_LIMIT = 1500;

	/** Discovered generator sequential patterns grouped by item count and sequence identifier sum. */
	private List<Map<Integer, List<GeneratorPattern>>> generatorPatterns = null;

	/** Empty integer array reused when a node has no candidate items and for the empty generator. */
	private static final int[] EMPTY_INT_ARRAY = new int[0];

	/** Empty pattern node array reused when a node has no extensions. */
	private static final PatternNode[] EMPTY_PATTERN_ARRAY = new PatternNode[0];

	/**
	 * Represents a pattern node in the FGenSM search tree along with its local
	 * pruning flags and vertical occurrence data.
	 */
	public static class PatternNode {

		/** Total number of items contained in this pattern (length of the sequence). */
		int itemCount;

		/** Sum of even original item identifiers in this pattern. */
		long sumOfEvenItems;

		/** Sum of odd original item identifiers in this pattern. */
		long sumOfOddItems;

		/** Bitset words storing all itemset occurrences when multi-item itemsets exist. */
		long[] bitmapWords;

		/** Number of valid 64-bit words in bitmapWords. */
		int bitmapWordCount;

		/** Supporting sequence identifiers in ascending order. */
		int[] occupiedSequenceIds;

		/** First set bit position of this pattern inside each supporting sequence. */
		int[] occupiedFirstBits;

		/** Absolute support of this pattern. */
		int support;

		/** Absolute support of the immediate parent pattern. */
		int parentSupport;

		/** Sum of sequence identifiers containing this pattern. */
		int sequenceIdSum;

		/** Measure SE(D_p) representing the total remaining itemset size in the projected database. */
		long sequenceExtensionMeasure;

		/** True if this pattern was created by a sequence extension, false if by an itemset extension. */
		boolean isSequenceExtension;

		/** True if the parent of this pattern was created by a sequence extension. */
		boolean parentIsSequenceExtension;

		/** Internal identifier of the last item added to form this pattern. */
		int lastItem;

		/** Internal identifier of the last item of the parent pattern. */
		int parentLastItem;

		/** Flag indicating whether extensions of this pattern should be explored. */
		boolean doExtension;

		/** Flag indicating whether sequence extensions of this pattern should be explored. */
		boolean doSequenceExtension;
	}

	/**
	 * Represents a frequent generator sequential pattern discovered by FGenSM.
	 */
	public static class GeneratorPattern {

		/** Flat token buffer of item identifiers with -1 as itemset separators. */
		int[] buffer;

		/** Number of valid tokens in buffer. */
		int length;

		/** Total number of items in the pattern. */
		int itemCount;

		/** Sum of even original item identifiers in this pattern. */
		long sumOfEvenItems;

		/** Sum of odd original item identifiers in this pattern. */
		long sumOfOddItems;

		/** Supporting sequence identifiers retained when sequence identifier output is enabled. */
		int[] sequenceIds;

		/** Absolute support of the pattern. */
		int support;

		/** Sum of sequence identifiers containing this pattern. */
		int sequenceIdSum;

		/**
		 * Returns the absolute support of this generator sequential pattern.
		 *
		 * @return the support of the pattern
		 */
		public int getSupport() {
			return support;
		}

		/**
		 * Returns a textual representation of this pattern and its support.
		 *
		 * @return the string representation of this pattern
		 */
		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			for (int index = 0; index < length; index++) {
				builder.append(buffer[index]).append(" ");
			}
			builder.append("#SUP: ").append(support);
			return builder.toString();
		}
	}

	/**
	 * Constructs a new instance of the FGenSM algorithm.
	 */
	public AlgoFGENSM() {
	}

	/**
	 * Runs the FGenSM algorithm on an input sequence database file and writes the
	 * discovered frequent generator sequential patterns to an output file.
	 *
	 * @param input                  the path of the input sequence database file
	 * @param outputFilePath         the path of the output file, or null to keep results in memory only
	 * @param minimumSupportRelative the relative minimum support threshold in (0, 1]
	 * @return the discovered generator sequential patterns grouped by item count and sequence identifier sum
	 * @throws IOException if an error occurs while reading the input file or writing the output file
	 */
	public List<Map<Integer, List<GeneratorPattern>>> runAlgorithm(String input, String outputFilePath,
			double minimumSupportRelative) throws IOException {
		if (outputFilePath != null) {
			writer = new BufferedWriter(new FileWriter(outputFilePath), 1 << 16);
			writer.write("@FILETYPE=\"Frequent sequential generators\"");
			writer.append(System.lineSeparator());
			writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
			writer.append(System.lineSeparator());
		} else {
			writer = null;
		}

		patternCount = 0;
		candidateCount = 0;
		memoryCheckCounter = 0;
		outputBuffer.setLength(0);
		MemoryLogger.getInstance().reset();

		startTime = System.currentTimeMillis();
		try {
			fgensm(input, minimumSupportRelative);
			translatePatternsToOriginalIds();
			if (writer != null) {
				writeResultToFile();
			} else {
				recountPatternsForMinimumLength();
			}
		} finally {
			if (writer != null) {
				if (outputBuffer.length() > 0) {
					writer.write(outputBuffer.toString());
					outputBuffer.setLength(0);
				}
				writer.close();
			}
		}
		endTime = System.currentTimeMillis();
		return generatorPatterns;
	}

	/**
	 * Translates the internal item identifiers stored in all discovered generator
	 * patterns back to their original item identifiers from the input file.
	 */
	private void translatePatternsToOriginalIds() {
		if (generatorPatterns == null || internalToOriginalItem == null) {
			return;
		}
		for (Map<Integer, List<GeneratorPattern>> level : generatorPatterns) {
			for (List<GeneratorPattern> patternList : level.values()) {
				for (GeneratorPattern pattern : patternList) {
					for (int index = 0; index < pattern.length; index++) {
						if (pattern.buffer[index] != -1) {
							pattern.buffer[index] = internalToOriginalItem[pattern.buffer[index]];
						}
					}
				}
			}
		}
	}

	/**
	 * Updates patternCount when no output file is written so that minimum pattern
	 * length filtering is reflected in the statistics.
	 */
	private void recountPatternsForMinimumLength() {
		patternCount = 0;
		if (generatorPatterns == null) {
			return;
		}
		for (Map<Integer, List<GeneratorPattern>> level : generatorPatterns) {
			for (List<GeneratorPattern> patternList : level.values()) {
				for (GeneratorPattern pattern : patternList) {
					if (pattern.itemCount >= minimumPatternLength) {
						patternCount++;
					}
				}
			}
		}
	}

	/**
	 * Checks whether a line of the input file is empty or a comment/metadata line
	 * that should be skipped.
	 *
	 * @param line the input line to inspect
	 * @return true if the line should be ignored, false otherwise
	 */
	private static boolean isSkippableLine(String line) {
		return line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@';
	}

	/**
	 * Splits a line of the input file into integer tokens and grows tokenBuffer if
	 * needed.
	 *
	 * @param line   the input line to tokenize
	 * @param buffer the array into which parsed integers are stored
	 * @return the number of integer tokens parsed from the line
	 */
	private int tokenizeLine(String line, int[] buffer) {
		int lineLength = line.length();
		int count = 0;
		int position = 0;
		while (position < lineLength) {
			while (position < lineLength && line.charAt(position) == ' ') {
				position++;
			}
			if (position >= lineLength) {
				break;
			}

			boolean negative = false;
			if (line.charAt(position) == '-') {
				negative = true;
				position++;
			}

			int value = 0;
			while (position < lineLength && line.charAt(position) != ' ') {
				value = value * 10 + (line.charAt(position) - '0');
				position++;
			}
			if (count == buffer.length) {
				buffer = Arrays.copyOf(buffer, buffer.length * 2);
			}
			buffer[count++] = negative ? -value : value;
		}
		tokenBuffer = buffer;
		return count;
	}

	/**
	 * Ensures that patternBuffer can hold at least the requested number of tokens.
	 *
	 * @param minimumCapacity the minimum required capacity of patternBuffer
	 */
	private void ensurePatternCapacity(int minimumCapacity) {
		if (minimumCapacity > patternBuffer.length) {
			patternBuffer = Arrays.copyOf(patternBuffer, Math.max(minimumCapacity, patternBuffer.length * 2));
		}
	}

	/**
	 * Executes the FGenSM mining process (Figure 7): builds the vertical database
	 * and co-occurrence structures, initializes frequent 1-item generator
	 * candidates, and invokes HybridSearch on each frequent 1-item pattern.
	 *
	 * @param input                  the path of the input sequence database file
	 * @param minimumSupportRelative the relative minimum support threshold
	 * @throws IOException if an error occurs while reading the input file
	 */
	private void fgensm(String input, double minimumSupportRelative) throws IOException {
		generatorPatterns = new ArrayList<>(20);
		generatorPatterns.add(new HashMap<>());
		generatorPatterns.add(new HashMap<>());

		int[] database = new int[1024];
		int databaseLength = 0;
		int[] lineStart = new int[64];
		int[] lineLength = new int[64];
		int lineCount = 0;

		ArrayListInt sequencesSizeList = new ArrayListInt();
		int[] itemSupport = new int[64];
		int[] itemLastSequenceId = new int[64];
		Arrays.fill(itemLastSequenceId, -1);
		int maximumItemId = 0;
		int bitIndex = 0;
		int sequenceId = 0;
		boolean sawMultiItemItemset = false;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))),
				INPUT_BUFFER_SIZE)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (isSkippableLine(line)) {
					continue;
				}

				int tokenCount = tokenizeLine(line, tokenBuffer);
				int[] tokens = tokenBuffer;

				if (databaseLength + tokenCount > database.length) {
					int newCapacity = Math.max(database.length * 2, databaseLength + tokenCount);
					database = Arrays.copyOf(database, newCapacity);
				}
				System.arraycopy(tokens, 0, database, databaseLength, tokenCount);

				if (lineCount == lineStart.length) {
					lineStart = Arrays.copyOf(lineStart, lineStart.length * 2);
					lineLength = Arrays.copyOf(lineLength, lineLength.length * 2);
				}
				lineStart[lineCount] = databaseLength;
				lineLength[lineCount] = tokenCount;
				lineCount++;
				databaseLength += tokenCount;

				sequencesSizeList.add(bitIndex);
				int itemsInCurrentItemset = 0;

				for (int tokenOffset = 0; tokenOffset < tokenCount; tokenOffset++) {
					int token = tokens[tokenOffset];
					if (token == -1) {
						bitIndex++;
						itemsInCurrentItemset = 0;
					} else if (token == -2) {
						sequenceId++;
					} else {
						if (!sawMultiItemItemset) {
							itemsInCurrentItemset++;
							if (itemsInCurrentItemset > 1) {
								sawMultiItemItemset = true;
							}
						}
						if (token >= itemSupport.length) {
							int oldLength = itemSupport.length;
							int newCapacity = Math.max(token + 1, oldLength * 2);
							itemSupport = Arrays.copyOf(itemSupport, newCapacity);
							itemLastSequenceId = Arrays.copyOf(itemLastSequenceId, newCapacity);
							Arrays.fill(itemLastSequenceId, oldLength, newCapacity, -1);
						}
						if (token > maximumItemId) {
							maximumItemId = token;
						}
						if (itemLastSequenceId[token] != sequenceId) {
							itemSupport[token]++;
							itemLastSequenceId[token] = sequenceId;
						}
					}
				}
			}
		}

		int lastBitIndex = bitIndex - 1;
		onlySingletonItemsets = !sawMultiItemItemset;

		int numberOfSequences = sequencesSizeList.size();
		minimumSupport = (int) Math.ceil((minimumSupportRelative * numberOfSequences) - 0.00001);
		if (minimumSupport <= 0) {
			minimumSupport = 1;
		}

		if (numberOfSequences == 0 || lastBitIndex < 0) {
			return;
		}

		int[] sequencesSize = new int[numberOfSequences];
		for (int index = 0; index < numberOfSequences; index++) {
			sequencesSize[index] = sequencesSizeList.get(index);
		}
		sequencesSizeList = null;

		sequenceEndBit = new int[numberOfSequences];
		for (int index = 0; index < numberOfSequences - 1; index++) {
			sequenceEndBit[index] = sequencesSize[index + 1] - 1;
		}
		sequenceEndBit[numberOfSequences - 1] = lastBitIndex;

		if (!onlySingletonItemsets) {
			bitToSequenceId = new int[lastBitIndex + 1];
			for (int currentSid = 0; currentSid < numberOfSequences; currentSid++) {
				Arrays.fill(bitToSequenceId, sequencesSize[currentSid], sequenceEndBit[currentSid] + 1, currentSid);
			}
		} else {
			bitToSequenceId = null;
		}

		candidateSequenceIdBuffer = new int[numberOfSequences];
		candidateFirstBitBuffer = new int[numberOfSequences];

		ArrayListInt frequentOriginalIdsList = new ArrayListInt();
		ArrayListInt frequentSupportsList = new ArrayListInt();
		for (int originalId = 0; originalId <= maximumItemId; originalId++) {
			int supportCount = itemSupport[originalId];
			if (supportCount >= minimumSupport) {
				int internalId = frequentOriginalIdsList.size();
				frequentOriginalIdsList.add(originalId);
				frequentSupportsList.add(supportCount);
				itemSupport[originalId] = internalId;
			} else {
				itemSupport[originalId] = -1;
			}
		}
		int[] itemRemap = itemSupport;
		itemSupport = null;
		itemLastSequenceId = null;

		frequentItemCount = frequentOriginalIdsList.size();
		internalToOriginalItem = new int[frequentItemCount];
		for (int index = 0; index < frequentItemCount; index++) {
			internalToOriginalItem[index] = frequentOriginalIdsList.get(index);
		}
		frequentOriginalIdsList = null;

		if (frequentItemCount == 0) {
			addEmptySetGenerator(numberOfSequences);
			return;
		}

		PatternNode[] rootSequenceExtensions = new PatternNode[frequentItemCount];
		for (int internalId = 0; internalId < frequentItemCount; internalId++) {
			int itemSupportCount = frequentSupportsList.get(internalId);
			PatternNode node = new PatternNode();
			node.itemCount = 1;
			long originalItem = internalToOriginalItem[internalId];
			node.sumOfEvenItems = (originalItem % 2 == 0) ? originalItem : 0L;
			node.sumOfOddItems = (originalItem % 2 != 0) ? originalItem : 0L;
			node.support = itemSupportCount;
			node.parentSupport = numberOfSequences;
			node.occupiedSequenceIds = new int[itemSupportCount];
			node.occupiedFirstBits = new int[itemSupportCount];
			node.isSequenceExtension = true;
			node.parentIsSequenceExtension = false;
			node.lastItem = internalId;
			node.parentLastItem = -1;
			node.doExtension = true;
			node.doSequenceExtension = true;
			rootSequenceExtensions[internalId] = node;
		}
		frequentSupportsList = null;

		verticalBitmapWords = new long[frequentItemCount][];
		verticalBitmapWordCount = new int[frequentItemCount];

		boolean useDenseCooccurrence = frequentItemCount <= DENSE_COOCCURRENCE_LIMIT;
		if (useDenseCooccurrence) {
			cooccurrenceAfterMatrix = new int[frequentItemCount][frequentItemCount];
			cooccurrenceEqualsMatrix = onlySingletonItemsets ? null : new int[frequentItemCount][frequentItemCount];
			cooccurrenceMapAfter = null;
			cooccurrenceMapEquals = null;
		} else {
			cooccurrenceAfterMatrix = null;
			cooccurrenceEqualsMatrix = null;
			cooccurrenceMapAfter = new HashMap<>(frequentItemCount);
			cooccurrenceMapEquals = onlySingletonItemsets ? null : new HashMap<>(frequentItemCount);
		}

		itemHasEqualsPartner = new boolean[frequentItemCount];
		itemHasAfterPartner = new boolean[frequentItemCount];

		int[] seenFirstItemSequenceId = new int[frequentItemCount];
		int[] seenSecondItemSequenceId = new int[frequentItemCount];
		int[] lastSequenceForItem = new int[frequentItemCount];
		int[] itemOccupiedCursor = new int[frequentItemCount];
		Arrays.fill(seenFirstItemSequenceId, -1);
		Arrays.fill(seenSecondItemSequenceId, -1);
		Arrays.fill(lastSequenceForItem, -1);

		int[][] seenEqualsSequenceId = (useDenseCooccurrence && !onlySingletonItemsets)
				? new int[frequentItemCount][frequentItemCount]
				: null;
		if (seenEqualsSequenceId != null) {
			for (int index = 0; index < frequentItemCount; index++) {
				Arrays.fill(seenEqualsSequenceId[index], -1);
			}
		}
		HashSet<Long> seenEqualsPairs = (!useDenseCooccurrence && !onlySingletonItemsets) ? new HashSet<>() : null;

		int[] sequenceItemsBuffer = new int[64];
		int[] itemsetStartBuffer = new int[32];
		int[] itemsetEndBuffer = new int[32];
		int pairMarkCounter = 0;

		for (int lineIndex = 0; lineIndex < lineCount; lineIndex++) {
			int start = lineStart[lineIndex];
			int count = lineLength[lineIndex];
			int sequenceStartBit = sequencesSize[lineIndex];
			int sequenceLastBit = sequenceEndBit[lineIndex];
			int transactionId = 0;

			int sequenceItemCount = 0;
			int itemsetCount = 0;
			int currentItemsetStart = 0;

			for (int tokenOffset = 0; tokenOffset < count; tokenOffset++) {
				int token = database[start + tokenOffset];
				if (token == -1 || token == -2) {
					if (token == -1) {
						transactionId++;
					}
					if (sequenceItemCount > currentItemsetStart) {
						if (itemsetCount == itemsetStartBuffer.length) {
							itemsetStartBuffer = Arrays.copyOf(itemsetStartBuffer, itemsetStartBuffer.length * 2);
							itemsetEndBuffer = Arrays.copyOf(itemsetEndBuffer, itemsetEndBuffer.length * 2);
						}
						itemsetStartBuffer[itemsetCount] = currentItemsetStart;
						itemsetEndBuffer[itemsetCount] = sequenceItemCount;
						itemsetCount++;
						currentItemsetStart = sequenceItemCount;
					}
				} else if (token >= 0) {
					int internalId = itemRemap[token];
					if (internalId >= 0) {
						int bitPosition = sequenceStartBit + transactionId;
						registerBitInVerticalDatabase(internalId, bitPosition);

						if (lastSequenceForItem[internalId] != lineIndex) {
							lastSequenceForItem[internalId] = lineIndex;
							PatternNode rootNode = rootSequenceExtensions[internalId];
							int cursor = itemOccupiedCursor[internalId]++;
							rootNode.occupiedSequenceIds[cursor] = lineIndex;
							rootNode.occupiedFirstBits[cursor] = bitPosition;
							rootNode.sequenceIdSum += lineIndex;
							rootNode.sequenceExtensionMeasure += (sequenceLastBit - bitPosition + 1);
						}

						if (sequenceItemCount == sequenceItemsBuffer.length) {
							sequenceItemsBuffer = Arrays.copyOf(sequenceItemsBuffer, sequenceItemsBuffer.length * 2);
						}
						sequenceItemsBuffer[sequenceItemCount++] = internalId;
					}
				}
			}

			pairMarkCounter = updateCooccurrenceStructures(sequenceItemsBuffer, itemsetStartBuffer, itemsetEndBuffer,
					itemsetCount, lineIndex, pairMarkCounter, seenFirstItemSequenceId, seenSecondItemSequenceId,
					seenEqualsSequenceId, seenEqualsPairs);
		}

		database = null;
		lineStart = null;
		lineLength = null;
		itemRemap = null;
		sequencesSize = null;
		seenFirstItemSequenceId = null;
		seenSecondItemSequenceId = null;
		lastSequenceForItem = null;
		itemOccupiedCursor = null;
		seenEqualsSequenceId = null;
		seenEqualsPairs = null;
		sequenceItemsBuffer = null;
		itemsetStartBuffer = null;
		itemsetEndBuffer = null;

		int[] frequentItemsArray = new int[frequentItemCount];
		for (int internalId = 0; internalId < frequentItemCount; internalId++) {
			frequentItemsArray[internalId] = internalId;
			PatternNode singleItemNode = rootSequenceExtensions[internalId];
			if (itemHasEqualsPartner[internalId]) {
				singleItemNode.bitmapWords = verticalBitmapWords[internalId];
				singleItemNode.bitmapWordCount = verticalBitmapWordCount[internalId];
			}
			if (singleItemNode.support < numberOfSequences) {
				candidateCount++;
				addSingleItemToGeneratorSet(singleItemNode);
			}
		}

		if (maximumPatternLength <= 1) {
			addEmptySetGenerator(numberOfSequences);
			MemoryLogger.getInstance().checkMemory();
			return;
		}

		for (int index = 0; index < frequentItemCount; index++) {
			PatternNode rootPattern = rootSequenceExtensions[index];
			patternLength = 0;
			ensurePatternCapacity(1);
			patternBuffer[patternLength++] = rootPattern.lastItem;

			hybridSearch(rootPattern, frequentItemsArray, frequentItemCount, frequentItemsArray, frequentItemCount,
					rootPattern.lastItem, rootSequenceExtensions, frequentItemCount, EMPTY_PATTERN_ARRAY, 0,
					numberOfSequences);
			if (!outputSequenceIdentifiers) {
				rootPattern.occupiedSequenceIds = null;
			}
			rootPattern.occupiedFirstBits = null;
		}

		addEmptySetGenerator(numberOfSequences);
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Adds the empty sequence as a level-0 generator pattern with support equal to
	 * the total number of sequences in the database.
	 *
	 * @param numberOfSequences the total number of sequences in the database
	 */
	private void addEmptySetGenerator(int numberOfSequences) {
		GeneratorPattern emptyGenerator = new GeneratorPattern();
		emptyGenerator.buffer = EMPTY_INT_ARRAY;
		emptyGenerator.length = 0;
		emptyGenerator.itemCount = 0;
		emptyGenerator.sumOfEvenItems = 0L;
		emptyGenerator.sumOfOddItems = 0L;
		emptyGenerator.sequenceIds = null;
		emptyGenerator.support = numberOfSequences;
		emptyGenerator.sequenceIdSum = 0;

		List<GeneratorPattern> levelZeroList = new ArrayList<>();
		levelZeroList.add(emptyGenerator);
		generatorPatterns.get(0).put(0, levelZeroList);
		patternCount++;
	}

	/**
	 * Sets the bit at the given global bit position in the vertical bitmap of the
	 * specified internal item identifier.
	 *
	 * @param internalId  the contiguous internal item identifier
	 * @param bitPosition the global bit position to set
	 */
	private void registerBitInVerticalDatabase(int internalId, int bitPosition) {
		int wordIndex = bitPosition >> 6;
		long[] words = verticalBitmapWords[internalId];
		if (words == null) {
			words = new long[Math.max(4, wordIndex + 1)];
			verticalBitmapWords[internalId] = words;
		} else if (wordIndex >= words.length) {
			words = Arrays.copyOf(words, Math.max(wordIndex + 1, words.length * 2));
			verticalBitmapWords[internalId] = words;
		}
		words[wordIndex] |= (1L << (bitPosition & 63));
		if (wordIndex >= verticalBitmapWordCount[internalId]) {
			verticalBitmapWordCount[internalId] = wordIndex + 1;
		}
	}

	/**
	 * Performs an I-step intersection between a parent pattern node and a candidate
	 * item, returning a populated PatternNode if its support reaches minimumSupport
	 * or null otherwise.
	 *
	 * @param parentNode    the parent pattern node being extended
	 * @param candidateItem the internal identifier of the item appended to the last itemset
	 * @return the frequent extended PatternNode, or null if infrequent
	 */
	private PatternNode createItemsetExtensionNode(PatternNode parentNode, int candidateItem) {
		long[] parentWords = parentNode.bitmapWords;
		long[] itemWords = verticalBitmapWords[candidateItem];
		int limit = Math.min(parentNode.bitmapWordCount, verticalBitmapWordCount[candidateItem]);
		if (limit == 0) {
			return null;
		}

		if (scratchBitmapWords.length < limit) {
			scratchBitmapWords = new long[limit];
		}

		int maxSequenceId = bitToSequenceId[Math.min((limit << 6) - 1, bitToSequenceId.length - 1)];
		int support = 0;
		int sequenceIdSum = 0;
		long extensionMeasure = 0L;
		int candidateWordCount = 0;
		int coveredUntilBit = -1;

		for (int wordIndex = 0; wordIndex < limit; wordIndex++) {
			int wordStartBit = wordIndex << 6;
			int remainingSequences = (coveredUntilBit >= wordStartBit)
					? (maxSequenceId - bitToSequenceId[coveredUntilBit])
					: (maxSequenceId - bitToSequenceId[wordStartBit] + 1);
			if (support + remainingSequences < minimumSupport) {
				return null;
			}

			long intersection = parentWords[wordIndex] & itemWords[wordIndex];
			scratchBitmapWords[wordIndex] = intersection;
			if (intersection == 0L) {
				continue;
			}
			candidateWordCount = wordIndex + 1;

			long unhandledBits = intersection;
			if (coveredUntilBit >= wordStartBit) {
				if (coveredUntilBit >= wordStartBit + 63) {
					continue;
				}
				unhandledBits &= (-2L << (coveredUntilBit & 63));
			}

			while (unhandledBits != 0L) {
				int firstBit = wordStartBit + Long.numberOfTrailingZeros(unhandledBits);
				int sequenceId = bitToSequenceId[firstBit];
				int sequenceLastBit = sequenceEndBit[sequenceId];

				candidateSequenceIdBuffer[support] = sequenceId;
				candidateFirstBitBuffer[support] = firstBit;
				support++;
				if (support + (maxSequenceId - sequenceId) < minimumSupport) {
					return null;
				}
				sequenceIdSum += sequenceId;
				extensionMeasure += (sequenceLastBit - firstBit + 1);
				coveredUntilBit = sequenceLastBit;

				if (sequenceLastBit >= wordStartBit + 63) {
					break;
				}
				unhandledBits &= (-2L << (sequenceLastBit & 63));
			}
		}

		if (support < minimumSupport) {
			return null;
		}
		return buildExtendedNode(parentNode, candidateItem, false, support, sequenceIdSum, extensionMeasure,
				candidateWordCount);
	}

	/**
	 * Performs an S-step join between a parent pattern node and a candidate item,
	 * returning a populated PatternNode if its support reaches minimumSupport or
	 * null otherwise.
	 *
	 * @param parentNode    the parent pattern node being extended
	 * @param candidateItem the internal identifier of the item appended as a new itemset
	 * @return the frequent extended PatternNode, or null if infrequent
	 */
	private PatternNode createSequenceExtensionNode(PatternNode parentNode, int candidateItem) {
		int[] parentSequenceIds = parentNode.occupiedSequenceIds;
		int[] parentFirstBits = parentNode.occupiedFirstBits;
		int occupiedCount = parentNode.support;

		long[] itemWords = verticalBitmapWords[candidateItem];
		int itemWordCount = verticalBitmapWordCount[candidateItem];
		boolean needsBitmapForIStep = itemHasEqualsPartner[candidateItem];

		int support = 0;
		int sequenceIdSum = 0;
		long extensionMeasure = 0L;
		int candidateWordCount = 0;

		if (needsBitmapForIStep) {
			if (scratchBitmapWords.length < itemWordCount) {
				scratchBitmapWords = new long[itemWordCount];
			} else {
				Arrays.fill(scratchBitmapWords, 0, itemWordCount, 0L);
			}
		}

		for (int occupiedIndex = 0; occupiedIndex < occupiedCount; occupiedIndex++) {
			int sequenceId = parentSequenceIds[occupiedIndex];
			int sequenceLastBit = sequenceEndBit[sequenceId];
			int fromBit = parentFirstBits[occupiedIndex] + 1;
			int fromWord = fromBit >> 6;
			if (fromBit <= sequenceLastBit && fromWord < itemWordCount) {
				int endWord = sequenceLastBit >> 6;
				int scanLimit = endWord < itemWordCount ? endWord : itemWordCount - 1;
				boolean matchedInSequence = false;

				for (int wordIndex = fromWord; wordIndex <= scanLimit; wordIndex++) {
					long itemWord = itemWords[wordIndex];
					if (itemWord == 0L) {
						continue;
					}
					if (wordIndex == fromWord) {
						int startBitOffset = fromBit & 63;
						if (startBitOffset != 0) {
							itemWord &= (-1L << startBitOffset);
						}
					}
					if (wordIndex == endWord) {
						int endBitOffset = sequenceLastBit & 63;
						if (endBitOffset != 63) {
							itemWord &= (1L << (endBitOffset + 1)) - 1L;
						}
					}
					if (itemWord != 0L) {
						if (!matchedInSequence) {
							matchedInSequence = true;
							int firstBit = (wordIndex << 6) + Long.numberOfTrailingZeros(itemWord);
							candidateSequenceIdBuffer[support] = sequenceId;
							candidateFirstBitBuffer[support] = firstBit;
							support++;
							sequenceIdSum += sequenceId;
							extensionMeasure += (sequenceLastBit - firstBit + 1);
							if (!needsBitmapForIStep) {
								break;
							}
						}
						scratchBitmapWords[wordIndex] |= itemWord;
						if (wordIndex >= candidateWordCount) {
							candidateWordCount = wordIndex + 1;
						}
					}
				}
			}

			int remainingSequences = occupiedCount - occupiedIndex - 1;
			if (support + remainingSequences < minimumSupport) {
				return null;
			}
		}

		if (support < minimumSupport) {
			return null;
		}
		return buildExtendedNode(parentNode, candidateItem, true, support, sequenceIdSum, extensionMeasure,
				candidateWordCount);
	}

	/**
	 * Allocates and populates a frequent extended PatternNode from the scratch
	 * occurrence buffers.
	 *
	 * @param parentNode          the parent pattern node
	 * @param candidateItem       the newly appended internal item identifier
	 * @param isSequenceExtension true for an S-extension, false for an I-extension
	 * @param support             the support of the extended pattern
	 * @param sequenceIdSum       the sum of supporting sequence identifiers
	 * @param extensionMeasure    the sequence extension measure SE(D_p)
	 * @param candidateWordCount  the number of active words in scratchBitmapWords
	 * @return the constructed PatternNode
	 */
	private PatternNode buildExtendedNode(PatternNode parentNode, int candidateItem, boolean isSequenceExtension,
			int support, int sequenceIdSum, long extensionMeasure, int candidateWordCount) {
		PatternNode node = new PatternNode();
		node.itemCount = parentNode.itemCount + 1;
		long originalItem = internalToOriginalItem[candidateItem];
		node.sumOfEvenItems = parentNode.sumOfEvenItems + ((originalItem % 2 == 0) ? originalItem : 0L);
		node.sumOfOddItems = parentNode.sumOfOddItems + ((originalItem % 2 != 0) ? originalItem : 0L);
		if (itemHasEqualsPartner[candidateItem]) {
			node.bitmapWords = Arrays.copyOf(scratchBitmapWords, candidateWordCount);
			node.bitmapWordCount = candidateWordCount;
		}
		if (itemHasAfterPartner[candidateItem] || outputSequenceIdentifiers) {
			node.occupiedSequenceIds = Arrays.copyOf(candidateSequenceIdBuffer, support);
		}
		if (itemHasAfterPartner[candidateItem]) {
			node.occupiedFirstBits = Arrays.copyOf(candidateFirstBitBuffer, support);
		}
		node.support = support;
		node.parentSupport = parentNode.support;
		node.sequenceIdSum = sequenceIdSum;
		node.sequenceExtensionMeasure = extensionMeasure;
		node.isSequenceExtension = isSequenceExtension;
		node.parentIsSequenceExtension = parentNode.isSequenceExtension;
		node.lastItem = candidateItem;
		node.parentLastItem = parentNode.lastItem;
		node.doExtension = true;
		node.doSequenceExtension = true;
		return node;
	}

	/**
	 * Updates the same-itemset and later-itemset co-occurrence structures for a
	 * single sequence using flat itemset boundary buffers.
	 *
	 * @param sequenceItems            the flat buffer of frequent internal items in the sequence
	 * @param itemsetStarts            the start index of each itemset in sequenceItems
	 * @param itemsetEnds              the end index (exclusive) of each itemset in sequenceItems
	 * @param itemsetCount             the number of non-empty itemsets in the sequence
	 * @param sequenceIndex            the 0-based index of the sequence
	 * @param pairMarkCounter          the running stamp counter for deduplicating after-pairs
	 * @param seenFirstItemSequenceId  per-item stamp of the last sequence in which the item was processed as first item
	 * @param seenSecondItemSequenceId per-item stamp of the last pair mark in which the item was processed as second item
	 * @param seenEqualsSequenceId     2D stamp array for deduplicating same-itemset pairs in dense mode
	 * @param seenEqualsPairs          per-sequence set for deduplicating same-itemset pairs in sparse mode
	 * @return the updated pairMarkCounter
	 */
	private int updateCooccurrenceStructures(int[] sequenceItems, int[] itemsetStarts, int[] itemsetEnds,
			int itemsetCount, int sequenceIndex, int pairMarkCounter, int[] seenFirstItemSequenceId,
			int[] seenSecondItemSequenceId, int[][] seenEqualsSequenceId, HashSet<Long> seenEqualsPairs) {
		if (cooccurrenceAfterMatrix != null) {
			for (int firstItemsetIndex = 0; firstItemsetIndex < itemsetCount; firstItemsetIndex++) {
				int firstStart = itemsetStarts[firstItemsetIndex];
				int firstEnd = itemsetEnds[firstItemsetIndex];

				if (cooccurrenceEqualsMatrix != null) {
					for (int firstIndex = firstStart; firstIndex < firstEnd; firstIndex++) {
						int firstItem = sequenceItems[firstIndex];
						int[] seenEqualsRow = seenEqualsSequenceId[firstItem];
						int[] equalsRow = cooccurrenceEqualsMatrix[firstItem];
						for (int secondIndex = firstIndex + 1; secondIndex < firstEnd; secondIndex++) {
							int secondItem = sequenceItems[secondIndex];
							if (seenEqualsRow[secondItem] != sequenceIndex) {
								seenEqualsRow[secondItem] = sequenceIndex;
								if (++equalsRow[secondItem] >= minimumSupport) {
									itemHasEqualsPartner[firstItem] = true;
								}
							}
						}
					}
				}

				for (int firstIndex = firstStart; firstIndex < firstEnd; firstIndex++) {
					int firstItem = sequenceItems[firstIndex];
					if (seenFirstItemSequenceId[firstItem] == sequenceIndex) {
						continue;
					}
					seenFirstItemSequenceId[firstItem] = sequenceIndex;
					pairMarkCounter++;
					int currentMark = pairMarkCounter;
					int[] afterRow = cooccurrenceAfterMatrix[firstItem];

					for (int secondItemsetIndex = firstItemsetIndex + 1; secondItemsetIndex < itemsetCount; secondItemsetIndex++) {
						int secondStart = itemsetStarts[secondItemsetIndex];
						int secondEnd = itemsetEnds[secondItemsetIndex];
						for (int secondIndex = secondStart; secondIndex < secondEnd; secondIndex++) {
							int secondItem = sequenceItems[secondIndex];
							if (seenSecondItemSequenceId[secondItem] != currentMark) {
								seenSecondItemSequenceId[secondItem] = currentMark;
								if (++afterRow[secondItem] >= minimumSupport) {
									itemHasAfterPartner[firstItem] = true;
								}
							}
						}
					}
				}
			}
		} else {
			if (seenEqualsPairs != null) {
				seenEqualsPairs.clear();
			}
			for (int firstItemsetIndex = 0; firstItemsetIndex < itemsetCount; firstItemsetIndex++) {
				int firstStart = itemsetStarts[firstItemsetIndex];
				int firstEnd = itemsetEnds[firstItemsetIndex];

				for (int firstIndex = firstStart; firstIndex < firstEnd; firstIndex++) {
					int firstItem = sequenceItems[firstIndex];
					if (seenEqualsPairs != null) {
						for (int secondIndex = firstIndex + 1; secondIndex < firstEnd; secondIndex++) {
							int secondItem = sequenceItems[secondIndex];
							long pairKey = (((long) firstItem) << 32) | (secondItem & 0xFFFFFFFFL);
							if (seenEqualsPairs.add(pairKey)) {
								int newSupport = cooccurrenceMapEquals
										.computeIfAbsent(firstItem, key -> new HashMap<>())
										.merge(secondItem, 1, Integer::sum);
								if (newSupport >= minimumSupport) {
									itemHasEqualsPartner[firstItem] = true;
								}
							}
						}
					}

					if (seenFirstItemSequenceId[firstItem] == sequenceIndex) {
						continue;
					}
					seenFirstItemSequenceId[firstItem] = sequenceIndex;
					pairMarkCounter++;
					int currentMark = pairMarkCounter;
					Map<Integer, Integer> afterMap = cooccurrenceMapAfter.computeIfAbsent(firstItem,
							key -> new HashMap<>());

					for (int secondItemsetIndex = firstItemsetIndex + 1; secondItemsetIndex < itemsetCount; secondItemsetIndex++) {
						int secondStart = itemsetStarts[secondItemsetIndex];
						int secondEnd = itemsetEnds[secondItemsetIndex];
						for (int secondIndex = secondStart; secondIndex < secondEnd; secondIndex++) {
							int secondItem = sequenceItems[secondIndex];
							if (seenSecondItemSequenceId[secondItem] != currentMark) {
								seenSecondItemSequenceId[secondItem] = currentMark;
								int newSupport = afterMap.merge(secondItem, 1, Integer::sum);
								if (newSupport >= minimumSupport) {
									itemHasAfterPartner[firstItem] = true;
								}
							}
						}
					}
				}
			}
		}

		return pairMarkCounter;
	}

	/**
	 * Finds the index of the first element in a sorted array strictly greater than
	 * the given value.
	 *
	 * @param sortedArray the sorted integer array
	 * @param count       the number of valid elements in sortedArray
	 * @param value       the value to search against
	 * @return the first index whose element is strictly greater than value, or count if none exists
	 */
	private static int findFirstGreaterIndex(int[] sortedArray, int count, int value) {
		int low = 0;
		int high = count;
		while (low < high) {
			int middle = (low + high) >>> 1;
			if (sortedArray[middle] <= value) {
				low = middle + 1;
			} else {
				high = middle;
			}
		}
		return low;
	}

	/**
	 * Searches a sorted array of PatternNode instances by their lastItem field using
	 * binary search.
	 *
	 * @param patternList     the array of PatternNode instances sorted by ascending lastItem
	 * @param patternListSize the number of valid entries in patternList
	 * @param targetItem      the internal item identifier to locate
	 * @return the matching PatternNode, or null if no node has lastItem equal to targetItem
	 */
	private static PatternNode findPatternByLastItem(PatternNode[] patternList, int patternListSize, int targetItem) {
		int low = 0;
		int high = patternListSize - 1;
		while (low <= high) {
			int middle = (low + high) >>> 1;
			int middleItem = patternList[middle].lastItem;
			if (middleItem < targetItem) {
				low = middle + 1;
			} else if (middleItem > targetItem) {
				high = middle - 1;
			} else {
				return patternList[middle];
			}
		}
		return null;
	}

	/**
	 * Performs the HybridSearch procedure of FGenSM (Figure 5), combining
	 * breadth-first sibling pruning via Corollary 3 (LPGEN) with depth-first
	 * recursive exploration.
	 *
	 * @param pattern                the current pattern node p being extended
	 * @param sequenceCandidateItems the candidate items Sn for sequence extensions
	 * @param sequenceCandidateCount the number of valid items in sequenceCandidateItems
	 * @param itemsetCandidateItems  the candidate items In for itemset extensions
	 * @param itemsetCandidateCount  the number of valid items in itemsetCandidateItems
	 * @param minimumItemForIStep    the last item of pattern (itemset extensions require items strictly greater)
	 * @param listSequenceExtensions the sibling sequence extensions ListS_ext from the parent node
	 * @param listSequenceExtCount   the number of valid entries in listSequenceExtensions
	 * @param listItemsetExtensions  the sibling itemset extensions ListI_ext from the parent node
	 * @param listItemsetExtCount    the number of valid entries in listItemsetExtensions
	 * @param numberOfSequences      the total number of sequences in the database
	 */
	private void hybridSearch(PatternNode pattern, int[] sequenceCandidateItems, int sequenceCandidateCount,
			int[] itemsetCandidateItems, int itemsetCandidateCount, int minimumItemForIStep,
			PatternNode[] listSequenceExtensions, int listSequenceExtCount, PatternNode[] listItemsetExtensions,
			int listItemsetExtCount, int numberOfSequences) {
		if (!pattern.doExtension || pattern.itemCount >= maximumPatternLength) {
			return;
		}

		int[] itemsetTempItems = EMPTY_INT_ARRAY;
		PatternNode[] listItemsetExtensionsNew = EMPTY_PATTERN_ARRAY;
		int itemsetTempCount = 0;

		if (itemHasEqualsPartner[pattern.lastItem]) {
			int startItemsetIndex = findFirstGreaterIndex(itemsetCandidateItems, itemsetCandidateCount,
					minimumItemForIStep);
			int maximumItemsetCandidates = itemsetCandidateCount - startItemsetIndex;

			if (maximumItemsetCandidates > 0) {
				itemsetTempItems = new int[maximumItemsetCandidates];
				listItemsetExtensionsNew = new PatternNode[maximumItemsetCandidates];

				int[] equalsRow = (cooccurrenceEqualsMatrix != null)
						? cooccurrenceEqualsMatrix[pattern.lastItem]
						: null;
				Map<Integer, Integer> mapSupportItemsEquals = (cooccurrenceEqualsMatrix == null)
						? cooccurrenceMapEquals.get(pattern.lastItem)
						: null;

				for (int index = startItemsetIndex; index < itemsetCandidateCount; index++) {
					int item = itemsetCandidateItems[index];

					if (equalsRow != null) {
						if (equalsRow[item] < minimumSupport) {
							continue;
						}
					} else {
						if (mapSupportItemsEquals == null) {
							continue;
						}
						Integer cooccurrenceSupport = mapSupportItemsEquals.get(item);
						if (cooccurrenceSupport == null || cooccurrenceSupport < minimumSupport) {
							continue;
						}
					}

					PatternNode itemsetNewNode = createItemsetExtensionNode(pattern, item);
					if (itemsetNewNode != null) {
						itemsetTempItems[itemsetTempCount] = item;
						listItemsetExtensionsNew[itemsetTempCount] = itemsetNewNode;
						itemsetTempCount++;

						if (itemsetNewNode.sequenceExtensionMeasure == pattern.sequenceExtensionMeasure) {
							itemsetNewNode.doSequenceExtension = false;
							itemsetNewNode.occupiedFirstBits = null;
						}

						PatternNode siblingB = pattern.isSequenceExtension
								? findPatternByLastItem(listSequenceExtensions, listSequenceExtCount, item)
								: findPatternByLastItem(listItemsetExtensions, listItemsetExtCount, item);
						if (siblingB != null
								&& itemsetNewNode.sequenceExtensionMeasure == siblingB.sequenceExtensionMeasure) {
							itemsetNewNode.doSequenceExtension = false;
							itemsetNewNode.occupiedFirstBits = null;
						}

						if (pattern.isSequenceExtension && pattern.parentIsSequenceExtension
								&& pattern.parentLastItem == pattern.lastItem) {
							PatternNode siblingR = findPatternByLastItem(listItemsetExtensions, listItemsetExtCount,
									item);
							if (siblingR != null
									&& itemsetNewNode.sequenceExtensionMeasure == siblingR.sequenceExtensionMeasure) {
								itemsetNewNode.doExtension = false;
								itemsetNewNode.bitmapWords = null;
								itemsetNewNode.occupiedFirstBits = null;
								if (!outputSequenceIdentifiers) {
									itemsetNewNode.occupiedSequenceIds = null;
								}
							}
						}
					}
				}
			}
		}

		int[] sequenceTempItems = EMPTY_INT_ARRAY;
		PatternNode[] listSequenceExtensionsNew = EMPTY_PATTERN_ARRAY;
		int sequenceTempCount = 0;

		if (pattern.doSequenceExtension && sequenceCandidateCount > 0 && itemHasAfterPartner[pattern.lastItem]) {
			sequenceTempItems = new int[sequenceCandidateCount];
			listSequenceExtensionsNew = new PatternNode[sequenceCandidateCount];

			int[] afterRow = (cooccurrenceAfterMatrix != null) ? cooccurrenceAfterMatrix[pattern.lastItem] : null;
			Map<Integer, Integer> mapSupportItemsAfter = (cooccurrenceAfterMatrix == null)
					? cooccurrenceMapAfter.get(pattern.lastItem)
					: null;

			for (int index = 0; index < sequenceCandidateCount; index++) {
				int item = sequenceCandidateItems[index];

				if (afterRow != null) {
					if (afterRow[item] < minimumSupport) {
						continue;
					}
				} else {
					if (mapSupportItemsAfter == null) {
						continue;
					}
					Integer cooccurrenceSupport = mapSupportItemsAfter.get(item);
					if (cooccurrenceSupport == null || cooccurrenceSupport < minimumSupport) {
						continue;
					}
				}

				PatternNode sequenceNewNode = createSequenceExtensionNode(pattern, item);
				if (sequenceNewNode != null) {
					sequenceTempItems[sequenceTempCount] = item;
					listSequenceExtensionsNew[sequenceTempCount] = sequenceNewNode;
					sequenceTempCount++;

					PatternNode siblingA = findPatternByLastItem(listSequenceExtensions, listSequenceExtCount, item);
					if (siblingA != null
							&& sequenceNewNode.sequenceExtensionMeasure == siblingA.sequenceExtensionMeasure) {
						sequenceNewNode.doExtension = false;
						sequenceNewNode.bitmapWords = null;
						sequenceNewNode.occupiedFirstBits = null;
						if (!outputSequenceIdentifiers) {
							sequenceNewNode.occupiedSequenceIds = null;
						}
					}
				}
			}
		}

		pattern.bitmapWords = null;
		pattern.occupiedFirstBits = null;
		if (!outputSequenceIdentifiers) {
			pattern.occupiedSequenceIds = null;
		}

		if (pattern.doSequenceExtension) {
			for (int index = 0; index < sequenceTempCount; index++) {
				PatternNode childPattern = listSequenceExtensionsNew[index];
				int savedLength = patternLength;
				ensurePatternCapacity(patternLength + 2);
				patternBuffer[patternLength++] = -1;
				patternBuffer[patternLength++] = childPattern.lastItem;

				updateGeneratorPattern(childPattern, numberOfSequences);

				if (childPattern.doExtension && childPattern.itemCount < maximumPatternLength) {
					hybridSearch(childPattern, sequenceTempItems, sequenceTempCount, sequenceTempItems,
							sequenceTempCount, childPattern.lastItem, listSequenceExtensionsNew, sequenceTempCount,
							listItemsetExtensionsNew, itemsetTempCount, numberOfSequences);
				} else {
					childPattern.bitmapWords = null;
					childPattern.occupiedFirstBits = null;
					if (!outputSequenceIdentifiers) {
						childPattern.occupiedSequenceIds = null;
					}
				}
				patternLength = savedLength;
			}
		}

		int[] nextSequenceItemsForIStep = pattern.doSequenceExtension ? sequenceTempItems : sequenceCandidateItems;
		int nextSequenceCountForIStep = pattern.doSequenceExtension ? sequenceTempCount : sequenceCandidateCount;

		for (int index = 0; index < itemsetTempCount; index++) {
			PatternNode childPattern = listItemsetExtensionsNew[index];
			int savedLength = patternLength;
			ensurePatternCapacity(patternLength + 1);
			patternBuffer[patternLength++] = childPattern.lastItem;

			updateGeneratorPattern(childPattern, numberOfSequences);

			boolean hasRemainingExtensions = childPattern.doSequenceExtension || (index + 1 < itemsetTempCount);
			if (childPattern.doExtension && hasRemainingExtensions && childPattern.itemCount < maximumPatternLength) {
				hybridSearch(childPattern, nextSequenceItemsForIStep, nextSequenceCountForIStep, itemsetTempItems,
						itemsetTempCount, childPattern.lastItem, listSequenceExtensionsNew, sequenceTempCount,
						listItemsetExtensionsNew, itemsetTempCount, numberOfSequences);
			} else {
				childPattern.bitmapWords = null;
				childPattern.occupiedFirstBits = null;
				if (!outputSequenceIdentifiers) {
					childPattern.occupiedSequenceIds = null;
				}
			}
			patternLength = savedLength;
		}

		if (++memoryCheckCounter >= MEMORY_CHECK_INTERVAL) {
			memoryCheckCounter = 0;
			MemoryLogger.getInstance().checkMemory();
		}
	}

	/**
	 * Updates the set of discovered generator sequential patterns FGS with a new
	 * candidate pattern according to Figure 8 and Corollary 3 of the paper.
	 *
	 * @param candidate         the candidate pattern node to test and insert
	 * @param numberOfSequences the total number of sequences in the database
	 */
	private void updateGeneratorPattern(PatternNode candidate, int numberOfSequences) {
		if (!candidate.doExtension || !candidate.doSequenceExtension || candidate.support == numberOfSequences
				|| candidate.support == candidate.parentSupport) {
			return;
		}

		candidateCount++;

		int candidateLength = candidate.itemCount;
		int candidateSupport = candidate.support;
		int candidateSequenceIdSum = candidate.sequenceIdSum;
		long candidateEvenSum = candidate.sumOfEvenItems;
		long candidateOddSum = candidate.sumOfOddItems;

		for (int levelIndex = 1; levelIndex < candidateLength && levelIndex < generatorPatterns.size(); levelIndex++) {
			Map<Integer, List<GeneratorPattern>> levelMap = generatorPatterns.get(levelIndex);
			List<GeneratorPattern> bucket = levelMap.get(candidateSequenceIdSum);
			if (bucket == null || bucket.isEmpty()) {
				continue;
			}
			for (int index = 0, bucketSize = bucket.size(); index < bucketSize; index++) {
				GeneratorPattern existingPattern = bucket.get(index);
				if (existingPattern.support == candidateSupport
						&& existingPattern.sumOfEvenItems <= candidateEvenSum
						&& existingPattern.sumOfOddItems <= candidateOddSum
						&& strictlyContainsPattern(patternBuffer, patternLength, existingPattern.buffer,
								existingPattern.length)) {
					return;
				}
			}
		}

		for (int levelIndex = generatorPatterns.size() - 1; levelIndex > candidateLength; levelIndex--) {
			Map<Integer, List<GeneratorPattern>> levelMap = generatorPatterns.get(levelIndex);
			List<GeneratorPattern> bucket = levelMap.get(candidateSequenceIdSum);
			if (bucket == null || bucket.isEmpty()) {
				continue;
			}
			int index = 0;
			while (index < bucket.size()) {
				GeneratorPattern existingPattern = bucket.get(index);
				if (existingPattern.support == candidateSupport
						&& existingPattern.sumOfEvenItems >= candidateEvenSum
						&& existingPattern.sumOfOddItems >= candidateOddSum
						&& strictlyContainsPattern(existingPattern.buffer, existingPattern.length, patternBuffer,
								patternLength)) {
					int lastIndex = bucket.size() - 1;
					if (index != lastIndex) {
						bucket.set(index, bucket.get(lastIndex));
					}
					bucket.remove(lastIndex);
					patternCount--;
				} else {
					index++;
				}
			}
		}

		addPatternToGeneratorSet(candidate);
	}

	/**
	 * Inserts a frequent 1-item pattern node into the generator pattern collection FGS.
	 *
	 * @param singleItemNode the 1-item pattern node to store in generatorPatterns
	 */
	private void addSingleItemToGeneratorSet(PatternNode singleItemNode) {
		List<GeneratorPattern> bucket = generatorPatterns.get(1).computeIfAbsent(singleItemNode.sequenceIdSum,
				key -> new ArrayList<>());

		GeneratorPattern generatorPattern = new GeneratorPattern();
		generatorPattern.buffer = new int[] { singleItemNode.lastItem };
		generatorPattern.length = 1;
		generatorPattern.itemCount = 1;
		generatorPattern.sumOfEvenItems = singleItemNode.sumOfEvenItems;
		generatorPattern.sumOfOddItems = singleItemNode.sumOfOddItems;
		generatorPattern.sequenceIds = outputSequenceIdentifiers ? singleItemNode.occupiedSequenceIds : null;
		generatorPattern.support = singleItemNode.support;
		generatorPattern.sequenceIdSum = singleItemNode.sequenceIdSum;
		bucket.add(generatorPattern);
		patternCount++;
	}

	/**
	 * Inserts a candidate pattern node into the generator pattern collection FGS.
	 *
	 * @param candidate the pattern node to store in generatorPatterns
	 */
	private void addPatternToGeneratorSet(PatternNode candidate) {
		int length = candidate.itemCount;
		while (generatorPatterns.size() <= length) {
			generatorPatterns.add(new HashMap<>());
		}

		Map<Integer, List<GeneratorPattern>> levelMap = generatorPatterns.get(length);
		List<GeneratorPattern> bucket = levelMap.get(candidate.sequenceIdSum);
		if (bucket == null) {
			bucket = new ArrayList<>();
			levelMap.put(candidate.sequenceIdSum, bucket);
		}

		GeneratorPattern generatorPattern = new GeneratorPattern();
		generatorPattern.buffer = Arrays.copyOf(patternBuffer, patternLength);
		generatorPattern.length = patternLength;
		generatorPattern.itemCount = candidate.itemCount;
		generatorPattern.sumOfEvenItems = candidate.sumOfEvenItems;
		generatorPattern.sumOfOddItems = candidate.sumOfOddItems;
		generatorPattern.sequenceIds = outputSequenceIdentifiers ? candidate.occupiedSequenceIds : null;
		generatorPattern.support = candidate.support;
		generatorPattern.sequenceIdSum = candidate.sequenceIdSum;
		bucket.add(generatorPattern);
		patternCount++;
	}

	/**
	 * Checks whether sorted itemset slice containerBuffer[containerStart..containerEnd)
	 * contains all items of sorted itemset slice subBuffer[subStart..subEnd).
	 *
	 * @param containerBuffer the flat buffer holding the potentially containing itemset
	 * @param containerStart  the inclusive start index of the container itemset
	 * @param containerEnd    the exclusive end index of the container itemset
	 * @param subBuffer       the flat buffer holding the potentially contained itemset
	 * @param subStart        the inclusive start index of the sub-itemset
	 * @param subEnd          the exclusive end index of the sub-itemset
	 * @return true if every item in the sub-itemset slice appears in the container slice
	 */
	private static boolean flatItemsetContainsAll(int[] containerBuffer, int containerStart, int containerEnd,
			int[] subBuffer, int subStart, int subEnd) {
		int containerIndex = containerStart;
		for (int subIndex = subStart; subIndex < subEnd; subIndex++) {
			int remainingSubItems = subEnd - subIndex;
			if (containerEnd - containerIndex < remainingSubItems) {
				return false;
			}
			int targetItem = subBuffer[subIndex];
			while (containerIndex < containerEnd && containerBuffer[containerIndex] < targetItem) {
				containerIndex++;
			}
			if (containerIndex == containerEnd || containerBuffer[containerIndex] != targetItem) {
				return false;
			}
			containerIndex++;
		}
		return true;
	}

	/**
	 * Checks whether superBuffer[0..superLength) contains subBuffer[0..subLength)
	 * as a subsequence directly on the flat -1-separated representations.
	 *
	 * @param superBuffer the flat token buffer of the longer pattern
	 * @param superLength the number of valid tokens in superBuffer
	 * @param subBuffer   the flat token buffer of the shorter pattern
	 * @param subLength   the number of valid tokens in subBuffer
	 * @return true if subBuffer is a subsequence of superBuffer
	 */
	private static boolean strictlyContainsPattern(int[] superBuffer, int superLength, int[] subBuffer, int subLength) {
		if (subLength == 0) {
			return true;
		}
		if (superLength < subLength) {
			return false;
		}
		int subStart = 0;
		int superStart = 0;
		while (subStart < subLength) {
			if (superLength - superStart < subLength - subStart) {
				return false;
			}
			int subEnd = subStart;
			while (subEnd < subLength && subBuffer[subEnd] != -1) {
				subEnd++;
			}
			int subItemsetSize = subEnd - subStart;

			boolean matchedCurrentSubItemset = false;
			while (superStart < superLength) {
				if (superLength - superStart < subLength - subStart) {
					return false;
				}
				int superEnd = superStart;
				while (superEnd < superLength && superBuffer[superEnd] != -1) {
					superEnd++;
				}
				int superItemsetSize = superEnd - superStart;

				if (superItemsetSize >= subItemsetSize
						&& flatItemsetContainsAll(superBuffer, superStart, superEnd, subBuffer, subStart, subEnd)) {
					matchedCurrentSubItemset = true;
					superStart = superEnd + 1;
					break;
				}
				superStart = superEnd + 1;
			}
			if (!matchedCurrentSubItemset) {
				return false;
			}
			subStart = subEnd + 1;
		}
		return true;
	}

	/**
	 * Flushes the in-memory output buffer to the writer if it has reached the flush
	 * threshold.
	 *
	 * @throws IOException if an error occurs while writing to the output file
	 */
	private void maybeFlushOutputBuffer() throws IOException {
		if (outputBuffer.length() >= OUTPUT_BUFFER_FLUSH_THRESHOLD) {
			writer.write(outputBuffer.toString());
			outputBuffer.setLength(0);
		}
	}

	/**
	 * Writes all discovered generator sequential patterns meeting the minimum
	 * pattern length to the output file.
	 *
	 * @throws IOException if an error occurs while writing to the output file
	 */
	private void writeResultToFile() throws IOException {
		patternCount = 0;
		for (Map<Integer, List<GeneratorPattern>> level : generatorPatterns) {
			for (List<GeneratorPattern> patternList : level.values()) {
				for (GeneratorPattern pattern : patternList) {
					if (pattern.itemCount < minimumPatternLength) {
						continue;
					}

					patternStringBuilder.setLength(0);
					if (pattern.length == 0) {
						patternStringBuilder.append("#SUP: ").append(pattern.support);
					} else {
						for (int index = 0; index < pattern.length; index++) {
							int token = pattern.buffer[index];
							if (token == -1) {
								patternStringBuilder.append("-1 ");
							} else {
								patternStringBuilder.append(token).append(' ');
							}
						}
						patternStringBuilder.append("-1 #SUP: ").append(pattern.support);
					}
					if (outputSequenceIdentifiers && pattern.sequenceIds != null) {
						patternStringBuilder.append(" #SID:");
						for (int index = 0; index < pattern.sequenceIds.length; index++) {
							patternStringBuilder.append(' ').append(pattern.sequenceIds[index]);
						}
					}
					outputBuffer.append(patternStringBuilder).append('\n');
					patternCount++;
					maybeFlushOutputBuffer();
				}
			}
		}
	}

	/**
	 * Prints statistics about the latest algorithm execution to standard output.
	 */
	public void printStats() {
		System.out.println("=============  FGENSM v.2.69 - STATISTICS =============");
		System.out.println(" Total time ~ " + (endTime - startTime) + " ms");
		System.out.println(" Frequent generator sequences count : " + patternCount);
		System.out.println(" Generator candidates count : " + candidateCount);
		System.out.println(" Max memory (mb) : " + MemoryLogger.getInstance().getMaxMemory());
		System.out.println(" minsup : " + minimumSupport);
		System.out.println("================================================");
	}
	
	/**
	 * Returns the number of frequent generator sequential patterns discovered.
	 *
	 * @return the discovered pattern count
	 */
	public int getPatternCount() {
		return patternCount;
	}

	/**
	 * Returns the number of generator candidate patterns evaluated during mining.
	 *
	 * @return the candidate pattern count
	 */
	public int getCandidateCount() {
		return candidateCount;
	}

	/**
	 * Returns the maximum allowed pattern length in number of items.
	 *
	 * @return the maximum pattern length
	 */
	public int getMaximumPatternLength() {
		return maximumPatternLength;
	}

	/**
	 * Sets the maximum allowed pattern length in number of items.
	 *
	 * @param maximumPatternLength the maximum pattern length to set
	 */
	public void setMaximumPatternLength(int maximumPatternLength) {
		this.maximumPatternLength = maximumPatternLength;
	}

	/**
	 * Sets the minimum required pattern length in number of items.
	 *
	 * @param minimumPatternLength the minimum pattern length to set
	 */
	public void setMinimumPatternLength(int minimumPatternLength) {
		this.minimumPatternLength = minimumPatternLength;
	}

	/**
	 * Sets whether supporting sequence identifiers should be written to the output.
	 *
	 * @param showSequenceIdentifiers true to include sequence identifiers, false otherwise
	 */
	public void showSequenceIdentifiersInOutput(boolean showSequenceIdentifiers) {
		this.outputSequenceIdentifiers = showSequenceIdentifiers;
	}
}
