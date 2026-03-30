package ca.pfv.spmf.test;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.lppgrowth.AlgoLPPGrowth;

/**
 * This file shows how to run the LPP_Growth algorithm on an input file.
 * @author Peng yang
 */
public class MainTestLPPGrowth {
    public static void main(String[] args) throws IOException {
        // the Input and output files
        String inputFile = fileToPath("contextLPP.txt");
        String outputFile = "output.txt";

        // The algorithm parameters:
        int maxPer = 3;
        int minDur = 7;
        int maxSoPer = 2;

        // If the input file does not contain timestamps, then set this variable to true
        // to automatically assign timestamps as 1,2,3...
        boolean self_increment = false;

        AlgoLPPGrowth algo = new AlgoLPPGrowth();
        algo.runAlgorithm(inputFile,outputFile, maxPer,minDur,maxSoPer,self_increment);
        algo.printStats();
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestLPPGrowth.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
    }
}
