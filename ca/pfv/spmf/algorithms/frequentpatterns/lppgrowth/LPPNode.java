package ca.pfv.spmf.algorithms.frequentpatterns.lppgrowth;

import java.util.ArrayList;
import java.util.List;

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
 * This class represents an LPP tree node.
 * 
 * @author Peng yang
 * @see AlgoLPPGrowth
 */
public class LPPNode {

	/** item id */
	int itemID = -1;

	/** the list of timestamps */
	List<Integer> timestamps = new ArrayList<Integer>();

	/** the parent node of that node or null if it is the root */
	LPPNode parent = null;

	/** the child nodes of that node */
	List<LPPNode> childs = new ArrayList<LPPNode>();

	/** link to next node with the same item id (for the header table). */
	LPPNode nodeLink = null;

	/**
	 * Constructor
	 */
	LPPNode() {

	}

	/**
	 * Return the immediate child of this node having a given ID. If there is no
	 * such child, return null;
	 */
	public LPPNode getChildByID(int id) {
		// for each child node
		for (LPPNode child : childs) {
			// if the id is the one that we are looking for
			if (child.itemID == id) {
				// return that node
				return child;
			}
		}
		// if not found, return null
		return null;
	}

	/**
	 * Remove a child having a given ID
	 * 
	 * @param id the id
	 */
	public void removeChildByID(int id) {
		// for each child node
		for (int i = 0; i < childs.size(); i++) {
			if (childs.get(i).itemID == id) {
				childs.remove(i);
				return;
			}
		}
	}

	/**
	 * Get a string representation of this object
	 * 
	 * @return a string
	 */
	public String toString() {
		return "" + itemID;
	}
}
