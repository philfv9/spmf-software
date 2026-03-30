package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.tools.dataset_stats.DoubleVectorDBStats;

/**
 * Example of how to read instances from a file (double vectors) and calculate stats about it.
 * @author Philippe Fournier-Viger, 2016.
 */
public class MainTestDoubleVectorDBStats {

	public static void main(String [] arg) throws IOException{
		
		// the input file
		String input = fileToPath("inputDBScan.txt");  

		// Parameters of the algorithm
		String separator = " ";
		
		// Applying the  algorithm
		DoubleVectorDBStats algorithm = new DoubleVectorDBStats();
		algorithm.runAlgorithm(input, separator);
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestDoubleVectorDBStats.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
