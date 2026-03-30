package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.File;
import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.frequentpatterns.fhuqiminer.EnumCombination;
import ca.pfv.spmf.algorithms.frequentpatterns.vhuqi.AlgoVHUQI;

/**
 * This class describes the VHUQI algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoVHUQI
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoVHUQI extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoVHUQI(){
	}

	@Override
	public String getName() {
		return "VHUQI";
	}

	@Override
	public String getAlgorithmCategory() {
		return "HIGH-UTILITY PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/VHUQI.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		String inputProfitFile = getParamAsString(parameters[0]);
		
		File file = new File(inputFile);
		if (file.getParent() != null) {
			inputProfitFile = file.getParent() + File.separator + inputProfitFile;
		}
		
		float minUtility = getParamAsFloat(parameters[1]);
		
		//Related quantitative coefficient
		int relativeCoefficient = getParamAsInteger(parameters[2]);
		
		// Combination method
		EnumCombination method = EnumCombination.valueOf(getParamAsString(parameters[3]));
		
		AlgoVHUQI algo = new AlgoVHUQI();
		algo.runAlgorithm(inputFile, inputProfitFile, minUtility, relativeCoefficient, method, outputFile);
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[4];
		parameters[0] = new DescriptionOfParameter("Profit table", "(e.g. dbHUQI.txt)", String.class, false);
		parameters[1] = new DescriptionOfParameter("Minimum utility (%)", "(e.g. 0.2)", Float.class, false);
		parameters[2] = new DescriptionOfParameter("Relative coefficient", "(e.g. 3)", Integer.class, false);
		parameters[3] = new DescriptionOfParameter("Method", "(e.g. COMBINEALL)", String.class, false);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Wu, Cheng-Wei. et al., Improvements by Nouioua et al.";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Transaction database", "Transaction database with utility values (HUQI)"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns",  "High-utility patterns","Quantitative high utility itemsets"};
	}
	
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
	
}
