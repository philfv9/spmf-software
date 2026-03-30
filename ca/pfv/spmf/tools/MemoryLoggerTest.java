package ca.pfv.spmf.tools;

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
