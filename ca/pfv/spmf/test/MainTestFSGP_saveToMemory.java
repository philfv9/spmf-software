package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;

import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.AlgoFSGP2;
import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.AlgoFSGP2.FSGPPattern;


/**
 * Example of how to use the FSGP algorithm in source code.
 * @author Philippe Fournier-Viger
 */
public class MainTestFSGP_saveToMemory {

	public static void main(String [] arg) throws IOException{    
		// Input sequence database
		String inputFile = fileToPath("contextPrefixSpan.txt");
		
		// Create an instance of the algorithm 
		AlgoFSGP2 algo = new AlgoFSGP2(); 
//		algo.setMaximumPatternLength(3);
		
		// execute the algorithm with minsup = 50 %
		boolean performPruning = true;// to activate pruning of search space
		List<FSGPPattern> patterns = algo.runAlgorithm(inputFile, 0.5, performPruning);    
		algo.printStatistics();
		
		System.out.println(" == PATTERNS ==");
		boolean displaySequenceIdentifiers = false;
		for(FSGPPattern pattern : patterns){
			System.out.println(pattern.toString(displaySequenceIdentifiers));
		}
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestFSGP_saveToMemory.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}