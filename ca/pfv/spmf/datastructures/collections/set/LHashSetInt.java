package ca.pfv.spmf.datastructures.collections.set;

import java.util.Arrays;
import java.util.NoSuchElementException;

/*
 * Copyright (c) 2023 Philippe Fournier-Viger
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
 * A Hashset, optimized to store integers (primitive type). The hash function is
 * the modulo. The internal structure is an array, and collisions are managed
 * using a custom linked-list. This implementation is not synchronized.
 * 
 * The set implements a rehashing strategy where the number of buckets in the
 * internal hash table is doubled when the load factor is over some threshold
 * (default of 0.8). Rehashing can be deactivated using the method
 * setRehashingEnabled(false).
 * 
 * @author Philippe Fournier-Viger
 * @see SetInt
 */
public class LHashSetInt extends SetInt {

	/** Array to store the data */
	private SetEntry[] buckets;

	/** Number of elements in the map */
	private int elementCount;

	/** DEFAULT CAPACITY */
	private int DEFAULT_BUCKET_COUNT = 100;

	// ================ FOR REHASHING =================
	/** the default maximum load factor */
	private static double DEFAULT_MAXIMUM_LOAD_FACTOR = 0.80d;

	/** the maximum load factor to be used (default or set by the user) */
	private final double maximum_load_factor;

	/** if true, automatic rehashing is enabled */
	private boolean rehashingEnabled = true;
	// ================================================

	/**
	 * Inner node class for storing a key and a pointer to a next node
	 */
	private class SetEntry {
		/** a key */
		int key;
		/** pointer to the next node */
		SetEntry next;

		/**
		 * Constructor
		 * 
		 * @param key a key
		 */
		public SetEntry(int key) {
			this.key = key;
		}
	}

	/**
	 * Constructor
	 */
	public LHashSetInt() {
		buckets = new SetEntry[DEFAULT_BUCKET_COUNT];
		elementCount = 0;

		// ================ FOR REHASHING =================
		maximum_load_factor = DEFAULT_MAXIMUM_LOAD_FACTOR;
		// ================================================
	}

	/**
	 * Constructor
	 * 
	 * @param initialCapacity the initial internal capacity
	 */
	public LHashSetInt(int initialCapacity) {
		elementCount = 0;
		buckets = new SetEntry[initialCapacity];

		// ================ FOR REHASHING =================
		maximum_load_factor = DEFAULT_MAXIMUM_LOAD_FACTOR;
		// ================================================
	}

	/**
	 * Constructor using an existing set
	 * 
	 * @param set a set
	 */
	public LHashSetInt(SetInt set) {
		this(set.size());
		EntryIterator x = set.iterator();
		while (x.hasNext()) {
			this.add(x.next());
		}
	}

	/**
	 * Add all ints from a set
	 * 
	 * @param set a set
	 */
	public void addAll(SetInt set) {
		EntryIterator x = set.iterator();
		while (x.hasNext()) {
			this.add(x.next());
		}
	}

	// ================ FOR REHASHING =================
	/**
	 * Constructor
	 * 
	 * @param initialCapacity the initial internal capacity (bucket number)
	 * @param maximum_load    the maximum load before rehashing is performed (the
	 *                        default is 0.8)
	 */
	public LHashSetInt(int initialCapacity, double maximum_load) {
		elementCount = 0;
		buckets = new SetEntry[initialCapacity];

		// set the value instead of using the default
		maximum_load_factor = maximum_load;
	}

	/**
	 * Indicate whether automatic rehashing should be used when the table is too
	 * full.
	 * 
	 * @param value true to activate or false to deactivate.
	 */
	public void setRehashingEnabled(boolean value) {
		this.rehashingEnabled = value;
	}
	// ================================================

	/**
	 * Clear the map
	 */
	public void clear() {
		Arrays.fill(buckets, null);
		elementCount = 0;
	}

	/**
	 * Get the number of keys in the set
	 * 
	 * @return the number
	 */
	public int size() {
		return elementCount;
	}

