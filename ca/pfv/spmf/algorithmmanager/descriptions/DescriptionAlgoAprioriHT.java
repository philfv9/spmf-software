package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.aprioriHT.AlgoAprioriHT;

/**
 * This class describes the Apriori with Hash-tree algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoAprioriHT
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoAprioriHT extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoAprioriHT(){
	}

	@Override
	public String getName() {
		return "Apriori_with_hash_tree";
	}

	@Override
	public String getAlgorithmCategory() {
		return "FREQUENT ITEMSET MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Apriori.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		double minsup = getParamAsDouble(parameters[0]);
		int branch_count = getParamAsInteger(parameters[1]);

		// Applying the Apriori algorithm, optimized version
		AlgoAprioriHT algorithm = new AlgoAprioriHT();
		
		if (parameters.length >=3 && "".equals(parameters[2]) == false) {
			algorithm.setMaximumPatternLength(getParamAsInteger(parameters[2]));
		}
		
		algorithm.runAlgorithm(minsup, inputFile, outputFile, branch_count);
		algorithm.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[3];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.4 or 40%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Hash-tree branch count", "(e.g. 30 branch)", Integer.class, false);
		parameters[2] = new DescriptionOfParameter("Max pattern length", "(e.g. 2 items)", Integer.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
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
