package ca.pfv.spmf.test;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.lcim.AlgoLCIM;

/**
 * Example of how to use the LCIM algorithm 
 * from the source code.
 * @author Philippe Fournier-Viger, M. Saqib Nawaz, 2021
 */
public class MainTestLCIM {

	public static void main(String [] arg) throws IOException{
		
		String input = fileToPath("DB_cost.txt");
		String output = ".//output.txt";

		double minutil = 10d;  
		double maxcost = 10d;  
		double minsup = 0.3d; // which means 40 % of the database size.
		
		// Applying the  algorithm
		AlgoLCIM algorithm = new AlgoLCIM();
		algorithm.runAlgorithm(input, output, minutil, maxcost, minsup);
		algorithm.printStats();

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestLCIM.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
