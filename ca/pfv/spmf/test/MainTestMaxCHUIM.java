/* This file is copyright (c) 2026 Philippe Fournier-Viger
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
 * Do not remove copyright and license information.
 */

package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.chuim_maxchuim.AlgoMaxCHUIM;

/**
 * Example of how to use MaxCHUIM from the source code.
 * @author Philippe Fournier-Viger, 2026
 */
public class MainTestMaxCHUIM {

	/**
	 * Run the paper's utility-database example with minimum utility 370.
	 *
	 * @param arg unused command-line arguments
	 * @throws IOException if reading or writing files fails
	 */
	public static void main(String [] arg) throws IOException{

		// The input file
		String input = fileToPath("DB_Utility.txt");
		
		// The output file path for saving maximal itemsets
		String outputMaximal = "output.txt";
		
		// Option, if you want to also save the closed itemsets then set this path:
//		String outputClosed = "output_closed.txt";
		// Otherwise, leave it null:
		String outputClosed = null;

		// The minimum utility threshold
		int min_utility = 30;  

		// Run the MaxCHUIM algorithm
		AlgoMaxCHUIM algo = new AlgoMaxCHUIM();
		algo.runAlgorithm(input, outputClosed, outputMaximal, min_utility);
		algo.printStats();

	}

	/**
	 * Resolve a UTF-8 classpath resource using the standard MainTestFHM helper.
	 *
	 * @param filename the example resource name
	 * @return the decoded resource path
	 * @throws UnsupportedEncodingException if UTF-8 decoding is unavailable
	 */
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestMaxCHUIM.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
