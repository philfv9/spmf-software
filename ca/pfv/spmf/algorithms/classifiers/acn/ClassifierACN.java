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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.RuleClassifier;

/**
 * Main class for the ACN classifier. It implements its own way of predicting
 * unseen examples
 * <ul>
 * <li>Precedence of rules. Sorting is required.
 * <li>By confidence</li>
 * <li>By pearson</li>
 * <li>By support of the rule</li>
 * <li>If it is a negative rule has less precedence than positive</li>
 * <li>Size of the antecedent</li>
 * </ul>
 * </li>
 * <li>ACN builds a classifier based on database coverage similar to CBA. ACN
 * takes each rule according to the sorted order and tests if it can provide
 * correct classification for at least one remaining training example. If it
 * can, ACN checks to see if it is a positive or negative rule. If it is a
 * positive rule,it is immediately taken in the final classifier.</li>
 * <li>On the other hand, if it is a negative rule, ACN calculates the accuracy
 * of the rule on the examples remaining. This rule is taken in the final
 * classifier only if the accuracy on the remaining examples is beyond a
 * user-defined threshold. In this way, ACN proceeds until all rules have been
 * examined or all examples have been covered. In case database is uncovered,
 * the default rule is the majority class from uncovered examples. Otherwise it
 * is simply the majority class from the entire training set</li>
 * </ul>
 * 
 * @see AlgoACN
 */
public class ClassifierACN extends RuleClassifier implements Serializable{

	/**
	 * UID
	 */
	private static final long serialVersionUID = 1486255234410043031L;

	/**
	 * Build the classifier
	 *
	 * @param rules    forming the classifier
	 * @param training dataset used to train the classifier
	 * @param config   for generating the classifier
	 */
	public ClassifierACN(List<RuleACN> rules, Dataset training, double minConf, double minAcc, double minCorr) {
		super("ACN");
		Collections.sort(rules, new Comparator<RuleACN>() {
			public int compare(RuleACN arg0, RuleACN arg1) {
				if (Double.compare(arg0.getConfidence(), arg1.getConfidence()) != 0) {
					return -Double.compare(arg0.getConfidence(), arg1.getConfidence());
				} else if (Double.compare(arg0.getPearson(), arg1.getPearson()) != 0) {
					return -Double.compare(arg0.getPearson(), arg1.getPearson());
				} else if (Double.compare(arg0.getSupportRule(), arg1.getSupportRule()) != 0) {
					return -Double.compare(arg0.getSupportRule(), arg1.getSupportRule());
				} else if (Integer.compare(arg0.getNegativeItems(), arg1.getNegativeItems()) != 0) {
					return -Double.compare(arg0.getNegativeItems(), arg1.getNegativeItems());
				} else {
					return -Integer.compare(arg0.size(), arg1.size());
				}
			}
		});

		List<Integer> instancesCoveredByRule = new ArrayList<Integer>();

		List<Boolean> coveredInstances = new ArrayList<Boolean>(Arrays.asList(new Boolean[training.getInstances().size()]));
		Collections.fill(coveredInstances, Boolean.FALSE);

		List<Instance> instances = training.getInstances();
		
		for (int i = 0; i < rules.size()
				&& coveredInstances.stream().filter(p -> p == false).findFirst().isPresent(); i++) {
			RuleACN rule = rules.get(i);

			// Check if cover at least one instance
			for (int j = 0; j < instances.size(); j++) {
				if (coveredInstances.get(j))
					continue;

				if (rule.matching(instances.get(j).getItems())) {
					instancesCoveredByRule.add(j);
				}
			}

			if (instancesCoveredByRule.isEmpty())
				continue;

			if (!rule.isANegativeRule() || this.getAccurracyRemainingDataset(rule, training, coveredInstances) >= minAcc) {
				this.rules.add(rule);

				// Remove covered instances
				for (int m = 0; m < instancesCoveredByRule.size(); m++) {
					int indexInstance = instancesCoveredByRule.get(m);
					coveredInstances.set(indexInstance, true);
				}
			}
		}

		// Check if there are instances not covered yet
		HashMap<Short, Long> counterByKlass = new HashMap<Short, Long>();
		for (int i = 0; i < training.getInstances().size(); i++) {
			if (!coveredInstances.get(i)) {
				short klass = instances.get(i).getKlass();

				Long count = counterByKlass.getOrDefault(klass, 0L);
				counterByKlass.put(klass, count + 1L);
			}
		}

		short defaultKlass;
		if (counterByKlass.isEmpty()) {
			// Get majority for the whole dataset
			defaultKlass = Collections.max(training.getMapClassToFrequency().entrySet(),
					(entry1, entry2) -> entry1.getValue().compareTo(entry2.getValue())).getKey();
		} else {
			// Get majority for the remaining dataset
			defaultKlass = Collections
					.max(counterByKlass.entrySet(), (entry1, entry2) -> entry1.getValue().compareTo(entry2.getValue()))
					.getKey();
		}

		// Remove rules by confidence
		this.rules.removeIf(rule -> rule.getConfidence() < minConf);

		// Remove rules by pearson coefficient
		this.rules.removeIf(rule -> ((RuleACN) rule).getPearson() < minCorr);

		// Default klass, rule without antecedent
		this.rules.add(new RuleACN(defaultKlass));
	}

	/**
	 * Get accuracy for the remaining dataset
	 * @param rule a rule
	 * @param dataset the dataset
	 * @param coveredInstances the covered instances
	 * @return the accuracy
	 */
	private double getAccurracyRemainingDataset(RuleACN rule, Dataset dataset, List<Boolean> coveredInstances) {
		double accuracy = 0;
		int numberNotCoveredInstances = 0;

		List<Instance> instances = dataset.getInstances();
		for (int i = 0; i < instances.size(); i++) {
			if (coveredInstances.get(i))
				continue;

			Instance instance = instances.get(i);
			// PHIL: Changed order below to optimize
			if (rule.getKlass() 
					== instance.getKlass() &&
					rule.matching(instance.getItems())) {
				accuracy += 1;
			}
			numberNotCoveredInstances += 1;
		}

		return accuracy / numberNotCoveredInstances;
	}
}
