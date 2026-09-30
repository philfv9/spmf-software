package ca.pfv.spmf.algorithms.sequentialpatterns.snaspm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.StringTokenizer;

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
 * Implementation of the SNA-SPM algorithm for mining frequent sequential
 * patterns.
 *
 * @author Philippe Fournier-Viger, 2026
 */
public class AlgoSNASPM {

	/** Maximum memory (MB) recorded during the last execution. */
	private double maxMemory = 0;

	/** Reusable char buffer for writing patterns without String allocation. */
	private char[] writeChars = new char[256];

	/** Minimum number of items in a pattern to output it. */
	private int minPatternLength = 0;

	/**
	 * Maximum number of items in a pattern to output it (Integer.MAX_VALUE =
	 * infinity).
	 */
	private int maxPatternLength = Integer.MAX_VALUE;

	/** Items per sequence (flat representation). */
	private int[][] seqItems;

	/** Event ids per sequence (flat representation). */
	private int[][] seqEvents;

	/** Number of valid entries per sequence. */
	private int[] seqLen;

	/** Number of sequences in the database. */
	private int seqCount;

	/** Minimum support threshold as an absolute number of sequences. */
	private int minsupAbsolute;

	/** Start time of the last run (ms). */
	private long startTime;

	/** End time of the last run (ms). */
	private long endTime;

	/** Number of patterns output. */
	private int patternCount;

	/** Writer used to output patterns. */
	private BufferedWriter writer;

	/** Root node of the SPC-tree. */
	private SPCNode root;

	/** Current pre-order counter for tree labeling. */
	private int currentPre;

	/** Current post-order counter for tree labeling. */
	private int currentPost;

	/** Frequent items in ascending order (original item values). */
	private int[] frequentItemsArray;

	/** Support of each frequent item (same index as frequentItemsArray). */
	private int[] itemSupport;

	/** Map item value -> index in frequentItemsArray, or -1 if infrequent. */
	private int[] itemToIndex;

	/** Largest item value observed in the input database. */
	private int largestItemInDatabase;

	/** SN-lists for all frequent 1-items. */
	private SNLists snListsOfItems;

	/** Stack of join results used during DFS backtracking. */
	private JoinStack joinStack;

	/** Current pattern prefix being explored. */
	private PatternState pattern;

	/** Reusable buffer used to build pattern strings. */
	private final StringBuilder writeBuffer = new StringBuilder(128);

	/** Number of DFS calls between periodic memory checks. */
	private static final int MEMORY_CHECK_INTERVAL = 1000;

	/** DFS node counter used for periodic memory checks. */
	private int dfsNodeCount;

	/** Maximum input sequence length (in items). */
	private int maxSequenceLength = 0;

	/** Size of the last join result written by an extension. */
	private int joinSize;

	/** Support of the last join result computed by an extension. */
	private int joinSupport;

	/**
	 * Stack of SN-list snapshots produced during joins, indexed by DFS depth.
	 */
	private static class JoinStack {
		/** Pre-order labels per stack level. */
		int[][] pre;
		/** Post-order labels per stack level. */
		int[][] post;
		/** Event indices per stack level. */
		int[][] event;
		/** Number of valid entries per stack level. */
		int[] size;
		/** Index of the next free level. */
		int top;

		/**
		 * Creates a join stack with an initial number of levels.
		 *
		 * @param initCap initial number of levels
		 */
		JoinStack(int initCap) {
			pre = new int[initCap][];
			post = new int[initCap][];
			event = new int[initCap][];
			size = new int[initCap];
			top = 0;
		}

		/**
		 * Doubles the number of available stack levels.
		 */
		void grow() {
			int newCap = pre.length * 2;
			pre = Arrays.copyOf(pre, newCap);
			post = Arrays.copyOf(post, newCap);
			event = Arrays.copyOf(event, newCap);
			size = Arrays.copyOf(size, newCap);
		}

		/**
		 * Ensures that the arrays at a level can hold at least a given capacity.
		 *
		 * @param idx       stack level index
		 * @param neededCap minimum capacity
		 */
		void ensureSlotCapacity(int idx, int neededCap) {
			final int curCap = pre[idx] == null ? 0 : pre[idx].length;
			if (curCap < neededCap) {
				final int newCap = Math.max(neededCap, curCap * 2);
				pre[idx] = new int[newCap];
				post[idx] = new int[newCap];
				event[idx] = new int[newCap];
			}
		}
	}

