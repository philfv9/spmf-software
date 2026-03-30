package ca.pfv.spmf.tools.dataset_stats;
/* This file is copyright (c) 2008-2012 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
import java.util.List;

/**
 * Basis stats functions for calculating the average, minimum, maximum of list of integers, list of doubles, etc.
 * @author Philippe Fournier-Viger
 */
public class BasicStatsFunctions {

	/**
	 * This method calculate the mean of a list of integers
	 * 
	 * @param list the list of integers
	 * @return the mean
	 */
	static double calculateMean(List<Integer> list) {
		double sum = 0;
		for (Integer val : list) {
			sum += val;
		}
		return sum / list.size();
	}

	/**
	 * This method calculate the mean of a list of doubles
	 * 
	 * @param list the list of doubles
	 * @return the mean
	 */
	static double calculateMeanDouble(List<Double> list) {
		double sum = 0;
		for (Double val : list) {
			sum += val;
		}
		return sum / list.size();
	}

	/**
	 * This method calculate the standard deviation of a list of integers
	 * 
	 * @param list the list of integers
	 * @return the standard deviation
	 */
	static double calculateStdDeviation(List<Integer> list) {
		double deviation = 0;
		double mean = calculateMean(list);
		for (Integer val : list) {
			deviation += Math.pow(mean - val, 2);
		}
		return Math.sqrt(deviation / list.size());
	}

	/**
	 * This method calculate the standard deviation of a list of doubles
	 * 
	 * @param list the list of doubles
	 * @return the standard deviation
	 */
	static double calculateStdDeviationDouble(List<Double> list) {
		double deviation = 0;
		double mean = calculateMeanDouble(list);
		for (Double val : list) {
			deviation += Math.pow(mean - val, 2);
		}
		return Math.sqrt(deviation / list.size());
	}

	/**
	 * This method calculate the variance of a list of integers
	 * 
	 * @param list the list of integers
	 * @return the variance
	 */
	static double calculateVariance(List<Integer> list) {
		double deviation = 0;
		double mean = calculateMean(list);
		for (Integer val : list) {
			deviation += Math.pow(mean - val, 2);
		}
		return Math.pow(Math.sqrt(deviation / list.size()), 2);
	}
	
	/**
	 * This method calculate the variance of a list of doubles
	 * 
	 * @param list the list of doubles
	 * @return the variance
	 */
	static double calculateVarianceDouble(List<Double> list) {
		double deviation = 0;
		double mean = calculateMeanDouble(list);
		for (Double val : list) {
			deviation += Math.pow(mean - val, 2);
		}
		return Math.pow(Math.sqrt(deviation / list.size()), 2);
	}

	/**
	 * This method return the smallest integer from a list of integers
	 * 
	 * @param list the list of integers
	 * @return the smallest integer
	 */
	static int calculateMinValue(List<Integer> list) {
		int min = Integer.MAX_VALUE;
		for (Integer val : list) {
			if (val < min) {
				min = val;
			}
		}
		return min;
	}
	
	/**
	 * This method return the smallest integer from a list of doubles
	 * 
	 * @param list the list of doubles
	 * @return the smallest double
	 */
	static double calculateMinValueDouble(List<Double> list) {
		double min = Double.MAX_VALUE;
		for (Double val : list) {
			if (val < min) {
				min = val;
			}
		}
		return min;
	}

	/**
	 * This method return the largest integer from a list of integers
	 * 
	 * @param list the list of integers
	 * @return the largest integer
	 */
	static int calculateMaxValue(List<Integer> list) {
		int max = 0;
		for (Integer val : list) {
			if (val >= max) {
				max = val;
			}
		}
		return max;
	}
	
	/**
	 * This method return the largest integer from a list of doubles
	 * 
	 * @param list the list of doubles
	 * @return the largest double
	 */
	static double calculateMaxValueDouble(List<Double> list) {
		double max = 0;
		for (Double val : list) {
			if (val >= max) {
				max = val;
			}
		}
		return max;
	}
}