	/**
	 * Check if this set is empty
	 * 
	 * @return true if empty. Otherwise, false
	 */
	public boolean isEmpty() {
		return elementCount == 0;
	}

	/**
	 * Calculate hash of a key
	 * 
	 * @param key the key
	 * @return the hash value
	 */
	protected int hash(int key) {
		return key % buckets.length;
	}

	/**
	 * Check if a value is in the structure
	 * 
	 * @param key the key
	 * @return the value true if it is contained. Otherwise, false
	 */
	public boolean contains(int key) {
		int initialIndex = hash(key);

		if (buckets[initialIndex] == null) {
			return false;
		}

		if (buckets[initialIndex].key == key) {
			return true;
		}

		SetEntry next = buckets[initialIndex].next;
		while (next != null) {
			if (next.key == key) {
				return true;
			}
			next = next.next;
		}
		return false;
	}

	/**
	 * Put a new value in the map
	 * 
	 * @param key the key
	 */
	public void add(int key) {
		// ================ FOR REHASHING =================
		// Note: We implement rehashing before adding an element
		// so as to avoid rehashing immediately after adding an element.
		// (this would be a waste of time
		if (rehashingEnabled) {
			resizeAndPerformRehashingIfNeeded();
		}
		// ================================================

		int initialIndex = hash(key);

		// If this key is not in the map
		if (buckets[initialIndex] == null) {
			buckets[initialIndex] = new SetEntry(key);
			elementCount++;
			return;
		}

		// If this key is in the first position
		if (buckets[initialIndex].key == key) {
			// Nothing to do
			return;
		}

		// otherwise check the next position
		SetEntry current = buckets[initialIndex];

		while (current.next != null) {
			if (current.next.key == key) {
				// Nothing else to do
				return;
			}
			current = current.next;
		}

		current.next = new SetEntry(key);
		elementCount++;
		return;
	}

	// ================ FOR REHASHING =================
	private void resizeAndPerformRehashingIfNeeded() {
		// We calculate the load factor
		double loadFactor = (1.0d * elementCount) / buckets.length;
		if (loadFactor >= maximum_load_factor) {
			// Keep the buckets as they are in a temporary variable
			SetEntry[] previousBuckets = buckets;

			// Create a new bucket list that is twice bigger
			int newSize = previousBuckets.length * 2;
			buckets = new SetEntry[newSize];

			// Set the buckets to null
			Arrays.fill(buckets, null);

			for (int i = 0; i < previousBuckets.length; i++) {
				SetEntry currentEntry = previousBuckets[i];
				while (currentEntry != null) {
					// Save the previous entry
					SetEntry nextOne = currentEntry.next;

					// Reinsert the current entry in the new map
					reInsertEntry(currentEntry);

					// Move to the next entry
					currentEntry = nextOne;
				}
			}

		}
	}

	/**
	 * Reinsert a node during the rehashing process
	 * 
	 * @param reinsertedEntry entry that must be reinserted in the map during
	 *                        rehashing
	 */
	private void reInsertEntry(SetEntry reinsertedEntry) {
		reinsertedEntry.next = null;

		int initialIndex = hash(reinsertedEntry.key);

		// If this key is not in the bucket, reinsert it in first position
		if (buckets[initialIndex] == null) {
			buckets[initialIndex] = reinsertedEntry;
			return;
		}
		// otherwise find the last node in the bucket
		SetEntry current = buckets[initialIndex];
		while (current.next != null) {
			current = current.next;
		}

		// And insert the node at the end of the bucket
		current.next = reinsertedEntry;
	}
	// ================================================

	/**
	 * Remove a key from the hash table
	 * 
	 * @param key the key
	 * @return true if removed, false if not found
	 */
	public boolean remove(int key) {
		int initialIndex = hash(key);

		// If this key is not in the map
		if (buckets[initialIndex] == null) {
			return false;
		}

		// If this key is in the first position
		if (buckets[initialIndex].key == key) {
			// Delete by replacing by the next node
			buckets[initialIndex] = buckets[initialIndex].next;
			elementCount--;
			return true;
		}

		// otherwise check the next position
		SetEntry current = buckets[initialIndex];

		while (current.next != null) {
			if (current.next.key == key) {
				// Delete by replacing by the next node
				current.next = current.next.next;
				elementCount--;
				return true;
			}
			current = current.next;
		}
		return false;
	}