	/**
	 * Mutable pattern state reused across DFS calls to avoid copying.
	 */
	private static class PatternState {
		/** Items in the current prefix (flattened). */
		int[] items;
		/** Number of valid items in items[]. */
		int size;
		/** Exclusive end offset in items[] for each itemset. */
		int[] itemsetEnd;
		/** Number of itemsets in the current prefix. */
		int itemsetCount;
		/** Last item added (used for i-extension ordering). */
		int lastItem;

		/**
		 * Creates buffers sized for the maximum pattern length.
		 *
		 * @param maxLen maximum number of items in a pattern
		 */
		PatternState(int maxLen) {
			items = new int[maxLen];
			itemsetEnd = new int[maxLen];
		}
	}

	/**
	 * Sets the minimum number of items that a pattern must contain to be output.
	 *
	 * @param minimumPatternLength the minimum pattern length (>= 0)
	 */
	public void setMinimumPatternLength(int minimumPatternLength) {
		if (minimumPatternLength < 0) {
			throw new IllegalArgumentException("minimumPatternLength must be >= 0");
		}
		this.minPatternLength = minimumPatternLength;
	}

	/**
	 * Sets the maximum number of items that a pattern can contain to be output.
	 *
	 * @param maximumPatternLength the maximum pattern length (>= 0,
	 *                             Integer.MAX_VALUE = infinity)
	 */
	public void setMaximumPatternLength(int maximumPatternLength) {
		if (maximumPatternLength < 0) {
			throw new IllegalArgumentException("maximumPatternLength must be >= 0");
		}
		this.maxPatternLength = maximumPatternLength;
	}

	/**
	 * Runs the algorithm on an input file and writes patterns to an output file.
	 *
	 * @param input  path to the input database
	 * @param output path to the output file
	 * @param minsup minimum support as a fraction of sequence count (e.g., 0.5)
	 * @throws IOException if an I/O error occurs
	 */
	public void runAlgorithm(String input, String output, double minsup) throws IOException {
		// Reset stats and memory logger
		MemoryLogger.getInstance().reset();
		startTime = System.currentTimeMillis();
		endTime = 0;
		patternCount = 0;
		dfsNodeCount = 0;
		maxMemory = 0;

		// Validate pattern length constraints
		if (minPatternLength > maxPatternLength) {
			throw new IllegalArgumentException("Minimum pattern length cannot be greater than maximum pattern length.");
		}

		// Load the database into memory
		loadDatabase(input);

		// Convert minsup to an absolute support count
		minsupAbsolute = (int) Math.ceil((minsup * seqCount) - 0.000001);

		// Find frequent items and build item name converter
		findFrequentItems();

		// Build the SPC-tree using frequent items only
		buildSPCTree();

		// Release the database arrays (tree is built)
		seqItems = null;
		seqEvents = null;
		seqLen = null;

		// Assign pre/post labels to tree nodes
		currentPre = 0;
		currentPost = 0;
		assignPrePost(root);

		// Build SN-lists for frequent 1-items
		initializeSNLists();
		root = null;

		// Allocate reusable pattern state buffers
		pattern = new PatternState(maxSequenceLength);

		// Mine patterns and write them to disk
		try (BufferedWriter localWriter = new BufferedWriter(new FileWriter(output))) {
			writer = localWriter;
			writer.write("@FILETYPE=\"Frequent sequential patterns\"");
			writer.newLine();
			writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
			writer.newLine();
			
			final int n = frequentItemsArray.length;

			for (int i = 0; i < n; i++) {
				// Initialize the 1-item prefix
				pattern.items[0] = frequentItemsArray[i];
				pattern.size = 1;
				pattern.itemsetEnd[0] = 1;
				pattern.itemsetCount = 1;
				pattern.lastItem = frequentItemsArray[i];

				// Output the prefix if it matches length constraints
				if (pattern.size >= minPatternLength && pattern.size <= maxPatternLength) {
					writePattern(itemSupport[i]);
				}

				// Continue growing if the maximum length is not reached
				if (pattern.size < maxPatternLength) {
					dfs(snListsOfItems.pre[i], snListsOfItems.post[i], snListsOfItems.event[i], snListsOfItems.size[i]);
				}
			}
		} finally {
			writer = null;
		}

		// Finalize stats
		endTime = System.currentTimeMillis();
		MemoryLogger.getInstance().checkMemory();
		maxMemory = MemoryLogger.getInstance().getMaxMemory();
	}

