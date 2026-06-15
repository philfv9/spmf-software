package ca.pfv.spmf.algorithms.frequentpatterns.cori;
import ca.pfv.spmf.patterns.itemset_array_integers_with_count.Itemset;

/* This file is copyright (c) 2008-2015 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/

/**
 * This class represents an itemset with support count and bond value.
 * It extends the standard Itemset class to add bond functionality.
 * 
 * @author Philippe Fournier-Viger 2015
 * @see AlgoCORI
 */
public class ItemsetCORI extends Itemset{
	
	/** The bond value of this itemset */
	public double bond = 0; 

	/**
	 * Constructor
	 * @param array an array of items representing this itemset
	 */
	public ItemsetCORI(int[] array) {
		super(array);
	}

	/**
	 * Get the bond value
	 * @return the bond value
	 */
	public double getBond() {
		return bond;
	}

	/**
	 * Set the bond value
	 * @param bond the bond value
	 */
	public void setBond(Integer bond) {
		this.bond = bond;
	}
}