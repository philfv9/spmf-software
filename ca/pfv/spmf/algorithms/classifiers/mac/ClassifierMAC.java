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
package ca.pfv.spmf.algorithms.classifiers.mac;

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
import ca.pfv.spmf.algorithms.classifiers.general.RuleClassifier;

/**
 * Main class for the MAC classifier. It implements its own way of predicting
 * unseen examples. MAC collects the subset of rules matching the new object
 * from the set of rules. If all the rules matching the new object have the same
 * class label, MAC just simply assigns that label to the new object. If the
 * rules are not consistent in class labels, MAC assigns the klass with the
 * highest number of rules
 * 
 * To obtain the final classifier, rule are post-processed following these
 * steps.
 *
 * <ul>
 * <li>Rules are sorted according to confidence, support and size.</li>
 * <li>To use a rule in the classifier, it has to cover at least one example
 * from the training dataset, in other case it is discarded</li>
 *
 * <li>Finally, the majority class is selected. At this point there are two
 * possibilites:
 * <ul>
 * <li>All the dataset have been covered, in this case the majority class is
 * selected for the whole dataset</li>
 * 
 * <li>All the dataset have not been covered yet, in this case the majority
 * class is selected from the remaining instances</li>
 * </ul>
 * </li>
 * </ul>
 * 
 * @see AlgoMAC
 */
public class ClassifierMAC extends RuleClassifier {

	/**
	 * UID, used for serialization
	 */
	private static final long serialVersionUID = 5516816919309986278L;

	/**
	 * Performs a post-processing of the rules to form the final classifier
	 * 
	 * @param dataset   the training dataset
	 * @param listRules set of rules forming the classifier
	 */
	public ClassifierMAC(Dataset dataset, List<RuleMAC> listRules) {
		super("MAC");

		// Sort rules by confidence, support and then size
		Collections.sort(listRules, new Comparator<RuleMAC>() {
			public int compare(RuleMAC arg0, RuleMAC arg1) {
				if (Double.compare(arg0.getConfidence(), arg1.getConfidence()) != 0) {
					return -Double.compare(arg0.getConfidence(), arg1.getConfidence());
				} else if (Double.compare(arg0.getSupportRule(), arg1.getSupportRule()) != 0) {
					return -Double.compare(arg0.getSupportRule(), arg1.getSupportRule());
				} else {
					return -Integer.compare(arg0.size(), arg1.size());
				}
			}
		});

		// Only those rules whose antecedent at least cover one instance are selected
		// OPTIMIZED BY PHILIPPE: Bitset instead of Boolean[]
		BitSet coveredX = new BitSet(dataset.getInstances().size());

		// Get the list of records
		List<Instance> instances = dataset.getInstances();

		// For each rule
		for (RuleMAC rule : listRules) {
			// For each record
			for (int i = 0; i < instances.size(); i++) {
				Short[] instance = instances.get(i).getItems();

				// Check if that i-th record contains the rule antecedent
				if (!coveredX.get(i) && ArraysAlgos.isSubsetOf(rule.getAntecedent(), instance)) {
					// If yes, we remember that this rule cover this transaction
					coveredX.set(i);
					// And, we keep that rule
					if (!this.rules.contains(rule)) {
						this.rules.add(rule);
					}
				}
			}
		}

		// Select majority class, there are two possible scenario
		// 1.- There are instances not yet covered, the majority class for those
		// instances is selected
		// 2.- There are no instances not yet covered. Then, the majority class is the
		// one for the whole dataset
		Boolean allAreCovered = coveredX.isEmpty() == false;

		Map<Short, Long> classesCounter = new HashMap<Short, Long>();
		// Case 1
		if (allAreCovered) {
			for (int k = 0; k < this.rules.size(); k++) {
				RuleMAC rule = (RuleMAC) this.rules.get(k);
				Long counter = classesCounter.getOrDefault(rule.getKlass(), 0L);

				classesCounter.put(rule.getKlass(), counter + 1);
			}
		} else { // Case 2
			for (int i = 0; i < instances.size(); i++) {
				if (!coveredX.get(i)) {
					Instance instance = instances.get(i);
					short klass = instance.getKlass();

					Long counter = classesCounter.getOrDefault(klass, 0L);
					classesCounter.put(klass, counter + 1);
				}
			}
		}

		// Find the most frequent class
		Short defaultKlass = null;
		Long counter = null;

		// For each class
		for (Entry<Short, Long> entry : classesCounter.entrySet()) {
			// If this is the first class that we check or the counter is larger than all
			// other previously seen classes
			if (defaultKlass == null || counter < entry.getValue()) {
				// We remember that class and its counter
				counter = entry.getValue();
				defaultKlass = entry.getKey();
			}
		}
		Rule defaultRule = new RuleMAC(defaultKlass);
		add(defaultRule);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see lac.algorithms.Classifier#predict(lac.data.Instance)
	 */
	@Override
	public short predict(Instance instance) {
		Short[] instanceItems = instance.getItems();

		// Map to count the number of rules matching for each class value
		// Key: class value, Value: number of matches
		Map<Short, Long> matchPerKlass = new HashMap<Short, Long>();
		short defaultKlass = -1;

		// For each rule
		for (int i = 0; i < this.rules.size(); i++) {
			RuleMAC rule = (RuleMAC) this.rules.get(i);

			// If it is the default rule (rule without antecedent) we use it to get the
			// default class value
			if (rule.getAntecedent().isEmpty()) {
				defaultKlass = rule.getKlass();
				continue;
			}

			// If the antecedent of the rule is a subset of the  instance
			if (ArraysAlgos.isSubsetOf(rule.getAntecedent(), instanceItems)) {
				
				// increate the counter for that class value by 1
				Long counter = matchPerKlass.getOrDefault(rule.getKlass(), 0L);
				matchPerKlass.put(rule.getKlass(), counter + 1);
			}
		}

		// If no rule was fired, return the default class value
		if (matchPerKlass.isEmpty()) {
			return defaultKlass;
		}

		// Otherwise return the class that has the highest number of matches
		Entry<Short, Long> max = Collections.max(matchPerKlass.entrySet(),
				(entry1, entry2) -> entry1.getValue().compareTo(entry2.getValue()));
		return max.getKey();
	}
}