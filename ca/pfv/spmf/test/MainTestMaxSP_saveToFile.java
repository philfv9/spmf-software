package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.AlgoMaxSP2;
/*
 * Example of how to use the MaxSP algorithm, from the source code.
 * @author copyright Philippe Fournier-Viger 2026
 */
public class MainTestMaxSP_saveToFile {

	public static void main(String [] arg) throws IOException{    
		// Input sequence database
		String inputFile = fileToPath("contextPrefixSpan.txt");
		
		// Output file path
		String outputPath = ".//output.txt";
		
		int minsup = 2; // we use a minsup of 2 sequences (50 % of the database size)
		
		AlgoMaxSP2 algo  = new AlgoMaxSP2();  //
		
        // if you set the following parameter to true, the sequence ids of the sequences where
        // each pattern appears will be shown in the result
        algo.setShowSequenceIdentifiers(false);
		
		// execute the algorithm
		algo.runAlgorithm(inputFile, outputPath, minsup);    
		algo.printStatistics();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestMaxSP_saveToFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}