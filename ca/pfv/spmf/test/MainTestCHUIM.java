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

import ca.pfv.spmf.algorithms.frequentpatterns.chuim_maxchuim.AlgoCHUIM;

/**
 * Example of how to use CHUIM from the source code.
 * @author Philippe Fournier-Viger, 2026
 */
public class MainTestCHUIM {

	/**
	 * Run the paper's utility-database example with minimum utility 370.
	 *
	 * @param arg unused command-line arguments
	 * @throws IOException if reading or writing files fails
	 */
	public static void main(String [] arg) throws IOException{

		String input = fileToPath("DB_Utility.txt");
		String output = "output.txt";

		int min_utility = 30;  // The paper's running example

		// Applying the CHUIM algorithm
		AlgoCHUIM algo = new AlgoCHUIM();
		algo.runAlgorithm(input, output, min_utility);
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
		URL url = MainTestCHUIM.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
