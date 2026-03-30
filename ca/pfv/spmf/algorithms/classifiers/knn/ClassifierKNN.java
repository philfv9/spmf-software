package ca.pfv.spmf.algorithms.classifiers.knn;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/* This file is copyright (c) 2008-2021 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* 
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/
import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.Classifier;

/**
 * A classifier of type KNN (k-nearest neighbors)
 * 
 * @author Philippe Fournier-Viger, 2021
 * @see AlgoKNN
 */
public class ClassifierKNN extends Classifier implements Serializable {

	/**
	 * UID
	 */
	private static final long serialVersionUID = 3234449944366L;

	/** The dataset */
	private Dataset dataset;

	/** The parameter k */
	private int k;

	/**
	 * The constructor
	 * 
	 * @param dataset the dataset
	 * @param k       the parameter k
	 */
	public ClassifierKNN(Dataset dataset, int k) {
		this.dataset = dataset;
		this.k = k;
	}

	@Override
	public String getName() {
		return "KNN";
	}

	@Override
	public short predict(Instance instanceX) {
		// (1) Look at all instances and calculate the number of matches with the
		// instance
		// to be predicted
		// Put each instance with its number of matches in an array
		InstanceMatch[] arrayInstance = new InstanceMatch[dataset.getInstances().size()];

		// For each instance.
		for (int i = 0; i < dataset.getInstances().size(); i++) {
			Instance instanceY = dataset.getInstances().get(i);

			// Calculate the number of matches
			int sameItemCount = findNumberCommonItems(instanceX, instanceY);
//			System.out.println("X: " + instanceX);
//			System.out.println("Y: " + instanceY);
//			System.out.println("SAME: " + sameItemCount);

			// Add the instance to the array with the number of matches
			arrayInstance[i] = new InstanceMatch(instanceY, sameItemCount);
		}

		// Sort the array by the number of matches so
		// that the instances with the highest number of matches will be first.
		Arrays.sort(arrayInstance);

		// Calculate the frequency of each class for the k first instances of the array.
		// Note that we will look at more than k if some instances have
		// the same number of matches.
		// Create a map to count the number of vote for each class value
		// (key = class value, Value = number)
		Map<Short, Long> mapItemCount = new HashMap<Short, Long>();
		// To remember the number of matches of the previous instance that we have read
		int previousMatches = Integer.MAX_VALUE;
		// The current position
		int i = 0;
		// Loop
		while (true) {
//			System.out.println(" i=" + i + "  " +arrayInstance[i].nbMatches);
			// Get the class value
			Short classValue = arrayInstance[i].instance.getKlass();
			Long count = mapItemCount.getOrDefault(classValue, 0L);
			// Increase its count
			mapItemCount.put(classValue, count + 1);

			// move to next item
			i++;

			if (i == arrayInstance.length) {
				break;
			}

			// If we have checked k instances already
			if (i >= k) {
				// and the number of matches for the current instance is less than the previous
				// instance, then we can stop
				if (arrayInstance[i].nbMatches < previousMatches) {
//					System.out.println(" STOP i=" + i + "  " +arrayInstance[i].nbMatches);
					break;
				}
			}
			previousMatches = arrayInstance[i].nbMatches;
		}

		// We find the most frequent classes and return it
		long highestCount = 0;
		Short highestName = null;
		for (Entry<Short, Long> entry : mapItemCount.entrySet()) {
			// if the frequency is higher
			if (entry.getValue() > highestCount) {
				highestCount = entry.getValue();
				highestName = entry.getKey();
			}
		}

		return highestName;
	}

	/**
	 * Inner class containing an instance and the number of matches with the
	 * instance to be predicted
	 */
	private class InstanceMatch implements Comparable<InstanceMatch> {
		/** the instance */
		Instance instance;
		/** the number of matches */
		int nbMatches;

		/**
		 * Constructor
		 * 
		 * @param instance  an instance
		 * @param nbMatches the number of matches
		 */
		InstanceMatch(Instance instance, int nbMatches) {
			this.instance = instance;
			this.nbMatches = nbMatches;
		}

		@Override
		public int compareTo(InstanceMatch other) {
			return other.nbMatches - this.nbMatches;
		}
	}

	/**
	 * Find the number of common items (attribute values) between two instances
	 * 
	 * @param instanceX the first instance (which has N attribute values)
	 * @param instanceY the second instance (which has N+1 attribute values because
	 *                  the class value is known)
	 * @return the number of common items
	 */
	private int findNumberCommonItems(Instance instanceX, Instance instanceY) {
		int count = 0;

		Short[] itemsX = instanceX.getItems();
		Short[] itemsY = instanceY.getItems();

		// Compare the values of each attribute
		for (int i = 0; i < itemsX.length; i++) {
			// If they are the same for both instances, increase the count
			if (itemsX[i].equals(itemsY[i])) {
				count++;
			}
		}
		// Return the count
		return count;
	}

}
