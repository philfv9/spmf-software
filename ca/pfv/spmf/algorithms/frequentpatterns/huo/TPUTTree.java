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

package ca.pfv.spmf.algorithms.frequentpatterns.huo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A transaction prefix utility tree with item-labelled nodes and exact utility cohorts.
 * The tree order is the reverse of the fixed mining order, so all possible
 * forward extensions of an anchor item occur on its ancestor path.
 *
 * @author Philippe Fournier-Viger
 */
final class TPUTTree {
	
	/** The empty prefix at the root of the tree. */
	private final Node root = new Node(null, -1, 0);
	
	/** The item nodes in increasing identifier order, including the root. */
	final List<Node> nodes = new ArrayList<>();
	
	/** The first-created node in each item's header chain, as in Definition 14. */
	private final Map<Integer, Node> headers = new HashMap<>();
	
	/** The last-created node used to append forward header links in constant time. */
	private final Map<Integer, Node> tails = new HashMap<>();
	
	/** The number of item-prefix nodes, including the root but no artificial leaves. */
	int nodeCount = 1;
	
	/** The number of complete revised utility/TU groups, for diagnostics only. */
	int terminalGroupCount;

	/** An item-labelled node shared independently of transaction utilities. */
	static final class Node {
		
		/** The preceding higher-ranked item node, or null for the root. */
		final Node parent;
		
		/** The globally unique item-node identifier. */
		final int nid;
		
		/** The fixed mining rank, or minus one for the root. */
		final int rank;
		
		/** The number of items on the root-to-node path. */
		final int depth;
		
		/** The outgoing edges indexed only by item rank. */
		final Map<Integer, Node> children = new HashMap<>();
		
		/** The exact utility/TU cohorts passing through this node. */
		final List<Cohort> cohorts = new ArrayList<>();
		
		/** The construction-time index of equal utility prefixes and original TUs. */
		final Map<Key, Cohort> cohortIndex = new HashMap<>();
		
		/** The next node for this item in the header table. */
		Node next;
		
		/** The number of original transactions passing through this node. */
		int nsup;

		/**
		 * Create an item-prefix node without depending on its utility values.
		 *
		 * @param parent the preceding item node
		 * @param rank   the fixed mining rank
		 * @param nid    the unique node identifier
		 */
		Node(Node parent, int rank, int nid) {
			this.parent = parent;
			this.rank = rank;
			this.nid = nid;
			depth = parent == null ? 0 : Math.incrementExact(parent.depth);
		}
	}

	/** A weighted exact utility prefix, frozen once singleton lists are initialized. */
	static final class Cohort {
		
		/** The item node owning this exact utility prefix. */
		final Node node;
		
		/** The exact utility prefix at the parent, or null for a root child. */
		final Cohort parent;
		
		/** The stable cohort index within its item node. */
		final int index;
		
		/** The original utility of this node's item in every represented row. */
		final long utility;
		
		/** The original positive transaction utility before filtering. */
		final long tu;
		
		/** The checked sum of utilities on this root-to-node path. */
		final long pathUtility;
		
		/** The number of original rows sharing this exact utility prefix and TU. */
		int nsup;
		
		/** The number of those rows ending at this item node. */
		int endings;

		/**
		 * Create an exact utility cohort with a persistent ancestor-utility path.
		 *
		 * @param node    the owning item node
		 * @param parent  the exact utility prefix at its parent
		 * @param index   the stable cohort index
		 * @param utility the original utility of this item
		 * @param tu      the original transaction denominator
		 */
		Cohort(Node node, Cohort parent, int index, long utility, long tu) {
			this.node = node;
			this.parent = parent;
			this.index = index;
			this.utility = utility;
			this.tu = tu;
			pathUtility = Math.addExact(parent == null ? 0 : parent.pathUtility, utility);
		}

		/**
		 * Locate the exact utility companion of a known ancestor item node.
		 *
		 * @param depth the positive depth of the required ancestor
		 * @return the cohort at that ancestor on the same utility path
		 */
		Cohort atDepth(int depth) {
			Cohort result = this;
			while (result.node.depth > depth) {
				result = result.parent;
			}
			return result;
		}
	}

	/** An exact construction key for equal utility prefixes within one item node. */
	private static final class Key {
		/** The stable parent-cohort index, or minus one at a root child. */
		final int parentIndex;
		/** The original utility of the current item. */
		final long utility;
		/** The original transaction denominator. */
		final long tu;

