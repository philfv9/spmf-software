package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.tools.dataset_stats.MDSequenceDatabaseStats;

/**
 * 
 * Example of how to use the tool to calculate stats about a multidimensional database from the source code.
 * 
 * @author Philippe Fournier-Viger (Copyright 2024)
 */
public class MainTestMDSequenceDatabaseStats {

	public static void main(String[] arg) throws IOException {

		// Input file path
		String input = fileToPath("ContextMDSequenceNoTime.txt");
//		String input = fileToPath("ContextMDSequence.txt");

		// Applying the Sequence Database viewer
		MDSequenceDatabaseStats algorithm = new MDSequenceDatabaseStats();
		algorithm.runAlgorithm(input);

	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		System.out.println("filename : " + filename);
		URL url = MainTestMDSequenceDatabaseStats.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}