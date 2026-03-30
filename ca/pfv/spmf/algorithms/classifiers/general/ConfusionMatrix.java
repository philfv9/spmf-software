/**
 * This file is part of Library for Associative Classification (LAC)
 *
 * Copyright (C) 2019
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
package ca.pfv.spmf.algorithms.classifiers.general;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Confusion matrix used to quality metrics for classifiers. <br/><br/>
 * 
 * The code was obtained from the LAC library under GPL license. Then, it was optimized 
 * and adapted for integration in SPMF.
 */
public class ConfusionMatrix {
	/**
	 * Total number of cases
	 */
	long total = 0;

	/**
	 * Correct number of cases
	 */
	long correct = 0;

	/** number of no predictions */
	long nopredictions = 0;

	/**
	 * Map used to represent the final confusion matrix
	 */
	Map<Short, Map<Short, Long>> matrix;

	/**
	 * All the real classes contained in the dataset
	 */
	Set<Short> allRealklasss = new TreeSet<Short>();

	/**
	 * All the predictions done by the classifier
	 */
	Set<Short> allPredictedklasss = new TreeSet<Short>();

	/**
	 * Constructor
	 */
	public ConfusionMatrix() {
		this.matrix = new TreeMap<Short, Map<Short, Long>>();
	}

	/**
	 * Get average recall of the classifier
	 * 
	 * @return the average recall of the classifier
	 */
	public double getAverageRecall() {
		double result = 0.0;
		// PHILIPPE: Simplified the code below to creating an unecessary map and copying its content
		for (Short klass : this.allRealklasss) {
			result += getRecallForKlass(klass);
		}   
		return result / (double) allRealklasss.size();
	}

	/**
	 * Get the average precision for all the classes
	 * 
	 * @return the average precision for classes
	 */
	public double getAveragePrecision() {
		double result = 0.0;
		
		// PHILIPPE: Simplified the code below to creating an unecessary map and copying its content
		for (Short klass : this.allRealklasss) {
			result += getPrecisionForKlass(klass);
		}
		return result / (double) allRealklasss.size();
	}

	/**
	 * Get the Micro F-measure
	 *
	 * @return the micro f-measure for all the classes
	 */
	public double getMicroFMeasure() {
		long allTruePositives = 0;
		long allTruePositivesAndFalsePositives = 0;
		long allTruePositivesAndFalseNegatives = 0;

		for (Entry<Short, Map<Short, Long>> x : this.matrix.entrySet()) {
			Short klass = x.getKey();
			// PHILIPPE: I have optimized the code below as there was
			// unecessary calls to search maps
			Map<Short, Long> map = this.matrix.get(klass);
			if (map != null) {
				Long value = map.get(klass);
				if (value != null) {
					allTruePositives += value;
				}
			}
			allTruePositivesAndFalsePositives += this.getColSum(klass);
			allTruePositivesAndFalseNegatives += this.getRowSum(klass);
		}

		double precision = allTruePositives / (double) allTruePositivesAndFalsePositives;
		double recall = allTruePositives / (double) allTruePositivesAndFalseNegatives;

		return (2.0 * precision * recall) / (precision + recall);
	}

	/**
	 * Get the Macro F-measure
	 *
	 * @return the macro f-measure for all the classes
	 */
	public double getMacroFMeasure() {
		// PHILIPPE: Refactored and simplified the code of that method to avoid creating three unecessary LinkedHashMaps
		// and copying the whole content of three maps...
		
		double totalFMeasure = 0;
		
		// for each class
		for (Short klass : this.allRealklasss) {
			double p = this.getPrecisionForKlass(klass);
			double r = this.getRecallForKlass(klass);
		
			double fm = 0.0;
		
			if ((p + r) > 0) {
				fm = (2.0 * p * r) / (p + r);
			}
			totalFMeasure += fm;
		}
		return totalFMeasure / allRealklasss.size();
	}

