package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;

import ca.pfv.spmf.algorithms.frequentpatterns.tkn.AlgoTKN;
import ca.pfv.spmf.algorithms.frequentpatterns.tkn.ItemsetTKN;

/* This file is copyright (c) 2021-2026 Mohamed Ashraf et al.
*
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
*
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
*/

/**
 * Example of how to run the TKN algorithm from the source code, and keep the
 * result in memory.
 *
 * @author Mohamed Ashraf
 */
public class MainTestTKN_saveToMemory {

	/**
	 * Main method to run the example
	 * 
	 * @param arg the command line arguments (not used)
	 * @throws IOException if an error occurs while reading or writing a file
	 */
	public static void main(String[] arg) throws IOException {

		// the input file (a database where some items have negative utilities)
		String input = fileToPath("DB_NegativeUtility.txt");

		// the number of itemsets to be found
		int k = 4;

		// Run the TKN algorithm (the output path is null to keep the result in memory)
		AlgoTKN algo = new AlgoTKN();
		List<ItemsetTKN> itemsets = algo.runAlgorithm(input, null, k);
		// Print statistics
		algo.printStats();

		// Print the itemsets (sorted by decreasing utility)
		for (ItemsetTKN itemset : itemsets) {
			System.out.println(itemset);
		}
	}

	/**
	 * Get the path of a file located in the same package as this class
	 * 
	 * @param filename the name of the file
	 * @return the path of the file
	 * @throws UnsupportedEncodingException if the path cannot be decoded
	 */
	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestTKN_saveToMemory.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
