package ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan;

/* Copyright (c) 2008-2013 Philippe Fournier-Viger
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
* 
* Do not remove copyright and license information.
*/
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of a sequence database where each sequence is stored as an array of integers.
 *
 * @see Sequence
 * @author Philipe-Fournier-Viger
 */
public class SequenceDatabase {

	/** List of sequences in this database, each sequence as an int[] in SPMF format. */
	protected List<int[]> sequences = new ArrayList<int[]>();

	/** Total number of item occurrences in this database (for statistics). */
	protected long itemOccurrenceCount = 0;

	/** Default initial capacity of the sequence list. */
	private static final int DEFAULT_SEQUENCE_LIST_CAPACITY = 1024;

	/** Buffer size used by the BufferedReader when reading a file. */
	private static final int READER_BUFFER_SIZE = 1 << 16; // 64 KB

	/** Reusable buffer for parsing integer tokens of the current line. */
	private int[] tokenBuffer = new int[256];

	/**
	 * Loads a sequence database from a text file in SPMF format.
	 * 
	 * @param path path to the input file
	 * @throws IOException if an error occurs while reading the file or parsing tokens
	 */
	public void loadFile(String path) throws IOException {
		// reset statistics
		itemOccurrenceCount = 0;
		// initialize sequence list
		sequences = new ArrayList<int[]>(DEFAULT_SEQUENCE_LIST_CAPACITY);

		String thisLine;
		long lineNumber = 0;

		// open the file and read it line by line
		try (FileInputStream fin = new FileInputStream(new File(path));
				BufferedReader myInput = new BufferedReader(new InputStreamReader(fin), READER_BUFFER_SIZE)) {

			while ((thisLine = myInput.readLine()) != null) {
				lineNumber++;

				// skip comments, metadata and empty lines
				if (thisLine.isEmpty() == false && thisLine.charAt(0) != '#' && thisLine.charAt(0) != '%'
						&& thisLine.charAt(0) != '@') {

					// parse valid line into a sequence
					int[] sequence = parseLine(thisLine, lineNumber);

					// add non-empty sequences to the database
					if (sequence.length > 0) {
						sequences.add(sequence);
					}
				}
			}
		}
	}

	/**
	 * Parses one line of the input file into an array of integers and updates item occurrence count.
	 * 
	 * @param line       the line to parse
	 * @param lineNumber line number in the file (1-based), used in error messages
	 * @return an int[] containing the parsed tokens
	 * @throws IOException if the line contains an invalid integer token
	 */
	private int[] parseLine(String line, long lineNumber) throws IOException {
		int len = line.length();
		int count = 0;
		int i = 0;

		// parse tokens separated by spaces
		while (i < len) {
			char c = line.charAt(i);

			// skip spaces between tokens
			if (c == ' ') {
				i++;
				continue;
			}

			int tokenStart = i;
			boolean negative = false;
			if (c == '-') {
				negative = true;
				i++;
			}

			int digitsStart = i;
			long value = 0;

			// accumulate digits of the token
			while (i < len && (c = line.charAt(i)) >= '0' && c <= '9') {
				value = value * 10 + (c - '0');
				i++;
			}

			// validate token format and range
			if (i == digitsStart || (i < len && line.charAt(i) != ' ') || value > Integer.MAX_VALUE) {
				throw new IOException("Invalid integer token in sequence database file at line " + lineNumber
						+ ", position " + tokenStart + ": \"" + line.substring(tokenStart, i) + "\"");
			}

			int intValue = negative ? -(int) value : (int) value;

			// grow token buffer if needed
			if (count == tokenBuffer.length) {
				tokenBuffer = Arrays.copyOf(tokenBuffer, tokenBuffer.length * 2);
			}
			tokenBuffer[count++] = intValue;

			// count item occurrences (non-negative values)
			if (intValue >= 0) {
				itemOccurrenceCount++;
			}
		}

		// return a right-sized copy of parsed tokens
		return Arrays.copyOf(tokenBuffer, count);
	}

	/**
	 * Prints this sequence database to the standard output.
	 */
	public void print() {
		System.out.println("============  SEQUENCE DATABASE ==========");
		System.out.println(toString());
	}

	/**
	 * Prints basic statistics about this database to the standard output.
	 */
	public void printDatabaseStats() {
		System.out.println("============  STATS ==========");
		System.out.println("Number of sequences : " + sequences.size());

		double meansize = ((float) itemOccurrenceCount) / ((float) sequences.size());
		System.out.println("mean size" + meansize);
	}

	/**
	 * Returns a human-readable string representation of this sequence database.
	 * 
	 * @return a string representation of the database
	 */
	public String toString() {
		StringBuilder buffer = new StringBuilder();

		// for each sequence
		for (int i = 0; i < sequences.size(); i++) {
			buffer.append(i + ":  ");

			int[] sequence = sequences.get(i);
			boolean startingANewItemset = true;

			// format tokens of the sequence
			for (int token : sequence) {
				if (token >= 0) {
					if (startingANewItemset) {
						startingANewItemset = false;
						buffer.append("(");
					} else {
						buffer.append(" ");
					}
					buffer.append(token);
				} else if (token == -1) {
					buffer.append(")");
					startingANewItemset = true;
				} else if (token == -2) {
					break;
				}
			}
			buffer.append(System.lineSeparator());
		}
		return buffer.toString();
	}

	/**
	 * Returns the number of sequences in this database.
	 * 
	 * @return the sequence count
	 */
	public int size() {
		return sequences.size();
	}

	/**
	 * Returns the list of sequences in this database.
	 * 
	 * @return list of sequences, each as an int[] in SPMF format
	 */
	public List<int[]> getSequences() {
		return sequences;
	}

}