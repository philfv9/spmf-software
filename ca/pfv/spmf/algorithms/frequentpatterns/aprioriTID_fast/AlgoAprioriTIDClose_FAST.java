package ca.pfv.spmf.algorithms.frequentpatterns.aprioriTID_fast;

/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
*
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see http://www.gnu.org/licenses/.
*/
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the AprioriTID algorithm transformed to mine
 * only frequent closed itemsets as proposed by Pasquier in Apriori Close (1999), rather than all
 * frequent itemsets. This is an optimized implementation using a vertical approach with hybrid tidset
 * representation: dense (long[] bitset) for high-support itemsets and sparse
 * (sorted int[] list) for low-support itemsets.
 * Besides, this version uses tidset-hash-based closure checking for improved performance.
 * <br/><br/>
 *
 * AprioriTID was originally proposed in:<br/><br/>
 *
 * Agrawal R, Srikant R. "Fast Algorithms for Mining Association Rules", VLDB.
 * Sep 12-15 1994, Chile, 487-99,<br/><br/>
 *
 * Modifying Apriori to mine closed itemsets was proposed in: <br/><br/>
 *
 * Pasquier, N., Bastide, Y., Taouil, R., & Lakhal, L. (1999).
 * Discovering frequent closed itemsets for association rules.
 * In Database Theory-ICDT'99 (pp. 398-416). Springer Berlin Heidelberg.<br/><br/>
 *
 * This implementation can save the result to a file.
 *
 * @see ItemsetWithTID
 * @see TidsetManager
 * @author Philippe Fournier-Viger
 */
public class AlgoAprioriTIDClose_FAST {

	/** the current level k */
	protected int k;

	/** the minimum support threshold (absolute) */
	protected int minSuppRelative;

	/** Maximum size of itemsets to discover */
	protected int maxItemsetSize = Integer.MAX_VALUE;

	/** start time of latest execution */
	protected long startTimestamp = 0;

	/** end time of latest execution */
	protected long endTimeStamp = 0;

	/** object to write the output file */
	protected BufferedWriter writer = null;

	/** the number of frequent closed itemsets found */
	protected int itemsetCount;

	/** the number of transactions */
	protected int tidcount = 0;

	/** if true, transaction identifiers of each pattern will be shown */
	protected boolean showTransactionIdentifiers = false;

	/** Manages tidset pooling, intersection, and conversion */
	protected final TidsetManager tidsetManager = new TidsetManager();

	/** Reusable StringBuilder for output */
	private final StringBuilder outputBuffer = new StringBuilder(512);

	/** Comparator for 1-itemsets (sorted by single item) */
	private static final Comparator<ItemsetWithTID> COMP_SINGLE_ITEM = new Comparator<ItemsetWithTID>() {
		public int compare(ItemsetWithTID o1, ItemsetWithTID o2) {
			return o1.items[0] - o2.items[0];
		}
	};

	/** Comparator for multi-item itemsets (lexicographic) */
	private static final Comparator<ItemsetWithTID> COMP_LEXICOGRAPHIC = new Comparator<ItemsetWithTID>() {
		public int compare(ItemsetWithTID a, ItemsetWithTID b) {
			int[] ia = a.items;
			int[] ib = b.items;
			int len = ia.length;
			for (int i = 0; i < len; i++) {
				if (ia[i] != ib[i])
					return ia[i] - ib[i];
			}
			return 0;
		}
	};

	/**
	 * Default constructor
	 */
	public AlgoAprioriTIDClose_FAST() {
	}

	/**
	 * Run the AprioriTID-Close algorithm.
	 *
	 * @param input  path to input file
	 * @param output path to output file
	 * @param minsup minimum support threshold as a fraction (e.g. 0.5)
	 * @throws NumberFormatException if the input contains non-integer tokens
	 * @throws IOException           if an error occurs reading/writing files
	 */
	public void runAlgorithm(String input, String output, double minsup)
			throws NumberFormatException, IOException {
		startTimestamp = System.currentTimeMillis();
		itemsetCount = 0;

		writer = new BufferedWriter(new FileWriter(output), 65536);

		// PHASE 1: Single-pass database scan
		List<ItemsetWithTID> level = scanDatabaseAndBuildLevel1(input, minsup);

		// Generate candidates with size k = 2, 3, ...
		k = 2;
		while (!level.isEmpty() && k <= maxItemsetSize) {
			MemoryLogger.getInstance().checkMemory();
			List<ItemsetWithTID> nextLevel = generateCandidateSizeK(level);

			// Check if itemsets of level k-1 are closed using tidset-hash approach
			checkIfItemsetsK_1AreClosed(level, nextLevel);

			tidsetManager.recycleTidsets(level);
			level = nextLevel;
			k++;
		}

		// Check closure for the last level (no next level to compare)
		if (!level.isEmpty()) {
			// All itemsets in the last level are closed (no frequent supersets exist)
			for (ItemsetWithTID itemset : level) {
				saveItemsetToFile(itemset);
			}
			tidsetManager.recycleTidsets(level);
		}

		writer.close();
		tidsetManager.clearPool();
		endTimeStamp = System.currentTimeMillis();
	}

