package ca.pfv.spmf.algorithms.associationrules.nar_miner;
/* This file is copyright (c) Philippe Fournier-Viger
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
 * A negative association rule A =&gt; NOT B with its statistics.
 * @see AlgoNARMiner
 */
class NegativeRule {

    /** Antecedent items (internal IDs, sorted) */
    final int[] antecedent;
    /** Consequent items (internal IDs, sorted) */
    final int[] consequent;
    /** Support of the infrequent itemset A union B */
    final int supportI;
    /** Support of the antecedent A */
    final int supportA;
    /** Confidence = 1 - support(I)/support(A) */
    final double confidence;
    /** Interestingness = confidence / H(I) */
    final double interestingness;

    NegativeRule(int[] antecedent, int[] consequent,
            int supportI, int supportA,
            double confidence, double interestingness) {
        this.antecedent      = antecedent;
        this.consequent      = consequent;
        this.supportI        = supportI;
        this.supportA        = supportA;
        this.confidence      = confidence;
        this.interestingness = interestingness;
    }
}