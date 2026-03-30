package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.mapd_owsp.AlgoMAPD;
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
 * Example of how to use the MAPD algorithm
 * 
 * @author Youxi Wu  et al.
 * @see AlgoMAPD
 *
 */
public class MainTestMAPD {

	public static void main(String[] args) throws IOException {
		// the input file
		String filePath = fileToPath("contextMAPD.txt");
		
		// the output file
		String outputFile = "output.txt";

		// a pre-specified threshold
		double minimumThreshold = 0.2; 

		// the possible maximum length of frequent patterns
		int maxPatternLength = 3; 
		
		// the minimum gap constraint
		int minGap = 0; 
		
		 // the maximum gap constraint
		int maxGap = 3;
		
		char[] charSet = { 'a', 'c', 't', 'g' };

		// Run the algorithm
		AlgoMAPD algo = new AlgoMAPD();
		algo.runAlgorithm(filePath, outputFile, minGap, maxGap, maxPatternLength, minimumThreshold, charSet);
		algo.printStats();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestMAPD.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
