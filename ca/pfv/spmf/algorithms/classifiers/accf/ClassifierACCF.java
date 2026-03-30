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
package ca.pfv.spmf.algorithms.classifiers.accf;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.Rule;
import ca.pfv.spmf.algorithms.classifiers.general.RuleClassifier;

/**
 * Main class for the ACCF classifier. It implements its own way of predicting
 * unseen examples
 * 
 * @see AlgoACCF
 */
public class ClassifierACCF extends RuleClassifier implements Serializable {
	/**
	 * UID
	 */
	private static final long serialVersionUID = -8202761480378245086L;

	/**
	 * Default constructor
	 * 
	 * @param rules array of rules forming trhe classifier
	 */
	public ClassifierACCF(List<Rule> rules) {
		super("ACCF");
		Collections.sort(rules, new Comparator<Rule>() {
			@Override
			public int compare(Rule arg0, Rule arg1) {
				if (Double.compare(arg0.getConfidence(), arg1.getConfidence()) != 0) {
					return -Double.compare(arg0.getConfidence(), arg1.getConfidence());
				} else if (Double.compare(arg0.getSupportRule(), arg1.getSupportRule()) != 0) {
					return -Double.compare(arg0.getSupportRule(), arg1.getSupportRule());
				} else {
					return -Integer.compare(arg0.size(), arg1.size());
				}
			}
		});

		for (int i = 0; i < rules.size(); i++) {
			this.add(rules.get(i));
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 */
	@Override
	public short predict(Instance instance) {
		Short[] example = instance.getItems();

		// Check if some rule matchs
		for (Rule rule : rules) {
			if (rule.matching(example))
				return rule.getKlass();
		}

		// When no rule is fired, we need to find the first rule whose antecedent has at
		// least one matching with the example
		for (Rule rule : rules) {
			List<Short> ruleAntecedent = rule.getAntecedent();

			// == Modified by Phil for efficiency
			if (hasNonEmptyIntersection(ruleAntecedent, example))
				return rule.getKlass();
		}

		// We don't know how to classify that
		return NOPREDICTION;
	}

	/**
	 * Performs the intersection of two sets of items
	 * 
	 * @param antecedent first set
	 * @param instance   second set
	 * @return true if the two sets have some non empty intersection
	 */
	public static boolean hasNonEmptyIntersection(List<Short> antecedent, Short[] instance) {
		// Method by Philippe
		// Even if we use contains, this will be efficient since the antecedent of a
		// rule usually
		// contains very few items.
		for (Short item : instance) {
			if (antecedent.contains(item)) {
				return true;
			}
		}
		return false;
	}

}
