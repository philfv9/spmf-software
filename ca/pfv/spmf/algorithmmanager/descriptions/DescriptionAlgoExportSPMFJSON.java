package ca.pfv.spmf.algorithmmanager.descriptions;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.algorithmmanager.exportlist.AlgoExportSPMFJSON;

/* This file is copyright (c) 2008-2025 Philippe Fournier-Viger
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
 * This class describes the algorithm that exports the list of algorithms, their
 * parameters, names, categories, and other metadata into a JSON file. It is
 * designed to be used by the graphical and command line interface.
 *
 * @see AlgoExportSPMFJSON
 * @author Philippe...
 */
public class DescriptionAlgoExportSPMFJSON extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoExportSPMFJSON() {
	}

	@Override
	public String getName() {
		return "Export_algorithms_list_to_JSON";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - SPMF";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Export_to_JSON.php"; // placeholder
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws Exception {

		// no inputFile needed; outputFile is mandatory
		String jsonOutputPath = outputFile;

		AlgoExportSPMFJSON exporter = new AlgoExportSPMFJSON();
		exporter.runAlgorithm(jsonOutputPath);

		System.out.println("JSON export completed.");
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
		return new String[] { "SPMF algorithm list (JSON)" };
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.OTHER_TOOL;
	}
}
