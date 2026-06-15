package ca.pfv.spmf.algorithms.frequentpatterns.witfwi;

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the "WIT-FWI-DIFF" algorithm for Frequent
 * Weighted Itemsets Mining using the Diffset strategy, as described in:
 *
 * Vo, B., Coenen, F., Le, B. (2013). A new method for mining Frequent Weighted
 * Itemsets based on WIT-trees. Expert Systems with Applications, 40(4),
 * 1256-1264.
 *
 * The algorithm extends WIT-FWI-MODIFY by using Diffsets instead of Tidsets.
 *
 * Key differences from WIT-FWI-MODIFY: - Level 1: stores full BitSet Tidsets -
 * Level 2+: stores Diffsets computed as d(lj) \ d(li) - Weighted support
 * computed via Eq. (4.3) with subtraction from parent - Theorem 4.2 check: if
 * d(PXY) = ∅ then ws(PXY) = ws(PX), skip computation
 *
 * @see WITNode
 * @see AlgoWIT_FWI
 * @see AlgoWIT_FWI_MOD
 * @author Philippe Fournier-Viger
 */
public class AlgoWIT_FWI_DIFF extends WITAlgoBase {

	/** Ascending weighted-support order used to sort every equivalence class. */
	private static final Comparator<WITNodeDiff> BY_WS_ASC = Comparator
			.comparingDouble((WITNodeDiff n) -> n.weightedSupport);

	/** the number of weighted support computations avoided by Theorem 4.2 */
	public int computationsAvoided = 0;

	/**
	 * Default constructor.
	 */
	public AlgoWIT_FWI_DIFF() {
	}

	/**
	 * Run the WIT-FWI-DIFF algorithm.
	 *
	 * @param inputDatabase   the path to the input transaction database file
	 * @param inputWeights    the path to the input weight table file
	 * @param output          the path to the output file
	 * @param minWeightedSupp the minimum weighted support threshold (0.0 to 1.0)
	 * @throws IOException if an error occurs while reading or writing files
	 */
	public void runAlgorithm(String inputDatabase, String inputWeights, String output, double minWeightedSupp)
			throws IOException {
		initRun(output, minWeightedSupp);
		this.computationsAvoided = 0;

		// Step 1: Load weight table
		Map<Integer, Double> weightTable = loadWeightTable(inputWeights);

		// Steps 2 and 3: Load transactions, precompute tw(tk) for each transaction,
		// and build 1-itemset BitSet tidsets — all in a single O(N) pass.
		Map<Integer, BitSet> itemTidsets = loadDatabase(inputDatabase, weightTable);

		// Step 4: collect all 1-itemsets whose ws >= minws (Lr).
		// ws is computed directly from the tidset via precomputed transactionWeights,
		// avoiding a separate accumulator array.
		List<WITNodeDiff> level1 = new ArrayList<>();
		for (Map.Entry<Integer, BitSet> entry : itemTidsets.entrySet()) {
			int item = entry.getKey();
			BitSet tidset = entry.getValue();
			double ws = computeWeightedSupportTidset(tidset);
			if (ws >= minWeightedSupport) {
				// Level 1 stores full Tidsets (not Diffsets)
				level1.add(new WITNodeDiff(new int[] { item }, tidset, ws));
			}
		}

		// Step 5: sort Lr by ws ascending
		level1.sort(BY_WS_ASC);

		// Step 6: recursively extend via FWI-EXTEND-DIFF
		fwiExtendDiff(level1, true); // true = level 1

		finalizeRun();
	}

