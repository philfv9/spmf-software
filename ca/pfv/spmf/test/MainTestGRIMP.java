package ca.pfv.spmf.test;

/* Copyright (c) 2008-2025 M. Zohaib Nawaz, M. Saqib Nawaz, Philippe Fournier-Viger
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
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;

import ca.pfv.spmf.algorithms.frequentpatterns.grimp.AlgoGrimp;
import ca.pfv.spmf.algorithms.frequentpatterns.grimp.CrossoverVariant;
import ca.pfv.spmf.algorithms.frequentpatterns.grimp.MutationVariant;

/**
 * Example of how to use the GRIMP algorithm from the source code.
 * 
 * @author Philippe Fournier-Viger, 2023
 */
public class MainTestGRIMP {

	/**
	 * Entry point to demonstrate running the GRIMP algorithm. Sets input/output
	 * paths, algorithm parameters, variant selection, and a debug toggle, then
	 * invokes the algorithm and prints execution stats.
	 * 
	 * @param args command-line arguments (unused)
	 * @throws IOException if input file cannot be read or output cannot be written
	 */
	public static void main(String[] args) throws IOException {
		// Add a debug toggle here:
		boolean debug = false; // <--- change as needed: true for debug output

		// The path to a transaction database
		String databaseFilePath = fileToPath("contextPasquier99.txt");

		// An output file path
		// (if you dont want to save the result to a file, it can be set to null)
		String outputFilePath = "output.txt";

		// Number of patterns to generate
		int patternCount = 4;

		// Number of iterations
		int iterationCount = 10;

		// Maximum pattern length
		int maxPatternLength = 5;

		// You can select a specific GRIMP variant
		CrossoverVariant crossoverVariant = CrossoverVariant.SINGLE;
		MutationVariant mutationVariant = MutationVariant.SINGLE;

		// Run the algorithm
		AlgoGrimp algo = new AlgoGrimp();
		List<int[]> result = algo.runAlgorithm(databaseFilePath, outputFilePath, patternCount, iterationCount,
				crossoverVariant, mutationVariant, maxPatternLength, debug);
						
		// Print statistics about the algorithm execution
		algo.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestGRIMP.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
