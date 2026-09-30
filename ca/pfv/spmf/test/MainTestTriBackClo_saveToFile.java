package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.tribackclo.AlgoTriBackClo;

/**
 * Example of how to use the TriBackClo algorithm in source code.
 * @author Philippe Fournier-Viger
 */
public class MainTestTriBackClo_saveToFile {

    public static void main(String[] args) throws IOException {
        // input file
        String inputFile = fileToPath("contextPrefixSpan.txt");

        // output file path
        String outputPath = ".//output.txt";

        // minimum support threshold (absolute number of sequences)
        int minsup = 2;

        // Create an instance of the algorithm
        AlgoTriBackClo algo = new AlgoTriBackClo();

        // Set algorithm options
        algo.enableSubtreePruning = true;      // enable/disable subtree pruning optimization
        algo.enableNodeGating = true;          // enable/disable node gating optimization
        algo.enableEagerVerification = false;  // enable/disable eager verification

        // execute the algorithm
        algo.runAlgorithm(inputFile, outputPath, minsup);
        algo.printStatistics();
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestTriBackClo_saveToFile.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}