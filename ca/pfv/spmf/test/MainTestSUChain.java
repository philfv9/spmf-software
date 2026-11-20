package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.suchain.AlgoSUChain;

/**
 * This file is for testing the SUCHain algorithm
 * @see AlgoSUChain
 * @author Philippe Fournier-Viger
 */
public class MainTestSUChain {

	public static void main(String [] arg) throws IOException{
		 // the input database
		String input = fileToPath("DataBase_HUSRM.txt");
		// the path for saving the patterns found
		String output = ".//output.txt";

		// the minimum utility threshold
		int minutil = 35;

		AlgoSUChain algo = new AlgoSUChain();

		// set the maximum pattern length (optional)
		algo.setMaxPatternLength(4);

		// run the algorithm
		algo.runAlgorithm(input, output, minutil);

		// print statistics
		algo.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestSUChain.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
