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

import java.io.Serializable;

import ca.pfv.spmf.algorithms.classifiers.general.Rule;

/**
 * A rule of the L3 classifier.
 *  @see AlgoL3
 */
public class RuleL3 extends Rule implements Serializable {

	/**
	 * UID
	 */
	private static final long serialVersionUID = -764236368300958022L;

	/**
	 * Default Constructor
	 */
	public RuleL3() {
		super();
	}

	/**
	 * Constructor
	 * 
	 * @param antecedent antecedent of the rule
	 * @param klass      consquent of the rule
	 */
	public RuleL3(short[] antecedent, Short klass) {
		super(antecedent, klass);
	}

	@Override
	public String getMeasuresToString() {
		return " #SUP: " + getSupportRule() + " #CONF: " + getConfidence();
	}
}
