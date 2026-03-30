package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.File;
import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.feacp.AlgoFEACP;

/**
 * This class describes the FEACP algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoFEACP
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoFEACP extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoFEACP(){
	}

	@Override
	public String getName() {
		return "FEACP";
	}

	@Override
	public String getAlgorithmCategory() {
		return "HIGH-UTILITY PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/cross_FEACP.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		int minutil = getParamAsInteger(parameters[0]);
		
		// Taxonomy file
		String taxonomyFilename = parameters[1];

		File file = new File(inputFile);
		String taxonomyPath;
		if (file.getParent() == null) {
			taxonomyPath = taxonomyFilename;
		} else {
			taxonomyPath = file.getParent() + File.separator + taxonomyFilename;
		}
		
		// Applying the algorithm
		AlgoFEACP algo = new AlgoFEACP();
		algo.runAlgorithm(minutil, inputFile, outputFile, taxonomyPath, Integer.MAX_VALUE);
		algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("minutil", "(e.g. 60)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("taxonomy file", "(e.g. taxonomy_CLHMiner.txt)", String.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Bay Vo et al.";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Transaction database with utility values", "Transaction database with utility values and taxonomy"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns",  "High-utility patterns","High-utility itemsets", "Cross-Level High-utility itemsets"};
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
