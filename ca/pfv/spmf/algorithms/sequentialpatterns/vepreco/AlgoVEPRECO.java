package ca.pfv.spmf.algorithms.sequentialpatterns.vepreco;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

import ca.pfv.spmf.tools.MemoryLogger;

/*
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
 * Copyright (c) Chen Hui
 *
 * Do not remove copyright and license information.
 */
/**
 * VEPRECO: mines frequent sequential patterns using a vertical representation.
 * Patterns are written in SPMF format.
 *
 * @author Chen Hui
 */
public class AlgoVEPRECO {

	/** Start timestamp of the last run. */
	private long startTime;

	/** End timestamp of the last run. */
	private long endTime;

	/** Number of patterns written to the output. */
	private int patternCount;

	/** Minimum absolute support (sequence count). */
	private int minSup;

	/** Minimum support as a fraction in [0,1]. */
	private double minSupRelative;

	/** Minimum number of items per pattern (default: 0). */
	private int minimumPatternLength = 0;

	/** Maximum number of items per pattern (default: Integer.MAX_VALUE). */
	private int maximumPatternLength = Integer.MAX_VALUE;

	/** Writer used to output patterns. */
	private BufferedWriter writer;

	/** Frequent 1-item patterns in vertical form (sorted by item). */
	private TreeMap<Integer, DictMap> freq1;

	/** Scratch buffer used by array intersections. */
	private int[] intersectScratch = new int[64];

	/** Scratch buffer used by candidate assembly. */
	private int[] assembleScratch = new int[64];

	/** Reusable output array for cExtend (s-ext in [0], i-ext in [1]). */
	private final DictMap[] cExtendResult = new DictMap[2];

	/** Shared builder used to format output patterns. */
	private final StringBuilder patternSB = new StringBuilder(64);

	/** Maximum DFS depth supported by the reusable buffers. */
	private static final int MAX_DEPTH = 500;

	/** Check memory every N visited nodes (to reduce overhead). */
	private static final int MEMORY_CHECK_PERIOD = 2048;

	/** Number of visited nodes (for memory check throttling). */
	private long visitedNodeCount = 0;

	/** Reusable sNew arrays by DFS depth. */
	private int[][] depthSNew;

	/** Reusable iNewFromC arrays by DFS depth. */
	private int[][] depthINewFromC;

	/** Reusable iNewFromI arrays by DFS depth. */
	private int[][] depthINewFromI;

	/** Reusable merged iNew arrays by DFS depth. */
	private int[][] depthINew;

	/** Reusable buffers holding s-extension DictMaps by DFS depth. */
	private DictMap[][] depthSPats;

	/** Reusable buffers holding i-extension DictMaps by DFS depth. */
	private DictMap[][] depthIPats;

	/** Reusable candidate sets by DFS depth. */
	private CandidateSet[] depthCandidateSets;

	/** Reusable support counters for pruning to avoid allocating DictMaps. */
	private final SupportCounterSExt supportCounterS = new SupportCounterSExt();
	private final SupportCounterIExt supportCounterI = new SupportCounterIExt();
	private final SupportCounterCExt supportCounterC = new SupportCounterCExt();

	/**
	 * Sets the minimum number of items for a pattern to be output.
	 *
	 * @param minimumPatternLength the minimum length (>= 0)
	 * @return nothing
	 */
	public void setMinimumPatternLength(int minimumPatternLength) {
		this.minimumPatternLength = minimumPatternLength;
	}

	/**
	 * Sets the maximum number of items for a pattern to be output and explored.
	 *
	 * @param maximumPatternLength the maximum length (>= 1 recommended)
	 * @return nothing
	 */
	public void setMaximumPatternLength(int maximumPatternLength) {
		this.maximumPatternLength = maximumPatternLength;
	}

