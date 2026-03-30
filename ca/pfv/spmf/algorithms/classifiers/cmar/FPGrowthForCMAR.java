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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.Rule;

/**
 * This is a modified version of FPGrowth used to mine class association rules.
 * It is used by the CMAR algorithm. This version of FPGrowth is designed to
 * find class association rules without keeping the frequent itemsets. Rules
 * that are discovered must have a support and confidence that is no less than
 * some minimum support and minimum confidence thresholds, respectively.
 * 
 * This implementation includes code of the normal FP-Growth implementation of
 * SPMF.
 * 
 * @see AlgoCMAR
 */
public class FPGrowthForCMAR {

	/**
	 * Dataset used to generate rules
	 */
	protected Dataset dataset;

	/**
	 * Minimum support treshold (as a number of instances (records))
	 */
	private long minSupportRelative;

	/**
	 * Minimum confidence threshold
	 */
	protected double minConf;

	/**
	 * Maximum size for the antecedent
	 */
	final int MAX_SIZE_ANTECEDENT = 2000;

	/**
	 * Buffer for storing nodes of a single tree path
	 */
	private FPNode[] fpNodeSingleBuffer = null;

	/**
	 * Map storing the support of single attribute values (key: item, value:
	 * support)
	 */
	Map<Short, Long> mapSupport;

	/**
	 * Map indicating the support of each item for each class value (Key: item,
	 * Value: (Key: class value, Value: support))
	 */
	Map<Short, Map<Short, Long>> mapSupportByKlass;

	/**
	 * The rules that are found by executing the algorithm
	 */
	protected List<Rule> rules;

	/**
	 * Constructor
	 * 
	 * @param dataset training dataset, used to generate rules
	 * @param minSup  minimum support threshold
	 * @param minConf minimum confidence threshold
	 */
	public FPGrowthForCMAR(Dataset dataset, double minSup, double minConf) {
		// Calculate the minimum support as a number of transactions
		this.minSupportRelative = (long) Math.ceil(minSup * dataset.getInstances().size());
		// Save the other parameters
		this.dataset = dataset;
		this.minConf = minConf;

	}

	/**
	 * Run the algorithm to generate class association rules from the training
	 * dataset
	 * 
	 * @return A list of class association rules
	 */
	public List<Rule> run() {

		// Find the support of single items (attribute values)
		calculateSingletons();

		// Initialize the list to store class association rules
		rules = new ArrayList<Rule>();

		// Create the initial FP-tree
		FPTree tree = new FPTree();

		// For each instance (record)
		for (Instance currentInstance : dataset.getInstances()) {

			// Create a list to store a revised version of this instance
			// that will contain only frequent items (attribute values)
			List<Short> revisedInstance = new ArrayList<Short>();

			// Get the class value of the current instance.
			short klass = currentInstance.getKlass();

			// For each item (attribute value) of the current instance
			for (int j = 0; j < dataset.getAttributes().size(); j++) {
				Short item = currentInstance.getItems()[j];

				// If the support is more than the minimum support threshold,
				// add this value to the revised instance
				if (mapSupport.get(item) >= minSupportRelative) {
					revisedInstance.add(item);
				}
			}

			// sort item in the revised instance by descending order of support
			Collections.sort(revisedInstance, new Comparator<Short>() {
				public int compare(Short item1, Short item2) {
					int compare = mapSupport.get(item2).compareTo(mapSupport.get(item1));

					if (compare == 0) {
						// When support is equal, lexical order is used
						return (item1 - item2);
					}
					return compare;
				}
			});

			// Insert the revised instance into the initial FP-Tree
			tree.addInstance(revisedInstance, klass);
		}

		// Create the header table of the initial FP-tree
		tree.createHeaderList(mapSupport);

		// If the tree contains at least some frequent items
		if (tree.headerList.size() > 0) {

			// Two buffer are initialized
			short[] antecedentBuffer = new short[MAX_SIZE_ANTECEDENT];
			fpNodeSingleBuffer = new FPNode[MAX_SIZE_ANTECEDENT];

			// Then, start to recursively mine rules in the FP-tree
			fpgrowth(tree, antecedentBuffer, 0, dataset.getInstances().size(), dataset.getMapClassToFrequency(),
					mapSupport, mapSupportByKlass);
		}

		// Return the class association rules that have been found
		return rules;
	}

