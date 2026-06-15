package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.frequentpatterns.HTK_Miner.AlgoHTKnegFIN;
import ca.pfv.spmf.input.transaction_database_list_integers.TransactionDatabase;

/**
 * Example of how to use HTK_negFIN algorithm from the source code. HTK_negFIN
 * mines the top-k frequent itemsets based on negNodesets presented in the
 * negFIN algorithm (Aryabarzan, N., Minaei-Bidgoli, B., & Teshnehlab, M.
 * (2018). negFIN: An efficient algorithm for fast mining frequent itemsets.
 * Expert Systems with Applications, 105, 129-143.).
 *
 * @author Konstantinos Malliaridis and Stefanos Ougiaroglou
 */
public class MainTestHTKnegFIN_saveToFile {

    public static void main(String[] arg) throws IOException {

        // the file paths
        String input = fileToPath("contextPasquier99.txt"); // the database
        String output = ".//output.txt"; // the path for saving the top-k itemsets found

        // parameters
        int topK = 8; // the number of top-k itemsets to find

        // Loading the transaction database
        TransactionDatabase database = new TransactionDatabase();
        try {
            database.loadFile(input);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Applying the HTK_negFIN algorithm
        AlgoHTKnegFIN algo = new AlgoHTKnegFIN();

        // Run the algorithm
        algo.runAlgorithm(input, output, topK, " ");

        // Print statistics
        algo.printStats();
    }

    /**
     * Method to convert a file name to its path
     *
     * @param filename the file name
     * @return the file path
     * @throws UnsupportedEncodingException
     */
    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestHTKnegFIN_saveToFile.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}