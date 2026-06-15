package ca.pfv.spmf.algorithms.associationrules.pnar;

/* This file is copyright (c) 2008-2025 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.ArrayList;
import java.util.List;

/**
 * A collection of positive and negative association rules found by the
 * PNAR algorithm (Cornelis et al., 2006).
 *
 * @see AlgoPNAR
 * @see RulePNAR
 * @author Philippe Fournier-Viger, 2026
 */
public class RulesPNAR {

    /** Name of this set of rules (for display). */
    private final String name;

    /** The rules stored in this collection. */
    private final List<RulePNAR> rules = new ArrayList<>();

    /**
     * Constructor
     * @param name the name for the set of rules
     */
    public RulesPNAR(String name) {
        this.name = name;
    }

    /**
     * Add a rule
     * @param rule the rule to add
     */
    public void addRule(RulePNAR rule) {
        rules.add(rule);
    }

    /**
     * Get the list of rules.
     * @return list of rules
     */
    public List<RulePNAR> getRules() {
        return rules;
    }

    /**
     * Get the number of rules.
     * @return rule count
     */
    public int getRuleCount() {
        return rules.size();
    }

    /**
     * Print all rules to System.out.
     */
    public void printRules() {
        System.out.println("=== " + name + " (" + rules.size() + " rules) ===");
        for (RulePNAR rule : rules) {
            System.out.println(rule + "  #SUP: " + rule.getAbsoluteSupport()
                    + "  #CONF: " + rule.getConfidence());
        }
    }
}