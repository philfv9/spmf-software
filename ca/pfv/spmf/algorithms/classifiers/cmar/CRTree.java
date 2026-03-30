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
import java.util.List;

import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.Rule;

/**
 * Class representing a CR-Tree. This is a data structure used by the CMAR
 * algorithm to search for class association rules. It is based on the concept
 * of FP-Tree used by the FP-Growth algorithm.
 * 
 * @see AlgoCMAR
 */
class CRTree {
	/**
	 * Number of singletons (attribute values) in the training dataset
	 */
	protected static int NUMBER_SINGLETONS;

	/**
	 * Root node for the current tree
	 */
	private CRNode rootNode = null;

	/**
	 * Minimum times an instance need to be covered
	 */
	private int minCover = 4;

	/**
	 * Critical threshold for 25% "significance" level (assuming "degree of freedom"
	 * equivalent to 1).
	 */
	private static final double THRESHOLD_20 = 1.6424;

	/**
	 * Critical threshold value for CHI_SQUARE
	 */
	private static final double THRESHOLD_CHI_SQUARE = THRESHOLD_20; // Default

	/**
	 * The training dataset used to create the CRTree
	 */
	public final Dataset dataset;

	/**
	 * Constructor
	 * 
	 * @param dataset dataset used to generate CRTree
	 * @param delta   the delta value
	 */
	CRTree(Dataset dataset, int delta) {
		minCover = delta;
		this.dataset = dataset;
	}

	/**
	 * Insert a rule in the tree
	 * 
	 * @param baseRule a rule to be inserted into the CRTree
	 */
	protected void insert(Rule baseRule) {

		RuleCMAR rule = (RuleCMAR) baseRule;

		// If the rule fails the Chi-Squared test
		if (rule.getChiSquare() <= THRESHOLD_CHI_SQUARE) {
			// We dont add the rule
			return;
		}

		// Otherwise, we create a new node for this rule
		CRNode newNode = new CRNode(rule);

		// If it is the first rule, it will be added as the root of the tree.
		if (rootNode == null) {
			rootNode = newNode;
			return;
		}

		// If more general rule with higher ranking exists, current rule will be
		// discarded
		if (isMoreGeneralNode(newNode)) {
			return;
		}

		// Add current node as the first node
		if (newNode.rule.isGreater(rootNode.rule)) {
			newNode.next = rootNode;
			rootNode = newNode;
			return;
		}

		// Search for the position where
		// the rule should be inserted in terms of rank
		CRNode currentNode = rootNode;
		CRNode nextNode = rootNode.next;
		while (nextNode != null) {
			if (newNode.rule.isGreater(nextNode.rule)) {
				currentNode.next = newNode;
				newNode.next = nextNode;
				return;
			}
			currentNode = nextNode;
			nextNode = nextNode.next;
		}

		// Add new node at the very end
		currentNode.next = newNode;
	}

	/**
	 * Checks whether there are a more general rule, with higher ranking in the
	 * current tree
	 * 
	 * @param ruleNode a node to be inserted
	 * @return true if more general rule exists in the tree
	 */
	private boolean isMoreGeneralNode(CRNode ruleNode) {
		
		CRNode currentNode = rootNode;

		// Search in tree by follwing the "next" links between nodes
		while (currentNode != null) {
			if (ruleNode.rule.isMoreGeneral(currentNode.rule) && ruleNode.rule.isGreater(currentNode.rule))
				return true;
			currentNode = currentNode.next;
		}

		return false;
	}

	/**
	 * Prunes this CRTree using the cover principle
	 */
	protected void pruneUsingCover() {
		// Create a two-dimensional array that will store the instances
		Short[][] datasetArray = new Short[this.dataset.getInstances().size()][];

		// Copy the i-th instance in the i-th position of the array
		List<Instance> instances = this.dataset.getInstances();
		for (int i = 0; i < instances.size(); i++) {
			datasetArray[i] = instances.get(i).getItems();
		}

		// Create an array to count how many times each instance is covered
		int[] numberTimesCovered = new int[this.dataset.getInstances().size()];

		// Define rule list references
		CRNode newStart = null;
		CRNode markerRef = null;
		CRNode currentNode = rootNode;

		// Browse through the nodes by following the "next" pointers
		while (currentNode != null) {
			// If dataset is empty, there are no need to continue pruning
			if (isEmptyDataSet(datasetArray))
				break;

			boolean coveredFlag = false;
			// For each record
			for (int m = 0; m < datasetArray.length; m++) {
				// Increment the counter for each instance being covered
				if (currentNode.rule.matching(datasetArray[m])) {
					numberTimesCovered[m]++;
					coveredFlag = true;
				}
			}

			// If current rule has covered at least one instance
			if (coveredFlag) {
				if (newStart == null)
					newStart = currentNode;
				else
					markerRef.next = currentNode;

				markerRef = currentNode;
				currentNode = currentNode.next;
				markerRef.next = null;
			} else {
				// Otherwise, go to the next node
				currentNode = currentNode.next;
			}

			// Remove instances that were already covered enough times
			for (int n = 0; n < numberTimesCovered.length; n++) {
				if (numberTimesCovered[n] > minCover)
					datasetArray[n] = null;
			}
		}

		// Update the root of the tree
		rootNode = newStart;
	}

	/**
	 * Check if a specified dataset is empty
	 * 
	 * @param dataset a dataset
	 * @return true if it is empty, or false otherwise
	 */
	private boolean isEmptyDataSet(Short[][] dataset) {
		for (Short[] instances : dataset) {
			if (instances != null)
				return false;
		}
		return true;
	}

	/**
	 * Returns the number of generated classification rules
	 * 
	 * @return array of rules
	 */
	public List<Rule> getRules() {
		// Create a list of rules
		List<Rule> rules = new ArrayList<Rule>();
		
		// Browse through the nodes by following the "next" pointers.
		CRNode currentNode = rootNode;
		while (currentNode != null) {
			// Add the rule of the current node
			rules.add(currentNode.rule);
			// Move to next node
			currentNode = currentNode.next;
		}
		
		// Return the list of rules
		return rules;
	}
}
