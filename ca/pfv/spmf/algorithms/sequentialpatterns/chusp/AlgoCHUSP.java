package ca.pfv.spmf.algorithms.sequentialpatterns.chusp;

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
import java.util.TreeMap;

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
 * This is an implementation of the "CHUSP" algorithm for mining Closed
 * High-Utility Sequential Patterns as described in the paper:<br/><br/>
 *
 *  Dinh, D.T., Fournier-Viger, P., Hong, S.-J.
 *  "On Mining Closed High-Utility Sequential Patterns". 2023.
 *
 * A CHUSP is a high-utility sequential pattern (HUSP) that has no proper HUSP
 * super-sequence with the same support. The algorithm uses a pattern-growth
 * approach on a lexicographic tree and a CHUS data structure that stores, for
 * each pattern, the projected q-sequences together with their utility and
 * remaining-utility information. The pruning strategies of the paper are the
 * sequence-weighted utilization (SWU) bound (Definition 20, applied to items
 * and 1-sequences, Algorithm 1 lines 3-4), the prefix extension utility
 * (PEU / CPMU) bound (Definition 21, Theorem 2, applied to a node before
 * expanding it, Algorithm 1 lines 5-6), the reduced sequence utility (RSU)
 * bound (Definition 22, Theorem 2, applied as the look-ahead filter that
 * discards I/S extension candidates, Algorithm 1 line 8), and the
 * minimum-support-based pruning (MSP, Theorem 3, Algorithm 1 line 12). In
 * growth() the RSU look-ahead bound of a candidate is computed as the prefix
 * extension utility (CPMU) of the prefix summed over the projected sequences;
 * candidates whose bound is below minUtility are discarded.
 * <br/>
 * The closedness of a pattern is checked against Definition 15: a pattern t is
 * a CHUSP if and only if t is an FHUSP and there is no FHUSP that is a proper
 * super-sequence of t and has the same support. Two mechanisms enforce it:
 * (1) the in-tree checkClosed procedure (Algorithm 1, line 14), which is
 * called whenever an FHUSP child t' is generated from a parent t and marks t
 * as not closed when their supports are equal (t' is a super-sequence with
 * the same support); and (2) a post-mining filter over all recorded FHUSPs
 * that guarantees the exact set defined by Definition 15. The post-mining
 * filter is necessary because utility is not anti-monotonic: the intermediate
 * node on the path from t to an equal-support FHUSP super-sequence t'' may
 * have a utility below the threshold, so it is never expanded and never
 * reaches checkClosed.
 *
 * The input uses the SPMF utility sequence format. The output uses the same
 * format as the USpan algorithm.
 *
 * @see CHUSPStructure
 * @author Philippe Fournier-Viger
 */
public class AlgoCHUSP {

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
	/** the minimum support as a count, computed from minSupport and the DB size */
	int minSupportCount = 0;

	/** max pattern length */
	int maxPatternLength = 1000;

	/** all FHUSPs found during mining: pattern string -> [utility, support] */
	Map<String, int[]> fhusps = null;
	/** patterns that are not closed: they have an FHUSP super-sequence with
	 *  the same support (the in-tree checkClosed procedure marks them) */
	Set<String> notClosed = null;

	/**
	 * Default constructor
	 */
	public AlgoCHUSP() {
	}

