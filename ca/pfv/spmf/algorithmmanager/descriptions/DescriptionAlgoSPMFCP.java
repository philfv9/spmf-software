package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.AlgoSPM_FC_P;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.SequenceDatabase;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.creators.AbstractionCreator;
import ca.pfv.spmf.algorithms.sequentialpatterns.spm_fc_p.items.creators.AbstractionCreator_Qualitative;

/**
 * This class describes the SPM-FC-P algorithm parameters. It is designed to be
 * used by the graphical and command line interface.
 * 
 * @see AlgoSPM_FC_P
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoSPMFCP extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoSPMFCP() {
	}

	@Override
	public String getName() {
		return "SPM_FC_P";
	}

	@Override
	public String getAlgorithmCategory() {
		return "SEQUENTIAL PATTERN MINING";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/SPM_FC_P_mooc.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		double minSupport = getParamAsDouble(parameters[0]);

		double alpha = getParamAsDouble(parameters[1]);
		double beta = getParamAsDouble(parameters[2]);
		double gamma = getParamAsDouble(parameters[3]);

		boolean outputSeqIdentifiers = false;
		if (parameters.length >= 5 && "".equals(parameters[4]) == false) {
			outputSeqIdentifiers = getParamAsBoolean(parameters[4]);
		}

//		// Parameters of the algorithm
//		support = 0.08;
//		alpha = 0.5 / 3.0;
//		beta = 1.5 / 3.0;
//		gamma = 1.0 / 3.0;
		boolean keepPatterns = true;
		// boolean verbose = false;
		boolean verbose = true;

		// Read the database
		AbstractionCreator abstractionCreator = AbstractionCreator_Qualitative.getInstance();
		SequenceDatabase sequenceDatabase = new SequenceDatabase(abstractionCreator);
		sequenceDatabase.loadFile(inputFile, minSupport, alpha, beta, gamma);

		// Run the algorithm
		AlgoSPM_FC_P algorithm = new AlgoSPM_FC_P(minSupport, abstractionCreator);
		algorithm.runAlgorithm(sequenceDatabase, keepPatterns, verbose, outputFile, outputSeqIdentifiers,
				minSupport, alpha, beta, gamma);
		System.out.println(algorithm.printsimpleStatistics());
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {

		DescriptionOfParameter[] parameters = new DescriptionOfParameter[5];
		parameters[0] = new DescriptionOfParameter("Minsup (%)", "(e.g. 0.08 or 8%)", Double.class, false);
		parameters[1] = new DescriptionOfParameter("Alpha", "(e.g. 0.1666)", Double.class, false);
		parameters[2] = new DescriptionOfParameter("Beta", "(e.g. 0.5)", Double.class, false);
		parameters[3] = new DescriptionOfParameter("Gamma", "(e.g. 0.33)", Double.class, false);
		parameters[4] = new DescriptionOfParameter("Show sequence ids?", "(default: false)", Boolean.class, true);
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
