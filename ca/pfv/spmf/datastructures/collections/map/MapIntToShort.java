package ca.pfv.spmf.datastructures.collections.map;

import ca.pfv.spmf.datastructures.collections.map.MapIntToInt.KeyIterator;
import ca.pfv.spmf.datastructures.collections.map.MapIntToInt.ValueIterator;

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
 * Abstract class defining the operations that a map of integers to short values should have.
 * 
 * @author Philippe Fournier-Viger
 */
public abstract class MapIntToShort {
	/**
	 * Clear the map
	 */
	public abstract void clear();

	/**
	 * Get the number of pairs in the map
	 * 
	 * @return the number
	 */
	public abstract int size();

	/**
	 * Check if this map is empty
	 * 
	 * @return true if empty. Otherwise, false
	 */
	public abstract  boolean isEmpty();

	/**
	 * Calculate the hash value of a key
	 * 
	 * @param key the key
	 * @return the hash value
	 */
	protected abstract int hash(int key);

	/**
	 * Check if a value is in the structure
	 * 
	 * @param key the key
	 * @return true if it is contained. Otherwise, false
	 */
	public abstract boolean containsKey(int key);

	/**
	 * Get a value from the map
	 * 
	 * @param key the key
	 * @return the value or -1 if not found
	 */
	public abstract  short get(int key);
	
	/**
	 * Increase the value that is associated to a key by a given value.
	 * For example, lets say that a map contains a key 5 associated to a value of 10.
	 * Then, calling this method with 5,7 will result in increasing the value of 10 by 7.
	 * Thus, the map will now contain the value 17 for the key 5.
	 * If the key is not in the map, the value of the key will be set to the given value.
	 * For example, if we call this method with 5, 10 but the key 5 is not in the map,
	 * the result will be to store a novel pair (5,10) in the map.
	 * @param key the key
	 * @param valueToAdd the value to be added.
	 */
	public abstract void getAndIncreaseValueBy(int key, short valueToAdd);

	/**
	 * Put a new value in the map
	 * 
	 * @param key   the key
	 * @param value the value
	 */
	public abstract void put(int key, short value);

	/**
	 * Remove a key from the hash table
	 * 
	 * @param key the key
	 * @return true if removed, false if not found
	 */
	public abstract boolean remove(int key);

	// ================= ITERATOR
	// =======================================================

	/**
	 * Get an iterator on this map
	 * 
	 * @return an iterator
	 */
	public abstract EntryIterator iterator();

	/**
	 * Abstract class for an iterator on this type of map. <br/>
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
	public abstract class EntryIterator {

		/**
		 * Get the next entry
		 * 
		 * @return the next entry or throws NoSuchElementException if there are no next
		 *         entry
		 */
		public abstract MapEntryIntToShort next();

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
	
	public abstract class MapEntryIntToShort{
		/** 
		 * Get the key
		 * @return the key
		 */
		public abstract int getKey();
		
		/** 
		 * Get the value
		 * @return the value
		 */
		public abstract short getValue();
	}
	// =====================================================================
	

	/**
	 * Get an iterator on this map for keys
	 * 
	 * @return an iterator
	 */
	public abstract KeyIterator iteratorForKeys();

	/**
	 * Abstract class for an iterator of keys on this type of map. <br/>
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
	public abstract class KeyIterator {

		/**
		 * Get the next entry
		 * 
		 * @return the next entry or throws NoSuchElementException if there are no next
		 *         entry
		 */
		public abstract int next();

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
	/**
	 * Get an iterator on this map for keys
	 * 
	 * @return an iterator
	 */
	public abstract ValueIterator iteratorForValues();

	/**
	 * Abstract class for an iterator of values on this type of map. <br/>
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
	public abstract class ValueIterator {

		/**
		 * Get the next entry
		 * 
		 * @return the next entry or throws NoSuchElementException if there are no next
		 *         entry
		 */
		public abstract short next();

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
	// =====================================================================
}