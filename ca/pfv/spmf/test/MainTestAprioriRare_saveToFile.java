package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.apriori_fast.AlgoAprioriFASTRare;


/**
 * Example of how to use APRIORI-RARE and save the output to a file,
 * from the source code.
 * @author Philippe Fournier-Viger (Copyright 2008)
 */
public class MainTestAprioriRare_saveToFile {

	public static void main(String [] arg) throws IOException{
		//Input and output file paths
		String inputFilePath = fileToPath("contextZart.txt");
		String outputFilePath = ".//output.txt"; 
		
		// the threshold that we will use:
		double minsup = 0.6;
		
		// There is a branch count parameter for the internal hash-tree used by Apriori
		// If you dont know what it is, just let it at the value 30. It is a good value.
		int branchCount = 30;
		
		// Applying the APRIORI-Inverse algorithm to find sporadic itemsets
		AlgoAprioriFASTRare apriori2 = new AlgoAprioriFASTRare();
		// apply the algorithm
		apriori2.runAlgorithm(minsup, inputFilePath, outputFilePath, branchCount);
		apriori2.printStats();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestAprioriRare_saveToFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
