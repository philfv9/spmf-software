package ca.pfv.spmf.algorithms.frequentpatterns.dci_closed_fast;
/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * A comparator interface for primitive int values.
 * Used by IntList.sort() to avoid Integer autoboxing.
 *
 * @see IntList
 * @author Philippe Fournier-Viger
 */
public interface IntComparator {
	
	/**
	 * Compare two primitive int values.
	 * @param a the first value
	 * @param b the second value
	 * @return negative if a &lt; b, zero if a == b, positive if a &gt; b
	 */
	int compare(int a, int b);
}