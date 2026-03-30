package ca.pfv.spmf.datastructures.collections.linkedlist;

import ca.pfv.spmf.datastructures.collections.comparators.ComparatorInt;
import ca.pfv.spmf.datastructures.collections.list.ListInt;

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
 * This class implements a simple linked list of integers (primitive type)
 * 
 * @author Philippe Fournier-Viger
 * @see ListInt
 */
public class LinkedListInt{
	/** size of the array */
	private int size = 0;
	/** data */
	private Entry head;
	/** data */
	private Entry lastInsertedEntry;

	/**
	 * Inner node class for storing a key and a pointer to a next node
	 */
	private class Entry {
		/** a key */
		int key;
		/** pointer to the next node */
		Entry next;

		/**
		 * Constructor
		 * 
		 * @param key a key
		 */
		public Entry(int key) {
			this.key = key;
		}
	}

	/**
	 * Constructor *
	 */
	public LinkedListInt() {
		clear();
	}

	/**
	 * Check if this list is empty
	 * 
	 * @return true if empty. Otherwise, false
	 */
	public boolean isEmpty() {
		return size == 0;
	}

	/**
	 * Clear the structure
	 */
	public void clear() {
		size = 0;
		head = null;
		lastInsertedEntry = null;
	}

	/**
	 * Add an element at a given position
	 * 
	 * @param element the element
	 */
	public void add(int element) {
		Entry entry = new Entry(element);
		if (size == 0) {
			head = entry;
		} else {
			lastInsertedEntry.next = entry;
		}
		lastInsertedEntry = entry;
		size++;
	}

	/**
	 * Set the value at a given position
	 * 
	 * @param index the position
	 * @param value the value
	 */
	public void set(int index, int value) {
		if (index < 0 || index >= size) {
			throw new IndexOutOfBoundsException();
		}
		Entry entry = head;
		while (index != 0) {
			index--;
			entry = entry.next;
		}
		entry.key = value;
	}

	/**
	 * Remove the element at a given position
	 * 
	 * @param index the element
	 */
	public void removeAt(int index) {
		if (index < 0 || index >= size) {
			throw new IndexOutOfBoundsException();
		}

		if (index == 0) {
			head = head.next;
			size--;
			return;
		}

		// Find the previous node of the node to be removed
		Entry prev = head;
		for (int i = 0; i < index - 1; i++) {
			prev = prev.next;
		}
		// Remove the node by skipping it in the link
		prev.next = prev.next.next;
		size--;
	}

	/**
	 * Remove the element that has a given value
	 * 
	 * @param index the element
	 */
	public void remove(int value) {
		if (size == 0) {
			return;
		}
		// If the head node has the given value, remove it and repeat until it doesn't
		while (head != null && head.key == value) {
			size--;
			head = head.next;
		}

		// If there are no more nodes left, return
		if (head == null) {
			return;
		}
		// Find and remove any other nodes with the given value in the rest of the list
		Entry current = head;
		Entry previous = null;
		while (current != null) {
			// If the current node has the given value, skip it in the link
			if (current.key == value) {
				previous.next = current.next;
				size--;
			} else {
				// Otherwise, update the previous node
				previous = current;
			}
			// Go to the next node
			current = current.next;
		}
	}

	/**
	 * Get the element at a given position
	 * 
	 * @param index the element
	 * @return the element
	 */
	public int get(int index) {
		if (index < 0 || index >= size) {
			throw new IndexOutOfBoundsException();
		}
		// Find the node at the given index
		Entry currentEntry = head;
		for (int i = 0; i < index; i++) {
			currentEntry = currentEntry.next;
		}
		// Return its value
		return currentEntry.key;
	}

	/**
	 * Get the number of elements in this array
	 * 
	 * @return the number of elements
	 */
	public int size() {
		return size;
	}

	/**
	 * Check if a value is in the structure
	 * 
	 * @param value the value
	 * @return true if it is contained. Otherwise, false
	 */
	public boolean contains(int value) {
		if (size == 0) {
			return false;
		}
		// Go from one node to the next to find the value
		Entry currentEntry = head;
		while (currentEntry != null) {
			// If we found the value
			if (currentEntry.key == value) {
				return true;
			}
			currentEntry = currentEntry.next;
		}
		// No match, return false
		return false;
	}

	/**
	 * Get the position of the first occurrence of a value in this list.
	 * 
	 * @param value the value
	 * @return the position of the first occurrence or -1 if the value is not found
	 */
	public int indexOf(int value) {
		if (size == 0) {
			return -1;
		}
		// start from current node and go through the next ones
		Entry currentEntry = head;
		int index = 0;
		while (currentEntry != null) {
			// If we found
			if (currentEntry.key == value) {
				return index;
			}
			// Go next
			currentEntry = currentEntry.next;
			index++;
		}
		// Did not find the value
		return -1;
	}

	/**
	 * Sort the array in increasing order (by selection sort)
	 */
	public void sortByIncreasingOrder() {
		throw new java.lang.UnsupportedOperationException("Unavailable operation for LinkedListInt.");
	}

	/**
	 * Sort the array in increasing order (by selection sort)
	 */
	public void sortByDecreasingOrder() {
		throw new java.lang.UnsupportedOperationException("Unavailable operation for LinkedListInt.");
	}

	/**
	 * Sort the array using a comparator (by selection sort)
	 */
	public void sort(ComparatorInt comparator) {
		throw new java.lang.UnsupportedOperationException("Unavailable operation for LinkedListInt.");
	}

	// ========================================= Immutable Sublist OPERATIONS

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
	public ListInt immutableSubList(int fromPosition, int toPosition) {
		throw new java.lang.UnsupportedOperationException("Unavailable operation for LinkedListInt.");
	}

	public int binarySearch(int element, ComparatorInt comparator) {
		throw new java.lang.UnsupportedOperationException("Unavailable operation for LinkedListInt.");
	}

	// set
}