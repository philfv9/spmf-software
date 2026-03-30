package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.mapd_owsp.AlgoOWSPMiner;

/**
 * This class describes the OWSD-Miner algorithm parameters. It is designed to
 * be used by the graphical and command line interface.
 * 
 * @see AlgoOWSPMiner
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoOWSPMiner extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoOWSPMiner() {
	}

	@Override
	public String getName() {
		return "OWSPMiner";
	}

	@Override
	public String getAlgorithmCategory() {
		return "EPISODE MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/OWSPMiner_string.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		// Get the parameter "minsup"
		int minsup = getParamAsInteger(parameters[0]);
		char[] gapstr = parameters[1].toCharArray();
		int numberOfCharactersToRead = getParamAsInteger(parameters[2]);

		AlgoOWSPMiner algorithm = new AlgoOWSPMiner();
		algorithm.runAlgorithm(inputFile, outputFile, gapstr, minsup, numberOfCharactersToRead);
		algorithm.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[3];
		parameters[0] = new DescriptionOfParameter("Minsup", "(e.g. 2)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("Weak character set", "(e.g. a)", String.class, false);
		parameters[2] = new DescriptionOfParameter("Char count to read", "(e.g. 20000)", Integer.class, false);
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
		return new String[] { "Patterns", "Sequential patterns", "Frequent sequential patterns", "Self-adaptive one-off weak-gap strong sequential patterns"};
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
