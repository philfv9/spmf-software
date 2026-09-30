package ca.pfv.spmf.algorithms.sequentialpatterns.spam;

import java.util.Arrays;

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
 * Copyright (c) Philipe Fournier-Viger et al.
 *
 * Do not remove copyright and license information.
 */
/**
 * Bitmap-based vertical representation of a single item's occurrences in a
 * sequence database.
 * <p>
 * Each bit represents the presence of the item at a given position (itemset) in
 * the flattened sequence database. This representation allows S-step (new
 * itemset) and I-step (same itemset) extensions of patterns to be computed
 * efficiently using bitwise operations.
 * @author Philippe Fournier-Viger
 */
public class BitmapSPAM {

	/**
	 * Bit array storing the occurrences of the item, one bit per position in the
	 * flattened database.
	 */
	long[] words;
	/** Number of valid entries (longs) currently used in {@link #words}. */
	int wordCount;

	/**
	 * Sequence id (SID) of the last occurrence registered, used to avoid counting a
	 * sequence twice.
	 */
	int lastSID = -1;

	/** Number of distinct sequences in which the item occurs. */
	private int support = 0;

	/**
	 * Support count computed while ignoring the maxGap constraint, used as an upper
	 * bound during pruning.
	 */
	private int supportWithoutGapTotal = 0;

	/** Minimum number of longs allocated for a new or freshly reset bitmap. */
	private static final int MIN_WORD_CAPACITY = 4;
	/**
	 * Factor above which the words array is reallocated smaller instead of just
	 * cleared, on reuse.
	 */
	private static final int SHRINK_FACTOR_THRESHOLD = 4;

	/** Creates an empty bitmap with the minimum initial capacity. */
	BitmapSPAM() {
		this.words = new long[MIN_WORD_CAPACITY];
		this.wordCount = 0;
	}

	/**
	 * Returns the index of the word (long) that contains the given bit position.
	 *
	 * @param bitPos the absolute bit position
	 * @return the index of the containing word
	 */
	private static int wordIndex(int bitPos) {
		return bitPos >> 6;
	}

	/**
	 * Returns a mask with only the bit corresponding to the given position set
	 * within its word.
	 *
	 * @param bitPos the absolute bit position
	 * @return a mask isolating that bit
	 */
	private static long bitMask(int bitPos) {
		return 1L << (bitPos & 63);
	}

	/**
	 * Grows the words array if needed so that the given word index is valid,
	 * updating {@link #wordCount} accordingly. This is the single growth routine
	 * used both when a caller already knows the target word index and when it is
	 * derived from an absolute bit position via {@link #wordIndex(int)}.
	 *
	 * @param wordIdx the word index that must fit in the array
	 */
	private void ensureWordCapacity(int wordIdx) {
		if (wordIdx >= words.length) {
			int newLen = Math.max(wordIdx + 1, words.length * 2);
			words = Arrays.copyOf(words, newLen);
		}
		if (wordIdx >= wordCount) {
			wordCount = wordIdx + 1;
		}
	}

	/**
	 * Resets the support counters and last-SID tracking without touching the words
	 * array.
	 */
	private void resetFieldsOnly() {
		lastSID = -1;
		support = 0;
		supportWithoutGapTotal = 0;
	}

	/**
	 * Prepares this bitmap for reuse as a scratch buffer, clearing its content and
	 * support counters. The underlying array is reallocated only if it is too small
	 * or excessively oversized compared to the given hint.
	 *
	 * @param wordCapacityHint the expected number of words this bitmap will need to
	 *                         hold
	 */
	void resetForReuse(int wordCapacityHint) {
		int hint = Math.max(wordCapacityHint, MIN_WORD_CAPACITY);

		if (hint > words.length) {
			// current array is too small: allocate a new one of the required size
			words = new long[hint];
		} else if (words.length > hint * SHRINK_FACTOR_THRESHOLD) {
			// current array is far oversized: shrink it to avoid wasting memory
			words = new long[hint];
		} else {
			// reuse the array as-is, just clear the bits that were previously used
			Arrays.fill(words, 0, wordCount, 0L);
		}

		wordCount = 0;
		resetFieldsOnly();
	}

