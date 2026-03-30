package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.episodes.emma.AlgoAFEM;

/**
 * This class describes the AFEM algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoAFEM
 * @author Philippe Fournier-Viger, Saqib Nawaz, Yang Peng
 */
public class DescriptionAlgoAFEM extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoAFEM(){
	}

	@Override
	public String getName() {
		return "AFEM";
	}

	@Override
	public String getAlgorithmCategory() {
		return "EPISODE MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/AFEM_temporal.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		int minSup = getParamAsInteger(parameters[0]);
		int maxWindow = getParamAsInteger(parameters[1]);  
		boolean selftIncrement  = getParamAsBoolean(parameters[2]);  

        // apply the algorithm
        AlgoAFEM algo = new AlgoAFEM();
        algo.runAlgorithm(inputFile, outputFile,minSup,maxWindow,selftIncrement);
        algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[3];
		parameters[0] = new DescriptionOfParameter("Minimum support", "(e.g. 2)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("Max. Time duration", "(e.g. 2)", Integer.class, false);
		parameters[2] = new DescriptionOfParameter("Has no timestamps?", "(default: false)", Boolean.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger, Yang Peng";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Transaction database with timestamps"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns",  "Episodes", "Frequent episodes"};
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
