package ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.BitSet;

/*
 * Copyright Antonio Gomariz Peñalver 2013
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
 * Do not remove copyright and license.
 */
/**
 * Class representing a pattern. A pattern is stored as two flat primitive
 * arrays (item ids and equal-relation flags). Note: this class also carries
 * some algorithm execution state ({@code appearingIn},
 * {@code appearingInWords}, {@code cachedSupport}).
 *
 * @author agomariz
 */
public class Pattern implements Comparable<Pattern> {

	/** Reusable empty array of integers */
	private static final int[] EMPTY_INTS = new int[0];

	/** Reusable empty array of Boolean values */
	private static final boolean[] EMPTY_BOOLS = new boolean[0];

	/** Reusable empty array of Long values */
	private static final long[] EMPTY_LONGS = new long[0];

	/**
	 * Item ids, index-aligned with equalRelations. IMPORTANT: Only indices [0,
	 * size) are valid.
	 */
	private int[] itemIds;

	/** equalRelation flags, index-aligned with itemIds. */
	private boolean[] equalRelations;

	/** Number of elements (may be < itemIds.length after growth). */
	private int size;

	/**
	 * BitSet of sequence ids in which this pattern appears. Empty until method
	 * setAppearingIn() is called once this pattern's id-list has been computed. An
	 * empty value here means "not computed yet". May become {@code null} after a
	 * call to {@link #discardAppearingSequences()}, meaning "computed, cached, and
	 * deliberately released".
	 */
	private BitSet appearingIn;

	/**
	 * Cached long[] word view of appearingIn, rebuilt once per call to
	 * setAppearingIn(). May become {@code null} after
	 * {@link #discardAppearingSequences()}, in lockstep with {@code appearingIn}.
	 */
	private long[] appearingInWords;

	/**
	 * Cached support (cardinality of appearingIn), or -1 if not yet known.
	 */
	private int cachedSupport = -1;

	/** Constructs an empty pattern. */
	public Pattern() {
		this.itemIds = EMPTY_INTS;
		this.equalRelations = EMPTY_BOOLS;
		this.size = 0;
		this.appearingIn = new BitSet();
		this.appearingInWords = EMPTY_LONGS;
	}

	/**
	 * Constructs a single-element pattern.
	 * 
	 * @param itemId        the item id
	 * @param equalRelation true for i-extension, false for s-extension
	 */
	public Pattern(int itemId, boolean equalRelation) {
		this();
		this.itemIds = new int[] { itemId };
		this.equalRelations = new boolean[] { equalRelation };
		this.size = 1;
	}

	/**
	 * Constructs a pattern from the given primitive arrays, defensively copying
	 * them.
	 *
	 * @param itemIds        the item ids
	 * @param equalRelations the relation flags, index-aligned with itemIds
	 * @param size           the number of valid elements (may be less than the
	 *                       arrays' length)
	 * @throws IllegalArgumentException if size is negative or exceeds the length of
	 *                                  either array
	 */
	public Pattern(int[] itemIds, boolean[] equalRelations, int size) {
		this();
		if (size < 0 || size > itemIds.length || size > equalRelations.length) {
			throw new IllegalArgumentException(
					"size (" + size + ") must be >= 0 and not exceed the length of both arrays");
		}
//		 Defensive copy
//		this.itemIds = Arrays.copyOf(itemIds, size);
//		this.equalRelations = Arrays.copyOf(equalRelations, size);
		this.itemIds = itemIds;
		this.equalRelations = equalRelations;
		this.size = size;
	}

	/**
	 * Creates a new pattern equal to this one with one (itemId, equalRelation) pair
	 * appended.
	 *
	 * @param itemId        the item id to append
	 * @param equalRelation the relation flag to append
	 * @return the extended pattern
	 */
	public Pattern cloneAndAppend(int itemId, boolean equalRelation) {
		Pattern p = new Pattern();
		int n = this.size;
		p.itemIds = Arrays.copyOf(this.itemIds, n + 1);
		p.equalRelations = Arrays.copyOf(this.equalRelations, n + 1);
		p.itemIds[n] = itemId;
		p.equalRelations[n] = equalRelation;
		p.size = n + 1;
		return p;
	}

	/**
	 * @return the item id of the last element. Warning: no bound check is
	 *         performed.
	 */
	public int getLastItemId() {
		return itemIds[size - 1];
	}

	/**
	 * @return the equalRelation flag of the last element. Warning: no bound check
	 *         is performed.
	 */
	public boolean getLastEqualRelation() {
		return equalRelations[size - 1];
	}

	/**
	 * @return the item id of the second-to-last element. Warning: no bound check is
	 *         performed.
	 */
	public int getLastButOneItemId() {
		return itemIds[size - 2];
	}

