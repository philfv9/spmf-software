/*
 * This file is part of SPMF data mining library.
 * It is adapted from some GPL code obtained from the LAC library, which used the ECLAT code from SPMF.
 *
 * Copyright (C) SPMF, LAC
 *   
 * LAC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details. You should have 
 * received a copy of the GNU General Public License along with 
 * this program.  If not, see http://www.gnu.org/licenses/
 */
package ca.pfv.spmf.algorithms.classifiers.mac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;

/**
 * Class a modified version of the ECLAT algorithm to be used with the MAC
 * algorithm. The ECLAT algorithm was resented in: M. J. Zaki, Scalable
 * algorithms for association mining.‚ÄùIEEE Trans. Knowl. DataEng., vol. 12, no.
 * 3, pp. 372‚Ä?390, 2000. This is an adaptation for ACAC algorithm.
 * 
 * This implementation is adapted for searching for class association rules. The
 * rules are mined directly without keeping intermediary results (frequent
 * itemsets).
 * 
 * @see AlgoMAC
 */
public class Eclat {
	/**
	 * Support relative to the dataset
	 */
	private double minsupRelative;

	/**
	 * Minimum confidence
	 */
	private double minConf;

	/**
	 * Dataset to extract class association rules
	 */
	private Dataset dataset;

	/**
	 * Set of returned rules
	 */
	private List<RuleMAC> rules = new ArrayList<RuleMAC>();

	/**
	 * A map indicating the transaction ids of each class value Key: class value
	 * Value: set of transaction ids
	 */
	private Map<Short, Set<Integer>> klassesTIDS;

	/**
	 * Default constructor
	 * 
	 * @param dataset training dataset
	 * @param minSup  minimum support threshold
	 * @param minConf minimum confidence threshold
	 */
	public Eclat(Dataset dataset, double minSup, double minConf) {
		// save the parameters
		this.dataset = dataset;
		this.minConf = minConf;

		// Calculate the minimum support as a number of transactions
		this.minsupRelative = Math.ceil(minSup * dataset.getInstances().size());
	}

	/**
	 * Extracts class association rules from training dataset
	 * 
	 * @return class association rules
	 */
	public List<RuleMAC> run() {
		// Scan the database to obtain the set of transaction IDs of each item
		// (attribute value)
		// The result is a map where Key: item Value: set of transaction IDs
		Map<Short, Set<Integer>> mapItemTIDS = this.generateSingletons();

		// Create the list of frequent items
		List<Short> frequentItems = new ArrayList<Short>();

		// For each item in the map
		for (Entry<Short, Set<Integer>> entry : mapItemTIDS.entrySet()) {
			Set<Integer> tidset = entry.getValue();
			long support = tidset.size();

			// If it is frequent
			if (support >= minsupRelative) {
				Short item = entry.getKey();

				// Then add the item to the list of frequent items
				frequentItems.add(item);
			}
		}

		// Sort the list of frequent items by increasing support
		Collections.sort(frequentItems, new Comparator<Short>() {
			public int compare(Short arg0, Short arg1) {
				return mapItemTIDS.get(arg0).size() - mapItemTIDS.get(arg1).size();
			}
		});

		// Combine frequent items to generate rules of size k=2,
		// that is, antecedent has one unique attribute and consequence with one class
		// value
		List<RuleMAC> k2 = this.generateK2(frequentItems, mapItemTIDS);

		// Then, try to combine rules to make larger rules
		// For each rule R1
		for (int i = 0; i < k2.size(); i++) {
			RuleMAC itemI = k2.get(i);
			List<RuleMAC> prefixedItemsI = new ArrayList<RuleMAC>();

			// For each rule R2
			for (int j = i + 1; j < k2.size(); j++) {
				RuleMAC itemJ = k2.get(j);

				// if R1 and R2 don't have the same consequent, they cannot be combined.
				if (itemI.getKlass() != itemJ.getKlass())
					continue;

				// Do the intersection of the sets of transactions for R1 and R2, to
				// find the set of transactions of the resulting rule
				Set<Integer> tidsetIJ = intersection(itemI.getTidsetRule(), itemJ.getTidsetRule());

				// If that support is no less than the minsup threshold
				if (tidsetIJ.size() >= minsupRelative) {
					// Create the rule and keep it
					Short[] newAntecedent = union(itemI.getAntecedent(), itemJ.getAntecedent());
					Set<Integer> tidsetAntecedent = intersection(itemI.getTidsetAntecedent(),
							itemJ.getTidsetAntecedent());
					RuleMAC rule = new RuleMAC(newAntecedent, tidsetAntecedent, itemI.getKlass(), tidsetIJ);
					prefixedItemsI.add(rule);
				}
			}

			// Process all prefixedItems, if there are some
			if (!prefixedItemsI.isEmpty()) {
				processPrefixedItems(itemI, prefixedItemsI);
			}
		}

		// Return the class association rules
		return rules;
	}

