package ca.pfv.spmf.datastructures.collections.automatic_test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import ca.pfv.spmf.datastructures.collections.map.AMapIntToFloat;
import ca.pfv.spmf.datastructures.collections.map.AMapIntToInt;
import ca.pfv.spmf.datastructures.collections.map.MapIntToInt;
import ca.pfv.spmf.datastructures.collections.map.LMapIntToInt.LValueIterator;
import ca.pfv.spmf.datastructures.collections.map.MapIntToInt.MapEntryIntToInt;
import ca.pfv.spmf.datastructures.collections.map.MapIntToInt.ValueIterator;
import ca.pfv.spmf.datastructures.collections.map.MapIntToInt.EntryIterator;
import ca.pfv.spmf.datastructures.collections.map.MapIntToInt.KeyIterator;

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
 * A class for testing the AMapIntToInt class.
 * 
 * @author Philippe Fournier-Viger, 2023
 */
public class MainTestAMapIntToInt {

	public static void main(String[] args) {

		// Run the same experiment while varying the number of buckets in the map from 1
		// to 100
		// and the initial collision array size from 1 to 100.
		// We test this because these two factors should not change the behavior of the
		// structure from the outside.
		for (int bucketCount = 1; bucketCount <= 100; bucketCount++) {
			for (int collisionArraySize = 1; collisionArraySize <= 10; collisionArraySize++) {
				runExperiment(bucketCount, collisionArraySize);
			}
		}
	}