	/**
	 * Runs the algorithm on an input file and writes patterns to an output file.
	 *
	 * @param inputPath path to the input database (SPMF format)
	 * @param outputPath path to the output file
	 * @param minSupRelative minimum support as a fraction in [0,1]
	 * @return nothing
	 * @throws IOException if an I/O error occurs
	 */
	public void runAlgorithm(String inputPath, String outputPath, double minSupRelative) throws IOException {
		this.minSupRelative = minSupRelative;
		this.patternCount = 0;
		this.visitedNodeCount = 0;

		// Initialize memory logger and timers
		MemoryLogger.getInstance().reset();
		startTime = System.currentTimeMillis();

		// Open output
		writer = new BufferedWriter(new FileWriter(outputPath));
		writer.write("@FILETYPE=\"Frequent sequential patterns\"");
		writer.newLine();
		writer.write("@SOURCE=\"SPMF SOFTWARE https://philippe-fournier-viger.com/spmf/\"");
		writer.newLine();

		// Allocate reusable buffers for DFS
		allocateDepthBuffers();

		// Build frequent 1-items (vertical representation)
		freq1 = buildVerticalLength1(inputPath);

		// Output 1-item patterns if allowed
		if (minimumPatternLength <= 1 && maximumPatternLength >= 1) {
			for (DictMap dm : freq1.values()) {
				writePattern(dm);
			}
		}

		// Pre-compute level-1 candidate pools (pruning)
		computeCandidatesForLevel1();

		// Generate frequent 2-patterns and immediately do DFS (no buffering of all level-2 patterns)
		int[] sortedItems = toIntArrayFromTreeMap(freq1);
		int n = sortedItems.length;

		if (maximumPatternLength >= 2) {
			for (int ji = 0; ji < n; ji++) {
				int j = sortedItems[ji];
				DictMap jDm = freq1.get(j);

				for (int ki = 0; ki < n; ki++) {
					int k = sortedItems[ki];
					DictMap kDm = freq1.get(k);

					if (k > j) {
						// Try both s- and i-extensions
						cExtend(jDm, kDm, cExtendResult);
						DictMap newS = cExtendResult[0];
						DictMap newI = cExtendResult[1];

						if (newS != null && newS.support >= minSup) {
							if (minimumPatternLength <= 2) {
								writePattern(newS);
							}
							// DFS for patterns of length >= 3
							if (maximumPatternLength >= 3) {
								getCandidatesForLevel2Node(newS, depthCandidateSets[0]);
								searchPatterns(newS, depthCandidateSets[0].sCands, depthCandidateSets[0].sCandCount,
										depthCandidateSets[0].iCands, depthCandidateSets[0].iCandCount, 0);
							}
						}
						if (newI != null && newI.support >= minSup) {
							if (minimumPatternLength <= 2) {
								writePattern(newI);
							}
							// DFS for patterns of length >= 3
							if (maximumPatternLength >= 3) {
								getCandidatesForLevel2Node(newI, depthCandidateSets[0]);
								searchPatterns(newI, depthCandidateSets[0].sCands, depthCandidateSets[0].sCandCount,
										depthCandidateSets[0].iCands, depthCandidateSets[0].iCandCount, 0);
							}
						}
					} else {
						// Try s-extension only
						DictMap newS = sExtend(jDm, kDm);
						if (newS != null && newS.support >= minSup) {
							if (minimumPatternLength <= 2) {
								writePattern(newS);
							}
							// DFS for patterns of length >= 3
							if (maximumPatternLength >= 3) {
								getCandidatesForLevel2Node(newS, depthCandidateSets[0]);
								searchPatterns(newS, depthCandidateSets[0].sCands, depthCandidateSets[0].sCandCount,
										depthCandidateSets[0].iCands, depthCandidateSets[0].iCandCount, 0);
							}
						}
					}
				}
			}
		}

		MemoryLogger.getInstance().checkMemory();

		// Cleanup and finalize
		freeDepthBuffers();
		writer.close();

		endTime = System.currentTimeMillis();
		MemoryLogger.getInstance().checkMemory();
	}

	/**
	 * Allocates reusable buffers used by the DFS search.
	 *
	 * @param none
	 * @return nothing
	 */
	private void allocateDepthBuffers() {
		// Allocate arrays by depth
		depthSNew = new int[MAX_DEPTH][];
		depthINewFromC = new int[MAX_DEPTH][];
		depthINewFromI = new int[MAX_DEPTH][];
		depthINew = new int[MAX_DEPTH][];
		depthSPats = new DictMap[MAX_DEPTH][];
		depthIPats = new DictMap[MAX_DEPTH][];
		depthCandidateSets = new CandidateSet[MAX_DEPTH];

		// Initialize small capacities that grow on demand
		for (int d = 0; d < MAX_DEPTH; d++) {
			depthSNew[d] = new int[8];
			depthINewFromC[d] = new int[8];
			depthINewFromI[d] = new int[8];
			depthINew[d] = new int[8];
			depthSPats[d] = new DictMap[8];
			depthIPats[d] = new DictMap[8];
			depthCandidateSets[d] = new CandidateSet(8, 8);
		}
	}

	/**
	 * Releases references to reusable buffers after mining.
	 *
	 * @param none
	 * @return nothing
	 */
	private void freeDepthBuffers() {
		depthSNew = null;
		depthINewFromC = null;
		depthINewFromI = null;
		depthINew = null;
		depthSPats = null;
		depthIPats = null;
		depthCandidateSets = null;
	}

