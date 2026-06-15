package ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.chuqiminer;

import java.util.ArrayList;
import java.util.BitSet;

import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.QItemTrans;
import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.Qitem;

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
* Do not remove the copyright and license information from this file.
*/

/**
 * A QUtility list with correlation information for mining correlated
 * quantitative high utility itemsets. This class represents a utility list that
 * tracks items, their utilities, transaction-weighted utility (TWU), and
 * correlation information using bitsets.
 * 
 * @see AlgoCHUQIMiner
 */
public class QUtilityListCorr {

	/** The list of quantitative items in this itemset */
	private ArrayList<Qitem> itemsetName;

	/** The sum of item utilities */
	private long sumIutils;

	/** The sum of item utilities for non-zero remaining utilities */
	private long sumIutilsNonZero;

	/** The sum of remaining utilities */
	private long sumRutils;

	/** The transaction-weighted utility value */
	private long twu;

	/** Flag indicating whether this utility list represents a range constraint */
	private boolean rangeOrNot = false;

	/** The list of quantitative item transactions containing this itemset */
	private ArrayList<QItemTrans> qItemTrans = null;

	/**
	 * Binary sequence represented as a bitset for efficient transaction tracking
	 */
	BitSet binarySeq;

	/** Disjunctive bitset of transaction IDs where this itemset appears */
	BitSet bitsetDisjunctiveTIDs;

	/**
	 * Default constructor initializing an empty QUtilityListCorr.
	 */
	public QUtilityListCorr() {
	}

	/**
	 * Constructor initializing a QUtilityListCorr with an itemset, TWU, and
	 * support.
	 * 
	 * @param qitemset the list of quantitative items
	 * @param twu      the transaction-weighted utility
	 * @param support  the support value
	 */
	public QUtilityListCorr(ArrayList<Qitem> qitemset, long twu, int support) {
		this.itemsetName = new ArrayList<Qitem>();
		this.itemsetName = qitemset;
		this.sumIutils = 0;
		this.sumIutilsNonZero = 0;
		this.sumRutils = 0;
		this.twu = twu;
		this.qItemTrans = new ArrayList<QItemTrans>();
		this.bitsetDisjunctiveTIDs = new BitSet();
		this.binarySeq = new BitSet();
	}

	/**
	 * Constructor initializing a QUtilityListCorr with an itemset and support.
	 * 
	 * @param qitemset the list of quantitative items
	 * @param support  the support value
	 */
	public QUtilityListCorr(ArrayList<Qitem> qitemset, int support) {
		this.itemsetName = new ArrayList<Qitem>();
		this.itemsetName = qitemset;
		this.sumIutils = 0;
		this.sumIutilsNonZero = 0;
		this.sumRutils = 0;
		this.twu = 0;
		this.qItemTrans = new ArrayList<QItemTrans>();
		this.bitsetDisjunctiveTIDs = new BitSet();
		this.binarySeq = new BitSet();
	}

	/**
	 * Constructor initializing a QUtilityListCorr with a single item.
	 * 
	 * @param name the quantitative item
	 */
	public QUtilityListCorr(Qitem name) {
		this.itemsetName = new ArrayList<Qitem>();
		this.itemsetName.add(name);
		this.sumIutils = 0;
		this.sumIutilsNonZero = 0;
		this.sumRutils = 0;
		this.twu = 0;
		qItemTrans = new ArrayList<QItemTrans>();
		this.bitsetDisjunctiveTIDs = new BitSet();
		this.binarySeq = new BitSet();
	}

	/**
	 * Constructor initializing a QUtilityListCorr with a single item and TWU.
	 * 
	 * @param name the quantitative item
	 * @param twu  the transaction-weighted utility
	 */
	public QUtilityListCorr(Qitem name, long twu) {
		this.itemsetName = new ArrayList<Qitem>();
		this.itemsetName.add(name);
		this.sumIutils = 0;
		this.sumIutilsNonZero = 0;
		this.sumRutils = 0;
		this.twu = twu;
		qItemTrans = new ArrayList<QItemTrans>();
		this.bitsetDisjunctiveTIDs = new BitSet();
		this.binarySeq = new BitSet();
	}

