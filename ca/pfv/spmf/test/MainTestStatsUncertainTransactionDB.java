package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.tools.dataset_stats.UncertainTransactionDBStatsGenerator;

/**
 * Example of how to use the Uncertain Transaction Database viewer algorithm from the
 * source code.
 * 
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MainTestStatsUncertainTransactionDB {

	public static void main(String[] arg) throws IOException {

		String input = fileToPath("contextUncertain.txt");

		// Applying the viewer
		UncertainTransactionDBStatsGenerator algo = new UncertainTransactionDBStatsGenerator();
		algo.getStats(input);
		
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
//		System.out.println("filename : " + filename);
		URL url = MainTestStatsUncertainTransactionDB.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