	/**
	 * Prints statistics about the last run.
	 *
	 * @param none
	 * @return nothing
	 */
	public void printStatistics() {
		System.out.println("==========  VEPRECO v.2.67 - STATISTICS ==========");
		System.out.println(" Total time           : " + (endTime - startTime) + " ms");
		System.out.println(" Pattern count        : " + patternCount);
		System.out.println(" Min support (rel)    : " + minSupRelative);
		System.out.println(" Min support (abs)    : " + minSup);
//		System.out.println(" Min pattern length   : " + minimumPatternLength);
//		System.out.println(" Max pattern length   : " + maximumPatternLength);
		System.out.println(" Max memory (MB)      : " + MemoryLogger.getInstance().getMaxMemory());
		System.out.println("============================================");
	}

	/**
	 * Builds the vertical representation for frequent 1-item patterns.
	 *
	 * @param inputPath path to the input database (SPMF format)
	 * @return a sorted map from item to its DictMap (only frequent items)
	 * @throws IOException if an I/O error occurs
	 */
	private TreeMap<Integer, DictMap> buildVerticalLength1(String inputPath) throws IOException {
		// Collect all item occurrences
		Map<Integer, DictMap> allItems = new HashMap<>();

		// Reuse a buffer for items inside the current itemset
		int[] itemScratch = new int[64];
		int itemScratchSize = 0;

		BufferedReader br = new BufferedReader(new FileReader(inputPath));
		String line;
		int sid = 0;

		while ((line = br.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '%' || line.charAt(0) == '@') {
				continue;
			}

			// Parse one sequence
			itemScratchSize = 0;
			int tid = 0;

			StringTokenizer st = new StringTokenizer(line);
			while (st.hasMoreTokens()) {
				int val = Integer.parseInt(st.nextToken());
				if (val == -2) {
					break;
				} else if (val == -1) {
					// End of itemset: store locations for each item
					if (itemScratchSize > 0) {
						for (int ix = 0; ix < itemScratchSize; ix++) {
							int item = itemScratch[ix];
							DictMap dm = allItems.get(item);
							if (dm == null) {
								dm = new DictMap(new int[] { item }, new int[] { 0, 1 }, 1);
								allItems.put(item, dm);
							}
							dm.addLocation(sid, tid);
						}
						tid++;
						itemScratchSize = 0;
					}
				} else {
					// Add an item to the current itemset buffer
					if (itemScratchSize == itemScratch.length) {
						itemScratch = Arrays.copyOf(itemScratch, itemScratchSize * 2);
					}
					itemScratch[itemScratchSize++] = val;
				}
			}
			sid++;
		}
		br.close();

		// Compute absolute minimum support
		minSup = (int) Math.ceil((minSupRelative * sid) - 0.000001);

		// Keep only frequent items
		TreeMap<Integer, DictMap> result = new TreeMap<>();
		for (Map.Entry<Integer, DictMap> e : allItems.entrySet()) {
			if (e.getValue().support >= minSup) {
				result.put(e.getKey(), e.getValue());
			}
		}
		return result;
	}

	/**
	 * Computes candidate extension items for each frequent 1-item pattern.
	 *
	 * @param none
	 * @return nothing
	 */
	private void computeCandidatesForLevel1() {
		// For each frequent item, test extensions with all frequent items
		// This version only counts support for pruning (no DictMap allocations).
		int[] sortedItems = toIntArrayFromTreeMap(freq1);

		for (int j : sortedItems) {
			DictMap jDm = freq1.get(j);
			for (int k : sortedItems) {
				DictMap kDm = freq1.get(k);

				if (k > j) {
					// C-extension case: both s and i
					supportCounterC.reset(kDm);
					jDm.locations.forEach(supportCounterC);
					if (supportCounterC.supportS >= minSup) {
						jDm.addSCandidate(k);
					}
					if (supportCounterC.supportI >= minSup) {
						jDm.addICandidate(k);
					}
				} else {
					// S-extension case
					int supS = sExtendSupport(jDm, kDm);
					if (supS >= minSup) {
						jDm.addSCandidate(k);
					}
				}
			}
		}

		// Freeze candidate arrays for fast intersection
		for (DictMap dm : freq1.values()) {
			dm.freezeCandidates();
		}
	}

	/**
	 * Counts the support of the s-extension of P by item F.
	 * This is used only for pruning (no location lists are built).
	 *
	 * @param P the current pattern
	 * @param F the 1-item pattern to append
	 * @return the support count
	 */
	private int sExtendSupport(DictMap P, DictMap F) {
		supportCounterS.reset(F);
		P.locations.forEach(supportCounterS);
		return supportCounterS.support;
	}

	/**
	 * Counts the support of the i-extension of P by item F.
	 * This is used only for pruning (no location lists are built).
	 *
	 * @param P the current pattern
	 * @param F the 1-item pattern to append
	 * @return the support count
	 */
	@SuppressWarnings("unused")
	private int iExtendSupport(DictMap P, DictMap F) {
		supportCounterI.reset(F);
		P.locations.forEach(supportCounterI);
		return supportCounterI.support;
	}

