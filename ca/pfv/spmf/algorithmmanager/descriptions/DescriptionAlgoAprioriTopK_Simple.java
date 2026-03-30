package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.apriori_simple.AlgoAprioriTopK;

/**
 * This class describes the Apriori(top-k) algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoAprioriTopK
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoAprioriTopK_Simple extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoAprioriTopK_Simple(){
	}

	@Override
	public String getName() {
		return "Apriori(top-k)_simple";
	}

	@Override
	public String getAlgorithmCategory() {
		return "FREQUENT ITEMSET MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Apriori_top_k.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		int  k = getParamAsInteger(parameters[0]);

		// Applying the Apriori algorithm, optimized version
		ca.pfv.spmf.algorithms.frequentpatterns.apriori_simple.AlgoAprioriTopK algorithm = new ca.pfv.spmf.algorithms.frequentpatterns.apriori_simple.AlgoAprioriTopK();
		
		if (parameters.length >=2 && "".equals(parameters[1]) == false) {
			algorithm.setMaximumPatternLength(getParamAsInteger(parameters[1]));
		}
		
		algorithm.runAlgorithm(k, inputFile, outputFile);
		algorithm.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("k ", "(e.g. 4)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("Max pattern length", "(e.g. 2 items)", Integer.class, true);
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
