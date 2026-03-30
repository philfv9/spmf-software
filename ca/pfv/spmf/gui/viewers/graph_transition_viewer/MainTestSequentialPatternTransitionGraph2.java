package ca.pfv.spmf.gui.viewers.graph_transition_viewer;

import java.io.UnsupportedEncodingException;
import java.net.URL;

import ca.pfv.spmf.gui.visual_pattern_viewer.PatternType;
import ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns.SequentialPatternsReader;

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
 * Example test file for trying the Sequential Pattern Transition Network
 * (Transition Graph) visualization tool. The input file should contain sequential
 * patterns in the standard SPMF output format
 * 
 * @author Philippe Fournier-Viger 2025
 **/
public class MainTestSequentialPatternTransitionGraph2 {

	public static void main(String[] args) throws Exception {

		// The path to a file containing sequential patterns
		// in the standard SPMF output format
		String inputFile = fileToPath("CEPN.txt");
		SequentialPatternsReader reader = new SequentialPatternsReader(inputFile);

		// Launch the Transition Graph Viewer in standalone mode
		boolean runAsStandaloneProgram = true;
		TransitionGraphViewer viewer = new TransitionGraphViewer(reader.getPatterns(),
				runAsStandaloneProgram);
		viewer.setVisible(true);

	}

	/**
	 * Utility method to get the file path of a file that is provided as a resource.
	 *
	 * @param filename the name of the file
	 * @return the file path as a String
	 * @throws UnsupportedEncodingException if encoding is not supported
	 */
	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestSequentialPatternTransitionGraph2.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
