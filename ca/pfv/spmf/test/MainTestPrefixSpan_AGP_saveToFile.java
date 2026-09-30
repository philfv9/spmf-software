package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.prefixSpan_AGP_fast.AlgoPrefixSpan_AGP;
import ca.pfv.spmf.algorithms.sequentialpatterns.prefixSpan_AGP_fast.SequenceDatabase;

/**
 * Example of how to use the algorithm GSP, saving the results in a given
 * file
 * @author agomariz
 */
public class MainTestPrefixSpan_AGP_saveToFile {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
    	String output = ".//output.txt";
        // Load a sequence database
        double support = (double)180/360;
        
        boolean keepPatterns = true;
        boolean verbose=false;
        
        // if you set the following parameter to true, the sequence ids of the sequences where
        // each pattern appears will be shown in the result
        boolean outputSequenceIdentifiers = false;

        SequenceDatabase sequenceDatabase = new SequenceDatabase();

        //sequenceDatabase.loadFile(fileToPath("salidaFormateadaCodificadaSinIDs.txt"), support);
        sequenceDatabase.loadFile(fileToPath("contextPrefixSpan.txt"), support);

        AlgoPrefixSpan_AGP algorithm = new AlgoPrefixSpan_AGP(support);
        
		// This optional parameter allows to specify the minimum pattern length:
//		algorithm.setMinimumPatternLength(1);  // optional

		// This optional parameter allows to specify the maximum pattern length:
//		algorithm.setMaximumPatternLength(2);  // optional

        System.out.println(sequenceDatabase.toString());

        //Put the concrete path file where we want to keep the output
        algorithm.runAlgorithm(sequenceDatabase,keepPatterns,verbose, output, outputSequenceIdentifiers);
        System.out.println(algorithm.getNumberOfFrequentPatterns()+ " patterns found.");
        System.out.println(algorithm.printStatistics());
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestPrefixSpan_AGP_saveToFile.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}
