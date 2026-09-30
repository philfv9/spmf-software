package ca.pfv.spmf.algorithms.sequentialpatterns.snaspm;

import java.util.Arrays;
/* This file is part of the SPMF DATA MINING SOFTWARE
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
 * Copyright (c) Philipe Fournier-Viger et al.
 *
 * Do not remove copyright and license information.
 */
/**
 * A node in the SPC-tree, storing item/event identity, sequence count, pre/post
 * labels, and a children list backed by a plain growing SPCNode[] array. For
 * lookups, a linear scan is used while the node has few children (the common
 * case), and an open-addressing hash map is built lazily only once a node's
 * fan-out exceeds a small threshold. This avoids allocating hash map arrays for
 * the vast majority of nodes, which have very few children.
 * 
 * @author Philippe Fournier-Viger
 */
class SPCNode {
	/** Item value stored at this node. */
	int item;
	/** Event index at which this item occurs. */
	int event;
	/** Number of sequences passing through this node. */
	int count = 0;
	/** Pre-order label assigned during tree traversal. */
	int pre;
	/** Post-order label assigned during tree traversal. */
	int post;
	/** Children stored in a plain array; childCount is the valid count. */
	SPCNode[] childArray = new SPCNode[2];
	/** Number of valid children in childArray. */
	int childCount = 0;

	/**
	 * Above this number of children, a hash map is built to speed up lookups;
	 * below it, a linear scan of childArray is faster and avoids allocation.
	 */
	private static final int LINEAR_SCAN_THRESHOLD = 8;

	/** Keys of the open-addressing map; Long.MIN_VALUE marks empty slots. Lazily allocated. */
	private long[] mapKeys;
	/**
	 * Values of the open-addressing map; each value is an index into childArray.
	 * Lazily allocated together with mapKeys.
	 */
	private int[] mapVals;
	/** Number of entries currently in the map. */
	private int mapSize;
	/** Current capacity of the map (always a power of two). */
	private int mapCap;

	/**
	 * Creates a new SPC-tree node. The hash map is not allocated here; it is
	 * built lazily only if the node ends up with many children.
	 *
	 * @param item  item value
	 * @param event event index
	 */
	SPCNode(int item, int event) {
		this.item = item;
		this.event = event;
	}

	/**
	 * Returns the child node matching the given item and event, or null if absent.
	 * Uses a linear scan for nodes with few children, or a hash lookup once the
	 * map has been built for this node.
	 *
	 * @param item  item value to look up
	 * @param event event index to look up
	 * @return the matching child node, or null
	 */
	SPCNode getChild(int item, int event) {
		if (mapKeys != null) {
			final long key = makeKey(item, event);
			final int mask = mapCap - 1;
			int slot = hash(key) & mask;
			while (true) {
				final long k = mapKeys[slot];
				if (k == Long.MIN_VALUE)
					return null;
				if (k == key)
					return childArray[mapVals[slot]];
				slot = (slot + 1) & mask;
			}
		}
		// linear scan: faster than hashing and allocation-free for the common
		// case of few children
		for (int i = 0; i < childCount; i++) {
			final SPCNode c = childArray[i];
			if (c.item == item && c.event == event)
				return c;
		}
		return null;
	}

	/**
	 * Adds a child node, registering it in the hash map only if one already
	 * exists or if the node's fan-out just crossed the linear-scan threshold.
	 *
	 * @param child the child node to add
	 */
	void addChild(SPCNode child) {
		// grow childArray if needed
		if (childCount == childArray.length) {
			childArray = Arrays.copyOf(childArray, childCount * 2);
		}
		final int childListIdx = childCount;
		childArray[childCount++] = child;

		if (mapKeys != null) {
			// map already built for this node: keep it in sync
			if (mapSize >= (mapCap >>> 1))
				rehash();
			putIntoMap(makeKey(child.item, child.event), childListIdx);
			mapSize++;
		} else if (childCount > LINEAR_SCAN_THRESHOLD) {
			// fan-out just became large enough to justify a hash map
			buildMap();
		}
	}

	/**
	 * Builds the hash map from the current contents of childArray. Called once,
	 * lazily, when a node's fan-out exceeds the linear-scan threshold.
	 */
	private void buildMap() {
		mapCap = 16;
		mapKeys = new long[mapCap];
		mapVals = new int[mapCap];
		Arrays.fill(mapKeys, Long.MIN_VALUE);
		for (int i = 0; i < childCount; i++) {
			putIntoMap(makeKey(childArray[i].item, childArray[i].event), i);
		}
		mapSize = childCount;
	}

	/**
	 * Inserts a key-value pair into the map without checking load factor.
	 *
	 * @param key the map key
	 * @param val the index into childArray
	 */
	private void putIntoMap(long key, int val) {
		final int mask = mapCap - 1;
		int slot = hash(key) & mask;
		while (mapKeys[slot] != Long.MIN_VALUE)
			slot = (slot + 1) & mask;
		mapKeys[slot] = key;
		mapVals[slot] = val;
	}

	/** Doubles the map capacity and re-inserts all existing entries. */
	private void rehash() {
		final int newCap = mapCap * 2;
		final long[] newKeys = new long[newCap];
		final int[] newVals = new int[newCap];
		Arrays.fill(newKeys, Long.MIN_VALUE);
		final int newMask = newCap - 1;
		for (int i = 0; i < mapCap; i++) {
			if (mapKeys[i] != Long.MIN_VALUE) {
				final long key = mapKeys[i];
				int slot = hash(key) & newMask;
				while (newKeys[slot] != Long.MIN_VALUE)
					slot = (slot + 1) & newMask;
				newKeys[slot] = key;
				newVals[slot] = mapVals[i];
			}
		}
		mapKeys = newKeys;
		mapVals = newVals;
		mapCap = newCap;
	}

	/**
	 * Computes a hash for a long key.
	 *
	 * @param key the key to hash
	 * @return the hash value
	 */
	private static int hash(long key) {
		int h = (int) (key ^ (key >>> 32));
		h ^= h >>> 16;
		return h;
	}

	/**
	 * Encodes an item and event into a single long key.
	 *
	 * @param item  item value
	 * @param event event index
	 * @return the combined key
	 */
	private static long makeKey(int item, int event) {
		return ((long) item << 32) | (event & 0xFFFFFFFFL);
	}
}