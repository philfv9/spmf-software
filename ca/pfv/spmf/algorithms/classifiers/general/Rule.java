/**
 * This file is part of SPMF
 *
 * Copyright SPMF, LAC (C)
 *   
 * LAC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details. You should have 
 * received a copy of the GNU General Public License along with 
 * this program.  If not, see http://www.gnu.org/licenses/
 */
package ca.pfv.spmf.algorithms.classifiers.general;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.algorithms.ArraysAlgos;

/**
 * Represent classification rules
 */
public abstract class Rule implements Serializable{

	/**
	 * UID
	 */
	private static final long serialVersionUID = -1232107487609781014L;

	/**
	 * Array with all the items forming the antecedent
	 */
	protected List<Short> antecedent;

	/**
	 * Consequent for the current rule
	 */
	protected short klass;

	/**
	 * Frequency of occurrence relative to the dataset for the antecedent
	 */
	protected long supportAntecedent;

	/**
	 * Frequency of occurrence relative to the dataset for the klass
	 */
	protected long supportKlass;

	/**
	 * Frequency of occurrence relative to the dataset for the whole rule
	 */
	protected long supportRule;

	/**
	 * Main constructor
	 */
	public Rule() {
		this.antecedent = new ArrayList<Short>();
		this.supportRule = 0;
		this.supportAntecedent = 0;
		this.supportKlass = 0;
	}

	/**
	 * Constructor to clone a rule
	 * 
	 * @param rule a rule
	 */
	public Rule(Rule rule) {
		klass = rule.klass;
		add(rule.antecedent);
		supportAntecedent = rule.supportAntecedent;
		supportKlass = rule.supportKlass;
		supportRule = rule.supportRule;
	}

	/**
	 * Constructor
	 * 
	 * @param klass consequent of the rule
	 */
	public Rule(short klass) {
		this();
		this.klass = klass;
	}

	/**
	 * Constructor
	 * 
	 * @param antecedent antecedent of the rule
	 * @param klass      consquent of the rule
	 */
	public Rule(short[] antecedent, short klass) {
		this(klass);
		add(antecedent);
	}

	/**
	 * Constructor
	 * 
	 * @param antecedent antecedent of the rule
	 * @param klass      consquent of the rule
	 */
	public Rule(Short[] antecedent, short klass) {
		this(klass);
		add(antecedent);
	}

	/**
	 * Returns the antecedent of the rule
	 * 
	 * @return antecedent of the rule
	 */
	public List<Short> getAntecedent() {
		return this.antecedent;
	}
	
	/**
	 * Check if this is a rule with some negative items
	 * @return true if there are some negative items in this rule.
	 */
	public boolean isANegativeRule() {
		return false;
	}
	
	/**
	 * Check if the i-th item of the antecedent is negative
	 * @param i the position
	 * @return true if negative, otherwise false
	 */
	public boolean isIthAntecedentItemNegative(int i) {
		return false;
	}
	
	
	/**
	 * Returns true if the i-th item of the antecedent is negative
	 * 
	 * @return antecedent of the rule
	 */
	public List<Short> isItemOfAntecedentNegative() {
		return this.antecedent;
	}

	/**
	 * Get confidence for the current evaluated rule
	 * 
	 * @return confidence or 0 if supportAntecedent is 0
	 */
	public double getConfidence() {
		double confidence = (double) this.supportRule / (double) this.supportAntecedent;

		return this.supportAntecedent > 0.0 ? confidence : 0.0;
	}

	/**
	 * Get support for the whole rule
	 * 
	 * @return relative support for the current rule
	 */
	public long getSupportRule() {
		return this.supportRule;
	}

	/**
	 * Get support for the class (consequent)
	 * 
	 * @return relative support for the consequent
	 */
	public long getSupportKlass() {
		return this.supportKlass;
	}

	/**
	 * Function to check if a given example fires a rule
	 * 
	 * @param example Example to be classified
	 * @return true if rule was fired, false otherwise
	 */
	public boolean matching(Short[] instance) {
		if (antecedent.isEmpty())
			return true;
		// ** Added the line below to avoid error (Philippe) 
		// --- Might check later why this method can receive NULL **
		if(instance == null) {
			return true;
		}
		return ArraysAlgos.isSubsetOf(antecedent, instance);
	}

	/**
	 * Function to check if a rule is equal to another given.
	 * 
	 * @param other Rule to compare with current
	 * @return true if they are equal, false otherwise
	 */
	@Override
	public boolean equals(Object other) {
		if (other == null) {
			return false;
		}
		Rule rule = (Rule) other;

		if (this.klass != rule.getKlass())
			return false;

		if (this.antecedent.size() != rule.getAntecedent().size())
			return false;

		for (int i = 0; i < this.antecedent.size(); i++)
			if (this.antecedent.get(i) != rule.antecedent.get(i))
				return false;

		return true;
	}

	/**
	 * Add a set of items to the antecedent
	 * 
	 * @param itemset Element to be added
	 */
	public void add(Short[] itemset) {
		for (int i = 0; i < itemset.length; i++)
			this.antecedent.add(itemset[i]);
	}

	/**
	 * Add a set of items to the antecedent
	 * 
	 * @param itemset Element to be added
	 */
	public void add(List<Short> itemset) {
		this.antecedent.addAll(itemset);
	}

