
package ca.pfv.spmf.algorithms.associationrules.hgb;

/* This file is copyright (c) Jayakrushna Sahoo
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
 * This is an element from a utility list as used by the FHIM and HUCI-Miner algorithms
 *
 * @author Jayakrushna Sahoo
 */
public class Element {
	final int tid;
	/** itemset utility */
	final int iutils;
	/** remaining utility */
	final int rutils;

	/**
	 * Constructor.
	 * 
	 * @param tid    the transaction id
	 * @param iutils the itemset utility
	 * @param rutils the remaining utility
	 */
	public Element(int tid, int iutils, int rutils) {
		this.tid = tid;
		this.iutils = iutils;
		this.rutils = rutils;
	}

}