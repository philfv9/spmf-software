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

import java.util.ArrayList;
import java.util.Arrays;

import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.Qitem;

/**
 * A Q-itemset as used by the TKQ algorithm
 * 
 * @see AlgoTKQ
 * @author Mourad Nouioua
 */
public class Qitemset implements Comparable<Qitemset> {
	/** an itemset */
	public ArrayList<Qitem> itemset;
	/** the itemset's utility */
	public long utility;

	/**
	 * Default constructor
	 */
	public Qitemset() {
		this.itemset = new ArrayList<Qitem>();
		this.utility = 0;
	}

	/**
	 * Constructor for an itemset with utility
	 * 
	 * @param itemset a list of Q-items
	 * @param util    the utility
	 */
	public Qitemset(ArrayList<Qitem> itemset, long util) {
		this.itemset = new ArrayList<Qitem>();
		this.itemset = itemset;
		this.utility = util;
	}

	/**
	 * Constructor for a single Q-item
	 * 
	 * @param item the q-item
	 * @param util its utility
	 */
	public Qitemset(Qitem item, long util) {
		this.itemset = new ArrayList<Qitem>();
		this.itemset.add(item);
		this.utility = util;
	}

	/**
	 * Constructor for an itemset consisting of two q-items
	 * 
	 * @param item1 the first q-item
	 * @param item2 the second q-item
	 * @param util  the utility of the itemset
	 */
	public Qitemset(Qitem item1, Qitem item2, long util) {
		this.itemset = new ArrayList<Qitem>();
		this.itemset.add(item1);
		this.itemset.add(item2);
		this.utility = util;
	}

	/**
	 * Constructor for an itemset defined using a prefix + two q-items
	 * 
	 * @param prefix the prefix
	 * @param length the prefix length
	 * @param x      a q-item
	 * @param y      another q-item
	 * @param util   the itemset's utility
	 */
	public Qitemset(Qitem[] prefix, int length, Qitem x, Qitem y, long util) {
//		Qitem[] p = new Qitem[length];
		this.itemset = new ArrayList<Qitem>(Arrays.asList(prefix).subList(0, length));
		this.itemset.add(x);
		this.itemset.add(y);
		this.utility = util;
	}

	/**
	 * Constructor for an itemset defined using a prefix + one q-item
	 * 
	 * @param prefix the prefix
	 * @param length the prefix length
	 * @param x      the Q-item
	 * @param util   the itemset's utility
	 */
	public Qitemset(Qitem[] prefix, int length, Qitem x, long util) {
//		Qitem[] p = new Qitem[length];
		this.itemset = new ArrayList<Qitem>(Arrays.asList(prefix).subList(0, length));
		this.itemset.add(x);
		this.utility = util;
	}

	/**
	 * Get a string representation of this object
	 * 
	 * @return a string
	 */
	public String toString() {
		String str = "";

		str += this.itemset.toString();

		str += " #Util" + this.utility;
		return str;
	}

	/**
	 * Compare this object with another of the same type
	 * 
	 * @param o another object
	 * @return an integer indicating whether the object is equal, smaller or greater
	 *         than the other one (see Java comparator)
	 */
	public int compareTo(Qitemset o) {
		if (o == this) {
			return 0;
		}
		long compare = this.utility - o.utility;
		if (compare != 0) {
			return (int) compare;
		}

		return this.hashCode() - o.hashCode();
	}

	/**
	 * Add a q-item
	 * 
	 * @param q the q-item
	 */
	public void addQitem(Qitem q) {
		this.itemset.add(q);
	}

	/**
	 * Remove a q-item
	 * 
	 * @param indiceOfQ the indice of the q-item
	 * @return the resulting q-itemset
	 */
	public Qitemset removeQitem(int indiceOfQ) {
		Qitemset newQ = new Qitemset();
		newQ.itemset = this.itemset;
		newQ.utility = this.utility;
		newQ.itemset.remove(indiceOfQ);
		return newQ;
	}

	/**
	 * Remove a q-item
	 * 
	 * @param q the q-item to be removed
	 * @return the resulting q-itemset
	 */
	public Qitemset removeQitem(Qitem q) {
		Qitemset newQ = new Qitemset();
		newQ.itemset = this.itemset;
		newQ.utility = this.utility;
		newQ.itemset.remove(q);
		return newQ;
	}

	/**
	 * Set the utility of this q-itemset
	 * 
	 * @param utility the utility value
	 */
	public void setUtility(long utility) {
		this.utility = utility;
	}

}
