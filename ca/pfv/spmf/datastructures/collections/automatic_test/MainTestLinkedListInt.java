package ca.pfv.spmf.datastructures.collections.automatic_test;

import ca.pfv.spmf.datastructures.collections.linkedlist.LinkedListInt;
import ca.pfv.spmf.datastructures.collections.list.ListInt;

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
 * A class for testing the LinkedListInt class
 * 
 * @author Philippe Fournier-Viger, 2023
 */
public class MainTestLinkedListInt {

	public static void main(String[] args) {
		// Run the same experiment while varying the initial list size
		// We test this because this should not change the behavior of the structure
		// from the outside.
		for (int initialSize = 1; initialSize <= 10; initialSize++) {
			runExperiment(initialSize);
		}
	}

	private static void runExperiment(int initialSize) {
		LinkedListInt list = new LinkedListInt();
		printContent(list);
		CheckResults.checkResult(list.size() == 0);
		CheckResults.checkResult(list.isEmpty() == true);

		addToList(list, 1);
		printContent(list);
		CheckResults.checkResult(list.size() == 1);
		CheckResults.checkResult(list.get(0) == 1);
		CheckResults.checkResult(list.isEmpty() == false);

		addToList(list, 2);
		printContent(list);
		CheckResults.checkResult(list.size() == 2);
		CheckResults.checkResult(list.get(0) == 1);
		CheckResults.checkResult(list.get(1) == 2);
		CheckResults.checkResult(list.isEmpty() == false);

		addToList(list, 3);
		printContent(list);
		CheckResults.checkResult(list.size() == 3);
		CheckResults.checkResult(list.get(0) == 1);
		CheckResults.checkResult(list.get(1) == 2);
		CheckResults.checkResult(list.get(2) == 3);
		CheckResults.checkResult(list.isEmpty() == false);

		addToList(list, 4);
		printContent(list);
		CheckResults.checkResult(list.size() == 4);
		CheckResults.checkResult(list.get(0) == 1);
		CheckResults.checkResult(list.get(1) == 2);
		CheckResults.checkResult(list.get(2) == 3);
		CheckResults.checkResult(list.get(3) == 4);
		CheckResults.checkResult(list.isEmpty() == false);

		addToList(list, 5);
		printContent(list);
		CheckResults.checkResult(list.size() == 5);
		CheckResults.checkResult(list.get(0) == 1);
		CheckResults.checkResult(list.get(1) == 2);
		CheckResults.checkResult(list.get(2) == 3);
		CheckResults.checkResult(list.get(3) == 4);
		CheckResults.checkResult(list.get(4) == 5);
		CheckResults.checkResult(list.isEmpty() == false);

		CheckResults.checkResult(list.indexOf(4) == 3);
		CheckResults.checkResult(list.indexOf(8) == -1);
		CheckResults.checkResult(list.indexOf(9) == -1);

		removeAtFromList(list, 0);
		printContent(list);
		CheckResults.checkResult(list.size() == 4);
		CheckResults.checkResult(list.get(0) == 2);
		CheckResults.checkResult(list.get(1) == 3);
		CheckResults.checkResult(list.get(2) == 4);
		CheckResults.checkResult(list.get(3) == 5);
		CheckResults.checkResult(list.isEmpty() == false);

		removeAtFromList(list, 2);
		printContent(list);
		CheckResults.checkResult(list.size() == 3);
		CheckResults.checkResult(list.get(0) == 2);
		CheckResults.checkResult(list.get(1) == 3);
		CheckResults.checkResult(list.get(2) == 5);
		CheckResults.checkResult(list.isEmpty() == false);

		removeAtFromList(list, 2);
		printContent(list);
		CheckResults.checkResult(list.size() == 2);
		CheckResults.checkResult(list.get(0) == 2);
		CheckResults.checkResult(list.get(1) == 3);
		CheckResults.checkResult(list.isEmpty() == false);

		removeAtFromList(list, 0);
		printContent(list);
		CheckResults.checkResult(list.size() == 1);
		CheckResults.checkResult(list.get(0) == 3);
		CheckResults.checkResult(list.isEmpty() == false);

		removeAtFromList(list, 0);
		printContent(list);
		CheckResults.checkResult(list.size() == 0);
		CheckResults.checkResult(list.isEmpty() == true);

		list.clear();
		printContent(list);
		System.out.println("Is empty ? :" + list.isEmpty());
		CheckResults.checkResult(list.size() == 0);
		CheckResults.checkResult(list.isEmpty() == true);

		addToList(list, 1);
		printContent(list);
		System.out.println("Is empty ? :" + list.isEmpty());
		CheckResults.checkResult(list.size() == 1);
		CheckResults.checkResult(list.get(0) == 1);
		CheckResults.checkResult(list.isEmpty() == false);

		addToList(list, 2);
		printContent(list);
		System.out.println("Is empty ? :" + list.isEmpty());

		CheckResults.checkResult(list.size() == 2);
		CheckResults.checkResult(list.get(0) == 1);
		CheckResults.checkResult(list.get(1) == 2);
		CheckResults.checkResult(list.isEmpty() == false);

		addToList(list, 3);
		printContent(list);

		CheckResults.checkResult(list.size() == 3);
		CheckResults.checkResult(list.get(0) == 1);
		CheckResults.checkResult(list.get(1) == 2);
		CheckResults.checkResult(list.get(2) == 3);
		CheckResults.checkResult(list.isEmpty() == false);

		System.out.println("List contains 1 ? :" + list.contains(1));
		System.out.println("List contains 2 ? :" + list.contains(2));
		System.out.println("List contains 3 ? :" + list.contains(3));
		System.out.println("List contains 4 ? :" + list.contains(4));
		System.out.println("Is empty ? :" + list.isEmpty());

		System.out.println("CLEAR");
		list.clear();
		printContent(list);
		System.out.println("Is empty ? :" + list.isEmpty());

		CheckResults.checkResult(list.size() == 0);
		CheckResults.checkResult(list.isEmpty() == true);

		addToList(list, 5);
		addToList(list, 5);
		addToList(list, 1);
		addToList(list, 5);
		addToList(list, 2);
		addToList(list, 5);
		addToList(list, 3);
		addToList(list, 4);
		addToList(list, 5);
		addToList(list, 6);
		addToList(list, 5);
		addToList(list, 5);
		printContent(list);

		CheckResults.checkResult(list.indexOf(5) == 0);
		CheckResults.checkResult(list.indexOf(3) == 6);
		CheckResults.checkResult(list.indexOf(4) == 7);
		CheckResults.checkResult(list.indexOf(5) == 0);
		CheckResults.checkResult(list.indexOf(6) == 9);
		CheckResults.checkResult(list.indexOf(8) == -1);
		CheckResults.checkResult(list.indexOf(9) == -1);

		CheckResults.checkResult(list.size() == 12);
		CheckResults.checkResult(list.get(0) == 5);
		CheckResults.checkResult(list.get(1) == 5);
		CheckResults.checkResult(list.get(2) == 1);
		CheckResults.checkResult(list.get(3) == 5);
		CheckResults.checkResult(list.get(4) == 2);
		CheckResults.checkResult(list.get(5) == 5);
		CheckResults.checkResult(list.get(6) == 3);
		CheckResults.checkResult(list.get(7) == 4);
		CheckResults.checkResult(list.get(8) == 5);
		CheckResults.checkResult(list.get(9) == 6);
		CheckResults.checkResult(list.get(10) == 5);
		CheckResults.checkResult(list.get(11) == 5);

		System.out.println("REMOVE THE VALUE 5:");
		list.remove(5);
		printContent(list);

		CheckResults.checkResult(list.size() == 5);
		CheckResults.checkResult(list.get(0) == 1);
		CheckResults.checkResult(list.get(1) == 2);
		CheckResults.checkResult(list.get(2) == 3);
		CheckResults.checkResult(list.get(3) == 4);
		CheckResults.checkResult(list.get(4) == 6);

		printContent(list);

		System.out.println("CLEAR");
		list.clear();
		printContent(list);
		System.out.println("Is empty ? :" + list.isEmpty());

		CheckResults.checkResult(list.size() == 0);
		CheckResults.checkResult(list.isEmpty() == true);

		System.out.println(" ADD  1,2,3 and then ");
		list.add(1);
		list.add(2);
		list.add(3);
		System.out.println("SET THE VALUE AT POSITION 0  TO 8");
		System.out.println("SET THE VALUE AT POSITION 1  TO 9");
		System.out.println("SET THE VALUE AT POSITION 2  TO 10");
		list.set(0, 8);
		list.set(1, 9);
		list.set(2, 10);

		CheckResults.checkResult(list.size() == 3);
		CheckResults.checkResult(list.get(0) == 8);
		CheckResults.checkResult(list.get(1) == 9);
		CheckResults.checkResult(list.get(2) == 10);

		printContent(list);

	}

	private static void indexOfVal(LinkedListInt list, int val) {
		System.out.println("index of value " + val + "  is: " + list.indexOf(val));
	}

	private static void printContent(LinkedListInt list) {

		System.out.println("LIST size = " + list.size());
		for (int i = 0; i < list.size(); i++) {
			System.out.println(" [" + i + "] = " + list.get(i));
		}
	}

	private static void addToList(LinkedListInt list, int value) {
		System.out.println("ADD " + value);
		list.add(value);
	}

	private static void removeAtFromList(LinkedListInt list, int index) {
		System.out.println("REMOVE At " + index);
		list.removeAt(index);
	}
}
