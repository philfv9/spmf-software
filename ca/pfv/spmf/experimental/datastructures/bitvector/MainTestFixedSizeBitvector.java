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
 * This class is for testing the FixedSizedBitsetPool
 * 
 * @see FixedSizeBitSetPool
 * @author Philippe Fournier-Viger
 */
public class MainTestFixedSizeBitvector {

	/** Method for testing */
	public static void main(String[] args) {
		FixedSizeBitvector bv = new FixedSizeBitvector(66);
		// Set bits at index 0 and 16 to 1
//		bv.set(0);
		bv.set(0,true);
		bv.set(16);
		bv.set(31);
		bv.set(58);
		bv.set(59);
		bv.set(65);
		
		// Print the bit vector
		System.out.println("Bitset 1:");
		bv.print();
		
		// Print the bit vector
		System.out.println();
		System.out.println("Size of bitset 1:");
		System.out.println(bv.size());
		
		// Check the bit at index 8
		System.out.println("Bit 0 of Bitset 1:");
		boolean bit0 = bv.get(0);
		System.out.println(bit0);
		
		System.out.println("Bit 1 of Bitset 1:");
		boolean bit1 = bv.get(0);
		System.out.println(bit1);

		// Create a new bit vector with the same size
		FixedSizeBitvector bv2 = new FixedSizeBitvector(66);

		// Set bits at index 4 and 8 to 1
		bv2.set(0, true);
		bv2.set(16, true);
		bv2.set(4, true);
		bv2.set(8, true);
		bv2.set(59, true);
		
		// Print the bit vector 2 
		System.out.println("Bitset 2:");
		bv2.print();
		System.out.println();
		
		// Create a new bit vector with the same size
		FixedSizeBitvector bv3 = new FixedSizeBitvector(60);

		// Set bits at index 4 and 8 to 1
		bv3.set(0, true);
		bv3.set(5, true);
		bv3.set(16, true);
		
		// Print the bit vector 3 
		System.out.println("Bitset 3:");
		bv3.print();
		System.out.println();


		// Perform the bitwise intersection between the two bitvectors
		System.out.println("Intersection of Bitset 1 and Bitset 2:");
		bv.and(bv2);
		bv.print();
		System.out.println();
		
		// Perform the bitwise union of the previous bitvector and Bitset 2
		System.out.println("Union of previous bitset and Bitset 2:");
		bv.union(bv2);
		bv.print();
		System.out.println();
		
		// Perform the bitwise union of the previous bitvector and Bitset 2
		System.out.println("Flip of previous bitset:");
		bv.flip();
		bv.print();
		System.out.println();
		
		// Perform the bitwise OR of the previous bitvector with Bitset 2
		System.out.println("OR of Bitset3 with Bitset2:");
		bv3.or(bv2);
		bv3.print();
		System.out.println();
		
		// Perform the bitwise union of the previous bitvector and Bitset 2
		System.out.println("Clear the previous bitset:");
		bv.clear();
		bv.print();
		System.out.println();
	}
}