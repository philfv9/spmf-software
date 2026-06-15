package ca.pfv.spmf.algorithms.frequentpatterns.dci_closed_fast;

import java.util.Arrays;
/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
*/
/**
 * A bitset backed by a long[] array with direct word-level access.
 * This allows word-level early exit in subset checks and intersection,
 * which is not possible with Java's built-in BitSet class.
 * Tracks the number of active (potentially non-zero) words to skip
 * trailing zeros in all operations.
 *
 * @see AlgoDCI_Closed_FAST
 * @author Philippe Fournier-Viger
 */
public class LongArrayBitSet {

    /** the backing word array, each long holds 64 bits */
    private long[] words;

    /** the number of active words (index of last non-zero word + 1, or 0 if empty) */
    private int activeWords;

    /**
     * Constructor with a given number of words.
     * @param numWords the number of 64-bit words
     */
    public LongArrayBitSet(int numWords) {
        words = new long[numWords];
        activeWords = 0;
    }

    /**
     * Calculate the number of words needed to hold the given number of bits.
     * @param numBits the number of bits
     * @return the number of 64-bit words needed
     */
    public static int wordsNeeded(int numBits) {
        return (numBits + 63) >>> 6;
    }

    /**
     * Return the number of words in this bitset.
     * @return the number of words
     */
    public int numWords() {
        return words.length;
    }

    /**
     * Return the number of active (potentially non-zero) words.
     * @return the active word count
     */
    public int activeWords() {
        return activeWords;
    }

    /**
     * Return the backing word array directly.
     * @return the long[] array
     */
    public long[] words() {
        return words;
    }

    /**
     * Get the value of a single bit.
     * @param bit the bit index
     * @return true if the bit is set
     */
    public boolean get(int bit) {
        int wordIndex = bit >>> 6;
        if (wordIndex >= activeWords) {
            return false;
        }
        return (words[wordIndex] & (1L << bit)) != 0;
    }

    /**
     * Set a single bit to 1.
     * @param bit the bit index
     */
    public void set(int bit) {
        int wordIndex = bit >>> 6;
        if (wordIndex >= words.length) {
            ensureCapacity(wordIndex + 1);
        }
        words[wordIndex] |= (1L << bit);
        if (wordIndex >= activeWords) {
            activeWords = wordIndex + 1;
        }
    }

    /**
     * Write a full 64-bit word directly into the given word index and update
     * activeWords if the written word extends the active range. Used by the
     * word-level projection path in projectItem() to avoid per-bit set() overhead.
     * The caller must ensure wordIndex is within the array bounds.
     *
     * @param wordIndex the word index to write
     * @param word      the 64-bit value to store
     */
    public void setWord(int wordIndex, long word) {
        if (wordIndex >= words.length) {
            ensureCapacity(wordIndex + 1);
        }
        words[wordIndex] = word;
        // update activeWords only when a non-zero word extends the active range
        if (word != 0L && wordIndex >= activeWords) {
            activeWords = wordIndex + 1;
        }
    }

    /**
     * Set all bits in the range [from, to) to 1.
     * @param from the start index (inclusive)
     * @param to   the end index (exclusive)
     */
    public void setRange(int from, int to) {
        if (from >= to) {
            return;
        }
        int startWord = from >>> 6;
        int endWord = (to - 1) >>> 6;
        if (endWord >= words.length) {
            ensureCapacity(endWord + 1);
        }

        long startMask = -1L << from;
        long endMask = -1L >>> -to;

        if (startWord == endWord) {
            words[startWord] |= (startMask & endMask);
        } else {
            words[startWord] |= startMask;
            for (int i = startWord + 1; i < endWord; i++) {
                words[i] = -1L;
            }
            words[endWord] |= endMask;
        }

        if (endWord >= activeWords) {
            activeWords = endWord + 1;
        }
    }

    /**
     * Perform a bitwise AND with another bitset in place.
     * Only processes active words and updates activeWords afterward.
     * Returns the cardinality of the result to avoid a separate scan.
     * @param other the other bitset
     * @return the number of set bits in the result
     */
    public int and(LongArrayBitSet other) {
        long[] otherWords = other.words;
        int commonActive = Math.min(activeWords, other.activeWords);
        int newActive = 0;
        int count = 0;

        for (int i = 0; i < commonActive; i++) {
            long w = words[i] & otherWords[i];
            words[i] = w;
            if (w != 0) {
                newActive = i + 1;
                count += Long.bitCount(w);
            }
        }
        // clear words beyond the common active range
        for (int i = commonActive; i < activeWords; i++) {
            words[i] = 0;
        }
        activeWords = newActive;
        return count;
    }