	/**
	 * Performs depth-first pattern growth from the current prefix and its SN-list.
	 *
	 * @param pPre   prefix pre-order labels
	 * @param pPost  prefix post-order labels
	 * @param pEvent prefix event indices
	 * @param pSize  number of entries in the prefix SN-list
	 * @throws IOException if an I/O error occurs while writing patterns
	 */
	private void dfs(int[] pPre, int[] pPost, int[] pEvent, int pSize) throws IOException {
		// Periodic memory check
		if (++dfsNodeCount % MEMORY_CHECK_INTERVAL == 0) {
			MemoryLogger.getInstance().checkMemory();
		}

		// Stop if there is nothing to extend
		if (pSize == 0) {
			return;
		}

		// Stop if maximum length is already reached
		if (pattern.size >= maxPatternLength) {
			return;
		}

		final int n = frequentItemsArray.length;

		// Save backtracking state
		final int savedSize = pattern.size;
		final int savedItemsetCount = pattern.itemsetCount;
		final int savedLastItem = pattern.lastItem;

		// Try all s-extensions
		for (int i = 0; i < n; i++) {
			final int slot = reserveJoinSlot(snListsOfItems.size[i]);
			sExtension(pPre, pPost, pEvent, pSize, i, joinStack.pre[slot], joinStack.post[slot], joinStack.event[slot]);

			if (joinSupport >= minsupAbsolute) {
				// Apply the extension to the current pattern
				joinStack.size[slot] = joinSize;
				joinStack.top++;
				pattern.items[pattern.size] = frequentItemsArray[i];
				pattern.itemsetEnd[pattern.itemsetCount] = pattern.size + 1;
				pattern.size++;
				pattern.itemsetCount++;
				pattern.lastItem = frequentItemsArray[i];

				// Output and recurse if allowed by length constraints
				if (pattern.size >= minPatternLength && pattern.size <= maxPatternLength) {
					writePattern(joinSupport);
				}
				if (pattern.size < maxPatternLength) {
					dfs(joinStack.pre[slot], joinStack.post[slot], joinStack.event[slot], joinStack.size[slot]);
				}

				// Backtrack
				pattern.size = savedSize;
				pattern.itemsetCount = savedItemsetCount;
				pattern.lastItem = savedLastItem;
				joinStack.top--;
			}
		}

		// Try i-extensions (items strictly greater than last item)
		final int startIdx = upperBoundIndex(savedLastItem);
		for (int i = startIdx; i < n; i++) {
			final int slot = reserveJoinSlot(snListsOfItems.size[i]);
			iExtension(pPre, pPost, pEvent, pSize, i, joinStack.pre[slot], joinStack.post[slot], joinStack.event[slot]);

			if (joinSupport >= minsupAbsolute) {
				// Apply the extension to the current pattern
				joinStack.size[slot] = joinSize;
				joinStack.top++;
				pattern.items[pattern.size] = frequentItemsArray[i];
				pattern.itemsetEnd[pattern.itemsetCount - 1] = pattern.size + 1;
				pattern.size++;
				pattern.lastItem = frequentItemsArray[i];

				// Output and recurse if allowed by length constraints
				if (pattern.size >= minPatternLength && pattern.size <= maxPatternLength) {
					writePattern(joinSupport);
				}
				if (pattern.size < maxPatternLength) {
					dfs(joinStack.pre[slot], joinStack.post[slot], joinStack.event[slot], joinStack.size[slot]);
				}

				// Backtrack
				pattern.size = savedSize;
				pattern.itemsetCount = savedItemsetCount;
				pattern.lastItem = savedLastItem;
				joinStack.top--;
			}
		}
	}

