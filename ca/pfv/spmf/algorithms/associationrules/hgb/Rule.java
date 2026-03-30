/* This file is copyright (c) Jayakrushna Sahoo
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

package ca.pfv.spmf.algorithms.associationrules.hgb;

import java.util.Objects;

/**
 * Class representing an association rule with utility information
 *  @see AlgoFHIM_and_HUCI
 *  @see AlgoHGB
 *  @see AlgoHGBAll
 * @author Jayakrushna Sahoo
 */
public class Rule implements Comparable<Rule> {

	/** the antecedent itemset */
	private Itemset antecedentItemset;
	/** the conseuqent itemset */
	private Itemset consequentItemset;
	/** the utility */
	private int utility;
	/** the utility confidence */
	private double utility_confidence;
	/** The antecedent parent */
	private Itemset antcon;
	/** The antecedent utility */
	private int utilityAntecedent;

	/**
	 * Constructor
	 * 
	 * @param antecedentItemset           antecedent itemset
	 * @param consequentItemset           consequent itemset
	 * @param utility            utility
	 * @param utilityConfidence utility confidence
	 * @param parent             parent itemset
	 * @param utilityAntecedent           utility of antecedent
	 */
	public Rule(Itemset antecedentItemset, Itemset consequentItemset, int utility, double utilityConfidence, Itemset parent,
			int utilityAntecedent) {
		this.antecedentItemset = antecedentItemset;
		this.consequentItemset = consequentItemset;
		this.utility = utility;
		this.utility_confidence = utilityConfidence;
		this.antcon = parent;
		this.utilityAntecedent = utilityAntecedent;
	}

	/**
	 * Get the utility
	 * 
	 * @return the utility of the rule
	 */
	public int getUtility() {
		return utility;
	}

	/**
	 * Get the parent
	 * 
	 * @return the parent
	 */
	public Itemset getParent() {
		return antcon;
	}

	/**
	 * Get antecedent utility
	 * 
	 * @return the utility
	 */
	public int getAntecedentUtility() {
		return utilityAntecedent;
	}

	/**
	 * Get the confidence
	 * 
	 * @return the confidence
	 */
	public double getConfidence() {
		return utility_confidence;
	}

	/**
	 * Print to the console
	 */
	public void print() {
		System.out.print(toString());
	}

	@Override
	public String toString() {
		// return itemset1.toString() + " ==> " + itemset2.toString();
		return antecedentItemset.getItems() + " ==> " + consequentItemset.getItems();
	}

	/**
	 * Get the antecedent
	 * 
	 * @return the itemset
	 */
	public Itemset getAntecedent() {
		return antecedentItemset;
	}

	/**
	 * Get the consequent
	 * 
	 * @return the itemset
	 */
	public Itemset getConsequent() {
		return consequentItemset;
	}

	@Override
	public int compareTo(Rule o) {
		// if the same object, return 0.
		if (o == this) {
			return 0;
		}
		// compare the supports
		int compare = this.getUtility() - o.getUtility();
		if (compare != 0) {
			return compare;
		}

		// compare antecedent sizes
		int itemset1sizeA = this.antecedentItemset == null ? 0 : this.antecedentItemset.size();
		int itemset1sizeB = o.antecedentItemset == null ? 0 : o.antecedentItemset.size();
		int compare2 = itemset1sizeA - itemset1sizeB;
		if (compare2 != 0) {
			return compare2;
		}

		int compare5 = this.getAntecedentUtility() - o.getAntecedentUtility();
		if (compare5 != 0) {
			return compare5;
		}
		// compare consequent sizes
		int itemset2sizeA = this.consequentItemset == null ? 0 : this.consequentItemset.size();
		int itemset2sizeB = o.consequentItemset == null ? 0 : o.consequentItemset.size();
		int compare3 = itemset2sizeA - itemset2sizeB;
		if (compare3 != 0) {
			return compare3;
		}

		// compare confidence
		int compare4 = (int) (this.getConfidence() - o.getConfidence());
		if (compare != 0) {
			return compare4;
		}

		// compare hashcodes
		return this.hashCode() - o.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if(o == null) {
			return false;
		}
		Rule ruleX = (Rule) o;
		if (ruleX.antecedentItemset.size() != this.antecedentItemset.size()) {
			return false;
		}
		if (ruleX.consequentItemset.size() != this.consequentItemset.size()) {
			return false;
		}
		for (int i = 0; i < antecedentItemset.size(); i++) {
			if (!Objects.equals(this.antecedentItemset.get(i), ruleX.antecedentItemset.get(i))) {
				return false;
			}
		}
		for (int i = 0; i < consequentItemset.size(); i++) {
			if (!Objects.equals(this.consequentItemset.get(i), ruleX.consequentItemset.get(i))) {
				return false;
			}
		}
		return true;
	}

}