	/**
	 * @return the equalRelation flag of the second-to-last element.
	 * @implNote see {@link #getLastButOneItemId()} regarding the unchecked size()
	 *           &gt;= 2 precondition.
	 */
	public boolean getLastButOneEqualRelation() {
		return equalRelations[size - 2];
	}

	/**
	 * @param i the index; must satisfy 0 &lt;= i &lt; size()
	 * @return the item id at index i
	 */
	public int getIthItemId(int i) {
		return itemIds[i];
	}

	/**
	 * @param i the index; must satisfy 0 &lt;= i &lt; size()
	 * @return the equalRelation flag at index i
	 */
	public boolean getIthEqualRelation(int i) {
		return equalRelations[i];
	}

	/**
	 * Returns the cached long[] word view of appearingIn (do not mutate), used by
	 * callers that need fast bitwise support pre-checks.
	 *
	 * @return the cached word view of appearingIn
	 * @throws IllegalStateException if called after
	 *                               {@link #discardAppearingSequences()}.
	 */
	public long[] getAppearingInWords() {
		if (appearingInWords == null) {
			throw new IllegalStateException(
					"getAppearingInWords() called after discardAppearingSequences(): this pattern's "
							+ "appearing-sequences bitset was released because the caller had proven it "
							+ "would never be needed again; this call means that proof no longer holds.");
		}
		return appearingInWords;
	}

	/**
	 * @return the number of elements in this pattern
	 */
	public int size() {
		return size;
	}

	/**
	 * Returns true if appending exactly one (itemId, equalRelation) pair to this
	 * pattern could have produced p.
	 * 
	 * @param p the longer pattern
	 * @return true if this pattern is the equivalence-class parent of p
	 */
	public boolean isPrefix(Pattern p) {
		int idx = p.size - 2;
		return p.itemIds[idx] == this.itemIds[this.size - 1]
				&& p.equalRelations[idx] == this.equalRelations[this.size - 1];
	}

	/**
	 * Returns the bitset of sequence ids in which this pattern appears. Warning:
	 * returns the live internal BitSet, not a copy.
	 *
	 * @return the appearing-in bitset, or {@code null} if it was released via
	 *         {@link #discardAppearingSequences()}
	 */
	public BitSet getAppearingIn() {
		return appearingIn;
	}

	/**
	 * Sets the bitset of sequence ids in which this pattern appears, and refreshes
	 * the cached word-view used for fast intersection support checks. Called once
	 * per pattern, when its id-list is finalized.
	 * 
	 * @param appearingIn the appearing-in bitset
	 */
	public void setAppearingIn(BitSet appearingIn) {
		this.appearingIn = appearingIn;
		this.appearingInWords = appearingIn.toLongArray();
		this.cachedSupport = -1;
	}

	/**
	 * Releases {@code appearingIn}/{@code appearingInWords} early, for callers that
	 * have proven no future join or output step will read them again. This is a
	 * safe no-op unless the support has already been cached via
	 * {@link #setCachedSupport(int)}, so that {@link #getSupport()} always has
	 * something to fall back on.
	 */
	public void discardAppearingSequences() {
		if (cachedSupport < 0) {
			// Support not yet cached: keep the bitset, getSupport() still needs it.
			return;
		}
		this.appearingIn = null;
		this.appearingInWords = null;
	}

	/**
	 * Returns this pattern's support, using the cached value if known, otherwise
	 * falling back to (and caching) a full popcount of appearingIn.
	 * 
	 * @return the support count
	 * @throws IllegalStateException if the support was never cached and the bitset
	 *                               has since been released — this should be
	 *                               unreachable, since
	 *                               {@link #discardAppearingSequences()} refuses to
	 *                               release the bitset while the support is still
	 *                               unknown.
	 */
	public int getSupport() {
		if (cachedSupport < 0) {
			if (appearingIn == null) {
				throw new IllegalStateException(
						"getSupport() called with neither a cached support nor a bitset to compute it from; "
								+ "this should be impossible, see discardAppearingSequences().");
			}
			cachedSupport = appearingIn.cardinality();
		}
		return cachedSupport;
	}

	/**
	 * Explicitly sets the cached support, to be called whenever the caller already
	 * knows it (e.g. right after an id-list join).
	 * 
	 * @param support the known support count; must be >= 0
	 * @throws IllegalArgumentException if support is negative
	 */
	public void setCachedSupport(int support) {
		if (support < 0) {
			throw new IllegalArgumentException("support must be >= 0, got " + support);
		}
		this.cachedSupport = support;
	}

