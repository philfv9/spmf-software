/* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* It was obtained from the LAC library under the GNU GPL license and adapted for SPMF.
* @Copyright 2021
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
package ca.pfv.spmf.algorithms.classifiers.adt;

import java.io.Serializable;
import java.util.List;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.general.ClassificationAlgorithm;
import ca.pfv.spmf.algorithms.classifiers.general.RuleClassifier;

/**
 * Main class for the ADT algorithm. K. Wang, S. Zhou, and Y. He, â€œGrowing
 * decision trees on support-less associa-tion rules,â€? inProceedings of the
 * Sixth ACM SIGKDD International Conference onKnowledge Discovery and Data
 * Mining, ser. KDD 20. New York, NY, USA: ACM,2000, pp. 265â€?269.
 */
public class AlgoADT extends ClassificationAlgorithm implements Serializable {

	/**
	 * UID
	 */
	private static final long serialVersionUID = 8028343474834277531L;

	/**
	 * Minimum value for the confidence measure
	 */
	private double minConf;

	/**
	 * Minimum value for the merit measure
	 */
	private double minMerit;

	/**
	 * Constructor
	 * 
	 * @param config configuration used to obtain rules
	 */
	public AlgoADT(double minConf, double minMerit) {
		this.minConf = minConf;
		this.minMerit = minMerit;
	}

	/**
	 * Train a classifier
	 * 
	 * @param training dataset
	 * @return a rule classifier
	 */
	@Override
	public RuleClassifier train(Dataset training){
		RuleExtractorADT extractor = new RuleExtractorADT(training, minConf);
		List<RuleADT> rules = extractor.run();
		return new ClassifierADT(rules, minMerit, training);
	}

	@Override
	public String getName() {
		return "ADT";
	}

}
