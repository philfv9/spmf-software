package ca.pfv.spmf.algorithms.sequentialpatterns.suchain;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.pfv.spmf.tools.MemoryLogger;

/* This file is copyright (c) 2026 Philippe Fournier-Viger
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
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 *
 * Do not remove copyright and license information.
 */

/**
 * This is an implementation of the "SU-Chain" algorithm for High-Utility
 * Sequential Pattern Mining as described in the paper:<br/><br/>
 *
 *  Lin, J.C.-W., Li, Y., Fournier-Viger, P., Djenouri, Y., Zhang, J.
 *  "Efficient Chain Structure for High-Utility Sequential Pattern Mining".
 *  IEEE Access, vol. 8, 2020.
 *
 * The algorithm uses a lexicographic enumeration (LE)-tree to represent the
 * search space and a sequence-utility (SU)-Chain structure to keep the
 * projection database together with its utility-lists. Four pruning strategies
 * (PS1-PS4) are applied: PS1 is the sequence-weighted utilization (SWU) bound,
 * PS2 is the prefix extension utility (PEU) bound, PS3 is the look-ahead
 * strategy (LAS), and PS4 is the irrelevant item pruning strategy (IPS),
 * which discards an item as irrelevant only when BOTH its I-Concatenation
 * upper bound and its S-Concatenation upper bound are below the threshold (an
 * item is kept if either concatenation could still yield a high-utility
 * sequence). Removing the irrelevant items lowers the remaining utilities
 * used to compute the PEU bounds, tightening them. As in the paper, the
 * removal persists for the supersets of a pattern: each projection chain
 * carries the accumulated set of items pruned by PS4 on the path from the
 * root, and the remaining utilities are recomputed on the fly excluding
 * those items' utilities; this yields the same pruning effect as the
 * paper's projection-database reduction, without physically rewriting the
 * shared chains.
 *
 * The input uses the SPMF utility sequence format and the output uses the same
 * format as the USpan algorithm.
 *
 * @see SUChain
 * @see SUElement
 * @author Philippe Fournier-Viger
 */
public class AlgoSUChain {

	/** the time the algorithm started */
	long startTimestamp = 0;
	/** the time the algorithm terminated */
	long endTimestamp = 0;
	/** the number of patterns generated */
	int patternCount = 0;
	/** the cached last-run maximum detected by SPMF MemoryLogger */
	double peakMemory = 0;

	/** writer to write the output file */
	BufferedWriter writer = null;

	/** buffer for storing the current pattern that is mined */
	final int BUFFERS_SIZE = 2000;
	int[] patternBuffer = null;

	/** the minUtility threshold */
	int minUtility = 0;

	/** max pattern length */
	int maxPatternLength = 1000;

	/**
	 * Default constructor
	 */
	public AlgoSUChain() {
	}

	/**
	 * Run the SUCHain algorithm
	 * @param input the input file path
	 * @param output the output file path
	 * @param minUtility the minimum utility threshold
	 * @throws IOException exception if error while writing the file
	 */
	public void runAlgorithm(String input, String output, int minUtility) throws IOException {
		// reset maximum
		MemoryLogger.getInstance().reset();
		peakMemory = 0;

		// initialize the buffer for storing the current pattern
		patternBuffer = new int[BUFFERS_SIZE];

		// record the start time of the algorithm
		startTimestamp = System.currentTimeMillis();

		// create a writer object to write results to file
		writer = new BufferedWriter(new FileWriter(output));
		writer.write("@FILETYPE=\"High-utility sequential patterns\"");
		writer.newLine();
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.newLine();

		// save the minimum utility threshold
		this.minUtility = minUtility;

		// ========== FIRST DATABASE SCAN: parse all sequences and calculate the
		// SWU of each item =========
		final List<List<List<int[]> > > sequences = new ArrayList<List<List<int[]> > >();
		BufferedReader myInput = null;
		String thisLine;
		try {
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));
			while ((thisLine = myInput.readLine()) != null) {
				if (thisLine.isEmpty() || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
					continue;
				}
				List<List<int[]>> itemsets = parseLine(thisLine);
				if (itemsets == null) {
					continue;
				}
				sequences.add(itemsets);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (myInput != null) {
				myInput.close();
			}
		}

