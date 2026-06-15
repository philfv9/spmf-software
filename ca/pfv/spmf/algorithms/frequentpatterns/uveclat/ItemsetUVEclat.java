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
 * Do not remove copyright or license information.
 */

import java.util.List;

/**
 * This class represents a frequent itemset as used by UV-Eclat.
 * It stores an ordered list of item IDs, an augmented tidlist, and the expected support.
 *
 * @see AlgoUVEclat
 * @see AugmentedTidList
 * @author Philippe Fournier-Viger
 */
public class ItemsetUVEclat {

    /** ordered list of item IDs in ascending order */
    private final List<Integer> items;

    /** augmented tidlist for this itemset */
    private final AugmentedTidList tidList;

    /** expected support of this itemset */
    private final double expectedSupport;

    /**
     * Constructor
     * @param items list of item IDs
     * @param tidList augmented tidlist
     * @param expectedSupport expected support value
     */
    public ItemsetUVEclat(List<Integer> items, AugmentedTidList tidList, double expectedSupport) {
        this.items = items;
        this.tidList = tidList;
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
     * Get the augmented tidlist.
     * @return augmented tidlist
     */
    public AugmentedTidList getTidList() {
        return tidList;
    }

    /**
     * Get the expected support.
     * @return expected support
     */
    public double getExpectedSupport() {
        return expectedSupport;
    }

    /**
     * Get the size of this itemset.
     * @return number of items
     */
    public int size() {
        return items.size();
    }

    /**
     * Get the last item ID in this itemset.
     * @return last item ID
     */
    public int getLastItem() {
        return items.get(items.size() - 1);
    }

    /**
     * Return string representation of this itemset.
     * @return space-separated item IDs
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