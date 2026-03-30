/* This file is copyright (c) 2020 Mourad Nouioua et al.
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
* 
*/
package ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer;

/**
 * Implementation of a Q-Item transaction element for utility-lists as used by
 * the FHUQI-Miner algorithm.
 * 
 * @see AlgoFHUQIMiner
 * 
 * @author Mourad Nouioua, copyright 2020
 */
public class QItemTrans {
	/** the identifier of a transaction */
	private int tid;
	/** the e-utility */
	private int eu;

	/** the remaining utility */
	private int ru;

	/**
	 * Constructor
	 * 
	 * @param tid the transaction identifier
	 * @param eu  the e-utility
	 * @param ru  the r-utility
	 */
	public QItemTrans(int tid, int eu, int ru) {
		this.tid = tid;
		this.eu = eu;
		this.ru = ru;
	}

	/**
	 * get the transaction id
	 * 
	 * @return the transaction id
	 */
	public int getTid() {
		return tid;
	}

	/**
	 * Get the e-utility
	 * 
	 * @return the utility
	 */
	public int getEu() {
		return eu;
	}

	/**
	 * Get the remaining utility
	 * 
	 * @return the utility
	 */
	public int getRu() {
		return ru;
	}

	/**
	 * Get the sum of e-utility and r-utility
	 * 
	 * @return the sum
	 */
	public int sum() {
		return eu + ru;

	}

	/**
	 * Get a string representation of this object
	 * 
	 * @return a string
	 */
	public String toString() {
		return tid + " " + eu + "	" + ru;
	}
}