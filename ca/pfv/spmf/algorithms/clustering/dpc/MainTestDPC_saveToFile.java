package ca.pfv.spmf.algorithms.clustering.dpc;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.clustering.distanceFunctions.DistanceEuclidian;
import ca.pfv.spmf.algorithms.clustering.distanceFunctions.DistanceFunction;

/**
 * Example of how to use the DPC (Density Peak Clustering) algorithm, in source code.
 * @author Philippe Fournier-Viger, copyright 2024
 */
public class MainTestDPC_saveToFile {

    public static void main(String[] args) throws NumberFormatException, IOException {
        // Input and output file paths
        String input = fileToPath("szu.txt");
        String output = "./output.txt";

        // We specify that in the input file, double values on each line are separated by spaces
        String separator = " ";

        // The parameters of DPC
        double dc = 1d;
        int rhoMin = 150;
        double deltaMin = 1d;

        // Apply the algorithm
        AlgoDPC algo = new AlgoDPC();

        DistanceFunction distanceFunction = new DistanceEuclidian(); 

        // Run the DPC algorithm
        algo.runAlgorithm(input, distanceFunction, separator, rhoMin, deltaMin, dc);

        // Print the statistics
        algo.printStatistics();

        // Save the clusters to the output file
        algo.saveToFile(output);
    }

    /**
     * Convert a resource file name to a file path.
     *
     * @param filename the name of the file
     * @return the file path
     * @throws UnsupportedEncodingException if the encoding is not supported
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestDPC_saveToFile.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}
