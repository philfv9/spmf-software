package ca.pfv.spmf.algorithms.frequentpatterns.NFWI;

/* This file is copyright (c) 2008-2024 Philippe Fournier-Viger
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
* Do not remove copyright or license information from this file.
*/

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the "NFWCI" algorithm for Frequent Weighted
 * Closed Itemsets Mining as described in the paper:
 *
 * Bui, H., Vo, B., Nguyen-Hoang, T.-A., Yun, U. (2021). Mining frequent
 * weighted closed itemsets using the WN-list structure and an early pruning
 * strategy. Applied Intelligence, 51, 1439-1459.
 *
 * The algorithm extends NFWI by adding closedness checking via the WN-list
 * ancestral operation.
 *
 * @author Philippe Fournier-Viger
 */
public class AlgoNFWCI extends AlgoNFWIBase {

	/** the number of frequent weighted closed itemsets found */
	public int fwciCount = 0;

	/**
	 * Registry of verified closed itemsets grouped by rounded weighted support for
	 * fast subsumption checks
	 */
	private Map<Long, List<ClosedItemset>> closedItemsetsByWs;

	/**
	 * Tolerance used for weighted-support equality comparisons. Two ws values are
	 * considered equal if they differ by less than this amount. Adjusted to absorb
	 * floating-point summation drift across distinct branch paths.
	 */
	private static final double WS_EPS = 1e-7;

	/**
	 * Internal structure to store verified closed itemsets for fast global
	 * subsumption checking. The itemset array is stored pre-sorted to avoid
	 * repeated sort overhead during isSubset checks.
	 */
	private static class ClosedItemset {
		int[] sortedItemset; // sorted
		double ws;

		ClosedItemset(int[] sortedItemset, double ws) {
			this.sortedItemset = sortedItemset;
			this.ws = ws;
		}
	}

	/**
	 * Wraps an itemset along with its corresponding WN-list and calculated weighted
	 * support. Used as a node in the equivalence-class search tree.
	 */
	private static class ItemsetWNList {

		/** The integer array representing the itemset */
		int[] itemset;

		/** The corresponding WN-list of codes for this itemset */
		List<WNNode> wnList;

		/** The calculated weighted support ratio */
		double weightedSupport;
		/**
		 * The item that distinguishes this node inside its equivalence class
		 */
		int distinguishingItem;

		/**
		 * Constructor to initialize the equivalence-class node wrapper.
		 * 
		 * @param itemset            the primitive array representing the itemset
		 * @param wnList             the WN-list structural codes
		 * @param weightedSupport    the normalized support weight
		 * @param distinguishingItem the item serving as the expansion seed
		 */
		ItemsetWNList(int[] itemset, List<WNNode> wnList, double weightedSupport, int distinguishingItem) {
			this.itemset = itemset;
			this.wnList = wnList;
			this.weightedSupport = weightedSupport;
			this.distinguishingItem = distinguishingItem;
		}
	}

	/**
	 * Default constructor.
	 */
	public AlgoNFWCI() {
	}

