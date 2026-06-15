package ca.pfv.spmf.algorithms.frequentpatterns.NFWI;

/*
 * This file is copyright (c) 2008-2024 Philippe Fournier-Viger
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see http://www.gnu.org/licenses/.
 *
 * Do not remove copyright or license information from this file.
 */

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This is an implementation of the "NFWI" algorithm for Frequent Weighted
 * Itemsets Mining as described in the paper:
 *
 * Bui, H., Vo, B., Nguyen, H., Nguyen-Hoang, T.-A., Hong, T.-P. (2018). A
 * weighted N-list-based method for mining frequent weighted itemsets. Expert
 * Systems with Applications, 96, 388-405.
 *
 * The algorithm enumerates itemsets using a prefix-based equivalence-class
 * decomposition of the search space mapped to a Weighted Node Tree (WN-tree).
 * Each equivalence class is represented as a flat list of WN-codes mapped to
 * the pre-order and post-order traversal integers.
 *
 * @see WNNode
 * @author Philippe Fournier-Viger
 */
public class AlgoNFWI extends AlgoNFWIBase {

	/** the number of frequent weighted itemsets found */
	public int fwiCount = 0;

	/**
	 * Wraps an itemset along with its corresponding WN-list and calculated weighted
	 * support.
	 */
	private static class ItemsetWNList {

		/** The integer array representing the itemset */
		int[] itemset;

		/** The corresponding WN-list of codes for this itemset */
		List<WNNode> wnList;

		/** The calculated weighted support ratio */
		double weightedSupport;

		/**
		 * Constructor to initialize the prefix class wrapper.
		 * 
		 * @param itemset         the primitive array representing the itemset
		 * @param wnList          the WN-list structural codes
		 * @param weightedSupport the normalized support weight
		 */
		ItemsetWNList(int[] itemset, List<WNNode> wnList, double weightedSupport) {
			this.itemset = itemset;
			this.wnList = wnList;
			this.weightedSupport = weightedSupport;
		}
	}

	/**
	 * Default constructor.
	 */
	public AlgoNFWI() {
	}

