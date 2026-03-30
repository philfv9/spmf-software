/* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* It was obtained from the LAC library under the GNU GPL license, but already contained
* some code from SPMF. It was then adapted again for integration in SPMF.
* 
* @Copyright original version LAC 2019   @copyright of reused code from SPMF and modifications 2021
*
* SPMF is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* SPMF is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with SPMF.  If not, see <http://www.gnu.org/licenses/>.
* 
*/
package ca.pfv.spmf.algorithms.classifiers.accf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.ArraysAlgos;
import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.Rule;

/**
 * An adaptation of the CHARM algorithm. It is able to directly obtain rules
 * without requiring to mine all patterns first. Based on the SPMF
 * implementation of Charm with modifications for generating rules.
 * 
 * @see AlgoACCF
 */
public class CHARMForACCF {
	/**
	 * Map to count the support of each class (key = class, value = bitmap
	 * representing the records of that class)
	 */
	private Map<Short, BitSetSupport> klasses;

	/**
	 * The hash table for storing itemsets for closeness checking (an optimization)
	 */
	private SupersetTable supersetTable;

	/**
	 * Obtained rules
	 */
	private List<Rule> rules;

	/**
	 * Constructor
	 */
	public CHARMForACCF() {
		// default constructor
	}

	/**
	 * Run the algorithm and obtain class association rules
	 * 
	 * @param dataset a dataset
	 * @param minSup  the minimum support threshold
	 * @param minConf the minimum confidence threshold
	 */
	public List<Rule> run(Dataset dataset, double minSup, double minConf) {
		// calculate the minimum support as a number of transaction
		long minSupRelative = (long) Math.ceil(minSup * dataset.getInstances().size());

		// The rules that will be the model
		this.rules = new ArrayList<Rule>();

		// Create the hash table to store (candidate) closed itemsets
		this.supersetTable = new SupersetTable();

		// Create a map to count the support of each class
		klasses = new HashMap<Short, BitSetSupport>();

		// Calculate the support of each item
		// The result is a map that store the support of each item
		// (key: item value: bitset indicating each transaction containing the itemset
		// and its support)
		Map<Short, BitSetSupport> mapItemSingletons = this.generateSingletons(dataset);

		// Find the list of frequent items
		List<Short> frequentItems = new ArrayList<Short>();
		// For each item
		for (Entry<Short, BitSetSupport> entry : mapItemSingletons.entrySet()) {

			// the item
			Short item = entry.getKey();

			// the list of transactions containing that item
			BitSetSupport bitset = entry.getValue();

			// If the item is frequent
			if (bitset.support >= minSupRelative) {
				// Add the item to the list of frequent items
				frequentItems.add(item);
			}
		}

		// Sort the list of items by the total order of increasing support.
		Collections.sort(frequentItems, new Comparator<Short>() {
			@Override
			public int compare(Short arg0, Short arg1) {
				return (int) (mapItemSingletons.get(arg0).support - mapItemSingletons.get(arg1).support);
			}
		});

		// Try to combine each item with each other to generate itemsets with two items.
		// For each frequent item I
		for (int i = 0; i < frequentItems.size(); i++) {
			// the item I
			Short itemI = frequentItems.get(i);
			if (itemI == null)
				continue;

			// the list of transactions containing item I
			BitSetSupport bitsetI = mapItemSingletons.get(itemI);

			Short[] itemsetI = new Short[] { itemI };

			List<Short[]> prefixedItems = new ArrayList<Short[]>();
			List<BitSetSupport> prefixedBitsets = new ArrayList<BitSetSupport>();

			// For each frequent item J
			for (int j = i + 1; j < frequentItems.size(); j++) {
				// the item J
				Short itemJ = frequentItems.get(j);
				if (itemJ == null)
					continue;

				// The list of transactions containing item J
				BitSetSupport bitsetJ = mapItemSingletons.get(itemJ);
				// Calculate the list of transactions containing I and J together
				BitSetSupport bitsetUnion = and(bitsetI, bitsetJ);

				// If the new pattern {I,J} is not frequent, we dont need to consider it
				if (bitsetUnion.support < minSupRelative) {
					continue;
				}

				// Otherwise, we check four properties
				if (bitsetI.support == bitsetJ.support && bitsetUnion.support == bitsetI.support) {
					// Property 1, where I is replaced by the new created union. j is removed
					frequentItems.set(j, null);
					Short[] union = new Short[itemsetI.length + 1];
					System.arraycopy(itemsetI, 0, union, 0, itemsetI.length);
					union[itemsetI.length] = itemJ;
					itemsetI = union;
				} else if (bitsetI.support < bitsetJ.support && bitsetUnion.support == bitsetI.support) {
					// Property 2, where I is replaced by he union. But I is not removed
					Short[] union = new Short[itemsetI.length + 1];
					System.arraycopy(itemsetI, 0, union, 0, itemsetI.length);
					union[itemsetI.length] = itemJ;
					itemsetI = union;
				} else if (bitsetI.support > bitsetJ.support && bitsetUnion.support == bitsetJ.support) {
					// Property 3, where j is removed and union add to the prefixed items
					frequentItems.set(j, null);
					prefixedItems.add(new Short[] { itemJ });
					prefixedBitsets.add(bitsetUnion);
				} else {
					// Property4, union is added to prefixedItems
					prefixedItems.add(new Short[] { itemJ });
					prefixedBitsets.add(bitsetUnion);
				}
			}

			// If there are some prefixed items, we process them
			if (prefixedItems.size() > 0) {
				processPrefixedItems(itemsetI, prefixedItems, prefixedBitsets, minConf, minSupRelative);
			}

			// Generate rules for the current frequent itemset I
			generateRules(null, itemsetI, bitsetI, minConf);
		}
		return this.rules;
	}

