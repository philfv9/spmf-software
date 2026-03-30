package ca.pfv.spmf.algorithms.frequentpatterns.dbvminer;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.input.transaction_database_list_integers.TransactionDatabase;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemsets;
import ca.pfv.spmf.tools.MemoryLogger;
/* This file is copyright (c) 2008-2025 Philippe Fournier-Viger
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
*/

/**
 * DBV-Miner implementation with Long-based Bit Vectors
 * 
 * @author Philippe Fournier-Viger
 * @see DBV
 */
public class AlgoDBVMiner {

	/** Minimum support threshold (absolute count) */
	private int minsupRelative;
	
	/** Algorithm start timestamp */
	protected long startTimestamp;
	
	/** Algorithm end timestamp */
	protected long endTime;
	
	/** Result itemsets (when not writing to file) */
	protected Itemsets frequentItemsets;
	
	/** Output file writer */
	private BufferedWriter writer = null;
	
	/** Count of closed itemsets found */
	protected int itemsetCount;
	
	/** Whether to output transaction identifiers */
	private boolean showTransactionIdentifiers = false;
	
	/** Maximum itemset size to consider */
	private int maxItemsetSize = Integer.MAX_VALUE;
	
	/** Total number of transactions in the database */
	private int numTransactions;
	
	/** Set to track already output itemsets and avoid duplicates */
	private Set<ItemsetKey> outputItemsets;
	
	/** Reusable buffer for merging itemsets (reduces allocations) */
	private int[] mergeBuffer;
	
	/** Initial size of the merge buffer */
	private static final int INITIAL_MERGE_BUFFER_SIZE = 256;
	
	/** Number of bits per long - used for position calculations */
	private static final int BITS_PER_LONG = 64;

	/**
	 * Helper class to efficiently store and compare itemsets in a HashSet.
	 * Precomputes hash for O(1) lookup.
	 */
	private static class ItemsetKey {
		final int[] items;
		final int hash;

		ItemsetKey(int[] items) {
			this.items = items;
			this.hash = Arrays.hashCode(items);
		}

		@Override
		public int hashCode() {
			return hash;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (!(o instanceof ItemsetKey))
				return false;
			ItemsetKey other = (ItemsetKey) o;
			return this.hash == other.hash && Arrays.equals(items, other.items);
		}
	}

	/**
	 * Default constructor. Initializes the merge buffer.
	 */
	public AlgoDBVMiner() {
		mergeBuffer = new int[INITIAL_MERGE_BUFFER_SIZE];
	}

	/**
	 * Set whether transaction identifiers should be shown in output.
	 * 
	 * @param show true to show transaction IDs
	 */
	public void setShowTransactionIdentifiers(boolean show) {
		this.showTransactionIdentifiers = show;
	}

	/**
	 * Set the maximum pattern length to mine.
	 * 
	 * @param maxItemsetSize maximum number of items in an itemset
	 */
	public void setMaximumPatternLength(int maxItemsetSize) {
		this.maxItemsetSize = maxItemsetSize;
	}

