package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.mapd_owsp.AlgoMAPD;

/**
 * This class describes the MAPD algorithm parameters. It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoMAPD
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoMAPD extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoMAPD(){
	}

	@Override
	public String getName() {
		return "MAPD";
	}

	@Override
	public String getAlgorithmCategory() {
		return "EPISODE MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/MAPD_string.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		// Get the parameter "minsup"
		double minimumThreshold = getParamAsDouble(parameters[0]);
		int maxPatternLength = getParamAsInteger(parameters[1]);
		int minGap = getParamAsInteger(parameters[2]);
		int maxGap = getParamAsInteger(parameters[3]);
		char[] charset = parameters[4].toCharArray();
		
        AlgoMAPD algorithm = new AlgoMAPD();
        algorithm.runAlgorithm(inputFile,outputFile, minGap, maxGap, maxPatternLength, minimumThreshold, charset);
        algorithm.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[5];
		parameters[0] = new DescriptionOfParameter("Min. threshold", "(e.g. 0.2)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Max. pattern length", "(e.g. 3)", Integer.class, false);
		parameters[2] = new DescriptionOfParameter("Min. gap", "(e.g. 0)", Integer.class, false);
		parameters[3] = new DescriptionOfParameter("Max. gap", "(e.g. 3)", Integer.class, false);
		parameters[4] = new DescriptionOfParameter("Charset", "(e.g. actg)", String.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Youxi Wu et al.";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[] {"Sequence", "String sequence"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[] { "Patterns", "Sequential patterns", "Frequent sequential patterns", "Sequential patterns with periodic wilcard gaps" };
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
