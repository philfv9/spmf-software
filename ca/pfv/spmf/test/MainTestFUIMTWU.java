package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.fuimtwu.AlgoFUIMTWU;

/**
 * Example of how to use the FUIMTWU-Tree algorithm Thanks for the SPMF library
 * to provide the source code of mHUIMiner
 */
public class MainTestFUIMTWU {
	public static void main(String[] arg) throws IOException {

		String input = fileToPath("DB_Utility.txt");
		String output = "output.txt";
		int min_utility = 30; //
		double min_supDouble = 0.1;

		// Applying the FUIMTWU-Tree algorithm
		AlgoFUIMTWU algo = new AlgoFUIMTWU();
		algo.runAlgorithm(input, output, min_utility, min_supDouble);
		algo.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestFUIMTWU.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