	/**
	 * Writes the given int directly into the writer as digits, without allocating
	 * an intermediate String.
	 */
	private static void writeInt(BufferedWriter writer, int value) throws IOException {
		if (value == 0) {
			writer.write('0');
			return;
		}
		char[] buf = new char[11];
		boolean negative = value < 0;
		long v = negative ? -(long) value : value;
		int pos = buf.length;
		while (v > 0) {
			buf[--pos] = (char) ('0' + (int) (v % 10));
			v /= 10;
		}
		if (negative) {
			buf[--pos] = '-';
		}
		writer.write(buf, pos, buf.length - pos);
	}

	/**
	 * Writes this pattern directly to the given writer.
	 * 
	 * @param writer                    the writer to write to
	 * @param outputSequenceIdentifiers if true, sequence IDs are appended
	 * @throws IOException           if an I/O error occurs
	 * @throws IllegalStateException if {@code outputSequenceIdentifiers} is true
	 *                               but the appearing-in bitset was already
	 *                               released via
	 *                               {@link #discardAppearingSequences()}.
	 */
	public void writeToFile(BufferedWriter writer, boolean outputSequenceIdentifiers) throws IOException {
		int sz = size;
		for (int i = 0; i < sz; i++) {
			if (i > 0) {
				writer.write(equalRelations[i] ? " " : " -1 ");
			}
			writeInt(writer, itemIds[i]);
			if (i == sz - 1) {
				writer.write(" -1");
			}
		}
		writer.write(" #SUP: ");
		writeInt(writer, getSupport());
		if (outputSequenceIdentifiers) {
			if (appearingIn == null) {
				throw new IllegalStateException(
						"Cannot write sequence identifiers: this pattern's bitset was already released.");
			}
			writer.write(" #SID: ");
			// Sequence ids are stored 1-based internally throughout this algorithm's
			// id-lists (see AlgoCMSPADE); output uses conventional 0-based numbering.
			for (int i = appearingIn.nextSetBit(0); i >= 0; i = appearingIn.nextSetBit(i + 1)) {
				writeInt(writer, i - 1);
				writer.write(' ');
			}
		}
	}

	/**
	 * Get the string representation of this pattern, used when patterns are kept in
	 * memory rather than written to a file.
	 * 
	 * @param outputSequenceIdentifiers if true, sequence IDs are appended
	 * @return the string representation
	 * @throws IllegalStateException if {@code outputSequenceIdentifiers} is true
	 *                               but the appearing-in bitset was already
	 *                               released (see {@link #writeToFile}).
	 */
	public String toStringToFile(boolean outputSequenceIdentifiers) {
		StringBuilder result = new StringBuilder();
		int sz = size;
		for (int i = 0; i < sz; i++) {
			if (i > 0) {
				result.append(equalRelations[i] ? " " : " -1 ");
			}
			result.append(itemIds[i]);
			if (i == sz - 1) {
				result.append(" -1");
			}
		}
		result.append(" #SUP: ");
		result.append(getSupport());
		if (outputSequenceIdentifiers) {
			if (appearingIn == null) {
				throw new IllegalStateException(
						"Cannot render sequence identifiers: this pattern's bitset was already released.");
			}
			result.append(" #SID: ");
			for (int i = appearingIn.nextSetBit(0); i >= 0; i = appearingIn.nextSetBit(i + 1)) {
				result.append(i - 1);
				result.append(' ');
			}
		}
		return result.toString();
	}

	/**
	 * Orders patterns by full lexicographic comparison of their (itemId,
	 * equalRelation) sequences, with a shorter pattern sorting before a longer one
	 * of which it is a strict prefix.
	 */
	@Override
	public int compareTo(Pattern other) {
		int minSize = Math.min(this.size, other.size);
		for (int i = 0; i < minSize; i++) {
			int cmp = Integer.compare(this.itemIds[i], other.itemIds[i]);
			if (cmp != 0) {
				return cmp;
			}
			cmp = Boolean.compare(this.equalRelations[i], other.equalRelations[i]);
			if (cmp != 0) {
				return cmp;
			}
		}
		return Integer.compare(this.size, other.size);
	}

	// equals()/hashCode() are intentionally NOT overridden: identity semantics
	// (Object's defaults) are kept.

	/**
	 * Returns the string representation of this pattern (debug/logging use only;
	 * see writeToFile()/toStringToFile() for the actual output format).
	 * 
	 * @return the string representation
	 */
	@Override
	public String toString() {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < size; i++) {
			if (i > 0) {
				result.append(equalRelations[i] ? " " : " -1 ");
			}
			result.append(itemIds[i]);
		}
		return result.toString();
	}

	/** Clears all the information for this pattern. */
	public void clear() {
		itemIds = EMPTY_INTS;
		equalRelations = EMPTY_BOOLS;
		size = 0;
		appearingIn = new BitSet();
		appearingInWords = EMPTY_LONGS;
		cachedSupport = -1;
	}

	/** @return the cached support, or -1 if it has not been computed/cached yet */
	public int getCachedSupport() {
		return cachedSupport;
	}
}