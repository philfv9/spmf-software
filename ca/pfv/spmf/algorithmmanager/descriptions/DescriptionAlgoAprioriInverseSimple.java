package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.apriori_simple.AlgoAprioriInverse;

/**
 * This class describes the AlgoAprioriInverse algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoAprioriInverse
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoAprioriInverseSimple extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoAprioriInverseSimple(){
	}

	@Override
	public String getName() {
		return "AprioriInverse_simple";
	}

	@Override
	public String getAlgorithmCategory() {
		return "FREQUENT ITEMSET MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/AprioriInverse.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		double minsup = getParamAsDouble(parameters[0]);
		double maxsup = getParamAsDouble(parameters[1]);

		AlgoAprioriInverse apriori = new AlgoAprioriInverse();
		apriori.runAlgorithm(minsup, maxsup, inputFile, outputFile);
		apriori.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.1 or 10%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Maxsup (%)", "(e.g. 0.61 or 61%)", Double.class, false);
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
		return new String[]{"Patterns", "Rare patterns", "Rare itemsets", "Perfectly rare itemsets"};
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
