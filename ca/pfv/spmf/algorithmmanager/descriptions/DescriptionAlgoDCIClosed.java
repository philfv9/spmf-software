package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.dci_closed_fast.AlgoDCI_Closed_FAST;
import ca.pfv.spmf.algorithms.frequentpatterns.dci_closed_simple.AlgoDCI_Closed_Optimized;

/**
 * This class describes the DCI_Closed algorithm parameters. It is designed to
 * be used by the graphical and command line interface.
 * 
 * @see AlgoDCI_Closed_Optimized
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoDCIClosed extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoDCIClosed() {
	}

	@Override
	public String getName() {
		return "DCI_Closed";
	}

	@Override
	public String getAlgorithmCategory() {
		return "FREQUENT ITEMSET MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/DCI_Closed.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		double minsup = getParamAsDouble(parameters[0]);
		AlgoDCI_Closed_FAST algorithm = new AlgoDCI_Closed_FAST();

		if (parameters.length >= 2 && "".equals(parameters[1]) == false) {
			algorithm.setShowTransactionIdentifiers(getParamAsBoolean(parameters[1]));
		}
		algorithm.runAlgorithm(inputFile, outputFile, minsup);
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.4)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Show transaction ids?", "(default: false)", Boolean.class, true);
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
