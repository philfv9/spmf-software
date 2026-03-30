package ca.pfv.spmf.algorithms.frequentpatterns.talkyg;

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
import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;

/**
 * This class represents a HashTable for storing itemsets found by the TalkyG
 * algorithm to perform the generator check.
 * 
 * A generator is an itemset that has no proper subset with the same support.
 * 
 * @see AlgoTalkyG_Bitset
 * @author Philippe Fournier-Viger
 */
class GeneratorHashTable {

    // Internal structure: map from support value to list of itemsets
    private List<Itemset>[] tableBySupport;
    
    // All stored itemsets for subset checking
    private List<Itemset> allItemsets;

    /**
     * Constructor.
     * @param size size of the internal array for the hash table.
     */
    @SuppressWarnings("unchecked")
	public GeneratorHashTable(int size) {
        tableBySupport = new ArrayList[size];
        allItemsets = new ArrayList<Itemset>();
    }

    /**
     * Check if there exists a subset of the given itemset with the same support.
     * This is used to determine if an itemset is NOT a generator.
     * 
     * @param itemset the itemset to check
     * @param support the support of the itemset
     * @return true if there exists a proper subset with the same support, false otherwise
     */
    public boolean containsSubsetWithSameSupport(int[] itemset, int support) {
        int hashcode = support % tableBySupport.length;
        
        if (tableBySupport[hashcode] == null) {
            return false;
        }
        
        // For each itemset X at that hashcode position (same support bucket)
        for (Itemset itemsetX : tableBySupport[hashcode]) {
            // Check if X has the same support and X is a proper subset of the given itemset
            if (itemsetX.getAbsoluteSupport() == support 
                    && itemsetX.size() < itemset.length
                    && isSubset(itemsetX.getItems(), itemset)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if array1 is a subset of array2.
     * Both arrays are assumed to be sorted.
     */
    private boolean isSubset(int[] subset, int[] superset) {
        if (subset.length > superset.length) {
            return false;
        }
        
        int j = 0;
        for (int i = 0; i < subset.length; i++) {
            // Find subset[i] in superset
            while (j < superset.length && superset[j] < subset[i]) {
                j++;
            }
            if (j >= superset.length || superset[j] != subset[i]) {
                return false;
            }
            j++;
        }
        return true;
    }

    /**
     * Add an itemset to the hash table.
     * 
     * @param itemset the itemset to be added
     * @param support the support of the itemset
     */
    public void put(Itemset itemset, int support) {
        int hashcode = support % tableBySupport.length;
        
        if (tableBySupport[hashcode] == null) {
            tableBySupport[hashcode] = new ArrayList<Itemset>();
        }
        tableBySupport[hashcode].add(itemset);
        allItemsets.add(itemset);
    }

    /**
     * Get all stored itemsets.
     */
    public List<Itemset> getAllItemsets() {
        return allItemsets;
    }
}