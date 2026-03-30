package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.nosep.AlgoNOSEP;

/**
 * This class describes the NOSEP algorithm parameters. It is designed to be used by the graphical and command line interface.
 * 
 * @see AlgoNOSEP
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoNOSEP extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoNOSEP(){
	}

	@Override
	public String getName() {
		return "NOSEP";
	}

	@Override
	public String getAlgorithmCategory() {
		return "SEQUENTIAL PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/NOSEP.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {


		// Get the parameter "minsup"
		int minlen = getParamAsInteger(parameters[0]);
		int maxlen = getParamAsInteger(parameters[1]);
		int mingap = getParamAsInteger(parameters[2]);
		int maxgap = getParamAsInteger(parameters[3]);
		int minsup = getParamAsInteger(parameters[4]);
		

        AlgoNOSEP algorithm = new AlgoNOSEP();
        algorithm.runAlgorithm(inputFile,outputFile,minlen, maxlen,mingap,maxgap,minsup);
        algorithm.printStats();
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[5];
		parameters[0] = new DescriptionOfParameter("Min. length", "(e.g. 1)", Integer.class, false);
		parameters[1] = new DescriptionOfParameter("Max. length", "(e.g. 20)", Integer.class, false);
		parameters[2] = new DescriptionOfParameter("Min. gap", "(e.g. 0)", Integer.class, false);
		parameters[3] = new DescriptionOfParameter("Max. gap", "(e.g. 2)", Integer.class, false);
		parameters[4] = new DescriptionOfParameter("Min. support", "(e.g. 3)", Integer.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Youxi Wu et al.";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Sequence", "Sequence database", "Simple sequence database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Patterns", "Sequential patterns", "Frequent sequential patterns"};
	}
//
//	@Override
//	String[] getSpecialInputFileTypes() {
//		return null; //new String[]{"ARFF"};
//	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
