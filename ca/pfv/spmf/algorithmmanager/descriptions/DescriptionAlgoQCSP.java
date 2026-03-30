package ca.pfv.spmf.algorithmmanager.descriptions;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.qcsp.AlgoQCSP;

/**
 * This class describes the QCSP algorithm parameters. 
 * It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoQCSP
 * @author Len Feremans
 */
public class DescriptionAlgoQCSP extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoQCSP(){
	}

	@Override
	public String getName() {
		return "QCSP";
	}

	@Override
	public String getAlgorithmCategory() {
		return "SEQUENTIAL PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "https://bitbucket.org/len_feremans/qcsp_public";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws Exception {

		int minsup = getParamAsInteger(parameters[0]);
		double alpha = getParamAsDouble(parameters[1]);
		int maxsize = getParamAsInteger(parameters[2]);
		int k = getParamAsInteger(parameters[3]);
		String labelFile = null;
		if(parameters.length > 4) {
			labelFile = parameters[4];
			if(labelFile.trim().equals("")) {
				labelFile = null;
			}
		}
		
		//--------------- Applying the  algorithm  ---------//
		AlgoQCSP algorithm = new AlgoQCSP();
		algorithm.setLabelsFile(labelFile);
		algorithm.runAlgorithm(inputFile, outputFile, minsup, alpha, maxsize, k);
		// Print statistics
		algorithm.printStatistics();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[5];
		parameters[0] = new DescriptionOfParameter("Minsup ", "Frequency threshold on single item", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("Alpha ", "Cohesion threshold, e.g. 2 times pattern size", Double.class, false);
		parameters[2] = new DescriptionOfParameter("Max pattern length", "", Integer.class, false);
		parameters[3] = new DescriptionOfParameter("Top-k patterns", "", Integer.class, false);
		parameters[4] = new DescriptionOfParameter("Label file name ", "(e.g. test_goKrimp.lab)", String.class, true);//optional	
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Feremans et al.";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Sequence database", "Simple sequence database", "Single sequence"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Sequential patterns", "Top-k sequential patterns with quantile-based cohesion"};
	}
	
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
	
}
