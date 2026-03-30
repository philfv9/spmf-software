package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.HUIM_SA_HC.AlgoHUIMSA;


/**
 * Example of how to use the HUIM-SA algorithm 
 * from the source code.
 */
public class MainTestHUIM_SA {

	public static void main(String [] arg) throws IOException{
		
		String input = fileToPath("contextHUIM.txt");
		
		String output = ".//output.txt";

		int min_utility = 40;  // 
		
		// Applying the huim_bpso algorithm
		AlgoHUIMSA algorithm = new AlgoHUIMSA();
		algorithm.runAlgorithm(input, output, min_utility);
		algorithm.printStats();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestHUIM_SA.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