	/**
	 * Constructor initializing a QUtilityListCorr with a single item, TWU, and
	 * support.
	 * 
	 * @param name    the quantitative item
	 * @param twu     the transaction-weighted utility
	 * @param support the support value
	 */
	public QUtilityListCorr(Qitem name, long twu, int support) {
		this.itemsetName = new ArrayList<Qitem>();
		this.itemsetName.add(name);
		this.sumIutils = 0;
		this.sumIutilsNonZero = 0;
		this.sumRutils = 0;
		this.twu = twu;
		qItemTrans = new ArrayList<QItemTrans>();
		this.bitsetDisjunctiveTIDs = new BitSet();
		this.binarySeq = new BitSet();
	}

	/**
	 * Constructor initializing a QUtilityListCorr with a single item and
	 * disjunctive bitset of transaction IDs.
	 * 
	 * @param name                  the quantitative item
	 * @param bitsetDisjunctiveTIDs the bitset of transaction IDs where this item
	 *                              appears
	 */
	public QUtilityListCorr(Qitem name, BitSet bitsetDisjunctiveTIDs) {
		this.itemsetName = new ArrayList<Qitem>();
		this.itemsetName.add(name);
		this.sumIutils = 0;
		this.sumIutilsNonZero = 0;
		this.sumRutils = 0;
		this.twu = 0;
		qItemTrans = new ArrayList<QItemTrans>();
		this.binarySeq = new BitSet();
		this.bitsetDisjunctiveTIDs = new BitSet();
		this.bitsetDisjunctiveTIDs = (BitSet) bitsetDisjunctiveTIDs.clone();
	}

	/**
	 * Get the binary sequence bitset.
	 * 
	 * @return the binary sequence as a BitSet
	 */
	public BitSet getBitSet() {
		return this.binarySeq;
	}

	/**
	 * Set the binary sequence bitset.
	 * 
	 * @param binaryseq the binary sequence to set
	 */
	public void setBitSet(BitSet binaryseq) {
		this.binarySeq = binaryseq;
	}

	/**
	 * Get the disjunctive bitset of transaction IDs.
	 * 
	 * @return the disjunctive bitset
	 */
	public BitSet getDisjunctive() {
		return this.bitsetDisjunctiveTIDs;
	}

	/**
	 * Set the disjunctive bitset of transaction IDs.
	 * 
	 * @param Disjunctive the disjunctive bitset to set
	 */
	public void setDisjunctive(BitSet Disjunctive) {
		this.bitsetDisjunctiveTIDs = Disjunctive;
	}

	/**
	 * Get the sum of item utilities.
	 * 
	 * @return the sum of item utilities
	 */
	public long getSumIutils() {
		return this.sumIutils;
	}

	/**
	 * Get the sum of remaining utilities.
	 * 
	 * @return the sum of remaining utilities
	 */
	public long getSumRutils() {
		return this.sumRutils;
	}

	/**
	 * Get the sum of item utilities for non-zero remaining utilities.
	 * 
	 * @return the sum of item utilities with non-zero remaining utilities
	 */
	public long sumIutilsNonZero() {
		return this.sumIutilsNonZero;
	}

	/**
	 * Set the sum of item utilities.
	 * 
	 * @param x the sum of item utilities to set
	 */
	public void setSumIutils(long x) {
		this.sumIutils = x;
	}

	/**
	 * Set the sum of remaining utilities.
	 * 
	 * @param x the sum of remaining utilities to set
	 */
	public void setSumRutils(long x) {
		this.sumRutils = x;
	}

	/**
	 * Mark this utility list as representing a range constraint.
	 */
	public void setRangeAsTrue() {
		this.rangeOrNot = true;
	}

	/**
	 * Get the transaction-weighted utility value.
	 * 
	 * @return the TWU value
	 */
	public long getTwu() {
		return twu;
	}

	/**
	 * Check if this utility list represents a range constraint.
	 * 
	 * @return true if this is a range constraint, false otherwise
	 */
	public boolean isRange() {
		return this.rangeOrNot;
	}

	/**
	 * Set the transaction-weighted utility value.
	 * 
	 * @param twu the TWU value to set
	 */
	public void setTwu(long twu) {
		this.twu = twu;
	}

