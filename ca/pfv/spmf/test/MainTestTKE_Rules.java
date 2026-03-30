package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;
import java.util.PriorityQueue;

import ca.pfv.spmf.algorithms.episodes.emma.AlgoTKE;
import ca.pfv.spmf.algorithms.episodes.emma.EpisodeEMMA;
import ca.pfv.spmf.algorithms.episodes.standardepisoderules.AlgoGenerateEpisodeRules;
import ca.pfv.spmf.algorithms.episodes.standardepisoderules.EpisodeRule;

/**
 * This file shows how to run the TKE algorithm on an input file.
 * 
* @author Peng yang, Yangming Chen, Philippe Fournier-Viger
 */
public class MainTestTKE_Rules {
	public static void main(String[] args) throws IOException {

		// the Input and output files
		String inputFile = fileToPath("contextEmma.txt");

		/// STEP 1 : WE FIRST NEED TO FIND THE FREQUENT EPISODES
		// The algorithm parameters:
		int k = 6;
		int maxWindow = 2;

		// If the input file does not contain timestamps, then set this variable to true
		// to automatically assign timestamps as 1,2,3...
		boolean selfIncrement = false;
		
		// Activate the dynamic search optimization  (it improves performance)
		boolean useDynamicSearch = true;

		AlgoTKE algo = new AlgoTKE();
		algo.setUseDynamicSearch(useDynamicSearch);
				
		PriorityQueue<EpisodeEMMA> frequentEpisodes = algo.runAlgorithm(inputFile, null, k, maxWindow, selfIncrement);
		algo.printStats();
		
        //frequentEpisodes.printFrequentEpisodes();

		/// STEP 2 : WE USE THE FREQUENT EPISODES TO GENERATE EPISODE RULES
		int minSup = 2;
		double minConfidence = 0.2;
		int maxConsequentSize = 1;

		String outputFileRules = "Output.txt";
		
        AlgoGenerateEpisodeRules ruleMiner = new AlgoGenerateEpisodeRules();
    	List<EpisodeRule> ruleList = ruleMiner.runAlgorithm(frequentEpisodes, minSup, minConfidence, maxConsequentSize);
		ruleMiner.printStats();
		ruleMiner.writeRulesToFileSPMFFormat(outputFileRules);
//		ruleMiner.printRules();
        
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestTKE_Rules.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