	/**
	 * Generate rules of size 2. Where the antecedent has one attribute, and the
	 * consequent has one class value
	 * 
	 * @param frequentItems List of frequent singletons
	 * @param mapItemTIDS   Map indicating the set of transactions of each item
	 *                      (attribute value)
	 * @return A list of class association rules of size 2
	 */
	private List<RuleMAC> generateK2(List<Short> frequentItems, Map<Short, Set<Integer>> mapItemTIDS) {
		// The list to store the rules
		List<RuleMAC> k2 = new ArrayList<RuleMAC>();

		// For each item I
		for (Short itemI : frequentItems) {
			// We obtain the set of transactions of I
			Set<Integer> tidsetI = mapItemTIDS.get(itemI);

			// For each class value J
			for (Entry<Short, Set<Integer>> klass : klassesTIDS.entrySet()) {
				// Find the set of transactions of the rule I ==> J
				Set<Integer> tidsetIJ = intersection(tidsetI, klass.getValue());
				// Create the rule I ==> J
				RuleMAC rule = new RuleMAC(new Short[] { itemI }, tidsetI, klass.getKey(), tidsetIJ);
				// Save the rule
				saveRule(rule);
				// Add the rule to the list of rules of size 2.
				k2.add(rule);
			}
		}
		// Return the list of rules
		return k2;
	}

	/**
	 * Scan the database to obtain the set of transaction IDs of each item
	 * (attribute value). The result is a map where Key: item, Value: set of
	 * transaction IDs
	 * 
	 * @return the map
	 */
	private Map<Short, Set<Integer>> generateSingletons() {
		// Create the map to store the results
		Map<Short, Set<Integer>> itemTids = new HashMap<Short, Set<Integer>>();

		// Create a map to store the set of TIDs of each class value 
		// (key: class value, Value: set of TIDs)
		klassesTIDS = new HashMap<Short, Set<Integer>>();

		// For each record
		List<Instance> instances = this.dataset.getInstances();
		for (int tid = 0; tid < instances.size(); tid++) {
			Instance instance = instances.get(tid);
			
			// For each item (attribute value) of the current record
			for (int j = 0; j < this.dataset.getAttributes().size(); j++) {
				Short itemJ = instance.getItems()[j];

				// Get the set of transactions of that item
				Set<Integer> tidset = itemTids.get(itemJ);

				// If that set does not exist, create it
				if (tidset == null) {
					tidset = new HashSet<Integer>();
					itemTids.put(itemJ, tidset);
				}

				// Add the current tid to the set of transaction ids of item J
				tidset.add(tid);
			}

			// Get the class value of that record and the corresponding set of transaction IDs
			Short klass = instance.getKlass();
			Set<Integer> tidset = klassesTIDS.get(klass);

			// If that set does not exist, create it
			if (tidset == null) {
				tidset = new HashSet<Integer>();
				klassesTIDS.put(klass, tidset);
			}

			// Add the current tid to the set of transaction ids of that class value.
			tidset.add(tid);
		}

		// Return the map
		return itemTids;
	}

