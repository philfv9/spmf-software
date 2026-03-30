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
package ca.pfv.spmf.algorithms.classifiers.acn;

import java.util.List;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.general.ClassificationAlgorithm;
import ca.pfv.spmf.algorithms.classifiers.general.RuleClassifier;

/**
 * This is an implementation of the ACN algorithm, proposed in the following paper 
 * <br/><br/><br/>
 *  G. Kundu, M. M. Islam, S. Munir, and M. F. Bari, An associative
 * classifier with negative rules, in in 2008 11th IEEE International Conference
 * on ComputationalScience and Engineering, July 2008, pp. 369-375.
 */
public class AlgoACN extends ClassificationAlgorithm {

	/**
	 * Minimum frequency of occurrence for the rules
	 */
	private double minSup; // = 0.01;

	/**
	 * Minimum confidence for the rules
	 */
	private double minConf; // = 0.5;

	/**
	 * Minimum accuracy for the rules
	 */
	private double minAcc; // = 0.55;

	/**
	 * Minimum correlation for the rules
	 */
	private double minCorr; // = 0.2;

	/**
	 * Default constructor
	 * 
	 * @param config for this algorithm
	 */
	public AlgoACN(double minSup, double minConf, double minAcc, double minCorr) {
		this.minSup = minSup;
		this.minConf = minConf;
		this.minAcc = minAcc;
		this.minCorr = minCorr;
	}

	/**
	 * Train a classifier
	 * 
	 * @param training dataset
	 * @return a rule classifier
	 */
	@Override
	public RuleClassifier train(Dataset training)  {
		AprioriNegativeForACN apriori = new AprioriNegativeForACN(training, minSup, minConf);
		List<RuleACN> rules = apriori.run();
		return new ClassifierACN(rules, training, minConf, minAcc, minCorr);
	}

	@Override
	public String getName() {
		return "ACN";
	}

}
