package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.AlgoTSP_nonClosed2;


/**
 * Example of how to use the TSP_NonClosed algorithm in source code.
 * @author copyright Philippe Fournier-Viger 2026
 */
public class MainTestTSP_nonClosed {

	public static void main(String [] arg) throws IOException{    
		// Input sequence database
		String inputFile = fileToPath("contextPrefixSpan.txt");
		
		// Output file path
		String outputPath = ".//output.txt";
			
		AlgoTSP_nonClosed2 algo = new AlgoTSP_nonClosed2(); 
		
		// This optional parameter allows to specify the minimum pattern length:
//		algo.setMinimumPatternLength(1);  // optional

		// This optional parameter allows to specify the maximum pattern length:
//		algo.setMaximumPatternLength(2);  // optional
		
        // if you set the following parameter to true, the sequence ids of the sequences where
        // each pattern appears will be shown in the result
        algo.setShowSequenceIdentifiers(false);
		
		int k = 2; // we use a k of 2 sequences.
		
		// execute the algorithm
		algo.runAlgorithm(inputFile, k);    
		algo.writeResultTofile("output.txt");
		algo.printStatistics();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestTSP_nonClosed.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}