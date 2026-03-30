package ca.pfv.spmf.datastructures.collections.list;

import ca.pfv.spmf.datastructures.collections.comparators.ComparatorShort;

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
 * This class implements a simple array of short (primitive type) that is
 * resized automatically
 * 
 * @author Philippe Fournier-Viger
 * @see ListShort
 */
public class ArrayListShort extends ListShort {
	/** size of the array */
	private int size = 0;
	/** data */
	private short[] data;

	/** DEFAULT CAPACITY */
	private int DEFAULT_SIZE = 20;

	/**
	 * Constructor *
	 */
	public ArrayListShort() {
		data = new short[DEFAULT_SIZE];
	}

	/**
	 * Constructor
	 * 
	 * @param size initial size
	 */
	public ArrayListShort(int size) {
		data = new short[size];
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
	}

	/**
	 * Add an element at a given position
	 * 
	 * @param element the element
	 */
	public void add(short element) {
		if (size == data.length) {
			increaseSize();
		}
		size = size + 1;
		data[size - 1] = element;
	}

	/**
	 * Set the value at a given position
	 * 
	 * @param index the position
	 * @param value the value
	 */
	public void set(int index, short value) {
		if (index < 0 || index >= size) {
			throw new IndexOutOfBoundsException();
		}
		data[index] = value;
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
		for (int i = index; i < size - 1; i++) {
			data[i] = data[i + 1];
		}
		size = size - 1;
	}

	/**
	 * Remove the element that has a given value
	 * 
	 * @param index the element
	 */
	public void remove(short value) {
		int i = 0;
		int newSize = size;
		for (int j = 0; j < size; j++) {
			if (data[j] != value) {
				data[i] = data[j];
				i++;
			} else {
				newSize--;
			}
		}
		size = newSize;
	}

