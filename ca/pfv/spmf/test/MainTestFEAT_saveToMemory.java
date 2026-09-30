package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;

import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.AlgoFEAT2;
import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.SequentialPattern;

/**
 * Example of how to use the FEAT algorithm in source code.
 * @author Philippe Fournier-Viger
 */
public class MainTestFEAT_saveToMemory {

	public static void main(String [] arg) throws IOException{    
		// Input file
		String inputFile = fileToPath("contextPrefixSpan.txt");
		
		// Create an instance of the algorithm 
		AlgoFEAT2 algo = new AlgoFEAT2(); 
//		algo.setMaximumPatternLength(3);
		
		// minimum support  (40% = 2 sequences)
		double minSup = 0.4;
		
		// execute the algorithm with minsup = 50 %
		List<SequentialPattern> patterns = algo.runAlgorithm(inputFile, minSup, null);    
		algo.printStatistics();
		System.out.println(" == PATTERNS ==");
		for(SequentialPattern pattern : patterns){
			System.out.print(pattern + " support : " + pattern.getAbsoluteSupport() + " sequence ids:");
			for(Integer sequenceID: pattern.getSequenceIDs()) {
				System.out.print(" " + sequenceID);
			}
			System.out.println();
		
		}
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestFEAT_saveToMemory.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}