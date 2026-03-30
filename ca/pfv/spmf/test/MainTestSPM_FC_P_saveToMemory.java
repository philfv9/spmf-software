package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.AlgoSPM_FC_P;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.SequenceDatabase;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.creators.AbstractionCreator;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.creators.AbstractionCreator_Qualitative;

/**
 * Example of how to use the algorithm GSP, saving the results in the main
 * memory
 *
 * @author WeiYe
 * @see AlgoSPM_FC_P
 */
public class MainTestSPM_FC_P_saveToMemory {

	public static void main(String[] args) throws IOException {
		// Parameters of the algorithm
		double support = 0.08;
		double alpha = 0.5 / 3.0;
		double beta = 1.5 / 3.0;
		double gamma = 1.0 / 3.0;
		boolean keepPatterns = true;
		// boolean verbose = false;
		boolean verbose = true;

		// if you set the following parameter to true, the sequence ids of the sequences
		// where
		// each pattern appears will be shown in the result
		boolean outputSequenceIdentifiers = false;
		
		// Read the database
		AbstractionCreator abstractionCreator = AbstractionCreator_Qualitative.getInstance();
		SequenceDatabase sequenceDatabase = new SequenceDatabase(abstractionCreator);
		sequenceDatabase.loadFile(fileToPath("mooc_small.txt"), support, alpha, beta, gamma);
		
		// Run the algorithm
		AlgoSPM_FC_P algorithm = new AlgoSPM_FC_P(support, abstractionCreator);
		algorithm.runAlgorithm(sequenceDatabase, keepPatterns, verbose, null, outputSequenceIdentifiers, support, alpha,
				beta, gamma);
		System.out.println(algorithm.printStatistics());
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestSPM_FC_P_saveToMemory.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
