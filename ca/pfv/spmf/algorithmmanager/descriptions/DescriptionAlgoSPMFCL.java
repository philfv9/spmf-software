package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.AlgoSPM_FC_L;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.SequenceDatabase;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.creators.AbstractionCreator;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_l.items.creators.AbstractionCreator_Qualitative;

/**
 * This class describes the SPM-FC-L algorithm parameters. It is designed to be
 * used by the graphical and command line interface.
 * 
 * @see AlgoSPM_FC_L
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoSPMFCL extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoSPMFCL() {
	}

	@Override
	public String getName() {
		return "SPM_FC_L";
	}

	@Override
	public String getAlgorithmCategory() {
		return "SEQUENTIAL PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/mooc_SPM_FC_L.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		double minimumSupport = getParamAsDouble(parameters[0]);

		double alpha = getParamAsDouble(parameters[1]);
		double beta = getParamAsDouble(parameters[2]);
		double gamma = getParamAsDouble(parameters[3]);
		

		double mingap = getParamAsDouble(parameters[4]);
		double maxgap = getParamAsDouble(parameters[5]);
		double windowSize = getParamAsDouble(parameters[6]);

		boolean outputSeqIdentifiers = false;
		if (parameters.length >= 8 && "".equals(parameters[7]) == false) {
			outputSeqIdentifiers = getParamAsBoolean(parameters[7]);
		}

//		// Parameters of the algorithm
//		support = 0.08;
//		alpha = 0.5 / 3.0;
//		beta = 1.5 / 3.0;
//		gamma = 1.0 / 3.0;
//		double minimumSupport = (double) 0.04;
//		double mingap = 0;
//		double maxgap = Integer.MAX_VALUE;
//		double windowSize = 0;
		boolean keepPatterns = true;
		// boolean verbose = false;
		boolean verbose = true;

		// Read the database
		AbstractionCreator abstractionCreator = AbstractionCreator_Qualitative.getInstance();
		SequenceDatabase sequenceDatabase = new SequenceDatabase(abstractionCreator);
		
		// datafile:mooc.txt (Containing timestamp data format)
		sequenceDatabase.loadFile(inputFile, minimumSupport, alpha, beta, gamma);

		// Run the algorithm
		AlgoSPM_FC_L algorithm = new AlgoSPM_FC_L(minimumSupport, mingap, maxgap, windowSize, abstractionCreator);
		algorithm.runAlgorithm(sequenceDatabase, keepPatterns, verbose, outputFile, outputSeqIdentifiers,
				alpha, beta, gamma);
		System.out.println(algorithm.printStatistics());
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[8];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.08 or 8%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Alpha", "(e.g. 0.1666)", Double.class, false);
		parameters[2] = new DescriptionOfParameter("Beta", "(e.g. 0.5)", Double.class, false);
		parameters[3] = new DescriptionOfParameter("Gamma", "(e.g. 0.33)", Double.class, false);
		parameters[4] = new DescriptionOfParameter("Mingap", "(e.g. 0)", Double.class, false);
		parameters[5] = new DescriptionOfParameter("Maxgap", "(e.g. 12345678)", Double.class, false);
		parameters[6] = new DescriptionOfParameter("Winsize", "(e.g. 0)", Double.class, false);
		parameters[7] = new DescriptionOfParameter("Show sequence ids?", "(default: false)", Boolean.class, true);
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Wei Song et al.";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances","Sequence database", "Sequence database with timestamps"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[] { "Patterns", "Sequential patterns", "Frequent sequential patterns" };
	}
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_MINING;
	}
}
