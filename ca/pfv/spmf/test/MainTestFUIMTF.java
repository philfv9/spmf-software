package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.fuimtf.AlgoFUIMTF;

/**
 * Example of how to use the FUIMTF-Tree algorithm . The code is under the GPLv3
 * license.
 */
public class MainTestFUIMTF {
	public static void main(String[] arg) throws IOException {

		String input = fileToPath("DB_Utility.txt");
		String output = "output.txt";
		int min_utility = 30; //
		double min_supDouble = 0.1;

		// Applying the TF-Free algorithm
		AlgoFUIMTF algo = new AlgoFUIMTF();
		algo.runAlgorithm(input, output, min_utility, min_supDouble);
		algo.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestFUIMTF.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
