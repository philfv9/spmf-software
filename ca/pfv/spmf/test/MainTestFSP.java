package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.graph_mining.fsp.AlgoFSP;

/**
 * Example of how to use the FSP algorithm
 * from the source code and output the result to a file.
 * @author Chen Hui
 */
public class MainTestFSP {

    public static void main(String[] arg) throws IOException, ClassNotFoundException {

        // set the input and output file path
        String input  = fileToPath("contextTKG.txt");
        String output = ".//output.txt";

        // minimum support threshold (fraction of graphs, e.g. 0.9 = 90%)
        double minSupport = 0.9;

        // Run FSP
        AlgoFSP algo = new AlgoFSP();
        algo.runAlgorithm(input, output, minSupport,
                true,            // outputSingleVertices
                false,           // outputDotFile
                Integer.MAX_VALUE, // maxNumberOfEdges (no limit)
                true);           // outputGraphIds

        // Print statistics
        algo.printStats();
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestFSP.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}