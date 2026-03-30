package ca.pfv.spmf.algorithms.classifiers.knn;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.ClassificationAlgorithm;
import ca.pfv.spmf.algorithms.classifiers.general.Classifier;

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
/**
 * This is an implementation of the KNN (K-nearest-neighboor) algorithm for
 * classification. <br/>
 * <br/>
 * KNN is a very popular algorithm described in many data mining textbooks.
 * 
 * @author Philippe Fournier-Viger
 */
public class AlgoKNN extends ClassificationAlgorithm {

	/** start time of the latest execution */
	private long startTime;

	/** end time of the latest execution */
	private long endTime;

	/** the parameter k */
	int k;

	/**
	 * Constructor
	 */
	public AlgoKNN(int k) {
		this.k = k;
	}

	/**
	 * Print statistics about the execution of this algorithm
	 */
	public void printStatistics() {
		System.out.println("Time to prepare the model = " + (endTime - startTime) + " ms");
		System.out.println();
	}

	@Override
	protected Classifier train(Dataset dataset) {
		// record the start time
		startTime = System.currentTimeMillis();
		// Create the classifier
		ClassifierKNN knn = new ClassifierKNN(dataset, k);
		// record end time
		endTime = System.currentTimeMillis();

		return knn; // return the classifier
	}

	@Override
	public String getName() {
		return "KNN";
	}
}
