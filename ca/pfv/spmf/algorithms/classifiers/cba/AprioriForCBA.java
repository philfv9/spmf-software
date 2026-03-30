/**
 * This file is part of SPMF data mining library.
 * It is adapted from some GPL code obtained from the LAC library, which used some SPMF code.
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
package ca.pfv.spmf.algorithms.classifiers.cba;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;

/**
 * Implementation of Apriori for CBA. It is adapted to output rules directly.
 * 
 * @see AlgoCBA
 */
public class AprioriForCBA {

	/**
	 * The minimum support threshold (expressed as a number of records)
	 */
	private long minSupRelative;

	/**
	 * Default constructor
	 */
	public AprioriForCBA() {

	}

	/**
	 * Extract class association rules from the previously set dataset
	 * 
	 * @param dataset the training dataset
	 * @param minSup  the minimum support
	 * @param minConf the minimum confidence
	 * 
	 * @return rules whose support and confidence is greater than a user-specified
	 *         threshold
	 */
	public List<RuleCBA> runAlgorithm(Dataset dataset, double minSup, double minConf) {
		this.minSupRelative = (long) Math.ceil(minSup * dataset.getInstances().size());

		// The list of rules to be returned
		List<RuleCBA> rules = new ArrayList<RuleCBA>();

		// Find the frequent itemsets of size 1
		List<Short> frequent1 = this.generateSingletons(dataset);

		// If no frequent item, there are no need to continue searching for larger
		// patterns
		if (frequent1.isEmpty()) {
			return new ArrayList<RuleCBA>();
		}

		// We sort the list of candidates by lexical order
		// (Apriori need to use a total order otherwise it does not work)
		Collections.sort(frequent1, new Comparator<Short>() {
			public int compare(Short o1, Short o2) {
				return o1 - o2;
			}
		});

		// ====== Recursively try to find larger patterns (having k items) =====
		List<RuleCBA> level = null;
		int k = 2;
		do {

			// if we are going to generate candidates of size 2
			if (k == 2) {
				level = generateAndTestCandidateSize2(dataset, minConf, rules, frequent1);
			} else {
				// If we are going to generate candidate of a size k > 2
				// create a variable to store candidates
				level = generateAndTestCandidateSizeK(dataset, minConf, rules, level);
			}
			k++;
			// Next we will search for candidates of size k+1 if the set of patterns is not
			// empty
		} while (!level.isEmpty());

		return rules;
	}

	/**
	 * Find valid rules of size k
	 * 
	 * @param dataset        The dataset
	 * @param minConf        The minimum confidence
	 * @param rules          The set of final rules (will be modified)
	 * @param level         the rules of size k -1
	 * @return The list of rules of size k that are valid 
	 */
	private List<RuleCBA> generateAndTestCandidateSizeK(Dataset dataset, double minConf, List<RuleCBA> rules,
			List<RuleCBA> level) {
		List<RuleCBA> levelX = new ArrayList<RuleCBA>();

		// For each itemset I1 of size k-1
		for (int i = 0; i < level.size(); i++) {
			RuleCBA rule1 = level.get(i);
			
			// For each itemset I2 of size k-1
			for (int j = i + 1; j < level.size(); j++) {
				RuleCBA rule2 = level.get(j);

				// Check if the rules can be combined
				if (!rule1.isCombinable(rule2)) {
					continue;
				}

				// Create a new candidate rule by combining I1 and I2
				RuleCBA newRule = new RuleCBA(rule1);
				newRule.add(rule2.get(rule2.size() - 1));

				// If the subsets of the candidate rule are frequent
				if (areSubsetsFrequents(newRule, level)) {
					// Calculate the support of the rule
					newRule.evaluate(dataset);

					// If the rule is frequent
					if (newRule.getSupportRule() >= this.minSupRelative
					// && !levelX.contains(candidate) // LIKELY UNECESSARY - REMOVED BY PHILIPPE
					) {
						// Keep the rule
						levelX.add(newRule);

						// If the rule also has a high confidence, add it to the set of rules
						if (newRule.getConfidence() >= minConf) {
							rules.add(newRule);
						}
					}
				}
			}
		}

		return levelX;
	}