	/**
	 * Get the list of quantitative items in this itemset.
	 * 
	 * @return the list of quantitative items
	 */
	public ArrayList<Qitem> getItemsetName() {
		return this.itemsetName;
	}

	/**
	 * Get the single quantitative item from this utility list.
	 * 
	 * @return the first quantitative item in the itemset
	 */
	public Qitem getSingleItemsetName() {
		return this.itemsetName.get(0);
	}

	/**
	 * Get the list of transactions containing this itemset.
	 * 
	 * @return the list of quantitative item transactions
	 */
	public ArrayList<QItemTrans> getTransactions() {
		return this.qItemTrans;
	}

	/**
	 * Set the list of transactions for this itemset.
	 * 
	 * @param elements the list of quantitative item transactions to set
	 */
	public void setTransactions(ArrayList<QItemTrans> elements) {
		this.qItemTrans = elements;
	}

	/**
	 * Increase the transaction-weighted utility by a given amount.
	 * 
	 * @param twu the amount to add to TWU
	 */
	public void addTWU(int twu) {
		this.twu += twu;
	}

	/**
	 * Reset the transaction-weighted utility to zero.
	 */
	public void setTWUtoZero() {
		this.twu = 0;
	}

	/**
	 * Get the support count for this itemset.
	 * 
	 * @return the number of transactions containing this itemset
	 */
	public int getSupport() {
		return this.qItemTrans.size();
	}

	/**
	 * Calculate the bond coefficient between actual and disjunctive support.
	 * 
	 * @return the bond value as support divided by disjunctive cardinality
	 */
	public double getBond() {
		return getSupport() / ((double) bitsetDisjunctiveTIDs.cardinality());
	}

	/**
	 * Add a transaction to this utility list with additional TWU information.
	 * 
	 * @param qTid the transaction to add
	 * @param twu  the TWU associated with the transaction
	 */
	public void addTrans(QItemTrans qTid, long twu) {
		if (qTid.getRu() != 0)
			this.sumIutilsNonZero = this.sumIutilsNonZero + qTid.getEu();
		this.sumIutils += qTid.getEu();
		this.sumRutils += qTid.getRu();
		qItemTrans.add(qTid);
		this.twu += twu;
		bitsetDisjunctiveTIDs.set(qTid.getTid());
		this.binarySeq.set(qTid.getTid());
	}

	/**
	 * Add a transaction to this utility list.
	 * 
	 * @param qTid the transaction to add
	 */
	public void addTrans(QItemTrans qTid) {
		if (qTid.getRu() != 0)
			this.sumIutilsNonZero = this.sumIutilsNonZero + qTid.getEu();
		this.sumIutils += qTid.getEu();
		this.sumRutils += qTid.getRu();
		qItemTrans.add(qTid);
		bitsetDisjunctiveTIDs.set(qTid.getTid());
		this.binarySeq.set(qTid.getTid());
	}

	/**
	 * Combine two transactions by summing their utility values.
	 * 
	 * @param a the first transaction
	 * @param b the second transaction
	 * @return a new transaction with combined utilities
	 */
	public QItemTrans QitemTransAdd(QItemTrans a, QItemTrans b) {
		QItemTrans x;
		x = new QItemTrans(a.getTid(), a.getEu() + b.getEu(), a.getRu() + b.getRu());
		return x;
	}

	/**
	 * Get a string representation of this utility list.
	 * 
	 * @return a formatted string containing itemset name, utilities, TWU, support,
	 *         transactions, and bitsets
	 */
	public String toString() {
		String str = "\n=================================\n";
		str += itemsetName + "\r\n";
		str += "sumEU=" + this.sumIutils + " sumRU=" + this.sumRutils + " twu=" + twu + " support="
				+ this.qItemTrans.size() + "\r\n";

		for (int i = 0; i < qItemTrans.size(); i++) {
			str += qItemTrans.get(i).toString() + "\r\n";
		}
		str += "Disj:" + this.bitsetDisjunctiveTIDs.toString();
		str += "Bitset:" + this.binarySeq.toString();
		str += "=================================\n";
		return str;
	}
}