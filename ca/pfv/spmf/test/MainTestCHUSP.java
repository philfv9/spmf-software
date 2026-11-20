package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.chusp.AlgoCHUSP;

/**
 * This file is for testing the CHUSP algorithm
 * @see AlgoCHUSP
 * @author Philippe Fournier-Viger
 */
public class MainTestCHUSP {

	public static void main(String [] arg) throws IOException{
		 // the input database
		String input = fileToPath("DataBase_HUSRM.txt");
		// the path for saving the patterns found
		String output = ".//output.txt";

		// the minimum utility threshold
		int minutil = 35;
		// the minimum support threshold, as a percentage (1-100)
		int minsup = 1;

		AlgoCHUSP algo = new AlgoCHUSP();

		// set the maximum pattern length (optional)
		algo.setMaxPatternLength(4);

		// run the algorithm
		algo.runAlgorithm(input, output, minutil, minsup);

		// print statistics
		algo.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestCHUSP.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
