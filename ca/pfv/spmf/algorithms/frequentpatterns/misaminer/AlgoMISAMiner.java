package ca.pfv.spmf.algorithms.frequentpatterns.misaminer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ca.pfv.spmf.tools.MemoryLogger;

/* This file is copyright (c) 2025 Philippe Fournier-Viger
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
* Do not remove license and copyright.
*/
/**
 * This is an implementation of the MiSA-Miner algorithm for mining Maximal
 * Frequent Itemsets (MFIs) and Closed Frequent Itemsets (CFIs) using the
 * "missing-set" data structure. The missing set of an itemset X indicates the
 * set of transactions that do NOT contain X (missing(X)).
 *
 * @author Philippe Fournier-Viger, 2026
 */
public class AlgoMISAMiner {

	/** Start timestamp for statistics */
	private long startTimestamp;

	/** End timestamp for statistics */
	private long endTimestamp;

	/** Number of patterns found */
	private long patternCount;

	/** Writer for the output file */
	private BufferedWriter writer;

	/**
	 * If true, mine closed frequent itemsets; otherwise, mine maximal frequent
	 * itemsets
	 */
	private boolean mineClosedItemsets;

	/** Number of frequent single items */
	private int m;

	/** Number of transactions in the filtered database D' */
	private int nPrime;

	/** Number of transactions in the original database */
	private int originalN;

	/** The absolute minimum support count */
	private int minSupportCount;

	/**
	 * The maximum allowed missing-set size for a frequent pattern.
	 */
	private int cap;

	/**
	 * itemIds[i] = original item id of the item with internal index i (sorted
	 * ascending)
	 */
	private int[] itemIds;

	/**
	 * missingSets[i] = the missing vector for the frequent item with internal index
	 * i
	 */
	private MissingSetVector[] missingSets;

	/**
	 * missingCard[i] = |missing(i)|, cached cardinality of the missing-set of item
	 * i
	 */
	private int[] missingCard;

	/**
	 * The anchor set A, stored as internal item indices, in the order selected by
	 * Eq. (7)
	 */
	private int[] anchors;

	/** dom[i] = the dominating anchor index for internal item index i */
	private int[] dom;

	/**
	 * Global item order used by the MFI search: a permutation of 0..m-1 sorted by
	 * (dom, |missing|, itemId)
	 */
	private int[] mfiOrder;

	/**
	 * Reused prefix buffer for MFI DFS (avoids re-allocating the prefix)
	 */
	private int[] prefixBuffer;

	/** Reused membership flags for the leaf-level maximality test (Eq. 10) */
	private boolean[] inPrefixFlags;

	/**
	 * Reused per-recursion-depth scratch buffers holding the classified candidate
	 * extension items at each MFI DFS node (Section 2.3). Indexed by DFS recursion
	 * depth (0..m), which strictly bounds the number of simultaneously "live"
	 * frames, so a single buffer per depth is always safe to reuse across
	 * sibling/ancestor calls.
	 */
	private int[][] mfiCandidateScratch;

	/**
	 * Reused per-recursion-depth missing-set vectors holding M' = M &cup;
	 * missing(x) (Eq. 9) for the child about to be visited at that depth, avoiding
	 * a fresh {@link MissingSetVector} allocation per candidate edge.
	 */
	private MissingSetVector[] mfiMissingScratch;

	/**
	 * Reused per-recursion-depth scratch buffers holding the closure item list
	 * C(M') (Eq. 12) computed at that depth during CFI mining.
	 */
	private int[][] cfiClosureScratch;

	/**
	 * Reused per-recursion-depth missing-set vectors holding M' = M &cup;
	 * missing(x_i) (Eq. 13) for the child about to be visited at that depth.
	 */
	private MissingSetVector[] cfiMissingScratch;

	/**
	 * Reused membership flags for the current closure prefix during CbO recursion
	 */
	private boolean[] inClosureFlags;

	/**
	 * Reused scratch buffer for sorting a pattern's internal indices before output
	 */
	private int[] outputSortBuffer;

	/**
	 * Reused string builder for pattern output, avoiding one allocation per pattern
	 */
	private final StringBuilder outputBuilder = new StringBuilder();

	/**
	 * Counter used to sample memory usage periodically instead of on every DFS node
	 */
	private int opCounter;

	/** Sample memory usage every (MEMORY_CHECK_MASK + 1) operations */
	private static final int MEMORY_CHECK_MASK = 0x3FF;

	/**
	 * Default constructor
	 */
	public AlgoMISAMiner() {
	}

