/* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* It was obtained from the LAC library under the GNU GPL license and adapted for SPMF.
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
package ca.pfv.spmf.algorithms.classifiers.acn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.Item;

/**
 * Class implementing the well-known Apriori algorithm. Presented at: R.
 * Agrawal, T. Imielinkski, A. Swami. Mining association rules between sets of
 * items in large databases. SIGMOD. 1993. 207-216. This is an adaptation for
 * ACN algorithm. The key differences are:
 * <ul>
 * <li>It searches for class association rules</li>
 * <li>No patterns are generated, but rules are mined directly without an
 * intermediary step for searching for patterns</li>
 * <li>It incorporates the calculation of all-confidence in the mined of class
 * association rules</li>
 * <li>It extracts negative class association rules</li>
 * </ul>
 * 
 * @see AlgoACN
 */
public class AprioriNegativeForACN {
	/**
	 * Threshold of frequency of occurrence for the current dataset being mined
	 */
	private long minSupRelative;

	/** Minimum Confidence */
	double minConf;

	/**
	 * Train dataset where find rules to form the associative classifier
	 */
	private Dataset dataset;

	/**
	 * Default constructor
	 * 
	 * @param dataset a dataset for training
	 * @param minsup  minimum support threshold
	 * @param minconf minimum confidence threshold
	 */
	public AprioriNegativeForACN(Dataset dataset, double minSup, double minConf) {
		this.dataset = dataset;
		this.minConf = minConf;
		// Calculate support relative to the current dataset
		this.minSupRelative = (long) Math.ceil(minSup * dataset.getInstances().size());
	}

	/**
	 * Extract class association rules from the dataset
	 * 
	 * @return rules whose support, confidence and correlation is greater than
	 *         user-specified thresholds
	 */
	public List<RuleACN> run() {
		// List of rules
		List<RuleACN> rules = new ArrayList<RuleACN>();
		// List of negative rules
		List<RuleACN> negativeRules = new ArrayList<RuleACN>();

		// Find the frequent items (attribute values)
		List<Item> frequent1 = this.generateSingletons();

		// If no frequent item, we stop because there are not frequent rules
		if (frequent1.isEmpty()) {
			return new ArrayList<RuleACN>();
		}

		// Sort the frequent items by lexical order
		Collections.sort(frequent1, new Comparator<Item>() {
			public int compare(Item o1, Item o2) {
				return o1.item - o2.item;
			}
		});

		// ====== Recursively try to find larger patterns (having k items) =====
		List<RuleACN> levelK = null;
		int k = 2;
		// Generate candidates level by level
		do {
			// if we are going to generate candidates of size 2
			if (k == 2) {
				levelK = generateAndTestCandidateSize2(rules, negativeRules, frequent1);
			} else {
				// If we are going to generate candidate of a size k > 2
				levelK = generateAndTestCandidateSizeK(rules, negativeRules, levelK);
			}

			// Next we will search for candidates of size k+1 if the set of patterns is not
			// empty
			k++;
		} while (!levelK.isEmpty());

		// Add all negative rules to the set of rules
		rules.addAll(negativeRules);

		return rules;
	}

	/**
	 * Find valid rules of size k
	 * 
	 * @param rules         The set of final rules (will be modified)
	 * @param negativeRules The set of negative rules
	 * @param level         the rules of size k -1
	 * @return The list of rules of size k that are valid (according to ACAC)
	 */
	private List<RuleACN> generateAndTestCandidateSizeK(List<RuleACN> rules, List<RuleACN> negativeRules,
			List<RuleACN> level) {
		List<RuleACN> levelX = new ArrayList<RuleACN>();

		// For each itemset I1 and I2 of level k-1
		for (int i = 0; i < level.size(); i++) {
			RuleACN rule1 = level.get(i);
			for (int j = i + 1; j < level.size(); j++) {
				RuleACN rule2 = level.get(j);

				if (!rule1.isCombinable(rule2))
					continue;

				// Create a new candidate by combining itemset1 and itemset2
				RuleACN newRule = new RuleACN(rule1);
				newRule.add((rule2.get(rule2.size() - 1)));

				// The candidate is tested to see if its subsets of size k-1 are
				// included in level k-1 (they are frequent).
				if (areSubsetsFrequents(newRule, level)) {
//							candidates.add(newRule);
					// Scan the database to calculate support of the rule
					newRule.evaluate(dataset);

					// evaluate the rule
					evaluateCandidate(rules, negativeRules, levelX, newRule);
				}
			}
		}
		return levelX;
	}

