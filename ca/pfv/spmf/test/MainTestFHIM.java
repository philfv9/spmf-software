package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.associationrules.hgb.AlgoFHIM_and_HUCI;

/**
 * Example of how to run the FHIM algorithm from the source code.
 */
public class MainTestFHIM {

	public static void main(String[] arg) throws IOException {

		// Input file path
		String input = fileToPath("DB_Utility.txt");
		
		// Output file path
		String output = ".//output.txt";

		// Minimum utility threshold
		int min_utility = 30; 

		// Applying the HUIMiner algorithm
		AlgoFHIM_and_HUCI algorithm = new AlgoFHIM_and_HUCI();
		algorithm.runAlgorithmFHIM(input, output, min_utility);
		algorithm.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestFHIM.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
