package ca.pfv.spmf.algorithms.frequentpatterns.dci_closed_fast;
/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger
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
*/

import java.util.Arrays;

/**
 * A lightweight resizable list of primitive ints. Avoids the autoboxing
 * overhead of java.util.List.
 *
 * @see AlgoDCI_Closed_FAST
 * @author Philippe Fournier-Viger
 */
public class IntList {

	/** the backing int array */
	private int[] data;

	/** the number of elements currently in the list */
	private int size;

	/**
	 * Default constructor with an initial capacity of 8.
	 */
	public IntList() {
		this(8);
	}

	/**
	 * Constructor with a given initial capacity.
	 * 
	 * @param capacity the initial capacity
	 */
	public IntList(int capacity) {
		if (capacity < 1) {
			capacity = 1;
		}
		data = new int[capacity];
		size = 0;
	}

	/**
	 * Add a value to the end of the list.
	 * 
	 * @param value the value to add
	 */
	public void add(int value) {
		ensureCapacity(size + 1);
		data[size++] = value;
	}

	/**
	 * Append all elements from another IntList to this list.
	 * 
	 * @param other the other IntList
	 */
	public void addAll(IntList other) {
		int otherSize = other.size;
		if (otherSize == 0) {
			return;
		}
		ensureCapacity(size + otherSize);
		System.arraycopy(other.data, 0, data, size, otherSize);
		size += otherSize;
	}

	/**
	 * Get the value at a given index.
	 * 
	 * @param index the index
	 * @return the value at that index
	 */
	public int get(int index) {
		return data[index];
	}

	/**
	 * Return the number of elements in the list.
	 * 
	 * @return the size
	 */
	public int size() {
		return size;
	}

	/**
	 * Return the backing array directly. Only the first size() elements are valid.
	 * 
	 * @return the backing int array
	 */
	public int[] data() {
		return data;
	}

	/**
	 * Reset the list to empty without deallocating the backing array.
	 */
	public void clear() {
		size = 0;
	}

	/**
	 * Truncate the list to a given size for backtracking. Has no effect if newSize
	 * >= current size.
	 * 
	 * @param newSize the size to truncate to
	 */
	public void truncate(int newSize) {
		if (newSize >= 0 && newSize < size) { // Added: newSize >= 0 check
			size = newSize;
		}
	}

	/**
	 * Sort the list using a primitive int comparator. Uses quicksort to avoid any
	 * Integer autoboxing.
	 * 
	 * @param comparator the comparator to use
	 */
	public void sort(IntComparator comparator) {
		if (size > 1) {
			quicksort(data, 0, size - 1, comparator);
		}
	}

	/**
	 * Quicksort implementation on a primitive int array. Uses insertion sort for
	 * small subarrays and median-of-three pivot selection.
	 * 
	 * @param arr  the array to sort
	 * @param low  the lower bound index (inclusive)
	 * @param high the upper bound index (inclusive)
	 * @param cmp  the comparator
	 */
	private static void quicksort(int[] arr, int low, int high, IntComparator cmp) {
		// use insertion sort for small subarrays to avoid recursion overhead
		if (high - low < 16) {
			for (int i = low + 1; i <= high; i++) {
				int key = arr[i];
				int j = i - 1;
				while (j >= low && cmp.compare(arr[j], key) > 0) {
					arr[j + 1] = arr[j];
					j--;
				}
				arr[j + 1] = key;
			}
			return;
		}

		// median-of-three: sort arr[low], arr[mid], arr[high] so that
		// arr[low] <= arr[mid] <= arr[high], then use arr[mid] as pivot.
		// arr[low] and arr[high] act as sentinels for the partition scan.
		int mid = low + (high - low) / 2;
		if (cmp.compare(arr[mid], arr[low]) < 0) {
			swap(arr, low, mid);
		}
		if (cmp.compare(arr[high], arr[low]) < 0) {
			swap(arr, low, high);
		}
		if (cmp.compare(arr[high], arr[mid]) < 0) {
			swap(arr, mid, high);
		}
		// place pivot at high-1; arr[low] and arr[high] are sentinels
		swap(arr, mid, high - 1);
		int pivot = arr[high - 1];

		// two-pointer partition; sentinels guarantee i and j stay in bounds
		int i = low;
		int j = high - 1;
		while (true) {
			while (cmp.compare(arr[++i], pivot) < 0)
				;
			while (cmp.compare(arr[--j], pivot) > 0)
				;
			if (i >= j) {
				break;
			}
			swap(arr, i, j);
		}
		// restore pivot to its final position
		swap(arr, i, high - 1);

		if (i - 1 > low) {
			quicksort(arr, low, i - 1, cmp);
		}
		if (i + 1 < high) {
			quicksort(arr, i + 1, high, cmp);
		}
	}

	/**
	 * Swap two elements in an int array.
	 * 
	 * @param arr the array
	 * @param a   first index
	 * @param b   second index
	 */
	private static void swap(int[] arr, int a, int b) {
		int tmp = arr[a];
		arr[a] = arr[b];
		arr[b] = tmp;
	}

	/**
	 * Ensure the backing array can hold at least minCapacity elements. Doubles the
	 * current capacity if that is sufficient, otherwise grows to minCapacity.
	 * 
	 * @param minCapacity the minimum capacity needed
	 */
	private void ensureCapacity(int minCapacity) {
		if (minCapacity > data.length) {
			data = Arrays.copyOf(data, Math.max(data.length * 2, minCapacity));
		}
	}
}