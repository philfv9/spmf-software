package ca.pfv.spmf.algorithms.frequentpatterns.tm;

import java.util.BitSet;
import java.util.List;
/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger
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
 * Inner class to hold either an interval list or a tid list (as BitSet)
 * 
 * @author Philippe Fournier-Viger
 */
public class TransactionList {
    /** List of intervals or null */
    List<Interval> intervals;
    
    /** BitSet representation of tids - OPTIMIZATION for fast intersection */
    BitSet tidBitSet;
    
    /** The support */
    int support;
    
    /** Total number of transactions (needed for BitSet sizing) */
    int transactionCount;

    /**
     * Constructor for interval-based representation
     * 
     * @param intervals        the interval list
     * @param support          the support value
     * @param transactionCount total number of transactions
     */
    public TransactionList(List<Interval> intervals, int support, int transactionCount) {
        this.intervals = intervals;
        this.tidBitSet = null;
        this.support = support;
        this.transactionCount = transactionCount;
    }

    /**
     * Constructor for BitSet-based representation - OPTIMIZATION
     * 
     * @param tidBitSet the BitSet representing transaction ids
     * @param support   the support value
     */
    public TransactionList(BitSet tidBitSet, int support) {
        this.intervals = null;
        this.tidBitSet = tidBitSet;
        this.support = support;
        this.transactionCount = 0; // Not needed for BitSet
    }

    /**
     * Get the list size (l_i) as defined in the paper
     * For BitSet, returns the number of contiguous ranges (approximating interval count)
     * 
     * @return the list size
     */
    public int getListSize() {
        if (isUsingIntervals()) {
            return (intervals != null) ? intervals.size() : 0;
        } else if (tidBitSet != null) {
            // For BitSet, the "list size" concept is approximated by 
            // counting contiguous ranges
            return countContiguousRanges();
        }
        return 0;
    }

    /**
     * Count the number of contiguous ranges in the BitSet
     * This approximates the "list size" for compression coefficient calculation
     * 
     * @return number of contiguous ranges
     */
    private int countContiguousRanges() {
        if (tidBitSet == null || tidBitSet.isEmpty()) {
            return 0;
        }
        
        int ranges = 0;
        int i = tidBitSet.nextSetBit(0);
        while (i >= 0) {
            ranges++;
            // Find the end of this contiguous range
            int j = tidBitSet.nextClearBit(i);
            // Move to next set bit
            if (j < 0) {
                break; // No more clear bits, we're done
            }
            i = tidBitSet.nextSetBit(j);
        }
        return ranges;
    }

    /**
     * Get the support (s_i)
     * 
     * @return the support
     */
    public int getSupport() {
        return support;
    }

    /**
     * Set the support value
     * 
     * @param support the new support value
     */
    public void setSupport(int support) {
        this.support = support;
    }

    /**
     * Convert to BitSet representation if currently using intervals
     * OPTIMIZATION: BitSet provides faster intersection operations
     */
    public void convertToBitSet() {
        if (isUsingIntervals()) {
            tidBitSet = new BitSet(transactionCount + 1);
            for (Interval interval : intervals) {
                // BitSet.set(fromIndex, toIndex) sets bits from fromIndex (inclusive) to toIndex (exclusive)
                tidBitSet.set(interval.start, interval.end + 1);
            }
            intervals = null;
        }
    }

    /**
     * Check if currently using interval representation
     * 
     * @return true if using intervals
     */
    public boolean isUsingIntervals() {
        return intervals != null;
    }

    /**
     * Check if currently using BitSet representation
     * 
     * @return true if using BitSet
     */
    public boolean isUsingBitSet() {
        return tidBitSet != null;
    }

    /**
     * Get the intervals (may be null if using different representation)
     * 
     * @return the interval list or null
     */
    public List<Interval> getIntervals() {
        return intervals;
    }

    /**
     * Get the BitSet (may be null if using different representation)
     * 
     * @return the BitSet or null
     */
    public BitSet getTidBitSet() {
        return tidBitSet;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TransactionList[support=").append(support);
        if (isUsingIntervals()) {
            sb.append(", intervals=").append(intervals);
        } else if (tidBitSet != null) {
            sb.append(", bitset_cardinality=").append(tidBitSet.cardinality());
        }
        sb.append("]");
        return sb.toString();
    }
}