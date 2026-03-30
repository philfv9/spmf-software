package ca.pfv.spmf.experimental.datastructures.bitvector;
/* This file is copyright (c) 2023 Philippe Fournier-Viger
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
 * This class implements a bit vector that has a fixed size
 * 
 * @author Philippe Fournier-Viger, 2023
 *
 */
public class FixedSizeBitvector {
	/** Words to store data */
	private long[] words;

	/** The total number of bits stored */
	private int size;

	/** The number of words to store data */
	private int wordNumber;

	/** The number of bits per word */
	private final static int BITS_PER_WORD = Long.BYTES * 8;

	/**
	 * Constructor
	 * 
	 * @param size the number of bits to store
	 */
	public FixedSizeBitvector(int size) {
		this.size = size;
		this.wordNumber = (int) Math.ceil(size / (double) BITS_PER_WORD);
		this.words = new long[this.wordNumber];
		// For debugging:
//		System.out.println("WORD COUNT :" + wordNumber);
//		System.out.println("BITS PER WORD :" + BITS_PER_WORD);
	}

	/**
	 * Sets a bit to true
	 * 
	 * @param index the position
	 */
	public void set(int index) {
		if (index >= size) {
			throw new ArrayIndexOutOfBoundsException();
		}

		int byteIndex = index / BITS_PER_WORD;
		int bitIndex = index % BITS_PER_WORD;
		words[byteIndex] |= (1L << bitIndex);
	}
	
	/**
	 * Sets the bit at the given index to true of false
	 * 
	 * @param index the index
	 * @param value the value
	 */
	public void set(int index, boolean value) {
		if (index >= size) {
			throw new ArrayIndexOutOfBoundsException();
		}

		int byteIndex = index / BITS_PER_WORD;
		int bitIndex = index % BITS_PER_WORD;
		if(value) {
			words[byteIndex] |= (1L << bitIndex);
		}else {
			words[byteIndex] &= ~(1L << bitIndex);
		}
	}

	/**
	 * Return the bit at a given index
	 * 
	 * @param index the index
	 * @return the bit value (0 or 1)
	 */
	public boolean get(int index) {
		if (index >= size) {
			throw new ArrayIndexOutOfBoundsException();
		}

		int byteIndex = index / BITS_PER_WORD;
		int bitIndex = index % BITS_PER_WORD;
		return (words[byteIndex] >> bitIndex & 1L) != 0;
	}

	/**
	 * Performs bitwise AND operation with another bit vector
	 * 
	 * @param bitvector the other bit vector
	 */
	public void and(FixedSizeBitvector bitvector) {
		for (int i = 0; i < wordNumber; i++) {
			words[i] &= bitvector.words[i];
		}
	}

	/**
	 * Performs bitwise OR operation with another bit vector
	 * 
	 * @param bitvector the other bit vector
	 */
	public void or(FixedSizeBitvector bitvector) {
		for (int i = 0; i < wordNumber; i++) {
			words[i] |= bitvector.words[i];
		}
	}

	/**
	 * Get the number of bits
	 * 
	 * @return
	 * @return number of bits
	 */
	public int size() {
		return size;
	}

	/**
	 * Reset all bits to zero
	 */
	public void clear() {
		for (int i = 0; i < wordNumber; i++) {
			words[i] = 0;
		}
	}

	/**
	 * Prints the content in the console
	 */
	public void print() {
		for (int i = 0; i < size; i++) {
			String bit = get(i) ? "1" : "0";
			System.out.print(bit);
		}
	}

	/**
	 * Flip this bit vector
	 */
	public void flip() {
		for (int i = 0; i < wordNumber; i++) {
			words[i] = ~words[i];
		}
	}

	/**
	 * Performs bitwise union with another bit vector
	 * 
	 * @param bitvector the other bit vector
	 */
	public void union(FixedSizeBitvector bitvector) {
		for (int i = 0; i < wordNumber; i++) {
			words[i] |= bitvector.words[i];
		}
	}

	@Override
	public Object clone() {
		FixedSizeBitvector clone = new FixedSizeBitvector(size);
		for (int i = 0; i < size; i++) {
			clone.words[i] = words[i];
		}
		return clone;
	}

}