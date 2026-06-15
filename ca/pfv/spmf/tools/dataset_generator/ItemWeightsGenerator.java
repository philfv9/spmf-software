package ca.pfv.spmf.tools.dataset_generator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;
import java.util.TreeSet;

/* This file is copyright (c) 2008-2024 Philippe Fournier-Viger

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
* SPMF. If not, see http://www.gnu.org/licenses/.
* 
* Do not remove the copyright and license information.
*/

/*
 * This class is a synthetic item weights file generator. The user provides
 * a transaction database in SPMF format as input, and this class generates
 * a weight file associating a random weight (a real number between 0 and 1)
 * to each distinct item found in the database. The weight file is written
 * to disk.
 *
 * @author Philippe Fournier-Viger, 2024
 */
public class ItemWeightsGenerator {

	/** the random number generator */
	private static Random random = new Random(System.currentTimeMillis());

	/**
	 * This method reads a transaction database in SPMF format and generates a
	 * weight file associating a random weight to each distinct item found in
	 * the database. Weights are real numbers uniformly distributed in [0, 1].
	 *
	 * @param input  the file path of the input transaction database in SPMF format
	 * @param output the file path for writing the generated weight file
	 * @throws IOException if an error occurs while reading or writing a file
	 */
	public static void generateWeights(String input, String output) throws IOException {

		// We create a sorted set to store the distinct items found in the database.
		// A TreeSet is used so that items are stored in ascending order.
		TreeSet<Integer> distinctItems = new TreeSet<Integer>();

		// We create objects for reading the input file
		BufferedReader myInput = null;

		try {
			// Objects to read the file
			FileInputStream fin = new FileInputStream(new File(input));
			myInput = new BufferedReader(new InputStreamReader(fin));

			String thisLine; // variable to read a line

			// We read the file line by line until the end of the file
			while ((thisLine = myInput.readLine()) != null) {

				// If the line is empty, we skip it
				if (thisLine.isEmpty()) {
					continue;
				}

				// If the line is a comment line (starting with #, % or @), we skip it
				if (thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
					continue;
				}

				// We split the line according to spaces to get the items
				String[] tokens = thisLine.split(" ");

				// For each token on the line
				for (String token : tokens) {
					// We trim the token to remove any extra spaces
					token = token.trim();

					// If the token is empty, we skip it
					if (token.isEmpty()) {
						continue;
					}

					// We parse the item as an integer and add it to the set of distinct items
					int item = Integer.parseInt(token);
					distinctItems.add(item);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			// We close the input file
			if (myInput != null) {
				myInput.close();
			}
		}

		// We create a BufferedWriter to write the weight file to disk
		BufferedWriter writer = new BufferedWriter(new FileWriter(output));

		try {
			// For each distinct item found in the database, in ascending order
			for (int item : distinctItems) {
				// We generate a random weight uniformly distributed in [0, 1]
				double weight = random.nextDouble();

				// We write the item and its weight to the output file,
				// separated by a single space
				writer.write(item + " " + weight);

				// We write a new line
				writer.newLine();
			}
		} finally {
			// We close the output file
			writer.close();
		}
	}
}