	/**
	 * Reserves a join stack slot and ensures it has enough capacity.
	 *
	 * @param neededCap upper bound on the number of join entries to write
	 * @return the reserved slot index
	 */
	private int reserveJoinSlot(int neededCap) {
		// Grow the stack if needed
		if (joinStack.top >= joinStack.pre.length) {
			joinStack.grow();
		}

		// Ensure arrays are large enough for the join output
		final int slot = joinStack.top;
		joinStack.ensureSlotCapacity(slot, neededCap);
		return slot;
	}

	/**
	 * Computes an s-extension join of a prefix SN-list with a 1-item SN-list.
	 *
	 * @param pPre     prefix pre-order labels
	 * @param pPost    prefix post-order labels
	 * @param pEvent   prefix event indices
	 * @param pSize    number of prefix entries
	 * @param itemIdx  index of the extension item in frequentItemsArray
	 * @param outPre   output pre-order labels
	 * @param outPost  output post-order labels
	 * @param outEvent output event indices
	 */
	private void sExtension(int[] pPre, int[] pPost, int[] pEvent, int pSize, int itemIdx, int[] outPre, int[] outPost,
			int[] outEvent) {

		// Read the SN-list of the extension item
		final int[] iPre = snListsOfItems.pre[itemIdx];
		final int[] iPost = snListsOfItems.post[itemIdx];
		final int[] iEvent = snListsOfItems.event[itemIdx];
		final int[] iCount = snListsOfItems.count[itemIdx];
		final int iSize = snListsOfItems.size[itemIdx];

		int size = 0;
		int support = 0;

		// Quick exit if the item has no occurrences
		if (iSize == 0) {
			joinSize = 0;
			joinSupport = 0;
			return;
		}

		int boundary = 0;
		int minEventSoFar = Integer.MAX_VALUE;
		int maxPostSoFar = Integer.MIN_VALUE;
		int lastCountedPost = -1;

		// Scan item occurrences and match them with prefix occurrences
		for (int i = 0; i < iSize; i++) {
			final int yPre = iPre[i];
			final int yPost = iPost[i];
			final int yEvent = iEvent[i];

			while (boundary < pSize && pPre[boundary] < yPre) {
				final int pe = pEvent[boundary];
				final int pp = pPost[boundary];
				if (pe < minEventSoFar)
					minEventSoFar = pe;
				if (pp > maxPostSoFar)
					maxPostSoFar = pp;
				boundary++;
			}

			if (maxPostSoFar <= yPost || minEventSoFar >= yEvent) {
				continue;
			}

			// Find a valid ancestor for the extension
			for (int j = boundary - 1; j >= 0; j--) {
				if (pPost[j] > yPost && pEvent[j] < yEvent) {
					outPre[size] = yPre;
					outPost[size] = yPost;
					outEvent[size] = yEvent;
					size++;

					if (yPost > lastCountedPost) {
						support += iCount[i];
						lastCountedPost = yPost;
					}
					break;
				}
			}
		}

		joinSize = size;
		joinSupport = support;
	}

	/**
	 * Computes an i-extension join of a prefix SN-list with a 1-item SN-list.
	 *
	 * @param pPre     prefix pre-order labels
	 * @param pPost    prefix post-order labels
	 * @param pEvent   prefix event indices
	 * @param pSize    number of prefix entries
	 * @param itemIdx  index of the extension item in frequentItemsArray
	 * @param outPre   output pre-order labels
	 * @param outPost  output post-order labels
	 * @param outEvent output event indices
	 */
	private void iExtension(int[] pPre, int[] pPost, int[] pEvent, int pSize, int itemIdx, int[] outPre, int[] outPost,
			int[] outEvent) {

		// Read the SN-list of the extension item
		final int[] iPre = snListsOfItems.pre[itemIdx];
		final int[] iPost = snListsOfItems.post[itemIdx];
		final int[] iEvent = snListsOfItems.event[itemIdx];
		final int[] iCount = snListsOfItems.count[itemIdx];
		final int iSize = snListsOfItems.size[itemIdx];

		int size = 0;
		int support = 0;

		// Quick exit if the item has no occurrences
		if (iSize == 0) {
			joinSize = 0;
			joinSupport = 0;
			return;
		}

		int boundary = 0;
		int minEventSoFar = Integer.MAX_VALUE;
		int maxEventSoFar = Integer.MIN_VALUE;
		int maxPostSoFar = Integer.MIN_VALUE;
		int lastCountedPost = -1;

		// Scan item occurrences and match them with prefix occurrences
		for (int i = 0; i < iSize; i++) {
			final int yPre = iPre[i];
			final int yPost = iPost[i];
			final int yEvent = iEvent[i];

			while (boundary < pSize && pPre[boundary] < yPre) {
				final int pe = pEvent[boundary];
				final int pp = pPost[boundary];
				if (pe < minEventSoFar)
					minEventSoFar = pe;
				if (pe > maxEventSoFar)
					maxEventSoFar = pe;
				if (pp > maxPostSoFar)
					maxPostSoFar = pp;
				boundary++;
			}

			if (maxPostSoFar <= yPost)
				continue;
			if (yEvent < minEventSoFar || yEvent > maxEventSoFar)
				continue;

			// Find a valid ancestor in the same event for the extension
			for (int j = boundary - 1; j >= 0; j--) {
				if (pPost[j] > yPost && pEvent[j] == yEvent) {
					outPre[size] = yPre;
					outPost[size] = yPost;
					outEvent[size] = yEvent;
					size++;

					if (yPost > lastCountedPost) {
						support += iCount[i];
						lastCountedPost = yPost;
					}
					break;
				}
			}
		}

		joinSize = size;
		joinSupport = support;
	}

