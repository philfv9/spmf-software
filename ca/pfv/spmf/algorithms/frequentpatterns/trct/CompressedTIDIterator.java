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

import java.util.List;

/**
 * This class is an iterator over the actual transaction ids stored in a compressed tidset.
 *
 * <p>The compressed tidset encodes runs of consecutive tids as (positive start, negative end).
 * This iterator expands those runs transparently, yielding one actual tid at a time.</p>
 *
 * @see AlgoTRCT
 * @author Philippe Fournier-Viger, 2016
 */
public class CompressedTIDIterator {

    /** the compressed elements list being iterated */
    private List<Integer> elements;

    /** current index into the elements list */
    private int idx;

    /** current actual tid being pointed to (within a run or at a single entry) */
    private int currentTid;

    /** the end of the current run if we are expanding a run, or -1 if not in a run */
    private int runEnd;

    /** whether there is a next element available */
    private boolean hasNext;

    /**
     * Constructor that initializes the iterator over a compressed elements list.
     * @param elements the list of compressed tid entries to iterate over
     */
    public CompressedTIDIterator(List<Integer> elements) {
        this.elements = elements;
        this.idx = 0;
        this.runEnd = -1;
        this.hasNext = !elements.isEmpty();
        if (hasNext) {
            advance();
        }
    }

    /**
     * Advance the internal state to point to the next actual tid.
     */
    private void advance() {
        if (idx >= elements.size()) {
            hasNext = false;
            return;
        }

        int val = elements.get(idx);

        if (val > 0) {
            // Positive entry: check if the next entry is negative (run) or not
            if (idx + 1 < elements.size() && elements.get(idx + 1) < 0) {
                // Start of a run: current tid = val, run ends at -elements[idx+1]
                currentTid = val;
                runEnd = -elements.get(idx + 1);
                // We stay at idx until the run is exhausted
            } else {
                // Isolated positive tid
                currentTid = val;
                runEnd = -1;
                idx++;
            }
        }
        hasNext = true;
    }

    /**
     * Return true if there are more actual tids to iterate over.
     * @return true if there is a next tid
     */
    public boolean hasNext() {
        return hasNext;
    }

    /**
     * Peek at the current actual tid without advancing the iterator.
     * @return the current actual tid
     */
    public int peek() {
        return currentTid;
    }

    /**
     * Consume the current actual tid and advance to the next one.
     */
    public void next() {
        if (runEnd >= 0 && currentTid < runEnd) {
            // Still inside a run
            currentTid++;
        } else {
            // Finished current run or isolated entry: move to next element
            if (runEnd >= 0) {
                // We were in a run, skip past the two entries (pos + neg)
                idx += 2;
                runEnd = -1;
            }
            // idx was already incremented for isolated case in advance()
            if (idx >= elements.size()) {
                hasNext = false;
            } else {
                advance();
            }
        }
    }
}