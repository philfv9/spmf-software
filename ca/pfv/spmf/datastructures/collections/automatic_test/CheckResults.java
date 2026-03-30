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
 * Class that provides a function for testing. The function checkResult verify
 * if a condition is true. If false, it throws an error.
 * 
 * @author Philippe Fournier-Viger, copyright 2023.
 */

class CheckResults {
	/** The number of tests that was done until now */
	private static long testCount = 0;

	/**
	 * Check if a condition is met. Throw an exception if the value is false.
	 */
	static void checkResult(boolean value) {
		testCount++;
		if (value == false) {
			throw new RuntimeException("Failed");
		}
	}

	/**
	 * Get the number of tests that was done
	 * 
	 * @return the number of tests
	 */
	public static long getTestDoneCount() {
		return testCount;
	}
}