	/**
	 * Return recall for single klass
	 *
	 * @param klass klass
	 * @return double
	 */
	private double getRecallForKlass(Short klass) {
		long fnTp = 0;
		double recall = 0;
		long tp = 0;

		// PHILIPPE : optimized the code. Maps were accessed several times while it was
		// unecessary
		Map<Short, Long> map = matrix.get(klass);
		if (map != null) {
			Long value = map.get(klass);
			if (value != null) {
				tp = value;
				fnTp = this.getRowSum(klass);
			}
		}

		if (fnTp > 0) {
			recall = (double) tp / (double) (fnTp);
		}
		return recall;
	}

	/**
	 * Add both prediction and real value to the confusion matrix
	 * 
	 * @param realValue
	 * @param observedValue
	 */
	public void add(Short realValue, Short observedValue) {
		// ===============================================
		// PHILIPPE: I have optimized the code below... The maps was accessed numerous
		// times while it was unnecessary.
		// ======================================
		allRealklasss.add(realValue);
		allPredictedklasss.add(observedValue);

		Map<Short, Long> map = matrix.get(realValue);
		if (map == null) {
			map = new TreeMap<Short, Long>();
			matrix.put(realValue, map);
		}

		Long value = map.get(observedValue);
		if (value == null) {
			map.put(observedValue, 1l);
		} else {
			map.put(observedValue, value + 1);
		}

		this.total += 1;
		if (realValue.equals(Classifier.NOPREDICTION)) {
			nopredictions += 1;
		}

		if (realValue.equals(observedValue)) {
			this.correct += 1;
		}
	}

	/**
	 * Get the accuracy for the classifier
	 * 
	 * @return the accuracy
	 */
	public double getAccuracy() {
		return this.correct / (double) this.total;
	}

	/**
	 * Get the value for the metric Cohen's Kappa
	 *
	 * @return double the value for kappa measure
	 */
	public double getKappa() {
		double p0 = this.getAccuracy();

		double pe = 0;
		for (Short klass : this.allRealklasss) {
			double rowSum = this.getRowSum(klass);
			double colSum = this.getColSum(klass);

			pe += (rowSum * colSum) / this.total;
		}

		pe /= this.total;

		return (p0 - pe) / (1.0 - pe);
	}

	/**
	 * Get the precision for one concrete class
	 * 
	 * @param klass to get the precision
	 * @return precision for the specified class
	 */
	private double getPrecisionForKlass(Short klass) {
		double precision = 0;
		long tp = 0;
		long fpTp = 0;

		// PHILIPPE : optimized the code below to remove unecessary calls to search the
		// maps with containsKey
		Map<Short, Long> map = matrix.get(klass);
		if (map != null) {
			Long value = matrix.get(klass).get(klass);
			if (value != null) {
				tp = value;
				fpTp = this.getColSum(klass);
			}
		}

		if (fpTp > 0) {
			precision = (double) tp / (double) (fpTp);
		}

		return precision;
	}

	/**
	 * Get the sum for the column of the specifid class
	 * 
	 * @param klass column to be sum
	 * @return the sum for the whole column
	 */
	private long getColSum(Short klass) {
		long result = 0;
		// PHILIPPE: Optimized
		for (Entry<Short, Map<Short, Long>> entry : this.matrix.entrySet()) {
			Map<Short, Long> row = entry.getValue();
			// optimized
			Long val = row.get(klass);
			if (val != null) {
				result += val;
			}
		}
		return result;
	}

	/**
	 * Get the sum for the row of the specifid class
	 * 
	 * @param klass row to be sum
	 * @return the sum for the whole row
	 */
	private long getRowSum(Short klass) {
		long result = 0;

		// Philippe : optimized
		for (Entry<Short, Long> entry : matrix.get(klass).entrySet()) {
			result += entry.getValue();
		}
		return result;
	}

	/**
	 * Get the percentage of no predictions
	 * 
	 * @return the number
	 */
	protected float getNopredictions() {
		return nopredictions / ((float) total);
	}

}