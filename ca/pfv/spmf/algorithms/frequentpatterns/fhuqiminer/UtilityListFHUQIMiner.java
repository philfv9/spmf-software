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

import java.util.ArrayList;

/**
 * Implementation of a utility list as used by the FHUQI-Miner algorithm.
 * 
 * @see AlgoFHUQIMiner
 * 
 * @author Mourad Nouioua, copyright 2020
 */
public class UtilityListFHUQIMiner {
	/** the Q-itemset */
	private ArrayList<Qitem> itemsetName;
	/** the sum of iutil values */
	private long sumIutils;
	/** the sum of rutil values */
	private long sumRutils;
	/** the twu */
	private long twu;

	/** the list of q-item transactions */
	private ArrayList<QItemTrans> qItemTrans = null;

	/**
	 * Constructor
	 * 
	 * @param qitemset a qitemset
	 * @param twu      the twu of that itemset
	 */
	public UtilityListFHUQIMiner(ArrayList<Qitem> qitemset, long twu) {
		// this.prefix="";
		this.itemsetName = new ArrayList<Qitem>();
		this.itemsetName = qitemset;
		this.sumIutils = 0;
		this.sumRutils = 0;
		this.twu = twu;
		this.qItemTrans = new ArrayList<QItemTrans>();
	}

	/**
	 * Constructor
	 * 
	 * @param qitemset a q-itemset
	 */
	public UtilityListFHUQIMiner(ArrayList<Qitem> qitemset) {
		// this.prefix="";
		this.itemsetName = new ArrayList<Qitem>();
		this.itemsetName = qitemset;
		this.sumIutils = 0;
		this.sumRutils = 0;
		this.twu = 0;
		this.qItemTrans = new ArrayList<QItemTrans>();
	}

	/**
	 * Constructor
	 * 
	 * @param name a q-item
	 */
	public UtilityListFHUQIMiner(Qitem name) {
		this.itemsetName = new ArrayList<Qitem>();
		this.itemsetName.add(name);
		this.sumIutils = 0;
		this.sumRutils = 0;
		this.twu = 0;
		qItemTrans = new ArrayList<QItemTrans>();
	}

	/**
	 * Constructor
	 * 
	 * @param name a q-item
	 * @param twu  the twu
	 */
	public UtilityListFHUQIMiner(Qitem name, long twu) {
		this.itemsetName = new ArrayList<Qitem>();
		this.itemsetName.add(name);
		this.sumIutils = 0;
		this.sumRutils = 0;
		this.twu = twu;
		qItemTrans = new ArrayList<QItemTrans>();
	}

	/**
	 * Default constructor
	 */
	public UtilityListFHUQIMiner() {

	}

	/**
	 * Add the TWU value
	 * 
	 * @param twu the twu
	 */
	public void addTWU(int twu) {
		this.twu += twu;
	}

	/**
	 * Set the TWU to 0.
	 */
	public void setTWUtoZero() {
		this.twu = 0;
	}

	/**
	 * Add a transaction
	 * 
	 * @param qTid the transaction id
	 * @param twu  the twu
	 */
	public void addTrans(QItemTrans qTid, long twu) {
		this.sumIutils += qTid.getEu();
		this.sumRutils += qTid.getRu();
		qItemTrans.add(qTid);
		this.twu += twu;
	}

	/**
	 * Add a transaction
	 * 
	 * @param qTid the transaction id
	 */
	public void addTrans(QItemTrans qTid) {
		this.sumIutils += qTid.getEu();
		this.sumRutils += qTid.getRu();
		qItemTrans.add(qTid);
	}

	/**
	 * Get the sum of all iutil values
	 * 
	 * @return the sum
	 */
	public long getSumIutils() {
		return this.sumIutils;
	}

	/**
	 * Get the sum of all rutil values
	 * 
	 * @return the sum
	 */
	public long getSumRutils() {
		return this.sumRutils;
	}

	/**
	 * Set the sum of all iutil values
	 * 
	 * @param x the sum
	 */
	public void setSumIutils(long x) {
		this.sumIutils = x;
	}

