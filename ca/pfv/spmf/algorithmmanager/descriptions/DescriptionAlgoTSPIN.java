package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.sppgrowth.AlgoTSPIN;

/**
 * This class describes the TPIN algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoTSPIN
 * @author Ying Wang, Peng Yang, Philippe Fournier-Viger
 */
public class DescriptionAlgoTSPIN extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoTSPIN(){
	}

	@Override
	public String getName() {
		return "TSPIN";
	}

	@Override
	public String getAlgorithmCategory() {
		return "PERIODIC PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/TSPIN_periodic.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		// Read the parameters
		int maxPer = getParamAsInteger(parameters[0]);  
		int k = getParamAsInteger(parameters[1]); 
		int maxLA = getParamAsInteger(parameters[2]);  
		boolean noTimestamps = getParamAsBoolean(parameters[3]);  

        AlgoTSPIN algo = new AlgoTSPIN(maxPer,maxLA,k,noTimestamps);
        algo.runAlgorithm(inputFile,outputFile);
        algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[4];
		parameters[0] = new DescriptionOfParameter("Maximum periodicity", "(e.g. 5 transactions)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("k", "(e.g. 3)", Integer.class, false);
		parameters[2] = new DescriptionOfParameter("Maximum lability", "(e.g. 1)", Integer.class, false);
		parameters[3] = new DescriptionOfParameter("Has no timestamps?", "(e.g. false)", Boolean.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Ying Wang and Peng Yang and Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Simple transaction database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Frequent patterns", "Periodic patterns", "Periodic frequent patterns", "Top-k Stable Periodic frequent itemsets"};
	}
	
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
	
}
