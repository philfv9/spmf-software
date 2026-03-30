package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.graph_mining.tkg.AlgoCGSPANMNI;

/**
 * Example of how to use the CGSPAN algorithm with MNI
 * from the source code and output the result to a file.
 *
 * @author Zevin Shaul
 */
public class MainTestCGSPANMNI_singleGraph {

    public static void main(String [] arg) throws IOException, ClassNotFoundException, InterruptedException {

        // set the input path
        String input = fileToPath("contextSingleGraph.txt");
        String output = ".//output.txt";

        int minMNI = 2;

        // The maximum number of edges for frequent subgraph patterns
        int maxNumberOfEdges = 4;

        // If true, single frequent vertices will be output
        boolean outputSingleFrequentVertices = false;

        // If true, a dot file will be output for visualization using GraphViz
        boolean outputDotFile = false;

        // Output the ids of graph containing each frequent subgraph
        boolean outputGraphIds = true;

        // Create the algorithm
        AlgoCGSPANMNI algo = new AlgoCGSPANMNI();

        // If the following line is uncommented, extra information is stored in the output file
        // algo.setDebugMode(false);

        algo.setDetectEarlyTerminationFailure(false);

        algo.setPdfsAutomorphismOptimization(true);
        
        // for debugging
//        algo.setDebugMode(true);

        // run the algorithm
        algo.runAlgorithm(input, output, minMNI, outputSingleFrequentVertices,
                outputDotFile, maxNumberOfEdges, outputGraphIds);

        // Print statistics about the algorithm execution
        algo.printStats();
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException{
        URL url = MainTestGSPAN.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
    }
}
