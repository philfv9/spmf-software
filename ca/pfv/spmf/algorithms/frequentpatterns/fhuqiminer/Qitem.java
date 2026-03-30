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

import java.util.Objects;

/**
 * Implementation of a Q-Item as used by the FHUQI-Miner algorithm.
 * 
 * @see AlgoFHUQIMiner
 * 
 * @author Mourad Nouioua, copyright 2020
 */
public class Qitem {
	/** an item */
	private int item;
	/** the minimum quantity */
	private int qteMin;
	/** the maximum quantity */
	private int qteMax;

	/**
	 * The constructor
	 * 
	 * @param i    the item
	 * @param qMin the minimum quantity
	 * @param qMax the maximum quantity
	 */
	public Qitem(int i, int qMin, int qMax) {
		this.item = i;
		this.qteMin = qMin;
		this.qteMax = qMax;
	}

	/**
	 * Consructor
	 * 
	 * @param i the item
	 * @param q the quantity
	 */
	public Qitem(int i, int q) {
		this.item = i;
		this.qteMin = q;
		this.qteMax = q;
	}

	/**
	 * Default constructor
	 */
	public Qitem() {

	}

	/**
	 * Get the item
	 * 
	 * @return the item
	 */
	public int getItem() {
		return this.item;
	}

	/**
	 * Get the minimum quantity
	 * 
	 * @return the quantity
	 */
	public int getQteMin() {
		return this.qteMin;
	}

	/**
	 * Get the maximum quantity
	 * 
	 * @return the maximum quantity
	 */
	public int getQteMax() {
		return this.qteMax;
	}

	/**
	 * Set the item
	 * 
	 * @param i the item
	 */
	public void setItem(int i) {
		this.item = i;
	}

	/**
	 * Set the minimum quantity
	 * 
	 * @param q the minimum quantity
	 */
	public void setQteMin(int q) {
		this.qteMin = q;
	}

	/**
	 * Set the maximum quantity
	 * 
	 * @param q the maximum quantity
	 */
	public void setQteMax(int q) {
		this.qteMax = q;
	}

	/**
	 * Copy the data from another q-item to this q-item
	 * 
	 * @param q the other q-item
	 */
	public void copy(Qitem q) {
		this.item = q.item;
		this.qteMin = q.qteMin;
		this.qteMax = q.qteMax;
	}

	/**
	 * Check if it is a range q-item
	 * 
	 * @return true if it is a range q-item. Otherwise, false.
	 */
	public boolean isRange() {
		if (this.qteMin == this.qteMax)
			return false;
		return true;
	}

	/**
	 * Get a string representation of this q-item
	 * 
	 * @return a string
	 */
	public String toString() {
		String str = "";
		if (this.isRange() == false)
			str += "(" + this.item + "," + this.qteMin + ")";
		else
			str += "(" + this.item + "," + this.qteMin + "," + this.qteMax + ")";
		return str;
	}

	@Override
	public boolean equals(Object o) {

		if (o == this)
			return true;
		if (!(o instanceof Qitem)) {
			return false;
		}
		Qitem x = (Qitem) o;
		return item == x.item && qteMin == x.qteMin && qteMax == x.qteMax;
	}

	@Override
	public int hashCode() {
		return Objects.hash(item, qteMin, qteMax);
	}
}