	/**
	 * Copies the full state of {@code other} into this bitmap, producing an
	 * independent deep copy. The destination {@code words} array is reused if it is
	 * already large enough; otherwise a new array is allocated. All support
	 * counters and bookkeeping fields are copied verbatim.
	 *
	 * @param other the bitmap to copy from; must not be {@code null}
	 */
	void copyFrom(BitmapSPAM other) {
		// grow the words array only if necessary; never shrink here so that the
		// permanent
		// copy does not waste memory beyond what it actually needs
		if (other.wordCount > words.length) {
			words = new long[other.wordCount];
		} else {
			// zero out any leftover bits from a previous use of this instance
			Arrays.fill(words, 0, wordCount, 0L);
		}
		System.arraycopy(other.words, 0, words, 0, other.wordCount);
		wordCount = other.wordCount;
		lastSID = other.lastSID;
		support = other.support;
		supportWithoutGapTotal = other.supportWithoutGapTotal;
	}

	/**
	 * Marks an occurrence of the item at the given sequence and itemset position,
	 * updating support.
	 *
	 * @param sid           the sequence id of the occurrence
	 * @param tid           the itemset position (transaction id) within the
	 *                      sequence
	 * @param sequencesSize starting bit index of each sequence, used to compute the
	 *                      absolute bit position
	 */
	public void registerBit(int sid, int tid, int[] sequencesSize) {
		// compute the absolute bit position from the sequence start and the itemset
		// offset
		int pos = sequencesSize[sid] + tid;
		int idx = wordIndex(pos);
		ensureWordCapacity(idx);
		words[idx] |= bitMask(pos);

		// count support once per sequence, not per occurrence
		if (sid != lastSID) {
			support++;
		}
		lastSID = sid;
	}

	/** @return the number of distinct sequences in which the item occurs */
	public int getSupport() {
		return support;
	}

	/** @return the support count computed while ignoring the maxGap constraint */
	public int getSupportWithoutGapTotal() {
		return supportWithoutGapTotal;
	}

	/**
	 * Scans this bitmap and records, for each distinct sequence it occupies, the
	 * sequence id and the position of the first set bit found in that sequence.
	 *
	 * @param sidScratch      buffer receiving the occupied sequence ids
	 * @param firstBitScratch buffer receiving the first set bit position for each
	 *                        occupied sequence
	 * @param sidResolver     resolves bit positions to sequence ids
	 * @return the number of distinct sequences found
	 */
	int collectOccupiedSidsAndFirstBit(int[] sidScratch, int[] firstBitScratch, SidResolver sidResolver) {
		int count = 0;
		int lastSid = -1;
		// iterate over every set bit, recording the first occurrence of each new
		// sequence
		for (int w = 0; w < wordCount; w++) {
			long word = words[w];
			while (word != 0L) {
				int bitOffset = Long.numberOfTrailingZeros(word);
				int bit = (w << 6) + bitOffset;
				word &= word - 1;

				int sid = sidResolver.resolve(bit);
				if (sid != lastSid) {
					sidScratch[count] = sid;
					firstBitScratch[count] = bit;
					count++;
					lastSid = sid;
				}
			}
		}
		return count;
	}

