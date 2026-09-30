package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.graph_mining.fsg.AlgoFSG;

/**
 * Example of how to use the FSG algorithm
 * from the source code and output the result to a file.
 * @author Chen Hui
 */
public class MainTestFSG {

    public static void main(String[] arg) throws IOException {

        // set the input and output file path
        String input  = fileToPath("contextTKG.txt");
        String output = ".//output.txt";

        // minimum support threshold (fraction of graphs, e.g. 0.9 = 90%)
        double minSupport = 0.9;

        // Output the ids of graphs containing each frequent subgraph
        boolean outputGraphIds = false;

        // Run FSG
        AlgoFSG algo = new AlgoFSG();
        algo.runAlgorithm(input, output, minSupport, outputGraphIds);

        // Print statistics
        algo.printStats();
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestFSG.class.getResource(filename);
        try {
            return java.nio.file.Paths.get(url.toURI()).toString();
        } catch (java.net.URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}