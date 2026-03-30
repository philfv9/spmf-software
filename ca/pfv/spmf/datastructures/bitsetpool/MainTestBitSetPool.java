package ca.pfv.spmf.datastructures.bitsetpool;

import java.util.BitSet;

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
 * This class is for testing the BitSetPool
 * 
 * @see BitSetPool
 * @author Philippe Fournier-Viger
 */
public class MainTestBitSetPool {

    /** Method for testing */
    public static void main(String[] args) {
        // Create a pool of 4 bitsets with variable size.
        BitSetPool pool = new BitSetPool(4);
        pool.printPoolInformation();

        BitSet bitSet1 = pool.acquire();
        bitSet1.set(0, true);
        bitSet1.set(1, false);
        bitSet1.set(2, true);

        BitSet bitSet2 = pool.acquire();
        bitSet2.set(0, false);
        bitSet2.set(1, true);
        bitSet2.set(2, true);

        System.out.println("Bitset 1: " + bitSet1);
        System.out.println("Bitset 2: " + bitSet2);
        System.out.println("Active count: " + pool.getActiveCount());

        pool.releaseBitSet(bitSet1);
        System.out.println("Release one bitset");
        pool.printPoolInformation();

        System.out.println("Get one bitset");
        BitSet bitSet3 = pool.acquire();
        pool.printPoolInformation();

        System.out.println("Bitset 3 (empty): " + bitSet3);
        System.out.println("Bitset 1 is bitset 3?: " + (bitSet3 == bitSet1));

        System.out.println("Release one bitset");
        pool.releaseBitSet(bitSet2);
        pool.printPoolInformation();

        System.out.println("Get one bitset");
        BitSet bitSet4 = pool.acquire();
        pool.printPoolInformation();

        System.out.println("Bitset 4 (empty): " + bitSet4);
        System.out.println("Bitset 4 is bitset 2?: " + (bitSet4 == bitSet2));
        System.out.println("Bitset 4 is not bitset 3?: " + (bitSet4 != bitSet3));

        System.out.println("Get four bitsets (should create new ones beyond pool size)");
        BitSet bitSet5 = pool.acquire();
        BitSet bitSet6 = pool.acquire();
        BitSet bitSet7 = pool.acquire();
        BitSet bitSet8 = pool.acquire();
        pool.printPoolInformation();

        System.out.println("Release four bitsets");
        pool.releaseBitSet(bitSet5);
        pool.releaseBitSet(bitSet6);
        pool.releaseBitSet(bitSet7);
        pool.releaseBitSet(bitSet8);
        pool.printPoolInformation();

        System.out.println("Get two bitsets");
        @SuppressWarnings("unused")
        BitSet bitSet9 = pool.acquire();
        @SuppressWarnings("unused")
        BitSet bitSet10 = pool.acquire();
        pool.printPoolInformation();

        // Test acquire/release aliases
        System.out.println("Test acquire/release methods");
        BitSet acquired = pool.acquire();
        System.out.println("Acquired: " + acquired);
        pool.release(acquired);
        pool.printPoolInformation();

        // Test createDedicated
        System.out.println("Create dedicated bitset (not managed by pool)");
        BitSet dedicated = pool.createDedicated();
        dedicated.set(5, true);
        System.out.println("Dedicated: " + dedicated);

        // Clear the pool
        System.out.println("Clear the pool");
        pool.clear();
        pool.printPoolInformation();
    }
}