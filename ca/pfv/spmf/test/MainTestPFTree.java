package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.pftree.AlgoPFTree;

/**
 * Example of how to use the PFTree algorithm from the source code.
 * @author Philippe Fournier-Viger, 2026
 */
public class MainTestPFTree {

    /**
     * Main method to run the PFTree algorithm example.
     * @param arg command-line arguments (not used)
     * @throws IOException if an error occurs while reading or writing files
     */
    public static void main(String[] arg) throws IOException {

        String input = fileToPath("contextPFPM.txt");
        String output = ".//output.txt";

        // Maximum periodicity threshold (lambda): e.g. 4 transactions
        int maxPeriodicity = 4;

        // Minimum support threshold as a percentage of the database size: e.g. 50%
        double minSupport = 50.0;

        AlgoPFTree algorithm = new AlgoPFTree();
        algorithm.runAlgorithm(input, output, maxPeriodicity, minSupport);
        algorithm.printStats();
    }

    /**
     * Converts a filename to an absolute file path using the class loader.
     * @param filename the name of the file
     * @return the absolute path of the file
     * @throws UnsupportedEncodingException if the encoding is not supported
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestPFTree.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}