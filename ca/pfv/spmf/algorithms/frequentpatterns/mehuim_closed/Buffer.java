package ca.pfv.spmf.algorithms.frequentpatterns.mehuim_closed;

/* This file is copyright (c) Yang Hongyang, Philippe Fournier-Viger
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

/**
 * This is an implementation of a buffer
 *
 * @author Hongyang Yang
 */
public class Buffer {
	public static int length;
	Transaction transactions[];
	int start;
	int end;
	int maxLen;

	public Buffer() {
	}

	public Buffer(int len) {
		if (len == -1) {
			len = length;
		}
		transactions = new Transaction[len];
		for (int i = 0; i < len; i++) {
			transactions[i] = new Transaction();
		}
		start = 0;
		end = 0;
		maxLen = len;
	}

	public Buffer(int len, boolean b) {
		if (len == -1) {
			len = length;
		}
		transactions = new Transaction[len];
		start = 0;
		end = 0;
		maxLen = 0;
	}

	public Transaction GetNextTransaction() {
		if (maxLen == end) {
			end++;
			return transactions[maxLen++] = new Transaction();
		}
		return transactions[end++];
	}

	public void newTransaction(int len) {
		while (maxLen < len) {
			transactions[maxLen++] = new Transaction();
		}
		maxLen--;
	}
}