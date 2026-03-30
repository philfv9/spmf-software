/**
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
package ca.pfv.spmf.algorithms.classifiers.cba;

/**
 * Class used to represent a selected rule while performing the post-processing
 * in CBA. A selectedRule is composed of the rule, the default klass and the
 * total of errors produced by them.
 */
public class SelectedRule implements Comparable<SelectedRule> {
    /**
     * Default class for the selected rule
     */
    private short defaultKlass;

    /**
     * Total number of errors while using this rule
     */
    private Long totalErrors;

    /**
     * Selected rule
     */
    private RuleCBA rule;

    /**
     * Constructor
     * 
     * @param rule         selected rule
     * @param defaultKlass default class
     * @param totalErrors  total number of errors while using this rule
     */
    public SelectedRule(RuleCBA rule, short defaultKlass, Long totalErrors) {
        this.rule = rule;
        this.defaultKlass = defaultKlass;
        this.totalErrors = totalErrors;
    }

    /**
     * Get the rule
     * 
     * @return the selected rule
     */
    public RuleCBA getRule() {
        return this.rule;
    }

    /**
     * Get the default class
     * 
     * @return the default class for the selected rule
     */
    public short getDefaultKlass() {
        return this.defaultKlass;
    }

    /**
     * Returns the total number of errors
     * 
     * @return total number of errors
     */
    public Long getTotalErrors() {
        return this.totalErrors;
    }

    /**
     * This has to be implemented to be able to sort array of selected rules in a
     * descending order of total errors
     */
    @Override
    public int compareTo(SelectedRule other) {
        return Long.compare(other.totalErrors, this.totalErrors);
    }
}
