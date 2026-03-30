package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.File;
import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.graph_mining.tseqminer.AlgoTSeqMiner;

/**
 * This class describes the TSEQMINER algorithm parameters. It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoTSEQMINER
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoTSeqMiner extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoTSeqMiner(){
	}

	@Override
	public String getName() {
		return "TSEQMINER";
	}

	@Override
	public String getAlgorithmCategory() {
		return "GRAPH PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/TSEQMINER.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException, ClassNotFoundException {

		// The input directory containing a dynamic attributed graph
		String inputDirectory = getParamAsString(parameters[0]) + File.separator;
		
		//PARAMETER 1: discretization threshold (float)
		// If the value is 2, it means when (next_value - cur_value) >= 2,
		// the trend is '+' and when (next_value - cur_value) <= -2, the trend is '-'. Otherwise the trend is '0'.
        float discretizationThreshold = getParamAsFloat(parameters[1]);
        
        // PARAMETER 2: A frequent sequence should satisfy that the frequency 
        // of the first item in sequence >= minInitSup.
        float minInitSup = getParamAsFloat(parameters[2]);
        
        // PARAMETER 3: A frequent sequence should satisfy that the number of 
        // tail point of the sequence >= minTailSup.
        int minTailSup = getParamAsInteger(parameters[3]);
        
        // PARAMETER 4: A signficant sequence should satisfy that the significance 
        // between any two items in sequence is >= minSig.
        float minSig = getParamAsFloat(parameters[4]);
        
        // PARAMETER 5: the number of considered attributes  (int)
        int attributeCount = getParamAsInteger(parameters[5]);

		// Apply the algorithm 
		AlgoTSeqMiner algo = new AlgoTSeqMiner();
		algo.runAlgorithm(inputDirectory, outputFile, discretizationThreshold,
				minInitSup, minTailSup, minSig, attributeCount);
		
		// Print statistics about the algorithm execution
		algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[6];
		parameters[0] = new DescriptionOfParameter("Input directory", "(e.g. DB_TSEQMINER)", String.class, false);
		parameters[1] = new DescriptionOfParameter("Discretization threshold", "(e.g. 1)", Float.class, false);
		parameters[2] = new DescriptionOfParameter("minInitSup", "(e.g. 0.004)", Float.class, false);
		parameters[3] = new DescriptionOfParameter("minTailSup", "(e.g. 60)", Integer.class, false);
		parameters[4] = new DescriptionOfParameter("minSig", "(e.g. 8.0)", Float.class, false);
		parameters[5] = new DescriptionOfParameter("Attribute count", "(e.g. 43)", Integer.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Chao Cheng and Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Dynamic Attributed Graph"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Trend patterns", "Significant Trend Sequences"};
	}
	
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
	
}