	/**
	 * Run the CHUSP algorithm
	 * @param input the input file path
	 * @param output the output file path
	 * @param minUtility the minimum utility threshold
	 * @param minSupport the minimum support threshold, as a percentage (1-100)
	 * @throws IOException exception if error while writing the file
	 */
	public void runAlgorithm(String input, String output, int minUtility, int minSupport) throws IOException {
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

		// save the thresholds
		this.minUtility = minUtility;

		// the set of all FHUSPs found during mining, keyed by pattern string,
		// and the set of patterns that are not closed
		fhusps = new TreeMap<String, int[]>();
		notClosed = new HashSet<String>();

		// ========== FIRST DATABASE SCAN: parse all sequences =========
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
		// the number of sequences in the original database (used for support)
		int sequenceCount = sequences.size();

		// ========== SWDC: sequence-weighted downward closure =========
		// Compute the SWU of each item in a single pass over the database and
		// remove all items whose SWU is below the threshold (Algorithm 1,
		// lines 3-4). The paper applies SWU in a single pass, not iteratively.
		// This is the downward-closure property of high-utility sequential
		// pattern mining (Apriori does not hold): if an item has SWU < minUtil,
		// no pattern containing it can reach the threshold (Theorem 1).
		Set<Integer> seen = new HashSet<Integer>();
		Map<Integer, Integer> currentSWU = new HashMap<Integer, Integer>();
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
		Set<Integer> toRemove = new HashSet<Integer>();
		for (Map.Entry<Integer, Integer> entry : currentSWU.entrySet()) {
			if (entry.getValue().intValue() < minUtility) {
				toRemove.add(entry.getKey());
			}
		}
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
		// the final SWU is held in currentSWU; used directly below (no copy,
		// memory optimization)

		// compute the minimum support count (integer arithmetic to avoid the
		// floating-point rounding error of ceil(minSupport/100.0 * n), e.g.
		// 29/100.0*100 = 29.000000000000004 would wrongly round up to 30)
		this.minSupportCount = (minSupport * sequenceCount + 99) / 100;

		// ========== Build the CHUS of each sequence =========
		final List<CHUSPStructure> database = new ArrayList<CHUSPStructure>(sequences.size());
		for (List<List<int[]>> itemsets : sequences) {
			if (!itemsets.isEmpty()) {
				database.add(new CHUSPStructure(itemsets));
			}
		}

		MemoryLogger.getInstance().checkMemory();

		// Mine the database. For each promising 1-sequence, build its CHUS and
		// grow the pattern recursively.
		List<Integer> promisingItems = new ArrayList<Integer>();
		for (Map.Entry<Integer, Integer> entry : currentSWU.entrySet()) {
			if (entry.getValue().intValue() >= minUtility) {
				promisingItems.add(entry.getKey());
			}
		}
		Collections.sort(promisingItems);

		for (Integer item : promisingItems) {
			int itemValue = item.intValue();
			List<CHUSPStructure> chus = buildFirstLevelCHUS(database, itemValue);
			if (chus.isEmpty()) {
				continue;
			}
			int[] uAndPeu = computeUtilityAndPEU(chus);
			int utility = uAndPeu[0];
			int peu = uAndPeu[1];

			patternBuffer[0] = itemValue;
			// MSP pruning (Theorem 3): a pattern whose support is below the
			// minimum support is not frequent, and neither are its
			// descendants (support is anti-monotonic). Such patterns can be
			// pruned from the search space without affecting the result.
			boolean frequent = chus.size() >= minSupportCount;
			if (utility >= minUtility && frequent) {
				// the 1-sequence is an FHUSP, record it as a CHUSP candidate
				fhusps.put(patternToString(patternBuffer, 1), new int[] { utility, chus.size() });
			}
			if (frequent && peu >= minUtility && 1 < maxPatternLength) {
				growth(patternBuffer, 1, 1, chus);
			}
		}

		MemoryLogger.getInstance().checkMemory();

		// Post-mining closedness filter (correctness backstop for the in-tree
		// checkClosed procedure). The in-tree procedure faithfully implements
		// Algorithm 1 but is not complete on its own, because utility is not
		// anti-monotonic: the intermediate node on the path from a pattern t
		// to an equal-support FHUSP super-sequence t'' may have a utility
		// below the threshold, so it is never expanded and never reaches
		// checkClosed. We therefore re-check Definition 15 over all recorded
		// FHUSPs: a pattern t is a CHUSP iff no FHUSP is a proper super-
		// sequence of t with the same support.
		for (Map.Entry<String, int[]> entry : fhusps.entrySet()) {
			String pattern = entry.getKey();
			if (notClosed.contains(pattern)) {
				continue;
			}
			int support = entry.getValue()[1];
			boolean closed = true;
			for (Map.Entry<String, int[]> other : fhusps.entrySet()) {
				if (other.getValue()[1] == support
						&& isProperSuperSequence(other.getKey(), pattern)) {
					closed = false;
					break;
				}
			}
			if (closed) {
				writeOut(pattern, entry.getValue()[0]);
			}
		}

		writer.close();
		peakMemory = MemoryLogger.getInstance().getMaxMemory();
		endTimestamp = System.currentTimeMillis();
	}

