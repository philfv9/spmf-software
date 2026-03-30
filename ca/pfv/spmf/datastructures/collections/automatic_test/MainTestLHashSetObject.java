package ca.pfv.spmf.datastructures.collections.automatic_test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import ca.pfv.spmf.datastructures.collections.set.LHashSetObject;
import ca.pfv.spmf.datastructures.collections.set.SetObject;
import ca.pfv.spmf.datastructures.collections.set.SetObject.AEntryIterator;

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
 * A class for testing the IntInt HashMap class.
 * 
 * @author Philippe Fournier-Viger, 2023
 */
public class MainTestLHashSetObject {

	public static void main(String[] args) {
		
		// Run the same experiment while varying the number of buckets in the map from 1 to 100
		// and the initial collision array size from 1 to 100.
		// We test this because these two factors should not change the behavior of the structure from the outside.
		for(int bucketCount=1; bucketCount<=20; bucketCount++) {
			runExperiment(bucketCount);
		}
	}

	private static void runExperiment(int bucketCount) {
		SetObject<Integer> set = new LHashSetObject<Integer>(bucketCount);
		System.out.println("SET size = " + set.size());
		System.out.println("ADD 1");
		set.add(1);
		System.out.println("MAP size = " + set.size());

		CheckResults.checkResult(set.size() == 1);

		System.out.println("1 is in the map? " + set.contains(1));
		System.out.println("SET size = " + set.size());

		CheckResults.checkResult(set.contains(1));
		CheckResults.checkResult(set.size() == 1);

		System.out.println("ADD 1");
		set.add(1);
		CheckResults.checkResult(set.contains(1));
		System.out.println("ADD 2");
		set.add(2);
		CheckResults.checkResult(set.contains(2));
		System.out.println("ADD 3");
		set.add(3);
		CheckResults.checkResult(set.contains(3));
		System.out.println("ADD 4");
		set.add(4);
		CheckResults.checkResult(set.contains(4));
		System.out.println("ADD 5");
		set.add(5);
		CheckResults.checkResult(set.contains(5));
		System.out.println("ADD 6");
		set.add(6);
		CheckResults.checkResult(set.contains(6));

		System.out.println("SET size = " + set.size());
		CheckResults.checkResult(set.size() == 6);

		System.out.println("1 is in the map? " + set.contains(1));
		System.out.println("2 is in the map? " + set.contains(2));
		System.out.println("3 is in the map? " + set.contains(3));
		System.out.println("4 is in the map? " + set.contains(4));
		System.out.println("5 is in the map? " + set.contains(5));
		System.out.println("6 is in the map? " + set.contains(6));

		System.out.println("REMOVE 1");
		set.remove(1);

		CheckResults.checkResult(set.contains(1) == false);
		CheckResults.checkResult(set.size() == 5);
		CheckResults.checkResult(set.contains(2));
		CheckResults.checkResult(set.contains(3));
		CheckResults.checkResult(set.contains(4));
		CheckResults.checkResult(set.contains(5));
		CheckResults.checkResult(set.contains(6));

		System.out.println("1 is in the map? " + set.contains(1));
		System.out.println("2 is in the map? " + set.contains(2));
		System.out.println("3 is in the map? " + set.contains(3));
		System.out.println("4 is in the map? " + set.contains(4));
		System.out.println("5 is in the map? " + set.contains(5));
		System.out.println("6 is in the map? " + set.contains(6));
		System.out.println("SET size = " + set.size());

		System.out.println("REMOVE 2");
		set.remove(2);

		CheckResults.checkResult(set.contains(1) == false);
		CheckResults.checkResult(set.contains(2) == false);
		CheckResults.checkResult(set.contains(3));
		CheckResults.checkResult(set.contains(4));
		CheckResults.checkResult(set.contains(5));
		CheckResults.checkResult(set.contains(6));
		CheckResults.checkResult(set.size() == 4);

		System.out.println("1 is in the map? " + set.contains(1));
		System.out.println("2 is in the map? " + set.contains(2));
		System.out.println("3 is in the map? " + set.contains(3));
		System.out.println("4 is in the map? " + set.contains(4));
		System.out.println("5 is in the map? " + set.contains(5));
		System.out.println("6 is in the map? " + set.contains(6));
		System.out.println("SET size = " + set.size());

		System.out.println("REMOVE 6");
		set.remove(6);

		CheckResults.checkResult(set.contains(1) == false);
		CheckResults.checkResult(set.contains(2) == false);
		CheckResults.checkResult(set.contains(3));
		CheckResults.checkResult(set.contains(4));
		CheckResults.checkResult(set.contains(5));
		CheckResults.checkResult(set.contains(6) == false);
		CheckResults.checkResult(set.size() == 3);

		System.out.println("1 is in the map? " + set.contains(1));
		System.out.println("2 is in the map? " + set.contains(2));
		System.out.println("3 is in the map? " + set.contains(3));
		System.out.println("4 is in the map? " + set.contains(4));
		System.out.println("5 is in the map? " + set.contains(5));
		System.out.println("6 is in the map? " + set.contains(6));
		System.out.println("SET size = " + set.size());

		System.out.println("ADD 4");
		set.add(4);
		System.out.println("ADD 5");
		set.add(5);
		System.out.println("ADD 6");
		set.add(6);

		CheckResults.checkResult(set.contains(1) == false);
		CheckResults.checkResult(set.contains(2) == false);
		CheckResults.checkResult(set.contains(3));
		CheckResults.checkResult(set.contains(4));
		CheckResults.checkResult(set.contains(5));
		CheckResults.checkResult(set.contains(6));
		CheckResults.checkResult(set.size() == 4);

		System.out.println("1 is in the map? " + set.contains(1));
		System.out.println("2 is in the map? " + set.contains(2));
		System.out.println("3 is in the map? " + set.contains(3));
		System.out.println("4 is in the map? " + set.contains(4));
		System.out.println("5 is in the map? " + set.contains(5));
		System.out.println("6 is in the map? " + set.contains(6));
		System.out.println("SET size = " + set.size());

		// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
		// Now the set contains 3, 4, 5, 6
		// !!!!!!!!!!!!!!!!!!!!!!!

		// ================ CODE TO VERIFY THAT THE ITERATOR IS WORKING PROPERLY
		System.out.println("ITERATING OVER THE KEY,VALUES");
		AEntryIterator iter3 = set.iterator();
		Set<Integer> setResults3 = new HashSet<Integer>();
		int count3 = 0;
		Integer removedElement = -1;
		// We chose randomly two elements to be removed
		int elementToBeRemoved = new Random().nextInt(4) + 1;
		while (iter3.hasNext()) {
			Integer value = (Integer) iter3.next();
			System.out.println("  Entry :" + value);
			count3++;
			if (count3 == elementToBeRemoved) {
				System.out.println("REMOVE THE RANDOM ELEMENT : " + value);
				iter3.remove();
				removedElement = value;
			} else {
				setResults3.add(value);
			}

			if (count3 < 4) {
				CheckResults.checkResult(iter3.hasNext() == true);
			} else {
				CheckResults.checkResult(iter3.hasNext() == false);
			}
		}
		// Make sure that there are six different elements visited by the iterator
		CheckResults.checkResult(setResults3.size() == 3);

		System.out.println("ADD THE REMOVED ELEMENT :" + removedElement);
		set.add(removedElement);

		System.out.println("CLEARING THE SET");
		set.clear();
		CheckResults.checkResult(set.isEmpty() == true);
		CheckResults.checkResult(set.size() == 0);
		CheckResults.checkResult(set.contains(1) == false);
		CheckResults.checkResult(set.contains(2) == false);
		CheckResults.checkResult(set.contains(3) == false);
		CheckResults.checkResult(set.contains(4) == false);
		CheckResults.checkResult(set.contains(5) == false);
		CheckResults.checkResult(set.contains(6) == false);

		System.out.println("WE DO AN ITERATOR ON AN EMPTY SET");
		AEntryIterator iter4 = set.iterator();
		CheckResults.checkResult(iter4.hasNext() == false);
		CheckResults.checkResult(set.size() == 0);
		CheckResults.checkResult(set.isEmpty() == true);
		System.out.println("OK.");
//		
		System.out.println("ADD 9");
		set.add(9);
		CheckResults.checkResult(set.size() == 1);
		CheckResults.checkResult(set.contains(9));

		System.out.println("WE DO AN ITERATOR ON THAT SET");
		AEntryIterator iter5 = set.iterator();
		int x = (Integer) iter5.next();
		System.out.println(" It contains : " + x);
		CheckResults.checkResult(x == 9);
		CheckResults.checkResult(iter5.hasNext() == false);
		CheckResults.checkResult(set.size() == 1);
		CheckResults.checkResult(set.isEmpty() == false);

		System.out.println("WE DO AN ITERATOR AGAIN ON THAT SET");
		AEntryIterator iter6 = set.iterator();
		int x6 = (Integer) iter6.next();
		System.out.println(" It contains : " + x);
		CheckResults.checkResult(x == 9);
		CheckResults.checkResult(iter6.hasNext() == false);
		CheckResults.checkResult(set.size() == 1);
		CheckResults.checkResult(set.isEmpty() == false);
		System.out.println("Now remove the current entry from the map");
		iter6.remove();
		CheckResults.checkResult(iter6.hasNext() == false);
		CheckResults.checkResult(iter6.hasNext() == false);
		CheckResults.checkResult(set.size() == 0);
		CheckResults.checkResult(set.size() == 0);
		CheckResults.checkResult(set.isEmpty() == true);
		CheckResults.checkResult(set.isEmpty() == true);
		System.out.println("The map is empty, and hasNext = " + iter6.hasNext());

		// ===================================================================================================================
		// ===================================================================================================================
		// ===================================================================================================================
		// ====================================== RANDOM TESTS
		// ================================================
		// Randomly add some numbers between 1 to 100 to the set
		// and compare the number of elements with the HashSet of Java.
		set.clear();
		CheckResults.checkResult(set.isEmpty() == true);
		CheckResults.checkResult(set.size() == 0);

		HashSet<Integer> mirror = new HashSet<Integer>();

		Random rand = new Random(System.currentTimeMillis());
		int i = 0;
		while (i < 50) {
			int randomNumber = rand.nextInt(100) + 1;
			set.add(randomNumber);
			mirror.add(randomNumber);
			i++;
		}
		// Result should be the same
		CheckResults.checkResult(set.size() == mirror.size());
		System.out.println(set.size());

		// Randomly remove 20 elements
		i = 0;
		while (i < 50) {
			int randomNumber = rand.nextInt(100) + 1;
			set.remove(randomNumber);
			mirror.remove(randomNumber);
			i++;
		}
		// Result should be the same
		CheckResults.checkResult(set.size() == mirror.size());
		System.out.println(set.size());

		// ======================================================================================
	}

}