	/**
	 * Run the MiSA-Miner algorithm to mine Maximal Frequent Itemsets (MFIs).
	 *
	 * @param input            the input file path (a transaction database)
	 * @param output           the output file path
	 * @param minsupPercentage the minimum support threshold, as a percentage (e.g.
	 *                         0.4 for 40%)
	 * @throws IOException if an error occurs while reading/writing files
	 */
	public void runAlgorithm(String input, String output, double minsupPercentage) throws IOException {
		runAlgorithm(input, output, minsupPercentage, false);
	}

	/**
	 * Run the MiSA-Miner algorithm to mine either Maximal Frequent Itemsets (MFIs)
	 * or Closed Frequent Itemsets (CFIs).
	 *
	 * @param input              the input file path (a transaction database)
	 * @param output             the output file path
	 * @param minsupPercentage   the minimum support threshold, as a percentage
	 *                           (e.g. 0.4 for 40%)
	 * @param mineClosedItemsets if true, mine closed frequent itemsets (CFIs); if
	 *                           false, mine maximal frequent itemsets (MFIs)
	 * @throws IOException if an error occurs while reading/writing files
	 */
	public void runAlgorithm(String input, String output, double minsupPercentage, boolean mineClosedItemsets)
			throws IOException {

		MemoryLogger.getInstance().reset();
		this.mineClosedItemsets = mineClosedItemsets;
		this.patternCount = 0;
		this.opCounter = 0;
		startTimestamp = System.currentTimeMillis();

		// Section 2.1 / 2.2: preprocessing and missing-set construction
		readDatabaseAndPreprocess(input, minsupPercentage);

		MemoryLogger.getInstance().checkMemory();

		writer = new BufferedWriter(new FileWriter(output));

		if (m > 0) {
			if (mineClosedItemsets) {
				// Section 2.4: missing-set based CFI enumeration
				mineCFI();
			} else {
				// Section 2.3: anchor-driven MFI mining
				buildAnchors();
				computeDom();
				buildMfiOrder();
				mineMFI();
			}
		}

		writer.close();

		MemoryLogger.getInstance().checkMemory();
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * Read the transaction database, filter out infrequent items and empty
	 * transactions (Section 2.1), and build the missing-set representation (Eq. 2)
	 * for each surviving frequent item.
	 * <p>
	 * This method avoids autoboxing and per-token {@code String} allocation: items
	 * are parsed directly from the line's char array positions (no
	 * {@code substring}), and item counts are accumulated in a lightweight
	 * open-addressing {@code int -> int} map ({@link IntIntMap}) instead of a boxed
	 * {@code HashMap<Integer,Integer>}.
	 * <p>
	 * The filtered database D' is never materialized as a second list of arrays:
	 * after determining the frequent items, this method makes two lightweight
	 * in-memory passes directly over the already-parsed raw transactions. The first
	 * pass only counts N' (the number of transactions that remain non-empty after
	 * removing infrequent items), which is required to size the fixed-length
	 * {@link MissingSetVector} bit vectors up front. The second pass assigns
	 * transaction indices to the surviving transactions and clears the
	 * corresponding missing-set bits (Eq. 2) directly, without ever allocating a
	 * filtered copy of the transaction.
	 *
	 * @param input            the input file path
	 * @param minsupPercentage the relative minimum support threshold
	 * @throws IOException if an error occurs while reading the file
	 */
	private void readDatabaseAndPreprocess(String input, double minsupPercentage) throws IOException {
		List<int[]> rawTransactions = new ArrayList<int[]>();
		IntIntMap itemSupport = new IntIntMap(1024);

		int[] tokenBuf = new int[64];

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(input)))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}

				int len = line.length();
				int tokenCount = 0;
				int start = 0;
				while (start < len) {
					int end = line.indexOf(' ', start);
					if (end == -1) {
						end = len;
					}
					if (end > start) {
						int item = parseInt(line, start, end);
						if (tokenCount == tokenBuf.length) {
							tokenBuf = Arrays.copyOf(tokenBuf, tokenBuf.length * 2);
						}
						tokenBuf[tokenCount++] = item;
						itemSupport.increment(item);
					}
					start = end + 1;
				}

				rawTransactions.add(Arrays.copyOf(tokenBuf, tokenCount));
			}
		}

		originalN = rawTransactions.size();
		minSupportCount = Math.max(1, (int) Math.ceil(minsupPercentage * originalN));

		// Determine the surviving frequent single items F (Section 2.1), sorted
		// ascending by item id, using a primitive int[] sort (no boxing).
		int candidateCount = 0;
		for (int i = 0; i < itemSupport.used.length; i++) {
			if (itemSupport.used[i] && itemSupport.values[i] >= minSupportCount) {
				candidateCount++;
			}
		}
		int[] freqItems = new int[candidateCount];
		int p = 0;
		for (int i = 0; i < itemSupport.used.length; i++) {
			if (itemSupport.used[i] && itemSupport.values[i] >= minSupportCount) {
				freqItems[p++] = itemSupport.keys[i];
			}
		}
		Arrays.sort(freqItems);

		m = freqItems.length;
		itemIds = freqItems;

		if (m == 0) {
			nPrime = 0;
			cap = 0;
			missingSets = new MissingSetVector[0];
			missingCard = new int[0];
			return;
		}

		IntIntMap itemToIndex = new IntIntMap(Math.max(16, m));
		for (int i = 0; i < m; i++) {
			itemToIndex.put(itemIds[i], i);
		}

		int rawCount = rawTransactions.size();

		// First in-memory pass: count how many transactions remain
		// non-empty after removing infrequent items (Section 2.1), so the
		// fixed-size missing-set bit vectors can be allocated up front.
		int filteredCount = 0;
		for (int r = 0; r < rawCount; r++) {
			int[] transaction = rawTransactions.get(r);
			int tlen = transaction.length;
			for (int t = 0; t < tlen; t++) {
				if (itemToIndex.getOrDefault(transaction[t], -1) >= 0) {
					filteredCount++;
					break;
				}
			}
		}

		nPrime = filteredCount;
		cap = nPrime - minSupportCount; // Eq. (1)

		// Build missing(x) for each frequent item x (Eq. 2): start as "all
		// transactions miss x", then clear the bit of every transaction that
		// actually contains x.
		missingSets = new MissingSetVector[m];
		for (int i = 0; i < m; i++) {
			missingSets[i] = new MissingSetVector(nPrime);
			missingSets[i].setAllOnes();
		}

		// Second in-memory pass: assign a transaction index to each
		// surviving (non-empty) filtered transaction and clear the
		// corresponding missing-set bits, without ever materializing the
		// filtered transaction as a separate array.
		int t = 0;
		for (int r = 0; r < rawCount; r++) {
			int[] transaction = rawTransactions.get(r);
			int tlen = transaction.length;
			boolean any = false;
			for (int k = 0; k < tlen; k++) {
				int idx = itemToIndex.getOrDefault(transaction[k], -1);
				if (idx >= 0) {
					any = true;
					missingSets[idx].clear(t);
				}
			}
			if (any) {
				t++;
			}
		}

		missingCard = new int[m];
		for (int i = 0; i < m; i++) {
			missingCard[i] = missingSets[i].cardinality();
		}
	}

	/**
	 * Parse a non-negative or negative integer directly from a substring range of a
	 * {@code String}, without allocating a temporary {@code String} (unlike
	 * {@code Integer.parseInt(s.substring(a, b))}).
	 *
	 * @param s     the source string
	 * @param start the start index (inclusive)
	 * @param end   the end index (exclusive)
	 * @return the parsed integer value
	 */
	private static int parseInt(String s, int start, int end) {
		int i = start;
		boolean negative = false;
		if (s.charAt(i) == '-') {
			negative = true;
			i++;
		}
		int result = 0;
		for (; i < end; i++) {
			result = result * 10 + (s.charAt(i) - '0');
		}
		return negative ? -result : result;
	}

	/**
	 * Build the anchor set A from inclusion-minimal single-item missing-sets (Eq.
	 * 7): candidates are sorted by increasing |missing(x)|, and an item x is
	 * selected as an anchor only if no previously selected anchor a satisfies
	 * missing(a) &sube; missing(x).
	 */
	private void buildAnchors() {
		int[] order = new int[m];
		for (int i = 0; i < m; i++) {
			order[i] = i;
		}
		// itemIds[i] is strictly increasing in i by construction, so
		// breaking ties on the internal index i is equivalent to (and
		// cheaper than) breaking ties on itemIds[i]. Sorted with a
		// primitive comparator to avoid boxing entirely.
		primitiveSort(order,
				(a, b) -> missingCard[a] != missingCard[b] ? Integer.compare(missingCard[a], missingCard[b])
						: Integer.compare(a, b));

		int[] anchorTemp = new int[m];
		int anchorCount = 0;
		for (int x : order) {
			boolean dominated = false;
			for (int k = 0; k < anchorCount; k++) {
				if (missingSets[anchorTemp[k]].isSubsetOf(missingSets[x])) {
					dominated = true;
					break;
				}
			}
			if (!dominated) {
				anchorTemp[anchorCount++] = x;
			}
		}
		anchors = Arrays.copyOf(anchorTemp, anchorCount);
	}

	/**
	 * Compute the dominating anchor index dom(i) for every frequent item (Eq. 8):
	 * dom(i) = min{k : missing(a_k) &sube; missing(i)}. By Theorem 2 (anchor
	 * coverage), every item is dominated by at least one anchor.
	 */
	private void computeDom() {
		dom = new int[m];
		for (int i = 0; i < m; i++) {
			int found = anchors.length - 1;
			for (int k = 0; k < anchors.length; k++) {
				if (missingSets[anchors[k]].isSubsetOf(missingSets[i])) {
					found = k;
					break;
				}
			}
			dom[i] = found;
		}
	}

	/**
	 * Build the global item order used by the MFI search: items are sorted by
	 * (dom(i), |missing(i)|, itemId(i)), as described in Section 2.3. Uses a
	 * primitive comparator-based sort to avoid boxing.
	 */
	private void buildMfiOrder() {
		mfiOrder = new int[m];
		for (int i = 0; i < m; i++) {
			mfiOrder[i] = i;
		}
		primitiveSort(mfiOrder, (a, b) -> {
			if (dom[a] != dom[b]) {
				return Integer.compare(dom[a], dom[b]);
			}
			if (missingCard[a] != missingCard[b]) {
				return Integer.compare(missingCard[a], missingCard[b]);
			}
			return Integer.compare(a, b);
		});
	}

	/**
	 * Launch one DFS block per anchor a_k, restricted to items whose dominating
	 * anchor index is at least k, as described in Section 2.3.
	 * <p>
	 * All per-node scratch needed by {@link #mfiSearch} (the classified candidate
	 * list and the child missing-set M') is allocated once here, as one buffer per
	 * possible recursion depth (0..m), and reused for the entire search rather than
	 * being (re-)allocated at every node.
	 *
	 * @throws IOException if an error occurs while writing the output
	 */
	private void mineMFI() throws IOException {
		prefixBuffer = new int[m];
		inPrefixFlags = new boolean[m];
		int[] tailScratch = new int[m];

		mfiCandidateScratch = new int[m + 1][];
		mfiMissingScratch = new MissingSetVector[m + 1];
		for (int d = 0; d <= m; d++) {
			mfiCandidateScratch[d] = new int[m];
			mfiMissingScratch[d] = new MissingSetVector(nPrime);
		}

		for (int k = 0; k < anchors.length; k++) {
			int anchorItem = anchors[k];

			int count = 0;
			for (int idx = 0; idx < m; idx++) {
				int i = mfiOrder[idx];
				if (i != anchorItem && dom[i] >= k) {
					tailScratch[count++] = i;
				}
			}

			prefixBuffer[0] = anchorItem;
			MissingSetVector m0 = mfiMissingScratch[0];
			m0.copyFrom(missingSets[anchorItem]);

			mfiSearch(1, m0, tailScratch, 0, count, 0);

			MemoryLogger.getInstance().checkMemory();
		}
	}

	/**
	 * The core anchor-driven DFS procedure for MFI mining. A search node is
	 * represented by (prefix, M, tail) as described in Section 2.3.
	 * <p>
	 * The prefix is kept in the shared {@link #prefixBuffer}, addressed by
	 * {@code prefixLen} (classic backtracking: writes beyond the current frame's
	 * logical length are simply overwritten by later siblings / deeper frames, so
	 * no explicit "pop" or copying is needed). The tail is addressed as an (array,
	 * start, length) window rather than sliced with {@code Arrays.copyOfRange}, so
	 * descending into a child costs O(1) instead of O(remaining tail length).
	 * <p>
	 * Tail items are classified in a single O(tailLen * B) pass: an item x with
	 * missing(x) &sube; M is absorbed directly into the prefix buffer (this does
	 * not change M, so no branching is needed); an item x with |M &cup; missing(x)|
	 * &le; cap is a frequent extension candidate (Eq. 9) and becomes a child
	 * branch; all other items are pruned as infrequent, using the allocation-free,
	 * early-exiting {@link MissingSetVector#unionCardinalityCapped}. If no
	 * candidate remains, a leaf-level maximality test (Eq. 10) decides whether to
	 * output the prefix.
	 * <p>
	 * The classified candidate list and each child's missing-set M' are written
	 * into {@link #mfiCandidateScratch}[depth] /
	 * {@link #mfiMissingScratch}[depth+1], reused buffers indexed by the current
	 * recursion depth, instead of allocating a fresh {@code int[]} /
	 * {@link MissingSetVector} at every node and candidate edge.
	 *
	 * @param prefixLen the length of the valid prefix currently held in
	 *                  {@link #prefixBuffer}
	 * @param m_        the missing-set of the current prefix
	 * @param tail      the array backing the tail window
	 * @param tailStart the start offset of the tail window within {@code tail}
	 * @param tailLen   the length of the tail window
	 * @param depth     the current DFS recursion depth (0 at the anchor root call),
	 *                  used to index the per-depth scratch buffers
	 * @throws IOException if an error occurs while writing the output
	 */
	private void mfiSearch(int prefixLen, MissingSetVector m_, int[] tail, int tailStart, int tailLen, int depth)
			throws IOException {

		int[] candidates = mfiCandidateScratch[depth];
		int candidateCount = 0;
		int newPrefixLen = prefixLen;

		for (int idx = 0; idx < tailLen; idx++) {
			int x = tail[tailStart + idx];
			if (missingSets[x].isSubsetOf(m_)) {
				// Absorption (Eq. 9-related rule): missing(x) subset of M,
				// so adding x does not change the missing-set; append it
				// to the prefix directly, no branching required.
				prefixBuffer[newPrefixLen++] = x;
			} else if (m_.unionCardinalityCapped(missingSets[x], cap) >= 0) {
				// Frequency pruning test (Eq. 9), allocation-free and
				// early-exiting on the common (rejected) path.
				candidates[candidateCount++] = x;
			}
			// else: infrequent branch, pruned by Eq. (6)
		}

		if (candidateCount == 0) {
			// Leaf-level maximality test (Eq. 10)
			if (isMaximal(newPrefixLen, m_)) {
				writePattern(prefixBuffer, newPrefixLen, nPrime - m_.cardinality());
			}
			maybeCheckMemory();
			return;
		}

		int childDepth = depth + 1;
		MissingSetVector childM = mfiMissingScratch[childDepth];

		for (int j = 0; j < candidateCount; j++) {
			int x = candidates[j];

			// Eq. (9): M' = M union missing(x), materialized in-place into
			// a reused scratch vector instead of allocating a new one.
			childM.copyFrom(m_);
			childM.orInPlace(missingSets[x]);

			prefixBuffer[newPrefixLen] = x;

			mfiSearch(newPrefixLen + 1, childM, candidates, j + 1, candidateCount - j - 1, childDepth);
		}
	}

	/**
	 * Perform the leaf-level maximality test (Eq. 10): the prefix is maximal only
	 * if no frequent item outside the prefix can be added while remaining frequent.
	 * Uses the allocation-free, early-exiting
	 * {@link MissingSetVector#unionCardinalityCapped} test, and a reused
	 * membership-flag buffer instead of allocating {@code new boolean[m]} at every
	 * leaf.
	 *
	 * @param prefixLen the length of the candidate itemset held in
	 *                  {@link #prefixBuffer}
	 * @param m_        the missing-set of the candidate itemset
	 * @return true if the itemset is maximal
	 */
	private boolean isMaximal(int prefixLen, MissingSetVector m_) {
		for (int p = 0; p < prefixLen; p++) {
			inPrefixFlags[prefixBuffer[p]] = true;
		}
		boolean maximal = true;
		for (int y = 0; y < m; y++) {
			if (!inPrefixFlags[y] && m_.unionCardinalityCapped(missingSets[y], cap) >= 0) {
				maximal = false;
				break;
			}
		}
		for (int p = 0; p < prefixLen; p++) {
			inPrefixFlags[prefixBuffer[p]] = false;
		}
		return maximal;
	}

	/**
	 * Perform closure-space depth-first enumeration to mine all closed frequent
	 * itemsets, using the missing-set-based closure operator C(M) (Eq. 12) and the
	 * standard Close-by-One (CbO) canonicity rule.
	 * <p>
	 * The root closure C(missing(&empty;)) = C(&empty;) is the set of items present
	 * in every transaction. It is output only if non-empty, since the empty itemset
	 * itself is not a valid pattern.
	 * <p>
	 * As in MFI mining, the closure item list and the child missing-set M' needed
	 * at each recursion depth are drawn from reusable per-depth buffers allocated
	 * once up front.
	 *
	 * @throws IOException if an error occurs while writing the output
	 */
	private void mineCFI() throws IOException {
		inClosureFlags = new boolean[m];

		cfiClosureScratch = new int[m + 1][];
		cfiMissingScratch = new MissingSetVector[m + 1];
		for (int d = 0; d <= m; d++) {
			cfiClosureScratch[d] = new int[m];
			cfiMissingScratch[d] = new MissingSetVector(nPrime);
		}

		// missing(empty set) = empty set; a freshly allocated
		// MissingSetVector is already all-zero.
		MissingSetVector m0 = cfiMissingScratch[0];
		int baseLen = computeClosure(m0, cfiClosureScratch[0]);
		if (baseLen > 0) {
			writePattern(cfiClosureScratch[0], baseLen, nPrime);
		}
		cfiRecurse(cfiClosureScratch[0], baseLen, m0, 0, 0);

		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Compute the closure operator C(M) = { x in F : missing(x) is a subset of M }
	 * (Eq. 12), in O(m * B) time as stated in the paper's complexity analysis.
	 * Writes directly into the caller-supplied destination buffer (a per-depth
	 * scratch buffer) and returns the number of items written, so no array is
	 * allocated by this method itself.
	 *
	 * @param m_   the missing-set of the current node
	 * @param dest the destination buffer to receive the closure's internal item
	 *             indices, in ascending order (must have length &ge; m)
	 * @return the number of items written to {@code dest}
	 */
	private int computeClosure(MissingSetVector m_, int[] dest) {
		int count = 0;
		for (int y = 0; y < m; y++) {
			if (missingSets[y].isSubsetOf(m_)) {
				dest[count++] = y;
			}
		}
		return count;
	}

	/**
	 * Recursive Close-by-One (CbO) style depth-first enumeration in the closure
	 * space (Section 2.4). For each candidate extension item x_i with i &ge;
	 * startIdx, forms M' = M &cup; missing(x_i) (Eq. 13), prunes if infrequent
	 * (allocation-free, early-exiting test), computes the closed child C(M'), and
	 * applies the standard CbO canonicity rule under the fixed ascending
	 * internal-index order: the closure must not contain any item smaller than i
	 * that is not already part of the current (parent) closure, otherwise this
	 * branch is a duplicate of one reachable via a smaller extension index and is
	 * rejected.
	 * <p>
	 * Membership in the current prefix is tracked via the reused
	 * {@link #inClosureFlags} buffer (set on entry, cleared on exit), instead of
	 * allocating a fresh {@code boolean[m]} on every call. The child closure list
	 * and child missing-set are written into {@link #cfiClosureScratch}[depth+1] /
	 * {@link #cfiMissingScratch} [depth+1], reused across all candidates tried at
	 * this node (each candidate's recursive subtree fully completes before the next
	 * candidate overwrites these buffers).
	 *
	 * @param prefix    the closed itemset at the current node (only the first
	 *                  {@code prefixLen} entries are valid)
	 * @param prefixLen the number of valid entries in {@code prefix}
	 * @param m_        the missing-set of the current node
	 * @param startIdx  the item index from which extension items are tried
	 * @param depth     the current recursion depth, used to index the per-depth
	 *                  scratch buffers
	 * @throws IOException if an error occurs while writing the output
	 */
	private void cfiRecurse(int[] prefix, int prefixLen, MissingSetVector m_, int startIdx, int depth)
			throws IOException {
		for (int i = 0; i < prefixLen; i++) {
			inClosureFlags[prefix[i]] = true;
		}

		int childDepth = depth + 1;
		int[] childClosureBuf = cfiClosureScratch[childDepth];
		MissingSetVector childM = cfiMissingScratch[childDepth];

		for (int i = startIdx; i < m; i++) {
			if (inClosureFlags[i]) {
				continue;
			}

			int card = m_.unionCardinalityCapped(missingSets[i], cap);
			if (card < 0) {
				continue; // infrequent extension, pruned
			}

			// Eq. (13): M' = M union missing(x_i)
			childM.copyFrom(m_);
			childM.orInPlace(missingSets[i]);

			int closureLen = computeClosure(childM, childClosureBuf);

			// Standard CbO canonicity check
			boolean canonical = true;
			for (int y = 0; y < closureLen; y++) {
				int item = childClosureBuf[y];
				if (item < i && !inClosureFlags[item]) {
					canonical = false;
					break;
				}
			}
			if (!canonical) {
				continue;
			}

			writePattern(childClosureBuf, closureLen, nPrime - card);
			cfiRecurse(childClosureBuf, closureLen, childM, i + 1, childDepth);
		}

		for (int i = 0; i < prefixLen; i++) {
			inClosureFlags[prefix[i]] = false;
		}
		maybeCheckMemory();
	}

	/**
	 * Sample memory usage periodically (every {@code MEMORY_CHECK_MASK + 1} calls)
	 * instead of on every DFS node, since {@link MemoryLogger#checkMemory()} itself
	 * has a non-trivial cost and calling it at every one of the potentially
	 * exponentially many search nodes (Eq. 19, 21) would be wasteful.
	 */
	private void maybeCheckMemory() {
		if ((++opCounter & MEMORY_CHECK_MASK) == 0) {
			MemoryLogger.getInstance().checkMemory();
		}
	}

	/**
	 * Write a pattern (itemset) to the output file, sorted by ascending original
	 * item id. Since internal item indices are assigned in ascending item-id order
	 * during preprocessing (itemIds[i] is strictly increasing in i), sorting the
	 * internal indices themselves (a primitive {@code int[]} sort, no boxing) is
	 * equivalent to sorting by item id.
	 * <p>
	 * A reused sort buffer and a reused {@link StringBuilder} are used instead of
	 * allocating a fresh sorting array and a fresh {@code StringBuilder} for every
	 * pattern written.
	 *
	 * @param buffer  the buffer holding the internal item indices of the pattern
	 *                (only the first {@code length} entries are used; the buffer
	 *                may be shared/reused by the caller after this call returns)
	 * @param length  the number of valid entries in {@code buffer}
	 * @param support the support count of the pattern
	 * @throws IOException if an error occurs while writing to the file
	 */
	private void writePattern(int[] buffer, int length, int support) throws IOException {
		if (outputSortBuffer == null) {
			outputSortBuffer = new int[m];
		}
		System.arraycopy(buffer, 0, outputSortBuffer, 0, length);
		Arrays.sort(outputSortBuffer, 0, length);

		outputBuilder.setLength(0);
		for (int i = 0; i < length; i++) {
			outputBuilder.append(itemIds[outputSortBuffer[i]]);
			outputBuilder.append(' ');
		}
		outputBuilder.append("#SUP: ");
		outputBuilder.append(support);
		writer.write(outputBuilder.toString());
		writer.newLine();
		patternCount++;
	}

	/**
	 * Print statistics about the last execution of the algorithm.
	 */
	public void printStats() {
		double memory = MemoryLogger.getInstance().getMaxMemory();
		System.out.println("=============  MiSA-Miner - STATS =============");
		System.out.println(" Mode: "
				+ (mineClosedItemsets ? "Closed Frequent Itemsets (CFI)" : "Maximal Frequent Itemsets (MFI)"));
//		System.out.println(" Transactions (original / filtered N'): " + originalN + " / " + nPrime);
//		System.out.println(" Frequent items (m): " + m);
//		System.out.println(" Minsup (absolute): " + minSupportCount + "   cap: " + cap);
//		if (!mineClosedItemsets && anchors != null) {
//			System.out.println(" Number of anchors: " + anchors.length);
//		}
//		if (m > 0) {
//			System.out.println(" Bit-vector words per missing-set (B): " + missingSets[0].sizeInWords());
//		}
		System.out.println(" Patterns found: " + patternCount);
		System.out.println(" Total time: " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Max memory: " + String.format("%.2f", memory) + " MB");
		System.out.println("=================================================");
	}

	/**
	 * @return the number of patterns found by the last execution
	 */
	public long getPatternCount() {
		return patternCount;
	}

	/**
	 * @return the total execution time in milliseconds of the last run
	 */
	public long getTotalTime() {
		return endTimestamp - startTimestamp;
	}

	/**
	 * A functional interface for comparing two primitive {@code int} values, used
	 * instead of {@code Comparator<Integer>} so that sorting an {@code int[]} of
	 * internal item indices by an arbitrary key (Section 2.3: anchor ordering,
	 * global item ordering) never boxes the indices being compared.
	 */
	private interface IntComparator {
		int compare(int a, int b);
	}

	/**
	 * Sort the given {@code int[]} array in place according to the given
	 * comparator, without any autoboxing (unlike sorting an {@code Integer[]} with
	 * a {@code Comparator<Integer>}). All comparators used in this class break ties
	 * on the (unique) internal item index, so a non-stable sort is safe here.
	 *
	 * @param arr the array to sort in place
	 * @param cmp the comparator defining the sort order
	 */
	private static void primitiveSort(int[] arr, IntComparator cmp) {
		quickSort(arr, 0, arr.length - 1, cmp);
	}

	private static void quickSort(int[] arr, int lo, int hi, IntComparator cmp) {
		while (lo < hi) {
			if (hi - lo < 12) {
				insertionSort(arr, lo, hi, cmp);
				return;
			}
			int mid = lo + ((hi - lo) >>> 1);
			// Median-of-three pivot selection.
			if (cmp.compare(arr[mid], arr[lo]) < 0) {
				swap(arr, mid, lo);
			}
			if (cmp.compare(arr[hi], arr[lo]) < 0) {
				swap(arr, hi, lo);
			}
			if (cmp.compare(arr[hi], arr[mid]) < 0) {
				swap(arr, hi, mid);
			}
			int pivot = arr[mid];
			int i = lo;
			int j = hi;
			while (i <= j) {
				while (cmp.compare(arr[i], pivot) < 0) {
					i++;
				}
				while (cmp.compare(arr[j], pivot) > 0) {
					j--;
				}
				if (i <= j) {
					swap(arr, i, j);
					i++;
					j--;
				}
			}
			// Recurse into the smaller partition, loop for the larger one
			// (keeps recursion depth O(log n)).
			if (j - lo < hi - i) {
				quickSort(arr, lo, j, cmp);
				lo = i;
			} else {
				quickSort(arr, i, hi, cmp);
				hi = j;
			}
		}
	}

	private static void insertionSort(int[] arr, int lo, int hi, IntComparator cmp) {
		for (int i = lo + 1; i <= hi; i++) {
			int val = arr[i];
			int j = i - 1;
			while (j >= lo && cmp.compare(arr[j], val) > 0) {
				arr[j + 1] = arr[j];
				j--;
			}
			arr[j + 1] = val;
		}
	}

	private static void swap(int[] arr, int i, int j) {
		int tmp = arr[i];
		arr[i] = arr[j];
		arr[j] = tmp;
	}

	/**
	 * A minimal open-addressing (linear probing) {@code int -> int} hash map, used
	 * during preprocessing to count item occurrences and to map item ids to
	 * internal indices, without the autoboxing overhead of
	 * {@code HashMap<Integer, Integer>}. Supports only {@code put},
	 * {@code increment}, and {@code getOrDefault}, which is all that is needed
	 * here.
	 */
	private static final class IntIntMap {
		private int[] keys;
		private int[] values;
		private boolean[] used;
		private int mask;
		private int size;

		IntIntMap(int expectedEntries) {
			int capacity = 16;
			int minCapacity = Math.max(16, expectedEntries * 2);
			while (capacity < minCapacity) {
				capacity <<= 1;
			}
			keys = new int[capacity];
			values = new int[capacity];
			used = new boolean[capacity];
			mask = capacity - 1;
		}

		void increment(int key) {
			ensureCapacityForInsert();
			int idx = findSlot(key);
			if (used[idx]) {
				values[idx]++;
			} else {
				used[idx] = true;
				keys[idx] = key;
				values[idx] = 1;
				size++;
			}
		}

		void put(int key, int value) {
			ensureCapacityForInsert();
			int idx = findSlot(key);
			if (!used[idx]) {
				used[idx] = true;
				keys[idx] = key;
				size++;
			}
			values[idx] = value;
		}

		int getOrDefault(int key, int defaultValue) {
			int idx = findSlot(key);
			return used[idx] ? values[idx] : defaultValue;
		}

		private void ensureCapacityForInsert() {
			if ((size + 1) * 4 >= used.length * 3) { // load factor 0.75
				grow();
			}
		}

		private int findSlot(int key) {
			int idx = hash(key) & mask;
			while (used[idx] && keys[idx] != key) {
				idx = (idx + 1) & mask;
			}
			return idx;
		}

		private static int hash(int key) {
			int h = key * 0x9E3779B1;
			return h ^ (h >>> 16);
		}

		private void grow() {
			int[] oldKeys = keys;
			int[] oldValues = values;
			boolean[] oldUsed = used;
			int newCapacity = used.length << 1;
			keys = new int[newCapacity];
			values = new int[newCapacity];
			used = new boolean[newCapacity];
			mask = newCapacity - 1;
			size = 0;
			for (int i = 0; i < oldUsed.length; i++) {
				if (oldUsed[i]) {
					int idx = findSlot(oldKeys[i]);
					used[idx] = true;
					keys[idx] = oldKeys[i];
					values[idx] = oldValues[i];
					size++;
				}
			}
		}
	}
}