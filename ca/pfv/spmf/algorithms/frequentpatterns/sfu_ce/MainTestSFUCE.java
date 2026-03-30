package ca.pfv.spmf.algorithms.frequentpatterns.sfu_ce;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

/**
 * This is the main class of algorithm sfu_ce
 *
 * SFU-CE: Skyline Frequent-Utility Itemset Discovery Using the Cross-Entropy Method
 *
 * @author Wei Song,Chuanlong Zheng
 *
 */
public class MainTestSFUCE {
    public static void main(String[] args) throws IOException {
        //input file
        String input = fileToPath("DB_Utility.txt");
        //output file
        String output = ".//output.txt";
        AlgoSFU_CE sfu_ce = new AlgoSFU_CE();
        //run algorithm sfu_ce
        sfu_ce.runAlgorithm(input, output);
        //execute state
        sfu_ce.printStats();


    }


    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestSFUCE.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}