	/**
	 * Scan the database, identify frequent 1-itemsets, and build level 1.
	 *
	 * @param input  path to input file
	 * @param minsup minimum support as a fraction
	 * @return sorted list of frequent 1-itemsets
	 * @throws IOException if an error occurs reading the file
	 */
	protected List<ItemsetWithTID> scanDatabaseAndBuildLevel1(String input, double minsup)
			throws IOException {
		tidcount = 0;
		
		// Create a map from item to list of TIDs
		Map<Integer, BitSet> mapItemTIDS = new HashMap<Integer, BitSet>(256);

		// Read input file, line by line
		BufferedReader reader = new BufferedReader(new FileReader(input), 65536);
		String line;
		while ((line = reader.readLine()) != null) {

			// If the line contains metadata or comments, skip it
			if (line.isEmpty() || line.charAt(0) == '#'
					|| line.charAt(0) == '%' || line.charAt(0) == '@') {
				continue;
			}

			// Fast integer parsing without String.split()
			int len = line.length();
			int number = 0;
			boolean hasNumber = false;
			for (int pos = 0; pos < len; pos++) {
				char c = line.charAt(pos);
				if (c >= '0' && c <= '9') {
					number = number * 10 + (c - '0');
					hasNumber = true;
				} else if (hasNumber) {
					BitSet tids = mapItemTIDS.get(number);
					if (tids == null) {
						tids = new BitSet();
						mapItemTIDS.put(number, tids);
					}
					tids.set(tidcount);
					number = 0;
					hasNumber = false;
				}
			}
			// We have read an item
			if (hasNumber) {
				// Get its list of TIDs
				BitSet tids = mapItemTIDS.get(number);
				
				// If there is none, create it
				if (tids == null) {
					tids = new BitSet();
					mapItemTIDS.put(number, tids);
				}
				// Add the tid of the current transaction
				tids.set(tidcount);
			}
			// Increase transaction count
			tidcount++;
		}
		// Close the file
		reader.close();

		MemoryLogger.getInstance().checkMemory();

		// Initialize tidset manager
		tidsetManager.init(tidcount);

		// Compute absolute minimum support
		this.minSuppRelative = (int) Math.ceil(minsup * tidcount);

		// Build level 1
		k = 1;
		List<ItemsetWithTID> level = new ArrayList<ItemsetWithTID>(mapItemTIDS.size());

		// For each single item
		Iterator<Entry<Integer, BitSet>> iterator = mapItemTIDS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, BitSet> entry = iterator.next();
			int cardinality = entry.getValue().cardinality();
			
			// If the item is frequent
			if (cardinality >= minSuppRelative && maxItemsetSize >= 1) {
				Integer item = entry.getKey();
				ItemsetWithTID ci;
				
				// Decide if we should use the bitset or int array representation of the
				// list of transaction ids.
				if (tidsetManager.shouldUseBitset(cardinality)) {
					long[] words = tidsetManager.bitsetToLongArray(entry.getValue());
					ci = new ItemsetWithTID(new int[] { item }, words, cardinality);
				} else {
					int[] list = tidsetManager.bitsetToSortedIntArray(entry.getValue(), cardinality);
					ci = new ItemsetWithTID(new int[] { item }, list);
				}
				// Add the item to the current level for processing by Apriori
				level.add(ci);
				// Don't save yet - wait for closure check
			}
			iterator.remove();
		}

		// Sort by item ID using static comparator
		Collections.sort(level, COMP_SINGLE_ITEM);

