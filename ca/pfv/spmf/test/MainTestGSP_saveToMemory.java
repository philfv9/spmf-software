package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.gsp_AGP_fast.AlgoGSP;
import ca.pfv.spmf.algorithms.sequentialpatterns.gsp_AGP_fast.SequenceDatabase;

/**
 * Example of how to use the algorithm GSP, saving the results in the main
 * memory
 * 
 * @author agomariz
 */
public class MainTestGSP_saveToMemory {

	public static void main(String[] args) throws IOException {
		// Load a sequence database
		double support = 0.5;
		boolean keepPatterns = true;
		boolean verbose = false;

		// if you set the following parameter to true, the sequence ids of the sequences
		// where
		// each pattern appears will be shown in the result
		boolean outputSequenceIdentifiers = false;

		SequenceDatabase sequenceDatabase = new SequenceDatabase();

		sequenceDatabase.loadFile(fileToPath("contextPrefixSpan.txt"), support);

		AlgoGSP algorithm = new AlgoGSP(support);
		
		// This optional parameter allows to specify the minimum pattern length:
//		algorithm.setMinimumPatternLength(1);  // optional

		// This optional parameter allows to specify the maximum pattern length:
//		algorithm.setMaximumPatternLength(2);  // optional

		System.out.println(sequenceDatabase.toString());

		algorithm.runAlgorithm(sequenceDatabase, keepPatterns, verbose, null, outputSequenceIdentifiers);
		System.out.println(algorithm.getNumberOfFrequentPatterns() + " frequent pattern found.");

		System.out.println(algorithm.printedOutputToSaveInFile());

		// System.out.println(algorithm.printStatistics());
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestGSP_saveToMemory.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
