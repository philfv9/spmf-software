package ca.pfv.spmf.algorithms.associationrules.pnar;
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
 * A pair of disjoint frequent positive itemsets (X, Y) together with
 * the two mixed supports needed for R3 and R4 rule generation.
 * @author Philippe Fournier-Viger 2026
 * @see AlgoPNAR
 */
class PairItemset {

    /** The positive antecedent itemset X. */
    final int[] X;

    /** The positive consequent itemset Y. */
    final int[] Y;

    /** Support of transactions containing X but not Y: supp(X) - supp(X union Y). */
    final int suppXNotY;

    /** Support of transactions containing Y but not X: supp(Y) - supp(X union Y). */
    final int suppNotXY;

    /**
     * Constructs a PairItemset.
     *
     * @param X         the positive itemset X
     * @param Y         the positive itemset Y
     * @param suppXNotY support of X AND NOT(Y)
     * @param suppNotXY support of NOT(X) AND Y
     */
    PairItemset(int[] X, int[] Y, int suppXNotY, int suppNotXY) {
        this.X         = X;
        this.Y         = Y;
        this.suppXNotY = suppXNotY;
        this.suppNotXY = suppNotXY;
    }
}