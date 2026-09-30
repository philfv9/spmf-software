package ca.pfv.spmf.gui.viewers.sequential_pattern_explorer;

import java.io.UnsupportedEncodingException;
import java.net.URL;

import javax.swing.SwingUtilities;

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
 * Example: launch the Sequential Pattern Explorer on a file of sequential
 * patterns in the standard SPMF output format.
 *
 * @author Philippe Fournier-Viger 2025
 */
public class MainTestSequentialPatternExplorer2 {

	public static void main(String[] args) throws Exception {
		String inputFile = fileToPath("Sequential patterns.txt");
		SwingUtilities.invokeLater(() -> {
			SequentialPatternExplorer viewer = new SequentialPatternExplorer(inputFile, true);
			viewer.setVisible(true);
		});
	}

	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestSequentialPatternExplorer2.class.getResource(filename);
		return java.net.URLDecoder.decode(url.getPath(), "UTF-8");
	}
}