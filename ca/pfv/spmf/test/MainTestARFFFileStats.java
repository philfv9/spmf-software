package ca.pfv.spmf.test;

import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.tools.dataset_stats.ARFFFileStats;

/**
 * Example of how to calculate stats about an ARFF file from the source code of SPMF.
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MainTestARFFFileStats {

	public static void main(String [] arg) throws Exception{

		String input = fileToPath("test.arff");

		// Applying the viewer
		ARFFFileStats algorithm = new ARFFFileStats();
		algorithm.runAlgorithm(input);
	}
	
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
//		System.out.println("filename : " + filename);
		URL url = MainTestARFFFileStats.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