	/**
	 * Find the rules of size 2
	 * 
	 * @param dataset        The dataset
	 * @param minConf        The minimum confidence
	 * @param rules          The set of final rules (will be modified)
	 * @param frequent1      The Frequent items.
	 * @return The list of rules of size 2 that are valid (according to ACAC)
	 */
	private List<RuleCBA> generateAndTestCandidateSize2(Dataset dataset, double minConf, List<RuleCBA> rules,
			List<Short> frequent1) {
		// Create a list to store the rules
		List<RuleCBA> levelX = new ArrayList<RuleCBA>();

		// For each frequent item I1
		for (Short item1 : frequent1) {
			RuleCBA rule = new RuleCBA();
			rule.add(item1);
			
			// For each frequent item I2
			for (int j = 0; j < dataset.getClassesCount(); j++) {

				// Get the class value 
				short klass = dataset.getKlassAt(j);
				
				// Create a new rule
				rule.setKlass(klass);
				RuleCBA newRule = new RuleCBA(rule);
				
				// Calculate the support of the rule
				newRule.evaluate(dataset);

				// If the rule is frequent
				if (newRule.getSupportRule() >= this.minSupRelative
				// && !levelX.contains(newRule) // THIS IS LIKELY UNECESSARY - REMOVED BY PHIL
				) {
					// Add it to the current level
					levelX.add(newRule);

					// If the rules also has a high confidence, add it to the set of rules.
					if (newRule.getConfidence() >= minConf) {
						rules.add(newRule);
					}
				}
			}
		}
		return levelX;
	}

	/**
	 * Generate singletons and its frequency. Only frequent singletons are
	 * considered
	 * 
	 * @return singletons and its frequency
	 */
	private List<Short> generateSingletons(Dataset dataset) {
		Map<Short, Long> mapItemCount = new HashMap<Short, Long>();

		for (Instance instance : dataset.getInstances()) {
			Short[] items = instance.getItems();

			// -1 because klass should not be saved in mapItemCount
			for (int j = 0; j < items.length - 1; j++) { // ** Do not refactor!
				short item = items[j];

				// increase the support count
				Long count = mapItemCount.getOrDefault(item, 0L);
				mapItemCount.put(item, ++count);
			}
		}

		// We add all frequent items to the set of candidate of size 1
		List<Short> frequent1 = new ArrayList<Short>();
		for (Entry<Short, Long> entry : mapItemCount.entrySet()) {
			if (entry.getValue() >= this.minSupRelative) {
				frequent1.add(entry.getKey());
			}
		}

		return frequent1;
	}


	/**
	 * Method to check if all the subsets of size k-1 of a candidate are frequent.
	 * That is a requirement of the anti-monotone property of the support
	 * 
	 * @param candidate a candidate rule of size k
	 * @param levelK1   the frequent rules of size k-1
	 * @return true if all the subsets are frequent
	 */
	protected boolean areSubsetsFrequents(RuleCBA candidate, List<RuleCBA> levelK1) {
		// Try removing each item
		for (int positionToRemove = 0; positionToRemove < candidate.getAntecedent().size(); positionToRemove++) {

			boolean found = false;
			// Loop over all rules of size k-1
			for (RuleCBA rule : levelK1) {
				// if the antecedent without the item is the same as the rule of size k-1
				if (sameAs(rule.getAntecedent(), candidate.getAntecedent(), positionToRemove) == 0) {
					// we found the subset, it is ok
					found = true;
					break;
				}
			}
			// if we did not found the subset then the candidate rule cannot be frequent
			if (!found) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Method to compare two sorted list of integers and see if they are the same,
	 * while ignoring an item from the second list of integer. This methods is used
	 * by some Apriori algorithms.
	 * 
	 * @param itemset1   the first itemset
	 * @param itemsets2  the second itemset
	 * @param posRemoved the position of an item that should be ignored from
	 *                   "itemset2" to perform the comparison.
	 * @return 0 if they are the same, 1 if itemset is larger according to lexical
	 *         order, -1 if smaller.
	 */
	int sameAs(List<Short> itemset1, List<Short> itemsets2, int posRemoved) {
		// a variable to know which item from candidate we are currently searching
		int j = 0;
		// loop on items from "itemset"
		for (int i = 0; i < itemset1.size(); i++) {
			// if it is the item that we should ignore, we skip it
			if (j == posRemoved) {
				j++;
			}
			// if we found the item j, we will search the next one
			if (itemset1.get(i).equals(itemsets2.get(j))) {
				j++;
				// if the current item from i is larger than j,
				// it means that "itemset" is larger according to lexical order
				// so we return 1
			} else if (itemset1.get(i) > itemsets2.get(j)) {
				return 1;
			} else {
				// otherwise "itemset" is smaller so we return -1.
				return -1;
			}
		}
		return 0;
	}
}