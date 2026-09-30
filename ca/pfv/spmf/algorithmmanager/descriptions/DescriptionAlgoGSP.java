package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.gsp_AGP_fast.AlgoGSP;

/**
 * This class describes the GSP algorithm parameters. It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoGSP
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoGSP extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoGSP(){
	}

	/**
	 * Get the name of the algorithm.
	 * @return the algorithm name as a string
	 */
	@Override
	public String getName() {
		return "GSP";
	}

	/**
	 * Get the category of the algorithm.
	 * @return the algorithm category as a string
	 */
	@Override
	public String getAlgorithmCategory() {
		return "SEQUENTIAL PATTERN MINING";
	}

	/**
	 * Get the URL of the online documentation of this algorithm.
	 * @return the URL as a string
	 */
	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/GSP.php";
	}

	/**
	 * Run the algorithm with the given parameters, input file and output file.
	 * @param parameters an array of parameters provided by the user
	 * @param inputFile the path to the input file
	 * @param outputFile the path to the output file
	 * @throws IOException if an error occurs while reading/writing files
	 */
	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		// get the minimum support threshold
		double minSupport = getParamAsDouble(parameters[0]);

		AlgoGSP algo = new AlgoGSP(minSupport);

		// get the optional minimum pattern length parameter
		if (parameters.length >=2 && "".equals(parameters[1]) == false) {
			algo.setMinimumPatternLength(getParamAsInteger(parameters[1]));
		}
		// get the optional maximum pattern length parameter
		if (parameters.length >=3 && "".equals(parameters[2]) == false) {
			algo.setMaximumPatternLength(getParamAsInteger(parameters[2]));
		}

		// get the optional parameter to show sequence identifiers in output
		boolean outputSeqIdentifiers = false;
		if (parameters.length >=4 && "".equals(parameters[3]) == false) {
			outputSeqIdentifiers = getParamAsBoolean(parameters[3]);
		}

		// load the sequence database
		ca.pfv.spmf.algorithms.sequentialpatterns.gsp_AGP_fast.SequenceDatabase sd = new ca.pfv.spmf.algorithms.sequentialpatterns.gsp_AGP_fast.SequenceDatabase();
		sd.loadFile(inputFile, minSupport);

		// run the algorithm
		algo.runAlgorithm(sd, true, false, outputFile,outputSeqIdentifiers);
		System.out.println(algo.printStatistics());
	}

	/**
	 * Get the description of the parameters accepted by this algorithm.
	 * @return an array of parameter descriptions
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
	 * Get the names of the authors of this implementation.
	 * @return the author names as a string
	 */
	@Override
	public String getImplementationAuthorNames() {
		return "Antonio Gomariz Penalver";
	}

	/**
	 * Get the types of input files accepted by this algorithm.
	 * @return an array of input file type descriptions
	 */
	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Sequence database", "Simple sequence database"};
	}

	/**
	 * Get the types of output files produced by this algorithm.
	 * @return an array of output file type descriptions
	 */
	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Sequential patterns",  "Frequent sequential patterns"};
	}

	/**
	 * Get the type of this algorithm.
	 * @return the algorithm type
	 */
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}