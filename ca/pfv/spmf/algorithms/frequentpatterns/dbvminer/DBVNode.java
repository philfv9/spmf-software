package ca.pfv.spmf.algorithms.frequentpatterns.dbvminer;

import java.util.Arrays;

/* This file is copyright (c) 2008-2025 Philippe Fournier-Viger
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
 * Node in the DBV-tree containing an itemset and its DBV. The itemset should
 * always be the CLOSURE for its DBV.
 * @see AlgoDBVMiner
 * @author Philippe Fournier-Viger
 */
public class DBVNode {
	/** Items in this node (sorted) */
	int[] itemset;
	
	/** Transaction set where this itemset appears */
	DBV dbv;

	public DBVNode(int[] itemset, DBV dbv) {
		this.itemset = itemset;
		this.dbv = dbv;
	}

	/**
	 * Create a copy of this node with a copied itemset array. The DBV is shared
	 * (immutable).
	 */
	public DBVNode copy() {
		return new DBVNode(Arrays.copyOf(itemset, itemset.length), this.dbv);
	}
}