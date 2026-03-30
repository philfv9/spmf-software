package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.associationrules.hgb.AlgoFHIM_and_HUCI;
import ca.pfv.spmf.algorithms.associationrules.hgb.AlgoHGBAll;
import ca.pfv.spmf.algorithms.associationrules.hgb.HUTable;

/**
 * This class describes the HGB_ALL algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoHGBAll
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoHGB_All extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoHGB_All(){
	}

	@Override
	public String getName() {
		return "HGB_All";
	}

	@Override
	public String getAlgorithmCategory() {
		return "HIGH-UTILITY PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/HGB_rules_non.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		int minutil = getParamAsInteger(parameters[0]);
		double minconf = getParamAsDouble(parameters[1]);
		
		// Step 1 : Applying the HUIMiner algorithm
		AlgoFHIM_and_HUCI huci = new AlgoFHIM_and_HUCI();
		huci.runAlgorithmHUCIMiner(inputFile, null, minutil);
		HUTable table = huci.getTableHU();
		huci.printStats();
		
		// Step 2: Applying the HGB algorithm to generate high utility association rules
		AlgoHGBAll algoHGB = new AlgoHGBAll();
		algoHGB.runAlgorithm(table, minconf, minutil);
		algoHGB.writeRulesToFile(outputFile);
		algoHGB.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("Minimum utility", "(e.g. 30)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("Minimum confidence", "(e.g. 0.5)", Double.class, false);
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
		return new String[]{"Patterns",  "High-utility patterns","High-utility itemsets"};
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
