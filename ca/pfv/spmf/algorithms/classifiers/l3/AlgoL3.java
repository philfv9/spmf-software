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

import java.util.List;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.general.Rule;

/**
 * An implementation of the L3 algorithm to generate a class association rule classifier.
 * L3 was proposed in this paper: <br/><br/>
 * 
 *  E. Baralis and P. Garza, “A lazy approach
 * to pruning classification rules, Proceedings of the 2002 IEEE
 * International Conference on Data Mining, ser. ICDM2002. Washington, DC, USA:
 * IEEE Computer Society, 2002
 */
public class AlgoL3 extends ca.pfv.spmf.algorithms.classifiers.general.ClassificationAlgorithm {
	
    /**
     * Minimum frequency of occurrence for the rules
     */
    private double minSup;

    /**
     * Minimum confidence for the rules
     */
    private double minConf;
    
    /**
     * Default constructor
     * 
     * @param config Configuration used to train this classifier
     */
    public AlgoL3(double minSup, double minConf) {
        this.minSup = minSup;
        this.minConf = minConf;
    }

    /**
     * Train a classifier
     * @param training dataset
     * @return a rule classifier
     */
    @Override
    public ClassifierL3 train(Dataset training){
        FPGrowthForL3 fpgrowthMultiple = new FPGrowthForL3(training, minSup, minConf);
        List<Rule> rules = fpgrowthMultiple.run();
        return new ClassifierL3(training, rules);
    }

	@Override
	public String getName() {
		return "L3";
	}
}