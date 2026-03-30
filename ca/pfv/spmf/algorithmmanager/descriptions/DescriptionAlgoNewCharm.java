package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.charm.AlgoDCharm_Bitset;
import ca.pfv.spmf.algorithms.frequentpatterns.newcharm.AlgoNewCharm_Bitset;
import ca.pfv.spmf.input.transaction_database_list_integers.TransactionDatabase;

/**
 * This class describes the NewCharm algorithm parameters. It is designed to
 * be used by the graphical and command line interface.
 * 
 * @see AlgoDCharm_Bitset
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoNewCharm extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoNewCharm() {
	}

	@Override
	public String getName() {
		return "NEWCharm";
	}

	@Override
	public String getAlgorithmCategory() {
		return "FREQUENT ITEMSET MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/NEWCharm.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		double minsup = getParamAsDouble(parameters[0]);
		AlgoNewCharm_Bitset algo = new AlgoNewCharm_Bitset();

		TransactionDatabase database = new TransactionDatabase();
		try {
			database.loadFile(inputFile);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		algo.runAlgorithm(outputFile, database, minsup, true);
		algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[1];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.4 or 40%)", Double.class, false);
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
		return new String[] { "Patterns", "Frequent patterns", "Frequent closed itemsets" };
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
