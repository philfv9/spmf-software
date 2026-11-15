/* This file is copyright (c) 2026 Philippe Fournier-Viger
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
 * Do not remove copyright and license information.
 */

package ca.pfv.spmf.algorithms.frequentpatterns.chuim_maxchuim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The internal-node MPUN-lists structures.
 * Pair construction restricts ancestor Lu utilities to descendant TIDs; larger
 * joins match equal smallest-item anchor IDs with one prefix subtraction.
 * No transaction-terminal intersection or all-HUI enumeration is used.
 *
 * @author Philippe Fournier-Viger
 */
final class MPUNList {
	
	/** The itemset in ascending fixed TWU-rank order. */
	final int[] items;
	
	/** The records in increasing anchor-node identifier order. */
	final List<Element> elements = new ArrayList<>();
	
	/** The exact sum of original supporting itemset utilities. */
	long utility;
	
	/** The remaining utility summed over all supporting rows. */
	long remaining;
	
	/** Weak bound on proper forward extensions. */
	long weakUpperBound;
	
	/** The number of original supporting transactions. */
	int support;
	
	/** The exact original-TU sum used as a backward-check hash key. */
	long twu;
	
	/** Whether LPSNonCHUB marked this sibling's branch. */
	boolean localPruned;

	/** A five-field MPUN summary with its anchor and suffix-presence companions. */
	static final class Element {
		/** The smallest-item internal anchor node. */
		final TPUT.Node node;
		/** The creation-order identifier of the anchor. */
		final int nid;
		/** The itemset utility summed over the anchor's rows. */
		final long nu;
		/** The remaining utility summed over the anchor's rows. */
		final long nru;
		/** The common-prefix utility summed over the anchor's rows. */
		final long npu;
		/** The number of original rows passing through the anchor. */
		final int nsup;
		/** The number of retained items later than this itemset on the path. */
		final int remainingItems;

		/**
		 * Create an exact aggregate tuple without collapsing original-row counts.
		 *
		 * @param node           the internal anchor node
		 * @param nu             the aggregate itemset utility
		 * @param nru            the aggregate remaining utility
		 * @param npu            the aggregate prefix utility
		 * @param remainingItems the number of later retained items on the path
		 */
		Element(TPUT.Node node, long nu, long nru, long npu, int remainingItems) {
			this.node = node;
			nid = node.nid;
			this.nu = nu;
			this.nru = nru;
			this.npu = npu;
			nsup = node.lu.size();
			this.remainingItems = remainingItems;
		}
	}

	/** A closed result retained without its working MPUN records. */
	static final class Pattern {
		/** The immutable itemset in TWU-rank order. */
		final int[] items;
		/** The original-row support. */
		final int support;
		/** The exact aggregate itemset utility. */
		final long utility;

		/**
		 * Snapshot a proven closed high-utility itemset.
		 *
		 * @param list the evaluated MPUN-list
		 */
		Pattern(MPUNList list) {
			items = list.items.clone();
			support = list.support;
			utility = list.utility;
		}
	}

	/**
	 * Create a list for one ascending-TWU-rank itemset.
	 *
	 * @param items the immutable item ranks
	 */
	MPUNList(int[] items) {
		this.items = items;
	}

	/**
	 * Add a node record and update the exact Proposition 4 totals.
	 *
	 * @param element the next anchor record in identifier order
	 */
	void add(Element element) {
		elements.add(element);
		utility = Math.addExact(utility, element.nu);
		remaining = Math.addExact(remaining, element.nru);
		support = Math.addExact(support, element.nsup);
		twu = Math.addExact(twu, element.node.twu);
		// Presence generalizes Proposition 4's nru > 0 to accepted zero-valued items.
		if (element.remainingItems > 0) {
			weakUpperBound = Math.addExact(weakUpperBound, Math.addExact(element.nu, element.nru));
		}
	}

	/**
	 * Get the largest mining rank of this nonempty itemset.
	 *
	 * @return the last TWU rank
	 */
	int lastItem() {
		return items[items.length - 1];
	}

	/**
	 * Construct a pair by ancestry or a larger sibling union by equal node IDs.
	 *
	 * @param right the later sibling sharing the same prefix
	 * @return the complete union MPUN-list
	 */
	MPUNList join(MPUNList right) {
		int[] union = Arrays.copyOf(items, items.length + 1);
		union[items.length] = right.lastItem();
		MPUNList result = new MPUNList(union);
		if (items.length == 1) {
			for (Element left : elements) {
				TPUT.Node ancestor = left.node.parent;
				while (ancestor.parent != null && ancestor.rank < right.lastItem()) {
					ancestor = ancestor.parent;
				}
				if (ancestor.rank != right.lastItem()) {
					continue;
				}
				long added = 0;
				long remaining = 0;
				int index = 0;
				for (TPUT.Entry entry : left.node.lu) {
					while (ancestor.lu.get(index).tid < entry.tid) {
						index++;
					}
					TPUT.Entry other = ancestor.lu.get(index);
					added = Math.addExact(added, other.utility);
					remaining = Math.addExact(remaining, other.above);
				}
				result.add(new Element(left.node, Math.addExact(left.nu, added), remaining,
						left.nu, ancestor.depth - 1));
			}
			return result;
		}
		int i = 0;
		int j = 0;
		while (i < elements.size() && j < right.elements.size()) {
			Element left = elements.get(i);
			Element other = right.elements.get(j);
			if (left.nid < other.nid) {
				i++;
			} else if (left.nid > other.nid) {
				j++;
			} else {
				long utility = Math.addExact(Math.subtractExact(left.nu, left.npu), other.nu);
				result.add(new Element(left.node, utility, other.nru, left.nu, other.remainingItems));
				i++;
				j++;
			}
		}
		return result;
	}

	/**
	 * Perform the closure jumping when it is allowed (see the paper)
	 *
	 * @param children the complete retained forward extensions
	 * @return the union MPUN-list with its actual utility
	 */
	MPUNList jumpingClosure(List<MPUNList> children) {
		if (children.isEmpty()) {
			return this;
		}
		int[] union = Arrays.copyOf(items, items.length + children.size());
		for (int i = 0; i < children.size(); i++) {
			union[items.length + i] = children.get(i).lastItem();
		}
		MPUNList result = new MPUNList(union);
		for (int i = 0; i < elements.size(); i++) {
			Element original = elements.get(i);
			long utility = original.nu;
			long lastAdded = 0;
			for (MPUNList child : children) {
				lastAdded = Math.subtractExact(child.elements.get(i).nu, original.nu);
				utility = Math.addExact(utility, lastAdded);
			}
			Element last = children.get(children.size() - 1).elements.get(i);
			result.add(new Element(original.node, utility, last.nru,
					Math.subtractExact(utility, lastAdded), last.remainingItems));
		}
		return result;
	}

	/**
	 * Verify exact inclusion of two distinct-rank sorted itemsets.
	 *
	 * @param subset   the possible subset
	 * @param superset the possible containing itemset
	 * @return true if every subset item occurs in the superset
	 */
	static boolean isSubset(int[] subset, int[] superset) {
		int i = 0;
		int j = 0;
		while (i < subset.length && j < superset.length) {
			if (subset[i] == superset[j]) {
				i++;
				j++;
			} else if (subset[i] > superset[j]) {
				j++;
			} else {
				return false;
			}
		}
		return i == subset.length;
	}
}
