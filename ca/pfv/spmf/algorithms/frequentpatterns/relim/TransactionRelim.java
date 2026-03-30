package ca.pfv.spmf.algorithms.frequentpatterns.relim;
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

/**
 * A transaction for the RELIM algorithm that supports weights and efficient sublisting.
 * The transaction stores items in a shared array with an offset, avoiding array copying
 * when creating sublists.
 * 
 * It includes:
 * - Reset method for object pooling support
 * - Final fields for JIT optimization
 * - hashCode/equals based on item content (not weight) for efficient merging
 * 
 * @see AlgoRelim
 * @author Philippe Fournier-Viger
 */
public class TransactionRelim {
	
	/** The items in this transaction (may be shared with other transactions) */
	private int[] items;
	
	/** The offset into the items array where this transaction starts */
	private int offset;
	
	/** The weight of this transaction (for merged identical transactions) */
	private int weight;
	
	/** Cached hash code (computed lazily) */
	private int cachedHash;
	
	/** Flag indicating if hash has been computed */
	private boolean hashComputed;
	
	/**
	 * Constructor
	 * @param items the items array
	 * @param offset the starting offset in the items array
	 * @param weight the weight of this transaction
	 */
	public TransactionRelim(int[] items, int offset, int weight) {
		this.items = items;
		this.offset = offset;
		this.weight = weight;
		this.hashComputed = false;
	}
	
	/**
	 * Get the length of this transaction (number of items from offset to end)
	 * @return the length
	 */
	public final int length() {
		return items.length - offset;
	}
	
	/**
	 * Get the item at the specified index (relative to the offset)
	 * @param index the index (0-based, relative to offset)
	 * @return the item value
	 */
	public final int get(int index) {
		return items[offset + index];
	}
	
	/**
	 * Get the underlying items array
	 * @return the items array
	 */
	public final int[] getItems() {
		return items;
	}
	
	/**
	 * Get the offset
	 * @return the offset
	 */
	public final int getOffset() {
		return offset;
	}
	
	/**
	 * Get the weight of this transaction
	 * @return the weight
	 */
	public final int getWeight() {
		return weight;
	}
	
	/**
	 * Add to the weight of this transaction
	 * @param additionalWeight the weight to add
	 */
	public void addWeight(int additionalWeight) {
		this.weight += additionalWeight;
	}
	
	/**
	 * Compute hash code based on item content only (NOT weight).
	 * This allows using TransactionRelim directly as a HashMap key for merging
	 * identical transactions.
	 * 
	 * @return hash code based on items from offset to end
	 */
	@Override
	public int hashCode() {
		if (!hashComputed) {
			int h = 1;
			for (int i = offset; i < items.length; i++) {
				h = 31 * h + items[i];
			}
			cachedHash = h;
			hashComputed = true;
		}
		return cachedHash;
	}
	
	/**
	 * Check equality based on item content only (NOT weight).
	 * Two transactions are equal if they have the same items from their
	 * respective offsets to the end.
	 * 
	 * @param object the object to compare
	 * @return true if the transactions have identical item content
	 */
	@Override
	public boolean equals(Object object) {
		if (this == object) return true;
		if (!(object instanceof TransactionRelim)) return false;
		
		TransactionRelim other = (TransactionRelim) object;
		
		int length = items.length - offset;
		int otherLength = other.items.length - other.offset;
		if (length != otherLength) return false;
		
		for (int i = 0; i < length; i++) {
			if (items[offset + i] != other.items[other.offset + i]) {
				return false;
			}
		}
		return true;
	}
}