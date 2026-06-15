
package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;
import java.text.ParseException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.visual_pattern_viewer.PatternType;
import ca.pfv.spmf.gui.visual_pattern_viewer.VisualPatternViewer;

/*
 * Copyright (c) 2008-2025 Philippe Fournier-Viger
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
 * This class describes the algorithm to visualize frequent subgraphs.
 * It allows viewing frequent subgraphs mined by algorithms such as GSPAN,
 * TKG, and other graph mining algorithms in SPMF format.
 * 
 * @see VisualPatternViewer
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoViewerFrequentSubgraphs extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoViewerFrequentSubgraphs(){
	}

	@Override
	public String getName() {
		return "Visualize_frequent_subgraphs";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA VIEWERS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/Visualize_FrequentSubgraphs.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile) throws IOException, ParseException {
		// Create the visual pattern viewer for subgraphs
		VisualPatternViewer viewer = new VisualPatternViewer(false, inputFile, PatternType.SUBGRAPHS);
		viewer.setVisible(true);
	}

	@Override
	public DescriptionOfParameter[] getParametersDescription() {
		// No parameters needed for the viewer
		DescriptionOfParameter[] parameters = new DescriptionOfParameter[0];
		return parameters;
	}

	@Override
	public String getImplementationAuthorNames() {
		return "Philippe Fournier-Viger";
	}

	@Override
	public String[] getInputFileTypes() {
		return new String[]{"Patterns", "Subgraphs", "Frequent subgraphs"};
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