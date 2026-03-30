package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.HUIM_SA_HC.AlgoHUIMHC;


/**
 * Example of how to use the HUIM-HC algorithm 
 * from the source code.
 */
public class MainTestHUIM_HC {

	public static void main(String [] arg) throws IOException{
		
		String input = fileToPath("contextHUIM.txt");
		
		String output = ".//output.txt";

		int min_utility = 40;  // 
		
		// Applying the huim_bpso algorithm
		AlgoHUIMHC huim_hc = new AlgoHUIMHC();
		huim_hc.runAlgorithm(input, output, min_utility);
		huim_hc.printStats();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestHUIM_HC.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