	/**
	 * Recursively mine a FP-tree to find class associtino rules
	 * 
	 * @param tree                 the FP-Tree being mined
	 * @param prefix               for current prefix
	 * @param prefixLength         the length of the current prefix
	 * @param prefixSupport        support of the current prefix
	 * @param prefixSupportByKlass support by klass for the current prefix
	 * @param mapSupport           minimum support for current prefix
	 * @param mapSupportByKlass    support class for current prefix
	 */
	@SuppressWarnings("unchecked")
	private void fpgrowth(FPTree tree, short[] prefix, int prefixLength, long prefixSupport,
			Map<Short, Long> prefixSupportByKlass, Map<Short, Long> mapSupport,
			Map<Short, Map<Short, Long>> mapSupportByKlass) {
		// Check if the maximum size has been achieved
		if (prefixLength == MAX_SIZE_ANTECEDENT) {
			return;
		}

		// Variable used to check if the current tree has a single path
		boolean singlePath = true;

		// The number of single items
		int numberSingleItems = 0;

		// if the root has more than one child, it is not a single path
		if (tree.root.childs.size() > 1) {
			singlePath = false;
		} else {
			// Otherwise, if the root has exactly one child, the algorithm will recursively
			// check
			// childs of the child to see if they also have one child
			FPNode currentNode = tree.root.childs.get(0);
			while (true) {
				// if the current child has more than one child, it isn't a single path!
				if (currentNode.childs.size() > 1) {
					singlePath = false;
					break;
				}
				// The buffer will be used to store nodes in the single path
				fpNodeSingleBuffer[numberSingleItems] = currentNode;

				// Increase the number of single items
				numberSingleItems++;

				// If this node has no child, it means that this is the end of this path
				// and it is a single path
				if (currentNode.childs.size() == 0) {
					break;
				}
				currentNode = currentNode.childs.get(0);
			}
		}

		// If it is a single path
		if (singlePath) {
			// It will be processed as a single path
			saveAllCombinationsOfPrefixPath(fpNodeSingleBuffer, numberSingleItems, prefix, prefixLength);
		} else {
			// Otherwise, process each frequent item in the header table list, in reverse
			// order.
			for (int i = tree.headerList.size() - 1; i >= 0; i--) {
				// Get the item and its support
				Short item = tree.headerList.get(i);
				Long support = mapSupport.get(item);

				// Create Beta by concatening prefix by adding the current item
				prefix[prefixLength] = item;

				// Calculate the support of the new prefix
				long betaSupport = (prefixSupport < support) ? prefixSupport : support;
				Map<Short, Long> supportByKlass = mapSupportByKlass.get(item);

				// save beta to the output file
				generateRules(prefix, prefixLength + 1, betaSupport, supportByKlass);

				// If the maximum rule antecedent size has not been reached
				if (prefixLength + 1 < MAX_SIZE_ANTECEDENT) {
					// It is a subdataset containing a set of prefix paths in the FP-tree
					// co-occurring with the prefix pattern.
					List<List<FPNode>> prefixPaths = new ArrayList<List<FPNode>>();
					FPNode path = tree.mapItemNodes.get(item);

					// Create a map to count the support of items in the conditional prefix tree
					Map<Short, Long> mapSupportBeta = new HashMap<Short, Long>();
					// and also a map to count the support of an attribute value for each class
					// value.
					Map<Short, Map<Short, Long>> mapSupportByKlassBeta = new HashMap<Short, Map<Short, Long>>();

					// Loop over the nodes in the path
					while (path != null) {
						// if the path is not just the root node
						if (path.parent.item != -1) {
							List<FPNode> prefixPath = new ArrayList<FPNode>();
							prefixPath.add(path);
							long pathCount = path.support;

							// Recursively add all the parents of node
							FPNode parent = path.parent;
							while (parent.item != -1) {
								// Add the parent to the prefix path
								prefixPath.add(parent);

								// Update the support of each item
								if (mapSupportBeta.get(parent.item) == null) {
									mapSupportBeta.put(parent.item, pathCount);
								} else {
									mapSupportBeta.put(parent.item, mapSupportBeta.get(parent.item) + pathCount);
								}

								// Update also the support for the class values
								if (mapSupportByKlassBeta.get(parent.item) == null) {
									mapSupportByKlassBeta.put(parent.item,
											(HashMap<Short, Long>) path.supportByklass.clone());
								} else {
									Map<Short, Long> currentByKlass = mapSupportByKlassBeta.get(parent.item);
									for (Entry<Short, Long> entry : path.supportByklass.entrySet()) {
										Long count = currentByKlass.get(entry.getKey());

										if (count == null) {
											currentByKlass.put(entry.getKey(), entry.getValue());
										} else {
											currentByKlass.put(entry.getKey(), count + entry.getValue());
										}
									}
								}

								parent = parent.parent;
							}
							// Keep that prefix path
							prefixPaths.add(prefixPath);
						}
						// Move to the next node
						path = path.nextNode;
					}

					// Construct beta's conditional FP-Tree
					FPTree treeBeta = new FPTree();
					// Add all prefix paths to the tree
					for (List<FPNode> prefixPath : prefixPaths) {
						treeBeta.addPrefixPath(prefixPath, mapSupportBeta, minSupportRelative);
					}

					// If the root is not empty in the new FP-Tree beta
					if (treeBeta.root.childs.size() > 0) {
						// Create the header list of that fp-tree
						treeBeta.createHeaderList(mapSupportBeta);

						// Recursively mine that fp-tree
						fpgrowth(treeBeta, prefix, prefixLength + 1, betaSupport, supportByKlass, mapSupportBeta,
								mapSupportByKlassBeta);
					}
				}
			}
		}

	}

