package ca.pfv.spmf.algorithms.frequentpatterns.witfwi;

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
*
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
 * This is an implementation of the "WIT-FWI-MODIFY" algorithm for Frequent
 * Weighted Itemsets Mining, the optimized variant of WIT-FWI using Corollary
 * 4.1, as described in:
 *
 * Vo, B., Coenen, F., Le, B. (2013). A new method for mining Frequent Weighted
 * Itemsets based on WIT-trees. Expert Systems with Applications, 40(4),
 * 1256-1264.
 *
 * @see WITNode
 * @author Philippe Fournier-Viger
 */
public class AlgoWIT_FWI_MOD extends WITAlgoBase {

	/** Ascending weighted-support order used to sort every equivalence class. */
	private static final Comparator<WITNode> BY_WS_ASC = Comparator.comparingDouble((WITNode n) -> n.weightedSupport);

	/** the number of weighted support computations avoided by Corollary 4.1 */
	public int computationsAvoided = 0;

	/**
	 * Default constructor.
	 */
	public AlgoWIT_FWI_MOD() {
	}

	/**
	 * Run the WIT-FWI-MODIFY algorithm.
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

		// Step 1: Load weight table (must precede database loading)
		Map<Integer, Double> weightTable = loadWeightTable(inputWeights);

		// Step 2 and 3: Load transactions, precompute tw for each transaction, and
		// build
		// individual BitSet tidsets for 1-itemsets.
		Map<Integer, BitSet> itemTidsets = loadDatabase(inputDatabase, weightTable);

		// Step 4 : collect all 1-itemsets whose ws >= minws (Lr)
		List<WITNode> level1 = new ArrayList<>();
		for (Map.Entry<Integer, BitSet> entry : itemTidsets.entrySet()) {
			int item = entry.getKey();
			BitSet bs = entry.getValue();
			double ws = computeWeightedSupportTidset(bs);

			if (ws >= minWeightedSupport) {
				level1.add(new WITNode(new int[] { item }, bs, ws, bs.cardinality()));
			}
		}

		// Step 5: sort Lr by ws ascending
		level1.sort(BY_WS_ASC);

		// Step 6: recursively extend via FWI-EXTEND-MODIFY,
		// which outputs each li before joining it with subsequent nodes
		fwiExtendModify(level1);

		finalizeRun();
	}

	/**
	 * Implements the FWI-EXTEND-MODIFY function (Fig. 4 of the paper).
	 *
	 * Identical to FWI-EXTEND except that ws(X) is determined by the three-way
	 * check before falling back to explicit COMPUTE-WS:
	 *
	 * if |t(li)| = |t(X)| then ws(X) = ws(li) 
	 * else if |t(lj)| =  |t(X)| 
	 * then ws(X) = ws(lj) else ws(X) = COMPUTE-WS(t(X))
	 *
	 * Each node li is output before its join loop. 
	 *
	 * @param level the current equivalence class as a list of WITNode tuples
	 * @throws IOException if an error occurs while writing results
	 */
	private void fwiExtendModify(List<WITNode> level) throws IOException {
		int levelSize = level.size();
		for (int i = 0; i < levelSize; i++) {
			WITNode nodeI = level.get(i);
			// Read cached cardinality once per outer node — O(1)
			int cardI = nodeI.tidsetCardinality;

			// Output li before processing its joins
			writeOut(nodeI.itemset, nodeI.weightedSupport);

			List<WITNode> nextLevel = new ArrayList<>();

			for (int j = i + 1; j < levelSize; j++) {
				WITNode nodeJ = level.get(j);
				candidateCount++;

				// t(X) = t(li) ∩ t(lj) via BitSet.and() — O(n/64)
				BitSet newTidset = (BitSet) nodeI.tidset.clone();
				newTidset.and(nodeJ.tidset);

				// Compute |t(X)| once; reused for both Corollary 4.1 comparisons
				int newCard = newTidset.cardinality();
				if (newCard == 0) {
					continue;
				}

				double newWS;

				// |t(li)| = |t(X)| → ws(X) = ws(li)
				if (cardI == newCard) {
					newWS = nodeI.weightedSupport;
					computationsAvoided++;
				}
				// |t(lj)| = |t(X)| → ws(X) = ws(lj)
				else if (nodeJ.tidsetCardinality == newCard) {
					newWS = nodeJ.weightedSupport;
					computationsAvoided++;
				}
				// Paper line 11: explicit COMPUTE-WS via precomputed tw values
				else {
					newWS = computeWeightedSupportTidset(newTidset);
				}

				// Optimization: Avoid array creation entirely if candidate fails pruning
				if (newWS >= minWeightedSupport) {
					// X = li.itemset ∪ {last item of lj}
					int[] newItemset = new int[nodeI.itemset.length + 1];
					System.arraycopy(nodeI.itemset, 0, newItemset, 0, nodeI.itemset.length);
					newItemset[nodeI.itemset.length] = nodeJ.itemset[nodeJ.itemset.length - 1];

					nextLevel.add(new WITNode(newItemset, newTidset, newWS, newCard));
				}
			}

			if (nextLevel.size() >= 2) {
				// PRecurse on Li; sort ascending by ws first
				nextLevel.sort(BY_WS_ASC);
				fwiExtendModify(nextLevel);
			} else if (nextLevel.size() == 1) {
				// Single candidate: output it directly (no recursion needed because
				// a list of size 1 has no pairs to join)
				writeOut(nextLevel.get(0).itemset, nextLevel.get(0).weightedSupport);
			}
		}
	}

	/**
	 * Prints statistics about the latest execution to System.out.
	 */
	public void printStats() {
		System.out.println("======  WIT-FWI-MODIFY ALGORITHM - v2.66 - STATS ======");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println(" Frequent weighted itemsets count: " + fwiCount);
		System.out.println(" Candidate count: " + candidateCount);
		System.out.println(" Computations avoided (Corollary 4.1): " + computationsAvoided);
		System.out.println("=========================================================");
	}
}