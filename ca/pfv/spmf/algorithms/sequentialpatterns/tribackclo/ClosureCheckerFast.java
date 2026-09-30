package ca.pfv.spmf.algorithms.sequentialpatterns.tribackclo;

import java.util.Arrays;
/* This file is copyright (c) 2026 Nabil Azizi et al.
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
* This code was provided by the authors under the MIT-LICENSE.
* 
* Do not remove copyright and license information.
*/
/**
 * Exact envelope-based closure checker using stamp intersections.
 *
 * Java implementation used for the SPMF-compatible public release.
 *
 * Key optimizations: 1. No mutable.BitSet - uses stamp arrays 2. No pattern
 * reconstruction - checks windows directly 3. No allSupport() calls - just
 * window intersection 4. No per-SID envelope allocation - uses flat buffers
 * 
 * @author Nabil Azizi, Makhlouf Ledmi, Abdeldjalil Ledmi, Mohammed El Habib
 *         Souidi, Mohamed Boussalem Aboubekeur Hamdi-Cherif
 */
public class ClosureCheckerFast {

	private final int[][][] sequences;
	private final int[][] singletonSeqs;
	private final boolean isSingleton;
	private final int[] seenStamp;
	private final int[] candidates;
	private int stampCounter = 0;
	private final int stampStride;
	private final int[] globalSupport;
	private final int minsup;

	// Reusable buffer for extracting itemsets from flat pattern buffer (zero
	// allocation)
	private int[] itemsetBuf = new int[64];
	private int[] itemsetOffsetsBuf = new int[64]; // [start0, end0, start1, end1, ...]

	public ClosureCheckerFast(int[][][] sequences, int[][] singletonSeqs, boolean isSingleton, int maxItem,
			int[] globalSupport, int minsup) {
		this.sequences = sequences;
		this.singletonSeqs = singletonSeqs;
		this.isSingleton = isSingleton;
		this.seenStamp = new int[maxItem + 1];
		this.candidates = new int[maxItem + 1];
		this.globalSupport = globalSupport;
		this.minsup = minsup;
		this.stampStride = Math.max(4, sequences.length + 2);
	}

	private int nextStampBase(int nSids) {
		if (stampCounter > 2000000000 - stampStride) {
			Arrays.fill(seenStamp, 0);
			stampCounter = 0;
		}
		stampCounter += stampStride;
		return stampCounter;
	}

