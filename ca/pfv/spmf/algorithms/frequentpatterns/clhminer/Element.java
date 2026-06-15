package ca.pfv.spmf.algorithms.frequentpatterns.clhminer;

/* This file is copyright (c) Bay Vo
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
 * Implementation of a utility list element as used by CLH-Miner
 * @see AlgoCLHMiner
 * 
 * @author Bay Vo et al.
 */
public class Element {

	/** transaction id */
	public int tid;

	/** itemset utility */
	public double iutils;

	/** remaining utility */
	public double rutils;

	/** transaction utility */
	public double TU;

	/**
	 * Constructor.
	 * @param tid the transaction id
	 * @param iutils the itemset utility
	 * @param rutils the remaining utility
	 * @param TU the transaction utility
	 */
	public Element(int tid, double iutils, double rutils, double TU) {
		this.tid = tid;
		this.iutils = iutils;
		this.rutils = rutils;
		this.TU = TU;
	}

	/**
	 * Constructor.
	 * @param tid the transaction id
	 */
	public Element(int tid) {
		this.tid = tid;
		this.iutils = 0;
		this.rutils = 0;
		this.TU = 0;
	}

	/**
	 * Constructor.
	 * @param tid the transaction id
	 * @param iutils the itemset utility
	 */
	public Element(int tid, double iutils) {
		this.tid = tid;
		this.iutils = iutils;
		this.rutils = 0;
		this.TU = 0;
	}
}