package ca.pfv.spmf.algorithms.frequentpatterns.dci_closed_fast;
/* This file is copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * This class represents a bit matrix as used by the optimized version of the
 * DCI_Closed algorithm. Uses LongArrayBitSet for direct word-level access.
 * Bitsets for individual items are allocated lazily so that projected matrices
 * only pay allocation cost for items that are actually written.
 * 
 * @see AlgoDCI_Closed_FAST
 * @see LongArrayBitSet
 * @author Philippe Fournier-Viger
 */
public class BitMatrix {

	// Array containing the tidset of each item as a LongArrayBitSet.
	// The position i represents the tidset of item i+1.
	// Entries may be null for items that were never written (e.g. in projected matrices).
	private LongArrayBitSet[] matrixItemTIDs;

	/** number of words per bitset, computed once from the transaction count */
	private int numWords;

	/**
	 * Constructor of a bit matrix. Individual item bitsets are allocated lazily
	 * on first write so that projected matrices only allocate what they need.
	 * @param itemCount        the number of items in the bitmatrix (slots)
	 * @param transactionCount the number of transactions (bits per bitset)
	 */
	BitMatrix(int itemCount, int transactionCount) {
		numWords = LongArrayBitSet.wordsNeeded(transactionCount);
		// allocate the slot array but leave entries null; each slot is created on demand
		matrixItemTIDs = new LongArrayBitSet[itemCount];
	}

	/**
	 * Ensure the bitset slot for the given item exists, creating it if needed.
	 * @param item the item (1-based renamed id)
	 */
	private void ensureSlot(int item) {
		int idx = item - 1;
		if (matrixItemTIDs[idx] == null) {
			matrixItemTIDs[idx] = new LongArrayBitSet(numWords);
		}
	}

	/**
	 * Add a tid to the tidset of an item.
	 * @param item the item (1-based renamed id)
	 * @param bit  the bit corresponding to the tid
	 */
	public void addTidForItem(int item, int bit) {
		ensureSlot(item);
		matrixItemTIDs[item - 1].set(bit);
	}

	/**
	 * Write a full 64-bit word directly into the given word index of an item's
	 * tidset. Used by the word-level projection path to avoid per-bit set() calls.
	 * @param item      the item (1-based renamed id)
	 * @param wordIndex the word index within the item's bitset
	 * @param word      the 64-bit value to store
	 */
	public void setWordForItem(int item, int wordIndex, long word) {
		ensureSlot(item);
		matrixItemTIDs[item - 1].setWord(wordIndex, word);
	}

	/**
	 * Get the support of an item by counting set bits in its tidset.
	 * Returns 0 for items whose slot was never allocated.
	 * @param i the item (1-based renamed id)
	 * @return the support
	 */
	public int getSupportOfItem(int i) {
		LongArrayBitSet bs = matrixItemTIDs[i - 1];
		if (bs == null) {
			return 0;
		}
		return bs.cardinality();
	}

	/**
	 * Get the tidset of an item. If the slot was never written (e.g. an item not
	 * present in a projected matrix) the slot is created empty so callers never
	 * receive null.
	 * @param i the item (1-based renamed id)
	 * @return a LongArrayBitSet representing the tidset of the item
	 */
	public LongArrayBitSet getBitSetOf(int i) {
		ensureSlot(i);
		return matrixItemTIDs[i - 1];
	}

	/**
	 * Return a string representation of the bitmatrix
	 */
	public String toString() {
		StringBuilder buffer = new StringBuilder();
		// for each bitset
		for (LongArrayBitSet bitset : matrixItemTIDs) {
			if (bitset == null) {
				buffer.append("(empty)\n");
				continue;
			}
			// append its set bits
			for (int bit = bitset.nextSetBit(0); bit >= 0; bit = bitset.nextSetBit(bit + 1)) {
				buffer.append(bit);
				buffer.append(' ');
			}
			buffer.append('\n');
		}
		// return the string
		return buffer.toString();
	}
}