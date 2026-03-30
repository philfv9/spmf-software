package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.SFUPMinerUemax.AlgoSFUPMinerUemax;

/**
 * This class describes the SFUPMinerUemax algorithm parameters. It is designed to be
 * used by the graphical and command line interface.
 * 
 * @see AlgoSFUPMinerUemax
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoSFUPMinerUemax extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoSFUPMinerUemax() {
	}

	@Override
	public String getName() {
		return "SFUPMinerUemax";
	}

	@Override
	public String getAlgorithmCategory() {
		return "HIGH-UTILITY PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/SFUPMinerUemax.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		// Create an instance of the algorithm
		AlgoSFUPMinerUemax up = new AlgoSFUPMinerUemax();
		up.runAlgorithm(inputFile, outputFile);
		// print statistics about the algorithm execution
		up.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[0];
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Jerry Chun-Wei Lin, Lu Yang, Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[] { "Database of instances", "Transaction database",
				"Transaction database with utility values skymine format" };
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[] { "Patterns", "Skyline patterns", "High-utility patterns",
				"Skyline Frequent High-utility itemsets" };
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
