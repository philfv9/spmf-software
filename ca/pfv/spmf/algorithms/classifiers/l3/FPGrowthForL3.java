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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.classifiers.cmar.FPGrowthForCMAR;
import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.general.Rule;

/**
 * An adaptation of the L3 algorithm used by CMAR and L3 for class association
 * rule mining.
 * 
 * @see AlgoL3
 */
public class FPGrowthForL3 extends FPGrowthForCMAR {
	/**
	 * Mapping storing support of each class value (key: class, value: support)
	 */
	private Map<Short, Long> supportByKlass;

	/**
	 * Constructor
	 * 
	 * @param training Dataset used to extract class association rules
	 * @param minSup   minimum support used to calculate the minimum support by
	 *                 class
	 * @param minConf  minimum confidence for the mined rules
	 */
	public FPGrowthForL3(Dataset training, double minSup, double minConf) {
		super(training, minSup, minConf);

		// Store the support of each class value in a map, where support is expressed as
		// a number of transactions.
		supportByKlass = new HashMap<Short, Long>();
		for (Entry<Short, Long> entry : training.getMapClassToFrequency().entrySet()) {
			supportByKlass.put(entry.getKey(), (long) Math.ceil(entry.getValue() * minSup));
		}
	}

	@Override
	protected void generateRules(short[] itemset, int itemsetLength, long support, Map<Short, Long> counterByKlass) {
		// Copy the itemset into a buffer and sort the items
		short[] itemsetOutputBuffer = new short[itemsetLength];
		System.arraycopy(itemset, 0, itemsetOutputBuffer, 0, itemsetLength);
		Arrays.sort(itemsetOutputBuffer, 0, itemsetLength);

		// Try to combine this itemset with each class value to make a rule.
		// For each class value:
		for (Entry<Short, Long> entry : counterByKlass.entrySet()) {
			// Make a new rule
			Rule rule = new RuleL3(itemsetOutputBuffer, entry.getKey());
			rule.setSupportAntecedent(support);
			rule.setSupportRule(entry.getValue());
			rule.setSupportKlass(dataset.getMapClassToFrequency().get(rule.getKlass()));

			// If the rule has a high support and confidence
			if (rule.getSupportRule() >= this.supportByKlass.get(rule.getKlass())
					&& rule.getConfidence() >= this.minConf)
				// Keep the rule
				rules.add(rule);
		}
	}
}
