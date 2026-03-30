package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.associationrules.hgb.AlgoFHIM_and_HUCI;

/**
 * This class describes the HUCI-Miner_generators algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoFHIM_and_HUCI
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoHUCI_Miner_Generators extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoHUCI_Miner_Generators(){
	}

	@Override
	public String getName() {
		return "HUCI_Miner_Generators";
	}

	@Override
	public String getAlgorithmCategory() {
		return "HIGH-UTILITY PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/HUCI_Miner_all_them.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		int minutil = getParamAsInteger(parameters[0]);
		// Applying the algorithm
		AlgoFHIM_and_HUCI algo = new AlgoFHIM_and_HUCI();
		algo.setDontOutputClosedItemsets(true);
		algo.runAlgorithmHUCIMiner(inputFile, outputFile, minutil);
		algo.printStats();
	} 

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[1];
		parameters[0] = new DescriptionOfParameter("Minimum utility", "(e.g. 30)", Integer.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Jayakrushna Sahoo";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Transaction database with utility values"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns",  "High-utility patterns","Generator itemsets","High-utility itemsets","Generator high-utility itemsets"};
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
	
}
