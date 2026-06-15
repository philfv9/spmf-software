package ca.pfv.spmf.algorithms.frequentpatterns.witfwi;

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
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
* 
* Do not remove copyright or license information from this file.
*/

import java.util.BitSet;

/**
 * This class represents a node used by the WIT-FWI algorithms. Despite the name
 * "WIT-tree" used in the paper, no explicit tree data structure is ever built;
 * the tree is a conceptual illustration of the search space.
 *
 * Vo, B., Coenen, F., Le, B. (2013). A new method for mining Frequent Weighted
 * Itemsets based on WIT-trees. Expert Systems with Applications, 40(4),
 * 1256-1264.
 *
 * In this implementation, tidsets are stored as Bitset objects, and cardinality
 * is cached.
 *
 * @see AlgoWIT_FWI
 * @see AlgoWIT_FWI_MOD
 * @author Philippe Fournier-Viger
 */
public class WITNode {

	/** the itemset represented by this node (sorted array of item integers) */
	public final int[] itemset;

	/**
	 * The tidset t(itemset): the BitSet of transaction IDs that contain this
	 * itemset. Bit position "tid" is set if and only if transaction "tid" contains
	 * the itemset.
	 */
	public final BitSet tidset;

	/** the weighted support ws(itemset), normalized to [0.0, 1.0] */
	public final double weightedSupport;

	/** Cached cardinality of the tidset, i.e. |t(itemset)|. */
	public final int tidsetCardinality;

	/**
	 * Constructs a WIT node from a pre-built BitSet tidset. The cardinality is
	 * computed once and cached. The BitSet is stored directly (not copied).
	 *
	 * @param itemset         the itemset represented by this node (sorted)
	 * @param tidset          the BitSet of transaction IDs containing the itemset
	 * @param weightedSupport the weighted support of the itemset
	 */
	public WITNode(int[] itemset, BitSet tidset, double weightedSupport) {
		this.itemset = itemset;
		this.tidset = tidset;
		this.weightedSupport = weightedSupport;
		// Compute cardinality once and cache it.
		this.tidsetCardinality = tidset.cardinality();
	}

	/**
	 * Copy constructor to clone a WIT node. Creates independent copies of both the
	 * itemset array and the tidset BitSet. The cached cardinality is copied
	 * directly from the source node.
	 *
	 * @param node the node to copy
	 */
	public WITNode(WITNode node) {
		this.itemset = node.itemset.clone();
		this.tidset = (BitSet) node.tidset.clone();
		this.weightedSupport = node.weightedSupport;
		this.tidsetCardinality = node.tidsetCardinality;
	}
	
    /**
     * Constructs a WITNode with all fields supplied by the caller.
     * The cardinality is passed in explicitly so the caller can reuse a value
     * it has already computed (e.g. via {@code BitSet.cardinality()} on the
     * freshly intersected tidset) rather than triggering a second scan.
     *
     * @param itemset           the primitive int array representing the itemset
     * @param tidset            the BitSet encoding t(itemset)
     * @param weightedSupport   the normalized weighted support in [0.0, 1.0]
     * @param tidsetCardinality the pre-computed cardinality of {@code tidset}
     */
    WITNode(int[] itemset, BitSet tidset, double weightedSupport, int tidsetCardinality) {
        this.itemset            = itemset;
        this.tidset             = tidset;
        this.weightedSupport    = weightedSupport;
        this.tidsetCardinality  = tidsetCardinality;
    }
}