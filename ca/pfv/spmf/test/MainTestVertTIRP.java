package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.fasttirp.AlgoFastTIRP;

/**
 * Example of how to use the VertTIRP algorithm from the source code.
 * 
 * @author Philippe Fournier-Viger, Yuechun Li, 2023
 */
public class MainTestVertTIRP {

	public static void main(String[] arg) throws IOException {

		// Input file path
		String input = fileToPath("test.csv");

		// Output file path
		String output = ".//output.txt";

		// Minimum vertical support
		double minVertSup = 0.5;

		// Minimum horizontal support
		double minHorSup = 0;

		// Epsilon
		int epsilon = 1;

		// Minimum gap
		int mingap = 0;

		// Maximum gap
		int maxgap = 5;

		// Minimum duration
		int minDuration = 0;

		// Maximum duration
		int maxDuration = 10;

		// Maximum pattern length
		int maxPatternLength = 3;

		// If true, details about each matching sequence for each pattern will be
		// displayed
		boolean detailedOutput = false;
		
		// This optimization must be set to false to run VertTIRP
		//  If it is set to true, then it becomes the FastTIRP algorithm
		boolean usePairSupportMatrix = false;
		
		// Applying the algorithm
		AlgoFastTIRP algo = new AlgoFastTIRP();

		// If you do not want to discover some specific temporal relationships, 
		// you can uncomment some of the lines below.
		
//		algo.removeRelation(Relation.B);
//		algo.removeRelation(Relation.M);
//		algo.removeRelation(Relation.O);
//		algo.removeRelation(Relation.F);
//		algo.removeRelation(Relation.S);
//		algo.removeRelation(Relation.L);
//		algo.removeRelation(Relation.C);
//		algo.removeRelation(Relation.E);
		
		algo.runAlgorithm(input, output, minHorSup, minVertSup, epsilon, mingap, maxgap, minDuration, maxDuration,
				maxPatternLength, detailedOutput, usePairSupportMatrix);
		algo.printStats();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestVertTIRP.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
