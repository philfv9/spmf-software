package ca.pfv.spmf.algorithms.episodes.maxfem;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.episodes.emma.AlgoAFEM;
import ca.pfv.spmf.algorithms.episodes.maxfem.AlgoMAXFEM;

/**
 * This file shows how to run the AFEM algorithm on an input file.
 * @author Philippe Fournier-Viger
 */
public class MainTestMAXFEM_Simple {
    public static void main(String[] args) throws IOException {

        // the Input and output files
        String inputFile = fileToPath("contextMAXFEM.txt");
        String outputFile = "output.txt";

        // The algorithm parameters:
        int minSup = 2;
        int maxWindow = 3;

        // If the input file does not contain timestamps, then set this variable to true
        // to automatically assign timestamps as 1,2,3...
        boolean selfIncrement = false;
        
//        LEVEL: 1
//        LEVEL: 2
//        2 -1 4 -1 #SUP: 1
//        LEVEL: 3
//        1 -1 1 -1 1 2 -1 #SUP: 1
//        1 -1 1 2 -1 3 -1 #SUP: 1
//        1 2 -1 3 -1 2 -1 #SUP: 1
        
//        =================

//        // the Input and output files
//        String inputFile = fileToPath("retail.txt");
//        String outputFile = "output.txt";
//
//        // The algorithm parameters:
//        int minSup = 3000;
//        int maxWindow = 5;
//
//        // If the input file does not contain timestamps, then set this variable to true
//        // to automatically assign timestamps as 1,2,3...
//        boolean selfIncrement = true;
        
//      =================

        // self-growth = flase only for online_minute.txt , others are true
        AlgoAFEM algo = new AlgoAFEM();
        algo.runAlgorithm(inputFile, outputFile,minSup,maxWindow,selfIncrement);
        algo.printStats();
        
//        =============  EMMA(head episode) - STATS =============  Retail   3000 minsup - 5 window
//        		 Candidates count : 38880
//        		 The algorithm stopped at size : 6
//        		 Frequent itemsets count : 1619
//        		 Maximum memory usage : 44.62596893310547 mb
//        		 Total time ~ : 7124 ms
//        		===================================================
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestMAXFEM_Simple.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
    }
}
