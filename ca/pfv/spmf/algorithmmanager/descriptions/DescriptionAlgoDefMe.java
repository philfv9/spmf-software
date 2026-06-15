package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.defme_fast.AlgoDefMe_FAST;
import ca.pfv.spmf.input.transaction_database_list_integers.TransactionDatabase;

/**
 * This class describes the Apriori algorithm parameters. It is designed to be
 * used by the graphical and command line interface.
 * 
 * @see AlgoDefMe_FAST
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoDefMe extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoDefMe() {
	}

	@Override
	public String getName() {
		return "DefMe";
	}

	@Override
	public String getAlgorithmCategory() {
		return "FREQUENT ITEMSET MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/DefMe.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		double minsup = getParamAsDouble(parameters[0]);
		AlgoDefMe_FAST algorithm = new AlgoDefMe_FAST();

		if (parameters.length >= 2 && "".equals(parameters[1]) == false) {
			algorithm.setMaximumPatternLength(getParamAsInteger(parameters[1]));
		}

		algorithm.runAlgorithm(inputFile, outputFile, minsup);
		algorithm.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.4 or 40%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Max pattern length", "(e.g. 2 items)", Integer.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[] { "Database of instances", "Transaction database", "Simple transaction database" };
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[] { "Patterns", "Frequent patterns", "Generator patterns", "Frequent itemsets",
				"Frequent generator itemsets" };
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
