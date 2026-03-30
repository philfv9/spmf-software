/* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* It was obtained from the LAC library under the GNU GPL license, which already contained
* some code from SPMF. Then, it was adapted for SPMF.
* @Copyright original version LAC 2019   @copyright of modifications SPMF 2021
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
package ca.pfv.spmf.algorithms.classifiers.acac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.Item;
import ca.pfv.spmf.algorithms.classifiers.general.Rule;

/**
 * Adaptation of the Apriori algorithm for the ACAC algorithm. The Apriori
 * algorithm was presented in:<br/>
 * <br/>
 * 
 * Agrawal, T. Imielinkski, A. Swami. Mining association rules between sets of
 * items in large databases. SIGMOD. 1993. 207-216. This is an adaptation for
 * ACAC algorithm. <br/>
 * <br/>
 * The key differences are:
 * <ul>
 * <li>It searches for class association rules</li>
 * <li>No patterns are generated, but rules are mined directly without an
 * intermediary step for searching for patterns</li>
 * <li>It incorporates the calculation of all-confidence in the mined of class
 * association rules</li>
 * </ul>
 * 
 * @see AlgoACAC
 */
public class AprioriForACAC {

	/**
	 * Default constructor
	 */
	public AprioriForACAC() {
		// empty
	}

	/**
	 * Extract class association rules from the previously set dataset
	 * 
	 * @param dataset a dataset
	 * @param minSup  the minimum suppor threshold
	 * @return rules whose support and confidence is greater than a user-specified
	 *         threshold
	 */
	public List<RuleACAC> run(Dataset dataset, double minSup, double minConf, double minAllConf) {

		// Calculate support relative to the current dataset
		long minSupRelative = (long) Math.ceil(minSup * dataset.getInstances().size());

		// Create a list to store rules
		List<RuleACAC> rules = new ArrayList<RuleACAC>();

		// Find the frequent itemsets of size 1
		List<Item> frequent1 = generateSingletons(dataset, minSupRelative);

		// If no frequent item, there are no need to continue searching for larger
		// patterns
		if (frequent1.isEmpty()) {
			return new ArrayList<RuleACAC>();
		}
		
		// Sort by lexical order
		Collections.sort(frequent1, new Comparator<Item>() {
			public int compare(Item o1, Item o2) {
				return o1.item - o2.item;
			}
		});

		// ====== Recursively try to find larger patterns (having k items) =====
		List<RuleACAC> level = null;
		int k = 2;
		// Generate candidates level by level
		do {
			// if we are going to generate candidates of size 2
			if (k == 2) {
				level = generateAndTestCandidateSize2(dataset, minConf, minAllConf, minSupRelative, rules, frequent1);
			} else {
				// If we are going to generate candidate of a size k > 2
				level = generateAndTestCandidateSizeK(dataset, minConf, minAllConf, minSupRelative, rules, level);
			}

			// Next we will search for candidates of size k+1 if the set of patterns is not
			// empty
			k++;
		} while (!level.isEmpty());

		// Return the rules
		return rules;
	}

	/**
	 * Find valid rules of size k
	 * 
	 * @param dataset        The dataset
	 * @param minConf        The minimum confidence
	 * @param minAllConf     The minimum all-confidence
	 * @param minSupRelative The minimum support (relative value)
	 * @param rules          The set of final rules (will be modified)
	 * @param level         the rules of size k -1
	 * @return The list of rules of size k that are valid (according to ACAC)
	 */
	private List<RuleACAC> generateAndTestCandidateSizeK(Dataset dataset, double minConf, double minAllConf,
			long minSupRelative, List<RuleACAC> rules, List<RuleACAC> level) {
		// Store the rules of size k-1 in a variable
		List<RuleACAC> previousLevel = level;
		// Initialize a variable to store rules of size k
		level = new ArrayList<RuleACAC>();

		// For each itemset I1 of size k-1
		for (int i = 0; i < previousLevel.size(); i++) {
			RuleACAC rule1 = previousLevel.get(i);

			// For each itemset I2 of size k-1
			for (int j = i + 1; j < previousLevel.size(); j++) {
				RuleACAC rule2 = previousLevel.get(j);

				// If we cannot combine I1 and I2, then we skip this pair of itemsets
				if (!rule1.isCombinable(rule2))
					continue;

				// Otherwise, we create a new candidate rule by combining itemset1 and itemset2
				RuleACAC newRule = new RuleACAC(rule1); // make a clone
				newRule.add((rule2.get(rule2.size() - 1)));
				newRule.setMaximums(rule1.getSupportRule(), rule2.getSupportRule());

				// If all the subsets of size k-1 of that rule are frequent, we will consider it
				// further
				if (areSubsetsFrequents(newRule, previousLevel)) {
					// Scan the database to calculate the support of the candidate rule
					newRule.evaluate(dataset);
					// If the rule is frequent and is all-confidence is greater than the minAllConf
					// threshold
					// it will be kept
					if (newRule.getSupportRule() >= minSupRelative && newRule.getAllConfidence() >= minAllConf) {
//									&& !level.contains(candidate)) {  //====  REMOVED BY PHILIPPE :  SEEMS UNECESSARY.    =====/

						// If rule has a higher confidence than the minconf threshold
						// it will be kept as a final rule.
						if (newRule.getConfidence() >= minConf) {
							rules.add(newRule);
						} else {
							// Otherwise it will be extended again.
							level.add(newRule);
						}
					}
				}
			}
		}
		return level;
	}

