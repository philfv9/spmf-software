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

import java.util.List;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.general.ClassificationAlgorithm;

/**
 * An implementation of the MAC algorithm for associative classification. It was proposed in this paper:
 * <br/><br/>
 *  N. Abdelhamid, A. Ayesh, F. Thabtah,
 * S. Ahmadi, and W. Hadi, A multiclass associative classification
 * algorithm, Jurnal of Information & Knowledge Manage-ment, vol. 11, 06 2012.
 */
public class AlgoMAC extends ClassificationAlgorithm {
	
	/**
	 * Get the algorithm name
	 */
	public String getName() {
		return "MAC";
	}

	/**  minimum support */
	double minSup;
	
	/**  minimum confience */
	double minConf;

    /**
     * Default constructor
     * @param minsup minimum support
     * @param minconf minimum confidence
     */
    public AlgoMAC(double minSup, double minConf) {
        this.minSup = minSup;
        this.minConf = minConf;
    }

    /**
     * Train a classifier
     * @param dataset dataset
     * @return a rule classifier
     */
    @Override
    public ClassifierMAC train(Dataset dataset){
        Eclat eclat = new Eclat(dataset, minSup, minConf);
        List<RuleMAC> rules = eclat.run();
        return new ClassifierMAC(dataset, rules);
    }
}