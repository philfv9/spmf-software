/* This file is copyright (c) 2020 Mourad Nouioua et al.
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
* Do not remove the copyright and license information from this file.
*/
package ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.chuqiminer.AlgoCHUQIMiner;

/**
 * Class that shows how to run the CHUQI-Miner algorithm from the source code.
 * CHUQI-Miner mines correlated high utility quantitative itemsets.
 * 
 * @author Mourad Nouioua et al. 2021
 */
public class MainTestCHUQIMiner {

	/**
	 * Main method to run the CHUQI-Miner algorithm.
	 * 
	 * @param args the command line arguments
	 * @throws IOException if an error occurs while reading or writing files
	 */
	public static void main(String[] args) throws IOException {

		// The input file path of the file indicating the profit value of each item
		String inputFileProfitPath = fileToPath("dbHUQI_p.txt");

		// the input file path containing the transactions with quantities
		String inputFileDBPath = fileToPath("dbHUQI.txt");

		// the output file path for writing the result
		String output = "output.txt";

		// The minimum utility threshold in percentage
		float percentage = 10f;

		// The minimum bond threshold
		double minBond = 0.1;

		// The related quantitative coefficient
		int coef = 3;

		// The combination method
//		EnumCombination combinationmethod = EnumCombination.COMBINEMIN;
//		EnumCombination combinationmethod = EnumCombination.COMBINEMAX;
		EnumCombination combinationmethod = EnumCombination.COMBINEALL;

		// Run the algorithm
		AlgoCHUQIMiner algo = new AlgoCHUQIMiner();
		algo.runAlgorithm(inputFileDBPath, inputFileProfitPath, percentage,
				minBond, coef, combinationmethod, output);
		algo.printStatistics();
	}

	/**
	 * Convert a file name to its absolute path.
	 * 
	 * @param filename the file name
	 * @return the absolute file path
	 * @throws UnsupportedEncodingException if the encoding is not supported
	 */
	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestCHUQIMiner.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}