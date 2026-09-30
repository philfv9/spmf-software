package ca.pfv.spmf.algorithms.sequentialpatterns.gsp_AGP_fast;
/*
 * Copyright Antonio Gomariz Peñalver 2013
 * 
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license information
 */
import java.util.Arrays;
import java.util.BitSet;

/**
 * Implementation of pattern structure. A pattern is a sequence of elements,
 * each element being an item together with a boolean qualitative abstraction
 * that indicates whether the item occurs in the same itemset as the previous
 * element (true), or strictly after it (false).
 * 
 * @author Antonio Gomariz Peñalver,  Philippe Fournier-Viger (some optimizations)
 */
public class Pattern implements Comparable<Pattern> {

    private int[] items;
    private boolean[] equalRelations;
    private int size;

    /**
     * Number of "strictly after" transitions, i.e. the minimum number of
     * distinct itemsets a sequence must have to possibly contain this pattern.
     */
    private int groupCount;

    /**
     * Cached group boundary array. groupBoundaries[g] is the index of the
     * first pattern element belonging to group g; groupBoundaries[groupCount]
     * is size. Lazily computed, nulled out on any structural mutation.
     */
    private int[] groupBoundaries;

    private BitSet appearingIn;
    private int supportCount;
    private BitSet possibleSequences;

    private int cachedHashCode;
    private boolean hashCodeComputed;

    /**
     * Creates an empty pattern with default backing-array capacity.
     */
    public Pattern() {
        this.items = new int[4];
        this.equalRelations = new boolean[4];
        this.size = 0;
        this.appearingIn = new BitSet();
    }

    /**
     * Creates a 1-element pattern, allocating backing arrays sized to exactly
     * fit this single element (rather than routing through the default
     * 4-capacity constructor plus add()), since 1-element patterns are
     * created once per distinct database item and are never extended in
     * place afterwards (extension always goes through clonePattern()).
     *
     * @param item          the item
     * @param equalRelation whether it occurs in the same itemset as the
     *                      (non-existent) previous element; normally false
     */
    public Pattern(int item, boolean equalRelation) {
        this.items = new int[] { item };
        this.equalRelations = new boolean[] { equalRelation };
        this.size = 1;
        this.groupCount = equalRelation ? 0 : 1;
        this.appearingIn = new BitSet();
    }

    /**
     * Creates a 2-element pattern, allocating backing arrays sized to exactly
     * fit these two elements. Bypasses the default constructor plus add()
     * calls since size-2 candidates can be generated in bulk (up to O(n^2) of
     * them for n frequent items) and, like 1-element patterns, are never
     * extended in place.
     *
     * @param item1          the first item
     * @param item2          the second item
     * @param equalRelation2 whether item2 occurs in the same itemset as item1
     */
    public Pattern(int item1, int item2, boolean equalRelation2) {
        this.items = new int[] { item1, item2 };
        this.equalRelations = new boolean[] { false, equalRelation2 };
        this.size = 2;
        this.groupCount = equalRelation2 ? 1 : 2;
        this.appearingIn = new BitSet();
    }

    /**
     * Internal constructor that takes ownership of the given arrays without
     * copying. Only called from clonePattern() and remapItems().
     *
     * @param items          the backing item array (ownership transferred)
     * @param equalRelations the backing relation array (ownership transferred)
     * @param size           the number of valid elements in the arrays
     * @param groupCount     the number of "strictly after" transitions
     */
    private Pattern(int[] items, boolean[] equalRelations, int size, int groupCount) {
        this.items = items;
        this.equalRelations = equalRelations;
        this.size = size;
        this.groupCount = groupCount;
        this.appearingIn = new BitSet();
    }

    /**
     * Builds a human-readable representation of this pattern and its
     * occurrence set.
     *
     * @return the string representation of this pattern
     */
    @Override
    public String toString() {
        int estimatedSize = size * 10 + getSupport() * 5 + 20;
        StringBuilder result = new StringBuilder(estimatedSize);

        for (int i = 0; i < size; i++) {
            if (equalRelations[i]) {
                result.append(' ').append(items[i]);
            } else {
                result.append(" ->").append(' ').append(items[i]);
            }
        }

        result.append("\t[");
        boolean first = true;
        for (int i = appearingIn.nextSetBit(0); i >= 0; i = appearingIn.nextSetBit(i + 1)) {
            if (!first) {
                result.append(", ");
            }
            result.append(i);
            first = false;
        }
        result.append("]");
        return result.toString();
    }

