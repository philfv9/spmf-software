package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.AlgoSPADE;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.SequenceDatabase;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.creators.IdListCreator;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.creators.IdListCreator_Bitmap;

/**
 * Example of how to use the algorithm SPADE, saving the results in a given file
 * 
 * @author agomariz
 */
public class MainTestSPADE_AGP_Parallelized_BitMap_saveToFile {

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) throws IOException {
		String outputPath = ".//output.txt";
		// Load a sequence database
		double support = 0.5;

		boolean keepPatterns = true;
		boolean verbose = false;

		boolean dfs = true;

		// if you set the following parameter to true, the sequence ids of the sequences
		// where
		// each pattern appears will be shown in the result
		boolean outputSequenceIdentifiers = false;

		IdListCreator idListCreator = IdListCreator_Bitmap.getInstance();

		SequenceDatabase sequenceDatabase = new SequenceDatabase(idListCreator);

		sequenceDatabase.loadFile(fileToPath("contextPrefixSpan.txt"), support);

		System.out.println(sequenceDatabase.toString());

		AlgoSPADE algorithm = new AlgoSPADE(support, dfs);
		
		// This optional parameter allows to specify the minimum pattern length:
//		algorithm.setMinimumPatternLength(1);  // optional

		// This optional parameter allows to specify the maximum pattern length:
//		algorithm.setMaximumPatternLength(2);  // optional

		algorithm.runAlgorithmParallelized(sequenceDatabase, keepPatterns, verbose, outputPath,
				outputSequenceIdentifiers);
		System.out.println("Minimum support (relative) = " + support);
		System.out.println(algorithm.getNumberOfFrequentPatterns() + " frequent patterns.");

		System.out.println(algorithm.printStatistics());
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestSPADE_AGP_FatBitMap_saveToFile.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
