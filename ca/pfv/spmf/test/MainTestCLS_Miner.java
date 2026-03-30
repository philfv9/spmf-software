package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.hui_miner.AlgoCLS_miner;


/**
 * This file shows how to use the CLS-Miner algorithm from the source code of SPMF. */

public class MainTestCLS_Miner {

	public static void main(String [] arg) throws IOException{
		// input file path
		String input = fileToPath("DB_Utility.txt");
		// the minutility threshold
		int min_utility = 30;
		
		// output file path
		String output = ".//output.txt";

		// (1) Applying the CHUI-Miner algorithm to find 
		// closed high utility itemsets (CHUIs)
		AlgoCLS_miner clsMiner = new AlgoCLS_miner(true,false,true,true);
		clsMiner.runAlgorithm(input, min_utility, output);
		clsMiner.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestCLS_Miner.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
