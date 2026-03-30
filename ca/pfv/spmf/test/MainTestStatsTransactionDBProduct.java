package ca.pfv.spmf.test;

import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.tools.dataset_stats.ProductTDBStats;

/**
 * Example of how to calculate stats for a Product Transaction Database  from the
 * source code. This format is used by algorithms such as VME.
 * 
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MainTestStatsTransactionDBProduct {

	public static void main(String[] arg) throws Exception {

		String input = fileToPath("contextVME.txt");

		// Applying the algorithm
		ProductTDBStats algo = new ProductTDBStats();
		algo.runAlgorithm(input);
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
//		System.out.println("filename : " + filename);
		URL url = MainTestStatsTransactionDBProduct.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