	/**
	 * Find the list of transactions containing each item (stored as a bitset)
	 * 
	 * @param dataset the dataset
	 * 
	 * @return a map that store the support of each item (key: item value: bitset
	 *         indicating each transaction containing the itemset and its support)
	 */
	private Map<Short, BitSetSupport> generateSingletons(Dataset dataset) {
		// Map to store the list of transactions of each item (key: item value: bitset
		// that represents the list of transactions)
		Map<Short, BitSetSupport> singletons = new HashMap<Short, BitSetSupport>();

		// For each record (transaction) of the dataset
		List<Instance> instances = dataset.getInstances();
		for (int i = 0; i < instances.size(); i++) {
			Instance instance = instances.get(i);

			// For each attribute value of the record
			for (int j = 0; j < dataset.getAttributes().size(); j++) {
				// The item (value)
				Short item = instance.getItems()[j];
				// The bitset of this item
				BitSetSupport tids = singletons.get(item);

				// If the bitset does not exist, create a new one
				if (tids == null) {
					tids = new BitSetSupport();
					singletons.put(item, tids);
				}

				// Add the current record ID to the bitset of this item
				tids.bitset.set(i);
				// Increase the support of that item.
				tids.support++;
			}

			// Get the class of the current record
			Short klass = instance.getKlass();

			// Get the list of transactions for that class
			BitSetSupport tids = klasses.get(klass);
			// If it does not exist, create this list as a bitset
			if (tids == null) {
				tids = new BitSetSupport();
				klasses.put(klass, tids);
			}

			// Add the current record id to that list
			tids.bitset.set(i);
			// Increase the suport of that class
			tids.support++;

		}
		// Return the frequent items
		return singletons;
	}

	/**
	 * Process all itemsets from a prefixed items to generate even larger itemsets
	 * 
	 * @param prefix           a prefix
	 * @param prefixedItemsets some prefixed itemsets
	 * @param prefixedBitsets  the bitsets of these prefixed itemsets
	 * @param minConf          minimum confidence
	 * @param minSupRelative   minimum support
	 */
	private void processPrefixedItems(Short[] prefix, List<Short[]> prefixedItemsets,
			List<BitSetSupport> prefixedBitsets, double minConf, double minSupRelative) {
		// If there is only one prefixed itemset
		if (prefixedItemsets.size() == 1) {
			Short[] itemsetI = prefixedItemsets.get(0);
			BitSetSupport bitsetI = prefixedBitsets.get(0);

			generateRules(prefix, itemsetI, bitsetI, minConf);
			return;
		} else if (prefixedItemsets.size() == 2) {
			// if there are two prefixed itemsets I and J
			Short[] itemsetI = prefixedItemsets.get(0);
			BitSetSupport bitsetI = prefixedBitsets.get(0);

			Short[] itemsetJ = prefixedItemsets.get(1);
			BitSetSupport bitsetJ = prefixedBitsets.get(1);

			// Calculate the bitset of itemset I U J by intersecting the bitsets of I and
			// the bitset of J
			BitSetSupport bitsetSupportIJ = and(bitsetI, bitsetJ);

			// If I U J is a frequent itemset
			if (bitsetSupportIJ.support >= minSupRelative) {
				Short[] suffixIJ = ArraysAlgos.concatenate(itemsetI, itemsetJ);
				generateRules(prefix, suffixIJ, bitsetSupportIJ, minConf);
			}

			// If support is not the same, it coulld be closed, so we have to generate rules
			if (bitsetSupportIJ.support != bitsetI.support) {
				generateRules(prefix, itemsetI, bitsetI, minConf);
			}
			if (bitsetSupportIJ.support != bitsetJ.support) {
				generateRules(prefix, itemsetJ, bitsetJ, minConf);
			}
			return;
		}
		// If there are more than two prefixed itemsets, then combine
		// prefixed itemsets to generate larger itemsets.

		// For each itemset I
		for (int i = 0; i < prefixedItemsets.size(); i++) {
			Short[] itemsetI = prefixedItemsets.get(i);
			if (itemsetI == null) {
				continue;
			}

			BitSetSupport bitsetI = prefixedBitsets.get(i);

			List<Short[]> prefixedIitemsets = new ArrayList<Short[]>();
			List<BitSetSupport> prefixedIBitsets = new ArrayList<BitSetSupport>();

			// For each itemset J
			for (int j = i + 1; j < prefixedItemsets.size(); j++) {
				Short[] itemsetJ = prefixedItemsets.get(j);

				if (itemsetJ == null) {
					continue;
				}
				BitSetSupport bitsetJ = prefixedBitsets.get(j);

				// Create the bitset of itemset I U J.
				BitSetSupport bitsetUnion = and(bitsetI, bitsetJ);

				// Check if the itemset I U J is frequent
				if (bitsetUnion.support < minSupRelative) {
					continue;
				}

				// Then check the 4 properties of CHARM
				if (bitsetI.support == bitsetJ.support && bitsetUnion.support == bitsetI.support) {
					// Property 1, where I is replaced by the new created union. j is removed
					prefixedItemsets.set(j, null);
					prefixedBitsets.set(j, null);
					Short[] union = ArraysAlgos.concatenate(itemsetI, itemsetJ);
					itemsetI = union;
				} else if (bitsetI.support < bitsetJ.support && bitsetUnion.support == bitsetI.support) {
					// Property 2, where I is replaced by he union. But I is not removed
					Short[] union = ArraysAlgos.concatenate(itemsetI, itemsetJ);
					itemsetI = union;
				} else if (bitsetI.support > bitsetJ.support && bitsetUnion.support == bitsetJ.support) {
					// Property 3, where j is removed and union add to the prefixed items
					prefixedItemsets.set(j, null);
					prefixedBitsets.set(j, null);
					prefixedIitemsets.add(itemsetJ);
					prefixedIBitsets.add(bitsetUnion);
				} else {
					// Property4, union is added to prefixedItems
					prefixedIitemsets.add(itemsetJ);
					prefixedIBitsets.add(bitsetUnion);
				}
			}

			// If there is still some prefixed itemsets
			if (prefixedIitemsets.size() > 0) {
				Short[] newPrefix = ArraysAlgos.concatenate(prefix, itemsetI);
				// Process them
				processPrefixedItems(newPrefix, prefixedIitemsets, prefixedIBitsets, minConf, minSupRelative);
			}
			generateRules(prefix, itemsetI, bitsetI, minConf);
		}
	}

