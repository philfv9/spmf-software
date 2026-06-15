package ca.pfv.spmf.algorithms.frequentpatterns.uveclat;
/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger
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
 */

import java.util.ArrayList;
import java.util.List;

/**
 * This class represents an augmented tidlist as used by UV-Eclat.
 * An augmented tidlist stores transaction IDs paired with existential probabilities
 * or expected support contributions. For item x, it stores {tid : P(x, tid)}.
 * For itemset X, it stores {tid : expSup(X, tid)}.
 *
 * @see AlgoUVEclat
 * @see ItemsetUVEclat
 * @author Philippe Fournier-Viger
 */
public class AugmentedTidList {

    /** list of transaction IDs in ascending order */
    private final List<Integer> tids;

    /** list of probabilities corresponding to each tid */
    private final List<Double> probabilities;

    /** cumulative expected support */
    private double expectedSupport;

    /**
     * Constructor
     */
    public AugmentedTidList() {
        tids = new ArrayList<Integer>();
        probabilities = new ArrayList<Double>();
        expectedSupport = 0.0;
    }

    /**
     * Add an entry to this augmented tidlist.
     * @param tid transaction ID
     * @param probability existential probability or expected support contribution
     */
    public void addEntry(int tid, double probability) {
        tids.add(tid);
        probabilities.add(probability);
        expectedSupport += probability;
    }

    /**
     * Get transaction ID at given index.
     * @param index the position
     * @return transaction ID
     */
    public int getTid(int index) {
        return tids.get(index);
    }

    /**
     * Get probability at given index.
     * @param index the position
     * @return probability value
     */
    public double getProbability(int index) {
        return probabilities.get(index);
    }

    /**
     * Get the list of transaction IDs.
     * @return list of tids
     */
    public List<Integer> getTids() {
        return tids;
    }

    /**
     * Get the list of probabilities.
     * @return list of probabilities
     */
    public List<Double> getProbabilities() {
        return probabilities;
    }

    /**
     * Get entries as int arrays for compatibility.
     * @return list of int arrays containing tids
     */
    public List<int[]> getEntries() {
        List<int[]> entries = new ArrayList<int[]>(tids.size());
        for (int tid : tids) {
            entries.add(new int[]{tid});
        }
        return entries;
    }

    /**
     * Get the expected support of the associated itemset.
     * @return expected support
     */
    public double getExpectedSupport() {
        return expectedSupport;
    }

    /**
     * Get the number of entries.
     * @return number of entries
     */
    public int size() {
        return tids.size();
    }

    /**
     * Return string representation.
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
        sb.append("}");
        return sb.toString();
    }
}