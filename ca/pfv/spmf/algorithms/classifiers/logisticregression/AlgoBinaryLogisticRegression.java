package ca.pfv.spmf.algorithms.classifiers.logisticregression;

import java.util.Arrays;
import java.util.List;

import ca.pfv.spmf.tools.MemoryLogger;

/*
 *  Copyright (c) 2022 Philippe Fournier-Viger
 * 
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
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
/**
 * The Binary Logistic Regression classifier. It is a classifier used for binary
 * classification prolems.
 * 
 * @author Philippe Fournier-Viger
 *
 */
public class AlgoBinaryLogisticRegression {

	/** The weights (one weight per feature) */
	double[] weights = null;
	/** the bias */
	double bias;
	/** The number of iterations */
	int iterationCount = 1000;
	/** The learning rate */
	double learningRate = 0.1d;

	// for stats

	/** The total number of iterations */
	int totalNumberIterations = 0;
	/** The total time */
	long totalTime = 0;
	/** The total memory */
	double totalMemory = 0;

	/**
	 * Constructor
	 */
	AlgoBinaryLogisticRegression() {

	}

	/**
	 * Predict the class for an instance
	 * 
	 * @param instance the instance
	 * @return the class (true or false)
	 */
	boolean predictBoolean(InstanceContinuous instance) {
		return weightedSum(instance.values) > 0.50d;
	}
	
	/**
	 * Predict the class for an instance
	 * 
	 * @param instance the instance
	 * @return the class as value in [0,1], where 0 = first class and  1 = second class
	 */
	double predictDouble(InstanceContinuous instance) {
		return weightedSum(instance.values);
	}

	/**
	 * Set the iteration count
	 * 
	 * @param count the count
	 */
	public void setIterationCount(int count) {
		iterationCount = count;
	}

	/**
	 * Set the learning rate
	 * 
	 * @param rate the learning rate
	 */
	public void setLearningRate(double rate) {
		learningRate = rate;
	}

	/**
	 * Calcaulate the sigmoid function sigmoid(z)
	 * 
	 * @param z the value z
	 * @return the result
	 */
	double sigmoid(double z) {
		return 1d / (1d + Math.pow(Math.E, (-1d * z)));
	}

	/**
	 * Calculate the weighted sum
	 * 
	 * @param instance an instance
	 * @return the sum
	 */
	private double weightedSum(double[] instance) {
		double sum = bias;
		for (int i = 0; i < weights.length; i++) {
			sum += instance[i] * weights[i];
		}
		return sigmoid(sum);
	}

	/**
	 * Train the model
	 * 
	 * @param instances     a list of instances
	 * @param targetOutputs a list of target classes corresponding to these
	 *                      instances
	 */
	void train(List<InstanceContinuous> instances, List<Boolean> targetOutputs) {

		totalNumberIterations = 0;
		totalTime = System.currentTimeMillis();
		MemoryLogger.getInstance().reset();

		int featureCount = instances.get(0).values.length;

		// Initialize weights
		weights = new double[featureCount];
		// Randomly initialize the bias
		bias = Math.random();

		// Array to store the modifications to be done to each weight during an iteration
		double weightChanges[] = new double[featureCount];
		// Variable to store how much the bias should be changed during an iteration
		double biasChange;
		
		// Learning rate divided by number of instances
		double learningRateDivided = learningRate / instances.size();
		
		// Variables to decide whether we should stop iterating
		// More precisely, we will stop if bias is updated by a value that is 
		//  greater than stopMin and less than stopMax. This is a design decision. 
		double stopMax = (learningRate / 3d);
		double stopMin = -stopMax;
		
		// For each iteration
		for (int j = 0; j < iterationCount; j++) {
			
			// Reset variables to store changes
			Arrays.fill(weightChanges, 0d);
			biasChange = 0d;

			// for each instance
			for (int k = 0; k < instances.size(); k++) {
				double[] instanceKValues = instances.get(k).values;
				double targetClass = targetOutputs.get(k) == true ? 1d : 0d;
				double predictedClass = weightedSum(instanceKValues);

				// for each feature
				for (int i = 0; i < featureCount; i++) {
					// calculate update to weight
					weightChanges[i] -= (predictedClass - targetClass) * instanceKValues[i];
				}

				// calculate update to bias
				biasChange -=  (predictedClass - targetClass);
			}

			// For each feature
			for (int i = 0; i < featureCount; i++) {
				// update the weight (divided by instance count because it is an average)
				weights[i] += learningRateDivided * weightChanges[i];
			}
			// Update the bias (divided by instance count because it is an average)
			bias += learningRateDivided * biasChange;

			// Record the number of iteration
			totalNumberIterations++;

			// If the bias change is very small, we stop the iterations
			if (biasChange < stopMax && (biasChange > stopMin)) {
				break;
			}
		}

		// Record statistics about memory usage and time
		MemoryLogger.getInstance().checkMemory();
		totalTime = System.currentTimeMillis() - totalTime;
		totalMemory = MemoryLogger.getInstance().getMaxMemory();
	}

	/**
	 * Print statistics about the algorithm execution to System.out.
	 */
	public void printStats() {
		System.out.println("=============  BinaryLogisticRegression v.2.53 - STATS =============");
		System.out.println(" Stopped at " + totalNumberIterations + " iterations.");
		System.out.println(" Total time ~ " + totalTime + " ms");
		System.out.println(" Maximum memory usage : " + totalMemory + " mb");
		System.out.println("===================================================");
	}
}