package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
/* This file is copyright (c) 2008-2025 Philippe Fournier-Viger
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

import ca.pfv.spmf.algorithms.associationrules.nar_miner.AlgoNARMiner;

/**
 * Example of how to use the NAR-Miner algorithm from the source code.
 * 
 * NAR-Miner mines negative association rules of the form A => NOT B
 * from a transaction database using two thresholds:
 * - mfs (minimum frequency support): itemsets with support >= mfs are frequent
 * - mis (maximum infrequency support): itemsets with 0 < support <= mis are
 *   infrequent itemsets of interest
 * - minConf: minimum confidence for generated negative rules
 * 
 * @author Philippe Fournier-Viger
 */
public class MainTestNARMiner {

	public static void main(String[] arg) throws IOException {
		// Loading the transaction database
		String input = fileToPath("contextPasquier99.txt"); // the database
		String output = "output.txt"; // the path for saving the negative rules found

		// Parameters for NAR-Miner:
		double mfs = 0.5;    // minimum frequency support (40% of transactions)
		double mis = 0.3;    // maximum infrequency support (30% of transactions)
		
		double minConf = 0.3; // minimum confidence for negative rules

		// Applying the NAR-Miner algorithm
		AlgoNARMiner algo = new AlgoNARMiner();

		// Uncomment the following line to set the maximum pattern length
		// algo.setMaximumPatternLength(3);

		algo.runAlgorithm(mfs, mis, minConf, input, output);

		// Print statistics about the algorithm execution
		algo.printStats();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestNARMiner.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}