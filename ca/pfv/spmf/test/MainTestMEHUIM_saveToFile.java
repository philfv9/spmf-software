package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.mehuim.AlgoMEHUIM;


/**
 * Example of how to run the MEHUIM algorithm from the source code, and save results to file
 * @author Philippe Fournier-Viger, 2015
 */
public class MainTestMEHUIM_saveToFile {

	public static void main(String [] arg) throws IOException{

		// the input and output file paths
		String input = fileToPath("DB_Utility.txt");
		String output = ".//output.txt";
		
		// the minutil threshold
		int minutil = 30; 

		// Run the EFIM algorithm
		AlgoMEHUIM algo = new AlgoMEHUIM();
		algo.runAlgorithm(minutil,  input, output, true, Integer.MAX_VALUE, true);
		
		// Print statistics
		algo.printStats();
////
//		// Print the itemsets
//		itemsets.printItemsets();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestMEHUIM_saveToFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
