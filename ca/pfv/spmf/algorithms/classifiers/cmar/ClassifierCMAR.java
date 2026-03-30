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
package ca.pfv.spmf.algorithms.classifiers.cmar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.Rule;
import ca.pfv.spmf.algorithms.classifiers.general.RuleClassifier;

/**
 * Generates classifier by means of the previously obtained rules. It sorts the
 * rules by confidence, support and size. Then, general rules are only
 * considered removing low confidence and no general rules. Finally, only
 * positively correlated rules are considered
 * 
 * @see AlgoCMAR
 */
public class ClassifierCMAR extends RuleClassifier {
	
    /**
	 * UID
	 */
	private static final long serialVersionUID = 346166758556004366L;

	/**
     * Constructor
     * 
     * @param rules    forming the current classifier
     * @param training dataset used while training classifier
     * @param delta  delta
     * 
     * @throws Exception
     */
    public ClassifierCMAR(List<Rule> rules, Dataset training, int delta) {
        super("CMAR");
        RuleCMAR.NUMBER_INSTANCES = training.getInstances().size();
        CRTree.NUMBER_SINGLETONS = training.getDistinctItemsCount();
        CRTree crTree = new CRTree(training, delta);
        for (Rule rule : rules) {
            crTree.insert(rule);
        }
        crTree.pruneUsingCover();
        this.rules = crTree.getRules();
    }

    @Override
    public short predict(Instance instance) {
        List<RuleCMAR> matchingRules = obtainallRulesForRecord(instance.getItems());

        // If no rules satisfy record, it cannot be performed any prediction
        if (matchingRules.isEmpty()) {
            return NOPREDICTION;
        }

        // If only one rule return class
        if (matchingRules.size() == 1) {
            return matchingRules.get(0).getKlass();
        }

        // If more than one rule but all have the same class return calss
        if (onlyOneClass(matchingRules)) {
            return matchingRules.get(0).getKlass();
        }

        // Group rules
        Map<Short, List<RuleCMAR>> ruleGroups = groupRulesByKlass(matchingRules);

        // Weighted Chi-Squared (WCS) Values for each group and
        // Select group with best WCS value and return associated class
        return getClassWithBestChiQuareValue(ruleGroups);    
     }

    /**
     * Forms groups of rules in function of its consequent
     * 
     * @param rules to be grouped by consequent
     * @return group of rules by klass
     */
    private Map<Short, List<RuleCMAR>> groupRulesByKlass(List<RuleCMAR> rules) {
    	Map<Short, List<RuleCMAR>> rulesByGroup = new HashMap<Short, List<RuleCMAR>>();
        for (RuleCMAR rule : rules) {
        	// Improved efficiency by Philippe
        	List<RuleCMAR> rulesForKlass = rulesByGroup.get(rule.getKlass());
            if (rulesForKlass == null) {
            	rulesForKlass = new ArrayList<RuleCMAR>();
                rulesByGroup.put(rule.getKlass(), rulesForKlass);
            }
            rulesForKlass.add(rule);
        }
        return rulesByGroup;
    }

    /**
     * Check if in specified rules there are more than one class
     * 
     * @param rules to check if they have more class
     * @return true if there are only one class, false otherwise
     */
    private boolean onlyOneClass(List<RuleCMAR> rules) {
        short firstKlass = rules.get(0).getKlass();
        for (int i = 1; i < rules.size(); i++) {
            if (rules.get(i).getKlass() != firstKlass) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines and returns the weighted Chi Squared values for the groups of
     * rules.
     * 
     * @param ruleByGroup the given groups of rule.
     * @return array of weighted Chi-Squared value for a set of rule groups
     */
    private short getClassWithBestChiQuareValue(Map<Short, List<RuleCMAR>> rulesByGroup) {
    	// Philippe: improved efficiency of this method
    	// No need to build a map and store all values... can just find the maximum directly
    	double bestChi = -1;
    	short  bestKlass = -1;
        for (Entry<Short, List<RuleCMAR>> entry : rulesByGroup.entrySet()) {
            double wcsValue = 0.0;
            List<RuleCMAR> rules = entry.getValue();
			for (RuleCMAR rule:  rules) {
                double chiSquare = rule.getChiSquare();
                double chiSquareUB = rule.getChiSquareUpperBound();
                wcsValue += (chiSquare * chiSquare) / chiSquareUB;
            }
			if(wcsValue > bestChi) {
				bestChi = wcsValue;
				bestKlass = entry.getKey();
			}
        }
        return bestKlass;
    }

    /**
     * Obtains all rules which are fired for current example
     * 
     * @param example to check rules
     * @return list of rules fired with current example
     */
    private List<RuleCMAR> obtainallRulesForRecord(Short[] example) {
        List<RuleCMAR> result = new ArrayList<RuleCMAR>();
        for (Rule rule : rules) {
            if (rule.matching(example)) {
                result.add((RuleCMAR)rule);
            }
        }
        return result;
    }
}