	/**
	 * Finds the first index of a frequent item strictly greater than a target.
	 *
	 * @param target the value to exceed
	 * @return the first index with frequentItemsArray[index] > target
	 */
	private int upperBoundIndex(int target) {
		// Binary search on the frequent item array
		int low = 0;
		int high = frequentItemsArray.length;
		while (low < high) {
			int mid = (low + high) >>> 1;
			if (frequentItemsArray[mid] <= target)
				low = mid + 1;
			else
				high = mid;
		}
		return low;
	}

	/**
	 * Builds SN-lists for all frequent 1-items by traversing the SPC-tree.
	 */
	private void initializeSNLists() {
		// Allocate the SN-list container
		final int n = frequentItemsArray.length;
		snListsOfItems = new SNLists(n);

		// Traverse the tree using an explicit stack
		final int stackCap = Math.max(currentPre, 16);
		SPCNode[] travStack = new SPCNode[stackCap];

		// First pass: count entries per item
		int[] nodeCounts = new int[n];
		int travTop = 0;
		travStack[travTop++] = root;

		while (travTop > 0) {
			SPCNode node = travStack[--travTop];
			final int idx = node.item;
			if (idx >= 0)
				nodeCounts[idx]++;

			final SPCNode[] ch = node.childArray;
			final int cnt = node.childCount;
			for (int i = cnt - 1; i >= 0; i--) {
				travStack[travTop++] = ch[i];
			}
		}

		// Allocate arrays for each item SN-list
		for (int i = 0; i < n; i++) {
			snListsOfItems.pre[i] = new int[nodeCounts[i]];
			snListsOfItems.post[i] = new int[nodeCounts[i]];
			snListsOfItems.event[i] = new int[nodeCounts[i]];
			snListsOfItems.count[i] = new int[nodeCounts[i]];
		}

		// Second pass: fill SN-list entries
		travTop = 0;
		travStack[travTop++] = root;

		while (travTop > 0) {
			SPCNode node = travStack[--travTop];
			final int idx = node.item;

			if (idx >= 0) {
				final int sz = snListsOfItems.size[idx];
				snListsOfItems.pre[idx][sz] = node.pre;
				snListsOfItems.post[idx][sz] = node.post;
				snListsOfItems.event[idx][sz] = node.event;
				snListsOfItems.count[idx][sz] = node.count;
				snListsOfItems.size[idx] = sz + 1;
			}

			final SPCNode[] ch = node.childArray;
			final int cnt = node.childCount;
			for (int i = cnt - 1; i >= 0; i--) {
				travStack[travTop++] = ch[i];
			}
		}

		// Initialize the join stack used by DFS
		joinStack = new JoinStack(64);
	}