    /**
     * Copy all words from another bitset into this one.
     * @param other the source bitset
     */
    public void copyFrom(LongArrayBitSet other) {
        int otherActive = other.activeWords;
        if (words.length < otherActive) {
            words = new long[otherActive];
        }
        System.arraycopy(other.words, 0, words, 0, otherActive);
        // clear any words that were active before but are not in other
        for (int i = otherActive; i < activeWords; i++) {
            words[i] = 0;
        }
        activeWords = otherActive;
    }

    /**
     * Copy words from source, AND with other, store result in this bitset,
     * and return the cardinality of the result. Combines copy, AND, and
     * cardinality into a single pass over the words.
     * @param source the bitset to copy from
     * @param other  the bitset to AND with
     * @return the number of set bits in the result
     */
    public int copyFromAndAnd(LongArrayBitSet source, LongArrayBitSet other) {
        long[] srcWords = source.words;
        int srcActive = source.activeWords;
        long[] otherWords = other.words;
        int otherActive = other.activeWords;

        int commonActive = Math.min(srcActive, otherActive);
        if (words.length < commonActive) {
            words = new long[commonActive];
        }

        int newActive = 0;
        int count = 0;

        // fused copy + AND + popcount in a single pass
        for (int i = 0; i < commonActive; i++) {
            long w = srcWords[i] & otherWords[i];
            words[i] = w;
            if (w != 0) {
                newActive = i + 1;
                count += Long.bitCount(w);
            }
        }
        // clear any leftover words from a previous wider result
        for (int i = commonActive; i < activeWords; i++) {
            words[i] = 0;
        }
        activeWords = newActive;
        return count;
    }

    /**
     * Create a copy of this bitset.
     * @return a new LongArrayBitSet with the same bits set
     */
    public LongArrayBitSet clone() {
        // allocate at least 1 word to avoid a zero-length array
        int capacity = Math.max(activeWords, 1);
        LongArrayBitSet copy = new LongArrayBitSet(capacity);
        System.arraycopy(words, 0, copy.words, 0, activeWords);
        copy.activeWords = activeWords;
        return copy;
    }

    /**
     * Check if this bitset is a subset of another bitset.
     * Uses word-level comparison with early exit on first mismatch.
     * @param other the other bitset
     * @return true if every bit set in this bitset is also set in other
     */
    public boolean isSubsetOf(LongArrayBitSet other) {
        if (this == other) {
            return true;
        }
        // words[activeWords-1] is guaranteed non-zero by the activeWords invariant,
        // so if this has more active words than other the extra word cannot be a subset
        if (activeWords > other.activeWords) {
            return false;
        }
        long[] otherWords = other.words;
        for (int i = 0; i < activeWords; i++) {
            if ((words[i] & ~otherWords[i]) != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Count the number of bits set to 1.
     * Only processes active words.
     * @return the number of set bits
     */
    public int cardinality() {
        int count = 0;
        for (int i = 0; i < activeWords; i++) {
            count += Long.bitCount(words[i]);
        }
        return count;
    }

    /**
     * Return the index of the next set bit at or after fromIndex, or -1 if none.
     * Only searches up to active words.
     * @param fromIndex the starting bit index
     * @return the index of the next set bit, or -1
     */
    public int nextSetBit(int fromIndex) {
        if (fromIndex < 0) {
            fromIndex = 0;
        }
        int wordIndex = fromIndex >>> 6;
        if (wordIndex >= activeWords) {
            return -1;
        }
        // mask out bits below fromIndex in the starting word
        long word = words[wordIndex] & (-1L << fromIndex);
        while (true) {
            if (word != 0) {
                return (wordIndex << 6) + Long.numberOfTrailingZeros(word);
            }
            if (++wordIndex >= activeWords) {
                return -1;
            }
            word = words[wordIndex];
        }
    }

    /**
     * Ensure the words array has at least the given capacity.
     * Doubles the current length if that is sufficient, otherwise grows to numWords.
     * @param numWords the minimum number of words needed
     */
    private void ensureCapacity(int numWords) {
        if (numWords > words.length) {
            // double to amortize repeated growth calls
            words = Arrays.copyOf(words, Math.max(words.length * 2, numWords));
        }
    }
}