	/**
	 * Run the NFWCI algorithm.
	 *
	 * @param inputDatabase   the path to the input transaction database file
	 * @param inputWeights    the path to the input weight table file
	 * @param output          the path to the output file
	 * @param minWeightedSupp the minimum weighted support threshold (0.0 to 1.0)
	 * @throws IOException if an error occurs while reading or writing files
	 */
	public void runAlgorithm(String inputDatabase, String inputWeights, String output, double minWeightedSupp)
			throws IOException {
		MemoryLogger.getInstance().reset();
		startTimestamp = System.currentTimeMillis();

		this.minWeightedSupport = minWeightedSupp;
		this.fwciCount = 0;
		this.candidateCount = 0;
		this.codeCounter = 1;
		this.closedItemsetsByWs = new HashMap<>();

		writer = new BufferedWriter(new FileWriter(output), 65536);

		// Steps 1-5: shared pipeline (database loading, WN-tree construction, code
		// assignment)
		PipelineResult pipeline = runPipeline(inputDatabase, inputWeights, false);

		if (pipeline == null) {
			writer.close();
			endTimestamp = System.currentTimeMillis();
			return;
		}

		// Step 6: Build the level-1 equivalence class.
		// globalOrder has index 0 = highest ws.
		// The Find_FWCI procedure iterates i from |Is|-1 down to 0 (lowest ws
		// first) and j from i-1 down to 0 (items with higher ws).
		List<ItemsetWNList> level1 = new ArrayList<>();
		for (int idx = 0; idx < pipeline.globalOrder.size(); idx++) {
			int item = pipeline.globalOrder.get(idx);
			level1.add(new ItemsetWNList(new int[] { item }, pipeline.wnListArray[idx],
					pipeline.frequent1ItemsetsWS.get(item), item));
		}

		// Step 7: Recursively mine FWCIs using the Find_FWCI procedure
		findFWCI(level1);

		MemoryLogger.getInstance().checkMemory();
		writer.close();
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * Implements the Find_FWCI procedure from Algorithm 3 of the paper.
	 *
	 * @param is the current equivalence class, index 0 = highest ws
	 * @throws IOException if an error occurs while writing results
	 */
	private void findFWCI(List<ItemsetWNList> is) throws IOException {
		// Work on a mutable copy so we can remove elements during iteration
		List<ItemsetWNList> isMut = new ArrayList<>(is);
		// Reusable out-parameter for weight accumulation; avoids per-call array
		// allocation
		double[] weightOut = new double[1];

		int i = isMut.size() - 1;
		while (i >= 0) {
			ItemsetWNList nodeI = isMut.get(i);
			List<ItemsetWNList> inext = new ArrayList<>();

			int j = i - 1;
			while (j >= 0) {
				ItemsetWNList nodeJ = isMut.get(j);
				candidateCount++;

				// check WL(Xi) ⊳ WL(Xj).
				// This means:
				// WL(Xj) (higher ws, ancestor in tree) is the ancestor of
				// WL(Xi) (lower ws, descendant in tree).
				// Every code in WL(Xi) must have an ancestor in WL(Xj).
				if (isWNListAncestor(nodeJ.wnList, nodeI.wnList)) {
					// Theorem 4a: ws(XiXj) = ws(Xi) because t(Xi) ⊆ t(Xj).
					// Xi alone is not closed (it has proper superset XiXj
					// with same ws). Action: Xi absorbs Xj's item, WL(Xi)
					// is replaced by WL_Intersection(WL(Xi), WL(Xj)).
					// Per the proof, the intersection equals WL(Xi) in weight
					// (every Xi node is under some Xj ancestor), but we must
					// do the intersection to get the correct structural codes
					// for further pairwise comparisons.
					weightOut[0] = 0.0;
					List<WNNode> intersected = intersectWNLists(nodeI.wnList, nodeJ.wnList, weightOut);
					nodeI.wnList = intersected.isEmpty() ? nodeI.wnList : intersected;

					// ws(Xi) stays the same (ws(XiXj) = ws(Xi))
					// Xi absorbs Xj's absolute distinguishing item instead of
					// relying on the last index position.
					int distItem = nodeJ.distinguishingItem;
					nodeI.itemset = appendItem(nodeI.itemset, distItem);

					// All existing candidates in Inext also absorb Xj's distinguishing item
					for (ItemsetWNList xk : inext) {
						xk.itemset = appendItem(xk.itemset, distItem);
					}

					// if ws(Xi) = ws(Xj), remove Xj
					if (Math.abs(nodeI.weightedSupport - nodeJ.weightedSupport) < WS_EPS) {
						isMut.remove(j);
						i--;
					}

					j--;

				} else {
					// Normal intersection branch (Algorithm 3, lines 20-23)
					weightOut[0] = 0.0;
					List<WNNode> intersected = intersectWNLists(nodeI.wnList, nodeJ.wnList, weightOut);

					if (!intersected.isEmpty()) {
						// Use the weight sum already accumulated inside intersectWNLists —
						// no second traversal over the result list is needed.
						double ws = weightOut[0] / totalTransactionWeight;

						// If the weighted support is enoough
						if (ws >= minWeightedSupport) {
							int distItem = nodeJ.distinguishingItem;
							int[] newItemset = appendItem(nodeI.itemset, distItem);

							// Pre-pruning check
							// Ensure candidate is not already subsumed globally
							// before generating branches.
							if (!isSubsumed(newItemset, ws)) {
								inext.add(new ItemsetWNList(newItemset, intersected, ws, distItem));
							}
						}
					}
					j--;
				}
			}

			// Recurse depth-first
			if (!inext.isEmpty()) {
				findFWCI(inext);
			}

			// Strict post-pruning validation: verify node is absolutely closed
			// globally before outputting.
			if (!isSubsumed(nodeI.itemset, nodeI.weightedSupport)) {
				writeOut(nodeI.itemset, nodeI.weightedSupport);
				addClosedItemset(nodeI.itemset, nodeI.weightedSupport);
			}

			i--;
		}

		// Check memory usage for statistics
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Checks if the given candidate itemset is subsumed (is a subset) of any
	 * globally registered closed itemset having identical weighted support.
	 * 
	 * @param itemset the primitive array candidate itemset
	 * @param ws      the corresponding calculated weighted support
	 * @return true if it is safely subsumed and not closed
	 */
	private boolean isSubsumed(int[] itemset, double ws) {
		long key = Math.round(ws * 1000000.0);
		List<ClosedItemset> list = closedItemsetsByWs.get(key);
		if (list != null) {
			for (ClosedItemset c : list) {
				if (Math.abs(c.ws - ws) < WS_EPS && isSubset(itemset, c.sortedItemset)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Registers a strictly verified closed itemset into the tracking data
	 * structures. The itemset is sorted once at registration time so that repeated
	 * isSubset checks can use binary search without re-sorting on every call.
	 * 
	 * @param itemset the closed itemset array
	 * @param ws      the calculated weighted support
	 */
	private void addClosedItemset(int[] itemset, double ws) {
		// Clone and sort once here; isSubset then uses binary search directly
		// on the stored sorted array without further allocation.
		int[] sorted = itemset.clone();
		Arrays.sort(sorted);
		long key = Math.round(ws * 1000000.0);
		closedItemsetsByWs.computeIfAbsent(key, k -> new ArrayList<>()).add(new ClosedItemset(sorted, ws));
	}

	/**
	 * Determines if a candidate itemset acts as a subset sequence against a target
	 * array. The superset array is pre-sorted at registration time (in
	 * addClosedItemset), so this method can use Arrays.binarySearch for O(log n)
	 * lookup per subset item directly on the passed array without any cloning or
	 * sorting here.
	 * 
	 * @param subset         the smaller itemset array
	 * @param sortedSuperset the pre-sorted larger array context to check within
	 * @return true if explicitly covered
	 */
	private static boolean isSubset(int[] subset, int[] sortedSuperset) {
		for (int subItem : subset) {
			if (Arrays.binarySearch(sortedSuperset, subItem) < 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Tests whether WL(wl1) is an ancestor of WL(wl2).
	 * 
	 * Both lists are pre-sorted by preCode. We exploit this order with a forward
	 * scan: for each code in wl2 we scan wl1 only up to the point where wl1's
	 * preCode exceeds wl2's preCode, then stop early. This reduces the worst-case
	 * cost compared to a fully nested scan.
	 *
	 * @param wl1 the candidate ancestor WN-list
	 * @param wl2 the candidate descendant WN-list
	 * @return {@code true} if WL(wl1) ⊳ WL(wl2)
	 */
	private static boolean isWNListAncestor(List<WNNode> wl1, List<WNNode> wl2) {
		if (wl1.isEmpty() || wl2.isEmpty()) {
			return false;
		}

		int size1 = wl1.size();

		// For each code c2 in wl2 (sorted by preCode ascending) we need to
		// find some c1 in wl1 such that c1.pre <= c2.pre AND c1.post >= c2.post.
		// Because wl1 is also sorted by preCode ascending we can use a pointer
		// that only moves forward: once c1.pre > c2.pre we know no later
		// c1 can be an ancestor either, so we look at the window of c1 nodes
		// whose preCode <= c2.preCode and check postCode among them.
		for (WNNode c2 : wl2) {
			boolean found = false;
			// Scan wl1 for any node that structurally contains c2
			for (int p = 0; p < size1; p++) {
				WNNode c1 = wl1.get(p);
				if (c1.preCode > c2.preCode) {
					// All remaining c1 nodes have even larger preCode; stop early
					break;
				}
				// c1.preCode <= c2.preCode; check postCode
				if (c1.postCode >= c2.postCode) {
					found = true;
					break;
				}
			}
			if (!found)
				return false;
		}
		return true;
	}

	/**
	 * Performs linear-time WN-List intersection.
	 *
	 * @param wl1       the descendant WN-list (Xi)
	 * @param wl2       the ancestor WN-list (Xj)
	 * @param weightOut single-element array; weightOut[0] receives the total weight
	 *                  sum
	 * @return the intersected WN-list
	 */
	private static List<WNNode> intersectWNLists(List<WNNode> wl1, List<WNNode> wl2, double[] weightOut) {
		// Bounded sizing: limit ArrayList expansion scaling by capping initial capacity
		List<WNNode> wl3 = new ArrayList<>(Math.min(wl1.size(), wl2.size()));
		int i = 0, j = 0;
		int size1 = wl1.size(), size2 = wl2.size();

		while (i < size1 && j < size2) {
			WNNode n1 = wl1.get(i); // descendant candidate
			WNNode n2 = wl2.get(j); // ancestor candidate

			if (n2.preCode <= n1.preCode) {
				if (n2.postCode >= n1.postCode) {
					// n2 is a verified structural ancestor of n1 (or identical).
					// Retain the descendant codes (n1) to prevent tree structural corruption.
					// Accumulate weight into out-param during this pass so the caller
					// does not need a separate traversal of the result list.
					weightOut[0] += n1.weight;
					wl3.add(new WNNode(n1.preCode, n1.postCode, n1.weight));
					i++;
				} else {
					// n2 is entirely to the left of n1's subtree
					j++;
				}
			} else {
				// n2 starts after n1; advance descendant pointer
				i++;
			}
		}
		return wl3;
	}

	/**
	 * Returns a new itemset array formed by appending an item to an itemset. If the
	 * item is already present the original array is returned unchanged.
	 * 
	 * @param itemset the base itemset
	 * @param item    the item to append
	 * @return extended array or original if item already present
	 */
	private static int[] appendItem(int[] itemset, int item) {
		for (int v : itemset) {
			if (v == item)
				return itemset;
		}
		int[] result = Arrays.copyOf(itemset, itemset.length + 1);
		result[itemset.length] = item;
		return result;
	}

	/**
	 * Writes a frequent weighted closed itemset to the output file.
	 * 
	 * @param itemset         the itemset to write
	 * @param weightedSupport the weighted support of the itemset
	 * @throws IOException if an error occurs while writing
	 */
	@Override
	protected void writeOut(int[] itemset, double weightedSupport) throws IOException {
		fwciCount++;
		super.writeOut(itemset, weightedSupport);
	}

	/**
	 * Prints statistics about the latest execution to System.out.
	 */
	@Override
	public void printStats() {
		System.out.println("====== NFWCI ALGORITHM v.2.66 STATS ======");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println(" Frequent weighted closed itemsets count: " + fwciCount);
		System.out.println(" Candidate count: " + candidateCount);
		System.out.println("=====================================");
	}
}