	/**
	 * Computes the candidate pools for a level-2 node.
	 *
	 * @param N a level-2 pattern node
	 * @param cs a reusable CandidateSet to fill
	 * @return nothing
	 */
	private void getCandidatesForLevel2Node(DictMap N, CandidateSet cs) {
		int lastItem = N.getLastItem();
		int prevItem = N.getSecondToLastItem();

		DictMap lastDm = freq1.get(lastItem);
		DictMap prevDm = (prevItem >= 0) ? freq1.get(prevItem) : null;

		buildCandidateSet(prevDm, lastDm, N.isLastExtensionI(), cs);
	}

	/**
	 * Builds s- and i-candidate pools from two 1-item DictMaps.
	 *
	 * @param prevDm DictMap of the second-to-last item (may be null)
	 * @param lastDm DictMap of the last item (must not be null to produce candidates)
	 * @param lastWasIExt true if the last extension was an i-extension
	 * @param cs a reusable CandidateSet to fill
	 * @return nothing
	 */
	private void buildCandidateSet(DictMap prevDm, DictMap lastDm, boolean lastWasIExt, CandidateSet cs) {
		// Reset counts
		cs.sCandCount = 0;
		cs.iCandCount = 0;
		if (lastDm == null) {
			return;
		}

		if (prevDm != null) {
			// Build s-pool by intersecting s-candidates
			int sLen = intersectArraysInto(prevDm.sCandidates, prevDm.sCandidates.length,
					lastDm.sCandidates, lastDm.sCandidates.length, cs.sCands, cs.sCands.length);
			if (sLen > cs.sCands.length) {
				cs.sCands = new int[sLen];
				intersectArraysInto(prevDm.sCandidates, prevDm.sCandidates.length,
						lastDm.sCandidates, lastDm.sCandidates.length, cs.sCands, cs.sCands.length);
			}
			cs.sCandCount = sLen;

			// Build i-pool by intersecting i-candidates (depends on last extension type)
			int[] iSrc1 = lastWasIExt ? prevDm.iCandidates : prevDm.sCandidates;
			int iLen = intersectArraysInto(iSrc1, iSrc1.length,
					lastDm.iCandidates, lastDm.iCandidates.length, cs.iCands, cs.iCands.length);
			if (iLen > cs.iCands.length) {
				cs.iCands = new int[iLen];
				intersectArraysInto(iSrc1, iSrc1.length,
						lastDm.iCandidates, lastDm.iCandidates.length, cs.iCands, cs.iCands.length);
			}
			cs.iCandCount = iLen;

		} else {
			// No previous item: start from lastDm's candidates
			int sLen = lastDm.sCandidates.length;
			if (sLen > cs.sCands.length) cs.sCands = new int[sLen];
			System.arraycopy(lastDm.sCandidates, 0, cs.sCands, 0, sLen);
			cs.sCandCount = sLen;

			int iLen = lastDm.iCandidates.length;
			if (iLen > cs.iCands.length) cs.iCands = new int[iLen];
			System.arraycopy(lastDm.iCandidates, 0, cs.iCands, 0, iLen);
			cs.iCandCount = iLen;
		}

		// Ensure "C-type" items appear in both pools
		assembleCandidateSetInPlace(cs);
	}

	/**
	 * Rewrites candidate pools so that items in both pools are kept in both.
	 *
	 * @param cs a CandidateSet to rewrite in-place
	 * @return nothing
	 */
	private void assembleCandidateSetInPlace(CandidateSet cs) {
		int sCandLen = cs.sCandCount;
		int iCandLen = cs.iCandCount;

		// Copy raw pools to scratch buffers
		if (intersectScratch.length < sCandLen) intersectScratch = new int[sCandLen * 2];
		if (assembleScratch.length < iCandLen) assembleScratch = new int[iCandLen * 2];
		System.arraycopy(cs.sCands, 0, intersectScratch, 0, sCandLen);
		System.arraycopy(cs.iCands, 0, assembleScratch, 0, iCandLen);

		// Reset output pools
		cs.sCandCount = 0;
		cs.iCandCount = 0;

		// Merge-scan to split into s-only, i-only, and both
		int si = 0, ii = 0;
		while (si < sCandLen && ii < iCandLen) {
			if (intersectScratch[si] == assembleScratch[ii]) {
				cs.sCands[cs.sCandCount++] = intersectScratch[si];
				cs.iCands[cs.iCandCount++] = intersectScratch[si];
				si++;
				ii++;
			} else if (intersectScratch[si] < assembleScratch[ii]) {
				cs.sCands[cs.sCandCount++] = intersectScratch[si++];
			} else {
				cs.iCands[cs.iCandCount++] = assembleScratch[ii++];
			}
		}
		while (si < sCandLen) cs.sCands[cs.sCandCount++] = intersectScratch[si++];
		while (ii < iCandLen) cs.iCands[cs.iCandCount++] = assembleScratch[ii++];
	}

