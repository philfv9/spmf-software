package ca.pfv.spmf.algorithms.frequentpatterns.clhminer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.test.MainTestAprioriHT_saveToFile;

/**
 * Example of how to use the CLH-Miner algorithm from the source code
 */
public class MainTestCLHMiner {


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
		AlgoCLHMiner cl = new AlgoCLHMiner();
		cl.runAlgorithm(minimumUtility, inputPath, outputPath, TaxonomyPath);
		cl.printStats();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestAprioriHT_saveToFile.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
