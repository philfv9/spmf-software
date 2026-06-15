package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.aprioriTID_fast.AlgoAprioriTIDrare_FAST;

/**
 * This class describes the AprioriRare algorithm (TID version) parameters. 
 * It is designed to be used by the graphical and command line interface. This 
 * keeps the transaction identifiers of patterns in memory and
 * is based on AprioriTID instead of Apriori.
 * 
 * @see AprioriClose
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoAprioriRareTID extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoAprioriRareTID(){
	}

	@Override
	public String getName() {
		return "AprioriRare_TID";
	}

	@Override
	public String getAlgorithmCategory() {
		return "FREQUENT ITEMSET MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/AprioriRare.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		double minsup = getParamAsDouble(parameters[0]);

		AlgoAprioriTIDrare_FAST algo = new AlgoAprioriTIDrare_FAST();
		
		if (parameters.length >=2 && "".equals(parameters[1]) == false) {
			algo.setShowTransactionIdentifiers(getParamAsBoolean(parameters[1]));
		}
		
		algo.runAlgorithm(inputFile, outputFile, minsup);
		algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.6 or 60%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Show transaction ids?", "(default: false)", Boolean.class, true);
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
		return new String[]{"Patterns", "Rare patterns", "Rare itemsets", "Minimal rare itemsets"};
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
