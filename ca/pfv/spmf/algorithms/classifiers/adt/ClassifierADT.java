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
package ca.pfv.spmf.algorithms.classifiers.adt;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.ArraysAlgos;
import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.Rule;
import ca.pfv.spmf.algorithms.classifiers.general.RuleClassifier;

/**
 * Class used to represent a classifier build by the ADT algorithm
 * @see AlgoADT
 */
public class ClassifierADT extends RuleClassifier implements Serializable {
	/**
	 * UID
	 */
	private static final long serialVersionUID = 8240202223112688265L;

	/**
	 * Field used to store the dataset
	 */
	private Dataset training;

	/**
	 * Minimum value for the merit measure
	 */
	private double minMerit;

	/**
	 * Constructor
	 * 
	 * @param rules    rules to be used as base to create the classifier
	 * @param minMerit the minimum merit threshold
	 * @param training dataset used as training set
	 */
	public ClassifierADT(List<RuleADT> rules, double minMerit, Dataset training) {
		super("ADT");
		this.training = training;
		this.minMerit = minMerit;

		// Sort the rules by confidence, support, size, lexicographic order...
		Collections.sort(rules, new Comparator<RuleADT>() {
			public int compare(RuleADT arg0, RuleADT arg1) {
				if (Double.compare(arg0.getConfidence(), arg1.getConfidence()) != 0) {
					return -Double.compare(arg0.getConfidence(), arg1.getConfidence());
				} else if (Double.compare(arg0.getSupportRule(), arg1.getSupportRule()) != 0) {
					return -Double.compare(arg0.getSupportRule(), arg1.getSupportRule());
				} else if (Integer.compare(arg0.size(), arg1.size()) != 0) {
					return Integer.compare(arg0.size(), arg1.size());
				} else {
					// Lexicography order
					for (int i = 0; i < arg0.size(); i++) {
						short x = arg0.getAntecedent().get(i);
						short y = arg1.getAntecedent().get(i);
						if (Integer.compare(x, y) != 0)
							return Integer.compare(x, y);
					}
					return Integer.compare(arg0.getKlass(), arg1.getKlass());
				}
			}
		});

		// Remove redundant rules
		rules = removeRedundant(rules);

		// Once rules are ranked, we need to calculate N(v) and E(v) for each node
		// having into account the previous order.
		// For each record:
		for (int indexInstance = 0; indexInstance < this.training.getInstances().size(); indexInstance++) {
			
			Instance instance = this.training.getInstances().get(indexInstance);
			Short[] items = instance.getItems();

			boolean match = false;
			// For each rule:
			for (int i = 0; i < rules.size() && !match; i++) {
				RuleADT rule = rules.get(i);

				if (rule.matching(items)) {
					match = true;
					rule.addCoveredInstance(indexInstance);

					if (rule.getKlass() == instance.getKlass()) {
						rule.incrementHits();
					} else {
						rule.incrementMisses();
					}
				}
			}
		}

		RuleADT defaultRule = extractDefaultRule();

		ADNode parent = new ADNode(defaultRule);

		for (int m = rules.size() - 1; m >= 0; m--) {
			ADNode tmpParent = parent;
			ADNode auxNode;

			RuleADT rule = rules.get(m);

			while ((auxNode = tmpParent.isChild(rule)) != null) {
				tmpParent = auxNode;
			}

			ADNode newNode = new ADNode(rule);
			newNode.parent = tmpParent;
			tmpParent.childs.add(newNode);
		}

		// Prune tree using pessimistic error estimate
		prune(parent);

		// transform from tree to list of rules to form the final classifier
		// while transformation is being done, merit is also calculate, and
		// rule do not satisfying the user-threshold are removed
		this.rules = transformTreeToRules(parent);
	}

	/**
	 * Transforms the tree to an array of rules while filtering by minMerit
	 * 
	 * @param node used as parent to filter
	 * @return array with all the rules
	 */
	private List<Rule> transformTreeToRules(ADNode node) {
		List<Rule> rules = new ArrayList<>();
		for (int i = node.childs.size() - 1; i >= 0; i--) {
			rules.addAll(transformTreeToRules(node.childs.get(i)));
		}
		// if the rule has enough merit, keep it
		if (node.rule.getMerit() >= minMerit) {
			rules.add(node.rule);
		}
		return rules;
	}

	/**
	 * Performs pruning of the tree
	 * 
	 * @param node being pruned
	 */
	private void prune(ADNode node) {
		if (node == null || node.childs.isEmpty())
			return;

		for (ADNode child: node.childs) {
			prune(child);
		}

		ADNode leafNode = new ADNode(node);
		// Check errors if this rule was acting as leaf
		double leafErrors = calculatePessimisticErrorEstimate(leafNode);

		// Calculate future errors if leaf are removed
		double treeErrors = node.rule.getPessimisticErrorEstimate();
		for (ADNode child: node.childs) {
			treeErrors += child.rule.getPessimisticErrorEstimate();
		}

		if (leafErrors < treeErrors) {
			node.childs.clear();
			// Replace rule, to replace E and N
			node.rule = leafNode.rule;
		}

	}

	/**
	 * Recalculates the pessimistic error rate for the specified node
	 * 
	 * @param node being used to recalculate per
	 * @return the pessimistic error rate for this node
	 */
	private double calculatePessimisticErrorEstimate(ADNode node) {
		for (ADNode child : node.childs) {
			List<Integer> instances = child.rule.getCoveredInstances();

			for (Integer tid :instances) {
				Instance instance = this.training.getInstances().get(tid);
				Short[] items = instance.getItems();
				
				if (node.rule.matching(items)) {
					node.rule.addCoveredInstance(tid);
					if (node.rule.getKlass() == instance.getKlass()) {
						node.rule.incrementHits();
					} else {
						node.rule.incrementMisses();
					}
				}
			}
		}
		return node.rule.getPessimisticErrorEstimate();
	}

	/**
	 * Extract the default rule, that is the majority class
	 * 
	 * @return the default rule
	 */
	private RuleADT extractDefaultRule() {
		short majorityKlass = Collections
				.max(training.getMapClassToFrequency().entrySet(), Comparator.comparingLong(Entry::getValue)).getKey();

		return new RuleADT(majorityKlass);
	}

	/**
	 * Removes redundant rule
	 * 
	 * @param rules to be filtered
	 * @return non-redundant rules
	 */
	private List<RuleADT> removeRedundant(List<RuleADT> rules) {
		List<RuleADT> finalRules = new ArrayList<RuleADT>();

		for (RuleADT ruleI : rules) {
			boolean isGeneral = true;
			for (int j = 0; j < finalRules.size() && isGeneral; j++) {
				RuleADT ruleJ = finalRules.get(j);

				if (ArraysAlgos.containsOrEquals(ruleI.getAntecedent(), ruleJ.getAntecedent())) {
					isGeneral = false;
				}
			}

			if (isGeneral) {
				finalRules.add(ruleI);
			}
		}
		return finalRules;
	}
}
