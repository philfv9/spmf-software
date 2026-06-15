package ca.pfv.spmf.algorithms.frequentpatterns.tufp;
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see http://www.gnu.org/licenses/.
 * Do not remove copyright or license information.
 */

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a CUP-List (Conditional Uncertain Probability-List) as used by TUFP.
 * A CUP-List stores a pattern name, its expected support, a TEP-List of (TID, probability)
 * tuples, and the maximum existential probability for pruning.
 *
 * @see AlgoTUFP
 * @author Philippe Fournier-Viger
 */
public class CUPList {

    /** list of transaction IDs in ascending order */
    private final List<Integer> tids;

    /** list of existential probabilities corresponding to each tid */
    private final List<Double> probabilities;

    /** expected support of the associated pattern */
    private double expectedSupport;

    /** maximum existential probability in the TEP-List */
    private double max;

    /**
     * Constructor creating an empty CUP-List.
     */
    public CUPList() {
        tids = new ArrayList<Integer>();
        probabilities = new ArrayList<Double>();
        expectedSupport = 0.0;
        max = 0.0;
    }

    /**
     * Add a (TID, probability) entry to this CUP-List.
     * @param tid the transaction identifier
     * @param probability the existential probability of the pattern in this transaction
     */
    public void addEntry(int tid, double probability) {
        tids.add(tid);
        probabilities.add(probability);
        expectedSupport += probability;
        if (probability > max) {
            max = probability;
        }
    }

    /**
     * Get the transaction ID at the given index.
     * @param index the position in the TEP-List
     * @return the transaction ID
     */
    public int getTid(int index) {
        return tids.get(index);
    }

    /**
     * Get the probability at the given index.
     * @param index the position in the TEP-List
     * @return the existential probability
     */
    public double getProbability(int index) {
        return probabilities.get(index);
    }

    /**
     * Get the expected support of the associated pattern.
     * @return expected support
     */
    public double getExpectedSupport() {
        return expectedSupport;
    }

    /**
     * Get the maximum existential probability in the TEP-List.
     * @return maximum probability
     */
    public double getMax() {
        return max;
    }

    /**
     * Get the number of entries in the TEP-List.
     * @return number of entries
     */
    public int size() {
        return tids.size();
    }

    /**
     * Return a string representation of this CUP-List.
     * @return string representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < tids.size(); i++) {
            sb.append(tids.get(i)).append(":").append(probabilities.get(i));
            if (i < tids.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("} expSup=").append(expectedSupport).append(" max=").append(max);
        return sb.toString();
    }
}