package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.tools.dataset_generator.ItemWeightsGenerator;

/* This file is copyright (c) 2008-2024 Philippe Fournier-Viger
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
 * You should have received a copy of the GNU General Public License along with
 * SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * This class describes the algorithm to generate an item weights file from a
 * transaction database in SPMF format. It is designed to be used by the
 * graphical and command line interface.
 *
 * @see ItemWeightsGenerator
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoGenerateItemWeights extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoGenerateItemWeights() {
	}

	@Override
	public String getName() {
		return "Generate_item_weights";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA GENERATORS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Generating_item_weights.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException {
		// We call the item weights generator with the input transaction database
		// and the output weight file path
		ItemWeightsGenerator.generateWeights(inputFile, outputFile);
		System.out.println("Item weights file generated successfully.");
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
		// This tool takes no additional parameters beyond the input and output files
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[0];
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Database of instances", "Transaction database", "Simple transaction database"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return new String[]{"Weight file", "Item weight file"};
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_GENERATOR;
	}

}