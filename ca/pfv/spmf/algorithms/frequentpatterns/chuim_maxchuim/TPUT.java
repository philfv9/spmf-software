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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The transaction prefix utility tree structure.
 * Nodes are shared by descending-TWU item prefixes, not by utility vectors.
 * Lu keeps original row utilities with renumbered RD TIDs; an exact cached
 * ancestor sum avoids repeatedly walking Lu lists when constructing pairs.
 *
 * @author Philippe Fournier-Viger
 */
final class TPUT {
	
	/** The empty prefix at the root. */
	final Node root = new Node(null, -1, 0);
	
	/** All nodes in creation order, including root zero. */
	final List<Node> nodes = new ArrayList<>();
	
	/** Header pointers to the first-created node of each mining rank. */
	final Node[] headers;
	
	/** Tail pointers used to append header links in constant time. */
	private final Node[] tails;

	/** One item node with parent link, next link and Lu list. */
	static final class Node {
		
		/** The parent node, or null at the root. */
		final Node parent;
		
		/** The item's ascending-TWU mining rank. */
		final int rank;
		
		/** The creation-order node identifier. */
		final int nid;
		
		/** The number of item nodes on the root-to-node path. */
		final int depth;
		
		/** The outgoing item-labelled edges. */
		final Map<Integer, Node> children = new HashMap<>();
		
		/** The original item utilities ordered by renumbered transaction ID. */
		final List<Entry> lu = new ArrayList<>();
		
		/** The next-created node with the same rank. */
		Node next;
		
		/** The sum of the node's original item utilities. */
		long utility;
		
		/** The sum of utilities strictly above this node over its rows. */
		long remaining;
		
		/** The sum of original TUs over this node's rows. */
		long twu;

		/**
		 * Create an item-prefix node with its insertion-order identifier.
		 *
		 * @param parent the parent item node
		 * @param rank   the ascending-TWU rank
		 * @param nid    the unique insertion identifier
		 */
		Node(Node parent, int rank, int nid) {
			this.parent = parent;
			this.rank = rank;
			this.nid = nid;
			depth = parent == null ? 0 : Math.incrementExact(parent.depth);
		}
	}

	/** One original-row Lu entry with a cached exact ancestor utility. */
	static final class Entry {
		
		/** The one-based RD transaction identifier. */
		final int tid;
		
		/** The original utility of this node's item in this row. */
		final long utility;
		
		/** The sum of utilities strictly above this item in the row. */
		final long above;

		/**
		 * Retain an original-row utility and its exact ancestor-sum companion.
		 *
		 * @param tid     the one-based revised transaction identifier
		 * @param utility the original item utility
		 * @param above   the exact ancestor utility sum
		 */
		Entry(int tid, long utility, long above) {
			this.tid = tid;
			this.utility = utility;
			this.above = above;
		}
	}

	/**
	 * Construct the tree by scanning the sorted reduced dataset once.
	 *
	 * @param rows      the nonempty RD rows in insertion order
	 * @param itemCount the number of retained mining ranks
	 */
	TPUT(List<CHUIMUtility.Row> rows, int itemCount) {
		headers = new Node[itemCount];
		tails = new Node[itemCount];
		nodes.add(root);
		int tid = 0;
		for (CHUIMUtility.Row row : rows) {
			tid = Math.incrementExact(tid);
			Node parent = root;
			long above = 0;
			for (int i = row.items.length - 1; i >= 0; i--) {
				int rank = row.items[i];
				Node node = parent.children.get(rank);
				if (node == null) {
					node = new Node(parent, rank, nodes.size());
					parent.children.put(rank, node);
					nodes.add(node);
					if (tails[rank] == null) {
						headers[rank] = node;
					} else {
						tails[rank].next = node;
					}
					tails[rank] = node;
				}
				long utility = row.utilities[i];
				node.lu.add(new Entry(tid, utility, above));
				node.utility = Math.addExact(node.utility, utility);
				node.remaining = Math.addExact(node.remaining, above);
				node.twu = Math.addExact(node.twu, row.tu);
				above = Math.addExact(above, utility);
				parent = node;
			}
		}
	}

	/**
	 * Construct singleton MPUN-lists from insertion-ordered item headers.
	 *
	 * @return all singleton lists in ascending mining order
	 */
	List<MPUNList> singletons() {
		List<MPUNList> result = new ArrayList<>();
		for (int rank = 0; rank < headers.length; rank++) {
			MPUNList list = new MPUNList(new int[] {rank});
			for (Node node = headers[rank]; node != null; node = node.next) {
				list.add(new MPUNList.Element(node, node.utility, node.remaining, 0, node.depth - 1));
			}
			result.add(list);
		}
		return result;
	}
}
