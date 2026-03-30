package ca.pfv.spmf.test;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.feacp.AlgoFEACP;

/**
 * Example of how to use the FEACP algorithm from the source code
 * 
 * The FEACP algorithm was proposed in this paper:
 * 
 * N. T. Tung, Loan T. T. Nguyen, Trinh D. D. Nguyen, Philippe Fournier-Viger, 
 * Ngoc Thanh Nguyen, Bay Vo:  Efficient mining of cross-level high-utility 
 * itemsets in taxonomy quantitative databases. Inf. Sci. 587: 41-62 (2022)
 * 
 * @see AlgoFEACP
 */
public class MainTestFEACP {


	public static void main(String[] args) throws IOException {

		// input file path (taxonomy)
		String TaxonomyPath = fileToPath("taxonomy_CLHMiner.txt");
		// input file path (transactions)
		String inputPath = fileToPath("transaction_CLHMiner.txt");
		// Output path
		String outputPath = "output.txt";
		
		// minimum utility
		int minimumUtility = 60;
		
		// run the algorithm
		AlgoFEACP algorithm = new AlgoFEACP();
		algorithm.runAlgorithm(minimumUtility, inputPath, outputPath, TaxonomyPath, Integer.MAX_VALUE);
		algorithm.printStats();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestFEACP.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