	/**
	 * Set the sum of all rutil values
	 * 
	 * @param x the sum
	 */
	public void setSumRutils(long x) {
		this.sumRutils = x;
	}

	/**
	 * Get the twu value
	 * 
	 * @return the twu
	 */
	public long getTwu() {
		return twu;
	}

	/**
	 * Set the TWU value
	 * 
	 * @param twu the twu
	 */
	public void setTwu(long twu) {
		this.twu = twu;
	}

	/**
	 * Get the q-itemset.
	 * 
	 * @return the q-itemset
	 */
	public ArrayList<Qitem> getItemsetName() {
		return this.itemsetName;
	}

	/**
	 * Get the first item
	 * 
	 * @return the first item
	 */
	public Qitem getSingleItemsetName() {
		return this.itemsetName.get(0);
	}

	/**
	 * Get the QItem transactions
	 * 
	 * @return the list of q-item transactions
	 */
	public ArrayList<QItemTrans> getQItemTrans() {
		return qItemTrans;
	}

	/**
	 * Set the Qitem transactions
	 * 
	 * @param elements the list of q-item transactions
	 */
	public void setQItemTrans(ArrayList<QItemTrans> elements) {
		this.qItemTrans = elements;
	}

	/**
	 * Add a q-item transaction
	 * 
	 * @param a the first one
	 * @param b the second one
	 * @return the result
	 */
	public QItemTrans QitemTransAdd(QItemTrans a, QItemTrans b) {
		QItemTrans x;
		x = new QItemTrans(a.getTid(), a.getEu() + b.getEu(), a.getRu() + b.getRu());
		return x;
	}

	/**
	 * Add a utility list
	 * 
	 * @param next the next utility list
	 */
	public void addUtilityList2(UtilityListFHUQIMiner next) {
		ArrayList<QItemTrans> temp = next.getQItemTrans();
		ArrayList<QItemTrans> mainlist = new ArrayList<QItemTrans>();
		this.sumIutils += next.getSumIutils();
		this.sumRutils += next.getSumRutils();
		this.twu += next.getTwu();

		if (qItemTrans.size() == 0) {
			for (int k = 0; k < temp.size(); k++) {
				qItemTrans.add(temp.get(k));
			}
		} else {
			int i = 0, j = 0;
			// System.out.println("qItemTrans="+qItemTrans.size()+" temp="+temp.size());

			while (i < qItemTrans.size() && j < temp.size()) {
				int t1 = qItemTrans.get(i).getTid();
				int t2 = temp.get(j).getTid();
				if (t1 > t2) {
					mainlist.add(temp.get(j));
					j++;
				} else if (t1 < t2) {
					mainlist.add(qItemTrans.get(i));
					i++;
				} else {

					mainlist.add(t1, QitemTransAdd(qItemTrans.get(i), temp.get(j)));
				}

			}
			if (i == qItemTrans.size()) {
				while (j < temp.size()) {
					mainlist.add(temp.get(j++));
				}
			} else if (j == temp.size()) {
				while (i < qItemTrans.size()) {
					mainlist.add(qItemTrans.get(i++));
				}
			}
			qItemTrans.clear();
			qItemTrans = mainlist;

		}

	}

	/**
	 * Get a string representation of this utility list
	 * 
	 * @return a string
	 */
	public String toString() {
		String str = "\n=================================\n";
		str += itemsetName + "\r\n";
		str += "sumEU=" + this.sumIutils + " sumRU=" + this.sumRutils + " twu=" + twu + "\r\n";

		for (int i = 0; i < qItemTrans.size(); i++) {
			str += qItemTrans.get(i).toString() + "\r\n";
		}
		str += "=================================\n";
		return str;
	}

	/**
	 * Get the q-item transaction count
	 * 
	 * @return the count
	 */
	public int getqItemTransLength() {
		if (qItemTrans == null)
			return 0;
		else
			return qItemTrans.size();
	}
}