	private static void runExperiment(int bucketCount, int collisionArraySize) {
		MapIntToInt hashMap = new AMapIntToInt(bucketCount, collisionArraySize);
		System.out.println("MAP size = " + hashMap.size());
		CheckResults.checkResult(hashMap.size() == 0);
		CheckResults.checkResult(hashMap.isEmpty() == true);

		System.out.println("ADD 1, 10");
		hashMap.put(1, 10);
		CheckResults.checkResult(hashMap.size() == 1);
		CheckResults.checkResult(hashMap.containsKey(1));
		CheckResults.checkResult(hashMap.get(1) == 10);

		System.out.println("ADD 2, 20");
		hashMap.put(2, 20);
		CheckResults.checkResult(hashMap.size() == 2);
		CheckResults.checkResult(hashMap.containsKey(2));
		CheckResults.checkResult(hashMap.get(2) == 20);

		System.out.println("ADD 3, 30");
		hashMap.put(3, 30);
		CheckResults.checkResult(hashMap.containsKey(3));
		CheckResults.checkResult(hashMap.get(3) == 30);
		CheckResults.checkResult(hashMap.size() == 3);

		System.out.println("ADD 4, 10");
		hashMap.put(4, 40);
		CheckResults.checkResult(hashMap.size() == 4);
		CheckResults.checkResult(hashMap.containsKey(4));
		CheckResults.checkResult(hashMap.get(4) == 40);

		System.out.println("ADD 5, 20");
		hashMap.put(5, 50);
		CheckResults.checkResult(hashMap.size() == 5);
		CheckResults.checkResult(hashMap.containsKey(5));
		CheckResults.checkResult(hashMap.get(5) == 50);

		System.out.println("ADD 6, 20");
		hashMap.put(6, 60);
		CheckResults.checkResult(hashMap.size() == 6);
		CheckResults.checkResult(hashMap.containsKey(6));
		CheckResults.checkResult(hashMap.get(6) == 60);

		System.out.println("MAP size = " + hashMap.size());
		System.out.println("Value for key 1: " + hashMap.get(1));
		System.out.println("Value for key 2: " + hashMap.get(2));
		System.out.println("Value for key 3: " + hashMap.get(3));
		System.out.println("Value for key 4: " + hashMap.get(4));
		System.out.println("Value for key 5: " + hashMap.get(5));
		System.out.println("Value for key 6: " + hashMap.get(6));
		System.out.println("MAP size = " + hashMap.size());

		System.out.println("MAP size = " + hashMap.size());
		System.out.println("ADD 1, 100");
		hashMap.put(1, 100);
		System.out.println("ADD 2, 200");
		hashMap.put(2, 200);
		System.out.println("ADD 3, 300");
		hashMap.put(3, 300);
		System.out.println("ADD 4, 400");
		hashMap.put(4, 400);
		System.out.println("ADD 5, 500");
		hashMap.put(5, 500);
		System.out.println("ADD 6, 600");
		hashMap.put(6, 600);
		System.out.println("MAP size = " + hashMap.size());

		System.out.println("Value for key 1: " + hashMap.get(1));
		System.out.println("Value for key 2: " + hashMap.get(2));
		System.out.println("Value for key 3: " + hashMap.get(3));
		System.out.println("Value for key 4: " + hashMap.get(4));
		System.out.println("Value for key 5: " + hashMap.get(5));
		System.out.println("Value for key 6: " + hashMap.get(6));
		System.out.println("MAP size = " + hashMap.size());

		CheckResults.checkResult(hashMap.containsKey(1));
		CheckResults.checkResult(hashMap.containsKey(2));
		CheckResults.checkResult(hashMap.containsKey(3));
		CheckResults.checkResult(hashMap.containsKey(4));
		CheckResults.checkResult(hashMap.containsKey(5));
		CheckResults.checkResult(hashMap.containsKey(6));
		CheckResults.checkResult(hashMap.get(1) == 100);
		CheckResults.checkResult(hashMap.get(2) == 200);
		CheckResults.checkResult(hashMap.get(3) == 300);
		CheckResults.checkResult(hashMap.get(4) == 400);
		CheckResults.checkResult(hashMap.get(5) == 500);
		CheckResults.checkResult(hashMap.get(6) == 600);
		CheckResults.checkResult(hashMap.size() == 6);

		System.out.println("REMOVE 1");
		hashMap.remove(1);

		System.out.println("Value for key 1: " + hashMap.get(1));
		System.out.println("Value for key 2: " + hashMap.get(2));
		System.out.println("Value for key 3: " + hashMap.get(3));
		System.out.println("Value for key 4: " + hashMap.get(4));
		System.out.println("Value for key 5: " + hashMap.get(5));
		System.out.println("Value for key 6: " + hashMap.get(6));
		System.out.println("MAP size = " + hashMap.size());

		CheckResults.checkResult(hashMap.containsKey(1) == false);
		CheckResults.checkResult(hashMap.containsKey(2));
		CheckResults.checkResult(hashMap.containsKey(3));
		CheckResults.checkResult(hashMap.containsKey(4));
		CheckResults.checkResult(hashMap.containsKey(5));
		CheckResults.checkResult(hashMap.containsKey(6));
		CheckResults.checkResult(hashMap.get(1) == -1);
		CheckResults.checkResult(hashMap.get(2) == 200);
		CheckResults.checkResult(hashMap.get(3) == 300);
		CheckResults.checkResult(hashMap.get(4) == 400);
		CheckResults.checkResult(hashMap.get(5) == 500);
		CheckResults.checkResult(hashMap.get(6) == 600);
		CheckResults.checkResult(hashMap.size() == 5);

		System.out.println("REMOVE 2");
		hashMap.remove(2);

		System.out.println("Value for key 1: " + hashMap.get(1));
		System.out.println("Value for key 2: " + hashMap.get(2));
		System.out.println("Value for key 3: " + hashMap.get(3));
		System.out.println("Value for key 4: " + hashMap.get(4));
		System.out.println("Value for key 5: " + hashMap.get(5));
		System.out.println("Value for key 6: " + hashMap.get(6));
		System.out.println("MAP size = " + hashMap.size());

		CheckResults.checkResult(hashMap.containsKey(1) == false);
		CheckResults.checkResult(hashMap.containsKey(2) == false);
		CheckResults.checkResult(hashMap.containsKey(3));
		CheckResults.checkResult(hashMap.containsKey(4));
		CheckResults.checkResult(hashMap.containsKey(5));
		CheckResults.checkResult(hashMap.containsKey(6));
		CheckResults.checkResult(hashMap.get(1) == -1);
		CheckResults.checkResult(hashMap.get(2) == -1);
		CheckResults.checkResult(hashMap.get(3) == 300);
		CheckResults.checkResult(hashMap.get(4) == 400);
		CheckResults.checkResult(hashMap.get(5) == 500);
		CheckResults.checkResult(hashMap.get(6) == 600);
		CheckResults.checkResult(hashMap.size() == 4);

		System.out.println("REMOVE 6");
		hashMap.remove(6);
		System.out.println("MAP size = " + hashMap.size());

		System.out.println("Value for key 1: " + hashMap.get(1));
		System.out.println("Value for key 2: " + hashMap.get(2));
		System.out.println("Value for key 3: " + hashMap.get(3));
		System.out.println("Value for key 4: " + hashMap.get(4));
		System.out.println("Value for key 5: " + hashMap.get(5));
		System.out.println("Value for key 6: " + hashMap.get(6));
		System.out.println("MAP size = " + hashMap.size());

		CheckResults.checkResult(hashMap.containsKey(1) == false);
		CheckResults.checkResult(hashMap.containsKey(2) == false);
		CheckResults.checkResult(hashMap.containsKey(3));
		CheckResults.checkResult(hashMap.containsKey(4));
		CheckResults.checkResult(hashMap.containsKey(5));
		CheckResults.checkResult(hashMap.containsKey(6) == false);
		CheckResults.checkResult(hashMap.get(1) == -1);
		CheckResults.checkResult(hashMap.get(2) == -1);
		CheckResults.checkResult(hashMap.get(3) == 300);
		CheckResults.checkResult(hashMap.get(4) == 400);
		CheckResults.checkResult(hashMap.get(5) == 500);
		CheckResults.checkResult(hashMap.get(6) == -1);
		CheckResults.checkResult(hashMap.size() == 3);

		System.out.println("Contains key 1?: " + hashMap.containsKey(1));
		System.out.println("Contains key 2?: " + hashMap.containsKey(2));
		System.out.println("Contains key 3?: " + hashMap.containsKey(3));
		System.out.println("Contains key 4?: " + hashMap.containsKey(4));
		System.out.println("Contains key 5?: " + hashMap.containsKey(5));
		System.out.println("Contains key 6?: " + hashMap.containsKey(6));

		System.out.println("ADD 1, 1000");
		hashMap.put(1, 1000);
		System.out.println("ADD 2, 2000");
		hashMap.put(2, 2000);
		System.out.println("ADD 6, 6000");
		hashMap.put(6, 6000);

		CheckResults.checkResult(hashMap.containsKey(1) == true);
		CheckResults.checkResult(hashMap.containsKey(2) == true);
		CheckResults.checkResult(hashMap.containsKey(3));
		CheckResults.checkResult(hashMap.containsKey(4));
		CheckResults.checkResult(hashMap.containsKey(5));
		CheckResults.checkResult(hashMap.containsKey(6) == true);
		CheckResults.checkResult(hashMap.get(1) == 1000);
		CheckResults.checkResult(hashMap.get(2) == 2000);
		CheckResults.checkResult(hashMap.get(3) == 300);
		CheckResults.checkResult(hashMap.get(4) == 400);
		CheckResults.checkResult(hashMap.get(5) == 500);
		CheckResults.checkResult(hashMap.get(6) == 6000);
		CheckResults.checkResult(hashMap.size() == 6);
		CheckResults.checkResult(hashMap.isEmpty() == false);

		System.out.println("Value for key 1: " + hashMap.get(1));
		System.out.println("Value for key 2: " + hashMap.get(2));
		System.out.println("Value for key 3: " + hashMap.get(3));
		System.out.println("Value for key 4: " + hashMap.get(4));
		System.out.println("Value for key 5: " + hashMap.get(5));
		System.out.println("Value for key 6: " + hashMap.get(6));
		System.out.println("MAP size = " + hashMap.size());

		System.out.println("Contains key 1?: " + hashMap.containsKey(1));
		System.out.println("Contains key 2?: " + hashMap.containsKey(2));
		System.out.println("Contains key 3?: " + hashMap.containsKey(3));
		System.out.println("Contains key 4?: " + hashMap.containsKey(4));
		System.out.println("Contains key 5?: " + hashMap.containsKey(5));
		System.out.println("Contains key 6?: " + hashMap.containsKey(6));

		// ===================================================================================================================
		// ===================================================================================================================

		// ================ CODE TO VERIFY THAT THE ITERATOR IS WORKING
		System.out.println("ITERATING OVER THE KEY,VALUES");
		EntryIterator iter = hashMap.iterator();
		Set<MapEntryIntToInt> hashSetResults = new HashSet<>();
		int count = 0;
		while (iter.hasNext()) {
			MapEntryIntToInt node = iter.next();
			hashSetResults.add(node);
			System.out.println("  Entry :" + node.getKey() + " " + node.getValue());
			count++;
			if (count < 6) {
				CheckResults.checkResult(iter.hasNext() == true);
			} else {
				CheckResults.checkResult(iter.hasNext() == false);
			}
		}
		// Make sure that there are six different elements visited by the iterator
		CheckResults.checkResult(hashSetResults.size() == 6);

		// ================ CODE TO VERIFY THAT THE ITERATOR IS WORKING PROPERLY IF WE
		// REMOVE TWO VALUES
		System.out.println("ITERATING OVER THE KEY,VALUES");
		EntryIterator iter2 = hashMap.iterator();
		Set<MapEntryIntToInt> hashSetResults2 = new HashSet<>();
		int count2 = 0;
		while (iter2.hasNext()) {
			MapEntryIntToInt node = iter2.next();
			count2++;
			if (count2 == 1 || count2 == 3) {
				System.out.println("  Removing entry :" + node.getKey() + " " + node.getValue());
				iter2.remove();
			} else {
				System.out.println("  Entry :" + node.getKey() + " " + node.getValue());
				hashSetResults2.add(node);
			}

			if (count2 < 6) {
				CheckResults.checkResult(iter2.hasNext() == true);
			} else {
				CheckResults.checkResult(iter2.hasNext() == false);
			}
		}
		// Make sure that there are six different elements visited by the iterator
		CheckResults.checkResult(hashSetResults2.size() == 4);

		// ================ CODE TO VERIFY THAT THE ITERATOR IS WORKING PROPERLY
		System.out.println("ITERATING OVER THE KEY,VALUES");
		EntryIterator iter3 = hashMap.iterator();
		Set<MapEntryIntToInt> hashSetResults3 = new HashSet<>();
		int count3 = 0;
		while (iter3.hasNext()) {
			MapEntryIntToInt node3 = iter3.next();
			hashSetResults3.add(node3);
			System.out.println("  Entry :" + node3.getKey() + " " + node3.getValue());
			count3++;
			if (count3 < 4) {
				CheckResults.checkResult(iter3.hasNext() == true);
			} else {
				CheckResults.checkResult(iter3.hasNext() == false);
			}
		}
		// Make sure that there are six different elements visited by the iterator
		CheckResults.checkResult(hashSetResults3.size() == 4);

		System.out.println("CLEARING THE MAP");
		hashMap.clear();
		CheckResults.checkResult(hashMap.isEmpty() == true);
		CheckResults.checkResult(hashMap.size() == 0);
		CheckResults.checkResult(hashMap.containsKey(1) == false);
		CheckResults.checkResult(hashMap.containsKey(2) == false);
		CheckResults.checkResult(hashMap.containsKey(3) == false);
		CheckResults.checkResult(hashMap.containsKey(4) == false);
		CheckResults.checkResult(hashMap.containsKey(5) == false);
		CheckResults.checkResult(hashMap.containsKey(6) == false);

		System.out.println("WE DO AN ITERATOR ON AN EMPTY MAP");
		EntryIterator iter4 = hashMap.iterator();
		CheckResults.checkResult(iter4.hasNext() == false);
		CheckResults.checkResult(hashMap.size() == 0);
		CheckResults.checkResult(hashMap.isEmpty() == true);
		System.out.println("OK.");

		System.out.println("ADD 9, 90");
		hashMap.put(9, 90);
		CheckResults.checkResult(hashMap.size() == 1);
		CheckResults.checkResult(hashMap.containsKey(9));
		CheckResults.checkResult(hashMap.get(9) == 90);

		System.out.println("WE DO AN ITERATOR ON THAT MAP");
		EntryIterator iter5 = hashMap.iterator();
		MapEntryIntToInt x = iter5.next();
		System.out.println(" It contains : " + x.getKey() + " , " + x.getValue());
		CheckResults.checkResult(x.getKey() == 9 && x.getValue() == 90);
		CheckResults.checkResult(iter5.hasNext() == false);
		CheckResults.checkResult(hashMap.size() == 1);
		CheckResults.checkResult(hashMap.isEmpty() == false);

		System.out.println("WE DO AN ITERATOR AGAIN ON THAT MAP");
		EntryIterator iter6 = hashMap.iterator();
		MapEntryIntToInt x6 = iter6.next();
		System.out.println(" It contains : " + x6.getKey() + " , " + x6.getValue());
		CheckResults.checkResult(x6.getKey() == 9 && x6.getValue() == 90);
		CheckResults.checkResult(iter6.hasNext() == false);
		CheckResults.checkResult(hashMap.size() == 1);
		CheckResults.checkResult(hashMap.isEmpty() == false);
		System.out.println("Now remove the current entry from the map");
		iter6.remove();
		CheckResults.checkResult(iter6.hasNext() == false);
		CheckResults.checkResult(iter6.hasNext() == false);
		CheckResults.checkResult(hashMap.size() == 0);
		CheckResults.checkResult(hashMap.size() == 0);
		CheckResults.checkResult(hashMap.isEmpty() == true);
		CheckResults.checkResult(hashMap.isEmpty() == true);
		System.out.println("The map is empty, and hasNext = " + iter6.hasNext());

		// ===================================================================================================================
		// ===================================================================================================================
		// ===================================================================================================================
		// ====================================== RANDOM TESTS
		// ================================================
		// Randomly add some numbers between 1 to 100 to the set
		// and compare the number of elements with the HashSet of Java.
		hashMap.clear();
		CheckResults.checkResult(hashMap.isEmpty() == true);
		CheckResults.checkResult(hashMap.size() == 0);

		HashMap<Integer, Integer> mirror = new HashMap<Integer, Integer>();

		Random rand = new Random(System.currentTimeMillis());
		int i = 0;
		while (i < 50) {
			int randomNumber = rand.nextInt(100) + 1;
			hashMap.put(randomNumber, randomNumber + 1);
			mirror.put(randomNumber, randomNumber + 1);
			i++;

			// Result should be the same
			CheckResults.checkResult(hashMap.size() == mirror.size());
			CheckResults.checkResult(hashMap.get(randomNumber) == mirror.get(randomNumber));
		}
		System.out.println(hashMap.size());

		i = 0;
		while (i < 10) {
			int randomNumber = rand.nextInt(100) + 1;
			hashMap.remove(randomNumber);
			mirror.remove(randomNumber);
			i++;

			// Result should be the same
			CheckResults.checkResult(hashMap.size() == mirror.size());
			CheckResults.checkResult(hashMap.get(randomNumber) == -1);
		}
		System.out.println(hashMap.size());
		// ======================================================================================

		// TESTS FOR "getAndIncreaseValueBy "

		AMapIntToInt hashMap2 = new AMapIntToInt(bucketCount);
		System.out.println("MAP size = " + hashMap2.size());
		CheckResults.checkResult(hashMap2.size() == 0);
		CheckResults.checkResult(hashMap2.isEmpty() == true);

		System.out.println("ADD 1, 10");
		hashMap2.put(1,  10);
		CheckResults.checkResult(hashMap2.size() == 1);
		CheckResults.checkResult(hashMap2.containsKey(1));
		CheckResults.checkResult(hashMap2.get(1) == 10);

		System.out.println("ADD 2, 20");
		hashMap2.put(2,  20);
		CheckResults.checkResult(hashMap2.size() == 2);
		CheckResults.checkResult(hashMap2.containsKey(2));
		CheckResults.checkResult(hashMap2.get(2) == 20);

		System.out.println("CHANGE VALUE OF KEY 2  TO :   20 + 5 = 25");
		hashMap2.getAndIncreaseValueBy(2,  5);
		CheckResults.checkResult(hashMap2.containsKey(2));
		CheckResults.checkResult(hashMap2.get(1) == 10);
		CheckResults.checkResult(hashMap2.get(2) == 25);

		System.out.println("CHANGE VALUE OF KEY 2  TO :   25 + 5 = 30");
		System.out.println("CHANGE VALUE OF KEY 1  TO :   10 + 2 = 12");
		hashMap2.getAndIncreaseValueBy(1,  2);
		hashMap2.getAndIncreaseValueBy(2,  5);
		CheckResults.checkResult(hashMap2.size() == 2);
		CheckResults.checkResult(hashMap2.containsKey(1));
		CheckResults.checkResult(hashMap2.containsKey(2));
		CheckResults.checkResult(hashMap2.get(1) == 12);
		CheckResults.checkResult(hashMap2.get(2) == 30);

		// Try to change key 3 and 4 to values 33 and 44 respectively
		hashMap2.getAndIncreaseValueBy(3,  33);
		hashMap2.getAndIncreaseValueBy(4,  44);
		CheckResults.checkResult(hashMap2.size() == 4);
		CheckResults.checkResult(hashMap2.containsKey(1));
		CheckResults.checkResult(hashMap2.containsKey(2));
		CheckResults.checkResult(hashMap2.containsKey(3));
		CheckResults.checkResult(hashMap2.containsKey(4));
		CheckResults.checkResult(hashMap2.get(1) == 12);
		CheckResults.checkResult(hashMap2.get(2) == 30);
		CheckResults.checkResult(hashMap2.get(3) == 33);
		CheckResults.checkResult(hashMap2.get(4) == 44);
		// ======================================================================================
		///===== VALUE ITERATOR
		
		ValueIterator iterVal = hashMap2.iteratorForValues();
		Set setValues = new HashSet<Integer>();
		while (iterVal.hasNext()) {
			int value = iterVal.next();
			System.out.println("Value : " + value);
			setValues.add(value);
		}

		CheckResults.checkResult(setValues.contains(12));
		CheckResults.checkResult(setValues.contains(30));
		CheckResults.checkResult(setValues.contains(33));
		CheckResults.checkResult(setValues.contains(44));
		CheckResults.checkResult(setValues.size()==4);

		//===== KEY ITERATOR
		KeyIterator iterKey = hashMap2.iteratorForKeys();
		Set setKeys = new HashSet<Integer>();
		while (iterKey.hasNext()) {
			int key = iterKey.next();
			System.out.println("key : " + key);
			setKeys.add(key);
		}
		
		CheckResults.checkResult(setKeys.contains(1));
		CheckResults.checkResult(setKeys.contains(2));
		CheckResults.checkResult(setKeys.contains(3));
		CheckResults.checkResult(setKeys.contains(4));
		CheckResults.checkResult(setKeys.size()==4);

	}
}