		// ========== SWDC: sequence-weighted downward closure =========
		// Repeatedly remove items whose SWU is below the threshold and recompute
		// the SWU of the remaining items, because removing an item lowers the
		// utility of the sequences that contained it (and thus the SWU of the
		// items sharing those sequences). This is the downward-closure property
		// of high-utility sequential pattern mining (Apriori does not hold).
		// Reused across the downward-closure iterations (memory optimization).
		Set<Integer> seen = new HashSet<Integer>();
		Set<Integer> toRemove = new HashSet<Integer>();
		Map<Integer, Integer> currentSWU = new HashMap<Integer, Integer>();
		boolean changed = true;
		while (changed) {
			currentSWU.clear();
			for (List<List<int[]>> itemsets : sequences) {
				int sequenceUtility = sequenceUtility(itemsets);
				seen.clear();
				for (List<int[]> itemset : itemsets) {
					for (int[] pu : itemset) {
						Integer item = Integer.valueOf(pu[0]);
						if (seen.add(item)) {
							Integer swu = currentSWU.get(item);
							currentSWU.put(item, swu == null ? Integer.valueOf(sequenceUtility) : Integer.valueOf(swu.intValue() + sequenceUtility));
						}
					}
				}
			}
			toRemove.clear();
			for (Map.Entry<Integer, Integer> entry : currentSWU.entrySet()) {
				if (entry.getValue().intValue() < minUtility) {
					toRemove.add(entry.getKey());
				}
			}
			if (toRemove.isEmpty()) {
				break;
			}
			changed = true;
			for (int si = 0; si < sequences.size(); si++) {
				List<List<int[]>> itemsets = sequences.get(si);
				List<List<int[]>> newItemsets = new ArrayList<List<int[]>>(itemsets.size());
				for (List<int[]> itemset : itemsets) {
					List<int[]> kept = new ArrayList<int[]>(itemset.size());
					for (int[] pu : itemset) {
						if (!toRemove.contains(Integer.valueOf(pu[0]))) {
							kept.add(pu);
						}
					}
					if (!kept.isEmpty()) {
						newItemsets.add(kept);
					}
				}
				sequences.set(si, newItemsets);
			}
		}
		// the final SWU is held in currentSWU; used directly below for the
		// list of promising 1-sequences (no copy, memory optimization)

		// ========== Build the SU-Chain of each sequence =========
		final List<SUChain> database = new ArrayList<SUChain>(sequences.size());
		for (List<List<int[]>> itemsets : sequences) {
			if (!itemsets.isEmpty()) {
				database.add(new SUChain(itemsets));
			}
		}

		MemoryLogger.getInstance().checkMemory();

		// Mine the database. For each promising 1-sequence, build its SU-Chain
		// and grow the pattern recursively using the Mining procedure.
		List<Integer> promisingItems = new ArrayList<Integer>();
		for (Map.Entry<Integer, Integer> entry : currentSWU.entrySet()) {
			if (entry.getValue().intValue() >= minUtility) {
				promisingItems.add(entry.getKey());
			}
		}
		Collections.sort(promisingItems);

		for (Integer item : promisingItems) {
			int itemValue = item.intValue();
			List<SUChain> suc = buildFirstLevelChain(database, itemValue);
			if (suc.isEmpty()) {
				continue;
			}
			int[] uAndPeu = computeUtilityAndPEU(suc);
			int utility = uAndPeu[0];
			int peu = uAndPeu[1];

			patternBuffer[0] = itemValue;
			if (utility >= minUtility) {
				writeOut(patternBuffer, 1, utility);
			}
			if (peu >= minUtility && 1 < maxPatternLength) {
				mining(patternBuffer, 1, suc);
			}
		}

