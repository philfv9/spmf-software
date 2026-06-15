package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.uapriori.UncertainTransactionDatabase;
import ca.pfv.spmf.algorithms.frequentpatterns.tufp.AlgoTUFP;

/**
 * Example of how to use the TUFP algorithm from source code.
 * @author Philippe Fournier-Viger
 */
public class MainTestTUFP {

    public static void main(String[] arg) throws IOException {

        // Load the uncertain transaction database
        UncertainTransactionDatabase database = new UncertainTransactionDatabase();
        try {
            database.loadFile(fileToPath("contextUncertain.txt"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        database.printDatabase();

        // Set the desired number of top-k patterns
        int k = 10;

        String output = ".//output.txt";

        // Run the TUFP algorithm
        AlgoTUFP algo = new AlgoTUFP(database);
        algo.runAlgorithm(k, output);
        algo.printStats();
    }

    /**
     * Convert a filename to its absolute path.
     * @param filename the filename to convert
     * @return the absolute path as a string
     * @throws UnsupportedEncodingException if encoding is not supported
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestTUFP.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}