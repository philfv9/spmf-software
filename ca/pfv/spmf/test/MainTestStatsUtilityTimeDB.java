package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.tools.dataset_stats.TransactionDBUtilityTimeStats;

/**
 * Example of how to calculate stats for a Time-Utility Transaction Database  from the
 * source code. This format is used by algorithms such as LTHUI-Miner
 * 
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MainTestStatsUtilityTimeDB {

	public static void main(String[] arg) throws IOException {

		String input = fileToPath("DB_LTHUI.txt");

		// Applying the algorithm
		TransactionDBUtilityTimeStats algo = new TransactionDBUtilityTimeStats();
		algo.runAlgorithm(input);
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
//		System.out.println("filename : " + filename);
		URL url = MainTestStatsUtilityTimeDB.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
