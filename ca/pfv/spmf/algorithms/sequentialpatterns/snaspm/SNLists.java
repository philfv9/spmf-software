package ca.pfv.spmf.algorithms.sequentialpatterns.snaspm;
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
 * Flat parallel arrays storing SN-list entries for all frequent 1-items. Index
 * i corresponds to frequentItemsArray[i]; snSize[i] is the valid count.
 * 
 * @author Philippe Fournier-Viger
 */
class SNLists {
	/** pre-order labels per item */
	int[][] pre;
	/** post-order labels per item */
	int[][] post;
	/** event indices per item */
	int[][] event;
	/** sequence counts per item */
	int[][] count;
	/** number of valid entries per item */
	int[] size;

	/**
	 * Allocates empty SN-list arrays for n frequent items.
	 *
	 * @param n number of frequent items
	 */
	SNLists(int n) {
		pre = new int[n][];
		post = new int[n][];
		event = new int[n][];
		count = new int[n][];
		size = new int[n];
	}
}