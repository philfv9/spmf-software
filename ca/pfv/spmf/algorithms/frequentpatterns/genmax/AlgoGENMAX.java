package ca.pfv.spmf.algorithms.frequentpatterns.genmax;

/* This file is copyright (c) 2008-2024 Philippe Fournier-Viger
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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.algorithms.ItemNameConverter;
import ca.pfv.spmf.datastructures.bitsetpool.BitSetPool;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * Implementation of the GENMAX algorithm for mining maximal frequent itemsets
 * with optimized item renaming for canonical ordering.
 * 
 * <p>
 * This implementation does not keep track of local maximal itemsets. It instead
 * use a global structure (globalMFIs).
 * This implementation uses two representations for transaction sets:
 * </p>
 * <ul>
 * <li><b>Tidset</b>: Set of transaction IDs where an itemset appears</li>
 * <li><b>Diffset</b>: Set of transaction IDs representing the difference
 * between parent and child tidsets. D(PY) = T(P) - T(PY)</li>
 * </ul>
 * 
 * <p>
 * At root level (empty prefix), tidsets are used. After the first extension,
 * diffsets are used for more efficient computation.
 * </p>
 *
 * <p>
 * Reference: Gouda, K., & Zaki, M. J. (2005). GenMax: An efficient algorithm
 * for mining maximal frequent itemsets. Data Mining and Knowledge Discovery.
 * </p>
 *
 * @author Philippe Fournier-Viger
 * @see MaxItemsetStorage
 * @see BitSetPool
 * @see FrequentItem
 * @see ItemsetWithSupport
 */
public class AlgoGENMAX {

	/** Algorithm start timestamp */
	private long startTimestamp;

	/** Algorithm end timestamp */
	private long endTimestamp;

	/** Total number of transactions in database */
	private int transactionCount;

	/** Minimum support threshold (absolute count) */
	protected int minsupRelative;

	/** Number of frequent items after filtering */
	private int numFrequentItems;

	/** Tidsets for each item (indexed by renamed item ID) - IMMUTABLE after init */
	private BitSet[] itemTidsets;

	/** Support values for items (indexed by renamed item ID) */
	private int[] itemSupports;

	/** Converter for renaming items to canonical order */
	private ItemNameConverter nameConverter;

	/** Global maximal frequent itemset storage for pruning and output */
	private MaxItemsetStorage globalMFI;

	/** Pool for reusable BitSets to reduce memory allocation */
	private BitSetPool bitSetPool;

