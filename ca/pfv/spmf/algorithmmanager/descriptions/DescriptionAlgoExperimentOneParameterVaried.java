package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.experiments.oneparametervaried.ExperimenterForParameterChange;
import ca.pfv.spmf.gui.experiments.ExperimenterWindow;

/**
 * This class describes the algorithm to compare algorithms when
 * one parameter is varied
 * 
 * @see ExperimenterForParameterChange
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoExperimentOneParameterVaried extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoExperimentOneParameterVaried(){
	}

	@Override
	public String getName() {
		return "Performance_experiment_one_parameter_varied";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - RUN EXPERIMENTS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/ExperimenterPerformance.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		ExperimenterWindow experimenter = new ExperimenterWindow();
		experimenter.setVisible(true);
		experimenter.setTitle("Run an experiment to test the performance of algorithm(s) when a parameter is varied");
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
        
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[0];
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return null;
	}

	@Override
	public String[] getOutputFileTypes() {
		return null;
	}

	
	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.EXPERIMENT_TOOL;
	}
	
}
