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
*/
package ca.pfv.spmf.algorithms.classifiers.accf;

import java.util.List;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.general.Rule;

/**
 * Main class for the algorithm ACCF. Please refer to the original publication
 * for more information on this algorithm X. <br/>
 * <br/>
 * <br/>
 * 
 * Li, D. Qin, and C. Yu, Associative classification based on closed frequent
 * itemsets,2008, pp. 380-384
 * 
 * @see AlgoAACF
 */
public class AlgoACCF extends ca.pfv.spmf.algorithms.classifiers.general.ClassificationAlgorithm {
	/** minimum support */
	double minSup;

	/** minimum confidence */
	double minConf;

	/**
	 * Default constructor
	 * 
	 * @param config Configuration for this algorithm
	 */
	public AlgoACCF(double minSup, double minConf) {
		this.minSup = minSup;
		this.minConf = minConf;
	}

	/**
	 * Train a classifier
	 * 
	 * @param training dataset
	 * @return a rule classifier
	 */
	@Override
	public ClassifierACCF train(Dataset training) {
		CHARMForACCF charm = new CHARMForACCF();
		List<Rule> rules = charm.run(training, minSup, minConf);
		return new ClassifierACCF(rules);
	}

	@Override
	public String getName() {
		return "ACCF";
	}

}
