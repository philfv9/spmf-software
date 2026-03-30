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
package ca.pfv.spmf.algorithms.classifiers.cmar;

import java.util.List;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.general.ClassificationAlgorithm;
import ca.pfv.spmf.algorithms.classifiers.general.Rule;

/**
 * This is an implementation of the CMAR algorithm. CMAR is an algorithm for classification
 * based on association rules, proposed in this paper: <br\><br\>
 * 
 * Li, J. Han, and J. Pei, Cmar:
 * Accurate and efficient classification based on multiple class-association
 * rules,in 2002 IEEE International Conference on DataMining(ICDM01), 2001,
 * pp. 369-376
 */
public class AlgoCMAR extends ClassificationAlgorithm {
	

	/**  minimum support */
	double minSup;
	
	/**  minimum confience */
	double minConf;
	
	/** delta */
	int delta;
	
    /**
     * Default constructor
     * @param minsup minimum support
     * @param minconf minimum confidence
     * @param delta delta
     */
    public AlgoCMAR(double minSup, double minConf, int delta) {
    	this.minSup = minSup;   // 0.01
    	this.minConf = minConf;  // 0.5
    	this.delta = delta;   // 4
    }
    

    @Override
	public String getName() {
		return "CMAR";
	}

    /**
     * Train a classifier
     * @param dataset a training dataset
     * @return a rule classifier
     * @throws Exception if an error occurs
     */
    @Override
    public ClassifierCMAR train(Dataset dataset){
    	// Apply a modified FPGrowth algorithm to obtain the rules
        FPGrowthForCMAR fpgrowth = new FPGrowthForCMAR(dataset, minSup, minConf);
        List<Rule> rules = fpgrowth.run();
        
        // Return a classifier that is created using these rules
        return new ClassifierCMAR(rules, dataset, delta);
    }
}