	/**
	 * Get the element at a given position
	 * 
	 * @param index the element
	 * @return the element
	 */
	public short get(int index) {
		if (index < 0 || index >= size) {
			throw new IndexOutOfBoundsException();
		}
		return data[index];
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
	 * Double the size of the internal storage
	 */
	private void increaseSize() {
		short[] newData = new short[data.length * 2];
		System.arraycopy(data, 0, newData, 0, size);
		data = newData;
	}

	/**
	 * Check if a value is in the structure
	 * 
	 * @param value the value
	 * @return true if it is contained. Otherwise, false
	 */
	public boolean contains(short value) {
		for (int i = 0; i < size; i++) {
			if (data[i] == value) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Get the position of the first occurrence of a value in this list.
	 * 
	 * @param value the value
	 * @return the position of the first occurrence or -1 if the value is not found
	 */
	public int indexOf(short value) {
		for (int i = 0; i < size; i++) {
			if (data[i] == value) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Sort the array in increasing order (by selection sort)
	 */
	public void sortByIncreasingOrder() {
		short temp;

		for (int i = 0; i < size; i++) {
			int minIndex = i;
			for (int j = i + 1; j < size; j++) {
				if (data[j] < data[minIndex]) {
					minIndex = j;
				}
			}
			// Swap the elements
			temp = data[i];
			data[i] = data[minIndex];
			data[minIndex] = temp;
		}
	}

	/**
	 * Sort the array in increasing order (by selection sort)
	 */
	public void sortByDecreasingOrder() {
		short temp;

		for (int i = 0; i < size; i++) {
			int minIndex = i;
			for (int j = i + 1; j < size; j++) {
				if (data[j] > data[minIndex]) {
					minIndex = j;
				}
			}
			// Swap the elements
			temp = data[i];
			data[i] = data[minIndex];
			data[minIndex] = temp;
		}
	}

	/**
	 * Sort the array using a comparator (by selection sort)
	 */
	public void sort(ComparatorShort comparator) {
		short temp;

		for (int i = 0; i < size; i++) {
			int minIndex = i;
			for (int j = i + 1; j < size; j++) {
				if (comparator.compare(data[j], data[minIndex]) < 0) {
					minIndex = j;
				}
			}
			// Swap the elements
			temp = data[i];
			data[i] = data[minIndex];
			data[minIndex] = temp;
		}
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
	public ListShort immutableSubList(int fromPosition, int toPosition) {
		if (fromPosition < 0) {
			throw new IndexOutOfBoundsException("This is outside the range of indices: " + fromPosition);
		} else if (toPosition > size) {
			throw new IndexOutOfBoundsException("This is outside the range of indices: " + toPosition);
		} else {
			return new ImmutableSublistObject(this, fromPosition, toPosition);
		}
	}

	public class ImmutableSublistObject extends ListShort {
		/** the original array */
		ListShort array;
		/** the position where the sublist starts (inclusive) */
		int from;
		/** the position where the sublist ends (exclusive) */
		int to;
		/** the length of this sublist */
		int length;

		/**
		 * Constructor
		 * 
		 * @param arrayListObject the original array
		 * @param fromPosition    the position where the sublist starts (inclusive)
		 * @param toPosition      the position where the sublist ends (exclusive)
		 */
		public ImmutableSublistObject(ListShort arrayListObject, int fromPosition, int toPosition) {
			this.array = arrayListObject;
			this.from = fromPosition;
			this.to = toPosition;
			this.length = (to - from);
		}

		@Override
		public boolean isEmpty() {
			return this.length == 0;
		}

		@Override
		public void clear() {
			throw new java.lang.UnsupportedOperationException("Unavailable operation for ImmutableSublist.");
		}

		@Override
		public void add(short element) {
			throw new java.lang.UnsupportedOperationException("Unavailable operation for ImmutableSublist.");
		}

		@Override
		public void set(int index, short value) {
			throw new java.lang.UnsupportedOperationException("Unavailable operation for ImmutableSublist.");
		}

		@Override
		public void removeAt(int index) {
			throw new java.lang.UnsupportedOperationException("Unavailable operation for ImmutableSublist.");
		}

		@Override
		public void remove(short value) {
			throw new java.lang.UnsupportedOperationException("Unavailable operation for ImmutableSublist.");
		}

		@Override
		public short get(int position) {
			if (position < 0) {
				throw new IndexOutOfBoundsException(" index " + position + " is out of bound");
			} else if (position >= to) {
				throw new IndexOutOfBoundsException(" index " + position + " is out of bound");
			}
			return array.get(from + position);
		}

		@Override
		public int size() {
			return length;
		}

		@Override
		public boolean contains(short value) {
			for (int i = from; i < length; i++) {
				if (array.get(i) == value) {
					return true;
				}
			}
			return false;
		}

		@Override
		public int indexOf(short value) {
			for (int i = from; i < length; i++) {
				if (array.get(i) == value) {
					return i;
				}
			}
			return -1;
		}

		@Override
		public void sort(ComparatorShort comparator) {
			throw new java.lang.UnsupportedOperationException("Unavailable operation for ImmutableSublist.");
		}

		@Override
		public ListShort immutableSubList(int fromPosition, int toPosition) {
			return array.immutableSubList(from + fromPosition, from + toPosition);
		}

		@Override
		public void sortByIncreasingOrder() {
			throw new java.lang.UnsupportedOperationException("Unavailable operation for ImmutableSublist.");
		}

		@Override
		public void sortByDecreasingOrder() {
			throw new java.lang.UnsupportedOperationException("Unavailable operation for ImmutableSublist.");
		}

		@Override
		public IteratorList iterator() {
			throw new java.lang.UnsupportedOperationException("Unavailable operation for ImmutableSublist.");
		}

	}

	// ==============================

	public IteratorList iterator() {
		return new Iterator();
	}

	private class Iterator extends IteratorList {
		/** The current index */
		private int index = 0;

		/**
		 * Has a next element
		 * 
		 * @return true or false
		 */
		public boolean hasNext() {
			return index < size();
		}

		/**
		 * Get the next element
		 * 
		 * @return the next element
		 */
		public short next() {
			return data[index++];
		}

		@Override
		public void remove() {
			// If there are no entry, we cannot do this operation
			if (index == 0) {
				throw new IllegalStateException();
			}
			removeAt(--index);
		}
	}
	// removeAll, addAll
}