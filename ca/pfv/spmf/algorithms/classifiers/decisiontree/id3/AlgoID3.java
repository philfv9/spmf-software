package ca.pfv.spmf.algorithms.classifiers.decisiontree.id3;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.ClassificationAlgorithm;
import ca.pfv.spmf.algorithms.classifiers.general.Classifier;

/**
 * This is an implementation of the ID3 algorithm for creating a decision tree.
 * <br/>
 * <br/>
 * ID3 is a very popular algorithms described in many artificial intelligence
 * and data mining textbooks.
 * 
 * @author Philippe Fournier-Viger
 */
public class AlgoID3 extends ClassificationAlgorithm {

	/** the list of values for the target attribute */
	private List<Short> targetAttributeValues;

	/** start time of the latest execution */
	private long startTime;

	/** end time of the latest execution */
	private long endTime;

	/**
	 * Method to create a subtree according to a set of attributes and training
	 * instances.
	 * 
	 * @param remainingAttributes remaining attributes to create the tree
	 * @param list                a list of training instances
	 * @return node of the subtree created
	 */
	private Node id3(int[] remainingAttributes, List<Instance> list, Map<Short, Long> mapClassToSupport) {

		// Calculate the frequency of each target attribute value
		Map<Short, Long> targetValuesFrequency;
		if (mapClassToSupport == null) {
			targetValuesFrequency = calculateFrequencyOfClassValues(list);
		} else {
			targetValuesFrequency = mapClassToSupport;
		}

		// if only one remaining attribute,
		// return a class node with the most common value in the instances
		if (remainingAttributes.length == 0) {

			// Loop over the values to find the class with the highest frequency
			long highestCount = 0;
			Short highestName = null;
			for (Entry<Short, Long> entry : targetValuesFrequency.entrySet()) {
				// if the frequency is higher
				if (entry.getValue() > highestCount) {
					highestCount = entry.getValue();
					highestName = entry.getKey();
				}
			}
			// return a class node with the value having the highest frequency
			ClassNode classNode = new ClassNode();
			classNode.className = highestName;
			return classNode;
		}

		// if all instances are from the same class
		if (targetValuesFrequency.entrySet().size() == 1) {
			ClassNode classNode = new ClassNode();
			classNode.className = targetValuesFrequency.entrySet().iterator().next().getKey();
			return classNode;
		}

		// Calculate global entropy
		double globalEntropy = 0d;
		// for each value
		for (Short value : targetAttributeValues) {
			// calculate frequency
			Long frequencyInt = targetValuesFrequency.get(value);
			// if the frequency is not zero
			if (frequencyInt != null) {

				// calculate the frequency has a double
				double frequencyDouble = frequencyInt / (double) list.size();

				// update the global entropy
				globalEntropy -= frequencyDouble * Math.log(frequencyDouble) / Math.log(2);
			}
		}
		// System.out.println("Global entropy = " + globalEntropy);

		// Select the attribute from remaining attributes such that if we split
		// the dataset on this
		// attribute, we will get the higher information gain
		int attributeWithHighestGain = 0;
		double highestGain = -99999;
		for (int attribute : remainingAttributes) {
			double gain = calculateGain(attribute, list, globalEntropy);
			// System.out.println("Process " + allAttributes[attribute] +
			// " gain = " + gain);
			if (gain >= highestGain) {
				highestGain = gain;
				attributeWithHighestGain = attribute;
			}
		}

		// if the highest gain is 0....
		if (highestGain == 0) {
			ClassNode classNode = new ClassNode();
			// take the most frequent classes
			long topFrequency = 0;
			Short className = null;
			for (Entry<Short, Long> entry : targetValuesFrequency.entrySet()) {
				if (entry.getValue() > topFrequency) {
					topFrequency = entry.getValue();
					className = entry.getKey();
				}
			}
			classNode.className = className;
			return classNode;
		}

		// Create a decision node for the attribute
		// System.out.println("Attribute with highest gain = " +
		// allAttributes[attributeWithHighestGain] + " " + highestGain);
		DecisionNode decisionNode = new DecisionNode();
		decisionNode.attribute = attributeWithHighestGain;

		// calculate the list of remaining attribute after we remove the
		// attribute
		int[] newRemainingAttribute = new int[remainingAttributes.length - 1];
		int pos = 0;
		for (int i = 0; i < remainingAttributes.length; i++) {
			if (remainingAttributes[i] != attributeWithHighestGain) {
				newRemainingAttribute[pos++] = remainingAttributes[i];
			}
		}

		// Split the dataset into partitions according to the selected attribute
		Map<Short, List<Instance>> partitions = new HashMap<Short, List<Instance>>();
		for (Instance instance : list) {
			Short value = instance.getItems()[attributeWithHighestGain]; ////////////////////////////////////////
			List<Instance> listInstances = partitions.get(value);
			if (listInstances == null) {
				listInstances = new ArrayList<Instance>();
				partitions.put(value, listInstances);
			}
			listInstances.add(instance);
		}

		// Create the values for the subnodes
		decisionNode.nodes = new Node[partitions.size()];
		decisionNode.attributeValues = new Short[partitions.size()];

		// For each partition, make a recursive call to create
		// the corresponding branches in the tree.
		int index = 0;
		for (Entry<Short, List<Instance>> partition : partitions.entrySet()) {
			decisionNode.attributeValues[index] = partition.getKey();
			decisionNode.nodes[index] = id3(newRemainingAttribute, partition.getValue(), null); // recursive call
			index++;
		}

		// return the root node of the subtree created
		return decisionNode;
	}

