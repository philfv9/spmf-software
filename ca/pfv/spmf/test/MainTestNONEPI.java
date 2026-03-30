/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;

import ca.pfv.spmf.algorithms.episodes.nonepi.AlgoNONEPI;
import ca.pfv.spmf.algorithms.episodes.nonepi.Episode;

/**
 * Example of how to run the NONEPI algorithm from the source code.
 * @see AlgoNONEPI
 * @author oualid
 */
public class MainTestNONEPI {

    public static void main(String[] args) throws IOException {
        // Input file path
    	String inputPath = fileToPath("contextEMMA.txt");
    	
    	// Input file path
    	String outputhPath = "output.txt";
    	
    	// Minimum support (an integer representing a number of occurrences)
    	int minsup = 2;
        
        // Minimum confidence (represnts a percentage)
        float minconf = 0.2f;
        
        // (1) First extracts frequent episodes with NONEPI
        AlgoNONEPI algo = new AlgoNONEPI();
        List<Episode> frequents = algo.findFrequentEpisodes(inputPath, minsup);
        
        // (2) Second, find episodes rules from frequent episodes        
        List<String> rules = algo.findNONEpiRulesWithPruning(frequents, minconf);
        algo.printStats();
        algo.saveRulesToFile(outputhPath);
    }
    
	public static String fileToPath(String filename) throws UnsupportedEncodingException{
		URL url = MainTestLCIM.class.getResource(filename);
		 return java.net.URLDecoder.decode(url.getPath(),"UTF-8");
	}
}
