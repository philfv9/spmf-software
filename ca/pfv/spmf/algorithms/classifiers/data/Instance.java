/* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* It was obtained from the LAC library under the GNU GPL license and adapted for SPMF.
* @Copyright original version LAC 2019   @copyright of modifications SPMF 2021
*
* SPMF is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* SPMF is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with SPMF.  If not, see <http://www.gnu.org/licenses/>.
* 
*/
package ca.pfv.spmf.algorithms.classifiers.data;

/**
 * Class used to represent each instance contained into a dataset
 */
public class Instance {
	/**
	 * Array with all the values contained in this instance
	 */
	private Short[] items;

	/**
	 * Constructor
	 * 
	 * @param items An instance
	 */
	public Instance(int count) {
		this.items = new Short[count];
	}

	/**
	 * Constructor
	 * 
	 * @param instance An instance
	 */
	public Instance(Short[] instance) {
		this.items = instance;
	}

	/**
	 * Get the attribute values
	 * 
	 * @return an array of attribute values (represented as numbers)
	 */
	public Short[] getItems() {
		return items;
	}
	
	/**  Get the attribute value of the k-th attribute */
	 Short getAttributeValue(int k) {
		return items[k];
	}

	/**
	 * Get the class for this instance
	 * 
	 * @return the internal representation for this instance
	 */
	public Short getKlass() {
		// the class is stored as the last attribute value
		return items[items.length - 1];
	}

	/**
	 * Set the value of the class in this instance
	 * 
	 * @param klass value for the class in this instance
	 */
	public void setKlass(Short klass) {
		// the class is stored as the last attribute value
		items[items.length - 1] = klass;
	}

	/**
	 * Set the attribute value situated in the position specified
	 * 
	 * @param j     position of the attribute in current instance
	 * @param value for this attribute in this instance
	 */
	public void set(int j, short value) {
		items[j] = value;
	}

	/**
	 * Get a string representation
	 * 
	 * @return String
	 */
	public String toString() {
		StringBuilder buffer = new StringBuilder();
		for (Object object : items) {
			buffer.append(object);
			buffer.append(" ");
		}
		return buffer.toString();
	}
}
