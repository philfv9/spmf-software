package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.episodes.huespan.AlgoHUESpan;

/**
 * This class describes the HUE-SPAN algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoHUESpan
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoHUESPAN extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoHUESPAN(){
	}

	@Override
	public String getName() {
		return "HUE-SPAN";
	}

	@Override
	public String getAlgorithmCategory() {
		return "EPISODE MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/HUE_SPAN.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		double minutil = getParamAsDouble(parameters[0]);
		int maxTimeDuration = getParamAsInteger(parameters[1]);  
		boolean outputSingleEvents  = getParamAsBoolean(parameters[2]);  
		
		boolean useTraditionalUtility = false;
		if (parameters.length >=4 && "".equals(parameters[3]) == false) {
			useTraditionalUtility = getParamAsBoolean(parameters[3]);
		}
		
		// Applying the algorithm
        /** Run the algorithm */
        AlgoHUESpan algo = new AlgoHUESpan();
        algo.runAlgorithm(inputFile,outputFile,minutil,maxTimeDuration, useTraditionalUtility == false, true, outputSingleEvents,true,true);
        algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[4];
		parameters[0] = new DescriptionOfParameter("Minimum utility", "(e.g. 45%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Max. Time duration", "(e.g. 2)", Integer.class, false);
		parameters[2] = new DescriptionOfParameter("Output single events", "(e.g. true)", Boolean.class, false);
		parameters[3] = new DescriptionOfParameter("Use traditional utility?", "(default: false)", Boolean.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Yang Peng et al.";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Transaction database with utility values"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns",  "Episodes", "High-utility patterns","High-Utility episodes"};
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
