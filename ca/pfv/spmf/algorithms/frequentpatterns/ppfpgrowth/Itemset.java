package ca.pfv.spmf.algorithms.frequentpatterns.ppfpgrowth;
import java.util.Arrays;

/*
 * This file is part of the SPMF DATA MINING SOFTWARE *
 * (http://www.philippe-fournier-viger.com/spmf).
 * 
 * SPMF is free software: you can redistribute it and/or modify it under the *
 * terms of the GNU General Public License as published by the Free Software *
 * Foundation, either version 3 of the License, or (at your option) any later *
 * version. SPMF is distributed in the hope that it will be useful, but WITHOUT
 * ANY * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Copyright (c) Peng Yang, Philippe Fournier-Viger, Illyes Hamimid et al. 2026.
 * 
 * Do not remove the copyright and license information.
 */

/**
 * A probabilistic periodic-frequent itemset found by {@link AlgoPPFPgrowth}
 * when the result is kept in memory.
 *
 * @see Itemsets
 */
public class Itemset {

    /** the items, sorted by ascending item id */
    public final int[] itemset;

    /** expected support of the itemset */
    public final double expectedSupport;

    /** periodicity (largest period, in transactions) when every occurrence is present */
    public final int periodicity;

    /** probability that the itemset is periodic */
    public final double periodicProbability;

    /**
     * Constructor.
     *
     * @param items               the items, sorted by ascending item id
     * @param expectedSupport     the expected support
     * @param periodicity         the periodicity
     * @param periodicProbability the probability that the itemset is periodic
     */
    public Itemset(int[] items, double expectedSupport, int periodicity, double periodicProbability) {
        this.itemset = items;
        this.expectedSupport = expectedSupport;
        this.periodicity = periodicity;
        this.periodicProbability = periodicProbability;
    }

    /**
     * Get the items.
     *
     * @return the items, sorted by ascending item id
     */
    public int[] getItems() {
        return itemset;
    }

    /**
     * Get the size of the itemset.
     *
     * @return the number of items
     */
    public int size() {
        return itemset.length;
    }

    @Override
    public String toString() {
        return Arrays.toString(itemset) + " expSup=" + expectedSupport + " per=" + periodicity
                + " P=" + periodicProbability;
    }
}