	/**
	 * The FWI-EXTEND-DIFF function.
	 *
	 * For each node li in the current equivalence class, li is first output, then
	 * li is joined with every subsequent node lj to produce a candidate diffset. If
	 * ws(X) >= minws the candidate is added to the next level. The next level is
	 * then passed recursively to FWI-EXTEND-DIFF if it contains at least two nodes.
	 * Single-element next levels are output directly without a recursive call.
	 *
	 * @param level    the current equivalence class as a list of WITNodeDiff tuples
	 * @param isLevel1 true if processing level 1 (Tidsets), false for level 2+
	 *                 (Diffsets)
	 * @throws IOException if an error occurs while writing results
	 */
	private void fwiExtendDiff(List<WITNodeDiff> level, boolean isLevel1) throws IOException {
		int levelSize = level.size();

		for (int i = 0; i < levelSize; i++) {
			WITNodeDiff nodeI = level.get(i);

			// Output li before processing its joins
			writeOut(nodeI.itemset, nodeI.weightedSupport);

			List<WITNodeDiff> nextLevel = new ArrayList<>();

			// Join li with all nodes lj following it in this equivalence class
			for (int j = i + 1; j < levelSize; j++) {
				WITNodeDiff nodeJ = level.get(j);
				candidateCount++;

				BitSet newDiffset;
				double newWS;

				if (isLevel1) {
					// Level 1: Use d(X) = t(li) \ t(lj)
					newDiffset = (BitSet) nodeI.tidsetOrDiffset.clone();
					newDiffset.andNot(nodeJ.tidsetOrDiffset);
				} else {
					// Level 2+: Use d(X) = d(lj) \ d(li)
					newDiffset = (BitSet) nodeJ.tidsetOrDiffset.clone();
					newDiffset.andNot(nodeI.tidsetOrDiffset);
				}

				// Theorem 4.2: if d(X) is the empty set, then ws(X) = ws(li)
				if (newDiffset.isEmpty()) {
					newWS = nodeI.weightedSupport;
					computationsAvoided++;
				} else {
					// ws(X) = ws(li) - sum_{t in d(X)} tw(t) / totalTW
					newWS = computeWeightedSupportDiff(nodeI.weightedSupport, newDiffset);

					// ws(X) <= 0 implies t(X) is empty (all transaction weights are positive,
					// so a zero or negative result after subtraction means the effective
					// tidset is empty). Skip this candidate to match base algorithm behaviour.
					if (newWS <= 0.0) {
						continue;
					}
				}

				// Optimization: defer newItemset allocation until after all pruning checks
				// pass, avoiding array allocation cost for rejected candidates.
				if (newWS >= minWeightedSupport) {
					// X = li.itemset ∪ {last item of lj}
					int[] newItemset = new int[nodeI.itemset.length + 1];
					System.arraycopy(nodeI.itemset, 0, newItemset, 0, nodeI.itemset.length);
					newItemset[nodeI.itemset.length] = nodeJ.itemset[nodeJ.itemset.length - 1];

					nextLevel.add(new WITNodeDiff(newItemset, newDiffset, newWS));
				}
			}

			if (nextLevel.size() >= 2) {
				// PRecurse on Li; sort ascending by ws first
				nextLevel.sort(BY_WS_ASC);
				fwiExtendDiff(nextLevel, false);
			} else if (nextLevel.size() == 1) {
				// Single candidate: output it directly (no recursion needed because
				// a list of size 1 has no pairs to join).
				writeOut(nextLevel.get(0).itemset, nextLevel.get(0).weightedSupport);
			}
		}
	}

	/**
	 * Computes ws(X) using ws(X) = ws(parent) - sum_{t in d(X)} tw(t) /
	 * totalTransactionWeight
	 *
	 * When all transaction weights are strictly positive, a result of zero or below
	 * indicates that the effective tidset t(X) is empty. The caller detects this
	 * via a newWS <= 0.0 guard and skips the candidate, mirroring the base
	 * algorithm's newCard == 0 check.
	 *
	 * @param parentWS the weighted support of the parent itemset
	 * @param diffset  the BitSet d(X) of transaction IDs to subtract
	 * @return the weighted support of X, normalized to [0.0, 1.0]
	 */
	private double computeWeightedSupportDiff(double parentWS, BitSet diffset) {
		double weightSum = 0.0;
		for (int tid = diffset.nextSetBit(0); tid >= 0; tid = diffset.nextSetBit(tid + 1)) {
			weightSum += transactionWeights[tid];
		}
		return parentWS - (weightSum / totalTransactionWeight);
	}

	/**
	 * Prints statistics about the latest execution to System.out.
	 */
	public void printStats() {
		System.out.println("====== WIT-FWI-DIFF ALGORITHM - v.2.66 - STATS ======");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println(" Frequent weighted itemsets count: " + fwiCount);
		System.out.println(" Candidate count: " + candidateCount);
		System.out.println(" Computations avoided (Theorem 4.2): " + computationsAvoided);
		System.out.println("=========================================================");
	}
}