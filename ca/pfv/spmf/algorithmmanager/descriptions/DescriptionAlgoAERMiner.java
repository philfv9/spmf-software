package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.File;
import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.graph_mining.aerminer.AlgoAERMiner;

/**
 * This class describes the AERMiner algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoAERMiner
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoAERMiner extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoAERMiner(){
	}

	@Override
	public String getName() {
		return "AERMiner";
	}

	@Override
	public String getAlgorithmCategory() {
		return "GRAPH PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/AERMiner.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException, ClassNotFoundException {

		// The input directory containing a dynamic attributed graph
		String inputDirectory = getParamAsString(parameters[0]) + File.separator;
		
        // minimum support
		double minsup = getParamAsDouble(parameters[1]);
        
        // minimum confidence
        double minconf = getParamAsDouble(parameters[2]);
        
        // minimum lift
        double minlift = getParamAsDouble(parameters[3]);
                
		// Apply the algorithm 
		AlgoAERMiner algo = new AlgoAERMiner();
		algo.runAlgorithm(inputDirectory, outputFile, minsup, minconf, minlift);
		
		// Print statistics about the algorithm execution
		algo.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        //0.02 0.01 0.4
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[4];
		parameters[0] = new DescriptionOfParameter("Input directory", "(e.g. DBLP)", String.class, false);
		parameters[1] = new DescriptionOfParameter("Minsup", "(e.g. 0.02)", Double.class, false);
		parameters[2] = new DescriptionOfParameter("Minconf", "(e.g. 0.01)", Double.class, false);
		parameters[3] = new DescriptionOfParameter("Minlift", "(e.g. 0.4)", Double.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Ganghuan He and Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Dynamic Attributed Graph"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Trend patterns", "Attribute Evolution Rules"};
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
