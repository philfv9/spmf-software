package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.defme_fast.AlgoDefMe_FAST;

/**
 * Example of how to use DefMe algorithm from the source code.
 * 
 * @author Philippe Fournier-Viger - 2009
 */
public class MainTestDefMe_saveToFile {

	public static void main(String[] arg) throws IOException {
		// Loading the binary context
		String input = fileToPath("contextZart.txt"); // the database
		String output = ".//output.txt"; // the path for saving the frequent itemsets found

		double minsup = 0.4; // means a minsup of 2 transaction (we used a relative support)

		// Applying the DefMe algorithm
		AlgoDefMe_FAST algo = new AlgoDefMe_FAST();

		// Uncomment the following line to set the maximum pattern length (number of
		// items per itemset)
//		algo.setMaximumPatternLength(2);

		algo.runAlgorithm(input, output, minsup);
		algo.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestDefMe_saveToFile.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
