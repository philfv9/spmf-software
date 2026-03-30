package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.graph_mining.tkg.AlgoCGSPANSupport;

/**
 * Example of how to use the CGSPAN algorithm with support
 * from the source code and output the result to a file.
 *
 * @author Zevin Shaul
 */
public class MainTestCGSPANSupport {

    public static void main(String [] arg) throws IOException, ClassNotFoundException, InterruptedException {

        // set the input path
        String input = fileToPath("contextTKG.txt");
        String output = ".//output.txt";

        // set the minimum support threshold
        double minSupport = 0.9;

        // The maximum number of edges for frequent subgraph patterns
        int maxNumberOfEdges = Integer.MAX_VALUE;

        // If true, single frequent vertices will be output
        boolean outputSingleFrequentVertices = false;

        // If true, a dot file will be output for visualization using GraphViz
        boolean outputDotFile = false;

        // Output the ids of graph containing each frequent subgraph
        boolean outputGraphIds = true;

        // Create the algorithm
        AlgoCGSPANSupport algo = new AlgoCGSPANSupport();

        // If the following line is uncommented, extra information is stored in the output file
        // algo.setDebugMode(true);

        algo.setDetectEarlyTerminationFailure(true);

        algo.setPdfsAutomorphismOptimization(false);

        algo.runAlgorithm(input, output, minSupport, outputSingleFrequentVertices,
                outputDotFile, maxNumberOfEdges, outputGraphIds);

        // Print statistics about the algorithm execution
        algo.printStats();
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException{
        URL url = MainTestGSPAN.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
    }
}
