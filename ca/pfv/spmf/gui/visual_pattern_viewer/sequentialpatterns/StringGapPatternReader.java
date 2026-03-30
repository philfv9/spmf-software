package ca.pfv.spmf.gui.visual_pattern_viewer.sequentialpatterns;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
 * Class to read sequential patterns from an SPMF-formatted file
 * where each itemset has a single item and there is no seperator like
 * -1 between item but a single space. This is the output format  of GoKrimp, for example.
 * @author Philippe Fournier-Viger 2025
 */
public class StringGapPatternReader extends SequentialPatternsReader {

	/**
	 * Constructor
	 * @param filePath pattern file path
	 * @throws IOException exception if problem while reading the file
	 */
	public StringGapPatternReader(String filePath) throws IOException {
		super(filePath);
	}
	
	/**
     * Parse the tokens that form a sequential pattern
     * @param sequencePart the sequencePart like this:  a b a c a  where it is a list of items separated by spaces.
     * @param itemsets a list of itemsets for storing the pattern after it has been parsed.
     */
	protected void parsePattern(String sequencePart,  List<List<String>> itemsets) {
        // Parse sequence into itemsets
        String[] tokens = sequencePart.split(" ");
		for (String token : tokens) {
			if(token.isEmpty() == false) {
				List<String> itemset = new ArrayList<>(1);
				itemset.add(token);
				itemsets.add(itemset);
			}
		}
	}

}
