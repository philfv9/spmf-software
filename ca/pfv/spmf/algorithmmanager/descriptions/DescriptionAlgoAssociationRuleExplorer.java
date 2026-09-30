package ca.pfv.spmf.algorithmmanager.descriptions;

import java.io.IOException;
import java.text.ParseException;

import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
import ca.pfv.spmf.algorithmmanager.DescriptionOfParameter;
import ca.pfv.spmf.gui.viewers.association_rule_explorer.AssociationRuleExplorer;

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
 * This class describes the tool to explore association rules: each rule is
 * shown as a point in a scatter plot (X, Y, color and size of the points are
 * measures such as support, confidence and lift), linked to a sortable table
 * with data bars, per-measure range filters, quick filters (positive
 * correlations only, top-K rules), item search, and plain-language explanations
 * of the selected rules. Filtered rules can be exported to a new file.
 * 
 * @see AssociationRuleExplorer
 * @author Philippe Fournier-Viger
 */
public class DescriptionAlgoAssociationRuleExplorer extends DescriptionOfAlgorithm {

	/**
	 * Default constructor
	 */
	public DescriptionAlgoAssociationRuleExplorer() {
	}

	@Override
	public String getName() {
		return "Association_rule_explorer";
	}

	@Override
	public String getAlgorithmCategory() {
		return "TOOLS - DATA VIEWERS";
	}

	@Override
	public String getURLOfDocumentation() {
		return "http://www.philippe-fournier-viger.com/spmf/AssociationRuleExplorer.php";
	}

	@Override
	public void runAlgorithm(String[] parameters, String inputFile, String outputFile)
			throws IOException, ParseException {
		AssociationRuleExplorer viewer = new AssociationRuleExplorer(inputFile);
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
		return new String[]{"Patterns", "Association rules"};
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