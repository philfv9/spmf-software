package ca.pfv.spmf.algorithms.frequentpatterns.vmhaui;

/* This is an implementation of the VMHAUI algorithm.
*
* Copyright (c) 2026 VMHAUI
*
* This file is part of the SPMF DATA MINING SOFTWARE * (http://www.philippe-fournier-viger.com/spmf).
*
*
* SPMF is free software: you can redistribute it and/or modify it under the * terms of the GNU General Public License as published by the Free Software * Foundation, either version 3 of the License, or (at your option) any later * version. *
*
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR * A PARTICULAR PURPOSE. See the GNU General Public License for more details. *
*
* You should have received a copy of the GNU General Public License along with * SPMF. If not, see <http://www.gnu.org/licenses/>.
*
* @author Philippe Fournier-Viger
* 
* Do not remove copyright and license information.
*/

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * A node of the transactional prefix utility tree (TPUT).
 */
class TPUTNode {
	/** the item stored in this node */
	final int item;
	
	/** the rank of the item in the ascending order of support */
	final int rank;
	
	/** the order number indicating when this node was added to the tree */
	final int nid;
	
	/** the depth of this node (the children of the root have a depth of 1) */
	final int depth;
	
	/** the parent_link field, which points to the parent of the node */
	final TPUTNode parent;
	
	/** the next_link field, which points to the next node storing the same item */
	TPUTNode next = null;
	
	/** the transaction identifiers of the tuList field, in ascending order */
	int[] tids = new int[2];
	
	/** the utilities of the tuList field, aligned with the transaction identifiers */
	int[] utils = new int[2];
	
	/** the number of elements of the tuList field */
	int size = 0;
	
	/** the children of this node, indexed by item rank */
	Map<Integer, TPUTNode> children = null;
	
	/** the vertical utility vector shared by all the NVUV-list elements of this node */
	UtilityVector vector = null;

	/**
	 * Constructor.
	 * @param item the item stored in the node
	 * @param rank the rank of the item
	 * @param nid the order number of the node
	 * @param parent the parent of the node, or null for the root
	 */
	public TPUTNode(int item, int rank, int nid, TPUTNode parent) {
		this.item = item;
		this.rank = rank;
		this.nid = nid;
		this.parent = parent;
		this.depth = (parent == null) ? 0 : parent.depth + 1;
	}

	/**
	 * Get the child of this node having a given item rank.
	 * @param childRank the rank of the item of the child
	 * @return the child node, or null if there is none
	 */
	public TPUTNode getChild(int childRank) {
		if (children == null) {
			return null;
		}
		return children.get(childRank);
	}

	/**
	 * Add a child to this node.
	 * @param child the child node to add
	 */
	public void addChild(TPUTNode child) {
		if (children == null) {
			children = new HashMap<Integer, TPUTNode>();
		}
		children.put(child.rank, child);
	}

	/**
	 * Append an element (tid, util) to the tuList of this node.
	 * @param tid the transaction identifier
	 * @param util the utility of the item of this node in the transaction
	 */
	public void addTuple(int tid, int util) {
		if (size == tids.length) {
			tids = Arrays.copyOf(tids, size * 2);
			utils = Arrays.copyOf(utils, size * 2);
		}
		tids[size] = tid;
		utils[size] = util;
		size++;
	}

	/**
	 * Sum the utilities of the tuList of this node restricted to the transactions of a descendant node.
	 * @param descendant a descendant of this node, whose transactions are included in this node
	 * @return the sum of the utilities of the common transactions
	 */
	private long sumUtilitiesOfTids(TPUTNode descendant) {
		long sum = 0;
		int low = 0;
		for (int i = 0; i < descendant.size; i++) {
			int tid = descendant.tids[i];
			int first = low;
			int last = size - 1;
			while (first <= last) {
				int middle = (first + last) >>> 1;
				if (tids[middle] < tid) {
					first = middle + 1;
				} else if (tids[middle] > tid) {
					last = middle - 1;
				} else {
					sum += utils[middle];
					low = middle + 1;
					break;
				}
			}
		}
		return sum;
	}

	/**
	 * Get the vertical utility vector of this node, computing it once on first use.
	 * It contains the column utilities of the item of this node and of all its ancestors over the transactions of this node.
	 * @return the vertical utility vector shared by all the elements having the nid of this node
	 */
	public UtilityVector getVector() {
		if (vector == null) {
			int[] pathRanks = new int[depth];
			long[] pathUtils = new long[depth];
			TPUTNode current = this;
			int position = 0;
			while (current.parent != null) {
				pathRanks[position] = current.rank;
				if (current == this) {
					long total = 0;
					for (int i = 0; i < size; i++) {
						total += utils[i];
					}
					pathUtils[position] = total;
				} else {
					pathUtils[position] = current.sumUtilitiesOfTids(this);
				}
				current = current.parent;
				position++;
			}
			vector = new UtilityVector(pathRanks, pathUtils);
		}
		return vector;
	}

	/**
	 * Release the vertical utility vector of this node to free memory.
	 */
	public void releaseVector() {
		vector = null;
	}
}