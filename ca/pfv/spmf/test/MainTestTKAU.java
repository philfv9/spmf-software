package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.tkau.AlgoTKAU;

/**
 * Example of how to use the TKAU algorithm from the source code.
 * @author Philippe Fournier-Viger, 2026
 */
public class MainTestTKAU {

    /**
     * Main method to run the example.
     * @param arg command-line arguments (unused)
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] arg) throws IOException {
        String input = fileToPath("contextHAUIMiner.txt");
        String output = "output.txt";
        int k = 5;

        AlgoTKAU tkau = new AlgoTKAU();
        tkau.runAlgorithm(input, output, k);
        tkau.printStats();
    }

    /**
     * Resolve a test resource file path.
     * @param filename the resource file name
     * @return the decoded file path
     * @throws UnsupportedEncodingException if UTF-8 is unsupported
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestTKAU.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}