package ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.tkq;

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
import java.util.ArrayList;

import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.QItemTrans;
import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.Qitem;

/**
 * A class representing a utility-list as used by the TKQ algorithm
 * 
 * @see AlgoTKQ
 * @author Mourad Nouioua
 */
public class UtilityListTKQ {
	/** the Q-itemset */
	private ArrayList<Qitem> items;
	/** the sum of iutil values */
	private long sumIutils;
	/** the sum of rutil values */
	private long sumRutils;
	/** the sum of iutil non zero values */
	private long sumIutilsNonZero;
	/** the twu */
	private long twu;

	/** the list of q-item transactions */
	private ArrayList<QItemTrans> qItemTrans = null;

	/** boolean indicating if this itemset is a range q-itemset */
	private boolean rangeOrNot = false;

	/**
	 * Default constructor
	 */
	public UtilityListTKQ() {

	}

	/**
	 * Constructor
	 * 
	 * @param qitemset A list of q-items
	 * @param twu      the TWU of the itemset
	 */
	public UtilityListTKQ(ArrayList<Qitem> qitemset, long twu) {
		// this.prefix="";
		this.items = new ArrayList<Qitem>();
		this.items = qitemset;
		this.sumIutils = 0;
		this.sumIutilsNonZero = 0;
		this.sumRutils = 0;
		this.twu = twu;
		this.qItemTrans = new ArrayList<QItemTrans>();
	}

	/**
	 * Constructor
	 * 
	 * @param qitemset A list of q-items
	 */
	public UtilityListTKQ(ArrayList<Qitem> qitemset) {
		// this.prefix="";
		this.items = new ArrayList<Qitem>();
		this.items = qitemset;
		this.sumIutils = 0;
		this.sumIutilsNonZero = 0;
		this.sumRutils = 0;
		this.twu = 0;
		this.qItemTrans = new ArrayList<QItemTrans>();
	}

	/**
	 * Constructor
	 * 
	 * @param item a q-item
	 */
	public UtilityListTKQ(Qitem item) {
		this.items = new ArrayList<Qitem>();
		this.items.add(item);
		this.sumIutils = 0;
		this.sumIutilsNonZero = 0;
		this.sumRutils = 0;
		this.twu = 0;
		qItemTrans = new ArrayList<QItemTrans>();
	}

	/**
	 * Constructor
	 * 
	 * @param item a q-item
	 * @param twu  the twu
	 */
	public UtilityListTKQ(Qitem item, long twu) {
		this.items = new ArrayList<Qitem>();
		this.items.add(item);
		this.sumIutils = 0;
		this.sumIutilsNonZero = 0;
		this.sumRutils = 0;
		this.twu = twu;
		qItemTrans = new ArrayList<QItemTrans>();
	}

	/**
	 * Increase the TWU by some value
	 * 
	 * @param twu the value
	 */
	public void addTWU(int twu) {
		this.twu += twu;
	}

	/**
	 * Set the TWU to sero
	 */
	public void setTWUtoZero() {
		this.twu = 0;
	}

	/**
	 * Get the support
	 * 
	 * @return the support
	 */
	public int getSupport() {
		return this.qItemTrans.size();
	}

	/**
	 * Add a transaction with its TWU
	 * 
	 * @param qTid the transaction
	 * @param twu  the twu
	 */
	public void addTrans(QItemTrans qTid, long twu) {
		if (qTid.getRu() != 0)
			this.sumIutilsNonZero = this.sumIutilsNonZero + qTid.getEu();

		this.sumIutils += qTid.getEu();
		this.sumRutils += qTid.getRu();
		qItemTrans.add(qTid);
		this.twu += twu;
	}

	/**
	 * Add a transaction
	 * 
	 * @param qTid the transaction
	 */
	public void addTrans(QItemTrans qTid) {
		if (qTid.getRu() != 0)
			this.sumIutilsNonZero = this.sumIutilsNonZero + qTid.getEu();

		this.sumIutils += qTid.getEu();
		this.sumRutils += qTid.getRu();
		qItemTrans.add(qTid);
	}

	/**
	 * Get the sum of iutil values
	 * 
	 * @return the sum
	 */
	public long getSumIutils() {
		return this.sumIutils;
	}

	/**
	 * Get the sum of rutil values
	 * 
	 * @return the sum
	 */
	public long getSumRutils() {
		return this.sumRutils;
	}

	/**
	 * Get the sum of iutil values that are non zero
	 * 
	 * @return the sum
	 */
	public long sumIutilsNonZero() {
		return this.sumIutilsNonZero;
	}

	/**
	 * Set the sum of iutil values
	 * 
	 * @param sum the value
	 */
	public void setSumIutils(long sum) {
		this.sumIutils = sum;
	}

	/**
	 * Set this itemset to be a range q-itemset
	 */
	public void setRangeAsTrue() {
		this.rangeOrNot = true;
	}

	/**
	 * Set the sum of rutil value
	 * 
	 * @param sum the value
	 */
	public void setSumRutils(long sum) {
		this.sumRutils = sum;
	}

	/**
	 * Get the TWU
	 * 
	 * @return the TWU
	 */
	public long getTwu() {
		return twu;
	}

	/**
	 * Check if this itemset is a range q-itemset
	 * 
	 * @return true if yes, false if not
	 */
	public boolean isRange() {
		return this.rangeOrNot;
	}

	/**
	 * Set the TWU of this q-itemset
	 * 
	 * @param twu the twu
	 */
	public void setTwu(long twu) {
		this.twu = twu;
	}

	/**
	 * Get the list of Q-items in this itemset
	 * 
	 * @return the list of Q-items
	 */
	public ArrayList<Qitem> getItemsetName() {
		return this.items;
	}

	/**
	 * Get the first q-item in this itemset
	 * 
	 * @return the q-item
	 */
	public Qitem getSingleItemsetName() {
		return this.items.get(0);
	}

	/**
	 * Get the list of transactions for this itemset
	 * 
	 * @return the list of transactions
	 */
	public ArrayList<QItemTrans> getQItemTrans() {
		return qItemTrans;
	}

	/**
	 * Set the list of transactions
	 * 
	 * @param elements the list
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
	public void addUtilityList2(UtilityListTKQ next) {
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
		str += items + "\r\n";
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
