package ca.pfv.spmf.test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.srpfpm.AlgoSRPFPM;

/**
 * Example of how to use SRPFPM from the source code and save
 * the resutls to a file.
 *
 * @author Vincent M. Nofong (modified from Philippe Fournier-Viger's implementation of FPGrowth)
 */
public class MainTestSRPFPM_saveToFile {

    public static void main(String[] arg) throws FileNotFoundException, IOException {
        // the file paths
        String input = fileToPath("contextPFPM.txt");  // the database
        String output = ".//output.txt";  // the path for saving the frequent itemsets found

        double minsup = 0.1; // means a minsup of 1 transaction (we used a relative support)

		/*
		periodicity: is the user threshold of periodicity
		difference: is the acceptable difference for selecting PFPs with similar
		periodicities. That is:
		minAverage =  the minimum user desired average period
		maxAverage = the maximum user desired average period
		minPeriod = the minimum user desired period
		maxPeriod = the maximum user desired period
 		*/
        double minAverage = 1.6;
        double maxAverage = 14.5;
        double minPeriod =0;
        double maxPeriod =139;

        // Applying the FPGROWTH algorithmMainTestFPGrowth.java
        AlgoSRPFPM algo = new AlgoSRPFPM();

        algo.runAlgorithm(input, output, minsup, minAverage, maxAverage, minPeriod, maxPeriod);

        algo.printStats();
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestSRPFPM_saveToFile.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}