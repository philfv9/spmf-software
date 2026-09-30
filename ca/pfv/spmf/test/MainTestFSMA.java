package ca.pfv.spmf.test;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.graph_mining.fsma.AlgoFSMA;

/**
 * Example of how to run the FSMA algorithm.
 *  @author Chen Hui
 */
public class MainTestFSMA {

    public static void main(String[] args) throws IOException {
        String input = fileToPath("contextTKG.txt");   // input graph database
        String output = "output.txt";          // output file for frequent subgraphs
        double minSupport = 0.9;               // 100% (relative). Use e.g. 0.5 for 50%,
                                                // or 3 for an absolute minimum support of 3.

        // Output the ids of graphs containing each frequent subgraph
        boolean outputGraphIds = false;

        AlgoFSMA algo = new AlgoFSMA();
        algo.runAlgorithm(input, output, minSupport, outputGraphIds);
        algo.printStats();
    }
    
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestFSMA.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}