    /**
     * Builds the SPMF output-format representation of this pattern.
     *
     * @param outputSequenceIdentifiers if true, appends the list of sequence
     *                                  ids where this pattern occurs
     * @return the SPMF-format line describing this pattern
     */
    public String toStringToFile(boolean outputSequenceIdentifiers) {
        int cardinality = getSupport();
        int estimatedSize = size * 8 + 30;
        if (outputSequenceIdentifiers) {
            estimatedSize += cardinality * 5;
        }
        StringBuilder result = new StringBuilder(estimatedSize);

        for (int i = 0; i < size; i++) {
            if (i == size - 1) {
                if (i != 0) {
                    appendElementToFile(result, i);
                } else {
                    result.append(items[i]);
                }
                result.append(" -1");
            } else if (i == 0) {
                result.append(items[i]);
            } else {
                appendElementToFile(result, i);
            }
        }

        result.append(" #SUP: ").append(cardinality);

        if (outputSequenceIdentifiers) {
            result.append(" #SID:");
            for (int i = appearingIn.nextSetBit(0); i >= 0; i = appearingIn.nextSetBit(i + 1)) {
                result.append(' ').append(i);
            }
        }

        return result.toString();
    }

    /**
     * Appends element i of this pattern to sb in SPMF format, prefixing it
     * with an itemset separator ("-1") when it starts a new itemset.
     *
     * @param sb the buffer to append to
     * @param i  the element index
     */
    private void appendElementToFile(StringBuilder sb, int i) {
        if (!equalRelations[i]) {
            sb.append(" -1");
        }
        sb.append(' ').append(items[i]);
    }

    /**
     * Clones this pattern, sizing the backing arrays for exactly one more
     * element so the immediately following add() in generateCandidate() never
     * triggers a grow-and-copy.
     *
     * @return a new Pattern with the same elements as this one
     */
    public Pattern clonePattern() {
        int newCapacity = size + 1;
        return new Pattern(
                Arrays.copyOf(items, newCapacity),
                Arrays.copyOf(equalRelations, newCapacity),
                size,
                groupCount);
    }

    /**
     * Returns a new Pattern with every item translated through itemMapping,
     * sharing this pattern's appearingIn BitSet directly (it is already final
     * at the call site in AlgoGSP).
     *
     * The equalRelations array is also shared rather than copied: the
     * returned Pattern is a write-once output copy that is never passed
     * through add() or reset(), and neither is this pattern once it has
     * reached this point in the algorithm (only clonePattern(), which always
     * makes its own copies, is used to derive further patterns from it), so
     * sharing the array cannot cause the two patterns to interfere.
     *
     * @param itemMapping the renamed-id -> original-item mapping
     * @return a new Pattern with translated items
     */
    public Pattern remapItems(int[] itemMapping) {
        int[] newItems = new int[size];
        for (int i = 0; i < size; i++) {
            newItems[i] = itemMapping[items[i]];
        }
        Pattern remapped = new Pattern(newItems, this.equalRelations, size, groupCount);
        remapped.setAppearingIn(appearingIn);
        return remapped;
    }

    /**
     * Returns the item at the given position.
     *
     * @param i the position
     * @return the item at position i
     */
    public int getItem(int i) {
        return items[i];
    }

    /**
     * Returns whether the element at position i occurs in the same itemset
     * as the previous element.
     *
     * @param i the position
     * @return true if element i shares its itemset with element i-1
     */
    public boolean isEqualRelation(int i) {
        return equalRelations[i];
    }

    /**
     * Returns the last item of this pattern.
     *
     * @return the last item
     */
    public int getLastItem() {
        return items[size - 1];
    }

    /**
     * Returns whether the last element occurs in the same itemset as the
     * previous one.
     *
     * @return true if the last element shares its itemset with the previous one
     */
    public boolean isLastEqualRelation() {
        return equalRelations[size - 1];
    }

    /**
     * Appends an element to this pattern, growing the backing arrays if
     * necessary.
     *
     * @param item          the item to append
     * @param equalRelation whether it occurs in the same itemset as the
     *                      previous element
     */
    public void add(int item, boolean equalRelation) {
        if (size == items.length) {
            int newCapacity = (items.length == 0) ? 4 : items.length * 2;
            items = Arrays.copyOf(items, newCapacity);
            equalRelations = Arrays.copyOf(equalRelations, newCapacity);
        }
        items[size] = item;
        equalRelations[size] = equalRelation;
        size++;
        if (!equalRelation) {
            groupCount++;
        }
        groupBoundaries = null;
        hashCodeComputed = false;
    }

    /**
     * Resets this pattern to empty, keeping its backing arrays for reuse.
     */
    public void reset() {
        size = 0;
        groupCount = 0;
        groupBoundaries = null;
        hashCodeComputed = false;
    }

    /**
     * Returns the number of elements in this pattern.
     *
     * @return the number of elements
     */
    public int size() {
        return size;
    }

    /**
     * Returns the minimum number of itemsets a sequence must have to
     * possibly contain this pattern.
     *
     * @return the minimum required number of itemsets
     */
    public int getMinItemsetsRequired() {
        return groupCount;
    }