	/**
	 * Add a set of items to the antecedent
	 * 
	 * @param itemset to be added to the antecedent
	 */
	public void add(short[] itemset) {
		for (int i = 0; i < itemset.length; i++)
			this.antecedent.add(itemset[i]);
	}

	/**
	 * Add a new item to the antecedent
	 * 
	 * @param item to be added
	 */
	public void add(short item) {
		this.antecedent.add(item);
	}

	/**
	 * It returns the item located in the given position of the antecedent
	 * 
	 * @param index Position of the requested item into the antecedent
	 * @return The requested item of the antecedent
	 */
	public short get(int index) {
		return this.antecedent.get(index);
	}

	/**
	 * It returns the size of the antecedent
	 * 
	 * @return Number of items in the antecedent
	 */
	public int size() {
		return this.antecedent.size();
	}

	/**
	 * It returns the consequent (class)
	 * 
	 * @return short output class
	 */
	public short getKlass() {
		return this.klass;
	}

	/**
	 * It returns the support of the antecedent
	 * 
	 * @return support of the antecedent
	 */
	public long getSupportAntecedent() {
		return this.supportAntecedent;
	}

	/**
	 * Increment the support for the antecedent
	 */
	public void incrementSupportAntecedent() {
		this.supportAntecedent++;
	}

	/**
	 * Increment the support for the rule
	 */
	public void incrementSupportRule() {
		this.supportRule++;
	}

	/**
	 * Function which sets the rule's klass.
	 * 
	 * @param klass rule's klass
	 */
	public void setKlass(short klass) {
		this.klass = klass;
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		List<Short> total = new ArrayList<Short>(antecedent);
		total.add(klass);
		return total.hashCode();
	}

	/**
	 * Set the support for the antecedent rule
	 * 
	 * @param supportAntecedent support for the antecedent of the rule
	 */
	public void setSupportAntecedent(long supportAntecedent) {
		this.supportAntecedent = supportAntecedent;
	}

	/**
	 * Set the support for the current rule
	 * 
	 * @param supportRule support for the rule
	 */
	public void setSupportRule(long supportRule) {
		this.supportRule = supportRule;
	}

	/**
	 * Set the support for the consequent of the current rule
	 * 
	 * @param supportKlass support for the consequent
	 */
	public void setSupportKlass(long supportKlass) {
		this.supportKlass = supportKlass;
	}

	/**
	 * Method used to calculate errors in PER
	 * 
	 * @param N number of hits
	 * @param e number of errors
	 * @return upper limit with confidence of 0.25
	 */
	protected static double errors(double N, double e) {
		double CF = 0.25;

		double[] VAL = { 0, 0.000000001, 0.00000001, 0.0000001, 0.000001, 0.00001, 0.00005, 0.0001,
				0.0005, 0.001, 0.005, 0.01, 0.05, 0.10, 0.20, 0.40, 1.00 };
		
		double[] DEV = { 100, 6.0, 5.61, 5.2, 4.75, 4.26, 3.89, 3.72, 3.29, 3.09, 2.58, 2.33, 1.65,
				1.28, 0.84, 0.25, 0.00 };
		double Val0;
		double Pr; 
		double coeff;
		int i = 0;

		while (CF > VAL[i]) {
			i++;
		}

		coeff = DEV[i - 1] + (DEV[i] - DEV[i - 1]) * (CF - VAL[i - 1]) / (VAL[i] - VAL[i - 1]);
		coeff = coeff * coeff;

		if (e == 0) {
			return N * (1 - Math.exp(Math.log(CF) / N));
		} else {
			if (e < 0.9999) {
				Val0 = N * (1 - Math.exp(Math.log(CF) / N));
				return Val0 + e * (errors(N, 1.0) - Val0);
			} else {
				if (e + 0.5 >= N) {
					return 0.67 * (N - e);
				} else {
					Pr = (e + 0.5 + coeff / 2 + Math.sqrt(coeff * ((e + 0.5) * (1 - (e + 0.5) / N) + coeff / 4)))
							/ (N + coeff);

					return (N * Pr - e);
				}
			}
		}
	}
	
	/**
	 * Check if a rule can be combined with another.
	 * This method is used by Apriori-based algorithms to generate candidate rules that
	 * are larger.
	 * Four conditions must be met: (1) they have the same class, (2) the same antecedent size,
	 * (3) they share all but one item and that item is the last one according to
	 * the lexicographical order.
	 * 
	 * @param other
	 * @return true, if rules can be combined
	 */
	public boolean isCombinable(Rule other) {
		if (this.getKlass() != other.getKlass()) {
			return false;
		}

		if (this.size() != other.size()) {
			return false;
		}

		//For each position except the last one
		for (int i = 0; i < this.size() - 1; i++) {
			// if the item (attribute value) is not the same
			if (this.get(i) != other.get(i)) {
				return false;
			}
		}

		// Now compare the last items
		short itemi = this.get(this.size() - 1);
		short itemj = other.get(other.size() - 1);

		return (itemi < itemj);
	}


	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return this.antecedent.toString() + " -> " + this.getKlass() + getMeasuresToString();
	}
	
	public abstract String getMeasuresToString();

}
