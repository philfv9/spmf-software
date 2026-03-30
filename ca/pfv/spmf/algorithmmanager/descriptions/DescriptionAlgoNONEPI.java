package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;
import java.util.List;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.episodes.emma.AlgoEMMA;
import ca.pfv.spmf.algorithms.episodes.nonepi.AlgoNONEPI;
import ca.pfv.spmf.algorithms.episodes.nonepi.Episode;

/**
 * This class describes the EMMA algorithm parameters. It is designed to be used
 * by the graphical and command line interface.
 * 
 * @see AlgoEMMA
 * @author Yang Peng
 */
public class DescriptionAlgoNONEPI extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoNONEPI() {
	}

	@Override
	public String getName() {
		return "NONEPI";
	}

	@Override
	public String getAlgorithmCategory() {
		return "EPISODE RULE MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/NONEPI_episode_rules.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		int minsup = getParamAsInteger(parameters[0]);
		float minconf = getParamAsFloat(parameters[1]);

		// apply the algorithm
        // (1) First extracts frequent episodes with NONEPI
        AlgoNONEPI algo = new AlgoNONEPI();
        List<Episode> frequentEpisodes = algo.findFrequentEpisodes(inputFile, minsup);
        
        // (2) Second, find episodes rules from frequent episodes        
        algo.findNONEpiRulesWithPruning(frequentEpisodes, minconf);
        algo.printStats();
        algo.saveRulesToFile(outputFile);
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("Minimum support", "(e.g. 2)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("Minimum confidence", "(e.g. 0.2)", Float.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Oualid Ouarem";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[] { "Database of instances", "Transaction database", "Transaction database with timestamps" };
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[] { "Patterns", "Episode rules" };
	}
	
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}

}
