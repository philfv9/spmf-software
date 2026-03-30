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
package ca.pfv.spmf.algorithms.classifiers.acac;

import java.io.Serializable;
import java.util.List;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.general.ClassificationAlgorithm;
import ca.pfv.spmf.algorithms.classifiers.general.Classifier;

/**
 * Implementation of the algorithm ACAC for associative classification. This
 * algorithm was proposed in this paper: <br/>
 * <br/>
 * Please refer to the original publication for more information on this
 * algorithm. Z. Huang, Z. Zhou, T. He, and X. Wang, "Acac: Associative
 * classification based on all-confidence", 11 2011, pp. 289-293. <br/>
 * <br/>
 * 
 * This implementation was obtained under the GPL license from the LAC library
 * and adapted for the SPMF library.  In particular some optimizations were made to avoid
 * creating unecessary temporary objects. 
 */
public class AlgoACAC extends ClassificationAlgorithm implements Serializable {

	/**
	 * UID used for serialization
	 */
	private static final long serialVersionUID = -5817121137954609095L;

	/** minimum support */
	double minSup;

	/** minimum all confidence */
	double minAllConf;

	/** minimum confidence */
	double minConf;

	/**
	 * Main constructor
	 * 
	 * @param minSup     minimum support
	 * @param minConf    minimum confidence
	 * @param minAllConf minimum all-confidence
	 */
	public AlgoACAC(double minSup, double minConf, double minAllConf) {
		this.minSup = minSup;
		this.minConf = minConf;
		this.minAllConf = minAllConf;
	}

	/**
	 * Train a classifier
	 * 
	 * @param dataset the training dataset
	 * @return a rule classifier
	 */
	@Override
	public Classifier train(Dataset dataset) {
		AprioriForACAC apriori = new AprioriForACAC();
		List<RuleACAC> rules = apriori.run(dataset, minSup, minConf, minAllConf);
		return new ClassifierACAC(rules);
	}

	@Override
	public String getName() {
		return "ACAC";
	}
}
