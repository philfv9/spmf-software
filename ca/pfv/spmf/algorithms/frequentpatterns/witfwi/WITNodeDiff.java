package ca.pfv.spmf.algorithms.frequentpatterns.witfwi;

/* This file is copyright (c) 2008-2024 Philippe Fournier-Viger
*
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see http://www.gnu.org/licenses/.
* Do not remove copyright or license information from this file.
*/

import java.util.BitSet;

/**
 * Represents a node in the WIT-tree equivalence class for the Diffset
 * algorithm.
 *
 * At level 1, tidsetOrDiffset holds the full BitSet Tidset t(X). At level 2 and
 * beyond, it holds the Diffset d(X) = t(parent) \ t(X). The algorithm method
 * fwiExtendDiff() distinguishes the two cases via the isLevel1 parameter it
 * carries explicitly, so no flag needs to be stored per node.
 *
 * @see AlgoWIT_FWI_DIFF
 */
class WITNodeDiff {

	/**
	 * The itemset represented by this node. Items are stored in the order they were
	 * appended during the join; the last element is the distinguishing item for
	 * this equivalence class.
	 */
	final int[] itemset;

	/**
	 * At level 1: the full Tidset t(X) encoded as a BitSet. At level 2+: the
	 * Diffset d(X) = t(parent) \ t(X) encoded as a BitSet. Which interpretation
	 * applies is tracked by the isLevel1 parameter of the WTI-DIFF algorithm.
	 */
	final BitSet tidsetOrDiffset;

	/**
	 * The weighted support ws(X), defined as: ws(X) = sum_{tk in t(X)} tw(tk) /
	 * sum_{tk in D} tw(tk). Stored as a ratio in [0.0, 1.0].
	 */
	final double weightedSupport;

	/**
	 * Constructs a WITNodeDiff with the given itemset, tidset or diffset, and
	 * weighted support. The caller is responsible for supplying the correct BitSet
	 * interpretation (Tidset at level 1, Diffset at level 2+).
	 *
	 * @param itemset         the primitive int array representing the itemset
	 * @param tidsetOrDiffset the BitSet encoding t(X) at level 1 or d(X) at level
	 *                        2+
	 * @param weightedSupport the normalized weighted support in [0.0, 1.0]
	 */
	WITNodeDiff(int[] itemset, BitSet tidsetOrDiffset, double weightedSupport) {
		this.itemset = itemset;
		this.tidsetOrDiffset = tidsetOrDiffset;
		this.weightedSupport = weightedSupport;
	}
}