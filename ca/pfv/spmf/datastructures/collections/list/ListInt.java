package ca.pfv.spmf.datastructures.collections.list;

import ca.pfv.spmf.datastructures.collections.comparators.ComparatorInt;

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
 * Abstract class defining the operations that a List of integers should have.
 * 
 * @author Philippe Fournier-Viger 2023
 *
 */
public abstract class ListInt {

	/**
	 * Check if this list is empty
	 * 
	 * @return true if empty. Otherwise, false
	 */
	public abstract boolean isEmpty();

	/**
	 * Clear the structure
	 */
	public abstract void clear();

	/**
	 * Add an element at a given position
	 * 
	 * @param element the element
	 */
	public abstract void add(int element);

	/**
	 * Add all elements from a list to this list
	 * 
	 * @param list another list
	 */
	public void addAll(ListInt list) {
		for (int z = 0; z < list.size(); z++) {
			this.add(list.get(z));
		}
	}

	/**
	 * Set the value at a given position
	 * 
	 * @param index the position
	 * @param value the value
	 */
	public abstract void set(int index, int value);

	/**
	 * Remove the element at a given position
	 * 
	 * @param index the element
	 */
	public abstract void removeAt(int index);

	/**
	 * Remove the element that has a given value
	 * 
	 * @param index the element
	 */
	public abstract void remove(int value);

	/**
	 * Add all elements from a list to this list
	 * 
	 * @param list another list
	 */
	public void removeAll(ListInt list) {
		for (int z = 0; z < list.size(); z++) {
			this.remove(list.get(z));
		}
	}

	/**
	 * Get the element at a given position
	 * 
	 * @param index the element
	 * @return the element
	 */
	public abstract int get(int index);

	/**
	 * Get the number of elements in this array
	 * 
	 * @return the number of elements
	 */
	public abstract int size();

	/**
	 * Check if a value is in the structure
	 * 
	 * @param value the value
	 * @return true if it is contained. Otherwise, false
	 */
	public abstract boolean contains(int value);

	/**
	 * Get the position of the first occurrence of a value in this list.
	 * 
	 * @param value the value
	 * @return the position of the first occurrence or -1 if the value is not found
	 */
	public abstract int indexOf(int value);

	/**
	 * Sort the array using a comparator (by selection sort)
	 */
	public abstract void sort(ComparatorInt comparator);

	/**
	 * Sort the array in increasing order (by selection sort)
	 */
	public abstract void sortByIncreasingOrder();

	/**
	 * Sort the array in increasing order (by selection sort)
	 */
	public abstract void sortByDecreasingOrder();

	/**
	 * This method returns a sub-list view of the list, that is immutable. This view
	 * is like a list but it does not allow to modify the content of the sublist. In
	 * other words, operations like add and remove are not allowed. However, note
	 * that if someone modifies the original list, then the content of the sublist
	 * could be changed indirectly.
	 * 
	 * @param fromPosition the position where the sublist starts (inclusive)
	 * @param toPosition   the position where the sublist ends (exclusive)
	 * @return the sublist
	 */
	public abstract ListInt immutableSubList(int fromPosition, int toPosition);

	/**
	 * Search for a key in the list using the binary search and a comparator and
	 * return the position of the key. It assumes that the item is sorted in
	 * ascending order by the comparator
	 * 
	 * @param key        the key
	 * @param comparator the comparator
	 * @return the position in the array or a negative value if it is not found
	 */
	public abstract int binarySearch(int key, ComparatorInt comparator);

	/**
	 * Get an iterator
	 * 
	 * @return an iterator
	 */
	public abstract IteratorList iterator();

	/**
	 * Iterator class
	 * @author philippe
	 */
	public abstract class IteratorList {

		/**
		 * Has a next element
		 * 
		 * @return true or false
		 */
		public abstract boolean hasNext();

		/**
		 * Get the next element
		 * 
		 * @return the next element
		 */
		public abstract int next();
		
		/**
		 * Remove the current element
		 */
		public abstract void remove();
	}

}