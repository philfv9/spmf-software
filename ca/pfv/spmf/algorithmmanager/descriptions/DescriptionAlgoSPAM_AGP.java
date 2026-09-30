package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.AlgoSPAM_AGP;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.SequenceDatabase;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.creators.IdListCreator;
import ca.pfv.spmf.algorithms.sequentialpatterns.clasp_spade_spam_AGP_fast.idLists.creators.IdListCreator_FatBitmap;

/**
 * This class describes the SPAM algorithm parameters (version implemented by
 * Antonio G.P.). It is designed to be used by the graphical and command line
 * interface.
 * 
 * @see AlgoSPAM_AGP
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoSPAM_AGP extends DescriptionOfAlgorithm {

	/**
	 * Default constructor.
	 */
	public DescriptionAlgoSPAM_AGP() {
	}

	/**
	 * Gets the name of the algorithm.
	 * 
	 * @return the algorithm name
	 */
	@Override
	public String getName() {
		return "SPAM_AGP";
	}

	/**
	 * Gets the category of the algorithm.
	 * 
	 * @return the algorithm category
	 */
	@Override
	public String getAlgorithmCategory() {
		return "SEQUENTIAL PATTERN MINING";
	}

	/**
	 * Gets the URL of the documentation for this algorithm.
	 * 
	 * @return the documentation URL
	 */
	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/SPAM.php";
	}

	/**
	 * Runs the SPAM_AGP algorithm with the given parameters on an input file and writes the result to an output file.
	 * 
	 * @param parameters the algorithm parameters
	 * @param inputFile  the path to the input file
	 * @param outputFile the path to the output file
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		// create the id-list creator
		IdListCreator idListCreator = IdListCreator_FatBitmap.getInstance();

		// read the minimum support threshold
		double minSupport = getParamAsDouble(parameters[0]);

		// read the optional minimum pattern length
		int minPatternLength = 0;
		if (parameters.length >= 2 && "".equals(parameters[1]) == false) {
			minPatternLength = getParamAsInteger(parameters[1]);
		}

		// read the optional maximum pattern length
		int maxPatternLength = Integer.MAX_VALUE;
		if (parameters.length >= 3 && "".equals(parameters[2]) == false) {
			maxPatternLength = getParamAsInteger(parameters[2]);
		}

		// read the optional flag to output sequence identifiers
		boolean outputSeqIdentifiers = false;
		if (parameters.length >= 4 && "".equals(parameters[3]) == false) {
			outputSeqIdentifiers = getParamAsBoolean(parameters[3]);
		}

		// create the algorithm instance
		AlgoSPAM_AGP algo = new AlgoSPAM_AGP(minSupport);

		// set the optional pattern length constraints
		algo.setMinimumPatternLength(minPatternLength);
		algo.setMaximumPatternLength(maxPatternLength);

		/*
		 * if("".equals(parameters[1]) == false){
		 * algo.setMaximumPatternLength(getParamAsInteger(parameters[1])); }
		 */

		// load the sequence database
		SequenceDatabase sd = new SequenceDatabase(idListCreator);
		sd.loadFile(inputFile, minSupport);

		// run the algorithm and print statistics
		algo.runAlgorithm(sd, true, false, outputFile, outputSeqIdentifiers);
		System.out.println(algo.printStatistics());
	}

	/**
	 * Gets the description of all algorithm parameters.
	 * 
	 * @return an array describing the parameters
	 */
	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[4];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.4 or 40%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Min pattern length", "(e.g. 1 items)", Integer.class, true);
		parameters[2] = new DescriptionOfParameter("Max pattern length", "(e.g. 10 items)", Integer.class, true);
		parameters[3] = new DescriptionOfParameter("Show sequence ids?", "(default: false)", Boolean.class, true);
		return parameters;
	}

	/**
	 * Gets the names of the authors of this implementation.
	 * 
	 * @return the implementation author names
	 */
	@Override
	public String getImplementationAuthorNames() {
		return "Antonio Gomariz Penalver";
	}

	/**
	 * Gets the accepted input file types.
	 * 
	 * @return an array of input file type descriptions
	 */
	@Override
	public String[] getInputFileTypes() {
		return new String[] { "Database of instances", "Sequence database", "Simple sequence database" };
	}

	/**
	 * Gets the output file types produced by this algorithm.
	 * 
	 * @return an array of output file type descriptions
	 */
	@Override
	public String[] getOutputFileTypes() {
		return new String[] { "Patterns", "Sequential patterns", "Frequent sequential patterns" };
	}

	/**
	 * Gets the type of this algorithm.
	 * 
	 * @return the algorithm type
	 */
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}

}