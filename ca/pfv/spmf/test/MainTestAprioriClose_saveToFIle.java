package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.apriori_fast.AlgoAprioriFASTClose;

/**
 * Example of how to use APRIORIClose (a.k.a Close)
 *  algorithm from the source code.
 * @author Philippe Fournier-Viger (Copyright 2008)
 */
public class MainTestAprioriClose_saveToFIle {

	public static void main(String [] arg) throws IOException{

		String input = fileToPath("contextPasquier99.txt");
		String output = ".//output.txt";  // the path for saving the frequent itemsets found
		
		double minsup = 0.4; // means a minsup of 2 transaction (we used a relative support)
		
		// There is a branch count parameter for the internal hash-tree used by Apriori
		// If you dont know what it is, just let it at the value 30. It is a good value.
		int branchCount = 30;
		
		// Applying the AprioriClose algorithm
		AlgoAprioriFASTClose apriori = new AlgoAprioriFASTClose();
		apriori.runAlgorithm(minsup, input, output, branchCount);
		apriori.printStats();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestAprioriClose_saveToFIle.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
