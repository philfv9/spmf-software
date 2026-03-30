package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.clustering.dbscan.AlgoAEDBSCAN;

/**
 *  Example of how to use the AEDBSCAN algorithm, in source code.
 */
public class MainTestAEDBSCAN_saveToFile {
	
	public static void main(String []args) throws NumberFormatException, IOException{
		
		String input = fileToPath("szu_clean.txt");
		String output = ".//output.txt";
		
		// we set the parameters of DBScan:
		int minPts = 50;
		
		// We specify that in the input file, double values on each line are separated by spaces
		String separator = " ";
		
		// Apply the algorithm
		AlgoAEDBSCAN algo = new AlgoAEDBSCAN();  
		
		algo.runAlgorithm(input, minPts, separator);
		algo.printStatistics();
		algo.saveToFile(output);
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestAEDBSCAN_saveToFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
	
	
}
