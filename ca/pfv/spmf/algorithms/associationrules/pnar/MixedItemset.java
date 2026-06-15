package ca.pfv.spmf.algorithms.associationrules.pnar;

import java.util.Arrays;
/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * An itemset that may contain both positive and negative items. Used internally
 * during the generation of P3 and P4 frequent itemsets.
 * 
 * @author Philippe Fournier-Viger
 * @see AlgoPNAR
 */
class MixedItemset {
	/** Items whose *absence* is required (the ¬X part). Sorted ascending. */
	int[] negative; // may be empty array but never null
	
	/** Items whose *presence* is required (the Y part). Sorted ascending. */
	int[] positive; // may be empty array but never null
	
	/** Cached absolute support (-1 = not yet computed). */
	int support;

	MixedItemset(int[] negative, int[] positive) {
		this.negative = negative;
		this.positive = positive;
		this.support = -1;
	}

	/** Total number of items (|negative| + |positive|). */
	int totalSize() {
		return negative.length + positive.length;
	}

	@Override
	public String toString() {
		return "NOT" + Arrays.toString(negative) + " " + Arrays.toString(positive);
	}
}