	/**
	 * Run the DBV-Miner algorithm to find all closed frequent itemsets.
	 * 
	 * @param output   Output file path (null to keep results in memory)
	 * @param database The transaction database
	 * @param minsupp  Minimum support threshold (0-1)
	 * @return The closed frequent itemsets (null if writing to file)
	 * @throws IOException If output file cannot be written
	 */
	public Itemsets runAlgorithm(String output, TransactionDatabase database, double minsupp) throws IOException {

		// Validate minimum support parameter
		if (minsupp <= 0 || minsupp > 1) {
			throw new IllegalArgumentException("Minimum support must be in range (0, 1]");
		}

		// Initialize memory tracking and counters
		MemoryLogger.getInstance().reset();
		this.numTransactions = database.size();
		itemsetCount = 0;
		outputItemsets = new HashSet<>();
		startTimestamp = System.currentTimeMillis();
		this.minsupRelative = (int) Math.ceil(minsupp * database.size());

		// Calculate number of longs needed for full bit vector
		// Each long holds 64 transaction bits
		int numLongs = (numTransactions + BITS_PER_LONG - 1) / BITS_PER_LONG;

		try {
			// Initialize output mechanism
			if (output == null) {
				writer = null;
				frequentItemsets = new Itemsets("FREQUENT CLOSED ITEMSETS");
			} else {
				frequentItemsets = null;
				writer = new BufferedWriter(new FileWriter(output));
			}

			// STEP 1: L = { i_DBV(i) | i in I AND σ(i) ≥ minSup }
			// Build initial DBV for each frequent single item
			Map<Integer, DBV> mapItemDBV = new HashMap<>();
			calculateDBVForSingleItems(database, mapItemDBV, numLongs);

			// Create level-1 nodes for all frequent items
			List<DBVNode> L = new ArrayList<>();
			for (Map.Entry<Integer, DBV> entry : mapItemDBV.entrySet()) {
				DBV dbv = entry.getValue();
				if (dbv.support >= minsupRelative && maxItemsetSize >= 1) {
					L.add(new DBVNode(new int[] { entry.getKey() }, dbv));
				}
			}

			// No frequent items - early termination
			if (L.isEmpty()) {
				endTime = System.currentTimeMillis();
				return frequentItemsets;
			}

			// STEP 2: Sort items in L increasingly by their supports, and subsume them
			// together
			L.sort((o1, o2) -> {
				int cmp = Integer.compare(o1.dbv.support, o2.dbv.support);
				return cmp != 0 ? cmp : Integer.compare(o1.itemset[0], o2.itemset[0]);
			});

			// Apply subsumption to compute closures and remove duplicates
			L = applySubsumption(L);

			// STEP 3: DBV EXTEND(L, minSup)
			// Pass L as the level-1 nodes for closure computation (per Step 9: parent
			// chain)
			if (!L.isEmpty()) {
				dbvExtend(L, L);
			}

			MemoryLogger.getInstance().checkMemory();

		} finally {
			if (writer != null) {
				writer.close();
			}
		}

		endTime = System.currentTimeMillis();
		return frequentItemsets;
	}

	/**
	 * Step 2: "subsume them together"
	 * 
	 * For each node X, if DBV(X) ⊆ DBV(Y), add Y's items to X's closure. If DBV(X)
	 * = DBV(Y) and Y comes after X, delete Y.
	 * 
	 * This implements the closure computation for a set of nodes.
	 * 
	 * @param L List of nodes to apply subsumption to
	 * @return New list with subsumption applied
	 */
	private List<DBVNode> applySubsumption(List<DBVNode> L) {
		if (L.size() <= 1)
			return L;

		// Create working copies to avoid modifying input
		List<DBVNode> workingList = new ArrayList<>(L.size());
		for (DBVNode node : L) {
			workingList.add(new DBVNode(Arrays.copyOf(node.itemset, node.itemset.length), node.dbv));
		}

		// Track which nodes are deleted due to equal DBVs
		boolean[] deleted = new boolean[workingList.size()];

		// For each node X, check against all other nodes Y
		for (int i = 0; i < workingList.size(); i++) {
			if (deleted[i])
				continue;
			DBVNode nodeX = workingList.get(i);

			for (int j = 0; j < workingList.size(); j++) {
				if (i == j || deleted[j])
					continue;
				DBVNode nodeY = workingList.get(j);

				// If DBV(X) ⊆ DBV(Y), Y appears in all transactions of X
				// Therefore Y's items belong in X's closure
				if (nodeX.dbv.isSubsetOf(nodeY.dbv)) {
					nodeX.itemset = mergeItemsetsSorted(nodeX.itemset, nodeY.itemset);

					// If equal DBVs and Y comes after X, delete Y (keep X as representative)
					if (j > i && nodeX.dbv.equalDBV(nodeY.dbv)) {
						deleted[j] = true;
					}
				}
			}
		}

		// Build result list excluding deleted nodes
		List<DBVNode> result = new ArrayList<>();
		for (int i = 0; i < workingList.size(); i++) {
			if (!deleted[i]) {
				result.add(workingList.get(i));
			}
		}
		return result;
	}

