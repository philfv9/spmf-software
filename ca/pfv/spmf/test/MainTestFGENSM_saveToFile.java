package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.flosm_fgensm_gfscr.AlgoFGENSM;


/**
 * Example of how to use the FGENSM algorithm in source code.
 * @author Philippe Fournier-Viger
 */
public class MainTestFGENSM_saveToFile {

	public static void main(String [] arg) throws IOException{    
		// Load a sequence database
		String input = fileToPath("contextPrefixSpan.txt");
		String output = ".//output.txt";
		
		// Create an instance of the algorithm 
		AlgoFGENSM algo = new AlgoFGENSM(); 

		// This optional parameter allows to specify the minimum pattern length:
//		algo.setMinimumPatternLength(0);  // optional

		// This optional parameter allows to specify the maximum pattern length:
//		algo.setMaximumPatternLength(4);  // optional
		
		// if you set the following parameter to true, the sequence ids of the sequences where
		// each pattern appears will be shown in the result
//		algo.showSequenceIdentifiersInOutput(true);
		
		// execute the algorithm with minsup = 2 sequences  (50 %)
		algo.runAlgorithm(input, output, 0.5);    
		algo.printStats();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestFGENSM_saveToFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