		/**
		 * Create an exact utility-prefix key without copying an entire utility vector.
		 *
		 * @param parentIndex the stable parent-cohort index
		 * @param utility     the original current-item utility
		 * @param tu          the original transaction denominator
		 */
		Key(int parentIndex, long utility, long tu) {
			this.parentIndex = parentIndex;
			this.utility = utility;
			this.tu = tu;
		}

		/**
		 * Compute the hash of an exact utility-prefix key.
		 *
		 * @return the hash code
		 */
		@Override
		public int hashCode() {
			return 31 * (31 * parentIndex + Long.hashCode(utility)) + Long.hashCode(tu);
		}

		/**
		 * Compare the complete utility prefix and original denominator.
		 *
		 * @param other the object to compare
		 * @return true if both keys describe the same exact cohort
		 */
		@Override
		public boolean equals(Object other) {
			if (!(other instanceof Key)) {
				return false;
			}
			Key key = (Key) other;
			return parentIndex == key.parentIndex && utility == key.utility && tu == key.tu;
		}
	}

	/**
	 * Create an empty item-prefix utility tree.
	 */
	TPUTTree() {
		nodes.add(root);
	}

	/**
	 * Insert a revised row in reverse mining order with its original denominator.
	 *
	 * @param transaction the original parsed transaction
	 * @param revised     the retained items in ascending mining order
	 * @param merge       whether exact utility/TU cohorts may share multiplicities
	 */
	void add(HUOUtility.Transaction transaction, HUOUtility.RevisedTransaction revised, boolean merge) {
		root.nsup = Math.incrementExact(root.nsup);
		Node node = root;
		Cohort prefix = null;
		for (int i = revised.size - 1; i >= 0; i--) {
			int rank = revised.rank(i);
			Node child = node.children.get(rank);
			if (child == null) {
				child = new Node(node, rank, nodeCount);
				nodeCount = Math.incrementExact(nodeCount);
				node.children.put(rank, child);
				nodes.add(child);
				Node previous = tails.put(rank, child);
				if (previous == null) {
					headers.put(rank, child);
				} else {
					previous.next = child;
				}
			}
			long utility = revised.utility(transaction, i);
			Key key = merge ? new Key(prefix == null ? -1 : prefix.index, utility, transaction.total) : null;
			Cohort cohort = merge ? child.cohortIndex.get(key) : null;
			if (cohort == null) {
				cohort = new Cohort(child, prefix, child.cohorts.size(), utility, transaction.total);
				child.cohorts.add(cohort);
				if (merge) {
					child.cohortIndex.put(key, cohort);
				}
			}
			cohort.nsup = Math.incrementExact(cohort.nsup);
			child.nsup = Math.incrementExact(child.nsup);
			prefix = cohort;
			node = child;
		}
		if (prefix != null) {
			if (prefix.endings == 0) {
				terminalGroupCount = Math.incrementExact(terminalGroupCount);
			}
			prefix.endings = Math.incrementExact(prefix.endings);
		}
	}

	/**
	 * Construct item-node singleton lists and exact pair supports from header chains.
	 *
	 * @param singletons   the support-ordered singleton headers
	 * @param cooccurrence the pairwise support map used by Strategy 3
	 */
	void initializeLists(List<PUONList> singletons, Map<Long, Integer> cooccurrence) {
		for (int rank = 0; rank < singletons.size(); rank++) {
			PUONList singleton = singletons.get(rank);
			for (Node node = headers.get(rank); node != null; node = node.next) {
				List<PUONList.Value> values = new ArrayList<>(node.cohorts.size());
				for (Cohort cohort : node.cohorts) {
					values.add(new PUONList.Value(cohort, cohort.utility,
							Math.subtractExact(cohort.pathUtility, cohort.utility), 0, node.depth - 1));
				}
				singleton.add(new PUONList.Element(node, values));
				for (Node ancestor = node.parent; ancestor.parent != null; ancestor = ancestor.parent) {
					long pair = pairKey(rank, ancestor.rank);
					Integer count = cooccurrence.get(pair);
					cooccurrence.put(pair, count == null ? node.nsup : Math.addExact(count, node.nsup));
				}
			}
		}
	}

	/**
	 * Encode an ordered pair of item ranks without a bit-mask item-count limit.
	 *
	 * @param left  the earlier support rank
	 * @param right the later support rank
	 * @return the exact ordered pair key
	 */
	static long pairKey(int left, int right) {
		return ((long) left << 32) | (right & 0xffffffffL);
	}
}
