/* This file is copyright (c) 2021 Mourad Nouioua et al.
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
package ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.tkq;

/**
 * A class to store information as pair of utility and Twu value for the TKQ
 * algorithm
 * 
 * @see AlgoTKQ
 * @author Mourad Nouioua
 */
public class InfoTKQ implements Comparable<InfoTKQ> {
	/** a TWU value */
	public long twu = 0L;
	/** a Utility value */
	public int utility = 0;

	/**
	 * Get a string representation of this object
	 * 
	 * @return a string
	 */
	public String toString() {
		String str = "";
		str += "(twu:" + twu + ", utility:" + utility + ")";
		return str;
	}

	/**
	 * Compare this object with another of the same type
	 * 
	 * @param o another object
	 * @return an integer indicating whether the object is equal, smaller or greater
	 *         than the other one (see Java comparator)
	 */
	public int compareTo(InfoTKQ o) {
		if (o == this) {
			return 0;
		}
		long compare = this.utility - o.utility;
		if (compare != 0) {
			return (int) compare;
		}

		return this.hashCode() - o.hashCode();
	}
}
