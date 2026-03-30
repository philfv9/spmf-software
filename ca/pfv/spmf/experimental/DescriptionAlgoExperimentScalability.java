package ca.pfv.spmf.experimental;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.experiments.oneparametervaried.ExperimenterForParameterChange;
import ca.pfv.spmf.gui.experiments.ExperimenterScalabilityWindow;
/*
 * Copyright (c) 2008-2015 Philippe Fournier-Viger
 *
 * This file is part of the SPMF DATA MINING SOFTWARE
 * (http://www.philippe-fournier-viger.com/spmf).
 *
 * SPMF is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * This class describes the algorithm to compare algorithms when
 * one parameter is varied
 * 
 * @see ExperimenterForParameterChange
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoExperimentScalability extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoExperimentScalability(){
	}

	@Override
	public String getName() {
		return "Performance_experiment_scalability";
	}

	@Override
	public String getAlgorithmCategory() {
		return "EXPERIMENTS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/ExperimenterPerformance_Scalability.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {

		ExperimenterScalabilityWindow experimenter = new ExperimenterScalabilityWindow();
		experimenter.setVisible(true);
		experimenter.setTitle("Run an experiment to test the performance of algorithm(s) when dataset size is varied.");
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
