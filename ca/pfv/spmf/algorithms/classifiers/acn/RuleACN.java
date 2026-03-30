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
package ca.pfv.spmf.algorithms.classifiers.acn;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import ca.pfv.spmf.algorithms.ArraysAlgos;
import ca.pfv.spmf.algorithms.classifiers.data.Dataset;
import ca.pfv.spmf.algorithms.classifiers.data.Instance;
import ca.pfv.spmf.algorithms.classifiers.general.Rule;

/**
 * Base class for a rule of ACN. It extends base Rule, but add functionalities
 * to negate items
 * 
 * @see AlgoACN
 */
public class RuleACN extends Rule implements Serializable {
	/**
	 * UID
	 */
	private static final long serialVersionUID = 5182408138874851612L;

	/**
	 * Determines which items are negated in the antecedent
	 */
	private List<Boolean> negatedItems;

	/**
	 * Value for the pearson coeffient for the current rule
	 */
	private double pearson;

	/**
	 * Default Constructor.
	 */
	public RuleACN() {
		super();
		pearson = Double.NaN;
		this.negatedItems = new ArrayList<Boolean>();
	}

	/**
	 * Constructor to clone a rule
	 * 
	 * @param a rule
	 */
	public RuleACN(RuleACN rule) {
		super(rule.klass);
		add(rule.antecedent);

		supportAntecedent = rule.supportAntecedent;
		supportKlass = rule.supportKlass;
		supportRule = rule.supportRule;
		negatedItems = new ArrayList<Boolean>(rule.negatedItems);
	}
	
	/**
	 * Constructor 
	 * 
	 * @param antecedent        itemset forming the antecedent of the rule
	 * @param negativeItems  the boolean values for negative items
	 * @param klass the class
	 */
	public RuleACN(short[] antecedent, List<Boolean> negativeItems, short klass) {
		super(klass);
		add(antecedent);
		pearson = Double.NaN;
		this.negatedItems = new ArrayList<Boolean>(negativeItems);
	}


	/**
	 * Parameterized Constructor.
	 * 
	 * @param klass Associated output of the rule
	 */
	public RuleACN(short klass) {
		super(klass);
		this.negatedItems = new ArrayList<Boolean>();
	}

	/**
	 * It computes the supports and the pearson coefficient for the current rule.
	 * 
	 * @param train Given training dataset to be able to calculate supports
	 */
	public void evaluate(Dataset train) {
		this.supportAntecedent = 0;
		this.supportRule = 0;
		this.supportKlass = 0;

		for (Instance instance: train.getInstances()) {
			Short[] items = instance.getItems();

			Boolean matchAntecedent = matching(items);

			Boolean matchConsequent = instance.getKlass() == this.klass;

			if (matchConsequent) {
				this.supportKlass++;
			}

			if (matchAntecedent) {
				this.supportAntecedent++;
			}

			if (matchAntecedent && matchConsequent)
				this.supportRule++;
		}

		double supR = supportRule / ((double) train.getInstances().size());
		double supA = supportAntecedent / ((double) train.getInstances().size());
		double supK = supportKlass / ((double) train.getInstances().size());
		double notSupA = 1.0 - supA;
		double notSupK = 1.0 - supK;
		pearson = (supR - supA * supK) / Math.sqrt(supA * supK * notSupA * notSupK);
	}

	@Override
	public void add(short[] item) {
		super.add(item);

		this.negatedItems = new ArrayList<Boolean>();
		for (int i = 0; i < item.length; i++)
			this.negatedItems.add(false);
	}

	@Override
	public void add(short item) {
		super.add(item);
		this.negatedItems.add(false);
	}

	/**
	 * Negates an item contained in the antecedent of the rule
	 * 
	 * @param index of the item being negated
	 */
	public void negateItem(int index) {
		this.negatedItems.set(index, true);
	}

	/**
	 * Pearson coeffient for this rule
	 * 
	 * @return he coefficient value for this rule
	 */
	public double getPearson() {
		return pearson;
	}

	/**
	 * Count the number of negated items in the rule
	 * 
	 * @return number of negated items in the rule
	 */
	public int getNegativeItems() {
		return (int) this.negatedItems.stream().filter(p -> p == true).count();
	}



	/*
	 * (non-Javadoc)
	 * 
	 * @Override
	 */
	public boolean matching(Short[] example) {
		if (antecedent.isEmpty()) {
			return true;
		}

		if (!this.isANegativeRule()) {
			return super.matching(example);
		} else {
			List<Short> positiveAntecedent = new ArrayList<Short>();
//			List<Short> exampleA = Arrays.asList(example);

			for (int i = 0; i < this.antecedent.size(); i++) {
				if (this.negatedItems.get(i)) {
					short negativeItem = this.antecedent.get(i);

					// If contain negative Item, it cannot match this example
					for(short element : example) {
						if(element == negativeItem) {
							return false;
						}
					}
					
////				if (exampleA.contains(negativeItem)) {
//					return false;
					
				} else {
					positiveAntecedent.add(this.antecedent.get(i));
				}
			}
			return ArraysAlgos.isSubsetOf(positiveAntecedent, example);
		}
	}
	
	/**
	 * Check if the i-th item of the antecedent is negative
	 * @param i the position
	 * @return true if negative, otherwise false
	 */
	public boolean isIthAntecedentItemNegative(int i) {
		return negatedItems.get(i);
	}

	/**
	 * Check if this is a rule with some negative items
	 * @return true if there are some negative items in this rule.
	 */
	public boolean isANegativeRule() {
		return this.getNegativeItems() > 0;
	}
	
	@Override
	public String getMeasuresToString() {
		return " #SUP: " + getSupportRule() + " #CONF: " + getConfidence() + " #PEARSON: " + this.getPearson();
	}
}
