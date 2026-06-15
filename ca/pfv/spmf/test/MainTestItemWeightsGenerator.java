package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.tools.dataset_generator.ItemWeightsGenerator;

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
*
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see http://www.gnu.org/licenses/.
*/

/*
 * Example of how to use the ItemWeightsGenerator to generate a weight file
 * from a transaction database in SPMF format.
 *
 * @author Philippe Fournier-Viger, 2024
 */
public class MainTestItemWeightsGenerator {

	public static void main(String[] arg) throws IOException {

		// The path of the input transaction database file in SPMF format
		String inputFile = fileToPath("DB_RWFIM.txt");

		// The path of the output weight file to be generated
		String outputFile = ".//output_weights.txt";

		// We call the generator to generate the weight file
		ItemWeightsGenerator.generateWeights(inputFile, outputFile);

		// We print a message to indicate that the file has been generated
		System.out.println("Weight file generated successfully: " + outputFile);
	}

	/**
	 * This method returns the file path of a file stored in the same package
	 * as this class.
	 *
	 * @param filename the name of the file
	 * @return the full path of the file as a String
	 * @throws UnsupportedEncodingException if the encoding is not supported
	 */
	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestItemWeightsGenerator.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}