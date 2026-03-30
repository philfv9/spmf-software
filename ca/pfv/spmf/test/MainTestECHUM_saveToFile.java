package ca.pfv.spmf.test;



import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.echum.AlgoECHUM;


/**
 * Example of how to run the ECHUM algorithm from the source code, and save the result to an output file.
 */
public class MainTestECHUM_saveToFile {

	public static void main(String [] arg) throws IOException{

	
		String input= fileToPath("DB_Utility.txt");

		String output = "output.txt";
		
		// the minutil threshold
		int minutil = 30; 
		
		double minCor= 0.6;

		// Run the  algorithm
		AlgoECHUM algo = new AlgoECHUM();
		algo.runAlgorithm(minutil, minCor, input, output, true, Integer.MAX_VALUE, true );
		// Print statistics
		algo.printStats();

	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestEFIM_saveToFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
