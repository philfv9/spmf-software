package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;

import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.AlgoMaxSP2;
import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.SequentialPattern;
import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.SequentialPatterns;

/**
 * Example of how to use the MaxSP algorithm, from the source code.
 * 
 * @author copyright Philippe Fournier-Viger 2026
 */
public class MainTestMaxSP_saveToMemory {

	public static void main(String [] arg) throws IOException{    
		// Input sequence database
		String inputFile = fileToPath("contextPrefixSpan.txt");
		
		// Create an instance of the algorithm
		AlgoMaxSP2 algo  = new AlgoMaxSP2();
		
        // if you set the following parameter to true, the sequence ids of the sequences where
        // each pattern appears will be shown in the result
        boolean showSequenceIdentifiers = false;
		
		// execute the algorithm
		SequentialPatterns patterns = algo.runAlgorithm(inputFile, null, 2);    
		algo.printStatistics();
		for(List<SequentialPattern> level : patterns.getLevels()) {
			for(SequentialPattern pattern : level) {
				System.out.println(pattern + " support: " + pattern.getAbsoluteSupport());
			}
		}
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestMaxSP_saveToMemory.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}