package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.hui_miner.AlgoMLHUIMiner;
/**
 * Example of how to use the MLHUI-Miner algorithm 
 * from the source code and save the result to file.
 * @author Ying Wang, Philippe Fournier-Viger 2020
 */
public class MainTestMLHUIMiner {
	public static void main(String [] args) throws IOException{
		String inputTransaction = fileToPath("transaction_CLHMiner.txt");
		String inputTaxonomy = fileToPath("taxonomy_CLHMiner.txt");
		
		double minUtil = 60;

		// run the algorithm
		AlgoMLHUIMiner algo = new AlgoMLHUIMiner();
		algo.runAlgorithm(inputTransaction,inputTaxonomy, "output.txt", minUtil);
		algo.printStatistics();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestMLHUIMiner.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}

}