	/**
	 * Builds the bitmap resulting from an S-step, that is appending a new itemset
	 * containing {@code bitmapItem}'s item after the occurrences represented by
	 * this bitmap, respecting the given maximum gap constraint.
	 *
	 * @param newBitmap        the bitmap to fill with the result
	 * @param bitmapItem       the bitmap of the candidate item being appended
	 * @param maxGap           the maximum allowed gap (in itemsets) between the
	 *                         prefix and the new item
	 * @param sidResolver      resolves bit positions to sequence ids
	 * @param sequenceEndBit   ending bit index of each sequence
	 * @param occupiedSids     sequence ids occupied by this bitmap (only used when
	 *                         maxGap is unbounded)
	 * @param occupiedFirstBit first set bit of this bitmap in each occupied
	 *                         sequence (only used when maxGap is unbounded)
	 * @param occCount         number of valid entries in occupiedSids and
	 *                         occupiedFirstBit
	 */
	void createNewBitmapSStep(BitmapSPAM newBitmap, BitmapSPAM bitmapItem, int maxGap, SidResolver sidResolver,
			int[] sequenceEndBit, int[] occupiedSids, int[] occupiedFirstBit, int occCount) {
		if (maxGap == Integer.MAX_VALUE) {
			// unbounded gap: for each occupied sequence, look for the item anywhere after
			// the first bit
			for (int oi = 0; oi < occCount; oi++) {
				int sid = occupiedSids[oi];
				int seqEnd = sequenceEndBit[sid];

				int fromBit = occupiedFirstBit[oi] + 1;
				if (fromBit > seqEnd)
					continue;

				int endWord = wordIndex(seqEnd);
				int fromWord = wordIndex(fromBit);
				boolean matchInSeq = false;

				for (int w = fromWord; w <= endWord; w++) {
					long iw = (w < bitmapItem.wordCount ? bitmapItem.words[w] : 0L);
					if (iw == 0L)
						continue;

					if (w == fromWord) {
						int offset = fromBit & 63;
						if (offset != 0)
							iw &= (-1L << offset);
					}
					if (w == endWord) {
						int offset = seqEnd & 63;
						iw &= (offset == 63) ? -1L : ((1L << (offset + 1)) - 1L);
					}

					if (iw != 0L) {
						newBitmap.ensureWordCapacity(w);
						newBitmap.words[w] |= iw;
						matchInSeq = true;
					}
				}

				if (matchInSeq && sid != newBitmap.lastSID) {
					newBitmap.support++;
					newBitmap.lastSID = sid;
				}
			}

			newBitmap.supportWithoutGapTotal = newBitmap.support;

		} else {
			// bounded gap: scan bit by bit and only accept matches within maxGap itemsets
			int previousSid = -1;
			int currentSid = -1;
			int currentSidEnd = -1;
			int currentSidEndWord = -1;

			for (int wIdx = 0; wIdx < wordCount; wIdx++) {
				long w = words[wIdx];
				while (w != 0L) {
					int bitOffset = Long.numberOfTrailingZeros(w);
					int bitK = (wIdx << 6) + bitOffset;
					w &= w - 1;

					int sid = sidResolver.resolve(bitK);
					if (sid != currentSid) {
						currentSid = sid;
						currentSidEnd = sequenceEndBit[sid];
						currentSidEndWord = wordIndex(currentSidEnd);
					}

					boolean match = false;
					boolean matchWithoutGap = false;

					int startBit = bitK + 1;
					int scanWord = wordIndex(startBit);

					outer: for (int iw = scanWord; iw <= currentSidEndWord
							&& iw < bitmapItem.wordCount; iw++) {
						long iWord = bitmapItem.words[iw];
						if (iWord == 0L)
							continue;

						if (iw == scanWord) {
							int off = startBit & 63;
							if (off != 0)
								iWord &= (-1L << off);
						}
						if (iw == currentSidEndWord) {
							int endOff = currentSidEnd & 63;
							iWord &= (endOff == 63) ? -1L : ((1L << (endOff + 1)) - 1L);
						}
						while (iWord != 0L) {
							int bOff = Long.numberOfTrailingZeros(iWord);
							int bit = (iw << 6) + bOff;
							iWord &= iWord - 1;

							matchWithoutGap = true;
							if (bit - bitK > maxGap) {
								break outer;
							}
							newBitmap.ensureWordCapacity(iw);
							newBitmap.words[iw] |= bitMask(bit);

							match = true;
						}
					}

					if (matchWithoutGap && previousSid != currentSid) {
						newBitmap.supportWithoutGapTotal++;
						previousSid = currentSid;
					}
					if (match && currentSid != newBitmap.lastSID) {
						newBitmap.support++;
						newBitmap.lastSID = currentSid;
					}
				}
			}
		}
	}