	/**
	 * Generate rules from current prefix and suffix
	 * 
	 * @param prefix a prefix
	 * @param suffix a suffix
	 * @param bitset the bitset 
	 * @param minConf minimum confidence
	 */
	private void generateRules(Short[] prefix, Short[] suffix, BitSetSupport bitset, double minConf) {
		// Concatenate the suffix and prefix of that itemset.
		Short[] items;
		if (prefix == null) {
			items = suffix;
		} else {
			items = ArraysAlgos.concatenate(prefix, suffix);
		}

		// Sort by lexical order
		Arrays.sort(items);

		ItemsetACCF itemset = new ItemsetACCF(items, bitset.support);

		// If there are not any superset in the superset table with the same support,
		// the itemset is closed
		if (supersetTable.isSuperset(itemset, bitset.bitset)) {
			// Generate rules for current item
			for (Entry<Short, BitSetSupport> klass : klasses.entrySet()) {
				BitSetSupport klassbitset = klass.getValue();
				long supportKlass = klassbitset.support;
				Short itemKlass = klass.getKey();

				// Calculate the list of transactions (bitset) of the rule
				BitSetSupport bitsetRule = and(bitset, klassbitset);
				
				// Create the rule
				Rule rule = new RuleACCF(items, itemKlass);
				rule.setSupportAntecedent(bitset.support);
				rule.setSupportRule(bitsetRule.support);
				rule.setSupportKlass(supportKlass);

				// If a high confidence rule, then keep it
				if (rule.getConfidence() >= minConf) {
					rules.add(rule);
				}
			}
			// Add the itemset to the hash table
			supersetTable.add(itemset, bitset.bitset);
		}
	}

	/**
	 * Anonymous inner class to store a bitset and its cardinality. Storing the
	 * cardinality is useful because the cardinality() method of a bitset in Java is
	 * very expensive.
	 */
	private class BitSetSupport {
		BitSet bitset = new BitSet();
		long support;
	}

	/**
	 * Perform the intersection of two tidsets for itemsets containing more than one
	 * item.
	 * 
	 * @param tidsetI the first tidset
	 * @param tidsetJ the second tidset
	 * @return the resulting tidset and its support
	 */
	private BitSetSupport and(BitSetSupport tidsetI, BitSetSupport tidsetJ) {
		// Create the new tidset and perform the logical AND to intersect the tidset
		BitSetSupport bitsetSupportIJ = new BitSetSupport();
		bitsetSupportIJ.bitset = (BitSet) tidsetI.bitset.clone();
		bitsetSupportIJ.bitset.and(tidsetJ.bitset);
		// set the support as the cardinality of the new tidset
		bitsetSupportIJ.support = bitsetSupportIJ.bitset.cardinality();
		// return the new tidset
		return bitsetSupportIJ;
	}
}
