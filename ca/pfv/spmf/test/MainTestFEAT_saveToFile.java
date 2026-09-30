package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.AlgoFEAT2;

/**
 * Example of how to use the FEAT algorithm in source code.
 * 
 * @author Philippe Fournier-Viger
 */
public class MainTestFEAT_saveToFile {

	public static void main(String[] arg) throws IOException {
		// Input file
		String inputFile = fileToPath("contextPrefixSpan.txt");

		// Output file
		String outputFile = "output.txt";

		// Create an instance of the algorithm
		AlgoFEAT2 algo = new AlgoFEAT2();
//		algo.setMaximumPatternLength(3);

		// minimum support (40% = 2 sequences)
		double minSup = 0.4;

		// if you set the following parameter to true, the sequence ids of the sequences
		// where
		// each pattern appears will be shown in the result
		algo.setShowSequenceIdentifiers(false);

		// execute the algorithm with minsup = 50 %
		algo.runAlgorithm(inputFile, minSup, outputFile);
		algo.printStatistics();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestFEAT_saveToFile.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}