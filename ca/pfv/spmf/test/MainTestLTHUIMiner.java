package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.lthui_miner.AlgoLTHUIMiner;

/**
 * Example of how to use the LTHUI-Miner algorithm from the source code
 * @author Yanjun Yang, Philippe Fournier-Viger
 * @see AlgoLTHUIMiner
 */
public class MainTestLTHUIMiner {

	public static void main(String[] args) throws IOException {
		// Local minimum utility threshold
		int lminutil = 20;
		
		// The length of a sliding window
		int winlen = 9;
		
		// The length of a bin
		int binlen = 3;
		
		// Minimum slope threshold, indicating increasing trends
		double minslope = 5;
		
		// The start timestamp of a database. 
		// If set to -1, the timestamp of the first transaction in the database is used
		// However, in real database, it may not equal to the timestamp of the first transaction in the database
		long databaseStartTimestamp = -1;
		
		// If true, then output period with the index of bins, otherwise, output period with timestamp
		boolean outputIndex = false;
		
		// Input file 
		String inputFile = fileToPath("DB_LTHUI.txt");
		
		// Output file 
		String outputFile = "output.txt";
		
		AlgoLTHUIMiner lthuiminer = new AlgoLTHUIMiner();
		lthuiminer.runAlgorithm(inputFile, outputFile, lminutil, winlen, binlen, minslope, databaseStartTimestamp, outputIndex);
		lthuiminer.printStats();
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestLTHUIMiner.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}

}
