package ca.pfv.spmf.algorithms.frequentpatterns.d2hup;

/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger
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
import java.util.List;

import ca.pfv.spmf.algorithms.frequentpatterns.hmine.AlgoHMine;

/**
 * This class represents a row in the CAUL structure table.
 * Each row stores information about an item including its support, utility,
 * local TWU, remaining utility (UbPFE), and pointers to transaction items.
 *
 * @see AlgoHMine
 * @author Philippe Fournier-Viger
 */
class Row {
	
	/** The item */
	int item;  
	
	/** Item support */
	int support; 
	
	/** Item utility */
	int utility; 
	
	/** Local TWU (ubitem) */
	int ltwu;
	
	/** Remaining utility (UbPFE) */
	int rutil = 0; 
	
	/** List of pointers to items in transactions */
	List<Pointer> pointers = new ArrayList<Pointer>();

	/**
	 * Constructor
	 * @param item the item for this row
	 */
	public Row(int item){
		this.item = item;
	}
	
	/**
	 * Get string representation of this row
	 * @return a string representation
	 */
	public String toString() {
		String temp = item + " s:" + support + " u:" + utility
				+ " ubItem:" + ltwu + " ubPFE:" + rutil + " pointers: " + pointers;
		return temp;
	}
}