		return level;
	}

	/**
	 * Generate frequent itemsets of size k from frequent itemsets of size k-1.
	 *
	 * @param levelK_1 frequent itemsets of size k-1 (sorted lexicographically)
	 * @return frequent itemsets of size k (sorted lexicographically)
	 * @throws IOException if writing to output fails
	 */
	protected List<ItemsetWithTID> generateCandidateSizeK(List<ItemsetWithTID> levelK_1)
			throws IOException {
		int levelSize = levelK_1.size();
		List<ItemsetWithTID> candidates = new ArrayList<ItemsetWithTID>(levelSize);

		// if we are at level 2
		if (k == 2) {
			// ==== SPECIAL CASE: Generate level 2 by joining all pairs ====
			for (int i = 0; i < levelSize; i++) {
				
				// Get the first item
				ItemsetWithTID ci1 = levelK_1.get(i);
				int item1 = ci1.items[0];

				for (int j = i + 1; j < levelSize; j++) {
					// Get the second item
					ItemsetWithTID ci2 = levelK_1.get(j);

					// Intersect tidsets to get support
					int cardinality = tidsetManager.intersect(ci1, ci2, minSuppRelative);

					// Keep only the join of the two items if it is frequent
					if (cardinality >= minSuppRelative) {
						int[] newItems = new int[] { item1, ci2.items[0] };
						ItemsetWithTID candidate = tidsetManager.claimResultAsItemset(newItems, cardinality);
						candidates.add(candidate);
					}
				}
			}
		} else {
			// ==== GENERAL CASE: Join itemsets sharing (k-2)-prefix ====
			int prefixLen = levelK_1.get(0).items.length - 1;

			// Group itemsets by their (k-2)-prefix
			int groupStart = 0;
			while (groupStart < levelSize) {
				int[] groupPrefix = levelK_1.get(groupStart).items;

				// Find end of group with same prefix
				int groupEnd = groupStart + 1;
				while (groupEnd < levelSize) {
					int[] otherItems = levelK_1.get(groupEnd).items;
					boolean samePrefix = true;
					
					// Compare (k-2)-prefix
					for (int p = 0; p < prefixLen; p++) {
						if (groupPrefix[p] != otherItems[p]) {
							samePrefix = false;
							break;
						}
					}
					if (!samePrefix) {
						break;
					}
					groupEnd++;
				}

				// Join pairs within the same prefix group
				for (int i = groupStart; i < groupEnd; i++) {
					// Get an itemset
					ItemsetWithTID ci1 = levelK_1.get(i);
					int[] itemset1 = ci1.items;

					for (int j = i + 1; j < groupEnd; j++) {
						// Get another itemset
						ItemsetWithTID ci2 = levelK_1.get(j);
						
						// Intersect tidsets to get the support of the join of both itemsets
						int cardinality = tidsetManager.intersect(ci1, ci2, minSuppRelative);

						// Create candidate if the join is frequent
						if (cardinality >= minSuppRelative) {
							int newLen = itemset1.length + 1;
							int[] newItems = new int[newLen];
							// Copy prefix and first itemset's last item
							System.arraycopy(itemset1, 0, newItems, 0, itemset1.length);
							// Add second itemset's last item
							newItems[itemset1.length] = ci2.items[ci2.items.length - 1];

							// Keep the candidate
							ItemsetWithTID candidate = tidsetManager.claimResultAsItemset(newItems, cardinality);
							candidates.add(candidate);
						}
					}
				}

				// Move to next prefix group
				groupStart = groupEnd;
			}
		}

		// Sort candidates lexicographically
		if (!candidates.isEmpty()) {
			Collections.sort(candidates, COMP_LEXICOGRAPHIC);
		}

		return candidates;
	}

	// =====================================================================
	//  TIDSET HASHING — fast, O(array-length), no bit enumeration
	// =====================================================================

	/**
	 * Compute a hash of an itemset's tidset.
	 * 
	 * @param itemset the itemset whose tidset to hash
	 * @return a 64-bit hash value
	 */
	private long computeTidsetHash(ItemsetWithTID itemset) {
		long h = 1;
		
		if (itemset.tidWords != null) {
			// Hash the dense long[] representation
			long[] words = itemset.tidWords;
			for (int i = 0; i < words.length; i++) {
				h = h * 31 + words[i];
			}
		} else if (itemset.tidList != null) {
			// Hash the sparse int[] representation
			int[] list = itemset.tidList;
			for (int i = 0; i < list.length; i++) {
				h = h * 31 + list[i];
			}
		}
		return h;
	}
	
	/**
	 * Fast tidset equality check.
	 * Same-representation case: straight array comparison.
	 * Mixed case (one dense, one sparse): enumerate dense bits and
	 * compare — only reached on hash collision with mismatched
	 * representations, which is extremely rare.
	 *
	 * @param a first itemset
	 * @param b second itemset
	 * @return true if their tidsets are identical
	 */
	private boolean tidsetEquals(ItemsetWithTID a, ItemsetWithTID b) {
		// Quick support check
		if (a.support != b.support) return false;

		// Both dense: direct array comparison
		if (a.tidWords != null && b.tidWords != null) {
			return Arrays.equals(a.tidWords, b.tidWords);
		}
		// Both sparse: direct array comparison
		if (a.tidList != null && b.tidList != null) {
			return Arrays.equals(a.tidList, b.tidList);
		}
		
		// Mixed representation (rare case): enumerate and compare
		ItemsetWithTID dense  = (a.tidWords != null) ? a : b;
		ItemsetWithTID sparse = (a.tidWords != null) ? b : a;
		int[] list = sparse.tidList;
		long[] words = dense.tidWords;
		int idx = 0;
		
		// Extract TIDs from dense bitset and compare with sparse list
		for (int w = 0; w < words.length; w++) {
			long word = words[w];
			while (word != 0) {
				if (idx >= list.length) return false;
				int bit = Long.numberOfTrailingZeros(word);
				if (((w << 6) + bit) != list[idx]) return false;
				idx++;
				word &= word - 1;
			}
		}
		return idx == list.length;
	}

	// =====================================================================
	//  CLOSURE CHECKING
	// =====================================================================

	/**
	 * Check which level-(k-1) itemsets are closed, using tidset hashing.
	 * <p>
	 * Key principle: X (size k-1) is NOT closed iff some Y (size k) has
	 * the identical tidset and Y &sup; X.  Identical tidset implies identical
	 * support, so we combine support + tidsetHash into one lookup key.
	 * </p>
	 * <p>
	 * Checking one level ahead is sufficient: if any superset Z of any
	 * size has support(Z)=support(X), then every k-sized subset of Z
	 * containing X also has support=support(X) and is generated by
	 * Apriori's join (all its (k-1)-subsets are frequent by downward
	 * closure).
	 * </p>
	 * <p>
	 * Optimization strategy:
	 * <ol>
	 *   <li>Compute a fast tidset hash for each level-k itemset.
	 *       Two itemsets can only have equal tidsets if they have the
	 *       same hash (barring rare collisions).</li>
	 *   <li>Group level-k itemsets by a composite key of (support, tidsetHash).
	 *       This is far more discriminating than support alone.</li>
	 *   <li>For each level-(k-1) itemset, compute the same composite key and
	 *       look up the matching group.</li>
	 *   <li>Within the (typically tiny) matching group, verify with
	 *       {@link #containsAll} and {@link #tidsetEquals} to handle hash
	 *       collisions.</li>
	 * </ol>
	 * </p>
	 *
	 * @param levelKm1 itemsets of size k-1
	 * @param levelK   itemsets of size k
	 * @throws IOException exception if error writing output file
	 */
	private void checkIfItemsetsK_1AreClosed(List<ItemsetWithTID> levelKm1,
			List<ItemsetWithTID> levelK) throws IOException {

		if (levelK.isEmpty()) {
			// No supersets exist; all k-1 itemsets are closed
			for (ItemsetWithTID itemset : levelKm1) {
				saveItemsetToFile(itemset);
			}
			return;
		}

		// Build hash map: compositeKey -> list of level-k itemsets
		int mapCap = Math.max(16, levelK.size() * 4 / 3 + 1);
		HashMap<Long, List<ItemsetWithTID>> map = new HashMap<Long, List<ItemsetWithTID>>(mapCap);

		// Group level-k itemsets by composite key (support + tidset hash)
		for (ItemsetWithTID itemsetK : levelK) {
			long key = makeCompositeKey(itemsetK);
			List<ItemsetWithTID> bucket = map.get(key);
			if (bucket == null) {
				bucket = new ArrayList<ItemsetWithTID>(2);
				map.put(key, bucket);
			}
			bucket.add(itemsetK);
		}

		// Check each level-(k-1) itemset for closure
		for (ItemsetWithTID itemset : levelKm1) {
			long key = makeCompositeKey(itemset);
			List<ItemsetWithTID> bucket = map.get(key);

			if (bucket == null) {
				// No level-k itemset shares the same tidset hash
				// This itemset is closed
				saveItemsetToFile(itemset);
				continue;
			}

			// Check if any itemset in bucket is a proper superset with same tidset
			boolean isClosed = true;
			for (int i = 0, sz = bucket.size(); i < sz; i++) {
				ItemsetWithTID candidateK = bucket.get(i);
				
				// Filter by support (quick check)
				if (candidateK.support != itemset.support) continue;
				
				// Check if candidateK contains all items of itemset
				if (!containsAll(candidateK, itemset)) continue;
				
				// Verify tidsets are identical (handles hash collisions)
				if (tidsetEquals(candidateK, itemset)) {
					// Found a proper superset with identical tidset
					// This itemset is NOT closed
					isClosed = false;
					break;
				}
			}

			if (isClosed) {
				saveItemsetToFile(itemset);
			}
		}

		// Clear map to help GC
		map.clear();
	}

	/**
	 * Composite key = mix of support and tidset hash.
	 * Keeps the hot path allocation-free (primitive long).
	 *
	 * @param itemset the itemset to compute the key for
	 * @return a 64-bit composite key combining support and tidset hash
	 */
	private long makeCompositeKey(ItemsetWithTID itemset) {
		long h = computeTidsetHash(itemset);
		// Mix support into hash for better distribution
		return h ^ (((long) itemset.support) * 0x9e3779b97f4a7c15L);
	}

	/**
	 * Check if itemsetK contains all items from itemsetKm1.
	 * Both itemsets are assumed to be sorted.
	 *
	 * @param itemsetK   the larger itemset (size k)
	 * @param itemsetKm1 the smaller itemset (size k-1)
	 * @return true if itemsetK contains all items from itemsetKm1
	 */
	private boolean containsAll(ItemsetWithTID itemsetK, ItemsetWithTID itemsetKm1) {
		int[] itemsK = itemsetK.items;
		int[] itemsKm1 = itemsetKm1.items;
		int j = 0;
		
		// Linear scan through both sorted arrays
		for (int i = 0; i < itemsK.length && j < itemsKm1.length; i++) {
			if (itemsK[i] == itemsKm1[j]) {
				j++; // Match found, advance to next item in Km1
			} else if (itemsK[i] > itemsKm1[j]) {
				// itemsKm1[j] not in itemsK, containment fails
				return false;
			}
		}
		// All items matched if j reached end
		return j == itemsKm1.length;
	}


	// =====================================================================
	//  OUTPUT
	// =====================================================================

	/**
	 * Save an itemset to the output file.
	 *
	 * @param ci the itemset to save
	 * @throws IOException if writing fails
	 */
	/**
	 * Save an itemset to the output file.
	 *
	 * @param ci the itemset to save
	 * @throws IOException if writing fails
	 */
	protected void saveItemsetToFile(ItemsetWithTID ci) throws IOException {
		outputBuffer.setLength(0);

		// Write items
		int[] items = ci.items;
		for (int i = 0; i < items.length; i++) {
			if (i > 0) outputBuffer.append(' ');
			outputBuffer.append(items[i]);
		}

		// Write support
		outputBuffer.append(" #SUP: ").append(ci.support);

		// Optionally write transaction IDs
		if (showTransactionIdentifiers) {
			outputBuffer.append(" #TID:");
			if (ci.tidWords != null) {
				// Dense representation: extract TIDs from bitset
				long[] words = ci.tidWords;
				for (int w = 0; w < words.length; w++) {
					long word = words[w];
					while (word != 0) {
						int bit = Long.numberOfTrailingZeros(word);
						outputBuffer.append(' ').append((w << 6) + bit);
						word &= word - 1;
					}
				}
			} else {
				// Sparse representation
				int[] list = ci.tidList;
				for (int i = 0; i < list.length; i++) {
					outputBuffer.append(' ').append(list[i]);
				}
			}
		}

		// Write to file
		writer.write(outputBuffer.toString());
		writer.newLine();
		itemsetCount++;
	}


	/**
	 * Set whether transaction identifiers should be shown for each pattern found.
	 *
	 * @param show true to show TIDs, false otherwise
	 */
	public void setShowTransactionIdentifiers(boolean show) {
		this.showTransactionIdentifiers = show;
	}

	/**
	 * Print statistics about the algorithm execution.
	 */
	public void printStats() {
		System.out.println("============= APRIORI-CLOSE TID FAST v2.66 - STATS =============");
		System.out.println(" Transactions count from database : " + tidcount);
		System.out.println(" Frequent closed itemsets count : " + itemsetCount);
		System.out.println(" Maximum memory usage : "
				+ MemoryLogger.getInstance().getMaxMemory() + " mb");
		System.out.println(" Total time ~ " + (endTimeStamp - startTimestamp) + " ms");
		System.out.println("===================================================");
	}

	/**
	 * Set the maximum pattern length.
	 *
	 * @param length the maximum length
	 */
	public void setMaximumPatternLength(int length) {
		this.maxItemsetSize = length;
	}
}