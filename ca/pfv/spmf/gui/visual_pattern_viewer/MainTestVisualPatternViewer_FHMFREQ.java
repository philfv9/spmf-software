package ca.pfv.spmf.gui.visual_pattern_viewer;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;

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
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Example of how to use RulesViewer to visualize itemsets stored in a file.
 * 
 * @author Philippe Fournier-Viger (Copyright 2025)
 */
public class MainTestVisualPatternViewer_FHMFREQ {
	public static void main(String[] args) throws IOException {
		// The input file path to the file
		String inputPath = fileToPath("fhmfreq.txt");
		
		// Create the tool for viewing rules.
        VisualPatternViewer viewer = new VisualPatternViewer(true, inputPath, PatternType.ITEMSETS);
        viewer.setVisible(true);
	}

	/**
	 * Helper method to convert a file name in the same directory to a path
	 * @param filename the filename (e.g. rules.txt)
	 * @return a full path
	 * @throws UnsupportedEncodingException if error occurs
	 */
	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestVisualPatternViewer_FHMFREQ.class.getResource(filename);
		if (url == null) {
			throw new RuntimeException("File not found in resources: " + filename);
		}
		return URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
