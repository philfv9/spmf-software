package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;
import java.text.ParseException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.viewers.sequential_pattern_explorer.SequentialPatternExplorer;

/*
 * Copyright (c) 2025 Philippe Fournier-Viger
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
 * This class describes the tool to explore sequential patterns by item: the
 * user selects an item and views all patterns that start with, end with or
 * contain that item, in a sortable table showing all measures found in the
 * pattern file (e.g. support, confidence).
 * 
 * @see SequentialPatternExplorer
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoSequentialPatternExplorer extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoSequentialPatternExplorer() {
	}

	@Override
	public String getName() {
		return "Sequential_pattern_explorer";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA VIEWERS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/SequentialPatternExplorer.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile)
			throws IOException, ParseException {
		SequentialPatternExplorer viewer = new SequentialPatternExplorer(inputFile, false);
		viewer.setVisible(true);
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
		return new String[]{"Patterns", "Sequential patterns"};
	}

	@Override
	public String[] getOutputFileTypes() {
		return null;
	}

	@Override
	public AlgorithmType getAlgorithmType() {
		return AlgorithmType.DATA_VIEWER;
	}
}