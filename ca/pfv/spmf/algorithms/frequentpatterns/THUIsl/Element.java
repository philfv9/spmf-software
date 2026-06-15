package ca.pfv.spmf.algorithms.frequentpatterns.THUIsl;

/* This file is copyright (c) 2024 Srikumar Krishnamoorthy
 * 
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
 
 /**
 * Represents a transaction element in a utility mining context.
 * Each element stores the transaction ID (TID), the item’s internal utility,
 * and the remaining utility of the item within the transaction.
 */

class Element {
	/** Transaction ID for this element. */
	final int tid ;   
	
	/** Internal utility of the item in this transaction. */
	final float iutils; 
	
	/** Remaining utility of the item in this transaction. */
	final float rutils; 
	
	/**
     * Constructs a new Element with the given transaction ID, internal utility,
     * and remaining utility.
     *
     * @param tid     the transaction ID
     * @param iutils  the internal utility of the item
     * @param rutils  the remaining utility of the item
     */
	public Element(int tid, float iutils, float rutils){
		this.tid = tid;
		this.iutils = iutils;
		this.rutils = rutils;
	}
	
	/**
     * Prints the element’s details with indentation corresponding to depth.
     * Useful for debugging hierarchical or tree-like structures.
     *
     * @param depth the indentation depth (number of tabs)
     */
	public void print(int depth){
		for (int i=0;i<depth;i++) System.out.print("\t");
		System.out.println("\t"+tid+" iutils: "+iutils+" rutils: "+rutils);
	}
}
