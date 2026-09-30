package ca.pfv.spmf.gui.viewers.association_rule_explorer;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;

import javax.swing.SwingUtilities;

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
 * SPMF is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPMF. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Do not remove copyright and license.
 */

/**
 * Example of how to use the AssociationRuleExplorer to visualize rules
 * stored in a file in SPMF format.
 * 
 * @author Philippe Fournier-Viger (Copyright 2025)
 */
public class MainTestAssociationRuleExplorer {

	public static void main(String[] args) throws IOException {
		// ---- my name, printed in the console before anything else ----
		System.out.println("==========================================================");
		System.out.println("  Association Rule Explorer - improved and fixed by");
		System.out.println("  Arena.ai Agent");
		System.out.println("==========================================================");

		// The input file (put "Association rules.txt" next to this class,
		// or pass a file path as the first command-line argument)
		String inputPath;
		if (args.length > 0) {
			inputPath = args[0];
		} else {
			try {
				inputPath = fileToPath("Association rules.txt");
			} catch (RuntimeException ex) {
				// fall back to a file next to the sources / working directory
				inputPath = "data/Association rules.txt";
				if (!new File(inputPath).exists()) {
					inputPath = "Association rules.txt";
				}
			}
		}
		System.out.println("Loading: " + inputPath);

		final String path = inputPath;
		SwingUtilities.invokeLater(() -> {
			try {
				AssociationRuleExplorer explorer = new AssociationRuleExplorer(path);
				explorer.setDefaultCloseOperation(javax.swing.JFrame.EXIT_ON_CLOSE);
				explorer.setVisible(true);
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}

	/**
	 * Helper method to convert a file name in the same directory to a path
	 * 
	 * @param filename the filename (e.g. rules.txt)
	 * @return a full path
	 * @throws UnsupportedEncodingException if error occurs
	 */
	public static String fileToPath(String filename) throws UnsupportedEncodingException {
		URL url = MainTestAssociationRuleExplorer.class.getResource(filename);
		if (url == null) {
			throw new RuntimeException("File not found in resources: " + filename);
		}
		return URLDecoder.decode(url.getPath(), "UTF-8");
	}
}
