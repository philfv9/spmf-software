package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.algorithms.episodes.emma.AlgoAFEM;
import ca.pfv.spmf.algorithms.episodes.general.FrequentEpisodes;
import ca.pfv.spmf.algorithms.episodes.standardepisoderules.AlgoGenerateEpisodeRules;

/**
 * This file shows how to run the AFEM algorithm on an input file to generate episode rules
 * 
 * @author Peng yang, Yangming Chen, Philippe Fournier-Viger
 * @see AlgoAFEM
 */
public class MainTestAFEM_Rules {
	public static void main(String[] args) throws IOException {

		// the Input and output files
		String inputFile = fileToPath("contextEMMA.txt");

		/// STEP 1 : WE FIRST NEED TO FIND THE FREQUENT EPISODES
		// The algorithm parameters:
		int minSup = 2;
		int maxWindow = 3;
		// If the input file does not contain timestamps, then set this variable to true
		// to automatically assign timestamps as 1,2,3...
		boolean selfIncrement = false;

		AlgoAFEM algo = new AlgoAFEM();
		FrequentEpisodes frequentEpisodes = algo.runAlgorithm(inputFile, null, minSup, maxWindow, selfIncrement);
		algo.printStats();
//        frequentEpisodes.printFrequentEpisodes();

		/// STEP 2 : WE USE THE FREQUENT EPISODES TO GENERATE EPISODE RULES
		double minConfidence = 0.2;
		int maxConsequentSize = 1;

		String outputFileRules = "Output.txt";

		AlgoGenerateEpisodeRules ruleMiner = new AlgoGenerateEpisodeRules();
		ruleMiner.runAlgorithm(frequentEpisodes, minSup, minConfidence, maxConsequentSize);
		
//		List<EpisodeRule> ruleList = ruleMiner.runAlgorithm(frequentEpisodes, minSup, minConfidence, maxConsequentSize);
		ruleMiner.printStats();
		ruleMiner.writeRulesToFileSPMFFormat(outputFileRules);
		ruleMiner.printRules();
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestAFEM_Rules.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