	// ================= ITERATOR
	// =======================================================

	/**
	 * Get an iterator on this map
	 * 
	 * @return an iterator
	 */
	public EntryIterator iterator() {
		return new LHashSetIntIterator();
	}

	/**
	 * Iterator for this Map. <br/>
	 * <br/>
	 * Note that the iterator does not check for concurrent modifications and is not
	 * synchronized. It is designed to be used by a single thread, and the map
	 * should not be modified while using the iterator except by calling the
	 * remove() method provided by the iterator. If the map would be modified by
	 * using other methods such as using add() or removeAt(), then the iterator will
	 * not work properly.
	 * 
	 * @author Philippe Fournier-Viger 2023
	 *
	 */
	public class LHashSetIntIterator extends EntryIterator {

		/** index of the bucket for the next entry */
		private int bucketIndexCurrentEntry = 0;

		/** index of the bucket for the next entry */
		private int bucketIndexNextEntry = 0;

		/** Previous entry */
		private SetEntry previousEntry = null;

		/** Next entry */
		private SetEntry currentEntry = null;

		/** Next entry */
		private SetEntry nextEntry = null;

		/**
		 * Constructor
		 */
		public LHashSetIntIterator() {

			// If the map is empty, no need to do anything else
			if (elementCount == 0) {
				return;
			}

			// If the map is not empty, we need to find the next element
			for (; bucketIndexNextEntry < buckets.length; bucketIndexNextEntry++) {
				if (buckets[bucketIndexNextEntry] != null) {
					nextEntry = buckets[bucketIndexNextEntry];
					return;
				}
			}
		}

		/**
		 * Get the next entry
		 * 
		 * @return the next entry or throws NoSuchElementException if there are no next
		 *         entry
		 */
		public int next() {
			if (nextEntry == null) {
				throw new NoSuchElementException();
			}
			// The current entry becomes the previous entry
			previousEntry = currentEntry;

			// The next entry becomes the current entry
			currentEntry = nextEntry;
			bucketIndexCurrentEntry = bucketIndexNextEntry;

			// Then, we need to update the next entry.

			// If there is another entry in the same bucket, this will be the next entry
			if (currentEntry.next != null) {
				nextEntry = currentEntry.next;
				return currentEntry.key;
			}

			// Otherwise, search in the following buckets
			bucketIndexNextEntry++;
			for (; bucketIndexNextEntry < buckets.length; bucketIndexNextEntry++) {
				if (buckets[bucketIndexNextEntry] != null) {
					nextEntry = buckets[bucketIndexNextEntry];
					return currentEntry.key;
				}
			}

			// We did not find!
			nextEntry = null;

			return currentEntry.key;
		}

		/**
		 * Check if there is a next entry
		 * 
		 * @return true if there is. Otherwise, false.
		 */
		public boolean hasNext() {
			return nextEntry != null;
		}

		/**
		 * Remove the current entry
		 * 
		 * @return the entry or throw IllegalStateException if there is no current
		 *         entry.
		 */
		public void remove() {
			// If there are no entry, we cannot do this operation
			if (currentEntry == null) {
				throw new IllegalStateException();
			}

			// If the removed node is the first one in a bucket
			if (buckets[bucketIndexCurrentEntry] == currentEntry) {
				// Update the pointer of this bucket to the next node (if it exist)
				buckets[bucketIndexCurrentEntry] = currentEntry.next;
				currentEntry = previousEntry;
				bucketIndexCurrentEntry = -1;
			}

			// If there was a previous entry and it was pointing to the removed node
			// (which means they are in the same bucket),
			// We need to update the pointer of the previous entry.
			else if (previousEntry != null && previousEntry.next == currentEntry) {
				previousEntry.next = currentEntry.next;
				currentEntry = previousEntry;
			}

			// Decrease the total number of elements
			elementCount--;
		}
	}
	// =====================================================================

}