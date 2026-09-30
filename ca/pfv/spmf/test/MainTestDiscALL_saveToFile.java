package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.disc.AlgoDiscALL;

/**
 * Example of how to use the DISC algorithm in source code, running it on the
 * SIGN dataset and printing its statistics.
 *
 * @author Philippe Fournier-Viger
 */
public class MainTestDiscALL_saveToFile {

	public static void main(String[] arg) throws IOException {
		String input = fileToPath("contextPrefixSpan.txt");
		String output = ".//output.txt";
		double minsup = 0.5; // relative minimum support (6.5 %)

		AlgoDiscALL algoDISC = new AlgoDiscALL();
		// If you want to enable output de-duplication (disabled by default
		// to avoid OOM on dense datasets), uncomment the following line:
//      algoDISC.setEnableOutputDeduplication(true);
		algoDISC.runAlgorithm(input, output, minsup);
		algoDISC.printStats();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestDiscALL_saveToFile.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}