	/**
	 * Runs the GENMAX algorithm on the specified input file.
	 * 
	 * @param input   path to transaction database file
	 * @param output  path to output file for maximal itemsets
	 * @param minsupp minimum support threshold (relative, 0.0-1.0)
	 * @throws IOException if file I/O error occurs
	 */
	public void runAlgorithm(String input, String output, double minsupp) throws IOException {
		startTimestamp = System.currentTimeMillis();
		globalMFI = new MaxItemsetStorage();
		MemoryLogger.getInstance().reset();

		// First pass: scan database to build tidsets
		Map<Integer, BitSet> tempTidsets = scanDatabase(input);

		// Find frequent items and rename for canonical ordering
		findFrequentItemsAndRename(tempTidsets, minsupp);

		// Initialize BitSet pool
		bitSetPool = new BitSetPool(transactionCount);

		// Run main algorithm if there are frequent items
		if (numFrequentItems > 0) {
			// At root level, tail contains all frequent items with their TIDSETS
			int[] tailItems = new int[numFrequentItems];
			BitSet[] tailTidsets = new BitSet[numFrequentItems];
			for (int i = 0; i < numFrequentItems; i++) {
				tailItems[i] = i;
				// CRITICAL: Copy tidsets to avoid aliasing with immutable itemTidsets
				tailTidsets[i] = bitSetPool.acquire();
				tailTidsets[i].or(itemTidsets[i]);
			}

			// Create tidset for empty prefix (all transactions)
			BitSet allTransactions = bitSetPool.acquire();
			allTransactions.set(0, transactionCount);

			// Start with empty prefix, using TIDSET mode
			genmaxWithTidsets(new int[0], transactionCount, allTransactions, tailItems, tailTidsets);

			// Release root level BitSets
			bitSetPool.release(allTransactions);
			for (int i = 0; i < numFrequentItems; i++) {
				bitSetPool.release(tailTidsets[i]);
			}
		}

		writeOutput(output);
		MemoryLogger.getInstance().checkMemory();
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * Scans the database to build tidsets for all items.
	 */
	private Map<Integer, BitSet> scanDatabase(String input) throws IOException {
		transactionCount = 0;
		Map<Integer, BitSet> tidsets = new HashMap<>();

		try (BufferedReader reader = new BufferedReader(new FileReader(input))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
					continue;
				}

				// Optimized parsing without creating array via split
				int start = 0;
				int len = line.length();
				while (start < len) {
					int end = line.indexOf(' ', start);
					if (end == -1) {
						end = len;
					}
					int item = Integer.parseInt(line.substring(start, end));
					tidsets.computeIfAbsent(item, k -> new BitSet()).set(transactionCount);
					start = end + 1;
				}
				transactionCount++;
			}
		}
		return tidsets;
	}

	/**
	 * Finds frequent items, orders them by support (ascending), and renames them.
	 */
	private void findFrequentItemsAndRename(Map<Integer, BitSet> tempTidsets, double minsupp) {
		minsupRelative = Math.max(1, (int) Math.ceil(minsupp * transactionCount));

		List<int[]> freqList = new ArrayList<>(tempTidsets.size());
		for (Map.Entry<Integer, BitSet> e : tempTidsets.entrySet()) {
			int sup = e.getValue().cardinality();
			if (sup >= minsupRelative) {
				freqList.add(new int[] { e.getKey(), sup });
			}
		}

		freqList.sort((a, b) -> a[1] != b[1] ? a[1] - b[1] : a[0] - b[0]);

		numFrequentItems = freqList.size();
		nameConverter = new ItemNameConverter(numFrequentItems, 0);
		itemTidsets = new BitSet[numFrequentItems];
		itemSupports = new int[numFrequentItems];

		for (int i = 0; i < numFrequentItems; i++) {
			int[] entry = freqList.get(i);
			int oldItem = entry[0];
			int newItem = nameConverter.assignNewName(oldItem);
			itemTidsets[newItem] = tempTidsets.get(oldItem);
			itemSupports[newItem] = entry[1];
		}
	}

	// ========================================================================
	// TIDSET MODE: Used at root level where tail contains tidsets
	// ========================================================================

	/**
	 * GENMAX procedure using TIDSET representation (root level only).
	 *
	 * @param prefix       current itemset prefix (empty at root)
	 * @param prefixSup    support of prefix
	 * @param prefixTidset tidset of prefix (owned by caller, do not modify)
	 * @param tail         candidate items for extension
	 * @param tailTidsets  tidsets for each tail item (owned by caller)
	 */
	private void genmaxWithTidsets(int[] prefix, int prefixSup, BitSet prefixTidset, int[] tail, BitSet[] tailTidsets) {

	    int tailLen = tail.length;
	    
	    // Base case: no more items to extend
	    if (tailLen == 0) {
	        if (prefix.length > 0) {
	            addMaximal(prefix, prefixSup);
	        }
	        return;
	    }

	    // Compute union of prefix and tail
	    int[] unionTail = mergePresorted(prefix, tail);
	    
	    // LMFI pruning: check if unionTail is subset of existing maximal
	    if (globalMFI.hasSuperset(unionTail)) {
	        return;
	    }

	    // FHUT pruning: compute support of prefix ∪ tail
	    BitSet hutTidset = bitSetPool.acquire();
	    hutTidset.or(prefixTidset);
	    for (int i = 0; i < tailLen; i++) {
	        hutTidset.and(tailTidsets[i]);
	    }
	    int fhutSup = hutTidset.cardinality();

	    if (fhutSup >= minsupRelative) {
	        // Head Union Tail is frequent - compute its closure
	        int[] closure = computeClosureForTail(unionTail, fhutSup, hutTidset, tail);
	        bitSetPool.release(hutTidset);

	        // If closure equals or extends unionTail, we found a maximal
	        if (arraysEqual(closure, unionTail) || closure.length > unionTail.length) {
	            addMaximal(closure, fhutSup);
	            return;
	        }
	    } else {
	        bitSetPool.release(hutTidset);
	    }

	    // Recursive exploration: extend prefix with each tail item
	    int prefixLen = prefix.length;
	    
	    for (int i = 0; i < tailLen; i++) {
	        int item = tail[i];
	        BitSet itemTidset = tailTidsets[i];

	        // Compute tidset for newPrefix = prefix ∪ {item}
	        BitSet newPrefixTidset = bitSetPool.acquire();
	        newPrefixTidset.or(prefixTidset);
	        newPrefixTidset.and(itemTidset);
	        int newSup = newPrefixTidset.cardinality();
	        
	        if (newSup < minsupRelative) {
	            bitSetPool.release(newPrefixTidset);
	            continue;
	        }

	        // Build new prefix
	        int[] newPrefix = new int[prefixLen + 1];
	        System.arraycopy(prefix, 0, newPrefix, 0, prefixLen);
	        newPrefix[prefixLen] = item;
	        int newPrefixLen = prefixLen + 1;

	        // Partition remaining tail items into closure items and remaining candidates
	        int maxRemaining = tailLen - i - 1;
	        int[] closureItems = new int[newPrefixLen + maxRemaining];
	        int closureCount = newPrefixLen;
	        System.arraycopy(newPrefix, 0, closureItems, 0, newPrefixLen);

	        int[] remainingIndices = new int[maxRemaining];
	        BitSet[] precomputedDiffsets = new BitSet[maxRemaining];
	        int remainingCount = 0;

	        for (int j = i + 1; j < tailLen; j++) {
	            // Compute intersection to get support
	            BitSet tempIntersect = bitSetPool.acquire();
	            tempIntersect.or(newPrefixTidset);
	            tempIntersect.and(tailTidsets[j]);
	            int tailItemSup = tempIntersect.cardinality();

	            if (tailItemSup == newSup) {
	                // Item has same support as newPrefix - add to closure
	                closureItems[closureCount++] = tail[j];
	                bitSetPool.release(tempIntersect);
	            } else if (tailItemSup >= minsupRelative) {
	                // Item is frequent but not in closure - add to remaining
	                // Precompute diffset to avoid redundant computation later
	                BitSet diffset = bitSetPool.acquire();
	                diffset.or(newPrefixTidset);
	                diffset.andNot(tailTidsets[j]);
	                
	                remainingIndices[remainingCount] = j;
	                precomputedDiffsets[remainingCount] = diffset;
	                remainingCount++;
	                bitSetPool.release(tempIntersect);
	            } else {
	                // Item is infrequent - discard
	                bitSetPool.release(tempIntersect);
	            }
	        }

	        // Build closed itemset and sort it
	        int[] closed = Arrays.copyOf(closureItems, closureCount);
	        Arrays.sort(closed);

	        // If no remaining items, closed is maximal
	        if (remainingCount == 0) {
	            addMaximal(closed, newSup);
	            bitSetPool.release(newPrefixTidset);
	            continue;
	        }

	        // Build new tail with precomputed diffsets
	        int[] newTail = new int[remainingCount];
	        BitSet[] newTailDiffsets = new BitSet[remainingCount];
	        
	        for (int k = 0; k < remainingCount; k++) {
	            int tailIndex = remainingIndices[k];
	            newTail[k] = tail[tailIndex];
	            newTailDiffsets[k] = precomputedDiffsets[k]; // Use precomputed diffset
	        }

	        // Recursive call switches to DIFFSET mode
	        genmaxWithDiffsets(closed, newSup, newPrefixTidset, newTail, newTailDiffsets);

	        // Release BitSets
	        bitSetPool.release(newPrefixTidset);
	        for (int k = 0; k < remainingCount; k++) {
	            bitSetPool.release(newTailDiffsets[k]);
	        }
	    }
	}



	// ========================================================================
	// DIFFSET MODE: Used after root level for efficient support computation
	// ========================================================================

	/**
	 * GENMAX procedure using DIFFSET representation.
	 * 
	 * <p>
	 * Diffset D(P, i) = T(P) - T(P ∪ {i}) = transactions where P appears but i
	 * doesn't.
	 * </p>
	 * <p>
	 * Key formulas:
	 * <ul>
	 * <li>support(P ∪ {i}) = support(P) - |D(P, i)|</li>
	 * <li>D(P ∪ {i}, j) = D(P, j) - D(P, i) (classical diffset transformation)</li>
	 * </ul>
	 * </p>
	 *
	 * @param prefix       current itemset prefix
	 * @param prefixSup    support of prefix
	 * @param prefixTidset tidset of prefix (owned by caller)
	 * @param tail         candidate items for extension
	 * @param tailDiffsets diffsets for each tail item relative to prefix (owned by
	 *                     caller)
	 */
	private void genmaxWithDiffsets(int[] prefix, int prefixSup, BitSet prefixTidset, int[] tail,
			BitSet[] tailDiffsets) {

		int[] unionTail = mergePresorted(prefix, tail);

		// LMFI pruning
		if (globalMFI.hasSuperset(unionTail)) {
			return;
		}

		int tailLen = tail.length;

		// Base case
		if (tailLen == 0) {
			if (prefix.length > 0) {
				addMaximal(prefix, prefixSup);
			}
			return;
		}

		// FHUT pruning: compute tidset of prefix ∪ tail
		// T(P ∪ tail) = T(P) - (D(P,t1) ∪ D(P,t2) ∪ ... ∪ D(P,tn))
		BitSet unionDiffsets = bitSetPool.acquire();
		for (int i = 0; i < tailLen; i++) {
			unionDiffsets.or(tailDiffsets[i]);
		}
		BitSet hutTidset = bitSetPool.acquire();
		hutTidset.or(prefixTidset);
		hutTidset.andNot(unionDiffsets);
		bitSetPool.release(unionDiffsets);

		int fhutSup = hutTidset.cardinality();

		if (fhutSup >= minsupRelative) {
			int[] closure = computeClosureForTail(unionTail, fhutSup, hutTidset, tail);
			bitSetPool.release(hutTidset);

			if (arraysEqual(closure, unionTail) || closure.length > unionTail.length) {
				addMaximal(closure, fhutSup);
				return;
			}
		} else {
			bitSetPool.release(hutTidset);
		}

		// Extend prefix with each tail item
		int prefixLen = prefix.length;
		for (int i = 0; i < tailLen; i++) {
			int item = tail[i];
			BitSet itemDiffset = tailDiffsets[i];

			// Compute new prefix tidset: T(P ∪ {i}) = T(P) - D(P, i)
			BitSet newPrefixTidset = bitSetPool.acquire();
			newPrefixTidset.or(prefixTidset);
			newPrefixTidset.andNot(itemDiffset);
			int newSup = newPrefixTidset.cardinality();

			if (newSup < minsupRelative) {
				bitSetPool.release(newPrefixTidset);
				continue;
			}

			int[] newPrefix = append(prefix, item);
			int newPrefixLen = prefixLen + 1;

			// Partition remaining tail items using primitive arrays
			int maxRemaining = tailLen - i - 1;
			int[] closureItems = new int[newPrefixLen + maxRemaining];
			int closureCount = 0;
			int[] remainingIndices = new int[maxRemaining];
			int remainingCount = 0;

			for (int p = 0; p < newPrefixLen; p++) {
				closureItems[closureCount++] = newPrefix[p];
			}

			for (int j = i + 1; j < tailLen; j++) {
				// Classical diffset transformation: D(P ∪ {i}, j) = D(P, j) - D(P, i)
				BitSet newDiffset = bitSetPool.acquire();
				newDiffset.or(tailDiffsets[j]);
				newDiffset.andNot(itemDiffset);
				int newDiffsetSize = newDiffset.cardinality();
				int tailItemSup = newSup - newDiffsetSize;
				bitSetPool.release(newDiffset);

				if (tailItemSup == newSup) {
					// Empty diffset means tail[j] is in closure
					closureItems[closureCount++] = tail[j];
				} else if (tailItemSup >= minsupRelative) {
					remainingIndices[remainingCount++] = j;
				}
			}

			int[] closed = Arrays.copyOf(closureItems, closureCount);
			Arrays.sort(closed);

			if (remainingCount == 0) {
				addMaximal(closed, newSup);
				bitSetPool.release(newPrefixTidset);
				continue;
			}

			// Build new tail with updated diffsets using classical transformation
			int[] newTail = new int[remainingCount];
			BitSet[] newTailDiffsets = new BitSet[remainingCount];

			for (int k = 0; k < remainingCount; k++) {
				int tailIndex = remainingIndices[k];
				// D(P ∪ {i}, j) = D(P, j) - D(P, i)
				BitSet newDiffset = bitSetPool.acquire();
				newDiffset.or(tailDiffsets[tailIndex]);
				newDiffset.andNot(itemDiffset);

				newTail[k] = tail[tailIndex];
				newTailDiffsets[k] = newDiffset;
			}

			// Recursive call stays in DIFFSET mode
			genmaxWithDiffsets(closed, newSup, newPrefixTidset, newTail, newTailDiffsets);

			// Release BitSets
			bitSetPool.release(newPrefixTidset);
			for (int k = 0; k < remainingCount; k++) {
				bitSetPool.release(newTailDiffsets[k]);
			}
		}
	}

	// ========================================================================
	// CLOSURE COMPUTATION
	// ========================================================================

	/**
	 * Computes closure by checking items beyond the current tail's max item.
	 * 
	 * <p>
	 * Only checks items with higher canonical order than those already in unionTail
	 * to avoid redundant work. Uses the actual tidset for accurate intersection.
	 * </p>
	 * 
	 * @param unionTail current prefix ∪ tail itemset
	 * @param support   support of unionTail
	 * @param tidset    tidset of unionTail
	 * @param tail      current tail items (to find max item)
	 * @return closed itemset
	 */
	private int[] computeClosureForTail(int[] unionTail, int support, BitSet tidset, int[] tail) {
		// Find maximum item in tail (not prefix, since we only extend with tail items)
		int maxTailItem = -1;
		int tailLen = tail.length;
		for (int i = 0; i < tailLen; i++) {
			if (tail[i] > maxTailItem) {
				maxTailItem = tail[i];
			}
		}

		// Maximum closure size = unionTail + items from maxTailItem+1 to numFrequentItems-1
		int maxAdditionalItems = numFrequentItems - maxTailItem - 1;
		int[] closureItems = new int[unionTail.length + maxAdditionalItems];
		int closureCount = 0;

		// Add all items from unionTail
		int unionTailLen = unionTail.length;
		for (int i = 0; i < unionTailLen; i++) {
			closureItems[closureCount++] = unionTail[i];
		}

		// Check items with HIGHER canonical order than max tail item
		BitSet tempIntersect = bitSetPool.acquire();
		for (int i = maxTailItem + 1; i < numFrequentItems; i++) {
			tempIntersect.clear();
			tempIntersect.or(tidset);
			tempIntersect.and(itemTidsets[i]);

			if (tempIntersect.cardinality() == support) {
				closureItems[closureCount++] = i;
			}
		}
		bitSetPool.release(tempIntersect);

		int[] result = Arrays.copyOf(closureItems, closureCount);
		Arrays.sort(result);
		return result;
	}

	// ========================================================================
	// UTILITY METHODS
	// ========================================================================

	/**
	 * Adds an itemset to the global MFI if it is maximal.
	 */
	private void addMaximal(int[] itemset, int support) {
		if (itemset.length > 0) {
			globalMFI.addIfMaximal(itemset, support);
		}
	}

	/**
	 * Merges two pre-sorted arrays into a single sorted array.
	 * CRITICAL FIX: Always returns a new array to prevent aliasing bugs.
	 */
	private int[] mergePresorted(int[] a, int[] b) {
		int aLen = a.length;
		int bLen = b.length;
		
		// FIX: Always copy to prevent aliasing
		if (aLen == 0) {
			return Arrays.copyOf(b, bLen);
		}
		if (bLen == 0) {
			return Arrays.copyOf(a, aLen);
		}

		int[] result = new int[aLen + bLen];
		int i = 0, j = 0, k = 0;

		while (i < aLen && j < bLen) {
			if (a[i] <= b[j]) {
				result[k++] = a[i++];
			} else {
				result[k++] = b[j++];
			}
		}

		while (i < aLen) {
			result[k++] = a[i++];
		}
		while (j < bLen) {
			result[k++] = b[j++];
		}

		return result;
	}

	/**
	 * Checks if two sorted arrays are equal.
	 */
	private boolean arraysEqual(int[] a, int[] b) {
		int len = a.length;
		if (len != b.length) {
			return false;
		}
		for (int i = 0; i < len; i++) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Appends a value to an array.
	 */
	private int[] append(int[] arr, int val) {
		int len = arr.length;
		int[] r = Arrays.copyOf(arr, len + 1);
		r[len] = val;
		return r;
	}

	/**
	 * Writes maximal itemsets to output file.
	 */
	private void writeOutput(String output) throws IOException {
		try (BufferedWriter w = new BufferedWriter(new FileWriter(output))) {
			List<int[]> itemsets = globalMFI.getItemsets();
			List<Integer> supports = globalMFI.getSupports();
			int size = itemsets.size();
			
			for (int i = 0; i < size; i++) {
				int[] itemset = itemsets.get(i);
				int itemsetLen = itemset.length;
				StringBuilder sb = new StringBuilder(itemsetLen * 4 + 16);

				for (int j = 0; j < itemsetLen; j++) {
					if (j > 0) {
						sb.append(' ');
					}
					sb.append(nameConverter.toOldName(itemset[j]));
				}
				sb.append(" #SUP: ").append(supports.get(i));

				w.write(sb.toString());
				w.newLine();
			}
		}
	}

	/**
	 * Gets the number of maximal itemsets found.
	 */
	public int getMaximalCount() {
		return globalMFI.size();
	}

	/**
	 * Gets total execution time in milliseconds.
	 */
	public long getTotalTime() {
		return endTimestamp - startTimestamp;
	}

	/**
	 * Prints algorithm statistics to standard output.
	 */
	public void printStats() {
		double memory = MemoryLogger.getInstance().getMaxMemory();
		System.out.println("============= GENMAX 2.64b - STATS =============");
//		System.out.println(" Transactions: " + transactionCount);
//		System.out.println(" Min support: " + minsupRelative);
//		System.out.println(" Frequent items: " + numFrequentItems);
		System.out.println(" Maximal itemsets: " + globalMFI.size());
		System.out.println(" Time: " + getTotalTime() + " ms");
		System.out.println(" Memory: " + String.format("%.2f", memory) + " MB");
		System.out.println("==========================================");
	}
}