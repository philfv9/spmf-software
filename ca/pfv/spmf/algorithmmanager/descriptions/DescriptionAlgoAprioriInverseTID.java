package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.aprioriTID_inverse.AlgoAprioriTIDInverse;

/**
 * This class describes the AprioriTIDInverse algorithm (TID version) parameters. 
 * It is designed to be used by the graphical and command line interface. This 
 * keeps the transaction identifiers of patterns in memory and
 * is based on AprioriTID instead of Apriori.
 * 
 * @see AlgoAprioriTIDInverse
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoAprioriInverseTID extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoAprioriInverseTID(){
	}

	@Override
	public String getName() {
		return "AprioriInverse_TID";
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

		AlgoAprioriTIDInverse algo = new AlgoAprioriTIDInverse();
		
		if (parameters.length >=2 && "".equals(parameters[2]) == false) {
			algo.setShowTransactionIdentifiers(getParamAsBoolean(parameters[2]));
		}
		
		algo.runAlgorithm(inputFile, outputFile, minsup, maxsup);
		algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[3];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.1 or 10%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Maxsup (%)", "(e.g. 0.6 or 60%)", Double.class, false);
		parameters[2] = new DescriptionOfParameter("Show transaction ids?", "(default: false)", Boolean.class, true);
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
