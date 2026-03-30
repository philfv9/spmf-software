package ca.pfv.spmf.test;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.hui_miner.AlgoTHUI;

/**
 * Class to test the THUI algorithm from the source code of SPMF
 */
public class MainTestTHUI {

	public static void main(String [] arg) throws IOException{

		// input file path
		String input = fileToPath("DB_Utility.txt");
		
		// output file path
		String output = "output.txt";
		
		// the parameter k
		int k = 8;
		
		// Applying the algorithm
		AlgoTHUI algorithm = new AlgoTHUI();
		algorithm.runAlgorithm(input, output, true, k);
		
		// Print statistics about the algorithm execution
		algorithm.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestTHUI.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
