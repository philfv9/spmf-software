package ca.pfv.spmf.test;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.npfpm.AlgoNPFPM;

/**
 * Example of how to use NPFPM from the source code and save
 * the resutls to a file.
 *
 * @author Vincent M. Nofong (modified from Philippe Fournier-Viger's implementation of FPGrowth)
 */
public class MainTestNPFPM_saveToFile {

    public static void main(String[] arg) throws FileNotFoundException, IOException {
        // the file paths
        String input = fileToPath("contextPFPM.txt");  // the database
        String output = ".//output.txt";  // the path for saving the frequent itemsets found

        double minsup = 0.1; // means a minsup of 1 transaction (we used a relative support)

		/*
		periodicity: is the user threshold of periodicity
		difference: is the acceptable difference for selecting PFPs with similar
		periodicities. That is:
		minimum = periodicity - difference
		maximum = periodicity + difference
		where difference cannot be greater than periodicity
 		*/
        double periodicity = 2; //means the user desired periodicity
        double difference = 1.5; //means the user acceptable difference of periodicity

        // Applying the NPFPM algorithmMainTestFPGrowth.java
        AlgoNPFPM algo = new AlgoNPFPM();

        algo.runAlgorithm(input, output, minsup, periodicity, difference);
        algo.printStats();
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestNPFPM_saveToFile.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}