	/**
	 * Builds the SPC-tree by inserting each sequence after filtering infrequent
	 * items.
	 */
	private void buildSPCTree() {
		// Create the root node
		root = new SPCNode(-1, -1);

		// Temporary arrays used to build the filtered sequence
		int[] tmpItems = new int[256];
		int[] tmpEvents = new int[256];

		for (int s = 0; s < seqCount; s++) {
			final int[] items = seqItems[s];
			final int[] evts = seqEvents[s];
			final int len = seqLen[s];

			// Filter infrequent items and rename to frequent-item indices
			int flatLen = 0;
			for (int k = 0; k < len; k++) {
				final int renamedIdx = itemToIndex[items[k]];
				if (renamedIdx >= 0) {
					if (flatLen == tmpItems.length) {
						tmpItems = Arrays.copyOf(tmpItems, flatLen * 2);
						tmpEvents = Arrays.copyOf(tmpEvents, flatLen * 2);
					}
					tmpItems[flatLen] = renamedIdx;
					tmpEvents[flatLen] = evts[k];
					flatLen++;
				}
			}

			// Insert the filtered sequence into the tree
			if (flatLen > 0) {
				insertSequence(tmpItems, tmpEvents, flatLen);
			}
		}
	}

	/**
	 * Inserts one sequence into the SPC-tree, creating nodes as needed.
	 *
	 * @param items  renamed item indices
	 * @param events event ids aligned with items
	 * @param len    number of valid entries
	 */
	private void insertSequence(int[] items, int[] events, int len) {
		// Walk the tree and add missing children
		SPCNode current = root;
		for (int i = 0; i < len; i++) {
			SPCNode child = current.getChild(items[i], events[i]);
			if (child == null) {
				child = new SPCNode(items[i], events[i]);
				current.addChild(child);
			}
			child.count++;
			current = child;
		}
	}

	/**
	 * Assigns pre-order and post-order labels to each SPC-tree node.
	 *
	 * @param rootNode root of the SPC-tree
	 */
	private void assignPrePost(SPCNode rootNode) {
		// Use an explicit stack for iterative DFS traversal
		int estimatedDepth = 128;
		SPCNode[] nodeStack = new SPCNode[estimatedDepth];
		int[] childIndex = new int[estimatedDepth];
		int top = 0;

		// Initialize root labels and stack
		rootNode.pre = currentPre++;
		nodeStack[top] = rootNode;
		childIndex[top] = 0;

		// Traverse the tree and set pre/post labels
		while (top >= 0) {
			final SPCNode node = nodeStack[top];
			final int ci = childIndex[top];
			final int nCh = node.childCount;

			if (ci < nCh) {
				childIndex[top]++;
				final SPCNode child = node.childArray[ci];
				child.pre = currentPre++;

				top++;
				if (top == nodeStack.length) {
					nodeStack = Arrays.copyOf(nodeStack, top * 2);
					childIndex = Arrays.copyOf(childIndex, top * 2);
				}
				nodeStack[top] = child;
				childIndex[top] = 0;
			} else {
				node.post = currentPost++;
				top--;
			}
		}
	}

	/**
	 * Scans the database to find frequent items and prepares item renaming.
	 */
	private void findFrequentItems() {
		// Find the largest item id to size support arrays
		largestItemInDatabase = 0;
		for (int s = 0; s < seqCount; s++) {
			final int[] items = seqItems[s];
			final int len = seqLen[s];
			for (int k = 0; k < len; k++) {
				if (items[k] > largestItemInDatabase) {
					largestItemInDatabase = items[k];
				}
			}
		}

		// Count support of each item (sequence-level support)
		final int[] support = new int[largestItemInDatabase + 1];
		final boolean[] seen = new boolean[largestItemInDatabase + 1];

		for (int s = 0; s < seqCount; s++) {
			final int[] items = seqItems[s];
			final int len = seqLen[s];

			for (int k = 0; k < len; k++) {
				final int item = items[k];
				if (!seen[item]) {
					seen[item] = true;
					support[item]++;
				}
			}
			for (int k = 0; k < len; k++) {
				seen[items[k]] = false;
			}
		}

		// Build the frequent item array and the itemToIndex map
		int freqCount = 0;
		for (int v = 0; v <= largestItemInDatabase; v++) {
			if (support[v] >= minsupAbsolute)
				freqCount++;
		}

		frequentItemsArray = new int[freqCount];
		itemSupport = new int[freqCount];
		itemToIndex = new int[largestItemInDatabase + 1];
		Arrays.fill(itemToIndex, -1);

		int idx = 0;
		for (int v = 0; v <= largestItemInDatabase; v++) {
			if (support[v] >= minsupAbsolute) {
				frequentItemsArray[idx] = v;
				itemSupport[idx] = support[v];
				itemToIndex[v] = idx;
				idx++;
			}
		}
	}

