package ca.pfv.spmf.algorithms.sequentialpatterns.treeminer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

import ca.pfv.spmf.tools.MemoryLogger;

/* This file is part of the SPMF DATA MINING SOFTWARE
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
 * Copyright (c) Philipe Fournier-Viger et al.
 *
 * Do not remove copyright and license information.
 */
/**
 * Efficient implementation of the Tree-Miner algorithm.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoTreeMiner {

	/** Initial tree capacity (will grow dynamically) */
	private static final int INITIAL_TREE_CAPACITY = 1024;

	/** Growth factor for dynamic arrays */
	private static final double GROWTH_FACTOR = 1.5;

	/** Null node indicator */
	private static final int NULL_NODE = -1;

	/** Root node index */
	private static final int SP_ROOT = 0;

	// =========================================================================
	// SP-Tree Node Arrays - DYNAMICALLY SIZED
	// =========================================================================

	private int[] nodeItem;
	private int[] nodeEvent;
	private int[] nodeCount;
	private long[] nodeParentInfo;
	private int[] nodeParent;
	private int[] nodeFirstChild;
	private int[] nodeRightSibling;
	private int[] nodePreOrder;
	private int[] nodeSubtreeEndPre;
	private int[][] headerArrays;
	private int treeSize;
	private int treeCapacity;

	// Temporary construction arrays
	private int[] headTable;
	private int[] tailTable;
	private int[] nodeNextLinkTemp;

	// =========================================================================
	// Item mapping - PRIMITIVE ARRAYS
	// =========================================================================

	/**
	 * Direct mapping array (sparse): originalToDense[originalItemId] = denseId or
	 * -1
	 */
	private int[] originalToDense;

	/** Reverse mapping: denseToOriginal[denseId] = originalItemId */
	private int[] denseToOriginal;

	/** Support count per dense item */
	private int[] itemSupport;

	/** Number of frequent items */
	private int frequentItemCount;

	// =========================================================================
	// Co-Existing Item Tables - SPARSE REPRESENTATION
	// =========================================================================

	/** Sparse SE table: for each alpha, list of (beta, count) pairs */
	private int[][] seqExtItems;
	private int[][] seqExtCounts;

	/**
	 * Sparse IE table: for each alpha, list of (beta, count) pairs where beta >
	 * alpha
	 */
	private int[][] itemExtItems;
	private int[][] itemExtCounts;

	// =========================================================================
	// Compact Database Storage - FLAT PRIMITIVE ARRAYS
	// =========================================================================

	/** Flat storage: [seq1_item1, item2, -1, item3, -1, -2, seq2_item1, ...] */
	private int[] flatDatabase;
	private int flatDbSize;

	/**
	 * Sequence boundaries: seqStarts[i] = index in flatDatabase where sequence i
	 * starts
	 */
	private int[] seqStarts;
	private int sequenceCount;

	// =========================================================================
	// Mining parameters
	// =========================================================================

	private int minSupportAbsolute;
	public boolean enablePruning = true;

	// =========================================================================
	// Pattern length constraints
	// =========================================================================

	/**
	 * Minimum number of items that a pattern can contain (default: 0, no minimum
	 * constraint).
	 */
	private int minimumPatternLength = 0;

	/**
	 * Maximum number of items that a pattern can contain (default:
	 * Integer.MAX_VALUE, no maximum constraint).
	 */
	private int maximumPatternLength = Integer.MAX_VALUE;

	// =========================================================================
	// Output and statistics
	// =========================================================================

	private int patternCount = 0;
	private BufferedWriter writer;
	private StringBuilder patternBuilder = new StringBuilder(256);
	private long startTimestamp;
	private long endTimestamp;
	private String inputPath; // Store for multiple passes

	// =========================================================================
	// Setters for optional pattern length constraints
	// =========================================================================

	/**
	 * Set the minimum pattern length (number of items).
	 * Patterns with fewer items than this value will not be output.
	 *
	 * @param minimumPatternLength the minimum number of items (default: 0)
	 */
	public void setMinimumPatternLength(int minimumPatternLength) {
		this.minimumPatternLength = minimumPatternLength;
	}

	/**
	 * Set the maximum pattern length (number of items).
	 * Patterns with more items than this value will not be output,
	 * and the search will be pruned accordingly.
	 *
	 * @param maximumPatternLength the maximum number of items (default: Integer.MAX_VALUE)
	 */
	public void setMaximumPatternLength(int maximumPatternLength) {
		this.maximumPatternLength = maximumPatternLength;
	}

	// =========================================================================
	// Helper method: compute total item count of a pattern
	// =========================================================================

	/**
	 * Compute the total number of items in a pattern represented by patternLens.
	 *
	 * @param patternLens array of itemset lengths
	 * @param depth       number of itemsets in the pattern
	 * @return total number of items
	 */
	private int computePatternLength(int[] patternLens, int depth) {
		int total = 0;
		for (int i = 0; i < depth; i++) {
			total += patternLens[i];
		}
		return total;
	}

	// =========================================================================
	// Public entry points
	// =========================================================================

	public void runAlgorithm(String inputPath, String outputPath, double minSupport) throws IOException {
		runInternal(inputPath, outputPath, minSupport);
	}

	private void runInternal(String inputPath, String outputPath, double minSupport) throws IOException {
		startTimestamp = System.currentTimeMillis();
		MemoryLogger.getInstance().reset();
		patternCount = 0;
		this.inputPath = inputPath;

		writer = (outputPath != null && !outputPath.equals("null")) ? new BufferedWriter(new FileWriter(outputPath))
				: null;
		writer.write("@FILETYPE=\"Frequent sequential patterns\"");
		writer.newLine();
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.newLine();

		// ===== PASS 1: Count item support and sequences =====
		FirstPassResult firstPass = firstPassScan();
		sequenceCount = firstPass.sequenceCount;
		int maxOriginalItem = firstPass.maxOriginalItem;

		minSupportAbsolute = Math.max(1, (int) Math.ceil((minSupport * sequenceCount) - 0.000001));

		buildItemMappings(firstPass.itemSupport, maxOriginalItem);

		if (frequentItemCount == 0) {
			cleanup();
			return;
		}

		// ===== PASS 2: Load compact database and build SP-Tree =====
		loadCompactDatabase();
		allocateSpTree();
		buildSpTreeFromCompact();
		computeDfsOrderIndices();
		materializeHeaderArrays();

		// ===== PASS 3: Build co-extension tables =====
		buildCoExtensionTablesFromCompact();

		// ===== MINE PATTERNS =====
		minePatterns();

		cleanup();
	}

	private void cleanup() throws IOException {
		if (writer != null) {
			writer.close();
		}
		MemoryLogger.getInstance().checkMemory();
		endTimestamp = System.currentTimeMillis();
	}

	// =========================================================================
	// PASS 1: Scan for item support
	// =========================================================================

	private static class FirstPassResult {
		int[] itemSupport;
		int sequenceCount;
		int maxOriginalItem;
	}

	private FirstPassResult firstPassScan() throws IOException {
	    FirstPassResult result = new FirstPassResult();
	    int[] tempSupport = new int[256];
	    int[] itemSeenStamp = new int[256];
	    int maxItem = 0;
	    int seqCount = 0;
	    int stamp = 0;

	    try (BufferedReader reader = new BufferedReader(new FileReader(inputPath))) {
	        String line;
	        while ((line = reader.readLine()) != null) {
	            line = line.trim();

	            // Every line is a sequence in the database, even if it is empty.
	            // It must be counted toward the total sequence count used for
	            // computing the absolute minimum support threshold.
	            stamp++;
	            seqCount++;

	            if (line.isEmpty())
	                continue;

	            for (String token : line.split("\\s+")) {
	                int value;
	                try {
	                    value = Integer.parseInt(token);
	                } catch (NumberFormatException e) {
	                    continue;
	                }

	                if (value == -2)
	                    break;
	                if (value == -1)
	                    continue;
	                if (value < 0)
	                    continue;

	                // Expand arrays if needed
	                if (value >= tempSupport.length) {
	                    int newSize = Math.max(value + 1, tempSupport.length * 2);
	                    tempSupport = Arrays.copyOf(tempSupport, newSize);
	                    itemSeenStamp = Arrays.copyOf(itemSeenStamp, newSize);
	                }

	                maxItem = Math.max(maxItem, value);

	                // Count support (once per sequence)
	                if (itemSeenStamp[value] != stamp) {
	                    itemSeenStamp[value] = stamp;
	                    tempSupport[value]++;
	                }
	            }
	        }
	    }

	    result.itemSupport = Arrays.copyOf(tempSupport, maxItem + 1);
	    result.sequenceCount = seqCount;
	    result.maxOriginalItem = maxItem;
	    return result;
	}

	// =========================================================================
	// Item mapping - PRIMITIVE ARRAYS instead of HashMap
	// =========================================================================

	private void buildItemMappings(int[] itemSupport, int maxOriginalItem) {
		// Count frequent items
		frequentItemCount = 0;
		for (int i = 0; i <= maxOriginalItem; i++) {
			if (itemSupport[i] >= minSupportAbsolute) {
				frequentItemCount++;
			}
		}

		// Build mappings
		originalToDense = new int[maxOriginalItem + 1];
		Arrays.fill(originalToDense, -1);
		denseToOriginal = new int[frequentItemCount];
		this.itemSupport = new int[frequentItemCount];

		int denseId = 0;
		for (int orig = 0; orig <= maxOriginalItem; orig++) {
			if (itemSupport[orig] >= minSupportAbsolute) {
				originalToDense[orig] = denseId;
				denseToOriginal[denseId] = orig;
				this.itemSupport[denseId] = itemSupport[orig];
				denseId++;
			}
		}
	}

	// =========================================================================
	// PASS 2: Load compact database (flat array)
	// =========================================================================

	private void loadCompactDatabase() throws IOException {
		// Estimate size: assume avg 100 items per sequence
		int estimatedSize = sequenceCount * 100;
		flatDatabase = new int[estimatedSize];
		seqStarts = new int[sequenceCount + 1];
		flatDbSize = 0;
		int seqIdx = 0;

		int[] itemBuffer = new int[64];

		try (BufferedReader reader = new BufferedReader(new FileReader(inputPath))) {
			String line;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty())
					continue;

				seqStarts[seqIdx] = flatDbSize;
				int itemsetStartPos = flatDbSize;
				int bufLen = 0;

				for (String token : line.split("\\s+")) {
					int value;
					try {
						value = Integer.parseInt(token);
					} catch (NumberFormatException e) {
						continue;
					}

					if (value == -2)
						break;

					if (value == -1) {
						// End of itemset - sort and write
						if (bufLen > 0) {
							Arrays.sort(itemBuffer, 0, bufLen);
							ensureFlatDbCapacity(flatDbSize + bufLen + 1);
							for (int k = 0; k < bufLen; k++) {
								flatDatabase[flatDbSize++] = itemBuffer[k];
							}
							flatDatabase[flatDbSize++] = -1;
							bufLen = 0;
						}
					} else if (value >= 0 && value < originalToDense.length) {
						int dense = originalToDense[value];
						if (dense >= 0) {
							if (bufLen == itemBuffer.length) {
								itemBuffer = Arrays.copyOf(itemBuffer, bufLen * 2);
							}
							itemBuffer[bufLen++] = dense;
						}
					}
				}

				// Final itemset
				if (bufLen > 0) {
					Arrays.sort(itemBuffer, 0, bufLen);
					ensureFlatDbCapacity(flatDbSize + bufLen + 1);
					for (int k = 0; k < bufLen; k++) {
						flatDatabase[flatDbSize++] = itemBuffer[k];
					}
					flatDatabase[flatDbSize++] = -1;
				}

				// Mark end of sequence
				if (flatDbSize > itemsetStartPos) {
					ensureFlatDbCapacity(flatDbSize + 1);
					flatDatabase[flatDbSize++] = -2;
					seqIdx++;
				}
			}
		}

		seqStarts[seqIdx] = flatDbSize;
		sequenceCount = seqIdx;
	}

	private void ensureFlatDbCapacity(int required) {
		if (required > flatDatabase.length) {
			int newSize = Math.max(required, (int) (flatDatabase.length * GROWTH_FACTOR));
			flatDatabase = Arrays.copyOf(flatDatabase, newSize);
		}
	}

	// =========================================================================
	// SP-Tree construction - DYNAMIC GROWTH
	// =========================================================================

	private void allocateSpTree() {
		treeCapacity = INITIAL_TREE_CAPACITY;
		nodeItem = new int[treeCapacity];
		nodeEvent = new int[treeCapacity];
		nodeCount = new int[treeCapacity];
		nodeParentInfo = new long[treeCapacity];
		nodeParent = new int[treeCapacity];
		nodeFirstChild = new int[treeCapacity];
		nodeRightSibling = new int[treeCapacity];
		nodeNextLinkTemp = new int[treeCapacity];
		nodePreOrder = new int[treeCapacity];
		nodeSubtreeEndPre = new int[treeCapacity];

		headTable = new int[frequentItemCount];
		tailTable = new int[frequentItemCount];
		Arrays.fill(headTable, NULL_NODE);
		Arrays.fill(tailTable, NULL_NODE);

		// Root node
		nodeItem[SP_ROOT] = -1;
		nodeFirstChild[SP_ROOT] = NULL_NODE;
		nodeRightSibling[SP_ROOT] = NULL_NODE;
		nodeParent[SP_ROOT] = NULL_NODE;
		nodeNextLinkTemp[SP_ROOT] = NULL_NODE;
		nodeCount[SP_ROOT] = 0;
		nodeParentInfo[SP_ROOT] = 0L;
		treeSize = 1;
	}

	private int allocateNode() {
		if (treeSize >= treeCapacity) {
			growTreeArrays();
		}
		int idx = treeSize++;
		nodeFirstChild[idx] = NULL_NODE;
		nodeRightSibling[idx] = NULL_NODE;
		nodeParent[idx] = NULL_NODE;
		nodeNextLinkTemp[idx] = NULL_NODE;
		nodeCount[idx] = 0;
		nodeParentInfo[idx] = 0L;
		return idx;
	}

	private void growTreeArrays() {
		int newCapacity = (int) (treeCapacity * GROWTH_FACTOR);
		nodeItem = Arrays.copyOf(nodeItem, newCapacity);
		nodeEvent = Arrays.copyOf(nodeEvent, newCapacity);
		nodeCount = Arrays.copyOf(nodeCount, newCapacity);
		nodeParentInfo = Arrays.copyOf(nodeParentInfo, newCapacity);
		nodeParent = Arrays.copyOf(nodeParent, newCapacity);
		nodeFirstChild = Arrays.copyOf(nodeFirstChild, newCapacity);
		nodeRightSibling = Arrays.copyOf(nodeRightSibling, newCapacity);
		nodeNextLinkTemp = Arrays.copyOf(nodeNextLinkTemp, newCapacity);
		nodePreOrder = Arrays.copyOf(nodePreOrder, newCapacity);
		nodeSubtreeEndPre = Arrays.copyOf(nodeSubtreeEndPre, newCapacity);
		treeCapacity = newCapacity;
	}

	private void buildSpTreeFromCompact() {
		for (int s = 0; s < sequenceCount; s++) {
			int cur = SP_ROOT;
			int eventNumber = 0;
			long parentBits = 0L;

			int pos = seqStarts[s];
			int end = seqStarts[s + 1];

			while (pos < end) {
				int item = flatDatabase[pos++];

				if (item == -2)
					break;

				if (item == -1) {
					// End of itemset
					eventNumber++;
					parentBits = 0L;
				} else {
					// Regular item
					cur = insertChild(cur, item, eventNumber, parentBits);
					if (item < 64) {
						parentBits |= (1L << item);
					}
				}
			}
		}
	}

	private int insertChild(int parentNode, int denseId, int eventNum, long parentBits) {
		int child = nodeFirstChild[parentNode];
		int prev = NULL_NODE;

		while (child != NULL_NODE) {
			if (nodeItem[child] == denseId && nodeEvent[child] == eventNum) {
				nodeCount[child]++;
				return child;
			}
			prev = child;
			child = nodeRightSibling[child];
		}

		int nd = allocateNode();
		nodeItem[nd] = denseId;
		nodeEvent[nd] = eventNum;
		nodeCount[nd] = 1;
		nodeParentInfo[nd] = parentBits;
		nodeParent[nd] = parentNode;

		if (prev == NULL_NODE) {
			nodeFirstChild[parentNode] = nd;
		} else {
			nodeRightSibling[prev] = nd;
		}

		if (headTable[denseId] == NULL_NODE) {
			headTable[denseId] = nd;
		} else {
			nodeNextLinkTemp[tailTable[denseId]] = nd;
		}
		tailTable[denseId] = nd;

		return nd;
	}

	// =========================================================================
	// DFS pre-order computation
	// =========================================================================

	private void computeDfsOrderIndices() {
		int[] stack = new int[Math.min(treeSize + 4, 10000)];
		int[] childPtr = new int[stack.length];
		int top = 0;
		int pre = 0;

		stack[0] = SP_ROOT;
		childPtr[0] = nodeFirstChild[SP_ROOT];
		nodePreOrder[SP_ROOT] = pre++;

		while (top >= 0) {
			int ch = childPtr[top];
			if (ch == NULL_NODE) {
				nodeSubtreeEndPre[stack[top]] = pre - 1;
				top--;
			} else {
				childPtr[top] = nodeRightSibling[ch];
				top++;
				if (top >= stack.length) {
					stack = Arrays.copyOf(stack, stack.length * 2);
					childPtr = Arrays.copyOf(childPtr, childPtr.length * 2);
				}
				stack[top] = ch;
				childPtr[top] = nodeFirstChild[ch];
				nodePreOrder[ch] = pre++;
			}
		}
	}

	// =========================================================================
	// Header-array materialisation
	// =========================================================================

	private void materializeHeaderArrays() {
		headerArrays = new int[frequentItemCount][];

		for (int item = 0; item < frequentItemCount; item++) {
			int count = 0;
			for (int nd = headTable[item]; nd != NULL_NODE; nd = nodeNextLinkTemp[nd]) {
				count++;
			}
			int[] arr = new int[count];
			int idx = 0;
			for (int nd = headTable[item]; nd != NULL_NODE; nd = nodeNextLinkTemp[nd]) {
				arr[idx++] = nd;
			}
			insertionSortByPreorder(arr);
			headerArrays[item] = arr;
		}

		nodeNextLinkTemp = null;
		headTable = null;
		tailTable = null;
	}

	private void insertionSortByPreorder(int[] arr) {
		for (int i = 1; i < arr.length; i++) {
			int key = arr[i];
			int kp = nodePreOrder[key];
			int j = i - 1;
			while (j >= 0 && nodePreOrder[arr[j]] > kp) {
				arr[j + 1] = arr[j];
				j--;
			}
			arr[j + 1] = key;
		}
	}

	// =========================================================================
	// Co-extension tables - SPARSE REPRESENTATION
	// =========================================================================

	private void buildCoExtensionTablesFromCompact() {
		int n = frequentItemCount;

		// Temporary builders using IntList
		IntList[] seBuilders = new IntList[n];
		IntList[] seCountBuilders = new IntList[n];
		IntList[] ieBuilders = new IntList[n];
		IntList[] ieCountBuilders = new IntList[n];

		for (int i = 0; i < n; i++) {
			seBuilders[i] = new IntList(16);
			seCountBuilders[i] = new IntList(16);
			ieBuilders[i] = new IntList(16);
			ieCountBuilders[i] = new IntList(16);
		}

		// Temporary count matrices (reused per sequence)
		int[] seqExtCount = new int[n * n];
		int[] itemExtCount = new int[n * n];

		// Process each sequence
		for (int s = 0; s < sequenceCount; s++) {
			Arrays.fill(seqExtCount, 0);
			Arrays.fill(itemExtCount, 0);

			int[] firstEvt = new int[n];
			Arrays.fill(firstEvt, -1);

			int pos = seqStarts[s];
			int end = seqStarts[s + 1];
			int evtNum = 0;

			// Collect items in current itemset
			IntList currentItemset = new IntList(16);

			while (pos < end) {
				int item = flatDatabase[pos++];

				if (item == -2)
					break;

				if (item == -1) {
					// Process itemset
					if (currentItemset.size > 0) {
						evtNum++;

						// Record first event for SE
						for (int k = 0; k < currentItemset.size; k++) {
							int d = currentItemset.data[k];
							if (firstEvt[d] < 0) {
								firstEvt[d] = evtNum;
							}
						}

						// IE: pairs within this itemset
						for (int i = 0; i < currentItemset.size; i++) {
							for (int j = i + 1; j < currentItemset.size; j++) {
								int alpha = currentItemset.data[i];
								int beta = currentItemset.data[j];
								if (alpha > beta) {
									int tmp = alpha;
									alpha = beta;
									beta = tmp;
								}
								itemExtCount[alpha * n + beta] = 1;
							}
						}

						// SE: alpha's first event < current event => all items in current itemset can
						// be SE targets
						for (int k = 0; k < currentItemset.size; k++) {
							int beta = currentItemset.data[k];
							for (int alpha = 0; alpha < n; alpha++) {
								if (firstEvt[alpha] >= 0 && firstEvt[alpha] < evtNum) {
									seqExtCount[alpha * n + beta] = 1;
								}
							}
						}

						currentItemset.size = 0;
					}
				} else {
					currentItemset.add(item);
				}
			}

			// Accumulate counts into sparse builders
			for (int alpha = 0; alpha < n; alpha++) {
				for (int beta = 0; beta < n; beta++) {
					if (seqExtCount[alpha * n + beta] > 0) {
						int idx = findInList(seBuilders[alpha], beta);
						if (idx < 0) {
							seBuilders[alpha].add(beta);
							seCountBuilders[alpha].add(1);
						} else {
							seCountBuilders[alpha].data[idx]++;
						}
					}

					if (alpha < beta && itemExtCount[alpha * n + beta] > 0) {
						int idx = findInList(ieBuilders[alpha], beta);
						if (idx < 0) {
							ieBuilders[alpha].add(beta);
							ieCountBuilders[alpha].add(1);
						} else {
							ieCountBuilders[alpha].data[idx]++;
						}
					}
				}
			}
		}

		// Convert to final arrays
		seqExtItems = new int[n][];
		seqExtCounts = new int[n][];
		itemExtItems = new int[n][];
		itemExtCounts = new int[n][];

		for (int i = 0; i < n; i++) {
			seqExtItems[i] = Arrays.copyOf(seBuilders[i].data, seBuilders[i].size);
			seqExtCounts[i] = Arrays.copyOf(seCountBuilders[i].data, seCountBuilders[i].size);
			itemExtItems[i] = Arrays.copyOf(ieBuilders[i].data, ieBuilders[i].size);
			itemExtCounts[i] = Arrays.copyOf(ieCountBuilders[i].data, ieCountBuilders[i].size);
		}
	}

	private int findInList(IntList list, int value) {
		for (int i = 0; i < list.size; i++) {
			if (list.data[i] == value)
				return i;
		}
		return -1;
	}

	// =========================================================================
	// Primitive growable int list
	// =========================================================================

	private static final class IntList {
		int[] data;
		int size;

		IntList() {
			this(8);
		}

		IntList(int capacity) {
			data = new int[Math.max(capacity, 2)];
			size = 0;
		}

		void add(int v) {
			if (size == data.length) {
				data = Arrays.copyOf(data, size * 2);
			}
			data[size++] = v;
		}
//
//		void clear() {
//			size = 0;
//		}
	}

	// =========================================================================
	// Mining
	// =========================================================================

	private void minePatterns() throws IOException {
		// Reusable pattern buffer (avoid allocation)
		int[] patternBuffer = new int[100];
		int[] patternLengths = new int[20]; // itemset lengths

		for (int alpha = 0; alpha < frequentItemCount; alpha++) {
			if (headerArrays[alpha].length == 0)
				continue;

			IntList nodeList = buildInitialNodeList(alpha);
			int support = computeSupport(nodeList);
			if (support < minSupportAbsolute)
				continue;

			patternBuffer[0] = alpha;
			patternLengths[0] = 1;

			// A single-item pattern has length 1; write it only if it satisfies
			// the minimum pattern length constraint.
			writePattern(patternBuffer, patternLengths, 1, support);

			// Only recurse if the maximum pattern length allows extending beyond
			// the current length of 1.
			if (maximumPatternLength > 1) {
				int[] sList = buildSeqExtCandidates(alpha);
				int[] iList = buildItemExtCandidates(alpha);

				treeMiner(patternBuffer, patternLengths, 1, nodeList, sList, iList);
			}
		}
	}

	private void treeMiner(int[] patternBuf, int[] patternLens, int depth, IntList nodeList, int[] sList, int[] iList)
			throws IOException {

		int lastItemsetStart = 0;
		for (int i = 0; i < depth - 1; i++) {
			lastItemsetStart += patternLens[i];
		}
		int lastItemsetLen = patternLens[depth - 1];

		// Current total pattern length (number of items across all itemsets)
		int currentPatternLength = computePatternLength(patternLens, depth);

		// Prune: if we have already reached the maximum pattern length, we cannot
		// add any more items, so stop recursion immediately.
		if (currentPatternLength >= maximumPatternLength) {
			return;
		}

		// Co-existence pruning
		if (enablePruning) {
			sList = coExtPrune(patternBuf, lastItemsetStart, lastItemsetLen, sList, true);
			iList = coExtPrune(patternBuf, lastItemsetStart, lastItemsetLen, iList, false);
		}

		// SE phase
		int[] freqSEItems = new int[sList.length];
		IntList[] freqSENodes = new IntList[sList.length];
		int[] freqSESup = new int[sList.length];
		int freqSECount = 0;
		int[] firstVisitedSum = new int[sList.length];

		for (int si = 0; si < sList.length; si++) {
			int beta = sList[si];
			IntList ext = new IntList();
			int fvSum = seExtension(nodeList, beta, ext);
			firstVisitedSum[si] = fvSum;
			int sup = computeSupport(ext);
			if (sup >= minSupportAbsolute) {
				freqSEItems[freqSECount] = beta;
				freqSENodes[freqSECount] = ext;
				freqSESup[freqSECount] = sup;
				freqSECount++;
			}
		}

		// IE phase
		int[] freqIEItems = new int[iList.length];
		IntList[] freqIENodes = new IntList[iList.length];
		int[] freqIESup = new int[iList.length];
		int freqIECount = 0;

		for (int si = 0; si < iList.length; si++) {
			int beta = iList[si];

			if (enablePruning) {
				int sIdx = linearSearch(sList, beta);
				if (sIdx >= 0 && !containedIn(freqSEItems, freqSECount, beta)
						&& firstVisitedSum[sIdx] < minSupportAbsolute) {
					continue;
				}
			}

			IntList ext = new IntList();
			int sup = ieExtension(nodeList, beta, patternBuf, lastItemsetStart, lastItemsetLen, ext);
			if (sup >= minSupportAbsolute) {
				freqIEItems[freqIECount] = beta;
				freqIENodes[freqIECount] = ext;
				freqIESup[freqIECount] = sup;
				freqIECount++;
			}
		}

		// Recurse SE
		for (int si = 0; si < freqSECount; si++) {
			int beta = freqSEItems[si];

			int newDepth = depth + 1;
			ensurePatternCapacity(patternBuf, patternLens, newDepth, lastItemsetStart + lastItemsetLen + 1);
			patternBuf[lastItemsetStart + lastItemsetLen] = beta;
			patternLens[depth] = 1;

			// The new pattern length after SE extension is currentPatternLength + 1
			// (one new item added in a new itemset).
			int newPatternLength = currentPatternLength + 1;

			// Write the pattern only if it satisfies the minimum length constraint.
			writePattern(patternBuf, patternLens, newDepth, freqSESup[si]);

			// Only recurse deeper if the new pattern length is still below the maximum.
			if (newPatternLength < maximumPatternLength) {
				int[] newSList = Arrays.copyOf(freqSEItems, freqSECount);
				int[] newIList = filterGreaterThan(freqSEItems, freqSECount, beta);

				treeMiner(patternBuf, patternLens, newDepth, freqSENodes[si], newSList, newIList);
			}
		}

		// Recurse IE
		for (int si = 0; si < freqIECount; si++) {
			int beta = freqIEItems[si];

			ensurePatternCapacity(patternBuf, patternLens, depth, lastItemsetStart + lastItemsetLen + 1);
			patternBuf[lastItemsetStart + lastItemsetLen] = beta;
			patternLens[depth - 1] = lastItemsetLen + 1;

			// The new pattern length after IE extension is currentPatternLength + 1
			// (one new item added to the last existing itemset).
			int newPatternLength = currentPatternLength + 1;

			// Write the pattern only if it satisfies the minimum length constraint.
			writePattern(patternBuf, patternLens, depth, freqIESup[si]);

			// Only recurse deeper if the new pattern length is still below the maximum.
			if (newPatternLength < maximumPatternLength) {
				int[] newSList = Arrays.copyOf(freqSEItems, freqSECount);
				int[] newIList = filterGreaterThan(freqIEItems, freqIECount, beta);

				treeMiner(patternBuf, patternLens, depth, freqIENodes[si], newSList, newIList);
			}

			// Restore
			patternLens[depth - 1] = lastItemsetLen;
		}
	}

	private void ensurePatternCapacity(int[] patternBuf, int[] patternLens, int depth, int requiredItems) {
		// Pattern buffers are passed by reference; if needed, caller should handle
		// resizing
		// For simplicity, we assume buffers are large enough or we'd need to return new
		// arrays
	}

	// =========================================================================
	// SE and IE extensions
	// =========================================================================

	private int seExtension(IntList nodeList, int beta, IntList extNodeList) {
		int[] hdr = headerArrays[beta];
		int heuristicUpperBound = 0;

		for (int s = 0; s < nodeList.size; s++) {
			int src = nodeList.data[s];
			int srcEvent = nodeEvent[src];
			int srcPre = nodePreOrder[src];
			int subEnd = nodeSubtreeEndPre[src];

			int i = lowerBound(hdr, srcPre + 1);
			while (i < hdr.length) {
				int cand = hdr[i];
				if (nodePreOrder[cand] > subEnd)
					break;

				heuristicUpperBound += nodeCount[cand];

				int candSubEnd = nodeSubtreeEndPre[cand];
				i++;
				while (i < hdr.length && nodePreOrder[hdr[i]] <= candSubEnd) {
					i++;
				}
			}

			i = lowerBound(hdr, srcPre + 1);
			while (i < hdr.length) {
				int cand = hdr[i];
				if (nodePreOrder[cand] > subEnd)
					break;

				if (nodeEvent[cand] > srcEvent) {
					extNodeList.add(cand);

					int candSubEnd = nodeSubtreeEndPre[cand];
					i++;
					while (i < hdr.length && nodePreOrder[hdr[i]] <= candSubEnd) {
						i++;
					}
				} else {
					i++;
				}
			}
		}

		return heuristicUpperBound;
	}

	private int ieExtension(IntList nodeList, int beta, int[] patternBuf, int lastStart, int lastLen,
			IntList extNodeList) {
		int[] hdr = headerArrays[beta];

		long reqMask = 0L;
		boolean hasLarge = false;
		for (int k = 0; k < lastLen; k++) {
			int item = patternBuf[lastStart + k];
			if (item < 64) {
				reqMask |= (1L << item);
			} else {
				hasLarge = true;
			}
		}

		for (int s = 0; s < nodeList.size; s++) {
			int src = nodeList.data[s];
			int srcPre = nodePreOrder[src];
			int subEnd = nodeSubtreeEndPre[src];

			int i = lowerBound(hdr, srcPre + 1);

			while (i < hdr.length) {
				int cand = hdr[i];
				if (nodePreOrder[cand] > subEnd)
					break;

				if (checkParentInfo(cand, patternBuf, lastStart, lastLen, reqMask, hasLarge)) {
					extNodeList.add(cand);
					int candSubEnd = nodeSubtreeEndPre[cand];
					i++;
					while (i < hdr.length && nodePreOrder[hdr[i]] <= candSubEnd) {
						i++;
					}
				} else {
					i++;
				}
			}
		}

		return computeSupport(extNodeList);
	}

	// =========================================================================
	// Helper methods
	// =========================================================================

	private IntList buildInitialNodeList(int alpha) {
		int[] hdr = headerArrays[alpha];
		IntList nl = new IntList();
		int subEnd = nodeSubtreeEndPre[SP_ROOT];
		int i = 0;

		while (i < hdr.length) {
			int cand = hdr[i];
			if (nodePreOrder[cand] > subEnd)
				break;
			nl.add(cand);
			int candEnd = nodeSubtreeEndPre[cand];
			i++;
			while (i < hdr.length && nodePreOrder[hdr[i]] <= candEnd) {
				i++;
			}
		}
		return nl;
	}

	private int computeSupport(IntList nodeList) {
		int sup = 0;
		for (int i = 0; i < nodeList.size; i++) {
			sup += nodeCount[nodeList.data[i]];
		}
		return sup;
	}

	private boolean checkParentInfo(int cand, int[] patternBuf, int start, int len, long reqMask, boolean hasLarge) {
		if ((nodeParentInfo[cand] & reqMask) != reqMask) {
			return false;
		}
		if (hasLarge) {
			int evt = nodeEvent[cand];
			for (int k = 0; k < len; k++) {
				int item = patternBuf[start + k];
				if (item >= 64) {
					boolean found = false;
					int p = nodeParent[cand];
					while (p != NULL_NODE && nodeEvent[p] == evt) {
						if (nodeItem[p] == item) {
							found = true;
							break;
						}
						p = nodeParent[p];
					}
					if (!found)
						return false;
				}
			}
		}
		return true;
	}

	private int lowerBound(int[] nodes, int targetPre) {
		int lo = 0, hi = nodes.length;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (nodePreOrder[nodes[mid]] < targetPre) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return lo;
	}

	private int[] buildSeqExtCandidates(int alphaDense) {
		IntList list = new IntList();
		int[] items = seqExtItems[alphaDense];
		int[] counts = seqExtCounts[alphaDense];
		for (int i = 0; i < items.length; i++) {
			if (counts[i] >= minSupportAbsolute) {
				list.add(items[i]);
			}
		}
		return Arrays.copyOf(list.data, list.size);
	}

	private int[] buildItemExtCandidates(int alphaDense) {
		IntList list = new IntList();
		int[] items = itemExtItems[alphaDense];
		int[] counts = itemExtCounts[alphaDense];
		for (int i = 0; i < items.length; i++) {
			if (counts[i] >= minSupportAbsolute) {
				list.add(items[i]);
			}
		}
		return Arrays.copyOf(list.data, list.size);
	}

	private int[] coExtPrune(int[] patternBuf, int lastStart, int lastLen, int[] candidates, boolean isSE) {
		IntList out = new IntList(candidates.length);

		outer: for (int beta : candidates) {
			for (int k = 0; k < lastLen; k++) {
				int alpha = patternBuf[lastStart + k];

				int[] items = isSE ? seqExtItems[alpha] : (alpha < beta ? itemExtItems[alpha] : itemExtItems[beta]);
				int[] counts = isSE ? seqExtCounts[alpha] : (alpha < beta ? itemExtCounts[alpha] : itemExtCounts[beta]);
				int target = isSE ? beta : (alpha < beta ? beta : alpha);

				int idx = linearSearch(items, target);
				if (idx < 0 || counts[idx] < minSupportAbsolute) {
					continue outer;
				}
			}
			out.add(beta);
		}
		return Arrays.copyOf(out.data, out.size);
	}

	private int[] filterGreaterThan(int[] array, int length, int threshold) {
		IntList out = new IntList(length);
		for (int i = 0; i < length; i++) {
			if (array[i] > threshold) {
				out.add(array[i]);
			}
		}
		return Arrays.copyOf(out.data, out.size);
	}

	private int linearSearch(int[] array, int value) {
		for (int i = 0; i < array.length; i++) {
			if (array[i] == value)
				return i;
		}
		return -1;
	}

	private boolean containedIn(int[] array, int length, int value) {
		for (int i = 0; i < length; i++) {
			if (array[i] == value)
				return true;
		}
		return false;
	}

	// =========================================================================
	// Output
	// =========================================================================

	/**
	 * Write a pattern to the output file if it satisfies the minimum pattern length
	 * constraint. The maximum pattern length constraint is enforced during the
	 * recursive search (patterns exceeding the maximum are never generated), so
	 * only the minimum needs to be checked here.
	 *
	 * @param patternBuf  flat array of dense item ids
	 * @param patternLens array of itemset sizes
	 * @param depth       number of itemsets in the pattern
	 * @param support     absolute support of the pattern
	 * @throws IOException if writing fails
	 */
	private void writePattern(int[] patternBuf, int[] patternLens, int depth, int support) throws IOException {
		// Compute the total number of items in this pattern.
		int patternLength = computePatternLength(patternLens, depth);

		// Enforce minimum pattern length constraint: skip patterns that are too short.
		if (patternLength < minimumPatternLength) {
			return;
		}

		// Note: the maximum pattern length constraint is enforced during the recursive
		// search, so we do not need to check it again here. However, as a safety net
		// we guard against it as well.
		if (patternLength > maximumPatternLength) {
			return;
		}

		patternCount++;
		if (writer == null)
			return;

		patternBuilder.setLength(0);
		int pos = 0;
		for (int d = 0; d < depth; d++) {
			for (int i = 0; i < patternLens[d]; i++) {
				patternBuilder.append(denseToOriginal[patternBuf[pos++]]).append(' ');
			}
			patternBuilder.append("-1 ");
		}
		patternBuilder.append("#SUP: ").append(support);
		writer.write(patternBuilder.toString());
		writer.newLine();
	}

	// =========================================================================
	// Public statistics
	// =========================================================================

	public long getTotalTime() {
		return endTimestamp - startTimestamp;
	}

	public int getPatternCount() {
		return patternCount;
	}

	public void printStatistics() {
		System.out.println("=== Tree-Miner v.2.67 Statistics ===");
		System.out.println("Pattern count : " + patternCount);
		System.out.println("Total time    : " + (endTimestamp - startTimestamp) + " ms");
		System.out.println("Max memory    : " + MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println("Node count    : " + treeSize);
		System.out.println("==============================");
	}
}