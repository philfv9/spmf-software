/**
 * This file is part of the SPMF library. 
 * Copyright (C) 
 *   
 * SPMF is free software: you can redistribute it and/or modify
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
package ca.pfv.spmf.algorithms.classifiers.general;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This class represents a classification algorithm. It is an abstract class.
 */
public abstract class ClassificationAlgorithm {
	
	/** Training time **/
	private long trainingTime;

	/** memory usage **/
	private double trainingMaxMemory;
	
	/** classifier ***/
	private Classifier classifier;
	
    /**
     * Main method used to create the classifier
     * 
     * @param training Dataset used to train the classifier
     * @return associative classifier
     * @throws Exception
     */
	public Classifier trainAndCalculateStats(Dataset training) throws Exception{
		// Initialize statistics
    	MemoryLogger.getInstance().reset();
    	trainingTime = System.currentTimeMillis();
    	
    	// Do the training
		classifier = train(training);
		
		// Finish calculating statistics
    	MemoryLogger.getInstance().checkMemory();
        trainingTime = System.currentTimeMillis() - trainingTime;
        trainingMaxMemory = MemoryLogger.getInstance().getMaxMemory();
		return classifier;
	}
	
	/**
	 * Train a classifier
	 * @param training a dataset for training
	 * @return a classifier
	 * @throws Exception if error while reading a file
	 */
    protected abstract Classifier train(Dataset training);
    
    /**
     * Get the training time
     * @return the training time
     */
	public long getTrainingTime() {
		return trainingTime;
	}

	/**
	 * Get the maximum amount of memory used for training
	 * @return the amount of memory
	 */
	public double getTrainingMaxMemory() {
		return trainingMaxMemory;
	}
	
	/**
	 * Get the name of this algorithm
	 * @return the name
	 */
	public abstract String getName();
	
	public void printStats(){
		System.out.println("============= " + getName() + "- STATS =============");
		System.out.println("Training time (ms):" + trainingTime);
		System.out.println("Max Memory (mb):" + trainingMaxMemory);
		if(classifier instanceof RuleClassifier) {
			RuleClassifier ruleClassifier = (RuleClassifier) classifier;
			System.out.println("Rule count: " + ruleClassifier.getNumberRules());
			System.out.println("Average attribute count per rule: " + ruleClassifier.getAverageNumberAttributes());
		}
		System.out.println("===================================================");
	}
}
