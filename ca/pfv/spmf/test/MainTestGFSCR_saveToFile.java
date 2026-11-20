package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.flosm_fgensm_gfscr.AlgoFCloSM;
import ca.pfv.spmf.algorithms.sequentialpatterns.flosm_fgensm_gfscr.AlgoFGENSM;
import ca.pfv.spmf.algorithms.sequentialpatterns.flosm_fgensm_gfscr.AlgoGFSCR;

/**
 * Example of how to use the GFS-CR algorithm in source code, by first generating
 * a file of frequent closed sequences with FCloSM and a file of frequent
 * generator sequences with FGENSM, and then generating all frequent sequences
 * from these two files.
 * @author Philippe Fournier-Viger
 */
public class MainTestGFSCR_saveToFile {

	public static void main(String [] arg) throws IOException{    
		// Load a sequence database
		String input = fileToPath("contextPrefixSpan.txt");
		String closedOutput = ".//output_closed.txt";
		String generatorOutput = ".//output_generators.txt";
		String output = ".//output.txt";
		
		// The minimum support threshold (50 % of the sequences)
		double minsup = 0.5;
		
		// Mine the frequent closed sequences with FCloSM
		AlgoFCloSM fclosm = new AlgoFCloSM();
		fclosm.runAlgorithm(input, closedOutput, minsup);
		fclosm.printStats();
		
		// Mine the frequent generator sequences with FGENSM
		AlgoFGENSM fgensm = new AlgoFGENSM();
		fgensm.runAlgorithm(input, generatorOutput, minsup);
		fgensm.printStats();
		
		// Create an instance of the GFS-CR algorithm
		AlgoGFSCR algo = new AlgoGFSCR();

		// This optional parameter allows to specify the minimum pattern length:
//		algo.setMinimumPatternLength(1);  // optional

		// This optional parameter allows to specify the maximum pattern length:
//		algo.setMaximumPatternLength(4);  // optional
		
		// generate all frequent sequences from the closed and generator sequence files
		algo.runAlgorithm(closedOutput, generatorOutput, output);
		algo.printStats();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestGFSCR_saveToFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
