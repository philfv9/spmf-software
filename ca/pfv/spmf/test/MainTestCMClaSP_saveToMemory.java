package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.AlgoCMClaSP_bitset;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.SequenceDatabase;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.creators.IdListCreator;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.creators.IdListCreator_Bitmap;

/**
 * Example of how to use the algorithm ClaSP, saving the results in the
 * main memory
 *
 * @author agomariz
 */
public class MainTestCMClaSP_saveToMemory {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {

        // Load a sequence database
        double support = 0.5;

        boolean keepPatterns = true;
        boolean verbose = true;
        boolean findClosedPatterns = true;
        boolean executePruningMethods = true;
        // if you set the following parameter to true, the sequence ids of the sequences where
        // each pattern appears will be shown in the result
        boolean outputSequenceIdentifiers = false;

        IdListCreator idListCreator = IdListCreator_Bitmap.getInstance();

        SequenceDatabase sequenceDatabase = new SequenceDatabase(idListCreator);

        //double relativeSupport = sequenceDatabase.loadFile(fileToPath("contextClaSP.txt"), support);
        sequenceDatabase.loadFile(fileToPath("contextPrefixSpan.txt"), support);

        AlgoCMClaSP_bitset algorithm = new AlgoCMClaSP_bitset(support, findClosedPatterns, executePruningMethods, false);


        //System.out.println(sequenceDatabase.toString());
        algorithm.runAlgorithm(sequenceDatabase, keepPatterns, verbose, null, outputSequenceIdentifiers);
        System.out.println("Minsup (relative) : " + support);
        System.out.println(algorithm.getNumberOfFrequentPatterns() + " patterns found.");

        if (verbose && keepPatterns) {
            System.out.println(algorithm.printStatistics());
        }
        
      //uncomment if we want to see the Trie graphically
//        ShowTrie.showTree(algorithm.getFrequentAtomsTrie());

    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestClaSP_saveToFile.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}
