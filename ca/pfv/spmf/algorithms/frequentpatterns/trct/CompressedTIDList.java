package ca.pfv.spmf.algorithms.frequentpatterns.trct;

/* This file is copyright (c) 2008-2016 Philippe Fournier-Viger
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
* Do not remove copyright and license information from this file.
*/

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a compressed tidset used by the TR-CT algorithm.
 *
 * <p>Runs of consecutive tids {u, u+1, ..., v} with v > u are encoded as the pair
 * (u+1, -(v+1)): positive (u+1) for the run start, negative -(v+1) for the run end.
 * An isolated tid t is stored as (t+1) (always positive and never zero).
 * Adding 1 to all stored values ensures that tid 0 is never stored as the integer 0,
 * keeping the sign bit unambiguous as a run-end marker.</p>
 *
 * @see AlgoTRCT
 * @author Philippe Fournier-Viger, 2016
 */
public class CompressedTIDList {

    /** the items in the pattern represented by this compressed tidset */
    int[] items;

    /** compressed tid entries using 1-based encoding to avoid storing zero */
    List<Integer> elements;

    /** the support of the pattern */
    int support;

    /** the largest periodicity of the pattern */
    int largestPeriodicity;

    /**
     * Constructor for a single-item compressed tidset.
     * @param item the single item represented by this compressed tidset
     */
    public CompressedTIDList(int item) {
        this.items = new int[]{item};
        this.elements = new ArrayList<Integer>();
        this.support = 0;
        this.largestPeriodicity = 0;
    }

    /**
     * Constructor for a multi-item compressed tidset representing an itemset.
     * @param items the items of the itemset represented by this compressed tidset
     */
    public CompressedTIDList(int[] items) {
        this.items = items;
        this.elements = new ArrayList<Integer>();
        this.support = 0;
        this.largestPeriodicity = 0;
    }

    /**
     * Add a new transaction id to the compressed tidset, extending a run or starting a new entry.
     * @param tid the new 0-based transaction id (must exceed every previously added tid)
     */
    public void addTID(int tid) {
        support++;
        int encoded = tid + 1; // shift by 1: stored value is always >= 1
        if (elements.isEmpty()) {
            elements.add(encoded);
            return;
        }
        int lastIdx = elements.size() - 1;
        int last = elements.get(lastIdx);

        if (last < 0) {
            // Last entry is a run-end marker; actual last tid = (-last) - 1
            int actualLast = (-last) - 1;
            if (tid == actualLast + 1) {
                elements.set(lastIdx, -encoded); // extend run
            } else {
                elements.add(encoded);           // new isolated entry
            }
        } else {
            // Last entry is a positive encoded tid; actual tid = last - 1
            int actualLast = last - 1;
            if (tid == actualLast + 1) {
                elements.add(-encoded);          // promote to run
            } else {
                elements.add(encoded);           // new isolated entry
            }
        }
    }

    /**
     * Decode the actual 0-based tid from a positive encoded value.
     * @param encoded the positive encoded entry
     * @return the actual 0-based tid
     */
    static int decodePos(int encoded) {
        return encoded - 1;
    }

    /**
     * Decode the actual 0-based tid from a negative encoded run-end value.
     * @param encoded the negative encoded entry
     * @return the actual 0-based tid at the end of the run
     */
    static int decodeNeg(int encoded) {
        return (-encoded) - 1;
    }

    /**
     * Compute the largest periodicity using MTKPP-compatible gap arithmetic on the compressed tidset.
     * @param databaseSize the total number of transactions in the database
     * @return the largest gap between consecutive occurrences including the head and tail gaps
     */
    public int computeLargestPeriodicity(int databaseSize) {
        if (elements.isEmpty()) {
            return databaseSize;
        }
        int maxPer = 0;
        int prevTid = -1; // matches MTKPP's lastTid = -1 sentinel

        int i = 0;
        while (i < elements.size()) {
            int val = elements.get(i);
            // val is always > 0 here (run-end markers are consumed as i+1)
            int startTid = decodePos(val);

            if (i + 1 < elements.size() && elements.get(i + 1) < 0) {
                // Run: gap before the run start
                int gap = startTid - prevTid;
                if (gap > maxPer) maxPer = gap;
                // prevTid advances to end of run; inner gaps are 1 (never the max unless max=1)
                int endTid = decodeNeg(elements.get(i + 1));
                prevTid = endTid;
                i += 2;
            } else {
                // Isolated tid
                int gap = startTid - prevTid;
                if (gap > maxPer) maxPer = gap;
                prevTid = startTid;
                i++;
            }
        }

        // Tail gap
        int tail = (databaseSize - 1) - prevTid;
        if (tail > maxPer) maxPer = tail;

        return maxPer;
    }

    /**
     * Return the support of this pattern.
     * @return the number of transactions where this pattern appears
     */
    public int getSupport() {
        return support;
    }

    /**
     * Return the single item of a 1-item pattern.
     * @return the item integer value stored in items[0]
     */
    public int getItem() {
        return items[0];
    }
}