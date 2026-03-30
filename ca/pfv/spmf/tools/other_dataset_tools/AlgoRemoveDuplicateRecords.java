package ca.pfv.spmf.tools.other_dataset_tools;

/* This file is copyright (c) 2008-2024 Philippe Fournier-Viger
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ca.pfv.spmf.test.MainTestRemoveDuplicateRecords;
import ca.pfv.spmf.tools.MemoryLogger;

/**
 * This algorithm removes duplicate records (lines) from a file. It keeps only
 * the first occurrence of each unique record. Metadata lines (starting with #,
 * %, @ or empty lines) are preserved and copied to the output file in their
 * original positions.
 * 
 * @author Philippe Fournier-Viger
 * @see MainTestRemoveDuplicateRecords
 */
public class AlgoRemoveDuplicateRecords {

	/** the number of data records in the input file */
	private int recordCountInput = 0;

	/** the number of data records in the output file */
	private int recordCountOutput = 0;

	/** the number of duplicates removed */
	private int duplicateCount = 0;

	/** the number of metadata lines */
	private int metadataLineCount = 0;

	/** the time the algorithm started */
	private long startTimestamp = 0;

	/** the time the algorithm ended */
	private long endTimestamp = 0;
	
	/** Max memory **/
	double maxMemory = 0;

	/**
	 * Default constructor
	 */
	public AlgoRemoveDuplicateRecords() {
	}

	/**
	 * Run the algorithm to remove duplicate records from a file. Metadata lines
	 * (starting with #, %, @ or empty lines) are preserved.
	 * 
	 * @param inputFile  the path to the input file
	 * @param outputFile the path to the output file
	 * @throws IOException if an I/O error occurs
	 */
	public void runAlgorithm(String inputFile, String outputFile) throws IOException {
		// Reset statistics
		recordCountInput = 0;
		recordCountOutput = 0;
		duplicateCount = 0;
		metadataLineCount = 0;
		maxMemory = 0;
		
		// Reset memory usage logger
		MemoryLogger.getInstance().reset();

		// Record start time
		startTimestamp = System.currentTimeMillis();

		// Set to store unique data records
		Set<String> uniqueRecords = new HashSet<String>();

		// List to store output lines (metadata + unique data records)
		List<String> outputLines = new ArrayList<String>();

		// Open the input file
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(inputFile))));

		String line;

		// First pass: read all lines and identify unique records
		while ((line = reader.readLine()) != null) {

			// Check if this is a metadata line (empty, or starts with #, %, @)
			if (isMetadataLine(line)) {
				// Add metadata line to output
				outputLines.add(line);
				metadataLineCount++;
			} else {
				// This is a data record
				recordCountInput++;

				// Normalize the line by trimming whitespace
				String normalizedLine = line.trim();

				// Check if this record is a duplicate
				if (uniqueRecords.add(normalizedLine)) {
					// Not a duplicate - write original line (or normalized, depending on
					// preference)
					outputLines.add(line);
					recordCountOutput++;
				} else {
					// It's a duplicate
					duplicateCount++;
				}
			}

			// Check memory usage periodically
			if ((recordCountInput + metadataLineCount) % 10000 == 0) {
				MemoryLogger.getInstance().checkMemory();
			}
		}

		reader.close();

		// Write output file
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));

		for (int i = 0; i < outputLines.size(); i++) {
			if (i > 0) {
				writer.newLine();
			}
			writer.write(outputLines.get(i));
		}

		writer.close();

		// Check memory one final time
		maxMemory = MemoryLogger.getInstance().checkMemory();

		// Record end time
		endTimestamp = System.currentTimeMillis();
	}

    /**
     * Check if a line is a metadata line.
     * A metadata line is empty or starts with #, %, or @.
     * 
     * @param line the line to check
     * @return true if it's a metadata line, false otherwise
     */
    private boolean isMetadataLine(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.charAt(0) == '#' 
                || trimmed.charAt(0) == '%' || trimmed.charAt(0) == '@';
    }


	/**
	 * Print statistics about the last algorithm execution.
	 */
	public void printStats() {
		System.out.println("=============  REMOVE DUPLICATE RECORDS TOOL v2.65 - STATS =============");
		System.out.println(" Metadata lines preserved: " + metadataLineCount);
		System.out.println(" Input data records count: " + recordCountInput);
		System.out.println(" Output data records count: " + recordCountOutput);
		System.out.println(" Duplicates removed: " + duplicateCount);
		System.out.println(" Total time: " + (endTimestamp - startTimestamp) + " ms");
		System.out.println(" Max memory: " + String.format("%.2f", maxMemory) + " MB");
		System.out.println("==============================================================");
	}

	/**
	 * Get the number of data records in the input file.
	 * 
	 * @return the record count
	 */
	public int getRecordCountInput() {
		return recordCountInput;
	}

	/**
	 * Get the number of data records in the output file.
	 * 
	 * @return the record count
	 */
	public int getRecordCountOutput() {
		return recordCountOutput;
	}

	/**
	 * Get the number of duplicates removed.
	 * 
	 * @return the duplicate count
	 */
	public int getDuplicateCount() {
		return duplicateCount;
	}

	/**
	 * Get the number of metadata lines preserved.
	 * 
	 * @return the metadata line count
	 */
	public int getMetadataLineCount() {
		return metadataLineCount;
	}
}