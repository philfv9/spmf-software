package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.AlgoSPAM_AGP;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.SequenceDatabase;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.creators.IdListCreator;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.creators.IdListCreator_Bitmap;

/**
 * Example of how to use the algorithm SPAM but executing a 
 * postprocessing step at the end, in order to find only the closed 
 * frequent patterns. The output is saved in the main memory
 *
 * @author agomariz
 */
public class MainTestSPAM_AGP_PostProcessingStepForClosedMining_saveToMemory {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws IOException {
        // Load a sequence database
        // Load a sequence database
        double support = 0.5;

        boolean keepPatterns = true;
        boolean verbose = true;
        boolean outputSequenceIdentifiers = true;

        IdListCreator idListCreator = IdListCreator_Bitmap.getInstance();

        SequenceDatabase sequenceDatabase = new SequenceDatabase(idListCreator);

        //double relativeSupport = sequenceDatabase.loadFile(fileToPath("contextClaSP.txt"), support);
        sequenceDatabase.loadFile(fileToPath("contextPrefixSpan.txt"), support);

        AlgoSPAM_AGP algorithm = new AlgoSPAM_AGP(support);

        //System.out.println(sequenceDatabase.toString());
        algorithm.runAlgorithm(sequenceDatabase, keepPatterns, verbose, null,outputSequenceIdentifiers);
        System.out.println("Minsup (relative) : " + support);
        System.out.println(algorithm.getNumberOfFrequentPatterns() + " patterns found.");

        if (verbose && keepPatterns) {
            System.out.println(algorithm.printStatistics());
        }

        //uncomment if we want to see the Trie graphically
        //algorithm.showTree(null);
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestClaSP_saveToFile.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}