	/**
	 * Builds the bitmap resulting from an I-step, that is adding
	 * {@code bitmapItem}'s item to the last itemset of the pattern, by intersecting
	 * this bitmap with {@code bitmapItem}.
	 *
	 * @param newBitmap   the bitmap to fill with the result
	 * @param bitmapItem  the bitmap of the candidate item being added to the
	 *                    itemset
	 * @param sidResolver resolves bit positions to sequence ids
	 */
	void createNewBitmapIStep(BitmapSPAM newBitmap, BitmapSPAM bitmapItem, SidResolver sidResolver) {
		int limit = Math.min(wordCount, bitmapItem.wordCount);

		// intersect the two bitmaps word by word and update the support of the result
		for (int w = 0; w < limit; w++) {
			long result = words[w] & bitmapItem.words[w];
			if (result != 0L) {
				newBitmap.ensureWordCapacity(w);
				newBitmap.words[w] = result;

				long tmp = result;
				while (tmp != 0L) {
					int bitOffset = Long.numberOfTrailingZeros(tmp);
					int bit = (w << 6) + bitOffset;
					tmp &= tmp - 1;

					int sid = sidResolver.resolve(bit);
					if (sid != newBitmap.lastSID) {
						newBitmap.support++;
						newBitmap.lastSID = sid;
					}
				}
			}
		}
	}

	/**
	 * Builds a space-separated string listing the sequence ids in which this bitmap
	 * has occurrences.
	 *
	 * @param sidResolver resolves bit positions to sequence ids
	 * @return the space-separated list of sequence ids
	 */
	public String getSIDs(SidResolver sidResolver) {
		StringBuilder builder = new StringBuilder();
		int lastSidSeen = -1;

		// walk the bitmap and append each new sequence id encountered
		for (int w = 0; w < wordCount; w++) {
			long word = words[w];
			while (word != 0L) {
				int bitOffset = Long.numberOfTrailingZeros(word);
				int bit = (w << 6) + bitOffset;
				word &= word - 1;

				int sid = sidResolver.resolve(bit);

				if (sid != lastSidSeen) {
					if (lastSidSeen != -1)
						builder.append(' ');
					builder.append(sid);
					lastSidSeen = sid;
				}
			}
		}
		return builder.toString();
	}

	/**
	 * Computes, for each sequence, the bit index at which it ends in the flattened
	 * bitmap representation.
	 *
	 * @param sequencesSize starting bit index of each sequence
	 * @param lastBitIndex  the last valid bit index across the whole database
	 * @param numSeq        the number of sequences
	 * @return an array giving the ending bit index of each sequence
	 */
	static int[] buildSequenceEndBit(int[] sequencesSize, int lastBitIndex, int numSeq) {
		int[] endBit = new int[numSeq];
		for (int s = 0; s < numSeq - 1; s++) {
			endBit[s] = sequencesSize[s + 1] - 1;
		}
		if (numSeq > 0)
			endBit[numSeq - 1] = lastBitIndex;
		return endBit;
	}

	/**
	 * Resolves an absolute bit position to the sequence id (SID) that owns it,
	 * using a small cache for locality.
	 */
	static final class SidResolver {
		/** Starting bit index of each sequence. */
		private final int[] sequencesSize;
		/** Ending bit index of each sequence. */
		private final int[] sequenceEndBit;
		/** Last sequence id resolved, used as a starting guess for the next lookup. */
		private int cachedSid = 0;

		/**
		 * Creates a resolver based on the given sequence boundaries.
		 *
		 * @param sequencesSize  starting bit index of each sequence
		 * @param sequenceEndBit ending bit index of each sequence
		 */
		SidResolver(int[] sequencesSize, int[] sequenceEndBit) {
			this.sequencesSize = sequencesSize;
			this.sequenceEndBit = sequenceEndBit;
		}

		/**
		 * Finds the sequence id owning the given bit position, checking the cached
		 * sequence first since bits are usually resolved in increasing order.
		 *
		 * @param bit the absolute bit position to resolve
		 * @return the sequence id owning that bit
		 */
		int resolve(int bit) {
			// try the cached sequence first
			int sid = cachedSid;
			if (bit >= sequencesSize[sid] && bit <= sequenceEndBit[sid]) {
				return sid;
			}
			// try the next sequence, the most common case when scanning in order
			if (sid + 1 < sequencesSize.length && bit >= sequencesSize[sid + 1] && bit <= sequenceEndBit[sid + 1]) {
				cachedSid = ++sid;
				return sid;
			}
			// fall back to a binary search over all sequences
			int lo = 0, hi = sequencesSize.length - 1, res = 0;
			while (lo <= hi) {
				int mid = (lo + hi) >>> 1;
				if (sequencesSize[mid] <= bit) {
					res = mid;
					lo = mid + 1;
				} else
					hi = mid - 1;
			}
			cachedSid = res;
			return res;
		}
	}
}