	/**
	 * Performs DFS extensions of a pattern and outputs frequent child patterns.
	 *
	 * @param N current pattern node to extend
	 * @param sCandidates sorted s-candidate items
	 * @param sCandCount number of valid values in sCandidates
	 * @param iCandidates sorted i-candidate items
	 * @param iCandCount number of valid values in iCandidates
	 * @param depth current DFS depth (for reusable buffers)
	 * @return nothing
	 * @throws IOException if an I/O error occurs
	 */
	private void searchPatterns(DictMap N, int[] sCandidates, int sCandCount, int[] iCandidates, int iCandCount,
			int depth) throws IOException {

		// Throttle memory checks to reduce overhead
		if ((++visitedNodeCount & (MEMORY_CHECK_PERIOD - 1)) == 0) {
			MemoryLogger.getInstance().checkMemory();
		}

		// Prune if the next length would exceed the maximum
		int childLength = N.patItems.length + 1;
		if (childLength > maximumPatternLength) {
			return;
		}

		// Prevent depth overflow
		int nextDepth = depth + 1;
		if (nextDepth >= MAX_DEPTH) {
			return;
		}

		// Ensure depth buffers are large enough
		if (depthSNew[depth].length < sCandCount) depthSNew[depth] = new int[sCandCount * 2];
		if (depthINewFromC[depth].length < sCandCount) depthINewFromC[depth] = new int[sCandCount * 2];
		if (depthINewFromI[depth].length < iCandCount) depthINewFromI[depth] = new int[iCandCount * 2];
		if (depthSPats[depth].length < sCandCount) depthSPats[depth] = new DictMap[sCandCount * 2];
		if (depthIPats[depth].length < iCandCount) depthIPats[depth] = new DictMap[iCandCount * 2];

		int[] sNew = depthSNew[depth];
		int[] iNewFromC = depthINewFromC[depth];
		int[] iNewFromI = depthINewFromI[depth];
		DictMap[] sPats = depthSPats[depth];
		DictMap[] iPats = depthIPats[depth];

		int sNewCount = 0;
		int iNewFromCCount = 0;
		int iNewFromICount = 0;
		int sPatsCount = 0;
		int iPatsCount = 0;

		// Extend with s-candidates (handling C-type items)
		int iPtr = 0;
		for (int si = 0; si < sCandCount; si++) {
			int item = sCandidates[si];
			DictMap F = freq1.get(item);
			if (F == null) continue;

			while (iPtr < iCandCount && iCandidates[iPtr] < item) iPtr++;
			boolean isCType = (iPtr < iCandCount && iCandidates[iPtr] == item);

			if (isCType) {
				cExtend(N, F, cExtendResult);
				DictMap Ps = cExtendResult[0];
				DictMap Pi = cExtendResult[1];

				if (Ps != null && Ps.support >= minSup) {
					if (childLength >= minimumPatternLength) writePattern(Ps);
					sPats[sPatsCount++] = Ps;
					sNew[sNewCount++] = item;
				}
				if (Pi != null && Pi.support >= minSup) {
					if (childLength >= minimumPatternLength) writePattern(Pi);
					iPats[iPatsCount++] = Pi;
					iNewFromC[iNewFromCCount++] = item;
				}
			} else {
				DictMap Ps = sExtend(N, F);
				if (Ps != null && Ps.support >= minSup) {
					if (childLength >= minimumPatternLength) writePattern(Ps);
					sPats[sPatsCount++] = Ps;
					sNew[sNewCount++] = item;
				}
			}
		}

		// Extend with i-candidates not handled above
		int sPtr = 0;
		for (int ii = 0; ii < iCandCount; ii++) {
			int item = iCandidates[ii];
			while (sPtr < sCandCount && sCandidates[sPtr] < item) sPtr++;
			if (sPtr < sCandCount && sCandidates[sPtr] == item) continue;

			DictMap F = freq1.get(item);
			if (F == null) continue;

			DictMap Pi = iExtend(N, F);
			if (Pi != null && Pi.support >= minSup) {
				if (childLength >= minimumPatternLength) writePattern(Pi);
				iPats[iPatsCount++] = Pi;
				iNewFromI[iNewFromICount++] = item;
			}
		}

		// Merge iNewFromC and iNewFromI into iNew
		int iNewCount = iNewFromCCount + iNewFromICount;
		if (depthINew[depth].length < iNewCount) depthINew[depth] = new int[iNewCount * 2];
		int[] iNew = depthINew[depth];
		{
			int ci = 0, ii2 = 0, pos = 0;
			while (ci < iNewFromCCount && ii2 < iNewFromICount) {
				if (iNewFromC[ci] <= iNewFromI[ii2]) iNew[pos++] = iNewFromC[ci++];
				else iNew[pos++] = iNewFromI[ii2++];
			}
			while (ci < iNewFromCCount) iNew[pos++] = iNewFromC[ci++];
			while (ii2 < iNewFromICount) iNew[pos++] = iNewFromI[ii2++];
		}

		// Recurse to next depth
		for (int pi = 0; pi < sPatsCount; pi++) {
			DictMap Pnew = sPats[pi];
			int lastItem = Pnew.getLastItem();

			int poolStart = upperBound(sNew, sNewCount, lastItem);
			int poolForICount = sNewCount - poolStart;

			CandidateSet cs = depthCandidateSets[nextDepth];
			getCandidatesFromParent(Pnew, sNew, sNewCount, sNew, poolStart, poolForICount, cs);
			searchPatterns(Pnew, cs.sCands, cs.sCandCount, cs.iCands, cs.iCandCount, nextDepth);

			// Clear references to help GC and reduce peak memory
			sPats[pi] = null;
		}

		for (int pi = 0; pi < iPatsCount; pi++) {
			DictMap Pnew = iPats[pi];
			int lastItem = Pnew.getLastItem();

			int poolStart = upperBound(iNew, iNewCount, lastItem);
			int poolForICount = iNewCount - poolStart;

			CandidateSet cs = depthCandidateSets[nextDepth];
			getCandidatesFromParent(Pnew, sNew, sNewCount, iNew, poolStart, poolForICount, cs);
			searchPatterns(Pnew, cs.sCands, cs.sCandCount, cs.iCands, cs.iCandCount, nextDepth);

			// Clear references to help GC and reduce peak memory
			iPats[pi] = null;
		}
	}