		MemoryLogger.getInstance().checkMemory();
		writer.close();
		peakMemory = MemoryLogger.getInstance().getMaxMemory();
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * This is the Mining procedure of SUCHain. It performs a depth-first search
	 * of the LE-tree. It first applies the pruning strategy 4 (PS4) to remove
	 * the irrelevant items from the projection database, then applies the
	 * look-ahead strategy (LAS) to obtain the candidate items for
	 * I-Concatenation and S-Concatenation, and finally evaluates each candidate.
	 * @param prefix the buffer storing the current prefix
	 * @param prefixLength the current prefix length
	 * @param suc the SU-Chain of the prefix
	 * @throws IOException if an error occurs while writing to file
	 */
	private void mining(int[] prefix, int prefixLength, List<SUChain> suc) throws IOException {
		// The set of items that were pruned as irrelevant by this node or by
		// its ancestors (PS4). The paper removes those items from the
		// projection database, so their utilities are excluded from the
		// remaining utilities used to compute the PEU bounds.
		// All chains at this node share the same ignored set (inherited from
		// the parent), so we take it from the first chain instead of unioning
		// the identical sets of every chain.
		final Set<Integer> nodeIgnored = suc.isEmpty() ? new HashSet<Integer>() : suc.get(0).ignored;
		final Map<Integer, Integer> mapItemToIBound = new HashMap<Integer, Integer>();
		final Map<Integer, Integer> mapItemToSBound = new HashMap<Integer, Integer>();
		final List<ChainInfo> infos = new ArrayList<ChainInfo>(suc.size());
		// Reused across the chains and the PS4 recompute (memory optimization).
		Set<Integer> iCandidates = new HashSet<Integer>();
		Set<Integer> sCandidates = new HashSet<Integer>();

		for (SUChain chain : suc) {
			int peuValue = 0;
			iCandidates.clear();
			sCandidates.clear();
			// Collect the S-Concatenation candidates once, from the itemsets
			// after the earliest match (the union over all matches equals the
			// items after the earliest one, since matches are ordered by itemset).
			int earliestItemset = Integer.MAX_VALUE;
			for (SUElement element : chain.utilityList) {
				int mItemset = chain.elements[element.position].itemsetIndex;
				if (mItemset < earliestItemset) {
					earliestItemset = mItemset;
				}
			}
			if (earliestItemset < chain.itemsetCount) {
				for (int c = earliestItemset + 1; c < chain.itemsetCount; c++) {
					for (Integer idx : chain.itemsets.get(c)) {
						int item = chain.elements[idx.intValue()].item;
						if (!nodeIgnored.contains(Integer.valueOf(item))) {
							sCandidates.add(Integer.valueOf(item));
						}
					}
				}
			}
			int lastItem = prefix[prefixLength - 1];
			for (SUElement element : chain.utilityList) {
				SUChain.SeqElement e = chain.elements[element.position];
				// Def. 15/16: the PEU contribution of a match is 0 when the utility
				// of the remaining sequence is 0.
				int ru = reducedRemainingUtility(chain, element.position, chain.ignored);
				if (ru > 0) {
					peuValue = Math.max(peuValue, element.maxUtility + ru);
				}
				// items in the same itemset after the last item of the prefix
				for (Integer idx : chain.itemsets.get(e.itemsetIndex)) {
					SUChain.SeqElement ce = chain.elements[idx.intValue()];
					if (ce.item > lastItem && !nodeIgnored.contains(Integer.valueOf(ce.item))) {
						iCandidates.add(Integer.valueOf(ce.item));
					}
				}
			}
			// LAS bounds: each candidate item is bounded by the PEU of the
			// prefix in this sequence.
			for (Integer item : iCandidates) {
				Integer bound = mapItemToIBound.get(item);
				mapItemToIBound.put(item, bound == null ? Integer.valueOf(peuValue) : Integer.valueOf(bound.intValue() + peuValue));
			}
			for (Integer item : sCandidates) {
				Integer bound = mapItemToSBound.get(item);
				mapItemToSBound.put(item, bound == null ? Integer.valueOf(peuValue) : Integer.valueOf(bound.intValue() + peuValue));
			}
			infos.add(new ChainInfo(chain, peuValue));
		}

		// =========== IRRELEVANT ITEM PRUNING STRATEGY (PS4) ===============
		// Pruning strategy 4 (PS4): an item ij is irrelevant if BOTH the
		// upper bound of its I-Concatenation and the upper bound of its
		// S-Concatenation are below the minimum utility threshold. If either
		// concatenation could still yield a high-utility sequence, the item is
		// kept. A missing bound (the item is not a candidate for that
		// concatenation) counts as 0, i.e. below the threshold. The paper
		// removes the irrelevant items from the projection database of the
		// node and of its supersets; we implement the removal by carrying an
		// accumulated set of irrelevant items down the search tree and by
		// recomputing the remaining utilities excluding those items' utilities
		// on the fly (a memory optimization with the same pruning effect).
		Set<Integer> irrelevant = new HashSet<Integer>();
		Set<Integer> candidateItems = new HashSet<Integer>(mapItemToIBound.keySet());
		candidateItems.addAll(mapItemToSBound.keySet());
		for (Integer item : candidateItems) {
			Integer iBound = mapItemToIBound.get(item);
			Integer sBound = mapItemToSBound.get(item);
			int iVal = iBound == null ? 0 : iBound.intValue();
			int sVal = sBound == null ? 0 : sBound.intValue();
			if (iVal < minUtility && sVal < minUtility) {
				irrelevant.add(item);
			}
		}
		// the set of items to exclude from the projection database of the
		// children of this node (this node's own irrelevant items plus those
		// of its ancestors)
		Set<Integer> childIgnored = nodeIgnored;
		if (!irrelevant.isEmpty()) {
			childIgnored = new HashSet<Integer>(nodeIgnored);
			childIgnored.addAll(irrelevant);
			// Recompute the PEU of each projected sequence with the remaining
			// utilities that exclude the irrelevant items, then rebuild the
			// I-Concatenation and S-Concatenation bounds. The irrelevant items
			// are also not offered as concatenation candidates anymore, as if
			// they had been removed from the projection database.
			mapItemToIBound.clear();
			mapItemToSBound.clear();
			for (ChainInfo info : infos) {
				SUChain chain = info.chain;
				int peuValue = 0;
				iCandidates.clear();
				sCandidates.clear();
				// Collect the S-Concatenation candidates once, from the itemsets
				// after the earliest match.
				int earliestItemset = Integer.MAX_VALUE;
				for (SUElement element : chain.utilityList) {
					int mItemset = chain.elements[element.position].itemsetIndex;
					if (mItemset < earliestItemset) {
						earliestItemset = mItemset;
					}
				}
				if (earliestItemset < chain.itemsetCount) {
					for (int c = earliestItemset + 1; c < chain.itemsetCount; c++) {
						for (Integer idx : chain.itemsets.get(c)) {
							int item = chain.elements[idx.intValue()].item;
							if (!childIgnored.contains(Integer.valueOf(item))) {
								sCandidates.add(Integer.valueOf(item));
							}
						}
					}
				}
				int lastItem = prefix[prefixLength - 1];
				for (SUElement element : chain.utilityList) {
					SUChain.SeqElement e = chain.elements[element.position];
					int ru = reducedRemainingUtility(chain, element.position, childIgnored);
					if (ru > 0) {
						peuValue = Math.max(peuValue, element.maxUtility + ru);
					}
					for (Integer idx : chain.itemsets.get(e.itemsetIndex)) {
						SUChain.SeqElement ce = chain.elements[idx.intValue()];
						if (ce.item > lastItem && !childIgnored.contains(Integer.valueOf(ce.item))) {
							iCandidates.add(Integer.valueOf(ce.item));
						}
					}
				}
				for (Integer item : iCandidates) {
					Integer bound = mapItemToIBound.get(item);
					mapItemToIBound.put(item, bound == null ? Integer.valueOf(peuValue) : Integer.valueOf(bound.intValue() + peuValue));
				}
				for (Integer item : sCandidates) {
					Integer bound = mapItemToSBound.get(item);
					mapItemToSBound.put(item, bound == null ? Integer.valueOf(peuValue) : Integer.valueOf(bound.intValue() + peuValue));
				}
				info.peuValue = peuValue;
			}
		}

		// =========== I-CONCATENATIONS ===========================
		for (Integer item : sortedCandidates(mapItemToIBound)) {
			int itemValue = item.intValue();
			List<SUChain> newSuc = new ArrayList<SUChain>();
			int[] uAndPeu = buildChainI(infos, prefix, prefixLength, itemValue, newSuc, childIgnored);
			if (newSuc.isEmpty()) {
				continue;
			}
			int utility = uAndPeu[0];
			int peu = uAndPeu[1];
			patternBuffer[prefixLength] = itemValue;
			if (utility >= minUtility) {
				writeOut(patternBuffer, prefixLength + 1, utility);
			}
			if (peu >= minUtility && prefixLength + 1 < maxPatternLength) {
				mining(patternBuffer, prefixLength + 1, newSuc);
			}
		}

		// =========== S-CONCATENATIONS ===========================
		for (Integer item : sortedCandidates(mapItemToSBound)) {
			int itemValue = item.intValue();
			List<SUChain> newSuc = new ArrayList<SUChain>();
			int[] uAndPeu = buildChainS(infos, prefix, prefixLength, itemValue, newSuc, childIgnored);
			if (newSuc.isEmpty()) {
				continue;
			}
			int utility = uAndPeu[0];
			int peu = uAndPeu[1];
			patternBuffer[prefixLength] = -1;
			patternBuffer[prefixLength + 1] = itemValue;
			if (utility >= minUtility) {
				writeOut(patternBuffer, prefixLength + 2, utility);
			}
			if (peu >= minUtility && prefixLength + 2 < maxPatternLength) {
				mining(patternBuffer, prefixLength + 2, newSuc);
			}
		}

		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Build the SU-Chain of the I-Concatenation of the prefix with an item, and
	 * compute the utility and the PEU of the resulting sequence.
	 * @param infos the SU-Chain of the prefix
	 * @param prefix the current prefix
	 * @param prefixLength the current prefix length
	 * @param item the item to append to the last itemset
	 * @param newSuc the SU-Chain of the new sequence (output)
	 * @param ignored the set of irrelevant items pruned by the parent node and
	 * its ancestors (PS4); they are carried to the child chains so that their
	 * utilities are excluded from the remaining utilities
	 * @return an array {utility, PEU} of the new sequence
	 */
	private int[] buildChainI(List<ChainInfo> infos, int[] prefix, int prefixLength, int item, List<SUChain> newSuc, Set<Integer> ignored) {
		int newUtility = 0;
		int newPeu = 0;
		int lastItem = prefix[prefixLength - 1];
		for (ChainInfo info : infos) {
			SUChain chain = info.chain;
			// sequence.
			List<SUElement> newList = new ArrayList<SUElement>();
			int maxUtil = 0;
			int maxPeu = 0;
			for (SUElement element : chain.utilityList) {
				SUChain.SeqElement e = chain.elements[element.position];
				int idx = chain.findItemInItemset(e.itemsetIndex, item);
				if (idx >= 0 && item > lastItem) {
					SUChain.SeqElement ne = chain.elements[idx];
					int newUtil = element.maxUtility + ne.utility;
					int ru = reducedRemainingUtility(chain, idx, ignored);
					newList.add(new SUElement(idx, newUtil, ru, -1));
					if (newUtil > maxUtil) {
						maxUtil = newUtil;
					}
					if (ru > 0 && newUtil + ru > maxPeu) {
						maxPeu = newUtil + ru;
					}
				}
			}
			if (!newList.isEmpty()) {
				SUChain newChain = copyChain(chain);
				newChain.utilityList = newList;
				newChain.ignored = ignored;
				linkUtilityList(newChain.utilityList);
				newSuc.add(newChain);
				newUtility += maxUtil;
				newPeu += maxPeu;
			}
		}
		return new int[] { newUtility, newPeu };
	}

	/**
	 * Build the SU-Chain of the S-Concatenation of the prefix with an item, and
	 * compute the utility and the PEU of the resulting sequence.
	 * @param infos the SU-Chain of the prefix
	 * @param prefix the current prefix
	 * @param prefixLength the current prefix length
	 * @param item the item to append as a new itemset
	 * @param newSuc the SU-Chain of the new sequence (output)
	 * @param ignored the set of irrelevant items pruned by the parent node and
	 * its ancestors (PS4); they are carried to the child chains so that their
	 * utilities are excluded from the remaining utilities
	 * @return an array {utility, PEU} of the new sequence
	 */
	private int[] buildChainS(List<ChainInfo> infos, int[] prefix, int prefixLength, int item, List<SUChain> newSuc, Set<Integer> ignored) {
		int newUtility = 0;
		int newPeu = 0;
		for (ChainInfo info : infos) {
			SUChain chain = info.chain;
			List<int[]> occurrences = chain.itemOccurrences.get(Integer.valueOf(item));
			if (occurrences == null) {
				continue;
			}
			// sequence.
			List<SUElement> newList = new ArrayList<SUElement>();
			int maxUtil = 0;
			int maxPeu = 0;
			for (SUElement element : chain.utilityList) {
				SUChain.SeqElement e = chain.elements[element.position];
				for (int[] occ : occurrences) {
					if (occ[0] > e.itemsetIndex) {
						int idx = occ[1];
						SUChain.SeqElement ne = chain.elements[idx];
						int newUtil = element.maxUtility + ne.utility;
						int ru = reducedRemainingUtility(chain, idx, ignored);
						newList.add(new SUElement(idx, newUtil, ru, -1));
						if (newUtil > maxUtil) {
							maxUtil = newUtil;
						}
						if (ru > 0 && newUtil + ru > maxPeu) {
							maxPeu = newUtil + ru;
						}
					}
				}
			}
			if (!newList.isEmpty()) {
				SUChain newChain = copyChain(chain);
				newChain.utilityList = newList;
				newChain.ignored = ignored;
				linkUtilityList(newChain.utilityList);
				newSuc.add(newChain);
				newUtility += maxUtil;
				newPeu += maxPeu;
			}
		}
		return new int[] { newUtility, newPeu };
	}

	/**
	 * Build the SU-Chain of a 1-sequence <item>.
	 * @param database the SU-Chains of the database
	 * @param item the item
	 * @return the SU-Chain of <item>
	 */
	private List<SUChain> buildFirstLevelChain(List<SUChain> database, int item) {
		List<SUChain> suc = new ArrayList<SUChain>();
		for (SUChain chain : database) {
			List<int[]> occurrences = chain.itemOccurrences.get(Integer.valueOf(item));
			if (occurrences == null || occurrences.isEmpty()) {
				continue;
			}
			List<SUElement> list = new ArrayList<SUElement>(occurrences.size());
			for (int[] occ : occurrences) {
				int idx = occ[1];
				SUChain.SeqElement e = chain.elements[idx];
				list.add(new SUElement(idx, e.utility, e.remainingUtility, -1));
			}
			SUChain newChain = copyChain(chain);
			newChain.utilityList = list;
			linkUtilityList(newChain.utilityList);
			suc.add(newChain);
		}
		return suc;
	}

	/**
	 * Compute the utility and the PEU of a sequence from its SU-Chain.
	 * @param suc the SU-Chain
	 * @return an array {utility, PEU}
	 */
	private int[] computeUtilityAndPEU(List<SUChain> suc) {
		int utility = 0;
		int peu = 0;
		for (SUChain chain : suc) {
			// sequence.
			int maxUtil = 0;
			int maxPeu = 0;
			for (SUElement element : chain.utilityList) {
				if (element.maxUtility > maxUtil) {
					maxUtil = element.maxUtility;
				}
			int ru = reducedRemainingUtility(chain, element.position, chain.ignored);
			if (ru > 0 && element.maxUtility + ru > maxPeu) {
				maxPeu = element.maxUtility + ru;
				}
			}
			utility += maxUtil;
			peu += maxPeu;
		}
		return new int[] { utility, peu };
	}

	/**
	 * Return the candidate items whose LAS bound is at least the threshold, in
	 * increasing order.
	 * @param bounds the LAS bound of each candidate item
	 * @return the sorted list of promising candidate items
	 */
	private List<Integer> sortedCandidates(Map<Integer, Integer> bounds) {
		List<Integer> items = new ArrayList<Integer>();
		for (Map.Entry<Integer, Integer> entry : bounds.entrySet()) {
			if (entry.getValue().intValue() >= minUtility) {
				items.add(entry.getKey());
			}
		}
		Collections.sort(items);
		return items;
	}

	/**
	 * Compute the remaining utility of an element, excluding the irrelevant
	 * items. This is the utility of all items that appear after the element in
	 * the projection sequence, minus the utilities of the irrelevant items. It
	 * is used by the irrelevant item pruning strategy (IPS) to tighten the
	 * PEU bound.
	 * @param chain the SU-Chain
	 * @param elementPosition the position of the element in the projection sequence
	 * @param irrelevant the set of irrelevant items to exclude
	 * @return the reduced remaining utility
	 */
	private int reducedRemainingUtility(SUChain chain, int elementPosition, Set<Integer> irrelevant) {
		if (irrelevant == null || irrelevant.isEmpty()) {
			return chain.elements[elementPosition].remainingUtility;
		}
		int utility = 0;
		SUChain.SeqElement[] elements = chain.elements;
		for (int i = elementPosition + 1; i < elements.length; i++) {
			if (!irrelevant.contains(Integer.valueOf(elements[i].item))) {
				utility += elements[i].utility;
			}
		}
		return utility;
	}

	/**
	 * Create a shallow copy of an SU-Chain so that each projected sequence owns
	 * its own utility-list without sharing it with its parent.
	 * @param chain the chain to copy
	 * @return a copy of the chain
	 */
	private SUChain copyChain(SUChain chain) {
		SUChain copy = new SUChain(new ArrayList<List<int[]>>());
		copy.sequenceUtility = chain.sequenceUtility;
		copy.itemsetCount = chain.itemsetCount;
		copy.elements = chain.elements;
		copy.itemsets = chain.itemsets;
		copy.itemsetItemMap = chain.itemsetItemMap;
		copy.itemOccurrences = chain.itemOccurrences;
		return copy;
	}

	/**
	 * Link the elements of a utility-list so that each element points to the
	 * next concatenation position, as described in the SU-Chain structure.
	 * @param list the utility-list to link
	 */
	private void linkUtilityList(List<SUElement> list) {
		for (int i = 0; i < list.size() - 1; i++) {
			list.get(i).next = list.get(i + 1).position;
		}
		if (!list.isEmpty()) {
			list.get(list.size() - 1).next = -1;
		}
	}

	/**
	 * Parse a line of the SPMF utility sequence database into a list of itemsets.
	 * @param thisLine the line to parse
	 * @return the list of itemsets, or null if the line is empty
	 */
	private List<List<int[]>> parseLine(String thisLine) {
		String[] tokens = thisLine.split(" ");
		List<List<int[]>> itemsets = new ArrayList<List<int[]>>();
		List<int[]> current = new ArrayList<int[]>();
		for (int i = 0; i < tokens.length - 3; i++) {
			String token = tokens[i];
			if (token.length() == 0) {
				continue;
			}
			if (token.equals("-1")) {
				if (!current.isEmpty()) {
					itemsets.add(current);
					current = new ArrayList<int[]>();
				}
			} else {
				int positionLeftBracket = token.indexOf('[');
				int positionRightBracket = token.indexOf(']');
				int item = Integer.parseInt(token.substring(0, positionLeftBracket));
				int utility = Integer.parseInt(token.substring(positionLeftBracket + 1, positionRightBracket));
				current.add(new int[] { item, utility });
			}
		}
		if (!current.isEmpty()) {
			itemsets.add(current);
		}
		return itemsets.isEmpty() ? null : itemsets;
	}

	/**
	 * Compute the utility of a q-sequence from its itemsets.
	 * @param itemsets the itemsets of the q-sequence
	 * @return the sequence utility
	 */
	private int sequenceUtility(List<List<int[]>> itemsets) {
		int utility = 0;
		for (List<int[]> itemset : itemsets) {
			for (int[] pu : itemset) {
				utility += pu[1];
			}
		}
		return utility;
	}

	/**
	 * Set the maximum pattern length
	 * @param maxPatternLength the maximum pattern length
	 */
	public void setMaxPatternLength(int maxPatternLength) {
		this.maxPatternLength = maxPatternLength;
	}

	/**
	 * Method to write a high-utility sequential pattern to the output file.
	 * @param prefix the pattern to be written to the output file
	 * @param prefixLength the pattern length
	 * @param utility the utility of the pattern
	 * @throws IOException if an error occurs while writing to file
	 */
	private void writeOut(int[] prefix, int prefixLength, int utility) throws IOException {
		patternCount++;
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < prefixLength; i++) {
			buffer.append(prefix[i]);
			buffer.append(' ');
		}
		buffer.append("-1 #UTIL: ");
		buffer.append(utility);
		writer.write(buffer.toString());
		writer.newLine();
	}

	/**
	 * Print statistics about the latest execution to System.out.
	 */
	public void printStats() {
		System.out.println("=============  SUCHain ALGORITHM - STATS ==========");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Max Memory ~ " + peakMemory + " MB");
		System.out.println(" High-utility sequential pattern count : " + patternCount);
		System.out.println("======================================================");
	}

	/** This class caches the PEU of a projected sequence for the LAS bound. */
	private static class ChainInfo {
		/** the projected sequence */
		SUChain chain;
		/** the PEU of the prefix in this sequence */
		int peuValue;

		ChainInfo(SUChain chain, int peuValue) {
			this.chain = chain;
			this.peuValue = peuValue;
		}
	}
}
