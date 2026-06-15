package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.mehuim_closed.AlgoMEHUIMClosed;


/**
 * Example of how to run the MEHUIM-Closed algorithm from the source code, and save the result to an output file.
 * @author Philippe Fournier-Viger, 2016
 */
public class MainTestMEHUIM_Closed_saveToFile {

	public static void main(String [] arg) throws IOException{

		// the input and output file paths
		String input = fileToPath("DB_Utility.txt");
		String output = ".//output.txt";
		
		// the minutil threshold
		int minutil = 30; 

		// Run the EFIM algorithm
		AlgoMEHUIMClosed algo = new AlgoMEHUIMClosed();
		algo.runAlgorithm(minutil,  input, output, true, Integer.MAX_VALUE, true, true);
		// Print statistics
		algo.printStats();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestMEHUIM_Closed_saveToFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
