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

import ca.pfv.spmf.algorithms.classifiers.general.Rule;

/**
 * Base class for a rule of ACCF. It extends base Rule, but add functionalities
 * to calculate all-confidence and informationGain
 * 
 * @see AlgoACCF
 */
public class RuleACCF extends Rule implements Serializable{

	/**
	 * UID
	 */
	private static final long serialVersionUID = -7617714831220064147L;

	/**
	 * Constructor
	 * 
	 * @param antecedent antecedent of the rule
	 * @param klass      consquent of the rule
	 */
	public RuleACCF(Short[] antecedent, short klass) {
		super(antecedent, klass);
	}

	@Override
	public String getMeasuresToString() {
		return " #SUP: " + getSupportRule() + " #CONF: " + getConfidence();
	}
}
