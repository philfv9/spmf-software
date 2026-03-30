package ca.pfv.spmf.test;

/* Copyright (c) 2008-2025 Chen Enze, M. Zohaib Nawaz, Philippe Fournier-Viger, M. Saqib Nawaz, 
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
import java.util.ArrayList;

import ca.pfv.spmf.algorithms.frequentpatterns.hmp.AlgoHMPHC;

/**
 * Example of how to use the HMP-HC algorithm from the source code.
 * 
 * @author Chen Enze, Philippe Fournier-Viger, et al. 2025
 */
public class MainTestHMPHC {

	public static void main(String[] args) throws IOException {

		// The path to a transaction database
		String inputFilePath = fileToPath("contextPasquier99.txt");

		// An output file path
		// (if you dont want to save the result to a file, it can be set to null)
		String outputFilePath = "output.txt";

		// Max number of patterns to find
		int maxCodeTableSize = 3;

		// Create the algorithm
		AlgoHMPHC algo = new AlgoHMPHC();

		// Optional line to set the max number of iterations of the algorithm
//		algo.setMaxiterations(15);

		// Run it
		ArrayList<int[]> result = algo.runAlgorithm(inputFilePath, outputFilePath, maxCodeTableSize);

		// Print statistics
		algo.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestHMPHC.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