	/**
	 * DBV EXTEND(L, minSup)
	 * 
	 * Recursively extends itemsets by combining nodes and computing closures.
	 * 
	 * @param Lr          Current level nodes to process
	 * @param level1Nodes The level-1 nodes (for Step 9: checking parent chain up to
	 *                    level 1)
	 * @throws IOException If output cannot be written
	 */
	private void dbvExtend(List<DBVNode> Lr, List<DBVNode> level1Nodes) throws IOException {

		// Step 4: FOR ALL X_DBV(X) in L
		for (int i = 0; i < Lr.size(); i++) {
			DBVNode nodeX = Lr.get(i);

			// Skip if already exceeds max size
			if (nodeX.itemset.length > maxItemsetSize)
				continue;

			// Step 5: Create the new set Li by joining X with each node Y following X
			List<DBVNode> Li = new ArrayList<>();

			for (int j = i + 1; j < Lr.size(); j++) {
				DBVNode nodeY = Lr.get(j);

				// Condition: Y NOT SUBSET OF X
				// Skip if Y's items are already contained in X's itemset
				if (isSubsetItemset(nodeY.itemset, nodeX.itemset)) {
					continue;
				}

				// DBV(XY) = DBV(X) ∩ DBV(Y)
				// Compute intersection with early termination if below minSup
				DBV dbvXY = computeDBVIntersection(nodeX.dbv, nodeY.dbv, minsupRelative);
				if (dbvXY == null)
					continue;

				// If DBV(XY) = DBV(X), no new closed itemset
				// Y is already in X's closure conceptually
				if (dbvXY.equalDBV(nodeX.dbv)) {
					continue;
				}

				// Start with XY = X ∪ Y
				int[] itemsetXY = mergeItemsetsSorted(nodeX.itemset, nodeY.itemset);

				// Step 9: Compute closure by checking successive nodes of X and parent(X)
				// Since parent chain goes to level-1, we check:
				// 1. Successive nodes in current level (after X)
				// 2. ALL level-1 nodes (represents the parent chain to root)
				boolean skip = false;

				// Check successive nodes in current level (after i, including j)
				for (int k = i + 1; k < Lr.size(); k++) {
					if (k == j)
						continue; // Already merged with Y
					DBVNode nodeZ = Lr.get(k);

					// If DBV(XY) ⊆ DBV(Z), Z appears in all transactions of XY
					if (dbvXY.isSubsetOf(nodeZ.dbv)) {
						// Z appears in all transactions of XY - add to closure
						itemsetXY = mergeItemsetsSorted(itemsetXY, nodeZ.itemset);

						// If same DBV and Z comes before j (in the j-loop sense for duplicates)
						// This handles: "if XY is not subsumed by any successive node"
						if (k < j && dbvXY.equalDBV(nodeZ.dbv)) {
							skip = true;
							break;
						}
					}
				}

				if (skip)
					continue;

				// Check level-1 nodes (Step 9: "parent(X)" chain up to level 1)
				// This is critical: even if a level-1 node Z is not in the current recursion,
				// if DBV(XY) ⊆ DBV(Z), then Z's items must be in XY's closure
				for (DBVNode level1Node : level1Nodes) {
					if (dbvXY.isSubsetOf(level1Node.dbv)) {
						// Add level-1 node's items to closure
						itemsetXY = mergeItemsetsSorted(itemsetXY, level1Node.itemset);

						// If same DBV as a level-1 node, this closed itemset
						// was already generated at level 1
						if (dbvXY.equalDBV(level1Node.dbv)) {
							skip = true;
							break;
						}
					}
				}

				if (skip)
					continue;

				// Skip if closure equals parent (no new closed itemset)
				if (Arrays.equals(itemsetXY, nodeX.itemset)) {
					continue;
				}

				// Skip if exceeds maximum itemset size
				if (itemsetXY.length > maxItemsetSize)
					continue;

				// Steps 6-8: Check existing children for subsumption
				boolean addedToExisting = false;
				for (DBVNode childZ : Li) {
					// If Z is subsumed by XY (DBV(Z) ⊆ DBV(XY))
					if (childZ.dbv.isSubsetOf(dbvXY)) {
						// Step 7: Replace Z by Z ∪ XY
						childZ.itemset = mergeItemsetsSorted(childZ.itemset, itemsetXY);

						// Step 8: Insert XY only if DBV(Z) ⊊ DBV(XY)
						if (childZ.dbv.equalDBV(dbvXY)) {
							addedToExisting = true;
						}
					}
				}

				// Step 9: Otherwise, insert XY into Li
				if (!addedToExisting) {
					Li.add(new DBVNode(itemsetXY, dbvXY));
				}
			}

			// Output X as closed itemset
			if (nodeX.itemset.length <= maxItemsetSize) {
				saveClosedItemset(nodeX.itemset, nodeX.dbv);
			}

			// Step 9: If |Li| ≥ 2, then call DBV EXTEND(Li, minSup)
			// (We call even if |Li| = 1 to output that single closed itemset)
			if (!Li.isEmpty()) {
				// Sort according to support (as per Step 5/8)
				Li.sort((o1, o2) -> {
					int cmp = Integer.compare(o1.dbv.support, o2.dbv.support);
					return cmp != 0 ? cmp : compareItemsets(o1.itemset, o2.itemset);
				});

				// Apply subsumption within Li (Step 2 applied at each level)
				Li = applySubsumption(Li);

				// Remove any child with same DBV as parent
				Li.removeIf(child -> child.dbv.equalDBV(nodeX.dbv));

				// Deduplicate by DBV - merge itemsets with same transaction set
				Li = deduplicateByDBV(Li);

				if (!Li.isEmpty()) {
					// Pass level1Nodes down for parent chain checking
					dbvExtend(Li, level1Nodes);
				}
			}
		}

		// Check memory usage periodically
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Remove duplicate nodes with same DBV. When two nodes have identical
	 * transaction sets, merge their itemsets.
	 * 
	 * @param nodes List of nodes to deduplicate
	 * @return Deduplicated list
	 */
	private List<DBVNode> deduplicateByDBV(List<DBVNode> nodes) {
		List<DBVNode> result = new ArrayList<>();
		for (DBVNode node : nodes) {
			boolean isDuplicate = false;
			for (DBVNode existing : result) {
				if (node.dbv.equalDBV(existing.dbv)) {
					// Merge items into existing node
					existing.itemset = mergeItemsetsSorted(existing.itemset, node.itemset);
					isDuplicate = true;
					break;
				}
			}
			if (!isDuplicate) {
				result.add(node);
			}
		}
		return result;
	}

	/**
	 * Check if itemset a is a subset of itemset b. Both itemsets must be sorted.
	 * 
	 * @param a First itemset (potential subset)
	 * @param b Second itemset (potential superset)
	 * @return true if a ⊆ b
	 */
	private boolean isSubsetItemset(int[] a, int[] b) {
		if (a.length > b.length)
			return false;
		int j = 0;
		for (int item : a) {
			while (j < b.length && b[j] < item)
				j++;
			if (j >= b.length || b[j] != item)
				return false;
			j++;
		}
		return true;
	}

	/**
	 * Calculate DBV (bit vector) for each single item in the database.
	 * 
	 * OPTIMIZATION: Uses long arrays (64 bits each) instead of byte arrays for more
	 * efficient bit operations.
	 * 
	 * @param database   The transaction database
	 * @param mapItemDBV Output map from item to its DBV
	 * @param numLongs   Number of longs needed for full bit vector
	 */
	private void calculateDBVForSingleItems(TransactionDatabase database, Map<Integer, DBV> mapItemDBV, int numLongs) {
		// First pass: count support for each item
		Map<Integer, Integer> itemSupports = new HashMap<>();
		for (List<Integer> transaction : database.getTransactions()) {
			for (Integer item : transaction) {
				itemSupports.merge(item, 1, Integer::sum);
			}
		}

		// Create bit vectors only for frequent items (optimization)
		Map<Integer, long[]> tempBitVectors = new HashMap<>();
		for (Map.Entry<Integer, Integer> entry : itemSupports.entrySet()) {
			if (entry.getValue() >= minsupRelative) {
				tempBitVectors.put(entry.getKey(), new long[numLongs]);
			}
		}

		// Second pass: set bits for each transaction
		// Using long (64-bit) for efficient bit operations
		List<List<Integer>> transactions = database.getTransactions();
		for (int tid = 0; tid < transactions.size(); tid++) {
			// Calculate which long and which bit within that long
			int longIndex = tid / BITS_PER_LONG;
			long mask = 1L << (tid % BITS_PER_LONG);

			for (Integer item : transactions.get(tid)) {
				long[] bitVector = tempBitVectors.get(item);
				if (bitVector != null) {
					bitVector[longIndex] |= mask;
				}
			}
		}

		// Compress each bit vector to DBV format
		for (Map.Entry<Integer, long[]> entry : tempBitVectors.entrySet()) {
			mapItemDBV.put(entry.getKey(), compressToDBV(entry.getValue()));
		}
	}

	/**
	 * Compress a full bit vector to DBV format by trimming leading/trailing zeros.
	 * 
	 * OPTIMIZATION: Uses long arrays for 8x faster processing.
	 * 
	 * @param fullVector Full bit vector (may have leading/trailing zeros)
	 * @return Compressed DBV
	 */
	private DBV compressToDBV(long[] fullVector) {
		// Find first non-zero long (skip leading zeros)
		int start = 0;
		while (start < fullVector.length && fullVector[start] == 0)
			start++;

		// Empty vector case
		if (start == fullVector.length)
			return new DBV(0, new long[0], 0);

		// Find last non-zero long (skip trailing zeros)
		int end = fullVector.length - 1;
		while (end >= 0 && fullVector[end] == 0)
			end--;

		// Extract the non-zero portion
		int length = end - start + 1;
		long[] compressed = new long[length];
		System.arraycopy(fullVector, start, compressed, 0, length);

		// Count total bits set (support) using hardware-accelerated Long.bitCount
		int support = 0;
		for (long l : compressed) {
			support += Long.bitCount(l);
		}

		return new DBV(start, compressed, support);
	}

	/**
	 * Compute the intersection of two DBVs: DBV(X) ∩ DBV(Y).
	 * 
	 * OPTIMIZATION: Uses long operations to process 64 bits at once, with early
	 * termination if support drops below minimum.
	 * 
	 * @param dbv1       First DBV
	 * @param dbv2       Second DBV
	 * @param minSupport Minimum support threshold for early termination
	 * @return Intersection DBV, or null if below minimum support
	 */
	private DBV computeDBVIntersection(DBV dbv1, DBV dbv2, int minSupport) {
		// Handle empty cases
		if (dbv1.isEmpty() || dbv2.isEmpty())
			return null;

		// Quick rejection based on support upper bound
		if (minSupport > 0 && Math.min(dbv1.support, dbv2.support) < minSupport)
			return null;

		// Calculate overlapping region (in long positions)
		int pos = Math.max(dbv1.pos, dbv2.pos);
		int endPos = Math.min(dbv1.pos + dbv1.getLength(), dbv2.pos + dbv2.getLength());
		int length = endPos - pos;

		// No overlap
		if (length <= 0)
			return null;

		// Calculate offsets into each DBV's array
		int offset1 = pos - dbv1.pos;
		int offset2 = pos - dbv2.pos;

		// Perform intersection using 64-bit AND operations
		long[] resultVector = new long[length];
		int supportSoFar = 0;
		int firstNonZero = -1;
		int lastNonZero = -1;

		for (int k = 0; k < length; k++) {
			// AND operation processes 64 bits at once
			long result = dbv1.getLong(offset1 + k) & dbv2.getLong(offset2 + k);
			resultVector[k] = result;

			if (result != 0) {
				// Hardware-accelerated bit counting
				supportSoFar += Long.bitCount(result);
				if (firstNonZero == -1)
					firstNonZero = k;
				lastNonZero = k;
			}

			// Early termination: maximum possible support if all remaining longs are full
			if (minSupport > 0 && (supportSoFar + (length - k - 1) * BITS_PER_LONG < minSupport)) {
				return null;
			}
		}

		// Check final support threshold
		if (supportSoFar < minSupport || firstNonZero == -1)
			return null;

		// Trim leading/trailing zeros from result
		int actualLen = lastNonZero - firstNonZero + 1;
		if (actualLen != length) {
			long[] trimmed = new long[actualLen];
			System.arraycopy(resultVector, firstNonZero, trimmed, 0, actualLen);
			return new DBV(pos + firstNonZero, trimmed, supportSoFar);
		}
		return new DBV(pos, resultVector, supportSoFar);
	}

	/**
	 * Merge two sorted itemsets into a new sorted itemset. Uses a reusable buffer
	 * to reduce memory allocations.
	 * 
	 * @param a First sorted itemset
	 * @param b Second sorted itemset
	 * @return Merged sorted itemset (union)
	 */
	private int[] mergeItemsetsSorted(int[] a, int[] b) {
		int requiredSize = a.length + b.length;

		// Expand buffer if needed
		if (mergeBuffer.length < requiredSize) {
			mergeBuffer = new int[Math.max(requiredSize, mergeBuffer.length * 2)];
		}

		// Standard merge of two sorted arrays
		int i = 0, j = 0, k = 0;
		while (i < a.length && j < b.length) {
			if (a[i] < b[j]) {
				mergeBuffer[k++] = a[i++];
			} else if (a[i] > b[j]) {
				mergeBuffer[k++] = b[j++];
			} else {
				// Equal elements - add only once
				mergeBuffer[k++] = a[i++];
				j++;
			}
		}

		// Copy remaining elements
		while (i < a.length)
			mergeBuffer[k++] = a[i++];
		while (j < b.length)
			mergeBuffer[k++] = b[j++];

		// Create result array of exact size
		int[] result = new int[k];
		System.arraycopy(mergeBuffer, 0, result, 0, k);
		return result;
	}

	/**
	 * Compare two itemsets lexicographically.
	 * 
	 * @param a First itemset
	 * @param b Second itemset
	 * @return Negative if a < b, positive if a > b, zero if equal
	 */
	private int compareItemsets(int[] a, int[] b) {
		int len = Math.min(a.length, b.length);
		for (int i = 0; i < len; i++) {
			if (a[i] != b[i])
				return a[i] - b[i];
		}
		return a.length - b.length;
	}

	/**
	 * Save a closed itemset to output (file or memory). Tracks already output
	 * itemsets to avoid duplicates.
	 * 
	 * @param itemset The closed itemset to save
	 * @param dbv     The DBV containing support and transaction information
	 * @throws IOException If writing to file fails
	 */
	private void saveClosedItemset(int[] itemset, DBV dbv) throws IOException {
		// Sort itemset for consistent output and duplicate detection
		int[] sortedItemset = Arrays.copyOf(itemset, itemset.length);
		Arrays.sort(sortedItemset);

		// Check for duplicates
		ItemsetKey key = new ItemsetKey(sortedItemset);
		if (outputItemsets.contains(key))
			return;
		outputItemsets.add(key);

		itemsetCount++;

		if (writer == null) {
			// Store in memory
			Itemset is = new Itemset(sortedItemset);
			is.setAbsoluteSupport(dbv.support);
			frequentItemsets.addItemset(is, sortedItemset.length);
		} else {
			// Write to file
			StringBuilder buffer = new StringBuilder();
			for (int i = 0; i < sortedItemset.length; i++) {
				if (i > 0)
					buffer.append(" ");
				buffer.append(sortedItemset[i]);
			}
			buffer.append(" #SUP: ").append(dbv.support);

			// Optionally output transaction identifiers
			if (showTransactionIdentifiers) {
				buffer.append(" #TID:");
				// Iterate through the bit vector to find set bits
				for (int longIdx = 0; longIdx < dbv.getLength(); longIdx++) {
					long bits = dbv.getLong(longIdx);
					if (bits != 0) {
						// Check each bit in this long
						for (int bit = 0; bit < BITS_PER_LONG; bit++) {
							if ((bits & (1L << bit)) != 0) {
								int tid = (dbv.pos + longIdx) * BITS_PER_LONG + bit;
								if (tid < numTransactions) {
									buffer.append(" ").append(tid);
								}
							}
						}
					}
				}
			}
			writer.write(buffer.toString());
			writer.newLine();
		}
	}

	/**
	 * Print algorithm statistics to console.
	 */
	public void printStats() {
		long executionTime = endTime > 0 ? (endTime - startTimestamp) : (System.currentTimeMillis() - startTimestamp);
		System.out.println("============= DBV-MINER 2.65 - STATS =========");
		System.out.println(" Closed itemsets count : " + itemsetCount);
		System.out.println(" Total time ~ " + executionTime + " ms");
		System.out.println(" Maximum memory usage : " + MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println("=============================================");
	}
}