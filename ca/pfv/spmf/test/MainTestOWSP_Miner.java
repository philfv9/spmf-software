package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.mapd_owsp.AlgoOWSPMiner;
/* This file is copyright (c) 2021 Youxi Wu et al.
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
/**
 * Example of how to use the OWSP-Miner algorithm from the source code
 * 
 * @author Youxi Wu et al.
 * @see AlgoOWSP-Miner
 */
public class MainTestOWSP_Miner {

	public static void main(String[] args) throws IOException {
		// the Input and output files
		String inputFile = fileToPath("contextMAPD.txt");
		String outputFile = "output.txt";
		
		/** The minimum support threshold */
		int minsup = 2;
		
		/** The weak character set*/
		char[] weakCharacterSet = new char[] { 'g' };
		
		/** The number of characters to read */
		int numberOfCharactersToRead =  20000;

		/** Run the algorithm */
		AlgoOWSPMiner algo = new AlgoOWSPMiner();
		algo.runAlgorithm(inputFile, outputFile, weakCharacterSet, minsup, numberOfCharactersToRead);
		algo.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestOWSP_Miner.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
