package ca.pfv.spmf.algorithms.classifiers.data;
/* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* @copyright SPMF 2021
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
import java.util.ArrayList;

/**
 * This is a view on a real dataset. It is used for sampling experiments where
 * we only use a subsets of instances from a dataset instead of all for training
 * and testing.
 * 
 * @author Philippe Fournier-Viger, 2021
 *
 */
public class VirtualDataset extends Dataset {

	/**
	 * Constructor
	 * 
	 * @param dataset   the database to use for creating the view
	 * @param i         the first record to be used for testing
	 * @param j         the last record to be used for testing
	 * @param addedName a string to be used as name for this view (e.g. training or
	 *                  testing)
	 */
	VirtualDataset(Dataset dataset, int i, int j, String addedName) {
		// Split the records
		this.instances = dataset.instances.subList(i, j);

		// Recompute the class frequencies (this is important!!)
		recalculateClassFrequencies();
//		System.out.println(mapClassToFrequency);

		// Other things, we just copy
		this.attributes = dataset.attributes;
		this.targetClassValues = dataset.targetClassValues;
		this.mapItemToString = dataset.mapItemToString;
		this.listAttributeIndexToItems = dataset.listAttributeIndexToItems;
		this.lastGivenID = dataset.lastGivenID;
		this.indexKlass = dataset.indexKlass;
		this.hasMissingValue = dataset.hasMissingValue;

		// Update the name
		this.name = dataset.getName() + addedName;
	}

	/**
	 * Constructor
	 * 
	 * @param dataset   a dataset to be used to create this view
	 * @param isTesting if true, this is a view for testing a classifier
	 * @param posStart  the first record to use for this view
	 * @param posEnd    the last record to use
	 * @param addedName a string to be used as name for this view (e.g. training or
	 *                  testing)
	 */
	public VirtualDataset(Dataset dataset, boolean isTesting, int posStart, int posEnd, String addedName) {
		// Get the records
		int recordCount = dataset.getInstances().size();
		int testingSize = posEnd - posStart;
		int thisSize = (isTesting) ? testingSize : (recordCount - testingSize);
		this.instances = new ArrayList<Instance>(thisSize);

//		System.out.println("PosStart " + posStart);
//		System.out.println("PosEnd " + posEnd);
		for (int j = 0; j < dataset.getInstances().size(); j++) {
			Instance instanceJ = dataset.getInstances().get(j);

			// is in testing set
			if (j >= posStart && j < posEnd) {
				if (isTesting) {
//					System.out.println("TESTING + " + j);
					this.instances.add(instanceJ);
				}
			} else {
				if (!isTesting) {
//					System.out.println("TRAINING + " + j);
					this.instances.add(instanceJ);
				}
			}
		}

		// Recompute the class frequencies (this is important!!)
		recalculateClassFrequencies();

		// Other things, we just copy
		this.attributes = dataset.attributes;
		this.targetClassValues = dataset.targetClassValues;
		this.mapItemToString = dataset.mapItemToString;
		this.listAttributeIndexToItems = dataset.listAttributeIndexToItems;
		this.lastGivenID = dataset.lastGivenID;
		this.indexKlass = dataset.indexKlass;
		this.hasMissingValue = dataset.hasMissingValue;

		// Update the name
		this.name = dataset.getName() + addedName;

	}

	/**
	 * Split this dataset into two datasets based on a percentage (used by holdout
	 * sampling)
	 * 
	 * @param percentage a percentage [0.0-1.0]
	 * @return an array of two datasets that are a partition of the original dataset
	 */
	public static Dataset[] splitDatasetForHoldout(Dataset dataset, double percentage) {
		int recordCount = dataset.getInstances().size();
		int recordCountFirstPart = (int) (recordCount * percentage);
		Dataset dataset1 = new VirtualDataset(dataset, 0, recordCountFirstPart, "training");
		Dataset dataset2 = new VirtualDataset(dataset, recordCountFirstPart, recordCount, "testing");
		return new Dataset[] { dataset1, dataset2 };
	}

	/**
	 * Split the dataset into two parts: a subset of record from a position i to j,
	 * and all other records.
	 * 
	 * @param dataset
	 * @param i       the first position
	 * @param j       the last position
	 * @return an array of two datasets that are a partition of the original dataset
	 */
	public static Dataset[] splitDatasetForKFold(Dataset dataset, int i, int j) {
		Dataset dataset1 = new VirtualDataset(dataset, false, i, j, "training");
		Dataset dataset2 = new VirtualDataset(dataset, true, i, j, "testing");
		return new Dataset[] { dataset1, dataset2 };
	}

}
