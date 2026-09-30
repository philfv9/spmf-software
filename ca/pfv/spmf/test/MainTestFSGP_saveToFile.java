package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.AlgoFSGP2;

/**
 * Example of how to use the FSGP algorithm in source code.
 * @author copyright Philippe Fournier-Viger 2026
 */
public class MainTestFSGP_saveToFile {

	public static void main(String [] arg) throws IOException{    
		// Input sequence database
		String inputFile = fileToPath("contextPrefixSpan.txt");
		
		// Output file path
		String outputPath = ".//output.txt";
		
		// Create an instance of the algorithm with minsup = 50 %
		AlgoFSGP2 algo = new AlgoFSGP2(); 
		
		// if you set the following parameter to true, the sequence ids of the sequences where
        // each pattern appears will be shown in the result
        algo.setShowSequenceIdentifiers(false);
		
		int minsup = 2; // we use a minimum support of 2 sequences.
		
		// execute the algorithm
		boolean performPruning = true;// to activate pruning of search space
		algo.runAlgorithm(inputFile, minsup, performPruning);    
		algo.writeResultToFile(outputPath);
		algo.printStatistics();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestFSGP_saveToFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}