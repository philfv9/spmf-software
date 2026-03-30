package ca.pfv.spmf.test;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.tkuce.AlgoTKUCE;

/**
 * Example of how to run TKU-CE from the source code
 * 
 * TKU-CE: Cross-Entropy Method for Mining Top-K High Utility Itemsets
 * @author Wei Song, Lu Liu, and Chaomin Huang
 * @see AlgoTKUCE
 */

public class MainTestTKUCE {
	public static void main(String [] arg) throws IOException{
		// input file
		String input = fileToPath("DB_Utility.txt");

		// output file
		String output = ".//output.txt";

		
		// the number of top-k huis
		int k = 3;
		
		AlgoTKUCE huim_cem = new AlgoTKUCE();
		huim_cem.runAlgorithm(input, output, k);
		huim_cem.printStats();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestTKUCE.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}