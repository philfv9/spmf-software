package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.echum.AlgoECHUM;

/**
 * This class describes the ECHUM algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoECHUM
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoECHUM extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoECHUM(){
	}

	@Override
	public String getName() {
		return "ECHUM";
	}

	@Override
	public String getAlgorithmCategory() {
		return "HIGH-UTILITY PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/ECHUM_correlation.php";
	}

	@Override	
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		int minutil = getParamAsInteger(parameters[0]);
		double minCorr = getParamAsDouble(parameters[1]);
		
		// Applying the algorithm
		AlgoECHUM algo = new AlgoECHUM();
		algo.runAlgorithm(minutil, minCorr, inputFile, outputFile, true, Integer.MAX_VALUE, true );
		algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[2];
		parameters[0] = new DescriptionOfParameter("Minimum utility", "(e.g. 30)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("Minimum correlation", "(e.g. 0.6)", Double.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Ramesh et al.";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Transaction database with utility values"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Correlated patterns", "High-utility patterns","High-utility itemsets","Correlated High-utility itemsets"};
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