	/**
	 * Run the NFWI algorithm.
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
		this.fwiCount = 0;
		this.candidateCount = 0;
		this.codeCounter = 1;

		writer = new BufferedWriter(new FileWriter(output), 65536);

		// Steps 1-5: shared pipeline (database loading, WN-tree construction, code
		// assignment)
		PipelineResult pipeline = runPipeline(inputDatabase, inputWeights, false);

		if (pipeline == null) {
			writer.close();
			endTimestamp = System.currentTimeMillis();
			return;
		}

		// Step 6: Form the prefix-based equivalence classes for level 1 (in reverse
		// global order)
		List<ItemsetWNList> level1 = new ArrayList<>();
		for (int i = pipeline.globalOrder.size() - 1; i >= 0; i--) {
			int item = pipeline.globalOrder.get(i);
			level1.add(new ItemsetWNList(new int[] { item }, pipeline.wnListArray[i],
					pipeline.frequent1ItemsetsWS.get(item)));
		}

		// Step 7: Recursively extend via FWI-EXTEND using WN-list intersection
		fwiExtend(level1);

		MemoryLogger.getInstance().checkMemory();
		writer.close();
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * The FWI-EXTEND function. For each node li in the current
	 * equivalence class Lr, li is first output, then li is joined with every
	 * subsequent node lj to produce candidate X. Intersection of WN-lists is
	 * performed in O(|WL1| + |WL2|) time.
	 *
	 * sumWeight is accumulated inside intersectWNLists via a one-element double[]
	 * out-parameter, eliminating the separate post-intersection loop over the
	 * result list.
	 *
	 * @param level the current equivalence class as a list of ItemsetWNList
	 *              wrappers
	 * @throws IOException if an error occurs while writing results
	 */
	private void fwiExtend(List<ItemsetWNList> level) throws IOException {
		int levelSize = level.size();
		double[] weightOut = new double[1]; // reusable out-param for sum

		for (int i = 0; i < levelSize; i++) {
			ItemsetWNList nodeI = level.get(i);
			writeOut(nodeI.itemset, nodeI.weightedSupport);
			List<ItemsetWNList> nextLevel = new ArrayList<>();

			for (int j = i + 1; j < levelSize; j++) {
				ItemsetWNList nodeJ = level.get(j);
				candidateCount++;

				weightOut[0] = 0.0;
				List<WNNode> intersectedList = intersectWNLists(nodeI.wnList, nodeJ.wnList, weightOut);

				if (intersectedList.isEmpty()) {
					continue;
				}

				// Use the already-computed sum instead of a second traversal
				double ws = weightOut[0] / totalTransactionWeight;

				if (ws >= minWeightedSupport) {
					int[] newItemset = new int[nodeI.itemset.length + 1];
					System.arraycopy(nodeI.itemset, 0, newItemset, 0, nodeI.itemset.length);
					newItemset[nodeI.itemset.length] = nodeJ.itemset[nodeJ.itemset.length - 1];
					nextLevel.add(new ItemsetWNList(newItemset, intersectedList, ws));
				}
			}

			if (!nextLevel.isEmpty()) {
				fwiExtend(nextLevel);
			}
		}

		MemoryLogger.getInstance().checkMemory();
	}
	/**
	 * Performs linear-time WN-List intersection checking the ancestor-descendant
	 * constraints. Multiple descendants matching the same ancestor are merged,
	 * inheriting the ancestor's codes.
	 *
	 * Accumulates the total weight of all added nodes into weightOut[0] so the
	 * caller does not need a second pass over the returned list.
	 *
	 * @param wl1       the descendant WN-list (prefix path item lower in the tree)
	 * @param wl2       the ancestor WN-list (extending item closer to the tree
	 *                  root)
	 * @param weightOut single-element array; weightOut[0] receives the total weight
	 *                  sum
	 * @return the intersected WN-list containing merged inherited weights
	 */
	private static List<WNNode> intersectWNLists(List<WNNode> wl1, List<WNNode> wl2, double[] weightOut) {
		// Bounded sizing: limit ArrayList expansion scaling by capping initial capacity
		List<WNNode> wl3 = new ArrayList<>(wl2.size());
		int i = 0, j = 0;
		int size1 = wl1.size(), size2 = wl2.size();

		while (i < size1 && j < size2) {
			WNNode n1 = wl1.get(i); // Descendant candidate
			WNNode n2 = wl2.get(j); // Ancestor candidate

			if (n2.preCode < n1.preCode) {
				if (n2.postCode > n1.postCode) {
					// n2 is a verified ancestor of n1
					double sumWeight = n1.weight;
					i++;

					// Merge all contiguous descendants under the same ancestor subtree
					while (i < size1) {
						WNNode nextN1 = wl1.get(i);
						if (n2.preCode < nextN1.preCode && n2.postCode > nextN1.postCode) {
							sumWeight += nextN1.weight;
							i++;
						} else {
							break;
						}
					}

					weightOut[0] += sumWeight;

					// Add the merged node inheriting the ancestor's structural codes
					wl3.add(new WNNode(n2.preCode, n2.postCode, sumWeight));
					j++;
				} else {
					// n2 is entirely to the left of n1's ancestry line
					j++;
				}
			} else {
				// n2 is to the right of n1's ancestry line
				i++;
			}
		}

		return wl3;
	}

	/**
	 * Writes a frequent weighted itemset to the output file. 
	 * 
	 * @param itemset         the itemset to write
	 * @param weightedSupport the weighted support of the itemset
	 * @throws IOException if an error occurs while writing
	 */
	@Override
	protected void writeOut(int[] itemset, double weightedSupport) throws IOException {
		fwiCount++;
		super.writeOut(itemset, weightedSupport);
	}

	/**
	 * Prints statistics about the latest execution to System.out.
	 */
	@Override
	public void printStats() {
		System.out.println("====== NFWI ALGORITHM 2.66 - STATS ======");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Memory ~ " + MemoryLogger.getInstance().getMaxMemory() + " MB");
		System.out.println(" Frequent weighted itemsets count: " + fwiCount);
		System.out.println(" Candidate count: " + candidateCount);
		System.out.println("====================================");
	}
}