	/**
	 * Computes the s-extension of a pattern by appending an item as a new itemset.
	 *
	 * @param P the current pattern
	 * @param F the 1-item pattern to append
	 * @return the extended pattern, or null if support is 0
	 */
	private DictMap sExtend(DictMap P, DictMap F) {
		// Create the new pattern object
		int fItem = F.patItems[0];
		DictMap newP = new DictMap(P, fItem, false);

		// Join location lists to compute occurrences
		P.locations.forEach((sid, tidsp) -> {
			IntList tidsF = F.locations.get(sid);
			if (tidsF == null) return;

			int minTid = tidsp.first() + 1;
			if (tidsF.last() < minTid) return;

			IntList destList = null;
			for (int fi = 0; fi < tidsF.size; fi++) {
				int tf = tidsF.get(fi);
				if (tf >= minTid) {
					if (destList == null) {
						newP.support++;
						destList = newP.locations.getOrCreate(sid);
					}
					destList.add(tf);
				}
			}
		});

		return newP.support > 0 ? newP : null;
	}

	/**
	 * Computes the i-extension of a pattern by appending an item to the last itemset.
	 *
	 * @param P the current pattern
	 * @param F the 1-item pattern to append
	 * @return the extended pattern, or null if support is 0
	 */
	private DictMap iExtend(DictMap P, DictMap F) {
		// Create the new pattern object
		int fItem = F.patItems[0];
		DictMap newP = new DictMap(P, fItem, true);

		// Intersect tids to find same-itemset co-occurrences
		P.locations.forEach((sid, tidsp) -> {
			IntList tidsF = F.locations.get(sid);
			if (tidsF == null) return;

			int ip = 0, iF = 0;
			IntList destList = null;
			while (ip < tidsp.size && iF < tidsF.size) {
				int tp = tidsp.get(ip);
				int tf = tidsF.get(iF);
				if (tp == tf) {
					if (destList == null) {
						newP.support++;
						destList = newP.locations.getOrCreate(sid);
					}
					destList.add(tf);
					ip++;
					iF++;
				} else if (tp < tf) {
					ip++;
				} else {
					iF++;
				}
			}
		});

		return newP.support > 0 ? newP : null;
	}

	/**
	 * Computes both s- and i-extensions in one pass.
	 *
	 * @param P the current pattern
	 * @param F the 1-item pattern to append
	 * @param result output array of size 2 (s-ext at [0], i-ext at [1])
	 * @return nothing
	 */
	private void cExtend(DictMap P, DictMap F, DictMap[] result) {
		// Create both candidate patterns
		int fItem = F.patItems[0];
		DictMap newS = new DictMap(P, fItem, false);
		DictMap newI = new DictMap(P, fItem, true);

		// Compute i- and s-occurrences
		P.locations.forEach((sid, tidsp) -> {
			IntList tidsF = F.locations.get(sid);
			if (tidsF == null) return;

			int minTidS = tidsp.first() + 1;

			// i-extension: equal tids
			{
				int ip = 0, iF2 = 0;
				IntList iDestList = null;
				while (ip < tidsp.size && iF2 < tidsF.size) {
					int tp = tidsp.get(ip);
					int tf = tidsF.get(iF2);
					if (tp == tf) {
						if (iDestList == null) {
							newI.support++;
							iDestList = newI.locations.getOrCreate(sid);
						}
						iDestList.add(tf);
						ip++;
						iF2++;
					} else if (tp < tf) {
						ip++;
					} else {
						iF2++;
					}
				}
			}

			// s-extension: tids after the last occurrence
			if (tidsF.last() >= minTidS) {
				IntList sDestList = null;
				for (int fi = 0; fi < tidsF.size; fi++) {
					int tf = tidsF.get(fi);
					if (tf >= minTidS) {
						if (sDestList == null) {
							newS.support++;
							sDestList = newS.locations.getOrCreate(sid);
						}
						sDestList.add(tf);
					}
				}
			}
		});

		result[0] = newS.support > 0 ? newS : null;
		result[1] = newI.support > 0 ? newI : null;
	}