	/**
	 * Returns true if pattern is closed (no witnesses found).
	 * 
	 * @param sids     SID array
	 * @param nSids    Number of SIDs
	 * @param pattern  Pattern itemsets (each is sorted)
	 * @param k        Pattern length
	 * @param firstBuf Flat buffer of first positions [sid0_e0, sid0_e1, ...,
	 *                 sid1_e0, ...]
	 * @param lastBuf  Flat buffer of last positions
	 * @return true if closed, false otherwise
	 */
	public boolean isClosedUsingEnvelopes(int[] sids, int nSids, int[][] pattern, int k, int[] firstBuf,
			int[] lastBuf) {
		if (k == 0)
			return true;

		// 1) Backward S-prepend witness: item appearing before E0 in all SIDs
		if (hasCommonItemInPrefixRange(sids, nSids, k, lastBuf)) {
			return false;
		}

		// 2) Backward I-augment into the first element (skip for single-itemset
		// datasets)
		if (!isSingleton) {
			if (hasCommonItemIPrepend(sids, nSids, pattern[0], k, firstBuf, lastBuf)) {
				return false;
			}
		}

		// 3) Middle S-insert in any gap - Wide Window: [firstPos(g)+1, lastPos(g+1))
		for (int g = 0; g < k - 1; g++) {
			if (hasCommonItemInGapRange(sids, nSids, k, firstBuf, lastBuf, g)) {
				return false;
			}
		}

		// 4) Middle I-augment into any element (skip for singletons)
		if (!isSingleton) {
			for (int i = 0; i < k; i++) {
				if (hasCommonItemIInsert(sids, nSids, pattern[i], k, firstBuf, lastBuf, i)) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Zero-allocation overload: accepts flat pattern buffer with pre-computed
	 * offsets. Uses direct indexing - no buffer copies at all.
	 */
	public boolean isClosedUsingEnvelopesFlat(int[] sids, int nSids, int[] patternBuf, int[] offsets, int k,
			int[] firstBuf, int[] lastBuf) {
		if (k == 0)
			return true;

		// 1) Backward S-prepend witness
		if (hasCommonItemInPrefixRange(sids, nSids, k, lastBuf)) {
			return false;
		}

		// 2) Backward I-augment into the first element (skip for single-itemset
		// datasets)
		if (!isSingleton) {
			int e0Start = offsets[0];
			int e0End = offsets[1];
			if (hasCommonItemIPrependDirect(sids, nSids, patternBuf, e0Start, e0End, k, firstBuf, lastBuf)) {
				return false;
			}
		}

		// 3) Middle S-insert in any gap
		for (int g = 0; g < k - 1; g++) {
			if (hasCommonItemInGapRange(sids, nSids, k, firstBuf, lastBuf, g)) {
				return false;
			}
		}

		// 4) Middle I-augment into any element (skip for singletons)
		if (!isSingleton) {
			for (int i = 0; i < k; i++) {
				int eiStart = offsets[i * 2];
				int eiEnd = offsets[i * 2 + 1];
				if (hasCommonItemIInsertDirect(sids, nSids, patternBuf, eiStart, eiEnd, k, firstBuf, lastBuf, i)) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Backward I-augment into the first element using direct offset indexing.
	 */
	private boolean hasCommonItemIPrependDirect(int[] sids, int nSids, int[] patternBuf, int e0Start, int e0End, int k,
			int[] firstBuf, int[] lastBuf) {
		if (nSids == 0)
			return false;

		int candCount = 0;
		int base = nextStampBase(nSids);
		int stamp1 = base + 1;

		// First SID
		{
			int sid = sids[0];
			int[][] seq = sequences[sid];

			int from = Math.max(0, firstBuf[0]);
			int to = Math.min(seq.length - 1, lastBuf[0]);

			for (int j = from; j <= to && j < seq.length; j++) {
				int[] is = seq[j];
				if (isSubsetDirect(patternBuf, e0Start, e0End, is)) {
					// Find items in is but not in e0
					int pi = e0Start;
					for (int si = 0; si < is.length; si++) {
						int x = is[si];
						while (pi < e0End && patternBuf[pi] < x)
							pi++;
						if (pi >= e0End || patternBuf[pi] != x) {
							if (seenStamp[x] != stamp1) {
								seenStamp[x] = stamp1;
								candidates[candCount++] = x;
							}
						}
					}
				}
			}
		}

		if (candCount == 0)
			return false;

		// Remaining SIDs
		for (int idx = 1; idx < nSids; idx++) {
			int sid = sids[idx];
			int[][] seq = sequences[sid];

			int off = idx * k;
			int from = Math.max(0, firstBuf[off]);
			int to = Math.min(seq.length - 1, lastBuf[off]);

			int curStamp = base + idx + 1;
			int prevStamp = curStamp - 1;

			for (int j = from; j <= to && j < seq.length; j++) {
				int[] is = seq[j];
				if (isSubsetDirect(patternBuf, e0Start, e0End, is)) {
					int pi = e0Start;
					for (int si = 0; si < is.length; si++) {
						int x = is[si];
						while (pi < e0End && patternBuf[pi] < x)
							pi++;
						if (pi >= e0End || patternBuf[pi] != x) {
							if (seenStamp[x] == prevStamp)
								seenStamp[x] = curStamp;
						}
					}
				}
			}

			// Compact
			int w = 0;
			for (int r = 0; r < candCount; r++) {
				int x = candidates[r];
				if (seenStamp[x] == curStamp)
					candidates[w++] = x;
			}
			candCount = w;

			if (candCount == 0)
				return false;
		}

		return true;
	}

	/**
	 * Middle I-augment into element iElem using direct offset indexing.
	 */
	private boolean hasCommonItemIInsertDirect(int[] sids, int nSids, int[] patternBuf, int eiStart, int eiEnd, int k,
			int[] firstBuf, int[] lastBuf, int iElem) {
		if (nSids == 0)
			return false;

		int candCount = 0;
		int base = nextStampBase(nSids);
		int stamp1 = base + 1;

		// First SID
		{
			int sid = sids[0];
			int[][] seq = sequences[sid];

			int from = Math.max(0, firstBuf[iElem]);
			int to = Math.min(seq.length - 1, lastBuf[iElem]);

			for (int j = from; j <= to && j < seq.length; j++) {
				int[] is = seq[j];
				if (isSubsetDirect(patternBuf, eiStart, eiEnd, is)) {
					int pi = eiStart;
					for (int si = 0; si < is.length; si++) {
						int x = is[si];
						while (pi < eiEnd && patternBuf[pi] < x)
							pi++;
						if (pi >= eiEnd || patternBuf[pi] != x) {
							if (seenStamp[x] != stamp1) {
								if (globalSupport[x] >= minsup) {
									seenStamp[x] = stamp1;
									candidates[candCount++] = x;
								}
							}
						}
					}
				}
			}
		}

		if (candCount == 0)
			return false;

		// Remaining SIDs
		for (int idx = 1; idx < nSids; idx++) {
			int sid = sids[idx];
			int[][] seq = sequences[sid];

			int off = idx * k;
			int from = Math.max(0, firstBuf[off + iElem]);
			int to = Math.min(seq.length - 1, lastBuf[off + iElem]);

			int curStamp = base + idx + 1;
			int prevStamp = curStamp - 1;

			for (int j = from; j <= to && j < seq.length; j++) {
				int[] is = seq[j];
				if (isSubsetDirect(patternBuf, eiStart, eiEnd, is)) {
					int pi = eiStart;
					for (int si = 0; si < is.length; si++) {
						int x = is[si];
						while (pi < eiEnd && patternBuf[pi] < x)
							pi++;
						if (pi >= eiEnd || patternBuf[pi] != x) {
							if (seenStamp[x] == prevStamp)
								seenStamp[x] = curStamp;
						}
					}
				}
			}

			// Compact
			int w = 0;
			for (int r = 0; r < candCount; r++) {
				int x = candidates[r];
				if (seenStamp[x] == curStamp)
					candidates[w++] = x;
			}
			candCount = w;

			if (candCount == 0)
				return false;
		}

		return true;
	}

	/**
	 * isSubset using direct range indexing (zero copy).
	 */
	private static boolean isSubsetDirect(int[] a, int aStart, int aEnd, int[] b) {
		int i = aStart, j = 0;
		while (i < aEnd && j < b.length) {
			if (a[i] == b[j]) {
				i++;
				j++;
			} else if (b[j] < a[i]) {
				j++;
			} else {
				return false;
			}
		}
		return i == aEnd;
	}

	/**
	 * Check if there's ANY common item in the range [0, lastPos(0)) across ALL
	 * SIDs.
	 */
	private boolean hasCommonItemInPrefixRange(int[] sids, int nSids, int k, int[] lastBuf) {
		if (nSids == 0)
			return false;

		int candCount = 0;
		int base = nextStampBase(nSids);
		int stamp1 = base + 1;

		// First SID: build candidate set
		{
			int sid = sids[0];
			int from = 0;
			int to = lastBuf[0];

			if (isSingleton) {
				int[] seq = singletonSeqs[sid];
				if (from < 0)
					from = 0;
				if (to > seq.length)
					to = seq.length;
				if (to <= from)
					return false;

				for (int j = from; j < to; j++) {
					int x = seq[j];
					if (seenStamp[x] != stamp1) {
						if (globalSupport[x] >= minsup) {
							seenStamp[x] = stamp1;
							candidates[candCount++] = x;
						}
					}
				}
			} else {
				int[][] seq = sequences[sid];
				if (from < 0)
					from = 0;
				if (to > seq.length)
					to = seq.length;
				if (to <= from)
					return false;

				for (int j = from; j < to; j++) {
					int[] is = seq[j];
					for (int t = 0; t < is.length; t++) {
						int x = is[t];
						if (seenStamp[x] != stamp1) {
							if (globalSupport[x] >= minsup) {
								seenStamp[x] = stamp1;
								candidates[candCount++] = x;
							}
						}
					}
				}
			}
		}

		if (candCount == 0)
			return false;

		// Remaining SIDs: filter candidates
		for (int idx = 1; idx < nSids; idx++) {
			int sid = sids[idx];
			int from = 0;
			int to = lastBuf[idx * k];

			int curStamp = base + idx + 1;
			int prevStamp = curStamp - 1;

			if (isSingleton) {
				int[] seq = singletonSeqs[sid];
				if (from < 0)
					from = 0;
				if (to > seq.length)
					to = seq.length;
				if (to <= from)
					return false;

				for (int j = from; j < to; j++) {
					int x = seq[j];
					if (seenStamp[x] == prevStamp)
						seenStamp[x] = curStamp;
				}
			} else {
				int[][] seq = sequences[sid];
				if (from < 0)
					from = 0;
				if (to > seq.length)
					to = seq.length;
				if (to <= from)
					return false;

				for (int j = from; j < to; j++) {
					int[] is = seq[j];
					for (int t = 0; t < is.length; t++) {
						int x = is[t];
						if (seenStamp[x] == prevStamp)
							seenStamp[x] = curStamp;
					}
				}
			}

			// Compact candidates
			int w = 0;
			for (int r = 0; r < candCount; r++) {
				int x = candidates[r];
				if (seenStamp[x] == curStamp) {
					candidates[w++] = x;
				}
			}
			candCount = w;

			if (candCount == 0)
				return false;
		}

		return true;
	}

	/**
	 * Check if there's ANY common item in the gap range [firstPos(g)+1,
	 * lastPos(g+1)) across ALL SIDs.
	 */
	private boolean hasCommonItemInGapRange(int[] sids, int nSids, int k, int[] firstBuf, int[] lastBuf, int gapIdx) {
		if (nSids == 0)
			return false;

		int candCount = 0;
		int base = nextStampBase(nSids);
		int stamp1 = base + 1;

		// First SID: build candidate set
		{
			int sid = sids[0];
			int from = firstBuf[gapIdx] + 1;
			int to = lastBuf[gapIdx + 1];

			if (isSingleton) {
				int[] seq = singletonSeqs[sid];
				if (from < 0)
					from = 0;
				if (to > seq.length)
					to = seq.length;
				if (to <= from)
					return false;

				for (int j = from; j < to; j++) {
					int x = seq[j];
					if (seenStamp[x] != stamp1) {
						if (globalSupport[x] >= minsup) {
							seenStamp[x] = stamp1;
							candidates[candCount++] = x;
						}
					}
				}
			} else {
				int[][] seq = sequences[sid];
				if (from < 0)
					from = 0;
				if (to > seq.length)
					to = seq.length;
				if (to <= from)
					return false;

				for (int j = from; j < to; j++) {
					int[] is = seq[j];
					for (int t = 0; t < is.length; t++) {
						int x = is[t];
						if (seenStamp[x] != stamp1) {
							if (globalSupport[x] >= minsup) {
								seenStamp[x] = stamp1;
								candidates[candCount++] = x;
							}
						}
					}
				}
			}
		}

		if (candCount == 0)
			return false;

		// Remaining SIDs: filter candidates
		for (int idx = 1; idx < nSids; idx++) {
			int sid = sids[idx];
			int from = firstBuf[idx * k + gapIdx] + 1;
			int to = lastBuf[idx * k + gapIdx + 1];

			int curStamp = base + idx + 1;
			int prevStamp = curStamp - 1;

			if (isSingleton) {
				int[] seq = singletonSeqs[sid];
				if (from < 0)
					from = 0;
				if (to > seq.length)
					to = seq.length;
				if (to <= from)
					return false;

				for (int j = from; j < to; j++) {
					int x = seq[j];
					if (seenStamp[x] == prevStamp)
						seenStamp[x] = curStamp;
				}
			} else {
				int[][] seq = sequences[sid];
				if (from < 0)
					from = 0;
				if (to > seq.length)
					to = seq.length;
				if (to <= from)
					return false;

				for (int j = from; j < to; j++) {
					int[] is = seq[j];
					for (int t = 0; t < is.length; t++) {
						int x = is[t];
						if (seenStamp[x] == prevStamp)
							seenStamp[x] = curStamp;
					}
				}
			}

			// Compact candidates
			int w = 0;
			for (int r = 0; r < candCount; r++) {
				int x = candidates[r];
				if (seenStamp[x] == curStamp) {
					candidates[w++] = x;
				}
			}
			candCount = w;

			if (candCount == 0)
				return false;
		}

		return true;
	}

	/**
	 * Backward I-augment into the first element: any x not in E0 that appears in
	 * some itemset matching E0.
	 */
	private boolean hasCommonItemIPrepend(int[] sids, int nSids, int[] e0, int k, int[] firstBuf, int[] lastBuf) {
		if (nSids == 0)
			return false;

		int candCount = 0;
		int base = nextStampBase(nSids);
		int stamp1 = base + 1;

		// First SID
		{
			int sid = sids[0];
			int[][] seq = sequences[sid];

			int from = Math.max(0, firstBuf[0]);
			int to = Math.min(seq.length - 1, lastBuf[0]);

			for (int j = from; j <= to && j < seq.length; j++) {
				int[] is = seq[j];
				if (isSubset(e0, is)) {
					// Find items in is but not in e0
					int pi = 0;
					for (int si = 0; si < is.length; si++) {
						int x = is[si];
						while (pi < e0.length && e0[pi] < x)
							pi++;
						if (pi >= e0.length || e0[pi] != x) {
							if (seenStamp[x] != stamp1) {
								seenStamp[x] = stamp1;
								candidates[candCount++] = x;
							}
						}
					}
				}
			}
		}

		if (candCount == 0)
			return false;

		// Remaining SIDs
		for (int idx = 1; idx < nSids; idx++) {
			int sid = sids[idx];
			int[][] seq = sequences[sid];

			int off = idx * k;
			int from = Math.max(0, firstBuf[off]);
			int to = Math.min(seq.length - 1, lastBuf[off]);

			int curStamp = base + idx + 1;
			int prevStamp = curStamp - 1;

			for (int j = from; j <= to && j < seq.length; j++) {
				int[] is = seq[j];
				if (isSubset(e0, is)) {
					int pi = 0;
					for (int si = 0; si < is.length; si++) {
						int x = is[si];
						while (pi < e0.length && e0[pi] < x)
							pi++;
						if (pi >= e0.length || e0[pi] != x) {
							if (seenStamp[x] == prevStamp)
								seenStamp[x] = curStamp;
						}
					}
				}
			}

			// Compact
			int w = 0;
			for (int r = 0; r < candCount; r++) {
				int x = candidates[r];
				if (seenStamp[x] == curStamp)
					candidates[w++] = x;
			}
			candCount = w;

			if (candCount == 0)
				return false;
		}

		return true;
	}

	/**
	 * Middle I-augment witness for element iElem.
	 */
	private boolean hasCommonItemIInsert(int[] sids, int nSids, int[] ei, int k, int[] firstBuf, int[] lastBuf,
			int iElem) {
		if (nSids == 0)
			return false;

		int candCount = 0;
		int base = nextStampBase(nSids);
		int stamp1 = base + 1;

		// First SID
		{
			int sid = sids[0];
			int[][] seq = sequences[sid];

			int from = Math.max(0, firstBuf[iElem]);
			int to = Math.min(seq.length - 1, lastBuf[iElem]);

			for (int j = from; j <= to && j < seq.length; j++) {
				int[] is = seq[j];
				if (isSubset(ei, is)) {
					int pi = 0;
					for (int si = 0; si < is.length; si++) {
						int x = is[si];
						while (pi < ei.length && ei[pi] < x)
							pi++;
						if (pi >= ei.length || ei[pi] != x) {
							if (seenStamp[x] != stamp1) {
								seenStamp[x] = stamp1;
								candidates[candCount++] = x;
							}
						}
					}
				}
			}
		}

		if (candCount == 0)
			return false;

		// Remaining SIDs
		for (int idx = 1; idx < nSids; idx++) {
			int sid = sids[idx];
			int[][] seq = sequences[sid];

			int off = idx * k;
			int from = Math.max(0, firstBuf[off + iElem]);
			int to = Math.min(seq.length - 1, lastBuf[off + iElem]);

			int curStamp = base + idx + 1;
			int prevStamp = curStamp - 1;

			for (int j = from; j <= to && j < seq.length; j++) {
				int[] is = seq[j];
				if (isSubset(ei, is)) {
					int pi = 0;
					for (int si = 0; si < is.length; si++) {
						int x = is[si];
						while (pi < ei.length && ei[pi] < x)
							pi++;
						if (pi >= ei.length || ei[pi] != x) {
							if (seenStamp[x] == prevStamp)
								seenStamp[x] = curStamp;
						}
					}
				}
			}

			// Compact
			int w = 0;
			for (int r = 0; r < candCount; r++) {
				int x = candidates[r];
				if (seenStamp[x] == curStamp)
					candidates[w++] = x;
			}
			candCount = w;

			if (candCount == 0)
				return false;
		}

		return true;
	}

	/**
	 * Check if a is a subset of b (both sorted).
	 */
	public static boolean isSubset(int[] a, int[] b) {
		int i = 0, j = 0;
		while (i < a.length && j < b.length) {
			if (a[i] == b[j]) {
				i++;
				j++;
			} else if (b[j] < a[i]) {
				j++;
			} else {
				return false;
			}
		}
		return i == a.length;
	}
}
