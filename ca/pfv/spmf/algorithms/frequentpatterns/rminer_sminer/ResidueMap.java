package ca.pfv.spmf.algorithms.frequentpatterns.rminer_sminer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
/* This file is copyright (c) 2025 Push Sra
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
 * This class represents a residue map for R-Miner algorithm 
 * It maintains a set of items, total utility, and a mapping from transaction IDs to utility values.
 * @author Pushp Sra et al. 
 */
public class ResidueMap {
    // Set of items in this residue map
    Set<Integer> itemSet = new HashSet<>();
    // Total utility of the itemset
    long totUtil = 0; 
    // Map from transaction IDs to utility values
    Map<Integer, Integer> tidUtil = new HashMap<>();

    /** Default constructor */
    public ResidueMap() {
    }

    /**
     * Adds a transaction ID and its utility value to this residue map.
     * Updates the total utility accordingly.
     *
     * @param tid  the transaction ID
     * @param util the utility value for the transaction ID
     */
    public void addToMap(int tid, int util) {
        totUtil += util;
        tidUtil.put(tid, util);
    }

    /**
     * Adds a set of items to this residue map.
     * 
     * @param itemsNew a set of items to add
     */
    public void putItem(Set<Integer> itemsNew) {
        itemSet.addAll(itemsNew);
    }

    @Override
    public String toString() {
        return "ItemSet: " + itemSet + ", Total Utility: " + totUtil + ", TID Utility: " + tidUtil;
    }
}