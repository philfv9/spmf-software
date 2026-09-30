package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.graph_mining.closegraph.AlgoCloseGraph;

/**
 * Example of how to use the CloseGraph algorithm
 * from the source code and output the result to a file.
 * 
 * Philippe Fournier-Viger
 */
public class MainTestCloseGraph {

    public static void main(String[] args) throws IOException, ClassNotFoundException {

        // set the input and output file path
        String input  = fileToPath("contextTKG.txt");
        String output = ".//output.txt";

        // set the minimum support threshold
        double minSupport = 0.5;

        // The maximum number of edges for closed frequent subgraph patterns
        int maxNumberOfEdges = Integer.MAX_VALUE;

        // Output the ids of graphs containing each closed frequent subgraph
        boolean outputGraphIds = true;
        
        // Output file in DOT format
        boolean outputDotFile = false;

        // Apply the algorithm
        AlgoCloseGraph algo = new AlgoCloseGraph();
        boolean outputSingleFrequentVertices = false;
        algo.runAlgorithm(input, output, minSupport, outputSingleFrequentVertices,
                outputDotFile, maxNumberOfEdges, outputGraphIds);
        // Print statistics about the algorithm execution
        algo.printStats();
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestCloseGraph.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}