	/**
	 * This is the pattern-growth procedure of CHUSP. It performs a depth-first
	 * search of the lexicographic tree. For each candidate extension it applies
	 * the SWU, PEU (CPMU) and RSU look-ahead bounds and the MSP pruning strategy
	 * (Algorithm 1, lines 5-8 and 12), then evaluates the new pattern and, if
	 * it is an FHUSP, records it for the closedness check.
	 * @param prefix the buffer storing the current prefix
	 * @param prefixLength the current prefix length (buffer positions, including
	 *                     the -1 itemset separators)
	 * @param itemCount the number of items in the current prefix (real pattern
	 *                   length, used for the max-pattern-length limit)
	 * @param chus the CHUS of the prefix
	 * @throws IOException if an error occurs while writing to file
	 */
	private void growth(int[] prefix, int prefixLength, int itemCount, List<CHUSPStructure> chus) throws IOException {
		final Map<Integer, Integer> mapItemToIBound = new HashMap<Integer, Integer>();
		final Map<Integer, Integer> mapItemToSBound = new HashMap<Integer, Integer>();
		// Reused across the CHUS structures (memory optimization).
		Set<Integer> iCandidates = new HashSet<Integer>();
		Set<Integer> sCandidates = new HashSet<Integer>();

		// Collect the I-Concatenation and S-Concatenation candidate items and
		// compute their RSU look-ahead bound (Algorithm 1, line 8): the prefix
		// extension utility (CPMU) summed over the projected sequences; SWU was
		// already applied when the CHUS was built.
		int lastItem = prefix[prefixLength - 1];
		for (CHUSPStructure c : chus) {
			int peuValue = 0;
			iCandidates.clear();
			sCandidates.clear();
			for (int[] match : c.matches) {
				CHUSPStructure.CItem e = c.elements[match[0]];
				if (e.remainingUtility > 0) {
					peuValue = Math.max(peuValue, match[1] + e.remainingUtility);
				}
				for (Integer idx : c.itemsets.get(e.itemsetIndex)) {
					CHUSPStructure.CItem ce = c.elements[idx.intValue()];
					if (ce.item > lastItem) {
						iCandidates.add(Integer.valueOf(ce.item));
					}
				}
				for (int cc = e.itemsetIndex + 1; cc < c.itemsetCount; cc++) {
					for (Integer idx : c.itemsets.get(cc)) {
						sCandidates.add(Integer.valueOf(c.elements[idx.intValue()].item));
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
		}

		// =========== I-CONCATENATIONS ===========================
		for (Integer item : sortedCandidates(mapItemToIBound)) {
			int itemValue = item.intValue();
			List<CHUSPStructure> newChus = new ArrayList<CHUSPStructure>();
			int[] uAndPeu = buildCHUSI(chus, prefix, prefixLength, itemValue, newChus);
			if (newChus.isEmpty()) {
				continue;
			}
			int utility = uAndPeu[0];
			int peu = uAndPeu[1];
			patternBuffer[prefixLength] = itemValue;
			// MSP pruning (Theorem 3): a non-frequent pattern and its
			// descendants are not CHUSPs and can be pruned.
			boolean frequent = newChus.size() >= minSupportCount;
			if (utility >= minUtility && frequent) {
				// MSP pruning: the child must be frequent. Record it as an
				// FHUSP and run the in-tree checkClosed procedure.
				checkClosed(prefix, prefixLength, chus.size(),
						patternBuffer, prefixLength + 1, newChus.size(), utility);
			}
			if (frequent && peu >= minUtility && itemCount + 1 < maxPatternLength) {
				growth(patternBuffer, prefixLength + 1, itemCount + 1, newChus);
			}
		}

		// =========== S-CONCATENATIONS ===========================
		for (Integer item : sortedCandidates(mapItemToSBound)) {
			int itemValue = item.intValue();
			List<CHUSPStructure> newChus = new ArrayList<CHUSPStructure>();
			int[] uAndPeu = buildCHUSS(chus, prefix, prefixLength, itemValue, newChus);
			if (newChus.isEmpty()) {
				continue;
			}
			int utility = uAndPeu[0];
			int peu = uAndPeu[1];
			patternBuffer[prefixLength] = -1;
			patternBuffer[prefixLength + 1] = itemValue;
			boolean frequent = newChus.size() >= minSupportCount;
			if (utility >= minUtility && frequent) {
				checkClosed(prefix, prefixLength, chus.size(),
						patternBuffer, prefixLength + 2, newChus.size(), utility);
			}
			if (frequent && peu >= minUtility && itemCount + 1 < maxPatternLength) {
				growth(patternBuffer, prefixLength + 2, itemCount + 1, newChus);
			}
		}

		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * This is the in-tree checkClosed procedure of CHUSP (Algorithm 1, line
	 * 14). It is called when a child pattern t' (an FHUSP) is generated from a
	 * parent pattern t by I-Extension or S-Extension. The procedure records t'
	 * as an FHUSP and, if the support of t equals the support of t', marks t
	 * as not closed, because t' is a super-sequence of t with the same
	 * support (which breaks Definition 15).
	 * <p>
	 * Note: this direct-child check is faithful to Algorithm 1 but is not
	 * complete on its own, because utility is not anti-monotonic: the
	 * intermediate node on the path from t to an equal-support FHUSP super-
	 * sequence t'' may have a utility below the threshold, so it is never
	 * expanded and never reaches checkClosed. A post-mining closedness filter
	 * over all recorded FHUSPs is therefore applied at the end of the search
	 * (runAlgorithm) to guarantee the exact set defined by Definition 15.
	 * @param parentPrefix the buffer storing the parent pattern
	 * @param parentLength the length of the parent pattern
	 * @param parentSupport the support of the parent pattern
	 * @param childPrefix the buffer storing the child pattern
	 * @param childLength the length of the child pattern
	 * @param childSupport the support of the child pattern
	 * @param childUtility the utility of the child pattern
	 */
	private void checkClosed(int[] parentPrefix, int parentLength, int parentSupport,
			int[] childPrefix, int childLength, int childSupport, int childUtility) {
		// record the child as an FHUSP (a CHUSP candidate)
		fhusps.put(patternToString(childPrefix, childLength), new int[] { childUtility, childSupport });
		// if the parent and the child have the same support, the parent is not
		// closed: the child is a super-sequence with the same support
		if (parentSupport == childSupport) {
			notClosed.add(patternToString(parentPrefix, parentLength));
		}
	}

	/**
	 * Build the CHUS of the I-Concatenation of the prefix with an item.
	 * @param chus the CHUS of the prefix
	 * @param prefix the current prefix
	 * @param prefixLength the current prefix length
	 * @param item the item to append to the last itemset
	 * @param newChus the CHUS of the new pattern (output)
	 * @return an array {utility, PEU} of the new pattern
	 */
	private int[] buildCHUSI(List<CHUSPStructure> chus, int[] prefix, int prefixLength, int item, List<CHUSPStructure> newChus) {
		int newUtility = 0;
		int newPeu = 0;
		int lastItem = prefix[prefixLength - 1];
		for (CHUSPStructure c : chus) {
			// One match entry per ending q-item (UCS, Def. 16): keep the maximum
			// utility for each ending position, ordered by position.
			Map<Integer, Integer> utilByPos = new TreeMap<Integer, Integer>();
			for (int[] match : c.matches) {
				CHUSPStructure.CItem e = c.elements[match[0]];
				int idx = c.findItemInItemset(e.itemsetIndex, item);
				if (idx >= 0 && item > lastItem) {
					CHUSPStructure.CItem ne = c.elements[idx];
					int newUtil = match[1] + ne.utility;
					Integer prev = utilByPos.get(Integer.valueOf(idx));
					if (prev == null || newUtil > prev.intValue()) {
						utilByPos.put(Integer.valueOf(idx), Integer.valueOf(newUtil));
					}
				}
			}
			if (!utilByPos.isEmpty()) {
				List<int[]> newMatches = new ArrayList<int[]>(utilByPos.size());
				int maxUtil = 0;
				int maxPeu = 0;
				for (Map.Entry<Integer, Integer> en : utilByPos.entrySet()) {
					int idx = en.getKey().intValue();
					int newUtil = en.getValue().intValue();
					newMatches.add(new int[] { idx, newUtil });
					if (newUtil > maxUtil) {
						maxUtil = newUtil;
					}
					CHUSPStructure.CItem ne = c.elements[idx];
					if (ne.remainingUtility > 0 && newUtil + ne.remainingUtility > maxPeu) {
						maxPeu = newUtil + ne.remainingUtility;
					}
				}
				CHUSPStructure newC = copyCHUS(c);
				newC.matches = newMatches;
				newChus.add(newC);
				newUtility += maxUtil;
				newPeu += maxPeu;
			}
		}
		return new int[] { newUtility, newPeu };
	}

	/**
	 * Build the CHUS of the S-Concatenation of the prefix with an item.
	 * @param chus the CHUS of the prefix
	 * @param prefix the current prefix
	 * @param prefixLength the current prefix length
	 * @param item the item to append as a new itemset
	 * @param newChus the CHUS of the new pattern (output)
	 * @return an array {utility, PEU} of the new pattern
	 */
	private int[] buildCHUSS(List<CHUSPStructure> chus, int[] prefix, int prefixLength, int item, List<CHUSPStructure> newChus) {
		int newUtility = 0;
		int newPeu = 0;
		for (CHUSPStructure c : chus) {
			List<int[]> occurrences = c.itemOccurrences.get(Integer.valueOf(item));
			if (occurrences == null) {
				continue;
			}
			// One match entry per ending q-item (UCS, Def. 16): keep the maximum
			// utility for each ending position, ordered by position.
			Map<Integer, Integer> utilByPos = new TreeMap<Integer, Integer>();
			for (int[] match : c.matches) {
				CHUSPStructure.CItem e = c.elements[match[0]];
				for (int[] occ : occurrences) {
					if (occ[0] > e.itemsetIndex) {
						int idx = occ[1];
						CHUSPStructure.CItem ne = c.elements[idx];
						int newUtil = match[1] + ne.utility;
						Integer prev = utilByPos.get(Integer.valueOf(idx));
						if (prev == null || newUtil > prev.intValue()) {
							utilByPos.put(Integer.valueOf(idx), Integer.valueOf(newUtil));
						}
					}
				}
			}
			if (!utilByPos.isEmpty()) {
				List<int[]> newMatches = new ArrayList<int[]>(utilByPos.size());
				int maxUtil = 0;
				int maxPeu = 0;
				for (Map.Entry<Integer, Integer> en : utilByPos.entrySet()) {
					int idx = en.getKey().intValue();
					int newUtil = en.getValue().intValue();
					newMatches.add(new int[] { idx, newUtil });
					if (newUtil > maxUtil) {
						maxUtil = newUtil;
					}
					CHUSPStructure.CItem ne = c.elements[idx];
					if (ne.remainingUtility > 0 && newUtil + ne.remainingUtility > maxPeu) {
						maxPeu = newUtil + ne.remainingUtility;
					}
				}
				CHUSPStructure newC = copyCHUS(c);
				newC.matches = newMatches;
				newChus.add(newC);
				newUtility += maxUtil;
				newPeu += maxPeu;
			}
		}
		return new int[] { newUtility, newPeu };
	}

	/**
	 * Build the CHUS of a 1-sequence <item>.
	 * @param database the CHUS of the database
	 * @param item the item
	 * @return the CHUS of <item>
	 */
	private List<CHUSPStructure> buildFirstLevelCHUS(List<CHUSPStructure> database, int item) {
		List<CHUSPStructure> chus = new ArrayList<CHUSPStructure>();
		for (CHUSPStructure c : database) {
			List<int[]> occurrences = c.itemOccurrences.get(Integer.valueOf(item));
			if (occurrences == null || occurrences.isEmpty()) {
				continue;
			}
			List<int[]> matches = new ArrayList<int[]>(occurrences.size());
			for (int[] occ : occurrences) {
				int idx = occ[1];
				matches.add(new int[] { idx, c.elements[idx].utility });
			}
			CHUSPStructure newC = copyCHUS(c);
			newC.matches = matches;
			chus.add(newC);
		}
		return chus;
	}

	/**
	 * Compute the utility and the PEU of a pattern from its CHUS.
	 * @param chus the CHUS
	 * @return an array {utility, PEU}
	 */
	private int[] computeUtilityAndPEU(List<CHUSPStructure> chus) {
		int utility = 0;
		int peu = 0;
		for (CHUSPStructure c : chus) {
			int maxUtil = 0;
			int maxPeu = 0;
			for (int[] match : c.matches) {
				CHUSPStructure.CItem e = c.elements[match[0]];
				if (match[1] > maxUtil) {
					maxUtil = match[1];
				}
				if (e.remainingUtility > 0 && match[1] + e.remainingUtility > maxPeu) {
					maxPeu = match[1] + e.remainingUtility;
				}
			}
			utility += maxUtil;
			peu += maxPeu;
		}
		return new int[] { utility, peu };
	}

	/**
	 * Return the candidate items whose RSU look-ahead bound is at least the
	 * utility threshold, in increasing order.
	 * @param bounds the RSU look-ahead bound of each candidate item
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
	 * Create a shallow copy of a CHUS so that each projected sequence owns its
	 * own match list without sharing it with its parent.
	 * @param c the CHUS to copy
	 * @return a copy of the CHUS
	 */
	private CHUSPStructure copyCHUS(CHUSPStructure c) {
		CHUSPStructure copy = new CHUSPStructure();
		copy.sequenceUtility = c.sequenceUtility;
		copy.itemsetCount = c.itemsetCount;
		copy.elements = c.elements;
		copy.itemsets = c.itemsets;
		copy.itemsetItemMap = c.itemsetItemMap;
		copy.itemOccurrences = c.itemOccurrences;
		return copy;
	}

	/**
	 * Convert the pattern buffer into a canonical pattern string, using "-1"
	 * as the itemset separator.
	 * @param prefix the pattern buffer
	 * @param prefixLength the pattern length
	 * @return the canonical pattern string
	 */
	private String patternToString(int[] prefix, int prefixLength) {
		StringBuilder buffer = new StringBuilder();
		for (int i = 0; i < prefixLength; i++) {
			buffer.append(prefix[i]);
			buffer.append(' ');
		}
		return buffer.toString().trim();
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
	 * Check whether a pattern sup is a proper super-sequence of a pattern sub,
	 * i.e. sub is a subsequence of sup and the two are not identical. This
	 * covers both I-concat (an extra item in one of the same itemsets) and
	 * S-concat (an extra itemset), as required by Definition 15 of the CHUSP
	 * paper.
	 * @param sup the candidate super-sequence pattern string
	 * @param sub the pattern string to test
	 * @return true if sup is a proper super-sequence of sub
	 */
	private boolean isProperSuperSequence(String sup, String sub) {
		if (sup.equals(sub)) {
			return false;
		}
		int[][] supItemsets = parsePatternString(sup);
		int[][] subItemsets = parsePatternString(sub);
		int si = 0;
		for (int[] subItemset : subItemsets) {
			boolean found = false;
			while (si < supItemsets.length) {
				if (containsItemset(supItemsets[si], subItemset)) {
					found = true;
					si++;
					break;
				}
				si++;
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Parse a canonical pattern string into an array of itemsets.
	 * @param pattern the pattern string (e.g. "1 2 -1 3")
	 * @return an array of itemsets, each itemset being an array of items
	 */
	private int[][] parsePatternString(String pattern) {
		String[] tokens = pattern.split(" ");
		List<int[]> itemsets = new ArrayList<int[]>();
		List<Integer> current = new ArrayList<Integer>();
		for (String token : tokens) {
			if (token.length() == 0) {
				continue;
			}
			if (token.equals("-1")) {
				if (!current.isEmpty()) {
					int[] arr = new int[current.size()];
					for (int j = 0; j < arr.length; j++) {
						arr[j] = current.get(j).intValue();
					}
					itemsets.add(arr);
					current = new ArrayList<Integer>();
				}
			} else {
				current.add(Integer.valueOf(Integer.parseInt(token)));
			}
		}
		if (!current.isEmpty()) {
			int[] arr = new int[current.size()];
			for (int j = 0; j < arr.length; j++) {
				arr[j] = current.get(j).intValue();
			}
			itemsets.add(arr);
		}
		return itemsets.toArray(new int[itemsets.size()][]);
	}

	/**
	 * Check whether a sequence itemset contains all items of a pattern itemset.
	 * @param seqItemset the sequence itemset
	 * @param patItemset the pattern itemset
	 * @return true if the sequence itemset contains the pattern itemset
	 */
	private boolean containsItemset(int[] seqItemset, int[] patItemset) {
		for (int item : patItemset) {
			boolean present = false;
			for (int s : seqItemset) {
				if (s == item) {
					present = true;
					break;
				}
			}
			if (!present) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Set the maximum pattern length
	 * @param maxPatternLength the maximum pattern length
	 */
	public void setMaxPatternLength(int maxPatternLength) {
		this.maxPatternLength = maxPatternLength;
	}

	/**
	 * Method to write a closed high-utility sequential pattern to the output
	 * file.
	 * @param pattern the pattern string to be written to the output file
	 * @param utility the utility of the pattern
	 * @throws IOException if an error occurs while writing to file
	 */
	private void writeOut(String pattern, int utility) throws IOException {
		patternCount++;
		StringBuilder buffer = new StringBuilder();
		buffer.append(pattern);
		buffer.append(' ');
		buffer.append("-1 #UTIL: ");
		buffer.append(utility);
		writer.write(buffer.toString());
		writer.newLine();
	}

	/**
	 * Print statistics about the latest execution to System.out.
	 */
	public void printStats() {
		System.out.println("=============  CHUSP ALGORITHM - STATS ==========");
		System.out.println(" Total time ~ " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Max Memory ~ " + peakMemory + " MB");
		System.out.println(" Closed high-utility sequential pattern count : " + patternCount);
		System.out.println("======================================================");
	}
}
