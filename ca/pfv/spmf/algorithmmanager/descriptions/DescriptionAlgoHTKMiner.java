package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.HTK_Miner.AlgoHTKMiner;

/**
 * This class describes the HTK_Miner algorithm parameters. 
 * HTK_Miner is an algorithm for mining the top-k high utility itemsets
 * from transaction databases with utility values.
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoHTKMiner
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoHTKMiner extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoHTKMiner(){
	}

	@Override
	public String getName() {
		return "HTK_Miner";
	}

	@Override
	public String getAlgorithmCategory() {
		return "FREQUENT ITEMSET MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/HTK_Miner.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		int k = getParamAsInteger(parameters[0]);
		String separator = " ";

		// Applying the HTK_Miner algorithm
		AlgoHTKMiner algorithm = new AlgoHTKMiner();
		
		if (parameters.length >= 2 && "".equals(parameters[1]) == false) {
			separator = parameters[1];
		}
		
		algorithm.runAlgorithm(inputFile, outputFile, k, separator);
		algorithm.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("k (top-k itemsets)", "(e.g. 100)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("Separator", "(e.g. space ' ')", String.class, true);
//		parameters[2] = new DescriptionOfParameter("Max pattern length", "(e.g. 5 items)", Integer.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Konstantinos Malliaridis and Stefanos Ougiaroglou";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Simple transaction database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Frequent patterns", "Frequent itemsets"};
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}