package ca.pfv.spmf.test;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.lppm.AlgoLPPMBreadth1;
import ca.pfv.spmf.algorithms.frequentpatterns.lppm.AlgoLPPMBreadth2;
public class MainTestLPPM_breadth {

	/**
	 * This file shows how to run the LPPM_Breadth algorithm on an input file.
	 * @author Peng yang
	 */
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


        // whether use the SPM strategy ( share prefix of patterns)
        boolean useSPM = true;

        if(useSPM){
            AlgoLPPMBreadth2 algo = new AlgoLPPMBreadth2();
            algo.runAlgorithm(inputFile, outputFile,maxPer,minDur,maxSoPer,self_increment);
            algo.printStats();
        }else {
            AlgoLPPMBreadth1 algo = new AlgoLPPMBreadth1();
            algo.runAlgorithm(inputFile, outputFile,maxPer,minDur,maxSoPer,self_increment);
            algo.printStats();
        }
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException{
        URL url = MainTestLPPM_breadth.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
    }
}
