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
package ca.pfv.spmf.algorithms.classifiers.cba;

import java.util.List;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.general.ClassificationAlgorithm;
import ca.pfv.spmf.algorithms.classifiers.general.Classifier;

/**
 * This is an implementation of the CBA and CBA2 algorithms. CBA is an algorithm
 * for classification based on association rules, proposed in this paper:
 * <br\><br\>
 * 
 * B. Liu, W. Hsu, and Y. Ma, Integrating classification and association rule
 * mining Proc. 4th International Conference on Knowledge Discovery and Data
 * Mining (KDD98),1998, pp. 80-86
 * 
 * and CBA2 was proposed in this paper:
 * 
 * B. Liu, Y. Ma, and C. Wong, Classification Using Association Rules:
 * Weaknesses and Enhancements. Kluwer Academic Publishers, 2001, pp. 591-601
 */
public class AlgoCBA extends ClassificationAlgorithm {

	/** minimum support */
	double minSup = 0d;

	/** minimum confience */
	double minConf = 0d;

	/** if true, CBA2 will be applied instead of CBA. */
	boolean shouldRunCBA2;

	/**
	 * Default constructor
	 * 
	 * @param minsup  minimum support
	 * @param minconf minimum confidence
	 */
	public AlgoCBA(double minSup, double minConf, boolean shouldRunCBA2) {
		this.minSup = minSup;
		this.minConf = minConf;
		this.shouldRunCBA2 = shouldRunCBA2;
	}

	/**
	 * Train a classifier
	 * 
	 * @param training dataset
	 * @return a rule classifier
	 */
	public Classifier train(Dataset training) {
		// Run Apriori to get the rules
		List<RuleCBA> rules;
		if (shouldRunCBA2) { // CBA version 2
			rules = new AprioriForCBA2().runAlgorithm(training, minSup, minConf);
		} else { // CBA version 1
			rules = new AprioriForCBA().runAlgorithm(training, minSup, minConf);
		}

		// Generate the classifier from these rules
		CBAM2 cba = new CBAM2(training, rules);
		return cba.getClassifier(getName());
	}

	/**
	 * Get the name of this algorithm
	 */
	public String getName() {
		return shouldRunCBA2 ? "CBA2" : "CBA";
	}
}