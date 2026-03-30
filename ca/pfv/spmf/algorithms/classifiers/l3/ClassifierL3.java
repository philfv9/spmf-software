/*
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
package ca.pfv.spmf.algorithms.classifiers.l3;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.Rule;
import ca.pfv.spmf.algorithms.classifiers.general.RuleClassifier;

/**
 * A rule classifier created by the L3 algorithm
 * 
 * @see AlgoL3
 */
public class ClassifierL3 extends RuleClassifier {
	/**
	 * UID for serialization
	 */
	private static final long serialVersionUID = 4178759210062021733L;

	/**
	 * Lazily create the classifier
	 * 
	 * @param training dataset used to generate the classifier
	 * @param rules    which will form the final classifier
	 */
	public ClassifierL3(Dataset training, List<Rule> rules) {
		super("L3");

		// Create two lists of rules to store rules of "level LI" and of "level LII"
		List<Rule> lI = new ArrayList<Rule>();
		List<Rule> lII = new ArrayList<Rule>();

		// Sorts rules by confidence, support, size and lexicographical order
		List<Rule> sortedRules = new ArrayList<Rule>(rules);
		Collections.sort(sortedRules, new Comparator<Rule>() {
			public int compare(Rule arg0, Rule arg1) {
				if (Double.compare(arg0.getConfidence(), arg1.getConfidence()) != 0) {
					return -Double.compare(arg0.getConfidence(), arg1.getConfidence());
				} else if (Double.compare(arg0.getSupportRule(), arg1.getSupportRule()) != 0) {
					return -Double.compare(arg0.getSupportRule(), arg1.getSupportRule());
				} else if (Integer.compare(arg0.size(), arg1.size()) != 0) {
					return -Integer.compare(arg0.size(), arg1.size());
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

		/**
		 * For each rule 1.- Find the instances (records that are covered by the rule in
		 * the rest of the dataset 2.- If the rule correctly classify at least one
		 * instance in the remaining dataset, it will be of levelI. If a rule doesn't
		 * cover correctly any instance, but it doesn't missclassify any one, it will be
		 * of levelII. Other rules will be discarded 3.- Remove transactions covered
		 * from current rule
		 * 
		 */
		BitSet instanceCovered = new BitSet(training.getInstances().size());  // Replaced by bitset -- Philippe

		// For each rule
		for (Rule rule : sortedRules) {
			// The number of instances for which the rule would make a correct prediction
			int correctly = 0;
			// The number of instances for which the rule would mage an incorrect prediction
			int incorrectly = 0;

			// For each record (instance)
			for (int i = 0; i < training.getInstances().size(); i++) {
				Instance instance = training.getInstances().get(i);
				Short[] items = instance.getItems();

				// If that instance is not covered by a rule yet, and the rule antecedent matches it
				if (!instanceCovered.get(i) && rule.matching(items)) {
					// If the class value is the same
					if (rule.getKlass() == instance.getKlass()) {
						// Increase the number of correct prediction
						correctly++;
						// Remember that this transaction has been covered by a rule
						instanceCovered.set(i);
					} else {
						// Increaset the number of incorrect predictions
						incorrectly++;
					}
				}
			}

			// If the number of correct prediction is more than 0, it is a LI rule
			if (correctly > 0) {
				lI.add(rule);
			} else if (correctly == 0 && incorrectly == 0) {
				// If the number of correct and incorrect predictions is 0, it is an LII rule
				lII.add(rule);
			}
		}

		// Add all the rules in the same final list of rules.
		// The rules from L1 are put first and then those of LII.
		this.rules = lI; // fixed inefficiency -- Philippe
		this.rules.addAll(lII);
	}

	/*
	 * (non-Javadoc)
	 * 
	 */ @Override
	public short predict(Instance instance) {
		// Loop over the rules and the first rule that matches is the prediction
		 // For each rule:
		for (Rule rule : this.rules) {
			//If this rule matches
			if (rule.matching(instance.getItems())) {
				// return the prediction
				return rule.getKlass();
			}
		}
		// If no rule match, no prediction is made.
		return NOPREDICTION;
	}
}
