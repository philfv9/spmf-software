package ca.pfv.spmf.algorithms.frequentpatterns.slim;

import java.util.Arrays;
/* Copyright (c) 2008-2013 Philippe Fournier-Viger
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
 *  class to represent a transaction, using an array of integers
 */
class Transaction {
	int[] items; // the items in the transaction

	// A constructor to create a transaction from an array of items and an id
	public Transaction(int[] items) {
		this.items = items;
	}

	// A method to return a string representation of this transaction
	public String toString() {
		return Arrays.toString(this.items);
	}
}