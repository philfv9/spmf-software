package ca.pfv.spmf.gui;

import ca.pfv.spmf.algorithmmanager.AlgorithmManager;
import ca.pfv.spmf.algorithmmanager.AlgorithmType;
import ca.pfv.spmf.algorithmmanager.DescriptionOfAlgorithm;
/*
 * Copyright (c) 2008-2026 Philippe Fournier-Viger
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
 * 
 * Do not remove copyright and license
 */
public class AlgorithmViewerOptions {
	/**
	 * Builds the list of viewer options offered in the Output tab, tailored to
	 * the given algorithm's output file types.
	 *
	 * @param algorithm the algorithm whose output is being previewed
	 * @return an array of viewer option labels
	 */
	public static String[] buildViewerOptionsForAlgorithm(DescriptionOfAlgorithm algorithm) {
		String[] types = algorithm.getOutputFileTypes();
		java.util.List<String> opts = new java.util.ArrayList<>();

		AlgorithmType algoType = algorithm.getAlgorithmType();

		if (algoType != AlgorithmType.DATA_VIEWER) {
			try {
				opts.add(AlgorithmManager.getInstance().getViewerFor(types).getName());
			} catch (Exception ignored) {
			}
		}

		// add specialized viewers based on the output type keywords
		for (String t : types) {
			if (t.contains("itemset")) {
				opts.add("Visualize_With_ItemItemset_Matrix");
				break;
			}
		}
		for (String t : types) {
			if (t.contains("association rules")) {
				opts.add("Association_rule_explorer");
				break;
			}
		}
		for (String t : types) {
			if (t.contains("sequential patterns")) {
				opts.add("Sequential_patterns_explorer");
				opts.add("Visualize_sequential_patterns_with_transition_graph_viewer");
				break;
			}
		}
		if (types[0].equals("Time series database")) {
			opts.remove("Pattern viewer");
			opts.add("Time series viewer");
		}
		if (types[0].equals("Clusters"))
			opts.add("Cluster viewer");
		
		if (!algoType.equals(AlgorithmType.OTHER_TOOL) && !algoType.equals(AlgorithmType.OTHER_GUI_TOOL)
				&& !algoType.equals(AlgorithmType.DATA_GENERATOR)) {
			opts.add("Pattern viewer");
		}
		opts.add("System text editor");
		opts.add("SPMF text editor");
		
		int last = types.length - 1;
		if (types[last].equals("Top-k Frequent subgraphs") || types[last].equals("Frequent subgraphs")) {
			opts.remove("Pattern viewer");
			opts.add("Graph viewer");
		}
		return opts.toArray(new String[0]);
	}
}
