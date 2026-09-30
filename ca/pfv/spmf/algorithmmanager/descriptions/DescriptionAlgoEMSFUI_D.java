package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.emsfui_d.AlgoEMSFUI_D;

/**
 * This class describes the EMSFUI_D algorithm parameters. It is designed to be
 * used by the graphical and command line interface.
 * 
 * @see AlgoEMSFUI_D
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoEMSFUI_D extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoEMSFUI_D() {
	}

	@Override
	public String getName() {
		return "EMSFUI_D";
	}

	@Override
	public String getAlgorithmCategory() {
		return "HIGH-UTILITY PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/EMSFUI_D_frequent.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		// Create an instance of the algorithm
		AlgoEMSFUI_D up = new AlgoEMSFUI_D();
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
		return "Xuan Liu et al.";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[] { "Database of instances", "Transaction database",
				"Transaction database with utility values" };
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
