package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.sfui_uf.AlgoSFUI_UF;

/**
 * This class describes the SFUI-UF algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoSFUI_UF
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoSFUI_UF extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoSFUI_UF(){
	}

	@Override
	public String getName() {
		return "SFUI-UF";
	}

	@Override
	public String getAlgorithmCategory() {
		return "HIGH-UTILITY PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/the_SFUI_UF_algo.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {		
		// Create an instance of the algorithm
		AlgoSFUI_UF up = new AlgoSFUI_UF();
		up.runAlgorithm(inputFile, outputFile);
		// print statistics about the algorithm execution
		up.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[0];
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Wei Song et al.";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Transaction database with utility values"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns",  "Skyline patterns", "High-utility patterns", "Skyline High-utility itemsets"};
	}
	
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
	
}