	/**
	 * Find the rules of size 2
	 * 
	 * @param dataset        The dataset
	 * @param minConf        The minimum confidence
	 * @param minAllConf     The minimum all-confidence
	 * @param minSupRelative The minimum support (relative value)
	 * @param rules          The set of final rules (will be modified)
	 * @param frequent1      The Frequent items.
	 * @return The list of rules of size 2 that are valid (according to ACAC)
	 */
	private List<RuleACAC> generateAndTestCandidateSize2(Dataset dataset, double minConf, double minAllConf,
			long minSupRelative, List<RuleACAC> rules, List<Item> frequent1) {
		// Create a list to store the rules
		List<RuleACAC> level = new ArrayList<RuleACAC>();

		// For each frequent item I1
		for (Item item1 : frequent1) {
			// It will be the rule antecedent
			short[] antecedent = new short[] { item1.item };

			// For each item I2 that is a class value
			for (int j = 0; j < dataset.getClassesCount(); j++) {
				// Get the class value and its support
				short klass = dataset.getKlassAt(j);
				long supportKlass = dataset.getMapClassToFrequency().getOrDefault(klass, 0L);

				// Create the rule
				RuleACAC rule = new RuleACAC(antecedent);
				rule.setKlass(klass);
				rule.setMaximums(item1.support, supportKlass);

				// Scan the database to calculate the support of that candidate rule
				rule.evaluate(dataset);

				// If a rule is frequent and is all-confidence is greater than the minAllConf
				// threshold
				// it will be kept
				if (rule.getSupportRule() >= minSupRelative && rule.getAllConfidence() >= minAllConf) {
//								&& !level.contains(candidate)) {  //====  REMOVED BY PHILIPPE :  SEEMS UNECESSARY.    =====/

					// If rule has a higher confidence than the minconf threshold
					// it will be kept as a final rule.
					if (rule.getConfidence() >= minConf) {
						rules.add(rule);
					} else {
						// Otherwise, the rule will be extended again.
						level.add(rule);
					}
				}
			}
		}
		return level;
	}

	/**
	 * Generate singletons and its frequency. Only frequent singletons are
	 * considered
	 * 
	 * @return the list of frequent items
	 */
	private List<Item> generateSingletons(Dataset dataset, double minSupRelative) {
		// Create a map to count the frequency of each item
		// Key: item Value: Frequency (support)
		Map<Short, Long> mapItemCount = new HashMap<Short, Long>();

		// For each record
		for (Instance instance : dataset.getInstances()) {
			Short[] example = instance.getItems();

			// For each attribute that is not the target attribute
			for (int j = 0; j < example.length - 1; j++) {
				short item = example[j];

				// increase the support count
				long count = mapItemCount.getOrDefault(item, 0L);
				mapItemCount.put(item, ++count);
			}
		}

		// Create list of frequent items
		List<Item> frequent1 = new ArrayList<Item>();

		// For each item
		for (Entry<Short, Long> entry : mapItemCount.entrySet()) {
			// If the support is enough
			if (entry.getValue() >= minSupRelative) {
				// Add the item to the list of frequent items
				frequent1.add(new Item(entry.getKey(), entry.getValue()));
			}
		}
		// Return the list of frequent items
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
	protected boolean areSubsetsFrequents(Rule candidate, List<RuleACAC> levelK1) {
		// Try removing each item
		for (int positionToRemove = 0; positionToRemove < candidate.getAntecedent().size(); positionToRemove++) {

			boolean found = false;
			// Loop over all rules of size k-1
			for(RuleACAC rule : levelK1) {
				// if the antecedent without the item is the same as the rule of size k-1
				if(sameAs(rule.getAntecedent(), candidate.getAntecedent(),  positionToRemove) == 0) {
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