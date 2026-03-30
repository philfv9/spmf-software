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

import java.io.Serializable;
import java.util.Set;

import ca.pfv.spmf.algorithms.classifiers.general.Rule;

/**
 * A classification rule as defined by the MAC algorithm. 
 * The difference with a regular classification rule is that the tidsets of the rules are kept.
 * 
 * @see AlgoMAC
 */
public class RuleMAC extends Rule implements Serializable {
    /**
	 * UID
	 */
	private static final long serialVersionUID = -8673519931647454948L;

	/**
     * Tidset of the antecedent
     */
    private Set<Integer> tidsetAntecedent;

    /**
     * Tidset of the rule
     */
    private Set<Integer> tidsetRule;

    /**
     * Constructor
     * 
     * @param klass to be used as consequent
     */
    public RuleMAC(short klass) {
        super(klass);
    }

    /**
     * Constructor
     * 
     * @param antecedent       of the rule
     * @param tidsetAntecedent set of ids where the antecedent is present
     * @param klass            to be used as consequent
     * @param tidsetRule       set of ids where the rule is present
     */
    public RuleMAC(Short[] antecedent, Set<Integer> tidsetAntecedent, Short klass, Set<Integer> tidsetRule) {
        super(antecedent, klass);
        this.setTidsetAntecedent(tidsetAntecedent);
        this.setTidsetRule(tidsetRule);
    }

    /**
     * @param tidsetAntecedent
     */
    public void setTidsetAntecedent(Set<Integer> tidsetAntecedent) {
        this.tidsetAntecedent = tidsetAntecedent;
        this.supportAntecedent = tidsetAntecedent.size();
    }

    /**
     * Set the tidset for the whole rule
     * 
     * @param tidsetRule set of ids where the rule is present
     */
    public void setTidsetRule(Set<Integer> tidsetRule) {
        this.tidsetRule = tidsetRule;
        this.supportRule = tidsetRule.size();
    }


    /**
     * Get the tidset for the whole rule
     * 
     * @return the tidset for the rule
     */
    public Set<Integer> getTidsetRule() {
        return tidsetRule;
    }

    /**
     * Get the tidset for the antecedent
     * 
     * @return the tidset for the antecedent
     */
    public Set<Integer> getTidsetAntecedent() {
        return tidsetAntecedent;
    }
    
    /**
     * Get the measures of this rule as a string
     * return a string
     */
	public String getMeasuresToString() {
		return " #SUP: " + getSupportRule() 
		+ " #CONF: " + getConfidence();
	}
}
