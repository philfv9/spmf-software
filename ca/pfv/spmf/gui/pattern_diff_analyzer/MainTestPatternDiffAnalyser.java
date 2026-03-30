package ca.pfv.spmf.gui.pattern_diff_analyzer;


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
 * Example of how to call the Pattern Diff Viewer from the source code
 * 
 * @author Philippe Fournier-Viger (Copyright 2025)
 */
public class MainTestPatternDiffAnalyser {
	public static void main(String[] args) throws IOException {
		// Create the tool for viewing rules.
        PatternDiffAnalyzer viewer = new PatternDiffAnalyzer(true);
        viewer.setVisible(true);
	}
}