	/**
	 * Generate and test candidate patterns of size 2
	 * 
	 * @param rules         the list of rules
	 * @param negativeRules The list of negative rules
	 * @param frequent1     the frequent items
	 * @return a list of rules of size 2
	 */
	private List<RuleACN> generateAndTestCandidateSize2(List<RuleACN> rules, List<RuleACN> negativeRules,
			List<Item> frequent1) {
		List<RuleACN> level = new ArrayList<RuleACN>();

		// For each itemset I1 containing k-1 items
		for (int i = 0; i < frequent1.size(); i++) {
			Item item1 = frequent1.get(i);

			short[] antecedent = new short[] { item1.item };
			List<Boolean> negativeItems = new ArrayList<Boolean>();
			negativeItems.add(false);

			// For each itemset class value
			for (int j = 0; j < dataset.getClassesCount(); j++) {
				// Create a new rule
				RuleACN newRule = new RuleACN(antecedent, negativeItems, dataset.getKlassAt(j));

				// Scan the database to calculate support of the rule
				newRule.evaluate(dataset);

				// Evaluate the candidate rule
				evaluateCandidate(rules, negativeRules, level, newRule);
			}
		}
		return level;
	}

	/**
	 * Evaluate a rule
	 * 
	 * @param rules         the list of rules
	 * @param negativeRules the list of negative rules
	 * @param level         the itemsets of size k-1
	 * @param newRule       the rule to be evaluated
	 */
	private void evaluateCandidate(List<RuleACN> rules, List<RuleACN> negativeRules, List<RuleACN> level,
			RuleACN newRule) {
		// If the rule is frequent
		if (newRule.getSupportRule() >= this.minSupRelative) {
			// Add the rule to this level
			level.add(newRule);
			// Add the rule to the list of rules
			rules.add(newRule);

			// Generate a negative rule by negating each item contained in the antecedent
			for (int m = 0; m < newRule.getAntecedent().size(); m++) {
				RuleACN negatedRule = new RuleACN(newRule);
				negatedRule.negateItem(m);

				// Count the support of this rule
				negatedRule.evaluate(this.dataset);

				// If the rule is frequent, add it to the set of negative rules
				if (negatedRule.getSupportRule() >= this.minSupRelative) {
					negativeRules.add(negatedRule);
				}
			}
		}
	}

	/**
	 * Generate singletons (frequent items) and calcualte their frequenty (support).
	 * Only frequent singletons are then considered by the algorithm
	 * 
	 * @return The list of frequent items
	 */
	private List<Item> generateSingletons() {
		// Create a map to count the frequency of each item
		// Key: item Value: Frequency (support)
		HashMap<Short, Long> mapItemCount = new HashMap<Short, Long>();

		// For each record
		for (Instance instance : dataset.getInstances()) {
			Short[] items = instance.getItems();

			// For each attribute that is not the target attribute
			for (int j = 0; j < items.length - 1; j++) {
				short item = items[j];

				// increase the support count
				Long count = mapItemCount.getOrDefault(item, 0L);
				mapItemCount.put(item, ++count);
			}
		}

		// Create list of frequent items
		List<Item> frequent1 = new ArrayList<Item>();
		// For each item
		for (Entry<Short, Long> entry : mapItemCount.entrySet()) {
			// If the support is enough
			if (entry.getValue() >= this.minSupRelative) {
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
	 * @param the       frequent rules of size k-1
	 * @return true if all the subsets are frequent
	 */
	protected boolean areSubsetsFrequents(RuleACN candidate, List<RuleACN> levelK1) {
		// Try removing each items
		for (int positionToRemove = 0; positionToRemove < candidate.getAntecedent().size(); positionToRemove++) {

			boolean found = false;
			// Loop over all rules of size k-1
			for (RuleACN rule : levelK1) {
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