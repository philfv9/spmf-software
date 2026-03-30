package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.hauim_gmu.AlgoHAUIM_GMU;

/**
 * Example of how to run the HAUIM-GMU algorithm from the paper: Generalized
 * maximal utility for mining high average-utility itemsets
 * 
 * @author Wei Song,Lu Liu,Chaomin Huang
 */

public class MainTestHAUIM_GMU {
	public static void main(String args[]) throws IOException {
		// input file
		String input = fileToPath("contextHAUIMiner.txt");
		
		// output file
		String output = "output.txt";
		
		// minimum utility
		int threshold = 24; 
		
		// create the algorithm
		AlgoHAUIM_GMU algorithm = new AlgoHAUIM_GMU();
		
		// Run the algorithm
		algorithm.runAlgorithm(input, output, threshold);
		
		// Prin statistics about the execution
		algorithm.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestHAUIM_GMU.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}