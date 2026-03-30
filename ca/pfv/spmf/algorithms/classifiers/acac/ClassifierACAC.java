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
package ca.pfv.spmf.algorithms.classifiers.acac;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.classifiers.general.RuleClassifier;

/**
 * Main class for the ACAC classifier. It implements its own way of predicting
 * unseen examples. ACAC collects the subset of rules matching the new object
 * from the set of rules. If all the rules matching the new object have the same
 * class label, ACAC just simply assigns that label to the new object. If the
 * rules are not consistent in class labels, ACAC divides the rules into groups
 * according to class labels. All rules in a group share the same class label
 * and each group has a distinct label.
 *
 * <ul>
 * <li>Firstly, entropy information of a rule condset X is used to evaluate its
 * classification power. Laplace expected error estimate is used to estimate
 * this probability.</li>
 *
 * <li>Secondly, the combined effect of a group rule is measured by calculating
 * strength of a rule. The strength of a group combines the average information
 * entropy with the number of rules in the group. The information entropy
 * contribution is larger, so ACAC gives a high weight to it.</li>
 *
 * <li>Finally, ACAC assigns the class label of the group with maximum strength
 * to the new object.</li>
 * </ul>
 * 
 * This implementation was obtained under the GPL license from the LAC library and adapted
 * for the SPMF library.
 * 
 * @see AlgoACAC
 */
public class ClassifierACAC extends RuleClassifier implements Serializable{
	/**
	 * UID for serialization
	 */
	private static final long serialVersionUID = -8555494816204669319L;

	/**
	 * Default constructor
	 * 
	 * @param rules forming the classifier
	 */
	public ClassifierACAC(List<RuleACAC> rules) {
		super("ACAC");
		this.rules.addAll(rules);
	}

	@Override
	public short predict(ca.pfv.spmf.algorithms.classifiers.data.Instance rawInstance) {
		// The current record (instance)
		Short[] instance = rawInstance.getItems();

		// Find the rules that are fired, i.e. that match with this instance
		List<RuleACAC> firedRules = new ArrayList<RuleACAC>();
		for (int i = 0; i < rules.size(); i++) {
			RuleACAC rule = (RuleACAC) rules.get(i);

			if (rule.matching(instance)) {
				firedRules.add(rule);
			}
		}

		// When no rule is fired, classifier is not able to perform a prediction
		if (firedRules.isEmpty()) {
			return NOPREDICTION;
		}

		Map<Short, Double> mapStrengths = new HashMap<Short, Double>();
		Map<Short, List<RuleACAC>> rulesPerKlass = new HashMap<Short, List<RuleACAC>>();

		Short klass = firedRules.get(0).getKlass();
		for (RuleACAC firedRule : firedRules) {
			// Calculate strength of firedRules by klass
			short ruleKlass = firedRule.getKlass();
			Double val = mapStrengths.get(ruleKlass);
			
			// ==== CODE BELOW IS OPTIMIZED BY PHILIPPE ====
			if (val == null) {
				mapStrengths.put(ruleKlass, 0.0);
				List<RuleACAC> list = new ArrayList<RuleACAC>();
				rulesPerKlass.put(ruleKlass, list);
				list.add(firedRule);
			}else{
				rulesPerKlass.get(ruleKlass).add(firedRule);
			}
		}

		// When all the firedRules have the same class, we use directly that
		if (rulesPerKlass.size() == 1) {
			return klass;
		}

		double numberKlasses = rulesPerKlass.size();   // PHIL: fixed 

		// Calculate information gain for each group of rule per class
		for (Entry<Short, List<RuleACAC>> entry : rulesPerKlass.entrySet()) {
			klass = entry.getKey();
			List<RuleACAC> rules = entry.getValue();
			double n = rules.size();

			double supAcc = 0.0;
			double numerator = 0.0;

			for (RuleACAC rule : rules) {
				double informationGain = -1.0 / (Math.log(numberKlasses) / Math.log(2.0));

				if (Double.isInfinite(informationGain))
					informationGain = 0.0;

				for (Entry<Short, List<RuleACAC>> perKlass : rulesPerKlass.entrySet()) {
					short klassI = perKlass.getKey();

					double ruleSup = rule.getSupportByKlass(klassI);
					double condsup = rule.getSupportAntecedent();

					double pCiX = (ruleSup + 1) / (condsup + numberKlasses);

					informationGain += pCiX * (Math.log(pCiX) / Math.log(2.0));
				}
				supAcc += rule.getSupportAntecedent();

				numerator += rule.getSupportAntecedent() * informationGain;

			}
			double strength = 0.9 * (1.0 - numerator / supAcc) + 0.1 * n / firedRules.size();

			mapStrengths.put(klass, strength);
		}

		// Get the class with the highest value of information gain
		return Collections
				.max(mapStrengths.entrySet(), (entry1, entry2) -> entry1.getValue().compareTo(entry2.getValue()))
				.getKey();
	}
}
