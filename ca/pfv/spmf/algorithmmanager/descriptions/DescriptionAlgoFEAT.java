package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.prefixspan.AlgoFEAT2;

/**
 * This class describes the PrefixSpan algorithm parameters. It is designed to
 * be used by the graphical and command line interface.
 * 
 * @see AlgoFEAT2
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoFEAT extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoFEAT() {
	}

	@Override
	public String getName() {
		return "FEAT";
	}

	@Override
	public String getAlgorithmCategory() {
		return "SEQUENTIAL PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/FEAT.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		AlgoFEAT2 algo = new AlgoFEAT2();
		if (parameters.length >= 2 && "".equals(parameters[1]) == false) {
			algo.setMaximumPatternLength(getParamAsInteger(parameters[1]));
		}

		double minsup = getParamAsDouble(parameters[0]); 

		boolean outputSeqIdentifiers = false;
		if (parameters.length >= 2 && "".equals(parameters[2]) == false) {
			outputSeqIdentifiers = getParamAsBoolean(parameters[2]);
		}

		algo.setShowSequenceIdentifiers(outputSeqIdentifiers);
		algo.runAlgorithm(inputFile, minsup, outputFile);
		algo.printStatistics();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[3];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.4 or 40%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Max pattern length", "", Integer.class, true);
		parameters[2] = new DescriptionOfParameter("Show sequence ids?", "(default: false)", Boolean.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[] { "Database of instances", "Sequence database", "Simple sequence database" };
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[] { "Patterns", "Sequential patterns", "Frequent sequential patterns",
				"Frequent sequential generators" };
	}

//
//	@Override
//	String[] getSpecialInputFileTypes() {
//		return null; //new String[]{"ARFF"};
//	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
