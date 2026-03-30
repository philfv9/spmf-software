package ca.pfv.spmf.algorithms.frequentpatterns.incchui;



/**
 * A customized class of Hash Table as used by the IncCHUI algorithm.
 * @see AlgoIncCHUI
 * @author Dam et al. 
 */

import java.util.ArrayList;
import java.util.List;

class HashTable {

	// the internal array for the hash table
	public List<ItemsetIncCHUI>[] table;

	
	public HashTable(int size) {
		table = new ArrayList[size];
	}

	
	public ItemsetIncCHUI retrieveItemset(int[] itemset, int hashcode) {
		
		if (table[hashcode] == null) {
			return null;
		}
		
		for (Object object : table[hashcode]) {
			ItemsetIncCHUI itemsetX = (ItemsetIncCHUI) object;
			
			if (same(itemsetX.getItems(), itemset)) {
				// then return true
				return itemsetX;
			}
		}
		// Otherwise we did not find the itemset in the hashtable
		return null;
	}	
	
	public static boolean same(int[]  itemset1, int[]  itemset2) {
		if(itemset1.length != itemset2.length) {
			return false;
		}
		// Otherwise, we have to compare item by item
                int i = 0;
		// for each item in itemset2, we will try to find it in itemset 1
		for(int j =0; j < itemset2.length-1; j++){
			boolean found = false; // flag to remember if we have find the item at position j
			
			// we search in this itemset starting from the current position i
			while(found == false && i< itemset1.length){
				// if we found the current item from itemset2, we stop searching
				if(itemset1[i] == itemset2[j]){
					found = true;
				}
				
				i++; // continue searching from position  i++
			}
			// if the item was not found in the previous loop, return false
			if(!found){
				return false;
			}
                        i=0;
		}
//		// All items are the same. We return true.
		return true;
	}

	/**
	 * Add an itemset to the hash table.
	 * @param itemset the itemset to be added to the hashtable
	 * @param hashcode the hashcode of the itemset (need to be calculated before by using the
	 *  provided hashcode() method.
	 */
	public void put(ItemsetIncCHUI itemset, int hashcode) {
		// if the position in the array is empty create a new array list
		// for that position
		if (table[hashcode] == null) {
			table[hashcode] = new ArrayList<ItemsetIncCHUI>();
		}
		// store the itemset in the arraylist of that position
		table[hashcode].add(itemset);
	}
	
	

	/**
	 * Calculate the hashcode of an itemset as the sum of the tids of its tidset,
	 * modulo the internal array length.
	 * @param tidset the tidset of the itemset
	 * @return the hashcode (an integer)
	 */
	public int hashCode(int[] itemset) {
		int hashcode = 0;
		// for each tid in the tidset
		for (int i=0; i< itemset.length; i++) {
			// make the sum
			hashcode += itemset[i];
		}
		// If an integer overflow occurs and the hashcode is negative,
		// then we make it positive.
//		if(hashcode < 0){
//			hashcode = 0 - hashcode;
//		}
		// Finally the hashcode is obtained by performing the modulo 
		// operation using the size of the internal array.
		return (hashcode);
	}
}