	/**
	 * Process all the prefixed items to generate much larger rules
	 * 
	 * @param rule          to be combined together with the prefixedItems
	 * @param prefixedItems items prefixed to the current rule
	 */
	private void processPrefixedItems(RuleMAC rule, List<RuleMAC> prefixedItems) {
		// FIRST CASE: If there is only one prefixed item
		if (prefixedItems.size() == 1) {
			// Combine the prefixed item with the rule to make a new rule
			RuleMAC itemI = prefixedItems.get(0);
			Short[] newAntecedent = union(rule.getAntecedent(), itemI.getAntecedent());
			Set<Integer> newTidset = intersection(rule.getTidsetRule(), itemI.getTidsetRule());
			Set<Integer> newTidsetAntecedent = intersection(rule.getTidsetAntecedent(), itemI.getTidsetAntecedent());

			// Save the new rule
			saveRule(new RuleMAC(newAntecedent, newTidsetAntecedent, rule.getKlass(), newTidset));
		} else if (prefixedItems.size() == 2) {
			// SECOND CASE: There are two prefixed items. 
			
			// Combine the first prefixed item with the rule to make a new rule and save it
			RuleMAC itemI = prefixedItems.get(0);

			Short[] newAntecedent = union(rule.getAntecedent(), itemI.getAntecedent());
			Set<Integer> newTidset = intersection(rule.getTidsetRule(), itemI.getTidsetRule());
			Set<Integer> newTidsetAntecedent = intersection(rule.getTidsetAntecedent(), itemI.getTidsetAntecedent());

			saveRule(new RuleMAC(newAntecedent, newTidsetAntecedent, rule.getKlass(), newTidset));


			// Combine the second prefixed item with the rule to make a new rule and save it
			RuleMAC itemJ = prefixedItems.get(1);

			Short[] newAntecedent2 = union(rule.getAntecedent(), itemJ.getAntecedent());
			Set<Integer> newTidset2 = intersection(rule.getTidsetRule(), itemJ.getTidsetRule());
			Set<Integer> newTidsetAntecedent2 = intersection(rule.getTidsetAntecedent(), itemJ.getTidsetAntecedent());

			saveRule(new RuleMAC(newAntecedent2, newTidsetAntecedent2, rule.getKlass(), newTidset2));

			// Besides, we also try to combine the two above prefixed items to make a third rule
			Short[] unionAntecedent = union(newAntecedent, itemJ.getAntecedent());
			if (unionAntecedent.length <= this.dataset.getAttributes().size()) {
				Set<Integer> tidsetIJ = intersection(newTidset, itemJ.getTidsetRule());
				long supportIJ = tidsetIJ.size();

				// If the resulting rule is frequent
				if (supportIJ >= minsupRelative) {
					// Save it
					Set<Integer> antecedentTidsetIJ = intersection(newTidsetAntecedent, itemJ.getTidsetAntecedent());
					saveRule(new RuleMAC(unionAntecedent, antecedentTidsetIJ, rule.getKlass(), tidsetIJ));
				}
			}
		} else {
			// THIRD CASE: There are more than 2 prefixed items.

			// For each prefixed item
			for (int i = 0; i < prefixedItems.size(); i++) {
				// First, we combine the prefixed item with the rule to make a new rule
				RuleMAC itemI = prefixedItems.get(i);

				Short[] newAntecedent = union(rule.getAntecedent(), itemI.getAntecedent());
				Set<Integer> newTidset = intersection(rule.getTidsetRule(), itemI.getTidsetRule());
				Set<Integer> newTidsetAntecedent = intersection(rule.getTidsetAntecedent(),
						itemI.getTidsetAntecedent());
				// and we save that rule.
				saveRule(new RuleMAC(newAntecedent, newTidsetAntecedent, rule.getKlass(), newTidset));

				// Then we will try to combine that prefixed item with other prefixed items
				// to make new rules
				List<RuleMAC> prefixedItemsSuffix = new ArrayList<RuleMAC>();
				// For each other item (after according to the lexical order)
				for (int j = i + 1; j < prefixedItems.size(); j++) {
					
					// Create a new rule by combining the prefixed item with the rule
					RuleMAC suffixJ = prefixedItems.get(j);
					Set<Integer> tidsetIJ = intersection(newTidset, suffixJ.getTidsetRule());

					long supportIJ = tidsetIJ.size();
					
					// If the support is enough
					if (supportIJ >= minsupRelative) {
						// Calculate the set of transaction ids of the ancedent
						Short[] new2Antecedent = union(newAntecedent, suffixJ.getAntecedent());
						Set<Integer> tidsetAntecedent = intersection(newTidsetAntecedent,
								suffixJ.getTidsetAntecedent());
						
						// save the rule so that it will be expanded further 
						prefixedItemsSuffix
								.add(new RuleMAC(new2Antecedent, tidsetAntecedent, rule.getKlass(), tidsetIJ));
					}
				}

				// Recursively process the rest of prefixed items to generate even larger rules
				if (!prefixedItemsSuffix.isEmpty()) {
					processPrefixedItems(new RuleMAC(newAntecedent, newTidsetAntecedent, rule.getKlass(), newTidset),
							prefixedItemsSuffix);
				}
			}
		}
	}

	/**
	 * Performs the union of two set of items
	 * 
	 * @param antecedent1 first set to join
	 * @param antecedent2 second set to join
	 * @return union of both sets of items
	 */
	public static Short[] union(Short[] antecedent1, List<Short> antecedent2) {
		List<Short> newArrayList = new ArrayList<Short>(antecedent2);
		// This may not be the most efficient way... but if the arrays are short, it is
		// fine.
		for (Short item : antecedent1) {
			if (antecedent2.contains(item) == false) {
				newArrayList.add(item);
			}
		}
		return newArrayList.toArray(antecedent1);
	}

	/**
	 * Performs the intersection of two sets of integers
	 * 
	 * @param setI First set to be intersected
	 * @param setJ Second set to be intersected
	 * @return a new set with the intersection
	 */
	public static Set<Integer> intersection(Set<Integer> setI, Set<Integer> setJ) {
		Set<Integer> result = new HashSet<Integer>();

		// If the setI is larger than the set J
		if (setI.size() > setJ.size()) {
			for (Integer tid : setJ) {
				if (setI.contains(tid)) {
					result.add(tid);
				}
			}
		} else {
			for (Integer tid : setI) {
				if (setJ.contains(tid)) {
					result.add(tid);
				}
			}
		}
		return result;
	}

	/**
	 * Performs the union of two set of items
	 * 
	 * @param antecedent  first set to join
	 * @param antecedent2 second set to join
	 * @return union of both sets of items
	 */
	public static Short[] union(List<Short> antecedent, List<Short> antecedent2) {
		// MODIFIED by PHILIPPE : fixed some inefficiency problem of converting to array
		// and then to list again...
		// Maybe can be further improved!
		Set<Short> set = new HashSet<Short>(antecedent.size());
		set.addAll(antecedent);
		set.addAll(antecedent2);

		Short[] union = {};
		union = set.toArray(union);
		return union;
	}

	/**
	 * Add the rule to the classifier
	 * 
	 * @param rule the rule
	 */
	private void saveRule(RuleMAC rule) {
		if (rule.getConfidence() >= minConf) {
			rules.add(rule);
		}
	}
}
