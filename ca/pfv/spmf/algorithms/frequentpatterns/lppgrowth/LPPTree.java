package ca.pfv.spmf.algorithms.frequentpatterns.lppgrowth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * This file is part of the SPMF DATA MINING SOFTWARE *
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the *
 * terms of the GNU General Public License as published by the Free Software *
 * Foundation, either version 3 of the License, or (at your option) any later *
 * version. SPMF is distributed in the hope that it will be useful, but WITHOUT
 * ANY * WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Copyright Peng Yang  2019
 */
/**
 * This class represents an LPP tree.
 * 
 * @author Peng yang
 * @see AlgoLPPGrowth
 */
public class LPPTree {
	/** List of items in the header table */
	List<Integer> headerList = null;

	/** List of pairs (item, LPPNode) of the header table */
	Map<Integer, LPPNode> mapItemNodes = new HashMap<Integer, LPPNode>();

	/**
	 * Map that indicates the last node for each item using the node links // key:
	 * item value: an fpti tree node
	 */
	Map<Integer, LPPNode> mapItemLastNode = new HashMap<>();

	/** root of the tree */
	LPPNode root = new LPPNode(); // null node

	/**
	 * Constructor
	 */
	public LPPTree() {
		// empty
	}

	/**
	 * Method for adding a transaction and its timestamp to the LPP-tree (for the
	 * initial construction of the PFTI-Tree).
	 * 
	 * @param transaction
	 * @param timestamp
	 */
	public void addTransaction(List<Integer> transaction, int timestamp) {
		LPPNode currentNode = root;

		// For each item in the transaction
		for (Integer item : transaction) {
			// look if there is a node already in the FP-Tree
			LPPNode child = currentNode.getChildByID(item);
			if (child == null) {
				// there is no node, we create a new one
				LPPNode newNode = new LPPNode();
				newNode.itemID = item;
				newNode.parent = currentNode;
				// we link the new node to its parrent
				currentNode.childs.add(newNode);

				// we take this node as the current node for the next for loop iteration
				currentNode = newNode;

				// We update the header table.
				// We check if there is already a node with this id in the header table
				fixNodeLinks(item, newNode);
			} else {
				// there is a node already, we skip it
				currentNode = child;
			}
		}
		// add timestamps to tail node(currentNode)
		currentNode.timestamps.add(timestamp);
	}

	/**
	 * Method to fix the node link for an item after inserting a new node.
	 * 
	 * @param item    the item of the new node
	 * @param newNode the new node thas has been inserted.
	 */
	private void fixNodeLinks(Integer item, LPPNode newNode) {
		// get the latest node in the tree with this item
		LPPNode lastNode = mapItemLastNode.get(item);
		if (lastNode != null) {
			// if not null, then we add the new node to the node link of the last node
			lastNode.nodeLink = newNode;
		}
		// Finally, we set the new node as the last node
		mapItemLastNode.put(item, newNode);

		LPPNode headernode = mapItemNodes.get(item);
		if (headernode == null) { // there is not
			mapItemNodes.put(item, newNode);
		}
	}

	/**
	 * Method for creating the list of items in the header table, in descending
	 * order of total duration.
	 * 
	 * @param mapTimeIntervals the frequencies of each item (key: item value:
	 *                         support)
	 */
	void createHeaderList(List<Integer> lastHeaderList, final Map<Integer, TimeIntervals> mapTimeIntervals) {

		if (lastHeaderList == null) {
			// this is headerList for total tree

			// create an array to store the header list with
			// all the items stored in the map received as parameter
			headerList = new ArrayList<Integer>(mapItemNodes.keySet());

			// sort the header table by decreasing order of total duration
			Collections.sort(headerList, new Comparator<Integer>() {
				public int compare(Integer item1, Integer item2) {
					// compare the total duration
					int compare = mapTimeIntervals.get(item2).getTotalDuration()
							- mapTimeIntervals.get(item1).getTotalDuration();
					// if the same total duration, we check the lexical ordering!
					if (compare == 0) {
						return (item1 - item2);
					}
					// otherwise, just use the total duration
					return compare;
//                return item1 - item2;
				}
			});
		} else {
			headerList = new ArrayList<>();
			for (int item : lastHeaderList) {
				if (mapTimeIntervals.containsKey(item)) {
					headerList.add(item);
				}
			}
		}
	}

	/**
	 * Method for adding a prefixpath to a pfti-tree
	 * 
	 * @param prefixPath           The prefix path
	 * @param mapBetaTimeIntervals The time-inetrvals of items in the prefixpaths
	 */
	public void addPrefixPath(List<LPPNode> prefixPath, Map<Integer, TimeIntervals> mapBetaTimeIntervals) {
		// the first element of the prefix path contains the path timestamps
		List<Integer> pathTimestamps = prefixPath.get(0).timestamps;

		LPPNode currentNode = root;

		// For each item in the transaction (in backward order)
		// (and we ignore the first element of the prefix path)
		for (int i = prefixPath.size() - 1; i >= 1; i--) {
			LPPNode pathItem = prefixPath.get(i);
			// if the item has periodic frequent time-interval
			if (mapBetaTimeIntervals.containsKey(pathItem.itemID)) {

				// look if there is a node already in the FP-Tree
				LPPNode child = currentNode.getChildByID(pathItem.itemID);
				if (child == null) {
					// there is no node, we create a new one
					LPPNode newNode = new LPPNode();
					newNode.itemID = pathItem.itemID;
					newNode.parent = currentNode;
					currentNode.childs.add(newNode);
					currentNode = newNode;
					// We update the header table.
					// and the node links
					fixNodeLinks(pathItem.itemID, newNode);
				} else {
					// there is a node already, we update it
					currentNode = child;
				}
			}
		}
		// for the second element should accept the timestamps from first
		// Node(pathTimestamps)
		// tail Node move its timestamps to its parent
		if (currentNode.itemID != -1) {
			currentNode.timestamps.addAll(pathTimestamps);
		}
	}

	/**
	 * Remove tail item from the tree
	 */
	@SuppressWarnings("serial")
	public void removeTailItem() {
		// get the index of tail
		int tail = headerList.size() - 1;

		// get the tail path
		LPPNode tailNode = mapItemNodes.get(headerList.get(tail));

		// delete the tail path from mapItemNodes
		mapItemNodes.remove(headerList.get(tail));

		// delete the tail from headerList
		headerList.remove(tail);

		// move each tail node's timestamps to its parent
		while (tailNode != null) {
			LPPNode parent = tailNode.parent;
			parent.removeChildByID(tailNode.itemID);
			if (parent.itemID != -1) {
				List<Integer> timestamps = tailNode.timestamps;

				parent.timestamps.addAll(new ArrayList<Integer>() {
					{
						addAll(timestamps);
					}
				});

			}
			tailNode = tailNode.nodeLink;
		}

	}
}
