package ca.pfv.spmf.tools;
/*
 *  Copyright (c) 2008-2012 Philippe Fournier-Viger
 * 
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * This class is used to record the maximum memory usaged of an algorithm during
 * a given execution. It is implemented by using the "singleton" design pattern.
 * The memory logger has the option of saving the logged values to a file if it
 * is set to be in recording mode, and a file path is provided.
 *
 */
public class MemoryLogger {

	// the only instance of this class (this is the "singleton" design pattern)
	private static MemoryLogger instance = new MemoryLogger();

	// variable to store the maximum memory usage
	private double maxMemory = 0;

	// A boolean flag to indicate whether the recording mode is on or off
	private boolean recordingMode = false;

	// A file object to store the output file name
	private File outputFile = null;

	// A buffered writer object to write the memory usage values to the file
	private BufferedWriter writer = null;

	/**
	 * Method to obtain the only instance of this class
	 * 
	 * @return instance of MemoryLogger
	 */
	public static MemoryLogger getInstance() {
		return instance;
	}

	/**
	 * To get the maximum amount of memory used until now
	 * 
	 * @return a double value indicating memory as megabytes
	 */
	public double getMaxMemory() {
		return maxMemory;
	}

	/**
	 * Reset the maximum amount of memory recorded.
	 */
	public void reset() {
		maxMemory = 0;
	}

	/**
	 * Check the current memory usage and record it if it is higher than the amount
	 * of memory previously recorded.
	 * 
	 * @return the memory usage in megabytes
	 */
	public double checkMemory() {
		double currentMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024d / 1024d;
		if (currentMemory > maxMemory) {
			maxMemory = currentMemory;
		}
		// If the recording mode is on
		if (recordingMode) {
			// Try to write the current memory value to the file
			try {
				writer.write(currentMemory + "\n");
			} catch (IOException e) {
				// Handle the exception
				e.printStackTrace();
			}
		}
		return currentMemory;
	}

	/**
	 * A method to set the recording mode and the output file name
	 * 
	 * @param mode     if true, recording mode is activated
	 * @param fileName the path to a file for saving recorded values
	 */
	public void startRecordingMode(String fileName) {
		// Set the recording mode flag
		recordingMode = true;
		// Create a new file object with the given file name
		outputFile = new File(fileName);
		// Try to create a new buffered writer object with the file object
		try {
			writer = new BufferedWriter(new FileWriter(outputFile));
		} catch (IOException e) {
			// Handle the exception
			e.printStackTrace();
		}
	}

	/**
	 * A method to stop the recording mode and close the file
	 */
	public void stopRecordingMode() {
		// If the recording mode is on
		if (recordingMode) {
			// Try to close the buffered writer object
			try {
				writer.close();
			} catch (IOException e) {
				// Handle the exception
				e.printStackTrace();
			}
			// Set the recording mode flag to false
			recordingMode = false;
		}
	}

}
