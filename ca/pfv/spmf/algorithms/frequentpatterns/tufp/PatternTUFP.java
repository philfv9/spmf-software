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

import java.util.List;

/**
 * This class represents a pattern (itemset with its CUP-List) as used by TUFP.
 * It stores an ordered list of item IDs, a CUP-List, and the expected support.
 *
 * @see AlgoTUFP
 * @see CUPList
 * @author Philippe Fournier-Viger
 */
public class PatternTUFP {

    /** ordered list of item IDs in ascending order */
    private final List<Integer> items;

    /** CUP-List for this pattern */
    private final CUPList cupList;

    /** expected support of this pattern */
    private final double expectedSupport;

    /**
     * Constructor.
     * @param items list of item IDs forming this pattern
     * @param cupList the CUP-List of this pattern
     * @param expectedSupport the expected support of this pattern
     */
    public PatternTUFP(List<Integer> items, CUPList cupList, double expectedSupport) {
        this.items = items;
        this.cupList = cupList;
        this.expectedSupport = expectedSupport;
    }

    /**
     * Get the list of item IDs.
     * @return list of items
     */
    public List<Integer> getItems() {
        return items;
    }

    /**
     * Get the CUP-List of this pattern.
     * @return CUP-List
     */
    public CUPList getCupList() {
        return cupList;
    }

    /**
     * Get the expected support of this pattern.
     * @return expected support
     */
    public double getExpectedSupport() {
        return expectedSupport;
    }

    /**
     * Get the number of items in this pattern.
     * @return number of items
     */
    public int size() {
        return items.size();
    }

    /**
     * Return a string representation of this pattern as space-separated item IDs.
     * @return string representation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            sb.append(items.get(i));
            if (i < items.size() - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }
}