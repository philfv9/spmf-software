package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.lppm.AlgoLPPMDepth1;
import ca.pfv.spmf.algorithms.frequentpatterns.lppm.AlgoLPPMDepth2;

/**
 * This file shows how to run the LPPM_Depth algorithm on an input file.
 * @author Peng yang
 */
public class MainTestLPPM_depth {
    public static void main(String[] args) throws IOException {
        // the Input and output files
        String inputFile = fileToPath("contextLPP2_notimestamp.txt");
        String outputFile = "output.txt";

        // The algorithm parameters:
        int maxPer = 3;
        int minDur = 7;
        int maxSoPer = 2;

        // If the input file does not contain timestamps, then set this variable to true
        // to automatically assign timestamps as 1,2,3...
        boolean self_increment = true;

        // whether use the OTS strategy (Only using Timestamps of Single items)
        boolean useOTS = false;

        if(useOTS){
            AlgoLPPMDepth1 algo = new AlgoLPPMDepth1();
            algo.runAlgorithm(inputFile, outputFile,maxPer,minDur,maxSoPer,self_increment);
            algo.printStats();
        }else {
            AlgoLPPMDepth2 algo = new AlgoLPPMDepth2();
            algo.runAlgorithm(inputFile, outputFile,maxPer,minDur,maxSoPer,self_increment);
            algo.printStats();
        }
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestLPPM_breadth.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
    }
}
