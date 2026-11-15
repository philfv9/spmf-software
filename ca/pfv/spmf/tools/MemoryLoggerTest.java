package ca.pfv.spmf.tools;
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
 * 
 * Do not remove copyright and license information
 */

/**
 * This class is for testing the Memory Logger tool.
 * @author Philippe Fournier-Viger
 */
public class MemoryLoggerTest {

	public static void main(String[] args) {
		// Reset the recorded memory usage
		MemoryLogger.getInstance().reset();

		// Check the memory usage
		MemoryLogger.getInstance().checkMemory();

		// Print the maximum memory usage until now.
		System.out.println("Max memory : " + MemoryLogger.getInstance().getMaxMemory());

//		int[][] array = new int[99999][9999];

		// Check the memory usage
		MemoryLogger.getInstance().checkMemory();

		// Print the maximum memory usage until now.
		System.out.println("Max memory : " + MemoryLogger.getInstance().getMaxMemory());

		// Create an instance of the memory logger
		MemoryLogger logger = MemoryLogger.getInstance();
		// Set the recording mode to true and the output file name to "memory.log"
		logger.startRecordingMode("memory.log");
		// Call some methods that use different amounts of memory
		method1();
		method2();
		method3();
		// Stop the recording mode and close the file
		logger.stopRecordingMode();
		// Print the maximum memory usage
		System.out.println("Maximum memory usage: " + logger.getMaxMemory() + " MB");
	}

	// A method that uses a small amount of memory
	public static void method1() {
		// Create a small array of integers
		int[] array = new int[10];
		// Fill the array with some values
		for (int i = 0; i < array.length; i++) {
			array[i] = i * i;
		}
		// Check the memory usage and print it
		double memory = MemoryLogger.getInstance().checkMemory();
		System.out.println("Memory usage in method1: " + memory + " MB");
	}

	// A method that uses a medium amount of memory
	public static void method2() {
		// Create a medium array of strings
		String[] array = new String[1000];
		// Fill the array with some values
		for (int i = 0; i < array.length; i++) {
			array[i] = "This is a string number " + i;
		}
		// Check the memory usage and print it
		double memory = MemoryLogger.getInstance().checkMemory();
		System.out.println("Memory usage in method2: " + memory + " MB");
	}

	// A method that uses a large amount of memory
	public static void method3() {
		// Create a large array of objects
		Object[] array = new Object[100000];
		// Fill the array with some values
		for (int i = 0; i < array.length; i++) {
			array[i] = new Object();
		}
		// Check the memory usage and print it
		double memory = MemoryLogger.getInstance().checkMemory();
		System.out.println("Memory usage in method3: " + memory + " MB");
	}
}
