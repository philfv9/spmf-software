package ca.pfv.spmf.test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import ca.pfv.spmf.algorithms.episodes.emdo.AlgoPEMDO;
import ca.pfv.spmf.algorithms.episodes.emdo.Episode;
import ca.pfv.spmf.algorithms.episodes.emdo.Occurrence;

/**
 * Example of how to run the EMDO-Rules algorithm from the source code, to
 * discover frequent parallel episodes and episode rules in a complex event
 * sequence.
 * @see AlgoPEMDO
 * @author O. Ouarem et al.
 */
public class MainTestEMDORules {

    public static void main(String[] args) throws IOException {
        // Input file path
        String inputPath = fileToPath("db_emdo.txt");

        // Output file path
        String outputPath = "output.txt";

        // Minimum support (an integer representing a number of distinct occurrences)
        int minsup = 2;

        // Minimum confidence (a percentage, e.g. 0.2 = 20%)
        double minconf = 0.2;

        // Create the algorithm
        AlgoPEMDO algo = new AlgoPEMDO();

        // (1) Scan the input sequence to find all single-event occurrences
        Map<String, List<Occurrence>> singleEpisodeEvent = algo.scanSequence(inputPath, true, false);

        // (2) First, find the frequent parallel episodes using EMDO
        List<Episode> frequentEpisodes = algo.findFrequentEpisodesEMDO(minsup, singleEpisodeEvent);

        // Print statistics about episode mining
        algo.printStats();

        // (3) Second, generate episode rules from the frequent episodes found
        algo.generateEpisodeRulesWithPruning(frequentEpisodes, minconf);

        // Save the episode rules found to the output file
        algo.saveRulesToFile(outputPath);

        // Print statistics about rule mining
        algo.printStats();
    }

    public static String fileToPath(String filename) throws UnsupportedEncodingException {
        URL url = MainTestEMDORules.class.getResource(filename);
        return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
    }
}