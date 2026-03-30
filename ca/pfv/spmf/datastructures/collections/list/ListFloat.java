package ca.pfv.spmf.datastructures.collections.list;

import ca.pfv.spmf.datastructures.collections.comparators.ComparatorFloat;

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
 * Abstract class defining the operations that a List of float values should have.
 * @author Philippe Fournier-Viger 2023
 *
 */
public abstract class ListFloat {

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
	public abstract void add(float element);
	
	/** Add all elements from a list to this list
	 * 
	 * @param list another list
	 */
	public void addAll(ListFloat list) {
		for(int z = 0; z < list.size(); z++) {
			this.add(list.get(z));
		}
	}

	/**
	 * Set the value at a given position
	 * 
	 * @param index the position
	 * @param value the value
	 */
	public abstract void set(int index, float value);

	/**
	 * Remove the element at a given position
	 * 
	 * @param index the element
	 */
	public abstract void removeAt(int index);
	
	/** Add all elements from a list to this list
	 * 
	 * @param list another list
	 */
	public void removeAll(ListFloat list) {
		for(int z = 0; z < list.size(); z++) {
			this.remove(list.get(z));
		}
	}

	/**
	 * Remove the element that has a given value
	 * 
	 * @param index the element
	 */
	public abstract void remove(float value);

	/**
	 * Get the element at a given position
	 * 
	 * @param index the element
	 * @return the element
	 */
	public abstract float get(int index);

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
	public abstract boolean contains(float value);

	/**
	 * Get the position of the first occurrence of a value in this list.
	 * 
	 * @param value the value
	 * @return the position of the first occurrence or -1 if the value is not found
	 */
	public abstract int indexOf(float value);

	/**
	 * Sort the array using a comparator (by selection sort)
	 */
	public abstract void sort(ComparatorFloat comparator);
	
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
	public abstract ListFloat immutableSubList(int fromPosition, int toPosition);
	
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
		public abstract float next();
		
		/**
		 * Remove the current element
		 */
		public abstract void remove();
	}
}