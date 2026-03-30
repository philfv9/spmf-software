package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.lppm.AlgoLPPMBreadth2;

/**
 * This class describes the LPPMBreadth algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoLPPMBreadth2
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoLPPMBreadth extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoLPPMBreadth(){
	}

	@Override
	public String getName() {
		return "LPPM_breadth";
	}

	@Override
	public String getAlgorithmCategory() {
		return "PERIODIC PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Local-periodic.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		// Read the parameters
		int maxPer = getParamAsInteger(parameters[0]);  
		int minDur = getParamAsInteger(parameters[1]); 
		int maxSoPer = getParamAsInteger(parameters[2]);  
		boolean noTimestamps = getParamAsBoolean(parameters[3]);  

		// Apply the algorithm
		AlgoLPPMBreadth2 algo = new AlgoLPPMBreadth2();
        algo.runAlgorithm(inputFile, outputFile,maxPer,minDur,maxSoPer,noTimestamps);
        algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[4];
		parameters[0] = new DescriptionOfParameter("Maximum periodicity", "(e.g. 3 transactions)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("Minimum duration", "(e.g. 7 transactions)", Integer.class, false);
		parameters[2] = new DescriptionOfParameter("Maximum Spillover", "(e.g. 2)", Integer.class, false);
		parameters[3] = new DescriptionOfParameter("Has no timestamps?", "(e.g. true)", Boolean.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Peng Yang and Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Simple transaction database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Frequent patterns", "Periodic patterns", "Periodic frequent patterns", "Local Periodic frequent itemsets"};
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
