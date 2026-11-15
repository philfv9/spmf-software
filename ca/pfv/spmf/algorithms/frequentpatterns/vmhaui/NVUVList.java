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

import java.util.ArrayList;
import java.util.List;

/**
 * The NVUV-list (node based vertical utility vector list) of an itemset.
 */
class NVUVList {
	
	/** the ranks of the items of the itemset, in ascending order */
	final int[] itemRanks;
	
	/** the elements of the list, in ascending order of nid */
	final List<NVUVElement> elements = new ArrayList<NVUVElement>();

	/**
	 * Constructor.
	 * @param itemRanks the ranks of the items of the itemset
	 */
	public NVUVList(int[] itemRanks) {
		this.itemRanks = itemRanks;
	}

	/**
	 * Get the rank of the last item of the itemset, which is the item of vList.item in the paper.
	 * @return the rank of the last item
	 */
	public int getLastRank() {
		return itemRanks[itemRanks.length - 1];
	}

	/**
	 * Append an element to this list.
	 * @param element the element to append
	 */
	public void addElement(NVUVElement element) {
		elements.add(element);
	}
}