	/**
	 * Calculate the information gain of an attribute for a set of instance
	 * 
	 * @param attributePos  the position of the attribute
	 * @param instances     a list of instances
	 * @param globalEntropy the global entropy
	 * @return the gain
	 */
	private double calculateGain(int attributePos, List<Instance> instances, double globalEntropy) {
		// Count the frequency of each value for the attribute
		Map<Short, Long> valuesFrequency = calculateFrequencyOfAttributeValues(instances, attributePos);

		// Calculate the gain
		double sum = 0;
		// for each value
		for (Entry<Short, Long> entry : valuesFrequency.entrySet()) {
			// make the sum
			sum += entry.getValue() / ((double) instances.size())
					* calculateEntropyIfValue(instances, attributePos, entry.getKey());
		}
		// subtract the sum from the global entropy
		return globalEntropy - sum;
	}

	/**
	 * Calculate the entropy for the target attribute, if a given attribute has a
	 * given value.
	 * 
	 * @param instances   : list of instances
	 * @param attributeIF : the given attribute
	 * @param valueIF     : the given value
	 * @return entropy
	 */
	private double calculateEntropyIfValue(List<Instance> instances, int attributeIF, Short valueIF) {

		// variable to count the number of instance having the value for that
		// attribute
		int instancesCount = 0;

		// variable to count the frequency of each value
		Map<Short, Integer> valuesFrequency = new HashMap<Short, Integer>();

		// for each instance
		for (Instance instance : instances) {
			// if that instance has the value for the attribute
			if (instance.getItems()[attributeIF].equals(valueIF)) {
				Short targetValue = instance.getKlass();
				// increase the frequency
				if (valuesFrequency.get(targetValue) == null) {
					valuesFrequency.put(targetValue, 1);
				} else {
					valuesFrequency.put(targetValue, valuesFrequency.get(targetValue) + 1);
				}
				// increase the number of instance having the value for that
				// attribute
				instancesCount++;
			}
		}
		// calculate entropy
		double entropy = 0;
		// for each value of the target attribute
		for (Short value : targetAttributeValues) {
			// get the frequency
			Integer count = valuesFrequency.get(value);
			// if the frequency is not null
			if (count != null) {
				// update entropy according to the formula
				double frequency = count / (double) instancesCount;
				entropy -= frequency * Math.log(frequency) / Math.log(2);
			}
		}
		return entropy;
	}

	/**
	 * This method calculates the frequency of each value for an attribute in a
	 * given set of instances
	 * 
	 * @param instances A set of instances
	 * @return A map where the keys are attributes and values are the number of
	 *         times that the value appeared in the set of instances.
	 */
	private Map<Short, Long> calculateFrequencyOfAttributeValues(List<Instance> instances, int attributePos) {
		// A map to calculate the frequency of each value:
		// Key: a string indicating a value
		// Value: the frequency
		Map<Short, Long> targetValuesFrequency = new HashMap<Short, Long>();

		// for each instance of the training set
		for (Instance instance : instances) {
			// get the value of the attribute for that instance
			Short targetValue = instance.getItems()[attributePos];
			// increase the frequency by 1
			if (targetValuesFrequency.get(targetValue) == null) {
				targetValuesFrequency.put(targetValue, 1L);
			} else {
				targetValuesFrequency.put(targetValue, targetValuesFrequency.get(targetValue) + 1);
			}
		}
		// return the map
		return targetValuesFrequency;
	}

	/**
	 * This method calculates the frequency of each class value for an attribute in
	 * a given set of instances
	 * 
	 * @param instances A set of instances
	 * @return A map where the keys are attributes and values are the number of
	 *         times that the value appeared in the set of instances.
	 */
	private Map<Short, Long> calculateFrequencyOfClassValues(List<Instance> instances) {
		// A map to calculate the frequency of each value:
		// Key: a string indicating a value
		// Value: the frequency
		Map<Short, Long> targetValuesFrequency = new HashMap<Short, Long>();

		// for each instance of the training set
		for (Instance instance : instances) {
			// get the value of the attribute for that instance
			Short targetValue = instance.getKlass();
			// increase the frequency by 1
			if (targetValuesFrequency.get(targetValue) == null) {
				targetValuesFrequency.put(targetValue, 1L);
			} else {
				targetValuesFrequency.put(targetValue, targetValuesFrequency.get(targetValue) + 1);
			}
		}
		// return the map
		return targetValuesFrequency;
	}

	/**
	 * Print statistics about the execution of this algorithm
	 */
	public void printStatistics() {
		System.out.println("Time to construct decision tree = " + (endTime - startTime) + " ms");
		System.out.println();
	}

	@Override
	protected Classifier train(Dataset dataset) {
		//String input, String targetAttribute,
		//String separator
		// record the start time
		startTime = System.currentTimeMillis();
		
		// create an empty decision tree
		DecisionTree tree = new DecisionTree(dataset.getMapItemToString(),
										    dataset.getAttributes());

		// (2) Start the recusive process
		
		// Copy the remaining attributes in an array
		// NOTE: Rather the representing an attribute as a String, it is just represented as a position in the
		// list of attributes (e.g. attribute 0, attribute 1.... 
		int[] remainingAttributes = new int[dataset.getAttributes().size()];
		for(int j=0; j < dataset.getAttributes().size(); j++) {
			remainingAttributes[j] = j;
		}
		
		//  Create the list of target attribute values
		targetAttributeValues = dataset.getListOfClassValues();
		
		// Make a list of all instances
//		List<Instance> instances = new ArrayList<Instance();
		
		// create the tree
		tree.root = id3(remainingAttributes, dataset.getInstances(), dataset.getMapClassToFrequency());
		
		endTime = System.currentTimeMillis();  // record end time
		
		return new ClassifierID3(tree); // return the tree
	}

	@Override
	public String getName() {
		return "ID3";
	}
}
