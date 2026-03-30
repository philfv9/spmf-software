package ca.pfv.spmf.datastructures.collections.automatic_test;

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
 * Class to automatically run some tests from this package for testing the
 * primitive data structures of SPMF
 * 
 * @author Philippe Fournier-Viger, copyright 2023.
 */
public class RunAllTests {

	public static void main(String[] args) {

		// Run all the tests.
		// If no error is thrown, then the test have completed
		// correctly.

		// Tests for LHashSet___
		MainTestLHashSetInt.main(null);
		MainTestLHashSetObject.main(null);

		// Tests for AHashSet___
		MainTestAHashSetInt.main(null);
		MainTestAHashSetObject.main(null);

		// Tests for ArrayList___
		MainTestLinkedListInt.main(null);
		MainTestArrayListInt.main(null);
		MainTestArrayListObject.main(null);
		MainTestArrayListDouble.main(null);
		MainTestArrayListLong.main(null);
		MainTestArrayListFloat.main(null);
		MainTestArrayListShort.main(null);

		// Tests for AMap______
		MainTestAMapIntToInt.main(null);
		MainTestAMapIntToLong.main(null);
		MainTestAMapIntToDouble.main(null);
		MainTestAMapIntToObject.main(null);
		MainTestAMapIntToFloat.main(null);
		MainTestAMapIntToShort.main(null);

		// Tests for LMap______
		MainTestLMapIntToDouble.main(null);
		MainTestLMapIntToInt.main(null);
		MainTestLMapIntToLong.main(null);
		MainTestLMapIntToObject.main(null);
		MainTestLMapIntToFloat.main(null);
		MainTestLMapIntToShort.main(null);
		System.out.println("==== All " + CheckResults.getTestDoneCount() + " tests completed successfully! ===");

	}

}
