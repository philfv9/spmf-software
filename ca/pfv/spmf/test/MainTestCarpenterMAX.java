package ca.pfv.spmf.test;

/* Copyright (c) 2008-2025 Philippe Fournier-Viger
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

import ca.pfv.spmf.algorithms.frequentpatterns.carpenter.AlgoCarpenter;

/**
 * Example of how to use the Carpenter algorithm from the source code to mine maximal itemsets.
 * 
 * Reference: F. Pan, G. Cong, A.K.H. Tung, J. Yang, and M. Zaki. Carpenter:
 * Finding Closed Patterns in Long Biological Datasets. Proc. 9th ACM SIGKDD
 * Int. Conf. on Knowledge Discovery and Data Mining (KDD 2003, Washington, DC),
 * 637-642. ACM Press, New York, NY, USA 2003
 * 
 * @author Philippe Fournier-Viger, 2025
 */
public class MainTestCarpenterMAX {

	public static void main(String[] args) throws IOException {

		// The path to a transaction database in SPMF format
		String inputFilePath = fileToPath("contextPasquier99.txt");

		// The output file path
		String outputFilePath = "./output.txt";

		// Minimum support threshold (e.g. 0.4 means 40 %).
		double minSupport = 0.4;

		// Constraints on the number of items per itemset discovered:
		int minSizeConstraint = 1;
		int maxSizeConstraint = Integer.MAX_VALUE;

		// Set this variable to true to find only the maximal patterns instead of closed
		// patterns.
		boolean keepOnlyMaximalPatterns = true;

		// Create and run the algorithm
		AlgoCarpenter algo = new AlgoCarpenter();
		algo.runAlgorithm(inputFilePath, outputFilePath, minSupport, minSizeConstraint, maxSizeConstraint,
				keepOnlyMaximalPatterns);
		algo.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestCarpenterMAX.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}