	/**
	 * Saves all the rules for current prefix with enough support
	 * 
	 * @param fpNodeTempBuffer current tree
	 * @param position         position in the current tree
	 * @param prefix           prefix itemset
	 * @param prefixLength     length of current prefix
	 */
	private void saveAllCombinationsOfPrefixPath(FPNode[] fpNodeTempBuffer, int position, short[] prefix,
			int prefixLength) {

		// Create a variable to count the overall support
		long support = 0;
		// Create a map to count the support for each class value
		Map<Short, Long> supportByKlass = null;

		// Generates all subsets of the current prefixPath except the empty set.
		// For each itemset that can be formed using this prefix path:
		loop1: for (long i = 1, max = 1 << position; i < max; i++) {
			int newPrefixLength = prefixLength;

			// Create the antecedent
			for (int j = 0; j < position; j++) {
				int isSet = (int) i & (1 << j);

				// if yes, add the bit position as an item to the new subset
				if (isSet > 0) {
					if (newPrefixLength == MAX_SIZE_ANTECEDENT) {
						continue loop1;
					}

					prefix[newPrefixLength++] = fpNodeTempBuffer[j].item;
					support = fpNodeTempBuffer[j].support;
					supportByKlass = fpNodeTempBuffer[j].supportByklass;
				}
			}

			// Then, generate rules using the current antecedent
			generateRules(prefix, newPrefixLength, support, supportByKlass);
		}
	}

	/**
	 * Scans the training dataset to calculate the support of single items (called
	 * singletons)
	 */
	private void calculateSingletons() {
		// Initialize the maps to count the supports of attribute values and class
		// values
		mapSupport = new HashMap<Short, Long>();
		mapSupportByKlass = new HashMap<Short, Map<Short, Long>>();

		// For each instance (record)
		List<Instance> instances = dataset.getInstances();
		for (Instance instance : instances) {
			// Get the class value of this instance
			Short klass = instance.getKlass();

			// For each attribute value (item)
			for (int j = 0; j < dataset.getAttributes().size(); j++) {
				Short item = instance.getItems()[j];

				// Get the current support count in the map
				Long count = mapSupport.getOrDefault(item, 0L);

				// and increase it by one
				mapSupport.put(item, ++count);

				// Get the map to store the support of the current class
				// for this item
				Map<Short, Long> byKlass = mapSupportByKlass.get(item);

				// If that class was not seen before for this item
				if (byKlass == null) {
					// Save the support of that class for this item to 1
					mapSupportByKlass.put(item, new HashMap<Short, Long>());
					mapSupportByKlass.get(item).put(klass, 1L);
				} else {
					// Otherwise, increase the value by 1
					Long counter = byKlass.getOrDefault(klass, 0L);
					byKlass.put(klass, counter + 1);
				}
			}
		}
	}

	/**
	 * Generate rules from an antecedent
	 * 
	 * @param antecedent       a rule antecedent
	 * @param antecedentLength number of items in the rule antecedent
	 * @param support          support of the rule
	 * @param counterByKlass   support for each class
	 */
	protected void generateRules(short[] antecedent, int antecedentLength, long support,
			Map<Short, Long> counterByKlass) {
		// Copy the antecedent into a buffer
		short[] itemsetOutputBuffer = new short[antecedentLength];
		System.arraycopy(antecedent, 0, itemsetOutputBuffer, 0, antecedentLength);
		Arrays.sort(itemsetOutputBuffer, 0, antecedentLength);

		// For each class value
		for (Entry<Short, Long> entry : counterByKlass.entrySet()) {
			// Create a rule by combining it with the antecedent
			RuleCMAR rule = new RuleCMAR(itemsetOutputBuffer, entry.getKey());
			rule.setSupportAntecedent(support);
			rule.setSupportRule(entry.getValue());
			rule.setSupportKlass(dataset.getMapClassToFrequency().get(rule.getKlass()));

			// If the rule is frequent and has a high confidence
			if (rule.getSupportRule() >= this.minSupportRelative && rule.getConfidence() >= this.minConf)
				// Save the rule
				rules.add(rule);
		}
	}
}