	/**
	 * Returns the first index where arr[index] is strictly greater than a threshold.
	 *
	 * @param arr sorted array
	 * @param len number of valid values in arr
	 * @param threshold threshold value
	 * @return the upper bound index in [0,len]
	 */
	private int upperBound(int[] arr, int len, int threshold) {
		int lo = 0, hi = len;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (arr[mid] <= threshold) lo = mid + 1;
			else hi = mid;
		}
		return lo;
	}

	/**
	 * Converts the keys of a TreeMap to a sorted int array.
	 *
	 * @param map the input TreeMap
	 * @return a sorted array of keys
	 */
	private int[] toIntArrayFromTreeMap(TreeMap<Integer, ?> map) {
		int[] arr = new int[map.size()];
		int i = 0;
		for (int key : map.keySet()) {
			arr[i++] = key;
		}
		return arr;
	}

	/**
	 * Intersects two sorted arrays into an output buffer.
	 *
	 * @param a first sorted array
	 * @param aLen number of valid values in a
	 * @param b second sorted array
	 * @param bLen number of valid values in b
	 * @param out output buffer
	 * @param outLen capacity of out
	 * @return the intersection size (may exceed outLen if out is too small)
	 */
	private int intersectArraysInto(int[] a, int aLen, int[] b, int bLen, int[] out, int outLen) {
		int i = 0, j = 0, pos = 0;
		while (i < aLen && j < bLen) {
			if (a[i] == b[j]) {
				if (pos < outLen) out[pos] = a[i];
				pos++;
				i++;
				j++;
			} else if (a[i] < b[j]) {
				i++;
			} else {
				j++;
			}
		}
		return pos;
	}

	/**
	 * Intersects a slice of a sorted array with another sorted array into an output buffer.
	 *
	 * @param a first array
	 * @param aStart starting index of the slice
	 * @param aLen length of the slice
	 * @param b second sorted array
	 * @param bLen number of valid values in b
	 * @param out output buffer
	 * @param outLen capacity of out
	 * @return the intersection size (may exceed outLen if out is too small)
	 */
	private int intersectArraysSliceInto(int[] a, int aStart, int aLen, int[] b, int bLen, int[] out, int outLen) {
		int i = aStart, iEnd = aStart + aLen;
		int j = 0, pos = 0;
		while (i < iEnd && j < bLen) {
			if (a[i] == b[j]) {
				if (pos < outLen) out[pos] = a[i];
				pos++;
				i++;
				j++;
			} else if (a[i] < b[j]) {
				i++;
			} else {
				j++;
			}
		}
		return pos;
	}

	/**
	 * Computes candidate pools for a node using the parent pools and last item candidates.
	 *
	 * @param N the current pattern node
	 * @param parentSNew sorted items frequent as s-extensions at the parent
	 * @param parentSNewCount number of valid values in parentSNew
	 * @param parentINew sorted items frequent as i-extensions at the parent
	 * @param iOffset starting index for the valid i-slice in parentINew
	 * @param iCount number of valid values in the i-slice
	 * @param cs a reusable CandidateSet to fill
	 * @return nothing
	 */
	private void getCandidatesFromParent(DictMap N, int[] parentSNew, int parentSNewCount, int[] parentINew,
			int iOffset, int iCount, CandidateSet cs) {
		// Reset counts
		cs.sCandCount = 0;
		cs.iCandCount = 0;

		// Find the last item and its candidate arrays
		int lastItem = N.getLastItem();
		DictMap lastDm = freq1.get(lastItem);
		if (lastDm == null) return;

		// Build s-pool (no allocations)
		int sLen = intersectArraysInto(parentSNew, parentSNewCount,
				lastDm.sCandidates, lastDm.sCandidates.length, cs.sCands, cs.sCands.length);
		if (sLen > cs.sCands.length) {
			cs.sCands = new int[sLen];
			sLen = intersectArraysInto(parentSNew, parentSNewCount,
					lastDm.sCandidates, lastDm.sCandidates.length, cs.sCands, cs.sCands.length);
		}
		cs.sCandCount = sLen;

		// Build i-pool (no allocations; depends on last extension type)
		int iLen;
		if (N.isLastExtensionI()) {
			iLen = intersectArraysSliceInto(parentINew, iOffset, iCount,
					lastDm.iCandidates, lastDm.iCandidates.length, cs.iCands, cs.iCands.length);
			if (iLen > cs.iCands.length) {
				cs.iCands = new int[iLen];
				iLen = intersectArraysSliceInto(parentINew, iOffset, iCount,
						lastDm.iCandidates, lastDm.iCandidates.length, cs.iCands, cs.iCands.length);
			}
		} else {
			iLen = intersectArraysInto(parentSNew, parentSNewCount,
					lastDm.iCandidates, lastDm.iCandidates.length, cs.iCands, cs.iCands.length);
			if (iLen > cs.iCands.length) {
				cs.iCands = new int[iLen];
				iLen = intersectArraysInto(parentSNew, parentSNewCount,
						lastDm.iCandidates, lastDm.iCandidates.length, cs.iCands, cs.iCands.length);
			}
		}
		cs.iCandCount = iLen;

		// Ensure C-type items are in both pools
		assembleCandidateSetInPlace(cs);
	}

	/**
	 * Writes one pattern to the output file in SPMF format.
	 *
	 * @param dm the pattern to write
	 * @return nothing
	 * @throws IOException if an I/O error occurs
	 */
	private void writePattern(DictMap dm) throws IOException {
		// Format "pattern #SUP: x"
		patternSB.setLength(0);
		dm.appendPatternTo(patternSB);
		patternSB.append(" #SUP: ");
		patternSB.append(dm.support);

		// Write to file and update counter
		writer.write(patternSB.toString());
		writer.newLine();
		patternCount++;
	}

	/**
	 * Support counter for s-extension pruning (no allocations).
	 */
	private static final class SupportCounterSExt implements IntIntArrayMap.EntryConsumer {

		/** The extension item DictMap. */
		private DictMap F;

		/** Counted support. */
		int support;

		/**
		 * Reset for a new F.
		 *
		 * @param F the extension DictMap
		 */
		void reset(DictMap F) {
			this.F = F;
			this.support = 0;
		}

		@Override
		public void accept(int sid, IntList tidsp) {
			IntList tidsF = F.locations.get(sid);
			if (tidsF == null) return;

			int minTid = tidsp.first() + 1;
			if (tidsF.last() >= minTid) {
				support++;
			}
		}
	}

	/**
	 * Support counter for i-extension pruning (no allocations).
	 */
	private static final class SupportCounterIExt implements IntIntArrayMap.EntryConsumer {

		/** The extension item DictMap. */
		private DictMap F;

		/** Counted support. */
		int support;

		/**
		 * Reset for a new F.
		 *
		 * @param F the extension DictMap
		 */
		void reset(DictMap F) {
			this.F = F;
			this.support = 0;
		}

		@Override
		public void accept(int sid, IntList tidsp) {
			IntList tidsF = F.locations.get(sid);
			if (tidsF == null) return;

			int ip = 0, iF = 0;
			while (ip < tidsp.size && iF < tidsF.size) {
				int tp = tidsp.get(ip);
				int tf = tidsF.get(iF);
				if (tp == tf) {
					support++;
					return;
				} else if (tp < tf) {
					ip++;
				} else {
					iF++;
				}
			}
		}
	}

	/**
	 * Support counter for c-extension pruning (no allocations).
	 */
	private static final class SupportCounterCExt implements IntIntArrayMap.EntryConsumer {

		/** The extension item DictMap. */
		private DictMap F;

		/** Counted support for s-extension. */
		int supportS;

		/** Counted support for i-extension. */
		int supportI;

		/**
		 * Reset for a new F.
		 *
		 * @param F the extension DictMap
		 */
		void reset(DictMap F) {
			this.F = F;
			this.supportS = 0;
			this.supportI = 0;
		}

		@Override
		public void accept(int sid, IntList tidsp) {
			IntList tidsF = F.locations.get(sid);
			if (tidsF == null) return;

			// s-extension support: check if some tf >= tidsp.first() + 1
			int minTid = tidsp.first() + 1;
			if (tidsF.last() >= minTid) {
				supportS++;
			}

			// i-extension support: check if tids intersect
			int ip = 0, iF = 0;
			while (ip < tidsp.size && iF < tidsF.size) {
				int tp = tidsp.get(ip);
				int tf = tidsF.get(iF);
				if (tp == tf) {
					supportI++;
					return;
				} else if (tp < tf) {
					ip++;
				} else {
					iF++;
				}
			}
		}
	}
}