	/**
	 * Loads a sequence database in SPMF format into memory.
	 *
	 * @param input path to the input file
	 * @throws IOException if an I/O error occurs
	 */
	private void loadDatabase(String input) throws IOException {
		// Initialize growing buffers
		int cap = 1024;
		int[][] itemsBuf = new int[cap][];
		int[][] eventsBuf = new int[cap][];
		int[] lenBuf = new int[cap];
		int count = 0;

		// Track the maximum sequence length
		maxSequenceLength = 0;

		try (BufferedReader reader = new BufferedReader(new FileReader(input), 1 << 16)) {
			String line;
			int[] tmpItems = new int[64];
			int[] tmpEvents = new int[64];
			int tmpLen;

			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty())
					continue;
				final char first = line.charAt(0);
				if (first == '#' || first == '%' || first == '@')
					continue;

				// Parse one sequence line
				tmpLen = 0;
				int event = 0;
				boolean hasItems = false;

				StringTokenizer tok = new StringTokenizer(line);
				while (tok.hasMoreTokens()) {
					final int value = Integer.parseInt(tok.nextToken());
					if (value == -1) {
						event++;
					} else if (value == -2) {
						break;
					} else {
						if (tmpLen == tmpItems.length) {
							tmpItems = Arrays.copyOf(tmpItems, tmpLen * 2);
							tmpEvents = Arrays.copyOf(tmpEvents, tmpLen * 2);
						}
						tmpItems[tmpLen] = value;
						tmpEvents[tmpLen] = event + 1;
						tmpLen++;
						hasItems = true;
					}
				}

				// Store the sequence if it contains items
				if (hasItems) {
					if (count == cap) {
						cap *= 2;
						itemsBuf = Arrays.copyOf(itemsBuf, cap);
						eventsBuf = Arrays.copyOf(eventsBuf, cap);
						lenBuf = Arrays.copyOf(lenBuf, cap);
					}

					itemsBuf[count] = Arrays.copyOf(tmpItems, tmpLen);
					eventsBuf[count] = Arrays.copyOf(tmpEvents, tmpLen);
					lenBuf[count] = tmpLen;

					if (tmpLen > maxSequenceLength) {
						maxSequenceLength = tmpLen;
					}
					count++;
				}
			}
		}

		// Finalize database arrays
		seqItems = itemsBuf;
		seqEvents = eventsBuf;
		seqLen = lenBuf;
		seqCount = count;
	}

	/**
	 * Writes the current pattern in SPMF format followed by its support.
	 *
	 * @param support the pattern support
	 * @throws IOException if an I/O error occurs
	 */
	private void writePattern(int support) throws IOException {
		// Serialize the pattern into the reusable buffer
		writeBuffer.setLength(0);
		int pos = 0;

		for (int s = 0; s < pattern.itemsetCount; s++) {
			final int end = pattern.itemsetEnd[s];
			while (pos < end) {
				writeBuffer.append(pattern.items[pos++]).append(' ');
			}
			writeBuffer.append("-1 ");
		}
		writeBuffer.append("#SUP: ").append(support);

		// Write using a reusable char array
		final int len = writeBuffer.length();
		if (writeChars.length < len) {
			writeChars = new char[len * 2];
		}
		writeBuffer.getChars(0, len, writeChars, 0);
		writer.write(writeChars, 0, len);
		writer.newLine();

		patternCount++;
	}

	/**
	 * Prints statistics about the last execution to standard output.
	 */
	public void printStatistics() {
		System.out.println("=============  SNA-SPM v2.67 - STATS =============");
		System.out.println(" Minsup        : " + minsupAbsolute);
		System.out.println(" Patterns count: " + patternCount);
		System.out.println(" Total time    : " + (endTime - startTime) + " ms");
		System.out.println(" Max memory    : " + maxMemory + " MB");
		System.out.println("============================================");
	}
}