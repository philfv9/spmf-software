package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.hui_miner.AlgoHUIMSU;

/**
 * Example of how to use the HUIM-SU algorithm 
 * from the source code.
 */
public class MainTestHUIMSU {

	public static void main(String [] arg) throws IOException{
		
		String input = fileToPath("DB_Utility.txt");
		String output = ".//output.txt";

		int min_utility = 30;  // 
		
		// Applying the HUIMiner algorithm
		AlgoHUIMSU algo = new AlgoHUIMSU();
		algo.runAlgorithm(input, output, min_utility);
		algo.printStats();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestHUIMSU.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
