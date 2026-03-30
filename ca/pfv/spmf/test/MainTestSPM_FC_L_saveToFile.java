package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.AlgoSPM_FC_L;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.SequenceDatabase;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.creators.AbstractionCreator;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.creators.AbstractionCreator_Qualitative;

/**
 * Example of how to use the algorithm SPM_FC_L, saving the results to a file
 * 
 * @author WeiYe
 * @see AlgoSPM_FC_L
 */
public class MainTestSPM_FC_L_saveToFile {

	public static void main(String[] args) throws IOException {
		// Parameters of the algorithm
		double alpha = 0.5 / 3.0;
		double beta = 1.5 / 3.0;
		double gamma = 1.0 / 3.0;
		double minimumSupport = (double) 0.08;
		double mingap = 0;
		double maxgap = Integer.MAX_VALUE;
		double windowSize = 0;

		boolean keepPatterns = true;
		boolean verbose = false;
		
		// Input file path
		String inputFilePath = fileToPath("mooc_small.txt");

		// The output file path
		String outputFilePath = "output.txt";

		// if you set the following parameter to true, the sequence ids of the sequences
		// where
		// each pattern appears will be shown in the result
		boolean outputSequenceIdentifiers = false;

		// Read the database
		AbstractionCreator abstractionCreator = AbstractionCreator_Qualitative.getInstance();
		SequenceDatabase sequenceDatabase = new SequenceDatabase(abstractionCreator);
		
		// datafile:mooc.txt (Containing timestamp data format)
		sequenceDatabase.loadFile(inputFilePath, minimumSupport, alpha, beta, gamma);

		// Run the algorithm
		AlgoSPM_FC_L algorithm = new AlgoSPM_FC_L(minimumSupport, mingap, maxgap, windowSize, abstractionCreator);
		algorithm.runAlgorithm(sequenceDatabase, keepPatterns, verbose, outputFilePath, outputSequenceIdentifiers,
				alpha, beta, gamma);
		System.out.println(algorithm.printStatistics());
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestSPM_FC_L_saveToFile.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
