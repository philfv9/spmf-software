package ca.pfv.spmf.algorithms.associationrules.pnar;

/*
 * This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * This class represents a positive or negative association rule as discovered
 * by the PNAR algorithm (Cornelis et al., 2006).
 *
 * Four types of rules can exist:<br/>
 * R1: X ==> Y (positive antecedent, positive consequent)<br/>
 * R2: ¬X ==> ¬Y (negative antecedent, negative consequent)<br/>
 * R3: X ==> ¬Y (positive antecedent, negative consequent)<br/>
 * R4: ¬X ==> Y (negative antecedent, positive consequent)<br/>
 *
 * @see AlgoPNAR
 * @author Philippe Fournier-Viger, 2026
 */
public class RulePNAR {

	/** Items of the antecedent. */
	final int[] antecedent;

	/** If true, the antecedent is negated. */
	final boolean antecedentNegated;

	/** Items of the consequent. */
	final int[] consequent;

	/** If true, the consequent is negated. */
	final boolean consequentNegated;

	/** Absolute support of the rule (number of transactions). */
	final int absoluteSupport;

	/** Confidence of the rule. */
	final double confidence;

	/**
	 * Constructor.
	 *
	 * @param antecedent        items in the antecedent
	 * @param antecedentNegated true if the antecedent is negated
	 * @param consequent        items in the consequent
	 * @param consequentNegated true if the consequent is negated
	 * @param absoluteSupport   absolute support of the rule
	 * @param confidence        confidence of the rule
	 */
	public RulePNAR(int[] antecedent, boolean antecedentNegated, int[] consequent, boolean consequentNegated,
			int absoluteSupport, double confidence) {
		this.antecedent = antecedent;
		this.antecedentNegated = antecedentNegated;
		this.consequent = consequent;
		this.consequentNegated = consequentNegated;
		this.absoluteSupport = absoluteSupport;
		this.confidence = confidence;
	}

	/**
	 * Return the absolute support of the rule.
	 * 
	 * @return absolute support
	 */
	public int getAbsoluteSupport() {
		return absoluteSupport;
	}

	/**
	 * Return the confidence of the rule.
	 * 
	 * @return confidence
	 */
	public double getConfidence() {
		return confidence;
	}

	/**
	 * Return a human-readable string representation of this rule.
	 * 
	 * @return string representation
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		appendSide(sb, antecedent, antecedentNegated);
		sb.append(" ==> ");
		appendSide(sb, consequent, consequentNegated);
		return sb.toString();
	}

	/**
	 * Appends one side of a rule to the given StringBuilder.
	 *
	 * @param sb      the StringBuilder to append to
	 * @param items   the items for this side
	 * @param negated true if this side is negated
	 */
	private static void appendSide(StringBuilder sb, int[] items, boolean negated) {
		if (negated) {
			sb.append("NOT ");
			for (int i = 0; i < items.length; i++) {
				if (i > 0)
					sb.append(' ');
				sb.append(items[i]);
			}
			sb.append(' ');
		} else {
			for (int i = 0; i < items.length; i++) {
				if (i > 0)
					sb.append(' ');
				sb.append(items[i]);
			}
		}
	}
}