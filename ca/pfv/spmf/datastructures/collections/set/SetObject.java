package ca.pfv.spmf.datastructures.collections.set;

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
 * Abstract class defining the operations that a hashset of objects should have.
 * 
 * @author Philippe Fournier-Viger
 */
public abstract class SetObject<T> {

	/**
	 * Clear the set
	 */
	public abstract void clear();

	/**
	 * Get the number of elements in the set
	 * 
	 * @return the number
	 */
	public abstract int size();

	/**
	 * Check if this set is empty
	 * 
	 * @return true if empty. Otherwise, false
	 */
	public abstract boolean isEmpty();

	/**
	 * Calculate the hash value of a key
	 * 
	 * @param key the key
	 * @return the hash value
	 */
	protected abstract int hash(T key);

	/**
	 * Check if a value is in the structure
	 * 
	 * @param key the key
	 * @return true if it is contained. Otherwise, false
	 */
	public abstract boolean contains(T key);

	/**
	 * Put a new value in the structure
	 * 
	 * @param key the key
	 */
	public abstract void add(T key);

	/**
	 * Remove a key from the hash table
	 * 
	 * @param key the key
	 * @return true if removed, false if not found
	 */
	public abstract boolean remove(T key);
	
	
	/** Add all Objects from a set
	 * @param set a set*/
	public abstract void addAll(SetObject<T> set);
	

	// ================= ITERATOR
	// =======================================================

	/**
	 * Get an iterator on this map
	 * 
	 * @return an iterator
	 */
	public abstract AEntryIterator iterator();

	/**
	 * Abstract iterator for a set of objects. <br/>
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
	public abstract class AEntryIterator {

		/**
		 * Get the next entry
		 * 
		 * @return the next entry or throws NoSuchElementException if there are no next
		 *         entry
		 */
		public abstract T next();

		/**
		 * Check if there is a next entry
		 * 
		 * @return true if there is. Otherwise, false.
		 */
		public abstract boolean hasNext();

		/**
		 * Remove the current entry
		 * 
		 * @return the entry or throw IllegalStateException if there is no current
		 *         entry.
		 */
		public abstract void remove();
	}
}