    /**
     * Returns the cached group boundary array, computing it lazily if needed.
     * groupBoundaries[g] is the index of the first pattern element in group g;
     * groupBoundaries[groupCount] == size.
     *
     * A "group" is a maximal run of consecutive elements that must all land in
     * the same itemset: the first element of the group has equalRelation==false
     * (or is element 0), and every subsequent element in the run has
     * equalRelation==true.
     *
     * This is computed once per candidate and cached, rather than being
     * re-derived inside matchInSequence for every sequence that candidate is
     * tested against.
     *
     * @return the group boundary array
     */
    public int[] getGroupBoundaries() {
        if (groupBoundaries == null) {
            // groupCount "false" transitions, so groupCount groups
            int[] bounds = new int[groupCount + 1];
            int g = 1;
            bounds[0] = 0;
            for (int i = 1; i < size; i++) {
                if (!equalRelations[i]) {
                    bounds[g++] = i;
                }
            }
            bounds[g] = size;
            groupBoundaries = bounds;
        }
        return groupBoundaries;
    }

    /**
     * Compares this pattern to another lexicographically by (item, relation)
     * pairs, then by size.
     *
     * @param arg the pattern to compare to
     * @return a negative, zero, or positive number as this pattern is less
     *         than, equal to, or greater than arg
     */
    @Override
    public int compareTo(Pattern arg) {
        if (arg == null) {
            return 1;
        }
        int minSize = Math.min(this.size, arg.size);
        for (int i = 0; i < minSize; i++) {
            int itemCmp = Integer.compare(this.items[i], arg.items[i]);
            if (itemCmp != 0) {
                return itemCmp;
            }
            int relCmp = Boolean.compare(this.equalRelations[i], arg.equalRelations[i]);
            if (relCmp != 0) {
                return relCmp;
            }
        }
        return Integer.compare(this.size, arg.size);
    }

    /**
     * Checks structural equality (same items and relations in the same order).
     *
     * @param arg the object to compare to
     * @return true if arg is a Pattern with the same elements as this one
     */
    @Override
    public boolean equals(Object arg) {
        if (this == arg) {
            return true;
        }
        if (!(arg instanceof Pattern)) {
            return false;
        }
        Pattern other = (Pattern) arg;
        if (this.size != other.size) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            if (this.items[i] != other.items[i] || this.equalRelations[i] != other.equalRelations[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Computes (and caches) a hash code consistent with equals().
     *
     * @return the hash code of this pattern
     */
    @Override
    public int hashCode() {
        if (!hashCodeComputed) {
            int hash = 1;
            for (int i = 0; i < size; i++) {
                hash = 31 * hash + items[i];
                hash = 31 * hash + (equalRelations[i] ? 1231 : 1237);
            }
            cachedHashCode = hash;
            hashCodeComputed = true;
        }
        return cachedHashCode;
    }

    /**
     * Returns the live internal BitSet of sequence IDs where this pattern
     * appears. Callers must not mutate the returned value.
     *
     * @return the occurrence set of this pattern
     */
    public BitSet getAppearingIn() {
        return appearingIn;
    }

    /**
     * Stores the given BitSet directly (no defensive copy). Updates the
     * cached support count from it. Caller must not mutate it afterwards.
     *
     * @param appearingIn the occurrence set to install
     */
    public void setAppearingIn(BitSet appearingIn) {
        this.appearingIn = appearingIn;
        this.supportCount = appearingIn.cardinality();
    }

    /**
     * Records that this pattern appears in the given sequence.
     * The guard is needed during parsing, where an item can appear in
     * multiple itemsets of the same sequence.
     *
     * @param sequenceId the id of the sequence where this pattern was found
     */
    public void addAppearance(int sequenceId) {
        if (!appearingIn.get(sequenceId)) {
            appearingIn.set(sequenceId);
            supportCount++;
        }
    }

    /**
     * Records an appearance without a uniqueness guard. Only safe to call
     * from countSupport(), where seqIds come from iterating distinct bits of
     * possibleSequences so uniqueness is already guaranteed.
     *
     * @param sequenceId the id of the sequence where this pattern was found
     */
    public void addAppearanceUnchecked(int sequenceId) {
        appearingIn.set(sequenceId);
        supportCount++;
    }

    /**
     * Returns the current support (number of sequences this pattern occurs in).
     *
     * @return the support count
     */
    public int getSupport() {
        return supportCount;
    }

    /**
     * Returns the set of sequence ids that still need to be checked for
     * this pattern (typically the intersection of its parents' occurrence
     * sets), or the occurrence set itself if none was set explicitly.
     *
     * @return the set of candidate sequence ids to check
     */
    public BitSet getPossibleSequences() {
        return possibleSequences != null ? possibleSequences : appearingIn;
    }

    /**
     * Stores the given BitSet directly (no defensive copy). Caller must not
     * mutate it afterwards.
     *
     * @param possibleSequences the candidate sequence-id set to install
     */
    public void setPossibleSequences(BitSet possibleSequences) {
        this.possibleSequences = possibleSequences;
    }
}