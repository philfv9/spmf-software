package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.mehuim_closed.*;


/**
 * Example of how to run the MEHUIM-Closed algorithm from the source code, and save the results to memory
 * @author Philippe Fournier-Viger, 2016
 */
public class MainTestMEHUIM_Closed_saveToMemory {

	public static void main(String [] arg) throws IOException{

		// the input and output file paths
		String input = fileToPath("DB_Utility.txt");
		
		// the minutil threshold
		int minutil = 30; 

		// Run the EFIM algorithm
		AlgoMEHUIMClosed algo = new AlgoMEHUIMClosed();
		Itemsets itemsets = algo.runAlgorithm(minutil,  input, null, true, Integer.MAX_VALUE, true, true);
		// Print statistics
		algo.printStats();
		
		// Print itemsets
		itemsets.printItemsets();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestMEHUIM_Closed_saveToMemory.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
